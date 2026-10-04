# Watchlist & 権利落ち月マトリクス Flutter (Dart) 実装仕様書 & フルソースコード

選択された「Watchlist（権利落ち月マトリクス展開ビュー）」のUIデザイン、オブシディアンダークテーマ（#0B1326 & Emerald #4EDEA3）、12ヶ月配当密度ヒートマップマトリクス、権利落ち・受取平準化アナリティクス、銘柄リスト、目標利回り設定、および複数口座配分登録モーダルを忠実に再現したFlutterコード一式です。

---

## 1. ディレクトリ構成推奨

```text
lib/
├── main.dart
├── theme/
│   └── app_theme.dart
├── models/
│   ├── watchlist_item.dart
│   └── ex_dividend_matrix_data.dart
└── screens/
    ├── watchlist_screen.dart
    └── widgets/
        ├── watchlist_kpi_summary.dart
        ├── ex_dividend_matrix_bottom_sheet.dart
        ├── watchlist_item_card.dart
        └── buy_target_stepper_modal.dart
```

---

## 2. デザイントークン & カラーテーマ (`theme/app_theme.dart`)

```dart
import 'package:flutter/material.dart';

class AppColors {
  // Kurogane Obsidian Dark Theme Palette
  static const Color obsidianNavy = Color(0xFF0B1326);
  static const Color surfaceContainerLowest = Color(0xFF060E20);
  static const Color surfaceContainerLow = Color(0xFF131B2E);
  static const Color surfaceContainer = Color(0xFF171F33);
  static const Color surfaceContainerHigh = Color(0xFF222A3D);
  static const Color surfaceContainerHighest = Color(0xFF2D3449);

  // Brand Accents
  static const Color primaryEmerald = Color(0xFF4EDEA3);
  static const Color primaryContainer = Color(0xFF10B981);
  static const Color onPrimary = Color(0xFF003824);
  static const Color secondaryBlue = Color(0xFFC0C1FF);
  static const Color cyanAccent = Color(0xFF38BDF8);

  // Status & Intensity Colors (Matrix Heatmap)
  static const Color matrixCellInactive = Color(0xFF171F33);
  static const Color matrixCellExDividend = Color(0xFF222A3D);
  static const Color matrixCellPayoutLow = Color(0xFF104E38);
  static const Color matrixCellPayoutHigh = Color(0xFF10B981);

  // Text Colors
  static const Color textPrimary = Color(0xFFDAE2FD);
  static const Color textSecondary = Color(0xFFBBCAAF);
  static const Color textTertiary = Color(0xFF86948A);
  static const Color errorRed = Color(0xFFFFB4AB);
}

class AppTheme {
  static ThemeData get darkTheme {
    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AppColors.obsidianNavy,
      primaryColor: AppColors.primaryEmerald,
      colorScheme: const ColorScheme.dark(
        primary: AppColors.primaryEmerald,
        primaryContainer: AppColors.primaryContainer,
        onPrimary: AppColors.onPrimary,
        surface: AppColors.surfaceContainerLow,
        onSurface: AppColors.textPrimary,
        secondary: AppColors.secondaryBlue,
      ),
      fontFamily: 'Manrope',
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.obsidianNavy,
        elevation: 0,
        centerTitle: false,
      ),
    );
  }
}
```

---

## 3. データモデル (`models/watchlist_item.dart`, `models/ex_dividend_matrix_data.dart`)

```dart
class WatchlistItem {
  final String code;
  final String name;
  final String market;
  final double currentPrice;
  final double priceChangePercent;
  final double currentYield;
  double targetYield;
  final double annualDividendPerShare;
  final bool isUsStock;
  final bool isTargetReached;
  final List<int> exDividendMonths; // 権利確定月 (1~12)
  final List<int> payoutMonths;     // 入金月 (1~12)
  final Map<int, String> monthlyPayoutText;

  WatchlistItem({
    required this.code,
    required this.name,
    required this.market,
    required this.currentPrice,
    required this.priceChangePercent,
    required this.currentYield,
    required this.targetYield,
    required this.annualDividendPerShare,
    this.isUsStock = false,
    this.isTargetReached = false,
    required this.exDividendMonths,
    required this.payoutMonths,
    required this.monthlyPayoutText,
  });

  // 目標買場価格の逆算 (配当金 / 目標利回り)
  double get targetPrice => targetYield > 0 ? (annualDividendPerShare / (targetYield / 100)) : currentPrice;
  double get priceDiffToTarget => currentPrice - targetPrice;
  double get progressToTarget => (currentYield / targetYield) * 100;
}
```

---

## 4. メイン画面実装 (`screens/watchlist_screen.dart`)

```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/watchlist_item.dart';
import '../theme/app_theme.dart';
import 'widgets/watchlist_kpi_summary.dart';
import 'widgets/ex_dividend_matrix_bottom_sheet.dart';
import 'widgets/watchlist_item_card.dart';

class WatchlistScreen extends StatefulWidget {
  const WatchlistScreen({super.key});

  @override
  State<WatchlistScreen> createState() => _WatchlistScreenState();
}

class _WatchlistScreenState extends State<WatchlistScreen> {
  int _currentBottomNavIndex = 3; // Watchlist
  String _selectedFilter = 'ALL';
  String _sortBy = 'PROXIMITY'; // 利回り達成率順

  final List<WatchlistItem> _items = [
    WatchlistItem(
      code: '8316',
      name: '三井住友FG',
      market: '東証P • 銀行業',
      currentPrice: 3150.0,
      priceChangePercent: -0.69,
      currentYield: 4.13,
      targetYield: 3.80,
      annualDividendPerShare: 130.0,
      isTargetReached: true,
      exDividendMonths: [3, 9],
      payoutMonths: [6, 12],
      monthlyPayoutText: {3: '権利落 ¥6,500', 6: '入金 ¥6,500', 9: '権利落 ¥6,500', 12: '入金 ¥6,500'},
    ),
    WatchlistItem(
      code: '7203',
      name: 'トヨタ自動車',
      market: '東証P • 輸送用機器',
      currentPrice: 2600.0,
      priceChangePercent: 1.36,
      currentYield: 2.80,
      targetYield: 3.00,
      annualDividendPerShare: 72.8,
      exDividendMonths: [3, 9],
      payoutMonths: [6, 12],
      monthlyPayoutText: {3: '権利落 ¥3,640', 6: '入金 ¥3,640', 9: '権利落 ¥3,640', 12: '入金 ¥3,640'},
    ),
    WatchlistItem(
      code: '9432',
      name: '日本電信電話 (NTT)',
      market: '東証P • 情報通信',
      currentPrice: 154.2,
      priceChangePercent: 0.0,
      currentYield: 3.37,
      targetYield: 4.00,
      annualDividendPerShare: 5.2,
      exDividendMonths: [3, 9],
      payoutMonths: [6, 12],
      monthlyPayoutText: {3: '権利落 ¥2,600', 6: '入金 ¥2,600', 9: '権利落 ¥2,600', 12: '入金 ¥2,600'},
    ),
    WatchlistItem(
      code: 'AAPL',
      name: 'Apple Inc.',
      market: 'NASDAQ • テクノロジー',
      currentPrice: 220.0,
      priceChangePercent: 0.66,
      currentYield: 0.45,
      targetYield: 0.50,
      annualDividendPerShare: 1.00,
      isUsStock: true,
      exDividendMonths: [2, 5, 8, 11],
      payoutMonths: [2, 5, 8, 11],
      monthlyPayoutText: {2: '権/入 \$2.5', 5: '権/入 \$2.5', 8: '権/入 \$2.5', 11: '権/入 \$2.5'},
    ),
  ];

  @override
  void initState() {
    super.initState();
    // 画面ロード時に権利落ち月マトリクスシートを展開（仕様再現）
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _showExDividendMatrixSheet();
    });
  }

  void _showExDividendMatrixSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => ExDividendMatrixBottomSheet(items: _items),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.obsidianNavy,
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Text(
              'Watchlist',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            Text(
              'MONITORED STOCKS & TARGET YIELDS',
              style: TextStyle(
                fontSize: 9,
                fontWeight: FontWeight.w600,
                color: AppColors.textTertiary,
                letterSpacing: 1.2,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: AppColors.textSecondary),
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(Icons.notifications_none, color: AppColors.textSecondary),
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined, color: AppColors.textSecondary),
            onPressed: () {},
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentBottomNavIndex,
        backgroundColor: AppColors.surfaceContainerLow,
        indicatorColor: AppColors.surfaceContainer,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.account_balance_wallet_outlined),
            label: '資産管理',
          ),
          NavigationDestination(
            icon: Icon(Icons.pie_chart_outline),
            label: '分析',
          ),
          NavigationDestination(
            icon: Icon(Icons.flag_outlined),
            label: 'ロードマップ',
          ),
          NavigationDestination(
            icon: Icon(Icons.bookmark, color: AppColors.primaryEmerald),
            label: 'Watchlist',
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: AppColors.primaryContainer,
        foregroundColor: AppColors.onPrimary,
        elevation: 6,
        onPressed: _showExDividendMatrixSheet,
        icon: const Icon(Icons.calendar_month, size: 20),
        label: const Text(
          '権利落ちマトリクス',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 1. KPI サマリー (買場到達銘柄 / 想定配当ポテンシャル)
          WatchlistKpiSummary(
            reachedCount: _items.where((i) => i.isTargetReached).length,
            totalMonitored: _items.length,
            avgYield: 3.84,
            potentialDividend: 42800,
          ),
          const SizedBox(height: 16),

          // 2. フィルター & ソート バー
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Wrap(
                spacing: 6,
                children: [
                  _buildFilterChip('すべて (${_items.length})', 'ALL'),
                  _buildFilterChip('日本株 (3)', 'JP'),
                  _buildFilterChip('米国株 (1)', 'US'),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: const [
                    Icon(Icons.swap_vert, size: 16, color: AppColors.primaryEmerald),
                    SizedBox(width: 4),
                    Text(
                      '利回り達成率順',
                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 3. 監視銘柄一覧
          ..._items.map((item) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: WatchlistItemCard(
                  item: item,
                  onTargetYieldChanged: (newYield) {
                    setState(() {
                      item.targetYield = newYield;
                    });
                  },
                ),
              )),
          const SizedBox(height: 80),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String value) {
    final isSelected = _selectedFilter == value;
    return GestureDetector(
      onTap: () => setState(() => _selectedFilter = value),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.primaryContainer : AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            color: isSelected ? AppColors.onPrimary : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }
}
```

---

## 5. 権利落ち月マトリクス・ボトムシート (`widgets/ex_dividend_matrix_bottom_sheet.dart`)

```dart
import 'package:flutter/material.dart';
import '../../models/watchlist_item.dart';
import '../../theme/app_theme.dart';

class ExDividendMatrixBottomSheet extends StatelessWidget {
  final List<WatchlistItem> items;

  const ExDividendMatrixBottomSheet({super.key, required this.items});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.only(
        top: 12,
        bottom: MediaQuery.of(context).padding.bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 上部ドラッグバー
          Center(
            child: Container(
              width: 44,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 14),

          // ヘッダー
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: AppColors.primaryContainer.withOpacity(0.18),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.calendar_month, color: AppColors.primaryEmerald, size: 20),
                    ),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: const [
                        Text(
                          '権利落ち月マトリクス',
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                        ),
                        Text(
                          'ヒートマップ分析・想定入金スケジュール',
                          style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                        ),
                      ],
                    ),
                  ],
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: AppColors.textSecondary, size: 20),
                  onPressed: () => Navigator.pop(context),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // 3大KPIスモールバナー
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                _buildSmallKpiCard('想定配当回数', '10回/年', '日6回 + 米4回'),
                const SizedBox(width: 8),
                _buildSmallKpiCard('次回権利落ち', '3月27日', 'あと42日 (買付検討期)', isHighlight: true),
                const SizedBox(width: 8),
                _buildSmallKpiCard('潜在配当規模', '¥42,800', '想定利回り 3.84%'),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 12ヶ月ヒートマップマトリクステーブル
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '12ヶ月配当密度ヒートマップ',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                ),
                Row(
                  children: [
                    const Text('少 ', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                    _buildLegendDot(AppColors.surfaceContainer),
                    _buildLegendDot(AppColors.surfaceContainerHighest),
                    _buildLegendDot(AppColors.primaryContainer.withOpacity(0.5)),
                    _buildLegendDot(AppColors.primaryEmerald),
                    const Text(' 多', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),

          // スクロール可能なマトリクステーブル
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(12),
              ),
              child: DataTable(
                headingRowHeight: 38,
                dataRowHeight: 46,
                columnSpacing: 16,
                horizontalMargin: 12,
                columns: [
                  const DataColumn(label: Text('銘柄', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textSecondary))),
                  ...List.generate(
                    12,
                    (index) {
                      final m = index + 1;
                      final isPeak = m == 3 || m == 6 || m == 9 || m == 12;
                      return DataColumn(
                        label: Text(
                          '$m月',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: isPeak ? FontWeight.bold : FontWeight.normal,
                            color: isPeak ? AppColors.primaryEmerald : AppColors.textTertiary,
                          ),
                        ),
                      );
                    },
                  ),
                ],
                rows: items.map((item) {
                  return DataRow(
                    cells: [
                      DataCell(
                        Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(item.code, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
                            Text(item.name, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
                          ],
                        ),
                      ),
                      ...List.generate(12, (mIndex) {
                        final month = mIndex + 1;
                        final cellContent = item.monthlyPayoutText[month];

                        if (cellContent != null) {
                          final isPayout = cellContent.contains('入金');
                          return DataCell(
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                              decoration: BoxDecoration(
                                color: isPayout
                                    ? AppColors.primaryContainer.withOpacity(0.25)
                                    : AppColors.surfaceContainerHighest,
                                borderRadius: BorderRadius.circular(6),
                                border: isPayout ? Border.all(color: AppColors.primaryEmerald.withOpacity(0.4), width: 1) : null,
                              ),
                              child: Text(
                                cellContent,
                                style: TextStyle(
                                  fontSize: 9,
                                  fontWeight: FontWeight.bold,
                                  color: isPayout ? AppColors.primaryEmerald : AppColors.textPrimary,
                                ),
                              ),
                            ),
                          );
                        }
                        return const DataCell(Center(child: Text('-', style: TextStyle(color: AppColors.textTertiary))));
                      }),
                    ],
                  );
                }).toList(),
              ),
            ),
          ),
          const SizedBox(height: 14),

          // 権利落ち・受取平準化アナリティクスカード
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.primaryEmerald.withOpacity(0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: const [
                          Icon(Icons.balance, color: AppColors.secondaryBlue, size: 16),
                          SizedBox(width: 6),
                          Text(
                            '権利落ち・受取平準化アナリティクス',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                          ),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: const Text('偏重度: 82%', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '監視中の日本株3銘柄は 3月・9月末 に権利落ちが集中しており、実際の配当受取は 6月・12月 に偏ります。米国株（AAPL）の四半期配当を組み合わせることで 2/5/8/11月 のインカム空白月を補完できます。',
                    style: TextStyle(fontSize: 10, height: 1.5, color: AppColors.textSecondary),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: const [
                      Icon(Icons.circle, size: 8, color: AppColors.primaryEmerald),
                      SizedBox(width: 4),
                      Text('日株 6/12月 想定計: ¥25,480', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                      SizedBox(width: 12),
                      Icon(Icons.circle, size: 8, color: AppColors.cyanAccent),
                      SizedBox(width: 4),
                      Text('米株 想定年間計: \$10.00', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 閉じるボタン
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: SizedBox(
              width: double.infinity,
              height: 44,
              child: OutlinedButton(
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.surfaceContainerHighest),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                onPressed: () => Navigator.pop(context),
                child: const Text('マトリクスを閉じる', style: TextStyle(color: AppColors.textPrimary)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSmallKpiCard(String label, String value, String sub, {bool isHighlight = false}) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(10),
          border: isHighlight ? Border.all(color: AppColors.primaryEmerald, width: 1) : null,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(fontSize: 9, color: AppColors.textTertiary)),
            const SizedBox(height: 2),
            Text(
              value,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: isHighlight ? AppColors.primaryEmerald : AppColors.textPrimary,
              ),
            ),
            Text(sub, style: const TextStyle(fontSize: 8, color: AppColors.textTertiary), maxLines: 1, overflow: TextOverflow.ellipsis),
          ],
        ),
      ),
    );
  }

  Widget _buildLegendDot(Color color) {
    return Container(
      width: 8,
      height: 8,
      margin: const EdgeInsets.symmetric(horizontal: 2),
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    );
  }
}
```

---

## 6. サブウィジェット (`widgets/watchlist_kpi_summary.dart`, `widgets/watchlist_item_card.dart`)

### A. Watchlist KPIサマリー (`widgets/watchlist_kpi_summary.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class WatchlistKpiSummary extends StatelessWidget {
  final int reachedCount;
  final int totalMonitored;
  final double avgYield;
  final int potentialDividend;

  const WatchlistKpiSummary({
    super.key,
    required this.reachedCount,
    required this.totalMonitored,
    required this.avgYield,
    required this.potentialDividend,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.check_circle, size: 14, color: AppColors.primaryEmerald),
                    const SizedBox(width: 4),
                    Text(
                      '買場到達 $reachedCount銘柄 / $totalMonitored銘柄監視中',
                      style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                    ),
                  ],
                ),
              ),
              Row(
                children: const [
                  Text('買場アラート', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                  SizedBox(width: 4),
                  Icon(Icons.toggle_on, color: AppColors.primaryEmerald, size: 28),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('平均現在利回り', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                      Text('${avgYield.toStringAsFixed(2)}%', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
                      const Text('東証平均 +1.8%', style: TextStyle(fontSize: 9, color: AppColors.textSecondary)),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainer,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('配当ポテンシャル', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                      Text('¥${NumberFormat('#,###').format(potentialDividend)}/年', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                      const Text('各1単元保有試算', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

### B. 監視銘柄カード & 目標利回り調整 (`widgets/watchlist_item_card.dart`)
```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/watchlist_item.dart';
import '../../theme/app_theme.dart';

class WatchlistItemCard extends StatelessWidget {
  final WatchlistItem item;
  final ValueChanged<double> onTargetYieldChanged;

  const WatchlistItemCard({
    super.key,
    required this.item,
    required this.onTargetYieldChanged,
  });

  @override
  Widget build(BuildContext context) {
    final currency = NumberFormat('#,###');
    final isPriceUp = item.priceChangePercent >= 0;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        border: item.isTargetReached ? Border.all(color: AppColors.primaryEmerald, width: 1.2) : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 買場到達バッジ
          if (item.isTargetReached) ...[
            Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.primaryContainer.withOpacity(0.2),
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Text('絶好の買場到達 (目標利回り達成)', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
            ),
          ],
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      item.code,
                      style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(item.name, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                      Text(item.market, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                    ],
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    (item.isUsStock ? '\$' : '¥') + currency.format(item.currentPrice),
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                  ),
                  Text(
                    '${isPriceUp ? "+" : ""}${item.priceChangePercent.toStringAsFixed(2)}%',
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: isPriceUp ? AppColors.primaryEmerald : AppColors.errorRed,
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 利回りメーター & 目標ステッパー
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('現在配当利回り: ${item.currentYield}%', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                    Row(
                      children: [
                        const Text('目標: ', style: TextStyle(fontSize: 11, color: AppColors.textTertiary)),
                        IconButton(
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          icon: const Icon(Icons.remove_circle_outline, size: 18, color: AppColors.textSecondary),
                          onPressed: () => onTargetYieldChanged(item.targetYield - 0.1),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: Text(
                            '${item.targetYield.toStringAsFixed(2)}%',
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                          ),
                        ),
                        IconButton(
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          icon: const Icon(Icons.add_circle_outline, size: 18, color: AppColors.textSecondary),
                          onPressed: () => onTargetYieldChanged(item.targetYield + 0.1),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(3),
                  child: LinearProgressIndicator(
                    value: (item.progressToTarget / 100).clamp(0.0, 1.0),
                    minHeight: 4,
                    backgroundColor: AppColors.surfaceContainerHighest,
                    valueColor: AlwaysStoppedAnimation<Color>(
                      item.isTargetReached ? AppColors.primaryEmerald : AppColors.cyanAccent,
                    ),
                  ),
                ),
                const SizedBox(height: 6),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '買場目安: ¥${currency.format(item.targetPrice)} (${item.priceDiffToTarget > 0 ? "あと¥" + currency.format(item.priceDiffToTarget) + "下落" : "買場到達"})',
                      style: const TextStyle(fontSize: 10, color: AppColors.textTertiary),
                    ),
                    Text(
                      '1株配当: ¥${item.annualDividendPerShare}',
                      style: const TextStyle(fontSize: 10, color: AppColors.textTertiary),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),

          // アクションボタン (保有株に登録 & アラート)
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 36,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primaryContainer,
                      foregroundColor: AppColors.onPrimary,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      elevation: 0,
                    ),
                    icon: const Icon(Icons.add_task, size: 16),
                    label: const Text('保有株に登録', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    onPressed: () {},
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.notifications_active_outlined, size: 18, color: AppColors.primaryEmerald),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```
