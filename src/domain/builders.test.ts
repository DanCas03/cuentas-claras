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
