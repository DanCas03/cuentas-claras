package com.cuentasclaras.app.importar

import com.cuentasclaras.app.data.db.ReferenciaCapturada
import com.cuentasclaras.app.data.db.TipoCaptura
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.importar.bnc.GapStatus
import com.cuentasclaras.app.importar.bnc.MOTIVO_POR_CAPTURA
import com.cuentasclaras.app.importar.bnc.MOTIVO_YA_IMPORTADO
import com.cuentasclaras.app.importar.bnc.bncImportKey
import com.cuentasclaras.app.importar.bnc.detectGap
import com.cuentasclaras.app.importar.bnc.mapBncRows
import com.cuentasclaras.app.importar.bnc.parseBncTxt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MapBncTest {
    private val rows = parseBncTxt(javaClass.getResource("/importar/bnc-fixture.txt")!!.readText()).rows
    private val categorias = listOf(
        Category("cat-comisiones", "Comisiones bancarias", "🏦", "#000", CategoryKind.EXPENSE),
        Category("cat-ocio", "Ocio/Juegos", "🎮", "#000", CategoryKind.EXPENSE),
        Category("cat-pago-movil-recibido", "Pago móvil recibido", "📲", "#000", CategoryKind.INCOME),
    )
    private val reglas = listOf(Rule("r1", "JUEGAENLINEA", "cat-ocio", true))
    private fun mapear(existentes: Set<String> = emptySet(), capturadas: List<ReferenciaCapturada> = emptyList()) =
        mapBncRows(rows, "acc-bnc", existentes, reglas, categorias, capturadas)

    @Test fun `mapea a borradores con importKey que incluye la fecha y propone categorias`() {
        val d = mapear()
        assertEquals(9, d.size)
        assertEquals(TxType.INCOME, d[0].tx.type)
        assertEquals("bnc:2026-07-02:100000007:16:20:30.900:150000", d[0].tx.importKey)
        assertEquals(34579L, d[1].tx.amountMinor)
        assertEquals("cat-pago-movil-recibido", d[0].tx.categoryId)
        assertEquals("cat-comisiones", d[2].tx.categoryId)
        assertEquals("cat-ocio", d[6].tx.categoryId)
        assertEquals("r1", d[6].matchedRuleId)
        assertNull(d[1].tx.categoryId)
        assertEquals(2, d.count { it.pairRef == "100000004" })
        val junio = rows.first { it.referencia == "0" }
        assertNotEquals(bncImportKey(junio), bncImportKey(junio.copy(date = "2026-07-30")))
    }

    @Test fun `duplicados por importKey ya existente`() {
        val d = mapear(existentes = setOf(bncImportKey(rows[0]), bncImportKey(rows[3])))
        assertEquals(2, d.count { it.duplicate })
        assertEquals(MOTIVO_YA_IMPORTADO, d[0].motivoDuplicado)
    }

    @Test fun `fila ya registrada por captura aunque cambien los ceros, pero no su comision`() {
        val d = mapear(capturadas = listOf(
            ReferenciaCapturada("0100000007", 150000, TipoCaptura.INGRESO),   // abono pago móvil, ref con un cero de más
            ReferenciaCapturada("00100000004", 120000, TipoCaptura.GASTO),    // cargo de 1.200,00; su comisión (3,60) comparte ref
            ReferenciaCapturada("0", 10, TipoCaptura.GASTO),                  // ref "0" y el monto de la fila de 0,10: nunca coincide
        ))
        assertEquals(MOTIVO_POR_CAPTURA, d[0].motivoDuplicado)
        assertEquals(true, d[0].duplicate)
        val cargo = d.first { it.pairRef == "100000004" && it.tx.amountMinor == 120000L }
        val comision = d.first { it.pairRef == "100000004" && it.tx.amountMinor == 360L }
        assertEquals(true, cargo.duplicate)
        assertEquals(MOTIVO_POR_CAPTURA, cargo.motivoDuplicado)
        assertEquals(false, comision.duplicate)
        assertNull(comision.motivoDuplicado)
        assertEquals(false, d.first { it.tx.amountMinor == 10L }.duplicate)
    }

    @Test fun `un reverso con misma referencia y monto pero sentido opuesto no sale duplicado`() {
        val cargo = rows.first { it.referencia == "100000004" && it.amountMinor == -120000L }
        val reverso = cargo.copy(date = "2026-06-24", time = "09:00:00.000", amountMinor = 120000L)
        val d = mapBncRows(listOf(cargo, reverso), "acc-bnc", emptySet(), reglas, categorias,
            listOf(ReferenciaCapturada("100000004", 120000, TipoCaptura.GASTO)))
        assertEquals(true, d[0].duplicate)
        assertEquals(MOTIVO_POR_CAPTURA, d[0].motivoDuplicado)
        assertEquals(false, d[1].duplicate)
        assertNull(d[1].motivoDuplicado)
    }

    @Test fun `una sola captura marca como mucho una fila de dos identicas`() {
        val cargo = rows.first { it.referencia == "100000004" && it.amountMinor == -120000L }
        val gemela = cargo.copy(time = "08:00:06.000")
        val d = mapBncRows(listOf(cargo, gemela), "acc-bnc", emptySet(), reglas, categorias,
            listOf(ReferenciaCapturada("100000004", 120000, TipoCaptura.GASTO)))
        assertEquals(true, d[0].duplicate)
        assertEquals(false, d[1].duplicate)
        val dos = mapBncRows(listOf(cargo, gemela), "acc-bnc", emptySet(), reglas, categorias, listOf(
            ReferenciaCapturada("100000004", 120000, TipoCaptura.GASTO),
            ReferenciaCapturada("0100000004", 120000, TipoCaptura.GASTO),
        ))
        assertEquals(listOf(true, true), dos.map { it.duplicate })
    }

    @Test fun `detectGap distingue los cuatro estados`() {
        assertEquals(GapStatus.PRIMERA_IMPORTACION, detectGap(rows, null, null))
        assertEquals(GapStatus.SOLAPADO, detectGap(rows, bncImportKey(rows[4]), 999))
        assertEquals(GapStatus.CONTINUO, detectGap(rows, "bnc:viejo:x:1", 50000))
        assertEquals(GapStatus.HUECO, detectGap(rows, "bnc:viejo:x:1", 123))
    }
}
