package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.nuevoId
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.componentes.colorDeCategoria
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Color por defecto de una categoría nueva (el de la web). */
private const val COLOR_NUEVA = "#6b7280"

/**
 * Colores ofrecidos al elegir: los de las categorías de fábrica (datos del usuario, no tokens del tema) más el
 * gris por defecto. Son 16; el campo hex permite cualquier otro.
 */
private val COLORES_CATEGORIA = listOf(
    "#4ade80", "#fbbf24", "#60a5fa", "#f87171", "#facc15", "#94a3b8", "#a78bfa", "#f472b6",
    "#22d3ee", "#fb923c", "#9ca3af", "#38bdf8", "#34d399", "#cbd5e1", "#c084fc", COLOR_NUEVA,
)

private val REGEX_HEX = Regex("^#[0-9a-fA-F]{6}$")

/** Diámetro del círculo de color dentro de su objetivo táctil, y grosor del anillo de la elegida. */
private val LADO_CIRCULO_COLOR = 32.dp
private val GROSOR_ANILLO_COLOR = 2.dp

private val TIPOS_CATEGORIA = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME)

private fun textoTipoCategoria(k: CategoryKind): String = if (k == CategoryKind.EXPENSE) "Gasto" else "Ingreso"

private fun categoriaNueva() = Category(nuevoId(), "", "📦", COLOR_NUEVA, CategoryKind.EXPENSE)

/** Presupuesto inicial del campo: con punto decimal como la web, sin depender del idioma del teléfono. */
private fun presupuestoInicial(c: Category): String {
    val minor = c.monthlyBudgetMinor
    return if (minor == null || minor == 0L) "" else String.format(java.util.Locale.ROOT, "%.2f", minor / 100.0)
}

/** Categorías: emoji, nombre, tipo y presupuesto; alta y edición (color, emoji y presupuesto en USD) en una hoja. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriasSeccion(
    categorias: List<Category>,
    guardando: Boolean,
    onGuardar: (Category, String, alGuardar: () -> Unit) -> Unit,
) {
    val e = CuentasTema.espacios
    var editando by remember { mutableStateOf<Category?>(null) }

    Tarjeta(titulo = "Categorías") {
        Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
            categorias.forEach { categoria ->
                FilaConAccion(
                    accion = "editar",
                    descripcionAccion = "Editar ${categoria.name}",
                    onAccion = { editando = categoria },
                ) {
                    Text(
                        "${categoria.icon} ${categoria.name}",
                        style = CuentasTema.tipografia.cuerpo,
                        color = CuentasTema.colores.texto,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(e.xs),
                        verticalArrangement = Arrangement.spacedBy(e.xs),
                    ) {
                        Chip(if (categoria.kind == CategoryKind.EXPENSE) "gasto" else "ingreso")
                        val presupuesto = categoria.monthlyBudgetMinor
                        if (presupuesto != null && presupuesto != 0L) {
                            Chip("presup. ${formatMoney(presupuesto, Currency.USD)}")
                        }
                    }
                }
            }
            BotonSecundario("Nueva categoría", onClick = { editando = categoriaNueva() }, modifier = Modifier.fillMaxWidth())
        }
    }

    editando?.let { categoria ->
        HojaCategoria(
            inicial = categoria,
            guardando = guardando,
            onCerrar = { editando = null },
            onGuardar = { cambiada, presupuesto -> onGuardar(cambiada, presupuesto) { editando = null } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HojaCategoria(
    inicial: Category,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (Category, String) -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    var categoria by remember(inicial.id) { mutableStateOf(inicial) }
    var hex by remember(inicial.id) { mutableStateOf(inicial.color) }
    var presupuesto by remember(inicial.id) { mutableStateOf(presupuestoInicial(inicial)) }
    val hexValido = REGEX_HEX.matches(hex)

    HojaModal(titulo = "Categoría", onCerrar = onCerrar) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            CampoTexto(valor = categoria.name, onCambio = { categoria = categoria.copy(name = it) }, etiqueta = "Nombre")
            CampoTexto(valor = categoria.icon, onCambio = { categoria = categoria.copy(icon = it) }, etiqueta = "Icono (emoji)")
            Column {
                Text(
                    "Color",
                    style = CuentasTema.tipografia.etiqueta,
                    color = c.textoSecundario,
                    modifier = Modifier.padding(bottom = e.xs),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(e.s), verticalArrangement = Arrangement.spacedBy(e.s)) {
                    COLORES_CATEGORIA.forEach { muestra ->
                        MuestraColor(
                            hex = muestra,
                            elegida = hexValido && muestra.equals(hex, ignoreCase = true),
                            onClick = {
                                hex = muestra
                                categoria = categoria.copy(color = muestra)
                            },
                        )
                    }
                }
            }
            CampoTexto(
                valor = hex,
                onCambio = {
                    hex = it
                    if (REGEX_HEX.matches(it)) categoria = categoria.copy(color = it)
                },
                etiqueta = "Color (hex)",
                placeholder = COLOR_NUEVA,
            )
            if (!hexValido) Banner("Color inválido: usa el formato #RRGGBB.", TipoBanner.ERROR)
            Selector(
                etiqueta = "Tipo",
                opciones = TIPOS_CATEGORIA,
                seleccion = categoria.kind,
                texto = ::textoTipoCategoria,
                onSeleccion = { categoria = categoria.copy(kind = it) },
            )
            CampoTexto(
                valor = presupuesto,
                onCambio = { presupuesto = it },
                etiqueta = "Presupuesto mensual en $ (vacío = sin presupuesto)",
                placeholder = "ej. 50.00",
                teclado = KeyboardType.Decimal,
            )
            AccionesDeHoja(
                textoGuardar = "Guardar categoría",
                guardando = guardando,
                onGuardar = {
                    if (hexValido) onGuardar(categoria, presupuesto) else Avisos.avisar("Color inválido: usa el formato #RRGGBB.")
                },
                onCancelar = onCerrar,
            )
        }
    }
}

/** Círculo de color de 32 dp dentro de un objetivo táctil de 48 dp; la elegida lleva anillo y visto. */
@Composable
private fun MuestraColor(hex: String, elegida: Boolean, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val color = colorDeCategoria(hex, c.borde)
    // El visto usa el más oscuro de `fondo` / `texto` sobre colores claros y el más claro sobre colores oscuros.
    val oscuroDelTema = if (c.fondo.luminance() < c.texto.luminance()) c.fondo else c.texto
    val claroDelTema = if (c.fondo.luminance() < c.texto.luminance()) c.texto else c.fondo
    val tintaVisto = if (color.luminance() > 0.5f) oscuroDelTema else claroDelTema
    Box(
        Modifier
            .size(ALTO_TACTIL)
            .clip(CircleShape)
            .selectable(selected = elegida, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Color $hex" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(LADO_CIRCULO_COLOR)
                .clip(CircleShape)
                .background(color)
                .border(GROSOR_ANILLO_COLOR, if (elegida) c.texto else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (elegida) {
                Icon(
                    painterResource(R.drawable.ic_ok),
                    contentDescription = null,
                    tint = tintaVisto,
                    modifier = Modifier.size(CuentasTema.espacios.m),
                )
            }
        }
    }
}
