package com.orbita.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * El logotipo, dibujado con texto en vez de con una imagen.
 *
 * Reproduce la composicion del logotipo para fondo oscuro que entrego el
 * diseno: la palabra en el color del texto, y despues un bloque macizo del
 * color de acento con "TV" dentro, a toda la altura.
 *
 * Se dibuja con texto a proposito, y no convirtiendo el SVG:
 *  - Queda nitido en cualquier televisor y a cualquier tamano, sin arrastrar
 *    medidas de una imagen.
 *  - Toma los colores del tema, asi que si cambia el acento el logotipo cambia
 *    con el resto de la app en vez de quedar desentonado.
 *  - Hereda la tipografia del tema, incluida la que se baje por fuenteUrl.
 *
 * Para el icono de la aplicacion y el banner del televisor esto no sirve: son
 * imagenes dentro del APK y necesitan trazos de verdad. Ver
 * docs/diseno/marca/pedido-al-disenador.md.
 *
 * El token logoUrl del tema sigue teniendo prioridad sobre esto: quien quiera
 * poner una imagen propia sin recompilar, puede.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: TextUnit = 26.sp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "ALEX",
            color = Tint.text,
            fontSize = size,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false,
        )
        // Sin esquinas redondeadas: el bloque macizo es el gesto del logotipo.
        Text(
            text = "TV",
            color = Tint.text,
            fontSize = size,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .background(Tint.accent, RoundedCornerShape(7.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}
