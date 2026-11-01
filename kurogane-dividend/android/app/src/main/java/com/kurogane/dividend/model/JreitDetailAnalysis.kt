package com.kurogane.dividend.model

import java.util.Locale

/** Product category comes from the exchange's security master, not a name or sector guess. */
internal fun isJreitProduct(category: Category, productCategory: String?, jpxMarket: String?): Boolean =
    category == Category.JAPAN && jpxMarket == "reit" && (productCategory == null || productCategory == "013")

internal data class JreitMetricExplanation(
    val key: String,
    val label: String,
    val value: String,
    val text: String
)

internal object JreitDetailAnalysis {
    private data class Metric(val key: String, val label: String, val unit: String, val text: String)

    private val metrics = listOf(
        Metric("per", "PER", "倍", "利益に対する投資口価格の倍率です。物件売却益などで利益が変動するため、単年の倍率だけで割安さや分配の継続性は判断できません。"),
        Metric("pbr", "PBR", "倍", "帳簿上の純資産に対する投資口価格の倍率です。不動産の鑑定評価額を反映するNAV倍率とは異なります。"),
        Metric("payout_ratio", "分配性向", "%", "J-REITは利益の大部分を分配する仕組みのため、100%前後という値だけで分配の継続性は判断できません。利益超過分配や一時的な売却益の有無、分配金の内訳を確認します。"),
        Metric("equity_ratio", "自己資本比率", "%", "資産に対する自己資本の割合です。借入への依存度を見る材料ですが、LTVとは異なります。借入金の返済期限や金利も確認します。"),
        Metric("operating_margin", "営業利益率", "%", "営業収益に対する営業利益の割合です。賃料や稼働率、物件売却益などの影響を受けるため、内訳と複数期の推移を確認します。"),
        Metric("fcf_margin", "FCF余力", "%", "営業収益に対するフリーキャッシュフローの割合です。物件取得や売却の時期に大きく左右されるため、単期の増減だけで分配余力を判断できません。"),
        Metric("dividend_growth_years", "連続増配年数", "年", "1口当たり分配金が増加した期間の参考値です。分配金は売却益や利益超過分配の影響も受けるため、内訳と各期の実績を確認します。")
    )

    fun explanations(values: Map<String, Double?>): List<JreitMetricExplanation> = metrics.map { metric ->
        val value = values[metric.key]?.takeIf { it.isFinite() && (metric.key != "dividend_growth_years" || it >= 0) }
        val formatted = when {
            value == null -> "未取得"
            metric.key == "dividend_growth_years" -> String.format(Locale.JAPAN, "%d%s", value.toInt(), metric.unit)
            else -> String.format(Locale.JAPAN, "%.2f%s", value, metric.unit)
        }
        val prefix = if (value == null) "数値は未取得です。" else if (metric.key == "payout_ratio" && value < 0) "負の値は利益の符号などを確認する必要があります。" else ""
        JreitMetricExplanation(metric.key, metric.label, formatted, prefix + metric.text)
    }
}
