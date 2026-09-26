package com.orbita.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbita.tv.data.ChannelLayout
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.net.UpdateInfo

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

    Row(Modifier.fillMaxSize().padding(skin.screenPad.dp)) {
        // ---- Columna de categorias ----
        Column(
            modifier = Modifier.width(skin.sidebarWidth.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(skin.gap.dp),
        ) {
            if (skin.logoUrl != null) {
                AsyncImage(
                    model = skin.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(40.dp),
                )
            } else {
                Text(
                    "ALEX TV",
                    color = Tint.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            // El aviso de version nueva solo aparece cuando hay una, y arriba,
            // donde el foco llega primero al abrir la pantalla.
            if (update != null) {
                FocusRow(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (updating) "Descargando…" else "Actualizar a la " + update.versionName,
                            color = Tint.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Hint(if (updating) "No cierres la app" else "OK para instalar")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(skin.gap.dp * 0.75f),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    CategoryRow("Todos los canales", selectedCategory == null) { onCategory(null) }
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

        Spacer(Modifier.width(skin.screenPad.dp))

        // ---- Canales ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
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
                    if (skin.layout == ChannelLayout.MOSAICO) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(skin.tileWidth.dp),
                            verticalArrangement = Arrangement.spacedBy(skin.gap.dp),
                            horizontalArrangement = Arrangement.spacedBy(skin.gap.dp),
                        ) {
                            gridItems(channels, key = { it.streamId }) { ch ->
                                ChannelTile(
                                    ch = ch,
                                    mosaico = true,
                                    showLogos = skin.showLogos,
                                    onClick = { onChannel(ch) },
                                    modifier = if (ch == first) {
                                        Modifier.fillMaxWidth().focusRequester(firstChannel)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    },
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(skin.gap.dp)) {
                            items(channels, key = { it.streamId }) { ch ->
                                ChannelTile(
                                    ch = ch,
                                    mosaico = false,
                                    showLogos = skin.showLogos,
                                    onClick = { onChannel(ch) },
                                    modifier = if (ch == first) {
                                        Modifier.fillMaxWidth().focusRequester(firstChannel)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    },
                                )
                            }
                        }
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
