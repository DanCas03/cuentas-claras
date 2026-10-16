package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/** Alto visual del chip; si es tocable, el área táctil se amplía a 48 dp. */
private val ALTO_CHIP = 36.dp

/**
 * Chip en píldora. Normal: relleno de controles (`borde`) con `texto`. `activo`: `primario` con `sobrePrimario`.
 * `sugerido` (E6, T30): tinte suave de `primario` con contorno `primario`, para propuestas que el usuario confirma.
 * Sin `onClick` es una etiqueta estática.
 */
@Composable
fun Chip(
    texto: String,
    onClick: (() -> Unit)? = null,
    activo: Boolean = false,
    enabled: Boolean = true,
    sugerido: Boolean = false,
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
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = CuentasTema.tipografia.etiqueta.copy(fontWeight = FontWeight.W600),
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
