package com.cuentasclaras.app

import android.app.Application
import android.content.Context
import com.cuentasclaras.app.data.AparienciaRepo
import com.cuentasclaras.app.data.IconoAppRepo
import com.cuentasclaras.app.capture.CapturaRepo
import com.cuentasclaras.app.capture.EstadoListener
import com.cuentasclaras.app.capture.Notificador
import com.cuentasclaras.app.data.CuentasRepo
import com.cuentasclaras.app.data.almacenApariencia
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.rates.crearHttp
import okhttp3.OkHttpClient
import java.io.File

/** Contenedor manual de dependencias (sin Hilt, spec §3.4). Una instancia por proceso. */
class AppContainer(context: Context) {
    /** Respaldos de seguridad previos a cada restauración (D16). */
    val dirRespaldos: File = File(context.filesDir, "respaldos")
    val db: CuentasDb = CuentasDb.crear(context)
    /** Lecturas reactivas y escrituras simples sobre `db` (T11). */
    val repo: CuentasRepo = CuentasRepo(db)
    val http: OkHttpClient = crearHttp()
    /** Modo y paletas elegidos (E20), en DataStore Preferences. */
    val apariencia: AparienciaRepo = AparienciaRepo(context.almacenApariencia)
    /** Ícono del lanzador por forma × paleta oscura (spec 2026-10-17 §7). */
    val iconoApp: IconoAppRepo = IconoAppRepo(context)
    /** Capturas de notificaciones del BNC (T18/T19). Al registrar o descartar una, se quita su notificación. */
    val capturas: CapturaRepo = CapturaRepo(db) { capturaId -> Notificador.cancelar(context, capturaId) }
    val estadoListener: EstadoListener = EstadoListener(context)
}

class CuentasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notificador.crearCanal(this)
    }
}
