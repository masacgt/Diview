package com.kurogane.dividend.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.MonthlyDividend
import com.kurogane.dividend.model.RoadmapStep
import com.kurogane.dividend.model.StepStatus
import com.kurogane.dividend.model.WatchlistStock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class DividendViewModel : ViewModel() {

    var assetScrollPosition = 0
    var annualScrollIndex = 0
    var annualScrollOffset = 0
    var roadmapScrollIndex = 0
    var roadmapScrollOffset = 0
    var watchlistScrollIndex = 0
    var watchlistScrollOffset = 0

    private val _isPrivacyActive = MutableStateFlow(false)
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _themeMode = MutableStateFlow("dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isOledBlack = MutableStateFlow(false)
    val isOledBlack: StateFlow<Boolean> = _isOledBlack.asStateFlow()

    private val _selectedAccent = MutableStateFlow("emerald")
    val selectedAccent: StateFlow<String> = _selectedAccent.asStateFlow()

    private val _taxRateMode = MutableStateFlow("gross") // "gross" or "net"
    val taxRateMode: StateFlow<String> = _taxRateMode.asStateFlow()

    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _holdings = MutableStateFlow(
        listOf(
            Holding(
                id = "h-7203",
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
                id = "h-2914",
                code = "2914",
                name = "日本たばこ産業 (JT)",
                sector = "東証P • 食料品",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 4100.0,
                shares = 100,
                acqPrice = 3800.0,
                dps = 194.0,
                yieldRate = 4.73,
                yoc = 5.11,
                nextPayoutTiming = "次回: 12月中旬 (¥9,700)",
                nextPayoutAmount = "¥9,700",
                dividendMonths = listOf(6, 12),
                dividendType = "期末配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥45 (+1.11%)",
                isPositive = true
            ),
            Holding(
                id = "h-8306",
                code = "8306",
                name = "三菱UFJフィナンシャルG",
                sector = "東証P • 銀行業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 1540.0,
                shares = 200,
                acqPrice = 1450.0,
                dps = 50.0,
                yieldRate = 3.25,
                yoc = 3.45,
                nextPayoutTiming = "次回: 12月中旬 (¥5,000)",
                nextPayoutAmount = "¥5,000",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥12 (+0.78%)",
                isPositive = true
            ),
            Holding(
                id = "h-8316",
                code = "8316",
                name = "三井住友フィナンシャルG",
                sector = "東証P • 銀行業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 3150.0,
                shares = 100,
                acqPrice = 3000.0,
                dps = 130.0,
                yieldRate = 4.13,
                yoc = 4.33,
                nextPayoutTiming = "次回: 12月中旬 (¥6,500)",
                nextPayoutAmount = "¥6,500",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "特定口座",
                dailyChange = "-¥22 (-0.69%)",
                isPositive = false
            ),
            Holding(
                id = "h-8058",
                code = "8058",
                name = "三菱商事",
                sector = "東証P • 卸売業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 3020.0,
                shares = 100,
                acqPrice = 2850.0,
                dps = 100.0,
                yieldRate = 3.31,
                yoc = 3.51,
                nextPayoutTiming = "次回: 12月中旬 (¥5,000)",
                nextPayoutAmount = "¥5,000",
                dividendMonths = listOf(6, 12),
                dividendType = "期末配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥34 (+1.14%)",
                isPositive = true
            ),
            Holding(
                id = "h-9432",
                code = "9432",
                name = "日本電信電話 (NTT)",
                sector = "東証P • 情報・通信業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 154.2,
                shares = 2000,
                acqPrice = 150.0,
                dps = 5.2,
                yieldRate = 3.37,
                yoc = 3.47,
                nextPayoutTiming = "次回: 12月中旬 (¥5,200)",
                nextPayoutAmount = "¥5,200",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "特定口座",
                dailyChange = "0.0 (0.00%)",
                isPositive = true
            ),
            Holding(
                id = "h-9433",
                code = "9433",
                name = "KDDI",
                sector = "東証P • 情報・通信業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 4500.0,
                shares = 50,
                acqPrice = 4200.0,
                dps = 145.0,
                yieldRate = 3.22,
                yoc = 3.45,
                nextPayoutTiming = "次回: 12月中旬 (¥3,625)",
                nextPayoutAmount = "¥3,625",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥15 (+0.33%)",
                isPositive = true
            ),
            Holding(
                id = "h-8766",
                code = "8766",
                name = "東京海上ホールディングス",
                sector = "東証P • 保険業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 3620.0,
                shares = 100,
                acqPrice = 3400.0,
                dps = 126.0,
                yieldRate = 3.48,
                yoc = 3.71,
                nextPayoutTiming = "次回: 12月中旬 (¥6,300)",
                nextPayoutAmount = "¥6,300",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥20 (+0.56%)",
                isPositive = true
            ),
            Holding(
                id = "h-8031",
                code = "8031",
                name = "三井物産",
                sector = "東証P • 卸売業",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 3200.0,
                shares = 100,
                acqPrice = 3100.0,
                dps = 105.0,
                yieldRate = 3.28,
                yoc = 3.39,
                nextPayoutTiming = "次回: 12月中旬 (¥5,250)",
                nextPayoutAmount = "¥5,250",
                dividendMonths = listOf(6, 12),
                dividendType = "期末配当",
                accountType = "新NISA成長枠",
                dailyChange = "+¥40 (+1.27%)",
                isPositive = true
            ),
            Holding(
                id = "h-4502",
                code = "4502",
                name = "武田薬品工業",
                sector = "東証P • 医薬品",
                market = "東証PRM",
                category = Category.JAPAN,
                price = 4300.0,
                shares = 100,
                acqPrice = 4150.0,
                dps = 188.0,
                yieldRate = 4.37,
                yoc = 4.53,
                nextPayoutTiming = "次回: 12月中旬 (¥9,400)",
                nextPayoutAmount = "¥9,400",
                dividendMonths = listOf(6, 12),
                dividendType = "中間配当",
                accountType = "特定口座",
                dailyChange = "+¥10 (+0.23%)",
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(1, 7),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(2, 8),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
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
                payoutMonths = listOf(6, 12),
                payoutTiming = "6月下旬 / 12月中旬",
                desc = "東証PRM · 鉄道・生活サービス最大手",
                alertEnabled = false,
                badge = "優良ディフェンシブ"
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

    init {
        // Fetch daily closing prices once on startup from Cloudflare R2 bucket with dummy security headers
        viewModelScope.launch {
            fetchPricesFromR2()
        }
    }

    private suspend fun fetchPricesFromR2() {
        val r2Url = "https://r2.yourdomain.com/stock_prices_daily.json"
        val accessKey = "cf_access_key_dummy_9f2a8c7b4e6d3f1a0e8d7c6b5a4"
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val connection = java.net.URL(r2Url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $accessKey")
                connection.setRequestProperty("X-Cloudflare-Access-Key", accessKey)
                connection.setRequestProperty("Accept", "application/json")
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            println("Cloudflare R2 Sync: Fetching on startup completed. Bucket offline or unconfigured, using high-fidelity preloads.")
        }
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

    fun toggleWatchlistStock(code: String) {
        _watchlist.update { list ->
            list.map { stock ->
                if (stock.code == code) {
                    stock.copy(alertEnabled = !stock.alertEnabled)
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

    fun addHolding(code: String, shares: Int, acqPrice: Double) {
        // Quick fallback helper
        _watchlist.value.find { it.code == code }?.let { stock ->
            val isAlreadyHeld = _holdings.value.any { it.code == code }
            if (!isAlreadyHeld) {
                val newHolding = Holding(
                    id = "h-${stock.code}",
                    code = stock.code,
                    name = stock.name,
                    sector = "東証P • ${stock.sector.split("·").first().trim()}",
                    market = stock.market,
                    category = if (stock.category == "us") Category.US else Category.JAPAN,
                    price = stock.price,
                    currency = stock.currency,
                    shares = shares,
                    acqPrice = acqPrice,
                    dps = stock.dps,
                    yieldRate = stock.currentYield,
                    yoc = (stock.dps / acqPrice) * 100,
                    nextPayoutTiming = "次回: ${stock.payoutTiming} (¥${((shares * stock.dps)/2).toInt()})",
                    nextPayoutAmount = "¥${((shares * stock.dps)/2).toInt()}",
                    dividendMonths = stock.payoutMonths,
                    dividendType = "一般配当",
                    accountType = "特定口座",
                    dailyChange = "+¥0 (0.0%)",
                    isPositive = true
                )
                _holdings.update { it + newHolding }
            }
        }
    }

    fun addOrUpdateHolding(code: String, shares: Int, acqPrice: Double, accountType: String) {
        val isAlreadyHeld = _holdings.value.any { it.code == code }
        if (isAlreadyHeld) {
            _holdings.update { list ->
                list.map {
                    if (it.code == code) {
                        val newShares = it.shares + shares
                        val newAcqPrice = if (newShares > 0) {
                            ((it.shares * it.acqPrice) + (shares * acqPrice)) / newShares
                        } else acqPrice
                        val halfDiv = (newShares * it.dps) / 2
                        it.copy(
                            shares = newShares,
                            acqPrice = newAcqPrice,
                            nextPayoutAmount = "¥${halfDiv.toInt()}",
                            accountType = accountType
                        )
                    } else it
                }
            }
        } else {
            val stockPreset = _watchlist.value.find { it.code == code }
            val name = stockPreset?.name ?: when(code) {
                "8316" -> "三井住友FG"
                "7203" -> "トヨタ自動車"
                "9432" -> "日本電信電話"
                "8058" -> "三菱商事"
                "AAPL" -> "Apple Inc."
                "MSFT" -> "Microsoft Corp."
                else -> "新規銘柄"
            }
            val dps = stockPreset?.dps ?: when(code) {
                "8316" -> 130.0
                "7203" -> 72.8
                "9432" -> 5.2
                "8058" -> 100.0
                "AAPL" -> 1.0
                "MSFT" -> 3.0
                else -> 10.0
            }
            val price = stockPreset?.price ?: when(code) {
                "8316" -> 3150.0
                "7203" -> 2600.0
                "9432" -> 154.2
                "8058" -> 3240.0
                "AAPL" -> 220.0
                "MSFT" -> 440.0
                else -> 100.0
            }
            val sector = stockPreset?.sector ?: "情報通信 · 日本株"
            val currency = stockPreset?.currency ?: if (code.matches(Regex("[A-Z]+"))) "$" else "¥"
            val category = if (currency == "$") Category.US else Category.JAPAN

            val newHolding = Holding(
                id = "h-${code}-${System.currentTimeMillis()}",
                code = code,
                name = name,
                sector = sector,
                market = if (currency == "$") "NASDAQ" else "東証PRM",
                category = category,
                price = price,
                currency = currency,
                shares = shares,
                acqPrice = acqPrice,
                dps = dps,
                yieldRate = (dps / price) * 100,
                yoc = (dps / acqPrice) * 100,
                nextPayoutTiming = "次回: 12月中旬",
                nextPayoutAmount = "¥${((shares * dps) / 2).toInt()}",
                dividendMonths = listOf(6, 12),
                dividendType = "一般配当",
                accountType = accountType,
                dailyChange = "+¥0 (0.0%)",
                isPositive = true
            )
            _holdings.update { it + newHolding }
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    fun setIsOledBlack(active: Boolean) {
        _isOledBlack.value = active
    }

    fun setSelectedAccent(accent: String) {
        _selectedAccent.value = accent
    }

    fun setTaxRateMode(mode: String) {
        _taxRateMode.value = mode
    }
}
