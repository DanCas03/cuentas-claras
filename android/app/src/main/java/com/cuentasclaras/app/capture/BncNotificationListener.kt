package com.cuentasclaras.app.capture

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.cuentasclaras.app.CuentasApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/** Texto estable de una notificación (E29): título + cuerpo, donde el cuerpo es BIG_TEXT si existe
 *  y si no TEXT. Así una reemisión con o sin BIG_TEXT produce el mismo hash. */
internal fun textoDeNotificacion(extras: Bundle): String {
    val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val cuerpo = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()).orEmpty().trim()
    return if (titulo.isEmpty() || cuerpo.contains(titulo)) cuerpo else "$titulo $cuerpo"
}

private const val NO_SON_MOVIMIENTOS = Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT

class BncNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ultimoLatidoMs = 0L
    private val container get() = (application as CuentasApp).container

    /** Ejecuta `bloque` en el scope del servicio; los fallos (DataStore, etc.) quedan en logcat. */
    private fun conLog(mensaje: String, bloque: suspend () -> Unit) {
        scope.launch {
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CuentasClaras", mensaje, e)
            }
        }
    }

    override fun onListenerConnected() {
        container.estadoListener.marcarEnlazado(true)
        Log.i("CuentasClaras", "Listener conectado")
        conLog("No se pudo guardar el latido") { container.estadoListener.latido() }
        // D4: los avisos del BNC que llegaron con el listener caído siguen en la cortina; se capturan ahora por el
        // mismo camino que onNotificationPosted. Los ya capturados no se duplican: `registrar` deduplica por el hash
        // del texto en las 24 h anteriores a `ahora`, y aquí `ahora` es la hora en que se publicó la notificación.
        conLog("No se pudieron revisar las notificaciones activas") {
            for (sbn in activeNotifications.orEmpty()) {
                val texto = textoCapturable(sbn) ?: continue
                capturar(texto, Instant.ofEpochMilli(sbn.postTime))
            }
        }
    }

    override fun onListenerDisconnected() {
        container.estadoListener.marcarEnlazado(false)
        Log.i("CuentasClaras", "Listener desconectado")
        conLog("No se pudo guardar la desconexión") { container.estadoListener.desconectado() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val ahora = System.currentTimeMillis()
            if (ahora - ultimoLatidoMs > 15 * 60 * 1000L) {
                ultimoLatidoMs = ahora
                conLog("No se pudo guardar el latido") { container.estadoListener.latido() }
            }
            val texto = textoCapturable(sbn) ?: return
            scope.launch { capturar(texto, Instant.now()) }
        } catch (e: Exception) {
            Log.w("CuentasClaras", "Notificación ignorada por error", e)
        }
    }

    /** Texto de `sbn` si es un movimiento que se captura; null si no (otro paquete, resumen, en curso o vacío). */
    private fun textoCapturable(sbn: StatusBarNotification): String? {
        if (sbn.packageName !in paquetesCapturables()) return null
        // Resúmenes de grupo y notificaciones en curso (progreso, sesión) no son movimientos: sin esto
        // crearían capturas «sin reconocer».
        if ((sbn.notification.flags and NO_SON_MOVIMIENTOS) != 0) return null
        return textoDeNotificacion(sbn.notification.extras).takeIf { it.isNotBlank() }
    }

    /** Guarda la captura (con su dedupe) y, si es nueva, publica la notificación propia. Los fallos van a logcat. */
    private suspend fun capturar(texto: String, ahora: Instant) {
        try {
            val captura = container.capturas.registrar(texto, ahora) ?: return
            Notificador.publicar(this, captura)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CuentasClaras", "No se pudo guardar la captura", e)
        }
    }

    override fun onDestroy() {
        container.estadoListener.marcarEnlazado(false)
        scope.cancel()
        super.onDestroy()
    }
}
