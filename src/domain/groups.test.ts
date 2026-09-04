import { categoriaDelResiduo, residuosDeGrupo, txsEfectivas } from './groups';
import { monthSummary, budgetStatus } from './stats';
import type { Account, Category, Transaction } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
];

const tx = (parcial: Partial<Transaction>): Transaction => ({
  id: crypto.randomUUID(), type: 'expense', accountId: 'acc-efectivo',
  amountMinor: 100, date: '2026-07-10', source: 'manual', ...parcial,
});

test('residuo positivo: me pasaron $5, la leche costó $3,50 → vuelto +$1,50', () => {
  const miembros = [
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 350, groupId: 'g1' }),
  ];
  expect(residuosDeGrupo(miembros, CUENTAS)).toEqual([{ currency: 'USD', residuoMinor: 150 }]);
});

test('residuo negativo y residuo cero', () => {
  expect(residuosDeGrupo([
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS)).toEqual([{ currency: 'USD', residuoMinor: -200 }]);
  expect(residuosDeGrupo([
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS)).toEqual([]);
});

test('grupo con dos monedas → un residuo por moneda', () => {
  const r = residuosDeGrupo([
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),                       // +$5.00
    tx({ type: 'expense', amountMinor: 73723, accountId: 'acc-bnc', groupId: 'g1' }), // −Bs 737,23
  ], CUENTAS);
  expect(r).toContainEqual({ currency: 'USD', residuoMinor: 500 });
  expect(r).toContainEqual({ currency: 'VES', residuoMinor: -73723 });
});

test('txsEfectivas: deja pasar las sueltas y sustituye el grupo por su vuelto virtual', () => {
  const txs = [
    tx({ id: 's1', amountMinor: 999 }), // suelta
    tx({ id: 'm1', type: 'income', amountMinor: 500, date: '2026-07-08', groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 350, date: '2026-07-10', time: '09:30:00', groupId: 'g1' }),
  ];
  const efectivas = txsEfectivas(txs, CUENTAS, [{ id: 'g1', nota: 'leche', createdIso: 'x' }]);
  expect(efectivas.map((t) => t.id)).toContain('s1');
  expect(efectivas.some((t) => t.id === 'm1' || t.id === 'm2')).toBe(false); // miembros excluidos
  const virtual = efectivas.find((t) => t.id === 'virtual-g1-USD');
  expect(virtual).toMatchObject({
    type: 'income', amountMinor: 150, categoryId: 'cat-vuelto-ingreso',
    date: '2026-07-10', time: '09:30:00',            // fecha del miembro más reciente
    accountId: 'acc-efectivo', note: 'leche',
  });
});

test('txsEfectivas: residuo negativo → gasto virtual cat-vuelto-gasto; cero → nada', () => {
  const negativo = txsEfectivas([
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS);
  expect(negativo).toHaveLength(1);
  expect(negativo[0]).toMatchObject({ type: 'expense', amountMinor: 200, categoryId: 'cat-vuelto-gasto' });

  const cero = txsEfectivas([
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ], CUENTAS);
  expect(cero).toHaveLength(0);
});

test('las agregaciones del mes solo ven el residuo (composición con monthSummary)', () => {
  const txs = [
    tx({ type: 'expense', amountMinor: 2000, categoryId: 'cat-supermercado' }),           // gasto normal $20
    tx({ type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 350, categoryId: 'cat-supermercado', groupId: 'g1' }),
  ];
  const resumen = monthSummary({
    txs: txsEfectivas(txs, CUENTAS), accounts: CUENTAS, month: '2026-07',
    viewCurrency: 'USD', rates: [], activeSource: 'BCV',
  });
  expect(resumen.expenseMinor).toBe(2000);  // el gasto agrupado NO suma
  expect(resumen.incomeMinor).toBe(150);    // solo el vuelto
});

test('presupuestos: el gasto agrupado no consume presupuesto; el vuelto negativo va a su categoría', () => {
  const categorias = [
    { id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#fff', kind: 'expense' as const, monthlyBudgetMinor: 10000 },
  ];
  const txs = [
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 350, categoryId: 'cat-supermercado', groupId: 'g1' }),
  ];
  const estados = budgetStatus({
    txs: txsEfectivas(txs, CUENTAS), accounts: CUENTAS, categories: categorias,
    month: '2026-07', rates: [], activeSource: 'BCV',
  });
  expect(estados[0].gastoUsdMinor).toBe(0); // los $3,50 agrupados no tocan Supermercado
});

test('desagrupar restaura: sin groupId todo vuelve a agregarse normal', () => {
  const agrupadas = [
    tx({ id: 'm1', type: 'income', amountMinor: 500, groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 350, groupId: 'g1' }),
  ];
  const desagrupadas = agrupadas.map(({ groupId: _fuera, ...t }) => t as Transaction);
  const resumen = monthSummary({
    txs: txsEfectivas(desagrupadas, CUENTAS), accounts: CUENTAS, month: '2026-07',
    viewCurrency: 'USD', rates: [], activeSource: 'BCV',
  });
  expect(resumen.incomeMinor).toBe(500);
  expect(resumen.expenseMinor).toBe(350);
});

const CATEGORIAS: Category[] = [
  { id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#4ade80', kind: 'expense' },
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-vuelto-ingreso', name: 'Vuelto', icon: '↩️', color: '#34d399', kind: 'income' },
  { id: 'cat-vuelto-gasto', name: 'Vuelto', icon: '↪️', color: '#f87171', kind: 'expense' },
];
const grupoCon = (categoryId?: string) => ({ id: 'g1', categoryId, createdIso: 'x' });

test('categoriaDelResiduo: el kind de la categoría debe coincidir con el signo del residuo', () => {
  expect(categoriaDelResiduo(grupoCon('cat-supermercado'), -200, CATEGORIAS)).toBe('cat-supermercado');
  expect(categoriaDelResiduo(grupoCon('cat-supermercado'), 150, CATEGORIAS)).toBe('cat-vuelto-ingreso');
  expect(categoriaDelResiduo(grupoCon('cat-sueldo'), 150, CATEGORIAS)).toBe('cat-sueldo');
  expect(categoriaDelResiduo(grupoCon('cat-sueldo'), -200, CATEGORIAS)).toBe('cat-vuelto-gasto');
  expect(categoriaDelResiduo(grupoCon('cat-borrada'), -200, CATEGORIAS)).toBe('cat-vuelto-gasto');
  expect(categoriaDelResiduo(grupoCon(undefined), -200, CATEGORIAS)).toBe('cat-vuelto-gasto');
  expect(categoriaDelResiduo(undefined, 150, CATEGORIAS)).toBe('cat-vuelto-ingreso');
  expect(categoriaDelResiduo(grupoCon('cat-supermercado'), -200, [])).toBe('cat-vuelto-gasto');
});

test('txsEfectivas con categorías: el residuo negativo lleva la categoría del grupo', () => {
  const txs = [
    tx({ id: 'm1', type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ];
  const [virtual] = txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')], CATEGORIAS);
  expect(virtual).toMatchObject({ type: 'expense', amountMinor: 200, categoryId: 'cat-supermercado' });
  expect(virtual.id).toBe('virtual-g1-USD'); // los virtuales siguen sin persistirse
});

test('txsEfectivas: sin el 4.º parámetro el comportamiento es EXACTAMENTE el de v2', () => {
  const txs = [
    tx({ id: 'm1', type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ id: 'm2', type: 'expense', amountMinor: 500, groupId: 'g1' }),
  ];
  expect(txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')])[0].categoryId).toBe('cat-vuelto-gasto');
});

test('txsEfectivas bimoneda: la regla se evalúa por moneda contra el signo de CADA residuo', () => {
  const txs = [
    tx({ id: 'm1', type: 'income', amountMinor: 500, groupId: 'g1' }),                          // +$5,00
    tx({ id: 'm2', type: 'expense', accountId: 'acc-bnc', amountMinor: 73723, groupId: 'g1' }), // −Bs 737,23
  ];
  const efectivas = txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')], CATEGORIAS);
  expect(efectivas.find((t) => t.id === 'virtual-g1-USD')?.categoryId).toBe('cat-vuelto-ingreso');
  expect(efectivas.find((t) => t.id === 'virtual-g1-VES')?.categoryId).toBe('cat-supermercado');
});

test('el residuo con categoría suma en byCategory y consume presupuesto', () => {
  const conBudget: Category[] = [
    { ...CATEGORIAS[0], monthlyBudgetMinor: 10000 }, ...CATEGORIAS.slice(1),
  ];
  const txs = [
    tx({ type: 'income', amountMinor: 300, groupId: 'g1' }),
    tx({ type: 'expense', amountMinor: 500, categoryId: 'cat-otros-gastos', groupId: 'g1' }),
  ];
  const efectivas = txsEfectivas(txs, CUENTAS, [grupoCon('cat-supermercado')], conBudget);
  const resumen = monthSummary({
    txs: efectivas, accounts: CUENTAS, month: '2026-07', viewCurrency: 'USD', rates: [], activeSource: 'BCV',
  });
  expect(resumen.byCategory).toEqual([{ categoryId: 'cat-supermercado', expenseMinor: 200 }]);
  const estados = budgetStatus({
    txs: efectivas, accounts: CUENTAS, categories: conBudget, month: '2026-07', rates: [], activeSource: 'BCV',
  });
  expect(estados[0].gastoUsdMinor).toBe(200); // los $2 del residuo sí consumen Supermercado
});
