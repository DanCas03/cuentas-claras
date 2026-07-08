import type { Account, Category, Rate, Rule, Settings, Transaction } from '../domain/types';
import { CuentasDB } from '../db/db';

export interface BackupFile {
  schemaVersion: 1;
  exportedAt: string; // ISO completo
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  rules: Rule[];
  rates: Rate[];
  settings: Settings[];
}

const TABLAS = ['accounts', 'transactions', 'categories', 'rules', 'rates', 'settings'] as const;

export async function exportBackup(d: CuentasDB): Promise<BackupFile> {
  const [accounts, transactions, categories, rules, rates, settings] = await Promise.all([
    d.accounts.toArray(),
    d.transactions.toArray(),
    d.categories.toArray(),
    d.rules.toArray(),
    d.rates.toArray(),
    d.settings.toArray(),
  ]);
  return { schemaVersion: 1, exportedAt: new Date().toISOString(), accounts, transactions, categories, rules, rates, settings };
}

export function validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string } {
  if (typeof data !== 'object' || data === null) return { ok: false, error: 'El archivo no es un respaldo de Cuentas Claras' };
  const b = data as Partial<BackupFile>;
  if (b.schemaVersion !== 1) return { ok: false, error: `Versión de respaldo no soportada: ${String(b.schemaVersion)}` };
  for (const tabla of TABLAS) {
    if (!Array.isArray(b[tabla])) return { ok: false, error: `Al respaldo le falta la tabla "${tabla}"` };
  }
  return { ok: true, backup: b as BackupFile };
}

/** Importar reemplaza TODO el contenido, en una sola transacción (todo-o-nada). */
export async function importBackup(d: CuentasDB, b: BackupFile): Promise<void> {
  await d.transaction('rw', [d.accounts, d.transactions, d.categories, d.rules, d.rates, d.settings], async () => {
    await Promise.all(TABLAS.map((t) => d[t].clear()));
    await d.accounts.bulkAdd(b.accounts);
    await d.transactions.bulkAdd(b.transactions);
    await d.categories.bulkAdd(b.categories);
    await d.rules.bulkAdd(b.rules);
    await d.rates.bulkAdd(b.rates);
    await d.settings.bulkAdd(b.settings);
  });
}

export function backupFilename(dateIso: string): string {
  return `cuentas-claras-respaldo-${dateIso}.json`;
}
