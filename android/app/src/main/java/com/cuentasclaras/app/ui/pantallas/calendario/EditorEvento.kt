package com.cuentasclaras.app.ui.pantallas.calendario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.componentes.BotonPeligro
import com.cuentasclaras.app.ui.componentes.BotonPrincipal
import com.cuentasclaras.app.ui.componentes.BotonSecundario
import com.cuentasclaras.app.ui.componentes.CampoFecha
import com.cuentasclaras.app.ui.componentes.CampoTexto
import com.cuentasclaras.app.ui.componentes.DialogoConfirmacion
import com.cuentasclaras.app.ui.componentes.Chip
import com.cuentasclaras.app.ui.componentes.HojaModal
import com.cuentasclaras.app.ui.componentes.Segmentado
import com.cuentasclaras.app.ui.componentes.Selector
import com.cuentasclaras.app.ui.theme.CuentasTema

private val TEXTO_REPETIR = mapOf(
    Repetir.ONCE to "Una vez",
    Repetir.WEEKLY to "Cada semana",
    Repetir.MONTHLY to "Cada mes",
    Repetir.QUINCENAL to "Quincenal (15 y último)",
    Repetir.YEARLY to "Cada año",
)

/**
 * Editor de un evento del calendario (`EditorEvento.tsx`), en el orden de la web. El borrador vive en el ViewModel;
 * la hoja solo se cierra desde `alGuardar`/`alEliminar`, tras escribir con éxito (si falla, queda abierta con lo
 * escrito). Todos los botones quedan deshabilitados mientras se guarda (restricción 9).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorEvento(
    borrador: BorradorEvento,
    cuentas: List<Account>,
    categorias: List<com.cuentasclaras.app.domain.Category>,
    movimientosAsignados: Int,
    guardando: Boolean,
    onCambio: ((BorradorEvento) -> BorradorEvento) -> Unit,
    onCerrar: () -> Unit,
    onGuardar: () -> Unit,
    onEliminar: () -> Unit,
) {
    val e = CuentasTema.espacios
    val b = borrador
    val esNuevo = b.original == null
    var confirmando by remember(b.id) { mutableStateOf(false) }

    val activas = remember(cuentas) { cuentas.filter { !it.archived } }
    val moneda = activas.firstOrNull { it.id == b.cuentaId }?.currency ?: Currency.USD
    val simbolo = if (moneda == Currency.VES) "Bs" else "$"
    val delKind = remember(categorias, b.kind) { categorias.filter { it.kind == b.kind } }
    val opcionesCategoria = remember(delKind) { listOf("") + delKind.map { it.id } }
    val opcionesCuenta = remember(activas) { listOf("") + activas.map { it.id } }

    fun cambiarKind(nuevo: CategoryKind) = onCambio { x ->
        val categoria = categorias.firstOrNull { it.id == x.categoriaId }
        x.copy(kind = nuevo, categoriaId = if (categoria?.kind != nuevo) "" else x.categoriaId)
    }

    // Sin cuenta el monto queda deshabilitado: si el texto siguiera escrito no se podría borrar y Guardar
    // fallaría señalando un campo intocable. Y al cambiar de moneda el monto escrito ya no vale.
    fun cambiarCuenta(nuevaId: String) {
        if (nuevaId.isEmpty()) {
            if (b.montoTxt.isNotEmpty()) Avisos.avisar("Sin cuenta no hay monto esperado: se borró el monto escrito")
            onCambio { it.copy(cuentaId = "", montoTxt = "") }
            return
        }
        val monedaNueva = activas.firstOrNull { it.id == nuevaId }?.currency ?: Currency.USD
        if (monedaNueva != moneda && b.montoTxt.isNotEmpty()) {
            onCambio { it.copy(cuentaId = nuevaId, montoTxt = "") }
            Avisos.avisar("La cuenta elegida usa otra moneda: vuelve a escribir el monto")
        } else {
            onCambio { it.copy(cuentaId = nuevaId) }
        }
    }

    HojaModal(if (esNuevo) "Nuevo evento" else "Editar evento", onCerrar = onCerrar) {
        Column(verticalArrangement = Arrangement.spacedBy(e.m)) {
            CampoTexto(b.titulo, { v -> onCambio { it.copy(titulo = v) } }, etiqueta = "Título", placeholder = "Quincena")

            Segmentado(
                opciones = listOf("Pago", "Cobro"),
                seleccion = if (b.kind == CategoryKind.EXPENSE) 0 else 1,
                onSeleccion = { cambiarKind(if (it == 0) CategoryKind.EXPENSE else CategoryKind.INCOME) },
            )

            Selector(
                etiqueta = "Categoría",
                opciones = opcionesCategoria,
                seleccion = b.categoriaId,
                texto = { id ->
                    val c = delKind.firstOrNull { it.id == id }
                    if (c == null) "Elige una categoría" else "${c.icon} ${c.name}"
                },
                onSeleccion = { id -> onCambio { it.copy(categoriaId = id) } },
            )

            Selector(
                etiqueta = "Cuenta esperada (define la moneda)",
                opciones = opcionesCuenta,
                seleccion = b.cuentaId,
                texto = { id ->
                    val c = activas.firstOrNull { it.id == id }
                    if (c == null) "— sin cuenta —" else "${c.name} (${if (c.currency == Currency.VES) "Bs" else "$"})"
                },
                onSeleccion = ::cambiarCuenta,
            )

            val hayCuenta = b.cuentaId.isNotEmpty()
            Column {
                // V18: sin cuenta el campo está deshabilitado de verdad (`enabled = false`: atenuado, sin foco ni
                // texto, y TalkBack lo anuncia así); el rótulo no promete una moneda y la ayuda sigue al estado.
                CampoTexto(
                    valor = b.montoTxt,
                    onCambio = { v -> if (hayCuenta) onCambio { it.copy(montoTxt = v) } },
                    etiqueta = if (hayCuenta) "Monto esperado en $simbolo (opcional)" else "Monto esperado (opcional)",
                    placeholder = "0,00",
                    teclado = KeyboardType.Decimal,
                    enabled = hayCuenta,
                )
                Spacer(Modifier.height(e.xs))
                Text(
                    if (hayCuenta) "Déjalo vacío si el monto varía." else "Sin cuenta no se puede fijar un monto.",
                    style = CuentasTema.tipografia.etiqueta,
                    color = CuentasTema.colores.textoSecundario,
                )
            }

            CampoFecha(if (b.repetir == Repetir.ONCE) "Fecha" else "Primera vez", b.fecha, { v -> onCambio { it.copy(fecha = v) } })

            Selector(
                etiqueta = "Repetir",
                opciones = Repetir.entries.toList(),
                seleccion = b.repetir,
                texto = { TEXTO_REPETIR.getValue(it) },
                onSeleccion = { r -> onCambio { it.copy(repetir = r, fin = if (r == Repetir.ONCE) "" else it.fin) } },
            )

            if (b.repetir == Repetir.MONTHLY) {
                Column {
                    CampoTexto(b.diasTxt, { v -> onCambio { it.copy(diasTxt = v) } }, etiqueta = "Días del mes", placeholder = "15, 31")
                    Spacer(Modifier.height(e.xs))
                    Text(
                        "31 = último día del mes",
                        style = CuentasTema.tipografia.etiqueta,
                        color = CuentasTema.colores.textoSecundario,
                    )
                }
            }

            if (b.repetir != Repetir.ONCE) {
                Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                    CampoFecha("Fin (opcional)", b.fin, { v -> onCambio { it.copy(fin = v) } })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(e.s)) {
                        Chip("Terminar hoy", onClick = { onCambio { it.copy(fin = todayIso()) } }, enabled = !guardando)
                        if (b.fin.isNotEmpty()) {
                            Chip("Quitar fin", onClick = { onCambio { it.copy(fin = "") } }, enabled = !guardando)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                BotonPrincipal("Guardar", onClick = onGuardar, enabled = !guardando, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(e.s)) {
                    BotonSecundario("Cancelar", onClick = onCerrar, enabled = !guardando, modifier = Modifier.weight(1f))
                    if (!esNuevo) {
                        BotonPeligro("Eliminar", onClick = { confirmando = true }, enabled = !guardando, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (confirmando && b.original != null) {
        DialogoConfirmacion(
            titulo = "¿Eliminar «${b.original.titulo}»?",
            // N6: concordancia en singular («1 movimiento quedará…»).
            texto = if (movimientosAsignados == 1) {
                "1 movimiento quedará sin evento (conserva su categoría)."
            } else {
                "$movimientosAsignados movimientos quedarán sin evento (conservan su categoría)."
            },
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
