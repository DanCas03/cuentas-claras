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
