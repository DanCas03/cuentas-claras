import type { Account, Debt, Transaction } from './types';
import { accountBalanceMinor, patrimonio } from './balances';

const deudaPendiente = (parcial: Partial<Debt>): Debt => ({
  id: 'd1', direction: 'porPagar', concepto: 'x', amountMinor: 500,
  accountId: 'usd1', status: 'pendiente', createdIso: '2026-07-22T00:00:00.000Z',
  ...parcial,
});

const cuentas: Account[] = [
  { id: 'bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
  { id: 'binance', name: 'Binance', currency: 'USD', kind: 'snapshot', isSavings: true, archived: false },
  { id: 'vieja', name: 'Archivada', currency: 'USD', kind: 'cash', isSavings: false, archived: true },
];

const tx = (t: Partial<Transaction>): Transaction => ({
  id: crypto.randomUUID(), type: 'expense', accountId: 'bnc', amountMinor: 0,
  date: '2026-07-01', source: 'manual', ...t,
});

const txs: Transaction[] = [
  tx({ type: 'income', accountId: 'bnc', amountMinor: 500000 }),          // +5.000,00 Bs
  tx({ type: 'expense', accountId: 'bnc', amountMinor: 150000 }),         // −1.500,00 Bs
  tx({ type: 'adjustment', accountId: 'binance', amountMinor: 25000 }),   // +$250.00
  tx({ type: 'transfer', accountId: 'efectivo', toAccountId: 'bnc', amountMinor: 1000, toAmountMinor: 111000, rateUsed: 111 }), // $10 → Bs 1.110
  tx({ type: 'income', accountId: 'efectivo', amountMinor: 5000 }),       // +$50.00
];

describe('accountBalanceMinor', () => {
  test('suma ingresos, resta gastos, aplica ambos lados de transferencias', () => {
    expect(accountBalanceMinor('bnc', txs)).toBe(500000 - 150000 + 111000); // 461000
    expect(accountBalanceMinor('efectivo', txs)).toBe(-1000 + 5000);        // 4000
    expect(accountBalanceMinor('binance', txs)).toBe(25000);
  });
  test('cuenta sin movimientos: 0', () => expect(accountBalanceMinor('vieja', [])).toBe(0));
  test('transferencia sin toAmountMinor usa amountMinor (misma moneda)', () => {
    const t = [tx({ type: 'transfer', accountId: 'efectivo', toAccountId: 'binance', amountMinor: 700 })];
    expect(accountBalanceMinor('efectivo', t)).toBe(-700);
    expect(accountBalanceMinor('binance', t)).toBe(700);
  });
});

describe('patrimonio', () => {
  test('consolida en USD y separa disposición/ahorro', () => {
    const p = patrimonio(cuentas, txs, 111);
    expect(p.lines).toHaveLength(3); // excluye la archivada
    const bnc = p.lines.find((l) => l.account.id === 'bnc')!;
    expect(bnc.balanceMinor).toBe(461000);
    expect(bnc.balanceUsdMinor).toBe(Math.round(461000 / 111)); // 4153
    expect(p.ahorroUsdMinor).toBe(25000);
    expect(p.disposicionUsdMinor).toBe(4153 + 4000);
    expect(p.totalUsdMinor).toBe(4153 + 4000 + 25000);
  });
  test('sin tasa: cuentas VES y totales dependientes quedan null, USD puras sobreviven', () => {
    const p = patrimonio(cuentas, txs, null);
    expect(p.lines.find((l) => l.account.id === 'bnc')!.balanceUsdMinor).toBeNull();
    expect(p.lines.find((l) => l.account.id === 'efectivo')!.balanceUsdMinor).toBe(4000);
    expect(p.disposicionUsdMinor).toBeNull(); // incluye una cuenta VES sin tasa
    expect(p.ahorroUsdMinor).toBe(25000);     // solo cuentas USD → sí se puede
    expect(p.totalUsdMinor).toBeNull();
  });

  test('aDisposicionUsdMinor usa el flag enDisposicion con default !isSavings', () => {
    const cuentasFlag: Account[] = [
      { id: 'usd1', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },              // sin flag → cuenta
      { id: 'usd2', name: 'Binance', currency: 'USD', kind: 'snapshot', isSavings: true, archived: false, enDisposicion: true },  // ahorro pero marcada → cuenta
      { id: 'usd3', name: 'Guardado', currency: 'USD', kind: 'cash', isSavings: false, archived: false, enDisposicion: false }, // no-ahorro desmarcada → NO cuenta
    ];
    const txsFlag: Transaction[] = [
      tx({ type: 'income', accountId: 'usd1', amountMinor: 1000 }),
      tx({ type: 'income', accountId: 'usd2', amountMinor: 2000 }),
      tx({ type: 'income', accountId: 'usd3', amountMinor: 4000 }),
    ];
    const p = patrimonio(cuentasFlag, txsFlag, null);
    expect(p.aDisposicionUsdMinor).toBe(3000); // usd1 + usd2
    expect(p.totalUsdMinor).toBe(7000);        // el total actual no cambia
  });

  test('netoUsdMinor descuenta las deudas por pagar convertidas con la tasa', () => {
    const cuentasNeto: Account[] = [
      { id: 'usd1', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
      { id: 'bs1', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
    ];
    const txsNeto: Transaction[] = [
      tx({ type: 'income', accountId: 'usd1', amountMinor: 10000 }),
    ];
    const deudas = [
      deudaPendiente({ id: 'a', accountId: 'usd1', amountMinor: 500 }),        // $5.00
      deudaPendiente({ id: 'b', accountId: 'bs1', amountMinor: 73723 }),       // Bs 737,23 → $1.00
    ];
    const p = patrimonio(cuentasNeto, txsNeto, 737.23, deudas);
    expect(p.netoUsdMinor).toBe(10000 - 600);
  });

  test('netoUsdMinor: sin deudas es igual al total; deuda en Bs sin tasa → null', () => {
    const cuentasNeto: Account[] = [
      { id: 'usd1', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
      { id: 'bs1', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
    ];
    const txsNeto: Transaction[] = [
      tx({ type: 'income', accountId: 'usd1', amountMinor: 10000 }),
    ];
    const soloUsd = patrimonio([cuentasNeto[0]], txsNeto, null);
    expect(soloUsd.netoUsdMinor).toBe(soloUsd.totalUsdMinor);
    const conBsSinTasa = patrimonio([cuentasNeto[0]], txsNeto, null, [deudaPendiente({ accountId: 'bs1' })]);
    expect(conBsSinTasa.netoUsdMinor).toBeNull();
  });
});
