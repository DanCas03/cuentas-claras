package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.AparienciaRepo
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.Instantanea
import com.cuentasclaras.app.data.ModoTema
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.EstadoPresupuesto
import com.cuentasclaras.app.domain.Patrimonio
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.ResumenMes
import com.cuentasclaras.app.domain.TotalesDeudas
import com.cuentasclaras.app.domain.budgetStatus
import com.cuentasclaras.app.domain.debtTotals
import com.cuentasclaras.app.domain.effectiveRate
import com.cuentasclaras.app.domain.lastMonths
import com.cuentasclaras.app.domain.monthOf
import com.cuentasclaras.app.domain.monthSummary
import com.cuentasclaras.app.domain.patrimonio
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.domain.txsEfectivas
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InicioViewModel(
    private val repo: CuentasRepo,
    private val apariencia: AparienciaRepo,
) : ViewModel() {
    data class Estado(
        val cargado: Boolean = false,
        val hoy: String,
        val viewCurrency: Currency = Currency.USD,
        val tasa: Rate? = null,
        val patrimonio: Patrimonio? = null,
        val cuentas: List<Account> = emptyList(),
        val categorias: List<Category> = emptyList(),
        val totalesDeudas: TotalesDeudas = TotalesDeudas(0, 0),
        /** Las 3 pendientes más recientes (createdIso desc). */
        val deudasRecientes: List<Debt> = emptyList(),
        /** Mes que gobierna Este mes, Comparativa y Presupuestos; acotado a [primerMes, mesActual]. */
        val mes: String,
        val mesActual: String,
        val primerMes: String,
        val resumen: ResumenMes? = null,
        val comparativa: List<ResumenMes> = emptyList(),
        val presupuestos: List<EstadoPresupuesto> = emptyList(),
    )

    private val hoy = todayIso()
    private val mesVisto = MutableStateFlow(monthOf(hoy))

    val estado: StateFlow<Estado> = combine(repo.instantanea, mesVisto) { i, visto -> calcular(i, visto) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            Estado(hoy = hoy, mes = monthOf(hoy), mesActual = monthOf(hoy), primerMes = monthOf(hoy)),
        )

    private fun calcular(i: Instantanea, visto: String): Estado {
        val s = i.settings
        val tasa = effectiveRate(hoy, s.rateSource, i.tasas)
        val pendientes = i.deudas.filter { it.status == DebtStatus.PENDIENTE }
        val p = patrimonio(i.cuentas, i.txs, tasa?.vesPerUsd, pendientes.filter { it.direction == DebtDirection.POR_PAGAR })
        val mesActual = monthOf(hoy)
        val primerMes = i.txs.minOfOrNull { it.date }?.let { monthOf(it) } ?: mesActual
        val mes = when {
            visto > mesActual -> mesActual
            visto < primerMes -> primerMes
            else -> visto
        }
        // txsEfectivas SOLO para agregaciones; el patrimonio de arriba usa las crudas (restricción 7).
        val efectivas = txsEfectivas(i.txs, i.cuentas, i.grupos, i.categorias)
        val resumen = monthSummary(efectivas, i.cuentas, mes, s.viewCurrency, i.tasas, s.rateSource)
        val comparativa = lastMonths(mes, 4)
            .map { monthSummary(efectivas, i.cuentas, it, s.viewCurrency, i.tasas, s.rateSource) }
            .reversed()
        val presupuestos = budgetStatus(efectivas, i.cuentas, i.categorias, mes, i.tasas, s.rateSource)
        return Estado(
            cargado = true, hoy = hoy, viewCurrency = s.viewCurrency, tasa = tasa, patrimonio = p,
            cuentas = i.cuentas, categorias = i.categorias,
            totalesDeudas = debtTotals(pendientes, i.cuentas, tasa?.vesPerUsd),
            deudasRecientes = pendientes.sortedByDescending { it.createdIso }.take(3),
            mes = mes, mesActual = mesActual, primerMes = primerMes,
            resumen = resumen, comparativa = comparativa, presupuestos = presupuestos,
        )
    }

    fun irAMes(m: String) {
        mesVisto.value = m
    }

    fun alternarMoneda() {
        viewModelScope.launch {
            try {
                repo.cambiarSettings { it.copy(viewCurrency = if (it.viewCurrency == Currency.USD) Currency.VES else Currency.USD) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo cambiar la moneda")
            }
        }
    }

    /** E21: fija el modo manual OPUESTO al que se ve ahora (`oscuroAhora` = `CuentasTema.oscuro`). */
    fun alternarModo(oscuroAhora: Boolean) {
        viewModelScope.launch {
            try {
                apariencia.cambiarModo(if (oscuroAhora) ModoTema.CLARO else ModoTema.OSCURO)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo cambiar el modo")
            }
        }
    }
}
