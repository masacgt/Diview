package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.model.WatchlistStock
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistFilteringTest {
    @Test
    fun japaneseWatchlistYieldMatchesDisplayedDividendAndPrice() {
        val japanese = stock("7203", "Toyota", "jp", dps = 95.0, price = 3102.0)
            .copy(yfinanceYieldRate = 3.22, irbankYieldRate = 3.22, irbankYieldDps = 95.0)
        assertEquals(95.0 / 3102.0 * 100, japanese.liveYield, 0.000001)
        assertEquals(0.0, japanese.copy(isNoDividendConfirmed = true).liveYield, 0.0)
    }

    @Test
    fun emptyQueryPreservesAllStocksAndTheirOrder() {
        val stocks = listOf(stock("7203", "Toyota", "jp"), stock("AAPL", "Apple", "us"))

        assertEquals(stocks, filterWatchlistStocks(stocks, "", "all", emptyMap()))
    }

    @Test
    fun searchMatchesCodeNameAndSectorWithoutCaseSensitivity() {
        val stocks = listOf(
            stock("toy1", "Other", sector = "Retail"),
            stock("7203", "Toyota", sector = "Automotive"),
            stock("abc", "Other", sector = "TECH")
        )

        assertEquals(listOf(stocks[0]), filterWatchlistStocks(stocks, "TOY1", "all", emptyMap()))
        assertEquals(listOf(stocks[1]), filterWatchlistStocks(stocks, "toyota", "all", emptyMap()))
        assertEquals(listOf(stocks[2]), filterWatchlistStocks(stocks, "tech", "all", emptyMap()))
    }

    @Test
    fun categoryFiltersKeepOnlyJapaneseOrUsStocks() {
        val stocks = listOf(stock("7203", "Toyota", "jp"), stock("AAPL", "Apple", "us"))

        assertEquals(listOf(stocks[0]), filterWatchlistStocks(stocks, "", "jp", emptyMap()))
        assertEquals(listOf(stocks[1]), filterWatchlistStocks(stocks, "", "us", emptyMap()))
    }

    @Test
    fun readyFilterUsesCachedYieldThenCalculatedYieldAndIncludesThreshold() {
        val cached = stock("cached", "Cached", dps = 1.0, price = 100.0, targetYield = 4.0)
        val calculated = stock("calculated", "Calculated", dps = 5.0, price = 100.0, targetYield = 5.0)
        val belowThreshold = stock("below", "Below", dps = 5.0, price = 100.0, targetYield = 5.01)
        val noPrice = stock("no-price", "No price", dps = 5.0, price = 0.0, targetYield = 0.1)
        val invalidDividend = stock("invalid", "Invalid", dps = Double.NaN, targetYield = 0.1)
        val stocks = listOf(cached, calculated, belowThreshold, noPrice, invalidDividend)

        assertEquals(
            listOf(cached, calculated),
            filterWatchlistStocks(stocks, "", "ready", mapOf(cached.code to 4.0))
        )
    }

    private fun stock(
        code: String,
        name: String,
        category: String = "jp",
        sector: String = "Other",
        dps: Double = 4.0,
        price: Double = 100.0,
        targetYield: Double = 4.0
    ) = WatchlistStock(
        code = code,
        name = name,
        market = "Market",
        sector = sector,
        category = category,
        price = price,
        dps = dps,
        currentYield = 0.0,
        targetYield = targetYield,
        targetPrice = 0.0,
        dailyChange = "—",
        isPositive = true,
        payoutMonths = emptyList(),
        payoutTiming = "",
        desc = ""
    )
}
