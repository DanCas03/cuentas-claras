package com.cuentasclaras.app.importar

import com.cuentasclaras.app.importar.bnc.BncMeta
import com.cuentasclaras.app.importar.bnc.parseBncTxt
import com.cuentasclaras.app.importar.bnc.validateChain
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseBncTxtTest {
    private val fixture = javaClass.getResource("/importar/bnc-fixture.txt")!!.readText()

    @Test fun `metadatos y 9 filas, sin cabecera ni Saldo Inicial, en orden de archivo`() {
        val r = parseBncTxt(fixture)
        assertEquals(BncMeta("01910000000000001234", "PEREZ RODRIGUEZ MARIA JOSE", "25 ULTIMOS MOVIMIENTOS"), r.meta)
        assertEquals(9, r.rows.size)
        assertEquals(0, r.unparsed.size)
        val primera = r.rows[0]
        assertEquals("2026-07-02", primera.date)
        assertEquals("16:20:30.900", primera.time)
        assertEquals("100000007", primera.referencia)
        assertEquals(150000L, primera.amountMinor)
        assertEquals(630001L, primera.saldoMinor)
    }

    @Test fun `debe y haber con signo, artefacto float del saldo y tipo de transaccion vacio`() {
        val r = parseBncTxt(fixture)
        assertEquals(-34579L, r.rows[1].amountMinor)
        assertEquals(480001L, r.rows[1].saldoMinor)
        assertEquals(-10L, r.rows[2].amountMinor)
        assertEquals("", r.rows[1].tipoTransaccion)
    }

    @Test fun `una linea corrupta va a unparsed sin tumbar el resto y la cadena de saldos cuadra`() {
        val roto = "$fixture\r\nesto no es una fila\r\n03/07/2026\t99:99\tx\t1\t2\t3\t4\tno-numero\t0\t0\t0"
        val r = parseBncTxt(roto)
        assertEquals(9, r.rows.size)
        assertEquals(2, r.unparsed.size)
        assertEquals("esto no es una fila", r.unparsed[0].raw)
        assertEquals(emptyList(), validateChain(parseBncTxt(fixture).rows))
        val alteradas = parseBncTxt(fixture).rows.mapIndexed { i, x -> if (i == 3) x.copy(saldoMinor = x.saldoMinor + 100) else x }
        assertEquals(listOf(2, 3), validateChain(alteradas))
    }
}
