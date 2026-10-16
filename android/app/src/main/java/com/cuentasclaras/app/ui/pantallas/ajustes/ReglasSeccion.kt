package com.cuentasclaras.app.ui.pantallas.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.ui.componentes.Tarjeta
import com.cuentasclaras.app.ui.componentes.Vacio
import com.cuentasclaras.app.ui.theme.CuentasTema

/** Reglas aprendidas: «patrón» → categoría, con «eliminar». Se crean al categorizar en los imports. */
@Composable
fun ReglasSeccion(
    reglas: List<Rule>,
    categorias: List<Category>,
    onEliminar: (String) -> Unit,
) {
    val e = CuentasTema.espacios
    fun nombre(id: String): String = categorias.firstOrNull { it.id == id }?.name ?: "?"

    Tarjeta(titulo = "Reglas aprendidas") {
        if (reglas.isEmpty()) {
            Vacio("🪄", "Aún no hay reglas. Se crean al categorizar en los imports.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(e.s)) {
                reglas.forEach { regla ->
                    FilaConAccion(
                        accion = "eliminar",
                        descripcionAccion = "Eliminar regla «${regla.pattern}»",
                        onAccion = { onEliminar(regla.id) },
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
