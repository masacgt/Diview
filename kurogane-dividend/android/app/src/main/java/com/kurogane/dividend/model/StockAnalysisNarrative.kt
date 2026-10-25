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
        val detailedDefinition: String,
        val detailedExplanation: String,
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
            key = "per", label = "PER", definition = "PERは株価が1株当たり利益の何倍で評価されているかを示します。", detailedDefinition = "PER（株価収益率）は、株価を1株当たり利益（EPS）で割った倍率です。", detailedExplanation = "利益が一時的に増減すると、株価が変わらなくてもPERは動きます。市場の成長期待や事業リスクも株価に反映されるため、低い値だけで割安が確定するわけではなく、高い値だけで過度な期待とも断定できません。PERは利益に対する株価の倍率であり、配当利回りや投資資金の回収年数を直接示す指標ではありません。", unit = "倍", weight = 15,
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
            key = "pbr", label = "PBR", definition = "PBRは株価が1株当たり純資産の何倍かを示し、純資産に対する市場評価を表します。", detailedDefinition = "PBR（株価純資産倍率）は、株価を1株当たり純資産（BPS）で割った倍率です。", detailedExplanation = "純資産は会計上の資産から負債を差し引いた値を基礎とするため、保有資産をすぐに換金した場合の金額や将来の利益をそのまま表すものではありません。資産の収益性、含み損益、事業の成長性も市場評価に関わります。PBRが1倍を下回る場合も、帳簿上の純資産を株価が下回る関係を示すにとどまり、株価の割安さや資産価値の確実性を単独で保証しません。", unit = "倍", weight = 10,
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
            key = "payout_ratio", label = "配当性向", definition = "配当性向は、当期利益に対する配当額の割合を示します。", detailedDefinition = "配当性向は、対象期間の利益に対する配当額の割合です。100%は利益と配当額が同程度の関係を示します。", detailedExplanation = "100%超は対象期間の利益を配当額が上回る関係です。利益が小さい年や赤字の年は比率が大きくなったり、通常と異なる符号になったりします。配当性向は会計上の利益と配当の比率で、手元資金、営業キャッシュフロー、借入、将来の配当方針を直接表しません。単年度の値は利益の変動にも左右されます。", unit = "%", weight = 20,
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
            key = "equity_ratio", label = "自己資本比率", definition = "自己資本比率は、総資本に占める自己資本の割合を示します。", detailedDefinition = "自己資本比率は、総資本に対する自己資本の割合を百分率で表します。", detailedExplanation = "比率が高いほど、資産のうち自己資本で賄われる部分が大きい関係です。負債への依存度や財務構成を見る手掛かりになりますが、現金の保有額、返済能力、利益の安定性を直接測る値ではありません。設備や在庫を多く持つ業種と資産の軽い業種では通常の水準が異なるため、同じ比率でも会社の事業構造によって意味合いが変わります。", unit = "%", weight = 15,
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
            key = "operating_margin", label = "営業利益率", definition = "営業利益率は、売上高に占める営業利益の割合を示します。", detailedDefinition = "営業利益率は、営業利益を売上高で割った比率です。売上高100円当たりの営業利益額としても読めます。", detailedExplanation = "営業外損益や税金を反映する前の利益率なので、最終利益や配当額とは一致しません。販売価格、原材料費、人件費、製品構成、景気によって変化し、業種ごとの利益構造にも差があります。", unit = "%", weight = 15,
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
            key = "fcf_margin", label = "FCF余力", definition = "FCF余力は、売上高に対するフリーキャッシュフローの割合です。", detailedDefinition = "FCF余力は、フリーキャッシュフロー（FCF）を売上高に対する割合で表します。", detailedExplanation = "正の値は売上に対してFCFがプラス、負の値はマイナスの関係を示します。設備投資、運転資金、資産売却などの時期により年度ごとの変動が大きく、単年度の比率だけで継続的な現金創出力や配当原資を決めつけることはできません。", unit = "%", weight = 15,
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
            key = "dividend_growth_years", label = "連続増配年数", definition = "連続増配年数は、1株当たり配当が連続して増えた実績期間を示します。", detailedDefinition = "連続増配年数は、取得した配当履歴上で1株当たり配当が連続して増えた年数です。", detailedExplanation = "この値は配当の過去実績を年数で表したもので、増配率、配当性向、配当利回りとは異なる指標です。増配が続いた年数が長くても、今後の増配や配当維持が決まるわけではありません。比較できる履歴の範囲や期間区分に左右され、履歴が不足している場合は未評価として扱います。横ばいの年は増配年数に含まれませんが、減配しなかった年数とも別に数えます。", unit = "年", weight = 10,
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

    fun generate(values: Map<String, Double?>): StockAnalysisNarrative = generate(values, includeDetailedExplanation = false)

    fun generateDetailed(values: Map<String, Double?>): StockAnalysisNarrative = generate(values, includeDetailedExplanation = true)

    private fun generate(values: Map<String, Double?>, includeDetailedExplanation: Boolean): StockAnalysisNarrative {
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
            val findingText = if (includeDetailedExplanation) {
                val rangeText = describeBandRange(rule, bandIndex)
                val positionDetail = describePositionScale(rule, range, value, fraction, detailIndex)
                val boundaryDetail = describeBandBoundary(rule, bandIndex, value)
                "${rule.detailedDefinition} 今回の取得値は${formatted}です。設定基準では${rangeText}を「${band.label}」の評価帯とし、${band.explanation} ${rule.positionText[detailIndex]} $positionDetail $boundaryDetail ${rule.detailedExplanation}"
            } else {
                "${rule.definition} ${band.explanation} ${rule.positionText[detailIndex]}"
            }
            findings += StockAnalysisFinding(
                key = rule.key,
                label = rule.label,
                value = value,
                formattedValue = formatted,
                bandLabel = band.label,
                score = band.score,
                position = position,
                text = findingText,
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

    private fun describeBandRange(rule: Rule, bandIndex: Int): String {
        return when (bandIndex) {
            0 -> "${formatMetricValue(rule, rule.cutoffs.first())}以下"
            rule.bands.lastIndex -> "${formatMetricValue(rule, rule.cutoffs.last())}超"
            else -> "${formatMetricValue(rule, rule.cutoffs[bandIndex - 1])}超${formatMetricValue(rule, rule.cutoffs[bandIndex])}以下"
        }
    }

    private fun describePositionScale(
        rule: Rule,
        range: Pair<Double, Double>,
        value: Double,
        fraction: Double,
        detailIndex: Int
    ): String {
        val scale = "${formatMetricValue(rule, range.first)}から${formatMetricValue(rule, range.second)}"
        return when {
            value < range.first -> "位置表示の尺度は${scale}で、今回の値はその下限を下回るため、画面上は下端側に置かれます。"
            value > range.second -> "位置表示の尺度は${scale}ですが、今回の値はその上限を超えるため、画面上は上端側に置かれます。"
            else -> {
                val percent = (fraction * 100.0).roundToInt()
                "位置表示の尺度は${scale}です。同じ帯を9段階に分けると下から${detailIndex + 1}段階目、下端から約${percent}%の位置に当たります。"
            }
        }
    }

    private fun describeBandBoundary(rule: Rule, bandIndex: Int, value: Double): String {
        val boundary = rule.cutoffs.getOrNull(bandIndex)
        return if (boundary == null) {
            val lastBoundary = rule.cutoffs.last()
            "最終境界の${formatMetricValue(rule, lastBoundary)}を${formatMetricValue(rule, value - lastBoundary)}上回っています。"
        } else {
            val distance = boundary - value
            if (distance == 0.0) {
                "上側の境界値${formatMetricValue(rule, boundary)}に達しており、この数値は境界を含む評価帯に分類されます。"
            } else {
                "上側の境界値${formatMetricValue(rule, boundary)}までは${formatMetricValue(rule, distance)}の差があります。"
            }
        }
    }

    private fun formatMetricValue(rule: Rule, value: Double): String = if (rule.key == "dividend_growth_years") {
        String.format(Locale.JAPAN, "%d%s", value.toInt(), rule.unit)
    } else {
        String.format(Locale.JAPAN, "%.2f%s", value, rule.unit)
    }
}
