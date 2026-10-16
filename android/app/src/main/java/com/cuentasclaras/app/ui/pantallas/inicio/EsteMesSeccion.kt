package com.cuentasclaras.app.ui.pantallas.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.EstadoPresupuesto
import com.cuentasclaras.app.domain.ResumenMes
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.domain.mesAnterior
import com.cuentasclaras.app.domain.mesSiguiente
import com.cuentasclaras.app.domain.nombreMes
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.Barras
import com.cuentasclaras.app.ui.componentes.BotonChevron
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.CuadroCategoria
import com.cuentasclaras.app.ui.componentes.CurvaAcumulada
import com.cuentasclaras.app.ui.componentes.Dona
import com.cuentasclaras.app.ui.componentes.FilaConFinal
import com.cuentasclaras.app.ui.componentes.GrupoBarras
import com.cuentasclaras.app.ui.componentes.LineaProgreso
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.SegmentoDona
import com.cuentasclaras.app.ui.componentes.Signo
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.componentes.colorDeCategoria
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Ícono en línea con una cifra (DESIGN.md §4: 16 dp). */
private val ICONO_LINEA = 16.dp

/** Muestra de color de la leyenda de la comparativa. */
private val MUESTRA_LEYENDA = 10.dp

/** Umbral de aviso de un presupuesto (DESIGN.md §7: pasa del 80 %). */
private const val UMBRAL_AVISO = 0.8

/**
 * «Este mes», «Comparativa mensual» y «Presupuestos (USD)» (contrato de `GraficasMes.tsx`). El navegador de mes
 * gobierna las TRES tarjetas; el ViewModel ya acota `mes` a [primerMes, mesActual] y agrega con `txsEfectivas`.
 */
@Composable
fun EsteMesSeccion(
    estado: InicioViewModel.Estado,
    onMes: (String) -> Unit,
    onCategoria: (categoryId: String, mes: String) -> Unit,
) {
    val resumen = estado.resumen ?: return
    val dinero: (Long) -> String = { formatMoney(it, estado.viewCurrency) }
    Column(verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.l)) {
        TarjetaEsteMes(estado, resumen, dinero, onMes, onCategoria)
        TarjetaComparativa(estado.comparativa, dinero)
        if (estado.presupuestos.isNotEmpty()) TarjetaPresupuestos(estado.presupuestos, estado.mes)
    }
}

@Composable
private fun TarjetaEsteMes(
    estado: InicioViewModel.Estado,
    resumen: ResumenMes,
    dinero: (Long) -> String,
    onMes: (String) -> Unit,
    onCategoria: (categoryId: String, mes: String) -> Unit,
) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    var expandido by rememberSaveable { mutableStateOf(false) }
    val categoria = { id: String? -> estado.categorias.firstOrNull { it.id == id } }
    val balance = resumen.incomeMinor - resumen.expenseMinor

    Tarjeta {
        NavegadorMes(estado.mes, estado.mesActual, estado.primerMes, onMes)
        Column(Modifier.padding(top = e.s), verticalArrangement = Arrangement.spacedBy(e.m)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.m)) {
                Flujo("Ingresos", R.drawable.ic_entra, c.ingreso, resumen.incomeMinor, estado.viewCurrency, Signo.POSITIVO, Modifier.weight(1f))
                Flujo("Gastos", R.drawable.ic_sale, c.gasto, resumen.expenseMinor, estado.viewCurrency, Signo.NEGATIVO, Modifier.weight(1f))
            }
            if (resumen.incomeMinor > 0L) {
                val pct = Math.round(resumen.expenseMinor * 100.0 / resumen.incomeMinor)
                Column(verticalArrangement = Arrangement.spacedBy(e.xs)) {
                    LineaProgreso((resumen.expenseMinor.toDouble() / resumen.incomeMinor).toFloat(), c.gasto)
                    Text(
                        "Gastaste el $pct % de lo que entró",
                        style = t.etiqueta.copy(fontFeatureSettings = "tnum"),
                        color = c.textoSecundario,
                    )
                }
            }
            Row(
                Modifier.semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Balance: ", style = t.cuerpo, color = c.texto)
                Text(
                    formatMoney(balance, estado.viewCurrency),
                    style = t.cifra,
                    // Cantidad de estado con formatMoney: en `texto` (DESIGN.md §4).
                    color = c.texto,
                    maxLines = 1,
                )
            }
            if (resumen.omitidasSinTasa > 0) {
                Banner("${resumen.omitidasSinTasa} movimiento(s) sin tasa quedaron fuera del resumen.", TipoBanner.AVISO)
            }
            if (resumen.expenseMinor <= 0L) {
                Vacio("🧾", if (estado.mes == estado.mesActual) "Sin gastos este mes" else "Sin gastos en ${nombreMes(estado.mes)}")
            } else {
                Rotulo("Gasto por categoría")
                Dona(
                    resumen.byCategory.map { b ->
                        val cat = categoria(b.categoryId)
                        SegmentoDona(
                            etiqueta = cat?.name ?: "Sin categoría",
                            valor = b.expenseMinor,
                            color = cat?.let { colorDeCategoria(it.color, c.textoSecundario) } ?: c.textoSecundario,
                        )
                    },
                    formato = dinero,
                )
                Rotulo("Gasto acumulado")
                CurvaAcumulada(resumen.dailyCumulative, formato = dinero)
            }
            Chip(
                if (expandido) "Ocultar" else "Ver todas las categorías",
                onClick = { expandido = !expandido },
            )
            if (expandido) {
                Column(verticalArrangement = Arrangement.spacedBy(e.xs)) {
                    if (resumen.byCategory.isNotEmpty()) Subtitulo("Gastos")
                    resumen.byCategory.forEach { b ->
                        FilaCategoria(
                            categoria(b.categoryId), b.expenseMinor, resumen.expenseMinor, estado.viewCurrency, Signo.NEGATIVO,
                            // Solo enlaza si la categoría existe (GraficasMes.tsx:58-60).
                            onClick = categoria(b.categoryId)?.let { cat -> { onCategoria(cat.id, estado.mes) } },
                        )
                    }
                    if (resumen.incomeByCategory.isNotEmpty()) Subtitulo("Ingresos")
                    resumen.incomeByCategory.forEach { b ->
                        FilaCategoria(
                            categoria(b.categoryId), b.incomeMinor, resumen.incomeMinor, estado.viewCurrency, Signo.POSITIVO,
                            onClick = categoria(b.categoryId)?.let { cat -> { onCategoria(cat.id, estado.mes) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * «‹ Este mes ›» con los mismos chevrons vectoriales que Calendario (`BotonChevron`) y el chip «hoy» fuera del mes
 * actual. Los extremos quedan deshabilitados en el rango.
 */
@Composable
private fun NavegadorMes(mes: String, mesActual: String, primerMes: String, onMes: (String) -> Unit) {
    val c = CuentasTema.colores
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BotonChevron("Mes anterior", haciaLaIzquierda = true, onClick = { onMes(mesAnterior(mes)) }, habilitado = mes > primerMes)
        Text(
            if (mes == mesActual) "Este mes" else nombreMes(mes),
            style = CuentasTema.tipografia.subtitulo,
            color = c.texto,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        BotonChevron("Mes siguiente", haciaLaIzquierda = false, onClick = { onMes(mesSiguiente(mes)) }, habilitado = mes < mesActual)
        if (mes != mesActual) {
            Chip("hoy", onClick = { onMes(mesActual) }, modifier = Modifier.padding(start = CuentasTema.espacios.xs))
        }
    }
}

/** Una columna de «Este mes»: flecha + rótulo y el monto con signo (el color nunca es la única señal). */
@Composable
private fun Flujo(
    rotulo: String,
    icono: Int,
    color: Color,
    minor: Long,
    moneda: Currency,
    signo: Signo,
    modifier: Modifier,
) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(ICONO_LINEA))
            Text(
                rotulo,
                style = CuentasTema.tipografia.etiqueta,
                color = CuentasTema.colores.textoSecundario,
                modifier = Modifier.padding(start = CuentasTema.espacios.xs),
            )
        }
        Monto(minor, moneda, signo)
    }
}

/** Rótulo de bloque en mayúsculas (DESIGN.md §3). */
@Composable
private fun Rotulo(texto: String) {
    Text(
        texto.uppercase(),
        style = CuentasTema.tipografia.etiqueta.copy(letterSpacing = 0.12.em),
        color = CuentasTema.colores.textoSecundario,
    )
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        texto,
        style = CuentasTema.tipografia.subtitulo,
        color = CuentasTema.colores.texto,
        modifier = Modifier.padding(top = CuentasTema.espacios.s).semantics { heading() },
    )
}

/**
 * Fila de la lista de categorías: emoji (o `ic_sin_categoria`), nombre, barra proporcional, monto con signo y porcentaje del total.
 * Con `onClick` (categoría existente) abre Movimientos filtrado; «Sin categoría» no es tocable y va en gris.
 */
@Composable
private fun FilaCategoria(
    cat: Category?,
    minor: Long,
    total: Long,
    moneda: Currency,
    signo: Signo,
    onClick: (() -> Unit)?,
) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    val color = cat?.let { colorDeCategoria(it.color, c.textoSecundario) } ?: c.textoSecundario
    val pct = if (total > 0L) Math.round(minor * 100.0 / total) else 0L
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = CuentasTema.medidas.tactilMinimo)
        .clip(CuentasTema.formas.control)
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base.semantics(mergeDescendants = true) { })
            .padding(vertical = e.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El mismo cuadro que las demás listas (V17). «Sin categoría» (📦 en la web) es `ic_sin_categoria`.
        CuadroCategoria(
            cat?.icon,
            R.drawable.ic_sin_categoria,
            fondo = color.copy(alpha = CuentasTema.medidas.alfaTinteCategoria),
            tintaVector = c.textoSecundario,
        )
        // Nombre y barra a la izquierda; el monto y el % a la derecha, o debajo si no caben (FilaConFinal).
        FilaConFinal(
            texto = {
                Column(verticalArrangement = Arrangement.spacedBy(e.xs)) {
                    Text(
                        cat?.name ?: "Sin categoría",
                        style = t.cuerpo,
                        color = if (cat != null) c.texto else c.textoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LineaProgreso((if (total > 0L) minor.toDouble() / total else 0.0).toFloat(), color)
                }
            },
            final = {
                Column(horizontalAlignment = Alignment.End) {
                    Monto(minor, moneda, signo)
                    Text("$pct %", style = t.etiqueta.copy(fontFeatureSettings = "tnum"), color = c.textoSecundario, maxLines = 1, softWrap = false)
                }
            },
            separacion = e.s,
            modifier = Modifier.weight(1f).padding(start = e.s),
        )
    }
}

@Composable
private fun TarjetaComparativa(comparativa: List<ResumenMes>, dinero: (Long) -> String) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    Tarjeta(titulo = "Comparativa mensual") {
        if (comparativa.all { it.incomeMinor == 0L && it.expenseMinor == 0L }) {
            Vacio("📊", "Sin movimientos en estos meses")
        } else {
            Barras(
                grupos = comparativa.map { GrupoBarras(it.month.substring(5), listOf(it.incomeMinor, it.expenseMinor)) },
                colores = listOf(c.ingreso, c.gasto),
                formato = dinero,
                series = listOf("Ingresos", "Gastos"),
            )
            Row(
                Modifier
                    .padding(top = e.s)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(e.m),
            ) {
                ItemLeyenda("+ Ingresos", c.ingreso)
                ItemLeyenda("− Gastos", c.gasto)
            }
        }
    }
}

@Composable
private fun ItemLeyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(MUESTRA_LEYENDA)
                .clip(CuentasTema.formas.control)
                .background(color),
        )
        Text(
            texto,
            style = CuentasTema.tipografia.etiqueta,
            color = CuentasTema.colores.texto,
            modifier = Modifier.padding(start = CuentasTema.espacios.xs),
        )
    }
}

@Composable
private fun TarjetaPresupuestos(presupuestos: List<EstadoPresupuesto>, mes: String) {
    val c = CuentasTema.colores
    val t = CuentasTema.tipografia
    val e = CuentasTema.espacios
    Tarjeta(titulo = "Presupuestos (USD) · ${nombreMes(mes)}") {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            presupuestos.forEach { p ->
                val excedido = p.ratio > 1
                Column(
                    Modifier.semantics(mergeDescendants = true) { },
                    verticalArrangement = Arrangement.spacedBy(e.xs),
                ) {
                    // «gastado / tope» nunca se corta: si no cabe junto al nombre, baja a su propia línea.
                    FilaConFinal(
                        texto = {
                            Text(
                                "${p.category.icon} ${p.category.name}",
                                style = t.cuerpo,
                                color = c.texto,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        final = {
                            Text(
                                "${formatMoney(p.gastoUsdMinor, Currency.USD)} / ${formatMoney(p.presupuestoMinor, Currency.USD)}",
                                // Cifra de dinero: `cifra` (DESIGN.md §3), el dato principal de la fila.
                                style = t.cifra,
                                color = if (excedido) c.gasto else c.texto,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        separacion = e.s,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LineaProgreso(
                        (minOf(100.0, p.ratio * 100) / 100).toFloat(),
                        relleno = when {
                            excedido -> c.error
                            p.ratio > UMBRAL_AVISO -> c.aviso
                            else -> c.gasto
                        },
                    )
                }
            }
        }
    }
}
