package com.kurogane.dividend.data

import com.kurogane.dividend.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
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
    val marketYieldRate: Double? = null,
    val isNoDividendConfirmed: Boolean = false
)

data class CloudflareSearchQuote(
    val price: Double?,
    val yieldRate: Double?,
    val annualDividendPerShare: Double? = null,
    val isNoDividendConfirmed: Boolean = false,
    val hasDividendStatus: Boolean = false,
    val quoteDate: String? = null
)

private data class CachedSearchCatalogQuotes(
    val loadedAtElapsedMillis: Long,
    val quotesByCode: Map<String, CloudflareSearchQuote>
)

data class CloudflareSecurity(
    val code: String,
    val name: String,
    val annualDividendPerShare: Double?,
    val marketPrice: Double?,
    val industry: String? = null,
    val marketYieldRate: Double? = null,
    val isNoDividendConfirmed: Boolean = false,
    val hasDividendStatus: Boolean = false,
    val dataRetrieval: CloudflareDataRetrieval? = null
)

data class CloudflareDataRetrieval(
    val yfinanceTicker: String?,
    val source: String?,
    val currency: String?,
    val lastAttemptAt: String?,
    val processingStatus: String?,
    val failureReason: String?,
    val dividendHistory: CloudflareDividendHistoryRetrieval?
)

data class CloudflareDividendHistoryRetrieval(
    val status: String?,
    val lastAttemptStatus: String?,
    val source: String?,
    val storedHistoryStatus: String?,
    val storedHistoryStart: String?,
    val storedHistoryEnd: String?,
    val lastCoverageStart: String?,
    val lastCoverageEnd: String?
)

data class CloudflareStockAnalysis(
    val values: Map<String, Double?>
)

data class CloudflareMarketQuote(
    val price: Double,
    val annualDividendPerShare: Double?,
    val isNoDividendConfirmed: Boolean = false,
    val hasDividendStatus: Boolean = false
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
    val tradingValue20d: Double,
    val isNoDividendConfirmed: Boolean = false
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

data class CloudflareShareSplitEvent(
    val date: LocalDate,
    val ratio: Double
)

data class CloudflareShareSplitHistory(
    val code: String,
    val historyStatus: String,
    val events: List<CloudflareShareSplitEvent>
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
    @Volatile
    private var cachedSearchCatalogQuotes: CachedSearchCatalogQuotes? = null
    private val searchCatalogQuotesLock = Any()

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
                    annualDividendPerShare = row.annualDividendPerShareOrNull(),
                    marketYieldRate = row.numberOrNull("yield_rate")?.takeIf { it in 0.0..100.0 },
                    isNoDividendConfirmed = row.hasConfirmedNoDividend()
                )
            }
        }
    }

    suspend fun fetchSearchQuotes(codes: Collection<String>): Map<String, CloudflareSearchQuote> = withContext(ioDispatcher) {
        val requestedCodes = codes.map { it.trim().uppercase(java.util.Locale.ROOT) }
            .filter(::isSecurityCode)
            .distinct()
        if (requestedCodes.isEmpty()) return@withContext emptyMap()
        val encodedCodes = URLEncoder.encode(requestedCodes.joinToString(","), Charsets.UTF_8.name())
        val liveQuotes = try {
            parseSearchQuotes(getJson("/api/v1/live-quotes?codes=$encodedCodes"), requestedCodes.toSet())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
        val needsCatalogFallback = requestedCodes.any { code ->
            val quote = liveQuotes[code]
            quote?.price == null || quote.yieldRate == null
        }
        if (!needsCatalogFallback) return@withContext liveQuotes

        val catalogQuotes = searchCatalogQuotes()
        buildMap {
            for (code in requestedCodes) {
                val live = liveQuotes[code]
                val catalog = catalogQuotes[code]
                val price = live?.price ?: catalog?.price
                val annualDividend = live?.annualDividendPerShare ?: catalog?.annualDividendPerShare
                val yieldRate = live?.yieldRate ?: catalog?.yieldRate
                val isNoDividendConfirmed = live?.isNoDividendConfirmed == true || catalog?.isNoDividendConfirmed == true
                val hasDividendStatus = live?.hasDividendStatus == true || catalog?.hasDividendStatus == true
                if (price != null || yieldRate != null || annualDividend != null) {
                    put(code, CloudflareSearchQuote(price, yieldRate, annualDividend, isNoDividendConfirmed, hasDividendStatus))
                }
            }
        }
    }

    private fun searchCatalogQuotes(): Map<String, CloudflareSearchQuote> {
        val now = System.nanoTime() / NANOS_PER_MILLISECOND
        cachedSearchCatalogQuotes?.let { cached ->
            if (now - cached.loadedAtElapsedMillis in 0 until SEARCH_CATALOG_CACHE_MILLIS) {
                return cached.quotesByCode
            }
        }
        return synchronized(searchCatalogQuotesLock) {
            val lockedNow = System.nanoTime() / NANOS_PER_MILLISECOND
            cachedSearchCatalogQuotes?.let { cached ->
                if (lockedNow - cached.loadedAtElapsedMillis in 0 until SEARCH_CATALOG_CACHE_MILLIS) {
                    return@synchronized cached.quotesByCode
                }
            }
            val rows = getJson("/api/v1/search-catalog").optJSONArray("items")
                ?: throw IOException("Cloudflareの銘柄カタログにitemsがありません")
            val quotes = buildMap {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT)
                    if (!isSecurityCode(code)) continue
                    val quote = parseSearchQuote(row) ?: continue
                    put(code, quote)
                }
            }
            if (quotes.isNotEmpty()) {
                cachedSearchCatalogQuotes = CachedSearchCatalogQuotes(lockedNow, quotes)
            }
            quotes
        }
    }

    private fun parseSearchQuotes(root: JSONObject, allowedCodes: Set<String>): Map<String, CloudflareSearchQuote> {
        val rows = root.optJSONArray("items") ?: throw IOException("Cloudflareの価格一覧にitemsがありません")
        return buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT)
                if (code !in allowedCodes) continue
                val quote = parseSearchQuote(row) ?: continue
                put(code, quote)
            }
        }
    }

    private fun parseSearchQuote(row: JSONObject): CloudflareSearchQuote? {
        val price = row.numberOrNull("price")?.takeIf { PortfolioMath.validMoney(it) && it > 0.0 }
        val annualDividend = row.annualDividendPerShareOrNull()
        val yieldRate = row.numberOrNull("yield_rate")?.takeIf { it in 0.0..100.0 }
        val noDividend = row.hasConfirmedNoDividend()
        val quoteDate = row.optString("quote_date").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        return CloudflareSearchQuote(price, yieldRate, annualDividend, noDividend, row.hasDividendStatus(), quoteDate)
            .takeIf { it.price != null || it.yieldRate != null || it.annualDividendPerShare != null }
    }

    private companion object {
        const val SEARCH_CATALOG_CACHE_MILLIS = 5L * 60L * 1_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    suspend fun getSecurity(code: String): CloudflareSecurity? = withContext(ioDispatcher) {
        val normalized = PortfolioMath.normalizeCode(code)
        require(isSecurityCode(normalized)) { "銘柄コードが正しくありません" }
        val safeCode = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        val root = try { getJson("/api/v1/securities/$safeCode") }
            catch (error: MarketHttpException) { if (error.status == 404) return@withContext null else throw error }
        val dividend = root.optJSONObject("dividend")
        // Legacy /securities responses contain IRBANK fiscal-year DPS. Only read
        // current dividend values from the v2 yfinance payload; calendar-year
        // history is handled separately by FinancialData and is not current DPS.
        val yfinanceDividend = dividend?.takeIf {
            it.has("yield_basis") || it.has("trailing_12_month_per_share") || it.optJSONObject("field_status") != null
        }
        val catalogQuote = runCatching { searchCatalogQuotes()[normalized] }.getOrNull()
        val detailPrice = root.optJSONObject("market_price")?.numberOrNull("close")
            ?: root.numberOrNull("market_price")
        val detailQuoteDate = root.optJSONObject("market_price")?.optString("date")
            ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            ?: root.optString("quote_date").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        val catalogPriceIsNewer = catalogQuote?.price != null &&
            (detailPrice == null || (catalogQuote.quoteDate != null &&
                (detailQuoteDate == null || catalogQuote.quoteDate > detailQuoteDate)))
        val retrievalJson = root.optJSONObject("data_retrieval")
        val dividendHistoryJson = retrievalJson?.optJSONObject("dividend_history")
        CloudflareSecurity(
            code = root.optString("code", normalized).ifBlank { normalized }.also {
                require(it.equals(normalized, true)) { "応答の銘柄コードが一致しません" }
            },
            name = root.optString("name").takeIf(String::isNotBlank) ?: return@withContext null,
            annualDividendPerShare = (if (root.hasUnknownDividendStatus() && !root.hasConfirmedNoDividend()) null else
                (yfinanceDividend?.numberOrNull("annual_dps_adjusted")
                    ?: yfinanceDividend?.numberOrNull("annual_dps"))?.takeIf(PortfolioMath::validMoney))
                ?: catalogQuote?.takeIf { it.hasDividendStatus }?.annualDividendPerShare,
            marketPrice = (if (catalogPriceIsNewer) catalogQuote?.price else detailPrice)
                ?.takeIf { PortfolioMath.validMoney(it) && it > 0.0 }
                ?: catalogQuote?.price,
            marketYieldRate = (if (yfinanceDividend != null) root.numberOrNull("market_yield_rate") else null)
                ?.takeIf { it in 0.0..100.0 }
                ?: catalogQuote?.yieldRate,
            isNoDividendConfirmed = root.hasConfirmedNoDividend() || catalogQuote?.isNoDividendConfirmed == true,
            hasDividendStatus = root.hasDividendStatus() || catalogQuote?.hasDividendStatus == true,
            dataRetrieval = retrievalJson?.let { retrieval ->
                CloudflareDataRetrieval(
                    yfinanceTicker = retrieval.stringOrNull("yfinance_ticker"),
                    source = retrieval.stringOrNull("source"),
                    currency = retrieval.stringOrNull("currency"),
                    lastAttemptAt = retrieval.stringOrNull("last_attempt_at"),
                    processingStatus = retrieval.stringOrNull("processing_status"),
                    failureReason = retrieval.stringOrNull("failure_reason"),
                    dividendHistory = dividendHistoryJson?.let { history ->
                        CloudflareDividendHistoryRetrieval(
                            status = history.stringOrNull("status"),
                            lastAttemptStatus = history.stringOrNull("last_attempt_status"),
                            source = history.stringOrNull("source"),
                            storedHistoryStatus = history.stringOrNull("stored_history_status"),
                            storedHistoryStart = history.stringOrNull("stored_history_start"),
                            storedHistoryEnd = history.stringOrNull("stored_history_end"),
                            lastCoverageStart = history.stringOrNull("last_coverage_start"),
                            lastCoverageEnd = history.stringOrNull("last_coverage_end")
                        )
                    }
                )
            }
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
                    val annualDividend = row.annualDividendPerShareOrNull()
                    val yieldRate = row.numberOrNull("yield_rate")?.takeIf { it in 0.0..100.0 }
                    put(code, CloudflareMarketQuote(
                        price,
                        annualDividend,
                        row.hasConfirmedNoDividend(),
                        row.hasDividendStatus()
                    ))
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
            val annualDividend = row.annualDividendPerShareOrNull()
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
                tradingValue20d = tradingValue,
                isNoDividendConfirmed = row.hasConfirmedNoDividend()
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

    /** Returns split and reverse-split events from the published yfinance R2 history. */
    suspend fun fetchShareSplitHistory(code: String, from: LocalDate, to: LocalDate): CloudflareShareSplitHistory = withContext(ioDispatcher) {
        val normalizedCode = code.trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T")
        require(normalizedCode.matches(Regex("[A-Z0-9]{4}")) && from <= to && to.year - from.year <= 9) {
            "銘柄コードまたは対象期間が正しくありません"
        }
        val safeCode = URLEncoder.encode(normalizedCode, Charsets.UTF_8.name())
        val encodedFrom = URLEncoder.encode(from.toString(), Charsets.UTF_8.name())
        val encodedTo = URLEncoder.encode(to.toString(), Charsets.UTF_8.name())
        val root = getJson("/api/v1/share-split-history?code=$safeCode&from=$encodedFrom&to=$encodedTo")
        val responseCode = root.optString("code").trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T")
        require(responseCode == normalizedCode) { "株式分割履歴の銘柄コードが一致しません" }
        val rows = root.optJSONArray("events")
        val events = (0 until (rows?.length() ?: 0)).mapNotNull { index ->
            val row = rows?.optJSONObject(index) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(row.optString("date")) }.getOrNull() ?: return@mapNotNull null
            val ratio = row.optDouble("ratio", Double.NaN)
            if (date !in from..to || !ratio.isFinite() || ratio <= 0.0 || kotlin.math.abs(ratio - 1.0) < 1e-9) {
                return@mapNotNull null
            }
            CloudflareShareSplitEvent(date, ratio)
        }.sortedBy { it.date }
        CloudflareShareSplitHistory(
            code = normalizedCode,
            historyStatus = root.optString("history_status", "not_fetched"),
            events = events
        )
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

}

private fun JSONObject.hasConfirmedNoDividend(): Boolean =
    optString("dividend_status").equals("no_dividend", ignoreCase = true) || optBoolean("is_no_dividend", false)

private fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.hasUnknownDividendStatus(): Boolean =
    optString("dividend_status").equals("unknown", ignoreCase = true)

private fun JSONObject.annualDividendPerShareOrNull(): Double? {
    if (hasUnknownDividendStatus() && !hasConfirmedNoDividend()) return null
    val amount = if (!has("annual_dividend_per_share") || isNull("annual_dividend_per_share")) null
        else optDouble("annual_dividend_per_share").takeIf(Double::isFinite)
    return amount
        ?.takeIf { it >= 0.0 && PortfolioMath.validMoney(it) }
}

private fun JSONObject.hasDividendStatus(): Boolean = has("dividend_status") || has("is_no_dividend")

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
