package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.pantallas.respaldo.RespaldoSeccion
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/**
 * Ajustes: apariencia, cuentas, categorías, tasas, reglas, respaldo y el enlace a Permisos de captura.
 * `nav` queda para la Tarea 21, que activa la fila de Permisos (`Rutas.PERMISOS`).
 */
@Suppress("UNUSED_PARAMETER")
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
        item(key = "apariencia") { AparienciaSeccion() }
        if (datos != null) {
            item(key = "cuentas") {
                CuentasSeccion(
                    cuentas = datos.cuentas,
                    guardando = estado.ocupado.guardando,
                    onGuardar = vm::guardarCuenta,
                )
            }
            item(key = "categorias") {
                CategoriasSeccion(
                    categorias = datos.categorias,
                    guardando = estado.ocupado.guardando,
                    onGuardar = vm::guardarCategoria,
                )
            }
            item(key = "tasas") {
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
            item(key = "reglas") {
                ReglasSeccion(
                    reglas = datos.reglas,
                    categorias = datos.categorias,
                    guardando = estado.ocupado.guardando,
                    onEliminar = vm::borrarRegla,
                )
            }
        }
        item(key = "respaldo") { RespaldoSeccion() }
        item(key = "permisos") { FilaPermisos() }
    }
}

/** «Permisos de captura»: deshabilitado hasta la fase 3, en la que la Tarea 21 lo enlaza a `Rutas.PERMISOS`. */
@Composable
private fun FilaPermisos() {
    Tarjeta(titulo = "Permisos de captura") {
        Text("Disponible en la fase 3", style = CuentasTema.tipografia.cuerpo, color = CuentasTema.colores.textoSecundario)
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
