package com.cuentasclaras.app

import android.app.Application
import android.content.Context
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.rates.crearHttp
import okhttp3.OkHttpClient
import java.io.File

/** Contenedor manual de dependencias (sin Hilt, spec §3.4). Una instancia por proceso. */
class AppContainer(context: Context) {
    /** Respaldos de seguridad previos a cada restauración (D16). */
    val dirRespaldos: File = File(context.filesDir, "respaldos")
    val db: CuentasDb = CuentasDb.crear(context)
    val http: OkHttpClient = crearHttp()
}

class CuentasApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
