package com.orbita.tv.net

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.orbita.tv.data.NetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

/**
 * Actualizacion del propio APK.
 *
 * Lo que se puede cambiar sin reinstalar es la apariencia (ver SkinSource).
 * Todo lo demas (pantallas nuevas, controles nuevos) necesita APK nuevo, y esto
 * existe para que eso cueste un OK con el mando en vez de repetir el baile de
 * Downloader y escribir direcciones con el teclado en pantalla.
 *
 * El numero de version lo pone la compilacion, asi que es monotono y comparar
 * enteros alcanza: no hay que interpretar fechas ni nombres de version.
 */
object Updater {

    private const val PAQUETE_PUBLICADO = "com.orbita.tv"

    private const val VERSION_URL =
        "https://github.com/rolo056/orbita-tv/releases/download/ultima/version.json"

    /** Devuelve la version nueva, o null si ya estamos al dia o no se pudo saber. */
    suspend fun check(ctx: Context, net: NetSettings): UpdateInfo? = withContext(Dispatchers.IO) {
        // La variante de prueba no se actualiza sola: lo publicado es la otra
        // app, y ofrecerla desde aca instalaria esa en vez de actualizar esta.
        if (ctx.packageName != PAQUETE_PUBLICADO) return@withContext null
        val local = localVersionCode(ctx)
        val raw = runCatching {
            val client = Http.client(net, readTimeoutSeconds = 10)
            val req = Request.Builder().url(VERSION_URL)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null

        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
        val remote = o.optInt("versionCode", -1)
        if (remote <= local) return@withContext null
        val apk = o.optString("apk")
        if (!apk.startsWith("http")) return@withContext null
        UpdateInfo(
            versionCode = remote,
            versionName = o.optString("versionName", remote.toString()),
            apkUrl = apk,
            notes = o.optString("notas", ""),
        )
    }

    suspend fun download(ctx: Context, info: UpdateInfo, net: NetSettings): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = Http.client(net, readTimeoutSeconds = 120)
                val req = Request.Builder().url(info.apkUrl).build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val bytes = res.body?.bytes() ?: return@use null
                    // Un APK truncado hace que Android muestre "aplicacion no
                    // instalada" sin decir por que. Mejor descartarlo aca.
                    if (bytes.size < 500_000) return@use null
                    val dir = File(ctx.cacheDir, "apk").apply { mkdirs() }
                    val file = File(dir, "orbita-" + info.versionCode + ".apk")
                    file.writeBytes(bytes)
                    file
                }
            }.getOrNull()
        }

    /**
     * Lanza el instalador de Android. Siempre pide confirmacion al usuario: no
     * existe forma de instalar en silencio sin ser aplicacion de sistema, y
     * tampoco seria deseable.
     */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
    }

    private fun localVersionCode(ctx: Context): Int = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        info.versionCode
    }.getOrDefault(0)
}
