"""Arma app/src/test/resources/campos-sin-hora.ts para EntrelazadosTest.

Un TS de prueba con la rareza de los canales entrelazados del proveedor: la
mitad de los trozos de video llegan sin hora (PES sin PTS). Se parte de un video
de prueba de ffmpeg (sin contenido de nadie) y a uno de cada dos trozos se le
borra la hora: los bits PTS_DTS pasan a 00 y los bytes de la hora se vuelven
relleno (0xFF), que el formato permite. El largo de cada paquete no cambia.

Uso: python tools/pruebas/hacer_campos_sin_hora.py   (necesita ffmpeg con libx264)
"""
import os
import subprocess
import tempfile

AQUI = os.path.dirname(os.path.abspath(__file__))
DESTINO = os.path.join(AQUI, "..", "..", "app", "src", "test", "resources", "campos-sin-hora.ts")
PID_VIDEO = 0x100  # el que usa ffmpeg para el primer flujo

with tempfile.TemporaryDirectory() as tmp:
    origen = os.path.join(tmp, "origen.ts")
    subprocess.run(
        [
            "ffmpeg", "-v", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25",
            "-t", "2",
            "-c:v", "libx264", "-profile:v", "baseline", "-bf", "0", "-g", "25",
            "-x264-params", "aud=1", "-pix_fmt", "yuv420p",
            "-f", "mpegts", origen,
        ],
        check=True,
    )
    datos = bytearray(open(origen, "rb").read())

assert len(datos) % 188 == 0, "no es un TS de paquetes de 188"
trozos = 0
sin_hora = 0
for i in range(0, len(datos), 188):
    p = datos[i:i + 188]
    assert p[0] == 0x47
    pusi = (p[1] & 0x40) != 0
    pid = ((p[1] & 0x1F) << 8) | p[2]
    if pid != PID_VIDEO or not pusi:
        continue
    inicio = 4
    if p[3] & 0x20:  # campo de adaptacion
        inicio += 1 + p[4]
    if p[inicio:inicio + 4] != b"\x00\x00\x01\xe0":
        continue
    trozos += 1
    if trozos % 2 == 1:  # el primero, el tercero...: conservan la hora
        continue
    banderas = p[inicio + 7]
    largo = p[inicio + 8]
    if banderas >> 6 == 0:
        continue
    datos[i + inicio + 7] = banderas & 0x3F
    for k in range(largo):
        datos[i + inicio + 9 + k] = 0xFF
    sin_hora += 1

os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
open(DESTINO, "wb").write(datos)
print(f"{DESTINO}: {len(datos)} bytes, {trozos} trozos de video, {sin_hora} sin hora")
