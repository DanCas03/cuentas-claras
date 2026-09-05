import type { Account, Category, Currency, Transaction, TxGroup } from './types';

export interface ResiduoMoneda {
  currency: Currency;
  /** Firmado: + ingreso (vuelto a favor), − gasto. */
  residuoMinor: number;
}

/** Σ firmada de los miembros por moneda (ingresos +, gastos −).
 *  Transferencias/ajustes se ignoran defensivamente (no son agrupables). */
export function residuosDeGrupo(miembros: Transaction[], accounts: Account[]): ResiduoMoneda[] {
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const porMoneda = new Map<Currency, number>();
  for (const t of miembros) {
    if (t.type !== 'income' && t.type !== 'expense') continue;
    const moneda = monedaDe.get(t.accountId) ?? 'USD';
    const firmado = t.type === 'income' ? t.amountMinor : -t.amountMinor;
    porMoneda.set(moneda, (porMoneda.get(moneda) ?? 0) + firmado);
  }
  return [...porMoneda.entries()]
    .filter(([, residuo]) => residuo !== 0)
    .map(([currency, residuoMinor]) => ({ currency, residuoMinor }));
}

function masReciente(txs: Transaction[]): Transaction {
  return txs.reduce((a, b) => (`${b.date} ${b.time ?? ''}` > `${a.date} ${a.time ?? ''}` ? b : a));
}

/** Categoría del residuo virtual (v3). La categoría del grupo se aplica SOLO si su kind
 *  coincide con el signo del residuo (expense ↔ residuo < 0, income ↔ residuo > 0);
 *  si no coincide, no existe o no hay categorías, cae en «Vuelto» del signo (v2). */
export function categoriaDelResiduo(grupo: TxGroup | undefined, residuoMinor: number, categories: Category[]): string {
  const necesario = residuoMinor > 0 ? 'income' : 'expense';
  const elegida = grupo?.categoryId ? categories.find((c) => c.id === grupo.categoryId) : undefined;
  if (elegida && elegida.kind === necesario) return elegida.id;
  return residuoMinor > 0 ? 'cat-vuelto-ingreso' : 'cat-vuelto-gasto';
}

/** Sustituye los movimientos agrupados por su residuo virtual «Vuelto».
 *  SOLO para agregaciones (mes, comparativa, gráficas, presupuestos).
 *  NUNCA para saldos: accountBalanceMinor/patrimonio usan las txs crudas.
 *  Los virtuales (`virtual-…`) jamás se persisten. */
export function txsEfectivas(
  txs: Transaction[], accounts: Account[], grupos: TxGroup[] = [], categories: Category[] = [],
): Transaction[] {
  const grupoDe = new Map(grupos.map((g) => [g.id, g]));
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const sueltas: Transaction[] = [];
  const porGrupo = new Map<string, Transaction[]>();
  for (const t of txs) {
    if (t.groupId) {
      const lista = porGrupo.get(t.groupId) ?? [];
      lista.push(t);
      porGrupo.set(t.groupId, lista);
    } else {
      sueltas.push(t);
    }
  }
  const virtuales: Transaction[] = [];
  for (const [groupId, miembros] of porGrupo) {
    const ultimo = masReciente(miembros);
    const grupo = grupoDe.get(groupId);
    for (const { currency, residuoMinor } of residuosDeGrupo(miembros, accounts)) {
      const deLaMoneda = miembros.filter((t) => (monedaDe.get(t.accountId) ?? 'USD') === currency);
      virtuales.push({
        id: `virtual-${groupId}-${currency}`,
        type: residuoMinor > 0 ? 'income' : 'expense',
        accountId: masReciente(deLaMoneda).accountId,
        amountMinor: Math.abs(residuoMinor),
        date: ultimo.date,
        time: ultimo.time,
        categoryId: categoriaDelResiduo(grupo, residuoMinor, categories),
        note: grupo?.nota || 'Vuelto de grupo transaccional',
        source: 'manual',
        groupId,
      });
    }
  }
  return [...sueltas, ...virtuales];
}
