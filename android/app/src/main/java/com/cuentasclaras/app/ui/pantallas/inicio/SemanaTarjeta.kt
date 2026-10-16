package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.EstadoOcurrencia
import com.cuentasclaras.app.domain.LETRAS_DIA
import com.cuentasclaras.app.domain.MontoMoneda
import com.cuentasclaras.app.domain.OcurrenciaVista
import com.cuentasclaras.app.domain.addDays
import com.cuentasclaras.app.domain.diaCorto
import com.cuentasclaras.app.domain.fechaLarga
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.kindDeEvento
import com.cuentasclaras.app.domain.partsOf
import com.cuentasclaras.app.domain.weekdayLunes0
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.FilaConFinal
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.componentes.colorDeCategoria
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.pantallas.calendario.EtiquetaEstado
import com.cuentasclaras.app.ui.pantallas.calendario.Punto
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Medidas iguales a las del Calendario (T29): cuadro del emoji de la fila, emoji y anillo de «hoy». */
private val CUADRO_ICONO = 44.dp
private val EMOJI_FILA = 22.sp
private val ANILLO_HOY = 2.dp
private const val MAX_PUNTOS = 3
private const val VISIBLES = 3

/**
 * «Esta semana» (`SemanaCard.tsx`): tira de 7 días desde hoy con hasta 3 puntos por día, totales por pagar y por
 * cobrar, y la lista de vencidas + ocurrencias de [hoy, hoy + 6] (3 visibles y «ver n más»). Tocar una pendiente o
 * vencida abre Nuevo con el evento activo; cualquier otra, el Calendario en ese día.
 */
@Composable
fun SemanaTarjeta(estado: InicioViewModel.Estado, nav: NavHostController, modifier: Modifier = Modifier) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val hoy = estado.hoy
    val ocurrencias = estado.ocurrenciasSemana
    val categorias = estado.categorias.associateBy { it.id }
    val cuentas = estado.cuentas.associateBy { it.id }
    val finSemana = addDays(hoy, 6)
    val lista = ocurrencias.filter { it.estado == EstadoOcurrencia.VENCIDA } +
        ocurrencias.filter { it.date >= hoy && it.date <= finSemana }
    var todo by rememberSaveable { mutableStateOf(false) }

    Tarjeta(
        modifier = modifier,
        titulo = "Esta semana",
        accion = { Chip("calendario", onClick = { nav.navigate(Rutas.calendario()) }) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            TiraSemana(hoy, ocurrencias, categorias, onDia = { nav.navigate(Rutas.calendario(dia = it)) })

            val (pagar, cobrar) = estado.totalesSemana
            if (pagar.isNotEmpty() || cobrar.isNotEmpty()) {
                Text(
                    lineaTotales(pagar, cobrar),
                    style = CuentasTema.tipografia.cuerpo.copy(fontFeatureSettings = "tnum"),
                    color = c.textoSecundario,
                )
            }

            if (lista.isEmpty()) {
                Vacio("🗓️", "Sin pagos ni cobros esta semana")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Chip("crear evento", onClick = { nav.navigate(Rutas.calendario(nuevo = true)) })
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(e.xs)) {
                    (if (todo) lista else lista.take(VISIBLES)).forEach { o ->
                        key(o.eventId, o.date) {
                            FilaSemana(
                                o = o,
                                categoria = categorias[o.evento.categoryId],
                                cuentas = cuentas,
                                onClick = {
                                    if (o.estado == EstadoOcurrencia.PENDIENTE || o.estado == EstadoOcurrencia.VENCIDA) {
                                        val ingreso = kindDeEvento(o.evento, estado.categorias) == CategoryKind.INCOME
                                        nav.navigate(
                                            Rutas.nuevo(
                                                tipo = if (ingreso) "ingreso" else "gasto",
                                                eventId = o.eventId,
                                                eventDate = o.date,
                                            ),
                                        )
                                    } else {
                                        nav.navigate(Rutas.calendario(dia = o.date))
                                    }
                                },
                            )
                        }
                    }
                }
                if (lista.size > VISIBLES) {
                    Chip(if (todo) "ver menos" else "ver ${lista.size - VISIBLES} más", onClick = { todo = !todo })
                }
            }
        }
    }
}

/** «Por pagar Bs 1.000,00 · $15.99 — Por cobrar Bs 80.000,00» (cada moneda por separado, sin conversión). */
private fun lineaTotales(pagar: List<MontoMoneda>, cobrar: List<MontoMoneda>): String {
    fun lista(m: List<MontoMoneda>) = m.joinToString(" · ") { formatMoney(it.totalMinor, it.currency) }
    return listOfNotNull(
        pagar.takeIf { it.isNotEmpty() }?.let { "Por pagar ${lista(it)}" },
        cobrar.takeIf { it.isNotEmpty() }?.let { "Por cobrar ${lista(it)}" },
    ).joinToString(" — ")
}

/** 7 días desde hoy: letra, número y hasta 3 puntos (sin las omitidas). Hoy con anillo `primario`. */
@Composable
private fun TiraSemana(
    hoy: String,
    ocurrencias: List<OcurrenciaVista>,
    categorias: Map<String, Category>,
    onDia: (String) -> Unit,
) {
    val porFecha = ocurrencias.filter { it.estado != EstadoOcurrencia.OMITIDA }.groupBy { it.date }
    Row(Modifier.fillMaxWidth()) {
        for (i in 0..6) {
            val d = addDays(hoy, i)
            DiaSemana(
                iso = d,
                esHoy = d == hoy,
                ocurrencias = porFecha[d] ?: emptyList(),
                categorias = categorias,
                onClick = { onDia(d) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DiaSemana(
    iso: String,
    esHoy: Boolean,
    ocurrencias: List<OcurrenciaVista>,
    categorias: Map<String, Category>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val forma = CuentasTema.formas.control
    val estados = buildList {
        if (esHoy) add("hoy")
        if (ocurrencias.isNotEmpty()) add(if (ocurrencias.size == 1) "1 evento" else "${ocurrencias.size} eventos")
    }.joinToString(", ")
    Column(
        modifier
            .padding(horizontal = e.xs / 2)
            .heightIn(min = CuentasTema.medidas.tactilMinimo)
            .clip(forma)
            .then(if (esHoy) Modifier.border(ANILLO_HOY, c.primario, forma) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = fechaLarga(iso)
                if (estados.isNotEmpty()) stateDescription = estados
            }
            .padding(vertical = e.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(e.xs),
    ) {
        Text(
            LETRAS_DIA[weekdayLunes0(iso)],
            style = CuentasTema.tipografia.etiqueta,
            color = c.textoSecundario,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            partsOf(iso).d.toString(),
            style = CuentasTema.tipografia.cuerpo.copy(fontFeatureSettings = "tnum"),
            color = c.texto,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
        // Alto reservado aunque el día no tenga puntos: la tira no salta.
        Row(
            Modifier.heightIn(min = e.xs + e.xs / 2).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(e.xs / 2),
        ) {
            ocurrencias.take(MAX_PUNTOS).forEach { o ->
                Punto(o, categorias[o.evento.categoryId]?.color, sobreSeleccion = false)
            }
        }
    }
}

/** Ícono, título, «vie 16 · Cuenta», cifra (hecha: lo asignado; si no, el monto esperado) y estado. */
@Composable
private fun FilaSemana(
    o: OcurrenciaVista,
    categoria: Category?,
    cuentas: Map<String, Account>,
    onClick: () -> Unit,
) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val cifra = if (o.estado == EstadoOcurrencia.HECHA) {
        o.asignadoPorMoneda.joinToString(" · ") { formatMoney(it.totalMinor, it.currency) }
    } else {
        o.evento.amountMinor?.let { formatMoney(it, cuentas[o.evento.accountId]?.currency ?: Currency.USD) } ?: ""
    }
    val cuenta = o.evento.accountId?.let { cuentas[it]?.name }
    val tono = categoria?.let { colorDeCategoria(it.color, c.borde) } ?: c.borde
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CuentasTema.medidas.tactilMinimo)
            .clip(CuentasTema.formas.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = e.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(e.m),
    ) {
        Box(
            Modifier
                .size(CUADRO_ICONO)
                .clip(CuentasTema.formas.control)
                .background(tono.copy(alpha = CuentasTema.medidas.alfaTinteCategoria)),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoria?.icon ?: "📅", fontSize = EMOJI_FILA, modifier = Modifier.clearAndSetSemantics { })
        }
        // El monto y el estado bajan juntos bajo el título cuando no caben a su lado (FilaConFinal).
        FilaConFinal(
            texto = {
                Column {
                    Text(
                        o.evento.titulo,
                        style = CuentasTema.tipografia.cuerpo,
                        color = if (o.estado == EstadoOcurrencia.HECHA) c.textoSecundario else c.texto,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        diaCorto(o.date) + (cuenta?.let { " · $it" } ?: ""),
                        style = CuentasTema.tipografia.etiqueta,
                        color = c.textoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            final = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(e.xs)) {
                    if (cifra.isNotEmpty()) Text(cifra, style = CuentasTema.tipografia.cifra, color = c.texto, maxLines = 1, softWrap = false)
                    EtiquetaEstado(o.estado)
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
