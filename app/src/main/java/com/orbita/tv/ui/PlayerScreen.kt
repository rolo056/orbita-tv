package com.orbita.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.player.ResilientPlayer

@Composable
fun PlayerScreen(
    engine: ResilientPlayer,
    stats: PlaybackStats,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onExit: () -> Unit,
) {
    var showInfo by remember { mutableStateOf(true) }
    var showHud by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // La barra de informacion se va sola; el HUD se queda hasta que lo apagues.
    LaunchedEffect(stats.channelName, showInfo) {
        if (showInfo) {
            kotlinx.coroutines.delay(5_000)
            showInfo = false
        }
    }

    BackHandler { onExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> {
                        onPrevChannel(); showInfo = true; true
                    }
                    Key.DirectionDown, Key.ChannelDown -> {
                        onNextChannel(); showInfo = true; true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        showInfo = !showInfo; true
                    }
                    Key.Menu, Key.I, Key.Info -> {
                        showHud = !showHud; true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = engine.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (stats.fatalError != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)).padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Canal detenido", color = Tint.fail, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(stats.fatalError, color = Tint.text, fontSize = 15.sp)
                    Spacer(Modifier.height(20.dp))
                    Hint("Atrás para volver a la lista · Arriba y abajo para cambiar de canal")
                }
            }
        }

        if (showInfo && stats.fatalError == null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
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
                    if (stats.reconnects > 0) {
                        Hint("reconexiones: " + stats.reconnects)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Hint("Arriba/abajo cambia de canal · OK muestra u oculta esto · Menú abre el detalle técnico")
            }
        }

        if (showHud) {
            // El detalle tecnico existe para una sola pregunta: cuando se corta,
            // ¿es la red o es el panel? El bufer y el contador de reconexiones lo
            // responden sin adivinar.
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(16.dp)
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
    }
}

@Composable
private fun HudLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Tint.textSoft, fontSize = 13.sp)
        Text(value, color = Tint.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
