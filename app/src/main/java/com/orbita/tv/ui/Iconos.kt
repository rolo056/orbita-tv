package com.orbita.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/** Los iconos de la app. */
enum class Icono { TV, PELICULA, SERIE, CUENTA, AJUSTES, RED }

/**
 * Los iconos, dibujados a mano: trazo redondeado, sin aristas, del mismo grosor
 * en todos. Dibujarlos aca evita sumar una libreria de iconos entera por seis
 * dibujos, y los deja con el estilo de la app y no con el de otra.
 *
 * Se dibujan sobre una grilla de 24 x 24 que se escala al tamano pedido.
 */
@Composable
fun Dibujo(icono: Icono, modifier: Modifier = Modifier, color: Color = Tint.text) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val ox = (size.width - 24f * u) / 2f
        val oy = (size.height - 24f * u) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
        val grosor = 1.8f * u
        val trazo = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (icono) {
            Icono.TV -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(2.5f, 6.5f),
                    size = Size(19f * u, 13f * u),
                    cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
                    style = trazo,
                )
                drawLine(color, p(8f, 2.5f), p(12f, 6.5f), strokeWidth = grosor, cap = StrokeCap.Round)
                drawLine(color, p(16f, 2.5f), p(12f, 6.5f), strokeWidth = grosor, cap = StrokeCap.Round)
                drawLine(color, p(9f, 22f), p(15f, 22f), strokeWidth = grosor, cap = StrokeCap.Round)
            }
            Icono.PELICULA -> {
                drawCircle(color, radius = 9.6f * u, center = p(12f, 12f), style = trazo)
                val triangulo = Path().apply {
                    val a = p(10f, 8.1f)
                    val b = p(16.4f, 12f)
                    val c = p(10f, 15.9f)
                    moveTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    lineTo(c.x, c.y)
                    close()
                }
                drawPath(triangulo, color)
                drawPath(triangulo, color, style = trazo)
            }
            Icono.SERIE -> {
                // Una claqueta: el cuerpo y la tapa abierta, con sus franjas.
                drawRoundRect(
                    color = color,
                    topLeft = p(3f, 10f),
                    size = Size(18f * u, 11f * u),
                    cornerRadius = CornerRadius(2.4f * u, 2.4f * u),
                    style = trazo,
                )
                rotate(degrees = -13f, pivot = p(3f, 9.6f)) {
                    drawRoundRect(
                        color = color,
                        topLeft = p(3f, 5.6f),
                        size = Size(18f * u, 4f * u),
                        cornerRadius = CornerRadius(1.6f * u, 1.6f * u),
                        style = trazo,
                    )
                    for (x in listOf(7.5f, 12.5f, 17.5f)) {
                        drawLine(color, p(x, 5.8f), p(x + 2.2f, 9.4f), strokeWidth = grosor, cap = StrokeCap.Round)
                    }
                }
            }
            Icono.CUENTA -> {
                drawCircle(color, radius = 4f * u, center = p(12f, 8f), style = trazo)
                val hombros = Path().apply {
                    val a = p(4.5f, 21f)
                    val c1 = p(4.5f, 15.6f)
                    val c2 = p(8.2f, 13.4f)
                    val m = p(12f, 13.4f)
                    val c3 = p(15.8f, 13.4f)
                    val c4 = p(19.5f, 15.6f)
                    val b = p(19.5f, 21f)
                    moveTo(a.x, a.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, m.x, m.y)
                    cubicTo(c3.x, c3.y, c4.x, c4.y, b.x, b.y)
                }
                drawPath(hombros, color, style = trazo)
            }
            Icono.AJUSTES -> {
                // Un engranaje: el aro, el centro y ocho dientes redondeados.
                drawCircle(color, radius = 6.2f * u, center = p(12f, 12f), style = trazo)
                drawCircle(color, radius = 2.4f * u, center = p(12f, 12f), style = trazo)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0 + 22.5)
                    val c = cos(a).toFloat()
                    val s = sin(a).toFloat()
                    drawLine(
                        color,
                        p(12f + 6.4f * c, 12f + 6.4f * s),
                        p(12f + 9.4f * c, 12f + 9.4f * s),
                        strokeWidth = 2.6f * u,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Icono.RED -> {
                // La senal: tres arcos y un punto.
                for (radio in listOf(9.5f, 6.4f, 3.3f)) {
                    drawArc(
                        color = color,
                        startAngle = 222f,
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = p(12f - radio, 18.5f - radio),
                        size = Size(2f * radio * u, 2f * radio * u),
                        style = trazo,
                    )
                }
                drawCircle(color, radius = 1.5f * u, center = p(12f, 18.5f))
            }
        }
    }
}
