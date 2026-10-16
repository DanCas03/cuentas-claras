package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.accountBalanceMinor
import com.cuentasclaras.app.domain.buildAdjustment
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class AjustarViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Formulario(val cuentaId: String = "acc-binance", val realTxt: String = "", val guardando: Boolean = false)

    data class Estado(
        val f: Formulario = Formulario(),
        val cuentas: List<Account> = emptyList(),
        val cuenta: Account? = null,
        val actual: Long = 0,
        val real: Long? = null,
        val delta: Long? = null,
    ) {
        val esSnapshot: Boolean get() = cuenta?.kind == AccountKind.SNAPSHOT
        val puedeGuardar: Boolean get() = !f.guardando && real != null && delta != null && delta != 0L
    }

    private val form = MutableStateFlow(Formulario())
    private val ocupacion = Ocupacion({ form.value.guardando }) { g -> form.update { it.copy(guardando = g) } }

    /**
     * El formulario tal como lo edita el usuario, sin pasar por [estado]. Los `TextField` leen de aquí: [estado] se
     * deriva con `combine` + `stateIn` y llega con uno o más saltos de corrutina de retraso, y un campo que recibe un
     * valor atrasado entre dos pulsaciones pierde y reordena caracteres (F1, el mismo síntoma que D1).
     */
    val formularioActual: StateFlow<Formulario> = form.asStateFlow()
    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    // Saldos con las transacciones CRUDAS (restricción 7).
    val estado: StateFlow<Estado> = combine(form, repo.cuentas, repo.txs) { f, cuentas, txs ->
        val activas = cuentas.filter { !it.archived }
        val cuenta = activas.firstOrNull { it.id == f.cuentaId }
        val actual = cuenta?.let { accountBalanceMinor(it.id, txs) } ?: 0L
        val real = parseVenezuelanAmount(f.realTxt.removeSuffix(","))
        val delta = if (real != null && real >= 0) real - actual else null
        Estado(f, activas, cuenta, actual, real, delta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiar(cambio: (Formulario) -> Formulario) = form.update(cambio)

    /** D19: tras guardar bien, `guardando` se queda en true (la pantalla tarda unos ms en cerrarse). */
    fun guardar() {
        val e = estado.value
        if (ocupacion.activa) return
        val cuenta = e.cuenta ?: return
        val real = e.real ?: return
        if (e.delta == null || e.delta == 0L) return
        escribir(
            ocupacion,
            "No se pudo ajustar",
            reponerAlExito = false,
            alTerminar = {
                Avisos.avisar("Saldo ajustado ✓")
                _salir.trySend(Unit)
            },
        ) { repo.agregarTx(buildAdjustment(cuenta.id, e.actual, real, todayIso())) }
    }
}
