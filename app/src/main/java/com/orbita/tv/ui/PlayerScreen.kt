package com.orbita.tv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    val esTv = isTvDevice()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // La barra de informacion se va sola; el HUD se queda hasta que lo apagues.
    LaunchedEffect(stats.channelName, showInfo) {
        if (showInfo) {
            kotlinx.coroutines.delay(5_000)
            showInfo = false
        }
    }

    // El video se mira en horizontal. El resto de la app puede rotar libre en un
    // telefono, asi que se pide horizontal solo mientras esta esta pantalla y se
    // devuelve al salir. En un televisor no cambia nada: no rota.
    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        val anterior = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (activity != null && anterior != null) activity.requestedOrientation = anterior
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
                    Key.DirectionUp -> {
                        onPrevChannel(); showInfo = true; true
                    }
                    Key.DirectionDown -> {
                        onNextChannel(); showInfo = true; true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        showInfo = !showInfo; true
                    }
                    Key.Menu, Key.DirectionRight -> {
                        showHud = !showHud; true
                    }
                    else -> false
                }
            }
            // Tocar la imagen equivale al OK del mando. Los botones de abajo se
            // dibujan encima, asi que reciben su propio toque antes que esto.
            .pointerInput(Unit) {
                detectTapGestures { showInfo = !showInfo }
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
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
                    Hint(
                        if (esTv) "Atrás para volver a la lista · Arriba y abajo para cambiar de canal"
                        else "Atrás para volver a la lista"
                    )
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
                Hint(
                    if (esTv) {
                        "Arriba y abajo cambian de canal · OK muestra u oculta esto · " +
                            "Derecha abre el detalle técnico"
                    } else {
                        "Toca la imagen para mostrar u ocultar esto"
                    }
                )
            }
        }

        // Sin mando no hay forma de cambiar de canal ni de ver el detalle, asi
        // que en telefonos y tabletas los controles van en pantalla.
        if (!esTv && stats.fatalError == null) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TouchButton("Canal −") { onPrevChannel(); showInfo = true }
                TouchButton("Canal +") { onNextChannel(); showInfo = true }
                TouchButton(if (showHud) "Ocultar detalle" else "Detalle") { showHud = !showHud }
                TouchButton("Salir") { onExit() }
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

/** Boton para dedo: area amplia y fondo propio, porque va sobre el video. */
@Composable
private fun TouchButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color(0xB3000000), RoundedCornerShape(8.dp))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, color = Tint.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HudLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Tint.textSoft, fontSize = 13.sp)
        Text(value, color = Tint.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
