# Cuentas Claras v3 — Calendario de eventos, asignación de movimientos, categoría en grupos y «Este mes» expandible

**Fecha:** 2026-09-04 · **Estado:** diseñado por el agente con defaults razonables (directiva de autonomía de Daniel); vetable en la validación final.
**Base:** v2 en producción (https://cuentas-claras-r2f.pages.dev, `main` = `origin/main` en `0b5e84e`), suite 137/137, Dexie v2, respaldo `schemaVersion: 2`.
**Método:** panel de 3 diseños independientes (dominio-primero, UX-primero, riesgo-primero) juzgados por 3 revisores (fidelidad, corrección contra el código real, simplicidad). Este documento es la síntesis: base «dominio» + injertos de «UX» y «riesgo».

## Petición de Daniel (literal)

> Necesito que las transacciones agrupadas puedas definirle también una categoría. Quiero agregarle un calendario (que tenga una preview semanal en la interfaz principal y sea extendible) principalmente para definir fechas de cuando tengo que pagar cosas o cuando me van a pagar, además de definir cosas constantes como sueldos o suscripciones que hay que pagar. Ahora que se agrega estos "eventos", se deben de poder asignar transacciones a estos eventos, por ejemplo si me cae un pago móvil por x bs en vez de simplemente caer en la categoría de sueldo que este se asigne al evento del día 15 que se llama quincena y tiene su categoría de sueldo. Además del gráfico principal quiero que sea expansible para determinar cuánto dinero gastado hay en todas las categorías en forma de lista, además de poder hacerlo cambiar de mes para ver registros anteriores.

Cuatro capacidades:

1. **Categoría en grupos transaccionales.**
2. **Calendario de eventos** (pagos por hacer / cobros por recibir; únicos y recurrentes) con tarjeta semanal en Inicio y vista completa.
3. **Asignación de movimientos a eventos**, en 1 toque desde Nuevo/captura, en el editor de movimientos y en la vista previa de los imports.
4. **«Este mes» expandible**: lista de todas las categorías (gastos e ingresos) y navegación de mes.

## Restricciones vigentes (no negociables)

Todas las de v1/v2 siguen íntegras: montos enteros en céntimos y formato `Bs 13.517,75` / `$84.20`; todo en español; tests solo en el núcleo (dominio/db/backup) con TDD; UI sin tests automáticos pero con verificación visual headless bloqueante; guard `guardando` en todo guardado async; ningún `useLiveQuery` tras un return condicional; jerarquía z contenido < nav(30) < modal(40) < toast(50); estilos SOLO por tokens/clases de `estilos.css`; `d.transaction` con ARRAY de tablas si son >5; `txsEfectivas()` alimenta SOLO agregaciones y los saldos usan txs crudas; ids `virtual-…` jamás se persisten; capas `domain/db/import/backup/capture` sin React; registro manual de un gasto en ≤3 toques; fechas locales `yyyy-mm-dd` (nunca `new Date('yyyy-mm-dd')`, que parsea UTC); sin librerías nuevas.

## Decisiones de diseño (tomadas por el agente; NO reabrir durante la implementación, Daniel puede vetarlas después)

1. **Ocurrencias derivadas, nunca persistidas.** Lo único persistido es la definición del evento (`CalEvent`) y la evidencia en la transacción (`eventId` + `eventDate`). Sin tabla de ocurrencias ni de overrides.
2. **`eventDate` es la fecha NOMINAL de la ocurrencia** (p. ej. `2026-09-15` para la quincena) aunque el movimiento sea del 17. Es la clave de la ocurrencia.
3. **`categoryId` obligatoria en el evento; pago/cobro se deriva del `kind` de su categoría.** Sin campo `direction`/`kind` redundante.
4. **Asignar copia la categoría del evento en el movimiento** (default editable antes de guardar) en TODOS los flujos (Nuevo, editor, imports). Desasignar borra `eventId`/`eventDate` y conserva la categoría. Editar el evento nunca retro-propaga nada a movimientos ya asignados.
5. **En la vista previa de imports el evento sugerido gana** a la heurística por defecto y a la regla aprendida (petición literal de Daniel); el select de categoría queda visible y corregible antes de confirmar.
6. **Varias transacciones pueden apuntar a la misma ocurrencia** (quincena en dos pagos móviles). Una transacción tiene a lo sumo un evento.
7. **Precedencia de estado: hecha (hay movimientos) > omitida > vencida (fecha < hoy) > pendiente.** «Hoy» cuenta como pendiente y SIEMPRE se inyecta como parámetro.
8. **`omitidas: string[]` dentro del evento** para silenciar una ocurrencia que no se va a registrar. No hay «hecha sin movimiento»: el efectivo es una cuenta y se registra.
9. **Recurrencias: `once` / `weekly` / `monthly { days }` / `yearly`**; semanal y anual derivan de `startDate`; sin `interval`. **Día > días del mes → último día** (31 ≡ último), con deduplicación. Preset «Quincenal (15 y último)» = `monthly { days: [15, 31] }`.
10. **Terminar una suscripción = `endDate`** (chip «Terminar hoy»). Sin `paused`/`archived`.
11. **Categoría de grupo = categoría del residuo virtual solo si su `kind` coincide con el signo del residuo**; si no, «Vuelto» del signo (como v2). Los miembros conservan su propia categoría.
12. **Ventana de sugerencia asimétrica**: la ocurrencia puede estar hasta 31 días ANTES del movimiento (pago atrasado) y hasta 7 días DESPUÉS (pago adelantado). Sugerencia automática solo si el evento no tiene monto o el monto coincide ±10 %; todo visible y editable.
13. **Tarjeta «Esta semana» = 7 días rodantes desde hoy** más las vencidas de los últimos 30 días (las más viejas viven en el calendario).
14. **El navegador de mes gobierna las tres tarjetas de estadísticas** (Este mes, Comparativa, Presupuestos), acotado entre el mes más antiguo con movimientos y el actual (sin futuro). Estado local, no persistido.
15. **`#/calendario` fuera de la barra de navegación** (patrón Deudas: se llega desde la tarjeta). Nav intacta.
16. **Respaldo v3 acepta respaldos v2** normalizándolos en memoria (`calEvents: []`) porque v2→v3 es puramente aditivo y Daniel tiene meses de datos reales; v1 sigue rechazado (decisión 7 del spec v2).
17. **Sin seeds v3** (los eventos son personales; las categorías necesarias ya existen).
18. **Deudas y eventos conviven sin enlace**: una deuda es «monto sin fecha»; un evento es «fecha con monto opcional». Posible `Debt.eventId` en v4.
19. **Sin notificaciones push**: la tarjeta semanal y el aviso de vencidas son el recordatorio.
20. **Nombres de mes y día codificados en español**, sin `Intl` (capturas headless estables).

## 1. Modelo de datos (`src/domain/types.ts`, todo aditivo)

```ts
export interface Transaction {
  // …campos v1/v2 intactos…
  /** v3: ocurrencia (eventId, eventDate) a la que se asignó. Van juntos: ambos o ninguno.
   *  eventDate es la fecha NOMINAL de la ocurrencia, no necesariamente = date.
   *  La asignación no altera monto, fecha, cuenta ni tipo; no afecta saldos ni agregaciones. */
  eventId?: string;
  eventDate?: string;
}

export interface TxGroup {
  id: string;
  nota?: string;
  /** v3: categoría del residuo virtual. Se aplica SOLO si su kind coincide con el signo del
   *  residuo (expense ↔ residuo < 0, income ↔ residuo > 0); si no, cae en «Vuelto». */
  categoryId?: string;
  createdIso: string;
}

/** Reglas de recurrencia (v3). weekly/yearly derivan día de la semana / mes+día de startDate. */
export type Recurrence =
  | { kind: 'once' }
  | { kind: 'weekly' }
  | { kind: 'monthly'; days: number[] }   // 1..31, ordenados, sin repetidos; día > días del mes → último día
  | { kind: 'yearly' };

/** Evento de calendario (v3): fecha en que hay que pagar o cobrar algo. NO es dinero:
 *  no altera saldos ni agregaciones. Sus ocurrencias se DERIVAN (fechasDeEvento). */
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

Sin cambios en `Account`, `Category`, `Rule`, `Settings`, `Debt`, `Rate`, `TxType`, `TxSource` (los movimientos creados por la v3 siguen siendo `source: 'manual'`).

### Dexie v3 (`src/db/db.ts`, aditivo, sin migración de contenido)

```ts
calEvents!: Table<CalEvent, string>;
// v1 y v2 quedan EXACTAMENTE como están.
this.version(3).stores({
  transactions: 'id, date, accountId, categoryId, importKey, groupId, eventId',
  calEvents: 'id',
});
```

- Índice `eventId`: lo usan `eliminarEvento` y las consultas «todas las asignadas» (`where('eventId').above('')`). Sin índice compuesto: los eventos son decenas y el cruce se hace en memoria.
- `txGroups` no cambia de esquema (`categoryId` nunca se consulta por índice).
- **Seeds v3: ninguno.** `db.test.ts` sigue esperando 18 categorías.

### Respaldo v3 (`src/backup/backup.ts`)

- `BackupFile.schemaVersion: 3`; `TABLAS` = las 8 de v2 + `'calEvents'` (9 tablas; `importBackup` ya usa array).
- `validateBackup`: `1` → rechazo con el mensaje actual; `2` → exige las 8 tablas v2 y devuelve `{ ok: true, backup: { ...b, schemaVersion: 3, calEvents: [] } }`; `3` → exige las 9; otra → «Versión de respaldo no soportada».
- `exportBackup` siempre escribe 3. Test guardia: `Object.keys(exportBackup())` = `TABLAS ∪ { schemaVersion, exportedAt }`.

## 2. Fechas (`src/domain/dates.ts`, ampliado; todo LOCAL por componentes)

```ts
export function partsOf(iso: string): { y: number; m: number; d: number }
export function isoFromParts(y: number, m: number, d: number): string       // padStart, sin validar
export function esFechaIso(s: string): boolean                                // /^\d{4}-\d{2}-\d{2}$/ y fecha real (round-trip)
export function daysInMonth(y: number, m: number): number                     // new Date(y, m, 0).getDate()
export function addDays(iso: string, n: number): string                       // new Date(y, m-1, d+n) local
export function diffDias(a: string, b: string): number                        // b − a en días enteros (Date.UTC sobre partes, inmune a DST)
export function weekdayLunes0(iso: string): number                            // 0 = lunes … 6 = domingo
export function mesAnterior(month: string): string
export function mesSiguiente(month: string): string
export function primerDiaMes(month: string): string
export function ultimoDiaMes(month: string): string
export function nombreMes(month: string): string                              // 'Septiembre 2026'
export function fechaCorta(iso: string): string                               // '15 sep'
export function diaCorto(iso: string): string                                 // 'vie 4'
export function fechaLarga(iso: string): string                               // 'Viernes 4 de septiembre'
export function ddmmyyyy(iso: string): string                                 // '04/09/2026'
export const LETRAS_DIA: readonly string[]                                    // ['L','M','X','J','V','S','D'] (lunes primero)
export function celdasMes(month: string): { iso: string; enMes: boolean }[]   // SIEMPRE 42 celdas, desde el lunes ≤ día 1
```

`todayIso`, `isoFromDdMmYyyy` y `monthOf` no cambian. Prohibido en todo el código nuevo: `new Date('yyyy-mm-dd')`, `Date.parse`, `toISOString()` para fechas de calendario. Las comparaciones de isos son por string.

## 3. Calendario y ocurrencias (`src/domain/calendar.ts`, nuevo, puro)

```ts
export type EstadoOcurrencia = 'pendiente' | 'hecha' | 'vencida' | 'omitida';
export interface Ocurrencia { eventId: string; date: string }
export interface MontoMoneda { currency: Currency; totalMinor: number }
export interface OcurrenciaVista extends Ocurrencia {
  evento: CalEvent;
  estado: EstadoOcurrencia;
  txIds: string[];                    // movimientos asignados (puede haber varios)
  asignadoPorMoneda: MontoMoneda[];   // Σ amountMinor de las txs asignadas, por moneda de la cuenta de cada tx
  fueraDeRegla: boolean;              // true si la regla actual no genera esa fecha (asignación a una fecha «rara» o regla editada)
}

export const RECURRENCIA_QUINCENAL: Recurrence;                  // { kind: 'monthly', days: [15, 31] }
export function esQuincenal(r: Recurrence): boolean;

export function buildEvent(opts: {
  titulo: string; categoryId: string; accountId?: string; amountMinor?: number;
  startDate: string; endDate?: string; recurrence: Recurrence; omitidas?: string[]; nowIso: string;
}): CalEvent;                                                     // id = crypto.randomUUID(); valida con validarEvento
export function validarEvento(e: CalEvent): void;                 // lanza Error en español
export function parseDias(texto: string): number[];               // '15, 31' → [15, 31]; ordena y dedup; lanza si vacío o fuera de 1..31
export function fechasDeEvento(e: CalEvent, desde: string, hasta: string): string[];
export function kindDeEvento(e: CalEvent, categories: Category[]): CategoryKind | null;   // null si la categoría no existe
export function estadoDe(o: { date: string; omitida: boolean; txIds: string[] }, hoy: string): EstadoOcurrencia;
export function ocurrenciasEnRango(opts: {
  eventos: CalEvent[]; txs: Transaction[]; accounts: Account[]; desde: string; hasta: string; hoy: string;
}): OcurrenciaVista[];
export function totalesEsperados(
  ocurrencias: OcurrenciaVista[], accounts: Account[], categories: Category[],
): { pagar: MontoMoneda[]; cobrar: MontoMoneda[] };               // solo pendientes/vencidas con amountMinor; por moneda de evento.accountId; sin conversión
export function descripcionRecurrencia(e: CalEvent): string;      // 'mensual: 15 y último' · 'quincenal (15 y último)' · 'semanal: viernes' · 'anual: 3 mar' · 'una vez: 20/10/2026'; añade ' · hasta dd/mm/yyyy' si endDate
```

**`validarEvento`** (mensajes exactos): título vacío → «Ponle nombre al evento»; `categoryId` vacío → «Elige una categoría»; `amountMinor` sin `accountId` → «El monto esperado necesita una cuenta»; `amountMinor` no entero o ≤ 0 → «Monto inválido»; `startDate` no válida → «Fecha inválida»; `endDate` no válida → «Fecha de fin inválida»; `endDate < startDate` → «El fin no puede ser antes del inicio»; `monthly` con `days` vacío, fuera de 1..31, no enteros, repetidos o desordenados → «Días del mes inválidos»; alguna `omitidas` no válida → «Fecha omitida inválida».

**`fechasDeEvento(e, desde, hasta)`** — fechas nominales dentro de `[desde, hasta]` (inclusive), ascendentes, sin repetidos. Sea `desdeEf = max(desde, startDate)` y `hastaEf = endDate ? min(hasta, endDate) : hasta`; si `desdeEf > hastaEf` → `[]`.
- `once`: `[startDate]` si `desde ≤ startDate ≤ hasta` (`endDate` se ignora).
- `weekly`: `startDate + 7k` (k ≥ 0); se salta directamente al primer k con fecha ≥ `desdeEf` (sin iterar desde el ancla) y se itera hasta `hastaEf`.
- `monthly { days }`: para cada mes de `monthOf(desdeEf)` a `monthOf(hastaEf)`, para cada `d` de `days`: `dd = min(d, daysInMonth(y, m))`; se conserva si `desdeEf ≤ fecha ≤ hastaEf`. Dedupe con `Set` (`[30, 31]` en febrero → una sola fecha).
- `yearly`: mismo mes y día de `startDate` en cada año de `desdeEf..hastaEf`, día recortado a `daysInMonth` (29/02 → 28/02 en no bisiesto).
- Coste O(meses o semanas del rango × |days|). Toda API recibe `hasta`: prohibido expandir sin límite.

**`ocurrenciasEnRango`** = UNIÓN de (a) las fechas generadas por cada evento y (b) las txs con `eventId` de un evento presente y `eventDate ∈ [desde, hasta]` (agrupadas por `(eventId, eventDate)`; si el par no está en (a) → `fueraDeRegla: true`). Txs cuyo `eventId` no resuelve se ignoran. Estado por `estadoDe` con precedencia hecha (`txIds.length > 0`) > omitida (`date ∈ evento.omitidas`) > vencida (`date < hoy`) > pendiente. `asignadoPorMoneda` suma por moneda de la cuenta de cada tx (`accounts`). Orden: `date` asc, luego `titulo` asc.

**Rangos de uso** (los fija SIEMPRE el llamador): Inicio `[hoy−30, hoy+6]`; calendario `[primerDiaMes, ultimoDiaMes]` del mes visible; Nuevo `[hoy−31, hoy+7]`; editor `[tx.date−31, tx.date+7]` más la asignación actual; imports `[minFecha−31, maxFecha+7]` de las filas.

## 4. Asignación y sugerencias (`src/domain/eventMatch.ts`, nuevo, puro)

```ts
export interface TxParaSugerir { type: 'income' | 'expense'; date: string; accountId: string; amountMinor: number }
export interface CtxSugerencia { eventos: CalEvent[]; txs: Transaction[]; accounts: Account[]; categories: Category[]; hoy: string }
export const VENTANA_ATRAS_DIAS = 31;      // la ocurrencia puede ser hasta 31 días anterior al movimiento
export const VENTANA_ADELANTE_DIAS = 7;    // …o hasta 7 días posterior
export const TOLERANCIA_MONTO = 0.10;

export interface Candidata {
  ocurrencia: OcurrenciaVista;
  distanciaDias: number;                   // |diffDias(ocurrencia.date, tx.date)|
  montoCoincide: boolean | null;           // null = evento sin monto o tx sin monto (amountMinor ≤ 0)
  sugerible: boolean;                      // estado ∈ {pendiente, vencida} && montoCoincide !== false
}
export function candidatasParaTx(tx: TxParaSugerir, ctx: CtxSugerencia, opts?: { incluirHechas?: boolean }): Candidata[];
export function sugerirOcurrencia(tx: TxParaSugerir, ctx: CtxSugerencia): OcurrenciaVista | null;   // primera sugerible o null
export function sugerirParaLote(txs: Transaction[], ctx: CtxSugerencia): Map<string, Ocurrencia>;  // txId → ocurrencia
export function aplicarAsignacion(tx: Transaction, o: Ocurrencia | null, evento: CalEvent | null): Transaction;  // no muta
export function validarAsignacion(tx: Pick<Transaction, 'type'>, evento: CalEvent, categories: Category[]): void; // lanza
```

**Elegibilidad** (`candidatasParaTx`): `kindDeEvento(evento) === tx.type`; `evento.accountId ∈ {undefined, tx.accountId}`; `ocurrencia.date ∈ [tx.date − 31, tx.date + 7]`; `estado ≠ 'omitida'`; `estado === 'hecha'` solo si `incluirHechas`. `montoCoincide`: `null` si el evento no tiene `amountMinor` o `tx.amountMinor ≤ 0`; si no, `|tx.amountMinor − evento.amountMinor| ≤ TOLERANCIA_MONTO × evento.amountMinor` (misma moneda garantizada porque el evento con monto exige cuenta y la cuenta debe coincidir). Orden: `sugerible` desc, `distanciaDias` asc, `montoCoincide === true` primero, `titulo` asc.

**`sugerirParaLote`** (imports): greedy GLOBAL — se generan todos los pares `(tx, candidata sugerible)`, se ordenan por `(distanciaDias asc, |Δ monto| asc, titulo asc, índice de la tx en el lote asc)` y se recorren asignando solo si ni la tx ni la ocurrencia fueron ya asignadas. Determinista; cada ocurrencia ≤ 1 tx y cada tx ≤ 1 ocurrencia. Solo se consideran txs `income|expense`.

**`aplicarAsignacion`**: con `o` y `evento` → `{ ...tx, eventId, eventDate: o.date, categoryId: evento.categoryId }`; con `null` → copia sin `eventId` ni `eventDate` (conserva `categoryId`).

**`validarAsignacion`**: `kindDeEvento === null` → «La categoría del evento no existe»; `tx.type === 'expense'` y kind `income` → «Este movimiento es un gasto y el evento es un cobro»; inverso → «Este movimiento es un ingreso y el evento es un pago».

## 5. Categoría de grupo (`src/domain/groups.ts` y `src/db/groupOps.ts`)

```ts
export function categoriaDelResiduo(grupo: TxGroup | undefined, residuoMinor: number, categories: Category[]): string;
export function txsEfectivas(txs: Transaction[], accounts: Account[], grupos: TxGroup[] = [], categories: Category[] = []): Transaction[];
```

- `categoriaDelResiduo`: residuo < 0 → `grupo.categoryId` si existe en `categories` con `kind === 'expense'`, si no `cat-vuelto-gasto`; residuo > 0 → ídem con `income`, si no `cat-vuelto-ingreso`. Sin `categories` (4.º parámetro ausente) el comportamiento es EXACTAMENTE el de v2: los tests actuales no cambian.
- Efecto: un grupo «Supermercado» con residuo −$2 suma $2 a Supermercado en `byCategory`, donut, lista y presupuesto; con residuo +$1,50 el virtual cae en «Vuelto» (ingreso). Grupo bimoneda: la regla se evalúa por moneda contra el signo de CADA residuo. Totales del mes idénticos a v2 (solo cambia la etiqueta).
- `groupOps`: `agruparTxs(d, txIds, nota?, categoryId?)` (4.º parámetro opcional, aditivo) y `actualizarGrupo(d, groupId, patch: { nota?: string | null; categoryId?: string | null })` — `null` borra el campo, `undefined` no lo toca; valida que el grupo y (si viene) la categoría existan, dentro de la transacción. `anadirAGrupo`, `quitarDeGrupo`, `desagrupar` no cambian.

## 6. Estadísticas (`src/domain/stats.ts`, aditivo)

`ResumenMes.incomeByCategory: { categoryId: string | null; incomeMinor: number }[]` (desc por monto). `byCategory` sigue siendo SOLO gastos; ningún campo existente cambia. `monthSummary` acumula ingresos por categoría con la misma conversión que los gastos.

## 7. Operaciones de base (`src/db/eventOps.ts`, nuevo; todas reciben `d: CuentasDB`, todo-o-nada)

```ts
export async function guardarEvento(d, e: CalEvent): Promise<void>
// validarEvento; la categoría debe existir («La categoría no existe»); si hay txs asignadas al evento cuyo type
// no coincide con el kind de la categoría nueva → «Desasigna primero los N movimientos» (N = las incompatibles).
// put del objeto COMPLETO: el editor debe conservar `omitidas` (test explícito).
export async function eliminarEvento(d, eventId: string): Promise<number>
// transacción [transactions, calEvents]: where('eventId').equals(id).modify(delete eventId/eventDate) + delete; devuelve nº desasignadas.
// Los movimientos NUNCA se borran; conservan categoría, monto, fecha y cuenta.
export async function asignarEvento(d, txId: string, o: Ocurrencia): Promise<void>
// tx existe y es income|expense («Solo se asignan gastos e ingresos»); evento existe («El evento no existe»);
// esFechaIso(o.date) («Fecha inválida»); validarAsignacion; update { eventId, eventDate, categoryId: evento.categoryId }.
export async function desasignarEvento(d, txId: string): Promise<void>      // modify + delete de ambos campos; idempotente
export async function omitirOcurrencia(d, eventId: string, date: string): Promise<void>
// idempotente; rechaza si hay txs asignadas a esa ocurrencia («Ya tiene movimientos asignados»).
export async function reanudarOcurrencia(d, eventId: string, date: string): Promise<void>   // quita la fecha; array vacío → borra el campo
```

Nota Dexie: `update(id, { eventId: undefined })` borra la propiedad en Dexie 4.4.4 (`setByKeyPath` hace `delete`); `db.test.ts` lo verifica. Las operaciones usan `modify` + `delete` para dejar el invariante explícito; `EditorMovimiento` puede usar `update` con `undefined` en el mismo patch que el resto de campos (una sola escritura).

## 8. UI

### Rutas (`src/ui/App.tsx`)

- `#/calendario` (+ `?mes=yyyy-mm&dia=yyyy-mm-dd&nuevo=1` opcionales) → `CalendarioPage({ params })`. Fuera de la barra (patrón Deudas).
- `#/nuevo?…&evento=<id>&fecha=<yyyy-mm-dd>` → prefill de evento (además de los params de captura).
- `#/movimientos?cat=<id>&mes=<yyyy-mm>` → `MovimientosPage({ params })` (prop opcional; estado inicial de los filtros).
- `Nav.tsx` NO cambia.

### Inicio (`DashboardPage.tsx`)

Orden: Patrimonio → **SemanaCard** (nueva) → Deudas → GraficasMes. Live queries nuevas (`db.calEvents`) y `useState` de `mesVisto`/`expandido` ANTES del `if (!settings)`. `txsEfectivas(txs, cuentas, grupos, categorias)`. `mesVisto` inicial = mes actual; `primerMes` = mes más antiguo con txs (o el actual si no hay). `resumen`, `comparativa` (`lastMonths(mesVisto, 4)`) y `presupuestos` se calculan para `mesVisto`. `ocurrenciasSemana = ocurrenciasEnRango({ desde: addDays(hoy, −30), hasta: addDays(hoy, 6), hoy, … })` con `useMemo`.

### Tarjeta «Esta semana» (`src/ui/componentes/SemanaCard.tsx`, nueva)

Props: `{ ocurrencias: OcurrenciaVista[]; cuentas: Account[]; categorias: Category[]; hoy: string }`.
- Cabecera `.tarjeta-cabecera`: `<h3>Esta semana</h3>` + `<a className="chip" href="#/calendario">calendario</a>`.
- Tira `.semana-tira` (7 días rodantes `hoy..hoy+6`): cada `.semana-dia` (button) con `.letra` (L M X J V S D), `.num` y `.puntos` (hasta 3 `.punto` con `background: category.color`; `.punto.hecha` atenuado; `.punto.vencida` no aplica en futuro). `.semana-dia.hoy` resaltado. Tocar un día → `#/calendario?dia=…`.
- Línea de totales (solo si hay montos): «Por pagar Bs 1.250,00 · $15.99 — Por cobrar Bs 8.000,00» (`totalesEsperados` sobre las pendientes/vencidas de la lista, por moneda, sin conversión).
- Lista: primero las **vencidas** (últimos 30 días, `.fila-ocurrencia` con `.estado.estado-vencida`), luego las de la semana por fecha (todos los estados; hechas con `.fila-ocurrencia.hecha`). Fila: icono de la categoría · título · `.meta` «vie 4 · BNC» · monto esperado (o Σ asignado si hecha) · chip de estado. Tocar pendiente/vencida → `#/nuevo?evento=…&fecha=…` (registrar); tocar hecha → `#/calendario?dia=…`.
- 3 filas + chip «ver N más» / «ver menos» (estado local). Vacío: `.vacio` «Sin pagos ni cobros esta semana» + chip «crear evento» → `#/calendario?nuevo=1`.

### Página Calendario (`src/ui/paginas/CalendarioPage.tsx`, nueva)

- `h2` «Calendario» + `.nav-mes`: botón «‹» · `.titulo` «Septiembre 2026» · botón «›» · chip «hoy» (si el mes no es el actual). Botones de ≥40 px. Estado `mes` (inicial `params.mes ?? monthOf(hoy)`), `diaSel` (inicial `params.dia` si cae en el mes, si no `hoy` si cae en el mes, si no el día 1).
- `.cal-cabecera` (L…D) + `.cal-grid` 7×6 con `celdasMes(mes)` (42 celdas fijas): `.cal-dia` button con `.num`, `.puntos` (hasta 3 `.punto` con color de categoría; `.punto.hecha` atenuado; `.punto.vencida` con borde `--gasto`) y `.mas` («+2») si hay más; modificadores `.otro-mes`, `.hoy`, `.seleccionado`. Datos: `ocurrenciasEnRango` del mes, agrupadas por fecha (`useMemo`).
- Lista del día `.cal-lista`: `h3` `fechaLarga(diaSel)` («Viernes 4 de septiembre»); `.fila-ocurrencia` por ocurrencia con `.acciones`: pendiente/vencida → chips «registrar» (→ `#/nuevo?evento&fecha`) y «omitir»; omitida → «reanudar»; hecha → se listan sus movimientos (nota · monto) con chip «quitar» (`desasignarEvento`); `fueraDeRegla` → chip «fuera de la regla». Vacío: «Nada este día».
- `boton` «Nuevo evento» (ancho completo) → `EditorEvento` con `startDate = diaSel`. `params.nuevo=1` lo abre al montar.
- `<details>` «Todos los eventos (N)»: filas icono · título · `descripcionRecurrencia` · chips «editar» / «eliminar» (`window.confirm` con «N movimientos quedarán sin evento (conservan su categoría)»).
- Todos los `useLiveQuery` (calEvents, transactions, accounts, categories) al inicio.

### Editor de evento (`src/ui/componentes/EditorEvento.tsx`, nueva, hoja `.modal-fondo > .modal`)

Props: `{ original: CalEvent | null; borrador: CalEvent; cerrar: () => void }` (patrón `EditorDeuda`). Campos en orden: Título · `.segmentado` Pago / Cobro (solo filtra el select de categoría por kind; inicial = kind de la categoría del borrador) · Categoría (select obligatorio) · Cuenta esperada (select «— sin cuenta —» + cuentas no archivadas; define la moneda) · Monto esperado (`inputMode="decimal"`, coma decimal, deshabilitado sin cuenta, etiqueta «en Bs/$»; al cambiar a una cuenta de otra moneda se vacía con aviso, como `EditorDeuda`) · Fecha (`type="date"`, etiqueta «Fecha» o «Primera vez» si se repite) · Repetir (select: Una vez / Cada semana / Cada mes / Quincenal (15 y último) / Cada año; «Quincenal» guarda `RECURRENCIA_QUINCENAL` y se reconoce al reabrir con `esQuincenal`) · si «Cada mes»: input «Días del mes» («15, 31», ayuda `.ayuda` «31 = último día del mes», default el día de la fecha) · Fin (`type="date"` opcional) + chip «Terminar hoy» · Botones `.fila-form`: Cancelar · Eliminar (solo edición; confirm con conteo) · Guardar. Guard `guardando`; errores de `buildEvent`/`guardarEvento` vía `avisar`. Al guardar una edición se conserva `omitidas` del original.

### Nuevo (`src/ui/paginas/NuevoPage.tsx`)

- Live queries nuevas al inicio: `calEvents`, `transactions.where('eventId').above('')`, `accounts` (ya), `categories` (ya).
- `candidatas = candidatasParaTx({ type, date: hoy, accountId: cuenta.id, amountMinor: minor ?? 0 }, ctx)` (sin hechas) con `useMemo`; máximo 4 chips.
- Estado `eventoActivo: Ocurrencia | null`. Fila `.chips-eventos` (UNA fila, scroll horizontal, chips `.chip-evento` de 40 px) entre la nota y «Toca la categoría para guardar», renderizada SOLO si hay candidatas o `eventoActivo`. Chip: `{icono de la categoría} {titulo} · {fechaCorta}` (+ «· vencida» en texto2). Sin candidatas ni activo, el DOM es idéntico a v2 (flujo ≤3 toques intacto).
- **Tocar un chip** con monto válido = **guardar** con `categoryId` del evento + asignación (1 toque). Con monto vacío → el chip pasa a `.activo` + `avisar('Escribe el monto')`. El chip activo muestra ✕ (`.cerrar`) para desmarcar; con chip activo, tocar una categoría de la grilla guarda con esa categoría y la asignación («es la quincena pero la quiero en Otros ingresos»).
- Deep link `?evento&fecha`: al montar, `eventoActivo` = esa ocurrencia (primera y activa); `modo` según `kindDeEvento`; `cuentaId = evento.accountId ?? lastAccountId`; `entrada` = `amountMinor` formateado si el evento lo tiene y no vino `monto`. Se aplica una sola vez cuando `calEvents` y `categorias` están cargadas (ref de «aplicado»).
- Desde captura (params `tipo`/`monto`/`cuenta` sin `evento`): `sugerirOcurrencia` → ese chip va primero con `.sugerido` (no activo; tocarlo guarda).
- `guardar(categoryId, o?)`: `buildManualTx` + `aplicarAsignacion` → `db.transactions.add`. `CapturaPage` NO cambia.

### Movimientos (`src/ui/paginas/MovimientosPage.tsx`)

- Firma `MovimientosPage({ params }: { params?: URLSearchParams })`; filtros iniciales desde `params?.get('cat')` / `params?.get('mes')`.
- Modelo de vista: además de los grupos con miembros visibles, cuando hay filtro de categoría y `grupo.categoryId === categoriaId`, el grupo se muestra con sus miembros filtrados por `q`/cuenta/mes (ignorando la categoría) si queda ≥1; así el drill-down desde «Este mes → Supermercado» encuentra el grupo que aportó residuo.
- Fila de grupo: icono = `cat(grupo.categoryId)?.icon ?? '🔁'`; `.meta` «N movimientos · vuelto · Supermercado» (o «· vuelto»). Área expandida: nuevo `boton secundario` «Editar grupo» → `EditorGrupo` (modal en el mismo archivo: nota + select de categoría con `<optgroup>` Gastos/Ingresos + «Sin categoría (Vuelto)» + `.ayuda` en vivo: «Con el residuo actual (+$1,50) esta categoría de gasto no aplica; contará como Vuelto») → `actualizarGrupo`.
- Modal «Agrupar como transaccional»: mismo select de categoría (opcional) → `agruparTxs(db, ids, nota, categoryId)`.
- `filaTx`: `.meta` añade « · 📅 {titulo}» si `t.eventId` resuelve.
- `EditorMovimiento`: bajo «Categoría» (solo income/expense), `<SelectEvento>`; elegir un evento pone el select de categoría en la del evento (editable); «Sin evento» desasigna sin tocar la categoría. Guardar en UNA escritura: `db.transactions.update(tx.id, { ...cambios, eventId, eventDate })` (`undefined` borra).

### `SelectEvento` (`src/ui/componentes/SelectEvento.tsx`, nueva; compartida por editor e imports)

Props: `{ tx: TxParaSugerir; actual: Ocurrencia | null; ctx: CtxSugerencia; ariaLabel: string; onChange: (o: Ocurrencia | null, evento: CalEvent | null) => void }`. Render: `<select>` con «Sin evento», la asignación actual (aunque esté fuera de la ventana; etiquetada «(asignado)») y `candidatasParaTx(tx, ctx, { incluirHechas: true })` etiquetadas «📅 Quincena · 15/09 · pendiente|vencida|hecha». Valor = `${eventId}|${date}`. Si no hay candidatas ni actual → `null` (no ocupa espacio).

### Imports (`ImportBnc.tsx`, `ImportZinli.tsx`)

- Live queries nuevas: `calEvents`, `accounts` (Zinli también). `DraftUI` gana `sugerido: boolean`.
- En `analizar()`, tras el mapeo: `sugerirParaLote(nuevasNoDuplicadas.map(d => d.tx), ctx)`; a cada draft con sugerencia se le aplica `aplicarAsignacion` (fija `eventId/eventDate` y `categoryId` del evento: gana a la regla y a la heurística) y `sugerido: true`.
- Por fila no duplicada, bajo el select de categoría: `<SelectEvento>` (solo se renderiza si hay candidatas) + chip «sugerido» cuando `d.sugerido` y la asignación sigue siendo la sugerida. Cambiar el evento → `actualizar(i, { tx: aplicarAsignacion(d.tx, o, evento), sugerido: false })`.
- `src/import/*` y `commitImport` NO cambian: los campos viajan en la tx; el backstop de `importKey` sigue intacto.

### «Este mes» (`src/ui/componentes/GraficasMes.tsx`)

Props nuevas: `mes: string; mesActual: string; primerMes: string; onMes: (m: string) => void`.
- Cabecera `.tarjeta-cabecera` con `.nav-mes`: «‹» (disabled si `mes <= primerMes`) · `.titulo` («Este mes» si `mes === mesActual`, si no `nombreMes(mes)`) · «›» (disabled si `mes >= mesActual`) · chip «hoy» (solo si no es el actual).
- Bajo el área acumulada: chip «Ver todas las categorías» ↔ «Ocultar» (estado local). Expandido → `.lista-categorias`: `.subtitulo` «Gastos» y una `.fila-categoria` (`<a href="#/movimientos?cat=…&mes=…">`) por `resumen.byCategory`: icono · `.nombre` + `.barra-categoria` (ancho = % del total de gastos, `background: category.color`) · `.monto` · `.pct` («37 %»); luego `.subtitulo` «Ingresos» con `incomeByCategory` (% del total de ingresos) si no está vacío. «Sin categoría» (`categoryId === null`) al final, gris, sin enlace. Los virtuales «Vuelto» aparecen como categoría normal.
- Título de Presupuestos: «Presupuestos (USD) · {nombreMes(mes)}». Recharts sin cambios.

### CSS (`src/ui/estilos.css`, dos bloques nuevos; SOLO tokens existentes)

Bloque «/* Calendario y eventos (v3) */»: `.tarjeta-cabecera` (flex, space-between, center) · `.nav-mes` (flex, gap 8, center) · `.nav-mes button` (min-height 40px, min-width 40px, `chip`-like, `:disabled` opacidad .45) · `.nav-mes .titulo` (flex 1, centrado, `h3` sin margen) · `.chip.activo` (fondo `--primario-tinta`, borde `--primario`, color `--texto`) · `.ayuda` (12px, `--texto2`, margin 4px 0) · `.semana-tira` (grid 7 col, gap 4) · `.semana-dia` (button, fondo `--fondo-2`, radio `--radio-s`, padding 6px 0, borde `--borde-suave`; `.hoy` borde `--primario` + `--primario-tinta`) · `.semana-dia .letra` (11px `--texto2`) · `.semana-dia .num` (14px 700) · `.puntos` (flex, gap 3, justify center, min-height 6px) · `.punto` (6×6 round) · `.punto.hecha` (opacidad .4) · `.punto.vencida` (outline 1px `--gasto`) · `.fila-ocurrencia` (misma anatomía que `.fila-mov`; `.hecha` opacidad .55) · `.estado` (chip) con `.estado-pendiente` (borde `--borde`), `.estado-hecha` (`--ingreso-tinta`/`--ingreso`), `.estado-vencida` (`--gasto-tinta`/`--gasto`), `.estado-omitida` (`--texto2`, tachado) · `.acciones` (flex, gap 6, margin-top 6) · `.chips-eventos` (flex, gap 8, overflow-x auto, scrollbar oculta, padding 4px 0, margin 8px 0) · `.chip-evento` (chip de 40 px de alto, `flex: 0 0 auto`, nowrap, `:active` feedback; `.activo` como `.chip.activo`; `.sugerido` outline `--primario`; `.cerrar` ✕ en `--texto2`) · `.cal-cabecera` (grid 7 col, 11px, `--texto2`, uppercase, centrado) · `.cal-grid` (grid 7 col, gap 4) · `.cal-dia` (button, aspect-ratio 1 / 0.9, fondo `--fondo-2`, borde `--borde-suave`, radio `--radio-s`, flex column, padding 4px 0; `.otro-mes` opacidad .35; `.hoy` borde `--primario`; `.seleccionado` fondo `--primario-tinta`) · `.cal-dia .num` (13px) · `.cal-dia .mas` (10px `--texto2`) · `.cal-lista` (margin-top 8).

Bloque «/* Este mes expandible (v3) */»: `.lista-categorias` (margin-top 8) · `.lista-categorias .subtitulo` (12px uppercase `--texto2`, margin 10px 0 4px) · `.fila-categoria` (grid `28px 1fr auto`, gap 10, padding 8px 0, borde inferior `--borde-suave`, color `--texto`, sin subrayado, align center) · `.fila-categoria .nombre` (14px 600, ellipsis) · `.barra-categoria` (height 6px, radio 999, fondo `--fondo-2`, margin-top 4, overflow hidden; `> div` height 100 %, radio 999, transición width) · `.fila-categoria .monto` (tabular, 14px 700, text-align right) · `.fila-categoria .pct` (12px `--texto2`, text-align right).

Jerarquía z intacta; los editores nuevos usan `.modal-fondo/.modal`. Colores de categoría inline (`category.color`) como ya hace el donut (dato del usuario, no estilo).

## 9. Testing (núcleo crítico, TDD)

- **dates**: `addDays` cruzando mes/año y 29/02 (`'2026-01-31'+1 → '2026-02-01'`, `'2026-03-01'−1 → '2026-02-28'`, `'2024-02-28'+1 → '2024-02-29'`, `'2026-12-31'+1 → '2027-01-01'`); `daysInMonth(2026,2)=28`, `(2024,2)=29`, `(2026,4)=30`; `diffDias('2026-09-10','2026-09-15')=5` y negativo al revés; `weekdayLunes0('2026-09-04')=4`, `('2026-09-06')=6`, `('2026-08-31')=0`; `mesAnterior('2026-01')='2025-12'`; `mesSiguiente('2026-12')='2027-01'`; `primerDiaMes/ultimoDiaMes('2026-02')`; `nombreMes('2026-09')='Septiembre 2026'`; `fechaCorta('2026-09-15')='15 sep'`; `diaCorto('2026-09-04')='vie 4'`; `celdasMes('2026-09')` → 42 celdas, primera `'2026-08-31'`, 30 `enMes`; `celdasMes('2026-02')` → primera `'2026-01-26'`; `esFechaIso('2026-02-30')=false`.
- **calendar**: `buildEvent/validarEvento` (cada mensaje); `parseDias` (`'15, 31'`, `'31,15'`, `'15 15'`, `''`, `'40'`); `fechasDeEvento` once/weekly (ancla miércoles `'2026-09-02'` en septiembre → 02, 09, 16, 23, 30; rango posterior al ancla; `endDate` recorta)/monthly (quincena `[15,31]` en feb 2026 → 15 y 28; abril → 15 y 30; enero → 15 y 31; feb 2024 → 29; `[30,31]` en febrero → una sola; ancla `'2026-09-20'` con `[15]` → septiembre `[]`, octubre 15; multi-mes; `endDate` inclusive)/yearly (`'2024-02-29'` → 2025-02-28 y 2028-02-29; dos años); `estadoDe` (hecha > omitida > vencida > pendiente; hoy = pendiente); `ocurrenciasEnRango` (hecha con `txIds` y `asignadoPorMoneda`; dos txs suman; tx a fecha no generada → `fueraDeRegla`; `eventId` desconocido se ignora; orden); `totalesEsperados`; `descripcionRecurrencia`; `esQuincenal`.
- **eventMatch**: elegibilidad (kind, cuenta, ventana `[−31, +7]` con bordes inclusivos, omitida excluida, hecha solo con `incluirHechas`); `montoCoincide` (±10 %, null sin monto, null con `amountMinor ≤ 0`); orden; `sugerirOcurrencia` null cuando la única candidata no es sugerible; `sugerirParaLote` (dos pagos móviles 14 y 15 → solo uno recibe Quincena 15; con monto esperado gana la fila que coincide aunque esté más lejos; el 30 no se lleva la ocurrencia del 15 si la del 30 está más cerca; determinismo en empates); `aplicarAsignacion` (no muta; asignar copia categoría; null conserva categoría); `validarAsignacion`.
- **groups**: `categoriaDelResiduo` y `txsEfectivas` con categorías (expense/−, income/+, kind incompatible → Vuelto, inexistente → Vuelto, bimoneda, sin 4.º parámetro ≡ v2); composición con `monthSummary.byCategory` y `budgetStatus`.
- **stats**: `incomeByCategory` (orden, null, conversión, `byCategory` intacto).
- **groupOps**: `agruparTxs` con categoría; categoría inexistente → lanza sin crear grupo; `actualizarGrupo` (`null` borra; `undefined` no toca; grupo inexistente lanza).
- **eventOps**: `guardarEvento` (categoría inexistente; cambio de kind con txs contrarias; conserva `omitidas`); `eliminarEvento` (devuelve N, txs sin `eventId/eventDate` pero con categoría; conteo de txs intacto); `asignarEvento` (fija los 3 campos; kind contrario lanza sin cambios; transfer lanza; evento inexistente lanza); `desasignarEvento` (borra las CLAVES; idempotente); `omitirOcurrencia` (idempotente; rechaza con txs); `reanudarOcurrencia` (array vacío → sin campo).
- **db**: v3 acepta `calEvents`; `where('eventId')` consulta; `update` con `undefined` borra la propiedad; 18 categorías.
- **backup**: round-trip v3 (evento quincenal + grupo con categoría + tx asignada); v2 → ok normalizado; v1 → rechazo; v4 → no soportada; v3 sin `calEvents` → falta la tabla; guardia `Object.keys`.
- **UI** (headless Playwright, bloqueante por tarea + E2E final): crear «Quincena» con el preset → captura → chip «Quincena» primero → guardado en 1 toque con categoría Sueldo y `eventId`; Nuevo sin eventos con DOM idéntico a v2; editor con select de evento; import BNC con sugerencia única y categoría del evento; grupo con categoría en donut, lista y drill-down; mes anterior y lista de categorías; calendario con grid, lista del día, omitir/reanudar, eliminar con desasignación; respaldo v2 restaurado.

## 10. Invariantes (lista de control del revisor)

1. `txsEfectivas()` alimenta SOLO agregaciones; `accountBalanceMinor`/`patrimonio` usan txs crudas. Ni la categoría de grupo ni los eventos tocan `balances.ts`.
2. Ids `virtual-…` jamás se persisten; un virtual jamás se asigna a un evento.
3. `eventId ⇒ eventDate` con forma `yyyy-mm-dd` válida.
4. Asignar/desasignar cambia SOLO `eventId`/`eventDate` (+ `categoryId` al asignar). Jamás `amountMinor`, `date`, `accountId`, `type`.
5. Las ocurrencias no se persisten. Ninguna función expande sin `hasta`.
6. Precedencia hecha > omitida > vencida > pendiente; `hoy` siempre parámetro.
7. Fechas por componentes locales; comparaciones de isos por string.
8. `eliminarEvento` en una sola transacción; NUNCA borra movimientos.
9. `monthSummary`: campos existentes intactos; `incomeByCategory` aditivo.
10. `txsEfectivas` sin `categories` ≡ v2 (tests v2 no se tocan).
11. `commitImport`, `src/import/*` y el backstop de `importKey` NO cambian.
12. `CalEvent.categoryId` obligatoria; el chip de 1 toque siempre puede guardar.
13. Categoría de grupo: kind ≡ signo del residuo o Vuelto; nunca cambia la categoría de los miembros.
14. Flujo ≤3 toques: la fila de chips solo existe con candidatas/activo, ocupa UNA fila y no desplaza la grilla fuera de pantalla.
15. Guard `guardando`; ningún `useLiveQuery` tras return condicional; z-index; solo tokens.
16. Dexie: versiones 1 y 2 intactas; sin índices booleanos; array en `d.transaction` si >5 tablas.
17. Respaldo: `TABLAS` ≡ claves-array de `BackupFile`; v2 se normaliza, v1 se rechaza.
18. Sin librerías nuevas; nombres de mes/día sin `Intl`.
19. Capas: `domain/db/backup` sin React; funciones de db reciben `d: CuentasDB`.
20. Movimientos nuevos con `source: 'manual'`.

## 11. Estructura de archivos

```
Nuevos:
  src/domain/calendar.ts + calendar.test.ts
  src/domain/eventMatch.ts + eventMatch.test.ts
  src/db/eventOps.ts + eventOps.test.ts
  src/ui/componentes/SemanaCard.tsx
  src/ui/componentes/EditorEvento.tsx
  src/ui/componentes/SelectEvento.tsx
  src/ui/paginas/CalendarioPage.tsx
Modificados:
  src/domain/types.ts · src/db/db.ts + db.test.ts · src/backup/backup.ts + backup.test.ts
  src/domain/dates.ts + dates.test.ts · src/domain/groups.ts + groups.test.ts · src/domain/stats.ts + stats.test.ts
  src/db/groupOps.ts + groupOps.test.ts
  src/ui/estilos.css · src/ui/App.tsx · src/ui/paginas/DashboardPage.tsx · src/ui/componentes/GraficasMes.tsx
  src/ui/paginas/NuevoPage.tsx · src/ui/paginas/MovimientosPage.tsx · src/ui/componentes/ImportBnc.tsx · src/ui/componentes/ImportZinli.tsx
  README.md (sección «Calendario y eventos»)
Sin cambios: CapturaPage, parser BNC, mapBnc/mapZinli, commit.ts, filters.ts, builders.ts, balances.ts, debts*, Deudas*, Patrimonio*, Nav, useHashRoute, Ajustes*.
```

## Criterio de éxito de la v3

> Daniel crea el evento «Quincena» (Sueldo/Pagos, BNC, quincenal) en 20 segundos; ve en Inicio la tira de la semana con el punto del 15 y «Por cobrar»; le llega el pago móvil, toca la notificación capturada y en Nuevo el chip «Quincena · 15 sep» está primero: un toque y el ingreso queda con categoría Sueldo y asignado a la quincena, que pasa a «hecha» en el calendario; importa el TXT del BNC y el pago móvil grande del 15 viene sugerido a Quincena mientras el del amigo no; agrupa los $5 que le pasaron con la compra de la leche, le pone categoría Supermercado y sus $2 de bolsillo aparecen en Supermercado; expande «Este mes» y ve la lista completa de categorías, retrocede a agosto y toca «Supermercado» para ver esos movimientos.

## Fuera de alcance (v4+)

Notificaciones push, `Debt.eventId` (convertir deuda en evento), intervalos («cada 2 semanas»), «último día hábil», proyección de saldo con eventos futuros, pestaña «Calendario» en la barra (revisar tras una semana de uso), semana configurable.
