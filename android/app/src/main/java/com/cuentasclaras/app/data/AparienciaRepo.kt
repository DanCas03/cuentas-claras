package com.cuentasclaras.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Modo de color de la app (E20): seguir al sistema (por defecto) o fijar claro/oscuro. */
enum class ModoTema { SISTEMA, CLARO, OSCURO }

/** Ids estables de las paletas por defecto (E20). Deben existir en `PALETAS` (`ui/theme/Color.kt`). */
const val ID_PALETA_OSCURA_POR_DEFECTO = "noche_lima"
const val ID_PALETA_CLARA_POR_DEFECTO = "noche_claro"

/** Ids estables por defecto de la fuente (`FUENTES`), el diseño (`DISENOS`) y el ícono de la app (`ICONOS_APP`). */
const val ID_FUENTE_POR_DEFECTO = "bricolage"
const val ID_DISENO_POR_DEFECTO = "libreta"
const val ID_ICONO_POR_DEFECTO = "check"

/**
 * Preferencias de apariencia. La paleta clara y la oscura se eligen por separado; el modo decide cuál se ve.
 * Fuente, diseño, nombres en la barra e ícono de la app son independientes entre sí (spec 2026-10-17).
 * Un id que ya no exista en su catálogo cae al valor por defecto al resolverlo (`fuentePorId`, `disenoPorId`, `iconoPorId`).
 */
data class Apariencia(
    val modo: ModoTema = ModoTema.SISTEMA,
    val idPaletaOscura: String = ID_PALETA_OSCURA_POR_DEFECTO,
    val idPaletaClara: String = ID_PALETA_CLARA_POR_DEFECTO,
    val idFuente: String = ID_FUENTE_POR_DEFECTO,
    val idDiseno: String = ID_DISENO_POR_DEFECTO,
    val etiquetasBarra: Boolean = true,
    val idIcono: String = ID_ICONO_POR_DEFECTO,
)

/** Un único DataStore por proceso (el delegado lo garantiza). Fuera de Room y fuera del respaldo JSON. */
val Context.almacenApariencia: DataStore<Preferences> by preferencesDataStore(name = "apariencia")

/** Lectura y escritura de la apariencia en DataStore Preferences (E20). */
class AparienciaRepo(private val almacen: DataStore<Preferences>) {

    val apariencia: Flow<Apariencia> = almacen.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            Apariencia(
                modo = p[CLAVE_MODO]?.let { guardado -> ModoTema.entries.firstOrNull { it.name == guardado } }
                    ?: ModoTema.SISTEMA,
                idPaletaOscura = p[CLAVE_PALETA_OSCURA] ?: ID_PALETA_OSCURA_POR_DEFECTO,
                idPaletaClara = p[CLAVE_PALETA_CLARA] ?: ID_PALETA_CLARA_POR_DEFECTO,
                idFuente = p[CLAVE_FUENTE] ?: ID_FUENTE_POR_DEFECTO,
                idDiseno = p[CLAVE_DISENO] ?: ID_DISENO_POR_DEFECTO,
                etiquetasBarra = p[CLAVE_ETIQUETAS_BARRA] ?: true,
                idIcono = p[CLAVE_ICONO] ?: ID_ICONO_POR_DEFECTO,
            )
        }
        .distinctUntilChanged()

    suspend fun cambiarModo(modo: ModoTema) {
        almacen.edit { it[CLAVE_MODO] = modo.name }
    }

    suspend fun cambiarPaletaOscura(id: String) {
        almacen.edit { it[CLAVE_PALETA_OSCURA] = id }
    }

    suspend fun cambiarPaletaClara(id: String) {
        almacen.edit { it[CLAVE_PALETA_CLARA] = id }
    }

    suspend fun cambiarFuente(id: String) {
        almacen.edit { it[CLAVE_FUENTE] = id }
    }

    suspend fun cambiarDiseno(id: String) {
        almacen.edit { it[CLAVE_DISENO] = id }
    }

    suspend fun cambiarEtiquetasBarra(mostrar: Boolean) {
        almacen.edit { it[CLAVE_ETIQUETAS_BARRA] = mostrar }
    }

    suspend fun cambiarIcono(id: String) {
        almacen.edit { it[CLAVE_ICONO] = id }
    }

    private companion object {
        val CLAVE_MODO = stringPreferencesKey("modo")
        val CLAVE_PALETA_OSCURA = stringPreferencesKey("paleta_oscura")
        val CLAVE_PALETA_CLARA = stringPreferencesKey("paleta_clara")
        val CLAVE_FUENTE = stringPreferencesKey("fuente")
        val CLAVE_DISENO = stringPreferencesKey("diseno")
        val CLAVE_ETIQUETAS_BARRA = booleanPreferencesKey("etiquetas_barra")
        val CLAVE_ICONO = stringPreferencesKey("icono")
    }
}
