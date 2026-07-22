import type { Category, Rule, Transaction } from '../../domain/types';
import { matchCategory } from '../../domain/rules';
import type { BncRow } from './parseBncTxt';

export function bncImportKey(row: BncRow): string {
  return `bnc:${row.date}:${row.referencia}:${row.time}:${row.amountMinor}`;
}

export interface BncDraft {
  tx: Transaction;
  saldoMinor: number;
  duplicate: boolean;
  isCommission: boolean;
  pairRef: string | null;
  matchedRuleId: string | null;
}

export function mapBncRows(rows: BncRow[], opts: {
  accountId: string;
  existingKeys: Set<string>;
  rules: Rule[];
  categories: Category[];
}): BncDraft[] {
  const porReferencia = new Map<string, number>();
  for (const r of rows) {
    if (r.referencia !== '' && r.referencia !== '0') {
      porReferencia.set(r.referencia, (porReferencia.get(r.referencia) ?? 0) + 1);
    }
  }
  const idComisiones = opts.categories.find((c) => c.id === 'cat-comisiones')?.id ?? null;
  const idPagoMovil = opts.categories.find((c) => c.id === 'cat-pago-movil-recibido')?.id ?? null;

  return rows
    .filter((r) => r.amountMinor !== 0)
    .map((r) => {
      const texto = `${r.tipOperacion} ${r.descripcion}`;
      const isCommission = /comisi[oó]n/i.test(r.tipOperacion);
      const regla = matchCategory(texto, opts.rules);
      const esIngreso = r.amountMinor > 0;
      let categoryId: string | undefined = regla?.categoryId;
      if (!categoryId && isCommission && idComisiones) categoryId = idComisiones;
      if (!categoryId && esIngreso && idPagoMovil && /pago m[oó]vil/i.test(texto)) categoryId = idPagoMovil;

      const importKey = bncImportKey(r);
      const tx: Transaction = {
        id: crypto.randomUUID(),
        type: esIngreso ? 'income' : 'expense',
        accountId: opts.accountId,
        amountMinor: Math.abs(r.amountMinor),
        date: r.date,
        time: r.time,
        categoryId,
        note: r.descripcion,
        source: 'bnc',
        importKey,
      };
      return {
        tx,
        saldoMinor: r.saldoMinor,
        duplicate: opts.existingKeys.has(importKey),
        isCommission,
        pairRef: (porReferencia.get(r.referencia) ?? 0) >= 2 ? r.referencia : null,
        matchedRuleId: regla?.id ?? null,
      };
    });
}

export type GapStatus = 'primera-importacion' | 'solapado' | 'continuo' | 'hueco';

/** rows en orden de archivo (descendente). `previo` viene de Account.bncLastImportKey/bncLastSaldoMinor. */
export function detectGap(rows: BncRow[], previo: { importKey?: string; saldoMinor?: number }): GapStatus {
  if (!previo.importKey || previo.saldoMinor === undefined) return 'primera-importacion';
  if (rows.some((r) => bncImportKey(r) === previo.importKey)) return 'solapado';
  const masVieja = rows[rows.length - 1];
  if (!masVieja) return 'hueco';
  const saldoAntes = masVieja.saldoMinor - masVieja.amountMinor;
  return saldoAntes === previo.saldoMinor ? 'continuo' : 'hueco';
}

export function newestRow(rows: BncRow[]): BncRow | null {
  return rows[0] ?? null;
}
