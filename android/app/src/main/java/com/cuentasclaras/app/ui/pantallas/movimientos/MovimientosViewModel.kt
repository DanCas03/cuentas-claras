package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.ops.actualizarGrupo
import com.cuentasclaras.app.data.ops.agruparTxs
import com.cuentasclaras.app.data.ops.anadirAGrupo
import com.cuentasclaras.app.data.ops.desagrupar
import com.cuentasclaras.app.data.ops.quitarDeGrupo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.FiltroMovs
import com.cuentasclaras.app.domain.Ocurrencia
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
        if (!gid.isNullOrEmpty()) gruposVisibles[gid] = (gruposVisibles[gid] ?: emptyList()) + t else sueltas.add(t)
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
    val miembrosDe = txs.filter { !it.groupId.isNullOrEmpty() }.groupBy { it.groupId }
    val items: List<ItemMov> = sueltas.map { ItemMov.Suelta(it) } + gruposVisibles.map { (gid, vis) ->
        val todos = miembrosDe[gid] ?: emptyList()
        ItemMov.Grupo(gid, vis, todos, residuosDeGrupo(todos, accounts), grupoDe[gid])
    }
    return items.sortedByDescending { it.clave }
}

/** Se agrupan solo gastos e ingresos sueltos (`esAgrupable` de `MovimientosPage.tsx`). */
internal fun esAgrupable(t: Transaction) = (t.type == TxType.INCOME || t.type == TxType.EXPENSE) && t.groupId.isNullOrEmpty()

class MovimientosViewModel(private val repo: CuentasRepo, args: SavedStateHandle) : ViewModel() {
    data class Ui(
        val filtro: FiltroMovs,
        val expandidos: Set<String> = emptySet(),
        val editando: Transaction? = null,
        val guardando: Boolean = false,
        /** null = sin modo selección. */
        val seleccion: Set<String>? = null,
        /** Con valor: la selección se añade a ese grupo existente. */
        val destinoGrupoId: String? = null,
        val hojaAgrupar: Boolean = false,
        val editandoGrupo: TxGroup? = null,
    )

    data class Estado(
        val ui: Ui,
        val items: List<ItemMov> = emptyList(),
        /** Meses con movimientos, desc, para el filtro. */
        val meses: List<String> = emptyList(),
        val cuentas: List<Account> = emptyList(),
        val categorias: List<Category> = emptyList(),
        val eventos: List<CalEvent> = emptyList(),
        /** Movimientos CRUDOS con evento asignado (E18): contexto del selector «Evento» del editor. */
        val txsAsignadas: List<Transaction> = emptyList(),
        /** Residuos del grupo que se está editando (con TODOS sus miembros, no solo los visibles por el filtro). */
        val residuosEditandoGrupo: List<ResiduoMoneda> = emptyList(),
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
            txsAsignadas = i.txs.filter { it.eventId != null },
            residuosEditandoGrupo = u.editandoGrupo?.let { g ->
                residuosDeGrupo(i.txs.filter { it.groupId == g.id }, i.cuentas)
            } ?: emptyList(),
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

    /**
     * Transferencias y ajustes: solo nota y fecha son editables (el monto afecta dos saldos o es un delta).
     *
     * Como la web (`db.transactions.update(id, cambios)`), se escriben SOLO los campos editados sobre la fila ACTUAL,
     * releída dentro de una transacción: así un `groupId` asignado (o quitado) mientras el editor estaba abierto no se
     * revierte con la copia tomada al abrirlo.
     *
     * Gasto e ingreso: `ocurrencia` es la asignación de evento elegida en el editor (null = «Sin evento»). Solo si
     * difiere de la que tenía el movimiento al abrirlo se asigna o desasigna, con las ops de T6 y en la misma
     * transacción que el resto de cambios (restricción 8); desasignar conserva la categoría.
     */
    fun guardarEdicion(
        original: Transaction,
        montoTxt: String,
        categoryId: String?,
        fecha: String,
        nota: String,
        ocurrencia: Ocurrencia?,
    ) {
        if (ocupacion.activa) return
        if (!esFechaIso(fecha)) { Avisos.avisar("Fecha inválida"); return }
        val notaLimpia = nota.trim().takeIf { it.isNotEmpty() }
        var monto: Long? = null
        if (original.type == TxType.EXPENSE || original.type == TxType.INCOME) {
            val m = parseVenezuelanAmount(montoTxt.removeSuffix(","))
            if (m == null || m <= 0) { Avisos.avisar("Monto inválido"); return }
            monto = m
        }
        val montoNuevo = monto
        val categoriaNueva = categoryId?.takeIf { it.isNotEmpty() }
        val eventId = original.eventId
        val eventDate = original.eventDate
        val ocurrenciaOriginal = if (eventId != null && eventDate != null) Ocurrencia(eventId, eventDate) else null
        val cambiaEvento = montoNuevo != null && ocurrencia != ocurrenciaOriginal
        escribir(
            ocupacion,
            "No se pudo actualizar",
            alTerminar = {
                Avisos.avisar("Actualizado ✓")
                ui.update { it.copy(editando = null) }
            },
        ) {
            val cambios = { actual: Transaction ->
                val cambiado = actual.copy(note = notaLimpia, date = fecha)
                if (montoNuevo != null) cambiado.copy(amountMinor = montoNuevo, categoryId = categoriaNueva) else cambiado
            }
            if (cambiaEvento) repo.actualizarTxConEvento(original.id, ocurrencia, cambios)
            else repo.actualizarTxParcial(original.id, cambios)
        }
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

    // ---- Grupos transaccionales: modo selección y acciones (`MovimientosPage.tsx` 66-135 y 240-285) ----

    /** Pulsación larga sobre una fila suelta (spec §7.2: «selección por pulsación larga»). */
    fun iniciarSeleccion(t: Transaction) =
        ui.update { it.copy(destinoGrupoId = null, seleccion = if (esAgrupable(t)) setOf(t.id) else emptySet()) }

    fun alternarSeleccion(t: Transaction) {
        if (!esAgrupable(t)) { Avisos.avisar("Solo se agrupan gastos e ingresos sueltos"); return }
        ui.update { u ->
            val s = u.seleccion ?: emptySet()
            u.copy(seleccion = if (t.id in s) s - t.id else s + t.id)
        }
    }

    fun cancelarSeleccion() = ui.update { it.copy(seleccion = null, destinoGrupoId = null, hojaAgrupar = false) }

    /** «Añadir movimientos» de un grupo expandido: entra en modo selección con ese grupo como destino. */
    fun anadirAlGrupo(gid: String) {
        ui.update { it.copy(destinoGrupoId = gid, seleccion = emptySet()) }
        Avisos.avisar("Toca los movimientos a añadir")
    }

    /** Botón de la barra: con destino añade directo; sin destino abre la hoja «Agrupar». */
    fun pedirAgrupar() {
        val u = ui.value
        if (u.destinoGrupoId != null) confirmarAgrupar("", "") else ui.update { it.copy(hojaAgrupar = true) }
    }

    /** «Cancelar» de la hoja «Agrupar»: la cierra sin salir del modo selección (como el modal de la web). */
    fun cerrarHojaAgrupar() = ui.update { it.copy(hojaAgrupar = false) }

    /**
     * Crea el grupo con la selección (≥ 2, lo valida `agruparTxs`) o la añade al grupo destino (≥ 1, `anadirAGrupo`).
     * La hoja y el modo selección se cierran SOLO tras el éxito; si falla, la hoja queda abierta con lo escrito.
     */
    fun confirmarAgrupar(nota: String, categoryId: String) {
        if (ocupacion.activa) return
        val u = ui.value
        val ids = u.seleccion?.toList() ?: return
        val destino = u.destinoGrupoId
        if (destino != null) {
            escribir(
                ocupacion,
                "No se pudo añadir al grupo",
                alTerminar = {
                    Avisos.avisar("Añadidos al grupo ✓")
                    cancelarSeleccion()
                },
            ) { anadirAGrupo(repo.db, destino, ids) }
        } else {
            escribir(
                ocupacion,
                "No se pudo agrupar",
                alTerminar = {
                    Avisos.avisar("Grupo transaccional creado ✓")
                    cancelarSeleccion()
                },
            ) { agruparTxs(repo.db, ids, nota, categoryId.takeIf { it.isNotEmpty() }) }
        }
    }

    /** Un grupo que baja de 2 miembros se disuelve (`quitarDeGrupo`). */
    fun quitarDelGrupo(txId: String) = escribir(
        ocupacion,
        "No se pudo quitar del grupo",
        alTerminar = { Avisos.avisar("Quitado del grupo ✓") },
    ) { quitarDeGrupo(repo.db, listOf(txId)) }

    /** La UI confirma ANTES: «¿Desagrupar? Todos los movimientos vuelven a contarse normal.» */
    fun desagruparGrupo(gid: String) = escribir(
        ocupacion,
        "No se pudo desagrupar",
        alTerminar = { Avisos.avisar("Grupo desagrupado ✓") },
    ) { desagrupar(repo.db, gid) }

    fun editarGrupo(g: TxGroup?) = ui.update { it.copy(editandoGrupo = g) }

    /** El editor de grupo se cierra SOLO tras guardar con éxito; si falla, queda abierto con lo escrito. */
    fun guardarGrupo(gid: String, nota: String, categoryId: String) = escribir(
        ocupacion,
        "No se pudo actualizar el grupo",
        alTerminar = {
            Avisos.avisar("Grupo actualizado ✓")
            ui.update { it.copy(editandoGrupo = null) }
        },
    ) { actualizarGrupo(repo.db, gid, nota, categoryId) }
}
