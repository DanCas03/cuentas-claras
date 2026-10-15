package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupsTest {
    private val cuentas = listOf(
        Account("acc-efectivo", "Efectivo", Currency.USD, AccountKind.CASH, false, false),
        Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, false, false),
    )
    private val categorias = listOf(
        Category("cat-supermercado", "Supermercado", "🛒", "#4ade80", CategoryKind.EXPENSE),
        Category("cat-sueldo", "Sueldo/Pagos", "💼", "#34d399", CategoryKind.INCOME),
        Category("cat-vuelto-ingreso", "Vuelto", "↩️", "#34d399", CategoryKind.INCOME),
        Category("cat-vuelto-gasto", "Vuelto", "↪️", "#f87171", CategoryKind.EXPENSE),
    )

    private fun tx(id: String, type: TxType, amountMinor: Long, groupId: String? = null, accountId: String = "acc-efectivo",
                   date: String = "2026-07-10", time: String? = null, categoryId: String? = null) =
        Transaction(id, type, accountId, amountMinor, date, time = time, categoryId = categoryId,
            source = TxSource.MANUAL, groupId = groupId)

    @Test fun `residuo por moneda firmado y residuo cero desaparece`() {
        assertEquals(listOf(ResiduoMoneda(Currency.USD, 150)),
            residuosDeGrupo(listOf(tx("a", TxType.INCOME, 500, "g1"), tx("b", TxType.EXPENSE, 350, "g1")), cuentas))
        assertEquals(emptyList(),
            residuosDeGrupo(listOf(tx("a", TxType.INCOME, 500, "g1"), tx("b", TxType.EXPENSE, 500, "g1")), cuentas))
        val bimoneda = residuosDeGrupo(listOf(tx("a", TxType.INCOME, 500, "g1"), tx("b", TxType.EXPENSE, 73723, "g1", "acc-bnc")), cuentas)
        assertTrue(ResiduoMoneda(Currency.USD, 500) in bimoneda)
        assertTrue(ResiduoMoneda(Currency.VES, -73723) in bimoneda)
    }

    @Test fun `categoriaDelResiduo exige que el kind coincida con el signo`() {
        val g = { cat: String? -> TxGroup("g1", categoryId = cat, createdIso = "x") }
        assertEquals("cat-supermercado", categoriaDelResiduo(g("cat-supermercado"), -200, categorias))
        assertEquals("cat-vuelto-ingreso", categoriaDelResiduo(g("cat-supermercado"), 150, categorias))
        assertEquals("cat-sueldo", categoriaDelResiduo(g("cat-sueldo"), 150, categorias))
        assertEquals("cat-vuelto-gasto", categoriaDelResiduo(g("cat-borrada"), -200, categorias))
        assertEquals("cat-vuelto-ingreso", categoriaDelResiduo(null, 150, categorias))
        assertEquals("cat-vuelto-gasto", categoriaDelResiduo(g("cat-supermercado"), -200, emptyList()))
    }

    @Test fun `txsEfectivas excluye los miembros y deja un virtual con la fecha del mas reciente`() {
        val txs = listOf(
            tx("s1", TxType.EXPENSE, 999),
            tx("m1", TxType.INCOME, 500, "g1", date = "2026-07-08"),
            tx("m2", TxType.EXPENSE, 350, "g1", date = "2026-07-10", time = "09:30:00"),
        )
        val efectivas = txsEfectivas(txs, cuentas, listOf(TxGroup("g1", nota = "leche", createdIso = "x")))
        assertEquals(listOf("s1", "virtual-g1-USD"), efectivas.map { it.id })
        val virtual = efectivas.last()
        assertEquals(TxType.INCOME, virtual.type)
        assertEquals(150L, virtual.amountMinor)
        assertEquals("cat-vuelto-ingreso", virtual.categoryId)
        assertEquals("2026-07-10", virtual.date)
        assertEquals("09:30:00", virtual.time)
        assertEquals("leche", virtual.note)
    }

    @Test fun `bimoneda evalua la categoria del grupo contra el signo de cada residuo`() {
        val txs = listOf(tx("m1", TxType.INCOME, 500, "g1"), tx("m2", TxType.EXPENSE, 73723, "g1", "acc-bnc"))
        val efectivas = txsEfectivas(txs, cuentas, listOf(TxGroup("g1", categoryId = "cat-supermercado", createdIso = "x")), categorias)
        assertEquals("cat-vuelto-ingreso", efectivas.first { it.id == "virtual-g1-USD" }.categoryId)
        assertEquals("cat-supermercado", efectivas.first { it.id == "virtual-g1-VES" }.categoryId)
    }
}
