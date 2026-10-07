package com.kurogane.dividend.model

/** Same purchase units as the watchlist's existing investment estimate; unknown months stay unknown. */
class WatchlistProjection(val stocks: List<WatchlistStock>) {
    fun shares(stock: WatchlistStock): Int = if (stock.category == "us") 1 else 100
    fun annual(stock: WatchlistStock): Double = stock.dps * shares(stock) * stock.currencyFactor
    fun months(stock: WatchlistStock): List<Int> = stock.payoutMonths.filter { it in 1..12 }.distinct().sorted()
    fun monthly(stock: WatchlistStock, month: Int): Double {
        val months = months(stock)
        return if (month in months) annual(stock) / months.size else 0.0
    }
    val annualTotal = stocks.sumOf(::annual)
    val investment = stocks.sumOf { it.price * shares(it) * it.currencyFactor }
    val monthlyTotals = (1..12).map { month -> stocks.sumOf { monthly(it, month) } }
    val quarterlyTotals = monthlyTotals.chunked(3).map { it.sum() }
    val scheduledTotal = monthlyTotals.sum()
    val unallocatedCount = stocks.count { months(it).isEmpty() }
    val missingExchangeRate = stocks.any { it.currency == "$" && it.currencyFactor == 0.0 }
    val emptyMonths = (1..12).filter { monthlyTotals[it - 1] == 0.0 }
    val paymentCount = stocks.sumOf { months(it).size }
    val peakShare = if (scheduledTotal > 0) monthlyTotals.max() / scheduledTotal * 100 else 0.0
}
