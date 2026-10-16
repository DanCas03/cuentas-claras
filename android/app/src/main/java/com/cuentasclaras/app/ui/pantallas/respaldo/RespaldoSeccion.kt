package com.cuentasclaras.app.ui.pantallas.respaldo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.backup.Conteos
import com.cuentasclaras.app.ui.vmCon

/** Respaldo: exportar, restaurar con confirmación y tabla de conteos. La reutiliza Ajustes (Tarea 16). */
@Composable
fun RespaldoSeccion() {
    val contexto = LocalContext.current
    val vm = vmCon { c, _ -> RespaldoViewModel(c.db, c.dirRespaldos) }
    val estado by vm.estado.collectAsStateWithLifecycle()

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportarA(contexto.contentResolver, uri)
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.elegirArchivo(contexto.contentResolver, uri)
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Respaldo", style = MaterialTheme.typography.headlineSmall)
        Text("Exporta tu respaldo y guárdalo en Google Drive desde el selector de archivos.")
        Button(
            onClick = { exportar.launch(vm.nombreSugerido()) },
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exportar respaldo") }
        OutlinedButton(
            onClick = { restaurar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restaurar desde un respaldo") }

        estado.pendiente?.let { pendiente ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Vas a ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("reemplazar TODOS los datos actuales") }
                            append(" por: ${pendiente.resumen}. Antes se guardará un respaldo de seguridad de lo actual.")
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::cancelar, enabled = !estado.guardando) { Text("Cancelar") }
                        Button(onClick = vm::restaurar, enabled = !estado.guardando) { Text("Sí, reemplazar todo") }
                    }
                }
            }
        }

        Text("Datos en la app", style = MaterialTheme.typography.titleMedium)
        estado.conteos?.let { TablaConteos(it) }
    }
}

@Composable
private fun TablaConteos(c: Conteos) {
    val filas = listOf(
        "Cuentas" to c.accounts, "Movimientos" to c.transactions, "Categorías" to c.categories,
        "Reglas" to c.rules, "Tasas" to c.rates, "Ajustes" to c.settings,
        "Deudas" to c.debts, "Grupos" to c.txGroups, "Eventos" to c.calEvents,
    )
    Column {
        filas.forEachIndexed { i, (nombre, n) ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(nombre)
                Text(n.toString(), fontWeight = FontWeight.Bold)
            }
        }
    }
}
