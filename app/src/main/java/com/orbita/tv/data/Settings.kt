package com.orbita.tv.data

/** Perfil del servidor Xtream + las palancas de red que arreglan Starlink. */
data class Account(
    val host: String = "",
    val port: Int = 80,
    val https: Boolean = false,
    val username: String = "",
    val password: String = "",
) {
    val base: String get() = "${if (https) "https" else "http"}://$host:$port"
    val isEmpty: Boolean get() = host.isBlank() || username.isBlank()
}

/** Orden en que se intenta pedir el canal. Ver StreamVariants. */
enum class VariantOrder { HLS_FIRST, TS_FIRST }

enum class BufferPreset(val label: String, val minMs: Int, val maxMs: Int, val startMs: Int, val rebufferMs: Int) {
    NORMAL("Normal", 15_000, 50_000, 1_500, 3_000),
    SATELITE("Satélite", 30_000, 120_000, 2_500, 6_000),
    EXTREMO("Extremo", 45_000, 180_000, 4_000, 10_000),
}

data class NetSettings(
    /** Ignora los registros AAAA. Arregla el caso "IPv6 roto del proveedor". */
    val forceIpv4: Boolean = true,
    /** Resuelve por DNS sobre HTTPS en Cloudflare en vez del DNS del router. */
    val useDoh: Boolean = false,
    /** HTTP/2 sufre bloqueo de cabeza de linea cuando Starlink pierde paquetes. */
    val forceHttp11: Boolean = true,
    val variantOrder: VariantOrder = VariantOrder.HLS_FIRST,
    val buffer: BufferPreset = BufferPreset.SATELITE,
    /** Muchos paneles responden distinto segun el User-Agent que ven. */
    val userAgent: String = DEFAULT_UA,
    /** Segundos sin avanzar la posicion antes de forzar reconexión. */
    val stallSeconds: Int = 8,
    /** Segundos en búfer vacío antes de forzar reconexión. */
    val bufferingSeconds: Int = 15,
    /** Consultar la IP pública en el diagnóstico (llamada a un tercero). */
    val allowIpLookup: Boolean = false,
) {
    companion object {
        const val DEFAULT_UA = "VLC/3.0.20 LibVLC/3.0.20"
    }
}

data class AppSettings(
    val account: Account = Account(),
    val net: NetSettings = NetSettings(),
    val lastChannelId: Int = -1,
)
