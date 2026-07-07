import type { Transaction } from './types';

export interface FiltroMovs {
  q?: string;
  accountId?: string;
  categoryId?: string;
  month?: string; // 'yyyy-mm'
}

export function filterTxs(txs: Transaction[], f: FiltroMovs): Transaction[] {
  const q = f.q?.trim().toLowerCase();
  return txs
    .filter((t) => !f.accountId || t.accountId === f.accountId || t.toAccountId === f.accountId)
    .filter((t) => !f.categoryId || t.categoryId === f.categoryId)
    .filter((t) => !f.month || t.date.startsWith(f.month))
    .filter((t) => !q || (t.note ?? '').toLowerCase().includes(q))
    .sort((a, b) => (b.date + (b.time ?? '')).localeCompare(a.date + (a.time ?? '')));
}
