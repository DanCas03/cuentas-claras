package com.cuentasclaras.app.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.util.UUID

// REGLA (D4): ninguna propiedad de estas clases tiene un default NO nulo. Los opcionales
// llevan `= null` para que, con encodeDefaults = false, no se escriban en el JSON (igual
// que `undefined` en JSON.stringify de la web) y para que su ausencia se lea como null.

fun nuevoId(): String = UUID.randomUUID().toString()

@Serializable
enum class Currency { VES, USD }

@Serializable
enum class AccountKind {
    @SerialName("bank") BANK,
    @SerialName("wallet") WALLET,
    @SerialName("snapshot") SNAPSHOT,
    @SerialName("cash") CASH,
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val archived: Boolean,
    /** Estado del último import BNC (solo cuentas kind 'bank') para detectar huecos. */
    val bncLastImportKey: String? = null,
    val bncLastSaldoMinor: Long? = null,
    /** Vista 1 del slider de patrimonio. Default al leer: !isSavings (v2). */
    val enDisposicion: Boolean? = null,
)

@Serializable
enum class TxType {
    @SerialName("expense") EXPENSE,
    @SerialName("income") INCOME,
    @SerialName("transfer") TRANSFER,
    @SerialName("adjustment") ADJUSTMENT,
}

@Serializable
enum class TxSource {
    @SerialName("manual") MANUAL,
    @SerialName("bnc") BNC,
    @SerialName("zinli") ZINLI,
    @SerialName("adjust") ADJUST,
}

/**
 * Invariantes (idénticas a types.ts):
 * - amountMinor es entero en céntimos de la moneda de accountId.
 * - expense/income/transfer: amountMinor > 0 (el tipo da el signo).
 * - adjustment: amountMinor es el DELTA con signo (real − calculado).
 * - transfer: toAccountId obligatorio; toAmountMinor en la moneda destino; rateUsed solo si cruza moneda.
 * - date: 'yyyy-mm-dd'; time: 'HH:mm:ss' o 'HH:mm:ss.SSS' (opcional).
 * - groupId (v2): excluido de agregaciones; cuenta solo el residuo virtual del grupo.
 * - eventId + eventDate (v3): van juntos; eventDate es la fecha NOMINAL de la ocurrencia.
 */
@Serializable
data class Transaction(
    val id: String,
    val type: TxType,
    val accountId: String,
    val amountMinor: Long,
    val date: String,
    val time: String? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val source: TxSource,
    val importKey: String? = null,
    val toAccountId: String? = null,
    val toAmountMinor: Long? = null,
    val rateUsed: Double? = null,
    val groupId: String? = null,
    val eventId: String? = null,
    val eventDate: String? = null,
)

@Serializable
enum class CategoryKind {
    @SerialName("expense") EXPENSE,
    @SerialName("income") INCOME,
}

/** EXPENSE/INCOME de un movimiento como kind de categoría; null para transferencias y ajustes. */
fun TxType.comoKind(): CategoryKind? = when (this) {
    TxType.EXPENSE -> CategoryKind.EXPENSE
    TxType.INCOME -> CategoryKind.INCOME
    else -> null
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val kind: CategoryKind,
    /** Presupuesto mensual opcional, SIEMPRE en céntimos de USD. */
    val monthlyBudgetMinor: Long? = null,
)

@Serializable
enum class RateSource { BCV, BINANCE, MANUAL }

@Serializable
data class Rate(
    val date: String,
    val source: RateSource,
    /** Tasa (no es dinero: puede tener decimales). */
    val vesPerUsd: Double,
)

@Serializable
data class Rule(
    val id: String,
    /** Substring, case-insensitive. */
    val pattern: String,
    val categoryId: String,
    val autoCreated: Boolean,
)

@Serializable
data class Settings(
    /** Siempre "main". Sin default a propósito (D4): debe escribirse en el JSON. */
    val id: String,
    val viewCurrency: Currency,
    val rateSource: RateSource,
    val lastAccountId: String? = null,
    val lastRatesFetchDate: String? = null,
)

@Serializable
enum class DebtDirection {
    @SerialName("porPagar") POR_PAGAR,
    @SerialName("porCobrar") POR_COBRAR,
}

@Serializable
enum class DebtStatus {
    @SerialName("pendiente") PENDIENTE,
    @SerialName("cerrada") CERRADA,
}

/** Deuda o cuenta por cobrar (v2). Pendiente NO altera ningún saldo; el movimiento real
 *  nace solo al cerrarla. amountMinor vive en la moneda de la cuenta asignada. */
@Serializable
data class Debt(
    val id: String,
    val direction: DebtDirection,
    val concepto: String,
    val amountMinor: Long,
    val accountId: String,
    val status: DebtStatus,
    val createdIso: String,
    val settledTxId: String? = null,
    val settledIso: String? = null,
)

/** Grupo transaccional (v2). El residuo es virtual: se deriva, no se persiste. */
@Serializable
data class TxGroup(
    val id: String,
    val nota: String? = null,
    /** v3: categoría del residuo; se aplica SOLO si su kind coincide con el signo del residuo. */
    val categoryId: String? = null,
    val createdIso: String,
)

/** Reglas de recurrencia (v3). weekly/yearly derivan el día de la semana / mes+día de startDate.
 *  JSON idéntico a la web: {"kind":"once"} · {"kind":"monthly","days":[15,31]}. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface Recurrence {
    @Serializable @SerialName("once") data object Once : Recurrence
    @Serializable @SerialName("weekly") data object Weekly : Recurrence
    /** 1..31, ordenados, sin repetidos; día > días del mes → último día. */
    @Serializable @SerialName("monthly") data class Monthly(val days: List<Int>) : Recurrence
    @Serializable @SerialName("yearly") data object Yearly : Recurrence
}

/** Evento de calendario (v3). NO es dinero: sus ocurrencias se DERIVAN (Calendar.kt). */
@Serializable
data class CalEvent(
    val id: String,
    val titulo: String,
    /** Obligatoria: define pago (expense) o cobro (income) y la categoría que copia la tx asignada. */
    val categoryId: String,
    /** Cuenta esperada; define la moneda de amountMinor. */
    val accountId: String? = null,
    /** Monto esperado (REQUIERE accountId); ausente = variable. */
    val amountMinor: Long? = null,
    val startDate: String,
    /** Inclusive; ausente = sin fin. */
    val endDate: String? = null,
    val recurrence: Recurrence,
    /** Fechas nominales marcadas «omitida». */
    val omitidas: List<String>? = null,
    val createdIso: String,
)
