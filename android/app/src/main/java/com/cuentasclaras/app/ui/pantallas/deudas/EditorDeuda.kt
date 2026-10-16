package com.cuentasclaras.app.ui.pantallas.deudas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.theme.CuentasTema

private val DIRECCIONES = listOf(DebtDirection.POR_PAGAR, DebtDirection.POR_COBRAR)

internal fun simboloMoneda(m: Currency): String = if (m == Currency.VES) "Bs" else "$"

/**
 * Hoja de alta/edición de deuda. `original` es la deuda que se edita (null = nueva); `direccionInicial` y
 * `cuentaInicial` rellenan una deuda nueva. Solo se cierra desde `alGuardar` (guardado correcto) o «Cancelar».
 */
@Composable
fun EditorDeuda(
    original: Debt?,
    direccionInicial: DebtDirection,
    cuentaInicial: String,
    cuentas: List<Account>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (direction: DebtDirection, concepto: String, cuentaId: String, montoTxt: String, alGuardar: () -> Unit) -> Unit,
) {
    val e = CuentasTema.espacios
    val esNueva = original == null
    var direction by remember { mutableStateOf(original?.direction ?: direccionInicial) }
    var concepto by remember { mutableStateOf(original?.concepto ?: "") }
    var cuentaId by remember { mutableStateOf(original?.accountId ?: cuentaInicial) }
    var montoTxt by remember { mutableStateOf(if (original == null) "" else montoEditable(original.amountMinor)) }
    val moneda = cuentas.firstOrNull { it.id == cuentaId }?.currency ?: Currency.USD

    fun cambiarCuenta(nuevaId: String) {
        val monedaNueva = cuentas.firstOrNull { it.id == nuevaId }?.currency ?: Currency.USD
        if (monedaNueva != moneda && montoTxt.isNotEmpty()) {
            montoTxt = ""
            Avisos.avisar("La cuenta elegida usa otra moneda: vuelve a escribir el monto")
        }
        cuentaId = nuevaId
    }

    HojaModal(titulo = if (esNueva) "Nueva deuda" else "Editar deuda", onCerrar = onCerrar) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            Segmentado(
                opciones = listOf("Debo", "Me deben"),
                seleccion = DIRECCIONES.indexOf(direction),
                onSeleccion = { direction = DIRECCIONES[it] },
            )
            CampoTexto(
                valor = concepto,
                onCambio = { concepto = it },
                etiqueta = "Concepto (persona o descripción)",
                placeholder = "Juan — préstamo almuerzo",
            )
            Selector(
                etiqueta = "Cuenta para ${if (direction == DebtDirection.POR_PAGAR) "pagarla" else "cobrarla"} (define la moneda)",
                opciones = cuentas,
                seleccion = cuentas.firstOrNull { it.id == cuentaId },
                texto = { "${it.name} (${simboloMoneda(it.currency)})" },
                onSeleccion = { cambiarCuenta(it.id) },
            )
            CampoTexto(
                valor = montoTxt,
                onCambio = { montoTxt = it },
                etiqueta = "Monto en ${simboloMoneda(moneda)} (coma decimal)",
                placeholder = "0,00",
                teclado = KeyboardType.Decimal,
            )
            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                BotonPrincipal(
                    "Guardar deuda",
                    onClick = { onGuardar(direction, concepto, cuentaId, montoTxt, onCerrar) },
                    enabled = !guardando,
                    modifier = Modifier.fillMaxWidth(),
                )
                BotonSecundario("Cancelar", onClick = onCerrar, enabled = !guardando, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
