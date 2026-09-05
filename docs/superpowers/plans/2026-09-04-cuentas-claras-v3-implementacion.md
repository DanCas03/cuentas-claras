# Cuentas Claras v3 — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** Añadir a la PWA «Cuentas Claras» (v2 en producción) el calendario de eventos con tarjeta semanal, la asignación de movimientos a eventos (1 toque desde Nuevo/captura, editor e imports), la categoría en grupos transaccionales y la tarjeta «Este mes» expandible con lista de categorías y navegación de mes, con Dexie v3 aditivo y respaldo `schemaVersion: 3` que acepta v2.

**Arquitectura:** La misma de v1/v2: SPA React con navegación por hash, IndexedDB vía Dexie + `useLiveQuery`, núcleo de dominio en funciones puras con TDD, UI sin tests automáticos pero con verificación visual headless bloqueante. Lo nuevo sigue el corte: `domain/calendar.ts` (expansión de recurrencias y estado de ocurrencias) y `domain/eventMatch.ts` (candidatas, sugerencia, asignación) son puros; `db/eventOps.ts` encapsula las operaciones transaccionales; la UI compone. Las ocurrencias son **DERIVADAS** (nunca se persisten): lo único persistido es `CalEvent` y la evidencia `eventId`+`eventDate` en la transacción.

**Stack:** React 19 + TypeScript 6 (`strict`, `noUnusedLocals`) + Vite 8 · Dexie 4.4.4 + dexie-react-hooks · Recharts · Vitest 4 (entorno `node`, fake-indexeddb, `globals: true`, setup `src/test/setup.ts`) · CSS puro con tokens de `estilos.css` · Node 23 · Playwright headless (chromium instalado) para verificación de UI.

**Spec (fuente de verdad del *qué*):** `docs/superpowers/specs/2026-09-04-cuentas-claras-v3-design.md`. Vigentes en todo lo no contradicho: spec v2, spec v1 y `.superpowers/sdd/restricciones-globales.md`.

## Restricciones globales

Aplican a TODAS las tareas; cada tarea las incluye implícitamente.

1. **Decisiones del spec v3 (20, sección «Decisiones de diseño») NO se reabren** durante la implementación. Cualquier desviación se declara en el reporte para que el revisor la valide.
2. Montos SIEMPRE enteros en céntimos; formato `Bs 13.517,75` / `$84.20` vía `formatMoney`. Todo en español (UI, docs, commits).
3. Tests SOLO en el núcleo (`src/domain`, `src/db`, `src/backup`) con TDD (RED → GREEN por paso). La UI no lleva tests automáticos: cada tarea de UI tiene verificación headless bloqueante con capturas en `.superpowers/sdd/capturas/v3-task-N/` (directorio gitignored).
4. Antes de cada commit: `npx vitest run` en verde. Al cerrar cada fase: `npm run build` sin errores (incluye `tsc --noEmit` con `noUnusedLocals`).
5. Patrones obligatorios: guard `guardando` en TODO guardado async (check → validación → `setGuardando(true)` → `await` en `try` con éxito-solo → `catch` `avisar` → `finally` reset; botones `disabled={guardando}`); ningún `useLiveQuery`/`useState`/`useMemo` después de un return condicional; jerarquía z contenido < nav(30) < modal(40) < toast(50); estilos SOLO por clases/tokens de `estilos.css` (los colores de categoría inline son dato del usuario, como en el donut); `d.transaction` con ARRAY de tablas si son >5.
6. **`txsEfectivas()` alimenta SOLO agregaciones**; los saldos (`accountBalanceMinor`, `patrimonio`) usan txs crudas. Los ids `virtual-…` jamás se persisten ni se asignan a eventos.
7. **Fechas locales por componentes**: prohibido `new Date('yyyy-mm-dd')`, `Date.parse` y `toISOString()` para fechas de calendario. `hoy` siempre se inyecta (`todayIso()` en la UI, string fijo en tests).
8. **Sin librerías nuevas.** Sin `Intl` para nombres de mes/día (tabla en español en `dates.ts`).
9. Capas: `src/domain`, `src/db`, `src/backup` no importan React ni `src/ui`; las funciones que tocan la base reciben `d: CuentasDB`; la UI usa el singleton `db`.
10. **No tocar** `src/import/*`, `src/db/commit.ts`, `src/domain/filters.ts`, `src/domain/builders.ts`, `src/domain/balances.ts`, `src/capture/*`, `CapturaPage.tsx`, `Nav.tsx`, `useHashRoute.ts`, `Deudas*`, `Patrimonio*`, `Ajustes*`.
11. Flujo ≤3 toques del gasto manual: intacto (la fila de chips de eventos solo existe con candidatas o evento activo).
12. Paralelismo (directiva de Daniel): tareas de una misma oleada solo si sus archivos son disjuntos; cada implementador hace `git add` SOLO de sus archivos y su commit propio; los paquetes de revisión se generan por commits de la tarea.
13. Entorno Windows 11; el tool Bash (Git Bash) para comandos. Cada implementador de UI levanta su dev server (`npm run dev -- --port 51NN`) y **lo mata al terminar**.
14. Footer de commit: las DOS líneas finales del mensaje son las que dicte la directiva de atribución
    de **la sesión que hace el commit** (`Co-Authored-By:` + `Claude-Session:`). NO se hardcodean aquí:
    esta restricción ya se reescribió tres veces porque cada sesión que retomó el plan tenía una
    atribución distinta. Cada commit queda atribuido a quien lo hizo, así que la rama tiene footers
    heterogéneos a propósito. El controlador se lo pasa a cada implementador en su despacho.

### Receta de verificación visual headless (para las tareas de UI)

Guarda variantes en el scratchpad (NUNCA en el repo). `npx -y -p playwright node verificar.mjs`.

```js
import { chromium } from 'playwright';
const PUERTO = process.env.PUERTO ?? '5173';
const navegador = await chromium.launch();
const pagina = await navegador.newPage({ viewport: { width: 390, height: 844 } });
await pagina.clock.install({ time: new Date(2026, 8, 4, 10, 0, 0) }); // «hoy» = 2026-09-04 (viernes) y congela Recharts
await pagina.goto(`http://localhost:${PUERTO}/#/`);
// OJO (hallazgo verificado en T5): con el reloj falso instalado, UN SOLO salto grande
// (`runFor(1500)`) deja de forma REPRODUCIBLE `accounts` en 0 mientras `categories` ya vale 18.
// No es pérdida de datos: es una carrera entre `page.clock` y el doble efecto de <StrictMode>
// sobre `seedIfEmpty`. Avanza en pasos pequeños Y espera a que el seed converja antes de sembrar:
for (let i = 0; i < 8; i++) await pagina.clock.runFor(200);
await pagina.waitForFunction(async () => {
  const d = window.__db;
  return !!d && (await d.accounts.count()) > 0;
}, null, { timeout: 10_000 });
// Siembra directa en IndexedDB (la app abre la BD 'cuentas-claras'; Dexie ya creó el esquema v3):
await pagina.evaluate(async () => {
  const { default: Dexie } = await import('https://unpkg.com/dexie@4.4.4/dist/dexie.mjs').catch(() => ({ default: null }));
  // Si el import CDN no está disponible offline, usa indexedDB nativo (ver receta alternativa abajo).
});
// … interacción específica de la tarea (clicks, fills, asserts) …
await pagina.screenshot({ path: '.superpowers/sdd/capturas/v3-task-N/01-nombre.png', fullPage: true });
await navegador.close();
```

**Receta alternativa de siembra (sin CDN, la recomendada):** la app expone en `window.__db` el singleton Dexie SOLO en desarrollo. La Tarea 5 añade en `src/main.tsx` la línea `if (import.meta.env.DEV) (window as unknown as { __db: unknown }).__db = db;`. Con eso, en Playwright: `await pagina.evaluate(async () => { const db = window.__db; await db.calEvents.put({...}); await db.transactions.add({...}); })`. `useLiveQuery` refresca solo tras la escritura. Al montar la app por primera vez en un perfil limpio, `seedIfEmpty` ya sembró cuentas y categorías.

Recharts se congela en headless: usa `pagina.clock.runFor(ms)` en vez de `waitForTimeout`. Verifica DE VERDAD lo que el paso describe antes de commitear.

## Estructura de archivos

```
Nuevos:
  src/domain/calendar.ts + calendar.test.ts        ← tipos de ocurrencia, buildEvent/validarEvento/parseDias, fechasDeEvento, estadoDe, ocurrenciasEnRango, totalesEsperados, descripcionRecurrencia
  src/domain/eventMatch.ts + eventMatch.test.ts    ← candidatasParaTx, sugerirOcurrencia, sugerirParaLote, aplicarAsignacion, validarAsignacion
  src/db/eventOps.ts + eventOps.test.ts            ← guardarEvento, eliminarEvento, asignarEvento, desasignarEvento, omitirOcurrencia, reanudarOcurrencia
  src/ui/componentes/SelectEvento.tsx              ← select compartido (editor de movimiento + imports)
  src/ui/componentes/EditorEvento.tsx              ← hoja modal de alta/edición de evento
  src/ui/componentes/SemanaCard.tsx                ← tarjeta «Esta semana» de Inicio
  src/ui/paginas/CalendarioPage.tsx                ← #/calendario
Modificados:
  src/domain/types.ts                ← Transaction.eventId/eventDate, TxGroup.categoryId, Recurrence, CalEvent
  src/db/db.ts + db.test.ts          ← version(3), tabla calEvents, índice eventId
  src/backup/backup.ts + .test.ts    ← schemaVersion 3, acepta v2 normalizado
  src/domain/dates.ts + .test.ts     ← helpers de calendario (locales, sin Intl)
  src/domain/groups.ts + .test.ts    ← categoriaDelResiduo, txsEfectivas con categorías
  src/db/groupOps.ts + .test.ts      ← agruparTxs con categoryId, actualizarGrupo
  src/domain/stats.ts + .test.ts     ← incomeByCategory
  src/main.tsx                       ← window.__db solo en DEV (siembra headless)
  src/ui/estilos.css                 ← bloques v3
  src/ui/App.tsx                     ← ruta /calendario (T11) y params a Movimientos (T14)
  src/ui/paginas/DashboardPage.tsx   ← mesVisto/primerMes (T12) y calEvents + SemanaCard (T14)
  src/ui/componentes/GraficasMes.tsx ← nav de mes + lista expandible
  src/ui/paginas/NuevoPage.tsx       ← chips de eventos + prefill evento/fecha
  src/ui/paginas/MovimientosPage.tsx ← params, categoría de grupo, EditorGrupo, SelectEvento en editor, meta 📅
  src/ui/componentes/ImportBnc.tsx, ImportZinli.tsx ← sugerencia + SelectEvento por fila
  README.md                          ← sección «Calendario y eventos»
```

## Oleadas y dependencias (paralelismo real por archivos disjuntos)

```
T1 (datos) ──┬─> T2 (dates) ──> T6 (calendar) ──> T7 (eventMatch) ──> T8 (eventOps)
             ├─> T3 (groups + groupOps)
             ├─> T4 (stats)
             └─> T5 (estilos.css + main.tsx)              [T5 no depende de nada; puede ir junto a T1]

Oleada UI (tras T5 y su dominio; archivos disjuntos entre sí):
  T9  SelectEvento + MovimientosPage            (dep T3, T8)
  T10 NuevoPage                                 (dep T7)
  T11 EditorEvento + SemanaCard + CalendarioPage + App.tsx(ruta)   (dep T8)
  T12 GraficasMes + DashboardPage(mesVisto)     (dep T4)
  T13 ImportBnc + ImportZinli                   (dep T9 por SelectEvento)
Integración:
  T14 DashboardPage(calEvents + SemanaCard) + App.tsx(params) + E2E headless   (dep T9–T13)
  T15 README + build + deploy + push + validación de Daniel
```

Orden sugerido: T1 → {T2, T3, T4, T5} → T6 → T7 → T8 → {T9, T10, T11, T12} → T13 → T14 → T15.

## Criterio de éxito (del spec)

> Daniel crea el evento «Quincena» (Sueldo/Pagos, BNC, quincenal) en 20 segundos; ve en Inicio la tira de la semana con el punto del 15 y «Por cobrar»; le llega el pago móvil, toca la notificación capturada y en Nuevo el chip «Quincena · 15 sep» está primero: un toque y el ingreso queda con categoría Sueldo y asignado a la quincena, que pasa a «hecha» en el calendario; importa el TXT del BNC y el pago móvil grande del 15 viene sugerido a Quincena mientras el del amigo no; agrupa los $5 que le pasaron con la compra de la leche, le pone categoría Supermercado y sus $2 de bolsillo aparecen en Supermercado; expande «Este mes» y ve la lista completa de categorías, retrocede a agosto y toca «Supermercado» para ver esos movimientos.

---

## Fase 0 — Capa de datos

### Tarea 1: Tipos v3, Dexie v3 y respaldo v3

**Files:**
- Modify: `src/domain/types.ts` (final del archivo: `Transaction`, `TxGroup`, nuevos `Recurrence` y `CalEvent`)
- Modify: `src/db/db.ts:4-30` (tabla `calEvents`, `version(3)`)
- Modify: `src/db/db.test.ts` (tests nuevos al final)
- Modify: `src/backup/backup.ts` (todo el archivo)
- Modify: `src/backup/backup.test.ts`

**Interfaces:**
- Consumes: nada nuevo.
- Produces: `Transaction.eventId?/eventDate?`, `TxGroup.categoryId?`, `Recurrence`, `CalEvent`, `db.calEvents: Table<CalEvent, string>`, índice `transactions.eventId`, `BackupFile.schemaVersion: 3` con `calEvents`, `validateBackup` que acepta v2.

- [ ] **Paso 1: Tipos.** En `src/domain/types.ts`, dentro de `Transaction` (tras `groupId?: string;`) añadir:

```ts
  /** v3: ocurrencia (eventId, eventDate) a la que se asignó. Van juntos: ambos o ninguno.
   *  eventDate es la fecha NOMINAL de la ocurrencia (p. ej. '2026-09-15' para la quincena),
   *  no necesariamente = date. No altera monto/fecha/cuenta/tipo; no afecta saldos ni agregaciones. */
  eventId?: string;
  eventDate?: string;
```

Reemplazar la interfaz `TxGroup` completa por:

```ts
/** Grupo de movimientos transaccionales (v2). El residuo es virtual: se deriva, no se persiste. */
export interface TxGroup {
  id: string;
  nota?: string;
  /** v3: categoría del residuo virtual. Se aplica SOLO si su kind coincide con el signo del
   *  residuo (expense ↔ residuo < 0, income ↔ residuo > 0); si no, cae en «Vuelto». */
  categoryId?: string;
  createdIso: string;
}
```

Añadir al final del archivo:

```ts
/** Reglas de recurrencia (v3). weekly/yearly derivan el día de la semana / mes+día de startDate. */
export type Recurrence =
  | { kind: 'once' }
  | { kind: 'weekly' }
  | { kind: 'monthly'; days: number[] }   // 1..31, ordenados, sin repetidos; día > días del mes → último día
  | { kind: 'yearly' };

/** Evento de calendario (v3): fecha en que hay que pagar o cobrar algo. NO es dinero:
 *  no altera saldos ni agregaciones. Sus ocurrencias se DERIVAN (calendar.ts). */
export interface CalEvent {
  id: string;
  titulo: string;          // «Quincena», «Netflix», «Condominio»
  categoryId: string;      // obligatoria: define pago (kind expense) o cobro (kind income) y la categoría que copia la tx asignada
  accountId?: string;      // cuenta esperada; define la moneda de amountMinor
  amountMinor?: number;    // monto esperado en céntimos de la moneda de accountId (REQUIERE accountId); ausente = variable
  startDate: string;       // 'yyyy-mm-dd' local; ancla y primera fecha posible
  endDate?: string;        // 'yyyy-mm-dd' inclusive; ausente = sin fin
  recurrence: Recurrence;
  omitidas?: string[];     // fechas nominales marcadas «omitida»
  createdIso: string;
}
```

- [ ] **Paso 2: Tests RED de db.** Añadir al final de `src/db/db.test.ts`:

```ts
test('esquema v3: calEvents acepta filas y el índice eventId consulta', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  await d.calEvents.add({
    id: 'e1', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
    startDate: '2026-01-01', recurrence: { kind: 'monthly', days: [15, 31] }, createdIso: '2026-09-04T10:00:00.000Z',
  });
  await d.transactions.add({
    id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000, date: '2026-09-15', source: 'manual',
    categoryId: 'cat-sueldo', eventId: 'e1', eventDate: '2026-09-15',
  });
  await d.transactions.add({ id: 't2', type: 'expense', accountId: 'acc-bnc', amountMinor: 100, date: '2026-09-15', source: 'manual' });
  expect(await d.calEvents.count()).toBe(1);
  expect(await d.transactions.where('eventId').equals('e1').count()).toBe(1);
  expect(await d.transactions.where('eventId').above('').count()).toBe(1);
  expect(await d.categories.count()).toBe(18); // sin seeds v3
});

test('update con undefined borra la propiedad (contrato que usa EditorMovimiento para desasignar)', async () => {
  const d = nuevaDb();
  await d.transactions.add({
    id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 1, date: '2026-09-15', source: 'manual',
    eventId: 'e1', eventDate: '2026-09-15',
  });
  await d.transactions.update('t1', { eventId: undefined, eventDate: undefined, note: 'x' });
  const t = await d.transactions.get('t1');
  expect(t && 'eventId' in t).toBe(false);
  expect(t && 'eventDate' in t).toBe(false);
  expect(t?.note).toBe('x');
});
```

- [ ] **Paso 3: Correr → deben fallar** (`calEvents` no existe / `eventId` no indexado).

Run: `npx vitest run src/db/db.test.ts`
Expected: FAIL.

- [ ] **Paso 4: Dexie v3.** En `src/db/db.ts`: importar `CalEvent` en el `import type`; añadir `calEvents!: Table<CalEvent, string>;` tras `txGroups`; y tras el bloque `this.version(2)` añadir:

```ts
    // v3 aditiva: tabla de eventos e índice eventId (decisión 1 del spec v3: ocurrencias derivadas).
    this.version(3).stores({
      transactions: 'id, date, accountId, categoryId, importKey, groupId, eventId',
      calEvents: 'id',
    });
```

- [ ] **Paso 5: Correr → verde.** `npx vitest run src/db/db.test.ts`. Si el test de `update` con `undefined` fallara (la propiedad sigue presente), cambia el contrato: EditorMovimiento usará `modify` con `delete` (anótalo en el reporte); NO borres el test, ajústalo a lo que Dexie haga realmente y documenta.

- [ ] **Paso 6: Tests RED de backup.** En `src/backup/backup.test.ts`: en `dbConDatos()` añadir tras el `txGroups.add`:

```ts
  await d.txGroups.update('grupo-1', { categoryId: 'cat-supermercado' });
  await d.calEvents.add({
    id: 'evento-1', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
    startDate: '2026-01-01', recurrence: { kind: 'monthly', days: [15, 31] }, omitidas: ['2026-08-31'],
    createdIso: '2026-09-04T10:00:00.000Z',
  });
  await d.transactions.add({
    id: 'tx-quincena', type: 'income', accountId: 'acc-bnc', amountMinor: 800000, date: '2026-09-15',
    source: 'manual', categoryId: 'cat-sueldo', eventId: 'evento-1', eventDate: '2026-09-15',
  });
```

En el test de round-trip cambiar `expect(await destino.transactions.count()).toBe(1)` por `.toBe(2)` y `expect((await destino.transactions.toArray())[0].note).toBe('prueba')` por `expect((await destino.transactions.toArray()).some((t) => t.note === 'prueba')).toBe(true)`; añadir al final del test:

```ts
  expect(await destino.calEvents.get('evento-1')).toEqual({
    id: 'evento-1', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
    startDate: '2026-01-01', recurrence: { kind: 'monthly', days: [15, 31] }, omitidas: ['2026-08-31'],
    createdIso: '2026-09-04T10:00:00.000Z',
  });
  expect((await destino.txGroups.get('grupo-1'))?.categoryId).toBe('cat-supermercado');
  expect(await destino.transactions.get('tx-quincena')).toMatchObject({ eventId: 'evento-1', eventDate: '2026-09-15' });
```

Reemplazar el test `validateBackup rechaza basura…` por:

```ts
const V2 = {
  schemaVersion: 2, exportedAt: 'x', accounts: [], transactions: [], categories: [],
  rules: [], rates: [], settings: [], debts: [], txGroups: [],
};

test('validateBackup: v3 exige las 9 tablas; v2 se acepta normalizado; v1 y otras se rechazan', () => {
  expect(validateBackup(null).ok).toBe(false);
  expect(validateBackup('texto').ok).toBe(false);
  expect(validateBackup({}).ok).toBe(false);
  const v3 = { ...V2, schemaVersion: 3, calEvents: [] };
  expect(validateBackup(v3).ok).toBe(true);
  const sinCal = validateBackup({ ...V2, schemaVersion: 3 });
  expect(sinCal.ok).toBe(false);
  if (!sinCal.ok) expect(sinCal.error).toContain('calEvents');
  expect(validateBackup({ ...v3, debts: undefined }).ok).toBe(false);
  const v2 = validateBackup(V2);
  expect(v2.ok).toBe(true);
  if (v2.ok) {
    expect(v2.backup.schemaVersion).toBe(3);
    expect(v2.backup.calEvents).toEqual([]);
  }
  expect(validateBackup({ ...V2, txGroups: undefined }).ok).toBe(false);
  const v1 = validateBackup({ ...V2, schemaVersion: 1 });
  expect(v1.ok).toBe(false);
  if (!v1.ok) expect(v1.error).toContain('versión 1');
  expect(validateBackup({ ...v3, schemaVersion: 4 }).ok).toBe(false);
});

test('un respaldo v2 se importa en una base v3 sin error', async () => {
  const v = validateBackup({ ...V2, settings: [{ id: 'main', viewCurrency: 'USD', rateSource: 'BCV' }] });
  expect(v.ok).toBe(true);
  if (!v.ok) return;
  const destino = new CuentasDB(`test-${crypto.randomUUID()}`);
  await importBackup(destino, v.backup);
  expect(await destino.calEvents.count()).toBe(0);
  expect(await destino.settings.count()).toBe(1);
});

test('exportBackup: las claves son exactamente TABLAS ∪ {schemaVersion, exportedAt} y la versión es 3', async () => {
  const b = await exportBackup(await dbConDatos());
  expect(b.schemaVersion).toBe(3);
  expect(Object.keys(b).sort()).toEqual(
    ['accounts', 'calEvents', 'categories', 'debts', 'exportedAt', 'rates', 'rules', 'schemaVersion', 'settings', 'transactions', 'txGroups'],
  );
});
```

- [ ] **Paso 7: Correr → RED.** `npx vitest run src/backup/backup.test.ts`.

- [ ] **Paso 8: backup.ts v3.** Reescribir `src/backup/backup.ts`:

```ts
import type { Account, CalEvent, Category, Debt, Rate, Rule, Settings, Transaction, TxGroup } from '../domain/types';
import { CuentasDB } from '../db/db';

export interface BackupFile {
  schemaVersion: 3;
  exportedAt: string; // ISO completo
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  rules: Rule[];
  rates: Rate[];
  settings: Settings[];
  debts: Debt[];
  txGroups: TxGroup[];
  calEvents: CalEvent[];
}

const TABLAS_V2 = ['accounts', 'transactions', 'categories', 'rules', 'rates', 'settings', 'debts', 'txGroups'] as const;
const TABLAS = [...TABLAS_V2, 'calEvents'] as const;

export async function exportBackup(d: CuentasDB): Promise<BackupFile> {
  const [accounts, transactions, categories, rules, rates, settings, debts, txGroups, calEvents] = await Promise.all([
    d.accounts.toArray(),
    d.transactions.toArray(),
    d.categories.toArray(),
    d.rules.toArray(),
    d.rates.toArray(),
    d.settings.toArray(),
    d.debts.toArray(),
    d.txGroups.toArray(),
    d.calEvents.toArray(),
  ]);
  return {
    schemaVersion: 3, exportedAt: new Date().toISOString(),
    accounts, transactions, categories, rules, rates, settings, debts, txGroups, calEvents,
  };
}

function faltaTabla(b: Record<string, unknown>, tablas: readonly string[]): string | null {
  for (const tabla of tablas) if (!Array.isArray(b[tabla])) return tabla;
  return null;
}

/** v3 exige las 9 tablas. v2 (8 tablas) se acepta normalizado en memoria con calEvents: [] —
 *  v2→v3 es puramente aditivo (decisión 16 del spec v3). v1 sigue rechazado (decisión 7 del spec v2). */
export function validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string } {
  if (typeof data !== 'object' || data === null) return { ok: false, error: 'El archivo no es un respaldo de Cuentas Claras' };
  const b = data as Record<string, unknown> & Partial<BackupFile>;
  const version = b.schemaVersion as number | undefined;
  if (version === 1) {
    return { ok: false, error: 'Este respaldo es de la versión 1 y ya no es compatible: re-ingresa o re-importa tus datos' };
  }
  if (version === 2) {
    const falta = faltaTabla(b, TABLAS_V2);
    if (falta) return { ok: false, error: `Al respaldo le falta la tabla "${falta}"` };
    return { ok: true, backup: { ...(b as unknown as Omit<BackupFile, 'schemaVersion' | 'calEvents'>), schemaVersion: 3, calEvents: [] } };
  }
  if (version !== 3) return { ok: false, error: `Versión de respaldo no soportada: ${String(version)}` };
  const falta = faltaTabla(b, TABLAS);
  if (falta) return { ok: false, error: `Al respaldo le falta la tabla "${falta}"` };
  return { ok: true, backup: b as BackupFile };
}

/** Importar reemplaza TODO el contenido, en una sola transacción (todo-o-nada). */
export async function importBackup(d: CuentasDB, b: BackupFile): Promise<void> {
  await d.transaction('rw', [d.accounts, d.transactions, d.categories, d.rules, d.rates, d.settings, d.debts, d.txGroups, d.calEvents], async () => {
    await Promise.all(TABLAS.map((t) => d[t].clear()));
    await d.accounts.bulkAdd(b.accounts);
    await d.transactions.bulkAdd(b.transactions);
    await d.categories.bulkAdd(b.categories);
    await d.rules.bulkAdd(b.rules);
    await d.rates.bulkAdd(b.rates);
    await d.settings.bulkAdd(b.settings);
    await d.debts.bulkAdd(b.debts);
    await d.txGroups.bulkAdd(b.txGroups);
    await d.calEvents.bulkAdd(b.calEvents);
  });
}

export function backupFilename(dateIso: string): string {
  return `cuentas-claras-respaldo-${dateIso}.json`;
}
```

- [ ] **Paso 9: Suite completa en verde y build.** `npx vitest run` (todo verde; los tests v2 no cambian) y `npm run build`.

- [ ] **Paso 10: Commit.**

```bash
git add src/domain/types.ts src/db/db.ts src/db/db.test.ts src/backup/backup.ts src/backup/backup.test.ts
git commit -m "feat(db): esquema Dexie v3 con calEvents, campos de evento y respaldo v3 que acepta v2" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_01EbFWGAvuqcj6BGusB5iUWL"
```

---

## Fase 1 — Núcleo independiente (T2, T3, T4, T5 en paralelo tras T1)

### Tarea 2: Helpers de calendario en `dates.ts`

**Files:**
- Modify: `src/domain/dates.ts` (añadir al final)
- Modify: `src/domain/dates.test.ts` (añadir al final)

**Interfaces:**
- Consumes: nada.
- Produces (todas puras, fechas LOCALES por componentes):
  `partsOf(iso)`, `isoFromParts(y,m,d)`, `esFechaIso(s)`, `daysInMonth(y,m)`, `addDays(iso,n)`, `diffDias(a,b)` (= b − a), `weekdayLunes0(iso)` (0 = lunes), `mesAnterior(month)`, `mesSiguiente(month)`, `primerDiaMes(month)`, `ultimoDiaMes(month)`, `nombreMes(month)` ('Septiembre 2026'), `nombreDia(iso)` ('viernes'), `fechaCorta(iso)` ('15 sep'), `diaCorto(iso)` ('vie 4'), `fechaLarga(iso)` ('Viernes 4 de septiembre'), `ddmmyyyy(iso)` ('04/09/2026'), `MES_CORTO`, `LETRAS_DIA`, `celdasMes(month)` (42 celdas `{ iso, enMes }`).

- [ ] **Paso 1: Tests RED.** Añadir al final de `src/domain/dates.test.ts` (y ampliar el import):

```ts
import {
  addDays, celdasMes, daysInMonth, ddmmyyyy, diaCorto, diffDias, esFechaIso, fechaCorta, fechaLarga,
  mesAnterior, mesSiguiente, nombreDia, nombreMes, primerDiaMes, ultimoDiaMes, weekdayLunes0, LETRAS_DIA,
} from './dates';

test('addDays cruza mes, año y 29 de febrero (local, sin UTC)', () => {
  expect(addDays('2026-01-31', 1)).toBe('2026-02-01');
  expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  expect(addDays('2024-02-28', 1)).toBe('2024-02-29');
  expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  expect(addDays('2026-09-04', 0)).toBe('2026-09-04');
});
test('daysInMonth y esFechaIso', () => {
  expect(daysInMonth(2026, 2)).toBe(28);
  expect(daysInMonth(2024, 2)).toBe(29);
  expect(daysInMonth(2026, 4)).toBe(30);
  expect(esFechaIso('2026-02-28')).toBe(true);
  expect(esFechaIso('2026-02-30')).toBe(false);
  expect(esFechaIso('2026-13-01')).toBe(false);
  expect(esFechaIso('2026-9-4')).toBe(false);
  expect(esFechaIso('')).toBe(false);
});
test('diffDias es b − a en días enteros', () => {
  expect(diffDias('2026-09-10', '2026-09-15')).toBe(5);
  expect(diffDias('2026-09-15', '2026-09-10')).toBe(-5);
  expect(diffDias('2026-08-31', '2026-09-01')).toBe(1);
  expect(diffDias('2026-09-04', '2026-09-04')).toBe(0);
});
test('weekdayLunes0: 0 = lunes … 6 = domingo', () => {
  expect(weekdayLunes0('2026-09-04')).toBe(4); // viernes
  expect(weekdayLunes0('2026-09-06')).toBe(6); // domingo
  expect(weekdayLunes0('2026-08-31')).toBe(0); // lunes
});
test('navegación de meses', () => {
  expect(mesAnterior('2026-01')).toBe('2025-12');
  expect(mesAnterior('2026-09')).toBe('2026-08');
  expect(mesSiguiente('2026-12')).toBe('2027-01');
  expect(primerDiaMes('2026-02')).toBe('2026-02-01');
  expect(ultimoDiaMes('2026-02')).toBe('2026-02-28');
  expect(ultimoDiaMes('2024-02')).toBe('2024-02-29');
});
test('nombres en español sin Intl', () => {
  expect(nombreMes('2026-09')).toBe('Septiembre 2026');
  expect(nombreDia('2026-09-04')).toBe('viernes');
  expect(fechaCorta('2026-09-15')).toBe('15 sep');
  expect(diaCorto('2026-09-04')).toBe('vie 4');
  expect(fechaLarga('2026-09-04')).toBe('Viernes 4 de septiembre');
  expect(ddmmyyyy('2026-09-04')).toBe('04/09/2026');
  expect(LETRAS_DIA).toEqual(['L', 'M', 'X', 'J', 'V', 'S', 'D']);
});
test('celdasMes: siempre 42 celdas desde el lunes ≤ día 1', () => {
  const sep = celdasMes('2026-09'); // 1 sep 2026 es martes
  expect(sep).toHaveLength(42);
  expect(sep[0]).toEqual({ iso: '2026-08-31', enMes: false });
  expect(sep[1]).toEqual({ iso: '2026-09-01', enMes: true });
  expect(sep.filter((c) => c.enMes)).toHaveLength(30);
  expect(sep[41].iso).toBe('2026-10-11');
  const feb = celdasMes('2026-02'); // 1 feb 2026 es domingo
  expect(feb[0].iso).toBe('2026-01-26');
  expect(feb[6]).toEqual({ iso: '2026-02-01', enMes: true });
});
```

- [ ] **Paso 2: Correr → RED.** `npx vitest run src/domain/dates.test.ts`.

- [ ] **Paso 3: Implementación.** Añadir al final de `src/domain/dates.ts`:

```ts
// ---- v3: calendario (todo LOCAL por componentes; nunca new Date('yyyy-mm-dd'), que parsea UTC) ----

const NOMBRES_MES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
export const MES_CORTO = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'] as const;
const DIA_CORTO = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom'];
const DIA_LARGO = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
/** Letras de la cabecera del calendario, lunes primero. */
export const LETRAS_DIA = ['L', 'M', 'X', 'J', 'V', 'S', 'D'] as const;

export function partsOf(iso: string): { y: number; m: number; d: number } {
  const [y, m, d] = iso.split('-').map(Number);
  return { y, m, d };
}

export function isoFromParts(y: number, m: number, d: number): string {
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function daysInMonth(y: number, m: number): number {
  return new Date(y, m, 0).getDate();
}

/** 'yyyy-mm-dd' con forma estricta y fecha real (30/02 → false). */
export function esFechaIso(s: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const { y, m, d } = partsOf(s);
  return m >= 1 && m <= 12 && d >= 1 && d <= daysInMonth(y, m);
}

export function addDays(iso: string, n: number): string {
  const { y, m, d } = partsOf(iso);
  const t = new Date(y, m - 1, d + n);
  return isoFromParts(t.getFullYear(), t.getMonth() + 1, t.getDate());
}

/** b − a en días enteros. Date.UTC sobre las partes: inmune a cambios de hora. */
export function diffDias(a: string, b: string): number {
  const A = partsOf(a);
  const B = partsOf(b);
  return Math.round((Date.UTC(B.y, B.m - 1, B.d) - Date.UTC(A.y, A.m - 1, A.d)) / 86_400_000);
}

/** 0 = lunes … 6 = domingo. */
export function weekdayLunes0(iso: string): number {
  const { y, m, d } = partsOf(iso);
  return (new Date(y, m - 1, d).getDay() + 6) % 7;
}

export function mesAnterior(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, '0')}`;
}

export function mesSiguiente(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`;
}

export function primerDiaMes(month: string): string {
  return `${month}-01`;
}

export function ultimoDiaMes(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return isoFromParts(y, m, daysInMonth(y, m));
}

/** 'Septiembre 2026' */
export function nombreMes(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const nombre = NOMBRES_MES[m - 1];
  return `${nombre[0].toUpperCase()}${nombre.slice(1)} ${y}`;
}

/** 'viernes' */
export function nombreDia(iso: string): string {
  return DIA_LARGO[weekdayLunes0(iso)];
}

/** '15 sep' */
export function fechaCorta(iso: string): string {
  const { m, d } = partsOf(iso);
  return `${d} ${MES_CORTO[m - 1]}`;
}

/** 'vie 4' */
export function diaCorto(iso: string): string {
  return `${DIA_CORTO[weekdayLunes0(iso)]} ${partsOf(iso).d}`;
}

/** 'Viernes 4 de septiembre' */
export function fechaLarga(iso: string): string {
  const { m, d } = partsOf(iso);
  const dia = nombreDia(iso);
  return `${dia[0].toUpperCase()}${dia.slice(1)} ${d} de ${NOMBRES_MES[m - 1]}`;
}

/** '04/09/2026' */
export function ddmmyyyy(iso: string): string {
  const { y, m, d } = partsOf(iso);
  return `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${y}`;
}

/** Grid mensual de 6 filas × 7 columnas (42 celdas fijas) desde el lunes ≤ día 1. */
export function celdasMes(month: string): { iso: string; enMes: boolean }[] {
  const primero = primerDiaMes(month);
  const inicio = addDays(primero, -weekdayLunes0(primero));
  return Array.from({ length: 42 }, (_, i) => {
    const iso = addDays(inicio, i);
    return { iso, enMes: iso.startsWith(month) };
  });
}
```

- [ ] **Paso 4: Correr → verde.** `npx vitest run src/domain/dates.test.ts` y luego `npx vitest run`.

- [ ] **Paso 5: Commit.**

```bash
git add src/domain/dates.ts src/domain/dates.test.ts
git commit -m "feat(dates): helpers de calendario locales sin Intl (addDays, diffDias, celdasMes, nombres en español)" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_01EbFWGAvuqcj6BGusB5iUWL"
```

---

### Tarea 3: Categoría del grupo transaccional (`groups.ts` + `groupOps.ts`)

**Files:**
- Modify: `src/domain/groups.ts` (import de la línea 1; `txsEfectivas` líneas 33-67; función nueva `categoriaDelResiduo`)
- Modify: `src/domain/groups.test.ts` (tests nuevos al final)
- Modify: `src/db/groupOps.ts` (`agruparTxs` líneas 17-27; función nueva `actualizarGrupo` al final)
- Modify: `src/db/groupOps.test.ts` (tests nuevos al final)

**Interfaces:**
- Consumes de T1: `TxGroup.categoryId?: string`.
- Produces:
  - `categoriaDelResiduo(grupo: TxGroup | undefined, residuoMinor: number, categories: Category[]): string`
  - `txsEfectivas(txs: Transaction[], accounts: Account[], grupos?: TxGroup[], categories?: Category[]): Transaction[]` — 4.º parámetro ADITIVO; sin él el comportamiento es EXACTAMENTE el de v2.
  - `agruparTxs(d: CuentasDB, txIds: string[], nota?: string, categoryId?: string): Promise<string>` — 4.º parámetro aditivo.
  - `actualizarGrupo(d: CuentasDB, groupId: string, patch: { nota?: string | null; categoryId?: string | null }): Promise<void>` — `null` BORRA la clave, `undefined` NO la toca.
- `residuosDeGrupo`, `anadirAGrupo`, `quitarDeGrupo`, `desagrupar` NO cambian de firma ni de comportamiento.

- [ ] **Paso 1: Tests RED de `groups`.** Añadir al final de `src/domain/groups.test.ts` (y ampliar la línea 1 del import a `import { categoriaDelResiduo, residuosDeGrupo, txsEfectivas } from './groups';` y la línea 3 a `import type { Account, Category, Transaction } from './types';`):

```ts
const CATEGORIAS: Category[] = [
  { id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#4ade80', kind: 'expense' },
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-vuelto-ingreso', name: 'Vuelto', icon: '↩️', color: '#34d399', kind: 'income' },
  { id: 'cat-vuelto-gasto', name: 'Vuelto', icon: '↪️', color: '#f87171', kind: 'expense' },
];
const grupoCon = (categoryId?: string) => ({ id: 'g1', categoryId, createdIso: 'x' });

test('categoriaDelResiduo: el kind de la categoría debe coincidir con el signo del residuo', () => {
  expect(categoriaDelResiduo(grupoCon('cat-supermercado'), -200, CATEGORIAS)).toBe('cat-supermercado');
  expect(categoriaDelResiduo(grupoCon('cat-supermercado'), 150, CATEGORIAS)).toBe('cat-vuelto-ingreso');
  expect(categoriaDelResiduo(grupoCon('cat-sueldo'), 150, CATEGORIAS)).toBe('cat-sueldo');
  expect(categoriaDelResiduo(grupoCon('cat-sueldo'), -200, CATEGORIAS)).toBe('cat-vuelto-gasto');
  expect(categoriaDelResiduo(grupoCon('cat-borrada'), -200, CATEGORIAS)).toBe('cat-vuelto-gasto');
  expect(categoriaDelResiduo(grupoCon(undefined), -200, CATEGORIAS)).toBe('cat-vuelto-gasto');
  expect(categoriaDelResiduo(undefined, 150, CATEGORIAS)).toBe('cat-vuelto-ingreso');
  expect(categoriaDelResiduo(grupoCon('cat-supermercado'), -200, [])).toBe('cat-vuelto-gasto');
});

test('txsEfectivas con categorías: el residuo negativo lleva la categoría del grupo', () => {
  const txs = [
    tx({ id: 'm1', type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ];
  const [virtual] = txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')], CATEGORIAS);
  expect(virtual).toMatchObject({ type: 'expense', amountMinor: 200, categoryId: 'cat-supermercado' });
  expect(virtual.id).toBe('virtual-g1-USD'); // los virtuales siguen sin persistirse
});

test('txsEfectivas: sin el 4.º parámetro el comportamiento es EXACTAMENTE el de v2', () => {
  const txs = [
    tx({ id: 'm1', type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ];
  expect(txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')])[0].categoryId).toBe('cat-vuelto-gasto');
});

test('txsEfectivas bimoneda: la regla se evalúa por moneda contra el signo de CADA residuo', () => {
  const txs = [
    tx({ id: 'm1', type: 'income', amountMinor: 500, groupId: 'g1' }),                          // +$5,00
    tx({ id: 'm2', type: 'expense', accountId: 'acc-bnc', amountMinor: 73723, groupId: 'g1' }), // −Bs 737,23
  ];
  const efectivas = txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')], CATEGORIAS);
  expect(efectivas.find((t) => t.id === 'virtual-g1-USD')?.categoryId).toBe('cat-vuelto-ingreso');
  expect(efectivas.find((t) => t.id === 'virtual-g1-VES')?.categoryId).toBe('cat-supermercado');
});

test('el residuo con categoría suma en byCategory y consume presupuesto', () => {
  const conBudget: Category[] = [
    { ...CATEGORIAS[0], monthlyBudgetMinor: 10000 }, ...CATEGORIAS.slice(1),
  ];
  const txs = [
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, categoryId: 'cat-otros-gastos', groupId: 'g1' }),
  ];
  const efectivas = txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')], conBudget);
  const resumen = monthSummary({
    txs: efectivas, accounts: CUENTAS, month: '2026-07', viewCurrency: 'USD', rates: [], activeSource: 'BCV',
  });
  expect(resumen.byCategory).toEqual([{ categoryId: 'cat-supermercado', expenseMinor: 200 }]);
  const estados = budgetStatus({
    txs: efectivas, accounts: CUENTAS, categories: conBudget, month: '2026-07', rates: [], activeSource: 'BCV',
  });
  expect(estados[0].gastoUsdMinor).toBe(200); // los $2 del residuo sí consumen Supermercado
});
```

- [ ] **Paso 2: Correr → RED.** `npx vitest run src/domain/groups.test.ts`. Expected: FAIL (`categoriaDelResiduo` no existe).

- [ ] **Paso 3: Implementar `groups.ts`.** Cambiar la línea 1 a:

```ts
import type { Account, Category, Currency, Transaction, TxGroup } from './types';
```

Añadir tras `residuosDeGrupo` (antes de `masReciente`):

```ts
/** Categoría del residuo virtual (v3). La categoría del grupo se aplica SOLO si su kind
 *  coincide con el signo del residuo (expense ↔ residuo < 0, income ↔ residuo > 0);
 *  si no coincide, no existe o no hay categorías, cae en «Vuelto» del signo (v2). */
export function categoriaDelResiduo(grupo: TxGroup | undefined, residuoMinor: number, categories: Category[]): string {
  const necesario = residuoMinor > 0 ? 'income' : 'expense';
  const elegida = grupo?.categoryId ? categories.find((c) => c.id === grupo.categoryId) : undefined;
  if (elegida && elegida.kind === necesario) return elegida.id;
  return residuoMinor > 0 ? 'cat-vuelto-ingreso' : 'cat-vuelto-gasto';
}
```

Reemplazar la firma y el cuerpo de `txsEfectivas` (líneas 33-67) por:

```ts
export function txsEfectivas(
  txs: Transaction[], accounts: Account[], grupos: TxGroup[] = [], categories: Category[] = [],
): Transaction[] {
  const grupoDe = new Map(grupos.map((g) => [g.id, g]));
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const sueltas: Transaction[] = [];
  const porGrupo = new Map<string, Transaction[]>();
  for (const t of txs) {
    if (t.groupId) {
      const lista = porGrupo.get(t.groupId) ?? [];
      lista.push(t);
      porGrupo.set(t.groupId, lista);
    } else {
      sueltas.push(t);
    }
  }
  const virtuales: Transaction[] = [];
  for (const [groupId, miembros] of porGrupo) {
    const ultimo = masReciente(miembros);
    const grupo = grupoDe.get(groupId);
    for (const { currency, residuoMinor } of residuosDeGrupo(miembros, accounts)) {
      const deLaMoneda = miembros.filter((t) => (monedaDe.get(t.accountId) ?? 'USD') === currency);
      virtuales.push({
        id: `virtual-${groupId}-${currency}`,
        type: residuoMinor > 0 ? 'income' : 'expense',
        accountId: masReciente(deLaMoneda).accountId,
        amountMinor: Math.abs(residuoMinor),
        date: ultimo.date,
        time: ultimo.time,
        categoryId: categoriaDelResiduo(grupo, residuoMinor, categories),
        note: grupo?.nota || 'Vuelto de grupo transaccional',
        source: 'manual',
        groupId,
      });
    }
  }
  return [...sueltas, ...virtuales];
}
```

El tipo `Currency` sigue usándose en `ResiduoMoneda`; no borres el import.

- [ ] **Paso 4: Correr → verde.** `npx vitest run src/domain/groups.test.ts` (todo verde, incluidos los 7 tests v2 sin tocar).

- [ ] **Paso 5: Tests RED de `groupOps`.** Añadir al final de `src/db/groupOps.test.ts` (y ampliar la línea 2 a `import { agruparTxs, anadirAGrupo, quitarDeGrupo, desagrupar, actualizarGrupo } from './groupOps';`):

```ts
test('agruparTxs acepta categoría; una categoría inexistente lanza sin crear nada', async () => {
  const { d, ids } = await dbConTxs();
  await expect(agruparTxs(d, [ids[0], ids[1]], 'leche', 'cat-no-existe')).rejects.toThrow('La categoría no existe');
  expect(await d.txGroups.count()).toBe(0);
  expect((await d.transactions.get(ids[0]))?.groupId).toBeUndefined();
  const gid = await agruparTxs(d, [ids[0], ids[1]], 'leche', 'cat-supermercado');
  expect(await d.txGroups.get(gid)).toMatchObject({ nota: 'leche', categoryId: 'cat-supermercado' });
});

test('actualizarGrupo: null borra la clave, undefined no la toca, categoría inexistente lanza', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]], 'leche');
  await actualizarGrupo(d, gid, { categoryId: 'cat-supermercado' });
  expect((await d.txGroups.get(gid))?.categoryId).toBe('cat-supermercado');
  expect((await d.txGroups.get(gid))?.nota).toBe('leche'); // undefined no tocó la nota
  await actualizarGrupo(d, gid, { nota: '  pan  ' });
  expect(await d.txGroups.get(gid)).toMatchObject({ nota: 'pan', categoryId: 'cat-supermercado' });
  await expect(actualizarGrupo(d, gid, { categoryId: 'cat-no-existe' })).rejects.toThrow('La categoría no existe');
  expect((await d.txGroups.get(gid))?.categoryId).toBe('cat-supermercado'); // nada a medias
  await actualizarGrupo(d, gid, { categoryId: null });
  const g = await d.txGroups.get(gid);
  expect(g && 'categoryId' in g).toBe(false);
  await actualizarGrupo(d, gid, { nota: null });
  expect((await d.txGroups.get(gid)) && 'nota' in (await d.txGroups.get(gid))!).toBe(false);
  await expect(actualizarGrupo(d, 'no-existe', { nota: 'x' })).rejects.toThrow('El grupo no existe');
});
```

- [ ] **Paso 6: Correr → RED.** `npx vitest run src/db/groupOps.test.ts`.

- [ ] **Paso 7: Implementar `groupOps.ts`.** Reemplazar `agruparTxs` (líneas 17-27) por:

```ts
/** Crea un grupo transaccional con ≥2 gastos/ingresos sueltos. Todo-o-nada.
 *  v3: `categoryId` opcional = categoría del residuo virtual (ver categoriaDelResiduo). */
export async function agruparTxs(d: CuentasDB, txIds: string[], nota?: string, categoryId?: string): Promise<string> {
  if (txIds.length < 2) throw new Error('Selecciona al menos 2 movimientos');
  const grupo: TxGroup = {
    id: crypto.randomUUID(),
    nota: nota?.trim() || undefined,
    createdIso: new Date().toISOString(),
  };
  if (categoryId) grupo.categoryId = categoryId;
  await d.transaction('rw', d.transactions, d.txGroups, d.categories, async () => {
    if (categoryId && !(await d.categories.get(categoryId))) throw new Error('La categoría no existe');
    await validarAgrupables(d, txIds);
    await d.txGroups.add(grupo);
    await d.transactions.where('id').anyOf(txIds).modify((t) => { t.groupId = grupo.id; });
  });
  return grupo.id;
}
```

Añadir al final del archivo:

```ts
/** Edita nota y/o categoría del grupo. `null` BORRA la clave; `undefined` no la toca. */
export async function actualizarGrupo(
  d: CuentasDB, groupId: string, patch: { nota?: string | null; categoryId?: string | null },
): Promise<void> {
  await d.transaction('rw', d.txGroups, d.categories, async () => {
    const grupo = await d.txGroups.get(groupId);
    if (!grupo) throw new Error('El grupo no existe');
    if (patch.categoryId && !(await d.categories.get(patch.categoryId))) throw new Error('La categoría no existe');
    const actualizado: TxGroup = { ...grupo };
    if (patch.nota !== undefined) {
      const limpia = patch.nota?.trim() ?? '';
      if (limpia === '') delete actualizado.nota;
      else actualizado.nota = limpia;
    }
    if (patch.categoryId !== undefined) {
      if (patch.categoryId) actualizado.categoryId = patch.categoryId;
      else delete actualizado.categoryId;
    }
    await d.txGroups.put(actualizado);
  });
}
```

- [ ] **Paso 8: Correr → verde y suite completa.** `npx vitest run src/db/groupOps.test.ts` y luego `npx vitest run`.

- [ ] **Paso 9: Commit.**

```bash
git add src/domain/groups.ts src/domain/groups.test.ts src/db/groupOps.ts src/db/groupOps.test.ts
git commit -m "feat(grupos): categoría del residuo virtual y actualizarGrupo" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 4: `incomeByCategory` en `stats.ts`

**Files:**
- Modify: `src/domain/stats.ts` (`ResumenMes` líneas 4-11; `monthSummary` líneas 13-65)
- Modify: `src/domain/stats.test.ts` (tests nuevos al final)

**Interfaces:**
- Consumes: nada.
- Produces: `ResumenMes.incomeByCategory: { categoryId: string | null; incomeMinor: number }[]` (desc por monto). Aditivo puro: `month`, `incomeMinor`, `expenseMinor`, `byCategory` (SOLO gastos), `dailyCumulative`, `omitidasSinTasa` quedan idénticos. `lastMonths` y `budgetStatus` no cambian.

- [ ] **Paso 1: Tests RED.** Añadir al final de `src/domain/stats.test.ts`:

```ts
test('incomeByCategory: ingresos por categoría, desc, con null y conversión', () => {
  const conIngresos: Transaction[] = [
    tx({ type: 'income', amountMinor: 2000, date: '2026-07-03', categoryId: 'cat-sueldo' }),      // $20,00
    tx({ type: 'income', amountMinor: 500, date: '2026-07-04', categoryId: 'cat-sueldo' }),       // $5,00
    tx({ type: 'income', accountId: 'bnc', amountMinor: 100000, date: '2026-07-05', categoryId: 'cat-pm' }), // Bs 1.000 → $10,00
    tx({ type: 'income', amountMinor: 100, date: '2026-07-06' }),                                 // sin categoría
    tx({ type: 'expense', amountMinor: 300, date: '2026-07-07', categoryId: 'cat-a' }),           // no cuenta
  ];
  const r = monthSummary({ txs: conIngresos, accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates, activeSource: 'BCV' });
  expect(r.incomeByCategory).toEqual([
    { categoryId: 'cat-sueldo', incomeMinor: 2500 },
    { categoryId: 'cat-pm', incomeMinor: 1000 },
    { categoryId: null, incomeMinor: 100 },
  ]);
  expect(r.incomeMinor).toBe(3600);
  expect(r.byCategory).toEqual([{ categoryId: 'cat-a', expenseMinor: 300 }]); // byCategory sigue siendo SOLO gastos
});

test('incomeByCategory está vacío si no hay ingresos y no altera el resumen v2', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates, activeSource: 'BCV' });
  expect(r.incomeByCategory).toEqual([{ categoryId: null, incomeMinor: 2000 }]); // el ingreso del fixture no tiene categoría
  expect(r.expenseMinor).toBe(1800);
  const sinNada = monthSummary({ txs: [], accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates, activeSource: 'BCV' });
  expect(sinNada.incomeByCategory).toEqual([]);
});
```

- [ ] **Paso 2: Correr → RED.** `npx vitest run src/domain/stats.test.ts`.

- [ ] **Paso 3: Implementación.** En `src/domain/stats.ts`:

Añadir a `ResumenMes`, justo después de `byCategory` (línea 8):

```ts
  /** v3: ingresos por categoría del mes, desc por monto. `byCategory` sigue siendo SOLO gastos. */
  incomeByCategory: { categoryId: string | null; incomeMinor: number }[];
```

Dentro de `monthSummary`, añadir tras `const porCategoria = new Map<string | null, number>();`:

```ts
  const porCategoriaIngreso = new Map<string | null, number>();
```

Reemplazar el bloque `if (t.type === 'income') { incomeMinor += minor; } else {` (líneas 41-42) por:

```ts
    if (t.type === 'income') {
      incomeMinor += minor;
      const keyIngreso = t.categoryId ?? null;
      porCategoriaIngreso.set(keyIngreso, (porCategoriaIngreso.get(keyIngreso) ?? 0) + minor);
    } else {
```

Añadir tras el cálculo de `byCategory` (línea 62):

```ts
  const incomeByCategory = [...porCategoriaIngreso.entries()]
    .map(([categoryId, ingreso]) => ({ categoryId, incomeMinor: ingreso }))
    .sort((a, b) => b.incomeMinor - a.incomeMinor);
```

Y cambiar el `return` final por:

```ts
  return { month: opts.month, incomeMinor, expenseMinor, byCategory, incomeByCategory, dailyCumulative, omitidasSinTasa };
```

- [ ] **Paso 4: Correr → verde y suite completa.** `npx vitest run src/domain/stats.test.ts` y luego `npx vitest run` (los tests de `groups.test.ts` que componen con `monthSummary` siguen verdes: el campo es aditivo).

- [ ] **Paso 5: Commit.**

```bash
git add src/domain/stats.ts src/domain/stats.test.ts
git commit -m "feat(stats): incomeByCategory aditivo en el resumen del mes" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 5: Estilos v3 y `window.__db` en desarrollo

**Files:**
- Modify: `src/ui/estilos.css` (añadir DOS bloques al final, tras `.grupo-miembros` línea 337)
- Modify: `src/main.tsx` (11 líneas; añadir import y una línea antes de `createRoot`)

**Interfaces:**
- Consumes: nada. **Esta tarea NO depende de ninguna otra y puede ir en paralelo con T1.**
- Produces (contrato de clases que T9–T14 usan LITERALMENTE; ninguna otra tarea añade CSS):
  - Layout de tarjeta/mes: `.tarjeta-cabecera`, `.nav-mes`, `.nav-mes .titulo`, `.ayuda`, `.chip.activo`
  - Semana: `.semana-tira`, `.semana-dia` (+ `.hoy`), `.semana-dia .letra`, `.semana-dia .num`, `.puntos`, `.punto` (+ `.hecha`, `.vencida`)
  - Ocurrencias: `.fila-ocurrencia` (+ `.hecha`), `.fila-ocurrencia .detalle`, `.fila-ocurrencia .nota`, `.fila-ocurrencia .meta`, `.acciones`, `.estado`, `.estado-pendiente`, `.estado-hecha`, `.estado-vencida`, `.estado-omitida`
  - Chips de evento en Nuevo: `.chips-eventos`, `.chip-evento` (+ `.activo`, `.sugerido`), `.chip-evento .cerrar`
  - Calendario: `.cal-cabecera`, `.cal-grid`, `.cal-dia` (+ `.otro-mes`, `.hoy`, `.seleccionado`), `.cal-dia .num`, `.cal-dia .mas`, `.cal-lista`
  - «Este mes» expandible: `.lista-categorias`, `.lista-categorias .subtitulo`, `.fila-categoria`, `.fila-categoria .icono`, `.fila-categoria .nombre`, `.fila-categoria .cifras`, `.fila-categoria .monto`, `.fila-categoria .pct`, `.barra-categoria`
  - Siembra headless: `window.__db` = singleton Dexie SOLO en `import.meta.env.DEV`.

- [ ] **Paso 1: Bloque «Calendario y eventos (v3)».** Añadir al final de `src/ui/estilos.css`:

```css
/* Calendario y eventos (v3). SOLO tokens existentes; jerarquía z intacta. */
.tarjeta-cabecera { display: flex; justify-content: space-between; align-items: center; }
.tarjeta-cabecera h3 { margin: 0; }

.nav-mes { display: flex; gap: 8px; align-items: center; }
.nav-mes button {
  min-width: 40px; min-height: 40px;
  background: var(--fondo-2); color: var(--texto2);
  border: 1px solid var(--borde-suave); border-radius: 999px;
  font-size: 16px; font-weight: 700; padding: 0;
  transition: transform var(--transicion), background var(--transicion), color var(--transicion);
}
.nav-mes button:active { transform: scale(0.94); background: var(--tarjeta-2); color: var(--texto); }
.nav-mes button:disabled { opacity: 0.45; cursor: default; transform: none; }
.nav-mes .titulo { flex: 1; text-align: center; margin: 0; font-size: 16px; font-weight: 700; }

.chip.activo { background: var(--primario-tinta); border-color: var(--primario); color: var(--texto); }
.ayuda { font-size: 12px; color: var(--texto2); margin: 4px 0; }

/* Tira de 7 días de la tarjeta «Esta semana» */
.semana-tira { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; margin: 10px 0 4px; }
.semana-dia {
  background: var(--fondo-2); border: 1px solid var(--borde-suave); border-radius: var(--radio-s);
  padding: 6px 0; display: flex; flex-direction: column; align-items: center; gap: 3px;
  transition: transform 90ms ease, background var(--transicion);
}
.semana-dia:active { transform: scale(0.94); background: var(--tarjeta-2); }
.semana-dia.hoy { border-color: var(--primario); background: var(--primario-tinta); }
.semana-dia .letra { font-size: 11px; color: var(--texto2); }
.semana-dia .num { font-size: 14px; font-weight: 700; }

.puntos { display: flex; gap: 3px; justify-content: center; min-height: 6px; }
.punto { width: 6px; height: 6px; border-radius: 50%; }
.punto.hecha { opacity: 0.4; }
.punto.vencida { outline: 1px solid var(--gasto); outline-offset: 1px; }

/* Fila de ocurrencia: misma anatomía que .fila-mov */
.fila-ocurrencia {
  display: flex; align-items: center; gap: 12px; padding: 12px 4px;
  border: none; border-bottom: 1px solid var(--borde-suave); width: 100%;
  background: none; text-align: left; font-size: 14px; color: var(--texto);
}
.fila-ocurrencia.hecha { opacity: 0.55; }
.fila-ocurrencia .detalle { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.fila-ocurrencia .nota { min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 600; }
.fila-ocurrencia .meta { font-size: 12px; color: var(--texto2); }
.acciones { display: flex; gap: 6px; margin-top: 6px; flex-wrap: wrap; }

.estado {
  display: inline-block; border-radius: 999px; padding: 2px 10px;
  font-size: 12px; font-weight: 600; border: 1px solid var(--borde-suave); white-space: nowrap;
}
.estado-pendiente { border-color: var(--borde); color: var(--texto2); }
.estado-hecha { background: var(--ingreso-tinta); border-color: var(--ingreso); color: var(--ingreso); }
.estado-vencida { background: var(--gasto-tinta); border-color: var(--gasto); color: var(--gasto); }
.estado-omitida { color: var(--texto2); text-decoration: line-through; }

/* Chips de evento en Nuevo: UNA fila con scroll horizontal (no desplaza la grilla) */
.chips-eventos {
  display: flex; gap: 8px; overflow-x: auto; scrollbar-width: none;
  padding: 4px 0; margin: 8px 0;
}
.chips-eventos::-webkit-scrollbar { display: none; }
.chip-evento {
  flex: 0 0 auto; min-height: 40px; white-space: nowrap;
  display: inline-flex; align-items: center; gap: 6px;
  background: var(--fondo-2); color: var(--texto); border: 1px solid var(--borde-suave);
  border-radius: 999px; padding: 6px 14px; font-size: 13px; font-weight: 600;
  transition: transform 90ms ease, background var(--transicion), border-color var(--transicion);
}
.chip-evento:active { transform: scale(0.94); background: var(--tarjeta-2); }
.chip-evento.activo { background: var(--primario-tinta); border-color: var(--primario); }
.chip-evento.sugerido { outline: 1px solid var(--primario); outline-offset: 1px; }
.chip-evento .cerrar { color: var(--texto2); font-size: 14px; font-weight: 700; }

/* Rejilla mensual del calendario: 7 columnas × 6 filas fijas */
.cal-cabecera {
  display: grid; grid-template-columns: repeat(7, 1fr);
  font-size: 11px; color: var(--texto2); text-transform: uppercase; text-align: center;
  letter-spacing: 0.06em; margin-bottom: 4px;
}
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
.cal-dia {
  aspect-ratio: 1 / 0.9;
  background: var(--fondo-2); border: 1px solid var(--borde-suave); border-radius: var(--radio-s);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  padding: 4px 0;
  transition: transform 90ms ease, background var(--transicion), border-color var(--transicion);
}
.cal-dia:active { transform: scale(0.94); }
.cal-dia.otro-mes { opacity: 0.35; }
.cal-dia.hoy { border-color: var(--primario); }
.cal-dia.seleccionado { background: var(--primario-tinta); }
.cal-dia .num { font-size: 13px; font-weight: 600; }
.cal-dia .mas { font-size: 10px; color: var(--texto2); }
.cal-lista { margin-top: 8px; }
```

- [ ] **Paso 2: Bloque «Este mes expandible (v3)».** Añadir a continuación:

```css
/* Este mes expandible (v3) */
.lista-categorias { margin-top: 8px; }
.lista-categorias .subtitulo {
  font-size: 12px; text-transform: uppercase; letter-spacing: 0.06em;
  color: var(--texto2); margin: 10px 0 4px;
}
.fila-categoria {
  display: grid; grid-template-columns: 28px 1fr auto; gap: 10px; align-items: center;
  padding: 8px 0; border-bottom: 1px solid var(--borde-suave);
  color: var(--texto); text-decoration: none;
}
.fila-categoria .icono { font-size: 20px; text-align: center; }
.fila-categoria .nombre {
  font-size: 14px; font-weight: 600;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.barra-categoria {
  height: 6px; border-radius: 999px; background: var(--fondo-2);
  margin-top: 4px; overflow: hidden;
}
.barra-categoria > div { height: 100%; border-radius: 999px; transition: width 300ms ease; }
.fila-categoria .cifras { display: flex; flex-direction: column; align-items: flex-end; }
.fila-categoria .monto {
  font-size: 14px; font-weight: 700; text-align: right;
  font-variant-numeric: tabular-nums; font-feature-settings: 'tnum';
}
.fila-categoria .pct { font-size: 12px; color: var(--texto2); text-align: right; }
```

- [ ] **Paso 3: `window.__db` solo en DEV.** Reescribir `src/main.tsx` completo:

```tsx
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
```

- [ ] **Paso 4: Verificación headless bloqueante (que nada se rompió y que `__db` existe).** Levanta `npm run dev -- --port 5105` y corre este script (guárdalo en el scratchpad, NUNCA en el repo) con `npx -y -p playwright node verificar.mjs`:

```js
import { chromium } from 'playwright';
const navegador = await chromium.launch();
const pagina = await navegador.newPage({ viewport: { width: 390, height: 844 } });
await pagina.clock.install({ time: new Date(2026, 8, 4, 10, 0, 0) });
await pagina.goto('http://localhost:5105/#/');
await pagina.clock.runFor(1500);
const hayDb = await pagina.evaluate(async () => {
  const d = window.__db;
  if (!d) return null;
  return { cuentas: await d.accounts.count(), categorias: await d.categories.count(), tablas: d.tables.map((t) => t.name).sort() };
});
console.log(hayDb);
await pagina.screenshot({ path: '.superpowers/sdd/capturas/v3-task-5/01-inicio-intacto.png', fullPage: true });
await pagina.goto('http://localhost:5105/#/movimientos');
await pagina.clock.runFor(800);
await pagina.screenshot({ path: '.superpowers/sdd/capturas/v3-task-5/02-movimientos-intacto.png', fullPage: true });
await navegador.close();
```

Verifica DE VERDAD: (a) la consola imprime `cuentas: 5`, `categorias: 18` y `tablas` incluye `calEvents` — si `hayDb` es `null`, `window.__db` no quedó expuesto y T9–T14 no podrán sembrar; (b) `01-inicio-intacto.png` se ve idéntico a la v2 (Patrimonio, Deudas, Este mes, donut y área) — los bloques nuevos son clases sin uso todavía y NO deben alterar nada existente; (c) `02-movimientos-intacto.png` mantiene filtros y filas. Mata el dev server al terminar.

- [ ] **Paso 5: Build.** `npm run build` sin errores.

- [ ] **Paso 6: Commit.**

```bash
git add src/ui/estilos.css src/main.tsx
git commit -m "feat(estilos): bloques v3 de calendario y lista de categorías; window.__db en desarrollo" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

## Fase 2 — Calendario, sugerencias y operaciones (secuencial: T6 → T7 → T8)

### Tarea 6: `src/domain/calendar.ts` (ocurrencias derivadas)

**Files:**
- Create: `src/domain/calendar.ts`
- Create: `src/domain/calendar.test.ts`

**Interfaces:**
- Consumes de T1: `CalEvent`, `Recurrence`, `Transaction.eventId/eventDate`. De T2: `addDays`, `daysInMonth`, `ddmmyyyy`, `diffDias`, `esFechaIso`, `isoFromParts`, `mesSiguiente`, `nombreDia`, `partsOf`, `MES_CORTO`. De v1: `monthOf`.
- Produces (TODO puro, sin React, sin Dexie; `hoy` SIEMPRE parámetro):

```ts
export type EstadoOcurrencia = 'pendiente' | 'hecha' | 'vencida' | 'omitida';
export interface Ocurrencia { eventId: string; date: string }
export interface MontoMoneda { currency: Currency; totalMinor: number }
export interface OcurrenciaVista extends Ocurrencia {
  evento: CalEvent; estado: EstadoOcurrencia; txIds: string[];
  asignadoPorMoneda: MontoMoneda[]; fueraDeRegla: boolean;
}
export const RECURRENCIA_QUINCENAL: Recurrence;
export function esQuincenal(r: Recurrence): boolean;
export function validarEvento(e: CalEvent): void;
export function buildEvent(opts: { titulo: string; categoryId: string; accountId?: string; amountMinor?: number;
  startDate: string; endDate?: string; recurrence: Recurrence; omitidas?: string[]; nowIso: string }): CalEvent;
export function parseDias(texto: string): number[];
export function fechasDeEvento(e: CalEvent, desde: string, hasta: string): string[];
export function kindDeEvento(e: CalEvent, categories: Category[]): CategoryKind | null;
export function estadoDe(o: { date: string; omitida: boolean; txIds: string[] }, hoy: string): EstadoOcurrencia;
export function ocurrenciasEnRango(opts: { eventos: CalEvent[]; txs: Transaction[]; accounts: Account[];
  desde: string; hasta: string; hoy: string }): OcurrenciaVista[];
export function totalesEsperados(ocurrencias: OcurrenciaVista[], accounts: Account[], categories: Category[]):
  { pagar: MontoMoneda[]; cobrar: MontoMoneda[] };
export function descripcionRecurrencia(e: CalEvent): string;
```

- [ ] **Paso 1: Tests RED.** Crear `src/domain/calendar.test.ts` con el contenido COMPLETO:

```ts
import {
  buildEvent, descripcionRecurrencia, esQuincenal, estadoDe, fechasDeEvento, kindDeEvento,
  ocurrenciasEnRango, parseDias, totalesEsperados, validarEvento, RECURRENCIA_QUINCENAL,
} from './calendar';
import type { Account, CalEvent, Category, Transaction } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-zinli', name: 'Zinli', currency: 'USD', kind: 'wallet', isSavings: false, archived: false },
];
const CATEGORIAS: Category[] = [
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#22d3ee', kind: 'expense' },
];
const ev = (p: Partial<CalEvent>): CalEvent => ({
  id: 'e1', titulo: 'Quincena', categoryId: 'cat-sueldo', startDate: '2026-01-01',
  recurrence: { kind: 'monthly', days: [15, 31] }, createdIso: '2026-09-04T10:00:00.000Z', ...p,
});
const tx = (p: Partial<Transaction>): Transaction => ({
  id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000,
  date: '2026-09-15', source: 'manual', ...p,
});

test('parseDias ordena, deduplica y valida el rango 1..31', () => {
  expect(parseDias('15, 31')).toEqual([15, 31]);
  expect(parseDias('31,15')).toEqual([15, 31]);
  expect(parseDias('15 15')).toEqual([15]);
  expect(parseDias(' 5 ')).toEqual([5]);
  expect(() => parseDias('')).toThrow('Días del mes inválidos');
  expect(() => parseDias('40')).toThrow('Días del mes inválidos');
  expect(() => parseDias('0')).toThrow('Días del mes inválidos');
  expect(() => parseDias('a')).toThrow('Días del mes inválidos');
});

test('validarEvento: cada mensaje en español', () => {
  expect(() => validarEvento(ev({ titulo: '  ' }))).toThrow('Ponle nombre al evento');
  expect(() => validarEvento(ev({ categoryId: '' }))).toThrow('Elige una categoría');
  expect(() => validarEvento(ev({ amountMinor: 800000 }))).toThrow('El monto esperado necesita una cuenta');
  expect(() => validarEvento(ev({ accountId: 'acc-bnc', amountMinor: 0 }))).toThrow('Monto inválido');
  expect(() => validarEvento(ev({ accountId: 'acc-bnc', amountMinor: 10.5 }))).toThrow('Monto inválido');
  expect(() => validarEvento(ev({ startDate: '2026-02-30' }))).toThrow('Fecha inválida');
  expect(() => validarEvento(ev({ endDate: '2026-13-01' }))).toThrow('Fecha de fin inválida');
  expect(() => validarEvento(ev({ startDate: '2026-09-10', endDate: '2026-09-09' }))).toThrow('El fin no puede ser antes del inicio');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [31, 15] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [15, 15] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [32] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ omitidas: ['2026-02-30'] }))).toThrow('Fecha omitida inválida');
  expect(() => validarEvento(ev({ accountId: 'acc-bnc', amountMinor: 800000, endDate: '2026-12-31', omitidas: ['2026-08-31'] }))).not.toThrow();
});

test('buildEvent: id nuevo, título recortado, sin claves vacías y validado', () => {
  const e = buildEvent({
    titulo: '  Netflix  ', categoryId: 'cat-suscripciones', startDate: '2026-09-05',
    recurrence: { kind: 'monthly', days: [5] }, nowIso: '2026-09-04T10:00:00.000Z',
  });
  expect(e.titulo).toBe('Netflix');
  expect(e.id).toMatch(/[0-9a-f-]{36}/);
  expect('accountId' in e).toBe(false);
  expect('amountMinor' in e).toBe(false);
  expect('endDate' in e).toBe(false);
  expect('omitidas' in e).toBe(false);
  expect(e.createdIso).toBe('2026-09-04T10:00:00.000Z');
  expect(() => buildEvent({
    titulo: 'X', categoryId: 'cat-sueldo', amountMinor: 100, startDate: '2026-09-05',
    recurrence: { kind: 'once' }, nowIso: 'x',
  })).toThrow('El monto esperado necesita una cuenta');
});

test('fechasDeEvento once: solo si cae en el rango; endDate se ignora', () => {
  const e = ev({ startDate: '2026-10-20', recurrence: { kind: 'once' }, endDate: '2026-01-01' });
  expect(fechasDeEvento(e, '2026-10-01', '2026-10-31')).toEqual(['2026-10-20']);
  expect(fechasDeEvento(e, '2026-11-01', '2026-11-30')).toEqual([]);
  expect(fechasDeEvento(e, '2026-10-20', '2026-10-20')).toEqual(['2026-10-20']);
});

test('fechasDeEvento weekly: salta al primer múltiplo dentro del rango y respeta endDate', () => {
  const e = ev({ startDate: '2026-09-02', recurrence: { kind: 'weekly' } }); // miércoles
  expect(fechasDeEvento(e, '2026-09-01', '2026-09-30'))
    .toEqual(['2026-09-02', '2026-09-09', '2026-09-16', '2026-09-23', '2026-09-30']);
  expect(fechasDeEvento(e, '2026-10-01', '2026-10-31'))
    .toEqual(['2026-10-07', '2026-10-14', '2026-10-21', '2026-10-28']);
  expect(fechasDeEvento(e, '2026-08-01', '2026-08-31')).toEqual([]); // anterior al ancla
  expect(fechasDeEvento(ev({ startDate: '2026-09-02', endDate: '2026-09-16', recurrence: { kind: 'weekly' } }), '2026-09-01', '2026-09-30'))
    .toEqual(['2026-09-02', '2026-09-09', '2026-09-16']); // endDate inclusive
});

test('fechasDeEvento monthly: día > días del mes cae en el último día y deduplica', () => {
  const q = ev({ recurrence: RECURRENCIA_QUINCENAL });
  expect(fechasDeEvento(q, '2026-01-01', '2026-01-31')).toEqual(['2026-01-15', '2026-01-31']);
  expect(fechasDeEvento(q, '2026-02-01', '2026-02-28')).toEqual(['2026-02-15', '2026-02-28']);
  expect(fechasDeEvento(q, '2026-04-01', '2026-04-30')).toEqual(['2026-04-15', '2026-04-30']);
  expect(fechasDeEvento(ev({ recurrence: { kind: 'monthly', days: [30, 31] } }), '2026-02-01', '2026-02-28'))
    .toEqual(['2026-02-28']); // 30 y 31 colapsan en una sola fecha
  expect(fechasDeEvento(ev({ startDate: '2024-01-01', recurrence: RECURRENCIA_QUINCENAL }), '2024-02-01', '2024-02-29'))
    .toEqual(['2024-02-15', '2024-02-29']);
  expect(fechasDeEvento(q, '2026-09-01', '2026-10-31'))
    .toEqual(['2026-09-15', '2026-09-30', '2026-10-15', '2026-10-31']);
  const tardio = ev({ startDate: '2026-09-20', recurrence: { kind: 'monthly', days: [15] } });
  expect(fechasDeEvento(tardio, '2026-09-01', '2026-09-30')).toEqual([]);   // el ancla recorta
  expect(fechasDeEvento(tardio, '2026-10-01', '2026-10-31')).toEqual(['2026-10-15']);
  expect(fechasDeEvento(ev({ endDate: '2026-09-15', recurrence: RECURRENCIA_QUINCENAL }), '2026-09-01', '2026-09-30'))
    .toEqual(['2026-09-15']); // endDate inclusive
});

test('fechasDeEvento yearly: recorta el 29 de febrero en años no bisiestos', () => {
  const e = ev({ startDate: '2024-02-29', recurrence: { kind: 'yearly' } });
  expect(fechasDeEvento(e, '2025-01-01', '2025-12-31')).toEqual(['2025-02-28']);
  expect(fechasDeEvento(e, '2028-01-01', '2028-12-31')).toEqual(['2028-02-29']);
  expect(fechasDeEvento(e, '2025-01-01', '2026-12-31')).toEqual(['2025-02-28', '2026-02-28']);
  expect(fechasDeEvento(e, '2023-01-01', '2023-12-31')).toEqual([]); // antes del ancla
});

test('estadoDe: hecha > omitida > vencida > pendiente; hoy es pendiente', () => {
  const hoy = '2026-09-04';
  expect(estadoDe({ date: '2026-08-31', omitida: true, txIds: ['t1'] }, hoy)).toBe('hecha');
  expect(estadoDe({ date: '2026-08-31', omitida: true, txIds: [] }, hoy)).toBe('omitida');
  expect(estadoDe({ date: '2026-08-31', omitida: false, txIds: [] }, hoy)).toBe('vencida');
  expect(estadoDe({ date: '2026-09-04', omitida: false, txIds: [] }, hoy)).toBe('pendiente');
  expect(estadoDe({ date: '2026-09-15', omitida: false, txIds: [] }, hoy)).toBe('pendiente');
});

test('kindDeEvento devuelve el kind de su categoría o null', () => {
  expect(kindDeEvento(ev({}), CATEGORIAS)).toBe('income');
  expect(kindDeEvento(ev({ categoryId: 'cat-suscripciones' }), CATEGORIAS)).toBe('expense');
  expect(kindDeEvento(ev({ categoryId: 'cat-borrada' }), CATEGORIAS)).toBeNull();
});

test('ocurrenciasEnRango: estados, txIds, suma por moneda y orden', () => {
  const eventos = [
    ev({ id: 'e1', titulo: 'Quincena', recurrence: RECURRENCIA_QUINCENAL, omitidas: ['2026-09-30'] }),
    ev({ id: 'e2', titulo: 'Netflix', categoryId: 'cat-suscripciones', startDate: '2026-01-05', recurrence: { kind: 'monthly', days: [5] } }),
  ];
  const txs = [
    tx({ id: 'a', eventId: 'e1', eventDate: '2026-09-15', amountMinor: 500000 }),
    tx({ id: 'b', eventId: 'e1', eventDate: '2026-09-15', amountMinor: 300000 }),
    tx({ id: 'c', eventId: 'e1', eventDate: '2026-09-15', accountId: 'acc-zinli', amountMinor: 1000 }),
    tx({ id: 'd', eventId: 'fantasma', eventDate: '2026-09-15' }), // evento inexistente: se ignora
  ];
  const o = ocurrenciasEnRango({ eventos, txs, accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04' });
  expect(o.map((x) => `${x.date} ${x.evento.titulo} ${x.estado}`)).toEqual([
    '2026-09-05 Netflix pendiente',
    '2026-09-15 Quincena hecha',
    '2026-09-30 Quincena omitida',
  ]);
  const quincena = o[1];
  expect(quincena.txIds.sort()).toEqual(['a', 'b', 'c']);
  expect(quincena.asignadoPorMoneda).toContainEqual({ currency: 'VES', totalMinor: 800000 });
  expect(quincena.asignadoPorMoneda).toContainEqual({ currency: 'USD', totalMinor: 1000 });
  expect(quincena.fueraDeRegla).toBe(false);
});

test('ocurrenciasEnRango: una tx asignada a una fecha que la regla no genera queda fueraDeRegla', () => {
  const o = ocurrenciasEnRango({
    eventos: [ev({ recurrence: RECURRENCIA_QUINCENAL })],
    txs: [tx({ id: 'a', eventId: 'e1', eventDate: '2026-09-07' })],
    accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04',
  });
  const rara = o.find((x) => x.date === '2026-09-07');
  expect(rara?.fueraDeRegla).toBe(true);
  expect(rara?.estado).toBe('hecha');
  expect(o.filter((x) => x.fueraDeRegla)).toHaveLength(1);
});

test('totalesEsperados: solo pendientes/vencidas con monto, por moneda y sin conversión', () => {
  const eventos = [
    ev({ id: 'e1', titulo: 'Quincena', accountId: 'acc-bnc', amountMinor: 800000, recurrence: RECURRENCIA_QUINCENAL }),
    ev({ id: 'e2', titulo: 'Netflix', categoryId: 'cat-suscripciones', accountId: 'acc-zinli', amountMinor: 1599, startDate: '2026-01-05', recurrence: { kind: 'monthly', days: [5] } }),
    ev({ id: 'e3', titulo: 'Variable', categoryId: 'cat-suscripciones', startDate: '2026-09-10', recurrence: { kind: 'once' } }),
  ];
  const o = ocurrenciasEnRango({ eventos, txs: [], accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04' });
  const t = totalesEsperados(o, CUENTAS, CATEGORIAS);
  expect(t.cobrar).toEqual([{ currency: 'VES', totalMinor: 1600000 }]); // dos quincenas de Bs 8.000,00
  expect(t.pagar).toEqual([{ currency: 'USD', totalMinor: 1599 }]);     // Netflix; el variable no suma
  const conHecha = ocurrenciasEnRango({
    eventos, txs: [tx({ id: 'a', eventId: 'e1', eventDate: '2026-09-15' })],
    accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04',
  });
  expect(totalesEsperados(conHecha, CUENTAS, CATEGORIAS).cobrar).toEqual([{ currency: 'VES', totalMinor: 800000 }]);
});

test('esQuincenal y descripcionRecurrencia', () => {
  expect(esQuincenal(RECURRENCIA_QUINCENAL)).toBe(true);
  expect(esQuincenal({ kind: 'monthly', days: [15] })).toBe(false);
  expect(esQuincenal({ kind: 'weekly' })).toBe(false);
  expect(descripcionRecurrencia(ev({ recurrence: RECURRENCIA_QUINCENAL }))).toBe('quincenal (15 y último)');
  expect(descripcionRecurrencia(ev({ recurrence: { kind: 'monthly', days: [15, 20, 31] } }))).toBe('mensual: 15, 20 y último');
  expect(descripcionRecurrencia(ev({ recurrence: { kind: 'monthly', days: [5] } }))).toBe('mensual: 5');
  expect(descripcionRecurrencia(ev({ startDate: '2026-09-04', recurrence: { kind: 'weekly' } }))).toBe('semanal: viernes');
  expect(descripcionRecurrencia(ev({ startDate: '2026-03-03', recurrence: { kind: 'yearly' } }))).toBe('anual: 3 mar');
  expect(descripcionRecurrencia(ev({ startDate: '2026-10-20', recurrence: { kind: 'once' } }))).toBe('una vez: 20/10/2026');
  expect(descripcionRecurrencia(ev({ recurrence: { kind: 'weekly' }, startDate: '2026-09-04', endDate: '2026-12-31' })))
    .toBe('semanal: viernes · hasta 31/12/2026');
});
```

- [ ] **Paso 2: Correr → RED.** `npx vitest run src/domain/calendar.test.ts`. Expected: FAIL (el módulo no existe).

- [ ] **Paso 3: Implementación.** Crear `src/domain/calendar.ts` con el contenido COMPLETO:

```ts
import type {
  Account, CalEvent, Category, CategoryKind, Currency, Recurrence, Transaction,
} from './types';
import {
  addDays, daysInMonth, ddmmyyyy, diffDias, esFechaIso, isoFromParts, mesSiguiente,
  monthOf, nombreDia, partsOf, MES_CORTO,
} from './dates';

export type EstadoOcurrencia = 'pendiente' | 'hecha' | 'vencida' | 'omitida';

/** Clave de una ocurrencia: el evento + su fecha NOMINAL (no la del movimiento). */
export interface Ocurrencia { eventId: string; date: string }

export interface MontoMoneda { currency: Currency; totalMinor: number }

export interface OcurrenciaVista extends Ocurrencia {
  evento: CalEvent;
  estado: EstadoOcurrencia;
  /** Movimientos asignados a esta ocurrencia (pueden ser varios). */
  txIds: string[];
  /** Σ amountMinor de las txs asignadas, por moneda de la cuenta de cada tx. Sin conversión. */
  asignadoPorMoneda: MontoMoneda[];
  /** true si la regla actual del evento NO genera esa fecha (asignación «rara» o regla editada). */
  fueraDeRegla: boolean;
}

/** Preset «Quincenal (15 y último)»: 31 se recorta al último día de cada mes. */
export const RECURRENCIA_QUINCENAL: Recurrence = { kind: 'monthly', days: [15, 31] };

export function esQuincenal(r: Recurrence): boolean {
  return r.kind === 'monthly' && r.days.length === 2 && r.days[0] === 15 && r.days[1] === 31;
}

/** '15, 31' → [15, 31]. Ordena y deduplica. Lanza si está vacío o hay algo fuera de 1..31. */
export function parseDias(texto: string): number[] {
  const partes = texto.split(/[\s,;]+/).filter((s) => s !== '');
  const dias = new Set<number>();
  for (const p of partes) {
    if (!/^\d{1,2}$/.test(p)) throw new Error('Días del mes inválidos');
    const n = Number(p);
    if (n < 1 || n > 31) throw new Error('Días del mes inválidos');
    dias.add(n);
  }
  if (dias.size === 0) throw new Error('Días del mes inválidos');
  return [...dias].sort((a, b) => a - b);
}

/** Lanza Error en español con el primer problema encontrado. */
export function validarEvento(e: CalEvent): void {
  if (!e.titulo.trim()) throw new Error('Ponle nombre al evento');
  if (!e.categoryId) throw new Error('Elige una categoría');
  if (e.amountMinor !== undefined) {
    if (!e.accountId) throw new Error('El monto esperado necesita una cuenta');
    if (!Number.isInteger(e.amountMinor) || e.amountMinor <= 0) throw new Error('Monto inválido');
  }
  if (!esFechaIso(e.startDate)) throw new Error('Fecha inválida');
  if (e.endDate !== undefined) {
    if (!esFechaIso(e.endDate)) throw new Error('Fecha de fin inválida');
    if (e.endDate < e.startDate) throw new Error('El fin no puede ser antes del inicio');
  }
  if (e.recurrence.kind === 'monthly') {
    const d = e.recurrence.days;
    const ok = d.length > 0
      && d.every((n) => Number.isInteger(n) && n >= 1 && n <= 31)
      && d.every((n, i) => i === 0 || n > d[i - 1]);
    if (!ok) throw new Error('Días del mes inválidos');
  }
  for (const o of e.omitidas ?? []) {
    if (!esFechaIso(o)) throw new Error('Fecha omitida inválida');
  }
}

/** Construye un CalEvent válido. Nunca escribe claves con valor undefined. */
export function buildEvent(opts: {
  titulo: string; categoryId: string; accountId?: string; amountMinor?: number;
  startDate: string; endDate?: string; recurrence: Recurrence; omitidas?: string[]; nowIso: string;
}): CalEvent {
  const e: CalEvent = {
    id: crypto.randomUUID(),
    titulo: opts.titulo.trim(),
    categoryId: opts.categoryId,
    startDate: opts.startDate,
    recurrence: opts.recurrence,
    createdIso: opts.nowIso,
  };
  if (opts.accountId) e.accountId = opts.accountId;
  if (opts.amountMinor !== undefined) e.amountMinor = opts.amountMinor;
  if (opts.endDate) e.endDate = opts.endDate;
  if (opts.omitidas && opts.omitidas.length > 0) e.omitidas = [...opts.omitidas];
  validarEvento(e);
  return e;
}

/** Fechas NOMINALES del evento dentro de [desde, hasta] (inclusive), ascendentes y sin repetidos.
 *  Coste O(meses o semanas del rango). NUNCA expande sin `hasta`. */
export function fechasDeEvento(e: CalEvent, desde: string, hasta: string): string[] {
  if (e.recurrence.kind === 'once') {
    return desde <= e.startDate && e.startDate <= hasta ? [e.startDate] : [];
  }
  const desdeEf = desde > e.startDate ? desde : e.startDate;
  const hastaEf = e.endDate !== undefined && e.endDate < hasta ? e.endDate : hasta;
  if (desdeEf > hastaEf) return [];

  if (e.recurrence.kind === 'weekly') {
    const salto = Math.ceil(diffDias(e.startDate, desdeEf) / 7);
    const out: string[] = [];
    for (let f = addDays(e.startDate, salto * 7); f <= hastaEf; f = addDays(f, 7)) out.push(f);
    return out;
  }

  const out = new Set<string>();
  if (e.recurrence.kind === 'yearly') {
    const { m, d } = partsOf(e.startDate);
    for (let y = partsOf(desdeEf).y; y <= partsOf(hastaEf).y; y++) {
      const f = isoFromParts(y, m, Math.min(d, daysInMonth(y, m)));
      if (f >= desdeEf && f <= hastaEf) out.add(f);
    }
    return [...out].sort();
  }

  const dias = e.recurrence.days;
  for (let mes = monthOf(desdeEf); mes <= monthOf(hastaEf); mes = mesSiguiente(mes)) {
    const [y, m] = mes.split('-').map(Number);
    for (const dia of dias) {
      const f = isoFromParts(y, m, Math.min(dia, daysInMonth(y, m)));
      if (f >= desdeEf && f <= hastaEf) out.add(f);
    }
  }
  return [...out].sort();
}

/** El kind de la categoría del evento define pago (expense) o cobro (income). null si no existe. */
export function kindDeEvento(e: CalEvent, categories: Category[]): CategoryKind | null {
  return categories.find((c) => c.id === e.categoryId)?.kind ?? null;
}

/** Precedencia: hecha > omitida > vencida > pendiente. «Hoy» cuenta como pendiente. */
export function estadoDe(o: { date: string; omitida: boolean; txIds: string[] }, hoy: string): EstadoOcurrencia {
  if (o.txIds.length > 0) return 'hecha';
  if (o.omitida) return 'omitida';
  if (o.date < hoy) return 'vencida';
  return 'pendiente';
}

/** UNIÓN de las fechas generadas por cada evento y de las asignaciones existentes en el rango.
 *  Las ocurrencias NO se persisten: esta función las deriva en cada render. */
export function ocurrenciasEnRango(opts: {
  eventos: CalEvent[]; txs: Transaction[]; accounts: Account[];
  desde: string; hasta: string; hoy: string;
}): OcurrenciaVista[] {
  const eventoDe = new Map(opts.eventos.map((e) => [e.id, e]));
  const monedaDe = new Map(opts.accounts.map((a) => [a.id, a.currency]));
  const generadas = new Set<string>();
  const pares = new Map<string, { evento: CalEvent; date: string }>();

  for (const e of opts.eventos) {
    for (const f of fechasDeEvento(e, opts.desde, opts.hasta)) {
      const k = `${e.id}|${f}`;
      generadas.add(k);
      pares.set(k, { evento: e, date: f });
    }
  }

  const txsPorPar = new Map<string, Transaction[]>();
  for (const t of opts.txs) {
    if (!t.eventId || !t.eventDate) continue;
    if (t.eventDate < opts.desde || t.eventDate > opts.hasta) continue;
    const evento = eventoDe.get(t.eventId);
    if (!evento) continue; // asignación huérfana: se ignora
    const k = `${t.eventId}|${t.eventDate}`;
    if (!pares.has(k)) pares.set(k, { evento, date: t.eventDate });
    const lista = txsPorPar.get(k) ?? [];
    lista.push(t);
    txsPorPar.set(k, lista);
  }

  const vistas: OcurrenciaVista[] = [];
  for (const [k, { evento, date }] of pares) {
    const asignadas = txsPorPar.get(k) ?? [];
    const txIds = asignadas.map((t) => t.id);
    const porMoneda = new Map<Currency, number>();
    for (const t of asignadas) {
      const moneda = monedaDe.get(t.accountId) ?? 'USD';
      porMoneda.set(moneda, (porMoneda.get(moneda) ?? 0) + t.amountMinor);
    }
    vistas.push({
      eventId: evento.id,
      date,
      evento,
      estado: estadoDe({ date, omitida: (evento.omitidas ?? []).includes(date), txIds }, opts.hoy),
      txIds,
      asignadoPorMoneda: [...porMoneda.entries()].map(([currency, totalMinor]) => ({ currency, totalMinor })),
      fueraDeRegla: !generadas.has(k),
    });
  }
  return vistas.sort((a, b) =>
    a.date.localeCompare(b.date) || a.evento.titulo.localeCompare(b.evento.titulo));
}

/** Lo que falta pagar/cobrar: solo pendientes y vencidas CON monto esperado.
 *  Por moneda de la cuenta del evento; SIN conversión (igual que las deudas del spec v2). */
export function totalesEsperados(
  ocurrencias: OcurrenciaVista[], accounts: Account[], categories: Category[],
): { pagar: MontoMoneda[]; cobrar: MontoMoneda[] } {
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const pagar = new Map<Currency, number>();
  const cobrar = new Map<Currency, number>();
  for (const o of ocurrencias) {
    if (o.estado !== 'pendiente' && o.estado !== 'vencida') continue;
    const { amountMinor, accountId } = o.evento;
    if (amountMinor === undefined || !accountId) continue;
    const kind = kindDeEvento(o.evento, categories);
    if (kind === null) continue;
    const moneda = monedaDe.get(accountId) ?? 'USD';
    const destino = kind === 'expense' ? pagar : cobrar;
    destino.set(moneda, (destino.get(moneda) ?? 0) + amountMinor);
  }
  const lista = (m: Map<Currency, number>): MontoMoneda[] =>
    [...m.entries()].map(([currency, totalMinor]) => ({ currency, totalMinor }));
  return { pagar: lista(pagar), cobrar: lista(cobrar) };
}

/** Texto humano de la regla, en español y sin Intl. */
export function descripcionRecurrencia(e: CalEvent): string {
  const fin = e.endDate ? ` · hasta ${ddmmyyyy(e.endDate)}` : '';
  const r = e.recurrence;
  if (r.kind === 'once') return `una vez: ${ddmmyyyy(e.startDate)}`;
  if (r.kind === 'weekly') return `semanal: ${nombreDia(e.startDate)}${fin}`;
  if (r.kind === 'yearly') {
    const { m, d } = partsOf(e.startDate);
    return `anual: ${d} ${MES_CORTO[m - 1]}${fin}`;
  }
  if (esQuincenal(r)) return `quincenal (15 y último)${fin}`;
  const dias = r.days.map((n) => (n === 31 ? 'último' : String(n)));
  const texto = dias.length === 1 ? dias[0] : `${dias.slice(0, -1).join(', ')} y ${dias[dias.length - 1]}`;
  return `mensual: ${texto}${fin}`;
}
```

Nota: `once` NUNCA muestra el sufijo « · hasta …» porque `fechasDeEvento` ignora `endDate` en ese caso (el editor de T11 además borra `endDate` al pasar a «Una vez»).

- [ ] **Paso 4: Correr → verde.** `npx vitest run src/domain/calendar.test.ts` y luego `npx vitest run`.

- [ ] **Paso 5: Commit.**

```bash
git add src/domain/calendar.ts src/domain/calendar.test.ts
git commit -m "feat(calendario): expansión de recurrencias y estado de ocurrencias derivadas" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 7: `src/domain/eventMatch.ts` (candidatas, sugerencia, asignación)

**Files:**
- Create: `src/domain/eventMatch.ts`
- Create: `src/domain/eventMatch.test.ts`

**Interfaces:**
- Consumes de T6: `OcurrenciaVista`, `Ocurrencia`, `ocurrenciasEnRango`, `kindDeEvento`. De T2: `addDays`, `diffDias`.
- Produces:

```ts
export interface TxParaSugerir { type: 'income' | 'expense'; date: string; accountId: string; amountMinor: number }
export interface CtxSugerencia { eventos: CalEvent[]; txs: Transaction[]; accounts: Account[]; categories: Category[]; hoy: string }
export const VENTANA_ATRAS_DIAS = 31;
export const VENTANA_ADELANTE_DIAS = 7;
export const TOLERANCIA_MONTO = 0.10;
export interface Candidata { ocurrencia: OcurrenciaVista; distanciaDias: number; montoCoincide: boolean | null; sugerible: boolean }
export function candidatasParaTx(tx: TxParaSugerir, ctx: CtxSugerencia, opts?: { incluirHechas?: boolean }): Candidata[];
export function sugerirOcurrencia(tx: TxParaSugerir, ctx: CtxSugerencia): OcurrenciaVista | null;
export function sugerirParaLote(txs: Transaction[], ctx: CtxSugerencia): Map<string, Ocurrencia>;
export function aplicarAsignacion(tx: Transaction, o: Ocurrencia | null, evento: CalEvent | null): Transaction;
export function validarAsignacion(tx: Pick<Transaction, 'type'>, evento: CalEvent, categories: Category[]): void;
```

- [ ] **Paso 1: Tests RED.** Crear `src/domain/eventMatch.test.ts` con el contenido COMPLETO:

```ts
import {
  aplicarAsignacion, candidatasParaTx, sugerirOcurrencia, sugerirParaLote, validarAsignacion,
} from './eventMatch';
import { RECURRENCIA_QUINCENAL } from './calendar';
import type { Account, CalEvent, Category, Transaction } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-zinli', name: 'Zinli', currency: 'USD', kind: 'wallet', isSavings: false, archived: false },
];
const CATEGORIAS: Category[] = [
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#22d3ee', kind: 'expense' },
];
const QUINCENA: CalEvent = {
  id: 'e-quincena', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
  amountMinor: 800000, startDate: '2026-01-01', recurrence: RECURRENCIA_QUINCENAL,
  createdIso: '2026-09-04T10:00:00.000Z',
};
const ctxCon = (eventos: CalEvent[], txs: Transaction[] = []) => ({
  eventos, txs, accounts: CUENTAS, categories: CATEGORIAS, hoy: '2026-09-04',
});
const tx = (p: Partial<Transaction>): Transaction => ({
  id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000,
  date: '2026-09-15', source: 'manual', ...p,
});

test('elegibilidad: kind, cuenta y ventana [−31, +7] con bordes inclusivos', () => {
  const ctx = ctxCon([QUINCENA]);
  const para = (date: string) => candidatasParaTx({ type: 'income', date, accountId: 'acc-bnc', amountMinor: 800000 }, ctx);
  expect(para('2026-09-15').map((c) => c.ocurrencia.date)).toContain('2026-09-15');
  expect(para('2026-10-16').map((c) => c.ocurrencia.date)).toContain('2026-09-15'); // 31 días después
  expect(para('2026-10-17').map((c) => c.ocurrencia.date)).not.toContain('2026-09-15');
  expect(para('2026-09-08').map((c) => c.ocurrencia.date)).toContain('2026-09-15'); // 7 días antes
  expect(para('2026-09-07').map((c) => c.ocurrencia.date)).not.toContain('2026-09-15');
  // kind contrario: un gasto no ve un evento de cobro
  expect(candidatasParaTx({ type: 'expense', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 }, ctx)).toEqual([]);
  // cuenta distinta a la esperada por el evento
  expect(candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-zinli', amountMinor: 800000 }, ctx)).toEqual([]);
  // evento sin accountId: cualquier cuenta sirve
  const libre = { ...QUINCENA, id: 'e-libre', accountId: undefined, amountMinor: undefined };
  expect(candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-zinli', amountMinor: 1 }, ctxCon([libre])))
    .toHaveLength(3); // ventana [15 ago, 22 sep]: 15 ago, 31 ago y 15 sep (el 30 sep queda fuera)
});

test('omitidas se excluyen; hechas solo con incluirHechas', () => {
  const omitida = { ...QUINCENA, omitidas: ['2026-09-15'] };
  const t = { type: 'income' as const, date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 };
  expect(candidatasParaTx(t, ctxCon([omitida])).map((c) => c.ocurrencia.date)).toEqual(['2026-08-31', '2026-08-15']);
  const hecha = ctxCon([QUINCENA], [tx({ id: 'ya', eventId: 'e-quincena', eventDate: '2026-09-15' })]);
  expect(candidatasParaTx(t, hecha).map((c) => c.ocurrencia.date)).toEqual(['2026-08-31', '2026-08-15']);
  const conHechas = candidatasParaTx(t, hecha, { incluirHechas: true });
  expect(conHechas.map((c) => c.ocurrencia.date).sort()).toEqual(['2026-08-15', '2026-08-31', '2026-09-15']);
  expect(conHechas.find((c) => c.ocurrencia.date === '2026-09-15')?.sugerible).toBe(false);
});

test('montoCoincide: ±10 %, null sin monto esperado y null con amountMinor ≤ 0', () => {
  const ctx = ctxCon([QUINCENA]);
  const con = (amountMinor: number) =>
    candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor }, ctx)
      .find((c) => c.ocurrencia.date === '2026-09-15');
  expect(con(800000)?.montoCoincide).toBe(true);
  expect(con(880000)?.montoCoincide).toBe(true);   // +10 % exacto
  expect(con(720000)?.montoCoincide).toBe(true);   // −10 % exacto
  expect(con(880001)?.montoCoincide).toBe(false);
  expect(con(500000)?.montoCoincide).toBe(false);
  expect(con(500000)?.sugerible).toBe(false);
  expect(con(0)?.montoCoincide).toBeNull();
  expect(con(0)?.sugerible).toBe(true);
  const sinMonto = { ...QUINCENA, amountMinor: undefined };
  const c = candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 1 }, ctxCon([sinMonto]));
  expect(c[0].montoCoincide).toBeNull();
  expect(c[0].sugerible).toBe(true);
});

test('orden: sugeribles primero, luego distancia, luego monto que coincide, luego título', () => {
  const netflix: CalEvent = {
    id: 'e-netflix', titulo: 'Netflix', categoryId: 'cat-suscripciones', startDate: '2026-01-05',
    recurrence: { kind: 'monthly', days: [5] }, createdIso: 'x',
  };
  const spotify: CalEvent = { ...netflix, id: 'e-spotify', titulo: 'Spotify' };
  const c = candidatasParaTx(
    { type: 'expense', date: '2026-09-05', accountId: 'acc-zinli', amountMinor: 1000 },
    ctxCon([spotify, netflix]),
  );
  expect(c.map((x) => x.ocurrencia.evento.titulo)).toEqual(['Netflix', 'Spotify', 'Netflix', 'Spotify']); // 2 ocurrencias por evento (5 ago y 5 sep); empate → título asc
  expect(c.every((x) => x.sugerible)).toBe(true);
});

test('sugerirOcurrencia devuelve la primera sugerible, o null si la única candidata no lo es', () => {
  const ctx = ctxCon([QUINCENA]);
  expect(sugerirOcurrencia({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 }, ctx)?.date)
    .toBe('2026-09-15');
  // monto muy lejos del esperado en TODAS las candidatas → sin sugerencia
  expect(sugerirOcurrencia({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 5000 }, ctx)).toBeNull();
  expect(sugerirOcurrencia({ type: 'expense', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 }, ctx)).toBeNull();
});

test('sugerirParaLote: greedy global, una ocurrencia por tx y una tx por ocurrencia', () => {
  const lote = [
    tx({ id: 'amigo', date: '2026-09-14', amountMinor: 5000 }),      // no coincide con Bs 8.000,00
    tx({ id: 'sueldo', date: '2026-09-15', amountMinor: 800000 }),   // coincide
    tx({ id: 'otro', date: '2026-09-16', amountMinor: 790000 }),     // también coincide (±10 %)
  ];
  const m = sugerirParaLote(lote, ctxCon([QUINCENA]));
  expect(m.get('sueldo')).toEqual({ eventId: 'e-quincena', date: '2026-09-15' });
  expect(m.has('amigo')).toBe(false);                                 // el monto lo descarta
  expect(m.get('otro')).toEqual({ eventId: 'e-quincena', date: '2026-08-31' }); // la del 15 ya está ocupada y el 30 sep cae fuera de la ventana
  expect(m.size).toBe(2);
});

test('sugerirParaLote: la fila que coincide en monto gana aunque esté más lejos', () => {
  const lote = [
    tx({ id: 'cerca', date: '2026-09-14', amountMinor: 100000 }),   // 1 día, monto NO coincide → no sugerible
    tx({ id: 'lejos', date: '2026-09-20', amountMinor: 800000 }),   // 5 días, monto coincide
  ];
  const m = sugerirParaLote(lote, ctxCon([QUINCENA]));
  expect(m.get('lejos')).toEqual({ eventId: 'e-quincena', date: '2026-09-15' });
  expect(m.has('cerca')).toBe(false);
});

test('sugerirParaLote: la ocurrencia más cercana gana y las transferencias se ignoran', () => {
  const sinMonto = { ...QUINCENA, amountMinor: undefined };
  const lote = [
    tx({ id: 'q15', date: '2026-09-15' }),
    tx({ id: 'q30', date: '2026-09-30' }),
    { id: 'tr', type: 'transfer' as const, accountId: 'acc-bnc', toAccountId: 'acc-zinli',
      amountMinor: 100, toAmountMinor: 1, date: '2026-09-15', source: 'manual' as const },
  ];
  const m = sugerirParaLote(lote, ctxCon([sinMonto]));
  expect(m.get('q15')).toEqual({ eventId: 'e-quincena', date: '2026-09-15' });
  expect(m.get('q30')).toEqual({ eventId: 'e-quincena', date: '2026-09-30' });
  expect(m.has('tr')).toBe(false);
  // determinista: el mismo lote da el mismo resultado
  expect([...sugerirParaLote(lote, ctxCon([sinMonto])).entries()]).toEqual([...m.entries()]);
});

test('aplicarAsignacion no muta, copia la categoría del evento y al desasignar la conserva', () => {
  const original = tx({ categoryId: 'cat-otros-ingresos' });
  const asignada = aplicarAsignacion(original, { eventId: 'e-quincena', date: '2026-09-15' }, QUINCENA);
  expect(asignada).toMatchObject({ eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo' });
  expect(original.categoryId).toBe('cat-otros-ingresos'); // no mutó
  expect('eventId' in original).toBe(false);
  expect(asignada.amountMinor).toBe(original.amountMinor);
  expect(asignada.date).toBe(original.date);
  expect(asignada.accountId).toBe(original.accountId);
  expect(asignada.type).toBe(original.type);
  const suelta = aplicarAsignacion(asignada, null, null);
  expect('eventId' in suelta).toBe(false);
  expect('eventDate' in suelta).toBe(false);
  expect(suelta.categoryId).toBe('cat-sueldo'); // desasignar CONSERVA la categoría
});

test('validarAsignacion: mensajes por dirección incompatible y categoría borrada', () => {
  expect(() => validarAsignacion({ type: 'income' }, QUINCENA, CATEGORIAS)).not.toThrow();
  expect(() => validarAsignacion({ type: 'expense' }, QUINCENA, CATEGORIAS))
    .toThrow('Este movimiento es un gasto y el evento es un cobro');
  const netflix: CalEvent = { ...QUINCENA, id: 'e2', categoryId: 'cat-suscripciones' };
  expect(() => validarAsignacion({ type: 'income' }, netflix, CATEGORIAS))
    .toThrow('Este movimiento es un ingreso y el evento es un pago');
  expect(() => validarAsignacion({ type: 'income' }, { ...QUINCENA, categoryId: 'cat-borrada' }, CATEGORIAS))
    .toThrow('La categoría del evento no existe');
});
```

- [ ] **Paso 2: Correr → RED.** `npx vitest run src/domain/eventMatch.test.ts`.

- [ ] **Paso 3: Implementación.** Crear `src/domain/eventMatch.ts` con el contenido COMPLETO:

```ts
import type { Account, CalEvent, Category, Transaction } from './types';
import { kindDeEvento, ocurrenciasEnRango, type Ocurrencia, type OcurrenciaVista } from './calendar';
import { addDays, diffDias } from './dates';

export interface TxParaSugerir {
  type: 'income' | 'expense';
  date: string;
  accountId: string;
  amountMinor: number;
}

export interface CtxSugerencia {
  eventos: CalEvent[];
  txs: Transaction[];
  accounts: Account[];
  categories: Category[];
  hoy: string;
}

/** Ventana asimétrica: la ocurrencia puede ser hasta 31 días ANTES del movimiento (pago
 *  atrasado) y hasta 7 días DESPUÉS (pago adelantado). Decisión 12 del spec v3. */
export const VENTANA_ATRAS_DIAS = 31;
export const VENTANA_ADELANTE_DIAS = 7;
export const TOLERANCIA_MONTO = 0.10;

export interface Candidata {
  ocurrencia: OcurrenciaVista;
  /** |diffDias(ocurrencia.date, tx.date)| */
  distanciaDias: number;
  /** null = el evento no tiene monto esperado, o la tx aún no tiene monto (amountMinor ≤ 0). */
  montoCoincide: boolean | null;
  sugerible: boolean;
}

export function candidatasParaTx(
  tx: TxParaSugerir, ctx: CtxSugerencia, opts?: { incluirHechas?: boolean },
): Candidata[] {
  const desde = addDays(tx.date, -VENTANA_ATRAS_DIAS);
  const hasta = addDays(tx.date, VENTANA_ADELANTE_DIAS);
  const elegibles = ctx.eventos.filter((e) =>
    kindDeEvento(e, ctx.categories) === tx.type
    && (e.accountId === undefined || e.accountId === tx.accountId));
  const ocurrencias = ocurrenciasEnRango({
    eventos: elegibles, txs: ctx.txs, accounts: ctx.accounts, desde, hasta, hoy: ctx.hoy,
  });

  const out: Candidata[] = [];
  for (const o of ocurrencias) {
    if (o.estado === 'omitida') continue;
    if (o.estado === 'hecha' && !opts?.incluirHechas) continue;
    const esperado = o.evento.amountMinor;
    const montoCoincide = esperado === undefined || tx.amountMinor <= 0
      ? null
      : Math.abs(tx.amountMinor - esperado) <= TOLERANCIA_MONTO * esperado;
    out.push({
      ocurrencia: o,
      distanciaDias: Math.abs(diffDias(o.date, tx.date)),
      montoCoincide,
      sugerible: (o.estado === 'pendiente' || o.estado === 'vencida') && montoCoincide !== false,
    });
  }
  return out.sort((a, b) =>
    Number(b.sugerible) - Number(a.sugerible)
    || a.distanciaDias - b.distanciaDias
    || Number(b.montoCoincide === true) - Number(a.montoCoincide === true)
    || a.ocurrencia.evento.titulo.localeCompare(b.ocurrencia.evento.titulo));
}

/** Primera candidata sugerible, o null. Es la que Nuevo marca con `.sugerido`. */
export function sugerirOcurrencia(tx: TxParaSugerir, ctx: CtxSugerencia): OcurrenciaVista | null {
  const candidatas = candidatasParaTx(tx, ctx);
  return candidatas.length > 0 && candidatas[0].sugerible ? candidatas[0].ocurrencia : null;
}

/** Emparejamiento greedy GLOBAL para la vista previa de los imports: cada ocurrencia recibe
 *  a lo sumo una tx y cada tx a lo sumo una ocurrencia. Determinista. */
export function sugerirParaLote(txs: Transaction[], ctx: CtxSugerencia): Map<string, Ocurrencia> {
  interface Par {
    txId: string; eventId: string; date: string; titulo: string;
    distanciaDias: number; deltaMonto: number; indice: number;
  }
  const pares: Par[] = [];
  txs.forEach((t, indice) => {
    if (t.type !== 'income' && t.type !== 'expense') return;
    const candidatas = candidatasParaTx(
      { type: t.type, date: t.date, accountId: t.accountId, amountMinor: t.amountMinor }, ctx,
    );
    for (const c of candidatas) {
      if (!c.sugerible) continue;
      const esperado = c.ocurrencia.evento.amountMinor;
      pares.push({
        txId: t.id,
        eventId: c.ocurrencia.eventId,
        date: c.ocurrencia.date,
        titulo: c.ocurrencia.evento.titulo,
        distanciaDias: c.distanciaDias,
        deltaMonto: esperado === undefined ? Number.MAX_SAFE_INTEGER : Math.abs(t.amountMinor - esperado),
        indice,
      });
    }
  });
  pares.sort((a, b) =>
    a.distanciaDias - b.distanciaDias
    || a.deltaMonto - b.deltaMonto
    || a.titulo.localeCompare(b.titulo)
    || a.indice - b.indice);

  const asignadas = new Map<string, Ocurrencia>();
  const ocupadas = new Set<string>();
  for (const p of pares) {
    const clave = `${p.eventId}|${p.date}`;
    if (asignadas.has(p.txId) || ocupadas.has(clave)) continue;
    asignadas.set(p.txId, { eventId: p.eventId, date: p.date });
    ocupadas.add(clave);
  }
  return asignadas;
}

/** Devuelve una COPIA. Asignar copia la categoría del evento; desasignar la conserva.
 *  Jamás toca amountMinor, date, accountId ni type. */
export function aplicarAsignacion(tx: Transaction, o: Ocurrencia | null, evento: CalEvent | null): Transaction {
  if (o === null || evento === null) {
    const { eventId: _e, eventDate: _f, ...resto } = tx;
    return resto as Transaction;
  }
  return { ...tx, eventId: o.eventId, eventDate: o.date, categoryId: evento.categoryId };
}

/** Lanza Error en español si el movimiento y el evento van en direcciones contrarias. */
export function validarAsignacion(
  tx: Pick<Transaction, 'type'>, evento: CalEvent, categories: Category[],
): void {
  const kind = kindDeEvento(evento, categories);
  if (kind === null) throw new Error('La categoría del evento no existe');
  if (tx.type === 'expense' && kind === 'income') {
    throw new Error('Este movimiento es un gasto y el evento es un cobro');
  }
  if (tx.type === 'income' && kind === 'expense') {
    throw new Error('Este movimiento es un ingreso y el evento es un pago');
  }
}
```

- [ ] **Paso 4: Correr → verde.** `npx vitest run src/domain/eventMatch.test.ts` y luego `npx vitest run`.

- [ ] **Paso 5: Commit.**

```bash
git add src/domain/eventMatch.ts src/domain/eventMatch.test.ts
git commit -m "feat(eventos): candidatas, sugerencia por lote y asignación pura" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 8: `src/db/eventOps.ts` (operaciones transaccionales)

**Files:**
- Create: `src/db/eventOps.ts`
- Create: `src/db/eventOps.test.ts`

**Interfaces:**
- Consumes: `db.calEvents` (T1), `validarEvento`/`kindDeEvento` (T6), `validarAsignacion` (T7), `esFechaIso` (T2), `Ocurrencia` (T6).
- Produces (todas reciben `d: CuentasDB`, todo-o-nada, mensajes en español):

```ts
export async function guardarEvento(d: CuentasDB, e: CalEvent): Promise<void>;
export async function eliminarEvento(d: CuentasDB, eventId: string): Promise<number>;   // nº de txs desasignadas
export async function asignarEvento(d: CuentasDB, txId: string, o: Ocurrencia): Promise<void>;
export async function desasignarEvento(d: CuentasDB, txId: string): Promise<void>;      // idempotente
export async function omitirOcurrencia(d: CuentasDB, eventId: string, date: string): Promise<void>;
export async function reanudarOcurrencia(d: CuentasDB, eventId: string, date: string): Promise<void>;
```

- [ ] **Paso 1: Tests RED.** Crear `src/db/eventOps.test.ts` con el contenido COMPLETO:

```ts
import { CuentasDB, seedIfEmpty } from './db';
import {
  asignarEvento, desasignarEvento, eliminarEvento, guardarEvento, omitirOcurrencia, reanudarOcurrencia,
} from './eventOps';
import { RECURRENCIA_QUINCENAL } from '../domain/calendar';
import type { CalEvent, Transaction } from '../domain/types';

const QUINCENA: CalEvent = {
  id: 'e-quincena', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
  amountMinor: 800000, startDate: '2026-01-01', recurrence: RECURRENCIA_QUINCENAL,
  createdIso: '2026-09-04T10:00:00.000Z',
};

async function base() {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  return d;
}

const tx = (p: Partial<Transaction>): Transaction => ({
  id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000,
  date: '2026-09-15', source: 'manual', categoryId: 'cat-otros-ingresos', ...p,
});

test('guardarEvento valida el evento y exige que la categoría exista', async () => {
  const d = await base();
  await expect(guardarEvento(d, { ...QUINCENA, titulo: ' ' })).rejects.toThrow('Ponle nombre al evento');
  await expect(guardarEvento(d, { ...QUINCENA, categoryId: 'cat-inventada' })).rejects.toThrow('La categoría no existe');
  expect(await d.calEvents.count()).toBe(0);
  await guardarEvento(d, QUINCENA);
  expect((await d.calEvents.get('e-quincena'))?.titulo).toBe('Quincena');
});

test('guardarEvento hace put del objeto COMPLETO: conserva omitidas', async () => {
  const d = await base();
  await guardarEvento(d, { ...QUINCENA, omitidas: ['2026-08-31'] });
  await guardarEvento(d, { ...QUINCENA, titulo: 'Quincena BNC', omitidas: ['2026-08-31'] });
  expect(await d.calEvents.get('e-quincena')).toMatchObject({ titulo: 'Quincena BNC', omitidas: ['2026-08-31'] });
});

test('guardarEvento rechaza cambiar a una categoría de kind contrario si hay txs asignadas', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15' }));
  await expect(guardarEvento(d, { ...QUINCENA, categoryId: 'cat-suscripciones' }))
    .rejects.toThrow('Desasigna primero los 1 movimientos');
  expect((await d.calEvents.get('e-quincena'))?.categoryId).toBe('cat-sueldo'); // nada a medias
});

test('eliminarEvento devuelve las desasignadas, borra el evento y NUNCA borra movimientos', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo' }));
  await d.transactions.add(tx({ id: 'b', eventId: 'e-quincena', eventDate: '2026-09-30', categoryId: 'cat-sueldo' }));
  await d.transactions.add(tx({ id: 'c' }));
  expect(await eliminarEvento(d, 'e-quincena')).toBe(2);
  expect(await d.calEvents.count()).toBe(0);
  expect(await d.transactions.count()).toBe(3);
  const a = await d.transactions.get('a');
  expect(a && 'eventId' in a).toBe(false);
  expect(a && 'eventDate' in a).toBe(false);
  expect(a?.categoryId).toBe('cat-sueldo');     // conserva categoría
  expect(a?.amountMinor).toBe(800000);
  expect(await eliminarEvento(d, 'e-quincena')).toBe(0); // idempotente
});

test('asignarEvento fija los 3 campos y valida tipo, evento, fecha y dirección', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a' }));
  await d.transactions.add({
    id: 'tr', type: 'transfer', accountId: 'acc-bnc', toAccountId: 'acc-zinli',
    amountMinor: 100, toAmountMinor: 1, date: '2026-09-15', source: 'manual',
  });
  await d.transactions.add(tx({ id: 'g', type: 'expense', categoryId: 'cat-hogar' }));

  await asignarEvento(d, 'a', { eventId: 'e-quincena', date: '2026-09-15' });
  expect(await d.transactions.get('a')).toMatchObject({
    eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo',
    amountMinor: 800000, date: '2026-09-15', accountId: 'acc-bnc', type: 'income',
  });

  await expect(asignarEvento(d, 'no-existe', { eventId: 'e-quincena', date: '2026-09-15' }))
    .rejects.toThrow('Solo se asignan gastos e ingresos');
  await expect(asignarEvento(d, 'tr', { eventId: 'e-quincena', date: '2026-09-15' }))
    .rejects.toThrow('Solo se asignan gastos e ingresos');
  await expect(asignarEvento(d, 'a', { eventId: 'fantasma', date: '2026-09-15' }))
    .rejects.toThrow('El evento no existe');
  await expect(asignarEvento(d, 'a', { eventId: 'e-quincena', date: '2026-02-30' }))
    .rejects.toThrow('Fecha inválida');
  await expect(asignarEvento(d, 'g', { eventId: 'e-quincena', date: '2026-09-15' }))
    .rejects.toThrow('Este movimiento es un gasto y el evento es un cobro');
  expect((await d.transactions.get('g'))?.categoryId).toBe('cat-hogar'); // no cambió nada
});

test('desasignarEvento borra las CLAVES y es idempotente', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo' }));
  await desasignarEvento(d, 'a');
  const a = await d.transactions.get('a');
  expect(a && 'eventId' in a).toBe(false);
  expect(a && 'eventDate' in a).toBe(false);
  expect(a?.categoryId).toBe('cat-sueldo');
  await expect(desasignarEvento(d, 'a')).resolves.toBeUndefined();
  await expect(desasignarEvento(d, 'no-existe')).resolves.toBeUndefined();
});

test('omitirOcurrencia es idempotente y rechaza si la ocurrencia tiene movimientos', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await omitirOcurrencia(d, 'e-quincena', '2026-09-30');
  await omitirOcurrencia(d, 'e-quincena', '2026-09-30');
  expect((await d.calEvents.get('e-quincena'))?.omitidas).toEqual(['2026-09-30']);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15' }));
  await expect(omitirOcurrencia(d, 'e-quincena', '2026-09-15')).rejects.toThrow('Ya tiene movimientos asignados');
  await expect(omitirOcurrencia(d, 'fantasma', '2026-09-15')).rejects.toThrow('El evento no existe');
  await expect(omitirOcurrencia(d, 'e-quincena', '2026-02-30')).rejects.toThrow('Fecha inválida');
});

test('reanudarOcurrencia quita la fecha y borra la clave si el array queda vacío', async () => {
  const d = await base();
  await guardarEvento(d, { ...QUINCENA, omitidas: ['2026-09-15', '2026-09-30'] });
  await reanudarOcurrencia(d, 'e-quincena', '2026-09-15');
  expect((await d.calEvents.get('e-quincena'))?.omitidas).toEqual(['2026-09-30']);
  await reanudarOcurrencia(d, 'e-quincena', '2026-09-30');
  const e = await d.calEvents.get('e-quincena');
  expect(e && 'omitidas' in e).toBe(false);
  await expect(reanudarOcurrencia(d, 'e-quincena', '2026-09-30')).resolves.toBeUndefined(); // idempotente
  await expect(reanudarOcurrencia(d, 'fantasma', '2026-09-30')).rejects.toThrow('El evento no existe');
});
```

- [ ] **Paso 2: Correr → RED.** `npx vitest run src/db/eventOps.test.ts`.

- [ ] **Paso 3: Implementación.** Crear `src/db/eventOps.ts` con el contenido COMPLETO:

```ts
import { CuentasDB } from './db';
import type { CalEvent } from '../domain/types';
import { kindDeEvento, validarEvento, type Ocurrencia } from '../domain/calendar';
import { validarAsignacion } from '../domain/eventMatch';
import { esFechaIso } from '../domain/dates';

/** Alta o edición del evento (put del objeto COMPLETO: el editor debe traer `omitidas`).
 *  Rechaza cambiar la dirección del evento si eso dejaría movimientos incompatibles. */
export async function guardarEvento(d: CuentasDB, e: CalEvent): Promise<void> {
  validarEvento(e);
  await d.transaction('rw', d.calEvents, d.categories, d.transactions, async () => {
    const kind = kindDeEvento(e, await d.categories.toArray());
    if (kind === null) throw new Error('La categoría no existe');
    const asignadas = await d.transactions.where('eventId').equals(e.id).toArray();
    const incompatibles = asignadas.filter((t) => t.type !== kind).length;
    if (incompatibles > 0) throw new Error(`Desasigna primero los ${incompatibles} movimientos`);
    await d.calEvents.put(e);
  });
}

/** Borra el evento y desasigna sus movimientos en UNA transacción.
 *  Los movimientos NUNCA se borran: conservan categoría, monto, fecha y cuenta. */
export async function eliminarEvento(d: CuentasDB, eventId: string): Promise<number> {
  let desasignadas = 0;
  await d.transaction('rw', d.transactions, d.calEvents, async () => {
    desasignadas = await d.transactions.where('eventId').equals(eventId).modify((t) => {
      delete t.eventId;
      delete t.eventDate;
    });
    await d.calEvents.delete(eventId);
  });
  return desasignadas;
}

/** Asigna la tx a la ocurrencia y copia la categoría del evento. No toca monto/fecha/cuenta/tipo. */
export async function asignarEvento(d: CuentasDB, txId: string, o: Ocurrencia): Promise<void> {
  await d.transaction('rw', d.transactions, d.calEvents, d.categories, async () => {
    const tx = await d.transactions.get(txId);
    if (!tx || (tx.type !== 'income' && tx.type !== 'expense')) {
      throw new Error('Solo se asignan gastos e ingresos');
    }
    const evento = await d.calEvents.get(o.eventId);
    if (!evento) throw new Error('El evento no existe');
    if (!esFechaIso(o.date)) throw new Error('Fecha inválida');
    validarAsignacion(tx, evento, await d.categories.toArray());
    await d.transactions.update(txId, {
      eventId: evento.id, eventDate: o.date, categoryId: evento.categoryId,
    });
  });
}

/** Quita la asignación conservando la categoría. Idempotente. */
export async function desasignarEvento(d: CuentasDB, txId: string): Promise<void> {
  await d.transactions.where('id').equals(txId).modify((t) => {
    delete t.eventId;
    delete t.eventDate;
  });
}

/** Silencia una ocurrencia. Idempotente; rechaza si ya tiene movimientos asignados. */
export async function omitirOcurrencia(d: CuentasDB, eventId: string, date: string): Promise<void> {
  await d.transaction('rw', d.calEvents, d.transactions, async () => {
    const evento = await d.calEvents.get(eventId);
    if (!evento) throw new Error('El evento no existe');
    if (!esFechaIso(date)) throw new Error('Fecha inválida');
    const conMovimientos = await d.transactions.where('eventId').equals(eventId)
      .filter((t) => t.eventDate === date).count();
    if (conMovimientos > 0) throw new Error('Ya tiene movimientos asignados');
    const omitidas = evento.omitidas ?? [];
    if (omitidas.includes(date)) return;
    await d.calEvents.put({ ...evento, omitidas: [...omitidas, date].sort() });
  });
}

/** Reactiva una ocurrencia omitida. Si el array queda vacío, borra la clave. */
export async function reanudarOcurrencia(d: CuentasDB, eventId: string, date: string): Promise<void> {
  await d.transaction('rw', d.calEvents, async () => {
    const evento = await d.calEvents.get(eventId);
    if (!evento) throw new Error('El evento no existe');
    const quedan = (evento.omitidas ?? []).filter((f) => f !== date);
    const actualizado: CalEvent = { ...evento };
    if (quedan.length === 0) delete actualizado.omitidas;
    else actualizado.omitidas = quedan;
    await d.calEvents.put(actualizado);
  });
}
```

- [ ] **Paso 4: Correr → verde, suite completa y build.** `npx vitest run src/db/eventOps.test.ts`, luego `npx vitest run` y `npm run build`.

- [ ] **Paso 5: Commit.**

```bash
git add src/db/eventOps.ts src/db/eventOps.test.ts
git commit -m "feat(eventos): operaciones de base todo-o-nada para eventos y asignaciones" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

## Fase 3 — Oleada de UI (T9, T10, T11 y T12 en paralelo; T13 tras T9)

Recordatorio para TODAS las tareas de esta fase: sin tests automáticos, pero con verificación headless **bloqueante**; guard `guardando` en todo guardado async; ningún hook tras un return condicional; estilos SOLO con las clases que T5 definió; `hoy` inyectado con `todayIso()`; cada implementador levanta su dev server en su puerto y **lo mata al terminar**.

### Tarea 9: `SelectEvento` y Movimientos (categoría de grupo + evento del movimiento)

**Files:**
- Create: `src/ui/componentes/SelectEvento.tsx`
- Modify: `src/ui/paginas/MovimientosPage.tsx` (firma línea 11; live queries líneas 26-29; modelo de vista líneas 131-151; `filaTx` líneas 153-177; `filaGrupo` líneas 179-247; modal de agrupar líneas 287-302; `EditorMovimiento` líneas 309-388; componente nuevo `EditorGrupo` al final)

**Interfaces:**
- Consumes: `actualizarGrupo`, `agruparTxs(d, ids, nota?, categoryId?)`, `categoriaDelResiduo` (T3); `candidatasParaTx`, `CtxSugerencia`, `TxParaSugerir` (T7); `Ocurrencia` (T6); `ddmmyyyy` (T2); clases `.ayuda`, `.fila-ocurrencia` no aplica aquí, `.chip` (v2).
- Produces:
  - `SelectEvento` — **lo consumen T13 (ImportBnc/ImportZinli) y nadie más**:

```tsx
export function SelectEvento({ tx, actual, ctx, ariaLabel, etiqueta, onChange }: {
  tx: TxParaSugerir;
  actual: Ocurrencia | null;
  ctx: CtxSugerencia;
  ariaLabel: string;
  /** Aditivo respecto al spec: si viene, el componente pinta su propio <label> y evita
   *  etiquetas huérfanas cuando no hay candidatas. Los imports lo omiten (van en una fila flex). */
  etiqueta?: string;
  onChange: (o: Ocurrencia | null, evento: CalEvent | null) => void;
}): JSX.Element | null
```
  - `MovimientosPage({ params }: { params?: URLSearchParams })` — prop **opcional**, así que `App.tsx` sigue compilando sin cambios hasta T14.

- [ ] **Paso 1: Crear `src/ui/componentes/SelectEvento.tsx`.** Componente SIN hooks (por eso puede devolver `null` sin violar la regla de hooks). Contenido completo:

```tsx
import type { CalEvent } from '../../domain/types';
import type { Ocurrencia } from '../../domain/calendar';
import { candidatasParaTx, type CtxSugerencia, type TxParaSugerir } from '../../domain/eventMatch';
import { ddmmyyyy } from '../../domain/dates';

interface Opcion { valor: string; etiqueta: string; evento: CalEvent; o: Ocurrencia }

/** Select compartido por el editor de movimientos y la vista previa de los imports.
 *  Si no hay candidatas ni asignación actual devuelve null (no ocupa espacio). */
export function SelectEvento({ tx, actual, ctx, ariaLabel, etiqueta, onChange }: {
  tx: TxParaSugerir;
  actual: Ocurrencia | null;
  ctx: CtxSugerencia;
  ariaLabel: string;
  etiqueta?: string;
  onChange: (o: Ocurrencia | null, evento: CalEvent | null) => void;
}) {
  const diaMes = (iso: string) => ddmmyyyy(iso).slice(0, 5); // '15/09'
  const opciones: Opcion[] = candidatasParaTx(tx, ctx, { incluirHechas: true }).map((c) => ({
    valor: `${c.ocurrencia.eventId}|${c.ocurrencia.date}`,
    etiqueta: `📅 ${c.ocurrencia.evento.titulo} · ${diaMes(c.ocurrencia.date)} · ${c.ocurrencia.estado}`,
    evento: c.ocurrencia.evento,
    o: { eventId: c.ocurrencia.eventId, date: c.ocurrencia.date },
  }));

  const valorActual = actual ? `${actual.eventId}|${actual.date}` : '';
  if (actual && !opciones.some((op) => op.valor === valorActual)) {
    // La asignación vigente SIEMPRE se lista, aunque haya quedado fuera de la ventana.
    const evento = ctx.eventos.find((e) => e.id === actual.eventId);
    if (evento) {
      opciones.unshift({
        valor: valorActual,
        etiqueta: `📅 ${evento.titulo} · ${diaMes(actual.date)} · (asignado)`,
        evento,
        o: actual,
      });
    }
  }
  if (opciones.length === 0) return null;

  return (
    <>
      {etiqueta && <label>{etiqueta}</label>}
      <select
        aria-label={ariaLabel}
        value={valorActual}
        onChange={(e) => {
          const op = opciones.find((x) => x.valor === e.target.value);
          onChange(op ? op.o : null, op ? op.evento : null);
        }}
      >
        <option value="">Sin evento</option>
        {opciones.map((op) => <option key={op.valor} value={op.valor}>{op.etiqueta}</option>)}
      </select>
    </>
  );
}
```

- [ ] **Paso 2: Params iniciales y live query de eventos en `MovimientosPage`.** Cambiar la línea 11 a `export function MovimientosPage({ params }: { params?: URLSearchParams }) {`; la línea 14 a `const [categoriaId, setCategoriaId] = useState(params?.get('cat') ?? '');` y la 15 a `const [mes, setMes] = useState(params?.get('mes') ?? '');`. Añadir al bloque de live queries (tras `grupos`, línea 29):

```tsx
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
```

Ampliar los imports del archivo: `import { todayIso } from '../../domain/dates';`, `import type { Transaction, TxGroup } from '../../domain/types';`, `import { categoriaDelResiduo, residuosDeGrupo, type ResiduoMoneda } from '../../domain/groups';`, `import { actualizarGrupo, agruparTxs, anadirAGrupo, quitarDeGrupo, desagrupar } from '../../db/groupOps';`, `import { SelectEvento } from '../componentes/SelectEvento';`, `import type { Ocurrencia } from '../../domain/calendar';`.

Nota: `params` llega desde `App.tsx` a partir de T14; hasta entonces el default `undefined` mantiene el comportamiento actual.

- [ ] **Paso 3: Drill-down de grupos por categoría de residuo.** Justo DESPUÉS del bucle `for (const t of visibles) {…}` (línea 143) y ANTES de `type Item = …`, insertar:

```tsx
  // Drill-down desde «Este mes → Supermercado»: si la CATEGORÍA DEL RESIDUO del grupo es la
  // filtrada, el grupo se muestra con sus miembros filtrados por texto/cuenta/mes (ignorando
  // la categoría), aunque ningún miembro lleve esa categoría.
  if (categoriaId) {
    const sinFiltroDeCategoria = filterTxs(txs, {
      q: q || undefined, accountId: cuentaId || undefined, month: mes || undefined,
    });
    for (const g of grupos) {
      if (g.categoryId !== categoriaId || gruposVisibles.has(g.id)) continue;
      const miembros = sinFiltroDeCategoria.filter((t) => t.groupId === g.id);
      if (miembros.length > 0) gruposVisibles.set(g.id, miembros);
    }
  }
```

- [ ] **Paso 4: Meta de evento en `filaTx`.** En la línea 173, sustituir el contenido de `.meta` por:

```tsx
        <span className="meta">
          {t.date} · {nombreCuenta(t.accountId)}{t.importKey ? ' · importado' : ''}
          {t.eventId && calEvents.some((e) => e.id === t.eventId)
            ? ` · 📅 ${calEvents.find((e) => e.id === t.eventId)?.titulo}`
            : ''}
        </span>
```

- [ ] **Paso 5: Categoría en la fila de grupo y botón «Editar grupo».** En `filaGrupo`:
  - Tras `const residuos = residuosDeGrupo(todos, cuentas);` añadir `const grupo = grupos.find((g) => g.id === gid);`.
  - Icono (línea 193): `<span style={{ fontSize: 22 }}>{cat(grupo?.categoryId)?.icon ?? '🔁'}</span>`.
  - `.meta` (línea 196): `{todos.length} movimientos · vuelto{grupo?.categoryId && cat(grupo.categoryId) ? ` · ${cat(grupo.categoryId)!.name}` : ''}`.
  - En la `.fila-form` del área expandida (líneas 223-242), añadir como PRIMER botón:

```tsx
              <button
                className="boton secundario"
                disabled={guardando}
                onClick={() => grupo && setEditandoGrupo(grupo)}
              >
                Editar grupo
              </button>
```
  - Añadir el estado `const [editandoGrupo, setEditandoGrupo] = useState<TxGroup | null>(null);` junto a los demás `useState` (tras la línea 22) y, al final del JSX de `MovimientosPage` (junto a `{editando && <EditorMovimiento …/>}`):

```tsx
      {editandoGrupo && (
        <EditorGrupo
          grupo={editandoGrupo}
          residuos={residuosDeGrupo(txs.filter((t) => t.groupId === editandoGrupo.id), cuentas)}
          cerrar={() => setEditandoGrupo(null)}
        />
      )}
```

- [ ] **Paso 6: Categoría al agrupar.** Añadir el estado `const [categoriaGrupo, setCategoriaGrupo] = useState('');` (junto a `notaGrupo`), resetearlo en `cancelarSeleccion()` y pasarlo en `confirmarAgrupar`: `await agruparTxs(db, [...seleccion], notaGrupo, categoriaGrupo || undefined);`. En el modal «Agrupar como transaccional», bajo el input de nota, añadir:

```tsx
            <label>Categoría del vuelto (opcional)</label>
            <select aria-label="Categoría del grupo" value={categoriaGrupo} onChange={(e) => setCategoriaGrupo(e.target.value)}>
              <option value="">Sin categoría (Vuelto)</option>
              <optgroup label="Gastos">
                {categorias.filter((c) => c.kind === 'expense').map((c) => (
                  <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
                ))}
              </optgroup>
              <optgroup label="Ingresos">
                {categorias.filter((c) => c.kind === 'income').map((c) => (
                  <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
                ))}
              </optgroup>
            </select>
            <p className="ayuda">Se aplica al vuelto del grupo solo si el signo coincide; si no, cuenta como Vuelto.</p>
```

- [ ] **Paso 7: `EditorGrupo` (modal nuevo, al final del archivo).**

```tsx
function EditorGrupo({ grupo, residuos, cerrar }: {
  grupo: TxGroup;
  residuos: ResiduoMoneda[];
  cerrar: () => void;
}) {
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [nota, setNota] = useState(grupo.nota ?? '');
  const [categoriaId, setCategoriaId] = useState(grupo.categoryId ?? '');
  const [guardando, setGuardando] = useState(false);

  const elegida = categorias.find((c) => c.id === categoriaId);
  const noAplican = elegida
    ? residuos.filter((r) => (r.residuoMinor > 0 ? 'income' : 'expense') !== elegida.kind)
    : [];

  async function guardar() {
    if (guardando) return;
    setGuardando(true);
    try {
      await actualizarGrupo(db, grupo.id, { nota: nota.trim() || null, categoryId: categoriaId || null });
      avisar('Grupo actualizado ✓');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="modal-fondo" onClick={cerrar}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Editar grupo</h3>
        <label>Nota del grupo</label>
        <input aria-label="Nota del grupo (editar)" value={nota} onChange={(e) => setNota(e.target.value)} placeholder="me pasaron para la leche" />
        <label>Categoría del vuelto</label>
        <select aria-label="Categoría del grupo (editar)" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)}>
          <option value="">Sin categoría (Vuelto)</option>
          <optgroup label="Gastos">
            {categorias.filter((c) => c.kind === 'expense').map((c) => (
              <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
            ))}
          </optgroup>
          <optgroup label="Ingresos">
            {categorias.filter((c) => c.kind === 'income').map((c) => (
              <option key={c.id} value={c.id}>{c.icon} {c.name}</option>
            ))}
          </optgroup>
        </select>
        {noAplican.map((r) => (
          <p className="ayuda" key={r.currency}>
            Con el residuo actual ({r.residuoMinor >= 0 ? '+' : '−'}{formatMoney(Math.abs(r.residuoMinor), r.currency)}){' '}
            esta categoría de {elegida?.kind === 'expense' ? 'gasto' : 'ingreso'} no aplica; contará como Vuelto.
          </p>
        ))}
        <p className="ayuda">Los movimientos del grupo conservan su propia categoría.</p>
        <div className="fila-form" style={{ marginTop: 12 }}>
          <button className="boton secundario" onClick={cerrar} disabled={guardando}>Cancelar</button>
          <button className="boton" onClick={guardar} disabled={guardando}>Guardar</button>
        </div>
      </div>
    </div>
  );
}
```

`categoriaDelResiduo` no se llama aquí: la ayuda replica su regla para el aviso en vivo; el cálculo real lo hace `txsEfectivas`. Importa `categoriaDelResiduo` SOLO si lo usas — si no, **no lo importes** (`noUnusedLocals`).

- [ ] **Paso 8: `SelectEvento` en `EditorMovimiento`.** Añadir, junto a la live query de `categorias` (línea 310) y ANTES de cualquier `return`:

```tsx
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const txsAsignadas = useLiveQuery(() => db.transactions.where('eventId').above('').toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  const [ocurrencia, setOcurrencia] = useState<Ocurrencia | null>(
    tx.eventId && tx.eventDate ? { eventId: tx.eventId, date: tx.eventDate } : null,
  );
```

Dentro del bloque `{montoEditable && (…)}`, DESPUÉS del select de categoría, añadir:

```tsx
            <SelectEvento
              etiqueta="Evento"
              ariaLabel="Evento del movimiento"
              tx={{
                type: tx.type as 'income' | 'expense',
                date: fecha,
                accountId: tx.accountId,
                amountMinor: parseVenezuelanAmount(montoTxt.replace(/,$/, '')) ?? 0,
              }}
              actual={ocurrencia}
              ctx={{ eventos: calEvents, txs: txsAsignadas, accounts: cuentas, categories: categorias, hoy }}
              onChange={(o, evento) => {
                setOcurrencia(o);
                if (o && evento) setCategoriaId(evento.categoryId); // default editable
              }}
            />
```

En `guardar()`, dentro del `if (montoEditable) { … }`, tras `cambios.categoryId = categoriaId || undefined;` añadir (UNA sola escritura; `undefined` borra la propiedad — contrato verificado en `db.test.ts` de T1):

```tsx
      cambios.eventId = ocurrencia?.eventId;
      cambios.eventDate = ocurrencia?.date;
```

- [ ] **Paso 9: Verificación headless bloqueante.** `npm run dev -- --port 5109`. Script (scratchpad, NUNCA en el repo), `npx -y -p playwright node verificar.mjs`:

```js
import { chromium } from 'playwright';
const navegador = await chromium.launch();
const pagina = await navegador.newPage({ viewport: { width: 390, height: 844 } });
await pagina.clock.install({ time: new Date(2026, 8, 4, 10, 0, 0) });
await pagina.goto('http://localhost:5109/#/');
await pagina.clock.runFor(1500);
await pagina.evaluate(async () => {
  const d = window.__db;
  await d.transactions.clear(); await d.txGroups.clear(); await d.calEvents.clear();
  await d.calEvents.put({ id: 'e-quincena', titulo: 'Quincena', categoryId: 'cat-sueldo',
    accountId: 'acc-bnc', amountMinor: 800000, startDate: '2026-01-01',
    recurrence: { kind: 'monthly', days: [15, 31] }, createdIso: '2026-09-04T10:00:00.000Z' });
  await d.txGroups.add({ id: 'g1', nota: 'leche', createdIso: '2026-09-01T10:00:00.000Z' });
  await d.transactions.bulkAdd([
    { id: 'm1', type: 'income', accountId: 'acc-efectivo', amountMinor: 500, date: '2026-09-01', source: 'manual', groupId: 'g1', note: 'me pasaron' },
    { id: 'm2', type: 'expense', accountId: 'acc-efectivo', amountMinor: 700, date: '2026-09-02', source: 'manual', categoryId: 'cat-otros-gastos', groupId: 'g1', note: 'leche' },
    { id: 'pm', type: 'income', accountId: 'acc-bnc', amountMinor: 800000, date: '2026-09-15', source: 'manual', categoryId: 'cat-otros-ingresos', note: 'pago movil' },
  ]);
});
await pagina.goto('http://localhost:5109/#/movimientos');
await pagina.clock.runFor(1000);
await pagina.screenshot({ path: '.superpowers/sdd/capturas/v3-task-9/01-lista.png', fullPage: true });
await navegador.close();
```

Verifica DE VERDAD, capturando cada paso en `.superpowers/sdd/capturas/v3-task-9/`:
1. `01-lista.png`: la fila del grupo dice «leche» con `.meta` «2 movimientos · vuelto» (aún sin categoría) e icono 🔁.
2. Expandir el grupo → aparece el botón «Editar grupo» junto a «Añadir movimientos» y «Desagrupar» → `02-grupo-expandido.png`.
3. Tocar «Editar grupo», elegir «Supermercado» → la `.ayuda` dice «Con el residuo actual (−$2,00) …» **NO** aparece (el residuo es −$2,00 y Supermercado es de gasto: sí aplica); elegir «Sueldo/Pagos» → SÍ aparece el aviso de que contará como Vuelto → `03-editor-grupo-aviso.png`. Volver a Supermercado y Guardar.
4. Tras guardar: la fila del grupo muestra el icono 🛒 y `.meta` «2 movimientos · vuelto · Supermercado» → `04-grupo-con-categoria.png`.
5. Abrir el movimiento «pago movil» (tap corto) → en el editor aparece la etiqueta «EVENTO» y el select con «Sin evento» y «📅 Quincena · 15/09 · pendiente» → `05-editor-select-evento.png`. Elegir la Quincena → el select de categoría cambia a «Sueldo/Pagos». Guardar.
6. La fila de «pago movil» muestra `.meta` «2026-09-15 · BNC · 📅 Quincena» → `06-meta-evento.png`. Comprueba en `window.__db` que `pm` tiene `eventId: 'e-quincena'`, `eventDate: '2026-09-15'` y `categoryId: 'cat-sueldo'`, y que `amountMinor`, `date`, `accountId` y `type` NO cambiaron.
7. Reabrir el editor y elegir «Sin evento» → guardar → `pm` queda sin las claves `eventId`/`eventDate` y **conserva** `categoryId: 'cat-sueldo'` → `07-desasignado.png`.
8. Drill-down: navegar a `#/movimientos?cat=cat-supermercado&mes=2026-09` → el grupo «leche» aparece en la lista aunque ninguno de sus miembros tenga esa categoría → `08-drill-down-grupo.png`.
9. Marcar dos movimientos sueltos con pulsación larga, agrupar, elegir categoría en el modal → el grupo nace con la categoría → `09-agrupar-con-categoria.png`.

Mata el dev server. `npm run build` sin errores.

- [ ] **Paso 10: Commit.**

```bash
git add src/ui/componentes/SelectEvento.tsx src/ui/paginas/MovimientosPage.tsx
git commit -m "feat(movimientos): categoría de grupo, editor de grupo y asignación de evento con SelectEvento" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 10: Chips de evento en Nuevo (1 toque)

**Files:**
- Modify: `src/ui/paginas/NuevoPage.tsx` (imports líneas 1-12; hooks líneas 16-43; `guardar` líneas 55-77; JSX líneas 89-113)

**Interfaces:**
- Consumes: `candidatasParaTx`, `sugerirOcurrencia`, `aplicarAsignacion`, `CtxSugerencia` (T7); `Ocurrencia`, `OcurrenciaVista` (T6); `fechaCorta` (T2); clases `.chips-eventos`, `.chip-evento`, `.chip-evento.activo`, `.chip-evento.sugerido`, `.chip-evento .cerrar` (T5).
- Produces: ruta `#/nuevo?evento=<id>&fecha=<yyyy-mm-dd>` funcional (la usan `SemanaCard` y `CalendarioPage` de T11).
- **NO toca** `CapturaPage.tsx` ni `Nav.tsx`. El flujo ≤3 toques queda intacto: sin candidatas ni evento activo el DOM es idéntico a v2.

- [ ] **Paso 1: Imports y hooks.** Ampliar los imports:

```tsx
import { useEffect, useMemo, useRef, useState } from 'react';
import { todayIso, fechaCorta } from '../../domain/dates';
import type { CalEvent } from '../../domain/types';
import type { Ocurrencia } from '../../domain/calendar';
import { aplicarAsignacion, candidatasParaTx, sugerirOcurrencia } from '../../domain/eventMatch';
```

Añadir DESPUÉS de la live query de `categorias` (línea 22) y ANTES del primer `useEffect` (no hay returns condicionales en este componente, pero mantén el bloque de hooks junto):

```tsx
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const txsAsignadas = useLiveQuery(() => db.transactions.where('eventId').above('').toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  const [eventoActivo, setEventoActivo] = useState<Ocurrencia | null>(null);
  const [sugerida, setSugerida] = useState<Ocurrencia | null>(null);
  const prefillHecho = useRef(false);
```

- [ ] **Paso 2: Contexto y candidatas (`useMemo`, antes del `return`).** Tras `const [guardando, setGuardando] = useState(false);` (línea 43):

```tsx
  const ctxEventos = useMemo(
    () => ({ eventos: calEvents, txs: txsAsignadas, accounts: cuentas, categories: categorias, hoy }),
    [calEvents, txsAsignadas, cuentas, categorias, hoy],
  );

  const candidatas = useMemo(() => {
    if (!cuenta || (modo !== 'gasto' && modo !== 'ingreso')) return [];
    return candidatasParaTx(
      { type: modo === 'gasto' ? 'expense' : 'income', date: hoy, accountId: cuenta.id, amountMinor: minor ?? 0 },
      ctxEventos,
    ).slice(0, 4);
  }, [cuenta, modo, hoy, minor, ctxEventos]);

  const mismaOcurrencia = (a: Ocurrencia | null, b: Ocurrencia | null) =>
    a !== null && b !== null && a.eventId === b.eventId && a.date === b.date;

  /** Chips a pintar: la sugerida/activa primero, luego las candidatas. Máximo 4 + la activa. */
  const chips = useMemo(() => {
    const lista = candidatas.map((c) => ({
      o: { eventId: c.ocurrencia.eventId, date: c.ocurrencia.date } as Ocurrencia,
      evento: c.ocurrencia.evento,
      vencida: c.ocurrencia.estado === 'vencida',
    }));
    for (const fijada of [sugerida, eventoActivo]) {
      if (!fijada || lista.some((x) => mismaOcurrencia(x.o, fijada))) continue;
      const evento = calEvents.find((e) => e.id === fijada.eventId);
      if (evento) lista.unshift({ o: fijada, evento, vencida: fijada.date < hoy });
    }
    // la sugerida (o la activa) siempre primera
    const primera = eventoActivo ?? sugerida;
    if (primera) {
      const i = lista.findIndex((x) => mismaOcurrencia(x.o, primera));
      if (i > 0) lista.unshift(...lista.splice(i, 1));
    }
    return lista;
  }, [candidatas, sugerida, eventoActivo, calEvents, hoy]);
```

- [ ] **Paso 3: Prefill de evento y sugerencia desde captura (un solo `useEffect`).** Añadir tras el `useEffect` de settings (línea 39):

```tsx
  // Deep link `?evento&fecha` (SemanaCard / Calendario) y sugerencia tras una captura.
  // Se aplica UNA sola vez, cuando eventos y categorías ya cargaron.
  useEffect(() => {
    if (prefillHecho.current) return;
    if (calEvents.length === 0 || categorias.length === 0 || cuentas.length === 0) return;
    const eventoParam = params.get('evento');
    const fechaParam = params.get('fecha');
    if (eventoParam && fechaParam) {
      const evento = calEvents.find((e) => e.id === eventoParam);
      const kind = evento && categorias.find((c) => c.id === evento.categoryId)?.kind;
      if (evento && kind) {
        prefillHecho.current = true;
        setEventoActivo({ eventId: evento.id, date: fechaParam });
        setModo(kind === 'income' ? 'ingreso' : 'gasto');
        if (evento.accountId) setCuentaId(evento.accountId);
        if (!params.get('monto') && evento.amountMinor !== undefined) {
          setEntrada((evento.amountMinor / 100).toFixed(2).replace('.', ','));
        }
        return;
      }
    }
    // Sin `?evento`: si vino de captura con monto y cuenta, se ofrece la sugerencia (no activa).
    const montoParam = Number(params.get('monto') ?? '0');
    const cuentaParam = params.get('cuenta') ?? cuentas[0]?.id;
    if (montoParam > 0 && cuentaParam) {
      prefillHecho.current = true;
      const o = sugerirOcurrencia(
        {
          type: params.get('tipo') === 'ingreso' ? 'income' : 'expense',
          date: hoy, accountId: cuentaParam, amountMinor: montoParam,
        },
        { eventos: calEvents, txs: txsAsignadas, accounts: cuentas, categories: categorias, hoy },
      );
      if (o) setSugerida({ eventId: o.eventId, date: o.date });
    }
  }, [calEvents, categorias, cuentas, txsAsignadas, params, hoy]);
```

- [ ] **Paso 4: `guardar` con asignación opcional.** Reemplazar la firma y el cuerpo de `guardar` (líneas 55-77) por:

```tsx
  async function guardar(categoryId: string, o?: Ocurrencia | null, evento?: CalEvent | null) {
    if (guardando) return;
    if (!cuenta || minor === null || minor <= 0) return;
    setGuardando(true);
    try {
      const base = buildManualTx({
        type: modo === 'gasto' ? 'expense' : 'income',
        accountId: cuenta.id,
        amountMinor: minor,
        categoryId,
        dateIso: todayIso(),
        note: nota.trim() || undefined,
      });
      // La asignación NO altera monto, fecha, cuenta ni tipo.
      const tx = o && evento ? { ...aplicarAsignacion(base, o, evento), categoryId } : base;
      await db.transactions.add(tx);
      await saveSettings(db, { lastAccountId: cuenta.id });
      avisar('Guardado ✓');
      navigate('/');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }
```

Nota: se reaplica `categoryId` DESPUÉS de `aplicarAsignacion` porque cuando el usuario toca una categoría de la grilla con un chip activo manda la categoría elegida («es la quincena pero la quiero en Otros ingresos»). Cuando se toca el chip, `categoryId` ya es `evento.categoryId`.

- [ ] **Paso 5: Fila de chips (JSX).** Insertar ENTRE el input de nota (línea 106) y el `<label>Toca la categoría para guardar</label>` (línea 107):

```tsx
          {chips.length > 0 && (
            <div className="chips-eventos">
              {chips.map(({ o, evento, vencida }) => {
                const activo = mismaOcurrencia(o, eventoActivo);
                const icono = categorias.find((c) => c.id === evento.categoryId)?.icon ?? '📅';
                return (
                  <button
                    key={`${o.eventId}|${o.date}`}
                    type="button"
                    disabled={guardando}
                    className={['chip-evento', activo ? 'activo' : '', mismaOcurrencia(o, sugerida) && !activo ? 'sugerido' : ''].join(' ').trim()}
                    onClick={() => {
                      if (activo) { setEventoActivo(null); return; }        // ✕ desmarca
                      if (minor !== null && minor > 0) {
                        void guardar(evento.categoryId, o, evento);          // 1 toque: guarda y asigna
                      } else {
                        setEventoActivo(o);
                        avisar('Escribe el monto');
                      }
                    }}
                  >
                    <span>{icono} {evento.titulo} · {fechaCorta(o.date)}{vencida ? ' · vencida' : ''}</span>
                    {activo && <span className="cerrar">✕</span>}
                  </button>
                );
              })}
            </div>
          )}
```

Y cambiar el `onElegir` de `CategoriaGrid` (línea 110) a:

```tsx
            onElegir={(categoryId) => {
              const activo = eventoActivo ? chips.find((x) => mismaOcurrencia(x.o, eventoActivo)) : undefined;
              void guardar(categoryId, activo?.o ?? null, activo?.evento ?? null);
            }}
```

- [ ] **Paso 6: Verificación headless bloqueante.** `npm run dev -- --port 5110`. Siembra el evento «Quincena» (`cat-sueldo`, `acc-bnc`, `amountMinor: 800000`, `{ kind: 'monthly', days: [15, 31] }`, `startDate: '2026-01-01'`) igual que en T9. Capturas en `.superpowers/sdd/capturas/v3-task-10/`:

> **Nota del controlador (hallazgo verificado en la revisión de T10):** la receta genérica fija el reloj falso en `2026-09-04`, pero la ventana de candidatas de la decisión 12 es `[hoy−31, hoy+7]` = `[2026-08-04, 2026-09-11]`, así que la ocurrencia `2026-09-15` NO es candidata ese día y los escenarios 2–4 («chip 15 sep») serían inalcanzables. Para esos escenarios fija el reloj del script en `2026-09-15`. Es corrección de script de verificación, nunca de la app.
1. **Flujo ≤3 toques intacto**: con la base SIN eventos (`await d.calEvents.clear()`), ir a `#/nuevo?tipo=gasto`, escribir `12,50` → NO existe ningún `.chips-eventos` en el DOM (`await pagina.locator('.chips-eventos').count()` debe ser `0`) y la grilla de categorías se ve completa sin scroll extra → `01-sin-eventos-dom-v2.png`.
2. Con el evento sembrado, ir a `#/nuevo?tipo=ingreso`, elegir cuenta BNC, escribir `8000,00` → aparece UNA fila de chips con «💼 Quincena · 15 sep» → `02-chip-quincena.png`. Confirma que la fila NO empuja la grilla fuera de pantalla.
3. Tocar el chip → guarda y navega a Inicio. En `window.__db`, la última tx tiene `categoryId: 'cat-sueldo'`, `eventId: 'e-quincena'`, `eventDate: '2026-09-15'`, `amountMinor: 800000`, `type: 'income'` → `03-guardado-1-toque.png` (Inicio tras guardar).
4. Volver a `#/nuevo?tipo=ingreso` SIN escribir monto y tocar el chip → el chip queda `.activo` con ✕ y sale el toast «Escribe el monto» → `04-chip-activo.png`. Escribir el monto y tocar «Otros ingresos» en la grilla → la tx queda con `categoryId: 'cat-otros-ingresos'` **y** `eventId`/`eventDate` de la quincena → `05-categoria-manual-con-evento.png`.
5. Deep link: `#/nuevo?evento=e-quincena&fecha=2026-09-30` → al montar, modo = Ingreso, cuenta = BNC, monto pre-llenado `8000,00` y el chip «Quincena · 30 sep» primero y `.activo` → `06-deep-link.png`.
6. Captura: `#/nuevo?tipo=ingreso&monto=800000&cuenta=acc-bnc&nota=pago+movil` → el chip aparece con `.sugerido` (borde) y **no** activo; un toque guarda → `07-sugerido-desde-captura.png`.

Mata el dev server. `npm run build` sin errores.

- [ ] **Paso 7: Commit.**

```bash
git add src/ui/paginas/NuevoPage.tsx
git commit -m "feat(nuevo): chips de eventos con guardado en 1 toque y prefill por deep link" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 11: Editor de evento, tarjeta «Esta semana» y página Calendario

**Files:**
- Create: `src/ui/componentes/EditorEvento.tsx`
- Create: `src/ui/componentes/SemanaCard.tsx`
- Create: `src/ui/paginas/CalendarioPage.tsx`
- Modify: `src/ui/App.tsx` (import nuevo y **una** línea de ruta; NO toques la línea de `MovimientosPage`, que es de T14)

**Interfaces:**
- Consumes: `buildEvent`, `descripcionRecurrencia`, `esQuincenal`, `kindDeEvento`, `ocurrenciasEnRango`, `parseDias`, `totalesEsperados`, `RECURRENCIA_QUINCENAL`, `OcurrenciaVista` (T6); `guardarEvento`, `eliminarEvento`, `omitirOcurrencia`, `reanudarOcurrencia`, `desasignarEvento` (T8); `addDays`, `celdasMes`, `diaCorto`, `fechaLarga`, `LETRAS_DIA`, `mesAnterior`, `mesSiguiente`, `nombreMes`, `primerDiaMes`, `ultimoDiaMes` (T2); `monthOf`, `todayIso` (v1); clases de T5.
- Produces:
  - `SemanaCard({ ocurrencias, cuentas, categorias, hoy })` — **la monta T14 en `DashboardPage`**; en esta tarea queda exportada sin consumidores (un export sin usar NO viola `noUnusedLocals`).
  - `EditorEvento({ original, borrador, cerrar })`.
  - `CalendarioPage({ params })` y la ruta `#/calendario`.

- [ ] **Paso 1: `src/ui/componentes/EditorEvento.tsx`.** Hoja `.modal-fondo > .modal`, patrón `EditorDeuda` (`DeudasPage.tsx:126-196`). Props:

```tsx
export function EditorEvento({ original, borrador, cerrar }: {
  original: CalEvent | null;   // null = alta
  borrador: CalEvent;          // valores iniciales (startDate = día seleccionado en el calendario)
  cerrar: () => void;
})
```

Estado y live queries (todos al inicio, sin returns condicionales antes):

```tsx
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txsDelEvento = useLiveQuery(() => db.transactions.where('eventId').equals(borrador.id).count(), [borrador.id]) ?? 0;
  const esNuevo = original === null;
  const [titulo, setTitulo] = useState(esNuevo ? '' : borrador.titulo);
  const [kind, setKind] = useState<CategoryKind>('expense');           // se sincroniza en el Paso 2
  const [categoriaId, setCategoriaId] = useState(esNuevo ? '' : borrador.categoryId);
  const [cuentaId, setCuentaId] = useState(borrador.accountId ?? '');
  const [montoTxt, setMontoTxt] = useState(borrador.amountMinor === undefined ? '' : (borrador.amountMinor / 100).toFixed(2).replace('.', ','));
  const [fecha, setFecha] = useState(borrador.startDate);
  const [repetir, setRepetir] = useState<'once' | 'weekly' | 'monthly' | 'quincenal' | 'yearly'>(
    borrador.recurrence.kind === 'monthly'
      ? (esQuincenal(borrador.recurrence) ? 'quincenal' : 'monthly')
      : borrador.recurrence.kind,
  );
  const [diasTxt, setDiasTxt] = useState(
    borrador.recurrence.kind === 'monthly' ? borrador.recurrence.days.join(', ') : String(partsOf(borrador.startDate).d),
  );
  const [fin, setFin] = useState(borrador.endDate ?? '');
  const [guardando, setGuardando] = useState(false);
  const moneda = cuentas.find((c) => c.id === cuentaId)?.currency ?? 'USD';
```

- [ ] **Paso 2: Campos del editor, en este orden exacto.**
  1. `<h3>` «Nuevo evento» / «Editar evento».
  2. `<label>Título</label>` + `<input aria-label="Título del evento" placeholder="Quincena">`.
  3. `.segmentado` con dos botones: «Pago» (`kind === 'expense'`) y «Cobro» (`kind === 'income'`). Solo filtra el select de categoría; al cambiar, si la categoría elegida ya no pertenece al kind, se vacía (`setCategoriaId('')`). Estado inicial: `useEffect` que, cuando `categorias` carga, hace `setKind(categorias.find((c) => c.id === borrador.categoryId)?.kind ?? 'expense')` **una sola vez** (ref `kindFijado`).
  4. `<label>Categoría</label>` + `<select aria-label="Categoría del evento">` con `<option value="">Elige una categoría</option>` y `categorias.filter((c) => c.kind === kind)`.
  5. `<label>Cuenta esperada (define la moneda)</label>` + `<select aria-label="Cuenta del evento">` con `<option value="">— sin cuenta —</option>` y las cuentas no archivadas (`{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})`). Al cambiar de cuenta, si la moneda cambia y `montoTxt !== ''`: `setMontoTxt('')` + `avisar('La cuenta elegida usa otra moneda: vuelve a escribir el monto')` (idéntico a `EditorDeuda.cambiarCuenta`).
  6. `<label>Monto esperado en {moneda === 'VES' ? 'Bs' : '$'} (opcional)</label>` + `<input aria-label="Monto esperado" inputMode="decimal" placeholder="0,00" disabled={!cuentaId}>` + `<p className="ayuda">Sin cuenta no se puede fijar un monto. Déjalo vacío si el monto varía.</p>`.
  7. `<label>{repetir === 'once' ? 'Fecha' : 'Primera vez'}</label>` + `<input aria-label="Fecha del evento" type="date">`.
  8. `<label>Repetir</label>` + `<select aria-label="Repetición">` con EXACTAMENTE estas opciones y valores: `once` «Una vez», `weekly` «Cada semana», `monthly` «Cada mes», `quincenal` «Quincenal (15 y último)», `yearly` «Cada año». Al pasar a `once`, además `setFin('')` (la regla `once` ignora `endDate`).
  9. Solo si `repetir === 'monthly'`: `<label>Días del mes</label>` + `<input aria-label="Días del mes" placeholder="15, 31">` + `<p className="ayuda">31 = último día del mes</p>`.
  10. Solo si `repetir !== 'once'`: `<label>Fin (opcional)</label>` + `<input aria-label="Fecha de fin" type="date">` + `<button className="chip" onClick={() => setFin(todayIso())}>Terminar hoy</button>`.
  11. `.fila-form` con: «Cancelar» (`boton secundario`), «Eliminar» (`boton peligro`, **solo en edición**) y «Guardar» (`boton`). Todos `disabled={guardando}`.

- [ ] **Paso 3: Guardar y eliminar (guard `guardando`).**

```tsx
  async function guardar() {
    if (guardando) return;
    const recurrence: Recurrence =
      repetir === 'quincenal' ? RECURRENCIA_QUINCENAL
      : repetir === 'monthly' ? { kind: 'monthly', days: [] }   // se rellena abajo
      : { kind: repetir };
    setGuardando(true);
    try {
      if (repetir === 'monthly') (recurrence as { kind: 'monthly'; days: number[] }).days = parseDias(diasTxt);
      let amountMinor: number | undefined;
      if (montoTxt.trim() !== '') {
        const m = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
        if (m === null || m <= 0) throw new Error('Monto inválido');
        amountMinor = m;
      }
      const evento = buildEvent({
        titulo, categoryId: categoriaId, accountId: cuentaId || undefined, amountMinor,
        startDate: fecha, endDate: fin || undefined, recurrence,
        omitidas: original?.omitidas,          // editar CONSERVA las omitidas
        nowIso: original?.createdIso ?? new Date().toISOString(),
      });
      await guardarEvento(db, esNuevo ? evento : { ...evento, id: borrador.id, createdIso: borrador.createdIso });
      avisar('Evento guardado ✓');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar() {
    if (guardando) return;
    if (!window.confirm(`¿Eliminar «${borrador.titulo}»? ${txsDelEvento} movimientos quedarán sin evento (conservan su categoría).`)) return;
    setGuardando(true);
    try {
      const n = await eliminarEvento(db, borrador.id);
      avisar(n > 0 ? `Evento eliminado · ${n} movimientos desasignados` : 'Evento eliminado');
      cerrar();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }
```

`parseDias` y `parseVenezuelanAmount` lanzan dentro del `try`, así que sus mensajes llegan por `avisar`. Un `buildEvent` de una edición genera un `id` nuevo que luego se sobrescribe con el del borrador: por eso el `put` usa `{ ...evento, id: borrador.id, createdIso: borrador.createdIso }`.

- [ ] **Paso 4: `src/ui/componentes/SemanaCard.tsx`.** Componente **puro de presentación** (sin acceso a `db`, sin guardado async → sin guard `guardando`). Firma exacta:

```tsx
export function SemanaCard({ ocurrencias, cuentas, categorias, hoy }: {
  ocurrencias: OcurrenciaVista[];   // ya viene del rango [hoy−30, hoy+6] que calcula el llamador
  cuentas: Account[];
  categorias: Category[];
  hoy: string;
})
```

Estructura y textos LITERALES:
- `<div className="tarjeta" style={{ marginTop: 12 }}>`.
- `<div className="tarjeta-cabecera"><h3>Esta semana</h3><a className="chip" href="#/calendario">calendario</a></div>`.
- Tira: `const dias = Array.from({ length: 7 }, (_, i) => addDays(hoy, i));`. Un `<div className="semana-tira">` con un `<button className={`semana-dia${d === hoy ? ' hoy' : ''}`} onClick={() => navigate(`/calendario?dia=${d}`)}>` por día, que contiene `<span className="letra">{LETRAS_DIA[weekdayLunes0(d)]}</span>`, `<span className="num">{partsOf(d).d}</span>` y `<span className="puntos">` con hasta 3 `<span className={`punto${o.estado === 'hecha' ? ' hecha' : ''}${o.estado === 'vencida' ? ' vencida' : ''}`} style={{ background: categorias.find((c) => c.id === o.evento.categoryId)?.color ?? '#8b96ab' }} />` (el color es dato del usuario, como el donut).
- Totales (solo si `pagar.length > 0 || cobrar.length > 0`), con `totalesEsperados(ocurrencias, cuentas, categorias)`: `<p className="ayuda">` con «Por pagar {lista} — Por cobrar {lista}», donde cada lista son sus monedas unidas con « · » y formateadas con `formatMoney(totalMinor, currency)`. Si un lado está vacío se omite junto a su guion.
- Lista: `const vencidas = ocurrencias.filter((o) => o.estado === 'vencida');` y `const semana = ocurrencias.filter((o) => o.date >= hoy && o.date <= addDays(hoy, 6));`; se muestran `[...vencidas, ...semana]` (vencidas primero). Estado local `const [todo, setTodo] = useState(false);` → se pintan `todo ? lista : lista.slice(0, 3)`, y si `lista.length > 3` un `<button className="chip" onClick={() => setTodo(!todo)}>` con texto «ver N más» / «ver menos» (N = `lista.length - 3`).
- Cada fila: `<button className={`fila-ocurrencia${o.estado === 'hecha' ? ' hecha' : ''}`}>` con el icono de la categoría, `<span className="detalle">` (`<span className="nota">{o.evento.titulo}</span>` y `<span className="meta">{diaCorto(o.date)}{o.evento.accountId ? ` · ${nombre de la cuenta}` : ''}</span>`), el monto (esperado con `formatMoney(o.evento.amountMinor, moneda de evento.accountId)` si lo hay; si la ocurrencia es `hecha`, la Σ de `o.asignadoPorMoneda`) y `<span className={`estado estado-${o.estado}`}>{o.estado}</span>`.
- `onClick` de la fila: `pendiente`/`vencida` → `navigate(`/nuevo?evento=${o.eventId}&fecha=${o.date}`)`; `hecha`/`omitida` → `navigate(`/calendario?dia=${o.date}`)`.
- Vacío (`lista.length === 0`): `<div className="vacio"><span className="icono">🗓️</span>Sin pagos ni cobros esta semana</div>` + `<a className="chip" href="#/calendario?nuevo=1">crear evento</a>`.

- [ ] **Paso 5: `src/ui/paginas/CalendarioPage.tsx`.** Firma `export function CalendarioPage({ params }: { params: URLSearchParams })`.

Todos los hooks al inicio, sin returns condicionales antes:

```tsx
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  const [mes, setMes] = useState(params.get('mes') ?? monthOf(params.get('dia') ?? hoy));
  const [diaSel, setDiaSel] = useState(() => {
    const pedido = params.get('dia');
    if (pedido && pedido.startsWith(mes)) return pedido;
    return hoy.startsWith(mes) ? hoy : primerDiaMes(mes);
  });
  const [editando, setEditando] = useState<CalEvent | null>(params.get('nuevo') === '1' ? NUEVO(hoy) : null);
  const [guardando, setGuardando] = useState(false);

  const ocurrencias = useMemo(
    () => ocurrenciasEnRango({
      eventos: calEvents, txs, accounts: cuentas,
      desde: primerDiaMes(mes), hasta: ultimoDiaMes(mes), hoy,
    }),
    [calEvents, txs, cuentas, mes, hoy],
  );
  const porFecha = useMemo(() => {
    const m = new Map<string, OcurrenciaVista[]>();
    for (const o of ocurrencias) m.set(o.date, [...(m.get(o.date) ?? []), o]);
    return m;
  }, [ocurrencias]);
```

`NUEVO(diaSel)` es un helper del módulo que devuelve el borrador:

```tsx
const NUEVO = (startDate: string): CalEvent => ({
  id: crypto.randomUUID(), titulo: '', categoryId: '', startDate,
  recurrence: { kind: 'once' }, createdIso: new Date().toISOString(),
});
```

Estructura y textos LITERALES:
- `<h2>Calendario</h2>`.
- `<div className="nav-mes">`: `<button aria-label="Mes anterior" onClick={() => cambiarMes(mesAnterior(mes))}>‹</button>` · `<h3 className="titulo">{nombreMes(mes)}</h3>` · `<button aria-label="Mes siguiente" onClick={() => cambiarMes(mesSiguiente(mes))}>›</button>` · y, solo si `mes !== monthOf(hoy)`, `<button className="chip" onClick={() => { setMes(monthOf(hoy)); setDiaSel(hoy); }}>hoy</button>`. `cambiarMes(m)` hace `setMes(m)` y `setDiaSel(monthOf(hoy) === m ? hoy : primerDiaMes(m))`. Sin límites de mes (el calendario SÍ navega al futuro; el límite de la decisión 14 es solo para las tarjetas de estadísticas).
- `<div className="cal-cabecera">{LETRAS_DIA.map((l, i) => <span key={i}>{l}</span>)}</div>`.
- `<div className="cal-grid">` con `celdasMes(mes).map(({ iso, enMes }) => …)`: `<button key={iso} className={['cal-dia', enMes ? '' : 'otro-mes', iso === hoy ? 'hoy' : '', iso === diaSel ? 'seleccionado' : ''].join(' ').trim()} onClick={() => setDiaSel(iso)}>` con `<span className="num">{partsOf(iso).d}</span>`, `<span className="puntos">` (hasta 3 `.punto` con `background: color de la categoría`; `.hecha` si el estado es hecha, `.vencida` si vencida) y, si hay más de 3, `<span className="mas">+{n - 3}</span>`.
- `<div className="cal-lista">` con `<h3>{fechaLarga(diaSel)}</h3>` y, por cada ocurrencia de `porFecha.get(diaSel) ?? []`, un bloque `.fila-ocurrencia` (misma anatomía que en `SemanaCard`) seguido de `<div className="acciones">`:
  - `pendiente`/`vencida`: `<a className="chip" href={`#/nuevo?evento=${o.eventId}&fecha=${o.date}`}>registrar</a>` y `<button className="chip" disabled={guardando} onClick={() => accion(() => omitirOcurrencia(db, o.eventId, o.date), 'Ocurrencia omitida')}>omitir</button>`.
  - `omitida`: `<button className="chip" …onClick={() => accion(() => reanudarOcurrencia(db, o.eventId, o.date), 'Ocurrencia reanudada')}>reanudar</button>`.
  - `hecha`: por cada `txId` de `o.txIds`, una línea con la nota y el monto del movimiento (búscalo en `txs`) y un `<button className="chip" disabled={guardando} onClick={() => accion(() => desasignarEvento(db, txId), 'Movimiento desasignado')}>quitar</button>`.
  - Si `o.fueraDeRegla`: `<span className="chip">fuera de la regla</span>`.
  - Vacío: `<div className="vacio"><span className="icono">🗓️</span>Nada este día</div>`.
- `<button className="boton" style={{ marginTop: 12 }} onClick={() => setEditando(NUEVO(diaSel))}>Nuevo evento</button>`.
- `<details style={{ marginTop: 16 }}><summary>Todos los eventos ({calEvents.length})</summary>` con una fila por evento: icono de su categoría · `<span className="nota">{e.titulo}</span>` · `<span className="meta">{descripcionRecurrencia(e)}</span>` · `<button className="chip" onClick={() => setEditando(e)}>editar</button>`. El chip «eliminar» abre el mismo `window.confirm` con el conteo y llama a `eliminarEvento`; para no duplicar lógica, basta con «editar» (el editor ya trae «Eliminar»); si añades «eliminar» aquí, usa exactamente el mismo texto de confirmación.
- `{editando && <EditorEvento original={calEvents.find((e) => e.id === editando.id) ?? null} borrador={editando} cerrar={() => setEditando(null)} />}`.

Helper de acciones (guard `guardando`, igual que `accionGrupo` de `MovimientosPage`):

```tsx
  async function accion(fn: () => Promise<unknown>, exito: string) {
    if (guardando) return;
    setGuardando(true);
    try { await fn(); avisar(exito); }
    catch (e) { avisar((e as Error).message); }
    finally { setGuardando(false); }
  }
```

- [ ] **Paso 6: Ruta en `App.tsx`.** Añadir `import { CalendarioPage } from './paginas/CalendarioPage';` tras la línea 12 y, dentro de `<main className="contenido">`, tras la línea de `/deudas`:

```tsx
        {path === '/calendario' && <CalendarioPage params={params} />}
```

**NO** cambies la línea `{path === '/movimientos' && <MovimientosPage />}` — ese cambio es de T14 y tocarlo aquí choca con esa tarea. `Nav.tsx` NO cambia (patrón Deudas: `#/calendario` se alcanza desde la tarjeta).

- [ ] **Paso 7: Verificación headless bloqueante.** `npm run dev -- --port 5111`. Siembra con `window.__db` la Quincena (`e-quincena`, `cat-sueldo`, `acc-bnc`, `amountMinor: 800000`, `monthly [15, 31]`, `startDate: '2026-01-01'`), Netflix (`e-netflix`, `cat-suscripciones`, `acc-zinli`, `amountMinor: 1599`, `monthly [5]`) y una tx asignada al 15. Capturas en `.superpowers/sdd/capturas/v3-task-11/`:
1. `#/calendario` → cabecera «Septiembre 2026», rejilla de **42 celdas** (`await pagina.locator('.cal-dia').count()` === 42), el 4 con `.hoy`, el 5 con un punto (Netflix, vencida → `.punto.vencida`), el 15 con un punto atenuado (`.punto.hecha`) y el 30 con punto → `01-grid.png`.
2. Tocar el día 30 → `.cal-dia.seleccionado` y la lista dice «Miércoles 30 de septiembre» con la fila «Quincena» y los chips «registrar» y «omitir» → `02-dia-30.png`.
3. Tocar «omitir» → la fila pasa a `.estado-omitida` (tachado) y el chip cambia a «reanudar»; el punto del 30 desaparece de la rejilla → `03-omitida.png`. Tocar «reanudar» → vuelve a pendiente → `04-reanudada.png`.
4. Tocar el día 15 → la fila está `hecha` y lista su movimiento con el chip «quitar»; tocarlo → la ocurrencia vuelve a `vencida` y el movimiento sigue existiendo con su categoría → `05-quitar-asignacion.png`.
5. «Nuevo evento» → el editor abre con la fecha del día seleccionado. Crear «Condominio» con Pago / Hogar / sin cuenta / Repetir «Cada mes» / Días «31» → guarda y aparece un punto el último día del mes → `06-editor-nuevo.png` y `07-evento-creado.png`. Mide el tiempo del flujo con el preset «Quincenal (15 y último)»: debe ser cómodo en ~20 s (criterio de éxito).
6. `<details>` «Todos los eventos (3)» abierto → cada fila muestra su `descripcionRecurrencia` («quincenal (15 y último)», «mensual: 5», «mensual: último») → `08-todos-los-eventos.png`.
7. Editar la Quincena (cambiar solo el título) y guardar → en `window.__db`, `omitidas` sigue igual que antes de editar → `09-omitidas-conservadas.png`.
8. Eliminar la Quincena desde el editor → el `confirm` dice «N movimientos quedarán sin evento (conservan su categoría)»; tras aceptar, el evento desaparece y la tx del 15 sigue en la base sin `eventId` → `10-eliminado.png`.
9. `#/calendario?nuevo=1` abre el editor al montar; `#/calendario?dia=2026-09-15` selecciona ese día → `11-deep-links.png`.
10. **SemanaCard**: en esta tarea todavía no está montada en Inicio (eso es T14). Verifica solo que `npm run build` compila y que Inicio se ve igual que antes → `12-inicio-sin-cambios.png`.

Mata el dev server. `npm run build` sin errores.

- [ ] **Paso 8: Commit.**

```bash
git add src/ui/componentes/EditorEvento.tsx src/ui/componentes/SemanaCard.tsx src/ui/paginas/CalendarioPage.tsx src/ui/App.tsx
git commit -m "feat(calendario): página #/calendario, editor de eventos y tarjeta de la semana" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 12: «Este mes» expandible y navegador de mes

**Files:**
- Modify: `src/ui/componentes/GraficasMes.tsx` (props líneas 15-21; cabecera de la tarjeta líneas 33-34; bloque nuevo bajo el área, tras la línea 89; título de Presupuestos línea 112)
- Modify: `src/ui/paginas/DashboardPage.tsx` (imports líneas 1-10; hooks líneas 13-19; cálculos líneas 27-34; render de `GraficasMes` línea 46)

**Interfaces:**
- Consumes: `ResumenMes.incomeByCategory` (T4); `txsEfectivas(txs, cuentas, grupos, categorias)` (T3); `mesAnterior`, `mesSiguiente`, `nombreMes` (T2); clases `.tarjeta-cabecera`, `.nav-mes`, `.nav-mes .titulo`, `.lista-categorias`, `.subtitulo`, `.fila-categoria`, `.barra-categoria`, `.cifras`, `.monto`, `.pct`, `.chip` (T5).
- Produces: `GraficasMes` con 4 props nuevas y la ruta de drill-down `#/movimientos?cat=<id>&mes=<yyyy-mm>` (los params los consume `MovimientosPage` de T9; `App.tsx` los pasa en T14 — hasta entonces el enlace navega y los filtros quedan en su default, que es el comportamiento aceptable de transición).
- **NO** toca `App.tsx` (T11/T14) ni monta `SemanaCard` (T14).

- [ ] **Paso 1: Props nuevas en `GraficasMes`.** Cambiar la firma (líneas 15-21) por:

```tsx
export function GraficasMes({ resumen, comparativa, presupuestos, categorias, viewCurrency, mes, mesActual, primerMes, onMes }: {
  resumen: ResumenMes;
  comparativa: ResumenMes[];
  presupuestos: EstadoPresupuesto[];
  categorias: Category[];
  viewCurrency: Currency;
  /** Mes que gobierna las TRES tarjetas (Este mes, Comparativa, Presupuestos). */
  mes: string;
  mesActual: string;
  /** Mes más antiguo con movimientos: tope del botón «‹». */
  primerMes: string;
  onMes: (m: string) => void;
}) {
```

Añadir el import `import { useState } from 'react';` y `import { mesAnterior, mesSiguiente, nombreMes } from '../../domain/dates';`, y dentro del componente (antes de cualquier return; no hay returns condicionales):

```tsx
  const [expandido, setExpandido] = useState(false);
```

- [ ] **Paso 2: Cabecera con navegador de mes.** Reemplazar la línea 34 (`<h3>Este mes ({resumen.month})</h3>`) por:

```tsx
        <div className="tarjeta-cabecera">
          <div className="nav-mes">
            <button aria-label="Mes anterior" disabled={mes <= primerMes} onClick={() => onMes(mesAnterior(mes))}>‹</button>
            <h3 className="titulo">{mes === mesActual ? 'Este mes' : nombreMes(mes)}</h3>
            <button aria-label="Mes siguiente" disabled={mes >= mesActual} onClick={() => onMes(mesSiguiente(mes))}>›</button>
            {mes !== mesActual && <button className="chip" onClick={() => onMes(mesActual)}>hoy</button>}
          </div>
        </div>
```

- [ ] **Paso 3: Lista expandible de categorías.** Insertar DESPUÉS del `<div style={{ height: 160 }}>…</div>` del área acumulada (tras la línea 89) y ANTES del `</div>` que cierra la tarjeta:

```tsx
        <button className="chip" style={{ marginTop: 8 }} onClick={() => setExpandido(!expandido)}>
          {expandido ? 'Ocultar' : 'Ver todas las categorías'}
        </button>
        {expandido && (
          <div className="lista-categorias">
            {resumen.byCategory.length > 0 && <div className="subtitulo">Gastos</div>}
            {resumen.byCategory.map((b) => filaCategoria(b.categoryId, b.expenseMinor, resumen.expenseMinor))}
            {resumen.incomeByCategory.length > 0 && <div className="subtitulo">Ingresos</div>}
            {resumen.incomeByCategory.map((b) => filaCategoria(b.categoryId, b.incomeMinor, resumen.incomeMinor))}
          </div>
        )}
```

Y definir `filaCategoria` dentro del componente, junto a `const cat = …` (línea 22):

```tsx
  const filaCategoria = (categoryId: string | null, minor: number, total: number) => {
    const c = cat(categoryId);
    const pct = total > 0 ? Math.round((minor / total) * 100) : 0;
    const contenido = (
      <>
        <span className="icono">{c?.icon ?? '📦'}</span>
        <span>
          <span className="nombre">{c?.name ?? 'Sin categoría'}</span>
          <span className="barra-categoria">
            <span style={{ display: 'block', height: '100%', width: `${pct}%`, background: c?.color ?? '#8b96ab' }} />
          </span>
        </span>
        <span className="cifras">
          <span className="monto">{dinero(minor)}</span>
          <span className="pct">{pct} %</span>
        </span>
      </>
    );
    // «Sin categoría» no tiene drill-down posible: se pinta gris y sin enlace.
    return c
      ? <a key={`${categoryId}`} className="fila-categoria" href={`#/movimientos?cat=${categoryId}&mes=${mes}`}>{contenido}</a>
      : <div key="sin-categoria" className="fila-categoria" style={{ color: 'var(--texto2)' }}>{contenido}</div>;
  };
```

La regla del CSS es `.barra-categoria > div`; aquí el hijo es un `<span>` con `display: block`, así que **cambia el selector de T5 no**: usa un `<div>` en vez del `<span>` interior si prefieres, pero NO edites `estilos.css` (es de T5). La opción segura es anidar `<div>` dentro del `<span className="barra-categoria">`.

Los virtuales «Vuelto» aparecen como una categoría normal (sus ids `cat-vuelto-*` existen en la tabla).

- [ ] **Paso 4: Título de Presupuestos.** Cambiar la línea 112 por:

```tsx
          <h3>Presupuestos (USD) · {nombreMes(mes)}</h3>
```

- [ ] **Paso 5: `mesVisto` y `primerMes` en `DashboardPage`.** Añadir a los imports `import { useState } from 'react';` y ampliar el de fechas a `import { todayIso, monthOf } from '../../domain/dates';` (ya está). Tras la última live query (línea 19) y ANTES del `if (!settings)`:

```tsx
  const [mesVisto, setMesVisto] = useState(() => monthOf(todayIso()));
```

Reemplazar las líneas 27-34 por:

```tsx
  const mesActual = monthOf(todayIso());
  const primerMes = txs.length > 0
    ? monthOf(txs.reduce((min, t) => (t.date < min ? t.date : min), txs[0].date))
    : mesActual;
  // El navegador de mes gobierna las TRES tarjetas; nunca sale del rango [primerMes, mesActual].
  const mes = mesVisto > mesActual ? mesActual : mesVisto < primerMes ? primerMes : mesVisto;
  const efectivas = txsEfectivas(txs, cuentas, grupos, categorias);
  const base = { txs: efectivas, accounts: cuentas, rates, activeSource: settings.rateSource };
  const resumen = monthSummary({ ...base, month: mes, viewCurrency: settings.viewCurrency });
  const comparativa = lastMonths(mes, 4)
    .map((m) => monthSummary({ ...base, month: m, viewCurrency: settings.viewCurrency }))
    .reverse();
  const presupuestos = budgetStatus({ ...base, categories: categorias, month: mes });
```

`patrimonio(...)` (líneas 23-26) sigue usando `txs` CRUDAS: no lo toques.

- [ ] **Paso 6: Pasar las props.** Cambiar la línea 46 por:

```tsx
      <GraficasMes
        resumen={resumen} comparativa={comparativa} presupuestos={presupuestos}
        categorias={categorias} viewCurrency={settings.viewCurrency}
        mes={mes} mesActual={mesActual} primerMes={primerMes} onMes={setMesVisto}
      />
```

- [ ] **Paso 7: Verificación headless bloqueante.** `npm run dev -- --port 5112`. Siembra movimientos en **agosto y septiembre de 2026** (varios gastos con categoría, al menos un ingreso con `cat-sueldo`, uno sin categoría) y un grupo con `categoryId: 'cat-supermercado'` cuyo residuo sea NEGATIVO (p. ej. ingreso $5,00 + gasto $7,00 → −$2,00). Capturas en `.superpowers/sdd/capturas/v3-task-12/`:
1. `#/` → la tarjeta dice «Este mes» con «‹» habilitado, «›» deshabilitado y sin chip «hoy» → `01-este-mes.png`.
2. Tocar «Ver todas las categorías» → aparece `.lista-categorias` con el subtítulo «Gastos», una fila por categoría con icono, barra de color, monto y porcentaje, luego «Ingresos» y «Sin categoría» al final en gris sin enlace → `02-lista-expandida.png`. Comprueba que los porcentajes de Gastos suman ~100 %.
3. **Categoría de grupo**: la fila «Supermercado» incluye los $2,00 del residuo del grupo y el donut muestra ese color; el gasto agrupado de $7,00 NO aparece por separado → `03-residuo-en-supermercado.png`.
4. Tocar «‹» → el título pasa a «Agosto 2026», aparece el chip «hoy», «›» se habilita, el donut/área/comparativa y «Presupuestos (USD) · Agosto 2026» se recalculan → `04-agosto.png`. Tocar «‹» hasta el mes más antiguo: el botón queda `:disabled` (opacidad .45) → `05-tope-primer-mes.png`.
5. Tocar el chip «hoy» → vuelve a «Este mes» → `06-vuelta-a-hoy.png`.
6. Con agosto abierto y la lista expandida, tocar «Supermercado» → navega a `#/movimientos?cat=cat-supermercado&mes=2026-08` → `07-drill-down.png`. (Hasta T14 `App.tsx` no pasa `params`, así que la lista puede llegar sin filtros preseleccionados: anótalo en el reporte, T14 lo cierra.)
7. Patrimonio: comprueba que el saldo de la tarjeta de Patrimonio NO cambia al navegar de mes (usa txs crudas) → `08-patrimonio-estable.png`.

Mata el dev server. `npm run build` sin errores.

- [ ] **Paso 8: Commit.**

```bash
git add src/ui/componentes/GraficasMes.tsx src/ui/paginas/DashboardPage.tsx
git commit -m "feat(inicio): «Este mes» expandible con lista de categorías y navegación de mes" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 13: Sugerencia de evento en los imports (dep T9 por `SelectEvento`)

**Files:**
- Modify: `src/ui/componentes/ImportBnc.tsx` (`DraftUI` líneas 14-18; live queries líneas 41-43; `analizar` líneas 45-65; fila de la vista previa líneas 225-248)
- Modify: `src/ui/componentes/ImportZinli.tsx` (`DraftUI` líneas 15-19; live queries líneas 33-34; `analizar` líneas 36-67; fila líneas 199-222)

**Interfaces:**
- Consumes: `SelectEvento` (T9); `sugerirParaLote`, `aplicarAsignacion` (T7); `todayIso` (ya importado en ambos).
- Produces: nada para tareas posteriores.
- **PROHIBIDO tocar** `src/import/*` (parsers y mappers), `src/db/commit.ts` y el backstop de `importKey`: los campos `eventId`/`eventDate`/`categoryId` viajan dentro de la `tx` del draft y `commitImport` los persiste tal cual.

- [ ] **Paso 1: `DraftUI` gana `sugerido`.** En AMBOS archivos, añadir a la interfaz local:

```ts
  /** true si `sugerirParaLote` propuso esta asignación y el usuario no la ha cambiado. */
  sugerido: boolean;
```

- [ ] **Paso 2: Live queries.** En `ImportBnc.tsx`, tras la línea 43 (`txs`):

```tsx
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
```

En `ImportZinli.tsx`, lo mismo tras la línea 34. (Ambos ya importan `useState` y `todayIso`.)

- [ ] **Paso 3: Sugerencia en `analizar()`.** En `ImportBnc.tsx`, sustituir la línea 63 (`setDrafts(mapeados.map(...))`) por:

```tsx
    // El evento sugerido GANA a la heurística por defecto y a la regla aprendida
    // (decisión 5 del spec v3): aplicarAsignacion fija eventId/eventDate y la categoría
    // del evento. El select de categoría sigue visible y corregible antes de confirmar.
    const asignadas = txs.filter((t) => t.eventId !== undefined);
    const sugerencias = sugerirParaLote(
      mapeados.filter((d) => !d.duplicate).map((d) => d.tx),
      { eventos: calEvents, txs: asignadas, accounts: cuentas, categories: categorias, hoy },
    );
    setDrafts(mapeados.map((d) => {
      const o = sugerencias.get(d.tx.id);
      const evento = o ? calEvents.find((e) => e.id === o.eventId) ?? null : null;
      return {
        ...d,
        tx: o && evento ? aplicarAsignacion(d.tx, o, evento) : d.tx,
        incluir: !d.duplicate,
        crearRegla: false,
        patron: d.tx.note ?? '',
        sugerido: Boolean(o && evento),
      };
    }));
```

En `ImportZinli.tsx`, aplicar el MISMO reemplazo sobre su línea 63 (el bloque es idéntico salvo que allí no hay `pairRef`).

Añadir en ambos: `import { aplicarAsignacion, sugerirParaLote } from '../../domain/eventMatch';` y `import { SelectEvento } from './SelectEvento';`.

- [ ] **Paso 4: `SelectEvento` por fila.** En AMBOS archivos, dentro del `<span style={{ display: 'flex', gap: 6, marginTop: 4, alignItems: 'center' }}>` que ya contiene el select de categoría y el checkbox «regla», añadir DESPUÉS del select de categoría:

```tsx
                  <SelectEvento
                    ariaLabel={`Evento fila ${i + 1}`}
                    tx={{
                      type: d.tx.type as 'income' | 'expense',
                      date: d.tx.date,
                      accountId: d.tx.accountId,
                      amountMinor: d.tx.amountMinor,
                    }}
                    actual={d.tx.eventId && d.tx.eventDate ? { eventId: d.tx.eventId, date: d.tx.eventDate } : null}
                    ctx={{ eventos: calEvents, txs: txs.filter((t) => t.eventId !== undefined), accounts: cuentas, categories: categorias, hoy }}
                    onChange={(o, evento) => actualizar(i, { tx: aplicarAsignacion(d.tx, o, evento), sugerido: false })}
                  />
                  {d.sugerido && d.tx.eventId && <span className="chip">sugerido</span>}
```

`SelectEvento` devuelve `null` si esa fila no tiene candidatas, así que la fila queda exactamente como en v2 cuando no hay eventos. No lleva `etiqueta` (iría dentro de una fila flex).

- [ ] **Paso 5: Verificación headless bloqueante.** `npm run dev -- --port 5113`. Siembra la Quincena (`cat-sueldo`, `acc-bnc`, `amountMinor: 800000`, `monthly [15, 31]`). Usa el TXT anonimizado de `src/import/bnc/fixtures/bncFixture.ts` como texto a pegar, **editando en el script** (no en el repo) las fechas/montos para que una fila sea un pago móvil recibido de Bs 8.000,00 el 2026-09-15 y otra un pago móvil pequeño del amigo el 2026-09-14. Capturas en `.superpowers/sdd/capturas/v3-task-13/`:
1. Pegar el texto y «Analizar» → en la vista previa, la fila del 15 con Bs 8.000,00 muestra el select de evento en «📅 Quincena · 15/09 · pendiente» **y** su select de categoría en «Sueldo/Pagos», con el chip «sugerido» al lado → `01-bnc-sugerido.png`.
2. La fila del amigo NO trae evento (su select dice «Sin evento» o no se renderiza) y conserva la categoría de la regla/heurística → `02-bnc-sin-sugerencia.png`.
3. Cambiar el evento de la fila sugerida a «Sin evento» → el chip «sugerido» desaparece; la categoría NO se revierte (queda «Sueldo/Pagos», editable) → `03-cambio-manual.png`. Volver a asignarla.
4. Corregir la categoría a «Otros ingresos» manteniendo el evento → el select de evento se mantiene → `04-categoria-corregida.png`.
5. Confirmar → en `window.__db`, la tx importada tiene `importKey`, `eventId: 'e-quincena'`, `eventDate: '2026-09-15'` y la categoría que quedó en pantalla → `05-importado.png`.
6. Reimportar el MISMO texto → todas las filas salen «ya importado» (el backstop de `importKey` sigue intacto) y no se crea nada → `06-dedupe-intacto.png`.
7. Zinli: repite 1, 2 y 5 con un PDF de fixture equivalente (o siembra un evento de gasto en `acc-zinli` y usa el fixture de `src/import/zinli/fixtures/zinliFixture.ts`) → `07-zinli-sugerido.png`.
8. Con la base SIN eventos (`await d.calEvents.clear()`), la vista previa de ambos imports se ve **idéntica a la v2** → `08-sin-eventos-identico.png`.

Mata el dev server. `npm run build` sin errores.

- [ ] **Paso 6: Commit.**

```bash
git add src/ui/componentes/ImportBnc.tsx src/ui/componentes/ImportZinli.tsx
git commit -m "feat(imports): sugerencia de evento por lote y select de evento por fila" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

## Fase 4 — Integración y cierre

### Tarea 14: Inicio con la tarjeta de la semana, params de Movimientos y E2E headless

**Files:**
- Modify: `src/ui/paginas/DashboardPage.tsx` (live queries; `useMemo` de ocurrencias; render de `SemanaCard`)
- Modify: `src/ui/App.tsx` (SOLO la línea de `MovimientosPage`)

**Interfaces:**
- Consumes: `SemanaCard` (T11), `ocurrenciasEnRango` (T6), `addDays` (T2), `MovimientosPage({ params })` (T9), `mesVisto`/`primerMes` (T12), la ruta `#/calendario` (T11).
- Produces: la v3 completa y verificada de punta a punta.
- Esta tarea es la ÚNICA que puede tocar a la vez archivos de T11 y T12: va después de todas ellas.

- [ ] **Paso 1: Live query de eventos y `hoy` hoisted.** En `DashboardPage.tsx`, junto a las demás live queries y ANTES del `if (!settings)`:

```tsx
  const calEvents = useLiveQuery(() => db.calEvents.toArray(), []) ?? [];
  const [hoy] = useState(todayIso);
  // Inicio muestra 7 días rodantes más las vencidas de los últimos 30 (decisión 13).
  const ocurrenciasSemana = useMemo(
    () => ocurrenciasEnRango({
      eventos: calEvents, txs, accounts: cuentas,
      desde: addDays(hoy, -30), hasta: addDays(hoy, 6), hoy,
    }),
    [calEvents, txs, cuentas, hoy],
  );
```

Ampliar los imports: `import { useMemo, useState } from 'react';`, `import { todayIso, monthOf, addDays } from '../../domain/dates';`, `import { ocurrenciasEnRango } from '../../domain/calendar';`, `import { SemanaCard } from '../componentes/SemanaCard';`.

**Todos** los hooks quedan por encima del `if (!settings) return <h2>Inicio</h2>;` — verifica el orden antes de seguir.

- [ ] **Paso 2: Montar `SemanaCard`.** En el JSX, ENTRE `<PatrimonioCard …/>` y `<DeudasCard …/>`:

```tsx
      <SemanaCard ocurrencias={ocurrenciasSemana} cuentas={cuentas} categorias={categorias} hoy={hoy} />
```

Orden final de Inicio: Patrimonio → **Esta semana** → Deudas → Este mes → Comparativa → Presupuestos.

Sustituye además el `todayIso()` inline de la línea 22 (`effectiveRate(todayIso(), …)`) por `hoy`, y el de `mesActual` por `monthOf(hoy)`, para que toda la página use el mismo «hoy».

- [ ] **Paso 3: Params a Movimientos.** En `src/ui/App.tsx`, cambiar la línea de `/movimientos` por:

```tsx
        {path === '/movimientos' && <MovimientosPage params={params} />}
```

Es el ÚNICO cambio de esta tarea en `App.tsx`; la ruta `/calendario` ya la puso T11.

> **Ruling del controlador, obligatorio en esta tarea (hallazgo verificado en la revisión de T10):** `App.tsx:43` monta `<NuevoPage params={params} />` SIN `key`, así que una navegación de `#/nuevo?...` a otro `#/nuevo?...` distinto NO remonta la página: el guard `prefillHecho.current` sigue en `true` de la visita anterior y el prefill del nuevo deep link nunca se aplica, dejando `modo`, `entrada`, `cuentaId` y `eventoActivo` obsoletos. Cambia esa línea a `<NuevoPage key={params.toString()} params={params} />` (una línea, misma tarea que ya toca `App.tsx`) y añade al E2E un paso que navegue de un deep link de ocurrencia A a uno de ocurrencia B sin pasar por otra pantalla, verificando que el segundo prefill sí se aplica. Esto amplía el «único cambio en App.tsx» del Paso 3 a dos líneas: la de `/movimientos` y la de `/nuevo`.

- [ ] **Paso 4: E2E headless bloqueante (el criterio de éxito del spec, completo).** `npm run dev -- --port 5114`. Un solo script que recorre el guion de Daniel de principio a fin, con capturas en `.superpowers/sdd/capturas/v3-task-14/`. Parte de una base **limpia** (`indexedDB.deleteDatabase('cuentas-claras')` antes de cargar, o `d.transactions.clear()` + `d.calEvents.clear()` + `d.txGroups.clear()`), reloj congelado en `new Date(2026, 8, 4, 10, 0, 0)`.

1. **Crear la Quincena por UI**: `#/calendario` → «Nuevo evento» → Cobro / Sueldo-Pagos / cuenta BNC / monto `8000,00` / fecha `2026-09-01` / Repetir «Quincenal (15 y último)» → Guardar. Cronometra: debe rondar los 20 s. → `01-quincena-creada.png`.
2. **Inicio**: la tarjeta «Esta semana» aparece entre Patrimonio y Deudas, con la tira de 7 días (4→10 de septiembre) y la línea de totales «Por cobrar Bs 16.000,00» (dos quincenas del mes están fuera de la semana: verifica el texto real que produzca `totalesEsperados` para el rango [hoy−30, hoy+6] y ajusta la aserción a ese valor, no al revés) → `02-inicio-semana.png`.
3. **Captura → Nuevo**: `#/captura?texto=<notificación de pago móvil recibido por Bs 8.000,00>` → redirige a `#/nuevo?...` y el chip «💼 Quincena · 15 sep» aparece **primero** con `.sugerido` → `03-chip-primero.png`. Un toque → guardado con `categoryId: 'cat-sueldo'` y `eventId`/`eventDate` de la quincena del 15.
4. **Calendario**: `#/calendario` → el día 15 muestra su punto atenuado y la lista del día dice `hecha` con el movimiento asignado → `04-quincena-hecha.png`.
5. **Import BNC**: pegar el TXT de fixture (con un pago móvil grande el 15 y uno pequeño del amigo) → el grande viene sugerido a la Quincena del 30 (la del 15 ya está hecha) y el del amigo no → `05-import-sugerido.png`. Confirmar.
6. **Grupo con categoría**: en `#/movimientos`, pulsación larga sobre el ingreso de $5,00 y la compra de la leche → «Agrupar como transaccional» con categoría «Supermercado» → `06-grupo-supermercado.png`.
7. **Este mes**: `#/` → «Ver todas las categorías» → «Supermercado» aparece con los $2,00 del residuo → `07-supermercado-en-lista.png`.
8. **Mes anterior + drill-down**: «‹» hasta agosto → tocar «Supermercado» → `#/movimientos?cat=cat-supermercado&mes=2026-08` abre **con los filtros ya puestos** (select de categoría en «Supermercado» y de mes en «2026-08») → `08-drill-down-con-filtros.png`. Éste es el paso que valida el cambio de `App.tsx`.
9. **Respaldo**: Ajustes → exportar; comprobar que el JSON descargado tiene `schemaVersion: 3` y `calEvents` con el evento; importar un respaldo **v2** (constrúyelo en el script quitando `calEvents` y poniendo `schemaVersion: 2`) → se acepta sin error y deja `calEvents` vacío → `09-respaldo-v2.png`.
10. **Regresiones**: Patrimonio, Deudas, Transferir, Ajustar y el flujo ≤3 toques (`#/nuevo?tipo=gasto` sin eventos aplicables → sin `.chips-eventos`) siguen exactamente como en v2 → `10-regresiones.png`.

Mata el dev server.

- [ ] **Paso 5: Suite y build.** `npx vitest run` (todo verde) y `npm run build` sin errores.

- [ ] **Paso 6: Commit.**

```bash
git add src/ui/paginas/DashboardPage.tsx src/ui/App.tsx
git commit -m "feat(inicio): tarjeta «Esta semana» en el dashboard y filtros por URL en Movimientos" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

---

### Tarea 15: README, build, deploy y validación de Daniel

**Files:**
- Modify: `README.md` (sección nueva «Calendario y eventos» tras «Privacidad»)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: v3 en producción y el reporte final para Daniel.

- [ ] **Paso 1: Sección del README.** Añadir al final de `README.md`:

```markdown
## Calendario y eventos (v3)

Un **evento** es una fecha en la que hay que pagar o cobrar algo (la quincena, Netflix, el
condominio). No es dinero: no altera saldos ni estadísticas. Sus **ocurrencias** (la quincena
del 15 de septiembre) se derivan de la regla de repetición cada vez que se dibujan; lo único
que se guarda es la definición del evento y, en cada movimiento asignado, el par
`eventId` + `eventDate`.

- **Dónde**: `#/calendario` (se llega desde la tarjeta «Esta semana» de Inicio, no desde la barra).
- **Crear**: «Nuevo evento» → Pago o Cobro, categoría (obligatoria: define la dirección),
  cuenta esperada y monto opcionales, fecha y repetición
  (Una vez · Cada semana · Cada mes · Quincenal (15 y último) · Cada año).
  En «Cada mes», `31` significa **último día del mes**.
- **Terminar una suscripción**: pon una fecha de Fin (chip «Terminar hoy»).
- **Estados**: hecha (tiene movimientos) > omitida > vencida > pendiente. Hoy cuenta como pendiente.
- **Asignar un movimiento**: en Nuevo, el chip del evento guarda y asigna en **un toque**
  (copia la categoría del evento, editable antes de guardar); en el editor de un movimiento,
  con el select «Evento»; en la vista previa de los imports, el evento sugerido gana a la
  regla aprendida y queda corregible. Desasignar conserva la categoría.
- **Sugerencia**: la ocurrencia puede estar hasta 31 días antes del movimiento o 7 después,
  y el monto esperado debe coincidir ±10 % (si el evento tiene monto).
- **Eliminar un evento** nunca borra movimientos: los desasigna y conservan su categoría.

## Grupos transaccionales con categoría (v3)

Un grupo puede llevar una **categoría del vuelto**. Se aplica solo si su tipo coincide con el
signo del residuo (una categoría de gasto para un residuo negativo, de ingreso para uno
positivo); si no coincide, el residuo cuenta como «Vuelto», igual que en v2. Los movimientos
del grupo conservan su propia categoría.

## «Este mes» expandible (v3)

La tarjeta «Este mes» tiene navegación de mes («‹ › hoy», acotada entre el mes más antiguo con
movimientos y el actual) que gobierna también Comparativa y Presupuestos, y un botón
«Ver todas las categorías» con la lista completa de gastos e ingresos; tocar una categoría
abre Movimientos ya filtrado por esa categoría y ese mes.

## Respaldos

`schemaVersion: 3` (9 tablas, incluye `calEvents`). Los respaldos **v2 se aceptan** y se
normalizan al importarlos; los v1 siguen rechazados.
```

- [ ] **Paso 2: Verificación final completa.** En este orden:
  1. `npx vitest run` → toda la suite en verde (los tests v1/v2 no se tocaron).
  2. `npm run build` → sin errores (incluye `tsc --noEmit` con `noUnusedLocals`).
  3. `npm run preview` y una pasada headless rápida sobre el build de producción: Inicio, `#/calendario`, `#/nuevo`, `#/movimientos` e `#/importar` cargan sin errores de consola (`pagina.on('pageerror', …)` y `pagina.on('console', …)` con filtro de `error`). Confirma además que en producción `window.__db` es `undefined`. Capturas en `.superpowers/sdd/capturas/v3-task-15/`.

- [ ] **Paso 3: Repaso de invariantes (sección 10 del spec).** Recorre las 20 y anota en el reporte cualquiera que no puedas verificar. En particular:
  - `git grep -n "new Date('" src/domain src/db src/backup` y `git grep -n "toISOString" src/domain` → ninguna fecha de calendario construida desde string ni desde UTC.
  - `git grep -n "txsEfectivas" src` → solo en agregaciones (`DashboardPage`), nunca en `balances.ts` ni en `PatrimonioCard`.
  - `git diff 57b2e78 --stat -- src/import src/db/commit.ts src/domain/filters.ts src/domain/builders.ts src/domain/balances.ts src/capture src/ui/paginas/CapturaPage.tsx src/ui/Nav.tsx src/ui/useHashRoute.ts` → **vacío** (archivos intocables).

- [ ] **Paso 4: Deploy y push.**

```bash
npm run build
npx wrangler pages deploy dist --project-name cuentas-claras
git push origin main
```

- [ ] **Paso 5: Commit del README.**

```bash
git add README.md
git commit -m "docs: documenta el calendario de eventos, la categoría de grupo y «Este mes» expandible" -m "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_017oWdjKjmQiA6V6rtnpGKAw"
```

- [ ] **Paso 6: Validación de Daniel (bloqueante, con desviaciones declaradas).** Entrega el reporte con:
  - La URL de producción y el guion del criterio de éxito para que lo recorra en su teléfono con sus datos reales (respaldo previo desde Ajustes, por si acaso).
  - La lista de **todas** las desviaciones respecto a las 20 decisiones del spec, si las hubo, con su justificación.
  - Los avisos de las tareas que dejaron algo abierto (revisa los reportes de T1–T14).
  - Recordatorio explícito: las 20 decisiones de diseño las tomó el agente y **Daniel puede vetar cualquiera ahora**.
