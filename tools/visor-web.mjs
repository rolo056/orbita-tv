#!/usr/bin/env node
/**
 * Visor de los canales en el navegador de la laptop.
 *
 * No es la app: es un visor. El vigilante y la escalera de reintentos de la app
 * estan en Kotlin; aca hay una version simplificada de la misma idea, suficiente
 * para mirar un canal y para comprobar el panel desde la computadora cuando algo
 * falla en el televisor.
 *
 * Por que hace falta un servidor y no alcanza un archivo HTML suelto: el panel
 * envia "Access-Control-Allow-Origin: *" en el video, pero NO en player_api.php,
 * asi que el navegador bloquea la peticion de la lista de canales. Este servidor
 * hace de intermediario solo para esa parte. El video va directo del panel al
 * navegador, sin pasar por aca, para no agregar latencia.
 *
 * Uso:  node tools/visor-web.mjs [--puerto 8788]
 *
 * No toca nada del proyecto Android ni del tema. Los datos del panel quedan en
 * el navegador, en localStorage.
 */

import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const iP = args.indexOf('--puerto');
const PUERTO = iP >= 0 ? Number(args[iP + 1]) : 8788;

const HLS_JS = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.17/dist/hls.min.js';
const MPEGTS_JS = 'https://cdn.jsdelivr.net/npm/mpegts.js@1.7.3/dist/mpegts.js';

/** Los colores salen del mismo tema.json que usa el televisor. */
async function colores() {
  const porOmision = {
    fondo: '#1C1A19', tarjeta: '#262322', tarjetaFoco: '#3D3836', linea: '#3A3736',
    texto: '#F3F2F2', textoSuave: '#BAB6B6', acento: '#FF563C',
    aviso: '#E8B04A', falla: '#E86BB0',
  };
  try {
    const t = JSON.parse(await fs.readFile(path.join(RAIZ, 'tema.json'), 'utf8'));
    for (const k of Object.keys(porOmision)) {
      if (typeof t[k] === 'string' && t[k].startsWith('#')) porOmision[k] = t[k];
    }
  } catch { /* si no hay tema, se usan estos */ }
  return porOmision;
}

function ipLocal() {
  for (const grupo of Object.values(os.networkInterfaces())) {
    for (const i of grupo || []) if (i.family === 'IPv4' && !i.internal) return i.address;
  }
  return '127.0.0.1';
}

/** Intermediario para player_api.php, que es lo unico que el navegador no puede pedir. */
function proxy(destino, res) {
  const u = new URL(destino);
  if (u.protocol !== 'http:' && u.protocol !== 'https:') {
    res.writeHead(400).end('protocolo no admitido');
    return;
  }
  const req = http.request(
    destino,
    { headers: { 'User-Agent': 'VLC/3.0.20 LibVLC/3.0.20' }, timeout: 25000 },
    (r) => {
      res.writeHead(r.statusCode || 502, {
        'Content-Type': r.headers['content-type'] || 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'no-store',
      });
      r.pipe(res);
    },
  );
  req.on('timeout', () => { req.destroy(); if (!res.headersSent) res.writeHead(504).end('sin respuesta'); });
  req.on('error', (e) => { if (!res.headersSent) res.writeHead(502).end(String(e.code || e.message)); });
  req.end();
}

async function pagina() {
  const c = await colores();
  return `<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ALEX TV — visor</title>
<script src="${HLS_JS}"></script>
<script src="${MPEGTS_JS}"></script>
<style>
  :root {
    color-scheme: dark;
    --bg:${c.fondo}; --card:${c.tarjeta}; --cardf:${c.tarjetaFoco}; --line:${c.linea};
    --text:${c.texto}; --soft:${c.textoSuave}; --accent:${c.acento};
    --warn:${c.aviso}; --fail:${c.falla};
  }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--text);
         font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif; }
  header { padding:14px 20px; border-bottom:1px solid var(--line);
           display:flex; align-items:center; gap:14px; }
  .marca { font-weight:900; letter-spacing:-0.5px; font-size:20px; }
  .marca b { background:var(--accent); padding:2px 7px; margin-left:5px; }
  .sub { color:var(--soft); font-size:13px; }
  main { display:grid; grid-template-columns:300px 1fr; gap:18px; padding:18px 20px; }
  @media (max-width:900px) { main { grid-template-columns:1fr; } }
  .panel { background:var(--card); border:1px solid var(--line); padding:14px; }
  input { width:100%; background:var(--bg); color:var(--text); border:1px solid var(--line);
          padding:8px 10px; font-size:14px; margin:4px 0 10px; }
  button { background:var(--accent); color:var(--bg); border:0; padding:9px 16px;
           font-size:14px; font-weight:700; cursor:pointer; }
  button.sec { background:var(--card); color:var(--text); border:1px solid var(--line); font-weight:500; }
  .cats { display:flex; flex-wrap:wrap; gap:6px; margin-bottom:10px; }
  .cat { background:var(--card); border:1px solid var(--line); padding:5px 10px;
         font-size:13px; cursor:pointer; }
  .cat.on { border-color:var(--accent); color:var(--accent); }
  .lista { max-height:52vh; overflow:auto; border:1px solid var(--line); }
  .canal { padding:9px 12px; border-bottom:1px solid var(--line); cursor:pointer;
           display:flex; align-items:center; gap:10px; }
  .canal:hover { background:var(--cardf); }
  .canal.on { background:var(--cardf); border-left:3px solid var(--accent); }
  .canal img { width:28px; height:28px; object-fit:contain; }
  .canal .n { color:var(--soft); font-size:12px; min-width:28px; }
  video { width:100%; background:#000; aspect-ratio:16/9; }
  .estado { display:flex; gap:18px; flex-wrap:wrap; margin-top:10px;
            color:var(--soft); font-size:13px; }
  .estado b { color:var(--text); font-weight:600; }
  .aviso { color:var(--warn); }
  .malo { color:var(--fail); }
  h2 { font-size:12px; letter-spacing:1px; text-transform:uppercase; color:var(--soft);
       margin:0 0 8px; font-weight:600; }
</style></head>
<body>
<header>
  <div class="marca">ALEX<b>TV</b></div>
  <div class="sub">visor en el navegador · no es la app, es para mirar y para probar el panel</div>
</header>

<main>
  <div>
    <div class="panel" id="acceso">
      <h2>Conexión</h2>
      <input id="pegado" placeholder="Pegar la URL del proveedor">
      <input id="host" placeholder="servidor">
      <input id="puerto" placeholder="puerto" inputmode="numeric">
      <input id="usuario" placeholder="usuario">
      <input id="clave" placeholder="contraseña">
      <button id="conectar">Conectar</button>
      <button class="sec" id="olvidar">Olvidar</button>
      <div class="sub" id="errorAcceso" style="margin-top:8px"></div>
    </div>
    <div class="panel" style="margin-top:14px">
      <h2>Canales</h2>
      <input id="buscar" placeholder="Buscar">
      <div class="cats" id="cats"></div>
      <div class="lista" id="lista"></div>
    </div>
  </div>

  <div>
    <div class="panel">
      <video id="video" controls playsinline></video>
      <div class="estado">
        <span>Canal: <b id="eCanal">—</b></span>
        <span>Estado: <b id="eEstado">en espera</b></span>
        <span>Formato: <b id="eFormato">—</b></span>
        <span>Búfer: <b id="eBufer">0 s</b></span>
        <span>Reconexiones: <b id="eRec">0</b></span>
      </div>
      <div class="estado" id="eNota"></div>
    </div>
  </div>
</main>

<script>
(function () {
  const $ = (id) => document.getElementById(id);
  const guardado = () => { try { return JSON.parse(localStorage.getItem('alextv') || '{}'); } catch { return {}; } };
  const guardar = (o) => { try { localStorage.setItem('alextv', JSON.stringify(o)); } catch {} };

  let cuenta = guardado();
  let canales = [], categorias = [], catSel = null, actual = null;

  // ---- acceso ----
  for (const [k, id] of [['host','host'],['puerto','puerto'],['usuario','usuario'],['clave','clave']]) {
    if (cuenta[k]) $(id).value = cuenta[k];
  }
  $('pegado').addEventListener('input', () => {
    try {
      const u = new URL($('pegado').value.trim());
      $('host').value = u.hostname;
      $('puerto').value = u.port || '80';
      const us = u.searchParams.get('username'); if (us) $('usuario').value = us;
      const pw = u.searchParams.get('password'); if (pw) $('clave').value = pw;
    } catch {}
  });
  $('olvidar').addEventListener('click', () => { localStorage.removeItem('alextv'); location.reload(); });
  $('conectar').addEventListener('click', () => {
    cuenta = {
      host: $('host').value.trim(), puerto: $('puerto').value.trim() || '80',
      usuario: $('usuario').value.trim(), clave: $('clave').value.trim(),
    };
    guardar(cuenta);
    cargarLista();
  });

  const base = () => 'http://' + cuenta.host + ':' + cuenta.puerto;
  // La lista pasa por el servidor local porque el panel no permite origen
  // cruzado en player_api.php. El video no: va directo.
  const api = (extra) => '/api?url=' + encodeURIComponent(
    base() + '/player_api.php?username=' + encodeURIComponent(cuenta.usuario) +
    '&password=' + encodeURIComponent(cuenta.clave) + (extra || ''));

  async function cargarLista() {
    if (!cuenta.host || !cuenta.usuario) { $('errorAcceso').textContent = 'Faltan datos.'; return; }
    $('errorAcceso').textContent = 'Cargando…';
    try {
      const info = await (await fetch(api(''))).json();
      if (!info.user_info || String(info.user_info.auth) !== '1') {
        $('errorAcceso').innerHTML = '<span class="malo">El panel rechazó la cuenta.</span>';
        return;
      }
      const cons = info.user_info;
      $('errorAcceso').innerHTML = 'Cuenta ' + cons.status + ' · conexiones ' +
        (cons.active_cons || 0) + ' de ' + (cons.max_connections || '?');
      categorias = await (await fetch(api('&action=get_live_categories'))).json();
      canales = await (await fetch(api('&action=get_live_streams'))).json();
      if (!Array.isArray(canales) || !canales.length) {
        $('errorAcceso').innerHTML = '<span class="malo">Autentica pero devuelve cero canales: ' +
          'bloqueo por país o por rango de IP.</span>';
        return;
      }
      pintarCats(); pintarLista();
    } catch (e) {
      $('errorAcceso').innerHTML = '<span class="malo">No se pudo leer la lista: ' + e.message + '</span>';
    }
  }

  function pintarCats() {
    const c = $('cats'); c.innerHTML = '';
    const mk = (nom, id) => {
      const d = document.createElement('div');
      d.className = 'cat' + (catSel === id ? ' on' : '');
      d.textContent = nom;
      d.onclick = () => { catSel = id; pintarCats(); pintarLista(); };
      c.appendChild(d);
    };
    mk('Todos', null);
    for (const k of (categorias || [])) mk(k.category_name, k.category_id);
  }

  function pintarLista() {
    const q = $('buscar').value.trim().toLowerCase();
    const l = $('lista'); l.innerHTML = '';
    const vis = canales.filter((c) =>
      (catSel === null || String(c.category_id) === String(catSel)) &&
      (!q || String(c.name).toLowerCase().includes(q)));
    for (const c of vis.slice(0, 400)) {
      const d = document.createElement('div');
      d.className = 'canal' + (actual && actual.stream_id === c.stream_id ? ' on' : '');
      const logo = c.stream_icon && String(c.stream_icon).startsWith('http')
        ? '<img src="' + c.stream_icon + '" onerror="this.remove()">' : '';
      d.innerHTML = '<span class="n">' + (c.num || '') + '</span>' + logo +
                    '<span>' + String(c.name).replace(/</g, '&lt;') + '</span>';
      d.onclick = () => reproducir(c);
      l.appendChild(d);
    }
    if (!vis.length) l.innerHTML = '<div class="canal">Sin resultados</div>';
  }
  $('buscar').addEventListener('input', pintarLista);

  // ---- reproduccion, con la misma idea que la app ----
  const video = $('video');
  const FORMATOS = [
    { id: 'hls', etiqueta: 'HLS (segmentado)', ext: '.m3u8' },
    { id: 'ts', etiqueta: 'TS (directo)', ext: '.ts' },
  ];
  let motor = null, fmt = 0, intentos = 0, rondas = 0, reconexiones = 0;
  let arranco = false, temporizador = null, sanoMs = 0, estancadoMs = 0, ultimoT = -1;

  const urlDe = (c, f) => base() + '/live/' + encodeURIComponent(cuenta.usuario) + '/' +
    encodeURIComponent(cuenta.clave) + '/' + c.stream_id + FORMATOS[f].ext;

  function estado(t, clase) {
    $('eEstado').textContent = t;
    $('eEstado').className = clase || '';
  }
  function nota(t) { $('eNota').innerHTML = t ? '<span class="aviso">' + t + '</span>' : ''; }

  function destruir() {
    if (temporizador) { clearTimeout(temporizador); temporizador = null; }
    if (motor) {
      try { motor.destroy(); } catch {}
      try { motor.detachMediaElement && motor.detachMediaElement(); } catch {}
      motor = null;
    }
  }

  function reproducir(c) {
    actual = c; fmt = 0; intentos = 0; rondas = 0; reconexiones = 0;
    arranco = false; sanoMs = 0; estancadoMs = 0; ultimoT = -1;
    $('eCanal').textContent = c.name;
    $('eRec').textContent = '0';
    pintarLista();
    cargar();
  }

  function cargar() {
    destruir();
    const f = FORMATOS[fmt];
    const url = urlDe(actual, fmt);
    $('eFormato').textContent = f.etiqueta;
    estado('conectando…');
    nota('');
    arranco = false;

    if (f.id === 'hls') {
      if (window.Hls && Hls.isSupported()) {
        motor = new Hls({ lowLatencyMode: false, maxBufferLength: 30, manifestLoadingMaxRetry: 2 });
        motor.on(Hls.Events.ERROR, (_e, d) => { if (d && d.fatal) fallo('error de HLS: ' + d.details); });
        motor.loadSource(url);
        motor.attachMedia(video);
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = url; // Safari reproduce HLS por su cuenta
      } else { fallo('el navegador no soporta HLS'); return; }
    } else {
      if (window.mpegts && mpegts.getFeatureList().mseLivePlayback) {
        motor = mpegts.createPlayer({ type: 'mpegts', isLive: true, url });
        motor.on(mpegts.Events.ERROR, (a, b) => fallo('error de TS: ' + a + ' ' + b));
        motor.attachMediaElement(video);
        motor.load();
      } else { fallo('el navegador no soporta TS en directo'); return; }
    }
    video.play().catch(() => {});
    // Plazo de arranque, igual que en la app: si no dio imagen, no se insiste
    // con este formato.
    temporizador = setTimeout(() => { if (!arranco) fallo('no dio imagen en 9 s'); }, 9000);
  }

  function fallo(motivo) {
    intentos++;
    const max = arranco ? 3 : 2;
    if (intentos > max) { siguienteFormato(motivo); return; }
    reconexiones++; $('eRec').textContent = String(reconexiones);
    estado('reconectando (' + intentos + ')', 'aviso');
    nota(motivo);
    destruir();
    temporizador = setTimeout(cargar, 600 * intentos);
  }

  function siguienteFormato(motivo) {
    intentos = 0; fmt++;
    if (fmt < FORMATOS.length) {
      nota(motivo + ' · probando otro formato');
      destruir();
      temporizador = setTimeout(cargar, 400);
      return;
    }
    // Agotados los formatos no se abandona: ronda lenta, como en la app.
    fmt = 0; rondas++;
    const espera = Math.min(5000 * rondas, 30000);
    estado('sin señal · reintentando en ' + (espera / 1000) + ' s', 'malo');
    nota(motivo);
    destruir();
    temporizador = setTimeout(cargar, espera);
  }

  video.addEventListener('playing', () => { arranco = true; estado('reproduciendo'); nota(''); });

  // Vigilante: el socket puede quedar abierto y dejar de traer bytes sin lanzar
  // ningun error. Si la posicion no avanza, se reconecta por cuenta propia.
  setInterval(() => {
    if (!actual) return;
    let bufer = 0;
    try {
      if (video.buffered.length) bufer = video.buffered.end(video.buffered.length - 1) - video.currentTime;
    } catch {}
    $('eBufer').textContent = Math.max(0, Math.round(bufer)) + ' s';

    if (video.paused || video.ended) { ultimoT = video.currentTime; return; }
    if (video.currentTime === ultimoT) {
      estancadoMs += 1000; sanoMs = 0;
      if (estancadoMs >= 8000) {
        estancadoMs = 0;
        fallo('imagen congelada sin error');
      }
    } else {
      estancadoMs = 0; sanoMs += 1000;
      if (sanoMs >= 60000) { intentos = 0; rondas = 0; }
    }
    ultimoT = video.currentTime;
  }, 1000);

  if (cuenta.host) cargarLista();
})();
</script>
</body></html>`;
}

const servidor = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/api') {
    const destino = url.searchParams.get('url');
    if (!destino) { res.writeHead(400).end('falta url'); return; }
    proxy(destino, res);
    return;
  }
  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(await pagina());
    return;
  }
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('no hay nada aca');
});

servidor.listen(PUERTO, '0.0.0.0', () => {
  console.log('');
  console.log('  Visor:  http://localhost:' + PUERTO + '/');
  console.log('  En la red local: http://' + ipLocal() + ':' + PUERTO + '/');
  console.log('');
  console.log('  El video va directo del panel al navegador.');
  console.log('  Solo la lista de canales pasa por aca, porque el panel no');
  console.log('  permite origen cruzado en player_api.php.');
  console.log('');
  console.log('  Ctrl+C para terminar.');
  console.log('');
});
