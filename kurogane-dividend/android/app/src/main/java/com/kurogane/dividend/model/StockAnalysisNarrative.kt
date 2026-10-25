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

    private val dividendContinuityExplanations = mapOf(
        "per" to listOf(
            "PERが低く株価に織り込まれた利益期待は控えめですが、利益縮小への懸念が反映される場合もあり、配当の強さとは言えず、PERは利益に対する株価倍率で配当原資や支払余力を直接測らないため、利益推移、配当性向、営業キャッシュフローも確認します。特に景気で利益が振れる企業は、好況期の値だけで判断しないことが大切です。",
            "PERはやや低めで成長期待は控えめですが、利益見通しの弱さを市場が織り込んでいる可能性もあり、PERは配当原資や支払余力を直接示さないため、利益推移、配当性向、営業キャッシュフローを見て安定性を判断します。利益が景気で変動する企業では、過去の好業績にも注意が必要です。",
            "PERは中位で株価の利益期待は極端ではありませんが、利益が安定して配当を支えられるかは別に確認が必要で、PERは配当原資や支払余力を直接示さないため、利益推移、配当性向、営業キャッシュフローも合わせて判断します。特別利益を除いた本業の傾向と、利益の変動幅も確認します。",
            "PERは高めで将来の利益成長への期待が株価に含まれ、期待を下回る利益が続けば評価が下がる可能性もあり、PERは配当原資や支払余力を測らないため、利益の実績、配当性向、営業キャッシュフローを確認します。成長投資と配当の両立状況、利益の達成度や予想の確かさも見ておきます。",
            "PERは高く利益成長への期待が強く織り込まれており、利益が伸びない局面では株価評価が見直されやすく、PERだけでは配当を続ける力は分からないため、利益推移、配当性向、営業キャッシュフローを確認します。期待先行か実績を伴う成長か、複数年の業績と計画で見極めます。"
        ),
        "pbr" to listOf(
            "PBRが低く株価は帳簿上の純資産を下回りますが、資産の収益性や含み損への懸念が反映される場合もあり、PBRは市場評価で現金残高や配当支払力を示さないため、利益、キャッシュフロー、負債と資産の中身も確認します。資産が利益や現金を生んでいるか、換金性と収入の継続性も見ます。",
            "PBRは低めで純資産に対する株価評価は控えめですが、その差が配当余力の大きさを示すとは限らず、PBRは現金残高や配当支払力を直接示さないため、利益、キャッシュフロー、負債と資産の中身も合わせて判断します。含み損や遊休資産、資産の換金性と質、回収可能性にも注意します。",
            "PBRは中位で純資産に対する株価評価は極端ではありませんが、配当を賄う利益や現金があるかは別の指標で見る必要があり、PBRは配当支払力を示さないため、利益、キャッシュフロー、負債も合わせて確認します。資産の構成や収益への貢献、換金性と質、現金化のしやすさも確認します。",
            "PBRは高めで市場が資産の収益性や成長を評価している可能性がありますが、評価の高さが配当原資の確保を意味するわけではなく、利益、キャッシュフロー、負債と資産の中身を見て配当支払力を別に確認します。期待が利益に結び付くか、資産が稼ぐ力と収益性、利益の継続性も見ます。",
            "PBRは高く将来収益への期待が株価に含まれる場合がありますが、期待を下回ると株価評価が変わりやすく、PBRは配当支払力を示さないため、利益、キャッシュフロー、負債と資産の中身も確認します。資産価値だけで配当の持続性は判断できず、資産の質や収益力、負債との均衡も見ます。"
        ),
        "payout_ratio" to listOf(
            "配当性向が低く利益の多くを社内に残せるため、減益時にも配当を維持する余地を確保しやすい状態です。利益は年度で変動し手元資金や将来方針は示さないため、複数年の利益推移と営業キャッシュフロー、特別損益の影響も確認します。",
            "配当性向は抑えめで利益の一部を再投資や内部留保に回せるため、業績変動時にも配当余地が比較的残ります。利益は年度で変動し現金や将来方針までは示さないため、複数年の利益推移と営業キャッシュフロー、利益の質も確認します。",
            "配当性向は中位で利益と配当を両立している水準ですが、大幅な減益が起きると維持余力が縮む可能性があります。単年度の値だけでは手元資金を把握できないため、利益推移と営業キャッシュフローに加え、投資や借入返済の負担も確認します。",
            "利益の多くを配当に回しており、利益が落ちると配当維持に必要な余裕が小さくなるおそれがあります。営業キャッシュフロー、手元資金、負債負担や配当方針も合わせて、利益変動時の支えと投資資金の必要額を複数年で確認します。",
            "配当が利益に近い、または上回る水準で、利益面の余裕は限られ、減益が続くと配当負担が重くなり得ます。単年度利益だけで結論を出さず、営業キャッシュフロー、手元資金、借入返済、配当方針の変化や利益の一時要因も確認します。"
        ),
        "equity_ratio" to listOf(
            "自己資本が薄く負債への依存が大きいため、業績悪化や借入負担の増加が配当を続ける余地を狭めることがあります。比率だけでは現金残高や返済期限、利益の安定性は分からないため、負債、営業キャッシュフロー、現預金の水準も合わせて見ます。",
            "自己資本の厚みは控えめで、外部資金への依存が続くと業績変動時に配当余力が影響を受けやすい構成です。比率だけでは現金残高や返済期限は分からないため、負債、営業キャッシュフロー、現預金と業種特性も確認します。",
            "自己資本比率は中位で財務構成に一定の安定感がありますが、配当維持には利益と現金収支も必要です。比率だけでは現金残高や返済期限は分からないため、負債、営業キャッシュフロー、現預金と業種特性も見て、資金繰りを確かめます。",
            "自己資本が厚めで負債に頼り過ぎない構成は、業績変動時に配当を支える財務上の余地になります。比率だけでは現金残高や返済期限を示さないため、負債、営業キャッシュフロー、現預金と業種特性も確認し、返済余力を見ます。",
            "自己資本が厚く財務面の耐性は配当継続を支える一材料ですが、現金や返済負担まで表すものではありません。負債と営業キャッシュフロー、現預金、業種特性を合わせ、配当を支える資金が続くか、借入返済に無理がないか確認します。"
        ),
        "operating_margin" to listOf(
            "本業の利益率が低く、売上減やコスト上昇が続くと配当を支える利益が細りやすい状態です。営業利益率は最終利益や配当用の現金とは一致しないため、営業キャッシュフロー、投資負担、赤字になりにくい売上構成も確認します。",
            "本業の収益余力は大きくなく、コスト増や需要減への耐性が限られる可能性があります。営業利益率は最終利益や配当用の現金とは一致しないため、営業キャッシュフロー、投資負担、利益率の複数年推移と利益の変動幅も確認します。",
            "本業の利益が配当を支える基礎になっていますが、利益率は景気や費用の変化で低下することがあります。営業利益率は最終利益や配当用の現金とは一致しないため、営業キャッシュフロー、投資負担、過去の変動幅も確認します。",
            "本業の利益率は比較的高く、コスト上昇を吸収できれば配当原資を保ちやすい状態です。利益率の持続性に加え、最終利益、営業キャッシュフロー、投資負担と一時的な価格要因、過去の利益変動や安定度、利益変動への耐性も確認します。",
            "本業の収益力は配当を支える材料で、高い利益率が続くか、単年度の特殊要因でないかを見ます。営業利益率は配当用の現金とは一致しないため、営業キャッシュフロー、投資負担、需要やコストの変化、利益の複数年推移も確認します。"
        ),
        "fcf_margin" to listOf(
            "FCFが大きくマイナスで、投資後の内部資金だけでは配当を賄えない可能性があります。設備投資や運転資金の時期で変動するため、複数年の推移、投資計画、資金不足を補う借入や現預金、配当負担と投資の一時性も見ます。",
            "FCFは小幅なマイナスで、直近は投資後の現金が不足しており、続く場合は手元資金や利益の補完が必要です。設備投資や運転資金の時期で変動するため、複数年の推移と投資計画、配当後の資金残高や借入、返済予定も見ます。",
            "FCFはプラスで配当に回せる現金が残っていますが、余裕は大きくなく、投資増で細る可能性があります。設備投資や運転資金の時期で変動するため、複数年の推移、投資計画、配当後に残る現金と借入負担も確認します。",
            "投資後にもFCFが残り、配当を内部資金で支えやすい状態です。設備更新や運転資金の変化で水準が変わるため、複数年の推移、投資計画、営業キャッシュフローの安定性、利益との整合性や配当後の余力、利益と配当のバランスも見ます。",
            "FCFが売上に対して大きく、投資後の資金創出は配当を支える強みです。資産売却や運転資金の一時変動で膨らむ場合があるため、複数年の推移、投資の必要額、営業キャッシュフローと配当後の残高、創出力の継続性も見ます。"
        ),
        "dividend_growth_years" to listOf(
            "連続増配の実績が短く、増配を続ける力はまだ判断しにくい状況で、景気後退時の維持実績も読み取れないため、利益と現金収支を確認し、配当額や配当性向の変化も追います。過去の年数は将来の増配や減配回避を保証しません。",
            "数年の増配実績は配当方針の一貫性を示す手掛かりですが、長期の景気変動を乗り越えた実績とは限らず、利益と現金収支、配当額や配当性向の推移を確認します。過去の年数は将来の増配や減配回避を保証せず、決算ごとの増配幅も見ます。",
            "複数年の増配は、利益成長を配当に反映してきた可能性を示す材料ですが、今後も同じ余力が続くとは限らないため、利益と現金収支、配当性向や配当額の推移を確認します。過去の年数は将来の増配や減配回避を保証しません。",
            "長めの増配履歴は、配当を継続して引き上げてきた実績として支えになります。利益や現金収支が弱まれば余力も変わるため、減配の有無や配当性向、配当額の推移も見て、将来の増配と減配への耐性、配当額の増え方を判断します。",
            "長期の増配実績は、複数の環境下で配当を伸ばした履歴として強みです。利益と現金収支が続くかは別途確認が必要で、過去の年数だけでは将来の増配や減配回避を保証しないため、配当性向、配当額、直近の利益も見ます。"
        )
    )

    private val nonPositiveValuationExplanations = mapOf(
        "per" to "PERが0以下で、赤字や一時要因により通常の倍率比較が難しい可能性があり、配当原資や支払余力を直接示さないため、利益推移、配当性向、営業キャッシュフローで確認します。単年の値だけでは継続力を判断できず、特別損益を含む利益の中身や回復の見通し、配当方針も確かめます。",
        "pbr" to "PBRが0以下で、負の純資産や一時要因の影響により通常の倍率比較が難しい可能性があり、現金残高や配当支払力を示さないため、利益、キャッシュフロー、負債を合わせて確認します。資産の評価や収益への貢献、換金性、含み損の有無、事業継続への影響と回収可能性も確かめます。",
        "payout_ratio" to "配当性向が負の値で、赤字や利益の符号が影響し、通常の配当余力を比率から読み取れない可能性があるため、利益、実際の配当額、営業キャッシュフローを個別に確認します。低い値だけで安全とは判断できず、特別損益も見ます。"
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
                nonPositiveValuationExplanations[rule.key]?.takeIf {
                    value <= 0.0 && (rule.key == "per" || rule.key == "pbr" || rule.key == "payout_ratio")
                }
                    ?: dividendContinuityExplanations.getValue(rule.key)[bandIndex]
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

    private fun formatMetricValue(rule: Rule, value: Double): String = if (rule.key == "dividend_growth_years") {
        String.format(Locale.JAPAN, "%d%s", value.toInt(), rule.unit)
    } else {
        String.format(Locale.JAPAN, "%.2f%s", value, rule.unit)
    }
}
