import type { Account, Transaction } from './types';
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
}

/** Patrimonio consolidado en USD. vesPerUsd null → sin tasa: lo no convertible queda null. */
export function patrimonio(accounts: Account[], txs: Transaction[], vesPerUsd: number | null): Patrimonio {
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

  return { lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor };
}
