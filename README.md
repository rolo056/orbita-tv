# ALEX TV

Reproductor de listas Xtream para Android TV, hecho para un enlace satelital que
se corta. La app no intenta ser bonita: intenta no quedarse en negro.

## El problema que resuelve

En Starlink los canales de IPTV fallan mientras YouTube y el navegador andan
perfecto. No es una casualidad ni mala suerte: son cinco causas concretas, y
ninguna se parece a lo que hace YouTube.

| Causa | Por qué YouTube no la sufre | Qué hace ALEX TV |
|---|---|---|
| El canal viene como MPEG-TS crudo sobre **una** conexión TCP que debe durar horas. Starlink salta de satélite cada ~15 s y pierde paquetes; la conexión muere y no hay nada que reintentar. | YouTube usa segmentos reintentables, búfer grande y QUIC, que se recupera rápido de la pérdida. | Pide el canal en HLS primero (segmentado, se recupera solo), con TS como respaldo. Búfer de 30 a 180 s y retardo deliberado respecto del directo. |
| **La imagen se congela sin dar error**: el socket queda abierto pero deja de traer bytes. ExoPlayer y casi todos los reproductores esperan para siempre. | No aplica. | Vigilante propio: compara la posición de reproducción cada segundo y, si no avanza, corta y reconecta por su cuenta. |
| **CGNAT**: la IP de salida es la del punto de presencia de Starlink, no la de tu ciudad. Muchos proveedores bloquean por país o por rango y devuelven 403 o lista vacía. | A Google le da igual desde dónde entres. | El diagnóstico lo identifica y lo dice con esas palabras, en vez de mostrar "no carga". Esto no lo arregla una app: hay que habilitar la IP con el proveedor. |
| **IPv6**: Starlink entrega IPv6 nativo. Si el panel publica un registro AAAA que en realidad no atiende, el televisor lo intenta primero y se cuelga. | Google atiende IPv6 de verdad. | "Forzar IPv4" descarta los AAAA. El diagnóstico prueba las dos familias por separado y compara. |
| **La cuenta ocupada**: el panel permite N conexiones y ya las tiene todas abiertas. | No aplica. | El diagnóstico lee `active_cons` y `max_connections` del propio panel y lo informa. Es la causa que más se confunde con un problema de red. |

## Cómo se compila

No hace falta instalar nada: GitHub Actions compila el APK en cada empujón a
`main` y también a pedido, desde la pestaña **Actions → Compilar APK → Run
workflow**.

Al terminar deja el APK en dos lados:

- como artefacto de la ejecución, y
- publicado en la etiqueta `ultima`, que siempre apunta a la última compilación.
  La URL de descarga no cambia nunca, así que sirve para instalar desde el
  televisor con la app Downloader.

El APK va firmado con la clave de depuración a propósito: se instala a mano, no
pasa por Play Store.

## Cómo se instala en el televisor

1. En el TV: Ajustes → Seguridad → permitir instalar de orígenes desconocidos.
2. Instala **Downloader** desde la tienda del televisor.
3. Pega la URL del APK de la etiqueta `ultima`.
4. Al abrir la app: pega la URL que te dio el proveedor y se rellenan los campos
   solos. Escribir a mano con el mando es el peor trabajo del mundo.

Con `adb` sobre la red, si el TV lo tiene habilitado:
`adb connect IP_DEL_TV:5555` y después `adb install -r orbita-tv.apk`.

## Diagnosticar sin esperar el APK

El mismo juego de pruebas corre desde cualquier computadora conectada a la misma
red que el televisor, sin instalar nada:

```
node tools/diagnose.mjs --url "http://servidor:puerto/get.php?username=U&password=P"
node tools/diagnose.mjs --host servidor --port 8080 --user U --pass P --ip
```

`--ip` agrega una consulta a un servicio externo para ver desde qué ciudad te ve
el proveedor. Sin esa opción, nada sale de tu red.

La cifra que importa no es el promedio de caudal: es **la pausa más larga sin
datos**. Un promedio bueno con una pausa de cuatro segundos es exactamente lo
que congela la imagen en un reproductor sin vigilante.

## Cambiar la apariencia sin reinstalar

La apariencia sale de [tema.json](tema.json), no del código. La app lo lee al
abrir y aplica lo que diga encima de los valores de fábrica.

La regla que lo hace seguro: **el archivo no reemplaza el tema, lo pisa campo por
campo**. Una clave que no exista, un color mal escrito o un número fuera de rango
se descartan y ese campo conserva su valor. No hay forma de dejar el televisor
con una pantalla inservible por un error de tipeo.

Se puede cambiar por datos: colores, esquinas, grosor y color del anillo de foco,
escala de todos los textos de una vez, tipografía (un `.ttf` remoto), márgenes,
lista contra mosaico, ancho de las tarjetas, mostrar u ocultar logos, ancho de la
barra lateral, logo de marca e imagen de fondo con su opacidad.

Necesita APK nuevo: pantallas nuevas, controles nuevos y cualquier cambio de
estructura que no esté previsto como opción.

**Modo diseño** (Ajustes): consulta el archivo cada 3 segundos en vez de una sola
vez al abrir, así se trabaja el diseño viendo el televisor cambiar. Déjalo
apagado para ver televisión.

### El editor en vivo

```
node tools/servidor-tema.mjs
```

Imprime dos direcciones: el editor para abrir en el navegador de la PC, y la que
va en Ajustes → Dirección del archivo de apariencia en el televisor. Con Modo
diseño activado, mueves un control y el TV cambia en 3 segundos.

Escribe el `tema.json` de verdad, así que cuando el diseño te guste solo queda
confirmarlo en git. No toca las claves que empiezan con guion bajo: son las notas
para quien edite el archivo a mano.

Funciona porque el televisor y la PC están en la misma red, que es exactamente
cuando estás diseñando. Para dejarlo fuera de la red local, el mismo archivo
corre en un servidor sin cambios.

Tres capas, en este orden, y por eso la app nunca arranca fea: lo que trae el
APK, lo último que se descargó bien (guardado en disco, sirve sin red), y lo que
responda el servidor ahora.

## Actualización del APK

Cada compilación publica un `version.json` junto al APK, con el número de
compilación como número de versión. La app lo consulta al abrir y, si hay algo
nuevo, ofrece descargarlo e instalarlo con un OK del mando. Sigue siendo una
instalación —Android siempre pide confirmación— pero sin Downloader y sin
escribir direcciones.

## Los ajustes, y qué falla arregla cada uno

| Ajuste | Cuándo tocarlo |
|---|---|
| Forzar IPv4 | Los canales tardan mucho en arrancar y después fallan. Déjalo activado. |
| Una conexión por pedido (evita HTTP/2) | Cortes durante la reproducción. Déjalo activado. |
| Resolver nombres por Cloudflare | Solo si el diagnóstico falla al resolver el nombre del servidor. |
| Búfer Normal / Satélite / Extremo | Más búfer es más retardo respecto del directo, y es justo lo que compra aguante para cruzar un corte. Satélite es el punto medio. |
| Segmentado / Directo primero | Segmentado se recupera solo. Directo da mejor imagen y menos retardo, pero muere en el primer corte. |
| Vigilante: 5 / 8 / 12 / 20 s | Cuánto esperar con la imagen congelada antes de reconectar solo. |
| Identificación ante el panel | Si el canal da 403 con la cuenta al día: algunos paneles solo entregan video a los reproductores que reconocen. |

Durante la reproducción, la tecla **Menú** abre el detalle técnico: búfer en
segundos, caudal, cuántas reconexiones hubo y desde cuándo está estable. Es lo
que responde "¿fue la red o fue el panel?" sin adivinar.

## Controles

| Tecla | Qué hace |
|---|---|
| Arriba / abajo | Canal anterior / siguiente |
| OK | Muestra u oculta la barra del canal |
| Menú | Detalle técnico |
| Atrás | Volver a la lista |

## Lo que la app no puede arreglar

Si el proveedor bloquea el rango de IP de Starlink, ninguna configuración lo
sortea: hay que pasarle la IP de salida y pedir que la habilite. El diagnóstico
sirve para llegar a esa conversación con el dato en la mano, en vez de discutir
sobre "no me carga".

## Cómo está armado

- Kotlin + Jetpack Compose, sin dependencias de TV específicas (foco manejado a
  mano, con anillo de foco visible en todo lo enfocable).
- Media3 / ExoPlayer con `LoadControl` propio, política de reintentos y
  extractor de TS tolerante a engancharse a mitad de GOP.
- OkHttp con DNS propio (filtro IPv4, DNS sobre HTTPS opcional) y HTTP/1.1
  forzado. El mismo cliente para la API y para el video, así lo que mide el
  diagnóstico es lo que después usa el reproductor.
- Sin backend, sin cuentas, sin base de datos. Las credenciales quedan en
  DataStore, en el televisor.

| Archivo | Qué resuelve |
|---|---|
| `player/ResilientPlayer.kt` | Búferes, escalera de reintentos y vigilante de congelamiento. El corazón. |
| `player/StreamVariants.kt` | Las tres formas de pedir el mismo canal, en orden. |
| `net/SmartDns.kt` | Filtro IPv4 y DNS sobre HTTPS. |
| `diag/Diagnostics.kt` | Las pruebas y el veredicto en palabras claras. |
| `tools/diagnose.mjs` | Las mismas pruebas desde una computadora. |
