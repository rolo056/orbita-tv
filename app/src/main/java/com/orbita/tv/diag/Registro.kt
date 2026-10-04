package com.orbita.tv.diag

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Una falla, con la foto de como estaba ESTE aparato en ese momento.
 *
 * Los milisegundos son lo que tardo en abrirse una conexion; null es que no se
 * abrio, y entonces el texto de al lado dice por que.
 */
data class Falla(
    val cuando: Long,
    val que: String,
    val motivo: String,
    val proveedorMs: Int?,
    val proveedorError: String?,
    val ipv4Ms: Int?,
    val ipv4Error: String?,
    val ipv6Ms: Int?,
    val ipv6Error: String?,
    /** "wifi", "cable", "datos moviles", "sin red" u "otra". */
    val red: String,
    val rssi: Int?,
    val enlaceMbps: Int?,
    val frecuenciaMhz: Int?,
    val tieneIpv4: Boolean,
    val tieneIpv6: Boolean,
    val memoriaLibreMb: Int,
    val pocaMemoria: Boolean,
)

/**
 * El registro de fallas.
 *
 * Existe por un televisor que falla a ratos y de una forma que no se deja
 * atrapar: los canales no dan, YouTube y Chrome si, y un telefono en la misma
 * red tampoco tiene problema. Cuando alguien llega a mirar, ya paso. Una prueba
 * hecha despues, con todo andando, no dice nada.
 *
 * Asi que el aparato se saca una foto a si mismo en el momento en que algo
 * falla: si llega al proveedor, si tiene internet por IPv4 y por IPv6 (YouTube
 * anda por IPv6 y los proveedores de IPTV solo tienen IPv4, de modo que perder
 * solo IPv4 se ve exactamente como "YouTube si, canales no"), como esta la señal
 * del wifi y cuanta memoria queda. Eso se guarda y se lee despues en
 * Diagnostico.
 */
object Registro {
    private const val ARCHIVO = "registro-fallas.jsonl"
    private const val TOPE = 60

    /** Entre fotos, para que una escalera de reintentos no llene el registro. */
    private const val PAUSA_MS = 20_000L
    private const val ESPERA_MS = 4_000

    // Direcciones escritas con numeros: no dependen del DNS, que es otra cosa
    // que puede fallar y se mediria sin querer.
    private const val NEUTRAL_IPV4 = "1.1.1.1"
    private const val NEUTRAL_IPV6 = "2606:4700:4700::1111"

    private val candado = Any()

    @Volatile
    private var ultima = 0L

    /**
     * Anota una falla. No bloquea: la foto se saca aparte y tarda unos segundos.
     *
     * [siempre] es para lo que no se puede perder (un canal que se rindio, la
     * lista que no cargo); lo demas respeta una pausa entre fotos.
     */
    fun anotar(
        ctx: Context,
        scope: CoroutineScope,
        host: String,
        port: Int,
        que: String,
        motivo: String,
        siempre: Boolean = false,
    ) {
        val ahora = SystemClock.elapsedRealtime()
        if (!siempre && ultima != 0L && ahora - ultima < PAUSA_MS) return
        ultima = ahora
        val app = ctx.applicationContext
        val cuando = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            runCatching { guardar(app.filesDir, medir(app, cuando, host, port, que, motivo)) }
        }
    }

    /** Las fallas guardadas, la mas reciente primero. */
    fun leer(ctx: Context): List<Falla> = leer(ctx.applicationContext.filesDir)

    internal fun leer(carpeta: File): List<Falla> = synchronized(candado) {
        val archivo = File(carpeta, ARCHIVO)
        if (!archivo.exists()) return emptyList()
        archivo.readLines().mapNotNull { linea -> runCatching { deJson(linea) }.getOrNull() }.reversed()
    }

    internal fun guardar(carpeta: File, falla: Falla) = synchronized(candado) {
        val archivo = File(carpeta, ARCHIVO)
        val lineas = if (archivo.exists()) archivo.readLines().filter { it.isNotBlank() } else emptyList()
        val nuevas = (lineas + aJson(falla)).takeLast(TOPE)
        archivo.writeText(nuevas.joinToString("\n") + "\n")
    }

    private suspend fun medir(
        app: Context,
        cuando: Long,
        host: String,
        port: Int,
        que: String,
        motivo: String,
    ): Falla = coroutineScope {
        val proveedor = async(Dispatchers.IO) { conectar(host, port) }
        val v4 = async(Dispatchers.IO) { conectar(NEUTRAL_IPV4, 443) }
        val v6 = async(Dispatchers.IO) { conectar(NEUTRAL_IPV6, 443) }
        val (tieneV4, tieneV6) = direcciones()
        val wifi = wifi(app)
        val memoria = memoria(app)
        Falla(
            cuando = cuando,
            que = que,
            motivo = motivo,
            proveedorMs = proveedor.await().first,
            proveedorError = proveedor.await().second,
            ipv4Ms = v4.await().first,
            ipv4Error = v4.await().second,
            ipv6Ms = v6.await().first,
            ipv6Error = v6.await().second,
            red = tipoDeRed(app),
            rssi = wifi?.first,
            enlaceMbps = wifi?.second,
            frecuenciaMhz = wifi?.third,
            tieneIpv4 = tieneV4,
            tieneIpv6 = tieneV6,
            memoriaLibreMb = memoria.first,
            pocaMemoria = memoria.second,
        )
    }

    /** Abre y cierra una conexion: (milisegundos, null) si abrio, (null, por que) si no. */
    private fun conectar(host: String, port: Int): Pair<Int?, String?> {
        if (host.isBlank()) return null to "sin servidor"
        val inicio = SystemClock.elapsedRealtime()
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(InetAddress.getByName(host), port), ESPERA_MS) }
            (SystemClock.elapsedRealtime() - inicio).toInt() to null
        } catch (e: java.net.SocketTimeoutException) {
            null to "sin respuesta en " + (ESPERA_MS / 1000) + " s"
        } catch (e: java.net.UnknownHostException) {
            null to "el nombre no se resolvió"
        } catch (e: java.net.ConnectException) {
            // "Network is unreachable" es no tener por donde salir; "refused" es
            // que del otro lado no hay nadie escuchando.
            val texto = e.message.orEmpty().lowercase()
            null to when {
                "unreachable" in texto || "enetunreach" in texto || "ehostunreach" in texto -> "sin salida"
                "refused" in texto -> "conexión rechazada"
                else -> "no conectó"
            }
        } catch (e: Exception) {
            null to "no conectó"
        }
    }

    /** Si el aparato tiene direccion IPv4 y direccion IPv6 de internet. */
    private fun direcciones(): Pair<Boolean, Boolean> {
        var v4 = false
        var v6 = false
        runCatching {
            for (interfaz in NetworkInterface.getNetworkInterfaces()) {
                if (!interfaz.isUp || interfaz.isLoopback) continue
                for (d in interfaz.inetAddresses) {
                    if (d.isLoopbackAddress || d.isLinkLocalAddress) continue
                    if (d is Inet4Address) v4 = true
                    if (d is Inet6Address) v6 = true
                }
            }
        }
        return v4 to v6
    }

    @Suppress("DEPRECATION")
    private fun tipoDeRed(app: Context): String = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = cm.activeNetworkInfo ?: return@runCatching "sin red"
        when (info.type) {
            ConnectivityManager.TYPE_WIFI -> "wifi"
            ConnectivityManager.TYPE_ETHERNET -> "cable"
            ConnectivityManager.TYPE_MOBILE -> "datos móviles"
            else -> "otra"
        }
    }.getOrDefault("otra")

    /** Señal, velocidad del enlace y frecuencia del wifi; null si no hay wifi. */
    @Suppress("DEPRECATION")
    private fun wifi(app: Context): Triple<Int, Int, Int>? = runCatching<Triple<Int, Int, Int>?> {
        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo ?: return@runCatching null
        if (info.networkId == -1 || info.rssi <= -127) return@runCatching null
        Triple(info.rssi, info.linkSpeed, info.frequency)
    }.getOrNull()

    private fun memoria(app: Context): Pair<Int, Boolean> = runCatching {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.availMem / (1024 * 1024)).toInt() to info.lowMemory
    }.getOrDefault(0 to false)

    // ------------------------------------------------------------ en palabras

    /** Lo que la foto dice, en una linea: de quien fue la falla. */
    fun veredicto(f: Falla): String {
        val conclusion = when {
            f.red == "sin red" -> "Este aparato estaba sin red"
            !f.tieneIpv4 && f.tieneIpv6 ->
                "Este aparato se quedó sin dirección IPv4 y con IPv6: YouTube anda, los canales no"
            f.ipv4Ms == null && f.ipv6Ms != null ->
                "Sin salida por IPv4 y con IPv6: YouTube anda, los canales no"
            f.ipv4Ms == null ->
                "Este aparato estaba sin internet"
            f.proveedorMs == null ->
                "Internet andaba, pero el proveedor no le respondió a este aparato"
            else ->
                "La red llegaba al proveedor: falló su respuesta o el video"
        }
        val agravantes = mutableListOf<String>()
        val senal = f.rssi
        if (senal != null && senal <= WIFI_DEBIL) agravantes += "wifi débil ($senal dBm)"
        if (f.pocaMemoria) agravantes += "poca memoria"
        return (listOf(conclusion) + agravantes).joinToString(" · ")
    }

    /** Es grave lo que deja al aparato sin llegar; lo demas es un aviso. */
    fun esGrave(f: Falla): Boolean = f.proveedorMs == null || f.ipv4Ms == null

    /** Las medidas, para quien quiera verlas. */
    fun medidas(f: Falla): String {
        fun tramo(ms: Int?, error: String?) = if (ms != null) "$ms ms" else (error ?: "no conectó")
        val partes = mutableListOf(
            "Proveedor: " + tramo(f.proveedorMs, f.proveedorError),
            "IPv4: " + tramo(f.ipv4Ms, f.ipv4Error),
            "IPv6: " + tramo(f.ipv6Ms, f.ipv6Error),
        )
        partes += when {
            f.rssi != null -> {
                val banda = f.frecuenciaMhz?.let { if (it >= 4900) "5 GHz" else "2,4 GHz" }
                listOfNotNull("wifi " + f.rssi + " dBm", f.enlaceMbps?.let { "$it Mbps" }, banda).joinToString(", ")
            }
            else -> f.red
        }
        partes += "memoria libre " + f.memoriaLibreMb + " MB"
        return partes.joinToString(" · ")
    }

    /** "sáb 3/10 · 21:07". */
    fun hora(f: Falla): String =
        SimpleDateFormat("EEE d/M · HH:mm", Locale("es")).format(Date(f.cuando))

    private const val WIFI_DEBIL = -75

    // -------------------------------------------------------------- guardado

    internal fun aJson(f: Falla): String = JSONObject().apply {
        put("cuando", f.cuando)
        put("que", f.que)
        put("motivo", f.motivo)
        putOpt("proveedorMs", f.proveedorMs)
        putOpt("proveedorError", f.proveedorError)
        putOpt("ipv4Ms", f.ipv4Ms)
        putOpt("ipv4Error", f.ipv4Error)
        putOpt("ipv6Ms", f.ipv6Ms)
        putOpt("ipv6Error", f.ipv6Error)
        put("red", f.red)
        putOpt("rssi", f.rssi)
        putOpt("enlaceMbps", f.enlaceMbps)
        putOpt("frecuenciaMhz", f.frecuenciaMhz)
        put("tieneIpv4", f.tieneIpv4)
        put("tieneIpv6", f.tieneIpv6)
        put("memoriaLibreMb", f.memoriaLibreMb)
        put("pocaMemoria", f.pocaMemoria)
    }.toString()

    internal fun deJson(linea: String): Falla {
        val o = JSONObject(linea)
        fun entero(clave: String): Int? = if (o.has(clave)) o.getInt(clave) else null
        fun texto(clave: String): String? = if (o.has(clave)) o.getString(clave) else null
        return Falla(
            cuando = o.getLong("cuando"),
            que = o.optString("que"),
            motivo = o.optString("motivo"),
            proveedorMs = entero("proveedorMs"),
            proveedorError = texto("proveedorError"),
            ipv4Ms = entero("ipv4Ms"),
            ipv4Error = texto("ipv4Error"),
            ipv6Ms = entero("ipv6Ms"),
            ipv6Error = texto("ipv6Error"),
            red = o.optString("red", "otra"),
            rssi = entero("rssi"),
            enlaceMbps = entero("enlaceMbps"),
            frecuenciaMhz = entero("frecuenciaMhz"),
            tieneIpv4 = o.optBoolean("tieneIpv4", true),
            tieneIpv6 = o.optBoolean("tieneIpv6", false),
            memoriaLibreMb = o.optInt("memoriaLibreMb", 0),
            pocaMemoria = o.optBoolean("pocaMemoria", false),
        )
    }
}
