package com.orbita.tv.net

import com.orbita.tv.data.Account
import com.orbita.tv.data.NetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class Category(val id: String, val name: String)

data class Channel(
    val streamId: Int,
    val name: String,
    val number: Int,
    val icon: String?,
    val categoryId: String?,
)

data class AccountStatus(
    val authOk: Boolean,
    val status: String,
    val expires: String,
    val maxConnections: Int,
    val activeConnections: Int,
)

class XtreamException(message: String, val httpCode: Int = 0) : Exception(message)

/**
 * Cliente del API de los paneles Xtream. Se parsea con org.json a proposito: los
 * paneles devuelven el mismo campo como texto o como numero segun la version, y
 * un parser estricto se cae con datos perfectamente validos.
 */
class Xtream(private val account: Account, net: NetSettings) {

    // 12 segundos de tope real por consulta. Sin esto una peticion colgada se
    // come el arranque entero y el plan B nunca llega a ejecutarse.
    private val client = Http.client(net, readTimeoutSeconds = 20, callTimeoutSeconds = 12)
    private val ua = net.userAgent

    private fun api(vararg extra: Pair<String, String>): String {
        val sb = StringBuilder(account.base + "/player_api.php")
        sb.append("?username=").append(enc(account.username))
        sb.append("&password=").append(enc(account.password))
        for ((k, v) in extra) sb.append("&").append(k).append("=").append(enc(v))
        return sb.toString()
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) {
                throw XtreamException("El servidor respondio " + res.code, res.code)
            }
            body
        }
    }

    suspend fun status(): AccountStatus {
        val body = get(api())
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: throw XtreamException("Respuesta ilegible del servidor")
        val info = root.optJSONObject("user_info") ?: JSONObject()
        return AccountStatus(
            authOk = info.optString("auth", "0") == "1" || info.optInt("auth", 0) == 1,
            status = info.optString("status", "desconocido"),
            expires = info.optString("exp_date", ""),
            maxConnections = info.optString("max_connections", "0").toIntOrNull() ?: 0,
            activeConnections = info.optString("active_cons", "0").toIntOrNull() ?: 0,
        )
    }

    suspend fun categories(): List<Category> {
        val arr = array(get(api("action" to "get_live_categories")))
        val out = ArrayList<Category>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Category(o.optString("category_id"), o.optString("category_name")))
        }
        return out
    }

    suspend fun channels(categoryId: String? = null): List<Channel> {
        val url = if (categoryId == null) {
            api("action" to "get_live_streams")
        } else {
            api("action" to "get_live_streams", "category_id" to categoryId)
        }
        val arr = array(get(url))
        val out = ArrayList<Channel>(arr.length())
        // Hay paneles que repiten el mismo canal en la lista. La pantalla usa el
        // id como clave de cada fila, y una clave repetida la cierra de golpe.
        val vistos = HashSet<Int>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id").toIntOrNull() ?: continue
            if (!vistos.add(id)) continue
            val name = o.optString("name")
            val icon = o.optString("stream_icon")
            val cat = o.optString("category_id")
            out.add(
                Channel(
                    streamId = id,
                    name = if (name.isBlank()) "Canal " + id else name,
                    number = o.optString("num").toIntOrNull() ?: (i + 1),
                    icon = if (icon.startsWith("http")) icon else null,
                    categoryId = if (cat.isBlank()) null else cat,
                )
            )
        }
        return out
    }

    /**
     * La lista completa de canales, con plan B.
     *
     * Medido contra un panel real: get_live_streams devuelve 35 KB de una vez y
     * a veces tarda 20 segundos o se cuelga, mientras que la MISMA consulta
     * partida por categoria son 3 KB y responde en 0,2 s siempre. Pedirlo todo
     * junto es depender de la unica peticion que falla, y cuando falla la app
     * queda sin canales aunque el panel este perfecto.
     *
     * Primero se intenta la via rapida con un plazo corto; si no llega a tiempo
     * o vuelve vacia, se arma la lista categoria por categoria. Asi una
     * categoria lenta no se lleva por delante a las demas.
     */
    suspend fun allChannels(cats: List<Category>): List<Channel> {
        // El tope lo pone ahora el propio cliente HTTP, que si corta. Envolverlo
        // en withTimeoutOrNull era inutil: la llamada es bloqueante y seguia
        // corriendo despues de que el plazo venciera.
        val rapido = runCatching { channels(null) }.getOrDefault(emptyList())
        if (rapido.isNotEmpty()) return rapido

        val vistos = LinkedHashMap<Int, Channel>()
        for (cat in cats) {
            val parte = runCatching { channels(cat.id) }.getOrDefault(emptyList())
            // putIfAbsent pide API 24 y la app soporta desde la 21.
            for (c in parte) if (!vistos.containsKey(c.streamId)) vistos[c.streamId] = c
        }
        return vistos.values.toList()
    }

    private fun array(body: String): JSONArray {
        val direct = runCatching { JSONArray(body) }.getOrNull()
        if (direct != null) return direct
        // Algunos paneles avisan del bloqueo con un objeto de error.
        val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
        throw XtreamException(
            if (msg.isNullOrBlank()) "El servidor no devolvio una lista" else msg
        )
    }

    companion object {
        /**
         * Puertos por los que suele responder el mismo panel, en orden de menos
         * a mas probable de estar bloqueado. El 80 primero porque es el que
         * ninguna red bloquea: si el navegador del aparato abre paginas, el 80
         * pasa.
         */
        val PUERTOS_ALTERNATIVOS = listOf(80, 8080, 8000, 443, 2082, 25461)

        /**
         * Busca un puerto por el que el panel si conteste desde ESTA red.
         *
         * Existe por un caso real y medido: el panel escuchaba en 25461 y en 80
         * a la vez, y desde Starlink y desde datos moviles el 25461 no abria
         * mientras el 80 respondia al instante. El sintoma era "no cargan los
         * canales" y la causa no tenia nada que ver con el proveedor ni con la
         * cuenta: era el puerto.
         */
        suspend fun puertoQueResponde(account: Account, net: NetSettings): Int? {
            for (puerto in PUERTOS_ALTERNATIVOS) {
                if (puerto == account.port) continue
                val prueba = account.copy(port = puerto, https = puerto == 443)
                val ok = runCatching { Xtream(prueba, net).status().authOk }.getOrDefault(false)
                if (ok) return puerto
            }
            return null
        }

        /**
         * Traduce el fallo a algo que se pueda actuar. Un
         * "failed to connect to /80.80.90.95 (port 25461) after 15000ms" en
         * pantalla no le dice nada a nadie, y ademas lleva a la conclusion
         * equivocada: parece que el proveedor esta caido cuando lo normal es que
         * la red desde la que se mira no deje salir por ese puerto.
         */
        fun mensajeAmigable(e: Throwable): String = when {
            e is XtreamException && e.httpCode == 401 ->
                "El panel rechazo la cuenta. Revisa usuario y contrasena, o si venció."
            e is XtreamException && e.httpCode == 403 ->
                "El panel rechazo la conexion. Suele ser la cuenta en uso en otro " +
                    "aparato, o el proveedor bloqueando esta IP."
            e is java.net.UnknownHostException ->
                "No se pudo resolver el nombre del servidor. Prueba activando " +
                    "\"Resolver nombres por Cloudflare\" en Ajustes."
            e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "No se pudo abrir la conexion con el servidor. El panel puede estar " +
                    "perfecto y aun asi fallar si ESTA red no deja salir por su puerto. " +
                    "Abre Diagnostico de red: te dice cual de las causas es."
            else -> e.message ?: "No se pudo leer la lista"
        }

        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

        /**
         * Acepta lo que el proveedor manda por correo: la URL de get.php, la de
         * player_api.php, o host:puerto pelado.
         */
        fun parsePasted(text: String): Account? {
            val t = text.trim()
            if (t.isEmpty()) return null
            val withScheme = if (t.contains("://")) t else "http://" + t
            val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            val https = uri.scheme == "https"
            val port = if (uri.port > 0) uri.port else if (https) 443 else 80
            val params = HashMap<String, String>()
            val query = uri.query
            if (query != null) {
                for (pair in query.split("&")) {
                    val p = pair.split("=", limit = 2)
                    if (p.size == 2) {
                        params[p[0].lowercase()] =
                            runCatching { java.net.URLDecoder.decode(p[1], "UTF-8") }
                                .getOrDefault(p[1])
                    }
                }
            }
            return Account(
                host = host,
                port = port,
                https = https,
                username = params["username"] ?: "",
                password = params["password"] ?: "",
            )
        }
    }
}
