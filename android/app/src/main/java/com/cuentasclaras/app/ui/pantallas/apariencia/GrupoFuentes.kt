package com.cuentasclaras.app.ui.pantallas.apariencia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.CuentasTemaConPaleta
import com.cuentasclaras.app.ui.theme.FUENTES
import com.cuentasclaras.app.ui.theme.Paleta

/** Lado de la muestra «Aa» de una fuente. */
private val LADO_MUESTRA_FUENTE = 44.dp

/** Cifra de ejemplo para ver los dígitos tabulares de cada fuente. */
private const val CIFRA_EJEMPLO = "Bs 13.517,75 · \$84.20"

/**
 * Grupo «Fuente» (spec 2026-10-17 §5): una fila por fuente, con el nombre pintado en esa misma fuente y una cifra
 * de ejemplo en su `cifra`. La paleta y el diseño actuales se conservan; solo cambia la tipografía de la muestra.
 */
@Composable
fun GrupoFuentes(paleta: Paleta, idElegida: String, onElegir: (String) -> Unit) {
    val diseno = CuentasTema.diseno
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs)) {
        FUENTES.forEach { fuente ->
            val elegida = fuente.id == idElegida
            CuentasTemaConPaleta(paleta, fuente = fuente, diseno = diseno) {
                FilaSeleccionable(
                    elegida = elegida,
                    onClick = { onElegir(fuente.id) },
                    muestra = {
                        Box(Modifier.size(LADO_MUESTRA_FUENTE), contentAlignment = Alignment.Center) {
                            Text("Aa", style = CuentasTema.tipografia.titulo, color = CuentasTema.colores.texto)
                        }
                    },
                ) {
                    Column {
                        Text(
                            fuente.nombre,
                            style = CuentasTema.tipografia.subtitulo.copy(fontWeight = if (elegida) FontWeight.W700 else FontWeight.W600),
                            color = CuentasTema.colores.texto,
                        )
                        Text(CIFRA_EJEMPLO, style = CuentasTema.tipografia.cifra, color = CuentasTema.colores.textoSecundario)
                    }
                }
            }
        }
    }
}
