package com.cuentasclaras.app.rates

import com.cuentasclaras.app.data.db.CuentasDb
import com.cuentasclaras.app.data.db.toEntity
import com.cuentasclaras.app.data.getSettings
import com.cuentasclaras.app.data.saveSettings
import com.cuentasclaras.app.domain.JsonCuentas
import com.cuentasclaras.app.domain.Rate
import com.cuentasclaras.app.domain.RateSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class FetchedRates(
    val bcv: Double? = null,
    /** Reservado: ningún proveedor online lo trae; Binance sale del override manual. */
    val binance: Double? = null,
)

fun interface RateProvider {
    suspend fun fetch(): FetchedRates
}

private fun numeroPositivo(e: JsonElement?): Double? =
    (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it > 0 }

/** ve.dolarapi.com/v1/dolares/oficial → BCV en `promedio`. */
fun parseDolarApiOficial(json: JsonElement): FetchedRates =
    FetchedRates(bcv = numeroPositivo((json as? JsonObject)?.get("promedio")))

/** exchange.vcoud.com/coins/latest → array; BCV = item con `slug == "dolar-bcv"`, valor en `price`. */
fun parseCriptoDolarVcoud(json: JsonElement): FetchedRates {
    val item = (json as? JsonArray)?.firstOrNull {
        ((it as? JsonObject)?.get("slug") as? JsonPrimitive)?.content == "dolar-bcv"
    } as? JsonObject
    return FetchedRates(bcv = numeroPositivo(item?.get("price")))
}

fun crearHttp(): OkHttpClient = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

private suspend fun fetchJson(http: OkHttpClient, url: String): JsonElement = withContext(Dispatchers.IO) {
    http.newCall(Request.Builder().url(url).build()).execute().use { res ->
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
        JsonCuentas.parseToJsonElement(res.body.string())
    }
}

/** Cadena solo-BCV: dolarapi (primario) → vcoud (fallback). URLs de docs/spikes/2026-07-07-cors-tasas.md. */
fun proveedoresPorDefecto(http: OkHttpClient): List<RateProvider> = listOf(
    RateProvider { parseDolarApiOficial(fetchJson(http, "https://ve.dolarapi.com/v1/dolares/oficial")) },
    RateProvider { parseCriptoDolarVcoud(fetchJson(http, "https://exchange.vcoud.com/coins/latest")) },
)

/** Recorre la cadena; en cuanto un proveedor da el BCV, para. Los errores pasan al siguiente. */
suspend fun fetchRatesWithFallback(providers: List<RateProvider>): FetchedRates {
    var out = FetchedRates()
    for (p in providers) {
        if (out.bcv != null) break
        try {
            val r = p.fetch()
            out = FetchedRates(bcv = out.bcv ?: r.bcv, binance = out.binance ?: r.binance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // siguiente proveedor; el último recurso siempre es la tasa manual
        }
    }
    return out
}

suspend fun storeFetchedRates(db: CuentasDb, hoyIso: String, r: FetchedRates): Int {
    val filas = buildList {
        r.bcv?.let { add(Rate(hoyIso, RateSource.BCV, it)) }
        r.binance?.let { add(Rate(hoyIso, RateSource.BINANCE, it)) }
    }
    if (filas.isNotEmpty()) db.rates().guardarTodas(filas.map { it.toEntity() })
    return filas.size
}

/** Una vez al día al abrir la app (sin WorkManager). Sin red: silencioso. */
suspend fun refreshRatesOncePerDay(db: CuentasDb, hoyIso: String, providers: List<RateProvider>) {
    if (getSettings(db).lastRatesFetchDate == hoyIso) return
    val guardadas = storeFetchedRates(db, hoyIso, fetchRatesWithFallback(providers))
    if (guardadas > 0) saveSettings(db) { it.copy(lastRatesFetchDate = hoyIso) }
}
