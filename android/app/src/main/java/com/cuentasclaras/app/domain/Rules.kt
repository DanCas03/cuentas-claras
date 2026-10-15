package com.cuentasclaras.app.domain

/** Primera regla cuyo patrón (substring, case-insensitive) aparece en la descripción. */
fun matchCategory(descripcion: String, rules: List<Rule>): Rule? {
    val d = descripcion.lowercase()
    return rules.firstOrNull { r ->
        val p = r.pattern.trim().lowercase()
        p.isNotEmpty() && d.contains(p)
    }
}

fun buildRule(pattern: String, categoryId: String, autoCreated: Boolean = true): Rule {
    val p = pattern.trim()
    require(p.isNotEmpty()) { "Patrón vacío" }
    return Rule(nuevoId(), p, categoryId, autoCreated)
}
