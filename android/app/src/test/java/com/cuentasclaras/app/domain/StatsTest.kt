package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {
    private val cuentas = listOf(
        Account("bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
        Account("efectivo", "Efectivo", Currency.USD, AccountKind.CASH, false, false),
    )
    private val rates = listOf(Rate("2026-07-01", RateSource.BCV, 100.0))
    private fun tx(type: TxType, amountMinor: Long, date: String, accountId: String = "efectivo",
                   categoryId: String? = null, groupId: String? = null) =
        Transaction(nuevoId(), type, accountId, amountMinor, date, categoryId = categoryId,
            source = TxSource.MANUAL, groupId = groupId)

    private val txs = listOf(
        tx(TxType.EXPENSE, 100000, "2026-07-05", "bnc", "cat-a"),
        tx(TxType.EXPENSE, 500, "2026-07-10", categoryId = "cat-a"),
        tx(TxType.EXPENSE, 300, "2026-07-10", categoryId = "cat-b"),
        tx(TxType.INCOME, 2000, "2026-07-03"),
        tx(TxType.TRANSFER, 999, "2026-07-05"),
        tx(TxType.ADJUSTMENT, 999, "2026-07-05"),
        tx(TxType.EXPENSE, 777, "2026-06-30"),
    )

    @Test fun `resume el mes en USD con categorias desc y acumulado diario`() {
        val r = monthSummary(txs, cuentas, "2026-07", Currency.USD, rates, RateSource.BCV)
        assertEquals(2000L, r.incomeMinor)
        assertEquals(1800L, r.expenseMinor)
        assertEquals(listOf(GastoCategoria("cat-a", 1500), GastoCategoria("cat-b", 300)), r.byCategory)
        assertEquals(listOf(IngresoCategoria(null, 2000)), r.incomeByCategory)
        assertEquals(31, r.dailyCumulative.size)
        assertEquals(PuntoDiario(4, 0), r.dailyCumulative[3])
        assertEquals(PuntoDiario(5, 1000), r.dailyCumulative[4])
        assertEquals(PuntoDiario(31, 1800), r.dailyCumulative[30])
    }

    @Test fun `acumula los ingresos por dia sin mezclar gastos`() {
        // Bs 1.000,00 en la cuenta bnc → a 100 Bs/USD son $10.00 = 1000 minor
        val conIngresos = txs + tx(TxType.INCOME, 100000, "2026-07-20", "bnc")
        val r = monthSummary(conIngresos, cuentas, "2026-07", Currency.USD, rates, RateSource.BCV)
        assertEquals(31, r.dailyCumulativeIncome.size)
        assertEquals(PuntoDiario(2, 0), r.dailyCumulativeIncome[1])
        assertEquals(PuntoDiario(3, 2000), r.dailyCumulativeIncome[2])
        assertEquals(PuntoDiario(19, 2000), r.dailyCumulativeIncome[18])
        assertEquals(PuntoDiario(31, 3000), r.dailyCumulativeIncome[30])
        assertEquals(PuntoDiario(31, 1800), r.dailyCumulative[30]) // los gastos no cambian
    }

    @Test fun `sin tasa omite y cuenta las VES, vista en Bs convierte las USD`() {
        val sinTasa = monthSummary(txs, cuentas, "2026-07", Currency.USD, emptyList(), RateSource.BCV)
        assertEquals(1, sinTasa.omitidasSinTasa)
        assertEquals(800L, sinTasa.expenseMinor)
        assertEquals(180000L, monthSummary(txs, cuentas, "2026-07", Currency.VES, rates, RateSource.BCV).expenseMinor)
    }

    @Test fun `con grupos solo cuenta el residuo y el residuo con categoria consume presupuesto`() {
        val categorias = listOf(
            Category("cat-supermercado", "Supermercado", "🛒", "#fff", CategoryKind.EXPENSE, monthlyBudgetMinor = 10000),
            Category("cat-vuelto-gasto", "Vuelto", "↪️", "#f87171", CategoryKind.EXPENSE),
        )
        val conGrupo = listOf(
            tx(TxType.EXPENSE, 2000, "2026-07-10", categoryId = "cat-otros"),
            tx(TxType.INCOME, 300, "2026-07-10", groupId = "g1"),
            tx(TxType.EXPENSE, 500, "2026-07-10", categoryId = "cat-otros", groupId = "g1"),
        )
        val efectivas = txsEfectivas(conGrupo, cuentas, listOf(TxGroup("g1", categoryId = "cat-supermercado", createdIso = "x")), categorias)
        val r = monthSummary(efectivas, cuentas, "2026-07", Currency.USD, emptyList(), RateSource.BCV)
        assertEquals(2200L, r.expenseMinor)
        assertEquals(0L, r.incomeMinor)
        assertEquals(listOf(GastoCategoria("cat-otros", 2000), GastoCategoria("cat-supermercado", 200)), r.byCategory)
        val presupuesto = budgetStatus(efectivas, cuentas, categorias, "2026-07", emptyList(), RateSource.BCV)
        assertEquals(200L, presupuesto.single().gastoUsdMinor)
        assertEquals(0.02, presupuesto.single().ratio)
        assertEquals(listOf("2026-01", "2025-12", "2025-11"), lastMonths("2026-01", 3))
    }
}
