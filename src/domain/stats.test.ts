import type { Account, Category, Rate, Transaction } from './types';
import { monthSummary, lastMonths, budgetStatus } from './stats';

const cuentas: Account[] = [
  { id: 'bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
];
const rates: Rate[] = [{ date: '2026-07-01', source: 'BCV', vesPerUsd: 100 }];
const tx = (t: Partial<Transaction>): Transaction => ({
  id: Math.random().toString(36).slice(2), type: 'expense', accountId: 'efectivo',
  amountMinor: 0, date: '2026-07-05', source: 'manual', ...t,
});

const txs: Transaction[] = [
  tx({ accountId: 'bnc', amountMinor: 100000, date: '2026-07-05', categoryId: 'cat-a' }), // Bs 1.000 → $10.00
  tx({ amountMinor: 500, date: '2026-07-10', categoryId: 'cat-a' }),                      // $5.00
  tx({ amountMinor: 300, date: '2026-07-10', categoryId: 'cat-b' }),                      // $3.00
  tx({ type: 'income', amountMinor: 2000, date: '2026-07-03' }),                          // $20.00
  tx({ type: 'transfer', accountId: 'efectivo', toAccountId: 'bnc', amountMinor: 999 }),  // no cuenta
  tx({ type: 'adjustment', accountId: 'efectivo', amountMinor: 999, source: 'adjust' }),  // no cuenta
  tx({ amountMinor: 777, date: '2026-06-30' }),                                           // otro mes
];

test('resume el mes en USD: totales, categorías y acumulado diario', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates, activeSource: 'BCV' });
  expect(r.incomeMinor).toBe(2000);
  expect(r.expenseMinor).toBe(1800);
  expect(r.byCategory).toEqual([
    { categoryId: 'cat-a', expenseMinor: 1500 },
    { categoryId: 'cat-b', expenseMinor: 300 },
  ]);
  expect(r.dailyCumulative).toHaveLength(31);
  expect(r.dailyCumulative[3]).toEqual({ day: 4, acumuladoMinor: 0 });
  expect(r.dailyCumulative[4]).toEqual({ day: 5, acumuladoMinor: 1000 });
  expect(r.dailyCumulative[30]).toEqual({ day: 31, acumuladoMinor: 1800 });
  expect(r.omitidasSinTasa).toBe(0);
});

test('sin tasa, las transacciones VES se omiten y se cuentan', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'USD', rates: [], activeSource: 'BCV' });
  expect(r.omitidasSinTasa).toBe(1);
  expect(r.expenseMinor).toBe(800); // solo las USD
});

test('vista en Bs convierte las USD', () => {
  const r = monthSummary({ txs, accounts: cuentas, month: '2026-07', viewCurrency: 'VES', rates, activeSource: 'BCV' });
  expect(r.expenseMinor).toBe(100000 + 50000 + 30000);
});

test('lastMonths cruza el año', () => {
  expect(lastMonths('2026-01', 3)).toEqual(['2026-01', '2025-12', '2025-11']);
});

test('budgetStatus compara gasto USD contra presupuesto', () => {
  const categorias: Category[] = [
    { id: 'cat-a', name: 'A', icon: 'a', color: '#000', kind: 'expense', monthlyBudgetMinor: 2000 },
    { id: 'cat-b', name: 'B', icon: 'b', color: '#000', kind: 'expense' }, // sin presupuesto: fuera
  ];
  const r = budgetStatus({ txs, accounts: cuentas, categories: categorias, month: '2026-07', rates, activeSource: 'BCV' });
  expect(r).toHaveLength(1);
  expect(r[0]).toMatchObject({ gastoUsdMinor: 1500, presupuestoMinor: 2000, ratio: 0.75 });
});
