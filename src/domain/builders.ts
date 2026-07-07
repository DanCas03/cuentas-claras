import type { Transaction } from './types';

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
