package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.Apariencia
import com.cuentasclaras.app.data.AparienciaRepo
import com.cuentasclaras.app.data.ModoTema
import com.cuentasclaras.app.ui.Avisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Modo y paletas elegidos (E20, E22). `null` mientras DataStore no ha respondido. */
class AparienciaViewModel(private val repo: AparienciaRepo) : ViewModel() {
    val apariencia: StateFlow<Apariencia?> =
        repo.apariencia.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun escribir(trabajo: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                trabajo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar(e.message ?: "No se pudo guardar la apariencia")
            }
        }
    }

    fun cambiarModo(modo: ModoTema) = escribir { repo.cambiarModo(modo) }
    fun cambiarPaletaOscura(id: String) = escribir { repo.cambiarPaletaOscura(id) }
    fun cambiarPaletaClara(id: String) = escribir { repo.cambiarPaletaClara(id) }
}
