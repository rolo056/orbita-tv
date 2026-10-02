package com.orbita.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Las piezas que comparten todas las pantallas de contenido: la cabecera con
 * las secciones y el reloj, la fila de categoria y el pie con las teclas.
 */

/** Las partes del servicio. No todos los proveedores traen las tres. */
enum class Seccion(val titulo: String) {
    EN_VIVO("EN VIVO"),
    PELICULAS("PELÍCULAS"),
    SERIES("SERIES"),
}

/** Lo que la cabecera mide por debajo del texto: el aire y la linea. */
private val CABECERA_EXTRA = 9.dp

/**
 * Alto de la cabecera, completo.
 *
 * Sale solo de la escala del texto, sin medir nada. Es a proposito: en la
 * pantalla de canales el video es una capa aparte (ver HomeScreen) y tiene que
 * caer justo en su hueco, debajo de la cabecera, en el mismo cuadro en que se
 * dibuja y no en el siguiente. Para eso el alto tiene que saberse de antemano.
 */
@Composable
fun altoCabecera(): Dp = (58f * LocalDensity.current.fontScale).dp + CABECERA_EXTRA

/**
 * Cabecera de la zona de contenido: cuanto hay, donde estas, las secciones y la
 * hora. El titulo cede espacio (se corta con puntos suspensivos) y lo demas
 * nunca se parte: una hora dibujada letra por letra hacia abajo fue, tal cual,
 * uno de los fallos de esta app.
 */
@Composable
fun Cabecera(
    sobretitulo: String,
    titulo: String,
    hora: String,
    seccion: Seccion,
    secciones: List<Seccion>,
    onSeccion: (Seccion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().height(altoCabecera()).clipToBounds()) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                SectionTitle(sobretitulo, maxLines = 1)
                Text(
                    titulo,
                    color = Tint.text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (secciones.size > 1) {
                Spacer(Modifier.width(16.dp))
                Pestanas(seccion, secciones, onSeccion)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                hora,
                color = Tint.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(CABECERA_EXTRA - 1.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tint.line))
    }
}

/** En vivo, peliculas, series. La que esta abierta va en el color de acento. */
@Composable
fun Pestanas(
    seccion: Seccion,
    secciones: List<Seccion>,
    onSeccion: (Seccion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        secciones.forEach { s ->
            val activa = s == seccion
            FocusRow(
                onClick = { onSeccion(s) },
                padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    s.titulo,
                    color = if (activa) Tint.accent else Tint.text,
                    fontSize = 13.sp,
                    fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun FilaCategoria(
    nombre: String,
    cuenta: Int?,
    activa: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusRow(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                nombre,
                color = if (activa) Tint.accent else Tint.text,
                fontSize = 15.sp,
                fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (cuenta != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    cuenta.toString(),
                    color = Tint.textSoft,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Una ayuda del pie: la tecla, lo que hace y que tan prescindible es. Cuando no
 * caben todas, se va primero la de numero mas alto.
 */
data class Ayuda(val tecla: String, val que: String, val prescindible: Int = 0)

/**
 * El pie con las teclas del mando.
 *
 * Tiene historia. Este pie fue el que escondio los canales: cuando no cabia,
 * cada texto se partia letra por letra hacia abajo, el pie crecia hasta comerse
 * todo el alto y la lista, que solo recibe lo que sobra, se quedaba en cero.
 *
 * Ahora cada ayuda se mide suelta y en una sola linea, asi que el pie mide una
 * linea de alto con cualquier escala de texto. Y las que no caben enteras no se
 * dibujan: mejor tres ayudas completas que cuatro con la ultima cortada a mitad
 * de palabra.
 */
@Composable
fun PieDeTeclas(ayudas: List<Ayuda>, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        content = { ayudas.forEach { Tecla(it.tecla, it.que) } },
    ) { medibles, limites ->
        val sep = 20.dp.roundToPx()
        val piezas = medibles.map { it.measure(Constraints()) }
        val quedan = ArrayList<Int>(piezas.size)
        for (i in piezas.indices) quedan.add(i)

        fun ancho(): Int {
            var total = 0
            for (i in quedan) total += piezas[i].width
            return total + sep * (quedan.size - 1).coerceAtLeast(0)
        }
        while (quedan.size > 1 && ancho() > limites.maxWidth) {
            var peor = quedan[0]
            for (i in quedan) if (ayudas[i].prescindible > ayudas[peor].prescindible) peor = i
            quedan.remove(peor)
        }

        var alto = 0
        for (i in quedan) alto = maxOf(alto, piezas[i].height)
        alto = alto.coerceIn(limites.minHeight, limites.maxHeight)
        val anchoTotal = if (limites.hasBoundedWidth) limites.maxWidth else ancho()
        layout(anchoTotal, alto) {
            var x = 0
            for (i in quedan) {
                val p = piezas[i]
                p.placeRelative(x, (alto - p.height) / 2)
                x += p.width + sep
            }
        }
    }
}

@Composable
private fun Tecla(tecla: String, que: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            tecla,
            color = Tint.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .background(Tint.card)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(que, color = Tint.textSoft, fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

/** Tres digitos, como en la television de toda la vida. */
fun numero(n: Int): String = n.toString().padStart(3, '0')

@Composable
fun relojEnVivo(): String {
    var hora by remember { mutableStateOf(horaActual()) }
    LaunchedEffect(Unit) {
        while (true) {
            hora = horaActual()
            kotlinx.coroutines.delay(15_000)
        }
    }
    return hora
}

private fun horaActual(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
