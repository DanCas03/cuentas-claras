import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/manrope/index.css';
import { App } from './ui/App';
import { db } from './db/db';
import './ui/estilos.css';

// Solo en desarrollo: engancha el singleton Dexie a window para la siembra de las
// verificaciones headless (Playwright). En producción esta rama se elimina en el build.
if (import.meta.env.DEV) (window as unknown as { __db: unknown }).__db = db;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
