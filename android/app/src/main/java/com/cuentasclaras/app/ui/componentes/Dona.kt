package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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

/** Barrido mínimo de toda porción > 0, en grados: una categoría diminuta no desaparece del anillo. */
private const val BARRIDO_MINIMO = 1f

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
 * suma en «Otras» (`textoSecundario`). Debajo, una leyenda (dos columnas si caben, si no una) con el color, el
 * nombre, el monto y el porcentaje: ningún texto vive sobre el anillo y la cifra nunca se recorta. Para el lector de pantalla todo el bloque
 * es un solo nodo con el resumen en español y los importes formateados.
 */
@Composable
fun Dona(
    segmentos: List<SegmentoDona>,
    formato: (Long) -> String,
    descripcion: String = "Gráfica de dona de gastos por categoría",
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    val visibles = remember(segmentos, c.textoSecundario) { agruparOtras(segmentos.filter { it.valor > 0 }, c.textoSecundario) }
    val total = visibles.sumOf { it.valor }
    if (total <= 0L) return
    val descripcionCompleta = buildString {
        append(descripcion)
        append(". Total ")
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
            .clearAndSetSemantics { contentDescription = descripcionCompleta },
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
                barridos(visibles, total).forEachIndexed { i, barrido ->
                    val s = visibles[i]
                    // La separación sale de cada porción, pero ninguna se dibuja por debajo del barrido mínimo.
                    val dibujado = maxOf(barrido - hueco, minOf(barrido, BARRIDO_MINIMO))
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
        Leyenda(visibles, total, formato)
    }
}

/**
 * Leyenda en dos columnas si en cada una cabe entera la línea «monto · %» más ancha; si no, en una sola. La cifra
 * y el porcentaje nunca se cortan (DESIGN.md §3); solo el nombre de la categoría puede acabar en «…».
 */
@Composable
private fun Leyenda(visibles: List<SegmentoDona>, total: Long, formato: (Long) -> String) {
    val e = CuentasTema.espacios
    val estiloCifra = CuentasTema.tipografia.etiqueta.copy(fontFeatureSettings = "tnum")
    val medidor = rememberTextMeasurer()
    val lineas = remember(visibles, total, formato) { visibles.map { lineaCifra(it, total, formato) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val densidad = LocalDensity.current
        val anchoCifra = remember(lineas, estiloCifra, densidad) {
            lineas.maxOfOrNull { medidor.measure(it, estiloCifra, maxLines = 1, softWrap = false).size.width } ?: 0
        }
        val anchoColumna = with(densidad) { ((maxWidth - e.m) / 2 - PUNTO_LEYENDA - e.s).roundToPx() }
        val columnas = if (anchoCifra <= anchoColumna) 2 else 1
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(e.s)) {
            visibles.indices.chunked(columnas).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.m)) {
                    fila.forEach { i -> ItemLeyenda(visibles[i], lineas[i], estiloCifra, Modifier.weight(1f)) }
                    if (fila.size < columnas) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** «$19.18 · 34 %»: la línea de la cifra de un elemento de la leyenda. */
private fun lineaCifra(s: SegmentoDona, total: Long, formato: (Long) -> String): String =
    "${formato(s.valor)} · ${porcentaje(s.valor, total)} %"

@Composable
private fun ItemLeyenda(s: SegmentoDona, lineaCifra: String, estiloCifra: TextStyle, modifier: Modifier) {
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
            // Leyenda en `etiqueta` + `tnum` (excepción a `cifra`, DESIGN.md §7): sin elipsis ni salto.
            Text(lineaCifra, style = estiloCifra, color = c.textoSecundario, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Grados de cada porción: proporcionales, con un mínimo de [BARRIDO_MINIMO] para toda porción > 0. Lo que ese
 * mínimo añade se resta de la porción mayor, para que el total no pase de 360°.
 */
private fun barridos(visibles: List<SegmentoDona>, total: Long): List<Float> {
    val grados = visibles.map { maxOf(it.valor.toFloat() / total * 360f, BARRIDO_MINIMO) }.toMutableList()
    val exceso = grados.sum() - 360f
    if (exceso > 0f) {
        val mayor = grados.indices.maxBy { grados[it] }
        grados[mayor] -= exceso
    }
    return grados
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
