package com.cuentasclaras.app.ui.theme

import com.cuentasclaras.app.data.ID_DISENO_POR_DEFECTO

/** Cómo se pinta una `Tarjeta`. */
enum class EstiloTarjeta { BISEL, PLANA, CONTORNO, AURORA }

/** Cómo se pinta la barra inferior. */
enum class EstiloBarra { PILDORA, SUBRAYADO, FLOTANTE, AURORA }

/** Forma del botón «+» de la barra. */
enum class FormaMas { DISCO, CUADRADO, GOTA }

/** Fondo de la raíz de la app. */
enum class EstiloFondo { LISO, AURORA }

/** Cuánto se mueve la interfaz: contenido (DESIGN.md §5) o expresivo (resortes rebotones, duraciones ×1,5). */
enum class NivelMovimiento { CONTENIDO, EXPRESIVO }

/**
 * Un diseño elegible en Apariencia (spec 2026-10-17 §6). `id` es estable: se guarda en DataStore.
 * Solo los componentes base (`ui/componentes/`, `BarraInferior`, `RaizApp`) leen `CuentasTema.diseno`; las
 * pantallas no. Un diseño no cambia la fuente ni la paleta.
 */
data class Diseno(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val formas: Formas,
    val tarjeta: EstiloTarjeta,
    val barra: EstiloBarra,
    val botonMas: FormaMas,
    val fondo: EstiloFondo,
    val movimiento: NivelMovimiento,
    /** Escala del botón o chip al presionarlo (0,97 en Libreta). */
    val escalaPresion: Float,
)

/** El diseño de siempre (DESIGN.md §1-§6 tal cual). */
val DISENO_LIBRETA = Diseno(
    id = "libreta",
    nombre = "Libreta",
    descripcion = "Tarjetas con doble bisel y barra con píldora. El de siempre.",
    formas = FormasCuentas,
    tarjeta = EstiloTarjeta.BISEL,
    barra = EstiloBarra.PILDORA,
    botonMas = FormaMas.DISCO,
    fondo = EstiloFondo.LISO,
    movimiento = NivelMovimiento.CONTENIDO,
    escalaPresion = 0.97f,
)

/** Catálogo cerrado de diseños. */
val DISENOS: List<Diseno> = listOf(DISENO_LIBRETA)

/** El diseño con ese id, o Libreta si el id no existe (versión vieja, respaldo). */
fun disenoPorId(id: String?): Diseno = DISENOS.firstOrNull { it.id == id } ?: DISENOS.first { it.id == ID_DISENO_POR_DEFECTO }
