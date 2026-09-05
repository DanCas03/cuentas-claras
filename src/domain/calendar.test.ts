import {
  buildEvent, descripcionRecurrencia, esQuincenal, estadoDe, fechasDeEvento, kindDeEvento,
  ocurrenciasEnRango, parseDias, totalesEsperados, validarEvento, RECURRENCIA_QUINCENAL,
} from './calendar';
import type { Account, CalEvent, Category, Transaction } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-zinli', name: 'Zinli', currency: 'USD', kind: 'wallet', isSavings: false, archived: false },
];
const CATEGORIAS: Category[] = [
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#22d3ee', kind: 'expense' },
];
const ev = (p: Partial<CalEvent>): CalEvent => ({
  id: 'e1', titulo: 'Quincena', categoryId: 'cat-sueldo', startDate: '2026-01-01',
  recurrence: { kind: 'monthly', days: [15, 31] }, createdIso: '2026-09-04T10:00:00.000Z', ...p,
});
const tx = (p: Partial<Transaction>): Transaction => ({
  id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000,
  date: '2026-09-15', source: 'manual', ...p,
});

test('parseDias ordena, deduplica y valida el rango 1..31', () => {
  expect(parseDias('15, 31')).toEqual([15, 31]);
  expect(parseDias('31,15')).toEqual([15, 31]);
  expect(parseDias('15 15')).toEqual([15]);
  expect(parseDias(' 5 ')).toEqual([5]);
  expect(() => parseDias('')).toThrow('Días del mes inválidos');
  expect(() => parseDias('40')).toThrow('Días del mes inválidos');
  expect(() => parseDias('0')).toThrow('Días del mes inválidos');
  expect(() => parseDias('a')).toThrow('Días del mes inválidos');
});

test('validarEvento: cada mensaje en español', () => {
  expect(() => validarEvento(ev({ titulo: '  ' }))).toThrow('Ponle nombre al evento');
  expect(() => validarEvento(ev({ categoryId: '' }))).toThrow('Elige una categoría');
  expect(() => validarEvento(ev({ amountMinor: 800000 }))).toThrow('El monto esperado necesita una cuenta');
  expect(() => validarEvento(ev({ accountId: 'acc-bnc', amountMinor: 0 }))).toThrow('Monto inválido');
  expect(() => validarEvento(ev({ accountId: 'acc-bnc', amountMinor: 10.5 }))).toThrow('Monto inválido');
  expect(() => validarEvento(ev({ startDate: '2026-02-30' }))).toThrow('Fecha inválida');
  expect(() => validarEvento(ev({ endDate: '2026-13-01' }))).toThrow('Fecha de fin inválida');
  expect(() => validarEvento(ev({ startDate: '2026-09-10', endDate: '2026-09-09' }))).toThrow('El fin no puede ser antes del inicio');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [31, 15] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [15, 15] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ recurrence: { kind: 'monthly', days: [32] } }))).toThrow('Días del mes inválidos');
  expect(() => validarEvento(ev({ omitidas: ['2026-02-30'] }))).toThrow('Fecha omitida inválida');
  expect(() => validarEvento(ev({ accountId: 'acc-bnc', amountMinor: 800000, endDate: '2026-12-31', omitidas: ['2026-08-31'] }))).not.toThrow();
});

test('buildEvent: id nuevo, título recortado, sin claves vacías y validado', () => {
  const e = buildEvent({
    titulo: '  Netflix  ', categoryId: 'cat-suscripciones', startDate: '2026-09-05',
    recurrence: { kind: 'monthly', days: [5] }, nowIso: '2026-09-04T10:00:00.000Z',
  });
  expect(e.titulo).toBe('Netflix');
  expect(e.id).toMatch(/[0-9a-f-]{36}/);
  expect('accountId' in e).toBe(false);
  expect('amountMinor' in e).toBe(false);
  expect('endDate' in e).toBe(false);
  expect('omitidas' in e).toBe(false);
  expect(e.createdIso).toBe('2026-09-04T10:00:00.000Z');
  expect(() => buildEvent({
    titulo: 'X', categoryId: 'cat-sueldo', amountMinor: 100, startDate: '2026-09-05',
    recurrence: { kind: 'once' }, nowIso: 'x',
  })).toThrow('El monto esperado necesita una cuenta');
});

test('fechasDeEvento once: solo si cae en el rango; endDate se ignora', () => {
  const e = ev({ startDate: '2026-10-20', recurrence: { kind: 'once' }, endDate: '2026-01-01' });
  expect(fechasDeEvento(e, '2026-10-01', '2026-10-31')).toEqual(['2026-10-20']);
  expect(fechasDeEvento(e, '2026-11-01', '2026-11-30')).toEqual([]);
  expect(fechasDeEvento(e, '2026-10-20', '2026-10-20')).toEqual(['2026-10-20']);
});

test('fechasDeEvento weekly: salta al primer múltiplo dentro del rango y respeta endDate', () => {
  const e = ev({ startDate: '2026-09-02', recurrence: { kind: 'weekly' } }); // miércoles
  expect(fechasDeEvento(e, '2026-09-01', '2026-09-30'))
    .toEqual(['2026-09-02', '2026-09-09', '2026-09-16', '2026-09-23', '2026-09-30']);
  expect(fechasDeEvento(e, '2026-10-01', '2026-10-31'))
    .toEqual(['2026-10-07', '2026-10-14', '2026-10-21', '2026-10-28']);
  expect(fechasDeEvento(e, '2026-08-01', '2026-08-31')).toEqual([]); // anterior al ancla
  expect(fechasDeEvento(ev({ startDate: '2026-09-02', endDate: '2026-09-16', recurrence: { kind: 'weekly' } }), '2026-09-01', '2026-09-30'))
    .toEqual(['2026-09-02', '2026-09-09', '2026-09-16']); // endDate inclusive
});

test('fechasDeEvento monthly: día > días del mes cae en el último día y deduplica', () => {
  const q = ev({ recurrence: RECURRENCIA_QUINCENAL });
  expect(fechasDeEvento(q, '2026-01-01', '2026-01-31')).toEqual(['2026-01-15', '2026-01-31']);
  expect(fechasDeEvento(q, '2026-02-01', '2026-02-28')).toEqual(['2026-02-15', '2026-02-28']);
  expect(fechasDeEvento(q, '2026-04-01', '2026-04-30')).toEqual(['2026-04-15', '2026-04-30']);
  expect(fechasDeEvento(ev({ recurrence: { kind: 'monthly', days: [30, 31] } }), '2026-02-01', '2026-02-28'))
    .toEqual(['2026-02-28']); // 30 y 31 colapsan en una sola fecha
  expect(fechasDeEvento(ev({ startDate: '2024-01-01', recurrence: RECURRENCIA_QUINCENAL }), '2024-02-01', '2024-02-29'))
    .toEqual(['2024-02-15', '2024-02-29']);
  expect(fechasDeEvento(q, '2026-09-01', '2026-10-31'))
    .toEqual(['2026-09-15', '2026-09-30', '2026-10-15', '2026-10-31']);
  const tardio = ev({ startDate: '2026-09-20', recurrence: { kind: 'monthly', days: [15] } });
  expect(fechasDeEvento(tardio, '2026-09-01', '2026-09-30')).toEqual([]);   // el ancla recorta
  expect(fechasDeEvento(tardio, '2026-10-01', '2026-10-31')).toEqual(['2026-10-15']);
  expect(fechasDeEvento(ev({ endDate: '2026-09-15', recurrence: RECURRENCIA_QUINCENAL }), '2026-09-01', '2026-09-30'))
    .toEqual(['2026-09-15']); // endDate inclusive
});

test('fechasDeEvento yearly: recorta el 29 de febrero en años no bisiestos', () => {
  const e = ev({ startDate: '2024-02-29', recurrence: { kind: 'yearly' } });
  expect(fechasDeEvento(e, '2025-01-01', '2025-12-31')).toEqual(['2025-02-28']);
  expect(fechasDeEvento(e, '2028-01-01', '2028-12-31')).toEqual(['2028-02-29']);
  expect(fechasDeEvento(e, '2025-01-01', '2026-12-31')).toEqual(['2025-02-28', '2026-02-28']);
  expect(fechasDeEvento(e, '2023-01-01', '2023-12-31')).toEqual([]); // antes del ancla
});

test('estadoDe: hecha > omitida > vencida > pendiente; hoy es pendiente', () => {
  const hoy = '2026-09-04';
  expect(estadoDe({ date: '2026-08-31', omitida: true, txIds: ['t1'] }, hoy)).toBe('hecha');
  expect(estadoDe({ date: '2026-08-31', omitida: true, txIds: [] }, hoy)).toBe('omitida');
  expect(estadoDe({ date: '2026-08-31', omitida: false, txIds: [] }, hoy)).toBe('vencida');
  expect(estadoDe({ date: '2026-09-04', omitida: false, txIds: [] }, hoy)).toBe('pendiente');
  expect(estadoDe({ date: '2026-09-15', omitida: false, txIds: [] }, hoy)).toBe('pendiente');
});

test('kindDeEvento devuelve el kind de su categoría o null', () => {
  expect(kindDeEvento(ev({}), CATEGORIAS)).toBe('income');
  expect(kindDeEvento(ev({ categoryId: 'cat-suscripciones' }), CATEGORIAS)).toBe('expense');
  expect(kindDeEvento(ev({ categoryId: 'cat-borrada' }), CATEGORIAS)).toBeNull();
});

test('ocurrenciasEnRango: estados, txIds, suma por moneda y orden', () => {
  const eventos = [
    ev({ id: 'e1', titulo: 'Quincena', recurrence: RECURRENCIA_QUINCENAL, omitidas: ['2026-09-30'] }),
    ev({ id: 'e2', titulo: 'Netflix', categoryId: 'cat-suscripciones', startDate: '2026-01-05', recurrence: { kind: 'monthly', days: [5] } }),
  ];
  const txs = [
    tx({ id: 'a', eventId: 'e1', eventDate: '2026-09-15', amountMinor: 500000 }),
    tx({ id: 'b', eventId: 'e1', eventDate: '2026-09-15', amountMinor: 300000 }),
    tx({ id: 'c', eventId: 'e1', eventDate: '2026-09-15', accountId: 'acc-zinli', amountMinor: 1000 }),
    tx({ id: 'd', eventId: 'fantasma', eventDate: '2026-09-15' }), // evento inexistente: se ignora
  ];
  const o = ocurrenciasEnRango({ eventos, txs, accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04' });
  expect(o.map((x) => `${x.date} ${x.evento.titulo} ${x.estado}`)).toEqual([
    '2026-09-05 Netflix pendiente',
    '2026-09-15 Quincena hecha',
    '2026-09-30 Quincena omitida',
  ]);
  const quincena = o[1];
  expect(quincena.txIds.sort()).toEqual(['a', 'b', 'c']);
  expect(quincena.asignadoPorMoneda).toContainEqual({ currency: 'VES', totalMinor: 800000 });
  expect(quincena.asignadoPorMoneda).toContainEqual({ currency: 'USD', totalMinor: 1000 });
  expect(quincena.fueraDeRegla).toBe(false);
});

test('ocurrenciasEnRango: una tx asignada a una fecha que la regla no genera queda fueraDeRegla', () => {
  const o = ocurrenciasEnRango({
    eventos: [ev({ recurrence: RECURRENCIA_QUINCENAL })],
    txs: [tx({ id: 'a', eventId: 'e1', eventDate: '2026-09-07' })],
    accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04',
  });
  const rara = o.find((x) => x.date === '2026-09-07');
  expect(rara?.fueraDeRegla).toBe(true);
  expect(rara?.estado).toBe('hecha');
  expect(o.filter((x) => x.fueraDeRegla)).toHaveLength(1);
});

test('totalesEsperados: solo pendientes/vencidas con monto, por moneda y sin conversión', () => {
  const eventos = [
    ev({ id: 'e1', titulo: 'Quincena', accountId: 'acc-bnc', amountMinor: 800000, recurrence: RECURRENCIA_QUINCENAL }),
    ev({ id: 'e2', titulo: 'Netflix', categoryId: 'cat-suscripciones', accountId: 'acc-zinli', amountMinor: 1599, startDate: '2026-01-05', recurrence: { kind: 'monthly', days: [5] } }),
    ev({ id: 'e3', titulo: 'Variable', categoryId: 'cat-suscripciones', startDate: '2026-09-10', recurrence: { kind: 'once' } }),
  ];
  const o = ocurrenciasEnRango({ eventos, txs: [], accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04' });
  const t = totalesEsperados(o, CUENTAS, CATEGORIAS);
  expect(t.cobrar).toEqual([{ currency: 'VES', totalMinor: 1600000 }]); // dos quincenas de Bs 8.000,00
  expect(t.pagar).toEqual([{ currency: 'USD', totalMinor: 1599 }]);     // Netflix; el variable no suma
  const conHecha = ocurrenciasEnRango({
    eventos, txs: [tx({ id: 'a', eventId: 'e1', eventDate: '2026-09-15' })],
    accounts: CUENTAS, desde: '2026-09-01', hasta: '2026-09-30', hoy: '2026-09-04',
  });
  expect(totalesEsperados(conHecha, CUENTAS, CATEGORIAS).cobrar).toEqual([{ currency: 'VES', totalMinor: 800000 }]);
});

test('esQuincenal y descripcionRecurrencia', () => {
  expect(esQuincenal(RECURRENCIA_QUINCENAL)).toBe(true);
  expect(esQuincenal({ kind: 'monthly', days: [15] })).toBe(false);
  expect(esQuincenal({ kind: 'weekly' })).toBe(false);
  expect(descripcionRecurrencia(ev({ recurrence: RECURRENCIA_QUINCENAL }))).toBe('quincenal (15 y último)');
  expect(descripcionRecurrencia(ev({ recurrence: { kind: 'monthly', days: [15, 20, 31] } }))).toBe('mensual: 15, 20 y último');
  expect(descripcionRecurrencia(ev({ recurrence: { kind: 'monthly', days: [5] } }))).toBe('mensual: 5');
  expect(descripcionRecurrencia(ev({ startDate: '2026-09-04', recurrence: { kind: 'weekly' } }))).toBe('semanal: viernes');
  expect(descripcionRecurrencia(ev({ startDate: '2026-03-03', recurrence: { kind: 'yearly' } }))).toBe('anual: 3 mar');
  expect(descripcionRecurrencia(ev({ startDate: '2026-10-20', recurrence: { kind: 'once' } }))).toBe('una vez: 20/10/2026');
  expect(descripcionRecurrencia(ev({ recurrence: { kind: 'weekly' }, startDate: '2026-09-04', endDate: '2026-12-31' })))
    .toBe('semanal: viernes · hasta 31/12/2026');
});
