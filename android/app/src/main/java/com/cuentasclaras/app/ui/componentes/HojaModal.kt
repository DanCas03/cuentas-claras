package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.RADIO_HOJA
import kotlinx.coroutines.launch

/**
 * Hoja de edición: `ModalBottomSheet` siempre expandida, con título, botón «Cerrar», contenido desplazable e
 * `imePadding()` para que el teclado no tape los campos. Cerrar anima la salida antes de avisar con `onCerrar`.
 *
 * La hoja es otra ventana encima de la de la actividad y tapa el `SnackbarHost` raíz (D2): mientras está abierta,
 * los avisos de `Avisos.avisar` los pinta ella misma ([AvisosDeCapa]) ARRIBA, para no tapar ni absorber los toques
 * de las acciones del pie («Cancelar», «Eliminar»; M2).
 *
 * Al salir de la composición (guardar, cancelar, cerrar o Atrás), la hoja suelta el foco de la PANTALLA de debajo
 * (M3). Causa más probable del salto a «Buscar»: mientras la hoja está abierta, una tecla física o inyectada
 * (`adb shell input text`) saca la pantalla de debajo del modo táctil, y Android le da el foco a su primer enfocable
 * (`ViewRootImpl.leaveTouchMode` → `requestFocus` del `ComposeView` → búsqueda de foco de Compose): en Movimientos,
 * «Buscar». Al cerrarse la hoja, esa ventana recupera el foco con «Buscar» enfocado y abre el teclado. Soltarlo al
 * cerrar (ya en modo táctil por el toque que cierra) deja la pantalla sin foco ni teclado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaModal(titulo: String, onCerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alcance = rememberCoroutineScope()
    // Fuera de `ModalBottomSheet`: es el gestor de foco de la ventana de la actividad, no el de la hoja.
    val focoPantalla = LocalFocusManager.current
    DisposableEffect(focoPantalla) {
        onDispose { focoPantalla.clearFocus(force = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = estado,
        shape = RoundedCornerShape(topStart = RADIO_HOJA, topEnd = RADIO_HOJA),
        containerColor = c.superficie,
        contentColor = c.texto,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.borde) },
    ) {
        // `imePadding` en la caja (fuera del desplazamiento, como antes) para que el contenido quede sobre el teclado.
        Box(
            Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = e.m)
                    .padding(bottom = e.l),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titulo,
                        style = CuentasTema.tipografia.titulo,
                        color = c.texto,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = {
                        alcance.launch { estado.hide() }.invokeOnCompletion { onCerrar() }
                    }) {
                        Icon(painterResource(R.drawable.ic_cerrar), contentDescription = "Cerrar", tint = c.textoSecundario)
                    }
                }
                Spacer(Modifier.height(e.m))
                contenido()
            }
            // M2: arriba (sobre el título), nunca sobre «Guardar», «Cancelar» o «Eliminar» del pie.
            AvisosDeCapa(Modifier.align(Alignment.TopCenter))
        }
    }
}

@Preview
@Composable
private fun VistaHojaModal() {
    VistaPreviaComponente {
        HojaModal("Editar cuenta", onCerrar = {}) {
            CampoTexto("BNC", {}, etiqueta = "Nombre")
        }
    }
}
