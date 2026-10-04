# 不労所得ロードマップ画面 Flutter (Dart) 実装仕様書 & フルソースコード

選択された「不労所得ロードマップ（Roadmap Quest / 不労所得RPGステージ攻略）」のUI設計、ダークモード配色（Obsidian Navy & Neon Emerald）、3大ステージ・レベル帯タブ、現在挑戦中クエストステータスカード、および全10マイルストーン・クエストマップ（展開アコーディオン、進捗ゲージ、達成バッジ）を忠実に再現したFlutterコード一式です。

---

## 1. ディレクトリ構造推奨

```text
lib/
├── main.dart
├── theme/
│   └── app_theme.dart
├── models/
│   └── roadmap_quest.dart
└── screens/
    ├── roadmap_screen.dart
    └── widgets/
        ├── current_status_hero_card.dart
        ├── stage_level_tab_bar.dart
        ├── quest_milestone_tile.dart
        └── milestone_share_bottom_sheet.dart
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

  // Accents & Brand
  static const Color primaryEmerald = Color(0xFF4EDEA3);
  static const Color primaryContainer = Color(0xFF10B981);
  static const Color onPrimary = Color(0xFF003824);
  static const Color secondaryBlue = Color(0xFFC0C1FF);
  static const Color accentAmber = Color(0xFFFFC107);
  static const Color accentCyan = Color(0xFF38BDF8);

  // Text Colors
  static const Color textPrimary = Color(0xFFDAE2FD);
  static const Color textSecondary = Color(0xFFBBCAAF);
  static const Color textTertiary = Color(0xFF86948A);
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

## 3. データモデル (`models/roadmap_quest.dart`)

```dart
enum QuestStage {
  level1, // 生活インフラ自給期
  level2, // 生活費自給・ミニFIRE期
  level3, // 完全経済的自由・FIRE期
}

enum QuestStatus {
  completed,
  inProgress,
  locked,
}

class RoadmapMilestone {
  final int step;
  final String title;
  final int targetAmountMonthly;
  final String description;
  final IconData icon;
  final QuestStage stage;
  final QuestStatus status;
  final double currentProgress; // 0.0 - 100.0
  final int? remainingAmountMonthly;

  const RoadmapMilestone({
    required this.step,
    required this.title,
    required this.targetAmountMonthly,
    required this.description,
    required this.icon,
    required this.stage,
    required this.status,
    required this.currentProgress,
    this.remainingAmountMonthly,
  });

  bool get isCompleted => status == QuestStatus.completed;
  bool get isInProgress => status == QuestStatus.inProgress;
  bool get isLocked => status == QuestStatus.locked;
}
```

---

## 4. メイン画面実装 (`screens/roadmap_screen.dart`)

```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/roadmap_quest.dart';
import '../theme/app_theme.dart';
import 'widgets/current_status_hero_card.dart';
import 'widgets/stage_level_tab_bar.dart';
import 'widgets/quest_milestone_tile.dart';

class RoadmapScreen extends StatefulWidget {
  const RoadmapScreen({super.key});

  @override
  State<RoadmapScreen> createState() => _RoadmapScreenState();
}

class _RoadmapScreenState extends State<RoadmapScreen> {
  int _currentNavIndex = 2; // ロードマップ
  int _selectedStageIndex = 1; // 0: LV1, 1: LV2, 2: LV3

  // 10マイルストーンのマスターデータ
  final List<RoadmapMilestone> _milestones = const [
    // --- LEVEL 1: 生活インフラ自給期 ---
    RoadmapMilestone(
      step: 1,
      title: 'STEP 1: 月3,000円',
      targetAmountMonthly: 3000,
      description: '電気代の基本料金・カフェ代を自給',
      icon: Icons.local_cafe_outlined,
      stage: QuestStage.level1,
      status: QuestStatus.completed,
      currentProgress: 100.0,
    ),
    RoadmapMilestone(
      step: 2,
      title: 'STEP 2: 月5,000円',
      targetAmountMonthly: 5000,
      description: 'スマホ通信費＆サブスク代を自給',
      icon: Icons.phone_iphone_outlined,
      stage: QuestStage.level1,
      status: QuestStatus.completed,
      currentProgress: 100.0,
    ),
    RoadmapMilestone(
      step: 3,
      title: 'STEP 3: 月10,000円',
      targetAmountMonthly: 10000,
      description: '水道光熱費の基本料金をカバー',
      icon: Icons.lightbulb_outline,
      stage: QuestStage.level1,
      status: QuestStatus.completed,
      currentProgress: 100.0,
    ),
    RoadmapMilestone(
      step: 4,
      title: 'STEP 4: 月20,000円',
      targetAmountMonthly: 20000,
      description: '週末外食・自己投資書籍費',
      icon: Icons.restaurant_outlined,
      stage: QuestStage.level1,
      status: QuestStatus.completed,
      currentProgress: 100.0,
    ),
    RoadmapMilestone(
      step: 5,
      title: 'STEP 5: 月30,000円',
      targetAmountMonthly: 30000,
      description: '水道光熱費の全額自給',
      icon: Icons.bolt_outlined,
      stage: QuestStage.level1,
      status: QuestStatus.completed,
      currentProgress: 100.0,
    ),

    // --- LEVEL 2: 生活費自給・ミニFIRE期 ---
    RoadmapMilestone(
      step: 6,
      title: 'STEP 6: 月50,000円',
      targetAmountMonthly: 5000,
      description: '食費・日用品の全額自給 (現在挑戦中)',
      icon: Icons.shopping_cart_outlined,
      stage: QuestStage.level2,
      status: QuestStatus.inProgress,
      currentProgress: 62.4,
      remainingAmountMonthly: 18792,
    ),
    RoadmapMilestone(
      step: 7,
      title: 'STEP 7: 月100,000円',
      targetAmountMonthly: 100000,
      description: '住居費・家賃の完全カバー',
      icon: Icons.home_outlined,
      stage: QuestStage.level2,
      status: QuestStatus.locked,
      currentProgress: 31.2,
      remainingAmountMonthly: 68792,
    ),

    // --- LEVEL 3: 完全経済的自由・FIRE期 ---
    RoadmapMilestone(
      step: 8,
      title: 'STEP 8: 月200,000円',
      targetAmountMonthly: 200000,
      description: '標準的生活費の全額自立 (Lean FIRE水準)',
      icon: Icons.sailing_outlined,
      stage: QuestStage.level3,
      status: QuestStatus.locked,
      currentProgress: 15.6,
      remainingAmountMonthly: 168792,
    ),
    RoadmapMilestone(
      step: 9,
      title: 'STEP 9: 月300,000円',
      targetAmountMonthly: 300000,
      description: 'ゆとりある生活 (Full FIRE)',
      icon: Icons.flight_takeoff_outlined,
      stage: QuestStage.level3,
      status: QuestStatus.locked,
      currentProgress: 10.4,
      remainingAmountMonthly: 268792,
    ),
    RoadmapMilestone(
      step: 10,
      title: 'STEP 10: 月500,000円+',
      targetAmountMonthly: 500000,
      description: '経済的自由・資産自動増殖＆完全自由',
      icon: Icons.military_tech_outlined,
      stage: QuestStage.level3,
      status: QuestStatus.locked,
      currentProgress: 6.2,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.obsidianNavy,
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            Text(
              'Roadmap Quest',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
            Text(
              '不労所得 RPG • ステージ攻略',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                color: AppColors.textTertiary,
                letterSpacing: 1.1,
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
      floatingActionButton: FloatingActionButton(
        backgroundColor: AppColors.primaryContainer,
        foregroundColor: AppColors.onPrimary,
        shape: const CircleBorder(),
        elevation: 6,
        onPressed: () {},
        child: const Icon(Icons.add, size: 28),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentNavIndex,
        backgroundColor: AppColors.surfaceContainerLow,
        indicatorColor: AppColors.surfaceContainer,
        elevation: 10,
        onDestinationSelected: (index) {
          setState(() => _currentNavIndex = index);
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.account_balance_wallet_outlined),
            selectedIcon: Icon(Icons.account_balance_wallet, color: AppColors.primaryEmerald),
            label: '資産管理',
          ),
          NavigationDestination(
            icon: Icon(Icons.pie_chart_outline),
            selectedIcon: Icon(Icons.pie_chart, color: AppColors.primaryEmerald),
            label: '年間配当',
          ),
          NavigationDestination(
            icon: Icon(Icons.flag_outlined),
            selectedIcon: Icon(Icons.flag, color: AppColors.primaryEmerald),
            label: 'ロードマップ',
          ),
          NavigationDestination(
            icon: Icon(Icons.bookmark_border),
            selectedIcon: Icon(Icons.bookmark, color: AppColors.primaryEmerald),
            label: 'Watchlist',
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // 1. 現在の配当獲得ステータス ヒーローカード
          const CurrentStatusHeroCard(
            annualDividend: 374500,
            monthlyDividend: 31208,
            currentQuestName: 'QUEST 6 目標: ¥50,000 / 月',
            progressPercent: 62.4,
            remainingMonthlyAmount: 18792,
            targetAdditionalCapital: 5630000,
          ),
          const SizedBox(height: 18),

          // 2. 3大ステージ・レベル帯 タブバー
          StageLevelTabBar(
            selectedIndex: _selectedStageIndex,
            onStageSelected: (index) {
              setState(() => _selectedStageIndex = index);
            },
          ),
          const SizedBox(height: 20),

          // 3. セクション見出し: 全10マイルストーン・クエストマップ
          Row(
            children: const [
              Icon(Icons.check_box_outlined, color: AppColors.primaryEmerald, size: 18),
              SizedBox(width: 8),
              Text(
                '全10マイルストーン・クエストマップ',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 4. LEVEL 1: 生活インフラ自給期 セクション
          _buildStageSectionHeader(
            icon: Icons.electric_bolt_outlined,
            title: 'LEVEL 1: 生活インフラ自給期',
            badgeText: '5/5 CLEAR',
            isComplete: true,
          ),
          const SizedBox(height: 8),
          ..._milestones.where((m) => m.stage == QuestStage.level1).map(
                (milestone) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: QuestMilestoneTile(milestone: milestone),
                ),
              ),
          const SizedBox(height: 14),

          // 5. LEVEL 2: 生活費自給・ミニFIRE期 セクション
          _buildStageSectionHeader(
            icon: Icons.shopping_cart_outlined,
            title: 'LEVEL 2: 生活費自給・ミニFIRE期',
            badgeText: '挑戦中ステージ',
            isComplete: false,
          ),
          const SizedBox(height: 8),
          ..._milestones.where((m) => m.stage == QuestStage.level2).map(
                (milestone) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: QuestMilestoneTile(milestone: milestone),
                ),
              ),
          const SizedBox(height: 14),

          // 6. LEVEL 3: 完全経済的自由・FIRE期 セクション
          _buildStageSectionHeader(
            icon: Icons.military_tech_outlined,
            title: 'LEVEL 3: 完全経済的自由・FIRE期',
            badgeText: '最終ステージ帯',
            isComplete: false,
          ),
          const SizedBox(height: 8),
          ..._milestones.where((m) => m.stage == QuestStage.level3).map(
                (milestone) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: QuestMilestoneTile(milestone: milestone),
                ),
              ),
          const SizedBox(height: 16),

          // 7. シェアボタン フッターカード
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.primaryEmerald,
              side: const BorderSide(color: AppColors.primaryContainer),
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            icon: const Icon(Icons.card_membership_outlined, size: 20),
            label: const Text(
              '目標達成カードをプレビューしてシェア',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            ),
            onPressed: () {
              // シェアモーダル起動
            },
          ),
          const SizedBox(height: 80),
        ],
      ),
    );
  }

  Widget _buildStageSectionHeader({
    required IconData icon,
    required String title,
    required String badgeText,
    required bool isComplete,
  }) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Icon(icon, size: 16, color: isComplete ? AppColors.primaryEmerald : AppColors.textSecondary),
            const SizedBox(width: 6),
            Text(
              title,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: isComplete ? AppColors.primaryEmerald : AppColors.textPrimary,
              ),
            ),
          ],
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
          decoration: BoxDecoration(
            color: isComplete
                ? AppColors.primaryContainer.withOpacity(0.18)
                : AppColors.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            badgeText,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.bold,
              color: isComplete ? AppColors.primaryEmerald : AppColors.textTertiary,
            ),
          ),
        ),
      ],
    );
  }
}
```

---

## 5. ウィジェット部品実装 (`widgets/*.dart`)

### A. ヒーローステータスカード (`widgets/current_status_hero_card.dart`)
```dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../theme/app_theme.dart';

class CurrentStatusHeroCard extends StatelessWidget {
  final int annualDividend;
  final int monthlyDividend;
  final String currentQuestName;
  final double progressPercent;
  final int remainingMonthlyAmount;
  final int targetAdditionalCapital;

  const CurrentStatusHeroCard({
    super.key,
    required this.annualDividend,
    required this.monthlyDividend,
    required this.currentQuestName,
    required this.progressPercent,
    required this.remainingMonthlyAmount,
    required this.targetAdditionalCapital,
  });

  @override
  Widget build(BuildContext context) {
    final currency = NumberFormat('#,###');

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 上部バッジ行
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  children: const [
                    Icon(Icons.play_circle_fill, color: AppColors.primaryEmerald, size: 12),
                    SizedBox(width: 4),
                    Text(
                      'LEVEL 2 進行中: STEP 6',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: AppColors.primaryEmerald,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: const [
                    Icon(Icons.emoji_events, color: AppColors.accentAmber, size: 14),
                    SizedBox(width: 4),
                    Text(
                      'LV.1 制覇済み (5/10)',
                      style: TextStyle(fontSize: 10, color: AppColors.textSecondary),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // 金額表示
          const Text(
            '現在の配当獲得ステータス',
            style: TextStyle(fontSize: 11, color: AppColors.textTertiary),
          ),
          const SizedBox(height: 2),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                '¥${currency.format(annualDividend)}',
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                  fontFamily: 'SpaceGrotesk',
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(width: 4),
              const Text(
                '/ 年',
                style: TextStyle(fontSize: 13, color: AppColors.textTertiary),
              ),
            ],
          ),
          Text(
            '月間換算不労所得: 約 ¥${currency.format(monthlyDividend)} / 月',
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: AppColors.primaryEmerald,
            ),
          ),
          const SizedBox(height: 16),

          // 進行中クエストプログレス
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      currentQuestName,
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                    ),
                    Text(
                      '${progressPercent.toStringAsFixed(1)}% 達成',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(3),
                  child: LinearProgressIndicator(
                    value: progressPercent / 100,
                    minHeight: 6,
                    backgroundColor: AppColors.surfaceContainerHighest,
                    valueColor: const AlwaysStoppedAnimation<Color>(AppColors.primaryEmerald),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '現在実績: ¥${currency.format(monthlyDividend)}',
                      style: const TextStyle(fontSize: 10, color: AppColors.textTertiary),
                    ),
                    Text(
                      'あと ¥${currency.format(remainingMonthlyAmount)} / 月で攻略',
                      style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.accentAmber),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // 必要追加投資元本アドバイス
          Row(
            children: [
              const Icon(Icons.insights, size: 16, color: AppColors.primaryEmerald),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  '配当利回り4.0% 想定時、必要な追加投資元本は約 ¥${(targetAdditionalCapital / 10000).toStringAsFixed(0)}万円 です。',
                  style: const TextStyle(fontSize: 10, color: AppColors.textSecondary),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // アクションボタン
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primaryContainer,
                    foregroundColor: AppColors.onPrimary,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  icon: const Icon(Icons.share, size: 16),
                  label: const Text('達成度を共有する', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                  onPressed: () {},
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton.icon(
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.textSecondary,
                  side: const BorderSide(color: AppColors.surfaceContainerHighest),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                icon: const Icon(Icons.file_download_outlined, size: 16),
                label: const Text('画像で保存', style: TextStyle(fontSize: 12)),
                onPressed: () {},
              ),
            ],
          ),
        ],
      ),
    );
  }
}
```

### B. 3大ステージ・レベル帯タブ (`widgets/stage_level_tab_bar.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class StageLevelTabBar extends StatelessWidget {
  final int selectedIndex;
  final ValueChanged<int> onStageSelected;

  const StageLevelTabBar({
    super.key,
    required this.selectedIndex,
    required this.onStageSelected,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: const [
            Text(
              '3大ステージ・レベル帯',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
            ),
            Text(
              'タップしてジャンプ',
              style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            _buildTabItem(
              index: 0,
              badge: 'LV.1 制覇',
              title: 'インフラ自給期',
              stepRange: 'STEP 1-5',
              isCompleted: true,
            ),
            const SizedBox(width: 8),
            _buildTabItem(
              index: 1,
              badge: 'LV.2 挑戦中',
              title: '生活費・ミニFIRE',
              stepRange: 'STEP 6-7',
              isInProgress: true,
            ),
            const SizedBox(width: 8),
            _buildTabItem(
              index: 2,
              badge: 'LV.3 ロック',
              title: '完全FIRE期',
              stepRange: 'STEP 8-10',
              isLocked: true,
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildTabItem({
    required int index,
    required String badge,
    required String title,
    required String stepRange,
    bool isCompleted = false,
    bool isInProgress = false,
    bool isLocked = false,
  }) {
    final isSelected = selectedIndex == index;

    return Expanded(
      child: GestureDetector(
        onTap: () => onStageSelected(index),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
          decoration: BoxDecoration(
            color: isSelected ? AppColors.surfaceContainerHigh : AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected
                  ? AppColors.primaryEmerald
                  : AppColors.surfaceContainerHighest.withOpacity(0.3),
              width: isSelected ? 1.5 : 1,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                badge,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: isCompleted
                      ? AppColors.primaryEmerald
                      : isInProgress
                          ? AppColors.accentCyan
                          : AppColors.textTertiary,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                title,
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              Text(
                stepRange,
                style: const TextStyle(fontSize: 9, color: AppColors.textTertiary),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
```

### C. クエストマイルストーンタイル (`widgets/quest_milestone_tile.dart`)
```dart
import 'package:flutter/material.dart';
import '../../models/roadmap_quest.dart';
import '../../theme/app_theme.dart';

class QuestMilestoneTile extends StatelessWidget {
  final RoadmapMilestone milestone;

  const QuestMilestoneTile({super.key, required this.milestone});

  @override
  Widget build(BuildContext context) {
    final isDone = milestone.isCompleted;
    final isCurrent = milestone.isInProgress;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isCurrent
            ? AppColors.surfaceContainerHigh
            : AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: isCurrent
              ? AppColors.primaryEmerald.withOpacity(0.6)
              : AppColors.surfaceContainerHighest.withOpacity(0.3),
        ),
      ),
      child: Row(
        children: [
          // 左端アイコンボックス
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: isDone
                  ? AppColors.primaryContainer.withOpacity(0.18)
                  : isCurrent
                      ? AppColors.primaryContainer
                      : AppColors.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              milestone.icon,
              size: 20,
              color: isCurrent
                  ? AppColors.onPrimary
                  : isDone
                      ? AppColors.primaryEmerald
                      : AppColors.textTertiary,
            ),
          ),
          const SizedBox(width: 12),

          // 中央テキスト
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      milestone.title,
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: milestone.isLocked ? AppColors.textSecondary : AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(width: 6),
                    if (isDone)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                        decoration: BoxDecoration(
                          color: AppColors.primaryContainer.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Text(
                          '達成済',
                          style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                        ),
                      )
                    else if (isCurrent)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                        decoration: BoxDecoration(
                          color: AppColors.accentCyan.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Text(
                          '現在挑戦中',
                          style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.accentCyan),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  milestone.description,
                  style: const TextStyle(fontSize: 11, color: AppColors.textTertiary),
                ),
              ],
            ),
          ),

          // 右端進捗・クリア表示
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                isDone ? '100%' : '${milestone.currentProgress.toStringAsFixed(1)}%',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: isDone
                      ? AppColors.primaryEmerald
                      : isCurrent
                          ? AppColors.accentCyan
                          : AppColors.textTertiary,
                  fontFamily: 'SpaceGrotesk',
                ),
              ),
              Text(
                isDone
                    ? 'CLEAR'
                    : milestone.remainingAmountMonthly != null
                        ? 'あと¥${milestone.remainingAmountMonthly}'
                        : 'LOCKED',
                style: TextStyle(
                  fontSize: 9,
                  fontWeight: FontWeight.w600,
                  color: isDone ? AppColors.primaryEmerald : AppColors.textTertiary,
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
