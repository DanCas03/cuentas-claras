package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Transaction

data class ParcheCuentaBnc(val id: String, val bncLastImportKey: String, val bncLastSaldoMinor: Long)

const val MENSAJE_YA_IMPORTADO = "Alguno de estos movimientos ya fue importado. Vuelve a analizar el archivo."

/** Import todo-o-nada: si algo falla, no entra nada. La puerta normal contra duplicados es el
 *  filtro de la vista previa; esto solo revalida DENTRO de la misma transacción (backstop). */
suspend fun commitImport(
    db: CuentasDb,
    txs: List<Transaction>,
    newRules: List<Rule> = emptyList(),
    accountPatch: ParcheCuentaBnc? = null,
) {
    db.withTransaction {
        if (txs.isNotEmpty()) {
            val keys = txs.mapNotNull { it.importKey }
            // Trozos de 500: límite de variables SQL en el SQLite de Android 8 (999).
            val yaExisten = keys.chunked(500).sumOf { db.transactions().contarPorImportKeys(it) }
            check(yaExisten == 0) { MENSAJE_YA_IMPORTADO }
            db.transactions().insertarTodas(txs.map { it.toEntity() })
        }
        if (newRules.isNotEmpty()) db.rules().insertarTodas(newRules.map { it.toEntity() })
        if (accountPatch != null) {
            db.accounts().parcheBnc(accountPatch.id, accountPatch.bncLastImportKey, accountPatch.bncLastSaldoMinor)
        }
    }
}
