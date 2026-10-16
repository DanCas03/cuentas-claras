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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Currency
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
        if (f.capturaId != null) {
            Banner(
                texto = "Desde una notificación del BNC",
                tipo = TipoBanner.OK,
                accion = { Chip("Descartar", onClick = vm::descartarCaptura, enabled = !f.guardando) },
            )
            // Duplicado inverso: el TXT ya trajo este movimiento. Solo avisa; Daniel decide si registrar.
            if (f.pareceImportada) {
                Banner(texto = "Este movimiento parece ya importado del TXT del BNC.", tipo = TipoBanner.AVISO)
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
                    Box(Modifier.weight(2f), contentAlignment = Alignment.BottomEnd) {
                        Chip(
                            texto = f.nota.trim().ifEmpty { "+ Nota" },
                            onClick = { notaAbierta = !notaAbierta },
                            activo = notaAbierta,
                        )
                    }
                }
                if (notaAbierta) {
                    CampoTexto(
                        valor = f.nota,
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
                CategoriaGrid(
                    categorias = estado.categorias,
                    enabled = !f.guardando,
                    onElegir = vm::guardar,
                )
            }
        }
    }
}
