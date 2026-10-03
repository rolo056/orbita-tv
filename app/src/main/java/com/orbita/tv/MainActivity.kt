package com.orbita.tv

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.orbita.tv.data.Account
import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.Biblioteca
import com.orbita.tv.data.Guardado
import com.orbita.tv.data.Prefs
import com.orbita.tv.data.PuntoDeSerie
import com.orbita.tv.data.Skin
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Diagnostics
import com.orbita.tv.diag.Finding
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.net.Episode
import com.orbita.tv.net.Movie
import com.orbita.tv.net.MovieInfo
import com.orbita.tv.net.Series
import com.orbita.tv.net.SeriesDetail
import com.orbita.tv.net.SkinSource
import com.orbita.tv.net.UpdateInfo
import com.orbita.tv.net.Updater
import com.orbita.tv.net.Xtream
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.player.ResilientPlayer
import com.orbita.tv.player.StreamVariants
import com.orbita.tv.ui.CAT_FAVORITOS
import com.orbita.tv.ui.CAT_SEGUIR
import com.orbita.tv.ui.CatalogoScreen
import com.orbita.tv.ui.CineScreen
import com.orbita.tv.ui.DiagnosticsScreen
import com.orbita.tv.ui.Estante
import com.orbita.tv.ui.FichaPeliculaScreen
import com.orbita.tv.ui.FichaSerieScreen
import com.orbita.tv.ui.HomeScreen
import com.orbita.tv.ui.LoginScreen
import com.orbita.tv.ui.OrbitaTheme
import com.orbita.tv.ui.Seccion
import com.orbita.tv.ui.SettingsScreen
import com.orbita.tv.ui.Tarjeta
import com.orbita.tv.ui.Tint
import com.orbita.tv.ui.Fondo
import com.orbita.tv.ui.InicioScreen
import com.orbita.tv.ui.CuentaScreen
import com.orbita.tv.ui.venceLegible
import com.orbita.tv.net.AccountStatus
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class Screen { LOGIN, INICIO, HOME, FICHA_PELICULA, FICHA_SERIE, CINE, CUENTA, DIAGNOSTICS, SETTINGS }

/** Lo que esta puesto en el reproductor de peliculas y episodios. */
private data class Funcion(
    /** Bajo que clave se guarda el avance. */
    val clave: String,
    val titulo: String,
    val subtitulo: String,
    val direccion: String,
    val desdeMs: Long,
    /** Solo en las series: el episodio, para saber cual viene despues. */
    val episodio: Episode? = null,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cuentaInicial = cuentaDePrueba()
        val seccionInicial = seccionDePrueba()
        val aparienciaInicial = aparienciaDePrueba()
        setContent {
            OrbitaTheme {
                Box(Modifier.fillMaxSize()) {
                    // El fondo con los resplandores, detras de todo el cristal.
                    Fondo()
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
                    App(cuentaInicial, seccionInicial, aparienciaInicial)
                }
            }
        }
    }

    /**
     * Solo en la variante de prueba, la que no se publica: deja abrir la app ya
     * conectada a una cuenta, pasandosela al lanzarla. Existe para la prueba
     * automatica en emulador, donde no hay nadie que escriba en la pantalla de
     * conexion. En la app que se instala en el televisor esto no hace nada.
     */
    private fun cuentaDePrueba(): Account? {
        if (!esVarianteDePrueba()) return null
        val direccion = intent?.getStringExtra("cuenta") ?: return null
        return Xtream.parsePasted(direccion)?.takeIf { !it.isEmpty }
    }

    /** Igual que la cuenta: solo en la variante de prueba, arrancar en una seccion. */
    private fun seccionDePrueba(): Seccion? {
        if (!esVarianteDePrueba()) return null
        return when (intent?.getStringExtra("seccion")) {
            "peliculas" -> Seccion.PELICULAS
            "series" -> Seccion.SERIES
            else -> null
        }
    }

    /** Y de donde leer la apariencia, para que la prueba se vea como en casa. */
    private fun aparienciaDePrueba(): String? {
        if (!esVarianteDePrueba()) return null
        return intent?.getStringExtra("apariencia")?.takeIf { it.startsWith("http") }
    }

    private fun esVarianteDePrueba(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

@Composable
private fun App(cuentaDePrueba: Account?, seccionDePrueba: Seccion?, aparienciaDePrueba: String?) {
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
    var aviso by remember { mutableStateOf<String?>(null) }

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }

    val findings = remember { mutableStateListOf<Finding>() }
    var report by remember { mutableStateOf<DiagReport?>(null) }
    var diagRunning by remember { mutableStateOf(false) }
    var volverDeDiag by remember { mutableStateOf(Screen.HOME) }

    // ---- peliculas y series ----
    var seccion by remember { mutableStateOf(Seccion.EN_VIVO) }
    val estantePeliculas = remember { Estante<Movie>() }
    val estanteSeries = remember { Estante<Series>() }
    var biblioteca by remember { mutableStateOf(Biblioteca()) }
    // El estado de las grillas vive aca arriba para que, al volver de una
    // ficha, el catalogo este donde se lo dejo.
    val grillaPeliculas = rememberLazyGridState()
    val grillaSeries = rememberLazyGridState()
    var enfocarPelicula by remember { mutableStateOf<Int?>(null) }
    var enfocarSerie by remember { mutableStateOf<Int?>(null) }

    var pelicula by remember { mutableStateOf<Movie?>(null) }
    var infoPelicula by remember { mutableStateOf<MovieInfo?>(null) }
    var cargandoInfo by remember { mutableStateOf(false) }

    var serie by remember { mutableStateOf<Series?>(null) }
    var detalleSerie by remember { mutableStateOf<SeriesDetail?>(null) }
    var cargandoSerie by remember { mutableStateOf(false) }
    var errorSerie by remember { mutableStateOf<String?>(null) }

    var funcion by remember { mutableStateOf<Funcion?>(null) }
    var volverDeCine by remember { mutableStateOf(Screen.HOME) }
    var volverDeAjustes by remember { mutableStateOf(Screen.INICIO) }

    // Lo que el panel dice de la cuenta: vencimiento y conexiones. Se muestra en
    // la pantalla de inicio y en Mi cuenta.
    var estadoCuenta by remember { mutableStateOf<AccountStatus?>(null) }
    var consultandoCuenta by remember { mutableStateOf(false) }
    var errorCuenta by remember { mutableStateOf<String?>(null) }

    // Un solo motor de reproduccion para toda la sesion. Es lo que permite que
    // la vista previa y la pantalla completa sean el mismo stream y una sola
    // conexion contra el panel.
    var engine by remember { mutableStateOf<ResilientPlayer?>(null) }
    var stats by remember { mutableStateOf(PlaybackStats()) }
    LaunchedEffect(engine) { engine?.stats?.collect { stats = it } }

    DisposableEffect(Unit) { onDispose { engine?.release() } }

    // Un reproductor reintentando en segundo plano, invisible, es como se
    // acumulan miles de conexiones contra el panel sin que nadie se entere, y es
    // asi como un servidor de IPTV termina bloqueando la IP. Fuera de pantalla
    // no se intenta nada.
    val duenoCicloVida = LocalLifecycleOwner.current
    DisposableEffect(duenoCicloVida, engine) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> engine?.onBackground()
                Lifecycle.Event.ON_START -> engine?.onForeground()
                else -> Unit
            }
        }
        duenoCicloVida.lifecycle.addObserver(observador)
        onDispose { duenoCicloVida.lifecycle.removeObserver(observador) }
    }

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

    fun guardarBiblioteca() {
        val b = biblioteca
        scope.launch { Prefs.saveLibrary(ctx, b) }
    }

    /**
     * Las categorias de peliculas y de series. Van aparte y despues de los
     * canales: son dos consultas chicas, y si el proveedor no tiene alguna de
     * las dos, esa seccion simplemente no aparece.
     */
    suspend fun cargarCatalogos() {
        val xt = Xtream(settings.account, settings.net)
        estantePeliculas.categorias =
            runCatching { xt.vodCategories() }.getOrDefault(emptyList())
        estanteSeries.categorias =
            runCatching { xt.seriesCategories() }.getOrDefault(emptyList())
    }

    suspend fun cargarTodo() {
        loading = true
        error = null
        try {
            val xt = Xtream(settings.account, settings.net)
            // Primero la cuenta, SIN tragarse el error. Es una respuesta chica y
            // es la unica que dice la verdad sobre si el servidor se alcanza.
            // Antes este paso no existia, las consultas siguientes se tragaban
            // sus errores, y un servidor totalmente inalcanzable terminaba en
            // "el panel autentica pero no devolvio canales": falso en las dos
            // mitades, y encima escondia el motivo real.
            val estado = xt.status()
            estadoCuenta = estado
            if (!estado.authOk) {
                error = "El panel rechazo la cuenta. Revisa usuario y contrasena, " +
                    "o si la cuenta vencio."
                allChannels = emptyList()
                return
            }
            // Una cuenta vencida autentica bien y despues no entrega nada.
            val motivo = Xtream.cuentaInservible(estado)
            if (motivo != null) {
                error = motivo
                categories = emptyList()
                allChannels = emptyList()
                return
            }
            scope.launch { cargarCatalogos() }
            categories = runCatching { xt.categories() }.getOrDefault(emptyList())
            allChannels = xt.allChannels(categories)
            if (allChannels.isEmpty()) {
                // Aqui SI es cierto: la cuenta respondio y la lista no llego.
                error = "El panel acepto la cuenta pero la lista de canales no llego. " +
                    "Suele ser la red: prueba de nuevo o abre Diagnostico de red."
            }
        } catch (e: Exception) {
            // Antes de rendirse: puede que el panel conteste por otro puerto.
            // Ver Xtream.puertoQueResponde, que existe por un caso real.
            val otro = if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) {
                Xtream.puertoQueResponde(settings.account, settings.net)
            } else null
            if (otro != null) {
                settings = settings.copy(account = settings.account.copy(port = otro))
                Prefs.save(ctx, settings)
                val xt2 = Xtream(settings.account, settings.net)
                categories = runCatching { xt2.categories() }.getOrDefault(emptyList())
                allChannels = runCatching { xt2.channels(null) }.getOrDefault(emptyList())
                error = if (allChannels.isEmpty()) Xtream.mensajeAmigable(e) else null
                aviso = "El puerto anterior no responde desde esta red. " +
                    "Se cambio al " + otro + ", que si contesta."
                scope.launch { cargarCatalogos() }
            } else {
                error = Xtream.mensajeAmigable(e)
                allChannels = emptyList()
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        settings = Prefs.load(ctx)
        if (cuentaDePrueba != null && cuentaDePrueba != settings.account) {
            settings = settings.copy(account = cuentaDePrueba)
            Prefs.save(ctx, settings)
        }
        if (aparienciaDePrueba != null && aparienciaDePrueba != settings.skinUrl) {
            settings = settings.copy(skinUrl = aparienciaDePrueba)
            Prefs.save(ctx, settings)
        }
        // Lo guardado de peliculas y series es de una cuenta. Con otra, los
        // mismos numeros son otros titulos: se empieza de cero.
        val guardada = Prefs.loadLibrary(ctx)
        val firma = Biblioteca.firma(settings.account)
        biblioteca = if (guardada.cuenta == firma) guardada else Biblioteca(cuenta = firma)
        booted = true
        if (!settings.account.isEmpty) {
            screen = Screen.INICIO
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

    fun abrirDiagnostico(volver: Screen) {
        findings.clear()
        report = null
        volverDeDiag = volver
        screen = Screen.DIAGNOSTICS
    }

    // ------------------------------------------------ peliculas y series

    // Un solo cliente del panel para todo el catalogo. Uno nuevo por consulta
    // deja detras conexiones abiertas que nadie vuelve a usar.
    val panel = remember(settings.account, settings.net) {
        Xtream(settings.account, settings.net)
    }

    fun hayPeliculasPorSeguir(): Boolean = biblioteca.recientes.any {
        !it.serie && biblioteca.avanceDePelicula(it.id)?.aMedias == true
    }

    fun abrirCategoriaDePeliculas(id: String) {
        scope.launch {
            if (id == CAT_SEGUIR || id == CAT_FAVORITOS) {
                estantePeliculas.abrirLocal(id)
            } else {
                estantePeliculas.abrir(id) { panel.movies(it) }
            }
        }
        enfocarPelicula = null
        scope.launch { grillaPeliculas.scrollToItem(0) }
    }

    fun abrirCategoriaDeSeries(id: String) {
        scope.launch {
            if (id == CAT_SEGUIR || id == CAT_FAVORITOS) {
                estanteSeries.abrirLocal(id)
            } else {
                estanteSeries.abrir(id) { panel.series(it) }
            }
        }
        enfocarSerie = null
        scope.launch { grillaSeries.scrollToItem(0) }
    }

    fun irASeccion(s: Seccion) {
        if (s == seccion) return
        seccion = s
        // La primera vez que se entra, se abre algo: lo que quedo a medias si
        // hay, y si no la primera categoria del proveedor.
        when (s) {
            Seccion.PELICULAS -> if (estantePeliculas.abierta == null) {
                val primera = if (hayPeliculasPorSeguir()) CAT_SEGUIR
                else estantePeliculas.categorias.firstOrNull()?.id
                if (primera != null) abrirCategoriaDePeliculas(primera)
            }
            Seccion.SERIES -> if (estanteSeries.abierta == null) {
                val primera = if (biblioteca.recientes.any { it.serie }) CAT_SEGUIR
                else estanteSeries.categorias.firstOrNull()?.id
                if (primera != null) abrirCategoriaDeSeries(primera)
            }
            Seccion.EN_VIVO -> Unit
        }
    }

    fun abrirPelicula(t: Tarjeta) {
        val m = estantePeliculas.titulos.firstOrNull { it.streamId == t.id }
            ?: (biblioteca.recientes + biblioteca.favoritas)
                .firstOrNull { !it.serie && it.id == t.id }
                ?.let { Movie(it.id, it.nombre, it.imagen, null, it.extension, null, null) }
            ?: return
        pelicula = m
        infoPelicula = null
        cargandoInfo = true
        enfocarPelicula = m.streamId
        screen = Screen.FICHA_PELICULA
        scope.launch {
            val info = runCatching { panel.movieInfo(m.streamId) }.getOrNull()
            if (pelicula?.streamId == m.streamId) {
                infoPelicula = info
                cargandoInfo = false
            }
        }
    }

    fun verPelicula(m: Movie, desdeMs: Long) {
        // La ficha sabe mejor que la lista con que extension esta el archivo.
        val extension = infoPelicula?.extension ?: m.extension
        engine?.release()
        engine = null
        funcion = Funcion(
            clave = Biblioteca.clavePelicula(m.streamId),
            titulo = m.name,
            subtitulo = m.year ?: "",
            direccion = StreamVariants.forMovie(settings.account, m.streamId, extension),
            desdeMs = desdeMs,
        )
        biblioteca = biblioteca.conReciente(
            Guardado(false, m.streamId, m.name, m.poster ?: infoPelicula?.poster, extension)
        )
        guardarBiblioteca()
        volverDeCine = Screen.FICHA_PELICULA
        screen = Screen.CINE
    }

    fun cargarSerie(id: Int) {
        cargandoSerie = true
        errorSerie = null
        scope.launch {
            try {
                val d = panel.seriesDetail(id)
                if (serie?.seriesId == id) detalleSerie = d
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serie?.seriesId == id) errorSerie = Xtream.mensajeAmigable(e)
            } finally {
                if (serie?.seriesId == id) cargandoSerie = false
            }
        }
    }

    fun abrirSerie(t: Tarjeta) {
        val s = estanteSeries.titulos.firstOrNull { it.seriesId == t.id }
            ?: (biblioteca.recientes + biblioteca.favoritas)
                .firstOrNull { it.serie && it.id == t.id }
                ?.let { Series(it.id, it.nombre, it.imagen, null, null, null, null, null, null, null) }
            ?: return
        serie = s
        detalleSerie = null
        enfocarSerie = s.seriesId
        screen = Screen.FICHA_SERIE
        cargarSerie(s.seriesId)
    }

    fun episodioSiguiente(e: Episode): Episode? {
        val todos = detalleSerie?.seasons?.flatMap { it.episodes } ?: return null
        val i = todos.indexOfFirst { it.id == e.id }
        return if (i >= 0) todos.getOrNull(i + 1) else null
    }

    fun verEpisodio(s: Series, e: Episode, desdeMs: Long) {
        engine?.release()
        engine = null
        funcion = Funcion(
            clave = Biblioteca.claveEpisodio(e.id),
            titulo = s.name,
            // Sin titulo propio el episodio se llama "Episodio 3": repetirlo sobra.
            subtitulo = "Temporada " + e.season + "  ·  Episodio " + e.number +
                (if (e.title.equals("Episodio " + e.number, ignoreCase = true)) "" else "  ·  " + e.title),
            direccion = StreamVariants.forEpisode(settings.account, e.id, e.extension),
            desdeMs = desdeMs,
            episodio = e,
        )
        biblioteca = biblioteca
            .conReciente(Guardado(true, s.seriesId, s.name, s.cover ?: detalleSerie?.series?.cover))
            .conPunto(s.seriesId, PuntoDeSerie(e.id, e.season, e.number))
        guardarBiblioteca()
        volverDeCine = Screen.FICHA_SERIE
        screen = Screen.CINE
    }

    val secciones = remember(estantePeliculas.categorias, estanteSeries.categorias) {
        buildList {
            add(Seccion.EN_VIVO)
            if (estantePeliculas.categorias.isNotEmpty()) add(Seccion.PELICULAS)
            if (estanteSeries.categorias.isNotEmpty()) add(Seccion.SERIES)
        }
    }

    var seccionPendiente by remember { mutableStateOf(seccionDePrueba) }
    LaunchedEffect(secciones, seccionPendiente) {
        val s = seccionPendiente ?: return@LaunchedEffect
        if (s in secciones) {
            seccionPendiente = null
            irASeccion(s)
            screen = Screen.HOME
        }
    }

    val tarjetasDePeliculas = remember(estantePeliculas.titulos, estantePeliculas.abierta, biblioteca) {
        fun deGuardado(g: Guardado): Tarjeta {
            val av = biblioteca.avanceDePelicula(g.id)
            return Tarjeta(
                id = g.id,
                titulo = g.nombre,
                imagen = g.imagen,
                avance = av?.takeIf { it.aMedias }?.fraccion,
                favorita = biblioteca.esFavorita(false, g.id),
            )
        }
        when (estantePeliculas.abierta) {
            CAT_SEGUIR -> biblioteca.recientes
                .filter { !it.serie && biblioteca.avanceDePelicula(it.id)?.aMedias == true }
                .map { deGuardado(it) }
            CAT_FAVORITOS -> biblioteca.favoritas.filter { !it.serie }.map { deGuardado(it) }
            else -> estantePeliculas.titulos.map { m ->
                Tarjeta(
                    id = m.streamId,
                    titulo = m.name,
                    imagen = m.poster,
                    detalle = listOfNotNull(m.year, m.rating?.let { "★ " + unDecimal(it) })
                        .joinToString("  ·  "),
                    avance = biblioteca.avanceDePelicula(m.streamId)?.takeIf { it.aMedias }?.fraccion,
                    favorita = biblioteca.esFavorita(false, m.streamId),
                )
            }
        }
    }

    val tarjetasDeSeries = remember(estanteSeries.titulos, estanteSeries.abierta, biblioteca) {
        fun deGuardado(g: Guardado) = Tarjeta(
            id = g.id,
            titulo = g.nombre,
            imagen = g.imagen,
            favorita = biblioteca.esFavorita(true, g.id),
        )
        when (estanteSeries.abierta) {
            CAT_SEGUIR -> biblioteca.recientes.filter { it.serie }.map { deGuardado(it) }
            CAT_FAVORITOS -> biblioteca.favoritas.filter { it.serie }.map { deGuardado(it) }
            else -> estanteSeries.titulos.map { s ->
                Tarjeta(
                    id = s.seriesId,
                    titulo = s.name,
                    imagen = s.cover,
                    detalle = listOfNotNull(
                        s.released?.let { anioDe(it) },
                        s.rating?.let { "★ " + unDecimal(it) },
                    ).joinToString("  ·  "),
                    favorita = biblioteca.esFavorita(true, s.seriesId),
                )
            }
        }
    }

    fun actualizar() {
        val info = update
        if (info != null && !updating) {
            updating = true
            scope.launch {
                val file = Updater.download(ctx, info, settings.net)
                updating = false
                if (file != null) Updater.install(ctx, file)
            }
        }
    }

    fun consultarCuenta() {
        if (consultandoCuenta) return
        consultandoCuenta = true
        errorCuenta = null
        scope.launch {
            try {
                estadoCuenta = panel.status()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCuenta = Xtream.mensajeAmigable(e)
            } finally {
                consultandoCuenta = false
            }
        }
    }

    fun hayPeliculas(): Boolean = estantePeliculas.categorias.isNotEmpty() ||
        hayPeliculasPorSeguir() || biblioteca.favoritas.any { !it.serie }

    fun haySeries(): Boolean = estanteSeries.categorias.isNotEmpty() ||
        biblioteca.recientes.any { it.serie } || biblioteca.favoritas.any { it.serie }

    // Atras desde canales, peliculas o series vuelve al inicio, no cierra la app.
    BackHandler(enabled = screen == Screen.HOME) {
        screen = Screen.INICIO
    }

    when (screen) {
        Screen.LOGIN -> LoginScreen(
            initial = settings.account,
            error = error,
            busy = loading,
            onSave = { account: Account ->
                scope.launch {
                    val otraCuenta = account.host != settings.account.host ||
                        account.username != settings.account.username
                    settings = if (otraCuenta) {
                        // Los favoritos son numeros de canal de la cuenta
                        // anterior: en otro proveedor apuntan a otros canales.
                        settings.copy(account = account, favorites = emptySet(), lastChannelId = -1)
                    } else {
                        settings.copy(account = account)
                    }
                    Prefs.save(ctx, settings)
                    if (otraCuenta) {
                        biblioteca = Biblioteca(cuenta = Biblioteca.firma(account))
                        Prefs.saveLibrary(ctx, biblioteca)
                    }
                    categories = emptyList()
                    selectedCategory = null
                    seccion = Seccion.EN_VIVO
                    estantePeliculas.vaciar()
                    estanteSeries.vaciar()
                    cargarTodo()
                    if (error == null) screen = Screen.INICIO
                }
            },
            onDiagnostics = { abrirDiagnostico(Screen.LOGIN) },
            onSalir = if (settings.account.isEmpty) null else ({ screen = Screen.CUENTA }),
        )

        Screen.INICIO -> InicioScreen(
            usuario = settings.account.username,
            vence = estadoCuenta?.let { venceLegible(it.expires) },
            conexiones = estadoCuenta?.let { it.activeConnections.toString() + " de " + it.maxConnections },
            subVivo = when {
                allChannels.isNotEmpty() -> cantidad(allChannels.size, "canal", "canales")
                loading -> "Cargando…"
                error != null -> "Sin conexión"
                else -> null
            },
            subPeliculas = when {
                estantePeliculas.categorias.isNotEmpty() ->
                    cantidad(estantePeliculas.categorias.size, "categoría", "categorías")
                loading -> "Cargando…"
                hayPeliculas() -> null
                else -> "No disponible"
            },
            subSeries = when {
                estanteSeries.categorias.isNotEmpty() ->
                    cantidad(estanteSeries.categorias.size, "categoría", "categorías")
                loading -> "Cargando…"
                haySeries() -> null
                else -> "No disponible"
            },
            update = update,
            updating = updating,
            onUpdate = { actualizar() },
            onVivo = {
                seccion = Seccion.EN_VIVO
                screen = Screen.HOME
            },
            onPeliculas = {
                if (hayPeliculas()) {
                    irASeccion(Seccion.PELICULAS)
                    screen = Screen.HOME
                }
            },
            onSeries = {
                if (haySeries()) {
                    irASeccion(Seccion.SERIES)
                    screen = Screen.HOME
                }
            },
            onCuenta = {
                consultarCuenta()
                screen = Screen.CUENTA
            },
            onDiagnostico = { abrirDiagnostico(Screen.INICIO) },
            onAjustes = {
                volverDeAjustes = Screen.INICIO
                screen = Screen.SETTINGS
            },
        )

        Screen.CUENTA -> CuentaScreen(
            usuario = settings.account.username,
            servidor = settings.account.host + ":" + settings.account.port,
            estado = estadoCuenta,
            cargando = consultandoCuenta,
            error = errorCuenta,
            onActualizar = { consultarCuenta() },
            onCambiarCuenta = { screen = Screen.LOGIN },
            onSalir = { screen = Screen.INICIO },
        )

        Screen.HOME -> when (seccion) {
            Seccion.EN_VIVO -> HomeScreen(
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
                onStop = { engine?.stop() },
                onToggleFavorite = { alternarFavorito(it) },
                onDiagnostics = { abrirDiagnostico(Screen.HOME) },
                onSettings = {
                    volverDeAjustes = Screen.HOME
                    screen = Screen.SETTINGS
                },
                notice = aviso,
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
                seccion = seccion,
                secciones = listOf(seccion),
                onSeccion = {},
            )

            Seccion.PELICULAS -> CatalogoScreen(
                seccion = seccion,
                secciones = listOf(seccion),
                onSeccion = {},
                categorias = estantePeliculas.categorias,
                abierta = estantePeliculas.abierta,
                haySeguir = hayPeliculasPorSeguir(),
                hayFavoritas = biblioteca.favoritas.any { !it.serie },
                tarjetas = tarjetasDePeliculas,
                cargando = estantePeliculas.cargando,
                error = estantePeliculas.error,
                estadoGrilla = grillaPeliculas,
                enfocar = enfocarPelicula,
                onCategoria = { abrirCategoriaDePeliculas(it) },
                onAbrir = { abrirPelicula(it) },
                onFavorita = { t ->
                    val ext = estantePeliculas.titulos.firstOrNull { it.streamId == t.id }?.extension
                        ?: biblioteca.favoritas.firstOrNull { !it.serie && it.id == t.id }?.extension
                        ?: "mp4"
                    biblioteca = biblioteca.alternarFavorita(Guardado(false, t.id, t.titulo, t.imagen, ext))
                    guardarBiblioteca()
                },
                onDiagnostics = { abrirDiagnostico(Screen.HOME) },
                onSettings = {
                    volverDeAjustes = Screen.HOME
                    screen = Screen.SETTINGS
                },
            )

            Seccion.SERIES -> CatalogoScreen(
                seccion = seccion,
                secciones = listOf(seccion),
                onSeccion = {},
                categorias = estanteSeries.categorias,
                abierta = estanteSeries.abierta,
                haySeguir = biblioteca.recientes.any { it.serie },
                hayFavoritas = biblioteca.favoritas.any { it.serie },
                tarjetas = tarjetasDeSeries,
                cargando = estanteSeries.cargando,
                error = estanteSeries.error,
                estadoGrilla = grillaSeries,
                enfocar = enfocarSerie,
                onCategoria = { abrirCategoriaDeSeries(it) },
                onAbrir = { abrirSerie(it) },
                onFavorita = { t ->
                    biblioteca = biblioteca.alternarFavorita(Guardado(true, t.id, t.titulo, t.imagen))
                    guardarBiblioteca()
                },
                onDiagnostics = { abrirDiagnostico(Screen.HOME) },
                onSettings = {
                    volverDeAjustes = Screen.HOME
                    screen = Screen.SETTINGS
                },
            )
        }

        Screen.FICHA_PELICULA -> {
            val m = pelicula
            if (m == null) {
                LaunchedEffect(Unit) { screen = Screen.HOME }
            } else {
                FichaPeliculaScreen(
                    pelicula = m,
                    info = infoPelicula,
                    cargandoInfo = cargandoInfo,
                    avance = biblioteca.avanceDePelicula(m.streamId),
                    favorita = biblioteca.esFavorita(false, m.streamId),
                    onReproducir = { desde -> verPelicula(m, desde) },
                    onFavorita = {
                        biblioteca = biblioteca.alternarFavorita(
                            Guardado(false, m.streamId, m.name, m.poster ?: infoPelicula?.poster, m.extension)
                        )
                        guardarBiblioteca()
                    },
                    onSalir = { screen = Screen.HOME },
                )
            }
        }

        Screen.FICHA_SERIE -> {
            val s = serie
            if (s == null) {
                LaunchedEffect(Unit) { screen = Screen.HOME }
            } else {
                FichaSerieScreen(
                    serie = s,
                    detalle = detalleSerie,
                    cargando = cargandoSerie,
                    error = errorSerie,
                    biblioteca = biblioteca,
                    favorita = biblioteca.esFavorita(true, s.seriesId),
                    onEpisodio = { e, desde -> verEpisodio(s, e, desde) },
                    onFavorita = {
                        biblioteca = biblioteca.alternarFavorita(
                            Guardado(true, s.seriesId, s.name, s.cover ?: detalleSerie?.series?.cover)
                        )
                        guardarBiblioteca()
                    },
                    onReintentar = { cargarSerie(s.seriesId) },
                    onSalir = { screen = Screen.HOME },
                )
            }
        }

        Screen.CINE -> {
            val f = funcion
            if (f == null) {
                LaunchedEffect(Unit) { screen = volverDeCine }
            } else {
                val proximo = f.episodio?.let { episodioSiguiente(it) }
                CineScreen(
                    titulo = f.titulo,
                    subtitulo = f.subtitulo,
                    direccion = f.direccion,
                    desdeMs = f.desdeMs,
                    haySiguiente = proximo != null,
                    settings = settings,
                    onAvance = { pos, dur ->
                        biblioteca = biblioteca.conAvance(f.clave, pos, dur, System.currentTimeMillis())
                        guardarBiblioteca()
                    },
                    onSiguiente = {
                        val s = serie
                        if (proximo != null && s != null) {
                            // El que termino queda como visto entero, aunque el
                            // ultimo guardado haya sido unos segundos antes.
                            val total = biblioteca.avances[f.clave]?.durMs ?: 0L
                            if (total > 0) {
                                biblioteca = biblioteca.conAvance(
                                    f.clave, total, total, System.currentTimeMillis(),
                                )
                            }
                            verEpisodio(s, proximo, 0L)
                        } else {
                            screen = volverDeCine
                        }
                    },
                    onSalir = { screen = volverDeCine },
                )
            }
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
            onExit = { screen = volverDeDiag },
        )

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = { s ->
                // El bufer y el cliente de red se fijan al crear el reproductor.
                // Si cambia algo de eso, se descarta: el proximo canal arma uno
                // nuevo con los ajustes al dia. Antes el cambio de bufer no
                // tenia efecto hasta cerrar y volver a abrir la app.
                if (s.net != settings.net || s.account != settings.account) {
                    engine?.release()
                    engine = null
                }
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
                    selectedCategory = null
                    seccion = Seccion.EN_VIVO
                    estantePeliculas.vaciar()
                    estanteSeries.vaciar()
                    screen = Screen.LOGIN
                }
            },
            onExit = { screen = volverDeAjustes },
        )
    }
}

/** "1.234 canales", "1 canal". */
private fun cantidad(n: Int, singular: String, plural: String): String =
    String.format(Locale("es"), "%,d", n) + " " + (if (n == 1) singular else plural)

private fun anioDe(fecha: String): String? = Regex("""(19|20)\d{2}""").find(fecha)?.value

private fun unDecimal(x: Float): String {
    val r = Math.round(x * 10f) / 10f
    return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString().replace('.', ',')
}
