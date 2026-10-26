package com.kurogane.dividend.model

/** Body copy complements the row label; quoted values and rating/position labels stay in the header. */
object StockDetailCommentGenerator {
    fun dividendYield(current: Double, minimum: Double, average: Double, maximum: Double,
                      context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = when {
            !minimum.isFinite() || !average.isFinite() || !maximum.isFinite() -> when {
                current == 0.0 -> "株価に対する配当収入が計上されておらず、実際の配当額と方針を確かめます。"
                current >= 4 -> "購入額に対する配当収入は大きい一方、株価下落や減配懸念の影響も見ます。"
                else -> "購入額に対する配当収入を示す値で、株価と予想配当のどちらが動いたかも見ます。"
            }
            current < minimum -> "株価上昇か予想配当の低下で、購入額に対する配当水準が切り下がった可能性があります。"
            current > maximum * 1.5 -> "非常に高い利回りには、株価急落や減配懸念が含まれる可能性があります。"
            current > maximum -> "購入額に対する配当水準は高い一方、株価下落や減配懸念も確かめます。"
            current < average -> "購入額に対する配当収入は控えめで、配当増額と株価変動を分けて見ます。"
            else -> "購入額に対する配当収入を得やすい一方、株価下落が背景かも確かめます。"
        }
        val limitation = if (minimum.isFinite() && average.isFinite() && maximum.isFinite())
            "予想配当と期末実績は時点が異なり、安全性は利益とFCFも見ます。"
            else "利回りだけでは配当の安全性は分からず、利益とFCFも確認します。"
        return StockMetricCommentary.contextual(base, "dividend_yield", context, limitation)
    }

    fun dividendIncreaseYears(years: Int, band: String, position: String,
                              context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = StockMetricCommentary.extreme("dividend_growth_years", years.toDouble()) ?: when {
            years <= 3 -> "数年の増配実績は還元方針の一貫性を示しますが、景気変動への耐性も見ます。"
            years <= 5 -> "配当を伸ばしてきた実績があり、利益成長に沿った還元かを確認します。"
            else -> "複数の環境で配当を増やした実績は、還元姿勢を支える材料です。"
        }
        return StockMetricCommentary.contextual(base, "dividend_growth_years", context,
            "年数だけで将来余力は分からず、増配幅、配当性向とFCFも見ます。")
    }

    fun nonCutYears(years: Int, band: String, position: String,
                    context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = when {
            years == 0 -> "直近では配当維持が連続しておらず、減配の背景と支払余力を確かめます。"
            years >= 20 -> "長期に配当を下げなかった実績は強みですが、事業環境の変化も見ます。"
            years >= 10 -> "長めの配当維持実績は還元姿勢を支える一方、今後の余力は別に見ます。"
            else -> "配当を下げなかった実績があり、業績変動時に維持できた背景も確認します。"
        }
        return StockMetricCommentary.contextual(base, "non_cut_years", context,
            "据え置きも含むため、増配の勢いは利益や各期の配当額も見ます。")
    }

    fun annualDividendCagr(rate: Double, isCalendarYearBasis: Boolean, band: String, position: String,
                           context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = when {
            rate < -5 -> "配当の減少が大きく、減配後の回復と利益の安定性が重要になります。"
            rate < 0 -> "配当が減少しており、減配の理由と足元の支払余力を確認します。"
            rate == 0.0 -> "期間の始点と終点は同水準ですが、途中の増配と減配が相殺される場合もあります。"
            rate < 5 -> "配当は緩やかに増えており、利益や現金創出の成長が伴うかを見ます。"
            else -> "配当は大きく伸びており、継続的な利益成長か一時的な増額かを見ます。"
        }
        val limitation = if (isCalendarYearBasis) "暦年と決算期は異なり、年率平均は途中の減配や急増を示しません。"
            else "年率平均は途中の減配や急増を示さず、配当性向とFCFも確認します。"
        return StockMetricCommentary.contextual(base, "dividend_cagr", context, limitation)
    }

    fun dividendDependency(value: Double, band: String, position: String,
                           context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = when {
            value > 50 -> "配当収入の過半を一社に頼り、減配が保有全体の受取額に大きく響きます。"
            value > 25 -> "一社への依存が大きく、減配時の影響を他銘柄で補いにくい構成です。"
            value > 10 -> "配当収入の柱になる一方、一社の減配で受取額が目立って減る構成です。"
            else -> "配当収入の一社への偏りは抑えられていますが、同時減配の影響も見ます。"
        }
        return StockMetricCommentary.risk(base, context,
            "保有配当の構成比から会社の支払力は分からず、業種の重なりも確認します。")
    }

    fun halfDividendImpact(value: Double, band: String, position: String,
                           context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = when {
            value > 25 -> "一社の配当半減だけで全体の受取額が大きく減り、収入源の分散が重要です。"
            value > 12.5 -> "一社の配当半減が全体の受取額を強く押し下げる試算です。"
            value > 5 -> "一社の配当半減が保有全体の配当収入を目立って減らす試算です。"
            else -> "一社の配当半減による全体への影響は比較的小さく、収入の偏りは抑えめです。"
        }
        return StockMetricCommentary.risk(base, context,
            "半減を仮定した試算で減配確率は示さず、同時減配や税引後額も含みません。")
    }

    fun sectorConcentration(sector: String, value: Double, band: String, position: String,
                            context: StockAnalysisContext = StockAnalysisContext()): String {
        val base = when {
            value > 50 -> "保有評価額の過半が同業種に偏り、市況や規制変化が全体に強く響き得ます。"
            value > 30 -> "同業種に資産が偏り、共通の景気・規制要因で複数銘柄が影響を受けやすい構成です。"
            value > 20 -> "景気や規制など共通要因の影響を、複数の保有銘柄で受ける可能性があります。"
            else -> "同業種への資産の偏りは抑えめですが、事業や顧客の重なりも確かめます。"
        }
        return StockMetricCommentary.risk(base, context,
            "評価額の比率は配当収入の偏りや各社の支払力を示さず、銘柄別配当比率も見ます。")
    }
}
