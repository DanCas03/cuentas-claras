package com.cuentasclaras.app.importar.bnc

import com.cuentasclaras.app.domain.isoFromDdMmYyyy
import com.cuentasclaras.app.domain.parseVenezuelanAmount

data class BncMeta(val accountNumber: String, val holder: String, val title: String)

data class BncRow(
    val date: String,
    val time: String,
    val referencia: String,
    val codTransaccion: String,
    val tipoTransaccion: String,
    val tipOperacion: String,
    val descripcion: String,
    /** Debe + Haber con signo, en céntimos de Bs. */
    val amountMinor: Long,
    val saldoMinor: Long,
    val referencia2: String,
)

data class BncUnparsed(val lineNumber: Int, val raw: String, val reason: String)

data class BncParseResult(val meta: BncMeta?, val rows: List<BncRow>, val unparsed: List<BncUnparsed>)

private val HORA_RE = Regex("""^\d{2}:\d{2}:\d{2}(\.\d+)?$""")
private val SALTO = Regex("\r?\n")

fun parseBncTxt(text: String): BncParseResult {
    var meta: BncMeta? = null
    val rows = mutableListOf<BncRow>()
    val unparsed = mutableListOf<BncUnparsed>()

    text.removePrefix("﻿").split(SALTO).forEachIndexed { idx, linea ->
        val lineNumber = idx + 1
        val raw = linea.trimEnd()
        if (raw.isBlank()) return@forEachIndexed

        // Metadatos: única línea sin tabs con separador '@'
        if (!raw.contains('\t') && raw.contains('@')) {
            val partes = raw.split('@')
            if (partes.size >= 3 && meta == null) {
                meta = BncMeta(partes[0], partes[1], partes.drop(2).joinToString("@"))
            } else {
                unparsed.add(BncUnparsed(lineNumber, raw, "metadatos irreconocibles"))
            }
            return@forEachIndexed
        }

        if (raw.startsWith("Fecha\t")) return@forEachIndexed // cabecera

        val f = raw.split('\t')
        if (f.size < 10) {
            unparsed.add(BncUnparsed(lineNumber, raw, "columnas insuficientes (${f.size})"))
            return@forEachIndexed
        }
        val fecha = f[0]
        val hora = f[1]
        val referencia = f[2]
        val tipOperacion = f[5]
        val debe = f[7]
        val haber = f[8]
        val saldo = f[9]

        // Pseudo-fila "Saldo Inicial": se reconoce y se descarta (semántica no confiable)
        if (tipOperacion.trim() == "Saldo Inicial" && referencia.trim().isEmpty()) return@forEachIndexed

        val date = isoFromDdMmYyyy(fecha)
        if (date == null) {
            unparsed.add(BncUnparsed(lineNumber, raw, "fecha inválida: \"$fecha\""))
            return@forEachIndexed
        }
        if (!HORA_RE.matches(hora.trim())) {
            unparsed.add(BncUnparsed(lineNumber, raw, "hora inválida: \"$hora\""))
            return@forEachIndexed
        }
        val debeMinor = parseVenezuelanAmount(if (debe.isBlank()) "0" else debe)
        val haberMinor = parseVenezuelanAmount(if (haber.isBlank()) "0" else haber)
        val saldoMinor = parseVenezuelanAmount(saldo)
        if (debeMinor == null || haberMinor == null || saldoMinor == null) {
            unparsed.add(BncUnparsed(lineNumber, raw, "monto ilegible"))
            return@forEachIndexed
        }

        rows.add(
            BncRow(
                date = date,
                time = hora.trim(),
                referencia = referencia.trim(),
                codTransaccion = f[3].trim(),
                tipoTransaccion = f[4].trim(),
                tipOperacion = tipOperacion.trim(),
                descripcion = f[6].trim(),
                amountMinor = debeMinor + haberMinor,
                saldoMinor = saldoMinor,
                referencia2 = f.getOrElse(10) { "" }.trim(),
            ),
        )
    }
    return BncParseResult(meta, rows, unparsed)
}

/** Con filas en orden de archivo (descendente), verifica saldo[i] − monto[i] == saldo[i+1].
 *  Devuelve los índices i donde NO cuadra. */
fun validateChain(rows: List<BncRow>): List<Int> =
    (0 until rows.size - 1).filter { i -> rows[i].saldoMinor - rows[i].amountMinor != rows[i + 1].saldoMinor }
