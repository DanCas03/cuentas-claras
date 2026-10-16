package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoFecha
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.theme.CuentasTema

private val FUENTES_ACTIVAS = listOf(RateSource.BCV, RateSource.BINANCE, RateSource.MANUAL)

private fun textoFuenteActiva(f: RateSource): String = when (f) {
    RateSource.BCV -> "BCV"
    RateSource.BINANCE -> "Binance USDT/VES"
    RateSource.MANUAL -> "Manual"
}

private val FUENTES_MANUALES = listOf(RateSource.BINANCE, RateSource.BCV, RateSource.MANUAL)

private fun textoFuenteManual(f: RateSource): String = when (f) {
    RateSource.BINANCE -> "Binance USDT/VES"
    RateSource.BCV -> "BCV"
    RateSource.MANUAL -> "Manual (override del día)"
}

/** Una tasa como la imprime la web (`111.02`, `230`): sin el «.0» que Kotlin añade a los enteros. */
private fun numeroComoWeb(v: Double): String =
    if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

/**
 * Tasas de cambio: vigente hoy (sobre el histórico completo), fuente activa, «Actualizar BCV ahora»,
 * registro manual de una tasa (Binance, BCV o override) y la tabla de las 14 más recientes.
 */
@Composable
fun TasasSeccion(
    vigente: Rate?,
    fuenteActiva: RateSource,
    recientes: List<Rate>,
    guardando: Boolean,
    buscando: Boolean,
    onCambiarFuente: (RateSource) -> Unit,
    onActualizarBcv: () -> Unit,
    onGuardarManual: (fecha: String, fuente: RateSource, valorTxt: String, alGuardar: () -> Unit) -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    var fecha by remember { mutableStateOf(todayIso()) }
    var fuenteManual by remember { mutableStateOf(RateSource.BINANCE) }
    var valorTxt by remember { mutableStateOf("") }

    Tarjeta(titulo = "Tasas de cambio") {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            Text(
                buildAnnotatedString {
                    append("Vigente hoy: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.W600)) {
                        append(
                            if (vigente != null) {
                                "${numeroComoWeb(vigente.vesPerUsd)} Bs/$ (${vigente.source.name} ${vigente.date})"
                            } else {
                                "sin tasa"
                            },
                        )
                    }
                },
                style = CuentasTema.tipografia.cuerpo,
                color = c.texto,
            )
            Selector(
                etiqueta = "Fuente activa para conversiones",
                opciones = FUENTES_ACTIVAS,
                seleccion = fuenteActiva,
                texto = ::textoFuenteActiva,
                onSeleccion = onCambiarFuente,
            )
            BotonSecundario(
                texto = if (buscando) "Buscando…" else "Actualizar BCV ahora",
                onClick = onActualizarBcv,
                enabled = !buscando,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                buildAnnotatedString {
                    append("Solo el ")
                    withStyle(SpanStyle(fontWeight = FontWeight.W600)) { append("BCV") }
                    append(" se actualiza online. ")
                    withStyle(SpanStyle(fontWeight = FontWeight.W600)) { append("Binance USDT/VES no tiene proveedor automático") }
                    append(": fíjalo tú abajo (fuente «Binance USDT/VES») y quedará guardado en el histórico.")
                },
                style = CuentasTema.tipografia.cuerpo,
                color = c.textoSecundario,
            )

            Text(
                "Registrar/corregir tasa (Bs por $)",
                style = CuentasTema.tipografia.subtitulo,
                color = c.texto,
                modifier = Modifier.semantics { heading() },
            )
            CampoFecha(etiqueta = "Fecha de tasa", iso = fecha, onCambio = { fecha = it })
            Selector(
                etiqueta = "Fuente de la tasa manual",
                opciones = FUENTES_MANUALES,
                seleccion = fuenteManual,
                texto = ::textoFuenteManual,
                onSeleccion = { fuenteManual = it },
            )
            CampoTexto(
                valor = valorTxt,
                onCambio = { valorTxt = it },
                etiqueta = "Valor de tasa",
                placeholder = "ej. 111,02",
                teclado = KeyboardType.Decimal,
            )
            BotonPrincipal(
                texto = "Guardar",
                onClick = { onGuardarManual(fecha, fuenteManual, valorTxt) { valorTxt = "" } },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Histórico reciente",
                style = CuentasTema.tipografia.subtitulo,
                color = c.texto,
                modifier = Modifier.semantics { heading() },
            )
            TablaHistorico(recientes)
        }
    }
}

/** Fecha · fuente · Bs/$ de las tasas más recientes, con cifras tabulares. */
@Composable
private fun TablaHistorico(recientes: List<Rate>) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val cifra = CuentasTema.tipografia.cuerpo.copy(fontFeatureSettings = "tnum")
    if (recientes.isEmpty()) {
        Vacio("📈", "Aún no hay tasas registradas.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EncabezadoColumna("Fecha", Modifier.weight(PESO_FECHA))
            EncabezadoColumna("Fuente", Modifier.weight(PESO_FUENTE))
            EncabezadoColumna("Bs/$", Modifier.weight(PESO_VALOR), TextAlign.End)
        }
        recientes.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = e.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.date, style = cifra, color = c.texto, modifier = Modifier.weight(PESO_FECHA))
                Row(Modifier.weight(PESO_FUENTE)) { Chip(r.source.name) }
                Text(
                    numeroComoWeb(r.vesPerUsd),
                    style = cifra,
                    color = c.texto,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(PESO_VALOR),
                )
            }
        }
    }
}

private const val PESO_FECHA = 1.3f
private const val PESO_FUENTE = 1.2f
private const val PESO_VALOR = 1f

@Composable
private fun EncabezadoColumna(texto: String, modifier: Modifier, alineacion: TextAlign = TextAlign.Start) {
    Text(
        texto,
        style = CuentasTema.tipografia.etiqueta,
        color = CuentasTema.colores.textoSecundario,
        textAlign = alineacion,
        modifier = modifier,
    )
}
