package com.orbita.tv.data

import org.json.JSONObject

enum class ChannelLayout { LISTA, MOSAICO }

/**
 * Toda la apariencia, como datos.
 *
 * El archivo remoto no reemplaza este objeto: lo pisa campo por campo. Lo que
 * el JSON no diga, o diga mal, se queda con el valor de aca. Esa es la regla
 * que hace imposible dejar el televisor con una pantalla inservible por un
 * error de tipeo a mil kilometros de distancia.
 *
 * Las claves del JSON estan en espanol porque el archivo se edita a mano.
 */
data class Skin(
    // Colores, en "#RRGGBB" o "#AARRGGBB"
    val bg: Long = 0xFF0B1020,
    val card: Long = 0xFF141E33,
    val cardFocused: Long = 0xFF1E2C49,
    val line: Long = 0xFF243352,
    val text: Long = 0xFFE8EDF5,
    val textSoft: Long = 0xFF9BA7BD,
    val accent: Long = 0xFF5FC9A0,
    val warn: Long = 0xFFE0B252,
    val fail: Long = 0xFFE2705F,
    /**
     * "Todo bien": la prueba que paso, la opcion activada. Antes se dibujaba con
     * el acento, y con un acento rojo un "activado" se leia como un error.
     */
    val ok: Long = 0xFF5FC9A0,

    // Forma y foco. En un televisor a tres metros, el anillo de foco no es
    // decoracion: es lo unico que dice donde estas parado.
    val radius: Float = 10f,
    val focusWidth: Float = 2f,
    val restWidth: Float = 1f,
    val focusScale: Float = 1f,

    // Tipografia. escalaTexto multiplica TODOS los tamanos de una sola vez,
    // sin tener que parametrizar cada pantalla.
    val fontUrl: String? = null,
    /** Peso pesado, para titulos y nombres de canal. Opcional. */
    val fontUrlBold: String? = null,
    val fontScale: Float = 1f,

    // Espaciado
    val screenPad: Float = 24f,
    val gap: Float = 8f,

    // Disposicion de los canales
    val layout: ChannelLayout = ChannelLayout.LISTA,
    val tileWidth: Float = 260f,
    val showLogos: Boolean = true,
    val sidebarWidth: Float = 300f,

    // Fondo y marca
    val bgImageUrl: String? = null,
    val bgImageAlpha: Float = 0.25f,
    val logoUrl: String? = null,
) {
    companion object {
        val DEFAULT = Skin()

        /**
         * Aplica un JSON encima de los valores actuales. Nada de esto lanza
         * excepciones: una clave desconocida se ignora, una mal escrita se
         * descarta y el campo conserva su valor.
         */
        fun merge(base: Skin, raw: String): Skin {
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return base
            return base.copy(
                bg = o.color("fondo", base.bg),
                card = o.color("tarjeta", base.card),
                cardFocused = o.color("tarjetaFoco", base.cardFocused),
                line = o.color("linea", base.line),
                text = o.color("texto", base.text),
                textSoft = o.color("textoSuave", base.textSoft),
                accent = o.color("acento", base.accent),
                warn = o.color("aviso", base.warn),
                fail = o.color("falla", base.fail),
                ok = o.color("bien", base.ok),

                radius = o.num("esquinas", base.radius, 0f, 40f),
                focusWidth = o.num("grosorFoco", base.focusWidth, 0f, 8f),
                restWidth = o.num("grosorReposo", base.restWidth, 0f, 8f),
                focusScale = o.num("escalaFoco", base.focusScale, 1f, 1.15f),

                fontUrl = o.str("fuenteUrl", base.fontUrl),
                fontUrlBold = o.str("fuenteUrlNegrita", base.fontUrlBold),
                fontScale = o.num("escalaTexto", base.fontScale, 0.7f, 1.6f),

                screenPad = o.num("margenPantalla", base.screenPad, 0f, 80f),
                gap = o.num("separacion", base.gap, 0f, 40f),

                layout = when (o.optString("disposicion").lowercase()) {
                    "mosaico" -> ChannelLayout.MOSAICO
                    "lista" -> ChannelLayout.LISTA
                    else -> base.layout
                },
                tileWidth = o.num("anchoTile", base.tileWidth, 120f, 520f),
                showLogos = if (o.has("mostrarLogos")) o.optBoolean("mostrarLogos", base.showLogos) else base.showLogos,
                sidebarWidth = o.num("anchoBarraLateral", base.sidebarWidth, 160f, 520f),

                bgImageUrl = o.str("fondoImagenUrl", base.bgImageUrl),
                bgImageAlpha = o.num("fondoImagenOpacidad", base.bgImageAlpha, 0f, 1f),
                logoUrl = o.str("logoUrl", base.logoUrl),
            )
        }

        private fun JSONObject.str(key: String, fallback: String?): String? {
            if (!has(key)) return fallback
            val v = optString(key, "")
            return if (v.isBlank() || v == "null") null else v
        }

        /** Fuera de rango se recorta en vez de rechazarse: un 99 en esquinas da 40, no un error. */
        private fun JSONObject.num(key: String, fallback: Float, min: Float, max: Float): Float {
            if (!has(key)) return fallback
            val v = optDouble(key, Double.NaN)
            if (v.isNaN()) return fallback
            return v.toFloat().coerceIn(min, max)
        }

        private fun JSONObject.color(key: String, fallback: Long): Long {
            if (!has(key)) return fallback
            val s = optString(key, "").trim()
            if (s.isEmpty()) return fallback
            return parseColor(s) ?: fallback
        }

        /** Acepta #RGB, #RRGGBB y #AARRGGBB. Sin alfa, se asume opaco. */
        fun parseColor(text: String): Long? {
            val h = text.removePrefix("#")
            val expanded = when (h.length) {
                3 -> h.map { "$it$it" }.joinToString("")
                6, 8 -> h
                else -> return null
            }
            val value = expanded.toLongOrNull(16) ?: return null
            return if (expanded.length == 6) value or 0xFF000000L else value
        }
    }
}
