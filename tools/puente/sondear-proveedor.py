#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sondea un proveedor de IPTV (API Xtream) y dice que necesita el puente.

Hay que correrlo DESDE EL SERVIDOR DONDE VIVE EL PUENTE: lo que importa es como
le responde el proveedor a esa IP, no a la de otra maquina.

    sondear-proveedor.py "http://servidor:puerto/get.php?username=U&password=P"
    sondear-proveedor.py servidor puerto usuario clave [http|https]

Sirve para decidir ANTES de pagar: si la cuenta de prueba trae peliculas y
series, si el video sale del mismo servidor o lo reparten otros, y si se puede
adelantar una pelicula. No imprime usuario ni clave.
"""

import datetime
import http.client
import json
import ssl
import sys
import time
from urllib.parse import parse_qs, quote, urljoin, urlsplit

UA = "VLC/3.0.20 LibVLC/3.0.20"
TOPE_JSON = 120 * 1024 * 1024
REDIRECCIONES = (301, 302, 303, 307, 308)


def pedir(url, cabeceras=None, leer=0, espera=25):
    """Una sola peticion, sin seguir redirecciones.

    leer: bytes del cuerpo a leer; None lee todo (con tope); 0 no lee nada.
    Devuelve (estado, cabeceras, cuerpo, segundos).
    """
    u = urlsplit(url)
    seguro = u.scheme == "https"
    puerto = u.port or (443 if seguro else 80)
    if seguro:
        con = http.client.HTTPSConnection(
            u.hostname, puerto, timeout=espera, context=ssl._create_unverified_context()
        )
    else:
        con = http.client.HTTPConnection(u.hostname, puerto, timeout=espera)
    ruta = u.path or "/"
    if u.query:
        ruta += "?" + u.query
    h = {"User-Agent": UA, "Accept": "*/*"}
    if cabeceras:
        h.update(cabeceras)
    t = time.time()
    try:
        con.request("GET", ruta, headers=h)
        r = con.getresponse()
        cab = {k.lower(): v for k, v in r.getheaders()}
        cuerpo = b""
        if leer is None:
            trozos = []
            total = 0
            while total < TOPE_JSON:
                parte = r.read(256 * 1024)
                if not parte:
                    break
                trozos.append(parte)
                total += len(parte)
            cuerpo = b"".join(trozos)
        elif leer:
            cuerpo = r.read(leer)
        return r.status, cab, cuerpo, time.time() - t
    finally:
        con.close()


def cadena(url, cabeceras=None, leer=0, maximo=6):
    """Sigue las redirecciones a mano para ver por donde pasa cada pedido."""
    pasos = []
    for _ in range(maximo):
        estado, cab, cuerpo, seg = pedir(url, cabeceras=cabeceras, leer=leer)
        pasos.append({"url": url, "estado": estado, "cab": cab, "cuerpo": cuerpo, "seg": seg})
        if estado in REDIRECCIONES and cab.get("location"):
            url = urljoin(url, cab["location"])
            continue
        break
    return pasos


def anfitrion(url):
    u = urlsplit(url)
    puerto = u.port or (443 if u.scheme == "https" else 80)
    return "%s://%s:%s" % (u.scheme, u.hostname, puerto)


def mb(n):
    return "%.1f MB" % (n / 1048576.0)


class Sonda:
    def __init__(self, esquema, host, puerto, usuario, clave):
        self.base = "%s://%s:%s" % (esquema, host, puerto)
        self.usuario = usuario
        self.clave = clave
        self.propio = anfitrion(self.base)
        self.otros = {}  # anfitrion ajeno -> donde aparecio
        self.avisos = []

    def limpio(self, texto):
        """Quita usuario y clave de cualquier cosa que se vaya a imprimir."""
        t = str(texto)
        for secreto in (self.usuario, self.clave, quote(self.usuario), quote(self.clave)):
            if secreto:
                t = t.replace("/" + secreto + "/", "/*/").replace("=" + secreto, "=*")
        return t

    def anotar(self, url, donde):
        a = anfitrion(url)
        if a != self.propio:
            self.otros.setdefault(a, set()).add(donde)

    def api(self, accion=None, extra=""):
        url = "%s/player_api.php?username=%s&password=%s" % (
            self.base, quote(self.usuario), quote(self.clave))
        if accion:
            url += "&action=" + accion + extra
        estado, cab, cuerpo, seg = pedir(url, leer=None, espera=120)
        if estado != 200:
            raise RuntimeError("el servidor respondio %s" % estado)
        return json.loads(cuerpo.decode("utf-8", "replace")), len(cuerpo), seg

    # ------------------------------------------------------------------

    def cuenta(self):
        print("1 - Cuenta")
        datos, _, seg = self.api()
        u = datos.get("user_info") or {}
        s = datos.get("server_info") or {}
        vence = u.get("exp_date")
        if vence and str(vence).isdigit():
            vence = datetime.datetime.fromtimestamp(int(vence)).strftime("%d/%m/%Y %H:%M")
        print("    autenticacion: %s | estado: %s | vence: %s" % (
            "correcta" if str(u.get("auth")) == "1" else "RECHAZADA", u.get("status"), vence or "sin fecha"))
        print("    conexiones: %s en uso de %s permitidas | respuesta en %.2f s" % (
            u.get("active_cons"), u.get("max_connections"), seg))
        print("    formatos permitidos: %s" % (u.get("allowed_output_formats"),))
        anunciado = "%s:%s" % (s.get("url"), s.get("port"))
        print("    el proveedor se anuncia como: %s (%s)" % (anunciado, s.get("server_protocol")))
        if s.get("url") and anunciado not in self.base:
            self.avisos.append(
                "Se anuncia con una direccion distinta a la que usamos (%s): el puente debe "
                "reescribir tambien esa." % anunciado)
        self.anunciado = (s.get("url"), str(s.get("port")))
        return str(u.get("auth")) == "1" and str(u.get("status", "")).lower() == "active"

    def catalogo(self):
        print("2 - Catalogo")
        self.vivo, self.pelis, self.series = [], [], []
        for nombre, accion, destino in (
            ("canales en vivo", "get_live_streams", "vivo"),
            ("peliculas", "get_vod_streams", "pelis"),
            ("series", "get_series", "series"),
        ):
            try:
                datos, peso, seg = self.api(accion)
                lista = datos if isinstance(datos, list) else []
                setattr(self, destino, lista)
                print("    %-16s %6d   (%s en %.1f s)" % (nombre, len(lista), mb(peso), seg))
            except Exception as e:
                print("    %-16s no se pudo leer: %s" % (nombre, e))

    def _informe_video(self, etiqueta, url, donde):
        """Pide el principio de un archivo de video y cuenta por donde paso."""
        pasos = cadena(url, cabeceras={"Range": "bytes=0-65535"}, leer=65536)
        for p in pasos:
            self.anotar(p["url"], donde)
        ultimo = pasos[-1]
        cab = ultimo["cab"]
        saltos = " -> ".join(anfitrion(p["url"]) for p in pasos)
        print("    %s" % etiqueta)
        print("      camino: %s" % saltos)
        print("      estado final: %s | tipo: %s" % (ultimo["estado"], cab.get("content-type")))
        if ultimo["estado"] == 206:
            print("      se puede adelantar: si (%s)" % cab.get("content-range"))
        elif ultimo["estado"] == 200:
            print("      se puede adelantar: DUDOSO, ignoro el pedido parcial (acepta rangos: %s)"
                  % cab.get("accept-ranges"))
            self.avisos.append("%s: el servidor no respeto el pedido parcial; adelantar puede fallar." % donde)
        else:
            self.avisos.append("%s: el video respondio %s." % (donde, ultimo["estado"]))
        return ultimo["estado"] in (200, 206)

    def canal(self):
        print("3 - Un canal en vivo")
        if not self.vivo:
            print("    no hay canales que probar")
            return
        for c in self.vivo[:4]:
            sid = c.get("stream_id")
            raiz = "%s/live/%s/%s/%s" % (self.base, quote(self.usuario), quote(self.clave), sid)
            try:
                pasos = cadena(raiz + ".m3u8", leer=4096)
                for p in pasos:
                    self.anotar(p["url"], "canal en vivo (HLS)")
                fin = pasos[-1]
                print("    canal %s por HLS: estado %s | camino: %s" % (
                    sid, fin["estado"], " -> ".join(anfitrion(p["url"]) for p in pasos)))
                if fin["estado"] == 200:
                    lineas = [l.strip() for l in fin["cuerpo"].decode("utf-8", "replace").splitlines()]
                    segs = [l for l in lineas if l and not l.startswith("#")]
                    if segs:
                        if segs[0].startswith("http"):
                            self.anotar(segs[0], "segmentos de HLS")
                            print("      segmentos: direccion completa en %s" % anfitrion(segs[0]))
                        else:
                            print("      segmentos: ruta relativa (pasan por el mismo servidor)")
                pasos = cadena(raiz + ".ts", leer=262144)
                for p in pasos:
                    self.anotar(p["url"], "canal en vivo (TS)")
                fin2 = pasos[-1]
                print("    canal %s por TS:  estado %s | %d KB en %.1f s | camino: %s" % (
                    sid, fin2["estado"], len(fin2["cuerpo"]) // 1024, fin2["seg"],
                    " -> ".join(anfitrion(p["url"]) for p in pasos)))
                if fin["estado"] == 200 or fin2["estado"] == 200:
                    return
            except Exception as e:
                print("    canal %s: %s" % (sid, self.limpio(e)))
        self.avisos.append("Ningun canal de los probados entrego video.")

    def pelicula(self):
        print("4 - Una pelicula")
        if not self.pelis:
            print("    este proveedor no trae peliculas")
            return
        for p in self.pelis[:3]:
            ext = p.get("container_extension") or "mp4"
            if p.get("direct_source"):
                self.anotar(p["direct_source"], "pelicula (fuente directa)")
            url = "%s/movie/%s/%s/%s.%s" % (
                self.base, quote(self.usuario), quote(self.clave), p.get("stream_id"), ext)
            try:
                if self._informe_video("pelicula %s (.%s)" % (p.get("stream_id"), ext), url, "pelicula"):
                    return
            except Exception as e:
                print("    pelicula %s: %s" % (p.get("stream_id"), self.limpio(e)))

    def serie(self):
        print("5 - Un episodio de serie")
        if not self.series:
            print("    este proveedor no trae series")
            return
        for s in self.series[:3]:
            try:
                info, _, _ = self.api("get_series_info", "&series_id=%s" % s.get("series_id"))
                episodios = info.get("episodes") or {}
                grupos = episodios.values() if isinstance(episodios, dict) else episodios
                primero = None
                for grupo in grupos:
                    if grupo:
                        primero = grupo[0]
                        break
                if not primero:
                    continue
                ext = primero.get("container_extension") or "mp4"
                if primero.get("direct_source"):
                    self.anotar(primero["direct_source"], "episodio (fuente directa)")
                url = "%s/series/%s/%s/%s.%s" % (
                    self.base, quote(self.usuario), quote(self.clave), primero.get("id"), ext)
                if self._informe_video("episodio %s (.%s)" % (primero.get("id"), ext), url, "episodio de serie"):
                    return
            except Exception as e:
                print("    serie %s: %s" % (s.get("series_id"), self.limpio(e)))

    def veredicto(self):
        print("")
        print("Veredicto")
        if self.otros:
            print("  El video NO sale solo del servidor principal. Tambien aparecen:")
            for a, donde in sorted(self.otros.items()):
                print("    %s   <- %s" % (a, ", ".join(sorted(donde))))
            print("  El puente simple no alcanza: hay que ampliarlo para que tambien esos pasen por el.")
        else:
            print("  Todo sale del mismo servidor: el puente simple alcanza.")
        for a in self.avisos:
            print("  AVISO: %s" % self.limpio(a))
        print("  Tiene: %d canales, %d peliculas, %d series." % (
            len(self.vivo), len(self.pelis), len(self.series)))


def argumentos(argv):
    if len(argv) == 2:
        u = urlsplit(argv[1] if "://" in argv[1] else "http://" + argv[1])
        q = parse_qs(u.query)
        esquema = u.scheme or "http"
        return (esquema, u.hostname, u.port or (443 if esquema == "https" else 80),
                (q.get("username") or [""])[0], (q.get("password") or [""])[0])
    if len(argv) >= 5:
        return (argv[5] if len(argv) > 5 else "http", argv[1], int(argv[2]), argv[3], argv[4])
    print(__doc__)
    sys.exit(1)


def main():
    esquema, host, puerto, usuario, clave = argumentos(sys.argv)
    if not host or not usuario:
        print("faltan el servidor o el usuario")
        sys.exit(1)
    sonda = Sonda(esquema, host, puerto, usuario, clave)
    print("Sondeo de %s" % sonda.propio)
    print("")
    try:
        activa = sonda.cuenta()
    except Exception as e:
        print("    no se pudo leer la cuenta: %s" % sonda.limpio(e))
        sys.exit(2)
    if not activa:
        print("")
        print("La cuenta no esta activa: no tiene sentido probar el video.")
        sys.exit(3)
    sonda.catalogo()
    for paso in (sonda.canal, sonda.pelicula, sonda.serie):
        try:
            paso()
        except Exception as e:
            print("    fallo inesperado: %s" % sonda.limpio(e))
    sonda.veredicto()


if __name__ == "__main__":
    main()
