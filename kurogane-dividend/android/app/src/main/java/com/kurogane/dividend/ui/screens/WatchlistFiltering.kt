package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.WatchlistStock

internal fun filterWatchlistStocks(
    stocks: List<WatchlistStock>,
    query: String,
    filter: String,
    currentYields: Map<String, Double>
): List<WatchlistStock> = stocks.filter { stock ->
    val currentYield = currentYields[stock.code]
        ?: PortfolioMath.yieldPercent(stock.dps, stock.price)
    val queryMatches = query.isEmpty() ||
        stock.code.contains(query, ignoreCase = true) ||
        stock.name.contains(query, ignoreCase = true) ||
        stock.sector.contains(query, ignoreCase = true)
    val categoryMatches = when (filter) {
        "jp" -> stock.category == "jp"
        "us" -> stock.category == "us"
        "ready" -> currentYield >= stock.targetYield
        else -> true
    }
    categoryMatches && queryMatches
}
