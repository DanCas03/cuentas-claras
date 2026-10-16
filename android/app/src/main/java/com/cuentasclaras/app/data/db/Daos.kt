package com.cuentasclaras.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// @Insert sin onConflict = ABORT: un id repetido lanza y revierte la transacción en curso
// (lo exige el todo-o-nada de commitImport e importBackup).

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts") fun observar(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun todas(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun porId(id: String): AccountEntity?
    @Query("SELECT COUNT(*) FROM accounts") suspend fun contar(): Int
    @Insert suspend fun insertar(filas: List<AccountEntity>)
    @Upsert suspend fun guardar(fila: AccountEntity)
    @Query("UPDATE accounts SET bncLastImportKey = :key, bncLastSaldoMinor = :saldo WHERE id = :id")
    suspend fun parcheBnc(id: String, key: String, saldo: Long)
    @Query("DELETE FROM accounts") suspend fun borrarTodo()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions") fun observar(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions") suspend fun todas(): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun porId(id: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE id IN (:ids)") suspend fun porIds(ids: List<String>): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions") suspend fun contar(): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE importKey IN (:keys)") suspend fun contarPorImportKeys(keys: List<String>): Int
    @Query("SELECT importKey FROM transactions WHERE importKey IS NOT NULL") suspend fun importKeys(): List<String>
    @Insert suspend fun insertar(fila: TransactionEntity)
    @Insert suspend fun insertarTodas(filas: List<TransactionEntity>)
    @Update suspend fun actualizar(fila: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun borrar(id: String)
    @Query("UPDATE transactions SET groupId = :groupId WHERE id IN (:ids)") suspend fun asignarGrupo(ids: List<String>, groupId: String)
    @Query("UPDATE transactions SET groupId = NULL WHERE id IN (:ids)") suspend fun limpiarGrupo(ids: List<String>)
    @Query("SELECT id FROM transactions WHERE groupId = :groupId") suspend fun idsDelGrupo(groupId: String): List<String>
    @Query("SELECT * FROM transactions WHERE eventId = :eventId") suspend fun delEvento(eventId: String): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions WHERE eventId = :eventId AND eventDate = :date")
    suspend fun contarDeOcurrencia(eventId: String, date: String): Int
    @Query("UPDATE transactions SET eventId = :eventId, eventDate = :eventDate, categoryId = :categoryId WHERE id = :txId")
    suspend fun asignarEvento(txId: String, eventId: String, eventDate: String, categoryId: String)
    @Query("UPDATE transactions SET eventId = NULL, eventDate = NULL WHERE id = :txId") suspend fun desasignarTx(txId: String)
    @Query("UPDATE transactions SET eventId = NULL, eventDate = NULL WHERE eventId = :eventId")
    suspend fun desasignarDelEvento(eventId: String): Int
    @Query("DELETE FROM transactions") suspend fun borrarTodo()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories") fun observar(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun todas(): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun porId(id: String): CategoryEntity?
    @Query("SELECT COUNT(*) FROM categories") suspend fun contar(): Int
    @Insert suspend fun insertar(fila: CategoryEntity)
    @Insert suspend fun insertarTodas(filas: List<CategoryEntity>)
    @Upsert suspend fun guardar(fila: CategoryEntity)
    @Query("DELETE FROM categories") suspend fun borrarTodo()
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates") fun observar(): Flow<List<RateEntity>>
    @Query("SELECT * FROM rates") suspend fun todas(): List<RateEntity>
    @Query("SELECT COUNT(*) FROM rates") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<RateEntity>)
    @Upsert suspend fun guardarTodas(filas: List<RateEntity>)
    @Query("DELETE FROM rates WHERE date = :date AND source = :source") suspend fun borrar(date: String, source: String)
    @Query("DELETE FROM rates") suspend fun borrarTodo()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules") fun observar(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM rules") suspend fun todas(): List<RuleEntity>
    @Query("SELECT COUNT(*) FROM rules") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<RuleEntity>)
    @Query("DELETE FROM rules WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM rules") suspend fun borrarTodo()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 'main'") fun observar(): Flow<SettingsEntity?>
    @Query("SELECT * FROM settings WHERE id = 'main'") suspend fun principal(): SettingsEntity?
    @Query("SELECT * FROM settings") suspend fun todas(): List<SettingsEntity>
    @Query("SELECT COUNT(*) FROM settings") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<SettingsEntity>)
    @Upsert suspend fun guardar(fila: SettingsEntity)
    @Query("DELETE FROM settings") suspend fun borrarTodo()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts") fun observar(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM debts") suspend fun todas(): List<DebtEntity>
    @Query("SELECT * FROM debts WHERE id = :id") suspend fun porId(id: String): DebtEntity?
    @Query("SELECT COUNT(*) FROM debts") suspend fun contar(): Int
    @Insert suspend fun insertar(fila: DebtEntity)
    @Insert suspend fun insertarTodas(filas: List<DebtEntity>)
    @Update suspend fun actualizar(fila: DebtEntity)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM debts") suspend fun borrarTodo()
}

@Dao
interface TxGroupDao {
    @Query("SELECT * FROM txGroups") fun observar(): Flow<List<TxGroupEntity>>
    @Query("SELECT * FROM txGroups") suspend fun todas(): List<TxGroupEntity>
    @Query("SELECT * FROM txGroups WHERE id = :id") suspend fun porId(id: String): TxGroupEntity?
    @Query("SELECT COUNT(*) FROM txGroups") suspend fun contar(): Int
    @Insert suspend fun insertar(fila: TxGroupEntity)
    @Insert suspend fun insertarTodas(filas: List<TxGroupEntity>)
    @Update suspend fun actualizar(fila: TxGroupEntity)
    @Query("DELETE FROM txGroups WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM txGroups") suspend fun borrarTodo()
}

@Dao
interface CalEventDao {
    @Query("SELECT * FROM calEvents") fun observar(): Flow<List<CalEventEntity>>
    @Query("SELECT * FROM calEvents") suspend fun todas(): List<CalEventEntity>
    @Query("SELECT * FROM calEvents WHERE id = :id") suspend fun porId(id: String): CalEventEntity?
    @Query("SELECT COUNT(*) FROM calEvents") suspend fun contar(): Int
    @Insert suspend fun insertarTodas(filas: List<CalEventEntity>)
    @Upsert suspend fun guardar(fila: CalEventEntity)
    @Query("DELETE FROM calEvents WHERE id = :id") suspend fun borrar(id: String)
    @Query("DELETE FROM calEvents") suspend fun borrarTodo()
}

@Dao
interface CapturaDao {
    @Query("SELECT * FROM capturas WHERE estado = 'PENDIENTE' ORDER BY recibidaIso DESC")
    fun observarPendientes(): Flow<List<CapturaEntity>>
    @Query("SELECT * FROM capturas WHERE id = :id") suspend fun porId(id: String): CapturaEntity?
    @Query("SELECT COUNT(*) FROM capturas WHERE hash = :hash AND recibidaIso >= :desdeIso")
    suspend fun contarRecientes(hash: String, desdeIso: String): Int
    /** E19: solo cuentan las registradas cuyo movimiento sigue existiendo (un respaldo restaurado puede dejar txId huérfanos). */
    @Query(
        "SELECT referencia, montoMinor, tipo FROM capturas WHERE estado = 'REGISTRADA' AND referencia IS NOT NULL " +
            "AND montoMinor IS NOT NULL AND tipo IS NOT NULL AND txId IS NOT NULL AND EXISTS (SELECT 1 FROM transactions WHERE transactions.id = capturas.txId)",
    )
    suspend fun registradas(): List<ReferenciaCapturada>
    @Insert suspend fun insertar(fila: CapturaEntity)
    @Query("UPDATE capturas SET estado = 'REGISTRADA', txId = :txId WHERE id = :id") suspend fun marcarRegistrada(id: String, txId: String)
    @Query("UPDATE capturas SET estado = 'DESCARTADA' WHERE id = :id") suspend fun marcarDescartada(id: String)
}

/** Proyección para la deduplicación captura ↔ import (D8). */
data class ReferenciaCapturada(val referencia: String, val montoMinor: Long, val tipo: TipoCaptura)
