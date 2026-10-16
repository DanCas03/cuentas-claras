package com.cuentasclaras.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * V25: la primera pantalla avisa con esta función de que ya pinta su contenido real. `MainActivity` retiene la
 * ventana de arranque (`@color/fondo_arranque`) hasta entonces, así el arranque en frío pasa de esa ventana a Inicio
 * completo, sin el frame intermedio con la barra inferior y el contenido vacío. Por defecto no hace nada (vistas
 * previas, pruebas).
 */
val LocalPrimerContenidoListo = staticCompositionLocalOf<() -> Unit> { {} }
