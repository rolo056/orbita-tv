# Reexportación de la marca para Android TV

**Estado: completo. No hace falta nada más.** Este archivo queda como registro de
qué se pidió, por qué, y cómo terminó resuelto cada pieza.

| Pieza | Cómo quedó |
|---|---|
| Banner del televisor | PNG 320 × 180 en `res/drawable-xhdpi/tv_banner.png` |
| Icono de la aplicación | PNG 192 × 192 en `res/mipmap-xxxhdpi/ic_launcher.png` |
| Logotipo de la barra lateral | Dibujado con texto real en la app, sin archivo |

El problema original —letras como texto en vez de trazos— se resolvió entregando
PNG ya rasterizados para el icono y un SVG vectorizado para el banner. Para el
icono el PNG es incluso preferible: evita depender de que el formato vectorial de
Android sepa reproducir el degradado.

## El problema

En los cinco archivos, las letras son elementos `<text>`. Dos consecuencias:

1. **El formato vectorial de Android no dibuja texto.** Solo acepta trazos
   (`<path>`). Si convertimos los archivos tal como están, se obtiene el fondo y
   los bloques de color **sin ninguna letra**.
2. **Ningún archivo declara tipografía.** Los `<text>` no traen `font-family`, así
   que cada programa que los abre dibuja "ALEX" con la fuente que tenga a mano,
   estirada al ancho fijo de `textLength`. Hoy el logotipo no se ve igual en dos
   lugares distintos.

## Lo que falta: un archivo

### El icono de la aplicación, con el texto en contornos

`alextv-icono-4b-rojo-profundo.svg`, reexportado con el texto pasado a trazos. En
la mayoría de los programas la opción se llama *convertir a contornos*,
*vectorizar texto* u *outline text*. El archivo resultante no debe contener
ningún `<text>`.

Si preferís mantenerlo como texto, la alternativa es mandar también el archivo de
la tipografía (`.ttf` o `.otf`) y declararla con `font-family`. Contornos es más
simple y no deja nada al azar.

Un PNG cuadrado de 512 × 512 también sirve, si resulta más rápido. Para el icono
no hace falta que sea vectorial.

### Ya resuelto

- **El banner**: llegó en 320 × 180, 1280 × 720 y 1920 × 1080, con el texto ya
  vectorizado. Instalado en la app.
- **El logotipo de la barra lateral**: se dibuja con texto real dentro de la app,
  tomando los colores del tema. No hace falta ningún archivo.

### Un carácter corrupto

En `alextv-icono-4c-carbon.svg`, la etiqueta dice `TV � 4K`: el separador se
perdió en la exportación. No lo usamos como icono, pero conviene corregirlo en el
original.

## Qué soporta el formato de Android, para que no se pierda nada

| Sí | No |
|---|---|
| Trazos con relleno y contorno | Texto (`<text>`, `<tspan>`) |
| Degradados lineales y radiales | Tramas y rellenos de patrón (`<pattern>`) |
| Grupos con traslación, rotación y escala | Filtros, desenfoques, sombras (`<filter>`) |
| Opacidad por elemento | Máscaras y modos de fusión |
| Recortes con trazos simples | Imágenes incrustadas (`<image>`) |

El degradado radial del icono rojo profundo **sí** se convierte bien, así que ese
efecto se conserva.

## Qué se usa y dónde

| Archivo | Destino | Estado |
|---|---|---|
| `alextv-icono-4b-rojo-profundo.svg` | Icono de la aplicación | **Lo único pendiente**: esperando contornos |
| `banner/alextv-banner-androidtv-320x180.png` | Banner del televisor | Instalado en la app |
| `alextv-logotipo-fondo-oscuro.svg` | Barra lateral de la app | **Ya resuelto**: se dibuja con texto real dentro de la app, tomando los colores del tema. Una versión en contornos igual sirve para otros usos. |
| `alextv-logotipo-fondo-claro.svg` | Fondos claros, fuera de la app | Sin uso por ahora |
| `alextv-icono-4a-reticula.svg`, `4c-carbon.svg` | Variantes no elegidas | Se archivan |

## Un detalle de contexto

La app se mira en un televisor, a unos tres metros, con mando a distancia. El
icono aparece chico en la fila de inicio: lo que a tamaño de pantalla parece un
detalle fino ahí desaparece. Si al vectorizar hay margen para engrosar un poco los
trazos o apretar el encuadre, mejor.
