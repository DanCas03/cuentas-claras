package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.textoTasa
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Formulario «Transferir» (TransferForm.tsx): origen, destino, monto, tasa si cruza de moneda, nota. El VM y su `salir` los gestiona NuevoPantalla. */
@Composable
fun TransferirFormulario(vm: TransferirViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()

    val f = estado.f
    Column(Modifier, verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.m)) {
        Selector(
            etiqueta = "Desde",
            opciones = estado.cuentas.filter { it.id != f.destinoId },
            seleccion = estado.origen,
            texto = ::etiquetaCuenta,
            onSeleccion = { cuenta -> vm.cambiar { it.copy(origenId = cuenta.id) } },
        )
        Selector(
            etiqueta = "Hacia",
            opciones = estado.cuentas.filter { it.id != f.origenId },
            seleccion = estado.destino,
            texto = ::etiquetaCuenta,
            onSeleccion = { cuenta -> vm.cambiar { it.copy(destinoId = cuenta.id) } },
        )
        CampoTexto(
            valor = f.montoTxt,
            onCambio = { v -> vm.cambiar { it.copy(montoTxt = v) } },
            etiqueta = "Monto en ${estado.origen?.let { simbolo(it.currency) } ?: "$"}",
            placeholder = "0,00",
            teclado = KeyboardType.Decimal,
        )
        if (estado.cruza) {
            val propuesta = estado.tasaDelDia
            CampoTexto(
                valor = f.tasaTxt,
                onCambio = { v -> vm.cambiar { it.copy(tasaTxt = v) } },
                etiqueta = "Tasa (Bs por $) — propuesta " +
                    (if (propuesta != null) "${textoTasa(propuesta.vesPerUsd)} del ${propuesta.date}" else "no disponible"),
                placeholder = if (propuesta != null) textoTasa(propuesta.vesPerUsd) else "ej. 111,02",
                teclado = KeyboardType.Decimal,
            )
            val destino = estado.destino
            val recibe = estado.destinoMinor
            if (recibe != null && destino != null) {
                Banner("Destino recibirá ${formatMoney(recibe, destino.currency)}", TipoBanner.OK)
            }
        }
        CampoTexto(
            valor = f.nota,
            onCambio = { v -> vm.cambiar { it.copy(nota = v) } },
            etiqueta = "Nota (opcional)",
        )
        BotonPrincipal("Transferir", onClick = vm::guardar, enabled = estado.puedeGuardar)
    }
}
