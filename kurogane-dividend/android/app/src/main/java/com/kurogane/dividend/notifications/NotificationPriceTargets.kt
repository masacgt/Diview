package com.kurogane.dividend.notifications

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.data.normalizeJapaneseSecurityCode

internal object NotificationPriceTargets {
    fun japaneseCodes(holdings: Iterable<Holding>, watchlist: Iterable<WatchlistStock>): List<String> =
        (holdings.asSequence()
            .filter { it.category == Category.JAPAN && it.shares > 0 }
            .map { it.code }
            .plus(watchlist.asSequence()
                .filter { it.category.equals("jp", ignoreCase = true) }
                .map { it.code }))
            .map(::normalizeJapaneseSecurityCode)
            .filter { it.matches(Regex("[A-Z0-9]{4,5}")) }
            .distinct()
            .toList()
}
