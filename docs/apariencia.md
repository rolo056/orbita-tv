# Cómo diseñar la apariencia de ALEX TV

Este archivo es el encargo completo. Se le entrega a quien vaya a diseñar la
apariencia —una persona o un asistente— y alcanza por sí solo: describe la app,
el contexto de uso, todos los valores que se pueden cambiar y las restricciones
que no se pueden romper.

**Lo que se espera de vuelta: un único bloque de JSON válido**, con las claves de
la tabla de abajo y nada más. Ese JSON se pega en el editor y el televisor lo
toma en segundos.

---

## 1. Qué es la app y cómo se mira

Un reproductor de televisión en vivo para Android TV. No se usa con el dedo ni con
mouse: se usa con **mando a distancia**, a **tres metros de distancia**, casi
siempre **de noche y con la luz apagada**, y buena parte del tiempo la pantalla
muestra **video a pantalla completa**.

Eso manda tres cosas antes que cualquier gusto estético:

1. **El anillo de foco es el único cursor.** El usuario no ve un puntero; lo único
   que le dice dónde está parado es el borde de la fila enfocada. Si ese borde no
   se distingue de un golpe de vista desde el sofá, la app es inusable, por linda
   que sea.
2. **Nada de texto chico.** A tres metros, todo lo que en un monitor parece
   "elegante y sutil" desaparece.
3. **La interfaz convive con video.** Los paneles de información se dibujan sobre
   la imagen en movimiento, así que no pueden depender de un fondo previsible.

## 2. Las pantallas, y dónde cae cada valor

```
┌──────────────────────────────────────────────────────────────────────────┐
│  CANALES EN VIVO                           margenPantalla en todo el borde │
│ ┌────────────┐  89 CANALES   <- textoSuave                                 │
│ │ logoUrl    │  Todos los canales        EN VIVO  PELÍCULAS  SERIES  20:17 │
│ │ o "ALEX TV"│  ───────────────────────────────────── <- linea ─────────── │
│ │            │ ┌─────────────────────────────┐ ┌─────────────────────────┐ │
│ │ CATEGORÍAS │ │ 001  Canal Uno HD           │ │                         │ │
│ │ Todos   89 │ └─────────────────────────────┘ │   el canal, en chico    │ │
│ │ Deportes 8 │ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ │   (el mismo video que   │ │
│ │ Noticias 8 │ ┃ 002  ENFOCADO           ★   ┃ │   la pantalla completa) │ │
│ │ Cine     8 │ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ └─────────────────────────┘ │
│ │            │   ^ tarjetaFoco + acento +       ┌─────────────────────────┐ │
│ │ Diagnóstico│     grosorFoco; escalaFoco       │ 002  Nombre del canal   │ │
│ │ Ajustes    │     lo agranda                   │ Reproduciendo · HLS     │ │
│ └────────────┘                                  └─────────────────────────┘ │
│  ^ anchoBarraLateral                                                       │
│                 ▲▼ Moverse   OK Ver en ventana   OK ×2 Pantalla completa   │
└──────────────────────────────────────────────────────────────────────────┘
        fondo en todo · fondoImagenUrl detrás de todo

┌──────────────────────────────────────────────────────────────────────────┐
│  PELÍCULAS y SERIES: las mismas categorías a la izquierda, y portadas      │
│ ┌────────────┐  14 PELÍCULAS                                               │
│ │ "ALEX TV"  │  ESTRENOS                 EN VIVO  PELÍCULAS  SERIES  20:17 │
│ │            │  ────────────────────────────────────────────────────────── │
│ │ Seguir     │ ┌────────┐ ┏━━━━━━━━┓ ┌────────┐ ┌────────┐ ┌────────┐      │
│ │  viendo    │ │        │ ┃        ┃ │      ★ │ │        │ │        │      │
│ │ Mis        │ │portada │ ┃ENFOCADA┃ │portada │ │portada │ │portada │      │
│ │  películas │ │        │ ┃        ┃ │        │ │        │ │        │      │
│ │ ESTRENOS   │ │▓▓▓░░░░░│ ┃        ┃ │        │ │        │ │        │      │
│ │ COMEDIA    │ │Título  │ ┃Título  ┃ │Título  │ │Título  │ │Título  │      │
│ │ DRAMA      │ │2019 ★7 │ ┃2020 ★8 ┃ │2021 ★6 │ │2018 ★7 │ │2022 ★8 │      │
│ │            │ └────────┘ ┗━━━━━━━━┛ └────────┘ └────────┘ └────────┘      │
│ └────────────┘   ^ la barra bajo la portada es lo visto (acento)           │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│  REPRODUCTOR: video a pantalla completa, sin marco                         │
│                                                                            │
│   en un canal:                          en una película o un episodio:     │
│ ┌──────────────────────────────┐      ┌─────────────────────────────────┐  │
│ │ Nombre del canal   <- texto  │      │ Título            EN PAUSA <-ace.│  │
│ │ Reproduciendo · HLS          │      │ Temporada 1 · Episodio 3         │  │
│ └──────────────────────────────┘      │ ▓▓▓▓▓▓▓▓▒▒░░░░░░░░░░░░░░░░░░░░░  │  │
│                                       │ 9:00 / 22:45   ◀▶ OK ▲ ▼         │  │
│                                       └─────────────────────────────────┘  │
│   ^ estos paneles van SOBRE el video, con su propio fondo negro            │
│     translúcido que no es configurable. Lo visto va en acento.             │
└──────────────────────────────────────────────────────────────────────────┘
```

Hay además dos fichas (la de una película, con su sinopsis y el botón para
verla, y la de una serie, con sus temporadas y episodios) que usan las mismas
tarjetas y el mismo anillo de foco, y una pantalla de **diagnóstico de red**,
donde `bien`, `aviso` y `falla` son puntos de color que indican si cada prueba
pasó, avisó o falló. Esos tres colores tienen que distinguirse entre sí a tres
metros. `bien` es también el color de "Visto" en la lista de episodios.

Las pantallas de verdad, dibujadas con el tema actual, están en la publicación
`capturas` del repositorio: mirarlas vale más que estos esquemas.

## 3. Los valores que se pueden cambiar

| Clave | Tipo | Rango | Por omisión | Dónde se ve |
|---|---|---|---|---|
| `fondo` | color | — | `#0B1020` | Lienzo de todas las pantallas |
| `tarjeta` | color | — | `#141E33` | Fondo de cada fila y tarjeta en reposo |
| `tarjetaFoco` | color | — | `#1E2C49` | Fondo de la fila enfocada |
| `linea` | color | — | `#243352` | Borde de las tarjetas en reposo |
| `texto` | color | — | `#E8EDF5` | Texto principal |
| `textoSuave` | color | — | `#9BA7BD` | Texto secundario, rótulos, ayudas |
| `acento` | color | — | `#5FC9A0` | Anillo de foco, categoría o sección activa |
| `aviso` | color | — | `#E0B252` | Advertencias del diagnóstico |
| `falla` | color | — | `#E2705F` | Errores y canal detenido |
| `bien` | color | — | `#5FC9A0` | "Todo bien": la prueba que pasó, la opción activada. Aparte del acento para que un acento rojo no haga leer un "activado" como error |
| `esquinas` | número (dp) | 0 – 40 | `10` | Redondeo de tarjetas |
| `grosorFoco` | número (dp) | 0 – 8 | `2` | Grosor del anillo al enfocar |
| `grosorReposo` | número (dp) | 0 – 8 | `1` | Grosor del borde en reposo |
| `escalaFoco` | número | 1 – 1.15 | `1` | Cuánto crece la fila enfocada |
| `escalaTexto` | número | 0.7 – 1.6 | `1` | Multiplica **todos** los textos a la vez |
| `fuenteUrl` | texto o `null` | — | `null` | Peso normal. Un `.ttf` accesible por HTTP |
| `fuenteUrlNegrita` | texto o `null` | — | `null` | Peso pesado, para títulos y nombres de canal |
| `margenPantalla` | número (dp) | 0 – 80 | `24` | Margen exterior |
| `separacion` | número (dp) | 0 – 40 | `8` | Espacio entre filas y tarjetas |
| `disposicion` | `"lista"` o `"mosaico"` | — | `"lista"` | Cómo se listan los canales |
| `anchoTile` | número (dp) | 120 – 520 | `260` | Ancho mínimo de tarjeta en mosaico; las columnas se acomodan solas |
| `mostrarLogos` | `true` / `false` | — | `true` | Logo del canal, o su número si se apaga |
| `anchoBarraLateral` | número (dp) | 160 – 520 | `300` | Ancho de la columna de categorías |
| `logoUrl` | texto o `null` | — | `null` | Reemplaza el texto "ALEX TV" |
| `fondoImagenUrl` | texto o `null` | — | `null` | Imagen detrás de todo |
| `fondoImagenOpacidad` | número | 0 – 1 | `0.25` | Opacidad de esa imagen |

Los colores aceptan `#RGB`, `#RRGGBB` y `#AARRGGBB`. Sin canal alfa se asume
opaco.

**No se puede cambiar por este archivo**: el tamaño individual de cada texto (solo
el multiplicador global), la posición de los paneles, qué información se muestra,
las pantallas existentes ni el fondo translúcido de los paneles sobre el video.
Todo eso necesita una versión nueva de la app.

## 4. Restricciones que no se pueden romper

Estas no son preferencias. Un diseño que las incumpla hace la app peor de usar,
aunque se vea mejor en una captura de pantalla.

**Contraste**
- `texto` sobre `tarjeta` y sobre `fondo`: al menos 7:1.
- `textoSuave` sobre `tarjeta`: al menos 4.5:1. Es texto pequeño y es el que
  primero se pierde.
- `acento` contra `tarjeta` **y** contra `tarjetaFoco`: al menos 3:1 en ambas. Si
  solo contrasta con una, el anillo de foco desaparece justo cuando hace falta.

**El foco**
- `tarjetaFoco` tiene que diferenciarse de `tarjeta` por sí solo, sin depender del
  borde. Dos estados que solo cambian el borde no se notan de lejos.
- `grosorFoco` por debajo de 2 no se ve a tres metros. Si bajás el grosor,
  compensá con `escalaFoco` o con más diferencia de fondo.
- `acento` no debería usarse para nada decorativo: es la marca de "acá estás".
  Cuanto más aparece en otros lados, menos sirve.

**Particularidades del televisor**
- **Evitá el blanco puro** (`#FFFFFF`) para el texto: en televisores produce halo
  alrededor de las letras y cansa en una habitación a oscuras. Un blanco apenas
  teñido se lee mejor y se ve más caro.
- **Evitá el negro puro** (`#000000`) como lienzo: aplana la imagen, borra el
  límite de las tarjetas y hace que la interfaz se confunda con el video, que sí
  es negro de verdad.
- **Las líneas finas muy saturadas se ensucian.** La compresión de color de la
  señal de TV arrastra los bordes de 1 dp: `linea` funciona mejor poco saturada.
- **Los rojos y naranjas saturados en áreas grandes desbordan.** Reservalos para
  detalles chicos, como el punto de `falla`.
- **Dejá `margenPantalla` en 24 o más.** Hay televisores que recortan un 2 o 3 %
  del borde y se comen el contenido.

**Legibilidad**
- `escalaTexto` por debajo de 0.9 deja el texto secundario al límite de lo
  legible desde el sofá. Si querés una interfaz más densa, bajá `separacion` y
  `margenPantalla` antes de bajar el texto.
- En `mosaico`, `anchoTile` por debajo de 180 corta los nombres de canal a dos
  líneas con puntos suspensivos casi siempre.

## 5. Qué devolver

Un solo bloque de JSON, sin comentarios y sin texto alrededor. Podés incluir solo
las claves que quieras cambiar: **lo que no esté conserva su valor de fábrica**, y
un valor mal escrito o fuera de rango se descarta solo. No hay forma de romper la
app desde este archivo.

Ejemplo de respuesta válida:

```json
{
  "fondo": "#0E1116",
  "tarjeta": "#171B22",
  "tarjetaFoco": "#242A34",
  "linea": "#252B35",
  "texto": "#E6E1D8",
  "textoSuave": "#9A968E",
  "acento": "#C9A227",
  "aviso": "#D8A657",
  "falla": "#D2685E",
  "esquinas": 6,
  "grosorFoco": 3,
  "grosorReposo": 1,
  "escalaFoco": 1.02,
  "escalaTexto": 1.05,
  "margenPantalla": 32,
  "separacion": 10,
  "disposicion": "mosaico",
  "anchoTile": 280,
  "mostrarLogos": true,
  "anchoBarraLateral": 320
}
```

Si además querés proponer una tipografía, hacen falta **dos** archivos `.ttf`:
uno de peso normal para el cuerpo y uno pesado para títulos y nombres de canal.
Con un solo peso pesado el texto secundario queda ilegible a tres metros.

Los dos enlaces tienen que apuntar al archivo directamente y responder por HTTP
sin redirigir a una página. Un enlace a la ficha de una fuente en un sitio web no
sirve. Si alguno no es una fuente válida, se descarta y se usa la del sistema: la
app comprueba la firma del archivo y obliga a Android a interpretarlo antes de
aplicarlo, justamente para que una descarga rota no deje la interfaz sin dibujar.
