package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.Signo
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Lado de los íconos en línea con cifras (DESIGN.md §4: 16 dp). */
private val ICONO_LINEA = 16.dp

/** Alto mínimo de cada fila de deuda: deja un objetivo legible aunque no sea tocable. */
private val ALTO_FILA = 40.dp

/** Deudas pendientes: totales (debes / te deben en la moneda de la vista) y las 3 más recientes. */
@Composable
fun DeudasTarjeta(estado: InicioViewModel.Estado, nav: NavHostController, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    val debes = enVista(estado.totalesDeudas.debesUsdMinor, estado.viewCurrency, estado.tasa)
    val teDeben = enVista(estado.totalesDeudas.teDebenUsdMinor, estado.viewCurrency, estado.tasa)
    val monedaDe = estado.cuentas.associate { it.id to it.currency }

    Tarjeta(
        modifier = modifier,
        titulo = "Deudas",
        accion = { Chip("ver todas", onClick = { nav.navigate(Rutas.DEUDAS) }) },
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.textoSecundario)) { append("Debes ") }
                withStyle(SpanStyle(color = c.texto, fontWeight = FontWeight.W600)) { append(debes) }
                withStyle(SpanStyle(color = c.textoSecundario)) { append(" · Te deben ") }
                withStyle(SpanStyle(color = c.texto, fontWeight = FontWeight.W600)) { append(teDeben) }
            },
            style = t.cuerpo,
        )
        if (estado.deudasRecientes.isEmpty()) {
            Text(
                "Sin deudas pendientes.",
                style = t.etiqueta,
                color = c.textoSecundario,
                modifier = Modifier.padding(top = e.s),
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(top = e.s)) {
                estado.deudasRecientes.forEach { deuda ->
                    val porPagar = deuda.direction == DebtDirection.POR_PAGAR
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = ALTO_FILA),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(
                            painterResource(if (porPagar) R.drawable.ic_sale else R.drawable.ic_entra),
                            contentDescription = if (porPagar) "Por pagar" else "Por cobrar",
                            tint = if (porPagar) c.gasto else c.ingreso,
                            modifier = Modifier.size(ICONO_LINEA),
                        )
                        Spacer(Modifier.width(e.s))
                        Text(
                            deuda.concepto,
                            style = t.cuerpo,
                            color = c.texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(e.s))
                        Monto(
                            deuda.amountMinor,
                            monedaDe[deuda.accountId] ?: Currency.USD,
                            if (porPagar) Signo.NEGATIVO else Signo.POSITIVO,
                        )
                    }
                }
            }
        }
    }
}
