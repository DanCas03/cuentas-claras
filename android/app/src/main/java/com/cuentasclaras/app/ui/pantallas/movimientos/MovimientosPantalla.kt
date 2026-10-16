@file:OptIn(ExperimentalLayoutApi::class)

package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.fechaLarga
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.nombreMes
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Signo
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.componentes.colorDeCategoria
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento
import com.cuentasclaras.app.ui.vmCon
import kotlin.math.abs

/** Medidas de las filas (DESIGN.md §4 y §6): objetivo táctil holgado y cuadro del emoji. */
private val ALTO_FILA = 64.dp
private val CUADRO_ICONO = 44.dp
private val ICONO_FLECHA = 16.dp
private val ICONO_CHEVRON = 24.dp
private val ICONO_QUITAR = 16.dp
private val ALTO_CHIP_FILTRO = 36.dp
private val EMOJI_FILA = 22.sp
private const val TINTE_CATEGORIA = 0.18f

/** Diccionarios para resolver cuenta, categoría y evento de cada fila sin recorrer las listas. */
private class Catalogos(
    val cuentas: Map<String, Account>,
    val categorias: Map<String, Category>,
    val eventos: Map<String, CalEvent>,
) {
    fun nombreCuenta(id: String?): String = cuentas[id]?.name ?: "?"
    fun moneda(accountId: String): Currency = cuentas[accountId]?.currency ?: Currency.USD
}

/**
 * Movimientos: buscador, filtros por cuenta, categoría y mes (los que llegan por la ruta se ven como chips
 * borrables), lista «sueltas + grupos» con el residuo visual de cada grupo y editor de movimiento.
 * La pestaña «Movs» entra sin filtros (E25); el drill-down desde Inicio entra con categoría y mes puestos.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MovimientosPantalla(nav: NavHostController) {
    val vm = vmCon { c, h -> MovimientosViewModel(c.repo, h) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val filtro = estado.ui.filtro
    val e = CuentasTema.espacios

    val catalogos = remember(estado.cuentas, estado.categorias, estado.eventos) {
        Catalogos(
            estado.cuentas.associateBy { it.id },
            estado.categorias.associateBy { it.id },
            estado.eventos.associateBy { it.id },
        )
    }
    val hoy = remember { todayIso() }
    var busqueda by rememberSaveable { mutableStateOf(filtro.q ?: "") }

    val opcionesCuenta = remember(estado.cuentas) { listOf("") + estado.cuentas.map { it.id } }
    val opcionesCategoria = remember(estado.categorias) { listOf("") + estado.categorias.map { it.id } }
    val opcionesMes = remember(estado.meses) { listOf("") + estado.meses }

    val textoCuenta = { id: String -> if (id.isEmpty()) "Todas las cuentas" else catalogos.cuentas[id]?.name ?: "Cuenta eliminada" }
    val textoCategoria = { id: String ->
        if (id.isEmpty()) "Todas las categorías" else catalogos.categorias[id]?.name ?: "Categoría eliminada"
    }
    val textoMes = { m: String -> if (m.isEmpty()) "Todos los meses" else runCatching { nombreMes(m) }.getOrDefault(m) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
            verticalArrangement = Arrangement.spacedBy(e.s),
        ) {
            item(key = "cabecera") {
                Column {
                    Text(
                        fechaLarga(hoy),
                        style = CuentasTema.tipografia.etiqueta,
                        color = CuentasTema.colores.textoSecundario,
                    )
                    Text(
                        "Movimientos",
                        style = CuentasTema.tipografia.titulo,
                        color = CuentasTema.colores.texto,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "buscar") {
                CampoTexto(
                    valor = busqueda,
                    onCambio = { texto ->
                        busqueda = texto
                        vm.ajustarFiltro { it.copy(q = texto.ifEmpty { null }) }
                    },
                    etiqueta = "Buscar",
                    placeholder = "Buscar en notas…",
                )
            }
            item(key = "filtros") {
                Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(e.s)) {
                        Selector(
                            etiqueta = "Cuenta",
                            opciones = opcionesCuenta,
                            seleccion = filtro.accountId ?: "",
                            texto = textoCuenta,
                            onSeleccion = { id -> vm.ajustarFiltro { it.copy(accountId = id.ifEmpty { null }) } },
                            modifier = Modifier.weight(1f),
                        )
                        Selector(
                            etiqueta = "Mes",
                            opciones = opcionesMes,
                            seleccion = filtro.month ?: "",
                            texto = textoMes,
                            onSeleccion = { m -> vm.ajustarFiltro { it.copy(month = m.ifEmpty { null }) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Selector(
                        etiqueta = "Categoría",
                        opciones = opcionesCategoria,
                        seleccion = filtro.categoryId ?: "",
                        texto = textoCategoria,
                        onSeleccion = { id -> vm.ajustarFiltro { it.copy(categoryId = id.ifEmpty { null }) } },
                    )
                }
            }
            val hayFiltros = !filtro.accountId.isNullOrEmpty() || !filtro.categoryId.isNullOrEmpty() || !filtro.month.isNullOrEmpty()
            if (hayFiltros) {
                item(key = "chips") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(e.s)) {
                        if (!filtro.categoryId.isNullOrEmpty()) {
                            val texto = "Categoría: ${textoCategoria(filtro.categoryId)}"
                            ChipFiltro(texto, "Quitar filtro $texto") { vm.ajustarFiltro { it.copy(categoryId = null) } }
                        }
                        if (!filtro.month.isNullOrEmpty()) {
                            val texto = "Mes: ${textoMes(filtro.month)}"
                            ChipFiltro(texto, "Quitar filtro $texto") { vm.ajustarFiltro { it.copy(month = null) } }
                        }
                        if (!filtro.accountId.isNullOrEmpty()) {
                            val texto = "Cuenta: ${textoCuenta(filtro.accountId)}"
                            ChipFiltro(texto, "Quitar filtro $texto") { vm.ajustarFiltro { it.copy(accountId = null) } }
                        }
                    }
                }
            }

            if (!estado.cargando && estado.items.isEmpty()) {
                item(key = "vacio") { Vacio("🔍", "No hay movimientos con estos filtros") }
            }
            estado.items.forEach { item ->
                when (item) {
                    is ItemMov.Suelta -> item(key = item.t.id) {
                        FilaMovimiento(item.t, catalogos, indentada = false) { vm.editar(item.t) }
                    }
                    is ItemMov.Grupo -> {
                        val abierto = item.gid in estado.ui.expandidos
                        item(key = "grupo-${item.gid}") {
                            FilaGrupo(item, abierto, catalogos) { vm.alternarGrupo(item.gid) }
                        }
                        if (abierto) {
                            items(item.visibles, key = { "miembro-${it.id}" }) { t ->
                                FilaMovimiento(t, catalogos, indentada = true) { vm.editar(t) }
                            }
                        }
                    }
                }
            }
        }

        estado.ui.editando?.let { tx ->
            EditorMovimiento(
                tx = tx,
                categorias = estado.categorias,
                guardando = estado.ui.guardando,
                onCerrar = vm::cerrarEditor,
                onGuardar = { monto, categoria, fecha, nota -> vm.guardarEdicion(tx, monto, categoria, fecha, nota) },
                onEliminar = { vm.eliminar(tx) },
            )
        }
    }
}

/** Chip de filtro activo: se borra tocándolo (ícono de cerrar a la derecha). */
@Composable
private fun ChipFiltro(texto: String, descripcion: String, onQuitar: () -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = ALTO_CHIP_FILTRO)
            .clip(CuentasTema.formas.pildora)
            .background(c.borde)
            .clickable(role = Role.Button, onClickLabel = "Quitar filtro", onClick = onQuitar)
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
            .padding(start = e.m, end = e.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.xs),
    ) {
        Text(
            texto,
            style = CuentasTema.tipografia.etiqueta,
            color = c.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Icon(
            painterResource(R.drawable.ic_cerrar),
            contentDescription = null,
            tint = c.textoSecundario,
            modifier = Modifier.size(ICONO_QUITAR),
        )
    }
}

/**
 * Base de toda fila de la lista: contenedor tonal tocable de ≥ 64 dp. La fila es UN nodo accesible con la
 * descripción dada (empieza por la descripción del movimiento); su contenido visual no se lee aparte.
 */
@Composable
private fun FilaBase(
    descripcion: String,
    modifier: Modifier,
    estadoAccesible: String?,
    onClick: () -> Unit,
    contenido: @Composable RowScope.() -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_FILA)
            .clip(CuentasTema.formas.control)
            .background(c.superficie)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                if (estadoAccesible != null) stateDescription = estadoAccesible
            }
            .padding(horizontal = e.m, vertical = e.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.m),
    ) {
        Row(
            Modifier.weight(1f).clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(e.m),
            content = contenido,
        )
    }
}

@Composable
private fun IconoFila(emoji: String, fondo: Color) {
    Box(
        Modifier.size(CUADRO_ICONO).clip(CuentasTema.formas.control).background(fondo),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = EMOJI_FILA)
    }
}

/** Descripción y meta apiladas; ocupan el ancho que dejan el ícono y el monto. */
@Composable
private fun RowScope.TextoFila(principal: String, meta: String) {
    Column(Modifier.weight(1f)) {
        Text(
            principal,
            style = CuentasTema.tipografia.cuerpo,
            color = CuentasTema.colores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            meta,
            style = CuentasTema.tipografia.etiqueta,
            color = CuentasTema.colores.textoSecundario,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Monto con dirección: flecha `ic_entra` / `ic_sale` + signo + color (nunca solo color). */
@Composable
private fun MontoConDireccion(minor: Long, currency: Currency, signo: Signo) {
    val c = CuentasTema.colores
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs)) {
        Icon(
            painterResource(if (signo == Signo.POSITIVO) R.drawable.ic_entra else R.drawable.ic_sale),
            contentDescription = null,
            tint = if (signo == Signo.POSITIVO) c.ingreso else c.gasto,
            modifier = Modifier.size(ICONO_FLECHA),
        )
        Monto(minor, currency, signo)
    }
}

@Composable
private fun FilaMovimiento(t: Transaction, cat: Catalogos, indentada: Boolean, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val categoria = t.categoryId?.let { cat.categorias[it] }
    val moneda = cat.moneda(t.accountId)
    val descripcion = when {
        t.type == TxType.TRANSFER -> "${cat.nombreCuenta(t.accountId)} → ${cat.nombreCuenta(t.toAccountId)}"
        !t.note.isNullOrEmpty() -> t.note
        categoria != null -> categoria.name
        t.type == TxType.ADJUSTMENT -> "Ajuste de saldo"
        else -> "Sin nota"
    }
    val evento = t.eventId?.let { cat.eventos[it] }
    val meta = buildString {
        append(t.date).append(" · ").append(cat.nombreCuenta(t.accountId))
        if (t.importKey != null) append(" · importado")
        if (evento != null) append(" · 📅 ").append(evento.titulo)
    }
    val accesible = "$descripcion, $meta, " + when (t.type) {
        TxType.INCOME -> "ingreso de ${formatMoney(t.amountMinor, moneda)}"
        TxType.EXPENSE -> "gasto de ${formatMoney(t.amountMinor, moneda)}"
        TxType.TRANSFER -> "transferencia de ${formatMoney(t.amountMinor, moneda)}"
        TxType.ADJUSTMENT -> "ajuste de ${formatMoney(t.amountMinor, moneda)}"
    }
    val emoji = when (t.type) {
        TxType.TRANSFER -> "🔁"
        TxType.ADJUSTMENT -> "🎯"
        else -> categoria?.icon ?: "📦"
    }
    val fondoIcono = if (categoria != null && t.type != TxType.TRANSFER && t.type != TxType.ADJUSTMENT) {
        colorDeCategoria(categoria.color, c.borde).copy(alpha = TINTE_CATEGORIA)
    } else {
        c.borde
    }
    FilaBase(
        descripcion = accesible,
        modifier = if (indentada) Modifier.padding(start = CuentasTema.espacios.l) else Modifier,
        estadoAccesible = null,
        onClick = onClick,
    ) {
        IconoFila(emoji, fondoIcono)
        TextoFila(descripcion, meta)
        when (t.type) {
            TxType.INCOME -> MontoConDireccion(t.amountMinor, moneda, Signo.POSITIVO)
            TxType.EXPENSE -> MontoConDireccion(t.amountMinor, moneda, Signo.NEGATIVO)
            TxType.ADJUSTMENT ->
                MontoConDireccion(t.amountMinor, moneda, if (t.amountMinor >= 0) Signo.POSITIVO else Signo.NEGATIVO)
            TxType.TRANSFER -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs),
            ) {
                Text("↔", style = CuentasTema.tipografia.cifra, color = c.textoSecundario)
                Monto(t.amountMinor, moneda)
            }
        }
    }
}

@Composable
private fun FilaGrupo(g: ItemMov.Grupo, abierto: Boolean, cat: Catalogos, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val categoria = g.grupo?.categoryId?.let { cat.categorias[it] }
    val nota = g.grupo?.nota?.takeIf { it.isNotEmpty() } ?: "Grupo transaccional"
    val meta = "${g.todos.size} movimientos · vuelto" + (categoria?.let { " · ${it.name}" } ?: "")
    val residuoAccesible = if (g.residuos.isEmpty()) {
        "sin residuo"
    } else {
        g.residuos.joinToString("; ") {
            (if (it.residuoMinor >= 0) "residuo a favor de " else "residuo en contra de ") +
                formatMoney(abs(it.residuoMinor), it.currency)
        }
    }
    val giro by animateFloatAsState(if (abierto) 180f else 0f, Movimiento.transicion(Movimiento.MEDIO), label = "giroGrupo")
    FilaBase(
        descripcion = "$nota, $meta, $residuoAccesible",
        modifier = Modifier,
        estadoAccesible = if (abierto) "Expandido" else "Contraído",
        onClick = onClick,
    ) {
        val fondoIcono = if (categoria != null) colorDeCategoria(categoria.color, c.borde).copy(alpha = TINTE_CATEGORIA) else c.borde
        IconoFila(categoria?.icon ?: "🔁", fondoIcono)
        TextoFila(nota, meta)
        Column(horizontalAlignment = Alignment.End) {
            if (g.residuos.isEmpty()) {
                Text("sin residuo", style = CuentasTema.tipografia.etiqueta, color = c.textoSecundario)
            } else {
                g.residuos.forEach { r ->
                    MontoConDireccion(abs(r.residuoMinor), r.currency, if (r.residuoMinor >= 0) Signo.POSITIVO else Signo.NEGATIVO)
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_expandir),
            contentDescription = null,
            tint = c.textoSecundario,
            modifier = Modifier.size(ICONO_CHEVRON).graphicsLayer { rotationZ = giro },
        )
    }
}
