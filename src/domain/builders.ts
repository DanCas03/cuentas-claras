import type { Account, Transaction } from './types';
import { convertMinor } from './rates';

export function buildManualTx(opts: {
  type: 'expense' | 'income';
  accountId: string;
  amountMinor: number;
  categoryId: string;
  dateIso: string;
  note?: string;
}): Transaction {
  if (!Number.isInteger(opts.amountMinor) || opts.amountMinor <= 0) throw new Error('Monto inválido');
  return {
    id: crypto.randomUUID(),
    type: opts.type,
    accountId: opts.accountId,
    amountMinor: opts.amountMinor,
    date: opts.dateIso,
    categoryId: opts.categoryId,
    note: opts.note,
    source: 'manual',
  };
}

export function buildTransfer(opts: {
  fromAccount: Account;
  toAccount: Account;
  amountMinor: number;
  dateIso: string;
  vesPerUsd?: number;
  note?: string;
}): Transaction {
  const { fromAccount, toAccount } = opts;
  if (fromAccount.id === toAccount.id) throw new Error('Origen y destino no pueden ser la misma cuenta');
  if (!Number.isInteger(opts.amountMinor) || opts.amountMinor <= 0) throw new Error('Monto inválido');
  const cruzaMoneda = fromAccount.currency !== toAccount.currency;
  if (cruzaMoneda && (opts.vesPerUsd === undefined || opts.vesPerUsd <= 0)) throw new Error('Falta la tasa para convertir');
  const toAmountMinor = cruzaMoneda
    ? convertMinor(opts.amountMinor, fromAccount.currency, toAccount.currency, opts.vesPerUsd as number)
    : opts.amountMinor;
  return {
    id: crypto.randomUUID(),
    type: 'transfer',
    accountId: fromAccount.id,
    toAccountId: toAccount.id,
    amountMinor: opts.amountMinor,
    toAmountMinor,
    rateUsed: cruzaMoneda ? opts.vesPerUsd : undefined,
    date: opts.dateIso,
    note: opts.note,
    source: 'manual',
  };
}

export function buildAdjustment(opts: {
  accountId: string;
  currentBalanceMinor: number;
  realBalanceMinor: number;
  dateIso: string;
  note?: string;
}): Transaction {
  const delta = opts.realBalanceMinor - opts.currentBalanceMinor;
  if (delta === 0) throw new Error('No hay diferencia que ajustar');
  return {
    id: crypto.randomUUID(),
    type: 'adjustment',
    accountId: opts.accountId,
    amountMinor: delta,
    date: opts.dateIso,
    note: opts.note ?? 'Ajuste de saldo',
    source: 'adjust',
  };
}
