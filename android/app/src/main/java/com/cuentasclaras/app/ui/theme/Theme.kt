package com.cuentasclaras.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.cuentasclaras.app.CuentasApp
import com.cuentasclaras.app.data.Apariencia
import com.cuentasclaras.app.data.ModoTema
import kotlinx.coroutines.flow.flowOf

private val LocalColores = staticCompositionLocalOf { paletaPorId(null, oscura = true).colores }
private val LocalTipografia = staticCompositionLocalOf { TipografiaSistema }
private val LocalFormas = staticCompositionLocalOf { FormasCuentas }
private val LocalEspacios = staticCompositionLocalOf { EspaciosCuentas }

/**
 * Tema de la app (E20: ya no es único). Lee la apariencia guardada (modo + paleta oscura + paleta clara) del
 * `AppContainer` y, con el modo del sistema, decide la paleta activa. Material 3 recibe un esquema derivado de
 * esa paleta para que sus componentes (TextField, DatePicker, ModalBottomSheet, Snackbar) hereden la identidad.
 * Mientras DataStore no responde (primer frame) solo pinta el fondo, para no destellar otra paleta.
 */
@Composable
fun CuentasTema(content: @Composable () -> Unit) {
    val contexto = LocalContext.current
    val repo = remember(contexto) { (contexto.applicationContext as? CuentasApp)?.container?.apariencia }
    val flujo = remember(repo) { repo?.apariencia ?: flowOf(Apariencia()) }
    val apariencia by flujo.collectAsState(initial = if (repo == null) Apariencia() else null)
    val oscuro = when (apariencia?.modo ?: ModoTema.SISTEMA) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.OSCURO -> true
    }
    val paleta = if (oscuro) {
        paletaPorId(apariencia?.idPaletaOscura, oscura = true)
    } else {
        paletaPorId(apariencia?.idPaletaClara, oscura = false)
    }
    BarrasDelSistema(oscuro)
    if (apariencia == null) {
        Box(Modifier.fillMaxSize().background(paleta.colores.fondo))
    } else {
        CuentasTemaConPaleta(paleta, content)
    }
}

/** Aplica una paleta concreta (usado por `CuentasTema` y por las vistas previas de paletas en Ajustes). */
@Composable
fun CuentasTemaConPaleta(paleta: Paleta, content: @Composable () -> Unit) {
    val assets = LocalContext.current.assets
    val tipografia = remember(assets) { tipografiaCuentas(assets) }
    CompositionLocalProvider(
        LocalColores provides paleta.colores,
        LocalTipografia provides tipografia,
        LocalFormas provides FormasCuentas,
        LocalEspacios provides EspaciosCuentas,
    ) {
        MaterialTheme(
            colorScheme = esquemaMaterial(paleta),
            typography = tipografia.comoMaterial(),
            shapes = formasMaterial(),
        ) {
            CompositionLocalProvider(LocalContentColor provides paleta.colores.texto, content = content)
        }
    }
}

object CuentasTema {
    val colores: Colores @Composable @ReadOnlyComposable get() = LocalColores.current
    val tipografia: Tipografia @Composable @ReadOnlyComposable get() = LocalTipografia.current
    val formas: Formas @Composable @ReadOnlyComposable get() = LocalFormas.current
    val espacios: Espacios @Composable @ReadOnlyComposable get() = LocalEspacios.current
}

/** Íconos de la barra de estado y de navegación acordes al modo activo (barras transparentes, edge-to-edge). */
@Composable
private fun BarrasDelSistema(oscuro: Boolean) {
    val actividad = LocalActivity.current as? ComponentActivity
    DisposableEffect(actividad, oscuro) {
        val transparente = android.graphics.Color.TRANSPARENT
        val estilo = if (oscuro) {
            SystemBarStyle.dark(transparente)
        } else {
            SystemBarStyle.light(transparente, transparente)
        }
        actividad?.enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
        onDispose { }
    }
}

private fun esquemaMaterial(paleta: Paleta): ColorScheme {
    val c = paleta.colores
    val base = if (paleta.esOscura) darkColorScheme() else lightColorScheme()
    val contenedorPrimario = lerp(c.superficieElevada, c.primario, 0.22f)
    return base.copy(
        primary = c.primario,
        onPrimary = c.sobrePrimario,
        primaryContainer = contenedorPrimario,
        onPrimaryContainer = c.texto,
        inversePrimary = c.primario,
        secondary = c.primario,
        onSecondary = c.sobrePrimario,
        secondaryContainer = contenedorPrimario,
        onSecondaryContainer = c.texto,
        background = c.fondo,
        onBackground = c.texto,
        surface = c.superficie,
        onSurface = c.texto,
        surfaceVariant = c.superficieElevada,
        onSurfaceVariant = c.textoSecundario,
        surfaceTint = Color.Transparent,
        inverseSurface = c.texto,
        inverseOnSurface = c.fondo,
        error = c.error,
        onError = c.sobreError(),
        errorContainer = lerp(c.superficieElevada, c.error, 0.18f),
        onErrorContainer = c.texto,
        outline = c.textoSecundario,
        outlineVariant = c.borde,
        surfaceBright = c.superficieElevada,
        surfaceDim = c.fondo,
        surfaceContainerLowest = c.fondo,
        surfaceContainerLow = c.superficie,
        surfaceContainer = c.superficie,
        surfaceContainerHigh = c.superficieElevada,
        surfaceContainerHighest = c.superficieElevada,
    )
}
