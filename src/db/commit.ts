import type { Rule, Transaction } from '../domain/types';
import { CuentasDB } from './db';

/** Import todo-o-nada: si algo falla, no entra nada (spec §5). */
export async function commitImport(d: CuentasDB, opts: {
  txs: Transaction[];
  newRules?: Rule[];
  accountPatch?: { id: string; bncLastImportKey: string; bncLastSaldoMinor: number };
}): Promise<void> {
  await d.transaction('rw', d.transactions, d.rules, d.accounts, async () => {
    if (opts.txs.length > 0) {
      // Backstop anti-duplicación (Important #3): la puerta normal es el filtro de la UI
      // (`incluir && !duplicate`) sobre un snapshot de existingKeys tomado al Analizar. Ese
      // snapshot puede quedar obsoleto si otra pestaña/instancia de la misma IndexedDB
      // confirmó los mismos movimientos mientras tanto (misma PWA instalada + pestaña del
      // navegador comparten la BD). commitImport NO deduplica — solo revalida dentro de la
      // MISMA transacción y aborta todo-o-nada si aparece una importKey que ya existe.
      const keysNuevas = opts.txs.map((t) => t.importKey).filter((k): k is string => k !== undefined);
      if (keysNuevas.length > 0) {
        const yaExisten = await d.transactions.where('importKey').anyOf(keysNuevas).count();
        if (yaExisten > 0) {
          throw new Error('Alguno de estos movimientos ya fue importado (en otra pestaña/ventana). Vuelve a analizar el archivo.');
        }
      }
      await d.transactions.bulkAdd(opts.txs);
    }
    if (opts.newRules && opts.newRules.length > 0) await d.rules.bulkAdd(opts.newRules);
    if (opts.accountPatch) {
      await d.accounts.update(opts.accountPatch.id, {
        bncLastImportKey: opts.accountPatch.bncLastImportKey,
        bncLastSaldoMinor: opts.accountPatch.bncLastSaldoMinor,
      });
    }
  });
}
