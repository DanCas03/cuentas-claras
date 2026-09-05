import { isoFromDdMmYyyy, monthOf, todayIso } from './dates';
import {
  addDays, celdasMes, daysInMonth, ddmmyyyy, diaCorto, diffDias, esFechaIso, fechaCorta, fechaLarga,
  mesAnterior, mesSiguiente, nombreDia, nombreMes, primerDiaMes, ultimoDiaMes, weekdayLunes0, LETRAS_DIA,
} from './dates';

test('convierte dd/mm/yyyy a ISO', () => {
  expect(isoFromDdMmYyyy('06/07/2026')).toBe('2026-07-06');
  expect(isoFromDdMmYyyy('28/06/2026')).toBe('2026-06-28');
});
test('rechaza formatos inválidos', () => {
  expect(isoFromDdMmYyyy('1/1')).toBeNull();
  expect(isoFromDdMmYyyy('2026-07-06')).toBeNull();
  expect(isoFromDdMmYyyy('32/13/2026')).toBeNull();
});
test('monthOf extrae yyyy-mm', () => expect(monthOf('2026-07-06')).toBe('2026-07'));
test('todayIso tiene forma yyyy-mm-dd', () => expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/));

test('addDays cruza mes, año y 29 de febrero (local, sin UTC)', () => {
  expect(addDays('2026-01-31', 1)).toBe('2026-02-01');
  expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  expect(addDays('2024-02-28', 1)).toBe('2024-02-29');
  expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  expect(addDays('2026-09-04', 0)).toBe('2026-09-04');
});
test('daysInMonth y esFechaIso', () => {
  expect(daysInMonth(2026, 2)).toBe(28);
  expect(daysInMonth(2024, 2)).toBe(29);
  expect(daysInMonth(2026, 4)).toBe(30);
  expect(esFechaIso('2026-02-28')).toBe(true);
  expect(esFechaIso('2026-02-30')).toBe(false);
  expect(esFechaIso('2026-13-01')).toBe(false);
  expect(esFechaIso('2026-9-4')).toBe(false);
  expect(esFechaIso('')).toBe(false);
});
test('diffDias es b − a en días enteros', () => {
  expect(diffDias('2026-09-10', '2026-09-15')).toBe(5);
  expect(diffDias('2026-09-15', '2026-09-10')).toBe(-5);
  expect(diffDias('2026-08-31', '2026-09-01')).toBe(1);
  expect(diffDias('2026-09-04', '2026-09-04')).toBe(0);
});
test('weekdayLunes0: 0 = lunes … 6 = domingo', () => {
  expect(weekdayLunes0('2026-09-04')).toBe(4); // viernes
  expect(weekdayLunes0('2026-09-06')).toBe(6); // domingo
  expect(weekdayLunes0('2026-08-31')).toBe(0); // lunes
});
test('navegación de meses', () => {
  expect(mesAnterior('2026-01')).toBe('2025-12');
  expect(mesAnterior('2026-09')).toBe('2026-08');
  expect(mesSiguiente('2026-12')).toBe('2027-01');
  expect(primerDiaMes('2026-02')).toBe('2026-02-01');
  expect(ultimoDiaMes('2026-02')).toBe('2026-02-28');
  expect(ultimoDiaMes('2024-02')).toBe('2024-02-29');
});
test('nombres en español sin Intl', () => {
  expect(nombreMes('2026-09')).toBe('Septiembre 2026');
  expect(nombreDia('2026-09-04')).toBe('viernes');
  expect(fechaCorta('2026-09-15')).toBe('15 sep');
  expect(diaCorto('2026-09-04')).toBe('vie 4');
  expect(fechaLarga('2026-09-04')).toBe('Viernes 4 de septiembre');
  expect(ddmmyyyy('2026-09-04')).toBe('04/09/2026');
  expect(LETRAS_DIA).toEqual(['L', 'M', 'X', 'J', 'V', 'S', 'D']);
});
test('celdasMes: siempre 42 celdas desde el lunes ≤ día 1', () => {
  const sep = celdasMes('2026-09'); // 1 sep 2026 es martes
  expect(sep).toHaveLength(42);
  expect(sep[0]).toEqual({ iso: '2026-08-31', enMes: false });
  expect(sep[1]).toEqual({ iso: '2026-09-01', enMes: true });
  expect(sep.filter((c) => c.enMes)).toHaveLength(30);
  expect(sep[41].iso).toBe('2026-10-11');
  const feb = celdasMes('2026-02'); // 1 feb 2026 es domingo
  expect(feb[0].iso).toBe('2026-01-26');
  expect(feb[6]).toEqual({ iso: '2026-02-01', enMes: true });
});
