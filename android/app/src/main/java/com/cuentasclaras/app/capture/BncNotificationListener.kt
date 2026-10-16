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

/** Texto estable de una notificación (E29): título + cuerpo, donde el cuerpo es BIG_TEXT si existe
 *  y si no TEXT. Así una reemisión con o sin BIG_TEXT produce el mismo hash. */
internal fun textoDeNotificacion(extras: Bundle): String {
    val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val cuerpo = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()).orEmpty().trim()
    return if (titulo.isEmpty() || cuerpo.contains(titulo)) cuerpo else "$titulo $cuerpo"
}

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
        conLog("No se pudo guardar el latido") { container.estadoListener.latido() }
    }

    override fun onListenerDisconnected() {
        container.estadoListener.marcarEnlazado(false)
        conLog("No se pudo guardar la desconexión") { container.estadoListener.desconectado() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val ahora = System.currentTimeMillis()
            if (ahora - ultimoLatidoMs > 15 * 60 * 1000L) {
                ultimoLatidoMs = ahora
                conLog("No se pudo guardar el latido") { container.estadoListener.latido() }
            }
            if (sbn.packageName !in paquetesCapturables(this)) return
            val texto = textoDeNotificacion(sbn.notification.extras)
            if (texto.isBlank()) return
            scope.launch {
                try {
                    val captura = container.capturas.registrar(texto) ?: return@launch
                    Notificador.publicar(this@BncNotificationListener, captura)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CuentasClaras", "No se pudo guardar la captura", e)
                }
            }
        } catch (e: Exception) {
            Log.w("CuentasClaras", "Notificación ignorada por error", e)
        }
    }

    override fun onDestroy() {
        container.estadoListener.marcarEnlazado(false)
        scope.cancel()
        super.onDestroy()
    }
}
