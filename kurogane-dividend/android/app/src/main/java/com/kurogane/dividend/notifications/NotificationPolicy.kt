package com.kurogane.dividend.notifications

import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.WatchlistStock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

enum class NotificationKind { PRICE, DIVIDEND, DIVIDEND_CHANGE }

data class NotificationSettings(
    val priceEnabled: Boolean = false,
    val dividendEnabled: Boolean = false,
    val revision: Long = 0L,
    val dividendChangeEnabled: Boolean = false
) {
    fun enabled(kind: NotificationKind): Boolean = when (kind) {
        NotificationKind.PRICE -> priceEnabled
        NotificationKind.DIVIDEND -> dividendEnabled
        NotificationKind.DIVIDEND_CHANGE -> dividendChangeEnabled
    }
}

data class DividendChangeEvent(
    val code: String,
    val name: String,
    val fiscalYear: Int,
    val previousDps: Double,
    val currentDps: Double
) {
    val isIncrease: Boolean get() = currentDps > previousDps
}

data class PriceAlertState(
    val signature: String,
    val ready: Boolean,
    val notifiedDate: String? = null
)

data class PriceAlertHit(val stock: WatchlistStock, val price: Double, val yield: Double)
data class PriceAlertPlan(val hits: List<PriceAlertHit>, val states: Map<String, PriceAlertState>)

/** Notification decisions use fetched quotes, never the watchlist's cached currentYield. */
object NotificationPolicy {
    val japanZone: ZoneId = ZoneId.of("Asia/Tokyo")
    val morning: LocalTime = LocalTime.of(8, 30)
    val evening: LocalTime = LocalTime.of(17, 30)

    fun nextDelay(now: ZonedDateTime, time: LocalTime): Duration {
        val japanNow = now.withZoneSameInstant(japanZone)
        var next = japanNow.toLocalDate().atTime(time).atZone(japanZone)
        if (!next.isAfter(japanNow)) next = next.plusDays(1)
        return Duration.between(japanNow, next)
    }

    /** A delayed background job must not wake the user overnight. */
    fun withinWindow(now: ZonedDateTime, isMorning: Boolean): Boolean {
        val time = now.withZoneSameInstant(japanZone).toLocalTime()
        return if (isMorning) time >= morning && time < LocalTime.NOON
        else time >= evening && time < LocalTime.of(21, 0)
    }

    fun priceAlerts(
        stocks: List<WatchlistStock>,
        prices: Map<String, Double>,
        previous: Map<String, PriceAlertState>,
        date: LocalDate
    ): PriceAlertPlan {
        val hits = mutableListOf<PriceAlertHit>()
        val next = mutableMapOf<String, PriceAlertState>()
        val normalizedPrices = prices.entries.associate { PortfolioMath.normalizeCode(it.key) to it.value }
        stocks.filter { it.alertEnabled }.distinctBy { PortfolioMath.normalizeCode(it.code) }.forEach { stock ->
            val code = PortfolioMath.normalizeCode(stock.code)
            if (!PortfolioMath.validCode(code) || !PortfolioMath.validMoney(stock.dps) || stock.dps <= 0 ||
                !stock.targetYield.isFinite() || stock.targetYield !in 0.1..100.0) return@forEach
            val old = previous[code]
            val price = normalizedPrices[code]
            if (price == null || !PortfolioMath.validMoney(price) || price <= 0) {
                if (old != null) next[code] = old
                return@forEach
            }
            val signature = "${stock.targetYield}:${stock.dps}"
            val yield = PortfolioMath.yieldPercent(stock.dps, price)
            val ready = yield >= stock.targetYield
            if (ready && (old?.ready != true || old.signature != signature) && old?.notifiedDate != date.toString()) {
                hits += PriceAlertHit(stock, price, yield)
            }
            next[code] = PriceAlertState(signature, ready, old?.notifiedDate)
        }
        return PriceAlertPlan(hits, next)
    }

    fun dividendHoldings(holdings: List<Holding>, date: LocalDate): List<Holding> = holdings.filter {
        it.shares > 0 && PortfolioMath.validMoney(it.dps) && it.dps > 0 && date.monthValue in it.dividendMonths
    }.distinctBy { PortfolioMath.normalizeCode(it.code) }
}
