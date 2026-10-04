package com.orbita.tv

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbita.tv.data.Account
import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.Avance
import com.orbita.tv.data.Biblioteca
import com.orbita.tv.data.PuntoDeSerie
import com.orbita.tv.data.Skin
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Falla
import com.orbita.tv.diag.Finding
import com.orbita.tv.diag.Level
import com.orbita.tv.net.Category
import com.orbita.tv.net.AccountStatus
import com.orbita.tv.net.Channel
import com.orbita.tv.net.UpdateInfo
import com.orbita.tv.net.Episode
import com.orbita.tv.net.Movie
import com.orbita.tv.net.MovieInfo
import com.orbita.tv.net.Season
import com.orbita.tv.net.Series
import com.orbita.tv.net.SeriesDetail
import com.orbita.tv.player.CineStats
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.ui.CAT_SEGUIR
import com.orbita.tv.ui.CatalogoScreen
import com.orbita.tv.ui.CineCapas
import com.orbita.tv.ui.CuentaScreen
import com.orbita.tv.ui.Fondo
import com.orbita.tv.ui.InicioScreen
import com.orbita.tv.ui.DiagnosticsScreen
import com.orbita.tv.ui.FichaPeliculaScreen
import com.orbita.tv.ui.FichaSerieScreen
import com.orbita.tv.ui.HomeScreen
import com.orbita.tv.ui.LoginScreen
import com.orbita.tv.ui.OrbitaTheme
import com.orbita.tv.ui.PlayerHud
import com.orbita.tv.ui.Seccion
import com.orbita.tv.ui.SettingsScreen
import com.orbita.tv.ui.Tarjeta
import com.orbita.tv.ui.Tint
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Un televisor 1080p: 960 x 540 dp a densidad 2, que es lo que reporta Android TV. */
private const val TELEVISOR = "w960dp-h540dp-land-television-xhdpi"
private const val TELEFONO_VERTICAL = "w360dp-h740dp-port-xxhdpi"
private const val TELEFONO_HORIZONTAL = "w740dp-h360dp-land-xxhdpi"

/**
 * No son pruebas en el sentido habitual: dibujan cada pantalla a un PNG.
 *
 * Existen por una razon concreta. Este proyecto no tiene donde ejecutarse antes
 * de llegar al televisor del usuario, y los fallos de pantalla solo aparecian en
 * sus fotos: el reloj partido letra por letra, el pie de teclas comiendose el
 * alto de la lista, los canales dibujados en un hueco invisible. Cada uno costo
 * una instalacion y una tarde. Con esto se ven antes.
 *
 * Los temas "dificiles" estan a proposito: son las combinaciones de ancho,
 * tipografia y escala que rompieron la pantalla de verdad.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TELEVISOR)
class CapturasTest {

    private val temaPublicado: String = File("../tema.json").readText()

    private val categorias = listOf(
        Category("1", "CANALES NACIONALES"),
        Category("2", "CULTURA"),
        Category("3", "DEPORTES"),
        Category("4", "NOTICIAS"),
        Category("5", "INFANTIL"),
        Category("6", "CINE Y SERIES"),
        Category("7", "MUSICA"),
        Category("8", "DOCUMENTALES"),
        Category("9", "RELIGIOSOS"),
        Category("10", "INTERNACIONALES"),
        Category("11", "VARIEDADES"),
    )

    private val nombres = listOf(
        "Canal Uno HD", "Tele Centro 5 HD+", "Noticias 24 Horas", "Deportes Total HD",
        "Liga Total 2", "Cine Clasico", "Serie Plus", "Drama TV", "Mundo Infantil",
        "Musica Latina HD", "Documental Planeta",
        "Canal con un nombre bastante largo para ver como se corta en la fila HD+",
    )

    private val canales = (1..89).map { n ->
        Channel(
            streamId = n,
            name = nombres[(n - 1) % nombres.size] + if (n > nombres.size) " $n" else "",
            number = n,
            icon = null,
            categoryId = ((n - 1) % categorias.size + 1).toString(),
        )
    }

    @Before
    fun preparar() {
        File("build/capturas").mkdirs()
        aplicarTema()
    }

    @After
    fun restaurar() {
        Tint.skin = Skin.DEFAULT
        Tint.fontFamily = null
    }

    private fun aplicarTema(encima: String? = null, conArchivo: Boolean = false) {
        var skin = Skin.merge(Skin.DEFAULT, temaPublicado)
        if (encima != null) skin = Skin.merge(skin, encima)
        Tint.skin = skin
        Tint.fontFamily = if (conArchivo) archivo() else null
    }

    /**
     * Un aparato que se maneja con el dedo. Sin esto la app se cree en un
     * televisor: lo decide mirando si hay pantalla tactil, y el entorno de
     * pruebas no declara ninguna.
     */
    private fun conDedo() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        Shadows.shadowOf(pm).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
    }

    /** La tipografia del diseno. La baja el paso de CI; si no esta, se dibuja con la del sistema. */
    private fun archivo(): FontFamily? {
        val normal = File("build/fuentes/Archivo.ttf")
        val negra = File("build/fuentes/ArchivoBlack.ttf")
        if (normal.length() < 4096 || negra.length() < 4096) return null
        return FontFamily(Font(normal, FontWeight.Normal), Font(negra, FontWeight.Black))
    }

    private fun capturar(nombre: String, contenido: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/capturas/$nombre.png") {
            OrbitaTheme {
                Box(Modifier.fillMaxSize()) {
                    Fondo()
                    contenido()
                }
            }
        }
    }

    @Composable
    private fun Inicio(
        lista: List<Channel> = canales,
        categoria: String? = null,
        favoritos: Set<Int> = setOf(2, 5),
        cargando: Boolean = false,
        error: String? = null,
        aviso: String? = null,
        secciones: List<Seccion> = listOf(Seccion.EN_VIVO),
    ) {
        HomeScreen(
            categories = categorias,
            allChannels = lista,
            selectedCategory = categoria,
            favorites = favoritos,
            loading = cargando,
            error = error,
            engine = null,
            stats = PlaybackStats(),
            onCategory = {},
            onPlay = {},
            onStop = {},
            onToggleFavorite = {},
            notice = aviso,
            secciones = secciones,
        )
    }

    // ----------------------------------------------------- pantalla de inicio

    @Composable
    private fun Menu(actualizacion: UpdateInfo? = null) {
        InicioScreen(
            usuario = "Alex",
            vence = "2 de diciembre de 2026",
            conexiones = "1 de 3",
            subVivo = "1.234 canales",
            subPeliculas = "9 categorías",
            subSeries = "8 categorías",
            update = actualizacion,
            updating = false,
            onUpdate = {},
            onVivo = {},
            onPeliculas = {},
            onSeries = {},
            onCuenta = {},
            onDiagnostico = {},
            onAjustes = {},
        )
    }

    @Test
    fun tv_menu() = capturar("tv-00-menu") { Menu() }

    @Test
    fun tv_menu_con_actualizacion() = capturar("tv-00b-menu-con-actualizacion") {
        Menu(UpdateInfo(29, "1.0.29", "", ""))
    }

    @Test
    fun tv_menu_texto_grande() {
        aplicarTema("""{"escalaTexto":1.3}""", conArchivo = true)
        capturar("tv-00c-menu-texto-grande") { Menu() }
    }

    @Composable
    private fun Cuenta() {
        CuentaScreen(
            usuario = "Alex",
            servidor = "203.0.113.10:25461",
            estado = AccountStatus(true, "Active", "1796176800", 3, 1),
            cargando = false,
            error = null,
            onActualizar = {},
            onCambiarCuenta = {},
            onSalir = {},
        )
    }

    @Test
    fun tv_cuenta() = capturar("tv-23-cuenta") { Cuenta() }

    /** Diagnostico con el registro de fallas: tres casos distintos, para ver que se distinguen. */
    @Test
    fun tv_registro_de_fallas() = capturar("tv-25-registro-de-fallas") {
        fun falla(
            cuando: Long,
            que: String,
            motivo: String,
            proveedorMs: Int?,
            ipv4Ms: Int?,
            ipv6Ms: Int?,
            rssi: Int,
            tieneIpv4: Boolean = true,
        ) = Falla(
            cuando = cuando, que = que, motivo = motivo,
            proveedorMs = proveedorMs, proveedorError = if (proveedorMs == null) "sin respuesta en 4 s" else null,
            ipv4Ms = ipv4Ms, ipv4Error = if (ipv4Ms == null) "sin salida" else null,
            ipv6Ms = ipv6Ms, ipv6Error = if (ipv6Ms == null) "sin salida" else null,
            red = "wifi", rssi = rssi, enlaceMbps = 65, frecuenciaMhz = 2437,
            tieneIpv4 = tieneIpv4, tieneIpv6 = true, memoriaLibreMb = 388, pocaMemoria = false,
        )
        DiagnosticsScreen(
            running = false,
            findings = emptyList(),
            report = null,
            onRun = {},
            onExit = {},
            fallas = listOf(
                falla(
                    1_791_075_600_000, "Canal 5 El Lider · HLS (segmentado)",
                    "El servidor rechazó el pedido de este canal (403). No es la red: el proveedor contestó, y contestó que no.",
                    proveedorMs = 152, ipv4Ms = 41, ipv6Ms = 38, rssi = -58,
                ),
                falla(
                    1_791_072_000_000, "HCH · HLS (segmentado)",
                    "No se pudo abrir la conexion · reintento 2",
                    proveedorMs = null, ipv4Ms = null, ipv6Ms = 36, rssi = -61, tieneIpv4 = false,
                ),
                falla(
                    1_791_068_400_000, "Lista de canales",
                    "No se pudo abrir la conexion con el servidor.",
                    proveedorMs = null, ipv4Ms = 44, ipv6Ms = 39, rssi = -82,
                ),
            ),
        )
    }

    /** El detalle tecnico de un canal que se oye bien y se ve con bloques verdes. */
    @Test
    fun tv_detalle_tecnico() = capturar("tv-24-detalle-tecnico") {
        Box(Modifier.fillMaxSize()) {
            PlayerHud(
                PlaybackStats(
                    channelName = "Canal Uno HD",
                    variantLabel = "TS",
                    variantUrl = "http://203.0.113.10:25461/live/usuario/clave/1.ts",
                    status = "Reproduciendo",
                    bufferedSeconds = 21,
                    kbps = 6400,
                    healthySeconds = 95,
                    imagen = "H.264 High 4:2:2 · 1920x1080 · 25 fps · EL APARATO NO PUEDE",
                    decodificador = "OMX.amlogic.avc.decoder.awesome2",
                    cuadrosPerdidos = 3,
                )
            )
        }
    }

    // ------------------------------------------------------------ televisor

    @Test
    fun tv_inicio_tema_publicado() = capturar("tv-01-inicio-tema-publicado") { Inicio() }

    @Test
    fun tv_inicio_una_categoria() = capturar("tv-02-inicio-categoria") { Inicio(categoria = "3") }

    /** El tema que el disenador entrego, ya en dp, con su tipografia. */
    @Test
    fun tv_inicio_tema_del_disenador() {
        aplicarTema(
            """{"escalaTexto":1.1,"anchoBarraLateral":180,"margenPantalla":20,"separacion":5}""",
            conArchivo = true,
        )
        capturar("tv-03-inicio-tema-del-disenador") { Inicio() }
    }

    /**
     * La combinacion exacta que escondio los canales: barra lateral y margenes en
     * pixeles tomados como dp, texto al 110 % y la tipografia ancha. Con el
     * arreglo, la lista tiene que seguir viendose.
     */
    @Test
    fun tv_inicio_tema_que_rompia() {
        aplicarTema(
            """{"escalaTexto":1.1,"anchoBarraLateral":360,"margenPantalla":40,"separacion":10}""",
            conArchivo = true,
        )
        capturar("tv-04-inicio-tema-que-rompia") { Inicio() }
    }

    @Test
    fun tv_inicio_texto_al_maximo() {
        aplicarTema("""{"escalaTexto":1.6}""", conArchivo = true)
        capturar("tv-05-inicio-texto-al-maximo") { Inicio() }
    }

    @Test
    fun tv_inicio_sin_canales() = capturar("tv-06-inicio-error") {
        Inicio(
            lista = emptyList(),
            error = "No se pudo abrir la conexion con el servidor. El panel puede estar " +
                "perfecto y aun asi fallar si ESTA red no deja salir por su puerto. " +
                "Abre Diagnostico de red: te dice cual de las causas es.",
        )
    }

    /** Con las tres secciones a la vista: asi queda la cabecera cuando hay peliculas y series. */
    @Test
    fun tv_inicio_con_secciones() = capturar("tv-10-inicio-con-secciones") {
        Inicio(secciones = listOf(Seccion.EN_VIVO, Seccion.PELICULAS, Seccion.SERIES))
    }

    @Test
    fun tv_conexion() = capturar("tv-07-conexion") {
        LoginScreen(
            initial = Account(host = "203.0.113.10", port = 8080, username = "usuario", password = "clave"),
            error = null,
            busy = false,
            onSave = {},
            onDiagnostics = {},
        )
    }

    @Test
    fun tv_ajustes() = capturar("tv-08-ajustes") {
        SettingsScreen(
            settings = AppSettings(),
            onChange = {},
            onReloadSkin = {},
            onForget = {},
            onExit = {},
        )
    }

    @Test
    fun tv_diagnostico() = capturar("tv-09-diagnostico") {
        val hallazgos = listOf(
            Finding("Nombre del servidor", Level.OK, "IPv4: 203.0.113.10 · IPv6: ninguna · 12 ms"),
            Finding("Puerto 8080 por IPv4", Level.OK, "203.0.113.10 respondio en 95 ms"),
            Finding("Cuenta en el panel", Level.OK, "Autenticacion: correcta · estado: Active · vence: 01/11/2026"),
            Finding("Conexiones simultaneas", Level.OK, "Conexiones: 0 en uso de 2 permitidas"),
            Finding("Lista de canales", Level.OK, "89 canales en vivo"),
            Finding("Caudal · HLS (segmentado)", Level.WARN, "Primer byte en 310 ms · 2400 KB en 8 s · 2400 kbps · pausa mas larga sin datos: 3400 ms"),
        )
        DiagnosticsScreen(
            running = false,
            findings = hallazgos,
            report = DiagReport(
                hallazgos,
                "El enlace trae cortes y hay que absorberlos",
                listOf(
                    "Deja el orden HLS primero: es el unico formato que se recupera solo.",
                    "Sube el bufer a Extremo: 35 segundos de retardo a cambio de no cortarse.",
                ),
            ),
            onRun = {},
            onExit = {},
        )
    }

    // --------------------------------------------------- peliculas y series

    private val todas = listOf(Seccion.EN_VIVO, Seccion.PELICULAS, Seccion.SERIES)

    private val generos = listOf(
        Category("20", "ESTRENOS"),
        Category("21", "ACCIÓN Y AVENTURA"),
        Category("22", "COMEDIA"),
        Category("23", "DRAMA"),
        Category("24", "TERROR"),
        Category("25", "ANIMACIÓN E INFANTIL"),
        Category("26", "DOCUMENTALES"),
        Category("27", "CLÁSICOS"),
    )

    private val titulos = listOf(
        "La casa del acantilado", "Ruta al sur", "Tres veranos", "El último tren",
        "Noche de estreno", "Cuenta regresiva", "Un título bastante largo para ver cómo se corta en la tarjeta",
        "Mar adentro", "La visita", "Vecinos", "El mapa", "Ciudad dormida", "Domingo", "Fuera de hora",
    )

    private val tarjetas = titulos.mapIndexed { i, t ->
        Tarjeta(
            id = 5000 + i,
            titulo = t,
            imagen = null,
            detalle = (2010 + i).toString() + "  ·  ★ " + (6 + i % 3) + "," + (i % 10),
            avance = if (i == 1) 0.35f else null,
            favorita = i == 2,
        )
    }

    @Composable
    private fun Catalogo(
        seccion: Seccion = Seccion.PELICULAS,
        abierta: String? = "20",
        lista: List<Tarjeta> = tarjetas,
        cargando: Boolean = false,
        error: String? = null,
    ) {
        CatalogoScreen(
            seccion = seccion,
            secciones = listOf(seccion),
            onSeccion = {},
            categorias = generos,
            abierta = abierta,
            haySeguir = true,
            hayFavoritas = true,
            tarjetas = lista,
            cargando = cargando,
            error = error,
            estadoGrilla = rememberLazyGridState(),
            enfocar = null,
            onCategoria = {},
            onAbrir = {},
            onFavorita = {},
        )
    }

    private val pelicula = Movie(5001, "Ruta al sur", null, "20", "mkv", 7.4f, "2019")

    private val fichaDePelicula = MovieInfo(
        plot = "Dos hermanos que no se hablan desde hace años tienen que cruzar el país en una " +
            "camioneta prestada para llegar a tiempo a la venta de la casa familiar. En el camino " +
            "aparece lo que ninguno quería discutir, y una avería a mitad de la nada los obliga a " +
            "pasar tres días en un pueblo donde nadie tiene apuro.",
        genre = "Drama, Comedia",
        cast = "Ana Uno, Luis Dos, Eva Tres, Juan Cuatro",
        director = "Marta Cinco",
        released = "2019-03-08",
        durationSecs = 6130,
        rating = 7.4f,
        backdrop = null,
        poster = null,
        extension = "mkv",
    )

    private val serie = Series(
        77, "Los vecinos", null, "22",
        "Un edificio de seis pisos, un ascensor que no anda y una administradora que renuncia " +
            "cada lunes. Los vecinos del tercero quieren cambiar todo; los del quinto, que nada cambie.",
        "Comedia", "Ana Uno, Luis Dos", "2015-04-02", 8.1f, null,
    )

    private val detalleDeSerie = SeriesDetail(
        serie,
        listOf(
            Season(1, (1..9).map { n ->
                Episode(9000 + n, 1, n, listOf("Piloto", "La mudanza", "El ascensor", "Reunión de consorcio",
                    "La gotera", "Un título de episodio bastante largo para ver cómo se corta en la fila",
                    "Vacaciones", "El plomero", "Final de temporada")[n - 1], "mp4", 1320 + n * 15, null, null)
            }),
            Season(2, (1..6).map { n -> Episode(9100 + n, 2, n, "Episodio $n", "mp4", 1380, null, null) }),
        ),
    )

    private val bibliotecaDeSerie = Biblioteca()
        .conAvance(Biblioteca.claveEpisodio(9001), 1335_000L, 1335_000L, 1)
        .conAvance(Biblioteca.claveEpisodio(9002), 1350_000L, 1350_000L, 2)
        .conAvance(Biblioteca.claveEpisodio(9003), 540_000L, 1365_000L, 3)
        .conPunto(77, PuntoDeSerie(9003, 1, 3))

    @Test
    fun tv_peliculas() = capturar("tv-11-peliculas") { Catalogo() }

    @Test
    fun tv_peliculas_texto_grande() {
        aplicarTema("""{"escalaTexto":1.3}""", conArchivo = true)
        capturar("tv-12-peliculas-texto-grande") { Catalogo() }
    }

    @Test
    fun tv_series_cargando_y_vacia() = capturar("tv-13-series-seguir-vacio") {
        Catalogo(seccion = Seccion.SERIES, abierta = CAT_SEGUIR, lista = emptyList())
    }

    @Test
    fun tv_ficha_pelicula() = capturar("tv-14-ficha-pelicula") {
        FichaPeliculaScreen(
            pelicula = pelicula,
            info = fichaDePelicula,
            cargandoInfo = false,
            avance = Avance(23 * 60_000L + 10_000L, 6130_000L, 0),
            favorita = true,
            onReproducir = {},
            onFavorita = {},
            onSalir = {},
        )
    }

    @Test
    fun tv_ficha_pelicula_sin_datos() = capturar("tv-15-ficha-pelicula-sin-datos") {
        FichaPeliculaScreen(
            pelicula = pelicula.copy(name = "Un título de película bastante largo para ver cómo queda en dos líneas", year = null, rating = null),
            info = null,
            cargandoInfo = false,
            avance = null,
            favorita = false,
            onReproducir = {},
            onFavorita = {},
            onSalir = {},
        )
    }

    @Test
    fun tv_ficha_serie() = capturar("tv-16-ficha-serie") {
        FichaSerieScreen(
            serie = serie,
            detalle = detalleDeSerie,
            cargando = false,
            error = null,
            biblioteca = bibliotecaDeSerie,
            favorita = false,
            onEpisodio = { _, _ -> },
            onFavorita = {},
            onReintentar = {},
            onSalir = {},
        )
    }

    @Test
    fun tv_ficha_serie_con_error() = capturar("tv-17-ficha-serie-error") {
        FichaSerieScreen(
            serie = serie,
            detalle = null,
            cargando = false,
            error = "No se pudo abrir la conexion con el servidor. El panel puede estar perfecto " +
                "y aun asi fallar si ESTA red no deja salir por su puerto.",
            biblioteca = Biblioteca(),
            favorita = true,
            onEpisodio = { _, _ -> },
            onFavorita = {},
            onReintentar = {},
            onSalir = {},
        )
    }

    @Composable
    private fun Cine(stats: CineStats, objetivo: Long? = null, cuenta: Int = -1, esTv: Boolean = true) {
        Box(Modifier.fillMaxSize().background(Color(0xFF14202B))) {
            CineCapas(
                titulo = "Los vecinos",
                subtitulo = "Temporada 1  ·  Episodio 3  ·  El ascensor",
                stats = stats,
                objetivo = objetivo,
                visibles = true,
                esTv = esTv,
                cuenta = cuenta,
                onBarra = {},
                botones = {},
            )
        }
    }

    private val enCurso = CineStats(
        estado = "Reproduciendo", posMs = 540_000L, durMs = 1365_000L, bufferMs = 28_000L,
        reproduciendo = true, cargando = false, audios = 2, audio = "Español", subtitulos = 1,
    )

    @Test
    fun tv_cine_en_curso() = capturar("tv-18-cine-en-curso") { Cine(enCurso) }

    @Test
    fun tv_cine_saltando_en_pausa() = capturar("tv-19-cine-salto-y-aviso") {
        Cine(
            enCurso.copy(
                estado = "En pausa", reproduciendo = false, enPausa = true,
                aviso = "Este aparato no puede decodificar el sonido de este video " +
                    "(Dolby Digital Plus, E-AC-3). Se ve pero no se oye.",
            ),
            objetivo = 900_000L,
        )
    }

    @Test
    fun tv_cine_error() = capturar("tv-20-cine-error") {
        Cine(
            enCurso.copy(
                error = "El servidor dejó de mandar datos y no volvió después de varios intentos. " +
                    "Pulsa OK para reintentar: sigue desde donde estaba.",
            ),
        )
    }

    @Test
    fun tv_cine_siguiente() = capturar("tv-21-cine-siguiente") {
        Cine(enCurso.copy(estado = "Terminado", terminado = true, reproduciendo = false, posMs = 1365_000L), cuenta = 6)
    }

    // ------------------------------------------------------------- telefono

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_inicio() {
        conDedo()
        capturar("tel-01-inicio-vertical") { Inicio() }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_HORIZONTAL)
    fun telefono_horizontal_inicio() {
        conDedo()
        capturar("tel-02-inicio-horizontal") { Inicio() }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_con_secciones() {
        conDedo()
        capturar("tel-03-inicio-con-secciones") {
            Inicio(secciones = listOf(Seccion.EN_VIVO, Seccion.PELICULAS, Seccion.SERIES))
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_menu() {
        conDedo()
        capturar("tel-00-menu") { Menu() }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_cuenta() {
        conDedo()
        capturar("tel-08-cuenta") { Cuenta() }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_peliculas() {
        conDedo()
        capturar("tel-04-peliculas") { Catalogo() }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_ficha_pelicula() {
        conDedo()
        capturar("tel-05-ficha-pelicula") {
            FichaPeliculaScreen(
                pelicula = pelicula,
                info = fichaDePelicula,
                cargandoInfo = false,
                avance = null,
                favorita = false,
                onReproducir = {},
                onFavorita = {},
                onSalir = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_VERTICAL)
    fun telefono_vertical_ficha_serie() {
        conDedo()
        capturar("tel-06-ficha-serie") {
            FichaSerieScreen(
                serie = serie,
                detalle = detalleDeSerie,
                cargando = false,
                error = null,
                biblioteca = bibliotecaDeSerie,
                favorita = false,
                onEpisodio = { _, _ -> },
                onFavorita = {},
                onReintentar = {},
                onSalir = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = TELEFONO_HORIZONTAL)
    fun telefono_horizontal_cine() {
        conDedo()
        capturar("tel-07-cine") { Cine(enCurso, esTv = false) }
    }
}
