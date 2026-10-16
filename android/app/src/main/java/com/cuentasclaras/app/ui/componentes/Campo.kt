package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/** Alto mínimo de los campos. */
internal val ALTO_CAMPO = 56.dp

/** Grosor del anillo de foco de los campos (y del selector abierto). */
internal val ANILLO_FOCO = 2.dp

/** Opacidad de un campo deshabilitado (DESIGN.md §4: 38 %, como los botones). */
private const val OPACIDAD_DESHABILITADO = 0.38f

/**
 * Campo de texto con la etiqueta ENCIMA (nunca flotante). Relleno de controles (`borde`), sin línea inferior;
 * al enfocar aparece un anillo de 2 dp en `primario`. El campo editable NO lleva `contentDescription` (TalkBack
 * lee su valor); lo nombra la etiqueta visible, que es un nodo propio y al tocarla enfoca el campo (así
 * `tocar "Etiqueta"` de la verificación por adb lo alcanza; [CampoConEtiqueta]). Con `enabled = false` el campo y
 * su etiqueta van al 38 %, no aceptan foco ni texto y el lector de pantalla lo anuncia como deshabilitado.
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
    enabled: Boolean = true,
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
    CampoConEtiqueta(etiqueta, modifier, habilitado = enabled, alTocar = { foco.requestFocus() }) {
        TextField(
            value = valor,
            onValueChange = onCambio,
            enabled = enabled,
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
 * Etiqueta encima + campo como UN solo objetivo táctil (V22, DESIGN.md §4 y §6). La etiqueta es un nodo propio con
 * su texto (nombra al campo y es lo que busca `tocar`) a todo el ancho; un toque en ella o en el hueco hasta el campo
 * ejecuta `alTocar` (enfocar el campo, abrir el menú o el selector de fecha). Etiqueta y campo suman un objetivo
 * continuo de ≥ 80 dp de alto dentro del margen, sin el cuadrado de 48 dp desplazado que el sistema le daba a una
 * etiqueta corta tocable (se salía del margen y se montaba en el control de encima).
 *
 * El toque se detecta en la `Column`: los toques que el propio campo consume (escribir, abrir la fecha) no lo
 * disparan; los que no consume repiten la misma acción, que es idempotente.
 */
@Composable
internal fun CampoConEtiqueta(
    etiqueta: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    alTocar: () -> Unit,
    campo: @Composable () -> Unit,
) {
    val accion by rememberUpdatedState(alTocar)
    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(habilitado) {
                if (habilitado) detectTapGestures(onTap = { accion() })
            },
    ) {
        Text(
            etiqueta,
            style = CuentasTema.tipografia.etiqueta,
            color = CuentasTema.colores.textoSecundario.copy(alpha = if (habilitado) 1f else OPACIDAD_DESHABILITADO),
            modifier = Modifier.fillMaxWidth().padding(bottom = CuentasTema.espacios.xs + 2.dp),
        )
        campo()
    }
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
        disabledPlaceholderColor = c.textoSecundario.copy(alpha = OPACIDAD_DESHABILITADO),
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
