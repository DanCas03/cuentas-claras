package com.cuentasclaras.app.data.ops

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.ahoraIso
import com.cuentasclaras.app.domain.nuevoId

private suspend fun validarAgrupables(db: CuentasDb, txIds: List<String>) {
    val txs = db.transactions().porIds(txIds)
    require(txs.size == txIds.distinct().size) { "Algún movimiento ya no existe" }
    for (t in txs) {
        require(t.type == TxType.INCOME || t.type == TxType.EXPENSE) { "Solo se agrupan gastos e ingresos" }
        require(t.groupId.isNullOrEmpty()) { "Algún movimiento ya pertenece a un grupo" }
    }
}

/** Crea un grupo transaccional con ≥ 2 gastos/ingresos sueltos. Todo-o-nada.
 *  `categoryId` opcional = categoría del residuo virtual (ver categoriaDelResiduo). */
suspend fun agruparTxs(db: CuentasDb, txIds: List<String>, nota: String? = null, categoryId: String? = null): String {
    require(txIds.size >= 2) { "Selecciona al menos 2 movimientos" }
    val grupo = TxGroup(
        id = nuevoId(),
        nota = nota?.trim()?.takeIf { it.isNotEmpty() },
        categoryId = categoryId?.takeIf { it.isNotEmpty() },
        createdIso = ahoraIso(),
    )
    db.withTransaction {
        val cat = grupo.categoryId
        if (cat != null) require(db.categories().porId(cat) != null) { "La categoría no existe" }
        validarAgrupables(db, txIds)
        db.txGroups().insertar(grupo.toEntity())
        db.transactions().asignarGrupo(txIds, grupo.id)
    }
    return grupo.id
}

suspend fun anadirAGrupo(db: CuentasDb, groupId: String, txIds: List<String>) {
    require(txIds.isNotEmpty()) { "Selecciona al menos 1 movimiento" }
    db.withTransaction {
        require(db.txGroups().porId(groupId) != null) { "El grupo no existe" }
        validarAgrupables(db, txIds)
        db.transactions().asignarGrupo(txIds, groupId)
    }
}

/** Saca movimientos de sus grupos; un grupo que baja de 2 miembros se disuelve. */
suspend fun quitarDeGrupo(db: CuentasDb, txIds: List<String>) {
    db.withTransaction {
        // `groupId` vacío = sin grupo (como `esAgrupable` y el `!t.groupId` de la web).
        val gruposTocados = db.transactions().porIds(txIds)
            .mapNotNull { it.groupId }
            .filter { it.isNotEmpty() }
            .distinct()
        db.transactions().limpiarGrupo(txIds)
        for (gid in gruposTocados) {
            val restantes = db.transactions().idsDelGrupo(gid)
            if (restantes.size < 2) {
                db.transactions().limpiarGrupo(restantes)
                db.txGroups().borrar(gid)
            }
        }
    }
}

/** Revierte el grupo completo: todos los miembros vuelven a ser movimientos normales. */
suspend fun desagrupar(db: CuentasDb, groupId: String) {
    db.withTransaction {
        db.transactions().limpiarGrupo(db.transactions().idsDelGrupo(groupId))
        db.txGroups().borrar(groupId)
    }
}

/** Reemplaza nota y categoría del grupo (D9). Vacío o null → se borra el campo. */
suspend fun actualizarGrupo(db: CuentasDb, groupId: String, nota: String?, categoryId: String?) {
    db.withTransaction {
        val grupo = db.txGroups().porId(groupId) ?: throw IllegalArgumentException("El grupo no existe")
        val cat = categoryId?.takeIf { it.isNotEmpty() }
        if (cat != null) require(db.categories().porId(cat) != null) { "La categoría no existe" }
        db.txGroups().actualizar(grupo.copy(nota = nota?.trim()?.takeIf { it.isNotEmpty() }, categoryId = cat))
    }
}
