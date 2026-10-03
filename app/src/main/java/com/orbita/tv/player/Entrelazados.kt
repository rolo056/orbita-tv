package com.orbita.tv.player

import android.net.Uri
import android.util.SparseArray
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.BundledHlsMediaChunkExtractor
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.ElementaryStreamReader
import androidx.media3.extractor.ts.H264Reader
import androidx.media3.extractor.ts.PesReader
import androidx.media3.extractor.ts.SeiReader
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsPayloadReader
import java.io.EOFException

/*
 * Canales entrelazados que se ven con bloques verdes y se oyen bien.
 *
 * Muchos canales tomados de satelite vienen entrelazados por campos: cada cuadro
 * llega en dos mitades (las lineas de arriba y las de abajo, "PAFF"), cada una
 * en su propio paquete, y el TS solo le pone hora a la primera. Es valido, y VLC
 * y ffmpeg (lo que usa IPTV Smarters) lo leen bien. Media3 no: tira todo trozo
 * de H.264 que llega sin hora (H264Reader, "if (sampleTimeUs == C.TIME_UNSET)
 * return", igual en 1.5.1 y en la version en desarrollo). Se pierde la segunda
 * mitad de cada cuadro, el decodificador arma la imagen con referencias que no
 * tiene, y salen los bloques verdes. El sonido va por otro lado y no se entera.
 *
 * Comprobado el 3/10/2026 con 43 segundos del canal "DHE HD" del proveedor:
 * H.264 Main 544x480, field_pic_flag en todos los cuadros, la segunda mitad de
 * cada uno sin PTS; ffmpeg decodifica los 1.300 cuadros sin errores.
 *
 * El arreglo: al trozo sin hora se le da la hora del ultimo que la tenia, que
 * es la otra mitad del mismo cuadro. Solo cambia lo que hoy se tira; todo lo
 * demas pasa por el codigo de Media3 sin tocar.
 */

/** La hora de los trozos de H.264 que llegan sin ella: la del ultimo que la tenia. */
private class HoraHeredada(private val lector: H264Reader) : ElementaryStreamReader {
    private var ultima = C.TIME_UNSET

    override fun seek() {
        ultima = C.TIME_UNSET
        lector.seek()
    }

    override fun createTracks(extractorOutput: ExtractorOutput, idGenerator: TsPayloadReader.TrackIdGenerator) =
        lector.createTracks(extractorOutput, idGenerator)

    override fun packetStarted(pesTimeUs: Long, flags: Int) {
        if (pesTimeUs != C.TIME_UNSET) ultima = pesTimeUs
        lector.packetStarted(ultima, flags)
    }

    override fun consume(data: ParsableByteArray) = lector.consume(data)

    override fun packetFinished(isEndOfInput: Boolean) = lector.packetFinished(isEndOfInput)
}

/**
 * Los lectores de un TS: los de Media3 para todo, menos el de H.264, que lleva
 * la hora heredada. Los subtitulos del H.264 (CEA-608 dentro de la imagen) se
 * arman con la misma lista que usaria Media3 cuando el canal no los declara.
 */
internal class LectoresTs(
    private val banderas: Int,
    private val subtitulos: List<Format>,
) : TsPayloadReader.Factory {
    private val base = DefaultTsPayloadReaderFactory(banderas, subtitulos)

    override fun createInitialPayloadReaders(): SparseArray<TsPayloadReader> =
        base.createInitialPayloadReaders()

    override fun createPayloadReader(streamType: Int, esInfo: TsPayloadReader.EsInfo): TsPayloadReader? {
        if (streamType != TsExtractor.TS_STREAM_TYPE_H264 || tiene(DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM)) {
            return base.createPayloadReader(streamType, esInfo)
        }
        return PesReader(
            HoraHeredada(
                H264Reader(
                    SeiReader(subtitulos),
                    tiene(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES),
                    tiene(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS),
                )
            )
        )
    }

    private fun tiene(bandera: Int): Boolean = (banderas and bandera) != 0
}

/** Para el TS directo: el mismo juego de extractores de Media3, con nuestro TS primero. */
internal fun extractoresDeCanal(banderas: Int, busqueda: Int): ExtractorsFactory {
    val base = DefaultExtractorsFactory()
        .setTsExtractorFlags(banderas)
        .setTsExtractorTimestampSearchBytes(busqueda)
    return ExtractorsFactory {
        val ts = TsExtractor(
            TsExtractor.MODE_SINGLE_PMT,
            /* extractorFlags= */ 0,
            DefaultSubtitleParserFactory(),
            TimestampAdjuster(0),
            LectoresTs(banderas, emptyList()),
            busqueda,
        )
        arrayOf<Extractor>(ts) + base.createExtractors().filterNot { it is TsExtractor }
    }
}

/**
 * Para HLS: los segmentos TS se leen con nuestros lectores; cualquier otra cosa
 * (o un TS que no se reconoce) sigue por el camino de Media3.
 */
internal class HlsDeCanal(private val banderas: Int) : HlsExtractorFactory {
    private val base = DefaultHlsExtractorFactory(banderas, /* exposeCea608WhenMissingDeclarations= */ true)
    private var subtitulos: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var subtitulosAlExtraer = true

    override fun createExtractor(
        uri: Uri,
        format: Format,
        muxedCaptionFormats: List<Format>?,
        timestampAdjuster: TimestampAdjuster,
        responseHeaders: Map<String, List<String>>,
        sniffingExtractorInput: ExtractorInput,
        playerId: PlayerId,
    ): HlsMediaChunkExtractor {
        // Lo mismo que hace Media3 con un segmento TS (DefaultHlsExtractorFactory),
        // con nuestros lectores.
        var banderasTs = banderas or DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
        if (muxedCaptionFormats != null) {
            banderasTs = banderasTs or DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS
        }
        val ts = TsExtractor(
            TsExtractor.MODE_HLS,
            if (subtitulosAlExtraer) 0 else TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            if (subtitulosAlExtraer) subtitulos else SubtitleParser.Factory.UNSUPPORTED,
            timestampAdjuster,
            LectoresTs(
                banderasTs,
                muxedCaptionFormats ?: listOf(Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build()),
            ),
            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
        )
        val esTs = try {
            ts.sniff(sniffingExtractorInput)
        } catch (e: EOFException) {
            false
        } finally {
            sniffingExtractorInput.resetPeekPosition()
        }
        if (esTs) return BundledHlsMediaChunkExtractor(ts, format, timestampAdjuster)
        return base.createExtractor(
            uri, format, muxedCaptionFormats, timestampAdjuster, responseHeaders, sniffingExtractorInput, playerId,
        )
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): HlsExtractorFactory {
        subtitulos = subtitleParserFactory
        base.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    override fun experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction: Boolean): HlsExtractorFactory {
        subtitulosAlExtraer = parseSubtitlesDuringExtraction
        base.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
        return this
    }

    override fun getOutputTextFormat(sourceFormat: Format): Format = base.getOutputTextFormat(sourceFormat)
}

/**
 * La fuente de un canal: HLS si la direccion termina en .m3u8, TS directo si no.
 * Lo mismo que decidiria DefaultMediaSourceFactory, pero con los lectores que no
 * tiran la mitad de los canales entrelazados. Cada camino conserva las banderas
 * que ya tenia: las del TS directo para el TS directo, ninguna para HLS.
 */
internal class FuenteDeCanal(datos: DataSource.Factory, banderasTs: Int, busqueda: Int) : MediaSource.Factory {
    private val hls = HlsMediaSource.Factory(datos).setExtractorFactory(HlsDeCanal(/* banderas= */ 0))
    private val directo = ProgressiveMediaSource.Factory(datos, extractoresDeCanal(banderasTs, busqueda))

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        hls.setDrmSessionManagerProvider(drmSessionManagerProvider)
        directo.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        hls.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        directo.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = checkNotNull(mediaItem.localConfiguration).uri
        return if (Util.inferContentType(uri) == C.CONTENT_TYPE_HLS) {
            hls.createMediaSource(mediaItem)
        } else {
            directo.createMediaSource(mediaItem)
        }
    }
}
