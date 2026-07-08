import { parseZinliLines, normalizePdfLine } from './parseZinli';
import { ZINLI_LINES } from './fixtures/zinliFixture';

test('normaliza ligaduras y espacios', () => {
  expect(normalizePdfLine('Saldo ﬁnal  del mes')).toBe('Saldo final del mes');
  expect(normalizePdfLine('identiﬁcación')).toBe('identificación');
});

test('extrae saldos y período pese a la ligadura partida en «fi nal»', () => {
  const r = parseZinliLines(ZINLI_LINES);
  expect(r.saldoInicialMinor).toBe(1019);
  expect(r.saldoFinalMinor).toBe(8420);
  expect(r.periodo).toBe('01 de junio al 30 de junio de 2026');
});

test('parsea los 4 movimientos, incluidos los partidos en dos líneas', () => {
  const r = parseZinliLines(ZINLI_LINES);
  expect(r.movements).toHaveLength(4);
  expect(r.movements[0]).toEqual({
    date: '2026-06-01',
    motivo: 'Pagaste',
    descripcion: 'CUOTA MENSUAL MANT. Y USO T.VISA JUNIO',
    referencia: '373700000001',
    amountMinor: -99,
  });
  expect(r.movements[1]).toMatchObject({ date: '2026-06-19', referencia: '950100000002', amountMinor: -500 });
  expect(r.movements[2]).toMatchObject({ motivo: 'Recibiste', referencia: '871300000003', amountMinor: 10000 });
  expect(r.movements[3]).toMatchObject({
    descripcion: 'SERVICIO EJEMPLO* SUSCRIP +14150000000 US',
    amountMinor: -2000,
  });
  expect(r.unparsed).toHaveLength(0);
});

test('valida el cuadre: inicial + suma = final', () => {
  expect(parseZinliLines(ZINLI_LINES).cuadra).toBe(true);
});

test('detecta cuando NO cuadra', () => {
  const rotas = ZINLI_LINES.map((l) => l.replace('-$5.00', '-$6.00'));
  expect(parseZinliLines(rotas).cuadra).toBe(false);
});

test('cuadra es null si falta un saldo', () => {
  // Reality-fix (Tarea 20, ver fixture): la línea de saldo final de ZINLI_LINES ya no
  // contiene la ligadura de un solo carácter «ﬁnal» sino el ítem partido «fi nal»
  // (estructura real de pdfjs-dist v6). Se filtra por esa forma para seguir removiendo
  // exactamente la línea de "Saldo final del mes".
  const sinFinal = ZINLI_LINES.filter((l) => !l.includes('fi nal'));
  expect(parseZinliLines(sinFinal).cuadra).toBeNull();
});

test('un movimiento que nunca cierra con referencia+monto va a unparsed', () => {
  const lineas = [
    'Saldo inicial del mes: $1.00',
    '05/06/2026 Pagaste ALGO SIN REFERENCIA NI MONTO',
    'Saldo final del mes: $1.00',
  ];
  const r = parseZinliLines(lineas);
  expect(r.movements).toHaveLength(0);
  expect(r.unparsed).toHaveLength(1);
  expect(r.cuadra).toBe(true); // sin movimientos, 1.00 + 0 = 1.00
});
