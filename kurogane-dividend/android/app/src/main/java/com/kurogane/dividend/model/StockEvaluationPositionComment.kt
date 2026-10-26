package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.abs

/** Explain the indicator's position without repeating the header or listing its band range. */
object StockEvaluationPositionComment {
    private data class Wording(val levels: List<String>, val perspective: String, val shortPerspective: String)
    private val wording = mapOf(
        "per" to Wording(listOf(
            "利益に対する株価評価は、基準上では低い側にあります。",
            "利益に対する株価評価は、比較的低い側に位置しています。",
            "利益に対する株価評価は、中位とする区分に位置しています。",
            "利益に対する株価評価は、比較的高い側に位置しています。",
            "利益に対する株価評価は、基準上では高い側にあります。"),
            "利益に対する市場評価の位置であり、買いやすさや配当の安全性を直接示すものではありません。",
            "市場の利益評価の位置であり、配当の安全性とは別の尺度です。"),
        "pbr" to Wording(listOf(
            "純資産に対する株価評価は、基準上では低い側にあります。",
            "純資産に対する株価評価は、比較的低い側に位置しています。",
            "純資産に対する株価評価は、中位とする区分に位置しています。",
            "純資産に対する株価評価は、比較的高い側に位置しています。",
            "純資産に対する株価評価は、基準上では高い側にあります。"),
            "帳簿上の純資産に対する市場評価の位置であり、資産の換金価値や配当用の現金を示すものではありません。",
            "帳簿上の資産に対する評価であり、資産の換金価値とは異なります。"),
        "payout_ratio" to Wording(listOf(
            "利益に対する配当負担は、基準上では小さい側にあります。",
            "利益に対する配当負担は、還元と留保を両立しやすい側にあります。",
            "利益に対する配当負担は、中位より大きい側に位置しています。",
            "利益に対する配当負担は、大きめとする側に位置しています。",
            "利益に対する配当負担は、基準上では大きい側にあります。"),
            "同じ判定でも帯の下側ほど利益に対する配当負担は小さく、上側ほど負担が大きい位置として読み取れます。",
            "同じ帯でも上側ほど、利益に対する配当負担が大きい位置です。"),
        "equity_ratio" to Wording(listOf(
            "自己資本の厚みは、基準上では薄い側に位置しています。",
            "自己資本の厚みは、比較的薄い側に位置しています。",
            "自己資本の厚みは、標準的とする区分に位置しています。",
            "自己資本の厚みは、比較的厚い側に位置しています。",
            "自己資本の厚みは、基準上では厚い側に位置しています。"),
            "帯の上側ほど自己資本の厚みを高く評価しますが、現預金の多さを直接表す位置ではありません。",
            "自己資本の厚みの位置であり、現預金の多さとは別の尺度です。"),
        "operating_margin" to Wording(listOf(
            "売上に対する本業の採算は、基準上では低い側にあります。",
            "売上に対する本業の採算は、比較的低い側に位置しています。",
            "売上に対する本業の採算は、標準的とする区分に位置しています。",
            "売上に対する本業の採算は、比較的高い側に位置しています。",
            "売上に対する本業の採算は、基準上では高い側にあります。"),
            "同じ帯でも上側ほど本業の採算が高く、下側ほど薄い位置であり、最終利益や現金収支とは異なる尺度です。",
            "本業の採算の位置であり、最終利益や現金収支とは異なります。"),
        "fcf_margin" to Wording(listOf(
            "投資後の現金収支は、基準上では弱い側に位置しています。",
            "投資後の現金収支は、現金が残りにくい側に位置しています。",
            "投資後の現金収支は、一定の現金を残す側に位置しています。",
            "投資後の現金収支は、比較的強い側に位置しています。",
            "投資後の現金収支は、基準上では強い側に位置しています。"),
            "投資後の現金収支の位置を示し、同じ帯でも上側ほど売上に対して多くの現金が残る区分です。",
            "同じ帯でも上側ほど、売上に対して多くの現金が残る位置です。"),
        "dividend_growth_years" to Wording(listOf(
            "増配の継続期間は、基準上では短い側に位置しています。",
            "増配の継続期間は、数年の実績を積み始めた側にあります。",
            "増配の継続期間は、複数年の実績を持つ側に位置しています。",
            "増配の継続期間は、比較的長い側に位置しています。",
            "増配の継続期間は、基準上では長い側に位置しています。"),
            "増額の大きさではなく継続期間の位置であり、小幅な増配でも年数は積み上がる点を分けて読み取ります。",
            "増配幅ではなく継続期間の位置で、小幅な増配でも年数は伸びます。"),
        "non_cut_years" to Wording(listOf(
            "配当を維持した期間は、基準上では短い側に位置しています。",
            "配当を維持した期間は、数年の実績を積み始めた側にあります。",
            "配当を維持した期間は、複数年の実績を持つ側に位置しています。",
            "配当を維持した期間は、比較的長い側に位置しています。",
            "配当を維持した期間は、基準上では長い側に位置しています。"),
            "据え置きを含む配当維持の期間を比べる位置であり、毎年増配してきた年数とは異なる尺度です。",
            "据え置きも含む維持期間であり、連続増配の年数とは異なります。"),
        "dividend_dependency" to Wording(listOf(
            "一社への配当収入の偏りは、基準上では抑えられる側にあります。",
            "一社への配当収入の偏りは、適正とする区分を上回る側にあります。",
            "一社への配当収入の偏りは、基準上では大きい側にあります。"),
            "帯の上側ほど一社の減配が保有全体の受取額に響きやすい配分で、会社自体の支払力とは別の尺度です。",
            "保有配当の集中度の位置であり、会社自体の支払力とは別です。"),
        "half_dividend_impact" to Wording(listOf(
            "一社の配当半減による全体への影響は、小さい側に位置しています。",
            "一社の配当半減による全体への影響は、中間の区分に位置しています。",
            "一社の配当半減による全体への影響は、大きい側に位置しています。"),
            "一社の配当半減を仮定した全体への影響の位置であり、半減が起きる確率や複数社の同時減配を示すものではありません。",
            "半減時の影響の位置であり、減配の確率を示すものではありません。"),
        "sector_concentration" to Wording(listOf(
            "同じ業種への資産の偏りは、基準上では抑えられる側にあります。",
            "同じ業種への資産の偏りは、比較的集中する側に位置しています。",
            "同じ業種への資産の偏りは、基準上では大きい側にあります。"),
            "評価額が同じ業種に偏る度合いを示す位置であり、配当収入の偏りも同じ割合になるとは限りません。",
            "評価額の業種配分の位置であり、配当収入の偏りとは異なります。")
    )
    private val fallback = Wording(listOf("取得値は、設定した基準と比較する位置にあります。"),
        "数値の位置は設定した基準との比較であり、帯の中での違いと境界までの距離を合わせて読み取ります。",
        "設定した基準との比較であり、境界までの距離も合わせて見ます。")

    private fun finish(intro: String, position: String, full: String, short: String = full): String {
        var result = intro + position + full
        if (result.length > 160) result = intro + position + short
        if (result.length < 80) result += "この位置は設定した基準との比較であり、将来の配当や株価の変化を予測するものではありません。"
        return result
    }

    private fun difference(value: Double, unit: String): String {
        val suffix = if (unit == "%") "ポイント" else unit
        if (value > 0 && value < 0.01 && unit != "年") return "0.01${suffix}未満"
        val number = when {
            abs(value) >= 1e12 -> String.format(Locale.JAPAN, "%.2e", value)
            unit == "年" -> String.format(Locale.JAPAN, "%.0f", value)
            else -> String.format(Locale.JAPAN, "%.2f", if (value == 0.0) 0.0 else value)
        }
        return number + suffix
    }

    fun range(value: Double, lower: Double?, upper: Double?, unit: String,
              lowerInclusive: Boolean = false, upperInclusive: Boolean = true,
              key: String = "generic", introduction: String? = null, referenceWidth: Double? = null,
              lowerIsBandBoundary: Boolean = true): String {
        require(value.isFinite() && (lower == null || lower.isFinite()) && (upper == null || upper.isFinite()))
        require(lower == null || upper == null || lower <= upper)
        val configured = wording[key] ?: fallback
        val words = when {
            key == "payout_ratio" && value < 0 -> configured.copy(
                perspective = "赤字の符号が比率に影響しており、低い値を配当負担の軽さとして比べる位置ではありません。",
                shortPerspective = "負の比率を、利益に対する配当負担の軽さとしては読み取りません。")
            key in setOf("per", "pbr") && value <= 0 -> configured.copy(
                perspective = "算定条件や利益・純資産の符号を確認する位置であり、低い倍率を割安さの根拠にはできません。",
                shortPerspective = "通常の正の倍率による比較から外れるため、算定条件も確認します。")
            else -> configured
        }
        val intro = introduction ?: words.levels.first()
        if (lower != null && lower == upper) return finish(intro,
            "比較の基準上にあり、境界との差は${difference(0.0, unit)}です。",
            words.perspective, words.shortPerspective)
        val exact = when {
            lower != null && value == lower && lowerInclusive -> "下側"
            upper != null && value == upper && upperInclusive -> "上側"
            else -> null
        }
        if (exact != null) return finish(intro,
            "${exact}境界と一致し、差は${difference(0.0, unit)}です。",
            if (exact == "下側" && !lowerIsBandBoundary) words.perspective else
                "現在は境界を含む側に分類され、境界を越えると隣の帯へ移りますが、指標の実態が急変する意味ではありません。",
            if (exact == "下側" && !lowerIsBandBoundary) words.shortPerspective else
                "境界を含む側に分類されており、判定の切替を実態の急変とは捉えないようにします。")
        if (lower == null || upper == null) {
            val above = upper == null
            val distance = abs(value - (lower ?: requireNotNull(upper)))
            val relation = if (above) "上回" else "下回"
            val position = "${if (above) "上限" else "下限"}のない帯で、${if (above) "下側" else "上側"}境界を${difference(distance, unit)}${relation}っています。"
            val relative = referenceWidth?.takeIf { it.isFinite() && it > 0 }?.let { distance / it }
            val interpretation = when {
                relative == null -> words.perspective
                relative < 1.0 / 9 -> "境界を越えた直後に近く、隣の判定との小さな数値差を、指標の実態の大きな違いとは捉えないようにします。"
                relative >= 3 -> "境界から大きく離れた位置ですが、区分内には終点がないため、帯内の割合や順位を示すものではありません。"
                relative >= 1 -> "境界から離れた側にあり、隣の判定へ戻るには比較的大きな変化が必要な位置として読み取れます。"
                else -> words.perspective
            }
            return finish(intro, position, interpretation, words.shortPerspective)
        }
        val fraction = ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0)
        val bucket = (fraction * 9).toInt().coerceIn(0, 8)
        val lowGap = difference(abs(value - lower), unit)
        val highGap = difference(abs(upper - value), unit)
        val position = when (bucket) {
            0 -> "帯の下端に非常に近く、下側境界との差は${lowGap}です。"
            1 -> "帯の下端寄りにあり、下側境界との差は${lowGap}です。"
            2 -> "帯の下側に位置し、下側境界との差は${lowGap}です。"
            3 -> "中央よりやや下側にあり、下側境界との差は${lowGap}です。"
            4 -> if (value - lower == upper - value) "帯の中央にあり、上下の境界までそれぞれ${lowGap}離れています。"
                else "帯の中央付近にあり、近い方の境界との差は${if (fraction < 0.5) lowGap else highGap}です。"
            5 -> "中央よりやや上側にあり、上側境界との差は${highGap}です。"
            6 -> "帯の上側に位置し、上側境界との差は${highGap}です。"
            7 -> "帯の上端寄りにあり、上側境界との差は${highGap}です。"
            else -> "帯の上端に非常に近く、上側境界との差は${highGap}です。"
        }
        val interpretation = when {
            bucket <= 1 && !lowerIsBandBoundary -> words.perspective
            bucket == 0 || bucket == 8 -> "境界付近にあり、小さな変化で帯が切り替わり得ますが、判定の違いを指標の実態の大きな差とは捉えないようにします。"
            bucket == 1 || bucket == 7 -> "帯の中央にある場合より隣の区分へ移りやすい位置であり、同じ判定でも境界との距離は異なります。"
            else -> words.perspective
        }
        return finish(intro, position, interpretation, words.shortPerspective)
    }

    fun threshold(value: Double, cutoffs: List<Double>, unit: String, minimum: Double? = null,
                  key: String = "generic"): String {
        val index = cutoffs.indexOfFirst { value <= it }.let { if (it < 0) cutoffs.size else it }
        val words = wording[key] ?: fallback
        var intro = words.levels.getOrElse(index) { words.levels.last() }
        if (key in setOf("per", "pbr") && value <= 0) intro = "取得された倍率は、通常の正の倍率による比較とは分けて読む位置です。"
        if (key == "payout_ratio" && value < 0) intro = "利益が赤字の場合、負の比率は配当負担が軽い位置とは読めません。"
        if (key in setOf("operating_margin", "fcf_margin") && value < 0) intro =
            if (key == "operating_margin") "本業の採算は赤字側にあり、基準上では低い側に位置しています。"
            else "投資後の現金収支は赤字側にあり、現金が残らない側に位置しています。"
        val width = when (index) {
            0 -> if (cutoffs.size > 1) cutoffs[1] - cutoffs[0] else null
            cutoffs.size -> if (cutoffs.size > 1) cutoffs.last() - cutoffs[cutoffs.lastIndex - 1] else null
            else -> null
        }
        return range(value, if (index == 0) minimum else cutoffs[index - 1], cutoffs.getOrNull(index), unit,
            lowerInclusive = index == 0 && minimum != null, key = key, introduction = intro, referenceWidth = width,
            lowerIsBandBoundary = index != 0 || minimum == null)
    }

    fun yield(value: Double, minimum: Double, average: Double, maximum: Double): String {
        if (!value.isFinite() || !listOf(minimum, average, maximum).all { it.isFinite() } || minimum > average || average > maximum)
            return unavailable("過去実績との比較基準が確定していないため、利回りの相対的な位置や境界との差は算出できません。")
        val intro = when {
            value < minimum -> "買値に対する配当水準は、比較対象の過去実績を下回る側にあります。"
            value > maximum -> "買値に対する配当水準は、比較対象の過去実績を上回る側にあります。"
            value < average -> "買値に対する配当水準は、過去平均を下回る側に位置しています。"
            value == average -> "買値に対する配当水準は、過去平均と同じ位置にあります。"
            else -> "買値に対する配当水準は、過去平均を上回る側に位置しています。"
        }
        val position = when {
            value < minimum -> "過去最低との差は${difference(minimum - value, "%")}で、過去の比較範囲の外側にあります。"
            value > maximum -> "過去最高との差は${difference(value - maximum, "%")}で、過去の比較範囲の外側にあります。"
            value == average -> "平均との差は${difference(0.0, "%")}で、平均未満と平均以上を分ける基準上にあります。"
            value < average -> "平均との差は${difference(average - value, "%")}で、過去最低から平均までの区間内にあります。"
            else -> "平均との差は${difference(value - average, "%")}で、過去平均から最高までの区間内にあります。"
        }
        return finish(intro, position,
            "過去の配当水準との比較であり、株価の割高・割安や将来の配当の安全性を直接決めるものではありません。",
            "過去との利回り比較であり、株価の割安さを直接決めるものではありません。")
    }

    fun cagr(value: Double): String {
        if (!value.isFinite()) return unavailable("配当の比較期間が不足しているため、年平均の伸びの位置や境界との差は算出できません。")
        val intro = when {
            value > 0 -> "比較期間全体では、配当額が増加する側に位置しています。"
            value < 0 -> "比較期間全体では、配当額が減少する側に位置しています。"
            else -> "比較期間の始点と終点では、配当額が横ばいとなる位置にあります。"
        }
        val position = if (value == 0.0) "増加と減少を分ける基準上にあり、境界との差は${difference(0.0, "%")}です。"
            else "横ばいの基準を${difference(abs(value), "%")}${if (value > 0) "上回" else "下回"}っています。"
        return finish(intro, position,
            "期間全体の年平均の変化であり、途中の据え置きや減配、毎年同じ割合で増減したかを示すものではありません。")
    }

    fun unavailable(reason: String): String = reason +
        "取得できない値をゼロや低い評価とみなさず、位置と距離の判定は保留します。必要な値がそろった段階で、同じ基準に照らして再評価する項目です。"
}
