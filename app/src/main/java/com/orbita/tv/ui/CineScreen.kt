package com.orbita.tv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent as TeclaAndroid
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.orbita.tv.data.AppSettings
import com.orbita.tv.player.CineStats
import com.orbita.tv.player.VodPlayer
import kotlinx.coroutines.delay

/**
 * Una pelicula o un episodio a pantalla completa.
 *
 * Con el mando: izquierda y derecha retroceden y adelantan (manteniendo la
 * tecla, cada vez mas rapido), OK pausa y sigue, arriba cambia el sonido y abajo
 * los subtitulos. Los saltos se juntan: diez toques a la derecha son un solo
 * pedido al servidor, no diez, y se hace cuando se suelta el mando.
 *
 * El avance se guarda cada diez segundos y al salir, asi que cerrar la app de
 * golpe cuesta como mucho diez segundos de pelicula.
 */
@Composable
fun CineScreen(
    titulo: String,
    subtitulo: String,
    direccion: String,
    desdeMs: Long,
    haySiguiente: Boolean,
    settings: AppSettings,
    onAvance: (posMs: Long, durMs: Long) -> Unit,
    onSiguiente: () -> Unit,
    onSalir: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cine = remember { VodPlayer(ctx, scope, settings) }
    val stats by cine.stats.collectAsState()
    val esTv = isTvDevice()

    val alAvance by rememberUpdatedState(onAvance)
    val alSiguiente by rememberUpdatedState(onSiguiente)
    val alSalir by rememberUpdatedState(onSalir)

    fun guardar() {
        val s = cine.stats.value
        if (s.durMs > 0 && s.error == null) alAvance(cine.posicion(), s.durMs)
    }

    DisposableEffect(Unit) {
        onDispose {
            guardar()
            cine.release()
        }
    }
    LaunchedEffect(direccion) { cine.abrir(direccion, desdeMs) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (cine.stats.value.reproduciendo) guardar()
        }
    }

    // Fuera de pantalla se suelta la conexion; al volver, sigue donde estaba.
    val dueno = LocalLifecycleOwner.current
    DisposableEffect(dueno) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> {
                    guardar()
                    cine.onBackground()
                }
                Lifecycle.Event.ON_START -> cine.onForeground()
                else -> Unit
            }
        }
        dueno.lifecycle.addObserver(observador)
        onDispose { dueno.lifecycle.removeObserver(observador) }
    }

    // En un telefono, el video se mira acostado.
    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        val anterior = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (activity != null && anterior != null) activity.requestedOrientation = anterior
        }
    }

    var capas by remember { mutableStateOf(true) }
    var toque by remember { mutableIntStateOf(0) }
    var objetivo by remember { mutableStateOf<Long?>(null) }
    var cuenta by remember { mutableIntStateOf(-1) }

    // El salto se pide cuando el mando se queda quieto un momento.
    LaunchedEffect(objetivo) {
        val o = objetivo ?: return@LaunchedEffect
        delay(650)
        cine.irA(o)
        objetivo = null
    }
    LaunchedEffect(capas, toque, stats.reproduciendo, objetivo) {
        if (capas && stats.reproduciendo && objetivo == null) {
            delay(4_000)
            capas = false
        }
    }
    // Al terminar: el episodio siguiente tras una cuenta atras, o de vuelta a la ficha.
    LaunchedEffect(stats.terminado) {
        if (!stats.terminado) {
            cuenta = -1
            return@LaunchedEffect
        }
        if (!haySiguiente) {
            alSalir()
            return@LaunchedEffect
        }
        for (n in 8 downTo 1) {
            cuenta = n
            delay(1_000)
        }
        cuenta = -1
        alSiguiente()
    }

    BackHandler { alSalir() }

    fun mover(delta: Long) {
        val base = objetivo ?: stats.posMs
        val dur = stats.durMs
        objetivo = if (dur > 0) (base + delta).coerceIn(0L, dur) else (base + delta).coerceAtLeast(0L)
        capas = true
        toque++
    }

    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(foco)
            .focusable()
            .onPreviewKeyEvent { e ->
                val codigo = e.nativeKeyEvent.keyCode
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent esTeclaDelCine(codigo)
                val repeticiones = e.nativeKeyEvent.repeatCount
                when (codigo) {
                    TeclaAndroid.KEYCODE_DPAD_LEFT -> { mover(-paso(repeticiones)); true }
                    TeclaAndroid.KEYCODE_DPAD_RIGHT -> { mover(paso(repeticiones)); true }
                    TeclaAndroid.KEYCODE_MEDIA_REWIND -> { mover(-30_000L); true }
                    TeclaAndroid.KEYCODE_MEDIA_FAST_FORWARD -> { mover(30_000L); true }
                    TeclaAndroid.KEYCODE_DPAD_CENTER,
                    TeclaAndroid.KEYCODE_ENTER,
                    TeclaAndroid.KEYCODE_NUMPAD_ENTER,
                    TeclaAndroid.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        when {
                            stats.error != null -> cine.reintentar()
                            cuenta > 0 -> {
                                cuenta = -1
                                alSiguiente()
                            }
                            else -> cine.alternarPausa()
                        }
                        capas = true
                        toque++
                        true
                    }
                    TeclaAndroid.KEYCODE_MEDIA_PLAY -> { cine.seguir(); capas = true; true }
                    TeclaAndroid.KEYCODE_MEDIA_PAUSE -> { cine.pausar(); capas = true; true }
                    TeclaAndroid.KEYCODE_DPAD_UP -> { cine.siguienteAudio(); capas = true; toque++; true }
                    TeclaAndroid.KEYCODE_DPAD_DOWN -> { cine.siguienteSubtitulo(); capas = true; toque++; true }
                    TeclaAndroid.KEYCODE_MEDIA_NEXT -> { if (haySiguiente) alSiguiente(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    capas = !capas
                    toque++
                }
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = cine.player
                }
            },
            update = { it.keepScreenOn = stats.reproduciendo || stats.cargando },
            modifier = Modifier.fillMaxSize(),
        )
        CineCapas(
            titulo = titulo,
            subtitulo = subtitulo,
            stats = stats,
            objetivo = objetivo,
            visibles = capas,
            esTv = esTv,
            cuenta = cuenta,
            onBarra = { fraccion ->
                if (stats.durMs > 0) {
                    objetivo = (stats.durMs * fraccion).toLong()
                    toque++
                }
            },
            botones = {
                TouchButton("−10 s") { mover(-10_000L) }
                TouchButton(if (stats.enPausa) "Seguir" else "Pausa") { cine.alternarPausa(); toque++ }
                TouchButton("+10 s") { mover(10_000L) }
                if (stats.audios > 1) TouchButton("Sonido") { cine.siguienteAudio(); toque++ }
                if (stats.subtitulos > 0) TouchButton("Subtítulos") { cine.siguienteSubtitulo(); toque++ }
                TouchButton("Volver") { alSalir() }
            },
        )
    }
}

/** Manteniendo la tecla, cada vez mas lejos: 10 segundos, despues 30, despues un minuto. */
private fun paso(repeticiones: Int): Long = when {
    repeticiones < 4 -> 10_000L
    repeticiones < 12 -> 30_000L
    else -> 60_000L
}

private fun esTeclaDelCine(codigo: Int): Boolean = when (codigo) {
    TeclaAndroid.KEYCODE_DPAD_LEFT, TeclaAndroid.KEYCODE_DPAD_RIGHT,
    TeclaAndroid.KEYCODE_DPAD_UP, TeclaAndroid.KEYCODE_DPAD_DOWN,
    TeclaAndroid.KEYCODE_DPAD_CENTER, TeclaAndroid.KEYCODE_ENTER,
    TeclaAndroid.KEYCODE_NUMPAD_ENTER -> true
    else -> false
}

/**
 * Lo que se dibuja encima del video: la barra, los avisos y la cuenta atras.
 * Va aparte de la pantalla para poder dibujarse sin un reproductor de verdad.
 */
@Composable
fun BoxScope.CineCapas(
    titulo: String,
    subtitulo: String,
    stats: CineStats,
    objetivo: Long?,
    visibles: Boolean,
    esTv: Boolean,
    cuenta: Int,
    onBarra: (Float) -> Unit,
    botones: @Composable () -> Unit,
) {
    val error = stats.error
    if (error != null) {
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
                Text("No se pudo reproducir", color = Tint.fail, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(error, color = Tint.text, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Hint(if (esTv) "OK reintenta · Atrás vuelve a la ficha" else "Toca Volver para salir")
                if (!esTv) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { botones() }
                }
            }
        }
        return
    }

    if (stats.cargando && objetivo == null && cuenta < 0) {
        Box(Modifier.align(Alignment.Center)) {
            Text(
                if (stats.estado.isBlank()) "Cargando…" else stats.estado + "…",
                color = Tint.text,
                fontSize = 16.sp,
                modifier = Modifier
                    .cristal(radio = 40.dp, oscuro = true)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }

    val aviso = stats.aviso
    if (aviso != null && (visibles || stats.enPausa)) {
        Text(
            aviso,
            color = Tint.warn,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 32.dp, end = 32.dp)
                .widthIn(max = 720.dp)
                .cristal(radio = 22.dp, oscuro = true)
                .padding(horizontal = 20.dp, vertical = 13.dp),
        )
    }

    if (cuenta > 0) {
        Column(
            Modifier
                .align(Alignment.Center)
                .cristal(radio = 28.dp, oscuro = true)
                .padding(horizontal = 32.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Siguiente episodio en $cuenta s", color = Tint.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Hint(if (esTv) "OK para verlo ya · Atrás para salir" else "Toca para ver los controles")
        }
        // Durante la cuenta atras, OK ya no es "pausa": la barra de abajo
        // diria una cosa y el aviso otra. Con mando, solo el aviso.
        if (esTv) return
    }

    if (!(visibles || stats.enPausa || objetivo != null)) return

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 28.dp, vertical = 22.dp)
            .fillMaxWidth()
            .cristal(radio = 28.dp, oscuro = true)
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        if (!esTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { botones() }
            Spacer(Modifier.height(14.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    color = Tint.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitulo.isNotBlank()) {
                    Text(subtitulo, color = Tint.textSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (stats.enPausa) {
                Text("EN PAUSA", color = Tint.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        BarraDeTiempo(stats, objetivo, onBarra)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val tiempo = if (objetivo != null) "→ " + reloj(objetivo) else reloj(stats.posMs)
            Text(
                tiempo + if (stats.durMs > 0) "  /  " + reloj(stats.durMs) else "",
                color = Tint.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.weight(1f))
            if (esTv) {
                val ayudas = buildList {
                    add(Ayuda("◀ ▶", "Atrás / adelante", prescindible = 1))
                    add(Ayuda("OK", if (stats.enPausa) "Seguir" else "Pausa", prescindible = 0))
                    if (stats.audios > 1) add(Ayuda("▲", "Sonido: " + stats.audio, prescindible = 2))
                    if (stats.subtitulos > 0) {
                        add(Ayuda("▼", "Subtítulos: " + stats.subtitulo.ifBlank { "no" }, prescindible = 3))
                    }
                }
                Box(Modifier.weight(3f)) { PieDeTeclas(ayudas) }
            }
        }
    }
}

@Composable
private fun BarraDeTiempo(stats: CineStats, objetivo: Long?, onBarra: (Float) -> Unit) {
    val dur = stats.durMs
    val visto = if (dur > 0) (stats.posMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val cargado = if (dur > 0) ((stats.posMs + stats.bufferMs).toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(dur) {
                detectTapGestures { p ->
                    if (size.width > 0) onBarra((p.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxWidth(cargado).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF)))
            Box(Modifier.fillMaxWidth(visto).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Tint.accent))
        }
        if (objetivo != null && dur > 0) {
            val f = (objetivo.toFloat() / dur).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(f).height(18.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxHeight().padding(vertical = 1.dp).widthIn(min = 4.dp, max = 4.dp).clip(RoundedCornerShape(2.dp)).background(Tint.text))
            }
        }
    }
}
