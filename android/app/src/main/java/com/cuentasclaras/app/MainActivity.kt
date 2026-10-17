package com.cuentasclaras.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cuentasclaras.app.backup.ValidacionRespaldo
import com.cuentasclaras.app.backup.leerRespaldo
import com.cuentasclaras.app.backup.restaurarConPrevio
import com.cuentasclaras.app.data.seedIfEmpty
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.rates.proveedoresPorDefecto
import com.cuentasclaras.app.rates.refreshRatesOncePerDay
import com.cuentasclaras.app.ui.LocalPrimerContenidoListo
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

/**
 * Tope de la retención de la ventana de arranque (V25): si la primera pantalla no avisa (p. ej. la base tarda mucho
 * en sembrarse la primera vez), se dibuja igual y no se queda la ventana de arranque congelada.
 */
private const val TOPE_VENTANA_ARRANQUE_MS = 2_000L

class MainActivity : ComponentActivity() {
    /** Captura pedida por el toque en la notificación propia; la consume `RaizApp`. */
    val capturaPedida = MutableStateFlow<String?>(null)

    /** V25: la primera pantalla ya pinta contenido real (ver [LocalPrimerContenidoListo]). */
    @Volatile
    private var primerContenidoListo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        retenerVentanaDeArranque()
        if (savedInstanceState == null) leerCaptura(intent)
        val container = (application as CuentasApp).container
        setContent {
            // Recordada: un `staticCompositionLocalOf` con una lambda nueva en cada recomposición recompondría todo.
            val avisarListo = remember { { primerContenidoListo = true } }
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
                    Arranque.ERROR -> {
                        SideEffect { avisarListo() }
                        PantallaErrorInicio()
                    }
                    Arranque.LISTO -> CompositionLocalProvider(LocalPrimerContenidoListo provides avisarListo) {
                        RaizApp(capturaPedida)
                    }
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

    /**
     * V25 (T20, punto 5): sin dependencias nuevas. Mientras la primera pantalla no tenga su contenido, el primer
     * dibujado del contenido se cancela y sigue a la vista la ventana de arranque (`@color/fondo_arranque`; la
     * splash del sistema en API 31+). Es el patrón de la plataforma para alargar la ventana de arranque
     * (`OnPreDrawListener` sobre `android.R.id.content`): la composición y la medida siguen corriendo, solo se
     * retiene el dibujado. Con tope [TOPE_VENTANA_ARRANQUE_MS].
     */
    private fun retenerVentanaDeArranque() {
        val contenido = findViewById<View>(android.R.id.content)
        val desde = SystemClock.uptimeMillis()
        contenido.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!primerContenidoListo && SystemClock.uptimeMillis() - desde < TOPE_VENTANA_ARRANQUE_MS) return false
                    contenido.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
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
    // V25: Inicio avisa cuando tiene su primera instantánea; si la app abre en otro destino (pila restaurada tras
    // matar el proceso, o Nuevo desde una notificación), ese destino no espera a Inicio.
    val avisarListo = LocalPrimerContenidoListo.current
    val entrada by nav.currentBackStackEntryAsState()
    val ruta = entrada?.destination?.route
    if (ruta != null && ruta != Rutas.INICIO) SideEffect { avisarListo() }
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
            // El relleno (barras del sistema y barra inferior) se marca como consumido: un `imePadding()` de una
            // pantalla solo añade lo que el teclado sobrepasa a la barra inferior, sin relleno doble (I1).
            CuentasNavHost(nav, Modifier.padding(relleno).consumeWindowInsets(relleno))
        }
    }
}
