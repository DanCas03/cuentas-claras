package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Diámetro del círculo del estado vacío. */
private val CIRCULO_VACIO = 72.dp

/** Estado vacío: ícono (emoji) en un círculo de `superficie` y una frase en `textoSecundario`. Nunca pantalla en blanco. */
@Composable
fun Vacio(icono: String, texto: String, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Column(
        modifier.fillMaxWidth().padding(horizontal = e.l, vertical = e.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(CIRCULO_VACIO).clip(CircleShape).background(c.superficie),
            contentAlignment = Alignment.Center,
        ) {
            Text(icono, fontSize = 32.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.height(e.m))
        Text(texto, style = CuentasTema.tipografia.cuerpo, color = c.textoSecundario, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
private fun VistaVacio() {
    VistaPreviaComponente {
        Vacio("🧾", "Todavía no hay movimientos este mes.")
    }
}
