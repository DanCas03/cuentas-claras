package com.cuentasclaras.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento

/** Medidas de la barra (DESIGN.md §1 y §6). */
private val ALTO_BARRA = 64.dp
private val DISCO_MAS = 56.dp
private val SOBRESALE_MAS = 14.dp
private val ICONO_PESTANA = 24.dp
private val ICONO_MAS = 28.dp
private val GROSOR_DIVISOR = 1.dp
private val ANCHO_INDICADOR = 56.dp
private val ALTO_INDICADOR = 32.dp
private const val TINTE_ACTIVO = 0.14f

/** `restaurar = false`: la pestaña siempre abre limpia (Movs sin filtros, E25), sin estado guardado. */
private class Pestana(
    val ruta: String,
    val destino: String,
    val texto: String,
    @DrawableRes val icono: Int,
    val restaurar: Boolean = true,
)

private val PESTANAS_IZQUIERDA = listOf(
    Pestana(Rutas.INICIO, Rutas.INICIO, "Inicio", R.drawable.ic_inicio),
    Pestana(Rutas.MOVIMIENTOS, Rutas.movimientos(), "Movs", R.drawable.ic_movimientos, restaurar = false),
)
private val PESTANAS_DERECHA = listOf(
    Pestana(Rutas.IMPORTAR, Rutas.IMPORTAR, "Importar", R.drawable.ic_importar),
    Pestana(Rutas.AJUSTES, Rutas.AJUSTES, "Ajustes", R.drawable.ic_ajustes),
)

/**
 * Barra inferior: Inicio, Movs, «+» central (acción principal), Importar, Ajustes. El «+» sobresale
 * `SOBRESALE_MAS` hacia arriba; esa franja forma parte de la barra (transparente) para que el disco entero
 * reciba toques y el contenido no quede tapado. Se oculta en `nuevo`, que usa todo el alto.
 */
@Composable
fun BarraInferior(nav: NavHostController) {
    val entrada by nav.currentBackStackEntryAsState()
    val jerarquia = entrada?.destination?.hierarchy
    if (jerarquia?.any { it.route == Rutas.NUEVO } == true) return
    val c = CuentasTema.colores

    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(top = SOBRESALE_MAS)
                .fillMaxWidth()
                .background(c.superficieElevada)
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().height(GROSOR_DIVISOR).background(c.borde))
            Row(Modifier.fillMaxWidth().height(ALTO_BARRA), verticalAlignment = Alignment.CenterVertically) {
                PESTANAS_IZQUIERDA.forEach { p ->
                    ItemPestana(p, activa = jerarquia?.any { it.route == p.ruta } == true) { irAPestana(nav, p.destino, p.restaurar) }
                }
                Spacer(Modifier.weight(1f))
                PESTANAS_DERECHA.forEach { p ->
                    ItemPestana(p, activa = jerarquia?.any { it.route == p.ruta } == true) { irAPestana(nav, p.destino, p.restaurar) }
                }
            }
        }
        BotonNuevo(Modifier.align(Alignment.TopCenter)) {
            nav.navigate(Rutas.nuevo("gasto")) { launchSingleTop = true }
        }
    }
}

/**
 * Patrón estándar de pestañas: una sola copia por destino, con su estado guardado y restaurado. Con
 * `restaurar = false` (Movimientos, E25) la pestaña abre limpia: no restaura la copia con filtros de un
 * drill-down y tampoco usa `launchSingleTop`, que reutilizaría esa entrada con sus argumentos anteriores.
 */
private fun irAPestana(nav: NavHostController, destino: String, restaurar: Boolean) {
    nav.navigate(destino) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = restaurar
        restoreState = restaurar
    }
}

@Composable
private fun RowScope.ItemPestana(pestana: Pestana, activa: Boolean, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val tinta by animateColorAsState(
        if (activa) c.primario else c.textoSecundario, Movimiento.transicion(Movimiento.MEDIO), label = "tintaPestana",
    )
    val indicador by animateColorAsState(
        if (activa) c.primario.copy(alpha = TINTE_ACTIVO) else c.primario.copy(alpha = 0f),
        Movimiento.transicion(Movimiento.MEDIO), label = "indicadorPestana",
    )
    Column(
        Modifier
            .weight(1f)
            .height(ALTO_BARRA)
            .selectable(selected = activa, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.width(ANCHO_INDICADOR).height(ALTO_INDICADOR).clip(CuentasTema.formas.pildora).background(indicador),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(pestana.icono), contentDescription = null, tint = tinta, modifier = Modifier.size(ICONO_PESTANA))
        }
        Text(
            pestana.texto,
            style = CuentasTema.tipografia.etiqueta,
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BotonNuevo(modifier: Modifier, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(if (presionado) 0.97f else 1f, Movimiento.resorte(), label = "escalaNuevo")
    Box(
        modifier
            .size(DISCO_MAS)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }
            .clip(CircleShape)
            .background(c.primario)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_mas), contentDescription = "Nuevo", tint = c.sobrePrimario, modifier = Modifier.size(ICONO_MAS))
    }
}
