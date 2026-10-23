package com.orbita.tv

import com.orbita.tv.data.Account
import com.orbita.tv.data.Biblioteca
import com.orbita.tv.data.hostParaUrl
import com.orbita.tv.net.SmartDns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet6Address

/**
 * Un servidor escrito como IPv6: el puente del VPS por IPv6, para cuando el
 * equipo de Starlink pierde IPv4 y la TV solo sale a internet por IPv6.
 */
class DireccionesTest {

    @Test
    fun `una IPv6 va entre corchetes en la direccion, con o sin corchetes al escribirla`() {
        assertEquals("http://[2001:470:1f06:a1::2]:18766", Account(host = "2001:470:1f06:a1::2", port = 18766).base)
        assertEquals("http://[2001:470:1f06:a1::2]:18766", Account(host = "[2001:470:1f06:a1::2]", port = 18766).base)
        assertEquals("http://38.188.185.254:25461", Account(host = "38.188.185.254", port = 25461).base)
        assertEquals("http://megaw.ddns.net:25461", Account(host = "megaw.ddns.net", port = 25461).base)
        assertEquals("[fd4e::1]", hostParaUrl("fd4e::1"))
    }

    @Test
    fun `una IPv6 escrita como servidor se usa aunque este puesto Forzar IPv4`() {
        val dns = SmartDns(forceIpv4 = true, useDoh = false)
        for (ip in listOf("2001:470:1f06:a1::2", "fd4e:8718:2a55:8::1")) {
            val r = dns.lookup(ip)
            assertEquals(1, r.size)
            assertTrue(r[0] is Inet6Address)
        }
    }

    @Test
    fun `cambiar solo el servidor no es cambiar de cuenta`() {
        val directo = Account(host = "38.188.185.254", port = 25461, username = "Alex", password = "x")
        val puente = directo.copy(host = "2001:470:1f06:a1::2", port = 18766)
        assertTrue(Biblioteca.mismoUsuario(Biblioteca.firma(directo), puente))
        assertFalse(Biblioteca.mismoUsuario(Biblioteca.firma(directo), puente.copy(username = "otro")))
        // Usuarios que son correos: el usuario va antes de la ULTIMA arroba.
        val correo = Account(host = "203.0.113.10", username = "ana@example.com", password = "x")
        assertTrue(Biblioteca.mismoUsuario(Biblioteca.firma(correo), correo.copy(host = "2001:db8::2")))
        assertFalse(Biblioteca.mismoUsuario("", correo))
    }
}
