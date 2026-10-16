package com.cuentasclaras.app.data

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.ops.asignarEvento
import com.cuentasclaras.app.data.ops.desasignarEvento
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Settings
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.rates.fetchRatesWithFallback
import com.cuentasclaras.app.rates.proveedoresPorDefecto
import com.cuentasclaras.app.rates.storeFetchedRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

/** Todas las tablas a la vez: equivalente a los useLiveQuery de una página de la web. */
data class Instantanea(
    val cuentas: List<Account>,
    val txs: List<Transaction>,
    val categorias: List<Category>,
    val tasas: List<Rate>,
    val settings: Settings,
    val deudas: List<Debt>,
    val grupos: List<TxGroup>,
    val eventos: List<CalEvent>,
    val reglas: List<Rule>,
)

/** Lecturas reactivas (Flow de Room) y escrituras simples. Las operaciones compuestas viven en
 *  data/ops y se llaman con `repo.db`. */
class CuentasRepo(val db: CuentasDb) {
    val cuentas: Flow<List<Account>> = db.accounts().observar().map { l -> l.map { it.toDomain() } }
    val txs: Flow<List<Transaction>> = db.transactions().observar().map { l -> l.map { it.toDomain() } }
    val categorias: Flow<List<Category>> = db.categories().observar().map { l -> l.map { it.toDomain() } }
    val tasas: Flow<List<Rate>> = db.rates().observar().map { l -> l.map { it.toDomain() } }
    val reglas: Flow<List<Rule>> = db.rules().observar().map { l -> l.map { it.toDomain() } }
    val settings: Flow<Settings> = db.settings().observar().map { it?.toDomain() ?: SETTINGS_SEED }
    val deudas: Flow<List<Debt>> = db.debts().observar().map { l -> l.map { it.toDomain() } }
    val grupos: Flow<List<TxGroup>> = db.txGroups().observar().map { l -> l.map { it.toDomain() } }
    val eventos: Flow<List<CalEvent>> = db.calEvents().observar().map { l -> l.map { it.toDomain() } }

    private data class Parte1(
        val cuentas: List<Account>, val txs: List<Transaction>, val categorias: List<Category>,
        val tasas: List<Rate>, val settings: Settings,
    )

    private data class Parte2(
        val deudas: List<Debt>, val grupos: List<TxGroup>, val eventos: List<CalEvent>, val reglas: List<Rule>,
    )

    val instantanea: Flow<Instantanea> = combine(
        combine(cuentas, txs, categorias, tasas, settings) { a, b, c, d, e -> Parte1(a, b, c, d, e) },
        combine(deudas, grupos, eventos, reglas) { a, b, c, d -> Parte2(a, b, c, d) },
    ) { p1, p2 ->
        Instantanea(p1.cuentas, p1.txs, p1.categorias, p1.tasas, p1.settings, p2.deudas, p2.grupos, p2.eventos, p2.reglas)
    }

    suspend fun agregarTx(tx: Transaction) = db.transactions().insertar(tx.toEntity())
    /**
     * Relee el movimiento DENTRO de una transacción y le aplica solo `cambios`: así un campo que el editor no toca
     * (p. ej. un `groupId` asignado mientras estaba abierto) no se pisa con una copia vieja.
     */
    suspend fun actualizarTxParcial(id: String, cambios: (Transaction) -> Transaction) = db.withTransaction {
        val actual = db.transactions().porId(id)?.toDomain()
            ?: throw IllegalStateException("El movimiento ya no existe")
        db.transactions().actualizar(cambios(actual).toEntity())
    }

    /**
     * Como [actualizarTxParcial], pero además cambia la asignación de evento con las ops de T6 en la MISMA transacción
     * (restricción 8): `ocurrencia` no nula → `asignarEvento` (valida la dirección y copia la categoría del evento);
     * null → `desasignarEvento` (conserva la categoría). `cambios` se aplica DESPUÉS sobre la fila releída, así la
     * categoría elegida en el editor prevalece sobre la copiada del evento. Si algo falla no se escribe nada.
     */
    suspend fun actualizarTxConEvento(id: String, ocurrencia: Ocurrencia?, cambios: (Transaction) -> Transaction) =
        db.withTransaction {
            if (ocurrencia != null) asignarEvento(db, id, ocurrencia) else desasignarEvento(db, id)
            actualizarTxParcial(id, cambios)
        }
    suspend fun borrarTx(id: String) = db.transactions().borrar(id)
    suspend fun guardarCuenta(a: Account) = db.accounts().guardar(a.toEntity())
    suspend fun guardarCategoria(c: Category) = db.categories().guardar(c.toEntity())
    suspend fun guardarTasa(r: Rate) = db.rates().guardarTodas(listOf(r.toEntity()))
    suspend fun borrarRegla(id: String) = db.rules().borrar(id)
    suspend fun cambiarSettings(cambio: (Settings) -> Settings) = saveSettings(db, cambio)

    /** «Actualizar BCV ahora» de Ajustes → Tasas. Devuelve cuántas tasas guardó (0 = sin red/proveedor). */
    suspend fun actualizarBcvAhora(http: OkHttpClient): Int =
        storeFetchedRates(db, todayIso(), fetchRatesWithFallback(proveedoresPorDefecto(http)))
}
