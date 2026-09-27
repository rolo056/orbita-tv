package com.orbita.tv.net

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
     * La tipografia, en dos pesos.
     *
     * Aqui esta la unica parte del tema que podria romper la app de verdad, y
     * por eso lleva dos cerrojos. Compose no interpreta un archivo de fuente al
     * crearlo, sino la primera vez que dibuja texto con el. Un archivo corrupto
     * —una descarga cortada, o una pagina de error guardada como si fuera una
     * fuente— reventaria al pintar, y como la tipografia se aplica a toda la
     * app, se llevaria por delante hasta la pantalla de Ajustes: no habria forma
     * de deshacerlo desde el televisor.
     *
     * Cerrojo 1: comprobar la firma del archivo. Una fuente real empieza por una
     * de cuatro marcas conocidas. Una pagina de error no.
     * Cerrojo 2: obligar a Android a interpretarla AHORA. Si no es valida, falla
     * aqui, en silencio, y se sigue con la del sistema.
     *
     * El peso normal es obligatorio: con solo el pesado, el texto secundario
     * quedaria ilegible. Si falta, no se aplica ninguna.
     */
    suspend fun font(
        ctx: Context,
        regularUrl: String?,
        boldUrl: String?,
        net: NetSettings,
    ): FontFamily? = withContext(Dispatchers.IO) {
        val regular = fuenteValida(ctx, regularUrl, net) ?: return@withContext null
        val bold = fuenteValida(ctx, boldUrl, net)
        val fuentes = ArrayList<Font>(2)
        fuentes.add(Font(regular, FontWeight.Normal))
        if (bold != null) fuentes.add(Font(bold, FontWeight.Black))
        runCatching { FontFamily(fuentes) }.getOrNull()
    }

    private fun fuenteValida(ctx: Context, url: String?, net: NetSettings): File? {
        if (url.isNullOrBlank()) return null
        val nombre = "fuente-" + url.hashCode().toString().replace("-", "n") + ".ttf"
        val file = File(ctx.cacheDir, nombre)

        if (!file.isFile || file.length() < 4096) {
            val ok = runCatching {
                val client = Http.client(net, readTimeoutSeconds = 30)
                val req = Request.Builder().url(url)
                    .header("User-Agent", net.userAgent)
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use false
                    val bytes = res.body?.bytes() ?: return@use false
                    if (bytes.size < 4096) return@use false
                    file.writeBytes(bytes)
                    true
                }
            }.getOrDefault(false)
            if (!ok) {
                file.delete()
                return null
            }
        }

        if (!pareceFuente(file) || !interpretable(file)) {
            // Se borra para que el proximo intento vuelva a descargarla en vez
            // de quedarse con un archivo malo para siempre.
            file.delete()
            return null
        }
        return file
    }

    /** Las cuatro firmas con las que empieza un archivo de fuente real. */
    private fun pareceFuente(f: File): Boolean = runCatching {
        val b = ByteArray(4)
        f.inputStream().use { if (it.read(b) != 4) return false }
        val firma = ((b[0].toInt() and 0xFF) shl 24) or
            ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or
            (b[3].toInt() and 0xFF)
        firma == 0x00010000 ||       // TrueType
            firma == 0x4F54544F ||   // "OTTO", OpenType con contornos CFF
            firma == 0x74727565 ||   // "true"
            firma == 0x74746366      // "ttcf", coleccion
    }.getOrDefault(false)

    /** Obliga a Android a interpretarla ya, para que no falle al dibujar. */
    private fun interpretable(f: File): Boolean =
        runCatching { Typeface.createFromFile(f) != null }.getOrDefault(false)
}
