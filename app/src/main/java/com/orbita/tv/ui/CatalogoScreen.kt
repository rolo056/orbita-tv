package com.orbita.tv.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbita.tv.net.Category

/** Categoria virtual: lo que quedo a medias. No viene del panel. */
const val CAT_SEGUIR = "__seguir__"

/** Lo que se dibuja en una tarjeta del catalogo, sea pelicula o serie. */
data class Tarjeta(
    val id: Int,
    val titulo: String,
    val imagen: String?,
    val detalle: String = "",
    /** De 0 a 1 si quedo a medias; nulo si no se empezo. */
    val avance: Float? = null,
    val favorita: Boolean = false,
)

private val AYUDAS_CATALOGO = listOf(
    Ayuda("▲▼◀▶", "Moverse", prescindible = 2),
    Ayuda("OK", "Abrir", prescindible = 0),
    Ayuda("Mantener OK", "Favorita", prescindible = 1),
)

/**
 * Peliculas o series: las categorias a la izquierda y las portadas en una
 * grilla. La misma pantalla sirve para las dos secciones; cambia lo que se le
 * pasa.
 *
 * Los titulos se piden de a una categoria, nunca el catalogo entero (ver
 * Xtream). Por eso las categorias no llevan cuenta: saberla obligaria a
 * pedirlo todo.
 */
@Composable
fun CatalogoScreen(
    seccion: Seccion,
    secciones: List<Seccion>,
    onSeccion: (Seccion) -> Unit,
    categorias: List<Category>,
    abierta: String?,
    haySeguir: Boolean,
    hayFavoritas: Boolean,
    tarjetas: List<Tarjeta>,
    cargando: Boolean,
    error: String?,
    estadoGrilla: LazyGridState,
    /** La tarjeta que tiene que recibir el foco al volver de su ficha. */
    enfocar: Int?,
    onCategoria: (String) -> Unit,
    onAbrir: (Tarjeta) -> Unit,
    onFavorita: (Tarjeta) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
) {
    val skin = Tint.skin
    val esTv = isTvDevice()
    val hora = relojEnVivo()
    val series = seccion == Seccion.SERIES

    val nombre = when (abierta) {
        CAT_SEGUIR -> "Seguir viendo"
        CAT_FAVORITOS -> if (series) "Mis series" else "Mis películas"
        null -> if (series) "Series" else "Películas"
        else -> categorias.firstOrNull { it.id == abierta }?.name ?: (if (series) "Series" else "Películas")
    }
    val cuantas = if (cargando) "" else tarjetas.size.toString() + " "
    val sobretitulo = cuantas + (if (series) "SERIES" else "PELÍCULAS")
    val vacio = when (abierta) {
        CAT_SEGUIR -> "Lo que dejes a medias aparece aquí, listo para seguir."
        CAT_FAVORITOS -> "Mantén OK sobre una portada para guardarla aquí."
        else -> "Esta categoría está vacía en el servidor del proveedor."
    }

    // Con mando siempre tiene que haber algo enfocado: al volver de una ficha,
    // la tarjeta que se abrio; si no, la primera.
    val focoTarjeta = remember { FocusRequester() }
    val focoPrimera = remember { FocusRequester() }
    val focoDado = remember { booleanArrayOf(false) }
    LaunchedEffect(esTv, tarjetas.isNotEmpty(), abierta) {
        if (!esTv || tarjetas.isEmpty()) return@LaunchedEffect
        if (focoDado[0] && enfocar == null) return@LaunchedEffect
        focoDado[0] = true
        withFrameNanos { }
        val vuelve = enfocar != null && tarjetas.any { it.id == enfocar }
        runCatching { if (vuelve) focoTarjeta.requestFocus() else focoPrimera.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compacto = maxWidth < 600.dp
        val pad: Dp = if (compacto) (skin.screenPad * 0.4f).dp else skin.screenPad.dp

        if (compacto) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                CabeceraCompacta(hora, seccion, secciones, onSeccion, onDiagnostics, onSettings)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (haySeguir) item { Chip("Seguir viendo", abierta == CAT_SEGUIR) { onCategoria(CAT_SEGUIR) } }
                    if (hayFavoritas) item { Chip("Favoritas", abierta == CAT_FAVORITOS) { onCategoria(CAT_FAVORITOS) } }
                    items(categorias) { c -> Chip(c.name, abierta == c.id) { onCategoria(c.id) } }
                }
                Spacer(Modifier.height(skin.gap.dp))
                Grilla(
                    tarjetas, cargando, error, vacio, estadoGrilla, 104.dp, enfocar,
                    focoTarjeta, focoPrimera, onAbrir, onFavorita,
                )
            }
            return@BoxWithConstraints
        }

        val disponible: Dp = maxWidth - pad * 3
        val lateral: Dp = minOf(skin.sidebarWidth.dp, disponible * 0.30f)
        Row(Modifier.fillMaxSize().padding(pad)) {
            BarraLateral(ancho = lateral, onDiagnostics = onDiagnostics, onSettings = onSettings) {
                if (haySeguir) {
                    item { FilaCategoria("Seguir viendo", null, abierta == CAT_SEGUIR) { onCategoria(CAT_SEGUIR) } }
                }
                if (hayFavoritas) {
                    item {
                        FilaCategoria(if (series) "Mis series" else "Mis películas", null, abierta == CAT_FAVORITOS) {
                            onCategoria(CAT_FAVORITOS)
                        }
                    }
                }
                items(categorias) { c ->
                    FilaCategoria(c.name, null, abierta == c.id) { onCategoria(c.id) }
                }
            }
            Spacer(Modifier.width(pad))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Cabecera(
                    sobretitulo = sobretitulo,
                    titulo = nombre,
                    hora = hora,
                    seccion = seccion,
                    secciones = secciones,
                    onSeccion = onSeccion,
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Grilla(
                        tarjetas, cargando, error, vacio, estadoGrilla, 118.dp, enfocar,
                        focoTarjeta, focoPrimera, onAbrir, onFavorita,
                    )
                }
                if (esTv) {
                    Spacer(Modifier.height(10.dp))
                    PieDeTeclas(AYUDAS_CATALOGO)
                }
            }
        }
    }
}

@Composable
private fun Grilla(
    tarjetas: List<Tarjeta>,
    cargando: Boolean,
    error: String?,
    vacio: String,
    estado: LazyGridState,
    anchoMinimo: Dp,
    enfocar: Int?,
    focoTarjeta: FocusRequester,
    focoPrimera: FocusRequester,
    onAbrir: (Tarjeta) -> Unit,
    onFavorita: (Tarjeta) -> Unit,
) {
    val skin = Tint.skin
    when {
        cargando -> Hint("Cargando…")
        error != null -> Text(error, color = Tint.fail, fontSize = 15.sp)
        tarjetas.isEmpty() -> Hint(vacio)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(anchoMinimo),
            state = estado,
            horizontalArrangement = Arrangement.spacedBy(skin.gap.dp + 4.dp),
            verticalArrangement = Arrangement.spacedBy(skin.gap.dp + 4.dp),
            // Aire para que la tarjeta enfocada, que crece, no quede recortada en el borde.
            contentPadding = PaddingValues(6.dp),
        ) {
            itemsIndexed(tarjetas, key = { _, t -> t.id }) { i, t ->
                var m: Modifier = Modifier
                if (i == 0) m = m.focusRequester(focoPrimera)
                if (t.id == enfocar) m = m.focusRequester(focoTarjeta)
                TarjetaDeTitulo(t, m, onClick = { onAbrir(t) }, onLongClick = { onFavorita(t) })
            }
        }
    }
}

@Composable
private fun TarjetaDeTitulo(
    t: Tarjeta,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusRow(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        padding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            val radio = radioDe(Tint.skin)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = radio, topEnd = radio))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                // Debajo de la portada, el titulo: es lo que queda a la vista si
                // la imagen no carga, que con proveedores de IPTV pasa seguido.
                Text(
                    t.titulo,
                    color = Tint.textSoft,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
                if (t.imagen != null) {
                    AsyncImage(
                        model = t.imagen,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (t.favorita) {
                    Text(
                        "★",
                        color = Tint.accent,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .cristal(radio = 20.dp, oscuro = true)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                val avance = t.avance
                if (avance != null && avance > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x99000000)),
                    ) {
                        Box(Modifier.fillMaxWidth(avance).fillMaxHeight().background(Tint.accent))
                    }
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    t.titulo,
                    color = Tint.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t.detalle.ifBlank { " " },
                    color = Tint.textSoft,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
