import type { Rule, Transaction } from '../domain/types';
import { CuentasDB } from './db';

/** Import todo-o-nada: si algo falla, no entra nada (spec §5). */
export async function commitImport(d: CuentasDB, opts: {
  txs: Transaction[];
  newRules?: Rule[];
  accountPatch?: { id: string; bncLastImportKey: string; bncLastSaldoMinor: number };
}): Promise<void> {
  await d.transaction('rw', d.transactions, d.rules, d.accounts, async () => {
    if (opts.txs.length > 0) await d.transactions.bulkAdd(opts.txs);
    if (opts.newRules && opts.newRules.length > 0) await d.rules.bulkAdd(opts.newRules);
    if (opts.accountPatch) {
      await d.accounts.update(opts.accountPatch.id, {
        bncLastImportKey: opts.accountPatch.bncLastImportKey,
        bncLastSaldoMinor: opts.accountPatch.bncLastSaldoMinor,
      });
    }
  });
}
