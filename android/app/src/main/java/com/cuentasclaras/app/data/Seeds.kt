package com.cuentasclaras.app.data

import androidx.room.withTransaction
import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toDomain
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.domain.Account
import com.cuentasclaras.app.domain.AccountKind
import com.cuentasclaras.app.domain.Category
import com.cuentasclaras.app.domain.CategoryKind
import com.cuentasclaras.app.domain.Currency
import com.cuentasclaras.app.domain.RateSource
import com.cuentasclaras.app.domain.Settings

val CUENTAS_SEED = listOf(
    Account("acc-bnc", "BNC", Currency.VES, AccountKind.BANK, isSavings = false, archived = false),
    Account("acc-zinli", "Zinli", Currency.USD, AccountKind.WALLET, isSavings = false, archived = false),
    Account("acc-binance", "Binance", Currency.USD, AccountKind.SNAPSHOT, isSavings = true, archived = false),
    Account("acc-efectivo", "Efectivo", Currency.USD, AccountKind.CASH, isSavings = false, archived = false),
    Account("acc-efectivo-ahorro", "Efectivo ahorro", Currency.USD, AccountKind.CASH, isSavings = true, archived = false),
)

val CATEGORIAS_SEED = listOf(
    Category("cat-supermercado", "Supermercado", "🛒", "#4ade80", CategoryKind.EXPENSE),
    Category("cat-comida-fuera", "Comida fuera", "🍔", "#fbbf24", CategoryKind.EXPENSE),
    Category("cat-transporte", "Transporte", "🚌", "#60a5fa", CategoryKind.EXPENSE),
    Category("cat-salud", "Salud", "💊", "#f87171", CategoryKind.EXPENSE),
    Category("cat-servicios", "Servicios", "💡", "#facc15", CategoryKind.EXPENSE),
    Category("cat-comisiones", "Comisiones bancarias", "🏦", "#94a3b8", CategoryKind.EXPENSE),
    Category("cat-hogar", "Hogar", "🏠", "#a78bfa", CategoryKind.EXPENSE),
    Category("cat-ocio", "Ocio/Juegos", "🎮", "#f472b6", CategoryKind.EXPENSE),
    Category("cat-suscripciones", "Suscripciones", "📺", "#22d3ee", CategoryKind.EXPENSE),
    Category("cat-ropa", "Ropa", "👕", "#fb923c", CategoryKind.EXPENSE),
    Category("cat-otros-gastos", "Otros gastos", "📦", "#9ca3af", CategoryKind.EXPENSE),
    Category("cat-pago-movil-recibido", "Pago móvil recibido", "📲", "#4ade80", CategoryKind.INCOME),
    Category("cat-transferencia-recibida", "Transferencia recibida", "🔁", "#38bdf8", CategoryKind.INCOME),
    Category("cat-sueldo", "Sueldo/Pagos", "💼", "#34d399", CategoryKind.INCOME),
    Category("cat-otros-ingresos", "Otros ingresos", "➕", "#cbd5e1", CategoryKind.INCOME),
)

/** Seeds v2: se añaden por id si faltan (DB ya sembrada) o junto al seed inicial (DB fresca). */
val CATEGORIAS_SEED_V2 = listOf(
    Category("cat-deudas", "Deudas", "🤝", "#c084fc", CategoryKind.EXPENSE),
    Category("cat-vuelto-ingreso", "Vuelto", "↩️", "#34d399", CategoryKind.INCOME),
    Category("cat-vuelto-gasto", "Vuelto", "↪️", "#f87171", CategoryKind.EXPENSE),
)

val SETTINGS_SEED = Settings(id = "main", viewCurrency = Currency.USD, rateSource = RateSource.BCV, lastAccountId = "acc-bnc")

/** Crea cuentas, categorías y settings iniciales solo si la tabla está vacía. Las categorías v2
 *  además se reponen por id si faltan (add-if-missing, nunca pisa ediciones). */
suspend fun seedIfEmpty(db: CuentasDb) {
    db.withTransaction {
        if (db.accounts().contar() == 0) db.accounts().insertar(CUENTAS_SEED.map { it.toEntity() })
        if (db.categories().contar() == 0) {
            db.categories().insertarTodas((CATEGORIAS_SEED + CATEGORIAS_SEED_V2).map { it.toEntity() })
        } else {
            for (c in CATEGORIAS_SEED_V2) {
                if (db.categories().porId(c.id) == null) db.categories().insertar(c.toEntity())
            }
        }
        if (db.settings().contar() == 0) db.settings().insertarTodas(listOf(SETTINGS_SEED.toEntity()))
    }
}

suspend fun getSettings(db: CuentasDb): Settings = db.settings().principal()?.toDomain() ?: SETTINGS_SEED

/** Equivale a saveSettings(d, patch) de la web: lee, aplica el cambio y guarda. */
suspend fun saveSettings(db: CuentasDb, cambio: (Settings) -> Settings) {
    db.withTransaction {
        db.settings().guardar(cambio(getSettings(db)).copy(id = "main").toEntity())
    }
}
