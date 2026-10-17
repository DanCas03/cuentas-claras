package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collectLatest

/**
 * Mantiene el elemento a la vista sobre el teclado mientras él (o un campo dentro de él) tiene el foco: cada vez que
 * se mueve el borde del IME, se trae a la vista con `BringIntoViewRequester`. El IME entra animado y el contenedor
 * desplazable se encoge con `imePadding()` DESPUÉS del foco; el último valor (el teclado ya abierto) termina su
 * desplazamiento y los intermedios se cancelan.
 *
 * No añade relleno: el `imePadding()` lo pone el contenedor desplazable de la pantalla (DESIGN.md §1, «Insets y
 * teclado»). Se usa en la nota y el botón de Transferir (D3) y en el «Patrón» de Importar (I1).
 */
@Composable
fun Modifier.visibleSobreElTeclado(): Modifier {
    val solicitante = remember { BringIntoViewRequester() }
    var enfocado by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val densidad = LocalDensity.current
    LaunchedEffect(enfocado) {
        if (enfocado) {
            snapshotFlow { ime.getBottom(densidad) }.collectLatest { solicitante.bringIntoView() }
        }
    }
    return this
        .bringIntoViewRequester(solicitante)
        .onFocusChanged { enfocado = it.hasFocus }
}
