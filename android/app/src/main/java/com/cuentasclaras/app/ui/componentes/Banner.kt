package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema

enum class TipoBanner { OK, AVISO, ERROR }

/** Diámetro del círculo del ícono del banner. */
private val CIRCULO_BANNER = 32.dp

/** Lado del ícono dentro del círculo. */
private val ICONO_BANNER = 18.dp

/**
 * Aviso en línea: fondo con un tinte suave del color del tipo, ícono en círculo y texto en `texto`.
 * El tipo se comunica también con el ícono y su descripción, nunca solo con el color.
 */
@Composable
fun Banner(
    texto: String,
    tipo: TipoBanner,
    accion: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val color = when (tipo) {
        TipoBanner.OK -> c.ingreso
        TipoBanner.AVISO -> c.aviso
        TipoBanner.ERROR -> c.error
    }
    val icono = when (tipo) {
        TipoBanner.OK -> R.drawable.ic_ok
        TipoBanner.AVISO -> R.drawable.ic_aviso
        TipoBanner.ERROR -> R.drawable.ic_error
    }
    val descripcion = when (tipo) {
        TipoBanner.OK -> "Correcto"
        TipoBanner.AVISO -> "Aviso"
        TipoBanner.ERROR -> "Error"
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(CuentasTema.formas.control)
            .background(lerp(c.superficieElevada, color, 0.12f))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = e.m, vertical = e.s + e.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(CIRCULO_BANNER).clip(CircleShape).background(lerp(c.superficieElevada, color, 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icono), contentDescription = descripcion, tint = color, modifier = Modifier.size(ICONO_BANNER))
        }
        Spacer(Modifier.width(e.s + e.xs))
        Text(texto, style = CuentasTema.tipografia.cuerpo, color = c.texto, modifier = Modifier.weight(1f))
        if (accion != null) {
            Spacer(Modifier.width(e.s))
            accion()
        }
    }
}

@Preview
@Composable
private fun VistaBanners() {
    VistaPreviaComponente {
        Banner("Respaldo restaurado: 132 movimientos.", TipoBanner.OK)
        Banner("La tasa del BCV tiene 3 días.", TipoBanner.AVISO, accion = {
            TextButton(onClick = {}) { Text("Actualizar") }
        })
        Banner("El archivo no es un respaldo válido.", TipoBanner.ERROR)
    }
}
