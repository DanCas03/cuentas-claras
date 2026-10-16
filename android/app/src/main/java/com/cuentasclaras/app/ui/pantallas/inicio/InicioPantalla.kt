package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.fechaLarga
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/** Lado mínimo del botón de modo: objetivo táctil de 48 dp (DESIGN.md §6). */
private val BOTON_MODO = 48.dp

/** Inicio: patrimonio, semana (provisional) y deudas. «Este mes» llega en la Tarea 14. */
@Composable
fun InicioPantalla(nav: NavHostController) {
    val vm = vmCon { c, _ -> InicioViewModel(c.repo, c.apariencia) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    // Mientras no hay datos no se pinta nada: sin parpadeos de cifras en cero.
    if (!estado.cargado) {
        Box(Modifier.fillMaxSize())
        return
    }
    val e = CuentasTema.espacios
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.l),
    ) {
        item(key = "cabecera") { Cabecera(estado.hoy, habilitado = !estado.guardando, onAlternarModo = { vm.alternarModo(it) }) }
        item(key = "patrimonio") { PatrimonioTarjeta(estado, onAlternarMoneda = vm::alternarMoneda) }
        item(key = "semana") { SemanaTarjeta() }
        item(key = "deudas") { DeudasTarjeta(estado, nav) }
        // Tarea 14: item(key = "este-mes") { EsteMesSeccion(estado, vm::irAMes) } (Este mes, Comparativa, Presupuestos).
    }
}

/** Fecha pequeña + título y, a la derecha, el botón sol/luna (E21) que fija el modo manual opuesto al que se ve. */
@Composable
private fun Cabecera(hoy: String, habilitado: Boolean, onAlternarModo: (oscuroAhora: Boolean) -> Unit) {
    val c = CuentasTema.colores
    val oscuro = CuentasTema.oscuro
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(fechaLarga(hoy), style = CuentasTema.tipografia.etiqueta, color = c.textoSecundario)
            Text(
                "Inicio",
                style = CuentasTema.tipografia.titulo,
                color = c.texto,
                modifier = Modifier.semantics { heading() },
            )
        }
        Box(
            Modifier
                .size(BOTON_MODO)
                .clip(CircleShape)
                .clickable(enabled = habilitado, role = Role.Button) { onAlternarModo(oscuro) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (oscuro) R.drawable.ic_sol else R.drawable.ic_luna),
                contentDescription = if (oscuro) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                tint = c.texto,
            )
        }
    }
}
