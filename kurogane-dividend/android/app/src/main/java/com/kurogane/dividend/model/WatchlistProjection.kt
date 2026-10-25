package com.kurogane.dividend.model

/** Same purchase units as the watchlist's existing investment estimate; unknown months stay unknown. */
class WatchlistProjection private constructor(
    val stocks: List<WatchlistStock>,
    private val summary: Summary
) {
    constructor(stocks: List<WatchlistStock>) : this(stocks, summarize(stocks.map(::calculateStock)))

    internal constructor(
        stocks: List<WatchlistStock>,
        stockMetrics: Map<String, WatchlistProjectionStockMetrics>
    ) : this(stocks, summarize(stockMetrics.values))

    fun shares(stock: WatchlistStock): Int = if (stock.category == "us") 1 else 100
    fun annual(stock: WatchlistStock): Double = stock.dps * shares(stock) * stock.currencyFactor
    fun months(stock: WatchlistStock): List<Int> = normalizedMonths(stock.payoutMonths)
    fun monthly(stock: WatchlistStock, month: Int): Double {
        val months = months(stock)
        return if (month in months) annual(stock) / months.size else 0.0
    }

    val currentYields: Map<String, Double> get() = summary.currentYields
    val averageYield: Double get() = summary.averageYield
    val readyCount: Int get() = summary.readyCount
    val japaneseCount: Int get() = summary.japaneseCount
    val usCount: Int get() = summary.usCount
    val annualTotal: Double get() = summary.annualTotal
    val investment: Double get() = summary.investment
    val monthlyTotals: List<Double> get() = summary.monthlyTotals
    val quarterlyTotals: List<Double> get() = summary.quarterlyTotals
    val scheduledTotal: Double get() = summary.scheduledTotal
    val unallocatedCount: Int get() = summary.unallocatedCount
    val missingExchangeRate: Boolean get() = summary.missingExchangeRate
    val emptyMonths: List<Int> get() = summary.emptyMonths
    val paymentCount: Int get() = summary.paymentCount
    val peakShare: Double get() = summary.peakShare

    internal fun withStocks(stocks: List<WatchlistStock>) = WatchlistProjection(stocks, summary)

    private data class Summary(
        val currentYields: Map<String, Double>,
        val averageYield: Double,
        val readyCount: Int,
        val japaneseCount: Int,
        val usCount: Int,
        val annualTotal: Double,
        val investment: Double,
        val monthlyTotals: List<Double>,
        val quarterlyTotals: List<Double>,
        val scheduledTotal: Double,
        val unallocatedCount: Int,
        val missingExchangeRate: Boolean,
        val emptyMonths: List<Int>,
        val paymentCount: Int,
        val peakShare: Double
    )

    companion object {
        internal fun inputsFor(stock: WatchlistStock) = WatchlistProjectionInputs(
            dps = stock.dps,
            price = stock.price,
            targetYield = stock.targetYield,
            category = stock.category,
            currency = stock.currency,
            currencyFactor = stock.currencyFactor,
            months = normalizedMonths(stock.payoutMonths)
        )

        internal fun calculateStock(stock: WatchlistStock): WatchlistProjectionStockMetrics {
            val inputs = inputsFor(stock)
            val shares = if (stock.category == "us") 1 else 100
            val annual = stock.dps * shares * stock.currencyFactor
            val currentYield = stock.liveYield
            val monthlyAmounts = (1..12).map { month ->
                if (month in inputs.months) annual / inputs.months.size else 0.0
            }
            return WatchlistProjectionStockMetrics(
                code = stock.code,
                inputs = inputs,
                currentYield = currentYield,
                ready = currentYield >= stock.targetYield,
                japanese = stock.category == "jp",
                us = stock.category == "us",
                annual = annual,
                investment = stock.price * shares * stock.currencyFactor,
                months = inputs.months,
                monthlyAmounts = monthlyAmounts,
                missingExchangeRate = stock.currency == "$" && stock.currencyFactor == 0.0
            )
        }

        private fun summarize(metrics: Collection<WatchlistProjectionStockMetrics>): Summary {
            val yields = metrics.associate { it.code to it.currentYield }
            val monthlyTotals = (0..11).map { monthIndex ->
                metrics.sumOf { it.monthlyAmounts[monthIndex] }
            }
            val scheduledTotal = monthlyTotals.sum()
            return Summary(
                currentYields = yields,
                averageYield = metrics.map { it.currentYield }.average().takeUnless { metrics.isEmpty() } ?: 0.0,
                readyCount = metrics.count { it.ready },
                japaneseCount = metrics.count { it.japanese },
                usCount = metrics.count { it.us },
                annualTotal = metrics.sumOf { it.annual },
                investment = metrics.sumOf { it.investment },
                monthlyTotals = monthlyTotals,
                quarterlyTotals = monthlyTotals.chunked(3).map { it.sum() },
                scheduledTotal = scheduledTotal,
                unallocatedCount = metrics.count { it.months.isEmpty() },
                missingExchangeRate = metrics.any { it.missingExchangeRate },
                emptyMonths = (1..12).filter { monthlyTotals[it - 1] == 0.0 },
                paymentCount = metrics.sumOf { it.months.size },
                peakShare = if (scheduledTotal > 0) monthlyTotals.max() / scheduledTotal * 100 else 0.0
            )
        }
    }
}

internal data class WatchlistProjectionInputs(
    val dps: Double,
    val price: Double,
    val targetYield: Double,
    val category: String,
    val currency: String,
    val currencyFactor: Double,
    val months: List<Int>
)

internal data class WatchlistProjectionStockMetrics(
    val code: String,
    val inputs: WatchlistProjectionInputs,
    val currentYield: Double,
    val ready: Boolean,
    val japanese: Boolean,
    val us: Boolean,
    val annual: Double,
    val investment: Double,
    val months: List<Int>,
    val monthlyAmounts: List<Double>,
    val missingExchangeRate: Boolean
)

/** Reuses per-stock calculations when a new Watchlist snapshot contains the same inputs. */
class WatchlistProjectionCache {
    private var sourceStocks: List<WatchlistStock>? = null
    private var stockMetrics: Map<String, WatchlistProjectionStockMetrics> = emptyMap()
    private var cachedProjection: WatchlistProjection? = null

    @Synchronized
    fun get(stocks: List<WatchlistStock>): WatchlistProjection {
        if (sourceStocks === stocks) return checkNotNull(cachedProjection)

        val nextMetrics = HashMap<String, WatchlistProjectionStockMetrics>(stocks.size)
        var metricsUnchanged = cachedProjection != null && stocks.size == stockMetrics.size
        stocks.forEach { stock ->
            val previous = stockMetrics[stock.code]
            val current = if (previous?.inputs == WatchlistProjection.inputsFor(stock)) {
                previous
            } else {
                metricsUnchanged = false
                WatchlistProjection.calculateStock(stock)
            }
            nextMetrics[stock.code] = current
        }
        if (nextMetrics.size != stockMetrics.size) metricsUnchanged = false

        val projection = if (metricsUnchanged) {
            checkNotNull(cachedProjection).withStocks(stocks)
        } else {
            WatchlistProjection(stocks, nextMetrics)
        }
        sourceStocks = stocks
        stockMetrics = nextMetrics
        cachedProjection = projection
        return projection
    }
}

private fun normalizedMonths(months: List<Int>): List<Int> =
    months.filter { it in 1..12 }.distinct().sorted()
