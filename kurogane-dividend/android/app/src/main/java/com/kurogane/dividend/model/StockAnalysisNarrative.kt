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
        const val TOTAL_COMBINATIONS: Long = 373_669_453_125L // Rating band/position combinations: 45^7
    }
}

/** Keeps 5-band x 9-position rating codes stable while adding evidence-dependent commentary. */
object StockAnalysisNarrativeGenerator {
    private enum class TextStyle { SUMMARY, DIVIDEND_CONTINUITY, EVALUATION_POSITION }
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

    private val positionBandExplanations = mapOf(
        "per" to listOf(
            "低い倍率は割安感がありますが、利益縮小への警戒も含み得ます。",
            "やや低めの倍率は利益期待が控えめな一方、景気による減益懸念も含み得ます。",
            "中位の評価で、株価の利益期待は極端ではありません。",
            "高めの倍率に成長期待が含まれ、未達なら評価が下がり得ます。",
            "期待が高く、利益未達時は株価評価が下がりやすい水準です。"
        ),
        "pbr" to listOf(
            "純資産を下回る評価ですが、資産の収益性への懸念もあり得ます。",
            "資産への評価は控えめでも、配当余力が大きいとは限りません。",
            "資産への評価は中位で、配当原資の十分さは別に確認します。",
            "市場は資産の収益力を評価していますが、配当支払力とは別です。",
            "資産への期待が高く、実際の収益が伴うか確かめます。"
        ),
        "payout_ratio" to listOf(
            "利益に対する配当負担は小さく、減益への余地を残す範囲です。",
            "還元と事業投資を両立しやすい配当負担の範囲です。",
            "利益の半分程度を配当に回し、現状の維持を支える水準です。",
            "利益の多くを配当に充て、減益で余裕が縮みやすい状態です。",
            "利益の大半またはそれ以上を配当に回し、負担が高い状態です。"
        ),
        "equity_ratio" to listOf(
            "自己資本が薄く、業績悪化時に負債負担が重くなりやすい範囲です。",
            "自己資本はやや少なめで、借入や利益変動への備えを見ます。",
            "標準的な厚みで、財務の耐性は中位の水準です。",
            "自己資本が厚く、損失を吸収する余地が比較的大きい状態です。",
            "自己資本は非常に厚く、財務面の備えを支える強みです。"
        ),
        "operating_margin" to listOf(
            "利益率は低めで、コスト上昇や売上減少が利益に響きやすい範囲です。",
            "利益率はやや薄く、売上の変化が本業利益に影響しやすい状態です。",
            "標準的な利益率で、本業の収益を確保している水準です。",
            "良好な利益率は、配当を支える収益力の一つです。",
            "高い利益率が強みですが、業種や好況の影響も確かめます。"
        ),
        "fcf_margin" to listOf(
            "投資後の現金が残りにくく、配当には手元資金も必要な範囲です。",
            "FCFは小さめで、投資負担により赤字化しやすい水準です。",
            "FCFは確保されていますが、配当余力に大きな余裕はありません。",
            "投資後に現金が残り、配当を支える資金面の強みです。",
            "大きなFCFがあり、投資後の配当余力を支える水準です。"
        ),
        "dividend_growth_years" to listOf(
            "増配履歴は短く、継続性を判断する材料が限られます。",
            "数年の実績は、還元方針の一貫性を示す一材料です。",
            "複数年の増配実績があり、還元姿勢を支える強みです。",
            "長めの履歴があり、複数の環境で配当を伸ばしています。",
            "長期の増配実績は強みですが、将来も続くとは限りません。"
        )
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

    fun generate(values: Map<String, Double?>, context: StockAnalysisContext = StockAnalysisContext()): StockAnalysisNarrative =
        generate(values, TextStyle.SUMMARY, context)

    fun generateDetailed(values: Map<String, Double?>, context: StockAnalysisContext = StockAnalysisContext()): StockAnalysisNarrative =
        generate(values, TextStyle.DIVIDEND_CONTINUITY, context)

    fun generatePositionDetailed(values: Map<String, Double?>, context: StockAnalysisContext = StockAnalysisContext()): StockAnalysisNarrative =
        generate(values, TextStyle.EVALUATION_POSITION, context)

    private fun generate(values: Map<String, Double?>, textStyle: TextStyle, context: StockAnalysisContext): StockAnalysisNarrative {
        val findings = mutableListOf<StockAnalysisFinding>()
        val missing = mutableListOf<String>()
        var weightedScore = 0.0
        var totalWeight = 0
        var combinationIndex = 0L

        rules.forEach { rule ->
            val value = values[rule.key]?.takeIf(Double::isFinite)
                ?.takeIf { rule.key != "dividend_growth_years" || it >= 0 }
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
            val findingText = StockMetricCommentary.comment(
                rule.key, value, positionBandExplanations.getValue(rule.key)[bandIndex], context,
                detailed = textStyle == TextStyle.DIVIDEND_CONTINUITY
            )
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

}
