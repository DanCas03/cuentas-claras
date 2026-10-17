package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.EstiloFondo
import com.cuentasclaras.app.ui.theme.Movimiento
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Duración de un vaivén completo de las manchas de Aurora. */
private const val VAIVEN_AURORA_MS = 24_000

/** Opacidad de cada mancha de color (spec 2026-10-17 §6.2: 18 %, detrás de las tarjetas). */
private const val ALFA_MANCHA = 0.18f

/** Desenfoque de las manchas (solo API ≥ 31; en menores `blur` no hace nada y se ven como degradados radiales). */
private val DESENFOQUE_MANCHA = 48.dp

/**
 * Contador de tarjetas compuestas en el mismo cuadro, para la entrada en cascada de Aurora: cada `Tarjeta` toma
 * su índice al nacer y `FondoDiseno` lo pone a cero en cada cuadro, así las tarjetas que aparecen juntas (entrar
 * a una pantalla) se escalonan y una que aparece sola arranca sin retardo.
 */
class ContadorCascada {
    private val valor = AtomicInteger(0)
    fun siguiente(): Int = valor.getAndIncrement()
    fun reiniciar() = valor.set(0)
}

val LocalContadorCascada = staticCompositionLocalOf<ContadorCascada?> { null }

/**
 * Fondo de la raíz de la app según `CuentasTema.diseno.fondo`. `LISO` pinta `fondo`; `AURORA` añade tres manchas
 * radiales de la paleta (`primario`, `ingreso`, `aviso`) que derivan en bucle lento tras el contenido, y provee el
 * contador de cascada a las tarjetas. Es la excepción declarada a DESIGN.md §5 (nada en bucle).
 *
 * `content` se compone SIEMPRE en el mismo sitio del árbol (un único `CompositionLocalProvider`, con contador o
 * sin él): si estuviera en dos ramas de un `if`, cambiar a Aurora o salir de él recrearía el `NavHost` entero y
 * se perdería el scroll y todo `rememberSaveable` de las pantallas.
 */
@Composable
fun FondoDiseno(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = CuentasTema.colores
    val aurora = CuentasTema.diseno.fondo == EstiloFondo.AURORA
    Box(modifier.fillMaxSize().background(c.fondo)) {
        if (aurora) ManchasAurora()
        val contador = remember { ContadorCascada() }
        if (aurora) {
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { }
                    contador.reiniciar()
                }
            }
        }
        CompositionLocalProvider(LocalContadorCascada provides if (aurora) contador else null, content = content)
    }
}

/** Las tres manchas de Aurora: `primario`, `ingreso` y `aviso` al 18 %, derivando en un vaivén de 24 s. */
@Composable
private fun ManchasAurora() {
    val c = CuentasTema.colores
    val transicion = rememberInfiniteTransition(label = "aurora")
    val fase by transicion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(VAIVEN_AURORA_MS, easing = Movimiento.suave), RepeatMode.Reverse),
        label = "faseAurora",
    )
    val manchas = listOf(c.primario, c.ingreso, c.aviso)
    Canvas(Modifier.fillMaxSize().blur(DESENFOQUE_MANCHA)) {
        val radio = size.minDimension * 0.45f
        manchas.forEachIndexed { i, color ->
            val angulo = (fase + i / 3f) * 2f * PI.toFloat()
            val centro = Offset(
                size.width * (0.5f + 0.35f * cos(angulo)),
                size.height * (0.4f + 0.3f * sin(angulo * 0.7f)),
            )
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = ALFA_MANCHA), Color.Transparent), centro, radio),
                radio,
                centro,
            )
        }
    }
}
