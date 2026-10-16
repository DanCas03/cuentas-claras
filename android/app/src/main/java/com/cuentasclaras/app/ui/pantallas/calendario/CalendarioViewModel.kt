package com.cuentasclaras.app.ui.pantallas.calendario

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.data.ops.desasignarEvento
import com.cuentasclaras.app.data.ops.eliminarEvento as eliminarEventoOp
import com.cuentasclaras.app.data.ops.guardarEvento
import com.cuentasclaras.app.data.ops.omitirOcurrencia
import com.cuentasclaras.app.data.ops.reanudarOcurrencia
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.OcurrenciaVista
import com.cuentasclaras.app.domain.RECURRENCIA_QUINCENAL
import com.cuentasclaras.app.domain.Recurrence
import com.cuentasclaras.app.domain.ahoraIso
import com.cuentasclaras.app.domain.buildEvent
import com.cuentasclaras.app.domain.esQuincenal
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.monthOf
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.nuevoId
import com.cuentasclaras.app.domain.ocurrenciasEnRango
import com.cuentasclaras.app.domain.parseDias
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.partsOf
import com.cuentasclaras.app.domain.primerDiaMes
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.domain.ultimoDiaMes
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class Repetir { ONCE, WEEKLY, MONTHLY, QUINCENAL, YEARLY }

/** Estado del editor de evento (equivale a los useState de EditorEvento.tsx). */
data class BorradorEvento(
    val original: CalEvent?,
    val id: String,
    val createdIso: String,
    val titulo: String,
    /** Solo filtra el selector de categoría: Pago (EXPENSE) o Cobro (INCOME). */
    val kind: CategoryKind,
    val categoriaId: String,
    val cuentaId: String,
    val montoTxt: String,
    val fecha: String,
    val repetir: Repetir,
    val diasTxt: String,
    val fin: String,
)

class CalendarioViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Ui(
        val mes: String,
        val diaSel: String,
        val editando: BorradorEvento? = null,
        val guardando: Boolean = false,
    )

    data class Estado(
        val ui: Ui,
        val hoy: String,
        val datos: Instantanea? = null,
        val porFecha: Map<String, List<OcurrenciaVista>> = emptyMap(),
    )

    /**
     * Día de hoy (I1): este ViewModel puede vivir de un día para otro, así que la pantalla llama a [refrescarHoy] en
     * cada ON_RESUME (vencidas, «hoy» marcado y el mes inicial no se quedan en ayer).
     */
    private val hoy = MutableStateFlow(todayIso())

    fun borradorNuevo(fecha: String) = BorradorEvento(
        original = null, id = nuevoId(), createdIso = ahoraIso(), titulo = "", kind = CategoryKind.EXPENSE,
        categoriaId = "", cuentaId = "", montoTxt = "", fecha = fecha, repetir = Repetir.ONCE,
        diasTxt = partsOf(fecha).d.toString(), fin = "",
    )

    private fun borradorDe(e: CalEvent, i: Instantanea): BorradorEvento {
        val r = e.recurrence
        return BorradorEvento(
            original = e, id = e.id, createdIso = e.createdIso, titulo = e.titulo,
            kind = kindDeEvento(e, i.categorias) ?: CategoryKind.EXPENSE,
            categoriaId = e.categoryId, cuentaId = e.accountId ?: "",
            montoTxt = e.amountMinor?.let { montoEditable(it) } ?: "",
            fecha = e.startDate,
            repetir = when {
                r is Recurrence.Monthly && esQuincenal(r) -> Repetir.QUINCENAL
                r is Recurrence.Monthly -> Repetir.MONTHLY
                r == Recurrence.Weekly -> Repetir.WEEKLY
                r == Recurrence.Yearly -> Repetir.YEARLY
                else -> Repetir.ONCE
            },
            diasTxt = if (r is Recurrence.Monthly) r.days.joinToString(", ") else partsOf(e.startDate).d.toString(),
            fin = e.endDate ?: "",
        )
    }

    private val ui: MutableStateFlow<Ui> = run {
        val pedido = args.get<String>("dia")
        val h = hoy.value
        val mes = monthOf(pedido ?: h)
        val dia = pedido ?: if (h.startsWith(mes)) h else primerDiaMes(mes)
        MutableStateFlow(Ui(mes, dia, editando = if (args.get<String>("nuevo") == "1") borradorNuevo(h) else null))
    }

    /**
     * La UI tal como la edita el usuario, sin pasar por [estado]. El editor de evento (título, monto, días) lee el
     * borrador de aquí: [estado] se calcula en `Dispatchers.Default` y llega con retraso, y un `TextField` que recibe
     * un valor atrasado entre dos pulsaciones pierde caracteres y mueve el cursor (D1).
     */
    val uiActual: StateFlow<Ui> = ui.asStateFlow()

    /** E18: las ocurrencias se derivan de las transacciones CRUDAS (`txs`), nunca de `txsEfectivas()`. */
    val estado: StateFlow<Estado> = combine(ui, repo.instantanea, hoy) { u, i, h ->
        val ocurrencias = ocurrenciasEnRango(i.eventos, i.txs, i.cuentas, primerDiaMes(u.mes), ultimoDiaMes(u.mes), h)
        Estado(u, h, i, ocurrencias.groupBy { it.date })
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(ui.value, hoy.value))

    fun cambiarMes(m: String) {
        val h = hoy.value
        ui.update { it.copy(mes = m, diaSel = if (monthOf(h) == m) h else primerDiaMes(m)) }
    }

    fun irAHoy() {
        val h = hoy.value
        ui.update { it.copy(mes = monthOf(h), diaSel = h) }
    }

    /**
     * Toma de nuevo la fecha de hoy (ON_RESUME). Si se veía el mes actual de antes, pasa al nuevo mes actual (y el día
     * elegido, si era hoy, al nuevo hoy); si Daniel miraba otro mes o había elegido otro día a propósito, se respeta.
     */
    fun refrescarHoy() {
        val nuevo = todayIso()
        val anterior = hoy.value
        if (nuevo == anterior) return
        ui.update { u ->
            when {
                u.mes != monthOf(anterior) -> u
                monthOf(nuevo) != u.mes -> u.copy(mes = monthOf(nuevo), diaSel = nuevo)
                u.diaSel == anterior -> u.copy(diaSel = nuevo)
                else -> u
            }
        }
        hoy.value = nuevo
    }
    fun seleccionarDia(iso: String) = ui.update { it.copy(diaSel = iso) }
    fun nuevoEvento() = ui.update { it.copy(editando = borradorNuevo(it.diaSel)) }
    fun editarEvento(e: CalEvent) {
        val datos = estado.value.datos ?: return
        ui.update { it.copy(editando = borradorDe(e, datos)) }
    }
    fun cerrarEditor() = ui.update { it.copy(editando = null) }
    fun cambiarBorrador(cambio: (BorradorEvento) -> BorradorEvento) = ui.update { u -> u.copy(editando = u.editando?.let(cambio)) }

    private val ocupacion = Ocupacion({ ui.value.guardando }) { g -> ui.update { it.copy(guardando = g) } }

    /** Guard primero (restricción 9, E7) vía `escribir`; el aviso de éxito va tras `alExito` (la hoja ya cerrada). */
    private fun conGuard(exito: String, fallo: String, alExito: () -> Unit = {}, trabajo: suspend () -> Unit) {
        escribir(ocupacion, fallo, alTerminar = {
            alExito()
            Avisos.avisar(exito)
        }, trabajo = trabajo)
    }

    /** La hoja se cierra solo desde `alGuardar`, tras guardar con éxito; si falla queda abierta con lo escrito. */
    fun guardarBorrador(alGuardar: () -> Unit) {
        val b = ui.value.editando ?: return
        conGuard("Evento guardado ✓", "No se pudo guardar", alGuardar) {
            val recurrence: Recurrence = when (b.repetir) {
                Repetir.QUINCENAL -> RECURRENCIA_QUINCENAL
                Repetir.MONTHLY -> Recurrence.Monthly(parseDias(b.diasTxt))
                Repetir.WEEKLY -> Recurrence.Weekly
                Repetir.YEARLY -> Recurrence.Yearly
                Repetir.ONCE -> Recurrence.Once
            }
            val amountMinor = if (b.montoTxt.isBlank()) null
            else parseVenezuelanAmount(b.montoTxt.removeSuffix(","))?.takeIf { it > 0 } ?: throw IllegalArgumentException("Monto inválido")
            val e = buildEvent(
                titulo = b.titulo, categoryId = b.categoriaId, accountId = b.cuentaId, amountMinor = amountMinor,
                startDate = b.fecha, endDate = b.fin, recurrence = recurrence,
                omitidas = b.original?.omitidas, // editar CONSERVA las omitidas
                nowIso = b.createdIso,
            )
            guardarEvento(repo.db, e.copy(id = b.id, createdIso = b.createdIso))
        }
    }

    /** La UI confirma ANTES: «¿Eliminar «titulo»? N movimientos quedarán sin evento (conservan su categoría).» */
    fun eliminarEvento(id: String, alEliminar: () -> Unit) {
        var n = 0
        escribir(ocupacion, "No se pudo eliminar", alTerminar = {
            alEliminar()
            Avisos.avisar(if (n > 0) "Evento eliminado · $n movimientos desasignados" else "Evento eliminado")
        }) { n = eliminarEventoOp(repo.db, id) }
    }

    fun omitir(o: OcurrenciaVista) =
        conGuard("Ocurrencia omitida", "No se pudo omitir") { omitirOcurrencia(repo.db, o.eventId, o.date) }

    fun reanudar(o: OcurrenciaVista) =
        conGuard("Ocurrencia reanudada", "No se pudo reanudar") { reanudarOcurrencia(repo.db, o.eventId, o.date) }

    fun desasignar(txId: String) =
        conGuard("Movimiento desasignado", "No se pudo desasignar") { desasignarEvento(repo.db, txId) }
}
