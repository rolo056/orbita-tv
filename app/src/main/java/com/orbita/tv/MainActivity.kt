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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.orbita.tv.data.Account
import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.Prefs
import com.orbita.tv.data.Skin
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Diagnostics
import com.orbita.tv.diag.Finding
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.net.SkinSource
import com.orbita.tv.net.UpdateInfo
import com.orbita.tv.net.Updater
import com.orbita.tv.net.Xtream
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.player.ResilientPlayer
import com.orbita.tv.ui.DiagnosticsScreen
import com.orbita.tv.ui.HomeScreen
import com.orbita.tv.ui.LoginScreen
import com.orbita.tv.ui.OrbitaTheme
import com.orbita.tv.ui.SettingsScreen
import com.orbita.tv.ui.Tint
import kotlinx.coroutines.launch

private enum class Screen { LOGIN, HOME, DIAGNOSTICS, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitaTheme {
                Box(Modifier.fillMaxSize().background(Tint.bg)) {
                    // Fondo remoto opcional. Va detras de todo y con opacidad
                    // propia, porque una foto a pantalla completa detras de
                    // texto claro arruina la legibilidad muy rapido.
                    val fondo = Tint.skin.bgImageUrl
                    if (fondo != null) {
                        AsyncImage(
                            model = fondo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(Tint.skin.bgImageAlpha),
                        )
                    }
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
    var skinReload by remember { mutableStateOf(0) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    // La lista completa se pide UNA vez y se filtra aqui. Asi las categorias
    // cambian sin esperar a la red, y los contadores por categoria salen gratis.
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }

    val findings = remember { mutableStateListOf<Finding>() }
    var report by remember { mutableStateOf<DiagReport?>(null) }
    var diagRunning by remember { mutableStateOf(false) }

    // Un solo motor de reproduccion para toda la sesion. Es lo que permite que
    // la vista previa y la pantalla completa sean el mismo stream y una sola
    // conexion contra el panel.
    var engine by remember { mutableStateOf<ResilientPlayer?>(null) }
    var stats by remember { mutableStateOf(PlaybackStats()) }
    LaunchedEffect(engine) { engine?.stats?.collect { stats = it } }

    DisposableEffect(Unit) { onDispose { engine?.release() } }

    // La apariencia. Arranca con lo ultimo que se descargo bien y despues
    // consulta el servidor; en modo diseno sigue consultando cada 3 segundos.
    LaunchedEffect(booted, settings.skinUrl, settings.liveDesign, skinReload) {
        if (!booted) return@LaunchedEffect
        SkinSource.cached(ctx)?.let { Tint.skin = Skin.merge(Skin.DEFAULT, it) }
        Tint.fontFamily = SkinSource.font(ctx, Tint.skin.fontUrl, Tint.skin.fontUrlBold, settings.net)
        while (true) {
            val raw = SkinSource.fetch(ctx, settings.skinUrl, settings.net)
            if (raw != null) {
                val next = Skin.merge(Skin.DEFAULT, raw)
                if (next != Tint.skin) {
                    val fuenteCambio = next.fontUrl != Tint.skin.fontUrl ||
                        next.fontUrlBold != Tint.skin.fontUrlBold
                    Tint.skin = next
                    if (fuenteCambio) {
                        Tint.fontFamily =
                            SkinSource.font(ctx, next.fontUrl, next.fontUrlBold, settings.net)
                    }
                }
            }
            if (!settings.liveDesign) break
            kotlinx.coroutines.delay(3_000)
        }
    }

    LaunchedEffect(booted) {
        if (booted) update = Updater.check(ctx, settings.net)
    }

    suspend fun cargarTodo() {
        loading = true
        error = null
        try {
            val xt = Xtream(settings.account, settings.net)
            categories = runCatching { xt.categories() }.getOrDefault(emptyList())
            allChannels = xt.channels(null)
        } catch (e: Exception) {
            error = Xtream.mensajeAmigable(e)
            allChannels = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        settings = Prefs.load(ctx)
        booted = true
        if (!settings.account.isEmpty) {
            screen = Screen.HOME
            cargarTodo()
        }
    }

    if (!booted) return

    fun reproducir(c: Channel) {
        val e = engine ?: ResilientPlayer(ctx, scope, settings).also { engine = it }
        e.updateSettings(settings)
        e.play(c.name, c.streamId)
        scope.launch { Prefs.save(ctx, settings.copy(lastChannelId = c.streamId)) }
    }

    fun alternarFavorito(c: Channel) {
        val nuevos = settings.favorites.toMutableSet()
        if (!nuevos.add(c.streamId)) nuevos.remove(c.streamId)
        settings = settings.copy(favorites = nuevos)
        scope.launch { Prefs.save(ctx, settings) }
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
                    cargarTodo()
                    if (error == null) screen = Screen.HOME
                }
            },
        )

        Screen.HOME -> HomeScreen(
            categories = categories,
            allChannels = allChannels,
            selectedCategory = selectedCategory,
            favorites = settings.favorites,
            loading = loading,
            error = error,
            engine = engine,
            stats = stats,
            onCategory = { selectedCategory = it },
            onPlay = { reproducir(it) },
            onToggleFavorite = { alternarFavorito(it) },
            onDiagnostics = {
                findings.clear()
                report = null
                screen = Screen.DIAGNOSTICS
            },
            onSettings = { screen = Screen.SETTINGS },
            update = update,
            updating = updating,
            onUpdate = {
                val info = update
                if (info != null && !updating) {
                    updating = true
                    scope.launch {
                        val file = Updater.download(ctx, info, settings.net)
                        updating = false
                        if (file != null) Updater.install(ctx, file)
                    }
                }
            },
        )

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
            onExit = { screen = Screen.HOME },
        )

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = { s ->
                settings = s
                engine?.updateSettings(s)
                scope.launch { Prefs.save(ctx, s) }
            },
            onReloadSkin = { skinReload++ },
            onForget = {
                scope.launch {
                    engine?.release()
                    engine = null
                    Prefs.clear(ctx)
                    settings = AppSettings()
                    categories = emptyList()
                    allChannels = emptyList()
                    screen = Screen.LOGIN
                }
            },
            onExit = { screen = Screen.HOME },
        )
    }
}
