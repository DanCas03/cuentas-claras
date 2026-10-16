package com.cuentasclaras.app.capture

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CapturaEntity
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.EstadoCaptura
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.instanteIso
import com.cuentasclaras.app.domain.nuevoId
import com.cuentasclaras.app.importar.bnc.normalizarReferencia
import com.cuentasclaras.app.importar.bnc.referenciaImportada
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

private fun sha256(texto: String): String =
    MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

/** Mensaje al registrar o descartar una captura que otra pantalla ya resolvió. */
const val MENSAJE_CAPTURA_RESUELTA = "Esta notificación ya se registró o se descartó"

/**
 * @param alResolver se llama tras registrar o descartar con éxito una captura; la app lo usa para quitar su
 *   notificación propia (`Notificador.cancelar`).
 */
class CapturaRepo(private val db: CuentasDb, private val alResolver: (capturaId: String) -> Unit = {}) {
    val pendientes: Flow<List<CapturaEntity>> = db.capturas().observarPendientes()

    /** Persiste la notificación ANTES de avisar (spec §3.8). Devuelve null si el texto está vacío o
     *  si la misma notificación ya se capturó en las últimas 24 h (el BNC y MIUI reemiten).
     *  `contarRecientes` + `insertar` van en UNA transacción (E17): dos reemisiones casi simultáneas
     *  no crean dos capturas. El listener de la Tarea 19 debe llamar aquí, no al DAO por separado. */
    suspend fun registrar(texto: String, ahora: Instant = Instant.now()): CapturaEntity? {
        val limpio = normalizarTexto(texto)
        if (limpio.isEmpty()) return null
        val hash = sha256(limpio)
        return db.withTransaction {
            if (db.capturas().contarRecientes(hash, instanteIso(ahora.minus(Duration.ofHours(24)))) > 0) {
                null
            } else {
                val p = parseNotificacionBnc(limpio)
                val captura = CapturaEntity(
                    id = nuevoId(),
                    recibidaIso = instanteIso(ahora),
                    textoCrudo = texto.take(2000),
                    reconocida = p != null,
                    tipo = p?.tipo,
                    montoMinor = p?.montoMinor,
                    referencia = p?.referencia,
                    nota = p?.nota,
                    estado = EstadoCaptura.PENDIENTE,
                    hash = hash,
                    txId = null,
                )
                db.capturas().insertar(captura)
                captura
            }
        }
    }

    suspend fun porId(id: String): CapturaEntity? = db.capturas().porId(id)

    /**
     * Registra la captura con su movimiento, todo-o-nada (restricción 8): inserta `tx` y marca la captura como
     * REGISTRADA en una transacción. Si la captura ya no estaba PENDIENTE (se registró o descartó desde otra
     * pantalla), lanza y el movimiento NO queda guardado.
     *
     * `ademas` corre dentro de la MISMA transacción, tras insertar `tx` (T30: asignar el movimiento a una ocurrencia
     * del calendario con `asignarEvento`). Si lanza, no queda ni el movimiento ni la captura registrada, y la
     * notificación sigue puesta (`alResolver` solo se llama tras confirmar).
     */
    suspend fun registrarConTx(capturaId: String, tx: Transaction, ademas: suspend () -> Unit = {}) {
        db.withTransaction {
            db.transactions().insertar(tx.toEntity())
            ademas()
            check(db.capturas().marcarRegistrada(capturaId, tx.id) == 1) { MENSAJE_CAPTURA_RESUELTA }
        }
        alResolver(capturaId)
    }

    /** Descarta una captura PENDIENTE; si ya se registró o descartó, lanza sin cambiar nada. */
    suspend fun descartar(id: String) {
        check(db.capturas().marcarDescartada(id) == 1) { MENSAJE_CAPTURA_RESUELTA }
        alResolver(id)
    }

    /**
     * true si ya hay un movimiento importado del TXT del BNC que parece ser esta captura: misma referencia
     * normalizada (no vacía), mismo |monto| y mismo sentido. Es el duplicado inverso de D8/E24 (la captura quedó
     * pendiente y el TXT ya trajo el movimiento); solo sirve para avisar, no bloquea el registro.
     */
    suspend fun pareceImportada(c: CapturaEntity): Boolean {
        val ref = normalizarReferencia(c.referencia ?: return false)
        val monto = c.montoMinor ?: return false
        val tipo = when (c.tipo ?: return false) {
            TipoCaptura.INGRESO -> TxType.INCOME
            TipoCaptura.GASTO -> TxType.EXPENSE
        }
        if (ref.isEmpty()) return false
        return db.transactions().importadasBnc(tipo.name, monto).any { referenciaImportada(it.toDomain()) == ref }
    }
}
