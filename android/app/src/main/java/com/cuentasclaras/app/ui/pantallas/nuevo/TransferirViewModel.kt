package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.buildTransfer
import com.cuentasclaras.app.domain.convertMinor
import com.cuentasclaras.app.domain.effectiveRate
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransferirViewModel(private val repo: CuentasRepo) : ViewModel() {
    data class Formulario(
        val origenId: String = "acc-efectivo",
        val destinoId: String = "acc-bnc",
        val montoTxt: String = "",
        val tasaTxt: String = "",
        val nota: String = "",
        val guardando: Boolean = false,
    )

    data class Estado(
        val f: Formulario = Formulario(),
        val cuentas: List<Account> = emptyList(),
        val origen: Account? = null,
        val destino: Account? = null,
        val monto: Long? = null,
        val cruza: Boolean = false,
        val tasaDelDia: Rate? = null,
        val tasa: Double? = null,
        val destinoMinor: Long? = null,
    ) {
        val puedeGuardar: Boolean
            get() = !f.guardando && monto != null && monto > 0 && (!cruza || (tasa != null && tasa > 0))
    }

    private val form = MutableStateFlow(Formulario())
    private val _salir = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = _salir.receiveAsFlow()

    val estado: StateFlow<Estado> = combine(form, repo.cuentas, repo.tasas, repo.settings) { f, cuentas, tasas, settings ->
        val activas = cuentas.filter { !it.archived }
        val origen = activas.firstOrNull { it.id == f.origenId }
        val destino = activas.firstOrNull { it.id == f.destinoId }
        val monto = parseVenezuelanAmount(f.montoTxt.removeSuffix(","))
        val cruza = origen != null && destino != null && origen.currency != destino.currency
        val tasaDelDia = effectiveRate(todayIso(), settings.rateSource, tasas)
        val tasa = if (f.tasaTxt.isNotBlank()) f.tasaTxt.trim().replace(',', '.').toDoubleOrNull() else tasaDelDia?.vesPerUsd
        val destinoMinor =
            if (cruza && monto != null && monto > 0 && tasa != null && tasa > 0) convertMinor(monto, origen!!.currency, destino!!.currency, tasa)
            else null
        Estado(f, activas, origen, destino, monto, cruza, tasaDelDia, tasa, destinoMinor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    fun cambiar(cambio: (Formulario) -> Formulario) = form.update(cambio)

    fun guardar() {
        val e = estado.value
        if (form.value.guardando) return
        val origen = e.origen ?: return
        val destino = e.destino ?: return
        val monto = e.monto ?: return
        if (monto <= 0) return
        form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            // Tras guardar bien, `guardando` se queda en true (la pantalla tarda unos ms en cerrarse).
            var guardado = false
            try {
                repo.agregarTx(
                    buildTransfer(
                        origen, destino, monto, todayIso(), if (e.cruza) e.tasa else null,
                        e.f.nota.trim().takeIf { it.isNotEmpty() },
                    ),
                )
                guardado = true
                Avisos.avisar("Transferencia guardada ✓")
                _salir.send(Unit)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Avisos.avisar(ex.message ?: "No se pudo guardar")
            } finally {
                if (!guardado) form.update { it.copy(guardando = false) }
            }
        }
    }
}
