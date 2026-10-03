package com.orbita.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbita.tv.player.PlaybackStats

/**
 * Las capas que se dibujan sobre el video.
 *
 * Viven aparte de la pantalla porque el reproductor ya no es una pantalla: es
 * una superficie que a veces ocupa un recuadro y a veces la pantalla entera, y
 * estas capas se usan en los dos casos.
 */

@Composable
fun BoxScope.PlayerFatal(stats: PlaybackStats, esTv: Boolean, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        PanelCristal(
            Modifier.widthIn(max = 720.dp),
            radio = 28.dp,
            oscuro = true,
            padding = PaddingValues(horizontal = 32.dp, vertical = 26.dp),
            alineacion = Alignment.CenterHorizontally,
        ) {
            Text("Canal detenido", color = Tint.fail, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(stats.fatalError ?: "", color = Tint.text, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            FocusRow(onClick = onRetry, modifier = Modifier.width(220.dp)) {
                Text("Reintentar", color = Tint.text, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Hint(
                if (esTv) {
                    "OK reintenta · Arriba y abajo cambian de canal · Atrás vuelve a la lista"
                } else {
                    "Atrás para volver a la lista"
                }
            )
        }
    }
}

/**
 * La app sigue insistiendo sola. Sin este aviso la pantalla queda negra y
 * parece colgada, que es la peor forma de esperar.
 */
@Composable
fun BoxScope.PlayerRetryNotice(stats: PlaybackStats) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        PanelCristal(
            radio = 26.dp,
            oscuro = true,
            padding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
            alineacion = Alignment.CenterHorizontally,
        ) {
            Text("Sin señal", color = Tint.warn, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stats.status, color = Tint.text, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Hint("Sigue reintentando solo")
        }
    }
}

@Composable
fun BoxScope.PlayerInfoBar(stats: PlaybackStats, esTv: Boolean) {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 28.dp, vertical = 22.dp)
            .fillMaxWidth()
            .cristal(radio = 26.dp, oscuro = true)
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        Text(
            stats.channelName,
            color = Tint.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Hint(stats.status)
            Hint(stats.variantLabel)
            if (stats.reconnects > 0) Hint("reconexiones: " + stats.reconnects)
        }
        Spacer(Modifier.height(4.dp))
        Hint(
            if (esTv) {
                "Arriba y abajo cambian de canal · OK muestra u oculta esto · " +
                    "Derecha abre el detalle técnico · Atrás vuelve a la lista"
            } else {
                "Toca la imagen para mostrar u ocultar esto"
            }
        )
    }
}

/**
 * El detalle tecnico existe para dos preguntas. Cuando se corta, ¿es la red o
 * es el panel? El bufer y el contador de reconexiones lo responden sin adivinar.
 * Y cuando se oye bien pero se ve con bloques, ¿es la señal o es el aparato? La
 * imagen dice el formato y si el decodificador del aparato declara poder con el.
 */
@Composable
fun BoxScope.PlayerHud(stats: PlaybackStats) {
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(20.dp)
            .cristal(radio = 24.dp, oscuro = true)
            .padding(18.dp)
            .width(320.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle("DETALLE TÉCNICO")
        HudLine("Estado", stats.status)
        HudLine("Formato", stats.variantLabel)
        HudLine("Búfer", stats.bufferedSeconds.toString() + " s")
        HudLine("Caudal", stats.kbps.toString() + " kbps")
        HudLine("Reconexiones", stats.reconnects.toString())
        HudLine("Estable desde", stats.healthySeconds.toString() + " s")
        HudLine("Cuadros perdidos", stats.cuadrosPerdidos.toString())
        Spacer(Modifier.height(4.dp))
        HudBloque("Imagen", stats.imagen.ifEmpty { "—" })
        HudBloque("Decodificador", stats.decodificador.ifEmpty { "—" })
        Spacer(Modifier.height(4.dp))
        Text(
            stats.variantUrl,
            color = Tint.textSoft,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HudLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Tint.textSoft, fontSize = 13.sp)
        Text(value, color = Tint.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Un dato largo: el nombre arriba y el valor debajo, en hasta dos lineas. */
@Composable
private fun HudBloque(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Tint.textSoft, fontSize = 13.sp)
        Text(
            value,
            color = Tint.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Boton para dedo: area amplia y fondo propio, porque va sobre el video. */
@Composable
fun TouchButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .cristal(radio = 40.dp, oscuro = true)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = Tint.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
