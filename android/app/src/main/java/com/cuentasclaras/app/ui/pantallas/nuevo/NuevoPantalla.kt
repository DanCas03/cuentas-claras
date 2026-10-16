package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.CategoriaGrid
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Teclado
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/** Símbolo de moneda como lo escribe la web en los selectores: «Bs» o «$». */
internal fun simbolo(moneda: Currency): String = if (moneda == Currency.VES) "Bs" else "$"

/** Texto de una cuenta en los selectores: «Nombre (Bs)» / «Nombre ($)». */
internal fun etiquetaCuenta(cuenta: Account): String = "${cuenta.name} (${simbolo(cuenta.currency)})"

/** Tras guardar, la web hace `navigate('/')`: se vuelve a Inicio (si no estuviera en la pila, se navega a él). */
internal fun volverAInicio(nav: NavHostController) {
    if (!nav.popBackStack(Rutas.INICIO, inclusive = false)) nav.navigate(Rutas.INICIO)
}

/**
 * Registro rápido: Gasto / Ingreso (cifra + teclado propio + categoría que guarda), Transferir y Ajustar.
 * Insets (E26): el Scaffold raíz ya aplicó las barras del sistema en su `relleno`; aquí se marcan como
 * consumidas y solo se añade `imePadding()` para el teclado del sistema (nota, monto de transferencia/ajuste).
 */
@Composable
fun NuevoPantalla(nav: NavHostController) {
    val vm = vmCon { c, h -> NuevoViewModel(c.repo, h) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.salir.collect { volverAInicio(nav) } }

    val e = CuentasTema.espacios
    val f = estado.formulario
    Column(
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.m),
    ) {
        Segmentado(
            opciones = listOf("Gasto", "Ingreso", "Transferir", "Ajustar"),
            seleccion = f.modo.ordinal,
            onSeleccion = { vm.cambiarModo(Modo.entries[it]) },
        )
        when (f.modo) {
            Modo.TRANSFERIR -> TransferirFormulario(nav)
            Modo.AJUSTAR -> AjustarFormulario(nav)
            Modo.GASTO, Modo.INGRESO -> {
                val c = CuentasTema.colores
                val cuenta = estado.cuenta
                val minor = estado.minor
                Selector(
                    etiqueta = "Cuenta",
                    opciones = estado.cuentas,
                    seleccion = cuenta,
                    texto = ::etiquetaCuenta,
                    onSeleccion = { vm.cambiarCuenta(it.id) },
                )
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
                Teclado(onTecla = vm::tecla)
                CampoTexto(
                    valor = f.nota,
                    onCambio = vm::cambiarNota,
                    etiqueta = "Nota (opcional)",
                    placeholder = "¿En qué fue?",
                )
                Text(
                    "Toca la categoría para guardar",
                    style = CuentasTema.tipografia.etiqueta,
                    color = c.textoSecundario,
                )
                CategoriaGrid(
                    categorias = estado.categorias,
                    enabled = !f.guardando,
                    onElegir = vm::guardar,
                )
            }
        }
    }
}
