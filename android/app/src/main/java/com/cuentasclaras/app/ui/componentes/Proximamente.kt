package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Destino de una fase que aún no existe: estado vacío centrado con la frase de cuándo llega. */
@Composable
fun Proximamente(texto: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Vacio("🛠️", texto)
    }
}
