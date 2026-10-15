package com.cuentasclaras.app.domain

import java.text.Collator
import java.util.Locale

private val COLLATOR: Collator = Collator.getInstance(Locale.forLanguageTag("es"))

/** Equivalente de `a.localeCompare(b)` de la web para ordenar títulos y nombres. */
fun compararTexto(a: String, b: String): Int = COLLATOR.compare(a, b)
