# Cuentas Claras v2 — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** Añadir a la PWA "Cuentas Claras" (v1 en producción, https://cuentas-claras-r2f.pages.dev) las cuatro capacidades de la v2 — deudas y cuentas por cobrar, patrimonio en 3 vistas con slider, movimientos transaccionales con residuo «Vuelto», y captura de notificaciones del BNC vía MacroDroid — más el fix del selector de TXT en Android, con Dexie v2 aditivo y respaldo `schemaVersion: 2`.

**Arquitectura:** Se mantiene la de la v1: SPA React con navegación por hash, IndexedDB vía Dexie + `useLiveQuery`, núcleo de dominio en funciones puras con TDD y UI sin tests automáticos pero con verificación visual bloqueante. Las novedades siguen el mismo corte: `domain/debts.ts` y `domain/groups.ts` son puros y testeables; `db/debtOps.ts` y `db/groupOps.ts` encapsulan las operaciones transaccionales; la UI solo compone. El residuo «Vuelto» de los grupos es **VIRTUAL**: nunca se persiste, se deriva en cada render con `txsEfectivas()` y alimenta SOLO las agregaciones (nunca los saldos).

**Stack:** el real del repo — React 19 + TypeScript 6 + vite 8 (rolldown) · Dexie 4.4.4 + dexie-react-hooks · Recharts · pdfjs-dist 6 · vite-plugin-pwa 1.3.0 · Vitest 4 (entorno `node`, fake-indexeddb, setup en `src/test/setup.ts`) · CSS puro con tokens de `estilos.css` · Node 23 (aviso EBADENGINE inofensivo).

**Spec (fuente de verdad del *qué*):** `docs/superpowers/specs/2026-07-22-cuentas-claras-v2-design.md`
**Vigentes en todo lo no contradicho:** spec v1 (`docs/superpowers/specs/2026-07-07-finanzas-personales-design.md`) y `.superpowers/sdd/restricciones-globales.md`.

## Prerrequisitos de ejecución (ANTES de la Tarea 1)

1. **Cerrar la v1**: la limpieza de compat legada ya se hizo (commit `83514aa`); queda **redeploy a producción** de `main` con los fixes del fix-wave (`npm run build` + `npx wrangler pages deploy dist --project-name cuentas-claras` — wrangler ya autenticado) y `superpowers:finishing-a-development-branch`. Verifica el ledger `.superpowers/sdd/progress.md` por si ya consta.
2. **Pedir a Daniel los 2-3 textos ANONIMIZADOS de notificaciones del BNC** (pago móvil recibido y compra con tarjeta, sin nombres/cédulas/números completos). No bloquean nada hasta la Fase 4: pídelos al arrancar y sigue trabajando.
3. Abrir la sección «v2» en el ledger `.superpowers/sdd/progress.md`. El helper `bash .superpowers/sdd/tarea-brief.sh <RUTA-PLAN> N` existe porque los planes usan "Tarea" y el script de la skill busca "Task".

## Restricciones globales

Aplican a TODAS las tareas; cada tarea las incluye implícitamente. Las 11 restricciones de `.superpowers/sdd/restricciones-globales.md` siguen vigentes ÍNTEGRAS (céntimos enteros, todo en español, privacidad de comprobantes reales, tests solo en núcleo con TDD, vitest verde antes de cada commit + build al cerrar fase, ≤3 toques, tokens CSS, footer de commit exacto `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`). Además, para la v2:

1. **Decisiones cerradas de Daniel (NO reabrir, NO volver a preguntar)** — las 7 del spec §«Decisiones cerradas». En especial la 7: **cero retrocompatibilidad de datos**; Daniel re-ingresa/re-importa tras actualizar; ninguna tarea escribe migración de contenido ni compat con respaldos/claves v1.
2. **«Omite las verificaciones con chrome»**: la extensión claude-in-chrome falla en esta PC. TODA verificación de UI se hace con Playwright headless (chromium YA instalado): `npx -y -p playwright node script.mjs`. Capturas a `.superpowers/sdd/capturas/task-N/`. Cada implementador levanta su propio dev server (`npm run dev`) y **lo mata al terminar** (hubo huérfanos en 5173/5174). Recharts se congela en headless → usa la Clock API de Playwright (receta en `.superpowers/sdd/task-12-report.md`).
3. **Patrones obligatorios del código** (el revisor los exige): guard `guardando` en TODO guardado async (check → validación → `setGuardando(true)` → await en try con éxito-solo → catch `avisar` → finally reset; botones `disabled={guardando}`); ningún `useLiveQuery` después de un return condicional; jerarquía z contenido < nav(30) < modal(40) < toast(50); estilos SOLO por tokens/clases de `estilos.css`.
4. **`d.transaction` con ARRAY de tablas si son >5** (el overload variádico de Dexie topa en 5; `backup.ts` ya usa array).
5. **No tocar el contrato anti-duplicación del import**: la puerta sigue siendo el filtro de la UI (`incluir && !duplicate`) y `commitImport` conserva su backstop que revalida importKeys dentro de la transacción y aborta. No revertir tampoco los demás fixes del fix-wave (bncImportKey con fecha, «Vigente hoy» con histórico completo, guards del banner «Crear ajuste», validación de fecha en EditorMovimiento, re-match de la regla default Zinli).
6. **`txsEfectivas()` alimenta SOLO agregaciones** (resumen del mes, comparativa, gráficas, presupuestos). Los saldos (`accountBalanceMinor`, `patrimonio`) usan SIEMPRE las transacciones crudas. Confundir esto corrompe los saldos visibles.
7. Entorno: Windows 11. El tool Bash (Git Bash) es el caballo de batalla para comandos; los bloques de comandos de este plan son sintaxis bash.
8. Los movimientos capturados/creados por la v2 usan `source: 'manual'` (no se añaden valores a `TxSource`).

### Receta de verificación visual headless (referenciada por las tareas de UI)

Guarda variantes de este script en el scratchpad (NUNCA en el repo), ajustando la interacción y las capturas que pida cada tarea:

```js
// verificar.mjs — npx -y -p playwright node verificar.mjs
import { chromium } from 'playwright';
const navegador = await chromium.launch();
const pagina = await navegador.newPage({ viewport: { width: 390, height: 844 } });
await pagina.clock.install();                    // congela animaciones de Recharts
await pagina.goto('http://localhost:5173/#/');   // el dev server lo levanta y mata el implementador
await pagina.clock.runFor(2000);
// … interacción específica de la tarea (clicks, fills, asserts con expect manual) …
await pagina.screenshot({ path: '.superpowers/sdd/capturas/task-N/01-nombre.png', fullPage: true });
await navegador.close();
```

Para sembrar datos de prueba usa `pagina.evaluate` contra el singleton IndexedDB (la app expone Dexie vía import; si no, siembra por UI). Verifica de verdad lo que el paso describe antes de commitear.

## Estructura de archivos (nuevos y modificados)

```
Nuevos:
  src/domain/debts.ts + debts.test.ts        ← buildDebt, buildSettleTx, debtTotals (puro)
  src/db/debtOps.ts + debtOps.test.ts        ← settleDebt transaccional e idempotente
  src/domain/groups.ts + groups.test.ts      ← residuosDeGrupo + txsEfectivas (puro)
  src/db/groupOps.ts + groupOps.test.ts      ← agruparTxs / anadirAGrupo / quitarDeGrupo / desagrupar
  src/capture/parseNotificacionBnc.ts + .test.ts + fixtures/notificacionesBnc.ts
  src/ui/paginas/DeudasPage.tsx
  src/ui/paginas/CapturaPage.tsx
  src/ui/componentes/DeudasCard.tsx
  docs/guia-macrodroid.md
Modificados:
  src/domain/types.ts        ← Debt, TxGroup, Account.enDisposicion, Transaction.groupId
  src/db/db.ts               ← version(2), tablas debts/txGroups, seeds v2 check-por-id
  src/db/db.test.ts          ← conteo 18 categorías + test de seeds v2 sobre DB ya sembrada
  src/backup/backup.ts       ← schemaVersion 2, tablas nuevas, validateBackup exige 2
  src/backup/backup.test.ts  ← round-trip con deuda+grupo, conteo 18, rechazo de v1
  src/domain/balances.ts     ← aDisposicionUsdMinor + netoUsdMinor
  src/domain/balances.test.ts
  src/ui/componentes/ImportBnc.tsx      ← accept del input TXT (bug Android)
  src/ui/componentes/PatrimonioCard.tsx ← slider de 3 vistas
  src/ui/componentes/CuentasSection.tsx ← checkbox «a disposición»
  src/ui/paginas/DashboardPage.tsx      ← deudas + txsEfectivas + DeudasCard
  src/ui/paginas/MovimientosPage.tsx    ← selección long-press + grupos
  src/ui/paginas/NuevoPage.tsx          ← prefill por params (captura)
  src/ui/App.tsx             ← rutas /deudas y /captura
  src/ui/estilos.css         ← slider, barra de selección, fila de grupo
```

Convención v1 vigente: `src/domain`, `src/db`, `src/import`, `src/backup`, `src/capture` no importan React ni `src/ui`; las funciones que tocan la base reciben `d: CuentasDB` como primer parámetro; la UI usa el singleton `db`.

## Decisiones de diseño de este plan (documentadas, no reabrir sin causa)

- **Residuo por moneda**: si un grupo mezcla cuentas de monedas distintas, se emite un movimiento virtual «Vuelto» POR MONEDA (Σ firmada dentro de cada moneda). El caso común (una sola moneda) produce exactamente el residuo único del spec. Cada virtual toma el `accountId` del miembro más reciente de su moneda (para que `monthSummary` convierta bien) y la fecha del miembro más reciente del grupo (spec §3).
- **Siembra de seeds v2**: `seedIfEmpty` mantiene el empty-check para DB fresca Y añade un check-por-id de las 3 categorías nuevas sobre DB ya sembrada (add-if-missing, nunca sobreescribe ediciones del usuario). Con la decisión 7 Daniel resetea igualmente, pero así el mecanismo queda declarado y robusto.
- **Quitar miembros dejando el grupo con <2**: el grupo se disuelve automáticamente (se limpia `groupId` del resto y se borra la fila de `txGroups`).
- **Fix del selector TXT**: ampliar `accept` a `.txt,text/plain,application/octet-stream` (Android SAF filtra por MIME e ignora extensiones; el TXT del banco llega típicamente como `application/octet-stream`). El parser ya rechaza limpio cualquier archivo no-BNC, así que ampliar no arriesga nada.
- **Ids de virtuales**: `virtual-${groupId}-${moneda}` — jamás se persisten; si aparecieran en la base sería un bug.

## Criterio de éxito de la v2

> Daniel registra una deuda con su amigo asignada a Efectivo y la ve descontada en la vista «Total neto»; la marca pagada y el gasto aparece en Efectivo; desliza el patrimonio entre sus 3 vistas; agrupa los $5 que le pasaron con la compra de la leche y su mes solo refleja el vuelto; toca la notificación capturada del BNC y confirma el pago móvil en 1 toque; y selecciona su TXT de BNC en Android sin pelear con el selector.

---

## Fase 0 — Bug del selector TXT y capa de datos v2

### Tarea 1: Fix del selector de TXT en Android (ImportBnc)

**REQUIRED SUB-SKILL:** `superpowers:systematic-debugging` — es un bug reportado, no una feature: confirma la causa antes de tocar.

**Files:**
- Modify: `src/ui/componentes/ImportBnc.tsx:144`

**Interfaces:**
- Consumes: nada nuevo.
- Produces: input de archivo BNC seleccionable en Android. Sin cambios de API.

- [ ] **Paso 1: Confirmar la hipótesis leyendo el código.** El input BNC es (`ImportBnc.tsx:141-147`):

```tsx
<input
  aria-label="Archivo TXT"
  type="file"
  accept=".txt,text/plain"
  onChange={async (e) => {
    const f = e.target.files?.[0];
    if (f) setTexto(await f.text());
```

Síntoma (Daniel, Android, selector desde Descargas/Archivos): el TXT aparece pero tocarlo no hace nada. Causa raíz esperada: el selector de documentos de Android construye el filtro con los MIME de `accept` (las extensiones como `.txt` las ignora), y el TXT exportado por el BNC se cataloga como `application/octet-stream` → el archivo se muestra pero deshabilitado. Verifica que no haya OTRO filtro en el flujo (busca `accept` y validaciones de `f.type` en `ImportBnc.tsx`); si encontraras una causa distinta, documéntala en el ledger y corrige esa.

- [ ] **Paso 2: Aplicar el fix mínimo** — ampliar el accept:

```tsx
accept=".txt,text/plain,application/octet-stream"
```

Revisar por el mismo patrón el input de Zinli (`ImportZinli.tsx:134`, `accept=".pdf,application/pdf"`): los PDF sí llegan con MIME `application/pdf` fiable en Android, se deja como está. Documenta esa decisión en el ledger.

- [ ] **Paso 3: Verificar en desktop headless que la carga sigue intacta.** Levanta `npm run dev`, crea en el scratchpad un TXT sintético con la fixture anonimizada de `src/import/bnc/fixtures/bncFixture.ts` (NUNCA el TXT real), y con la receta de verificación: navega a `#/importar`, `setInputFiles` del TXT, comprueba que aparece la vista previa con filas, captura a `.superpowers/sdd/capturas/task-1/`. Además `expect` de que el atributo `accept` del input contiene `application/octet-stream`. Mata el dev server.

- [ ] **Paso 4: Suite verde.** Run: `npx vitest run` — Expected: 104 passed (este fix no toca núcleo).

- [ ] **Paso 5: Commit**

```bash
git add src/ui/componentes/ImportBnc.tsx
git commit -m "fix(import): el selector Android permite elegir el TXT del BNC

El accept del input filtraba por text/plain y Android cataloga el
export del banco como application/octet-stream, dejándolo deshabilitado
en el selector de documentos.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

- [ ] **Paso 6 (diferido, NO bloquea las tareas siguientes):** registrar en el ledger que la validación de Daniel en su Android queda pendiente para la Tarea 16.

### Tarea 2: Tipos, esquema Dexie v2 y seeds v2

**Files:**
- Modify: `src/domain/types.ts`
- Modify: `src/db/db.ts`
- Test: `src/db/db.test.ts`

**Interfaces:**
- Consumes: tipos v1.
- Produces: `Debt`, `DebtDirection`, `DebtStatus`, `TxGroup`, `Account.enDisposicion?: boolean`, `Transaction.groupId?: string`; tablas `db.debts` (índices `id, status`) y `db.txGroups` (`id`); índice `groupId` en transactions; categorías seed `cat-deudas` (expense), `cat-vuelto-ingreso` (income), `cat-vuelto-gasto` (expense). Todas las tareas posteriores dependen de esto.

- [ ] **Paso 1: Tests en RED.** En `src/db/db.test.ts`, actualizar el conteo y añadir tests (el test «5 cuentas y 15 categorías» pasa a 18):

```ts
// En el test 'el seed crea las 5 cuentas y 15 categorías de la spec':
//   renombrar a 'el seed crea las 5 cuentas y 18 categorías de la spec'
//   y cambiar la aserción:
  expect(await d.categories.count()).toBe(18);

// Tests NUEVOS al final del archivo:
test('seeds v2: las 3 categorías nuevas existen con el kind correcto', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  expect((await d.categories.get('cat-deudas'))?.kind).toBe('expense');
  expect((await d.categories.get('cat-vuelto-ingreso'))?.kind).toBe('income');
  expect((await d.categories.get('cat-vuelto-gasto'))?.kind).toBe('expense');
});

test('seeds v2: sobre una DB ya sembrada añade solo las que falten, sin pisar ediciones', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  await d.categories.delete('cat-vuelto-gasto');
  await d.categories.update('cat-deudas', { name: 'Deudas editada' });
  await seedIfEmpty(d);
  expect(await d.categories.count()).toBe(18);                       // repuso la borrada
  expect((await d.categories.get('cat-deudas'))?.name).toBe('Deudas editada'); // no pisó la edición
});

test('esquema v2: debts y txGroups aceptan filas y el índice groupId consulta', async () => {
  const d = nuevaDb();
  await seedIfEmpty(d);
  await d.debts.add({
    id: 'd1', direction: 'porPagar', concepto: 'Juan — préstamo', amountMinor: 500,
    accountId: 'acc-efectivo', status: 'pendiente', createdIso: '2026-07-22T10:00:00.000Z',
  });
  await d.txGroups.add({ id: 'g1', createdIso: '2026-07-22T10:00:00.000Z' });
  await d.transactions.add({
    id: 't1', type: 'expense', accountId: 'acc-efectivo', amountMinor: 100,
    date: '2026-07-22', source: 'manual', groupId: 'g1',
  });
  expect(await d.debts.where('status').equals('pendiente').count()).toBe(1);
  expect(await d.transactions.where('groupId').equals('g1').count()).toBe(1);
});
```

- [ ] **Paso 2: Verificar que fallan.** Run: `npx vitest run src/db/db.test.ts` — Expected: FAIL (`toBe(18)` recibe 15; `d.debts` undefined).

- [ ] **Paso 3: Tipos.** En `src/domain/types.ts`, añadir a `Account` (tras `bncLastSaldoMinor?`):

```ts
  /** Vista 1 del slider de patrimonio. Default al leer: !isSavings (v2). */
  enDisposicion?: boolean;
```

a `Transaction` (tras `rateUsed?`):

```ts
  /** Grupo transaccional (v2). Con groupId, el movimiento se excluye de las
   *  agregaciones y solo cuenta el residuo virtual del grupo. Los saldos NO cambian. */
  groupId?: string;
```

y al final del archivo:

```ts
export type DebtDirection = 'porPagar' | 'porCobrar';
export type DebtStatus = 'pendiente' | 'cerrada';

/** Deuda o cuenta por cobrar (v2). Pendiente NO altera ningún saldo;
 *  el movimiento real nace solo al cerrarla. amountMinor vive en la
 *  moneda de la cuenta asignada (accountId). */
export interface Debt {
  id: string;
  direction: DebtDirection;
  concepto: string;
  amountMinor: number;
  accountId: string;
  status: DebtStatus;
  createdIso: string;
  settledTxId?: string;
  settledIso?: string;
}

/** Grupo de movimientos transaccionales (v2). El residuo es virtual: se deriva, no se persiste. */
export interface TxGroup {
  id: string;
  nota?: string;
  createdIso: string;
}
```

- [ ] **Paso 4: Esquema y seeds.** En `src/db/db.ts`:

```ts
import Dexie, { type Table } from 'dexie';
import type { Account, Category, Debt, Rate, Rule, Settings, Transaction, TxGroup } from '../domain/types';

export class CuentasDB extends Dexie {
  accounts!: Table<Account, string>;
  transactions!: Table<Transaction, string>;
  categories!: Table<Category, string>;
  rates!: Table<Rate, [string, string]>;
  rules!: Table<Rule, string>;
  settings!: Table<Settings, string>;
  debts!: Table<Debt, string>;
  txGroups!: Table<TxGroup, string>;

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
    // v2 aditiva: sin migración de contenido (decisión 7 del spec v2).
    this.version(2).stores({
      transactions: 'id, date, accountId, categoryId, importKey, groupId',
      debts: 'id, status',
      txGroups: 'id',
    });
  }
}
```

Tras `CATEGORIAS_SEED`, añadir:

```ts
/** Seeds v2: se añaden por id si faltan (DB ya sembrada) o junto al seed inicial (DB fresca). */
const CATEGORIAS_SEED_V2: Category[] = [
  { id: 'cat-deudas', name: 'Deudas', icon: '🤝', color: '#c084fc', kind: 'expense' },
  { id: 'cat-vuelto-ingreso', name: 'Vuelto', icon: '↩️', color: '#34d399', kind: 'income' },
  { id: 'cat-vuelto-gasto', name: 'Vuelto', icon: '↪️', color: '#f87171', kind: 'expense' },
];
```

y reemplazar `seedIfEmpty` por:

```ts
/** Crea cuentas, categorías y settings iniciales solo si la tabla está vacía.
 *  Las categorías v2 además se reponen por id si faltan (add-if-missing, nunca pisa ediciones). */
export async function seedIfEmpty(d: CuentasDB): Promise<void> {
  await d.transaction('rw', d.accounts, d.categories, d.settings, async () => {
    if ((await d.accounts.count()) === 0) await d.accounts.bulkAdd(CUENTAS_SEED);
    if ((await d.categories.count()) === 0) {
      await d.categories.bulkAdd([...CATEGORIAS_SEED, ...CATEGORIAS_SEED_V2]);
    } else {
      for (const c of CATEGORIAS_SEED_V2) {
        if (!(await d.categories.get(c.id))) await d.categories.add(c);
      }
    }
    if ((await d.settings.count()) === 0) await d.settings.add(SETTINGS_SEED);
  });
}
```

- [ ] **Paso 5: GREEN.** Run: `npx vitest run` — Expected: TODA la suite verde (los tests de backup siguen en 15 hasta la Tarea 3… **NO**: `backup.test.ts:32` aserta `categories.count()).toBe(15)` sobre una DB sembrada y AHORA fallará con 18). Actualiza en este mismo paso `src/backup/backup.test.ts:31-32`:

```ts
  expect(await destino.accounts.count()).toBe(5);
  expect(await destino.categories.count()).toBe(18);
```

Re-run: `npx vitest run` — Expected: todo verde (≈107 passed).

- [ ] **Paso 6: Commit**

```bash
git add src/domain/types.ts src/db/db.ts src/db/db.test.ts src/backup/backup.test.ts
git commit -m "feat(db): esquema Dexie v2 con deudas, grupos y seeds nuevos

Tablas debts y txGroups, índice groupId en transactions, campos
opcionales enDisposicion/groupId y 3 categorías seed (Deudas, Vuelto
ingreso/gasto) con reposición por id. Sin migración de contenido.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 3: Respaldo v2 (schemaVersion 2)

**Files:**
- Modify: `src/backup/backup.ts`
- Test: `src/backup/backup.test.ts`

**Interfaces:**
- Consumes: tablas `debts`/`txGroups` de la Tarea 2.
- Produces: `BackupFile` con `schemaVersion: 2` y arrays `debts`/`txGroups`; `validateBackup` exige EXACTAMENTE 2 (los v1 se rechazan con mensaje claro). `RespaldoSection` no cambia (consume estas funciones tal cual).

- [ ] **Paso 1: Tests en RED.** En `src/backup/backup.test.ts`: en `dbConDatos()` añadir antes del `return d`:

```ts
  await d.debts.add({
    id: 'deuda-1', direction: 'porCobrar', concepto: 'Ana — almuerzo', amountMinor: 700,
    accountId: 'acc-zinli', status: 'pendiente', createdIso: '2026-07-20T12:00:00.000Z',
  });
  await d.txGroups.add({ id: 'grupo-1', nota: 'leche', createdIso: '2026-07-21T09:00:00.000Z' });
```

En el test de round-trip, añadir al final:

```ts
  expect(await destino.debts.count()).toBe(1);
  expect((await destino.debts.get('deuda-1'))?.concepto).toBe('Ana — almuerzo');
  expect(await destino.txGroups.count()).toBe(1);
```

Reemplazar el test de validación por:

```ts
test('validateBackup rechaza basura, versiones distintas de 2 y tablas faltantes', () => {
  expect(validateBackup(null).ok).toBe(false);
  expect(validateBackup('texto').ok).toBe(false);
  expect(validateBackup({}).ok).toBe(false);
  const casi = {
    schemaVersion: 2, exportedAt: 'x', accounts: [], transactions: [], categories: [],
    rules: [], rates: [], settings: [], debts: [], txGroups: [],
  };
  expect(validateBackup(casi).ok).toBe(true);
  expect(validateBackup({ ...casi, schemaVersion: 3 }).ok).toBe(false);
  expect(validateBackup({ ...casi, debts: undefined }).ok).toBe(false);
  const v1 = validateBackup({ ...casi, schemaVersion: 1 });
  expect(v1.ok).toBe(false);
  if (!v1.ok) expect(v1.error).toContain('versión 1');
});
```

- [ ] **Paso 2: Verificar RED.** Run: `npx vitest run src/backup/backup.test.ts` — Expected: FAIL (schemaVersion 2 rechazada por el código actual).

- [ ] **Paso 3: Implementación.** `src/backup/backup.ts` queda:

```ts
import type { Account, Category, Debt, Rate, Rule, Settings, Transaction, TxGroup } from '../domain/types';
import { CuentasDB } from '../db/db';

export interface BackupFile {
  schemaVersion: 2;
  exportedAt: string; // ISO completo
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  rules: Rule[];
  rates: Rate[];
  settings: Settings[];
  debts: Debt[];
  txGroups: TxGroup[];
}

const TABLAS = ['accounts', 'transactions', 'categories', 'rules', 'rates', 'settings', 'debts', 'txGroups'] as const;

export async function exportBackup(d: CuentasDB): Promise<BackupFile> {
  const [accounts, transactions, categories, rules, rates, settings, debts, txGroups] = await Promise.all([
    d.accounts.toArray(),
    d.transactions.toArray(),
    d.categories.toArray(),
    d.rules.toArray(),
    d.rates.toArray(),
    d.settings.toArray(),
    d.debts.toArray(),
    d.txGroups.toArray(),
  ]);
  return {
    schemaVersion: 2, exportedAt: new Date().toISOString(),
    accounts, transactions, categories, rules, rates, settings, debts, txGroups,
  };
}

export function validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string } {
  if (typeof data !== 'object' || data === null) return { ok: false, error: 'El archivo no es un respaldo de Cuentas Claras' };
  const b = data as Partial<BackupFile>;
  if ((b.schemaVersion as number) === 1) {
    return { ok: false, error: 'Este respaldo es de la versión 1 y la v2 no es compatible: re-ingresa o re-importa tus datos' };
  }
  if (b.schemaVersion !== 2) return { ok: false, error: `Versión de respaldo no soportada: ${String(b.schemaVersion)}` };
  for (const tabla of TABLAS) {
    if (!Array.isArray(b[tabla])) return { ok: false, error: `Al respaldo le falta la tabla "${tabla}"` };
  }
  return { ok: true, backup: b as BackupFile };
}

/** Importar reemplaza TODO el contenido, en una sola transacción (todo-o-nada). */
export async function importBackup(d: CuentasDB, b: BackupFile): Promise<void> {
  await d.transaction('rw', [d.accounts, d.transactions, d.categories, d.rules, d.rates, d.settings, d.debts, d.txGroups], async () => {
    await Promise.all(TABLAS.map((t) => d[t].clear()));
    await d.accounts.bulkAdd(b.accounts);
    await d.transactions.bulkAdd(b.transactions);
    await d.categories.bulkAdd(b.categories);
    await d.rules.bulkAdd(b.rules);
    await d.rates.bulkAdd(b.rates);
    await d.settings.bulkAdd(b.settings);
    await d.debts.bulkAdd(b.debts);
    await d.txGroups.bulkAdd(b.txGroups);
  });
}

export function backupFilename(dateIso: string): string {
  return `cuentas-claras-respaldo-${dateIso}.json`;
}
```

- [ ] **Paso 4: GREEN.** Run: `npx vitest run` — Expected: todo verde.

- [ ] **Paso 5: Commit**

```bash
git add src/backup/backup.ts src/backup/backup.test.ts
git commit -m "feat(respaldo): schemaVersion 2 con deudas y grupos; rechaza respaldos v1

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### GATE Fase 0 (bloqueante)

- [ ] Run: `npx vitest run` — Expected: toda la suite verde.
- [ ] Run: `npm run build` — Expected: sin errores (el warning consultivo de chunk ~742 kB está aceptado y documentado).

---

## Fase 1 — Deudas y cuentas por cobrar

### Tarea 4: Dominio de deudas (TDD)

**REQUIRED SUB-SKILL:** `superpowers:test-driven-development`.

**Files:**
- Create: `src/domain/debts.ts`, `src/domain/debts.test.ts`
- Create: `src/db/debtOps.ts`, `src/db/debtOps.test.ts`

**Interfaces:**
- Consumes: `Debt`, `Transaction`, `Account` (Tarea 2); `vesToUsdMinor` de `domain/rates.ts`; `CuentasDB`.
- Produces (las tareas 5-7 dependen de las firmas EXACTAS):
  - `buildDebt(opts: { direction: DebtDirection; concepto: string; amountMinor: number; accountId: string; nowIso: string }): Debt` — valida y arma una pendiente.
  - `buildSettleTx(debt: Debt, dateIso: string): Transaction` — el movimiento de cierre (puro).
  - `debtTotals(deudasPendientes: Debt[], accounts: Account[], vesPerUsd: number | null): { debesUsdMinor: number | null; teDebenUsdMinor: number | null }`.
  - `settleDebt(d: CuentasDB, debtId: string, dateIso: string): Promise<void>` — cierre transaccional e idempotente.

- [ ] **Paso 1: Tests en RED** — `src/domain/debts.test.ts`:

```ts
import { buildDebt, buildSettleTx, debtTotals } from './debts';
import type { Account, Debt } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
];

function deuda(parcial: Partial<Debt>): Debt {
  return {
    id: 'd1', direction: 'porPagar', concepto: 'Juan — préstamo', amountMinor: 500,
    accountId: 'acc-efectivo', status: 'pendiente', createdIso: '2026-07-22T10:00:00.000Z',
    ...parcial,
  };
}

test('buildDebt arma una pendiente válida', () => {
  const d = buildDebt({
    direction: 'porCobrar', concepto: 'Ana — almuerzo', amountMinor: 700,
    accountId: 'acc-efectivo', nowIso: '2026-07-22T10:00:00.000Z',
  });
  expect(d).toMatchObject({
    direction: 'porCobrar', concepto: 'Ana — almuerzo', amountMinor: 700,
    accountId: 'acc-efectivo', status: 'pendiente', createdIso: '2026-07-22T10:00:00.000Z',
  });
  expect(d.id).toBeTruthy();
});

test('buildDebt rechaza monto no entero, cero o negativo y concepto vacío', () => {
  const base = { direction: 'porPagar' as const, accountId: 'acc-efectivo', nowIso: 'x' };
  expect(() => buildDebt({ ...base, concepto: 'a', amountMinor: 0 })).toThrow('Monto inválido');
  expect(() => buildDebt({ ...base, concepto: 'a', amountMinor: -5 })).toThrow('Monto inválido');
  expect(() => buildDebt({ ...base, concepto: 'a', amountMinor: 1.5 })).toThrow('Monto inválido');
  expect(() => buildDebt({ ...base, concepto: '   ', amountMinor: 100 })).toThrow('concepto');
});

test('buildSettleTx: por pagar → gasto con categoría Deudas y nota exacta', () => {
  const tx = buildSettleTx(deuda({ direction: 'porPagar' }), '2026-07-22');
  expect(tx).toMatchObject({
    type: 'expense', accountId: 'acc-efectivo', amountMinor: 500, date: '2026-07-22',
    categoryId: 'cat-deudas', note: 'Deuda pagada: Juan — préstamo', source: 'manual',
  });
});

test('buildSettleTx: por cobrar → ingreso con Transferencia recibida y nota exacta', () => {
  const tx = buildSettleTx(deuda({ direction: 'porCobrar', concepto: 'Ana' }), '2026-07-22');
  expect(tx).toMatchObject({
    type: 'income', categoryId: 'cat-transferencia-recibida', note: 'Deuda cobrada: Ana',
  });
});

test('buildSettleTx: el monto queda en la moneda de la cuenta, sin conversión', () => {
  const tx = buildSettleTx(deuda({ accountId: 'acc-bnc', amountMinor: 1351775 }), '2026-07-22');
  expect(tx.amountMinor).toBe(1351775); // céntimos de Bs, intactos
});

test('buildSettleTx rechaza deudas ya cerradas', () => {
  expect(() => buildSettleTx(deuda({ status: 'cerrada' }), '2026-07-22')).toThrow('cerrada');
});

test('debtTotals consolida a USD por dirección con la tasa dada', () => {
  const deudas = [
    deuda({ id: 'a', direction: 'porPagar', accountId: 'acc-efectivo', amountMinor: 500 }),   // $5.00
    deuda({ id: 'b', direction: 'porPagar', accountId: 'acc-bnc', amountMinor: 73723 }),      // Bs 737,23 → $1.00 a 737,23
    deuda({ id: 'c', direction: 'porCobrar', accountId: 'acc-efectivo', amountMinor: 700 }),  // $7.00
  ];
  const t = debtTotals(deudas, CUENTAS, 737.23);
  expect(t.debesUsdMinor).toBe(600);
  expect(t.teDebenUsdMinor).toBe(700);
});

test('debtTotals: deuda en Bs sin tasa → total null; sin deudas → 0', () => {
  const conBs = [deuda({ accountId: 'acc-bnc' })];
  expect(debtTotals(conBs, CUENTAS, null).debesUsdMinor).toBeNull();
  expect(debtTotals([], CUENTAS, null)).toEqual({ debesUsdMinor: 0, teDebenUsdMinor: 0 });
});
```

y `src/db/debtOps.test.ts`:

```ts
import { CuentasDB, seedIfEmpty } from './db';
import { settleDebt } from './debtOps';
import { buildDebt } from '../domain/debts';

async function dbConDeuda() {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  const deuda = buildDebt({
    direction: 'porPagar', concepto: 'Juan — préstamo', amountMinor: 500,
    accountId: 'acc-efectivo', nowIso: '2026-07-22T10:00:00.000Z',
  });
  await d.debts.add(deuda);
  return { d, deudaId: deuda.id };
}

test('settleDebt crea el movimiento y cierra la deuda con settledTxId', async () => {
  const { d, deudaId } = await dbConDeuda();
  await settleDebt(d, deudaId, '2026-07-22');
  const txs = await d.transactions.toArray();
  expect(txs).toHaveLength(1);
  expect(txs[0]).toMatchObject({
    type: 'expense', accountId: 'acc-efectivo', amountMinor: 500,
    categoryId: 'cat-deudas', note: 'Deuda pagada: Juan — préstamo',
  });
  const cerrada = await d.debts.get(deudaId);
  expect(cerrada?.status).toBe('cerrada');
  expect(cerrada?.settledTxId).toBe(txs[0].id);
  expect(cerrada?.settledIso).toBe('2026-07-22');
});

test('settleDebt es idempotente: la segunda llamada no crea otro movimiento', async () => {
  const { d, deudaId } = await dbConDeuda();
  await settleDebt(d, deudaId, '2026-07-22');
  await settleDebt(d, deudaId, '2026-07-23');
  expect(await d.transactions.count()).toBe(1);
  expect((await d.debts.get(deudaId))?.settledIso).toBe('2026-07-22'); // la primera manda
});

test('settleDebt lanza si la deuda no existe', async () => {
  const { d } = await dbConDeuda();
  await expect(settleDebt(d, 'no-existe', '2026-07-22')).rejects.toThrow('no existe');
});
```

- [ ] **Paso 2: Verificar RED.** Run: `npx vitest run src/domain/debts.test.ts src/db/debtOps.test.ts` — Expected: FAIL (módulos inexistentes).

- [ ] **Paso 3: Implementación.** `src/domain/debts.ts`:

```ts
import type { Account, Debt, DebtDirection, Transaction } from './types';
import { vesToUsdMinor } from './rates';

export function buildDebt(opts: {
  direction: DebtDirection;
  concepto: string;
  amountMinor: number;
  accountId: string;
  nowIso: string;
}): Debt {
  if (!Number.isInteger(opts.amountMinor) || opts.amountMinor <= 0) throw new Error('Monto inválido');
  if (!opts.concepto.trim()) throw new Error('Ponle un concepto a la deuda');
  return {
    id: crypto.randomUUID(),
    direction: opts.direction,
    concepto: opts.concepto.trim(),
    amountMinor: opts.amountMinor,
    accountId: opts.accountId,
    status: 'pendiente',
    createdIso: opts.nowIso,
  };
}

/** Movimiento real de cierre: gasto (por pagar) o ingreso (por cobrar),
 *  en la moneda de la cuenta asignada, SIN conversión (decisión 2 del spec v2). */
export function buildSettleTx(debt: Debt, dateIso: string): Transaction {
  if (debt.status !== 'pendiente') throw new Error('La deuda ya está cerrada');
  const porPagar = debt.direction === 'porPagar';
  return {
    id: crypto.randomUUID(),
    type: porPagar ? 'expense' : 'income',
    accountId: debt.accountId,
    amountMinor: debt.amountMinor,
    date: dateIso,
    categoryId: porPagar ? 'cat-deudas' : 'cat-transferencia-recibida',
    note: `${porPagar ? 'Deuda pagada' : 'Deuda cobrada'}: ${debt.concepto}`,
    source: 'manual',
  };
}

export interface TotalesDeudas {
  debesUsdMinor: number | null;
  teDebenUsdMinor: number | null;
}

/** Totales de pendientes consolidados a USD SOLO para mostrar (spec v2 §1).
 *  Deuda en Bs sin tasa → el total de su dirección queda null. */
export function debtTotals(deudasPendientes: Debt[], accounts: Account[], vesPerUsd: number | null): TotalesDeudas {
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const sumar = (deudas: Debt[]): number | null => {
    let total = 0;
    for (const deuda of deudas) {
      const moneda = monedaDe.get(deuda.accountId) ?? 'USD';
      if (moneda === 'USD') total += deuda.amountMinor;
      else if (vesPerUsd === null) return null;
      else total += vesToUsdMinor(deuda.amountMinor, vesPerUsd);
    }
    return total;
  };
  return {
    debesUsdMinor: sumar(deudasPendientes.filter((deuda) => deuda.direction === 'porPagar')),
    teDebenUsdMinor: sumar(deudasPendientes.filter((deuda) => deuda.direction === 'porCobrar')),
  };
}
```

`src/db/debtOps.ts`:

```ts
import { CuentasDB } from './db';
import { buildSettleTx } from '../domain/debts';

/** Cierra la deuda creando su movimiento real, todo-o-nada e idempotente:
 *  si ya está cerrada, no hace nada (guard dentro de la transacción). */
export async function settleDebt(d: CuentasDB, debtId: string, dateIso: string): Promise<void> {
  await d.transaction('rw', d.debts, d.transactions, async () => {
    const debt = await d.debts.get(debtId);
    if (!debt) throw new Error('La deuda no existe');
    if (debt.status !== 'pendiente') return;
    const tx = buildSettleTx(debt, dateIso);
    await d.transactions.add(tx);
    await d.debts.update(debtId, { status: 'cerrada', settledTxId: tx.id, settledIso: dateIso });
  });
}
```

- [ ] **Paso 4: GREEN.** Run: `npx vitest run` — Expected: todo verde.

- [ ] **Paso 5: Commit**

```bash
git add src/domain/debts.ts src/domain/debts.test.ts src/db/debtOps.ts src/db/debtOps.test.ts
git commit -m "feat(deudas): dominio de deudas con cierre transaccional idempotente

buildDebt/buildSettleTx/debtTotals puros + settleDebt todo-o-nada.
La deuda vive en la moneda de su cuenta; el movimiento nace al cerrarla.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 5: Vista de gestión de deudas (`#/deudas`)

**Files:**
- Create: `src/ui/paginas/DeudasPage.tsx`
- Modify: `src/ui/App.tsx` (ruta)

**Interfaces:**
- Consumes: `buildDebt`, `settleDebt`, `formatMoney`, `parseVenezuelanAmount`, `todayIso`, `avisar`, tablas `db.debts`/`db.accounts`.
- Produces: ruta `#/deudas` funcional (la Tarea 6 enlaza aquí desde la tarjeta de Inicio).

- [ ] **Paso 1: Ruta.** En `src/ui/App.tsx`, añadir el import y la ruta junto a las existentes:

```tsx
import { DeudasPage } from './paginas/DeudasPage';
// ... dentro de <main className="contenido">:
        {path === '/deudas' && <DeudasPage />}
```

- [ ] **Paso 2: Crear `src/ui/paginas/DeudasPage.tsx`** (completo):

```tsx
import { useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Debt, DebtDirection } from '../../domain/types';
import { buildDebt } from '../../domain/debts';
import { settleDebt } from '../../db/debtOps';
import { formatMoney, parseVenezuelanAmount } from '../../domain/money';
import { todayIso } from '../../domain/dates';
import { avisar } from '../Toast';

export function DeudasPage() {
  const [pestana, setPestana] = useState<DebtDirection>('porPagar');
  const [editando, setEditando] = useState<Debt | null>(null);
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const deudas = useLiveQuery(() => db.debts.toArray(), []) ?? [];
  const [guardando, setGuardando] = useState(false);

  const monedaDe = (accountId: string) => cuentas.find((c) => c.id === accountId)?.currency ?? 'USD';
  const nombreCuenta = (accountId: string) => cuentas.find((c) => c.id === accountId)?.name ?? '?';
  const delaPestana = deudas.filter((deuda) => deuda.direction === pestana);
  const pendientes = delaPestana.filter((deuda) => deuda.status === 'pendiente')
    .sort((a, b) => b.createdIso.localeCompare(a.createdIso));
  const cerradas = delaPestana.filter((deuda) => deuda.status === 'cerrada')
    .sort((a, b) => (b.settledIso ?? '').localeCompare(a.settledIso ?? ''));

  async function marcarCerrada(deuda: Debt) {
    if (guardando) return;
    const verbo = deuda.direction === 'porPagar' ? 'pagada' : 'cobrada';
    const efecto = deuda.direction === 'porPagar' ? 'el gasto' : 'el ingreso';
    if (!window.confirm(`¿Marcar «${deuda.concepto}» como ${verbo}? Se creará ${efecto} en ${nombreCuenta(deuda.accountId)}.`)) return;
    setGuardando(true);
    try {
      await settleDebt(db, deuda.id, todayIso());
      avisar(`Deuda ${verbo} ✓`);
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar(deuda: Debt) {
    if (guardando) return;
    if (!window.confirm(deuda.status === 'cerrada'
      ? '¿Eliminar esta deuda cerrada? Su movimiento NO se borra.'
      : '¿Eliminar esta deuda pendiente?')) return;
    setGuardando(true);
    try {
      await db.debts.delete(deuda.id);
      avisar('Deuda eliminada');
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  const NUEVA = (): Debt => buildDebt({
    direction: pestana, concepto: 'nueva', amountMinor: 1,
    accountId: cuentas[0]?.id ?? '', nowIso: new Date().toISOString(),
  });

  return (
    <div>
      <h2>Deudas</h2>
      <div className="segmentado">
        <button className={pestana === 'porPagar' ? 'activo' : ''} onClick={() => setPestana('porPagar')}>Por pagar</button>
        <button className={pestana === 'porCobrar' ? 'activo' : ''} onClick={() => setPestana('porCobrar')}>Por cobrar</button>
      </div>
      {pestana === 'porCobrar' && (
        <p style={{ fontSize: 12, color: 'var(--texto2)' }}>
          Lo que te deben es informativo: no suma al patrimonio hasta cobrarse.
        </p>
      )}
      {pendientes.length === 0 && (
        <div className="vacio"><span className="icono">🤝</span>Sin deudas {pestana === 'porPagar' ? 'por pagar' : 'por cobrar'}.</div>
      )}
      {pendientes.map((deuda) => (
        <div key={deuda.id} className="tarjeta" style={{ marginTop: 8 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>{deuda.concepto}</span>
            <span className="cifra">{formatMoney(deuda.amountMinor, monedaDe(deuda.accountId))}</span>
          </div>
          <p style={{ margin: '4px 0', fontSize: 12, color: 'var(--texto2)' }}>
            {nombreCuenta(deuda.accountId)} · registrada el {deuda.createdIso.slice(0, 10)}
          </p>
          <div className="fila-form" style={{ marginTop: 8 }}>
            <button className="boton secundario" onClick={() => setEditando(deuda)} disabled={guardando}>Editar</button>
            <button className="boton peligro" onClick={() => eliminar(deuda)} disabled={guardando}>Eliminar</button>
            <button className="boton" onClick={() => marcarCerrada(deuda)} disabled={guardando}>
              {deuda.direction === 'porPagar' ? 'Marcar pagada' : 'Marcar cobrada'}
            </button>
          </div>
        </div>
      ))}
      <button className="boton secundario" style={{ marginTop: 12 }} onClick={() => cuentas.length ? setEditando(NUEVA()) : avisar('No hay cuentas')}>
        Nueva deuda {pestana === 'porPagar' ? 'por pagar' : 'por cobrar'}
      </button>

      {cerradas.length > 0 && (
        <details style={{ marginTop: 16 }}>
          <summary>Historial ({cerradas.length})</summary>
          {cerradas.map((deuda) => (
            <div key={deuda.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', color: 'var(--texto2)' }}>
              <span>{deuda.concepto} · {deuda.settledIso}</span>
              <span>
                <span className="cifra">{formatMoney(deuda.amountMinor, monedaDe(deuda.accountId))}</span>
                <button className="chip" style={{ marginLeft: 6 }} aria-label={`Eliminar ${deuda.concepto}`} onClick={() => eliminar(deuda)}>eliminar</button>
              </span>
            </div>
          ))}
        </details>
      )}

      {editando && (
        <EditorDeuda
          original={deudas.some((deuda) => deuda.id === editando.id) ? editando : null}
          borrador={editando}
          cerrar={() => setEditando(null)}
        />
      )}
    </div>
  );
}

function EditorDeuda({ original, borrador, cerrar }: { original: Debt | null; borrador: Debt; cerrar: () => void }) {
  const cuentas = useLiveQuery(() => db.accounts.filter((a) => !a.archived).toArray(), []) ?? [];
  const esNueva = original === null;
  const [concepto, setConcepto] = useState(esNueva ? '' : borrador.concepto);
  const [cuentaId, setCuentaId] = useState(borrador.accountId);
  const [direction, setDirection] = useState<DebtDirection>(borrador.direction);
  const [montoTxt, setMontoTxt] = useState(esNueva ? '' : (borrador.amountMinor / 100).toFixed(2).replace('.', ','));
  const [guardando, setGuardando] = useState(false);
  const moneda = cuentas.find((c) => c.id === cuentaId)?.currency ?? 'USD';

  function cambiarCuenta(nuevaId: string) {
    const monedaNueva = cuentas.find((c) => c.id === nuevaId)?.currency ?? 'USD';
    if (monedaNueva !== moneda && montoTxt !== '') {
      setMontoTxt('');
      avisar('La cuenta elegida usa otra moneda: vuelve a escribir el monto');
    }
    setCuentaId(nuevaId);
  }

  async function guardar() {
    if (guardando) return;
    const minor = parseVenezuelanAmount(montoTxt.replace(/,$/, ''));
    if (minor === null || minor <= 0) { avisar('Monto inválido'); return; }
    if (!concepto.trim()) { avisar('Ponle un concepto a la deuda'); return; }
    if (!cuentas.some((c) => c.id === cuentaId)) { avisar('Elige una cuenta'); return; }
    setGuardando(true);
    try {
      if (esNueva) {
        await db.debts.add(buildDebt({
          direction, concepto, amountMinor: minor, accountId: cuentaId, nowIso: new Date().toISOString(),
        }));
      } else {
        await db.debts.update(borrador.id, {
          direction, concepto: concepto.trim(), amountMinor: minor, accountId: cuentaId,
        });
      }
      avisar('Deuda guardada ✓');
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
        <h3>{esNueva ? 'Nueva deuda' : 'Editar deuda'}</h3>
        <div className="segmentado">
          <button className={direction === 'porPagar' ? 'activo' : ''} onClick={() => setDirection('porPagar')}>Debo</button>
          <button className={direction === 'porCobrar' ? 'activo' : ''} onClick={() => setDirection('porCobrar')}>Me deben</button>
        </div>
        <label>Concepto (persona o descripción)</label>
        <input aria-label="Concepto" value={concepto} onChange={(e) => setConcepto(e.target.value)} placeholder="Juan — préstamo almuerzo" />
        <label>Cuenta para {direction === 'porPagar' ? 'pagarla' : 'cobrarla'} (define la moneda)</label>
        <select aria-label="Cuenta de la deuda" value={cuentaId} onChange={(e) => cambiarCuenta(e.target.value)}>
          {cuentas.map((c) => (
            <option key={c.id} value={c.id}>{c.name} ({c.currency === 'VES' ? 'Bs' : '$'})</option>
          ))}
        </select>
        <label>Monto en {moneda === 'VES' ? 'Bs' : '$'} (coma decimal)</label>
        <input aria-label="Monto de la deuda" inputMode="decimal" value={montoTxt} onChange={(e) => setMontoTxt(e.target.value)} placeholder="0,00" />
        <div className="fila-form" style={{ marginTop: 12 }}>
          <button className="boton secundario" onClick={cerrar} disabled={guardando}>Cancelar</button>
          <button className="boton" onClick={guardar} disabled={guardando}>Guardar deuda</button>
        </div>
      </div>
    </div>
  );
}
```

Nota de implementación: el `NUEVA()` de arriba crea un borrador técnico con valores mínimos válidos solo para abrir el editor; el editor arranca con concepto y monto vacíos cuando `original === null`. Los movimientos de cierre aparecen en Movimientos como cualquier otro; las deudas en sí NO aparecen allí ni en métricas (spec §1).

- [ ] **Paso 3: Verificación visual bloqueante (headless).** Con dev server propio y la receta:
  1. Navega a `#/deudas` → captura del estado vacío por pestaña.
  2. Crea una deuda «Juan — préstamo» $5,00 en Efectivo (Debo) → aparece en «Por pagar» con `$5.00`.
  3. Edita: cambia la cuenta a BNC (Bs) → el monto se vacía y sale el toast de moneda; escribe `3686,15` y guarda.
  4. «Marcar pagada» (acepta el confirm con `page.on('dialog')`) → la deuda pasa al historial plegado y en `#/movimientos` existe el gasto `Deuda pagada: Juan — préstamo` en BNC.
  5. Crea una por cobrar, márcala cobrada → ingreso con nota `Deuda cobrada: …` y categoría Transferencia recibida.
  6. Elimina la cerrada → el movimiento sigue existiendo en Movimientos.
  Capturas de cada estado a `.superpowers/sdd/capturas/task-5/`. Mata el dev server.

- [ ] **Paso 4: Suite verde.** Run: `npx vitest run` — Expected: todo verde.

- [ ] **Paso 5: Commit**

```bash
git add src/ui/paginas/DeudasPage.tsx src/ui/App.tsx
git commit -m "feat(deudas): vista de gestión con pestañas, alta, cierre y historial

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 6: Tarjeta «Deudas» en Inicio

**Files:**
- Create: `src/ui/componentes/DeudasCard.tsx`
- Modify: `src/ui/paginas/DashboardPage.tsx`

**Interfaces:**
- Consumes: `debtTotals` (Tarea 4), `formatMoney`, `usdToVesMinor`, tipos `Debt`/`Account`/`Rate`/`Currency`.
- Produces: `DeudasCard({ deudas, cuentas, tasa, viewCurrency })` bajo la tarjeta de patrimonio.

- [ ] **Paso 1: Crear `src/ui/componentes/DeudasCard.tsx`:**

```tsx
import type { Account, Currency, Debt, Rate } from '../../domain/types';
import { debtTotals } from '../../domain/debts';
import { formatMoney } from '../../domain/money';
import { usdToVesMinor } from '../../domain/rates';

export function DeudasCard({ deudas, cuentas, tasa, viewCurrency }: {
  deudas: Debt[];
  cuentas: Account[];
  tasa: Rate | null;
  viewCurrency: Currency;
}) {
  const pendientes = deudas.filter((deuda) => deuda.status === 'pendiente');
  const totales = debtTotals(pendientes, cuentas, tasa?.vesPerUsd ?? null);
  const monedaDe = (accountId: string) => cuentas.find((c) => c.id === accountId)?.currency ?? 'USD';

  const enVista = (usdMinor: number | null): string => {
    if (usdMinor === null) return 'sin tasa';
    if (viewCurrency === 'USD') return formatMoney(usdMinor, 'USD');
    if (!tasa) return 'sin tasa';
    return formatMoney(usdToVesMinor(usdMinor, tasa.vesPerUsd), 'VES');
  };

  const recientes = [...pendientes].sort((a, b) => b.createdIso.localeCompare(a.createdIso)).slice(0, 3);

  return (
    <div className="tarjeta" style={{ marginTop: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>Deudas</h3>
        <a className="chip" href="#/deudas">ver todas</a>
      </div>
      <p style={{ margin: '8px 0 4px', color: 'var(--texto2)' }}>
        Debes <strong className="cifra">{enVista(totales.debesUsdMinor)}</strong>
        {' · '}Te deben <strong className="cifra">{enVista(totales.teDebenUsdMinor)}</strong>
      </p>
      {recientes.length === 0 ? (
        <p style={{ margin: '4px 0', fontSize: 12, color: 'var(--texto2)' }}>Sin deudas pendientes.</p>
      ) : (
        recientes.map((deuda) => (
          <div key={deuda.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
            <span>{deuda.direction === 'porPagar' ? '📤' : '📥'} {deuda.concepto}</span>
            <span className="cifra">{formatMoney(deuda.amountMinor, monedaDe(deuda.accountId))}</span>
          </div>
        ))
      )}
    </div>
  );
}
```

- [ ] **Paso 2: Integrar en `DashboardPage.tsx`.** Añadir el `useLiveQuery` de deudas JUNTO a los existentes (antes del guard `if (!settings)` — regla de hooks) y la tarjeta bajo `PatrimonioCard`:

```tsx
import { DeudasCard } from '../componentes/DeudasCard';
// junto a los otros useLiveQuery:
  const deudas = useLiveQuery(() => db.debts.toArray(), []) ?? [];
// en el JSX, entre <PatrimonioCard …/> y <GraficasMes …/>:
      <DeudasCard deudas={deudas} cuentas={cuentas} tasa={tasa} viewCurrency={settings.viewCurrency} />
```

- [ ] **Paso 3: Verificación visual bloqueante.** Headless: con una deuda por pagar ($5) y una por cobrar ($7) sembradas, Inicio muestra «Debes $5.00 · Te deben $7.00», las recientes, y el chip «ver todas» navega a `#/deudas`. Toggle a Bs → totales en Bs. «Te deben» NO altera la cifra de patrimonio. Capturas a `.superpowers/sdd/capturas/task-6/`. Mata el dev server.

- [ ] **Paso 4: Suite + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/ui/componentes/DeudasCard.tsx src/ui/paginas/DashboardPage.tsx
git commit -m "feat(deudas): tarjeta de totales y recientes en Inicio

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### GATE Fase 1 (bloqueante)

- [ ] Run: `npx vitest run` — Expected: verde. Run: `npm run build` — Expected: sin errores.

---

## Fase 2 — Patrimonio en 3 vistas

### Tarea 7: `patrimonio()` con «A disposición» (flag) y «Total neto» (TDD)

**REQUIRED SUB-SKILL:** `superpowers:test-driven-development`.

**Files:**
- Modify: `src/domain/balances.ts`
- Test: `src/domain/balances.test.ts`

**Interfaces:**
- Consumes: `Debt` (Tarea 2), `vesToUsdMinor`.
- Produces: `patrimonio(accounts, txs, vesPerUsd, deudasPorPagarPendientes?: Debt[])` — 4.º parámetro opcional (default `[]`, los llamadores v1 y sus tests siguen compilando) — y dos campos nuevos en `Patrimonio`:
  - `aDisposicionUsdMinor: number | null` — Σ cuentas con `enDisposicion ?? !isSavings`.
  - `netoUsdMinor: number | null` — `totalUsdMinor` − Σ deudas por pagar pendientes convertidas a USD.

- [ ] **Paso 1: Tests en RED.** Añadir a `src/domain/balances.test.ts` (usa los helpers/estilo ya existentes en ese archivo; crea cuentas literales si no hay helper):

```ts
import type { Debt } from './types';

const deudaPendiente = (parcial: Partial<Debt>): Debt => ({
  id: 'd1', direction: 'porPagar', concepto: 'x', amountMinor: 500,
  accountId: 'usd1', status: 'pendiente', createdIso: '2026-07-22T00:00:00.000Z',
  ...parcial,
});

test('aDisposicionUsdMinor usa el flag enDisposicion con default !isSavings', () => {
  const cuentas = [
    { id: 'usd1', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },              // sin flag → cuenta
    { id: 'usd2', name: 'Binance', currency: 'USD', kind: 'snapshot', isSavings: true, archived: false, enDisposicion: true },  // ahorro pero marcada → cuenta
    { id: 'usd3', name: 'Guardado', currency: 'USD', kind: 'cash', isSavings: false, archived: false, enDisposicion: false }, // no-ahorro desmarcada → NO cuenta
  ] as const;
  const txs = [
    { id: 't1', type: 'income', accountId: 'usd1', amountMinor: 1000, date: '2026-07-01', source: 'manual' },
    { id: 't2', type: 'income', accountId: 'usd2', amountMinor: 2000, date: '2026-07-01', source: 'manual' },
    { id: 't3', type: 'income', accountId: 'usd3', amountMinor: 4000, date: '2026-07-01', source: 'manual' },
  ];
  const p = patrimonio([...cuentas] as Account[], txs as Transaction[], null);
  expect(p.aDisposicionUsdMinor).toBe(3000); // usd1 + usd2
  expect(p.totalUsdMinor).toBe(7000);        // el total actual no cambia
});

test('netoUsdMinor descuenta las deudas por pagar convertidas con la tasa', () => {
  const cuentas: Account[] = [
    { id: 'usd1', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
    { id: 'bs1', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  ];
  const txs: Transaction[] = [
    { id: 't1', type: 'income', accountId: 'usd1', amountMinor: 10000, date: '2026-07-01', source: 'manual' },
  ];
  const deudas = [
    deudaPendiente({ id: 'a', accountId: 'usd1', amountMinor: 500 }),        // $5.00
    deudaPendiente({ id: 'b', accountId: 'bs1', amountMinor: 73723 }),       // Bs 737,23 → $1.00
  ];
  const p = patrimonio(cuentas, txs, 737.23, deudas);
  expect(p.netoUsdMinor).toBe(10000 - 600);
});

test('netoUsdMinor: sin deudas es igual al total; deuda en Bs sin tasa → null', () => {
  const cuentas: Account[] = [
    { id: 'usd1', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
    { id: 'bs1', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  ];
  const txs: Transaction[] = [
    { id: 't1', type: 'income', accountId: 'usd1', amountMinor: 10000, date: '2026-07-01', source: 'manual' },
  ];
  const soloUsd = patrimonio([cuentas[0]], txs, null);
  expect(soloUsd.netoUsdMinor).toBe(soloUsd.totalUsdMinor);
  const conBsSinTasa = patrimonio([cuentas[0]], txs, null, [deudaPendiente({ accountId: 'bs1' })]);
  expect(conBsSinTasa.netoUsdMinor).toBeNull();
});
```

(Ajusta los imports de `Account`/`Transaction` al encabezado ya presente en el archivo de test.)

- [ ] **Paso 2: RED.** Run: `npx vitest run src/domain/balances.test.ts` — Expected: FAIL.

- [ ] **Paso 3: Implementación.** En `src/domain/balances.ts`: importar `Debt` y `vesToUsdMinor` ya está importado; ampliar la interfaz y la función:

```ts
import type { Account, Debt, Transaction } from './types';
// interfaz:
export interface Patrimonio {
  lines: PatrimonioLinea[];
  disposicionUsdMinor: number | null;
  ahorroUsdMinor: number | null;
  totalUsdMinor: number | null;
  /** v2 — vista 1 del slider: cuentas con enDisposicion (default !isSavings). */
  aDisposicionUsdMinor: number | null;
  /** v2 — vista 2: total − deudas por pagar pendientes (convertidas a USD). */
  netoUsdMinor: number | null;
}

export function patrimonio(
  accounts: Account[],
  txs: Transaction[],
  vesPerUsd: number | null,
  deudasPorPagarPendientes: Debt[] = [],
): Patrimonio {
  // … cuerpo v1 intacto hasta calcular totalUsdMinor, y antes del return añadir:

  const aDisposicion = lines.filter((l) => l.account.enDisposicion ?? !l.account.isSavings);
  const aDisposicionUsdMinor = suma(aDisposicion);

  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  let deudasUsdMinor: number | null = 0;
  for (const deuda of deudasPorPagarPendientes) {
    const moneda = monedaDe.get(deuda.accountId) ?? 'USD';
    if (moneda === 'USD') deudasUsdMinor += deuda.amountMinor;
    else if (vesPerUsd === null) { deudasUsdMinor = null; break; }
    else deudasUsdMinor += vesToUsdMinor(deuda.amountMinor, vesPerUsd);
  }
  const netoUsdMinor =
    totalUsdMinor === null || deudasUsdMinor === null ? null : totalUsdMinor - deudasUsdMinor;

  return { lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor, aDisposicionUsdMinor, netoUsdMinor };
}
```

(El filtro de las deudas por dirección/status lo hace el LLAMADOR — la firma recibe ya las «por pagar pendientes»; documentado en el nombre del parámetro.)

- [ ] **Paso 4: GREEN + commit**

Run: `npx vitest run` — Expected: verde (los tests v1 de patrimonio no se tocan).

```bash
git add src/domain/balances.ts src/domain/balances.test.ts
git commit -m "feat(patrimonio): vistas a disposición (flag) y total neto con deudas

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 8: Slider de patrimonio + checkbox «a disposición» en cuentas

**Files:**
- Modify: `src/ui/componentes/PatrimonioCard.tsx`
- Modify: `src/ui/componentes/CuentasSection.tsx`
- Modify: `src/ui/paginas/DashboardPage.tsx`
- Modify: `src/ui/estilos.css`

**Interfaces:**
- Consumes: `Patrimonio` con los campos nuevos (Tarea 7); deudas ya cargadas en Dashboard (Tarea 6).
- Produces: slider CSS `scroll-snap` de 3 vistas con puntos indicadores; campo editable `enDisposicion` en el editor de cuentas.

- [ ] **Paso 1: CSS.** Añadir al final de `src/ui/estilos.css` (solo tokens existentes):

```css
/* Slider de patrimonio (v2): 3 vistas con scroll-snap, sin librerías */
.slider-patrimonio {
  display: flex;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  margin: 8px 0 4px;
}
.slider-patrimonio::-webkit-scrollbar { display: none; }
.slider-patrimonio .vista {
  flex: 0 0 100%;
  scroll-snap-align: center;
  text-align: center;
}
.slider-patrimonio .vista .titulo-vista {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--texto2);
}
.slider-puntos {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin-bottom: 6px;
}
.slider-puntos span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--borde);
  transition: background var(--transicion);
}
.slider-puntos span.activo { background: var(--primario); }
```

- [ ] **Paso 2: PatrimonioCard.** Reemplazar la cabecera de cifra y la línea «A disposición / Ahorro» por el slider (el desglose por cuentas y la línea de tasa QUEDAN IGUAL):

```tsx
import { useState } from 'react';
// dentro del componente:
  const [vistaActiva, setVistaActiva] = useState(0);
  const vistas = [
    { clave: 'disposicion', titulo: 'A disposición', usdMinor: p.aDisposicionUsdMinor },
    { clave: 'neto', titulo: 'Total neto', usdMinor: p.netoUsdMinor },
    { clave: 'total', titulo: 'Total actual', usdMinor: p.totalUsdMinor },
  ] as const;

// JSX: reemplazar
//   <p className="cifra-grande" …>{enVista(p.totalUsdMinor)}</p>
//   <p …>A disposición: … · Ahorro: …</p>
// por:
      <div
        className="slider-patrimonio"
        onScroll={(e) => {
          const el = e.currentTarget;
          setVistaActiva(Math.min(2, Math.max(0, Math.round(el.scrollLeft / el.clientWidth))));
        }}
      >
        {vistas.map((v) => (
          <div key={v.clave} className="vista">
            <p className="titulo-vista">{v.titulo}</p>
            <p className="cifra-grande" style={{ margin: '4px 0' }}>{enVista(v.usdMinor)}</p>
          </div>
        ))}
      </div>
      <div className="slider-puntos" aria-hidden="true">
        {vistas.map((v, i) => <span key={v.clave} className={i === vistaActiva ? 'activo' : ''} />)}
      </div>
```

El `<h3>` del encabezado pasa de «Patrimonio total» a «Patrimonio» (la vista activa ya titula la cifra). El toggle USD/Bs existente aplica a las 3 vistas sin cambios (todas pasan por `enVista`).

- [ ] **Paso 3: DashboardPage pasa las deudas al patrimonio.** Cambiar la línea del cálculo:

```tsx
  const p = patrimonio(
    cuentas, txs, tasa?.vesPerUsd ?? null,
    deudas.filter((deuda) => deuda.status === 'pendiente' && deuda.direction === 'porPagar'),
  );
```

(`deudas` ya se carga desde la Tarea 6.)

- [ ] **Paso 4: CuentasSection.** En `NUEVA()` añadir el default de creación (decisión del spec: `!ahorro`):

```ts
const NUEVA = (): Account => ({
  id: crypto.randomUUID(), name: '', currency: 'USD', kind: 'wallet',
  isSavings: false, archived: false, enDisposicion: true,
});
```

y en el modal, entre el checkbox de ahorro y el de archivada:

```tsx
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="checkbox"
                style={{ width: 'auto' }}
                aria-label="A disposición"
                checked={editando.enDisposicion ?? !editando.isSavings}
                onChange={(e) => setEditando({ ...editando, enDisposicion: e.target.checked })}
              />
              A disposición (cuenta en la vista «A disposición» del patrimonio)
            </label>
```

- [ ] **Paso 5: Verificación visual bloqueante.** Headless (Clock API por Recharts en la misma página):
  1. Inicio muestra la vista «A disposición» primero; scroll horizontal programático (`el.scrollTo`) a cada vista → título y cifra cambian, el punto activo acompaña.
  2. Con la deuda por pagar de prueba, «Total neto» < «Total actual» exactamente por su valor convertido.
  3. Toggle a Bs → las 3 vistas en Bs.
  4. En Ajustes → cuentas, marcar Binance «A disposición» y desmarcar Efectivo → la vista 1 cambia acorde; los seeds sin flag se comportan como `!ahorro`.
  5. Desglose por cuentas y línea de tasa intactos debajo.
  Capturas a `.superpowers/sdd/capturas/task-8/`. Mata el dev server.

- [ ] **Paso 6: Suite + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/ui/componentes/PatrimonioCard.tsx src/ui/componentes/CuentasSection.tsx src/ui/paginas/DashboardPage.tsx src/ui/estilos.css
git commit -m "feat(patrimonio): slider de 3 vistas con scroll-snap y flag a disposición

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### GATE Fase 2 (bloqueante)

- [ ] Run: `npx vitest run` — Expected: verde. Run: `npm run build` — Expected: sin errores.

---

## Fase 3 — Movimientos transaccionales

### Tarea 9: Residuo virtual `txsEfectivas` (TDD)

**REQUIRED SUB-SKILL:** `superpowers:test-driven-development`.

**Files:**
- Create: `src/domain/groups.ts`, `src/domain/groups.test.ts`

**Interfaces:**
- Consumes: `Transaction`, `Account`, `TxGroup`, `Currency`.
- Produces (las tareas 11-12 dependen de las firmas EXACTAS):
  - `residuosDeGrupo(miembros: Transaction[], accounts: Account[]): { currency: Currency; residuoMinor: number }[]` — Σ firmada por moneda (ingresos +, gastos −), sin las de residuo 0.
  - `txsEfectivas(txs: Transaction[], accounts: Account[], grupos?: TxGroup[]): Transaction[]` — las sin `groupId` intactas + un virtual «Vuelto» por grupo y moneda. **SOLO agregaciones, NUNCA saldos.**

- [ ] **Paso 1: Tests en RED** — `src/domain/groups.test.ts`:

```ts
import { residuosDeGrupo, txsEfectivas } from './groups';
import { monthSummary, budgetStatus } from './stats';
import type { Account, Transaction } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
];

const tx = (parcial: Partial<Transaction>): Transaction => ({
  id: crypto.randomUUID(), type: 'expense', accountId: 'acc-efectivo',
  amountMinor: 100, date: '2026-07-10', source: 'manual', ...parcial,
});

test('residuo positivo: me pasaron $5, la leche costó $3,50 → vuelto +$1,50', () => {
  const miembros = [
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 350, groupId: 'g1' }),
  ];
  expect(residuosDeGrupo(miembros, CUENTAS)).toEqual([{ currency: 'USD', residuoMinor: 150 }]);
});

test('residuo negativo y residuo cero', () => {
  expect(residuosDeGrupo([
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS)).toEqual([{ currency: 'USD', residuoMinor: -200 }]);
  expect(residuosDeGrupo([
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS)).toEqual([]);
});

test('grupo con dos monedas → un residuo por moneda', () => {
  const r = residuosDeGrupo([
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),                       // +$5.00
    tx({ type: 'expense', amountMinor: 73723, accountId: 'acc-bnc', groupId: 'g1' }), // −Bs 737,23
  ], CUENTAS);
  expect(r).toContainEqual({ currency: 'USD', residuoMinor: 500 });
  expect(r).toContainEqual({ currency: 'VES', residuoMinor: -73723 });
});

test('txsEfectivas: deja pasar las sueltas y sustituye el grupo por su vuelto virtual', () => {
  const txs = [
    tx({ id: 's1', amountMinor: 999 }), // suelta
    tx({ id: 'm1', type: 'income', amountMinor: 500, date: '2026-07-08', groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 350, date: '2026-07-10', time: '09:30:00', groupId: 'g1' }),
  ];
  const efectivas = txsEfectivas(txs, CUENTAS, [{ id: 'g1', nota: 'leche', createdIso: 'x' }]);
  expect(efectivas.map((t) => t.id)).toContain('s1');
  expect(efectivas.some((t) => t.id === 'm1' || t.id === 'm2')).toBe(false); // miembros excluidos
  const virtual = efectivas.find((t) => t.id === 'virtual-g1-USD');
  expect(virtual).toMatchObject({
    type: 'income', amountMinor: 150, categoryId: 'cat-vuelto-ingreso',
    date: '2026-07-10', time: '09:30:00',            // fecha del miembro más reciente
    accountId: 'acc-efectivo', note: 'leche',
  });
});

test('txsEfectivas: residuo negativo → gasto virtual cat-vuelto-gasto; cero → nada', () => {
  const negativo = txsEfectivas([
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS);
  expect(negativo).toHaveLength(1);
  expect(negativo[0]).toMatchObject({ type: 'expense', amountMinor: 200, categoryId: 'cat-vuelto-gasto' });

  const cero = txsEfectivas([
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS);
  expect(cero).toHaveLength(0);
});

test('las agregaciones del mes solo ven el residuo (composición con monthSummary)', () => {
  const txs = [
    tx({ type: 'expense', amountMinor: 2000, categoryId: 'cat-supermercado' }),           // gasto normal $20
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 350, categoryId: 'cat-supermercado', groupId: 'g1' }),
  ];
  const resumen = monthSummary({
    txs: txsEfectivas(txs, CUENTAS), accounts: CUENTAS, month: '2026-07',
    viewCurrency: 'USD', rates: [], activeSource: 'BCV',
  });
  expect(resumen.expenseMinor).toBe(2000);  // el gasto agrupado NO suma
  expect(resumen.incomeMinor).toBe(150);    // solo el vuelto
});

test('presupuestos: el gasto agrupado no consume presupuesto; el vuelto negativo va a su categoría', () => {
  const categorias = [
    { id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#fff', kind: 'expense' as const, monthlyBudgetMinor: 10000 },
  ];
  const txs = [
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 350, categoryId: 'cat-supermercado', groupId: 'g1' }),
  ];
  const estados = budgetStatus({
    txs: txsEfectivas(txs, CUENTAS), accounts: CUENTAS, categories: categorias,
    month: '2026-07', rates: [], activeSource: 'BCV',
  });
  expect(estados[0].gastoUsdMinor).toBe(0); // los $3,50 agrupados no tocan Supermercado
});

test('desagrupar restaura: sin groupId todo vuelve a agregarse normal', () => {
  const agrupadas = [
    tx({ id: 'm1', type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 350, groupId: 'g1' }),
  ];
  const desagrupadas = agrupadas.map(({ groupId: _fuera, ...t }) => t as Transaction);
  const resumen = monthSummary({
    txs: txsEfectivas(desagrupadas, CUENTAS), accounts: CUENTAS, month: '2026-07',
    viewCurrency: 'USD', rates: [], activeSource: 'BCV',
  });
  expect(resumen.incomeMinor).toBe(500);
  expect(resumen.expenseMinor).toBe(350);
});
```

- [ ] **Paso 2: RED.** Run: `npx vitest run src/domain/groups.test.ts` — Expected: FAIL (módulo inexistente).

- [ ] **Paso 3: Implementación** — `src/domain/groups.ts`:

```ts
import type { Account, Currency, Transaction, TxGroup } from './types';

export interface ResiduoMoneda {
  currency: Currency;
  /** Firmado: + ingreso (vuelto a favor), − gasto. */
  residuoMinor: number;
}

/** Σ firmada de los miembros por moneda (ingresos +, gastos −).
 *  Transferencias/ajustes se ignoran defensivamente (no son agrupables). */
export function residuosDeGrupo(miembros: Transaction[], accounts: Account[]): ResiduoMoneda[] {
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const porMoneda = new Map<Currency, number>();
  for (const t of miembros) {
    if (t.type !== 'income' && t.type !== 'expense') continue;
    const moneda = monedaDe.get(t.accountId) ?? 'USD';
    const firmado = t.type === 'income' ? t.amountMinor : -t.amountMinor;
    porMoneda.set(moneda, (porMoneda.get(moneda) ?? 0) + firmado);
  }
  return [...porMoneda.entries()]
    .filter(([, residuo]) => residuo !== 0)
    .map(([currency, residuoMinor]) => ({ currency, residuoMinor }));
}

function masReciente(txs: Transaction[]): Transaction {
  return txs.reduce((a, b) => (`${b.date} ${b.time ?? ''}` > `${a.date} ${a.time ?? ''}` ? b : a));
}

/** Sustituye los movimientos agrupados por su residuo virtual «Vuelto».
 *  SOLO para agregaciones (mes, comparativa, gráficas, presupuestos).
 *  NUNCA para saldos: accountBalanceMinor/patrimonio usan las txs crudas.
 *  Los virtuales (`virtual-…`) jamás se persisten. */
export function txsEfectivas(txs: Transaction[], accounts: Account[], grupos: TxGroup[] = []): Transaction[] {
  const notaDe = new Map(grupos.map((g) => [g.id, g.nota]));
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
    for (const { currency, residuoMinor } of residuosDeGrupo(miembros, accounts)) {
      const deLaMoneda = miembros.filter((t) => (monedaDe.get(t.accountId) ?? 'USD') === currency);
      virtuales.push({
        id: `virtual-${groupId}-${currency}`,
        type: residuoMinor > 0 ? 'income' : 'expense',
        accountId: masReciente(deLaMoneda).accountId,
        amountMinor: Math.abs(residuoMinor),
        date: ultimo.date,
        time: ultimo.time,
        categoryId: residuoMinor > 0 ? 'cat-vuelto-ingreso' : 'cat-vuelto-gasto',
        note: notaDe.get(groupId) || 'Vuelto de grupo transaccional',
        source: 'manual',
        groupId,
      });
    }
  }
  return [...sueltas, ...virtuales];
}
```

- [ ] **Paso 4: GREEN + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/domain/groups.ts src/domain/groups.test.ts
git commit -m "feat(grupos): residuo virtual Vuelto por moneda para las agregaciones

txsEfectivas excluye los movimientos agrupados y suma solo su residuo;
los saldos siguen usando las transacciones crudas.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 10: Operaciones de grupo en la base (TDD)

**REQUIRED SUB-SKILL:** `superpowers:test-driven-development`.

**Files:**
- Create: `src/db/groupOps.ts`, `src/db/groupOps.test.ts`

**Interfaces:**
- Consumes: `CuentasDB`, `TxGroup`.
- Produces (la Tarea 12 depende de las firmas EXACTAS):
  - `agruparTxs(d, txIds: string[], nota?: string): Promise<string>` — valida ≥2, solo income/expense, sin grupo previo; devuelve el id del grupo.
  - `anadirAGrupo(d, groupId: string, txIds: string[]): Promise<void>` — mismas validaciones + grupo existente.
  - `quitarDeGrupo(d, txIds: string[]): Promise<void>` — si un grupo queda con <2 miembros se disuelve.
  - `desagrupar(d, groupId: string): Promise<void>` — limpia `groupId` de todos y borra el grupo.

- [ ] **Paso 1: Tests en RED** — `src/db/groupOps.test.ts`:

```ts
import { CuentasDB, seedIfEmpty } from './db';
import { agruparTxs, anadirAGrupo, quitarDeGrupo, desagrupar } from './groupOps';
import { buildManualTx } from '../domain/builders';

async function dbConTxs() {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  const ids: string[] = [];
  for (const [type, amountMinor] of [['income', 500], ['expense', 350], ['expense', 120]] as const) {
    const tx = buildManualTx({ type, accountId: 'acc-efectivo', amountMinor, categoryId: 'cat-otros-gastos', dateIso: '2026-07-10' });
    await d.transactions.add(tx);
    ids.push(tx.id);
  }
  return { d, ids };
}

test('agruparTxs asigna groupId a todos y crea el grupo con nota', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]], '  leche  ');
  expect((await d.txGroups.get(gid))?.nota).toBe('leche');
  expect((await d.transactions.get(ids[0]))?.groupId).toBe(gid);
  expect((await d.transactions.get(ids[1]))?.groupId).toBe(gid);
  expect((await d.transactions.get(ids[2]))?.groupId).toBeUndefined();
});

test('agruparTxs rechaza <2, tipos no agrupables y re-agrupar (todo-o-nada)', async () => {
  const { d, ids } = await dbConTxs();
  await expect(agruparTxs(d, [ids[0]])).rejects.toThrow('al menos 2');
  const transfer = { id: 'tr1', type: 'transfer' as const, accountId: 'acc-efectivo', toAccountId: 'acc-zinli', amountMinor: 100, toAmountMinor: 100, date: '2026-07-10', source: 'manual' as const };
  await d.transactions.add(transfer);
  await expect(agruparTxs(d, [ids[0], 'tr1'])).rejects.toThrow('gastos e ingresos');
  expect((await d.transactions.get(ids[0]))?.groupId).toBeUndefined(); // nada quedó a medias
  const gid = await agruparTxs(d, [ids[0], ids[1]]);
  await expect(agruparTxs(d, [ids[1], ids[2]])).rejects.toThrow('ya pertenece');
  expect((await d.txGroups.toArray()).map((g) => g.id)).toEqual([gid]); // no se creó un grupo fantasma
});

test('anadirAGrupo suma un miembro; a grupo inexistente lanza', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]]);
  await anadirAGrupo(d, gid, [ids[2]]);
  expect(await d.transactions.where('groupId').equals(gid).count()).toBe(3);
  await expect(anadirAGrupo(d, 'no-existe', [ids[2]])).rejects.toThrow('no existe');
});

test('quitarDeGrupo con quórum mantiene el grupo; bajando de 2 lo disuelve', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1], ids[2]]);
  await quitarDeGrupo(d, [ids[2]]);
  expect(await d.transactions.where('groupId').equals(gid).count()).toBe(2);
  expect(await d.txGroups.get(gid)).toBeTruthy();
  await quitarDeGrupo(d, [ids[1]]);
  expect(await d.txGroups.get(gid)).toBeUndefined();          // disuelto
  expect((await d.transactions.get(ids[0]))?.groupId).toBeUndefined(); // el último quedó libre
});

test('desagrupar limpia groupId de todos y borra el grupo', async () => {
  const { d, ids } = await dbConTxs();
  const gid = await agruparTxs(d, [ids[0], ids[1]]);
  await desagrupar(d, gid);
  expect(await d.txGroups.count()).toBe(0);
  for (const id of [ids[0], ids[1]]) {
    expect((await d.transactions.get(id))?.groupId).toBeUndefined();
  }
});
```

- [ ] **Paso 2: RED.** Run: `npx vitest run src/db/groupOps.test.ts` — Expected: FAIL.

- [ ] **Paso 3: Implementación** — `src/db/groupOps.ts`:

```ts
import { CuentasDB } from './db';
import type { TxGroup } from '../domain/types';

async function validarAgrupables(d: CuentasDB, txIds: string[]): Promise<void> {
  const txs = await d.transactions.bulkGet(txIds);
  for (const t of txs) {
    if (!t) throw new Error('Algún movimiento ya no existe');
    if (t.type !== 'income' && t.type !== 'expense') throw new Error('Solo se agrupan gastos e ingresos');
    if (t.groupId) throw new Error('Algún movimiento ya pertenece a un grupo');
  }
}

async function limpiarGroupId(d: CuentasDB, txIds: string[]): Promise<void> {
  await d.transactions.where('id').anyOf(txIds).modify((t) => { delete t.groupId; });
}

/** Crea un grupo transaccional con ≥2 gastos/ingresos sueltos. Todo-o-nada. */
export async function agruparTxs(d: CuentasDB, txIds: string[], nota?: string): Promise<string> {
  if (txIds.length < 2) throw new Error('Selecciona al menos 2 movimientos');
  const grupo: TxGroup = { id: crypto.randomUUID(), nota: nota?.trim() || undefined, createdIso: new Date().toISOString() };
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    await validarAgrupables(d, txIds);
    await d.txGroups.add(grupo);
    await d.transactions.where('id').anyOf(txIds).modify((t) => { t.groupId = grupo.id; });
  });
  return grupo.id;
}

export async function anadirAGrupo(d: CuentasDB, groupId: string, txIds: string[]): Promise<void> {
  if (txIds.length === 0) throw new Error('Selecciona al menos 1 movimiento');
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    if (!(await d.txGroups.get(groupId))) throw new Error('El grupo no existe');
    await validarAgrupables(d, txIds);
    await d.transactions.where('id').anyOf(txIds).modify((t) => { t.groupId = groupId; });
  });
}

/** Saca movimientos de sus grupos; un grupo que baja de 2 miembros se disuelve. */
export async function quitarDeGrupo(d: CuentasDB, txIds: string[]): Promise<void> {
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    const txs = (await d.transactions.bulkGet(txIds)).filter((t) => t !== undefined);
    const gruposTocados = [...new Set(txs.map((t) => t.groupId).filter((g): g is string => !!g))];
    await limpiarGroupId(d, txIds);
    for (const gid of gruposTocados) {
      const restantes = await d.transactions.where('groupId').equals(gid).primaryKeys();
      if (restantes.length < 2) {
        await limpiarGroupId(d, restantes);
        await d.txGroups.delete(gid);
      }
    }
  });
}

/** Revierte el grupo completo: todos los miembros vuelven a ser movimientos normales. */
export async function desagrupar(d: CuentasDB, groupId: string): Promise<void> {
  await d.transaction('rw', d.transactions, d.txGroups, async () => {
    const miembros = await d.transactions.where('groupId').equals(groupId).primaryKeys();
    await limpiarGroupId(d, miembros);
    await d.txGroups.delete(groupId);
  });
}
```

- [ ] **Paso 4: GREEN + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/db/groupOps.ts src/db/groupOps.test.ts
git commit -m "feat(grupos): agrupar, añadir, quitar y desagrupar transaccionales

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 11: Las agregaciones del Dashboard usan `txsEfectivas`

**Files:**
- Modify: `src/ui/paginas/DashboardPage.tsx`

**Interfaces:**
- Consumes: `txsEfectivas` (Tarea 9), `db.txGroups`.
- Produces: resumen del mes, comparativa, gráficas y presupuestos calculados sobre las efectivas; patrimonio y saldos sobre las crudas.

- [ ] **Paso 1: Cambios en `DashboardPage.tsx`.** Añadir junto a los otros `useLiveQuery` (antes del guard):

```tsx
import { txsEfectivas } from '../../domain/groups';
// junto a los otros useLiveQuery:
  const grupos = useLiveQuery(() => db.txGroups.toArray(), []) ?? [];
```

y tras calcular `p` (que sigue recibiendo `txs` crudas — NO cambiar):

```tsx
  const efectivas = txsEfectivas(txs, cuentas, grupos);
  const base = { txs: efectivas, accounts: cuentas, rates, activeSource: settings.rateSource };
```

(`resumen`, `comparativa` y `presupuestos` ya derivan de `base`, así que quedan cubiertos sin más cambios; `GraficasMes` consume `ResumenMes` y no se toca.)

- [ ] **Paso 2: Verificación bloqueante (headless).** Sembrar por consola de la página: un ingreso de $5 y un gasto de $3,50 el mismo día, agrupados vía `agruparTxs` (impórtalo con `page.evaluate` sobre módulos expuestos o siembra `groupId`/`txGroups` directo con Dexie). Comprobar en Inicio:
  1. «Este mes» muestra ingreso $1.50 y el gasto agrupado NO aparece en el donut.
  2. El patrimonio NO cambió por agrupar (los saldos son los mismos que sin grupo).
  3. Desagrupando (borra `groupId` + fila de `txGroups`), las cifras vuelven a $5/$3,50.
  Capturas a `.superpowers/sdd/capturas/task-11/` (Clock API por Recharts). Mata el dev server.

- [ ] **Paso 3: Suite + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/ui/paginas/DashboardPage.tsx
git commit -m "feat(grupos): el dashboard agrega sobre txsEfectivas; saldos intactos

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 12: UI de selección y grupos en Movimientos

**Files:**
- Modify: `src/ui/paginas/MovimientosPage.tsx`
- Modify: `src/ui/estilos.css`

**Interfaces:**
- Consumes: `agruparTxs`/`anadirAGrupo`/`quitarDeGrupo`/`desagrupar` (Tarea 10), `residuosDeGrupo` (Tarea 9), `db.txGroups`.
- Produces: modo selección por pulsación larga, agrupar con nota, grupos plegados con 🔁 y residuo, expandir/añadir/quitar/desagrupar. `EditorMovimiento` queda INTACTO (incluida su validación de fecha del fix-wave).

- [ ] **Paso 1: CSS.** Añadir al final de `src/ui/estilos.css`:

```css
/* Selección y grupos transaccionales (v2) */
.fila-mov.seleccionada {
  outline: 2px solid var(--primario);
  outline-offset: -2px;
  background: var(--primario-tinta);
}
.fila-mov.no-seleccionable { opacity: 0.45; }
.barra-seleccion {
  position: fixed;
  bottom: calc(64px + env(safe-area-inset-bottom));
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 32px);
  max-width: 448px;
  display: flex;
  gap: 8px;
  padding: 10px;
  background: var(--tarjeta-2);
  border: 1px solid var(--borde);
  border-radius: var(--radio);
  box-shadow: var(--sombra);
  z-index: 30; /* bajo modales (40) */
}
.barra-seleccion .boton { flex: 1; margin: 0; }
.grupo-miembros {
  margin-left: 12px;
  border-left: 2px solid var(--borde);
  padding-left: 8px;
}
```

- [ ] **Paso 2: Reescribir la parte de lista de `MovimientosPage.tsx`** (los filtros, `descripcionFila`, `montoFila` y `EditorMovimiento` se conservan tal cual). El componente queda:

```tsx
import { useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import type { Transaction } from '../../domain/types';
import { filterTxs } from '../../domain/filters';
import { formatMoney, parseVenezuelanAmount } from '../../domain/money';
import { residuosDeGrupo } from '../../domain/groups';
import { agruparTxs, anadirAGrupo, quitarDeGrupo, desagrupar } from '../../db/groupOps';
import { avisar } from '../Toast';

export function MovimientosPage() {
  const [q, setQ] = useState('');
  const [cuentaId, setCuentaId] = useState('');
  const [categoriaId, setCategoriaId] = useState('');
  const [mes, setMes] = useState('');
  const [editando, setEditando] = useState<Transaction | null>(null);
  const [seleccion, setSeleccion] = useState<Set<string> | null>(null); // null = sin modo selección
  const [destinoGrupoId, setDestinoGrupoId] = useState<string | null>(null); // añadir a grupo existente
  const [modalNota, setModalNota] = useState(false);
  const [notaGrupo, setNotaGrupo] = useState('');
  const [expandidos, setExpandidos] = useState<Set<string>>(new Set());
  const [guardando, setGuardando] = useState(false);
  const timerRef = useRef<number | null>(null);
  const largaRef = useRef(false);

  const cuentas = useLiveQuery(() => db.accounts.toArray(), []) ?? [];
  const categorias = useLiveQuery(() => db.categories.toArray(), []) ?? [];
  const txs = useLiveQuery(() => db.transactions.toArray(), []) ?? [];
  const grupos = useLiveQuery(() => db.txGroups.toArray(), []) ?? [];

  const visibles = filterTxs(txs, {
    q: q || undefined,
    accountId: cuentaId || undefined,
    categoryId: categoriaId || undefined,
    month: mes || undefined,
  });
  const meses = [...new Set(txs.map((t) => t.date.slice(0, 7)))].sort().reverse();
  const nombreCuenta = (id?: string) => cuentas.find((c) => c.id === id)?.name ?? '?';
  const cat = (id?: string) => categorias.find((c) => c.id === id);
  const notaDeGrupo = (gid: string) => grupos.find((g) => g.id === gid)?.nota;

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

  // ---- modo selección ----
  const esAgrupable = (t: Transaction) =>
    (t.type === 'income' || t.type === 'expense') && !t.groupId;

  function iniciarSeleccion(t: Transaction, destino: string | null = null) {
    setDestinoGrupoId(destino);
    setSeleccion(new Set(t && esAgrupable(t) ? [t.id] : []));
  }

  function toggleSeleccion(t: Transaction) {
    if (!esAgrupable(t)) { avisar('Solo se agrupan gastos e ingresos sueltos'); return; }
    setSeleccion((s) => {
      const nueva = new Set(s);
      if (nueva.has(t.id)) nueva.delete(t.id); else nueva.add(t.id);
      return nueva;
    });
  }

  function cancelarSeleccion() {
    setSeleccion(null);
    setDestinoGrupoId(null);
    setModalNota(false);
    setNotaGrupo('');
  }

  function alPresionar(t: Transaction) {
    largaRef.current = false;
    timerRef.current = window.setTimeout(() => {
      largaRef.current = true;
      if (seleccion === null) iniciarSeleccion(t);
    }, 500);
  }

  function alSoltar() {
    if (timerRef.current !== null) { clearTimeout(timerRef.current); timerRef.current = null; }
  }

  function alClickFila(t: Transaction) {
    if (largaRef.current) { largaRef.current = false; return; }
    if (seleccion !== null) toggleSeleccion(t);
    else setEditando(t);
  }

  async function confirmarAgrupar() {
    if (guardando || seleccion === null) return;
    setGuardando(true);
    try {
      if (destinoGrupoId) {
        await anadirAGrupo(db, destinoGrupoId, [...seleccion]);
        avisar('Añadidos al grupo ✓');
      } else {
        await agruparTxs(db, [...seleccion], notaGrupo);
        avisar('Grupo transaccional creado ✓');
      }
      cancelarSeleccion();
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  async function accionGrupo(fn: () => Promise<void>, exito: string) {
    if (guardando) return;
    setGuardando(true);
    try {
      await fn();
      avisar(exito);
    } catch (e) {
      avisar((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }

  // ---- modelo de vista: sueltas + grupos, ordenados por fecha desc ----
  const clave = (t: Transaction) => `${t.date} ${t.time ?? ''}`;
  const gruposVisibles = new Map<string, Transaction[]>();
  const sueltasVisibles: Transaction[] = [];
  for (const t of visibles) {
    if (t.groupId) {
      const lista = gruposVisibles.get(t.groupId) ?? [];
      lista.push(t);
      gruposVisibles.set(t.groupId, lista);
    } else {
      sueltasVisibles.push(t);
    }
  }
  type Item = { clave: string } & ({ tipo: 'tx'; t: Transaction } | { tipo: 'grupo'; gid: string; visibles: Transaction[] });
  const items: Item[] = [
    ...sueltasVisibles.map((t) => ({ tipo: 'tx' as const, t, clave: clave(t) })),
    ...[...gruposVisibles.entries()].map(([gid, vis]) => ({
      tipo: 'grupo' as const, gid, visibles: vis,
      clave: clave(vis.reduce((a, b) => (clave(b) > clave(a) ? b : a))),
    })),
  ].sort((a, b) => b.clave.localeCompare(a.clave));

  const filaTx = (t: Transaction, dentroDeGrupo: boolean) => (
    <button
      key={t.id}
      className={[
        'fila-mov',
        seleccion?.has(t.id) ? 'seleccionada' : '',
        seleccion !== null && !esAgrupable(t) ? 'no-seleccionable' : '',
      ].join(' ').trim()}
      onPointerDown={() => !dentroDeGrupo && alPresionar(t)}
      onPointerUp={alSoltar}
      onPointerLeave={alSoltar}
      onPointerCancel={alSoltar}
      onContextMenu={(e) => e.preventDefault()}
      onClick={() => alClickFila(t)}
    >
      <span style={{ fontSize: 22 }}>
        {t.type === 'transfer' ? '🔁' : t.type === 'adjustment' ? '🎯' : cat(t.categoryId)?.icon ?? '📦'}
      </span>
      <span className="detalle">
        <span className="nota">{descripcionFila(t)}</span>
        <span className="meta">{t.date} · {nombreCuenta(t.accountId)}{t.importKey ? ' · importado' : ''}</span>
      </span>
      {montoFila(t)}
    </button>
  );

  const filaGrupo = (gid: string, visiblesDelGrupo: Transaction[]) => {
    const todos = txs.filter((t) => t.groupId === gid);
    const residuos = residuosDeGrupo(todos, cuentas);
    const abierto = expandidos.has(gid);
    return (
      <div key={`grupo-${gid}`}>
        <button
          className="fila-mov"
          onClick={() => setExpandidos((s) => {
            const nueva = new Set(s);
            if (nueva.has(gid)) nueva.delete(gid); else nueva.add(gid);
            return nueva;
          })}
        >
          <span style={{ fontSize: 22 }}>🔁</span>
          <span className="detalle">
            <span className="nota">{notaDeGrupo(gid) || 'Grupo transaccional'}</span>
            <span className="meta">{todos.length} movimientos · vuelto</span>
          </span>
          <span>
            {residuos.length === 0
              ? <span className="meta">sin residuo</span>
              : residuos.map((r) => (
                  <span key={r.currency} className={r.residuoMinor >= 0 ? 'monto-pos' : 'monto-neg'} style={{ display: 'block' }}>
                    {r.residuoMinor >= 0 ? '+' : '−'}{formatMoney(Math.abs(r.residuoMinor), r.currency)}
                  </span>
                ))}
          </span>
        </button>
        {abierto && (
          <div className="grupo-miembros">
            {visiblesDelGrupo.map((t) => (
              <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <div style={{ flex: 1 }}>{filaTx(t, true)}</div>
                <button
                  className="chip"
                  aria-label="Quitar del grupo"
                  disabled={guardando}
                  onClick={() => accionGrupo(() => quitarDeGrupo(db, [t.id]), 'Quitado del grupo ✓')}
                >
                  quitar
                </button>
              </div>
            ))}
            <div className="fila-form" style={{ margin: '8px 0' }}>
              <button
                className="boton secundario"
                disabled={guardando || seleccion !== null}
                onClick={() => { setDestinoGrupoId(gid); setSeleccion(new Set()); avisar('Toca los movimientos a añadir'); }}
              >
                Añadir movimientos
              </button>
              <button
                className="boton peligro"
                disabled={guardando}
                onClick={() => {
                  if (window.confirm('¿Desagrupar? Todos los movimientos vuelven a contarse normal.')) {
                    void accionGrupo(() => desagrupar(db, gid), 'Grupo desagrupado ✓');
                  }
                }}
              >
                Desagrupar
              </button>
            </div>
          </div>
        )}
      </div>
    );
  };

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
        {items.length === 0 && (
          <div className="vacio"><span className="icono">🗂️</span>No hay movimientos que coincidan.</div>
        )}
        {items.map((item) => item.tipo === 'tx' ? filaTx(item.t, false) : filaGrupo(item.gid, item.visibles))}
      </div>

      {seleccion !== null && (
        <div className="barra-seleccion">
          <button className="boton secundario" onClick={cancelarSeleccion} disabled={guardando}>Cancelar</button>
          <button
            className="boton"
            disabled={guardando || seleccion.size < (destinoGrupoId ? 1 : 2)}
            onClick={() => destinoGrupoId ? void confirmarAgrupar() : setModalNota(true)}
          >
            {destinoGrupoId ? `Añadir al grupo (${seleccion.size})` : `Agrupar como transaccional (${seleccion.size})`}
          </button>
        </div>
      )}

      {modalNota && (
        <div className="modal-fondo" onClick={() => setModalNota(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Agrupar como transaccional</h3>
            <p style={{ fontSize: 12, color: 'var(--texto2)' }}>
              Los movimientos agrupados no cuentan en tus métricas: solo su vuelto.
            </p>
            <label>Nota del grupo (opcional)</label>
            <input aria-label="Nota del grupo" value={notaGrupo} onChange={(e) => setNotaGrupo(e.target.value)} placeholder="me pasaron para la leche" />
            <div className="fila-form" style={{ marginTop: 12 }}>
              <button className="boton secundario" onClick={() => setModalNota(false)} disabled={guardando}>Cancelar</button>
              <button className="boton" onClick={() => void confirmarAgrupar()} disabled={guardando}>Agrupar</button>
            </div>
          </div>
        </div>
      )}

      {editando && <EditorMovimiento tx={editando} cerrar={() => setEditando(null)} />}
    </div>
  );
}
```

`EditorMovimiento` se conserva EXACTAMENTE como está (con su validación de fecha del fix-wave). Al editar un miembro del grupo, el residuo se recalcula solo (es derivado).

- [ ] **Paso 3: Verificación visual bloqueante (headless).** Con datos sembrados (2 sueltos agrupables + 1 transferencia):
  1. Pulsación larga (down + `waitForTimeout(600)` + up con `page.mouse`) sobre una fila → modo selección con esa fila marcada y barra visible.
  2. Tocar la segunda → «Agrupar como transaccional (2)» habilitado; la transferencia se ve atenuada y avisa al tocarla.
  3. Agrupar con nota «leche» → fila de grupo 🔁 con la nota y el residuo correcto; capturar plegado y expandido.
  4. «Quitar» un miembro de un grupo de 2 → el grupo desaparece (disuelto) y ambos vuelven como sueltos.
  5. Re-agrupar, «Añadir movimientos» → añade un tercero; «Desagrupar» con confirm → todo vuelve a la lista normal.
  6. Click corto sobre una fila suelta SIN modo selección sigue abriendo el editor (≤3 toques intactos).
  Capturas a `.superpowers/sdd/capturas/task-12/`. Mata el dev server.

- [ ] **Paso 4: Suite + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/ui/paginas/MovimientosPage.tsx src/ui/estilos.css
git commit -m "feat(grupos): selección por pulsación larga y grupos en Movimientos

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### GATE Fase 3 (bloqueante)

- [ ] Run: `npx vitest run` — Expected: verde. Run: `npm run build` — Expected: sin errores.

---

## Fase 4 — Captura desde notificaciones del BNC

> **BLOQUEO EXTERNO:** las Tareas 13-14 requieren los 2-3 textos ANONIMIZADOS de notificaciones reales del BNC que Daniel debe entregar (pedidos en los prerrequisitos). Si no han llegado, la Tarea 15 (guía) puede escribirse igual; 13-14 esperan. NUNCA inventes los formatos ni copies textos con datos personales.

### Tarea 13: `parseNotificacionBnc` (TDD con fixtures de Daniel)

**REQUIRED SUB-SKILL:** `superpowers:test-driven-development`.

**Files:**
- Create: `src/capture/parseNotificacionBnc.ts`, `src/capture/parseNotificacionBnc.test.ts`, `src/capture/fixtures/notificacionesBnc.ts`

**Interfaces:**
- Consumes: `parseVenezuelanAmount` de `domain/money.ts` (si el formato del monto lo permite; si no, parseo propio documentado).
- Produces (la Tarea 14 depende de la firma EXACTA):

```ts
export interface CapturaBnc {
  tipo: 'ingreso' | 'gasto';
  /** céntimos de Bs (la notificación del BNC siempre es en Bs). */
  montoMinor: number;
  /** nota lista para el movimiento: tipo de operación + referencia/comercio. */
  nota: string;
}
export function parseNotificacionBnc(texto: string): CapturaBnc | null;
```

- [ ] **Paso 1: Fixtures.** Crear `src/capture/fixtures/notificacionesBnc.ts` con los textos entregados por Daniel, EXACTOS pero ya anonimizados por él (verifica visualmente que no traen nombres/cédulas/números completos antes de commitear — restricción de privacidad):

```ts
/** Textos reales anonimizados de notificaciones del BNC (entregados por Daniel, 2026-07).
 *  PRIVACIDAD: solo pueden contener datos inventados/enmascarados. */
export const NOTIFICACION_PAGO_MOVIL: string = /* pegar aquí el texto entregado */;
export const NOTIFICACION_COMPRA: string = /* pegar aquí el texto entregado */;
```

- [ ] **Paso 2: Tests en RED** — `src/capture/parseNotificacionBnc.test.ts`:

```ts
import { parseNotificacionBnc } from './parseNotificacionBnc';
import { NOTIFICACION_PAGO_MOVIL, NOTIFICACION_COMPRA } from './fixtures/notificacionesBnc';

test('pago móvil recibido → ingreso en Bs con monto y referencia en la nota', () => {
  const r = parseNotificacionBnc(NOTIFICACION_PAGO_MOVIL);
  expect(r).not.toBeNull();
  expect(r?.tipo).toBe('ingreso');
  expect(r?.montoMinor).toBeGreaterThan(0);
  expect(Number.isInteger(r?.montoMinor)).toBe(true);
  // Ajustar con el monto EXACTO de la fixture al pegarla, p. ej.:
  // expect(r?.montoMinor).toBe(1351775);
  // expect(r?.nota).toContain('ref');  // referencia visible en la nota
});

test('compra con tarjeta → gasto en Bs con comercio en la nota', () => {
  const r = parseNotificacionBnc(NOTIFICACION_COMPRA);
  expect(r).not.toBeNull();
  expect(r?.tipo).toBe('gasto');
  expect(r?.montoMinor).toBeGreaterThan(0);
  // Ajustar con el monto y comercio EXACTOS de la fixture al pegarla.
});

test('textos no reconocidos → null', () => {
  expect(parseNotificacionBnc('')).toBeNull();
  expect(parseNotificacionBnc('hola mundo')).toBeNull();
  expect(parseNotificacionBnc('BNC te informa algo sin monto')).toBeNull();
});
```

Al pegar las fixtures reales, REEMPLAZA los comentarios «Ajustar…» por aserciones exactas de monto y nota (mismo criterio que los parsers BNC/Zinli de la v1: aserciones literales, no genéricas).

- [ ] **Paso 3: RED.** Run: `npx vitest run src/capture` — Expected: FAIL.

- [ ] **Paso 4: Implementación.** Estructura del módulo (los regex CONCRETOS se derivan de las fixtures reales — es la única parte que este plan no puede fijar sin los textos; escríbelos anclados al formato literal, tolerantes solo en lo variable: montos, referencias, comercios):

```ts
import { parseVenezuelanAmount } from '../domain/money';
import type {} from '../domain/types';

export interface CapturaBnc {
  tipo: 'ingreso' | 'gasto';
  montoMinor: number;
  nota: string;
}

interface Reconocedor {
  tipo: CapturaBnc['tipo'];
  /** Debe capturar el monto en el grupo 1 y referencia/comercio en el 2 (si existe). */
  regex: RegExp;
  nota: (monto: string, extra: string | undefined) => string;
}

// Derivados de src/capture/fixtures/notificacionesBnc.ts (textos reales anonimizados):
const RECONOCEDORES: Reconocedor[] = [
  // { tipo: 'ingreso', regex: /…pago móvil…/i, nota: (…) => `Pago móvil recibido · ref ${…}` },
  // { tipo: 'gasto',   regex: /…compra…/i,     nota: (…) => `Compra ${comercio}` },
];

/** Reconoce pago móvil recibido y compra con tarjeta del BNC. Cualquier otra cosa → null. */
export function parseNotificacionBnc(texto: string): CapturaBnc | null {
  const limpio = texto.normalize('NFKC').replace(/\s+/g, ' ').trim();
  if (!limpio) return null;
  for (const r of RECONOCEDORES) {
    const m = r.regex.exec(limpio);
    if (!m) continue;
    const montoMinor = parseVenezuelanAmount(m[1]);
    if (montoMinor === null || montoMinor <= 0) continue;
    return { tipo: r.tipo, montoMinor, nota: r.nota(m[1], m[2]) };
  }
  return null;
}
```

Completa `RECONOCEDORES` con los dos casos reales y borra los comentarios de ejemplo. Si el monto de la notificación no viene en formato venezolano (`1.234,56`), escribe el parseo del formato real con test propio.

- [ ] **Paso 5: GREEN + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/capture
git commit -m "feat(captura): parser de notificaciones del BNC con fixtures anonimizadas

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 14: Ruta `#/captura` y prefill de Nuevo

**Files:**
- Create: `src/ui/paginas/CapturaPage.tsx`
- Modify: `src/ui/App.tsx`, `src/ui/paginas/NuevoPage.tsx`

**Interfaces:**
- Consumes: `parseNotificacionBnc` (Tarea 13), `navigate`.
- Produces: `#/captura?texto=<urlencoded>` → Nuevo pre-llenado (confirmar = 1 toque en la categoría); no reconocido → Nuevo en blanco con el texto en la nota. NUNCA se guarda nada sin confirmación.

- [ ] **Paso 1: Prefill en `NuevoPage.tsx`.** Añadir tras los `useState` iniciales (el `modo` ya lee `tipo` de params):

```tsx
  // Prefill de captura (v2): solo al montar; lo que el usuario edite después manda.
  useEffect(() => {
    const monto = params.get('monto');
    if (monto && /^\d+$/.test(monto)) {
      setEntrada((Number(monto) / 100).toFixed(2).replace('.', ','));
    }
    const notaParam = params.get('nota');
    if (notaParam) setNota(notaParam);
    const cuentaParam = params.get('cuenta');
    if (cuentaParam) setCuentaId(cuentaParam);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
```

(Va ANTES del `useEffect` de `getSettings` ya existente o después — da igual: aquel usa `setCuentaId((prev) => prev ?? …)` y no pisa el prefill. Verifícalo al integrar.)

- [ ] **Paso 2: `src/ui/paginas/CapturaPage.tsx`:**

```tsx
import { useEffect } from 'react';
import { parseNotificacionBnc } from '../../capture/parseNotificacionBnc';
import { navigate } from '../useHashRoute';

/** Recibe el texto crudo de una notificación (MacroDroid) y redirige a Nuevo
 *  pre-llenado. Nunca guarda nada por sí misma. */
export function CapturaPage({ params }: { params: URLSearchParams }) {
  useEffect(() => {
    const texto = params.get('texto') ?? '';
    const r = parseNotificacionBnc(texto);
    const destino = r
      ? new URLSearchParams({
          tipo: r.tipo, monto: String(r.montoMinor), cuenta: 'acc-bnc', nota: r.nota,
        })
      : new URLSearchParams({ tipo: 'gasto', nota: texto.slice(0, 200) });
    navigate(`/nuevo?${destino.toString()}`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return <div className="vacio"><span className="icono">📨</span>Procesando notificación…</div>;
}
```

- [ ] **Paso 3: Ruta en `App.tsx`:**

```tsx
import { CapturaPage } from './paginas/CapturaPage';
// en <main>:
        {path === '/captura' && <CapturaPage params={params} />}
```

- [ ] **Paso 4: Verificación visual bloqueante (headless).**
  1. Navegar a `#/captura?texto=` + el texto urlencoded de la fixture de pago móvil → aterriza en Nuevo con modo Ingreso, monto y nota pre-llenados y cuenta BNC seleccionada; tocar una categoría guarda el movimiento (1 toque) y aparece en Movimientos con la nota de la referencia.
  2. Con la fixture de compra → modo Gasto pre-llenado.
  3. Con `texto=hola%20mundo` → Nuevo en blanco (gasto, monto vacío) con «hola mundo» en la nota; NADA se guardó solo.
  4. Volver a `#/nuevo` normal → sin prefill fantasma.
  Capturas a `.superpowers/sdd/capturas/task-14/`. Mata el dev server.

- [ ] **Paso 5: Suite + commit**

Run: `npx vitest run` — Expected: verde.

```bash
git add src/ui/paginas/CapturaPage.tsx src/ui/paginas/NuevoPage.tsx src/ui/App.tsx
git commit -m "feat(captura): ruta #/captura con prefill de Nuevo en 1 toque

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Tarea 15: Guía de MacroDroid para Daniel

**Files:**
- Create: `docs/guia-macrodroid.md`

**Interfaces:**
- Consumes: la ruta `#/captura` (Tarea 14) — pero el texto puede escribirse en paralelo.
- Produces: guía paso a paso en español.

- [ ] **Paso 1: Crear `docs/guia-macrodroid.md`:**

```markdown
# Capturar notificaciones del BNC con MacroDroid

Esta guía configura tu Android para que, al tocar una acción de MacroDroid tras
una notificación del BNC, se abra Cuentas Claras con el movimiento pre-llenado.
Solo confirmas la categoría: 1 toque.

## Requisitos

- MacroDroid instalado (la versión gratis alcanza: esta es 1 macro).
- Cuentas Claras instalada como PWA desde https://cuentas-claras-r2f.pages.dev
- Permiso de acceso a notificaciones concedido a MacroDroid
  (Ajustes → Notificaciones → Acceso a notificaciones → MacroDroid).

## La macro, paso a paso

1. Abre MacroDroid → **Añadir macro**.
2. **Disparador**: `Notificación` → `Notificación recibida` → elige
   **la app del BNC** en la lista de aplicaciones. Acepta.
3. **Acción**: `Abrir sitio web / URL` y pega EXACTAMENTE:

   `https://cuentas-claras-r2f.pages.dev/#/captura?texto={not_text_urlencoded}`

   (`{not_text_urlencoded}` es una "magic variable" de MacroDroid: el texto de
   la notificación ya codificado para URL. Escríbela con el selector de
   variables `…` del campo, no a mano, para evitar errores de tipeo.)
4. **Restricciones**: ninguna.
5. Ponle nombre (p. ej. «BNC → Cuentas Claras») y guarda.

## Cómo se usa

- Llega un pago móvil o una compra → llega la notificación del BNC → MacroDroid
  abre Cuentas Claras con el ingreso/gasto pre-llenado en la cuenta BNC.
- Tocas la categoría → guardado. Si no quieres registrarlo, cierras y ya:
  **nada se guarda sin tu confirmación**.
- Si la notificación no se reconoce, se abre Nuevo en blanco con el texto en la
  nota, para que lo registres a mano.

## Ojo con los duplicados (papercut aceptado en v2)

Si registras un movimiento por captura y LUEGO importas el TXT mensual del BNC,
el import puede volver a traer ese mismo movimiento: el detector de duplicados
del import compara contra movimientos importados, no contra los capturados.
Regla práctica: al revisar la vista previa del import mensual, desmarca las
filas que ya registraste por captura (la referencia en la nota te ayuda a
ubicarlas). La deduplicación automática captura↔import queda para la v3.
```

- [ ] **Paso 2: Commit**

```bash
git add docs/guia-macrodroid.md
git commit -m "docs(captura): guía de MacroDroid para las notificaciones del BNC

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### GATE Fase 4 (bloqueante)

- [ ] Run: `npx vitest run` — Expected: verde. Run: `npm run build` — Expected: sin errores.

---

## Fase 5 — Cierre: redeploy y validación de Daniel

### Tarea 16: Redeploy a producción y validación en dispositivos

**Files:** ninguno nuevo (deploy + validación).

- [ ] **Paso 1: Suite y build finales.**

```bash
npx vitest run     # Expected: toda la suite verde
npm run build      # Expected: sin errores
```

- [ ] **Paso 2: Deploy** (wrangler ya autenticado con la cuenta de Daniel):

```bash
npx wrangler pages deploy dist --project-name cuentas-claras
```

Expected: deploy OK hacia https://cuentas-claras-r2f.pages.dev

- [ ] **Paso 3: Smoke de producción (headless, sin extensión de Chrome):** contra la URL real: la app carga, el SW se activa, `#/deudas` y `#/captura?texto=hola` responden. Nota: los dispositivos con la v1 instalada abren con datos v1; la subida `db.version(2)` es aditiva y Daniel re-ingresa/re-importa (decisión 7) — si algo se ve raro, «Borrar datos de la app» y empezar limpio es el camino soportado.

- [ ] **Paso 4: Validación de Daniel (BLOQUEANTE — es el criterio de éxito de la v2):** pedirle que en su Android y su PC recorra:
  1. Registrar una deuda con su amigo asignada a Efectivo → la ve descontada en «Total neto».
  2. Marcarla pagada → el gasto aparece en Efectivo.
  3. Deslizar el patrimonio entre las 3 vistas (y el toggle Bs/$).
  4. Agrupar los $5 que le pasaron con la compra de la leche → su mes solo refleja el vuelto.
  5. Tocar la notificación capturada del BNC (macro de la guía) → confirmar el pago móvil en 1 toque.
  6. Seleccionar su TXT de BNC en Android sin pelear con el selector (fix de la Tarea 1).
  7. Exportar un respaldo y restaurarlo (v2); comprobar que un respaldo v1 se rechaza con mensaje claro.

- [ ] **Paso 5: Cierre.** Registrar el veredicto en el ledger; correcciones que surjan → mini-tareas con el mismo estándar (TDD si tocan núcleo, verificación visual si tocan UI). Al aprobar Daniel: `superpowers:finishing-a-development-branch`.

---

## Autorrevisión del plan (hecha al escribirlo)

- **Cobertura del spec:** §1 deudas → Tareas 2-6; §2 slider → 7-8; §3 transaccionales → 9-12; §4 captura → 13-15; §5 bug TXT → 1; §6 datos → 2-3; §7 testing → distribuido en cada tarea TDD + verificaciones visuales; criterio de éxito → 16.
- **Consistencia de firmas:** `buildDebt/buildSettleTx/debtTotals/settleDebt` (T4) se consumen tal cual en T5-T6; `patrimonio(..., deudasPorPagarPendientes)` (T7) en T8; `residuosDeGrupo/txsEfectivas` (T9) en T11-T12; `agruparTxs/anadirAGrupo/quitarDeGrupo/desagrupar` (T10) en T12; `CapturaBnc/parseNotificacionBnc` (T13) en T14.
- **Único hueco declarado:** los regex de `RECONOCEDORES` (T13) dependen de fixtures que aún no existen — bloqueo externo explícito, no un placeholder accidental.
