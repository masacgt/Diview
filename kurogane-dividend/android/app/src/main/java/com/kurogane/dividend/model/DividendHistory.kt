package com.kurogane.dividend.model

import com.kurogane.dividend.data.FinancialData

data class PortfolioDividendYear(val year: Int, val amount: Double?, val covered: Int, val expected: Int)

object DividendHistory {
    fun calculate(holdings: List<Holding>, data: Map<String, FinancialData>, years: IntRange, taxMode: String): List<PortfolioDividendYear> {
        val domestic = holdings.filter { it.category == Category.JAPAN && it.shares > 0 }
        return years.map { year ->
            var covered = 0
            var amount = 0.0
            domestic.forEach { holding ->
                val dividend = data[holding.code]?.dividends?.firstOrNull { it.year == year } ?: return@forEach
                covered++
                amount += holding.copy(dps = dividend.dps).getEffectiveAnnualDividend(taxMode)
            }
            PortfolioDividendYear(year, amount.takeIf { covered > 0 }, covered, domestic.size)
        }
    }
}
