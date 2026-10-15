package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun `parseVenezuelanAmount con decimales, negativo y entero`() {
        assertEquals(1351775L, parseVenezuelanAmount("13517,75"))
        assertEquals(-350000L, parseVenezuelanAmount("-3500"))
        assertEquals(917970L, parseVenezuelanAmount("9179,7"))
        assertEquals(60000L, parseVenezuelanAmount(" 600 "))
    }

    @Test fun `parseVenezuelanAmount redondea el artefacto float del export BNC`() {
        assertEquals(8770L, parseVenezuelanAmount("87,6999999999998"))
        assertEquals(480001L, parseVenezuelanAmount("4800,0099999999998"))
    }

    @Test fun `parseVenezuelanAmount rechaza punto de miles y basura`() {
        assertNull(parseVenezuelanAmount(""))
        assertNull(parseVenezuelanAmount("abc"))
        assertNull(parseVenezuelanAmount("1.234,50"))
    }

    @Test fun `formatMoney en Bs y en dolares, con miles y negativos`() {
        assertEquals("Bs 13.517,75", formatMoney(1351775, Currency.VES))
        assertEquals("Bs -3.500,00", formatMoney(-350000, Currency.VES))
        assertEquals("Bs 1.234.567,89", formatMoney(123456789, Currency.VES))
        assertEquals("$84.20", formatMoney(8420, Currency.USD))
        assertEquals("-$5.00", formatMoney(-500, Currency.USD))
        assertEquals("$1,000.00", formatMoney(100000, Currency.USD))
        assertEquals("Bs 0,00", formatMoney(0, Currency.VES))
    }
}
