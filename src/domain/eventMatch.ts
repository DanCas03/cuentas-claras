import type { Account, CalEvent, Category, Transaction } from './types';
import { kindDeEvento, ocurrenciasEnRango, type Ocurrencia, type OcurrenciaVista } from './calendar';
import { addDays, diffDias } from './dates';

export interface TxParaSugerir {
  type: 'income' | 'expense';
  date: string;
  accountId: string;
  amountMinor: number;
}

export interface CtxSugerencia {
  eventos: CalEvent[];
  txs: Transaction[];
  accounts: Account[];
  categories: Category[];
  hoy: string;
}

/** Ventana asimétrica: la ocurrencia puede ser hasta 31 días ANTES del movimiento (pago
 *  atrasado) y hasta 7 días DESPUÉS (pago adelantado). Decisión 12 del spec v3. */
export const VENTANA_ATRAS_DIAS = 31;
export const VENTANA_ADELANTE_DIAS = 7;
export const TOLERANCIA_MONTO = 0.10;

export interface Candidata {
  ocurrencia: OcurrenciaVista;
  /** |diffDias(ocurrencia.date, tx.date)| */
  distanciaDias: number;
  /** null = el evento no tiene monto esperado, o la tx aún no tiene monto (amountMinor ≤ 0). */
  montoCoincide: boolean | null;
  sugerible: boolean;
}

export function candidatasParaTx(
  tx: TxParaSugerir, ctx: CtxSugerencia, opts?: { incluirHechas?: boolean },
): Candidata[] {
  const desde = addDays(tx.date, -VENTANA_ATRAS_DIAS);
  const hasta = addDays(tx.date, VENTANA_ADELANTE_DIAS);
  const elegibles = ctx.eventos.filter((e) =>
    kindDeEvento(e, ctx.categories) === tx.type
    && (e.accountId === undefined || e.accountId === tx.accountId));
  const ocurrencias = ocurrenciasEnRango({
    eventos: elegibles, txs: ctx.txs, accounts: ctx.accounts, desde, hasta, hoy: ctx.hoy,
  });

  const out: Candidata[] = [];
  for (const o of ocurrencias) {
    if (o.estado === 'omitida') continue;
    if (o.estado === 'hecha' && !opts?.incluirHechas) continue;
    const esperado = o.evento.amountMinor;
    const montoCoincide = esperado === undefined || tx.amountMinor <= 0
      ? null
      : Math.abs(tx.amountMinor - esperado) <= TOLERANCIA_MONTO * esperado;
    out.push({
      ocurrencia: o,
      distanciaDias: Math.abs(diffDias(o.date, tx.date)),
      montoCoincide,
      sugerible: (o.estado === 'pendiente' || o.estado === 'vencida') && montoCoincide !== false,
    });
  }
  return out.sort((a, b) =>
    Number(b.sugerible) - Number(a.sugerible)
    || a.distanciaDias - b.distanciaDias
    || Number(b.montoCoincide === true) - Number(a.montoCoincide === true)
    || a.ocurrencia.evento.titulo.localeCompare(b.ocurrencia.evento.titulo));
}

/** Primera candidata sugerible, o null. Es la que Nuevo marca con `.sugerido`. */
export function sugerirOcurrencia(tx: TxParaSugerir, ctx: CtxSugerencia): OcurrenciaVista | null {
  const candidatas = candidatasParaTx(tx, ctx);
  return candidatas.length > 0 && candidatas[0].sugerible ? candidatas[0].ocurrencia : null;
}

/** Emparejamiento greedy GLOBAL para la vista previa de los imports: cada ocurrencia recibe
 *  a lo sumo una tx y cada tx a lo sumo una ocurrencia. Determinista. */
export function sugerirParaLote(txs: Transaction[], ctx: CtxSugerencia): Map<string, Ocurrencia> {
  interface Par {
    txId: string; eventId: string; date: string; titulo: string;
    distanciaDias: number; deltaMonto: number; indice: number;
  }
  const pares: Par[] = [];
  txs.forEach((t, indice) => {
    if (t.type !== 'income' && t.type !== 'expense') return;
    const candidatas = candidatasParaTx(
      { type: t.type, date: t.date, accountId: t.accountId, amountMinor: t.amountMinor }, ctx,
    );
    for (const c of candidatas) {
      if (!c.sugerible) continue;
      const esperado = c.ocurrencia.evento.amountMinor;
      pares.push({
        txId: t.id,
        eventId: c.ocurrencia.eventId,
        date: c.ocurrencia.date,
        titulo: c.ocurrencia.evento.titulo,
        distanciaDias: c.distanciaDias,
        deltaMonto: esperado === undefined ? Number.MAX_SAFE_INTEGER : Math.abs(t.amountMinor - esperado),
        indice,
      });
    }
  });
  pares.sort((a, b) =>
    a.distanciaDias - b.distanciaDias
    || a.deltaMonto - b.deltaMonto
    || a.titulo.localeCompare(b.titulo)
    || a.indice - b.indice);

  const asignadas = new Map<string, Ocurrencia>();
  const ocupadas = new Set<string>();
  for (const p of pares) {
    const clave = `${p.eventId}|${p.date}`;
    if (asignadas.has(p.txId) || ocupadas.has(clave)) continue;
    asignadas.set(p.txId, { eventId: p.eventId, date: p.date });
    ocupadas.add(clave);
  }
  return asignadas;
}

/** Devuelve una COPIA. Asignar copia la categoría del evento; desasignar la conserva.
 *  Jamás toca amountMinor, date, accountId ni type. */
export function aplicarAsignacion(tx: Transaction, o: Ocurrencia | null, evento: CalEvent | null): Transaction {
  if (o === null || evento === null) {
    const { eventId: _e, eventDate: _f, ...resto } = tx;
    return resto as Transaction;
  }
  return { ...tx, eventId: o.eventId, eventDate: o.date, categoryId: evento.categoryId };
}

/** Lanza Error en español si el movimiento y el evento van en direcciones contrarias. */
export function validarAsignacion(
  tx: Pick<Transaction, 'type'>, evento: CalEvent, categories: Category[],
): void {
  const kind = kindDeEvento(evento, categories);
  if (kind === null) throw new Error('La categoría del evento no existe');
  if (tx.type === 'expense' && kind === 'income') {
    throw new Error('Este movimiento es un gasto y el evento es un cobro');
  }
  if (tx.type === 'income' && kind === 'expense') {
    throw new Error('Este movimiento es un ingreso y el evento es un pago');
  }
}
