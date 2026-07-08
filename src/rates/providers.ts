import type { Rate } from '../domain/types';
import { CuentasDB, getSettings, saveSettings } from '../db/db';

export interface FetchedRates {
  bcv?: number;
  binance?: number; // reservado: ningún proveedor online lo trae hoy — el valor Binance sale del override manual
}

export interface RateProvider {
  name: string;
  fetch(): Promise<FetchedRates>;
}

/** ve.dolarapi.com/v1/dolares/oficial → BCV en `promedio` (compra/venta vienen null). */
export function parseDolarApiOficial(json: unknown): FetchedRates {
  const j = json as { promedio?: unknown };
  return typeof j?.promedio === 'number' && j.promedio > 0 ? { bcv: j.promedio } : {};
}

/** exchange.vcoud.com/coins/latest → array de monitores; BCV = item con `slug === 'dolar-bcv'`, valor en `price`. */
export function parseCriptoDolarVcoud(json: unknown): FetchedRates {
  const arr = Array.isArray(json) ? (json as Array<{ slug?: unknown; price?: unknown }>) : [];
  const bcv = arr.find((x) => x?.slug === 'dolar-bcv');
  const price = bcv?.price;
  return typeof price === 'number' && price > 0 ? { bcv: price } : {};
}

async function fetchJson(url: string): Promise<unknown> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 8000);
  try {
    const res = await fetch(url, { signal: ctl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

// URLs y campos confirmados en docs/spikes/2026-07-07-cors-tasas.md — mantener sincronizadas.
// Cadena solo-BCV: dolarapi (primario) → vcoud (fallback). Binance USDT/VES no tiene proveedor CORS público hoy.
export const dolarApiProvider: RateProvider = {
  name: 'dolarapi',
  fetch: async () => parseDolarApiOficial(await fetchJson('https://ve.dolarapi.com/v1/dolares/oficial')),
};

export const criptoDolarProvider: RateProvider = {
  name: 'vcoud',
  fetch: async () => parseCriptoDolarVcoud(await fetchJson('https://exchange.vcoud.com/coins/latest')),
};

export const PROVIDERS_DEFAULT: RateProvider[] = [dolarApiProvider, criptoDolarProvider];

/** Recorre la cadena acumulando lo que falte; errores de red/CORS pasan al siguiente. */
export async function fetchRatesWithFallback(providers: RateProvider[] = PROVIDERS_DEFAULT): Promise<FetchedRates> {
  const out: FetchedRates = {};
  for (const p of providers) {
    if (out.bcv !== undefined) break; // cadena solo-BCV: en cuanto un proveedor da el BCV, paramos
    try {
      const r = await p.fetch();
      out.bcv ??= r.bcv;
      out.binance ??= r.binance;
    } catch {
      // siguiente proveedor; el último recurso siempre es la tasa manual
    }
  }
  return out;
}

export async function storeFetchedRates(d: CuentasDB, hoyIso: string, r: FetchedRates): Promise<number> {
  const filas: Rate[] = [];
  if (r.bcv) filas.push({ date: hoyIso, source: 'BCV', vesPerUsd: r.bcv });
  if (r.binance) filas.push({ date: hoyIso, source: 'BINANCE', vesPerUsd: r.binance });
  if (filas.length > 0) await d.rates.bulkPut(filas);
  return filas.length;
}

export async function refreshRatesOncePerDay(
  d: CuentasDB,
  hoyIso: string,
  providers: RateProvider[] = PROVIDERS_DEFAULT,
): Promise<void> {
  const s = await getSettings(d);
  if (s.lastRatesFetchDate === hoyIso) return;
  // `=== false` explícito: en el navegador es idéntico a `!navigator.onLine`, pero
  // en Node (test TDD) `navigator.onLine` es undefined y esto NO cortocircuita.
  if (navigator.onLine === false) return;
  const guardadas = await storeFetchedRates(d, hoyIso, await fetchRatesWithFallback(providers));
  if (guardadas > 0) await saveSettings(d, { lastRatesFetchDate: hoyIso });
}
