package com.kurogane.dividend.viewmodel

import com.kurogane.dividend.R
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.MonthlyDividend
import com.kurogane.dividend.model.RoadmapStep
import com.kurogane.dividend.model.StepStatus
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.model.WatchlistProjectionCache
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.CloudflareIncomeAdviceCatalog
import com.kurogane.dividend.data.CloudflareDividendEvent
import com.kurogane.dividend.data.CloudflareUpcomingDividendEvent
import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.data.CloudflareStockAnalysis
import com.kurogane.dividend.data.CloudflareSearchHit
import com.kurogane.dividend.data.CloudflareSearchQuote
import com.kurogane.dividend.data.CloudflareMonthlyPriceSeries
import com.kurogane.dividend.data.CloudflareDataRetrieval
import com.kurogane.dividend.data.CloudflareDividendHistoryRetrieval
import com.kurogane.dividend.data.CloudflareShareSplitEvent
import com.kurogane.dividend.data.CloudflareShareSplitHistory
import com.kurogane.dividend.data.DividendHistoryOverride
import com.kurogane.dividend.data.PriceRefreshPolicy
import com.kurogane.dividend.data.Tse33IndustryMaster
import com.kurogane.dividend.data.JPXTickSizeMaster
import com.kurogane.dividend.data.CsvHoldingRow
import com.kurogane.dividend.data.DvmBackupPreview
import com.kurogane.dividend.data.DvmBackupKind
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.data.PortfolioImporter
import com.kurogane.dividend.data.runCatchingCancellable
import com.kurogane.dividend.model.HoldingAllocation
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.PortfolioMath.refreshPayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.data.ExchangeRate
import com.kurogane.dividend.data.ExchangeRateRepository
import com.kurogane.dividend.data.JapaneseSecurityIdentity
import com.kurogane.dividend.notifications.NotificationKind
import com.kurogane.dividend.notifications.NotificationSettings
import com.kurogane.dividend.notifications.NotificationStore
import com.kurogane.dividend.notifications.NotificationScheduler
import com.kurogane.dividend.notifications.DiviewNotifications

private const val ANNUAL_DPS_CONTRACT_VERSION = 2

data class AssetManagementListItem(
    val id: String,
    val code: String,
    val displayName: String,
    val sector: String,
    val category: Category,
    val dividendMonths: List<Int>,
    val missingExchangeRate: Boolean,
    val sortValue: Double
)

data class AssetManagementSummary(
    val totalAssets: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalGain: Double = 0.0,
    val gainPercent: Double = 0.0,
    val annualDividend: Double = 0.0,
    val netAnnualDividend: Double = 0.0,
    val roadmapDividend: Long = 0L,
    val averageYield: Double = 0.0
)

data class AssetManagementUpcomingDividend(
    val month: Int = 12,
    val name: String? = null,
    val dividendType: String? = null,
    val shares: Int = 0,
    val amount: Long = 0L
)

data class WatchlistSortSelection(
    val criteria: String,
    val direction: String
)

class DividendViewModel @JvmOverloads constructor(
    application: Application,
    private val refreshOnStart: Boolean = true,
    private val marketRepository: CloudflareMarketRepository = CloudflareMarketRepository(),
    private val notificationScheduler: (Context, NotificationSettings) -> Unit = NotificationScheduler::sync
) : AndroidViewModel(application) {

    private val displayPreferences = application.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
    private val marketPreferences = application.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE)
    private val incomeAdviceMutex = Mutex()
    private val _incomeAdviceCatalog = MutableStateFlow(loadCachedIncomeAdviceCatalog())
    val incomeAdviceCatalog: StateFlow<CloudflareIncomeAdviceCatalog?> = _incomeAdviceCatalog.asStateFlow()
    private val _incomeAdviceFetchFailed = MutableStateFlow(marketPreferences.contains("income_advice_fetch_error"))
    val incomeAdviceFetchFailed: StateFlow<Boolean> = _incomeAdviceFetchFailed.asStateFlow()
    private val _watchlistSortSelection = MutableStateFlow(loadWatchlistSortSelection())
    val watchlistSortSelection: StateFlow<WatchlistSortSelection?> = _watchlistSortSelection.asStateFlow()
    private val dividendMonthRefreshMutex = Mutex()
    private val localImportStore = LocalImportStore(application)
    private val _shareholderBenefits = MutableStateFlow<Map<String, List<ShareholderBenefit>>>(emptyMap())
    val shareholderBenefits: StateFlow<Map<String, List<ShareholderBenefit>>> = _shareholderBenefits.asStateFlow()
    private var shareholderBenefitsSaveJob: Job? = null
    private val _stockClimateOverrides = MutableStateFlow<Map<String, StockClimate>>(emptyMap())
    val stockClimateOverrides: StateFlow<Map<String, StockClimate>> = _stockClimateOverrides.asStateFlow()
    private var stockClimateSaveJob: Job? = null
    private val _dividendHistoryOverrides = MutableStateFlow<Map<String, List<DividendHistoryOverride>>>(emptyMap())
    val dividendHistoryOverrides: StateFlow<Map<String, List<DividendHistoryOverride>>> = _dividendHistoryOverrides.asStateFlow()
    private val dividendHistoryOverrideSaveMutex = Mutex()
    private val notificationStore = NotificationStore(application)
    private val notificationMutex = Mutex()
    private val _notificationSettings = MutableStateFlow(notificationStore.settings())
    val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()
    private var hasRestoredLocalHoldings = false
    private val saveMutex = Mutex()
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val restoration = CompletableDeferred<Unit>()
    private val _localDataRestored = MutableStateFlow(false)
    val localDataRestored: StateFlow<Boolean> = _localDataRestored.asStateFlow()
    private var holdingsLoadFailed = false
    private var watchlistLoadFailed = false
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    var assetScrollIndex = 0
    var assetScrollOffset = 0
    var annualScrollIndex = 0
    var annualScrollOffset = 0
    var annualAnalysisScrollPositions: List<Pair<Int, Int>> = List(3) { 0 to 0 }
    var roadmapScrollIndex = 0
    var roadmapScrollOffset = 0
    var roadmapStageFilter = 1
    var watchlistScrollIndex = 0
    var watchlistScrollOffset = 0

    private val _isPrivacyActive = MutableStateFlow(displayPreferences.getBoolean("mask_at_startup", false))
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _themeMode = MutableStateFlow(displayPreferences.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isOledBlack = MutableStateFlow(displayPreferences.getBoolean("oled_black", false))
    val isOledBlack: StateFlow<Boolean> = _isOledBlack.asStateFlow()

    private val _selectedAccent = MutableStateFlow(displayPreferences.getString("selected_accent", "emerald") ?: "emerald")
    val selectedAccent: StateFlow<String> = _selectedAccent.asStateFlow()

    private val _taxRateMode = MutableStateFlow(displayPreferences.getString("tax_rate_mode", "gross") ?: "gross")
    val taxRateMode: StateFlow<String> = _taxRateMode.asStateFlow()
    private val _maskAtStartup = MutableStateFlow(displayPreferences.getBoolean("mask_at_startup", false))
    val maskAtStartup: StateFlow<Boolean> = _maskAtStartup.asStateFlow()

    private val _calculationBasis = MutableStateFlow(displayPreferences.getString("calculation_basis", "eval") ?: "eval")
    val calculationBasis = _calculationBasis.asStateFlow()
    private val _roadmapTargetYield = MutableStateFlow(displayPreferences.getFloat("roadmap_target_yield", 4f).toDouble())
    val roadmapTargetYield = _roadmapTargetYield.asStateFlow()
    private val _growthSimulatorMonthlyContribution = MutableStateFlow(
        displayPreferences.getLong("growth_simulator_monthly_contribution", 30_000L).coerceIn(0L, 1_000_000L)
    )
    val growthSimulatorMonthlyContribution = _growthSimulatorMonthlyContribution.asStateFlow()
    private val _growthSimulatorDividendYield = MutableStateFlow(
        displayPreferences.getInt("growth_simulator_dividend_yield_tenths", 40).coerceIn(0, 300) / 10.0
    )
    val growthSimulatorDividendYield = _growthSimulatorDividendYield.asStateFlow()
    private val _compositionThreshold = MutableStateFlow(displayPreferences.getInt("composition_threshold", 5).coerceIn(1, 10))
    val compositionThreshold = _compositionThreshold.asStateFlow()
    private val _compositionOtherEnabled = MutableStateFlow(displayPreferences.getBoolean("composition_other_enabled", true))
    val compositionOtherEnabled = _compositionOtherEnabled.asStateFlow()
    private val _roadmapShareTab = MutableStateFlow(displayPreferences.getInt("roadmap_share_tab", 0))
    val roadmapShareTab = _roadmapShareTab.asStateFlow()
    private val _roadmapShareMasked = MutableStateFlow(displayPreferences.getBoolean("roadmap_share_masked", _maskAtStartup.value))
    val roadmapShareMasked = _roadmapShareMasked.asStateFlow()

    private fun saveDisplaySetting(editor: android.content.SharedPreferences.Editor, update: () -> Unit) {
        if (editor.commit()) update()
        else _saveError.value = getApplication<Application>().getString(R.string.setting_save_failed)
    }

    private fun loadWatchlistSortSelection(): WatchlistSortSelection? {
        val criteria = displayPreferences.getString("watchlist_sort_criteria", null)
            ?.takeIf { it in WATCHLIST_SORT_CRITERIA }
            ?: return null
        val direction = displayPreferences.getString("watchlist_sort_direction", null)
            ?.takeIf { it in WATCHLIST_SORT_DIRECTIONS }
            ?: return null
        return WatchlistSortSelection(criteria, direction)
    }

    fun saveWatchlistSortSelection(criteria: String, direction: String): Boolean {
        if (criteria !in WATCHLIST_SORT_CRITERIA || direction !in WATCHLIST_SORT_DIRECTIONS) return false
        val selection = WatchlistSortSelection(criteria, direction)
        if (_watchlistSortSelection.value == selection) return true
        val saved = displayPreferences.edit()
            .putString("watchlist_sort_criteria", criteria)
            .putString("watchlist_sort_direction", direction)
            .commit()
        if (!saved) {
            _saveError.value = getApplication<Application>().getString(R.string.setting_save_failed)
            return false
        }
        _watchlistSortSelection.value = selection
        return true
    }

    fun setCalculationBasis(basis: String) {
        if (basis !in setOf("eval", "cost")) return
        saveDisplaySetting(displayPreferences.edit().putString("calculation_basis", basis)) { _calculationBasis.value = basis }
    }

    fun setRoadmapTargetYield(value: Double) {
        if (value !in setOf(3.5, 4.0, 4.5)) return
        saveDisplaySetting(displayPreferences.edit().putFloat("roadmap_target_yield", value.toFloat())) { _roadmapTargetYield.value = value }
    }

    fun setGrowthSimulatorMonthlyContribution(value: Long) {
        val bounded = value.coerceIn(0L, 1_000_000L)
        saveDisplaySetting(displayPreferences.edit().putLong("growth_simulator_monthly_contribution", bounded)) {
            _growthSimulatorMonthlyContribution.value = bounded
        }
    }

    fun setGrowthSimulatorDividendYield(value: Double) {
        val tenths = kotlin.math.round(value * 10.0).toInt().coerceIn(0, 300)
        saveDisplaySetting(displayPreferences.edit().putInt("growth_simulator_dividend_yield_tenths", tenths)) {
            _growthSimulatorDividendYield.value = tenths / 10.0
        }
    }

    fun setCompositionThreshold(value: Int) {
        if (value !in 1..10) return
        updateCompositionThreshold(value)
        persistCompositionThreshold()
    }

    fun updateCompositionThreshold(value: Int) {
        if (value in 1..10 && _compositionThreshold.value != value) {
            _compositionThreshold.value = value
        }
    }

    fun persistCompositionThreshold() {
        val value = _compositionThreshold.value
        if (displayPreferences.getInt("composition_threshold", 5) == value) return
        if (!displayPreferences.edit().putInt("composition_threshold", value).commit()) {
            _compositionThreshold.value = displayPreferences.getInt("composition_threshold", 5).coerceIn(1, 10)
            _saveError.value = getApplication<Application>().getString(R.string.setting_save_failed)
        }
    }

    fun setCompositionOtherEnabled(enabled: Boolean) {
        saveDisplaySetting(displayPreferences.edit().putBoolean("composition_other_enabled", enabled)) { _compositionOtherEnabled.value = enabled }
    }

    fun setRoadmapShareTab(value: Int) {
        if (value !in 0..1) return
        saveDisplaySetting(displayPreferences.edit().putInt("roadmap_share_tab", value)) { _roadmapShareTab.value = value }
    }

    fun setRoadmapShareMasked(value: Boolean) {
        saveDisplaySetting(displayPreferences.edit().putBoolean("roadmap_share_masked", value)) { _roadmapShareMasked.value = value }
    }

    private val _usTaxDeduction = MutableStateFlow(displayPreferences.getBoolean("us_tax_deduction", true))
    val usTaxDeduction = _usTaxDeduction.asStateFlow()
    private val _biometricEnabled = MutableStateFlow(displayPreferences.getBoolean("biometric_enabled", false))
    val biometricEnabled = _biometricEnabled.asStateFlow()
    private val _exchangeRate = MutableStateFlow<ExchangeRate?>(null)
    val exchangeRate = _exchangeRate.asStateFlow()
    private val _exchangeRateError = MutableStateFlow<String?>(null)
    val exchangeRateError = _exchangeRateError.asStateFlow()
    private var identities: Map<String, JapaneseSecurityIdentity> = emptyMap()
    private val _financialData = MutableStateFlow<Map<String, FinancialData>>(emptyMap())
    val financialData = _financialData.asStateFlow()
    private val _stockAnalysisData = MutableStateFlow<Map<String, CloudflareStockAnalysis>>(emptyMap())
    val stockAnalysisData = _stockAnalysisData.asStateFlow()
    private val _cloudflareSecurityData = MutableStateFlow<Map<String, CloudflareSecurity>>(emptyMap())
    val cloudflareSecurityData = _cloudflareSecurityData.asStateFlow()
    private val stockAnalysisMutex = Mutex()
    private val stockAnalysisLoading = mutableSetOf<String>()
    private val _dividendEventRevision = MutableStateFlow(0)
    val dividendEventRevision = _dividendEventRevision.asStateFlow()
    private val dividendEventCacheLock = Any()
    private val dividendEventCache = mutableMapOf<String, List<CloudflareDividendEvent>>()
    private val upcomingDividendEventCache = mutableMapOf<String, CloudflareUpcomingDividendEvent?>()
    private val watchlistProjectionCache = WatchlistProjectionCache()
    private val financialMutex = Mutex()
    private val financialLoading = mutableSetOf<String>()
    private val financialSlots = Semaphore(4)
    private val cloudflareSecurityMutex = Mutex()
    private val cloudflareSecuritySlots = Semaphore(4)
    private val cloudflareSecurityLoading = mutableSetOf<String>()
    private val stockAnalysisSlots = Semaphore(4)
    private val monthlyPriceRefreshMutex = Mutex()
    private val shareSplitRefreshMutex = Mutex()
    private val dataGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    fun japaneseDisplayName(code: String, fallback: String): String =
        identities[Tse33IndustryMaster.normalizeCode(code)]?.name ?: when (code.uppercase(java.util.Locale.ROOT)) {
            "AAPL" -> "アップル"; "MSFT" -> "マイクロソフト"; "GOOGL", "GOOG" -> "アルファベット"
            "AMZN" -> "アマゾン・ドット・コム"; "NVDA" -> "エヌビディア"; "TSLA" -> "テスラ"
            "META" -> "メタ・プラットフォームズ"; "KO" -> "コカ・コーラ"; "V" -> "ビザ"
            "JNJ" -> "ジョンソン・エンド・ジョンソン"; "PG" -> "プロクター・アンド・ギャンブル"
            else -> fallback
        }

    private fun runtimeHolding(
        holding: Holding,
        cachedPrices: Map<String, Double> = readCachedPrices(),
        cachedDividendsPerShare: Map<String, Double> = readCachedDividendsPerShare(),
        cachedNoDividendCodes: Set<String> = readCachedNoDividendCodes(),
        cachedDividendStatusCodes: Set<String> = readCachedDividendStatusCodes()
    ): Holding {
        val code = holding.code.uppercase(java.util.Locale.ROOT)
        val price = cachedPrices[code] ?: holding.price
        val quoteDps = cachedDividendsPerShare[code]
        val noDividendConfirmed = resolveNoDividendStatus(
            code, quoteDps, holding.isNoDividendConfirmed, cachedNoDividendCodes, cachedDividendStatusCodes
        )
        val dps = quoteDps ?: if (noDividendConfirmed) 0.0 else
            _financialData.value[holding.code]?.dividends?.lastOrNull()?.dps ?: holding.dps
        return holding.copy(
        name = japaneseDisplayName(holding.code, holding.name),
        sector = if (holding.category == Category.JAPAN) identities[Tse33IndustryMaster.normalizeCode(holding.code)]?.industry ?: "東証33業種未分類" else holding.sector,
        price = price,
        usdJpyRate = _exchangeRate.value?.usdJpy,
        usWithholdingEnabled = _usTaxDeduction.value,
        financialProfile = _financialData.value[holding.code]?.profile,
        dps = dps, yieldRate = PortfolioMath.yieldPercent(dps, price), yoc = PortfolioMath.yieldPercent(dps, holding.acqPrice),
        isNoDividendConfirmed = noDividendConfirmed
        ).refreshPayout()
    }

    private fun normalizedSecurityCode(code: String): String =
        PortfolioMath.normalizeCode(code).removeSuffix(".T").uppercase(java.util.Locale.ROOT)

    private fun refreshRuntimeValues(onlyCode: String? = null) {
        val normalizedOnlyCode = onlyCode?.let(::normalizedSecurityCode)
        val cachedPrices = if (normalizedOnlyCode == null) readCachedPrices() else emptyMap()
        val cachedDividendsPerShare = if (normalizedOnlyCode == null) {
            readCachedDividendsPerShare()
        } else {
            readCachedDividendPerShare(normalizedOnlyCode)?.let { mapOf(normalizedOnlyCode to it) }.orEmpty()
        }
        val cachedNoDividendCodes = readCachedNoDividendCodes()
        val cachedDividendStatusCodes = readCachedDividendStatusCodes()
        _holdings.update { list ->
            list.map { holding ->
                if (normalizedOnlyCode == null || normalizedSecurityCode(holding.code) == normalizedOnlyCode) {
                    runtimeHolding(holding, cachedPrices, cachedDividendsPerShare, cachedNoDividendCodes, cachedDividendStatusCodes)
                } else holding
            }
        }
        _watchlist.update { list -> list.map { stock ->
            if (normalizedOnlyCode != null && normalizedSecurityCode(stock.code) != normalizedOnlyCode) return@map stock
            val code = stock.code.uppercase(java.util.Locale.ROOT)
            val price = cachedPrices[code] ?: stock.price
            val quoteDps = cachedDividendsPerShare[code]
            val noDividendConfirmed = resolveNoDividendStatus(
                code, quoteDps, stock.isNoDividendConfirmed, cachedNoDividendCodes, cachedDividendStatusCodes
            )
            val dps = quoteDps ?: if (noDividendConfirmed) 0.0 else
                _financialData.value[stock.code]?.dividends?.lastOrNull()?.dps ?: stock.dps
            stock.copy(
                name = japaneseDisplayName(stock.code, stock.name),
                sector = if (stock.category == "jp") identities[Tse33IndustryMaster.normalizeCode(stock.code)]?.industry ?: "東証33業種未分類" else stock.sector,
                price = price,
                usdJpyRate = _exchangeRate.value?.usdJpy,
                dps = dps, currentYield = PortfolioMath.yieldPercent(dps, price),
                isNoDividendConfirmed = noDividendConfirmed,
                targetPrice = if (stock.targetYield.isFinite() && stock.targetYield > 0) dps / stock.targetYield * 100 else 0.0
            )
        } }
    }

    fun setUsTaxDeduction(enabled: Boolean) {
        _usTaxDeduction.value = enabled
        displayPreferences.edit().putBoolean("us_tax_deduction", enabled).apply()
        refreshRuntimeValues()
    }

    fun setBiometricEnabled(enabled: Boolean): Boolean {
        // The activity only calls this after a successful system authentication.
        if (!displayPreferences.edit().putBoolean("biometric_enabled", enabled).commit()) return false
        _biometricEnabled.value = enabled
        return true
    }

    suspend fun refreshExchangeRate() {
        val now = System.currentTimeMillis()
        val attemptAtKey = "fx_fetch_attempt_at"
        val attemptDateKey = "fx_fetch_attempt_jst_date"
        if (!PriceRefreshPolicy.shouldFetch(
                now,
                marketPreferences.getLong(attemptAtKey, 0L),
                marketPreferences.getString(attemptDateKey, null)
            )) return
        marketPreferences.edit().putLong(attemptAtKey, now)
            .putString(attemptDateKey, PriceRefreshPolicy.japanDate(now)).apply()
        val generation = dataGeneration.get()
        runCatchingCancellable { ExchangeRateRepository().latest() }.onSuccess { rate ->
            if (generation != dataGeneration.get()) return@onSuccess
            _exchangeRate.value = rate
            _exchangeRateError.value = null
            marketPreferences.edit().putString("fx", JSONObject().put("base", "USD").put("quote", "JPY")
                .put("rate", rate.usdJpy).put("date", rate.date).toString()).apply()
            refreshRuntimeValues()
        }.onFailure {
            if (generation != dataGeneration.get()) return@onFailure
            _exchangeRateError.value = if (_exchangeRate.value == null) getApplication<android.app.Application>().getString(R.string.exchange_rate_error) else getApplication<android.app.Application>().getString(R.string.exchange_rate_error_2)
        }
    }

    suspend fun fetchFinancialData(code: String, force: Boolean = false): FinancialData? {
        val generation = dataGeneration.get()
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        if (!normalized.matches(Regex("[A-Z0-9]{4}"))) return null
        val today = PriceRefreshPolicy.japanDate(System.currentTimeMillis())
        val contractVersionKey = "financial_contract_version_$normalized"
        val contractAttemptVersionKey = "financial_contract_attempt_version_$normalized"
        val requiresContractRefresh = marketPreferences.getInt(contractVersionKey, 0) < FINANCIAL_PAYLOAD_VERSION
        val attemptKey = "financial_fetch_attempt_at_$normalized"
        val attemptDateKey = "financial_fetch_attempt_jst_$normalized"
        val failureKey = "financial_fetch_failure_$normalized"
        val contractRefreshAttemptedToday = requiresContractRefresh
            && marketPreferences.getInt(contractAttemptVersionKey, 0) >= FINANCIAL_PAYLOAD_VERSION
            && marketPreferences.getString(attemptDateKey, null) == today
        if (!requiresContractRefresh && marketPreferences.getString("financial_date_$normalized", null) == today) {
            _financialData.value[normalized]?.let { return it }
            val cached = withContext(Dispatchers.IO) {
                runCatching {
                    marketPreferences.getString("financial_$normalized", null)
                        ?.let { FinancialData.parse(JSONObject(it), normalized) }
                }.getOrNull()
            }
            if (cached != null && generation == dataGeneration.get()) {
                _financialData.update { it + (normalized to cached) }
                return cached
            }
        }
        val now = System.currentTimeMillis()
        // An explicit retry may bypass a failed attempt. Today's successfully
        // parsed document is still returned by the cache check above.
        if (!force && !PriceRefreshPolicy.shouldFetch(
                now,
                marketPreferences.getLong(attemptKey, 0L),
                marketPreferences.getString(attemptDateKey, null)
                    ?: marketPreferences.getString("financial_date_$normalized", null)
            ) && (!requiresContractRefresh || contractRefreshAttemptedToday)) {
            val cached = _financialData.value[normalized] ?: withContext(Dispatchers.IO) {
                runCatching {
                    marketPreferences.getString("financial_$normalized", null)
                        ?.let { FinancialData.parse(JSONObject(it), normalized) }
                }.getOrNull()
            }
            if (cached != null) _financialData.update { it + (normalized to cached) }
            if (cached == null) {
                com.kurogane.dividend.data.CachedFetchFailure.decode(marketPreferences.getString(failureKey, null))
                    ?.let { throw it }
            }
            return cached
        }
        marketPreferences.edit()
            .putLong(attemptKey, now)
            .putString(attemptDateKey, PriceRefreshPolicy.japanDate(now))
            .putInt(contractAttemptVersionKey, FINANCIAL_PAYLOAD_VERSION)
            .apply()
        val allowed = financialMutex.withLock { financialLoading.add(normalized) }
        if (!allowed) return _financialData.value[normalized]
        try {
            val document = financialSlots.withPermit { marketRepository.financialDocument(normalized) }
            if (document == null) {
                if (generation == dataGeneration.get()) marketPreferences.edit { remove(failureKey) }
                return null
            }
            val parsed = withContext(Dispatchers.Default) { FinancialData.parse(document, normalized) }
            if (generation != dataGeneration.get()) return null
            if (_notificationSettings.value.dividendChangeEnabled) {
                notificationStore.observeDividendHistory(normalized, _holdings.value.firstOrNull { it.code.equals(normalized, true) }?.name
                    ?: _watchlist.value.firstOrNull { it.code.equals(normalized, true) }?.name
                    ?: normalized, parsed.dividends.map { it.year to it.dps })
            }
            _financialData.update { it + (normalized to parsed) }
            marketPreferences.edit().putString("financial_$normalized", document.toString())
                .putString("financial_date_$normalized", today)
                .putInt(contractVersionKey, FINANCIAL_PAYLOAD_VERSION)
                .remove(failureKey).apply()
            refreshRuntimeValues(normalized)
            saveRequests.trySend(Unit)
            return parsed
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (generation == dataGeneration.get()) {
                marketPreferences.edit {
                    putString(failureKey, com.kurogane.dividend.data.CachedFetchFailure.encode(failure))
                }
                android.util.Log.w("CloudflareMarket", "Financial fetch failed (${failure.javaClass.simpleName})")
            }
            throw failure
        } finally { financialMutex.withLock { financialLoading.remove(normalized) } }
    }

    suspend fun fetchPortfolioFinancialData(force: Boolean = false) = coroutineScope {
        _holdings.value.filter { it.category == Category.JAPAN }.map { it.code }.distinct().map { code ->
            async { runCatchingCancellable { fetchFinancialData(code, force = force) } }
        }.awaitAll()
    }

    fun refreshAnalysisFinancialDataIfDue() {
        viewModelScope.launch {
            restoration.await()
            refreshFinancialDataIfDue()
            refreshStockAnalysisForHoldings()
        }
    }

    private suspend fun refreshStockAnalysisForHoldings() = coroutineScope {
        val codes = (
            _holdings.value.filter { it.category != Category.FUND }.map { it.code } +
                _watchlist.value.filter { it.category == "jp" || it.category == "us" }.map { it.code }
            ).distinctBy { it.uppercase(java.util.Locale.ROOT) }
        codes.map { code ->
            async { runCatchingCancellable { fetchStockAnalysis(code) } }
        }.awaitAll()
    }

    private suspend fun refreshStockDetailDataForPortfolio() = coroutineScope {
        val holdings = _holdings.value.filter { it.category != Category.FUND }
        val watchlist = _watchlist.value.filter { it.category == "jp" || it.category == "us" }
        val codes = (holdings.map { it.code } + watchlist.map { it.code })
            .distinctBy { PortfolioMath.normalizeCode(it).uppercase(java.util.Locale.ROOT) }
        codes.map { code ->
            async { fetchCloudflareSecurityIfDue(code) }
        }.awaitAll()

        refreshStockAnalysisForHoldings()

        val japaneseCodes = (
            holdings.filter { it.category == Category.JAPAN }.map { it.code } +
                _watchlist.value.filter { it.category == "jp" }.map { it.code }
            ).distinctBy { PortfolioMath.normalizeCode(it).uppercase(java.util.Locale.ROOT) }
        val latestCompleteYear = LocalDate.now(ZoneId.of("Asia/Tokyo")).year - 1
        val historyYears = (latestCompleteYear - 7..latestCompleteYear).toList()
        if (japaneseCodes.isNotEmpty()) {
            runCatchingCancellable { loadMonthlyPriceHistory(japaneseCodes, historyYears.map { "$it-12" }) }
                .onFailure { android.util.Log.w("CloudflareMarket", "年末株価履歴の取得に失敗しました", it) }
        }

        val from = LocalDate.of(historyYears.first(), 1, 1)
        val to = LocalDate.of(historyYears.last(), 12, 31)
        codes.map { code ->
            async {
                runCatchingCancellable { loadShareSplitHistory(code, from, to) }
                    .onFailure { android.util.Log.w("CloudflareMarket", "株式分割履歴の取得に失敗しました: $code", it) }
            }
        }.awaitAll()
    }

    private suspend fun refreshFinancialDataIfDue(force: Boolean = false) = coroutineScope {
        val japanDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))
        val today = japanDate.toString()
        val requiredHistoricalYears = (japanDate.year - 4 until japanDate.year).toSet()
        (_holdings.value.filter { it.category == Category.JAPAN }.map { it.code } +
            _watchlist.value.filter { it.category == "jp" }.map { it.code }).distinct().map { code ->
            async {
                val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
                val history = _financialData.value[normalized]?.dividends.orEmpty()
                val hasMissingHistoricalYear = requiredHistoricalYears.any { year -> history.none { it.year == year } }
                val retryKey = "financial_dividend_gap_retry_jst_$normalized"
                val alreadyRetriedToday = marketPreferences.getString(retryKey, null) == today
                val shouldRefreshMissingHistory = hasMissingHistoricalYear && !alreadyRetriedToday
                val result = runCatchingCancellable {
                    fetchFinancialData(code, force = force || shouldRefreshMissingHistory)
                }
                if (shouldRefreshMissingHistory && result.getOrNull() != null) {
                    marketPreferences.edit().putString(retryKey, today).apply()
                }
                result
            }
        }.awaitAll()
    }

    private fun yahooSymbol(category: Category, code: String): String? {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        return when (category) {
            Category.JAPAN -> "$normalized.T".takeIf { normalized.matches(Regex("[A-Z0-9]{4,5}")) }
            Category.US -> normalized.takeIf { it.matches(Regex("[A-Z0-9.^_=-]{1,32}")) }
            Category.FUND -> null
        }
    }

    private fun yahooSymbol(stock: WatchlistStock): String? = when (stock.category.lowercase(java.util.Locale.ROOT)) {
        "jp" -> yahooSymbol(Category.JAPAN, stock.code)
        "us" -> yahooSymbol(Category.US, stock.code)
        else -> null
    }

    private fun cachedDividendMonths(symbol: String): List<Int> = runCatching {
        val rows = marketPreferences.getString("dividend_months_$symbol", null)
            ?.let(::JSONArray) ?: return@runCatching emptyList()
        (0 until rows.length()).map { rows.optInt(it, -1) }.filter { it in 1..12 }.distinct().sorted()
    }.getOrDefault(emptyList())

    private fun storeDividendMonths(symbol: String, months: List<Int>, fetchedOnJapanDate: String) {
        val rows = JSONArray()
        months.filter { it in 1..12 }.distinct().sorted().forEach(rows::put)
        marketPreferences.edit()
            .putString("dividend_months_$symbol", rows.toString())
            .putString("dividend_months_fetched_jst_$symbol", fetchedOnJapanDate)
            .apply()
    }

    private fun applyDividendMonths(symbol: String, months: List<Int>) {
        _holdings.update { list ->
            list.map { holding ->
                if (yahooSymbol(holding.category, holding.code) == symbol)
                    holding.copy(dividendMonths = months).refreshPayout()
                else holding
            }
        }
        _watchlist.update { list ->
            list.map { stock ->
                if (yahooSymbol(stock) == symbol) stock.copy(
                    payoutMonths = months,
                    payoutTiming = months.joinToString(" / ") { "${it}月" }.ifBlank { "配当月未取得" }
                ) else stock
            }
        }
        saveRequests.trySend(Unit)
    }

    private fun applyCachedDividendMonths() {
        _holdings.update { list -> list.map { holding ->
            val months = yahooSymbol(holding.category, holding.code)?.let { symbol ->
                cachedDividendMonths(symbol)
            } ?: emptyList()
            holding.copy(dividendMonths = months).refreshPayout()
        } }
        _watchlist.update { list -> list.map { stock ->
            val months = yahooSymbol(stock)?.let { symbol ->
                cachedDividendMonths(symbol)
            } ?: emptyList()
            stock.copy(payoutMonths = months,
                payoutTiming = months.joinToString(" / ") { "${it}月" }.ifBlank { "配当月未取得" })
        } }
    }

    private fun loadCachedIncomeAdviceCatalog(): CloudflareIncomeAdviceCatalog? = runCatching {
        marketPreferences.getString("income_advice_catalog", null)?.let(marketRepository::parseIncomeAdviceCatalog)
    }.getOrNull()

    private suspend fun refreshIncomeAdviceCatalogIfDue() = incomeAdviceMutex.withLock {
        val now = System.currentTimeMillis()
        val attemptAt = marketPreferences.getLong("income_advice_fetch_attempt_at", 0L)
        val attemptDate = marketPreferences.getString("income_advice_fetch_attempt_jst", null)
        if (!PriceRefreshPolicy.shouldFetch(now, attemptAt, attemptDate)) return@withLock

        marketPreferences.edit()
            .putLong("income_advice_fetch_attempt_at", now)
            .putString("income_advice_fetch_attempt_jst", PriceRefreshPolicy.japanDate(now))
            .apply()
        runCatchingCancellable { marketRepository.fetchIncomeAdviceCatalog() }
            .onSuccess { catalog ->
                marketPreferences.edit()
                    .putString("income_advice_catalog", catalog.rawJson)
                    .putLong("income_advice_fetched_at", now)
                    .putString("income_advice_fetched_jst", PriceRefreshPolicy.japanDate(now))
                    .remove("income_advice_fetch_error")
                    .apply()
                _incomeAdviceCatalog.value = catalog
                _incomeAdviceFetchFailed.value = false
            }
            .onFailure { error ->
                _incomeAdviceFetchFailed.value = true
                marketPreferences.edit().putString("income_advice_fetch_error", error::class.java.simpleName).apply()
                android.util.Log.w("CloudflareMarket", "空白月解消候補の取得に失敗しました", error)
            }
    }

    fun dividendEventsFor(code: String): List<CloudflareDividendEvent> {
        val normalized = yahooSymbol(Category.JAPAN, code) ?: yahooSymbol(Category.US, code) ?: return emptyList()
        synchronized(dividendEventCacheLock) { dividendEventCache[normalized] }?.let { return it }

        val parsed: List<CloudflareDividendEvent> = runCatching {
            val rows = marketPreferences.getString("dividend_events_$normalized", null)?.let(::JSONArray)
                ?: return@runCatching emptyList()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(row.optString("date")) }.getOrNull() ?: return@mapNotNull null
                val amount = row.optDouble("amount", Double.NaN).takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                val finalRightsDate = runCatching { LocalDate.parse(row.optString("final_rights_date")) }.getOrNull()
                val recordDate = runCatching { LocalDate.parse(row.optString("record_date")) }.getOrNull()
                CloudflareDividendEvent(date, amount, finalRightsDate, recordDate)
            }.sortedBy { it.date }
        }.getOrDefault(emptyList())

        return synchronized(dividendEventCacheLock) {
            dividendEventCache.getOrPut(normalized) { parsed }
        }
    }

    fun upcomingDividendEventFor(code: String): CloudflareUpcomingDividendEvent? {
        val normalized = yahooSymbol(Category.JAPAN, code) ?: yahooSymbol(Category.US, code) ?: return null
        synchronized(dividendEventCacheLock) {
            if (upcomingDividendEventCache.containsKey(normalized)) return upcomingDividendEventCache[normalized]
        }
        val parsed = runCatching {
            val row = marketPreferences.getString("upcoming_dividend_event_$normalized", null)
                ?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: return@runCatching null
            val exDividendDate = LocalDate.parse(row.optString("ex_dividend_date"))
            val finalRightsDate = runCatching { LocalDate.parse(row.optString("final_rights_date")) }.getOrNull()
            val recordDate = runCatching { LocalDate.parse(row.optString("record_date")) }.getOrNull()
            CloudflareUpcomingDividendEvent(
                exDividendDate, finalRightsDate, recordDate,
                estimatedFromHistory = row.optBoolean("estimated_from_history", false)
            )
        }.getOrNull()
        return synchronized(dividendEventCacheLock) {
            upcomingDividendEventCache.getOrPut(normalized) { parsed }
        }
    }

    private fun storeUpcomingDividendEvent(
        symbol: String,
        event: CloudflareUpcomingDividendEvent?
    ) {
        val normalized = symbol.uppercase(java.util.Locale.ROOT)
        if (event == null) {
            marketPreferences.edit().putString("upcoming_dividend_event_$normalized", "").apply()
        } else {
            marketPreferences.edit().putString("upcoming_dividend_event_$normalized", JSONObject()
                .put("ex_dividend_date", event.exDividendDate.toString())
                .put("final_rights_date", event.finalRightsDate?.toString().orEmpty())
                .put("record_date", event.recordDate?.toString().orEmpty())
                .put("estimated_from_history", event.estimatedFromHistory).toString()).apply()
        }
        synchronized(dividendEventCacheLock) {
            upcomingDividendEventCache[normalized] = event
        }
    }

    private fun storeDividendEvents(symbol: String, events: List<com.kurogane.dividend.data.CloudflareDividendEvent>) {
        val rows = JSONArray()
        events.forEach { event ->
            rows.put(JSONObject()
                .put("date", event.date.toString())
                .put("amount", event.amountPerShare)
                .put("final_rights_date", event.finalRightsDate?.toString())
                .put("record_date", event.recordDate?.toString()))
        }
        marketPreferences.edit().putString("dividend_events_$symbol", rows.toString()).apply()
        synchronized(dividendEventCacheLock) {
            dividendEventCache[symbol.uppercase(java.util.Locale.ROOT)] = events.sortedBy { it.date }
        }
        _dividendEventRevision.update { it + 1 }
    }

    private suspend fun refreshDividendMonths(symbols: Collection<String>) = dividendMonthRefreshMutex.withLock {
        val today = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString()
        val now = System.currentTimeMillis()
        val dueSymbols = symbols.map { it.trim().uppercase(java.util.Locale.ROOT) }
            .distinct()
            .filter { symbol ->
                PriceRefreshPolicy.shouldFetch(
                    now,
                    marketPreferences.getLong("dividend_fetch_attempt_at_$symbol", 0L),
                    marketPreferences.getString("dividend_fetch_attempt_jst_$symbol", null)
                        ?: marketPreferences.getString("dividend_events_fetched_jst_$symbol", null)
                        ?: marketPreferences.getString("dividend_months_fetched_jst_$symbol", null)
                ) && (marketPreferences.getString("dividend_months_fetched_jst_$symbol", null) != today ||
                    marketPreferences.getString("dividend_events_fetched_jst_$symbol", null) != today)
            }
        if (dueSymbols.isEmpty()) return@withLock
        val attemptEditor = marketPreferences.edit()
        dueSymbols.forEach { symbol ->
            attemptEditor.putLong("dividend_fetch_attempt_at_$symbol", now)
                .putString("dividend_fetch_attempt_jst_$symbol", today)
        }
        attemptEditor.apply()
        for (batch in dueSymbols.chunked(50)) {
            val result = runCatchingCancellable { marketRepository.fetchDividendMonths(batch) }
            if (result.isFailure) {
                android.util.Log.w("CloudflareMarket", "配当月の取得に失敗しました", result.exceptionOrNull())
                continue
            }
            val monthsBySymbol = result.getOrThrow().monthsBySymbol
            val eventsBySymbol = result.getOrThrow().eventsBySymbol
            for (symbol in batch) {
                val months = monthsBySymbol[symbol] ?: continue
                storeDividendMonths(symbol, months, today)
                storeDividendEvents(symbol, eventsBySymbol[symbol].orEmpty())
                storeUpcomingDividendEvent(symbol, result.getOrThrow().upcomingEventsBySymbol[symbol])
                marketPreferences.edit().putString("dividend_events_fetched_jst_$symbol", today).apply()
                applyDividendMonths(symbol, months)
            }
        }
    }

    private suspend fun refreshDividendHistoryIfDue() {
        if (!refreshOnStart || dividendHistoryRefreshStarted) return
        dividendHistoryRefreshStarted = true
        val symbols = (_holdings.value.mapNotNull { yahooSymbol(it.category, it.code) } +
            _watchlist.value.mapNotNull(::yahooSymbol)).distinct()
        refreshDividendMonths(symbols)
    }

    fun refreshDividendMonthsForSecurity(category: Category, code: String) {
        if (!refreshOnStart) return
        val symbol = yahooSymbol(category, code) ?: return
        viewModelScope.launch { refreshDividendMonths(listOf(symbol)) }
    }

    private var dividendHistoryRefreshStarted = false

    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _holdings = MutableStateFlow(emptyList<Holding>())
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val holdingsById = _holdings
        .map { current -> current.associateBy(Holding::id) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val assetManagementSummary: StateFlow<AssetManagementSummary> = combine(
        _holdings, _taxRateMode, _calculationBasis
    ) { current, taxMode, basis ->
        val totalAssets = current.sumOf { it.totalValuation }
        val totalCost = current.sumOf { it.totalCost }
        val annualDividend = current.sumOf { it.getEffectiveAnnualDividend(taxMode) }
        val netAnnualDividend = current.sumOf { it.getEffectiveAnnualDividend("net") }
        val roadmapDividend = current.sumOf { it.getEffectiveAnnualDividend(taxMode).toLong() }
        val totalGain = totalAssets - totalCost
        AssetManagementSummary(
            totalAssets = totalAssets,
            totalCost = totalCost,
            totalGain = totalGain,
            gainPercent = if (totalCost > 0.0) totalGain / totalCost * 100.0 else 0.0,
            annualDividend = annualDividend,
            netAnnualDividend = netAnnualDividend,
            roadmapDividend = roadmapDividend,
            averageYield = PortfolioMath.portfolioYield(annualDividend, totalAssets, totalCost, basis)
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AssetManagementSummary())

    val assetManagementUpcomingDividend: StateFlow<AssetManagementUpcomingDividend> = _holdings
        .map { current ->
            val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
            val valid = current.filter { it.dividendMonths.isNotEmpty() && it.shares > 0 }
            var next = valid.firstOrNull()
            var nextMonth = 12
            var minDiff = 999
            valid.forEach { holding ->
                holding.dividendMonths.forEach { month ->
                    var diff = month - currentMonth
                    if (diff < 0) diff += 12
                    if (diff < minDiff) {
                        minDiff = diff
                        next = holding
                        nextMonth = month
                    }
                }
            }
            val amount = next?.let { holding ->
                val monthsCount = if (holding.dividendMonths.isNotEmpty()) holding.dividendMonths.size else 2
                ((holding.shares * holding.dps * holding.currencyFactor) / monthsCount).toLong()
            } ?: 0L
            AssetManagementUpcomingDividend(
                month = nextMonth,
                name = next?.name,
                dividendType = next?.dividendType,
                shares = next?.shares ?: 0,
                amount = amount
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AssetManagementUpcomingDividend())

    fun assetManagementListItems(sortCriteria: String): Flow<List<AssetManagementListItem>> = combine(
        _holdings, _taxRateMode, _calculationBasis
    ) { current, taxRateMode, calculationBasis ->
            current.map { holding ->
                val sortValue = when (sortCriteria) {
                    "val" -> holding.totalValuation
                    "gain" -> holding.gainLossPercent
                    "div" -> if (calculationBasis == "cost") holding.liveYoc else holding.liveYield
                    "div_amt" -> holding.getEffectiveAnnualDividend(taxRateMode)
                    else -> 0.0
                }
                AssetManagementListItem(
                    id = holding.id,
                    code = holding.code,
                    displayName = japaneseDisplayName(holding.code, holding.name),
                    sector = holding.sector,
                    category = holding.category,
                    dividendMonths = holding.dividendMonths,
                    missingExchangeRate = holding.currency == "$" && holding.usdJpyRate == null,
                    sortValue = sortValue
                )
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun holdingForId(id: String): Flow<Holding?> = holdingsById
        .map { it[id] }
        .distinctUntilChanged()

    fun holdingSnapshot(id: String): Holding? = holdingsById.value[id]
        ?: _holdings.value.firstOrNull { it.id == id }

    fun financialDataFor(code: String): Flow<FinancialData?> {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        return _financialData.map { it[normalized] }.distinctUntilChanged()
    }

    fun financialDataSnapshot(code: String): FinancialData? {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        return _financialData.value[normalized]
    }

    suspend fun awaitRestoration() { restoration.await() }

    private fun updatePortfolio(next: List<Holding>) {
        if (next == _holdings.value) return
        hasRestoredLocalHoldings = true
        val prices = readCachedPrices()
        val dividends = readCachedDividendsPerShare()
        _holdings.value = next.map { runtimeHolding(it, prices, dividends) }
        saveRequests.trySend(Unit)
    }

    private suspend fun persistCurrentHoldings() = saveMutex.withLock {
        val snapshot = _holdings.value
        val watched = _watchlist.value
        withContext(Dispatchers.IO) {
            if (!holdingsLoadFailed) localImportStore.saveHoldings(snapshot)
            if (!watchlistLoadFailed) localImportStore.saveWatchlist(watched)
        }
        // Saving holdings must not erase an unrelated load or notification error.
    }

    fun clearSaveError() { _saveError.value = null }

    fun setShareholderBenefits(code: String, benefits: List<ShareholderBenefit>) {
        val next = _shareholderBenefits.value.toMutableMap().apply {
            if (benefits.isEmpty()) remove(code) else put(code, benefits)
        }
        _shareholderBenefits.value = next
        shareholderBenefitsSaveJob?.cancel()
        shareholderBenefitsSaveJob = viewModelScope.launch {
            delay(300)
            runCatching {
                withContext(Dispatchers.IO) { localImportStore.saveShareholderBenefits(_shareholderBenefits.value) }
            }.onFailure { _saveError.value = it.message ?: getApplication<Application>().getString(R.string.setting_save_failed) }
        }
    }

    fun setStockClimateOverride(code: String, climate: StockClimate?) {
        val next = _stockClimateOverrides.value.toMutableMap().apply {
            if (climate == null) remove(code) else put(code, climate)
        }
        _stockClimateOverrides.value = next
        stockClimateSaveJob?.cancel()
        stockClimateSaveJob = viewModelScope.launch {
            delay(300)
            runCatching {
                withContext(Dispatchers.IO) { localImportStore.saveStockClimateOverrides(_stockClimateOverrides.value) }
            }.onFailure { _saveError.value = it.message ?: getApplication<Application>().getString(R.string.setting_save_failed) }
        }
    }

    fun saveDividendHistoryOverrides(code: String, overrides: List<DividendHistoryOverride>) {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        if (!normalized.matches(Regex("[A-Z0-9]{4}"))) return
        val valid = overrides.distinctBy { it.year }.filter { row ->
            row.year in 1900..2100 && (row.hasStockPriceOverride || row.hasAnnualDividendOverride) &&
                (row.stockPrice == null || (row.stockPrice.isFinite() && row.stockPrice >= 0.0 && PortfolioMath.validMoney(row.stockPrice))) &&
                (row.annualDividend == null || (row.annualDividend.isFinite() && row.annualDividend >= 0.0 && PortfolioMath.validMoney(row.annualDividend)))
        }.sortedBy { it.year }
        _dividendHistoryOverrides.update { current ->
            current.toMutableMap().apply {
                if (valid.isEmpty()) remove(normalized) else put(normalized, valid)
            }
        }
        viewModelScope.launch {
            runCatching {
                dividendHistoryOverrideSaveMutex.withLock {
                    withContext(Dispatchers.IO) {
                        localImportStore.saveDividendHistoryOverrides(_dividendHistoryOverrides.value)
                    }
                }
            }.onFailure { _saveError.value = it.message ?: getApplication<Application>().getString(R.string.setting_save_failed) }
        }
    }

    /** CSV and legacy backup data remain on this device. */
    suspend fun importCsvRows(rows: List<CsvHoldingRow>, fields: Set<String>) {
        restoration.await()
        val industries = withContext(Dispatchers.IO) {
            runCatching { Tse33IndustryMaster.industries(getApplication()) }.getOrDefault(emptyMap())
        }
        val next = PortfolioImporter.importRows(_holdings.value, rows, fields, industries)
        holdingsLoadFailed = false
        updatePortfolio(next)
        applyCachedDividendMonths()
        refreshRuntimeValues()
        persistCurrentHoldings()
    }

    suspend fun importDvmBackups(backups: List<DvmBackupPreview>) {
        restoration.await()
        val stocks = backups.filter { it.kind == DvmBackupKind.STOCKS }.flatMap { it.stocks }
        val next = PortfolioImporter.importStocks(_holdings.value, stocks).map {
            it.copy(dividendMonths = emptyList()).refreshPayout()
        }
        val extras = backups.map { it.extras }
        val nextBenefits = _shareholderBenefits.value.toMutableMap().apply { extras.forEach { putAll(it.shareholderBenefits) } }
        val nextClimates = _stockClimateOverrides.value.toMutableMap().apply { extras.forEach { putAll(it.stockClimateOverrides) } }
        val nextDividendHistory = _dividendHistoryOverrides.value.toMutableMap().apply { extras.forEach { putAll(it.dividendHistoryOverrides) } }
        withContext(Dispatchers.IO) {
            backups.forEach { preview -> localImportStore.saveBackup(preview.kind, preview.payload) }
            localImportStore.saveShareholderBenefits(nextBenefits)
            localImportStore.saveStockClimateOverrides(nextClimates)
            localImportStore.saveDividendHistoryOverrides(nextDividendHistory)
        }
        _shareholderBenefits.value = nextBenefits
        _stockClimateOverrides.value = nextClimates
        _dividendHistoryOverrides.value = nextDividendHistory
        holdingsLoadFailed = false
        updatePortfolio(next)
        applyCachedDividendMonths()
        refreshRuntimeValues()
        persistCurrentHoldings()
    }

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
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 輸送用機器",
                alertEnabled = false,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "2914",
                name = "日本たばこ産業 (JT)",
                market = "東証PRM",
                sector = "食料品 · 日本株",
                category = "jp",
                price = 4100.0,
                dps = 194.0,
                currentYield = 4.73,
                targetYield = 4.80,
                targetPrice = 4041.0,
                dailyChange = "+45 (+1.11%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月下旬",
                desc = "東証PRM · 高配当ディフェンシブ",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "8306",
                name = "三菱UFJフィナンシャルG",
                market = "東証PRM",
                sector = "銀行業 · 日本株",
                category = "jp",
                price = 1540.0,
                dps = 50.0,
                currentYield = 3.25,
                targetYield = 3.50,
                targetPrice = 1428.0,
                dailyChange = "+12 (+0.78%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · メガバンク",
                alertEnabled = true,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "8316",
                name = "三井住友フィナンシャルG",
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
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 銀行業",
                alertEnabled = true,
                badge = "絶好の買場到達 (利回り4.13%)"
            ),
            WatchlistStock(
                code = "8411",
                name = "みずほフィナンシャルG",
                market = "東証PRM",
                sector = "銀行業 · 日本株",
                category = "jp",
                price = 2850.0,
                dps = 115.0,
                currentYield = 4.03,
                targetYield = 4.20,
                targetPrice = 2738.0,
                dailyChange = "+14 (+0.49%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 銀行業",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "8058",
                name = "三菱商事",
                market = "東証PRM",
                sector = "卸売業 · 日本株",
                category = "jp",
                price = 3020.0,
                dps = 100.0,
                currentYield = 3.31,
                targetYield = 3.50,
                targetPrice = 2857.0,
                dailyChange = "+34 (+1.14%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 総合商社",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "8031",
                name = "三井物産",
                market = "東証PRM",
                sector = "卸売業 · 日本株",
                category = "jp",
                price = 3200.0,
                dps = 105.0,
                currentYield = 3.28,
                targetYield = 3.40,
                targetPrice = 3088.0,
                dailyChange = "+40 (+1.27%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 総合商社",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "8766",
                name = "東京海上ホールディングス",
                market = "東証PRM",
                sector = "保険業 · 日本株",
                category = "jp",
                price = 3620.0,
                dps = 126.0,
                currentYield = 3.48,
                targetYield = 3.60,
                targetPrice = 3500.0,
                dailyChange = "+20 (+0.56%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 損害保険",
                alertEnabled = true,
                badge = "買場目安近接"
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
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月上旬",
                desc = "東証PRM · 情報・通信業",
                alertEnabled = true,
                badge = "ディフェンシブ"
            ),
            WatchlistStock(
                code = "9433",
                name = "KDDI",
                market = "東証PRM",
                sector = "情報・通信業 · 日本株",
                category = "jp",
                price = 4500.0,
                dps = 145.0,
                currentYield = 3.22,
                targetYield = 3.40,
                targetPrice = 4264.0,
                dailyChange = "+15 (+0.33%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 通信キャリア",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "9434",
                name = "ソフトバンク",
                market = "東証PRM",
                sector = "情報・通信業 · 日本株",
                category = "jp",
                price = 195.0,
                dps = 8.6,
                currentYield = 4.41,
                targetYield = 4.50,
                targetPrice = 191.0,
                dailyChange = "+1.5 (+0.78%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 通信キャリア",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "1605",
                name = "INPEX",
                market = "東証PRM",
                sector = "鉱業 · 日本株",
                category = "jp",
                price = 2010.0,
                dps = 86.0,
                currentYield = 4.28,
                targetYield = 4.50,
                targetPrice = 1911.0,
                dailyChange = "+32 (+1.62%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 資源開発最大手",
                alertEnabled = false,
                badge = "超高配当"
            ),
            WatchlistStock(
                code = "1928",
                name = "積水ハウス",
                market = "東証PRM",
                sector = "建設業 · 日本株",
                category = "jp",
                price = 3340.0,
                dps = 125.0,
                currentYield = 3.74,
                targetYield = 3.80,
                targetPrice = 3289.0,
                dailyChange = "+10 (+0.30%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "1月下旬 / 7月下旬",
                desc = "東証PRM · ハウスメーカー大手",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "1925",
                name = "大和ハウス工業",
                market = "東証PRM",
                sector = "建設業 · 日本株",
                category = "jp",
                price = 4120.0,
                dps = 145.0,
                currentYield = 3.52,
                targetYield = 3.60,
                targetPrice = 4027.0,
                dailyChange = "-15 (-0.36%)",
                isPositive = false,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 住宅・不動産開発",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "2503",
                name = "キリンホールディングス",
                market = "東証PRM",
                sector = "食料品 · 日本株",
                category = "jp",
                price = 2180.0,
                dps = 71.0,
                currentYield = 3.26,
                targetYield = 3.40,
                targetPrice = 2088.0,
                dailyChange = "+5 (+0.23%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 飲料最大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "2502",
                name = "アサヒグループHD",
                market = "東証PRM",
                sector = "食料品 · 日本株",
                category = "jp",
                price = 5410.0,
                dps = 125.0,
                currentYield = 2.31,
                targetYield = 2.50,
                targetPrice = 5000.0,
                dailyChange = "+20 (+0.37%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 飲料・ビール大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "4502",
                name = "武田薬品工業",
                market = "東証PRM",
                sector = "医薬品 · 日本株",
                category = "jp",
                price = 4300.0,
                dps = 188.0,
                currentYield = 4.37,
                targetYield = 4.40,
                targetPrice = 4272.0,
                dailyChange = "+10 (+0.23%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 製薬国内首位",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "4503",
                name = "アステラス製薬",
                market = "東証PRM",
                sector = "医薬品 · 日本株",
                category = "jp",
                price = 1720.0,
                dps = 74.0,
                currentYield = 4.30,
                targetYield = 4.50,
                targetPrice = 1644.0,
                dailyChange = "-12 (-0.69%)",
                isPositive = false,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 医療用医薬品首位級",
                alertEnabled = false,
                badge = "超高配当"
            ),
            WatchlistStock(
                code = "7751",
                name = "キヤノン",
                market = "東証PRM",
                sector = "電気機器 · 日本株",
                category = "jp",
                price = 3820.0,
                dps = 150.0,
                currentYield = 3.93,
                targetYield = 4.00,
                targetPrice = 3750.0,
                dailyChange = "+45 (+1.19%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 精密機器・カメラ",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "7267",
                name = "本田技研工業 (ホンダ)",
                market = "東証PRM",
                sector = "輸送用機器 · 日本株",
                category = "jp",
                price = 1680.0,
                dps = 68.0,
                currentYield = 4.05,
                targetYield = 4.20,
                targetPrice = 1619.0,
                dailyChange = "+18 (+1.08%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 二輪車世界首位級",
                alertEnabled = false,
                badge = "超高配当"
            ),
            WatchlistStock(
                code = "5108",
                name = "ブリヂストン",
                market = "東証PRM",
                sector = "ゴム製品 · 日本株",
                category = "jp",
                price = 5850.0,
                dps = 210.0,
                currentYield = 3.59,
                targetYield = 3.70,
                targetPrice = 5675.0,
                dailyChange = "+20 (+0.34%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · タイヤ世界首位級",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "5401",
                name = "日本製鉄",
                market = "東証PRM",
                sector = "鉄鋼 · 日本株",
                category = "jp",
                price = 3200.0,
                dps = 160.0,
                currentYield = 5.00,
                targetYield = 5.20,
                targetPrice = 3076.0,
                dailyChange = "-32 (-0.99%)",
                isPositive = false,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 鉄鋼国内最大手",
                alertEnabled = true,
                badge = "超高配当 (利回り5.00%)"
            ),
            WatchlistStock(
                code = "6301",
                name = "小松製作所 (コマツ)",
                market = "東証PRM",
                sector = "機械 · 日本株",
                category = "jp",
                price = 4230.0,
                dps = 167.0,
                currentYield = 3.95,
                targetYield = 4.00,
                targetPrice = 4175.0,
                dailyChange = "+34 (+0.81%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 建機世界2位",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "6326",
                name = "クボタ",
                market = "東証PRM",
                sector = "機械 · 日本株",
                category = "jp",
                price = 2120.0,
                dps = 48.0,
                currentYield = 2.26,
                targetYield = 2.50,
                targetPrice = 1920.0,
                dailyChange = "+5 (+0.24%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 農機国内首位",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "6501",
                name = "日立製作所",
                market = "東証PRM",
                sector = "電気機器 · 日本株",
                category = "jp",
                price = 3650.0,
                dps = 80.0,
                currentYield = 2.19,
                targetYield = 2.30,
                targetPrice = 3478.0,
                dailyChange = "+55 (+1.53%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 総合電気最大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "6758",
                name = "ソニーグループ",
                market = "東証PRM",
                sector = "電気機器 · 日本株",
                category = "jp",
                price = 2850.0,
                dps = 85.0,
                currentYield = 2.98,
                targetYield = 3.00,
                targetPrice = 2833.0,
                dailyChange = "+12 (+0.42%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · ゲーム、映画、音楽",
                alertEnabled = true,
                badge = "買場目安近接"
            ),
            WatchlistStock(
                code = "6752",
                name = "パナソニックHD",
                market = "東証PRM",
                sector = "電気機器 · 日本株",
                category = "jp",
                price = 1320.0,
                dps = 35.0,
                currentYield = 2.65,
                targetYield = 2.80,
                targetPrice = 1250.0,
                dailyChange = "+2 (+0.15%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 総合家電大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "6902",
                name = "デンソー",
                market = "東証PRM",
                sector = "輸送用機器 · 日本株",
                category = "jp",
                price = 2240.0,
                dps = 52.0,
                currentYield = 2.32,
                targetYield = 2.50,
                targetPrice = 2080.0,
                dailyChange = "+12 (+0.54%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 自動車部品最大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "6981",
                name = "村田製作所",
                market = "東証PRM",
                sector = "電気機器 · 日本株",
                category = "jp",
                price = 2820.0,
                dps = 54.0,
                currentYield = 1.91,
                targetYield = 2.00,
                targetPrice = 2700.0,
                dailyChange = "+10 (+0.36%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 電子部品大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "4452",
                name = "花王",
                market = "東証PRM",
                sector = "化学 · 日本株",
                category = "jp",
                price = 5800.0,
                dps = 152.0,
                currentYield = 2.62,
                targetYield = 2.80,
                targetPrice = 5428.0,
                dailyChange = "+20 (+0.17%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 34期連続増配企業",
                alertEnabled = true,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "4063",
                name = "信越化学工業",
                market = "東証PRM",
                sector = "化学 · 日本株",
                category = "jp",
                price = 6120.0,
                dps = 104.0,
                currentYield = 1.70,
                targetYield = 1.80,
                targetPrice = 5777.0,
                dailyChange = "+45 (+0.74%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 塩ビ・ウエハ世界一",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "8267",
                name = "イオン",
                market = "東証PRM",
                sector = "小売業 · 日本株",
                category = "jp",
                price = 3410.0,
                dps = 40.0,
                currentYield = 1.17,
                targetYield = 1.30,
                targetPrice = 3076.0,
                dailyChange = "-10 (-0.29%)",
                isPositive = false,
                payoutMonths = emptyList(),
                payoutTiming = "2月下旬 / 8月下旬",
                desc = "東証PRM · 小売国内最大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "7974",
                name = "任天堂",
                market = "東証PRM",
                sector = "その他製品 · 日本株",
                category = "jp",
                price = 8120.0,
                dps = 186.0,
                currentYield = 2.29,
                targetYield = 2.50,
                targetPrice = 7440.0,
                dailyChange = "+150 (+1.88%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · ゲーム専用機世界首位",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "8801",
                name = "三井不動産",
                market = "東証PRM",
                sector = "不動産業 · 日本株",
                category = "jp",
                price = 1450.0,
                dps = 34.0,
                currentYield = 2.34,
                targetYield = 2.50,
                targetPrice = 1360.0,
                dailyChange = "+12 (+0.83%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 不動産総合デベロッパー",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "9020",
                name = "東日本旅客鉄道 (JR東日本)",
                market = "東証PRM",
                sector = "陸運業 · 日本株",
                category = "jp",
                price = 2850.0,
                dps = 60.0,
                currentYield = 2.11,
                targetYield = 2.30,
                targetPrice = 2608.0,
                dailyChange = "+10 (+0.35%)",
                isPositive = true,
                payoutMonths = emptyList(),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 鉄道・生活サービス最大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
            ),
            WatchlistStock(
                code = "AAPL",
                name = "アップル",
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
                payoutMonths = emptyList(),
                payoutTiming = "各月中旬 (四半期)",
                desc = "NASDAQ · US Tech · 米国株",
                alertEnabled = false,
                badge = "自社株買い+連続増配"
            )
        )
    )
    val watchlist: StateFlow<List<WatchlistStock>> = _watchlist.asStateFlow()

    fun watchlistProjectionFor(stocks: List<WatchlistStock>) = watchlistProjectionCache.get(stocks)

    suspend fun searchCloudflareSecurities(query: String): List<CloudflareSearchHit> {
        val hits = marketRepository.searchSecurities(query)
        val industries = withContext(Dispatchers.IO) { runCatching { Tse33IndustryMaster.industries(getApplication()) }.getOrDefault(emptyMap()) }
        return hits.map { hit ->
            hit.copy(
                name = japaneseDisplayName(hit.code, hit.name),
                industry = industries[hit.code.take(4).uppercase(java.util.Locale.ROOT)]
            )
        }
    }

    suspend fun loadCloudflareSearchQuotes(codes: List<String>): Map<String, CloudflareSearchQuote> =
        marketRepository.fetchSearchQuotes(codes)

    suspend fun registerCloudflareHolding(
        code: String,
        shares: Int,
        averageAcquisitionPrice: Double,
        accountType: String
    ): Boolean {
        restoration.await()
        if (shares <= 0 || !PortfolioMath.validMoney(averageAcquisitionPrice)) return false
        val security = runCatchingCancellable { marketRepository.getSecurity(code) }.getOrNull() ?: return false
        val added = addOrUpdateHolding(code, shares, averageAcquisitionPrice, accountType, security)
        if (added) persistCurrentHoldings()
        return added
    }

    suspend fun registerCloudflareAllocations(code: String, allocations: List<HoldingAllocation>, replaceExisting: Boolean = false): Boolean {
        restoration.await()
        val security = resolveCloudflareSecurity(code) ?: return false
        return registerResolvedAllocations(code, allocations, security, replaceExisting)
    }

    suspend fun registerResolvedAllocations(code: String, allocations: List<HoldingAllocation>, security: CloudflareSecurity?, replaceExisting: Boolean = false): Boolean {
        restoration.await()
        if (security != null && !security.code.equals(code, true)) return false
        val registered = saveMutex.withLock {
            // Publish only after a durable save. Once writing starts, finish the transaction even
            // if the sheet closes; otherwise a retry could add the same shares twice.
            withContext(kotlinx.coroutines.NonCancellable) {
                var saved: Holding? = null
                while (saved == null) {
                    val before = _holdings.value
                    val merged = allocatedHolding(code, allocations, security, before, replaceExisting) ?: return@withContext null
                    val updated = if (before.any { it.id == merged.id }) before.map { if (it.id == merged.id) merged else it } else before + merged
                    val prices = readCachedPrices()
                    val dividends = readCachedDividendsPerShare()
                    val next = updated.map { runtimeHolding(it, prices, dividends) }
                    withContext(Dispatchers.IO) { localImportStore.saveHoldings(next) }
                    // If a price refresh or another edit happened during IO, merge onto that
                    // newer snapshot and save it before publishing.
                    if (_holdings.compareAndSet(before, next)) saved = merged
                }
                saved
            }
        } ?: return false
        hasRestoredLocalHoldings = true
        refreshAllocationMonths(registered)
        viewModelScope.launch {
            refreshMarketPricesIfDue()
            runCatchingCancellable { fetchFinancialData(registered.code, force = true) }
            runCatchingCancellable { fetchStockAnalysis(registered.code, force = true) }
        }
        return true
    }

    private fun loadCachedCloudflareSecurityData(): Map<String, CloudflareSecurity> =
        marketPreferences.all.entries.asSequence()
            .filter { it.key.startsWith("cloudflare_security_snapshot_") }
            .mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                val security = parseCloudflareSecuritySnapshot(raw) ?: return@mapNotNull null
                key.removePrefix("cloudflare_security_snapshot_") to security
            }.toMap()

    private fun parseCloudflareSecuritySnapshot(raw: String): CloudflareSecurity? = runCatching {
        val json = JSONObject(raw)
        fun JSONObject.stringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
        fun JSONObject.doubleOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)
        val retrievalJson = json.optJSONObject("data_retrieval")
        val historyJson = retrievalJson?.optJSONObject("dividend_history")
        val retrieval = retrievalJson?.let {
            CloudflareDataRetrieval(
                yfinanceTicker = it.stringOrNull("yfinance_ticker"),
                source = it.stringOrNull("source"),
                currency = it.stringOrNull("currency"),
                lastAttemptAt = it.stringOrNull("last_attempt_at"),
                processingStatus = it.stringOrNull("processing_status"),
                failureReason = it.stringOrNull("failure_reason"),
                dividendHistory = historyJson?.let { history ->
                    CloudflareDividendHistoryRetrieval(
                        status = history.stringOrNull("status"),
                        lastAttemptStatus = history.stringOrNull("last_attempt_status"),
                        source = history.stringOrNull("source"),
                        storedHistoryStatus = history.stringOrNull("stored_history_status"),
                        storedHistoryStart = history.stringOrNull("stored_history_start"),
                        storedHistoryEnd = history.stringOrNull("stored_history_end"),
                        lastCoverageStart = history.stringOrNull("last_coverage_start"),
                        lastCoverageEnd = history.stringOrNull("last_coverage_end")
                    )
                }
            )
        }
        CloudflareSecurity(
            code = json.getString("code"),
            name = json.getString("name"),
            annualDividendPerShare = json.doubleOrNull("annual_dividend_per_share"),
            marketPrice = json.doubleOrNull("market_price"),
            industry = json.stringOrNull("industry"),
            marketYieldRate = json.doubleOrNull("market_yield_rate"),
            isNoDividendConfirmed = json.optBoolean("is_no_dividend_confirmed"),
            hasDividendStatus = json.optBoolean("has_dividend_status"),
            dataRetrieval = retrieval
        )
    }.getOrNull()

    private fun cacheCloudflareSecuritySnapshot(security: CloudflareSecurity) {
        val normalized = PortfolioMath.normalizeCode(security.code).removeSuffix(".T")
        val history = security.dataRetrieval?.dividendHistory?.let { value ->
            JSONObject().apply {
                put("status", value.status ?: JSONObject.NULL)
                put("last_attempt_status", value.lastAttemptStatus ?: JSONObject.NULL)
                put("source", value.source ?: JSONObject.NULL)
                put("stored_history_status", value.storedHistoryStatus ?: JSONObject.NULL)
                put("stored_history_start", value.storedHistoryStart ?: JSONObject.NULL)
                put("stored_history_end", value.storedHistoryEnd ?: JSONObject.NULL)
                put("last_coverage_start", value.lastCoverageStart ?: JSONObject.NULL)
                put("last_coverage_end", value.lastCoverageEnd ?: JSONObject.NULL)
            }
        }
        val retrieval = security.dataRetrieval?.let { value ->
            JSONObject().apply {
                put("yfinance_ticker", value.yfinanceTicker ?: JSONObject.NULL)
                put("source", value.source ?: JSONObject.NULL)
                put("currency", value.currency ?: JSONObject.NULL)
                put("last_attempt_at", value.lastAttemptAt ?: JSONObject.NULL)
                put("processing_status", value.processingStatus ?: JSONObject.NULL)
                put("failure_reason", value.failureReason ?: JSONObject.NULL)
                put("dividend_history", history ?: JSONObject.NULL)
            }
        }
        val json = JSONObject().apply {
            put("code", security.code)
            put("name", security.name)
            put("annual_dividend_per_share", security.annualDividendPerShare ?: JSONObject.NULL)
            put("market_price", security.marketPrice ?: JSONObject.NULL)
            put("industry", security.industry ?: JSONObject.NULL)
            put("market_yield_rate", security.marketYieldRate ?: JSONObject.NULL)
            put("is_no_dividend_confirmed", security.isNoDividendConfirmed)
            put("has_dividend_status", security.hasDividendStatus)
            put("data_retrieval", retrieval ?: JSONObject.NULL)
        }
        marketPreferences.edit()
            .putString("cloudflare_security_snapshot_$normalized", json.toString())
            .putString("cloudflare_security_fetched_jst_$normalized", PriceRefreshPolicy.japanDate(System.currentTimeMillis()))
            .apply()
        _cloudflareSecurityData.update { it + (normalized to security) }
    }

    private suspend fun fetchCloudflareSecurityIfDue(code: String): CloudflareSecurity? {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        if (!normalized.matches(Regex("[A-Z0-9.\\-]{1,16}"))) return null
        val cacheKey = "cloudflare_security_snapshot_$normalized"
        val attemptKey = "cloudflare_security_fetch_attempt_at_$normalized"
        val attemptDateKey = "cloudflare_security_fetch_attempt_jst_$normalized"
        var cached: CloudflareSecurity? = null
        val shouldFetch = cloudflareSecurityMutex.withLock {
            cached = _cloudflareSecurityData.value[normalized]
                ?: marketPreferences.getString(cacheKey, null)?.let(::parseCloudflareSecuritySnapshot)
            val now = System.currentTimeMillis()
            if (!PriceRefreshPolicy.shouldFetch(
                    now,
                    marketPreferences.getLong(attemptKey, 0L),
                    marketPreferences.getString(attemptDateKey, null)
                        ?: marketPreferences.getString("cloudflare_security_fetched_jst_$normalized", null)
                ) || !cloudflareSecurityLoading.add(normalized)) {
                false
            } else {
                marketPreferences.edit().putLong(attemptKey, now)
                    .putString(attemptDateKey, PriceRefreshPolicy.japanDate(now)).apply()
                true
            }
        }
        if (!shouldFetch) return cached
        try {
            val fetched = runCatchingCancellable {
                cloudflareSecuritySlots.withPermit { marketRepository.getSecurity(normalized) }
            }.onFailure {
                android.util.Log.w("CloudflareMarket", "銘柄情報の取得に失敗しました: $normalized", it)
            }.getOrNull() ?: return cached
            val enriched = withContext(Dispatchers.IO) {
                fetched.copy(
                    name = japaneseDisplayName(fetched.code, fetched.name),
                    industry = runCatching {
                        Tse33IndustryMaster.industries(getApplication())[fetched.code.take(4).uppercase(java.util.Locale.ROOT)]
                    }.getOrNull()
                )
            }
            cacheCloudflareSecuritySnapshot(enriched)
            return enriched
        } finally {
            cloudflareSecurityMutex.withLock { cloudflareSecurityLoading.remove(normalized) }
        }
    }

    fun cachedCloudflareSecurity(code: String): CloudflareSecurity? {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        return _cloudflareSecurityData.value[normalized]
            ?: marketPreferences.getString("cloudflare_security_snapshot_$normalized", null)
                ?.let(::parseCloudflareSecuritySnapshot)
    }

    suspend fun resolveCloudflareSecurity(code: String): CloudflareSecurity? {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        val security = cachedCloudflareSecurity(normalized) ?: fetchCloudflareSecurityIfDue(normalized) ?: return null
        if ((security.annualDividendPerShare ?: 0.0) <= 0.0) {
            runCatchingCancellable { fetchFinancialData(security.code) }
        }
        return security
    }

    suspend fun fetchStockAnalysis(code: String, force: Boolean = false): CloudflareStockAnalysis? {
        val normalized = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        if (!normalized.matches(Regex("[A-Z0-9]{4}"))) return null
        _stockAnalysisData.value[normalized]?.let { if (!force) return it }
        val now = System.currentTimeMillis()
        val attemptKey = "stock_analysis_fetch_attempt_at_$normalized"
        val attemptDateKey = "stock_analysis_fetch_attempt_jst_$normalized"
        if (!PriceRefreshPolicy.shouldFetch(
                now,
                marketPreferences.getLong(attemptKey, 0L),
                marketPreferences.getString(attemptDateKey, null)
            )) {
            val cached = runCatching {
                marketPreferences.getString("stock_analysis_$normalized", null)?.let { raw ->
                    val json = JSONObject(raw)
                    val values = json.keys().asSequence().associateWith { key ->
                        if (json.isNull(key)) null else json.optDouble(key).takeIf(Double::isFinite)
                    }
                    CloudflareStockAnalysis(values)
                }
            }.getOrNull()
            if (cached != null) _stockAnalysisData.update { it + (normalized to cached) }
            return cached ?: _stockAnalysisData.value[normalized]
        }
        marketPreferences.edit().putLong(attemptKey, now)
            .putString(attemptDateKey, PriceRefreshPolicy.japanDate(now)).apply()
        val allowed = stockAnalysisMutex.withLock { stockAnalysisLoading.add(normalized) }
        if (!allowed) return _stockAnalysisData.value[normalized]
        return try {
            val result = stockAnalysisSlots.withPermit { marketRepository.getStockAnalysis(normalized) }
            if (result != null) {
                _stockAnalysisData.update { it + (normalized to result) }
                val json = JSONObject().apply {
                    result.values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
                }
                marketPreferences.edit().putString("stock_analysis_$normalized", json.toString()).apply()
            }
            result
        } finally {
            stockAnalysisMutex.withLock { stockAnalysisLoading.remove(normalized) }
        }
    }

    fun cachedMonthlyPriceHistory(codes: List<String>, months: List<String>): List<CloudflareMonthlyPriceSeries> {
        val requestedMonths = months.toSet()
        return codes.distinct().mapNotNull { code ->
            readCachedMonthlyPrices(code).values
                .filter { it.month in requestedMonths }
                .takeIf { it.isNotEmpty() }
                ?.let { prices -> CloudflareMonthlyPriceSeries(code, prices.sortedBy { it.month }) }
        }
    }

    suspend fun loadShareSplitHistory(code: String, from: LocalDate, to: LocalDate, force: Boolean = false): CloudflareShareSplitHistory =
        shareSplitRefreshMutex.withLock {
        val normalizedCode = PortfolioMath.normalizeCode(code).removeSuffix(".T")
        val cacheKey = "share_split_history_${normalizedCode}_${from}_$to"
        val attemptKey = "share_split_fetch_attempt_at_${normalizedCode}_${from}_$to"
        val attemptDateKey = "share_split_fetch_attempt_jst_${normalizedCode}_${from}_$to"
        fun readCached(): CloudflareShareSplitHistory? = runCatching {
            val cached = marketPreferences.getString(cacheKey, null)?.let(::JSONObject) ?: return@runCatching null
            val rows = cached.optJSONArray("events")
            val events = (0 until (rows?.length() ?: 0)).mapNotNull { index ->
                val row = rows?.optJSONObject(index) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(row.optString("date")) }.getOrNull()
                    ?: return@mapNotNull null
                val ratio = row.optDouble("ratio", Double.NaN)
                if (!ratio.isFinite() || ratio <= 0.0) return@mapNotNull null
                CloudflareShareSplitEvent(date, ratio)
            }
            CloudflareShareSplitHistory(
                code = normalizedCode,
                historyStatus = cached.optString("history_status", "not_fetched"),
                events = events
            )
        }.getOrNull()

        val now = System.currentTimeMillis()
        val lastAttemptDate = marketPreferences.getString(attemptDateKey, null)
        if (!force && !PriceRefreshPolicy.shouldFetch(
                now,
                marketPreferences.getLong(attemptKey, 0L),
                lastAttemptDate
            )) {
            return@withLock readCached()
                ?: throw java.io.IOException("株式分割履歴を取得できませんでした")
        }
        marketPreferences.edit().putLong(attemptKey, now)
            .putString(attemptDateKey, PriceRefreshPolicy.japanDate(now)).apply()
        val fetched = marketRepository.fetchShareSplitHistory(normalizedCode, from, to)
        val rows = JSONArray().apply {
            fetched.events.forEach { event ->
                put(JSONObject().put("date", event.date.toString()).put("ratio", event.ratio))
            }
        }
        marketPreferences.edit().putString(cacheKey, JSONObject()
            .put("history_status", fetched.historyStatus)
            .put("events", rows).toString()).apply()
        fetched
    }

    suspend fun loadMonthlyPriceHistory(codes: List<String>, months: List<String>): List<CloudflareMonthlyPriceSeries> =
        monthlyPriceRefreshMutex.withLock {
        val normalizedCodes = codes.map { code ->
            code.trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T").let {
                if (it.length == 5 && it.endsWith('0')) it.dropLast(1) else it
            }
        }.filter { it.matches(Regex("[A-Z0-9]{4}")) }.distinct()
        val requestedMonths = months.distinct().filter { it.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")) }
        if (normalizedCodes.isEmpty() || requestedMonths.isEmpty()) return@withLock emptyList()

        val cachedByCode = normalizedCodes.associateWith { readCachedMonthlyPrices(it) }
        val now = System.currentTimeMillis()
        val missingByCode = normalizedCodes.associateWith { code ->
            requestedMonths.filter { month ->
                if (month in cachedByCode[code].orEmpty()) return@filter false
                val key = "monthly_price_fetch_attempt_at_${code}_$month"
                val dateKey = "monthly_price_fetch_attempt_jst_${code}_$month"
                PriceRefreshPolicy.shouldFetch(
                    now,
                    marketPreferences.getLong(key, 0L),
                    marketPreferences.getString(dateKey, null)
                )
            }
        }
        for (codeBatch in normalizedCodes.chunked(20)) {
            for (monthBatch in requestedMonths.chunked(12)) {
                val codesForBatch = codeBatch.filter { code ->
                    missingByCode[code].orEmpty().any { it in monthBatch }
                }
                if (codesForBatch.isNotEmpty()) {
                    val attemptEditor = marketPreferences.edit()
                    codesForBatch.forEach { code ->
                        monthBatch.filter { it in missingByCode[code].orEmpty() }.forEach { month ->
                            attemptEditor.putLong("monthly_price_fetch_attempt_at_${code}_$month", now)
                                .putString("monthly_price_fetch_attempt_jst_${code}_$month", PriceRefreshPolicy.japanDate(now))
                        }
                    }
                    attemptEditor.apply()
                    try {
                        marketRepository.fetchMonthlyPriceHistory(codesForBatch, monthBatch).forEach { series ->
                            val merged = readCachedMonthlyPrices(series.code).toMutableMap()
                            series.prices.forEach { price -> merged[price.month] = price }
                            if (merged.isNotEmpty()) saveCachedMonthlyPrices(series.code, merged)
                        }
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    }
                }
            }
        }
        cachedMonthlyPriceHistory(normalizedCodes, requestedMonths)
    }

    private fun readCachedMonthlyPrices(code: String): Map<String, com.kurogane.dividend.data.CloudflareMonthlyPrice> = runCatching {
        val rows = JSONArray(marketPreferences.getString("monthly_prices_v2_$code", "[]") ?: "[]")
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val month = row.optString("month")
            val date = row.optString("date")
            val close = row.optDouble("close", Double.NaN)
            if (month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")) && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && close.isFinite() && close > 0.0)
                com.kurogane.dividend.data.CloudflareMonthlyPrice(month, date, close) else null
        }.associateBy { it.month }
    }.getOrDefault(emptyMap())

    private fun saveCachedMonthlyPrices(code: String, prices: Map<String, com.kurogane.dividend.data.CloudflareMonthlyPrice>) {
        val merged = readCachedMonthlyPrices(code).toMutableMap().apply { putAll(prices) }
        val current = readCachedMonthlyPrices(code)
        if (merged == current) return
        val rows = JSONArray().apply {
            merged.values.sortedBy { it.month }.forEach { put(JSONObject().put("month", it.month).put("date", it.date).put("close", it.close)) }
        }
        marketPreferences.edit().putString("monthly_prices_v2_$code", rows.toString()).apply()
    }

    private suspend fun refreshMarketPricesIfDue() {
        val generation = dataGeneration.get()
        val now = System.currentTimeMillis()
        val lastFetchAt = marketPreferences.getLong(
            "last_price_fetch_attempt_at",
            marketPreferences.getLong("last_price_fetch_at", 0L)
        )
        val lastFetchJstDate = marketPreferences.getString("last_price_fetch_attempt_jst_date", null)
            ?: marketPreferences.getString("last_price_fetch_jst_date", null)
        val shouldFetchPrices = PriceRefreshPolicy.shouldFetch(now, lastFetchAt, lastFetchJstDate)
        val needsAnnualDpsRefresh = marketPreferences.getInt("annual_dps_contract_version", 0) < ANNUAL_DPS_CONTRACT_VERSION
        val dpsAttemptKey = "annual_dps_contract_refresh_attempt_at"
        val dpsAttemptDateKey = "annual_dps_contract_refresh_attempt_jst"
        val shouldRefreshAnnualDps = needsAnnualDpsRefresh && PriceRefreshPolicy.shouldFetch(
            now,
            marketPreferences.getLong(dpsAttemptKey, 0L),
            marketPreferences.getString(dpsAttemptDateKey, null)
        )
        if (!shouldFetchPrices && !shouldRefreshAnnualDps) return
        if (shouldRefreshAnnualDps) {
            marketPreferences.edit()
                .putLong(dpsAttemptKey, now)
                .putString(dpsAttemptDateKey, PriceRefreshPolicy.japanDate(now))
                .apply()
        }

        val heldCodes = _holdings.value
            .filter { it.category == Category.JAPAN && it.shares > 0 }
            .map { normalizedSecurityCode(it.code) }
            .distinct()
        val heldQuotes = runCatchingCancellable {
            heldCodes.chunked(50).flatMap { codes -> marketRepository.fetchSearchQuotes(codes).entries }
                .associate { it.toPair() }
        }.getOrElse { error ->
            android.util.Log.w("CloudflareMarket", "保有銘柄の年間配当データ取得に失敗しました", error)
            emptyMap()
        }
        val heldDpsCoverageComplete = heldCodes.all { code ->
            heldQuotes[code]?.let { it.annualDividendPerShare != null || it.isNoDividendConfirmed } == true
        }
        if (heldQuotes.isNotEmpty()) {
            val cachedDividendsPerShare = readCachedDividendsPerShare().toMutableMap()
            val cachedNoDividendCodes = readCachedNoDividendCodes().toMutableSet()
            val cachedDividendStatusCodes = readCachedDividendStatusCodes().toMutableSet()
            heldQuotes.forEach { (code, quote) ->
                when {
                    quote.annualDividendPerShare != null -> cachedDividendsPerShare[code] = quote.annualDividendPerShare
                    quote.isNoDividendConfirmed -> cachedDividendsPerShare[code] = 0.0
                }
                if (quote.hasDividendStatus) {
                    cachedDividendStatusCodes += code
                    if (quote.isNoDividendConfirmed) cachedNoDividendCodes += code
                    else cachedNoDividendCodes -= code
                }
            }
            marketPreferences.edit()
                .putString("dividend_per_share", JSONObject().apply {
                    cachedDividendsPerShare.forEach { (code, dps) -> put(code, dps) }
                }.toString())
                .putString("confirmed_no_dividend_codes", JSONArray().apply {
                    cachedNoDividendCodes.sorted().forEach { put(it) }
                }.toString())
                .putString("known_dividend_status_codes", JSONArray().apply {
                    cachedDividendStatusCodes.sorted().forEach { put(it) }
                }.toString())
                .apply()
            applyPrices(readCachedPrices(), cachedDividendsPerShare, cachedNoDividendCodes, cachedDividendStatusCodes)
            saveRequests.trySend(Unit)
        }
        if (heldDpsCoverageComplete) {
            marketPreferences.edit().putInt("annual_dps_contract_version", ANNUAL_DPS_CONTRACT_VERSION).apply()
        }
        if (!shouldFetchPrices) return
        marketPreferences.edit()
            .putLong("last_price_fetch_attempt_at", now)
            .putString("last_price_fetch_attempt_jst_date", PriceRefreshPolicy.japanDate(now))
            .putString("last_price_fetch_status", "running")
            .apply()

        runCatchingCancellable { marketRepository.fetchPriceCatalogSnapshot() }
            .onSuccess { snapshot ->
                if (generation != dataGeneration.get()) {
                    marketPreferences.edit().putString("last_price_fetch_status", "superseded").apply()
                    return@onSuccess
                }
                val fetchedQuotes = snapshot.quotes
                val fetchedPrices = fetchedQuotes.mapValues { it.value.price }
                val cachedDividendsPerShare = readCachedDividendsPerShare().toMutableMap()
                val cachedNoDividendCodes = readCachedNoDividendCodes().toMutableSet()
                val cachedDividendStatusCodes = readCachedDividendStatusCodes().toMutableSet()
                fetchedQuotes.forEach { (code, quote) ->
                    if (quote.annualDividendPerShare != null) cachedDividendsPerShare[code] = quote.annualDividendPerShare
                    else if (quote.isNoDividendConfirmed) cachedDividendsPerShare[code] = 0.0
                    if (quote.hasDividendStatus) {
                        cachedDividendStatusCodes += code
                        if (quote.isNoDividendConfirmed) cachedNoDividendCodes += code
                        else cachedNoDividendCodes -= code
                    } else if (quote.annualDividendPerShare != null && quote.annualDividendPerShare > 0.0) {
                        cachedNoDividendCodes -= code
                    }
                }
                val mergedPrices = readCachedPrices() + fetchedPrices
                val json = JSONObject().apply { mergedPrices.forEach { (code, price) -> put(code, price) } }
                val dividendJson = JSONObject().apply { cachedDividendsPerShare.forEach { (code, dps) -> put(code, dps) } }
                val noDividendJson = JSONArray().apply { cachedNoDividendCodes.sorted().forEach { put(it) } }
                val dividendStatusJson = JSONArray().apply { cachedDividendStatusCodes.sorted().forEach { put(it) } }
                marketPreferences.edit()
                    .putString("prices", json.toString())
                    .putString("dividend_per_share", dividendJson.toString())
                    .putString("confirmed_no_dividend_codes", noDividendJson.toString())
                    .putString("known_dividend_status_codes", dividendStatusJson.toString())
                    .putLong("last_price_fetch_at", now)
                    .putString("last_price_fetch_jst_date", PriceRefreshPolicy.japanDate(now))
                    .putString("last_price_fetch_status", "ok")
                    .remove("last_price_fetch_error")
                    .apply()
                applyPrices(mergedPrices, cachedDividendsPerShare, cachedNoDividendCodes, cachedDividendStatusCodes)
            }
            .onFailure { error ->
                marketPreferences.edit()
                    .putString("last_price_fetch_status", "failed")
                    .putString("last_price_fetch_error", error::class.java.simpleName)
                    .apply()
                android.util.Log.w("CloudflareMarket", "価格一覧の取得に失敗しました", error)
            }
    }

    private fun readCachedPrices(): Map<String, Double> {
        val json = runCatching { JSONObject(marketPreferences.getString("prices", "{}") ?: "{}") }.getOrNull()
            ?: return emptyMap()
        return buildMap {
            json.keys().forEach { code ->
                val price = json.optDouble(code, Double.NaN)
                if (PortfolioMath.validMoney(price) && price > 0.0) put(code.uppercase(java.util.Locale.ROOT), price)
            }
        }
    }

    private fun readCachedDividendsPerShare(): Map<String, Double> {
        val json = runCatching { JSONObject(marketPreferences.getString("dividend_per_share", "{}") ?: "{}") }.getOrNull()
            ?: return emptyMap()
        return buildMap {
            json.keys().forEach { code ->
                val dps = json.optDouble(code, Double.NaN)
                if (dps.isFinite() && dps >= 0.0 && PortfolioMath.validMoney(dps)) put(code.uppercase(java.util.Locale.ROOT), dps)
            }
        }
    }

    private fun readCachedDividendPerShare(code: String): Double? = runCatching {
        val json = JSONObject(marketPreferences.getString("dividend_per_share", "{}") ?: "{}")
        val value = json.optDouble(code.uppercase(java.util.Locale.ROOT), Double.NaN)
        value.takeIf { it.isFinite() && it >= 0.0 && PortfolioMath.validMoney(it) }
    }.getOrNull()

    private fun readCachedNoDividendCodes(): Set<String> = runCatching {
        val rows = JSONArray(marketPreferences.getString("confirmed_no_dividend_codes", "[]") ?: "[]")
        (0 until rows.length()).mapNotNull { index ->
            rows.optString(index).trim().uppercase(java.util.Locale.ROOT)
                .takeIf { it.matches(Regex("[A-Z0-9.\\-]{1,16}")) }
        }.toSet()
    }.getOrDefault(emptySet())

    private fun readCachedDividendStatusCodes(): Set<String> = runCatching {
        val rows = JSONArray(marketPreferences.getString("known_dividend_status_codes", "[]") ?: "[]")
        (0 until rows.length()).mapNotNull { index ->
            rows.optString(index).trim().uppercase(java.util.Locale.ROOT)
                .takeIf { it.matches(Regex("[A-Z0-9.\\-]{1,16}")) }
        }.toSet()
    }.getOrDefault(emptySet())

    private fun resolveNoDividendStatus(
        code: String,
        quoteDps: Double?,
        existingStatus: Boolean,
        noDividendCodes: Set<String>,
        knownStatusCodes: Set<String>
    ): Boolean = when {
        quoteDps?.let { it > 0.0 } == true -> false
        code in knownStatusCodes -> code in noDividendCodes
        else -> existingStatus || code in noDividendCodes
    }

    private fun applyCachedPrices() = applyPrices(
        readCachedPrices(), readCachedDividendsPerShare(), readCachedNoDividendCodes(), readCachedDividendStatusCodes()
    )

    private fun applyPrices(
        prices: Map<String, Double>,
        dividendsPerShare: Map<String, Double>,
        noDividendCodes: Set<String>,
        knownStatusCodes: Set<String>
    ) {
        if (prices.isEmpty() && dividendsPerShare.isEmpty() && noDividendCodes.isEmpty() && knownStatusCodes.isEmpty()) return
        _holdings.update { list -> list.map { holding ->
            val code = holding.code.uppercase(java.util.Locale.ROOT)
            val price = prices[code] ?: holding.price
            val quoteDps = dividendsPerShare[code]
            val noDividendConfirmed = resolveNoDividendStatus(
                code, quoteDps, holding.isNoDividendConfirmed, noDividendCodes, knownStatusCodes
            )
            val dps = quoteDps ?: if (noDividendConfirmed) 0.0 else
                _financialData.value[holding.code]?.dividends?.lastOrNull()?.dps ?: holding.dps
            if (price == holding.price && dps == holding.dps && noDividendConfirmed == holding.isNoDividendConfirmed) holding
            else holding.copy(price = price, dps = dps, yieldRate = PortfolioMath.yieldPercent(dps, price),
                isNoDividendConfirmed = noDividendConfirmed).refreshPayout()
        } }
        _watchlist.update { list -> list.map { stock ->
            val code = stock.code.uppercase(java.util.Locale.ROOT)
            val price = prices[code] ?: stock.price
            val quoteDps = dividendsPerShare[code]
            val noDividendConfirmed = resolveNoDividendStatus(
                code, quoteDps, stock.isNoDividendConfirmed, noDividendCodes, knownStatusCodes
            )
            val dps = quoteDps ?: if (noDividendConfirmed) 0.0 else
                _financialData.value[stock.code]?.dividends?.lastOrNull()?.dps ?: stock.dps
            if (price == stock.price && dps == stock.dps && noDividendConfirmed == stock.isNoDividendConfirmed) stock
            else stock.copy(
                    price = price,
                    dps = dps,
                    currentYield = PortfolioMath.yieldPercent(dps, price),
                    isNoDividendConfirmed = noDividendConfirmed,
                    targetPrice = if (stock.targetYield > 0) dps / (stock.targetYield / 100) else stock.targetPrice
                )
        } }
    }

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
        if (!restoration.isCompleted || !canEditWatchlist()) return
        _watchlist.update { list ->
            list.map { stock ->
                if (stock.code == code) {
                    if (!delta.isFinite()) return@map stock
                    val newTarget = PortfolioMath.adjustedTarget(stock.targetYield, delta)
                    stock.copy(targetYield = newTarget, targetPrice = stock.dps / (newTarget / 100))
                } else stock
            }
        }
        saveRequests.trySend(Unit)
    }

    fun toggleWatchlistStock(code: String) {
        if (!restoration.isCompleted || !canEditWatchlist()) return
        _watchlist.update { list ->
            list.map { stock ->
                if (stock.code == code) {
                    stock.copy(alertEnabled = !stock.alertEnabled)
                } else stock
            }
        }
        saveRequests.trySend(Unit)
    }

    fun removeWatchlistStock(code: String): WatchlistStock? {
        if (!restoration.isCompleted || !canEditWatchlist()) return null
        val removed = _watchlist.value.firstOrNull { it.code.equals(code, true) } ?: return null
        _watchlist.update { list -> list.filterNot { it.code.equals(code, true) } }
        saveRequests.trySend(Unit)
        return removed
    }

    fun restoreWatchlistStock(stock: WatchlistStock): Boolean {
        if (!restoration.isCompleted || !canEditWatchlist()) return false
        if (_watchlist.value.any { it.code.equals(stock.code, true) }) return false
        _watchlist.update { list -> list + stock }
        saveRequests.trySend(Unit)
        return true
    }

    fun toggleWatchlistMembership(holding: Holding) {
        if (!restoration.isCompleted || !canEditWatchlist()) return
        _watchlist.update { list ->
            if (list.any { it.code.equals(holding.code, true) }) list.filterNot { it.code.equals(holding.code, true) }
            else list + WatchlistStock(holding.code, holding.name, holding.market, holding.sector,
                if (holding.category == Category.US) "us" else "jp", holding.price, holding.currency, holding.dps,
                holding.liveYield, holding.liveYield.coerceAtLeast(0.1), holding.price, holding.dailyChange, holding.isPositive,
                holding.dividendMonths, holding.nextPayoutTiming, "", false, usdJpyRate = _exchangeRate.value?.usdJpy,
                isNoDividendConfirmed = holding.isNoDividendConfirmed)
        }
        saveRequests.trySend(Unit)
        applyCachedDividendMonths()
    }

    fun addHoldingToWatchlist(holding: Holding) {
        if (!restoration.isCompleted || !canEditWatchlist()) return
        val stock = WatchlistStock(
            holding.code, holding.name, holding.market, holding.sector,
            if (holding.category == Category.US) "us" else "jp", holding.price, holding.currency, holding.dps,
            holding.liveYield, holding.liveYield.coerceAtLeast(0.1), holding.price, holding.dailyChange, holding.isPositive,
            holding.dividendMonths, holding.nextPayoutTiming, "", false, usdJpyRate = _exchangeRate.value?.usdJpy,
            isNoDividendConfirmed = holding.isNoDividendConfirmed
        )
        _watchlist.update { list ->
            if (list.any { it.code.equals(holding.code, true) }) list else list + stock
        }
        saveRequests.trySend(Unit)
        applyCachedDividendMonths()
    }

    suspend fun registerWatchlistStock(code: String): Boolean {
        restoration.await()
        if (!canEditWatchlist()) return false
        val normalized = PortfolioMath.normalizeCode(code)
        if (!PortfolioMath.validCode(normalized)) return false
        if (_watchlist.value.none { it.code.equals(normalized, true) }) {
            val owned = _holdings.value.firstOrNull { it.code.equals(normalized, true) }
            val security = if (owned == null) resolveCloudflareSecurity(normalized) else null
            val holding = com.kurogane.dividend.model.StockDetailResolver.resolve(
                normalized, _holdings.value, _watchlist.value, security
            ) ?: return false
            addHoldingToWatchlist(holding)
        }
        persistCurrentHoldings()
        return true
    }

    private fun canEditWatchlist(): Boolean {
        if (watchlistLoadFailed) _saveError.value = getApplication<android.app.Application>().getString(R.string.watchlist_restore_required)
        return !watchlistLoadFailed
    }

    fun updateHolding(
        code: String,
        shares: Int,
        acqPrice: Double,
        currentPrice: Double? = null
    ): Boolean {
        if (!restoration.isCompleted || holdingsLoadFailed) return false
        if (shares < 0 || !PortfolioMath.validMoney(acqPrice) ||
            (currentPrice != null && !PortfolioMath.validMoney(currentPrice))) return false
        if (_holdings.value.none { it.code.equals(code, true) }) return false
        updatePortfolio(_holdings.value.map { holding ->
            if (!holding.code.equals(code, true)) holding else {
                val free = if (holding.shares > 0) (shares.toDouble() * holding.effectiveTaxFreeShares / holding.shares).toInt() else 0
                holding.copy(shares = shares, acqPrice = acqPrice, price = currentPrice ?: holding.price,
                    taxFreeShares = free
                ).refreshPayout()
            }
        })
        return true
    }

    fun removeHolding(id: String): Boolean {
        if (!restoration.isCompleted || holdingsLoadFailed) return false
        val current = _holdings.value
        if (current.none { it.id == id }) return false
        updatePortfolio(current.filterNot { it.id == id })
        return true
    }

    fun restoreHolding(holding: Holding): Boolean {
        if (!restoration.isCompleted || holdingsLoadFailed) return false
        if (_holdings.value.any { it.id == holding.id || it.code.equals(holding.code, true) }) return false
        updatePortfolio(_holdings.value + holding)
        return true
    }

    fun addHolding(code: String, shares: Int, acqPrice: Double) {
        if (_holdings.value.none { it.code.equals(code, true) })
            addOrUpdateHolding(code, shares, acqPrice, "特定口座")
    }

    fun addOrUpdateHolding(code: String, shares: Int, acqPrice: Double, accountType: String,
                           cloudflareSecurity: CloudflareSecurity? = null): Boolean =
        addAllocations(code, listOf(HoldingAllocation(shares, acqPrice, accountType)), cloudflareSecurity)

    private fun addAllocations(code: String, allocations: List<HoldingAllocation>, security: CloudflareSecurity?): Boolean {
        val before = _holdings.value
        val merged = allocatedHolding(code, allocations, security, before) ?: return false
        updatePortfolio(if (before.any { it.id == merged.id }) before.map { if (it.id == merged.id) merged else it } else before + merged)
        refreshAllocationMonths(merged)
        viewModelScope.launch {
            refreshMarketPricesIfDue()
            runCatchingCancellable { fetchFinancialData(merged.code, force = true) }
            runCatchingCancellable { fetchStockAnalysis(merged.code, force = true) }
        }
        return true
    }

    private fun refreshAllocationMonths(merged: Holding) {
        applyCachedDividendMonths()
    }

    private fun allocatedHolding(code: String, allocations: List<HoldingAllocation>, security: CloudflareSecurity?, holdings: List<Holding>, replaceExisting: Boolean = false): Holding? {
        if (!restoration.isCompleted || holdingsLoadFailed) return null
        val normalized = PortfolioMath.normalizeCode(code)
        if (!PortfolioMath.validCode(normalized) || allocations.isEmpty() || allocations.none { it.shares > 0 } ||
            allocations.any { it.shares < 0 || !PortfolioMath.validMoney(it.acquisitionPrice) || it.accountType.isBlank() }) return null
        val old = holdings.firstOrNull { it.code.equals(normalized, true) }
        val preset = _watchlist.value.firstOrNull { it.code.equals(normalized, true) }
        if (old == null && preset == null && security == null) return null
        val securityHasDividend = security?.annualDividendPerShare?.let { it > 0.0 } == true
        val noDividendConfirmed = when {
            securityHasDividend -> false
            security?.hasDividendStatus == true -> security.isNoDividendConfirmed
            old?.isNoDividendConfirmed == true || preset?.isNoDividendConfirmed == true -> true
            else -> false
        }
        val dps = when {
            security?.isNoDividendConfirmed == true -> 0.0
            security?.annualDividendPerShare != null -> security.annualDividendPerShare
            noDividendConfirmed -> 0.0
            else -> old?.dps ?: preset?.dps ?: 0.0
        }
        val price = readCachedPrices()[normalized] ?: security?.marketPrice ?: old?.price ?: preset?.price ?: 0.0
        if (!PortfolioMath.validMoney(dps) || !PortfolioMath.validMoney(price)) return null
        val currency = old?.currency ?: preset?.currency ?: if (normalized.matches(Regex("[A-Z]+"))) "$" else "¥"
        val template = old ?: Holding(
            id = "holding-$normalized", code = normalized, name = security?.name ?: preset?.name.orEmpty(),
            sector = security?.industry ?: preset?.sector ?: "業種未登録",
            market = preset?.market ?: if (currency == "$") "米国株" else "東証", category = if (currency == "$") Category.US else Category.JAPAN,
            price = price, currency = currency, shares = 0, acqPrice = 0.0, dps = dps, yieldRate = 0.0, yoc = 0.0,
            nextPayoutTiming = "配当月未取得", nextPayoutAmount = "—", dividendMonths = emptyList(),
            dividendType = "", accountType = "", dailyChange = "—", isPositive = true
        )
        val mergeBase = if (replaceExisting && old != null) {
            template.copy(shares = 0, acqPrice = 0.0, taxFreeShares = 0, accountType = "")
        } else template
        val merged = runCatching {
            PortfolioMath.mergeAllocations(mergeBase.copy(
                price = price, dps = dps, name = security?.name ?: template.name,
                isNoDividendConfirmed = noDividendConfirmed
            ), allocations)
        }.getOrNull() ?: return null
        return merged
    }

    fun addHoldingAllocations(code: String, allocations: List<HoldingAllocation>): Boolean = addAllocations(code, allocations, null)

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        displayPreferences.edit().putString("theme_mode", mode).apply()
    }

    fun setIsOledBlack(active: Boolean) {
        _isOledBlack.value = active
        displayPreferences.edit().putBoolean("oled_black", active).apply()
    }

    fun setSelectedAccent(accent: String) {
        _selectedAccent.value = accent
        displayPreferences.edit().putString("selected_accent", accent).apply()
    }

    fun setTaxRateMode(mode: String) {
        if (mode !in setOf("gross", "net")) return
        _taxRateMode.value = mode
        displayPreferences.edit().putString("tax_rate_mode", mode).apply()
    }

    fun setMaskAtStartup(mask: Boolean) {
        _maskAtStartup.value = mask
        displayPreferences.edit().putBoolean("mask_at_startup", mask).apply()
    }

    fun setNotificationEnabled(kind: NotificationKind, enabled: Boolean) {
        viewModelScope.launch {
            notificationMutex.withLock {
                runCatchingCancellable {
                    restoration.await()
                    // Worker must see the latest stock alert flags before it can run.
                    if (enabled) persistCurrentHoldings()
                    if (enabled && kind == NotificationKind.DIVIDEND_CHANGE) {
                        _financialData.value.values.forEach { data ->
                            notificationStore.seedDividendChangeBaseline(data.code, data.dividends.map { it.year to it.dps })
                        }
                    }
                    val settings = withContext(Dispatchers.IO) { notificationStore.setEnabled(kind, enabled) }
                    _notificationSettings.value = settings
                    notificationScheduler(getApplication(), settings)
                    if (!enabled && kind == NotificationKind.DIVIDEND_CHANGE) notificationStore.clearPendingDividendChanges()
                    if (!enabled) DiviewNotifications(getApplication()).cancel(kind)
                    if (enabled && kind == NotificationKind.DIVIDEND_CHANGE) {
                        launch { refreshFinancialDataIfDue(force = true) }
                    }
                    if (enabled && kind == NotificationKind.DIVIDEND) {
                        NotificationScheduler.enqueueDividendRightsRefresh(getApplication())
                    }
                }.onFailure { _saveError.value = getApplication<android.app.Application>().getString(R.string.dividend_view_model_message) }
            }
        }
    }

    suspend fun resetLocalData() {
        restoration.await()
        dataGeneration.incrementAndGet()
        notificationMutex.withLock {
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                _notificationSettings.value = notificationStore.clear()
                notificationScheduler(getApplication(), _notificationSettings.value)
                DiviewNotifications(getApplication()).clear()
                localImportStore.clear()
                localImportStore.saveHoldings(emptyList())
                localImportStore.saveWatchlist(emptyList())
                check(marketPreferences.edit().clear().commit()) { "価格キャッシュを初期化できませんでした" }
                synchronized(dividendEventCacheLock) { dividendEventCache.clear() }
                check(displayPreferences.edit().clear().putBoolean("popular_r2_holdings_seeded_v1", true).commit()) { "設定を初期化できませんでした" }
            }
            _watchlistSortSelection.value = null
            _holdings.value = emptyList()
            _watchlist.value = emptyList()
            _dividendEventRevision.update { it + 1 }
            shareholderBenefitsSaveJob?.cancel()
            _shareholderBenefits.value = emptyMap()
            stockClimateSaveJob?.cancel()
            _stockClimateOverrides.value = emptyMap()
            _dividendHistoryOverrides.value = emptyMap()
            hasRestoredLocalHoldings = true
            holdingsLoadFailed = false
            watchlistLoadFailed = false
            _themeMode.value = "system"
            _selectedAccent.value = "emerald"
            _isOledBlack.value = false
            _isPrivacyActive.value = false
            _maskAtStartup.value = false
            _calculationBasis.value = "eval"
            _roadmapTargetYield.value = 4.0
            _growthSimulatorMonthlyContribution.value = 30_000L
            _growthSimulatorDividendYield.value = 4.0
            _compositionThreshold.value = 5
            _compositionOtherEnabled.value = true
            _roadmapShareTab.value = 0
            _roadmapShareMasked.value = false
            _taxRateMode.value = "gross"
            _usTaxDeduction.value = true
            _biometricEnabled.value = false
            _financialData.value = emptyMap()
            _exchangeRate.value = null
            _exchangeRateError.value = null
            _saveError.value = null
        }
        }
    }

    init {
        viewModelScope.launch {
            val (heldResult, watchedResult) = withContext(Dispatchers.IO) {
                JPXTickSizeMaster.load(getApplication())
                hasRestoredLocalHoldings = localImportStore.hasHoldings()
                _shareholderBenefits.value = runCatching { localImportStore.loadShareholderBenefits() }.getOrDefault(emptyMap())
                _stockClimateOverrides.value = runCatching { localImportStore.loadStockClimateOverrides() }.getOrDefault(emptyMap())
                _dividendHistoryOverrides.value = runCatching { localImportStore.loadDividendHistoryOverrides() }.getOrDefault(emptyMap())
                _cloudflareSecurityData.value = loadCachedCloudflareSecurityData()
                runCatching { localImportStore.loadHoldings() } to runCatching { localImportStore.loadWatchlist() }
            }
            heldResult.onSuccess { restored ->
                _holdings.value = (restored ?: _holdings.value).filterNot { it.id in SAMPLE_HOLDING_IDS || it.id.startsWith("r2-") }.map { it.copy(dividendMonths = emptyList()).refreshPayout() }
            }.onFailure {
                holdingsLoadFailed = true
                _holdings.value = emptyList()
                _saveError.value = getApplication<android.app.Application>().getString(R.string.dividend_view_model_message_2)
            }
            watchedResult.onSuccess { restored ->
                _watchlist.value = (restored ?: _watchlist.value).map {
                    it.copy(payoutMonths = emptyList(), payoutTiming = "配当月未取得")
                }
            }.onFailure {
                watchlistLoadFailed = true
                _watchlist.value = emptyList()
                _saveError.value = getApplication<android.app.Application>().getString(R.string.dividend_view_model_message_3)
            }
            withContext(Dispatchers.IO) {
                identities = Tse33IndustryMaster.identities(getApplication())
                _exchangeRate.value = runCatching { marketPreferences.getString("fx", null)?.let { ExchangeRate.parse(JSONObject(it)) } }.getOrNull()
                val cached = (_holdings.value.map { it.code } + _watchlist.value.filter { it.category == "jp" }.map { it.code })
                    .distinct().mapNotNull { code ->
                    runCatching { marketPreferences.getString("financial_$code", null)?.let { FinancialData.parse(JSONObject(it), code) } }.getOrNull()
                }
                _financialData.value = cached.associateBy { it.code }
            }
            if (_notificationSettings.value.dividendChangeEnabled) {
                _financialData.value.values.forEach { data ->
                    notificationStore.seedDividendChangeBaseline(data.code, data.dividends.map { it.year to it.dps })
                }
            }
            applyCachedDividendMonths()
            refreshRuntimeValues()
            // Readiness includes the first durable save. Otherwise a late startup
            // write can replace a backup or other edit made immediately after startup.
            runCatchingCancellable { persistCurrentHoldings() }.onFailure {
                _saveError.value = getApplication<android.app.Application>().getString(R.string.dividend_view_model_message_4)
            }
            _localDataRestored.value = true
            restoration.complete(Unit)
            for (ignored in saveRequests) {
                runCatchingCancellable { persistCurrentHoldings() }.onFailure {
                    _saveError.value = getApplication<android.app.Application>().getString(R.string.dividend_view_model_message_4)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            restoration.await()
            applyCachedPrices()
            if (refreshOnStart) {
                // Start the payout-month request as soon as local Watchlist data is restored.
                // The monthly projection observes the Watchlist flow and updates when results arrive.
                launch { refreshDividendHistoryIfDue() }
                launch { refreshIncomeAdviceCatalogIfDue() }
                launch { refreshStockDetailDataForPortfolio() }
                launch { refreshExchangeRate() }
                runCatching { notificationScheduler(getApplication(), _notificationSettings.value) }.onFailure {
                    _saveError.value = getApplication<android.app.Application>().getString(R.string.dividend_view_model_message_5)
                }
                if (_notificationSettings.value.dividendEnabled) {
                    NotificationScheduler.enqueueDividendRightsRefresh(getApplication())
                }
                refreshMarketPricesIfDue()
                launch { refreshFinancialDataIfDue(force = _notificationSettings.value.dividendChangeEnabled) }
            }
        }
    }

    private companion object {
        const val FINANCIAL_PAYLOAD_VERSION = 2
        val WATCHLIST_SORT_CRITERIA = setOf("ach", "yield", "price", "code")
        val WATCHLIST_SORT_DIRECTIONS = setOf("asc", "desc")
        val SAMPLE_HOLDING_IDS = setOf("h-7203", "h-2914", "h-8306", "h-8316", "h-8058", "h-9432", "h-9433", "h-8766", "h-8031", "h-4502", "h-apple", "h-emaxis")
    }
}
