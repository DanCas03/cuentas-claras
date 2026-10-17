package com.cuentasclaras.app.ui.pantallas.apariencia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuentasclaras.app.ui.theme.CuentasTema
import com.cuentasclaras.app.ui.theme.CuentasTemaConPaleta
import com.cuentasclaras.app.ui.theme.DISENOS
import com.cuentasclaras.app.ui.theme.Diseno
import com.cuentasclaras.app.ui.theme.EstiloBarra
import com.cuentasclaras.app.ui.theme.EstiloTarjeta
import com.cuentasclaras.app.ui.theme.FormaMas
import com.cuentasclaras.app.ui.theme.Paleta
import com.cuentasclaras.app.ui.theme.fuentePorId

/** Medidas de la miniatura de un diseño. */
private val ANCHO_MINIATURA = 72.dp
private val ALTO_MINIATURA = 56.dp
private val ALTO_MINI_TARJETA = 26.dp
private val ALTO_MINI_BARRA = 14.dp
private val PUNTO_MINI = 5.dp
private val MAS_MINI = 10.dp
private val RADIO_MINI = 6.dp

/**
 * Grupo «Diseño» (spec 2026-10-17 §6): una fila por diseño con miniatura (mini tarjeta + mini barra pintadas con
 * los tokens de ese diseño), nombre, descripción de una línea y visto.
 */
@Composable
fun GrupoDisenos(paleta: Paleta, idFuente: String, idElegido: String, onElegir: (String) -> Unit) {
    val fuente = fuentePorId(idFuente)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CuentasTema.espacios.xs)) {
        DISENOS.forEach { diseno ->
            val elegido = diseno.id == idElegido
            FilaSeleccionable(
                elegida = elegido,
                onClick = { onElegir(diseno.id) },
                muestra = { CuentasTemaConPaleta(paleta, fuente = fuente, diseno = diseno) { Miniatura(diseno) } },
            ) {
                Column {
                    Text(
                        diseno.nombre,
                        style = CuentasTema.tipografia.subtitulo.copy(fontWeight = if (elegido) FontWeight.W700 else FontWeight.W600),
                        color = CuentasTema.colores.texto,
                    )
                    Text(
                        diseno.descripcion,
                        style = CuentasTema.tipografia.etiqueta,
                        color = CuentasTema.colores.textoSecundario,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Mini lienzo: fondo de la paleta, una mini tarjeta con el estilo del diseño y una mini barra con su «+». */
@Composable
private fun Miniatura(diseno: Diseno) {
    val c = CuentasTema.colores
    val e = CuentasTema.espacios
    val f = CuentasTema.formas
    Column(
        Modifier
            .size(ANCHO_MINIATURA, ALTO_MINIATURA)
            .clip(f.control)
            .background(c.fondo)
            .padding(e.xs),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val formaMini = androidx.compose.foundation.shape.RoundedCornerShape(RADIO_MINI)
        val fondoTarjeta = if (diseno.tarjeta == EstiloTarjeta.BISEL) c.superficie else c.superficieElevada
        Box(Modifier.fillMaxWidth().height(ALTO_MINI_TARJETA).clip(formaMini).background(fondoTarjeta)) {
            if (diseno.tarjeta == EstiloTarjeta.BISEL) {
                Box(Modifier.padding(2.dp).fillMaxWidth().height(ALTO_MINI_TARJETA - 4.dp).clip(formaMini).background(c.superficieElevada))
            }
        }
        val flotante = diseno.barra == EstiloBarra.FLOTANTE || diseno.barra == EstiloBarra.AURORA
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (flotante) e.xs else 0.dp)
                .height(ALTO_MINI_BARRA)
                .clip(if (flotante) f.pildora else formaMini)
                .background(c.superficieElevada),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { Box(Modifier.size(PUNTO_MINI).clip(CircleShape).background(c.textoSecundario)) }
            Box(
                Modifier
                    .size(MAS_MINI)
                    .clip(if (diseno.botonMas == FormaMas.CUADRADO) androidx.compose.foundation.shape.RoundedCornerShape(2.dp) else CircleShape)
                    .background(c.primario),
            )
            repeat(2) { Box(Modifier.size(PUNTO_MINI).clip(CircleShape).background(c.textoSecundario)) }
        }
    }
}
