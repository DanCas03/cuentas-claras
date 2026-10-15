package com.cuentasclaras.app.domain

import kotlin.math.abs

data class ResiduoMoneda(
    val currency: Currency,
    /** Firmado: + ingreso (vuelto a favor), − gasto. */
    val residuoMinor: Long,
)

/** Σ firmada de los miembros por moneda (ingresos +, gastos −). Transferencias/ajustes se ignoran. */
fun residuosDeGrupo(miembros: List<Transaction>, accounts: List<Account>): List<ResiduoMoneda> {
    val monedaDe = accounts.associate { it.id to it.currency }
    val porMoneda = LinkedHashMap<Currency, Long>()
    for (t in miembros) {
        if (t.type != TxType.INCOME && t.type != TxType.EXPENSE) continue
        val moneda = monedaDe[t.accountId] ?: Currency.USD
        val firmado = if (t.type == TxType.INCOME) t.amountMinor else -t.amountMinor
        porMoneda[moneda] = (porMoneda[moneda] ?: 0L) + firmado
    }
    return porMoneda.filter { it.value != 0L }.map { ResiduoMoneda(it.key, it.value) }
}

private fun masReciente(txs: List<Transaction>): Transaction =
    txs.reduce { a, b -> if ("${b.date} ${b.time ?: ""}" > "${a.date} ${a.time ?: ""}") b else a }

/** Categoría del residuo virtual (v3): la del grupo SOLO si su kind coincide con el signo
 *  (expense ↔ residuo < 0, income ↔ residuo > 0); si no, «Vuelto» del signo (v2). */
fun categoriaDelResiduo(grupo: TxGroup?, residuoMinor: Long, categories: List<Category>): String {
    val necesario = if (residuoMinor > 0) CategoryKind.INCOME else CategoryKind.EXPENSE
    val elegida = grupo?.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    if (elegida != null && elegida.kind == necesario) return elegida.id
    return if (residuoMinor > 0) "cat-vuelto-ingreso" else "cat-vuelto-gasto"
}

/** Sustituye los movimientos agrupados por su residuo virtual. SOLO para agregaciones;
 *  NUNCA para saldos. Los virtuales (`virtual-…`) jamás se persisten. */
fun txsEfectivas(
    txs: List<Transaction>,
    accounts: List<Account>,
    grupos: List<TxGroup> = emptyList(),
    categories: List<Category> = emptyList(),
): List<Transaction> {
    val grupoDe = grupos.associateBy { it.id }
    val monedaDe = accounts.associate { it.id to it.currency }
    val sueltas = mutableListOf<Transaction>()
    val porGrupo = LinkedHashMap<String, MutableList<Transaction>>()
    for (t in txs) {
        val gid = t.groupId
        if (!gid.isNullOrEmpty()) porGrupo.getOrPut(gid) { mutableListOf() }.add(t) else sueltas.add(t)
    }
    val virtuales = mutableListOf<Transaction>()
    for ((groupId, miembros) in porGrupo) {
        val ultimo = masReciente(miembros)
        val grupo = grupoDe[groupId]
        for ((currency, residuoMinor) in residuosDeGrupo(miembros, accounts)) {
            val deLaMoneda = miembros.filter { (monedaDe[it.accountId] ?: Currency.USD) == currency }
            virtuales.add(
                Transaction(
                    id = "virtual-$groupId-${currency.name}",
                    type = if (residuoMinor > 0) TxType.INCOME else TxType.EXPENSE,
                    accountId = masReciente(deLaMoneda).accountId,
                    amountMinor = abs(residuoMinor),
                    date = ultimo.date,
                    time = ultimo.time,
                    categoryId = categoriaDelResiduo(grupo, residuoMinor, categories),
                    note = grupo?.nota?.takeIf { it.isNotEmpty() } ?: "Vuelto de grupo transaccional",
                    source = TxSource.MANUAL,
                    groupId = groupId,
                ),
            )
        }
    }
    return sueltas + virtuales
}
