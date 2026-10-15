package com.cuentasclaras.app.domain

import kotlin.math.abs

private val MONTO_VE = Regex("""^([+-]?)(\d+)(?:,(\d+))?$""")

/** "13517,75" | "-3500" | "87,6999999999998" → céntimos enteros. null si no es un monto BNC válido. */
fun parseVenezuelanAmount(raw: String): Long? {
    val m = MONTO_VE.find(raw.trim()) ?: return null
    val (signo, entero, fraccion) = m.destructured
    val sign = if (signo == "-") -1L else 1L
    val intMinor = (entero.toLongOrNull() ?: return null) * 100
    // La parte decimal puede traer artefactos float del export ("6999999999998"):
    // se interpreta como fracción y se redondea a céntimos (Math.round = redondeo de JS).
    val fracMinor = if (fraccion.isEmpty()) 0L else Math.round("0.$fraccion".toDouble() * 100)
    return sign * (intMinor + fracMinor)
}

private fun conMiles(unidades: Long, separador: Char): String =
    unidades.toString().reversed().chunked(3).joinToString(separador.toString()).reversed()

/** Formato visual venezolano: `Bs 13.517,75` · `$84.20` · negativos: `Bs -3.500,00` / `-$5.00`. */
fun formatMoney(minor: Long, currency: Currency): String {
    val signo = if (minor < 0) "-" else ""
    val absoluto = abs(minor)
    val unidades = absoluto / 100
    val centimos = (absoluto % 100).toString().padStart(2, '0')
    return if (currency == Currency.USD) {
        "$signo\$${conMiles(unidades, ',')}.$centimos"
    } else {
        "Bs $signo${conMiles(unidades, '.')},$centimos"
    }
}

/** Céntimos positivos → texto para el teclado/campo de monto: 125000 → "1250,00".
 *  Equivale a `(minor / 100).toFixed(2).replace('.', ',')` de la web. */
fun montoEditable(minor: Long): String =
    "${minor / 100},${(minor % 100).toString().padStart(2, '0')}"
