package com.cuentasclaras.app.ui.componentes

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Alto de cada tecla: ≥ 56 dp (brief). Cuatro filas + separaciones = 248 dp. */
private val ALTO_TECLA = 56.dp

private const val TECLA_COMA = ","
private const val TECLA_BORRAR = "⌫"

private val FILAS_TECLADO = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(TECLA_COMA, "0", TECLA_BORRAR),
)

/**
 * Teclado numérico propio de 4 × 3 (`1 2 3 / 4 5 6 / 7 8 9 / , 0 ⌫`). `onTecla` recibe el dígito, `","` o
 * `"⌫"`. Las teclas especiales llevan `contentDescription` «coma» y «borrar». Vibración corta de tecla.
 */
@Composable
fun Teclado(onTecla: (String) -> Unit, modifier: Modifier = Modifier) {
    val separacion = CuentasTema.espacios.s
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(separacion)) {
        FILAS_TECLADO.forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(separacion)) {
                fila.forEach { tecla -> Tecla(tecla, onTecla, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tecla(tecla: String, onTecla: (String) -> Unit, modifier: Modifier) {
    val c = CuentasTema.colores
    val vista = LocalView.current
    val descripcion = when (tecla) {
        TECLA_COMA -> "coma"
        TECLA_BORRAR -> "borrar"
        else -> null
    }
    Box(
        modifier
            .height(ALTO_TECLA)
            .clip(CuentasTema.formas.control)
            .background(c.superficieElevada)
            .clickable {
                vista.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onTecla(tecla)
            }
            .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (tecla == TECLA_BORRAR) {
            Icon(painterResource(R.drawable.ic_borrar), contentDescription = null, tint = c.texto, modifier = Modifier.size(24.dp))
        } else {
            Text(tecla, style = CuentasTema.tipografia.cifra.copy(fontSize = 24.sp), color = c.texto)
        }
    }
}

@Preview
@Composable
private fun VistaTeclado() {
    VistaPreviaComponente {
        Teclado(onTecla = {})
    }
}
