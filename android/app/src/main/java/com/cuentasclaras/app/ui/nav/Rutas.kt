package com.cuentasclaras.app.ui.nav

import android.net.Uri

/** Rutas de la app. Las constantes de patrón registran destinos; los constructores arman la ruta a navegar. */
object Rutas {
    const val INICIO = "inicio"
    const val NUEVO = "nuevo?tipo={tipo}&monto={monto}&cuenta={cuenta}&nota={nota}&eventId={eventId}&eventDate={eventDate}&capturaId={capturaId}"
    const val MOVIMIENTOS = "movimientos?categoria={categoria}&mes={mes}&cuenta={cuenta}"
    const val IMPORTAR = "importar"
    const val AJUSTES = "ajustes"
    const val DEUDAS = "deudas"
    const val CALENDARIO = "calendario?dia={dia}&nuevo={nuevo}"
    const val PERMISOS = "permisos"
    const val APARIENCIA = "apariencia"

    val ARGS_NUEVO = listOf("tipo", "monto", "cuenta", "nota", "eventId", "eventDate", "capturaId")
    val ARGS_MOVIMIENTOS = listOf("categoria", "mes", "cuenta")
    val ARGS_CALENDARIO = listOf("dia", "nuevo")

    private fun conParametros(base: String, pares: List<Pair<String, String?>>): String {
        val q = pares.filter { it.second != null }.joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
        return if (q.isEmpty()) base else "$base?$q"
    }

    fun nuevo(
        tipo: String = "gasto", monto: Long? = null, cuenta: String? = null, nota: String? = null,
        eventId: String? = null, eventDate: String? = null, capturaId: String? = null,
    ): String = conParametros(
        "nuevo",
        listOf(
            "tipo" to tipo, "monto" to monto?.toString(), "cuenta" to cuenta, "nota" to nota,
            "eventId" to eventId, "eventDate" to eventDate, "capturaId" to capturaId,
        ),
    )

    fun movimientos(categoria: String? = null, mes: String? = null, cuenta: String? = null): String =
        conParametros("movimientos", listOf("categoria" to categoria, "mes" to mes, "cuenta" to cuenta))

    /** `nuevo = true` abre el editor de evento al entrar (como `#/calendario?nuevo=1` de la web). */
    fun calendario(dia: String? = null, nuevo: Boolean = false): String =
        conParametros("calendario", listOf("dia" to dia, "nuevo" to if (nuevo) "1" else null))
}
