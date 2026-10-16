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
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val guardar = Ocupacion({ ocupado.value.guardando }) { g -> ocupado.update { it.copy(guardando = g) } }
    private val buscar = Ocupacion({ ocupado.value.buscando }) { b -> ocupado.update { it.copy(buscando = b) } }

    /** Tras guardar con éxito: `alGuardar` (la hoja se cierra o el campo se vacía, como la web) y el aviso. */
    private fun tras(alGuardar: () -> Unit, exito: String): () -> Unit = {
        alGuardar()
        Avisos.avisar(exito)
    }

    /** La hoja se cierra solo desde `alGuardar`, tras escribir; si falla o hay un guardado en curso queda abierta. */
    fun guardarCuenta(a: Account, alGuardar: () -> Unit) {
        if (guardar.activa) return
        if (a.name.isBlank()) { Avisos.avisar("Ponle nombre a la cuenta"); return }
        escribir(guardar, "No se pudo guardar", alTerminar = tras(alGuardar, "Cuenta guardada ✓")) {
            repo.guardarCuenta(a.copy(name = a.name.trim()))
        }
    }

    fun guardarCategoria(c: Category, presupuestoTxt: String, alGuardar: () -> Unit) {
        if (guardar.activa) return
        if (c.name.isBlank()) { Avisos.avisar("Ponle nombre a la categoría"); return }
        val limpio = presupuestoTxt.trim().replace(',', '.')
        val presupuesto: Long? = if (limpio.isEmpty()) null else {
            val v = limpio.toDoubleOrNull()
            if (v == null || !v.isFinite() || v <= 0) { Avisos.avisar("Presupuesto inválido"); return }
            Math.round(v * 100)
        }
        escribir(guardar, "No se pudo guardar", alTerminar = tras(alGuardar, "Categoría guardada ✓")) {
            repo.guardarCategoria(c.copy(name = c.name.trim(), monthlyBudgetMinor = presupuesto))
        }
    }

    /** E30: escritura idempotente de una preferencia; el selector no se deshabilita, pero un fallo avisa. */
    fun cambiarFuente(f: RateSource) = escribir(null, "No se pudo cambiar la fuente") {
        repo.cambiarSettings { it.copy(rateSource = f) }
    }

    fun guardarTasaManual(fecha: String, fuente: RateSource, valorTxt: String, alGuardar: () -> Unit) {
        if (guardar.activa) return
        val v = valorTxt.replace(',', '.').toDoubleOrNull()
        if (v == null || !v.isFinite() || v <= 0 || !esFechaIso(fecha)) { Avisos.avisar("Tasa o fecha inválida"); return }
        escribir(guardar, "No se pudo guardar", alTerminar = tras(alGuardar, "Tasa guardada ✓")) {
            repo.guardarTasa(Rate(fecha, fuente, v))
        }
    }

    fun actualizarBcvAhora() = escribir(buscar, "No se pudo actualizar") {
        val n = repo.actualizarBcvAhora(http)
        Avisos.avisar(if (n > 0) "Tasa BCV actualizada ✓" else "No se pudo obtener el BCV ahora — usa la tasa manual")
    }

    /** Con guard `guardando` (E7); sin aviso de éxito, como antes. */
    fun borrarRegla(id: String) = escribir(guardar, "No se pudo guardar") { repo.borrarRegla(id) }
}
