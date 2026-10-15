package com.cuentasclaras.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.Recurrence
import com.cuentasclaras.app.domain.TxSource
import com.cuentasclaras.app.domain.TxType

// Mismas 9 tablas y campos que src/db/db.ts (v3) + `capturas` (nativa, fuera del respaldo).
// Los enums se guardan por `name` (conversor por defecto de Room); el JSON del respaldo usa
// los literales web vía @SerialName en los tipos de dominio (D2).

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val archived: Boolean,
    val bncLastImportKey: String?,
    val bncLastSaldoMinor: Long?,
    val enDisposicion: Boolean?,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("date"), Index("accountId"), Index("categoryId"),
        Index("importKey"), Index("groupId"), Index("eventId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: TxType,
    val accountId: String,
    val amountMinor: Long,
    val date: String,
    val time: String?,
    val categoryId: String?,
    val note: String?,
    val source: TxSource,
    val importKey: String?,
    val toAccountId: String?,
    val toAmountMinor: Long?,
    val rateUsed: Double?,
    val groupId: String?,
    val eventId: String?,
    val eventDate: String?,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val kind: CategoryKind,
    val monthlyBudgetMinor: Long?,
)

@Entity(tableName = "rates", primaryKeys = ["date", "source"], indices = [Index("date")])
data class RateEntity(
    val date: String,
    val source: RateSource,
    val vesPerUsd: Double,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val categoryId: String,
    val autoCreated: Boolean,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String,
    val viewCurrency: Currency,
    val rateSource: RateSource,
    val lastAccountId: String?,
    val lastRatesFetchDate: String?,
)

@Entity(tableName = "debts", indices = [Index("status")])
data class DebtEntity(
    @PrimaryKey val id: String,
    val direction: DebtDirection,
    val concepto: String,
    val amountMinor: Long,
    val accountId: String,
    val status: DebtStatus,
    val createdIso: String,
    val settledTxId: String?,
    val settledIso: String?,
)

@Entity(tableName = "txGroups")
data class TxGroupEntity(
    @PrimaryKey val id: String,
    val nota: String?,
    val categoryId: String?,
    val createdIso: String,
)

@Entity(tableName = "calEvents")
data class CalEventEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val categoryId: String,
    val accountId: String?,
    val amountMinor: Long?,
    val startDate: String,
    val endDate: String?,
    /** JSON {"kind":…} vía Convertidores (D3). */
    val recurrence: Recurrence,
    /** JSON array de fechas vía Convertidores; null = sin omitidas. */
    val omitidas: List<String>?,
    val createdIso: String,
)

enum class EstadoCaptura { PENDIENTE, REGISTRADA, DESCARTADA }

enum class TipoCaptura { INGRESO, GASTO }

/** Notificación del BNC capturada (spec §7.3). Se guarda ANTES de avisar al usuario.
 *  NO forma parte del respaldo v3. */
@Entity(tableName = "capturas", indices = [Index("estado"), Index("referencia"), Index("hash")])
data class CapturaEntity(
    @PrimaryKey val id: String,
    /** Instante UTC ISO con milisegundos (ahoraIso()). */
    val recibidaIso: String,
    val textoCrudo: String,
    val reconocida: Boolean,
    val tipo: TipoCaptura?,
    val montoMinor: Long?,
    val referencia: String?,
    val nota: String?,
    val estado: EstadoCaptura,
    /** sha256 hex del texto normalizado: deduplica reemisiones en 24 h. */
    val hash: String,
    /** Movimiento creado al registrarla. */
    val txId: String?,
)
