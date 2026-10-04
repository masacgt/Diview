package com.kurogane.dividend.data

import android.content.Context
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import org.json.JSONArray
import org.json.JSONObject

/** Stores personal portfolio data in this app's private on-device preferences only. */
class LocalPortfolioRepository(context: Context) {
    private val prefs = context.getSharedPreferences("local_portfolio", Context.MODE_PRIVATE)

    fun loadHoldings(): List<Holding> = readArray(KEY_HOLDINGS) { item ->
        Holding(
            id = item.getString("id"),
            code = item.getString("code"),
            name = item.getString("name"),
            sector = item.optString("sector"),
            market = item.optString("market"),
            category = Category.valueOf(item.getString("category")),
            price = item.optDouble("price"),
            currency = item.optString("currency", "¥"),
            shares = item.optInt("shares"),
            acqPrice = item.optDouble("acqPrice"),
            dps = item.optDouble("dps"),
            yieldRate = item.optDouble("yieldRate"),
            yoc = item.optDouble("yoc"),
            nextPayoutTiming = item.optString("nextPayoutTiming"),
            nextPayoutAmount = item.optString("nextPayoutAmount"),
            dividendMonths = item.optJSONArray("dividendMonths").toIntList(),
            dividendType = item.optString("dividendType"),
            accountType = item.optString("accountType"),
            dailyChange = item.optString("dailyChange"),
            isPositive = item.optBoolean("isPositive"),
            fxRateToJpy = item.optDouble("fxRateToJpy", 1.0)
        )
    }.ifEmpty { if (!prefs.contains(KEY_HOLDINGS)) sampleHoldings else emptyList() }

    fun saveHoldings(holdings: List<Holding>) = saveArray(KEY_HOLDINGS, holdings.map { item ->
        JSONObject()
            .put("id", item.id)
            .put("code", item.code)
            .put("name", item.name)
            .put("sector", item.sector)
            .put("market", item.market)
            .put("category", item.category.name)
            .put("price", item.price)
            .put("currency", item.currency)
            .put("shares", item.shares)
            .put("acqPrice", item.acqPrice)
            .put("dps", item.dps)
            .put("yieldRate", item.yieldRate)
            .put("yoc", item.yoc)
            .put("nextPayoutTiming", item.nextPayoutTiming)
            .put("nextPayoutAmount", item.nextPayoutAmount)
            .put("dividendMonths", item.dividendMonths.toJsonArray())
            .put("dividendType", item.dividendType)
            .put("accountType", item.accountType)
            .put("dailyChange", item.dailyChange)
            .put("isPositive", item.isPositive)
            .put("fxRateToJpy", item.fxRateToJpy)
    })

    fun loadWatchlist(): List<WatchlistStock> = readArray(KEY_WATCHLIST) { item ->
        WatchlistStock(
            code = item.getString("code"),
            name = item.getString("name"),
            market = item.optString("market"),
            sector = item.optString("sector"),
            category = item.optString("category", "jp"),
            price = item.optDouble("price"),
            currency = item.optString("currency", "¥"),
            dps = item.optDouble("dps"),
            currentYield = item.optDouble("currentYield"),
            targetYield = item.optDouble("targetYield"),
            targetPrice = item.optDouble("targetPrice"),
            dailyChange = item.optString("dailyChange"),
            isPositive = item.optBoolean("isPositive"),
            payoutMonths = item.optJSONArray("payoutMonths").toIntList(),
            payoutTiming = item.optString("payoutTiming"),
            desc = item.optString("desc"),
            alertEnabled = item.optBoolean("alertEnabled"),
            badge = item.optString("badge").takeIf(String::isNotBlank),
            defaultShares = item.optInt("defaultShares", 100)
        )
    }.ifEmpty { if (!prefs.contains(KEY_WATCHLIST)) sampleWatchlist else emptyList() }

    fun saveWatchlist(stocks: List<WatchlistStock>) = saveArray(KEY_WATCHLIST, stocks.map { item ->
        JSONObject()
            .put("code", item.code)
            .put("name", item.name)
            .put("market", item.market)
            .put("sector", item.sector)
            .put("category", item.category)
            .put("price", item.price)
            .put("currency", item.currency)
            .put("dps", item.dps)
            .put("currentYield", item.currentYield)
            .put("targetYield", item.targetYield)
            .put("targetPrice", item.targetPrice)
            .put("dailyChange", item.dailyChange)
            .put("isPositive", item.isPositive)
            .put("payoutMonths", item.payoutMonths.toJsonArray())
            .put("payoutTiming", item.payoutTiming)
            .put("desc", item.desc)
            .put("alertEnabled", item.alertEnabled)
            .put("badge", item.badge)
            .put("defaultShares", item.defaultShares)
    })

    fun clearPortfolio() {
        prefs.edit().putString(KEY_HOLDINGS, "[]").putString(KEY_WATCHLIST, "[]").apply()
    }

    private fun <T> readArray(key: String, mapper: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (index in 0 until array.length()) add(mapper(array.getJSONObject(index))) }
        }.getOrDefault(emptyList())
    }

    private fun saveArray(key: String, objects: List<JSONObject>) {
        val array = JSONArray()
        objects.forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun List<Int>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach(array::put) }

    private fun JSONArray?.toIntList(): List<Int> = this?.let { array ->
        buildList { for (index in 0 until array.length()) add(array.optInt(index)) }
    } ?: emptyList()

    private companion object {
        const val KEY_HOLDINGS = "holdings"
        const val KEY_WATCHLIST = "watchlist"

        // First-run preview data mirrors the React app's initial mock portfolio.
        val sampleHoldings = listOf(
            Holding("h-toyota", "7203", "トヨタ自動車", "東証P • 輸送用機器", "東証PRM", Category.JAPAN,
                2600.0, "¥", 100, 2450.0, 72.8, 2.80, 2.97, "次回: 12月中旬 (¥3,640)", "¥3,640",
                listOf(6, 12), "中間配当", "新NISA成長枠", "+¥35 (+1.36%)", true),
            Holding("h-apple", "AAPL", "Apple Inc.", "NASDAQ • テクノロジー", "NASDAQ", Category.US,
                220.0, "$", 10, 192.8, 1.0, 0.52, 0.52, "次回: 2月中旬 ($2.50)", "¥425 ($2.50)",
                listOf(2, 5, 8, 11), "四半期配当", "特定口座 (四半期)", "+1.45 (+0.66%)", true, 154.0),
            Holding("h-emaxis", "eMAXIS", "eMAXIS Slim 全世界株式", "オルカン • 三菱UFJアセット", "投資信託", Category.FUND,
                24460.0, "¥", 50000, 24460.0, 0.0, 0.0, 0.0, "無分配で複利最大化", "¥0",
                emptyList(), "自動再投資", "新NISAつみたて投資枠", "-¥2,300 (-1.9%)", false)
        )

        val sampleWatchlist = listOf(
            WatchlistStock("7203", "トヨタ自動車", "東証PRM", "輸送用機器 · 日本株", "jp", 2600.0, "¥", 72.8, 2.80, 3.00, 2427.0, "+35 (+1.36%)", true, listOf(6, 12), "6月下旬 / 12月中旬", "東証PRM · 輸送用機器", false, "買場目安近接", 100),
            WatchlistStock("8316", "三井住友FG", "東証PRM", "銀行業 · 日本株", "jp", 3150.0, "¥", 130.0, 4.13, 3.80, 3421.0, "-22 (-0.69%)", false, listOf(6, 12), "6月下旬 / 12月中旬", "東証PRM · 銀行業", true, "絶好の買場到達 (利回り4.13%)", 100),
            WatchlistStock("9432", "日本電信電話 (NTT)", "東証PRM", "情報・通信業 · 日本株", "jp", 154.2, "¥", 5.2, 3.37, 4.00, 130.0, "0.0 (0.00%)", true, listOf(6, 12), "6月下旬 / 12月上旬", "東証PRM · 情報・通信業", true, "ディフェンシブ", 1000),
            WatchlistStock("AAPL", "Apple Inc.", "NASDAQ", "テクノロジー · 米国株", "us", 220.0, "$", 1.0, 0.45, 0.50, 200.0, "+1.45 (+0.66%)", true, listOf(2, 5, 8, 11), "各月中旬 (四半期)", "NASDAQ · US Tech · 米国株", false, "自社株買い+連続増配", 10)
        )
    }
}
