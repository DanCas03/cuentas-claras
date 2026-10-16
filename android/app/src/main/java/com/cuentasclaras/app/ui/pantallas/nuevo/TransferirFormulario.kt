package com.cuentasclaras.app.ui.pantallas.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.collectLatest
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
    // F1: los campos (monto, tasa, nota) leen el formulario síncrono, no `estado.f`, que llega atrasado.
    val f by vm.formularioActual.collectAsStateWithLifecycle()
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
        NotaYBoton(
            nota = f.nota,
            onNota = { v -> vm.cambiar { it.copy(nota = v) } },
            onTransferir = vm::guardar,
            puedeGuardar = estado.puedeGuardar,
        )
    }
}

/**
 * D3: con la nota enfocada, la nota y el botón «Transferir» se traen juntos a la vista cada vez que cambia el
 * borde del teclado (el de texto es más alto que el numérico y tapaba medio botón). El relleno del teclado ya lo
 * pone `imePadding()` en NuevoPantalla: aquí solo se desplaza el contenido, sin relleno extra.
 */
@Composable
private fun NotaYBoton(nota: String, onNota: (String) -> Unit, onTransferir: () -> Unit, puedeGuardar: Boolean) {
    val solicitante = remember { BringIntoViewRequester() }
    var notaEnfocada by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val densidad = LocalDensity.current
    LaunchedEffect(notaEnfocada) {
        if (notaEnfocada) {
            // El último valor (el teclado ya abierto) termina su desplazamiento; los intermedios se cancelan.
            snapshotFlow { ime.getBottom(densidad) }.collectLatest { solicitante.bringIntoView() }
        }
    }
    Column(
        Modifier.bringIntoViewRequester(solicitante),
        verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.m),
    ) {
        CampoTexto(
            valor = nota,
            onCambio = onNota,
            etiqueta = "Nota (opcional)",
            modifier = Modifier.onFocusChanged { notaEnfocada = it.hasFocus },
        )
        BotonPrincipal("Transferir", onClick = onTransferir, enabled = puedeGuardar)
    }
}
