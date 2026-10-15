package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatesTest {
    private val rates = listOf(
        Rate("2026-07-01", RateSource.BCV, 110.5),
        Rate("2026-07-03", RateSource.BCV, 111.02),
        Rate("2026-07-03", RateSource.BINANCE, 132.4),
        Rate("2026-07-02", RateSource.MANUAL, 120.0),
    )

    @Test fun `effectiveRate usa la fuente activa mas reciente y el override manual exacto gana`() {
        assertEquals(111.02, effectiveRate("2026-07-05", RateSource.BCV, rates)?.vesPerUsd)
        assertEquals(120.0, effectiveRate("2026-07-02", RateSource.BCV, rates)?.vesPerUsd)
        assertEquals(132.4, effectiveRate("2026-07-04", RateSource.BINANCE, rates)?.vesPerUsd)
    }

    @Test fun `effectiveRate sin entrada previa cae a cualquier fuente, luego a la mas antigua futura`() {
        assertEquals(110.5, effectiveRate("2026-07-01", RateSource.BINANCE, rates)?.vesPerUsd)
        assertEquals(110.5, effectiveRate("2026-06-01", RateSource.BCV, rates)?.vesPerUsd)
        assertNull(effectiveRate("2026-07-01", RateSource.BCV, emptyList()))
    }

    @Test fun `convertMinor ida y vuelta redondea como la web`() {
        assertEquals(55510L, convertMinor(500, Currency.USD, Currency.VES, 111.02))
        assertEquals(500L, convertMinor(55510, Currency.VES, Currency.USD, 111.02))
        assertEquals(1L, vesToUsdMinor(100, 111.02))
        assertEquals(1234L, convertMinor(1234, Currency.USD, Currency.USD, 111.0))
    }
}
