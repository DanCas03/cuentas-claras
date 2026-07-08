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
