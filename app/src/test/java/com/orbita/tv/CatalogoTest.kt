package com.orbita.tv

import com.orbita.tv.data.Account
import com.orbita.tv.net.AccountStatus
import com.orbita.tv.net.Channel
import com.orbita.tv.net.Movie
import com.orbita.tv.net.Xtream
import com.orbita.tv.net.XtreamException
import com.orbita.tv.net.XtreamJson
import com.orbita.tv.player.StreamVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

/**
 * Las respuestas de los paneles, tal como llegan de verdad.
 *
 * Cada caso de aca es una forma distinta en que un panel devuelve lo mismo. No
 * hay cuenta contra la que probar mientras se escribe esto, asi que lo que se
 * puede comprobar se comprueba con respuestas guardadas: que ninguna rareza
 * cierre la app ni deje una lista vacia sin motivo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogoTest {

    private fun canales(json: String): List<Channel> {
        val out = ArrayList<Channel>()
        XtreamJson.cadaObjeto(StringReader(json)) { o, i -> XtreamJson.canal(o, i)?.let { out.add(it) } }
        return out
    }

    private fun peliculas(json: String): List<Movie> {
        val out = ArrayList<Movie>()
        XtreamJson.cadaObjeto(StringReader(json)) { o, _ -> XtreamJson.pelicula(o)?.let { out.add(it) } }
        return out
    }

    private fun falla(json: String): XtreamException {
        try {
            canales(json)
        } catch (e: XtreamException) {
            return e
        }
        fail("Se esperaba un error del panel y no hubo ninguno")
        throw IllegalStateException()
    }

    // ------------------------------------------------------------- listas

    @Test
    fun `los ids llegan como numero o como texto`() {
        val lista = canales(
            """
            [
              {"num": 1, "name": "Canal Uno HD", "stream_id": 101, "stream_icon": "http://203.0.113.10/logos/1.png", "category_id": "3"},
              {"num": "2", "name": "Tele Centro", "stream_id": "102", "stream_icon": "", "category_id": 3},
              {"num": 3, "name": "", "stream_id": 103, "stream_icon": null, "category_id": null}
            ]
            """
        )
        assertEquals(listOf(101, 102, 103), lista.map { it.streamId })
        assertEquals(listOf(1, 2, 3), lista.map { it.number })
        assertEquals("http://203.0.113.10/logos/1.png", lista[0].icon)
        assertNull(lista[1].icon)
        assertEquals("3", lista[1].categoryId)
        assertEquals("Canal 103", lista[2].name)
        assertNull(lista[2].categoryId)
    }

    @Test
    fun `lo que no es un canal se salta sin romper la lista`() {
        val lista = canales(
            """
            [
              {"name": "Sin id"},
              "texto suelto",
              null,
              42,
              {"stream_id": "no es un numero", "name": "Id ilegible"},
              {"stream_id": 7, "name": "El unico bueno", "epg": {"a": [1, 2, {"b": null}]}, "tags": []}
            ]
            """
        )
        assertEquals(1, lista.size)
        assertEquals("El unico bueno", lista[0].name)
        // Sin "num", el numero de canal es la posicion en la lista.
        assertEquals(6, lista[0].number)
    }

    @Test
    fun `una lista con huecos llega como objeto de claves numericas`() {
        val lista = canales(
            """
            {"0": {"stream_id": 1, "name": "A"}, "3": {"stream_id": 2, "name": "B"}}
            """
        )
        assertEquals(listOf("A", "B"), lista.map { it.name })
    }

    @Test
    fun `una lista vacia no es un error`() {
        assertTrue(canales("[]").isEmpty())
        assertTrue(canales("  [ ]  ").isEmpty())
    }

    @Test
    fun `el aviso de error del panel se muestra tal cual`() {
        val e = falla("""{"error": "Too many connections"}""")
        assertEquals("Too many connections", e.message)
    }

    @Test
    fun `una cuenta rechazada se reconoce aunque la respuesta parezca valida`() {
        val e = falla("""{"user_info": {"auth": 0}}""")
        assertEquals(401, e.httpCode)
    }

    @Test
    fun `una pagina de error o una respuesta vacia no cierran la app`() {
        assertNotNull(falla("<html><body><h1>502 Bad Gateway</h1></body></html>").message)
        assertNotNull(falla("").message)
        assertNotNull(falla("""{"user_info": {"auth": 1}, "server_info": {"url": "x"}}""").message)
        // Cortada a la mitad, como la deja un enlace que se cae.
        assertNotNull(falla("""[{"stream_id": 1, "name": "A"}, {"stream_id": 2, "na""").message)
    }

    // ----------------------------------------------------------- peliculas

    @Test
    fun `peliculas con sus rarezas`() {
        val lista = peliculas(
            """
            [
              {"num": 1, "name": "La primera (2019)", "stream_type": "movie", "stream_id": 5001,
               "stream_icon": "https://image.example/p/uno.jpg", "rating": "7.4", "rating_5based": 3.7,
               "added": "1690000000", "category_id": "12", "container_extension": "mkv"},
              {"name": "Sin puntaje", "stream_id": "5002", "stream_icon": "/imagenes/dos.jpg",
               "rating": "", "rating_5based": 0, "category_ids": [44, 12], "container_extension": ".MP4"},
              {"name": "Puntaje sobre cinco", "stream_id": 5003, "rating": 0, "rating_5based": "4.1",
               "category_id": "", "container_extension": null, "year": "2021"},
              {"name": "Extension rara", "stream_id": 5004, "container_extension": "mp4?x=1/../",
               "releaseDate": "1998-05-17"}
            ]
            """
        )
        assertEquals(4, lista.size)

        assertEquals("mkv", lista[0].extension)
        assertEquals(7.4f, lista[0].rating!!, 0.001f)
        assertEquals("12", lista[0].categoryId)
        assertEquals("https://image.example/p/uno.jpg", lista[0].poster)

        // Una portada que no es una direccion completa no sirve para nada.
        assertNull(lista[1].poster)
        assertNull(lista[1].rating)
        assertEquals("44", lista[1].categoryId)
        assertEquals("mp4", lista[1].extension)

        assertEquals(8.2f, lista[2].rating!!, 0.001f)
        assertNull(lista[2].categoryId)
        assertEquals("mp4", lista[2].extension)
        assertEquals("2021", lista[2].year)

        // La extension va dentro de una direccion: no puede traer nada mas.
        assertEquals("mp4", lista[3].extension)
        assertEquals("1998", lista[3].year)
    }

    @Test
    fun `ficha de pelicula completa`() {
        val f = XtreamJson.fichaPelicula(
            """
            {"info": {"movie_image": "https://image.example/p/uno.jpg",
                      "backdrop_path": ["https://image.example/b/uno.jpg", "https://image.example/b/dos.jpg"],
                      "plot": "Una historia.", "cast": "Ana Uno, Luis Dos", "director": "Eva Tres",
                      "genre": "Drama, Suspenso", "releasedate": "2019-03-08",
                      "duration_secs": 6130, "duration": "01:42:10", "rating": "7.4"},
             "movie_data": {"stream_id": 5001, "name": "La primera", "container_extension": "mkv"}}
            """
        )!!
        assertEquals("Una historia.", f.plot)
        assertEquals(6130, f.durationSecs)
        assertEquals("https://image.example/b/uno.jpg", f.backdrop)
        assertEquals("mkv", f.extension)
        assertEquals("2019-03-08", f.released)
    }

    @Test
    fun `ficha de pelicula sin datos`() {
        // Asi responde un panel que no tiene ficha: "info" es una lista vacia.
        val f = XtreamJson.fichaPelicula(
            """{"info": [], "movie_data": {"stream_id": "5002", "container_extension": "mp4"}}"""
        )!!
        assertNull(f.plot)
        assertEquals(0, f.durationSecs)
        assertEquals("mp4", f.extension)

        val soloTexto = XtreamJson.fichaPelicula(
            """{"info": {"backdrop_path": "https://image.example/b/x.jpg", "duration": "95:30"}}"""
        )!!
        assertEquals("https://image.example/b/x.jpg", soloTexto.backdrop)
        assertEquals(95 * 60 + 30, soloTexto.durationSecs)
        assertNull(soloTexto.extension)

        assertNull(XtreamJson.fichaPelicula("<html>error</html>"))
    }

    // -------------------------------------------------------------- series

    @Test
    fun `episodios agrupados por temporada en un objeto`() {
        val d = XtreamJson.fichaSerie(
            """
            {"seasons": [],
             "info": {"name": "Los vecinos", "cover": "https://image.example/s/vecinos.jpg",
                      "plot": "Un edificio.", "genre": "Comedia", "releaseDate": "2015-01-01",
                      "rating": "8", "backdrop_path": [], "category_id": "9"},
             "episodes": {
               "2": [{"id": "9003", "episode_num": 1, "title": "Los vecinos - S02E01 - El regreso",
                      "container_extension": "mp4", "info": [], "season": 2}],
               "1": [{"id": "9002", "episode_num": "2", "title": "Los vecinos - S01E02 - La mudanza",
                      "container_extension": "mkv",
                      "info": {"duration_secs": "1320", "plot": "Llega alguien.",
                               "movie_image": "https://image.example/e/2.jpg"}, "season": 1},
                     {"id": 9001, "episode_num": 1, "title": "Los vecinos - S01E01",
                      "container_extension": "mkv", "info": {"duration": "00:21:40"}}]
             }}
            """,
            seriesId = 77,
        )
        assertEquals(77, d.series!!.seriesId)
        assertEquals("Los vecinos", d.series!!.name)
        assertEquals(8f, d.series!!.rating!!, 0.001f)
        assertNull(d.series!!.backdrop)

        // Las temporadas salen en orden aunque el panel las mande al reves.
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
        val t1 = d.seasons[0].episodes
        assertEquals(listOf(9001, 9002), t1.map { it.id })
        // Sin "season" en el episodio, vale la clave bajo la que venia.
        assertEquals(1, t1[0].season)
        assertEquals("Episodio 1", t1[0].title)
        assertEquals(21 * 60 + 40, t1[0].durationSecs)
        assertEquals("La mudanza", t1[1].title)
        assertEquals(1320, t1[1].durationSecs)
        assertEquals("mkv", t1[1].extension)
        assertEquals("El regreso", d.seasons[1].episodes[0].title)
        assertEquals(0, d.seasons[1].episodes[0].durationSecs)
    }

    @Test
    fun `episodios como lista de listas`() {
        val d = XtreamJson.fichaSerie(
            """
            {"info": [],
             "episodes": [
               [{"id": 1, "episode_num": 1, "title": "Piloto", "season": 1, "container_extension": "mp4"},
                {"id": 2, "episode_num": 2, "title": "", "season": 1, "container_extension": "mp4"}],
               [{"id": 3, "episode_num": 1, "title": "Vuelta", "season": 2, "container_extension": "mp4"}]
             ]}
            """,
            seriesId = 5,
        )
        assertNull(d.series)
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
        assertEquals(listOf("Piloto", "Episodio 2"), d.seasons[0].episodes.map { it.title })
    }

    @Test
    fun `una serie sin episodios o una respuesta ilegible dan una ficha vacia`() {
        assertTrue(XtreamJson.fichaSerie("""{"info": {"name": "Vacia"}, "episodes": []}""", 1).seasons.isEmpty())
        assertTrue(XtreamJson.fichaSerie("""{"info": {"name": "Vacia"}}""", 1).seasons.isEmpty())
        assertTrue(XtreamJson.fichaSerie("no es json", 1).seasons.isEmpty())
    }

    @Test
    fun `el titulo del episodio se queda con lo que lo distingue`() {
        assertEquals("El joven", XtreamJson.tituloDeEpisodio("Una serie - S01E02 - El joven", 2))
        assertEquals("El joven", XtreamJson.tituloDeEpisodio("Una serie S1 E2: El joven", 2))
        assertEquals("Episodio 2", XtreamJson.tituloDeEpisodio("Una serie - S01E02", 2))
        assertEquals("Episodio 7", XtreamJson.tituloDeEpisodio(null, 7))
        assertEquals("Episodio", XtreamJson.tituloDeEpisodio("  ", 0))
        // Sin la marca de temporada y episodio, el titulo se deja como esta.
        assertEquals("Capítulo final", XtreamJson.tituloDeEpisodio("Capítulo final", 12))
    }

    // --------------------------------------------------------------- cuenta

    @Test
    fun `una cuenta vencida se dice como vencida y con su fecha`() {
        // Asi contesta un panel real con la cuenta vencida: autenticacion correcta.
        val vencida = AccountStatus(true, "Expired", "1790812800", 2, 0)
        val mensaje = Xtream.cuentaInservible(vencida)!!
        assertTrue(mensaje, mensaje.contains("venció el 01/10/2026"))

        assertNull(Xtream.cuentaInservible(AccountStatus(true, "Active", "1893456000", 2, 0)))
        // Un panel que no informa el estado no es una cuenta inservible.
        assertNull(Xtream.cuentaInservible(AccountStatus(true, "desconocido", "", 0, 0)))
        assertNotNull(Xtream.cuentaInservible(AccountStatus(true, "Banned", "null", 1, 0)))
        // Vencida y sin fecha legible: se dice igual, sin inventar la fecha.
        val sinFecha = Xtream.cuentaInservible(AccountStatus(true, "expired", "", 1, 0))!!
        assertTrue(sinFecha, sinFecha.startsWith("La cuenta venció. "))
    }

    // --------------------------------------------------------- direcciones

    @Test
    fun `las direcciones del video llevan la cuenta codificada`() {
        val cuenta = Account(host = "203.0.113.10", port = 8080, username = "u s", password = "p/1")
        assertEquals(
            "http://203.0.113.10:8080/movie/u%20s/p%2F1/5001.mkv",
            StreamVariants.forMovie(cuenta, 5001, "mkv"),
        )
        assertEquals(
            "http://203.0.113.10:8080/series/u%20s/p%2F1/9002.mp4",
            StreamVariants.forEpisode(cuenta, 9002, "mp4"),
        )
    }
}
