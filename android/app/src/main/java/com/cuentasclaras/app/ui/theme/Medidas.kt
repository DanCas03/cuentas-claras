package com.cuentasclaras.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Medidas comunes que no son espacios ni formas (DESIGN.md §2). */
data class Medidas(
    /** Lado o alto mínimo de un objetivo táctil (DESIGN.md §6: ≥ 48 dp). */
    val tactilMinimo: Dp,
    /** Opacidad del tinte del color de una categoría tras su emoji o su ícono (grid de Nuevo, filas de listas). */
    val alfaTinteCategoria: Float,
)

val MedidasCuentas = Medidas(
    tactilMinimo = 48.dp,
    alfaTinteCategoria = 0.18f,
)
