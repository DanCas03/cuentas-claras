package com.cuentasclaras.app.ui.pantallas.apariencia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.data.ICONOS_APP
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Paleta

/** Lado del disco de muestra de cada ícono y del glifo dentro. */
private val LADO_MUESTRA_ICONO = 64.dp
private val LADO_GLIFO = 56.dp

/**
 * Grupo «Ícono de la app» (spec 2026-10-17 §7): tres cuadros con la forma pintada en el `fondo` y el `primario`
 * de la paleta oscura elegida (se repintan al cambiar esa paleta), el nombre debajo y un visto en el elegido.
 */
@Composable
fun GrupoIconos(paletaOscura: Paleta, idElegido: String, onElegir: (String) -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val contexto = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(e.m)) {
            ICONOS_APP.forEach { icono ->
                val elegido = icono.id == idElegido
                // Los nombres de los drawables los genera scripts/generar-iconos-app.py; de ahí el lookup por nombre.
                val idGlifo = contexto.resources.getIdentifier("ic_launcher_mono_${icono.id}", "drawable", contexto.packageName)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CuentasTema.formas.control)
                        .background(if (elegido) c.primario.copy(alpha = 0.14f) else Color.Transparent)
                        .selectable(selected = elegido, role = Role.RadioButton, onClick = { onElegir(icono.id) })
                        .heightIn(min = CuentasTema.medidas.tactilMinimo)
                        .padding(e.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(e.xs),
                ) {
                    Box(
                        Modifier.size(LADO_MUESTRA_ICONO).clip(CircleShape).background(paletaOscura.colores.fondo),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (idGlifo != 0) {
                            Icon(
                                painterResource(idGlifo),
                                contentDescription = null,
                                tint = paletaOscura.colores.primario,
                                modifier = Modifier.size(LADO_GLIFO),
                            )
                        }
                    }
                    Text(
                        icono.nombre,
                        style = CuentasTema.tipografia.etiqueta.copy(fontWeight = if (elegido) FontWeight.W600 else FontWeight.W500),
                        color = c.texto,
                    )
                    if (elegido) {
                        Icon(painterResource(R.drawable.ic_ok), contentDescription = null, tint = c.primario, modifier = Modifier.size(ICONO_ELEGIDA))
                    }
                }
            }
        }
        Text(
            "El lanzador puede tardar unos segundos en mostrar el cambio.",
            style = CuentasTema.tipografia.etiqueta,
            color = c.textoSecundario,
        )
    }
}
