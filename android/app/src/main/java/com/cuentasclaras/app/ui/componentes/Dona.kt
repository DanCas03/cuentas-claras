package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Una porción de la dona: nombre visible, valor en céntimos (> 0) y color (el de la categoría, dato del usuario). */
data class SegmentoDona(val etiqueta: String, val valor: Long, val color: Color)

/** Diámetro exterior del anillo. */
private val DIAMETRO_DONA = 184.dp

/** Grosor del anillo (DESIGN.md §7). */
private val GROSOR_DONA = 20.dp

/** Separación entre porciones, en grados (DESIGN.md §7): deja ver el núcleo `superficieElevada` de la tarjeta. */
private const val SEPARACION_GRADOS = 2f

/** Porciones con nombre como máximo; el resto se agrupa en «Otras» (DESIGN.md §7). */
private const val MAX_PORCIONES = 6

/** Margen entre el borde interior del anillo y la cifra central. */
private val MARGEN_HUECO = 12.dp

/** Punto de color de la leyenda. */
private val PUNTO_LEYENDA = 10.dp

/**
 * Dona de partes de un total dibujada en `Canvas`: anillo de 20 dp que arranca a las 12 en punto y gira en el
 * sentido del reloj, con 2° de separación entre porciones; en el hueco, el total en `cifra` (se encoge para no
 * cortarse con montos largos en Bs). Si hay más de 7 segmentos, los 6 mayores conservan su nombre y el resto se
 * suma en «Otras» (`textoSecundario`). Debajo, una leyenda en dos columnas con el color, el nombre, el monto y
 * el porcentaje: ningún texto vive sobre el anillo, así nada se recorta. Para el lector de pantalla todo el bloque
 * es un solo nodo con el resumen en español y los importes formateados.
 */
@Composable
fun Dona(
    segmentos: List<SegmentoDona>,
    formato: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    val visibles = remember(segmentos, c.textoSecundario) { agruparOtras(segmentos.filter { it.valor > 0 }, c.textoSecundario) }
    val total = visibles.sumOf { it.valor }
    if (total <= 0L) return
    val descripcion = buildString {
        append("Gráfica de dona de gastos por categoría. Total ")
        append(formato(total))
        append(". ")
        visibles.forEach { s ->
            append(s.etiqueta)
            append(": ")
            append(formato(s.valor))
            append(", ")
            append(porcentaje(s.valor, total))
            append(" %. ")
        }
    }.trim()

    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(e.m),
    ) {
        Box(Modifier.size(DIAMETRO_DONA), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(DIAMETRO_DONA)) {
                val grosor = GROSOR_DONA.toPx()
                val esquina = Offset(grosor / 2f, grosor / 2f)
                val lado = Size(size.width - grosor, size.height - grosor)
                val hueco = if (visibles.size > 1) SEPARACION_GRADOS else 0f
                var inicio = -90f
                visibles.forEach { s ->
                    val barrido = s.valor.toFloat() / total * 360f
                    // Una porción diminuta no desaparece por la separación: conserva la mitad de su arco.
                    val dibujado = if (barrido > hueco * 2f) barrido - hueco else barrido / 2f
                    drawArc(
                        color = s.color,
                        startAngle = inicio + (barrido - dibujado) / 2f,
                        sweepAngle = dibujado,
                        useCenter = false,
                        topLeft = esquina,
                        size = lado,
                        style = Stroke(width = grosor, cap = StrokeCap.Butt),
                    )
                    inicio += barrido
                }
            }
            Column(
                Modifier.width(DIAMETRO_DONA - GROSOR_DONA * 2 - MARGEN_HUECO * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Gastos", style = t.etiqueta, color = c.textoSecundario, maxLines = 1)
                BasicText(
                    formato(total),
                    style = t.cifra.copy(color = c.texto, textAlign = TextAlign.Center),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = t.cifra.fontSize),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(e.s)) {
            visibles.chunked(2).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.m)) {
                    fila.forEach { s -> ItemLeyenda(s, total, formato, Modifier.weight(1f)) }
                    if (fila.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ItemLeyenda(s: SegmentoDona, total: Long, formato: (Long) -> String, modifier: Modifier) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = CuentasTema.espacios.xs)
                .size(PUNTO_LEYENDA)
                .background(s.color, CircleShape),
        )
        Column(Modifier.padding(start = CuentasTema.espacios.s)) {
            Text(s.etiqueta, style = t.etiqueta, color = c.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formato(s.valor)} · ${porcentaje(s.valor, total)} %",
                style = t.etiqueta.copy(fontFeatureSettings = "tnum"),
                color = c.textoSecundario,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Porcentaje entero de `valor` sobre `total`, redondeado como `Math.round` de JS. */
private fun porcentaje(valor: Long, total: Long): Long = if (total > 0L) Math.round(valor * 100.0 / total) else 0L

/** Los 6 mayores con su nombre y el resto sumado en «Otras»; con 7 o menos se dibujan todos. */
private fun agruparOtras(segmentos: List<SegmentoDona>, colorOtras: Color): List<SegmentoDona> {
    if (segmentos.size <= MAX_PORCIONES + 1) return segmentos
    val ordenados = segmentos.sortedByDescending { it.valor }
    val resto = ordenados.drop(MAX_PORCIONES).sumOf { it.valor }
    return ordenados.take(MAX_PORCIONES) + SegmentoDona("Otras", resto, colorOtras)
}

@Preview
@Composable
private fun VistaDona() {
    VistaPreviaComponente {
        Tarjeta(titulo = "Este mes") {
            Dona(
                listOf(
                    SegmentoDona("Transporte", 120000, Color(0xFF60A5FA)),
                    SegmentoDona("Supermercado", 34579, Color(0xFF34D399)),
                    SegmentoDona("Suscripciones", 1599, Color(0xFFA78BFA)),
                    SegmentoDona("Comisiones bancarias", 360, Color(0xFFF59E0B)),
                ),
                formato = { formatMoney(it, Currency.VES) },
            )
        }
    }
}
