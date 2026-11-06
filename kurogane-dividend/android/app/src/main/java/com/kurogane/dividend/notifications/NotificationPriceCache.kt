package com.kurogane.dividend.notifications

import android.content.Context
import com.kurogane.dividend.data.CloudflareMarketQuote
import com.kurogane.dividend.data.CloudflarePriceCatalogSnapshot
import com.kurogane.dividend.data.normalizeJapaneseSecurityCode
import com.kurogane.dividend.model.PortfolioMath
import org.json.JSONObject
import java.time.LocalDate

data class CachedNotificationPrice(val price: Double, val quoteDate: String)

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
                    put(code, CachedNotificationPrice(price, quoteDate))
                }
            }
        }
    }

    /** A successful but partial catalog refresh keeps previously acquired quotes for other codes. */
    fun merge(snapshot: CloudflarePriceCatalogSnapshot, fetchedAtMillis: Long) {
        val merged = mergeQuotes(read(), snapshot.quotes)
        val quotesJson = JSONObject().apply {
            merged.toSortedMap().forEach { (code, quote) ->
                put(code, JSONObject().put("price", quote.price).put("quote_date", quote.quoteDate))
            }
        }
        preferences.edit()
            .putString(QUOTES_KEY, quotesJson.toString())
            .putString(MARKET_DATE_KEY, snapshot.marketDataDate)
            .putLong(FETCHED_AT_KEY, fetchedAtMillis)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "notification_price_cache"
        private const val QUOTES_KEY = "quotes_v1"
        private const val MARKET_DATE_KEY = "market_data_date"
        private const val FETCHED_AT_KEY = "fetched_at_millis"

        internal fun mergeQuotes(
            existing: Map<String, CachedNotificationPrice>,
            fetched: Map<String, CloudflareMarketQuote>
        ): Map<String, CachedNotificationPrice> = buildMap {
            existing.entries.sortedBy { it.key == normalizeJapaneseSecurityCode(it.key) }.forEach { (rawCode, quote) ->
                val code = normalizeJapaneseSecurityCode(rawCode)
                if (!PortfolioMath.validCode(code) || !PortfolioMath.validMoney(quote.price) || quote.price <= 0.0 ||
                    !validDate(quote.quoteDate)) return@forEach
                val previous = get(code)
                if (previous == null || quote.quoteDate >= previous.quoteDate) put(code, quote)
            }
            fetched.forEach { (rawCode, quote) ->
                val code = normalizeJapaneseSecurityCode(rawCode)
                val date = quote.quoteDate?.takeIf(::validDate) ?: return@forEach
                if (!PortfolioMath.validCode(code) || !PortfolioMath.validMoney(quote.price) || quote.price <= 0.0) return@forEach
                val previous = get(code)
                if (previous == null || date >= previous.quoteDate) put(code, CachedNotificationPrice(quote.price, date))
            }
        }

        private fun validDate(value: String): Boolean = runCatching {
            LocalDate.parse(value).toString() == value
        }.getOrDefault(false)
    }
}
