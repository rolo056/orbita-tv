package com.orbita.tv.net

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.orbita.tv.data.NetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * De donde sale la apariencia.
 *
 * Tres capas, en este orden, y ninguna puede dejar la pantalla inservible:
 *  1. Lo que trae el APK (Skin.DEFAULT), que siempre funciona.
 *  2. Lo ultimo que se descargo bien, guardado en disco. Asi la app arranca con
 *     el diseno correcto aunque el servidor del tema este caido o el televisor
 *     todavia no tenga red.
 *  3. Lo que responde el servidor ahora.
 */
object SkinSource {

    private const val CACHE_FILE = "tema.json"

    fun cached(ctx: Context): String? {
        val f = File(ctx.filesDir, CACHE_FILE)
        return if (f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    private fun store(ctx: Context, raw: String) {
        runCatching { File(ctx.filesDir, CACHE_FILE).writeText(raw) }
    }

    /**
     * Devuelve el JSON crudo, o null si no se pudo traer. Guarda en disco solo
     * lo que llego completo: un tema a medias nunca reemplaza al anterior.
     */
    suspend fun fetch(ctx: Context, url: String, net: NetSettings): String? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            runCatching {
                val client = Http.client(net, readTimeoutSeconds = 10)
                val req = Request.Builder().url(url)
                    .header("User-Agent", net.userAgent)
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val body = res.body?.string()
                    if (body.isNullOrBlank()) null else {
                        store(ctx, body)
                        body
                    }
                }
            }.getOrNull()
        }

    /**
     * Baja una tipografia y la deja lista para Compose. Se guarda por nombre de
     * archivo derivado de la URL, asi que cambiar la URL trae fuente nueva y
     * repetirla no vuelve a descargar nada.
     */
    suspend fun font(ctx: Context, url: String?, net: NetSettings): FontFamily? =
        withContext(Dispatchers.IO) {
            if (url.isNullOrBlank()) return@withContext null
            val name = "fuente-" + url.hashCode().toString().replace("-", "n") + ".ttf"
            val file = File(ctx.cacheDir, name)
            if (!file.isFile || file.length() == 0L) {
                val ok = runCatching {
                    val client = Http.client(net, readTimeoutSeconds = 20)
                    val req = Request.Builder().url(url)
                        .header("User-Agent", net.userAgent)
                        .build()
                    client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use false
                        val bytes = res.body?.bytes() ?: return@use false
                        if (bytes.size < 1024) return@use false // no es una fuente
                        file.writeBytes(bytes)
                        true
                    }
                }.getOrDefault(false)
                if (!ok) return@withContext null
            }
            // Una fuente corrupta hace que Compose falle al dibujar cualquier
            // texto, asi que se descarta en silencio y se sigue con la de fabrica.
            runCatching { FontFamily(Font(file)) }.getOrNull()
        }
}
