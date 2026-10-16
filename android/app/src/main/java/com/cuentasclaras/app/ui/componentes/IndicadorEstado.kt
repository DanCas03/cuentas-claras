package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Tinte del fondo de un indicador con tipo (el mismo 12 % del `Banner`, DESIGN.md §4). */
private const val TINTE_INDICADOR = 0.12f

/**
 * Indicador de estado no tocable (Permisos): píldora de 36 dp con el texto en `etiqueta` 600 + `texto`. Con `tipo`
 * lleva el tinte del 12 % del color del tipo sobre `superficieElevada` (`ingreso`, `aviso`, `error`, como el
 * `Banner`) y su ícono `ic_ok` / `ic_aviso` / `ic_error` de 16 dp en ese color: el estado se lee por texto, ícono y
 * color, nunca solo por color. Sin `tipo` es neutro (`borde`, sin ícono). No compite con la acción principal: nunca
 * usa `primario`.
 */
@Composable
fun IndicadorEstado(texto: String, tipo: TipoBanner?, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val color = when (tipo) {
        TipoBanner.OK -> c.ingreso
        TipoBanner.AVISO -> c.aviso
        TipoBanner.ERROR -> c.error
        null -> null
    }
    val icono = when (tipo) {
        TipoBanner.OK -> R.drawable.ic_ok
        TipoBanner.AVISO -> R.drawable.ic_aviso
        TipoBanner.ERROR -> R.drawable.ic_error
        null -> null
    }
    Row(
        modifier
            .heightIn(min = ALTO_CHIP)
            .clip(CuentasTema.formas.pildora)
            .background(if (color != null) lerp(c.superficieElevada, color, TINTE_INDICADOR) else c.borde)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = e.s + e.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.xs),
    ) {
        if (icono != null && color != null) {
            Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(CuentasTema.medidas.iconoEnLinea))
        }
        Text(
            texto,
            style = CuentasTema.tipografia.etiqueta.copy(fontWeight = FontWeight.W600),
            color = c.texto,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview
@Composable
private fun VistaIndicadorEstado() {
    VistaPreviaComponente {
        IndicadorEstado("Concedido", TipoBanner.OK)
        IndicadorEstado("Falta", TipoBanner.AVISO)
        IndicadorEstado("No detectable", null)
    }
}
