package com.orbita.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbita.tv.net.UpdateInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La pantalla de inicio: la puerta a todo lo demas.
 *
 * Sigue la disposicion que el dueño eligio de referencia: la television en vivo
 * en una baldosa grande, peliculas y series al lado, y debajo los botones chicos
 * de cuenta, diagnostico y ajustes. Arriba la hora y la fecha; abajo hasta
 * cuando dura la cuenta, cuantos aparatos pueden ver a la vez y con que usuario
 * se entro, y al pie la firma de quien la diseño. Todo de cristal sobre el
 * fondo con resplandores.
 *
 * Es la unica pantalla con el diagnostico, los ajustes y el aviso de version
 * nueva: las secciones (canales, peliculas, series) muestran solo lo suyo.
 */
@Composable
fun InicioScreen(
    usuario: String,
    vence: String?,
    conexiones: String?,
    subVivo: String?,
    subPeliculas: String?,
    subSeries: String?,
    update: UpdateInfo?,
    updating: Boolean,
    onUpdate: () -> Unit,
    onVivo: () -> Unit,
    onPeliculas: () -> Unit,
    onSeries: () -> Unit,
    onCuenta: () -> Unit,
    onDiagnostico: () -> Unit,
    onAjustes: () -> Unit,
    /** La baldosa que recibe el foco: la ultima que se abrio, para volver a ella. */
    enfocar: String = "vivo",
) {
    val esTv = isTvDevice()
    val hora = relojEnVivo()
    val fecha = remember(hora) { fechaDeHoy() }

    // Con mando, al llegar ya esta parado en algo: la primera vez en la
    // television en vivo, y al volver, en la baldosa de donde se vino.
    val focos = remember {
        listOf("vivo", "peliculas", "series", "cuenta", "diagnostico", "ajustes")
            .associateWith { FocusRequester() }
    }
    LaunchedEffect(esTv) {
        if (!esTv) return@LaunchedEffect
        withFrameNanos { }
        runCatching { (focos[enfocar] ?: focos.getValue("vivo")).requestFocus() }
    }

    val tintePeliculas = Tint.warn
    val tinteSeries = Tint.glow2

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compacto = maxWidth < 600.dp

        if (compacto) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 20.sp)
                    Spacer(Modifier.weight(1f))
                    Text(hora, color = Tint.text, fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
                }
                Text(fecha, color = Tint.textSoft, fontSize = 14.sp, maxLines = 1)
                if (update != null) Actualizar(update, updating, onUpdate, Modifier.fillMaxWidth())
                Baldosa(
                    Icono.TV, "TV en vivo", subVivo, onVivo,
                    Modifier.fillMaxWidth().height(170.dp).focusRequester(focos.getValue("vivo")),
                    icono = 52.dp, titulo = 24.sp, radio = 30.dp, tinte = Tint.accent,
                )
                Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Baldosa(
                        Icono.PELICULA, "Películas", subPeliculas, onPeliculas,
                        Modifier.weight(1f).fillMaxHeight().focusRequester(focos.getValue("peliculas")),
                        icono = 36.dp, titulo = 18.sp, radio = 26.dp, tinte = tintePeliculas,
                    )
                    Baldosa(
                        Icono.SERIE, "Series", subSeries, onSeries,
                        Modifier.weight(1f).fillMaxHeight().focusRequester(focos.getValue("series")),
                        icono = 36.dp, titulo = 18.sp, radio = 26.dp, tinte = tinteSeries,
                    )
                }
                Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BaldosaChica(Icono.CUENTA, "Cuenta", onCuenta, focos.getValue("cuenta"), apilada = true)
                    BaldosaChica(Icono.RED, "Red", onDiagnostico, focos.getValue("diagnostico"), apilada = true)
                    BaldosaChica(Icono.AJUSTES, "Ajustes", onAjustes, focos.getValue("ajustes"), apilada = true)
                }
                Spacer(Modifier.height(4.dp))
                Dato("Vence", vence ?: "—")
                if (conexiones != null) Dato("Conexiones", conexiones)
                Dato("Usuario", usuario)
                Spacer(Modifier.height(4.dp))
                Firma(Modifier.align(Alignment.CenterHorizontally))
            }
            return@BoxWithConstraints
        }

        val altoBaldosas: Dp = (maxHeight * 0.56f).coerceIn(220.dp, 360.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 26.sp)
                Spacer(Modifier.weight(1f))
                if (update != null) {
                    Actualizar(update, updating, onUpdate)
                    Spacer(Modifier.width(22.dp))
                }
                Text(
                    hora,
                    color = Tint.text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(14.dp))
                Text(fecha, color = Tint.textSoft, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth().height(altoBaldosas),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Baldosa(
                    Icono.TV, "TV en vivo", subVivo, onVivo,
                    Modifier.weight(1f).fillMaxHeight().focusRequester(focos.getValue("vivo")),
                    icono = 64.dp, titulo = 26.sp, radio = 32.dp, tinte = Tint.accent,
                )
                Column(
                    Modifier.weight(2.15f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        Modifier.weight(1.3f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Baldosa(
                            Icono.PELICULA, "Películas", subPeliculas, onPeliculas,
                            Modifier.weight(1f).fillMaxHeight().focusRequester(focos.getValue("peliculas")),
                            icono = 44.dp, titulo = 22.sp, radio = 28.dp, tinte = tintePeliculas,
                        )
                        Baldosa(
                            Icono.SERIE, "Series", subSeries, onSeries,
                            Modifier.weight(1f).fillMaxHeight().focusRequester(focos.getValue("series")),
                            icono = 44.dp, titulo = 22.sp, radio = 28.dp, tinte = tinteSeries,
                        )
                    }
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        BaldosaChica(Icono.CUENTA, "Mi cuenta", onCuenta, focos.getValue("cuenta"))
                        BaldosaChica(Icono.RED, "Diagnóstico", onDiagnostico, focos.getValue("diagnostico"))
                        BaldosaChica(Icono.AJUSTES, "Ajustes", onAjustes, focos.getValue("ajustes"))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dato("Vence", vence ?: "—")
                Spacer(Modifier.weight(1f))
                if (conexiones != null) {
                    Dato("Conexiones", conexiones)
                    Spacer(Modifier.weight(1f))
                }
                Dato("Usuario", usuario)
            }

            Spacer(Modifier.height(14.dp))
            Firma(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** "Diseñada por: **Alexander Rosales**", con el nombre en negrita. */
@Composable
private fun Firma(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("Diseñada por: ")
            withStyle(SpanStyle(color = Tint.text, fontWeight = FontWeight.Bold)) {
                append("Alexander Rosales")
            }
        },
        modifier = modifier,
        color = Tint.textSoft,
        fontSize = 14.sp,
        maxLines = 1,
        softWrap = false,
    )
}

/** Una baldosa grande o mediana: el icono, el nombre y, debajo, un dato. */
@Composable
private fun Baldosa(
    dibujo: Icono,
    texto: String,
    sub: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    icono: Dp,
    titulo: TextUnit,
    radio: Dp,
    tinte: Color,
) {
    BotonCristal(
        onClick = onClick,
        modifier = modifier,
        radio = radio,
        padding = PaddingValues(16.dp),
        tinte = tinte,
        alineacion = Alignment.Center,
        escalaFoco = 1.04f,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Dibujo(dibujo, Modifier.size(icono))
            Spacer(Modifier.height(12.dp))
            Text(
                texto,
                color = Tint.text,
                fontSize = titulo,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    sub,
                    color = Tint.text.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Un boton chico de la fila de abajo: icono y nombre en una linea o, si no hay
 * ancho (un telefono parado), el icono arriba del nombre.
 */
@Composable
private fun RowScope.BaldosaChica(
    icono: Icono,
    texto: String,
    onClick: () -> Unit,
    foco: FocusRequester,
    apilada: Boolean = false,
) {
    BotonCristal(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(foco),
        radio = 22.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        alineacion = Alignment.Center,
        escalaFoco = 1.05f,
    ) {
        val nombre: @Composable () -> Unit = {
            Text(
                texto,
                color = Tint.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (apilada) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Dibujo(icono, Modifier.size(24.dp))
                Spacer(Modifier.height(6.dp))
                nombre()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dibujo(icono, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                nombre()
            }
        }
    }
}

/** El aviso de version nueva, como una pastilla de cristal junto a la hora. */
@Composable
private fun Actualizar(update: UpdateInfo, updating: Boolean, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    BotonCristal(
        onClick = onUpdate,
        modifier = modifier,
        radio = 40.dp,
        padding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
        tinte = Tint.accent,
        alineacion = Alignment.Center,
    ) {
        Text(
            if (updating) "Descargando…" else "Actualizar a la " + update.versionName,
            color = Tint.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$nombre:  ", color = Tint.textSoft, fontSize = 15.sp, maxLines = 1, softWrap = false)
        Text(valor, color = Tint.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** "Viernes, 3 de octubre". */
private fun fechaDeHoy(): String {
    val texto = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es")).format(Date())
    return texto.replaceFirstChar { it.uppercase() }
}
