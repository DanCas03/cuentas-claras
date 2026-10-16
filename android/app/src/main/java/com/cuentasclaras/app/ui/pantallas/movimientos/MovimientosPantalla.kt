@file:OptIn(ExperimentalLayoutApi::class)

package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.cuentasclaras.app.ui.componentes.BotonPeligro
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.DialogoConfirmacion
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
private val ICONO_FILA = 24.dp
private val ICONO_QUITAR = 16.dp
private val ALTO_CHIP_FILTRO = 36.dp
private val EMOJI_FILA = 22.sp

/**
 * Fila seleccionada: tinte `primario` al 14 % (el mismo del estado activo de la barra inferior, del chip sugerido y
 * de la paleta elegida en Ajustes) + contorno `primario` de 1 dp + visto en el cuadro del ícono (no solo color).
 */
private const val TINTE_SELECCION = 0.14f
private val TRAZO_SELECCION = 1.dp

/** Filas que no se pueden agrupar, en modo selección (opacidad de deshabilitado, DESIGN.md §4). */
private const val OPACIDAD_NO_AGRUPABLE = 0.38f

/** Acción de la pulsación larga (y su acción accesible equivalente, sin gesto). */
private const val ACCION_SELECCIONAR = "Seleccionar"

/** Estado de una fila suelta respecto al modo selección. */
private enum class EnSeleccion { FUERA, SELECCIONADA, LIBRE, NO_AGRUPABLE }

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
    val seleccion = estado.ui.seleccion
    val guardando = estado.ui.guardando
    var grupoADesagrupar by remember { mutableStateOf<String?>(null) }
    // Alto de la barra contextual, para reservar ese espacio al final de la lista y que no tape la última fila.
    var altoBarra by remember { mutableStateOf(0.dp) }
    val densidad = LocalDensity.current

    // Atrás del sistema sale del modo selección antes que de la pantalla.
    BackHandler(enabled = seleccion != null) { vm.cancelarSeleccion() }

    val catalogos = remember(estado.cuentas, estado.categorias, estado.eventos) {
        Catalogos(
            estado.cuentas.associateBy { it.id },
            estado.categorias.associateBy { it.id },
            estado.eventos.associateBy { it.id },
        )
    }
    // I1: la pantalla puede seguir viva de un día para otro; al volver a la app se toma de nuevo la fecha de hoy.
    var hoy by remember { mutableStateOf(todayIso()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hoy = todayIso() }
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
                item(key = "vacio") { Vacio("🗂️", "No hay movimientos que coincidan.") }
            }
            estado.items.forEach { item ->
                when (item) {
                    is ItemMov.Suelta -> item(key = item.t.id) {
                        val t = item.t
                        val enSeleccion = when {
                            seleccion == null -> EnSeleccion.FUERA
                            t.id in seleccion -> EnSeleccion.SELECCIONADA
                            esAgrupable(t) -> EnSeleccion.LIBRE
                            else -> EnSeleccion.NO_AGRUPABLE
                        }
                        FilaMovimiento(
                            t,
                            catalogos,
                            enSeleccion = enSeleccion,
                            onClick = { if (seleccion != null) vm.alternarSeleccion(t) else vm.editar(t) },
                            // Solo una fila suelta y fuera del modo selección inicia la selección.
                            onLongClick = if (seleccion == null) ({ vm.iniciarSeleccion(t) }) else null,
                        )
                    }
                    is ItemMov.Grupo -> {
                        val abierto = item.gid in estado.ui.expandidos
                        item(key = "grupo-${item.gid}") {
                            FilaGrupo(item, abierto, catalogos) { vm.alternarGrupo(item.gid) }
                        }
                        if (abierto) {
                            items(item.visibles, key = { "miembro-${it.id}" }) { t ->
                                MiembroDeGrupo(
                                    t,
                                    catalogos,
                                    enSeleccion = if (seleccion == null) EnSeleccion.FUERA else EnSeleccion.NO_AGRUPABLE,
                                    guardando = guardando,
                                    onClick = { if (seleccion != null) vm.alternarSeleccion(t) else vm.editar(t) },
                                    onQuitar = { vm.quitarDelGrupo(t.id) },
                                )
                            }
                            item(key = "acciones-${item.gid}") {
                                AccionesDeGrupo(
                                    guardando = guardando,
                                    hayGrupo = item.grupo != null,
                                    haySeleccion = seleccion != null,
                                    onEditar = { vm.editarGrupo(item.grupo) },
                                    onAnadir = { vm.anadirAlGrupo(item.gid) },
                                    onDesagrupar = { grupoADesagrupar = item.gid },
                                )
                            }
                        }
                    }
                }
            }
            if (seleccion != null) {
                item(key = "espacio-barra") { Spacer(Modifier.height(altoBarra)) }
            }
        }

        if (seleccion != null) {
            val n = seleccion.size
            val anadiendo = estado.ui.destinoGrupoId != null
            BarraSeleccion(
                texto = if (anadiendo) "Añadir al grupo ($n)" else "Agrupar como transaccional ($n)",
                habilitado = !guardando && n >= (if (anadiendo) 1 else 2),
                guardando = guardando,
                onAgrupar = vm::pedirAgrupar,
                onCancelar = vm::cancelarSeleccion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { altoBarra = with(densidad) { it.height.toDp() } },
            )
        }

        if (estado.ui.hojaAgrupar) {
            HojaAgrupar(
                categorias = estado.categorias,
                guardando = guardando,
                onCerrar = vm::cerrarHojaAgrupar,
                onAgrupar = vm::confirmarAgrupar,
            )
        }

        estado.ui.editandoGrupo?.let { g ->
            EditorGrupo(
                grupo = g,
                residuos = estado.residuosEditandoGrupo,
                categorias = estado.categorias,
                guardando = guardando,
                onCerrar = { vm.editarGrupo(null) },
                onGuardar = { nota, categoria -> vm.guardarGrupo(g.id, nota, categoria) },
            )
        }

        grupoADesagrupar?.let { gid ->
            ConfirmarDesagrupar(
                guardando = guardando,
                onConfirmar = {
                    grupoADesagrupar = null
                    vm.desagruparGrupo(gid)
                },
                onDescartar = { grupoADesagrupar = null },
            )
        }

        estado.ui.editando?.let { tx ->
            EditorMovimiento(
                tx = tx,
                categorias = estado.categorias,
                cuentas = estado.cuentas,
                eventos = estado.eventos,
                txsAsignadas = estado.txsAsignadas,
                guardando = estado.ui.guardando,
                onCerrar = vm::cerrarEditor,
                onGuardar = { monto, categoria, fecha, nota, ocurrencia ->
                    vm.guardarEdicion(tx, monto, categoria, fecha, nota, ocurrencia)
                },
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
 *
 * `onLongClick` (pulsación larga) inicia la selección; `combinedClickable` la expone además como acción accesible
 * con la etiqueta «Seleccionar», así que se puede iniciar sin el gesto. Seleccionada: tinte y contorno `primario`.
 * Atenuada: fila que no se puede agrupar mientras dura el modo selección.
 */
@Composable
private fun FilaBase(
    descripcion: String,
    modifier: Modifier,
    estadoAccesible: String?,
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    seleccionada: Boolean = false,
    atenuada: Boolean = false,
    contenido: @Composable RowScope.() -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val forma = CuentasTema.formas.control
    val tinte by animateColorAsState(
        if (seleccionada) c.primario.copy(alpha = TINTE_SELECCION) else Color.Transparent,
        Movimiento.transicion(Movimiento.MEDIO),
        label = "tinteSeleccion",
    )
    val contorno by animateColorAsState(
        if (seleccionada) c.primario else Color.Transparent,
        Movimiento.transicion(Movimiento.MEDIO),
        label = "contornoSeleccion",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_FILA)
            .alpha(if (atenuada) OPACIDAD_NO_AGRUPABLE else 1f)
            .clip(forma)
            .background(c.superficie)
            .background(tinte)
            .border(TRAZO_SELECCION, contorno, forma)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongClick != null) ACCION_SELECCIONAR else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
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

/**
 * Cuadro del ícono de una fila: el emoji de la categoría (dato del usuario) o, si no hay, un vector `ic_*`
 * (DESIGN.md §8). Sin descripción: la fila entera ya se lee con su descripción (tipo de movimiento incluido).
 */
@Composable
private fun IconoFila(fondo: Color, emoji: String?, vector: Int, marcada: Boolean = false) {
    val c = CuentasTema.colores
    Box(
        Modifier.size(CUADRO_ICONO).clip(CuentasTema.formas.control).background(if (marcada) c.primario else fondo),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            // Fila seleccionada: visto en `sobrePrimario` (la selección no depende solo del color).
            Icon(painterResource(R.drawable.ic_ok), contentDescription = null, tint = c.sobrePrimario, modifier = Modifier.size(ICONO_FILA))
        } else if (emoji != null) {
            Text(emoji, fontSize = EMOJI_FILA)
        } else {
            Icon(painterResource(vector), contentDescription = null, tint = CuentasTema.colores.texto, modifier = Modifier.size(ICONO_FILA))
        }
    }
}

/** Clave del ícono en línea `ic_calendario` dentro de la meta. */
private const val ICONO_EVENTO = "evento"

/**
 * Descripción y meta apiladas; ocupan el ancho que dejan el ícono y el monto. Con `evento`, la meta termina en
 * « · [ic_calendario] título» (📅 en la web; ícono de interfaz, DESIGN.md §8).
 */
@Composable
private fun RowScope.TextoFila(principal: String, meta: String, evento: String? = null) {
    val etiqueta = CuentasTema.tipografia.etiqueta
    val tinta = CuentasTema.colores.textoSecundario
    Column(Modifier.weight(1f)) {
        Text(
            principal,
            style = CuentasTema.tipografia.cuerpo,
            color = CuentasTema.colores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildAnnotatedString {
                append(meta)
                if (evento != null) {
                    append(" · ")
                    appendInlineContent(ICONO_EVENTO, "evento")
                    append(" ")
                    append(evento)
                }
            },
            style = etiqueta,
            color = tinta,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            inlineContent = mapOf(
                ICONO_EVENTO to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                    Icon(painterResource(R.drawable.ic_calendario), contentDescription = null, tint = tinta, modifier = Modifier.fillMaxSize())
                },
            ),
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
private fun FilaMovimiento(
    t: Transaction,
    cat: Catalogos,
    enSeleccion: EnSeleccion,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
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
    }
    // La fila es un solo nodo accesible: el ícono del evento se lee como «evento {título}».
    val metaAccesible = if (evento != null) "$meta · evento ${evento.titulo}" else meta
    val accesible = "$descripcion, $metaAccesible, " + when (t.type) {
        TxType.INCOME -> "ingreso de ${formatMoney(t.amountMinor, moneda)}"
        TxType.EXPENSE -> "gasto de ${formatMoney(t.amountMinor, moneda)}"
        TxType.TRANSFER -> "transferencia de ${formatMoney(t.amountMinor, moneda)}"
        TxType.ADJUSTMENT -> "ajuste de ${formatMoney(t.amountMinor, moneda)}"
    }
    // La web pinta 🔁 (transferencia), 🎯 (ajuste) y 📦 (sin categoría): aquí son vectores (DESIGN.md §8).
    val emoji = if (t.type == TxType.TRANSFER || t.type == TxType.ADJUSTMENT) null else categoria?.icon
    val vector = when (t.type) {
        TxType.TRANSFER -> R.drawable.ic_transferir
        TxType.ADJUSTMENT -> R.drawable.ic_ajuste_saldo
        else -> R.drawable.ic_sin_categoria
    }
    val fondoIcono = if (categoria != null && t.type != TxType.TRANSFER && t.type != TxType.ADJUSTMENT) {
        colorDeCategoria(categoria.color, c.borde).copy(alpha = CuentasTema.medidas.alfaTinteCategoria)
    } else {
        c.borde
    }
    // En modo selección la fila dice su estado y qué hace el toque; fuera de él, el toque edita.
    val estadoAccesible = when (enSeleccion) {
        EnSeleccion.FUERA -> null
        EnSeleccion.SELECCIONADA -> "Seleccionado"
        EnSeleccion.LIBRE -> "No seleccionado"
        EnSeleccion.NO_AGRUPABLE -> "No se puede agrupar"
    }
    val etiquetaToque = when (enSeleccion) {
        EnSeleccion.FUERA -> "Editar"
        EnSeleccion.SELECCIONADA -> "Quitar de la selección"
        EnSeleccion.LIBRE -> ACCION_SELECCIONAR
        EnSeleccion.NO_AGRUPABLE -> null
    }
    FilaBase(
        descripcion = accesible,
        modifier = modifier,
        estadoAccesible = estadoAccesible,
        onClick = onClick,
        onClickLabel = etiquetaToque,
        onLongClick = onLongClick,
        seleccionada = enSeleccion == EnSeleccion.SELECCIONADA,
        atenuada = enSeleccion == EnSeleccion.NO_AGRUPABLE,
    ) {
        IconoFila(fondoIcono, emoji, vector, marcada = enSeleccion == EnSeleccion.SELECCIONADA)
        TextoFila(descripcion, meta, evento?.titulo)
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
        val fondoIcono = if (categoria != null) {
            colorDeCategoria(categoria.color, c.borde).copy(alpha = CuentasTema.medidas.alfaTinteCategoria)
        } else {
            c.borde
        }
        // Sin categoría, la web pinta 🔁: aquí `ic_grupo` (DESIGN.md §8).
        IconoFila(fondoIcono, categoria?.icon, R.drawable.ic_grupo)
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

/**
 * Miembro de un grupo expandido: su fila (indentada; no inicia selección con pulsación larga) y el botón
 * «Quitar del grupo». Un grupo que baja de 2 miembros se disuelve.
 */
@Composable
private fun MiembroDeGrupo(
    t: Transaction,
    cat: Catalogos,
    enSeleccion: EnSeleccion,
    guardando: Boolean,
    onClick: () -> Unit,
    onQuitar: () -> Unit,
) {
    val c = CuentasTema.colores
    Row(
        Modifier.fillMaxWidth().padding(start = CuentasTema.espacios.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs),
    ) {
        FilaMovimiento(t, cat, enSeleccion = enSeleccion, onClick = onClick, onLongClick = null, modifier = Modifier.weight(1f))
        IconButton(onClick = onQuitar, enabled = !guardando) {
            Icon(
                painterResource(R.drawable.ic_cerrar),
                contentDescription = "Quitar del grupo",
                tint = if (guardando) c.textoSecundario.copy(alpha = OPACIDAD_NO_AGRUPABLE) else c.textoSecundario,
                modifier = Modifier.size(ICONO_FILA),
            )
        }
    }
}

/**
 * Acciones de un grupo expandido, debajo de sus miembros: «Añadir movimientos» (no mientras ya hay una selección),
 * «Editar grupo» y «Desagrupar» (pide confirmación). Ninguna es la acción principal de la pantalla.
 */
@Composable
private fun AccionesDeGrupo(
    guardando: Boolean,
    hayGrupo: Boolean,
    haySeleccion: Boolean,
    onEditar: () -> Unit,
    onAnadir: () -> Unit,
    onDesagrupar: () -> Unit,
) {
    val e = CuentasTema.espacios
    Column(
        Modifier.fillMaxWidth().padding(start = e.l),
        verticalArrangement = Arrangement.spacedBy(e.s),
    ) {
        BotonSecundario(
            "Añadir movimientos",
            onClick = onAnadir,
            enabled = !guardando && !haySeleccion,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.s)) {
            BotonSecundario("Editar grupo", onClick = onEditar, enabled = !guardando && hayGrupo, modifier = Modifier.weight(1f))
            BotonPeligro("Desagrupar", onClick = onDesagrupar, enabled = !guardando, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Barra contextual del modo selección, anclada abajo dentro de la pantalla (encima de la barra de navegación):
 * la acción principal (agrupar o añadir al grupo, con el número de seleccionados) y «Cancelar». Plano
 * `superficieElevada` sobre la lista, sin sombra (profundidad tonal, DESIGN.md §6).
 */
@Composable
private fun BarraSeleccion(
    texto: String,
    habilitado: Boolean,
    guardando: Boolean,
    onAgrupar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier,
) {
    val e = CuentasTema.espacios
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = e.m, vertical = e.s)
            .clip(CuentasTema.formas.tarjeta)
            .background(CuentasTema.colores.superficieElevada)
            .padding(e.m),
        verticalArrangement = Arrangement.spacedBy(e.s),
    ) {
        BotonPrincipal(texto, onClick = onAgrupar, enabled = habilitado, modifier = Modifier.fillMaxWidth())
        BotonSecundario("Cancelar", onClick = onCancelar, enabled = !guardando, modifier = Modifier.fillMaxWidth())
    }
}

/** Confirmación de «Desagrupar» (`window.confirm` de la web). Botones distintos de los de la lista para `tocar`. */
@Composable
private fun ConfirmarDesagrupar(guardando: Boolean, onConfirmar: () -> Unit, onDescartar: () -> Unit) {
    DialogoConfirmacion(
        titulo = "¿Desagrupar?",
        texto = "Todos los movimientos vuelven a contarse normal.",
        textoConfirmar = "Sí, desagrupar",
        habilitado = !guardando,
        onConfirmar = onConfirmar,
        onCancelar = onDescartar,
    )
}
