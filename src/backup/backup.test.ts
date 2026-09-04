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
  await d.debts.add({
    id: 'deuda-1', direction: 'porCobrar', concepto: 'Ana — almuerzo', amountMinor: 700,
    accountId: 'acc-zinli', status: 'pendiente', createdIso: '2026-07-20T12:00:00.000Z',
  });
  await d.txGroups.add({ id: 'grupo-1', nota: 'leche', createdIso: '2026-07-21T09:00:00.000Z' });
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

  expect(await destino.transactions.count()).toBe(2);
  expect((await destino.transactions.toArray()).some((t) => t.note === 'prueba')).toBe(true);
  expect(await destino.accounts.count()).toBe(5);
  expect(await destino.categories.count()).toBe(18);
  expect(await destino.rules.count()).toBe(1);
  expect(await destino.rates.count()).toBe(1);
  expect((await destino.settings.get('main'))?.viewCurrency).toBe('USD');
  expect(await destino.debts.count()).toBe(1);
  expect((await destino.debts.get('deuda-1'))?.concepto).toBe('Ana — almuerzo');
  expect(await destino.txGroups.count()).toBe(1);
  expect(await destino.calEvents.get('evento-1')).toEqual({
    id: 'evento-1', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
    startDate: '2026-01-01', recurrence: { kind: 'monthly', days: [15, 31] }, omitidas: ['2026-08-31'],
    createdIso: '2026-09-04T10:00:00.000Z',
  });
  expect((await destino.txGroups.get('grupo-1'))?.categoryId).toBe('cat-supermercado');
  expect(await destino.transactions.get('tx-quincena')).toMatchObject({ eventId: 'evento-1', eventDate: '2026-09-15' });
});

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

test('backupFilename incluye la fecha', () => {
  expect(backupFilename('2026-07-07')).toBe('cuentas-claras-respaldo-2026-07-07.json');
});
