package com.cuentasclaras.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

/** Preferencias de apariencia. La paleta clara y la oscura se eligen por separado; el modo decide cuál se ve. */
data class Apariencia(
    val modo: ModoTema = ModoTema.SISTEMA,
    val idPaletaOscura: String = ID_PALETA_OSCURA_POR_DEFECTO,
    val idPaletaClara: String = ID_PALETA_CLARA_POR_DEFECTO,
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

    private companion object {
        val CLAVE_MODO = stringPreferencesKey("modo")
        val CLAVE_PALETA_OSCURA = stringPreferencesKey("paleta_oscura")
        val CLAVE_PALETA_CLARA = stringPreferencesKey("paleta_clara")
    }
}
