package com.cuentasclaras.app.domain

import kotlin.math.ceil
import kotlin.math.min

enum class EstadoOcurrencia { PENDIENTE, HECHA, VENCIDA, OMITIDA }

/** Clave de una ocurrencia: el evento + su fecha NOMINAL (no la del movimiento). */
data class Ocurrencia(val eventId: String, val date: String)

data class MontoMoneda(val currency: Currency, val totalMinor: Long)

data class OcurrenciaVista(
    val eventId: String,
    val date: String,
    val evento: CalEvent,
    val estado: EstadoOcurrencia,
    /** Movimientos asignados a esta ocurrencia (pueden ser varios). */
    val txIds: List<String>,
    /** Σ amountMinor de las txs asignadas, por moneda de la cuenta de cada tx. Sin conversión. */
    val asignadoPorMoneda: List<MontoMoneda>,
    /** true si la regla actual del evento NO genera esa fecha. */
    val fueraDeRegla: Boolean,
) {
    val ocurrencia: Ocurrencia get() = Ocurrencia(eventId, date)
}

/** Preset «Quincenal (15 y último)»: 31 se recorta al último día de cada mes. */
val RECURRENCIA_QUINCENAL: Recurrence = Recurrence.Monthly(listOf(15, 31))

fun esQuincenal(r: Recurrence): Boolean = r is Recurrence.Monthly && r.days == listOf(15, 31)

/**
 * Separadores de días: el `\s` de JS incluye los espacios Unicode (NBSP, U+202F, U+FEFF...) y el `\s` de Java/ICU
 * solo los ASCII. Se listan explícitamente con `\uXXXX`, que entienden por igual el regex de Java (tests en JVM) y
 * el ICU de Android; `(?U)` NO sirve porque ICU rechaza esa bandera en línea.
 */
private val SEPARADOR_DIAS = Regex("""[\s   -     　﻿,;]+""")
private val DIA_TEXTO = Regex("""^\d{1,2}$""")

/** '15, 31' → [15, 31]. Ordena y deduplica. Lanza si está vacío o hay algo fuera de 1..31. */
fun parseDias(texto: String): List<Int> {
    val dias = sortedSetOf<Int>()
    for (p in texto.split(SEPARADOR_DIAS).filter { it.isNotEmpty() }) {
        require(DIA_TEXTO.matches(p)) { "Días del mes inválidos" }
        val n = p.toInt()
        require(n in 1..31) { "Días del mes inválidos" }
        dias.add(n)
    }
    require(dias.isNotEmpty()) { "Días del mes inválidos" }
    return dias.toList()
}

/** Lanza IllegalArgumentException en español con el primer problema encontrado. */
fun validarEvento(e: CalEvent) {
    require(e.titulo.isNotBlank()) { "Ponle nombre al evento" }
    require(e.categoryId.isNotEmpty()) { "Elige una categoría" }
    if (e.amountMinor != null) {
        require(!e.accountId.isNullOrEmpty()) { "El monto esperado necesita una cuenta" }
        require(e.amountMinor > 0) { "Monto inválido" }
    }
    require(esFechaIso(e.startDate)) { "Fecha inválida" }
    if (e.endDate != null) {
        require(esFechaIso(e.endDate)) { "Fecha de fin inválida" }
        require(e.endDate >= e.startDate) { "El fin no puede ser antes del inicio" }
    }
    val r = e.recurrence
    if (r is Recurrence.Monthly) {
        val d = r.days
        val ok = d.isNotEmpty() && d.all { it in 1..31 } && d.zipWithNext().all { (a, b) -> b > a }
        require(ok) { "Días del mes inválidos" }
    }
    for (o in e.omitidas ?: emptyList()) require(esFechaIso(o)) { "Fecha omitida inválida" }
}

/** Construye un CalEvent válido. Nunca guarda cadenas vacías ni listas vacías (quedan null). */
fun buildEvent(
    titulo: String,
    categoryId: String,
    accountId: String? = null,
    amountMinor: Long? = null,
    startDate: String,
    endDate: String? = null,
    recurrence: Recurrence,
    omitidas: List<String>? = null,
    nowIso: String,
): CalEvent {
    val e = CalEvent(
        id = nuevoId(),
        titulo = titulo.trim(),
        categoryId = categoryId,
        accountId = accountId?.takeIf { it.isNotEmpty() },
        amountMinor = amountMinor,
        startDate = startDate,
        endDate = endDate?.takeIf { it.isNotEmpty() },
        recurrence = recurrence,
        omitidas = omitidas?.takeIf { it.isNotEmpty() }?.toList(),
        createdIso = nowIso,
    )
    validarEvento(e)
    return e
}

/** Fechas NOMINALES del evento dentro de [desde, hasta] (inclusive), ascendentes y sin repetidos.
 *  NUNCA expande sin `hasta`. */
fun fechasDeEvento(e: CalEvent, desde: String, hasta: String): List<String> {
    val r = e.recurrence
    if (r is Recurrence.Once) {
        return if (desde <= e.startDate && e.startDate <= hasta) listOf(e.startDate) else emptyList()
    }
    val desdeEf = if (desde > e.startDate) desde else e.startDate
    val hastaEf = if (e.endDate != null && e.endDate < hasta) e.endDate else hasta
    if (desdeEf > hastaEf) return emptyList()

    return when (r) {
        Recurrence.Weekly -> {
            val salto = ceil(diffDias(e.startDate, desdeEf) / 7.0).toInt()
            val out = mutableListOf<String>()
            var f = addDays(e.startDate, salto * 7)
            while (f <= hastaEf) {
                out.add(f)
                f = addDays(f, 7)
            }
            out
        }
        Recurrence.Yearly -> {
            val (_, m, d) = partsOf(e.startDate)
            val out = sortedSetOf<String>()
            for (y in partsOf(desdeEf).y..partsOf(hastaEf).y) {
                val f = isoFromParts(y, m, min(d, daysInMonth(y, m)))
                if (f >= desdeEf && f <= hastaEf) out.add(f)
            }
            out.toList()
        }
        is Recurrence.Monthly -> {
            val out = sortedSetOf<String>()
            var mes = monthOf(desdeEf)
            while (mes <= monthOf(hastaEf)) {
                val (y, m) = mes.split("-").map { it.toInt() }
                for (dia in r.days) {
                    val f = isoFromParts(y, m, min(dia, daysInMonth(y, m)))
                    if (f >= desdeEf && f <= hastaEf) out.add(f)
                }
                mes = mesSiguiente(mes)
            }
            out.toList()
        }
        Recurrence.Once -> emptyList() // ya tratado arriba
    }
}

/** El kind de la categoría del evento define pago (EXPENSE) o cobro (INCOME). null si no existe. */
fun kindDeEvento(e: CalEvent, categories: List<Category>): CategoryKind? =
    categories.firstOrNull { it.id == e.categoryId }?.kind

/** Precedencia: hecha > omitida > vencida > pendiente. «Hoy» cuenta como pendiente. */
fun estadoDe(date: String, omitida: Boolean, txIds: List<String>, hoy: String): EstadoOcurrencia = when {
    txIds.isNotEmpty() -> EstadoOcurrencia.HECHA
    omitida -> EstadoOcurrencia.OMITIDA
    date < hoy -> EstadoOcurrencia.VENCIDA
    else -> EstadoOcurrencia.PENDIENTE
}

/** UNIÓN de las fechas generadas por cada evento y de las asignaciones existentes en el rango.
 *  Las ocurrencias NO se persisten: esta función las deriva cada vez. */
fun ocurrenciasEnRango(
    eventos: List<CalEvent>,
    txs: List<Transaction>,
    accounts: List<Account>,
    desde: String,
    hasta: String,
    hoy: String,
): List<OcurrenciaVista> {
    val eventoDe = eventos.associateBy { it.id }
    val monedaDe = accounts.associate { it.id to it.currency }
    val generadas = HashSet<String>()
    val pares = LinkedHashMap<String, Pair<CalEvent, String>>()

    for (e in eventos) {
        for (f in fechasDeEvento(e, desde, hasta)) {
            val k = "${e.id}|$f"
            generadas.add(k)
            pares[k] = e to f
        }
    }

    val txsPorPar = HashMap<String, MutableList<Transaction>>()
    for (t in txs) {
        val eventId = t.eventId ?: continue
        val eventDate = t.eventDate ?: continue
        if (eventDate < desde || eventDate > hasta) continue
        val evento = eventoDe[eventId] ?: continue // asignación huérfana: se ignora
        val k = "$eventId|$eventDate"
        if (k !in pares) pares[k] = evento to eventDate
        txsPorPar.getOrPut(k) { mutableListOf() }.add(t)
    }

    val vistas = pares.map { (k, par) ->
        val (evento, date) = par
        val asignadas = txsPorPar[k] ?: emptyList()
        val porMoneda = LinkedHashMap<Currency, Long>()
        for (t in asignadas) {
            val moneda = monedaDe[t.accountId] ?: Currency.USD
            porMoneda[moneda] = (porMoneda[moneda] ?: 0L) + t.amountMinor
        }
        val txIds = asignadas.map { it.id }
        OcurrenciaVista(
            eventId = evento.id,
            date = date,
            evento = evento,
            estado = estadoDe(date, (evento.omitidas ?: emptyList()).contains(date), txIds, hoy),
            txIds = txIds,
            asignadoPorMoneda = porMoneda.map { MontoMoneda(it.key, it.value) },
            fueraDeRegla = k !in generadas,
        )
    }
    return vistas.sortedWith { a, b ->
        val porFecha = a.date.compareTo(b.date)
        if (porFecha != 0) porFecha else compararTexto(a.evento.titulo, b.evento.titulo)
    }
}

data class TotalesEsperados(val pagar: List<MontoMoneda>, val cobrar: List<MontoMoneda>)

/** Lo que falta pagar/cobrar: solo pendientes y vencidas CON monto esperado, por moneda, sin conversión. */
fun totalesEsperados(
    ocurrencias: List<OcurrenciaVista>,
    accounts: List<Account>,
    categories: List<Category>,
): TotalesEsperados {
    val monedaDe = accounts.associate { it.id to it.currency }
    val pagar = LinkedHashMap<Currency, Long>()
    val cobrar = LinkedHashMap<Currency, Long>()
    for (o in ocurrencias) {
        if (o.estado != EstadoOcurrencia.PENDIENTE && o.estado != EstadoOcurrencia.VENCIDA) continue
        val monto = o.evento.amountMinor ?: continue
        val cuenta = o.evento.accountId?.takeIf { it.isNotEmpty() } ?: continue
        val kind = kindDeEvento(o.evento, categories) ?: continue
        val moneda = monedaDe[cuenta] ?: Currency.USD
        val destino = if (kind == CategoryKind.EXPENSE) pagar else cobrar
        destino[moneda] = (destino[moneda] ?: 0L) + monto
    }
    return TotalesEsperados(
        pagar = pagar.map { MontoMoneda(it.key, it.value) },
        cobrar = cobrar.map { MontoMoneda(it.key, it.value) },
    )
}

/** Texto humano de la regla, en español. */
fun descripcionRecurrencia(e: CalEvent): String {
    val fin = e.endDate?.let { " · hasta ${ddmmyyyy(it)}" } ?: ""
    return when (val r = e.recurrence) {
        Recurrence.Once -> "una vez: ${ddmmyyyy(e.startDate)}"
        Recurrence.Weekly -> "semanal: ${nombreDia(e.startDate)}$fin"
        Recurrence.Yearly -> {
            val p = partsOf(e.startDate)
            "anual: ${p.d} ${MES_CORTO[p.m - 1]}$fin"
        }
        is Recurrence.Monthly -> {
            if (esQuincenal(r)) {
                "quincenal (15 y último)$fin"
            } else {
                val dias = r.days.map { if (it == 31) "último" else it.toString() }
                val texto = if (dias.size == 1) dias[0] else "${dias.dropLast(1).joinToString(", ")} y ${dias.last()}"
                "mensual: $texto$fin"
            }
        }
    }
}
