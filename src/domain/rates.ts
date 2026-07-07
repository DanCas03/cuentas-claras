import type { Currency, Rate, RateSource } from './types';

/** Tasa vigente para una fecha según la fuente activa. Ver reglas numeradas en el plan. */
export function effectiveRate(dateIso: string, activeSource: RateSource, rates: Rate[]): Rate | null {
  const manualExacta = rates.find((r) => r.source === 'MANUAL' && r.date === dateIso);
  if (manualExacta) return manualExacta;

  const masReciente = (candidatas: Rate[]): Rate | null =>
    candidatas.length === 0
      ? null
      : candidatas.reduce((a, b) => (b.date > a.date ? b : a));

  const deFuente = masReciente(rates.filter((r) => r.source === activeSource && r.date <= dateIso));
  if (deFuente) return deFuente;

  const cualquiera = masReciente(rates.filter((r) => r.date <= dateIso));
  if (cualquiera) return cualquiera;

  const futuras = rates.filter((r) => r.date > dateIso);
  if (futuras.length > 0) return futuras.reduce((a, b) => (b.date < a.date ? b : a));

  return null;
}

export function usdToVesMinor(usdMinor: number, vesPerUsd: number): number {
  return Math.round(usdMinor * vesPerUsd);
}

export function vesToUsdMinor(vesMinor: number, vesPerUsd: number): number {
  return Math.round(vesMinor / vesPerUsd);
}

export function convertMinor(amountMinor: number, from: Currency, to: Currency, vesPerUsd: number): number {
  if (from === to) return amountMinor;
  return from === 'USD' ? usdToVesMinor(amountMinor, vesPerUsd) : vesToUsdMinor(amountMinor, vesPerUsd);
}
