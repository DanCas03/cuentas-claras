package com.cuentasclaras.app.domain

fun buildDebt(
    direction: DebtDirection,
    concepto: String,
    amountMinor: Long,
    accountId: String,
    nowIso: String,
): Debt {
    require(amountMinor > 0) { "Monto inválido" }
    require(concepto.isNotBlank()) { "Ponle un concepto a la deuda" }
    return Debt(nuevoId(), direction, concepto.trim(), amountMinor, accountId, DebtStatus.PENDIENTE, nowIso)
}

/** Movimiento real de cierre: gasto (por pagar) o ingreso (por cobrar), en la moneda de la
 *  cuenta asignada, SIN conversión (decisión 2 del spec v2). */
fun buildSettleTx(debt: Debt, dateIso: String): Transaction {
    require(debt.status == DebtStatus.PENDIENTE) { "La deuda ya está cerrada" }
    val porPagar = debt.direction == DebtDirection.POR_PAGAR
    return Transaction(
        id = nuevoId(),
        type = if (porPagar) TxType.EXPENSE else TxType.INCOME,
        accountId = debt.accountId,
        amountMinor = debt.amountMinor,
        date = dateIso,
        categoryId = if (porPagar) "cat-deudas" else "cat-transferencia-recibida",
        note = "${if (porPagar) "Deuda pagada" else "Deuda cobrada"}: ${debt.concepto}",
        source = TxSource.MANUAL,
    )
}

data class TotalesDeudas(val debesUsdMinor: Long?, val teDebenUsdMinor: Long?)

/** Totales de pendientes en USD SOLO para mostrar. Deuda en Bs sin tasa → su total queda null. */
fun debtTotals(deudasPendientes: List<Debt>, accounts: List<Account>, vesPerUsd: Double?): TotalesDeudas {
    val monedaDe = accounts.associate { it.id to it.currency }
    fun sumar(deudas: List<Debt>): Long? {
        var total = 0L
        for (deuda in deudas) {
            val moneda = monedaDe[deuda.accountId] ?: Currency.USD
            total += when {
                moneda == Currency.USD -> deuda.amountMinor
                vesPerUsd == null -> return null
                else -> vesToUsdMinor(deuda.amountMinor, vesPerUsd)
            }
        }
        return total
    }
    return TotalesDeudas(
        debesUsdMinor = sumar(deudasPendientes.filter { it.direction == DebtDirection.POR_PAGAR }),
        teDebenUsdMinor = sumar(deudasPendientes.filter { it.direction == DebtDirection.POR_COBRAR }),
    )
}
