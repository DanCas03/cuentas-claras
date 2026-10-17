package com.cuentasclaras.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.cuentasclaras.app.ui.theme.PALETAS_OSCURAS

/** Una forma de ícono de la app elegible en Apariencia (spec 2026-10-17 §7). `id` es estable: se guarda en DataStore. */
data class IconoApp(val id: String, val nombre: String)

/** Catálogo cerrado. Las formas viven en `scripts/generar-iconos-app.py`, que genera los drawables y los alias. */
val ICONOS_APP: List<IconoApp> = listOf(
    IconoApp("check", "Visto"),
    IconoApp("moneda", "Monedas"),
    IconoApp("libreta", "Libreta"),
)

/** El ícono con ese id, o «Visto» si el id no existe. */
fun iconoPorId(id: String?): IconoApp = ICONOS_APP.firstOrNull { it.id == id } ?: ICONOS_APP.first { it.id == ID_ICONO_POR_DEFECTO }

/** Paquete de clases del manifest (no el `applicationId`: el sabor `verif` añade sufijo al id, no a las clases). */
private const val PAQUETE_CLASES = "com.cuentasclaras.app"

/**
 * Cambia el ícono del lanzador habilitando el `activity-alias` de la forma × paleta oscura y deshabilitando los
 * demás (`DONT_KILL_APP`). Android puede tardar unos segundos en refrescar el lanzador.
 */
class IconoAppRepo(private val context: Context) {

    fun aliasDe(idIcono: String, idPaletaOscura: String): ComponentName =
        ComponentName(context.packageName, "$PAQUETE_CLASES.Lanzador_${idIcono}_$idPaletaOscura")

    fun aplicar(idIcono: String, idPaletaOscura: String) {
        val pm = context.packageManager
        val objetivo = aliasDe(iconoPorId(idIcono).id, idPaletaOscura)
        for (icono in ICONOS_APP) {
            for (paleta in PALETAS_OSCURAS) {
                val alias = aliasDe(icono.id, paleta.id)
                val estado = if (alias == objetivo) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                if (pm.getComponentEnabledSetting(alias) != estado) {
                    pm.setComponentEnabledSetting(alias, estado, PackageManager.DONT_KILL_APP)
                }
            }
        }
    }
}
