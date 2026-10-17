package com.cuentasclaras.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Formas de la app (DESIGN.md §2). Salen de `CuentasTema.diseno.formas`; Libreta: tarjeta 20 dp (bisel 4 dp),
 * control 12 dp, píldora 50 %, hoja 28 dp.
 */
data class Formas(
    val tarjeta: CornerBasedShape,
    val control: CornerBasedShape,
    val pildora: CornerBasedShape,
    /** Radio exterior de la tarjeta (el núcleo del doble bisel usa `radioTarjeta - biselTarjeta`). */
    val radioTarjeta: Dp,
    /** Grosor del bisel de la tarjeta (0 dp en los diseños sin bisel). */
    val biselTarjeta: Dp,
    /** Radio superior de la hoja modal. */
    val hoja: Dp,
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
    radioTarjeta = RADIO_TARJETA,
    biselTarjeta = BISEL_TARJETA,
    hoja = RADIO_HOJA,
)

val EspaciosCuentas = Espacios(
    xs = 4.dp,
    s = 8.dp,
    m = 16.dp,
    l = 24.dp,
    xl = 32.dp,
)

/** Formas de Material 3 derivadas de las del diseño activo (menús, diálogos, campos). */
internal fun formasMaterial(f: Formas): Shapes = Shapes(
    extraSmall = f.control,
    small = f.control,
    medium = f.control,
    large = f.tarjeta,
    extraLarge = RoundedCornerShape(f.hoja),
)
