@file:OptIn(ExperimentalLayoutApi::class)

package com.cuentasclaras.app.ui.pantallas.importar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.cuentasclaras.app.R
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.formatMoney
import com.cuentasclaras.app.importar.bnc.GapStatus
import com.cuentasclaras.app.ui.componentes.Banner
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.Monto
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.componentes.Signo
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.TipoBanner
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.vmCon
import kotlin.math.abs

private val ALTO_TACTIL = 48.dp
private val ICONO_FLECHA = 16.dp
private const val ALFA_DUPLICADA = 0.45f

/** Importar: TXT del BNC (archivo por SAF o texto pegado), vista previa editable y confirmación. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ImportarPantalla(nav: NavHostController) {
    val vm = vmCon { c, _ -> ImportarViewModel(c.repo) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val e = CuentasTema.espacios
    val u = estado.ui
    val resolver = LocalContext.current.contentResolver
    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.cargarArchivo(resolver, uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = e.m, vertical = e.m),
        verticalArrangement = Arrangement.spacedBy(e.m),
    ) {
        item(key = "titulo") {
            Text(
                "Importar",
                style = CuentasTema.tipografia.titulo,
                color = CuentasTema.colores.texto,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (u.fase) {
            FaseImport.ENTRADA -> item(key = "entrada") {
                Entrada(
                    texto = u.texto,
                    sinResultados = u.sinResultados,
                    ocupado = u.guardando,
                    onTexto = vm::cambiarTexto,
                    onElegir = { elegirArchivo.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                    onAnalizar = vm::analizar,
                )
            }
            FaseImport.PREVIA -> {
                item(key = "resumen") { ResumenYAvisos(estado) }
                itemsIndexed(u.drafts, key = { i, d -> "$i:${d.tx.importKey ?: d.tx.id}" }) { i, d ->
                    FilaDraft(
                        indice = i,
                        d = d,
                        categorias = estado.categorias,
                        onCambio = { cambio -> vm.actualizar(i, cambio) },
                    )
                }
                item(key = "pie") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.s)) {
                        BotonSecundario("Cancelar", onClick = vm::reiniciar, enabled = !u.guardando, modifier = Modifier.weight(1f))
                        BotonPrincipal(
                            "Confirmar (${estado.nuevas.size})",
                            onClick = vm::confirmar,
                            enabled = estado.nuevas.isNotEmpty() && !u.guardando,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            FaseImport.HECHO -> item(key = "hecho") {
                Hecho(estado, onCrearAjuste = vm::crearAjuste, onOtro = vm::reiniciar)
            }
        }
    }
}

@Composable
private fun Entrada(
    texto: String,
    sinResultados: Boolean,
    ocupado: Boolean,
    onTexto: (String) -> Unit,
    onElegir: () -> Unit,
    onAnalizar: () -> Unit,
) {
    val e = CuentasTema.espacios
    Tarjeta(titulo = "TXT del BNC") {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            Text(
                "Exporta desde BNC los \"25 últimos movimientos\" en TXT y súbelo o pega su contenido.",
                style = CuentasTema.tipografia.cuerpo,
                color = CuentasTema.colores.textoSecundario,
            )
            BotonSecundario("Elegir archivo TXT", onClick = onElegir, modifier = Modifier.fillMaxWidth())
            CampoTexto(
                valor = texto,
                onCambio = onTexto,
                etiqueta = "O pega el contenido",
                singleLine = false,
                minLines = 6,
            )
            BotonPrincipal(
                "Analizar",
                onClick = onAnalizar,
                enabled = texto.isNotBlank() && !ocupado,
                modifier = Modifier.fillMaxWidth(),
            )
            if (sinResultados) {
                Vacio("🔍", "No se reconoció ningún movimiento en el texto. Revisa que sea el export completo de BNC.")
            }
        }
    }
}

@Composable
private fun ResumenYAvisos(estado: ImportarViewModel.Estado) {
    val e = CuentasTema.espacios
    val u = estado.ui
    val resultado = u.resultado
    val cuenta = resultado?.meta?.let { " de la cuenta ${it.accountNumber.takeLast(4).padStart(8, '•')}" } ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
        Text(
            "${u.drafts.size} movimientos en el archivo$cuenta.",
            style = CuentasTema.tipografia.subtitulo,
            color = CuentasTema.colores.texto,
        )
        when (u.gap) {
            GapStatus.PRIMERA_IMPORTACION -> Unit
            GapStatus.SOLAPADO -> Banner("El archivo solapa con lo ya importado: los repetidos se omiten solos.", TipoBanner.OK)
            GapStatus.CONTINUO -> Banner("El archivo conecta exactamente con el último import. Sin huecos.", TipoBanner.OK)
            GapStatus.HUECO -> Banner(
                "⚠ Hay movimientos NO exportados entre tu último import y este archivo (el saldo no encadena). " +
                    "Exporta desde BNC un rango que cubra el hueco e importa de nuevo.",
                TipoBanner.AVISO,
            )
        }
        if (u.rotos.isNotEmpty()) {
            Banner(
                "El encadenado de saldos no cuadra dentro del archivo (filas ${u.rotos.joinToString(", ") { (it + 1).toString() }}). " +
                    "Revisa que el TXT esté completo.",
                TipoBanner.ERROR,
            )
        }
        val sinLeer = resultado?.unparsed.orEmpty()
        if (sinLeer.isNotEmpty()) {
            val detalle = sinLeer.joinToString("\n") { "línea ${it.lineNumber} (${it.reason}): «${it.raw.take(60)}…»" }
            Banner(
                "${sinLeer.size} línea(s) irreconocibles — se importa el resto y estas quedan fuera " +
                    "(añádelas a mano si hacían falta):\n$detalle",
                TipoBanner.ERROR,
            )
        }
    }
}

@Composable
private fun FilaDraft(
    indice: Int,
    d: DraftUI,
    categorias: List<Category>,
    onCambio: ((DraftUI) -> DraftUI) -> Unit,
) {
    val e = CuentasTema.espacios
    val c = CuentasTema.colores
    val n = indice + 1
    val duplicada = d.draft.duplicate
    val ingreso = d.tx.type == TxType.INCOME
    Tarjeta(modifier = Modifier.graphicsLayer { alpha = if (duplicada) ALFA_DUPLICADA else 1f }) {
        Row(horizontalArrangement = Arrangement.spacedBy(e.s), verticalAlignment = Alignment.Top) {
            Casilla(
                texto = null,
                marcado = d.incluir,
                habilitada = !duplicada,
                descripcion = "Incluir fila $n",
                onCambio = { v -> onCambio { it.copy(incluir = v) } },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(e.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(e.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.tx.note ?: "",
                        style = CuentasTema.tipografia.cuerpo,
                        color = c.texto,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(if (ingreso) R.drawable.ic_entra else R.drawable.ic_sale),
                        contentDescription = null,
                        tint = if (ingreso) c.ingreso else c.gasto,
                        modifier = Modifier.size(ICONO_FLECHA),
                    )
                    Monto(abs(d.tx.amountMinor), Currency.VES, if (ingreso) Signo.POSITIVO else Signo.NEGATIVO)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(e.s), verticalArrangement = Arrangement.spacedBy(e.xs)) {
                    Text(
                        "${d.tx.date} ${d.tx.time?.take(8) ?: ""}".trim(),
                        style = CuentasTema.tipografia.etiqueta,
                        color = c.textoSecundario,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    if (d.draft.pairRef != null) Chip("cargo+comisión")
                    if (duplicada) Chip(d.draft.motivoDuplicado ?: "ya importado")
                    if (d.repetida) Chip("Repetida en este texto")
                }
                if (!duplicada) {
                    val opciones = listOf("") + categorias.filter { (it.kind == CategoryKind.INCOME) == ingreso }.map { it.id }
                    Selector(
                        etiqueta = "Categoría",
                        opciones = opciones,
                        seleccion = d.tx.categoryId ?: "",
                        texto = { id -> categorias.firstOrNull { it.id == id }?.let { "${it.icon} ${it.name}" } ?: "Sin categoría" },
                        onSeleccion = { id -> onCambio { it.copy(tx = it.tx.copy(categoryId = id.ifEmpty { null })) } },
                        contentDescription = "Categoría fila $n",
                    )
                    Casilla(
                        texto = "regla",
                        marcado = d.crearRegla,
                        habilitada = true,
                        descripcion = "Crear regla fila $n",
                        onCambio = { v -> onCambio { it.copy(crearRegla = v) } },
                    )
                    if (d.crearRegla) {
                        CampoTexto(
                            valor = d.patron,
                            onCambio = { v -> onCambio { it.copy(patron = v) } },
                            etiqueta = "Patrón",
                            placeholder = "texto que identifica este comercio",
                        )
                    }
                }
            }
        }
    }
}

/** Casilla con etiqueta opcional; toda la fila es el objetivo táctil (≥ 48 dp). */
@Composable
private fun Casilla(
    texto: String?,
    marcado: Boolean,
    habilitada: Boolean,
    descripcion: String,
    onCambio: (Boolean) -> Unit,
) {
    val c = CuentasTema.colores
    Row(
        Modifier
            .heightIn(min = ALTO_TACTIL)
            .toggleable(value = marcado, enabled = habilitada, role = Role.Checkbox, onValueChange = onCambio)
            .semantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs),
    ) {
        Checkbox(
            checked = marcado,
            onCheckedChange = null,
            enabled = habilitada,
            colors = CheckboxDefaults.colors(
                checkedColor = c.primario,
                checkmarkColor = c.sobrePrimario,
                uncheckedColor = c.textoSecundario,
                disabledCheckedColor = c.textoSecundario,
                disabledUncheckedColor = c.textoSecundario,
            ),
        )
        if (texto != null) Text(texto, style = CuentasTema.tipografia.cuerpo, color = c.texto)
    }
}

@Composable
private fun Hecho(estado: ImportarViewModel.Estado, onCrearAjuste: () -> Unit, onOtro: () -> Unit) {
    val e = CuentasTema.espacios
    val u = estado.ui
    val saldoTxt = u.saldoTxt?.let { formatMoney(it, Currency.VES) } ?: ""
    val saldoApp = formatMoney(estado.saldoApp, Currency.VES)
    Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
        Banner("Import terminado. Saldo según la app: $saldoApp.", TipoBanner.OK)
        if (estado.rawMismatch && estado.hayTxsPosteriores) {
            Banner(
                "El TXT termina en $saldoTxt pero la app calcula $saldoApp — hay movimientos más recientes en esta " +
                    "cuenta, así que este descuadre no es fiable para crear un ajuste. Revísalo a mano si hace falta.",
                TipoBanner.AVISO,
            )
        }
        if (estado.descuadre != null && !u.ajusteHecho) {
            Banner(
                "El TXT termina en $saldoTxt pero la app calcula $saldoApp (movimientos previos al primer export).",
                TipoBanner.AVISO,
                accion = { BotonSecundario("Crear ajuste", onClick = onCrearAjuste, enabled = !u.guardando) },
            )
        }
        BotonPrincipal("Importar otro archivo", onClick = onOtro, enabled = !u.guardando, modifier = Modifier.fillMaxWidth())
    }
}
