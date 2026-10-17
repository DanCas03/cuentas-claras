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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.CuentasApp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.fechaLarga
import com.cuentasclaras.app.ui.LocalPrimerContenidoListo
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/** Inicio: patrimonio, «Este mes» con su comparativa y presupuestos, «Esta semana» (calendario) y deudas. */
@Composable
fun InicioPantalla(nav: NavHostController) {
    val vm = vmCon { c, _ -> InicioViewModel(c.repo, c.apariencia, c.capturas) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    // I1: al volver a la app (quizá al día siguiente, con el proceso vivo) se toma de nuevo la fecha de hoy.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refrescarHoy() }
    // Mientras no hay datos no se pinta nada: sin parpadeos de cifras en cero. En el arranque en frío ese hueco no
    // llega a verse: MainActivity retiene la ventana de arranque hasta el aviso de abajo (V25).
    if (!estado.cargado) {
        Box(Modifier.fillMaxSize())
        return
    }
    val avisarListo = LocalPrimerContenidoListo.current
    SideEffect { avisarListo() }
    val e = CuentasTema.espacios
    // El acceso a notificaciones se puede revocar fuera de la app: se vuelve a comprobar al volver a primer plano.
    val listener = (LocalContext.current.applicationContext as CuentasApp).container.estadoListener
    var acceso by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { acceso = listener.accesoConcedido() }
    // D1: acceso concedido pero listener sin enlazar y re-enlace frenado (MIUI sin Autoinicio). Se apaga al conectar.
    val enPausa by listener.enPausa.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.l),
    ) {
        item(key = "cabecera") { Cabecera(estado.hoy, habilitado = !estado.guardando, onAlternarModo = { vm.alternarModo(it) }) }
        if (!acceso) {
            item(key = "acceso-perdido") {
                Banner(
                    texto = "La app perdió el acceso a notificaciones",
                    tipo = TipoBanner.ERROR,
                    accion = { Chip("Arreglar", onClick = { nav.navigate(Rutas.PERMISOS) }) },
                )
            }
        } else if (enPausa) {
            item(key = "captura-en-pausa") {
                Banner(
                    // Fiel al código: activar Autoinicio no re-enlaza al momento; el re-enlace llega en un onResume
                    // pasado el freno (≤ 10 min desde el último), o antes si el sistema re-enlaza solo.
                    texto = "Captura en pausa. Activa Autoinicio; se reanuda al volver a la app en unos minutos.",
                    tipo = TipoBanner.AVISO,
                    accion = { Chip("Arreglar", onClick = { nav.navigate(Rutas.PERMISOS) }) },
                )
            }
        }
        if (estado.capturasPendientes.isNotEmpty()) {
            item(key = "capturas") {
                CapturasTarjeta(
                    capturas = estado.capturasPendientes,
                    habilitado = !estado.guardando,
                    // Igual que el toque en la notificación (MainActivity): nunca dos Nuevo apilados.
                    onRegistrar = { nav.navigate(Rutas.nuevo(capturaId = it)) { launchSingleTop = true } },
                    onDescartar = vm::descartarCaptura,
                )
            }
        }
        item(key = "patrimonio") { PatrimonioTarjeta(estado, onAlternarMoneda = vm::alternarMoneda) }
        // DESIGN.md §1: «Este mes» va justo después del patrimonio.
        item(key = "este-mes") {
            EsteMesSeccion(
                estado,
                onMes = vm::irAMes,
                onCategoria = { id, mes ->
                    nav.navigate(Rutas.movimientos(categoria = id, mes = mes)) { launchSingleTop = true }
                },
            )
        }
        item(key = "semana") { SemanaTarjeta(estado, nav) }
        item(key = "deudas") { DeudasTarjeta(estado, nav) }
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
                .size(CuentasTema.medidas.tactilMinimo)
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
