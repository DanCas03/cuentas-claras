import { CuentasDB } from './db';
import { buildSettleTx } from '../domain/debts';

/** Cierra la deuda creando su movimiento real, todo-o-nada e idempotente:
 *  si ya está cerrada, no hace nada (guard dentro de la transacción). */
export async function settleDebt(d: CuentasDB, debtId: string, dateIso: string): Promise<void> {
  await d.transaction('rw', d.debts, d.transactions, async () => {
    const debt = await d.debts.get(debtId);
    if (!debt) throw new Error('La deuda no existe');
    if (debt.status !== 'pendiente') return;
    const tx = buildSettleTx(debt, dateIso);
    await d.transactions.add(tx);
    await d.debts.update(debtId, { status: 'cerrada', settledTxId: tx.id, settledIso: dateIso });
  });
}
