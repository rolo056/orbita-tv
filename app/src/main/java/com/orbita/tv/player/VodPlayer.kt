package com.orbita.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.orbita.tv.data.AppSettings
import com.orbita.tv.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Lo que la pantalla necesita saber de una pelicula o un episodio en curso. */
data class CineStats(
    val estado: String = "Abriendo",
    val posMs: Long = 0,
    val durMs: Long = 0,
    val bufferMs: Long = 0,
    val reproduciendo: Boolean = false,
    val enPausa: Boolean = false,
    val cargando: Boolean = true,
    val terminado: Boolean = false,
    val reintentos: Int = 0,
    val kbps: Long = 0,
    /** No impide ver, pero hay que decirlo: por ejemplo, que el sonido no se puede decodificar. */
    val aviso: String? = null,
    val error: String? = null,
    /** Pistas de sonido que este aparato puede reproducir, y la que esta puesta. */
    val audios: Int = 0,
    val audio: String = "",
    /** Subtitulos que trae el archivo, y el que esta puesto (vacio: ninguno). */
    val subtitulos: Int = 0,
    val subtitulo: String = "",
)

/**
 * El reproductor de peliculas y episodios.
 *
 * Es otro que el de los canales en vivo, y no por comodidad: un canal es un
 * chorro sin fin que se pide de tres formas, y una pelicula es un archivo con
 * principio y fin que se pide de una sola. Lo que aca importa es otra cosa:
 *
 *  1. Volver al mismo segundo. Si se corta la conexion, se vuelve a pedir el
 *     archivo desde donde iba, no desde el principio. Para eso el servidor
 *     tiene que aceptar pedidos parciales, y el puente los deja pasar.
 *  2. Un tope de memoria de verdad. Una pelicula en buena calidad son varios
 *     megas por cada diez segundos, y guardar dos minutos por adelantado tira
 *     la app en un televisor con poca memoria. El bufer se corta por tamano.
 *  3. Decir la verdad sobre los formatos. Un televisor viejo puede no saber
 *     decodificar el sonido o la imagen de un archivo. Eso no se arregla
 *     reintentando, asi que se dice tal cual en vez de dejar una pantalla muda.
 *
 * Nunca convive con el reproductor de canales: quien lo crea suelta antes el
 * otro. Una cuenta tiene pocas conexiones y cada reproductor ocupa una.
 */
class VodPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
) {
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

    val player: ExoPlayer = build()

    private val _stats = MutableStateFlow(CineStats())
    val stats: StateFlow<CineStats> = _stats.asStateFlow()

    private var url = ""
    private var ultimaPos = 0L
    private var arranco = false
    private var intentos = 0
    private var pausadoPorUsuario = false
    private var enSegundoPlano = false
    private var liberado = false
    private var reintentoJob: Job? = null
    private val relojJob: Job

    // Vigilante: lo mismo que en los canales, el corte tipico no da error.
    private var posAnterior = -1L
    private var quietoMs = 0L
    private var cargandoMs = 0L
    private var sanoMs = 0L

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                alFallar(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) arranco = true
                if (playbackState == Player.STATE_ENDED && _stats.value.error == null) {
                    _stats.value = _stats.value.copy(terminado = true)
                }
                publicar()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publicar()
            }

            override fun onTracksChanged(tracks: Tracks) {
                revisarPistas(tracks)
            }
        })
        relojJob = scope.launch {
            while (true) {
                delay(PASO_MS)
                latido()
            }
        }
    }

    private fun build(): ExoPlayer {
        val net = settings.net

        // El tope es por tamano, no por tiempo: ver el punto 2 de arriba.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 90_000, 2_000, 4_000)
            .setTargetBufferBytes(TOPE_DE_BUFER)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(10_000, true)
            .build()

        val httpFactory = OkHttpDataSource.Factory(Http.client(net, readTimeoutSeconds = 30))
            .setUserAgent(net.userAgent)

        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory, extractors)
            .setLoadErrorHandlingPolicy(Paciente())

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setRenderersFactory(
                DefaultRenderersFactory(context).setEnableDecoderFallback(true)
            )
            .build()
            .also {
                it.setWakeMode(C.WAKE_MODE_NETWORK)
                // El sonido en espanol cuando el archivo trae varios. Y los
                // subtitulos apagados salvo que se pidan: muchos archivos marcan
                // "por omision" unos subtitulos en ingles que nadie eligio.
                it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("es", "spa")
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
    }

    /** Insistir con los cortes de red; no insistir con lo que no se arregla insistiendo. */
    private class Paciente : DefaultLoadErrorHandlingPolicy() {
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 6

        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val e = info.exception
            if (e is HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode == 401 || e.responseCode == 403 || e.responseCode == 404) {
                    return C.TIME_UNSET
                }
            }
            return minOf(800L * info.errorCount, 5_000L)
        }
    }

    // ------------------------------------------------------------ control

    fun abrir(direccion: String, desdeMs: Long) {
        reintentoJob?.cancel()
        url = direccion
        ultimaPos = desdeMs.coerceAtLeast(0)
        intentos = 0
        arranco = false
        pausadoPorUsuario = false
        _stats.value = CineStats(posMs = ultimaPos)
        preparar(ultimaPos)
    }

    private fun preparar(desdeMs: Long) {
        if (liberado || url.isEmpty()) return
        reiniciarVigilante()
        player.setMediaItem(MediaItem.fromUri(url), desdeMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = !pausadoPorUsuario
        publicar()
    }

    fun alternarPausa() {
        if (liberado || _stats.value.error != null) return
        pausadoPorUsuario = player.playWhenReady
        player.playWhenReady = !pausadoPorUsuario
        publicar()
    }

    fun pausar() {
        if (liberado) return
        pausadoPorUsuario = true
        player.playWhenReady = false
        publicar()
    }

    fun seguir() {
        if (liberado || _stats.value.error != null) return
        pausadoPorUsuario = false
        player.playWhenReady = true
        publicar()
    }

    /** Salta a un punto. Los saltos se piden ya sumados: ver CineScreen. */
    fun irA(ms: Long) {
        if (liberado || _stats.value.error != null) return
        val dur = _stats.value.durMs
        val destino = if (dur > 0) ms.coerceIn(0L, (dur - 2_000L).coerceAtLeast(0L)) else ms.coerceAtLeast(0L)
        ultimaPos = destino
        if (_stats.value.terminado) _stats.value = _stats.value.copy(terminado = false)
        reintentoJob?.cancel()
        if (player.playbackState == Player.STATE_IDLE) {
            preparar(destino)
        } else {
            reiniciarVigilante()
            player.seekTo(destino)
        }
        publicar()
    }

    /** Reintento pedido a mano despues de un fallo. Sigue desde donde iba. */
    fun reintentar() {
        if (liberado) return
        intentos = 0
        _stats.value = _stats.value.copy(error = null, estado = "Abriendo", cargando = true)
        preparar(ultimaPos)
    }

    /** Pasa a la pista de sonido siguiente. Con una sola no hace nada. */
    fun siguienteAudio() {
        if (liberado) return
        val grupos = player.currentTracks.groups.filter {
            it.type == C.TRACK_TYPE_AUDIO && it.isSupported
        }
        if (grupos.size < 2) return
        val actual = grupos.indexOfFirst { it.isSelected }
        val proximo = grupos[(actual + 1) % grupos.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(proximo.mediaTrackGroup, 0))
            .build()
    }

    /** Recorre los subtitulos: ninguno, el primero, el segundo... y de vuelta a ninguno. */
    fun siguienteSubtitulo() {
        if (liberado) return
        val grupos = player.currentTracks.groups.filter {
            it.type == C.TRACK_TYPE_TEXT && it.isSupported
        }
        if (grupos.isEmpty()) return
        val actual = grupos.indexOfFirst { it.isSelected }
        val b = player.trackSelectionParameters.buildUpon()
        if (actual + 1 >= grupos.size) {
            b.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            b.setOverrideForType(TrackSelectionOverride(grupos[actual + 1].mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = b.build()
    }

    /** Por donde va, aunque el reproductor este detenido por un fallo. */
    fun posicion(): Long = if (liberado) ultimaPos else maxOf(ultimaPos, 0L)

    // ------------------------------------------------------------- fallos

    private fun alFallar(error: PlaybackException) {
        val causa = error.cause
        if (causa is HttpDataSource.InvalidResponseCodeException) {
            when (causa.responseCode) {
                401, 403 -> {
                    fatal(
                        "El servidor rechazó la conexión (" + causa.responseCode + "). Suele ser " +
                            "el límite de la cuenta: hay otra cosa abierta con el mismo usuario " +
                            "en otro aparato."
                    )
                    return
                }
                404 -> {
                    fatal("Este título ya no está en el servidor del proveedor.")
                    return
                }
            }
        }
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> {
                fatal(
                    "Este aparato no puede decodificar este video. No es la conexión: es el " +
                        "formato del archivo (" + error.errorCodeName + ")."
                )
                return
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> {
                fatal(
                    "El archivo de este título está dañado o tiene un formato que la app no " +
                        "sabe leer (" + error.errorCodeName + ")."
                )
                return
            }
        }
        reconectar(describir(error))
    }

    private fun describir(error: PlaybackException): String = when {
        error.cause is java.net.UnknownHostException -> "No se resolvió el nombre del servidor"
        error.cause is java.net.SocketTimeoutException -> "El servidor dejó de responder"
        error.cause is java.net.ConnectException -> "No se pudo abrir la conexión"
        else -> "Se cortó la conexión"
    }

    private fun reconectar(motivo: String) {
        reintentoJob?.cancel()
        intentos++
        if (intentos > ESPERAS_MS.size) {
            fatal(
                motivo + " y no volvió después de varios intentos. Pulsa OK para reintentar: " +
                    "sigue desde donde estaba."
            )
            return
        }
        val espera = ESPERAS_MS[intentos - 1]
        _stats.value = _stats.value.copy(
            estado = motivo + " · reintento " + intentos,
            cargando = true,
            reproduciendo = false,
            reintentos = _stats.value.reintentos + 1,
        )
        reintentoJob = scope.launch {
            delay(espera)
            preparar(ultimaPos)
        }
    }

    private fun fatal(mensaje: String) {
        reintentoJob?.cancel()
        if (!liberado) player.stop()
        _stats.value = _stats.value.copy(
            estado = "Detenido",
            error = mensaje,
            cargando = false,
            reproduciendo = false,
        )
    }

    private fun revisarPistas(tracks: Tracks) {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val texto = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        val sinAudio = audio.isNotEmpty() && audio.none { it.isSupported }
        val sinVideo = video.isNotEmpty() && video.none { it.isSupported }

        if (sinAudio && sinVideo) {
            fatal(
                "Este aparato no puede decodificar ni la imagen (" + codec(video) + ") ni el " +
                    "sonido (" + codec(audio) + ") de este video. No es la conexión: es el formato."
            )
            return
        }
        val aviso = when {
            sinVideo -> "Este aparato no puede decodificar la imagen de este video (" +
                codec(video) + "). Se oye pero no se ve."
            sinAudio -> "Este aparato no puede decodificar el sonido de este video (" +
                codec(audio) + "). Se ve pero no se oye."
            else -> null
        }
        val audiosUtiles = audio.filter { it.isSupported }
        val audioEnUso = audiosUtiles.indexOfFirst { it.isSelected }
        val textoEnUso = texto.indexOfFirst { it.isSelected }
        _stats.value = _stats.value.copy(
            aviso = aviso,
            audios = audiosUtiles.size,
            audio = if (audioEnUso >= 0) etiqueta(audiosUtiles[audioEnUso], audioEnUso) else "",
            subtitulos = texto.size,
            subtitulo = if (textoEnUso >= 0) etiqueta(texto[textoEnUso], textoEnUso) else "",
        )
    }

    private fun codec(grupos: List<Tracks.Group>): String {
        val mime = grupos.firstOrNull()?.takeIf { it.length > 0 }?.getTrackFormat(0)?.sampleMimeType
        return nombreDeCodec(mime)
    }

    private fun etiqueta(grupo: Tracks.Group, posicion: Int): String {
        val formato = grupo.getTrackFormat(0)
        val idioma = formato.language
            ?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { runCatching { Locale(it).getDisplayLanguage(Locale("es")) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { c -> c.uppercase() }
        val titulo = formato.label?.takeIf { it.isNotBlank() }
        return when {
            idioma != null && titulo != null && !titulo.equals(idioma, ignoreCase = true) ->
                "$idioma · $titulo"
            idioma != null -> idioma
            titulo != null -> titulo
            else -> "Pista " + (posicion + 1)
        }
    }

    // ---------------------------------------------------------- vigilante

    private fun reiniciarVigilante() {
        posAnterior = -1L
        quietoMs = 0L
        cargandoMs = 0L
    }

    private fun latido() {
        if (liberado || enSegundoPlano) return
        val s = _stats.value
        if (s.error != null || s.terminado) return

        val estado = player.playbackState
        val pos = player.currentPosition
        if ((estado == Player.STATE_READY || estado == Player.STATE_BUFFERING) && pos > 0) {
            ultimaPos = pos
        }
        when (estado) {
            Player.STATE_BUFFERING -> {
                if (player.playWhenReady) cargandoMs += PASO_MS
                quietoMs = 0L
                sanoMs = 0L
            }
            Player.STATE_READY -> {
                cargandoMs = 0L
                if (player.isPlaying) {
                    if (pos == posAnterior) {
                        quietoMs += PASO_MS
                        sanoMs = 0L
                    } else {
                        quietoMs = 0L
                        sanoMs += PASO_MS
                    }
                } else {
                    quietoMs = 0L
                }
            }
            else -> Unit
        }
        posAnterior = pos
        // Medio minuto andando bien: el proximo corte vuelve a tener todos sus intentos.
        if (sanoMs >= 30_000L) intentos = 0

        publicar()

        if (reintentoJob?.isActive == true) return
        if (cargandoMs >= SIN_DATOS_MS) {
            reiniciarVigilante()
            player.stop()
            reconectar("El servidor dejó de mandar datos")
        } else if (quietoMs >= CONGELADO_MS) {
            reiniciarVigilante()
            player.stop()
            reconectar("La imagen se congeló")
        }
    }

    private fun publicar() {
        if (liberado) return
        val s = _stats.value
        if (s.error != null) return
        val estado = player.playbackState
        val dur = player.duration.let { if (it == C.TIME_UNSET || it < 0) 0L else it }
        val pos = if (estado == Player.STATE_IDLE) ultimaPos else player.currentPosition.coerceAtLeast(0L)
        val esperando = reintentoJob?.isActive == true
        val texto = when {
            s.terminado -> "Terminado"
            esperando -> s.estado
            estado == Player.STATE_BUFFERING -> if (arranco) "Cargando" else "Abriendo"
            estado == Player.STATE_READY && player.playWhenReady -> "Reproduciendo"
            estado == Player.STATE_READY -> "En pausa"
            else -> s.estado
        }
        _stats.value = s.copy(
            estado = texto,
            posMs = pos,
            durMs = if (dur > 0) dur else s.durMs,
            bufferMs = player.totalBufferedDuration.coerceAtLeast(0L),
            reproduciendo = player.isPlaying,
            enPausa = estado == Player.STATE_READY && !player.playWhenReady,
            cargando = esperando || estado == Player.STATE_BUFFERING,
            kbps = bandwidthMeter.bitrateEstimate / 1000,
        )
    }

    // ---------------------------------------------------------- ciclo de vida

    /**
     * La app dejo de estar en pantalla. Se suelta la conexion: una pelicula en
     * pausa detras del televisor apagado sigue ocupando la cuenta.
     */
    fun onBackground() {
        if (liberado || enSegundoPlano) return
        enSegundoPlano = true
        reintentoJob?.cancel()
        val pos = player.currentPosition
        if (pos > 0 && player.playbackState != Player.STATE_IDLE) ultimaPos = pos
        player.stop()
    }

    /** Volvio a pantalla: sigue desde donde quedo. */
    fun onForeground() {
        if (liberado || !enSegundoPlano) return
        enSegundoPlano = false
        val s = _stats.value
        if (url.isEmpty() || s.error != null || s.terminado) return
        preparar(ultimaPos)
    }

    fun release() {
        if (liberado) return
        val pos = player.currentPosition
        if (pos > 0 && player.playbackState != Player.STATE_IDLE) ultimaPos = pos
        liberado = true
        reintentoJob?.cancel()
        relojJob.cancel()
        player.release()
    }

    companion object {
        private const val PASO_MS = 500L

        /** Cuanto se espera sin recibir nada antes de pedir el archivo de nuevo. */
        private const val SIN_DATOS_MS = 30_000L
        private const val CONGELADO_MS = 12_000L

        /** Las esperas entre reintentos. Cuando se acaban, se deja de insistir. */
        private val ESPERAS_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)

        /** 32 megas por adelantado: unos 30 segundos de una pelicula en buena calidad. */
        private const val TOPE_DE_BUFER = 32 * 1024 * 1024

        fun nombreDeCodec(mime: String?): String = when (mime) {
            null -> "formato desconocido"
            MimeTypes.AUDIO_AC3 -> "Dolby Digital, AC-3"
            MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus, E-AC-3"
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
            MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
            MimeTypes.VIDEO_H265 -> "HEVC, H.265"
            MimeTypes.VIDEO_AV1 -> "AV1"
            MimeTypes.VIDEO_VP9 -> "VP9"
            MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
            MimeTypes.VIDEO_H264 -> "H.264"
            else -> mime.substringAfter('/')
        }
    }
}
