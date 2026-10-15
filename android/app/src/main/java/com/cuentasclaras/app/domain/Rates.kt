package com.cuentasclaras.app.domain

/** Tasa vigente para una fecha según la fuente activa (mismas reglas que rates.ts):
 *  1) MANUAL exacta del día; 2) la más reciente ≤ fecha de la fuente activa;
 *  3) la más reciente ≤ fecha de cualquier fuente; 4) la más antigua posterior; 5) null. */
fun effectiveRate(dateIso: String, activeSource: RateSource, rates: List<Rate>): Rate? {
    rates.firstOrNull { it.source == RateSource.MANUAL && it.date == dateIso }?.let { return it }

    fun masReciente(candidatas: List<Rate>): Rate? =
        candidatas.reduceOrNull { a, b -> if (b.date > a.date) b else a }

    masReciente(rates.filter { it.source == activeSource && it.date <= dateIso })?.let { return it }
    masReciente(rates.filter { it.date <= dateIso })?.let { return it }
    return rates.filter { it.date > dateIso }.reduceOrNull { a, b -> if (b.date < a.date) b else a }
}

fun usdToVesMinor(usdMinor: Long, vesPerUsd: Double): Long = Math.round(usdMinor * vesPerUsd)

fun vesToUsdMinor(vesMinor: Long, vesPerUsd: Double): Long = Math.round(vesMinor / vesPerUsd)

fun convertMinor(amountMinor: Long, from: Currency, to: Currency, vesPerUsd: Double): Long {
    if (from == to) return amountMinor
    return if (from == Currency.USD) usdToVesMinor(amountMinor, vesPerUsd) else vesToUsdMinor(amountMinor, vesPerUsd)
}
