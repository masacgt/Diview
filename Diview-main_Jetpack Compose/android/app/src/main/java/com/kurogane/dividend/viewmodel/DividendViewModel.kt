package com.kurogane.dividend.viewmodel

import androidx.lifecycle.ViewModel
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.MonthlyDividend
import com.kurogane.dividend.model.RoadmapStep
import com.kurogane.dividend.model.StepStatus
import com.kurogane.dividend.model.WatchlistStock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DividendViewModel : ViewModel() {

    private val _isPrivacyActive = MutableStateFlow(false)
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _holdings = MutableStateFlow(
        listOf(
            Holding(
                id = "h-toyota",
                code = "7203",
                name = "トヨタ自動車",
                sector = "東証P • 輸送用機器",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 2600.0,
                shares = 100,
                acqPrice = 2450.0,
                dps = 72.8,
                yieldRate = 2.80,
                yoc = 2.97,
                nextPayoutTiming = "次回: 12月中旬 (¥3,640)",
                nextPayoutAmount = "¥3,640",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥35 (+1.36%)",
                isPositive = true
            ),
            Holding(
                id = "h-apple",
                code = "AAPL",
                name = "Apple Inc.",
                sector = "NASDAQ • テクノロジー",
                market = "NASDAQ",
                category = Category.US,
                price = 220.0,
                currency = "$",
                shares = 10,
                acqPrice = 192.8,
                dps = 1.0,
                yieldRate = 0.52,
                yoc = 0.52,
                nextPayoutTiming = "次回: 2月中旬 ($2.50)",
                nextPayoutAmount = "¥425 ($2.50)",
                dividendMonths = listOf(2, 5, 8, 11),
                dividendType = "四半期配当",
                accountType = "特定口座 (四半期)",
                dailyChange = "+1.45 (+0.66%)",
                isPositive = true
            ),
            Holding(
                id = "h-emaxis",
                code = "eMAXIS",
                name = "eMAXIS Slim 全世界株式",
                sector = "オルカン • 三菱UFJアセット",
                market = "投資信託",
                category = Category.FUND,
                price = 24460.0,
                shares = 50000,
                acqPrice = 24460.0,
                dps = 0.0,
                yieldRate = 0.0,
                yoc = 0.0,
                nextPayoutTiming = "無分配で複利最大化",
                nextPayoutAmount = "¥0",
                dividendMonths = emptyList(),
                dividendType = "自動再投資",
                accountType = "新NISAつみたて投資枠",
                dailyChange = "-¥2,300 (-1.9%)",
                isPositive = false
            )
        )
    )
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _watchlist = MutableStateFlow(
        listOf(
            WatchlistStock(
                code = "7203",
                name = "トヨタ自動車",
                market = "東証PRM",
                sector = "輸送用機器 · 日本株",
                category = "jp",
                price = 2600.0,
                dps = 72.8,
                currentYield = 2.80,
                targetYield = 3.00,
                targetPrice = 2427.0,
                dailyChange = "+35 (+1.36%)",
                isPositive = true,
                payoutMonths = listOf(6, 12),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 輸送用機器",
                alertEnabled = false,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "8316",
                name = "三井住友FG",
                market = "東証PRM",
                sector = "銀行業 · 日本株",
                category = "jp",
                price = 3150.0,
                dps = 130.0,
                currentYield = 4.13,
                targetYield = 3.80,
                targetPrice = 3421.0,
                dailyChange = "-22 (-0.69%)",
                isPositive = false,
                payoutMonths = listOf(6, 12),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 銀行業",
                alertEnabled = true,
                badge = "絶好の買場到達 (利回り4.13%)"
            ),
            WatchlistStock(
                code = "9432",
                name = "日本電信電話 (NTT)",
                market = "東証PRM",
                sector = "情報・通信業 · 日本株",
                category = "jp",
                price = 154.2,
                dps = 5.2,
                currentYield = 3.37,
                targetYield = 4.00,
                targetPrice = 130.0,
                dailyChange = "0.0 (0.00%)",
                isPositive = true,
                payoutMonths = listOf(6, 12),
                payoutTiming = "6月下旬 / 12月上旬",
                desc = "東証PRM · 情報・通信業",
                alertEnabled = true,
                badge = "ディフェンシブ"
            ),
            WatchlistStock(
                code = "AAPL",
                name = "Apple Inc.",
                market = "NASDAQ",
                sector = "テクノロジー · 米国株",
                category = "us",
                price = 220.0,
                currency = "$",
                dps = 1.0,
                currentYield = 0.45,
                targetYield = 0.50,
                targetPrice = 200.0,
                dailyChange = "+1.45 (+0.66%)",
                isPositive = true,
                payoutMonths = listOf(2, 5, 8, 11),
                payoutTiming = "各月中旬 (四半期)",
                desc = "NASDAQ · US Tech · 米国株",
                alertEnabled = false,
                badge = "自社株買い+連続増配"
            )
        )
    )
    val watchlist: StateFlow<List<WatchlistStock>> = _watchlist.asStateFlow()

    val monthlyDividends = listOf(
        MonthlyDividend(1, 2100, "0.2万"),
        MonthlyDividend(2, 3800, "0.4万"),
        MonthlyDividend(3, 12400, "1.2万"),
        MonthlyDividend(4, 1500, "0.2万"),
        MonthlyDividend(5, 4200, "0.4万"),
        MonthlyDividend(6, 38500, "3.8万"),
        MonthlyDividend(7, 2100, "0.2万"),
        MonthlyDividend(8, 3200, "0.3万"),
        MonthlyDividend(9, 9800, "1.0万"),
        MonthlyDividend(10, 1600, "0.2万"),
        MonthlyDividend(11, 4800, "0.5万"),
        MonthlyDividend(12, 42000, "4.2万")
    )

    fun togglePrivacy() {
        _isPrivacyActive.update { !it }
    }

    fun selectMonth(month: Int) {
        _selectedMonth.update { if (it == month) null else month }
    }

    fun selectCategory(cat: Category?) {
        _selectedCategory.value = cat
    }

    fun updateTargetYield(code: String, delta: Double) {
        _watchlist.update { list ->
            list.map { stock ->
                if (stock.code == code) {
                    val newTarget = (stock.targetYield + delta).coerceAtLeast(0.1)
                    val newPrice = (stock.dps / (newTarget / 100))
                    stock.copy(targetYield = String.format("%.2f", newTarget).toDouble(), targetPrice = newPrice)
                } else stock
            }
        }
    }

    fun updateHolding(code: String, shares: Int, acqPrice: Double) {
        _holdings.update { list ->
            list.map {
                if (it.code == code) {
                    val halfDiv = (shares * it.dps) / 2
                    it.copy(
                        shares = shares,
                        acqPrice = acqPrice,
                        nextPayoutAmount = "¥${halfDiv.toInt()}"
                    )
                } else it
            }
        }
    }
}
