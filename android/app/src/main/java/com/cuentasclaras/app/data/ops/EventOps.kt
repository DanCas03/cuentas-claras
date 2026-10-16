package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.comoKind
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.validarAsignacion
import com.cuentasclaras.app.domain.validarEvento

/** Alta o edición (upsert del objeto COMPLETO: el editor debe traer `omitidas`). Rechaza cambiar
 *  la dirección del evento si eso dejaría movimientos asignados incompatibles. */
suspend fun guardarEvento(db: CuentasDb, e: CalEvent) {
    validarEvento(e)
    db.withTransaction {
        val kind = kindDeEvento(e, db.categories().todas().map { it.toDomain() })
            ?: throw IllegalArgumentException("La categoría no existe")
        val incompatibles = db.transactions().delEvento(e.id).count { it.type.comoKind() != kind }
        require(incompatibles == 0) { "Desasigna primero los $incompatibles movimientos" }
        db.calEvents().guardar(e.toEntity())
    }
}

/** Borra el evento y desasigna sus movimientos en UNA transacción. Los movimientos NUNCA se borran.
 *  Devuelve cuántos se desasignaron. */
suspend fun eliminarEvento(db: CuentasDb, eventId: String): Int =
    db.withTransaction {
        val desasignadas = db.transactions().desasignarDelEvento(eventId)
        db.calEvents().borrar(eventId)
        desasignadas
    }

/**
 * Asigna la tx a la ocurrencia y le pone la categoría del evento, o `categoryId` si no es null (la categoría tocada
 * en Nuevo manda sobre la del evento, `NuevoPage.tsx:152`). No toca monto/fecha/cuenta/tipo.
 */
suspend fun asignarEvento(db: CuentasDb, txId: String, o: Ocurrencia, categoryId: String? = null) {
    db.withTransaction {
        val tx = db.transactions().porId(txId) ?: throw IllegalArgumentException("El movimiento ya no existe")
        require(tx.type == TxType.INCOME || tx.type == TxType.EXPENSE) { "Solo se asignan gastos e ingresos" }
        val evento = db.calEvents().porId(o.eventId)?.toDomain() ?: throw IllegalArgumentException("El evento no existe")
        require(esFechaIso(o.date)) { "Fecha inválida" }
        validarAsignacion(tx.type, evento, db.categories().todas().map { it.toDomain() })
        db.transactions().asignarEvento(txId, evento.id, o.date, categoryId ?: evento.categoryId)
    }
}

/** Quita la asignación conservando la categoría. Idempotente. */
suspend fun desasignarEvento(db: CuentasDb, txId: String) {
    db.transactions().desasignarTx(txId)
}

/** Silencia una ocurrencia. Idempotente; rechaza si ya tiene movimientos asignados. */
suspend fun omitirOcurrencia(db: CuentasDb, eventId: String, date: String) {
    db.withTransaction {
        val evento = db.calEvents().porId(eventId)?.toDomain() ?: throw IllegalArgumentException("El evento no existe")
        require(esFechaIso(date)) { "Fecha inválida" }
        require(db.transactions().contarDeOcurrencia(eventId, date) == 0) { "Ya tiene movimientos asignados" }
        val omitidas = evento.omitidas ?: emptyList()
        if (date in omitidas) return@withTransaction
        db.calEvents().guardar(evento.copy(omitidas = (omitidas + date).sorted()).toEntity())
    }
}

/** Reactiva una ocurrencia omitida. Si la lista queda vacía, se guarda null. */
suspend fun reanudarOcurrencia(db: CuentasDb, eventId: String, date: String) {
    db.withTransaction {
        val evento = db.calEvents().porId(eventId)?.toDomain() ?: throw IllegalArgumentException("El evento no existe")
        val quedan = (evento.omitidas ?: emptyList()).filter { it != date }
        db.calEvents().guardar(evento.copy(omitidas = quedan.takeIf { it.isNotEmpty() }).toEntity())
    }
}
