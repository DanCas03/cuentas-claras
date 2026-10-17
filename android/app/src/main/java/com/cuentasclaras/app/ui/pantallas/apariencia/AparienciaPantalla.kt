package com.cuentasclaras.app.ui.pantallas.apariencia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.data.Apariencia
import com.cuentasclaras.app.data.ModoTema
import com.cuentasclaras.app.ui.componentes.BotonChevron
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.PALETAS_CLARAS
import com.cuentasclaras.app.ui.theme.PALETAS_OSCURAS
import com.cuentasclaras.app.ui.theme.Paleta
import com.cuentasclaras.app.ui.theme.paletaPorId
import com.cuentasclaras.app.ui.vmCon

/** Lado de la muestra de color de una paleta. */
private val LADO_MUESTRA = 44.dp

/** Ícono de «elegida» dentro de una fila seleccionable. */
internal val ICONO_ELEGIDA = 20.dp

private val MODOS = listOf(ModoTema.SISTEMA, ModoTema.CLARO, ModoTema.OSCURO)
private val TEXTO_MODOS = listOf("Sistema", "Claro", "Oscuro")

/**
 * Pantalla «Apariencia» (spec 2026-10-17 §3): Modo, Paleta oscura, Paleta clara (E22), Fuente, Diseño, Barra e
 * Ícono de la app. Cada grupo en su `Tarjeta`; cabecera con botón «Volver».
 */
@Composable
fun AparienciaPantalla(nav: NavHostController) {
    val vm = vmCon { c, _ -> AparienciaViewModel(c.apariencia) }
    val apariencia by vm.apariencia.collectAsStateWithLifecycle()
    val actual: Apariencia = apariencia ?: return
    val e = CuentasTema.espacios
    val paletaActual = paletaPorId(if (CuentasTema.oscuro) actual.idPaletaOscura else actual.idPaletaClara, CuentasTema.oscuro)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.l),
    ) {
        item(key = "cabecera") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BotonChevron("Volver", haciaLaIzquierda = true, onClick = { nav.popBackStack() })
                Text(
                    "Apariencia",
                    style = CuentasTema.tipografia.titulo,
                    color = CuentasTema.colores.texto,
                    modifier = Modifier.padding(start = e.s).semantics { heading() },
                )
            }
        }
        item(key = "modo") {
            Tarjeta(titulo = "Modo") {
                Segmentado(
                    opciones = TEXTO_MODOS,
                    seleccion = MODOS.indexOf(actual.modo).coerceAtLeast(0),
                    onSeleccion = { vm.cambiarModo(MODOS[it]) },
                )
            }
        }
        item(key = "paleta_oscura") {
            Tarjeta(titulo = "Paleta oscura") {
                GrupoPaletas(PALETAS_OSCURAS, paletaPorId(actual.idPaletaOscura, oscura = true).id, vm::cambiarPaletaOscura)
            }
        }
        item(key = "paleta_clara") {
            Tarjeta(titulo = "Paleta clara") {
                GrupoPaletas(PALETAS_CLARAS, paletaPorId(actual.idPaletaClara, oscura = false).id, vm::cambiarPaletaClara)
            }
        }
        item(key = "fuente") {
            Tarjeta(titulo = "Fuente") { GrupoFuentes(paletaActual, actual.idFuente, vm::cambiarFuente) }
        }
        item(key = "diseno") {
            Tarjeta(titulo = "Diseño") { GrupoDisenos(paletaActual, actual.idFuente, actual.idDiseno, vm::cambiarDiseno) }
        }
        item(key = "barra") {
            Tarjeta(titulo = "Barra") {
                FilaInterruptor("Nombres en la barra", actual.etiquetasBarra, vm::cambiarEtiquetasBarra)
            }
        }
        item(key = "icono") {
            Tarjeta(titulo = "Ícono de la app") {
                GrupoIconos(paletaPorId(actual.idPaletaOscura, oscura = true), actual.idIcono, vm::cambiarIcono)
            }
        }
    }
}

@Composable
private fun GrupoPaletas(paletas: List<Paleta>, idElegida: String, onElegir: (String) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs)) {
        paletas.forEach { paleta ->
            FilaSeleccionable(elegida = paleta.id == idElegida, onClick = { onElegir(paleta.id) }, muestra = { MuestraPaleta(paleta) }) {
                Text(
                    paleta.nombre,
                    style = CuentasTema.tipografia.cuerpo.copy(fontWeight = if (paleta.id == idElegida) FontWeight.W600 else FontWeight.W400),
                    color = CuentasTema.colores.texto,
                )
            }
        }
    }
}

/**
 * Fila seleccionable común de Apariencia: muestra a la izquierda, contenido en el centro y, si es la elegida,
 * tinte `primario` al 14 % y un visto (nunca solo color).
 */
@Composable
internal fun FilaSeleccionable(
    elegida: Boolean,
    onClick: () -> Unit,
    muestra: @Composable () -> Unit,
    contenido: @Composable () -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CuentasTema.formas.control)
            .background(if (elegida) c.primario.copy(alpha = 0.14f) else Color.Transparent)
            .selectable(selected = elegida, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = LADO_MUESTRA + e.s * 2)
            .padding(horizontal = e.s, vertical = e.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        muestra()
        Spacer(Modifier.width(e.m))
        Box(Modifier.weight(1f)) { contenido() }
        if (elegida) {
            Icon(
                painterResource(R.drawable.ic_ok),
                contentDescription = null,
                tint = c.primario,
                modifier = Modifier.padding(start = e.s).size(ICONO_ELEGIDA),
            )
        }
    }
}

/** Mini lienzo con el `fondo`, una tarjeta en `superficieElevada` y el acento `primario` de la paleta. */
@Composable
private fun MuestraPaleta(paleta: Paleta) {
    val p = paleta.colores
    val e = CuentasTema.espacios
    Box(
        Modifier
            .size(LADO_MUESTRA)
            .clip(RoundedCornerShape(e.s + e.xs))
            .background(p.fondo),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(LADO_MUESTRA - e.m)
                .clip(RoundedCornerShape(e.s))
                .background(p.superficieElevada),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(e.m).clip(CircleShape).background(p.primario))
        }
    }
}

/** Fila con texto y un interruptor; la fila entera es el objetivo (≥ 48 dp, `Role.Switch`). */
@Composable
private fun FilaInterruptor(texto: String, activo: Boolean, onCambio: (Boolean) -> Unit) {
    val e = CuentasTema.espacios
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CuentasTema.formas.control)
            .selectable(selected = activo, role = Role.Switch, onClick = { onCambio(!activo) })
            .heightIn(min = CuentasTema.medidas.tactilMinimo)
            .padding(horizontal = e.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, style = CuentasTema.tipografia.cuerpo, color = CuentasTema.colores.texto, modifier = Modifier.weight(1f))
        Switch(checked = activo, onCheckedChange = null)
    }
}
