package com.cuentasclaras.app.capture

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CapturaEntity
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.EstadoCaptura
import com.cuentasclaras.app.domain.instanteIso
import com.cuentasclaras.app.domain.nuevoId
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

private fun sha256(texto: String): String =
    MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

class CapturaRepo(private val db: CuentasDb) {
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

    suspend fun descartar(id: String) = db.capturas().marcarDescartada(id)
}
