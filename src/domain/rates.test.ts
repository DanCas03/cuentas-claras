import type { Rate } from './types';
import { effectiveRate, usdToVesMinor, vesToUsdMinor, convertMinor } from './rates';

const rates: Rate[] = [
  { date: '2026-07-01', source: 'BCV', vesPerUsd: 110.5 },
  { date: '2026-07-03', source: 'BCV', vesPerUsd: 111.02 },
  { date: '2026-07-03', source: 'BINANCE', vesPerUsd: 132.4 },
  { date: '2026-07-02', source: 'MANUAL', vesPerUsd: 120 },
];

describe('effectiveRate', () => {
  test('usa la fuente activa más reciente <= fecha', () => {
    expect(effectiveRate('2026-07-05', 'BCV', rates)?.vesPerUsd).toBe(111.02);
    expect(effectiveRate('2026-07-01', 'BCV', rates)?.vesPerUsd).toBe(110.5);
  });
  test('el override MANUAL exacto del día gana sobre la fuente activa', () => {
    expect(effectiveRate('2026-07-02', 'BCV', rates)?.vesPerUsd).toBe(120);
  });
  test('fuente activa BINANCE', () => {
    expect(effectiveRate('2026-07-04', 'BINANCE', rates)?.vesPerUsd).toBe(132.4);
  });
  test('fuente activa sin entrada <= fecha cae a cualquier fuente <= fecha', () => {
    expect(effectiveRate('2026-07-01', 'BINANCE', rates)?.vesPerUsd).toBe(110.5);
  });
  test('fecha anterior a todas: usa la más antigua disponible', () => {
    expect(effectiveRate('2026-06-01', 'BCV', rates)?.vesPerUsd).toBe(110.5);
  });
  test('sin tasas devuelve null', () => {
    expect(effectiveRate('2026-07-01', 'BCV', [])).toBeNull();
  });
});

describe('conversión', () => {
  test('usd → ves redondeando a céntimos', () => expect(usdToVesMinor(500, 111.02)).toBe(55510));
  test('ves → usd redondeando a céntimos', () => expect(vesToUsdMinor(55510, 111.02)).toBe(500));
  test('redondeo no sesgado', () => expect(vesToUsdMinor(100, 111.02)).toBe(1)); // 0,9º céntimo → 1
  test('convertMinor misma moneda es identidad', () => expect(convertMinor(1234, 'USD', 'USD', 111)).toBe(1234));
  test('convertMinor cruza monedas', () => {
    expect(convertMinor(500, 'USD', 'VES', 111.02)).toBe(55510);
    expect(convertMinor(55510, 'VES', 'USD', 111.02)).toBe(500);
  });
});
