package com.orbita.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.orbita.tv.data.AppSettings
import com.orbita.tv.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo que se ve en el HUD y lo que explica por que se cayo el canal. */
data class PlaybackStats(
    val channelName: String = "",
    val variantLabel: String = "",
    val variantUrl: String = "",
    val status: String = "En espera",
    val bufferedSeconds: Int = 0,
    val kbps: Long = 0,
    val reconnects: Int = 0,
    val healthySeconds: Int = 0,
    /** Mayor que cero cuando se agoto la escalera y se sigue insistiendo despacio. */
    val retryRound: Int = 0,
    val fatalError: String? = null,
)

/**
 * ExoPlayer con la logica que le falta a cualquier reproductor generico en un
 * enlace satelital.
 *
 * Tres piezas, y la tercera es la que de verdad importa:
 *
 *  1. Buferes grandes y retardo deliberado respecto del directo. Pagar 20 o 35
 *     segundos de retardo compra el margen para atravesar un corte sin que se
 *     vacie el bufer.
 *  2. Escalera de reintentos: mismo enlace tres veces con espera creciente,
 *     despues la siguiente forma del stream (HLS a TS a ruta antigua), y como
 *     ultimo recurso el modo endurecido (solo IPv4 y TS). Lo que funciona se
 *     recuerda: tras un minuto sano la escalera se reinicia ahi.
 *  3. Vigilante de congelamiento. La falla tipica de Starlink no es un error:
 *     el socket TCP queda abierto pero deja de traer bytes, y ExoPlayer se
 *     queda esperando para siempre sin lanzar excepcion. El vigilante compara
 *     la posicion de reproduccion cada segundo y, si no avanza, corta y
 *     reconecta por su cuenta. Sin esto, la pantalla se queda congelada.
 */
class ResilientPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private var settings: AppSettings,
) {

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

    val player: ExoPlayer = build()

    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats.asStateFlow()

    private var variants: List<Variant> = emptyList()
    private var currentStreamId = -1
    private var variantIndex = 0
    private var attemptsOnVariant = 0
    private var hardened = false
    private var retryJob: Job? = null
    private var watchdogJob: Job? = null
    private var deadlineJob: Job? = null
    private var startedOnVariant = false
    private var ladderRounds = 0

    // Estado del vigilante
    private var lastPosition = -1L
    private var stalledMs = 0
    private var bufferingMs = 0
    private var healthyMs = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError(error)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    startedOnVariant = true
                    deadlineJob?.cancel()
                    _stats.value = _stats.value.copy(
                        status = "Reproduciendo",
                        retryRound = 0,
                    )
                }
            }
        })
        startWatchdog()
    }

    private fun build(): ExoPlayer {
        val net = settings.net
        val preset = net.buffer

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(preset.minMs, preset.maxMs, preset.startMs, preset.rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()

        val httpFactory = OkHttpDataSource.Factory(Http.client(net, readTimeoutSeconds = 30))
            .setUserAgent(net.userAgent)
            .setDefaultRequestProperties(mapOf("Connection" to "keep-alive"))

        // FLAG_ALLOW_NON_IDR_KEYFRAMES: los canales en vivo se enganchan a mitad
        // de GOP. Sin esto se ve audio sin imagen hasta el siguiente keyframe.
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
            .setTsExtractorTimestampSearchBytes(600 * 188)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory, extractors)
            .setLoadErrorHandlingPolicy(StubbornPolicy())

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
            )
            .build()
            .also {
                it.setWakeMode(C.WAKE_MODE_NETWORK)
                it.playWhenReady = true
            }
    }

    /**
     * Politica de carga: insistir mucho con los fallos de red y no insistir nada
     * con los que no se arreglan reintentando. Un 403 del panel significa
     * bloqueo o cuenta ocupada; martillarlo solo confirma el bloqueo.
     */
    private class StubbornPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 8

        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val e = info.exception
            if (e is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode == 401 || e.responseCode == 403 || e.responseCode == 404) {
                    return C.TIME_UNSET // no reintentar: no es un problema de red
                }
            }
            return minOf(700L * info.errorCount, 5_000L)
        }
    }

    fun updateSettings(s: AppSettings) {
        settings = s
    }

    fun play(channelName: String, streamId: Int) {
        retryJob?.cancel()
        deadlineJob?.cancel()
        currentStreamId = streamId
        variants = StreamVariants.forChannel(settings.account, streamId, settings.net.variantOrder)
        variantIndex = 0
        attemptsOnVariant = 0
        hardened = false
        ladderRounds = 0
        resetWatchdog()
        _stats.value = PlaybackStats(channelName = channelName, reconnects = 0)
        load("Conectando")
    }

    private fun load(status: String) {
        val v = variants.getOrNull(variantIndex) ?: return
        val targetOffset = when (settings.net.buffer) {
            com.orbita.tv.data.BufferPreset.NORMAL -> 8_000L
            com.orbita.tv.data.BufferPreset.SATELITE -> 20_000L
            com.orbita.tv.data.BufferPreset.EXTREMO -> 35_000L
        }
        val item = MediaItem.Builder()
            .setUri(v.url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(targetOffset)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
            .build()

        _stats.value = _stats.value.copy(
            variantLabel = v.label,
            variantUrl = v.url,
            status = status,
            fatalError = null,
        )
        resetWatchdog()
        startedOnVariant = false
        player.setMediaItem(item)
        player.prepare()
        player.play()

        deadlineJob?.cancel()
        deadlineJob = scope.launch {
            delay(LOAD_DEADLINE_MS)
            if (!startedOnVariant && _stats.value.fatalError == null) {
                advanceVariant("No dio imagen en " + (LOAD_DEADLINE_MS / 1000) + " s")
            }
        }
    }

    private fun onError(error: PlaybackException) {
        val cause = error.cause
        if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            when (cause.responseCode) {
                401, 403 -> {
                    fatal(
                        "El servidor rechazo la conexion (" + cause.responseCode + "). " +
                            "Suele ser la cuenta en uso en otro aparato, o el proveedor " +
                            "bloqueando la IP de Starlink. Revisa Diagnostico."
                    )
                    return
                }
                404 -> {
                    // Esa forma del stream no existe en este panel: saltar ya.
                    advanceVariant("El canal no esta en esta ruta")
                    return
                }
            }
        }
        retry(describe(error))
    }

    private fun describe(error: PlaybackException): String = when {
        error.cause is java.net.UnknownHostException -> "No se resolvio el nombre del servidor"
        error.cause is java.net.SocketTimeoutException -> "El servidor dejo de responder"
        error.cause is java.net.ConnectException -> "No se pudo abrir la conexion"
        error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "Se quedo atras del directo"
        else -> error.errorCodeName
    }

    /** Reconexion forzada por el vigilante: no hubo error, simplemente no llegan bytes. */
    private fun forceReconnect(reason: String) {
        player.stop()
        retry(reason)
    }

    private fun retry(reason: String) {
        retryJob?.cancel()
        attemptsOnVariant++
        // Una forma del stream que ya dio imagen merece mas paciencia: si fallo,
        // fue el momento, no el formato. Una que nunca arranco recibe dos
        // intentos y se cambia, pero DOS, no cero: un hipo de red al conectar no
        // puede descartar un formato que en realidad funciona.
        val maxEnEsteFormato = if (startedOnVariant) 3 else 2
        if (attemptsOnVariant > maxEnEsteFormato) {
            advanceVariant(reason)
            return
        }
        val wait = 600L * attemptsOnVariant
        bump(reason + " · reintento " + attemptsOnVariant)
        retryJob = scope.launch {
            delay(wait)
            load("Reconectando")
        }
    }

    private fun advanceVariant(reason: String) {
        attemptsOnVariant = 0
        variantIndex++
        if (variantIndex < variants.size) {
            bump(reason + " · probando otra forma del stream")
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(400)
                load("Cambiando de formato")
            }
            return
        }
        if (!hardened) {
            // Ultimo recurso: solo IPv4, HTTP/1.1 y TS crudo, bufer maximo.
            hardened = true
            variantIndex = 0
            settings = settings.copy(
                net = settings.net.copy(
                    forceIpv4 = true,
                    forceHttp11 = true,
                    variantOrder = com.orbita.tv.data.VariantOrder.TS_FIRST,
                    buffer = com.orbita.tv.data.BufferPreset.EXTREMO,
                )
            )
            variants = StreamVariants.forChannel(
                settings.account,
                currentStreamId,
                settings.net.variantOrder,
            )
            bump("Modo endurecido: IPv4 y bufer maximo")
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(400)
                load("Modo endurecido")
            }
            return
        }
        // Agotada la escalera NO se abandona. Un enlace satelital puede estar
        // caido treinta segundos y volver, y rendirse para siempre a los diez
        // segundos es justo lo contrario de para que existe esta app. Se sigue
        // insistiendo despacio, con esperas cada vez mas largas para no
        // martillar al panel.
        restartLadder(reason)
    }

    private fun restartLadder(reason: String) {
        retryJob?.cancel()
        deadlineJob?.cancel()
        ladderRounds++
        variantIndex = 0
        attemptsOnVariant = 0
        val wait = minOf(5_000L * ladderRounds, 30_000L)
        _stats.value = _stats.value.copy(
            status = "Sin señal · reintentando en " + (wait / 1000) + " s",
            retryRound = ladderRounds,
        )
        retryJob = scope.launch {
            delay(wait)
            load("Reintentando · ronda " + ladderRounds)
        }
    }

    /** Reintento pedido a mano, tras un rechazo del panel. Empieza de cero. */
    fun retryNow() {
        if (currentStreamId <= 0) return
        play(_stats.value.channelName, currentStreamId)
    }

    private fun bump(status: String) {
        _stats.value = _stats.value.copy(
            status = status,
            reconnects = _stats.value.reconnects + 1,
        )
    }

    private fun fatal(message: String) {
        retryJob?.cancel()
        player.stop()
        _stats.value = _stats.value.copy(status = "Detenido", fatalError = message)
    }

    private fun resetWatchdog() {
        lastPosition = -1L
        stalledMs = 0
        bufferingMs = 0
        healthyMs = 0
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                delay(1_000)
                tick()
            }
        }
    }

    private fun tick() {
        val net = settings.net
        val state = player.playbackState
        val pos = player.currentPosition
        val buffered = (player.totalBufferedDuration / 1000L).toInt()

        _stats.value = _stats.value.copy(
            bufferedSeconds = buffered,
            kbps = bandwidthMeter.bitrateEstimate / 1000,
            healthySeconds = healthyMs / 1000,
        )

        if (_stats.value.fatalError != null) return

        when (state) {
            Player.STATE_BUFFERING -> {
                bufferingMs += 1000
                stalledMs = 0
                healthyMs = 0
            }
            Player.STATE_READY -> {
                bufferingMs = 0
                if (player.isPlaying) {
                    if (pos == lastPosition) {
                        stalledMs += 1000
                        healthyMs = 0
                    } else {
                        stalledMs = 0
                        healthyMs += 1000
                    }
                }
            }
            else -> Unit
        }
        lastPosition = pos

        // Un minuto sano: esta forma del stream sirve. Se olvidan los intentos y
        // tambien las rondas, para que la proxima caida arranque con esperas
        // cortas en vez de heredar los treinta segundos de la ultima racha mala.
        if (healthyMs >= 60_000) {
            attemptsOnVariant = 0
            ladderRounds = 0
        }

        if (stalledMs >= net.stallSeconds * 1000) {
            resetWatchdog()
            forceReconnect("Imagen congelada sin error")
        } else if (bufferingMs >= net.bufferingSeconds * 1000) {
            resetWatchdog()
            forceReconnect("Bufer vacio demasiado tiempo")
        }
    }

    private companion object {
        const val LOAD_DEADLINE_MS = 9_000L
    }

    fun release() {
        retryJob?.cancel()
        deadlineJob?.cancel()
        watchdogJob?.cancel()
        player.release()
    }
}
