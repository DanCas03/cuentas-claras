package com.cuentasclaras.app.ui.pantallas.apariencia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.data.Apariencia
import com.cuentasclaras.app.data.AparienciaRepo
import com.cuentasclaras.app.data.ModoTema
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Modo, paletas, fuente, diseño, nombres en la barra e ícono elegidos (E20, E22, spec 2026-10-17). `null` mientras
 * DataStore no ha respondido.
 * E30: cada cambio es una escritura idempotente de una sola preferencia; sus controles no se deshabilitan con
 * `guardando`, pero un fallo avisa (helper `escribir` sin ocupación).
 */
class AparienciaViewModel(private val repo: AparienciaRepo) : ViewModel() {
    val apariencia: StateFlow<Apariencia?> =
        repo.apariencia.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cambiarModo(modo: ModoTema) = escribir(null, MENSAJE_ERROR) { repo.cambiarModo(modo) }
    /** La paleta oscura también tiñe el ícono del lanzador; el alias se aplica al salir de la app (`MainActivity.onStop`). */
    fun cambiarPaletaOscura(id: String) = escribir(null, MENSAJE_ERROR) { repo.cambiarPaletaOscura(id) }
    fun cambiarPaletaClara(id: String) = escribir(null, MENSAJE_ERROR) { repo.cambiarPaletaClara(id) }
    fun cambiarFuente(id: String) = escribir(null, MENSAJE_ERROR) { repo.cambiarFuente(id) }
    fun cambiarDiseno(id: String) = escribir(null, MENSAJE_ERROR) { repo.cambiarDiseno(id) }
    fun cambiarEtiquetasBarra(mostrar: Boolean) = escribir(null, MENSAJE_ERROR) { repo.cambiarEtiquetasBarra(mostrar) }
    fun cambiarIcono(id: String) = escribir(null, MENSAJE_ERROR) { repo.cambiarIcono(id) }

    private companion object {
        const val MENSAJE_ERROR = "No se pudo guardar la apariencia"
    }
}
