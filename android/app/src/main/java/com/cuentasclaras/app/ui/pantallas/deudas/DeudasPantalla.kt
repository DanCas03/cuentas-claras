package com.cuentasclaras.app.ui.pantallas.deudas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.DialogoConfirmacion
import com.cuentasclaras.app.ui.componentes.FilaConFinal
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento
import com.cuentasclaras.app.ui.vmCon

/** Lado del chevron del historial (DESIGN.md §4: íconos de 24 dp en acciones). */
private val ICONO_ACCION = 24.dp

private val PESTANAS = listOf(DebtDirection.POR_PAGAR, DebtDirection.POR_COBRAR)

/** Qué deuda se está editando en la hoja: la existente, o una nueva con dirección y cuenta iniciales. */
private data class Edicion(val original: Debt?, val direccion: DebtDirection, val cuentaId: String)

/** Confirmación pendiente (AlertDialog): cerrar o eliminar una deuda. */
private sealed interface Confirmacion {
    val deuda: Debt

    data class Cerrar(override val deuda: Debt) : Confirmacion
    data class Eliminar(override val deuda: Debt) : Confirmacion
}

/**
 * Deudas: pestañas Por pagar / Por cobrar, pendientes con editar, eliminar y cierre (movimiento real, transaccional
 * e idempotente), historial plegable y el editor en una hoja.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DeudasPantalla(nav: NavHostController) {
    val vm = vmCon { c, _ -> DeudasViewModel(c.repo) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    val pestana = estado.ui.pestana
    val porPagar = pestana == DebtDirection.POR_PAGAR
    val guardando = estado.ui.guardando

    var edicion by remember { mutableStateOf<Edicion?>(null) }
    var confirmacion by remember { mutableStateOf<Confirmacion?>(null) }
    var historialAbierto by remember { mutableStateOf(false) }

    // A diferencia de la web (que muestra "?" y USD para una cuenta archivada), aquí se resuelve con todas las cuentas.
    fun monedaDe(accountId: String): Currency = estado.todasLasCuentas.firstOrNull { it.id == accountId }?.currency ?: Currency.USD
    fun nombreCuenta(accountId: String): String = estado.todasLasCuentas.firstOrNull { it.id == accountId }?.name ?: "?"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.m),
    ) {
        item(key = "titulo") {
            Text(
                "Deudas",
                style = t.titulo,
                color = c.texto,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "pestanas") {
            Segmentado(
                opciones = listOf("Por pagar", "Por cobrar"),
                seleccion = PESTANAS.indexOf(pestana),
                onSeleccion = { vm.cambiarPestana(PESTANAS[it]) },
            )
        }
        if (!porPagar) {
            item(key = "ayuda") {
                Text(
                    "Lo que te deben es informativo: no suma al patrimonio hasta cobrarse.",
                    style = t.etiqueta,
                    color = c.textoSecundario,
                )
            }
        }
        if (!estado.cargando && estado.pendientes.isEmpty()) {
            item(key = "vacio") {
                Vacio("🤝", "Sin deudas ${if (porPagar) "por pagar" else "por cobrar"}.", Modifier.fillMaxWidth())
            }
        }
        items(estado.pendientes, key = { it.id }) { deuda ->
            Tarjeta {
                Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                    FilaConFinal(
                        texto = { Text(deuda.concepto, style = t.cuerpo, color = c.texto) },
                        final = { Monto(deuda.amountMinor, monedaDe(deuda.accountId)) },
                        separacion = e.s,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${nombreCuenta(deuda.accountId)} · registrada el ${deuda.createdIso.take(10)}",
                        style = t.etiqueta,
                        color = c.textoSecundario,
                    )
                    BotonSecundario(
                        if (deuda.direction == DebtDirection.POR_PAGAR) "Marcar pagada" else "Marcar cobrada",
                        onClick = { confirmacion = Confirmacion.Cerrar(deuda) },
                        enabled = !guardando,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.s)) {
                        BotonSecundario(
                            "Editar",
                            onClick = { edicion = Edicion(deuda, deuda.direction, deuda.accountId) },
                            enabled = !guardando,
                            modifier = Modifier.weight(1f),
                        )
                        BotonSecundario(
                            "Eliminar",
                            onClick = { confirmacion = Confirmacion.Eliminar(deuda) },
                            enabled = !guardando,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item(key = "nueva") {
            BotonPrincipal(
                "Nueva deuda ${if (porPagar) "por pagar" else "por cobrar"}",
                onClick = {
                    val primera = estado.cuentas.firstOrNull()
                    if (primera == null) Avisos.avisar("No hay cuentas") else edicion = Edicion(null, pestana, primera.id)
                },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (estado.cerradas.isNotEmpty()) {
            item(key = "historial") {
                Tarjeta {
                    Column {
                        EncabezadoHistorial(estado.cerradas.size, historialAbierto) { historialAbierto = !historialAbierto }
                        if (historialAbierto) {
                            estado.cerradas.forEach { deuda ->
                                FilaCerrada(
                                    deuda = deuda,
                                    moneda = monedaDe(deuda.accountId),
                                    habilitada = !guardando,
                                    onEliminar = { confirmacion = Confirmacion.Eliminar(deuda) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    edicion?.let { ed ->
        EditorDeuda(
            original = ed.original,
            direccionInicial = ed.direccion,
            cuentaInicial = ed.cuentaId,
            cuentas = estado.cuentas,
            guardando = guardando,
            onCerrar = { edicion = null },
            onGuardar = { direction, concepto, cuentaId, montoTxt, alGuardar ->
                vm.guardar(ed.original, direction, concepto, cuentaId, montoTxt, alGuardar)
            },
        )
    }

    confirmacion?.let { pedida ->
        val deuda = pedida.deuda
        val cerrar = { confirmacion = null }
        when (pedida) {
            is Confirmacion.Cerrar -> {
                val porPagarDeuda = deuda.direction == DebtDirection.POR_PAGAR
                DialogoConfirmacion(
                    titulo = if (porPagarDeuda) "Marcar pagada" else "Marcar cobrada",
                    texto = "¿Marcar «${deuda.concepto}» como ${if (porPagarDeuda) "pagada" else "cobrada"}? " +
                        "Se creará ${if (porPagarDeuda) "el gasto" else "el ingreso"} en ${nombreCuenta(deuda.accountId)}.",
                    textoConfirmar = "Sí, marcar",
                    peligro = false,
                    habilitado = !guardando,
                    onConfirmar = { cerrar(); vm.marcarCerrada(deuda) },
                    onCancelar = cerrar,
                )
            }
            is Confirmacion.Eliminar -> DialogoConfirmacion(
                titulo = "Eliminar deuda",
                texto = if (deuda.status == DebtStatus.CERRADA) {
                    "¿Eliminar esta deuda cerrada? Su movimiento NO se borra."
                } else {
                    "¿Eliminar esta deuda pendiente?"
                },
                textoConfirmar = "Sí, eliminar",
                habilitado = !guardando,
                onConfirmar = { cerrar(); vm.eliminar(deuda) },
                onCancelar = cerrar,
            )
        }
    }
}

/** Encabezado plegable «Historial (n)»: toda la fila es el objetivo táctil y anuncia si está abierto. */
@Composable
private fun EncabezadoHistorial(cantidad: Int, abierto: Boolean, alternar: () -> Unit) {
    val c = CuentasTema.colores
    val giro by animateFloatAsState(if (abierto) 180f else 0f, Movimiento.transicion(Movimiento.MEDIO), label = "giroHistorial")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CuentasTema.medidas.tactilMinimo)
            .clickable(role = Role.Button, onClick = alternar)
            .semantics { stateDescription = if (abierto) "Expandido" else "Contraído" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s),
    ) {
        Text("Historial ($cantidad)", style = CuentasTema.tipografia.subtitulo, color = c.texto, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_expandir),
            contentDescription = null,
            tint = c.textoSecundario,
            modifier = Modifier.size(ICONO_ACCION).graphicsLayer { rotationZ = giro },
        )
    }
}

/** Deuda cerrada: «concepto · fecha de cierre», monto y «eliminar» (el movimiento de cierre se conserva). */
@Composable
private fun FilaCerrada(deuda: Debt, moneda: Currency, habilitada: Boolean, onEliminar: () -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Row(
        Modifier.fillMaxWidth().heightIn(min = CuentasTema.medidas.tactilMinimo),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.s),
    ) {
        FilaConFinal(
            texto = {
                Text("${deuda.concepto} · ${deuda.settledIso}", style = CuentasTema.tipografia.cuerpo, color = c.textoSecundario)
            },
            final = { Monto(deuda.amountMinor, moneda) },
            separacion = e.s,
            modifier = Modifier.weight(1f),
        )
        Chip(
            "eliminar",
            onClick = onEliminar,
            enabled = habilitada,
            modifier = Modifier.semantics { contentDescription = "Eliminar ${deuda.concepto}" },
        )
    }
}
