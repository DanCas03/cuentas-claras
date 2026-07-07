import Dexie, { type Table } from 'dexie';
import type { Account, Category, Rate, Rule, Settings, Transaction } from '../domain/types';

export class CuentasDB extends Dexie {
  accounts!: Table<Account, string>;
  transactions!: Table<Transaction, string>;
  categories!: Table<Category, string>;
  rates!: Table<Rate, [string, string]>;
  rules!: Table<Rule, string>;
  settings!: Table<Settings, string>;

  constructor(name = 'cuentas-claras') {
    super(name);
    this.version(1).stores({
      accounts: 'id',
      transactions: 'id, date, accountId, categoryId, importKey',
      categories: 'id',
      rates: '[date+source], date',
      rules: 'id',
      settings: 'id',
    });
  }
}

export const db = new CuentasDB();

const CUENTAS_SEED: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-zinli', name: 'Zinli', currency: 'USD', kind: 'wallet', isSavings: false, archived: false },
  { id: 'acc-binance', name: 'Binance', currency: 'USD', kind: 'snapshot', isSavings: true, archived: false },
  { id: 'acc-efectivo', name: 'Efectivo', currency: 'USD', kind: 'cash', isSavings: false, archived: false },
  { id: 'acc-efectivo-ahorro', name: 'Efectivo ahorro', currency: 'USD', kind: 'cash', isSavings: true, archived: false },
];

const CATEGORIAS_SEED: Category[] = [
  { id: 'cat-supermercado', name: 'Supermercado', icon: '🛒', color: '#4ade80', kind: 'expense' },
  { id: 'cat-comida-fuera', name: 'Comida fuera', icon: '🍔', color: '#fbbf24', kind: 'expense' },
  { id: 'cat-transporte', name: 'Transporte', icon: '🚌', color: '#60a5fa', kind: 'expense' },
  { id: 'cat-salud', name: 'Salud', icon: '💊', color: '#f87171', kind: 'expense' },
  { id: 'cat-servicios', name: 'Servicios', icon: '💡', color: '#facc15', kind: 'expense' },
  { id: 'cat-comisiones', name: 'Comisiones bancarias', icon: '🏦', color: '#94a3b8', kind: 'expense' },
  { id: 'cat-hogar', name: 'Hogar', icon: '🏠', color: '#a78bfa', kind: 'expense' },
  { id: 'cat-ocio', name: 'Ocio/Juegos', icon: '🎮', color: '#f472b6', kind: 'expense' },
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#22d3ee', kind: 'expense' },
  { id: 'cat-ropa', name: 'Ropa', icon: '👕', color: '#fb923c', kind: 'expense' },
  { id: 'cat-otros-gastos', name: 'Otros gastos', icon: '📦', color: '#9ca3af', kind: 'expense' },
  { id: 'cat-pago-movil-recibido', name: 'Pago móvil recibido', icon: '📲', color: '#4ade80', kind: 'income' },
  { id: 'cat-transferencia-recibida', name: 'Transferencia recibida', icon: '🔁', color: '#38bdf8', kind: 'income' },
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-otros-ingresos', name: 'Otros ingresos', icon: '➕', color: '#cbd5e1', kind: 'income' },
];

const SETTINGS_SEED: Settings = { id: 'main', viewCurrency: 'USD', rateSource: 'BCV', lastAccountId: 'acc-bnc' };

/** Crea cuentas, categorías y settings iniciales solo si la tabla está vacía. */
export async function seedIfEmpty(d: CuentasDB): Promise<void> {
  await d.transaction('rw', d.accounts, d.categories, d.settings, async () => {
    if ((await d.accounts.count()) === 0) await d.accounts.bulkAdd(CUENTAS_SEED);
    if ((await d.categories.count()) === 0) await d.categories.bulkAdd(CATEGORIAS_SEED);
    if ((await d.settings.count()) === 0) await d.settings.add(SETTINGS_SEED);
  });
}

export async function getSettings(d: CuentasDB): Promise<Settings> {
  return (await d.settings.get('main')) ?? SETTINGS_SEED;
}

export async function saveSettings(d: CuentasDB, patch: Partial<Omit<Settings, 'id'>>): Promise<void> {
  const actual = await getSettings(d);
  await d.settings.put({ ...actual, ...patch });
}
