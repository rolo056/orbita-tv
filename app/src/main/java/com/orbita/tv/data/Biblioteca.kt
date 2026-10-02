package com.orbita.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** Hasta donde se llego en una pelicula o en un episodio. */
data class Avance(val posMs: Long, val durMs: Long, val cuando: Long) {
    val fraccion: Float
        get() = if (durMs <= 0) 0f else (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)

    /** Se da por vista cuando solo quedan los creditos. */
    val terminado: Boolean
        get() = durMs > 0 && (posMs >= durMs * 0.93 || durMs - posMs < 45_000)

    /** Vale la pena ofrecer "seguir": ni recien empezada ni ya terminada. */
    val aMedias: Boolean
        get() = posMs > 60_000 && !terminado
}

/** Un titulo guardado con lo justo para volver a dibujarlo sin pedirle nada al panel. */
data class Guardado(
    val serie: Boolean,
    val id: Int,
    val nombre: String,
    val imagen: String?,
    val extension: String = "mp4",
)

/** En que episodio se quedo una serie. */
data class PuntoDeSerie(val episodioId: Int, val temporada: Int, val numero: Int)

/**
 * Lo que el usuario dejo a medias o marco como favorito, guardado en el aparato.
 *
 * Es un valor inmutable: cada cambio devuelve una biblioteca nueva. Asi la
 * pantalla se entera sola de que algo cambio, y guardar es escribir el valor
 * entero, sin estados intermedios.
 *
 * Todo esto pertenece a UNA cuenta. Los ids de peliculas y series no significan
 * nada en otro proveedor: con otra cuenta, el 5001 es otra pelicula. Por eso la
 * biblioteca recuerda de que cuenta es, y con otra cuenta empieza vacia.
 */
data class Biblioteca(
    val cuenta: String = "",
    val avances: Map<String, Avance> = emptyMap(),
    val recientes: List<Guardado> = emptyList(),
    val favoritas: List<Guardado> = emptyList(),
    val puntos: Map<Int, PuntoDeSerie> = emptyMap(),
) {
    fun avanceDePelicula(id: Int): Avance? = avances[clavePelicula(id)]
    fun avanceDeEpisodio(id: Int): Avance? = avances[claveEpisodio(id)]

    fun esFavorita(serie: Boolean, id: Int): Boolean =
        favoritas.any { it.serie == serie && it.id == id }

    fun conAvance(clave: String, posMs: Long, durMs: Long, ahora: Long): Biblioteca {
        var nuevos = avances + (clave to Avance(posMs.coerceAtLeast(0), durMs.coerceAtLeast(0), ahora))
        if (nuevos.size > MAX_AVANCES) {
            // Se van los mas viejos: nadie retoma un episodio de hace cuatrocientos.
            nuevos = nuevos.entries.sortedByDescending { it.value.cuando }
                .take(MAX_AVANCES).associate { it.key to it.value }
        }
        return copy(avances = nuevos)
    }

    /** Lo ultimo que se abrio va primero. */
    fun conReciente(g: Guardado): Biblioteca {
        val resto = recientes.filterNot { it.serie == g.serie && it.id == g.id }
        return copy(recientes = (listOf(g) + resto).take(MAX_RECIENTES))
    }

    fun sinReciente(serie: Boolean, id: Int): Biblioteca =
        copy(recientes = recientes.filterNot { it.serie == serie && it.id == id })

    fun alternarFavorita(g: Guardado): Biblioteca {
        val estaba = esFavorita(g.serie, g.id)
        val resto = favoritas.filterNot { it.serie == g.serie && it.id == g.id }
        return copy(favoritas = if (estaba) resto else listOf(g) + resto)
    }

    fun conPunto(serieId: Int, punto: PuntoDeSerie): Biblioteca =
        copy(puntos = puntos + (serieId to punto))

    fun aJson(): String {
        val o = JSONObject()
        o.put("cuenta", cuenta)
        val a = JSONObject()
        for ((k, v) in avances) {
            a.put(k, JSONArray().put(v.posMs).put(v.durMs).put(v.cuando))
        }
        o.put("avances", a)
        o.put("recientes", lista(recientes))
        o.put("favoritas", lista(favoritas))
        val p = JSONObject()
        for ((k, v) in puntos) {
            p.put(k.toString(), JSONArray().put(v.episodioId).put(v.temporada).put(v.numero))
        }
        o.put("puntos", p)
        return o.toString()
    }

    companion object {
        private const val MAX_AVANCES = 400
        private const val MAX_RECIENTES = 40

        fun clavePelicula(id: Int): String = "p:$id"
        fun claveEpisodio(id: Int): String = "e:$id"

        /** La cuenta a la que pertenece una biblioteca: el servidor y el usuario. */
        fun firma(account: Account): String = account.username + "@" + account.host

        /**
         * Lee lo guardado. Nada de esto lanza excepciones: lo que no se entiende
         * se descarta, y un archivo ilegible es una biblioteca vacia.
         */
        fun deJson(raw: String?): Biblioteca {
            if (raw.isNullOrBlank()) return Biblioteca()
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return Biblioteca()

            val avances = LinkedHashMap<String, Avance>()
            o.optJSONObject("avances")?.let { a ->
                val claves = a.keys()
                while (claves.hasNext()) {
                    val k = claves.next()
                    val v = a.optJSONArray(k) ?: continue
                    if (v.length() < 3) continue
                    avances[k] = Avance(v.optLong(0), v.optLong(1), v.optLong(2))
                }
            }

            val puntos = LinkedHashMap<Int, PuntoDeSerie>()
            o.optJSONObject("puntos")?.let { p ->
                val claves = p.keys()
                while (claves.hasNext()) {
                    val k = claves.next()
                    val id = k.toIntOrNull() ?: continue
                    val v = p.optJSONArray(k) ?: continue
                    if (v.length() < 3) continue
                    puntos[id] = PuntoDeSerie(v.optInt(0), v.optInt(1), v.optInt(2))
                }
            }

            return Biblioteca(
                cuenta = o.optString("cuenta", ""),
                avances = avances,
                recientes = guardados(o.optJSONArray("recientes")),
                favoritas = guardados(o.optJSONArray("favoritas")),
                puntos = puntos,
            )
        }

        private fun lista(titulos: List<Guardado>): JSONArray {
            val a = JSONArray()
            for (g in titulos) {
                val o = JSONObject()
                o.put("s", g.serie)
                o.put("i", g.id)
                o.put("n", g.nombre)
                if (g.imagen != null) o.put("g", g.imagen)
                o.put("x", g.extension)
                a.put(o)
            }
            return a
        }

        private fun guardados(a: JSONArray?): List<Guardado> {
            if (a == null) return emptyList()
            val out = ArrayList<Guardado>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (!o.has("i")) continue
                val imagen = o.optString("g", "")
                out.add(
                    Guardado(
                        serie = o.optBoolean("s", false),
                        id = o.optInt("i"),
                        nombre = o.optString("n", ""),
                        imagen = if (imagen.isBlank()) null else imagen,
                        extension = o.optString("x", "mp4").ifBlank { "mp4" },
                    )
                )
            }
            return out
        }
    }
}
