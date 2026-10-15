package com.cuentasclaras.app.domain

fun buildManualTx(
    type: TxType,
    accountId: String,
    amountMinor: Long,
    categoryId: String,
    dateIso: String,
    note: String? = null,
): Transaction {
    require(type == TxType.EXPENSE || type == TxType.INCOME) { "Tipo inválido" }
    require(amountMinor > 0) { "Monto inválido" }
    return Transaction(
        id = nuevoId(), type = type, accountId = accountId, amountMinor = amountMinor,
        date = dateIso, categoryId = categoryId, note = note, source = TxSource.MANUAL,
    )
}

fun buildTransfer(
    fromAccount: Account,
    toAccount: Account,
    amountMinor: Long,
    dateIso: String,
    vesPerUsd: Double? = null,
    note: String? = null,
): Transaction {
    require(fromAccount.id != toAccount.id) { "Origen y destino no pueden ser la misma cuenta" }
    require(amountMinor > 0) { "Monto inválido" }
    val cruzaMoneda = fromAccount.currency != toAccount.currency
    require(!cruzaMoneda || (vesPerUsd != null && vesPerUsd > 0)) { "Falta la tasa para convertir" }
    val toAmountMinor =
        if (cruzaMoneda) convertMinor(amountMinor, fromAccount.currency, toAccount.currency, vesPerUsd!!) else amountMinor
    return Transaction(
        id = nuevoId(),
        type = TxType.TRANSFER,
        accountId = fromAccount.id,
        toAccountId = toAccount.id,
        amountMinor = amountMinor,
        toAmountMinor = toAmountMinor,
        rateUsed = if (cruzaMoneda) vesPerUsd else null,
        date = dateIso,
        note = note,
        source = TxSource.MANUAL,
    )
}

fun buildAdjustment(
    accountId: String,
    currentBalanceMinor: Long,
    realBalanceMinor: Long,
    dateIso: String,
    note: String? = null,
): Transaction {
    val delta = realBalanceMinor - currentBalanceMinor
    require(delta != 0L) { "No hay diferencia que ajustar" }
    return Transaction(
        id = nuevoId(), type = TxType.ADJUSTMENT, accountId = accountId, amountMinor = delta,
        date = dateIso, note = note ?: "Ajuste de saldo", source = TxSource.ADJUST,
    )
}
