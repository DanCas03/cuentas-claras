package com.cuentasclaras.app.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Equivalente de `avisar()` de Toast.tsx: un único canal que pinta el SnackbarHost raíz (D15). */
object Avisos {
    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val mensajes: SharedFlow<String> = _mensajes

    fun avisar(texto: String) {
        _mensajes.tryEmit(texto)
    }
}
