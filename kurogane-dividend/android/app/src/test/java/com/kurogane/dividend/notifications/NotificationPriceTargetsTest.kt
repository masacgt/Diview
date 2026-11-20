package com.kurogane.dividend.notifications

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPriceTargetsTest {
    @Test fun includesJapaneseHoldingsAndWatchlistOnlyAndRemovesDuplicates() {
        val holdings = listOf(
            holding("7203.T", Category.JAPAN, 100),
            holding("72030", Category.JAPAN, 10),
            holding("8306", Category.JAPAN, 0),
            holding("AAPL", Category.US, 10),
            holding("1306", Category.FUND, 10)
        )
        val watchlist = listOf(
            watch("7203", "jp"),
            watch("130A", "JP"),
            watch("MSFT", "us"),
            watch("25935", "jp")
        )

        assertEquals(listOf("7203", "130A", "25935"), NotificationPriceTargets.japaneseCodes(holdings, watchlist))
    }

    private fun holding(code: String, category: Category, shares: Int) = Holding(
        id = code,
        code = code,
        name = code,
        sector = "",
        market = "",
        category = category,
        price = 0.0,
        shares = shares,
        acqPrice = 0.0,
        dps = 0.0,
        yieldRate = 0.0,
        yoc = 0.0,
        nextPayoutTiming = "",
        nextPayoutAmount = "",
        dividendMonths = emptyList(),
        dividendType = "",
        accountType = "",
        dailyChange = "",
        isPositive = false
    )

    private fun watch(code: String, category: String) = WatchlistStock(
        code = code,
        name = code,
        market = "",
        sector = "",
        category = category,
        price = 0.0,
        dps = 0.0,
        currentYield = 0.0,
        targetYield = 0.0,
        targetPrice = 0.0,
        dailyChange = "",
        isPositive = false,
        payoutMonths = emptyList(),
        payoutTiming = "",
        desc = ""
    )
}
