package com.kurogane.dividend.model

data class Holding(
    val id: String,
    val code: String,
    val name: String,
    val sector: String,
    val market: String,
    val category: Category,
    val price: Double,
    val currency: String = "¥",
    val shares: Int,
    val acqPrice: Double,
    val dps: Double, // annual dividend per share
    val yieldRate: Double,
    val yoc: Double,
    val nextPayoutTiming: String,
    val nextPayoutAmount: String,
    val dividendMonths: List<Int>,
    val dividendType: String,
    val accountType: String,
    val dailyChange: String,
    val isPositive: Boolean,
    val taxFreeShares: Int? = null,
    val usdJpyRate: Double? = null,
    val usWithholdingEnabled: Boolean = true,
    val financialProfile: StockFinancialProfile? = null,
    val isNoDividendConfirmed: Boolean = false,
    val irbankYieldRate: Double? = null,
    val irbankYieldDps: Double? = null,
    val yfinanceYieldRate: Double? = null
) {
    val priceUnit: Double get() = if (category == Category.FUND) 10_000.0 else 1.0
    val effectiveTaxFreeShares: Int get() =
        (taxFreeShares ?: if (PortfolioMath.isTaxFree(accountType)) shares else 0).coerceIn(0, shares.coerceAtLeast(0))
    val currencyFactor: Double get() = when {
        !PortfolioMath.hasValidMoneyBasis(category, currency) -> 0.0
        currency == "$" -> usdJpyRate?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        else -> 1.0
    }
    val valuationJpy: JpyAmount? get() = PortfolioMath.holdingJpyAmount(this, price)
    val costJpy: JpyAmount? get() = PortfolioMath.holdingJpyAmount(this, acqPrice)
    val annualDividendJpy: JpyAmount? get() = PortfolioMath.holdingJpyAmount(this, dps)
    val totalValuation: Double get() = valuationJpy?.value ?: 0.0
    val totalCost: Double get() = costJpy?.value ?: 0.0
    val gainLoss: Double get() = totalValuation - totalCost
    val gainLossPercent: Double get() = if (totalCost > 0) (gainLoss / totalCost) * 100 else 0.0
    val annualDividend: Double get() = annualDividendJpy?.value ?: 0.0
    val liveYield: Double get() = yfinanceYieldRate
        ?.takeIf { it.isFinite() && it in 0.0..100.0 }
        ?: irbankYieldRate
        ?.takeIf { it.isFinite() && it in 0.0..100.0 && irbankYieldDps == dps }
        ?: PortfolioMath.yieldPercent(dps, price)
    val liveYoc: Double get() = PortfolioMath.yieldPercent(dps, acqPrice)

    fun getEffectiveAnnualDividend(taxRateMode: String): Double =
        getEffectiveAnnualDividendJpy(taxRateMode)?.value ?: 0.0

    fun getEffectiveAnnualDividendJpy(taxRateMode: String): JpyAmount? =
        effectiveDividendJpy(shares.toLong(), taxRateMode)

    fun getEffectiveDividendForShares(shareCount: Long, taxRateMode: String): Double {
        return effectiveDividendJpy(shareCount, taxRateMode)?.value ?: 0.0
    }

    private fun effectiveDividendJpy(shareCount: Long, taxRateMode: String): JpyAmount? {
        val gross = PortfolioMath.holdingJpyAmount(this, dps, shareCount.coerceAtLeast(0))?.value ?: return null
        val net = if (taxRateMode == "net") {
            val freeRatio = if (shares > 0) effectiveTaxFreeShares.toDouble() / shares else if (PortfolioMath.isTaxFree(accountType)) 1.0 else 0.0
            DividendTax.net(gross, freeRatio, category == Category.US && usWithholdingEnabled)
        } else {
            gross
        }
        return net.takeIf(Double::isFinite)?.let(::JpyAmount)
    }
}

data class ShareholderBenefit(
    val name: String = "",
    val marketValue: String = ""
)

enum class StockClimate(val label: String) {
    CYCLICAL("景気敏感株"),
    DEFENSIVE("ディフェンシブ株"),
    NEUTRAL("景気中立株")
}

data class StockFinancialProfile(
    val payoutRatio: Double = Double.NaN,
    val equityRatio: Double = Double.NaN,
    val netDeRatio: String = "未取得",
    val healthGrade: String = "未判定",
    val healthEvaluation: String = "取得済み指標で診断",
    val consecutiveYears: Int = -1,
    val noCutYears: Int = -1,
    val growthRate: Double = Double.NaN,
    val eps: Double = Double.NaN,
    val bps: Double = Double.NaN,
    val recordMonths: String = "未取得",
    val payoutSchedule: String = "未取得",
    val payoutStatusBadge: String = "未取得",
    val equityRatioSub: String = "未取得",
    val calculatedDoe: String = "未取得",
    val calculatedDoeSub: String = "未取得",
    val operatingMargin: Double = Double.NaN,
    val fcfMargin: Double? = null,
    val fcfStatus: String = "未取得",
    val modelType: String = "決算実績",
    val healthScore: Int = -1,
    val healthDiagnosisComment: String = "財務データ未取得",
    val valuationScore: Int = -1,
    val yieldRangeMin: Double = Double.NaN,
    val yieldRangeAvg: Double = Double.NaN,
    val yieldRangeMax: Double = Double.NaN,
    val yieldRangeBadge: String = "未取得",
    val perEvaluation: String = "決算EPSから算出",
    val pbrEvaluation: String = "決算BPSから算出",
    val buySignalZone: String = "取得済み指標を確認",
    val buySignalTitle: String = "株価と決算データによる評価",
    val buySignalBadge: String = "指標別診断を確認",
    val buySignalComment: String = "未取得項目を補完せず、取得済みの指標のみで診断します。"
) {
    fun getPer(price: Double, reportedPer: Double? = null): String {
        val value = reportedPer?.takeIf { it.isFinite() }
            ?: (price / eps).takeIf { eps.isFinite() && eps > 0 && price > 0 }
        return value?.let { String.format(java.util.Locale.JAPAN, "%.1f倍", it) } ?: "未取得"
    }
    fun getPbr(price: Double, reportedPbr: Double? = null): String {
        val value = reportedPbr?.takeIf { it.isFinite() }
            ?: (price / bps).takeIf { bps.isFinite() && bps > 0 && price > 0 }
        return value?.let { String.format(java.util.Locale.JAPAN, "%.2f倍", it) } ?: "未取得"
    }
    fun getValuationStatus(price: Double, dps: Double): String {
        if (!eps.isFinite() || eps <= 0 || price <= 0) return "未判定"
        val per = price / eps
        return when { per < 10 -> "低PER・指標を確認"; per > 25 -> "高PER・指標を確認"; else -> "PER中間帯・指標を確認" }
    }
}

enum class Category {
    JAPAN, US, FUND
}

data class WatchlistStock(
    val code: String,
    val name: String,
    val market: String,
    val sector: String,
    val category: String, // "jp" or "us"
    val price: Double,
    val currency: String = "¥",
    val dps: Double,
    val currentYield: Double,
    val targetYield: Double,
    val targetPrice: Double,
    val dailyChange: String,
    val isPositive: Boolean,
    val payoutMonths: List<Int>,
    val payoutTiming: String,
    val desc: String,
    val alertEnabled: Boolean = false,
    val badge: String? = null,
    val defaultShares: Int = 100,
    val usdJpyRate: Double? = null,
    val isNoDividendConfirmed: Boolean = false,
    val yfinanceYieldRate: Double? = null,
    val irbankYieldRate: Double? = null,
    val irbankYieldDps: Double? = null
) {
    val currencyFactor: Double get() = when {
        !PortfolioMath.hasValidMoneyBasis(category, currency) -> 0.0
        currency == "$" -> usdJpyRate?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        else -> 1.0
    }
    val liveYield: Double get() = yfinanceYieldRate
        ?.takeIf { it.isFinite() && it in 0.0..100.0 }
        ?: irbankYieldRate?.takeIf { it.isFinite() && it in 0.0..100.0 && irbankYieldDps == dps }
        ?: PortfolioMath.yieldPercent(dps, price)
    val isReady: Boolean get() = targetYield.isFinite() && targetYield > 0.0 && currentYield.isFinite() && currentYield >= targetYield
    val achievementRate: Int get() = if (targetYield.isFinite() && targetYield > 0.0 && currentYield.isFinite())
        ((currentYield / targetYield) * 100).toInt().coerceIn(0, 100) else 0
}

data class RoadmapStep(
    val step: Int,
    val level: Int,
    val name: String,
    val title: String,
    val description: String,
    val iconName: String,
    val targetMonthly: Int,
    val targetAnnual: Int,
    val status: StepStatus,
    val statusLabel: String,
    val currentRate: Double,
    val rateLabel: String,
    val shortageMonthly: Int = 0,
    val shortageAnnual: Int = 0,
    val yield35Capital: String = "",
    val yield40Capital: String = "",
    val yield45Capital: String = ""
)

enum class StepStatus {
    COMPLETED, IN_PROGRESS, LOCKED
}

data class MonthlyDividend(
    val month: Int,
    val amount: Int,
    val label: String
)
