package com.cuentasclaras.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Estado «ocupado» de un ViewModel (restricción 9): cómo se lee y cómo se cambia. Cada ViewModel guarda su
 * `guardando` donde le encaja (un `MutableStateFlow<Boolean>` propio o un campo de su formulario o de su estado).
 */
class Ocupacion(private val leer: () -> Boolean, private val poner: (Boolean) -> Unit) {
    val activa: Boolean get() = leer()

    internal fun marcar(valor: Boolean) = poner(valor)
}

/** Texto de los avisos con prefijo («No se importó nada: …») cuando la excepción no trae mensaje. */
const val ERROR_DESCONOCIDO = "error desconocido"

/**
 * Helper único de escritura asíncrona de los ViewModels (restricción 9, E7, E16).
 *
 * Secuencia: guard (si `ocupacion` ya está activa, no hace nada) → `ocupacion = true` → en `viewModelScope`,
 * `trabajo()` y, si termina bien, `alTerminar()` (hilo principal) → `CancellationException` se relanza → cualquier
 * otra excepción avisa con `aviso(e)` (por defecto `e.message ?: mensajeError`) → `finally` repone la ocupación,
 * salvo tras un éxito con `reponerAlExito = false` (D19: la pantalla se cierra y un segundo toque no debe repetir
 * la escritura).
 *
 * `aviso` permite los textos con prefijo de la web («No se importó nada: …»); quien lo pase decide qué mostrar
 * cuando `e.message` es nulo (nunca «null»).
 *
 * La validación del ViewModel va ANTES de llamar aquí y DESPUÉS de su propio guard (`if (ocupacion.activa) return`),
 * para no avisar de un error de validación mientras otro guardado sigue en curso.
 *
 * `ocupacion = null` (E30): escrituras idempotentes de una sola preferencia que no deshabilitan sus controles;
 * conservan el try/catch con aviso.
 */
fun ViewModel.escribir(
    ocupacion: Ocupacion?,
    mensajeError: String,
    reponerAlExito: Boolean = true,
    alTerminar: () -> Unit = {},
    aviso: (Exception) -> String = { it.message ?: mensajeError },
    trabajo: suspend () -> Unit,
) {
    if (ocupacion?.activa == true) return
    ocupacion?.marcar(true)
    viewModelScope.launch {
        var exito = false
        try {
            trabajo()
            exito = true
            alTerminar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Avisos.avisar(aviso(e))
        } finally {
            if (!exito || reponerAlExito) ocupacion?.marcar(false)
        }
    }
}
