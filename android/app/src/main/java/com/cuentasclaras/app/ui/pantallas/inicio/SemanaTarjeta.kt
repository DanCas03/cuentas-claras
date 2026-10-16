package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.Vacio

/** Placeholder de la fase 2 (spec §7.2): la Tarea 30 la reemplaza por la semana real con ocurrencias. */
@Composable
fun SemanaTarjeta(onCalendario: () -> Unit, modifier: Modifier = Modifier) {
    Tarjeta(
        modifier = modifier,
        titulo = "Esta semana",
        accion = { Chip("Calendario", onClick = onCalendario) },
    ) {
        Vacio("📅", "Sin eventos")
    }
}
