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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.almacenListener by preferencesDataStore(name = "estado_listener")
private val ULTIMO_LATIDO = longPreferencesKey("ultimoLatidoMillis")
private val ULTIMO_REENLACE = longPreferencesKey("ultimoReenlaceMillis")
/** true entre onListenerConnected y onListenerDisconnected/onDestroy de ESTE proceso. */
private val enlazadoEnProceso = MutableStateFlow(false)
/**
 * D1: el acceso está concedido, el listener sigue sin enlazar pasada la gracia y el freno de 10 min impide alternar.
 * Se apaga en cuanto el listener se conecta.
 */
private val capturaEnPausa = MutableStateFlow(false)
private const val SIN_LATIDO_MAX_MS = 6 * 60 * 60 * 1000L
private const val ENTRE_REENLACES_MS = 10 * 60 * 1000L
/**
 * Margen para que el sistema re-enlace solo el listener al arrancar el proceso antes de alternar nosotros. AOSP
 * re-enlaza a los ~10 s (`ManagedServices`), así que 12 s dejan pasar ese re-enlace sin alternar en vano.
 */
private const val GRACIA_REENLACE_MS = 12_000L
private val candado = Mutex()

class EstadoListener(private val context: Context) {
    private val componente = ComponentName(context, BncNotificationListener::class.java)

    fun accesoConcedido(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** El listener lo marca de forma síncrona en onListenerConnected/Disconnected: si está enlazado
     *  en ESTE proceso no hace falta alternar el componente (cada alternancia arriesga el acceso). */
    fun marcarEnlazado(enlazado: Boolean) {
        enlazadoEnProceso.value = enlazado
        if (enlazado) capturaEnPausa.value = false
    }

    /**
     * true si la captura está en pausa: acceso concedido, pero el listener no se enlazó en este proceso pasada la
     * gracia y el freno impide alternarlo (en MIUI sin Autoinicio el sistema rechaza el re-enlace). Inicio avisa.
     */
    val enPausa: StateFlow<Boolean> = capturaEnPausa.asStateFlow()

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
     *   re-enlazaba solo, no había re-enlace durante horas. Por eso se espera una gracia de 12 s (el sistema suele
     *   re-enlazar solo al arrancar el proceso, a los ~10 s en AOSP, y alternar sin necesidad arriesga el acceso),
     *   se vuelve a mirar el flag y, si sigue en false, se alterna.
     * En ambos casos manda el freno de 10 min entre alternancias. El `Mutex` evita que dos onResume seguidos
     * pasen los dos el freno mientras esperan la gracia.
     *
     * D1: si el freno corta la alternancia, o si tras alternar el listener sigue sin enlazar pasada la gracia, la
     * captura queda en pausa (`enPausa`) hasta que el listener se conecte: Inicio lo avisa en vez de callar.
     */
    suspend fun reenlazarSiHaceFalta(): Unit = candado.withLock {
        if (enlazadoEnProceso.value) return@withLock
        if (!withContext(Dispatchers.IO) { accesoConcedido() }) return@withLock
        val ultimo = context.almacenListener.data.first()[ULTIMO_LATIDO] ?: 0L
        val latidoReciente = ultimo != 0L && System.currentTimeMillis() - ultimo < SIN_LATIDO_MAX_MS
        if (latidoReciente) {
            delay(GRACIA_REENLACE_MS)
            if (enlazadoEnProceso.value) return@withLock
        }
        val ahora = System.currentTimeMillis()
        // Sin esto, un listener que no vuelve provocaría una alternancia en cada onResume.
        if (ahora - (context.almacenListener.data.first()[ULTIMO_REENLACE] ?: 0L) < ENTRE_REENLACES_MS) {
            // Sin latido reciente no se esperó la gracia: el sistema aún puede re-enlazar solo al arrancar el proceso.
            if (!latidoReciente) {
                delay(GRACIA_REENLACE_MS)
                if (enlazadoEnProceso.value) return@withLock
            }
            capturaEnPausa.value = true
            Log.w("CuentasClaras", "Listener sin enlazar y re-enlace frenado: captura en pausa")
            return@withLock
        }
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
        // Ahora manda el freno: si la alternancia no enlazó pasada la gracia, no habrá otra en 10 min.
        delay(GRACIA_REENLACE_MS)
        if (!enlazadoEnProceso.value) {
            capturaEnPausa.value = true
            Log.w("CuentasClaras", "Listener sin enlazar tras re-enlazar: captura en pausa")
        }
    }
}
