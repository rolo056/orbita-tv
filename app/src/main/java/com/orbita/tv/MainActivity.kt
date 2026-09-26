package com.orbita.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.orbita.tv.data.Account
import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.Prefs
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Diagnostics
import com.orbita.tv.diag.Finding
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.net.Xtream
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.player.ResilientPlayer
import com.orbita.tv.ui.ChannelsScreen
import com.orbita.tv.ui.DiagnosticsScreen
import com.orbita.tv.ui.LoginScreen
import com.orbita.tv.ui.OrbitaTheme
import com.orbita.tv.ui.PlayerScreen
import com.orbita.tv.ui.SettingsScreen
import com.orbita.tv.ui.Tint
import kotlinx.coroutines.launch

private enum class Screen { LOGIN, CHANNELS, PLAYER, DIAGNOSTICS, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitaTheme {
                Box(Modifier.fillMaxSize().background(Tint.bg)) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(AppSettings()) }
    var screen by remember { mutableStateOf(Screen.LOGIN) }
    var booted by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playingIndex by remember { mutableStateOf(0) }

    val findings = remember { mutableStateListOf<Finding>() }
    var report by remember { mutableStateOf<DiagReport?>(null) }
    var diagRunning by remember { mutableStateOf(false) }

    // Un solo motor de reproduccion para toda la sesion: cambiar de canal no
    // recrea el reproductor, solo le pasa otra URL. Asi el zapping es inmediato.
    var engine by remember { mutableStateOf<ResilientPlayer?>(null) }
    var stats by remember { mutableStateOf(PlaybackStats()) }
    LaunchedEffect(engine) {
        engine?.stats?.collect { stats = it }
    }

    DisposableEffect(Unit) {
        onDispose { engine?.release() }
    }

    suspend fun loadChannels(categoryId: String?) {
        loading = true
        error = null
        try {
            val xt = Xtream(settings.account, settings.net)
            if (categories.isEmpty()) {
                categories = runCatching { xt.categories() }.getOrDefault(emptyList())
            }
            channels = xt.channels(categoryId)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo leer la lista"
            channels = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        settings = Prefs.load(ctx)
        booted = true
        if (!settings.account.isEmpty) {
            screen = Screen.CHANNELS
            loadChannels(null)
        }
    }

    if (!booted) return

    fun startPlayback(index: Int) {
        val list = channels
        if (list.isEmpty()) return
        val i = ((index % list.size) + list.size) % list.size
        playingIndex = i
        val ch = list[i]
        val e = engine ?: ResilientPlayer(ctx, scope, settings).also { engine = it }
        e.updateSettings(settings)
        e.play(ch.name, ch.streamId)
        scope.launch { Prefs.save(ctx, settings.copy(lastChannelId = ch.streamId)) }
    }

    when (screen) {
        Screen.LOGIN -> LoginScreen(
            initial = settings.account,
            error = error,
            busy = loading,
            onSave = { account: Account ->
                scope.launch {
                    settings = settings.copy(account = account)
                    Prefs.save(ctx, settings)
                    categories = emptyList()
                    loadChannels(null)
                    if (error == null) screen = Screen.CHANNELS
                }
            },
        )

        Screen.CHANNELS -> ChannelsScreen(
            categories = categories,
            channels = channels,
            selectedCategory = selectedCategory,
            loading = loading,
            error = error,
            onCategory = { id ->
                selectedCategory = id
                scope.launch { loadChannels(id) }
            },
            onChannel = { ch ->
                startPlayback(channels.indexOf(ch))
                screen = Screen.PLAYER
            },
            onDiagnostics = {
                findings.clear()
                report = null
                screen = Screen.DIAGNOSTICS
            },
            onSettings = { screen = Screen.SETTINGS },
        )

        Screen.PLAYER -> {
            val e = engine
            if (e == null) {
                screen = Screen.CHANNELS
            } else PlayerScreen(
                engine = e,
                stats = stats,
                onPrevChannel = { startPlayback(playingIndex - 1) },
                onNextChannel = { startPlayback(playingIndex + 1) },
                onExit = {
                    e.player.pause()
                    screen = Screen.CHANNELS
                },
            )
        }

        Screen.DIAGNOSTICS -> DiagnosticsScreen(
            running = diagRunning,
            findings = findings,
            report = report,
            onRun = {
                if (!diagRunning) {
                    findings.clear()
                    report = null
                    diagRunning = true
                    scope.launch {
                        report = Diagnostics.run(settings) { f -> findings.add(f) }
                        diagRunning = false
                    }
                }
            },
            onExit = { screen = Screen.CHANNELS },
        )

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = { s ->
                settings = s
                engine?.updateSettings(s)
                scope.launch { Prefs.save(ctx, s) }
            },
            onForget = {
                scope.launch {
                    engine?.release()
                    engine = null
                    Prefs.clear(ctx)
                    settings = AppSettings()
                    categories = emptyList()
                    channels = emptyList()
                    screen = Screen.LOGIN
                }
            },
            onExit = { screen = Screen.CHANNELS },
        )
    }
}
