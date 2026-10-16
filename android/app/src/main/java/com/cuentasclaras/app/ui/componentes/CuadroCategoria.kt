package com.cuentasclaras.app.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuentasclaras.app.R
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Emoji de la categoría dentro del cuadro. */
private val EMOJI_CUADRO = 22.sp

/** Ícono vectorial dentro del cuadro (DESIGN.md §4: 24 dp). */
private val ICONO_CUADRO = 24.dp

/**
 * Tinte del cuadro para una categoría: su color (dato del usuario) al `medidas.alfaTinteCategoria` (18 %); sin
 * categoría, `borde`. [respaldo] es el color si el hex de la categoría no se puede leer.
 */
@Composable
fun fondoDeCategoria(hex: String?, respaldo: Color = CuentasTema.colores.borde): Color =
    if (hex == null) {
        CuentasTema.colores.borde
    } else {
        colorDeCategoria(hex, respaldo).copy(alpha = CuentasTema.medidas.alfaTinteCategoria)
    }

/**
 * Cuadro del ícono de una fila de lista (Movimientos, Esta semana, Calendario, «Todos los eventos», categorías de
 * Inicio): `medidas.cuadroFila` (44 dp), radio `control`, fondo `fondo` (ver [fondoDeCategoria]). Dentro, el emoji de
 * la categoría (dato del usuario) o, si no hay, el vector [vector] de 24 dp en [tintaVector] (DESIGN.md §8: nunca
 * un emoji de interfaz, como el calendario de respaldo de la web). `marcada` (fila seleccionada en Movimientos):
 * `primario` con un visto en `sobrePrimario`. Es decorativo: la fila que lo contiene se lee con su descripción.
 */
@Composable
fun CuadroCategoria(
    emoji: String?,
    @DrawableRes vector: Int,
    fondo: Color,
    modifier: Modifier = Modifier,
    tintaVector: Color = CuentasTema.colores.texto,
    marcada: Boolean = false,
) {
    val c = CuentasTema.colores
    Box(
        modifier
            .size(CuentasTema.medidas.cuadroFila)
            .clip(CuentasTema.formas.control)
            .background(if (marcada) c.primario else fondo)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        when {
            marcada -> Icon(painterResource(R.drawable.ic_ok), contentDescription = null, tint = c.sobrePrimario, modifier = Modifier.size(ICONO_CUADRO))
            emoji != null -> Text(emoji, fontSize = EMOJI_CUADRO)
            else -> Icon(painterResource(vector), contentDescription = null, tint = tintaVector, modifier = Modifier.size(ICONO_CUADRO))
        }
    }
}

@Preview
@Composable
private fun VistaCuadroCategoria() {
    VistaPreviaComponente {
        Row {
            CuadroCategoria("🛒", R.drawable.ic_sin_categoria, fondoDeCategoria("#34D399"))
            CuadroCategoria(null, R.drawable.ic_calendario, fondoDeCategoria(null), tintaVector = CuentasTema.colores.textoSecundario)
            CuadroCategoria("🛒", R.drawable.ic_sin_categoria, fondoDeCategoria("#34D399"), marcada = true)
        }
    }
}
