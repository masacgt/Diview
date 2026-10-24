package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareSecurity

object StockDetailResolver {
    fun resolve(code: String, holdings: List<Holding>, watchlist: List<WatchlistStock>, security: CloudflareSecurity?): Holding? {
        holdings.firstOrNull { it.code.equals(code, true) }?.let { holding ->
            val noDividendConfirmed = when {
                security?.annualDividendPerShare?.let { it > 0.0 } == true -> false
                security?.hasDividendStatus == true -> security.isNoDividendConfirmed
                else -> holding.isNoDividendConfirmed
            }
            return holding.copy(isNoDividendConfirmed = noDividendConfirmed)
        }
        val stock = watchlist.firstOrNull { it.code.equals(code, true) }
        if (stock == null && (security == null || !security.code.equals(code, true))) return null
        val currency = stock?.currency ?: if (code.matches(Regex("[A-Za-z]+"))) "$" else "¥"
        val price = security?.marketPrice ?: stock?.price ?: 0.0
        val noDividendConfirmed = security?.annualDividendPerShare?.let { it > 0.0 } != true &&
            (if (security?.hasDividendStatus == true) security.isNoDividendConfirmed else
                security?.isNoDividendConfirmed == true || stock?.isNoDividendConfirmed == true)
        val dps = if (noDividendConfirmed) 0.0 else security?.annualDividendPerShare ?: stock?.dps ?: 0.0
        val name = security?.name ?: stock?.name ?: return null
        return Holding(
            "detail-${PortfolioMath.normalizeCode(code)}", PortfolioMath.normalizeCode(code), name,
            security?.industry ?: stock?.sector ?: "業種未登録", stock?.market ?: "東証",
            if (currency == "$") Category.US else Category.JAPAN, price, currency, 0, 0.0, dps,
            PortfolioMath.yieldPercent(dps, price), 0.0, stock?.payoutTiming ?: "配当月未取得", "—",
            stock?.payoutMonths ?: emptyList(), "", "未保有", stock?.dailyChange ?: "—", stock?.isPositive ?: true,
            usdJpyRate = stock?.usdJpyRate,
            isNoDividendConfirmed = noDividendConfirmed,
            irbankYieldRate = security?.marketYieldRate,
            irbankYieldDps = security?.annualDividendPerShare
        )
    }
}
