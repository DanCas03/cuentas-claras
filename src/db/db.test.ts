import Dexie from 'dexie';
import { CuentasDB, seedIfEmpty, getSettings, saveSettings } from './db';

function nuevaDb() {
  return new CuentasDB(`test-${crypto.randomUUID()}`);
}

test('el seed crea las 5 cuentas y 18 categorías de la spec', async () => {
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
  expect(await d.categories.count()).toBe(18);
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

test('migración v2→v3: una base v2 poblada se abre con el código v3 sin perder filas', async () => {
  const nombre = `test-migracion-${crypto.randomUUID()}`;

  // 1) Base tal y como la dejó v2: SOLO version(1) y version(2), copiadas de db.ts.
  const v2 = new Dexie(nombre);
  v2.version(1).stores({
    accounts: 'id',
    transactions: 'id, date, accountId, categoryId, importKey',
    categories: 'id',
    rates: '[date+source], date',
    rules: 'id',
    settings: 'id',
  });
  v2.version(2).stores({
    transactions: 'id, date, accountId, categoryId, importKey, groupId',
    debts: 'id, status',
    txGroups: 'id',
  });
  await v2.open();
  expect(v2.verno).toBe(2);

  // 2) Filas reales escritas por v2 (sin eventId/eventDate: no existían).
  await v2.table('accounts').add({
    id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false,
  });
  await v2.table('categories').add({
    id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#4ade80', kind: 'expense',
  });
  await v2.table('txGroups').add({ id: 'g-viejo', createdIso: '2026-07-22T10:00:00.000Z' });
  await v2.table('transactions').bulkAdd([
    {
      id: 't-viejo-1', type: 'expense', accountId: 'acc-bnc', amountMinor: 13517_75,
      date: '2026-07-22', source: 'import', importKey: 'bnc|2026-07-22|1', categoryId: 'cat-supermercado',
      groupId: 'g-viejo', note: 'Compra de la leche',
    },
    {
      id: 't-viejo-2', type: 'income', accountId: 'acc-bnc', amountMinor: 500_00,
      date: '2026-07-23', source: 'manual',
    },
  ]);
  await v2.table('settings').add({ id: 'main', viewCurrency: 'USD', rateSource: 'BCV' });

  // 3) La app v2 se cierra (el usuario recarga con el bundle nuevo).
  v2.close();

  // 4) El código v3 abre la MISMA base: version(3) es aditiva, no hay upgrade() de contenido.
  const d = new CuentasDB(nombre);
  await d.open();
  expect(d.verno).toBe(3);

  // 5) Las filas de v2 sobreviven íntegras.
  expect(await d.transactions.count()).toBe(2);
  expect(await d.transactions.get('t-viejo-1')).toEqual({
    id: 't-viejo-1', type: 'expense', accountId: 'acc-bnc', amountMinor: 1351775,
    date: '2026-07-22', source: 'import', importKey: 'bnc|2026-07-22|1', categoryId: 'cat-supermercado',
    groupId: 'g-viejo', note: 'Compra de la leche',
  });
  expect(await d.txGroups.get('g-viejo')).toEqual({ id: 'g-viejo', createdIso: '2026-07-22T10:00:00.000Z' });
  expect((await d.accounts.get('acc-bnc'))?.name).toBe('BNC');
  expect((await d.categories.get('cat-supermercado'))?.kind).toBe('expense');
  expect((await getSettings(d)).viewCurrency).toBe('USD');

  // 6) La tabla nueva existe y nace vacía; el índice nuevo se puede consultar.
  expect(await d.calEvents.count()).toBe(0);
  expect(await d.transactions.where('eventId').above('').count()).toBe(0);

  // 7) Y v3 sigue escribiendo sobre esa base migrada.
  await d.calEvents.add({
    id: 'e1', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
    startDate: '2026-08-01', recurrence: { kind: 'monthly', days: [15, 31] },
    createdIso: '2026-09-04T10:00:00.000Z',
  });
  await d.transactions.update('t-viejo-2', { eventId: 'e1', eventDate: '2026-07-23' });
  expect(await d.transactions.where('eventId').equals('e1').count()).toBe(1);
  d.close();
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
