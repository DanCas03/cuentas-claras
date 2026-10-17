package com.cuentasclaras.app.ui.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/**
 * Sección de Ajustes que se pliega (spec 2026-10-17 §3): una `Tarjeta` cuya cabecera entera es el objetivo
 * táctil (≥ 48 dp, botón, estado «desplegada» / «plegada») con `ic_expandir` que gira 180°. El contenido entra
 * con alfa + traslación (`LARGO`), nunca animando el alto; cerrada no se compone.
 */
@Composable
fun SeccionPlegable(
    titulo: String,
    abierta: Boolean,
    onAlternar: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val nivel = CuentasTema.diseno.movimiento
    val giro by animateFloatAsState(if (abierta) 180f else 0f, Movimiento.transicion(Movimiento.MEDIO, nivel), label = "chevronSeccion")
    val estado = if (abierta) "desplegada" else "plegada"
    Tarjeta {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CuentasTema.medidas.tactilMinimo)
                .clip(CuentasTema.formas.control)
                .clickable(role = Role.Button, onClick = onAlternar)
                .semantics { stateDescription = estado },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                titulo,
                style = CuentasTema.tipografia.subtitulo,
                color = c.texto,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Icon(
                painterResource(R.drawable.ic_expandir),
                contentDescription = null,
                tint = c.textoSecundario,
                modifier = Modifier.graphicsLayer { rotationZ = giro },
            )
        }
        AnimatedVisibility(
            abierta,
            enter = fadeIn(Movimiento.transicion(Movimiento.LARGO, nivel)) +
                slideInVertically(Movimiento.transicion(Movimiento.LARGO, nivel)) { -it / 8 },
            exit = fadeOut(Movimiento.transicion(Movimiento.MEDIO, nivel)),
        ) {
            Column(Modifier.padding(top = e.s), content = contenido)
        }
    }
}
