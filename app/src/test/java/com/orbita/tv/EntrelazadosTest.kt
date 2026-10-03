package com.orbita.tv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.orbita.tv.player.HlsDeCanal
import com.orbita.tv.player.LectoresTs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Los canales entrelazados por campos traen la segunda mitad de cada cuadro sin
 * hora, y Media3 la tira: de ahi los bloques verdes (ver Entrelazados.kt).
 *
 * campos-sin-hora.ts reproduce esa rareza con video de prueba de ffmpeg: 50
 * trozos de video, 25 de ellos sin hora. Lo arma
 * tools/pruebas/hacer_campos_sin_hora.py.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntrelazadosTest {

    private val ts: ByteArray = File("src/test/resources/campos-sin-hora.ts").readBytes()

    /** Lo que llega al decodificador: cuantos trozos de video y con que hora. */
    private class Video : TrackOutput {
        val horas = mutableListOf<Long>()
        val tamanos = mutableListOf<Int>()
        private val basura = ByteArray(64 * 1024)

        override fun format(format: Format) = Unit

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            val leidos = input.read(basura, 0, minOf(length, basura.size))
            return if (leidos == C.RESULT_END_OF_INPUT && !allowEndOfInput) error("fin inesperado") else leidos
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            data.skipBytes(length)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            horas += timeUs
            tamanos += size
        }
    }

    private class Salida : ExtractorOutput {
        val video = Video()
        private val otra = Video()
        override fun track(id: Int, type: Int): TrackOutput = if (type == C.TRACK_TYPE_VIDEO) video else otra
        override fun endTracks() = Unit
        override fun seekMap(seekMap: SeekMap) = Unit
    }

    private fun entrada(): ExtractorInput {
        val fuente = ByteArrayDataSource(ts)
        fuente.open(DataSpec(Uri.EMPTY))
        // Largo desconocido, como un canal en vivo: el extractor no intenta
        // saltar al final para medir la duracion.
        return DefaultExtractorInput(fuente, 0, C.LENGTH_UNSET.toLong())
    }

    private fun leer(extractor: Extractor): Video {
        val salida = Salida()
        extractor.init(salida)
        val entrada = entrada()
        val posicion = PositionHolder()
        while (extractor.read(entrada, posicion) == Extractor.RESULT_CONTINUE) Unit
        return salida.video
    }

    @Test
    fun `Media3 sin tocar tira la mitad de los trozos de video`() {
        val video = leer(
            TsExtractor(TsExtractor.MODE_SINGLE_PMT, TimestampAdjuster(0), DefaultTsPayloadReaderFactory(0), TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES)
        )
        assertTrue(
            "con Media3 tal cual llegan solo los trozos con hora: ${video.horas.size}",
            video.horas.size in 24..25,
        )
    }

    @Test
    fun `con la hora heredada llegan todos, y el que no tenia hora lleva la del anterior`() {
        val video = leer(
            TsExtractor(
                TsExtractor.MODE_SINGLE_PMT,
                0,
                DefaultSubtitleParserFactory(),
                TimestampAdjuster(0),
                LectoresTs(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, emptyList()),
                TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
            )
        )
        // El ultimo puede quedar sin cerrar al terminar el archivo: 49 o 50.
        val n = video.horas.size
        assertTrue("llegan todos los trozos: $n", n in 49..50)
        for (i in 1 until n step 2) {
            assertEquals("el trozo $i hereda la hora del ${i - 1}", video.horas[i - 1], video.horas[i])
        }
        for (i in 2 until n step 2) {
            assertTrue("las horas propias siguen avanzando", video.horas[i] > video.horas[i - 2])
        }
        assertTrue("ningun trozo vacio", video.tamanos.all { it > 0 })
    }

    @Test
    fun `por HLS tambien llegan todos`() {
        val fabrica = HlsDeCanal(0)
        val ajuste = TimestampAdjuster(0)
        val sondeo = entrada()
        val trozo = fabrica.createExtractor(
            Uri.parse("http://203.0.113.10/hls/138_1.ts"),
            Format.Builder().build(),
            null,
            ajuste,
            emptyMap(),
            sondeo,
            PlayerId.UNSET,
        )
        val salida = Salida()
        trozo.init(salida)
        val entrada = entrada()
        while (trozo.read(entrada)) Unit
        // Con los lectores de Media3 (si el segmento no se hubiera reconocido
        // como TS) llegarian 25.
        val n = salida.video.horas.size
        assertTrue("por HLS llegan todos los trozos: $n", n in 49..50)
    }
}
