import type {
  Account, CalEvent, Category, CategoryKind, Currency, Recurrence, Transaction,
} from './types';
import {
  addDays, daysInMonth, ddmmyyyy, diffDias, esFechaIso, isoFromParts, mesSiguiente,
  monthOf, nombreDia, partsOf, MES_CORTO,
} from './dates';

export type EstadoOcurrencia = 'pendiente' | 'hecha' | 'vencida' | 'omitida';

/** Clave de una ocurrencia: el evento + su fecha NOMINAL (no la del movimiento). */
export interface Ocurrencia { eventId: string; date: string }

export interface MontoMoneda { currency: Currency; totalMinor: number }

export interface OcurrenciaVista extends Ocurrencia {
  evento: CalEvent;
  estado: EstadoOcurrencia;
  /** Movimientos asignados a esta ocurrencia (pueden ser varios). */
  txIds: string[];
  /** Σ amountMinor de las txs asignadas, por moneda de la cuenta de cada tx. Sin conversión. */
  asignadoPorMoneda: MontoMoneda[];
  /** true si la regla actual del evento NO genera esa fecha (asignación «rara» o regla editada). */
  fueraDeRegla: boolean;
}

/** Preset «Quincenal (15 y último)»: 31 se recorta al último día de cada mes. */
export const RECURRENCIA_QUINCENAL: Recurrence = { kind: 'monthly', days: [15, 31] };

export function esQuincenal(r: Recurrence): boolean {
  return r.kind === 'monthly' && r.days.length === 2 && r.days[0] === 15 && r.days[1] === 31;
}

/** '15, 31' → [15, 31]. Ordena y deduplica. Lanza si está vacío o hay algo fuera de 1..31. */
export function parseDias(texto: string): number[] {
  const partes = texto.split(/[\s,;]+/).filter((s) => s !== '');
  const dias = new Set<number>();
  for (const p of partes) {
    if (!/^\d{1,2}$/.test(p)) throw new Error('Días del mes inválidos');
    const n = Number(p);
    if (n < 1 || n > 31) throw new Error('Días del mes inválidos');
    dias.add(n);
  }
  if (dias.size === 0) throw new Error('Días del mes inválidos');
  return [...dias].sort((a, b) => a - b);
}

/** Lanza Error en español con el primer problema encontrado. */
export function validarEvento(e: CalEvent): void {
  if (!e.titulo.trim()) throw new Error('Ponle nombre al evento');
  if (!e.categoryId) throw new Error('Elige una categoría');
  if (e.amountMinor !== undefined) {
    if (!e.accountId) throw new Error('El monto esperado necesita una cuenta');
    if (!Number.isInteger(e.amountMinor) || e.amountMinor <= 0) throw new Error('Monto inválido');
  }
  if (!esFechaIso(e.startDate)) throw new Error('Fecha inválida');
  if (e.endDate !== undefined) {
    if (!esFechaIso(e.endDate)) throw new Error('Fecha de fin inválida');
    if (e.endDate < e.startDate) throw new Error('El fin no puede ser antes del inicio');
  }
  if (e.recurrence.kind === 'monthly') {
    const d = e.recurrence.days;
    const ok = d.length > 0
      && d.every((n) => Number.isInteger(n) && n >= 1 && n <= 31)
      && d.every((n, i) => i === 0 || n > d[i - 1]);
    if (!ok) throw new Error('Días del mes inválidos');
  }
  for (const o of e.omitidas ?? []) {
    if (!esFechaIso(o)) throw new Error('Fecha omitida inválida');
  }
}

/** Construye un CalEvent válido. Nunca escribe claves con valor undefined. */
export function buildEvent(opts: {
  titulo: string; categoryId: string; accountId?: string; amountMinor?: number;
  startDate: string; endDate?: string; recurrence: Recurrence; omitidas?: string[]; nowIso: string;
}): CalEvent {
  const e: CalEvent = {
    id: crypto.randomUUID(),
    titulo: opts.titulo.trim(),
    categoryId: opts.categoryId,
    startDate: opts.startDate,
    recurrence: opts.recurrence,
    createdIso: opts.nowIso,
  };
  if (opts.accountId) e.accountId = opts.accountId;
  if (opts.amountMinor !== undefined) e.amountMinor = opts.amountMinor;
  if (opts.endDate) e.endDate = opts.endDate;
  if (opts.omitidas && opts.omitidas.length > 0) e.omitidas = [...opts.omitidas];
  validarEvento(e);
  return e;
}

/** Fechas NOMINALES del evento dentro de [desde, hasta] (inclusive), ascendentes y sin repetidos.
 *  Coste O(meses o semanas del rango). NUNCA expande sin `hasta`. */
export function fechasDeEvento(e: CalEvent, desde: string, hasta: string): string[] {
  if (e.recurrence.kind === 'once') {
    return desde <= e.startDate && e.startDate <= hasta ? [e.startDate] : [];
  }
  const desdeEf = desde > e.startDate ? desde : e.startDate;
  const hastaEf = e.endDate !== undefined && e.endDate < hasta ? e.endDate : hasta;
  if (desdeEf > hastaEf) return [];

  if (e.recurrence.kind === 'weekly') {
    const salto = Math.ceil(diffDias(e.startDate, desdeEf) / 7);
    const out: string[] = [];
    for (let f = addDays(e.startDate, salto * 7); f <= hastaEf; f = addDays(f, 7)) out.push(f);
    return out;
  }

  const out = new Set<string>();
  if (e.recurrence.kind === 'yearly') {
    const { m, d } = partsOf(e.startDate);
    for (let y = partsOf(desdeEf).y; y <= partsOf(hastaEf).y; y++) {
      const f = isoFromParts(y, m, Math.min(d, daysInMonth(y, m)));
      if (f >= desdeEf && f <= hastaEf) out.add(f);
    }
    return [...out].sort();
  }

  const dias = e.recurrence.days;
  for (let mes = monthOf(desdeEf); mes <= monthOf(hastaEf); mes = mesSiguiente(mes)) {
    const [y, m] = mes.split('-').map(Number);
    for (const dia of dias) {
      const f = isoFromParts(y, m, Math.min(dia, daysInMonth(y, m)));
      if (f >= desdeEf && f <= hastaEf) out.add(f);
    }
  }
  return [...out].sort();
}

/** El kind de la categoría del evento define pago (expense) o cobro (income). null si no existe. */
export function kindDeEvento(e: CalEvent, categories: Category[]): CategoryKind | null {
  return categories.find((c) => c.id === e.categoryId)?.kind ?? null;
}

/** Precedencia: hecha > omitida > vencida > pendiente. «Hoy» cuenta como pendiente. */
export function estadoDe(o: { date: string; omitida: boolean; txIds: string[] }, hoy: string): EstadoOcurrencia {
  if (o.txIds.length > 0) return 'hecha';
  if (o.omitida) return 'omitida';
  if (o.date < hoy) return 'vencida';
  return 'pendiente';
}

/** UNIÓN de las fechas generadas por cada evento y de las asignaciones existentes en el rango.
 *  Las ocurrencias NO se persisten: esta función las deriva en cada render. */
export function ocurrenciasEnRango(opts: {
  eventos: CalEvent[]; txs: Transaction[]; accounts: Account[];
  desde: string; hasta: string; hoy: string;
}): OcurrenciaVista[] {
  const eventoDe = new Map(opts.eventos.map((e) => [e.id, e]));
  const monedaDe = new Map(opts.accounts.map((a) => [a.id, a.currency]));
  const generadas = new Set<string>();
  const pares = new Map<string, { evento: CalEvent; date: string }>();

  for (const e of opts.eventos) {
    for (const f of fechasDeEvento(e, opts.desde, opts.hasta)) {
      const k = `${e.id}|${f}`;
      generadas.add(k);
      pares.set(k, { evento: e, date: f });
    }
  }

  const txsPorPar = new Map<string, Transaction[]>();
  for (const t of opts.txs) {
    if (!t.eventId || !t.eventDate) continue;
    if (t.eventDate < opts.desde || t.eventDate > opts.hasta) continue;
    const evento = eventoDe.get(t.eventId);
    if (!evento) continue; // asignación huérfana: se ignora
    const k = `${t.eventId}|${t.eventDate}`;
    if (!pares.has(k)) pares.set(k, { evento, date: t.eventDate });
    const lista = txsPorPar.get(k) ?? [];
    lista.push(t);
    txsPorPar.set(k, lista);
  }

  const vistas: OcurrenciaVista[] = [];
  for (const [k, { evento, date }] of pares) {
    const asignadas = txsPorPar.get(k) ?? [];
    const txIds = asignadas.map((t) => t.id);
    const porMoneda = new Map<Currency, number>();
    for (const t of asignadas) {
      const moneda = monedaDe.get(t.accountId) ?? 'USD';
      porMoneda.set(moneda, (porMoneda.get(moneda) ?? 0) + t.amountMinor);
    }
    vistas.push({
      eventId: evento.id,
      date,
      evento,
      estado: estadoDe({ date, omitida: (evento.omitidas ?? []).includes(date), txIds }, opts.hoy),
      txIds,
      asignadoPorMoneda: [...porMoneda.entries()].map(([currency, totalMinor]) => ({ currency, totalMinor })),
      fueraDeRegla: !generadas.has(k),
    });
  }
  return vistas.sort((a, b) =>
    a.date.localeCompare(b.date) || a.evento.titulo.localeCompare(b.evento.titulo));
}

/** Lo que falta pagar/cobrar: solo pendientes y vencidas CON monto esperado.
 *  Por moneda de la cuenta del evento; SIN conversión (igual que las deudas del spec v2). */
export function totalesEsperados(
  ocurrencias: OcurrenciaVista[], accounts: Account[], categories: Category[],
): { pagar: MontoMoneda[]; cobrar: MontoMoneda[] } {
  const monedaDe = new Map(accounts.map((a) => [a.id, a.currency]));
  const pagar = new Map<Currency, number>();
  const cobrar = new Map<Currency, number>();
  for (const o of ocurrencias) {
    if (o.estado !== 'pendiente' && o.estado !== 'vencida') continue;
    const { amountMinor, accountId } = o.evento;
    if (amountMinor === undefined || !accountId) continue;
    const kind = kindDeEvento(o.evento, categories);
    if (kind === null) continue;
    const moneda = monedaDe.get(accountId) ?? 'USD';
    const destino = kind === 'expense' ? pagar : cobrar;
    destino.set(moneda, (destino.get(moneda) ?? 0) + amountMinor);
  }
  const lista = (m: Map<Currency, number>): MontoMoneda[] =>
    [...m.entries()].map(([currency, totalMinor]) => ({ currency, totalMinor }));
  return { pagar: lista(pagar), cobrar: lista(cobrar) };
}

/** Texto humano de la regla, en español y sin Intl. */
export function descripcionRecurrencia(e: CalEvent): string {
  const fin = e.endDate ? ` · hasta ${ddmmyyyy(e.endDate)}` : '';
  const r = e.recurrence;
  if (r.kind === 'once') return `una vez: ${ddmmyyyy(e.startDate)}`;
  if (r.kind === 'weekly') return `semanal: ${nombreDia(e.startDate)}${fin}`;
  if (r.kind === 'yearly') {
    const { m, d } = partsOf(e.startDate);
    return `anual: ${d} ${MES_CORTO[m - 1]}${fin}`;
  }
  if (esQuincenal(r)) return `quincenal (15 y último)${fin}`;
  const dias = r.days.map((n) => (n === 31 ? 'último' : String(n)));
  const texto = dias.length === 1 ? dias[0] : `${dias.slice(0, -1).join(', ')} y ${dias[dias.length - 1]}`;
  return `mensual: ${texto}${fin}`;
}
