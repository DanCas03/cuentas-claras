package com.cuentasclaras.app.ui.pantallas.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CtxSugerencia
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxParaSugerir
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.comoKind
import com.cuentasclaras.app.domain.montoEditable
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.componentes.BotonPeligro
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoFecha
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.DialogoConfirmacion
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.SelectEvento
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Editor de un movimiento (`EditorMovimiento` de `MovimientosPage.tsx`). Gasto e ingreso: monto, categoría
 * (del mismo tipo), evento (`SelectEvento`; elegir uno pone la categoría del evento, que sigue editable), fecha y
 * nota. `txsAsignadas` son las transacciones CRUDAS con evento (E18). Transferencias y ajustes: solo fecha y nota
 * (el monto afecta dos saldos o es un delta). Los botones quedan deshabilitados mientras se guarda
 * (restricción 9). El estado local se reinicia cuando cambia el movimiento editado.
 */
@Composable
fun EditorMovimiento(
    tx: Transaction,
    categorias: List<Category>,
    cuentas: List<Account>,
    eventos: List<CalEvent>,
    txsAsignadas: List<Transaction>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (montoTxt: String, categoryId: String?, fecha: String, nota: String, ocurrencia: Ocurrencia?) -> Unit,
    onEliminar: () -> Unit,
) {
    val e = CuentasTema.espacios
    val montoEditableTx = tx.type == TxType.EXPENSE || tx.type == TxType.INCOME
    var montoTxt by remember(tx.id) { mutableStateOf(montoEditable(tx.amountMinor)) }
    var categoryId by remember(tx.id) { mutableStateOf(tx.categoryId ?: "") }
    var fecha by remember(tx.id) { mutableStateOf(tx.date) }
    var nota by remember(tx.id) { mutableStateOf(tx.note ?: "") }
    var ocurrencia by remember(tx.id) {
        val eventId = tx.eventId
        val eventDate = tx.eventDate
        mutableStateOf(if (!eventId.isNullOrEmpty() && eventDate != null) Ocurrencia(eventId, eventDate) else null)
    }
    var confirmando by remember(tx.id) { mutableStateOf(false) }
    val hoy = remember { todayIso() }

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
            val kind = tx.type.comoKind()
            if (kind != null) {
                SelectEvento(
                    tx = TxParaSugerir(kind, fecha, tx.accountId, parseVenezuelanAmount(montoTxt.removeSuffix(",")) ?: 0),
                    actual = ocurrencia,
                    ctx = CtxSugerencia(eventos, txsAsignadas, cuentas, categorias, hoy),
                    etiqueta = "Evento",
                    descripcion = "Evento del movimiento",
                    onCambio = { o, evento ->
                        ocurrencia = o
                        if (o != null && evento != null) categoryId = evento.categoryId
                    },
                    // El hueco va dentro: sin eventos aplicables SelectEvento no pinta nada y no queda hueco doble.
                    modifier = Modifier.padding(bottom = e.m),
                )
            }
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
            onClick = { onGuardar(montoTxt, categoryId.ifEmpty { null }, fecha, nota, ocurrencia) },
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
        DialogoConfirmacion(
            titulo = "¿Eliminar este movimiento?",
            texto = "Esta acción no se puede deshacer.",
            textoConfirmar = "Sí, eliminar",
            habilitado = !guardando,
            onConfirmar = {
                confirmando = false
                onEliminar()
            },
            onCancelar = { confirmando = false },
        )
    }
}
