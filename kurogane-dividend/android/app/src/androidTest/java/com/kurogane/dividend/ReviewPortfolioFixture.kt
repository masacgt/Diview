package com.kurogane.dividend

import android.app.Application
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock

/** Tests own these records; a fresh production installation intentionally starts empty. */
internal fun seedReviewPortfolio(application: Application) {
    val toyota = Holding(
        id = "review-toyota", code = "7203", name = "トヨタ自動車", sector = "輸送用機器", market = "東証",
        category = Category.JAPAN, price = 2600.0, shares = 100, acqPrice = 2000.0,
        dps = 75.0, yieldRate = 75.0 / 2600 * 100, yoc = 3.75,
        nextPayoutTiming = "未取得", nextPayoutAmount = "未取得", dividendMonths = emptyList(),
        dividendType = "配当金", accountType = "特定口座", dailyChange = "—", isPositive = true
    )
    val jt = toyota.copy(id = "review-jt", code = "2914", name = "日本たばこ産業", sector = "食料品")
    val apple = toyota.copy(id = "review-apple", code = "AAPL", name = "アップル", sector = "情報技術",
        market = "NASDAQ", category = Category.US, currency = "$", price = 200.0, acqPrice = 150.0,
        dps = 1.0, yieldRate = .5, yoc = 1.0 / 150 * 100)
    val store = LocalImportStore(application)
    store.saveHoldings(listOf(toyota, jt, apple))
    store.saveWatchlist(listOf(toyota, jt).map { stock -> WatchlistStock(
        code = stock.code, name = stock.name, market = stock.market, sector = stock.sector, category = "jp",
        price = stock.price, dps = stock.dps, currentYield = stock.yieldRate, targetYield = 3.0,
        targetPrice = stock.dps / .03, dailyChange = "—", isPositive = true,
        payoutMonths = emptyList(), payoutTiming = "未取得", desc = "検証データ"
    ) })
}
