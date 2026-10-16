package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/** Alto mínimo de los campos. */
internal val ALTO_CAMPO = 56.dp

/** Grosor del anillo de foco de los campos. */
private val ANILLO_FOCO = 2.dp

/**
 * Campo de texto con la etiqueta ENCIMA (nunca flotante). Relleno de controles (`borde`), sin línea inferior;
 * al enfocar aparece un anillo de 2 dp en `primario`. El campo editable NO lleva `contentDescription` (TalkBack
 * lee su valor); lo nombra la etiqueta visible, que es un nodo propio y al tocarla enfoca el campo (así
 * `tocar "Etiqueta"` de la verificación por adb lo alcanza).
 */
@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    placeholder: String = "",
    teclado: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    autoFoco: Boolean = false,
) {
    val c = CuentasTema.colores
    val interaccion = remember { MutableInteractionSource() }
    val enfocado by interaccion.collectIsFocusedAsState()
    val anillo by animateColorAsState(
        if (enfocado) c.primario else Color.Transparent,
        Movimiento.transicion(Movimiento.CORTO),
        label = "anilloCampo",
    )
    val forma = CuentasTema.formas.control
    val foco = remember { FocusRequester() }
    if (autoFoco) LaunchedEffect(Unit) { foco.requestFocus() }
    Column(modifier.fillMaxWidth()) {
        EtiquetaCampo(etiqueta) { foco.requestFocus() }
        TextField(
            value = valor,
            onValueChange = onCambio,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_CAMPO)
                .border(ANILLO_FOCO, anillo, forma)
                .focusRequester(foco),
            textStyle = CuentasTema.tipografia.cuerpo,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, style = CuentasTema.tipografia.cuerpo) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = teclado),
            singleLine = singleLine,
            minLines = minLines,
            shape = forma,
            interactionSource = interaccion,
            colors = coloresCampo(),
        )
    }
}

/**
 * Etiqueta visible encima de un campo. Es un nodo accesible con su texto (nombra al campo y es lo que busca
 * `tocar`); tocarla ejecuta `alTocar` (enfocar el campo, abrir el menú o el selector de fecha).
 */
@Composable
internal fun EtiquetaCampo(etiqueta: String, alTocar: () -> Unit) {
    Text(
        etiqueta,
        style = CuentasTema.tipografia.etiqueta,
        color = CuentasTema.colores.textoSecundario,
        modifier = Modifier
            .padding(bottom = CuentasTema.espacios.xs + 2.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = alTocar),
    )
}

/** Colores comunes de los campos (texto, selector): relleno de controles y sin indicador inferior. */
@Composable
internal fun coloresCampo(): TextFieldColors {
    val c = CuentasTema.colores
    return TextFieldDefaults.colors(
        focusedTextColor = c.texto,
        unfocusedTextColor = c.texto,
        disabledTextColor = c.texto.copy(alpha = 0.38f),
        focusedContainerColor = c.borde,
        unfocusedContainerColor = c.borde,
        disabledContainerColor = c.borde.copy(alpha = 0.38f),
        cursorColor = c.primario,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedPlaceholderColor = c.textoSecundario,
        unfocusedPlaceholderColor = c.textoSecundario,
        focusedTrailingIconColor = c.textoSecundario,
        unfocusedTrailingIconColor = c.textoSecundario,
    )
}

@Preview
@Composable
private fun VistaCampoTexto() {
    VistaPreviaComponente {
        var nota by remember { mutableStateOf("") }
        CampoTexto(nota, { nota = it }, etiqueta = "Nota", placeholder = "Ej.: mercado del sábado")
        CampoTexto("120,50", {}, etiqueta = "Monto", teclado = KeyboardType.Decimal)
        CampoTexto("", {}, etiqueta = "Descripción", singleLine = false, minLines = 3)
    }
}
