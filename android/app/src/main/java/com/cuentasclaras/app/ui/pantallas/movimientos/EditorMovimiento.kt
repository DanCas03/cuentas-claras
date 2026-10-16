package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.comoKind
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.ui.componentes.BotonPeligro
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoFecha
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Editor de un movimiento (`EditorMovimiento` de `MovimientosPage.tsx`). Gasto e ingreso: monto, categoría
 * (del mismo tipo), fecha y nota. Transferencias y ajustes: solo fecha y nota (el monto afecta dos saldos o es un
 * delta). Los botones quedan deshabilitados mientras se guarda (restricción 9). El estado local se reinicia
 * cuando cambia el movimiento editado.
 */
@Composable
fun EditorMovimiento(
    tx: Transaction,
    categorias: List<Category>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (montoTxt: String, categoryId: String?, fecha: String, nota: String) -> Unit,
    onEliminar: () -> Unit,
) {
    val e = CuentasTema.espacios
    val montoEditableTx = tx.type == TxType.EXPENSE || tx.type == TxType.INCOME
    var montoTxt by remember(tx.id) { mutableStateOf(montoEditable(tx.amountMinor)) }
    var categoryId by remember(tx.id) { mutableStateOf(tx.categoryId ?: "") }
    var fecha by remember(tx.id) { mutableStateOf(tx.date) }
    var nota by remember(tx.id) { mutableStateOf(tx.note ?: "") }
    var confirmando by remember(tx.id) { mutableStateOf(false) }

    val delMismoTipo = remember(categorias, tx.type) { categorias.filter { it.kind == tx.type.comoKind() } }
    val opcionesCategoria = remember(delMismoTipo) { listOf("") + delMismoTipo.map { it.id } }

    HojaModal("Editar movimiento", onCerrar = onCerrar) {
        if (montoEditableTx) {
            CampoTexto(montoTxt, { montoTxt = it }, etiqueta = "Monto", teclado = KeyboardType.Decimal)
            Spacer(Modifier.height(e.m))
            Selector(
                etiqueta = "Categoría",
                opciones = opcionesCategoria,
                seleccion = categoryId,
                texto = { id ->
                    val cat = delMismoTipo.firstOrNull { it.id == id }
                    if (cat == null) "Sin categoría" else "${cat.icon} ${cat.name}"
                },
                onSeleccion = { categoryId = it },
            )
            Spacer(Modifier.height(e.m))
        } else {
            Text(
                "En transferencias y ajustes solo se pueden cambiar la fecha y la nota.",
                style = CuentasTema.tipografia.cuerpo,
                color = CuentasTema.colores.textoSecundario,
            )
            Spacer(Modifier.height(e.m))
        }
        CampoFecha("Fecha", fecha, { fecha = it })
        Spacer(Modifier.height(e.m))
        CampoTexto(nota, { nota = it }, etiqueta = "Nota")
        Spacer(Modifier.height(e.l))
        BotonPrincipal(
            "Guardar",
            onClick = { onGuardar(montoTxt, categoryId.ifEmpty { null }, fecha, nota) },
            enabled = !guardando,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(e.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.s)) {
            BotonSecundario("Cancelar", onClick = onCerrar, enabled = !guardando, modifier = Modifier.weight(1f))
            BotonPeligro("Eliminar", onClick = { confirmando = true }, enabled = !guardando, modifier = Modifier.weight(1f))
        }
    }

    if (confirmando) {
        val c = CuentasTema.colores
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("¿Eliminar este movimiento?", style = CuentasTema.tipografia.subtitulo) },
            text = {
                Text(
                    "Esta acción no se puede deshacer.",
                    style = CuentasTema.tipografia.cuerpo,
                    color = c.textoSecundario,
                )
            },
            confirmButton = {
                BotonPeligro(
                    "Sí, eliminar",
                    onClick = {
                        confirmando = false
                        onEliminar()
                    },
                    enabled = !guardando,
                )
            },
            dismissButton = {
                BotonSecundario("Conservar", onClick = { confirmando = false }, enabled = !guardando)
            },
            containerColor = c.superficie,
            titleContentColor = c.texto,
            textContentColor = c.textoSecundario,
            shape = CuentasTema.formas.tarjeta,
        )
    }
}
