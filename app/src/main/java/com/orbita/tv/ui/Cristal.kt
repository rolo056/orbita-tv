package com.orbita.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbita.tv.data.Skin
import kotlin.math.max
import kotlin.math.min

/**
 * El cristal: el material de todos los botones y paneles de la app.
 *
 * Imita el vidrio de las versiones nuevas de iOS sin desenfocar lo que hay
 * detras. Los televisores de esta app son viejos, y ese desenfoque pide
 * Android 12. El truco es que el fondo ya es suave (resplandores, sin bordes),
 * asi que un velo blanco translucido, un brillo arriba y un borde que capta la
 * luz alcanzan para que se lea como vidrio.
 *
 * Nada aqui es brusco: las esquinas son redondas, y el foco no salta de un
 * estado a otro, se enciende y se apaga en un instante.
 */

/** El radio de las esquinas. Nunca menos de 12: el dueño no quiere esquinas cuadradas. */
fun radioDe(skin: Skin): Dp = max(skin.radius, 12f).dp

/**
 * El fondo de todas las pantallas: oscuro, con dos resplandores de color que el
 * cristal deja ver.
 */
@Composable
fun Fondo(modifier: Modifier = Modifier) {
    val base = Tint.bg
    val luz1 = Tint.glow1
    val luz2 = Tint.glow2
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                listOf(luz1.copy(alpha = 0.40f), luz1.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.02f),
                radius = size.width * 0.62f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(luz2.copy(alpha = 0.55f), luz2.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.04f, size.height * 1.04f),
                radius = size.width * 0.72f,
            )
        )
        // Un poco de luz al centro, para que el vidrio tenga algo que mostrar.
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.45f),
                radius = size.width * 0.55f,
            )
        )
    }
}

/**
 * El vidrio de un boton o de un panel.
 *
 * [luz] va de 0 (en reposo) a 1 (enfocado); quien lo usa la anima. [tinte] le da
 * un color propio al vidrio, como las baldosas de la pantalla de inicio. [oscuro]
 * es para lo que va encima del video: un vidrio claro sobre una imagen clara no
 * se lee.
 */
fun Modifier.cristal(
    radio: Dp,
    luz: Float = 0f,
    acento: Color = Color.White,
    tinte: Color? = null,
    oscuro: Boolean = false,
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val r = min(radio.toPx(), min(w, h) / 2f)
    val esquina = CornerRadius(r, r)
    val relleno = if (oscuro) {
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.62f + 0.08f * luz), Color.Black.copy(alpha = 0.50f + 0.08f * luz))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.14f + 0.10f * luz), Color.White.copy(alpha = 0.05f + 0.07f * luz))
        )
    }
    val color = tinte?.let {
        Brush.radialGradient(
            listOf(it.copy(alpha = 0.36f + 0.14f * luz), it.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(w * 0.22f, h * 0.12f),
            radius = max(w, h) * 0.95f,
        )
    }
    val brillo = Brush.radialGradient(
        listOf(Color.White.copy(alpha = (if (oscuro) 0.07f else 0.15f) + 0.08f * luz), Color.Transparent),
        center = Offset(w * 0.2f, 0f),
        radius = max(w, h) * 0.7f,
    )
    val borde = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (oscuro) 0.30f else 0.48f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = if (oscuro) 0.12f else 0.22f),
        ),
        start = Offset.Zero,
        end = Offset(w, h),
    )
    val bordeFoco = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.95f), acento, acento.copy(alpha = 0.75f)),
        start = Offset.Zero,
        end = Offset(w, h),
    )
    val fino = Stroke(width = 1.dp.toPx())
    val grueso = Stroke(width = 2.dp.toPx())
    val anillo = Stroke(width = 3.dp.toPx())
    val paso = 3.dp.toPx()
    onDrawBehind {
        // El halo del foco: anillos cada vez mas anchos y tenues. Sin
        // desenfoque, que los televisores viejos no tienen.
        if (luz > 0.01f) {
            for (i in 1..5) {
                val e = i * paso
                drawRoundRect(
                    color = acento.copy(alpha = 0.11f * luz * (6 - i) / 5f),
                    topLeft = Offset(-e, -e),
                    size = Size(w + 2 * e, h + 2 * e),
                    cornerRadius = CornerRadius(r + e, r + e),
                    style = anillo,
                )
            }
        }
        drawRoundRect(brush = relleno, cornerRadius = esquina)
        if (color != null) drawRoundRect(brush = color, cornerRadius = esquina)
        drawRoundRect(brush = brillo, cornerRadius = esquina)
        drawRoundRect(brush = borde, cornerRadius = esquina, style = fino, alpha = 1f - luz)
        if (luz > 0.01f) {
            drawRoundRect(brush = bordeFoco, cornerRadius = esquina, style = grueso, alpha = luz)
        }
    }
}

/**
 * Un boton de cristal. Todos los botones de la app lo son, desde la fila de un
 * canal hasta las baldosas de la pantalla de inicio.
 *
 * Al enfocarse se enciende el borde con el acento, aparece un halo y crece un
 * poco; al pulsarse se hunde apenas. Todo con una transicion corta: nada salta.
 * Sin la onda de Material al pulsar, que es un rectangulo que se nota encima de
 * un vidrio redondo.
 */
@Composable
fun BotonCristal(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radio: Dp = radioDe(Tint.skin),
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    tinte: Color? = null,
    alineacion: Alignment = Alignment.CenterStart,
    escalaFoco: Float = Tint.skin.focusScale,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable (enfocado: Boolean) -> Unit,
) {
    var enfocado by remember { mutableStateOf(false) }
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val luz by animateFloatAsState(
        targetValue = if (enfocado) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "luz",
    )
    val escala by animateFloatAsState(
        targetValue = when {
            presionado -> 0.97f
            enfocado -> max(escalaFoco, 1.02f)
            else -> 1f
        },
        animationSpec = tween(durationMillis = 160),
        label = "escala",
    )
    val acento = Tint.accent
    Box(
        modifier = modifier
            .onFocusChanged {
                enfocado = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .combinedClickable(
                interactionSource = fuente,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }
            .cristal(radio = radio, luz = luz, acento = acento, tinte = tinte)
            .padding(padding),
        contentAlignment = alineacion,
    ) {
        content(enfocado)
    }
}

/** Un panel de cristal que no se enfoca: fichas, avisos, datos de la cuenta. */
@Composable
fun PanelCristal(
    modifier: Modifier = Modifier,
    radio: Dp = radioDe(Tint.skin),
    oscuro: Boolean = false,
    tinte: Color? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    arreglo: Arrangement.Vertical = Arrangement.Top,
    alineacion: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .cristal(radio = radio, oscuro = oscuro, tinte = tinte)
            .padding(padding),
        verticalArrangement = arreglo,
        horizontalAlignment = alineacion,
        content = content,
    )
}
