package com.cuentasclaras.app.domain

data class FiltroMovs(
    val q: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    /** 'yyyy-mm' */
    val month: String? = null,
)

fun filterTxs(txs: List<Transaction>, f: FiltroMovs): List<Transaction> {
    val q = f.q?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    return txs
        .filter { f.accountId.isNullOrEmpty() || it.accountId == f.accountId || it.toAccountId == f.accountId }
        .filter { f.categoryId.isNullOrEmpty() || it.categoryId == f.categoryId }
        .filter { f.month.isNullOrEmpty() || it.date.startsWith(f.month) }
        .filter { q == null || (it.note ?: "").lowercase().contains(q) }
        .sortedByDescending { it.date + (it.time ?: "") }
}
