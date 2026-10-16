package com.cuentasclaras.app.ui.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cuentasclaras.app.ui.theme.CuentasTema

/**
 * Confirmación en `AlertDialog` (DESIGN.md §4): `superficie`, título en `subtitulo`, texto en `textoSecundario`,
 * forma `tarjeta`. `peligro` pinta la confirmación con `BotonPeligro`; si no, con `BotonPrincipal`.
 * `habilitado = false` (p. ej. `!guardando`) bloquea ambos botones mientras hay una escritura en curso.
 */
@Composable
fun DialogoConfirmacion(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    textoCancelar: String = "Conservar",
    peligro: Boolean = true,
    habilitado: Boolean = true,
) {
    val c = CuentasTema.colores
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo, style = CuentasTema.tipografia.subtitulo) },
        text = {
            Column {
                Text(texto, style = CuentasTema.tipografia.cuerpo, color = c.textoSecundario)
                // D2: el diálogo es otra ventana y atenúa el aviso raíz; mientras está abierto, lo pinta él.
                AvisosDeCapa()
            }
        },
        confirmButton = {
            if (peligro) {
                BotonPeligro(textoConfirmar, onClick = onConfirmar, enabled = habilitado)
            } else {
                BotonPrincipal(textoConfirmar, onClick = onConfirmar, enabled = habilitado)
            }
        },
        dismissButton = { BotonSecundario(textoCancelar, onClick = onCancelar, enabled = habilitado) },
        containerColor = c.superficie,
        titleContentColor = c.texto,
        textContentColor = c.textoSecundario,
        shape = CuentasTema.formas.tarjeta,
    )
}
