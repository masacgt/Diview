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
        val unit: String,
        val weight: Int,
        val cutoffs: List<Double>,
        val ranges: List<Pair<Double, Double>>,
        val bands: List<Band>,
        val positionText: List<String>
    )

    private val rules = listOf(
        Rule(
            key = "per", label = "PER", unit = "倍", weight = 15,
            cutoffs = listOf(10.0, 15.0, 20.0, 30.0),
            ranges = listOf(0.0 to 10.0, 10.0 to 15.0, 15.0 to 20.0, 20.0 to 30.0, 30.0 to 60.0),
            bands = listOf(
                Band("割安", 100, "利益に対する評価倍率は低い水準です。低PERの理由が一時的な利益増加でないかも確認してください。"),
                Band("やや割安", 80, "利益に対する株価評価は比較的抑えられています。同業他社や自社の過去水準も参考になります。"),
                Band("適正", 65, "利益に対しておおむね中立的な倍率です。利益成長や配当余力と合わせて判断してください。"),
                Band("やや割高", 45, "利益に対する評価は高めです。利益成長が現在の倍率を支えられるか確認が必要です。"),
                Band("割高", 25, "利益に対する評価倍率は高い水準です。成長期待が弱まった場合の調整にも注意してください。")
            ),
            positionText = listOf(
                "PER帯の始点に近く、同じ評価帯では倍率が低い側です。",
                "PER帯の前半にあり、利益に対する価格負担は比較的軽い位置です。",
                "PER帯の中央へ近づいており、帯域の中ほどに差しかかっています。",
                "PER帯の中央域に入り、評価倍率は中心値を下回る位置です。",
                "PER帯の中央付近で、利益に対する評価は基準値に近い水準です。",
                "PER帯の中央域にあり、評価倍率は中心値を上回っています。",
                "PER帯の後半に入り、利益成長への期待が評価に表れています。",
                "PER帯の上側にあり、現在の倍率を利益成長で支えられるかが焦点です。",
                "PER帯の終点に近く、次の評価帯との境界が近づいています。"
            )
        ),
        Rule(
            key = "pbr", label = "PBR", unit = "倍", weight = 10,
            cutoffs = listOf(0.8, 1.2, 2.0, 3.5),
            ranges = listOf(0.0 to 0.8, 0.8 to 1.2, 1.2 to 2.0, 2.0 to 3.5, 3.5 to 7.0),
            bands = listOf(
                Band("資産面で割安", 100, "純資産に対する評価は低い水準です。資産の質や収益力が低いために割安でないかも見てください。"),
                Band("割安", 85, "純資産に対する評価は比較的低めです。資本効率や利益の安定性が裏付けになります。"),
                Band("適正", 65, "純資産に対しておおむね標準的な評価です。ROEや利益成長と合わせて確認してください。"),
                Band("高め", 45, "純資産に対する評価は高めです。高い資本効率が継続できるかが判断材料になります。"),
                Band("高評価", 25, "純資産に対する評価倍率は高い水準です。収益性が落ちた際の倍率縮小に注意してください。")
            ),
            positionText = listOf(
                "PBR帯の始点に近く、純資産に対する倍率は低い側です。",
                "PBR帯の前半にあり、資産に対する株価評価は比較的抑えられています。",
                "PBR帯の中央へ近づき、純資産に対する倍率が帯域の中ほどにあります。",
                "PBR帯の中央域に入り、資産評価は中心値を下回る位置です。",
                "PBR帯の中央付近で、純資産に対する評価は帯域の基準に近い水準です。",
                "PBR帯の中央域にあり、純資産に対する倍率は中心値を上回っています。",
                "PBR帯の後半に入り、株価は純資産に対して高めに評価されています。",
                "PBR帯の上側にあり、高い資本効率が続くかが判断材料になります。",
                "PBR帯の終点に近く、次の倍率帯との境界が近づいています。"
            )
        ),
        Rule(
            key = "payout_ratio", label = "配当性向", unit = "%", weight = 20,
            cutoffs = listOf(30.0, 50.0, 70.0, 90.0),
            ranges = listOf(0.0 to 30.0, 30.0 to 50.0, 50.0 to 70.0, 70.0 to 90.0, 90.0 to 150.0),
            bands = listOf(
                Band("余力大", 85, "利益に対する配当負担は小さめです。配当余力に加え、株主還元方針も確認してください。"),
                Band("健全", 100, "利益と配当のバランスが取りやすく、継続配当の余地を確保しやすい範囲です。"),
                Band("やや高め", 70, "配当負担はやや高めですが、一定の利益余力があります。利益の安定性も確認してください。"),
                Band("高め", 45, "利益の多くを配当に回しています。利益が減った場合に配当を維持できるか注意が必要です。"),
                Band("要警戒", 20, "配当負担が非常に大きい水準です。一時利益や借入に依存していないか確認してください。")
            ),
            positionText = listOf(
                "配当性向帯の始点に近く、利益に対する配当負担は軽い側です。",
                "配当性向帯の前半にあり、利益のうち配当に回す割合は比較的低めです。",
                "配当性向帯の中央へ近づき、利益に対する配当負担が増しています。",
                "配当性向帯の中央域に入り、配当負担は中心値を下回る位置です。",
                "配当性向帯の中央付近で、利益と配当の比率は基準値に近い水準です。",
                "配当性向帯の中央域にあり、配当負担は中心値を上回っています。",
                "配当性向帯の後半に入り、利益が減った場合の配当余力に注意が必要です。",
                "配当性向帯の上側にあり、配当負担は次の注意水準に近づいています。",
                "配当性向帯の終点に近く、利益の変化が配当維持に影響しやすい水準です。"
            )
        ),
        Rule(
            key = "equity_ratio", label = "自己資本比率", unit = "%", weight = 15,
            cutoffs = listOf(15.0, 25.0, 40.0, 60.0),
            ranges = listOf(-20.0 to 15.0, 15.0 to 25.0, 25.0 to 40.0, 40.0 to 60.0, 60.0 to 100.0),
            bands = listOf(
                Band("低い", 25, "自己資本の厚みが薄い水準です。借入依存度、利払い、資金繰りを確認してください。"),
                Band("低め", 45, "自己資本の余裕は小さめです。景気悪化や金利上昇への耐性を点検してください。"),
                Band("標準", 65, "自己資本比率は標準的な範囲です。業種特性と負債構成も合わせて評価してください。"),
                Band("高い", 85, "自己資本が比較的厚く、財務の安定性を支えています。有利子負債の推移も確認しましょう。"),
                Band("非常に高い", 100, "自己資本の厚みが大きく、財務面の耐久力が期待できる水準です。業種ごとの適正値も考慮してください。")
            ),
            positionText = listOf(
                "自己資本比率帯の始点に近く、財務の厚みは低い側です。",
                "自己資本比率帯の前半にあり、自己資本の余裕はまだ小さめです。",
                "自己資本比率帯の中央へ近づき、負債とのバランスが改善しています。",
                "自己資本比率帯の中央域に入り、帯域の中心値を下回る位置です。",
                "自己資本比率帯の中央付近で、財務構成は同帯域の基準に近い水準です。",
                "自己資本比率帯の中央域にあり、自己資本の厚みは中心値を上回ります。",
                "自己資本比率帯の後半に入り、財務の安定性を支える余裕が増しています。",
                "自己資本比率帯の上側にあり、次の安定性評価帯が近づいています。",
                "自己資本比率帯の終点に近く、自己資本の厚みが大きい水準です。"
            )
        ),
        Rule(
            key = "operating_margin", label = "営業利益率", unit = "%", weight = 15,
            cutoffs = listOf(2.0, 5.0, 10.0, 15.0),
            ranges = listOf(-20.0 to 2.0, 2.0 to 5.0, 5.0 to 10.0, 10.0 to 15.0, 15.0 to 30.0),
            bands = listOf(
                Band("低収益", 20, "本業の利益率は低い水準です。構造的な低採算か、一時的な要因かを確認してください。"),
                Band("低め", 45, "本業の利益率は低めです。コスト上昇や売上減少への耐性を点検しましょう。"),
                Band("標準", 65, "営業利益率は標準的です。業界平均と数年の改善・悪化傾向が参考になります。"),
                Band("良好", 85, "本業の利益率は良好です。過去数年にわたって水準を維持できているか確認してください。"),
                Band("高収益", 100, "本業の利益率は高い水準です。競争優位性や高収益の持続性を確認してください。")
            ),
            positionText = listOf(
                "営業利益率帯の始点に近く、本業の採算は低い側です。",
                "営業利益率帯の前半にあり、売上から得る利益はまだ限られています。",
                "営業利益率帯の中央へ近づき、本業の採算は改善側にあります。",
                "営業利益率帯の中央域に入り、利益率は帯域の中心値を下回ります。",
                "営業利益率帯の中央付近で、本業の採算は同帯域の基準に近い水準です。",
                "営業利益率帯の中央域にあり、利益率は中心値を上回っています。",
                "営業利益率帯の後半に入り、売上に対する利益の割合は高まっています。",
                "営業利益率帯の上側にあり、次の収益性評価帯に近づいています。",
                "営業利益率帯の終点に近く、高い利益率を維持できるかが焦点です。"
            )
        ),
        Rule(
            key = "fcf_margin", label = "FCF余力", unit = "%", weight = 15,
            cutoffs = listOf(-5.0, 0.0, 5.0, 10.0),
            ranges = listOf(-30.0 to -5.0, -5.0 to 0.0, 0.0 to 5.0, 5.0 to 10.0, 10.0 to 30.0),
            bands = listOf(
                Band("赤字", 20, "フリーキャッシュフローは赤字です。配当や投資が借入・手元資金に依存していないか確認してください。"),
                Band("弱い", 40, "フリーキャッシュフローの余裕は弱めです。設備投資や運転資金の負担を確認しましょう。"),
                Band("確保", 65, "フリーキャッシュフローは黒字圏です。投資負担の変化で悪化しないか推移を見てください。"),
                Band("強い", 85, "現金創出力は良好です。株主還元と成長投資の両立が続くか確認してください。"),
                Band("非常に強い", 100, "売上に対する現金創出力は強い水準です。配当、投資、債務返済を支える余力があります。")
            ),
            positionText = listOf(
                "FCF余力帯の始点に近く、売上に対する現金創出は弱い側です。",
                "FCF余力帯の前半にあり、現金収支には引き続き注意が必要です。",
                "FCF余力帯の中央へ近づき、キャッシュ創出は持ち直しています。",
                "FCF余力帯の中央域に入り、現金余力は中心値を下回る水準です。",
                "FCF余力帯の中央付近で、売上から生む現金は基準に近い水準です。",
                "FCF余力帯の中央域で、現金余力は中心値を超える水準です。",
                "FCF余力帯の後半に入り、投資や還元に回せる余地が広がっています。",
                "FCF余力帯の上側にあり、次のキャッシュ余力帯に近づいています。",
                "FCF余力帯の終点に近く、現金創出力は帯域内で強い水準です。"
            )
        ),
        Rule(
            key = "dividend_growth_years", label = "連続増配年数", unit = "年", weight = 10,
            cutoffs = listOf(1.0, 3.0, 5.0, 10.0),
            ranges = listOf(0.0 to 1.0, 1.0 to 3.0, 3.0 to 5.0, 5.0 to 10.0, 10.0 to 25.0),
            bands = listOf(
                Band("実績限定", 40, "連続増配の実績は限定的です。配当方針と過去の減配履歴を合わせて確認してください。"),
                Band("短期実績", 55, "連続増配の実績が見られますが、長期的な継続性を判断するには今後の実績も必要です。"),
                Band("実績あり", 70, "複数年の増配実績があります。増配率と利益成長の釣り合いを確認してください。"),
                Band("増配継続", 85, "5年以上の連続増配実績があります。還元方針と利益・現金の裏付けを確認しましょう。"),
                Band("長期増配", 100, "10年以上の連続増配実績があります。長い実績に加えて今後の利益と現金が支えられるか確認してください。")
            ),
            positionText = listOf(
                "連続増配年数帯の始点に近く、継続実績は短い側です。",
                "連続増配年数帯の前半にあり、長期実績の評価にはまだ期間が必要です。",
                "連続増配年数帯の中央へ近づき、複数年の増配実績が積み上がっています。",
                "連続増配年数帯の中央域に入り、実績年数は節目の手前です。",
                "連続増配年数帯の中央付近で、同じ実績帯の基準に近い年数です。",
                "連続増配年数帯の中央域にあり、次の節目を超えた実績があります。",
                "連続増配年数帯の後半に入り、長期の継続実績に近づいています。",
                "連続増配年数帯の上側にあり、次の年数の節目が近づいています。",
                "連続増配年数帯の終点に近く、長期にわたる増配実績があります。"
            )
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
                text = "${rule.label}は${formatted}で「${band.label}」帯の${position.label}にあります。${rule.positionText[detailIndex]}${band.explanation}",
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
