package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Selector desplegable: `ExposedDropdownMenuBox` de Material 3 con un ancla de solo lectura ([AnclaSelector]) con
 * el mismo aspecto que `CampoTexto` (etiqueta encima, relleno de controles); el valor acaba en «…» si no cabe. Sin `contentDescription` el campo lo nombra la
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
            AnclaSelector(seleccion?.let(texto), abierto, contentDescription)
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

/**
 * Ancla de un selector: el aspecto de `CampoTexto` (≥ 56 dp, relleno de controles, radio `control`, anillo de 2 dp
 * en `primario` con el menú abierto) con el valor en UNA línea que acaba en «…» si no cabe (un `TextField` lo
 * cortaba a media letra en anchos estrechos). Sin valor muestra «Elegir» en `textoSecundario`. La abre y cierra
 * `menuAnchor` de Material 3, que también la expone como lista desplegable al lector de pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExposedDropdownMenuBoxScope.AnclaSelector(valor: String?, abierto: Boolean, contentDescription: String?) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val forma = CuentasTema.formas.control
    Row(
        Modifier
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            .fillMaxWidth()
            .heightIn(min = ALTO_CAMPO)
            .clip(forma)
            .background(c.borde)
            .border(ANILLO_FOCO, if (abierto) c.primario else Color.Transparent, forma)
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .padding(start = e.m, end = e.s + e.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.s),
    ) {
        Text(
            valor ?: "Elegir",
            style = CuentasTema.tipografia.cuerpo,
            color = if (valor != null) c.texto else c.textoSecundario,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CompositionLocalProvider(LocalContentColor provides c.textoSecundario) {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto)
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
