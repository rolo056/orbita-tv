#!/usr/bin/env node
/**
 * Editor de apariencia en vivo, sin dependencias.
 *
 * Sirve tres cosas en el mismo puerto:
 *   GET  /            el editor, para abrir en el navegador de la PC
 *   GET  /tema.json   lo que la app del televisor consulta
 *   POST /tema.json   guarda los cambios en el tema.json del repositorio
 *
 * Con el televisor en la misma red, en Ajustes de la app pones la direccion que
 * este script imprime al arrancar y activas Modo diseno. Mueves un control aca y
 * el televisor cambia en 3 segundos, con la app abierta.
 *
 * Escribe el tema.json de verdad, asi que cuando el diseno te guste solo queda
 * confirmarlo en git. Este mismo archivo sirve para correr en un VPS si algun
 * dia quieres el editor fuera de la red local.
 *
 * Uso:  node tools/servidor-tema.mjs [--puerto 8787]
 */

import http from 'node:http';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ARCHIVO = path.join(RAIZ, 'tema.json');

const args = process.argv.slice(2);
const iPuerto = args.indexOf('--puerto');
const PUERTO = iPuerto >= 0 ? Number(args[iPuerto + 1]) : 8787;

// Cada control del editor: clave del tema, etiqueta, tipo y rango.
// La lista tiene que coincidir con Skin.kt; si agregas un token alla, agregalo aca.
const CONTROLES = [
  { grupo: 'Colores' },
  { k: 'fondo', t: 'color', l: 'Fondo' },
  { k: 'tarjeta', t: 'color', l: 'Tarjeta' },
  { k: 'tarjetaFoco', t: 'color', l: 'Tarjeta enfocada' },
  { k: 'linea', t: 'color', l: 'Borde' },
  { k: 'texto', t: 'color', l: 'Texto' },
  { k: 'textoSuave', t: 'color', l: 'Texto secundario' },
  { k: 'acento', t: 'color', l: 'Acento' },
  { k: 'aviso', t: 'color', l: 'Aviso' },
  { k: 'falla', t: 'color', l: 'Falla' },

  { grupo: 'Forma y foco' },
  { k: 'esquinas', t: 'rango', l: 'Esquinas', min: 0, max: 40, paso: 1 },
  { k: 'grosorFoco', t: 'rango', l: 'Grosor del foco', min: 0, max: 8, paso: 0.5 },
  { k: 'grosorReposo', t: 'rango', l: 'Grosor en reposo', min: 0, max: 8, paso: 0.5 },
  { k: 'escalaFoco', t: 'rango', l: 'Agrandar al enfocar', min: 1, max: 1.15, paso: 0.01 },

  { grupo: 'Texto' },
  { k: 'escalaTexto', t: 'rango', l: 'Tamano de todos los textos', min: 0.7, max: 1.6, paso: 0.05 },
  { k: 'fuenteUrl', t: 'texto', l: 'Tipografia (.ttf)' },

  { grupo: 'Espacio' },
  { k: 'margenPantalla', t: 'rango', l: 'Margen de pantalla', min: 0, max: 80, paso: 2 },
  { k: 'separacion', t: 'rango', l: 'Separacion', min: 0, max: 40, paso: 1 },

  { grupo: 'Canales' },
  { k: 'disposicion', t: 'opciones', l: 'Disposicion', opciones: ['lista', 'mosaico'] },
  { k: 'anchoTile', t: 'rango', l: 'Ancho de tarjeta', min: 120, max: 520, paso: 10 },
  { k: 'mostrarLogos', t: 'si', l: 'Mostrar logos de canal' },
  { k: 'anchoBarraLateral', t: 'rango', l: 'Ancho de barra lateral', min: 160, max: 520, paso: 10 },

  { grupo: 'Marca' },
  { k: 'logoUrl', t: 'texto', l: 'Logo (imagen)' },
  { k: 'fondoImagenUrl', t: 'texto', l: 'Imagen de fondo' },
  { k: 'fondoImagenOpacidad', t: 'rango', l: 'Opacidad del fondo', min: 0, max: 1, paso: 0.05 },
];

async function leerTema() {
  try {
    return JSON.parse(await fs.readFile(ARCHIVO, 'utf8'));
  } catch {
    return {};
  }
}

/**
 * Guarda conservando las claves que empiezan con guion bajo, que son las notas
 * para quien edite el archivo a mano. El editor no las muestra ni las pisa.
 */
async function guardarTema(nuevo) {
  const actual = await leerTema();
  const salida = {};
  for (const [k, v] of Object.entries(actual)) if (k.startsWith('_')) salida[k] = v;
  for (const [k, v] of Object.entries(nuevo)) if (!k.startsWith('_')) salida[k] = v;
  for (const [k, v] of Object.entries(actual)) {
    if (!k.startsWith('_') && !(k in salida)) salida[k] = v;
  }
  await fs.writeFile(ARCHIVO, JSON.stringify(salida, null, 2) + '\n', 'utf8');
  return salida;
}

function ipLocal() {
  for (const grupo of Object.values(os.networkInterfaces())) {
    for (const i of grupo || []) {
      if (i.family === 'IPv4' && !i.internal) return i.address;
    }
  }
  return '127.0.0.1';
}

function paginaEditor(tema) {
  const controles = CONTROLES.map((c) => {
    if (c.grupo) return `<h2>${c.grupo}</h2>`;
    const valor = tema[c.k];
    const id = `c_${c.k}`;
    if (c.t === 'color') {
      const v = typeof valor === 'string' && valor.startsWith('#') ? valor.slice(0, 7) : '#000000';
      return `<label for="${id}"><span>${c.l}</span>
        <input type="color" id="${id}" data-k="${c.k}" data-t="color" value="${v}">
        <code id="${id}_v">${v}</code></label>`;
    }
    if (c.t === 'rango') {
      const v = Number.isFinite(valor) ? valor : c.min;
      return `<label for="${id}"><span>${c.l}</span>
        <input type="range" id="${id}" data-k="${c.k}" data-t="rango"
               min="${c.min}" max="${c.max}" step="${c.paso}" value="${v}">
        <code id="${id}_v">${v}</code></label>`;
    }
    if (c.t === 'si') {
      return `<label for="${id}"><span>${c.l}</span>
        <input type="checkbox" id="${id}" data-k="${c.k}" data-t="si" ${valor ? 'checked' : ''}>
        <code></code></label>`;
    }
    if (c.t === 'opciones') {
      const ops = c.opciones
        .map((o) => `<option value="${o}" ${valor === o ? 'selected' : ''}>${o}</option>`)
        .join('');
      return `<label for="${id}"><span>${c.l}</span>
        <select id="${id}" data-k="${c.k}" data-t="opciones">${ops}</select>
        <code></code></label>`;
    }
    const v = typeof valor === 'string' ? valor : '';
    return `<label for="${id}"><span>${c.l}</span>
      <input type="text" id="${id}" data-k="${c.k}" data-t="texto" value="${v}" placeholder="vacio">
      <code></code></label>`;
  }).join('\n');

  return `<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Apariencia</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; background:#0b1020; color:#e8edf5;
         font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif; }
  header { padding:20px 28px; border-bottom:1px solid #243352; }
  h1 { margin:0 0 4px; font-size:19px; font-weight:600; }
  p.sub { margin:0; color:#9ba7bd; font-size:13px; }
  main { padding:20px 28px 60px; max-width:720px; }
  h2 { margin:26px 0 10px; font-size:12px; letter-spacing:1px; text-transform:uppercase;
       color:#9ba7bd; font-weight:600; }
  label { display:grid; grid-template-columns:1fr 200px 90px; gap:14px; align-items:center;
          padding:8px 0; border-bottom:1px solid #16203a; }
  label span { color:#e8edf5; }
  code { color:#5fc9a0; font-size:12px; text-align:right; }
  input[type=range] { width:200px; accent-color:#5fc9a0; }
  input[type=color] { width:200px; height:30px; background:none; border:1px solid #243352;
                      border-radius:6px; padding:2px; }
  input[type=text], select { width:200px; background:#141e33; color:#e8edf5;
                             border:1px solid #243352; border-radius:6px; padding:6px 8px; }
  input[type=checkbox] { width:20px; height:20px; accent-color:#5fc9a0; }
  #estado { position:fixed; right:20px; bottom:20px; background:#141e33;
            border:1px solid #243352; border-radius:8px; padding:10px 14px;
            font-size:13px; color:#9ba7bd; }
  #pegar { margin:0 0 8px; }
  #pegar textarea { width:100%; min-height:120px; background:#141e33; color:#e8edf5;
                    border:1px solid #243352; border-radius:8px; padding:10px;
                    font:13px/1.5 ui-monospace,Consolas,monospace; resize:vertical; }
  #pegar button { margin-top:8px; background:#5fc9a0; color:#0b1020; border:0;
                  border-radius:8px; padding:9px 18px; font-size:14px;
                  font-weight:600; cursor:pointer; }
  #pegar .aviso { color:#e2705f; font-size:13px; min-height:19px; margin-top:6px; }
</style></head>
<body>
<header>
  <h1>Apariencia de Órbita TV</h1>
  <p class="sub">Cada cambio se guarda en tema.json. Con Modo diseño activado, el
  televisor lo toma en 3 segundos.</p>
</header>
<main>
  <h2>Pegar un diseño</h2>
  <div id="pegar">
    <p class="sub">Pegá acá el JSON que devolvió quien diseñó la apariencia y aplicalo.
    Solo entran las claves conocidas; lo demás se ignora.</p>
    <textarea id="json" placeholder='{ "fondo": "#0E1116", "acento": "#C9A227" }'></textarea>
    <div class="aviso" id="avisoPegar"></div>
    <button id="aplicar">Aplicar al televisor</button>
  </div>
${controles}</main>
<div id="estado">sin cambios</div>
<script>
  const estado = document.getElementById('estado');
  let pendiente = null;

  function valorDe(el) {
    const t = el.dataset.t;
    if (t === 'rango') return Number(el.value);
    if (t === 'si') return el.checked;
    if (t === 'texto') return el.value.trim() === '' ? null : el.value.trim();
    return el.value;
  }

  async function guardar() {
    const tema = {};
    for (const el of document.querySelectorAll('[data-k]')) tema[el.dataset.k] = valorDe(el);
    estado.textContent = 'guardando...';
    try {
      const r = await fetch('/tema.json', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tema),
      });
      estado.textContent = r.ok ? 'guardado ' + new Date().toLocaleTimeString() : 'error al guardar';
    } catch {
      estado.textContent = 'sin conexion con el servidor';
    }
  }

  // Aplicar un JSON pegado. Se manda tal cual al servidor, que ya conserva las
  // notas del archivo y descarta lo que no reconoce; despues se recarga la
  // pagina para que los controles muestren los valores nuevos.
  document.getElementById('aplicar').addEventListener('click', async () => {
    const aviso = document.getElementById('avisoPegar');
    const texto = document.getElementById('json').value.trim();
    if (!texto) {
      aviso.textContent = 'No hay nada pegado.';
      return;
    }
    let datos;
    try {
      datos = JSON.parse(texto);
    } catch (e) {
      aviso.textContent = 'Eso no es JSON válido: ' + e.message;
      return;
    }
    if (typeof datos !== 'object' || datos === null || Array.isArray(datos)) {
      aviso.textContent = 'Se espera un objeto JSON, con claves y valores.';
      return;
    }
    aviso.textContent = '';
    estado.textContent = 'aplicando...';
    const r = await fetch('/tema.json', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(datos),
    });
    if (r.ok) {
      location.reload();
    } else {
      aviso.textContent = 'El servidor lo rechazó.';
    }
  });

  for (const el of document.querySelectorAll('[data-k]')) {
    el.addEventListener('input', () => {
      const eco = document.getElementById(el.id + '_v');
      if (eco) eco.textContent = el.value;
      clearTimeout(pendiente);
      pendiente = setTimeout(guardar, 250);
    });
  }
</script>
</body></html>`;
}

const servidor = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');

  if (req.method === 'OPTIONS') {
    res.writeHead(204).end();
    return;
  }

  if (url.pathname === '/tema.json' && req.method === 'GET') {
    const tema = await leerTema();
    res.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
      // El televisor consulta cada 3 segundos en modo diseno: nada de cache.
      'Cache-Control': 'no-store',
    });
    res.end(JSON.stringify(tema));
    return;
  }

  if (url.pathname === '/tema.json' && req.method === 'POST') {
    const trozos = [];
    for await (const t of req) trozos.push(t);
    try {
      const nuevo = JSON.parse(Buffer.concat(trozos).toString('utf8'));
      const guardado = await guardarTema(nuevo);
      console.log('tema guardado', new Date().toLocaleTimeString());
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(guardado));
    } catch (e) {
      res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('JSON invalido: ' + e.message);
    }
    return;
  }

  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(paginaEditor(await leerTema()));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('no hay nada aca');
});

servidor.listen(PUERTO, '0.0.0.0', () => {
  const ip = ipLocal();
  console.log('');
  console.log('  Editor:   http://localhost:' + PUERTO + '/');
  console.log('  Para el televisor, en Ajustes > Direccion del archivo de apariencia:');
  console.log('            http://' + ip + ':' + PUERTO + '/tema.json');
  console.log('');
  console.log('  Escribe en ' + ARCHIVO);
  console.log('  Ctrl+C para terminar.');
  console.log('');
});
