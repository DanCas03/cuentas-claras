import type { Category, Rule } from '../../domain/types';
import { parseZinliLines } from './parseZinli';
import { ZINLI_LINES } from './fixtures/zinliFixture';
import { mapZinliMovements, zinliImportKey } from './mapZinli';

const categorias: Category[] = [
  { id: 'cat-suscripciones', name: 'Suscripciones', icon: '📺', color: '#000', kind: 'expense' },
  { id: 'cat-transferencia-recibida', name: 'Transferencia recibida', icon: '🔁', color: '#000', kind: 'income' },
];
const reglas: Rule[] = [{ id: 'r1', pattern: 'SERVICIO EJEMPLO', categoryId: 'cat-suscripciones', autoCreated: true }];
const movs = parseZinliLines(ZINLI_LINES).movements;
const opciones = { accountId: 'acc-zinli', existingKeys: new Set<string>(), rules: reglas, categories: categorias };

test('mapea movimientos a transacciones Zinli', () => {
  const drafts = mapZinliMovements(movs, opciones);
  expect(drafts).toHaveLength(4);
  expect(drafts[0].tx).toMatchObject({
    type: 'expense', accountId: 'acc-zinli', amountMinor: 99, date: '2026-06-01',
    source: 'zinli', importKey: 'zinli:373700000001',
  });
  expect(drafts[0].tx.note).toBe('Pagaste: CUOTA MENSUAL MANT. Y USO T.VISA JUNIO');
});

test('propone categorías: regla para gasto, transferencia recibida para ingreso', () => {
  const drafts = mapZinliMovements(movs, opciones);
  expect(drafts[3].tx.categoryId).toBe('cat-suscripciones');            // regla SERVICIO EJEMPLO
  expect(drafts[2].tx.categoryId).toBe('cat-transferencia-recibida');   // Recibiste
  expect(drafts[0].tx.categoryId).toBeUndefined();                      // gasto sin regla
});

test('dedupe por referencia', () => {
  const existentes = new Set([zinliImportKey(movs[1])]);
  const drafts = mapZinliMovements(movs, { ...opciones, existingKeys: existentes });
  expect(drafts.filter((d) => d.duplicate).map((d) => d.tx.importKey)).toEqual(['zinli:950100000002']);
});
