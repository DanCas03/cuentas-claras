import type { Currency } from './types';

/** "13517,75" | "-3500" | "87,6999999999998" → céntimos enteros. null si no es un monto BNC válido. */
export function parseVenezuelanAmount(raw: string): number | null {
  const m = /^([+-]?)(\d+)(?:,(\d+))?$/.exec(raw.trim());
  if (!m) return null;
  const sign = m[1] === '-' ? -1 : 1;
  const intMinor = Number(m[2]) * 100;
  // La parte decimal puede traer artefactos float del export ("6999999999998"):
  // se interpreta como fracción y se redondea a céntimos.
  const fracMinor = m[3] ? Math.round(Number(`0.${m[3]}`) * 100) : 0;
  return sign * (intMinor + fracMinor);
}

/** "-$0.99" | "$1,000.00" | "+$5.00" | "84.20" → céntimos enteros. */
export function parseUsAmount(raw: string): number | null {
  const m = /^([+-]?)\$?\s?([\d,]+)\.(\d{2})$/.exec(raw.trim());
  if (!m) return null;
  const sign = m[1] === '-' ? -1 : 1;
  const units = Number(m[2].replace(/,/g, ''));
  if (Number.isNaN(units)) return null;
  return sign * (units * 100 + Number(m[3]));
}

function conMiles(unidades: number, separador: string): string {
  return unidades.toString().replace(/\B(?=(\d{3})+(?!\d))/g, separador);
}

/** Formato visual venezolano: `Bs 13.517,75` · `$84.20` · negativos: `Bs -3.500,00` / `-$5.00`. */
export function formatMoney(minor: number, currency: Currency): string {
  const signo = minor < 0 ? '-' : '';
  const abs = Math.abs(Math.round(minor));
  const unidades = Math.floor(abs / 100);
  const centimos = String(abs % 100).padStart(2, '0');
  if (currency === 'USD') return `${signo}$${conMiles(unidades, ',')}.${centimos}`;
  return `Bs ${signo}${conMiles(unidades, '.')},${centimos}`;
}
