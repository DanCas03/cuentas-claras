import { CuentasDB } from './db';
import type { CalEvent } from '../domain/types';
import { kindDeEvento, validarEvento, type Ocurrencia } from '../domain/calendar';
import { validarAsignacion } from '../domain/eventMatch';
import { esFechaIso } from '../domain/dates';

/** Alta o edición del evento (put del objeto COMPLETO: el editor debe traer `omitidas`).
 *  Rechaza cambiar la dirección del evento si eso dejaría movimientos incompatibles. */
export async function guardarEvento(d: CuentasDB, e: CalEvent): Promise<void> {
  validarEvento(e);
  await d.transaction('rw', d.calEvents, d.categories, d.transactions, async () => {
    const kind = kindDeEvento(e, await d.categories.toArray());
    if (kind === null) throw new Error('La categoría no existe');
    const asignadas = await d.transactions.where('eventId').equals(e.id).toArray();
    const incompatibles = asignadas.filter((t) => t.type !== kind).length;
    if (incompatibles > 0) throw new Error(`Desasigna primero los ${incompatibles} movimientos`);
    await d.calEvents.put(e);
  });
}

/** Borra el evento y desasigna sus movimientos en UNA transacción.
 *  Los movimientos NUNCA se borran: conservan categoría, monto, fecha y cuenta. */
export async function eliminarEvento(d: CuentasDB, eventId: string): Promise<number> {
  let desasignadas = 0;
  await d.transaction('rw', d.transactions, d.calEvents, async () => {
    desasignadas = await d.transactions.where('eventId').equals(eventId).modify((t) => {
      delete t.eventId;
      delete t.eventDate;
    });
    await d.calEvents.delete(eventId);
  });
  return desasignadas;
}

/** Asigna la tx a la ocurrencia y copia la categoría del evento. No toca monto/fecha/cuenta/tipo. */
export async function asignarEvento(d: CuentasDB, txId: string, o: Ocurrencia): Promise<void> {
  await d.transaction('rw', d.transactions, d.calEvents, d.categories, async () => {
    const tx = await d.transactions.get(txId);
    if (!tx || (tx.type !== 'income' && tx.type !== 'expense')) {
      throw new Error('Solo se asignan gastos e ingresos');
    }
    const evento = await d.calEvents.get(o.eventId);
    if (!evento) throw new Error('El evento no existe');
    if (!esFechaIso(o.date)) throw new Error('Fecha inválida');
    validarAsignacion(tx, evento, await d.categories.toArray());
    await d.transactions.update(txId, {
      eventId: evento.id, eventDate: o.date, categoryId: evento.categoryId,
    });
  });
}

/** Quita la asignación conservando la categoría. Idempotente. */
export async function desasignarEvento(d: CuentasDB, txId: string): Promise<void> {
  await d.transactions.where('id').equals(txId).modify((t) => {
    delete t.eventId;
    delete t.eventDate;
  });
}

/** Silencia una ocurrencia. Idempotente; rechaza si ya tiene movimientos asignados. */
export async function omitirOcurrencia(d: CuentasDB, eventId: string, date: string): Promise<void> {
  await d.transaction('rw', d.calEvents, d.transactions, async () => {
    const evento = await d.calEvents.get(eventId);
    if (!evento) throw new Error('El evento no existe');
    if (!esFechaIso(date)) throw new Error('Fecha inválida');
    const conMovimientos = await d.transactions.where('eventId').equals(eventId)
      .filter((t) => t.eventDate === date).count();
    if (conMovimientos > 0) throw new Error('Ya tiene movimientos asignados');
    const omitidas = evento.omitidas ?? [];
    if (omitidas.includes(date)) return;
    await d.calEvents.put({ ...evento, omitidas: [...omitidas, date].sort() });
  });
}

/** Reactiva una ocurrencia omitida. Si el array queda vacío, borra la clave. */
export async function reanudarOcurrencia(d: CuentasDB, eventId: string, date: string): Promise<void> {
  await d.transaction('rw', d.calEvents, async () => {
    const evento = await d.calEvents.get(eventId);
    if (!evento) throw new Error('El evento no existe');
    const quedan = (evento.omitidas ?? []).filter((f) => f !== date);
    const actualizado: CalEvent = { ...evento };
    if (quedan.length === 0) delete actualizado.omitidas;
    else actualizado.omitidas = quedan;
    await d.calEvents.put(actualizado);
  });
}
