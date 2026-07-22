import type { Account, Category, Debt, Rate, Rule, Settings, Transaction, TxGroup } from '../domain/types';
import { CuentasDB } from '../db/db';

export interface BackupFile {
  schemaVersion: 2;
  exportedAt: string; // ISO completo
  accounts: Account[];
  transactions: Transaction[];
  categories: Category[];
  rules: Rule[];
  rates: Rate[];
  settings: Settings[];
  debts: Debt[];
  txGroups: TxGroup[];
}

const TABLAS = ['accounts', 'transactions', 'categories', 'rules', 'rates', 'settings', 'debts', 'txGroups'] as const;

export async function exportBackup(d: CuentasDB): Promise<BackupFile> {
  const [accounts, transactions, categories, rules, rates, settings, debts, txGroups] = await Promise.all([
    d.accounts.toArray(),
    d.transactions.toArray(),
    d.categories.toArray(),
    d.rules.toArray(),
    d.rates.toArray(),
    d.settings.toArray(),
    d.debts.toArray(),
    d.txGroups.toArray(),
  ]);
  return {
    schemaVersion: 2, exportedAt: new Date().toISOString(),
    accounts, transactions, categories, rules, rates, settings, debts, txGroups,
  };
}

export function validateBackup(data: unknown): { ok: true; backup: BackupFile } | { ok: false; error: string } {
  if (typeof data !== 'object' || data === null) return { ok: false, error: 'El archivo no es un respaldo de Cuentas Claras' };
  const b = data as Partial<BackupFile>;
  if ((b.schemaVersion as number) === 1) {
    return { ok: false, error: 'Este respaldo es de la versión 1 y la v2 no es compatible: re-ingresa o re-importa tus datos' };
  }
  if (b.schemaVersion !== 2) return { ok: false, error: `Versión de respaldo no soportada: ${String(b.schemaVersion)}` };
  for (const tabla of TABLAS) {
    if (!Array.isArray(b[tabla])) return { ok: false, error: `Al respaldo le falta la tabla "${tabla}"` };
  }
  return { ok: true, backup: b as BackupFile };
}

/** Importar reemplaza TODO el contenido, en una sola transacción (todo-o-nada). */
export async function importBackup(d: CuentasDB, b: BackupFile): Promise<void> {
  await d.transaction('rw', [d.accounts, d.transactions, d.categories, d.rules, d.rates, d.settings, d.debts, d.txGroups], async () => {
    await Promise.all(TABLAS.map((t) => d[t].clear()));
    await d.accounts.bulkAdd(b.accounts);
    await d.transactions.bulkAdd(b.transactions);
    await d.categories.bulkAdd(b.categories);
    await d.rules.bulkAdd(b.rules);
    await d.rates.bulkAdd(b.rates);
    await d.settings.bulkAdd(b.settings);
    await d.debts.bulkAdd(b.debts);
    await d.txGroups.bulkAdd(b.txGroups);
  });
}

export function backupFilename(dateIso: string): string {
  return `cuentas-claras-respaldo-${dateIso}.json`;
}
