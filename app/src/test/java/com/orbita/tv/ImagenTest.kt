package com.orbita.tv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.orbita.tv.player.describirImagen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lo que el detalle tecnico dice de la imagen de un canal. Es la pista para los
 * canales que se oyen bien y se ven con bloques verdes: si el formato es uno que
 * el aparato no maneja, tiene que decirlo con todas las letras.
 *
 * Con Robolectric porque Media3 usa TextUtils de Android al armar las pistas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImagenTest {

    private fun pistas(formato: Format, soporte: Int): Tracks = Tracks(
        listOf(
            Tracks.Group(
                TrackGroup(formato),
                false,
                intArrayOf(soporte),
                booleanArrayOf(true),
            )
        )
    )

    private fun video(mime: String, codecs: String?) = Format.Builder()
        .setSampleMimeType(mime)
        .setCodecs(codecs)
        .setWidth(1920)
        .setHeight(1080)
        .setFrameRate(25f)
        .build()

    @Test
    fun `una señal de satelite en 4 2 2 se nombra y se dice que el aparato no puede`() {
        assertEquals(
            "H.264 High 4:2:2 · 1920x1080 · 25 fps · EL APARATO NO PUEDE",
            describirImagen(pistas(video(MimeTypes.VIDEO_H264, "avc1.7A0028"), C.FORMAT_EXCEEDS_CAPABILITIES)),
        )
    }

    @Test
    fun `un canal comun se ve como comun`() {
        assertEquals(
            "H.264 High · 1920x1080 · 25 fps · el aparato puede",
            describirImagen(pistas(video(MimeTypes.VIDEO_H264, "avc1.640028"), C.FORMAT_HANDLED)),
        )
    }

    @Test
    fun `H265 de 10 bits y MPEG-2 tambien se nombran`() {
        assertEquals(
            "H.265 Main 10 bits · 1920x1080 · 25 fps · el aparato puede",
            describirImagen(pistas(video(MimeTypes.VIDEO_H265, "hvc1.2.4.L120.90"), C.FORMAT_HANDLED)),
        )
        assertEquals(
            "MPEG-2 · 1920x1080 · 25 fps · EL APARATO NO PUEDE",
            describirImagen(pistas(video(MimeTypes.VIDEO_MPEG2, null), C.FORMAT_UNSUPPORTED_SUBTYPE)),
        )
    }

    @Test
    fun `sin pista de video lo dice`() {
        assertEquals("sin imagen", describirImagen(Tracks.EMPTY))
    }
}
