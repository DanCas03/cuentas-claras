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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
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
    val estilo = CuentasTema.tipografia.etiqueta
    // Un solo medidor y una sola medida del ancho para todo el grid: todas las celdas miden lo mismo, así que los
    // nombres se ajustan de una vez, antes de pintarlos (M1).
    val medidor = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val hueco = with(LocalDensity.current) { e.xs.roundToPx() }
        val anchoCelda = (constraints.maxWidth - hueco * (COLUMNAS_CATEGORIAS - 1)) / COLUMNAS_CATEGORIAS
        val nombres = categorias.map { it.name }
        val ajustes = remember(nombres, estilo, anchoCelda, medidor) {
            nombres.distinct().associateWith { ajustarNombre(medidor, it, estilo, anchoCelda) }
        }
        Column(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
            verticalArrangement = Arrangement.spacedBy(e.xs),
        ) {
            categorias.chunked(COLUMNAS_CATEGORIAS).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.xs)) {
                    fila.forEach { categoria ->
                        val ajuste = ajustes[categoria.name] ?: AjusteNombre(estilo.fontSize, ModoNombre.PALABRAS)
                        CeldaCategoria(categoria, enabled, onElegir, ajuste, Modifier.weight(1f))
                    }
                    repeat(COLUMNAS_CATEGORIAS - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Tamaño mínimo del nombre al encogerse (DESIGN.md §3: nada por debajo de 11 sp). */
private val NOMBRE_MINIMO = 11.sp

/** Paso del autoajuste del nombre, de `etiqueta` (13 sp) hacia abajo: 13 · 12,5 · 12 · 11,5 · 11. */
private val PASO_NOMBRE = 0.5.sp

/** Idioma de los cortes con guion del nombre (patrones de partición del español). */
private val IDIOMA_GUION = LocaleList("es-VE")

/** Cómo se pinta el nombre, en el orden en que se prueba (DESIGN.md §4, `CategoriaGrid`). */
private enum class ModoNombre {
    /** 1-2 líneas cortando solo entre palabras (o tras «-» / «/»). */
    PALABRAS,

    /** 1-2 líneas con guion automático en español: la palabra se corta, pero marcada con «-». */
    GUION,

    /** Ni con guion cabe: una línea con «…» al final. */
    ELIPSIS,
}

/** Tamaño y modo elegidos para el nombre de una categoría. */
private data class AjusteNombre(val tamano: TextUnit, val modo: ModoNombre)

/** Estilo con el que se mide y se pinta el nombre en el modo dado. */
private fun estiloNombre(estilo: TextStyle, tamano: TextUnit, modo: ModoNombre): TextStyle =
    if (modo == ModoNombre.GUION) {
        estilo.copy(fontSize = tamano, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph, localeList = IDIOMA_GUION)
    } else {
        estilo.copy(fontSize = tamano)
    }

/**
 * Elige cómo cabe el nombre en `ancho` px (M1: «Supermer/cado» no vuelve a salir). Orden:
 * 1. palabras enteras en 2 líneas, de `estilo.fontSize` a [NOMBRE_MINIMO] en pasos de [PASO_NOMBRE];
 * 2. guion automático en español, otra vez del tamaño mayor al mínimo;
 * 3. una línea con «…».
 * La interlínea del estilo no cambia: el alto de la fila del grid es el mismo con cualquier tamaño.
 */
private fun ajustarNombre(medidor: TextMeasurer, nombre: String, estilo: TextStyle, ancho: Int): AjusteNombre {
    for (modo in listOf(ModoNombre.PALABRAS, ModoNombre.GUION)) {
        var tamano = estilo.fontSize.value
        while (tamano >= NOMBRE_MINIMO.value) {
            val estiloModo = estiloNombre(estilo, tamano.sp, modo)
            val medida = medidor.measure(nombre, estiloModo, maxLines = 2, constraints = Constraints(maxWidth = ancho))
            val cabe = !medida.hasVisualOverflow && when (modo) {
                ModoNombre.PALABRAS -> cortesPartidos(nombre, medida).isEmpty()
                else -> cortesPartidos(nombre, medida).all { linea -> llevaGuion(medidor, nombre, medida, linea, estiloModo) }
            }
            if (cabe) return AjusteNombre(tamano.sp, modo)
            tamano -= PASO_NOMBRE.value
        }
    }
    return AjusteNombre(NOMBRE_MINIMO, ModoNombre.ELIPSIS)
}

/**
 * Líneas que terminan a media palabra: la línea siguiente empieza tras un carácter que no es un espacio ni un corte
 * natural («-», «/»).
 */
private fun cortesPartidos(texto: String, medida: TextLayoutResult): List<Int> =
    (0 until medida.lineCount - 1).filter { linea ->
        val inicio = medida.getLineStart(linea + 1)
        inicio in 1 until texto.length && texto[inicio - 1].let { !it.isWhitespace() && it != '-' && it != '/' }
    }

/**
 * true si la línea partida termina con el guion que dibuja la partición automática. El guion no está en el texto,
 * así que se nota en el ancho: la línea pintada mide al menos medio guion más que su texto solo. Un corte «a la
 * desesperada» (la palabra no tiene punto de partición que quepa) no lo lleva y se rechaza.
 */
private fun llevaGuion(medidor: TextMeasurer, texto: String, medida: TextLayoutResult, linea: Int, estilo: TextStyle): Boolean {
    val trozo = texto.substring(medida.getLineStart(linea), medida.getLineEnd(linea))
    val anchoTrozo = medidor.measure(trozo, estilo, softWrap = false, maxLines = 1).size.width
    val anchoGuion = medidor.measure("-", estilo, softWrap = false, maxLines = 1).size.width
    val anchoLinea = medida.getLineRight(linea) - medida.getLineLeft(linea)
    return anchoLinea >= anchoTrozo + anchoGuion / 2f
}

@Composable
private fun CeldaCategoria(
    categoria: Category,
    enabled: Boolean,
    onElegir: (String) -> Unit,
    ajuste: AjusteNombre,
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
        // M1: una palabra nunca se parte sin guion (el ajuste lo eligió el grid, ver `ajustarNombre`).
        val elipsis = ajuste.modo == ModoNombre.ELIPSIS
        Text(
            categoria.name,
            style = estiloNombre(CuentasTema.tipografia.etiqueta, ajuste.tamano, ajuste.modo),
            color = c.texto,
            maxLines = if (elipsis) 1 else 2,
            softWrap = !elipsis,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
