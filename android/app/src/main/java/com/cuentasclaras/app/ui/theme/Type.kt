package com.cuentasclaras.app.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Estilos de texto de la app. `cifra` y `cifraGrande` llevan dígitos tabulares (`tnum`). */
data class Tipografia(
    val titulo: TextStyle,
    val subtitulo: TextStyle,
    val cuerpo: TextStyle,
    val etiqueta: TextStyle,
    val cifraGrande: TextStyle,
    val cifra: TextStyle,
)

/** Bricolage Grotesque variable (OFL 1.1, `assets/fuentes/OFL.txt`). */
private const val RUTA_BRICOLAGE = "fuentes/bricolage_grotesque_variable.ttf"

/** Tamaño óptico para cuerpo, etiquetas y cifras pequeñas. */
private const val OPSZ_TEXTO = 14f

/** Tamaño óptico para títulos y la cifra grande. */
private const val OPSZ_DISPLAY = 40f

/**
 * E23: la fuente se carga desde `assets/` porque `Font(R.font…, variationSettings)` ignora la variación en el
 * Xiaomi de Daniel (Android 16) y pinta siempre la instancia por defecto (wght 800, opsz 96).
 */
private fun familiaBricolage(assets: AssetManager, opsz: Float, vararg pesos: Int): FontFamily = FontFamily(
    pesos.map { peso ->
        Font(
            path = RUTA_BRICOLAGE,
            assetManager = assets,
            weight = FontWeight(peso),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(peso),
                FontVariation.Setting("opsz", opsz),
            ),
        )
    }
)

private fun construirTipografia(texto: FontFamily, display: FontFamily) = Tipografia(
    titulo = TextStyle(fontFamily = display, fontWeight = FontWeight.W700, fontSize = 24.sp, lineHeight = 30.sp),
    subtitulo = TextStyle(fontFamily = texto, fontWeight = FontWeight.W600, fontSize = 17.sp, lineHeight = 22.sp),
    cuerpo = TextStyle(fontFamily = texto, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 21.sp),
    etiqueta = TextStyle(fontFamily = texto, fontWeight = FontWeight.W500, fontSize = 13.sp, lineHeight = 18.sp),
    cifraGrande = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.W700,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
    ),
    cifra = TextStyle(
        fontFamily = texto,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum",
    ),
)

/** Tipografía real de la app (Bricolage Grotesque desde `assets/`). Se construye una vez en `CuentasTema`. */
fun tipografiaCuentas(assets: AssetManager): Tipografia = construirTipografia(
    texto = familiaBricolage(assets, OPSZ_TEXTO, 400, 500, 600, 700),
    display = familiaBricolage(assets, OPSZ_DISPLAY, 500, 600, 700),
)

/** Respaldo con la fuente del sistema, solo para el valor inicial del `CompositionLocal`. */
internal val TipografiaSistema: Tipografia = construirTipografia(FontFamily.Default, FontFamily.Default)

/**
 * Mapea los estilos propios a los roles de Material 3 para que sus componentes (TextField, DatePicker,
 * ModalBottomSheet, Snackbar) hereden la fuente: `titulo → headlineSmall`, `subtitulo → titleMedium`,
 * `cuerpo → bodyLarge/bodyMedium`, `etiqueta → labelLarge`; el resto conserva su escala de Material con la
 * familia de texto (o la de display en los roles grandes).
 */
fun Tipografia.comoMaterial(): Typography {
    val base = Typography()
    val texto = cuerpo.fontFamily
    val display = titulo.fontFamily
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display),
        headlineSmall = titulo,
        titleLarge = base.titleLarge.copy(fontFamily = texto, fontWeight = FontWeight.W600),
        titleMedium = subtitulo,
        titleSmall = base.titleSmall.copy(fontFamily = texto),
        bodyLarge = cuerpo,
        bodyMedium = cuerpo,
        bodySmall = base.bodySmall.copy(fontFamily = texto),
        labelLarge = etiqueta,
        labelMedium = base.labelMedium.copy(fontFamily = texto),
        labelSmall = base.labelSmall.copy(fontFamily = texto),
    )
}
