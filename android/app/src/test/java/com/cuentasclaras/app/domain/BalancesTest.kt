package com.cuentasclaras.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalancesTest {
    private val cuentas = listOf(
        Account("bnc", "BNC", Currency.VES, AccountKind.BANK, isSavings = false, archived = false),
        Account("efectivo", "Efectivo", Currency.USD, AccountKind.CASH, isSavings = false, archived = false),
        Account("binance", "Binance", Currency.USD, AccountKind.SNAPSHOT, isSavings = true, archived = false),
        Account("vieja", "Archivada", Currency.USD, AccountKind.CASH, isSavings = false, archived = true),
    )

    private fun tx(type: TxType, accountId: String, amountMinor: Long, toAccountId: String? = null, toAmountMinor: Long? = null) =
        Transaction(nuevoId(), type, accountId, amountMinor, "2026-07-01", source = TxSource.MANUAL,
            toAccountId = toAccountId, toAmountMinor = toAmountMinor)

    private val txs = listOf(
        tx(TxType.INCOME, "bnc", 500000),
        tx(TxType.EXPENSE, "bnc", 150000),
        tx(TxType.ADJUSTMENT, "binance", 25000),
        tx(TxType.TRANSFER, "efectivo", 1000, toAccountId = "bnc", toAmountMinor = 111000),
        tx(TxType.INCOME, "efectivo", 5000),
    )

    @Test fun `saldo por cuenta con transferencia cruzada y ajuste`() {
        assertEquals(461000L, accountBalanceMinor("bnc", txs))
        assertEquals(4000L, accountBalanceMinor("efectivo", txs))
        assertEquals(25000L, accountBalanceMinor("binance", txs))
        val sinDestino = listOf(tx(TxType.TRANSFER, "efectivo", 700, toAccountId = "binance"))
        assertEquals(700L, accountBalanceMinor("binance", sinDestino))
    }

    @Test fun `patrimonio en USD separa disposicion y ahorro y excluye archivadas`() {
        val p = patrimonio(cuentas, txs, 111.0)
        assertEquals(3, p.lines.size)
        assertEquals(4153L, p.lines.first { it.account.id == "bnc" }.balanceUsdMinor)
        assertEquals(25000L, p.ahorroUsdMinor)
        assertEquals(4153L + 4000L, p.disposicionUsdMinor)
        assertEquals(4153L + 4000L + 25000L, p.totalUsdMinor)
    }

    @Test fun `sin tasa lo no convertible queda null y lo USD sobrevive`() {
        val p = patrimonio(cuentas, txs, null)
        assertNull(p.lines.first { it.account.id == "bnc" }.balanceUsdMinor)
        assertNull(p.disposicionUsdMinor)
        assertEquals(25000L, p.ahorroUsdMinor)
        assertNull(p.totalUsdMinor)
    }

    @Test fun `vista a disposicion usa enDisposicion y el neto descuenta deudas por pagar convertidas`() {
        val flag = listOf(
            Account("usd1", "Efectivo", Currency.USD, AccountKind.CASH, false, false),
            Account("usd2", "Binance", Currency.USD, AccountKind.SNAPSHOT, true, false, enDisposicion = true),
            Account("usd3", "Guardado", Currency.USD, AccountKind.CASH, false, false, enDisposicion = false),
            Account("bs1", "BNC", Currency.VES, AccountKind.BANK, false, false),
        )
        val movs = listOf(tx(TxType.INCOME, "usd1", 1000), tx(TxType.INCOME, "usd2", 2000), tx(TxType.INCOME, "usd3", 4000))
        val deudas = listOf(
            Debt("a", DebtDirection.POR_PAGAR, "x", 500, "usd1", DebtStatus.PENDIENTE, "2026-07-22T00:00:00.000Z"),
            Debt("b", DebtDirection.POR_PAGAR, "y", 73723, "bs1", DebtStatus.PENDIENTE, "2026-07-22T00:00:00.000Z"),
        )
        val p = patrimonio(flag, movs, 737.23, deudas)
        assertEquals(3000L, p.aDisposicionUsdMinor)
        assertEquals(7000L, p.totalUsdMinor)
        assertEquals(7000L - 600L, p.netoUsdMinor)
        assertNull(patrimonio(flag, movs, null, deudas).netoUsdMinor)
    }
}
