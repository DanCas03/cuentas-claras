package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Patrón único de «fila con algo a la derecha» (monto, estado, acción): el texto a la izquierda y el elemento
 * final a la derecha, centrados en vertical. El final se mide primero con su ancho natural (una cifra nunca se
 * parte ni se corta, DESIGN.md §3); si al texto le quedan menos de `medidas.anchoMinimoTexto`, la fila se apila:
 * el texto arriba a todo el ancho y el final debajo, alineado al final, separado `espacios.xs`. Con el ancho
 * habitual se ve como una fila normal.
 *
 * El final se mide como mucho a todo el ancho de la fila: debe caber en esa línea. Por eso cada cifra va en su
 * propio `Monto` (una por moneda, apiladas, nunca unidas en una sola línea), y `Monto` se encoge hasta 11 sp si ni
 * a todo el ancho le alcanza: así ninguna cifra se corta.
 *
 * El ícono o cuadro de la izquierda y los chevrons van fuera, en la `Row` que contiene a esta fila.
 */
@Composable
fun FilaConFinal(
    texto: @Composable () -> Unit,
    final: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    separacion: Dp = CuentasTema.espacios.m,
) {
    val minimoTexto = CuentasTema.medidas.anchoMinimoTexto
    val separacionVertical = CuentasTema.espacios.xs
    Layout(
        contents = listOf({ Box { texto() } }, { Box { final() } }),
        modifier = modifier,
        measurePolicy = MultiContentMeasurePolicy { (medibleTexto, medibleFinal), restricciones ->
            val sep = separacion.roundToPx()
            if (!restricciones.hasBoundedWidth) {
                // Sin ancho acotado (p. ej. dentro de un desplazamiento horizontal) no hay nada que ceder.
                val f = medibleFinal.first().measure(Constraints())
                val t = medibleTexto.first().measure(Constraints())
                val ancho = t.width + (if (f.width > 0) sep + f.width else 0)
                val alto = maxOf(t.height, f.height, restricciones.minHeight)
                return@MultiContentMeasurePolicy layout(ancho, alto) {
                    t.placeRelative(0, (alto - t.height) / 2)
                    f.placeRelative(ancho - f.width, (alto - f.height) / 2)
                }
            }
            val ancho = restricciones.maxWidth
            val f = medibleFinal.first().measure(Constraints(maxWidth = ancho))
            val anchoLado = if (f.width > 0) ancho - f.width - sep else ancho
            if (f.width == 0 || anchoLado >= minimoTexto.roundToPx()) {
                val t = medibleTexto.first().measure(Constraints(minWidth = anchoLado, maxWidth = anchoLado))
                val alto = maxOf(t.height, f.height, restricciones.minHeight)
                layout(ancho, alto) {
                    t.placeRelative(0, (alto - t.height) / 2)
                    f.placeRelative(ancho - f.width, (alto - f.height) / 2)
                }
            } else {
                val t = medibleTexto.first().measure(Constraints(minWidth = ancho, maxWidth = ancho))
                val sepV = separacionVertical.roundToPx()
                val alto = maxOf(t.height + sepV + f.height, restricciones.minHeight)
                layout(ancho, alto) {
                    t.placeRelative(0, 0)
                    f.placeRelative(ancho - f.width, t.height + sepV)
                }
            }
        },
    )
}

@Preview(widthDp = 260)
@Composable
private fun VistaFilaConFinal() {
    VistaPreviaComponente {
        FilaConFinal(
            texto = {
                Column {
                    Text("Supermercado", style = CuentasTema.tipografia.cuerpo, color = CuentasTema.colores.texto)
                    Text("jue 15 · BNC", style = CuentasTema.tipografia.etiqueta, color = CuentasTema.colores.textoSecundario)
                }
            },
            final = { Monto(1351775, Currency.VES, Signo.NEGATIVO) },
        )
        FilaConFinal(
            texto = { Text("Café", style = CuentasTema.tipografia.cuerpo, color = CuentasTema.colores.texto) },
            final = { Monto(250, Currency.USD, Signo.NEGATIVO) },
        )
    }
}
