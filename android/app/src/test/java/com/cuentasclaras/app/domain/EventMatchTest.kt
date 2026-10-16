package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EventMatchTest {
    private val cuentas = listOf(
        Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
        Account("acc-zinli", "Zinli", Currency.USD, AccountKind.WALLET, false, false),
    )
    private val categorias = listOf(
        Category("cat-sueldo", "Sueldo/Pagos", "💼", "#34d399", CategoryKind.INCOME),
        Category("cat-suscripciones", "Suscripciones", "📺", "#22d3ee", CategoryKind.EXPENSE),
    )
    private val quincena = CalEvent(
        "e-quincena", "Quincena", "cat-sueldo", accountId = "acc-bnc", amountMinor = 800000,
        startDate = "2026-01-01", recurrence = RECURRENCIA_QUINCENAL, createdIso = "2026-09-04T10:00:00.000Z",
    )
    private fun ctx(eventos: List<CalEvent> = listOf(quincena), txs: List<Transaction> = emptyList()) =
        CtxSugerencia(eventos, txs, cuentas, categorias, "2026-09-04")
    private fun ingreso(date: String, monto: Long = 800000, accountId: String = "acc-bnc") =
        TxParaSugerir(CategoryKind.INCOME, date, accountId, monto)

    @Test fun `ventana de 31 dias atras y 7 adelante con bordes inclusivos`() {
        fun fechas(date: String) = candidatasParaTx(ingreso(date), ctx()).map { it.ocurrencia.date }
        assertEquals(true, "2026-09-15" in fechas("2026-10-16"))
        assertEquals(false, "2026-09-15" in fechas("2026-10-17"))
        assertEquals(true, "2026-09-15" in fechas("2026-09-08"))
        assertEquals(false, "2026-09-15" in fechas("2026-09-07"))
        assertEquals(emptyList(), candidatasParaTx(TxParaSugerir(CategoryKind.EXPENSE, "2026-09-15", "acc-bnc", 800000), ctx()))
        assertEquals(emptyList(), candidatasParaTx(ingreso("2026-09-15", accountId = "acc-zinli"), ctx()))
    }

    @Test fun `sugerirOcurrencia respeta la tolerancia de 10 por ciento`() {
        assertEquals("2026-09-15", sugerirOcurrencia(ingreso("2026-09-15", 880000), ctx())?.date)
        assertEquals("2026-09-15", sugerirOcurrencia(ingreso("2026-09-15", 720000), ctx())?.date)
        assertNull(sugerirOcurrencia(ingreso("2026-09-15", 5000), ctx()))
        val c = candidatasParaTx(ingreso("2026-09-15", 880001), ctx()).first { it.ocurrencia.date == "2026-09-15" }
        assertEquals(false, c.montoCoincide)
        assertEquals(true, candidatasParaTx(ingreso("2026-09-15", 0), ctx()).first().sugerible)
    }

    @Test fun `omitidas fuera y hechas solo con incluirHechas`() {
        val hecha = ctx(txs = listOf(Transaction("ya", TxType.INCOME, "acc-bnc", 800000, "2026-09-15",
            source = TxSource.MANUAL, eventId = "e-quincena", eventDate = "2026-09-15")))
        assertEquals(listOf("2026-08-31", "2026-08-15"), candidatasParaTx(ingreso("2026-09-15"), hecha).map { it.ocurrencia.date })
        val omitida = ctx(eventos = listOf(quincena.copy(omitidas = listOf("2026-09-15"))))
        assertEquals(listOf("2026-08-31", "2026-08-15"), candidatasParaTx(ingreso("2026-09-15"), omitida).map { it.ocurrencia.date })
    }

    @Test fun `sugerirParaLote es greedy global y determinista`() {
        fun tx(id: String, date: String, monto: Long) =
            Transaction(id, TxType.INCOME, "acc-bnc", monto, date, source = TxSource.BNC)
        val lote = listOf(tx("amigo", "2026-09-14", 5000), tx("sueldo", "2026-09-15", 800000), tx("otro", "2026-09-16", 790000))
        val m = sugerirParaLote(lote, ctx())
        assertEquals(Ocurrencia("e-quincena", "2026-09-15"), m["sueldo"])
        assertEquals(Ocurrencia("e-quincena", "2026-08-31"), m["otro"])
        assertEquals(false, "amigo" in m)
        assertEquals(m.toList(), sugerirParaLote(lote, ctx()).toList())
    }

    @Test fun `aplicarAsignacion copia la categoria y desasignar la conserva, direccion contraria lanza`() {
        val original = Transaction("t1", TxType.INCOME, "acc-bnc", 800000, "2026-09-15", categoryId = "cat-otros-ingresos", source = TxSource.MANUAL)
        val asignada = aplicarAsignacion(original, Ocurrencia("e-quincena", "2026-09-15"), quincena)
        assertEquals("cat-sueldo", asignada.categoryId)
        assertEquals(original.amountMinor, asignada.amountMinor)
        val suelta = aplicarAsignacion(asignada, null, null)
        assertNull(suelta.eventId)
        assertEquals("cat-sueldo", suelta.categoryId)
        val e = assertFailsWith<IllegalArgumentException> { validarAsignacion(TxType.EXPENSE, quincena, categorias) }
        assertEquals("Este movimiento es un gasto y el evento es un cobro", e.message)
    }
}
