package com.cuentasclaras.app.data

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.ops.ParcheCuentaBnc
import com.cuentasclaras.app.data.ops.agruparTxs
import com.cuentasclaras.app.data.ops.asignarEvento
import com.cuentasclaras.app.data.ops.commitImport
import com.cuentasclaras.app.data.ops.desagrupar
import com.cuentasclaras.app.data.ops.eliminarEvento
import com.cuentasclaras.app.data.ops.settleDebt
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.DebtDirection
import com.cuentasclaras.app.domain.DebtStatus
import com.cuentasclaras.app.domain.Ocurrencia
import com.cuentasclaras.app.domain.RECURRENCIA_QUINCENAL
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxSource
import com.cuentasclaras.app.domain.TxType
import com.cuentasclaras.app.domain.buildDebt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class OpsTodoONadaTest {
    private lateinit var db: CuentasDb

    @Before fun abrir() = runBlocking {
        db = dbDePrueba()
        seedIfEmpty(db)
    }

    @After fun cerrar() = db.close()

    private fun tx(id: String, type: TxType = TxType.EXPENSE, importKey: String? = "bnc:$id", accountId: String = "acc-bnc") =
        Transaction(id, type, accountId, 100, "2026-07-01", source = TxSource.BNC, importKey = importKey)

    @Test fun `commitImport revierte transacciones y reglas si falla al parchear la cuenta`() = runBlocking {
        db.transactions().insertar(tx("a").toEntity())
        db.fallarAntesDe("UPDATE", "accounts")
        assertFailsWith<Exception> {
            commitImport(
                db,
                txs = listOf(tx("b"), tx("c")),
                newRules = listOf(Rule("r1", "x", "cat-ocio", true)),
                accountPatch = ParcheCuentaBnc("acc-bnc", "k", 1),
            )
        }
        assertEquals(1, db.transactions().contar())
        assertEquals(0, db.rules().contar())
        assertNull(db.accounts().porId("acc-bnc")?.bncLastSaldoMinor)
    }

    @Test fun `commitImport aborta si una importKey ya existe (backstop) y no inserta nada`() = runBlocking {
        db.transactions().insertar(tx("otra-instancia").toEntity())
        assertFailsWith<IllegalStateException> {
            commitImport(db, txs = listOf(tx("esta", importKey = "bnc:otra-instancia")))
        }
        assertEquals(1, db.transactions().contar())
    }

    @Test fun `settleDebt revierte el movimiento si falla al cerrar la deuda y es idempotente`() = runBlocking {
        val deuda = buildDebt(DebtDirection.POR_PAGAR, "Juan — préstamo", 500, "acc-efectivo", "2026-07-22T10:00:00.000Z")
        db.debts().insertar(deuda.toEntity())
        db.fallarAntesDe("UPDATE", "debts")
        assertFailsWith<Exception> { settleDebt(db, deuda.id, "2026-07-22") }
        assertEquals(0, db.transactions().contar())
        assertEquals(DebtStatus.PENDIENTE, db.debts().porId(deuda.id)?.status)

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER falla_UPDATE_debts")
        settleDebt(db, deuda.id, "2026-07-22")
        settleDebt(db, deuda.id, "2026-07-23")
        assertEquals(1, db.transactions().contar())
        assertEquals("2026-07-22", db.debts().porId(deuda.id)?.settledIso)
    }

    @Test fun `agruparTxs revierte el grupo si falla al marcar los movimientos`() = runBlocking {
        db.transactions().insertarTodas(listOf(tx("m1", TxType.INCOME, null), tx("m2", TxType.EXPENSE, null)).map { it.toEntity() })
        db.fallarAntesDe("UPDATE", "transactions")
        assertFailsWith<Exception> { agruparTxs(db, listOf("m1", "m2"), "leche") }
        assertEquals(0, db.txGroups().contar())
        assertNull(db.transactions().porId("m1")?.groupId)
    }

    @Test fun `desagrupar revierte la limpieza si falla al borrar el grupo`() = runBlocking {
        db.transactions().insertarTodas(listOf(tx("m1", TxType.INCOME, null), tx("m2", TxType.EXPENSE, null)).map { it.toEntity() })
        val gid = agruparTxs(db, listOf("m1", "m2"), "leche")
        db.fallarAntesDe("DELETE", "txGroups")
        assertFailsWith<Exception> { desagrupar(db, gid) }
        assertEquals(gid, db.transactions().porId("m1")?.groupId)
        assertEquals(1, db.txGroups().contar())
    }

    @Test fun `eliminarEvento revierte la desasignacion si falla al borrar el evento, asignar valida la direccion`() = runBlocking {
        val quincena = CalEvent("e1", "Quincena", "cat-sueldo", accountId = "acc-bnc", startDate = "2026-01-01",
            recurrence = RECURRENCIA_QUINCENAL, createdIso = "2026-09-04T10:00:00.000Z")
        db.calEvents().guardar(quincena.toEntity())
        db.transactions().insertarTodas(listOf(tx("ing", TxType.INCOME, null), tx("gas", TxType.EXPENSE, null)).map { it.toEntity() })
        asignarEvento(db, "ing", Ocurrencia("e1", "2026-09-15"))
        assertEquals("cat-sueldo", db.transactions().porId("ing")?.categoryId)
        val e = assertFailsWith<IllegalArgumentException> { asignarEvento(db, "gas", Ocurrencia("e1", "2026-09-15")) }
        assertEquals("Este movimiento es un gasto y el evento es un cobro", e.message)

        db.fallarAntesDe("DELETE", "calEvents")
        assertFailsWith<Exception> { eliminarEvento(db, "e1") }
        assertEquals("e1", db.transactions().porId("ing")?.eventId)
        assertEquals(1, db.calEvents().contar())
    }
}
