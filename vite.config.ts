/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Cuentas Claras',
        short_name: 'Cuentas',
        description: 'Finanzas personales en Bs y USD, 100% locales',
        lang: 'es-VE',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#0a0f1c',
        theme_color: '#0a0f1c',
        icons: [
          { src: 'iconos/icono-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'iconos/icono-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'iconos/icono-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
        shortcuts: [
          {
            name: '➕ Nuevo gasto',
            short_name: 'Gasto',
            url: '/#/nuevo?tipo=gasto',
            icons: [{ src: 'iconos/icono-192.png', sizes: '192x192' }],
          },
          {
            name: '➕ Nuevo ingreso',
            short_name: 'Ingreso',
            url: '/#/nuevo?tipo=ingreso',
            icons: [{ src: 'iconos/icono-192.png', sizes: '192x192' }],
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,mjs,css,html,svg,png,woff2}'], // `mjs` incluye el worker de pdf.js (Vite lo emite como .mjs)
        navigateFallback: '/index.html',
        // El worker de pdf.js pasa de 1 MB; debe quedar precacheado para importar Zinli offline
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      },
    }),
  ],
  test: {
    environment: 'node', // solo se testea el núcleo (sin DOM); fake-indexeddb cubre Dexie
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
