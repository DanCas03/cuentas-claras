package com.cuentasclaras.app.domain

import kotlin.math.abs

data class TxParaSugerir(val type: CategoryKind, val date: String, val accountId: String, val amountMinor: Long)

data class CtxSugerencia(
    val eventos: List<CalEvent>,
    val txs: List<Transaction>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val hoy: String,
)

/** Ventana asimétrica: la ocurrencia puede estar hasta 31 días ANTES del movimiento y hasta 7 DESPUÉS. */
const val VENTANA_ATRAS_DIAS = 31
const val VENTANA_ADELANTE_DIAS = 7
const val TOLERANCIA_MONTO = 0.10

data class Candidata(
    val ocurrencia: OcurrenciaVista,
    /** |diffDias(ocurrencia.date, tx.date)| */
    val distanciaDias: Int,
    /** null = el evento no tiene monto esperado, o la tx aún no tiene monto (amountMinor ≤ 0). */
    val montoCoincide: Boolean?,
    val sugerible: Boolean,
)

fun candidatasParaTx(tx: TxParaSugerir, ctx: CtxSugerencia, incluirHechas: Boolean = false): List<Candidata> {
    val desde = addDays(tx.date, -VENTANA_ATRAS_DIAS)
    val hasta = addDays(tx.date, VENTANA_ADELANTE_DIAS)
    val elegibles = ctx.eventos.filter { e ->
        kindDeEvento(e, ctx.categories) == tx.type && (e.accountId == null || e.accountId == tx.accountId)
    }
    val ocurrencias = ocurrenciasEnRango(elegibles, ctx.txs, ctx.accounts, desde, hasta, ctx.hoy)

    val out = mutableListOf<Candidata>()
    for (o in ocurrencias) {
        if (o.estado == EstadoOcurrencia.OMITIDA) continue
        if (o.estado == EstadoOcurrencia.HECHA && !incluirHechas) continue
        val esperado = o.evento.amountMinor
        val montoCoincide =
            if (esperado == null || tx.amountMinor <= 0) null
            else abs(tx.amountMinor - esperado) <= TOLERANCIA_MONTO * esperado
        out.add(
            Candidata(
                ocurrencia = o,
                distanciaDias = abs(diffDias(o.date, tx.date)),
                montoCoincide = montoCoincide,
                sugerible = (o.estado == EstadoOcurrencia.PENDIENTE || o.estado == EstadoOcurrencia.VENCIDA) &&
                    montoCoincide != false,
            ),
        )
    }
    return out.sortedWith(
        compareByDescending<Candidata> { it.sugerible }
            .thenBy { it.distanciaDias }
            .thenByDescending { it.montoCoincide == true }
            .thenComparator { a, b -> compararTexto(a.ocurrencia.evento.titulo, b.ocurrencia.evento.titulo) },
    )
}

/** Primera candidata sugerible, o null. Es la que Nuevo marca como «sugerida». */
fun sugerirOcurrencia(tx: TxParaSugerir, ctx: CtxSugerencia): OcurrenciaVista? {
    val primera = candidatasParaTx(tx, ctx).firstOrNull() ?: return null
    return if (primera.sugerible) primera.ocurrencia else null
}

/** Emparejamiento greedy GLOBAL para la vista previa de los imports: cada ocurrencia recibe a lo
 *  sumo una tx y cada tx a lo sumo una ocurrencia. Determinista. Clave del mapa: tx.id. */
fun sugerirParaLote(txs: List<Transaction>, ctx: CtxSugerencia): Map<String, Ocurrencia> {
    data class Par(
        val txId: String, val eventId: String, val date: String, val titulo: String,
        val distanciaDias: Int, val deltaMonto: Long, val indice: Int,
    )
    val pares = mutableListOf<Par>()
    txs.forEachIndexed { indice, t ->
        val kind = t.type.comoKind() ?: return@forEachIndexed
        for (c in candidatasParaTx(TxParaSugerir(kind, t.date, t.accountId, t.amountMinor), ctx)) {
            if (!c.sugerible) continue
            val esperado = c.ocurrencia.evento.amountMinor
            pares.add(
                Par(
                    txId = t.id, eventId = c.ocurrencia.eventId, date = c.ocurrencia.date,
                    titulo = c.ocurrencia.evento.titulo, distanciaDias = c.distanciaDias,
                    deltaMonto = if (esperado == null) Long.MAX_VALUE else abs(t.amountMinor - esperado),
                    indice = indice,
                ),
            )
        }
    }
    val ordenados = pares.sortedWith(
        compareBy<Par> { it.distanciaDias }
            .thenBy { it.deltaMonto }
            .thenComparator { a, b -> compararTexto(a.titulo, b.titulo) }
            .thenBy { it.indice },
    )
    val asignadas = LinkedHashMap<String, Ocurrencia>()
    val ocupadas = HashSet<String>()
    for (p in ordenados) {
        val clave = "${p.eventId}|${p.date}"
        if (p.txId in asignadas || clave in ocupadas) continue
        asignadas[p.txId] = Ocurrencia(p.eventId, p.date)
        ocupadas.add(clave)
    }
    return asignadas
}

/** Devuelve una COPIA. Asignar copia la categoría del evento; desasignar la conserva.
 *  Jamás toca amountMinor, date, accountId ni type. */
fun aplicarAsignacion(tx: Transaction, o: Ocurrencia?, evento: CalEvent?): Transaction =
    if (o == null || evento == null) tx.copy(eventId = null, eventDate = null)
    else tx.copy(eventId = o.eventId, eventDate = o.date, categoryId = evento.categoryId)

/** Lanza IllegalArgumentException en español si el movimiento y el evento van en direcciones contrarias. */
fun validarAsignacion(txType: TxType, evento: CalEvent, categories: List<Category>) {
    val kind = kindDeEvento(evento, categories) ?: throw IllegalArgumentException("La categoría del evento no existe")
    if (txType == TxType.EXPENSE && kind == CategoryKind.INCOME) {
        throw IllegalArgumentException("Este movimiento es un gasto y el evento es un cobro")
    }
    if (txType == TxType.INCOME && kind == CategoryKind.EXPENSE) {
        throw IllegalArgumentException("Este movimiento es un ingreso y el evento es un pago")
    }
}
