package com.cuentasclaras.app.ui.componentes

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.theme.CuentasTema
import kotlin.math.abs

/** Signo visual de un monto: nunca solo color (DESIGN.md §4). */
enum class Signo { NINGUNO, POSITIVO, NEGATIVO }

/**
 * Tarjeta con doble bisel: una carcasa exterior en `superficie` y un núcleo concéntrico en `superficieElevada`
 * con un filo superior apenas iluminado. Sin bordes grises de 1 px ni sombras duras. El título (hasta 3 líneas) y
 * la acción comparten fila mientras al título le quede `medidas.anchoMinimoTexto`; si no, la acción baja.
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
    val nucleo = RoundedCornerShape(f.radioTarjeta - f.biselTarjeta)
    Box(
        modifier
            .fillMaxWidth()
            .clip(CuentasTema.formas.tarjeta)
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
            contenido()
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
