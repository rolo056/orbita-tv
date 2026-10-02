package com.orbita.tv.net

import android.util.JsonReader
import android.util.JsonToken
import android.util.MalformedJsonException
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.Reader

/** Una pelicula del catalogo, tal como viene en la lista: lo justo para dibujar la portada. */
data class Movie(
    val streamId: Int,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    /** mp4, mkv, avi... Forma parte de la direccion del video. */
    val extension: String,
    /** De 0 a 10. Nulo cuando el panel no la trae. */
    val rating: Float?,
    val year: String?,
)

/** La ficha de una pelicula. Se pide aparte, solo al abrirla. */
data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val released: String?,
    val durationSecs: Int,
    val rating: Float?,
    val backdrop: String?,
    val poster: String?,
    /** La extension segun la ficha, que manda sobre la de la lista cuando difieren. */
    val extension: String?,
)

data class Series(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String?,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val released: String?,
    val rating: Float?,
    val backdrop: String?,
)

data class Episode(
    /** El id del video del episodio: es el que va en la direccion. */
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val durationSecs: Int,
    val plot: String?,
    val image: String?,
)

data class Season(val number: Int, val episodes: List<Episode>)

data class SeriesDetail(val series: Series?, val seasons: List<Season>)

/**
 * Lectura de lo que devuelven los paneles Xtream.
 *
 * Vive aparte de la red para poder probarse con respuestas guardadas, y porque
 * aca esta casi todo el riesgo: cada panel devuelve lo mismo de una forma
 * distinta. El mismo campo llega como numero o como texto; una lista vacia llega
 * como [] donde se esperaba un objeto; los episodios vienen agrupados por
 * temporada en un objeto, o en una lista de listas, segun como se numeren. Nada
 * de eso puede cerrar la app: lo que no se entiende se salta.
 *
 * Las listas se recorren elemento por elemento, sin cargar la respuesta entera.
 * Un catalogo de peliculas pesa decenas de megas, y un televisor viejo no tiene
 * memoria para tenerlo dos veces (una como texto y otra como objetos).
 */
object XtreamJson {

    /**
     * Recorre una lista del panel y entrega cada elemento con su posicion.
     *
     * Acepta las dos formas en que llega una lista: un arreglo, o un objeto con
     * los elementos colgados de claves numericas (asi serializa PHP una lista
     * con huecos). Si lo que llega es un aviso de error del panel, lo lanza.
     */
    fun cadaObjeto(fuente: Reader, entregar: (JSONObject, Int) -> Unit) {
        val r = JsonReader(fuente)
        r.isLenient = true
        try {
            when (r.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    var i = 0
                    r.beginArray()
                    while (r.hasNext()) {
                        val v = leer(r)
                        if (v is JSONObject) entregar(v, i)
                        i++
                    }
                    r.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    var i = 0
                    var rechazo = false
                    r.beginObject()
                    while (r.hasNext()) {
                        val clave = r.nextName()
                        val v = leer(r)
                        when {
                            clave == "error" && v is String && v.isNotBlank() ->
                                throw XtreamException(v)
                            clave == "user_info" -> {
                                val auth = (v as? JSONObject)?.opt("auth")?.toString()
                                if (auth == "0") rechazo = true
                            }
                            clave == "server_info" -> Unit
                            v is JSONObject -> {
                                entregar(v, i)
                                i++
                            }
                        }
                    }
                    r.endObject()
                    if (rechazo) throw XtreamException("El panel rechazo la cuenta", 401)
                    if (i == 0) throw XtreamException("El servidor no devolvio una lista")
                }
                else -> throw XtreamException("El servidor no devolvio una lista")
            }
        } catch (e: MalformedJsonException) {
            throw XtreamException("El servidor no devolvio una lista")
        } catch (e: EOFException) {
            throw XtreamException("El servidor no devolvio una lista")
        } catch (e: IllegalStateException) {
            // JsonReader avisa asi cuando la estructura no es la que anuncia.
            throw XtreamException("El servidor no devolvio una lista")
        }
    }

    /** Un valor JSON cualquiera, leido entero. Los numeros se guardan como texto, sin perder nada. */
    private fun leer(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val o = JSONObject()
            r.beginObject()
            while (r.hasNext()) {
                val k = r.nextName()
                o.put(k, leer(r) ?: JSONObject.NULL)
            }
            r.endObject()
            o
        }
        JsonToken.BEGIN_ARRAY -> {
            val a = JSONArray()
            r.beginArray()
            while (r.hasNext()) a.put(leer(r) ?: JSONObject.NULL)
            r.endArray()
            a
        }
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean()
        JsonToken.NULL -> {
            r.nextNull()
            null
        }
        else -> {
            r.skipValue()
            null
        }
    }

    // ------------------------------------------------------------ elementos

    fun categoria(o: JSONObject): Category? {
        val id = o.texto("category_id") ?: return null
        return Category(id, o.texto("category_name") ?: "Sin nombre")
    }

    fun canal(o: JSONObject, posicion: Int): Channel? {
        val id = o.entero("stream_id") ?: return null
        return Channel(
            streamId = id,
            name = o.texto("name") ?: ("Canal $id"),
            number = o.entero("num") ?: (posicion + 1),
            icon = o.url("stream_icon"),
            categoryId = o.categoriaId(),
        )
    }

    fun pelicula(o: JSONObject): Movie? {
        val id = o.entero("stream_id") ?: return null
        return Movie(
            streamId = id,
            name = o.texto("name") ?: o.texto("title") ?: ("Película $id"),
            poster = o.url("stream_icon") ?: o.url("cover"),
            categoryId = o.categoriaId(),
            extension = o.extension(),
            rating = o.puntaje(),
            year = o.texto("year") ?: anio(o.texto("releaseDate") ?: o.texto("release_date")),
        )
    }

    fun serie(o: JSONObject): Series? {
        val id = o.entero("series_id") ?: return null
        return Series(
            seriesId = id,
            name = o.texto("name") ?: o.texto("title") ?: ("Serie $id"),
            cover = o.url("cover") ?: o.url("stream_icon"),
            categoryId = o.categoriaId(),
            plot = o.texto("plot"),
            genre = o.texto("genre"),
            cast = o.texto("cast"),
            released = o.texto("releaseDate") ?: o.texto("release_date") ?: o.texto("year"),
            rating = o.puntaje(),
            backdrop = o.primeraUrl("backdrop_path"),
        )
    }

    // -------------------------------------------------------------- fichas

    /** get_vod_info. Devuelve nulo si la respuesta no es una ficha. */
    fun fichaPelicula(cuerpo: String): MovieInfo? {
        val raiz = runCatching { JSONObject(cuerpo) }.getOrNull() ?: return null
        // "info" llega como [] cuando el panel no tiene datos de la pelicula.
        val info = raiz.optJSONObject("info") ?: JSONObject()
        val datos = raiz.optJSONObject("movie_data") ?: JSONObject()
        return MovieInfo(
            plot = info.texto("plot") ?: info.texto("description"),
            genre = info.texto("genre"),
            cast = info.texto("cast") ?: info.texto("actors"),
            director = info.texto("director"),
            released = info.texto("releasedate") ?: info.texto("release_date")
                ?: info.texto("releaseDate"),
            durationSecs = info.duracion(),
            rating = info.puntaje(),
            backdrop = info.primeraUrl("backdrop_path"),
            poster = info.url("movie_image") ?: info.url("cover_big") ?: info.url("cover"),
            extension = datos.texto("container_extension")?.let { limpiarExtension(it) },
        )
    }

    /** get_series_info: la serie con sus temporadas, cada una con los episodios en orden. */
    fun fichaSerie(cuerpo: String, seriesId: Int): SeriesDetail {
        val raiz = runCatching { JSONObject(cuerpo) }.getOrNull()
            ?: return SeriesDetail(null, emptyList())

        val info = raiz.optJSONObject("info")
        val serie = info?.let {
            // La ficha no repite el id: se toma el que se pidio.
            if (!it.has("series_id")) it.put("series_id", seriesId)
            serie(it)
        }

        val porTemporada = java.util.TreeMap<Int, ArrayList<Episode>>()
        fun sumar(o: JSONObject, temporadaDeLaClave: Int?) {
            val e = episodio(o, temporadaDeLaClave) ?: return
            porTemporada.getOrPut(e.season) { ArrayList() }.add(e)
        }
        fun sumarGrupo(grupo: Any?, temporadaDeLaClave: Int?) {
            when (grupo) {
                is JSONArray -> for (i in 0 until grupo.length()) {
                    val x = grupo.opt(i)
                    if (x is JSONObject) sumar(x, temporadaDeLaClave) else sumarGrupo(x, null)
                }
                // Un episodio suelto donde se esperaba una lista.
                is JSONObject -> if (grupo.has("id")) sumar(grupo, temporadaDeLaClave)
            }
        }
        when (val eps = raiz.opt("episodes")) {
            is JSONObject -> {
                val claves = eps.keys()
                while (claves.hasNext()) {
                    val k = claves.next()
                    sumarGrupo(eps.opt(k), k.trim().toIntOrNull())
                }
            }
            is JSONArray -> sumarGrupo(eps, null)
        }

        val temporadas = porTemporada.map { (n, lista) ->
            // Dos entradas con el mismo numero de episodio son el mismo episodio
            // subido dos veces; se queda la primera y las demas no se pierden:
            // van a continuacion, por su id.
            Season(n, lista.sortedWith(compareBy({ it.number }, { it.id })))
        }
        return SeriesDetail(serie, temporadas)
    }

    private fun episodio(o: JSONObject, temporadaDeLaClave: Int?): Episode? {
        val id = o.entero("id") ?: o.entero("stream_id") ?: return null
        // Igual que en las peliculas: "info" puede venir como [].
        val info = o.optJSONObject("info") ?: JSONObject()
        val temporada = o.entero("season") ?: temporadaDeLaClave ?: info.entero("season") ?: 1
        val numero = o.entero("episode_num") ?: info.entero("episode_num") ?: 0
        return Episode(
            id = id,
            season = temporada,
            number = numero,
            title = tituloDeEpisodio(o.texto("title"), numero),
            extension = o.extension(),
            durationSecs = info.duracion(),
            plot = info.texto("plot") ?: info.texto("overview"),
            image = info.url("movie_image") ?: info.url("cover_big"),
        )
    }

    /**
     * Los paneles suelen titular cada episodio "Nombre de la serie - S01E02 -
     * Titulo". En una lista de episodios de esa misma serie y temporada, todo
     * menos el final es ruido que empuja lo unico distinto fuera de la pantalla.
     */
    fun tituloDeEpisodio(crudo: String?, numero: Int): String {
        val porDefecto = if (numero > 0) "Episodio $numero" else "Episodio"
        val t = crudo?.trim().orEmpty()
        if (t.isEmpty()) return porDefecto
        val m = MARCA_EPISODIO.find(t) ?: return t
        val resto = t.substring(m.range.last + 1).trim().trimStart('-', ':', '.', '|', ' ').trim()
        return if (resto.isEmpty()) porDefecto else resto
    }

    private val MARCA_EPISODIO = Regex("""[Ss]\d{1,3}\s*[._ -]?\s*[Ee]\d{1,4}""")

    // --------------------------------------------------------- utilidades

    /** El texto de un campo, o nulo si falta, esta vacio o no es un texto. */
    private fun JSONObject.texto(clave: String): String? {
        if (!has(clave) || isNull(clave)) return null
        val v = opt(clave)
        if (v is JSONObject || v is JSONArray) return null
        val s = v.toString().trim()
        if (s.isEmpty() || s.equals("null", true) || s.equals("N/A", true)) return null
        return s
    }

    private fun JSONObject.entero(clave: String): Int? {
        val s = texto(clave) ?: return null
        return s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt()
    }

    private fun JSONObject.url(clave: String): String? {
        val s = texto(clave) ?: return null
        return if (s.startsWith("http://") || s.startsWith("https://")) s else null
    }

    /** Para campos que llegan como una direccion o como una lista de direcciones. */
    private fun JSONObject.primeraUrl(clave: String): String? {
        val v = opt(clave)
        if (v is JSONArray) {
            for (i in 0 until v.length()) {
                val s = v.optString(i, "").trim()
                if (s.startsWith("http://") || s.startsWith("https://")) return s
            }
            return null
        }
        return url(clave)
    }

    /** category_id, o el primero de category_ids en los paneles que usan lista. */
    private fun JSONObject.categoriaId(): String? {
        texto("category_id")?.let { return it }
        val varios = opt("category_ids") as? JSONArray ?: return null
        for (i in 0 until varios.length()) {
            val s = varios.optString(i, "").trim()
            if (s.isNotEmpty() && s != "null") return s
        }
        return null
    }

    private fun JSONObject.extension(): String =
        limpiarExtension(texto("container_extension") ?: "mp4")

    /**
     * Solo las letras y numeros del principio: la extension termina dentro de
     * una direccion, y lo que venga despues no es extension.
     */
    private fun limpiarExtension(crudo: String): String {
        val limpia = crudo.trim().trimStart('.').lowercase().takeWhile { it.isLetterOrDigit() }
        return if (limpia.isEmpty() || limpia.length > 5) "mp4" else limpia
    }

    /** De 0 a 10. El cero es "sin puntaje", no una pelicula pesima. */
    private fun JSONObject.puntaje(): Float? {
        val diez = texto("rating")?.replace(',', '.')?.toFloatOrNull()
        if (diez != null && diez > 0f) return diez.coerceAtMost(10f)
        val cinco = texto("rating_5based")?.replace(',', '.')?.toFloatOrNull()
        if (cinco != null && cinco > 0f) return (cinco * 2f).coerceAtMost(10f)
        return null
    }

    /** En segundos. Prueba duration_secs, despues "01:42:10", despues minutos sueltos. */
    private fun JSONObject.duracion(): Int {
        entero("duration_secs")?.let { if (it > 0) return it }
        val d = texto("duration")
        if (d != null) {
            val partes = d.split(":").map { it.trim().toIntOrNull() }
            if (partes.all { it != null }) {
                val n = partes.filterNotNull()
                val s = when (n.size) {
                    3 -> n[0] * 3600 + n[1] * 60 + n[2]
                    2 -> n[0] * 60 + n[1]
                    else -> 0
                }
                if (s > 0) return s
            }
        }
        entero("episode_run_time")?.let { if (it > 0) return it * 60 }
        return 0
    }

    private fun anio(fecha: String?): String? {
        val m = Regex("""(19|20)\d{2}""").find(fecha ?: return null) ?: return null
        return m.value
    }
}
