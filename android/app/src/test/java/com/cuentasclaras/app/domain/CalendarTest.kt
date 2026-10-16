package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarTest {
    private val cuentas = listOf(
        Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
        Account("acc-zinli", "Zinli", Currency.USD, AccountKind.WALLET, false, false),
    )
    private fun ev(id: String = "e1", titulo: String = "Quincena", startDate: String = "2026-01-01",
                   endDate: String? = null, recurrence: Recurrence = RECURRENCIA_QUINCENAL,
                   omitidas: List<String>? = null, categoryId: String = "cat-sueldo") =
        CalEvent(id, titulo, categoryId, startDate = startDate, endDate = endDate,
            recurrence = recurrence, omitidas = omitidas, createdIso = "2026-09-04T10:00:00.000Z")

    @Test fun `quincenal recorta el 31 al ultimo dia del mes, incluido febrero bisiesto`() {
        assertEquals(listOf("2026-01-15", "2026-01-31"), fechasDeEvento(ev(), "2026-01-01", "2026-01-31"))
        assertEquals(listOf("2026-02-15", "2026-02-28"), fechasDeEvento(ev(), "2026-02-01", "2026-02-28"))
        assertEquals(listOf("2026-04-15", "2026-04-30"), fechasDeEvento(ev(), "2026-04-01", "2026-04-30"))
        assertEquals(listOf("2024-02-15", "2024-02-29"), fechasDeEvento(ev(startDate = "2024-01-01"), "2024-02-01", "2024-02-29"))
        assertEquals(listOf("2026-02-28"),
            fechasDeEvento(ev(recurrence = Recurrence.Monthly(listOf(30, 31))), "2026-02-01", "2026-02-28"))
        assertEquals(listOf("2026-09-15"), fechasDeEvento(ev(endDate = "2026-09-15"), "2026-09-01", "2026-09-30"))
        assertEquals(emptyList(),
            fechasDeEvento(ev(startDate = "2026-09-20", recurrence = Recurrence.Monthly(listOf(15))), "2026-09-01", "2026-09-30"))
    }

    @Test fun `semanal salta al primer multiplo, una vez ignora el fin y anual recorta el 29 de febrero`() {
        val semanal = ev(startDate = "2026-09-02", recurrence = Recurrence.Weekly)
        assertEquals(listOf("2026-10-07", "2026-10-14", "2026-10-21", "2026-10-28"), fechasDeEvento(semanal, "2026-10-01", "2026-10-31"))
        assertEquals(emptyList(), fechasDeEvento(semanal, "2026-08-01", "2026-08-31"))
        val once = ev(startDate = "2026-10-20", recurrence = Recurrence.Once, endDate = "2026-01-01")
        assertEquals(listOf("2026-10-20"), fechasDeEvento(once, "2026-10-20", "2026-10-20"))
        val anual = ev(startDate = "2024-02-29", recurrence = Recurrence.Yearly)
        assertEquals(listOf("2025-02-28", "2026-02-28"), fechasDeEvento(anual, "2025-01-01", "2026-12-31"))
    }

    @Test fun `estadoDe hecha gana a omitida gana a vencida y hoy es pendiente`() {
        val hoy = "2026-09-04"
        assertEquals(EstadoOcurrencia.HECHA, estadoDe("2026-08-31", true, listOf("t1"), hoy))
        assertEquals(EstadoOcurrencia.OMITIDA, estadoDe("2026-08-31", true, emptyList(), hoy))
        assertEquals(EstadoOcurrencia.VENCIDA, estadoDe("2026-08-31", false, emptyList(), hoy))
        assertEquals(EstadoOcurrencia.PENDIENTE, estadoDe("2026-09-04", false, emptyList(), hoy))
    }

    @Test fun `ocurrenciasEnRango une generadas y asignadas, suma por moneda y marca fueraDeRegla`() {
        val eventos = listOf(
            ev(omitidas = listOf("2026-09-30")),
            ev(id = "e2", titulo = "Netflix", categoryId = "cat-suscripciones", startDate = "2026-01-05",
                recurrence = Recurrence.Monthly(listOf(5))),
        )
        fun tx(id: String, eventDate: String, accountId: String = "acc-bnc", monto: Long = 800000, eventId: String = "e1") =
            Transaction(id, TxType.INCOME, accountId, monto, "2026-09-15", source = TxSource.MANUAL,
                eventId = eventId, eventDate = eventDate)
        val txs = listOf(
            tx("a", "2026-09-15", monto = 500000), tx("b", "2026-09-15", monto = 300000),
            tx("c", "2026-09-15", "acc-zinli", 1000), tx("d", "2026-09-15", eventId = "fantasma"),
            tx("rara", "2026-09-07"),
        )
        val o = ocurrenciasEnRango(eventos, txs, cuentas, "2026-09-01", "2026-09-30", "2026-09-04")
        assertEquals(
            listOf("2026-09-05 Netflix PENDIENTE", "2026-09-07 Quincena HECHA", "2026-09-15 Quincena HECHA", "2026-09-30 Quincena OMITIDA"),
            o.map { "${it.date} ${it.evento.titulo} ${it.estado}" },
        )
        val quincena = o.first { it.date == "2026-09-15" }
        assertEquals(listOf("a", "b", "c"), quincena.txIds.sorted())
        assertTrue(MontoMoneda(Currency.VES, 800000) in quincena.asignadoPorMoneda)
        assertTrue(MontoMoneda(Currency.USD, 1000) in quincena.asignadoPorMoneda)
        assertEquals(true, o.first { it.date == "2026-09-07" }.fueraDeRegla)
        assertEquals(false, quincena.fueraDeRegla)
    }
}
