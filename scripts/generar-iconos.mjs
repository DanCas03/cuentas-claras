import { mkdirSync } from 'node:fs';
import sharp from 'sharp';

// Moneda estilizada (anillo + barra, tipo ¢) en teal menta sobre el fondo oscuro de la app.
function svg(maskable) {
  const rx = maskable ? 0 : 96;
  const escala = maskable ? 0.7 : 0.92; // zona segura para máscaras redondas
  return Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="${rx}" fill="#0a0f1c"/>
  <g transform="translate(256 256) scale(${escala}) translate(-256 -256)">
    <circle cx="256" cy="256" r="140" fill="none" stroke="#2dd4bf" stroke-width="38"/>
    <line x1="256" y1="70" x2="256" y2="442" stroke="#2dd4bf" stroke-width="38" stroke-linecap="round"/>
  </g>
</svg>`);
}

mkdirSync('public/iconos', { recursive: true });
await sharp(svg(false)).resize(192, 192).png().toFile('public/iconos/icono-192.png');
await sharp(svg(false)).resize(512, 512).png().toFile('public/iconos/icono-512.png');
await sharp(svg(true)).resize(512, 512).png().toFile('public/iconos/icono-maskable-512.png');
console.log('Iconos generados en public/iconos/');
