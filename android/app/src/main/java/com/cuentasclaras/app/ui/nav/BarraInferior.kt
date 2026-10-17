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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.EstiloBarra
import com.cuentasclaras.app.ui.theme.FormaMas
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
private val GROSOR_SUBRAYADO = 2.dp

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
 * Barra inferior: Inicio, Movs, «+» central (acción principal), Importar, Ajustes. Su aspecto lo decide
 * `CuentasTema.diseno.barra` (spec 2026-10-17 §6):
 * - `PILDORA` (Libreta): franja en `superficieElevada` con divisor de 1 dp; pestaña activa con píldora `primario`.
 * - `SUBRAYADO` (Tinta): misma franja; pestaña activa con subrayado de 2 dp en `primario` bajo el ícono.
 * - `FLOTANTE` (Isla): píldora despegada del borde (margen `m` a los lados y `s` abajo) sin divisor; el «+» va
 *   dentro de la píldora y no sobresale.
 * - `AURORA`: Task 7 del plan; mientras tanto se pinta como `PILDORA`.
 * El «+» sobresale `SOBRESALE_MAS` hacia arriba; esa franja forma parte de la barra (transparente) para que el
 * disco entero reciba toques y el contenido no quede tapado. `etiquetas = false` deja solo los íconos (el nombre
 * pasa al `contentDescription`). Se oculta en `nuevo`, que usa todo el alto.
 */
@Composable
fun BarraInferior(nav: NavHostController, etiquetas: Boolean = true) {
    val entrada by nav.currentBackStackEntryAsState()
    val jerarquia = entrada?.destination?.hierarchy
    if (jerarquia?.any { it.route == Rutas.NUEVO } == true) return
    val c = CuentasTema.colores
    val d = CuentasTema.diseno
    val e = CuentasTema.espacios
    val irANuevo = { nav.navigate(Rutas.nuevo("gasto")) { launchSingleTop = true } }
    val pestanas: @Composable RowScope.() -> Unit = {
        PESTANAS_IZQUIERDA.forEach { p ->
            ItemPestana(p, activa = jerarquia?.any { it.route == p.ruta } == true, etiquetas, d.barra) { irAPestana(nav, p) }
        }
        Spacer(Modifier.weight(1f))
        PESTANAS_DERECHA.forEach { p ->
            ItemPestana(p, activa = jerarquia?.any { it.route == p.ruta } == true, etiquetas, d.barra) { irAPestana(nav, p) }
        }
    }

    if (d.barra == EstiloBarra.FLOTANTE) {
        // Isla: la barra es una píldora despegada; el «+» vive dentro y no sobresale.
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = e.m, end = e.m, bottom = e.s)) {
            Row(
                Modifier.fillMaxWidth().clip(CuentasTema.formas.pildora).background(c.superficieElevada).height(ALTO_BARRA),
                verticalAlignment = Alignment.CenterVertically,
                content = pestanas,
            )
            BotonNuevo(Modifier.align(Alignment.Center), d.botonMas, irANuevo)
        }
        return
    }

    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(top = SOBRESALE_MAS)
                .fillMaxWidth()
                .background(c.superficieElevada)
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().height(GROSOR_DIVISOR).background(c.borde))
            Row(Modifier.fillMaxWidth().height(ALTO_BARRA), verticalAlignment = Alignment.CenterVertically, content = pestanas)
        }
        BotonNuevo(Modifier.align(Alignment.TopCenter), d.botonMas, irANuevo)
    }
}

/**
 * Navegación de la barra inferior. Describe lo que hace de verdad; el comportamiento está aceptado tal cual
 * (V5 aparcado por el ruling «Final: parked — BarraInferior.kt:130… se acepta»).
 *
 * 1. Tocar una pestaña que restaura (Inicio, Importar, Ajustes) y que YA está en la pila (M2): se vuelve a su raíz
 *    con `popBackStack(ruta, inclusive = false)`, que saca todo lo de encima SIN `saveState`, y no se navega;
 *    estando ya en su raíz no se hace nada. Inicio es el destino inicial y siempre está en la pila, así que tocar
 *    «Inicio» desde cualquier otra pestaña entra por aquí: la pestaña de la que se sale (Importar, Ajustes,
 *    Movimientos, Deudas, Calendario…) se saca sin guardar su estado, su `ViewModel` se limpia y, al volver a ella,
 *    abre limpia (Importar pierde el texto pegado y la vista previa). Si se navegara en su lugar,
 *    `popUpTo(start) { saveState = true }` guardaría la pila de encima asociada a la propia Inicio y
 *    `restoreState` la volvería a poner: Inicio → «ver todas» Deudas → «Inicio» dejaba Deudas.
 * 2. Tocar una pestaña que NO está en la pila (p. ej. Ajustes estando en Importar): antes se sacan todas las
 *    entradas de Movimientos sin guardar (`popBackStack(..., saveState = false)`: `popBackStack` solo limpia el
 *    `ViewModelStore` de una entrada si no se guarda su estado, y Movimientos nunca se restaura) y luego se navega
 *    con `popUpTo(start) { saveState = true }` + `restoreState`: la pestaña de la que se sale guarda su estado y la
 *    de destino recupera el suyo, si lo tenía. Así Ajustes → Permisos → Importar → Ajustes vuelve a Permisos, y
 *    Importar → Ajustes → Importar conserva lo pegado.
 *
 * Tocar «Inicio» (punto 1) solo descarta la pestaña DESDE la que se toca, la que está en la pila en ese momento.
 * No borra el estado que otra pestaña ya guardó antes por el punto 2: Importar → Ajustes → «Inicio» → Importar
 * recupera lo pegado (el estado de Importar se guardó al ir a Ajustes y `restoreState` lo devuelve); en cambio,
 * Importar → «Inicio» → Importar llega vacío.
 * 3. Movimientos (`restaurar = false`, E25) abre siempre limpio: no restaura la copia con filtros de un drill-down
 *    ni usa `launchSingleTop`, que reutilizaría esa entrada con sus argumentos anteriores. Tocar «Movs» estando en
 *    Movimientos sin filtros no hace nada.
 */
private fun irAPestana(nav: NavHostController, p: Pestana) {
    val actual = nav.currentBackStackEntry
    if (p.restaurar && (actual?.destination?.route == p.ruta || nav.popBackStack(p.ruta, inclusive = false))) return
    val destino = p.destino
    val restaurar = p.restaurar
    val enMovimientos = actual?.destination?.route == Rutas.MOVIMIENTOS
    // Tocar «Movs» estando en Movimientos sin filtros no hace nada (como el mismo hash en la web).
    if (!restaurar && enMovimientos && Rutas.ARGS_MOVIMIENTOS.none { actual.arguments?.getString(it) != null }) return
    while (nav.popBackStack(Rutas.MOVIMIENTOS, inclusive = true, saveState = false)) { /* una por entrada */ }
    nav.navigate(destino) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = restaurar
        restoreState = restaurar
    }
}

@Composable
private fun RowScope.ItemPestana(
    pestana: Pestana,
    activa: Boolean,
    etiquetas: Boolean,
    estilo: EstiloBarra,
    onClick: () -> Unit,
) {
    val c = CuentasTema.colores
    val nivel = CuentasTema.diseno.movimiento
    val tinta by animateColorAsState(
        if (activa) c.primario else c.textoSecundario, Movimiento.transicion(Movimiento.MEDIO, nivel), label = "tintaPestana",
    )
    // Píldora activa en `primario` sólido con el ícono en `sobrePrimario` (V9): ≥ 4,99:1 frente a la barra
    // (`superficieElevada`) en todas las paletas; el tinte al 14 % se quedaba en 1,2-1,45:1 y no se veía.
    val indicador by animateColorAsState(
        if (activa) c.primario else c.primario.copy(alpha = 0f),
        Movimiento.transicion(Movimiento.MEDIO, nivel), label = "indicadorPestana",
    )
    val conPildora = estilo != EstiloBarra.SUBRAYADO
    val tintaIcono by animateColorAsState(
        if (activa && conPildora) c.sobrePrimario else if (activa) c.primario else c.textoSecundario,
        Movimiento.transicion(Movimiento.MEDIO, nivel), label = "iconoPestana",
    )
    Column(
        Modifier
            .weight(1f)
            .height(ALTO_BARRA)
            .selectable(selected = activa, role = Role.Tab, onClick = onClick)
            .then(if (etiquetas) Modifier else Modifier.semantics { contentDescription = pestana.texto }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (conPildora) {
            Box(
                Modifier.width(ANCHO_INDICADOR).height(ALTO_INDICADOR).clip(CuentasTema.formas.pildora).background(indicador),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(pestana.icono), contentDescription = null, tint = tintaIcono, modifier = Modifier.size(ICONO_PESTANA))
            }
        } else {
            Box(Modifier.height(ALTO_INDICADOR), contentAlignment = Alignment.Center) {
                Icon(painterResource(pestana.icono), contentDescription = null, tint = tintaIcono, modifier = Modifier.size(ICONO_PESTANA))
            }
            Box(Modifier.width(ANCHO_INDICADOR).height(GROSOR_SUBRAYADO).clip(CuentasTema.formas.pildora).background(indicador))
        }
        if (etiquetas) {
            Text(
                pestana.texto,
                style = CuentasTema.tipografia.etiqueta,
                color = tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BotonNuevo(modifier: Modifier, forma: FormaMas, onClick: () -> Unit) {
    val c = CuentasTema.colores
    val d = CuentasTema.diseno
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(if (presionado) d.escalaPresion else 1f, Movimiento.resorte(d.movimiento), label = "escalaNuevo")
    val silueta = when (forma) {
        FormaMas.DISCO, FormaMas.GOTA -> CircleShape
        FormaMas.CUADRADO -> CuentasTema.formas.control
    }
    Box(
        modifier
            .size(DISCO_MAS)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }
            .clip(silueta)
            .background(c.primario)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_mas), contentDescription = "Nuevo", tint = c.sobrePrimario, modifier = Modifier.size(ICONO_MAS))
    }
}
