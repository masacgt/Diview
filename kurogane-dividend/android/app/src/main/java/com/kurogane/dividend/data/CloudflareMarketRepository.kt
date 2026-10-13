package com.kurogane.dividend.data

import com.kurogane.dividend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kurogane.dividend.model.PortfolioMath
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.URI
import java.io.IOException
import java.time.LocalDate

data class CloudflareSearchHit(
    val code: String,
    val name: String,
    val cachedPrice: Double? = null,
    val annualDividendPerShare: Double? = null,
    val industry: String? = null,
    val marketYieldRate: Double? = null
)

data class CloudflareSearchQuote(
    val price: Double?,
    val yieldRate: Double?,
    val annualDividendPerShare: Double? = null
)

data class CloudflareSecurity(
    val code: String,
    val name: String,
    val annualDividendPerShare: Double?,
    val marketPrice: Double?,
    val industry: String? = null,
    val marketYieldRate: Double? = null
)

data class CloudflareStockAnalysis(
    val values: Map<String, Double?>
)

data class CloudflareMarketQuote(
    val price: Double,
    val annualDividendPerShare: Double?
)

data class CloudflarePriceCatalogSnapshot(
    val quotes: Map<String, CloudflareMarketQuote>,
    val marketDataDate: String?
)

data class CloudflarePopularStock(
    val code: String,
    val name: String,
    val sector: String?,
    val currentPrice: Double,
    val acquisitionPrice: Double,
    val acquisitionDate: String,
    val annualDividendPerShare: Double?,
    val dividendYield: Double?,
    val tradingValue20d: Double
)

data class CloudflarePopularStocksPage(
    val items: List<CloudflarePopularStock>,
    val nextOffset: Int,
    val totalCount: Int
)

data class CloudflareMonthlyPrice(
    val month: String,
    val date: String,
    val close: Double
)

data class CloudflareMonthlyPriceSeries(
    val code: String,
    val prices: List<CloudflareMonthlyPrice>
)

data class CloudflareDividendMonths(
    val asOf: LocalDate,
    val monthsBySymbol: Map<String, List<Int>>,
    val eventsBySymbol: Map<String, List<CloudflareDividendEvent>> = emptyMap(),
    val upcomingEventsBySymbol: Map<String, CloudflareUpcomingDividendEvent> = emptyMap()
)

data class CloudflareDividendEvent(
    val date: LocalDate,
    val amountPerShare: Double,
    val finalRightsDate: LocalDate? = null,
    val recordDate: LocalDate? = null
)

data class CloudflareUpcomingDividendEvent(
    val exDividendDate: LocalDate,
    val finalRightsDate: LocalDate? = null,
    val recordDate: LocalDate? = null
)

/** Public, read-only access to the app's Cloudflare D1/R2 data. No credentials belong in the APK. */
class CloudflareMarketRepository(
    private val baseUrl: String = BuildConfig.DIVIEW_D1_API_BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    private data class SearchCatalogCache(val loadedAtMillis: Long, val quotesByCode: Map<String, CloudflareSearchQuote>)

    private val searchCatalogMutex = Mutex()
    @Volatile private var searchCatalogCache: SearchCatalogCache? = null

    suspend fun searchSecurities(query: String): List<CloudflareSearchHit> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        require(query.length <= 256) { "検索文字列が長すぎます" }
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val root = getJson("/api/v1/security-search?q=$encoded")
        val rows = root.optJSONArray("items") ?: return@withContext emptyList()
        if (rows.length() == 0) return@withContext emptyList()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT)
            val name = row.optString("name").trim()
            if (!isSecurityCode(code) || name.isEmpty()) {
                null
            } else {
                CloudflareSearchHit(
                    code = code,
                    name = name,
                    cachedPrice = row.numberOrNull("price")?.takeIf { PortfolioMath.validMoney(it) && it > 0.0 },
                    annualDividendPerShare = row.numberOrNull("annual_dividend_per_share")
                        ?.takeIf { it >= 0.0 && PortfolioMath.validMoney(it) },
                    marketYieldRate = row.numberOrNull("yield_rate")?.takeIf { it in 0.0..100.0 }
                )
            }
        }
    }

    suspend fun fetchSearchQuotes(codes: Collection<String>): Map<String, CloudflareSearchQuote> = withContext(ioDispatcher) {
        val requestedCodes = codes.map { it.trim().uppercase(java.util.Locale.ROOT) }
            .filter(::isSecurityCode)
            .distinct()
        if (requestedCodes.isEmpty()) return@withContext emptyMap()
        val quotesByCode = searchCatalogQuotesByCode()
        requestedCodes.mapNotNull { code -> quotesByCode[code]?.let { code to it } }.toMap()
    }

    private suspend fun searchCatalogQuotesByCode(): Map<String, CloudflareSearchQuote> {
        fun elapsedMillis() = System.nanoTime() / 1_000_000L
        searchCatalogCache?.takeIf { elapsedMillis() - it.loadedAtMillis < SEARCH_CATALOG_CACHE_TTL_MILLIS }
            ?.let { return it.quotesByCode }

        return searchCatalogMutex.withLock {
            val now = elapsedMillis()
            searchCatalogCache?.takeIf { now - it.loadedAtMillis < SEARCH_CATALOG_CACHE_TTL_MILLIS }
                ?.let { return@withLock it.quotesByCode }

            val root = getJson("/api/v1/search-catalog")
            val rows = root.optJSONArray("items") ?: throw IOException("Cloudflareの銘柄カタログにitemsがありません")
            val catalogDate = root.optString("market_data_date").takeIf(String::isNotBlank)
            val quotes = buildMap {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT)
                    if (!isSecurityCode(code)) continue
                    val quoteDate = row.optString("quote_date").takeIf(String::isNotBlank)
                    if (catalogDate != null && quoteDate != null && quoteDate > catalogDate) continue
                    val price = row.numberOrNull("price")
                        ?.takeIf { PortfolioMath.validMoney(it) && it > 0.0 }
                    val annualDividend = row.numberOrNull("annual_dividend_per_share")
                        ?.takeIf { it >= 0.0 && PortfolioMath.validMoney(it) }
                    val yieldRate = row.numberOrNull("yield_rate")
                        ?.takeIf { it in 0.0..100.0 }
                    if (price != null || annualDividend != null || yieldRate != null) {
                        put(code, CloudflareSearchQuote(price, yieldRate, annualDividend))
                    }
                }
            }
            searchCatalogCache = SearchCatalogCache(elapsedMillis(), quotes)
            quotes
        }
    }

    suspend fun getSecurity(code: String): CloudflareSecurity? = withContext(ioDispatcher) {
        val normalized = PortfolioMath.normalizeCode(code)
        require(isSecurityCode(normalized)) { "銘柄コードが正しくありません" }
        val safeCode = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        val root = try { getJson("/api/v1/securities/$safeCode") }
            catch (error: MarketHttpException) { if (error.status == 404) return@withContext null else throw error }
        val dividend = root.optJSONObject("dividend")
        CloudflareSecurity(
            code = root.optString("code", normalized).ifBlank { normalized }.also {
                require(it.equals(normalized, true)) { "応答の銘柄コードが一致しません" }
            },
            name = root.optString("name").takeIf(String::isNotBlank) ?: return@withContext null,
            annualDividendPerShare = (dividend?.numberOrNull("annual_dps_adjusted")
                ?: dividend?.numberOrNull("annual_dps"))?.takeIf(PortfolioMath::validMoney),
            marketPrice = root.optJSONObject("market_price")?.numberOrNull("close")?.takeIf { PortfolioMath.validMoney(it) && it > 0 },
            marketYieldRate = root.numberOrNull("market_yield_rate")?.takeIf { it in 0.0..100.0 }
        )
    }

    suspend fun getStockAnalysis(code: String): CloudflareStockAnalysis? = withContext(ioDispatcher) {
        require(isSecurityCode(PortfolioMath.normalizeCode(code))) { "銘柄コードが正しくありません" }
        val safeCode = URLEncoder.encode(code.trim(), Charsets.UTF_8.name())
        val root = runCatchingCancellable { getJson("/api/v1/analysis/$safeCode") }.getOrNull()
            ?: return@withContext null
        val sourceValues = root.optJSONObject("values") ?: return@withContext null
        val keys = listOf(
            "per", "pbr", "payout_ratio", "equity_ratio", "operating_margin",
            "fcf_margin", "dividend_growth_years"
        )
        CloudflareStockAnalysis(
            values = keys.associateWith { sourceValues.numberOrNull(it) }
        )
    }

    /** Fetches the published end-of-day catalog. Prices are never refreshed live by the app. */
    suspend fun fetchPriceCatalogSnapshot(): CloudflarePriceCatalogSnapshot = withContext(ioDispatcher) {
        val root = getJson("/api/v1/search-catalog")
        val rows = root.optJSONArray("items") ?: error("Cloudflareの銘柄カタログにitemsがありません")
        val catalogDate = root.optString("market_data_date").takeIf(String::isNotBlank)
        val quotes = buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT)
                val quoteDate = row.optString("quote_date").takeIf(String::isNotBlank)
                if (catalogDate != null && quoteDate != null && quoteDate > catalogDate) continue
                val price = row.numberOrNull("price")
                if (isSecurityCode(code) && price != null && PortfolioMath.validMoney(price) && price > 0.0) {
                    val annualDividend = row.numberOrNull("annual_dividend_per_share")
                        ?.takeIf { it >= 0.0 && PortfolioMath.validMoney(it) }
                    put(code, CloudflareMarketQuote(price, annualDividend))
                }
            }
        }.toMutableMap()
        CloudflarePriceCatalogSnapshot(quotes, catalogDate)
    }

    suspend fun fetchPriceQuotes(): Map<String, CloudflareMarketQuote> = fetchPriceCatalogSnapshot().quotes

    suspend fun fetchPriceCatalog(): Map<String, Double> = fetchPriceQuotes().mapValues { it.value.price }

    /** Returns a page of R2-ranked stocks with current and five-year reference prices from the database. */
    suspend fun fetchPopularStocks(offset: Int, date: String, limit: Int = 1): CloudflarePopularStocksPage = withContext(ioDispatcher) {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "日付はyyyy-MM-dd形式で指定してください" }
        LocalDate.parse(date)
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 20)
        val encodedDate = URLEncoder.encode(date, Charsets.UTF_8.name())
        val root = getJson("/api/v1/popular-holdings?offset=$safeOffset&limit=$safeLimit&date=$encodedDate")
        val rows = root.optJSONArray("items")
        val items = if (rows == null) emptyList() else (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("code").trim().uppercase()
            val name = row.optString("name").trim()
            val currentPrice = row.numberOrNull("current_price") ?: return@mapNotNull null
            val acquisitionPrice = row.numberOrNull("acquisition_price") ?: return@mapNotNull null
            val tradingValue = row.numberOrNull("estimated_average_trading_value_20d") ?: return@mapNotNull null
            if (!isSecurityCode(code) || name.isBlank() || !PortfolioMath.validMoney(currentPrice) || !PortfolioMath.validMoney(acquisitionPrice) || currentPrice <= 0.0 || acquisitionPrice <= 0.0 || tradingValue <= 0.0) return@mapNotNull null
            val annualDividend = row.numberOrNull("annual_dividend_per_share")
                ?.takeIf { it >= 0.0 && PortfolioMath.validMoney(it) }
            val dividendYield = annualDividend?.let { dividend ->
                (dividend / currentPrice * 100.0).takeIf { it.isFinite() && it in 0.0..100.0 }
            }
            CloudflarePopularStock(
                code = code,
                name = name,
                sector = row.optString("sector").takeIf(String::isNotBlank),
                currentPrice = currentPrice,
                acquisitionPrice = acquisitionPrice,
                acquisitionDate = row.optString("acquisition_date").ifBlank { date },
                annualDividendPerShare = annualDividend,
                dividendYield = dividendYield,
                tradingValue20d = tradingValue
            )
        }
        CloudflarePopularStocksPage(
            items = items,
            nextOffset = root.optInt("next_offset", safeOffset + safeLimit),
            totalCount = root.optInt("total_count", safeOffset + safeLimit)
        )
    }

    /** Returns month-end closes from R2 for up to 20 listed Japanese securities. */
    suspend fun fetchMonthlyPriceHistory(codes: List<String>, months: List<String>): List<CloudflareMonthlyPriceSeries> = withContext(Dispatchers.IO) {
        val normalizedCodes = codes.map { it.trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T") }.distinct()
        require(normalizedCodes.isNotEmpty() && normalizedCodes.size <= 20 && normalizedCodes.all { it.matches(Regex("[A-Z0-9]{4}")) }) {
            "銘柄コードは日本株20銘柄以内で指定してください"
        }
        val monthKeys = months.distinct()
        require(monthKeys.isNotEmpty() && monthKeys.size <= 12 && monthKeys.all { it.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")) }) {
            "対象月は12か月以内のyyyy-MM形式で指定してください"
        }
        val encodedCodes = URLEncoder.encode(normalizedCodes.joinToString(","), Charsets.UTF_8.name())
        val encodedMonths = URLEncoder.encode(monthKeys.joinToString(","), Charsets.UTF_8.name())
        val root = getJson("/api/v1/monthly-price-history?codes=$encodedCodes&months=$encodedMonths")
        val rows = root.optJSONArray("items") ?: return@withContext emptyList()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T")
            if (code !in normalizedCodes) return@mapNotNull null
            val prices = row.optJSONArray("prices") ?: return@mapNotNull null
            val monthlyPrices = (0 until prices.length()).mapNotNull { priceIndex ->
                val point = prices.optJSONObject(priceIndex) ?: return@mapNotNull null
                val month = point.optString("month")
                val date = point.optString("date")
                val close = point.numberOrNull("close") ?: return@mapNotNull null
                if (month !in monthKeys || !date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || close <= 0.0 || !close.isFinite()) {
                    return@mapNotNull null
                }
                CloudflareMonthlyPrice(month, date, close)
            }
            CloudflareMonthlyPriceSeries(code, monthlyPrices)
        }
    }

    /** Returns dividend months observed in the last 365 days from the Cloudflare R2 history. */
    suspend fun fetchDividendMonths(symbols: List<String>): CloudflareDividendMonths = withContext(ioDispatcher) {
        val normalizedSymbols = symbols.map { it.trim().uppercase(java.util.Locale.ROOT) }.distinct()
        require(normalizedSymbols.isNotEmpty() && normalizedSymbols.size <= 50 &&
            normalizedSymbols.all { it.matches(Regex("[A-Z0-9.^_=-]{1,32}")) }) {
            "配当履歴の銘柄コードは1〜50件で指定してください"
        }
        val encodedSymbols = URLEncoder.encode(normalizedSymbols.joinToString(","), Charsets.UTF_8.name())
        val root = getJson("/api/v1/dividend-months?symbols=$encodedSymbols")
        val asOf = runCatching { LocalDate.parse(root.optString("as_of")) }
            .getOrElse { throw IOException("Cloudflareの配当履歴日付が正しくありません", it) }
        val rows = root.optJSONArray("items") ?: throw IOException("Cloudflareの配当履歴にitemsがありません")
        val requested = normalizedSymbols.toSet()
        val eventsBySymbol = buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val symbol = row.optString("symbol").trim().uppercase(java.util.Locale.ROOT)
                if (symbol !in requested) continue
                val events = row.optJSONArray("events") ?: continue
                put(symbol, (0 until events.length()).mapNotNull { eventIndex ->
                    val event = events.optJSONObject(eventIndex) ?: return@mapNotNull null
                    val date = runCatching { LocalDate.parse(event.optString("date")) }.getOrNull()
                        ?: return@mapNotNull null
                    val amount = event.numberOrNull("amount")?.takeIf { it > 0.0 }
                        ?: return@mapNotNull null
                    val finalRightsDate = runCatching { LocalDate.parse(event.optString("final_rights_date")) }.getOrNull()
                    val recordDate = runCatching { LocalDate.parse(event.optString("record_date")) }.getOrNull()
                    CloudflareDividendEvent(date, amount, finalRightsDate, recordDate)
                }.distinctBy { it.date to it.amountPerShare }.sortedBy { it.date })
            }
        }
        val upcomingEventsBySymbol = buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val symbol = row.optString("symbol").trim().uppercase(java.util.Locale.ROOT)
                if (symbol !in requested) continue
                val event = row.optJSONObject("upcoming_event") ?: continue
                val exDividendDate = runCatching { LocalDate.parse(event.optString("ex_dividend_date")) }.getOrNull()
                    ?: continue
                val finalRightsDate = runCatching { LocalDate.parse(event.optString("final_rights_date")) }.getOrNull()
                val recordDate = runCatching { LocalDate.parse(event.optString("record_date")) }.getOrNull()
                put(symbol, CloudflareUpcomingDividendEvent(exDividendDate, finalRightsDate, recordDate))
            }
        }
        val monthsBySymbol = buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val symbol = row.optString("symbol").trim().uppercase(java.util.Locale.ROOT)
                if (symbol !in requested) continue
                val months = row.optJSONArray("months") ?: continue
                val validMonths = (0 until months.length())
                    .map { months.optInt(it, -1) }
                    .filter { it in 1..12 }
                    .distinct()
                    .sorted()
                put(symbol, validMonths)
            }
        }
        CloudflareDividendMonths(asOf, monthsBySymbol, eventsBySymbol, upcomingEventsBySymbol)
    }

    suspend fun financialDocument(code: String): JSONObject? = withContext(ioDispatcher) {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        if (!normalized.matches(Regex("[A-Z0-9]{4}"))) return@withContext null
        try { getJson("/api/v1/financial-data/$normalized") }
        catch (error: MarketHttpException) { if (error.status == 404) null else throw error }
    }

    private fun getJson(path: String): JSONObject {
        val base = URI(baseUrl)
        require(base.scheme == "https" && !base.host.isNullOrBlank() && base.userInfo == null && base.query == null && base.fragment == null) { "Cloudflare API URLは認証情報を含まないHTTPSで設定してください" }
        val connection = connectionFactory(URL(baseUrl.trimEnd('/') + path))
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw MarketHttpException(status)
            val body = connection.inputStream.use { it.readBoundedBytes(8 * 1024 * 1024).toString(Charsets.UTF_8) }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.numberOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun isSecurityCode(code: String) = code.matches(Regex("[A-Z0-9.\\-]{1,16}"))

    private companion object {
        const val SEARCH_CATALOG_CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}

class MarketHttpException(val status: Int) : IOException("Cloudflare API HTTP $status")

/** Refreshes at most once per Japan-local date and never more often than every twelve hours. */
object PriceRefreshPolicy {
    private const val MINIMUM_INTERVAL_MILLIS = 12L * 60L * 60L * 1_000L
    private val japanZone = java.time.ZoneId.of("Asia/Tokyo")

    fun shouldFetch(
        nowEpochMillis: Long,
        lastFetchEpochMillis: Long,
        lastFetchJapanDate: String?
    ): Boolean {
        val todayInJapan = java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(japanZone).toLocalDate().toString()
        if (lastFetchEpochMillis <= 0L) return true
        if (lastFetchJapanDate == todayInJapan) return false
        return nowEpochMillis >= lastFetchEpochMillis &&
            nowEpochMillis - lastFetchEpochMillis >= MINIMUM_INTERVAL_MILLIS
    }

    fun japanDate(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(japanZone).toLocalDate().toString()
}
