package com.cuentasclaras.app.ui.pantallas.respaldo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.backup.Conteos
import com.cuentasclaras.app.backup.RespaldoSeguridad
import com.cuentasclaras.app.domain.fechaCorta
import com.cuentasclaras.app.ui.componentes.AvisosDeCapa
import com.cuentasclaras.app.ui.componentes.BotonPeligro
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.DialogoConfirmacion
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon

/** «16 oct 2026 · 15:30»: cuándo se guardó un respaldo de seguridad. */
private fun momentoLegible(r: RespaldoSeguridad): String = "${fechaCorta(r.fecha)} ${r.fecha.take(4)} · ${r.hora}"

/**
 * Respaldo: exportar, restaurar con confirmación, respaldos de seguridad y tabla de conteos. La usa Ajustes (Tarea
 * 16); se dibuja como una `Tarjeta` más, así que no hace scroll propio. La confirmación de restaurar (que reemplaza
 * todo) es un `AlertDialog`.
 *
 * «Respaldos de seguridad» (I3): los previos que guarda cada restauración en `filesDir/respaldos/` (máx. 5), cada uno
 * con «Restaurar» tras `DialogoConfirmacion`. Se leen directamente de la carpeta, sin selector de archivos.
 */
@Composable
fun RespaldoSeccion() {
    val contexto = LocalContext.current
    val vm = vmCon { c, _ -> RespaldoViewModel(c.db, c.dirRespaldos) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val c = CuentasTema.colores
    val e = CuentasTema.espacios

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportarA(contexto.contentResolver, uri)
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.elegirArchivo(contexto.contentResolver, uri)
    }

    Tarjeta(titulo = "Respaldo") {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            Text(
                "Exporta tu respaldo y guárdalo en Google Drive desde el selector de archivos.",
                style = CuentasTema.tipografia.cuerpo,
                color = c.textoSecundario,
            )
            BotonSecundario(
                texto = "Exportar respaldo",
                onClick = { exportar.launch(vm.nombreSugerido()) },
                enabled = !estado.guardando,
                modifier = Modifier.fillMaxWidth(),
            )
            BotonSecundario(
                texto = "Restaurar desde un respaldo",
                onClick = { restaurar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !estado.guardando,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Respaldos de seguridad",
                style = CuentasTema.tipografia.subtitulo,
                color = c.texto,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Antes de cada restauración se guarda aquí una copia de lo que había (se conservan las 5 últimas).",
                style = CuentasTema.tipografia.cuerpo,
                color = c.textoSecundario,
            )
            if (estado.seguridad.isEmpty()) {
                Text(
                    "Aún no hay respaldos de seguridad.",
                    style = CuentasTema.tipografia.cuerpo,
                    color = c.textoSecundario,
                )
            } else {
                Column {
                    estado.seguridad.forEach { r ->
                        key(r.archivo.name) {
                            FilaSeguridad(
                                r = r,
                                enabled = !estado.guardando,
                                onRestaurar = { vm.pedirRestaurarSeguridad(r) },
                            )
                        }
                    }
                }
            }
            Text(
                "Datos en la app",
                style = CuentasTema.tipografia.subtitulo,
                color = c.texto,
                modifier = Modifier.semantics { heading() },
            )
            estado.conteos?.let { TablaConteos(it) }
        }
    }

    estado.pendiente?.let { pendiente ->
        AlertDialog(
            onDismissRequest = { if (!estado.guardando) vm.cancelar() },
            containerColor = c.superficieElevada,
            titleContentColor = c.texto,
            textContentColor = c.texto,
            title = { Text("Restaurar respaldo", style = CuentasTema.tipografia.titulo) },
            text = {
                Column {
                    Text(
                        buildAnnotatedString {
                            append("Vas a ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("reemplazar TODOS los datos actuales") }
                            append(" por: ${pendiente.resumen}. Antes se guardará un respaldo de seguridad de lo actual.")
                        },
                        style = CuentasTema.tipografia.cuerpo,
                    )
                    // D2: si la restauración falla, el diálogo sigue abierto y el aviso se pinta aquí.
                    AvisosDeCapa()
                }
            },
            confirmButton = {
                BotonPeligro("Sí, reemplazar todo", onClick = vm::restaurar, enabled = !estado.guardando)
            },
            dismissButton = {
                BotonSecundario("Cancelar", onClick = vm::cancelar, enabled = !estado.guardando)
            },
        )
    }

    estado.seguridadPorRestaurar?.let { r ->
        DialogoConfirmacion(
            titulo = "Restaurar respaldo de seguridad",
            texto = "Vas a reemplazar TODOS los datos actuales por el respaldo de seguridad del " +
                "${momentoLegible(r)}. Antes se guardará un respaldo de seguridad de lo actual.",
            textoConfirmar = "Sí, reemplazar todo",
            onConfirmar = vm::restaurarSeguridad,
            onCancelar = vm::cancelarSeguridad,
            textoCancelar = "Cancelar",
            habilitado = !estado.guardando,
        )
    }
}

/** Una fila de «Respaldos de seguridad»: cuándo se guardó y su botón «Restaurar». */
@Composable
private fun FilaSeguridad(r: RespaldoSeguridad, enabled: Boolean, onRestaurar: () -> Unit) {
    val e = CuentasTema.espacios
    val momento = momentoLegible(r)
    Row(
        Modifier.fillMaxWidth().padding(vertical = e.xs),
        horizontalArrangement = Arrangement.spacedBy(e.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            momento,
            style = CuentasTema.tipografia.cuerpo,
            color = CuentasTema.colores.texto,
            modifier = Modifier.weight(1f),
        )
        BotonSecundario(
            texto = "Restaurar",
            onClick = onRestaurar,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Restaurar el respaldo de seguridad del $momento" },
        )
    }
}

@Composable
private fun TablaConteos(c: Conteos) {
    val e = CuentasTema.espacios
    val colores = CuentasTema.colores
    val filas = listOf(
        "Cuentas" to c.accounts, "Movimientos" to c.transactions, "Categorías" to c.categories,
        "Reglas" to c.rules, "Tasas" to c.rates, "Ajustes" to c.settings,
        "Deudas" to c.debts, "Grupos" to c.txGroups, "Eventos" to c.calEvents,
    )
    Column {
        filas.forEach { (nombre, n) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = e.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(nombre, style = CuentasTema.tipografia.cuerpo, color = colores.textoSecundario)
                Text(n.toString(), style = CuentasTema.tipografia.cifra, color = colores.texto)
            }
        }
    }
}
