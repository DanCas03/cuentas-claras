package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `DatePickerState.selectedDateMillis` es medianoche **UTC**: convertir con la zona local corre el día en
 * Venezuela (UTC−4). Por eso ambas conversiones usan `ZoneOffset.UTC`.
 */
fun isoDesdeDatePicker(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

fun millisParaDatePicker(iso: String): Long =
    java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

private val FORMATO_FECHA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))

/** «jueves 15 de octubre de 2026»; si el texto no es una fecha ISO, lo muestra tal cual. */
private fun fechaLegible(iso: String): String =
    runCatching { LocalDate.parse(iso).format(FORMATO_FECHA) }.getOrDefault(iso)

/** Campo de fecha (`yyyy-mm-dd`) que abre el `DatePickerDialog` de Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(etiqueta: String, iso: String, onCambio: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    var abierto by remember { mutableStateOf(false) }
    val legible = fechaLegible(iso)
    Column(modifier.fillMaxWidth()) {
        EtiquetaCampo(etiqueta)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_CAMPO)
                .clip(CuentasTema.formas.control)
                .background(c.borde)
                .clickable(role = Role.Button, onClickLabel = "Cambiar fecha") { abierto = true }
                .semantics(mergeDescendants = true) { contentDescription = "$etiqueta: $legible" }
                .padding(horizontal = e.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                legible,
                style = CuentasTema.tipografia.cuerpo,
                color = c.texto,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            Icon(
                painterResource(R.drawable.ic_calendario),
                contentDescription = null,
                tint = c.textoSecundario,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (abierto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { millisParaDatePicker(iso) }.getOrNull(),
        )
        val colores = DatePickerDefaults.colors(containerColor = c.superficie)
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onCambio(isoDesdeDatePicker(it)) }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { abierto = false }) { Text("Cancelar") }
            },
            colors = colores,
        ) {
            DatePicker(state = estado, colors = colores)
        }
    }
}

@Preview
@Composable
private fun VistaCampoFecha() {
    VistaPreviaComponente {
        var fecha by remember { mutableStateOf("2026-10-15") }
        CampoFecha("Fecha", fecha, { fecha = it })
    }
}
