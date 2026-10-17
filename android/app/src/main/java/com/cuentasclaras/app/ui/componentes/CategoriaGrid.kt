package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.ui.theme.CuentasTema

private const val COLUMNAS_CATEGORIAS = 4

/** Lado del cuadro del emoji de cada categoría. */
private val CUADRO_CATEGORIA = 48.dp

/** `#RGB` (forma corta de CSS, posible en respaldos de la PWA). */
private val HEX_CORTO = Regex("^#[0-9a-fA-F]{3}$")

/**
 * Color de una categoría: es dato del usuario (hex de la web), por eso no sale del tema. `#RGB` se expande a
 * `#RRGGBB` (como en CSS) porque `parseColor` no lo admite. Si el hex no es válido, usa el respaldo dado.
 */
fun colorDeCategoria(hex: String, respaldo: Color): Color {
    val largo = if (HEX_CORTO.matches(hex)) "#" + hex.drop(1).map { "$it$it" }.joinToString("") else hex
    return runCatching { Color(android.graphics.Color.parseColor(largo)) }.getOrDefault(respaldo)
}

/**
 * Grid de categorías de 4 columnas: emoji grande sobre un tinte del color de la categoría y el nombre en 2
 * líneas como máximo. Tocar una categoría llama a `onElegir(id)`. Con `enabled = false` (mientras se guarda)
 * todo queda deshabilitado al 38 %. No es perezoso: vive dentro del scroll de la pantalla de registro.
 */
@Composable
fun CategoriaGrid(
    categorias: List<Category>,
    enabled: Boolean,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val e = CuentasTema.espacios
    // Un solo medidor para todas las celdas: el nombre se mide antes de pintarlo para elegir su tamaño.
    val medidor = rememberTextMeasurer()
    Column(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
        verticalArrangement = Arrangement.spacedBy(e.xs),
    ) {
        categorias.chunked(COLUMNAS_CATEGORIAS).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.xs)) {
                fila.forEach { categoria ->
                    CeldaCategoria(categoria, enabled, onElegir, medidor, Modifier.weight(1f))
                }
                repeat(COLUMNAS_CATEGORIAS - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Tamaño mínimo del nombre al encogerse (DESIGN.md §3: nada por debajo de 11 sp). */
private val NOMBRE_MINIMO = 11.sp

/** Paso del autoajuste del nombre, de `etiqueta` (13 sp) hacia abajo: 13 · 12,5 · 12 · 11,5 · 11. */
private val PASO_NOMBRE = 0.5.sp

/** Tamaño elegido para el nombre; `unaLinea` = ni a 11 sp cabe sin partir una palabra (va en una línea con «…»). */
private data class AjusteNombre(val tamano: TextUnit, val unaLinea: Boolean)

/**
 * Mayor tamaño, de `estilo.fontSize` a [NOMBRE_MINIMO] en pasos de [PASO_NOMBRE], en que el nombre cabe en 2 líneas
 * sin partir ninguna palabra (M1: «Supermer/cado»). La interlínea del estilo no cambia: el alto de la fila del grid
 * es el mismo con cualquier tamaño.
 */
private fun ajustarNombre(medidor: TextMeasurer, nombre: String, estilo: TextStyle, ancho: Int): AjusteNombre {
    var tamano = estilo.fontSize.value
    while (tamano >= NOMBRE_MINIMO.value) {
        val medida = medidor.measure(
            nombre,
            estilo.copy(fontSize = tamano.sp),
            maxLines = 2,
            constraints = Constraints(maxWidth = ancho),
        )
        if (!medida.hasVisualOverflow && !parteUnaPalabra(nombre, medida)) return AjusteNombre(tamano.sp, unaLinea = false)
        tamano -= PASO_NOMBRE.value
    }
    return AjusteNombre(NOMBRE_MINIMO, unaLinea = true)
}

/**
 * true si alguna línea (salvo la primera) empieza a media palabra: el carácter anterior no es un espacio ni un
 * corte natural («-», «/»).
 */
private fun parteUnaPalabra(texto: String, medida: TextLayoutResult): Boolean =
    (1 until medida.lineCount).any { linea ->
        val inicio = medida.getLineStart(linea)
        inicio in 1 until texto.length && texto[inicio - 1].let { !it.isWhitespace() && it != '-' && it != '/' }
    }

@Composable
private fun CeldaCategoria(
    categoria: Category,
    enabled: Boolean,
    onElegir: (String) -> Unit,
    medidor: TextMeasurer,
    modifier: Modifier,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val tono = colorDeCategoria(categoria.color, c.primario)
    Column(
        modifier
            .clip(CuentasTema.formas.control)
            .clickable(enabled = enabled, role = Role.Button) { onElegir(categoria.id) }
            // Sin relleno horizontal: el nombre usa todo el ancho de la celda (8 dp más por palabra en estrecho).
            .padding(vertical = e.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(CUADRO_CATEGORIA).clip(CuentasTema.formas.control).background(tono.copy(alpha = CuentasTema.medidas.alfaTinteCategoria)),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoria.icon, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.height(e.xs))
        // M1: una palabra nunca se parte. El nombre va en 1-2 líneas por palabras y se encoge hasta 11 sp; si ni así
        // cabe, una línea con «…» al final.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val estilo = CuentasTema.tipografia.etiqueta
            val ancho = constraints.maxWidth
            val ajuste = remember(categoria.name, estilo, ancho, medidor) { ajustarNombre(medidor, categoria.name, estilo, ancho) }
            Text(
                categoria.name,
                style = estilo.copy(fontSize = ajuste.tamano),
                color = c.texto,
                maxLines = if (ajuste.unaLinea) 1 else 2,
                softWrap = !ajuste.unaLinea,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun VistaCategoriaGrid() {
    val categorias = listOf(
        Category("cat-comida", "Comida", "🍔", "#F97316", CategoryKind.EXPENSE),
        Category("cat-mercado", "Mercado", "🛒", "#22C55E", CategoryKind.EXPENSE),
        Category("cat-transporte", "Transporte", "🚌", "#3B82F6", CategoryKind.EXPENSE),
        Category("cat-servicios", "Servicios del hogar", "💡", "#EAB308", CategoryKind.EXPENSE),
        Category("cat-salud", "Salud", "💊", "#EF4444", CategoryKind.EXPENSE),
    )
    VistaPreviaComponente {
        CategoriaGrid(categorias, enabled = true, onElegir = {})
    }
}
