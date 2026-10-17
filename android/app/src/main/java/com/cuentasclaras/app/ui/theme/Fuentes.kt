package com.cuentasclaras.app.ui.theme

import com.cuentasclaras.app.data.ID_FUENTE_POR_DEFECTO

/**
 * Una fuente elegible en Apariencia (spec 2026-10-17 §5). Variable y OFL; `archivo` es relativo a `assets/`.
 * `opszTexto` / `opszDisplay` en `null` = la fuente no tiene el eje óptico y se construye solo con el peso.
 */
data class Fuente(
    val id: String,
    val nombre: String,
    val archivo: String,
    val opszTexto: Float?,
    val opszDisplay: Float?,
    val serif: Boolean = false,
)

/** Catálogo cerrado. `id` es estable: se guarda en DataStore. Licencias en `assets/fuentes/OFL*.txt`. */
val FUENTES: List<Fuente> = listOf(
    Fuente("bricolage", "Bricolage Grotesque", "fuentes/bricolage_grotesque_variable.ttf", 14f, 40f),
    Fuente("inter", "Inter", "fuentes/inter_variable.ttf", 14f, 32f),
    Fuente("manrope", "Manrope", "fuentes/manrope_variable.ttf", null, null),
    Fuente("space_grotesk", "Space Grotesk", "fuentes/space_grotesk_variable.ttf", null, null),
    Fuente("dm_sans", "DM Sans", "fuentes/dm_sans_variable.ttf", 14f, 40f),
    Fuente("fraunces", "Fraunces", "fuentes/fraunces_variable.ttf", 14f, 72f, serif = true),
)

/** La fuente con ese id, o Bricolage si el id no existe (versión vieja, respaldo). */
fun fuentePorId(id: String?): Fuente = FUENTES.firstOrNull { it.id == id } ?: FUENTES.first { it.id == ID_FUENTE_POR_DEFECTO }
