#!/bin/sh
# Crea, o reemplaza, un puente hacia un proveedor de IPTV.
#
#   crear-puente.sh <contenedor> <puerto-local> <host-real> <puerto-real> [http|https]
#
# Ejemplo:
#   crear-puente.sh puente-iptv 18766 tv.ejemplo.com 8080
#
# <puerto-local> puede llevar direccion, por ejemplo 127.0.0.1:18767, para un
# puente de prueba que no quede abierto a internet.
#
# La configuracion nueva se valida ANTES de tocar el puente que esta
# funcionando: si tiene un error, el puente anterior sigue arriba.
set -eu

if [ $# -lt 4 ]; then
    echo "uso: $0 <contenedor> <puerto-local> <host-real> <puerto-real> [http|https]"
    exit 1
fi

NOMBRE="$1"
LOCAL="$2"
RHOST="$3"
RPORT="$4"
ESQ="${5:-http}"

DIR="$(cd "$(dirname "$0")" && pwd)"
PLANTILLA="$DIR/puente.conf.plantilla"
CONF="$DIR/$NOMBRE.conf"

if [ ! -f "$PLANTILLA" ]; then
    echo "falta la plantilla: $PLANTILLA"
    exit 1
fi

# El encabezado Host no lleva el puerto cuando es el estandar del esquema.
case "$ESQ:$RPORT" in
    http:80|https:443) HOSTHDR="$RHOST" ;;
    *)                 HOSTHDR="$RHOST:$RPORT" ;;
esac

sed -e "s|@@ESQ@@|$ESQ|g" \
    -e "s|@@RHOST@@|$RHOST|g" \
    -e "s|@@RPORT@@|$RPORT|g" \
    -e "s|@@HOSTHDR@@|$HOSTHDR|g" \
    "$PLANTILLA" > "$CONF.nuevo"

if ! docker run --rm -v "$CONF.nuevo:/etc/nginx/conf.d/default.conf:ro" nginx:alpine nginx -t \
        > "$CONF.revision" 2>&1; then
    echo "la configuracion no es valida; el puente anterior no se toco:"
    cat "$CONF.revision"
    rm -f "$CONF.nuevo" "$CONF.revision"
    exit 1
fi
rm -f "$CONF.revision"
mv "$CONF.nuevo" "$CONF"

docker rm -f "$NOMBRE" > /dev/null 2>&1 || true
docker run -d --name "$NOMBRE" --restart unless-stopped \
    -p "$LOCAL:80" \
    -v "$CONF:/etc/nginx/conf.d/default.conf:ro" \
    nginx:alpine > /dev/null

sleep 1
ESTADO="$(docker inspect "$NOMBRE" --format '{{.State.Status}}')"
echo "puente $NOMBRE: $LOCAL -> $ESQ://$RHOST:$RPORT ($ESTADO)"
