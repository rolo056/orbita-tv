package com.orbita.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Tint.textSoft,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

/**
 * Fila enfocable con anillo de foco visible. Es el ladrillo de toda la app: en
 * un TV el usuario no ve el puntero, solo ve donde esta parado.
 */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable (focused: Boolean) -> Unit,
) {
    val skin = Tint.skin
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(skin.radius.dp)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .scale(if (focused) skin.focusScale else 1f)
            .background(if (focused) Tint.cardFocused else Tint.card, shape)
            .border(
                width = (if (focused) skin.focusWidth else skin.restWidth).dp,
                color = if (focused) Tint.accent else Tint.line,
                shape = shape,
            )
            .padding(padding),
        contentAlignment = Alignment.CenterStart,
    ) {
        content(focused)
    }
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
