package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.FiltroMovs
import com.cuentasclaras.app.domain.ResiduoMoneda
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.filterTxs
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.residuosDeGrupo
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Una fila de la lista: un movimiento suelto o un grupo transaccional (con el residuo como valor visual). */
sealed interface ItemMov {
    val clave: String

    data class Suelta(val t: Transaction) : ItemMov {
        override val clave: String get() = "${t.date} ${t.time ?: ""}"
    }

    /** `visibles`: miembros que pasan el filtro; `todos`: todos los miembros (el residuo se calcula con todos). */
    data class Grupo(
        val gid: String,
        val visibles: List<Transaction>,
        val todos: List<Transaction>,
        val residuos: List<ResiduoMoneda>,
        val grupo: TxGroup?,
    ) : ItemMov {
        override val clave: String get() = visibles.maxOf { "${it.date} ${it.time ?: ""}" }
    }
}

/**
 * Modelo de vista «sueltas + grupos», de `MovimientosPage.tsx` (líneas 136-176). Con filtro de categoría, un grupo
 * cuya categoría del residuo es la filtrada se muestra con sus miembros filtrados por texto/cuenta/mes aunque
 * ninguno lleve esa categoría (drill-down desde «Este mes»). Restricción 7: la lista usa las txs CRUDAS.
 */
fun construirItems(txs: List<Transaction>, grupos: List<TxGroup>, accounts: List<Account>, filtro: FiltroMovs): List<ItemMov> {
    val visibles = filterTxs(txs, filtro)
    val gruposVisibles = LinkedHashMap<String, List<Transaction>>()
    val sueltas = mutableListOf<Transaction>()
    for (t in visibles) {
        val gid = t.groupId
        if (gid != null) gruposVisibles[gid] = (gruposVisibles[gid] ?: emptyList()) + t else sueltas.add(t)
    }
    if (!filtro.categoryId.isNullOrEmpty()) {
        val sinFiltroDeCategoria = filterTxs(txs, filtro.copy(categoryId = null))
        for (g in grupos) {
            if (g.categoryId != filtro.categoryId || g.id in gruposVisibles) continue
            val miembros = sinFiltroDeCategoria.filter { it.groupId == g.id }
            if (miembros.isNotEmpty()) gruposVisibles[g.id] = miembros
        }
    }
    val grupoDe = grupos.associateBy { it.id }
    val miembrosDe = txs.filter { it.groupId != null }.groupBy { it.groupId }
    val items: List<ItemMov> = sueltas.map { ItemMov.Suelta(it) } + gruposVisibles.map { (gid, vis) ->
        val todos = miembrosDe[gid] ?: emptyList()
        ItemMov.Grupo(gid, vis, todos, residuosDeGrupo(todos, accounts), grupoDe[gid])
    }
    return items.sortedByDescending { it.clave }
}

class MovimientosViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Ui(
        val filtro: FiltroMovs,
        val expandidos: Set<String> = emptySet(),
        val editando: Transaction? = null,
        val guardando: Boolean = false,
    )

    data class Estado(
        val ui: Ui,
        val items: List<ItemMov> = emptyList(),
        /** Meses con movimientos, desc, para el filtro. */
        val meses: List<String> = emptyList(),
        val cuentas: List<Account> = emptyList(),
        val categorias: List<Category> = emptyList(),
        val eventos: List<CalEvent> = emptyList(),
        /** `true` hasta que llega la primera lectura de la base (evita parpadear el estado vacío). */
        val cargando: Boolean = true,
    )

    private val ui = MutableStateFlow(
        Ui(
            FiltroMovs(
                categoryId = args.get<String>("categoria"),
                month = args.get<String>("mes"),
                accountId = args.get<String>("cuenta"),
            ),
        ),
    )

    private val ocupacion = Ocupacion({ ui.value.guardando }) { g -> ui.update { it.copy(guardando = g) } }

    val estado: StateFlow<Estado> = combine(ui, repo.instantanea) { u, i ->
        Estado(
            ui = u,
            items = construirItems(i.txs, i.grupos, i.cuentas, u.filtro),
            meses = i.txs.map { it.date.take(7) }.distinct().sortedDescending(),
            cuentas = i.cuentas,
            categorias = i.categorias,
            eventos = i.eventos,
            cargando = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado(ui.value))

    fun cambiarFiltro(f: FiltroMovs) = ui.update { it.copy(filtro = f) }

    /** Cambia solo un campo del filtro sin depender de un estado de UI posiblemente atrasado. */
    fun ajustarFiltro(cambio: (FiltroMovs) -> FiltroMovs) = ui.update { it.copy(filtro = cambio(it.filtro)) }

    fun alternarGrupo(gid: String) =
        ui.update { it.copy(expandidos = if (gid in it.expandidos) it.expandidos - gid else it.expandidos + gid) }

    fun editar(t: Transaction) = ui.update { it.copy(editando = t) }
    fun cerrarEditor() = ui.update { it.copy(editando = null) }

    /** Transferencias y ajustes: solo nota y fecha son editables (el monto afecta dos saldos o es un delta). */
    fun guardarEdicion(original: Transaction, montoTxt: String, categoryId: String?, fecha: String, nota: String) {
        if (ocupacion.activa) return
        if (!esFechaIso(fecha)) { Avisos.avisar("Fecha inválida"); return }
        var cambiado = original.copy(note = nota.trim().takeIf { it.isNotEmpty() }, date = fecha)
        if (original.type == TxType.EXPENSE || original.type == TxType.INCOME) {
            val m = parseVenezuelanAmount(montoTxt.removeSuffix(","))
            if (m == null || m <= 0) { Avisos.avisar("Monto inválido"); return }
            cambiado = cambiado.copy(amountMinor = m, categoryId = categoryId?.takeIf { it.isNotEmpty() })
        }
        escribir(
            ocupacion,
            "No se pudo actualizar",
            alTerminar = {
                Avisos.avisar("Actualizado ✓")
                ui.update { it.copy(editando = null) }
            },
        ) { repo.actualizarTx(cambiado) }
    }

    /** La UI pide confirmación ANTES de llamar aquí. */
    fun eliminar(t: Transaction) = escribir(
        ocupacion,
        "No se pudo eliminar",
        alTerminar = {
            Avisos.avisar("Eliminado")
            ui.update { it.copy(editando = null) }
        },
    ) { repo.borrarTx(t.id) }
}
