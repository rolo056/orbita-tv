package com.orbita.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.BufferPreset
import com.orbita.tv.data.NetSettings
import com.orbita.tv.data.VariantOrder

@Composable
private fun versionInstalada(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(ctx) {
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            info.versionName + " (" + @Suppress("DEPRECATION") info.versionCode + ")"
        }.getOrDefault("desconocida")
    }
}

/**
 * Cada palanca de aca corresponde a una falla concreta del enlace. La etiqueta
 * dice que arregla, no como se llama por dentro.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onReloadSkin: () -> Unit,
    onForget: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler { onExit() }
    val net = settings.net

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ajustes", color = Tint.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        // La version, a la vista. Sin esto no hay forma de distinguir "el
        // arreglo no funciona" de "el arreglo no esta instalado", y esa duda
        // cuesta horas de depuracion a ciegas.
        Hint("Versión " + versionInstalada())
        Spacer(Modifier.height(10.dp))

        SectionTitle("ESTABILIDAD")
        Toggle(
            title = "Forzar IPv4",
            detail = "Ignora las direcciones IPv6 del servidor. Actívalo si los canales " +
                "tardan mucho en arrancar y después fallan.",
            value = net.forceIpv4,
        ) { onChange(settings.copy(net = net.copy(forceIpv4 = it))) }

        Toggle(
            title = "Una conexión por pedido",
            detail = "Evita HTTP/2. Con pérdida de paquetes, multiplexar congela todo " +
                "el stream por un solo paquete perdido.",
            value = net.forceHttp11,
        ) { onChange(settings.copy(net = net.copy(forceHttp11 = it))) }

        Toggle(
            title = "Resolver nombres por Cloudflare",
            detail = "Usa DNS sobre HTTPS en vez del DNS del router. Actívalo solo si el " +
                "diagnóstico falla al resolver el nombre del servidor.",
            value = net.useDoh,
        ) { onChange(settings.copy(net = net.copy(useDoh = it))) }

        Spacer(Modifier.height(8.dp))
        SectionTitle("BÚFER")
        Hint(
            "Más búfer es más retardo respecto del directo, y es exactamente lo que " +
                "compra aguante para cruzar un corte del satélite sin quedarse en negro."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BufferPreset.entries.forEach { preset ->
                FocusRow(
                    onClick = { onChange(settings.copy(net = net.copy(buffer = preset))) },
                    modifier = Modifier.width(200.dp),
                ) {
                    Column {
                        Text(
                            preset.label,
                            color = if (net.buffer == preset) Tint.accent else Tint.text,
                            fontSize = 16.sp,
                            fontWeight = if (net.buffer == preset) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Hint((preset.minMs / 1000).toString() + " a " + (preset.maxMs / 1000) + " s")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("FORMATO DEL CANAL")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrderOption("Segmentado primero", "Se recupera solo de los cortes. Recomendado con Starlink.",
                net.variantOrder == VariantOrder.HLS_FIRST) {
                onChange(settings.copy(net = net.copy(variantOrder = VariantOrder.HLS_FIRST)))
            }
            OrderOption("Directo primero", "Menos retardo y mejor imagen, pero muere en el primer corte.",
                net.variantOrder == VariantOrder.TS_FIRST) {
                onChange(settings.copy(net = net.copy(variantOrder = VariantOrder.TS_FIRST)))
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("VIGILANTE")
        Hint(
            "Cuando la imagen se congela sin dar error, la app corta y reconecta por su " +
                "cuenta a los " + net.stallSeconds + " segundos."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(5, 8, 12, 20).forEach { secs ->
                FocusRow(
                    onClick = { onChange(settings.copy(net = net.copy(stallSeconds = secs))) },
                    modifier = Modifier.width(120.dp),
                ) {
                    Text(
                        secs.toString() + " s",
                        color = if (net.stallSeconds == secs) Tint.accent else Tint.text,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("IDENTIFICACIÓN ANTE EL PANEL")
        Hint(
            "Algunos paneles solo entregan video a los reproductores que reconocen. Si el " +
                "canal da 403 con la cuenta al día, prueba cambiando esto."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                NetSettings.DEFAULT_UA to "VLC",
                "IPTVSmartersPlayer" to "Smarters",
                "Lavf/60.16.100" to "FFmpeg",
                "Mozilla/5.0 (SmartHub) AppleWebKit/537.36" to "Navegador",
            ).forEach { (ua, label) ->
                FocusRow(
                    onClick = { onChange(settings.copy(net = net.copy(userAgent = ua))) },
                    modifier = Modifier.width(160.dp),
                ) {
                    Text(
                        label,
                        color = if (net.userAgent == ua) Tint.accent else Tint.text,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("DIAGNÓSTICO")
        Toggle(
            title = "Consultar la IP pública al diagnosticar",
            detail = "Pregunta a un servicio externo desde qué ciudad te ve el proveedor. " +
                "Es la forma de confirmar un bloqueo por zona.",
            value = net.allowIpLookup,
        ) { onChange(settings.copy(net = net.copy(allowIpLookup = it))) }

        Spacer(Modifier.height(8.dp))
        SectionTitle("APARIENCIA")
        Hint(
            "La apariencia se lee de un archivo, así que se puede cambiar sin volver a " +
                "instalar la app. Lo que el archivo no diga se queda como está de fábrica, " +
                "y un valor mal escrito se descarta: no hay forma de dejar la pantalla inservible."
        )
        OutlinedTextField(
            value = settings.skinUrl,
            onValueChange = { onChange(settings.copy(skinUrl = it.trim())) },
            label = { Text("Dirección del archivo de apariencia", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Tint.cardFocused,
                unfocusedContainerColor = Tint.card,
                focusedIndicatorColor = Tint.accent,
                unfocusedIndicatorColor = Tint.line,
                focusedLabelColor = Tint.accent,
                unfocusedLabelColor = Tint.textSoft,
                focusedTextColor = Tint.text,
                unfocusedTextColor = Tint.text,
                cursorColor = Tint.accent,
            ),
        )
        Toggle(
            title = "Modo diseño",
            detail = "Consulta la apariencia cada 3 segundos en vez de una sola vez al abrir. " +
                "Déjalo apagado para ver televisión: no tiene sentido pedir el archivo cada " +
                "3 segundos durante horas.",
            value = settings.liveDesign,
        ) { onChange(settings.copy(liveDesign = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusRow(onClick = onReloadSkin, modifier = Modifier.width(260.dp)) {
                Text("Recargar apariencia ahora", color = Tint.text, fontSize = 16.sp)
            }
            FocusRow(
                onClick = {
                    onChange(settings.copy(skinUrl = AppSettings.DEFAULT_SKIN_URL))
                },
                modifier = Modifier.width(240.dp),
            ) {
                Text("Volver al archivo del repo", color = Tint.textSoft, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusRow(onClick = onExit, modifier = Modifier.width(200.dp)) {
                Text("Volver", color = Tint.text, fontSize = 16.sp)
            }
            FocusRow(onClick = onForget, modifier = Modifier.width(260.dp)) {
                Text("Olvidar esta cuenta", color = Tint.warn, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    detail: String,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    FocusRow(onClick = { onToggle(!value) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Tint.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Hint(detail)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                if (value) "activado" else "apagado",
                color = if (value) Tint.ok else Tint.textSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OrderOption(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusRow(onClick = onClick, modifier = Modifier.width(320.dp)) {
        Column {
            Text(
                title,
                color = if (selected) Tint.accent else Tint.text,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Hint(detail)
        }
    }
}
