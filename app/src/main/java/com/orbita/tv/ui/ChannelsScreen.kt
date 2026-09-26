package com.orbita.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel

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
) {
    val firstChannel = remember { FocusRequester() }
    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isNotEmpty()) runCatching { firstChannel.requestFocus() }
    }

    Row(Modifier.fillMaxSize().padding(24.dp)) {
        // ---- Columna de categorias ----
        Column(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Órbita TV", color = Tint.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                item {
                    CategoryRow("Todos los canales", selectedCategory == null) { onCategory(null) }
                }
                items(categories) { cat ->
                    CategoryRow(cat.name, selectedCategory == cat.id) { onCategory(cat.id) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FocusRow(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Diagnóstico de red", color = Tint.text, fontSize = 15.sp)
            }
            FocusRow(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Ajustes", color = Tint.text, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.width(24.dp))

        // ---- Lista de canales ----
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
                    val state = rememberLazyListState()
                    LazyColumn(
                        state = state,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(channels, key = { it.streamId }) { ch ->
                            val mod = if (ch == channels.first()) {
                                Modifier.fillMaxWidth().focusRequester(firstChannel)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                            FocusRow(onClick = { onChannel(ch) }, modifier = mod) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(52.dp)) {
                                        if (ch.icon != null) {
                                            AsyncImage(
                                                model = ch.icon,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(40.dp),
                                            )
                                        } else {
                                            Text(
                                                ch.number.toString(),
                                                color = Tint.textSoft,
                                                fontSize = 15.sp,
                                            )
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
                }
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
