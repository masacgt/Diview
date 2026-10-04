package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.roundToInt

enum class PortfolioBandPosition(val label: String, val digit: Int) {
    LOWER("下限寄り", 0),
    CENTER("中央", 1),
    UPPER("上限寄り", 2)
}

data class PortfolioDiagnosisFinding(
    val key: String,
    val label: String,
    val value: Double,
    val formattedValue: String,
    val bandLabel: String,
    val position: PortfolioBandPosition,
    val score: Int,
    val text: String,
    val combinationDigit: Int
)

data class PortfolioDiagnosis(
    val score: Int?,
    val grade: String,
    val title: String,
    val narrative: String,
    val findings: List<PortfolioDiagnosisFinding>,
    val missingLabels: List<String>,
    val combinationIndex: Long?,
    val possibleCombinations: Long,
    val dividendGrowthLabel: String,
    val dividendRiskLabel: String
) {
    val complete: Boolean get() = missingLabels.isEmpty()

    companion object {
        const val TOTAL_COMBINATIONS: Long = 170_859_375L // 15^7
    }
}

/** Builds a portfolio narrative from seven portfolio-weighted indicators. */
object PortfolioDiagnosisGenerator {
    private data class Band(
        val label: String,
        val score: Int,
        val explanation: String
    )

    private data class MetricRule(
        val key: String,
        val label: String,
        val unit: String,
        val weight: Int,
        val upperBounds: List<Double>,
        val bands: List<Band>,
        val positionText: List<String>
    )

    private data class MetricValue(
        val value: Double?,
        val formatted: String,
        val detail: String
    )

    private val rules = listOf(
        MetricRule(
            key = "largest_holding_weight", label = "評価額比率", unit = "%", weight = 20,
            upperBounds = listOf(10.0, 20.0, 30.0, 45.0),
            bands = listOf(
                Band("分散の効いた水準", 100, "一銘柄への偏りは小さく、個別銘柄の値動きが全体に及ぼす影響を抑えやすい構成です。"),
                Band("偏りの小さい水準", 85, "最大銘柄の比率は抑えられています。銘柄ごとの値動きが資産全体を左右しにくい状態です。"),
                Band("一定の偏りがある水準", 65, "最大銘柄の値動きが資産全体に影響しやすい比率です。"),
                Band("集中が目立つ水準", 40, "一銘柄の株価変動がポートフォリオ全体に強く表れやすい構成です。"),
                Band("集中の強い水準", 20, "資産評価額が一銘柄に大きく依存しています。")
            ),
            positionText = listOf(
                "最大銘柄の比率はこの区分の下側にあります。",
                "集中度はこの区分の中央付近を示しています。",
                "集中度は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "top_three_dividend_share", label = "年間配当比率", unit = "%", weight = 15,
            upperBounds = listOf(25.0, 40.0, 55.0, 70.0),
            bands = listOf(
                Band("配当源が分散した水準", 100, "配当収入は複数銘柄に分かれ、上位銘柄への依存は小さい状態です。"),
                Band("配当の偏りが小さい水準", 85, "上位銘柄の比率は比較的低く、配当源を分散できています。"),
                Band("一定の配当集中がある水準", 65, "上位銘柄の配当が全体を左右する余地があります。"),
                Band("上位銘柄への依存が大きい水準", 40, "上位銘柄の配当が減ると、年間受取額への影響が大きくなりやすい構成です。"),
                Band("上位銘柄への依存が強い水準", 20, "年間配当の多くを上位3銘柄に頼っています。")
            ),
            positionText = listOf(
                "配当集中度はこの区分の下限寄りにあります。",
                "配当集中度は中央付近を示しています。",
                "配当集中度は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "largest_sector_weight", label = "評価額比率", unit = "%", weight = 15,
            upperBounds = listOf(20.0, 35.0, 50.0, 65.0),
            bands = listOf(
                Band("業種分散が効いた水準", 100, "評価額は複数業種に分かれ、特定業種への偏りを抑えています。"),
                Band("業種の偏りが小さい水準", 85, "最大業種の比率は比較的低く、業種分散が保たれています。"),
                Band("一定の業種偏りがある水準", 65, "最大業種の動向が資産全体に影響しやすい構成です。"),
                Band("業種集中が目立つ水準", 40, "最大業種の業績や市況が変化すると、複数銘柄が同時に影響を受ける可能性があります。"),
                Band("業種集中の強い水準", 20, "評価額が一つの業種に大きく偏っています。")
            ),
            positionText = listOf(
                "最大業種の比率は区分の下側にあります。",
                "業種の偏りは区分の中央付近を示しています。",
                "最大業種の比率は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "largest_market_weight", label = "評価額比率", unit = "%", weight = 10,
            upperBounds = listOf(55.0, 70.0, 85.0, 95.0),
            bands = listOf(
                Band("複数の登録区分に分かれた水準", 100, "保有データ上、日本株・米国株・投資信託など複数の区分に資産が分かれています。"),
                Band("登録区分の偏りが小さい水準", 85, "複数の区分を保有し、最大区分への偏りも抑えられています。"),
                Band("一定の登録区分に偏りがある水準", 65, "最大区分が評価額の中心ですが、ほかの区分にも資産があります。"),
                Band("一つの登録区分への集中が目立つ水準", 40, "評価額の大半が一つの区分にあります。投資信託の投資先まではこの分類に含めていません。"),
                Band("一つの登録区分に強く集中した水準", 20, "評価額のほとんどが一つの区分にあります。投資信託の投資先までは別に集計していません。")
            ),
            positionText = listOf(
                "登録区分の比率はこの帯域の下側にあります。",
                "登録区分の比率はこの帯域の中央付近を示しています。",
                "登録区分の比率は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "healthy_payout_share", label = "配当性向70%以下の年間配当比率", unit = "%", weight = 15,
            upperBounds = listOf(20.0, 40.0, 60.0, 80.0),
            bands = listOf(
                Band("低い水準", 25, "利益に対する配当負担が小さい銘柄から得る配当は、ポートフォリオ内で少なめです。"),
                Band("やや低い水準", 45, "年間配当の多くは、配当性向が70%を超える銘柄から得ています。"),
                Band("標準的な水準", 65, "年間配当は、配当性向70%以下と70%超の銘柄の両方から得ています。"),
                Band("高い水準", 85, "年間配当の多くは、配当性向70%以下の銘柄から得ています。"),
                Band("非常に高い水準", 100, "年間配当の大半は、配当性向70%以下の銘柄から得ています。")
            ),
            positionText = listOf(
                "配当性向70%以下の銘柄から得る年間配当比率は区分の下側にあります。",
                "配当性向70%以下の年間配当比率は中央付近を示しています。",
                "配当性向70%以下の銘柄から得る年間配当比率は次の評価区分に近づいています。"
            )
        ),
        MetricRule(
            key = "weighted_dividend_history", label = "配当履歴の配当額加重平均", unit = "年", weight = 15,
            upperBounds = listOf(1.0, 3.0, 5.0, 10.0),
            bands = listOf(
                Band("短い水準", 30, "保有配当額で重み付けした増配・非減配の実績年数は短めです。"),
                Band("やや短い水準", 50, "増配・非減配の実績は積み上がりつつありますが、確認できる期間はまだ限られます。"),
                Band("標準的な水準", 70, "一定期間の増配・非減配実績を確認できます。"),
                Band("長い水準", 85, "保有配当額の多くが、長期の増配・非減配実績を持つ銘柄に由来します。"),
                Band("非常に長い水準", 100, "長期にわたる増配・非減配実績が、ポートフォリオの配当を支えています。")
            ),
            positionText = listOf(
                "配当履歴の平均年数はこの帯域の下側にあります。",
                "平均年数はこの帯域の中央付近を示しています。",
                "平均年数は次の実績区分に近づいています。"
            )
        ),
        MetricRule(
            key = "largest_monthly_dividend_share", label = "最大月集中度", unit = "%", weight = 10,
            upperBounds = listOf(15.0, 25.0, 35.0, 50.0),
            bands = listOf(
                Band("月別分散が効いた水準", 100, "年間配当は複数の月に分かれ、特定月への集中は小さい状態です。"),
                Band("月別の偏りが小さい水準", 85, "配当月の偏りは比較的小さく、受取時期を分散できています。"),
                Band("一定の月別偏りがある水準", 65, "配当が集まる月があり、月ごとの受取額には差があります。"),
                Band("月別集中が目立つ水準", 40, "年間配当の多くが限られた月に集中しています。"),
                Band("月別集中の強い水準", 20, "最大月の配当額が大きく、月ごとの受取額に偏りがあります。")
            ),
            positionText = listOf(
                "最大月の割合はこの区分の下限寄りにあります。",
                "月別の配当集中度は区分の中央付近を示しています。",
                "最大月の割合は次の区分に近づいています。"
            )
        )
    )

    fun generate(holdings: List<Holding>): PortfolioDiagnosis {
        if (holdings.isEmpty()) {
            return PortfolioDiagnosis(
                score = null,
                grade = "-",
                title = "保有銘柄なし",
                narrative = "保有銘柄が登録されていないため、ポートフォリオを評価できません。銘柄を登録すると、資産配分と配当構成を集計します。",
                findings = emptyList(),
                missingLabels = rules.map { it.label },
                combinationIndex = null,
                possibleCombinations = 0L,
                dividendGrowthLabel = "評価対象なし",
                dividendRiskLabel = "評価対象なし"
            )
        }

        val totalValue = holdings.sumOf { it.totalValuation.coerceAtLeast(0.0) }
        val dividendHoldings = holdings.filter { it.annualDividend > 0.0 }
        val totalDividend = dividendHoldings.sumOf { it.annualDividend }

        val values = mapOf(
            "largest_holding_weight" to percentageValue(
                if (totalValue > 0) holdings.maxOf { it.totalValuation.coerceAtLeast(0.0) } / totalValue * 100 else null,
                holdings.maxByOrNull { it.totalValuation.coerceAtLeast(0.0) }?.name ?: "最大銘柄"
            ),
            "top_three_dividend_share" to percentageValue(
                if (totalDividend > 0) dividendHoldings.sortedByDescending { it.annualDividend }.take(3).sumOf { it.annualDividend } / totalDividend * 100 else null,
                "上位3銘柄"
            ),
            "largest_sector_weight" to percentageValue(
                largestGroupShare(holdings, totalValue) { it.sector.trim().ifBlank { "業種未登録" } },
                holdings.groupBy { it.sector.trim().ifBlank { "業種未登録" } }.maxByOrNull { (_, group) -> group.sumOf { it.totalValuation.coerceAtLeast(0.0) } }?.key ?: "業種未登録"
            ),
            "largest_market_weight" to percentageValue(
                largestGroupShare(holdings, totalValue) { marketGroup(it) },
                holdings.groupBy(::marketGroup).maxByOrNull { (_, group) -> group.sumOf { it.totalValuation.coerceAtLeast(0.0) } }?.key ?: "市場区分"
            ),
            "healthy_payout_share" to percentageValue(
                if (totalDividend > 0) dividendHoldings.filter { getStockProfile(it.code).payoutRatio <= HEALTHY_PAYOUT_RATIO_MAX }
                    .sumOf { it.annualDividend } / totalDividend * 100 else null,
                ""
            ),
            "weighted_dividend_history" to yearValue(dividendHoldings, totalDividend),
            "largest_monthly_dividend_share" to monthlyValue(dividendHoldings, totalDividend)
        )

        var combinationIndex = 0L
        var evaluatedCount = 0
        val findings = rules.mapIndexedNotNull { index, rule ->
            val metric = values[rule.key] ?: return@mapIndexedNotNull null
            val value = metric.value ?: return@mapIndexedNotNull null
            val bandIndex = rule.upperBounds.indexOfFirst { value < it }.let { if (it < 0) rule.bands.lastIndex else it }
            val lower = if (bandIndex == 0) 0.0 else rule.upperBounds[bandIndex - 1]
            val upper = if (bandIndex < rule.upperBounds.size) rule.upperBounds[bandIndex] else 100.0
            val positionIndex = (((value - lower) / (upper - lower).coerceAtLeast(0.0001)) * 3.0).toInt().coerceIn(0, 2)
            val position = PortfolioBandPosition.entries[positionIndex]
            val band = rule.bands[bandIndex]
            val detail = metric.detail.takeIf(String::isNotBlank)?.let { "${it}の" }.orEmpty()
            val text = "${detail}${rule.label}は${metric.formatted}で、${band.label}です。${band.explanation}${rule.positionText[positionIndex]}"
            val digit = bandIndex * 3 + position.digit
            combinationIndex = combinationIndex * 15L + digit
            evaluatedCount++
            PortfolioDiagnosisFinding(
                key = rule.key,
                label = rule.label,
                value = value,
                formattedValue = metric.formatted,
                bandLabel = band.label,
                position = position,
                score = band.score,
                text = text,
                combinationDigit = digit
            )
        }

        val evaluatedKeys = findings.mapTo(mutableSetOf()) { it.key }
        val missingLabels = rules.filterNot { it.key in evaluatedKeys }.map { it.label }
        val score = if (findings.isEmpty()) null else {
            val scoreByKey = findings.associate { it.key to it.score }
            val activeWeight = rules.filter { it.key in scoreByKey }.sumOf { it.weight }
            if (activeWeight == 0) null else rules.sumOf { rule -> (scoreByKey[rule.key] ?: 0) * rule.weight } / activeWeight
        }
        val grade = when {
            score == null -> "-"
            score >= 90 -> "S"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            else -> "D"
        }
        val title = when (grade) {
            "S" -> "非常に健全"
            "A" -> "健全"
            "B" -> "概ね良好"
            "C" -> "要点検"
            "D" -> "見直し推奨"
            else -> "判定不可"
        }
        val findingGroups = listOf(
            setOf("largest_holding_weight", "top_three_dividend_share", "largest_sector_weight", "largest_market_weight"),
            setOf("healthy_payout_share", "weighted_dividend_history"),
            setOf("largest_monthly_dividend_share")
        )
        val paragraphs = findingGroups.mapNotNull { keys ->
            findings.filter { it.key in keys }.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.text }
        }
        val weakest = findings.minByOrNull { it.score }
        val recommendation = weakest?.let(::recommendationFor)
        val narrative = buildList {
            addAll(paragraphs)
            if (recommendation != null) add(recommendation)
            if (missingLabels.isNotEmpty()) add("${missingLabels.joinToString("・")}はデータを確認できず、今回の評価に含めていません。")
        }.joinToString("\n\n")
        val growthYears = values["weighted_dividend_history"]?.value
        val healthyShare = values["healthy_payout_share"]?.value
        val dividendGrowthLabel = growthYears?.let { "配当額加重平均 ${format(it, 1)}年" } ?: "評価対象なし"
        val dividendRiskLabel = when {
            healthyShare == null -> "判定材料なし"
            healthyShare >= 80.0 && (growthYears ?: 0.0) >= 10.0 -> "低め"
            healthyShare >= 60.0 && (growthYears ?: 0.0) >= 5.0 -> "やや低め"
            healthyShare >= 40.0 -> "標準"
            healthyShare >= 20.0 -> "やや高め"
            else -> "高め"
        }

        return PortfolioDiagnosis(
            score = score,
            grade = grade,
            title = title,
            narrative = narrative,
            findings = findings,
            missingLabels = missingLabels,
            combinationIndex = combinationIndex.takeIf { evaluatedCount == rules.size },
            possibleCombinations = 15.0.pow(evaluatedCount).toLong(),
            dividendGrowthLabel = dividendGrowthLabel,
            dividendRiskLabel = dividendRiskLabel
        )
    }

    private fun percentageValue(value: Double?, detail: String) = MetricValue(
        value = value?.coerceIn(0.0, 100.0),
        formatted = value?.let { "${format(it, 1)}%" } ?: "",
        detail = detail
    )

    private fun yearValue(dividendHoldings: List<Holding>, totalDividend: Double): MetricValue {
        if (totalDividend <= 0.0) return MetricValue(null, "", "")
        val weightedYears = dividendHoldings.sumOf { holding ->
            val profile = getStockProfile(holding.code)
            val years = ((profile.noCutYears + profile.consecutiveYears) / 2.0).coerceAtLeast(0.0)
            holding.annualDividend * years
        } / totalDividend
        return MetricValue(weightedYears, "${format(weightedYears, 1)}年", "")
    }

    private fun largestGroupShare(
        holdings: List<Holding>,
        totalValue: Double,
        groupBy: (Holding) -> String
    ): Double? {
        if (totalValue <= 0.0) return null
        return holdings.groupBy(groupBy).values.maxOfOrNull { group ->
            group.sumOf { it.totalValuation.coerceAtLeast(0.0) }
        }?.div(totalValue)?.times(100.0)
    }

    private fun monthlyConcentration(holdings: List<Holding>, totalDividend: Double): Double? {
        if (totalDividend <= 0.0) return null
        val monthlyAmounts = DoubleArray(12)
        var coveredDividend = 0.0
        holdings.forEach { holding ->
            val months = holding.dividendMonths.distinct().filter { it in 1..12 }
            if (months.isNotEmpty()) {
                val monthlyAmount = holding.annualDividend / months.size
                months.forEach { month -> monthlyAmounts[month - 1] += monthlyAmount }
                coveredDividend += holding.annualDividend
            }
        }
        if (coveredDividend <= 0.0) return null
        return monthlyAmounts.maxOrNull()?.div(coveredDividend)?.times(100.0)
    }

    private fun monthlyValue(holdings: List<Holding>, totalDividend: Double): MetricValue {
        if (totalDividend <= 0.0) return MetricValue(null, "", "")
        val coveredDividend = holdings.filter { holding ->
            holding.dividendMonths.distinct().any { it in 1..12 }
        }.sumOf { it.annualDividend }
        val concentration = monthlyConcentration(holdings, totalDividend)
        if (concentration == null || coveredDividend <= 0.0) return MetricValue(null, "", "")
        val coverage = coveredDividend / totalDividend * 100.0
        return MetricValue(
            value = concentration,
            formatted = "${format(concentration, 1)}%",
            detail = "配当月登録分（年間配当の${format(coverage, 1)}%）"
        )
    }

    private fun marketGroup(holding: Holding): String = when (holding.category) {
        Category.JAPAN -> "日本株"
        Category.US -> "米国株"
        Category.FUND -> if (holding.currency == "$") "外貨建て投資信託" else "投資信託"
    }

    private fun recommendationFor(finding: PortfolioDiagnosisFinding): String = when (finding.key) {
        "largest_holding_weight" -> "次の買い増しでは、最大銘柄以外に資金を振り分けると、銘柄ごとの値動きの影響を抑えられます。"
        "top_three_dividend_share" -> "配当収入の偏りを抑えるには、上位3銘柄以外への買い増しも選択肢です。"
        "largest_sector_weight" -> "業種の偏りを抑えるには、最大業種以外の銘柄を買い増し候補にできます。"
        "largest_market_weight" -> "地域や通貨を分けたい場合は、現在比率の低い市場区分を候補にできます。"
        "healthy_payout_share" -> "配当性向が高い銘柄は、利益の推移と配当方針を確認してから買い増しを判断してください。"
        "weighted_dividend_history" -> "増配・非減配の年数だけでなく、配当性向や利益の推移も銘柄ごとに確認してください。"
        else -> "月ごとの受取額を平準化したい場合は、配当月が異なる銘柄も買い増し候補になります。"
    }

    private fun format(value: Double, digits: Int): String = String.format(Locale.JAPAN, "%.${digits}f", value)

    private fun Double.pow(exponent: Int): Double = Math.pow(this, exponent.toDouble())

    private const val HEALTHY_PAYOUT_RATIO_MAX = 70.0
}
