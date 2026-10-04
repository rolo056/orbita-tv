package com.orbita.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Falla
import com.orbita.tv.diag.Finding
import com.orbita.tv.diag.Level
import com.orbita.tv.diag.Registro

@Composable
fun DiagnosticsScreen(
    running: Boolean,
    findings: List<Finding>,
    report: DiagReport?,
    onRun: () -> Unit,
    onExit: () -> Unit,
    /** Lo que el aparato anoto cada vez que algo fallo, lo mas reciente primero. */
    fallas: List<Falla> = emptyList(),
) {
    BackHandler { onExit() }

    Column(Modifier.fillMaxSize().padding(40.dp)) {
        Text("Diagnóstico de red", color = Tint.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Hint(
            "Prueba en este orden: nombre del servidor, puerto por IPv4 y por IPv6, " +
                "estado de la cuenta, conexiones en uso, lista de canales y caudal real del video."
        )
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusRow(onClick = onRun, modifier = Modifier.width(260.dp)) {
                Text(
                    if (running) "Probando…" else "Ejecutar diagnóstico",
                    color = Tint.text,
                    fontSize = 16.sp,
                )
            }
            FocusRow(onClick = onExit, modifier = Modifier.width(180.dp)) {
                Text("Volver", color = Tint.text, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (report != null) {
            PanelCristal(
                Modifier.fillMaxWidth(),
                radio = 24.dp,
                padding = androidx.compose.foundation.layout.PaddingValues(22.dp),
                arreglo = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("CONCLUSIÓN")
                // La conclusion lleva el color de lo peor que se encontro. Con
                // el acento fijo, un "todo en orden" salia en rojo.
                val tono = when {
                    report.findings.any { it.level == Level.FAIL } -> Tint.fail
                    report.findings.any { it.level == Level.WARN } -> Tint.warn
                    else -> Tint.ok
                }
                Text(report.verdict, color = tono, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                report.advice.forEach { line ->
                    Text("· " + line, color = Tint.text, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Aire para el halo de la fila enfocada del registro.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        ) {
            items(findings) { f ->
                Row(
                    Modifier.fillMaxWidth().cristal(radio = 18.dp).padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(10.dp)
                            .background(color(f.level), CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(f.step, color = Tint.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(f.detail, color = Tint.textSoft, fontSize = 13.sp)
                    }
                }
            }

            // El registro de fallas. Va en la misma lista y cada fila se puede
            // enfocar: con mando, una lista sin nada enfocable no se desplaza.
            item {
                Spacer(Modifier.height(10.dp))
                SectionTitle("REGISTRO DE FALLAS")
                Spacer(Modifier.height(4.dp))
                Hint(
                    if (fallas.isEmpty()) {
                        "Todavía no hay nada anotado. Cada vez que un canal o la lista fallen, " +
                            "aquí queda la hora y cómo estaba la red de este aparato en ese momento."
                    } else {
                        "Lo que este aparato anotó cada vez que algo falló, lo más reciente primero. " +
                            "Dice si en ese momento llegaba al proveedor, si tenía internet y cómo estaba el wifi."
                    }
                )
            }
            items(fallas) { f -> FilaDeFalla(f) }
        }
    }
}

@Composable
private fun FilaDeFalla(f: Falla) {
    FocusRow(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                Registro.hora(f) + "  —  " + f.que,
                color = Tint.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                Registro.veredicto(f),
                color = if (Registro.esGrave(f)) Tint.fail else Tint.warn,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(Registro.medidas(f), color = Tint.textSoft, fontSize = 13.sp)
            Text(f.motivo, color = Tint.textSoft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun color(level: Level): Color = when (level) {
    Level.OK -> Tint.ok
    Level.WARN -> Tint.warn
    Level.FAIL -> Tint.fail
    Level.INFO -> Tint.textSoft
}
