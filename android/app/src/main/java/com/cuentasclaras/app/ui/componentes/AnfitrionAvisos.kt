package com.cuentasclaras.app.ui.componentes

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.cuentasclaras.app.ui.Avisos

/**
 * Dónde se pintan los avisos de [Avisos] (D15). Hay UNA sola cola (`estado`, alimentada por un único colector de
 * `Avisos.mensajes`), pero el aviso en curso lo pinta solo la superficie de más arriba.
 *
 * Por qué: `ModalBottomSheet` y `AlertDialog` abren su propia ventana encima de la de la actividad. El
 * `SnackbarHost` raíz se dibuja en la ventana de la actividad y queda tapado mientras hay una hoja o un diálogo
 * abiertos (D2). Cada hoja y cada diálogo se registran como capa mientras están en composición; la capa más
 * reciente (la de más arriba) pinta el aviso con el MISMO `SnackbarHostState`, y la raíz solo lo pinta cuando no
 * hay ninguna capa. Así el aviso se ve una sola vez y, si la hoja se cierra con un aviso en pantalla, la raíz lo
 * sigue pintando (el `SnackbarHostState` conserva el aviso en curso y su temporizador vuelve a empezar).
 */
@Stable
class AnfitrionAvisos internal constructor(internal val estado: SnackbarHostState) {
    private val capas = mutableStateListOf<Any>()

    internal fun abrir(capa: Any) {
        capas.add(capa)
    }

    internal fun cerrar(capa: Any) {
        capas.remove(capa)
    }

    /** La capa más reciente pinta los avisos; sin capas, los pinta la raíz. */
    internal fun pinta(capa: Any?): Boolean = capas.lastOrNull() === capa
}

/** Anfitrión de avisos de la app; null fuera de `RaizApp` (vistas previas): las capas no pintan nada. */
val LocalAvisos = staticCompositionLocalOf<AnfitrionAvisos?> { null }

/** Crea el anfitrión de la raíz y arranca el ÚNICO colector de `Avisos.mensajes`. */
@Composable
fun rememberAnfitrionAvisos(): AnfitrionAvisos {
    val anfitrion = remember { AnfitrionAvisos(SnackbarHostState()) }
    LaunchedEffect(anfitrion) { Avisos.mensajes.collect { anfitrion.estado.showSnackbar(it) } }
    return anfitrion
}

/** `SnackbarHost` de la raíz (el `snackbarHost` del Scaffold): pinta el aviso solo si no hay hojas ni diálogos. */
@Composable
fun AvisosRaiz(anfitrion: AnfitrionAvisos, modifier: Modifier = Modifier) {
    if (anfitrion.pinta(null)) SnackbarHost(anfitrion.estado, modifier)
}

/**
 * `SnackbarHost` de una hoja o un diálogo: registra la capa mientras está en composición y pinta el aviso en curso
 * si es la de más arriba. Va DENTRO del contenido de la hoja o del diálogo (su ventana).
 */
@Composable
fun AvisosDeCapa(modifier: Modifier = Modifier) {
    val anfitrion = LocalAvisos.current ?: return
    val capa = remember { Any() }
    DisposableEffect(anfitrion, capa) {
        anfitrion.abrir(capa)
        onDispose { anfitrion.cerrar(capa) }
    }
    if (anfitrion.pinta(capa)) SnackbarHost(anfitrion.estado, modifier)
}
