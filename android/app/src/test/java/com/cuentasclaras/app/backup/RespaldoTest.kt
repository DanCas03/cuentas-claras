package com.cuentasclaras.app.backup

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.dbDePrueba
import com.cuentasclaras.app.data.fallarAntesDe
import com.cuentasclaras.app.data.seedIfEmpty
import com.cuentasclaras.app.domain.Recurrence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RespaldoTest {
    private lateinit var db: CuentasDb
    private val demo = javaClass.getResource("/respaldo-demo-v3.json")!!.readText()

    @Before fun abrir() { db = dbDePrueba() }
    @After fun cerrar() = db.close()

    private fun ok(texto: String) = assertIs<ValidacionRespaldo.Ok>(leerRespaldo(texto)).backup
    private fun error(texto: String) = assertIs<ValidacionRespaldo.Error>(leerRespaldo(texto)).mensaje

    @Test fun `un respaldo de la PWA con opcionales ausentes, tasa entera y clave extra se importa completo`() = runBlocking {
        importBackup(db, ok(demo))
        assertEquals(Conteos(5, 12, 18, 1, 3, 1, 2, 1, 2), contarTablas(db))
        assertEquals(180.0, db.rates().todas().first { it.date == "2026-10-01" }.vesPerUsd)
        val netflix = db.calEvents().porId("evento-netflix")!!.toDomain()
        assertEquals(Recurrence.Monthly(listOf(5)), netflix.recurrence)
        assertEquals(listOf("2026-09-05"), netflix.omitidas)
        assertEquals(true, db.accounts().porId("acc-binance")!!.enDisposicion)
        assertEquals(null, db.accounts().porId("acc-zinli")!!.enDisposicion)
    }

    @Test fun `exportar y volver a importar reproduce todo y el JSON no lleva null`() = runBlocking {
        importBackup(db, ok(demo))
        val json = respaldoAJson(exportBackup(db, "2026-10-15T12:00:00.000Z"))
        assertFalse(json.contains("null"), "la PWA rompe con claves en null")
        assertTrue(json.contains("\"kind\": \"monthly\""))
        assertTrue(json.contains("\"direction\": \"porPagar\""))
        val otra = dbDePrueba()
        seedIfEmpty(otra)
        importBackup(otra, ok(json))
        assertEquals(contarTablas(db), contarTablas(otra))
        assertEquals("grupo-demo", otra.transactions().porId("tx-demo-07")!!.groupId)
        otra.close()
    }

    @Test fun `validar acepta v3 exacto, normaliza v2 y rechaza v1, versiones raras y tablas faltantes`() {
        val v2 = """{"schemaVersion":2,"exportedAt":"x","accounts":[],"transactions":[],"categories":[],"rules":[],"rates":[],"settings":[],"debts":[],"txGroups":[]}"""
        assertEquals(emptyList(), ok(v2).calEvents)
        assertEquals(3, ok(v2).schemaVersion)
        assertEquals("Al respaldo le falta la tabla \"calEvents\"", error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":3")))
        assertTrue(error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":1")).contains("versión 1"))
        assertEquals("Versión de respaldo no soportada: 4", error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":4")))
        assertEquals("El archivo no es un respaldo de Cuentas Claras", error("esto no es json"))
        assertEquals("cuentas-claras-respaldo-2026-07-07.json", backupFilename("2026-07-07"))
    }

    @Test fun `importar es todo-o-nada si falla a mitad y deja intactos los datos previos`() = runBlocking {
        seedIfEmpty(db)
        val antes = contarTablas(db)
        db.fallarAntesDe("INSERT", "calEvents")
        assertFailsWith<Exception> { importBackup(db, ok(demo)) }
        assertEquals(antes, contarTablas(db))
    }
}
