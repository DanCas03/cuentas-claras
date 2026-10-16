package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Formulario «Ajustar» (AjusteForm.tsx): cuadra el saldo de una cuenta con el saldo real. El VM y su `salir` los gestiona NuevoPantalla. */
@Composable
fun AjustarFormulario(vm: AjustarViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()

    val c = CuentasTema.colores
    val cuenta = estado.cuenta
    val delta = estado.delta
    Column(Modifier, verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.m)) {
        Selector(
            etiqueta = "Cuenta",
            opciones = estado.cuentas,
            seleccion = cuenta,
            texto = ::etiquetaCuenta,
            onSeleccion = { elegida -> vm.cambiar { it.copy(cuentaId = elegida.id) } },
        )
        if (cuenta != null) {
            Text(
                buildAnnotatedString {
                    append("Saldo según la app: ")
                    withStyle(CuentasTema.tipografia.cifra.toSpanStyle()) { append(formatMoney(estado.actual, cuenta.currency)) }
                },
                style = CuentasTema.tipografia.cuerpo,
                color = c.texto,
            )
        }
        CampoTexto(
            valor = estado.f.realTxt,
            onCambio = { v -> vm.cambiar { it.copy(realTxt = v) } },
            etiqueta = "Saldo real actual",
            placeholder = "0,00",
            teclado = KeyboardType.Decimal,
        )
        if (cuenta != null && delta != null && delta != 0L) {
            Banner(
                "${if (estado.esSnapshot) "Variación/rendimiento" else "Diferencia"}: ${formatMoney(delta, cuenta.currency)}",
                if (delta > 0) TipoBanner.OK else TipoBanner.AVISO,
            )
        }
        BotonPrincipal("Ajustar saldo", onClick = vm::guardar, enabled = estado.puedeGuardar)
    }
}
