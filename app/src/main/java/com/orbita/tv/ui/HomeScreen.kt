package com.orbita.tv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.net.UpdateInfo
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.player.ResilientPlayer

/** Categoria virtual: no viene del panel, se arma con los favoritos locales. */
const val CAT_FAVORITOS = "__favoritos__"

/**
 * La pantalla principal: categorias, canales y el video.
 *
 * La decision que ordena todo este archivo: **hay una sola superficie de video**.
 * La vista previa no es un reproductor aparte, es el mismo dibujado pequeno, y
 * "expandir" solo le cambia el tamano. Eso importa por una razon muy concreta:
 * la cuenta permite dos conexiones simultaneas, asi que un segundo reproductor
 * para la miniatura consumiria la mitad del cupo y dejaria al televisor peleando
 * consigo mismo.
 *
 * Tecnicamente se consigue dejando el AndroidView SIEMPRE en el mismo lugar del
 * arbol de composicion, y cambiandole solo el modificador de tamano. Si en vez
 * de eso se dibujara dentro de la columna en un caso y dentro de la pantalla
 * completa en otro, Compose lo tratraria como dos vistas distintas y el canal se
 * reiniciaria en cada cambio.
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
    onToggleFavorite: (Channel) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    update: UpdateInfo?,
    updating: Boolean,
    onUpdate: () -> Unit,
    notice: String? = null,
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

    fun abrir(c: Channel) {
        if (enVentana?.streamId == c.streamId && !pantallaCompleta) {
            pantallaCompleta = true
            mostrarInfo = true
        } else {
            enVentana = c
            onPlay(c)
            if (!esTv) pantallaCompleta = true // en telefono no hay sitio para el recuadro
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

    BackHandler(enabled = pantallaCompleta) { pantallaCompleta = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compacto = maxWidth < 600.dp
        val pad: Dp = if (compacto) (skin.screenPad * 0.4f).dp else skin.screenPad.dp
        val lateral: Dp = minOf(skin.sidebarWidth.dp, maxWidth * 0.4f)
        // La columna derecha aloja la vista previa. En pantallas chicas no hay
        // sitio, asi que no existe y OK va directo a pantalla completa.
        val anchoPrevia: Dp = if (compacto) 0.dp else minOf(520.dp, maxWidth * 0.33f)
        val altoPrevia: Dp = anchoPrevia * 9f / 16f

        if (!pantallaCompleta) {
            Contenido(
                compacto = compacto, pad = pad, lateral = lateral, anchoPrevia = anchoPrevia,
                altoPrevia = altoPrevia, skin = skin, hora = hora,
                categories = categories, conteos = conteos, favoritos = favorites,
                seleccionada = selectedCategory, visibles = visibles,
                loading = loading, error = error, enVentana = enVentana, stats = stats,
                update = update, updating = updating, onUpdate = onUpdate,
                notice = notice,
                onCategory = onCategory, onAbrir = { abrir(it) },
                onFavorito = onToggleFavorite,
                onDiagnostics = onDiagnostics, onSettings = onSettings,
            )
        }

        // ------------------------------------------------------------------
        // La superficie de video. Siempre aqui, en el mismo lugar del arbol:
        // lo unico que cambia entre recuadro y pantalla completa es el tamano.
        // ------------------------------------------------------------------
        Box(
            modifier = (
                if (pantallaCompleta) Modifier.fillMaxSize()
                else Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = pad, end = pad)
                    .width(anchoPrevia)
                    .height(altoPrevia)
                )
                .background(Color.Black)
                .pointerInput(pantallaCompleta) {
                    detectTapGestures {
                        if (pantallaCompleta) mostrarInfo = !mostrarInfo
                        else enVentana?.let { pantallaCompleta = true }
                    }
                },
        ) {
            if (!compacto || pantallaCompleta) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { it.player = engine?.player },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (enVentana == null && !pantallaCompleta) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SectionTitle("SEÑAL DE VIDEO")
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
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> { saltar(-1); true }
                            Key.DirectionDown -> { saltar(1); true }
                            Key.DirectionCenter, Key.Enter -> { mostrarInfo = !mostrarInfo; true }
                            Key.Menu, Key.DirectionRight -> { mostrarHud = !mostrarHud; true }
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
                        TouchButton("Volver") { pantallaCompleta = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun Contenido(
    compacto: Boolean,
    pad: Dp,
    lateral: Dp,
    anchoPrevia: Dp,
    altoPrevia: Dp,
    skin: com.orbita.tv.data.Skin,
    hora: String,
    categories: List<Category>,
    conteos: Map<String?, Int>,
    favoritos: Set<Int>,
    seleccionada: String?,
    visibles: List<Channel>,
    loading: Boolean,
    error: String?,
    enVentana: Channel?,
    stats: PlaybackStats,
    update: UpdateInfo?,
    updating: Boolean,
    onUpdate: () -> Unit,
    notice: String?,
    onCategory: (String?) -> Unit,
    onAbrir: (Channel) -> Unit,
    onFavorito: (Channel) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
) {
    val nombreCat = when (seleccionada) {
        null -> "Todos los canales"
        CAT_FAVORITOS -> "Favoritos"
        else -> categories.firstOrNull { it.id == seleccionada }?.name ?: "Canales"
    }

    if (compacto) {
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BrandMark(size = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(hora, color = Tint.textSoft, fontSize = 14.sp)
                    FocusRow(onClick = onDiagnostics) { Text("Red", color = Tint.text, fontSize = 14.sp) }
                    FocusRow(onClick = onSettings) { Text("Ajustes", color = Tint.text, fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(skin.gap.dp))
            BannerActualizacion(update, updating, onUpdate)
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
            Lista(visibles, favoritos, loading, error, skin, enVentana, onAbrir, onFavorito)
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(pad)) {
        // ---- categorias ----
        Column(
            Modifier.width(lateral).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(skin.gap.dp),
        ) {
            BrandMark(size = 22.sp)
            Spacer(Modifier.height(6.dp))
            SectionTitle("CATEGORÍAS")
            BannerActualizacion(update, updating, onUpdate)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(skin.gap.dp * 0.6f),
                modifier = Modifier.weight(1f),
            ) {
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
            Spacer(Modifier.height(skin.gap.dp))
            FocusRow(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Diagnóstico de red", color = Tint.text, fontSize = 15.sp)
            }
            FocusRow(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Ajustes", color = Tint.text, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.width(pad))

        // ---- canales ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    SectionTitle(visibles.size.toString() + " CANALES")
                    Text(
                        nombreCat,
                        color = Tint.text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(hora, color = Tint.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))
            Aviso(notice)
            Box(Modifier.weight(1f)) {
                Lista(visibles, favoritos, loading, error, skin, enVentana, onAbrir, onFavorito)
            }
            Spacer(Modifier.height(10.dp))
            PieDeTeclas()
        }

        Spacer(Modifier.width(pad))

        // ---- columna derecha: hueco de la vista previa + datos del canal ----
        Column(Modifier.width(anchoPrevia).fillMaxHeight()) {
            Spacer(Modifier.height(altoPrevia))
            Spacer(Modifier.height(12.dp))
            if (enVentana != null) {
                Column(
                    Modifier.fillMaxWidth().background(Tint.card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            Text(
                                numero(enVentana.number),
                                color = Tint.textSoft,
                                fontSize = 12.sp,
                            )
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
                    Hint(stats.status + " · " + stats.variantLabel)
                    if (stats.reconnects > 0) Hint("reconexiones: " + stats.reconnects)
                    Spacer(Modifier.height(2.dp))
                    Hint("OK otra vez para ver en pantalla completa")
                }
            } else {
                Hint("Elige un canal y pulsa OK para verlo en la ventana.")
            }
        }
    }
}

@Composable
private fun Lista(
    visibles: List<Channel>,
    favoritos: Set<Int>,
    loading: Boolean,
    error: String?,
    skin: com.orbita.tv.data.Skin,
    enVentana: Channel?,
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
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(skin.gap.dp)) {
            items(visibles, key = { it.streamId }) { c ->
                FilaCanal(
                    c = c,
                    favorito = c.streamId in favoritos,
                    enVentana = enVentana?.streamId == c.streamId,
                    mostrarLogo = skin.showLogos,
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // El numero SIEMPRE, tambien cuando hay logo: es como la gente
            // identifica un canal y en la version anterior desaparecia.
            Text(
                numero(c.number),
                color = Tint.textSoft,
                fontSize = 14.sp,
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
            if (enVentana) Text("EN VENTANA", color = Tint.accent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FilaCategoria(nombre: String, cuenta: Int, activa: Boolean, onClick: () -> Unit) {
    FocusRow(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                nombre,
                color = if (activa) Tint.accent else Tint.text,
                fontSize = 15.sp,
                fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(cuenta.toString(), color = Tint.textSoft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Chip(texto: String, activa: Boolean, onClick: () -> Unit) {
    FocusRow(onClick = onClick) {
        Text(
            texto,
            color = if (activa) Tint.accent else Tint.text,
            fontSize = 14.sp,
            fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Algo que la app resolvio sola y conviene que se sepa. No es un error. */
@Composable
private fun Aviso(texto: String?) {
    if (texto == null) return
    Column(
        Modifier.fillMaxWidth().background(Tint.card).padding(12.dp),
    ) {
        Text(texto, color = Tint.accent, fontSize = 14.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun BannerActualizacion(update: UpdateInfo?, updating: Boolean, onUpdate: () -> Unit) {
    if (update == null) return
    FocusRow(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                if (updating) "Descargando…" else "Actualizar a la " + update.versionName,
                color = Tint.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Hint(if (updating) "No cierres la app" else "Toca o pulsa OK para instalar")
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PieDeTeclas() {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Tecla("▲▼", "Moverse")
        Tecla("OK", "Ver en ventana")
        Tecla("OK", "otra vez: pantalla completa")
        Tecla("Mantener OK", "Favorito")
    }
}

@Composable
private fun Tecla(tecla: String, que: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            tecla,
            color = Tint.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Tint.card)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(que, color = Tint.textSoft, fontSize = 12.sp)
    }
}

/** Tres digitos, como en la television de toda la vida. */
private fun numero(n: Int): String = n.toString().padStart(3, '0')

@Composable
private fun relojEnVivo(): String {
    var hora by remember { mutableStateOf(horaActual()) }
    LaunchedEffect(Unit) {
        while (true) {
            hora = horaActual()
            kotlinx.coroutines.delay(15_000)
        }
    }
    return hora
}

private fun horaActual(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
