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
