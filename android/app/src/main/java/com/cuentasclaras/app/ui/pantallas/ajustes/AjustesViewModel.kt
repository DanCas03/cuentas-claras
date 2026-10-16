package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.effectiveRate
import com.cuentasclaras.app.domain.esFechaIso
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AjustesViewModel(private val repo: CuentasRepo, private val http: OkHttpClient) : ViewModel() {
    data class Ocupado(val guardando: Boolean = false, val buscando: Boolean = false)
    data class Estado(
        val datos: Instantanea? = null,
        /** Calculada sobre el histórico COMPLETO (no sobre la tabla de 14 filas). */
        val vigente: Rate? = null,
        val recientes: List<Rate> = emptyList(),
        val ocupado: Ocupado = Ocupado(),
    )

    private val ocupado = MutableStateFlow(Ocupado())

    val estado: StateFlow<Estado> = combine(repo.instantanea, ocupado) { i, o ->
        Estado(
            datos = i,
            vigente = effectiveRate(todayIso(), i.settings.rateSource, i.tasas),
            recientes = i.tasas.sortedByDescending { it.date }.take(14),
            ocupado = o,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Estado())

    /**
     * Guard primero (E7); si ya hay un guardado en curso no hace nada. `alTerminar` se llama en el hilo
     * principal solo tras guardar con éxito (la hoja se cierra o el campo se vacía ahí, como la web).
     */
    private fun guardarCon(exito: String?, alTerminar: () -> Unit = {}, trabajo: suspend () -> Unit) {
        if (ocupado.value.guardando) return
        ocupado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            try {
                trabajo()
                alTerminar()
                if (exito != null) Avisos.avisar(exito)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo guardar")
            } finally {
                ocupado.update { it.copy(guardando = false) }
            }
        }
    }

    /** La hoja se cierra solo desde `alGuardar`, tras escribir; si falla o hay un guardado en curso queda abierta. */
    fun guardarCuenta(a: Account, alGuardar: () -> Unit) {
        if (ocupado.value.guardando) return
        if (a.name.isBlank()) { Avisos.avisar("Ponle nombre a la cuenta"); return }
        guardarCon("Cuenta guardada ✓", alGuardar) { repo.guardarCuenta(a.copy(name = a.name.trim())) }
    }

    fun guardarCategoria(c: Category, presupuestoTxt: String, alGuardar: () -> Unit) {
        if (ocupado.value.guardando) return
        if (c.name.isBlank()) { Avisos.avisar("Ponle nombre a la categoría"); return }
        val limpio = presupuestoTxt.trim().replace(',', '.')
        val presupuesto: Long? = if (limpio.isEmpty()) null else {
            val v = limpio.toDoubleOrNull()
            if (v == null || !v.isFinite() || v <= 0) { Avisos.avisar("Presupuesto inválido"); return }
            Math.round(v * 100)
        }
        guardarCon("Categoría guardada ✓", alGuardar) { repo.guardarCategoria(c.copy(name = c.name.trim(), monthlyBudgetMinor = presupuesto)) }
    }

    fun cambiarFuente(f: RateSource) {
        viewModelScope.launch {
            try {
                repo.cambiarSettings { it.copy(rateSource = f) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo cambiar la fuente")
            }
        }
    }

    fun guardarTasaManual(fecha: String, fuente: RateSource, valorTxt: String, alGuardar: () -> Unit) {
        if (ocupado.value.guardando) return
        val v = valorTxt.replace(',', '.').toDoubleOrNull()
        if (v == null || !v.isFinite() || v <= 0 || !esFechaIso(fecha)) { Avisos.avisar("Tasa o fecha inválida"); return }
        guardarCon("Tasa guardada ✓", alGuardar) { repo.guardarTasa(Rate(fecha, fuente, v)) }
    }

    fun actualizarBcvAhora() {
        if (ocupado.value.buscando) return
        ocupado.update { it.copy(buscando = true) }
        viewModelScope.launch {
            try {
                val n = repo.actualizarBcvAhora(http)
                Avisos.avisar(if (n > 0) "Tasa BCV actualizada ✓" else "No se pudo obtener el BCV ahora — usa la tasa manual")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo actualizar")
            } finally {
                ocupado.update { it.copy(buscando = false) }
            }
        }
    }

    /** Con guard `guardando` (E7); sin aviso de éxito, como antes. */
    fun borrarRegla(id: String) {
        guardarCon(exito = null) { repo.borrarRegla(id) }
    }
}
