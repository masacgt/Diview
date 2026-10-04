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
    val isPositive: Boolean
) {
    val totalValuation: Double get() = shares * price * (if (currency == "$") 154.0 else 1.0)
    val totalCost: Double get() = shares * acqPrice * (if (currency == "$") 154.0 else 1.0)
    val gainLoss: Double get() = totalValuation - totalCost
    val gainLossPercent: Double get() = if (totalCost > 0) (gainLoss / totalCost) * 100 else 0.0
    val annualDividend: Double get() = shares * dps * (if (currency == "$") 154.0 else 1.0)
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
