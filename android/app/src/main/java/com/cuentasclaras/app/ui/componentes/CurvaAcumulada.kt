package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.PuntoDiario
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Alto total de la gráfica, incluidas la banda del valor máximo arriba y la de días abajo. */
private val ALTO_CURVA = 168.dp

/** Grosor de la línea (DESIGN.md §7). */
private val GROSOR_LINEA = 2.5.dp

/** Grosor de la rejilla (DESIGN.md §7). */
private val GROSOR_REJILLA = 1.dp

/** Aire entre el texto y la rejilla. */
private val AIRE_TEXTO = 4.dp

/** Opacidad del área bajo la curva: un velo, no un bloque saturado. */
private const val ALFA_AREA = 0.12f

/** Días rotulados en el eje X (más el último día del mes). */
private val DIAS_ROTULADOS = listOf(1, 8, 15, 22)

/**
 * Gasto acumulado día a día del mes, dibujado en `Canvas`: línea de 2,5 dp en `gasto` con extremos redondeados y
 * un velo del mismo color debajo; eje X de días (1, 8, 15, 22 y el último), una sola escala de 0 al total del mes
 * rotulado arriba a la izquierda con `formato`, y rejilla de 1 dp en `borde` solo en el máximo y en la base. La
 * descripción accesible da el acumulado al cierre de cada semana y el total.
 */
@Composable
fun CurvaAcumulada(
    puntos: List<PuntoDiario>,
    formato: (Long) -> String,
    descripcion: String = "Gráfica de gasto acumulado del mes",
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val medidor = rememberTextMeasurer()
    val estiloEje = CuentasTema.tipografia.etiqueta.copy(color = c.textoSecundario, fontFeatureSettings = "tnum")
    val maximo = puntos.maxOfOrNull { it.acumuladoMinor } ?: 0L
    val ultimoDia = puntos.maxOfOrNull { it.day } ?: 0
    val descripcionCompleta = buildString {
        append(descripcion)
        append(". ")
        puntos.filter { it.day % 7 == 0 && it.day != ultimoDia }.forEach { p ->
            append("Día ")
            append(p.day)
            append(": ")
            append(formato(p.acumuladoMinor))
            append(". ")
        }
        puntos.lastOrNull()?.let { p ->
            append("Total al día ")
            append(p.day)
            append(": ")
            append(formato(p.acumuladoMinor))
            append(".")
        }
    }.trim()

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ALTO_CURVA)
            .semantics { contentDescription = descripcion },
    ) {
        if (puntos.isEmpty()) return@Canvas
        val aire = AIRE_TEXTO.toPx()
        val rotuloMaximo = medidor.measure(formato(maximo), estiloEje)
        val altoTexto = rotuloMaximo.size.height.toFloat()
        val arriba = altoTexto + aire
        val abajo = size.height - altoTexto - aire * 2f
        val altoUtil = abajo - arriba
        val rejilla = GROSOR_REJILLA.toPx()
        val linea = GROSOR_LINEA.toPx()
        // Media línea de margen a cada lado para que los extremos redondeados no se corten.
        val izquierda = linea / 2f
        val anchoUtil = size.width - linea
        val tramos = (ultimoDia - 1).coerceAtLeast(1)
        fun x(dia: Int) = izquierda + (dia - 1).toFloat() / tramos * anchoUtil
        fun y(valor: Long) = if (maximo > 0L) abajo - valor.toFloat() / maximo * altoUtil else abajo

        drawLine(c.borde, Offset(0f, arriba), Offset(size.width, arriba), rejilla)
        drawLine(c.borde, Offset(0f, abajo), Offset(size.width, abajo), rejilla)
        drawText(rotuloMaximo, topLeft = Offset(0f, 0f))

        val ordenados = puntos.sortedBy { it.day }
        val trazo = Path().apply {
            ordenados.forEachIndexed { i, p ->
                if (i == 0) moveTo(x(p.day), y(p.acumuladoMinor)) else lineTo(x(p.day), y(p.acumuladoMinor))
            }
        }
        val area = Path().apply {
            moveTo(x(ordenados.first().day), abajo)
            ordenados.forEach { p -> lineTo(x(p.day), y(p.acumuladoMinor)) }
            lineTo(x(ordenados.last().day), abajo)
            lineTo(x(ordenados.first().day), abajo)
            close()
        }
        drawPath(area, c.gasto.copy(alpha = ALFA_AREA))
        drawPath(trazo, c.gasto, style = Stroke(width = linea, cap = StrokeCap.Round, join = StrokeJoin.Round))

        (DIAS_ROTULADOS.filter { it < ultimoDia - 2 } + ultimoDia).forEach { dia ->
            val rotulo = medidor.measure(dia.toString(), estiloEje)
            val centro = x(dia) - rotulo.size.width / 2f
            drawText(
                rotulo,
                topLeft = Offset(centro.coerceIn(0f, size.width - rotulo.size.width), abajo + aire),
            )
        }
    }
}

@Preview
@Composable
private fun VistaCurvaAcumulada() {
    val porDia = mapOf(1 to 345439L, 3 to 0L, 9 to 120000L)
    var acumulado = 0L
    val puntos = List(31) { i ->
        acumulado += porDia[i + 1] ?: 0L
        PuntoDiario(i + 1, acumulado)
    }
    VistaPreviaComponente {
        Tarjeta(titulo = "Este mes") {
            CurvaAcumulada(puntos, formato = { formatMoney(it, Currency.VES) })
        }
    }
}
