package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.abs

/** Explain the evidence below the existing numeric label; never echo that label. */
internal object StockMetricCommentary {
    private data class Evidence(val short: String, val full: String = short)

    fun comment(key: String, value: Double, base: String, context: StockAnalysisContext,
                detailed: Boolean = false): String {
        val core = extreme(key, value) ?: base
        val trend = trend(key, context)
        val relation = relation(key, context)
        val limit = limitations.getValue(key)
        return if (detailed) {
            listOfNotNull(core, trend?.full, relation?.full, limit.second).joinToString("")
        } else {
            compact(core, trend?.short, relation?.short, limit.first)
        }
    }

    fun contextual(base: String, key: String, context: StockAnalysisContext, limitation: String): String =
        compact(base, trend(key, context)?.short, relation(key, context)?.short, limitation)

    fun risk(base: String, context: StockAnalysisContext, limitation: String): String =
        compact(base, trend("fcf_margin", context)?.short?.let { "発行会社のFCFは、$it" },
            relation("payout_ratio", context)?.short?.let { "発行会社の$it" }, limitation)

    private fun compact(core: String, trend: String?, relation: String?, limitation: String): String {
        val pieces = mutableListOf(core)
        // Reserve the indicator's limitation; fit concrete evidence in the approved priority order.
        for (evidence in listOfNotNull(trend, relation)) {
            if (pieces.sumOf { it.length } + evidence.length + limitation.length <= 140) pieces += evidence
        }
        pieces += limitation
        return pieces.joinToString("")
    }

    private val limitations = mapOf(
        "per" to ("配当原資は利益推移と営業CFも確認します。" to
            "利益に対する市場評価からは、配当を支払う現金や予想利益の達成度までは分かりません。利益の一時要因、配当性向、営業キャッシュフローと投資負担を合わせて確認します。"),
        "pbr" to ("資産の質や現金余力は別に確認します。" to
            "帳簿上の純資産は、そのまま配当に使える現金ではありません。資産の収益性や換金性、負債、利益と現金収支を合わせて見て、資産が配当原資を生む状態か確認します。"),
        "payout_ratio" to ("支払余力はFCFと利益の一時要因も見ます。" to
            "利益に対する配当負担だけでは、手元資金や投資・借入返済後の支払余力は分かりません。特別損益を除く利益の安定性、営業キャッシュフロー、FCFと配当方針も確認します。"),
        "equity_ratio" to ("現預金、返済期限、資産の質も見ます。" to
            "自己資本の厚みは、現預金の多さや借入返済の時期までは示しません。現金収支、負債の返済期限、資産の換金性と業種特性を見て、配当維持に使える資金を別に確認します。"),
        "operating_margin" to ("現金回収や投資負担は営業CFも見ます。" to
            "本業利益は最終利益や配当用の現金と一致しません。利益の現金化、投資額、需要やコストの変化も合わせて確認し、現在の収益力が配当原資として残るかを見ます。"),
        "fcf_margin" to ("投資計画と配当後の資金残高も見ます。" to
            "投資時期、資産売却や運転資金の変動でも値は動き、プラスだけで配当額を賄えるとは判断できません。営業キャッシュフロー、投資の必要額、実際の配当支払額と返済負担も確認します。"),
        "dividend_growth_years" to ("将来余力は配当性向とFCFも確認します。" to
            "過去の増配実績は、増配額の大きさや将来の配当維持を保証しません。利益成長に沿った増配か、配当性向とFCFに無理がないか、各期の配当額と最新の配当方針も確認します。")
    )

    /** Approved editorial thresholds do not alter rating bands, scores or combination codes. */
    internal fun extreme(key: String, value: Double): String? = when (key) {
        "per" -> when {
            value < 0 -> "利益が赤字のため、倍率の低さを割安さとして読めません。"
            value == 0.0 -> "倍率が成立する利益や算定条件を確かめる必要があります。"
            value < 5 -> "低い利益倍率には、利益縮小や一時的な増益の影響もあり得ます。"
            value > 100 -> "利益に比べ株価評価が極端に大きく、利益の小ささや一時要因も確かめます。"
            value > 60 -> "利益成長への期待が大きく、見通し未達時に株価評価が変わりやすい状態です。"
            else -> null
        }
        "pbr" -> when {
            value < 0 -> "純資産が負の場合があり、通常の資産倍率として比較できません。"
            value == 0.0 -> "純資産と株価の算定条件を確認する必要があります。"
            value < 0.3 -> "資産への評価が非常に低く、収益性や資産価値への懸念も確かめます。"
            value > 10 -> "資産に対する市場の期待が極めて大きく、実際の収益との関係を確かめます。"
            value > 7 -> "高い資産評価に収益成長への期待が含まれ、期待未達の影響を受け得ます。"
            else -> null
        }
        "payout_ratio" -> when {
            value < 0 -> "赤字が比率の符号に影響しており、低い配当負担とは読めません。"
            value == 0.0 -> "利益に対する配当負担は計上されておらず、実際の配当額と方針を確認します。"
            value > 200 -> "配当額が利益を大きく超え、現金や蓄積資金による支えを確認する必要があります。"
            value > 120 -> "利益を上回る配当負担が大きく、一時利益の減少や配当方針の影響も確かめます。"
            value > 100 -> "当期利益だけでは配当額に届かず、現預金や過年度の蓄積も支えになり得ます。"
            else -> null
        }
        "equity_ratio" -> when {
            value < 0 -> "自己資本が負で、損失への備えや借入返済と配当の両立に注意が必要です。"
            value == 0.0 -> "損失を吸収する自己資本の余地が乏しく、財務構成の確認が必要です。"
            value < 10 -> "自己資本の備えが非常に薄く、損失や資金調達環境の変化に弱い構成です。"
            value > 100 -> "通常の資本構成から外れる値のため、負債の符号や算定対象を確かめます。"
            else -> null
        }
        "operating_margin" -> when {
            value < -20 -> "本業の損失が売上に対して大きく、配当原資の回復と資金繰りの確認が必要です。"
            value <= -0.005 -> "本業が赤字で、利益面から配当を支える力が弱まっています。"
            abs(value) < 0.005 -> "本業利益がゼロ付近で、わずかな費用増や売上減でも赤字化し得ます。"
            value > 30 -> "売上に対する利益が非常に大きく、業種特性や一時的な高収益の影響も見ます。"
            else -> null
        }
        "fcf_margin" -> when {
            value < -30 -> "投資後の資金流出が大きく、手元資金や投資計画、資金調達の必要性も見ます。"
            value <= -0.005 -> "投資後の現金収支がマイナスで、配当支払には現預金などの補完も必要です。"
            abs(value) < 0.005 -> "投資後に残る資金はゼロ付近で、追加投資により配当原資が細り得ます。"
            value > 30 -> "投資後の資金創出が非常に大きく、資産売却や運転資金の一時変動も見ます。"
            else -> null
        }
        "dividend_growth_years" -> when {
            value == 0.0 -> "直近では増配が連続しておらず、配当維持と減配のどちらかは配当額で確かめます。"
            value == 1.0 -> "増配を始めた段階で、利益や現金の成長が伴うかが継続の支えになります。"
            value >= 20 -> "長期にわたり増配を重ねた実績は強みですが、直近の支払余力は別に見ます。"
            value >= 10 -> "長い増配実績は還元姿勢を支える一方、将来も同じ増配幅が続くとは限りません。"
            else -> null
        }
        else -> null
    }

    private fun trend(key: String, context: StockAnalysisContext): Evidence? {
        if (key in setOf("dividend_growth_years", "non_cut_years", "dividend_cagr")) {
            val points = context.series("dividend_dps")
            if (points.size < 2) return null
            val changes = points.zipWithNext().map { (a, b) -> b.value - a.value }
            val action = when {
                changes.last() > 0 && changes.dropLast(1).any { it < 0 } -> "減配後に再び配当額を増やしています"
                changes.last() < 0 -> "直近の配当額が減少しています"
                changes.all { it > 0 } -> "配当額の増加が続いています"
                changes.all { abs(it) < 0.005 } -> "配当額を維持しています"
                changes.all { it >= 0 } -> "据え置きを挟んで配当額を増やしています"
                else -> "配当額の増減が混在しています"
            }
            return Evidence("暦年実績では${action}。", "${points.first().periodEnd.year}〜${points.last().periodEnd.year}年の暦年実績では${action}。")
        }
        val points = context.series(key)
        if (points.size < 2 || (key in setOf("per", "pbr") && points.any { it.value <= 0 }) ||
            (key == "payout_ratio" && points.any { it.value < 0 })) return null
        val delta = points.last().value - points.first().value
        val tolerance = when (key) {
            "per" -> 0.5; "pbr" -> 0.05; "payout_ratio", "equity_ratio" -> 2.0
            "operating_margin" -> 0.5; "fcf_margin" -> 1.0; else -> 0.1
        }
        val changes = points.zipWithNext().map { (a, b) -> b.value - a.value }
        val spread = points.maxOf { it.value } - points.minOf { it.value }
        if (!delta.isFinite() || !spread.isFinite() || changes.any { !it.isFinite() }) return null
        val direction = when {
            key in setOf("operating_margin", "fcf_margin") && points[points.lastIndex - 1].value < 0 && points.last().value >= 0.005 -> "直近でプラスに転じています"
            key in setOf("operating_margin", "fcf_margin") && points[points.lastIndex - 1].value > 0 && points.last().value <= -0.005 -> "直近でマイナスに転じています"
            spread <= tolerance -> "おおむね横ばいです"
            abs(delta) <= tolerance -> "上下の変動が目立ちます"
            changes.all { it >= 0 } -> "上昇しています"
            changes.all { it <= 0 } -> "低下しています"
            delta > 0 -> "上下しながら上昇しています"
            else -> "上下しながら低下しています"
        }
        val scope = when (points.first().basis) {
            MetricHistoryPoint.YEAR_END -> "期末実績"
            MetricHistoryPoint.ISSUER_YEAR -> "年度実績"
            else -> "決算実績"
        }
        val change = if (abs(delta) > tolerance) {
            val unit = if (key == "per" || key == "pbr") "倍" else "ポイント"
            if (abs(delta) > 10_000) "始点から大幅に${if (delta > 0) "上昇" else "低下"}しました。"
            else "始点から${format(abs(delta))}${unit}${if (delta > 0) "上昇" else "低下"}しました。"
        } else ""
        val short = if (points.size == 2 && abs(delta) > tolerance && abs(delta) <= 10_000)
            "前期の${scope}から${format(abs(delta))}${if (key in setOf("per", "pbr")) "倍" else "ポイント"}${if (delta > 0) "上昇" else "低下"}しました。"
            else "直近${points.size}期の${scope}は${direction}。".replace("。。", "。")
        return Evidence(short,
            "${points.first().periodEnd.year}〜${points.last().periodEnd.year}年の${scope}は${direction}。$change".replace("。。", "。"))
    }

    private fun relation(key: String, context: StockAnalysisContext): Evidence? {
        if (key in setOf("payout_ratio", "fcf_margin")) {
            context.alignedLatest("payout_ratio", "fcf_margin")?.let { values ->
                val payout = values.getValue("payout_ratio")
                val fcf = values.getValue("fcf_margin")
                return when {
                    payout > 100 && fcf < 0 -> dated(context, "payout_ratio", "配当負担が重く、FCFもマイナスです。")
                    payout in 0.0..50.0 && fcf < 0 -> dated(context, "payout_ratio", "利益上の余裕と投資後の資金不足が併存します。")
                    payout > 100 && fcf >= 0 -> dated(context, "payout_ratio", "利益を超える配当でもFCFはプラスです。", "配当が利益を超える一方、FCFはプラスで、利益と現金収支の見方が異なります。")
                    else -> null
                }
            }
        }
        if (key in setOf("operating_margin", "fcf_margin")) {
            context.alignedLatest("operating_margin", "fcf_margin")?.let { values ->
                if (values.getValue("operating_margin") >= 10 && values.getValue("fcf_margin") < 0)
                    return dated(context, "operating_margin", "本業利益が出ても投資後の資金は不足しています。")
            }
        }
        if (key in setOf("equity_ratio", "operating_margin")) {
            context.alignedLatest("equity_ratio", "operating_margin")?.let { values ->
                if (values.getValue("equity_ratio") >= 40 && values.getValue("operating_margin") < 2)
                    return dated(context, "equity_ratio", "資本が厚い一方、本業の収益力は弱めです。")
            }
        }
        if (key in setOf("dividend_yield", "payout_ratio")) {
            context.alignedLatest("dividend_yield", "payout_ratio")?.let { values ->
                if (values.getValue("dividend_yield") >= 4 && values.getValue("payout_ratio") > 100)
                    return dated(context, "dividend_yield", "高利回りと利益を超える配当負担が重なっています。")
            }
        }
        if (key in setOf("dividend_growth_years", "non_cut_years", "dividend_cagr")) {
            val dps = context.series("issuer_dps").takeLast(2)
            val fcf = context.series("fcf_margin").takeLast(2)
            if (dps.size == 2 && fcf.size == 2 && dps.map { it.periodEnd } == fcf.map { it.periodEnd } &&
                dps.map { it.periodPrecision } == fcf.map { it.periodPrecision } &&
                dps.last().value > dps.first().value && fcf.last().value < fcf.first().value)
                return dated(context, "issuer_dps", "配当増額とFCF低下が重なっています。")
        }
        return null
    }

    private fun format(value: Double) = String.format(Locale.JAPAN, "%.2f", value)

    private fun dated(context: StockAnalysisContext, key: String, body: String, full: String = body): Evidence {
        val date = context.series(key).last().periodEnd.toString().take(7).replace('-', '/')
        return Evidence("${date}実績では$body", "${date}の同じ決算期の実績では$full")
    }
}
