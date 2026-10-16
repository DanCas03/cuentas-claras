package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.fechaCorta
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.CategoriaGrid
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Teclado
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/** Símbolo de moneda como lo escribe la web en los selectores: «Bs» o «$». */
internal fun simbolo(moneda: Currency): String = if (moneda == Currency.VES) "Bs" else "$"

/** Texto de una cuenta en los selectores: «Nombre (Bs)» / «Nombre ($)». */
internal fun etiquetaCuenta(cuenta: Account): String = "${cuenta.name} (${simbolo(cuenta.currency)})"

/**
 * Tras guardar, la web hace `navigate('/')`: se vuelve a Inicio. Si Inicio no estuviera en la pila, se navega a
 * él sacando `nuevo` de la pila.
 */
internal fun volverAInicio(nav: NavHostController) {
    if (!nav.popBackStack(Rutas.INICIO, inclusive = false)) {
        nav.navigate(Rutas.INICIO) { popUpTo(nav.graph.id) { inclusive = true } }
    }
}

/**
 * Registro rápido: Gasto / Ingreso (cifra + teclado propio + categoría que guarda), Transferir y Ajustar.
 *
 * Esta pantalla prioriza el grid de categorías (restricción 6: ≤ 3 toques): sin cabecera ni rótulo sobre el
 * grid, la cuenta y la nota comparten fila (nota como chip «+ Nota» que despliega el campo) y el teclado es
 * compacto (DESIGN.md §1 y §4). Los tres ViewModels viven aquí, y aquí se recogen los tres `salir`, para que
 * cambiar de modo durante un guardado no deje el evento de salida sin recoger.
 *
 * Insets (E26): el Scaffold raíz ya aplicó las barras del sistema en su `relleno`; aquí se marcan como
 * consumidas y solo se añade `imePadding()` para el teclado del sistema.
 */
@Composable
fun NuevoPantalla(nav: NavHostController) {
    val vm = vmCon { c, h -> NuevoViewModel(c.repo, c.capturas, h) }
    val vmTransferir = vmCon { c, _ -> TransferirViewModel(c.repo) }
    val vmAjustar = vmCon { c, _ -> AjustarViewModel(c.repo) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    // D1: la nota se lee del formulario síncrono, no de `estado` (que se calcula en otro hilo y llega atrasado).
    val formulario by vm.formularioActual.collectAsStateWithLifecycle()
    // Con launchSingleTop la entrada se reutiliza con argumentos nuevos: el prellenado sigue al `capturaId` de la ruta.
    val entrada by nav.currentBackStackEntryAsState()
    val capturaRuta = entrada?.arguments?.getString("capturaId")
    LaunchedEffect(capturaRuta) { vm.abrirCaptura(capturaRuta) }
    LaunchedEffect(Unit) { vm.salir.collect { volverAInicio(nav) } }
    LaunchedEffect(Unit) { vmTransferir.salir.collect { volverAInicio(nav) } }
    LaunchedEffect(Unit) { vmAjustar.salir.collect { volverAInicio(nav) } }

    val e = CuentasTema.espacios
    val f = estado.formulario
    Column(
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = e.m, vertical = e.s),
        verticalArrangement = Arrangement.spacedBy(e.s),
    ) {
        // V6: la captura solo se registra desde Gasto o Ingreso. Guardar una transferencia o un ajuste no la consume
        // (sigue pendiente en Inicio), así que en esos modos el banner y «Descartar» no se muestran.
        if (f.capturaId != null && (f.modo == Modo.GASTO || f.modo == Modo.INGRESO)) {
            Banner(
                texto = "Desde una notificación del BNC",
                tipo = TipoBanner.OK,
                accion = { Chip("Descartar", onClick = vm::descartarCaptura, enabled = !f.guardando) },
            )
            // Duplicado inverso: el TXT ya trajo este movimiento. Solo avisa; Daniel decide si registrar.
            if (f.pareceImportada) {
                Banner(texto = "Este movimiento parece ya importado del TXT del BNC.", tipo = TipoBanner.AVISO)
            }
            // M4: sin la cuenta BNC activa no se prellena ninguna; el aviso se va al elegir una.
            if (f.cuentaBncNoActiva && estado.cuenta == null) {
                Banner(texto = "La cuenta BNC está archivada o no existe: elige la cuenta.", tipo = TipoBanner.AVISO)
            }
        }
        Segmentado(
            opciones = listOf("Gasto", "Ingreso", "Transferir", "Ajustar"),
            seleccion = f.modo.ordinal,
            onSeleccion = { vm.cambiarModo(Modo.entries[it]) },
        )
        when (f.modo) {
            Modo.TRANSFERIR -> TransferirFormulario(vmTransferir)
            Modo.AJUSTAR -> AjustarFormulario(vmAjustar)
            Modo.GASTO, Modo.INGRESO -> {
                val c = CuentasTema.colores
                val cuenta = estado.cuenta
                val minor = estado.minor
                var notaAbierta by rememberSaveable { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(e.s),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Selector(
                        etiqueta = "Cuenta",
                        opciones = estado.cuentas,
                        seleccion = cuenta,
                        texto = ::etiquetaCuenta,
                        onSeleccion = { vm.cambiarCuenta(it.id) },
                        modifier = Modifier.weight(3f),
                    )
                    // V26: el chip ocupa 48 dp táctiles y el campo 56 dp; `espacios.xs` abajo lo centra con el campo.
                    Box(Modifier.weight(2f).padding(bottom = e.xs), contentAlignment = Alignment.BottomEnd) {
                        Chip(
                            texto = formulario.nota.trim().ifEmpty { "+ Nota" },
                            onClick = { notaAbierta = !notaAbierta },
                            activo = notaAbierta,
                        )
                    }
                }
                if (notaAbierta) {
                    CampoTexto(
                        valor = formulario.nota,
                        onCambio = vm::cambiarNota,
                        etiqueta = "Nota (opcional)",
                        placeholder = "¿En qué fue?",
                        autoFoco = true,
                    )
                }
                val cifra = if (minor != null && minor > 0 && cuenta != null) {
                    formatMoney(if (f.modo == Modo.GASTO) -minor else minor, cuenta.currency)
                } else {
                    null
                }
                Text(
                    text = cifra ?: "—",
                    style = CuentasTema.tipografia.cifraGrande,
                    color = when {
                        cifra == null -> c.textoSecundario
                        f.modo == Modo.GASTO -> c.gasto
                        else -> c.ingreso
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { if (cifra == null) contentDescription = "Sin monto" },
                )
                Teclado(onTecla = vm::tecla, compacto = true)
                // Restricción 6: sin eventos aplicables no hay fila y el camino de ≤ 3 toques no cambia.
                if (estado.chips.isNotEmpty()) {
                    FilaChipsEvento(estado.chips, enabled = !f.guardando, onTocar = vm::tocarChip)
                }
                CategoriaGrid(
                    categorias = estado.categorias,
                    enabled = !f.guardando,
                    onElegir = vm::guardar,
                )
            }
        }
    }
}

/** Texto del chip de evento (`NuevoPage.tsx`): «💼 Quincena · 15 oct · vencida», con «✕» si está elegido. */
private fun textoChipEvento(c: NuevoViewModel.ChipEvento): String =
    "${c.icono} ${c.evento.titulo} · ${fechaCorta(c.o.date)}" +
        (if (c.vencida) " · vencida" else "") +
        (if (c.activo) " ✕" else "")

/**
 * Chips de evento en UNA línea con desplazamiento horizontal (compacta: suma una sola fila de 48 dp táctiles sobre
 * el grid). Activo = `primario` con «✕» para desmarcar; sugerido = tinte y contorno `primario` (DESIGN.md §4).
 */
@Composable
private fun FilaChipsEvento(
    chips: List<NuevoViewModel.ChipEvento>,
    enabled: Boolean,
    onTocar: (NuevoViewModel.ChipEvento) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { c ->
            key(c.o.eventId, c.o.date) {
                Chip(
                    texto = textoChipEvento(c),
                    onClick = { onTocar(c) },
                    activo = c.activo,
                    enabled = enabled,
                    sugerido = c.sugerido,
                    modifier = Modifier.semantics {
                        when {
                            c.activo -> stateDescription = "Evento elegido, toca para quitarlo"
                            c.sugerido -> stateDescription = "Evento sugerido"
                        }
                    },
                )
            }
        }
    }
}
