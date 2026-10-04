# お問い合わせ・フィードバック画面 Flutter (Dart) 実装仕様書 & フルソースコード

選択された「お問い合わせ・フィードバック（Contact And Feedback）」画面のUIコンポーネント構造、ダークモード配色（Obsidian Navy & Emerald Accent）、FAQアコーディオン、カテゴリ選択チップ、入力フォーム、端末診断トグル、コミュニティリンクを忠実に再現したFlutterコード一式です。

---

## 1. ディレクトリ構造推奨

```text
lib/
├── main.dart
├── theme/
│   └── app_theme.dart
└── screens/
    ├── contact_feedback_screen.dart
    └── widgets/
        ├── support_desk_banner.dart
        ├── faq_accordion_section.dart
        ├── feedback_form_card.dart
        ├── community_links_section.dart
        └── privacy_guarantee_card.dart
```

---

## 2. デザイントークン & カラーテーマ (`theme/app_theme.dart`)

```dart
import 'package:flutter/material.dart';

class AppColors {
  // Kurogane Obsidian Navy Dark Palette
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
  static const Color onPrimaryContainer = Color(0xFF002113);
  static const Color secondaryBlue = Color(0xFFC0C1FF);
  
  // Text & Border Colors
  static const Color textPrimary = Color(0xFFDAE2FD);
  static const Color textSecondary = Color(0xFFBBCAAF);
  static const Color textTertiary = Color(0xFF86948A);
  static const Color outline = Color(0xFF3C4A42);
  static const Color error = Color(0xFFFFB4AB);
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
        iconTheme: IconThemeData(color: AppColors.textPrimary),
      ),
    );
  }
}
```

---

## 3. メイン画面実装 (`screens/contact_feedback_screen.dart`)

```dart
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import 'widgets/support_desk_banner.dart';
import 'widgets/faq_accordion_section.dart';
import 'widgets/feedback_form_card.dart';
import 'widgets/community_links_section.dart';
import 'widgets/privacy_guarantee_card.dart';

class ContactFeedbackScreen extends StatefulWidget {
  const ContactFeedbackScreen({super.key});

  @override
  State<ContactFeedbackScreen> createState() => _ContactFeedbackScreenState();
}

class _ContactFeedbackScreenState extends State<ContactFeedbackScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.obsidianNavy,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const Text(
          'Contact And Feedback',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
            letterSpacing: -0.2,
          ),
        ),
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          children: const [
            // 1. 公式サポートデスク・ヘッダーバナー
            SupportDeskBanner(),
            SizedBox(height: 20),

            // 2. よくあるご質問 (FAQ アコーディオン)
            FaqAccordionSection(),
            SizedBox(height: 24),

            // 3. お問い合わせ・ご意見 投稿フォーム
            FeedbackFormCard(),
            SizedBox(height: 24),

            // 4. 公式コミュニティ・ストアレビュー
            CommunityLinksSection(),
            SizedBox(height: 20),

            // 5. Kurogane プライバシー・セキュリティ保証
            PrivacyGuaranteeCard(),
            SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}
```

---

## 4. サブコンポーネント実装 (`widgets/*.dart`)

### A. サポートデスク・バナー (`widgets/support_desk_banner.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class SupportDeskBanner extends StatelessWidget {
  const SupportDeskBanner({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.4)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(
              Icons.support_agent,
              color: AppColors.primaryEmerald,
              size: 26,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppColors.primaryContainer.withOpacity(0.18),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Text(
                        'Official Support',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: AppColors.primaryEmerald,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    Container(
                      width: 6,
                      height: 6,
                      decoration: const BoxDecoration(
                        color: AppColors.primaryEmerald,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                const Text(
                  'Kurogane サポートデスク',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  '配当管理や操作についてご不明な点、改善のご要望をお気軽にお寄せください。通常24時間以内に返信いたします。',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                    height: 1.45,
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

### B. FAQ アコーディオン (`widgets/faq_accordion_section.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class FaqItem {
  final String question;
  final String answer;
  bool isExpanded;

  FaqItem({
    required this.question,
    required this.answer,
    this.isExpanded = false,
  });
}

class FaqAccordionSection extends StatefulWidget {
  const FaqAccordionSection({super.key});

  @override
  State<FaqAccordionSection> createState() => _FaqAccordionSectionState();
}

class _FaqAccordionSectionState extends State<FaqAccordionSection> {
  final List<FaqItem> _faqList = [
    FaqItem(
      question: '証券口座の自動連携はできますか？',
      answer: '当アプリはゼロナレッジ・プライバシー保護の観点から、ID・パスワード等の認証情報を預かるAPI連携はあえて行わず、安全なCSVインポートと手動登録を採用しております。',
    ),
    FaqItem(
      question: 'CSVインポートで対応している証券会社は？',
      answer: 'SBI証券、楽天証券、マネックス証券、松井証券、moomoo証券などの主要フォーマットを自動判定・サポートしています。',
    ),
    FaqItem(
      question: '新NISAの非課税計算はどうなっていますか？',
      answer: '成長投資枠およびつみたて投資枠に対応し、国内源泉徴収（20.315%）を非課税0%としてリアルタイム試算します。',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: const [
            Row(
              children: [
                Icon(Icons.help_outline, size: 18, color: AppColors.primaryEmerald),
                SizedBox(width: 6),
                Text(
                  'よくあるご質問 (FAQ)',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
              ],
            ),
            Text(
              'タップして展開',
              style: TextStyle(fontSize: 11, color: AppColors.textTertiary),
            ),
          ],
        ),
        const SizedBox(height: 10),
        ..._faqList.map((item) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.4)),
                ),
                child: Theme(
                  data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                  child: ExpansionTile(
                    tilePadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                    leading: Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerHigh,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      alignment: Alignment.center,
                      child: const Text(
                        'Q',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: AppColors.primaryEmerald,
                        ),
                      ),
                    ),
                    title: Text(
                      item.question,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
                    children: [
                      Text(
                        item.answer,
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                          height: 1.45,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            )),
      ],
    );
  }
}
```

### C. お問い合わせフォーム (`widgets/feedback_form_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class FeedbackFormCard extends StatefulWidget {
  const FeedbackFormCard({super.key});

  @override
  State<FeedbackFormCard> createState() => _FeedbackFormCardState();
}

class _FeedbackFormCardState extends State<FeedbackFormCard> {
  final _formKey = GlobalKey<FormState>();
  String _selectedCategory = '機能の改善要望';
  final TextEditingController _nicknameController = TextEditingController(text: '配当雪だるま');
  final TextEditingController _emailController = TextEditingController(text: 'investor@example.com');
  final TextEditingController _contentController = TextEditingController();
  bool _includeDiagnostics = true;
  bool _isSubmitting = false;

  final List<Map<String, dynamic>> _categories = [
    {'label': '不具合の報告', 'icon': Icons.bug_report_outlined},
    {'label': '機能の改善要望', 'icon': Icons.lightbulb_outline},
    {'label': '銘柄データの誤り', 'icon': Icons.table_chart_outlined},
    {'label': 'その他', 'icon': Icons.more_horiz},
  ];

  @override
  void dispose() {
    _nicknameController.dispose();
    _emailController.dispose();
    _contentController.dispose();
    super.dispose();
  }

  void _handleSubmit() {
    if (_contentController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('お問い合わせ内容を入力してください'),
          backgroundColor: AppColors.surfaceContainerHigh,
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    Future.delayed(const Duration(milliseconds: 1200), () {
      setState(() => _isSubmitting = false);
      _contentController.clear();

      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          backgroundColor: AppColors.surfaceContainerLow,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
          title: Row(
            children: const [
              Icon(Icons.check_circle, color: AppColors.primaryEmerald),
              SizedBox(width: 8),
              Text('送信完了', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            ],
          ),
          content: const Text(
            'お問い合わせ・ご意見を受け付けました。ご協力いただき誠にありがとうございます。',
            style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('OK', style: TextStyle(color: AppColors.primaryEmerald, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: const [
            Icon(Icons.rate_review_outlined, size: 18, color: AppColors.primaryEmerald),
            SizedBox(width: 6),
            Text(
              'お問い合わせ・ご意見',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),

        // カテゴリ選択グリッド (2x2)
        const Text(
          'カテゴリ *',
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textSecondary),
        ),
        const SizedBox(height: 8),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            childAspectRatio: 3.4,
            crossAxisSpacing: 8,
            mainAxisSpacing: 8,
          ),
          itemCount: _categories.length,
          itemBuilder: (context, index) {
            final cat = _categories[index];
            final isSelected = _selectedCategory == cat['label'];

            return InkWell(
              onTap: () => setState(() => _selectedCategory = cat['label']),
              borderRadius: BorderRadius.circular(10),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                decoration: BoxDecoration(
                  color: isSelected ? AppColors.primaryContainer : AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: isSelected ? AppColors.primaryEmerald : AppColors.surfaceContainerHighest,
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      cat['icon'],
                      size: 16,
                      color: isSelected ? AppColors.onPrimaryContainer : AppColors.textSecondary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      cat['label'],
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.w600,
                        color: isSelected ? AppColors.onPrimaryContainer : AppColors.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
        const SizedBox(height: 14),

        // お名前 / ニックネーム
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: const [
            Text('お名前／ニックネーム', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
            Text('任意', style: TextStyle(fontSize: 11, color: AppColors.textTertiary)),
          ],
        ),
        const SizedBox(height: 6),
        TextField(
          controller: _nicknameController,
          style: const TextStyle(fontSize: 13, color: AppColors.textPrimary),
          decoration: InputDecoration(
            prefixIcon: const Icon(Icons.person_outline, size: 18, color: AppColors.textTertiary),
            filled: true,
            fillColor: AppColors.surfaceContainerHigh,
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
          ),
        ),
        const SizedBox(height: 14),

        // 返信用メールアドレス
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: const [
            Text('返信用メールアドレス', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
            Text('返信が必要な場合', style: TextStyle(fontSize: 11, color: AppColors.textTertiary)),
          ],
        ),
        const SizedBox(height: 6),
        TextField(
          controller: _emailController,
          keyboardType: TextInputType.emailAddress,
          style: const TextStyle(fontSize: 13, color: AppColors.textPrimary),
          decoration: InputDecoration(
            prefixIcon: const Icon(Icons.mail_outline, size: 18, color: AppColors.textTertiary),
            filled: true,
            fillColor: AppColors.surfaceContainerHigh,
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
          ),
        ),
        const SizedBox(height: 14),

        // お問い合わせ内容
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text('お問い合わせ内容・ご要望 *', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
            ValueListenableBuilder(
              valueListenable: _contentController,
              builder: (context, value, child) {
                return Text(
                  '${value.text.length} / 1,000',
                  style: const TextStyle(fontSize: 11, color: AppColors.textTertiary),
                );
              },
            ),
          ],
        ),
        const SizedBox(height: 6),
        TextField(
          controller: _contentController,
          maxLines: 4,
          maxLength: 1000,
          style: const TextStyle(fontSize: 13, color: AppColors.textPrimary),
          decoration: InputDecoration(
            counterText: '',
            hintText: '具体的な銘柄コードや再現手順、こうなったら嬉しい機能などを教えてください。',
            hintStyle: const TextStyle(fontSize: 12, color: AppColors.textTertiary),
            filled: true,
            fillColor: AppColors.surfaceContainerHigh,
            contentPadding: const EdgeInsets.all(14),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
          ),
        ),
        const SizedBox(height: 14),

        // 端末診断情報の添付トグル
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.phone_iphone, size: 20, color: AppColors.textSecondary),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Text(
                          '端末診断情報を添付',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                        ),
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                          decoration: BoxDecoration(
                            color: AppColors.primaryContainer.withOpacity(0.2),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: const Text(
                            '推奨',
                            style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                          ),
                        ),
                      ],
                    ),
                    const Text(
                      'v1.2.0 (Build 248) • iOS/Android OS...',
                      style: TextStyle(fontSize: 11, color: AppColors.textTertiary),
                    ),
                  ],
                ),
              ),
              Switch(
                value: _includeDiagnostics,
                activeColor: AppColors.primaryEmerald,
                onChanged: (val) => setState(() => _includeDiagnostics = val),
              ),
            ],
          ),
        ),
        const SizedBox(height: 10),

        // SSL暗号化注記
        Row(
          children: const [
            Icon(Icons.verified_user_outlined, size: 14, color: AppColors.primaryEmerald),
            SizedBox(width: 6),
            Expanded(
              child: Text(
                '入力内容はSSL暗号化され、お問い合わせ対応のみに厳重管理されます',
                style: TextStyle(fontSize: 11, color: AppColors.textTertiary),
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),

        // 送信ボタン
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primaryContainer,
              foregroundColor: AppColors.onPrimary,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              elevation: 4,
            ),
            onPressed: _isSubmitting ? null : _handleSubmit,
            icon: _isSubmitting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(color: AppColors.onPrimary, strokeWidth: 2),
                  )
                : const Icon(Icons.send_rounded, size: 18),
            label: Text(
              _isSubmitting ? '送信中...' : '送信する',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
            ),
          ),
        ),
      ],
    );
  }
}
```

### D. 公式コミュニティ・応援リンク (`widgets/community_links_section.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class CommunityLinksSection extends StatelessWidget {
  const CommunityLinksSection({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: const [
            Icon(Icons.people_outline, size: 18, color: AppColors.primaryEmerald),
            SizedBox(width: 6),
            Text(
              '公式コミュニティ・応援',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),

        // 公式Xリンク
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.4)),
          ),
          child: Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.tag, size: 20, color: AppColors.primaryEmerald),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: const [
                        Text(
                          '開発チーム公式 X (旧Twitter)',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                        ),
                        SizedBox(width: 4),
                        Icon(Icons.check_circle, size: 14, color: AppColors.primaryEmerald),
                      ],
                    ),
                    const Text(
                      '最新の配当データ更新・アプデ速報を発信中',
                      style: TextStyle(fontSize: 11, color: AppColors.textTertiary),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.open_in_new, size: 18, color: AppColors.textTertiary),
            ],
          ),
        ),
        const SizedBox(height: 8),

        // アプリストア応援レビュー
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.4)),
          ),
          child: Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.star_outline, size: 20, color: AppColors.secondaryBlue),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: const [
                    Text(
                      'アプリストアで応援レビューを書く',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                    ),
                    Text(
                      '★5レビューが個人開発の大きな励みになります',
                      style: TextStyle(fontSize: 11, color: AppColors.textTertiary),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.arrow_forward_ios, size: 14, color: AppColors.textTertiary),
            ],
          ),
        ),
      ],
    );
  }
}
```

### E. プライバシー保証カード (`widgets/privacy_guarantee_card.dart`)
```dart
import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

class PrivacyGuaranteeCard extends StatelessWidget {
  const PrivacyGuaranteeCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.lock_outline, size: 18, color: AppColors.primaryEmerald),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Kurogane プライバシー・セキュリティ保証',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                ),
                SizedBox(height: 4),
                Text(
                  '送信されたお問い合わせ内容およびメールアドレスは、返信および機能改善の統計分析目的でのみ厳重に取り扱われます。第三者への提供、マーケティング用途への転用、資産状況の追跡などは一切行いません。',
                  style: TextStyle(fontSize: 11, color: AppColors.textTertiary, height: 1.45),
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
