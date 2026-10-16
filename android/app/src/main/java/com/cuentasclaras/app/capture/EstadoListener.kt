package com.cuentasclaras.app.capture

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.almacenListener by preferencesDataStore(name = "estado_listener")
private val ULTIMO_LATIDO = longPreferencesKey("ultimoLatidoMillis")
private val ULTIMO_REENLACE = longPreferencesKey("ultimoReenlaceMillis")
@Volatile private var enlazadoEnProceso = false
private const val SIN_LATIDO_MAX_MS = 6 * 60 * 60 * 1000L
private const val ENTRE_REENLACES_MS = 10 * 60 * 1000L
/** Margen para que el sistema re-enlace solo el listener al arrancar el proceso antes de alternar nosotros. */
private const val GRACIA_REENLACE_MS = 4_000L
private val candado = Mutex()

class EstadoListener(private val context: Context) {
    private val componente = ComponentName(context, BncNotificationListener::class.java)

    fun accesoConcedido(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** El listener lo marca de forma síncrona en onListenerConnected/Disconnected: si está enlazado
     *  en ESTE proceso no hace falta alternar el componente (cada alternancia arriesga el acceso). */
    fun marcarEnlazado(enlazado: Boolean) {
        enlazadoEnProceso = enlazado
    }

    suspend fun latido() {
        context.almacenListener.edit { it[ULTIMO_LATIDO] = System.currentTimeMillis() }
    }

    suspend fun desconectado() {
        context.almacenListener.edit { it[ULTIMO_LATIDO] = 0L }
    }

    /**
     * Llamado en MainActivity.onResume. El re-enlace efectivo es alternar el componente (E28): `requestRebind`
     * solo deshace un `requestUnbind` propio y no sirve si el proceso murió.
     *
     * Decisión, con el acceso concedido y el flag de ESTE proceso en false (el listener no está enlazado aquí):
     * - Sin latido o con el último de hace más de 6 h: alternar ya (el listener lleva horas sin dar señales).
     * - Con un latido reciente: ese latido puede ser del proceso ANTERIOR. Tras un force-stop o un kill,
     *   `onListenerDisconnected` no se llama y el latido viejo sigue «reciente»; si salíamos aquí y MIUI no
     *   re-enlazaba solo, no había re-enlace durante horas. Por eso se espera una gracia corta (el sistema suele
     *   re-enlazar solo al arrancar el proceso, y alternar sin necesidad arriesga el acceso), se vuelve a mirar
     *   el flag y, si sigue en false, se alterna.
     * En ambos casos manda el freno de 10 min entre alternancias. El `Mutex` evita que dos onResume seguidos
     * pasen los dos el freno mientras esperan la gracia.
     */
    suspend fun reenlazarSiHaceFalta(): Unit = candado.withLock {
        if (enlazadoEnProceso) return@withLock
        if (!withContext(Dispatchers.IO) { accesoConcedido() }) return@withLock
        val ultimo = context.almacenListener.data.first()[ULTIMO_LATIDO] ?: 0L
        val latidoReciente = ultimo != 0L && System.currentTimeMillis() - ultimo < SIN_LATIDO_MAX_MS
        if (latidoReciente) {
            delay(GRACIA_REENLACE_MS)
            if (enlazadoEnProceso) return@withLock
        }
        val ahora = System.currentTimeMillis()
        // Sin esto, un listener que no vuelve provocaría una alternancia en cada onResume.
        if (ahora - (context.almacenListener.data.first()[ULTIMO_REENLACE] ?: 0L) < ENTRE_REENLACES_MS) return@withLock
        context.almacenListener.edit { it[ULTIMO_REENLACE] = ahora }
        Log.w(
            "CuentasClaras",
            if (latidoReciente) "Listener sin enlazar tras la gracia: re-enlazando" else "Listener sin latido reciente: re-enlazando",
        )
        val pm = context.packageManager
        // Las dos llamadas seguidas, en el mismo bloque y sin suspensión entre medias, con DONT_KILL_APP:
        // si el PACKAGE_CHANGED se procesara con el componente deshabilitado, el sistema revocaría el acceso.
        withContext(Dispatchers.IO) {
            pm.setComponentEnabledSetting(componente, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(componente, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
