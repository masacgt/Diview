package com.kurogane.dividend.model

import kotlin.math.abs

/** Replaces explanation slots within the length of the former explanation for the same input. */
internal object DividendExplanationVariants {
    fun compose(key: String, value: Double, core: String, context: StockAnalysisContext,
                values: Map<String, Double?>, nearBoundary: Boolean,
                previousTrend: List<String>?, previousRelation: List<String>?,
                limitation: Pair<String, String>, budget: Int): String {
        val coreOptions = extreme(key, value) ?: listOf(core)
        val evidence = listOfNotNull(
            if (nearBoundary) listOf(
                "境界付近のため、小さな差による帯の切替を過大視しません。",
                "評価帯の境界付近にあり、値の小さな変化で判定が変わり得ますが、配当を続ける実力が急変する意味ではありません。"
            ) else null,
            relation(key, values, context) ?: previousRelation,
            trend(key, context) ?: previousTrend
        ).toMutableList()
        val limitOptions = listOf(limitation.first, limitation.second.substringBefore('。') + "。", limitation.second)
        // Every retained slot is a complete sentence; never cut text or drop the indicator's limitation.
        while (true) {
            val slots = listOf(coreOptions) + evidence + listOf(limitOptions)
            var best: String? = null
            fun select(index: Int, text: String) {
                if (text.length > budget) return
                if (index == slots.size) {
                    if (best == null || text.length > best!!.length) best = text
                    return
                }
                slots[index].distinct().forEach { select(index + 1, text + it) }
            }
            select(0, "")
            best?.let { return it }
            // Boundary and contradictory evidence have precedence when all slots cannot fit.
            if (evidence.isNotEmpty()) evidence.removeAt(evidence.lastIndex)
            else return core + limitation.first
        }
    }

    internal fun extreme(key: String, value: Double): List<String>? {
        fun copy(short: String, full: String) = listOf(short, full)
        return when (key) {
            "per" -> when {
                value < 0 -> copy("赤字の倍率は通常の割安比較に使えません。", "利益が赤字のため、負の倍率を低い買値の根拠にはできず、黒字回復と配当原資を分けて確認します。")
                value == 0.0 -> copy("倍率の算定条件と利益の有無を確かめます。", "ゼロの倍率は割安さを示すとは限らず、利益の有無、算定条件と配当を賄う現金を確認します。")
                value < 3 -> copy("極端な低倍率は一時利益や減益懸念も見ます。", "極端に低い倍率には、一時的な利益膨張や将来の減益懸念も含まれ得るため、通常利益と資金繰りを確認します。")
                value < 5 -> copy("低倍率でも利益の持続性は別に見ます。", "低い利益倍率は買値の軽さを示す一方、利益縮小への警戒も含み得るため、通常利益と配当原資の持続性を見ます。")
                value > 200 -> copy("極端な高倍率は利益の小ささも確認します。", "極端な高倍率は利益がわずかになった結果の可能性もあり、成長期待と利益の一時的な落ち込みを分けて見ます。")
                value > 100 -> copy("非常に高い倍率は利益回復への依存も見ます。", "非常に高い利益倍率には成長や利益回復への期待が含まれ、期待未達時の評価変化と配当原資を別に確認します。")
                value > 60 -> copy("高倍率は成長期待と利益の実現性を分けて見ます。", "成長期待の大きい倍率ですが、株価への期待がそのまま配当用の現金になるわけではなく、利益の実現性も見ます。")
                else -> null
            }
            "pbr" -> when {
                value < 0 -> copy("負の純資産では通常の資産倍率比較に注意します。", "純資産が負の場合があり、低い倍率を資産の割安さとは読めないため、財務再建と配当用の現金を確認します。")
                value == 0.0 -> copy("純資産と株価の算定条件を確かめます。", "ゼロの資産倍率は割安さの根拠とは限らず、純資産と株価の算定条件、配当を支える資産の実態を確認します。")
                value < 0.15 -> copy("極端な低評価は資産の毀損や収益力も見ます。", "極端に低い資産評価には、資産の毀損や低収益への懸念が含まれ得るため、帳簿価額と現金を生む力を分けて見ます。")
                value < 0.3 -> copy("低い資産評価でも換金性と収益力を確認します。", "資産への評価は非常に低いものの、帳簿上の割安さだけでは配当を支えられず、資産の換金性と収益力も確認します。")
                value > 15 -> copy("極端な資産評価は純資産の小ささも見ます。", "極端な資産倍率には高い成長期待だけでなく純資産の小ささも影響するため、事業収益と現金創出を確認します。")
                value > 10 -> copy("非常に高い資産評価は実際の収益も見ます。", "資産への期待が非常に高く、帳簿に表れない事業価値を含む一方、実際の収益と配当用の現金が伴うかも見ます。")
                value > 7 -> copy("高い資産評価と支払力は分けて見ます。", "高い資産評価には収益成長への期待が含まれ得ますが、その期待と配当を支払える現金は分けて確認します。")
                else -> null
            }
            "payout_ratio" -> when {
                value < 0 -> copy("赤字の比率を配当負担の軽さとは読めません。", "赤字が比率の符号に影響しており、低い配当負担とは読めず、損益の回復と現金で賄う配当額を確認します。")
                value == 0.0 -> copy("利益に対する配当負担は計上されていません。", "利益に対する配当負担は計上されていませんが、無配の継続か一時的な休配かは配当方針と現金収支も確認します。")
                value > 200 -> copy("配当が利益を大幅に超え、現金の補完も見ます。", "配当が利益を大幅に超え、当期利益以外の支えが必要なため、現金の蓄積、一時損益と配当方針を確認します。")
                value > 120 -> copy("利益を超える配当負担が大きい状態です。", "利益を超える配当負担が大きく、利益減少で比率が膨らんだのか、蓄積資金を使う方針なのかも分けて確認します。")
                value > 100 -> copy("配当が利益を超え、現金面の支えも必要です。", "配当が利益を超えていますが、それだけで減配とは決まらず、利益の一時要因と投資・返済後に残る現金も見ます。")
                value > 90 -> copy("利益のほぼ全額を還元し、減益への余白は小さめです。", "利益のほぼ全額を配当に充て、減益時の余白は小さめですが、一時損益と現金収支を確かめて負担を判断します。")
                else -> null
            }
            "operating_margin", "fcf_margin" -> {
                val cash = key == "fcf_margin"
                val deep = if (cash) -15.0 else -10.0
                when {
                    value < (if (cash) -30.0 else -20.0) -> copy(
                        if (cash) "大幅な資金流出があり、調達と投資計画を見ます。" else "大幅な営業赤字で、回復策と資金繰りを見ます。",
                        if (cash) "大幅な資金流出があり、投資の集中なのか営業収支の悪化なのかを分け、手元資金と返済・配当の両立を見ます。" else "大幅な営業赤字で本業の配当原資は弱く、構造的な採算悪化か一時費用かを分け、回復策と資金繰りを確認します。")
                    value <= deep -> copy(
                        if (cash) "深い資金赤字で、現預金の取り崩しも確認します。" else "深い営業赤字で、本業以外の資金の支えも見ます。",
                        if (cash) "深い資金赤字では現預金の取り崩しや調達が支えになり得るため、投資回収の見込みと配当後の残高も確認します。" else "深い営業赤字が本業の配当原資を弱めており、一時費用の有無と、本業以外の資金で支える余地を確認します。")
                    value <= -0.005 && value > (if (cash) -5.0 else -2.0) -> copy(
                        if (cash) "小幅な資金赤字でも、投資時期と現金残高を見ます。" else "小幅な営業赤字で、採算回復と現金収支を見ます。",
                        if (cash) "小幅な資金赤字でも、投資時期や運転資金のずれで値は変わるため、一時的な流出か継続的な不足かを確認します。" else "小幅な営業赤字では売上や費用の変化で黒字にも赤字にも振れやすく、採算回復と現金収支の両方を確認します。")
                    value <= -0.005 -> copy(
                        if (cash) "資金赤字があり、配当の補完資金も見ます。" else "営業赤字があり、利益回復と現金の支えも見ます。",
                        if (cash) "投資後の現金収支は赤字で配当には補完資金が必要なため、営業収支の悪化と成長投資の負担を分けて見ます。" else "本業が赤字で利益面の配当原資は弱まっていますが、費用の一時要因と営業キャッシュフローの回復も確認します。")
                    abs(value) < 0.005 -> copy(
                        if (cash) "投資後の現金はゼロ付近で、追加支出も見ます。" else "本業の採算はゼロ付近で、費用増への耐性も見ます。",
                        if (cash) "投資後に残る現金はゼロ付近で、追加投資や返済により配当原資が細り得るため、支出予定と現金残高を見ます。" else "本業の採算はゼロ付近で、わずかな売上減や費用増でも赤字化し得るため、採算の安定性と現金回収を確認します。")
                    value > 50 -> copy(
                        if (cash) "非常に大きい資金余剰は一時的な流入も見ます。" else "突出した利益率は業種と一時利益も確認します。",
                        if (cash) "非常に大きい資金余剰は配当の支えになりますが、資産売却や投資先送りによる一時的な流入ではないかも見ます。" else "突出した利益率は収益力の強みになり得ますが、業種特性や一時利益の影響を分け、現金回収が伴うかも見ます。")
                    value > 30 -> copy(
                        if (cash) "大幅な資金余剰があり、持続性と投資予定も見ます。" else "大幅な黒字が強みですが、持続性と現金回収も見ます。",
                        if (cash) "大幅な資金余剰は配当の支えになり得る一方、投資時期や運転資金の回収でも増えるため、持続性を確認します。" else "大幅な黒字は配当を支える収益力ですが、好況や一時要因の影響を分け、同じ採算と現金回収が続くかを見ます。")
                    else -> null
                }
            }
            "equity_ratio" -> when {
                value in 0.0..<5.0 -> copy("自己資本の備えが極めて薄く、資金調達も見ます。", "損失を吸収する自己資本の備えが極めて薄く、返済と配当を両立できるか、現預金と資金調達の条件を確認します。")
                value in 80.0..100.0 -> copy("資本の厚さは強みですが、現金と収益力も見ます。", "非常に厚い自己資本は損失への備えを支えますが、資産が現金を生むかは別のため、収益力と現金収支も確認します。")
                else -> null
            }
            "dividend_growth_years" -> when {
                value == 0.0 -> copy("連続増配はなく、据え置きと減配を分けて見ます。", "直近では増配が連続していませんが、配当の据え置きと減配では意味が違うため、実際の配当額と支払余力を確認します。")
                value == 1.0 -> copy("増配再開の可能性もあり、過去の減配と分けて見ます。", "増配を始めた段階で、初めての増額か減配後の再開かを分け、利益や現金の成長が伴っているかを確認します。")
                value <= 3 -> copy("増配の履歴は短く、方針の定着と原資を見ます。", "増配の履歴はまだ短く、還元方針が定着するか、増額が一時利益に依存せず利益と現金の成長に沿うかを確認します。")
                value >= 30 -> copy("非常に長い増配実績でも、直近の負担は別に見ます。", "非常に長い増配実績は還元姿勢の強みですが、小さな増額でも年数は伸びるため、直近の増配幅と支払余力を確認します。")
                value >= 20 -> copy("長期増配が強みですが、増配幅と今の余力も見ます。", "長期の増配実績は複数の環境で還元してきた強みですが、増配幅の縮小や配当負担の高まりがないかも確認します。")
                value >= 10 -> copy("長い増配実績と、将来の増額余力は分けて見ます。", "長い増配実績は還元姿勢を支える一方、過去の継続と将来の増額余力は別のため、利益と現金の伸びも確認します。")
                else -> null
            }
            else -> null
        }
    }

    private fun trend(key: String, context: StockAnalysisContext): List<String>? {
        if (key == "dividend_growth_years") {
            val issuer = context.series("issuer_dps")
            val points = if (issuer.size >= 2) issuer else context.series("dividend_dps")
            if (points.size < 2) return null
            val changes = points.zipWithNext().map { (a, b) -> b.value - a.value }
            if (changes.any { !it.isFinite() }) return null
            val scope = if (points.first().basis == MetricHistoryPoint.CALENDAR_ACTUAL) "暦年実績" else "年度実績"
            val action = when {
                points.last().value > 0 && points[points.lastIndex - 1].value == 0.0 -> "無配の後に配当を再開しています"
                changes.last() < -0.005 -> "直近で配当額を減らしています"
                changes.last() > 0.005 && changes.dropLast(1).any { it < -0.005 } -> "減配後に再び増額しています"
                abs(changes.last()) < 0.005 && changes.dropLast(1).any { it > 0.005 } -> "増額の後に据え置いています"
                changes.all { abs(it) < 0.005 } -> "配当額の据え置きが続いています"
                changes.all { it > 0.005 } -> "配当額の増加が続いています"
                changes.all { it >= -0.005 } -> "据え置きを挟んで増額しています"
                else -> "増額と減配が混在しています"
            }
            return listOf("${scope}では$action。", "確認できた${points.size}期の${scope}では$action。年数とは別に増額の幅と継続性も見ます。")
        }
        val points = context.series(key)
        if (points.size < 2 || key !in setOf("operating_margin", "fcf_margin", "payout_ratio", "equity_ratio") ||
            (key == "payout_ratio" && points.any { it.value < 0 })) return null
        val changes = points.zipWithNext().map { (a, b) -> b.value - a.value }
        val spread = points.maxOf { it.value } - points.minOf { it.value }
        if (!spread.isFinite() || changes.any { !it.isFinite() }) return null
        val tolerance = when (key) { "payout_ratio", "equity_ratio" -> 2.0; "fcf_margin" -> 1.0; else -> 0.5 }
        val action = when {
            key in setOf("operating_margin", "fcf_margin") && points.all { it.value < -0.005 } -> when {
                changes.all { it > 0 } -> "赤字は続くものの縮小しています"
                changes.all { it < 0 } -> "赤字が続き、さらに深まっています"
                spread <= tolerance -> "赤字が続き、回復はまだ確認できません"
                else -> "赤字が続き、年ごとの振れもあります"
            }
            key in setOf("operating_margin", "fcf_margin") && points[points.lastIndex - 1].value < -0.005 && points.last().value >= 0.005 -> "直近でプラスに転じ、回復の継続が焦点です"
            key in setOf("operating_margin", "fcf_margin") && points[points.lastIndex - 1].value > 0.005 && points.last().value <= -0.005 -> "直近で赤字に転じ、悪化の背景を確かめます"
            spread > tolerance * 2 && changes.any { it > 0 } && changes.any { it < 0 } -> "上下の変動が大きく、単年の値と安定性は分けて見ます"
            key == "payout_ratio" && points.all { it.value >= 0 } && spread > tolerance && changes.all { it <= 0 } -> "利益に対する配当負担が軽くなっています"
            key == "payout_ratio" && points.all { it.value >= 0 } && spread > tolerance && changes.all { it >= 0 } -> "利益に対する配当負担が重くなっています"
            else -> return null
        }
        val scope = if (points.first().basis == MetricHistoryPoint.ISSUER_YEAR) "年度実績" else "決算実績"
        val followUp = when (key) {
            "payout_ratio" -> "配当額の変更と利益の変動を分けて見ます。"
            "fcf_margin" -> "投資時期と営業収支の変化を分けて見ます。"
            "equity_ratio" -> "資産と負債の増減の内訳も確かめます。"
            else -> "売上の変化と一時的な費用の影響も見ます。"
        }
        return listOf("${scope}では$action。", "確認できた直近${points.size}期の${scope}では$action。$followUp")
    }

    private fun relation(key: String, values: Map<String, Double?>, context: StockAnalysisContext): List<String>? {
        fun current(name: String) = values[name]?.takeIf { it.isFinite() }
        fun copy(short: String, full: String) = listOf(short, full)
        if (key in setOf("equity_ratio", "fcf_margin")) {
            context.alignedLatest("equity_ratio", "fcf_margin")?.let {
                if (it.getValue("equity_ratio") >= 40 && it.getValue("fcf_margin") < 0) {
                    val date = context.series("equity_ratio").last().periodEnd.toString().take(7).replace('-', '/')
                    return copy("${date}実績は資本が厚くてもFCFは赤字です。", "${date}の同じ決算期では自己資本が厚い一方、FCFは赤字で、損失への備えと配当用の現金は分けて見ます。")
                }
            }
        }
        if (key in setOf("per", "operating_margin") && current("per")?.let { it > 0 && it <= 10 } == true &&
            current("operating_margin")?.let { it < 0 } == true) {
            return copy("取得値は低PERと営業赤字が併存し、利益の種類と時点も見ます。", "取得値では低いPERと営業赤字が併存しますが、純利益と営業利益は対象が異なるため、算定時点と一時損益を確かめ、割安と即断しません。")
        }
        if (key in setOf("dividend_growth_years", "payout_ratio") && current("dividend_growth_years")?.let { it >= 10 } == true &&
            current("payout_ratio")?.let { it > 90 } == true) {
            return copy("長期増配と高い配当負担が併存し、取得時点と今の余力も見ます。", "長期の増配実績がある一方、取得された配当性向は高く、過去の還元姿勢と今後の増額余地は取得時点も分けて確認します。")
        }
        if (key == "dividend_growth_years" && current("dividend_growth_years")?.let { it >= 10 } == true &&
            current("fcf_margin")?.let { it < 0 } == true) {
            return copy("長期増配でも取得FCFは赤字で、時点と補完資金を見ます。", "長期増配の実績と取得されたFCFの赤字が併存するため、実績の時点を確認し、今後も現金で増額を賄えるかは別に見ます。")
        }
        return null
    }
}
