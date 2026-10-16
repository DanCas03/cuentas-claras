package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Lado del ícono de navegación (DESIGN.md §4: 24 dp). */
private val ICONO_CHEVRON = 24.dp

/** Opacidad deshabilitada (DESIGN.md §4: 38 %). */
private const val OPACIDAD_DESHABILITADO = 0.38f

/**
 * Flecha de mes anterior / siguiente (Calendario y «Este mes» de Inicio): `ic_expandir` girado hacia un lado, en
 * `texto`, dentro de un objetivo circular de 48 dp. Deshabilitada al 38 % y anunciada como tal (extremos del rango
 * de «Este mes»).
 */
@Composable
fun BotonChevron(
    descripcion: String,
    haciaLaIzquierda: Boolean,
    onClick: () -> Unit,
    habilitado: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(CuentasTema.medidas.tactilMinimo)
            .clip(CircleShape)
            .alpha(if (habilitado) 1f else OPACIDAD_DESHABILITADO)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = descripcion
                role = Role.Button
                if (!habilitado) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_expandir),
            contentDescription = null,
            tint = CuentasTema.colores.texto,
            // `ic_expandir` apunta hacia abajo: +90° lo lleva a la izquierda y −90° a la derecha.
            modifier = Modifier.size(ICONO_CHEVRON).graphicsLayer { rotationZ = if (haciaLaIzquierda) 90f else -90f },
        )
    }
}

@Preview
@Composable
private fun VistaBotonChevron() {
    VistaPreviaComponente {
        BotonChevron("Mes anterior", haciaLaIzquierda = true, onClick = {})
        BotonChevron("Mes siguiente", haciaLaIzquierda = false, onClick = {}, habilitado = false)
    }
}
