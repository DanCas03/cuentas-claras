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

    /** Formas reales de la PWA que la fixture demo no cubre. Datos inventados; sin ningún null. */
    private val formas = """
{"schemaVersion":3,"exportedAt":"2026-10-10T14:00:00.000Z",
"accounts":[{"id":"a1","name":"X","currency":"USD","kind":"wallet","isSavings":false,"archived":true,"enDisposicion":false},
 {"id":"a2","name":"Y","currency":"VES","kind":"bank","isSavings":false,"archived":false,"bncLastSaldoMinor":-500}],
"transactions":[
 {"id":"t1","type":"expense","accountId":"a1","amountMinor":100,"date":"2026-06-01","source":"zinli","note":"Z"},
 {"id":"t2","type":"adjustment","accountId":"a1","amountMinor":-2500,"date":"2026-06-02","source":"adjust"},
 {"id":"t3","type":"transfer","accountId":"a1","toAccountId":"a2","amountMinor":100,"toAmountMinor":18550,"rateUsed":185.5,"date":"2026-06-03","source":"manual"},
 {"id":"t4","type":"transfer","accountId":"a1","toAccountId":"a1","amountMinor":100,"toAmountMinor":100,"date":"2026-06-03","source":"manual"}],
"categories":[{"id":"c1","name":"C","icon":"🛒","color":"#fff","kind":"income"}],
"rules":[{"id":"r1","pattern":"P","categoryId":"c1","autoCreated":false}],
"rates":[{"date":"2026-06-01","source":"MANUAL","vesPerUsd":0.5}],
"settings":[{"id":"main","viewCurrency":"VES","rateSource":"MANUAL"}],
"debts":[{"id":"d1","direction":"porCobrar","concepto":"x","amountMinor":5,"accountId":"a1","status":"cerrada","createdIso":"2026-06-01T00:00:00.000Z","settledTxId":"t1","settledIso":"2026-06-02T00:00:00.000Z"}],
"txGroups":[{"id":"g1","createdIso":"2026-06-01T00:00:00.000Z"}],
"calEvents":[
 {"id":"e1","titulo":"Una vez","categoryId":"c1","startDate":"2026-06-01","recurrence":{"kind":"once"},"createdIso":"2026-06-01T00:00:00.000Z"},
 {"id":"e2","titulo":"Semanal","categoryId":"c1","accountId":"a1","startDate":"2026-06-01","endDate":"2026-12-31","recurrence":{"kind":"weekly"},"createdIso":"2026-06-01T00:00:00.000Z"},
 {"id":"e3","titulo":"Anual","categoryId":"c1","amountMinor":7,"accountId":"a1","startDate":"2024-02-29","recurrence":{"kind":"yearly"},"omitidas":[],"createdIso":"2026-06-01T00:00:00.000Z"},
 {"id":"e4","titulo":"Mensual","categoryId":"c1","startDate":"2026-06-01","recurrence":{"days":[1,15,31],"kind":"monthly"},"omitidas":["2026-06-15","2026-07-01"],"createdIso":"2026-06-01T00:00:00.000Z"}]}
"""

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
        val exportado = exportBackup(db, "2026-10-15T12:00:00.000Z")
        assertEquals(ok(demo).copy(exportedAt = "2026-10-15T12:00:00.000Z"), exportado)
        val json = respaldoAJson(exportado)
        assertEquals(exportado, ok(json))
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

    @Test fun `todas las formas de la PWA sobreviven ida y vuelta completas y sin null`() = runBlocking {
        val original = ok(formas)
        importBackup(db, original)
        val exportado = exportBackup(db, original.exportedAt)
        assertEquals(original, exportado)
        val json = respaldoAJson(exportado)
        assertFalse(json.contains("null"), "la PWA rompe con claves en null")
        assertTrue(json.contains("\"kind\": \"once\""))
        assertTrue(json.contains("\"kind\": \"weekly\""))
        assertTrue(json.contains("\"kind\": \"yearly\""))
        assertEquals(original, ok(json))
    }

    @Test fun `validar acepta v3 exacto, normaliza v2 y rechaza v1, versiones raras y tablas faltantes`() {
        val v2 = """{"schemaVersion":2,"exportedAt":"x","accounts":[],"transactions":[],"categories":[],"rules":[],"rates":[],"settings":[],"debts":[],"txGroups":[]}"""
        assertEquals(emptyList(), ok(v2).calEvents)
        assertEquals(3, ok(v2).schemaVersion)
        assertEquals("Al respaldo le falta la tabla \"calEvents\"", error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":3")))
        assertTrue(error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":1")).contains("versión 1"))
        assertEquals("Versión de respaldo no soportada: 4", error(v2.replace("\"schemaVersion\":2", "\"schemaVersion\":4")))
        assertEquals("El archivo no es un JSON válido", error("esto no es json"))
        assertEquals("El archivo no es un respaldo de Cuentas Claras", error("5"))
        val ilegible = formas.replace("\"source\":\"zinli\"", "\"source\":\"otro\"")
        assertEquals("El respaldo tiene datos ilegibles en la tabla \"transactions\"", error(ilegible))
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
