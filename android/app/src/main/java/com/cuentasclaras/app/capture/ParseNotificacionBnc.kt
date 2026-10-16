package com.cuentasclaras.app.capture

import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.parseVenezuelanAmount
import java.text.Normalizer

data class CapturaBnc(
    val tipo: TipoCaptura,
    /** Céntimos de Bs (las notificaciones del BNC siempre son en Bs). */
    val montoMinor: Long,
    /** Nota lista para el movimiento: tipo de operación + referencia. */
    val nota: String,
    /** Dígitos de «Ref:»; alimenta la deduplicación con el import del TXT (D8). */
    val referencia: String,
)

private data class Reconocedor(val tipo: TipoCaptura, val regex: Regex, val nota: (String) -> String)

// Estructura real (captura de Daniel 2026-07-22; valores enmascarados):
//   BNC Pago Movil Recibido Bs.<monto> Telf.04XX***NNNN Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …
//   BNC Compra POS Bs.<monto> Tarj:<4 dígitos> Dia:dd/mm/aa-HH:MM Ref:<dígitos> Llamar al …
private val RECONOCEDORES = listOf(
    Reconocedor(
        TipoCaptura.INGRESO,
        Regex("""BNC Pago Movil Recibido Bs\.(\d+,\d{2})\b.*?\bRef:(\d+)""", RegexOption.IGNORE_CASE),
    ) { "Pago móvil recibido · ref $it" },
    Reconocedor(
        TipoCaptura.GASTO,
        Regex("""BNC Compra POS Bs\.(\d+,\d{2})\b.*?\bRef:(\d+)""", RegexOption.IGNORE_CASE),
    ) { "Compra POS · ref $it" },
)

// Tras NFKC, los espacios Unicode raros ya son U+0020; la clase explícita cubre los que NFKC
// no toca (U+2028/U+2029/U+FEFF). Sin (?U): el motor de regex de Android no lo garantiza.
private val ESPACIOS = Regex("""[\s\u00A0\u2007\u202F\u2028\u2029\uFEFF]+""")

/** Mismo criterio que la web: NFKC + espacios colapsados + trim. También es la base del hash. */
fun normalizarTexto(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFKC).replace(ESPACIOS, " ").trim()

/** Reconoce pago móvil recibido y compra con tarjeta del BNC. Cualquier otra cosa → null. */
fun parseNotificacionBnc(texto: String): CapturaBnc? {
    val limpio = normalizarTexto(texto)
    if (limpio.isEmpty()) return null
    for (r in RECONOCEDORES) {
        val m = r.regex.find(limpio) ?: continue
        val montoMinor = parseVenezuelanAmount(m.groupValues[1]) ?: continue
        if (montoMinor <= 0) continue
        val referencia = m.groupValues[2]
        return CapturaBnc(r.tipo, montoMinor, r.nota(referencia), referencia)
    }
    return null
}
