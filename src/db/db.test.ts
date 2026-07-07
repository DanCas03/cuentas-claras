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
