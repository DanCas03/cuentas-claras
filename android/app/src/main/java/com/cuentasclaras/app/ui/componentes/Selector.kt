package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Selector desplegable: `ExposedDropdownMenuBox` de Material 3 con un `TextField` de solo lectura, con el mismo
 * aspecto que `CampoTexto` (etiqueta encima, relleno de controles). Sin `contentDescription` el campo lo nombra la
 * etiqueta visible (tocarla abre el menú). `contentDescription` (E6, T31) solo cuando dos selectores comparten
 * etiqueta y hace falta distinguirlos (p. ej. «Cuenta de origen»): debe empezar con la etiqueta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Selector(
    etiqueta: String,
    opciones: List<T>,
    seleccion: T?,
    texto: (T) -> String,
    onSeleccion: (T) -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    var abierto by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        EtiquetaCampo(etiqueta) { abierto = true }
        ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
            TextField(
                value = seleccion?.let(texto) ?: "",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text("Elegir", style = CuentasTema.tipografia.cuerpo) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
                textStyle = CuentasTema.tipografia.cuerpo,
                shape = CuentasTema.formas.control,
                colors = coloresCampo(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = ALTO_CAMPO)
                    .border(2.dp, if (abierto) c.primario else Color.Transparent, CuentasTema.formas.control)
                    .then(
                        if (contentDescription != null) {
                            Modifier.semantics { this.contentDescription = contentDescription }
                        } else {
                            Modifier
                        },
                    ),
            )
            ExposedDropdownMenu(
                expanded = abierto,
                onDismissRequest = { abierto = false },
                shape = CuentasTema.formas.control,
                containerColor = c.superficieElevada,
            ) {
                opciones.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(texto(opcion), style = CuentasTema.tipografia.cuerpo, color = c.texto) },
                        onClick = {
                            onSeleccion(opcion)
                            abierto = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun VistaSelector() {
    VistaPreviaComponente {
        var cuenta by remember { mutableStateOf<String?>("BNC") }
        Selector(
            etiqueta = "Cuenta",
            opciones = listOf("BNC", "Zinli", "Binance", "Efectivo"),
            seleccion = cuenta,
            texto = { it },
            onSeleccion = { cuenta = it },
        )
    }
}
