package com.cuentasclaras.app.domain

fun accountBalanceMinor(accountId: String, txs: List<Transaction>): Long {
    var saldo = 0L
    for (t in txs) {
        if (t.accountId == accountId) {
            saldo += when (t.type) {
                TxType.INCOME -> t.amountMinor
                TxType.EXPENSE, TxType.TRANSFER -> -t.amountMinor
                TxType.ADJUSTMENT -> t.amountMinor // delta con signo
            }
        }
        if (t.type == TxType.TRANSFER && t.toAccountId == accountId) {
            saldo += t.toAmountMinor ?: t.amountMinor
        }
    }
    return saldo
}

data class PatrimonioLinea(val account: Account, val balanceMinor: Long, val balanceUsdMinor: Long?)

data class Patrimonio(
    val lines: List<PatrimonioLinea>,
    val disposicionUsdMinor: Long?,
    val ahorroUsdMinor: Long?,
    val totalUsdMinor: Long?,
    /** v2 — vista 1 del slider: cuentas con enDisposicion (default !isSavings). */
    val aDisposicionUsdMinor: Long?,
    /** v2 — vista 2: total − deudas por pagar pendientes (convertidas a USD). */
    val netoUsdMinor: Long?,
)

/** Patrimonio consolidado en USD. vesPerUsd null → sin tasa: lo no convertible queda null.
 *  Usa transacciones CRUDAS (nunca txsEfectivas). */
fun patrimonio(
    accounts: List<Account>,
    txs: List<Transaction>,
    vesPerUsd: Double?,
    deudasPorPagarPendientes: List<Debt> = emptyList(),
): Patrimonio {
    val lines = accounts.filter { !it.archived }.map { account ->
        val balanceMinor = accountBalanceMinor(account.id, txs)
        val balanceUsdMinor = when {
            account.currency == Currency.USD -> balanceMinor
            vesPerUsd == null -> null
            else -> vesToUsdMinor(balanceMinor, vesPerUsd)
        }
        PatrimonioLinea(account, balanceMinor, balanceUsdMinor)
    }

    fun suma(grupo: List<PatrimonioLinea>): Long? =
        if (grupo.any { it.balanceUsdMinor == null }) null else grupo.sumOf { it.balanceUsdMinor ?: 0L }

    val disposicionUsdMinor = suma(lines.filter { !it.account.isSavings })
    val ahorroUsdMinor = suma(lines.filter { it.account.isSavings })
    val totalUsdMinor =
        if (disposicionUsdMinor == null || ahorroUsdMinor == null) null else disposicionUsdMinor + ahorroUsdMinor
    val aDisposicionUsdMinor = suma(lines.filter { it.account.enDisposicion ?: !it.account.isSavings })

    val monedaDe = accounts.associate { it.id to it.currency }
    var deudasUsdMinor = 0L
    var deudaSinTasa = false
    for (deuda in deudasPorPagarPendientes) {
        val moneda = monedaDe[deuda.accountId] ?: Currency.VES
        when {
            moneda == Currency.USD -> deudasUsdMinor += deuda.amountMinor
            vesPerUsd == null -> { deudaSinTasa = true; break }
            else -> deudasUsdMinor += vesToUsdMinor(deuda.amountMinor, vesPerUsd)
        }
    }
    val netoUsdMinor = if (totalUsdMinor == null || deudaSinTasa) null else totalUsdMinor - deudasUsdMinor

    return Patrimonio(lines, disposicionUsdMinor, ahorroUsdMinor, totalUsdMinor, aDisposicionUsdMinor, netoUsdMinor)
}
