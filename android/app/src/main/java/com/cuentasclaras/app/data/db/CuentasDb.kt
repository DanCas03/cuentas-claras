package com.cuentasclaras.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Versión 1 = esquema completo (9 tablas del respaldo v3 + capturas). CONGELADO tras esta tarea:
 *  cualquier cambio exige Migration + version + 1 (restricción 14). Nunca destructive fallback. */
@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, CategoryEntity::class, RateEntity::class,
        RuleEntity::class, SettingsEntity::class, DebtEntity::class, TxGroupEntity::class,
        CalEventEntity::class, CapturaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class CuentasDb : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun rates(): RateDao
    abstract fun rules(): RuleDao
    abstract fun settings(): SettingsDao
    abstract fun debts(): DebtDao
    abstract fun txGroups(): TxGroupDao
    abstract fun calEvents(): CalEventDao
    abstract fun capturas(): CapturaDao

    companion object {
        fun crear(context: Context): CuentasDb =
            Room.databaseBuilder(context.applicationContext, CuentasDb::class.java, "cuentas-claras.db").build()
    }
}
