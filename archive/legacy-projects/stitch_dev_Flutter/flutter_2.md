# 設定・データ管理画面 Flutter (Dart) 実装仕様書 & フルソースコード

選択された「設定・データ管理（案1：タブ切替＆証券会社選択型）」のUI、ダークモード配色（Obsidian Navy & Emerald Accent）、インポート/エクスポート切替、証券会社セレクター、ドラッグ＆ドロップ風CSVアップロードエリア、各種トグルスイッチ、アクセントカラーパレット、配当利回り計算ロジック選択、および各種設定項目を忠実に再現したFlutterコード一式です。

---

## 1. ディレクトリ構成推奨

```text
lib/
├── main.dart
├── theme/
│   └── app_theme.dart
└── screens/
    ├── settings_screen.dart
    └── widgets/
        ├── settings_section_header.dart
        ├── csv_data_management_card.dart
        ├── theme_appearance_card.dart
        ├── calculation_settings_card.dart
        ├── notification_settings_card.dart
        └── security_support_card.dart
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

  // Accents
  static const Color primaryEmerald = Color(0xFF4EDEA3);
  static const Color primaryContainer = Color(0xFF10B981);
  static const Color onPrimary = Color(0xFF003824);
  static const Color secondaryBlue = Color(0xFFC0C1FF);
  
  // Theme Color Options
  static const Color accentCyan = Color(0xFF22D3EE);
  static const Color accentBlue = Color(0xFF3B82F6);
  static const Color accentAmber = Color(0xFFF59E0B);
  static const Color accentPurple = Color(0xFF8B5CF6);

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

## 3. 設定・データ管理 メイン画面 (`screens/settings_screen.dart`)

```dart
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import 'widgets/settings_section_header.dart';
import 'widgets/csv_data_management_card.dart';
import 'widgets/theme_appearance_card.dart';
import 'widgets/calculation_settings_card.dart';
import 'widgets/notification_settings_card.dart';
import 'widgets/security_support_card.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  // --- 状態管理ステート ---
  // データ管理
  bool _isImportMode = true;
  String _selectedBroker = 'SBI証券';

  // 外観・テーマ
  String _colorTheme = 'ダーク';
  Color _selectedAccent = AppColors.primaryEmerald;
  bool _oledDark = false;

  // 表示・計算カスタマイズ
  bool _isMarketPriceBase = true; // true: 評価額基準, false: 取得額基準(YOC)
  bool _taxCalculation = true; // 20.315%控除
  bool _usWithholdingTax = true; // 米国株10%控除
  String _targetYield = '4.0%(標準)';
  double _otherAggregationThreshold = 5.0; // 5%

  // 通知・リマインダー
  bool _notifyDividendDate = true;
  bool _notifyBuySignal = true;

  // セキュリティ
  bool _biometrics = true;
  bool _maskOnLaunch = false;

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
          '設定・データ管理',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: false,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // 1. データ管理＆バックアップ
          const SettingsSectionHeader(
            icon: Icons.inventory_2_outlined,
            title: 'データ管理＆バックアップ',
          ),
          const SizedBox(height: 8),
          CsvDataManagementCard(
            isImportMode: _isImportMode,
            onModeChanged: (val) => setState(() => _isImportMode = val),
            selectedBroker: _selectedBroker,
            onBrokerChanged: (val) => setState(() => _selectedBroker = val),
            onSelectFile: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('ファイルピッカーを起動します'),
                  backgroundColor: AppColors.surfaceContainerHigh,
                  behavior: SnackBarBehavior.floating,
                ),
              );
            },
          ),
          const SizedBox(height: 20),

          // 2. 外観・テーマ設定
          const SettingsSectionHeader(
            icon: Icons.palette_outlined,
            title: '外観・テーマ設定',
          ),
          const SizedBox(height: 8),
          ThemeAppearanceCard(
            colorTheme: _colorTheme,
            onThemeChanged: (val) => setState(() => _colorTheme = val),
            selectedAccent: _selectedAccent,
            onAccentChanged: (val) => setState(() => _selectedAccent = val),
            oledDark: _oledDark,
            onOledDarkChanged: (val) => setState(() => _oledDark = val),
          ),
          const SizedBox(height: 20),

          // 3. 表示・計算カスタマイズ
          const SettingsSectionHeader(
            icon: Icons.tune,
            title: '表示・計算カスタマイズ',
          ),
          const SizedBox(height: 8),
          CalculationSettingsCard(
            isMarketPriceBase: _isMarketPriceBase,
            onYieldBaseChanged: (val) => setState(() => _isMarketPriceBase = val),
            taxCalculation: _taxCalculation,
            onTaxCalculationChanged: (val) => setState(() => _taxCalculation = val),
            usWithholdingTax: _usWithholdingTax,
            onUsWithholdingTaxChanged: (val) => setState(() => _usWithholdingTax = val),
            targetYield: _targetYield,
            onTargetYieldChanged: (val) => setState(() => _targetYield = val),
            otherThreshold: _otherAggregationThreshold,
            onThresholdChanged: (val) => setState(() => _otherAggregationThreshold = val),
          ),
          const SizedBox(height: 20),

          // 4. 通知・リマインダー設定
          const SettingsSectionHeader(
            icon: Icons.notifications_none,
            title: '通知・リマインダー設定',
          ),
          const SizedBox(height: 8),
          NotificationSettingsCard(
            notifyDividendDate: _notifyDividendDate,
            onNotifyDividendDateChanged: (val) => setState(() => _notifyDividendDate = val),
            notifyBuySignal: _notifyBuySignal,
            onNotifyBuySignalChanged: (val) => setState(() => _notifyBuySignal = val),
          ),
          const SizedBox(height: 20),

          // 5. セキュリティ＆アプリ情報
          const SettingsSectionHeader(
            icon: Icons.shield_outlined,
            title: 'セキュリティ＆プライバシー',
          ),
          const SizedBox(height: 8),
          SecuritySupportCard(
            biometrics: _biometrics,
            onBiometricsChanged: (val) => setState(() => _biometrics = val),
            maskOnLaunch: _maskOnLaunch,
            onMaskOnLaunchChanged: (val) => setState(() => _maskOnLaunch = val),
            onResetData: () {
              _showResetConfirmDialog(context);
            },
          ),
          const SizedBox(height: 40),

          // コピーライトフッター
          Center(
            child: Column(
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: const [
                    Icon(Icons.lock_outline, size: 12, color: AppColors.primaryEmerald),
                    SizedBox(width: 4),
                    Text(
                      '金融データは端末内暗号化保存されています',
                      style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                const Text(
                  '© 2025 Kurogane Dividend System',
                  style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  void _showResetConfirmDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surfaceContainerLow,
        title: const Text('ローカルデータの完全消去', style: TextStyle(color: AppColors.textPrimary)),
        content: const Text(
          '端末内に保存されている保有銘柄および設定データがすべて削除されます。この操作は取り消せません。実行しますか？',
          style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('キャンセル', style: TextStyle(color: AppColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.errorRed),
            onPressed: () {
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('データを初期化しました'),
                  backgroundColor: AppColors.surfaceContainerHigh,
                ),
              );
            },
            child: const Text('初期化実行', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
```

---

## 4. サブウィジェット部品 (`screens/widgets/*.dart`)

### A. セクション見出し (`widgets/settings_section_header.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class SettingsSectionHeader extends StatelessWidget {
  final IconData icon;
  final String title;

  const SettingsSectionHeader({
    super.key,
    required this.icon,
    required this.title,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: AppColors.primaryEmerald),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
      ],
    );
  }
}
```

### B. CSVデータ管理カード (`widgets/csv_data_management_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class CsvDataManagementCard extends StatelessWidget {
  final bool isImportMode;
  final ValueChanged<bool> onModeChanged;
  final String selectedBroker;
  final ValueChanged<String> onBrokerChanged;
  final VoidCallback onSelectFile;

  const CsvDataManagementCard({
    super.key,
    required this.isImportMode,
    required this.onModeChanged,
    required this.selectedBroker,
    required this.onBrokerChanged,
    required this.onSelectFile,
  });

  @override
  Widget build(BuildContext context) {
    final brokers = [
      {'name': 'SBI証券', 'desc': 'CSV読込'},
      {'name': '楽天証券', 'desc': 'CSV読込'},
      {'name': 'マネックス', 'desc': 'CSV読込'},
      {'name': 'moomoo', 'desc': 'CSV読込'},
    ];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // インポート / エクスポート セグメントタブ
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              children: [
                Expanded(
                  child: InkWell(
                    onTap: () => onModeChanged(true),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: isImportMode ? AppColors.surfaceContainerHigh : Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.file_download_outlined,
                            size: 16,
                            color: isImportMode ? AppColors.primaryEmerald : AppColors.textTertiary,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            'インポート (取込)',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: isImportMode ? FontWeight.bold : FontWeight.normal,
                              color: isImportMode ? AppColors.textPrimary : AppColors.textTertiary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                Expanded(
                  child: InkWell(
                    onTap: () => onModeChanged(false),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: !isImportMode ? AppColors.surfaceContainerHigh : Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.file_upload_outlined,
                            size: 16,
                            color: !isImportMode ? AppColors.primaryEmerald : AppColors.textTertiary,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            'エクスポート (保存)',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: !isImportMode ? FontWeight.bold : FontWeight.normal,
                              color: !isImportMode ? AppColors.textPrimary : AppColors.textTertiary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 対応証券口座フォーマット選択
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text(
                '対応証券口座フォーマット',
                style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
              ),
              Row(
                children: [
                  Icon(Icons.check_circle_outline, size: 14, color: AppColors.primaryEmerald),
                  SizedBox(width: 4),
                  Text(
                    '自動フォーマット判定',
                    style: TextStyle(fontSize: 10, color: AppColors.primaryEmerald, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: brokers.map((b) {
              final isSelected = selectedBroker == b['name'];
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: InkWell(
                    onTap: () => onBrokerChanged(b['name']!),
                    borderRadius: BorderRadius.circular(8),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: isSelected ? AppColors.surfaceContainerHigh : AppColors.surfaceContainer,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: isSelected ? AppColors.primaryEmerald.withOpacity(0.5) : Colors.transparent,
                          width: 1,
                        ),
                      ),
                      child: Column(
                        children: [
                          Text(
                            b['name']!,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: isSelected ? AppColors.primaryEmerald : AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            b['desc']!,
                            style: const TextStyle(fontSize: 9, color: AppColors.textTertiary),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 16),

          // ドラッグ＆ドロップ風ファイルアップロード枠
          InkWell(
            onTap: onSelectFile,
            borderRadius: BorderRadius.circular(12),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer.withOpacity(0.5),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: AppColors.primaryEmerald.withOpacity(0.3),
                  width: 1.2,
                  style: BorderStyle.solid,
                ),
              ),
              child: Column(
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: AppColors.primaryEmerald.withOpacity(0.12),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.cloud_upload_outlined, color: AppColors.primaryEmerald, size: 26),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'CSVファイルを選択 または ドロップ',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                  ),
                  const SizedBox(height: 4),
                  const Text(
                    '.csv形式 / UTF-8・Shift-JIS自動識別（最大 10MB）',
                    style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                  ),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: const [
                        Icon(Icons.folder_open, size: 14, color: AppColors.primaryEmerald),
                        SizedBox(width: 6),
                        Text(
                          '端末内のファイルを参照',
                          style: TextStyle(fontSize: 11, color: AppColors.textPrimary, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 前回インポート情報
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: const [
                  Icon(Icons.history, size: 14, color: AppColors.textTertiary),
                  SizedBox(width: 4),
                  Text('前回インポート:', style: TextStyle(fontSize: 11, color: AppColors.textTertiary)),
                ],
              ),
              Row(
                children: [
                  const Text('2024/10/24 15:30', style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
                  const SizedBox(width: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text('SBI証券 48銘柄', style: TextStyle(fontSize: 10, color: AppColors.primaryEmerald)),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),

          // ローカル暗号化の安心宣言
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Icon(Icons.verified_user_outlined, size: 16, color: AppColors.primaryEmerald),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '安心のローカル暗号化処理: CSV/JSONファイルは端末内でのみ処理されます。外部サーバーへの通信やデータ蓄積は一切行われません。',
                    style: TextStyle(fontSize: 10, color: AppColors.textSecondary, height: 1.4),
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

### C. 外観・テーマ設定カード (`widgets/theme_appearance_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class ThemeAppearanceCard extends StatelessWidget {
  final String colorTheme;
  final ValueChanged<String> onThemeChanged;
  final Color selectedAccent;
  final ValueChanged<Color> onAccentChanged;
  final bool oledDark;
  final ValueChanged<bool> onOledDarkChanged;

  const ThemeAppearanceCard({
    super.key,
    required this.colorTheme,
    required this.onThemeChanged,
    required this.selectedAccent,
    required this.onAccentChanged,
    required this.oledDark,
    required this.onOledDarkChanged,
  });

  @override
  Widget build(BuildContext context) {
    final themes = [
      {'name': 'ダーク', 'icon': Icons.dark_mode_outlined},
      {'name': 'ライト', 'icon': Icons.light_mode_outlined},
      {'name': 'システム', 'icon': Icons.settings_suggest_outlined},
    ];

    final accents = [
      AppColors.primaryEmerald,
      AppColors.accentCyan,
      AppColors.accentBlue,
      AppColors.accentAmber,
      AppColors.accentPurple,
    ];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('カラーテーマ', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Text('ダーク適用中', style: TextStyle(fontSize: 10, color: AppColors.primaryEmerald)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: themes.map((t) {
              final isSelected = colorTheme == t['name'];
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: InkWell(
                    onTap: () => onThemeChanged(t['name'] as String),
                    borderRadius: BorderRadius.circular(10),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        color: isSelected ? AppColors.surfaceContainerHigh : AppColors.surfaceContainer,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: isSelected ? AppColors.primaryEmerald : Colors.transparent,
                        ),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(t['icon'] as IconData, size: 15, color: isSelected ? AppColors.primaryEmerald : AppColors.textTertiary),
                          const SizedBox(width: 6),
                          Text(
                            t['name'] as String,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: isSelected ? AppColors.textPrimary : AppColors.textTertiary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 18),

          // アクセントカラーパレット
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('アクセントカラー', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
              Text('エメラルド', style: TextStyle(fontSize: 11, color: AppColors.primaryEmerald)),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: accents.map((c) {
              final isSelected = selectedAccent == c;
              return GestureDetector(
                onTap: () => onAccentChanged(c),
                child: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: c,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isSelected ? Colors.white : Colors.transparent,
                      width: 2.5,
                    ),
                    boxShadow: isSelected
                        ? [BoxShadow(color: c.withOpacity(0.5), blurRadius: 8, spreadRadius: 1)]
                        : [],
                  ),
                  child: isSelected
                      ? const Icon(Icons.check, size: 20, color: Colors.black)
                      : null,
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 18),

          // 有機ELダーク (完全ブラック)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('有機ELダーク（完全ブラック）', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('ピクセル発光を最小化し黒色消費電力を抑制', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: oledDark,
                activeColor: AppColors.primaryEmerald,
                onChanged: onOledDarkChanged,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

### D. 表示・計算カスタマイズカード (`widgets/calculation_settings_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class CalculationSettingsCard extends StatelessWidget {
  final bool isMarketPriceBase;
  final ValueChanged<bool> onYieldBaseChanged;
  final bool taxCalculation;
  final ValueChanged<bool> onTaxCalculationChanged;
  final bool usWithholdingTax;
  final ValueChanged<bool> onUsWithholdingTaxChanged;
  final String targetYield;
  final ValueChanged<String> onTargetYieldChanged;
  final double otherThreshold;
  final ValueChanged<double> onThresholdChanged;

  const CalculationSettingsCard({
    super.key,
    required this.isMarketPriceBase,
    required this.onYieldBaseChanged,
    required this.taxCalculation,
    required this.onTaxCalculationChanged,
    required this.usWithholdingTax,
    required this.onUsWithholdingTaxChanged,
    required this.targetYield,
    required this.onTargetYieldChanged,
    required this.otherThreshold,
    required this.onThresholdChanged,
  });

  @override
  Widget build(BuildContext context) {
    final yieldTargets = ['3.5%(堅実)', '4.0%(標準)', '4.5%(積極)'];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('配当利回りの計算基準', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
              Text('利回り計算ロジック', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: InkWell(
                  onTap: () => onYieldBaseChanged(true),
                  child: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: isMarketPriceBase ? AppColors.surfaceContainerHigh : AppColors.surfaceContainer,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: isMarketPriceBase ? AppColors.primaryEmerald : Colors.transparent,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: const [
                        Text('評価額基準', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                        SizedBox(height: 2),
                        Text('現在値ベース', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: InkWell(
                  onTap: () => onYieldBaseChanged(false),
                  child: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: !isMarketPriceBase ? AppColors.surfaceContainerHigh : AppColors.surfaceContainer,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: !isMarketPriceBase ? AppColors.primaryEmerald : Colors.transparent,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: const [
                        Text('取得額基準', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                        SizedBox(height: 2),
                        Text('買付単価 (YOC)', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // 税金計算設定 (20.315%)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('税金計算設定', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('税引後表示 (国内税 20.315%を控除)', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: taxCalculation,
                activeColor: AppColors.primaryEmerald,
                onChanged: onTaxCalculationChanged,
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 米国株 現地源泉徴収控除 (10%)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('米国株 現地源泉徴収控除 (10%)', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('外国税額控除前の手取り概算に反映', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: usWithholdingTax,
                activeColor: AppColors.primaryEmerald,
                onChanged: onUsWithholdingTaxChanged,
              ),
            ],
          ),
          const SizedBox(height: 16),

          // 目標利回り (ロードマップ試算)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: const [
              Text('目標利回り (ロードマップ試算)', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
              Text('シミュレーション基準 4.0%', style: TextStyle(fontSize: 10, color: AppColors.primaryEmerald)),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: yieldTargets.map((yt) {
              final isSelected = targetYield == yt;
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: InkWell(
                    onTap: () => onTargetYieldChanged(yt),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: isSelected ? AppColors.primaryContainer : AppColors.surfaceContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Center(
                        child: Text(
                          yt,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: isSelected ? AppColors.onPrimary : AppColors.textSecondary,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 16),

          // 構成比「その他」集約閾値スライダー
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('構成比「その他」集約閾値', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
              Text('${otherThreshold.toStringAsFixed(0)}%', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald)),
            ],
          ),
          Slider(
            value: otherThreshold,
            min: 3.0,
            max: 10.0,
            divisions: 7,
            activeColor: AppColors.primaryEmerald,
            inactiveColor: AppColors.surfaceContainerHighest,
            onChanged: onThresholdChanged,
          ),
        ],
      ),
    );
  }
}
```

### E. 通知・リマインダー設定カード (`widgets/notification_settings_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class NotificationSettingsCard extends StatelessWidget {
  final bool notifyDividendDate;
  final ValueChanged<bool> onNotifyDividendDateChanged;
  final bool notifyBuySignal;
  final ValueChanged<bool> onNotifyBuySignalChanged;

  const NotificationSettingsCard({
    super.key,
    required this.notifyDividendDate,
    required this.onNotifyDividendDateChanged,
    required this.notifyBuySignal,
    required this.onNotifyBuySignalChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('配当金の権利落ち日・入金通知', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('権利付最終日および振込予定日をプッシュ通知', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: notifyDividendDate,
                activeColor: AppColors.primaryEmerald,
                onChanged: onNotifyDividendDateChanged,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('買場シグナル・株価アラート', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('利回り4.5%突破時や年初来安値更新時', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: notifyBuySignal,
                activeColor: AppColors.primaryEmerald,
                onChanged: onNotifyBuySignalChanged,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('通知時間帯の指定', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('市場寄り前・引け後まとめ', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainer,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text('朝 08:30 / 夕 17:30', style: TextStyle(fontSize: 11, color: AppColors.primaryEmerald, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

### F. セキュリティ＆サポートカード (`widgets/security_support_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class SecuritySupportCard extends StatelessWidget {
  final bool biometrics;
  final ValueChanged<bool> onBiometricsChanged;
  final bool maskOnLaunch;
  final ValueChanged<bool> onMaskOnLaunchChanged;
  final VoidCallback onResetData;

  const SecuritySupportCard({
    super.key,
    required this.biometrics,
    required this.onBiometricsChanged,
    required this.maskOnLaunch,
    required this.onMaskOnLaunchChanged,
    required this.onResetData,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('生体認証 (Face ID / 指紋認証)', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('アプリ起動時のロック解除', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: biometrics,
                activeColor: AppColors.primaryEmerald,
                onChanged: onBiometricsChanged,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('起動時の金額伏字マスク', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('電車内など外出時の画面閲覧用', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              Switch(
                value: maskOnLaunch,
                activeColor: AppColors.primaryEmerald,
                onChanged: onMaskOnLaunchChanged,
              ),
            ],
          ),
          const SizedBox(height: 14),

          // ローカルデータ完全消去
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('ローカルデータ完全消去', style: TextStyle(fontSize: 12, color: AppColors.textPrimary)),
                  SizedBox(height: 2),
                  Text('端末内の保有・設定データをすべて削除', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                ],
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.surfaceContainerHigh,
                  foregroundColor: AppColors.errorRed,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                ),
                onPressed: onResetData,
                child: const Text('初期化', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
          const Divider(height: 28, color: AppColors.surfaceContainerHighest),

          // アプリ情報リンク群
          _buildSupportTile('お問い合わせ・フィードバック', Icons.mail_outline, () {}),
          _buildSupportTile('利用規約＆プライバシーポリシー', Icons.description_outlined, () {}),
          _buildSupportTile('アプリをレビューで応援', Icons.star_outline, () {}),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: const [
                Text('バージョン', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                Text('v1.2.0 (Build 248)', style: TextStyle(fontSize: 12, color: AppColors.textTertiary)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSupportTile(String title, IconData icon, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Icon(icon, size: 16, color: AppColors.textSecondary),
                const SizedBox(width: 8),
                Text(title, style: const TextStyle(fontSize: 12, color: AppColors.textPrimary)),
              ],
            ),
            const Icon(Icons.chevron_right, size: 16, color: AppColors.textTertiary),
          ],
        ),
      ),
    );
  }
}
```
