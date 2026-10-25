package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.roundToInt

enum class BandPosition(val label: String, val digit: Int) {
    LOWER("下限寄り", 0),
    CENTER("中央", 1),
    UPPER("上限寄り", 2)
}

data class StockAnalysisFinding(
    val key: String,
    val label: String,
    val value: Double,
    val formattedValue: String,
    val bandLabel: String,
    val score: Int,
    val position: BandPosition,
    val text: String,
    val combinationDigit: Int
)

data class StockAnalysisNarrative(
    val score: Int?,
    val grade: String?,
    val title: String,
    val findings: List<StockAnalysisFinding>,
    val missingLabels: List<String>,
    val combinationIndex: Long?,
    val possibleCombinations: Long
) {
    val complete: Boolean get() = missingLabels.isEmpty()

    companion object {
        const val TOTAL_COMBINATIONS: Long = 373_669_453_125L // 45^7
    }
}

/** Deterministic 5-band x 9-position narratives for the seven backend stock-analysis metrics. */
object StockAnalysisNarrativeGenerator {
    private data class Band(val label: String, val score: Int, val explanation: String)
    private data class Rule(
        val key: String,
        val label: String,
        val definition: String,
        val unit: String,
        val weight: Int,
        val cutoffs: List<Double>,
        val ranges: List<Pair<Double, Double>>,
        val bands: List<Band>,
        val positionText: List<String>
    )

    private val positionExplanations = listOf(
        "この評価帯の下端に近い位置です。",
        "この評価帯の下端寄りに位置します。",
        "この評価帯の下側から中央へ近づく位置です。",
        "この評価帯の中央より下側に位置します。",
        "この評価帯の中央付近に位置します。",
        "この評価帯の中央より上側に位置します。",
        "この評価帯の中央から上側へ寄った位置です。",
        "この評価帯の上端寄りに位置します。",
        "この評価帯の上端に近い位置です。"
    )

    private val rules = listOf(
        Rule(
            key = "per", label = "PER", definition = "PERは株価が1株当たり利益の何倍で評価されているかを示します。", unit = "倍", weight = 15,
            cutoffs = listOf(10.0, 15.0, 20.0, 30.0),
            ranges = listOf(0.0 to 10.0, 10.0 to 15.0, 15.0 to 20.0, 20.0 to 30.0, 30.0 to 60.0),
            bands = listOf(
                Band("割安", 100, "利益に対する株価倍率が低い範囲です。"),
                Band("やや割安", 80, "利益に対する株価倍率がやや低い範囲です。"),
                Band("適正", 65, "利益に対する株価倍率が中位の範囲です。"),
                Band("やや割高", 45, "利益に対する株価倍率がやや高い範囲です。"),
                Band("割高", 25, "利益に対する株価倍率が高い範囲です。")
            ),
            positionText = positionExplanations
        ),
        Rule(
            key = "pbr", label = "PBR", definition = "PBRは株価が1株当たり純資産の何倍かを示し、純資産に対する市場評価を表します。", unit = "倍", weight = 10,
            cutoffs = listOf(0.8, 1.2, 2.0, 3.5),
            ranges = listOf(0.0 to 0.8, 0.8 to 1.2, 1.2 to 2.0, 2.0 to 3.5, 3.5 to 7.0),
            bands = listOf(
                Band("資産面で割安", 100, "純資産に対する株価倍率が低い範囲です。"),
                Band("割安", 85, "純資産に対する株価倍率がやや低い範囲です。"),
                Band("適正", 65, "純資産に対する株価倍率が中位の範囲です。"),
                Band("高め", 45, "純資産に対する株価倍率がやや高い範囲です。"),
                Band("高評価", 25, "純資産に対する株価倍率が高い範囲です。")
            ),
            positionText = positionExplanations
        ),
        Rule(
            key = "payout_ratio", label = "配当性向", definition = "配当性向は、当期利益に対する配当額の割合を示します。", unit = "%", weight = 20,
            cutoffs = listOf(30.0, 50.0, 70.0, 90.0),
            ranges = listOf(0.0 to 30.0, 30.0 to 50.0, 50.0 to 70.0, 70.0 to 90.0, 90.0 to 150.0),
            bands = listOf(
                Band("余力大", 85, "当期利益のうち配当に回す割合が小さい範囲です。"),
                Band("健全", 100, "当期利益と配当額の割合が中位の範囲です。"),
                Band("やや高め", 70, "当期利益に対する配当の割合がやや大きい範囲です。"),
                Band("高め", 45, "当期利益の大部分を配当に回す範囲です。"),
                Band("要警戒", 20, "配当額が当期利益に近い、または上回る範囲です。")
            ),
            positionText = positionExplanations
        ),
        Rule(
            key = "equity_ratio", label = "自己資本比率", definition = "自己資本比率は、総資本に占める自己資本の割合を示します。", unit = "%", weight = 15,
            cutoffs = listOf(15.0, 25.0, 40.0, 60.0),
            ranges = listOf(-20.0 to 15.0, 15.0 to 25.0, 25.0 to 40.0, 40.0 to 60.0, 60.0 to 100.0),
            bands = listOf(
                Band("低い", 25, "総資本に占める自己資本の割合が低い範囲です。"),
                Band("低め", 45, "総資本に占める自己資本の割合がやや低い範囲です。"),
                Band("標準", 65, "総資本に占める自己資本の割合が中位の範囲です。"),
                Band("高い", 85, "総資本に占める自己資本の割合がやや高い範囲です。"),
                Band("非常に高い", 100, "総資本に占める自己資本の割合が高い範囲です。")
            ),
            positionText = positionExplanations
        ),
        Rule(
            key = "operating_margin", label = "営業利益率", definition = "営業利益率は、売上高に占める営業利益の割合を示します。", unit = "%", weight = 15,
            cutoffs = listOf(2.0, 5.0, 10.0, 15.0),
            ranges = listOf(-20.0 to 2.0, 2.0 to 5.0, 5.0 to 10.0, 10.0 to 15.0, 15.0 to 30.0),
            bands = listOf(
                Band("低収益", 20, "売上高に占める営業利益の割合が低い範囲です。"),
                Band("低め", 45, "売上高に占める営業利益の割合がやや低い範囲です。"),
                Band("標準", 65, "売上高に占める営業利益の割合が中位の範囲です。"),
                Band("良好", 85, "売上高に占める営業利益の割合がやや高い範囲です。"),
                Band("高収益", 100, "売上高に占める営業利益の割合が高い範囲です。")
            ),
            positionText = positionExplanations
        ),
        Rule(
            key = "fcf_margin", label = "FCF余力", definition = "FCF余力は、売上高に対するフリーキャッシュフローの割合です。", unit = "%", weight = 15,
            cutoffs = listOf(-5.0, 0.0, 5.0, 10.0),
            ranges = listOf(-30.0 to -5.0, -5.0 to 0.0, 0.0 to 5.0, 5.0 to 10.0, 10.0 to 30.0),
            bands = listOf(
                Band("赤字", 20, "売上高に対するフリーキャッシュフローの割合が負の範囲です。"),
                Band("弱い", 40, "売上高に対するフリーキャッシュフローの割合が小さい範囲です。"),
                Band("確保", 65, "売上高に対するフリーキャッシュフローの割合が正の範囲です。"),
                Band("強い", 85, "売上高に対するフリーキャッシュフローの割合が中程度の範囲です。"),
                Band("非常に強い", 100, "売上高に対するフリーキャッシュフローの割合が大きい範囲です。")
            ),
            positionText = positionExplanations
        ),
        Rule(
            key = "dividend_growth_years", label = "連続増配年数", definition = "連続増配年数は、1株当たり配当が連続して増えた実績期間を示します。", unit = "年", weight = 10,
            cutoffs = listOf(1.0, 3.0, 5.0, 10.0),
            ranges = listOf(0.0 to 1.0, 1.0 to 3.0, 3.0 to 5.0, 5.0 to 10.0, 10.0 to 25.0),
            bands = listOf(
                Band("実績限定", 40, "連続増配年数が1年以下の範囲です。"),
                Band("短期実績", 55, "連続増配年数が1年超3年以下の範囲です。"),
                Band("実績あり", 70, "連続増配年数が3年超5年以下の範囲です。"),
                Band("増配継続", 85, "連続増配年数が5年超10年以下の範囲です。"),
                Band("長期増配", 100, "連続増配年数が10年を超える範囲です。")
            ),
            positionText = positionExplanations
        )
    )

    init {
        require(rules.size == 7 && rules.all { it.bands.size == 5 && it.ranges.size == 5 })
        require(rules.all { it.positionText.size == 9 })
    }

    fun generate(values: Map<String, Double?>): StockAnalysisNarrative {
        val findings = mutableListOf<StockAnalysisFinding>()
        val missing = mutableListOf<String>()
        var weightedScore = 0.0
        var totalWeight = 0
        var combinationIndex = 0L

        rules.forEach { rule ->
            val value = values[rule.key]?.takeIf(Double::isFinite)
            if (value == null) {
                missing += rule.label
                return@forEach
            }
            val bandIndex = rule.cutoffs.indexOfFirst { value <= it }.let { if (it == -1) 4 else it }
            val band = rule.bands[bandIndex]
            val range = rule.ranges[bandIndex]
            val fraction = ((value - range.first) / (range.second - range.first)).coerceIn(0.0, 1.0)
            val detailIndex = (fraction * 9.0).toInt().coerceIn(0, 8)
            val position = when (detailIndex / 3) {
                0 -> BandPosition.LOWER
                1 -> BandPosition.CENTER
                else -> BandPosition.UPPER
            }
            val digit = bandIndex * 9 + detailIndex
            val formatted = if (rule.key == "dividend_growth_years") {
                String.format(Locale.JAPAN, "%d%s", value.toInt(), rule.unit)
            } else {
                String.format(Locale.JAPAN, "%.2f%s", value, rule.unit)
            }
            findings += StockAnalysisFinding(
                key = rule.key,
                label = rule.label,
                value = value,
                formattedValue = formatted,
                bandLabel = band.label,
                score = band.score,
                position = position,
                text = "${rule.definition} ${band.explanation} ${rule.positionText[detailIndex]}",
                combinationDigit = digit
            )
            weightedScore += band.score * rule.weight
            totalWeight += rule.weight
            combinationIndex = combinationIndex * 45L + digit
        }

        val score = if (totalWeight == 0) null else (weightedScore / totalWeight).roundToInt()
        val grade = when {
            score == null -> null
            score >= 90 -> "S"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            else -> "D"
        }
        val title = when (grade) {
            "S" -> "非常に良好"
            "A" -> "良好"
            "B" -> "概ね良好"
            "C" -> "注意点あり"
            "D" -> "要点検"
            else -> "評価に必要なデータがありません"
        }
        val dimensions = findings.size
        val possible = if (dimensions == rules.size) StockAnalysisNarrative.TOTAL_COMBINATIONS else powerOf45(dimensions)
        return StockAnalysisNarrative(
            score = score,
            grade = grade,
            title = title,
            findings = findings,
            missingLabels = missing,
            combinationIndex = combinationIndex.takeIf { dimensions == rules.size },
            possibleCombinations = possible
        )
    }

    private fun powerOf45(exponent: Int): Long = (0 until exponent).fold(1L) { acc, _ -> acc * 45L }
}
