package com.kurogane.dividend.model

data class Holding(
    val id: String,
    val code: String,
    val name: String,
    val sector: String,
    val market: String,
    val category: Category,
    val price: Double,
    val currency: String = "¥",
    val shares: Int,
    val acqPrice: Double,
    val dps: Double, // annual dividend per share
    val yieldRate: Double,
    val yoc: Double,
    val nextPayoutTiming: String,
    val nextPayoutAmount: String,
    val dividendMonths: List<Int>,
    val dividendType: String,
    val accountType: String,
    val dailyChange: String,
    val isPositive: Boolean,
    val fxRateToJpy: Double = 1.0
) {
    // Japanese mutual fund NAV is quoted per 10,000 units; stock prices are per share.
    private val unitScale: Double get() = if (category == Category.FUND) 10_000.0 else 1.0
    val totalValuation: Double get() = shares * price * fxRateToJpy / unitScale
    val totalCost: Double get() = shares * acqPrice * fxRateToJpy / unitScale
    val gainLoss: Double get() = totalValuation - totalCost
    val gainLossPercent: Double get() = if (totalCost > 0) (gainLoss / totalCost) * 100 else 0.0
    val annualDividend: Double get() = shares * dps * fxRateToJpy
    val currentYield: Double get() = if (price > 0.0) dps / price * 100.0 else 0.0
    val yieldOnCost: Double get() = if (acqPrice > 0.0) dps / acqPrice * 100.0 else 0.0
}

enum class Category {
    JAPAN, US, FUND
}

data class WatchlistStock(
    val code: String,
    val name: String,
    val market: String,
    val sector: String,
    val category: String, // "jp" or "us"
    val price: Double,
    val currency: String = "¥",
    val dps: Double,
    val currentYield: Double,
    val targetYield: Double,
    val targetPrice: Double,
    val dailyChange: String,
    val isPositive: Boolean,
    val payoutMonths: List<Int>,
    val payoutTiming: String,
    val desc: String,
    val alertEnabled: Boolean = false,
    val badge: String? = null,
    val defaultShares: Int = 100
) {
    val isReady: Boolean get() = currentYield >= targetYield
    val achievementRate: Int get() = ((currentYield / targetYield) * 100).toInt().coerceIn(0, 100)
}

data class RoadmapStep(
    val step: Int,
    val level: Int,
    val name: String,
    val title: String,
    val description: String,
    val iconName: String,
    val targetMonthly: Int,
    val targetAnnual: Int,
    val status: StepStatus,
    val statusLabel: String,
    val currentRate: Double,
    val rateLabel: String,
    val shortageMonthly: Int = 0,
    val shortageAnnual: Int = 0,
    val yield35Capital: String = "",
    val yield40Capital: String = "",
    val yield45Capital: String = ""
)

enum class StepStatus {
    COMPLETED, IN_PROGRESS, LOCKED
}

data class MonthlyDividend(
    val month: Int,
    val amount: Int,
    val label: String
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class AccentColor(val key: String) {
    EMERALD("emerald"), CYAN("cyan"), BLUE("blue"), AMBER("amber"), PURPLE("purple")
}

data class AppUiSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.EMERALD,
    val oledBlack: Boolean = false
)

data class PortfolioDiagnosisBand(
    val minScore: Int,
    val maxScore: Int,
    val grade: String,
    val title: String,
    val comment: String
)

data class ValuationScenario(
    val id: Int,
    val perMax: Double,
    val pbrMax: Double,
    val title: String,
    val badge: String,
    val comment: String
)

data class YearlyAssetSnapshot(
    val year: String,
    val principal: Int,
    val valuation: Int
) {
    val gain: Int get() = valuation - principal
    val gainPercent: Double get() = if (principal > 0) gain.toDouble() / principal * 100.0 else 0.0
}
