import type { Rule } from './types';
import { matchCategory, buildRule } from './rules';

const reglas: Rule[] = [
  { id: 'r1', pattern: 'juegaenlinea', categoryId: 'cat-ocio', autoCreated: true },
  { id: 'r2', pattern: 'FARMACIA', categoryId: 'cat-salud', autoCreated: true },
  { id: 'r3', pattern: 'farma', categoryId: 'cat-otros-gastos', autoCreated: true },
];

test('matchea substring sin importar mayúsculas', () => {
  expect(matchCategory('Pago Móvil BNC al Instante JUEGAENLINEA3', reglas)?.categoryId).toBe('cat-ocio');
  expect(matchCategory('farmacia central c a', reglas)?.categoryId).toBe('cat-salud');
});
test('gana la primera regla del array', () => {
  expect(matchCategory('FARMACIA AVILA', reglas)?.id).toBe('r2'); // no r3
});
test('sin match devuelve null', () => {
  expect(matchCategory('MERCADO LA ESQUINA', reglas)).toBeNull();
});
test('patrón vacío nunca matchea', () => {
  expect(matchCategory('cualquier cosa', [{ id: 'x', pattern: '  ', categoryId: 'c', autoCreated: true }])).toBeNull();
});
test('buildRule recorta y valida', () => {
  expect(buildRule('  Netflix  ', 'cat-suscripciones')).toMatchObject({ pattern: 'Netflix', categoryId: 'cat-suscripciones', autoCreated: true });
  expect(() => buildRule('   ', 'c')).toThrow('Patrón vacío');
});
