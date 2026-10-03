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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.orbita.tv.data.Skin
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.player.ResilientPlayer

/** Categoria virtual: no viene del panel, se arma con los favoritos locales. */
const val CAT_FAVORITOS = "__favoritos__"

/** Aire entre la cabecera y lo que va debajo. */
private val BAJO_CABECERA = 10.dp

private val AYUDAS_CANALES = listOf(
    Ayuda("▲▼", "Moverse", prescindible = 3),
    Ayuda("OK", "Ver en ventana", prescindible = 0),
    Ayuda("OK ×2", "Pantalla completa", prescindible = 1),
    Ayuda("Mantener OK", "Favorito", prescindible = 2),
)

/**
 * La pantalla de canales en vivo: categorias, canales y el video.
 *
 * La decision que ordena todo este archivo: **hay una sola superficie de video**.
 * La vista previa no es un reproductor aparte, es el mismo dibujado pequeno, y
 * "expandir" solo le cambia el tamano. Eso importa por una razon muy concreta:
 * la cuenta permite pocas conexiones simultaneas, asi que un segundo reproductor
 * para la miniatura consumiria una y dejaria al televisor peleando consigo mismo.
 *
 * Tecnicamente se consigue dejando el AndroidView SIEMPRE en el mismo lugar del
 * arbol de composicion, y cambiandole solo el modificador de tamano. Si en vez
 * de eso se dibujara dentro de la columna en un caso y dentro de la pantalla
 * completa en otro, Compose lo trataria como dos vistas distintas y el canal se
 * reiniciaria en cada cambio.
 *
 * Por lo mismo la lista sigue compuesta debajo del video a pantalla completa: al
 * volver esta donde se la dejo, con el foco en el canal que se estaba mirando.
 *
 * El recuadro es cosa de televisor: con mando se recorre la lista mirando un
 * canal de reojo. Con el dedo se toca y se mira, asi que en un telefono no hay
 * recuadro ni teclas en el pie, y al salir de la pantalla completa el canal se
 * cierra de verdad.
 *
 * El canal se abre con OK, no al mover el foco: recorrer la lista no debe abrir
 * un stream por canal.
 */
@Composable
fun HomeScreen(
    categories: List<Category>,
    allChannels: List<Channel>,
    selectedCategory: String?,
    favorites: Set<Int>,
    loading: Boolean,
    error: String?,
    engine: ResilientPlayer?,
    stats: PlaybackStats,
    onCategory: (String?) -> Unit,
    onPlay: (Channel) -> Unit,
    onStop: () -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    notice: String? = null,
    seccion: Seccion = Seccion.EN_VIVO,
    secciones: List<Seccion> = listOf(Seccion.EN_VIVO),
    onSeccion: (Seccion) -> Unit = {},
) {
    val skin = Tint.skin
    val esTv = isTvDevice()
    val ctx = LocalContext.current

    var enVentana by remember { mutableStateOf<Channel?>(null) }
    var pantallaCompleta by remember { mutableStateOf(false) }
    var mostrarInfo by remember { mutableStateOf(true) }
    var mostrarHud by remember { mutableStateOf(false) }

    val visibles = remember(selectedCategory, allChannels, favorites) {
        when (selectedCategory) {
            null -> allChannels
            CAT_FAVORITOS -> allChannels.filter { it.streamId in favorites }
            else -> allChannels.filter { it.categoryId == selectedCategory }
        }
    }
    val conteos = remember(allChannels) { allChannels.groupingBy { it.categoryId }.eachCount() }

    val hora = relojEnVivo()

    val estadoLista = rememberLazyListState()
    val focoCanal = remember { FocusRequester() }
    val focoPrimero = remember { FocusRequester() }

    // Al salir de esta pantalla el canal se corta. Un canal sonando detras de
    // Ajustes o de las peliculas ocupa una conexion de la cuenta, y con cuentas
    // de una sola conexion hace fallar al diagnostico y a lo que se abra despues.
    val alSalir by rememberUpdatedState(onStop)
    DisposableEffect(Unit) { onDispose { alSalir() } }

    LaunchedEffect(selectedCategory) { estadoLista.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compacto = maxWidth < 600.dp
        val conPrevia = esTv && !compacto
        val pad: Dp = if (compacto) (skin.screenPad * 0.4f).dp else skin.screenPad.dp

        // Reparto del ancho, con una regla por encima del tema: la lista de
        // canales es lo que esta pantalla existe para mostrar, y nunca puede
        // quedarse sin sitio.
        //
        // Viene de un caso real: el tema traia la barra lateral en 360, pensada
        // como pixeles de una maqueta de 1920 pero aplicada como dp. Entre la
        // barra, los margenes y la vista previa, a los canales les quedaban 123
        // dp y el nombre se dibujaba en tres. Los canales estaban ahi y no se
        // veian. Ahora, si no caben, se recorta primero la vista previa —que es
        // un lujo— y despues la barra lateral.
        val disponible: Dp = maxWidth - pad * (if (conPrevia) 4 else 3)
        val listaMinima: Dp = 300.dp
        var lateral: Dp = minOf(skin.sidebarWidth.dp, disponible * 0.30f)
        var anchoPrevia: Dp = if (conPrevia) minOf(520.dp, disponible * 0.34f) else 0.dp
        var falta: Dp = listaMinima - (disponible - lateral - anchoPrevia)
        if (falta > 0.dp && anchoPrevia > 0.dp) {
            val recorte = minOf(falta, (anchoPrevia - 180.dp).coerceAtLeast(0.dp))
            anchoPrevia -= recorte
            falta -= recorte
        }
        if (falta > 0.dp) {
            lateral = (lateral - falta).coerceAtLeast(140.dp)
        }
        val altoPrevia: Dp = anchoPrevia * 9f / 16f
        // Donde empieza el cuerpo, debajo de la cabecera: ahi cae el recuadro.
        val arribaDelCuerpo: Dp = pad + altoCabecera() + BAJO_CABECERA

        fun abrir(c: Channel) {
            val mismo = enVentana?.streamId == c.streamId
            when {
                // El canal se cayo y se rindio: OK sobre el mismo lo reintenta.
                mismo && stats.fatalError != null -> onPlay(c)
                mismo && conPrevia -> {
                    pantallaCompleta = true
                    mostrarInfo = true
                }
                else -> {
                    enVentana = c
                    onPlay(c)
                    if (!conPrevia) {
                        pantallaCompleta = true
                        mostrarInfo = true
                    }
                }
            }
        }

        fun saltar(paso: Int) {
            if (visibles.isEmpty()) return
            val i = visibles.indexOfFirst { it.streamId == enVentana?.streamId }
            val n = if (i < 0) 0 else ((i + paso) % visibles.size + visibles.size) % visibles.size
            enVentana = visibles[n]
            onPlay(visibles[n])
            mostrarInfo = true
        }

        fun cerrarCompleta() {
            pantallaCompleta = false
            mostrarHud = false
            if (!conPrevia) {
                // Sin recuadro no queda donde seguir mirando. Antes el canal
                // seguia sonando sin imagen detras de la lista.
                enVentana = null
                onStop()
            }
        }

        // El video se mira en horizontal; el resto puede rotar libre en un telefono.
        DisposableEffect(pantallaCompleta) {
            val activity = ctx as? Activity
            val anterior = activity?.requestedOrientation
            if (pantallaCompleta) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            onDispose {
                if (activity != null && anterior != null) activity.requestedOrientation = anterior
            }
        }

        LaunchedEffect(stats.channelName, mostrarInfo, pantallaCompleta) {
            if (pantallaCompleta && mostrarInfo) {
                kotlinx.coroutines.delay(5_000)
                mostrarInfo = false
            }
        }

        BackHandler(enabled = pantallaCompleta) { cerrarCompleta() }

        // Con mando siempre tiene que haber algo enfocado. Al abrir, el primer
        // canal; al volver de la pantalla completa, el canal que se miraba, que
        // pudo cambiar con arriba y abajo mientras tanto.
        val focoInicialDado = remember { booleanArrayOf(false) }
        LaunchedEffect(esTv, visibles.isNotEmpty()) {
            if (!esTv || focoInicialDado[0] || visibles.isEmpty()) return@LaunchedEffect
            focoInicialDado[0] = true
            withFrameNanos { }
            runCatching { focoPrimero.requestFocus() }
        }
        val veniaDeCompleta = remember { booleanArrayOf(false) }
        LaunchedEffect(pantallaCompleta) {
            if (pantallaCompleta) {
                veniaDeCompleta[0] = true
                return@LaunchedEffect
            }
            if (!veniaDeCompleta[0]) return@LaunchedEffect
            veniaDeCompleta[0] = false
            val i = visibles.indexOfFirst { it.streamId == enVentana?.streamId }
            if (i < 0) return@LaunchedEffect
            val aLaVista = estadoLista.layoutInfo.visibleItemsInfo.any { it.index == i }
            if (!aLaVista) estadoLista.scrollToItem((i - 2).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { focoCanal.requestFocus() }
        }

        Contenido(
            compacto = compacto, conPrevia = conPrevia, conMando = esTv,
            pad = pad, lateral = lateral, anchoPrevia = anchoPrevia, altoPrevia = altoPrevia,
            skin = skin, hora = hora,
            seccion = seccion, secciones = secciones, onSeccion = onSeccion,
            categories = categories, conteos = conteos, favoritos = favorites,
            seleccionada = selectedCategory, visibles = visibles,
            loading = loading, error = error, enVentana = enVentana, stats = stats,
            estadoLista = estadoLista, focoCanal = focoCanal, focoPrimero = focoPrimero,
            notice = notice,
            onCategory = onCategory, onAbrir = { abrir(it) },
            onFavorito = onToggleFavorite,
        )

        // ------------------------------------------------------------------
        // La superficie de video. Siempre aqui, en el mismo lugar del arbol:
        // lo unico que cambia entre recuadro y pantalla completa es el tamano.
        // ------------------------------------------------------------------
        Box(
            modifier = (
                if (pantallaCompleta) Modifier.fillMaxSize()
                else Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = arribaDelCuerpo, end = pad)
                    .width(anchoPrevia)
                    .height(altoPrevia)
                    .cristal(radio = 22.dp)
                )
                .then(if (pantallaCompleta) Modifier.background(Color.Black) else Modifier)
                .pointerInput(pantallaCompleta) {
                    detectTapGestures {
                        if (pantallaCompleta) {
                            mostrarInfo = !mostrarInfo
                        } else if (enVentana != null) {
                            pantallaCompleta = true
                            mostrarInfo = true
                        }
                    }
                },
        ) {
            if (conPrevia || pantallaCompleta) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = {
                        it.player = engine?.player
                        // Sin tocar el mando durante horas, el televisor no
                        // tiene que saltar al protector de pantalla.
                        it.keepScreenOn = enVentana != null
                    },
                    // En el recuadro, la imagen va dentro del marco de cristal y
                    // con las esquinas redondeadas; a pantalla completa, entera.
                    modifier = if (pantallaCompleta) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .padding(7.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    },
                )
            }
            if (conPrevia && !pantallaCompleta) {
                val rotulo = when {
                    enVentana == null -> "SEÑAL DE VIDEO"
                    stats.fatalError != null -> "CANAL DETENIDO"
                    stats.retryRound > 0 -> "SIN SEÑAL"
                    else -> null
                }
                if (rotulo != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SectionTitle(rotulo, maxLines = 1)
                    }
                }
            }
        }

        // ---- capas sobre el video, solo en pantalla completa ----
        if (pantallaCompleta) {
            val foco = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(foco)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        val codigo = e.nativeKeyEvent.keyCode
                        // Las flechas no pueden escaparse de aqui: debajo sigue
                        // la lista, y un foco que se va a una fila tapada por el
                        // video termina abriendo un canal que nadie eligio.
                        val esFlecha = codigo == TeclaAndroid.KEYCODE_DPAD_UP ||
                            codigo == TeclaAndroid.KEYCODE_DPAD_DOWN ||
                            codigo == TeclaAndroid.KEYCODE_DPAD_LEFT ||
                            codigo == TeclaAndroid.KEYCODE_DPAD_RIGHT
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent esFlecha
                        when (codigo) {
                            TeclaAndroid.KEYCODE_DPAD_UP,
                            TeclaAndroid.KEYCODE_CHANNEL_DOWN -> { saltar(-1); true }
                            TeclaAndroid.KEYCODE_DPAD_DOWN,
                            TeclaAndroid.KEYCODE_CHANNEL_UP -> { saltar(1); true }
                            TeclaAndroid.KEYCODE_DPAD_CENTER,
                            TeclaAndroid.KEYCODE_ENTER,
                            TeclaAndroid.KEYCODE_NUMPAD_ENTER -> {
                                // Con el canal detenido, OK es "reintentar": el
                                // boton de la pantalla no se alcanza con el mando.
                                if (stats.fatalError != null) engine?.retryNow()
                                else mostrarInfo = !mostrarInfo
                                true
                            }
                            TeclaAndroid.KEYCODE_MENU,
                            TeclaAndroid.KEYCODE_DPAD_RIGHT -> { mostrarHud = !mostrarHud; true }
                            TeclaAndroid.KEYCODE_DPAD_LEFT -> true
                            else -> false
                        }
                    },
            ) {
                when {
                    stats.fatalError != null ->
                        PlayerFatal(stats, esTv) { engine?.retryNow() }
                    stats.retryRound > 0 -> PlayerRetryNotice(stats)
                }
                if (mostrarInfo && stats.fatalError == null) PlayerInfoBar(stats, esTv)
                if (mostrarHud) PlayerHud(stats)
                // Una imagen quieta sin explicacion parece la app colgada. Si
                // no hay imagen corriendo, arriba dice por que.
                if (!stats.onAir && stats.fatalError == null && stats.retryRound == 0 &&
                    stats.status.isNotBlank() && enVentana != null && !mostrarHud
                ) {
                    Text(
                        stats.status + "…",
                        color = Tint.text,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .cristal(radio = 40.dp, oscuro = true)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
                if (!esTv && stats.fatalError == null) {
                    Row(
                        Modifier.align(Alignment.TopStart).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TouchButton("Canal −") { saltar(-1) }
                        TouchButton("Canal +") { saltar(1) }
                        TouchButton(if (mostrarHud) "Ocultar detalle" else "Detalle") {
                            mostrarHud = !mostrarHud
                        }
                        TouchButton("Volver") { cerrarCompleta() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Contenido(
    compacto: Boolean,
    conPrevia: Boolean,
    conMando: Boolean,
    pad: Dp,
    lateral: Dp,
    anchoPrevia: Dp,
    altoPrevia: Dp,
    skin: Skin,
    hora: String,
    seccion: Seccion,
    secciones: List<Seccion>,
    onSeccion: (Seccion) -> Unit,
    categories: List<Category>,
    conteos: Map<String?, Int>,
    favoritos: Set<Int>,
    seleccionada: String?,
    visibles: List<Channel>,
    loading: Boolean,
    error: String?,
    enVentana: Channel?,
    stats: PlaybackStats,
    estadoLista: LazyListState,
    focoCanal: FocusRequester,
    focoPrimero: FocusRequester,
    notice: String?,
    onCategory: (String?) -> Unit,
    onAbrir: (Channel) -> Unit,
    onFavorito: (Channel) -> Unit,
) {
    val nombreCat = when (seleccionada) {
        null -> "Todos los canales"
        CAT_FAVORITOS -> "Favoritos"
        else -> categories.firstOrNull { it.id == seleccionada }?.name ?: "Canales"
    }

    if (compacto) {
        Column(Modifier.fillMaxSize().padding(pad)) {
            CabeceraCompacta(hora, seccion, secciones, onSeccion)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Todos", seleccionada == null) { onCategory(null) } }
                if (favoritos.isNotEmpty()) {
                    item {
                        Chip("Favoritos " + favoritos.size, seleccionada == CAT_FAVORITOS) {
                            onCategory(CAT_FAVORITOS)
                        }
                    }
                }
                items(categories) { c ->
                    Chip(c.name, seleccionada == c.id) { onCategory(c.id) }
                }
            }
            Spacer(Modifier.height(skin.gap.dp))
            Aviso(notice)
            Lista(
                visibles, favoritos, loading, error, skin, enVentana, conPrevia,
                estadoLista, focoCanal, focoPrimero, onAbrir, onFavorito,
            )
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(pad)) {
        // ---- categorias ----
        BarraLateral(ancho = lateral) {
            item {
                FilaCategoria("Todos", conteos.values.sum(), seleccionada == null) {
                    onCategory(null)
                }
            }
            if (favoritos.isNotEmpty()) {
                item {
                    FilaCategoria("Favoritos", favoritos.size, seleccionada == CAT_FAVORITOS) {
                        onCategory(CAT_FAVORITOS)
                    }
                }
            }
            items(categories) { c ->
                FilaCategoria(c.name, conteos[c.id] ?: 0, seleccionada == c.id) {
                    onCategory(c.id)
                }
            }
        }

        Spacer(Modifier.width(pad))

        // ---- a la derecha de la barra: cabecera, cuerpo y pie, a todo el ancho ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Cabecera(
                sobretitulo = visibles.size.toString() + " CANALES",
                titulo = nombreCat,
                hora = hora,
                seccion = seccion,
                secciones = secciones,
                onSeccion = onSeccion,
            )
            Spacer(Modifier.height(BAJO_CABECERA))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Aviso(notice)
                    Lista(
                        visibles, favoritos, loading, error, skin, enVentana, conPrevia,
                        estadoLista, focoCanal, focoPrimero, onAbrir, onFavorito,
                    )
                }
                if (conPrevia) {
                    Spacer(Modifier.width(pad))
                    // El hueco del recuadro, que se dibuja aparte, y los datos del canal.
                    Column(Modifier.width(anchoPrevia).fillMaxHeight()) {
                        Spacer(Modifier.height(altoPrevia + 12.dp))
                        FichaDelCanal(enVentana, stats)
                    }
                }
            }
            if (conMando) {
                Spacer(Modifier.height(10.dp))
                PieDeTeclas(AYUDAS_CANALES)
            }
        }
    }
}

@Composable
private fun FichaDelCanal(enVentana: Channel?, stats: PlaybackStats) {
    if (enVentana == null) {
        Hint("Elige un canal y pulsa OK para verlo en esta ventana.")
        return
    }
    val falla = stats.fatalError
    PanelCristal(
        Modifier.fillMaxWidth(),
        radio = 22.dp,
        padding = PaddingValues(18.dp),
        arreglo = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (enVentana.icon != null) {
                AsyncImage(
                    model = enVentana.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(numero(enVentana.number), color = Tint.textSoft, fontSize = 12.sp, maxLines = 1)
                Text(
                    enVentana.name,
                    color = Tint.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Hint(
            if (stats.variantLabel.isBlank()) stats.status
            else stats.status + " · " + stats.variantLabel
        )
        if (stats.reconnects > 0) Hint("reconexiones: " + stats.reconnects)
        if (falla != null) {
            Text(
                falla,
                color = Tint.fail,
                fontSize = 13.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        Hint(
            if (falla != null) "OK sobre el mismo canal para reintentar"
            else "OK otra vez para ver en pantalla completa"
        )
    }
}

@Composable
private fun Lista(
    visibles: List<Channel>,
    favoritos: Set<Int>,
    loading: Boolean,
    error: String?,
    skin: Skin,
    enVentana: Channel?,
    conPrevia: Boolean,
    estado: LazyListState,
    focoCanal: FocusRequester,
    focoPrimero: FocusRequester,
    onAbrir: (Channel) -> Unit,
    onFavorito: (Channel) -> Unit,
) {
    when {
        loading -> Hint("Cargando canales…")
        error != null -> Text(error, color = Tint.fail, fontSize = 15.sp)
        visibles.isEmpty() -> Hint(
            "No hay canales para mostrar. Si el panel autentica pero la lista " +
                "viene vacía, el proveedor está bloqueando la IP de salida."
        )
        else -> LazyColumn(
            state = estado,
            verticalArrangement = Arrangement.spacedBy(skin.gap.dp),
            // Aire para el halo y el leve agrandado del foco: sin esto, la
            // primera y la ultima fila quedan cortadas contra el borde.
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        ) {
            itemsIndexed(visibles, key = { _, c -> c.streamId }) { i, c ->
                val actual = enVentana?.streamId == c.streamId
                var m: Modifier = Modifier.fillMaxWidth()
                if (i == 0) m = m.focusRequester(focoPrimero)
                if (actual) m = m.focusRequester(focoCanal)
                FilaCanal(
                    c = c,
                    favorito = c.streamId in favoritos,
                    enVentana = actual && conPrevia,
                    mostrarLogo = skin.showLogos,
                    modifier = m,
                    onClick = { onAbrir(c) },
                    onLongClick = { onFavorito(c) },
                )
            }
        }
    }
}

@Composable
private fun FilaCanal(
    c: Channel,
    favorito: Boolean,
    enVentana: Boolean,
    mostrarLogo: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // El numero SIEMPRE, tambien cuando hay logo: es como la gente
            // identifica un canal y en la version anterior desaparecia.
            Text(
                numero(c.number),
                color = Tint.textSoft,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(46.dp),
            )
            if (mostrarLogo && c.icon != null) {
                AsyncImage(
                    model = c.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                c.name,
                color = Tint.text,
                fontSize = 16.sp,
                fontWeight = if (enVentana) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (favorito) {
                Text("★", color = Tint.accent, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
            }
            if (enVentana) {
                Text("EN VENTANA", color = Tint.accent, fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Algo que la app resolvio sola y conviene que se sepa. No es un error. */
@Composable
private fun Aviso(texto: String?) {
    if (texto == null) return
    PanelCristal(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Text(texto, color = Tint.accent, fontSize = 14.sp)
    }
    Spacer(Modifier.height(10.dp))
}
