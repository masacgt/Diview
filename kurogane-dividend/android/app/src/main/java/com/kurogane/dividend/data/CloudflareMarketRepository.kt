package com.kurogane.dividend.data

import com.kurogane.dividend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CloudflareSearchHit(
    val code: String,
    val name: String,
    val cachedPrice: Double? = null,
    val industry: String? = null
)

data class CloudflareSecurity(
    val code: String,
    val name: String,
    val annualDividendPerShare: Double?,
    val marketPrice: Double?,
    val industry: String? = null
)

data class CloudflareStockAnalysis(
    val values: Map<String, Double?>
)

data class CloudflarePopularStock(
    val code: String,
    val name: String,
    val sector: String?,
    val currentPrice: Double,
    val acquisitionPrice: Double,
    val acquisitionDate: String,
    val annualDividendPerShare: Double,
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

/** Public, read-only access to the app's Cloudflare D1/R2 data. No credentials belong in the APK. */
class CloudflareMarketRepository(
    private val baseUrl: String = BuildConfig.DIVIEW_D1_API_BASE_URL
) {
    suspend fun searchSecurities(query: String): List<CloudflareSearchHit> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val root = getJson("/api/v1/security-search?q=$encoded")
        val rows = root.optJSONArray("items") ?: return@withContext emptyList()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("code").trim()
            val name = row.optString("name").trim()
            if (code.isEmpty() || name.isEmpty()) null else CloudflareSearchHit(code, name)
        }
    }

    suspend fun getSecurity(code: String): CloudflareSecurity? = withContext(Dispatchers.IO) {
        val safeCode = URLEncoder.encode(code.trim(), Charsets.UTF_8.name())
        val root = runCatching { getJson("/api/v1/securities/$safeCode?include_market=false") }.getOrNull()
            ?: return@withContext null
        val dividend = root.optJSONObject("dividend")
        CloudflareSecurity(
            code = root.optString("code", code).ifBlank { code },
            name = root.optString("name").takeIf(String::isNotBlank) ?: return@withContext null,
            annualDividendPerShare = dividend?.numberOrNull("annual_dps_adjusted")
                ?: dividend?.numberOrNull("annual_dps"),
            marketPrice = root.optJSONObject("market_price")?.numberOrNull("close")
        )
    }

    suspend fun getStockAnalysis(code: String): CloudflareStockAnalysis? = withContext(Dispatchers.IO) {
        val safeCode = URLEncoder.encode(code.trim(), Charsets.UTF_8.name())
        val root = runCatching { getJson("/api/v1/analysis/$safeCode") }.getOrNull()
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

    /** Fetches the public catalog only when the caller's JST/12-hour gate permits a price refresh. */
    suspend fun fetchPriceCatalog(): Map<String, Double> = withContext(Dispatchers.IO) {
        val root = getJson("/api/v1/search-catalog")
        val rows = root.optJSONArray("items") ?: error("Cloudflareの銘柄カタログにitemsがありません")
        buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val code = row.optString("code").trim().uppercase()
                val price = row.numberOrNull("price")
                if (code.isNotEmpty() && price != null && price > 0.0) put(code, price)
            }
        }
    }

    /** Returns a page of R2-ranked stocks with current and five-year reference prices from the database. */
    suspend fun fetchPopularStocks(offset: Int, date: String, limit: Int = 1): CloudflarePopularStocksPage = withContext(Dispatchers.IO) {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "日付はyyyy-MM-dd形式で指定してください" }
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
            if (code.isBlank() || name.isBlank() || currentPrice <= 0.0 || acquisitionPrice <= 0.0 || tradingValue <= 0.0) return@mapNotNull null
            CloudflarePopularStock(
                code = code,
                name = name,
                sector = row.optString("sector").takeIf(String::isNotBlank),
                currentPrice = currentPrice,
                acquisitionPrice = acquisitionPrice,
                acquisitionDate = row.optString("acquisition_date").ifBlank { date },
                annualDividendPerShare = row.numberOrNull("annual_dividend_per_share") ?: 0.0,
                dividendYield = row.numberOrNull("yield_rate"),
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

    private fun getJson(path: String): JSONObject {
        require(baseUrl.startsWith("https://")) { "Cloudflare API URLはHTTPSで設定してください" }
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Cloudflare API HTTP $status: ${body.take(240)}" }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.numberOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }
}

/** The price feed is eligible only once on each Japan-local date, and never more often than 12 hours. */
object PriceRefreshPolicy {
    private const val MINIMUM_INTERVAL_MILLIS = 12L * 60L * 60L * 1_000L
    private val japanZone = java.time.ZoneId.of("Asia/Tokyo")

    fun shouldFetch(nowEpochMillis: Long, lastFetchEpochMillis: Long, lastFetchJapanDate: String?): Boolean {
        if (lastFetchEpochMillis <= 0L) return true
        val todayInJapan = java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(japanZone).toLocalDate().toString()
        if (lastFetchJapanDate == todayInJapan) return false
        return nowEpochMillis - lastFetchEpochMillis >= MINIMUM_INTERVAL_MILLIS
    }

    fun japanDate(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(japanZone).toLocalDate().toString()
}
