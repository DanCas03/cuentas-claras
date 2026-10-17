package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Fondo de la raíz de la app según `CuentasTema.diseno.fondo`. `LISO` pinta `fondo`; `AURORA` (Task 7 del plan
 * 2026-10-17) añadirá las manchas de color en movimiento tras el contenido.
 */
@Composable
fun FondoDiseno(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(CuentasTema.colores.fondo)) {
        content()
    }
}
