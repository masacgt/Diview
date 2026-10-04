# 利用規約＆プライバシーポリシー Flutter (Dart) 実装仕様書 & フルソースコード

選択された画面（`SCREEN_50`: 利用規約＆プライバシーポリシー）のObsidian Navyダークテーマ、セキュリティステータスヘッダー、利用規約／プライバシーポリシーのタブ切り替え、ゼロナレッジ宣言バナー、各条項カード、チェックリスト、および最下部の利用同意フッターを忠実に再現したFlutterコード一式です。

---

## 1. 推奨ディレクトリ構造

```text
lib/
├── theme/
│   └── app_theme.dart
└── screens/
    └── terms_and_privacy_screen.dart
```

---

## 2. デザイントークン & カラーテーマ (`theme/app_theme.dart`)

```dart
import 'package:flutter/material.dart';

class AppColors {
  // Kurogane Obsidian Palette
  static const Color obsidianNavy = Color(0xFF0B1326);
  static const Color surfaceContainerLowest = Color(0xFF060E20);
  static const Color surfaceContainerLow = Color(0xFF131B2E);
  static const Color surfaceContainer = Color(0xFF171F33);
  static const Color surfaceContainerHigh = Color(0xFF222A3D);
  static const Color surfaceContainerHighest = Color(0xFF2D3449);

  // Accents & Brand Signals
  static const Color primaryEmerald = Color(0xFF4EDEA3);
  static const Color primaryContainer = Color(0xFF10B981);
  static const Color onPrimary = Color(0xFF003824);
  static const Color secondaryBlue = Color(0xFFC0C1FF);
  
  // Status & Text
  static const Color textPrimary = Color(0xFFDAE2FD);
  static const Color textSecondary = Color(0xFFBBCAAF);
  static const Color textTertiary = Color(0xFF86948A);
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
      ),
      fontFamily: 'Manrope',
    );
  }
}
```

---

## 3. メイン画面実装 (`screens/terms_and_privacy_screen.dart`)

```dart
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class TermsAndPrivacyScreen extends StatefulWidget {
  const TermsAndPrivacyScreen({super.key});

  @override
  State<TermsAndPrivacyScreen> createState() => _TermsAndPrivacyScreenState();
}

class _TermsAndPrivacyScreenState extends State<TermsAndPrivacyScreen> {
  // 0: 利用規約, 1: プライバシーポリシー
  int _selectedTabIndex = 1;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.obsidianNavy,
      appBar: AppBar(
        backgroundColor: AppColors.obsidianNavy,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 20, color: AppColors.textPrimary),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const Text(
          'Terms Of Service And Privacy Policy',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
          ),
        ),
        centerTitle: false,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // 1. Kurogane Security Core ステータスヘッダー
          _buildSecurityCoreHeader(),
          const SizedBox(height: 16),

          // 2. タブセレクター (利用規約 / プライバシー)
          _buildSegmentedTabSelector(),
          const SizedBox(height: 16),

          // 3. 切り替えコンテンツ
          if (_selectedTabIndex == 1) ...[
            _buildPrivacyPolicyContent(),
          ] else ...[
            _buildTermsOfServiceContent(),
          ],

          const SizedBox(height: 16),

          // 4. 安心してお使いいただくために
          _buildTrustBanner(),
          const SizedBox(height: 16),

          // 5. 利用同意フッター
          _buildConsentFooter(),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  // --- Widgets ---

  // 1. Security Core ステータスヘッダー
  Widget _buildSecurityCoreHeader() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.surfaceContainerHighest.withOpacity(0.5)),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: AppColors.primaryEmerald,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'Kurogane Security Core',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer.withOpacity(0.18),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.primaryEmerald.withOpacity(0.4)),
                ),
                child: Row(
                  children: const [
                    Icon(Icons.verified_user_outlined, size: 13, color: AppColors.primaryEmerald),
                    SizedBox(width: 4),
                    Text(
                      '保護稼働中',
                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppColors.primaryEmerald),
                    ),
                  ],
                ),
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
                  child: Row(
                    children: [
                      const Icon(Icons.shield_outlined, size: 18, color: AppColors.primaryEmerald),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              '端末内AES-256暗号化',
                              style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            Text(
                              'クラウド送信 0 Byte',
                              style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                            ),
                          ],
                        ),
                      ),
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
                  child: Row(
                    children: [
                      const Icon(Icons.block, size: 18, color: AppColors.primaryEmerald),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              '第三者提供・売買ゼロ',
                              style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            Text(
                              '証券認証情報は非保持',
                              style: TextStyle(fontSize: 10, color: AppColors.textTertiary),
                            ),
                          ],
                        ),
                      ),
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

  // 2. セグメント切り替えタブ
  Widget _buildSegmentedTabSelector() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: () => setState(() => _selectedTabIndex = 0),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 10),
                decoration: BoxDecoration(
                  color: _selectedTabIndex == 0 ? AppColors.primaryContainer : Colors.transparent,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.description_outlined,
                      size: 16,
                      color: _selectedTabIndex == 0 ? AppColors.onPrimary : AppColors.textSecondary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      '利用規約',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: _selectedTabIndex == 0 ? AppColors.onPrimary : AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Expanded(
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: () => setState(() => _selectedTabIndex = 1),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 10),
                decoration: BoxDecoration(
                  color: _selectedTabIndex == 1 ? AppColors.primaryContainer : Colors.transparent,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.verified_outlined,
                      size: 16,
                      color: _selectedTabIndex == 1 ? AppColors.onPrimary : AppColors.textSecondary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      'プライバシー',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: _selectedTabIndex == 1 ? AppColors.onPrimary : AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // 3. プライバシーポリシー コンテンツ一式
  Widget _buildPrivacyPolicyContent() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // ゼロナレッジ宣言カード
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.primaryEmerald.withOpacity(0.3)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: AppColors.primaryContainer.withOpacity(0.18),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.security, size: 20, color: AppColors.primaryEmerald),
                  ),
                  const SizedBox(width: 10),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: const [
                      Text(
                        'ZERO KNOWLEDGE ARCHITECTURE',
                        style: TextStyle(
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                          color: AppColors.primaryEmerald,
                          letterSpacing: 1.1,
                        ),
                      ),
                      Text(
                        '金融データ端末内完結宣言',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              const Text(
                'Kurogane Dividendは「あなたの蓄財状況を誰にも見せない」ことを思想として設計されています。入力された全株式データ・投資金額・配当キャッシュフローは、クラウドサーバーへ一切送信されず、端末内でのみ演算処理されます。',
                style: TextStyle(fontSize: 12, height: 1.6, color: AppColors.textSecondary),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainer,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Row(
                            children: [
                              Icon(Icons.wifi_off, size: 14, color: AppColors.primaryEmerald),
                              SizedBox(width: 4),
                              Text('外部送信', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                            ],
                          ),
                          SizedBox(height: 2),
                          Text(
                            '0 Bytes',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                          ),
                          Text('保有株・金額の送出なし', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainer,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Row(
                            children: [
                              Icon(Icons.lock_outline, size: 14, color: AppColors.primaryEmerald),
                              SizedBox(width: 4),
                              Text('保護規格', style: TextStyle(fontSize: 10, color: AppColors.textTertiary)),
                            ],
                          ),
                          SizedBox(height: 2),
                          Text(
                            'AES-256',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                          ),
                          Text('SQLite端末暗号化', style: TextStyle(fontSize: 9, color: AppColors.textTertiary)),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // 1. 取得する情報とその範囲
        _buildSectionCard(
          titleNumber: '1',
          title: '取得する情報とその範囲',
          body: '当社がアプリの安定運用および技術的改善のためにのみ取得する情報は以下の通りです：',
          bulletList: [
            {
              'title': '匿名化クラッシュログ',
              'desc': 'アプリクラッシュ時のスタックトレース、OSバージョン、デバイスモデル（個人を特定する情報は含まれません）'
            },
            {
              'title': '匿名の機能利用統計',
              'desc': '画面遷移頻度などの集計情報（Firebase Analytics等の標準基盤を利用）'
            },
          ],
        ),
        const SizedBox(height: 12),

        // 2. 情報の利用目的
        _buildSectionCard(
          titleNumber: '2',
          title: '情報の利用目的',
          body:
              '収集した匿名ログデータは、バグの特定・解消、パフォーマンス改善、新機能（配当カレンダーの視認性向上等）の開発検証にのみ限定して使用されます。マーケティング目的での第三者名簿提供などは行いません。',
        ),
        const SizedBox(height: 12),

        // 3. 第三者提供および外部SDK
        _buildSectionCard(
          titleNumber: '3',
          title: '第三者提供および外部SDK',
          body: '本アプリの正常な動作および課金決済の管理のため、信頼性の高い以下のSDKモジュールを組み込んでいます。',
          extraWidget: Column(
            children: [
              const SizedBox(height: 10),
              _buildSdkItem('StoreKit / Google Billing', 'サブスクリプション購入領収証の検証', '決済処理'),
              const SizedBox(height: 6),
              _buildSdkItem('Crashlytics', '匿名クラッシュ診断・アプリ安定化', '診断'),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // 4. セキュリティ管理体制
        _buildSectionCard(
          titleNumber: '4',
          title: 'セキュリティ管理体制（SQLiteローカル暗号化）',
          body:
              'ポートフォリオデータは、端末固有のセキュアキーストア（iOS Keychain / Android Keystore）から導出されたマスターキーにより、ローカルデータベース（SQLite）レベルで暗号化されています。端末が脱獄（Jailbreak）されていない限り、他のアプリケーションからの不正アクセスから強固に隔離保護されます。',
        ),
        const SizedBox(height: 12),

        // 5. お問い合わせ窓口
        _buildSectionCard(
          titleNumber: '5',
          title: 'お問い合わせ窓口',
          body: '規約内容、プライバシー情報の取り扱い、またはアプリに関するご不明点はサポート窓口までお寄せください。',
        ),
      ],
    );
  }

  // 利用規約コンテンツ（プレースホルダー対応）
  Widget _buildTermsOfServiceContent() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionCard(
          titleNumber: '1',
          title: '総則・本規約の適用',
          body: '本規約は、当アプリの利用に関する一切の関係に適用されます。ユーザーは本アプリをインストールした時点で、本規約に同意したものとみなされます。',
        ),
        const SizedBox(height: 12),
        _buildSectionCard(
          titleNumber: '2',
          title: '投資判断に関する免責事項',
          body:
              '本アプリが提供する配当金推移、株価、利回り試算、およびFIREシミュレーション等の数値は、投資の勧誘や助言を目的としたものではありません。実際の投資判断および資産運用はユーザーご自身の責任において行われます。',
        ),
        const SizedBox(height: 12),
        _buildSectionCard(
          titleNumber: '3',
          title: 'データの自己責任原則',
          body:
              '本アプリはローカル完結型アーキテクチャを採用しているため、端末の紛失や故障に伴うデータ復旧は保証されません。定期的なCSVエクスポートによるバックアップを推奨いたします。',
        ),
      ],
    );
  }

  // 汎用条項カード
  Widget _buildSectionCard({
    required String titleNumber,
    required String title,
    required String body,
    List<Map<String, String>>? bulletList,
    Widget? extraWidget,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 3,
                height: 14,
                decoration: BoxDecoration(
                  color: AppColors.primaryEmerald,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '$titleNumber. $title',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textPrimary,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            body,
            style: const TextStyle(fontSize: 12, height: 1.55, color: AppColors.textSecondary),
          ),
          if (bulletList != null) ...[
            const SizedBox(height: 10),
            ...bulletList.map((item) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.check, size: 14, color: AppColors.primaryEmerald),
                      const SizedBox(width: 6),
                      Expanded(
                        child: RichText(
                          text: TextSpan(
                            style: const TextStyle(fontSize: 11, height: 1.45, color: AppColors.textSecondary),
                            children: [
                              TextSpan(
                                text: '${item['title']}: ',
                                style: const TextStyle(fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                              ),
                              TextSpan(text: item['desc']),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                )),
          ],
          if (extraWidget != null) extraWidget,
        ],
      ),
    );
  }

  // SDKリストアイテム
  Widget _buildSdkItem(String name, String desc, String tag) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppColors.textPrimary)),
                Text(desc, style: const TextStyle(fontSize: 10, color: AppColors.textTertiary)),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              tag,
              style: const TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.textSecondary),
            ),
          ),
        ],
      ),
    );
  }

  // 4. 安心バナー
  Widget _buildTrustBanner() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: AppColors.primaryContainer.withOpacity(0.18),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.verified, size: 18, color: AppColors.primaryEmerald),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  '安心してお使いいただくために',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AppColors.textPrimary),
                ),
                SizedBox(height: 4),
                Text(
                  'Kurogane Dividendは金融機関の認証情報（ログインパスワード、取引暗証番号等）をお預かりすることは未来永劫ありません。日々の配当受取日と利回り可視化を、安全にお楽しみください。',
                  style: TextStyle(fontSize: 11, height: 1.5, color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // 5. 利用同意フッター
  Widget _buildConsentFooter() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const [
          Icon(Icons.info_outline, size: 16, color: AppColors.textTertiary),
          SizedBox(width: 8),
          Expanded(
            child: Text(
              '本アプリのダウンロード、インストール、またはご利用の開始をもって、上記「利用規約」および「プライバシーポリシー」のすべての条項に同意いただいたものとみなします。',
              style: TextStyle(fontSize: 10, height: 1.5, color: AppColors.textTertiary),
            ),
          ),
        ],
      ),
    );
  }
}
```

---

## 4. 特徴と設計ポイント

1. **ゼロナレッジ・ローカル完結のビジュアル表現**:
   - `0 Bytes` 送出なし & `AES-256` 暗号化のツインバッジメトリクスをカード形式で配置。
2. **直感的なタブ切り替え (利用規約 / プライバシー)**:
   - `_selectedTabIndex` による状態管理で、タップ時にエメラルドグリーンのアクティブコンテナへ瞬時にアニメーション切り替え。
3. **セマンティックな条項レンダリング**:
   - 左端にエメラルドグリーンのインジケータバーを配した見出しと、チェックアイコン付きの箇条書き（匿名クラッシュログ等）をコンポーネント化（`_buildSectionCard`）。
4. **外部SDKの安全タグ表示**:
   - `StoreKit`（決済処理）や `Crashlytics`（診断）を用途タグ付きの高密度リストで整理。
