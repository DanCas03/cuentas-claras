package com.cuentasclaras.app.backup

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.JsonCuentas
import com.cuentasclaras.app.domain.JsonRespaldo
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Settings
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.ahoraIso
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Mismo formato que la PWA (schemaVersion 3, 9 tablas, camelCase). La tabla `capturas` NO va aquí. */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val rules: List<Rule>,
    val rates: List<Rate>,
    val settings: List<Settings>,
    val debts: List<Debt>,
    val txGroups: List<TxGroup>,
    val calEvents: List<CalEvent>,
)

sealed interface ValidacionRespaldo {
    data class Ok(val backup: BackupFile) : ValidacionRespaldo
    data class Error(val mensaje: String) : ValidacionRespaldo
}

private val TABLAS_V2 = listOf("accounts", "transactions", "categories", "rules", "rates", "settings", "debts", "txGroups")
private val TABLAS = TABLAS_V2 + "calEvents"
private const val NO_ES_RESPALDO = "El archivo no es un respaldo de Cuentas Claras"
private const val JSON_INVALIDO = "El archivo no es un JSON válido"

private fun faltaTabla(b: JsonObject, tablas: List<String>): String? = tablas.firstOrNull { b[it] !is JsonArray }

private val SERIALIZADORES_TABLAS: Map<String, KSerializer<out List<Any>>> = mapOf(
    "accounts" to ListSerializer(Account.serializer()),
    "transactions" to ListSerializer(Transaction.serializer()),
    "categories" to ListSerializer(Category.serializer()),
    "rules" to ListSerializer(Rule.serializer()),
    "rates" to ListSerializer(Rate.serializer()),
    "settings" to ListSerializer(Settings.serializer()),
    "debts" to ListSerializer(Debt.serializer()),
    "txGroups" to ListSerializer(TxGroup.serializer()),
    "calEvents" to ListSerializer(CalEvent.serializer()),
)

/** Primera tabla cuyas filas no se pueden leer (null si el problema está fuera de las tablas). */
private fun tablaIlegible(obj: JsonObject): String? = SERIALIZADORES_TABLAS.entries.firstOrNull { (tabla, serializador) ->
    try {
        JsonCuentas.decodeFromJsonElement(serializador, obj.getValue(tabla))
        false
    } catch (e: IllegalArgumentException) {
        true
    }
}?.key

/** El detalle técnico de kotlinx (en inglés) no se muestra: solo qué tabla falla. */
private fun decodificar(obj: JsonObject): ValidacionRespaldo = try {
    ValidacionRespaldo.Ok(JsonCuentas.decodeFromJsonElement(BackupFile.serializer(), obj))
} catch (e: IllegalArgumentException) {
    // SerializationException es subclase de IllegalArgumentException.
    val tabla = tablaIlegible(obj)
    ValidacionRespaldo.Error(
        if (tabla != null) "El respaldo tiene datos ilegibles en la tabla \"$tabla\"" else "El respaldo tiene datos ilegibles",
    )
}

/** Como `String(version)` de la web: la primitiva sin comillas; ausente → «undefined». */
private fun textoDeVersion(crudo: JsonElement?): String =
    if (crudo is JsonPrimitive) crudo.content else crudo?.toString() ?: "undefined"

/** v3 exige las 9 tablas. v2 (8 tablas) se acepta normalizado con calEvents vacío.
 *  v1 sigue rechazado con el mismo mensaje que la web. */
fun validateBackup(data: JsonElement?): ValidacionRespaldo {
    val b = data as? JsonObject ?: return ValidacionRespaldo.Error(NO_ES_RESPALDO)
    val crudo = b["schemaVersion"]
    val version = (crudo as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    return when (version) {
        1 -> ValidacionRespaldo.Error("Este respaldo es de la versión 1 y ya no es compatible: re-ingresa o re-importa tus datos")
        2 -> faltaTabla(b, TABLAS_V2)?.let { ValidacionRespaldo.Error("Al respaldo le falta la tabla \"$it\"") }
            ?: decodificar(JsonObject(b + mapOf("schemaVersion" to JsonPrimitive(3), "calEvents" to JsonArray(emptyList()))))
        3 -> faltaTabla(b, TABLAS)?.let { ValidacionRespaldo.Error("Al respaldo le falta la tabla \"$it\"") }
            ?: decodificar(b)
        else -> ValidacionRespaldo.Error("Versión de respaldo no soportada: ${textoDeVersion(crudo)}")
    }
}

/** Texto del archivo → validación. JSON sintácticamente roto → «no es un JSON válido» (como la web);
 *  JSON válido que no es un respaldo → «no es un respaldo de Cuentas Claras». */
fun leerRespaldo(texto: String): ValidacionRespaldo {
    val elemento = try {
        JsonCuentas.parseToJsonElement(texto.removePrefix("\uFEFF"))
    } catch (e: SerializationException) {
        return ValidacionRespaldo.Error(JSON_INVALIDO)
    }
    return validateBackup(elemento)
}

suspend fun exportBackup(db: CuentasDb, exportedAt: String = ahoraIso()): BackupFile =
    db.withTransaction {
        BackupFile(
            schemaVersion = 3,
            exportedAt = exportedAt,
            accounts = db.accounts().todas().map { it.toDomain() },
            transactions = db.transactions().todas().map { it.toDomain() },
            categories = db.categories().todas().map { it.toDomain() },
            rules = db.rules().todas().map { it.toDomain() },
            rates = db.rates().todas().map { it.toDomain() },
            settings = db.settings().todas().map { it.toDomain() },
            debts = db.debts().todas().map { it.toDomain() },
            txGroups = db.txGroups().todas().map { it.toDomain() },
            calEvents = db.calEvents().todas().map { it.toDomain() },
        )
    }

fun respaldoAJson(b: BackupFile): String = JsonRespaldo.encodeToString(BackupFile.serializer(), b)

/** Importar reemplaza TODO el contenido de las 9 tablas en una sola transacción (todo-o-nada).
 *  La tabla `capturas` no se toca. */
suspend fun importBackup(db: CuentasDb, b: BackupFile) {
    db.withTransaction {
        db.accounts().borrarTodo()
        db.transactions().borrarTodo()
        db.categories().borrarTodo()
        db.rules().borrarTodo()
        db.rates().borrarTodo()
        db.settings().borrarTodo()
        db.debts().borrarTodo()
        db.txGroups().borrarTodo()
        db.calEvents().borrarTodo()
        db.accounts().insertar(b.accounts.map { it.toEntity() })
        db.transactions().insertarTodas(b.transactions.map { it.toEntity() })
        db.categories().insertarTodas(b.categories.map { it.toEntity() })
        db.rules().insertarTodas(b.rules.map { it.toEntity() })
        db.rates().insertarTodas(b.rates.map { it.toEntity() })
        db.settings().insertarTodas(b.settings.map { it.toEntity() })
        db.debts().insertarTodas(b.debts.map { it.toEntity() })
        db.txGroups().insertarTodas(b.txGroups.map { it.toEntity() })
        db.calEvents().insertarTodas(b.calEvents.map { it.toEntity() })
    }
}

fun backupFilename(dateIso: String): String = "cuentas-claras-respaldo-$dateIso.json"

data class Conteos(
    val accounts: Int, val transactions: Int, val categories: Int, val rules: Int, val rates: Int,
    val settings: Int, val debts: Int, val txGroups: Int, val calEvents: Int,
)

suspend fun contarTablas(db: CuentasDb): Conteos = Conteos(
    db.accounts().contar(), db.transactions().contar(), db.categories().contar(), db.rules().contar(),
    db.rates().contar(), db.settings().contar(), db.debts().contar(), db.txGroups().contar(), db.calEvents().contar(),
)
