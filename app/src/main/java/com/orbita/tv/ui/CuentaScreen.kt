package com.orbita.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbita.tv.net.AccountStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Mi cuenta: lo que el panel dice de la cuenta, en palabras.
 *
 * La pregunta que responde casi siempre es una: cuantos aparatos pueden ver a
 * la vez, y cuantos lo estan haciendo ahora.
 */
@Composable
fun CuentaScreen(
    usuario: String,
    servidor: String,
    estado: AccountStatus?,
    cargando: Boolean,
    error: String?,
    onActualizar: () -> Unit,
    onCambiarCuenta: () -> Unit,
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

    val activa = estado?.status?.equals("Active", ignoreCase = true) == true

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 44.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dibujo(Icono.CUENTA, Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Text("Mi cuenta", color = Tint.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        PanelCristal(
            Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            radio = 26.dp,
            padding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            arreglo = Arrangement.spacedBy(14.dp),
        ) {
            Fila("Usuario", usuario)
            Fila("Servidor", servidor)
            Fila(
                "Estado",
                when {
                    estado == null && cargando -> "Consultando…"
                    estado == null -> "Sin datos"
                    else -> estadoLegible(estado.status)
                },
                color = when {
                    estado == null -> Tint.textSoft
                    activa -> Tint.ok
                    else -> Tint.fail
                },
            )
            Fila("Vence", estado?.let { venceLegible(it.expires) } ?: "—")
            Fila(
                "Conexiones",
                if (estado == null) {
                    "—"
                } else {
                    estado.activeConnections.toString() + " en uso de " +
                        estado.maxConnections + " permitidas"
                },
            )
        }

        if (error != null) Text(error, color = Tint.fail, fontSize = 14.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BotonCristal(
                onClick = onActualizar,
                modifier = Modifier.focusRequester(foco),
                radio = 40.dp,
                padding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                alineacion = Alignment.Center,
            ) {
                Text(
                    if (cargando) "Consultando…" else "Actualizar",
                    color = Tint.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            BotonCristal(
                onClick = onCambiarCuenta,
                radio = 40.dp,
                padding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                alineacion = Alignment.Center,
            ) {
                Text("Cambiar de cuenta", color = Tint.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
            if (!esTv) {
                BotonCristal(
                    onClick = onSalir,
                    radio = 40.dp,
                    padding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    alineacion = Alignment.Center,
                ) {
                    Text("Volver", color = Tint.text, fontSize = 16.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        Hint(
            "Las conexiones son los aparatos que pueden estar viendo algo a la vez con esta " +
                "cuenta. Recorrer los menús no ocupa ninguna; un canal o una película sí, " +
                "mientras se reproduce."
        )
    }
}

@Composable
private fun Fila(nombre: String, valor: String, color: Color = Tint.text) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Tint.textSoft, fontSize = 16.sp, modifier = Modifier.width(150.dp), maxLines = 1)
        Text(
            valor,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
    }
}

/** El estado del panel, en castellano. */
fun estadoLegible(estado: String): String = when (estado.trim().lowercase()) {
    "active" -> "Activa"
    "expired" -> "Vencida"
    "banned" -> "Bloqueada por el proveedor"
    "disabled" -> "Desactivada"
    "", "desconocido" -> "Desconocido"
    else -> estado
}

/**
 * "2 de diciembre de 2026". El panel manda la fecha en segundos desde 1970;
 * sin fecha, la cuenta no vence.
 */
fun venceLegible(segundos: String): String {
    val t = segundos.trim().toLongOrNull()
    if (t == null || t <= 0) return "Sin vencimiento"
    val formato = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es"))
    formato.timeZone = TimeZone.getTimeZone("UTC")
    return formato.format(Date(t * 1000))
}
