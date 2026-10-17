package com.cuentasclaras.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.cuentasclaras.app.ui.theme.PALETAS_OSCURAS
import com.cuentasclaras.app.ui.theme.paletaPorId

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

/** El único alias con `android:enabled="true"` en el manifest: Visto × Noche lima. */
private val ALIAS_DEL_MANIFEST = ID_ICONO_POR_DEFECTO to ID_PALETA_OSCURA_POR_DEFECTO

/**
 * Cambia el ícono del lanzador habilitando el `activity-alias` de la forma × paleta oscura y deshabilitando los
 * demás (`DONT_KILL_APP`). Se llama **al salir de la app** (`MainActivity.onStop`), no al tocar el selector:
 * deshabilitar el alias con el que se lanzó la tarea hace que Android quite esa tarea de Recientes, y hecho en
 * primer plano cerraría la app en la cara del usuario. En segundo plano, la próxima apertura desde el lanzador
 * arranca limpia; aceptado (spec §2.5). Android puede tardar unos segundos en refrescar el lanzador.
 */
class IconoAppRepo(private val context: Context) {

    fun aliasDe(idIcono: String, idPaletaOscura: String): ComponentName =
        ComponentName(context.packageName, "$PAQUETE_CLASES.Lanzador_${idIcono}_$idPaletaOscura")

    /** Ids resueltos contra sus catálogos: un id desconocido cae al valor por defecto, nunca a «ningún alias». */
    fun aplicar(idIcono: String, idPaletaOscura: String) {
        val icono = iconoPorId(idIcono).id
        val paleta = paletaPorId(idPaletaOscura, oscura = true).id
        if (PALETAS_OSCURAS.none { it.id == paleta }) return
        val pm = context.packageManager
        val objetivo = aliasDe(icono, paleta)
        // Primero se habilita el objetivo y después se apagan los demás: nunca hay un instante sin lanzador.
        fijar(pm, objetivo, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, habilitadoPorDefecto(icono, paleta))
        for (i in ICONOS_APP) {
            for (p in PALETAS_OSCURAS) {
                val alias = aliasDe(i.id, p.id)
                if (alias != objetivo) {
                    fijar(pm, alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, habilitadoPorDefecto(i.id, p.id))
                }
            }
        }
    }

    private fun habilitadoPorDefecto(icono: String, paleta: String) = (icono to paleta) == ALIAS_DEL_MANIFEST

    /** `COMPONENT_ENABLED_STATE_DEFAULT` significa «lo que diga el manifest»; solo se escribe si el estado efectivo cambia. */
    private fun fijar(pm: PackageManager, alias: ComponentName, estado: Int, porDefecto: Boolean) {
        val actual = when (pm.getComponentEnabledSetting(alias)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                if (porDefecto) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else -> pm.getComponentEnabledSetting(alias)
        }
        if (actual != estado) pm.setComponentEnabledSetting(alias, estado, PackageManager.DONT_KILL_APP)
    }
}
