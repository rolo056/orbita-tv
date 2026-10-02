#!/usr/bin/env python3
"""Un panel Xtream de mentira, para probar la app sin una cuenta de verdad.

Contesta lo mismo que un panel: la cuenta, las categorias, los canales, las
peliculas y las series, y entrega video de verdad:

  - los canales en vivo como HLS que nunca termina (una lista que avanza con el
    reloj, armada con segmentos que se repiten), que es como llega un canal;
  - las peliculas y los episodios como un archivo que acepta pedidos parciales,
    que es lo que hace falta para adelantar, retroceder y seguir donde quedo.

Uso:  panel_falso.py <carpeta-de-medios> [puerto]

La carpeta tiene que traer pelicula.mp4 y hls/seg_000.ts ... (ver
preparar-medios.sh). Todo lo que se pide queda anotado en la salida.
"""
import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

MEDIOS = sys.argv[1] if len(sys.argv) > 1 else "medios"
PUERTO = int(sys.argv[2]) if len(sys.argv) > 2 else 8080
USUARIO = "prueba"
CLAVE = "prueba"
SEGUNDOS_POR_SEGMENTO = 2
# Treinta segundos de lista, como la de un panel de verdad.
SEGMENTOS_A_LA_VISTA = 15

CATEGORIAS_VIVO = [
    {"category_id": "1", "category_name": "NACIONALES", "parent_id": 0},
    {"category_id": "2", "category_name": "DEPORTES", "parent_id": 0},
    {"category_id": "3", "category_name": "NOTICIAS", "parent_id": 0},
]
NOMBRES_DE_CANAL = [
    "Canal Uno HD", "Tele Centro", "Deportes Total", "Liga Total 2", "Noticias 24",
    "Mundo Infantil", "Cine Clasico", "Musica Latina", "Documental Planeta",
]
CANALES = [
    {
        "num": i + 1,
        "name": n,
        "stream_type": "live",
        # A proposito: unos ids como numero y otros como texto, como en los paneles.
        "stream_id": (100 + i) if i % 2 == 0 else str(100 + i),
        "stream_icon": "",
        "epg_channel_id": None,
        "category_id": str(i % 3 + 1),
    }
    for i, n in enumerate(NOMBRES_DE_CANAL)
]

CATEGORIAS_VOD = [
    {"category_id": "20", "category_name": "ESTRENOS", "parent_id": 0},
    {"category_id": "21", "category_name": "COMEDIA", "parent_id": 0},
    {"category_id": "22", "category_name": "VACIA", "parent_id": 0},
]
TITULOS = [
    "La casa del acantilado", "Ruta al sur", "Tres veranos", "El ultimo tren",
    "Noche de estreno", "Cuenta regresiva", "Mar adentro", "La visita",
    "Vecinos", "El mapa", "Ciudad dormida", "Domingo", "Fuera de hora", "Tierra firme",
]
PELICULAS = [
    {
        "num": i + 1,
        "name": t,
        "stream_type": "movie",
        "stream_id": 5000 + i,
        "stream_icon": "",
        "rating": str(6 + i % 3) + "." + str(i % 10),
        "rating_5based": 3.5,
        "added": "1690000000",
        "category_id": "20" if i < 10 else "21",
        "container_extension": "mp4",
    }
    for i, t in enumerate(TITULOS)
]

CATEGORIAS_SERIES = [
    {"category_id": "40", "category_name": "COMEDIAS", "parent_id": 0},
    {"category_id": "41", "category_name": "DRAMAS", "parent_id": 0},
]
SERIES = [
    {
        "num": i + 1,
        "name": n,
        "series_id": 70 + i,
        "cover": "",
        "plot": "Una serie de prueba con varias temporadas.",
        "cast": "Ana Uno, Luis Dos",
        "genre": "Comedia" if i < 3 else "Drama",
        "releaseDate": "2015-04-02",
        "rating": "8",
        "backdrop_path": [],
        "category_id": "40" if i < 3 else "41",
    }
    for i, n in enumerate(["Los vecinos", "La oficina de al lado", "Domingos", "El puerto"])
]


def ficha_de_serie(serie_id):
    episodios = {}
    for temporada, cuantos in ((1, 5), (2, 3)):
        episodios[str(temporada)] = [
            {
                "id": str(serie_id * 100 + temporada * 10 + n),
                "episode_num": n,
                "title": "Serie de prueba - S%02dE%02d - Episodio %d" % (temporada, n, n),
                "container_extension": "mp4",
                # A proposito: "info" vacio llega como lista, no como objeto.
                "info": {"duration_secs": 60, "duration": "00:01:00"} if n % 2 else [],
                "season": temporada,
            }
            for n in range(1, cuantos + 1)
        ]
    base = next((s for s in SERIES if s["series_id"] == serie_id), None)
    return {"seasons": [], "info": base or [], "episodes": episodios}


def ficha_de_pelicula(vod_id):
    base = next((p for p in PELICULAS if p["stream_id"] == vod_id), None)
    if base is None:
        return {"info": [], "movie_data": []}
    return {
        "info": {
            "plot": "Una pelicula de prueba: barras de colores y un reloj que avanza.",
            "cast": "Ana Uno, Luis Dos",
            "director": "Marta Cinco",
            "genre": "Prueba",
            "releasedate": "2019-03-08",
            "duration_secs": 180,
            "duration": "00:03:00",
            "rating": base["rating"],
            "backdrop_path": [],
        },
        "movie_data": {
            "stream_id": vod_id,
            "name": base["name"],
            "container_extension": "mp4",
        },
    }


def cuantos_segmentos():
    carpeta = os.path.join(MEDIOS, "hls")
    return len([f for f in os.listdir(carpeta) if f.startswith("seg_") and f.endswith(".ts")])


def lista_en_vivo():
    """Una lista HLS que avanza con el reloj y nunca termina, como un canal."""
    total = cuantos_segmentos()
    primero = int(time.time() // SEGUNDOS_POR_SEGMENTO)
    lineas = [
        "#EXTM3U",
        "#EXT-X-VERSION:3",
        "#EXT-X-TARGETDURATION:%d" % SEGUNDOS_POR_SEGMENTO,
        "#EXT-X-MEDIA-SEQUENCE:%d" % primero,
        # Cada vuelta del video de prueba es un corte en los tiempos.
        "#EXT-X-DISCONTINUITY-SEQUENCE:%d" % (primero // total),
    ]
    for k in range(primero, primero + SEGMENTOS_A_LA_VISTA):
        if k % total == 0 and k != primero:
            lineas.append("#EXT-X-DISCONTINUITY")
        lineas.append("#EXTINF:%d.000," % SEGUNDOS_POR_SEGMENTO)
        lineas.append("seg_%03d.ts" % (k % total))
    return "\n".join(lineas) + "\n"


class Panel(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, formato, *args):
        rango = self.headers.get("Range") if self.headers else None
        sys.stdout.write("%s %s%s\n" % (
            time.strftime("%H:%M:%S"),
            formato % args,
            (" [" + rango + "]") if rango else "",
        ))
        sys.stdout.flush()

    def json(self, dato, codigo=200):
        cuerpo = json.dumps(dato).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def texto(self, cuerpo, tipo, codigo=200):
        datos = cuerpo.encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def archivo(self, ruta, tipo):
        if not os.path.isfile(ruta):
            self.texto("no esta", "text/plain", 404)
            return
        tam = os.path.getsize(ruta)
        ini, fin = 0, tam - 1
        rango = self.headers.get("Range")
        m = re.match(r"bytes=(\d*)-(\d*)", rango or "")
        if m and (m.group(1) or m.group(2)):
            if m.group(1):
                ini = int(m.group(1))
                if m.group(2):
                    fin = min(int(m.group(2)), tam - 1)
            else:
                # bytes=-N: los ultimos N.
                ini = max(tam - int(m.group(2)), 0)
            if ini > fin or ini >= tam:
                self.send_response(416)
                self.send_header("Content-Range", "bytes */%d" % tam)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            self.send_response(206)
            self.send_header("Content-Range", "bytes %d-%d/%d" % (ini, fin, tam))
        else:
            self.send_response(200)
        self.send_header("Content-Type", tipo)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(fin - ini + 1))
        self.end_headers()
        falta = fin - ini + 1
        try:
            with open(ruta, "rb") as f:
                f.seek(ini)
                while falta > 0:
                    trozo = f.read(min(65536, falta))
                    if not trozo:
                        break
                    self.wfile.write(trozo)
                    falta -= len(trozo)
        except (BrokenPipeError, ConnectionResetError):
            # El reproductor corto el pedido: es lo normal al adelantar.
            pass

    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        partes = [p for p in url.path.split("/") if p]

        if url.path == "/player_api.php":
            if q.get("username") != USUARIO or q.get("password") != CLAVE:
                self.json({"user_info": {"auth": 0}})
                return
            accion = q.get("action")
            cat = q.get("category_id")
            if accion is None:
                self.json({
                    "user_info": {
                        "username": USUARIO, "password": CLAVE, "message": "", "auth": 1,
                        "status": "Active", "exp_date": "1893456000", "is_trial": "0",
                        "active_cons": "0", "created_at": "1690000000",
                        "max_connections": "1", "allowed_output_formats": ["m3u8", "ts"],
                    },
                    "server_info": {
                        "url": "10.0.2.2", "port": str(PUERTO), "https_port": "443",
                        "server_protocol": "http", "rtmp_port": "0", "timezone": "UTC",
                        "timestamp_now": int(time.time()),
                        "time_now": time.strftime("%Y-%m-%d %H:%M:%S"),
                    },
                })
            elif accion == "get_live_categories":
                self.json(CATEGORIAS_VIVO)
            elif accion == "get_live_streams":
                self.json([c for c in CANALES if cat is None or c["category_id"] == cat])
            elif accion == "get_vod_categories":
                self.json(CATEGORIAS_VOD)
            elif accion == "get_vod_streams":
                self.json([p for p in PELICULAS if cat is None or p["category_id"] == cat])
            elif accion == "get_vod_info":
                self.json(ficha_de_pelicula(int(q.get("vod_id", "0") or 0)))
            elif accion == "get_series_categories":
                self.json(CATEGORIAS_SERIES)
            elif accion == "get_series":
                self.json([s for s in SERIES if cat is None or s["category_id"] == cat])
            elif accion == "get_series_info":
                self.json(ficha_de_serie(int(q.get("series_id", "0") or 0)))
            else:
                self.json([])
            return

        # /live/usuario/clave/100.m3u8  y sus segmentos
        if len(partes) == 4 and partes[0] == "live":
            if partes[1] != USUARIO or partes[2] != CLAVE:
                self.texto("", "text/plain", 403)
                return
            nombre = partes[3]
            if nombre.endswith(".m3u8"):
                self.texto(lista_en_vivo(), "application/vnd.apple.mpegurl")
            elif re.match(r"^seg_\d{3}\.ts$", nombre):
                self.archivo(os.path.join(MEDIOS, "hls", nombre), "video/mp2t")
            else:
                # El canal pedido como .ts directo: este panel solo da HLS.
                self.texto("", "text/plain", 404)
            return

        # /movie/usuario/clave/5001.mp4  y  /series/usuario/clave/7011.mp4
        if len(partes) == 4 and partes[0] in ("movie", "series"):
            if partes[1] != USUARIO or partes[2] != CLAVE:
                self.texto("", "text/plain", 403)
                return
            self.archivo(os.path.join(MEDIOS, "pelicula.mp4"), "video/mp4")
            return

        self.texto("no esta", "text/plain", 404)


if __name__ == "__main__":
    print("panel de prueba en el puerto %d, medios en %s (%d segmentos)" % (
        PUERTO, MEDIOS, cuantos_segmentos()))
    sys.stdout.flush()
    ThreadingHTTPServer(("0.0.0.0", PUERTO), Panel).serve_forever()
