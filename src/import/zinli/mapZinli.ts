import type { Category, Rule, Transaction } from '../../domain/types';
import { matchCategory } from '../../domain/rules';
import type { ZinliMovement } from './parseZinli';

export function zinliImportKey(m: ZinliMovement): string {
  return `zinli:${m.referencia}`;
}

export interface ZinliDraft {
  tx: Transaction;
  duplicate: boolean;
  matchedRuleId: string | null;
}

export function mapZinliMovements(movs: ZinliMovement[], opts: {
  accountId: string;
  existingKeys: Set<string>;
  rules: Rule[];
  categories: Category[];
}): ZinliDraft[] {
  const hayTransferencia = opts.categories.some((c) => c.id === 'cat-transferencia-recibida');
  return movs
    .filter((m) => m.amountMinor !== 0)
    .map((m) => {
      // El texto de matching debe incluir los ":" para que sea coherente con `note` (línea
      // de abajo): el patrón por defecto que la UI siembra es el note completo con dos
      // puntos ("Pagaste: TIENDA…"), y antes de este fix (Important #7) esa cadena nunca
      // era substring del texto de matching (sin ":"), así que la regla nunca re-matcheaba.
      const regla = matchCategory(`${m.motivo}: ${m.descripcion}`, opts.rules);
      const esIngreso = m.amountMinor > 0;
      let categoryId = regla?.categoryId;
      if (!categoryId && esIngreso && hayTransferencia) categoryId = 'cat-transferencia-recibida';
      const importKey = zinliImportKey(m);
      const tx: Transaction = {
        id: crypto.randomUUID(),
        type: esIngreso ? 'income' : 'expense',
        accountId: opts.accountId,
        amountMinor: Math.abs(m.amountMinor),
        date: m.date,
        categoryId,
        note: `${m.motivo}: ${m.descripcion}`,
        source: 'zinli',
        importKey,
      };
      return { tx, duplicate: opts.existingKeys.has(importKey), matchedRuleId: regla?.id ?? null };
    });
}
