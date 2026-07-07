/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'node', // solo se testea el núcleo (sin DOM); fake-indexeddb cubre Dexie
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
