package com.orbita.tv

import com.orbita.tv.diag.Falla
import com.orbita.tv.diag.Registro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * El registro de fallas: lo que concluye de cada foto, y que lo guardado vuelve
 * igual. La foto misma (conexiones, wifi, memoria) solo se puede sacar en un
 * aparato de verdad; aca se prueba lo que se hace con ella.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegistroTest {

    /** Una falla con todo en orden: se le cambia lo que cada prueba necesita. */
    private fun falla(
        proveedorMs: Int? = 140,
        ipv4Ms: Int? = 30,
        ipv6Ms: Int? = 35,
        red: String = "wifi",
        rssi: Int? = -55,
        tieneIpv4: Boolean = true,
        tieneIpv6: Boolean = true,
        pocaMemoria: Boolean = false,
        cuando: Long = 1_791_000_000_000,
        que: String = "Canal 5 El Lider · HLS (segmentado)",
    ) = Falla(
        cuando = cuando,
        que = que,
        motivo = "El servidor dejo de responder · reintento 1",
        proveedorMs = proveedorMs,
        proveedorError = if (proveedorMs == null) "sin respuesta en 4 s" else null,
        ipv4Ms = ipv4Ms,
        ipv4Error = if (ipv4Ms == null) "sin salida" else null,
        ipv6Ms = ipv6Ms,
        ipv6Error = if (ipv6Ms == null) "sin salida" else null,
        red = red,
        rssi = rssi,
        enlaceMbps = if (rssi != null) 72 else null,
        frecuenciaMhz = if (rssi != null) 2437 else null,
        tieneIpv4 = tieneIpv4,
        tieneIpv6 = tieneIpv6,
        memoriaLibreMb = 412,
        pocaMemoria = pocaMemoria,
    )

    @Test
    fun `si la red llegaba al proveedor, la falla fue de su respuesta o del video`() {
        val f = falla()
        assertEquals("La red llegaba al proveedor: falló su respuesta o el video", Registro.veredicto(f))
        assertFalse(Registro.esGrave(f))
    }

    @Test
    fun `internet si y proveedor no`() {
        val f = falla(proveedorMs = null)
        assertEquals("Internet andaba, pero el proveedor no le respondió a este aparato", Registro.veredicto(f))
        assertTrue(Registro.esGrave(f))
    }

    @Test
    fun `sin IPv4 y con IPv6 es el caso de YouTube si y canales no`() {
        assertEquals(
            "Sin salida por IPv4 y con IPv6: YouTube anda, los canales no",
            Registro.veredicto(falla(proveedorMs = null, ipv4Ms = null)),
        )
        assertEquals(
            "Este aparato se quedó sin dirección IPv4 y con IPv6: YouTube anda, los canales no",
            Registro.veredicto(falla(proveedorMs = null, ipv4Ms = null, tieneIpv4 = false)),
        )
    }

    @Test
    fun `sin IPv4 ni IPv6 es el aparato sin internet`() {
        assertEquals(
            "Este aparato estaba sin internet",
            Registro.veredicto(falla(proveedorMs = null, ipv4Ms = null, ipv6Ms = null)),
        )
        assertEquals(
            "Este aparato estaba sin red",
            Registro.veredicto(falla(proveedorMs = null, ipv4Ms = null, ipv6Ms = null, red = "sin red", rssi = null)),
        )
    }

    @Test
    fun `el wifi debil y la poca memoria se dicen ademas de la conclusion`() {
        assertEquals(
            "La red llegaba al proveedor: falló su respuesta o el video · wifi débil (-81 dBm) · poca memoria",
            Registro.veredicto(falla(rssi = -81, pocaMemoria = true)),
        )
    }

    @Test
    fun `las medidas se leen de corrido`() {
        assertEquals(
            "Proveedor: 140 ms · IPv4: 30 ms · IPv6: sin salida · wifi -55 dBm, 72 Mbps, 2,4 GHz · memoria libre 412 MB",
            Registro.medidas(falla(ipv6Ms = null)),
        )
        assertEquals(
            "Proveedor: sin respuesta en 4 s · IPv4: 30 ms · IPv6: 35 ms · cable · memoria libre 412 MB",
            Registro.medidas(falla(proveedorMs = null, red = "cable", rssi = null)),
        )
    }

    @Test
    fun `lo guardado vuelve igual, lo mas reciente primero`() {
        val carpeta = Files.createTempDirectory("registro").toFile()
        val primera = falla(cuando = 1_000, proveedorMs = null, rssi = null, red = "cable")
        val segunda = falla(cuando = 2_000, que = "Lista de canales")
        Registro.guardar(carpeta, primera)
        Registro.guardar(carpeta, segunda)
        assertEquals(listOf(segunda, primera), Registro.leer(carpeta))
    }

    @Test
    fun `se guardan las ultimas 60 y una linea rota no rompe la lectura`() {
        val carpeta = Files.createTempDirectory("registro").toFile()
        for (i in 1..70) Registro.guardar(carpeta, falla(cuando = i.toLong()))
        val leidas = Registro.leer(carpeta)
        assertEquals(60, leidas.size)
        assertEquals(70L, leidas.first().cuando)
        assertEquals(11L, leidas.last().cuando)

        File(carpeta, "registro-fallas.jsonl").appendText("esto no es una falla\n")
        assertEquals(60, Registro.leer(carpeta).size)
    }

    @Test
    fun `sin archivo no hay fallas`() {
        assertTrue(Registro.leer(Files.createTempDirectory("vacio").toFile()).isEmpty())
    }
}
