import { parseDolarApiOficial, parseCriptoDolarVcoud, fetchRatesWithFallback, refreshRatesOncePerDay, type RateProvider } from './providers';
import { CuentasDB, seedIfEmpty, getSettings } from '../db/db';
import dolarapi from './fixtures/dolarapi-oficial.json';
import vcoud from './fixtures/criptodolar-vcoud.json';

test('parseDolarApiOficial extrae el BCV del campo promedio (fixture real)', () => {
  const r = parseDolarApiOficial(dolarapi);
  expect(r.bcv).toBeGreaterThan(0);
});

test('parseCriptoDolarVcoud extrae el BCV del item slug "dolar-bcv" (campo price, fixture real)', () => {
  const r = parseCriptoDolarVcoud(vcoud);
  expect(r.bcv).toBeGreaterThan(0);
});

test('ningún parser online trae Binance: no hay proveedor USDT/VES con CORS hoy', () => {
  expect(parseDolarApiOficial(dolarapi).binance).toBeUndefined();
  expect(parseCriptoDolarVcoud(vcoud).binance).toBeUndefined();
});

test('parsers devuelven {} ante basura', () => {
  expect(parseDolarApiOficial({ promedio: 'nope' })).toEqual({});
  expect(parseCriptoDolarVcoud({})).toEqual({});
  expect(parseCriptoDolarVcoud([])).toEqual({});
});

const ok = (r: { bcv?: number; binance?: number }): RateProvider => ({ name: 'ok', fetch: async () => r });
const roto: RateProvider = { name: 'roto', fetch: async () => { throw new Error('CORS'); } };

test('fallback: dolarapi falla, vcoud responde con el BCV', async () => {
  expect(await fetchRatesWithFallback([roto, ok({ bcv: 111 })])).toEqual({ bcv: 111 });
});

test('fallback: si el primario ya trajo el BCV, el fallback no lo pisa', async () => {
  expect(await fetchRatesWithFallback([ok({ bcv: 111 }), ok({ bcv: 999 })])).toEqual({ bcv: 111 });
});

test('todos fallan: objeto vacío (la app sigue con última tasa/manual)', async () => {
  expect(await fetchRatesWithFallback([roto, roto])).toEqual({});
});

test('refreshRatesOncePerDay guarda el BCV de hoy y no repite', async () => {
  const d = new CuentasDB(`test-${crypto.randomUUID()}`);
  await seedIfEmpty(d);
  await refreshRatesOncePerDay(d, '2026-07-07', [ok({ bcv: 111 })]);
  expect(await d.rates.count()).toBe(1);
  expect((await getSettings(d)).lastRatesFetchDate).toBe('2026-07-07');
  await refreshRatesOncePerDay(d, '2026-07-07', [ok({ bcv: 999 })]); // mismo día: no toca nada
  expect((await d.rates.get(['2026-07-07', 'BCV']))?.vesPerUsd).toBe(111);
});
