package com.cuentasclaras.app.backup

import com.cuentasclaras.app.data.db.CuentasDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PREFIJO_PREVIO = "cuentas-claras-respaldo-previo-"
private const val RESPALDOS_CONSERVADOS = 5
private val FORMATO_PREVIO = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSS")

/** D16: antes de reemplazar datos, escribe el estado actual en `dir` como
 *  `cuentas-claras-respaldo-previo-<fecha>-<hora local>.json` (el nombre nunca se repite entre
 *  restauraciones) y conserva solo los 5 más recientes. Si algo falla, lanza: quien llama NO debe importar. */
private suspend fun guardarRespaldoPrevio(db: CuentasDb, dir: File, ahora: LocalDateTime): File {
    val json = withContext(Dispatchers.Default) { respaldoAJson(exportBackup(db)) }
    return withContext(Dispatchers.IO) {
        if (!dir.isDirectory && !dir.mkdirs()) error("No se pudo crear la carpeta de respaldos de seguridad")
        val archivo = File(dir, backupFilename("previo-${ahora.format(FORMATO_PREVIO)}"))
        archivo.writeText(json)
        dir.listFiles { f -> f.name.startsWith(PREFIJO_PREVIO) }
            ?.sortedByDescending { it.name }
            ?.drop(RESPALDOS_CONSERVADOS)
            ?.forEach { it.delete() }
        archivo
    }
}

/** Respaldo de seguridad de `filesDir/respaldos/`: fecha `yyyy-mm-dd` y hora `HH:mm` locales en que se guardó. */
data class RespaldoSeguridad(val archivo: File, val fecha: String, val hora: String)

/** Sello `<fecha>-<HHmm>` del nombre (`FORMATO_PREVIO`): el resto (segundos y milisegundos) no se muestra. */
private val SELLO_PREVIO = Regex("^(\\d{4}-\\d{2}-\\d{2})-(\\d{2})(\\d{2})\\d*$")

/**
 * Los respaldos de seguridad de `dir` (I3), del más reciente al más antiguo y como mucho 5. La fecha y la hora salen
 * del nombre; si no lo trae con esa forma, de la última modificación del archivo. Sin carpeta, lista vacía.
 */
suspend fun listarRespaldosPrevios(dir: File): List<RespaldoSeguridad> = withContext(Dispatchers.IO) {
    (dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIJO_PREVIO) && f.name.endsWith(".json") } ?: emptyArray())
        .sortedByDescending { it.name }
        .take(RESPALDOS_CONSERVADOS)
        .map { f ->
            val sello = SELLO_PREVIO.matchEntire(f.name.removePrefix(PREFIJO_PREVIO).removeSuffix(".json"))
            if (sello != null) {
                val (fecha, hh, mm) = sello.destructured
                RespaldoSeguridad(f, fecha, "$hh:$mm")
            } else {
                val momento = LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault())
                RespaldoSeguridad(f, momento.toLocalDate().toString(), momento.format(FORMATO_HORA))
            }
        }
}

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Única vía para reemplazar los datos con un respaldo: primero el previo en `dirRespaldos` (D16) y, solo si
 *  se escribió, `importBackup`. Si el previo falla, lanza y no se importa nada. */
suspend fun restaurarConPrevio(db: CuentasDb, dirRespaldos: File, backup: BackupFile, ahora: LocalDateTime = LocalDateTime.now()) {
    guardarRespaldoPrevio(db, dirRespaldos, ahora)
    importBackup(db, backup)
}
