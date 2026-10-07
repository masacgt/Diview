package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.round

data class HoldingAllocation(val shares: Int, val acquisitionPrice: Double, val accountType: String)

/** Validation and arithmetic shared by imports, editing and multi-account registration. */
object PortfolioMath {
    fun portfolioYield(dividend: Double, valuation: Double, cost: Double, basis: String): Double {
        val denominator = if (basis == "cost") cost else valuation
        return if (denominator > 0) (dividend / denominator * 100).takeIf(Double::isFinite) ?: 0.0 else 0.0
    }

    /** Each position occurs once; positions strictly below the threshold form the final group when enabled. */
    fun compositionGroups(values: List<Double>, thresholdPercent: Int, aggregateOther: Boolean = true): List<List<Int>> {
        if (!aggregateOther) return values.indices.map(::listOf)
        val total = values.sum()
        if (total <= 0) return emptyList()
        val (small, large) = values.indices.partition { values[it] / total * 100 < thresholdPercent.coerceIn(1, 10) }
        return large.map { listOf(it) } + if (small.isEmpty()) emptyList() else listOf(small)
    }

    fun validMoney(value: Double): Boolean = value.isFinite() && value >= 0.0 && value <= 1.0e12

    fun wholeShares(value: Double?): Int? = value?.takeIf {
        it.isFinite() && it >= 0.0 && it <= Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0
    }?.toInt()

    fun normalizeCode(code: String): String = code.trim().uppercase(Locale.ROOT)

    fun validCode(code: String): Boolean = code.isNotBlank() && code.length <= 256 &&
        code.none { it.isISOControl() || it == '/' || it == '?' || it == '#' }

    fun isTaxFree(account: String): Boolean = account.uppercase(Locale.ROOT).contains("NISA") ||
        account.contains("非課税") || account.equals("iDeCo", ignoreCase = true)

    fun yieldPercent(dividend: Double, price: Double): Double =
        if (validMoney(dividend) && validMoney(price) && price > 0.0) {
            (dividend / price * 100.0).takeIf(Double::isFinite) ?: 0.0
        } else 0.0

    fun adjustedTarget(current: Double, delta: Double): Double {
        require(current.isFinite() && delta.isFinite()) { "利回りは有限の数値を指定してください" }
        return round((current + delta).coerceIn(0.1, 100.0) * 100.0) / 100.0
    }

    fun addShares(current: Int, delta: Int): Int =
        (current.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun mergeAllocations(holding: Holding, allocations: List<HoldingAllocation>): Holding {
        require(allocations.isNotEmpty()) { "口座を指定してください" }
        require(allocations.all { it.shares >= 0 && validMoney(it.acquisitionPrice) && it.accountType.isNotBlank() }) {
            "数量・取得単価・口座を確認してください"
        }
        val total = holding.shares.toLong() + allocations.sumOf { it.shares.toLong() }
        require(total in 1..Int.MAX_VALUE.toLong()) { "保有数量が上限を超えています" }
        val oldFree = holding.effectiveTaxFreeShares
        val free = oldFree.toLong() + allocations.filter { isTaxFree(it.accountType) }.sumOf { it.shares.toLong() }
        val average = (holding.shares * holding.acqPrice + allocations.sumOf { it.shares * it.acquisitionPrice }) / total
        val accounts = (listOf(holding.accountType).filter { holding.shares > 0 } +
            allocations.filter { it.shares > 0 }.map { it.accountType }).distinct()
        return holding.copy(
            shares = total.toInt(), acqPrice = average, taxFreeShares = free.toInt(),
            accountType = accounts.singleOrNull() ?: "複数口座",
            yoc = yieldPercent(holding.dps, average)
        ).refreshPayout()
    }

    fun Holding.refreshPayout(): Holding {
        val months = dividendMonths.filter { it in 1..12 }.distinct()
        val payout = shares * dps / priceUnit / months.size.coerceAtLeast(1)
        val currentMonth = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).monthValue
        val nextMonth = months.firstOrNull { it >= currentMonth } ?: months.firstOrNull()
        val amount = "$currency${java.text.NumberFormat.getNumberInstance(Locale.JAPAN).format(payout)}"
        return copy(
            yieldRate = liveYield, yoc = liveYoc, nextPayoutAmount = amount,
            nextPayoutTiming = nextMonth?.let { "次回: ${it}月 ($amount)" } ?: "配当月未取得"
        )
    }
}
