#!/bin/bash
# Arma el video de prueba: una pelicula corta y los segmentos de un canal.
#
# El video es el patron de pruebas de ffmpeg: barras de colores y un reloj que
# avanza. En una foto de la pantalla se lee por donde va la reproduccion, que es
# justo lo que hace falta para comprobar que adelantar y "seguir viendo" caen
# donde tienen que caer.
#
# Va en el perfil mas basico de H.264 y con sonido AAC: lo que decodifica
# cualquier aparato, tambien un emulador sin aceleracion.
set -e
DEST="${1:-medios}"
mkdir -p "$DEST/hls"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=640x360:rate=25" \
  -f lavfi -i "sine=frequency=440:sample_rate=44100" \
  -t 600 \
  -c:v libx264 -preset ultrafast -profile:v baseline -level 3.0 -g 50 -pix_fmt yuv420p \
  -c:a aac -b:a 96k \
  -movflags +faststart \
  "$DEST/pelicula.mp4"

# El episodio es corto: para llegar al final y ver si pasa solo al siguiente.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=640x360:rate=25" \
  -f lavfi -i "sine=frequency=550:sample_rate=44100" \
  -t 120 \
  -c:v libx264 -preset ultrafast -profile:v baseline -level 3.0 -g 50 -pix_fmt yuv420p \
  -c:a aac -b:a 96k \
  -movflags +faststart \
  "$DEST/episodio.mp4"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=640x360:rate=25" \
  -f lavfi -i "sine=frequency=330:sample_rate=44100" \
  -t 60 \
  -c:v libx264 -preset ultrafast -profile:v baseline -level 3.0 \
  -g 50 -keyint_min 50 -sc_threshold 0 -pix_fmt yuv420p \
  -c:a aac -b:a 96k \
  -f hls -hls_time 2 -hls_list_size 0 \
  -hls_segment_filename "$DEST/hls/seg_%03d.ts" \
  "$DEST/hls/base.m3u8"

echo "pelicula: $(stat -c %s "$DEST/pelicula.mp4") bytes"
echo "episodio: $(stat -c %s "$DEST/episodio.mp4") bytes"
echo "segmentos del canal: $(ls "$DEST/hls" | grep -c '^seg_')"
