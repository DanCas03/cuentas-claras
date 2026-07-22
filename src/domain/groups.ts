import type { Account, Currency, Transaction, TxGroup } from './types';

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

/** Sustituye los movimientos agrupados por su residuo virtual «Vuelto».
 *  SOLO para agregaciones (mes, comparativa, gráficas, presupuestos).
 *  NUNCA para saldos: accountBalanceMinor/patrimonio usan las txs crudas.
 *  Los virtuales (`virtual-…`) jamás se persisten. */
export function txsEfectivas(txs: Transaction[], accounts: Account[], grupos: TxGroup[] = []): Transaction[] {
  const notaDe = new Map(grupos.map((g) => [g.id, g.nota]));
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
    for (const { currency, residuoMinor } of residuosDeGrupo(miembros, accounts)) {
      const deLaMoneda = miembros.filter((t) => (monedaDe.get(t.accountId) ?? 'USD') === currency);
      virtuales.push({
        id: `virtual-${groupId}-${currency}`,
        type: residuoMinor > 0 ? 'income' : 'expense',
        accountId: masReciente(deLaMoneda).accountId,
        amountMinor: Math.abs(residuoMinor),
        date: ultimo.date,
        time: ultimo.time,
        categoryId: residuoMinor > 0 ? 'cat-vuelto-ingreso' : 'cat-vuelto-gasto',
        note: notaDe.get(groupId) || 'Vuelto de grupo transaccional',
        source: 'manual',
        groupId,
      });
    }
  }
  return [...sueltas, ...virtuales];
}
