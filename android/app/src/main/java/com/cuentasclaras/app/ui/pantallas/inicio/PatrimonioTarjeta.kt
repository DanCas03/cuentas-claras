package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.Patrimonio
import com.cuentasclaras.app.domain.PatrimonioLinea
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.usdToVesMinor
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.Movimiento
import java.math.BigDecimal

/** Cifra de una cantidad en USD según la moneda de la vista (misma regla que `enVista` de la web). */
internal fun enVista(usdMinor: Long?, vista: Currency, tasa: Rate?): String {
    if (usdMinor == null) return "sin tasa"
    if (vista == Currency.USD) return formatMoney(usdMinor, Currency.USD)
    if (tasa == null) return "sin tasa"
    return formatMoney(usdToVesMinor(usdMinor, tasa.vesPerUsd), Currency.VES)
}

/** Equivalencia en la OTRA moneda (DESIGN.md §1); `null` si no se puede calcular. */
private fun equivalencia(usdMinor: Long?, vista: Currency, tasa: Rate?): String? {
    if (usdMinor == null) return null
    return if (vista == Currency.USD) {
        tasa?.let { "≈ " + formatMoney(usdToVesMinor(usdMinor, it.vesPerUsd), Currency.VES) }
    } else {
        "≈ " + formatMoney(usdMinor, Currency.USD)
    }
}

/** Como el `${number}` de JS: 185.5 → «185.5», 36.0 → «36», sin notación científica. */
private fun tasaTexto(vesPerUsd: Double): String =
    BigDecimal.valueOf(vesPerUsd).stripTrailingZeros().toPlainString()

private const val ESPACIO_FIJO = " "

/** Cifra más grande y más pequeña del texto de la cifra principal si una cantidad en Bs no cabe. */
private val CIFRA_MINIMA = 24.sp
private val CIFRA_MAXIMA = 40.sp

/** Diámetro de cada punto del indicador del slider. */
private val PUNTO = 8.dp

private class VistaPatrimonio(val titulo: String, val usdMinor: Long?)

/**
 * Patrimonio (contrato de `PatrimonioCard.tsx`): rótulo + chip de moneda, slider de 3 vistas con la cifra grande
 * sin caja (dirección A), línea de tasa y la lista de cuentas activas.
 */
@Composable
fun PatrimonioTarjeta(
    estado: InicioViewModel.Estado,
    onAlternarMoneda: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p: Patrimonio = estado.patrimonio ?: return
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    val vistas = listOf(
        VistaPatrimonio("A disposición", p.aDisposicionUsdMinor),
        VistaPatrimonio("Total neto", p.netoUsdMinor),
        VistaPatrimonio("Total actual", p.totalUsdMinor),
    )
    val pager = rememberPagerState { vistas.size }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PATRIMONIO",
                style = t.etiqueta.copy(letterSpacing = 0.12.em),
                color = c.textoSecundario,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Chip(
                if (estado.viewCurrency == Currency.USD) "ver en Bs" else "ver en $",
                onClick = onAlternarMoneda,
            )
        }

        HorizontalPager(state = pager, pageSpacing = e.m, modifier = Modifier.fillMaxWidth()) { i ->
            val vista = vistas[i]
            Column(Modifier.fillMaxWidth()) {
                Text(vista.titulo, style = t.etiqueta, color = c.textoSecundario)
                BasicText(
                    enVista(vista.usdMinor, estado.viewCurrency, estado.tasa),
                    style = t.cifraGrande.copy(color = c.texto),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = CIFRA_MINIMA, maxFontSize = CIFRA_MAXIMA),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    equivalencia(vista.usdMinor, estado.viewCurrency, estado.tasa) ?: ESPACIO_FIJO,
                    style = t.cuerpo,
                    color = c.textoSecundario,
                )
            }
        }

        Row(
            Modifier
                .padding(top = e.s)
                .clearAndSetSemantics {
                    contentDescription = "Vista ${pager.currentPage + 1} de ${vistas.size}: ${vistas[pager.currentPage].titulo}"
                },
            horizontalArrangement = Arrangement.spacedBy(e.s),
        ) {
            vistas.indices.forEach { i ->
                val color by animateColorAsState(
                    if (i == pager.currentPage) c.primario else c.borde,
                    Movimiento.transicion(),
                    label = "puntoPatrimonio",
                )
                Spacer(Modifier.size(PUNTO).clip(CircleShape).background(color))
            }
        }

        val tasa = estado.tasa
        if (tasa != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = e.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tasa ${tasa.source.name} del ${tasa.date}: ${tasaTexto(tasa.vesPerUsd)} Bs/$",
                    style = t.etiqueta,
                    color = c.textoSecundario,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tasa.date != estado.hoy) {
                    Spacer(Modifier.width(e.s))
                    Chip("tasa vieja")
                }
            }
        } else {
            Banner(
                "Sin tasa configurada — regístrala en Ajustes → Tasas.",
                TipoBanner.AVISO,
                modifier = Modifier.padding(top = e.s),
            )
        }

        Tarjeta(modifier = Modifier.padding(top = e.m), titulo = "Cuentas") {
            if (p.lines.isEmpty()) {
                Vacio("🏦", "Todavía no hay cuentas activas.")
            } else {
                p.lines.forEach { linea -> FilaCuenta(linea) }
            }
        }
    }
}

/** Lado de los íconos en línea con cifras (DESIGN.md §4: 16 dp). */
private val ICONO_LINEA = 16.dp

/** Alto mínimo de cada fila de cuenta. */
private val ALTO_FILA = 48.dp

@Composable
private fun FilaCuenta(linea: PatrimonioLinea) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    Row(
        Modifier.fillMaxWidth().heightIn(min = ALTO_FILA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (linea.account.isSavings) {
            Icon(
                painterResource(R.drawable.ic_candado),
                contentDescription = "Ahorro",
                tint = c.textoSecundario,
                modifier = Modifier.size(ICONO_LINEA),
            )
            Spacer(Modifier.width(e.xs))
        }
        Text(
            linea.account.name,
            style = t.cuerpo,
            color = c.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(e.s))
        Column(horizontalAlignment = Alignment.End) {
            Monto(linea.balanceMinor, linea.account.currency)
            if (linea.account.currency == Currency.VES) {
                Text(
                    "≈ " + (linea.balanceUsdMinor?.let { formatMoney(it, Currency.USD) } ?: "—"),
                    style = t.etiqueta,
                    color = c.textoSecundario,
                )
            }
        }
    }
}
