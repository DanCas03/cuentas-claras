package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.nuevoId
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.theme.CuentasTema

private val MONEDAS = listOf(Currency.USD, Currency.VES)

private fun textoMoneda(m: Currency): String = if (m == Currency.USD) "USD ($)" else "Bolívares (Bs)"

private val TIPOS = listOf(AccountKind.BANK, AccountKind.WALLET, AccountKind.SNAPSHOT, AccountKind.CASH)

private fun textoTipo(t: AccountKind): String = when (t) {
    AccountKind.BANK -> "Banco"
    AccountKind.WALLET -> "Billetera"
    AccountKind.SNAPSHOT -> "Solo saldo (ajustes manuales)"
    AccountKind.CASH -> "Efectivo"
}

private fun cuentaNueva() = Account(
    id = nuevoId(), name = "", currency = Currency.USD, kind = AccountKind.WALLET,
    isSavings = false, archived = false, enDisposicion = true,
)

/** Cuentas: lista con chips de moneda, ahorro y archivada; alta y edición en una hoja. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CuentasSeccion(
    cuentas: List<Account>,
    guardando: Boolean,
    onGuardar: (Account) -> Boolean,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    var editando by remember { mutableStateOf<Account?>(null) }

    Tarjeta(titulo = "Cuentas") {
        Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
            cuentas.forEach { cuenta ->
                FilaConAccion(
                    accion = "editar",
                    descripcionAccion = "Editar ${cuenta.name}",
                    onAccion = { editando = cuenta },
                ) {
                    Text(
                        cuenta.name,
                        style = CuentasTema.tipografia.cuerpo,
                        color = if (cuenta.archived) c.textoSecundario else c.texto,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(e.xs),
                        verticalArrangement = Arrangement.spacedBy(e.xs),
                    ) {
                        Chip(if (cuenta.currency == Currency.VES) "Bs" else "$")
                        if (cuenta.isSavings) Chip("ahorro")
                        if (cuenta.archived) Chip("archivada")
                    }
                }
            }
            BotonSecundario("Nueva cuenta", onClick = { editando = cuentaNueva() }, modifier = Modifier.fillMaxWidth())
        }
    }

    editando?.let { cuenta ->
        HojaCuenta(
            inicial = cuenta,
            esNueva = cuentas.none { it.id == cuenta.id },
            guardando = guardando,
            onCerrar = { editando = null },
            onGuardar = { if (onGuardar(it)) editando = null },
        )
    }
}

@Composable
private fun HojaCuenta(
    inicial: Account,
    esNueva: Boolean,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (Account) -> Unit,
) {
    val e = CuentasTema.espacios
    var cuenta by remember(inicial.id) { mutableStateOf(inicial) }
    HojaModal(titulo = if (esNueva) "Nueva cuenta" else "Editar cuenta", onCerrar = onCerrar) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            CampoTexto(valor = cuenta.name, onCambio = { cuenta = cuenta.copy(name = it) }, etiqueta = "Nombre")
            Selector(
                etiqueta = "Moneda (no convierte movimientos existentes)",
                opciones = MONEDAS,
                seleccion = cuenta.currency,
                texto = ::textoMoneda,
                onSeleccion = { cuenta = cuenta.copy(currency = it) },
            )
            Selector(
                etiqueta = "Tipo",
                opciones = TIPOS,
                seleccion = cuenta.kind,
                texto = ::textoTipo,
                onSeleccion = { cuenta = cuenta.copy(kind = it) },
            )
            Column {
                Interruptor(
                    texto = "Cuenta de ahorro (no cuenta como disposición)",
                    marcado = cuenta.isSavings,
                    onCambio = { cuenta = cuenta.copy(isSavings = it) },
                )
                Interruptor(
                    texto = "A disposición (cuenta en la vista «A disposición» del patrimonio)",
                    marcado = cuenta.enDisposicion ?: !cuenta.isSavings,
                    onCambio = { cuenta = cuenta.copy(enDisposicion = it) },
                )
                Interruptor(
                    texto = "Archivada (oculta del dashboard y formularios)",
                    marcado = cuenta.archived,
                    onCambio = { cuenta = cuenta.copy(archived = it) },
                )
            }
            AccionesDeHoja(
                textoGuardar = "Guardar cuenta",
                guardando = guardando,
                onGuardar = { onGuardar(cuenta) },
                onCancelar = onCerrar,
            )
        }
    }
}

/** Fila con texto y un interruptor; toda la fila es el objetivo táctil (≥ 48 dp). */
@Composable
private fun Interruptor(texto: String, marcado: Boolean, onCambio: (Boolean) -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_TACTIL)
            .toggleable(value = marcado, role = Role.Switch, onValueChange = onCambio)
            .padding(vertical = e.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.m),
    ) {
        Text(texto, style = CuentasTema.tipografia.cuerpo, color = c.texto, modifier = Modifier.weight(1f))
        Switch(
            checked = marcado,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.sobrePrimario,
                checkedTrackColor = c.primario,
                checkedBorderColor = c.primario,
                uncheckedThumbColor = c.textoSecundario,
                uncheckedTrackColor = c.borde,
                uncheckedBorderColor = c.textoSecundario,
            ),
        )
    }
}
