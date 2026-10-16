package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.R
import com.cuentasclaras.app.data.Apariencia
import com.cuentasclaras.app.data.ModoTema
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.PALETAS_CLARAS
import com.cuentasclaras.app.ui.theme.PALETAS_OSCURAS
import com.cuentasclaras.app.ui.theme.Paleta
import com.cuentasclaras.app.ui.vmCon

/** Lado de la muestra de color de una paleta. */
private val LADO_MUESTRA = 44.dp

/** Ícono de «elegida» dentro de la fila de paleta. */
private val ICONO_ELEGIDA = 20.dp

private val MODOS = listOf(ModoTema.SISTEMA, ModoTema.CLARO, ModoTema.OSCURO)
private val TEXTO_MODOS = listOf("Sistema", "Claro", "Oscuro")

/**
 * «Apariencia» (E22): Modo (Sistema / Claro / Oscuro) y una paleta oscura y otra clara, que se eligen por
 * separado. El modo decide cuál de las dos se ve. Cada paleta se muestra con su propia muestra de color.
 */
@Composable
fun AparienciaSeccion() {
    val vm = vmCon { c, _ -> AparienciaViewModel(c.apariencia) }
    val apariencia by vm.apariencia.collectAsStateWithLifecycle()
    val actual: Apariencia = apariencia ?: return
    val e = CuentasTema.espacios

    Tarjeta(titulo = "Apariencia") {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                RotuloGrupo("Modo")
                Segmentado(
                    opciones = TEXTO_MODOS,
                    seleccion = MODOS.indexOf(actual.modo).coerceAtLeast(0),
                    onSeleccion = { vm.cambiarModo(MODOS[it]) },
                )
            }
            GrupoPaletas("Paleta oscura", PALETAS_OSCURAS, actual.idPaletaOscura, vm::cambiarPaletaOscura)
            GrupoPaletas("Paleta clara", PALETAS_CLARAS, actual.idPaletaClara, vm::cambiarPaletaClara)
        }
    }
}

@Composable
private fun RotuloGrupo(texto: String) {
    Text(
        texto,
        style = CuentasTema.tipografia.etiqueta,
        color = CuentasTema.colores.textoSecundario,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun GrupoPaletas(titulo: String, paletas: List<Paleta>, idElegida: String, onElegir: (String) -> Unit) {
    val e = CuentasTema.espacios
    Column(verticalArrangement = Arrangement.spacedBy(e.xs)) {
        RotuloGrupo(titulo)
        paletas.forEach { paleta ->
            FilaPaleta(paleta, elegida = paleta.id == idElegida, onClick = { onElegir(paleta.id) })
        }
    }
}

/** Fila seleccionable: muestra de la paleta, su nombre y, si es la elegida, un visto (no solo color). */
@Composable
private fun FilaPaleta(paleta: Paleta, elegida: Boolean, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val forma = CuentasTema.formas.control
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (elegida) c.primario.copy(alpha = 0.14f) else Color.Transparent)
            .selectable(selected = elegida, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = LADO_MUESTRA + e.s * 2)
            .padding(horizontal = e.s, vertical = e.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MuestraPaleta(paleta)
        Spacer(Modifier.width(e.m))
        Text(
            paleta.nombre,
            style = CuentasTema.tipografia.cuerpo.copy(fontWeight = if (elegida) FontWeight.W600 else FontWeight.W400),
            color = c.texto,
            modifier = Modifier.weight(1f),
        )
        if (elegida) {
            Icon(
                painterResource(R.drawable.ic_ok),
                contentDescription = null,
                tint = c.primario,
                modifier = Modifier.size(ICONO_ELEGIDA),
            )
        }
    }
}

/** Mini lienzo con el `fondo`, una tarjeta en `superficieElevada` y el acento `primario` de la paleta. */
@Composable
private fun MuestraPaleta(paleta: Paleta) {
    val p = paleta.colores
    Box(
        Modifier
            .size(LADO_MUESTRA)
            .clip(RoundedCornerShape(CuentasTema.espacios.s + CuentasTema.espacios.xs))
            .background(p.fondo),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(LADO_MUESTRA - CuentasTema.espacios.m)
                .clip(RoundedCornerShape(CuentasTema.espacios.s))
                .background(p.superficieElevada),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(CuentasTema.espacios.m).clip(CircleShape).background(p.primario))
        }
    }
}
