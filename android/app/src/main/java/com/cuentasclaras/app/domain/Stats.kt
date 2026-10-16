package com.cuentasclaras.app.domain

import java.time.YearMonth

data class GastoCategoria(val categoryId: String?, val expenseMinor: Long)
data class IngresoCategoria(val categoryId: String?, val incomeMinor: Long)
data class PuntoDiario(val day: Int, val acumuladoMinor: Long)

data class ResumenMes(
    val month: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    /** SOLO gastos, desc por monto. */
    val byCategory: List<GastoCategoria>,
    /** v3: ingresos por categoría, desc por monto. */
    val incomeByCategory: List<IngresoCategoria>,
    val dailyCumulative: List<PuntoDiario>,
    val omitidasSinTasa: Int,
)

fun monthSummary(
    txs: List<Transaction>,
    accounts: List<Account>,
    month: String,
    viewCurrency: Currency,
    rates: List<Rate>,
    activeSource: RateSource,
): ResumenMes {
    val monedaDe = accounts.associate { it.id to it.currency }
    var incomeMinor = 0L
    var expenseMinor = 0L
    var omitidasSinTasa = 0
    val porCategoria = LinkedHashMap<String?, Long>()
    val porCategoriaIngreso = LinkedHashMap<String?, Long>()
    val porDia = HashMap<Int, Long>()

    for (t in txs) {
        if (!t.date.startsWith(month)) continue
        if (t.type != TxType.EXPENSE && t.type != TxType.INCOME) continue
        val desde = monedaDe[t.accountId] ?: Currency.USD
        var minor = t.amountMinor
        if (desde != viewCurrency) {
            val rate = effectiveRate(t.date, activeSource, rates)
            if (rate == null) {
                omitidasSinTasa++
                continue
            }
            minor = convertMinor(minor, desde, viewCurrency, rate.vesPerUsd)
        }
        if (t.type == TxType.INCOME) {
            incomeMinor += minor
            porCategoriaIngreso[t.categoryId] = (porCategoriaIngreso[t.categoryId] ?: 0L) + minor
        } else {
            // Una fecha corrupta (un respaldo sin validar) no tumba el resumen: esa tx se salta.
            val dia = (if (t.date.length >= 10) t.date.substring(8, 10).toIntOrNull() else null) ?: continue
            expenseMinor += minor
            porCategoria[t.categoryId] = (porCategoria[t.categoryId] ?: 0L) + minor
            porDia[dia] = (porDia[dia] ?: 0L) + minor
        }
    }

    val diasDelMes = YearMonth.parse(month).lengthOfMonth()
    var acumulado = 0L
    val dailyCumulative = List(diasDelMes) { i ->
        acumulado += porDia[i + 1] ?: 0L
        PuntoDiario(i + 1, acumulado)
    }
    val byCategory = porCategoria.map { GastoCategoria(it.key, it.value) }.sortedByDescending { it.expenseMinor }
    val incomeByCategory = porCategoriaIngreso.map { IngresoCategoria(it.key, it.value) }.sortedByDescending { it.incomeMinor }
    return ResumenMes(month, incomeMinor, expenseMinor, byCategory, incomeByCategory, dailyCumulative, omitidasSinTasa)
}

/** lastMonths("2026-01", 3) → ["2026-01", "2025-12", "2025-11"] */
fun lastMonths(baseMonth: String, n: Int): List<String> {
    val base = YearMonth.parse(baseMonth)
    return List(n) { i -> base.minusMonths(i.toLong()).toString() }
}

data class EstadoPresupuesto(
    val category: Category,
    val gastoUsdMinor: Long,
    val presupuestoMinor: Long,
    val ratio: Double,
)

fun budgetStatus(
    txs: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>,
    month: String,
    rates: List<Rate>,
    activeSource: RateSource,
): List<EstadoPresupuesto> {
    val resumenUsd = monthSummary(txs, accounts, month, Currency.USD, rates, activeSource)
    return categories
        .filter { it.kind == CategoryKind.EXPENSE && (it.monthlyBudgetMinor ?: 0L) > 0L }
        .map { category ->
            val gasto = resumenUsd.byCategory.firstOrNull { it.categoryId == category.id }?.expenseMinor ?: 0L
            val presupuesto = category.monthlyBudgetMinor ?: 0L
            EstadoPresupuesto(category, gasto, presupuesto, gasto.toDouble() / presupuesto)
        }
}
