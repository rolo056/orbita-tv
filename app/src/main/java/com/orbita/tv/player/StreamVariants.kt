package com.orbita.tv.player

import com.orbita.tv.data.Account
import com.orbita.tv.data.VariantOrder

enum class Container { HLS, TS, LEGACY }

data class Variant(val container: Container, val url: String, val label: String)

/**
 * Un canal Xtream se puede pedir de tres formas y no todas aguantan igual.
 *
 *  - HLS (.m3u8): el stream viene partido en segmentos. Si se cae uno, el
 *    reproductor lo vuelve a pedir y sigue. Es la unica forma que sobrevive
 *    sola a los cortes de Starlink, y por eso se intenta primero.
 *  - TS (.ts): MPEG-TS crudo sobre UNA conexion TCP que dura horas. Se corta la
 *    conexion y el canal muere; no hay nada que reintentar dentro del stream.
 *    Suele dar mejor imagen y menos retardo, asi que se conserva como segunda
 *    opcion (y como primera para quien prefiera calidad sobre estabilidad).
 *  - Legacy (sin extension): paneles viejos que solo responden a esta ruta.
 *
 * El orden importa: es la escalera que recorre el reproductor cuando algo falla.
 */
object StreamVariants {

    fun forChannel(account: Account, streamId: Int, order: VariantOrder): List<Variant> {
        val root = account.base + "/live/" + enc(account.username) + "/" + enc(account.password)
        val hls = Variant(Container.HLS, "$root/$streamId.m3u8", "HLS (segmentado)")
        val ts = Variant(Container.TS, "$root/$streamId.ts", "TS (directo)")
        val legacy = Variant(
            Container.LEGACY,
            account.base + "/" + enc(account.username) + "/" + enc(account.password) + "/" + streamId,
            "Ruta antigua",
        )
        return when (order) {
            VariantOrder.HLS_FIRST -> listOf(hls, ts, legacy)
            VariantOrder.TS_FIRST -> listOf(ts, hls, legacy)
        }
    }

    /**
     * Una pelicula o un episodio. A diferencia de un canal, es un archivo: tiene
     * una sola forma de pedirse y la extension es parte de la direccion.
     */
    fun forMovie(account: Account, streamId: Int, extension: String): String =
        account.base + "/movie/" + enc(account.username) + "/" + enc(account.password) +
            "/" + streamId + "." + extension

    fun forEpisode(account: Account, episodeId: Int, extension: String): String =
        account.base + "/series/" + enc(account.username) + "/" + enc(account.password) +
            "/" + episodeId + "." + extension

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
