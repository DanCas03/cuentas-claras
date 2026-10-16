package com.cuentasclaras.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.cuentasclaras.app.data.ID_PALETA_CLARA_POR_DEFECTO
import com.cuentasclaras.app.data.ID_PALETA_OSCURA_POR_DEFECTO

/**
 * Tokens de color. Todas las paletas usan EXACTAMENTE estos nombres; las pantallas solo leen
 * `CuentasTema.colores` y nunca un hex suelto (DESIGN.md §2 y §8).
 * `borde` es a la vez el trazo decorativo y el relleno de controles (campos, chips, botón secundario).
 */
data class Colores(
    val fondo: Color,
    val superficie: Color,
    val superficieElevada: Color,
    val borde: Color,
    val primario: Color,
    val sobrePrimario: Color,
    val ingreso: Color,
    val gasto: Color,
    val aviso: Color,
    val error: Color,
    val texto: Color,
    val textoSecundario: Color,
)

/** Una paleta elegible en Ajustes (E20/E22). `id` es estable: se guarda en DataStore. */
data class Paleta(
    val id: String,
    val nombre: String,
    val esOscura: Boolean,
    val colores: Colores,
)

/** Texto sobre `error` (botón de peligro): el `fondo` en paletas oscuras, la superficie blanca en las claras. */
fun Colores.sobreError(): Color = if (fondo.luminance() < 0.5f) fondo else superficieElevada

private fun paleta(
    id: String,
    nombre: String,
    esOscura: Boolean,
    fondo: Long,
    superficie: Long,
    superficieElevada: Long,
    borde: Long,
    primario: Long,
    sobrePrimario: Long,
    ingreso: Long,
    gasto: Long,
    aviso: Long,
    error: Long,
    texto: Long,
    textoSecundario: Long,
) = Paleta(
    id = id,
    nombre = nombre,
    esOscura = esOscura,
    colores = Colores(
        fondo = Color(fondo),
        superficie = Color(superficie),
        superficieElevada = Color(superficieElevada),
        borde = Color(borde),
        primario = Color(primario),
        sobrePrimario = Color(sobrePrimario),
        ingreso = Color(ingreso),
        gasto = Color(gasto),
        aviso = Color(aviso),
        error = Color(error),
        texto = Color(texto),
        textoSecundario = Color(textoSecundario),
    ),
)

/**
 * Catálogo cerrado de paletas (DESIGN.md §2). Todas verificadas WCAG AA para texto sobre fondo, superficie y
 * superficie elevada; el peor ratio de cada una está anotado.
 */
val PALETAS: List<Paleta> = listOf(
    // ---------------------------------------------------------------- Oscuras
    // Peor: gasto 5,55
    paleta(
        "grafito_menta", "Grafito menta", true,
        0xFF0F110F, 0xFF181B18, 0xFF222622, 0xFF2F342E, 0xFF2DD4BF, 0xFF06251F,
        0xFF34D399, 0xFFF87171, 0xFFFBBF24, 0xFFFB7185, 0xFFEEF1EA, 0xFFA2A99D,
    ),
    // Peor: error 5,55
    paleta(
        "grafito_lima", "Grafito lima", true,
        0xFF0F110F, 0xFF181B18, 0xFF222622, 0xFF2F342E, 0xFFC5F05A, 0xFF142008,
        0xFF62C6F5, 0xFFFF8F5E, 0xFFF0C24B, 0xFFFF6B72, 0xFFEEF1EA, 0xFFA2A99D,
    ),
    // Peor: gasto 5,68 (oscura por defecto)
    paleta(
        "noche_lima", "Noche lima", true,
        0xFF0A0F1C, 0xFF121A2B, 0xFF182338, 0xFF223049, 0xFFC5F05A, 0xFF142008,
        0xFF34D399, 0xFFF87171, 0xFFFBBF24, 0xFFFB7185, 0xFFE8EDF6, 0xFF939EB3,
    ),
    // La paleta de la PWA (alto contraste). Peor: gasto 5,68
    paleta(
        "como_antes", "Como antes", true,
        0xFF0A0F1C, 0xFF121A2B, 0xFF182338, 0xFF223049, 0xFF2DD4BF, 0xFF06251F,
        0xFF34D399, 0xFFF87171, 0xFFFBBF24, 0xFFFB7185, 0xFFE8EDF6, 0xFF939EB3,
    ),
    // Peor: error 5,33
    paleta(
        "cacao", "Cacao", true,
        0xFF1B1411, 0xFF251C17, 0xFF30251F, 0xFF43352C, 0xFFFFA24C, 0xFF2B1605,
        0xFF9ED67C, 0xFFF58A92, 0xFFF3C75A, 0xFFFF6B5E, 0xFFF7EEE6, 0xFFBCAA9B,
    ),
    // Peor: error 5,73
    paleta(
        "pizarra_cobalto", "Pizarra cobalto", true,
        0xFF0E1117, 0xFF161B24, 0xFF1E2530, 0xFF2A3341, 0xFF8AB4FF, 0xFF0B1A3D,
        0xFF4ADE80, 0xFFFF8A65, 0xFFFACC15, 0xFFFB7185, 0xFFE9EDF5, 0xFF9AA4B5,
    ),
    // ---------------------------------------------------------------- Claras
    // Peor: primario 4,99
    paleta(
        "menta_claro", "Menta claro", false,
        0xFFF3F5F2, 0xFFFAFBF9, 0xFFFFFFFF, 0xFFDDE2DB, 0xFF0F766E, 0xFFFFFFFF,
        0xFF047857, 0xFFB91C1C, 0xFF92400E, 0xFFBE123C, 0xFF121512, 0xFF586058,
    ),
    // Peor: primario 4,55
    paleta(
        "lima_claro", "Lima claro", false,
        0xFFF3F5F2, 0xFFFAFBF9, 0xFFFFFFFF, 0xFFDDE2DB, 0xFF4D7C0F, 0xFFFFFFFF,
        0xFF0369A1, 0xFFC2410C, 0xFF854D0E, 0xFFBE123C, 0xFF121512, 0xFF586058,
    ),
    // Peor: ingreso 4,97 (clara por defecto)
    paleta(
        "noche_claro", "Noche claro", false,
        0xFFF1F4F9, 0xFFF8FAFD, 0xFFFFFFFF, 0xFFD9E0EC, 0xFF18233A, 0xFFC5F05A,
        0xFF047857, 0xFFB91C1C, 0xFF92400E, 0xFFBE123C, 0xFF0A0F1C, 0xFF55607A,
    ),
    // Peor: gasto 5,37
    paleta(
        "libreta", "Libreta", false,
        0xFFF3EFE6, 0xFFFBF8F2, 0xFFFFFFFF, 0xFFE2DACB, 0xFF24357A, 0xFFFBF8F2,
        0xFF1C6E47, 0xFFB0362A, 0xFF855400, 0xFFA1203B, 0xFF1D1B16, 0xFF625C50,
    ),
    // Peor: ingreso 4,99
    paleta(
        "cobalto_claro", "Cobalto claro", false,
        0xFFF4F4F5, 0xFFFAFAFA, 0xFFFFFFFF, 0xFFE4E4E7, 0xFF1D4ED8, 0xFFFFFFFF,
        0xFF047857, 0xFFB91C1C, 0xFF92400E, 0xFFBE123C, 0xFF18181B, 0xFF52525B,
    ),
)

val PALETAS_OSCURAS: List<Paleta> = PALETAS.filter { it.esOscura }
val PALETAS_CLARAS: List<Paleta> = PALETAS.filter { !it.esOscura }

/** Paleta por id dentro del grupo pedido; si el id no existe (o es del otro grupo), la de por defecto. */
fun paletaPorId(id: String?, oscura: Boolean): Paleta {
    val grupo = if (oscura) PALETAS_OSCURAS else PALETAS_CLARAS
    val porDefecto = if (oscura) ID_PALETA_OSCURA_POR_DEFECTO else ID_PALETA_CLARA_POR_DEFECTO
    return grupo.firstOrNull { it.id == id }
        ?: grupo.firstOrNull { it.id == porDefecto }
        ?: grupo.first()
}
