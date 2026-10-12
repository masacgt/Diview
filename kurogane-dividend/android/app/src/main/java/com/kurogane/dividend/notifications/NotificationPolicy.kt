package com.kurogane.dividend.notifications

import com.kurogane.dividend.model.Category
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
    val currentDps: Double,
    val deliverAtMillis: Long = 0L
) {
    val isIncrease: Boolean get() = currentDps > previousDps
}

data class PriceAlertState(
    val signature: String,
    val ready: Boolean,
    val notifiedDate: String? = null
)

data class PriceAlertHit(val stock: WatchlistStock, val price: Double, val yield: Double, val held: Boolean = false)
data class PriceAlertPlan(val hits: List<PriceAlertHit>, val states: Map<String, PriceAlertState>)

data class PendingPriceAlert(
    val code: String,
    val name: String,
    val currency: String,
    val price: Double,
    val yield: Double,
    val targetYield: Double,
    val held: Boolean,
    val deliverAtMillis: Long
)

data class DividendRightsEvent(
    val id: String,
    val code: String,
    val name: String,
    val exDividendDate: LocalDate,
    val finalRightsDate: LocalDate?,
    val recordDate: LocalDate
)

data class PendingDividendRightsEvent(
    val event: DividendRightsEvent,
    val deliverAtMillis: Long
)

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

    /** The next notification checkpoint, strictly after the moment an event was detected. */
    fun nextDeliveryAt(now: ZonedDateTime): ZonedDateTime {
        val japanNow = now.withZoneSameInstant(japanZone)
        listOf(morning, evening).forEach { time ->
            val candidate = japanNow.toLocalDate().atTime(time).atZone(japanZone)
            if (candidate.isAfter(japanNow)) return candidate
        }
        return japanNow.toLocalDate().plusDays(1).atTime(morning).atZone(japanZone)
    }

    /** A delayed background job must not wake the user overnight. */
    fun withinWindow(now: ZonedDateTime, isMorning: Boolean): Boolean {
        val time = now.withZoneSameInstant(japanZone).toLocalTime()
        return if (isMorning) time >= morning && time < morning.plusHours(1)
        else time >= evening && time < evening.plusHours(1)
    }

    fun priceAlerts(
        stocks: List<WatchlistStock>,
        prices: Map<String, Double>,
        previous: Map<String, PriceAlertState>,
        date: LocalDate
    ): PriceAlertPlan = priceAlerts(stocks, emptyList(), prices, previous, date)

    /** Holdings reuse the matching watchlist stock's target yield. */
    fun priceAlerts(
        stocks: List<WatchlistStock>,
        holdings: List<Holding>,
        prices: Map<String, Double>,
        previous: Map<String, PriceAlertState>
    ): PriceAlertPlan = priceAlerts(stocks, holdings, prices, previous, LocalDate.now(japanZone))

    fun priceAlerts(
        stocks: List<WatchlistStock>,
        holdings: List<Holding>,
        prices: Map<String, Double>,
        previous: Map<String, PriceAlertState>,
        date: LocalDate
    ): PriceAlertPlan {
        val hits = mutableListOf<PriceAlertHit>()
        val next = mutableMapOf<String, PriceAlertState>()
        val normalizedPrices = prices.entries.associate { PortfolioMath.normalizeCode(it.key) to it.value }
        val heldByCode = holdings.asSequence().filter { it.shares > 0 }
            .associateBy { PortfolioMath.normalizeCode(it.code) }
        val currentDate = date.toString()
        stocks.distinctBy { PortfolioMath.normalizeCode(it.code) }.forEach { configuredStock ->
            val code = PortfolioMath.normalizeCode(configuredStock.code)
            val holding = heldByCode[code]?.takeIf { holding ->
                when (configuredStock.category.lowercase(java.util.Locale.ROOT)) {
                    "jp" -> holding.category == Category.JAPAN
                    "us" -> holding.category == Category.US
                    else -> false
                }
            }
            if (!configuredStock.alertEnabled) return@forEach
            val stock = if (holding != null && PortfolioMath.validMoney(holding.dps) && holding.dps > 0.0) {
                configuredStock.copy(dps = holding.dps, name = holding.name, currency = holding.currency)
            } else configuredStock
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
            val newlyReady = ready && (old?.ready != true || old.signature != signature)
            if (newlyReady && old?.notifiedDate == currentDate) {
                next[code] = old
                return@forEach
            }
            if (newlyReady) {
                hits += PriceAlertHit(stock, price, yield, held = holding != null)
            }
            next[code] = PriceAlertState(signature, ready, old?.notifiedDate)
        }
        return PriceAlertPlan(hits, next)
    }

    fun dividendHoldings(holdings: List<Holding>, date: LocalDate): List<Holding> = holdings.filter {
        it.shares > 0 && PortfolioMath.validMoney(it.dps) && it.dps > 0 && date.monthValue in it.dividendMonths
    }.distinctBy { PortfolioMath.normalizeCode(it.code) }
}
