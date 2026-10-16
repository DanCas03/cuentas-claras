package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.ResiduoMoneda
import com.cuentasclaras.app.domain.TxGroup
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.componentes.ALTO_CAMPO
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.EtiquetaCampo
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.componentes.coloresCampo
import com.cuentasclaras.app.ui.theme.CuentasTema
import kotlin.math.abs

/** Anillo de foco del campo desplegado (el mismo de `Selector`). */
private val ANILLO_FOCO = 2.dp

/** Valor de la opción «Sin categoría (Vuelto)». */
private const val SIN_CATEGORIA = ""

/** Placeholder de la nota del grupo (el de la web). */
private const val EJEMPLO_NOTA = "me pasaron para la leche"

/**
 * Hoja «Agrupar como transaccional» (modal de `MovimientosPage.tsx` 315-345): nota y categoría del vuelto opcionales.
 * La cierra el ViewModel SOLO tras agrupar con éxito; si falla, sigue abierta con lo escrito. «Cancelar» la cierra
 * sin salir del modo selección.
 */
@Composable
fun HojaAgrupar(
    categorias: List<Category>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onAgrupar: (nota: String, categoryId: String) -> Unit,
) {
    val e = CuentasTema.espacios
    var nota by rememberSaveable { mutableStateOf("") }
    var categoriaId by rememberSaveable { mutableStateOf(SIN_CATEGORIA) }

    HojaModal("Agrupar como transaccional", onCerrar = onCerrar) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            TextoAyuda("Los movimientos agrupados no cuentan en tus métricas: solo su vuelto.")
            CampoTexto(nota, { nota = it }, etiqueta = "Nota del grupo (opcional)", placeholder = EJEMPLO_NOTA)
            Column(verticalArrangement = Arrangement.spacedBy(e.xs)) {
                SelectorCategoriaVuelto(
                    etiqueta = "Categoría del vuelto (opcional)",
                    categorias = categorias,
                    seleccion = categoriaId,
                    onSeleccion = { categoriaId = it },
                )
                TextoAyuda("Se aplica al vuelto del grupo solo si el signo coincide; si no, cuenta como Vuelto.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                BotonPrincipal(
                    "Agrupar",
                    onClick = { onAgrupar(nota, categoriaId) },
                    enabled = !guardando,
                    modifier = Modifier.fillMaxWidth(),
                )
                BotonSecundario("Cancelar", onClick = onCerrar, enabled = !guardando, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Editor de un grupo transaccional (`EditorGrupo` de `MovimientosPage.tsx` 472-535): nota y categoría del vuelto.
 * Por cada residuo cuyo signo no casa con el tipo de la categoría elegida avisa de que contará como Vuelto
 * (residuo > 0 = ingreso; 0 o negativo = gasto, como la web). Se cierra SOLO tras guardar con éxito.
 */
@Composable
fun EditorGrupo(
    grupo: TxGroup,
    residuos: List<ResiduoMoneda>,
    categorias: List<Category>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (nota: String, categoryId: String) -> Unit,
) {
    val e = CuentasTema.espacios
    var nota by rememberSaveable(grupo.id) { mutableStateOf(grupo.nota ?: "") }
    var categoriaId by rememberSaveable(grupo.id) { mutableStateOf(grupo.categoryId ?: SIN_CATEGORIA) }

    val elegida = categorias.firstOrNull { it.id == categoriaId }
    val noAplican = if (elegida == null) {
        emptyList()
    } else {
        residuos.filter { r -> (if (r.residuoMinor > 0) CategoryKind.INCOME else CategoryKind.EXPENSE) != elegida.kind }
    }

    HojaModal("Editar grupo", onCerrar = onCerrar) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            CampoTexto(nota, { nota = it }, etiqueta = "Nota del grupo", placeholder = EJEMPLO_NOTA)
            SelectorCategoriaVuelto(
                etiqueta = "Categoría del vuelto",
                categorias = categorias,
                seleccion = categoriaId,
                onSeleccion = { categoriaId = it },
            )
            if (elegida != null) {
                val tipo = if (elegida.kind == CategoryKind.EXPENSE) "gasto" else "ingreso"
                noAplican.forEach { r ->
                    Banner(
                        "Con el residuo actual (${textoResiduo(r)}) esta categoría de $tipo no aplica; contará como Vuelto.",
                        TipoBanner.AVISO,
                    )
                }
            }
            TextoAyuda("Los movimientos del grupo conservan su propia categoría.")
            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                BotonPrincipal(
                    "Guardar",
                    onClick = { onGuardar(nota, categoriaId) },
                    enabled = !guardando,
                    modifier = Modifier.fillMaxWidth(),
                )
                BotonSecundario("Cancelar", onClick = onCerrar, enabled = !guardando, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Residuo con su dirección, en el mismo formato que `Monto(signo)` de la fila del grupo: «+$1.50» / «−Bs 12,50»
 * (signo delante de la moneda, U+2212; DESIGN.md §4, flujo con dirección).
 */
private fun textoResiduo(r: ResiduoMoneda): String =
    (if (r.residuoMinor >= 0) "+" else "−") + formatMoney(abs(r.residuoMinor), r.currency)

@Composable
private fun TextoAyuda(texto: String) {
    Text(texto, style = CuentasTema.tipografia.etiqueta, color = CuentasTema.colores.textoSecundario)
}

/**
 * Selector de la categoría del vuelto: «Sin categoría (Vuelto)» y las categorías en dos secciones, «Gastos» e
 * «Ingresos» (los `optgroup` de la web). Mismo aspecto y accesibilidad que `Selector` (etiqueta encima que abre el
 * menú, relleno de controles, anillo `primario` al abrir, menú en `superficieElevada`); `Selector` no admite
 * encabezados de sección, por eso este campo vive aquí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorCategoriaVuelto(
    etiqueta: String,
    categorias: List<Category>,
    seleccion: String,
    onSeleccion: (String) -> Unit,
) {
    val c = CuentasTema.colores
    var abierto by remember { mutableStateOf(false) }
    val gastos = remember(categorias) { categorias.filter { it.kind == CategoryKind.EXPENSE } }
    val ingresos = remember(categorias) { categorias.filter { it.kind == CategoryKind.INCOME } }
    val texto = { id: String ->
        categorias.firstOrNull { it.id == id }?.let { "${it.icon} ${it.name}" } ?: "Sin categoría (Vuelto)"
    }

    Column(Modifier.fillMaxWidth()) {
        EtiquetaCampo(etiqueta) { abierto = true }
        ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
            TextField(
                value = texto(seleccion),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
                textStyle = CuentasTema.tipografia.cuerpo,
                shape = CuentasTema.formas.control,
                colors = coloresCampo(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = ALTO_CAMPO)
                    .border(ANILLO_FOCO, if (abierto) c.primario else Color.Transparent, CuentasTema.formas.control),
            )
            ExposedDropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                shape = CuentasTema.formas.control,
                containerColor = c.superficieElevada,
            ) {
                val opcion: @Composable (String) -> Unit = { id ->
                    DropdownMenuItem(
                        text = { Text(texto(id), style = CuentasTema.tipografia.cuerpo, color = c.texto) },
                        onClick = {
                            onSeleccion(id)
                            abierto = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
                opcion(SIN_CATEGORIA)
                if (gastos.isNotEmpty()) {
                    EncabezadoSeccion("Gastos")
                    gastos.forEach { opcion(it.id) }
                }
                if (ingresos.isNotEmpty()) {
                    EncabezadoSeccion("Ingresos")
                    ingresos.forEach { opcion(it.id) }
                }
            }
        }
    }
}

/** Rótulo de sección del menú (DESIGN.md §3: `etiqueta` en mayúsculas, tracking 0,12 em, `textoSecundario`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncabezadoSeccion(texto: String) {
    Text(
        texto.uppercase(),
        style = CuentasTema.tipografia.etiqueta.copy(letterSpacing = 0.12.em),
        color = CuentasTema.colores.textoSecundario,
        modifier = Modifier
            .padding(ExposedDropdownMenuDefaults.ItemContentPadding)
            .padding(top = CuentasTema.espacios.s, bottom = CuentasTema.espacios.xs)
            .semantics { heading() },
    )
}
