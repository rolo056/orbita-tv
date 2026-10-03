package com.orbita.tv

import com.orbita.tv.data.Account
import com.orbita.tv.data.Avance
import com.orbita.tv.data.Biblioteca
import com.orbita.tv.data.Guardado
import com.orbita.tv.data.PuntoDeSerie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que se guarda de peliculas y series: que no se pierda ni se mezcle entre cuentas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BibliotecaTest {

    private val minuto = 60_000L

    @Test
    fun `lo guardado vuelve igual despues de escribirlo y leerlo`() {
        val b = Biblioteca(cuenta = "ana@203.0.113.10")
            .conAvance(Biblioteca.clavePelicula(5001), 23 * minuto, 102 * minuto, 1_000L)
            .conAvance(Biblioteca.claveEpisodio(9002), 5 * minuto, 22 * minuto, 2_000L)
            .conReciente(Guardado(false, 5001, "La primera", "https://image.example/p/uno.jpg", "mkv"))
            .conReciente(Guardado(true, 77, "Los vecinos", null))
            .alternarFavorita(Guardado(true, 77, "Los vecinos", null))
            .conPunto(77, PuntoDeSerie(9002, 1, 2))

        val leida = Biblioteca.deJson(b.aJson())
        assertEquals(b, leida)
        assertEquals(23 * minuto, leida.avanceDePelicula(5001)!!.posMs)
        assertEquals("mkv", leida.recientes.first { !it.serie }.extension)
        assertEquals(PuntoDeSerie(9002, 1, 2), leida.puntos[77])
    }

    @Test
    fun `un archivo ilegible es una biblioteca vacia y no un cierre`() {
        assertEquals(Biblioteca(), Biblioteca.deJson(null))
        assertEquals(Biblioteca(), Biblioteca.deJson(""))
        assertEquals(Biblioteca(), Biblioteca.deJson("no es json"))
        // Con partes rotas, se conserva lo que se entiende.
        val b = Biblioteca.deJson(
            """{"cuenta": "x@y", "avances": {"p:1": [10, 20, 30], "p:2": "roto", "p:3": [1]},
                "recientes": [{"s": false, "i": 1, "n": "Uno"}, "roto", {"n": "sin id"}],
                "puntos": {"abc": [1, 2, 3], "7": [9, 1, 2]}}"""
        )
        assertEquals("x@y", b.cuenta)
        assertEquals(setOf("p:1"), b.avances.keys)
        assertEquals(listOf(1), b.recientes.map { it.id })
        assertEquals(setOf(7), b.puntos.keys)
    }

    @Test
    fun `cuando se ofrece seguir y cuando se da por vista`() {
        val total = 100 * minuto
        // Recien empezada: no vale la pena ofrecer "seguir".
        assertFalse(Avance(30_000L, total, 0).aMedias)
        assertTrue(Avance(20 * minuto, total, 0).aMedias)
        // En los creditos: vista.
        assertTrue(Avance(95 * minuto, total, 0).terminado)
        assertFalse(Avance(95 * minuto, total, 0).aMedias)
        // Un episodio corto al que le quedan treinta segundos: visto.
        assertTrue(Avance(20 * minuto - 30_000L, 20 * minuto, 0).terminado)
        // Sin duracion conocida no se puede dar por vista.
        assertFalse(Avance(5 * minuto, 0, 0).terminado)
        assertEquals(0.2f, Avance(20 * minuto, total, 0).fraccion, 0.001f)
    }

    @Test
    fun `lo ultimo que se abrio va primero y no se repite`() {
        val b = Biblioteca()
            .conReciente(Guardado(false, 1, "Uno", null))
            .conReciente(Guardado(false, 2, "Dos", null))
            .conReciente(Guardado(true, 1, "Serie uno", null))
            .conReciente(Guardado(false, 1, "Uno", null))
        assertEquals(listOf("Uno", "Serie uno", "Dos"), b.recientes.map { it.nombre })
        // Una pelicula y una serie pueden tener el mismo numero: son cosas distintas.
        assertEquals(3, b.recientes.size)
        assertEquals(listOf("Serie uno", "Dos"), b.sinReciente(false, 1).recientes.map { it.nombre })
    }

    @Test
    fun `marcar favorita dos veces la quita`() {
        val g = Guardado(false, 5, "Cinco", null)
        val una = Biblioteca().alternarFavorita(g)
        assertTrue(una.esFavorita(false, 5))
        assertFalse(una.esFavorita(true, 5))
        assertFalse(una.alternarFavorita(g).esFavorita(false, 5))
    }

    @Test
    fun `los avances viejos se van cuando hay demasiados`() {
        var b = Biblioteca()
        for (i in 1..450) b = b.conAvance(Biblioteca.claveEpisodio(i), minuto, 10 * minuto, i.toLong())
        assertEquals(400, b.avances.size)
        assertNull(b.avanceDeEpisodio(1))
        assertEquals(450L, b.avanceDeEpisodio(450)!!.cuando)
    }

    @Test
    fun `la firma distingue cuentas en el mismo servidor`() {
        val a = Account(host = "203.0.113.10", port = 8080, username = "ana", password = "x")
        val otra = a.copy(username = "luis")
        assertTrue(Biblioteca.firma(a) != Biblioteca.firma(otra))
        // Cambiar la clave o el puerto no es cambiar de cuenta.
        assertEquals(Biblioteca.firma(a), Biblioteca.firma(a.copy(password = "y", port = 80)))
    }
}
