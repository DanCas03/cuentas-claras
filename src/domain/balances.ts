import type { Account, Debt, Transaction } from './types';
import { vesToUsdMinor } from './rates';

export function accountBalanceMinor(accountId: string, txs: Transaction[]): number {
  let saldo = 0;
  for (const t of txs) {
    if (t.accountId === accountId) {
      if (t.type === 'income') saldo += t.amountMinor;
      else if (t.type === 'expense') saldo -= t.amountMinor;
      else if (t.type === 'transfer') saldo -= t.amountMinor;
      else saldo += t.amountMinor; // adjustment: delta con signo
    }
    if (t.type === 'transfer' && t.toAccountId === accountId) {
      saldo += t.toAmountMinor ?? t.amountMinor;
    }
  }
  return saldo;
}

export interface PatrimonioLinea {
  account: Account;
  balanceMinor: number;
  balanceUsdMinor: number | null;
}

export interface Patrimonio {
  lines: PatrimonioLinea[];
  disposicionUsdMinor: number | null;
  ahorroUsdMinor: number | null;
  totalUsdMinor: number | null;
  /** v2 — vista 1 del slider: cuentas con enDisposicion (default !isSavings). */
  aDisposicionUsdMinor: number | null;
  /** v2 — vista 2: total − deudas por pagar pendientes (convertidas a USD). */
  netoUsdMinor: number | null;
}

/** Patrimonio consolidado en USD. vesPerUsd null → sin tasa: lo no convertible queda null. */
export function patrimonio(
  accounts: Account[],
  txs: Transaction[],
  vesPerUsd: number | null,
  deudasPorPagarPendientes: Debt[] = [],
): Patrimonio {
  const activas = accounts.filter((a) => !a.archived);
  const lines: PatrimonioLinea[] = activas.map((account) => {
    const balanceMinor = accountBalanceMinor(account.id, txs);
    const balanceUsdMinor =
      account.currency === 'USD'
        ? balanceMinor
        : vesPerUsd === null
          ? null
          : vesToUsdMinor(balanceMinor, vesPerUsd);
    return { account, balanceMinor, balanceUsdMinor };
  });

  const suma = (grupo: PatrimonioLinea[]): number | null =>
    grupo.some((l) => l.balanceUsdMinor === null)
      ? null
      : grupo.reduce((acc, l) => acc + (l.balanceUsdMinor as number), 0);

  const disposicion = lines.filter((l) => !l.account.isSavings);
  const ahorro = lines.filter((l) => l.account.isSavings);
  const disposicionUsdMinor = suma(disposicion);
  const ahorroUsdMinor = suma(ahorro);
  const totalUsdMinor =
    disposicionUsdMinor === null || ahorroUsdMinor === null ? null : disposicionUsdMinor + ahorroUsdMinor;

  const aDisposicion = lines.filter((l) => l.account.enDisposicion ?? !l.account.isSavings);
  const aDisposicionUsdMinor = suma(aDisposicion);

  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  let deudasUsdMinor: number | null = 0;
  for (const deuda of deudasPorPagarPendientes) {
    const moneda = monedaDe.get(deuda.accountId) ?? 'VES';
    if (moneda === 'USD') deudasUsdMinor += deuda.amountMinor;
    else if (vesPerUsd === null) { deudasUsdMinor = null; break; }
    else deudasUsdMinor += vesToUsdMinor(deuda.amountMinor, vesPerUsd);
  }
  const netoUsdMinor =
    totalUsdMinor === null || deudasUsdMinor === null ? null : totalUsdMinor - deudasUsdMinor;

  return { lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor, aDisposicionUsdMinor, netoUsdMinor };
}
