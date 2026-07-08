import { parseBncTxt, validateChain } from './parseBncTxt';
import { BNC_FIXTURE } from './fixtures/bncFixture';

test('extrae metadatos de la línea 1', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(r.meta).toEqual({
    accountNumber: '01910000000000001234',
    holder: 'PEREZ RODRIGUEZ MARIA JOSE',
    title: '25 ULTIMOS MOVIMIENTOS',
  });
});

test('parsea las 9 filas de datos, ignorando cabecera y pseudo-fila Saldo Inicial', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(r.rows).toHaveLength(9);
  expect(r.unparsed).toHaveLength(0);
  const primera = r.rows[0]; // orden del archivo: la más reciente primero
  expect(primera).toMatchObject({
    date: '2026-07-02', time: '16:20:30.900', referencia: '100000007',
    tipOperacion: 'Abono Pago Movil BNC', amountMinor: 150000, saldoMinor: 630001,
  });
});

test('combina Debe/Haber con signo y redondea artefactos float del saldo', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(r.rows[1].amountMinor).toBe(-34579);          // compra POS
  expect(r.rows[1].saldoMinor).toBe(480001);           // '4800,0099999999998' → céntimos
  expect(r.rows[2].amountMinor).toBe(-10);             // comisión de 0,10 Bs
  expect(r.rows[4].tipoTransaccion).toBe('P2PCSO');
  expect(r.rows[1].tipoTransaccion).toBe('');          // puede venir vacío
});

test('el par cargo+comisión comparte referencia pero difiere en monto y hora', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  const par = r.rows.filter((x) => x.referencia === '100000004');
  expect(par).toHaveLength(2);
  expect(par.map((x) => x.amountMinor).sort((a, b) => a - b)).toEqual([-120000, -360]);
});

test('una línea corrupta va a unparsed con motivo, sin tumbar el resto', () => {
  const roto = `${BNC_FIXTURE}\r\nesto no es una fila\r\n03/07/2026\t99:99\tx\t1\t2\t3\t4\tno-numero\t0\t0\t0`;
  const r = parseBncTxt(roto);
  expect(r.rows).toHaveLength(9);
  expect(r.unparsed).toHaveLength(2);
  expect(r.unparsed[0].raw).toBe('esto no es una fila');
  expect(r.unparsed[0].lineNumber).toBeGreaterThan(10);
  expect(r.unparsed[0].reason).toBeTruthy();
});

test('validateChain: la fixture encadena perfecta; una fila alterada rompe', () => {
  const r = parseBncTxt(BNC_FIXTURE);
  expect(validateChain(r.rows)).toEqual([]);
  const alteradas = r.rows.map((x, i) => (i === 3 ? { ...x, saldoMinor: x.saldoMinor + 100 } : x));
  expect(validateChain(alteradas)).toEqual([2, 3]); // rompe contra la anterior y la siguiente
});
