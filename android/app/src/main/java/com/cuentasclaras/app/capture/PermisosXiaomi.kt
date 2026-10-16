package com.cuentasclaras.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Defensas para que la captura sobreviva en Xiaomi (MIUI/HyperOS). Autoinicio y bloqueo en Recientes
 * no se pueden detectar (E28): la pantalla de Permisos los marca como «no detectable».
 *
 * Las aperturas de ajustes devuelven false si ni la pantalla pedida ni los detalles de la app se pudieron abrir
 * (la UI avisa); nunca lanzan.
 */
object PermisosXiaomi {
    fun bateriaSinRestricciones(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    /** true si falta el permiso de ejecución POST_NOTIFICATIONS (solo existe desde Android 13). */
    fun faltaPermisoDeAvisos(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /**
     * La app puede avisar de verdad: permiso de ejecución (Android 13+), notificaciones de la app activadas y el
     * canal «Capturas BNC» creado y sin importancia NONE (MIUI deja apagar el canal o toda la app sin revocar el
     * permiso). `Notificador.publicar` usa esta misma comprobación.
     */
    fun notificacionesPermitidas(ctx: Context): Boolean {
        if (faltaPermisoDeAvisos(ctx)) return false
        val gestor = NotificationManagerCompat.from(ctx)
        if (!gestor.areNotificationsEnabled()) return false
        val canal = gestor.getNotificationChannelCompat(Notificador.CANAL) ?: return false
        return canal.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    fun abrirAccesoNotificaciones(ctx: Context): Boolean =
        abrir(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) || abrirDetallesApp(ctx)

    /**
     * Sin exención, el diálogo AOSP la pide directamente. Ya exenta, ese diálogo no se mostraría: se abren los
     * detalles de la app, donde vive «Sin restricciones» de MIUI (Ahorro de batería).
     */
    @SuppressLint("BatteryLife") // App personal fuera de Play Store: pedirlo directamente es lo correcto aquí.
    fun abrirBateria(ctx: Context): Boolean {
        if (bateriaSinRestricciones(ctx)) return abrirDetallesApp(ctx)
        val directo = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        return abrir(ctx, directo) || abrirDetallesApp(ctx)
    }

    /** Ajuste de notificaciones de la app (para encender la app o el canal «Capturas BNC»). */
    fun abrirAjusteNotificaciones(ctx: Context): Boolean =
        abrir(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) ||
            abrirDetallesApp(ctx)

    /** Best-effort: la pantalla de autoinicio de MIUI. false si no existe (la UI muestra instrucciones). */
    fun abrirAutoinicio(ctx: Context): Boolean = abrir(
        ctx,
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
    )

    private fun abrirDetallesApp(ctx: Context): Boolean =
        abrir(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    /**
     * Cualquier fallo al abrir (actividad inexistente, SecurityException de MIUI…) es false, nunca un crash.
     *
     * Desde una Activity se lanza sin `NEW_TASK`: así el ajuste entra en la tarea de la app y Atrás vuelve a ella.
     * Con `NEW_TASK` se abría sobre la tarea que ya tuviera `com.android.settings` y Atrás llevaba a otra pantalla
     * de Ajustes (Wi-Fi en el Xiaomi). `NEW_TASK` solo queda para un contexto sin Activity, que lo exige.
     */
    private fun abrir(ctx: Context, intent: Intent): Boolean = try {
        val actividad = ctx.actividad()
        if (actividad != null) actividad.startActivity(intent) else ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** La Activity detrás de `this` (Compose da la Activity, a veces envuelta en un `ContextWrapper`), o null. */
    private tailrec fun Context.actividad(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.actividad()
        else -> null
    }
}
