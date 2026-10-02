#!/bin/sh
# Autoprueba de la plantilla del puente, sin depender de ningun proveedor real.
#
# Levanta un proveedor falso que se comporta como un panel Xtream en lo que
# importa (se anuncia con su direccion real, entrega listas con direcciones
# completas, redirige a si mismo y sirve un archivo de video grande), pone un
# puente delante y comprueba que el puente hace lo que promete. Al terminar
# borra todo, pase lo que pase.
#
# Ni el proveedor falso ni el puente de prueba quedan abiertos a internet.
set -u

DIR="$(cd "$(dirname "$0")" && pwd)"
T="$(mktemp -d)"
GW="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}')"
PF=18768   # proveedor falso, solo en la red interna de docker
PP=18767   # puente de prueba, solo en 127.0.0.1
B="http://127.0.0.1:$PP"
PESO=3145728

limpiar() {
    docker rm -f proveedor-falso puente-prueba > /dev/null 2>&1
    rm -rf "$T" "$DIR/puente-prueba.conf"
}
trap limpiar EXIT

fallas=0
ok()  { echo "  OK     $1"; }
mal() { echo "  FALLA  $1"; fallas=$((fallas + 1)); }

# ---------------------------------------------------------------- escenario
mkdir -p "$T/datos/movie/u/p"
printf '{"user_info":{"auth":1,"status":"Active"},"server_info":{"url":"%s","port":"%s","https_port":"443","server_protocol":"http"}}' \
    "$GW" "$PF" > "$T/datos/player_api.json"
{
    echo "#EXTM3U"
    i=1
    while [ $i -le 120 ]; do
        echo "#EXTINF:-1,Canal $i"
        echo "http://$GW:$PF/live/u/p/$i.ts"
        i=$((i + 1))
    done
} > "$T/datos/lista.m3u"
printf '#EXTM3U\n#EXTINF:10,\nhttp://%s:%s/hls/u/p/1/abc/1_1.ts\n' "$GW" "$PF" > "$T/datos/canal.m3u8"
dd if=/dev/urandom of="$T/datos/movie/u/p/1.mkv" bs=1024 count=3072 2> /dev/null

cat > "$T/falso.conf" <<EOF
server {
    listen 80;
    location = /player_api.php   { default_type application/json;         alias /datos/player_api.json; }
    location = /get.php          { default_type application/octet-stream; alias /datos/lista.m3u; }
    location = /live/u/p/1.m3u8  { default_type application/x-mpegurl;    alias /datos/canal.m3u8; }
    location = /redir            { return 302 http://$GW:$PF/movie/u/p/1.mkv; }
    location /movie/             { root /datos; default_type video/x-matroska; }
}
EOF
chmod -R a+rX "$T"

docker run -d --name proveedor-falso -p "$GW:$PF:80" \
    -v "$T/falso.conf:/etc/nginx/conf.d/default.conf:ro" \
    -v "$T/datos:/datos:ro" nginx:alpine > /dev/null || { echo "no se pudo levantar el proveedor falso"; exit 1; }

sh "$DIR/crear-puente.sh" puente-prueba "127.0.0.1:$PP" "$GW" "$PF" || exit 1
sleep 1
echo ""
echo "Proveedor falso en $GW:$PF, puente de prueba en 127.0.0.1:$PP"
echo ""

# ------------------------------------------------------------------ pruebas
R="$(curl -s --max-time 10 "$B/player_api.php?username=u&password=p")"
case "$R" in
    *'"url":"127.0.0.1"'*'"port":"'"$PP"'"'*) ok "la ficha de la cuenta anuncia al puente, no al proveedor" ;;
    *) mal "la ficha de la cuenta sigue anunciando al proveedor: $R" ;;
esac

R="$(curl -s --max-time 10 "$B/get.php?username=u&password=p&type=m3u_plus")"
N="$(printf '%s\n' "$R" | grep -c "^http://127.0.0.1:$PP/live/u/p/")"
V="$(printf '%s\n' "$R" | grep -c "$GW:$PF")"
if [ "$N" = "120" ] && [ "$V" = "0" ]; then
    ok "la lista M3U (tipo generico de descarga): 120 de 120 direcciones apuntan al puente"
else
    mal "la lista M3U: $N direcciones al puente y $V al proveedor (se esperaban 120 y 0)"
fi

H="$(curl -s --max-time 10 -H 'Accept-Encoding: gzip' -D - -o "$T/lista.gz" "$B/get.php?username=u&password=p" | tr -d '\r')"
if printf '%s\n' "$H" | grep -qi '^content-encoding: gzip' && gzip -dc "$T/lista.gz" 2> /dev/null | grep -q "^http://127.0.0.1:$PP/live/u/p/1.ts"; then
    ok "comprimida hacia el aparato, y aun asi reescrita"
else
    mal "la lista comprimida no llego reescrita"
fi

R="$(curl -s --max-time 10 "$B/live/u/p/1.m3u8")"
case "$R" in
    *"http://127.0.0.1:$PP/hls/u/p/1/abc/1_1.ts"*) ok "la lista de segmentos HLS apunta al puente" ;;
    *) mal "la lista de segmentos HLS no se reescribio: $R" ;;
esac

H="$(curl -s --max-time 10 -o /dev/null -D - "$B/redir" | tr -d '\r')"
L="$(printf '%s\n' "$H" | grep -i '^location:' | sed 's/^[^:]*: *//')"
if [ "$L" = "http://127.0.0.1:$PP/movie/u/p/1.mkv" ]; then
    ok "una redireccion del proveedor a si mismo vuelve al puente"
else
    mal "la redireccion quedo apuntando a: $L"
fi

H="$(curl -s --max-time 20 -D - -o "$T/todo" "$B/movie/u/p/1.mkv" | tr -d '\r')"
CL="$(printf '%s\n' "$H" | grep -i '^content-length:' | sed 's/^[^:]*: *//')"
M1="$(md5sum < "$T/datos/movie/u/p/1.mkv" | cut -d' ' -f1)"
M2="$(md5sum < "$T/todo" | cut -d' ' -f1)"
if [ "$CL" = "$PESO" ] && [ "$M1" = "$M2" ]; then
    ok "una pelicula completa pasa intacta y conserva su tamano ($CL bytes)"
else
    mal "la pelicula completa: tamano anunciado '$CL', identica: $([ "$M1" = "$M2" ] && echo si || echo no)"
fi

H="$(curl -s --max-time 10 -r 1000000-1999999 -D - -o "$T/parte" "$B/movie/u/p/1.mkv" | tr -d '\r')"
E="$(printf '%s\n' "$H" | head -1 | awk '{print $2}')"
CR="$(printf '%s\n' "$H" | grep -i '^content-range:' | sed 's/^[^:]*: *//')"
M1="$(dd if="$T/datos/movie/u/p/1.mkv" bs=1000000 skip=1 count=1 2> /dev/null | md5sum | cut -d' ' -f1)"
M2="$(md5sum < "$T/parte" | cut -d' ' -f1)"
if [ "$E" = "206" ] && [ "$CR" = "bytes 1000000-1999999/$PESO" ] && [ "$M1" = "$M2" ]; then
    ok "adelantar una pelicula: pedido parcial respetado ($CR)"
else
    mal "adelantar: estado $E, rango '$CR', identico: $([ "$M1" = "$M2" ] && echo si || echo no)"
fi

REG="$(docker logs puente-prueba 2>&1)"
if printf '%s\n' "$REG" | grep -q '/movie/\*/\*/1.mkv' \
    && ! printf '%s\n' "$REG" | grep -q '/u/p/' \
    && ! printf '%s\n' "$REG" | grep -q 'username='; then
    ok "el registro no guarda usuario ni clave"
else
    mal "el registro contiene credenciales"
fi

echo ""
if [ "$fallas" = "0" ]; then
    echo "Todas las pruebas pasaron."
else
    echo "$fallas prueba(s) fallaron."
fi
exit "$fallas"
