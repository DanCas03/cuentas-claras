package com.cuentasclaras.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Medidas comunes que no son espacios ni formas (DESIGN.md §2). */
data class Medidas(
    /** Lado o alto mínimo de un objetivo táctil (DESIGN.md §6: ≥ 48 dp). */
    val tactilMinimo: Dp,
    /** Opacidad del tinte del color de una categoría tras su emoji o su ícono (grid de Nuevo, filas de listas). */
    val alfaTinteCategoria: Float,
    /** Lado de un ícono en línea con una cifra (DESIGN.md §4: 16 dp), p. ej. `ic_entra` / `ic_sale` junto a un monto. */
    val iconoEnLinea: Dp,
    /** Opacidad de un elemento que se muestra pero no cuenta (fila duplicada en la previa del import). */
    val alfaAtenuado: Float,
    /**
     * Ancho mínimo que una `FilaConFinal` deja al texto junto al elemento final (monto, estado, acción). Si no le
     * queda, el final baja bajo el texto (DESIGN.md §4).
     */
    val anchoMinimoTexto: Dp,
)

val MedidasCuentas = Medidas(
    tactilMinimo = 48.dp,
    alfaTinteCategoria = 0.18f,
    iconoEnLinea = 16.dp,
    alfaAtenuado = 0.45f,
    anchoMinimoTexto = 120.dp,
)
