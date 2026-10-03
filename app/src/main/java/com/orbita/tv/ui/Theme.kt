package com.orbita.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbita.tv.data.Skin

/**
 * El tema vive en un solo estado observable y global.
 *
 * Es deliberado: al leer Tint.text dentro de un composable, ese composable
 * queda suscrito al estado, asi que cuando llega un tema nuevo desde el
 * servidor se redibuja solo lo que usa lo que cambio. No hay que pasar el tema
 * por parametro por toda la app ni recrear pantallas, y por eso el modo diseno
 * puede cambiar la apariencia con la app abierta.
 *
 * La app es de un solo tema a la vez, sin claro/oscuro simultaneos, que es lo
 * unico que este enfoque no permitiria.
 */
object Tint {
    var skin by mutableStateOf(Skin.DEFAULT)
    var fontFamily by mutableStateOf<FontFamily?>(null)

    val bg: Color get() = Color(skin.bg)
    val card: Color get() = Color(skin.card)
    val cardFocused: Color get() = Color(skin.cardFocused)
    val line: Color get() = Color(skin.line)
    val text: Color get() = Color(skin.text)
    val textSoft: Color get() = Color(skin.textSoft)
    val accent: Color get() = Color(skin.accent)
    val warn: Color get() = Color(skin.warn)
    val fail: Color get() = Color(skin.fail)
    val ok: Color get() = Color(skin.ok)
    val glow1: Color get() = Color(skin.glow1 ?: skin.accent)
    val glow2: Color get() = Color(skin.glow2)
}

@Composable
fun OrbitaTheme(content: @Composable () -> Unit) {
    val skin = Tint.skin
    val density = LocalDensity.current
    val family = Tint.fontFamily

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tint.accent,
            background = Tint.bg,
            surface = Tint.card,
            onPrimary = Tint.bg,
            onBackground = Tint.text,
            onSurface = Tint.text,
        ),
    ) {
        // escalaTexto se aplica una sola vez, en la densidad: escala todos los
        // tamanos de letra de la app sin tener que parametrizar cada pantalla.
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * skin.fontScale),
            LocalTextStyle provides LocalTextStyle.current.let {
                if (family != null) it.copy(fontFamily = family) else it
            },
            content = content,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        color = Tint.textSoft,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Escribe la primera de las opciones que cabe entera en una linea.
 *
 * Existe porque la etiqueta de un boton no puede partirse: en una barra angosta
 * "Diagnostico de red" se dibujaba "Diagnostic / o de red". Se va de la mas
 * completa a la mas corta, y si ni la ultima cabe se recorta con puntos
 * suspensivos, siempre en una sola linea.
 */
@Composable
fun TextoQueCabe(
    opciones: List<String>,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Layout(
        modifier = modifier,
        content = {
            opciones.forEach { t ->
                Text(
                    t, color = color, fontSize = fontSize, fontWeight = fontWeight,
                    maxLines = 1, softWrap = false,
                )
            }
            Text(
                opciones.last(), color = color, fontSize = fontSize, fontWeight = fontWeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
    ) { medibles, limites ->
        val sueltas = medibles.dropLast(1).map { it.measure(Constraints()) }
        val recortada = medibles.last().measure(limites.copy(minWidth = 0, minHeight = 0))
        val elegida = sueltas.firstOrNull { it.width <= limites.maxWidth } ?: recortada
        val ancho = elegida.width.coerceIn(limites.minWidth, limites.maxWidth)
        val alto = elegida.height.coerceIn(limites.minHeight, limites.maxHeight)
        layout(ancho, alto) { elegida.placeRelative(0, 0) }
    }
}

/**
 * Fila enfocable. Es el ladrillo de toda la app: en un TV el usuario no ve el
 * puntero, solo ve donde esta parado. Es de cristal, como todos los botones:
 * ver BotonCristal.
 */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    /** Mantener OK, o mantener el dedo. Se usa para marcar favoritos. */
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    BotonCristal(
        onClick = onClick,
        modifier = modifier,
        padding = padding,
        onLongClick = onLongClick,
        onFocus = onFocus,
        content = content,
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Tint.textSoft,
        fontSize = 13.sp,
        modifier = modifier.alpha(0.9f),
    )
}
