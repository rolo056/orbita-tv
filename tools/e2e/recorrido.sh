#!/bin/bash
# Recorre la app en un emulador de televisor como lo haria alguien con el mando,
# y deja una foto de cada paso.
#
# Es la unica forma de ver la app FUNCIONANDO antes de que llegue a un
# televisor de verdad: abrir un canal, pasarlo a pantalla completa, entrar a
# una pelicula, adelantarla, salir y volver a entrar para ver si sigue donde
# quedo. Contra un panel de mentira (panel_falso.py), con video de prueba que
# lleva un reloj dibujado, asi en cada foto se lee por donde iba.
#
# Solo usa las flechas, OK y Atras: lo mismo que tiene el mando.
#
# Uso: recorrido.sh <apk> <carpeta-de-medios> <carpeta-de-salida>
set -u
APK="$1"
MEDIOS="$2"
SALIDA="$3"
AQUI="$(cd "$(dirname "$0")" && pwd)"
# La variante de prueba es otra app (ver app/build.gradle.kts): otro paquete,
# la misma pantalla de entrada.
PAQUETE=com.orbita.tv.prueba
ENTRADA=com.orbita.tv.MainActivity
CUENTA="http://10.0.2.2:8080/get.php?username=prueba&password=prueba"
# La apariencia publicada, servida por el mismo panel de mentira: asi las fotos
# salen con los colores que ve el televisor de casa y no con los de fabrica.
APARIENCIA="http://10.0.2.2:8080/tema.json"
INFORME="$SALIDA/recorrido.txt"
N=0

mkdir -p "$SALIDA"
: > "$INFORME"

anotar() { echo "$*" | tee -a "$INFORME"; }
paso() { anotar ""; anotar "== $* =="; }

foto() {
  N=$((N + 1))
  local nombre
  nombre=$(printf "e2e-%02d-%s.png" "$N" "$1")
  adb exec-out screencap -p > "$SALIDA/$nombre" 2> /dev/null
  anotar "  foto $nombre ($(stat -c %s "$SALIDA/$nombre" 2> /dev/null || echo 0) bytes)"
}

# El arbol de la pantalla. Falla cuando algo en pantalla cambia sin parar (la
# barra de una pelicula en marcha, por ejemplo): en ese caso se sigue sin el.
volcar() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml "$SALIDA/ui.xml" > /dev/null 2>&1 || return 1
}

foco() {
  if volcar; then
    anotar "  foco: $(python3 "$AQUI/pantalla.py" "$SALIDA/ui.xml" foco)"
  else
    anotar "  foco: (la pantalla no se dejo leer)"
  fi
}

textos() {
  if volcar; then
    anotar "  a la vista: $(python3 "$AQUI/pantalla.py" "$SALIDA/ui.xml" textos)"
  fi
}

# teclas <segundos-de-espera> <tecla> [tecla...]   Todas juntas, como dedos rapidos.
teclas() {
  local espera="$1"
  shift
  adb shell input keyevent "$@"
  sleep "$espera"
}

abrir() {
  # $1: seccion en la que arrancar (vacio: canales)
  adb shell am force-stop "$PAQUETE"
  sleep 1
  if [ -n "${1:-}" ]; then
    adb shell "am start -n $PAQUETE/$ENTRADA --es cuenta '$CUENTA' --es apariencia '$APARIENCIA' --es seccion '$1'" > /dev/null
  else
    adb shell "am start -n $PAQUETE/$ENTRADA --es cuenta '$CUENTA' --es apariencia '$APARIENCIA'" > /dev/null
  fi
}

OK=KEYCODE_DPAD_CENTER
ARRIBA=KEYCODE_DPAD_UP
ABAJO=KEYCODE_DPAD_DOWN
IZQ=KEYCODE_DPAD_LEFT
DER=KEYCODE_DPAD_RIGHT
ATRAS=KEYCODE_BACK

# ---------------------------------------------------------------- preparar

cp "$AQUI/../../tema.json" "$MEDIOS/tema.json"
python3 "$AQUI/panel_falso.py" "$MEDIOS" 8080 > "$SALIDA/panel.txt" 2>&1 &
PANEL=$!
sleep 2

anotar "aparato: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), $(adb shell wm size | tr -d '\r')"
if adb shell ping -c 1 -W 4 raw.githubusercontent.com > /dev/null 2>&1; then
  anotar "internet desde el emulador: si"
else
  anotar "internet desde el emulador: NO (la apariencia remota no se va a poder bajar: se ve la de fabrica)"
fi
adb install -r "$APK" > "$SALIDA/instalacion.txt" 2>&1
anotar "instalacion: $(tail -1 "$SALIDA/instalacion.txt")"
adb logcat -c

# ------------------------------------------------------------ canales en vivo

paso "1. Abrir la app: la pantalla de inicio, con TV en vivo enfocado"
abrir ""
sleep 18
foto menu
foco
textos

paso "1b. OK en TV en vivo: la lista de canales, con el primero enfocado"
teclas 4 $OK
foto inicio
foco

paso "2. OK: el canal se ve en la ventana"
teclas 12 $OK
foto canal-en-ventana
foco

paso "3. OK otra vez: pantalla completa"
teclas 5 $OK
foto pantalla-completa

paso "4. Abajo: canal siguiente, sin salir de la pantalla completa"
teclas 10 $ABAJO
foto canal-siguiente

paso "4b. Derecha: el detalle tecnico, con el formato de la imagen y el decodificador"
teclas 3 $DER
foto detalle-tecnico
textos
teclas 2 $DER

paso "5. Atras: de vuelta en la lista, con el foco en el canal que se miraba"
teclas 3 $ATRAS
foto de-vuelta
foco

paso "6. Abajo, abajo, izquierda: a las categorias; OK elige una y el foco va a su primer canal"
teclas 1 $ABAJO
teclas 1 $ABAJO
foco
teclas 2 $IZQ
foco
teclas 3 $OK
foto categoria
foco
textos

paso "7. Atras: vuelve al inicio. Derecha y OK: Peliculas"
teclas 3 $ATRAS
foco
foto de-vuelta-al-menu
teclas 2 $DER
foco
teclas 8 $OK
foto tras-ok-en-peliculas
textos

paso "7b. Atras: el foco vuelve a Peliculas. Abajo y OK: Mi cuenta"
teclas 3 $ATRAS
foco
teclas 1 $ABAJO
foco
teclas 4 $OK
foto mi-cuenta
textos
teclas 3 $ATRAS

# ------------------------------------------------------------------ peliculas

paso "8. Peliculas: el catalogo, con la primera portada enfocada"
abrir peliculas
sleep 18
foto peliculas
foco
textos

paso "9. OK: la ficha, con el foco en Reproducir"
teclas 4 $OK
foto ficha-pelicula
foco

paso "10. OK: empieza la pelicula"
teclas 12 $OK
foto pelicula-en-marcha

paso "11. Derecha ocho veces: adelanta 80 segundos de un solo salto"
teclas 2 $DER $DER $DER $DER $DER $DER $DER $DER
foto salto-pedido
sleep 8
foto tras-el-salto

paso "12. OK: pausa. La barra queda a la vista"
teclas 3 $OK
foto en-pausa
teclas 2 $OK

paso "13. Atras: la ficha ahora ofrece seguir desde donde quedo"
teclas 4 $ATRAS
foto ficha-con-seguir
foco

paso "14. OK en Seguir: retoma en ese punto, no desde el principio"
teclas 12 $OK
foto retomada

paso "15. Atras dos veces: el catalogo, con el foco en la misma portada"
teclas 3 $ATRAS
teclas 3 $ATRAS
foto catalogo-de-vuelta
foco

paso "15b. Izquierda a las categorias, arriba hasta Seguir viendo y OK: el foco va a su primera portada"
# Arriba del todo esta "Seguir viendo", que ya tiene la pelicula de los pasos
# 10 a 14: una categoria con algo adentro (la de mas abajo es la vacia).
teclas 2 $IZQ
teclas 2 $ARRIBA $ARRIBA $ARRIBA $ARRIBA $ARRIBA
foco
teclas 8 $OK
foto otra-categoria
foco

# --------------------------------------------------------------------- series

paso "16. Series: el catalogo"
abrir series
sleep 18
foto series
foco

paso "17. OK: la ficha de la serie, con el primer episodio enfocado"
teclas 6 $OK
foto ficha-serie
foco
textos

paso "18. OK: empieza el episodio"
teclas 12 $OK
foto episodio-en-marcha

paso "19. Derecha hasta el final: termina y pasa solo al episodio siguiente"
teclas 1 $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER
sleep 9
foto cuenta-atras
sleep 14
teclas 2 $ARRIBA
foto episodio-siguiente

paso "20. Atras: la ficha marca lo visto"
teclas 4 $ATRAS
foto ficha-serie-despues
textos

# ------------------------------------------------------ cortes de conexion
#
# Para lo que existe esta app. El corte tipico de un enlace satelital no da
# error: la conexion queda abierta y dejan de llegar datos. Se imita tal cual,
# tirando en silencio los paquetes hacia y desde el panel.

cortar() {
  sudo iptables -I INPUT -p tcp --dport 8080 -j DROP
  sudo iptables -I OUTPUT -p tcp --sport 8080 -j DROP
  anotar "  [conexion cortada]"
}

reponer() {
  sudo iptables -D INPUT -p tcp --dport 8080 -j DROP
  sudo iptables -D OUTPUT -p tcp --sport 8080 -j DROP
  anotar "  [conexion repuesta]"
}

if sudo -n true 2> /dev/null && command -v iptables > /dev/null; then

  paso "21. Pelicula con corte: se adelanta lejos justo cuando no hay conexion"
  abrir peliculas
  sleep 18
  teclas 4 $OK
  foco
  teclas 12 $OK
  foto antes-del-corte
  cortar
  teclas 20 $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER $DER
  foto durante-el-corte
  reponer
  sleep 40
  teclas 2 $ARRIBA
  foto despues-del-corte

  paso "22. Canal con corte: 40 segundos sin datos, a pantalla completa"
  abrir ""
  sleep 18
  teclas 4 $OK
  teclas 10 $OK
  teclas 8 $OK
  foto canal-antes-del-corte
  cortar
  sleep 40
  foto canal-durante-el-corte
  reponer
  sleep 40
  teclas 2 $OK
  foto canal-despues-del-corte

  paso "23. Diagnostico: el registro de fallas anoto el corte, con la foto de la red en ese momento"
  teclas 3 $ATRAS
  teclas 3 $ATRAS
  teclas 2 $DER
  teclas 2 $ABAJO
  teclas 2 $DER
  foco
  teclas 5 $OK
  foto registro-de-fallas
  textos

else
  paso "21 y 22. Cortes de conexion: no se pudieron hacer (hace falta iptables)"
fi

# ------------------------------------------------------------------- cierre

paso "Caidas de la app durante el recorrido"
# Solo las lineas de error: cada orden de adb arranca su propio proceso y deja
# lineas de "AndroidRuntime" que no son caidas de nada.
adb logcat -d 2> /dev/null | grep -E "FATAL EXCEPTION| E AndroidRuntime: " > "$SALIDA/caidas.txt"
if [ -s "$SALIDA/caidas.txt" ]; then
  anotar "  HUBO CAIDAS:"
  head -60 "$SALIDA/caidas.txt" | tee -a "$INFORME"
else
  anotar "  ninguna"
fi
adb logcat -d 2> /dev/null | grep -iE "orbita|ExoPlayer|MediaCodec" | tail -400 > "$SALIDA/registro-del-aparato.txt"

paso "Lo que la app le pidio al panel"
anotar "  pedidos en total: $(grep -c 'GET /' "$SALIDA/panel.txt")"
anotar "  pedidos parciales (adelantar y retomar): $(grep -c '\[bytes=' "$SALIDA/panel.txt")"
anotar "  listas de canal pedidas: $(grep -c 'm3u8' "$SALIDA/panel.txt")"

kill "$PANEL" 2> /dev/null
exit 0
