package com.cuentasclaras.app.capture

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.almacenListener by preferencesDataStore(name = "estado_listener")
private val ULTIMO_LATIDO = longPreferencesKey("ultimoLatidoMillis")
private val ULTIMO_REENLACE = longPreferencesKey("ultimoReenlaceMillis")
private const val SIN_LATIDO_MAX_MS = 6 * 60 * 60 * 1000L
private const val ENTRE_REENLACES_MS = 10 * 60 * 1000L

class EstadoListener(private val context: Context) {
    private val componente = ComponentName(context, BncNotificationListener::class.java)

    fun accesoConcedido(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    suspend fun latido() {
        context.almacenListener.edit { it[ULTIMO_LATIDO] = System.currentTimeMillis() }
    }

    suspend fun desconectado() {
        context.almacenListener.edit { it[ULTIMO_LATIDO] = 0L }
    }

    /** Llamado en MainActivity.onResume. El re-enlace efectivo es alternar el componente (E28):
     *  `requestRebind` solo deshace un `requestUnbind` propio y no sirve si el proceso murió. */
    suspend fun reenlazarSiHaceFalta() {
        if (!accesoConcedido()) return
        val ahora = System.currentTimeMillis()
        val datos = context.almacenListener.data.first()
        val ultimo = datos[ULTIMO_LATIDO] ?: 0L
        if (ultimo != 0L && ahora - ultimo < SIN_LATIDO_MAX_MS) return
        // Sin esto, un listener que no vuelve provocaría una alternancia en cada onResume.
        if (ahora - (datos[ULTIMO_REENLACE] ?: 0L) < ENTRE_REENLACES_MS) return
        context.almacenListener.edit { it[ULTIMO_REENLACE] = ahora }
        Log.w("CuentasClaras", "Listener sin latido reciente: re-enlazando")
        val pm = context.packageManager
        // Las dos llamadas seguidas y con DONT_KILL_APP: si el PACKAGE_CHANGED se procesara con el
        // componente deshabilitado, el sistema revocaría el acceso a notificaciones.
        pm.setComponentEnabledSetting(componente, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(componente, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
}
