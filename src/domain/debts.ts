import type { Account, Debt, DebtDirection, Transaction } from './types';
import { vesToUsdMinor } from './rates';

export function buildDebt(opts: {
  direction: DebtDirection;
  concepto: string;
  amountMinor: number;
  accountId: string;
  nowIso: string;
}): Debt {
  if (!Number.isInteger(opts.amountMinor) || opts.amountMinor <= 0) throw new Error('Monto inválido');
  if (!opts.concepto.trim()) throw new Error('Ponle un concepto a la deuda');
  return {
    id: crypto.randomUUID(),
    direction: opts.direction,
    concepto: opts.concepto.trim(),
    amountMinor: opts.amountMinor,
    accountId: opts.accountId,
    status: 'pendiente',
    createdIso: opts.nowIso,
  };
}

/** Movimiento real de cierre: gasto (por pagar) o ingreso (por cobrar),
 *  en la moneda de la cuenta asignada, SIN conversión (decisión 2 del spec v2). */
export function buildSettleTx(debt: Debt, dateIso: string): Transaction {
  if (debt.status !== 'pendiente') throw new Error('La deuda ya está cerrada');
  const porPagar = debt.direction === 'porPagar';
  return {
    id: crypto.randomUUID(),
    type: porPagar ? 'expense' : 'income',
    accountId: debt.accountId,
    amountMinor: debt.amountMinor,
    date: dateIso,
    categoryId: porPagar ? 'cat-deudas' : 'cat-transferencia-recibida',
    note: `${porPagar ? 'Deuda pagada' : 'Deuda cobrada'}: ${debt.concepto}`,
    source: 'manual',
  };
}

export interface TotalesDeudas {
  debesUsdMinor: number | null;
  teDebenUsdMinor: number | null;
}

/** Totales de pendientes consolidados a USD SOLO para mostrar (spec v2 §1).
 *  Deuda en Bs sin tasa → el total de su dirección queda null. */
export function debtTotals(deudasPendientes: Debt[], accounts: Account[], vesPerUsd: number | null): TotalesDeudas {
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const sumar = (deudas: Debt[]): number | null => {
    let total = 0;
    for (const deuda of deudas) {
      const moneda = monedaDe.get(deuda.accountId) ?? 'USD';
      if (moneda === 'USD') total += deuda.amountMinor;
      else if (vesPerUsd === null) return null;
      else total += vesToUsdMinor(deuda.amountMinor, vesPerUsd);
    }
    return total;
  };
  return {
    debesUsdMinor: sumar(deudasPendientes.filter((deuda) => deuda.direction === 'porPagar')),
    teDebenUsdMinor: sumar(deudasPendientes.filter((deuda) => deuda.direction === 'porCobrar')),
  };
}
