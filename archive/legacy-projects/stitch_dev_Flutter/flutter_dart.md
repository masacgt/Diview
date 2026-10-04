# 銘柄詳細・分析画面 Flutter (Dart) 実装仕様書 & フルソースコード

選択された「銘柄詳細・検索サジェスト連動（{{DATA:SCREEN:SCREEN_46}}）」のUI構造、ダークモード配色（Kurogane Obsidian Navy & Emerald Accent）、リアルタイム株価ヘッダー、買い増しシミュレータ、1株配当推移バーグラフ、銘柄総合分析ステータス、および下部アクションバーを忠実に再現したFlutter実装コード一式です。

---

## 1. 推奨ディレクトリ構成

```text
lib/
├── theme/
│   └── app_theme.dart
├── models/
│   └── stock_detail_model.dart
└── screens/
    ├── stock_detail_screen.dart
    └── widgets/
        ├── stock_header_card.dart
        ├── dividend_simulation_card.dart
        ├── incremental_buy_simulator.dart
        ├── dividend_history_chart.dart
        ├── comprehensive_analysis_card.dart
        ├── buy_signal_detail_card.dart
        ├── upcoming_event_card.dart
        └── tax_status_card.dart
```

---

## 2. デザイントークン & カラーテーマ (`theme/app_theme.dart`)

```dart
import 'package:flutter/material.dart';

class AppColors {
  // Kurogane Obsidian Dark Palette
  static const Color obsidianNavy = Color(0xFF0B1326);
  static const Color surfaceContainerLowest = Color(0xFF060E20);
  static const Color surfaceContainerLow = Color(0xFF131B2E);
  static const Color surfaceContainer = Color(0xFF171F33);
  static const Color surfaceContainerHigh = Color(0xFF222A3D);
  static const Color surfaceContainerHighest = Color(0xFF2D3449);

  // Accents
  static const Color primaryEmerald = Color(0xFF4EDEA3);
  static const Color primaryContainer = Color(0xFF10B981);
  static const Color onPrimary = Color(0xFF003824);
  static const Color secondaryBlue = Color(0xFFC0C1FF);
  
  // Status Colors
  static const Color textPrimary = Color(0xFFDAE2FD);
  static const Color textSecondary = Color(0xFFBBCAAF);
  static const Color textTertiary = Color(0xFF86948A);
  static const Color errorRed = Color(0xFFFFB4AB);
  static const Color warningOrange = Color(0xFFFFB865);
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
      ),
    );
  }
}
```

---

## 3. データモデル (`models/stock_detail_model.dart`)

```dart
class StockDetail {
  final String code;
  final String name;
  final String market;
  final String marketSection;
  final double currentPrice;
  final double priceChange;
  final double priceChangePercent;
  final int sharesHeld;
  final double costPrice;
  final double dividendPerShare;
  final double dividendYield;
  final double yoc; // Yield On Cost
  final String nextPayoutDate;
  final String recordDate;
  final String accountType;

  const StockDetail({
    required this.code,
    required this.name,
    required this.market,
    required this.marketSection,
    required this.currentPrice,
    required this.priceChange,
    required this.priceChangePercent,
    required this.sharesHeld,
    required this.costPrice,
    required this.dividendPerShare,
    required this.dividendYield,
    required this.yoc,
    required this.nextPayoutDate,
    required this.recordDate,
    required this.accountType,
  });

  double get valuation => sharesHeld * currentPrice;
  double get totalCost => sharesHeld * costPrice;
  double get profitLoss => valuation - totalCost;
  double get profitLossPercent =>
      totalCost > 0 ? (profitLoss / totalCost) * 100 : 0.0;
  double get annualDividend => sharesHeld * dividendPerShare;
  double get monthlyDividend => annualDividend / 12;
}

class DividendHistoryItem {
  final String period;
  final double amount;
  final bool isForecast;

  const DividendHistoryItem({
    required this.period,
    required this.amount,
    this.isForecast = false,
  });
}
```

---

## 4. メイン画面実装 (`screens/stock_detail_screen.dart`)

```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/stock_detail_model.dart';
import '../theme/app_theme.dart';
import 'widgets/stock_header_card.dart';
import 'widgets/dividend_simulation_card.dart';
import 'widgets/incremental_buy_simulator.dart';
import 'widgets/dividend_history_chart.dart';
import 'widgets/comprehensive_analysis_card.dart';
import 'widgets/buy_signal_detail_card.dart';
import 'widgets/upcoming_event_card.dart';
import 'widgets/tax_status_card.dart';

class StockDetailScreen extends StatefulWidget {
  const StockDetailScreen({super.key});

  @override
  State<StockDetailScreen> createState() => _StockDetailScreenState();
}

class _StockDetailScreenState extends State<StockDetailScreen> {
  bool _isWatchlisted = true;
  int _additionalShares = 100;

  final StockDetail _stock = const StockDetail(
    code: '7203',
    name: 'トヨタ自動車',
    market: '東証PRM',
    marketSection: '輸送用機器',
    currentPrice: 2600.0,
    priceChange: 35.0,
    priceChangePercent: 1.36,
    sharesHeld: 100,
    costPrice: 2450.0,
    dividendPerShare: 72.8,
    dividendYield: 2.80,
    yoc: 2.97,
    nextPayoutDate: '12月中旬 (中間配当 ¥3,640)',
    recordDate: '3月末 / 9月末 (年2回中間・期末)',
    accountType: '新NISA 成長投資枠',
  );

  final List<DividendHistoryItem> _dividendHistory = const [
    DividendHistoryItem(period: '22.3期', amount: 52.0),
    DividendHistoryItem(period: '23.3期', amount: 60.0),
    DividendHistoryItem(period: '24.3期', amount: 65.0),
    DividendHistoryItem(period: '25.3予', amount: 72.8, isForecast: true),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.obsidianNavy,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.textPrimary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const Text(
          '銘柄情報',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
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
      bottomNavigationBar: _buildBottomActionBar(),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // 1. 銘柄名・株価・保有状況ヘッダー
          StockHeaderCard(
            stock: _stock,
            isWatchlisted: _isWatchlisted,
            onWatchlistToggle: () {
              setState(() {
                _isWatchlisted = !_isWatchlisted;
              });
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(_isWatchlisted ? 'Watchlistに登録しました' : 'Watchlistから解除しました'),
                  backgroundColor: AppColors.surfaceContainerHigh,
                  behavior: SnackBarBehavior.floating,
                ),
              );
            },
          ),
          const SizedBox(height: 16),

          // 2. 年間配当シミュレーション
          DividendSimulationCard(stock: _stock),
          const SizedBox(height: 16),

          // 3. 買い増しシミュレータ
          IncrementalBuySimulator(
            stock: _stock,
            additionalShares: _additionalShares,
            onSharesChanged: (newVal) {
              setState(() {
                _additionalShares = newVal;
              });
            },
          ),
          const SizedBox(height: 16),

          // 4. 1株配当金の推移（4期連続増配バー）
          DividendHistoryChart(history: _dividendHistory),
          const SizedBox(height: 16),

          // 5. 銘柄総合分析ステータス (健全性・買場・増配・集中リスク)
          const ComprehensiveAnalysisCard(),
          const SizedBox(height: 16),

          // 6. 買場シグナル詳細分析 & AIバリュエーション判定
          const BuySignalDetailCard(),
          const SizedBox(height: 16),

          // 7. 次回権利落ち・直近イベント
          const UpcomingEventCard(
            daysLeft: 18,
            nextAmountText: '¥3,640 想定入金',
            recordTitle: '9月末 中間配当 権利付き最終日まで',
          ),
          const SizedBox(height: 16),

          // 8. 税務ステータス
          TaxStatusCard(accountType: _stock.accountType),
          const SizedBox(height: 24),

          // 9. 保有情報編集・銘柄解除ボタン
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.textPrimary,
                    side: const BorderSide(color: AppColors.surfaceContainerHighest),
                    backgroundColor: AppColors.surfaceContainerLow,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  icon: const Icon(Icons.edit_outlined, size: 16),
                  label: const Text('保有情報を編集', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  onPressed: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.errorRed,
                    side: const BorderSide(color: AppColors.surfaceContainerHighest),
                    backgroundColor: AppColors.surfaceContainerLow,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  icon: const Icon(Icons.delete_outline, size: 16),
                  label: const Text('銘柄を解除', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  onPressed: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildBottomActionBar() {
    return Container(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 10,
        bottom: MediaQuery.of(context).padding.bottom + 10,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainerLow,
        border: Border(top: BorderSide(color: AppColors.surfaceContainerHigh)),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 1,
            child: SizedBox(
              height: 46,
              child: OutlinedButton(
                style: OutlinedButton.styleFrom(
                  backgroundColor: AppColors.surfaceContainerHigh,
                  foregroundColor: AppColors.textSecondary,
                  side: BorderSide.none,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                onPressed: () => Navigator.of(context).maybePop(),
                child: const Text('キャンセル', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 2,
            child: SizedBox(
              height: 46,
              child: ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primaryContainer,
                  foregroundColor: AppColors.onPrimary,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  elevation: 4,
                ),
                icon: const Icon(Icons.check, size: 18),
                label: const Text('保存する', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('変更内容を保存しました'),
                      backgroundColor: AppColors.surfaceContainerHigh,
                      behavior: SnackBarBehavior.floating,
                    ),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

---

## 5. ウィジェット部品実装 (`widgets/*.dart`)

### A. 銘柄ヘッダー & 保有状況カード (`widgets/stock_header_card.dart`)
```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/stock_detail_model.dart';
import '../../theme/app_theme.dart';

class StockHeaderCard extends StatelessWidget {
  final StockDetail stock;
  final bool isWatchlisted;
  final VoidCallback onWatchlistToggle;

  const StockHeaderCard({
    super.key,
    required this.stock,
    required this.isWatchlisted,
    required this.onWatchlistToggle,
  });

  @override
  Widget build(BuildContext context) {
    final currency = NumberFormat('#,###');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        stock.name,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: AppColors.textPrimary,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          stock.market,
                          style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.textSecondary),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Text(
                        stock.code,
                        style: const TextStyle(fontSize: 12, color: AppColors.textTertiary, fontFamily: 'SpaceGrotesk'),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.primaryEmerald.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          stock.accountType,
                          style: const TextStyle(fontSize: 10, color: AppColors.primaryEmerald, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              IconButton(
                style: IconButton.styleFrom(
                  backgroundColor: isWatchlisted ? AppColors.primaryEmerald.withOpacity(0.15) : AppColors.surfaceContainerHigh,
                ),
                icon: Icon(
                  isWatchlisted ? Icons.bookmark : Icons.bookmark_border,
                  color: isWatchlisted ? AppColors.primaryEmerald : AppColors.textSecondary,
                ),
                onPressed: onWatchlistToggle,
              ),
            ],
          ),
          const SizedBox(height: 14),

          // 現在株価 & 前日比
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                '¥${currency.format(stock.currentPrice)}',
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                  fontFamily: 'SpaceGrotesk',
                ),
              ),
              const SizedBox(width: 6),
              const Text('/株', style: TextStyle(fontSize: 12, color: AppColors.textTertiary)),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer.withOpacity(0.18),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.arrow_drop_up, color: AppColors.primaryEmerald, size: 16),
                    Text(
                      '+¥${stock.priceChange.toInt()} (+${stock.priceChangePercent}%)',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: AppColors.primaryEmerald,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // 2x2 保有状況タイル
          Row(
            children: [
              Expanded(
                child: _buildTile(
                  title: '保有数量',
                  value: '${stock.sharesHeld}株',
                  subtitle: '単元保有',
                  showEdit: true,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildTile(
                  title: '取得単価',
                  value: '¥${currency.format(stock.costPrice)}',
                  subtitle: '含み益圏',
                  showEdit: true,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: _buildTile(
                  title: '評価額',
                  value: '¥${currency.format(stock.valuation)}',
                  subtitle: '時価総額',
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildTile(
                  title: '含み損益',
                  value: '+¥${currency.format(stock.profitLoss)}',
                  subtitle: '(+${stock.profitLossPercent.toStringAsFixed(1)}%)',
                  isProfit: true,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 外部リサーチリンク
          Row(
            children: [
              const Text('外部リサーチ:', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
              const SizedBox(width: 8),
              _buildExternalLinkChip('Y! ファイナンス'),
              const SizedBox(width: 6),
              _buildExternalLinkChip('IR BANK'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildTile({
    required String title,
    required String value,
    required String subtitle,
    bool showEdit = false,
    bool isProfit = false,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(title, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
              if (showEdit)
                const Icon(Icons.edit_outlined, size: 12, color: AppColors.textTertiary),
            ],
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: isProfit ? AppColors.primaryEmerald : AppColors.textPrimary,
              fontFamily: 'SpaceGrotesk',
            ),
          ),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 9,
              color: isProfit ? AppColors.primaryEmerald : AppColors.textTertiary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildExternalLinkChip(String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: const BoxDecoration(color: Colors.redAccent, shape: BoxShape.circle),
          ),
          const SizedBox(width: 4),
          Text(label, style: const TextStyle(fontSize: 10, color: AppColors.textSecondary)),
          const SizedBox(width: 2),
          const Icon(Icons.open_in_new, size: 10, color: AppColors.textTertiary),
        ],
      ),
    );
  }
}
```

### B. 年間配当シミュレーションカード (`widgets/dividend_simulation_card.dart`)
```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/stock_detail_model.dart';
import '../../theme/app_theme.dart';

class DividendSimulationCard extends StatelessWidget {
  final StockDetail stock;

  const DividendSimulationCard({super.key, required this.stock});

  @override
  Widget build(BuildContext context) {
    final currency = NumberFormat('#,###');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.payments_outlined, color: AppColors.primaryEmerald, size: 18),
                  SizedBox(width: 6),
                  Text(
                    '年間配当シミュレーション',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text('年2回配当', style: TextStyle(fontSize: 9, color: AppColors.textSecondary)),
              ),
            ],
          ),
          const SizedBox(height: 12),

          Text(
            '年間受取予定額 (${stock.sharesHeld}株保有)',
            style: const TextStyle(fontSize: 10, color: AppColors.textTertiary),
          ),
          const SizedBox(height: 2),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                '¥${currency.format(stock.annualDividend)}',
                style: const TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                  color: AppColors.primaryEmerald,
                  fontFamily: 'SpaceGrotesk',
                ),
              ),
              const SizedBox(width: 6),
              Text(
                '(1株あたり ¥${stock.dividendPerShare.toStringAsFixed(1)})',
                style: const TextStyle(fontSize: 11, color: AppColors.textTertiary),
              ),
            ],
          ),
          Text(
            '月換算 約¥${currency.format(stock.monthlyDividend)} / 月',
            style: const TextStyle(fontSize: 10, color: AppColors.textSecondary),
          ),
          const SizedBox(height: 14),

          // 2カラムグリッド：現在利回り vs 取得ベース利回り (YOC)
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
                      const Text('配当利回り（現在）', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                      const SizedBox(height: 2),
                      Text(
                        '${stock.dividendYield.toStringAsFixed(2)}%',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                      ),
                      Text('現在株価 ¥${currency.format(stock.currentPrice)}換算', style: const TextStyle(fontSize: 8, color: AppColors.textTertiary)),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 8),
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
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: const [
                          Text('取得ベース利回り', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                          Icon(Icons.info_outline, size: 10, color: AppColors.textTertiary),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${stock.yoc.toStringAsFixed(2)}% YOC',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                      ),
                      Text('取得単価 ¥${currency.format(stock.costPrice)}換算', style: const TextStyle(fontSize: 8, color: AppColors.textTertiary)),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // 次回配当予定 & 権利確定月
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('次回配当予定', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                    Text(stock.nextPayoutDate, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text('権利確定月', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                    Text(stock.recordDate, style: const TextStyle(fontSize: 10, color: AppColors.textSecondary)),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
```

### C. 買い増しシミュレータ (`widgets/incremental_buy_simulator.dart`)
```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/stock_detail_model.dart';
import '../../theme/app_theme.dart';

class IncrementalBuySimulator extends StatelessWidget {
  final StockDetail stock;
  final int additionalShares;
  final ValueChanged<int> onSharesChanged;

  const IncrementalBuySimulator({
    super.key,
    required this.stock,
    required this.additionalShares,
    required this.onSharesChanged,
  });

  @override
  Widget build(BuildContext context) {
    final currency = NumberFormat('#,###');
    final totalSharesAfter = stock.sharesHeld + additionalShares;
    final additionalCost = additionalShares * stock.currentPrice;
    final additionalDividend = additionalShares * stock.dividendPerShare;
    final totalDividendAfter = totalSharesAfter * stock.dividendPerShare;
    final additionalMonthly = additionalDividend / 12;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.calculate_outlined, color: AppColors.primaryEmerald, size: 18),
                  SizedBox(width: 6),
                  Text(
                    '買い増しシミュレータ',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer.withOpacity(0.18),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '+$additionalShares株 (合計$totalSharesAfter株)',
                  style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 買い増しインパクト比較カード (Before vs After)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('現在保有 (${stock.sharesHeld}株)', style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                      const SizedBox(height: 2),
                      Text(
                        '¥${currency.format(stock.annualDividend)}/年',
                        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                      ),
                      Text('月 ¥${currency.format(stock.monthlyDividend)} / 月', style: const TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                    ],
                  ),
                ),
                const Icon(Icons.arrow_forward, size: 16, color: AppColors.primaryEmerald),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                            decoration: BoxDecoration(color: AppColors.primaryEmerald, borderRadius: BorderRadius.circular(4)),
                            child: const Text('AFTER', style: TextStyle(fontSize: 8, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                          ),
                          const SizedBox(width: 4),
                          Text('シミュレーション ($totalSharesAfter株)', style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '¥${currency.format(totalDividendAfter)}/年',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                      ),
                      Text('月 約¥${currency.format(totalDividendAfter / 12)} / 月', style: const TextStyle(fontSize: 9, color: AppColors.primaryEmerald)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // 追加買増株数ステッパー
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton.filled(
                style: IconButton.styleFrom(backgroundColor: AppColors.surfaceContainerHigh),
                icon: const Icon(Icons.remove, size: 18),
                onPressed: () {
                  if (additionalShares >= 100) onSharesChanged(additionalShares - 100);
                },
              ),
              Text(
                '$additionalShares 株',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppColors.textPrimary, fontFamily: 'SpaceGrotesk'),
              ),
              IconButton.filled(
                style: IconButton.styleFrom(backgroundColor: AppColors.surfaceContainerHigh),
                icon: const Icon(Icons.add, size: 18),
                onPressed: () {
                  onSharesChanged(additionalShares + 100);
                },
              ),
            ],
          ),
          const SizedBox(height: 12),

          // プリセットチップ
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [0, 100, 250, 500].map((preset) {
              final isSelected = additionalShares == preset;
              return GestureDetector(
                onTap: () => onSharesChanged(preset),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(
                    color: isSelected ? AppColors.primaryContainer : AppColors.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    preset == 0 ? '現状維持' : '+$preset株',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      color: isSelected ? AppColors.onPrimary : AppColors.textSecondary,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 14),

          // 追加資金 & 増配サマリー
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('追加必要資金', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                  Text('¥${currency.format(additionalCost)}', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                  Text('年間配当増額分 +¥${currency.format(additionalDividend)}/年', style: const TextStyle(fontSize: 9, color: AppColors.primaryEmerald)),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const Text('シミュレーション年間受取', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                  Text('¥${currency.format(totalDividendAfter)}', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
                  Text('新月間不労所得 約¥${currency.format(additionalMonthly)}/月', style: const TextStyle(fontSize: 9, color: AppColors.textSecondary)),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

### D. 1株配当金の推移グラフ (`widgets/dividend_history_chart.dart`)
```dart
import 'package:flutter/material.dart';
import '../../models/stock_detail_model.dart';
import '../../theme/app_theme.dart';

class DividendHistoryChart extends StatelessWidget {
  final List<DividendHistoryItem> history;

  const DividendHistoryChart({super.key, required this.history});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.bar_chart, color: AppColors.primaryEmerald, size: 18),
                  SizedBox(width: 6),
                  Text('1株配当金の推移 (4期)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: AppColors.primaryEmerald.withOpacity(0.15), borderRadius: BorderRadius.circular(4)),
                child: const Text('連続増配', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // 4本のバーチャート
          SizedBox(
            height: 90,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: history.map((item) {
                final double maxHeight = 70.0;
                final double height = (item.amount / 72.8) * maxHeight;

                return Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Text(
                      '¥${item.amount.toStringAsFixed(item.isForecast ? 1 : 0)}',
                      style: TextStyle(
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                        color: item.isForecast ? AppColors.primaryEmerald : AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Container(
                      width: 44,
                      height: height,
                      decoration: BoxDecoration(
                        color: item.isForecast ? AppColors.primaryEmerald : AppColors.secondaryBlue.withOpacity(0.65),
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      item.period,
                      style: TextStyle(
                        fontSize: 10,
                        color: item.isForecast ? AppColors.primaryEmerald : AppColors.textTertiary,
                        fontWeight: item.isForecast ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                  ],
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('4期増配率: +40.0%', style: TextStyle(fontSize: 10, color: AppColors.primaryEmerald, fontWeight: FontWeight.bold)),
              Text('配当性向: 約35.2%', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
            ],
          ),
        ],
      ),
    );
  }
}
```

### E. 銘柄総合分析ステータス (`widgets/comprehensive_analysis_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class ComprehensiveAnalysisCard extends StatelessWidget {
  const ComprehensiveAnalysisCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('銘柄総合分析ステータス', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
              Text('タップで切替', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
            ],
          ),
          const SizedBox(height: 12),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: 2.2,
            crossAxisSpacing: 8,
            mainAxisSpacing: 8,
            children: [
              _buildMetricTile('① 健全性', 'A判定 35.2%', '極めて安全', Icons.verified_user_outlined, AppColors.primaryEmerald),
              _buildMetricTile('② 買場シグナル', '検討圏 2.80%', '超割安ゾーン', Icons.lightbulb_outline, AppColors.primaryEmerald),
              _buildMetricTile('③ 増配耐性', '10期連続 +8.5%', '非減配25年', Icons.military_tech_outlined, AppColors.primaryEmerald),
              _buildMetricTile('④ 集中リスク', 'やや高め 18.2%', '上限目安10%', Icons.pie_chart_outline, AppColors.warningOrange),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMetricTile(String title, String mainVal, String subVal, IconData icon, Color accentColor) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(title, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                Text(mainVal, style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: accentColor)),
                Text(subVal, style: const TextStyle(fontSize: 9, color: AppColors.textTertiary)),
              ],
            ),
          ),
          Icon(icon, size: 18, color: accentColor),
        ],
      ),
    );
  }
}
```

### F. 買場シグナル詳細 & AIバリュエーション (`widgets/buy_signal_detail_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class BuySignalDetailCard extends StatelessWidget {
  const BuySignalDetailCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('買場シグナル詳細分析', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: AppColors.primaryEmerald.withOpacity(0.18), borderRadius: BorderRadius.circular(6)),
                child: const Text('超割安ゾーン (検討圏)', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 配当利回りレンジ判定ゲージ
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('配当利回りレンジ判定', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
              Text('過去5年平均超え 2.80%', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              value: 0.65,
              backgroundColor: AppColors.surfaceContainerHighest,
              valueColor: const AlwaysStoppedAnimation<Color>(AppColors.primaryEmerald),
              minHeight: 5,
            ),
          ),
          const SizedBox(height: 4),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('安値圏 2.10%', style: TextStyle(fontSize: 8, color: AppColors.textTertiary)),
              Text('5年平均 2.45%', style: TextStyle(fontSize: 8, color: AppColors.textTertiary)),
              Text('高値圏 3.20%', style: TextStyle(fontSize: 8, color: AppColors.textTertiary)),
            ],
          ),
          const SizedBox(height: 14),

          // PER & PBR
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(10)),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: const [
                      Text('PER (株価収益率)', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                      Text('10.4倍', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                      Text('市場平均比で割安', style: TextStyle(fontSize: 8, color: AppColors.primaryEmerald)),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: AppColors.surfaceContainer, borderRadius: BorderRadius.circular(10)),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: const [
                      Text('PBR (純資産倍率)', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                      Text('1.12倍', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                      Text('資本効率適正圏', style: TextStyle(fontSize: 8, color: AppColors.textSecondary)),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // AIバリュエーション診断吹き出し
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.primaryEmerald.withOpacity(0.3)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  '絶好の押し目買い判定 (割安度スコア 88 / 100)',
                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                ),
                SizedBox(height: 4),
                Text(
                  'PER 10.4倍・PBR 1.12倍と、日経平均平均（PER 15倍・PBR 1.4倍）を大幅に下回る優良バリュー水準。高収益性とキャッシュ創出力に対して割安に放置されており、配当再投資や中期的な買い増しに最適な好機シグナルです。',
                  style: TextStyle(fontSize: 10, color: AppColors.textSecondary, height: 1.4),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
```

### G. 直近イベントカード (`widgets/upcoming_event_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class UpcomingEventCard extends StatelessWidget {
  final int daysLeft;
  final String nextAmountText;
  final String recordTitle;

  const UpcomingEventCard({
    super.key,
    required this.daysLeft,
    required this.nextAmountText,
    required this.recordTitle,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.event, color: AppColors.primaryEmerald, size: 18),
                  SizedBox(width: 6),
                  Text('次回権利落ち・直近イベント', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: AppColors.primaryEmerald.withOpacity(0.15), borderRadius: BorderRadius.circular(4)),
                child: const Text('通知設定中', style: TextStyle(fontSize: 9, color: AppColors.primaryEmerald, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(recordTitle, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                      const SizedBox(height: 4),
                      Text(nextAmountText, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    'あと $daysLeft 日',
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
```

### H. 税務ステータスカード (`widgets/tax_status_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class TaxStatusCard extends StatelessWidget {
  final String accountType;

  const TaxStatusCard({super.key, required this.accountType});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: AppColors.primaryEmerald.withOpacity(0.15),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.shield_outlined, color: AppColors.primaryEmerald, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(accountType, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                      decoration: BoxDecoration(color: AppColors.primaryEmerald, borderRadius: BorderRadius.circular(4)),
                      child: const Text('税率 0%', style: TextStyle(fontSize: 8, fontWeight: FontWeight.bold, color: AppColors.onPrimary)),
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                const Text(
                  '課税口座時の税額（20.315% = 約¥1,478/年）が全額非課税。受取額がそのまま手取りになります。',
                  style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
```
