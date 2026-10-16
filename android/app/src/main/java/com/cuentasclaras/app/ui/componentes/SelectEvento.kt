package com.cuentasclaras.app.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.CtxSugerencia
import com.cuentasclaras.app.domain.EstadoOcurrencia
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.TxParaSugerir
import com.cuentasclaras.app.domain.candidatasParaTx
import com.cuentasclaras.app.domain.ddmmyyyy

/** Una opción del selector; `o == null` es «Sin evento». */
private data class OpcionEvento(val valor: String, val etiqueta: String, val evento: CalEvent?, val o: Ocurrencia?)

private const val SIN_EVENTO = "Sin evento"

/** «15/09» a partir de «2026-09-15». */
private fun diaMes(iso: String): String = ddmmyyyy(iso).take(5)

/** Literal del estado, el mismo que muestra la web (`c.ocurrencia.estado`). */
private fun textoEstado(estado: EstadoOcurrencia): String = when (estado) {
    EstadoOcurrencia.PENDIENTE -> "pendiente"
    EstadoOcurrencia.HECHA -> "hecha"
    EstadoOcurrencia.VENCIDA -> "vencida"
    EstadoOcurrencia.OMITIDA -> "omitida"
}

/**
 * Selector de evento compartido por el editor de movimientos y la vista previa del import (`SelectEvento.tsx`).
 *
 * Opciones: «Sin evento» y las candidatas de `candidatasParaTx(tx, ctx, incluirHechas = true)` con la etiqueta
 * «{título} · {dd/mm} · {estado}». La asignación vigente SIEMPRE se lista: si `actual` no está entre las candidatas
 * (quedó fuera de la ventana) y su evento existe, se antepone «{título} · {dd/mm} · (asignado)». Sin candidatas ni
 * asignación listable no pinta nada (no ocupa espacio).
 *
 * La web antepone «📅» a cada opción; aquí no hay emojis de interfaz (DESIGN.md §8) y la etiqueta visible del campo
 * («Evento») ya dice qué se elige. `ctx.txs` son las transacciones CRUDAS asignadas (E18).
 *
 * `onCambio(null, null)` = «Sin evento».
 */
@Composable
fun SelectEvento(
    tx: TxParaSugerir,
    actual: Ocurrencia?,
    ctx: CtxSugerencia,
    etiqueta: String,
    descripcion: String,
    onCambio: (Ocurrencia?, CalEvent?) -> Unit,
) {
    val opciones = remember(tx, actual, ctx) {
        val candidatas = candidatasParaTx(tx, ctx, incluirHechas = true).map { c ->
            val oc = c.ocurrencia
            OpcionEvento(
                valor = "${oc.eventId}|${oc.date}",
                etiqueta = "${oc.evento.titulo} · ${diaMes(oc.date)} · ${textoEstado(oc.estado)}",
                evento = oc.evento,
                o = Ocurrencia(oc.eventId, oc.date),
            )
        }
        val lista = candidatas.toMutableList()
        if (actual != null && candidatas.none { it.valor == "${actual.eventId}|${actual.date}" }) {
            val evento = ctx.eventos.firstOrNull { it.id == actual.eventId }
            if (evento != null) {
                lista.add(
                    0,
                    OpcionEvento(
                        valor = "${actual.eventId}|${actual.date}",
                        etiqueta = "${evento.titulo} · ${diaMes(actual.date)} · (asignado)",
                        evento = evento,
                        o = actual,
                    ),
                )
            }
        }
        lista.toList()
    }
    if (opciones.isEmpty()) return

    val sinEvento = OpcionEvento(valor = "", etiqueta = SIN_EVENTO, evento = null, o = null)
    val valorActual = actual?.let { "${it.eventId}|${it.date}" } ?: ""
    Selector(
        etiqueta = etiqueta,
        opciones = listOf(sinEvento) + opciones,
        seleccion = opciones.firstOrNull { it.valor == valorActual } ?: sinEvento,
        texto = { it.etiqueta },
        onSeleccion = { op -> onCambio(op.o, op.evento) },
        contentDescription = descripcion,
    )
}
