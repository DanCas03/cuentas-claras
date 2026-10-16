package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Un grupo de barras (p. ej. un mes): su rótulo bajo el eje y un valor en céntimos por serie, en el orden de `colores`. */
data class GrupoBarras(val etiqueta: String, val valores: List<Long>)

/** Alto total de la gráfica, incluidas la banda del valor máximo arriba y la de rótulos abajo. */
private val ALTO_BARRAS = 184.dp

/** Ancho máximo de cada barra: marcas finas aunque haya pocos grupos. */
private val ANCHO_MAX_BARRA = 28.dp

/** Separación entre barras vecinas de un mismo grupo (hueco del color de la superficie). */
private val SEPARACION_BARRAS = 2.dp

/** Radio de las esquinas superiores (DESIGN.md §7); la base queda recta sobre el eje. */
private val RADIO_BARRA = 6.dp

/** Grosor de la rejilla (DESIGN.md §7). */
private val GROSOR_REJILLA = 1.dp

/** Aire entre el texto y la rejilla. */
private val AIRE_TEXTO = 4.dp

/**
 * Barras agrupadas dibujadas en `Canvas` con una sola escala que va de 0 al valor máximo de todos los grupos
 * (rotulado arriba a la izquierda con `formato`). Rejilla de 1 dp en `borde` en el máximo y en la base, sin ejes
 * pesados; barras de hasta 28 dp con esquinas superiores de 6 dp y 2 dp de separación. El último grupo (el mes
 * que se está viendo) rotula su etiqueta en `texto`; los demás en `textoSecundario`. `series` nombra cada serie
 * en la descripción accesible, que enumera todos los importes formateados.
 */
@Composable
fun Barras(
    grupos: List<GrupoBarras>,
    colores: List<Color>,
    formato: (Long) -> String,
    modifier: Modifier = Modifier,
    series: List<String> = emptyList(),
) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val medidor = rememberTextMeasurer()
    val estiloEje = t.etiqueta.copy(color = c.textoSecundario, fontFeatureSettings = "tnum")
    val estiloActual = estiloEje.copy(color = c.texto, fontWeight = FontWeight.W600)
    val maximo = grupos.maxOfOrNull { g -> g.valores.maxOrNull() ?: 0L } ?: 0L
    val descripcion = buildString {
        append("Gráfica de barras. ")
        grupos.forEach { g ->
            append("Mes ")
            append(g.etiqueta)
            append(": ")
            append(
                g.valores.mapIndexed { i, v -> (series.getOrNull(i)?.let { "$it " } ?: "") + formato(v) }.joinToString(", "),
            )
            append(". ")
        }
    }.trim()

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ALTO_BARRAS)
            .semantics { contentDescription = descripcion },
    ) {
        if (grupos.isEmpty()) return@Canvas
        val aire = AIRE_TEXTO.toPx()
        val rotuloMaximo = medidor.measure(formato(maximo), estiloEje)
        val altoTexto = rotuloMaximo.size.height.toFloat()
        val arriba = altoTexto + aire
        val abajo = size.height - altoTexto - aire * 2f
        val altoUtil = abajo - arriba
        val rejilla = GROSOR_REJILLA.toPx()

        // Rejilla: máximo y base.
        drawLine(c.borde, Offset(0f, arriba), Offset(size.width, arriba), rejilla)
        drawLine(c.borde, Offset(0f, abajo), Offset(size.width, abajo), rejilla)
        drawText(rotuloMaximo, topLeft = Offset(0f, 0f))

        val ranura = size.width / grupos.size
        val separacion = SEPARACION_BARRAS.toPx()
        val radio = RADIO_BARRA.toPx()
        grupos.forEachIndexed { gi, g ->
            val n = g.valores.size.coerceAtLeast(1)
            val ancho = minOf(ANCHO_MAX_BARRA.toPx(), (ranura * 0.7f - separacion * (n - 1)) / n)
            val anchoGrupo = ancho * n + separacion * (n - 1)
            val x0 = ranura * gi + (ranura - anchoGrupo) / 2f
            g.valores.forEachIndexed { i, v ->
                if (v <= 0L || maximo <= 0L) return@forEachIndexed
                val alto = (v.toFloat() / maximo * altoUtil).coerceAtLeast(rejilla)
                val izquierda = x0 + i * (ancho + separacion)
                val r = minOf(radio, alto, ancho / 2f)
                val esquina = CornerRadius(r, r)
                val barra = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = izquierda, top = abajo - alto, right = izquierda + ancho, bottom = abajo,
                            topLeftCornerRadius = esquina, topRightCornerRadius = esquina,
                            bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(barra, colores.getOrElse(i) { c.textoSecundario })
            }
            val rotulo = medidor.measure(g.etiqueta, if (gi == grupos.lastIndex) estiloActual else estiloEje)
            drawText(
                rotulo,
                topLeft = Offset(ranura * gi + (ranura - rotulo.size.width) / 2f, abajo + aire),
            )
        }
    }
}

@Preview
@Composable
private fun VistaBarras() {
    VistaPreviaComponente {
        Tarjeta(titulo = "Comparativa mensual") {
            Barras(
                grupos = listOf(
                    GrupoBarras("07", listOf(0, 0)),
                    GrupoBarras("08", listOf(0, 800)),
                    GrupoBarras("09", listOf(80000, 0)),
                    GrupoBarras("10", listOf(1650, 4709)),
                ),
                colores = listOf(CuentasTema.colores.ingreso, CuentasTema.colores.gasto),
                formato = { formatMoney(it, Currency.USD) },
                series = listOf("Ingresos", "Gastos"),
            )
        }
    }
}
