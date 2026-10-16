package com.cuentasclaras.app.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cuentasclaras.app.MainActivity
import com.cuentasclaras.app.R
import com.cuentasclaras.app.data.db.CapturaEntity
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.formatMoney

object Notificador {
    /** La importancia de un canal no se puede subir después de crearlo: si cambia, otro id. */
    const val CANAL = "capturas_bnc"
    const val EXTRA_CAPTURA_ID = "capturaId"

    fun crearCanal(ctx: Context) {
        val canal = NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Capturas BNC")
            .setDescription("Pagos y compras del BNC listos para registrar")
            .build()
        NotificationManagerCompat.from(ctx).createNotificationChannel(canal)
    }

    fun titulo(c: CapturaEntity): String {
        val monto = c.montoMinor
        return when {
            !c.reconocida || monto == null -> "Notificación del BNC sin reconocer"
            c.tipo == TipoCaptura.INGRESO -> "Pago móvil recibido ${formatMoney(monto, Currency.VES)}"
            else -> "Compra POS ${formatMoney(monto, Currency.VES)}"
        }
    }

    /** Id de la notificación propia de una captura: el mismo al publicar y al cancelar. */
    private fun idNotificacion(capturaId: String): Int = capturaId.hashCode()

    /** Sin permiso para avisar no se publica nada, pero la captura YA está en Room y aparece en la tarjeta
     *  «Capturas pendientes» de Inicio. `notificacionesPermitidas` comprueba el permiso de Android 13+. */
    @SuppressLint("MissingPermission")
    fun publicar(ctx: Context, c: CapturaEntity) {
        if (!PermisosXiaomi.notificacionesPermitidas(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_CAPTURA_ID, c.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendiente = PendingIntent.getActivity(
            ctx, idNotificacion(c.id), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo(c))
            .setContentText("Toca para registrar")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendiente)
            .build()
        NotificationManagerCompat.from(ctx).notify(idNotificacion(c.id), notificacion)
    }

    /** Quita la notificación propia de una captura ya registrada o descartada (desde Nuevo o desde Inicio). */
    fun cancelar(ctx: Context, capturaId: String) {
        NotificationManagerCompat.from(ctx).cancel(idNotificacion(capturaId))
    }
}
