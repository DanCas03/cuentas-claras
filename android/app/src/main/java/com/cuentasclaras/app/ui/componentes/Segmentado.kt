package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/** Separación entre la pista y el segmento activo. */
private val HOLGURA_SEGMENTADO = 4.dp

/** Alto mínimo de cada segmento (objetivo táctil de 48 dp). */
private val ALTO_SEGMENTO = 48.dp

/**
 * Control segmentado en píldora: pista en el relleno de controles (`borde`), segmento activo en `primario`.
 * El cambio de selección anima el color con la curva propia.
 */
@Composable
fun Segmentado(
    opciones: List<String>,
    seleccion: Int,
    onSeleccion: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val pildora = CuentasTema.formas.pildora
    Row(
        modifier
            .fillMaxWidth()
            .clip(pildora)
            .background(c.borde)
            .padding(HOLGURA_SEGMENTADO)
            .selectableGroup(),
    ) {
        opciones.forEachIndexed { indice, opcion ->
            val activo = indice == seleccion
            val fondo by animateColorAsState(
                if (activo) c.primario else Color.Transparent,
                Movimiento.transicion(),
                label = "fondoSegmento",
            )
            val tinta by animateColorAsState(
                if (activo) c.sobrePrimario else c.texto,
                Movimiento.transicion(),
                label = "tintaSegmento",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = ALTO_SEGMENTO)
                    .clip(pildora)
                    .background(fondo)
                    .selectable(selected = activo, role = Role.Tab, onClick = { onSeleccion(indice) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opcion,
                    style = CuentasTema.tipografia.etiqueta.copy(fontWeight = FontWeight.W600),
                    color = tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = CuentasTema.espacios.s),
                )
            }
        }
    }
}

@Preview
@Composable
private fun VistaSegmentado() {
    VistaPreviaComponente {
        var seleccion by remember { mutableIntStateOf(0) }
        Segmentado(listOf("Gasto", "Ingreso", "Transferencia"), seleccion, { seleccion = it })
    }
}
