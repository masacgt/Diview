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
    val liveYield: Double get() = if (price > 0) (dps / price) * 100 else 0.0
    val liveYoc: Double get() = if (acqPrice > 0) (dps / acqPrice) * 100 else liveYield

    fun getEffectiveAnnualDividend(taxRateMode: String): Double {
        val gross = annualDividend
        return if (taxRateMode == "net") {
            val isTaxFree = accountType.contains("NISA") || accountType.contains("非課税")
            if (isTaxFree) gross else gross * 0.79685
        } else {
            gross
        }
    }
}

data class StockFinancialProfile(
    val payoutRatio: Double,
    val equityRatio: Double,
    val netDeRatio: String,
    val healthGrade: String,
    val healthEvaluation: String,
    val consecutiveYears: Int,
    val noCutYears: Int,
    val growthRate: Double,
    val eps: Double,
    val bps: Double,
    val recordMonths: String,
    val payoutSchedule: String,
    val payoutStatusBadge: String = "適正レンジ内 (黄金比率)",
    val equityRatioSub: String = "業界高水準・安定",
    val doePolicy: String = "3.2% (DOE)",
    val doePolicySub: String = "安定還元・DOE基準",
    val operatingMargin: Double = 11.4,
    val fcfStatus: String = "黒字維持（配当余力 十分）",
    val modelType: String = "王道大型バリュー（トヨタ基準型）",
    val healthScore: Int = 92,
    val healthDiagnosisComment: String = "自己資本比率39.8%および実質無借金（ネットD/E -0.22倍）を誇り、本業の営業利益率11.4%と極めて堅牢。配当性向35.2%（適正目安30〜50%）に加えてDOE3.2%基準による安定還元を実施しており、中長期的な減配懸念は極小と評価されます。",
    val valuationScore: Int = 88,
    val yieldRangeMin: Double = 2.10,
    val yieldRangeAvg: Double = 2.45,
    val yieldRangeMax: Double = 3.20,
    val yieldRangeBadge: String = "過去5年平均超え",
    val perEvaluation: String = "市場平均比で割安",
    val pbrEvaluation: String = "資本効率適正圏",
    val buySignalZone: String = "超割安ゾーン（検討圏）",
    val buySignalTitle: String = "【王道大型バリュー健全圏】PER 10.4倍 × PBR 1.12倍",
    val buySignalBadge: String = "絶好の買増し検討期",
    val buySignalComment: String = "PER 10.4倍・PBR 1.12倍と、日経平均平均（PER 15倍・PBR 1.4倍）を大幅に下回る優良バリュー水準。高収益性とキャッシュ創出力に対して割安に放置されており、配当再投資や中期的な買い増しに最適な好機シグナルです。"
) {
    fun getPer(price: Double): String = if (eps > 0) String.format("%.1f倍", price / eps) else "-"
    fun getPbr(price: Double): String = if (bps > 0) String.format("%.2f倍", price / bps) else "-"
    fun getValuationStatus(price: Double, dps: Double): String {
        val yld = if (price > 0) (dps / price) * 100 else 0.0
        val per = if (eps > 0) price / eps else 15.0
        return when {
            yld >= 4.0 || per < 10 -> "超割安・買場ゾーン"
            yld >= 3.2 || per < 14 -> "買場目安接近・検討ゾーン"
            per > 25 -> "高PER・成長評価圏"
            else -> "適正・中立ゾーン"
        }
    }
}

fun getStockProfile(code: String): StockFinancialProfile {
    return when (code) {
        "7203" -> StockFinancialProfile(35.2, 39.8, "-0.22倍 (実質無借金)", "A判定", "極めて安全", 10, 25, 8.5, 250.0, 2321.4, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "2914" -> StockFinancialProfile(74.6, 51.8, "0.12倍 (健全財務)", "A判定", "高還元健全", 3, 10, 5.2, 260.0, 1950.0, "6月末 / 12月末", "9月下旬 / 3月下旬")
        "8306" -> StockFinancialProfile(37.0, 5.2, "BIS規制適合 (健全)", "A判定", "自己資本規制適合", 4, 16, 12.0, 135.0, 1850.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8316" -> StockFinancialProfile(40.0, 5.1, "BIS規制適合 (健全)", "A判定", "メガバンク高資本", 5, 16, 9.1, 325.0, 3850.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8058" -> StockFinancialProfile(34.5, 34.5, "0.32倍 (良質資産)", "A+判定", "強固なCF創出力", 9, 12, 10.5, 290.0, 2650.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "9432" -> StockFinancialProfile(38.5, 33.8, "0.88倍 (インフラ適正)", "A判定", "ディフェンシブ極致", 14, 25, 3.5, 13.5, 118.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "9433" -> StockFinancialProfile(46.8, 45.1, "0.45倍 (優良)", "A+判定", "23期連続増配の実績", 23, 23, 5.0, 310.0, 2800.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8766" -> StockFinancialProfile(45.0, 15.5, "ESR 220% (健全)", "A判定", "国際基準健全", 13, 13, 15.0, 280.0, 2400.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8031" -> StockFinancialProfile(31.8, 38.0, "0.28倍 (商社優良)", "A判定", "高収益資源流通", 4, 12, 8.0, 330.0, 3350.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "4502" -> StockFinancialProfile(94.9, 48.5, "0.72倍 (純負債圧縮進行)", "B+判定", "配当維持コミット", 0, 30, 0.0, 198.0, 3800.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "AAPL" -> StockFinancialProfile(15.4, 28.5, "0.85倍 (莫大CF補完)", "A+判定", "自社株買い強力", 12, 12, 4.5, 6.5, 4.8, "2/5/8/11月", "四半期配当 (2・5・8・11月)")
        else -> StockFinancialProfile(35.0, 40.0, "0.20倍 (標準健全)", "A判定", "適正健全水準", 5, 10, 5.0, 100.0, 1000.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
    }
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
