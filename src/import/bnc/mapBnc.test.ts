import type { Category, Rule } from '../../domain/types';
import { parseBncTxt } from './parseBncTxt';
import { BNC_FIXTURE } from './fixtures/bncFixture';
import { bncImportKey, bncImportKeyLegacy, mapBncRows, detectGap, newestRow } from './mapBnc';

const categorias: Category[] = [
  { id: 'cat-comisiones', name: 'Comisiones bancarias', icon: '🏦', color: '#000', kind: 'expense' },
  { id: 'cat-ocio', name: 'Ocio/Juegos', icon: '🎮', color: '#000', kind: 'expense' },
  { id: 'cat-pago-movil-recibido', name: 'Pago móvil recibido', icon: '📲', color: '#000', kind: 'income' },
];
const reglas: Rule[] = [{ id: 'r1', pattern: 'JUEGAENLINEA', categoryId: 'cat-ocio', autoCreated: true }];
const rows = parseBncTxt(BNC_FIXTURE).rows;
const opciones = { accountId: 'acc-bnc', existingKeys: new Set<string>(), rules: reglas, categories: categorias };

test('mapea filas a borradores de transacción', () => {
  const drafts = mapBncRows(rows, opciones);
  expect(drafts).toHaveLength(9);
  const abono = drafts[0];
  expect(abono.tx).toMatchObject({
    type: 'income', accountId: 'acc-bnc', amountMinor: 150000, date: '2026-07-02',
    time: '16:20:30.900', source: 'bnc',
  });
  expect(abono.tx.note).toContain('PagomóvilBDV');
  expect(abono.tx.importKey).toBe('bnc:2026-07-02:100000007:16:20:30.900:150000');
  const gasto = drafts[1];
  expect(gasto.tx).toMatchObject({ type: 'expense', amountMinor: 34579 }); // valor absoluto
});

test('propone categorías: regla > comisión > pago móvil recibido', () => {
  const drafts = mapBncRows(rows, opciones);
  expect(drafts[6].tx.categoryId).toBe('cat-ocio');                 // regla JUEGAENLINEA
  expect(drafts[6].matchedRuleId).toBe('r1');
  expect(drafts[2].tx.categoryId).toBe('cat-comisiones');           // Comisión Mantenimiento
  expect(drafts[2].isCommission).toBe(true);
  expect(drafts[4].tx.categoryId).toBe('cat-comisiones');           // Comisión Pago Movil
  expect(drafts[0].tx.categoryId).toBe('cat-pago-movil-recibido');  // Abono Pago Movil
  expect(drafts[1].tx.categoryId).toBeUndefined();                  // POS sin regla
});

test('marca duplicados contra las claves existentes', () => {
  const existentes = new Set([bncImportKey(rows[0]), bncImportKey(rows[3])]);
  const drafts = mapBncRows(rows, { ...opciones, existingKeys: existentes });
  expect(drafts.filter((d) => d.duplicate)).toHaveLength(2);
});

test('detecta pares cargo+comisión por referencia compartida', () => {
  const drafts = mapBncRows(rows, opciones);
  const par = drafts.filter((d) => d.pairRef === '100000004');
  expect(par).toHaveLength(2);
  expect(drafts[1].pairRef).toBeNull(); // referencia única
});

test('detectGap distingue los cuatro estados', () => {
  expect(detectGap(rows, {})).toBe('primera-importacion');
  expect(detectGap(rows, { importKey: bncImportKey(rows[4]), saldoMinor: 999 })).toBe('solapado');
  expect(detectGap(rows, { importKey: 'bnc:viejo:x:1', saldoMinor: 50000 })).toBe('continuo'); // 500,00 Bs previos
  expect(detectGap(rows, { importKey: 'bnc:viejo:x:1', saldoMinor: 123 })).toBe('hueco');
});

test('newestRow es la primera fila del archivo', () => {
  expect(newestRow(rows)?.referencia).toBe('100000007');
  expect(newestRow([])).toBeNull();
});

test('Important #2: la clave incluye la fecha — comisiones recurrentes idénticas de distinto mes ya no colisionan', () => {
  const comisionJunio = rows.find((r) => r.referencia === '0' && r.tipOperacion === 'Comisión Mantenimiento Cuenta')!;
  const comisionJulio = { ...comisionJunio, date: '2026-07-30' };
  expect(bncImportKey(comisionJunio)).not.toBe(bncImportKey(comisionJulio));
  // Antes del fix, la comisión de julio se marcaba erróneamente "ya importada" contra la de junio.
  const existentesSoloJunio = new Set([bncImportKey(comisionJunio)]);
  const drafts = mapBncRows([comisionJulio], { ...opciones, existingKeys: existentesSoloJunio });
  expect(drafts[0].duplicate).toBe(false);
});

test('Important #2 — compat hacia atrás: reconoce como duplicado un movimiento ya persistido con la clave vieja (sin fecha)', () => {
  // Simula datos ya existentes en un dispositivo (importKey con el formato previo al fix).
  const existentesFormatoViejo = new Set([bncImportKeyLegacy(rows[0])]);
  const drafts = mapBncRows(rows, { ...opciones, existingKeys: existentesFormatoViejo });
  expect(drafts[0].duplicate).toBe(true); // re-importar el mismo TXT ya importado antes del fix: 0 duplicados perdidos
});

test('Important #2 — detectGap reconoce solapamiento contra un ancla con el formato de clave viejo (compat)', () => {
  const filaAncla = rows[4];
  expect(detectGap(rows, { importKey: bncImportKeyLegacy(filaAncla), saldoMinor: 999 })).toBe('solapado');
});
