# Cuentas Claras — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** Construir "Cuentas Claras", una PWA offline-first de finanzas personales bi-monetarias (Bs + USD) con registro en ≤ 3 toques, importadores de comprobantes BNC (TXT) y Zinli (PDF), tasas BCV/Binance, dashboard consolidado y respaldo JSON, desplegada por HTTPS e instalada en el Android y el Windows de Daniel.

**Arquitectura:** SPA React con navegación por hash (sin router externo), datos en IndexedDB vía Dexie con `useLiveQuery` para reactividad, y un núcleo de dominio en funciones puras (dinero, tasas, saldos, parsers, reglas, dedupe) separado de la UI para poder hacer TDD sin navegador. Los importadores son pipelines `parse → map → preview → commit transaccional`. Todo el estado vive en el dispositivo; el hosting solo sirve código estático.

**Stack:** React 19 + TypeScript + Vite · Dexie 4 + dexie-react-hooks · Recharts · pdfjs-dist · vite-plugin-pwa · Vitest + Testing Library + fake-indexeddb.

**Spec (fuente de verdad del *qué*):** `docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`

## Restricciones globales

Aplican a TODAS las tareas; cada tarea las incluye implícitamente.

1. **Montos siempre como enteros en céntimos** (`amountMinor`), nunca float. Formato visual venezolano exacto: `Bs 13.517,75` (punto de miles, coma decimal) y `$84.20` (formato US).
2. **Todo en español**: textos de UI, docs, nombres de campos visibles, mensajes de commit. Nombres de código (tipos, funciones) pueden mezclar inglés/español; sé consistente con lo que define este plan.
3. **Privacidad**: `TXTBNC1.txt`, `TXTBNC2.txt`, `PDFBNC.pdf`, `ExcelBNC.xls` y `Resumen_de_movimientos_*_Zinli.pdf` están gitignored y contienen cédula, cuentas, teléfonos y dirección reales. **Nunca los commitees ni copies ningún dato de ellos** a fixtures, docs o código. Las fixtures de este plan ya vienen anonimizadas (estructura idéntica, datos inventados) — úsalas tal cual.
4. **TDD** (skill `superpowers:test-driven-development`) en parsers, motor de reglas, dedupe, encadenado de saldos, conversión de monedas, saldos/patrimonio y export/import JSON: test rojo primero, implementación mínima, verde, commit.
5. Antes de cada commit: `npx vitest run` con **toda** la suite en verde. Al cerrar cada fase: `npm run build` sin errores.
6. **Prioridad UX #1**: registro manual de gasto en ≤ 3 toques (+ dígitos). Ninguna tarea posterior puede degradar ese flujo.
7. Entorno: Windows 11, PowerShell 5.1 — **no existe `&&` en la consola**; encadena con `;` o ejecuta comandos separados (dentro de `package.json` los `&&` sí funcionan porque npm usa cmd.exe). Node v23.3.0, npm como gestor de paquetes.
8. Mensajes de commit en español terminando con:
   `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`
9. Decisiones cerradas por Daniel (NO reabrir): el stack fijado en la cabecera; importador BNC solo formato TXT tabulado; respaldo por selector de archivos (sin Drive API); tasas BCV + Binance USDT/VES con override manual e histórico; efectivo físico solo USD (disposición + ahorro); widget nativo Android queda para v2 (v1 solo `shortcuts` del manifest); céntimos enteros.
10. IDs de entidades: `crypto.randomUUID()`, salvo los seeds que usan ids fijos legibles (`acc-bnc`, `cat-comisiones`, …) definidos en la Tarea 5.

## Estructura de archivos final

```
package.json, tsconfig.json, vite.config.ts, index.html
public/iconos/ (generados por scripts/generar-iconos.mjs)
scripts/generar-iconos.mjs
docs/spikes/2026-07-07-cors-tasas.md          ← resultado del spike CORS
src/
  main.tsx
  test/setup.ts            ← fake-indexeddb + jest-dom
  test/utils.ts            ← limpiarDb()
  domain/types.ts          ← TODAS las entidades e invariantes
  domain/money.ts          ← parse/format de montos (Bs y US)
  domain/dates.ts          ← helpers de fechas ISO
  domain/rates.ts          ← effectiveRate + conversión
  domain/balances.ts       ← saldo por cuenta + patrimonio
  domain/stats.ts          ← resumen mensual, acumulado diario, comparativa
  domain/rules.ts          ← motor de reglas de categorización
  domain/builders.ts       ← buildTransfer / buildAdjustment / buildManualTx
  db/db.ts                 ← Dexie schema + seeds + settings
  db/commit.ts             ← commitImport transaccional
  rates/providers.ts       ← RateProvider + pydolarve/dolarapi + fallback
  rates/fixtures/*.json    ← respuestas reales capturadas en el spike
  import/bnc/parseBncTxt.ts
  import/bnc/mapBnc.ts     ← mapper + dedupe + pares + detección de hueco
  import/zinli/extractPdfText.ts  ← pdf.js → líneas de texto
  import/zinli/parseZinli.ts
  import/zinli/mapZinli.ts
  backup/backup.ts         ← export/validate/import JSON
  backup/saveFile.ts       ← picker | share | download
  ui/App.tsx, ui/useHashRoute.ts, ui/Nav.tsx, ui/Toast.tsx, ui/estilos.css
  ui/paginas/DashboardPage.tsx, NuevoPage.tsx, MovimientosPage.tsx,
     ImportarPage.tsx, AjustesPage.tsx
  ui/componentes/Teclado.tsx, CategoriaGrid.tsx, PatrimonioCard.tsx,
     GraficasMes.tsx, ImportPreview.tsx, FormCuenta.tsx, FormCategoria.tsx,
     TasasSection.tsx, RespaldoSection.tsx, TransferForm.tsx, AjusteForm.tsx
  (cada módulo de dominio/parser/backup lleva su `*.test.ts` al lado;
   fixtures de tests en src/import/bnc/fixtures/ y src/import/zinli/fixtures/)
```

Convención: los módulos de `src/domain`, `src/import`, `src/rates`, `src/backup` y `src/db` **no importan nada de React ni de `src/ui`**. Las funciones que tocan la base reciben la instancia `CuentasDB` como primer parámetro `d`; la UI usa el singleton `db`.

---

## Fase 0 — Scaffold y retiro de riesgos

### Tarea 1: Scaffold del proyecto

**Files:**
- Create: `package.json`, `tsconfig.json`, `vite.config.ts`, `index.html`, `src/main.tsx`, `src/ui/App.tsx`, `src/ui/estilos.css`, `src/test/setup.ts`, `src/ui/App.test.tsx`, `README.md`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nada (repo sin código).
- Produces: proyecto Vite operativo con `npm run dev`, `npx vitest run` verde y `npm run build` limpio. Alias de scripts npm que usarán todas las tareas.

- [ ] **Paso 1: Crear `package.json` a mano** (no uses `npm create vite` — el directorio no está vacío y el asistente interactivo se traba):

```json
{
  "name": "cuentas-claras",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview",
    "test": "vitest",
    "test:run": "vitest run",
    "iconos": "node scripts/generar-iconos.mjs"
  }
}
```

- [ ] **Paso 2: Instalar dependencias** (últimas versiones estables; quedan registradas en package.json):

```powershell
npm install react react-dom dexie dexie-react-hooks recharts pdfjs-dist
npm install -D typescript vite @vitejs/plugin-react vitest jsdom fake-indexeddb "@testing-library/react" "@testing-library/jest-dom" "@testing-library/user-event" "@types/react" "@types/react-dom" vite-plugin-pwa sharp
```

Verifica majors esperados en package.json: react ^19, vite ^7, dexie ^4, vitest ^3–4, pdfjs-dist ^5, recharts ^3. Si algún major difiere y una API de este plan no compila, adapta la llamada puntual (documentándolo en el commit), no cambies de librería.

- [ ] **Paso 3: Crear archivos de configuración.**

`tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "skipLibCheck": true,
    "noEmit": true,
    "isolatedModules": true,
    "forceConsistentCasingInFileNames": true,
    "types": ["vite/client", "vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "scripts", "vite.config.ts"]
}
```

`vite.config.ts` (la sección `test` la lee Vitest; VitePWA se añade en la Tarea 24):

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

`src/test/setup.ts`:

```ts
import 'fake-indexeddb/auto';
import '@testing-library/jest-dom/vitest';
```

`index.html`:

```html
<!doctype html>
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
    <meta name="theme-color" content="#0e7490" />
    <title>Cuentas Claras</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

`src/main.tsx`:

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './ui/App';
import './ui/estilos.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

`src/ui/App.tsx` (placeholder; la shell real llega en la Tarea 7):

```tsx
export function App() {
  return <h1>Cuentas Claras</h1>;
}
```

`src/ui/estilos.css` (base mínima; se amplía en la Tarea 7):

```css
:root {
  --fondo: #f6f7f9;
  --tarjeta: #ffffff;
  --primario: #0e7490;
  --gasto: #dc2626;
  --ingreso: #16a34a;
  --texto: #111827;
  --texto2: #6b7280;
  --borde: #e5e7eb;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  background: var(--fondo);
  color: var(--texto);
}
```

`README.md`:

```markdown
# Cuentas Claras

PWA personal de finanzas multi-cuenta y multi-moneda (Bs + USD) para la realidad venezolana.
Offline-first: todos los datos viven en IndexedDB del dispositivo; el hosting solo sirve código.

- Spec: `docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`
- Plan: `docs/superpowers/plans/2026-07-07-cuentas-claras-implementacion.md`

## Comandos

- `npm run dev` — servidor de desarrollo
- `npm run test` / `npm run test:run` — tests (Vitest)
- `npm run build` — typecheck + build de producción
- `npm run preview` — sirve el build (necesario para probar la PWA/offline)
- `npm run iconos` — regenera los íconos PNG desde SVG

## Privacidad

Los comprobantes reales de la raíz están gitignored y **nunca** se commitean.
Las fixtures de tests son anonimizadas (estructura real, datos inventados).
```

- [ ] **Paso 4: Añadir al `.gitignore`** (bloque nuevo al final; no toques las reglas de comprobantes existentes):

```gitignore
# PWA / build extra
dev-dist/
*.tsbuildinfo
```

- [ ] **Paso 5: Test de humo del pipeline.** Crear `src/ui/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { App } from './App';

test('la app monta y muestra el título', () => {
  render(<App />);
  expect(screen.getByText('Cuentas Claras')).toBeInTheDocument();
});
```

- [ ] **Paso 6: Verificar todo.**

```powershell
npx vitest run
```
Esperado: `1 passed`.

```powershell
npm run build
```
Esperado: termina sin errores y genera `dist/`.

```powershell
npm run dev
```
Esperado: abre en `http://localhost:5173` y muestra "Cuentas Claras". Ciérralo (Ctrl+C).

- [ ] **Paso 7: Commit.**

```powershell
git add package.json package-lock.json tsconfig.json vite.config.ts index.html src README.md .gitignore
git commit -m "Scaffold: Vite + React + TS + Vitest para Cuentas Claras

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 2: Spike CORS de las APIs de tasas

Retiro temprano del riesgo técnico #1 de la spec (§4.4). No es TDD: es un experimento con resultado documentado. Pase lo que pase, la app sigue viable (modo manual siempre existe).

**Files:**
- Create: `docs/spikes/2026-07-07-cors-tasas.md`, `src/rates/fixtures/pydolarve.json`, `src/rates/fixtures/dolarapi-oficial.json` (solo los que respondan bien)

**Interfaces:**
- Consumes: nada.
- Produces: decisión documentada del orden de proveedores y **fixtures JSON reales** que la Tarea 13 usa para los tests de parseo.

- [ ] **Paso 1: Probar los candidatos desde el navegador.** Arranca `npm run dev`, abre `http://localhost:5173`, y en la consola DevTools ejecuta cada fetch (así el resultado refleja CORS real de un origen web, no de Node):

```js
// Candidato A: pyDolarVenezuela — BCV y Binance en una sola llamada
fetch('https://pydolarve.org/api/v2/dollar?page=criptodolar')
  .then(r => r.json()).then(j => console.log('pydolarve OK', j)).catch(e => console.error('pydolarve FALLA', e));

// Candidato B: ve.dolarapi.com — solo oficial (BCV)
fetch('https://ve.dolarapi.com/v1/dolares/oficial')
  .then(r => r.json()).then(j => console.log('dolarapi OK', j)).catch(e => console.error('dolarapi FALLA', e));

// Candidato C: ExchangeMonitor (investigar si expone JSON público con CORS)
```

Notas: si la ruta exacta de pydolarve cambió, consulta `https://pydolarve.org/` y su GitHub (proyecto pyDolarVenezuela) hasta dar con el endpoint que devuelva BCV y Binance; anota la URL final. Errores CORS aparecen como `TypeError: Failed to fetch` con aviso CORS en consola.

- [ ] **Paso 2: Capturar fixtures.** Para cada endpoint que funcione, copia la respuesta JSON completa (`copy(j)` en DevTools) a `src/rates/fixtures/<proveedor>.json`. Las respuestas son datos públicos de mercado — sí se commitean.

- [ ] **Paso 3: Documentar el resultado** en `docs/spikes/2026-07-07-cors-tasas.md` con esta plantilla:

```markdown
# Spike: CORS de APIs de tasas (BCV / Binance USDT-VES)

**Fecha:** <fecha real> · **Origen probado:** http://localhost:5173

| Proveedor | URL exacta | ¿CORS OK? | ¿Trae BCV? | ¿Trae Binance? | Campo del valor |
|---|---|---|---|---|---|
| pydolarve | ... | ... | ... | ... | p. ej. monitors.bcv.price |
| ve.dolarapi.com | ... | ... | ... | ... | p. ej. promedio |
| ExchangeMonitor | ... | ... | ... | ... | ... |

## Decisión
Cadena de proveedores para la Tarea 13: <orden elegido>. Binance solo disponible en <...>.
Pendiente re-verificar desde el origen desplegado en la Tarea 24 (CORS puede diferir por dominio).
```

- [ ] **Paso 4: Commit.**

```powershell
git add docs/spikes/2026-07-07-cors-tasas.md src/rates/fixtures
git commit -m "Spike CORS de tasas: resultados y fixtures de proveedores

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Fase 1 — Núcleo de dominio (funciones puras, TDD estricto)

### Tarea 3: Dinero — parseo y formato

**Files:**
- Create: `src/domain/types.ts`, `src/domain/money.ts`, `src/domain/dates.ts`
- Test: `src/domain/money.test.ts`, `src/domain/dates.test.ts`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `type Currency = 'VES' | 'USD'` y todas las entidades (`Account`, `Transaction`, `Category`, `Rate`, `Rule`, `Settings`) en `types.ts`.
  - `parseVenezuelanAmount(raw: string): number | null` — `"13517,75"` → `1351775`.
  - `parseUsAmount(raw: string): number | null` — `"-$0.99"` → `-99`.
  - `formatMoney(minor: number, currency: Currency): string`.
  - `todayIso(): string`, `isoFromDdMmYyyy(s: string): string | null`, `monthOf(dateIso: string): string`.

- [ ] **Paso 1: Crear `src/domain/types.ts`** (sin test propio; lo ejercitan todos los demás):

```ts
export type Currency = 'VES' | 'USD';
export type AccountKind = 'bank' | 'wallet' | 'snapshot' | 'cash';

export interface Account {
  id: string;
  name: string;
  currency: Currency;
  kind: AccountKind;
  isSavings: boolean;
  archived: boolean;
  /** Estado del último import BNC (solo cuentas kind 'bank') para detectar huecos. */
  bncLastImportKey?: string;
  bncLastSaldoMinor?: number;
}

export type TxType = 'expense' | 'income' | 'transfer' | 'adjustment';
export type TxSource = 'manual' | 'bnc' | 'zinli' | 'adjust';

/**
 * Invariantes:
 * - amountMinor es entero en céntimos de la moneda de accountId.
 * - expense/income/transfer: amountMinor > 0 (el tipo da el signo).
 * - adjustment: amountMinor es el DELTA con signo (real − calculado).
 * - transfer: toAccountId obligatorio; toAmountMinor en la moneda destino;
 *   rateUsed (VES por USD) solo si cruza moneda.
 * - date: 'yyyy-mm-dd'; time: 'HH:mm:ss' o 'HH:mm:ss.SSS' (opcional).
 */
export interface Transaction {
  id: string;
  type: TxType;
  accountId: string;
  amountMinor: number;
  date: string;
  time?: string;
  categoryId?: string;
  note?: string;
  source: TxSource;
  importKey?: string;
  toAccountId?: string;
  toAmountMinor?: number;
  rateUsed?: number;
}

export type CategoryKind = 'expense' | 'income';

export interface Category {
  id: string;
  name: string;
  icon: string;   // emoji
  color: string;  // hex
  kind: CategoryKind;
  /** Presupuesto mensual opcional, SIEMPRE en céntimos de USD. */
  monthlyBudgetMinor?: number;
}

export type RateSource = 'BCV' | 'BINANCE' | 'MANUAL';

export interface Rate {
  date: string;        // yyyy-mm-dd
  source: RateSource;
  vesPerUsd: number;   // tasa (no es dinero: puede tener decimales)
}

export interface Rule {
  id: string;
  pattern: string;     // substring, case-insensitive
  categoryId: string;
  autoCreated: boolean;
}

export interface Settings {
  id: 'main';
  viewCurrency: Currency;      // moneda del dashboard (defecto USD)
  rateSource: RateSource;      // fuente activa (defecto BCV)
  lastAccountId?: string;
  lastRatesFetchDate?: string; // yyyy-mm-dd del último fetch exitoso
}
```

- [ ] **Paso 2: Escribir los tests que fallan** — `src/domain/money.test.ts`:

```ts
import { parseVenezuelanAmount, parseUsAmount, formatMoney } from './money';

describe('parseVenezuelanAmount (formato BNC: coma decimal, sin separador de miles)', () => {
  test('entero sin decimales', () => expect(parseVenezuelanAmount('-3500')).toBe(-350000));
  test('con decimales', () => expect(parseVenezuelanAmount('13517,75')).toBe(1351775));
  test('negativo con decimales', () => expect(parseVenezuelanAmount('-2434,34')).toBe(-243434));
  test('cero', () => expect(parseVenezuelanAmount('0')).toBe(0));
  test('artefacto float del export BNC se redondea a céntimos', () =>
    expect(parseVenezuelanAmount('87,6999999999998')).toBe(8770));
  test('un solo decimal', () => expect(parseVenezuelanAmount('9179,7')).toBe(917970));
  test('espacios alrededor se toleran', () => expect(parseVenezuelanAmount(' 600 ')).toBe(60000));
  test('basura devuelve null', () => {
    expect(parseVenezuelanAmount('')).toBeNull();
    expect(parseVenezuelanAmount('abc')).toBeNull();
    expect(parseVenezuelanAmount('1.234,50')).toBeNull(); // BNC nunca trae punto de miles
  });
});

describe('parseUsAmount (formato Zinli: $ con signo, coma de miles, punto decimal)', () => {
  test('negativo', () => expect(parseUsAmount('-$0.99')).toBe(-99));
  test('positivo sin signo', () => expect(parseUsAmount('$100.00')).toBe(10000));
  test('positivo con signo', () => expect(parseUsAmount('+$5.00')).toBe(500));
  test('miles con coma', () => expect(parseUsAmount('$1,000.00')).toBe(100000));
  test('sin símbolo de dólar', () => expect(parseUsAmount('84.20')).toBe(8420));
  test('basura devuelve null', () => {
    expect(parseUsAmount('')).toBeNull();
    expect(parseUsAmount('Pagaste')).toBeNull();
  });
});

describe('formatMoney', () => {
  test('VES con miles y coma decimal', () => expect(formatMoney(1351775, 'VES')).toBe('Bs 13.517,75'));
  test('VES negativo', () => expect(formatMoney(-350000, 'VES')).toBe('Bs -3.500,00'));
  test('VES millones', () => expect(formatMoney(123456789, 'VES')).toBe('Bs 1.234.567,89'));
  test('USD', () => expect(formatMoney(8420, 'USD')).toBe('$84.20'));
  test('USD negativo', () => expect(formatMoney(-500, 'USD')).toBe('-$5.00'));
  test('USD miles', () => expect(formatMoney(100000, 'USD')).toBe('$1,000.00'));
  test('cero', () => {
    expect(formatMoney(0, 'VES')).toBe('Bs 0,00');
    expect(formatMoney(0, 'USD')).toBe('$0.00');
  });
});
```

- [ ] **Paso 3: Verificar que falla.**

```powershell
npx vitest run src/domain/money.test.ts
```
Esperado: FAIL — `Cannot find module './money'` (o equivalente).

- [ ] **Paso 4: Implementar `src/domain/money.ts`:**

```ts
import type { Currency } from './types';

/** "13517,75" | "-3500" | "87,6999999999998" → céntimos enteros. null si no es un monto BNC válido. */
export function parseVenezuelanAmount(raw: string): number | null {
  const m = /^([+-]?)(\d+)(?:,(\d+))?$/.exec(raw.trim());
  if (!m) return null;
  const sign = m[1] === '-' ? -1 : 1;
  const intMinor = Number(m[2]) * 100;
  // La parte decimal puede traer artefactos float del export ("6999999999998"):
  // se interpreta como fracción y se redondea a céntimos.
  const fracMinor = m[3] ? Math.round(Number(`0.${m[3]}`) * 100) : 0;
  return sign * (intMinor + fracMinor);
}

/** "-$0.99" | "$1,000.00" | "+$5.00" | "84.20" → céntimos enteros. */
export function parseUsAmount(raw: string): number | null {
  const m = /^([+-]?)\$?\s?([\d,]+)\.(\d{2})$/.exec(raw.trim());
  if (!m) return null;
  const sign = m[1] === '-' ? -1 : 1;
  const units = Number(m[2].replace(/,/g, ''));
  if (Number.isNaN(units)) return null;
  return sign * (units * 100 + Number(m[3]));
}

function conMiles(unidades: number, separador: string): string {
  return unidades.toString().replace(/\B(?=(\d{3})+(?!\d))/g, separador);
}

/** Formato visual venezolano: `Bs 13.517,75` · `$84.20` · negativos: `Bs -3.500,00` / `-$5.00`. */
export function formatMoney(minor: number, currency: Currency): string {
  const signo = minor < 0 ? '-' : '';
  const abs = Math.abs(Math.round(minor));
  const unidades = Math.floor(abs / 100);
  const centimos = String(abs % 100).padStart(2, '0');
  if (currency === 'USD') return `${signo}$${conMiles(unidades, ',')}.${centimos}`;
  return `Bs ${signo}${conMiles(unidades, '.')},${centimos}`;
}
```

- [ ] **Paso 5: Verificar que pasa.**

```powershell
npx vitest run src/domain/money.test.ts
```
Esperado: PASS (todos los tests).

- [ ] **Paso 6: Fechas — test `src/domain/dates.test.ts`:**

```ts
import { isoFromDdMmYyyy, monthOf, todayIso } from './dates';

test('convierte dd/mm/yyyy a ISO', () => {
  expect(isoFromDdMmYyyy('06/07/2026')).toBe('2026-07-06');
  expect(isoFromDdMmYyyy('28/06/2026')).toBe('2026-06-28');
});
test('rechaza formatos inválidos', () => {
  expect(isoFromDdMmYyyy('1/1')).toBeNull();
  expect(isoFromDdMmYyyy('2026-07-06')).toBeNull();
  expect(isoFromDdMmYyyy('32/13/2026')).toBeNull();
});
test('monthOf extrae yyyy-mm', () => expect(monthOf('2026-07-06')).toBe('2026-07'));
test('todayIso tiene forma yyyy-mm-dd', () => expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/));
```

Verifica que falla: `npx vitest run src/domain/dates.test.ts` → FAIL.

- [ ] **Paso 7: Implementar `src/domain/dates.ts`:**

```ts
/** Fecha local del dispositivo como 'yyyy-mm-dd' (la app es 100% local; no usar UTC). */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** '06/07/2026' → '2026-07-06'. null si no es dd/mm/yyyy plausible. */
export function isoFromDdMmYyyy(s: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s.trim());
  if (!m) return null;
  const [, dd, mm, yyyy] = m;
  const dia = Number(dd), mes = Number(mm);
  if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null;
  return `${yyyy}-${mm}-${dd}`;
}

/** '2026-07-06' → '2026-07' */
export function monthOf(dateIso: string): string {
  return dateIso.slice(0, 7);
}
```

Verifica que pasa: `npx vitest run src/domain/dates.test.ts` → PASS.

- [ ] **Paso 8: Suite completa y commit.**

```powershell
npx vitest run
git add src/domain
git commit -m "Dominio: tipos, parseo/formato de dinero en céntimos y fechas

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 4: Tasas — tasa efectiva histórica y conversión

**Files:**
- Create: `src/domain/rates.ts`
- Test: `src/domain/rates.test.ts`

**Interfaces:**
- Consumes: `Rate`, `RateSource`, `Currency` de `./types`.
- Produces:
  - `effectiveRate(dateIso: string, activeSource: RateSource, rates: Rate[]): Rate | null`
  - `usdToVesMinor(usdMinor: number, vesPerUsd: number): number`
  - `vesToUsdMinor(vesMinor: number, vesPerUsd: number): number`
  - `convertMinor(amountMinor: number, from: Currency, to: Currency, vesPerUsd: number): number`

Reglas de `effectiveRate` (deterministas, en este orden):
1. Si existe un `Rate` con `source: 'MANUAL'` **exactamente en esa fecha**, gana siempre (el override manual de un día pisa a la fuente activa).
2. Si no, la entrada más reciente de la fuente activa con `date <= dateIso`. (Si la fuente activa es MANUAL: la MANUAL más reciente `<= dateIso`.)
3. Si no, la entrada más reciente de **cualquier** fuente con `date <= dateIso`.
4. Si no, la entrada más antigua de cualquier fuente con `date > dateIso` (mejor una tasa futura que nada).
5. Si no hay tasas: `null` (la UI muestra "sin tasa").

- [ ] **Paso 1: Test `src/domain/rates.test.ts`:**

```ts
import type { Rate } from './types';
import { effectiveRate, usdToVesMinor, vesToUsdMinor, convertMinor } from './rates';

const rates: Rate[] = [
  { date: '2026-07-01', source: 'BCV', vesPerUsd: 110.5 },
  { date: '2026-07-03', source: 'BCV', vesPerUsd: 111.02 },
  { date: '2026-07-03', source: 'BINANCE', vesPerUsd: 132.4 },
  { date: '2026-07-02', source: 'MANUAL', vesPerUsd: 120 },
];

describe('effectiveRate', () => {
  test('usa la fuente activa más reciente <= fecha', () => {
    expect(effectiveRate('2026-07-05', 'BCV', rates)?.vesPerUsd).toBe(111.02);
    expect(effectiveRate('2026-07-01', 'BCV', rates)?.vesPerUsd).toBe(110.5);
  });
  test('el override MANUAL exacto del día gana sobre la fuente activa', () => {
    expect(effectiveRate('2026-07-02', 'BCV', rates)?.vesPerUsd).toBe(120);
  });
  test('fuente activa BINANCE', () => {
    expect(effectiveRate('2026-07-04', 'BINANCE', rates)?.vesPerUsd).toBe(132.4);
  });
  test('fuente activa sin entrada <= fecha cae a cualquier fuente <= fecha', () => {
    expect(effectiveRate('2026-07-01', 'BINANCE', rates)?.vesPerUsd).toBe(110.5);
  });
  test('fecha anterior a todas: usa la más antigua disponible', () => {
    expect(effectiveRate('2026-06-01', 'BCV', rates)?.vesPerUsd).toBe(110.5);
  });
  test('sin tasas devuelve null', () => {
    expect(effectiveRate('2026-07-01', 'BCV', [])).toBeNull();
  });
});

describe('conversión', () => {
  test('usd → ves redondeando a céntimos', () => expect(usdToVesMinor(500, 111.02)).toBe(55510));
  test('ves → usd redondeando a céntimos', () => expect(vesToUsdMinor(55510, 111.02)).toBe(500));
  test('redondeo no sesgado', () => expect(vesToUsdMinor(100, 111.02)).toBe(1)); // 0,9º céntimo → 1
  test('convertMinor misma moneda es identidad', () => expect(convertMinor(1234, 'USD', 'USD', 111)).toBe(1234));
  test('convertMinor cruza monedas', () => {
    expect(convertMinor(500, 'USD', 'VES', 111.02)).toBe(55510);
    expect(convertMinor(55510, 'VES', 'USD', 111.02)).toBe(500);
  });
});
```

- [ ] **Paso 2: Verificar que falla.** `npx vitest run src/domain/rates.test.ts` → FAIL (módulo no existe).

- [ ] **Paso 3: Implementar `src/domain/rates.ts`:**

```ts
import type { Currency, Rate, RateSource } from './types';

/** Tasa vigente para una fecha según la fuente activa. Ver reglas numeradas en el plan. */
export function effectiveRate(dateIso: string, activeSource: RateSource, rates: Rate[]): Rate | null {
  const manualExacta = rates.find((r) => r.source === 'MANUAL' && r.date === dateIso);
  if (manualExacta) return manualExacta;

  const masReciente = (candidatas: Rate[]): Rate | null =>
    candidatas.length === 0
      ? null
      : candidatas.reduce((a, b) => (b.date > a.date ? b : a));

  const deFuente = masReciente(rates.filter((r) => r.source === activeSource && r.date <= dateIso));
  if (deFuente) return deFuente;

  const cualquiera = masReciente(rates.filter((r) => r.date <= dateIso));
  if (cualquiera) return cualquiera;

  const futuras = rates.filter((r) => r.date > dateIso);
  if (futuras.length > 0) return futuras.reduce((a, b) => (b.date < a.date ? b : a));

  return null;
}

export function usdToVesMinor(usdMinor: number, vesPerUsd: number): number {
  return Math.round(usdMinor * vesPerUsd);
}

export function vesToUsdMinor(vesMinor: number, vesPerUsd: number): number {
  return Math.round(vesMinor / vesPerUsd);
}

export function convertMinor(amountMinor: number, from: Currency, to: Currency, vesPerUsd: number): number {
  if (from === to) return amountMinor;
  return from === 'USD' ? usdToVesMinor(amountMinor, vesPerUsd) : vesToUsdMinor(amountMinor, vesPerUsd);
}
```

- [ ] **Paso 4: Verificar que pasa.** `npx vitest run src/domain/rates.test.ts` → PASS.

- [ ] **Paso 5: Commit.**

```powershell
npx vitest run
git add src/domain/rates.ts src/domain/rates.test.ts
git commit -m "Dominio: tasa efectiva con histórico y conversión Bs/USD

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 5: Base de datos Dexie — schema, seeds y settings

**Files:**
- Create: `src/db/db.ts`, `src/test/utils.ts`
- Test: `src/db/db.test.ts`

**Interfaces:**
- Consumes: entidades de `src/domain/types.ts`.
- Produces:
  - `class CuentasDB extends Dexie` con tablas `accounts, transactions, categories, rates, rules, settings`.
  - `export const db = new CuentasDB()` (singleton de la app).
  - `seedIfEmpty(d: CuentasDB): Promise<void>` — idempotente.
  - `getSettings(d: CuentasDB): Promise<Settings>` / `saveSettings(d: CuentasDB, patch: Partial<Omit<Settings,'id'>>): Promise<void>`.
  - Ids fijos de seeds que otras tareas referencian: cuentas `acc-bnc`, `acc-zinli`, `acc-binance`, `acc-efectivo`, `acc-efectivo-ahorro`; categorías `cat-supermercado`, `cat-comida-fuera`, `cat-transporte`, `cat-salud`, `cat-servicios`, `cat-comisiones`, `cat-hogar`, `cat-ocio`, `cat-suscripciones`, `cat-ropa`, `cat-otros-gastos`, `cat-pago-movil-recibido`, `cat-transferencia-recibida`, `cat-sueldo`, `cat-otros-ingresos`.
  - `limpiarDb(): Promise<void>` en `src/test/utils.ts` (resetea el singleton entre tests).

- [ ] **Paso 1: Test `src/db/db.test.ts`:**

```ts
import { CuentasDB, seedIfEmpty, getSettings, saveSettings } from './db';

function nuevaDb() {
  return new CuentasDB(`test-${crypto.randomUUID()}`);
}

test('el seed crea las 5 cuentas y 15 categorías de la spec', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  const cuentas = await d.accounts.toArray();
  expect(cuentas.map((c) => c.id).sort()).toEqual([
    'acc-binance', 'acc-bnc', 'acc-efectivo', 'acc-efectivo-ahorro', 'acc-zinli',
  ]);
  const bnc = await d.accounts.get('acc-bnc');
  expect(bnc).toMatchObject({ currency: 'VES', kind: 'bank', isSavings: false });
  const binance = await d.accounts.get('acc-binance');
  expect(binance).toMatchObject({ currency: 'USD', kind: 'snapshot', isSavings: true });
  expect(await d.categories.count()).toBe(15);
  const comisiones = await d.categories.get('cat-comisiones');
  expect(comisiones?.kind).toBe('expense');
});

test('el seed es idempotente', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  await d.accounts.update('acc-bnc', { name: 'BNC renombrado' });
  await seedIfEmpty(d);
  expect(await d.accounts.count()).toBe(5);
  expect((await d.accounts.get('acc-bnc'))?.name).toBe('BNC renombrado');
});

test('settings: defecto USD/BCV y patch parcial', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  const s = await getSettings(d);
  expect(s).toMatchObject({ id: 'main', viewCurrency: 'USD', rateSource: 'BCV' });
  await saveSettings(d, { lastAccountId: 'acc-efectivo' });
  expect((await getSettings(d)).lastAccountId).toBe('acc-efectivo');
  expect((await getSettings(d)).viewCurrency).toBe('USD');
});

test('rates admite una entrada por fecha+fuente (clave compuesta)', async () => {
  const d = nuevaDb();
  await d.rates.put({ date: '2026-07-06', source: 'BCV', vesPerUsd: 111 });
  await d.rates.put({ date: '2026-07-06', source: 'BCV', vesPerUsd: 112 }); // pisa, no duplica
  await d.rates.put({ date: '2026-07-06', source: 'BINANCE', vesPerUsd: 130 });
  expect(await d.rates.count()).toBe(2);
});
```

- [ ] **Paso 2: Verificar que falla.** `npx vitest run src/db/db.test.ts` → FAIL.

- [ ] **Paso 3: Implementar `src/db/db.ts`:**

```ts
import Dexie, { type Table } from 'dexie';
import type { Account, Category, Rate, Rule, Settings, Transaction } from '../domain/types';

export class CuentasDB extends Dexie {
  accounts!: Table<Account, string>;
  transactions!: Table<Transaction, string>;
  categories!: Table<Category, string>;
  rates!: Table<Rate, [string, string]>;
  rules!: Table<Rule, string>;
  settings!: Table<Settings, string>;

  constructor(name = 'cuentas-claras') {
    super(name);
    this.version(1).stores({
      accounts: 'id',
      transactions: 'id, date, accountId, categoryId, importKey',
      categories: 'id',
      rates: '[date+source], date',
      rules: 'id',
      settings: 'id',
    });
  }
}

export const db = new CuentasDB();

const CUENTAS_SEED: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-zinli', name: 'Zinli', currency: 'USD', kind: 'wallet', isSavings: false, archived: false },
  { id: 'acc-binance', name: 'Binance', currency: 'USD', kind: 'snapshot', isSavings: true, archived: false },
  { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
  { id: 'acc-efectivo-ahorro', name: 'Efectivo ahorro', currency: 'USD', kind: 'cash', isSavings: true, archived: false },
];

const CATEGORIAS_SEED: Category[] = [
  { id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#16a34a', kind: 'expense' },
  { id: 'cat-comida-fuera', name: 'Comida fuera', icon: '🍔', color: '#f59e0b', kind: 'expense' },
  { id: 'cat-transporte', name: 'Transporte', icon: '🚌', color: '#3b82f6', kind: 'expense' },
  { id: 'cat-salud', name: 'Salud', icon: '💊', color: '#ef4444', kind: 'expense' },
  { id: 'cat-servicios', name: 'Servicios', icon: '💡', color: '#eab308', kind: 'expense' },
  { id: 'cat-comisiones', name: 'Comisiones bancarias', icon: '🏦', color: '#64748b', kind: 'expense' },
  { id: 'cat-hogar', name: 'Hogar', icon: '🏠', color: '#8b5cf6', kind: 'expense' },
  { id: 'cat-ocio', name: 'Ocio/Juegos', icon: '🎮', color: '#ec4899', kind: 'expense' },
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#06b6d4', kind: 'expense' },
  { id: 'cat-ropa', name: 'Ropa', icon: '👕', color: '#a16207', kind: 'expense' },
  { id: 'cat-otros-gastos', name: 'Otros gastos', icon: '📦', color: '#6b7280', kind: 'expense' },
  { id: 'cat-pago-movil-recibido', name: 'Pago móvil recibido', icon: '📲', color: '#16a34a', kind: 'income' },
  { id: 'cat-transferencia-recibida', name: 'Transferencia recibida', icon: '🔁', color: '#0ea5e9', kind: 'income' },
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#22c55e', kind: 'income' },
  { id: 'cat-otros-ingresos', name: 'Otros ingresos', icon: '➕', color: '#94a3b8', kind: 'income' },
];

const SETTINGS_SEED: Settings = { id: 'main', viewCurrency: 'USD', rateSource: 'BCV', lastAccountId: 'acc-bnc' };

/** Crea cuentas, categorías y settings iniciales solo si la tabla está vacía. */
export async function seedIfEmpty(d: CuentasDB): Promise<void> {
  await d.transaction('rw', d.accounts, d.categories, d.settings, async () => {
    if ((await d.accounts.count()) === 0) await d.accounts.bulkAdd(CUENTAS_SEED);
    if ((await d.categories.count()) === 0) await d.categories.bulkAdd(CATEGORIAS_SEED);
    if ((await d.settings.count()) === 0) await d.settings.add(SETTINGS_SEED);
  });
}

export async function getSettings(d: CuentasDB): Promise<Settings> {
  return (await d.settings.get('main')) ?? SETTINGS_SEED;
}

export async function saveSettings(d: CuentasDB, patch: Partial<Omit<Settings, 'id'>>): Promise<void> {
  const actual = await getSettings(d);
  await d.settings.put({ ...actual, ...patch });
}
```

- [ ] **Paso 4: Crear `src/test/utils.ts`** (lo usan los tests de componentes que comparten el singleton):

```ts
import { db, seedIfEmpty } from '../db/db';

/** Borra y recrea la base singleton con seeds. Llamar en beforeEach de tests de UI. */
export async function limpiarDb(): Promise<void> {
  await db.delete();
  await db.open();
  await seedIfEmpty(db);
}
```

- [ ] **Paso 5: Verificar que pasa.** `npx vitest run src/db/db.test.ts` → PASS.

- [ ] **Paso 6: Commit.**

```powershell
npx vitest run
git add src/db src/test/utils.ts
git commit -m "Base Dexie: schema v1, seeds de cuentas/categorías y settings

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 6: Saldos por cuenta y patrimonio

**Files:**
- Create: `src/domain/balances.ts`
- Test: `src/domain/balances.test.ts`

**Interfaces:**
- Consumes: `Account`, `Transaction`, `Currency` de `./types`; `vesToUsdMinor`, `usdToVesMinor` de `./rates`.
- Produces:
  - `accountBalanceMinor(accountId: string, txs: Transaction[]): number`
  - `interface PatrimonioLinea { account: Account; balanceMinor: number; balanceUsdMinor: number | null }`
  - `interface Patrimonio { lines: PatrimonioLinea[]; disposicionUsdMinor: number | null; ahorroUsdMinor: number | null; totalUsdMinor: number | null }`
  - `patrimonio(accounts: Account[], txs: Transaction[], vesPerUsd: number | null): Patrimonio` — excluye cuentas `archived`; si falta la tasa, los USD de cuentas VES y los totales que dependan de ellas quedan `null`.

Semántica del saldo (deriva del invariante de `Transaction`):
- `expense`: resta `amountMinor` a `accountId`.
- `income`: suma.
- `transfer`: resta `amountMinor` a `accountId` y suma `toAmountMinor ?? amountMinor` a `toAccountId`.
- `adjustment`: suma el delta con signo.

- [ ] **Paso 1: Test `src/domain/balances.test.ts`:**

```ts
import type { Account, Transaction } from './types';
import { accountBalanceMinor, patrimonio } from './balances';

const cuentas: Account[] = [
  { id: 'bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
  { id: 'binance', name: 'Binance', currency: 'USD', kind: 'snapshot', isSavings: true, archived: false },
  { id: 'vieja', name: 'Archivada', currency: 'USD', kind: 'cash', isSavings: false, archived: true },
];

const tx = (t: Partial<Transaction>): Transaction => ({
  id: crypto.randomUUID(), type: 'expense', accountId: 'bnc', amountMinor: 0,
  date: '2026-07-01', source: 'manual', ...t,
});

const txs: Transaction[] = [
  tx({ type: 'income', accountId: 'bnc', amountMinor: 500000 }),          // +5.000,00 Bs
  tx({ type: 'expense', accountId: 'bnc', amountMinor: 150000 }),         // −1.500,00 Bs
  tx({ type: 'adjustment', accountId: 'binance', amountMinor: 25000 }),   // +$250.00
  tx({ type: 'transfer', accountId: 'efectivo', toAccountId: 'bnc', amountMinor: 1000, toAmountMinor: 111000, rateUsed: 111 }), // $10 → Bs 1.110
  tx({ type: 'income', accountId: 'efectivo', amountMinor: 5000 }),       // +$50.00
];

describe('accountBalanceMinor', () => {
  test('suma ingresos, resta gastos, aplica ambos lados de transferencias', () => {
    expect(accountBalanceMinor('bnc', txs)).toBe(500000 - 150000 + 111000); // 461000
    expect(accountBalanceMinor('efectivo', txs)).toBe(-1000 + 5000);        // 4000
    expect(accountBalanceMinor('binance', txs)).toBe(25000);
  });
  test('cuenta sin movimientos: 0', () => expect(accountBalanceMinor('vieja', [])).toBe(0));
  test('transferencia sin toAmountMinor usa amountMinor (misma moneda)', () => {
    const t = [tx({ type: 'transfer', accountId: 'efectivo', toAccountId: 'binance', amountMinor: 700 })];
    expect(accountBalanceMinor('efectivo', t)).toBe(-700);
    expect(accountBalanceMinor('binance', t)).toBe(700);
  });
});

describe('patrimonio', () => {
  test('consolida en USD y separa disposición/ahorro', () => {
    const p = patrimonio(cuentas, txs, 111);
    expect(p.lines).toHaveLength(3); // excluye la archivada
    const bnc = p.lines.find((l) => l.account.id === 'bnc')!;
    expect(bnc.balanceMinor).toBe(461000);
    expect(bnc.balanceUsdMinor).toBe(Math.round(461000 / 111)); // 4153
    expect(p.ahorroUsdMinor).toBe(25000);
    expect(p.disposicionUsdMinor).toBe(4153 + 4000);
    expect(p.totalUsdMinor).toBe(4153 + 4000 + 25000);
  });
  test('sin tasa: cuentas VES y totales dependientes quedan null, USD puras sobreviven', () => {
    const p = patrimonio(cuentas, txs, null);
    expect(p.lines.find((l) => l.account.id === 'bnc')!.balanceUsdMinor).toBeNull();
    expect(p.lines.find((l) => l.account.id === 'efectivo')!.balanceUsdMinor).toBe(4000);
    expect(p.disposicionUsdMinor).toBeNull(); // incluye una cuenta VES sin tasa
    expect(p.ahorroUsdMinor).toBe(25000);     // solo cuentas USD → sí se puede
    expect(p.totalUsdMinor).toBeNull();
  });
});
```

- [ ] **Paso 2: Verificar que falla.** `npx vitest run src/domain/balances.test.ts` → FAIL.

- [ ] **Paso 3: Implementar `src/domain/balances.ts`:**

```ts
import type { Account, Transaction } from './types';
import { vesToUsdMinor } from './rates';

export function accountBalanceMinor(accountId: string, txs: Transaction[]): number {
  let saldo = 0;
  for (const t of txs) {
    if (t.accountId === accountId) {
      if (t.type === 'income') saldo += t.amountMinor;
      else if (t.type === 'expense') saldo -= t.amountMinor;
      else if (t.type === 'transfer') saldo -= t.amountMinor;
      else saldo += t.amountMinor; // adjustment: delta con signo
    }
    if (t.type === 'transfer' && t.toAccountId === accountId) {
      saldo += t.toAmountMinor ?? t.amountMinor;
    }
  }
  return saldo;
}

export interface PatrimonioLinea {
  account: Account;
  balanceMinor: number;
  balanceUsdMinor: number | null;
}

export interface Patrimonio {
  lines: PatrimonioLinea[];
  disposicionUsdMinor: number | null;
  ahorroUsdMinor: number | null;
  totalUsdMinor: number | null;
}

/** Patrimonio consolidado en USD. vesPerUsd null → sin tasa: lo no convertible queda null. */
export function patrimonio(accounts: Account[], txs: Transaction[], vesPerUsd: number | null): Patrimonio {
  const activas = accounts.filter((a) => !a.archived);
  const lines: PatrimonioLinea[] = activas.map((account) => {
    const balanceMinor = accountBalanceMinor(account.id, txs);
    const balanceUsdMinor =
      account.currency === 'USD'
        ? balanceMinor
        : vesPerUsd === null
          ? null
          : vesToUsdMinor(balanceMinor, vesPerUsd);
    return { account, balanceMinor, balanceUsdMinor };
  });

  const suma = (grupo: PatrimonioLinea[]): number | null =>
    grupo.some((l) => l.balanceUsdMinor === null)
      ? null
      : grupo.reduce((acc, l) => acc + (l.balanceUsdMinor as number), 0);

  const disposicion = lines.filter((l) => !l.account.isSavings);
  const ahorro = lines.filter((l) => l.account.isSavings);
  const disposicionUsdMinor = suma(disposicion);
  const ahorroUsdMinor = suma(ahorro);
  const totalUsdMinor =
    disposicionUsdMinor === null || ahorroUsdMinor === null ? null : disposicionUsdMinor + ahorroUsdMinor;

  return { lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor };
}
```

- [ ] **Paso 4: Verificar que pasa.** `npx vitest run src/domain/balances.test.ts` → PASS.

- [ ] **Paso 5: Commit.**

```powershell
npx vitest run
git add src/domain/balances.ts src/domain/balances.test.ts
git commit -m "Dominio: saldos por cuenta y patrimonio disposición/ahorro en USD

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Fase 2 — Shell de UI y registro rápido

### Tarea 7: Shell de la app, navegación por hash y estilos

**Files:**
- Create: `src/ui/useHashRoute.ts`, `src/ui/Nav.tsx`, `src/ui/Toast.tsx`, `src/ui/paginas/DashboardPage.tsx`, `src/ui/paginas/NuevoPage.tsx`, `src/ui/paginas/MovimientosPage.tsx`, `src/ui/paginas/ImportarPage.tsx`, `src/ui/paginas/AjustesPage.tsx`
- Modify: `src/ui/App.tsx`, `src/ui/App.test.tsx`, `src/ui/estilos.css` (reemplazar completo)

**Interfaces:**
- Consumes: `db`, `seedIfEmpty` de `src/db/db.ts`.
- Produces:
  - `useHashRoute(): { path: string; params: URLSearchParams }` y `navigate(to: string): void` (asigna `window.location.hash`).
  - `avisar(mensaje: string): void` y componente `<Toast />`.
  - Rutas: `/` dashboard · `/nuevo` registro · `/movimientos` · `/importar` · `/ajustes`.
  - Clases CSS que TODAS las tareas de UI posteriores reutilizan: `.tarjeta .boton .boton.secundario .boton.peligro .segmentado .monto-display .teclado .grid-categorias .fila-mov .monto-pos .monto-neg .banner(.aviso/.error/.ok) .modal-fondo .modal .fila-form .tabla .progreso .chip`.

- [ ] **Paso 1: Test primero** — reemplazar `src/ui/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { App } from './App';
import { limpiarDb } from '../test/utils';

beforeEach(async () => {
  await limpiarDb();
  window.location.hash = '';
});

test('arranca en el dashboard y navega por hash', async () => {
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Inicio' })).toBeInTheDocument();
  window.location.hash = '#/ajustes';
  window.dispatchEvent(new HashChangeEvent('hashchange')); // jsdom no siempre lo emite solo
  expect(await screen.findByRole('heading', { name: 'Ajustes' })).toBeInTheDocument();
});

test('la barra inferior tiene las 4 secciones y el botón +', async () => {
  render(<App />);
  await screen.findByRole('heading', { name: 'Inicio' });
  expect(screen.getByText('Movs')).toBeInTheDocument();
  expect(screen.getByText('Importar')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Nuevo' })).toBeInTheDocument();
});
```

Verifica que falla: `npx vitest run src/ui/App.test.tsx` → FAIL.

- [ ] **Paso 2: Implementar la navegación** — `src/ui/useHashRoute.ts`:

```ts
import { useEffect, useState } from 'react';

export function navigate(to: string): void {
  window.location.hash = to;
}

export function useHashRoute(): { path: string; params: URLSearchParams } {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  const limpio = hash.startsWith('#') ? hash.slice(1) : hash;
  const [path = '/', query = ''] = limpio.split('?');
  return { path: path || '/', params: new URLSearchParams(query) };
}
```

`src/ui/Nav.tsx`:

```tsx
import { navigate } from './useHashRoute';

export function Nav({ path }: { path: string }) {
  const enlace = (ruta: string, icono: string, etiqueta: string) => (
    <a href={`#${ruta}`} className={path === ruta ? 'activo' : ''}>
      <span className="icono">{icono}</span>
      {etiqueta}
    </a>
  );
  return (
    <nav className="nav">
      {enlace('/', '📊', 'Inicio')}
      {enlace('/movimientos', '📋', 'Movs')}
      <div>
        <button className="fab" aria-label="Nuevo" onClick={() => navigate('/nuevo?tipo=gasto')}>+</button>
      </div>
      {enlace('/importar', '📥', 'Importar')}
      {enlace('/ajustes', '⚙️', 'Ajustes')}
    </nav>
  );
}
```

`src/ui/Toast.tsx`:

```tsx
import { useEffect, useState } from 'react';

export function avisar(mensaje: string): void {
  window.dispatchEvent(new CustomEvent('toast', { detail: mensaje }));
}

export function Toast() {
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const on = (e: Event) => {
      setMsg((e as CustomEvent<string>).detail);
      clearTimeout(timer);
      timer = setTimeout(() => setMsg(null), 2500);
    };
    window.addEventListener('toast', on);
    return () => {
      window.removeEventListener('toast', on);
      clearTimeout(timer);
    };
  }, []);
  if (!msg) return null;
  return <div className="toast" role="status">{msg}</div>;
}
```

- [ ] **Paso 3: Páginas stub** (las tareas siguientes las rellenan; el `<h2>` es lo que asertan los tests de navegación):

```tsx
// src/ui/paginas/DashboardPage.tsx
export function DashboardPage() { return <h2>Inicio</h2>; }
// src/ui/paginas/NuevoPage.tsx
export function NuevoPage({ params }: { params: URLSearchParams }) { void params; return <h2>Nuevo</h2>; }
// src/ui/paginas/MovimientosPage.tsx
export function MovimientosPage() { return <h2>Movimientos</h2>; }
// src/ui/paginas/ImportarPage.tsx
export function ImportarPage() { return <h2>Importar</h2>; }
// src/ui/paginas/AjustesPage.tsx
export function AjustesPage() { return <h2>Ajustes</h2>; }
```

`src/ui/App.tsx` (reemplazar):

```tsx
import { useEffect, useState } from 'react';
import { db, seedIfEmpty } from '../db/db';
import { useHashRoute } from './useHashRoute';
import { Nav } from './Nav';
import { Toast } from './Toast';
import { DashboardPage } from './paginas/DashboardPage';
import { NuevoPage } from './paginas/NuevoPage';
import { MovimientosPage } from './paginas/MovimientosPage';
import { ImportarPage } from './paginas/ImportarPage';
import { AjustesPage } from './paginas/AjustesPage';

export function App() {
  const [listo, setListo] = useState(false);
  const { path, params } = useHashRoute();

  useEffect(() => {
    seedIfEmpty(db).then(() => setListo(true));
  }, []);

  if (!listo) return null;
  return (
    <div className="app">
      <main className="contenido">
        {path === '/' && <DashboardPage />}
        {path === '/nuevo' && <NuevoPage params={params} />}
        {path === '/movimientos' && <MovimientosPage />}
        {path === '/importar' && <ImportarPage />}
        {path === '/ajustes' && <AjustesPage />}
      </main>
      <Nav path={path} />
      <Toast />
    </div>
  );
}
```

- [ ] **Paso 4: Reemplazar `src/ui/estilos.css` completo** (hoja única de la app; las tareas posteriores NO añaden CSS salvo que un paso lo diga):

```css
:root {
  --fondo: #f6f7f9;
  --tarjeta: #ffffff;
  --primario: #0e7490;
  --gasto: #dc2626;
  --ingreso: #16a34a;
  --texto: #111827;
  --texto2: #6b7280;
  --borde: #e5e7eb;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  background: var(--fondo);
  color: var(--texto);
}
h1, h2, h3 { margin: 8px 0; }
button { font: inherit; cursor: pointer; }

.app { max-width: 480px; margin: 0 auto; min-height: 100vh; }
.contenido { padding: 12px 12px 92px; }

.nav {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 100%; max-width: 480px;
  display: grid; grid-template-columns: repeat(5, 1fr); align-items: center;
  background: var(--tarjeta); border-top: 1px solid var(--borde);
  padding: 4px 0 max(4px, env(safe-area-inset-bottom));
}
.nav a { text-align: center; text-decoration: none; color: var(--texto2); font-size: 11px; }
.nav a.activo { color: var(--primario); font-weight: 600; }
.nav .icono { display: block; font-size: 20px; }
.fab {
  width: 54px; height: 54px; margin: -22px auto 0; display: block;
  border-radius: 50%; border: none; background: var(--primario); color: #fff;
  font-size: 30px; line-height: 1; box-shadow: 0 2px 8px rgb(0 0 0 / 0.25);
}

.tarjeta { background: var(--tarjeta); border: 1px solid var(--borde); border-radius: 12px; padding: 14px; margin-bottom: 12px; }
.boton { background: var(--primario); color: #fff; border: none; border-radius: 10px; padding: 12px 16px; font-size: 16px; width: 100%; }
.boton.secundario { background: #e2e8f0; color: var(--texto); }
.boton.peligro { background: var(--gasto); }
.boton:disabled { opacity: 0.5; cursor: default; }

input, select, textarea { font: inherit; padding: 10px; border: 1px solid var(--borde); border-radius: 8px; width: 100%; background: #fff; }
label { font-size: 13px; color: var(--texto2); display: block; margin: 10px 0 4px; }
.fila-form { display: flex; gap: 8px; }
.fila-form > * { flex: 1; }

.segmentado { display: flex; gap: 4px; background: #e2e8f0; border-radius: 10px; padding: 4px; margin-bottom: 12px; }
.segmentado button { flex: 1; border: none; background: transparent; padding: 8px 2px; border-radius: 8px; font-size: 14px; }
.segmentado button.activo { background: #fff; font-weight: 600; }

.monto-display { text-align: center; font-size: 40px; font-weight: 700; padding: 12px 0; min-height: 66px; }
.monto-display.gasto { color: var(--gasto); }
.monto-display.ingreso { color: var(--ingreso); }

.teclado { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
.teclado button { padding: 14px 0; font-size: 22px; border: 1px solid var(--borde); border-radius: 10px; background: #fff; }

.grid-categorias { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 8px; }
.grid-categorias button {
  border: 1px solid var(--borde); border-radius: 10px; background: #fff;
  padding: 8px 2px; font-size: 11px; display: flex; flex-direction: column; align-items: center; gap: 2px;
}
.grid-categorias .icono { font-size: 22px; }

.fila-mov {
  display: flex; align-items: center; gap: 10px; padding: 10px 4px;
  border: none; border-bottom: 1px solid var(--borde); width: 100%;
  background: none; text-align: left; font-size: 14px;
}
.fila-mov .detalle { flex: 1; min-width: 0; }
.fila-mov .nota { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fila-mov .meta { font-size: 12px; color: var(--texto2); }
.monto-pos { color: var(--ingreso); font-weight: 600; white-space: nowrap; }
.monto-neg { color: var(--gasto); font-weight: 600; white-space: nowrap; }

.toast {
  position: fixed; bottom: 96px; left: 50%; transform: translateX(-50%);
  background: #111827; color: #fff; padding: 10px 18px; border-radius: 999px;
  font-size: 14px; z-index: 50; white-space: nowrap;
}

.banner { border-radius: 10px; padding: 10px 12px; font-size: 14px; margin: 8px 0; }
.banner.aviso { background: #fef3c7; color: #92400e; }
.banner.error { background: #fee2e2; color: #991b1b; }
.banner.ok { background: #dcfce7; color: #166534; }

.modal-fondo { position: fixed; inset: 0; background: rgb(0 0 0 / 0.4); display: flex; align-items: flex-end; justify-content: center; z-index: 40; }
.modal { background: #fff; border-radius: 16px 16px 0 0; padding: 16px; width: 100%; max-width: 480px; max-height: 85vh; overflow: auto; }

.tabla { width: 100%; border-collapse: collapse; font-size: 13px; }
.tabla th, .tabla td { border-bottom: 1px solid var(--borde); padding: 6px 4px; text-align: left; vertical-align: top; }

.progreso { background: #e5e7eb; border-radius: 999px; height: 8px; overflow: hidden; }
.progreso > div { height: 100%; background: var(--primario); }
.chip { display: inline-block; background: #e2e8f0; border-radius: 999px; padding: 2px 10px; font-size: 12px; }
```

- [ ] **Paso 5: Verificar.** `npx vitest run` → todo verde. `npm run dev` → navega con la barra inferior entre las 5 rutas; el hash cambia en la URL.

- [ ] **Paso 6: Commit.**

```powershell
git add src/ui
git commit -m "Shell de la app: navegación por hash, barra inferior, toast y estilos base

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 8: Registro rápido (prioridad UX #1)

Flujo objetivo: FAB `+` (toque 1) → dígitos en teclado grande → toque en la categoría (toque 2) **guarda directamente** y vuelve al inicio. Fecha = hoy, cuenta = última usada, nota opcional.

**Files:**
- Create: `src/domain/builders.ts`, `src/ui/componentes/Teclado.tsx`, `src/ui/componentes/CategoriaGrid.tsx`
- Modify: `src/ui/paginas/NuevoPage.tsx` (reemplazar el stub)
- Test: `src/domain/builders.test.ts`, `src/ui/paginas/NuevoPage.test.tsx`

**Interfaces:**
- Consumes: `parseVenezuelanAmount`, `formatMoney` (Tarea 3); `todayIso` (Tarea 3); `db`, `getSettings`, `saveSettings` (Tarea 5); `avisar`, `navigate` (Tarea 7).
- Produces:
  - `buildManualTx(opts: { type: 'expense' | 'income'; accountId: string; amountMinor: number; categoryId: string; dateIso: string; note?: string }): Transaction` — lanza `Error('Monto inválido')` si el monto no es entero positivo.
  - `<Teclado onTecla={(t: string) => void} />` — teclas `1-9`, `,`, `0`, `⌫`.
  - `<CategoriaGrid categorias={Category[]} onElegir={(id: string) => void} />`.
  - `NuevoPage` lee `params.get('tipo')` (`'gasto' | 'ingreso'`) — lo usan los shortcuts del manifest (Tarea 24).

- [ ] **Paso 1: Test del builder** — `src/domain/builders.test.ts`:

```ts
import { buildManualTx } from './builders';

test('buildManualTx crea un gasto manual completo', () => {
  const tx = buildManualTx({
    type: 'expense', accountId: 'acc-bnc', amountMinor: 3550,
    categoryId: 'cat-supermercado', dateIso: '2026-07-07', note: 'pan',
  });
  expect(tx).toMatchObject({
    type: 'expense', accountId: 'acc-bnc', amountMinor: 3550,
    categoryId: 'cat-supermercado', date: '2026-07-07', note: 'pan', source: 'manual',
  });
  expect(tx.id).toMatch(/^[0-9a-f-]{36}$/);
});

test('rechaza montos no positivos o no enteros', () => {
  const base = { type: 'expense' as const, accountId: 'a', categoryId: 'c', dateIso: '2026-07-07' };
  expect(() => buildManualTx({ ...base, amountMinor: 0 })).toThrow('Monto inválido');
  expect(() => buildManualTx({ ...base, amountMinor: -5 })).toThrow('Monto inválido');
  expect(() => buildManualTx({ ...base, amountMinor: 10.5 })).toThrow('Monto inválido');
});
```

Verifica que falla, luego implementa `src/domain/builders.ts`:

```ts
import type { Transaction } from './types';

export function buildManualTx(opts: {
  type: 'expense' | 'income';
  accountId: string;
  amountMinor: number;
  categoryId: string;
  dateIso: string;
  note?: string;
}): Transaction {
  if (!Number.isInteger(opts.amountMinor) || opts.amountMinor <= 0) throw new Error('Monto inválido');
  return {
    id: crypto.randomUUID(),
    type: opts.type,
    accountId: opts.accountId,
    amountMinor: opts.amountMinor,
    date: opts.dateIso,
    categoryId: opts.categoryId,
    note: opts.note,
    source: 'manual',
  };
}
```

`npx vitest run src/domain/builders.test.ts` → PASS.

- [ ] **Paso 2: Test del componente** — `src/ui/paginas/NuevoPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NuevoPage } from './NuevoPage';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';

beforeEach(async () => { await limpiarDb(); });

test('registra un gasto: dígitos + toque en categoría', async () => {
  const user = userEvent.setup();
  render(<NuevoPage params={new URLSearchParams('tipo=gasto')} />);
  await screen.findByText('Supermercado');
  await user.click(screen.getByRole('button', { name: '3' }));
  await user.click(screen.getByRole('button', { name: '5' }));
  await user.click(screen.getByRole('button', { name: ',' }));
  await user.click(screen.getByRole('button', { name: '5' }));
  await user.click(screen.getByText('Supermercado'));
  await waitFor(async () => expect(await db.transactions.count()).toBe(1));
  const tx = (await db.transactions.toArray())[0];
  expect(tx).toMatchObject({
    type: 'expense', amountMinor: 3550, accountId: 'acc-bnc',
    categoryId: 'cat-supermercado', source: 'manual',
  });
});

test('sin monto, tocar categoría no guarda nada', async () => {
  const user = userEvent.setup();
  render(<NuevoPage params={new URLSearchParams('tipo=gasto')} />);
  await user.click(await screen.findByText('Supermercado'));
  expect(await db.transactions.count()).toBe(0);
});

test('el modo ingreso muestra categorías de ingreso', async () => {
  const user = userEvent.setup();
  render(<NuevoPage params={new URLSearchParams('tipo=gasto')} />);
  await user.click(await screen.findByRole('button', { name: 'Ingreso' }));
  expect(await screen.findByText('Sueldo/Pagos')).toBeInTheDocument();
  expect(screen.queryByText('Supermercado')).not.toBeInTheDocument();
});

test('la tecla ⌫ borra y la coma no se duplica', async () => {
  const user = userEvent.setup();
  render(<NuevoPage params={new URLSearchParams('tipo=gasto')} />);
  await screen.findByText('Supermercado');
  for (const tecla of ['1', ',', ',', '5', '0']) {
    await user.click(screen.getByRole('button', { name: tecla === ',' ? 'coma' : tecla }));
  }
  await user.click(screen.getByRole('button', { name: 'borrar' }));
  // entrada queda "1,5" → Bs -1,50 en el display
  expect(screen.getByText(/1,50/)).toBeInTheDocument();
});
```

Verifica que falla: `npx vitest run src/ui/paginas/NuevoPage.test.tsx` → FAIL.

- [ ] **Paso 3: Implementar componentes.** `src/ui/componentes/Teclado.tsx`:

```tsx
const TECLAS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', ',', '0', '⌫'] as const;

export function Teclado({ onTecla }: { onTecla: (t: string) => void }) {
  return (
    <div className="teclado">
      {TECLAS.map((t) => (
        <button
          key={t}
          type="button"
          aria-label={t === '⌫' ? 'borrar' : t === ',' ? 'coma' : t}
          onClick={() => onTecla(t)}
        >
          {t}
        </button>
      ))}
    </div>
  );
}
```

`src/ui/componentes/CategoriaGrid.tsx`:

```tsx
import type { Category } from '../../domain/types';

export function CategoriaGrid({ categorias, onElegir }: { categorias: Category[]; onElegir: (id: string) => void }) {
  return (
    <div className="grid-categorias">
      {categorias.map((c) => (
        <button key={c.id} type="button" onClick={() => onElegir(c.id)}>
          <span className="icono">{c.icon}</span>
          {c.name}
        </button>
      ))}
    </div>
  );
}
```

`src/ui/paginas/NuevoPage.tsx` (reemplazar):

```tsx
import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { buildManualTx } from '../../domain/builders';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { Teclado } from '../componentes/Teclado';
import { CategoriaGrid } from '../componentes/CategoriaGrid';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

type Modo = 'gasto' | 'ingreso'; // la Tarea 9 añade 'transferir' | 'ajustar'

export function NuevoPage({ params }: { params: URLSearchParams }) {
  const [modo, setModo] = useState<Modo>(params.get('tipo') === 'ingreso' ? 'ingreso' : 'gasto');
  const [entrada, setEntrada] = useState('');
  const [nota, setNota] = useState('');
  const [cuentaId, setCuentaId] = useState<string | null>(null);
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];

  useEffect(() => {
    getSettings(db).then((s) => setCuentaId((prev) => prev ?? s.lastAccountId ?? null));
  }, []);

  const cuenta = cuentas.find((c) => c.id === cuentaId) ?? cuentas[0];
  const minor = parseVenezuelanAmount(entrada.replace(/,$/, ''));

  function onTecla(t: string) {
    setEntrada((e) => {
      if (t === '⌫') return e.slice(0, -1);
      if (t === ',') return e === '' || e.includes(',') ? e : `${e},`;
      const dec = e.split(',')[1];
      if (dec !== undefined && dec.length >= 2) return e; // máximo 2 decimales
      return e + t;
    });
  }

  async function guardar(categoryId: string) {
    if (!cuenta || minor === null || minor <= 0) return;
    const tx = buildManualTx({
      type: modo === 'gasto' ? 'expense' : 'income',
      accountId: cuenta.id,
      amountMinor: minor,
      categoryId,
      dateIso: todayIso(),
      note: nota.trim() || undefined,
    });
    await db.transactions.add(tx);
    await saveSettings(db, { lastAccountId: cuenta.id });
    avisar('Guardado ✓');
    navigate('/');
  }

  return (
    <div>
      <div className="segmentado">
        <button className={modo === 'gasto' ? 'activo' : ''} onClick={() => setModo('gasto')}>Gasto</button>
        <button className={modo === 'ingreso' ? 'activo' : ''} onClick={() => setModo('ingreso')}>Ingreso</button>
      </div>
      <label>Cuenta</label>
      <select aria-label="Cuenta" value={cuenta?.id ?? ''} onChange={(e) => setCuentaId(e.target.value)}>
        {cuentas.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name} ({c.currency === 'VES' ? 'Bs' : '$'})
          </option>
        ))}
      </select>
      <div className={`monto-display ${modo}`}>
        {minor !== null && minor > 0 && cuenta
          ? formatMoney(modo === 'gasto' ? -minor : minor, cuenta.currency)
          : '—'}
      </div>
      <Teclado onTecla={onTecla} />
      <label>Nota (opcional)</label>
      <input aria-label="Nota" value={nota} onChange={(e) => setNota(e.target.value)} placeholder="¿En qué fue?" />
      <label>Toca la categoría para guardar</label>
      <CategoriaGrid
        categorias={categorias.filter((c) => c.kind === (modo === 'gasto' ? 'expense' : 'income'))}
        onElegir={guardar}
      />
    </div>
  );
}
```

- [ ] **Paso 4: Verificar.** `npx vitest run` → todo verde. Prueba manual en `npm run dev`: FAB → `35,5` → tocar "Supermercado" → toast "Guardado ✓" y vuelve al inicio.

- [ ] **Paso 5: Commit.**

```powershell
git add src/domain/builders.ts src/domain/builders.test.ts src/ui
git commit -m "Registro rápido: teclado numérico, grid de categorías y guardado en 2 toques + dígitos

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 9: Transferencias y ajuste de saldo

**Files:**
- Create: `src/ui/componentes/TransferForm.tsx`, `src/ui/componentes/AjusteForm.tsx`
- Modify: `src/domain/builders.ts`, `src/ui/paginas/NuevoPage.tsx` (segmentado pasa a 4 modos)
- Test: `src/domain/builders.test.ts` (ampliar), `src/ui/componentes/AjusteForm.test.tsx`

**Interfaces:**
- Consumes: `convertMinor`, `effectiveRate` (Tarea 4); `accountBalanceMinor` (Tarea 6); resto igual que Tarea 8.
- Produces:
  - `buildTransfer(opts: { fromAccount: Account; toAccount: Account; amountMinor: number; dateIso: string; vesPerUsd?: number; note?: string }): Transaction` — si cruza moneda calcula `toAmountMinor` con la tasa y guarda `rateUsed`; lanza `Error` si origen=destino, monto inválido o falta tasa.
  - `buildAdjustment(opts: { accountId: string; currentBalanceMinor: number; realBalanceMinor: number; dateIso: string; note?: string }): Transaction` — `amountMinor = real − actual` (delta con signo); lanza `Error('No hay diferencia que ajustar')` si el delta es 0. `source: 'adjust'`, nota por defecto `'Ajuste de saldo'`.

- [ ] **Paso 1: Ampliar `src/domain/builders.test.ts`** (añadir al final):

```ts
import type { Account } from './types';
import { buildTransfer, buildAdjustment } from './builders';

const bnc: Account = { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false };
const efectivo: Account = { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false };
const ahorro: Account = { id: 'acc-efectivo-ahorro', name: 'Efectivo ahorro', currency: 'USD', kind: 'cash', isSavings: true, archived: false };

describe('buildTransfer', () => {
  test('misma moneda: destino recibe el mismo monto, sin tasa', () => {
    const tx = buildTransfer({ fromAccount: efectivo, toAccount: ahorro, amountMinor: 5000, dateIso: '2026-07-07' });
    expect(tx).toMatchObject({
      type: 'transfer', accountId: 'acc-efectivo', toAccountId: 'acc-efectivo-ahorro',
      amountMinor: 5000, toAmountMinor: 5000, source: 'manual',
    });
    expect(tx.rateUsed).toBeUndefined();
  });
  test('USD → VES convierte con la tasa y la registra', () => {
    const tx = buildTransfer({ fromAccount: efectivo, toAccount: bnc, amountMinor: 1000, dateIso: '2026-07-07', vesPerUsd: 111.02 });
    expect(tx.toAmountMinor).toBe(111020); // $10.00 → Bs 1.110,20
    expect(tx.rateUsed).toBe(111.02);
  });
  test('VES → USD convierte dividiendo', () => {
    const tx = buildTransfer({ fromAccount: bnc, toAccount: efectivo, amountMinor: 111020, dateIso: '2026-07-07', vesPerUsd: 111.02 });
    expect(tx.toAmountMinor).toBe(1000);
  });
  test('errores: misma cuenta, monto inválido, tasa faltante', () => {
    expect(() => buildTransfer({ fromAccount: bnc, toAccount: bnc, amountMinor: 100, dateIso: '2026-07-07' })).toThrow();
    expect(() => buildTransfer({ fromAccount: efectivo, toAccount: ahorro, amountMinor: 0, dateIso: '2026-07-07' })).toThrow('Monto inválido');
    expect(() => buildTransfer({ fromAccount: efectivo, toAccount: bnc, amountMinor: 100, dateIso: '2026-07-07' })).toThrow('Falta la tasa');
  });
});

describe('buildAdjustment', () => {
  test('delta positivo (rendimiento en Binance)', () => {
    const tx = buildAdjustment({ accountId: 'acc-binance', currentBalanceMinor: 100000, realBalanceMinor: 102500, dateIso: '2026-07-07' });
    expect(tx).toMatchObject({ type: 'adjustment', amountMinor: 2500, source: 'adjust', note: 'Ajuste de saldo' });
  });
  test('delta negativo (cuadre de efectivo)', () => {
    const tx = buildAdjustment({ accountId: 'acc-efectivo', currentBalanceMinor: 5000, realBalanceMinor: 4200, dateIso: '2026-07-07' });
    expect(tx.amountMinor).toBe(-800);
  });
  test('sin diferencia lanza error', () => {
    expect(() => buildAdjustment({ accountId: 'a', currentBalanceMinor: 100, realBalanceMinor: 100, dateIso: '2026-07-07' })).toThrow('No hay diferencia que ajustar');
  });
});
```

Verifica que falla, luego añade a `src/domain/builders.ts`:

```ts
import type { Account } from './types';
import { convertMinor } from './rates';

export function buildTransfer(opts: {
  fromAccount: Account;
  toAccount: Account;
  amountMinor: number;
  dateIso: string;
  vesPerUsd?: number;
  note?: string;
}): Transaction {
  const { fromAccount, toAccount } = opts;
  if (fromAccount.id === toAccount.id) throw new Error('Origen y destino no pueden ser la misma cuenta');
  if (!Number.isInteger(opts.amountMinor) || opts.amountMinor <= 0) throw new Error('Monto inválido');
  const cruzaMoneda = fromAccount.currency !== toAccount.currency;
  if (cruzaMoneda && (opts.vesPerUsd === undefined || opts.vesPerUsd <= 0)) throw new Error('Falta la tasa para convertir');
  const toAmountMinor = cruzaMoneda
    ? convertMinor(opts.amountMinor, fromAccount.currency, toAccount.currency, opts.vesPerUsd as number)
    : opts.amountMinor;
  return {
    id: crypto.randomUUID(),
    type: 'transfer',
    accountId: fromAccount.id,
    toAccountId: toAccount.id,
    amountMinor: opts.amountMinor,
    toAmountMinor,
    rateUsed: cruzaMoneda ? opts.vesPerUsd : undefined,
    date: opts.dateIso,
    note: opts.note,
    source: 'manual',
  };
}

export function buildAdjustment(opts: {
  accountId: string;
  currentBalanceMinor: number;
  realBalanceMinor: number;
  dateIso: string;
  note?: string;
}): Transaction {
  const delta = opts.realBalanceMinor - opts.currentBalanceMinor;
  if (delta === 0) throw new Error('No hay diferencia que ajustar');
  return {
    id: crypto.randomUUID(),
    type: 'adjustment',
    accountId: opts.accountId,
    amountMinor: delta,
    date: opts.dateIso,
    note: opts.note ?? 'Ajuste de saldo',
    source: 'adjust',
  };
}
```

(Y ajusta el import existente: `import type { Account, Transaction } from './types';` en una sola línea.)

`npx vitest run src/domain/builders.test.ts` → PASS.

- [ ] **Paso 2: Formularios.** `src/ui/componentes/TransferForm.tsx`:

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings } from '../../db/db';
import { buildTransfer } from '../../domain/builders';
import { effectiveRate, convertMinor } from '../../domain/rates';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

export function TransferForm() {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const rates = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);
  const [origenId, setOrigenId] = useState('acc-efectivo');
  const [destinoId, setDestinoId] = useState('acc-bnc');
  const [montoTxt, setMontoTxt] = useState('');
  const [tasaTxt, setTasaTxt] = useState('');
  const [nota, setNota] = useState('');

  const origen = cuentas.find((c) => c.id === origenId);
  const destino = cuentas.find((c) => c.id === destinoId);
  const monto = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
  const cruza = !!origen && !!destino && origen.currency !== destino.currency;
  const tasaDelDia = settings ? effectiveRate(todayIso(), settings.rateSource, rates) : null;
  const tasa = tasaTxt.trim() !== '' ? Number(tasaTxt.replace(',', '.')) : tasaDelDia?.vesPerUsd;
  const destinoMinor =
    cruza && monto !== null && monto > 0 && tasa && tasa > 0 && origen && destino
      ? convertMinor(monto, origen.currency, destino.currency, tasa)
      : null;

  async function guardar() {
    if (!origen || !destino || monto === null || monto <= 0) return;
    try {
      const tx = buildTransfer({
        fromAccount: origen, toAccount: destino, amountMinor: monto,
        dateIso: todayIso(), vesPerUsd: cruza ? tasa : undefined,
        note: nota.trim() || undefined,
      });
      await db.transactions.add(tx);
      avisar('Transferencia guardada ✓');
      navigate('/');
    } catch (e) {
      avisar((e as Error).message);
    }
  }

  const opciones = (excluir: string) =>
    cuentas.filter((c) => c.id !== excluir).map((c) => (
      <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
    ));

  return (
    <div>
      <label>Desde</label>
      <select aria-label="Cuenta origen" value={origenId} onChange={(e) => setOrigenId(e.target.value)}>
        {opciones(destinoId)}
      </select>
      <label>Hacia</label>
      <select aria-label="Cuenta destino" value={destinoId} onChange={(e) => setDestinoId(e.target.value)}>
        {opciones(origenId)}
      </select>
      <label>Monto en {origen?.currency === 'VES' ? 'Bs' : '$'}</label>
      <input aria-label="Monto" inputMode="decimal" value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)} placeholder="0,00" />
      {cruza && (
        <>
          <label>Tasa (Bs por $) — propuesta {tasaDelDia ? `${tasaDelDia.vesPerUsd} del ${tasaDelDia.date}` : 'no disponible'}</label>
          <input
            aria-label="Tasa"
            inputMode="decimal"
            value={tasaTxt}
            onChange={(e) => setTasaTxt(e.target.value)}
            placeholder={tasaDelDia ? String(tasaDelDia.vesPerUsd) : 'ej. 111,02'}
          />
          {destinoMinor !== null && destino && (
            <div className="banner ok">Destino recibirá {formatMoney(destinoMinor, destino.currency)}</div>
          )}
        </>
      )}
      <label>Nota (opcional)</label>
      <input aria-label="Nota de transferencia" value={nota} onChange={(e) => setNota(e.target.value)} />
      <button className="boton" style={{ marginTop: 12 }} onClick={guardar} disabled={monto === null || monto <= 0 || (cruza && (!tasa || tasa <= 0))}>
        Transferir
      </button>
    </div>
  );
}
```

`src/ui/componentes/AjusteForm.tsx`:

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { buildAdjustment } from '../../domain/builders';
import { accountBalanceMinor } from '../../domain/balances';
import { parseVenezuelanAmount, formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';
import { navigate } from '../useHashRoute';

export function AjusteForm() {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const [cuentaId, setCuentaId] = useState('acc-binance');
  const [realTxt, setRealTxt] = useState('');

  const cuenta = cuentas.find((c) => c.id === cuentaId);
  const actual = cuenta ? accountBalanceMinor(cuenta.id, txs) : 0;
  const real = parseVenezuelanAmount(realTxt.replace(/,$/, ''));
  const delta = real !== null && real >= 0 ? real - actual : null;
  const esSnapshot = cuenta?.kind === 'snapshot';

  async function guardar() {
    if (!cuenta || real === null || delta === null || delta === 0) return;
    const tx = buildAdjustment({
      accountId: cuenta.id, currentBalanceMinor: actual, realBalanceMinor: real, dateIso: todayIso(),
    });
    await db.transactions.add(tx);
    avisar('Saldo ajustado ✓');
    navigate('/');
  }

  return (
    <div>
      <label>Cuenta</label>
      <select aria-label="Cuenta a ajustar" value={cuentaId} onChange={(e) => setCuentaId(e.target.value)}>
        {cuentas.map((c) => (
          <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
        ))}
      </select>
      {cuenta && (
        <p>
          Saldo según la app: <strong>{formatMoney(actual, cuenta.currency)}</strong>
        </p>
      )}
      <label>Saldo real actual</label>
      <input aria-label="Saldo real" inputMode="decimal" value={realTxt} onChange={(e) => setRealTxt(e.target.value)} placeholder="0,00" />
      {cuenta && delta !== null && delta !== 0 && (
        <div className={`banner ${delta > 0 ? 'ok' : 'aviso'}`}>
          {esSnapshot ? 'Variación/rendimiento' : 'Diferencia'}: {formatMoney(delta, cuenta.currency)}
        </div>
      )}
      <button className="boton" style={{ marginTop: 12 }} onClick={guardar} disabled={real === null || delta === null || delta === 0}>
        Ajustar saldo
      </button>
    </div>
  );
}
```

- [ ] **Paso 3: Ampliar `NuevoPage`** — cambiar el tipo `Modo` y el segmentado (el resto de la página queda igual):

```tsx
type Modo = 'gasto' | 'ingreso' | 'transferir' | 'ajustar';
```

En el JSX, el segmentado pasa a 4 botones y el cuerpo se bifurca:

```tsx
import { TransferForm } from '../componentes/TransferForm';
import { AjusteForm } from '../componentes/AjusteForm';
// ...
return (
  <div>
    <div className="segmentado">
      <button className={modo === 'gasto' ? 'activo' : ''} onClick={() => setModo('gasto')}>Gasto</button>
      <button className={modo === 'ingreso' ? 'activo' : ''} onClick={() => setModo('ingreso')}>Ingreso</button>
      <button className={modo === 'transferir' ? 'activo' : ''} onClick={() => setModo('transferir')}>Transferir</button>
      <button className={modo === 'ajustar' ? 'activo' : ''} onClick={() => setModo('ajustar')}>Ajustar</button>
    </div>
    {modo === 'transferir' && <TransferForm />}
    {modo === 'ajustar' && <AjusteForm />}
    {(modo === 'gasto' || modo === 'ingreso') && (
      <> {/* …todo el contenido actual de gasto/ingreso tal cual… */} </>
    )}
  </div>
);
```

- [ ] **Paso 4: Test de integración del ajuste** — `src/ui/componentes/AjusteForm.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AjusteForm } from './AjusteForm';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';

beforeEach(async () => { await limpiarDb(); });

test('ajusta Binance introduciendo el saldo real', async () => {
  const user = userEvent.setup();
  render(<AjusteForm />);
  const input = await screen.findByLabelText('Saldo real');
  await user.type(input, '250,75');
  await user.click(screen.getByRole('button', { name: 'Ajustar saldo' }));
  await waitFor(async () => expect(await db.transactions.count()).toBe(1));
  const tx = (await db.transactions.toArray())[0];
  expect(tx).toMatchObject({ type: 'adjustment', accountId: 'acc-binance', amountMinor: 25075, source: 'adjust' });
});
```

- [ ] **Paso 5: Verificar y commit.**

```powershell
npx vitest run
git add src/domain/builders.ts src/domain/builders.test.ts src/ui
git commit -m "Transferencias (con conversión de moneda) y ajuste de saldo

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 10: Lista de movimientos con búsqueda, filtros y edición

**Files:**
- Create: `src/domain/filters.ts`
- Modify: `src/ui/paginas/MovimientosPage.tsx` (reemplazar el stub)
- Test: `src/domain/filters.test.ts`, `src/ui/paginas/MovimientosPage.test.tsx`

**Interfaces:**
- Consumes: entidades y `formatMoney`; `db`; clases CSS de la Tarea 7.
- Produces:
  - `interface FiltroMovs { q?: string; accountId?: string; categoryId?: string; month?: string }`
  - `filterTxs(txs: Transaction[], f: FiltroMovs): Transaction[]` — ordena por fecha+hora descendente; el filtro de cuenta incluye transferencias donde la cuenta es destino; `q` busca substring case-insensitive en `note`.

- [ ] **Paso 1: Test `src/domain/filters.test.ts`:**

```ts
import type { Transaction } from './types';
import { filterTxs } from './filters';

const tx = (t: Partial<Transaction>): Transaction => ({
  id: Math.random().toString(36).slice(2), type: 'expense', accountId: 'bnc',
  amountMinor: 100, date: '2026-07-01', source: 'manual', ...t,
});

const txs: Transaction[] = [
  tx({ id: 'a', date: '2026-07-02', note: 'Farmacia Ávila' }),
  tx({ id: 'b', date: '2026-07-02', time: '15:00:00', categoryId: 'cat-ocio' }),
  tx({ id: 'c', date: '2026-06-15', accountId: 'efectivo' }),
  tx({ id: 'd', date: '2026-07-01', type: 'transfer', accountId: 'efectivo', toAccountId: 'bnc', toAmountMinor: 100 }),
];

test('ordena descendente por fecha y hora', () => {
  expect(filterTxs(txs, {}).map((t) => t.id)).toEqual(['b', 'a', 'd', 'c']);
});
test('filtro por mes', () => {
  expect(filterTxs(txs, { month: '2026-06' }).map((t) => t.id)).toEqual(['c']);
});
test('filtro por cuenta incluye transferencias entrantes', () => {
  expect(filterTxs(txs, { accountId: 'bnc' }).map((t) => t.id)).toEqual(['b', 'a', 'd']);
});
test('filtro por categoría', () => {
  expect(filterTxs(txs, { categoryId: 'cat-ocio' }).map((t) => t.id)).toEqual(['b']);
});
test('búsqueda en nota, case-insensitive', () => {
  expect(filterTxs(txs, { q: 'farmacia' }).map((t) => t.id)).toEqual(['a']);
});
```

Verifica que falla, luego implementa `src/domain/filters.ts`:

```ts
import type { Transaction } from './types';

export interface FiltroMovs {
  q?: string;
  accountId?: string;
  categoryId?: string;
  month?: string; // 'yyyy-mm'
}

export function filterTxs(txs: Transaction[], f: FiltroMovs): Transaction[] {
  const q = f.q?.trim().toLowerCase();
  return txs
    .filter((t) => !f.accountId || t.accountId === f.accountId || t.toAccountId === f.accountId)
    .filter((t) => !f.categoryId || t.categoryId === f.categoryId)
    .filter((t) => !f.month || t.date.startsWith(f.month))
    .filter((t) => !q || (t.note ?? '').toLowerCase().includes(q))
    .sort((a, b) => (b.date + (b.time ?? '')).localeCompare(a.date + (a.time ?? '')));
}
```

`npx vitest run src/domain/filters.test.ts` → PASS.

- [ ] **Paso 2: Página** — `src/ui/paginas/MovimientosPage.tsx` (reemplazar):

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Transaction } from '../../domain/types';
import { filterTxs } from '../../domain/filters';
import { formatMoney, parseVenezuelanAmount } from '../../domain/money';
import { avisar } from '../Toast';

export function MovimientosPage() {
  const [q, setQ] = useState('');
  const [cuentaId, setCuentaId] = useState('');
  const [categoriaId, setCategoriaId] = useState('');
  const [mes, setMes] = useState('');
  const [editando, setEditando] = useState<Transaction | null>(null);

  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];

  const visibles = filterTxs(txs, {
    q: q || undefined,
    accountId: cuentaId || undefined,
    categoryId: categoriaId || undefined,
    month: mes || undefined,
  });
  const meses = [...new Set(txs.map((t) => t.date.slice(0, 7)))].sort().reverse();
  const nombreCuenta = (id?: string) => cuentas.find((c) => c.id === id)?.name ?? '?';
  const cat = (id?: string) => categorias.find((c) => c.id === id);

  function descripcionFila(t: Transaction): string {
    if (t.type === 'transfer') return `${nombreCuenta(t.accountId)} → ${nombreCuenta(t.toAccountId)}`;
    return t.note || cat(t.categoryId)?.name || (t.type === 'adjustment' ? 'Ajuste de saldo' : 'Sin nota');
  }

  function montoFila(t: Transaction) {
    const moneda = cuentas.find((c) => c.id === t.accountId)?.currency ?? 'USD';
    if (t.type === 'income') return <span className="monto-pos">+{formatMoney(t.amountMinor, moneda)}</span>;
    if (t.type === 'expense') return <span className="monto-neg">−{formatMoney(t.amountMinor, moneda)}</span>;
    if (t.type === 'adjustment')
      return <span className={t.amountMinor >= 0 ? 'monto-pos' : 'monto-neg'}>{formatMoney(t.amountMinor, moneda)}</span>;
    return <span>↔ {formatMoney(t.amountMinor, moneda)}</span>;
  }

  return (
    <div>
      <h2>Movimientos</h2>
      <input aria-label="Buscar" placeholder="Buscar en notas…" value={q} onChange={(e) => setQ(e.target.value)} />
      <div className="fila-form" style={{ marginTop: 8 }}>
        <select aria-label="Filtrar cuenta" value={cuentaId} onChange={(e) => setCuentaId(e.target.value)}>
          <option value="">Todas las cuentas</option>
          {cuentas.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select aria-label="Filtrar categoría" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)}>
          <option value="">Todas las categorías</option>
          {categorias.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select aria-label="Filtrar mes" value={mes} onChange={(e) => setMes(e.target.value)}>
          <option value="">Todos los meses</option>
          {meses.map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
      </div>
      <div style={{ marginTop: 8 }}>
        {visibles.length === 0 && <p style={{ color: 'var(--texto2)' }}>No hay movimientos que coincidan.</p>}
        {visibles.map((t) => (
          <button key={t.id} className="fila-mov" onClick={() => setEditando(t)}>
            <span style={{ fontSize: 22 }}>
              {t.type === 'transfer' ? '🔁' : t.type === 'adjustment' ? '🎯' : cat(t.categoryId)?.icon ?? '📦'}
            </span>
            <span className="detalle">
              <span className="nota">{descripcionFila(t)}</span>
              <span className="meta">{t.date} · {nombreCuenta(t.accountId)}{t.importKey ? ' · importado' : ''}</span>
            </span>
            {montoFila(t)}
          </button>
        ))}
      </div>
      {editando && <EditorMovimiento tx={editando} cerrar={() => setEditando(null)} />}
    </div>
  );
}

function EditorMovimiento({ tx, cerrar }: { tx: Transaction; cerrar: () => void }) {
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [categoriaId, setCategoriaId] = useState(tx.categoryId ?? '');
  const [nota, setNota] = useState(tx.note ?? '');
  const [fecha, setFecha] = useState(tx.date);
  const [montoTxt, setMontoTxt] = useState((tx.amountMinor / 100).toFixed(2).replace('.', ','));
  // Transferencias y ajustes: solo nota y fecha son editables (el monto afecta dos saldos o es un delta).
  const montoEditable = tx.type === 'expense' || tx.type === 'income';

  async function guardar() {
    const cambios: Partial<Transaction> = { note: nota.trim() || undefined, date: fecha };
    if (montoEditable) {
      const m = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
      if (m === null || m <= 0) { avisar('Monto inválido'); return; }
      cambios.amountMinor = m;
      cambios.categoryId = categoriaId || undefined;
    }
    await db.transactions.update(tx.id, cambios);
    avisar('Actualizado ✓');
    cerrar();
  }

  async function eliminar() {
    if (!window.confirm('¿Eliminar este movimiento?')) return;
    await db.transactions.delete(tx.id);
    avisar('Eliminado');
    cerrar();
  }

  return (
    <div className="modal-fondo" onClick={cerrar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Editar movimiento</h3>
        {montoEditable && (
          <>
            <label>Monto</label>
            <input aria-label="Editar monto" inputMode="decimal" value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)} />
            <label>Categoría</label>
            <select aria-label="Editar categoría" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)}>
              <option value="">Sin categoría</option>
              {categorias.filter((c) => c.kind === tx.type).map((c) => (
                <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
              ))}
            </select>
          </>
        )}
        <label>Fecha</label>
        <input aria-label="Editar fecha" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        <label>Nota</label>
        <input aria-label="Editar nota" value={nota} onChange={(e) => setNota(e.target.value)} />
        <div className="fila-form" style={{ marginTop: 12 }}>
          <button className="boton secundario" onClick={cerrar}>Cancelar</button>
          <button className="boton peligro" onClick={eliminar}>Eliminar</button>
          <button className="boton" onClick={guardar}>Guardar</button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Paso 3: Test del componente** — `src/ui/paginas/MovimientosPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MovimientosPage } from './MovimientosPage';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';
import { buildManualTx } from '../../domain/builders';

beforeEach(async () => {
  await limpiarDb();
  await db.transactions.bulkAdd([
    buildManualTx({ type: 'expense', accountId: 'acc-bnc', amountMinor: 5000, categoryId: 'cat-ocio', dateIso: '2026-07-01', note: 'partida' }),
    buildManualTx({ type: 'expense', accountId: 'acc-efectivo', amountMinor: 900, categoryId: 'cat-comida-fuera', dateIso: '2026-07-02', note: 'arepas' }),
  ]);
});

test('lista, filtra por categoría y busca por nota', async () => {
  const user = userEvent.setup();
  render(<MovimientosPage />);
  expect(await screen.findByText('arepas')).toBeInTheDocument();
  expect(screen.getByText('partida')).toBeInTheDocument();

  await user.selectOptions(screen.getByLabelText('Filtrar categoría'), 'cat-ocio');
  await waitFor(() => expect(screen.queryByText('arepas')).not.toBeInTheDocument());

  await user.selectOptions(screen.getByLabelText('Filtrar categoría'), '');
  await user.type(screen.getByLabelText('Buscar'), 'arep');
  await waitFor(() => expect(screen.queryByText('partida')).not.toBeInTheDocument());
  expect(screen.getByText('arepas')).toBeInTheDocument();
});

test('elimina un movimiento desde el editor', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  const user = userEvent.setup();
  render(<MovimientosPage />);
  await user.click(await screen.findByText('arepas'));
  await user.click(screen.getByRole('button', { name: 'Eliminar' }));
  await waitFor(async () => expect(await db.transactions.count()).toBe(1));
});
```

- [ ] **Paso 4: Verificar y commit.**

```powershell
npx vitest run
git add src/domain/filters.ts src/domain/filters.test.ts src/ui/paginas/MovimientosPage.tsx src/ui/paginas/MovimientosPage.test.tsx
git commit -m "Movimientos: lista con búsqueda, filtros y edición/borrado

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

**Fin de fase:** `npm run build` sin errores.

---

## Fase 3 — Dashboard

### Tarea 11: Patrimonio consolidado con toggle USD/Bs

**Files:**
- Create: `src/ui/componentes/PatrimonioCard.tsx`
- Modify: `src/ui/paginas/DashboardPage.tsx` (reemplazar el stub)
- Test: `src/ui/paginas/DashboardPage.test.tsx`

**Interfaces:**
- Consumes: `patrimonio`, `Patrimonio` (Tarea 6); `effectiveRate`, `usdToVesMinor` (Tarea 4); `formatMoney` (Tarea 3); `todayIso` (Tarea 3); `db`, `getSettings`, `saveSettings` (Tarea 5).
- Produces: `<PatrimonioCard p={Patrimonio} tasa={Rate | null} viewCurrency={Currency} onToggle={() => void} />`. La Tarea 12 añadirá `<GraficasMes />` debajo dentro de `DashboardPage`.

- [ ] **Paso 1: Test** — `src/ui/paginas/DashboardPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DashboardPage } from './DashboardPage';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';
import { buildManualTx } from '../../domain/builders';

beforeEach(async () => {
  await limpiarDb();
  await db.rates.put({ date: '2026-01-01', source: 'BCV', vesPerUsd: 100 });
  await db.transactions.bulkAdd([
    buildManualTx({ type: 'income', accountId: 'acc-efectivo', amountMinor: 5000, categoryId: 'cat-otros-ingresos', dateIso: '2026-07-01' }), // $50.00
    buildManualTx({ type: 'income', accountId: 'acc-bnc', amountMinor: 100000, categoryId: 'cat-otros-ingresos', dateIso: '2026-07-01' }),    // Bs 1.000,00 → $10.00
  ]);
});

test('muestra el patrimonio total en USD con desglose disposición/ahorro', async () => {
  render(<DashboardPage />);
  expect(await screen.findByText('$60.00')).toBeInTheDocument();
  expect(screen.getByText(/A disposición/)).toBeInTheDocument();
  // La fila de BNC muestra saldo nativo y su equivalente
  expect(screen.getByText(/Bs 1\.000,00/)).toBeInTheDocument();
});

test('el toggle cambia la vista a Bs', async () => {
  const user = userEvent.setup();
  render(<DashboardPage />);
  await user.click(await screen.findByRole('button', { name: 'ver en Bs' }));
  expect(await screen.findByText('Bs 6.000,00')).toBeInTheDocument();
});
```

Verifica que falla: `npx vitest run src/ui/paginas/DashboardPage.test.tsx` → FAIL.

- [ ] **Paso 2: Implementar.** `src/ui/componentes/PatrimonioCard.tsx`:

```tsx
import type { Currency, Rate } from '../../domain/types';
import type { Patrimonio } from '../../domain/balances';
import { formatMoney } from '../../domain/money';
import { usdToVesMinor } from '../../domain/rates';
import { todayIso } from '../../domain/dates';

export function PatrimonioCard({ p, tasa, viewCurrency, onToggle }: {
  p: Patrimonio;
  tasa: Rate | null;
  viewCurrency: Currency;
  onToggle: () => void;
}) {
  const enVista = (usdMinor: number | null): string => {
    if (usdMinor === null) return 'sin tasa';
    if (viewCurrency === 'USD') return formatMoney(usdMinor, 'USD');
    if (!tasa) return 'sin tasa';
    return formatMoney(usdToVesMinor(usdMinor, tasa.vesPerUsd), 'VES');
  };

  return (
    <div className="tarjeta">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>Patrimonio total</h3>
        <button className="chip" onClick={onToggle}>{viewCurrency === 'USD' ? 'ver en Bs' : 'ver en $'}</button>
      </div>
      <p style={{ fontSize: 32, fontWeight: 700, margin: '8px 0' }}>{enVista(p.totalUsdMinor)}</p>
      <p style={{ margin: '4px 0', color: 'var(--texto2)' }}>
        A disposición: <strong>{enVista(p.disposicionUsdMinor)}</strong> · Ahorro: <strong>{enVista(p.ahorroUsdMinor)}</strong>
      </p>
      {tasa ? (
        <p style={{ fontSize: 12, color: 'var(--texto2)' }}>
          Tasa {tasa.source} del {tasa.date}: {tasa.vesPerUsd} Bs/$
          {tasa.date !== todayIso() && <span className="chip" style={{ marginLeft: 6 }}>tasa vieja</span>}
        </p>
      ) : (
        <div className="banner aviso">Sin tasa configurada — regístrala en Ajustes → Tasas.</div>
      )}
      <div style={{ marginTop: 8 }}>
        {p.lines.map((l) => (
          <div key={l.account.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
            <span>{l.account.isSavings ? '🔒 ' : ''}{l.account.name}</span>
            <span>
              {formatMoney(l.balanceMinor, l.account.currency)}
              {l.account.currency === 'VES' && (
                <span style={{ color: 'var(--texto2)' }}>
                  {' '}≈ {l.balanceUsdMinor === null ? '—' : formatMoney(l.balanceUsdMinor, 'USD')}
                </span>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
```

`src/ui/paginas/DashboardPage.tsx` (reemplazar):

```tsx
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import { patrimonio } from '../../domain/balances';
import { effectiveRate } from '../../domain/rates';
import { todayIso } from '../../domain/dates';
import { PatrimonioCard } from '../componentes/PatrimonioCard';

export function DashboardPage() {
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const rates = useLiveQuery(() => db.rates.toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);

  if (!settings) return <h2>Inicio</h2>;
  const tasa = effectiveRate(todayIso(), settings.rateSource, rates);
  const p = patrimonio(cuentas, txs, tasa?.vesPerUsd ?? null);

  return (
    <div>
      <h2>Inicio</h2>
      <PatrimonioCard
        p={p}
        tasa={tasa}
        viewCurrency={settings.viewCurrency}
        onToggle={() => saveSettings(db, { viewCurrency: settings.viewCurrency === 'USD' ? 'VES' : 'USD' })}
      />
      {/* La Tarea 12 añade aquí <GraficasMes /> */}
    </div>
  );
}
```

- [ ] **Paso 3: Verificar y commit.**

```powershell
npx vitest run
git add src/ui
git commit -m "Dashboard: patrimonio consolidado con desglose y toggle USD/Bs

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 12: Estadísticas del mes, gráficas y presupuestos

**Files:**
- Create: `src/domain/stats.ts`, `src/ui/componentes/GraficasMes.tsx`
- Modify: `src/ui/paginas/DashboardPage.tsx`
- Test: `src/domain/stats.test.ts`

**Interfaces:**
- Consumes: `effectiveRate`, `convertMinor` (Tarea 4); entidades de `types.ts`.
- Produces:
  - `interface ResumenMes { month: string; incomeMinor: number; expenseMinor: number; byCategory: { categoryId: string | null; expenseMinor: number }[]; dailyCumulative: { day: number; acumuladoMinor: number }[]; omitidasSinTasa: number }` — todos los montos en `viewCurrency`; solo `expense`/`income` cuentan (transferencias y ajustes NO son gasto ni ingreso); conversión por transacción usando la tasa vigente de **su** fecha; si falta tasa para convertir, la transacción se omite y se cuenta en `omitidasSinTasa`.
  - `monthSummary(opts: { txs: Transaction[]; accounts: Account[]; month: string; viewCurrency: Currency; rates: Rate[]; activeSource: RateSource }): ResumenMes`
  - `lastMonths(baseMonth: string, n: number): string[]` — p. ej. `('2026-01', 3)` → `['2026-01', '2025-12', '2025-11']`.
  - `interface EstadoPresupuesto { category: Category; gastoUsdMinor: number; presupuestoMinor: number; ratio: number }`
  - `budgetStatus(opts: { txs: Transaction[]; accounts: Account[]; categories: Category[]; month: string; rates: Rate[]; activeSource: RateSource }): EstadoPresupuesto[]` — presupuestos siempre en USD (`monthlyBudgetMinor`).

- [ ] **Paso 1: Test `src/domain/stats.test.ts`:**

```ts
import type { Account, Category, Rate, Transaction } from './types';
import { monthSummary, lastMonths, budgetStatus } from './stats';

const cuentas: Account[] = [
  { id: 'bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
];
const rates: Rate[] = [{ date: '2026-07-01', source: 'BCV', vesPerUsd: 100 }];
const tx = (t: Partial<Transaction>): Transaction => ({
  id: Math.random().toString(36).slice(2), type: 'expense', accountId: 'efectivo',
  amountMinor: 0, date: '2026-07-05', source: 'manual', ...t,
});

const txs: Transaction[] = [
  tx({ accountId: 'bnc', amountMinor: 100000, date: '2026-07-05', categoryId: 'cat-a' }), // Bs 1.000 → $10.00
  tx({ amountMinor: 500, date: '2026-07-10', categoryId: 'cat-a' }),                      // $5.00
  tx({ amountMinor: 300, date: '2026-07-10', categoryId: 'cat-b' }),                      // $3.00
  tx({ type: 'income', amountMinor: 2000, date: '2026-07-03' }),                          // $20.00
  tx({ type: 'transfer', accountId: 'efectivo', toAccountId: 'bnc', amountMinor: 999 }),  // no cuenta
  tx({ type: 'adjustment', accountId: 'efectivo', amountMinor: 999, source: 'adjust' }),  // no cuenta
  tx({ amountMinor: 777, date: '2026-06-30' }),                                           // otro mes
];

test('resume el mes en USD: totales, categorías y acumulado diario', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates, activeSource: 'BCV' });
  expect(r.incomeMinor).toBe(2000);
  expect(r.expenseMinor).toBe(1800);
  expect(r.byCategory).toEqual([
    { categoryId: 'cat-a', expenseMinor: 1500 },
    { categoryId: 'cat-b', expenseMinor: 300 },
  ]);
  expect(r.dailyCumulative).toHaveLength(31);
  expect(r.dailyCumulative[3]).toEqual({ day: 4, acumuladoMinor: 0 });
  expect(r.dailyCumulative[4]).toEqual({ day: 5, acumuladoMinor: 1000 });
  expect(r.dailyCumulative[30]).toEqual({ day: 31, acumuladoMinor: 1800 });
  expect(r.omitidasSinTasa).toBe(0);
});

test('sin tasa, las transacciones VES se omiten y se cuentan', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates: [], activeSource: 'BCV' });
  expect(r.omitidasSinTasa).toBe(1);
  expect(r.expenseMinor).toBe(800); // solo las USD
});

test('vista en Bs convierte las USD', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'VES', rates, activeSource: 'BCV' });
  expect(r.expenseMinor).toBe(100000 + 50000 + 30000);
});

test('lastMonths cruza el año', () => {
  expect(lastMonths('2026-01', 3)).toEqual(['2026-01', '2025-12', '2025-11']);
});

test('budgetStatus compara gasto USD contra presupuesto', () => {
  const categorias: Category[] = [
    { id: 'cat-a', name: 'A', icon: 'a', color: '#000', kind: 'expense', monthlyBudgetMinor: 2000 },
    { id: 'cat-b', name: 'B', icon: 'b', color: '#000', kind: 'expense' }, // sin presupuesto: fuera
  ];
  const r = budgetStatus({ txs, accounts: cuentas, categories: categorias, month: '2026-07', rates, activeSource: 'BCV' });
  expect(r).toHaveLength(1);
  expect(r[0]).toMatchObject({ gastoUsdMinor: 1500, presupuestoMinor: 2000, ratio: 0.75 });
});
```

- [ ] **Paso 2: Verificar que falla, implementar `src/domain/stats.ts`:**

```ts
import type { Account, Category, Currency, Rate, RateSource, Transaction } from './types';
import { convertMinor, effectiveRate } from './rates';

export interface ResumenMes {
  month: string;
  incomeMinor: number;
  expenseMinor: number;
  byCategory: { categoryId: string | null; expenseMinor: number }[];
  dailyCumulative: { day: number; acumuladoMinor: number }[];
  omitidasSinTasa: number;
}

export function monthSummary(opts: {
  txs: Transaction[];
  accounts: Account[];
  month: string;
  viewCurrency: Currency;
  rates: Rate[];
  activeSource: RateSource;
}): ResumenMes {
  const monedaDe = new Map(opts.accounts.map((a) => [a.id, a.currency]));
  let incomeMinor = 0;
  let expenseMinor = 0;
  let omitidasSinTasa = 0;
  const porCategoria = new Map<string | null, number>();
  const porDia = new Map<number, number>();

  for (const t of opts.txs) {
    if (!t.date.startsWith(opts.month)) continue;
    if (t.type !== 'expense' && t.type !== 'income') continue;
    const desde = monedaDe.get(t.accountId) ?? 'USD';
    let minor = t.amountMinor;
    if (desde !== opts.viewCurrency) {
      const rate = effectiveRate(t.date, opts.activeSource, opts.rates);
      if (!rate) {
        omitidasSinTasa++;
        continue;
      }
      minor = convertMinor(minor, desde, opts.viewCurrency, rate.vesPerUsd);
    }
    if (t.type === 'income') {
      incomeMinor += minor;
    } else {
      expenseMinor += minor;
      const key = t.categoryId ?? null;
      porCategoria.set(key, (porCategoria.get(key) ?? 0) + minor);
      const dia = Number(t.date.slice(8, 10));
      porDia.set(dia, (porDia.get(dia) ?? 0) + minor);
    }
  }

  const [y, m] = opts.month.split('-').map(Number);
  const diasDelMes = new Date(y, m, 0).getDate();
  let acumulado = 0;
  const dailyCumulative = Array.from({ length: diasDelMes }, (_, i) => {
    acumulado += porDia.get(i + 1) ?? 0;
    return { day: i + 1, acumuladoMinor: acumulado };
  });

  const byCategory = [...porCategoria.entries()]
    .map(([categoryId, gasto]) => ({ categoryId, expenseMinor: gasto }))
    .sort((a, b) => b.expenseMinor - a.expenseMinor);

  return { month: opts.month, incomeMinor, expenseMinor, byCategory, dailyCumulative, omitidasSinTasa };
}

export function lastMonths(baseMonth: string, n: number): string[] {
  const [y, m] = baseMonth.split('-').map(Number);
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(y, m - 1 - i, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
}

export interface EstadoPresupuesto {
  category: Category;
  gastoUsdMinor: number;
  presupuestoMinor: number;
  ratio: number;
}

export function budgetStatus(opts: {
  txs: Transaction[];
  accounts: Account[];
  categories: Category[];
  month: string;
  rates: Rate[];
  activeSource: RateSource;
}): EstadoPresupuesto[] {
  const resumenUsd = monthSummary({ ...opts, viewCurrency: 'USD' });
  return opts.categories
    .filter((c) => c.kind === 'expense' && (c.monthlyBudgetMinor ?? 0) > 0)
    .map((category) => {
      const gastoUsdMinor = resumenUsd.byCategory.find((b) => b.categoryId === category.id)?.expenseMinor ?? 0;
      const presupuestoMinor = category.monthlyBudgetMinor as number;
      return { category, gastoUsdMinor, presupuestoMinor, ratio: gastoUsdMinor / presupuestoMinor };
    });
}
```

`npx vitest run src/domain/stats.test.ts` → PASS.

- [ ] **Paso 3: Gráficas** — `src/ui/componentes/GraficasMes.tsx`:

```tsx
import { Area, AreaChart, Bar, BarChart, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { Category, Currency } from '../../domain/types';
import type { EstadoPresupuesto, ResumenMes } from '../../domain/stats';
import { formatMoney } from '../../domain/money';

export function GraficasMes({ resumen, comparativa, presupuestos, categorias, viewCurrency }: {
  resumen: ResumenMes;
  comparativa: ResumenMes[];
  presupuestos: EstadoPresupuesto[];
  categorias: Category[];
  viewCurrency: Currency;
}) {
  const cat = (id: string | null) => categorias.find((c) => c.id === id);
  const donut = resumen.byCategory.map((b) => ({
    name: cat(b.categoryId)?.name ?? 'Sin categoría',
    value: b.expenseMinor / 100,
    color: cat(b.categoryId)?.color ?? '#9ca3af',
  }));
  const balance = resumen.incomeMinor - resumen.expenseMinor;
  const dinero = (minor: number) => formatMoney(minor, viewCurrency);

  return (
    <>
      <div className="tarjeta">
        <h3>Este mes ({resumen.month})</h3>
        <p>
          Ingresos <span className="monto-pos">+{dinero(resumen.incomeMinor)}</span>
          {' · '}Gastos <span className="monto-neg">−{dinero(resumen.expenseMinor)}</span>
        </p>
        <p style={{ fontSize: 22, fontWeight: 700 }}>
          Balance: <span className={balance >= 0 ? 'monto-pos' : 'monto-neg'}>{dinero(balance)}</span>
        </p>
        {resumen.omitidasSinTasa > 0 && (
          <div className="banner aviso">{resumen.omitidasSinTasa} movimiento(s) sin tasa quedaron fuera del resumen.</div>
        )}
        {donut.length > 0 && (
          <div style={{ height: 220 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie data={donut} dataKey="value" nameKey="name" innerRadius={55} outerRadius={85} label={(e) => e.name}>
                  {donut.map((d) => <Cell key={d.name} fill={d.color} />)}
                </Pie>
                <Tooltip formatter={(v: number) => dinero(Math.round(v * 100))} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        )}
        <div style={{ height: 160 }}>
          <ResponsiveContainer>
            <AreaChart data={resumen.dailyCumulative.map((d) => ({ ...d, gasto: d.acumuladoMinor / 100 }))}>
              <XAxis dataKey="day" tickLine={false} />
              <YAxis width={50} tickLine={false} />
              <Tooltip formatter={(v: number) => dinero(Math.round(v * 100))} />
              <Area type="monotone" dataKey="gasto" stroke="#0e7490" fill="#0e749033" name="Gasto acumulado" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="tarjeta">
        <h3>Comparativa mensual</h3>
        <div style={{ height: 180 }}>
          <ResponsiveContainer>
            <BarChart data={comparativa.map((r) => ({
              mes: r.month.slice(5), ingresos: r.incomeMinor / 100, gastos: r.expenseMinor / 100,
            }))}>
              <XAxis dataKey="mes" tickLine={false} />
              <YAxis width={50} tickLine={false} />
              <Tooltip formatter={(v: number) => dinero(Math.round(v * 100))} />
              <Legend />
              <Bar dataKey="ingresos" fill="#16a34a" name="Ingresos" />
              <Bar dataKey="gastos" fill="#dc2626" name="Gastos" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {presupuestos.length > 0 && (
        <div className="tarjeta">
          <h3>Presupuestos (USD)</h3>
          {presupuestos.map((e) => (
            <div key={e.category.id} style={{ marginBottom: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
                <span>{e.category.icon} {e.category.name}</span>
                <span className={e.ratio > 1 ? 'monto-neg' : ''}>
                  {formatMoney(e.gastoUsdMinor, 'USD')} / {formatMoney(e.presupuestoMinor, 'USD')}
                </span>
              </div>
              <div className="progreso">
                <div style={{ width: `${Math.min(100, e.ratio * 100)}%`, background: e.ratio > 1 ? 'var(--gasto)' : 'var(--primario)' }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
```

- [ ] **Paso 4: Integrar en `DashboardPage`** — añadir imports y, debajo de `<PatrimonioCard …/>`, sustituir el comentario por:

```tsx
import { monthSummary, lastMonths, budgetStatus } from '../../domain/stats';
import { monthOf } from '../../domain/dates';
import { GraficasMes } from '../componentes/GraficasMes';
// ...dentro del componente, tras calcular `p`:
const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
const mesActual = monthOf(todayIso());
const base = { txs, accounts: cuentas, rates, activeSource: settings.rateSource };
const resumen = monthSummary({ ...base, month: mesActual, viewCurrency: settings.viewCurrency });
const comparativa = lastMonths(mesActual, 4)
  .map((m) => monthSummary({ ...base, month: m, viewCurrency: settings.viewCurrency }))
  .reverse();
const presupuestos = budgetStatus({ ...base, categories: categorias, month: mesActual });
// ...en el JSX:
<GraficasMes resumen={resumen} comparativa={comparativa} presupuestos={presupuestos} categorias={categorias} viewCurrency={settings.viewCurrency} />
```

- [ ] **Paso 5: Verificar.** `npx vitest run` → verde (los tests de la Tarea 11 siguen pasando: las gráficas Recharts renderizan vacías en jsdom sin romper). Prueba manual: registra 2-3 gastos y mira donut/curva/comparativa; ponle presupuesto a una categoría directamente en DevTools → Application → IndexedDB si quieres verlo ya (la UI de presupuestos llega en la Tarea 14).

- [ ] **Paso 6: Commit.**

```powershell
git add src/domain/stats.ts src/domain/stats.test.ts src/ui
git commit -m "Dashboard: resumen mensual, donut por categoría, evolución diaria, comparativa y presupuestos

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

**Fin de fase:** `npm run build` sin errores.

---

## Fase 4 — Tasas online y ajustes

### Tarea 13: Proveedores de tasas con fallback + sección Tasas en Ajustes

**Files:**
- Create: `src/rates/providers.ts`, `src/ui/componentes/TasasSection.tsx`
- Modify: `src/ui/paginas/AjustesPage.tsx` (estructura real con secciones), `src/ui/App.tsx` (auto-fetch al abrir), `tsconfig.json` (añadir `"resolveJsonModule": true` a `compilerOptions`)
- Test: `src/rates/providers.test.ts`

**Interfaces:**
- Consumes: fixtures JSON del spike (Tarea 2); `db`, `getSettings`, `saveSettings` (Tarea 5); `effectiveRate` (Tarea 4).
- Produces:
  - `interface FetchedRates { bcv?: number; binance?: number }`
  - `interface RateProvider { name: string; fetch(): Promise<FetchedRates> }`
  - `parsePyDolar(json: unknown): FetchedRates` / `parseDolarApiOficial(json: unknown): FetchedRates` (puras).
  - `fetchRatesWithFallback(providers?: RateProvider[]): Promise<FetchedRates>` — recorre la cadena acumulando lo que falte; los errores pasan al siguiente.
  - `storeFetchedRates(d: CuentasDB, hoyIso: string, r: FetchedRates): Promise<number>` — guarda filas BCV/BINANCE de hoy, devuelve cuántas.
  - `refreshRatesOncePerDay(d: CuentasDB, hoyIso: string, providers?: RateProvider[]): Promise<void>` — salta si ya se hizo hoy o no hay conexión.

**IMPORTANTE:** las URLs y paths de los parsers de abajo siguen la forma canónica esperada; el spike (Tarea 2) es la fuente de verdad. Si tu fixture capturada difiere (otro path para el precio, otra URL), ajusta parser y URL aquí — el test contra la fixture real es lo que manda. Si el spike descartó un proveedor (CORS), elimina su parser, su fixture y sus tests, y deja la cadena solo con los que funcionaron.

- [ ] **Paso 1: Test `src/rates/providers.test.ts`:**

```ts
import { parsePyDolar, parseDolarApiOficial, fetchRatesWithFallback, refreshRatesOncePerDay, type RateProvider } from './providers';
import { CuentasDB, seedIfEmpty, getSettings } from '../db/db';
import pydolarve from './fixtures/pydolarve.json';
import dolarapi from './fixtures/dolarapi-oficial.json';

test('parsePyDolar extrae BCV y Binance de la fixture real', () => {
  const r = parsePyDolar(pydolarve);
  expect(r.bcv).toBeGreaterThan(0);
  expect(r.binance).toBeGreaterThan(0);
});

test('parseDolarApiOficial extrae el promedio', () => {
  const r = parseDolarApiOficial(dolarapi);
  expect(r.bcv).toBeGreaterThan(0);
});

test('parsers devuelven {} ante basura', () => {
  expect(parsePyDolar({})).toEqual({});
  expect(parseDolarApiOficial({ promedio: 'nope' })).toEqual({});
});

const ok = (r: { bcv?: number; binance?: number }): RateProvider => ({ name: 'ok', fetch: async () => r });
const roto: RateProvider = { name: 'roto', fetch: async () => { throw new Error('CORS'); } };

test('fallback: el primero falla, el segundo responde', async () => {
  expect(await fetchRatesWithFallback([roto, ok({ bcv: 111 })])).toEqual({ bcv: 111 });
});

test('fallback acumula: el primero trae solo BCV, el segundo completa Binance', async () => {
  expect(await fetchRatesWithFallback([ok({ bcv: 111 }), ok({ bcv: 999, binance: 130 })]))
    .toEqual({ bcv: 111, binance: 130 });
});

test('todos fallan: objeto vacío (la app sigue con última tasa/manual)', async () => {
  expect(await fetchRatesWithFallback([roto, roto])).toEqual({});
});

test('refreshRatesOncePerDay guarda hoy y no repite', async () => {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  await refreshRatesOncePerDay(d, '2026-07-07', [ok({ bcv: 111, binance: 130 })]);
  expect(await d.rates.count()).toBe(2);
  expect((await getSettings(d)).lastRatesFetchDate).toBe('2026-07-07');
  await refreshRatesOncePerDay(d, '2026-07-07', [ok({ bcv: 999 })]); // mismo día: no toca nada
  expect((await d.rates.get(['2026-07-07', 'BCV']))?.vesPerUsd).toBe(111);
});
```

Verifica que falla: `npx vitest run src/rates/providers.test.ts` → FAIL. (Si `import ... .json` no compila, confirma que añadiste `"resolveJsonModule": true` al tsconfig.)

- [ ] **Paso 2: Implementar `src/rates/providers.ts`:**

```ts
import type { Rate } from '../domain/types';
import { CuentasDB, getSettings, saveSettings } from '../db/db';

export interface FetchedRates {
  bcv?: number;
  binance?: number;
}

export interface RateProvider {
  name: string;
  fetch(): Promise<FetchedRates>;
}

/** Forma observada en el spike: { monitors: { bcv: { price }, binance: { price } } }. */
export function parsePyDolar(json: unknown): FetchedRates {
  const j = json as { monitors?: Record<string, { price?: unknown }> };
  const out: FetchedRates = {};
  const bcv = j?.monitors?.bcv?.price;
  const binance = j?.monitors?.binance?.price;
  if (typeof bcv === 'number' && bcv > 0) out.bcv = bcv;
  if (typeof binance === 'number' && binance > 0) out.binance = binance;
  return out;
}

/** Forma observada en el spike: { promedio: number, ... }. */
export function parseDolarApiOficial(json: unknown): FetchedRates {
  const j = json as { promedio?: unknown };
  return typeof j?.promedio === 'number' && j.promedio > 0 ? { bcv: j.promedio } : {};
}

async function fetchJson(url: string): Promise<unknown> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 8000);
  try {
    const res = await fetch(url, { signal: ctl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

// URLs confirmadas en docs/spikes/2026-07-07-cors-tasas.md — mantener sincronizadas.
export const pyDolarProvider: RateProvider = {
  name: 'pydolarve',
  fetch: async () => parsePyDolar(await fetchJson('https://pydolarve.org/api/v2/dollar?page=criptodolar')),
};

export const dolarApiProvider: RateProvider = {
  name: 'dolarapi',
  fetch: async () => parseDolarApiOficial(await fetchJson('https://ve.dolarapi.com/v1/dolares/oficial')),
};

export const PROVIDERS_DEFAULT: RateProvider[] = [pyDolarProvider, dolarApiProvider];

/** Recorre la cadena acumulando lo que falte; errores de red/CORS pasan al siguiente. */
export async function fetchRatesWithFallback(providers: RateProvider[] = PROVIDERS_DEFAULT): Promise<FetchedRates> {
  const out: FetchedRates = {};
  for (const p of providers) {
    if (out.bcv !== undefined && out.binance !== undefined) break;
    try {
      const r = await p.fetch();
      out.bcv ??= r.bcv;
      out.binance ??= r.binance;
    } catch {
      // siguiente proveedor; el último recurso siempre es la tasa manual
    }
  }
  return out;
}

export async function storeFetchedRates(d: CuentasDB, hoyIso: string, r: FetchedRates): Promise<number> {
  const filas: Rate[] = [];
  if (r.bcv) filas.push({ date: hoyIso, source: 'BCV', vesPerUsd: r.bcv });
  if (r.binance) filas.push({ date: hoyIso, source: 'BINANCE', vesPerUsd: r.binance });
  if (filas.length > 0) await d.rates.bulkPut(filas);
  return filas.length;
}

export async function refreshRatesOncePerDay(
  d: CuentasDB,
  hoyIso: string,
  providers: RateProvider[] = PROVIDERS_DEFAULT,
): Promise<void> {
  const s = await getSettings(d);
  if (s.lastRatesFetchDate === hoyIso) return;
  if (typeof navigator !== 'undefined' && !navigator.onLine) return;
  const guardadas = await storeFetchedRates(d, hoyIso, await fetchRatesWithFallback(providers));
  if (guardadas > 0) await saveSettings(d, { lastRatesFetchDate: hoyIso });
}
```

`npx vitest run src/rates/providers.test.ts` → PASS.

- [ ] **Paso 3: Sección de UI** — `src/ui/componentes/TasasSection.tsx`:

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getSettings, saveSettings } from '../../db/db';
import type { RateSource } from '../../domain/types';
import { effectiveRate } from '../../domain/rates';
import { todayIso } from '../../domain/dates';
import { fetchRatesWithFallback, storeFetchedRates } from '../../rates/providers';
import { avisar } from '../Toast';

export function TasasSection() {
  const rates = useLiveQuery(() => db.rates.orderBy('date').reverse().limit(14).toArray(), []) ?? [];
  const settings = useLiveQuery(() => getSettings(db), []);
  const [fecha, setFecha] = useState(todayIso());
  const [valorTxt, setValorTxt] = useState('');
  const [buscando, setBuscando] = useState(false);
  if (!settings) return null;

  const vigente = effectiveRate(todayIso(), settings.rateSource, rates);

  async function actualizarAhora() {
    setBuscando(true);
    try {
      const n = await storeFetchedRates(db, todayIso(), await fetchRatesWithFallback());
      avisar(n > 0 ? `Tasas actualizadas (${n}) ✓` : 'Ningún proveedor respondió — usa la tasa manual');
    } finally {
      setBuscando(false);
    }
  }

  async function guardarManual() {
    const v = Number(valorTxt.replace(',', '.'));
    if (!Number.isFinite(v) || v <= 0 || !/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
      avisar('Tasa o fecha inválida');
      return;
    }
    await db.rates.put({ date: fecha, source: 'MANUAL', vesPerUsd: v });
    setValorTxt('');
    avisar('Tasa manual guardada ✓');
  }

  return (
    <div>
      <p>
        Vigente hoy: <strong>{vigente ? `${vigente.vesPerUsd} Bs/$ (${vigente.source} ${vigente.date})` : 'sin tasa'}</strong>
      </p>
      <label>Fuente activa para conversiones</label>
      <select
        aria-label="Fuente de tasa"
        value={settings.rateSource}
        onChange={(e) => saveSettings(db, { rateSource: e.target.value as RateSource })}
      >
        <option value="BCV">BCV</option>
        <option value="BINANCE">Binance USDT/VES</option>
        <option value="MANUAL">Manual</option>
      </select>
      <button className="boton secundario" style={{ marginTop: 8 }} onClick={actualizarAhora} disabled={buscando}>
        {buscando ? 'Buscando…' : 'Actualizar tasas ahora'}
      </button>
      <label>Corregir/registrar tasa manual (Bs por $)</label>
      <div className="fila-form">
        <input aria-label="Fecha de tasa" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        <input aria-label="Valor de tasa" inputMode="decimal" placeholder="ej. 111,02" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
        <button className="boton" onClick={guardarManual}>Guardar</button>
      </div>
      <h4 style={{ margin: '12px 0 4px' }}>Histórico reciente</h4>
      <table className="tabla">
        <thead><tr><th>Fecha</th><th>Fuente</th><th>Bs/$</th></tr></thead>
        <tbody>
          {rates.map((r) => (
            <tr key={`${r.date}-${r.source}`}>
              <td>{r.date}</td>
              <td><span className="chip">{r.source}</span></td>
              <td>{r.vesPerUsd}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

`src/ui/paginas/AjustesPage.tsx` (reemplazar el stub; deja los huecos comentados para las Tareas 14 y 23):

```tsx
import { TasasSection } from '../componentes/TasasSection';

export function AjustesPage() {
  return (
    <div>
      <h2>Ajustes</h2>
      <div className="tarjeta">
        <h3>Tasas de cambio</h3>
        <TasasSection />
      </div>
      {/* Tarea 14: <CuentasSection /> <CategoriasSection /> <ReglasSection /> */}
      {/* Tarea 23: <RespaldoSection /> */}
    </div>
  );
}
```

`src/ui/App.tsx` — añadir el auto-fetch dentro del `useEffect` existente:

```tsx
import { refreshRatesOncePerDay } from '../rates/providers';
import { todayIso } from '../domain/dates';
// ...
useEffect(() => {
  seedIfEmpty(db)
    .then(() => setListo(true))
    .then(() => refreshRatesOncePerDay(db, todayIso()))
    .catch(() => {}); // sin red o sin proveedor: silencioso, la UI muestra la tasa vieja
}, []);
```

- [ ] **Paso 4: Verificar.** `npx vitest run` → verde. Manual: en Ajustes → "Actualizar tasas ahora" debe traer tasas reales (spike ya confirmó CORS); registra un override manual y comprueba en el dashboard que la fuente MANUAL de hoy gana.

- [ ] **Paso 5: Commit.**

```powershell
git add src/rates src/ui tsconfig.json
git commit -m "Tasas: proveedores con fallback, fetch diario automático y sección de ajustes

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 14: Ajustes — cuentas, categorías (con presupuesto) y reglas

**Files:**
- Create: `src/ui/componentes/CuentasSection.tsx`, `src/ui/componentes/CategoriasSection.tsx`, `src/ui/componentes/ReglasSection.tsx`
- Modify: `src/ui/paginas/AjustesPage.tsx`
- Test: `src/ui/componentes/CuentasSection.test.tsx`

**Interfaces:**
- Consumes: `db`; entidades; `parseUsAmount`, `formatMoney` (Tarea 3); clases CSS (Tarea 7).
- Produces: CRUD de cuentas (crear/editar/archivar — spec §3), CRUD de categorías incluyendo `monthlyBudgetMinor` en USD (spec §4.7) y listado/borrado de reglas aprendidas (spec §4.5).

- [ ] **Paso 1: Test** — `src/ui/componentes/CuentasSection.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CuentasSection } from './CuentasSection';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';

beforeEach(async () => { await limpiarDb(); });

test('crea una cuenta nueva', async () => {
  const user = userEvent.setup();
  render(<CuentasSection />);
  await user.click(await screen.findByRole('button', { name: 'Nueva cuenta' }));
  await user.type(screen.getByLabelText('Nombre de cuenta'), 'Zelle');
  await user.selectOptions(screen.getByLabelText('Moneda'), 'USD');
  await user.selectOptions(screen.getByLabelText('Tipo'), 'wallet');
  await user.click(screen.getByRole('button', { name: 'Guardar cuenta' }));
  await waitFor(async () => expect(await db.accounts.count()).toBe(6));
  const zelle = (await db.accounts.toArray()).find((a) => a.name === 'Zelle');
  expect(zelle).toMatchObject({ currency: 'USD', kind: 'wallet', isSavings: false, archived: false });
});

test('archiva una cuenta existente', async () => {
  const user = userEvent.setup();
  render(<CuentasSection />);
  await user.click(await screen.findByRole('button', { name: 'Editar Efectivo ahorro' }));
  await user.click(screen.getByLabelText('Archivada'));
  await user.click(screen.getByRole('button', { name: 'Guardar cuenta' }));
  await waitFor(async () => expect((await db.accounts.get('acc-efectivo-ahorro'))?.archived).toBe(true));
});
```

Verifica que falla.

- [ ] **Paso 2: Implementar.** `src/ui/componentes/CuentasSection.tsx`:

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Account, AccountKind, Currency } from '../../domain/types';
import { avisar } from '../Toast';

const NUEVA = (): Account => ({
  id: crypto.randomUUID(), name: '', currency: 'USD', kind: 'wallet', isSavings: false, archived: false,
});

const TIPOS: { valor: AccountKind; texto: string }[] = [
  { valor: 'bank', texto: 'Banco' },
  { valor: 'wallet', texto: 'Billetera' },
  { valor: 'snapshot', texto: 'Solo saldo (ajustes manuales)' },
  { valor: 'cash', texto: 'Efectivo' },
];

export function CuentasSection() {
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const [editando, setEditando] = useState<Account | null>(null);

  async function guardar() {
    if (!editando) return;
    if (!editando.name.trim()) { avisar('Ponle nombre a la cuenta'); return; }
    await db.accounts.put({ ...editando, name: editando.name.trim() });
    setEditando(null);
    avisar('Cuenta guardada ✓');
  }

  return (
    <div>
      {cuentas.map((c) => (
        <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
          <span style={{ color: c.archived ? 'var(--texto2)' : undefined }}>
            {c.name} <span className="chip">{c.currency === 'VES' ? 'Bs' : '$'}</span>
            {c.isSavings && <span className="chip" style={{ marginLeft: 4 }}>ahorro</span>}
            {c.archived && <span className="chip" style={{ marginLeft: 4 }}>archivada</span>}
          </span>
          <button className="chip" aria-label={`Editar ${c.name}`} onClick={() => setEditando(c)}>editar</button>
        </div>
      ))}
      <button className="boton secundario" onClick={() => setEditando(NUEVA())}>Nueva cuenta</button>

      {editando && (
        <div className="modal-fondo" onClick={() => setEditando(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{cuentas.some((c) => c.id === editando.id) ? 'Editar cuenta' : 'Nueva cuenta'}</h3>
            <label>Nombre</label>
            <input aria-label="Nombre de cuenta" value={editando.name} onChange={(e) => setEditando({ ...editando, name: e.target.value })} />
            <label>Moneda (no convierte movimientos existentes)</label>
            <select aria-label="Moneda" value={editando.currency} onChange={(e) => setEditando({ ...editando, currency: e.target.value as Currency })}>
              <option value="USD">USD ($)</option>
              <option value="VES">Bolívares (Bs)</option>
            </select>
            <label>Tipo</label>
            <select aria-label="Tipo" value={editando.kind} onChange={(e) => setEditando({ ...editando, kind: e.target.value as AccountKind })}>
              {TIPOS.map((t) => <option key={t.valor} value={t.valor}>{t.texto}</option>)}
            </select>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" style={{ width: 'auto' }} aria-label="Es ahorro" checked={editando.isSavings} onChange={(e) => setEditando({ ...editando, isSavings: e.target.checked })} />
              Cuenta de ahorro (no cuenta como disposición)
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" style={{ width: 'auto' }} aria-label="Archivada" checked={editando.archived} onChange={(e) => setEditando({ ...editando, archived: e.target.checked })} />
              Archivada (oculta del dashboard y formularios)
            </label>
            <div className="fila-form" style={{ marginTop: 12 }}>
              <button className="boton secundario" onClick={() => setEditando(null)}>Cancelar</button>
              <button className="boton" onClick={guardar}>Guardar cuenta</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

`src/ui/componentes/CategoriasSection.tsx` (mismo patrón; el presupuesto se introduce en dólares con punto o coma):

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Category, CategoryKind } from '../../domain/types';
import { formatMoney } from '../../domain/money';
import { avisar } from '../Toast';

const NUEVA = (): Category => ({
  id: crypto.randomUUID(), name: '', icon: '📦', color: '#6b7280', kind: 'expense',
});

export function CategoriasSection() {
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [editando, setEditando] = useState<Category | null>(null);
  const [presupuestoTxt, setPresupuestoTxt] = useState('');

  function abrir(c: Category) {
    setEditando(c);
    setPresupuestoTxt(c.monthlyBudgetMinor ? (c.monthlyBudgetMinor / 100).toFixed(2) : '');
  }

  async function guardar() {
    if (!editando) return;
    if (!editando.name.trim()) { avisar('Ponle nombre a la categoría'); return; }
    const limpio = presupuestoTxt.trim().replace(',', '.');
    let monthlyBudgetMinor: number | undefined;
    if (limpio !== '') {
      const v = Number(limpio);
      if (!Number.isFinite(v) || v <= 0) { avisar('Presupuesto inválido'); return; }
      monthlyBudgetMinor = Math.round(v * 100);
    }
    await db.categories.put({ ...editando, name: editando.name.trim(), monthlyBudgetMinor });
    setEditando(null);
    avisar('Categoría guardada ✓');
  }

  return (
    <div>
      {categorias.map((c) => (
        <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
          <span>
            {c.icon} {c.name} <span className="chip">{c.kind === 'expense' ? 'gasto' : 'ingreso'}</span>
            {c.monthlyBudgetMinor ? <span className="chip" style={{ marginLeft: 4 }}>presup. {formatMoney(c.monthlyBudgetMinor, 'USD')}</span> : null}
          </span>
          <button className="chip" aria-label={`Editar ${c.name}`} onClick={() => abrir(c)}>editar</button>
        </div>
      ))}
      <button className="boton secundario" onClick={() => abrir(NUEVA())}>Nueva categoría</button>

      {editando && (
        <div className="modal-fondo" onClick={() => setEditando(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Categoría</h3>
            <label>Nombre</label>
            <input aria-label="Nombre de categoría" value={editando.name} onChange={(e) => setEditando({ ...editando, name: e.target.value })} />
            <div className="fila-form">
              <div>
                <label>Icono (emoji)</label>
                <input aria-label="Icono" value={editando.icon} onChange={(e) => setEditando({ ...editando, icon: e.target.value })} />
              </div>
              <div>
                <label>Color</label>
                <input aria-label="Color" type="color" value={editando.color} onChange={(e) => setEditando({ ...editando, color: e.target.value })} />
              </div>
            </div>
            <label>Tipo</label>
            <select aria-label="Tipo de categoría" value={editando.kind} onChange={(e) => setEditando({ ...editando, kind: e.target.value as CategoryKind })}>
              <option value="expense">Gasto</option>
              <option value="income">Ingreso</option>
            </select>
            <label>Presupuesto mensual en $ (vacío = sin presupuesto)</label>
            <input aria-label="Presupuesto" inputMode="decimal" placeholder="ej. 50.00" value={presupuestoTxt} onChange={(e) => setPresupuestoTxt(e.target.value)} />
            <div className="fila-form" style={{ marginTop: 12 }}>
              <button className="boton secundario" onClick={() => setEditando(null)}>Cancelar</button>
              <button className="boton" onClick={guardar}>Guardar categoría</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

`src/ui/componentes/ReglasSection.tsx`:

```tsx
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';

export function ReglasSection() {
  const reglas = useLiveQuery(() => db.rules.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const nombre = (id: string) => categorias.find((c) => c.id === id)?.name ?? '?';

  if (reglas.length === 0) return <p style={{ color: 'var(--texto2)' }}>Aún no hay reglas. Se crean al categorizar en los imports.</p>;
  return (
    <div>
      {reglas.map((r) => (
        <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
          <span>«{r.pattern}» → {nombre(r.categoryId)}</span>
          <button className="chip" onClick={() => db.rules.delete(r.id)}>eliminar</button>
        </div>
      ))}
    </div>
  );
}
```

`src/ui/paginas/AjustesPage.tsx` — añadir las secciones:

```tsx
import { CuentasSection } from '../componentes/CuentasSection';
import { CategoriasSection } from '../componentes/CategoriasSection';
import { ReglasSection } from '../componentes/ReglasSection';
// ...debajo de la tarjeta de Tasas:
<div className="tarjeta"><h3>Cuentas</h3><CuentasSection /></div>
<div className="tarjeta"><h3>Categorías</h3><CategoriasSection /></div>
<div className="tarjeta"><h3>Reglas aprendidas</h3><ReglasSection /></div>
```

- [ ] **Paso 3: Verificar y commit.**

```powershell
npx vitest run
git add src/ui
git commit -m "Ajustes: CRUD de cuentas y categorías con presupuesto, y reglas aprendidas

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

**Fin de fase:** `npm run build` sin errores.

---

## Fase 5 — Importador BNC (TXT tabulado)

Contexto del formato real (verificado contra `TXTBNC1.txt`, que es UTF-8 sin BOM con CRLF):
- Línea 1: metadatos `numeroCuenta@TITULAR@25 ULTIMOS MOVIMIENTOS` separados por `@`. Línea 2 vacía.
- Línea 3: cabecera tab-separated exacta: `Fecha	Hora	Referencia	Cod. Transacción	Tipo Transacción	Tip. Operación	Descripción	Debe	Haber	Saldo	Referencia 2`.
- Sigue una **pseudo-fila "Saldo Inicial"** (Referencia vacía, `Tip. Operación = Saldo Inicial`, Debe/Haber = 0) que se reconoce y se ignora (su semántica no es confiable).
- Filas de datos en **orden descendente** (más reciente primero). Números con coma decimal, sin separador de miles; `Debe` ≤ 0, `Haber` ≥ 0; la hora puede traer milisegundos (`15:08:30.638`); `Tipo Transacción` puede venir vacío; el export trae artefactos float (`87,6999999999998`) que el parseo redondea a céntimos.
- Los pagos móviles generan **dos filas con la misma Referencia** (cargo + comisión, con distinto monto y milisegundos distintos).
- La columna `Saldo` encadena: `saldo[i] − monto[i] = saldo[i+1]` (con filas descendentes), lo que permite validar el archivo y detectar huecos entre imports.

### Tarea 15: Motor de reglas de categorización

**Files:**
- Create: `src/domain/rules.ts`
- Test: `src/domain/rules.test.ts`

**Interfaces:**
- Consumes: `Rule` de `./types`.
- Produces:
  - `matchCategory(descripcion: string, rules: Rule[]): Rule | null` — substring case-insensitive; gana la primera regla que matchee (orden del array); patrones vacíos nunca matchean.
  - `buildRule(pattern: string, categoryId: string, autoCreated?: boolean): Rule` — trim; lanza `Error('Patrón vacío')`; `autoCreated` por defecto `true`.

- [ ] **Paso 1: Test `src/domain/rules.test.ts`:**

```ts
import type { Rule } from './types';
import { matchCategory, buildRule } from './rules';

const reglas: Rule[] = [
  { id: 'r1', pattern: 'juegaenlinea', categoryId: 'cat-ocio', autoCreated: true },
  { id: 'r2', pattern: 'FARMACIA', categoryId: 'cat-salud', autoCreated: true },
  { id: 'r3', pattern: 'farma', categoryId: 'cat-otros-gastos', autoCreated: true },
];

test('matchea substring sin importar mayúsculas', () => {
  expect(matchCategory('Pago Móvil BNC al Instante JUEGAENLINEA3', reglas)?.categoryId).toBe('cat-ocio');
  expect(matchCategory('farmacia central c a', reglas)?.categoryId).toBe('cat-salud');
});
test('gana la primera regla del array', () => {
  expect(matchCategory('FARMACIA AVILA', reglas)?.id).toBe('r2'); // no r3
});
test('sin match devuelve null', () => {
  expect(matchCategory('MERCADO LA ESQUINA', reglas)).toBeNull();
});
test('patrón vacío nunca matchea', () => {
  expect(matchCategory('cualquier cosa', [{ id: 'x', pattern: '  ', categoryId: 'c', autoCreated: true }])).toBeNull();
});
test('buildRule recorta y valida', () => {
  expect(buildRule('  Netflix  ', 'cat-suscripciones')).toMatchObject({ pattern: 'Netflix', categoryId: 'cat-suscripciones', autoCreated: true });
  expect(() => buildRule('   ', 'c')).toThrow('Patrón vacío');
});
```

- [ ] **Paso 2: Verificar que falla, implementar `src/domain/rules.ts`:**

```ts
import type { Rule } from './types';

/** Primera regla cuyo patrón (substring, case-insensitive) aparece en la descripción. */
export function matchCategory(descripcion: string, rules: Rule[]): Rule | null {
  const d = descripcion.toLowerCase();
  return rules.find((r) => {
    const p = r.pattern.trim().toLowerCase();
    return p !== '' && d.includes(p);
  }) ?? null;
}

export function buildRule(pattern: string, categoryId: string, autoCreated = true): Rule {
  const p = pattern.trim();
  if (!p) throw new Error('Patrón vacío');
  return { id: crypto.randomUUID(), pattern: p, categoryId, autoCreated };
}
```

- [ ] **Paso 3: Verificar y commit.**

```powershell
npx vitest run src/domain/rules.test.ts
npx vitest run
git add src/domain/rules.ts src/domain/rules.test.ts
git commit -m "Motor de reglas de categorización por substring

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 16: Parser del TXT de BNC

**Files:**
- Create: `src/import/bnc/parseBncTxt.ts`, `src/import/bnc/fixtures/bncFixture.ts`
- Test: `src/import/bnc/parseBncTxt.test.ts`

**Interfaces:**
- Consumes: `parseVenezuelanAmount` (Tarea 3), `isoFromDdMmYyyy` (Tarea 3).
- Produces:
  - `interface BncMeta { accountNumber: string; holder: string; title: string }`
  - `interface BncRow { date: string; time: string; referencia: string; codTransaccion: string; tipoTransaccion: string; tipOperacion: string; descripcion: string; amountMinor: number; saldoMinor: number; referencia2: string }` — `amountMinor = debe + haber` con signo (gastos negativos).
  - `interface BncUnparsed { lineNumber: number; raw: string; reason: string }`
  - `interface BncParseResult { meta: BncMeta | null; rows: BncRow[]; unparsed: BncUnparsed[] }` — `rows` conserva el orden del archivo (descendente).
  - `parseBncTxt(text: string): BncParseResult`
  - `validateChain(rows: BncRow[]): number[]` — índices `i` donde `rows[i].saldoMinor − rows[i].amountMinor ≠ rows[i+1].saldoMinor`.

- [ ] **Paso 1: Crear la fixture anonimizada** — `src/import/bnc/fixtures/bncFixture.ts`. Datos 100% inventados con la estructura real (¡NO sustituir por datos del TXT real!). El encadenado de saldos es consistente a propósito y la fila 2 trae el artefacto float:

```ts
const F = (campos: string[]) => campos.join('\t');

/** 9 movimientos inventados con la estructura exacta del export BNC "25 últimos movimientos". */
export const BNC_FIXTURE = [
  '01910000000000001234@PEREZ RODRIGUEZ MARIA JOSE@25 ULTIMOS MOVIMIENTOS',
  '',
  F(['Fecha', 'Hora', 'Referencia', 'Cod. Transacción', 'Tipo Transacción', 'Tip. Operación', 'Descripción', 'Debe', 'Haber', 'Saldo', 'Referencia 2']),
  F(['02/07/2026', '12:00:00', '', '', '', 'Saldo Inicial', '', '0', '0', '6300,01', '', '']),
  F(['02/07/2026', '16:20:30.900', '100000007', '388', 'P2POTR', 'Abono Pago Movil BNC', 'TELF.:584160000003 BANCO:0102 CED.:V00000003 PMPP PagomóvilBDV ', '0', '1500', '6300,01', '584140000000']),
  F(['01/07/2026', '12:34:56.789', '100000006', '377', '', 'Compra de POS DebitMC', 'PANADERIA CENTRAL C A  CCS ESTE      VEN ZONA= BANCO:0102        TERMINAL:00000002 COMER:0022222222', '-345,79', '0', '4800,0099999999998', '12345']),
  F(['30/06/2026', '02:00:00.000', '0', '750', 'COMSAL', 'Comisión Mantenimiento Cuenta', 'Comisión Mantenimiento Cuenta Mes: Junio     - 2026  ', '-0,1', '0', '5145,8', '0']),
  F(['24/06/2026', '10:10:10.500', '100000005', '488', 'CIPOTR', 'Credito Inmediato Recibido', 'TELF.:000000000000 CTA.: 01050000000000000001 BANCO:0105 PAGO MOVIL. EMISOR : PEREZ GOMEZ ANA', '0', '5000', '5145,9', '4140000000']),
  F(['23/06/2026', '08:00:05.410', '100000004', '751', 'P2PCSO', 'Comisión Pago Movil', 'TELF.:584120000002 CED.:000000002 BANCO:0134 Pago Móvil BNC al Instante FARMACIA AVILA', '-3,6', '0', '145,9', '584140000000']),
  F(['23/06/2026', '08:00:05.400', '100000004', '387', 'P2PTSO', 'Cargo Pago Movil BNC', 'TELF.:584120000002 CED.:000000002 BANCO:0134 Pago Móvil BNC al Instante FARMACIA AVILA', '-1200', '0', '149,5', '584140000000']),
  F(['22/06/2026', '18:30:00.300', '100000003', '387', 'P2CTRO', 'Cargo Pago Movil BNC', 'TELF.:584140000001 CED.:000000001 BANCO:0169 Pago Móvil BNC al Instante JUEGAENLINEA3', '-300', '0', '1349,5', '584140000000']),
  F(['21/06/2026', '13:05:10.200', '100000002', '377', 'MAESTR', 'Compra de POS DebitMC', 'MERCADO LA ESQUINA CA  CCS CENTRO    VEN ZONA= BANCO:0102        TERMINAL:00000001 COMER:0011111111', '-850,5', '0', '1649,5', '54321']),
  F(['20/06/2026', '09:15:00.100', '100000001', '488', 'CIPOTR', 'Credito Inmediato Recibido', 'TELF.:000000000000 CTA.: 01050000000000000001 BANCO:0105 PAGO MOVIL. EMISOR : PEREZ GOMEZ ANA', '0', '2000', '2500', '4140000000']),
].join('\r\n');
```

Referencia del encadenado (para entender los tests): saldo más nuevo 6.300,01; la fila más vieja parte de un saldo previo implícito de 500,00 Bs (250000 − 200000 en céntimos).

- [ ] **Paso 2: Test `src/import/bnc/parseBncTxt.test.ts`:**

```ts
import { parseBncTxt, validateChain } from './parseBncTxt';
import { BNC_FIXTURE } from './fixtures/bncFixture';

test('extrae metadatos de la línea 1', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(r.meta).toEqual({
    accountNumber: '01910000000000001234',
    holder: 'PEREZ RODRIGUEZ MARIA JOSE',
    title: '25 ULTIMOS MOVIMIENTOS',
  });
});

test('parsea las 9 filas de datos, ignorando cabecera y pseudo-fila Saldo Inicial', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(r.rows).toHaveLength(9);
  expect(r.unparsed).toHaveLength(0);
  const primera = r.rows[0]; // orden del archivo: la más reciente primero
  expect(primera).toMatchObject({
    date: '2026-07-02', time: '16:20:30.900', referencia: '100000007',
    tipOperacion: 'Abono Pago Movil BNC', amountMinor: 150000, saldoMinor: 630001,
  });
});

test('combina Debe/Haber con signo y redondea artefactos float del saldo', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(r.rows[1].amountMinor).toBe(-34579);          // compra POS
  expect(r.rows[1].saldoMinor).toBe(480001);           // '4800,0099999999998' → céntimos
  expect(r.rows[2].amountMinor).toBe(-10);             // comisión de 0,10 Bs
  expect(r.rows[4].tipoTransaccion).toBe('P2PCSO');
  expect(r.rows[1].tipoTransaccion).toBe('');          // puede venir vacío
});

test('el par cargo+comisión comparte referencia pero difiere en monto y hora', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  const par = r.rows.filter((x) => x.referencia === '100000004');
  expect(par).toHaveLength(2);
  expect(par.map((x) => x.amountMinor).sort((a, b) => a - b)).toEqual([-120000, -360]);
});

test('una línea corrupta va a unparsed con motivo, sin tumbar el resto', () => {
  const roto = `${BNC_FIXTURE}\r\nesto no es una fila\r\n03/07/2026\t99:99\tx\t1\t2\t3\t4\tno-numero\t0\t0\t0`;
  const r = parseBncTxt(roto);
  expect(r.rows).toHaveLength(9);
  expect(r.unparsed).toHaveLength(2);
  expect(r.unparsed[0].raw).toBe('esto no es una fila');
  expect(r.unparsed[0].lineNumber).toBeGreaterThan(10);
  expect(r.unparsed[0].reason).toBeTruthy();
});

test('validateChain: la fixture encadena perfecta; una fila alterada rompe', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(validateChain(r.rows)).toEqual([]);
  const alteradas = r.rows.map((x, i) => (i === 3 ? { ...x, saldoMinor: x.saldoMinor + 100 } : x));
  expect(validateChain(alteradas)).toEqual([2, 3]); // rompe contra la anterior y la siguiente
});
```

Verifica que falla: `npx vitest run src/import/bnc/parseBncTxt.test.ts` → FAIL.

- [ ] **Paso 3: Implementar `src/import/bnc/parseBncTxt.ts`:**

```ts
import { parseVenezuelanAmount } from '../../domain/money';
import { isoFromDdMmYyyy } from '../../domain/dates';

export interface BncMeta {
  accountNumber: string;
  holder: string;
  title: string;
}

export interface BncRow {
  date: string;
  time: string;
  referencia: string;
  codTransaccion: string;
  tipoTransaccion: string;
  tipOperacion: string;
  descripcion: string;
  amountMinor: number;
  saldoMinor: number;
  referencia2: string;
}

export interface BncUnparsed {
  lineNumber: number;
  raw: string;
  reason: string;
}

export interface BncParseResult {
  meta: BncMeta | null;
  rows: BncRow[];
  unparsed: BncUnparsed[];
}

const HORA_RE = /^\d{2}:\d{2}:\d{2}(\.\d+)?$/;

export function parseBncTxt(text: string): BncParseResult {
  const lineas = text.split(/\r?\n/);
  const result: BncParseResult = { meta: null, rows: [], unparsed: [] };

  lineas.forEach((linea, idx) => {
    const lineNumber = idx + 1;
    const raw = linea.replace(/\s+$/, '');
    if (raw.trim() === '') return;

    // Metadatos: única línea sin tabs con separador '@'
    if (!raw.includes('\t') && raw.includes('@')) {
      const partes = raw.split('@');
      if (partes.length >= 3 && result.meta === null) {
        result.meta = { accountNumber: partes[0], holder: partes[1], title: partes.slice(2).join('@') };
      } else {
        result.unparsed.push({ lineNumber, raw, reason: 'metadatos irreconocibles' });
      }
      return;
    }

    if (raw.startsWith('Fecha\t')) return; // cabecera

    const f = raw.split('\t');
    if (f.length < 10) {
      result.unparsed.push({ lineNumber, raw, reason: `columnas insuficientes (${f.length})` });
      return;
    }
    const [fecha, hora, referencia, codTransaccion, tipoTransaccion, tipOperacion, descripcion, debe, haber, saldo, referencia2 = ''] = f;

    // Pseudo-fila "Saldo Inicial": se reconoce y se descarta (semántica no confiable)
    if (tipOperacion.trim() === 'Saldo Inicial' && referencia.trim() === '') return;

    const date = isoFromDdMmYyyy(fecha);
    if (!date) {
      result.unparsed.push({ lineNumber, raw, reason: `fecha inválida: "${fecha}"` });
      return;
    }
    if (!HORA_RE.test(hora.trim())) {
      result.unparsed.push({ lineNumber, raw, reason: `hora inválida: "${hora}"` });
      return;
    }
    const debeMinor = parseVenezuelanAmount(debe.trim() === '' ? '0' : debe);
    const haberMinor = parseVenezuelanAmount(haber.trim() === '' ? '0' : haber);
    const saldoMinor = parseVenezuelanAmount(saldo);
    if (debeMinor === null || haberMinor === null || saldoMinor === null) {
      result.unparsed.push({ lineNumber, raw, reason: 'monto ilegible' });
      return;
    }

    result.rows.push({
      date,
      time: hora.trim(),
      referencia: referencia.trim(),
      codTransaccion: codTransaccion.trim(),
      tipoTransaccion: tipoTransaccion.trim(),
      tipOperacion: tipOperacion.trim(),
      descripcion: descripcion.trim(),
      amountMinor: debeMinor + haberMinor,
      saldoMinor,
      referencia2: referencia2.trim(),
    });
  });

  return result;
}

/**
 * Con filas en orden de archivo (descendente), verifica saldo[i] − monto[i] === saldo[i+1].
 * Devuelve los índices i donde NO cuadra.
 */
export function validateChain(rows: BncRow[]): number[] {
  const rotos: number[] = [];
  for (let i = 0; i < rows.length - 1; i++) {
    if (rows[i].saldoMinor - rows[i].amountMinor !== rows[i + 1].saldoMinor) rotos.push(i);
  }
  return rotos;
}
```

- [ ] **Paso 4: Verificar y commit.**

```powershell
npx vitest run src/import/bnc/parseBncTxt.test.ts
npx vitest run
git add src/import
git commit -m "Parser del TXT tabulado de BNC con fixture anonimizada y validación de encadenado

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

- [ ] **Paso 5 (verificación manual con datos reales, NO commitear nada de esto):** en un scratch (`node --input-type=module`) o test temporal local, pasa el contenido de `TXTBNC1.txt` real por `parseBncTxt` y comprueba: 25 filas, 0 unparsed, `validateChain` vacío. Si algo falla, corrige el parser (y refleja el caso en la fixture con datos inventados) antes de seguir.

### Tarea 17: Mapper BNC — dedupe, pares, hueco y commit transaccional

**Files:**
- Create: `src/import/bnc/mapBnc.ts`, `src/db/commit.ts`
- Test: `src/import/bnc/mapBnc.test.ts`, `src/db/commit.test.ts`

**Interfaces:**
- Consumes: `BncRow` (Tarea 16); `matchCategory` (Tarea 15); entidades.
- Produces:
  - `bncImportKey(row: BncRow): string` — `` `bnc:${referencia}:${time}:${amountMinor}` `` (dedupe de la spec §4.5: referencia + hora + monto).
  - `interface BncDraft { tx: Transaction; saldoMinor: number; duplicate: boolean; isCommission: boolean; pairRef: string | null; matchedRuleId: string | null }`
  - `mapBncRows(rows: BncRow[], opts: { accountId: string; existingKeys: Set<string>; rules: Rule[]; categories: Category[] }): BncDraft[]` — signo→tipo (`haber`→income, `debe`→expense), descripción→`note`, filas con monto 0 se descartan; propuesta de categoría: 1º regla que matchee, 2º comisiones (si `tipOperacion` contiene "comisión") → `cat-comisiones` si existe, 3º ingresos con "pago móvil" en el texto → `cat-pago-movil-recibido` si existe, si no `undefined`.
  - `type GapStatus = 'primera-importacion' | 'solapado' | 'continuo' | 'hueco'`
  - `detectGap(rows: BncRow[], previo: { importKey?: string; saldoMinor?: number }): GapStatus`
  - `newestRow(rows: BncRow[]): BncRow | null` — la primera del archivo (descendente).
  - `commitImport(d: CuentasDB, opts: { txs: Transaction[]; newRules?: Rule[]; accountPatch?: { id: string; bncLastImportKey: string; bncLastSaldoMinor: number } }): Promise<void>` — todo-o-nada en una transacción Dexie.

- [ ] **Paso 1: Test `src/import/bnc/mapBnc.test.ts`:**

```ts
import type { Category, Rule } from '../../domain/types';
import { parseBncTxt } from './parseBncTxt';
import { BNC_FIXTURE } from './fixtures/bncFixture';
import { bncImportKey, mapBncRows, detectGap, newestRow } from './mapBnc';

const categorias: Category[] = [
  { id: 'cat-comisiones', name: 'Comisiones bancarias', icon: '🏦', color: '#000', kind: 'expense' },
  { id: 'cat-ocio', name: 'Ocio/Juegos', icon: '🎮', color: '#000', kind: 'expense' },
  { id: 'cat-pago-movil-recibido', name: 'Pago móvil recibido', icon: '📲', color: '#000', kind: 'income' },
];
const reglas: Rule[] = [{ id: 'r1', pattern: 'JUEGAENLINEA', categoryId: 'cat-ocio', autoCreated: true }];
const rows = parseBncTxt(BNC_FIXTURE).rows;
const opciones = { accountId: 'acc-bnc', existingKeys: new Set<string>(), rules: reglas, categories: categorias };

test('mapea filas a borradores de transacción', () => {
  const drafts = mapBncRows(rows, opciones);
  expect(drafts).toHaveLength(9);
  const abono = drafts[0];
  expect(abono.tx).toMatchObject({
    type: 'income', accountId: 'acc-bnc', amountMinor: 150000, date: '2026-07-02',
    time: '16:20:30.900', source: 'bnc',
  });
  expect(abono.tx.note).toContain('PagomóvilBDV');
  expect(abono.tx.importKey).toBe('bnc:100000007:16:20:30.900:150000');
  const gasto = drafts[1];
  expect(gasto.tx).toMatchObject({ type: 'expense', amountMinor: 34579 }); // valor absoluto
});

test('propone categorías: regla > comisión > pago móvil recibido', () => {
  const drafts = mapBncRows(rows, opciones);
  expect(drafts[6].tx.categoryId).toBe('cat-ocio');                 // regla JUEGAENLINEA
  expect(drafts[6].matchedRuleId).toBe('r1');
  expect(drafts[2].tx.categoryId).toBe('cat-comisiones');           // Comisión Mantenimiento
  expect(drafts[2].isCommission).toBe(true);
  expect(drafts[4].tx.categoryId).toBe('cat-comisiones');           // Comisión Pago Movil
  expect(drafts[0].tx.categoryId).toBe('cat-pago-movil-recibido');  // Abono Pago Movil
  expect(drafts[1].tx.categoryId).toBeUndefined();                  // POS sin regla
});

test('marca duplicados contra las claves existentes', () => {
  const existentes = new Set([bncImportKey(rows[0]), bncImportKey(rows[3])]);
  const drafts = mapBncRows(rows, { ...opciones, existingKeys: existentes });
  expect(drafts.filter((d) => d.duplicate)).toHaveLength(2);
});

test('detecta pares cargo+comisión por referencia compartida', () => {
  const drafts = mapBncRows(rows, opciones);
  const par = drafts.filter((d) => d.pairRef === '100000004');
  expect(par).toHaveLength(2);
  expect(drafts[1].pairRef).toBeNull(); // referencia única
});

test('detectGap distingue los cuatro estados', () => {
  expect(detectGap(rows, {})).toBe('primera-importacion');
  expect(detectGap(rows, { importKey: bncImportKey(rows[4]), saldoMinor: 999 })).toBe('solapado');
  expect(detectGap(rows, { importKey: 'bnc:viejo:x:1', saldoMinor: 50000 })).toBe('continuo'); // 500,00 Bs previos
  expect(detectGap(rows, { importKey: 'bnc:viejo:x:1', saldoMinor: 123 })).toBe('hueco');
});

test('newestRow es la primera fila del archivo', () => {
  expect(newestRow(rows)?.referencia).toBe('100000007');
  expect(newestRow([])).toBeNull();
});
```

- [ ] **Paso 2: Verificar que falla, implementar `src/import/bnc/mapBnc.ts`:**

```ts
import type { Category, Rule, Transaction } from '../../domain/types';
import { matchCategory } from '../../domain/rules';
import type { BncRow } from './parseBncTxt';

export function bncImportKey(row: BncRow): string {
  return `bnc:${row.referencia}:${row.time}:${row.amountMinor}`;
}

export interface BncDraft {
  tx: Transaction;
  saldoMinor: number;
  duplicate: boolean;
  isCommission: boolean;
  pairRef: string | null;
  matchedRuleId: string | null;
}

export function mapBncRows(rows: BncRow[], opts: {
  accountId: string;
  existingKeys: Set<string>;
  rules: Rule[];
  categories: Category[];
}): BncDraft[] {
  const porReferencia = new Map<string, number>();
  for (const r of rows) {
    if (r.referencia !== '' && r.referencia !== '0') {
      porReferencia.set(r.referencia, (porReferencia.get(r.referencia) ?? 0) + 1);
    }
  }
  const idComisiones = opts.categories.find((c) => c.id === 'cat-comisiones')?.id ?? null;
  const idPagoMovil = opts.categories.find((c) => c.id === 'cat-pago-movil-recibido')?.id ?? null;

  return rows
    .filter((r) => r.amountMinor !== 0)
    .map((r) => {
      const texto = `${r.tipOperacion} ${r.descripcion}`;
      const isCommission = /comisi[oó]n/i.test(r.tipOperacion);
      const regla = matchCategory(texto, opts.rules);
      const esIngreso = r.amountMinor > 0;
      let categoryId: string | undefined = regla?.categoryId;
      if (!categoryId && isCommission && idComisiones) categoryId = idComisiones;
      if (!categoryId && esIngreso && idPagoMovil && /pago m[oó]vil/i.test(texto)) categoryId = idPagoMovil;

      const importKey = bncImportKey(r);
      const tx: Transaction = {
        id: crypto.randomUUID(),
        type: esIngreso ? 'income' : 'expense',
        accountId: opts.accountId,
        amountMinor: Math.abs(r.amountMinor),
        date: r.date,
        time: r.time,
        categoryId,
        note: r.descripcion,
        source: 'bnc',
        importKey,
      };
      return {
        tx,
        saldoMinor: r.saldoMinor,
        duplicate: opts.existingKeys.has(importKey),
        isCommission,
        pairRef: (porReferencia.get(r.referencia) ?? 0) >= 2 ? r.referencia : null,
        matchedRuleId: regla?.id ?? null,
      };
    });
}

export type GapStatus = 'primera-importacion' | 'solapado' | 'continuo' | 'hueco';

/** rows en orden de archivo (descendente). `previo` viene de Account.bncLastImportKey/bncLastSaldoMinor. */
export function detectGap(rows: BncRow[], previo: { importKey?: string; saldoMinor?: number }): GapStatus {
  if (!previo.importKey || previo.saldoMinor === undefined) return 'primera-importacion';
  if (rows.some((r) => bncImportKey(r) === previo.importKey)) return 'solapado';
  const masVieja = rows[rows.length - 1];
  if (!masVieja) return 'hueco';
  const saldoAntes = masVieja.saldoMinor - masVieja.amountMinor;
  return saldoAntes === previo.saldoMinor ? 'continuo' : 'hueco';
}

export function newestRow(rows: BncRow[]): BncRow | null {
  return rows[0] ?? null;
}
```

`npx vitest run src/import/bnc/mapBnc.test.ts` → PASS.

- [ ] **Paso 3: Commit transaccional** — test `src/db/commit.test.ts`:

```ts
import { CuentasDB, seedIfEmpty } from './db';
import { commitImport } from './commit';
import type { Transaction } from '../domain/types';

const tx = (id: string): Transaction => ({
  id, type: 'expense', accountId: 'acc-bnc', amountMinor: 100,
  date: '2026-07-01', source: 'bnc', importKey: `bnc:${id}`,
});

test('inserta transacciones, reglas y parchea la cuenta atómicamente', async () => {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  await commitImport(d, {
    txs: [tx('a'), tx('b')],
    newRules: [{ id: 'r1', pattern: 'x', categoryId: 'cat-ocio', autoCreated: true }],
    accountPatch: { id: 'acc-bnc', bncLastImportKey: 'bnc:a', bncLastSaldoMinor: 630001 },
  });
  expect(await d.transactions.count()).toBe(2);
  expect(await d.rules.count()).toBe(1);
  expect((await d.accounts.get('acc-bnc'))?.bncLastSaldoMinor).toBe(630001);
});

test('todo-o-nada: un id duplicado revierte transacciones, reglas y parche', async () => {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  await d.transactions.add(tx('a'));
  await expect(commitImport(d, {
    txs: [tx('b'), tx('a')], // 'a' ya existe → bulkAdd falla
    newRules: [{ id: 'r1', pattern: 'x', categoryId: 'cat-ocio', autoCreated: true }],
    accountPatch: { id: 'acc-bnc', bncLastImportKey: 'k', bncLastSaldoMinor: 1 },
  })).rejects.toThrow();
  expect(await d.transactions.count()).toBe(1); // solo la previa
  expect(await d.rules.count()).toBe(0);
  expect((await d.accounts.get('acc-bnc'))?.bncLastSaldoMinor).toBeUndefined();
});
```

Verifica que falla, implementa `src/db/commit.ts`:

```ts
import type { Rule, Transaction } from '../domain/types';
import { CuentasDB } from './db';

/** Import todo-o-nada: si algo falla, no entra nada (spec §5). */
export async function commitImport(d: CuentasDB, opts: {
  txs: Transaction[];
  newRules?: Rule[];
  accountPatch?: { id: string; bncLastImportKey: string; bncLastSaldoMinor: number };
}): Promise<void> {
  await d.transaction('rw', d.transactions, d.rules, d.accounts, async () => {
    if (opts.txs.length > 0) await d.transactions.bulkAdd(opts.txs);
    if (opts.newRules && opts.newRules.length > 0) await d.rules.bulkAdd(opts.newRules);
    if (opts.accountPatch) {
      await d.accounts.update(opts.accountPatch.id, {
        bncLastImportKey: opts.accountPatch.bncLastImportKey,
        bncLastSaldoMinor: opts.accountPatch.bncLastSaldoMinor,
      });
    }
  });
}
```

`npx vitest run src/db/commit.test.ts` → PASS. (Dexie aborta la transacción completa cuando `bulkAdd` rechaza; si vieras el test rojo porque algo persiste, revisa que no estés capturando el error dentro de la transacción.)

- [ ] **Paso 4: Verificar y commit.**

```powershell
npx vitest run
git add src/import/bnc/mapBnc.ts src/import/bnc/mapBnc.test.ts src/db/commit.ts src/db/commit.test.ts
git commit -m "Mapper BNC: dedupe por referencia+hora+monto, pares, hueco y commit todo-o-nada

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 18: UI del importador BNC con vista previa

**Files:**
- Create: `src/ui/componentes/ImportBnc.tsx`
- Modify: `src/ui/paginas/ImportarPage.tsx` (reemplazar el stub)
- Test: `src/ui/componentes/ImportBnc.test.tsx`

**Interfaces:**
- Consumes: todo lo de las Tareas 15-17; `buildAdjustment` (Tarea 9); `accountBalanceMinor` (Tarea 6); `buildRule` (Tarea 15).
- Produces: flujo completo subir/pegar TXT → vista previa (categorías propuestas editables, checkbox "crear regla" con patrón editable, pares agrupados, duplicados deshabilitados, líneas irreconocibles marcadas, banner de hueco/solapado) → confirmar transaccional → oferta de ajuste si el saldo de la app no cuadra con el TXT.

- [ ] **Paso 1: Implementar `src/ui/componentes/ImportBnc.tsx`:**

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { commitImport } from '../../db/commit';
import { parseBncTxt, validateChain, type BncParseResult } from '../../import/bnc/parseBncTxt';
import { mapBncRows, detectGap, bncImportKey, newestRow, type BncDraft, type GapStatus } from '../../import/bnc/mapBnc';
import { buildRule } from '../../domain/rules';
import { buildAdjustment } from '../../domain/builders';
import { accountBalanceMinor } from '../../domain/balances';
import { formatMoney } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';

interface DraftUI extends BncDraft {
  incluir: boolean;
  crearRegla: boolean;
  patron: string;
}

const CUENTA_BNC = 'acc-bnc'; // el TXT de BNC siempre va a la cuenta bancaria en Bs del seed

const TEXTO_GAP: Record<GapStatus, { clase: string; texto: string } | null> = {
  'primera-importacion': null,
  solapado: { clase: 'ok', texto: 'El archivo solapa con lo ya importado: los repetidos se omiten solos.' },
  continuo: { clase: 'ok', texto: 'El archivo conecta exactamente con el último import. Sin huecos.' },
  hueco: { clase: 'aviso', texto: '⚠ Hay movimientos NO exportados entre tu último import y este archivo (el saldo no encadena). Exporta desde BNC un rango que cubra el hueco e importa de nuevo.' },
};

export function ImportBnc() {
  const [texto, setTexto] = useState('');
  const [drafts, setDrafts] = useState<DraftUI[] | null>(null);
  const [resultado, setResultado] = useState<BncParseResult | null>(null);
  const [gap, setGap] = useState<GapStatus>('primera-importacion');
  const [rotos, setRotos] = useState<number[]>([]);
  const [saldoTxt, setSaldoTxt] = useState<number | null>(null);
  const [fase, setFase] = useState<'entrada' | 'previa' | 'hecho'>('entrada');

  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const cuenta = useLiveQuery(() => db.accounts.get(CUENTA_BNC), []);
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];

  async function analizar() {
    const r = parseBncTxt(texto);
    if (r.rows.length === 0) {
      avisar('No se encontraron movimientos en el texto');
      return;
    }
    const reglas = await db.rules.toArray();
    const existentes = new Set(
      (await db.transactions.filter((t) => t.importKey !== undefined).toArray()).map((t) => t.importKey as string),
    );
    const mapeados = mapBncRows(r.rows, { accountId: CUENTA_BNC, existingKeys: existentes, rules: reglas, categories: categorias });
    setResultado(r);
    setRotos(validateChain(r.rows));
    setGap(detectGap(r.rows, { importKey: cuenta?.bncLastImportKey, saldoMinor: cuenta?.bncLastSaldoMinor }));
    setSaldoTxt(newestRow(r.rows)?.saldoMinor ?? null);
    setDrafts(mapeados.map((d) => ({ ...d, incluir: !d.duplicate, crearRegla: false, patron: d.tx.note ?? '' })));
    setFase('previa');
  }

  function actualizar(i: number, patch: Partial<DraftUI>) {
    setDrafts((ds) => ds!.map((d, j) => (j === i ? { ...d, ...patch, tx: patch.tx ?? d.tx } : d)));
  }

  const nuevas = (drafts ?? []).filter((d) => d.incluir && !d.duplicate);
  const duplicadas = (drafts ?? []).filter((d) => d.duplicate).length;

  async function confirmar() {
    if (!drafts || !resultado) return;
    const reglasNuevas = drafts
      .filter((d) => d.incluir && !d.duplicate && d.crearRegla && d.tx.categoryId && d.patron.trim() !== '')
      .map((d) => buildRule(d.patron, d.tx.categoryId as string));
    const fila = newestRow(resultado.rows);
    try {
      await commitImport(db, {
        txs: nuevas.map((d) => d.tx),
        newRules: reglasNuevas,
        accountPatch: fila ? { id: CUENTA_BNC, bncLastImportKey: bncImportKey(fila), bncLastSaldoMinor: fila.saldoMinor } : undefined,
      });
      avisar(`${nuevas.length} importados, ${duplicadas} duplicados omitidos ✓`);
      setFase('hecho');
    } catch (e) {
      avisar(`No se importó nada: ${(e as Error).message}`);
    }
  }

  function reiniciar() {
    setTexto('');
    setDrafts(null);
    setResultado(null);
    setFase('entrada');
  }

  // Cuadre post-import: saldo calculado por la app vs saldo final del TXT
  const saldoApp = accountBalanceMinor(CUENTA_BNC, txs);
  const descuadre = fase === 'hecho' && saldoTxt !== null && saldoApp !== saldoTxt ? saldoTxt - saldoApp : null;

  async function crearAjuste() {
    if (descuadre === null || saldoTxt === null) return;
    await db.transactions.add(
      buildAdjustment({
        accountId: CUENTA_BNC, currentBalanceMinor: saldoApp, realBalanceMinor: saldoTxt,
        dateIso: todayIso(), note: 'Cuadre tras import BNC',
      }),
    );
    avisar('Saldo cuadrado con el banco ✓');
  }

  if (fase === 'entrada') {
    return (
      <div>
        <p>Exporta desde BNC los "25 últimos movimientos" en TXT y súbelo o pega su contenido.</p>
        <input
          aria-label="Archivo TXT"
          type="file"
          accept=".txt,text/plain"
          onChange={async (e) => {
            const f = e.target.files?.[0];
            if (f) setTexto(await f.text());
          }}
        />
        <label>O pega el contenido</label>
        <textarea aria-label="Texto del TXT" rows={6} value={texto} onChange={(e) => setTexto(e.target.value)} />
        <button className="boton" style={{ marginTop: 8 }} onClick={analizar} disabled={texto.trim() === ''}>
          Analizar
        </button>
      </div>
    );
  }

  if (fase === 'hecho') {
    return (
      <div>
        <div className="banner ok">Import terminado. Saldo según la app: {formatMoney(saldoApp, 'VES')}.</div>
        {descuadre !== null && (
          <div className="banner aviso">
            El TXT termina en {formatMoney(saldoTxt as number, 'VES')} pero la app calcula {formatMoney(saldoApp, 'VES')}
            {' '}(movimientos previos al primer export).
            <button className="boton secundario" style={{ marginTop: 8 }} onClick={crearAjuste}>Crear ajuste</button>
          </div>
        )}
        <button className="boton" onClick={reiniciar}>Importar otro archivo</button>
      </div>
    );
  }

  const aviso = TEXTO_GAP[gap];
  return (
    <div>
      <p>
        <strong>{drafts?.length ?? 0} movimientos</strong> en el archivo
        {resultado?.meta ? ` de la cuenta ${resultado.meta.accountNumber.slice(-4).padStart(8, '•')}` : ''}.
      </p>
      {aviso && <div className={`banner ${aviso.clase}`}>{aviso.texto}</div>}
      {rotos.length > 0 && (
        <div className="banner error">El encadenado de saldos no cuadra dentro del archivo (filas {rotos.join(', ')}). Revisa que el TXT esté completo.</div>
      )}
      {resultado && resultado.unparsed.length > 0 && (
        <div className="banner error">
          {resultado.unparsed.length} línea(s) irreconocibles — se importa el resto y estas quedan fuera (añádelas a mano si hacían falta):
          <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
            {resultado.unparsed.map((u) => (
              <li key={u.lineNumber}>línea {u.lineNumber} ({u.reason}): «{u.raw.slice(0, 60)}…»</li>
            ))}
          </ul>
        </div>
      )}
      {drafts?.map((d, i) => (
        <div key={d.tx.importKey} className="fila-mov" style={{ opacity: d.duplicate ? 0.45 : 1, alignItems: 'flex-start' }}>
          <input
            type="checkbox"
            style={{ width: 'auto', marginTop: 4 }}
            aria-label={`Incluir fila ${i + 1}`}
            checked={d.incluir}
            disabled={d.duplicate}
            onChange={(e) => actualizar(i, { incluir: e.target.checked })}
          />
          <span className="detalle">
            <span className="nota">{d.tx.note}</span>
            <span className="meta">
              {d.tx.date} {d.tx.time?.slice(0, 8)}
              {d.pairRef && <span className="chip" style={{ marginLeft: 4 }}>cargo+comisión</span>}
              {d.duplicate && <span className="chip" style={{ marginLeft: 4 }}>ya importado</span>}
            </span>
            {!d.duplicate && (
              <span style={{ display: 'flex', gap: 6, marginTop: 4, alignItems: 'center' }}>
                <select
                  aria-label={`Categoría fila ${i + 1}`}
                  value={d.tx.categoryId ?? ''}
                  onChange={(e) => actualizar(i, { tx: { ...d.tx, categoryId: e.target.value || undefined } })}
                >
                  <option value="">Sin categoría</option>
                  {categorias.filter((c) => c.kind === d.tx.type).map((c) => (
                    <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
                  ))}
                </select>
                <label style={{ display: 'flex', alignItems: 'center', gap: 4, margin: 0, whiteSpace: 'nowrap' }}>
                  <input
                    type="checkbox"
                    style={{ width: 'auto' }}
                    aria-label={`Crear regla fila ${i + 1}`}
                    checked={d.crearRegla}
                    onChange={(e) => actualizar(i, { crearRegla: e.target.checked })}
                  />
                  regla
                </label>
              </span>
            )}
            {d.crearRegla && !d.duplicate && (
              <input
                aria-label={`Patrón fila ${i + 1}`}
                value={d.patron}
                onChange={(e) => actualizar(i, { patron: e.target.value })}
                placeholder="texto que identifica este comercio"
              />
            )}
          </span>
          <span className={d.tx.type === 'income' ? 'monto-pos' : 'monto-neg'}>
            {d.tx.type === 'income' ? '+' : '−'}{formatMoney(d.tx.amountMinor, 'VES')}
          </span>
        </div>
      ))}
      <div className="fila-form" style={{ marginTop: 12 }}>
        <button className="boton secundario" onClick={reiniciar}>Cancelar</button>
        <button className="boton" onClick={confirmar} disabled={nuevas.length === 0}>
          Confirmar ({nuevas.length})
        </button>
      </div>
    </div>
  );
}
```

`src/ui/paginas/ImportarPage.tsx` (reemplazar):

```tsx
import { useState } from 'react';
import { ImportBnc } from '../componentes/ImportBnc';

export function ImportarPage() {
  const [tab, setTab] = useState<'bnc' | 'zinli'>('bnc');
  return (
    <div>
      <h2>Importar</h2>
      <div className="segmentado">
        <button className={tab === 'bnc' ? 'activo' : ''} onClick={() => setTab('bnc')}>BNC (TXT)</button>
        <button className={tab === 'zinli' ? 'activo' : ''} onClick={() => setTab('zinli')}>Zinli (PDF)</button>
      </div>
      {tab === 'bnc' ? <ImportBnc /> : <p>El importador Zinli llega en la Fase 6.</p>}
    </div>
  );
}
```

- [ ] **Paso 2: Test de integración** — `src/ui/componentes/ImportBnc.test.tsx`:

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImportBnc } from './ImportBnc';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';
import { BNC_FIXTURE } from '../../import/bnc/fixtures/bncFixture';

beforeEach(async () => { await limpiarDb(); });

test('flujo completo: analizar → confirmar → cuadrar → reimportar sin duplicados', async () => {
  const user = userEvent.setup();
  const { unmount } = render(<ImportBnc />);

  fireEvent.change(await screen.findByLabelText('Texto del TXT'), { target: { value: BNC_FIXTURE } });
  await user.click(screen.getByRole('button', { name: 'Analizar' }));

  expect(await screen.findByText(/9 movimientos/)).toBeInTheDocument();
  // la comisión de mantenimiento (fila 3) viene pre-categorizada
  expect((screen.getByLabelText('Categoría fila 3') as HTMLSelectElement).value).toBe('cat-comisiones');
  // el par cargo+comisión está señalado
  expect(screen.getAllByText('cargo+comisión')).toHaveLength(2);

  await user.click(screen.getByRole('button', { name: 'Confirmar (9)' }));
  await waitFor(async () => expect(await db.transactions.count()).toBe(9));
  expect((await db.accounts.get('acc-bnc'))?.bncLastSaldoMinor).toBe(630001);

  // El saldo del TXT (6.300,01) no cuadra con la app (falta el saldo previo de 500,00) → ajuste
  await user.click(await screen.findByRole('button', { name: 'Crear ajuste' }));
  await waitFor(async () => expect(await db.transactions.count()).toBe(10));
  const ajuste = (await db.transactions.toArray()).find((t) => t.type === 'adjustment');
  expect(ajuste?.amountMinor).toBe(50000);

  unmount();

  // Reimportar el mismo TXT: solapado, todo duplicado, no se puede confirmar
  render(<ImportBnc />);
  fireEvent.change(await screen.findByLabelText('Texto del TXT'), { target: { value: BNC_FIXTURE } });
  await user.click(screen.getByRole('button', { name: 'Analizar' }));
  expect(await screen.findByText(/solapa/)).toBeInTheDocument();
  expect(screen.getAllByText('ya importado')).toHaveLength(9);
  expect(screen.getByRole('button', { name: 'Confirmar (0)' })).toBeDisabled();
  expect(await db.transactions.count()).toBe(10);
});

test('crea una regla al confirmar con el checkbox marcado', async () => {
  const user = userEvent.setup();
  render(<ImportBnc />);
  fireEvent.change(await screen.findByLabelText('Texto del TXT'), { target: { value: BNC_FIXTURE } });
  await user.click(screen.getByRole('button', { name: 'Analizar' }));
  await screen.findByText(/9 movimientos/);

  // Fila 9 (crédito recibido): elegir categoría y crear regla con patrón editado
  await user.selectOptions(screen.getByLabelText('Categoría fila 9'), 'cat-transferencia-recibida');
  await user.click(screen.getByLabelText('Crear regla fila 9'));
  const patron = screen.getByLabelText('Patrón fila 9');
  await user.clear(patron);
  await user.type(patron, 'PEREZ GOMEZ ANA');
  await user.click(screen.getByRole('button', { name: 'Confirmar (9)' }));

  await waitFor(async () => expect(await db.rules.count()).toBe(1));
  expect((await db.rules.toArray())[0]).toMatchObject({ pattern: 'PEREZ GOMEZ ANA', categoryId: 'cat-transferencia-recibida' });
});
```

- [ ] **Paso 3: Verificar.** `npx vitest run` → verde.

- [ ] **Paso 4: Prueba manual con el TXT real** (`npm run dev`): Importar → BNC → subir `TXTBNC1.txt`. Debes ver 25 movimientos, el par cargo+comisión marcado, comisiones pre-categorizadas; confirma, acepta el ajuste, y verifica en el dashboard que el saldo BNC coincide con el saldo final del TXT real. Reimporta el mismo archivo: 0 nuevos.

- [ ] **Paso 5: Commit.**

```powershell
git add src/ui
git commit -m "UI del import BNC: vista previa con reglas, pares, duplicados, hueco y cuadre

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

**Fin de fase:** `npm run build` sin errores.

---

## Fase 6 — Importador Zinli (PDF mensual)

Contexto del formato real (verificado extrayendo el texto del PDF real de junio 2026, 1 página):
- Cabecera con datos personales en líneas de dos columnas; ahí viven `Saldo inicial del mes: $10.19` y `Saldo ﬁnal del mes: $84.20` — **ojo: el PDF usa la ligadura «ﬁ» (U+FB01)**, así que "final" NO matchea sin normalizar (NFKC la expande).
- Cabecera de tabla: `Fecha Motivo Descripción Referencia Monto`.
- Cada movimiento: `dd/mm/yyyy` + motivo (`Pagaste`/`Recibiste`) + descripción **que puede partirse en 2+ líneas** + referencia (12 dígitos) + monto US (`-$0.99`, `$100.00`). La referencia y el monto cierran el movimiento al final de su última línea.
- El texto trae además pie de página (`1/1`, `Tu dinero Zin límite`, copyright) y un párrafo legal; según el orden de extracción pueden aparecer intercalados — el parser NO debe asumir que los movimientos van justo tras la cabecera: acota por patrón (línea que empieza con fecha abre; cola `referencia + monto` cierra).
- Validación: `saldo inicial + Σ montos = saldo final` (con los datos reales: 10.19 − 0.99 − 5.00 + 100.00 − 20.00 = 84.20 ✓).

### Tarea 19: Extracción de texto con pdf.js en Vite

**Files:**
- Create: `src/import/zinli/extractPdfText.ts`, `src/ui/componentes/ImportZinli.tsx` (versión mínima: volcador de texto)
- Modify: `src/ui/paginas/ImportarPage.tsx`

**Interfaces:**
- Consumes: `pdfjs-dist` (instalado en Tarea 1).
- Produces: `extractPdfLines(data: ArrayBuffer): Promise<string[]>` — líneas en **orden visual** (agrupadas por coordenada Y del baseline, ordenadas de arriba a abajo, items unidos por X). Sin test unitario: pdf.js no corre en jsdom; la frontera testeable es `parseZinliLines` (Tarea 20) y esta capa se verifica manualmente aquí y offline en la Tarea 24.

- [ ] **Paso 1: Implementar `src/import/zinli/extractPdfText.ts`** (imports dinámicos para que ningún test que importe la UI cargue pdf.js en jsdom):

```ts
/** Reconstruye las líneas visuales del PDF agrupando items de texto por su coordenada Y. */
export async function extractPdfLines(data: ArrayBuffer): Promise<string[]> {
  const pdfjs = await import('pdfjs-dist');
  const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
  pdfjs.GlobalWorkerOptions.workerSrc = worker.default;

  const doc = await pdfjs.getDocument({ data }).promise;
  const lineas: string[] = [];
  try {
    for (let p = 1; p <= doc.numPages; p++) {
      const page = await doc.getPage(p);
      const contenido = await page.getTextContent();
      const porY: { y: number; items: { x: number; str: string }[] }[] = [];
      for (const item of contenido.items) {
        if (!('str' in item) || item.str.trim() === '') continue;
        const x = item.transform[4] as number;
        const y = item.transform[5] as number;
        const linea = porY.find((l) => Math.abs(l.y - y) <= 2); // misma baseline ±2pt
        if (linea) linea.items.push({ x, str: item.str });
        else porY.push({ y, items: [{ x, str: item.str }] });
      }
      porY.sort((a, b) => b.y - a.y); // Y crece hacia arriba en PDF → mayor Y = más arriba
      for (const l of porY) {
        l.items.sort((a, b) => a.x - b.x);
        lineas.push(l.items.map((i) => i.str).join(' ').replace(/\s+/g, ' ').trim());
      }
    }
  } finally {
    await doc.destroy();
  }
  return lineas;
}
```

Nota de compilación: el import `pdfjs-dist/build/pdf.worker.min.mjs?url` lo resuelve Vite (`vite/client` ya está en `types` del tsconfig). Si el nombre del worker difiere en la versión instalada (`ls node_modules/pdfjs-dist/build/`), usa el `.mjs` minificado que exista.

- [ ] **Paso 2: Volcador provisional** — `src/ui/componentes/ImportZinli.tsx` (la Tarea 21 lo reescribe; esto permite verificar la extracción y capturar texto para depurar):

```tsx
import { useState } from 'react';
import { extractPdfLines } from '../../import/zinli/extractPdfText';

export function ImportZinli() {
  const [lineas, setLineas] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <div>
      <p>Sube el "Resumen de movimientos" mensual de Zinli (PDF).</p>
      <input
        aria-label="Archivo PDF"
        type="file"
        accept=".pdf,application/pdf"
        onChange={async (e) => {
          const f = e.target.files?.[0];
          if (!f) return;
          try {
            setError(null);
            setLineas(await extractPdfLines(await f.arrayBuffer()));
          } catch (err) {
            setError((err as Error).message);
          }
        }}
      />
      {error && <div className="banner error">No se pudo leer el PDF: {error}</div>}
      {lineas && (
        <details open>
          <summary>Texto extraído ({lineas.length} líneas)</summary>
          <textarea aria-label="Texto extraído" readOnly rows={12} value={lineas.join('\n')} />
        </details>
      )}
    </div>
  );
}
```

En `src/ui/paginas/ImportarPage.tsx`, reemplaza el placeholder del tab Zinli:

```tsx
import { ImportZinli } from '../componentes/ImportZinli';
// ...
{tab === 'bnc' ? <ImportBnc /> : <ImportZinli />}
```

- [ ] **Paso 3: Verificación manual (bloqueante).** `npm run dev` → Importar → Zinli → subir `Resumen_de_movimientos_Junio_2026_Zinli.pdf` (el real, local). Comprobar en el volcado:
  1. Aparece una línea con `Saldo inicial del mes: $…` y otra con `Saldo ﬁnal del mes: $…`.
  2. Los movimientos aparecen como líneas que empiezan con `dd/mm/yyyy` seguidas de `Pagaste`/`Recibiste`, con la referencia (12 dígitos) y el monto al final de su última línea (algunos movimientos parten la descripción en 2 líneas).
  3. El pie legal aparece como líneas sueltas al final (o intercaladas — da igual, el parser lo tolera).

  Si la estructura visual difiere de la fixture de la Tarea 20 (p. ej. los items no agrupan bien y una fila sale partida en 2 líneas distintas de las esperadas), ajusta la tolerancia Y (±2) y/o **actualiza la fixture** con la MISMA estructura observada pero datos inventados. Nunca pegues texto real (nombre, email, teléfono, documento, dirección, tarjeta) en la fixture.

- [ ] **Paso 4: Verificar suite y commit.**

```powershell
npx vitest run
npm run build
git add src/import/zinli src/ui
git commit -m "Extractor de texto PDF con pdf.js (worker Vite) y volcador Zinli provisional

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 20: Parser del resumen Zinli

**Files:**
- Create: `src/import/zinli/parseZinli.ts`, `src/import/zinli/fixtures/zinliFixture.ts`
- Test: `src/import/zinli/parseZinli.test.ts`

**Interfaces:**
- Consumes: `parseUsAmount` (Tarea 3), `isoFromDdMmYyyy` (Tarea 3).
- Produces:
  - `normalizePdfLine(s: string): string` — NFKC (expande ligaduras), colapsa espacios.
  - `interface ZinliMovement { date: string; motivo: string; descripcion: string; referencia: string; amountMinor: number }`
  - `interface ZinliParseResult { periodo: string | null; saldoInicialMinor: number | null; saldoFinalMinor: number | null; movements: ZinliMovement[]; unparsed: string[]; cuadra: boolean | null }`
  - `parseZinliLines(rawLines: string[]): ZinliParseResult` — `cuadra` es `null` si falta algún saldo.

- [ ] **Paso 1: Fixture anonimizada** — `src/import/zinli/fixtures/zinliFixture.ts`. Estructura real, datos inventados; **conserva las ligaduras «ﬁ»** (son parte del caso de test) y el cuadre 10.19 − 0.99 − 5.00 + 100.00 − 20.00 = 84.20:

```ts
/** Líneas como las devuelve extractPdfLines sobre un resumen mensual (datos inventados). */
export const ZINLI_LINES: string[] = [
  'Resumen de movimientos:',
  'Desde el 01 de junio al 30 de junio de 2026',
  'Nombre: Maria Jose Perez Rodriguez Límite: $1,000.00',
  'Tarjeta número: 400000*****0002 Saldo inicial del mes: $10.19',
  'Email: ejemplo@correo.com Saldo ﬁnal del mes: $84.20',
  'Teléfono: +584140000001 En Zinli desde: Abril de 2023',
  'Documento de identiﬁcación: 000000001 Estado de la billetera: Activa',
  'Dirección: Calle Ficticia, Ediﬁcio Ejemplo, Caracas, Venezuela',
  'Fecha Motivo Descripción Referencia Monto',
  '01/06/2026 Pagaste CUOTA MENSUAL MANT. Y USO',
  'T.VISA JUNIO 373700000001 -$0.99',
  '19/06/2026 Pagaste TIENDA EJEMPLO +14150000000 US 950100000002 -$5.00',
  '28/06/2026 Recibiste Desde Banco Ejemplo',
  'ejemplo@correo.com 871300000003 $100.00',
  '29/06/2026 Pagaste SERVICIO EJEMPLO* SUSCRIP',
  '+14150000000 US 314000000004 -$20.00',
  '1/1',
  'Tu dinero Zin límite',
  'Todos los derechos reservados. | Zinli.com',
  'La información conﬁdencial no podrá ser reproducida por ningún medio ni en ningún formato por el Receptor',
];
```

- [ ] **Paso 2: Test `src/import/zinli/parseZinli.test.ts`:**

```ts
import { parseZinliLines, normalizePdfLine } from './parseZinli';
import { ZINLI_LINES } from './fixtures/zinliFixture';

test('normaliza ligaduras y espacios', () => {
  expect(normalizePdfLine('Saldo ﬁnal  del mes')).toBe('Saldo final del mes');
  expect(normalizePdfLine('identiﬁcación')).toBe('identificación');
});

test('extrae saldos y período pese a la ligadura en «ﬁnal»', () => {
  const r = parseZinliLines(ZINLI_LINES);
  expect(r.saldoInicialMinor).toBe(1019);
  expect(r.saldoFinalMinor).toBe(8420);
  expect(r.periodo).toBe('01 de junio al 30 de junio de 2026');
});

test('parsea los 4 movimientos, incluidos los partidos en dos líneas', () => {
  const r = parseZinliLines(ZINLI_LINES);
  expect(r.movements).toHaveLength(4);
  expect(r.movements[0]).toEqual({
    date: '2026-06-01',
    motivo: 'Pagaste',
    descripcion: 'CUOTA MENSUAL MANT. Y USO T.VISA JUNIO',
    referencia: '373700000001',
    amountMinor: -99,
  });
  expect(r.movements[1]).toMatchObject({ date: '2026-06-19', referencia: '950100000002', amountMinor: -500 });
  expect(r.movements[2]).toMatchObject({ motivo: 'Recibiste', referencia: '871300000003', amountMinor: 10000 });
  expect(r.movements[3]).toMatchObject({
    descripcion: 'SERVICIO EJEMPLO* SUSCRIP +14150000000 US',
    amountMinor: -2000,
  });
  expect(r.unparsed).toHaveLength(0);
});

test('valida el cuadre: inicial + suma = final', () => {
  expect(parseZinliLines(ZINLI_LINES).cuadra).toBe(true);
});

test('detecta cuando NO cuadra', () => {
  const rotas = ZINLI_LINES.map((l) => l.replace('-$5.00', '-$6.00'));
  expect(parseZinliLines(rotas).cuadra).toBe(false);
});

test('cuadra es null si falta un saldo', () => {
  const sinFinal = ZINLI_LINES.filter((l) => !l.includes('ﬁnal'));
  expect(parseZinliLines(sinFinal).cuadra).toBeNull();
});

test('un movimiento que nunca cierra con referencia+monto va a unparsed', () => {
  const lineas = [
    'Saldo inicial del mes: $1.00',
    '05/06/2026 Pagaste ALGO SIN REFERENCIA NI MONTO',
    'Saldo final del mes: $1.00',
  ];
  const r = parseZinliLines(lineas);
  expect(r.movements).toHaveLength(0);
  expect(r.unparsed).toHaveLength(1);
  expect(r.cuadra).toBe(true); // sin movimientos, 1.00 + 0 = 1.00
});
```

Verifica que falla: `npx vitest run src/import/zinli/parseZinli.test.ts` → FAIL.

- [ ] **Paso 3: Implementar `src/import/zinli/parseZinli.ts`:**

```ts
import { parseUsAmount } from '../../domain/money';
import { isoFromDdMmYyyy } from '../../domain/dates';

export interface ZinliMovement {
  date: string;
  motivo: string;
  descripcion: string;
  referencia: string;
  amountMinor: number;
}

export interface ZinliParseResult {
  periodo: string | null;
  saldoInicialMinor: number | null;
  saldoFinalMinor: number | null;
  movements: ZinliMovement[];
  unparsed: string[];
  cuadra: boolean | null;
}

/**
 * NFKC expande ligaduras del PDF («ﬁ» → «fi») y normaliza espacios.
 * OJO: el primer replace elimina NBSP; el carácter dentro del regex es un
 * U+00A0 literal — si al copiar se convirtió en espacio normal, escríbelo
 * escrito con escape ASCII: sustituye ese regex por new RegExp('\\u00a0', 'g').
 */
export function normalizePdfLine(s: string): string {
  return s.normalize('NFKC').replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
}

const INICIO_MOV = /^(\d{2}\/\d{2}\/\d{4})\s+(\S+)(?:\s+(.*))?$/;
const CIERRE = /(\d{6,})\s+([+-]?\$[\d,]+\.\d{2})$/;

export function parseZinliLines(rawLines: string[]): ZinliParseResult {
  const result: ZinliParseResult = {
    periodo: null, saldoInicialMinor: null, saldoFinalMinor: null,
    movements: [], unparsed: [], cuadra: null,
  };

  let abierto: { date: string; motivo: string; partes: string[]; raw: string[] } | null = null;
  const abandonar = () => {
    if (abierto) {
      result.unparsed.push(abierto.raw.join(' '));
      abierto = null;
    }
  };

  for (const cruda of rawLines) {
    const linea = normalizePdfLine(cruda);
    if (linea === '') continue;

    const ini = /Saldo inicial del mes:\s*([+-]?\$[\d,]+\.\d{2})/.exec(linea);
    if (ini) result.saldoInicialMinor = parseUsAmount(ini[1]);
    const fin = /Saldo final del mes:\s*([+-]?\$[\d,]+\.\d{2})/.exec(linea);
    if (fin) result.saldoFinalMinor = parseUsAmount(fin[1]);
    const per = /^Desde el (.+)$/.exec(linea);
    if (per && result.periodo === null) result.periodo = per[1];
    if (ini || fin) continue; // línea de cabecera: nunca es parte de un movimiento

    const inicio = INICIO_MOV.exec(linea);
    const fecha = inicio ? isoFromDdMmYyyy(inicio[1]) : null;
    if (inicio && fecha) {
      abandonar(); // el movimiento anterior nunca cerró
      abierto = { date: fecha, motivo: inicio[2], partes: inicio[3] ? [inicio[3]] : [], raw: [linea] };
    } else if (abierto) {
      abierto.partes.push(linea);
      abierto.raw.push(linea);
    } else {
      continue; // cabecera, tabla, pie o texto legal fuera de un movimiento
    }

    const cuerpo = abierto.partes.join(' ');
    const m = CIERRE.exec(cuerpo);
    if (m) {
      const amountMinor = parseUsAmount(m[2]);
      if (amountMinor !== null) {
        result.movements.push({
          date: abierto.date,
          motivo: abierto.motivo,
          descripcion: cuerpo.slice(0, m.index).trim(),
          referencia: m[1],
          amountMinor,
        });
        abierto = null;
      }
    }
  }
  abandonar();

  if (result.saldoInicialMinor !== null && result.saldoFinalMinor !== null) {
    const suma = result.movements.reduce((acc, m) => acc + m.amountMinor, 0);
    result.cuadra = result.saldoInicialMinor + suma === result.saldoFinalMinor;
  }
  return result;
}
```

- [ ] **Paso 4: Verificar y commit.**

```powershell
npx vitest run src/import/zinli/parseZinli.test.ts
npx vitest run
git add src/import/zinli
git commit -m "Parser del resumen Zinli: ligaduras, multilínea, referencia+monto y cuadre

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

- [ ] **Paso 5 (verificación manual, NO commitear):** con el volcador de la Tarea 19, pasa las líneas del PDF real por `parseZinliLines` (puedes hacerlo temporalmente en la consola del navegador o en un test local no commiteado): debe dar los movimientos reales del mes, `unparsed` vacío y `cuadra === true`. Si algo difiere, corrige parser y fixture (datos inventados).

### Tarea 21: Mapper + UI del importador Zinli

**Files:**
- Create: `src/import/zinli/mapZinli.ts`
- Modify: `src/ui/componentes/ImportZinli.tsx` (reescribir con el flujo completo)
- Test: `src/import/zinli/mapZinli.test.ts`, `src/ui/componentes/ImportZinli.test.tsx`

**Interfaces:**
- Consumes: `ZinliMovement`, `parseZinliLines` (Tarea 20); `extractPdfLines` (Tarea 19); `matchCategory`, `buildRule` (Tarea 15); `commitImport` (Tarea 17).
- Produces:
  - `zinliImportKey(m: ZinliMovement): string` — `` `zinli:${referencia}` `` (dedupe por referencia, spec §4.6).
  - `interface ZinliDraft { tx: Transaction; duplicate: boolean; matchedRuleId: string | null }`
  - `mapZinliMovements(movs: ZinliMovement[], opts: { accountId: string; existingKeys: Set<string>; rules: Rule[]; categories: Category[] }): ZinliDraft[]` — nota = `"{motivo}: {descripcion}"`; ingresos sin regla → `cat-transferencia-recibida` si existe.
  - `<ImportZinli extraerLineas?: (data: ArrayBuffer) => Promise<string[]> />` — la prop inyectable (defecto `extractPdfLines`) existe para poder testear el componente sin pdf.js.

- [ ] **Paso 1: Test `src/import/zinli/mapZinli.test.ts`:**

```ts
import type { Category, Rule } from '../../domain/types';
import { parseZinliLines } from './parseZinli';
import { ZINLI_LINES } from './fixtures/zinliFixture';
import { mapZinliMovements, zinliImportKey } from './mapZinli';

const categorias: Category[] = [
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#000', kind: 'expense' },
  { id: 'cat-transferencia-recibida', name: 'Transferencia recibida', icon: '🔁', color: '#000', kind: 'income' },
];
const reglas: Rule[] = [{ id: 'r1', pattern: 'SERVICIO EJEMPLO', categoryId: 'cat-suscripciones', autoCreated: true }];
const movs = parseZinliLines(ZINLI_LINES).movements;
const opciones = { accountId: 'acc-zinli', existingKeys: new Set<string>(), rules: reglas, categories: categorias };

test('mapea movimientos a transacciones Zinli', () => {
  const drafts = mapZinliMovements(movs, opciones);
  expect(drafts).toHaveLength(4);
  expect(drafts[0].tx).toMatchObject({
    type: 'expense', accountId: 'acc-zinli', amountMinor: 99, date: '2026-06-01',
    source: 'zinli', importKey: 'zinli:373700000001',
  });
  expect(drafts[0].tx.note).toBe('Pagaste: CUOTA MENSUAL MANT. Y USO T.VISA JUNIO');
});

test('propone categorías: regla para gasto, transferencia recibida para ingreso', () => {
  const drafts = mapZinliMovements(movs, opciones);
  expect(drafts[3].tx.categoryId).toBe('cat-suscripciones');            // regla SERVICIO EJEMPLO
  expect(drafts[2].tx.categoryId).toBe('cat-transferencia-recibida');   // Recibiste
  expect(drafts[0].tx.categoryId).toBeUndefined();                      // gasto sin regla
});

test('dedupe por referencia', () => {
  const existentes = new Set([zinliImportKey(movs[1])]);
  const drafts = mapZinliMovements(movs, { ...opciones, existingKeys: existentes });
  expect(drafts.filter((d) => d.duplicate).map((d) => d.tx.importKey)).toEqual(['zinli:950100000002']);
});
```

Verifica que falla, implementa `src/import/zinli/mapZinli.ts`:

```ts
import type { Category, Rule, Transaction } from '../../domain/types';
import { matchCategory } from '../../domain/rules';
import type { ZinliMovement } from './parseZinli';

export function zinliImportKey(m: ZinliMovement): string {
  return `zinli:${m.referencia}`;
}

export interface ZinliDraft {
  tx: Transaction;
  duplicate: boolean;
  matchedRuleId: string | null;
}

export function mapZinliMovements(movs: ZinliMovement[], opts: {
  accountId: string;
  existingKeys: Set<string>;
  rules: Rule[];
  categories: Category[];
}): ZinliDraft[] {
  const hayTransferencia = opts.categories.some((c) => c.id === 'cat-transferencia-recibida');
  return movs
    .filter((m) => m.amountMinor !== 0)
    .map((m) => {
      const regla = matchCategory(`${m.motivo} ${m.descripcion}`, opts.rules);
      const esIngreso = m.amountMinor > 0;
      let categoryId = regla?.categoryId;
      if (!categoryId && esIngreso && hayTransferencia) categoryId = 'cat-transferencia-recibida';
      const importKey = zinliImportKey(m);
      const tx: Transaction = {
        id: crypto.randomUUID(),
        type: esIngreso ? 'income' : 'expense',
        accountId: opts.accountId,
        amountMinor: Math.abs(m.amountMinor),
        date: m.date,
        categoryId,
        note: `${m.motivo}: ${m.descripcion}`,
        source: 'zinli',
        importKey,
      };
      return { tx, duplicate: opts.existingKeys.has(importKey), matchedRuleId: regla?.id ?? null };
    });
}
```

`npx vitest run src/import/zinli/mapZinli.test.ts` → PASS.

- [ ] **Paso 2: Reescribir `src/ui/componentes/ImportZinli.tsx`** con el flujo completo (mismo patrón de vista previa que BNC):

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { commitImport } from '../../db/commit';
import { extractPdfLines } from '../../import/zinli/extractPdfText';
import { parseZinliLines, type ZinliParseResult } from '../../import/zinli/parseZinli';
import { mapZinliMovements, type ZinliDraft } from '../../import/zinli/mapZinli';
import { buildRule } from '../../domain/rules';
import { formatMoney } from '../../domain/money';
import { avisar } from '../Toast';

interface DraftUI extends ZinliDraft {
  incluir: boolean;
  crearRegla: boolean;
  patron: string;
}

const CUENTA_ZINLI = 'acc-zinli';

export function ImportZinli({ extraerLineas = extractPdfLines }: {
  extraerLineas?: (data: ArrayBuffer) => Promise<string[]>;
}) {
  const [lineas, setLineas] = useState<string[] | null>(null);
  const [resultado, setResultado] = useState<ZinliParseResult | null>(null);
  const [drafts, setDrafts] = useState<DraftUI[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hecho, setHecho] = useState(false);
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];

  async function analizar(f: File) {
    try {
      setError(null);
      setHecho(false);
      const lin = await extraerLineas(await f.arrayBuffer());
      const r = parseZinliLines(lin);
      if (r.movements.length === 0) {
        setError('No se reconocieron movimientos en el PDF. Revisa el texto extraído abajo.');
        setLineas(lin);
        setResultado(null);
        setDrafts(null);
        return;
      }
      const reglas = await db.rules.toArray();
      const existentes = new Set(
        (await db.transactions.filter((t) => t.importKey !== undefined).toArray()).map((t) => t.importKey as string),
      );
      const mapeados = mapZinliMovements(r.movements, {
        accountId: CUENTA_ZINLI, existingKeys: existentes, rules: reglas, categories: categorias,
      });
      setLineas(lin);
      setResultado(r);
      setDrafts(mapeados.map((d) => ({ ...d, incluir: !d.duplicate, crearRegla: false, patron: d.tx.note ?? '' })));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function actualizar(i: number, patch: Partial<DraftUI>) {
    setDrafts((ds) => ds!.map((d, j) => (j === i ? { ...d, ...patch, tx: patch.tx ?? d.tx } : d)));
  }

  const nuevas = (drafts ?? []).filter((d) => d.incluir && !d.duplicate);
  const duplicadas = (drafts ?? []).filter((d) => d.duplicate).length;

  async function confirmar() {
    if (!drafts) return;
    const reglasNuevas = drafts
      .filter((d) => d.incluir && !d.duplicate && d.crearRegla && d.tx.categoryId && d.patron.trim() !== '')
      .map((d) => buildRule(d.patron, d.tx.categoryId as string));
    try {
      await commitImport(db, { txs: nuevas.map((d) => d.tx), newRules: reglasNuevas });
      avisar(`${nuevas.length} importados, ${duplicadas} duplicados omitidos ✓`);
      setHecho(true);
      setDrafts(null);
      setResultado(null);
    } catch (e) {
      avisar(`No se importó nada: ${(e as Error).message}`);
    }
  }

  return (
    <div>
      <p>Sube el "Resumen de movimientos" mensual de Zinli (PDF).</p>
      <input
        aria-label="Archivo PDF"
        type="file"
        accept=".pdf,application/pdf"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) analizar(f);
        }}
      />
      {error && <div className="banner error">{error}</div>}
      {hecho && <div className="banner ok">Import terminado ✓</div>}

      {resultado && drafts && (
        <>
          <p>
            <strong>{drafts.length} movimientos</strong>
            {resultado.periodo ? ` · ${resultado.periodo}` : ''}
          </p>
          {resultado.cuadra === true && (
            <div className="banner ok">
              Cuadra: {formatMoney(resultado.saldoInicialMinor as number, 'USD')} inicial + movimientos ={' '}
              {formatMoney(resultado.saldoFinalMinor as number, 'USD')} final ✓
            </div>
          )}
          {resultado.cuadra === false && (
            <div className="banner error">
              ⚠ NO cuadra: inicial {formatMoney(resultado.saldoInicialMinor as number, 'USD')} + movimientos ≠ final{' '}
              {formatMoney(resultado.saldoFinalMinor as number, 'USD')}. Faltan movimientos por reconocer — revisa las líneas de abajo antes de confiar en este import.
            </div>
          )}
          {resultado.cuadra === null && (
            <div className="banner aviso">No se encontraron los saldos inicial/final del mes: no se puede validar el cuadre.</div>
          )}
          {resultado.unparsed.length > 0 && (
            <div className="banner error">
              {resultado.unparsed.length} fragmento(s) sin cerrar: {resultado.unparsed.map((u) => `«${u.slice(0, 50)}…»`).join(' · ')}
            </div>
          )}

          {drafts.map((d, i) => (
            <div key={d.tx.importKey} className="fila-mov" style={{ opacity: d.duplicate ? 0.45 : 1, alignItems: 'flex-start' }}>
              <input
                type="checkbox"
                style={{ width: 'auto', marginTop: 4 }}
                aria-label={`Incluir fila ${i + 1}`}
                checked={d.incluir}
                disabled={d.duplicate}
                onChange={(e) => actualizar(i, { incluir: e.target.checked })}
              />
              <span className="detalle">
                <span className="nota">{d.tx.note}</span>
                <span className="meta">
                  {d.tx.date}
                  {d.duplicate && <span className="chip" style={{ marginLeft: 4 }}>ya importado</span>}
                </span>
                {!d.duplicate && (
                  <span style={{ display: 'flex', gap: 6, marginTop: 4, alignItems: 'center' }}>
                    <select
                      aria-label={`Categoría fila ${i + 1}`}
                      value={d.tx.categoryId ?? ''}
                      onChange={(e) => actualizar(i, { tx: { ...d.tx, categoryId: e.target.value || undefined } })}
                    >
                      <option value="">Sin categoría</option>
                      {categorias.filter((c) => c.kind === d.tx.type).map((c) => (
                        <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
                      ))}
                    </select>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 4, margin: 0, whiteSpace: 'nowrap' }}>
                      <input
                        type="checkbox"
                        style={{ width: 'auto' }}
                        aria-label={`Crear regla fila ${i + 1}`}
                        checked={d.crearRegla}
                        onChange={(e) => actualizar(i, { crearRegla: e.target.checked })}
                      />
                      regla
                    </label>
                  </span>
                )}
                {d.crearRegla && !d.duplicate && (
                  <input
                    aria-label={`Patrón fila ${i + 1}`}
                    value={d.patron}
                    onChange={(e) => actualizar(i, { patron: e.target.value })}
                  />
                )}
              </span>
              <span className={d.tx.type === 'income' ? 'monto-pos' : 'monto-neg'}>
                {d.tx.type === 'income' ? '+' : '−'}{formatMoney(d.tx.amountMinor, 'USD')}
              </span>
            </div>
          ))}
          <button className="boton" style={{ marginTop: 12 }} onClick={confirmar} disabled={nuevas.length === 0}>
            Confirmar ({nuevas.length})
          </button>
        </>
      )}

      {lineas && (
        <details style={{ marginTop: 12 }}>
          <summary>Texto extraído ({lineas.length} líneas)</summary>
          <textarea aria-label="Texto extraído" readOnly rows={10} value={lineas.join('\n')} />
        </details>
      )}
    </div>
  );
}
```

- [ ] **Paso 3: Test del componente** — `src/ui/componentes/ImportZinli.test.tsx` (inyecta el extractor falso; no toca pdf.js):

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImportZinli } from './ImportZinli';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';
import { ZINLI_LINES } from '../../import/zinli/fixtures/zinliFixture';

beforeEach(async () => { await limpiarDb(); });

function subirPdfFalso() {
  const input = screen.getByLabelText('Archivo PDF');
  fireEvent.change(input, { target: { files: [new File(['%PDF-falso'], 'resumen.pdf', { type: 'application/pdf' })] } });
}

test('importa el resumen: cuadre visible, confirmar, y reimportar deduplica', async () => {
  const user = userEvent.setup();
  const { unmount } = render(<ImportZinli extraerLineas={async () => ZINLI_LINES} />);
  subirPdfFalso();

  expect(await screen.findByText(/4 movimientos/)).toBeInTheDocument();
  expect(screen.getByText(/Cuadra:/)).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'Confirmar (4)' }));
  await waitFor(async () => expect(await db.transactions.count()).toBe(4));
  const txs = await db.transactions.toArray();
  expect(txs.every((t) => t.accountId === 'acc-zinli' && t.source === 'zinli')).toBe(true);

  unmount();
  render(<ImportZinli extraerLineas={async () => ZINLI_LINES} />);
  subirPdfFalso();
  await screen.findByText(/4 movimientos/);
  expect(screen.getAllByText('ya importado')).toHaveLength(4);
  expect(screen.getByRole('button', { name: 'Confirmar (0)' })).toBeDisabled();
});

test('avisa cuando el resumen no cuadra', async () => {
  const rotas = ZINLI_LINES.map((l) => l.replace('-$5.00', '-$6.00'));
  render(<ImportZinli extraerLineas={async () => rotas} />);
  subirPdfFalso();
  expect(await screen.findByText(/NO cuadra/)).toBeInTheDocument();
});
```

- [ ] **Paso 4: Verificar.** `npx vitest run` → verde. Prueba manual con el PDF real: 4+ movimientos del mes, banner verde de cuadre, confirmar, ver el saldo Zinli en el dashboard. Reimportar el mismo PDF → 0 nuevos.

- [ ] **Paso 5: Commit.**

```powershell
git add src/import/zinli src/ui
git commit -m "Importador Zinli completo: mapper con dedupe por referencia y UI con validación de cuadre

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

**Fin de fase:** `npm run build` sin errores.

---

## Fase 7 — Respaldo JSON (Drive sin API)

### Tarea 22: Export/import de respaldo — lógica

**Files:**
- Create: `src/backup/backup.ts`, `src/backup/saveFile.ts`
- Test: `src/backup/backup.test.ts`, `src/backup/saveFile.test.ts`

**Interfaces:**
- Consumes: `CuentasDB` (Tarea 5); entidades.
- Produces:
  - `interface BackupFile { schemaVersion: 1; exportedAt: string; accounts: Account[]; transactions: Transaction[]; categories: Category[]; rules: Rule[]; rates: Rate[]; settings: Settings[] }`
  - `exportBackup(d: CuentasDB): Promise<BackupFile>`
  - `validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string }`
  - `importBackup(d: CuentasDB, b: BackupFile): Promise<void>` — reemplaza TODO en una transacción.
  - `backupFilename(dateIso: string): string` — `cuentas-claras-respaldo-2026-07-07.json`.
  - `type SaveStrategy = 'picker' | 'share' | 'download'`
  - `pickSaveStrategy(caps: { hasPicker: boolean; canShareFiles: boolean }): SaveStrategy`
  - `saveJsonFile(json: string, filename: string): Promise<SaveStrategy>` — File System Access API (Windows/Chromium) → share sheet con archivo (Android) → descarga `<a download>`.

- [ ] **Paso 1: Test `src/backup/backup.test.ts`:**

```ts
import { CuentasDB, seedIfEmpty } from '../db/db';
import { exportBackup, importBackup, validateBackup, backupFilename } from './backup';
import { buildManualTx } from '../domain/builders';

async function dbConDatos() {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  await d.transactions.add(
    buildManualTx({ type: 'expense', accountId: 'acc-bnc', amountMinor: 3500, categoryId: 'cat-ocio', dateIso: '2026-07-05', note: 'prueba' }),
  );
  await d.rates.put({ date: '2026-07-05', source: 'BCV', vesPerUsd: 111 });
  await d.rules.add({ id: 'r1', pattern: 'x', categoryId: 'cat-ocio', autoCreated: true });
  return d;
}

test('round-trip: export → texto JSON → validate → import reproduce todo', async () => {
  const origen = await dbConDatos();
  const json = JSON.stringify(await exportBackup(origen), null, 2); // pasa por texto, como en la vida real
  const v = validateBackup(JSON.parse(json));
  expect(v.ok).toBe(true);
  if (!v.ok) return;

  const destino = new CuentasDB(`test-${crypto.randomUUID()}`);
  await destino.transactions.add(
    buildManualTx({ type: 'income', accountId: 'x', amountMinor: 1, categoryId: 'c', dateIso: '2026-01-01' }),
  ); // dato previo que DEBE desaparecer
  await importBackup(destino, v.backup);

  expect(await destino.transactions.count()).toBe(1);
  expect((await destino.transactions.toArray())[0].note).toBe('prueba');
  expect(await destino.accounts.count()).toBe(5);
  expect(await destino.categories.count()).toBe(15);
  expect(await destino.rules.count()).toBe(1);
  expect(await destino.rates.count()).toBe(1);
  expect((await destino.settings.get('main'))?.viewCurrency).toBe('USD');
});

test('validateBackup rechaza basura, versiones desconocidas y tablas faltantes', () => {
  expect(validateBackup(null).ok).toBe(false);
  expect(validateBackup('texto').ok).toBe(false);
  expect(validateBackup({}).ok).toBe(false);
  const casi = { schemaVersion: 1, exportedAt: 'x', accounts: [], transactions: [], categories: [], rules: [], rates: [], settings: [] };
  expect(validateBackup(casi).ok).toBe(true);
  expect(validateBackup({ ...casi, schemaVersion: 2 }).ok).toBe(false);
  expect(validateBackup({ ...casi, rates: undefined }).ok).toBe(false);
});

test('backupFilename incluye la fecha', () => {
  expect(backupFilename('2026-07-07')).toBe('cuentas-claras-respaldo-2026-07-07.json');
});
```

Y `src/backup/saveFile.test.ts`:

```ts
import { pickSaveStrategy } from './saveFile';

test('elige picker > share > download según capacidades', () => {
  expect(pickSaveStrategy({ hasPicker: true, canShareFiles: true })).toBe('picker');
  expect(pickSaveStrategy({ hasPicker: false, canShareFiles: true })).toBe('share');
  expect(pickSaveStrategy({ hasPicker: false, canShareFiles: false })).toBe('download');
});
```

Verifica que fallan ambos.

- [ ] **Paso 2: Implementar `src/backup/backup.ts`:**

```ts
import type { Account, Category, Rate, Rule, Settings, Transaction } from '../domain/types';
import { CuentasDB } from '../db/db';

export interface BackupFile {
  schemaVersion: 1;
  exportedAt: string; // ISO completo
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  rules: Rule[];
  rates: Rate[];
  settings: Settings[];
}

const TABLAS = ['accounts', 'transactions', 'categories', 'rules', 'rates', 'settings'] as const;

export async function exportBackup(d: CuentasDB): Promise<BackupFile> {
  const [accounts, transactions, categories, rules, rates, settings] = await Promise.all([
    d.accounts.toArray(),
    d.transactions.toArray(),
    d.categories.toArray(),
    d.rules.toArray(),
    d.rates.toArray(),
    d.settings.toArray(),
  ]);
  return { schemaVersion: 1, exportedAt: new Date().toISOString(), accounts, transactions, categories, rules, rates, settings };
}

export function validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string } {
  if (typeof data !== 'object' || data === null) return { ok: false, error: 'El archivo no es un respaldo de Cuentas Claras' };
  const b = data as Partial<BackupFile>;
  if (b.schemaVersion !== 1) return { ok: false, error: `Versión de respaldo no soportada: ${String(b.schemaVersion)}` };
  for (const tabla of TABLAS) {
    if (!Array.isArray(b[tabla])) return { ok: false, error: `Al respaldo le falta la tabla "${tabla}"` };
  }
  return { ok: true, backup: b as BackupFile };
}

/** Importar reemplaza TODO el contenido, en una sola transacción (todo-o-nada). */
export async function importBackup(d: CuentasDB, b: BackupFile): Promise<void> {
  await d.transaction('rw', d.accounts, d.transactions, d.categories, d.rules, d.rates, d.settings, async () => {
    await Promise.all(TABLAS.map((t) => d[t].clear()));
    await d.accounts.bulkAdd(b.accounts);
    await d.transactions.bulkAdd(b.transactions);
    await d.categories.bulkAdd(b.categories);
    await d.rules.bulkAdd(b.rules);
    await d.rates.bulkAdd(b.rates);
    await d.settings.bulkAdd(b.settings);
  });
}

export function backupFilename(dateIso: string): string {
  return `cuentas-claras-respaldo-${dateIso}.json`;
}
```

Y `src/backup/saveFile.ts`:

```ts
export type SaveStrategy = 'picker' | 'share' | 'download';

export function pickSaveStrategy(caps: { hasPicker: boolean; canShareFiles: boolean }): SaveStrategy {
  if (caps.hasPicker) return 'picker';
  if (caps.canShareFiles) return 'share';
  return 'download';
}

interface FsWindow {
  showSaveFilePicker?: (opts: {
    suggestedName: string;
    types: { description: string; accept: Record<string, string[]> }[];
  }) => Promise<{ createWritable(): Promise<{ write(b: Blob): Promise<void>; close(): Promise<void> }> }>;
}

/**
 * Guarda el JSON por el mejor camino disponible:
 * - Windows/Chromium: File System Access API (elige carpeta, p. ej. la de Google Drive).
 * - Android: share sheet con archivo (elige Drive) — Chrome Android no tiene showSaveFilePicker.
 * - Resto: descarga clásica.
 * Lanza AbortError si el usuario cancela el diálogo.
 */
export async function saveJsonFile(json: string, filename: string): Promise<SaveStrategy> {
  const archivo = new File([json], filename, { type: 'application/json' });
  const w = window as unknown as FsWindow;
  const estrategia = pickSaveStrategy({
    hasPicker: typeof w.showSaveFilePicker === 'function',
    canShareFiles: typeof navigator.canShare === 'function' && navigator.canShare({ files: [archivo] }),
  });
  if (estrategia === 'picker') {
    const handle = await w.showSaveFilePicker!({
      suggestedName: filename,
      types: [{ description: 'Respaldo JSON', accept: { 'application/json': ['.json'] } }],
    });
    const writable = await handle.createWritable();
    await writable.write(archivo);
    await writable.close();
  } else if (estrategia === 'share') {
    await navigator.share({ files: [archivo], title: filename });
  } else {
    const url = URL.createObjectURL(archivo);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
  return estrategia;
}
```

- [ ] **Paso 3: Verificar y commit.**

```powershell
npx vitest run
git add src/backup
git commit -m "Respaldo: export/import JSON versionado con round-trip y guardado multiplataforma

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 23: Sección Respaldo en Ajustes

**Files:**
- Create: `src/ui/componentes/RespaldoSection.tsx`
- Modify: `src/ui/paginas/AjustesPage.tsx`
- Test: `src/ui/componentes/RespaldoSection.test.tsx`

**Interfaces:**
- Consumes: todo lo de la Tarea 22; `todayIso`; `avisar`.
- Produces: `<RespaldoSection guardarArchivo?: (json: string, filename: string) => Promise<SaveStrategy> />` (prop inyectable para tests; defecto `saveJsonFile`). Flujo de restauración con confirmación y **auto-respaldo de seguridad previo** (spec §4.8).

- [ ] **Paso 1: Implementar `src/ui/componentes/RespaldoSection.tsx`:**

```tsx
import { useState } from 'react';
import { db } from '../../db/db';
import { backupFilename, exportBackup, importBackup, validateBackup, type BackupFile } from '../../backup/backup';
import { saveJsonFile, type SaveStrategy } from '../../backup/saveFile';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';

export function RespaldoSection({ guardarArchivo = saveJsonFile }: {
  guardarArchivo?: (json: string, filename: string) => Promise<SaveStrategy>;
}) {
  const [pendiente, setPendiente] = useState<{ backup: BackupFile; resumen: string } | null>(null);

  async function exportar() {
    try {
      const b = await exportBackup(db);
      const via = await guardarArchivo(JSON.stringify(b, null, 2), backupFilename(todayIso()));
      avisar(via === 'download' ? 'Respaldo descargado — súbelo a tu Drive' : 'Respaldo guardado ✓');
    } catch (e) {
      const err = e as Error;
      if (err.name !== 'AbortError') avisar(`No se pudo exportar: ${err.message}`);
    }
  }

  async function elegirArchivo(f: File) {
    try {
      const v = validateBackup(JSON.parse(await f.text()));
      if (!v.ok) {
        avisar(v.error);
        return;
      }
      setPendiente({
        backup: v.backup,
        resumen: `${v.backup.transactions.length} movimientos, ${v.backup.accounts.length} cuentas, exportado el ${v.backup.exportedAt.slice(0, 10)}`,
      });
    } catch {
      avisar('El archivo no es un JSON válido');
    }
  }

  async function restaurar() {
    if (!pendiente) return;
    try {
      // Auto-respaldo de seguridad ANTES de reemplazar (spec §4.8)
      const previo = await exportBackup(db);
      await guardarArchivo(JSON.stringify(previo, null, 2), backupFilename(`previo-${todayIso()}`));
      await importBackup(db, pendiente.backup);
      setPendiente(null);
      avisar('Datos restaurados ✓');
    } catch (e) {
      const err = e as Error;
      if (err.name === 'AbortError') avisar('Restauración cancelada: guarda primero el respaldo de seguridad');
      else avisar(`No se restauró nada: ${err.message}`);
    }
  }

  return (
    <div>
      <p style={{ color: 'var(--texto2)', fontSize: 13 }}>
        En Android: Exportar abre el selector/compartir — elige Drive. En Windows: elige directamente tu carpeta de Google Drive.
      </p>
      <button className="boton" onClick={exportar}>Exportar respaldo</button>
      <label>Restaurar desde un respaldo</label>
      <input
        aria-label="Archivo de respaldo"
        type="file"
        accept=".json,application/json"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) elegirArchivo(f);
          e.target.value = '';
        }}
      />
      {pendiente && (
        <div className="banner aviso">
          <p style={{ margin: '0 0 8px' }}>
            Vas a <strong>reemplazar TODOS los datos actuales</strong> por: {pendiente.resumen}.
            Antes se guardará un respaldo de seguridad de lo actual.
          </p>
          <div className="fila-form">
            <button className="boton secundario" onClick={() => setPendiente(null)}>Cancelar</button>
            <button className="boton peligro" onClick={restaurar}>Sí, reemplazar todo</button>
          </div>
        </div>
      )}
    </div>
  );
}
```

En `src/ui/paginas/AjustesPage.tsx`, sustituye el comentario de la Tarea 23 por:

```tsx
import { RespaldoSection } from '../componentes/RespaldoSection';
// ...
<div className="tarjeta"><h3>Respaldo (Google Drive)</h3><RespaldoSection /></div>
```

- [ ] **Paso 2: Test `src/ui/componentes/RespaldoSection.test.tsx`:**

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RespaldoSection } from './RespaldoSection';
import { db } from '../../db/db';
import { limpiarDb } from '../../test/utils';
import { buildManualTx } from '../../domain/builders';
import type { BackupFile } from '../../backup/backup';

beforeEach(async () => { await limpiarDb(); });

test('exportar produce un JSON válido con los seeds', async () => {
  const guardados: { json: string; nombre: string }[] = [];
  const fake = async (json: string, nombre: string) => {
    guardados.push({ json, nombre });
    return 'download' as const;
  };
  const user = userEvent.setup();
  render(<RespaldoSection guardarArchivo={fake} />);
  await user.click(screen.getByRole('button', { name: 'Exportar respaldo' }));
  await waitFor(() => expect(guardados).toHaveLength(1));
  const backup = JSON.parse(guardados[0].json);
  expect(backup.schemaVersion).toBe(1);
  expect(backup.accounts).toHaveLength(5);
  expect(guardados[0].nombre).toMatch(/^cuentas-claras-respaldo-\d{4}-\d{2}-\d{2}\.json$/);
});

test('restaurar reemplaza todo tras auto-respaldo de seguridad', async () => {
  await db.transactions.add(
    buildManualTx({ type: 'expense', accountId: 'acc-bnc', amountMinor: 100, categoryId: 'cat-ocio', dateIso: '2026-07-01' }),
  );
  const guardados: string[] = [];
  const fake = async (json: string) => {
    guardados.push(json);
    return 'download' as const;
  };
  const user = userEvent.setup();
  render(<RespaldoSection guardarArchivo={fake} />);

  const backup: BackupFile = {
    schemaVersion: 1,
    exportedAt: '2026-07-01T00:00:00.000Z',
    accounts: [{ id: 'x', name: 'Única', currency: 'USD', kind: 'cash', isSavings: false, archived: false }],
    transactions: [],
    categories: [],
    rules: [],
    rates: [],
    settings: [{ id: 'main', viewCurrency: 'VES', rateSource: 'MANUAL' }],
  };
  fireEvent.change(screen.getByLabelText('Archivo de respaldo'), {
    target: { files: [new File([JSON.stringify(backup)], 'r.json', { type: 'application/json' })] },
  });
  expect(await screen.findByText(/0 movimientos, 1 cuentas/)).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'Sí, reemplazar todo' }));
  await waitFor(async () => expect(await db.transactions.count()).toBe(0));
  expect(guardados).toHaveLength(1); // el auto-respaldo previo
  expect(await db.accounts.count()).toBe(1);
  expect((await db.settings.get('main'))?.viewCurrency).toBe('VES');
});

test('un JSON inválido no ofrece restaurar', async () => {
  render(<RespaldoSection guardarArchivo={async () => 'download' as const} />);
  fireEvent.change(screen.getByLabelText('Archivo de respaldo'), {
    target: { files: [new File(['{"nada": true}'], 'r.json', { type: 'application/json' })] },
  });
  await waitFor(() => expect(screen.queryByText(/reemplazar TODO/)).not.toBeInTheDocument());
});
```

- [ ] **Paso 3: Verificar.** `npx vitest run` → verde. Manual en Windows (`npm run dev` en Chrome/Edge): Exportar abre el diálogo del sistema para elegir carpeta (elige tu carpeta de Google Drive si la tienes sincronizada); restaurar el mismo archivo funciona y antes te pide guardar el respaldo previo.

- [ ] **Paso 4: Commit.**

```powershell
git add src/ui
git commit -m "Ajustes: sección de respaldo con exportar, restaurar y auto-respaldo previo

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

**Fin de fase:** `npm run build` sin errores.

---

## Fase 8 — PWA, deploy e instalación real

### Tarea 24: PWA — manifest, iconos, shortcuts y offline

**Files:**
- Create: `scripts/generar-iconos.mjs`, `public/iconos/` (PNGs generados — sí se commitean)
- Modify: `vite.config.ts` (reemplazar completo)

**Interfaces:**
- Consumes: `NuevoPage` ya lee `?tipo=` del hash (Tarea 8) — los shortcuts apuntan ahí.
- Produces: app instalable (manifest es-VE + iconos + maskable), shell y worker de pdf.js precacheados (funciona 100% offline), atajos "Nuevo gasto"/"Nuevo ingreso" al mantener pulsado el icono en Android.

- [ ] **Paso 1: Script de iconos** — `scripts/generar-iconos.mjs` (solo formas, sin texto — la rasterización de fuentes con sharp/librsvg no es fiable en Windows):

```js
import { mkdirSync } from 'node:fs';
import sharp from 'sharp';

// Moneda estilizada (anillo + barra, tipo ¢) sobre teal de la app.
function svg(maskable) {
  const rx = maskable ? 0 : 96;
  const escala = maskable ? 0.7 : 0.92; // zona segura para máscaras redondas
  return Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="${rx}" fill="#0e7490"/>
  <g transform="translate(256 256) scale(${escala}) translate(-256 -256)">
    <circle cx="256" cy="256" r="140" fill="none" stroke="#ffffff" stroke-width="38"/>
    <line x1="256" y1="70" x2="256" y2="442" stroke="#ffffff" stroke-width="38" stroke-linecap="round"/>
  </g>
</svg>`);
}

mkdirSync('public/iconos', { recursive: true });
await sharp(svg(false)).resize(192, 192).png().toFile('public/iconos/icono-192.png');
await sharp(svg(false)).resize(512, 512).png().toFile('public/iconos/icono-512.png');
await sharp(svg(true)).resize(512, 512).png().toFile('public/iconos/icono-maskable-512.png');
console.log('Iconos generados en public/iconos/');
```

Ejecutar: `npm run iconos` → esperado: 3 PNGs en `public/iconos/`.

- [ ] **Paso 2: Reemplazar `vite.config.ts` completo:**

```ts
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
        background_color: '#f6f7f9',
        theme_color: '#0e7490',
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
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        navigateFallback: '/index.html',
        // El worker de pdf.js pasa de 1 MB; debe quedar precacheado para importar Zinli offline
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      },
    }),
  ],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

- [ ] **Paso 3: Build y verificación offline (manual, bloqueante):**

```powershell
npm run build
npm run preview
```

En el navegador sobre la URL de preview:
1. DevTools → Application → Manifest: nombre, iconos y shortcuts presentes, sin avisos de instalabilidad.
2. Application → Service Workers: worker activo. En la lista de precache (Cache Storage) debe aparecer el asset del worker de pdf.js (busca `pdf.worker`).
3. Marca **Offline** en DevTools → recarga → la app abre y funciona (registrar un gasto).
4. Aún offline: Importar → Zinli → subir el PDF real → la extracción funciona (el worker vino del precache).

- [ ] **Paso 4: Suite y commit.**

```powershell
npx vitest run
git add scripts public/iconos vite.config.ts package.json
git commit -m "PWA: manifest en español, iconos, atajos de nuevo gasto/ingreso y precache offline

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 25: Deploy con HTTPS (Cloudflare Pages)

La instalación en Android exige HTTPS (spec §2). Cloudflare Pages con `wrangler pages deploy` publica el build **sin necesidad de repo público**. Alternativa equivalente si Cloudflare falla: `npx vercel deploy dist --prod`.

**Files:**
- Create: `wrangler.toml`
- Modify: `README.md` (sección de deploy), `docs/spikes/2026-07-07-cors-tasas.md` (resultado CORS desde producción)

- [ ] **Paso 1: PAUSA — pedir a Daniel el login** (una sola vez, es interactivo con navegador):

```powershell
npx wrangler login
```

No continúes hasta que confirme que inició sesión (cuenta gratuita de Cloudflare).

- [ ] **Paso 2: Crear `wrangler.toml`:**

```toml
name = "cuentas-claras"
pages_build_output_dir = "dist"
compatibility_date = "2026-07-07"
```

- [ ] **Paso 3: Crear el proyecto y desplegar.**

```powershell
npm run build
npx wrangler pages project create cuentas-claras --production-branch main
npx wrangler pages deploy dist --project-name cuentas-claras
```

Esperado: URL de producción tipo `https://cuentas-claras.pages.dev`. Anótala en el README (sección "Deploy": los 2 comandos de build+deploy y la URL).

- [ ] **Paso 4: Verificación desde producción.** Abrir la URL desplegada:
  1. La app carga por HTTPS y el service worker se registra.
  2. En la consola, re-ejecutar los fetch del spike (Tarea 2): confirmar que CORS también funciona desde `*.pages.dev` y actualizar la tabla del spike con una columna "desde producción". Si algún proveedor falla solo en producción, reordena `PROVIDERS_DEFAULT`.
  3. Las tasas del día aparecen solas en Ajustes → Tasas.

- [ ] **Paso 5: Commit.**

```powershell
git add wrangler.toml README.md docs/spikes
git commit -m "Deploy en Cloudflare Pages con verificación de CORS desde producción

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 26: Instalación real y validación del criterio de éxito

Checklist manual **con Daniel y sus dispositivos** (el agente guía, Daniel ejecuta en el teléfono). Criterio de éxito global del proyecto: *"Daniel instala la app en su Android y su PC, registra un gasto en efectivo en menos de 5 segundos, importa su TXT de BNC y su PDF de Zinli sin duplicados, ve su patrimonio consolidado en USD con separación disposición/ahorro, y guarda un respaldo JSON en su Drive."*

- [ ] **Android (Chrome):** abrir la URL → menú ⋮ → "Instalar app" → icono en el launcher → abre a pantalla completa (standalone, sin barra del navegador).
- [ ] **Atajos:** mantener pulsado el icono → aparece "➕ Nuevo gasto" → tocarlo abre directo el teclado numérico.
- [ ] **Registro < 5 s (cronometrado):** desde el icono/atajo: monto en efectivo → tocar categoría → guardado. La prioridad UX #1 se cumple.
- [ ] **Import BNC real:** subir el TXT real → 25 movimientos, par cargo+comisión agrupado, comisiones auto-categorizadas → confirmar → aceptar el ajuste de saldo inicial → el saldo BNC del dashboard coincide con el banco. Reimportar el MISMO archivo → 0 nuevos.
- [ ] **Import Zinli real:** subir el PDF real → banner verde "Cuadra" → confirmar → saldo Zinli correcto. Reimportar → 0 nuevos.
- [ ] **Patrimonio:** total en USD correcto; alternar a Bs; separación "A disposición" (BNC + Zinli + Efectivo) vs "Ahorro" (Binance + Efectivo ahorro) tras registrar los saldos reales de Binance y efectivo por Ajustar.
- [ ] **Tasas:** BCV y Binance del día visibles; cambiar la fuente activa recalcula el patrimonio; override manual funciona.
- [ ] **Respaldo Android:** Exportar → share sheet → Drive (2 toques) → el JSON aparece en Drive. Importar desde Drive → confirmación → auto-respaldo previo → datos intactos.
- [ ] **Offline:** modo avión → abrir la app → registrar un gasto → todo funciona; el dashboard muestra la última tasa con su fecha.
- [ ] **Windows (Edge o Chrome):** instalar desde el icono de la barra de direcciones → app con ventana propia; Exportar guarda directo en la carpeta local de Google Drive vía File System Access.
- [ ] **Cierre:** si todo pasa, actualizar el README con la URL final e invocar la skill `superpowers:finishing-a-development-branch`. Si algo falla, tratarlo como bug (skill `superpowers:systematic-debugging`), corregir y repetir SOLO el punto afectado.

- [ ] **Commit final** (si hubo correcciones):

```powershell
npx vitest run
npm run build
git add -A -- ':!TXTBNC1.txt' ':!TXTBNC2.txt' ':!PDFBNC.pdf' ':!ExcelBNC.xls' ':!Resumen_de_movimientos_*'
git commit -m "Validación en dispositivos reales: criterio de éxito v1 cumplido

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Cobertura spec → tareas (verificación rápida)

| Spec | Tareas |
|---|---|
| §2 Stack/PWA/hosting | 1, 24, 25 |
| §3 Modelo de datos + seeds + CRUD cuentas/categorías | 3, 5, 14 |
| §4.1 Registro rápido + shortcuts | 8, 24 |
| §4.2 Transferencias (cross-moneda con tasa editable) | 9 |
| §4.3 Ajuste de saldo (Binance/efectivo) | 9 |
| §4.4 Tasas BCV/Binance, fuente activa, override, offline | 2, 4, 13 |
| §4.5 Import BNC (dedupe, reglas, pares, hueco) | 15, 16, 17, 18 |
| §4.6 Import Zinli (pdf.js, cuadre, dedupe) | 19, 20, 21 |
| §4.7 Dashboard (patrimonio, mes, presupuestos, lista) | 6, 10, 11, 12 |
| §4.8 Respaldo JSON (picker/share, auto-export previo) | 22, 23 |
| §5 Manejo de errores | 13, 18, 21 (unparsed/cuadre/hueco/fallback) |
| §6 Testing | TDD en 3-6, 10, 12, 13, 15-17, 20-22 |
| Criterio de éxito global | 26 |

**Notas para el ejecutor:** las tareas van en orden (la 2 puede hacerse en paralelo con la 3-6 si hace falta, pero antes de la 13). Ninguna tarea posterior puede alargar el flujo de registro rápido más allá de FAB → dígitos → categoría. Ante cualquier duda entre este plan y la spec, gana la spec; ante duda entre plan y realidad de los comprobantes reales (gitignored), gana la realidad — ajusta el código Y la fixture (anonimizada) para reflejarla.

