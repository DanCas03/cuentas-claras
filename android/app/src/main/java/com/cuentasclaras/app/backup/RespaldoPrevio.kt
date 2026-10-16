package com.cuentasclaras.app.backup

import com.cuentasclaras.app.data.db.CuentasDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val PREFIJO_PREVIO = "cuentas-claras-respaldo-previo-"
private const val RESPALDOS_CONSERVADOS = 5
private val FORMATO_PREVIO = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSS")

/** D16: antes de reemplazar datos, escribe el estado actual en `dir` como
 *  `cuentas-claras-respaldo-previo-<fecha>-<hora local>.json` (el nombre nunca se repite entre
 *  restauraciones) y conserva solo los 5 más recientes. Si algo falla, lanza: quien llama NO debe importar. */
suspend fun guardarRespaldoPrevio(db: CuentasDb, dir: File, ahora: LocalDateTime = LocalDateTime.now()): File {
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
