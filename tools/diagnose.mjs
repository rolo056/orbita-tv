#!/usr/bin/env node
/**
 * Diagnóstico del enlace contra un panel Xtream, sin dependencias.
 *
 * Corre las mismas pruebas que la pantalla de Diagnóstico de la app, pero desde
 * una computadora conectada a la misma red que el televisor. Sirve para saber
 * cuál de las causas es la tuya antes de tener el APK instalado.
 *
 * Uso:
 *   node tools/diagnose.mjs --url "http://servidor:puerto/get.php?username=U&password=P"
 *   node tools/diagnose.mjs --host servidor --port 8080 --user U --pass P
 *
 * Nada se guarda en disco y nada sale de tu red, salvo que pidas --ip para
 * consultar desde qué ciudad te ve el proveedor.
 */

import dns from 'node:dns';
import net from 'node:net';
import http from 'node:http';
import https from 'node:https';
import { URL } from 'node:url';

const UA = 'VLC/3.0.20 LibVLC/3.0.20';

// ---------------------------------------------------------------- argumentos

function parseArgs(argv) {
  const out = {};
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      out[key] = next;
      i++;
    } else {
      out[key] = true;
    }
  }
  return out;
}

function accountFromArgs(args) {
  if (args.url) {
    const u = new URL(String(args.url));
    return {
      protocol: u.protocol === 'https:' ? 'https:' : 'http:',
      host: u.hostname,
      port: Number(u.port) || (u.protocol === 'https:' ? 443 : 80),
      user: u.searchParams.get('username') || '',
      pass: u.searchParams.get('password') || '',
    };
  }
  return {
    protocol: args.https ? 'https:' : 'http:',
    host: String(args.host || ''),
    port: Number(args.port || 80),
    user: String(args.user || ''),
    pass: String(args.pass || ''),
  };
}

// ------------------------------------------------------------------- salida

const C = {
  reset: '\u001b[0m',
  dim: '\u001b[2m',
  bold: '\u001b[1m',
  ok: '\u001b[32m',
  warn: '\u001b[33m',
  fail: '\u001b[31m',
  info: '\u001b[36m',
};

const causes = new Set();

function line(level, step, detail) {
  const mark = { ok: '  OK ', warn: ' AVISO', fail: ' FALLA', info: ' DATO' }[level];
  const color = { ok: C.ok, warn: C.warn, fail: C.fail, info: C.info }[level];
  console.log(`${color}${mark}${C.reset}  ${C.bold}${step}${C.reset}`);
  for (const l of String(detail).split('\n')) {
    console.log(`        ${C.dim}${l}${C.reset}`);
  }
}

function section(title) {
  console.log(`\n${C.bold}${title}${C.reset}`);
}

// -------------------------------------------------------------------- red

function resolve(host, family) {
  return new Promise((resolve) => {
    const fn = family === 4 ? dns.resolve4 : dns.resolve6;
    fn(host, (err, addrs) => resolve(err ? [] : addrs));
  });
}

function tcpProbe(address, port, family) {
  return new Promise((resolve) => {
    const started = Date.now();
    const socket = new net.Socket();
    let settled = false;
    const done = (ok, error) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve({ ok, ms: Date.now() - started, error });
    };
    socket.setTimeout(8000);
    socket.once('connect', () => done(true));
    socket.once('timeout', () => done(false, 'tiempo de espera agotado'));
    socket.once('error', (e) => done(false, e.code || e.message));
    socket.connect({ host: address, port, family });
  });
}

/** GET simple que devuelve el cuerpo completo. */
function fetchText(url, { timeout = 20000 } = {}) {
  return new Promise((resolve) => {
    const u = new URL(url);
    const mod = u.protocol === 'https:' ? https : http;
    const started = Date.now();
    const req = mod.request(
      url,
      { headers: { 'User-Agent': UA }, timeout },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () =>
          resolve({
            code: res.statusCode,
            body: Buffer.concat(chunks).toString('utf8'),
            ms: Date.now() - started,
          }),
        );
      },
    );
    req.on('timeout', () => {
      req.destroy();
      resolve({ code: 0, body: '', ms: Date.now() - started, error: 'tiempo de espera agotado' });
    });
    req.on('error', (e) => resolve({ code: 0, body: '', ms: Date.now() - started, error: e.code || e.message }));
    req.end();
  });
}

/**
 * Descarga el stream unos segundos. Lo que importa no es el promedio: es
 * maxGap, la pausa más larga sin recibir un solo byte. Un promedio bueno con
 * una pausa de cuatro segundos es exactamente lo que congela la imagen.
 */
function probeStream(url, seconds) {
  return new Promise((resolve) => {
    const u = new URL(url);
    const mod = u.protocol === 'https:' ? https : http;
    const started = Date.now();
    let bytes = 0;
    let maxGap = 0;
    let last = started;
    let ttfb = 0;

    const req = mod.request(url, { headers: { 'User-Agent': UA }, timeout: 15000 }, (res) => {
      if (res.statusCode < 200 || res.statusCode >= 300) {
        req.destroy();
        return resolve({ code: res.statusCode, bytes: 0, maxGap: 0, ttfb: Date.now() - started, kbps: 0, seconds: 0 });
      }
      res.on('data', (chunk) => {
        const now = Date.now();
        if (!ttfb) ttfb = now - started;
        const gap = now - last;
        if (gap > maxGap) maxGap = gap;
        last = now;
        bytes += chunk.length;
        if (now - started >= seconds * 1000) {
          req.destroy();
          finish(res.statusCode);
        }
      });
      res.on('end', () => finish(res.statusCode));
      res.on('error', () => finish(res.statusCode));
    });

    let settled = false;
    function finish(code) {
      if (settled) return;
      settled = true;
      const elapsed = Math.max(1, Math.round((Date.now() - started) / 1000));
      resolve({
        code,
        bytes,
        maxGap,
        ttfb,
        seconds: elapsed,
        kbps: Math.round((bytes * 8) / 1000 / elapsed),
      });
    }

    req.on('timeout', () => { req.destroy(); finish(0); });
    req.on('error', () => finish(0));
    req.end();
  });
}

// -------------------------------------------------------------------- main

async function main() {
  const args = parseArgs(process.argv);
  const acc = accountFromArgs(args);

  if (!acc.host || !acc.user) {
    console.log('Falta el servidor o el usuario.\n');
    console.log('  node tools/diagnose.mjs --url "http://servidor:puerto/get.php?username=U&password=P"');
    console.log('  node tools/diagnose.mjs --host servidor --port 8080 --user U --pass P');
    console.log('\nAgrega --ip para consultar además desde qué ciudad te ve el proveedor.');
    process.exit(1);
  }

  const base = `${acc.protocol}//${acc.host}:${acc.port}`;
  console.log(`${C.bold}Diagnóstico de ${acc.host}:${acc.port}${C.reset}`);
  console.log(`${C.dim}usuario ${acc.user} · ${new Date().toLocaleString()}${C.reset}`);

  // 1. Nombre del servidor
  section('1 · Nombre del servidor');
  const [v4, v6] = await Promise.all([resolve(acc.host, 4), resolve(acc.host, 6)]);
  if (!v4.length && !v6.length) {
    line('fail', 'Resolución DNS', `Ninguna dirección para ${acc.host}. El DNS del router no lo resuelve.`);
    causes.add('PUERTO');
  } else {
    line('ok', 'Resolución DNS', `IPv4: ${v4.join(', ') || 'ninguna'}\nIPv6: ${v6.join(', ') || 'ninguna'}`);
  }

  // 2. Puerto, separando familias
  section('2 · Puerto, por familia de direcciones');
  let v4ok = false;
  let v6ok = false;
  for (const a of v4.slice(0, 2)) {
    const r = await tcpProbe(a, acc.port, 4);
    v4ok = v4ok || r.ok;
    line(r.ok ? 'ok' : 'fail', `IPv4 ${a}:${acc.port}`, r.ok ? `abierto en ${r.ms} ms` : r.error);
  }
  for (const a of v6.slice(0, 2)) {
    const r = await tcpProbe(a, acc.port, 6);
    v6ok = v6ok || r.ok;
    line(r.ok ? 'ok' : 'warn', `IPv6 ${a}:${acc.port}`, r.ok ? `abierto en ${r.ms} ms` : r.error);
  }
  if (v6.length && !v6ok && v4ok) {
    causes.add('IPV6');
    line('fail', 'IPv4 contra IPv6',
      'El servidor publica IPv6 pero no atiende ahí, y sí atiende por IPv4.\n' +
      'Starlink entrega IPv6 nativo, así que el televisor lo intenta primero y se queda colgado.\n' +
      'En la app: dejar activado "Forzar IPv4".');
  }
  if (!v4ok && !v6ok) causes.add('PUERTO');

  // 3. La cuenta, según el propio panel
  section('3 · La cuenta, según el panel');
  const api = (extra = '') =>
    `${base}/player_api.php?username=${encodeURIComponent(acc.user)}&password=${encodeURIComponent(acc.pass)}${extra}`;
  const info = await fetchText(api());
  let auth = false;
  if (info.code !== 200) {
    line('fail', 'player_api.php', `Respondió ${info.code || 'nada'} ${info.error || ''}`);
    if (info.code === 403) causes.add('BLOQUEO');
  } else {
    let parsed = null;
    try { parsed = JSON.parse(info.body); } catch { /* panel devolviendo basura */ }
    const u = parsed?.user_info || {};
    auth = String(u.auth) === '1';
    const active = Number(u.active_cons || 0);
    const max = Number(u.max_connections || 0);
    line(auth && u.status === 'Active' ? 'ok' : 'fail', 'Autenticación',
      `auth: ${auth ? 'correcta' : 'rechazada'} · estado: ${u.status || '?'} · ` +
      `vence: ${u.exp_date ? new Date(Number(u.exp_date) * 1000).toLocaleDateString() : 'sin fecha'} · ` +
      `respuesta en ${info.ms} ms`);
    if (max > 0 && active >= max) {
      causes.add('OCUPADA');
      line('fail', 'Conexiones simultáneas',
        `${active} en uso de ${max} permitidas.\n` +
        'El panel no va a entregar otro canal hasta que se cierre el que quedó abierto\n' +
        'en otro aparato. Se ve igual que un problema de red, y no lo es.');
    } else {
      line('ok', 'Conexiones simultáneas', `${active} en uso de ${max} permitidas`);
    }
  }

  // 4. La lista de canales
  section('4 · La lista de canales');
  let firstId = null;
  if (auth) {
    const list = await fetchText(api('&action=get_live_streams'));
    let chans = [];
    try { chans = JSON.parse(list.body); } catch { /* no es una lista */ }
    if (!Array.isArray(chans) || chans.length === 0) {
      causes.add('BLOQUEO');
      line('fail', 'get_live_streams',
        'El panel autentica pero devuelve cero canales.\n' +
        'Es el síntoma clásico de bloqueo por país o por rango de IP: la salida de\n' +
        'Starlink no es tu ciudad, es su punto de presencia.');
    } else {
      firstId = chans[0].stream_id;
      line('ok', 'get_live_streams', `${chans.length} canales en vivo · respuesta en ${list.ms} ms`);
    }
  } else {
    line('warn', 'get_live_streams', 'Se omite: la autenticación no pasó.');
  }

  // 5. Caudal real del video
  section('5 · Caudal real del video');
  if (firstId) {
    const cred = `${encodeURIComponent(acc.user)}/${encodeURIComponent(acc.pass)}`;

    // HLS: primero la lista, después el primer segmento. Medir la lista no dice
    // nada del video, porque son dos kilobytes de texto.
    const playlistUrl = `${base}/live/${cred}/${firstId}.m3u8`;
    const playlist = await fetchText(playlistUrl, { timeout: 15000 });
    if (playlist.code !== 200) {
      line('warn', 'HLS (segmentado)', `La lista respondió ${playlist.code || 'nada'} ${playlist.error || ''}`);
      if (playlist.code === 403 || playlist.code === 401) causes.add('BLOQUEO');
    } else {
      const seg = playlist.body.split('\n').map((l) => l.trim())
        .find((l) => l && !l.startsWith('#'));
      if (!seg) {
        line('warn', 'HLS (segmentado)', 'La lista llegó pero sin segmentos dentro.');
      } else {
        const segUrl = seg.startsWith('http') ? seg : new URL(seg, playlistUrl).toString();
        const p = await probeStream(segUrl, 8);
        const level = p.code < 200 || p.code >= 300 || p.bytes === 0 ? 'fail' : p.maxGap > 3000 ? 'warn' : 'ok';
        if (level === 'warn') causes.add('INESTABLE');
        if (p.code === 403 || p.code === 401) causes.add('BLOQUEO');
        line(level, 'HLS (segmentado)',
          `primer byte en ${p.ttfb} ms · ${Math.round(p.bytes / 1024)} KB en ${p.seconds} s · ` +
          `${p.kbps} kbps · pausa más larga sin datos: ${p.maxGap} ms`);
      }
    }

    // TS crudo: una sola conexión TCP que debe durar horas.
    const tsUrl = `${base}/live/${cred}/${firstId}.ts`;
    const t = await probeStream(tsUrl, 10);
    const level = t.code < 200 || t.code >= 300 || t.bytes === 0 ? 'fail' : t.maxGap > 3000 ? 'warn' : 'ok';
    if (level === 'warn') causes.add('INESTABLE');
    if (t.code === 403 || t.code === 401) causes.add('BLOQUEO');
    line(level, 'TS (directo)',
      t.bytes === 0
        ? `El servidor respondió ${t.code || 'nada'} y no envió video.`
        : `primer byte en ${t.ttfb} ms · ${Math.round(t.bytes / 1024)} KB en ${t.seconds} s · ` +
          `${t.kbps} kbps · pausa más larga sin datos: ${t.maxGap} ms`);
  } else {
    line('warn', 'Prueba de caudal', 'Se omite: no hubo ningún canal que probar.');
  }

  // 6. Desde dónde te ve el proveedor
  if (args.ip) {
    section('6 · Salida a internet');
    const r = await fetchText('https://ipinfo.io/json', { timeout: 10000 });
    try {
      const o = JSON.parse(r.body);
      const starlink = /starlink|spacex/i.test(o.org || '');
      line('info', 'IP pública',
        `${o.ip} · ${o.city}, ${o.region}, ${o.country} · ${o.org}\n` +
        (starlink
          ? 'Confirmado: la salida es de Starlink. Si la ciudad no es la tuya, el\nproveedor de IPTV te ve desde ahí y por eso puede bloquearte.'
          : 'Esta salida no parece de Starlink: la prueba puede no reflejar lo que ve el televisor.'));
    } catch {
      line('warn', 'IP pública', 'No se pudo consultar.');
    }
  }

  // Conclusión
  section('Conclusión');
  if (causes.size === 0) {
    console.log(`${C.ok}El enlace y la cuenta están sanos en este momento.${C.reset}`);
    console.log(`${C.dim}Si igual se corta durante el día, es intermitente: el búfer grande y el
vigilante de la app son exactamente para eso. Repite la prueba justo cuando
falle en el televisor para ver la pausa sin datos.${C.reset}`);
  } else {
    const verdicts = {
      OCUPADA: 'La cuenta está ocupada, no es la red.',
      BLOQUEO: 'El proveedor está bloqueando la salida de Starlink.',
      IPV6: 'IPv6 roto del lado del servidor.',
      PUERTO: 'El puerto del panel no responde.',
      INESTABLE: 'El enlace trae cortes y hay que absorberlos.',
    };
    const order = ['OCUPADA', 'BLOQUEO', 'IPV6', 'PUERTO', 'INESTABLE'];
    for (const k of order) {
      if (causes.has(k)) console.log(`${C.warn}· ${verdicts[k]}${C.reset}`);
    }
  }
  console.log('');
}

main();
