package com.cuentasclaras.app.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.filter

/**
 * Suelta el foco de la pantalla cuando su ventana lo recupera tras cerrarse una hoja (M3).
 *
 * `HojaModal` es otra ventana. Al cerrarse, suelta el foco de la pantalla de debajo en el acto y ARMA esta guardia.
 * La guardia vuelve a soltarlo, y cierra el teclado, la primera vez que la ventana de la actividad vuelve a tener el
 * foco de ventana. Así ningún campo de la pantalla queda enfocado ni abre el teclado, tanto si el foco le llegó con la
 * hoja abierta como si le llega al devolverle Android el foco a la ventana. Es de un solo uso: se desarma al
 * dispararse. Entre el cierre de la hoja y la vuelta del foco de ventana el usuario no puede haber tocado un campo
 * (la hoja ocupa la pantalla hasta que se va), así que nunca le quita un foco que él pidió.
 */
@Stable
class GuardiaFoco internal constructor() {
    internal var armada by mutableStateOf(false)

    /** Lo llama la hoja al salir de la composición. */
    internal fun armar() {
        armada = true
    }
}

/** Guardia de la raíz; null fuera de `RaizApp` (vistas previas): las hojas no arman nada. */
val LocalGuardiaFoco = staticCompositionLocalOf<GuardiaFoco?> { null }

/** Crea la guardia de la raíz y su único efecto. Va en la composición de la actividad, fuera de toda hoja. */
@Composable
fun rememberGuardiaFoco(): GuardiaFoco {
    val guardia = remember { GuardiaFoco() }
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    val ventana = LocalWindowInfo.current
    LaunchedEffect(guardia, foco, teclado, ventana) {
        snapshotFlow { guardia.armada && ventana.isWindowFocused }
            .filter { it }
            .collect {
                guardia.armada = false
                foco.clearFocus(force = true)
                teclado?.hide()
            }
    }
    return guardia
}
