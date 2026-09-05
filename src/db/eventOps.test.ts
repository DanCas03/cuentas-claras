import { CuentasDB, seedIfEmpty } from './db';
import {
  asignarEvento, desasignarEvento, eliminarEvento, guardarEvento, omitirOcurrencia, reanudarOcurrencia,
} from './eventOps';
import { RECURRENCIA_QUINCENAL } from '../domain/calendar';
import type { CalEvent, Transaction } from '../domain/types';

const QUINCENA: CalEvent = {
  id: 'e-quincena', titulo: 'Quincena', categoryId: 'cat-sueldo', accountId: 'acc-bnc',
  amountMinor: 800000, startDate: '2026-01-01', recurrence: RECURRENCIA_QUINCENAL,
  createdIso: '2026-09-04T10:00:00.000Z',
};

async function base() {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  return d;
}

const tx = (p: Partial<Transaction>): Transaction => ({
  id: 't1', type: 'income', accountId: 'acc-bnc', amountMinor: 800000,
  date: '2026-09-15', source: 'manual', categoryId: 'cat-otros-ingresos', ...p,
});

test('guardarEvento valida el evento y exige que la categoría exista', async () => {
  const d = await base();
  await expect(guardarEvento(d, { ...QUINCENA, titulo: ' ' })).rejects.toThrow('Ponle nombre al evento');
  await expect(guardarEvento(d, { ...QUINCENA, categoryId: 'cat-inventada' })).rejects.toThrow('La categoría no existe');
  expect(await d.calEvents.count()).toBe(0);
  await guardarEvento(d, QUINCENA);
  expect((await d.calEvents.get('e-quincena'))?.titulo).toBe('Quincena');
});

test('guardarEvento hace put del objeto COMPLETO: conserva omitidas', async () => {
  const d = await base();
  await guardarEvento(d, { ...QUINCENA, omitidas: ['2026-08-31'] });
  await guardarEvento(d, { ...QUINCENA, titulo: 'Quincena BNC', omitidas: ['2026-08-31'] });
  expect(await d.calEvents.get('e-quincena')).toMatchObject({ titulo: 'Quincena BNC', omitidas: ['2026-08-31'] });
});

test('guardarEvento rechaza cambiar a una categoría de kind contrario si hay txs asignadas', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15' }));
  await expect(guardarEvento(d, { ...QUINCENA, categoryId: 'cat-suscripciones' }))
    .rejects.toThrow('Desasigna primero los 1 movimientos');
  expect((await d.calEvents.get('e-quincena'))?.categoryId).toBe('cat-sueldo'); // nada a medias
});

test('eliminarEvento devuelve las desasignadas, borra el evento y NUNCA borra movimientos', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo' }));
  await d.transactions.add(tx({ id: 'b', eventId: 'e-quincena', eventDate: '2026-09-30', categoryId: 'cat-sueldo' }));
  await d.transactions.add(tx({ id: 'c' }));
  expect(await eliminarEvento(d, 'e-quincena')).toBe(2);
  expect(await d.calEvents.count()).toBe(0);
  expect(await d.transactions.count()).toBe(3);
  const a = await d.transactions.get('a');
  expect(a && 'eventId' in a).toBe(false);
  expect(a && 'eventDate' in a).toBe(false);
  expect(a?.categoryId).toBe('cat-sueldo');     // conserva categoría
  expect(a?.amountMinor).toBe(800000);
  expect(await eliminarEvento(d, 'e-quincena')).toBe(0); // idempotente
});

test('asignarEvento fija los 3 campos y valida tipo, evento, fecha y dirección', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a' }));
  await d.transactions.add({
    id: 'tr', type: 'transfer', accountId: 'acc-bnc', toAccountId: 'acc-zinli',
    amountMinor: 100, toAmountMinor: 1, date: '2026-09-15', source: 'manual',
  });
  await d.transactions.add(tx({ id: 'g', type: 'expense', categoryId: 'cat-hogar' }));

  await asignarEvento(d, 'a', { eventId: 'e-quincena', date: '2026-09-15' });
  expect(await d.transactions.get('a')).toMatchObject({
    eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo',
    amountMinor: 800000, date: '2026-09-15', accountId: 'acc-bnc', type: 'income',
  });

  await expect(asignarEvento(d, 'no-existe', { eventId: 'e-quincena', date: '2026-09-15' }))
    .rejects.toThrow('Solo se asignan gastos e ingresos');
  await expect(asignarEvento(d, 'tr', { eventId: 'e-quincena', date: '2026-09-15' }))
    .rejects.toThrow('Solo se asignan gastos e ingresos');
  await expect(asignarEvento(d, 'a', { eventId: 'fantasma', date: '2026-09-15' }))
    .rejects.toThrow('El evento no existe');
  await expect(asignarEvento(d, 'a', { eventId: 'e-quincena', date: '2026-02-30' }))
    .rejects.toThrow('Fecha inválida');
  await expect(asignarEvento(d, 'g', { eventId: 'e-quincena', date: '2026-09-15' }))
    .rejects.toThrow('Este movimiento es un gasto y el evento es un cobro');
  expect((await d.transactions.get('g'))?.categoryId).toBe('cat-hogar'); // no cambió nada
});

test('desasignarEvento borra las CLAVES y es idempotente', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15', categoryId: 'cat-sueldo' }));
  await desasignarEvento(d, 'a');
  const a = await d.transactions.get('a');
  expect(a && 'eventId' in a).toBe(false);
  expect(a && 'eventDate' in a).toBe(false);
  expect(a?.categoryId).toBe('cat-sueldo');
  await expect(desasignarEvento(d, 'a')).resolves.toBeUndefined();
  await expect(desasignarEvento(d, 'no-existe')).resolves.toBeUndefined();
});

test('omitirOcurrencia es idempotente y rechaza si la ocurrencia tiene movimientos', async () => {
  const d = await base();
  await guardarEvento(d, QUINCENA);
  await omitirOcurrencia(d, 'e-quincena', '2026-09-30');
  await omitirOcurrencia(d, 'e-quincena', '2026-09-30');
  expect((await d.calEvents.get('e-quincena'))?.omitidas).toEqual(['2026-09-30']);
  await d.transactions.add(tx({ id: 'a', eventId: 'e-quincena', eventDate: '2026-09-15' }));
  await expect(omitirOcurrencia(d, 'e-quincena', '2026-09-15')).rejects.toThrow('Ya tiene movimientos asignados');
  await expect(omitirOcurrencia(d, 'fantasma', '2026-09-15')).rejects.toThrow('El evento no existe');
  await expect(omitirOcurrencia(d, 'e-quincena', '2026-02-30')).rejects.toThrow('Fecha inválida');
});

test('reanudarOcurrencia quita la fecha y borra la clave si el array queda vacío', async () => {
  const d = await base();
  await guardarEvento(d, { ...QUINCENA, omitidas: ['2026-09-15', '2026-09-30'] });
  await reanudarOcurrencia(d, 'e-quincena', '2026-09-15');
  expect((await d.calEvents.get('e-quincena'))?.omitidas).toEqual(['2026-09-30']);
  await reanudarOcurrencia(d, 'e-quincena', '2026-09-30');
  const e = await d.calEvents.get('e-quincena');
  expect(e && 'omitidas' in e).toBe(false);
  await expect(reanudarOcurrencia(d, 'e-quincena', '2026-09-30')).resolves.toBeUndefined(); // idempotente
  await expect(reanudarOcurrencia(d, 'fantasma', '2026-09-30')).rejects.toThrow('El evento no existe');
});
