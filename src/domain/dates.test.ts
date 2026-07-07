import { isoFromDdMmYyyy, monthOf, todayIso } from './dates';

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
