# Versiones de ALEX TV y cómo volver atrás

Este archivo dice qué versión trae qué, dónde está guardada cada una y cómo
volver a una anterior si la nueva falla. **Volver atrás nunca obliga a
desinstalar** la app del televisor ni a cargar de nuevo la cuenta.

## Las versiones

| Versión | Qué trae | Código guardado | Archivo de instalación |
|---|---|---|---|
| **1.0.28** | Diseño nuevo: pantalla de inicio con TV en vivo, Películas y Series; botones de cristal con esquinas redondeadas; Mi cuenta | rama `main` (fusión del 3/10/2026) | publicación `v1.0.28` |
| **1.0.27** | Películas y series, con el diseño anterior | etiqueta `respaldo-1.0.27` (commit `a775470`) | publicación `v1.0.27` |
| **1.0.26** | Solo canales en vivo | etiqueta `respaldo-1.0.26` (commit `5d5e836`) | publicación `v1.0.26` |

La versión que instala el televisor (el código de Downloader y el botón
"Actualizar" de la app) siempre es la de la publicación `ultima`.

Para saber qué versión tiene un aparato: **Ajustes**, arriba de todo dice
"Versión 1.0.x".

## Por qué volver atrás no es instalar el archivo viejo

Android no deja instalar una versión **más vieja** encima de una más nueva. La
única salida sería desinstalar, y desinstalar borra la cuenta, los favoritos y
lo visto.

Por eso se hace al revés: se vuelve a publicar el código de la versión vieja
con un número **más nuevo**. La app lo ofrece como cualquier actualización, se
instala encima y conserva todo lo guardado. Los datos son compatibles en las
dos direcciones: cada versión ignora lo que no conoce.

## Cómo volver a la 1.0.27 (si la 1.0.28 falla)

Desde la carpeta del proyecto:

```
git checkout main
git pull
git rm -r -q app
git checkout respaldo-1.0.27 -- app tema.json
git commit -m "Volver a la 1.0.27"
git push origin main
```

- `git rm` y `git checkout` dejan la carpeta `app` y la apariencia (`tema.json`)
  **exactamente** como estaban en la 1.0.27, incluido quitar los archivos que
  esa versión no tenía. Sin el `git rm`, quedarían archivos del diseño nuevo que
  no compilan con el código viejo.
- Todo lo demás (la firma, la compilación, las herramientas) no cambió entre las
  dos versiones, así que el resultado es la 1.0.27 tal cual.
- GitHub la compila en unos cuatro minutos con el número siguiente (por ejemplo
  1.0.29) y la publica en `ultima`.
- En el televisor aparece "Actualizar a la 1.0.29". OK, instalar, y listo.

El diseño nuevo no se pierde: sigue en la rama `desarrollo`, para corregirlo y
volver a publicarlo.

Este procedimiento se ensayó antes de publicar la 1.0.28: después de aplicarlo,
`app` y `tema.json` quedaron idénticos a la 1.0.27, sin restos del diseño nuevo.

## Cómo volver a la 1.0.26 (solo canales)

Igual, con la otra etiqueta:

```
git checkout main
git pull
git rm -r -q app
git checkout respaldo-1.0.26 -- app tema.json
git commit -m "Volver a la 1.0.26"
git push origin main
```

## Si se cambia solo la apariencia

`tema.json` se lee al abrir la app y no necesita versión nueva: un cambio ahí
llega a todos los aparatos en unos minutos, sin instalar nada. Las versiones
viejas ignoran las claves que no conocen (por ejemplo `resplandor2`).

## Cómo se comprueba una versión antes de publicarla

Todo se trabaja en la rama `desarrollo`, que no publica nada para el
televisor. Cada cambio ahí pasa por dos pruebas automáticas:

- **Capturas** (`.github/workflows/capturas.yml`): dibuja cada pantalla y corre
  las pruebas de lectura del catálogo y de lo guardado.
- **Emulador** (`.github/workflows/emulador.yml`): instala la app en un
  televisor Android 6 simulado y la recorre solo con el mando, contra un panel
  de mentira con video de prueba: inicio, canales, películas, series, Mi cuenta
  y cortes de conexión. Deja una foto de cada paso en la publicación `emulador`.

Solo cuando las dos salen bien, `desarrollo` se fusiona en `main`, y `main`
compila y publica la versión nueva.

## Firma

Todas las versiones desde la 1.0.23 están firmadas con la misma clave
(`clave-firma.jks`, en la raíz del repositorio). Es lo que permite instalar una
encima de otra. **No borrar ni cambiar ese archivo**: con otra clave, ninguna
actualización se instalaría sin desinstalar.
