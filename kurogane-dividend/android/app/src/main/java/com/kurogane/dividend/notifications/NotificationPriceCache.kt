package com.kurogane.dividend.notifications

import android.content.Context
import com.kurogane.dividend.data.CloudflareMarketQuote
import com.kurogane.dividend.data.CloudflarePriceCatalogSnapshot
import com.kurogane.dividend.data.normalizeJapaneseSecurityCode
import com.kurogane.dividend.model.PortfolioMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

data class CachedNotificationPrice(
    val price: Double,
    val quoteDate: String,
    val fetchedAtMillis: Long,
    val marketDataDate: String?,
    val generation: String?
) {
    constructor(price: Double, quoteDate: String) : this(price, quoteDate, 0L, null, null)
}

/** Local quote cache consumed by notification work; this class has no network dependency. */
class NotificationPriceCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): Map<String, CachedNotificationPrice> {
        val root = runCatching { JSONObject(preferences.getString(QUOTES_KEY, "{}") ?: "{}") }.getOrNull()
            ?: return emptyMap()
        return buildMap {
            root.keys().asSequence().toList()
                .sortedBy { it == normalizeJapaneseSecurityCode(it) }
                .forEach { rawCode ->
                val code = normalizeJapaneseSecurityCode(rawCode)
                val item = root.optJSONObject(rawCode) ?: return@forEach
                val price = item.optDouble("price", Double.NaN)
                val quoteDate = item.optString("quote_date").takeIf(::validDate) ?: return@forEach
                if (PortfolioMath.validCode(code) && PortfolioMath.validMoney(price) && price > 0.0) {
                    val quote = CachedNotificationPrice(
                        price = price,
                        quoteDate = quoteDate,
                        fetchedAtMillis = item.optLong("fetched_at_millis", 0L).coerceAtLeast(0L),
                        marketDataDate = item.optString("market_data_date")
                            .takeIf { it != "null" && validDate(it) },
                        generation = item.optString("generation")
                            .takeIf { it != "null" && validGeneration(it) }
                    )
                    val previous = get(code)
                    if (previous == null || quoteDate > previous.quoteDate ||
                        (quoteDate == previous.quoteDate && rawCode == code)) {
                        put(code, quote)
                    }
                }
            }
        }
    }

    /** A successful but partial catalog refresh keeps previously acquired quotes for other codes. */
    fun merge(snapshot: CloudflarePriceCatalogSnapshot, fetchedAtMillis: Long) {
        persist(snapshot, fetchedAtMillis, waitForDisk = false)
    }

    /** Price data is consumed hours later by a separate Worker, so wait for durable storage off the main thread. */
    suspend fun mergeAndCommit(snapshot: CloudflarePriceCatalogSnapshot, fetchedAtMillis: Long): Boolean =
        withContext(Dispatchers.IO) {
            persist(snapshot, fetchedAtMillis, waitForDisk = true)
        }

    private fun persist(
        snapshot: CloudflarePriceCatalogSnapshot,
        fetchedAtMillis: Long,
        waitForDisk: Boolean
    ): Boolean {
        val merged = mergeQuotes(
            existing = read(),
            fetched = snapshot.quotes,
            fetchedAtMillis = fetchedAtMillis,
            marketDataDate = snapshot.marketDataDate,
            generation = snapshot.generation
        )
        val quotesJson = JSONObject().apply {
            merged.toSortedMap().forEach { (code, quote) ->
                put(code, JSONObject()
                    .put("price", quote.price)
                    .put("quote_date", quote.quoteDate)
                    .put("fetched_at_millis", quote.fetchedAtMillis)
                    .put("market_data_date", quote.marketDataDate)
                    .put("generation", quote.generation))
            }
        }
        val editor = preferences.edit()
            .putString(QUOTES_KEY, quotesJson.toString())
            .putString(MARKET_DATE_KEY, snapshot.marketDataDate)
            .putString(GENERATION_KEY, snapshot.generation)
            .putLong(FETCHED_AT_KEY, fetchedAtMillis)
        return if (waitForDisk) editor.commit() else {
            editor.apply()
            true
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "notification_price_cache"
        private const val QUOTES_KEY = "quotes_v1"
        private const val MARKET_DATE_KEY = "market_data_date"
        private const val GENERATION_KEY = "generation"
        private const val FETCHED_AT_KEY = "fetched_at_millis"

        internal fun mergeQuotes(
            existing: Map<String, CachedNotificationPrice>,
            fetched: Map<String, CloudflareMarketQuote>,
            fetchedAtMillis: Long = 0L,
            marketDataDate: String? = null,
            generation: String? = null
        ): Map<String, CachedNotificationPrice> = buildMap {
            existing.entries.sortedBy { it.key == normalizeJapaneseSecurityCode(it.key) }
                .forEach { (rawCode, quote) ->
                    val code = normalizeJapaneseSecurityCode(rawCode)
                    if (!PortfolioMath.validCode(code) || !PortfolioMath.validMoney(quote.price) || quote.price <= 0.0 ||
                        !validDate(quote.quoteDate)) return@forEach
                    val previous = get(code)
                    if (previous == null || quote.quoteDate > previous.quoteDate ||
                        (quote.quoteDate == previous.quoteDate && rawCode == code)) {
                        put(code, quote)
                    }
                }
            fetched.forEach { (rawCode, quote) ->
                val code = normalizeJapaneseSecurityCode(rawCode)
                val date = quote.quoteDate?.takeIf(::validDate) ?: return@forEach
                if (!PortfolioMath.validCode(code) || !PortfolioMath.validMoney(quote.price) || quote.price <= 0.0) return@forEach
                val previous = get(code)
                if (previous == null || date >= previous.quoteDate) {
                    put(code, CachedNotificationPrice(
                        price = quote.price,
                        quoteDate = date,
                        fetchedAtMillis = fetchedAtMillis.coerceAtLeast(0L),
                        marketDataDate = marketDataDate?.takeIf(::validDate),
                        generation = generation?.takeIf(::validGeneration)
                    ))
                }
            }
        }

        private fun validDate(value: String): Boolean = runCatching {
            LocalDate.parse(value).toString() == value
        }.getOrDefault(false)

        private fun validGeneration(value: String): Boolean =
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}(?:-[0-9a-f]{32})?"))
    }
}
