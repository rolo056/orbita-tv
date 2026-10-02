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
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Finding
import com.orbita.tv.diag.Level

@Composable
fun DiagnosticsScreen(
    running: Boolean,
    findings: List<Finding>,
    report: DiagReport?,
    onRun: () -> Unit,
    onExit: () -> Unit,
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Tint.card)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(findings) { f ->
                Row(
                    Modifier.fillMaxWidth().background(Tint.card).padding(14.dp),
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
        }
    }
}

private fun color(level: Level): Color = when (level) {
    Level.OK -> Tint.ok
    Level.WARN -> Tint.warn
    Level.FAIL -> Tint.fail
    Level.INFO -> Tint.textSoft
}
