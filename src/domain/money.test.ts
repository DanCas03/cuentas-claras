import { parseVenezuelanAmount, parseUsAmount, formatMoney } from './money';

describe('parseVenezuelanAmount (formato BNC: coma decimal, sin separador de miles)', () => {
  test('entero sin decimales', () => expect(parseVenezuelanAmount('-3500')).toBe(-350000));
  test('con decimales', () => expect(parseVenezuelanAmount('13517,75')).toBe(1351775));
  test('negativo con decimales', () => expect(parseVenezuelanAmount('-2434,34')).toBe(-243434));
  test('cero', () => expect(parseVenezuelanAmount('0')).toBe(0));
  test('artefacto float del export BNC se redondea a céntimos', () =>
    expect(parseVenezuelanAmount('87,6999999999998')).toBe(8770));
  test('un solo decimal', () => expect(parseVenezuelanAmount('9179,7')).toBe(917970));
  test('espacios alrededor se toleran', () => expect(parseVenezuelanAmount(' 600 ')).toBe(60000));
  test('basura devuelve null', () => {
    expect(parseVenezuelanAmount('')).toBeNull();
    expect(parseVenezuelanAmount('abc')).toBeNull();
    expect(parseVenezuelanAmount('1.234,50')).toBeNull(); // BNC nunca trae punto de miles
  });
});

describe('parseUsAmount (formato Zinli: $ con signo, coma de miles, punto decimal)', () => {
  test('negativo', () => expect(parseUsAmount('-$0.99')).toBe(-99));
  test('positivo sin signo', () => expect(parseUsAmount('$100.00')).toBe(10000));
  test('positivo con signo', () => expect(parseUsAmount('+$5.00')).toBe(500));
  test('miles con coma', () => expect(parseUsAmount('$1,000.00')).toBe(100000));
  test('sin símbolo de dólar', () => expect(parseUsAmount('84.20')).toBe(8420));
  test('basura devuelve null', () => {
    expect(parseUsAmount('')).toBeNull();
    expect(parseUsAmount('Pagaste')).toBeNull();
  });
});

describe('formatMoney', () => {
  test('VES con miles y coma decimal', () => expect(formatMoney(1351775, 'VES')).toBe('Bs 13.517,75'));
  test('VES negativo', () => expect(formatMoney(-350000, 'VES')).toBe('Bs -3.500,00'));
  test('VES millones', () => expect(formatMoney(123456789, 'VES')).toBe('Bs 1.234.567,89'));
  test('USD', () => expect(formatMoney(8420, 'USD')).toBe('$84.20'));
  test('USD negativo', () => expect(formatMoney(-500, 'USD')).toBe('-$5.00'));
  test('USD miles', () => expect(formatMoney(100000, 'USD')).toBe('$1,000.00'));
  test('cero', () => {
    expect(formatMoney(0, 'VES')).toBe('Bs 0,00');
    expect(formatMoney(0, 'USD')).toBe('$0.00');
  });
});
