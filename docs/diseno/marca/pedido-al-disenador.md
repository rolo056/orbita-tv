# Reexportación de la marca para Android TV

Los cinco SVG que entregaste sirven como composición, pero no se pueden usar
dentro de la app todavía. Abajo está el motivo técnico y qué hace falta. Son tres
pedidos y uno solo es urgente.

## El problema

En los cinco archivos, las letras son elementos `<text>`. Dos consecuencias:

1. **El formato vectorial de Android no dibuja texto.** Solo acepta trazos
   (`<path>`). Si convertimos los archivos tal como están, se obtiene el fondo y
   los bloques de color **sin ninguna letra**.
2. **Ningún archivo declara tipografía.** Los `<text>` no traen `font-family`, así
   que cada programa que los abre dibuja "ALEX" con la fuente que tenga a mano,
   estirada al ancho fijo de `textLength`. Hoy el logotipo no se ve igual en dos
   lugares distintos.

## Lo que hace falta

### 1. Reexportar con el texto convertido a contornos — urgente

Todos los archivos, con el texto pasado a trazos. En la mayoría de los programas
la opción se llama *convertir a contornos*, *vectorizar texto* u *outline text*.
El archivo resultante no debe contener ningún `<text>`.

Si preferís mantenerlo como texto, la alternativa es mandar también el archivo de
la tipografía (`.ttf` o `.otf`) y declararla con `font-family`. Contornos es más
simple y no deja nada al azar.

### 2. Falta el banner del televisor — urgente

No vino en la entrega y Android TV lo necesita: es el mosaico que representa la
app en la fila de inicio del televisor, y es lo primero que se ve.

- **Proporción 16:9**, idealmente `viewBox` de 320 × 180 (o cualquier múltiplo).
- Es una composición **horizontal** y aparte, no el icono cuadrado recortado.
- **El nombre tiene que leerse dentro del banner**, porque el televisor no
  siempre muestra un rótulo al lado.
- Mismo criterio que el icono elegido: la variante **rojo profundo**.

### 3. Un carácter corrupto

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
| `alextv-icono-4b-rojo-profundo.svg` | Icono de la aplicación | Esperando contornos |
| *(falta)* | Banner del televisor, 16:9 | Por crear |
| `alextv-logotipo-fondo-oscuro.svg` | Barra lateral de la app | **Ya resuelto**: se dibuja con texto real dentro de la app, tomando los colores del tema. Una versión en contornos igual sirve para otros usos. |
| `alextv-logotipo-fondo-claro.svg` | Fondos claros, fuera de la app | Sin uso por ahora |
| `alextv-icono-4a-reticula.svg`, `4c-carbon.svg` | Variantes no elegidas | Se archivan |

## Un detalle de contexto

La app se mira en un televisor, a unos tres metros, con mando a distancia. El
icono aparece chico en la fila de inicio: lo que a tamaño de pantalla parece un
detalle fino ahí desaparece. Si al vectorizar hay margen para engrosar un poco los
trazos o apretar el encuadre, mejor.
