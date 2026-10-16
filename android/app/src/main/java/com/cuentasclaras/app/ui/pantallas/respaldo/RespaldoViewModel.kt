package com.cuentasclaras.app.ui.pantallas.respaldo

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuentasclaras.app.backup.BackupFile
import com.cuentasclaras.app.backup.Conteos
import com.cuentasclaras.app.backup.RespaldoSeguridad
import com.cuentasclaras.app.backup.ValidacionRespaldo
import com.cuentasclaras.app.backup.backupFilename
import com.cuentasclaras.app.backup.contarTablas
import com.cuentasclaras.app.backup.exportBackup
import com.cuentasclaras.app.backup.restaurarConPrevio
import com.cuentasclaras.app.backup.leerRespaldo
import com.cuentasclaras.app.backup.listarRespaldosPrevios
import com.cuentasclaras.app.backup.respaldoAJson
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.domain.todayIso
import com.cuentasclaras.app.ui.Avisos
import com.cuentasclaras.app.ui.ERROR_DESCONOCIDO
import com.cuentasclaras.app.ui.Ocupacion
import com.cuentasclaras.app.ui.escribir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Tablas de Room que cuenta `contarTablas` (los nombres de `@Entity`/`@Query` de `data/db`). */
private val TABLAS_CONTADAS = arrayOf(
    "accounts", "transactions", "categories", "rules", "rates", "settings", "debts", "txGroups", "calEvents",
)

class RespaldoViewModel(private val db: CuentasDb, private val dirSeguridad: File) : ViewModel() {
    data class Pendiente(val backup: BackupFile, val resumen: String)
    data class Estado(
        val conteos: Conteos? = null,
        val pendiente: Pendiente? = null,
        val guardando: Boolean = false,
        /** Respaldos de seguridad de `filesDir/respaldos/` (I3), del más reciente al más antiguo. */
        val seguridad: List<RespaldoSeguridad> = emptyList(),
        /** Respaldo de seguridad que se pide confirmar antes de restaurarlo. */
        val seguridadPorRestaurar: RespaldoSeguridad? = null,
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()
    private val ocupacion = Ocupacion({ _estado.value.guardando }) { g -> _estado.update { it.copy(guardando = g) } }

    init {
        vigilarConteos()
        refrescarSeguridad()
    }

    /**
     * «Datos en la app» (D4): se vuelve a contar cada vez que Room invalida alguna de las tablas contadas (alta de
     * una cuenta, un movimiento, una restauración…), no solo al crear el ViewModel.
     */
    private fun vigilarConteos() {
        viewModelScope.launch {
            db.invalidationTracker.createFlow(*TABLAS_CONTADAS).collectLatest {
                _estado.update { it.copy(conteos = contarTablas(db)) }
            }
        }
    }

    /** Relee la carpeta de respaldos de seguridad: cada restauración añade uno (D16). */
    private fun refrescarSeguridad() {
        viewModelScope.launch {
            try {
                val lista = listarRespaldosPrevios(dirSeguridad)
                _estado.update { it.copy(seguridad = lista) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar("No se pudieron leer los respaldos de seguridad: ${e.message ?: ERROR_DESCONOCIDO}")
            }
        }
    }

    fun nombreSugerido(): String = backupFilename(todayIso())

    fun exportarA(resolver: ContentResolver, uri: Uri) = escribir(
        ocupacion,
        "No se pudo exportar",
        alTerminar = { Avisos.avisar("Respaldo guardado ✓") },
        aviso = { "No se pudo exportar: ${it.message ?: ERROR_DESCONOCIDO}" },
    ) {
        val json = withContext(Dispatchers.Default) { respaldoAJson(exportBackup(db)) }
        withContext(Dispatchers.IO) {
            val salida = resolver.openOutputStream(uri, "wt") ?: error("No se pudo abrir el archivo")
            salida.bufferedWriter().use { it.write(json) }
        }
    }

    fun elegirArchivo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val texto = try {
                withContext(Dispatchers.IO) {
                    val entrada = resolver.openInputStream(uri) ?: error("No se pudo abrir el archivo")
                    entrada.bufferedReader().use { it.readText() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Avisos.avisar("No se pudo leer el archivo: ${e.message ?: ERROR_DESCONOCIDO}")
                return@launch
            }
            when (val v = withContext(Dispatchers.Default) { leerRespaldo(texto) }) {
                is ValidacionRespaldo.Error -> Avisos.avisar(v.mensaje)
                is ValidacionRespaldo.Ok -> {
                    val b = v.backup
                    val resumen = "${b.transactions.size} movimientos, ${b.accounts.size} cuentas, " +
                        "${b.categories.size} categorías, ${b.debts.size} deudas, ${b.calEvents.size} eventos, exportado el ${b.exportedAt.take(10)}"
                    _estado.update { it.copy(pendiente = Pendiente(b, resumen)) }
                }
            }
        }
    }

    fun cancelar() = _estado.update { it.copy(pendiente = null) }

    /** Reemplaza TODO. Antes guarda un respaldo de seguridad de lo actual (D16). */
    fun restaurar() {
        val pendiente = _estado.value.pendiente ?: return
        escribir(
            ocupacion,
            "No se restauró nada",
            alTerminar = {
                _estado.update { it.copy(pendiente = null) }
                Avisos.avisar("Datos restaurados ✓")
            },
            aviso = { "No se restauró nada: ${it.message ?: ERROR_DESCONOCIDO}" },
        ) {
            try {
                restaurarConPrevio(db, dirSeguridad, pendiente.backup)
            } finally {
                // Aunque falle, el previo pudo guardarse (y rotar el más antiguo): la lista se relee siempre.
                refrescarSeguridad()
            }
        }
    }

    fun pedirRestaurarSeguridad(r: RespaldoSeguridad) = _estado.update { it.copy(seguridadPorRestaurar = r) }

    fun cancelarSeguridad() {
        if (!ocupacion.activa) _estado.update { it.copy(seguridadPorRestaurar = null) }
    }

    /**
     * Restaura un respaldo de seguridad leyéndolo directamente de `filesDir` (sin selector SAF). Pasa por
     * `restaurarConPrevio`, así que antes guarda otro previo de lo actual. Un respaldo inválido avisa con el mismo
     * texto que el restore desde un archivo (`leerRespaldo`) y no cambia nada.
     */
    fun restaurarSeguridad() {
        val r = _estado.value.seguridadPorRestaurar ?: return
        var restaurado = false
        escribir(
            ocupacion,
            "No se restauró nada",
            alTerminar = {
                _estado.update { it.copy(seguridadPorRestaurar = null) }
                if (restaurado) Avisos.avisar("Datos restaurados ✓")
            },
            aviso = { "No se restauró nada: ${it.message ?: ERROR_DESCONOCIDO}" },
        ) {
            try {
                val texto = withContext(Dispatchers.IO) { r.archivo.readText() }
                when (val v = withContext(Dispatchers.Default) { leerRespaldo(texto) }) {
                    is ValidacionRespaldo.Error -> Avisos.avisar(v.mensaje)
                    is ValidacionRespaldo.Ok -> {
                        restaurarConPrevio(db, dirSeguridad, v.backup)
                        restaurado = true
                    }
                }
            } finally {
                // Aunque falle, el previo pudo guardarse (y rotar el más antiguo) o el archivo ya no estar: se relee.
                refrescarSeguridad()
            }
        }
    }
}
