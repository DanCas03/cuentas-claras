package com.cuentasclaras.app.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/** Alto visual del chip; si es tocable, el área táctil se amplía a 48 dp. */
internal val ALTO_CHIP = 36.dp

/**
 * Chip en píldora. Normal: relleno de controles (`borde`) con `texto`. `activo`: `primario` con `sobrePrimario`.
 * `sugerido` (E6, T30): tinte suave de `primario` con contorno `primario`, para propuestas que el usuario confirma.
 * Sin `onClick` es una etiqueta estática. `icono`: vector `ic_*` de 16 dp delante del texto, en el color del texto
 * (p. ej. `ic_calendario` en el chip de un evento sin categoría); decorativo, el texto ya nombra el chip.
 * `cifra`: monto ya formateado que va tras el texto («nota · Bs 12.345.678,90») y NUNCA se corta: se mide primero
 * con su ancho natural y, si falta sitio, es el texto el que acaba en «…» (I2).
 */
@Composable
fun Chip(
    texto: String,
    onClick: (() -> Unit)? = null,
    activo: Boolean = false,
    enabled: Boolean = true,
    sugerido: Boolean = false,
    @DrawableRes icono: Int? = null,
    cifra: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val pildora = CuentasTema.formas.pildora
    val fondo by animateColorAsState(
        when {
            activo -> c.primario
            sugerido -> c.primario.copy(alpha = 0.14f)
            else -> c.borde
        },
        Movimiento.transicion(),
        label = "fondoChip",
    )
    val tinta = if (activo) c.sobrePrimario else c.texto
    val contorno = if (sugerido && !activo) c.primario else Color.Transparent
    val base = if (onClick != null) modifier.minimumInteractiveComponentSize() else modifier
    Box(
        base
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = ALTO_CHIP)
            .clip(pildora)
            .background(fondo)
            .border(1.dp, contorno, pildora)
            .then(
                when {
                    onClick != null -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    // Con cifra, nota y cifra son dos textos: se leen como UNO («nota · Bs 12,50»), como antes.
                    cifra != null -> Modifier.semantics(mergeDescendants = true) { }
                    else -> Modifier
                },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs)) {
            if (icono != null) {
                Icon(painterResource(icono), contentDescription = null, tint = tinta, modifier = Modifier.size(CuentasTema.medidas.iconoEnLinea))
            }
            // Dígitos tabulares: hay chips con montos («presup. $50.00») y fechas.
            val estilo = CuentasTema.tipografia.etiqueta.copy(fontWeight = FontWeight.W600, fontFeatureSettings = "tnum")
            if (cifra == null) {
                Text(texto, style = estilo, color = tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // El texto lleva peso sin rellenar: la Row mide antes «· cifra» y le da al texto solo lo que sobra.
                // El «·» va con la cifra: si la nota se corta («pago del gimnas…»), el separador sigue ahí. El hueco
                // antes de «·» es el `espacios.xs` de la Row.
                Text(
                    texto,
                    style = estilo,
                    color = tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text("· $cifra", style = estilo, color = tinta, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Preview
@Composable
private fun VistaChips() {
    VistaPreviaComponente {
        Row(horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s)) {
            Chip("Todas", onClick = {}, activo = true)
            Chip("BNC", onClick = {})
            Chip("Quincena", onClick = {}, sugerido = true)
            Chip("Zinli", onClick = {}, enabled = false)
        }
    }
}
