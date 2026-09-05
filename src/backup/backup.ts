import type { Account, CalEvent, Category, Debt, Rate, Rule, Settings, Transaction, TxGroup } from '../domain/types';
import { CuentasDB } from '../db/db';

export interface BackupFile {
  schemaVersion: 3;
  exportedAt: string; // ISO completo
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  rules: Rule[];
  rates: Rate[];
  settings: Settings[];
  debts: Debt[];
  txGroups: TxGroup[];
  calEvents: CalEvent[];
}

const TABLAS_V2 = ['accounts', 'transactions', 'categories', 'rules', 'rates', 'settings', 'debts', 'txGroups'] as const;
const TABLAS = [...TABLAS_V2, 'calEvents'] as const;

export async function exportBackup(d: CuentasDB): Promise<BackupFile> {
  const [accounts, transactions, categories, rules, rates, settings, debts, txGroups, calEvents] = await Promise.all([
    d.accounts.toArray(),
    d.transactions.toArray(),
    d.categories.toArray(),
    d.rules.toArray(),
    d.rates.toArray(),
    d.settings.toArray(),
    d.debts.toArray(),
    d.txGroups.toArray(),
    d.calEvents.toArray(),
  ]);
  return {
    schemaVersion: 3, exportedAt: new Date().toISOString(),
    accounts, transactions, categories, rules, rates, settings, debts, txGroups, calEvents,
  };
}

function faltaTabla(b: Record<string, unknown>, tablas: readonly string[]): string | null {
  for (const tabla of tablas) if (!Array.isArray(b[tabla])) return tabla;
  return null;
}

/** v3 exige las 9 tablas. v2 (8 tablas) se acepta normalizado en memoria con calEvents: [] —
 *  v2→v3 es puramente aditivo (decisión 16 del spec v3). v1 sigue rechazado (decisión 7 del spec v2). */
export function validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string } {
  if (typeof data !== 'object' || data === null) return { ok: false, error: 'El archivo no es un respaldo de Cuentas Claras' };
  const b = data as Record<string, unknown> & Partial<BackupFile>;
  const version = b.schemaVersion as number | undefined;
  if (version === 1) {
    return { ok: false, error: 'Este respaldo es de la versión 1 y ya no es compatible: re-ingresa o re-importa tus datos' };
  }
  if (version === 2) {
    const falta = faltaTabla(b, TABLAS_V2);
    if (falta) return { ok: false, error: `Al respaldo le falta la tabla "${falta}"` };
    return { ok: true, backup: { ...(b as unknown as Omit<BackupFile, 'schemaVersion' | 'calEvents'>), schemaVersion: 3, calEvents: [] } };
  }
  if (version !== 3) return { ok: false, error: `Versión de respaldo no soportada: ${String(version)}` };
  const falta = faltaTabla(b, TABLAS);
  if (falta) return { ok: false, error: `Al respaldo le falta la tabla "${falta}"` };
  return { ok: true, backup: b as BackupFile };
}

/** Importar reemplaza TODO el contenido, en una sola transacción (todo-o-nada). */
export async function importBackup(d: CuentasDB, b: BackupFile): Promise<void> {
  await d.transaction('rw', [d.accounts, d.transactions, d.categories, d.rules, d.rates, d.settings, d.debts, d.txGroups, d.calEvents], async () => {
    await Promise.all(TABLAS.map((t) => d[t].clear()));
    await d.accounts.bulkAdd(b.accounts);
    await d.transactions.bulkAdd(b.transactions);
    await d.categories.bulkAdd(b.categories);
    await d.rules.bulkAdd(b.rules);
    await d.rates.bulkAdd(b.rates);
    await d.settings.bulkAdd(b.settings);
    await d.debts.bulkAdd(b.debts);
    await d.txGroups.bulkAdd(b.txGroups);
    await d.calEvents.bulkAdd(b.calEvents);
  });
}

export function backupFilename(dateIso: string): string {
  return `cuentas-claras-respaldo-${dateIso}.json`;
}
