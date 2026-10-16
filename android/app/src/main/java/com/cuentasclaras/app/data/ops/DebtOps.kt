package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.buildSettleTx

/** Cierra la deuda creando su movimiento real, todo-o-nada e idempotente: si ya está cerrada,
 *  no hace nada (guard dentro de la transacción). */
suspend fun settleDebt(db: CuentasDb, debtId: String, dateIso: String) {
    db.withTransaction {
        val debt = db.debts().porId(debtId)?.toDomain() ?: throw IllegalArgumentException("La deuda no existe")
        if (debt.status != DebtStatus.PENDIENTE) return@withTransaction
        val tx = buildSettleTx(debt, dateIso)
        db.transactions().insertar(tx.toEntity())
        db.debts().actualizar(debt.copy(status = DebtStatus.CERRADA, settledTxId = tx.id, settledIso = dateIso).toEntity())
    }
}
