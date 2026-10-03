package com.orbita.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbita.tv.data.Avance
import com.orbita.tv.data.Biblioteca
import com.orbita.tv.net.Episode
import com.orbita.tv.net.Movie
import com.orbita.tv.net.MovieInfo
import com.orbita.tv.net.Series
import com.orbita.tv.net.SeriesDetail

/**
 * La ficha de una pelicula: de que trata, cuanto dura y el boton para verla. Si
 * quedo a medias, el boton principal es seguir desde ahi; empezar de nuevo es
 * la segunda opcion, nunca la primera.
 */
@Composable
fun FichaPeliculaScreen(
    pelicula: Movie,
    info: MovieInfo?,
    cargandoInfo: Boolean,
    avance: Avance?,
    favorita: Boolean,
    onReproducir: (desdeMs: Long) -> Unit,
    onFavorita: () -> Unit,
    onSalir: () -> Unit,
) {
    BackHandler { onSalir() }
    val esTv = isTvDevice()
    val foco = remember { FocusRequester() }
    LaunchedEffect(esTv) {
        if (!esTv) return@LaunchedEffect
        withFrameNanos { }
        runCatching { foco.requestFocus() }
    }

    val datos = listOfNotNull(
        pelicula.year ?: info?.released?.let { anioDe(it) },
        duracion(info?.durationSecs ?: 0).ifBlank { null },
        (info?.rating ?: pelicula.rating)?.let { "★ " + unDecimal(it) },
    ).joinToString("  ·  ")

    FondoDeFicha(info?.backdrop) { pad, compacto ->
        val poster = info?.poster ?: pelicula.poster
        if (compacto) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad)) {
                Row {
                    Portada(poster, pelicula.name, Modifier.width(110.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        TituloDeFicha(pelicula.name, 22)
                        Spacer(Modifier.height(6.dp))
                        if (datos.isNotBlank()) Hint(datos)
                        info?.genre?.let { Hint(it) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                BotonesDePelicula(avance, favorita, foco, true, onReproducir, onFavorita, onSalir)
                Spacer(Modifier.height(16.dp))
                TextosDePelicula(info, cargandoInfo, 30)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(pad * 2)) {
                Portada(poster, pelicula.name, Modifier.width(210.dp))
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TituloDeFicha(pelicula.name, 32)
                    Spacer(Modifier.height(8.dp))
                    if (datos.isNotBlank()) Text(datos, color = Tint.textSoft, fontSize = 15.sp, maxLines = 1)
                    info?.genre?.let {
                        Spacer(Modifier.height(2.dp))
                        Hint(it)
                    }
                    Spacer(Modifier.height(20.dp))
                    BotonesDePelicula(avance, favorita, foco, false, onReproducir, onFavorita, onSalir)
                    Spacer(Modifier.height(20.dp))
                    TextosDePelicula(info, cargandoInfo, 7)
                }
            }
        }
    }
}

@Composable
private fun BotonesDePelicula(
    avance: Avance?,
    favorita: Boolean,
    foco: FocusRequester,
    apilados: Boolean,
    onReproducir: (desdeMs: Long) -> Unit,
    onFavorita: () -> Unit,
    onSalir: () -> Unit,
) {
    val botones: @Composable () -> Unit = {
        if (avance != null && avance.aMedias) {
            Boton("▶  Seguir desde " + reloj(avance.posMs), Modifier.focusRequester(foco), principal = true) {
                onReproducir(avance.posMs)
            }
            Boton("Desde el principio") { onReproducir(0L) }
        } else {
            Boton(
                if (avance != null && avance.terminado) "▶  Ver otra vez" else "▶  Reproducir",
                Modifier.focusRequester(foco),
                principal = true,
            ) { onReproducir(0L) }
        }
        Boton(if (favorita) "★  En mis películas" else "☆  Guardar en mis películas") { onFavorita() }
        if (!isTvDevice()) Boton("Volver") { onSalir() }
    }
    if (apilados) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { botones() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { botones() }
    }
}

@Composable
private fun TextosDePelicula(info: MovieInfo?, cargando: Boolean, lineas: Int) {
    when {
        info?.plot != null -> Text(
            info.plot,
            color = Tint.text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = lineas,
            overflow = TextOverflow.Ellipsis,
        )
        cargando -> Hint("Cargando la ficha…")
        else -> Hint("El proveedor no tiene la sinopsis de este título.")
    }
    info?.cast?.let {
        Spacer(Modifier.height(12.dp))
        Hint("Reparto: $it", Modifier)
    }
    info?.director?.let {
        Spacer(Modifier.height(4.dp))
        Hint("Dirección: $it")
    }
}

/**
 * La ficha de una serie: las temporadas arriba y los episodios de la elegida
 * debajo. Abre en la temporada y el episodio donde se quedo.
 */
@Composable
fun FichaSerieScreen(
    serie: Series,
    detalle: SeriesDetail?,
    cargando: Boolean,
    error: String?,
    biblioteca: Biblioteca,
    favorita: Boolean,
    onEpisodio: (Episode, desdeMs: Long) -> Unit,
    onFavorita: () -> Unit,
    onReintentar: () -> Unit,
    onSalir: () -> Unit,
) {
    BackHandler { onSalir() }
    val esTv = isTvDevice()
    val datosSerie = detalle?.series
    val temporadas = detalle?.seasons ?: emptyList()
    val punto = biblioteca.puntos[serie.seriesId]

    // El episodio con el que se sigue: el ultimo abierto si quedo a medias, o
    // el siguiente si se termino.
    val siguiente: Episode? = run {
        if (punto == null) return@run null
        val todos = temporadas.flatMap { it.episodes }
        val i = todos.indexOfFirst { it.id == punto.episodioId }
        if (i < 0) return@run null
        val av = biblioteca.avanceDeEpisodio(todos[i].id)
        if (av != null && av.terminado) todos.getOrNull(i + 1) else todos[i]
    }

    // La temporada a la vista: la que se elija a mano, y si no la del episodio
    // con el que se sigue (que puede ser ya la temporada siguiente).
    var elegida by remember(serie.seriesId) { mutableIntStateOf(-1) }
    val numeroTemporada = when {
        elegida >= 0 && temporadas.any { it.number == elegida } -> elegida
        siguiente != null -> siguiente.season
        else -> temporadas.firstOrNull()?.number ?: -1
    }
    val episodios = temporadas.firstOrNull { it.number == numeroTemporada }?.episodes ?: emptyList()

    val focoEpisodio = remember { FocusRequester() }
    val estadoLista = rememberLazyListState()
    LaunchedEffect(esTv, episodios.isNotEmpty(), numeroTemporada) {
        if (episodios.isEmpty()) return@LaunchedEffect
        val i = episodios.indexOfFirst { it.id == (siguiente?.id ?: -1) }.coerceAtLeast(0)
        if (i > 2) estadoLista.scrollToItem(i - 2)
        if (!esTv) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focoEpisodio.requestFocus() }
    }
    val enfocado = episodios.firstOrNull { it.id == siguiente?.id }?.id ?: episodios.firstOrNull()?.id

    val datos = listOfNotNull(
        (datosSerie?.released ?: serie.released)?.let { anioDe(it) },
        (datosSerie?.genre ?: serie.genre),
        (datosSerie?.rating ?: serie.rating)?.let { "★ " + unDecimal(it) },
    ).joinToString("  ·  ")
    val sinopsis = datosSerie?.plot ?: serie.plot
    val portada = serie.cover ?: datosSerie?.cover

    FondoDeFicha(datosSerie?.backdrop ?: serie.backdrop) { pad, compacto ->
        val cabeza: @Composable () -> Unit = {
            TituloDeFicha(serie.name, if (compacto) 22 else 28)
            Spacer(Modifier.height(6.dp))
            if (datos.isNotBlank()) Hint(datos)
            if (sinopsis != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    sinopsis,
                    color = Tint.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (compacto) 4 else 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Boton(if (favorita) "★  En mis series" else "☆  Guardar en mis series") { onFavorita() }
        }
        val lista: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                when {
                    cargando -> Hint("Cargando temporadas…")
                    error != null -> {
                        Text(error, color = Tint.fail, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        Boton("Reintentar") { onReintentar() }
                    }
                    temporadas.isEmpty() -> Hint("El proveedor no tiene episodios cargados para esta serie.")
                    else -> {
                        SectionTitle("TEMPORADAS", maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(temporadas) { t ->
                                Chip("Temporada " + t.number, t.number == numeroTemporada) {
                                    elegida = t.number
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(
                            state = estadoLista,
                            verticalArrangement = Arrangement.spacedBy(Tint.skin.gap.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            itemsIndexed(episodios, key = { _, e -> e.id }) { _, e ->
                                val av = biblioteca.avanceDeEpisodio(e.id)
                                FilaEpisodio(
                                    e = e,
                                    avance = av,
                                    sigue = e.id == siguiente?.id,
                                    modifier = if (e.id == enfocado) Modifier.focusRequester(focoEpisodio) else Modifier,
                                ) {
                                    onEpisodio(e, if (av != null && av.aMedias) av.posMs else 0L)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (compacto) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row {
                    Portada(portada, serie.name, Modifier.width(96.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { cabeza() }
                }
                Spacer(Modifier.height(14.dp))
                lista(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(pad * 2)) {
                Column(Modifier.width(250.dp).fillMaxHeight()) {
                    Portada(portada, serie.name, Modifier.width(150.dp))
                    Spacer(Modifier.height(14.dp))
                    cabeza()
                }
                Spacer(Modifier.width(32.dp))
                lista(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun FilaEpisodio(
    e: Episode,
    avance: Avance?,
    sigue: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.number.toString(),
                    color = Tint.textSoft,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(36.dp),
                )
                Text(
                    e.title,
                    color = Tint.text,
                    fontSize = 15.sp,
                    fontWeight = if (sigue) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val estado = when {
                    avance != null && avance.terminado -> "Visto"
                    avance != null && avance.aMedias ->
                        "Quedan " + duracion(((avance.durMs - avance.posMs) / 1000).toInt())
                    else -> duracion(e.durationSecs)
                }
                if (estado.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        estado,
                        color = if (avance != null && avance.terminado) Tint.ok else Tint.textSoft,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (sigue) {
                    Spacer(Modifier.width(10.dp))
                    Text("SEGUIR", color = Tint.accent, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
            if (avance != null && avance.aMedias) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(avance.fraccion).fillMaxHeight().background(Tint.accent))
                }
            }
        }
    }
}

// ------------------------------------------------------------- piezas

/**
 * El fondo de las fichas: la imagen grande del titulo, muy apagada, detras de
 * todo. Con mas opacidad el texto encima deja de leerse a tres metros.
 */
@Composable
private fun FondoDeFicha(
    imagen: String?,
    contenido: @Composable (pad: Dp, compacto: Boolean) -> Unit,
) {
    val skin = Tint.skin
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compacto = maxWidth < 600.dp
        if (imagen != null) {
            AsyncImage(
                model = imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.16f),
            )
        }
        val pad: Dp = if (compacto) (skin.screenPad * 0.6f).dp else skin.screenPad.dp.coerceAtLeast(16.dp)
        contenido(pad, compacto)
    }
}

@Composable
private fun Portada(imagen: String?, titulo: String, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .cristal(radio = 22.dp)
            .padding(6.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            titulo,
            color = Tint.textSoft,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
        if (imagen != null) {
            AsyncImage(
                model = imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TituloDeFicha(texto: String, tamano: Int) {
    Text(
        texto,
        color = Tint.text,
        fontSize = tamano.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (tamano * 1.15f).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Boton(
    texto: String,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    onClick: () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        modifier = modifier,
        padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            texto,
            color = if (principal) Tint.accent else Tint.text,
            fontSize = 15.sp,
            fontWeight = if (principal) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun anioDe(fecha: String): String? = Regex("""(19|20)\d{2}""").find(fecha)?.value

private fun unDecimal(x: Float): String {
    val r = Math.round(x * 10f) / 10f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString().replace('.', ',')
}
