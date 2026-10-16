package com.cuentasclaras.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Formas de la app (DESIGN.md §2): tarjeta 20 dp, control 12 dp, píldora 50 %. */
data class Formas(
    val tarjeta: Shape,
    val control: Shape,
    val pildora: Shape,
)

/** Ritmo de espacios en múltiplos de 4 dp (densidad cómoda de la dirección A). */
data class Espacios(
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
)

/** Radio exterior de la tarjeta. */
internal val RADIO_TARJETA: Dp = 20.dp

/** Grosor del bisel de la tarjeta (doble bisel: carcasa exterior + núcleo interior concéntrico). */
internal val BISEL_TARJETA: Dp = 4.dp

/** Radio de los controles (campos, teclas, banners, celdas de categoría). */
internal val RADIO_CONTROL: Dp = 12.dp

/** Radio superior de la hoja modal. */
internal val RADIO_HOJA: Dp = 28.dp

val FormasCuentas = Formas(
    tarjeta = RoundedCornerShape(RADIO_TARJETA),
    control = RoundedCornerShape(RADIO_CONTROL),
    pildora = RoundedCornerShape(percent = 50),
)

val EspaciosCuentas = Espacios(
    xs = 4.dp,
    s = 8.dp,
    m = 16.dp,
    l = 24.dp,
    xl = 32.dp,
)

/** Formas de Material 3 derivadas de las propias (menús, diálogos, campos). */
internal fun formasMaterial(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(RADIO_CONTROL),
    small = RoundedCornerShape(RADIO_CONTROL),
    medium = RoundedCornerShape(RADIO_CONTROL),
    large = RoundedCornerShape(RADIO_TARJETA),
    extraLarge = RoundedCornerShape(RADIO_HOJA),
)
