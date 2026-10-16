package com.cuentasclaras.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.cuentasclaras.app.backup.ValidacionRespaldo
import com.cuentasclaras.app.backup.leerRespaldo
import com.cuentasclaras.app.backup.restaurarConPrevio
import com.cuentasclaras.app.data.seedIfEmpty
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.rates.proveedoresPorDefecto
import com.cuentasclaras.app.rates.refreshRatesOncePerDay
import com.cuentasclaras.app.ui.componentes.AvisosRaiz
import com.cuentasclaras.app.ui.componentes.LocalAvisos
import com.cuentasclaras.app.ui.componentes.rememberAnfitrionAvisos
import com.cuentasclaras.app.ui.nav.BarraInferior
import com.cuentasclaras.app.capture.Notificador
import com.cuentasclaras.app.ui.nav.CuentasNavHost
import com.cuentasclaras.app.ui.nav.Rutas
import com.cuentasclaras.app.ui.theme.CuentasTema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Arranque { CARGANDO, LISTO, ERROR }

class MainActivity : ComponentActivity() {
    /** Captura pedida por el toque en la notificación propia; la consume `RaizApp`. */
    val capturaPedida = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) leerCaptura(intent)
        val container = (application as CuentasApp).container
        setContent {
            CuentasTema {
                var arranque by remember { mutableStateOf(Arranque.CARGANDO) }
                LaunchedEffect(Unit) {
                    arranque = try {
                        seedIfEmpty(container.db)
                        cargarRespaldoDebug(container)
                        Arranque.LISTO
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("CuentasClaras", "No se pudo iniciar la base de datos", e)
                        Arranque.ERROR
                    }
                    if (arranque == Arranque.LISTO) {
                        launch {
                            // Sin red o sin proveedor: silencioso; la UI muestra la tasa vieja.
                            // Fuera del hilo de UI: la red corre en Dispatchers.IO dentro de los proveedores.
                            try {
                                refreshRatesOncePerDay(container.db, todayIso(), proveedoresPorDefecto(container.http))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w("CuentasClaras", "No se pudieron refrescar las tasas", e)
                            }
                        }
                    }
                }
                when (arranque) {
                    Arranque.CARGANDO -> Unit
                    Arranque.ERROR -> PantallaErrorInicio()
                    Arranque.LISTO -> RaizApp(capturaPedida)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leerCaptura(intent)
    }

    override fun onResume() {
        super.onResume()
        val container = (application as CuentasApp).container
        lifecycleScope.launch {
            try {
                container.estadoListener.reenlazarSiHaceFalta()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CuentasClaras", "No se pudo re-enlazar el listener", e)
            }
        }
    }

    private fun leerCaptura(intent: Intent?) {
        intent?.getStringExtra(Notificador.EXTRA_CAPTURA_ID)?.let { capturaPedida.value = it }
    }

    /** D12: solo en el build type `verif` (no en debug, que Daniel usa a diario, ni en release).
     *  `adb … am start … --es respaldoDebug archivo.json` carga filesDir/archivo.json (puesto con `run-as`),
     *  pasando por `restaurarConPrevio`, y lo borra. El nombre debe estar directamente en filesDir. */
    private suspend fun cargarRespaldoDebug(container: AppContainer) {
        if (BuildConfig.BUILD_TYPE != "verif") return
        val nombre = intent.getStringExtra("respaldoDebug") ?: return
        if (nombre.isEmpty() || nombre.contains('/') || nombre.contains('\\') || nombre.contains("..")) {
            Log.w("CuentasClaras", "respaldoDebug rechazado: el nombre debe estar directamente en filesDir")
            return
        }
        val archivo = File(filesDir, nombre)
        if (!archivo.isFile) return
        val texto = withContext(Dispatchers.IO) { archivo.readText() }
        when (val v = withContext(Dispatchers.Default) { leerRespaldo(texto) }) {
            is ValidacionRespaldo.Ok -> restaurarConPrevio(container.db, container.dirRespaldos, v.backup)
            is ValidacionRespaldo.Error -> Log.w("CuentasClaras", "respaldoDebug inválido: ${v.mensaje}")
        }
        archivo.delete()
    }
}

@Composable
private fun PantallaErrorInicio() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("No se pudo iniciar la base de datos local. Cierra y vuelve a abrir la app.")
    }
}

@Composable
private fun RaizApp(capturaPedida: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    // D2: una sola cola de avisos; la pinta la raíz o, si hay una hoja o un diálogo abiertos, el de más arriba.
    val avisos = rememberAnfitrionAvisos()
    LaunchedEffect(Unit) {
        capturaPedida.filterNotNull().collect { id ->
            nav.navigate(Rutas.nuevo(capturaId = id)) { launchSingleTop = true }
            capturaPedida.value = null
        }
    }
    CompositionLocalProvider(LocalAvisos provides avisos) {
        Scaffold(
            bottomBar = { BarraInferior(nav) },
            snackbarHost = { AvisosRaiz(avisos) },
        ) { relleno ->
            CuentasNavHost(nav, Modifier.padding(relleno))
        }
    }
}
