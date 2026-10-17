package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.SeccionPlegable
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.pantallas.respaldo.RespaldoSeccion
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/**
 * Ajustes (spec 2026-10-17 §3): la fila «Apariencia ›» (pantalla propia) y las secciones plegables de cuentas,
 * categorías, tasas, reglas, respaldo y permisos de captura, todas cerradas al entrar.
 */
@Composable
fun AjustesPantalla(nav: NavHostController) {
    val vm = vmCon { c, _ -> AjustesViewModel(c.repo, c.http) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val e = CuentasTema.espacios
    val datos = estado.datos

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.l),
    ) {
        item(key = "titulo") {
            Text(
                "Ajustes",
                style = CuentasTema.tipografia.titulo,
                color = CuentasTema.colores.texto,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "apariencia") { FilaApariencia(onAbrir = { nav.navigate(Rutas.APARIENCIA) }) }
        if (datos != null) {
            seccion("cuentas", "Cuentas") {
                CuentasSeccion(
                    cuentas = datos.cuentas,
                    guardando = estado.ocupado.guardando,
                    onGuardar = vm::guardarCuenta,
                )
            }
            seccion("categorias", "Categorías") {
                CategoriasSeccion(
                    categorias = datos.categorias,
                    guardando = estado.ocupado.guardando,
                    onGuardar = vm::guardarCategoria,
                )
            }
            seccion("tasas", "Tasas de cambio") {
                TasasSeccion(
                    vigente = estado.vigente,
                    fuenteActiva = datos.settings.rateSource,
                    recientes = estado.recientes,
                    guardando = estado.ocupado.guardando,
                    buscando = estado.ocupado.buscando,
                    onCambiarFuente = vm::cambiarFuente,
                    onActualizarBcv = vm::actualizarBcvAhora,
                    onGuardarManual = vm::guardarTasaManual,
                )
            }
            seccion("reglas", "Reglas aprendidas") {
                ReglasSeccion(
                    reglas = datos.reglas,
                    categorias = datos.categorias,
                    guardando = estado.ocupado.guardando,
                    onEliminar = vm::borrarRegla,
                )
            }
        }
        seccion("respaldo", "Respaldo") { RespaldoSeccion() }
        seccion("permisos", "Permisos de captura") { ContenidoPermisos(onAbrir = { nav.navigate(Rutas.PERMISOS) }) }
    }
}

/** Una sección plegable de Ajustes: cerrada al entrar; su estado sobrevive a la rotación (`rememberSaveable`). */
private fun LazyListScope.seccion(clave: String, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    item(key = clave) {
        var abierta by rememberSaveable { mutableStateOf(false) }
        SeccionPlegable(titulo, abierta, onAlternar = { abierta = !abierta }, contenido = contenido)
    }
}

/** «Apariencia ›»: lleva a la pantalla Apariencia. La fila entera es el objetivo (≥ 48 dp). */
@Composable
private fun FilaApariencia(onAbrir: () -> Unit) {
    val c = CuentasTema.colores
    Tarjeta {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CuentasTema.medidas.tactilMinimo)
                .clip(CuentasTema.formas.control)
                .clickable(role = Role.Button, onClick = onAbrir),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Apariencia", style = CuentasTema.tipografia.subtitulo, color = c.texto, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_expandir),
                contentDescription = null,
                tint = c.textoSecundario,
                modifier = Modifier.graphicsLayer { rotationZ = -90f },
            )
        }
    }
}

/**
 * «Permisos de captura»: lleva a la lista de verificación de la captura de notificaciones. Un solo objetivo táctil,
 * «Revisar permisos» (V14).
 */
@Composable
private fun ContenidoPermisos(onAbrir: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s)) {
        Text(
            "Acceso a notificaciones, batería y defensas de Xiaomi.",
            style = CuentasTema.tipografia.cuerpo,
            color = CuentasTema.colores.textoSecundario,
        )
        BotonSecundario("Revisar permisos", onClick = onAbrir, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Fila de lista editable: contenido a la izquierda (nombre y chips) y una acción en chip a la derecha
 * («editar», «eliminar»). `descripcionAccion` es el `contentDescription` de la acción («Editar Efectivo»).
 */
@Composable
internal fun FilaConAccion(
    accion: String,
    descripcionAccion: String,
    onAccion: () -> Unit,
    habilitada: Boolean = true,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val e = CuentasTema.espacios
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(e.xs), content = contenido)
        Chip(accion, onClick = onAccion, enabled = habilitada, modifier = Modifier.semantics { contentDescription = descripcionAccion })
    }
}

/** Botones de una hoja de edición: el principal arriba y «Cancelar» debajo, a ancho completo (sin recortar textos). */
@Composable
internal fun AccionesDeHoja(
    textoGuardar: String,
    guardando: Boolean,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.s)) {
        BotonPrincipal(textoGuardar, onClick = onGuardar, enabled = !guardando, modifier = Modifier.fillMaxWidth())
        BotonSecundario("Cancelar", onClick = onCancelar, modifier = Modifier.fillMaxWidth())
    }
}
