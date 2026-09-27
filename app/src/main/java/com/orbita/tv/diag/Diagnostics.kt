package com.orbita.tv.diag

import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.VariantOrder
import com.orbita.tv.net.Http
import com.orbita.tv.net.Xtream
import com.orbita.tv.player.StreamVariants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class Level { OK, WARN, FAIL, INFO }

data class Finding(
    val step: String,
    val level: Level,
    val detail: String,
)

data class DiagReport(
    val findings: List<Finding>,
    val verdict: String,
    val advice: List<String>,
)

/**
 * Las cinco causas por las que un panel Xtream falla en Starlink mientras
 * YouTube anda perfecto. Cada prueba apunta a una de ellas, para no quedarse en
 * "no carga".
 */
private enum class Cause {
    IPV6_ROTO,      // el panel publica AAAA que no atiende
    BLOQUEO_IP,     // el proveedor bloquea el rango de Starlink (CGNAT compartido)
    CUENTA_OCUPADA, // el panel ya tiene abiertas todas las conexiones permitidas
    PUERTO_CERRADO, // el puerto raro del panel no responde
    ENLACE_INESTABLE, // conecta pero el caudal se corta a cada rato
    PUERTO_ALTERNATIVO, // el puerto configurado no pasa, pero otro del mismo panel si
    SOLO_ESTE_SERVIDOR, // hay internet desde la app, pero no camino hasta este servidor
}

object Diagnostics {

    suspend fun run(settings: AppSettings, onLine: (Finding) -> Unit): DiagReport =
        withContext(Dispatchers.IO) {
            val findings = ArrayList<Finding>()
            val causes = LinkedHashSet<Cause>()
            val account = settings.account
            val net = settings.net

            fun emit(step: String, level: Level, detail: String) {
                val f = Finding(step, level, detail)
                findings.add(f)
                onLine(f)
            }

            if (account.isEmpty) {
                emit("Cuenta", Level.FAIL, "Faltan los datos del servidor.")
                return@withContext DiagReport(findings, "Sin datos que probar", emptyList())
            }

            // ---------- 1. Resolucion de nombres ----------
            var v4: List<InetAddress> = emptyList()
            var v6: List<InetAddress> = emptyList()
            val dnsMs = measure {
                val all = runCatching { InetAddress.getAllByName(account.host).toList() }
                    .getOrDefault(emptyList())
                v4 = all.filterIsInstance<Inet4Address>()
                v6 = all.filterIsInstance<Inet6Address>()
            }
            if (v4.isEmpty() && v6.isEmpty()) {
                emit(
                    "Nombre del servidor", Level.FAIL,
                    "El DNS no devolvio ninguna direccion para " + account.host + ". " +
                        "Prueba activando DNS sobre HTTPS en Ajustes."
                )
                causes.add(Cause.PUERTO_CERRADO)
            } else {
                val txt = StringBuilder()
                txt.append("IPv4: ").append(if (v4.isEmpty()) "ninguna" else v4.joinToString { it.hostAddress ?: "?" })
                txt.append(" · IPv6: ").append(if (v6.isEmpty()) "ninguna" else v6.joinToString { it.hostAddress ?: "?" })
                txt.append(" · ").append(dnsMs).append(" ms")
                emit("Nombre del servidor", if (dnsMs > 2000) Level.WARN else Level.OK, txt.toString())
            }

            // ---------- 2. Puerto, por familia de direcciones ----------
            // Aqui se separa IPv4 de IPv6 a mano: es la unica forma de ver el caso
            // "el panel anuncia IPv6 pero solo escucha en IPv4", que en Starlink es
            // una causa frecuente de canales que nunca arrancan.
            var v4Reachable = false
            for (addr in v4.take(2)) {
                val (ok, ms, err) = tcp(addr, account.port)
                v4Reachable = v4Reachable || ok
                emit(
                    "Puerto " + account.port + " por IPv4",
                    if (ok) Level.OK else Level.FAIL,
                    if (ok) (addr.hostAddress + " respondio en " + ms + " ms")
                    else (addr.hostAddress + ": " + err),
                )
            }
            var v6Reachable = false
            for (addr in v6.take(2)) {
                val (ok, ms, err) = tcp(addr, account.port)
                v6Reachable = v6Reachable || ok
                emit(
                    "Puerto " + account.port + " por IPv6",
                    if (ok) Level.OK else Level.WARN,
                    if (ok) (addr.hostAddress + " respondio en " + ms + " ms")
                    else (addr.hostAddress + ": " + err),
                )
            }
            if (v6.isNotEmpty() && !v6Reachable && v4Reachable) {
                causes.add(Cause.IPV6_ROTO)
                emit(
                    "Comparacion IPv4 / IPv6", Level.FAIL,
                    "El servidor publica IPv6 pero no atiende ahi, y si atiende por " +
                        "IPv4. Con Starlink el TV intenta IPv6 primero y se queda colgado. " +
                        "Mantener activado \"Forzar IPv4\"."
                )
            }
            if (!v4Reachable && !v6Reachable) {
                causes.add(Cause.PUERTO_CERRADO)
                // No basta con decir que el puerto no responde: el mismo panel
                // suele escuchar tambien en uno estandar. Buscarlo convierte el
                // diagnostico en una solucion.
                val otro = com.orbita.tv.net.Xtream.puertoQueResponde(account, net)
                if (otro != null) {
                    causes.add(Cause.PUERTO_ALTERNATIVO)
                    emit(
                        "Otro puerto que si responde", Level.OK,
                        "El puerto " + account.port + " no abre desde esta red, pero el " +
                            otro + " del mismo servidor contesta con la misma cuenta. " +
                            "La app se cambia sola al recargar; tambien puedes ponerlo a mano."
                    )
                } else {
                    emit(
                        "Otros puertos", Level.FAIL,
                        "Se probaron los puertos habituales y ninguno responde desde esta red."
                    )
                }

                // Prueba de control. Sin esto, "el puerto no responde" deja sin
                // resolver la pregunta que importa: si esta app no puede abrir
                // NINGUNA conexion desde este aparato, o si puede abrirlas a todos
                // lados menos a este servidor. Son causas opuestas y hasta ahora
                // habia que adivinar cual era.
                val controles = listOf(
                    "1.1.1.1" to "Cloudflare",
                    "8.8.8.8" to "Google",
                )
                var controlOk = 0
                for ((ip, nombre) in controles) {
                    val r = tcp(InetAddress.getByName(ip), 80)
                    if (r.first) controlOk++
                    emit(
                        "Control · " + nombre,
                        if (r.first) Level.OK else Level.WARN,
                        if (r.first) ("responde en " + r.second + " ms")
                        else ("no responde: " + r.third),
                    )
                }
                if (controlOk > 0) {
                    causes.add(Cause.SOLO_ESTE_SERVIDOR)
                    emit(
                        "Conclusion del control", Level.FAIL,
                        "Esta app SI abre conexiones a internet desde este aparato, pero " +
                            "no a " + account.host + ". O sea que no es la app ni el permiso " +
                            "de red: este aparato no tiene camino hasta ese servidor, aunque " +
                            "otros aparatos de la casa si lo tengan."
                    )
                } else {
                    emit(
                        "Conclusion del control", Level.FAIL,
                        "Esta app no consigue abrir NINGUNA conexion, ni siquiera a " +
                            "servidores publicos. El problema es la red del aparato o los " +
                            "permisos, no el proveedor."
                    )
                }
            }

            // ---------- 3. La cuenta, segun el propio panel ----------
            val xt = Xtream(account, net)
            val status = runCatching { xt.status() }.getOrElse { e ->
                emit("Cuenta en el panel", Level.FAIL, e.message ?: "sin respuesta")
                if ((e as? com.orbita.tv.net.XtreamException)?.httpCode == 403) {
                    causes.add(Cause.BLOQUEO_IP)
                }
                null
            }
            if (status != null) {
                emit(
                    "Cuenta en el panel",
                    if (status.authOk && status.status.lowercase() == "active") Level.OK else Level.FAIL,
                    "Autenticacion: " + (if (status.authOk) "correcta" else "rechazada") +
                        " · estado: " + status.status +
                        " · vence: " + expires(status.expires),
                )
                val cons = "Conexiones: " + status.activeConnections + " en uso de " +
                    status.maxConnections + " permitidas"
                if (status.maxConnections in 1..status.activeConnections) {
                    causes.add(Cause.CUENTA_OCUPADA)
                    emit(
                        "Conexiones simultaneas", Level.FAIL,
                        cons + ". El panel no te va a dar otro canal hasta que cierres " +
                            "el que quedo abierto en otro aparato. Esto se ve igual que " +
                            "un problema de red y no lo es."
                    )
                } else {
                    emit("Conexiones simultaneas", Level.OK, cons)
                }
            }

            // ---------- 4. La lista de canales ----------
            var firstId = -1
            if (status != null && status.authOk) {
                val chans = runCatching { xt.channels() }.getOrElse { e ->
                    emit("Lista de canales", Level.FAIL, e.message ?: "sin respuesta")
                    causes.add(Cause.BLOQUEO_IP)
                    emptyList()
                }
                if (chans.isEmpty()) {
                    emit(
                        "Lista de canales", Level.FAIL,
                        "El panel autentica pero devuelve cero canales. Es el sintoma " +
                            "clasico de bloqueo por pais o por rango de IP: tu salida de " +
                            "Starlink no es tu ciudad, es el punto de presencia."
                    )
                    causes.add(Cause.BLOQUEO_IP)
                } else {
                    firstId = chans.first().streamId
                    emit("Lista de canales", Level.OK, chans.size.toString() + " canales en vivo")
                }
            }

            // ---------- 5. Caudal real del video ----------
            if (firstId > 0) {
                for (variant in StreamVariants.forChannel(account, firstId, VariantOrder.HLS_FIRST).take(2)) {
                    val probe = probeStream(settings, variant.url, seconds = 8)
                    val level = when {
                        probe.httpCode !in 200..299 -> Level.FAIL
                        probe.bytes == 0L -> Level.FAIL
                        probe.maxGapMs > 3000 -> Level.WARN
                        else -> Level.OK
                    }
                    if (probe.httpCode == 403 || probe.httpCode == 401) causes.add(Cause.BLOQUEO_IP)
                    if (level == Level.WARN) causes.add(Cause.ENLACE_INESTABLE)
                    emit(
                        "Caudal · " + variant.label,
                        level,
                        if (probe.httpCode !in 200..299) {
                            "El servidor respondio " + probe.httpCode +
                                (if (probe.error != null) " (" + probe.error + ")" else "")
                        } else {
                            "Primer byte en " + probe.ttfbMs + " ms · " +
                                (probe.bytes / 1024) + " KB en " + probe.seconds + " s · " +
                                probe.kbps + " kbps · pausa mas larga sin datos: " +
                                probe.maxGapMs + " ms"
                        },
                    )
                }
            }

            // ---------- 6. IP publica (opcional, llamada a un tercero) ----------
            if (net.allowIpLookup) {
                val ip = runCatching {
                    val c = Http.client(net, readTimeoutSeconds = 10)
                    val req = Request.Builder().url("https://ipinfo.io/json").build()
                    c.newCall(req).execute().use { it.body?.string() ?: "" }
                }.getOrNull()
                if (ip != null) {
                    val o = runCatching { org.json.JSONObject(ip) }.getOrNull()
                    emit(
                        "Salida a internet", Level.INFO,
                        "IP " + (o?.optString("ip") ?: "?") +
                            " · " + (o?.optString("city") ?: "?") +
                            ", " + (o?.optString("region") ?: "?") +
                            ", " + (o?.optString("country") ?: "?") +
                            " · " + (o?.optString("org") ?: "?") +
                            ". Si la ciudad no es la tuya, el proveedor te ve desde ahi."
                    )
                }
            }

            val (verdict, advice) = conclude(causes, findings)
            DiagReport(findings, verdict, advice)
        }

    // ------------------------------------------------------------------

    private data class Probe(
        val httpCode: Int,
        val ttfbMs: Long,
        val bytes: Long,
        val seconds: Int,
        val kbps: Long,
        val maxGapMs: Long,
        val error: String?,
    )

    /**
     * Descarga el stream de verdad durante unos segundos. La cifra que importa no
     * es el promedio, es maxGapMs: la pausa mas larga sin recibir un solo byte.
     * Un promedio bueno con una pausa de cuatro segundos es exactamente lo que
     * congela la imagen en un reproductor sin vigilante.
     */
    private fun probeStream(settings: AppSettings, url: String, seconds: Int): Probe {
        val client = Http.client(settings.net, readTimeoutSeconds = 12)
        val req = Request.Builder().url(url)
            .header("User-Agent", settings.net.userAgent)
            .build()
        val start = System.currentTimeMillis()
        return try {
            client.newCall(req).execute().use { res ->
                val ttfb = System.currentTimeMillis() - start
                if (!res.isSuccessful) {
                    return Probe(res.code, ttfb, 0, 0, 0, 0, res.message)
                }
                val stream = res.body?.byteStream()
                    ?: return Probe(res.code, ttfb, 0, 0, 0, 0, "cuerpo vacio")
                val buf = ByteArray(32 * 1024)
                var total = 0L
                var maxGap = 0L
                var lastRead = System.currentTimeMillis()
                val deadline = start + seconds * 1000L
                while (System.currentTimeMillis() < deadline) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    val now = System.currentTimeMillis()
                    val gap = now - lastRead
                    if (gap > maxGap) maxGap = gap
                    lastRead = now
                    total += n
                }
                val elapsed = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(1)
                Probe(
                    httpCode = res.code,
                    ttfbMs = ttfb,
                    bytes = total,
                    seconds = elapsed.toInt(),
                    kbps = total * 8 / 1000 / elapsed,
                    maxGapMs = maxGap,
                    error = null,
                )
            }
        } catch (e: Exception) {
            Probe(0, System.currentTimeMillis() - start, 0, 0, 0, 0, e.javaClass.simpleName)
        }
    }

    private fun tcp(addr: InetAddress, port: Int): Triple<Boolean, Long, String> {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(addr, port), 8000)
                Triple(true, System.currentTimeMillis() - start, "")
            }
        } catch (e: Exception) {
            Triple(false, System.currentTimeMillis() - start, e.message ?: e.javaClass.simpleName)
        }
    }

    private inline fun measure(block: () -> Unit): Long {
        val t = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - t
    }

    private fun expires(epoch: String): String {
        val secs = epoch.toLongOrNull() ?: return "sin fecha"
        return java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(secs * 1000))
    }

    private fun conclude(
        causes: Set<Cause>,
        findings: List<Finding>,
    ): Pair<String, List<String>> {
        val advice = ArrayList<String>()
        if (causes.isEmpty()) {
            val anyFail = findings.any { it.level == Level.FAIL }
            return if (anyFail) {
                "Hay fallas sin causa clara" to listOf(
                    "Repite la prueba con \"Forzar IPv4\" y bufer Extremo.",
                    "Si el panel responde pero el video no, prueba el orden TS primero.",
                )
            } else {
                "El enlace y la cuenta estan sanos" to listOf(
                    "Si igual se corta durante el dia, deja el bufer en Satelite: el " +
                        "vigilante reconecta solo y lo veras en el contador del HUD.",
                )
            }
        }
        val headline = when {
            Cause.SOLO_ESTE_SERVIDOR in causes ->
                "Este aparato no tiene camino hasta ese servidor"
            Cause.CUENTA_OCUPADA in causes ->
                "La cuenta esta ocupada, no es la red"
            Cause.BLOQUEO_IP in causes ->
                "El proveedor esta bloqueando la salida de Starlink"
            Cause.IPV6_ROTO in causes ->
                "IPv6 roto del lado del servidor"
            Cause.PUERTO_ALTERNATIVO in causes ->
                "El puerto configurado no pasa por esta red, pero hay otro que si"
            Cause.PUERTO_CERRADO in causes ->
                "El puerto del panel no responde"
            else ->
                "El enlace trae cortes y hay que absorberlos"
        }
        if (Cause.CUENTA_OCUPADA in causes) {
            advice.add("Cierra la app o el aparato que dejo el canal abierto y repite la prueba.")
            advice.add("Si nunca abres dos, pide al proveedor que te suba el limite de conexiones.")
        }
        if (Cause.BLOQUEO_IP in causes) {
            advice.add(
                "Tu IP de salida es la del punto de presencia de Starlink, no la de tu " +
                    "ciudad. Pasale esa IP al proveedor y pidele que la habilite."
            )
            advice.add(
                "Mientras tanto, una VPN con salida en tu pais devuelve la lista de " +
                    "canales, porque el bloqueo es por IP y no algo que la app pueda sortear."
            )
        }
        if (Cause.IPV6_ROTO in causes) {
            advice.add("Deja \"Forzar IPv4\" activado: evita el intento a IPv6 que se cuelga.")
        }
        if (Cause.SOLO_ESTE_SERVIDOR in causes) {
            advice.add(
                "Compara la red: mira la IP de este aparato y la de un telefono donde " +
                    "si funcione. Si no empiezan igual, no estan en la misma red aunque " +
                    "lo parezca; pasa a menudo con repetidores y con redes de invitados."
            )
            advice.add(
                "Revisa en los ajustes del aparato si hay DNS privado, proxy o VPN " +
                    "activados, y en el router si hay filtrado por dispositivo."
            )
        }
        if (Cause.PUERTO_ALTERNATIVO in causes) {
            advice.add(
                "Cambia el puerto por el que si responde. No es culpa del proveedor ni " +
                    "de la cuenta: esta red no deja salir por el puerto que estaba puesto, " +
                    "igual que deja pasar el navegador sin problema."
            )
        } else if (Cause.PUERTO_CERRADO in causes) {
            advice.add("Confirma host y puerto con el proveedor; los paneles usan 8080, 8000 o 25461.")
            advice.add("Si el nombre no resuelve, activa DNS sobre HTTPS en Ajustes.")
        }
        if (Cause.ENLACE_INESTABLE in causes) {
            advice.add("Deja el orden HLS primero: es el unico formato que se recupera solo.")
            advice.add("Sube el bufer a Extremo: 35 segundos de retardo a cambio de no cortarse.")
        }
        return headline to advice
    }
}
