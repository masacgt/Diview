package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.HoldingAllocation
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.PortfolioMath.refreshPayout

object PortfolioImporter {
    fun importRows(current: List<Holding>, rows: List<CsvHoldingRow>, fields: Set<String>,
                   industries: Map<String, String> = emptyMap()): List<Holding> {
        val next = current.toMutableList()
        rows.filter { it.selected }.groupBy { PortfolioMath.normalizeCode(it.code) }.forEach { (code, group) ->
            require(PortfolioMath.validCode(code) && group.all { it.name.isNotBlank() }) { "銘柄コード・名称を確認してください" }
            val index = next.indexOfFirst { it.code.equals(code, true) }
            val old = next.getOrNull(index)
            val first = group.first()
            require(group.all { it.isFund == first.isFund }) { "銘柄区分が一致しません" }
            val allocations = group.map { row ->
                val shares = if ("shares" in fields) PortfolioMath.wholeShares(row.shares)
                    ?: throw IllegalArgumentException("保有数量は0以上の整数で指定してください") else 0
                val cost = if ("averageCost" in fields) row.averageCost ?: old?.acqPrice ?: 0.0 else old?.acqPrice ?: 0.0
                require(PortfolioMath.validMoney(cost)) { "取得単価が正しくありません" }
                HoldingAllocation(shares, cost,
                    if ("account" in fields) row.account?.takeIf(String::isNotBlank) ?: old?.accountType ?: "特定口座"
                    else old?.accountType ?: "特定口座")
            }
            val total = allocations.sumOf { it.shares.toLong() }
            require(total <= Int.MAX_VALUE) { "保有数量が上限を超えています" }
            val shares = if ("shares" in fields) total.toInt() else old?.shares ?: 0
            val cost = if ("averageCost" in fields) {
                val weight = group.sumOf { PortfolioMath.wholeShares(it.shares)?.toLong() ?: 0L }
                if (weight > 0) group.sumOf { (PortfolioMath.wholeShares(it.shares) ?: 0) * (it.averageCost ?: old?.acqPrice ?: 0.0) } / weight
                else allocations.first().acquisitionPrice
            } else old?.acqPrice ?: 0.0
            val price = if ("currentPrice" in fields) group.firstNotNullOfOrNull { it.currentPrice } ?: old?.price ?: 0.0 else old?.price ?: 0.0
            require(PortfolioMath.validMoney(price) && PortfolioMath.validMoney(cost)) { "単価が正しくありません" }
            val currency = first.currency ?: old?.currency ?: if (!first.isFund && code.matches(Regex("[A-Z]+"))) "$" else "¥"
            val category = if (first.isFund) Category.FUND else if (currency == "$") Category.US else Category.JAPAN
            val activeAccounts = allocations.filter { it.shares > 0 || "shares" !in fields }.map { it.accountType }.distinct()
            val account = if ("account" in fields) activeAccounts.singleOrNull() ?: if (activeAccounts.isEmpty()) old?.accountType ?: "特定口座" else "複数口座" else old?.accountType ?: "特定口座"
            val taxFree = if ("account" in fields && "shares" in fields) allocations.filter { PortfolioMath.isTaxFree(it.accountType) }.sumOf { it.shares }
                else if ("account" in fields) if (PortfolioMath.isTaxFree(account)) shares else 0
                else old?.let { if (it.shares > 0) (shares.toDouble() * it.effectiveTaxFreeShares / it.shares).toInt() else 0 } ?: 0
            val template = old ?: Holding("import-$code", code, first.name,
                if (category == Category.FUND) "投資信託" else industries[code]?.let { "東証 • $it" } ?: "業種未登録",
                if (category == Category.US) "米国株" else if (category == Category.FUND) "投資信託" else "東証",
                category, price, currency, shares, cost, 0.0, 0.0, 0.0, "未登録", "¥0", emptyList(), "", account, "—", true)
            val imported = template.copy(name = first.name, shares = shares, acqPrice = cost, price = price,
                accountType = account, taxFreeShares = taxFree).refreshPayout()
            if (index >= 0) next[index] = imported else next += imported
        }
        return next
    }

    fun importStocks(current: List<Holding>, stocks: List<DvmStock>): List<Holding> =
        importRows(current, stocks.flatMap { stock ->
            val free = stock.taxFreeShares ?: if (PortfolioMath.isTaxFree(stock.account)) stock.shares else 0.0
            require(PortfolioMath.wholeShares(free) != null && free <= stock.shares) { "非課税数量が正しくありません" }
            listOf(CsvHoldingRow(stock.code, stock.name, free, stock.averageCost, stock.currentPrice,
                "NISA", stock.code.startsWith("FUND:")),
                CsvHoldingRow(stock.code, stock.name, stock.shares - free, stock.averageCost, stock.currentPrice,
                    if (PortfolioMath.isTaxFree(stock.account)) "特定口座" else stock.account, stock.code.startsWith("FUND:")))
        }, setOf("shares", "averageCost", "currentPrice", "account"))
}
