import {
  aplicarAsignacion, candidatasParaTx, sugerirOcurrencia, sugerirParaLote, validarAsignacion,
} from './eventMatch';
import { RECURRENCIA_QUINCENAL } from './calendar';
import type { Account, CalEvent, Category, Transaction } from './types';

const CUENTAS: Account[] = [
  { id: 'acc-bnc', name: 'BNC', currency: 'VES', kind: 'bank', isSavings: false, archived: false },
  { id: 'acc-zinli', name: 'Zinli', currency: 'USD', kind: 'wallet', isSavings: false, archived: false },
];
const CATEGORIAS: Category[] = [
  { id: 'cat-sueldo', name: 'Sueldo/Pagos', icon: '💼', color: '#34d399', kind: 'income' },
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#22d3ee', kind: 'expense' },
];
const QUINCENA: CalEvent = {
  id: 'e-quincena', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
  amountMinor: 800000, startDate: '2026-01-01', recurrence: RECURRENCIA_QUINCENAL,
  createdIso: '2026-09-04T10:00:00.000Z',
};
const ctxCon = (eventos: CalEvent[], txs: Transaction[] = []) => ({
  eventos, txs, accounts: CUENTAS, categories: CATEGORIAS, hoy: '2026-09-04',
});
const tx = (p: Partial<Transaction>): Transaction => ({
  id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000,
  date: '2026-09-15', source: 'manual', ...p,
});

test('elegibilidad: kind, cuenta y ventana [−31, +7] con bordes inclusivos', () => {
  const ctx = ctxCon([QUINCENA]);
  const para = (date: string) => candidatasParaTx({ type: 'income', date, accountId: 'acc-bnc', amountMinor: 800000 }, ctx);
  expect(para('2026-09-15').map((c) => c.ocurrencia.date)).toContain('2026-09-15');
  expect(para('2026-10-16').map((c) => c.ocurrencia.date)).toContain('2026-09-15'); // 31 días después
  expect(para('2026-10-17').map((c) => c.ocurrencia.date)).not.toContain('2026-09-15');
  expect(para('2026-09-08').map((c) => c.ocurrencia.date)).toContain('2026-09-15'); // 7 días antes
  expect(para('2026-09-07').map((c) => c.ocurrencia.date)).not.toContain('2026-09-15');
  // kind contrario: un gasto no ve un evento de cobro
  expect(candidatasParaTx({ type: 'expense', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 }, ctx)).toEqual([]);
  // cuenta distinta a la esperada por el evento
  expect(candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-zinli', amountMinor: 800000 }, ctx)).toEqual([]);
  // evento sin accountId: cualquier cuenta sirve
  const libre = { ...QUINCENA, id: 'e-libre', accountId: undefined, amountMinor: undefined };
  // ventana [-31,+7] desde el 15-sep = [15-ago, 22-sep]: la quincenal reaparece en 15-ago y 31-ago
  expect(candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-zinli', amountMinor: 1 }, ctxCon([libre])))
    .toHaveLength(3); // 15 y 31 de agosto, 15 de septiembre
});

test('omitidas se excluyen; hechas solo con incluirHechas', () => {
  const omitida = { ...QUINCENA, omitidas: ['2026-09-15'] };
  const t = { type: 'income' as const, date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 };
  // ventana [-31,+7] desde 15-sep = [15-ago, 22-sep]: incluye 15-ago y 31-ago; 30-sep queda fuera
  expect(candidatasParaTx(t, ctxCon([omitida])).map((c) => c.ocurrencia.date)).toEqual(['2026-08-31', '2026-08-15']);
  const hecha = ctxCon([QUINCENA], [tx({ id: 'ya', eventId: 'e-quincena', eventDate: '2026-09-15' })]);
  expect(candidatasParaTx(t, hecha).map((c) => c.ocurrencia.date)).toEqual(['2026-08-31', '2026-08-15']);
  const conHechas = candidatasParaTx(t, hecha, { incluirHechas: true });
  expect(conHechas.map((c) => c.ocurrencia.date).sort()).toEqual(['2026-08-15', '2026-08-31', '2026-09-15']);
  expect(conHechas.find((c) => c.ocurrencia.date === '2026-09-15')?.sugerible).toBe(false);
});

test('montoCoincide: ±10 %, null sin monto esperado y null con amountMinor ≤ 0', () => {
  const ctx = ctxCon([QUINCENA]);
  const con = (amountMinor: number) =>
    candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor }, ctx)
      .find((c) => c.ocurrencia.date === '2026-09-15');
  expect(con(800000)?.montoCoincide).toBe(true);
  expect(con(880000)?.montoCoincide).toBe(true);   // +10 % exacto
  expect(con(720000)?.montoCoincide).toBe(true);   // −10 % exacto
  expect(con(880001)?.montoCoincide).toBe(false);
  expect(con(500000)?.montoCoincide).toBe(false);
  expect(con(500000)?.sugerible).toBe(false);
  expect(con(0)?.montoCoincide).toBeNull();
  expect(con(0)?.sugerible).toBe(true);
  const sinMonto = { ...QUINCENA, amountMinor: undefined };
  const c = candidatasParaTx({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 1 }, ctxCon([sinMonto]));
  expect(c[0].montoCoincide).toBeNull();
  expect(c[0].sugerible).toBe(true);
});

test('orden: sugeribles primero, luego distancia, luego monto que coincide, luego título', () => {
  const netflix: CalEvent = {
    id: 'e-netflix', titulo: 'Netflix', categoryId: 'cat-suscripciones', startDate: '2026-01-05',
    recurrence: { kind: 'monthly', days: [5] }, createdIso: 'x',
  };
  const spotify: CalEvent = { ...netflix, id: 'e-spotify', titulo: 'Spotify' };
  const c = candidatasParaTx(
    { type: 'expense', date: '2026-09-05', accountId: 'acc-zinli', amountMinor: 1000 },
    ctxCon([spotify, netflix]),
  );
  // ventana [-31,+7] desde 5-sep = [5-ago, 12-sep]: cada evento mensual (día 5) aparece dos veces
  // (5-ago y 5-sep); a igual distancia dentro de cada grupo, empate → título asc
  expect(c.map((x) => x.ocurrencia.evento.titulo)).toEqual(['Netflix', 'Spotify', 'Netflix', 'Spotify']);
  expect(c.every((x) => x.sugerible)).toBe(true);
});

test('sugerirOcurrencia devuelve la primera sugerible, o null si la única candidata no lo es', () => {
  const ctx = ctxCon([QUINCENA]);
  expect(sugerirOcurrencia({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 }, ctx)?.date)
    .toBe('2026-09-15');
  // monto muy lejos del esperado en TODAS las candidatas → sin sugerencia
  expect(sugerirOcurrencia({ type: 'income', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 5000 }, ctx)).toBeNull();
  expect(sugerirOcurrencia({ type: 'expense', date: '2026-09-15', accountId: 'acc-bnc', amountMinor: 800000 }, ctx)).toBeNull();
});

test('sugerirParaLote: greedy global, una ocurrencia por tx y una tx por ocurrencia', () => {
  const lote = [
    tx({ id: 'amigo', date: '2026-09-14', amountMinor: 5000 }),      // no coincide con Bs 8.000,00
    tx({ id: 'sueldo', date: '2026-09-15', amountMinor: 800000 }),   // coincide
    tx({ id: 'otro', date: '2026-09-16', amountMinor: 790000 }),     // también coincide (±10 %)
  ];
  const m = sugerirParaLote(lote, ctxCon([QUINCENA]));
  expect(m.get('sueldo')).toEqual({ eventId: 'e-quincena', date: '2026-09-15' });
  expect(m.has('amigo')).toBe(false);                                 // el monto lo descarta
  // la del 15 ya está ocupada por 'sueldo'; para 'otro' (16-sep) la ventana es [16-ago, 23-sep],
  // así que 30-sep queda FUERA y la quincena anterior (31-ago) es la siguiente candidata válida
  expect(m.get('otro')).toEqual({ eventId: 'e-quincena', date: '2026-08-31' });
  expect(m.size).toBe(2);
});

test('sugerirParaLote: la fila que coincide en monto gana aunque esté más lejos', () => {
  const lote = [
    tx({ id: 'cerca', date: '2026-09-14', amountMinor: 100000 }),   // 1 día, monto NO coincide → no sugerible
    tx({ id: 'lejos', date: '2026-09-20', amountMinor: 800000 }),   // 5 días, monto coincide
  ];
  const m = sugerirParaLote(lote, ctxCon([QUINCENA]));
  expect(m.get('lejos')).toEqual({ eventId: 'e-quincena', date: '2026-09-15' });
  expect(m.has('cerca')).toBe(false);
});

test('sugerirParaLote: la ocurrencia más cercana gana y las transferencias se ignoran', () => {
  const sinMonto = { ...QUINCENA, amountMinor: undefined };
  const lote = [
    tx({ id: 'q15', date: '2026-09-15' }),
    tx({ id: 'q30', date: '2026-09-30' }),
    { id: 'tr', type: 'transfer' as const, accountId: 'acc-bnc', toAccountId: 'acc-zinli',
      amountMinor: 100, toAmountMinor: 1, date: '2026-09-15', source: 'manual' as const },
  ];
  const m = sugerirParaLote(lote, ctxCon([sinMonto]));
  expect(m.get('q15')).toEqual({ eventId: 'e-quincena', date: '2026-09-15' });
  expect(m.get('q30')).toEqual({ eventId: 'e-quincena', date: '2026-09-30' });
  expect(m.has('tr')).toBe(false);
  // determinista: el mismo lote da el mismo resultado
  expect([...sugerirParaLote(lote, ctxCon([sinMonto])).entries()]).toEqual([...m.entries()]);
});

test('aplicarAsignacion no muta, copia la categoría del evento y al desasignar la conserva', () => {
  const original = tx({ categoryId: 'cat-otros-ingresos' });
  const asignada = aplicarAsignacion(original, { eventId: 'e-quincena', date: '2026-09-15' }, QUINCENA);
  expect(asignada).toMatchObject({ eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo' });
  expect(original.categoryId).toBe('cat-otros-ingresos'); // no mutó
  expect('eventId' in original).toBe(false);
  expect(asignada.amountMinor).toBe(original.amountMinor);
  expect(asignada.date).toBe(original.date);
  expect(asignada.accountId).toBe(original.accountId);
  expect(asignada.type).toBe(original.type);
  const suelta = aplicarAsignacion(asignada, null, null);
  expect('eventId' in suelta).toBe(false);
  expect('eventDate' in suelta).toBe(false);
  expect(suelta.categoryId).toBe('cat-sueldo'); // desasignar CONSERVA la categoría
});

test('validarAsignacion: mensajes por dirección incompatible y categoría borrada', () => {
  expect(() => validarAsignacion({ type: 'income' }, QUINCENA, CATEGORIAS)).not.toThrow();
  expect(() => validarAsignacion({ type: 'expense' }, QUINCENA, CATEGORIAS))
    .toThrow('Este movimiento es un gasto y el evento es un cobro');
  const netflix: CalEvent = { ...QUINCENA, id: 'e2', categoryId: 'cat-suscripciones' };
  expect(() => validarAsignacion({ type: 'income' }, netflix, CATEGORIAS))
    .toThrow('Este movimiento es un ingreso y el evento es un pago');
  expect(() => validarAsignacion({ type: 'income' }, { ...QUINCENA, categoryId: 'cat-borrada' }, CATEGORIAS))
    .toThrow('La categoría del evento no existe');
});
