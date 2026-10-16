package com.cuentasclaras.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Todo LOCAL por componentes. Nunca Instant/UTC para fechas de calendario (restricción 4).

private val FORMATO_INSTANTE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Fecha local del dispositivo como 'yyyy-mm-dd'. */
fun todayIso(): String = LocalDate.now().toString()

/** Instante como `new Date().toISOString()`: '2026-09-04T14:00:00.000Z'. Solo para createdIso/exportedAt/recibidaIso. */
fun instanteIso(i: Instant): String = FORMATO_INSTANTE.format(i)

fun ahoraIso(): String = instanteIso(Instant.now())

/** Día local (zona del sistema) en que ocurrió un instante UTC ISO como `recibidaIso`: 'yyyy-mm-dd'.
 *  Un aviso de las 23:50 locales cae en su día aunque en UTC ya sea el siguiente. */
fun fechaLocalDeInstante(instanteIso: String): String =
    Instant.parse(instanteIso).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private val DD_MM_YYYY = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")

/** '06/07/2026' → '2026-07-06'. null si no es dd/mm/yyyy plausible. */
fun isoFromDdMmYyyy(s: String): String? {
    val m = DD_MM_YYYY.find(s.trim()) ?: return null
    val (dd, mm, yyyy) = m.destructured
    val dia = dd.toInt()
    val mes = mm.toInt()
    if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null
    return "$yyyy-$mm-$dd"
}

/** '2026-07-06' → '2026-07' */
fun monthOf(dateIso: String): String = dateIso.substring(0, 7)

private val NOMBRES_MES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "octubre", "noviembre", "diciembre",
)
val MES_CORTO = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val DIA_CORTO = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val DIA_LARGO = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Letras de la cabecera del calendario, lunes primero. */
val LETRAS_DIA = listOf("L", "M", "X", "J", "V", "S", "D")

data class Partes(val y: Int, val m: Int, val d: Int)

fun partsOf(iso: String): Partes {
    val (y, m, d) = iso.split("-").map { it.toInt() }
    return Partes(y, m, d)
}

fun isoFromParts(y: Int, m: Int, d: Int): String =
    "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"

fun daysInMonth(y: Int, m: Int): Int = YearMonth.of(y, m).lengthOfMonth()

private val FECHA_ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** 'yyyy-mm-dd' con forma estricta y fecha real (30/02 → false). */
fun esFechaIso(s: String): Boolean {
    if (!FECHA_ISO.matches(s)) return false
    val (y, m, d) = partsOf(s)
    return m in 1..12 && d >= 1 && d <= daysInMonth(y, m)
}

fun addDays(iso: String, n: Int): String = LocalDate.parse(iso).plusDays(n.toLong()).toString()

/** b − a en días enteros (inmune a cambios de hora: LocalDate no tiene zona). */
fun diffDias(a: String, b: String): Int = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)).toInt()

/** 0 = lunes … 6 = domingo. */
fun weekdayLunes0(iso: String): Int = LocalDate.parse(iso).dayOfWeek.value - 1

fun mesAnterior(month: String): String = YearMonth.parse(month).minusMonths(1).toString()

fun mesSiguiente(month: String): String = YearMonth.parse(month).plusMonths(1).toString()

fun primerDiaMes(month: String): String = "$month-01"

fun ultimoDiaMes(month: String): String {
    val ym = YearMonth.parse(month)
    return isoFromParts(ym.year, ym.monthValue, ym.lengthOfMonth())
}

/** 'Septiembre 2026' */
fun nombreMes(month: String): String {
    val ym = YearMonth.parse(month)
    return "${NOMBRES_MES[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}"
}

/** 'viernes' */
fun nombreDia(iso: String): String = DIA_LARGO[weekdayLunes0(iso)]

/** '15 sep' */
fun fechaCorta(iso: String): String {
    val p = partsOf(iso)
    return "${p.d} ${MES_CORTO[p.m - 1]}"
}

/** 'vie 4' */
fun diaCorto(iso: String): String = "${DIA_CORTO[weekdayLunes0(iso)]} ${partsOf(iso).d}"

/** 'Viernes 4 de septiembre' */
fun fechaLarga(iso: String): String {
    val p = partsOf(iso)
    return "${nombreDia(iso).replaceFirstChar { it.uppercase() }} ${p.d} de ${NOMBRES_MES[p.m - 1]}"
}

/** '04/09/2026' */
fun ddmmyyyy(iso: String): String {
    val p = partsOf(iso)
    return "${p.d.toString().padStart(2, '0')}/${p.m.toString().padStart(2, '0')}/${p.y}"
}

data class Celda(val iso: String, val enMes: Boolean)

/** Grid mensual de 6 filas × 7 columnas (42 celdas fijas) desde el lunes ≤ día 1. */
fun celdasMes(month: String): List<Celda> {
    val primero = primerDiaMes(month)
    val inicio = addDays(primero, -weekdayLunes0(primero))
    return List(42) { i ->
        val iso = addDays(inicio, i)
        Celda(iso, iso.startsWith(month))
    }
}
