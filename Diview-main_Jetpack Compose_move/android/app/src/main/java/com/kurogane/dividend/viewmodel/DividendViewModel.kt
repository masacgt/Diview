package com.kurogane.dividend.viewmodel

import androidx.lifecycle.viewModelScope
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kurogane.dividend.alerts.WatchlistAlertScheduler
import com.kurogane.dividend.alerts.WatchlistAlertWorker
import com.kurogane.dividend.BuildConfig
import com.kurogane.dividend.data.SharedMarketRepository
import com.kurogane.dividend.data.PortfolioSnapshotRepository
import com.kurogane.dividend.data.LocalPortfolioRepository
import com.kurogane.dividend.data.NotificationHistoryItem
import com.kurogane.dividend.data.NotificationHistoryRepository
import com.kurogane.dividend.data.SecurityCatalogRepository
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.MonthlyDividend
import com.kurogane.dividend.model.RoadmapStep
import com.kurogane.dividend.model.StepStatus
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.model.AppUiSettings
import com.kurogane.dividend.model.AccentColor
import com.kurogane.dividend.model.ThemeMode
import com.kurogane.dividend.model.PortfolioDiagnosisBand
import com.kurogane.dividend.model.ValuationScenario
import com.kurogane.dividend.model.YearlyAssetSnapshot
import com.kurogane.dividend.data.StockAnalysisDefinitions
import com.kurogane.dividend.data.WatchlistAnalysisDefinitions
import com.kurogane.dividend.data.PortfolioAnalysisDefinitions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DividendViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedRepository = SharedMarketRepository(application)
    private val snapshotRepository = PortfolioSnapshotRepository(application)
    private val localPortfolioRepository = LocalPortfolioRepository(application)
    private val notificationHistoryRepository = NotificationHistoryRepository(application)
    private val securityCatalogRepository by lazy { SecurityCatalogRepository(application) }

    private val _uiSettings = MutableStateFlow(AppUiSettings())
    val uiSettings: StateFlow<AppUiSettings> = _uiSettings.asStateFlow()

    private val _isPrivacyActive = MutableStateFlow(false)
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _holdings = MutableStateFlow(localPortfolioRepository.loadHoldings())
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _watchlist = MutableStateFlow(localPortfolioRepository.loadWatchlist())
    val watchlist: StateFlow<List<WatchlistStock>> = _watchlist.asStateFlow()

    private val _notificationHistory = MutableStateFlow(notificationHistoryRepository.load())
    val notificationHistory: StateFlow<List<NotificationHistoryItem>> = _notificationHistory.asStateFlow()

    fun refreshNotificationHistory() { _notificationHistory.value = notificationHistoryRepository.load() }

    fun markNotificationRead(id: String) {
        notificationHistoryRepository.markRead(id)
        refreshNotificationHistory()
    }

    fun markAllNotificationsRead() {
        notificationHistoryRepository.markAllRead()
        refreshNotificationHistory()
    }

    fun loadSecurityForDetail(code: String) {
        sharedRepository.cachedSecurity(code)?.let(::applySharedSecurityData)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sharedRepository.fetchSecurity(code)?.let(::applySharedSecurityData)
        }
    }

    fun securityNameForCode(code: String): String? =
        securityCatalogRepository.securities.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name

    val monthlyDividends: List<MonthlyDividend>
        get() {
            val totals = IntArray(12)
            _holdings.value.forEach { holding ->
                if (holding.dividendMonths.isEmpty() || holding.annualDividend <= 0.0) return@forEach
                val perPayment = holding.annualDividend / holding.dividendMonths.size
                holding.dividendMonths.forEach { month ->
                    if (month in 1..12) totals[month - 1] += perPayment.toInt()
                }
            }
            return totals.mapIndexed { index, amount ->
                MonthlyDividend(
                    month = index + 1,
                    amount = amount,
                    label = when {
                        amount <= 0 -> "—"
                        amount >= 10000 -> String.format("%.1f万", amount / 10000.0)
                        else -> "${amount / 1000.0}千"
                    }
                )
            }
        }

    private val _diagnosisBands = MutableStateFlow<List<PortfolioDiagnosisBand>>(emptyList())
    val diagnosisBands: StateFlow<List<PortfolioDiagnosisBand>> = _diagnosisBands.asStateFlow()

    private val _valuationScenarios = MutableStateFlow<List<ValuationScenario>>(emptyList())
    val valuationScenarios: StateFlow<List<ValuationScenario>> = _valuationScenarios.asStateFlow()

    private val _sharedSecurities = MutableStateFlow<Map<String, com.kurogane.dividend.data.SharedSecurityData>>(emptyMap())
    val sharedSecurities: StateFlow<Map<String, com.kurogane.dividend.data.SharedSecurityData>> = _sharedSecurities.asStateFlow()

    private val _stockAnalysisDefinitions = MutableStateFlow<StockAnalysisDefinitions?>(null)
    val stockAnalysisDefinitions: StateFlow<StockAnalysisDefinitions?> = _stockAnalysisDefinitions.asStateFlow()

    private val _watchlistAnalysisDefinitions = MutableStateFlow<WatchlistAnalysisDefinitions?>(null)
    val watchlistAnalysisDefinitions: StateFlow<WatchlistAnalysisDefinitions?> = _watchlistAnalysisDefinitions.asStateFlow()

    private val _portfolioAnalysisDefinitions = MutableStateFlow<PortfolioAnalysisDefinitions?>(null)
    val portfolioAnalysisDefinitions: StateFlow<PortfolioAnalysisDefinitions?> = _portfolioAnalysisDefinitions.asStateFlow()

    private val _yearlyAssetHistory = MutableStateFlow(snapshotRepository.load())
    val yearlyAssetHistory: StateFlow<List<YearlyAssetSnapshot>> = _yearlyAssetHistory.asStateFlow()

    init {
        WatchlistAlertScheduler.sync(application, _watchlist.value.any { it.alertEnabled })
        recordCurrentYearSnapshot()
        if (BuildConfig.D1_TEST_MODE) clearD1TestFallbacks() else applyCachedSharedData()
        refreshSharedDataIfNeeded()
    }

    private fun clearD1TestFallbacks() {
        _holdings.update { holdings -> holdings.map { holding ->
            if (holding.category != Category.JAPAN) holding else holding.copy(
                price = 0.0,
                dps = 0.0,
                yieldRate = 0.0,
                yoc = 0.0,
                nextPayoutTiming = "D1未収録",
                nextPayoutAmount = "—",
                dailyChange = "D1/R2未収録"
            )
        } }
        _watchlist.update { stocks -> stocks.map { stock ->
            if (stock.category != "jp") stock else stock.copy(
                price = 0.0,
                dps = 0.0,
                currentYield = 0.0,
                targetPrice = 0.0,
                dailyChange = "D1/R2未収録"
            )
        } }
    }

    private fun recordCurrentYearSnapshot() {
        val principal = _holdings.value.sumOf { it.totalCost }.toInt()
        val valuation = _holdings.value.sumOf { it.totalValuation }.toInt()
        val currentYear = java.time.LocalDate.now().year.toString()
        if (principal <= 0 && valuation <= 0 && _yearlyAssetHistory.value.none { it.year == currentYear }) return
        _yearlyAssetHistory.value = snapshotRepository.upsertCurrentYear(principal, valuation)
    }

    private fun applyCachedSharedData() {
        trackedJapaneseCodes().forEach { code ->
            sharedRepository.cachedSecurity(code)?.let(::applySharedSecurityData)
        }
        sharedRepository.cachedDiagnosisDefinitions().takeIf { it.isNotEmpty() }?.let { diagnosis ->
            _diagnosisBands.value = diagnosis.map {
                PortfolioDiagnosisBand(it.minScore, it.maxScore, it.grade, it.title, it.comment)
            }
        }
        sharedRepository.cachedValuationDefinitions().takeIf { it.isNotEmpty() }?.let { scenarios ->
            _valuationScenarios.value = scenarios.map {
                ValuationScenario(it.id, it.perMax, it.pbrMax, it.title, it.badge, it.comment)
            }
        }
        _stockAnalysisDefinitions.value = sharedRepository.cachedStockAnalysisDefinitions()
        _watchlistAnalysisDefinitions.value = sharedRepository.cachedWatchlistAnalysisDefinitions()
        _portfolioAnalysisDefinitions.value = sharedRepository.cachedPortfolioAnalysisDefinitions()
    }

    private fun trackedJapaneseCodes(): Set<String> = buildSet {
        _holdings.value.filter { it.category == Category.JAPAN }.forEach { add(it.code) }
        _watchlist.value.filter { it.category == "jp" }.forEach { add(it.code) }
    }

    private fun applySharedSecurityData(shared: com.kurogane.dividend.data.SharedSecurityData) {
        _sharedSecurities.update { it + (shared.code to shared) }
        _holdings.update { list ->
            list.map { holding ->
                if (holding.code == shared.code) {
                    val newDps = shared.annualDps ?: if (BuildConfig.D1_TEST_MODE) 0.0 else holding.dps
                    val newPrice = shared.price ?: if (BuildConfig.D1_TEST_MODE) 0.0 else holding.price
                    holding.copy(
                        name = shared.name,
                        price = newPrice,
                        dps = newDps,
                        yieldRate = if (newPrice > 0) newDps / newPrice * 100.0 else 0.0,
                        yoc = if (holding.acqPrice > 0) newDps / holding.acqPrice * 100.0 else 0.0,
                        nextPayoutTiming = "支払予定データなし",
                        nextPayoutAmount = "—",
                        dailyChange = shared.dailyChange ?: if (BuildConfig.D1_TEST_MODE) "D1/R2未収録" else holding.dailyChange,
                        isPositive = shared.isPositive ?: holding.isPositive
                    )
                } else holding
            }
        }
        localPortfolioRepository.saveHoldings(_holdings.value)
        recordCurrentYearSnapshot()
        _watchlist.update { list ->
            list.map { stock ->
                if (stock.code == shared.code) {
                    val newDps = shared.annualDps ?: if (BuildConfig.D1_TEST_MODE) 0.0 else stock.dps
                    val newPrice = shared.price ?: if (BuildConfig.D1_TEST_MODE) 0.0 else stock.price
                    stock.copy(
                        name = shared.name,
                        price = newPrice,
                        dps = newDps,
                        currentYield = if (newPrice > 0) newDps / newPrice * 100.0 else 0.0,
                        targetPrice = if (stock.targetYield > 0.0 && newDps > 0.0) newDps / (stock.targetYield / 100.0) else 0.0,
                        dailyChange = shared.dailyChange ?: if (BuildConfig.D1_TEST_MODE) "D1/R2未収録" else stock.dailyChange,
                        isPositive = shared.isPositive ?: stock.isPositive
                    )
                } else stock
            }
        }
        localPortfolioRepository.saveWatchlist(_watchlist.value)
    }

    fun refreshSharedDataIfNeeded() {
        if (!sharedRepository.shouldRefresh()) return
        // Throttle failed attempts too, so an offline launch cannot retry repeatedly all day.
        sharedRepository.markRefreshed()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            trackedJapaneseCodes().forEach { code ->
                sharedRepository.fetchSecurity(code)?.let { shared ->
                    applySharedSecurityData(shared)
                }
            }

            val diagnosis = sharedRepository.fetchDiagnosisDefinitions()
            if (diagnosis.isNotEmpty()) {
                _diagnosisBands.value = diagnosis.map {
                    PortfolioDiagnosisBand(it.minScore, it.maxScore, it.grade, it.title, it.comment)
                }
            }
            val scenarios = sharedRepository.fetchValuationDefinitions()
            if (scenarios.isNotEmpty()) {
                _valuationScenarios.value = scenarios.map {
                    ValuationScenario(it.id, it.perMax, it.pbrMax, it.title, it.badge, it.comment)
                }
            }
            sharedRepository.fetchStockAnalysisDefinitions()?.let { _stockAnalysisDefinitions.value = it }
            sharedRepository.fetchWatchlistAnalysisDefinitions()?.let { _watchlistAnalysisDefinitions.value = it }
            sharedRepository.fetchPortfolioAnalysisDefinitions()?.let { _portfolioAnalysisDefinitions.value = it }
        }
    }

    fun setThemeMode(mode: ThemeMode) { _uiSettings.update { it.copy(themeMode = mode) } }
    fun setAccentColor(color: AccentColor) { _uiSettings.update { it.copy(accentColor = color) } }
    fun setOledBlack(enabled: Boolean) { _uiSettings.update { it.copy(oledBlack = enabled) } }

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
                    val newPrice = if (stock.dps > 0.0) stock.dps / (newTarget / 100) else 0.0
                    stock.copy(targetYield = String.format("%.2f", newTarget).toDouble(), targetPrice = newPrice)
                } else stock
            }
        }
        localPortfolioRepository.saveWatchlist(_watchlist.value)
    }

    fun updateHolding(code: String, shares: Int, acqPrice: Double) {
        _holdings.update { list ->
            val targetId = list.firstOrNull { it.code == code }?.id
            list.map {
                if (it.id == targetId) {
                    it.copy(
                        shares = shares,
                        acqPrice = acqPrice,
                        nextPayoutAmount = "—"
                    )
                } else it
            }
        }
        localPortfolioRepository.saveHoldings(_holdings.value)
        recordCurrentYearSnapshot()
    }

    fun addHolding(holding: Holding) {
        _holdings.value = _holdings.value + holding
        localPortfolioRepository.saveHoldings(_holdings.value)
        recordCurrentYearSnapshot()
    }

    fun clearLocalData() {
        localPortfolioRepository.clearPortfolio()
        _holdings.value = emptyList()
        _watchlist.value = emptyList()
        _selectedMonth.value = null
        _selectedCategory.value = null
        recordCurrentYearSnapshot()
    }

    fun removeHolding(id: String) {
        _holdings.value = _holdings.value.filterNot { it.id == id }
        localPortfolioRepository.saveHoldings(_holdings.value)
        recordCurrentYearSnapshot()
    }

    fun addWatchlistStock(stock: WatchlistStock): Boolean {
        if (_watchlist.value.any { it.code.equals(stock.code, ignoreCase = true) }) return false
        _watchlist.value = _watchlist.value + stock
        localPortfolioRepository.saveWatchlist(_watchlist.value)
        if (stock.alertEnabled) {
            WatchlistAlertScheduler.sync(getApplication(), true)
            WatchlistAlertScheduler.checkNow(getApplication())
        }
        return true
    }

    fun removeWatchlistStock(code: String) {
        _watchlist.value.firstOrNull { it.code.equals(code, ignoreCase = true) }?.let {
            WatchlistAlertWorker.clearState(getApplication(), it)
        }
        _watchlist.value = _watchlist.value.filterNot { it.code.equals(code, ignoreCase = true) }
        localPortfolioRepository.saveWatchlist(_watchlist.value)
        WatchlistAlertScheduler.sync(getApplication(), _watchlist.value.any { it.alertEnabled })
    }

    fun setWatchlistAlertEnabled(code: String, enabled: Boolean) {
        val stock = _watchlist.value.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: return
        if (enabled && stock.category != "jp") return
        if (!enabled) WatchlistAlertWorker.clearState(getApplication(), stock)
        _watchlist.update { list ->
            list.map { if (it.code.equals(code, ignoreCase = true)) it.copy(alertEnabled = enabled) else it }
        }
        localPortfolioRepository.saveWatchlist(_watchlist.value)
        WatchlistAlertScheduler.sync(getApplication(), _watchlist.value.any { it.alertEnabled })
        if (enabled) WatchlistAlertScheduler.checkNow(getApplication())
    }

    fun registerWatchlistAsHolding(code: String, shares: Int, acqPrice: Double, accountType: String) {
        val stock = _watchlist.value.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: return
        if (shares <= 0 || acqPrice <= 0.0) return
        val category = when (stock.category.lowercase()) {
            "us" -> Category.US
            "fund" -> Category.FUND
            else -> Category.JAPAN
        }
        addHolding(
            Holding(
                id = java.util.UUID.randomUUID().toString(),
                code = stock.code,
                name = stock.name,
                sector = stock.sector,
                market = stock.market,
                category = category,
                price = stock.price,
                currency = stock.currency,
                shares = shares,
                acqPrice = acqPrice,
                dps = stock.dps,
                yieldRate = if (stock.price > 0.0) stock.dps / stock.price * 100.0 else 0.0,
                yoc = stock.dps / acqPrice * 100.0,
                nextPayoutTiming = "支払予定データなし",
                nextPayoutAmount = "—",
                dividendMonths = stock.payoutMonths,
                dividendType = "未登録",
                accountType = accountType,
                dailyChange = stock.dailyChange,
                isPositive = stock.isPositive,
                fxRateToJpy = if (category == Category.US) 0.0 else 1.0
            )
        )
        removeWatchlistStock(stock.code)
    }

    fun holdingForCode(code: String): Holding? = _holdings.value.firstOrNull { it.code == code }

    fun sharedSecurityForCode(code: String): com.kurogane.dividend.data.SharedSecurityData? =
        _sharedSecurities.value[code]
}
