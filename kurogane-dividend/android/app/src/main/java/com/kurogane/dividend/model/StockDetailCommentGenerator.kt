package com.kurogane.dividend.model

import java.util.Locale

/** Short, state-specific comments for the indicator rows in the four Stock Detail analysis tabs. */
object StockDetailCommentGenerator {
    private val finePositionLabels = listOf(
        "下端に近い位置", "下端寄り", "下側寄り", "中央より下", "中央付近",
        "中央より上", "上側寄り", "上端寄り", "上端に近い位置"
    )

    fun dividendYield(current: Double, minimum: Double, average: Double, maximum: Double): String {
        val position = when {
            current < minimum -> "過去最低未満"
            current > maximum -> "過去最高超"
            current < average -> "平均未満の${positionWithin(current, minimum, average)}"
            else -> "平均以上の${positionWithin(current, average, maximum)}"
        }
        val context = when {
            current < minimum -> "最低未満は、株価上昇か予想配当の低下が影響した可能性があります。"
            current > maximum -> "最高超は高利回りですが、株価下落や減配懸念も確かめます。"
            current < average -> "過去平均より控えめな水準です。"
            else -> "過去平均以上の配当水準が見込まれます。"
        }
        return "現在利回りは${percent(current, 2)}で、直近5期の最低${percent(minimum, 2)}・平均${percent(average, 2)}・最高${percent(maximum, 2)}に対して${position}です。$context 利回りは株価と予想配当で動くため、配当の安全性は配当性向、FCF、業績も確認します。"
    }

    fun dividendIncreaseYears(years: Int, band: String, position: String): String {
        val history = when {
            years <= 1 -> "履歴が短く、継続姿勢を判断する材料は限られます。"
            years <= 3 -> "数年の実績は還元方針の一貫性を示す一材料です。"
            years <= 5 -> "複数年の実績は株主還元を続けた強みです。"
            years <= 10 -> "長めの履歴は、配当を伸ばしてきた姿勢を支えます。"
            else -> "長期の実績は、複数の環境で配当を伸ばした強みです。"
        }
        return "連続増配は${years}年で「${band}」帯の${position}です。$history 年数だけでは増配額や現在の支払余力は分からないため、利益の安定性、配当性向、FCFも合わせて見て、配当額の推移も追います。"
    }

    fun nonCutYears(years: Int, band: String, position: String): String {
        val history = if (years == 0) {
            "比較期間に配当維持の実績を確認できていません。"
        } else {
            "${years}年間、年間配当を下げなかった実績です。"
        }
        return "非減配は${years}年で「${band}」帯の${position}です。$history 横ばい配当も含むため増配幅や将来余力は分からず、配当性向、FCF、利益推移と各期の配当額も確かめ、減配時期も追います。"
    }

    fun annualDividendCagr(rate: Double, isCalendarYearBasis: Boolean, band: String, position: String): String {
        val trend = when {
            rate < -5.0 -> "配当は大きく減少しており、回復状況に注意します。"
            rate < 0.0 -> "配当は減少傾向で、減配後の水準を確認します。"
            rate == 0.0 -> "比較期間の始点と終点は同水準です。"
            rate < 5.0 -> "配当は緩やかな増加傾向です。"
            else -> "配当は大きく増えていますが、急増の背景も確認します。"
        }
        val basis = if (isCalendarYearBasis) {
            "暦年集計は決算期の年間配当と期間がずれる場合があります。"
        } else {
            "年率換算は途中の減配や急増を平均化します。"
        }
        return "年間配当CAGRは${signedPercent(rate)}で「${band}」と判定され、${position}水準です。$trend $basis 継続力は配当性向、FCF、利益推移も確認します。"
    }

    fun dividendDependency(value: Double, band: String, position: String): String {
        val context = when {
            value <= 10.0 -> "目安内の比率でも、一社の減配は受取額に影響します。"
            value <= 25.0 -> "配当収入の柱である一方、減配時の影響も大きめです。"
            else -> "一社への依存が大きく、減配時に全体の受取額が下がります。"
        }
        val positionContext = positionContext(position)
        return "銘柄配当依存度は${percent(value, 1)}で「${band}」帯の${position}です。$context $positionContext 保有配当の集中を示す値で、会社自身の支払力は表さないため、業種偏り、配当性向、FCFも確認します。"
    }

    fun halfDividendImpact(value: Double, band: String, position: String): String {
        val impact = when {
            value <= 5.0 -> "半減しても保有全体への影響は比較的小さい試算です。"
            value <= 12.5 -> "一社の減配が保有配当を目立って押し下げる試算です。"
            else -> "半減時に保有全体の受取額が大きく減る試算です。"
        }
        val positionContext = positionContext(position)
        return "配当半減時の影響比率は${percent(value, 1)}で「${band}」帯の${position}です。$impact $positionContext この試算は減配確率や会社の財務状態を示さず、税引後の受取額も含みません。"
    }

    fun sectorConcentration(sector: String, value: Double, band: String, position: String): String {
        val sharedRisk = when {
            value <= 20.0 -> "同業種の景気や規制変化が同時に響く可能性は抑えられています。"
            value <= 30.0 -> "景気や規制など共通要因の影響を複数銘柄で受け得ます。"
            else -> "同業種の市況や規制変化が保有資産全体に響きやすい状態です。"
        }
        val positionContext = positionContext(position)
        return "${sector}の集中度は${percent(value, 1)}で「${band}」帯の${position}です。$sharedRisk $positionContext 評価額比率は配当の偏りや各社の支払力を示さないため、銘柄別配当比率も確認します。"
    }

    private fun positionWithin(value: Double, lower: Double, upper: Double): String {
        if (!lower.isFinite() || !upper.isFinite() || upper <= lower) return "中央付近"
        val index = (((value - lower) / (upper - lower)) * finePositionLabels.size)
            .toInt().coerceIn(finePositionLabels.indices)
        return finePositionLabels[index]
    }

    private fun positionContext(position: String): String = when (position) {
        "下端に近い位置" -> "帯の下端に近く、基準まで余裕があります。"
        "下端寄り" -> "帯の下側にあり、上の基準まで距離があります。"
        "下側寄り" -> "帯の下側ですが、中央に近づいています。"
        "中央より下" -> "中央より低く、上限まで間があります。"
        "中央付近" -> "帯の中央付近で、上下の境界から離れています。"
        "中央より上" -> "中央より高く、上限側に近づいています。"
        "上側寄り" -> "帯の上側にあり、次の基準が近づいています。"
        "上端寄り" -> "帯の上端寄りで、次の判定帯に近い水準です。"
        "上端に近い位置" -> "帯の上端に近く、次の判定帯との境界にあります。"
        "基準を下回る" -> "設定した0%基準を下回る水準です。"
        "基準を上回る" -> "設定した0%基準を上回る水準です。"
        "基準値" -> "設定した0%基準と同じ水準です。"
        "基準超過" -> "設定した上限を超える水準です。"
        else -> "目安の範囲や境界も確かめます。"
    }

    private fun percent(value: Double, digits: Int): String = String.format(Locale.JAPAN, "%.${digits}f%%", value)

    private fun signedPercent(value: Double): String = String.format(Locale.JAPAN, "%+.1f%%", value)
}
