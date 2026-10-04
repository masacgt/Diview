package com.kurogane.dividend.data

import android.content.Context
import com.kurogane.dividend.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class SharedSecurityData(
    val code: String,
    val name: String,
    val price: Double?,
    val priceDate: String?,
    val annualDps: Double?,
    val payoutRatio: Double?,
    val consecutiveDividendIncreaseYears: Int?,
    val per: Double?,
    val pbr: Double?,
    val equityRatio: Double?,
    val operatingMargin: Double?,
    val fcfMargin: Double?,
    val dailyChange: String?,
    val isPositive: Boolean?
)

data class AnalysisBand(val minScore: Int, val maxScore: Int, val grade: String, val title: String, val comment: String, val detail: String = "")
data class MetricBand(val min: Double?, val max: Double?, val score: Int, val label: String, val comment: String = "")
data class MetricRule(val key: String, val label: String, val weight: Int, val bands: List<MetricBand>)
data class NarrativeSettings(
    val strengthThreshold: Int = 80,
    val weaknessThreshold: Int = 59,
    val boundaryRatio: Double = 0.18,
    val bandPositionLowerRatio: Double = 1.0 / 3.0,
    val bandPositionUpperRatio: Double = 2.0 / 3.0,
    val maxStrengths: Int = 2,
    val maxWeaknesses: Int = 2
)
data class StockAnalysisDefinitions(
    val overallBands: List<AnalysisBand>,
    val metricRules: List<MetricRule>,
    val narrativeSettings: NarrativeSettings = NarrativeSettings()
)
data class StockMetricResult(val rule: MetricRule, val value: Double, val band: MetricBand)
data class StockAnalysisResult(
    val score: Int?,
    val overallBand: AnalysisBand?,
    val metrics: List<StockMetricResult>,
    val riskMetric: StockMetricResult?,
    val narrative: String = ""
)
data class WatchlistBuyZone(val minRatio: Double, val status: String, val label: String, val comment: String, val detail: String = "")
data class WatchlistAnalysisDefinitions(val displayFields: Set<String>, val buyZone: List<WatchlistBuyZone>)

data class ScoreBand(val min: Double? = null, val max: Double? = null, val score: Int)
data class LabelBand(val min: Int? = null, val max: Int? = null, val label: String)
data class PortfolioAnalysisDefinitions(
    val healthyPayoutRatioMax: Double,
    val concentrationBands: List<ScoreBand>,
    val sectorBands: List<ScoreBand>,
    val holdingBands: List<ScoreBand>,
    val dividendGrowthBands: List<ScoreBand>,
    val healthyShareBands: List<ScoreBand>,
    val missingConcentrationScore: Int,
    val missingDividendGrowthScore: Int,
    val missingHealthyShareScore: Int,
    val weights: Map<String, Int>,
    val stabilityWeights: Map<String, Int>,
    val dividendRiskWeights: Map<String, Int>,
    val stabilityLabels: List<LabelBand>,
    val dividendRiskLabels: List<LabelBand>
)

fun evaluateStockAnalysis(
    shared: SharedSecurityData?,
    definitions: StockAnalysisDefinitions?
): StockAnalysisResult {
    if (shared == null || definitions == null) return StockAnalysisResult(null, null, emptyList(), null, "")
    val metricValues = mapOf(
        "per" to shared.per,
        "pbr" to shared.pbr,
        "payout_ratio" to shared.payoutRatio,
        "equity_ratio" to shared.equityRatio,
        "operating_margin" to shared.operatingMargin,
        "fcf_margin" to shared.fcfMargin,
        "dividend_growth_years" to shared.consecutiveDividendIncreaseYears?.toDouble()
    )
    val metrics = definitions.metricRules.mapNotNull { rule ->
        val value = metricValues[rule.key] ?: return@mapNotNull null
        val band = rule.bands.firstOrNull { band ->
            (band.min == null || value >= band.min) && (band.max == null || value <= band.max)
        } ?: return@mapNotNull null
        StockMetricResult(rule, value, band)
    }
    val totalWeight = metrics.sumOf { it.rule.weight }
    val score = if (totalWeight > 0) {
        (metrics.sumOf { it.band.score * it.rule.weight }.toDouble() / totalWeight).toInt()
    } else null
    val overallBand = score?.let { value -> definitions.overallBands.firstOrNull { value in it.minScore..it.maxScore } }
    val riskMetric = metrics.minByOrNull { it.band.score }?.takeIf { it.band.score < 60 }
    val narrative = buildStockNarrative(overallBand, metrics, definitions.narrativeSettings)
    return StockAnalysisResult(score, overallBand, metrics, riskMetric, narrative)
}

private fun buildStockNarrative(
    overallBand: AnalysisBand?,
    metrics: List<StockMetricResult>,
    settings: NarrativeSettings
): String {
    if (overallBand == null || metrics.isEmpty()) return ""

    val strengths = metrics
        .filter { it.band.score >= settings.strengthThreshold }
        .sortedWith(compareByDescending<StockMetricResult> { it.band.score }.thenByDescending { it.rule.weight })
        .take(settings.maxStrengths)
    val weaknesses = metrics
        .filter { it.band.score <= settings.weaknessThreshold }
        .sortedWith(compareBy<StockMetricResult> { it.band.score }.thenByDescending { it.rule.weight })
        .take(settings.maxWeaknesses)

    val parts = mutableListOf<String>()
    parts += "総合評価は${overallBand.grade}（${overallBand.title}）です。"

    if (strengths.isNotEmpty()) {
        parts += strengths.joinToString(" また、") { result ->
            "${result.rule.label}は${formatMetricValue(result)}で「${result.band.label}・${bandPositionLabel(result, settings)}」です。${trimSentence(result.band.comment)}${bandPositionComment(result, settings)}"
        }
    }
    if (weaknesses.isNotEmpty()) {
        parts += "一方、" + weaknesses.joinToString(" また、") { result ->
            "${result.rule.label}は${formatMetricValue(result)}で「${result.band.label}・${bandPositionLabel(result, settings)}」です。${trimSentence(result.band.comment)}${bandPositionComment(result, settings)}"
        }
    }

    val boundary = metrics
        .mapNotNull { result -> boundaryComment(result, settings.boundaryRatio) }
        .firstOrNull()
    if (boundary != null) parts += boundary

    if (overallBand.detail.isNotBlank()) parts += overallBand.detail
    return parts.joinToString(" ")
}

private fun formatMetricValue(result: StockMetricResult): String = when (result.rule.key) {
    "per", "pbr" -> String.format(Locale.JAPAN, "%.2f倍", result.value)
    "dividend_growth_years" -> "${result.value.toInt()}年"
    else -> String.format(Locale.JAPAN, "%.1f%%", result.value)
}

private fun trimSentence(value: String): String = value.trim().trimEnd('。') + "。"

private fun bandPositionRatio(result: StockMetricResult): Double? {
    val min = result.band.min
    val max = result.band.max
    if (min == null || max == null || max <= min || min <= -90000 || max >= 90000) return null
    return ((result.value - min) / (max - min)).coerceIn(0.0, 1.0)
}

private fun bandPositionLabel(result: StockMetricResult, settings: NarrativeSettings): String {
    val ratio = bandPositionRatio(result) ?: return "中央"
    return when {
        ratio < settings.bandPositionLowerRatio -> "下限寄り"
        ratio >= settings.bandPositionUpperRatio -> "上限寄り"
        else -> "中央"
    }
}

private fun bandPositionComment(result: StockMetricResult, settings: NarrativeSettings): String {
    return when (bandPositionLabel(result, settings)) {
        "下限寄り" -> " 同じ判定帯の中では下限側に位置しており、下側の判定帯に比較的近い水準です。"
        "上限寄り" -> " 同じ判定帯の中では上限側に位置しており、上側の判定帯に比較的近い水準です。"
        else -> " 同じ判定帯の中では中央付近に位置しています。"
    }
}

private fun boundaryComment(result: StockMetricResult, ratio: Double): String? {
    val min = result.band.min
    val max = result.band.max
    if (min == null && max == null) return null
    if (min != null && max != null && max > min && min > -90000 && max < 90000) {
        val width = max - min
        val lowerDistance = (result.value - min) / width
        val upperDistance = (max - result.value) / width
        return when {
            lowerDistance <= ratio -> "${result.rule.label}は現在の判定帯の下限付近にあり、わずかな変化で隣の評価へ移る可能性があります。"
            upperDistance <= ratio -> "${result.rule.label}は現在の判定帯の上限付近にあり、少しの変化で次の評価帯へ移る水準です。"
            else -> null
        }
    }
    if (max != null && max < 90000) {
        val distance = kotlin.math.abs(max - result.value) / kotlin.math.max(kotlin.math.abs(max), 1.0)
        if (distance <= ratio) return "${result.rule.label}は判定境界に近く、数値が少し動くだけでも評価文が変わる水準です。"
    }
    if (min != null && min > -90000) {
        val distance = kotlin.math.abs(result.value - min) / kotlin.math.max(kotlin.math.abs(min), 1.0)
        if (distance <= ratio) return "${result.rule.label}は判定境界に近く、数値が少し動くだけでも評価文が変わる水準です。"
    }
    return null
}

data class DiagnosisDefinition(
    val minScore: Int,
    val maxScore: Int,
    val grade: String,
    val title: String,
    val comment: String
)

data class ValuationDefinition(
    val id: Int,
    val perMax: Double,
    val pbrMax: Double,
    val title: String,
    val badge: String,
    val comment: String
)

class SharedMarketRepository(private val context: Context) {
    companion object {
        private const val PREFS = "shared_market_cache"
        private const val LAST_REFRESH = "last_refresh"
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldRefresh(now: Long = System.currentTimeMillis()): Boolean =
        BuildConfig.D1_TEST_MODE || now - prefs.getLong(LAST_REFRESH, 0L) >= ONE_DAY_MS

    fun markRefreshed(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(LAST_REFRESH, now).apply()
    }

    fun fetchSecurity(code: String): SharedSecurityData? {
        val key = "security_$code"
        val fresh = runCatching { get("/api/v1/securities/$code") }.getOrNull()
        val raw = if (BuildConfig.D1_TEST_MODE) fresh ?: return null
        else fresh ?: prefs.getString(key, null) ?: return null
        if (fresh != null) prefs.edit().putString(key, fresh).apply()
        return parseSecurity(raw)
    }

    fun cachedSecurity(code: String): SharedSecurityData? =
        prefs.getString("security_$code", null)?.let(::parseSecurity)

    fun cachedDiagnosisDefinitions(): List<DiagnosisDefinition> =
        cachedList("diagnosis_defs") { obj ->
            DiagnosisDefinition(
                minScore = obj.getInt("min_score"),
                maxScore = obj.getInt("max_score"),
                grade = obj.getString("grade"),
                title = obj.getString("title"),
                comment = obj.getString("comment")
            )
        }

    fun cachedValuationDefinitions(): List<ValuationDefinition> =
        cachedList("valuation_defs") { obj ->
            ValuationDefinition(
                id = obj.getInt("id"),
                perMax = obj.optDouble("per_max", Double.MAX_VALUE),
                pbrMax = obj.optDouble("pbr_max", Double.MAX_VALUE),
                title = obj.getString("title"),
                badge = obj.getString("badge"),
                comment = obj.getString("comment")
            )
        }

    fun fetchDiagnosisDefinitions(): List<DiagnosisDefinition> =
        fetchList("diagnosis_defs", "/api/v1/diagnosis-definitions") { obj ->
            DiagnosisDefinition(
                minScore = obj.getInt("min_score"),
                maxScore = obj.getInt("max_score"),
                grade = obj.getString("grade"),
                title = obj.getString("title"),
                comment = obj.getString("comment")
            )
        }

    fun fetchValuationDefinitions(): List<ValuationDefinition> =
        fetchList("valuation_defs", "/api/v1/valuation-scenarios") { obj ->
            ValuationDefinition(
                id = obj.getInt("id"),
                perMax = obj.optDouble("per_max", Double.MAX_VALUE),
                pbrMax = obj.optDouble("pbr_max", Double.MAX_VALUE),
                title = obj.getString("title"),
                badge = obj.getString("badge"),
                comment = obj.getString("comment")
            )
        }

    fun cachedStockAnalysisDefinitions(): StockAnalysisDefinitions? = prefs.getString("stock_analysis_defs", null)?.let(::parseStockAnalysisDefinitions)
    fun fetchStockAnalysisDefinitions(): StockAnalysisDefinitions? = fetchDocument("stock_analysis_defs", "/api/v1/stock-analysis-definitions", ::parseStockAnalysisDefinitions)
    fun cachedWatchlistAnalysisDefinitions(): WatchlistAnalysisDefinitions? = prefs.getString("watchlist_analysis_defs", null)?.let(::parseWatchlistAnalysisDefinitions)
    fun fetchWatchlistAnalysisDefinitions(): WatchlistAnalysisDefinitions? = fetchDocument("watchlist_analysis_defs", "/api/v1/watchlist-analysis-definitions", ::parseWatchlistAnalysisDefinitions)
    fun cachedPortfolioAnalysisDefinitions(): PortfolioAnalysisDefinitions? = prefs.getString("portfolio_analysis_defs", null)?.let(::parsePortfolioAnalysisDefinitions)
    fun fetchPortfolioAnalysisDefinitions(): PortfolioAnalysisDefinitions? = fetchDocument("portfolio_analysis_defs", "/api/v1/portfolio-analysis-definitions", ::parsePortfolioAnalysisDefinitions)

    private fun <T> fetchList(cacheKey: String, path: String, mapper: (JSONObject) -> T): List<T> {
        val fresh = runCatching { get(path) }.getOrNull()
        val raw = fresh ?: prefs.getString(cacheKey, null) ?: return emptyList()
        if (fresh != null) prefs.edit().putString(cacheKey, fresh).apply()
        val items = JSONObject(raw).optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) add(mapper(items.getJSONObject(i)))
        }
    }

    private fun <T> cachedList(cacheKey: String, mapper: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(cacheKey, null) ?: return emptyList()
        val items = JSONObject(raw).optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) add(mapper(items.getJSONObject(i)))
        }
    }

    private fun <T> fetchDocument(cacheKey: String, path: String, mapper: (String) -> T): T? {
        val fresh = runCatching { get(path) }.getOrNull()
        val raw = fresh ?: prefs.getString(cacheKey, null) ?: return null
        if (fresh != null) prefs.edit().putString(cacheKey, fresh).apply()
        return runCatching { mapper(raw) }.getOrNull()
    }

    private fun parseStockAnalysisDefinitions(raw: String): StockAnalysisDefinitions {
        val root = JSONObject(raw)
        val overall = root.optJSONArray("overall_bands") ?: JSONArray()
        val metrics = root.optJSONArray("metric_rules") ?: JSONArray()
        val narrative = root.optJSONObject("narrative_settings")
        return StockAnalysisDefinitions(
            overallBands = buildList {
                for (i in 0 until overall.length()) {
                    val o = overall.getJSONObject(i)
                    add(AnalysisBand(o.getInt("min_score"), o.getInt("max_score"), o.getString("grade"), o.getString("title"), o.getString("comment"), o.optString("detail")))
                }
            },
            metricRules = buildList {
                for (i in 0 until metrics.length()) {
                    val m = metrics.getJSONObject(i)
                    val bands = m.optJSONArray("bands") ?: JSONArray()
                    add(MetricRule(m.getString("key"), m.getString("label"), m.getInt("weight"), buildList {
                        for (j in 0 until bands.length()) {
                            val b = bands.getJSONObject(j)
                            add(MetricBand(b.optDouble("min").takeUnless { it.isNaN() }, b.optDouble("max").takeUnless { it.isNaN() }, b.getInt("score"), b.getString("label"), b.optString("comment")))
                        }
                    }))
                }
            },
            narrativeSettings = NarrativeSettings(
                strengthThreshold = narrative?.optInt("strength_threshold", 80) ?: 80,
                weaknessThreshold = narrative?.optInt("weakness_threshold", 59) ?: 59,
                boundaryRatio = narrative?.optDouble("boundary_ratio", 0.18) ?: 0.18,
                bandPositionLowerRatio = narrative?.optDouble("band_position_lower_ratio", 1.0 / 3.0) ?: (1.0 / 3.0),
                bandPositionUpperRatio = narrative?.optDouble("band_position_upper_ratio", 2.0 / 3.0) ?: (2.0 / 3.0),
                maxStrengths = narrative?.optInt("max_strengths", 2) ?: 2,
                maxWeaknesses = narrative?.optInt("max_weaknesses", 2) ?: 2
            )
        )
    }

    private fun parseWatchlistAnalysisDefinitions(raw: String): WatchlistAnalysisDefinitions {
        val root = JSONObject(raw)
        val displayFieldsJson = root.optJSONArray("display_fields") ?: JSONArray()
        val displayFields = buildSet {
            for (i in 0 until displayFieldsJson.length()) add(displayFieldsJson.getString(i))
        }
        val zones = root.optJSONArray("buy_zone") ?: JSONArray()
        return WatchlistAnalysisDefinitions(displayFields, buildList {
            for (i in 0 until zones.length()) {
                val z = zones.getJSONObject(i)
                add(WatchlistBuyZone(z.getDouble("min_ratio"), z.getString("status"), z.getString("label"), z.getString("comment"), z.optString("detail")))
            }
        })
    }

    private fun parsePortfolioAnalysisDefinitions(raw: String): PortfolioAnalysisDefinitions {
        val root = JSONObject(raw)
        fun scoreBands(name: String): List<ScoreBand> {
            val arr = root.getJSONObject("scores").optJSONArray(name) ?: JSONArray()
            return buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ScoreBand(o.optDouble("min").takeUnless { it.isNaN() }, o.optDouble("max").takeUnless { it.isNaN() }, o.getInt("score"))) } }
        }
        fun weights(name: String): Map<String, Int> {
            val obj = root.getJSONObject(name)
            return obj.keys().asSequence().associateWith { obj.getInt(it) }
        }
        fun labels(name: String): List<LabelBand> {
            val arr = root.optJSONArray(name) ?: JSONArray()
            return buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(LabelBand(o.optInt("min").takeIf { o.has("min") }, o.optInt("max").takeIf { o.has("max") }, o.getString("label"))) } }
        }
        val missing = root.getJSONObject("missing_scores")
        return PortfolioAnalysisDefinitions(
            healthyPayoutRatioMax = root.getDouble("healthy_payout_ratio_max"),
            concentrationBands = scoreBands("concentration"),
            sectorBands = scoreBands("sector_count"),
            holdingBands = scoreBands("holding_count"),
            dividendGrowthBands = scoreBands("dividend_growth_years"),
            healthyShareBands = scoreBands("healthy_share"),
            missingConcentrationScore = missing.getInt("concentration"),
            missingDividendGrowthScore = missing.getInt("dividend_growth_years"),
            missingHealthyShareScore = missing.getInt("healthy_share"),
            weights = weights("weights"),
            stabilityWeights = weights("stability_weights"),
            dividendRiskWeights = weights("dividend_risk_weights"),
            stabilityLabels = labels("stability_labels"),
            dividendRiskLabels = labels("dividend_risk_labels")
        )
    }

    private fun parseSecurity(raw: String): SharedSecurityData {
        val obj = JSONObject(raw)
        val dividend = obj.optJSONObject("dividend")
        val valuation = obj.optJSONObject("valuation")
        val financial = obj.optJSONObject("financial")?.optJSONObject("data")
        val marketPrice = obj.optJSONObject("market_price")
        val change = marketPrice?.optDouble("change")?.takeUnless { it.isNaN() }
        val changePercent = marketPrice?.optDouble("change_percent")?.takeUnless { it.isNaN() }
        return SharedSecurityData(
            code = obj.getString("code"),
            name = obj.getString("name"),
            price = marketPrice?.optDouble("close")?.takeUnless { it.isNaN() },
            priceDate = marketPrice?.optString("date")?.takeIf { it.isNotBlank() },
            annualDps = dividend?.optDouble("annual_dps_adjusted")?.takeUnless { it.isNaN() },
            payoutRatio = dividend?.optDouble("payout_ratio")?.takeUnless { it.isNaN() },
            consecutiveDividendIncreaseYears = dividend?.optInt("consecutive_dividend_increase_years"),
            per = valuation?.optDouble("per")?.takeUnless { it.isNaN() },
            pbr = valuation?.optDouble("pbr")?.takeUnless { it.isNaN() },
            equityRatio = financial?.optDouble("equity_ratio_computed")?.takeUnless { it.isNaN() },
            operatingMargin = financial?.let { data ->
                val sales = data.optDouble("sales").takeUnless { it.isNaN() || it == 0.0 }
                val op = data.optDouble("operating_profit").takeUnless { it.isNaN() }
                if (sales != null && op != null) op / sales * 100.0 else null
            },
            fcfMargin = financial?.let { data ->
                val sales = data.optDouble("sales").takeUnless { it.isNaN() || it == 0.0 }
                val ocf = data.optDouble("operating_cf").takeUnless { it.isNaN() }
                val icf = data.optDouble("investing_cf").takeUnless { it.isNaN() }
                if (sales != null && ocf != null && icf != null) (ocf + icf) / sales * 100.0 else null
            },
            dailyChange = if (change != null && changePercent != null) {
                String.format(Locale.JAPAN, "%+.2f (%+.2f%%)", change, changePercent)
            } else null,
            isPositive = change?.let { it >= 0.0 }
        )
    }

    private fun get(path: String): String {
        val connection = URL(BuildConfig.D1_API_BASE_URL + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
