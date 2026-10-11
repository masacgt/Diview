package com.kurogane.dividend.model

import com.kurogane.dividend.data.FinancialData

/** One row per domestic security, combining holdings across accounts. */
data class DividendChangeSecurity(
    val code: String,
    val name: String,
    val sector: String,
    val shares: Long,
    val annualDividend: Double,
    val previousYear: Int?,
    val currentYear: Int?,
    val previousDps: Double?,
    val currentDps: Double?,
    val previousAnnualDividend: Double?,
    val annualImpact: Double?,
    val profile: StockFinancialProfile?,
    val dividendHistoryBasis: String = "fiscal_year_reported"
) {
    val change: DividendChangeStatus get() = when {
        previousDps == null || currentDps == null -> DividendChangeStatus.UNKNOWN
        currentDps > previousDps -> DividendChangeStatus.INCREASE
        currentDps < previousDps -> DividendChangeStatus.DECREASE
        else -> DividendChangeStatus.MAINTAIN
    }
    val growthRate: Double? get() = previousDps?.takeIf { it > 0 }?.let {
        currentDps?.let { current -> (current / it - 1) * 100 }
    }
    val payoutRatio: Double? get() = profile?.payoutRatio?.takeIf { it.isFinite() }
    val requiresMonitoring: Boolean get() = change == DividendChangeStatus.DECREASE ||
        payoutRatio?.let { it > 80 || it < 0 } == true
}

enum class DividendChangeStatus { INCREASE, MAINTAIN, DECREASE, UNKNOWN }

data class DividendChangeAnalysis(val securities: List<DividendChangeSecurity>) {
    val compared get() = securities.filter { it.annualImpact != null }
    val payoutCovered get() = securities.filter { it.payoutRatio != null }
    val impact: Double? get() = compared.takeIf { it.isNotEmpty() }?.sumOf { it.annualImpact!! }
    val positiveImpact get() = compared.sumOf { it.annualImpact!!.coerceAtLeast(0.0) }
    val negativeImpact get() = compared.sumOf { it.annualImpact!!.coerceAtMost(0.0) }
    val growthRate: Double? get() = compared.sumOf { it.previousAnnualDividend ?: 0.0 }
        .takeIf { it > 0.0 }?.let { impact!! / it * 100 }
    val monitoring get() = securities.filter { it.requiresMonitoring }
    val highPayout get() = payoutCovered.filter { it.payoutRatio!! > 80 || it.payoutRatio!! < 0 }
    val highPayoutDividendShare: Double? get() = payoutCovered.sumOf { it.annualDividend }
        .takeIf { it > 0 }?.let { highPayout.sumOf { row -> row.annualDividend } / it * 100 }
    // A transparent screening indicator, not a prediction of a dividend cut.
    val headroomScore: Int? get() = payoutCovered.takeIf { it.isNotEmpty() }?.let { rows ->
        (rows.count { it.payoutRatio!! in 0.0..70.0 }.toDouble() / rows.size * 100).toInt()
    }

    companion object {
        fun calculate(
            holdings: List<Holding>,
            financialData: Map<String, FinancialData>,
            taxRateMode: String
        ): DividendChangeAnalysis = DividendChangeAnalysis(
            holdings.filter { it.category == Category.JAPAN && it.shares > 0 }
                .groupBy { it.code }.map { (code, lots) ->
                    val holding = lots.first()
                    val data = financialData[code]
                    val years = data?.dividends.orEmpty()
                        .filter { it.dps.isFinite() && it.dps >= 0 }
                        .distinctBy { it.year }.sortedBy { it.year }.takeLast(2)
                        .takeIf { it.size == 2 && it[1].year == it[0].year + 1 }
                    val previous = years?.first()
                    val current = years?.last()
                    val previousAmount = previous?.let { dividend ->
                        lots.sumOf { it.copy(dps = dividend.dps).getEffectiveAnnualDividend(taxRateMode) }
                    }
                    val currentAmount = current?.let { dividend ->
                        lots.sumOf { it.copy(dps = dividend.dps).getEffectiveAnnualDividend(taxRateMode) }
                    }
                    DividendChangeSecurity(
                        code = code,
                        name = holding.name,
                        sector = holding.sector,
                        shares = lots.sumOf { it.shares.toLong() },
                        annualDividend = lots.sumOf { it.getEffectiveAnnualDividend(taxRateMode) },
                        previousYear = previous?.year,
                        currentYear = current?.year,
                        previousDps = previous?.dps,
                        currentDps = current?.dps,
                        previousAnnualDividend = previousAmount,
                        annualImpact = currentAmount?.let { it - previousAmount!! },
                        profile = data?.profile ?: lots.firstNotNullOfOrNull { it.financialProfile },
                        dividendHistoryBasis = data?.dividendHistoryBasis ?: "fiscal_year_reported"
                    )
                }
        )
    }
}
