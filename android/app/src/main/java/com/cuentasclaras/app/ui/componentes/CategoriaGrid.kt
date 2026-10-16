package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.ui.theme.CuentasTema

private const val COLUMNAS_CATEGORIAS = 4

/** Lado del cuadro del emoji de cada categoría. */
private val CUADRO_CATEGORIA = 48.dp

/**
 * Color de una categoría: es dato del usuario (hex de la web), por eso no sale del tema. Si el hex no es
 * válido, usa el respaldo dado.
 */
fun colorDeCategoria(hex: String, respaldo: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(respaldo)

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
    Column(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
        verticalArrangement = Arrangement.spacedBy(e.xs),
    ) {
        categorias.chunked(COLUMNAS_CATEGORIAS).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.xs)) {
                fila.forEach { categoria ->
                    CeldaCategoria(categoria, enabled, onElegir, Modifier.weight(1f))
                }
                repeat(COLUMNAS_CATEGORIAS - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CeldaCategoria(categoria: Category, enabled: Boolean, onElegir: (String) -> Unit, modifier: Modifier) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val tono = colorDeCategoria(categoria.color, c.primario)
    Column(
        modifier
            .clip(CuentasTema.formas.control)
            .clickable(enabled = enabled, role = Role.Button) { onElegir(categoria.id) }
            .padding(vertical = e.xs, horizontal = e.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(CUADRO_CATEGORIA).clip(CuentasTema.formas.control).background(tono.copy(alpha = CuentasTema.medidas.alfaTinteCategoria)),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoria.icon, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.height(e.xs))
        Text(
            categoria.name,
            style = CuentasTema.tipografia.etiqueta,
            color = c.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
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
