import type { Account, Category, Currency, Rate, RateSource, Transaction } from './types';
import { convertMinor, effectiveRate } from './rates';

export interface ResumenMes {
  month: string;
  incomeMinor: number;
  expenseMinor: number;
  byCategory: { categoryId: string | null; expenseMinor: number }[];
  /** v3: ingresos por categoría del mes, desc por monto. `byCategory` sigue siendo SOLO gastos. */
  incomeByCategory: { categoryId: string | null; incomeMinor: number }[];
  dailyCumulative: { day: number; acumuladoMinor: number }[];
  omitidasSinTasa: number;
}

export function monthSummary(opts: {
  txs: Transaction[];
  accounts: Account[];
  month: string;
  viewCurrency: Currency;
  rates: Rate[];
  activeSource: RateSource;
}): ResumenMes {
  const monedaDe = new Map(opts.accounts.map((a) => [a.id, a.currency]));
  let incomeMinor = 0;
  let expenseMinor = 0;
  let omitidasSinTasa = 0;
  const porCategoria = new Map<string | null, number>();
  const porCategoriaIngreso = new Map<string | null, number>();
  const porDia = new Map<number, number>();

  for (const t of opts.txs) {
    if (!t.date.startsWith(opts.month)) continue;
    if (t.type !== 'expense' && t.type !== 'income') continue;
    const desde = monedaDe.get(t.accountId) ?? 'USD';
    let minor = t.amountMinor;
    if (desde !== opts.viewCurrency) {
      const rate = effectiveRate(t.date, opts.activeSource, opts.rates);
      if (!rate) {
        omitidasSinTasa++;
        continue;
      }
      minor = convertMinor(minor, desde, opts.viewCurrency, rate.vesPerUsd);
    }
    if (t.type === 'income') {
      incomeMinor += minor;
      const keyIngreso = t.categoryId ?? null;
      porCategoriaIngreso.set(keyIngreso, (porCategoriaIngreso.get(keyIngreso) ?? 0) + minor);
    } else {
      expenseMinor += minor;
      const key = t.categoryId ?? null;
      porCategoria.set(key, (porCategoria.get(key) ?? 0) + minor);
      const dia = Number(t.date.slice(8, 10));
      porDia.set(dia, (porDia.get(dia) ?? 0) + minor);
    }
  }

  const [y, m] = opts.month.split('-').map(Number);
  const diasDelMes = new Date(y, m, 0).getDate();
  let acumulado = 0;
  const dailyCumulative = Array.from({ length: diasDelMes }, (_, i) => {
    acumulado += porDia.get(i + 1) ?? 0;
    return { day: i + 1, acumuladoMinor: acumulado };
  });

  const byCategory = [...porCategoria.entries()]
    .map(([categoryId, gasto]) => ({ categoryId, expenseMinor: gasto }))
    .sort((a, b) => b.expenseMinor - a.expenseMinor);

  const incomeByCategory = [...porCategoriaIngreso.entries()]
    .map(([categoryId, ingreso]) => ({ categoryId, incomeMinor: ingreso }))
    .sort((a, b) => b.incomeMinor - a.incomeMinor);

  return { month: opts.month, incomeMinor, expenseMinor, byCategory, incomeByCategory, dailyCumulative, omitidasSinTasa };
}

export function lastMonths(baseMonth: string, n: number): string[] {
  const [y, m] = baseMonth.split('-').map(Number);
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(y, m - 1 - i, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
}

export interface EstadoPresupuesto {
  category: Category;
  gastoUsdMinor: number;
  presupuestoMinor: number;
  ratio: number;
}

export function budgetStatus(opts: {
  txs: Transaction[];
  accounts: Account[];
  categories: Category[];
  month: string;
  rates: Rate[];
  activeSource: RateSource;
}): EstadoPresupuesto[] {
  const resumenUsd = monthSummary({ ...opts, viewCurrency: 'USD' });
  return opts.categories
    .filter((c) => c.kind === 'expense' && (c.monthlyBudgetMinor ?? 0) > 0)
    .map((category) => {
      const gastoUsdMinor = resumenUsd.byCategory.find((b) => b.categoryId === category.id)?.expenseMinor ?? 0;
      const presupuestoMinor = category.monthlyBudgetMinor as number;
      return { category, gastoUsdMinor, presupuestoMinor, ratio: gastoUsdMinor / presupuestoMinor };
    });
}
