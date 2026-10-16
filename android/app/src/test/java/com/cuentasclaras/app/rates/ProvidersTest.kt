package com.cuentasclaras.app.rates

import com.cuentasclaras.app.domain.JsonCuentas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvidersTest {
    private fun fixture(nombre: String) =
        JsonCuentas.parseToJsonElement(javaClass.getResource("/rates/$nombre")!!.readText())

    @Test fun `dolarapi trae el BCV en promedio`() {
        assertEquals(674.9305, parseDolarApiOficial(fixture("dolarapi-oficial.json")).bcv)
    }

    @Test fun `vcoud trae el BCV en el item dolar-bcv`() {
        val bcv = parseCriptoDolarVcoud(fixture("criptodolar-vcoud.json")).bcv
        assertTrue(bcv != null && bcv > 0)
    }
}
