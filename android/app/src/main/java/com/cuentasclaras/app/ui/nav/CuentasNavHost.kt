package com.cuentasclaras.app.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cuentasclaras.app.ui.componentes.Proximamente
import com.cuentasclaras.app.ui.pantallas.ajustes.AjustesPantalla
import com.cuentasclaras.app.ui.pantallas.inicio.InicioPantalla
import com.cuentasclaras.app.ui.pantallas.movimientos.MovimientosPantalla
import com.cuentasclaras.app.ui.pantallas.nuevo.NuevoPantalla
import com.cuentasclaras.app.ui.theme.Movimiento

/** Argumentos de texto opcionales de una ruta con parámetros de consulta (nulos si no vienen). */
private fun argumentos(nombres: List<String>) =
    nombres.map { nombre -> navArgument(nombre) { type = NavType.StringType; nullable = true; defaultValue = null } }

/**
 * Mapa de destinos. UNA línea por destino: cada tarea de pantalla cambia solo la suya
 * (T12 NUEVO, T13 INICIO, T15 MOVIMIENTOS, T16 AJUSTES; fases 3 a 6 el resto).
 */
@Composable
fun CuentasNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = nav,
        startDestination = Rutas.INICIO,
        modifier = modifier,
        enterTransition = { fadeIn(Movimiento.transicion(Movimiento.LARGO)) },
        exitTransition = { fadeOut(Movimiento.transicion(Movimiento.CORTO)) },
        popEnterTransition = { fadeIn(Movimiento.transicion(Movimiento.LARGO)) },
        popExitTransition = { fadeOut(Movimiento.transicion(Movimiento.CORTO)) },
    ) {
        composable(Rutas.INICIO) { InicioPantalla(nav) }
        composable(Rutas.NUEVO, arguments = argumentos(Rutas.ARGS_NUEVO)) { NuevoPantalla(nav) }
        composable(Rutas.MOVIMIENTOS, arguments = argumentos(Rutas.ARGS_MOVIMIENTOS)) { MovimientosPantalla(nav) }
        composable(Rutas.AJUSTES) { AjustesPantalla(nav) }
        composable(Rutas.IMPORTAR) { Proximamente("El import del TXT del BNC llega en la fase 4") }
        composable(Rutas.DEUDAS) { Proximamente("Deudas llega en la fase 5") }
        composable(Rutas.CALENDARIO, arguments = argumentos(Rutas.ARGS_CALENDARIO)) { Proximamente("El calendario llega en la fase 6") }
        composable(Rutas.PERMISOS) { Proximamente("Permisos de captura llega en la fase 3") }
    }
}
