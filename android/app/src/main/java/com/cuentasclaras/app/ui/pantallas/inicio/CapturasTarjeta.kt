package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cuentasclaras.app.capture.Notificador
import com.cuentasclaras.app.data.db.CapturaEntity
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.theme.CuentasTema
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** `recibidaIso` es un instante UTC (no una fecha de calendario): se muestra en la hora local del teléfono. */
internal fun horaLocal(recibidaIso: String): String =
    runCatching { FORMATO_HORA.format(Instant.parse(recibidaIso).atZone(ZoneId.systemDefault())) }.getOrDefault("")

/** Capturas del BNC pendientes (spec §7.3): se registran en Nuevo (2 toques) o se descartan. Solo se pinta con n > 0. */
@Composable
fun CapturasTarjeta(
    capturas: List<CapturaEntity>,
    habilitado: Boolean,
    onRegistrar: (id: String) -> Unit,
    onDescartar: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    Tarjeta(modifier = modifier, titulo = "Capturas pendientes (${capturas.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            capturas.forEach { captura ->
                Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                    Column {
                        Text(Notificador.titulo(captura), style = t.cuerpo, color = c.texto)
                        Text(horaLocal(captura.recibidaIso), style = t.etiqueta, color = c.textoSecundario)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(e.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Chip("Registrar", onClick = { onRegistrar(captura.id) }, activo = true, enabled = habilitado)
                        Chip("Descartar", onClick = { onDescartar(captura.id) }, enabled = habilitado)
                    }
                }
            }
        }
    }
}
