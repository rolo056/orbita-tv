#!/usr/bin/env node
/**
 * Revisa un tema antes de aplicarlo, sin dependencias.
 *
 * La app nunca se rompe con un tema malo —descarta lo que no entiende— pero
 * "no se rompe" no es lo mismo que "se puede usar desde el sofá". Esto comprueba
 * lo que la app no puede: que el contraste alcance, que el anillo de foco se
 * distinga de los dos fondos, y que los tres colores de estado no se confundan
 * entre si a tres metros.
 *
 * Uso:  node tools/validar-tema.mjs archivo.json
 *       node tools/validar-tema.mjs            (revisa el tema.json del repo)
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// Espejo de Skin.kt. Si agregas un token alla, agregalo aca.
const ESQUEMA = {
  fondo: { t: 'color' }, tarjeta: { t: 'color' }, tarjetaFoco: { t: 'color' },
  linea: { t: 'color' }, texto: { t: 'color' }, textoSuave: { t: 'color' },
  acento: { t: 'color' }, aviso: { t: 'color' }, falla: { t: 'color' },
  esquinas: { t: 'num', min: 0, max: 40 },
  grosorFoco: { t: 'num', min: 0, max: 8 },
  grosorReposo: { t: 'num', min: 0, max: 8 },
  escalaFoco: { t: 'num', min: 1, max: 1.15 },
  escalaTexto: { t: 'num', min: 0.7, max: 1.6 },
  fuenteUrl: { t: 'url' },
  margenPantalla: { t: 'num', min: 0, max: 80 },
  separacion: { t: 'num', min: 0, max: 40 },
  disposicion: { t: 'enum', valores: ['lista', 'mosaico'] },
  anchoTile: { t: 'num', min: 120, max: 520 },
  mostrarLogos: { t: 'bool' },
  anchoBarraLateral: { t: 'num', min: 160, max: 520 },
  logoUrl: { t: 'url' },
  fondoImagenUrl: { t: 'url' },
  fondoImagenOpacidad: { t: 'num', min: 0, max: 1 },
};

const C = { r: '\u001b[0m', b: '\u001b[1m', d: '\u001b[2m',
            ok: '\u001b[32m', wa: '\u001b[33m', fa: '\u001b[31m' };

let fallas = 0;
let avisos = 0;

function di(nivel, texto, detalle) {
  const m = { ok: `${C.ok}  OK  ${C.r}`, wa: `${C.wa} AVISO${C.r}`, fa: `${C.fa} FALLA${C.r}` }[nivel];
  if (nivel === 'fa') fallas++;
  if (nivel === 'wa') avisos++;
  console.log(`${m}  ${texto}`);
  if (detalle) console.log(`        ${C.d}${detalle}${C.r}`);
}

function rgb(hex) {
  const h = String(hex).replace('#', '');
  const e = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  if (!/^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/.test(e)) return null;
  const v = e.length === 8 ? e.slice(2) : e; // #AARRGGBB -> descartar alfa
  return [0, 2, 4].map((i) => parseInt(v.slice(i, i + 2), 16));
}

/** Luminancia relativa segun WCAG 2.1. */
function lum([r, g, b]) {
  const f = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

function contraste(a, b) {
  const la = lum(rgb(a));
  const lb = lum(rgb(b));
  const [hi, lo] = la > lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

/** Tono en grados y saturacion 0-1, para comparar colores de estado. */
function hs(hex) {
  const [r, g, b] = rgb(hex).map((c) => c / 255);
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  const l = (max + min) / 2;
  const s = d === 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
  return { h, s, l };
}

function distanciaTono(a, b) {
  const d = Math.abs(hs(a).h - hs(b).h);
  return Math.min(d, 360 - d);
}

// ------------------------------------------------------------------ main

const archivo = process.argv[2] ? path.resolve(process.argv[2]) : path.join(RAIZ, 'tema.json');
let tema;
try {
  tema = JSON.parse(fs.readFileSync(archivo, 'utf8'));
} catch (e) {
  console.log(`${C.fa} FALLA${C.r}  No se pudo leer el tema: ${e.message}`);
  process.exit(1);
}

console.log(`\n${C.b}Revisando ${archivo}${C.r}\n`);

// ---- 1. Claves y rangos ----
console.log(`${C.b}1 · Claves y rangos${C.r}`);
const desconocidas = Object.keys(tema).filter((k) => !k.startsWith('_') && !(k in ESQUEMA));
if (desconocidas.length) {
  di('wa', 'Claves que la app va a ignorar', desconocidas.join(', '));
} else {
  di('ok', 'Todas las claves son conocidas');
}

for (const [k, v] of Object.entries(tema)) {
  if (k.startsWith('_') || !(k in ESQUEMA)) continue;
  const e = ESQUEMA[k];
  if (e.t === 'color') {
    if (!rgb(v)) di('fa', `${k}: no es un color valido`, `valor: ${JSON.stringify(v)}`);
  } else if (e.t === 'num') {
    if (typeof v !== 'number' || Number.isNaN(v)) {
      di('fa', `${k}: no es un numero`, `valor: ${JSON.stringify(v)}`);
    } else if (v < e.min || v > e.max) {
      di('wa', `${k} = ${v} queda fuera de ${e.min} a ${e.max}`,
        `La app lo va a recortar a ${Math.min(Math.max(v, e.min), e.max)}.`);
    }
  } else if (e.t === 'enum' && !e.valores.includes(v)) {
    di('fa', `${k}: solo acepta ${e.valores.join(' o ')}`, `valor: ${JSON.stringify(v)}`);
  } else if (e.t === 'bool' && typeof v !== 'boolean') {
    di('fa', `${k}: tiene que ser true o false`, `valor: ${JSON.stringify(v)}`);
  } else if (e.t === 'url' && v !== null && typeof v === 'string' && v !== ''
             && !/^https?:\/\//.test(v)) {
    di('fa', `${k}: tiene que ser un enlace http`, `valor: ${JSON.stringify(v)}`);
  }
}

// ---- 2. Contraste ----
console.log(`\n${C.b}2 · Contraste${C.r}`);
const pares = [
  ['texto', 'tarjeta', 7, 'Texto principal sobre las tarjetas'],
  ['texto', 'fondo', 7, 'Texto principal sobre el lienzo'],
  ['textoSuave', 'tarjeta', 4.5, 'Texto secundario sobre las tarjetas'],
  ['textoSuave', 'fondo', 4.5, 'Texto secundario sobre el lienzo'],
  ['acento', 'tarjeta', 3, 'Anillo de foco contra la tarjeta en reposo'],
  ['acento', 'tarjetaFoco', 3, 'Anillo de foco contra la tarjeta enfocada'],
];
for (const [a, b, min, desc] of pares) {
  if (!(a in tema) || !(b in tema) || !rgb(tema[a]) || !rgb(tema[b])) continue;
  const r = contraste(tema[a], tema[b]);
  const txt = `${desc}: ${r.toFixed(2)}:1 (minimo ${min}:1)`;
  if (r >= min) di('ok', txt);
  else di('fa', txt, `${a} ${tema[a]} contra ${b} ${tema[b]}. Aclarar ${a} u oscurecer ${b}.`);
}

// ---- 3. El foco ----
console.log(`\n${C.b}3 · El foco${C.r}`);
if (tema.tarjeta && tema.tarjetaFoco && rgb(tema.tarjeta) && rgb(tema.tarjetaFoco)) {
  const r = contraste(tema.tarjeta, tema.tarjetaFoco);
  if (r >= 1.3) {
    di('ok', `La tarjeta enfocada se distingue de la normal por su fondo: ${r.toFixed(2)}:1`);
  } else {
    di('fa', `La tarjeta enfocada casi no se distingue: ${r.toFixed(2)}:1`,
      'Dos estados que solo cambian el borde no se notan desde el sofa.');
  }
}
if (typeof tema.grosorFoco === 'number') {
  if (tema.grosorFoco >= 2) di('ok', `Grosor del foco: ${tema.grosorFoco} dp`);
  else di('wa', `Grosor del foco de ${tema.grosorFoco} dp no se ve a tres metros`,
    'Subilo a 2 o mas, o compensa con escalaFoco y mas diferencia de fondo.');
}

// ---- 4. Los tres colores de estado ----
console.log(`\n${C.b}4 · Colores de estado${C.r}`);
const estados = [['acento', 'aviso'], ['acento', 'falla'], ['aviso', 'falla']];
for (const [a, b] of estados) {
  if (!rgb(tema[a] || '') || !rgb(tema[b] || '')) continue;
  const d = distanciaTono(tema[a], tema[b]);
  const txt = `${a} y ${b} se separan ${d.toFixed(0)} grados de tono`;
  if (d >= 40) di('ok', txt);
  else di('wa', txt + ', se pueden confundir a tres metros',
    `${a} ${tema[a]} y ${b} ${tema[b]}. En la pantalla de diagnostico son puntos chicos de color.`);
}

// ---- 5. Particularidades del televisor ----
console.log(`\n${C.b}5 · Particularidades del televisor${C.r}`);
if (tema.texto && rgb(tema.texto)) {
  const [r, g, b] = rgb(tema.texto);
  if (r === 255 && g === 255 && b === 255) {
    di('wa', 'El texto es blanco puro', 'En television produce halo alrededor de las letras.');
  } else di('ok', 'El texto no es blanco puro');
}
if (tema.fondo && rgb(tema.fondo)) {
  const [r, g, b] = rgb(tema.fondo);
  if (r + g + b === 0) {
    di('wa', 'El lienzo es negro puro',
      'Borra el limite de las tarjetas y confunde la interfaz con el video.');
  } else di('ok', 'El lienzo no es negro puro');
}
if (tema.linea && rgb(tema.linea) && typeof tema.grosorReposo === 'number' && tema.grosorReposo <= 1) {
  const s = hs(tema.linea).s;
  if (s > 0.5) {
    di('wa', `El borde en reposo esta muy saturado (${(s * 100).toFixed(0)} %) para 1 dp`,
      'La compresion de color de la senal de TV arrastra las lineas finas saturadas.');
  } else di('ok', 'El borde en reposo tiene saturacion baja, apto para 1 dp');
}
if (tema.acento && rgb(tema.acento) && typeof tema.grosorFoco === 'number') {
  const { s } = hs(tema.acento);
  const tono = hs(tema.acento).h;
  const calido = tono < 40 || tono > 330;
  if (s > 0.8 && calido && tema.grosorFoco >= 3) {
    di('wa', `El acento es un rojo o naranja muy saturado en un anillo de ${tema.grosorFoco} dp`,
      'Los rojos saturados desbordan en television. Ademas un anillo rojo se lee como error: ' +
      'revisa que no se confunda con el color de falla.');
  } else di('ok', 'El acento no desborda en el grosor elegido');
}
if (typeof tema.margenPantalla === 'number') {
  if (tema.margenPantalla >= 24) di('ok', `Margen de pantalla: ${tema.margenPantalla} dp`);
  else di('wa', `Margen de ${tema.margenPantalla} dp es poco`,
    'Hay televisores que recortan un 2 o 3 % del borde.');
}
if (typeof tema.escalaTexto === 'number' && tema.escalaTexto < 0.9) {
  di('wa', `escalaTexto de ${tema.escalaTexto} deja el texto secundario al limite`,
    'Para una interfaz mas densa, baja separacion y margenPantalla antes que el texto.');
}
if (tema.disposicion === 'mosaico' && typeof tema.anchoTile === 'number' && tema.anchoTile < 180) {
  di('wa', `anchoTile de ${tema.anchoTile} corta casi todos los nombres de canal`);
}

console.log(`\n${C.b}Resumen${C.r}`);
if (fallas === 0 && avisos === 0) {
  console.log(`${C.ok}El tema cumple todo. Se puede aplicar.${C.r}\n`);
} else {
  console.log(`${fallas} falla(s) y ${avisos} aviso(s).`);
  console.log(`${C.d}Las fallas hacen la app peor de usar y conviene corregirlas.`);
  console.log(`Los avisos son criterio: la app funciona igual.${C.r}\n`);
}
process.exit(fallas > 0 ? 1 : 0);
