package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Reglas aprendidas: «patrón» → categoría, con «eliminar». Se crean al categorizar en los imports. */
@Composable
fun ReglasSeccion(
    reglas: List<Rule>,
    categorias: List<Category>,
    guardando: Boolean,
    onEliminar: (String) -> Unit,
) {
    val e = CuentasTema.espacios
    fun nombre(id: String): String = categorias.firstOrNull { it.id == id }?.name ?: "?"

    // Sin Tarjeta propia: la pinta la SeccionPlegable de Ajustes (spec 2026-10-17 §3).
    Column {
        if (reglas.isEmpty()) {
            Vacio("🪄", "Aún no hay reglas. Se crean al categorizar en los imports.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                reglas.forEach { regla ->
                    FilaConAccion(
                        accion = "eliminar",
                        descripcionAccion = "Eliminar regla «${regla.pattern}»",
                        onAccion = { onEliminar(regla.id) },
                        habilitada = !guardando,
                    ) {
                        Text(
                            "«${regla.pattern}» → ${nombre(regla.categoryId)}",
                            style = CuentasTema.tipografia.cuerpo,
                            color = CuentasTema.colores.texto,
                        )
                    }
                }
            }
        }
    }
}
