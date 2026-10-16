package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Alto de la línea (DESIGN.md §7: 6 dp, píldora). */
private val ALTO_PROGRESO = 6.dp

/** Grosor de la pista: un trazo fino centrado en el alto de la línea. */
private val GROSOR_PISTA = 2.dp

/**
 * Línea de progreso de «Este mes» y de los presupuestos (DESIGN.md §7): relleno de 6 dp en píldora sobre una pista
 * de 2 dp en `textoSecundario` centrada que marca el 100 %. La pista pasa de 4,5:1 frente a `superficieElevada` en
 * todas las paletas (una pista `fondo` o `borde` se quedaba en 1,1-1,4:1 y no se veía); el final del relleno se
 * distingue por el grosor, no solo por el color. `fraccion` se acota a [0, 1].
 */
@Composable
fun LineaProgreso(fraccion: Float, relleno: Color, modifier: Modifier = Modifier) {
    val pildora = CuentasTema.formas.pildora
    Box(modifier.fillMaxWidth().height(ALTO_PROGRESO), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(GROSOR_PISTA)
                .clip(pildora)
                .background(CuentasTema.colores.textoSecundario),
        )
        val f = fraccion.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(ALTO_PROGRESO)
                    .clip(pildora)
                    .background(relleno),
            )
        }
    }
}

@Preview
@Composable
private fun VistaLineaProgreso() {
    VistaPreviaComponente {
        Tarjeta(titulo = "Este mes") {
            LineaProgreso(0f, CuentasTema.colores.gasto)
            LineaProgreso(0.42f, CuentasTema.colores.gasto)
            LineaProgreso(0.9f, CuentasTema.colores.aviso)
            LineaProgreso(1f, CuentasTema.colores.error)
        }
    }
}
