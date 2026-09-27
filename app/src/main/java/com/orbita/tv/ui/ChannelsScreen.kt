package com.orbita.tv.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.orbita.tv.data.ChannelLayout
import com.orbita.tv.data.Skin
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.net.UpdateInfo

/**
 * Dos disposiciones de la misma pantalla.
 *
 * En un televisor las categorias van en una columna a la izquierda, que es lo
 * natural con mando: bajar por la columna, cruzar a la derecha, elegir canal.
 *
 * En un telefono esa columna no cabe. El tema pide 360 dp de barra lateral,
 * pensados para 1920 de ancho; en un telefono en vertical eso seria la pantalla
 * entera. Asi que por debajo de 600 dp las categorias pasan a una fila que se
 * desliza arriba, y los canales se quedan con todo el ancho.
 *
 * El corte se decide por el ancho disponible y no por si es televisor: una
 * tableta en vertical tiene el mismo problema que un telefono.
 */
@Composable
fun ChannelsScreen(
    categories: List<Category>,
    channels: List<Channel>,
    selectedCategory: String?,
    loading: Boolean,
    error: String?,
    onCategory: (String?) -> Unit,
    onChannel: (Channel) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    update: UpdateInfo?,
    updating: Boolean,
    onUpdate: () -> Unit,
) {
    val skin = Tint.skin
    val firstChannel = remember { FocusRequester() }
    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isNotEmpty()) runCatching { firstChannel.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val pad: Dp = if (compact) (skin.screenPad * 0.4f).dp else skin.screenPad.dp
        // La barra lateral nunca puede comerse la pantalla, diga lo que diga el
        // tema: se recorta al 40 % del ancho disponible.
        val sidebar: Dp = minOf(skin.sidebarWidth.dp, maxWidth * 0.4f)

        if (compact) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Brand(skin, compact = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusRow(onClick = onDiagnostics) {
                            Text("Red", color = Tint.text, fontSize = 14.sp)
                        }
                        FocusRow(onClick = onSettings) {
                            Text("Ajustes", color = Tint.text, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(skin.gap.dp))
                UpdateBanner(update, updating, onUpdate)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        CategoryChip("Todos", selectedCategory == null) { onCategory(null) }
                    }
                    items(categories) { cat ->
                        CategoryChip(cat.name, selectedCategory == cat.id) { onCategory(cat.id) }
                    }
                }
                Spacer(Modifier.height(skin.gap.dp))
                ChannelArea(
                    channels = channels, loading = loading, error = error,
                    skin = skin, firstChannel = firstChannel, onChannel = onChannel,
                )
            }
        } else {
            Row(Modifier.fillMaxSize().padding(pad)) {
                Column(
                    modifier = Modifier.width(sidebar).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(skin.gap.dp),
                ) {
                    Brand(skin, compact = false)
                    Spacer(Modifier.height(4.dp))
                    UpdateBanner(update, updating, onUpdate)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(skin.gap.dp * 0.75f),
                        modifier = Modifier.weight(1f),
                    ) {
                        item {
                            CategoryRow("Todos los canales", selectedCategory == null) {
                                onCategory(null)
                            }
                        }
                        items(categories) { cat ->
                            CategoryRow(cat.name, selectedCategory == cat.id) { onCategory(cat.id) }
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

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ChannelArea(
                        channels = channels, loading = loading, error = error,
                        skin = skin, firstChannel = firstChannel, onChannel = onChannel,
                    )
                }
            }
        }
    }
}

@Composable
private fun Brand(skin: Skin, compact: Boolean) {
    if (skin.logoUrl != null) {
        AsyncImage(
            model = skin.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(if (compact) 28.dp else 40.dp),
        )
    } else {
        BrandMark(size = if (compact) 18.sp else 22.sp)
    }
}

@Composable
private fun UpdateBanner(update: UpdateInfo?, updating: Boolean, onUpdate: () -> Unit) {
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
private fun ChannelArea(
    channels: List<Channel>,
    loading: Boolean,
    error: String?,
    skin: Skin,
    firstChannel: FocusRequester,
    onChannel: (Channel) -> Unit,
) {
    when {
        loading -> Hint("Cargando canales…")
        error != null -> Text(error, color = Tint.fail, fontSize = 15.sp)
        channels.isEmpty() -> Hint(
            "El servidor no devolvió canales. Abre Diagnóstico de red: " +
                "si autentica pero la lista viene vacía, el proveedor está " +
                "bloqueando la IP de salida de Starlink."
        )
        else -> {
            SectionTitle(channels.size.toString() + " CANALES")
            Spacer(Modifier.height(10.dp))
            val first = channels.first()
            fun modFor(ch: Channel): Modifier =
                if (ch == first) Modifier.fillMaxWidth().focusRequester(firstChannel)
                else Modifier.fillMaxWidth()

            if (skin.layout == ChannelLayout.MOSAICO) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(skin.tileWidth.dp),
                    verticalArrangement = Arrangement.spacedBy(skin.gap.dp),
                    horizontalArrangement = Arrangement.spacedBy(skin.gap.dp),
                ) {
                    gridItems(channels, key = { it.streamId }) { ch ->
                        ChannelTile(ch, true, skin.showLogos, { onChannel(ch) }, modFor(ch))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(skin.gap.dp)) {
                    items(channels, key = { it.streamId }) { ch ->
                        ChannelTile(ch, false, skin.showLogos, { onChannel(ch) }, modFor(ch))
                    }
                }
            }
        }
    }
}

/**
 * El mismo canal, en fila o en mosaico. Es una sola pieza a proposito: cambiar
 * la disposicion desde el tema no debe poder dejar las dos vistas distintas.
 */
@Composable
private fun ChannelTile(
    ch: Channel,
    mosaico: Boolean,
    showLogos: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusRow(
        onClick = onClick,
        modifier = modifier,
        padding = if (mosaico) {
            PaddingValues(horizontal = 14.dp, vertical = 16.dp)
        } else {
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        },
    ) {
        if (mosaico) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showLogos && ch.icon != null) {
                    AsyncImage(
                        model = ch.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Text(ch.number.toString(), color = Tint.textSoft, fontSize = 20.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    ch.name,
                    color = Tint.text,
                    fontSize = 15.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(52.dp)) {
                    if (showLogos && ch.icon != null) {
                        AsyncImage(
                            model = ch.icon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(40.dp),
                        )
                    } else {
                        Text(ch.number.toString(), color = Tint.textSoft, fontSize = 15.sp)
                    }
                }
                Text(
                    ch.name,
                    color = Tint.text,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    FocusRow(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            name,
            color = if (selected) Tint.accent else Tint.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    FocusRow(onClick = onClick) {
        Text(
            name,
            color = if (selected) Tint.accent else Tint.text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
