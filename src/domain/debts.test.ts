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
