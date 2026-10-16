@file:OptIn(ExperimentalLayoutApi::class)

package com.cuentasclaras.app.ui.pantallas.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.EstadoOcurrencia
import com.cuentasclaras.app.domain.LETRAS_DIA
import com.cuentasclaras.app.domain.MontoMoneda
import com.cuentasclaras.app.domain.OcurrenciaVista
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.celdasMes
import com.cuentasclaras.app.domain.descripcionRecurrencia
import com.cuentasclaras.app.domain.diaCorto
import com.cuentasclaras.app.domain.fechaLarga
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.mesAnterior
import com.cuentasclaras.app.domain.mesSiguiente
import com.cuentasclaras.app.domain.monthOf
import com.cuentasclaras.app.domain.nombreMes
import com.cuentasclaras.app.domain.partsOf
import com.cuentasclaras.app.ui.componentes.BotonChevron
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.CuadroCategoria
import com.cuentasclaras.app.ui.componentes.FilaConFinal
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.componentes.colorDeCategoria
import com.cuentasclaras.app.ui.componentes.fondoDeCategoria
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/** Medidas de la cuadrícula y de las filas (DESIGN.md §4 y §6): celdas táctiles holgadas, puntos de 6 dp. */
private val ALTO_CELDA = 56.dp
private val LADO_PUNTO = 6.dp
private val ICONO_CHEVRON = 24.dp
private val TRAZO = 1.dp
private val ANILLO_HOY = 2.dp
private val SEPARACION_PUNTOS = 3.dp
private val RELLENO_ESTADO_H = 10.dp
private val RELLENO_ESTADO_V = 2.dp
private const val MAX_PUNTOS = 3
private const val OPACIDAD_HECHA = 0.4f
private const val TINTE_ESTADO = 0.14f
private const val OPACIDAD_FUERA_DE_MES = 0.4f

/**
 * Calendario: cuadrícula de mes con hasta 3 puntos por día (color de la categoría; hecha atenuada, vencida con
 * anillo, omitida sin punto), lista del día con registrar/omitir/reanudar/quitar y todos los eventos. Crear y
 * editar eventos abre `EditorEvento`.
 */
@Composable
fun CalendarioPantalla(nav: NavHostController) {
    val vm = vmCon { c, h -> CalendarioViewModel(c.repo, h) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    // I1: al volver a la app (quizá al día siguiente, con el proceso vivo) se toma de nuevo la fecha de hoy.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refrescarHoy() }
    val ui = estado.ui
    // D1: el editor lee el borrador de la UI síncrona, no de `estado` (que se calcula en otro hilo y llega atrasado).
    val uiActual by vm.uiActual.collectAsStateWithLifecycle()
    val datos = estado.datos
    val e = CuentasTema.espacios
    val c = CuentasTema.colores

    val categorias = remember(datos) { datos?.categorias?.associateBy { it.id } ?: emptyMap() }
    val cuentas = remember(datos) { datos?.cuentas?.associateBy { it.id } ?: emptyMap() }
    val txs = remember(datos) { datos?.txs?.associateBy { it.id } ?: emptyMap() }
    val eventos = datos?.eventos ?: emptyList()
    val ocurrenciasDelDia = estado.porFecha[ui.diaSel] ?: emptyList()
    var todosAbierto by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
            verticalArrangement = Arrangement.spacedBy(e.m),
        ) {
            item(key = "cabecera") {
                Column {
                    Text(fechaLarga(estado.hoy), style = CuentasTema.tipografia.etiqueta, color = c.textoSecundario)
                    Text(
                        "Calendario",
                        style = CuentasTema.tipografia.titulo,
                        color = c.texto,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "mes") {
                NavegacionMes(
                    mes = ui.mes,
                    esMesActual = ui.mes == monthOf(estado.hoy),
                    onAnterior = { vm.cambiarMes(mesAnterior(ui.mes)) },
                    onSiguiente = { vm.cambiarMes(mesSiguiente(ui.mes)) },
                    onHoy = vm::irAHoy,
                )
            }
            item(key = "cuadricula") {
                Cuadricula(
                    mes = ui.mes,
                    hoy = estado.hoy,
                    diaSel = ui.diaSel,
                    porFecha = estado.porFecha,
                    categorias = categorias,
                    onDia = vm::seleccionarDia,
                )
            }
            item(key = "dia") {
                Text(
                    fechaLarga(ui.diaSel),
                    style = CuentasTema.tipografia.subtitulo,
                    color = c.texto,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (ocurrenciasDelDia.isEmpty()) {
                item(key = "dia-vacio") { Vacio("🗓️", "Nada este día") }
            }
            items(ocurrenciasDelDia.size, key = { "${ocurrenciasDelDia[it].eventId}|${ocurrenciasDelDia[it].date}" }) { i ->
                val o = ocurrenciasDelDia[i]
                FilaOcurrencia(
                    o = o,
                    categoria = categorias[o.evento.categoryId],
                    cuentas = cuentas,
                    txs = txs,
                    guardando = ui.guardando,
                    onRegistrar = { nav.navigate(Rutas.nuevo(eventId = o.eventId, eventDate = o.date)) },
                    onOmitir = { vm.omitir(o) },
                    onReanudar = { vm.reanudar(o) },
                    onQuitar = vm::desasignar,
                )
            }
            item(key = "nuevo") {
                BotonPrincipal("Nuevo evento", onClick = vm::nuevoEvento, enabled = !ui.guardando, modifier = Modifier.fillMaxWidth())
            }
            item(key = "todos-cabecera") {
                CabeceraTodos(eventos.size, todosAbierto) { todosAbierto = !todosAbierto }
            }
            if (todosAbierto) {
                items(eventos.size, key = { "evento-${eventos[it].id}" }) { i ->
                    val ev = eventos[i]
                    FilaEvento(ev, categorias[ev.categoryId], enabled = !ui.guardando) { vm.editarEvento(ev) }
                }
            }
        }

        uiActual.editando?.let { borrador ->
            EditorEvento(
                borrador = borrador,
                cuentas = datos?.cuentas ?: emptyList(),
                categorias = datos?.categorias ?: emptyList(),
                movimientosAsignados = datos?.txs?.count { it.eventId == borrador.id } ?: 0,
                guardando = uiActual.guardando,
                onCambio = vm::cambiarBorrador,
                onCerrar = vm::cerrarEditor,
                onGuardar = { vm.guardarBorrador(alGuardar = vm::cerrarEditor) },
                onEliminar = { vm.eliminarEvento(borrador.id, alEliminar = vm::cerrarEditor) },
            )
        }
    }
}

/** «‹ Octubre 2026 ›» y el chip «hoy» cuando el mes visible no es el actual. Sin tope de meses. */
@Composable
private fun NavegacionMes(
    mes: String,
    esMesActual: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onHoy: () -> Unit,
) {
    val c = CuentasTema.colores
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s)) {
        BotonChevron("Mes anterior", haciaLaIzquierda = true, onClick = onAnterior)
        Text(
            nombreMes(mes),
            style = CuentasTema.tipografia.subtitulo,
            color = c.texto,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        BotonChevron("Mes siguiente", haciaLaIzquierda = false, onClick = onSiguiente)
        if (!esMesActual) Chip("hoy", onClick = onHoy)
    }
}

/** Cabecera de letras y 6 semanas × 7 días (`celdasMes`). */
@Composable
private fun Cuadricula(
    mes: String,
    hoy: String,
    diaSel: String,
    porFecha: Map<String, List<OcurrenciaVista>>,
    categorias: Map<String, Category>,
    onDia: (String) -> Unit,
) {
    val c = CuentasTema.colores
    val celdas = remember(mes) { celdasMes(mes) }
    Column {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            LETRAS_DIA.forEach { letra ->
                Text(
                    letra,
                    style = CuentasTema.tipografia.etiqueta,
                    color = c.textoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        celdas.chunked(7).forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                semana.forEach { celda ->
                    // Las omitidas no se pintan como punto: el usuario ya las canceló a propósito.
                    val delDia = (porFecha[celda.iso] ?: emptyList()).filter { it.estado != EstadoOcurrencia.OMITIDA }
                    CeldaDia(
                        iso = celda.iso,
                        enMes = celda.enMes,
                        esHoy = celda.iso == hoy,
                        seleccionado = celda.iso == diaSel,
                        ocurrencias = delDia,
                        categorias = categorias,
                        onClick = { onDia(celda.iso) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CeldaDia(
    iso: String,
    enMes: Boolean,
    esHoy: Boolean,
    seleccionado: Boolean,
    ocurrencias: List<OcurrenciaVista>,
    categorias: Map<String, Category>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val forma = CuentasTema.formas.control
    val estados = buildList {
        if (esHoy) add("hoy")
        if (seleccionado) add("seleccionado")
        if (ocurrencias.isNotEmpty()) add(if (ocurrencias.size == 1) "1 evento" else "${ocurrencias.size} eventos")
    }.joinToString(", ")
    Column(
        modifier
            .padding(TRAZO)
            .heightIn(min = ALTO_CELDA)
            .clip(forma)
            .background(if (seleccionado) c.primario else Color.Transparent)
            .then(if (esHoy && !seleccionado) Modifier.border(ANILLO_HOY, c.primario, forma) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = fechaLarga(iso)
                if (estados.isNotEmpty()) stateDescription = estados
            }
            .padding(vertical = e.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(e.xs),
    ) {
        val tinta = when {
            seleccionado -> c.sobrePrimario
            enMes -> c.texto
            else -> c.textoSecundario
        }
        Text(
            partsOf(iso).d.toString(),
            style = CuentasTema.tipografia.cuerpo,
            color = tinta,
            modifier = Modifier.alpha(if (enMes || seleccionado) 1f else OPACIDAD_FUERA_DE_MES).clearAndSetSemantics { },
        )
        Row(
            Modifier.heightIn(min = LADO_PUNTO).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(SEPARACION_PUNTOS),
        ) {
            ocurrencias.take(MAX_PUNTOS).forEach { o ->
                Punto(o, categorias[o.evento.categoryId]?.color, seleccionado)
            }
        }
        if (ocurrencias.size > MAX_PUNTOS) {
            Text("+${ocurrencias.size - MAX_PUNTOS}", style = CuentasTema.tipografia.etiqueta, color = tinta, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/**
 * Punto del color de la categoría: hecha atenuada, vencida con anillo (el estado no depende solo del color).
 * También lo usa la tira de «Esta semana» en Inicio (T30), para que ambos calendarios se vean igual.
 */
@Composable
internal fun Punto(o: OcurrenciaVista, hex: String?, sobreSeleccion: Boolean) {
    val c = CuentasTema.colores
    val color = if (hex != null) colorDeCategoria(hex, c.textoSecundario) else c.textoSecundario
    // Sobre la celda seleccionada (fondo `primario`) el punto lleva un borde de `sobrePrimario` para no perderse.
    val base = Modifier.size(LADO_PUNTO).clip(CircleShape).background(color)
    val conAnillo = when {
        o.estado == EstadoOcurrencia.VENCIDA -> base.border(TRAZO, c.gasto, CircleShape)
        sobreSeleccion -> base.border(TRAZO, c.sobrePrimario, CircleShape)
        else -> base
    }
    Box(conAnillo.alpha(if (o.estado == EstadoOcurrencia.HECHA) OPACIDAD_HECHA else 1f))
}

@Composable
private fun FilaOcurrencia(
    o: OcurrenciaVista,
    categoria: Category?,
    cuentas: Map<String, Account>,
    txs: Map<String, Transaction>,
    guardando: Boolean,
    onRegistrar: () -> Unit,
    onOmitir: () -> Unit,
    onReanudar: () -> Unit,
    onQuitar: (String) -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val monedaDe = { accountId: String? -> cuentas[accountId]?.currency ?: Currency.USD }
    val cifras = if (o.estado == EstadoOcurrencia.HECHA) {
        o.asignadoPorMoneda
    } else {
        listOfNotNull(o.evento.amountMinor?.let { MontoMoneda(monedaDe(o.evento.accountId), it) })
    }
    val cuenta = o.evento.accountId?.let { cuentas[it]?.name }

    Column(
        Modifier.fillMaxWidth().clip(CuentasTema.formas.control).background(c.superficie).padding(e.m),
        verticalArrangement = Arrangement.spacedBy(e.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(e.m)) {
            // Sin categoría, `ic_calendario` (el 📅 de la web es un ícono de interfaz: vector, DESIGN.md §8).
            CuadroCategoria(categoria?.icon, R.drawable.ic_calendario, fondoDeCategoria(categoria?.color), tintaVector = c.textoSecundario)
            // El monto y el estado bajan juntos bajo el título cuando no caben a su lado (FilaConFinal).
            FilaConFinal(
                texto = {
                    Column {
                        Text(
                            o.evento.titulo,
                            style = CuentasTema.tipografia.cuerpo,
                            color = if (o.estado == EstadoOcurrencia.HECHA) c.textoSecundario else c.texto,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            diaCorto(o.date) + (cuenta?.let { " · $it" } ?: ""),
                            style = CuentasTema.tipografia.etiqueta,
                            color = c.textoSecundario,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                final = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(e.xs)) {
                        // Una cifra por moneda, apiladas: unidas en una línea («Bs … · $…») no cabían en estrecho.
                    cifras.forEach { m -> Monto(m.totalMinor, m.currency) }
                        EtiquetaEstado(o.estado)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(e.s), verticalArrangement = Arrangement.spacedBy(e.xs)) {
            if (o.estado == EstadoOcurrencia.PENDIENTE || o.estado == EstadoOcurrencia.VENCIDA) {
                Chip(
                    "registrar", onClick = onRegistrar, enabled = !guardando,
                    modifier = Modifier.semantics { contentDescription = "Registrar ${o.evento.titulo}" },
                )
                Chip(
                    "omitir", onClick = onOmitir, enabled = !guardando,
                    modifier = Modifier.semantics { contentDescription = "Omitir ${o.evento.titulo}" },
                )
            }
            if (o.estado == EstadoOcurrencia.OMITIDA) {
                Chip(
                    "reanudar", onClick = onReanudar, enabled = !guardando,
                    modifier = Modifier.semantics { contentDescription = "Reanudar ${o.evento.titulo}" },
                )
            }
            if (o.estado == EstadoOcurrencia.HECHA) {
                o.txIds.forEach { txId ->
                    val tx = txs[txId]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(e.xs)) {
                        Chip(
                            if (tx != null) "${tx.note ?: "movimiento"} · ${formatMoney(tx.amountMinor, monedaDe(tx.accountId))}" else "movimiento",
                        )
                        Chip(
                            "quitar", onClick = { onQuitar(txId) }, enabled = !guardando,
                            modifier = Modifier.semantics { contentDescription = "Quitar movimiento de ${o.evento.titulo}" },
                        )
                    }
                }
            }
            if (o.fueraDeRegla) Chip("fuera de la regla")
        }
    }
}

/** Estado en texto + tinte: nunca solo color. Omitida va tachada y en `textoSecundario`. También en «Esta semana». */
@Composable
internal fun EtiquetaEstado(estado: EstadoOcurrencia) {
    val c = CuentasTema.colores
    val (texto, color) = when (estado) {
        EstadoOcurrencia.PENDIENTE -> "pendiente" to c.texto
        EstadoOcurrencia.HECHA -> "hecha" to c.ingreso
        EstadoOcurrencia.VENCIDA -> "vencida" to c.gasto
        EstadoOcurrencia.OMITIDA -> "omitida" to c.textoSecundario
    }
    val fondo = if (estado == EstadoOcurrencia.PENDIENTE || estado == EstadoOcurrencia.OMITIDA) c.borde else color.copy(alpha = TINTE_ESTADO)
    Text(
        texto,
        style = CuentasTema.tipografia.etiqueta,
        color = color,
        textDecoration = if (estado == EstadoOcurrencia.OMITIDA) TextDecoration.LineThrough else null,
        modifier = Modifier.clip(CuentasTema.formas.pildora).background(fondo).padding(horizontal = RELLENO_ESTADO_H, vertical = RELLENO_ESTADO_V),
    )
}

/** Cabecera plegable de «Todos los eventos (n)». */
@Composable
private fun CabeceraTodos(cantidad: Int, abierto: Boolean, onAlternar: () -> Unit) {
    val c = CuentasTema.colores
    Row(
        Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(CuentasTema.formas.control)
            .clickable(role = Role.Button, onClick = onAlternar)
            .semantics { stateDescription = if (abierto) "Expandido" else "Contraído" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Todos los eventos ($cantidad)",
            style = CuentasTema.tipografia.subtitulo,
            color = c.texto,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_expandir),
            contentDescription = null,
            tint = c.textoSecundario,
            modifier = Modifier.size(ICONO_CHEVRON).graphicsLayer { rotationZ = if (abierto) 180f else 0f },
        )
    }
}

@Composable
private fun FilaEvento(evento: CalEvent, categoria: Category?, enabled: Boolean, onEditar: () -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Row(
        Modifier.fillMaxWidth().clip(CuentasTema.formas.control).background(c.superficie).padding(horizontal = e.m, vertical = e.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.m),
    ) {
        // V17: el mismo cuadro tintado que la lista del día (antes, el emoji suelto sin cuadro).
        CuadroCategoria(categoria?.icon, R.drawable.ic_calendario, fondoDeCategoria(categoria?.color), tintaVector = c.textoSecundario)
        Column(Modifier.weight(1f)) {
            Text(evento.titulo, style = CuentasTema.tipografia.cuerpo, color = c.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(descripcionRecurrencia(evento), style = CuentasTema.tipografia.etiqueta, color = c.textoSecundario)
        }
        Chip(
            "editar", onClick = onEditar, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Editar ${evento.titulo}" },
        )
    }
}
