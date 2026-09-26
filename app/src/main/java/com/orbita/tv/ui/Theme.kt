package com.orbita.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta oscura y sobria: en un televisor grande a tres metros, el contraste
 * alto y los bordes gruesos de foco son accesibilidad, no decoracion.
 */
object Tint {
    val bg = Color(0xFF0B1020)
    val card = Color(0xFF141E33)
    val cardFocused = Color(0xFF1E2C49)
    val line = Color(0xFF243352)
    val text = Color(0xFFE8EDF5)
    val textSoft = Color(0xFF9BA7BD)
    val accent = Color(0xFF5FC9A0)
    val warn = Color(0xFFE0B252)
    val fail = Color(0xFFE2705F)
}

@Composable
fun OrbitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tint.accent,
            background = Tint.bg,
            surface = Tint.card,
            onPrimary = Tint.bg,
            onBackground = Tint.text,
            onSurface = Tint.text,
        ),
        content = content,
    )
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
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                if (focused) Tint.cardFocused else Tint.card,
                RoundedCornerShape(10.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Tint.accent else Tint.line,
                shape = RoundedCornerShape(10.dp),
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
