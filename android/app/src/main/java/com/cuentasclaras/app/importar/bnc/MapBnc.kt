package com.cuentasclaras.app.importar.bnc

import com.cuentasclaras.app.data.db.ReferenciaCapturada
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxSource
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.matchCategory
import com.cuentasclaras.app.domain.nuevoId
import kotlin.math.abs

/** Incluye la fecha: comisiones recurrentes idénticas de distinto mes no colisionan. */
fun bncImportKey(row: BncRow): String = "bnc:${row.date}:${row.referencia}:${row.time}:${row.amountMinor}"

const val MOTIVO_YA_IMPORTADO = "ya importado"
const val MOTIVO_POR_CAPTURA = "ya registrada por captura"

/** "0100000007" y "100000007" son la misma referencia; "0" o vacía no identifican nada. */
fun normalizarReferencia(ref: String): String = ref.trim().trimStart('0')

/**
 * Mínimo de dígitos (ya sin ceros a la izquierda) que debe tener una referencia de captura más corta que la del TXT
 * para casar por sufijo: la Ref de la notificación podría llegar truncada, pero con menos dígitos un final común
 * sería casualidad y marcaría como duplicada una fila ajena.
 */
const val MIN_DIGITOS_SUFIJO_REFERENCIA = 6

/**
 * true si la referencia de una captura corresponde a la de una fila del TXT: iguales normalizadas, o la de la
 * captura es más corta, tiene al menos [MIN_DIGITOS_SUFIJO_REFERENCIA] dígitos y la del TXT termina en ella.
 */
fun refsCasan(refCaptura: String, refTxt: String): Boolean {
    val captura = normalizarReferencia(refCaptura)
    val txt = normalizarReferencia(refTxt)
    if (captura.isEmpty() || txt.isEmpty()) return false
    if (captura == txt) return true
    return captura.length < txt.length && captura.length >= MIN_DIGITOS_SUFIJO_REFERENCIA && txt.endsWith(captura)
}

/**
 * Referencia normalizada de un movimiento importado del TXT, sacada de su `importKey` (`bncImportKey`:
 * `bnc:fecha:ref:hora:monto`); null si la clave no tiene esa forma. La fecha (`yyyy-mm-dd`) y el monto no llevan
 * «:», así que se cortan por el primero y el último; la hora sí los lleva, así que se quita la del propio
 * movimiento. Si el movimiento ya no tiene hora, la referencia es el primer campo (las del BNC son dígitos).
 */
fun referenciaImportada(tx: Transaction): String? {
    val clave = tx.importKey?.removePrefix("bnc:")?.takeIf { it != tx.importKey } ?: return null
    val trasFecha = clave.substringAfter(':', "")
    val refYHora = trasFecha.substringBeforeLast(':', "")
    if (refYHora.isEmpty()) return null
    val hora = tx.time
    val ref = if (hora != null && refYHora.endsWith(":$hora")) refYHora.removeSuffix(":$hora") else refYHora.substringBefore(':')
    return normalizarReferencia(ref)
}

data class BncDraft(
    val tx: Transaction,
    val saldoMinor: Long,
    val duplicate: Boolean,
    val motivoDuplicado: String?,
    val isCommission: Boolean,
    val pairRef: String?,
    val matchedRuleId: String?,
)

private val COMISION = Regex("comisi[oóOÓ]n", RegexOption.IGNORE_CASE)
private val PAGO_MOVIL = Regex("pago m[oóOÓ]vil", RegexOption.IGNORE_CASE)

fun mapBncRows(
    rows: List<BncRow>,
    accountId: String,
    existingKeys: Set<String>,
    rules: List<Rule>,
    categories: List<Category>,
    capturadas: List<ReferenciaCapturada> = emptyList(),
): List<BncDraft> {
    // La pareja cargo+comisión se cuenta sobre filas DISTINTAS (por bncImportKey): una fila repetida en el mismo
    // texto (export pegado dos veces; la previa la desmarca como «Repetida en este texto») no es su propia pareja.
    // La columna sigue siendo `Referencia` (T25).
    val porReferencia = rows
        .filter { it.referencia != "" && it.referencia != "0" }
        .distinctBy { bncImportKey(it) }
        .groupingBy { it.referencia }
        .eachCount()
    val idComisiones = categories.firstOrNull { it.id == "cat-comisiones" }?.id
    val idPagoMovil = categories.firstOrNull { it.id == "cat-pago-movil-recibido" }?.id
    // D8 + E24: referencia normalizada + monto absoluto + sentido. Cada captura marca como mucho una fila
    // (multiset que se consume, en el orden de las filas): un reverso con el sentido opuesto no coincide.
    // La referencia casa exacta o por sufijo (refsCasan); a igualdad de lo demás se consume antes la exacta.
    val disponibles = capturadas.filter { normalizarReferencia(it.referencia).isNotEmpty() }.toMutableList()

    return rows.filter { it.amountMinor != 0L }.map { r ->
        val texto = "${r.tipOperacion} ${r.descripcion}"
        val isCommission = COMISION.containsMatchIn(r.tipOperacion)
        val regla = matchCategory(texto, rules)
        val esIngreso = r.amountMinor > 0
        var categoryId: String? = regla?.categoryId
        if (categoryId == null && isCommission && idComisiones != null) categoryId = idComisiones
        if (categoryId == null && esIngreso && idPagoMovil != null && PAGO_MOVIL.containsMatchIn(texto)) categoryId = idPagoMovil

        val importKey = bncImportKey(r)
        val refNorm = normalizarReferencia(r.referencia)
        val porImportKey = importKey in existingKeys
        val tipoFila = if (esIngreso) TipoCaptura.INGRESO else TipoCaptura.GASTO
        // Una fila ya duplicada por importKey no consume captura: la captura queda para otra fila real.
        val candidatas = if (porImportKey || refNorm.isEmpty()) emptyList() else disponibles.filter {
            it.montoMinor == abs(r.amountMinor) && it.tipo == tipoFila && refsCasan(it.referencia, r.referencia)
        }
        val elegida = candidatas.firstOrNull { normalizarReferencia(it.referencia) == refNorm } ?: candidatas.firstOrNull()
        val porCaptura = elegida != null
        if (elegida != null) disponibles.remove(elegida)
        BncDraft(
            tx = Transaction(
                id = nuevoId(),
                type = if (esIngreso) TxType.INCOME else TxType.EXPENSE,
                accountId = accountId,
                amountMinor = abs(r.amountMinor),
                date = r.date,
                time = r.time,
                categoryId = categoryId,
                note = r.descripcion,
                source = TxSource.BNC,
                importKey = importKey,
            ),
            saldoMinor = r.saldoMinor,
            duplicate = porImportKey || porCaptura,
            motivoDuplicado = when {
                porImportKey -> MOTIVO_YA_IMPORTADO
                porCaptura -> MOTIVO_POR_CAPTURA
                else -> null
            },
            isCommission = isCommission,
            pairRef = if ((porReferencia[r.referencia] ?: 0) >= 2) r.referencia else null,
            matchedRuleId = regla?.id,
        )
    }
}

enum class GapStatus { PRIMERA_IMPORTACION, SOLAPADO, CONTINUO, HUECO }

/** rows en orden de archivo (descendente). El previo viene de Account.bncLastImportKey/bncLastSaldoMinor. */
fun detectGap(rows: List<BncRow>, previoImportKey: String?, previoSaldoMinor: Long?): GapStatus {
    if (previoImportKey.isNullOrEmpty() || previoSaldoMinor == null) return GapStatus.PRIMERA_IMPORTACION
    if (rows.any { bncImportKey(it) == previoImportKey }) return GapStatus.SOLAPADO
    val masVieja = rows.lastOrNull() ?: return GapStatus.HUECO
    val saldoAntes = masVieja.saldoMinor - masVieja.amountMinor
    return if (saldoAntes == previoSaldoMinor) GapStatus.CONTINUO else GapStatus.HUECO
}

fun newestRow(rows: List<BncRow>): BncRow? = rows.firstOrNull()
