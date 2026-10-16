package com.cuentasclaras.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.backup.ValidacionRespaldo
import com.cuentasclaras.app.backup.importBackup
import com.cuentasclaras.app.backup.leerRespaldo
import com.cuentasclaras.app.data.seedIfEmpty
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.rates.proveedoresPorDefecto
import com.cuentasclaras.app.rates.refreshRatesOncePerDay
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.pantallas.respaldo.RespaldoSeccion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Arranque { CARGANDO, LISTO, ERROR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CuentasApp).container
        setContent {
            MaterialTheme {
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
                    Arranque.LISTO -> RaizApp()
                }
            }
        }
    }

    /** D12: solo en builds depurables. `adb … am start … --es respaldoDebug archivo.json`
     *  carga filesDir/archivo.json (puesto con `run-as`) y lo borra. */
    private suspend fun cargarRespaldoDebug(container: AppContainer) {
        val depurable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val nombre = intent.getStringExtra("respaldoDebug") ?: return
        if (!depurable) return
        val archivo = File(filesDir, nombre)
        if (!archivo.exists()) return
        val texto = withContext(Dispatchers.IO) { archivo.readText() }
        when (val v = leerRespaldo(texto)) {
            is ValidacionRespaldo.Ok -> importBackup(container.db, v.backup)
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
private fun RaizApp() {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { Avisos.mensajes.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { relleno ->
        Box(Modifier.padding(relleno).padding(16.dp)) { RespaldoSeccion() }
    }
}
