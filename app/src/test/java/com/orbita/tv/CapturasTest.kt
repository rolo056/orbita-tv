package com.orbita.tv

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbita.tv.data.Account
import com.orbita.tv.data.AppSettings
import com.orbita.tv.data.Skin
import com.orbita.tv.diag.DiagReport
import com.orbita.tv.diag.Finding
import com.orbita.tv.diag.Level
import com.orbita.tv.net.Category
import com.orbita.tv.net.Channel
import com.orbita.tv.player.PlaybackStats
import com.orbita.tv.ui.DiagnosticsScreen
import com.orbita.tv.ui.HomeScreen
import com.orbita.tv.ui.LoginScreen
import com.orbita.tv.ui.OrbitaTheme
import com.orbita.tv.ui.Seccion
import com.orbita.tv.ui.SettingsScreen
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
                Box(Modifier.fillMaxSize().background(Tint.bg)) { contenido() }
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
            onDiagnostics = {},
            onSettings = {},
            update = null,
            updating = false,
            onUpdate = {},
            notice = aviso,
            secciones = secciones,
        )
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
}
