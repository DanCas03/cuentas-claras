package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.EstiloTarjeta
import com.cuentasclaras.app.ui.theme.Movimiento
import kotlin.math.abs

/** Grosor del contorno giratorio de Aurora. */
private val GROSOR_FILO_AURORA = 1.5.dp

/** Un giro completo del contorno de Aurora. */
private const val GIRO_FILO_MS = 8_000

/** Retardo entre tarjetas en la entrada en cascada de Aurora, y tope de tarjetas escalonadas. */
private const val RETARDO_CASCADA_MS = 60
private const val MAX_CASCADA = 8

/** Signo visual de un monto: nunca solo color (DESIGN.md §4). */
enum class Signo { NINGUNO, POSITIVO, NEGATIVO }

/**
 * Tarjeta de la app. Su aspecto lo decide `CuentasTema.diseno.tarjeta`:
 * - `BISEL` (Libreta): carcasa exterior en `superficie` y núcleo concéntrico en `superficieElevada` con un filo
 *   superior apenas iluminado. Sin bordes grises de 1 px ni sombras duras.
 * - `PLANA` (Tinta): `superficie` plana con el radio del diseño y un filete de 1 dp en `borde` solo abajo.
 * - `CONTORNO` (Isla): `superficieElevada` plana con contorno de 1 dp degradado (`texto` 10 % → 0) y relleno
 *   `m + xs`; sin carcasa.
 * - `AURORA`: `superficieElevada` con un contorno de 1,5 dp en degradado de barrido (`primario`, `ingreso`,
 *   `aviso`) que gira en bucle, y entrada en cascada (alfa + traslación, 60 ms por tarjeta). Excepción declarada
 *   a DESIGN.md §5/§8.
 * El título (hasta 3 líneas) y la acción comparten fila mientras al título le quede `medidas.anchoMinimoTexto`;
 * si no, la acción baja.
 */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    accion: (@Composable () -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val f = CuentasTema.formas
    when (CuentasTema.diseno.tarjeta) {
        EstiloTarjeta.PLANA -> Column(
            modifier
                .fillMaxWidth()
                .clip(f.tarjeta)
                .background(c.superficie)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(c.borde, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                .padding(e.m),
        ) {
            CabeceraTarjeta(titulo, accion)
            contenido()
        }
        EstiloTarjeta.CONTORNO -> Column(
            modifier
                .fillMaxWidth()
                .clip(f.tarjeta)
                .background(c.superficieElevada)
                .border(1.dp, Brush.verticalGradient(listOf(c.texto.copy(alpha = 0.10f), Color.Transparent)), f.tarjeta)
                .padding(e.m + e.xs),
        ) {
            CabeceraTarjeta(titulo, accion)
            contenido()
        }
        EstiloTarjeta.AURORA -> {
            val entrada = remember { Animatable(0f) }
            val indice = LocalContadorCascada.current?.let { remember { it.siguiente() } } ?: 0
            LaunchedEffect(Unit) {
                entrada.animateTo(
                    1f,
                    tween(Movimiento.LARGO * 3 / 2, delayMillis = RETARDO_CASCADA_MS * minOf(indice, MAX_CASCADA), easing = Movimiento.suave),
                )
            }
            val giro = rememberInfiniteTransition(label = "filoAurora")
            // Un giro continuo no puede acelerar ni frenar: es el único uso permitido de LinearEasing (solo Aurora).
            val angulo by giro.animateFloat(
                0f, 360f, infiniteRepeatable(tween(GIRO_FILO_MS, easing = LinearEasing), RepeatMode.Restart), label = "anguloFilo",
            )
            val colores = listOf(c.primario, c.ingreso, c.aviso, c.primario)
            val desplazamiento = e.m
            Column(
                modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = entrada.value
                        translationY = desplazamiento.toPx() * (1f - entrada.value)
                    }
                    .clip(f.tarjeta)
                    .background(c.superficieElevada)
                    .drawBehind {
                        val grosor = GROSOR_FILO_AURORA.toPx()
                        val radio = CornerRadius(f.radioTarjeta.toPx())
                        val anillo = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRoundRect(RoundRect(Rect(Offset.Zero, size), radio))
                            addRoundRect(
                                RoundRect(
                                    Rect(Offset(grosor, grosor), Size(size.width - grosor * 2, size.height - grosor * 2)),
                                    CornerRadius(radio.x - grosor),
                                ),
                            )
                        }
                        clipPath(anillo) {
                            rotate(angulo) {
                                val lado = maxOf(size.width, size.height) * 1.5f
                                drawRect(
                                    Brush.sweepGradient(colores, center),
                                    topLeft = Offset(center.x - lado / 2, center.y - lado / 2),
                                    size = Size(lado, lado),
                                )
                            }
                        }
                    }
                    .padding(e.m + e.xs),
            ) {
                CabeceraTarjeta(titulo, accion)
                contenido()
            }
        }
        EstiloTarjeta.BISEL -> {
            val nucleo = RoundedCornerShape(f.radioTarjeta - f.biselTarjeta)
            Box(
                modifier
                    .fillMaxWidth()
                    .clip(f.tarjeta)
                    .background(c.superficie)
                    .padding(f.biselTarjeta),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(nucleo)
                        .background(c.superficieElevada)
                        .border(1.dp, Brush.verticalGradient(listOf(c.texto.copy(alpha = 0.06f), Color.Transparent)), nucleo)
                        .padding(e.m),
                ) {
                    CabeceraTarjeta(titulo, accion)
                    contenido()
                }
            }
        }
    }
}

/** Título (encabezado accesible) y acción de la tarjeta; sin título ni acción no pinta nada. */
@Composable
private fun CabeceraTarjeta(titulo: String?, accion: (@Composable () -> Unit)?) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    if (titulo != null) {
        // Si el título no cabe junto a la acción, la acción baja bajo él (FilaConFinal): nunca «Acceso a notificacion…».
        FilaConFinal(
            texto = {
                Text(
                    titulo,
                    style = CuentasTema.tipografia.subtitulo,
                    color = c.texto,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            },
            final = { accion?.invoke() },
            separacion = e.s,
            modifier = Modifier.fillMaxWidth().padding(bottom = e.s),
        )
    } else if (accion != null) {
        Row(Modifier.fillMaxWidth().padding(bottom = e.s), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            accion()
        }
    }
}

/**
 * Monto con `formatMoney` y cifras tabulares. `POSITIVO` antepone «+» y usa `ingreso`; `NEGATIVO` antepone «−»
 * (U+2212) y usa `gasto`; `NINGUNO` muestra el formato de `formatMoney` tal cual (negativos como `-$5.00`).
 * Siempre en una línea y nunca cortado: si ni siquiera el ancho disponible le alcanza (p. ej. el final apilado de
 * una `FilaConFinal` a 277 dp con un monto en Bs muy largo), la cifra se encoge hasta 11 sp (DESIGN.md §3).
 */
@Composable
fun Monto(
    minor: Long,
    currency: Currency,
    signo: Signo = Signo.NINGUNO,
    grande: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val texto = when (signo) {
        Signo.NINGUNO -> formatMoney(minor, currency)
        Signo.POSITIVO -> "+" + formatMoney(abs(minor), currency)
        Signo.NEGATIVO -> "−" + formatMoney(abs(minor), currency)
    }
    val color = when (signo) {
        Signo.NINGUNO -> c.texto
        Signo.POSITIVO -> c.ingreso
        Signo.NEGATIVO -> c.gasto
    }
    val estilo = if (grande) CuentasTema.tipografia.cifraGrande else CuentasTema.tipografia.cifra
    BasicText(
        texto,
        style = estilo.copy(color = color),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = CIFRA_MINIMA, maxFontSize = estilo.fontSize),
        modifier = modifier,
    )
}

/** Tamaño mínimo de una cifra al encogerse para no cortarse (DESIGN.md §3: nada por debajo de 11 sp). */
private val CIFRA_MINIMA = 11.sp

/** Marco común de las vistas previas de los componentes: tema real sobre el `fondo`. */
@Composable
internal fun VistaPreviaComponente(contenido: @Composable ColumnScope.() -> Unit) {
    CuentasTema {
        Column(
            Modifier.background(CuentasTema.colores.fondo).padding(CuentasTema.espacios.m),
            verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.m),
            content = contenido,
        )
    }
}

@Preview
@Composable
private fun VistaTarjeta() {
    VistaPreviaComponente {
        Tarjeta(titulo = "Este mes", accion = { Text("octubre", style = CuentasTema.tipografia.etiqueta) }) {
            Monto(85000, Currency.USD, Signo.POSITIVO)
            Monto(31020, Currency.USD, Signo.NEGATIVO)
            Monto(123456, Currency.USD, grande = true)
        }
    }
}
