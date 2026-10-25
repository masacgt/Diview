package com.kurogane.dividend.model

import java.util.Locale

/** Long-form comments for the indicator rows in the four Stock Detail analysis tabs. */
object StockDetailCommentGenerator {
    fun dividendYield(current: Double, minimum: Double, average: Double, maximum: Double): String {
        val rangePosition = when {
            current < minimum -> "過去最低を下回る位置"
            current > maximum -> "過去最高を上回る位置"
            current < average -> "過去平均を下回る位置"
            else -> "過去平均以上の位置"
        }
        val context = when {
            current < minimum -> "過去最低を下回る値は、株価上昇で利回りが下がった場合も、予想配当の減少で水準が切り下がった場合もあります。"
            current > maximum -> "過去最高を上回る利回りは取得価格に対する収入が高い材料ですが、株価下落や減配懸念が反映された可能性もあります。"
            current < average -> "過去平均を下回るため、取得価格に対する配当水準は過去平均より控えめです。株価上昇か予想DPSの低下かを見分けます。"
            else -> "過去平均以上の水準は、取得価格に対する配当収入が相対的に高い材料ですが、株価下落が理由なら注意が必要です。"
        }
        val values = listOf(
            "現在利回りは${percent(current, 2)}で、直近最大5期の期末実績は最低${percent(minimum, 2)}・平均${percent(average, 2)}・最高${percent(maximum, 2)}、${rangePosition}です。",
            context,
            "利回りは予想配当を株価で割った値なので、株価の変動だけでも上下し、高い値が配当の強さを保証するわけではありません。",
            "この指標だけでは配当の安全性や株価の割安さは判断できないため、配当性向、FCF、業績見通し、予想DPSの根拠も合わせて確認します。"
        )
        return values.joinToString("")
    }

    fun dividendIncreaseYears(years: Int): String {
        val history = when {
            years <= 1 -> "増配履歴はまだ短く、株主還元の継続性を判断する材料は限られます。"
            years <= 3 -> "数年の増配は還元方針の一貫性を示す一方、長期の景気変動を経験した実績とは限りません。"
            years <= 5 -> "複数年の増配実績は還元姿勢の強みですが、利益成長が続いているかは別途確認が必要です。"
            years <= 10 -> "長めの増配履歴は配当を引き上げてきた実績として支えになりますが、今後の余力を直接示すものではありません。"
            else -> "長期の増配履歴は複数の環境下で還元を続けた強みですが、過去の実績だけで将来の増配は確定しません。"
        }
        return "連続増配年数は${years}年で、配当を引き上げてきた実績は株主還元を続ける姿勢を示す材料です。$history 増配が利益やキャッシュフローの成長に沿っているなら、将来の配当維持を支える力になります。増配年数だけでは増配額の大きさや、現在の配当を利益・現金で無理なく賄えるかは分かりません。配当性向、FCF、利益推移、一株配当の増額幅や業績が落ち込んだ期の対応を合わせて確認します。"
    }

    fun nonCutYears(years: Int): String {
        val history = if (years == 0) {
            "最新の連続比較期間では非減配実績を確認できず、直近の減配や履歴の区切れが影響している可能性があります。"
        } else {
            "少なくとも${years}年の連続した比較履歴で年間配当を下げていない点は、過去の維持実績として評価できます。"
        }
        return "非減配年数は${years}年です。${history}この年数には横ばい配当も含まれるため、増配の勢いや配当額の伸びを示す指標ではありません。過去の記録は将来の維持を保証せず、利益や現金収支が弱まれば方針も変わり得ます。比較した期間の利益や投資負担が平常時だったか、減配がなかった理由や増配を伴ったかも見て、配当性向、FCF、配当履歴を合わせて確認します。"
    }

    fun annualDividendCagr(rate: Double, isCalendarYearBasis: Boolean): String {
        val trend = when {
            rate > 0.0 -> "配当額は比較期間の始点から終点にかけて増加しています。"
            rate < 0.0 -> "配当額は比較期間の始点から終点にかけて減少しており、配当水準の回復状況に注意が必要です。"
            else -> "比較期間の始点と終点の配当額は同じで、全体として横ばいです。"
        }
        val basis = if (isCalendarYearBasis) {
            "暦年に観測された配当合計の参考値で、決算期の年間配当とは集計期間が異なる場合があります。"
        } else {
            "直近最大5期の年率換算であり、毎年同じ率で増減したことを示すものではありません。"
        }
        return "年間配当CAGRは${signedPercent(rate)}で、比較期間の配当額を年率に換算した変化を示します。$trend CAGRは途中の減配や急増を平均化するため、毎期の動きや配当継続の余力までは分かりません。$basis 増配の継続性を見るには、率だけでなく各期の配当額、減配や急増が起きた時期を追い、配当性向、FCFと利益の変化も確認します。"
    }

    fun dividendDependency(value: Double, band: String, position: String): String {
        return "銘柄配当依存度は${percent(value, 1)}で、保有全体の年間配当に占めるこの銘柄の割合を示し、「${band}」帯の${position}にあります。この比率が高いほど配当収入の柱になる一方、減配時に保有全体の受取額が落ちる影響も大きくなります。一社に偏る度合いは保有銘柄数ではなく各銘柄の配当額の重みで決まり、保有株数や予想配当の更新でも変動します。これは保有配当の集中度であり、この会社自身が配当を続ける力は評価しません。業種の重なり、配当性向、利益とFCFを別に確認します。"
    }

    fun halfDividendImpact(value: Double, band: String, position: String): String {
        return "配当半減時の影響比率は${percent(value, 1)}で、この銘柄の年間配当が半分になる仮定で保有全体に及ぶ減少割合を示します。「${band}」帯の${position}にあり、値が大きいほど一社の減配が受取額に響きやすい状態です。ただし、これは影響を測る試算で、減配の確率や会社の財務状態を示すものではありません。実際の受取額は保有株数や税区分でも変わるため、他銘柄との同時減配や保有配当の集中を併せて確認し、株価変動や税引後受取額はこの試算に含まれない点にも留意します。"
    }

    fun sectorConcentration(sector: String, value: Double, band: String, position: String): String {
        return "${sector}セクター集中度は${percent(value, 1)}で、保有評価額に占める同セクターの割合を示します。「${band}」帯の${position}で、集中が高いほど景気、規制、商品市況など共通要因の影響を複数銘柄で受けやすくなります。ただし業種内でも事業内容、顧客、仕入れ環境は異なり、全銘柄が同じ影響を受けるとは限りません。評価額の比率は株価変動でも上下し、配当額の偏りや各社の配当支払力、事業構成までは示しません。銘柄別配当比率、配当性向、FCFと収益源も確認します。"
    }

    private fun percent(value: Double, digits: Int): String = String.format(Locale.JAPAN, "%.${digits}f%%", value)

    private fun signedPercent(value: Double): String = String.format(Locale.JAPAN, "%+.1f%%", value)
}
