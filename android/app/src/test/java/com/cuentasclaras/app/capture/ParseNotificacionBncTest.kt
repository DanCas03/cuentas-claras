package com.cuentasclaras.app.capture

import com.cuentasclaras.app.data.db.TipoCaptura
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseNotificacionBncTest {
    private val fixtures = javaClass.getResource("/capture/notificaciones-bnc.txt")!!.readText().lines().filter { it.isNotBlank() }
    private val pagoMovil = fixtures[0]
    private val compra = fixtures[1]

    @Test fun `pago movil recibido es ingreso en Bs con referencia`() {
        assertEquals(CapturaBnc(TipoCaptura.INGRESO, 123456, "Pago móvil recibido · ref 123456789", "123456789"), parseNotificacionBnc(pagoMovil))
    }

    @Test fun `compra POS es gasto en Bs con referencia`() {
        assertEquals(CapturaBnc(TipoCaptura.GASTO, 789012, "Compra POS · ref 654321", "654321"), parseNotificacionBnc(compra))
    }

    @Test fun `monto sin miles con muchas cifras, saltos de linea, espacios no separables y mayusculas`() {
        assertEquals(25000000L, parseNotificacionBnc(pagoMovil.replace("Bs.1234,56", "Bs.250000,00"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.replace(" Dia:", "\nDia:").replace(" Ref:", "\nRef:"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.replace(" ", "\u00A0"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.replace(" Telf", "\u202FTelf"))?.montoMinor)
        assertEquals(123456L, parseNotificacionBnc(pagoMovil.uppercase())?.montoMinor)
    }

    @Test fun `textos no reconocidos devuelven null`() {
        assertNull(parseNotificacionBnc(""))
        assertNull(parseNotificacionBnc("hola mundo"))
        assertNull(parseNotificacionBnc("BNC te informa algo sin monto"))
    }
}
