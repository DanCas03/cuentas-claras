package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento
import com.cuentasclaras.app.ui.theme.sobreError

/** Alto mínimo de los botones (objetivo táctil holgado). */
private val ALTO_BOTON = 52.dp

/** Tamaño mínimo del texto de un botón al encogerse (DESIGN.md §3: nada por debajo de 11 sp). */
private val TEXTO_MINIMO = 11.sp

/** Acción principal de la pantalla (una por pantalla): píldora en `primario`. */
@Composable
fun BotonPrincipal(texto: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    BotonBase(texto, onClick, enabled, modifier, fondo = c.primario, tinta = c.sobrePrimario)
}

/** Acción secundaria: píldora tonal en el relleno de controles (`borde`) con `texto`. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    BotonBase(texto, onClick, enabled, modifier, fondo = c.borde, tinta = c.texto)
}

/** Acción destructiva (siempre tras confirmar con `AlertDialog`): píldora en `error`. */
@Composable
fun BotonPeligro(texto: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    BotonBase(texto, onClick, enabled, modifier, fondo = c.error, tinta = c.sobreError())
}

/**
 * Píldora con hundimiento por resorte al presionar (escala 0,97) y estado deshabilitado al 38 %. Si el texto no
 * cabe (botones a medio ancho en teléfonos estrechos), se encoge antes de acabar en «…».
 */
@Composable
private fun BotonBase(
    texto: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    fondo: Color,
    tinta: Color,
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(if (presionado) 0.97f else 1f, Movimiento.resorte(), label = "escalaBoton")
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ALTO_BOTON)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            },
        enabled = enabled,
        shape = CuentasTema.formas.pildora,
        colors = ButtonDefaults.buttonColors(
            containerColor = fondo,
            contentColor = tinta,
            disabledContainerColor = fondo.copy(alpha = 0.38f),
            disabledContentColor = tinta.copy(alpha = 0.38f),
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = CuentasTema.espacios.l, vertical = CuentasTema.espacios.s),
        interactionSource = interaccion,
    ) {
        // En anchos estrechos el texto se encoge hasta 11 sp (DESIGN.md §3) antes de recurrir a la elipsis.
        val estilo = CuentasTema.tipografia.cuerpo
        BasicText(
            texto,
            style = estilo.copy(fontWeight = FontWeight.W600, color = LocalContentColor.current),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = TEXTO_MINIMO, maxFontSize = estilo.fontSize),
        )
    }
}

@Preview
@Composable
private fun VistaBotones() {
    VistaPreviaComponente {
        BotonPrincipal("Guardar", onClick = {}, modifier = Modifier.fillMaxWidth())
        BotonSecundario("Cancelar", onClick = {}, modifier = Modifier.fillMaxWidth())
        BotonPeligro("Eliminar", onClick = {}, modifier = Modifier.fillMaxWidth())
        BotonPrincipal("Guardando…", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    }
}
