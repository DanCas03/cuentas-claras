package com.cuentasclaras.app.ui.pantallas.deudas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.ops.settleDebt
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.ahoraIso
import com.cuentasclaras.app.domain.buildDebt
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class DeudasViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Ui(val pestana: DebtDirection = DebtDirection.POR_PAGAR, val guardando: Boolean = false)
    data class Estado(
        val ui: Ui = Ui(),
        /** Cuentas activas (las que se pueden elegir en el editor). */
        val cuentas: List<Account> = emptyList(),
        /** Todas las cuentas, también las archivadas: una deuda vieja debe mostrar su cuenta y su moneda reales. */
        val todasLasCuentas: List<Account> = emptyList(),
        /** Pendientes de la pestaña, createdIso desc. */
        val pendientes: List<Debt> = emptyList(),
        /** Cerradas de la pestaña, settledIso desc. */
        val cerradas: List<Debt> = emptyList(),
    )

    private val ui = MutableStateFlow(Ui())
    private val ocupacion = Ocupacion({ ui.value.guardando }) { g -> ui.update { it.copy(guardando = g) } }

    val estado: StateFlow<Estado> = combine(ui, repo.cuentas, repo.deudas) { u, cuentas, deudas ->
        val delaPestana = deudas.filter { it.direction == u.pestana }
        Estado(
            ui = u,
            cuentas = cuentas.filter { !it.archived },
            todasLasCuentas = cuentas,
            pendientes = delaPestana.filter { it.status == DebtStatus.PENDIENTE }.sortedByDescending { it.createdIso },
            cerradas = delaPestana.filter { it.status == DebtStatus.CERRADA }.sortedByDescending { it.settledIso ?: "" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiarPestana(d: DebtDirection) = ui.update { it.copy(pestana = d) }

    /**
     * Escritura con el helper común (E7, E16): si ya hay una en curso no hace nada. `alTerminar` se llama en el
     * hilo principal solo tras escribir con éxito, y después se avisa `exito`; si falla, se avisa con el mensaje
     * y la hoja queda abierta.
     */
    private fun conGuard(exito: String, alTerminar: () -> Unit = {}, trabajo: suspend () -> Unit) = escribir(
        ocupacion,
        "No se pudo completar",
        alTerminar = {
            alTerminar()
            Avisos.avisar(exito)
        },
        trabajo = trabajo,
    )

    /** La UI confirma ANTES con «¿Marcar «concepto» como pagada/cobrada? Se creará el gasto/ingreso en {cuenta}.» */
    fun marcarCerrada(d: Debt) {
        val verbo = if (d.direction == DebtDirection.POR_PAGAR) "pagada" else "cobrada"
        conGuard("Deuda $verbo ✓") { settleDebt(repo.db, d.id, todayIso()) }
    }

    /** Cerrada: su movimiento NO se borra (la UI lo dice en la confirmación). */
    fun eliminar(d: Debt) = conGuard("Deuda eliminada") { repo.db.debts().borrar(d.id) }

    /** La hoja se cierra solo desde `alGuardar`, tras escribir; si falla o hay otro guardado en curso queda abierta. */
    fun guardar(
        original: Debt?,
        direction: DebtDirection,
        concepto: String,
        cuentaId: String,
        montoTxt: String,
        alGuardar: () -> Unit,
    ) {
        if (ocupacion.activa) return
        val minor = parseVenezuelanAmount(montoTxt.removeSuffix(","))
        if (minor == null || minor <= 0) { Avisos.avisar("Monto inválido"); return }
        if (concepto.isBlank()) { Avisos.avisar("Ponle un concepto a la deuda"); return }
        if (estado.value.cuentas.none { it.id == cuentaId }) { Avisos.avisar("Elige una cuenta"); return }
        conGuard("Deuda guardada ✓", alGuardar) {
            if (original == null) {
                repo.db.debts().insertar(buildDebt(direction, concepto, minor, cuentaId, ahoraIso()).toEntity())
            } else {
                repo.db.debts().actualizar(
                    original.copy(direction = direction, concepto = concepto.trim(), amountMinor = minor, accountId = cuentaId).toEntity(),
                )
            }
        }
    }
}
