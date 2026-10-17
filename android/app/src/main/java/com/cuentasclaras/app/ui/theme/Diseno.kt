package com.cuentasclaras.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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

private val FORMAS_TINTA = Formas(
    tarjeta = RoundedCornerShape(8.dp),
    control = RoundedCornerShape(8.dp),
    pildora = RoundedCornerShape(8.dp),
    radioTarjeta = 8.dp,
    biselTarjeta = 0.dp,
    hoja = 16.dp,
)

/** Hecho sin invocar skills de diseño (Daniel quiso comparar). Plano y editorial. */
val DISENO_TINTA = Diseno(
    id = "tinta",
    nombre = "Tinta",
    descripcion = "Plano y editorial: esquinas de 8 dp, filetes finos, barra con subrayado.",
    formas = FORMAS_TINTA,
    tarjeta = EstiloTarjeta.PLANA,
    barra = EstiloBarra.SUBRAYADO,
    botonMas = FormaMas.CUADRADO,
    fondo = EstiloFondo.LISO,
    movimiento = NivelMovimiento.CONTENIDO,
    escalaPresion = 0.98f,
)

private val FORMAS_ISLA = Formas(
    tarjeta = RoundedCornerShape(24.dp),
    control = RoundedCornerShape(14.dp),
    pildora = RoundedCornerShape(percent = 50),
    radioTarjeta = 24.dp,
    biselTarjeta = 0.dp,
    hoja = 28.dp,
)

/**
 * Hecho tras invocar las skills `high-end-visual-design` y `ui-ux-pro-max` (ver
 * `docs/research/2026-10-17-diseno-con-skills.md`): barra flotante tipo isla, contornos translúcidos, radios amplios.
 */
val DISENO_ISLA = Diseno(
    id = "isla",
    nombre = "Isla",
    descripcion = "Barra flotante despegada del borde, tarjetas con contorno translúcido y radios amplios.",
    formas = FORMAS_ISLA,
    tarjeta = EstiloTarjeta.CONTORNO,
    barra = EstiloBarra.FLOTANTE,
    botonMas = FormaMas.GOTA,
    fondo = EstiloFondo.LISO,
    movimiento = NivelMovimiento.CONTENIDO,
    escalaPresion = 0.96f,
)

/** Catálogo cerrado de diseños. */
val DISENOS: List<Diseno> = listOf(DISENO_LIBRETA, DISENO_TINTA, DISENO_ISLA)

/** El diseño con ese id, o Libreta si el id no existe (versión vieja, respaldo). */
fun disenoPorId(id: String?): Diseno = DISENOS.firstOrNull { it.id == id } ?: DISENOS.first { it.id == ID_DISENO_POR_DEFECTO }
