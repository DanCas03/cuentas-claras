import { buildManualTx, buildTransfer, buildAdjustment } from './builders';
import type { Account } from './types';

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

const bnc: Account = { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false };
const efectivo: Account = { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false };
const ahorro: Account = { id: 'acc-efectivo-ahorro', name: 'Efectivo ahorro', currency: 'USD', kind: 'cash', isSavings: true, archived: false };

describe('buildTransfer', () => {
  test('misma moneda: destino recibe el mismo monto, sin tasa', () => {
    const tx = buildTransfer({ fromAccount: efectivo, toAccount: ahorro, amountMinor: 5000, dateIso: '2026-07-07' });
    expect(tx).toMatchObject({
      type: 'transfer', accountId: 'acc-efectivo', toAccountId: 'acc-efectivo-ahorro',
      amountMinor: 5000, toAmountMinor: 5000, source: 'manual',
    });
    expect(tx.rateUsed).toBeUndefined();
  });
  test('USD → VES convierte con la tasa y la registra', () => {
    const tx = buildTransfer({ fromAccount: efectivo, toAccount: bnc, amountMinor: 1000, dateIso: '2026-07-07', vesPerUsd: 111.02 });
    expect(tx.toAmountMinor).toBe(111020); // $10.00 → Bs 1.110,20
    expect(tx.rateUsed).toBe(111.02);
  });
  test('VES → USD convierte dividiendo', () => {
    const tx = buildTransfer({ fromAccount: bnc, toAccount: efectivo, amountMinor: 111020, dateIso: '2026-07-07', vesPerUsd: 111.02 });
    expect(tx.toAmountMinor).toBe(1000);
  });
  test('errores: misma cuenta, monto inválido, tasa faltante', () => {
    expect(() => buildTransfer({ fromAccount: bnc, toAccount: bnc, amountMinor: 100, dateIso: '2026-07-07' })).toThrow();
    expect(() => buildTransfer({ fromAccount: efectivo, toAccount: ahorro, amountMinor: 0, dateIso: '2026-07-07' })).toThrow('Monto inválido');
    expect(() => buildTransfer({ fromAccount: efectivo, toAccount: bnc, amountMinor: 100, dateIso: '2026-07-07' })).toThrow('Falta la tasa');
  });
});

describe('buildAdjustment', () => {
  test('delta positivo (rendimiento en Binance)', () => {
    const tx = buildAdjustment({ accountId: 'acc-binance', currentBalanceMinor: 100000, realBalanceMinor: 102500, dateIso: '2026-07-07' });
    expect(tx).toMatchObject({ type: 'adjustment', amountMinor: 2500, source: 'adjust', note: 'Ajuste de saldo' });
  });
  test('delta negativo (cuadre de efectivo)', () => {
    const tx = buildAdjustment({ accountId: 'acc-efectivo', currentBalanceMinor: 5000, realBalanceMinor: 4200, dateIso: '2026-07-07' });
    expect(tx.amountMinor).toBe(-800);
  });
  test('sin diferencia lanza error', () => {
    expect(() => buildAdjustment({ accountId: 'a', currentBalanceMinor: 100, realBalanceMinor: 100, dateIso: '2026-07-07' })).toThrow('No hay diferencia que ajustar');
  });
});
