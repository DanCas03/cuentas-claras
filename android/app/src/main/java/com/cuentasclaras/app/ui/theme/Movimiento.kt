package com.cuentasclaras.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Tokens de movimiento (DESIGN.md §5): nada de `LinearEasing`; duraciones entre 150 y 300 ms. */
object Movimiento {
    /** Salida rápida y frenado largo: entra con decisión y se asienta suave. */
    val suave: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val CORTO = 150
    const val MEDIO = 220
    const val LARGO = 300

    /** Resorte para presiones y cambios de estado (escala de botones, chips). `EXPRESIVO` (Aurora) rebota más. */
    fun <T> resorte(nivel: NivelMovimiento = NivelMovimiento.CONTENIDO): FiniteAnimationSpec<T> = when (nivel) {
        NivelMovimiento.CONTENIDO -> spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        NivelMovimiento.EXPRESIVO -> spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
    }

    /** Transición de color o de opacidad con la curva propia. Con `EXPRESIVO` la duración sube un 50 %. */
    fun <T> transicion(duracion: Int = MEDIO, nivel: NivelMovimiento = NivelMovimiento.CONTENIDO): FiniteAnimationSpec<T> =
        tween(durationMillis = if (nivel == NivelMovimiento.EXPRESIVO) duracion * 3 / 2 else duracion, easing = suave)
}
