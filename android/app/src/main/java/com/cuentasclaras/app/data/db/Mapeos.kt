package com.cuentasclaras.app.data.db

import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.CalEvent
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.Debt
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.Rule
import com.cuentasclaras.app.domain.Settings
import com.cuentasclaras.app.domain.Transaction
import com.cuentasclaras.app.domain.TxGroup

fun AccountEntity.toDomain() = Account(id, name, currency, kind, isSavings, archived, bncLastImportKey, bncLastSaldoMinor, enDisposicion)
fun Account.toEntity() = AccountEntity(id, name, currency, kind, isSavings, archived, bncLastImportKey, bncLastSaldoMinor, enDisposicion)

fun TransactionEntity.toDomain() = Transaction(
    id, type, accountId, amountMinor, date, time, categoryId, note, source, importKey,
    toAccountId, toAmountMinor, rateUsed, groupId, eventId, eventDate,
)
fun Transaction.toEntity() = TransactionEntity(
    id, type, accountId, amountMinor, date, time, categoryId, note, source, importKey,
    toAccountId, toAmountMinor, rateUsed, groupId, eventId, eventDate,
)

fun CategoryEntity.toDomain() = Category(id, name, icon, color, kind, monthlyBudgetMinor)
fun Category.toEntity() = CategoryEntity(id, name, icon, color, kind, monthlyBudgetMinor)

fun RateEntity.toDomain() = Rate(date, source, vesPerUsd)
fun Rate.toEntity() = RateEntity(date, source, vesPerUsd)

fun RuleEntity.toDomain() = Rule(id, pattern, categoryId, autoCreated)
fun Rule.toEntity() = RuleEntity(id, pattern, categoryId, autoCreated)

fun SettingsEntity.toDomain() = Settings(id, viewCurrency, rateSource, lastAccountId, lastRatesFetchDate)
fun Settings.toEntity() = SettingsEntity(id, viewCurrency, rateSource, lastAccountId, lastRatesFetchDate)

fun DebtEntity.toDomain() = Debt(id, direction, concepto, amountMinor, accountId, status, createdIso, settledTxId, settledIso)
fun Debt.toEntity() = DebtEntity(id, direction, concepto, amountMinor, accountId, status, createdIso, settledTxId, settledIso)

fun TxGroupEntity.toDomain() = TxGroup(id, nota, categoryId, createdIso)
fun TxGroup.toEntity() = TxGroupEntity(id, nota, categoryId, createdIso)

fun CalEventEntity.toDomain() = CalEvent(id, titulo, categoryId, accountId, amountMinor, startDate, endDate, recurrence, omitidas, createdIso)
fun CalEvent.toEntity() = CalEventEntity(id, titulo, categoryId, accountId, amountMinor, startDate, endDate, recurrence, omitidas, createdIso)
