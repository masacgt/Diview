# Diview リリース前品質レビュー（2026-10-05）

追記：このレビューの後、端末で定期確認する株価アラートと配当月リマインダーを実装しました。現在の通知仕様・検証結果は[通知実装の記録](notifications.md)を参照してください。以下の内容はレビュー時点の記録です。

対象は `kurogane-dividend/android/` です。現在のAndroid実装を修正しました。`archive/`、業種マスター、配色・レイアウトの刷新には変更を加えていません。

ユニットテスト 74 件が成功しました。Android/Composeテスト12件も成功しました。Debug/ReleaseビルドとLintが成功し、Lintはエラー0件・警告89件です。スクロール状態の直接読取による警告は6件から0件になりました。

この作業は発見した不具合の修正と回帰検証を行ったものです。未実装機能や参考データを実運用可能と判定するものではありません。リリース条件として残る項目は以下に明記します。

## ■ 分析フェーズ（バグ・改善点の洗い出し）

### 1. 保有編集・追加・監視設定の保存漏れ

- 【対象箇所】: [viewmodel/DividendViewModel.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 編集・追加・目標利回り変更がメモリ上だけに反映され、再起動で元に戻っていました。保存を共通化し、直列化した非同期保存と保存失敗通知を追加しました。CSV/DVMの確定処理は保存完了を待ちます。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 2. 初回銘柄取得によるユーザー保有情報の上書き

- 【対象箇所】: [viewmodel/DividendViewModel.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 人気銘柄取得後に既存保有をAPI由来の100株の位置へ置換していました。保存済み保有がある場合は初回登録を行わず、取得待ちの間に編集された場合も適用を中止します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 3. 検索した銘柄と異なる詳細表示

- 【対象箇所】: [ui/screens/StockDetailScreen.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 未保有銘柄を開くと保有一覧の先頭または固定のトヨタを表示していました。StockDetailResolverでコードの一致を確認し、保有・監視・取得した銘柄から解決します。解決できなければ取得失敗を表示します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 4. NISAと課税口座の合算時の税額誤り

- 【対象箇所】: [model/Models.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/Models.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 一括登録で最初の口座区分だけを保存し、全株がその区分の税率になっていました。口座ごとの数量を受け取り、taxFreeSharesを保存し、非課税部分と課税部分を分けて計算します。CSVの同一コードの複数行も数量合算・取得単価の加重平均で取り込みます。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 5. 投資信託の評価額・取得原価が10000倍

- 【対象箇所】: [model/Models.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/Models.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 保有口数に1万口あたりの基準価額をそのまま掛けていました。Category.FUNDのpriceUnitを10000とし、評価額・原価・配当・詳細試算・構成比に反映しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 6. ロケールによる利回り変更時の例外

- 【対象箇所】: [model/PortfolioMath.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/PortfolioMath.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: String.formatの結果をtoDoubleへ渡していたため、小数点がカンマの言語設定で例外が発生します。数値のまま丸める計算へ変更し、ドイツ語ロケールで検証しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 7. ゼロ除算・非有限値・数量の桁あふれ

- 【対象箇所】: [model/PortfolioMath.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/PortfolioMath.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 未取得の価格0で利回りがInfinityになり、複数口座のInt加算が負数に反転し、NaNや巨大数量を受け入れる経路がありました。数値検証・安全な利回り計算・Longでの合算・範囲検証を共通化しました。金額表示の32ビット変換も64ビットへ変更しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 8. CSVとDVMの不正値・部分取込

- 【対象箇所】: [data/BrokerCsvImporter.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/BrokerCsvImporter.kt)
- 【カテゴリ】: セキュリティ
- 【重大度】: 高
- 【原因分析】: 負数量、端数数量、Infinity、NaN、閉じていない引用符などを通せました。異常行を明示し、整数数量と有限の単価を確認します。DVMでは暗号認証、形式、対象配列、数量、単価を確認し、異常な内容を拒否します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 9. 投資信託名の正規化で商品差異を消去

- 【対象箇所】: [data/BrokerCsvImporter.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/BrokerCsvImporter.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 正規表現の文字クラス内のハイフンが文字範囲として解釈され、かなまで消去していました。ハイフンをエスケープし、ヘッジあり/なしの異なる商品を照合候補から外します。回帰テストが実際にこの誤照合を検出しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 10. 大きいファイルを読み終わってからサイズ検査

- 【対象箇所】: [data/InputSafety.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/InputSafety.kt)
- 【カテゴリ】: セキュリティ
- 【重大度】: 高
- 【原因分析】: readBytesでファイル全体を確保した後に10MB/50MBを確認していました。上限を1バイト超えた段階で読込を中止します。CSVは10MB、DVMは50MB、API応答は8MBを上限にします。0バイトのバルク読込にも対応し、上限長のInt計算の桁あふれも防ぎます。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 11. キャンセルの握りつぶしと古い検索結果

- 【対象箇所】: [ui/components/SearchSheet.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/components/SearchSheet.kt)
- 【カテゴリ】: バグ
- 【重大度】: 中
- 【原因分析】: runCatchingがCancellationExceptionも失敗結果にしていました。キャンセルとVMエラーを再送出する共通処理へ変更し、検索入力変更時に前の結果を破棄します。銘柄登録は処理を開始する前に二重起動を禁止します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 12. HTTP通信・URL・外部応答の検証不足

- 【対象箇所】: [data/CloudflareMarketRepository.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/CloudflareMarketRepository.kt)
- 【カテゴリ】: セキュリティ
- 【重大度】: 高
- 【原因分析】: 応答全体を無制限に読み、HTTPエラー本文を利用者向けエラーに含め、応答コードと要求銘柄の一致も確認していませんでした。HTTPS URLの構造、認証情報の不在、日付実在性、応答コード・価格を確認し、リダイレクトを無効化しました。接続はfinallyで閉じます。404とサーバー障害を区別します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 13. 暗号鍵作成の競合と復元失敗の無視

- 【対象箇所】: [data/LocalImportStore.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/LocalImportStore.kt)
- 【カテゴリ】: セキュリティ
- 【重大度】: 高
- 【原因分析】: 複数の保存クラスが同じKeystoreエイリアスを同時に生成し得ました。生成を共通ロックで直列化し、既存鍵をキャッシュします。破損・復号失敗を通知し、失敗した保有データを監視設定保存で上書きしません。保存済みデータの読込・暗号処理は画面のスレッドから移しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 14. 端末固有鍵で暗号化したデータの自動移行

- 【対象箇所】: [AndroidManifest.xml](C:/Codex/Diview/kurogane-dividend/android/app/src/main/AndroidManifest.xml)
- 【カテゴリ】: セキュリティ
- 【重大度】: 高
- 【原因分析】: 暗号化済みSharedPreferencesが通常のバックアップ対象でした。local_import_store.xmlを旧形式とAndroid12以降のクラウド/端末移行ルールで除外し、平文HTTPも禁止しました。移行には利用者が書き出したファイルが必要です。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 15. 書き出し・初期化・問い合わせの見せかけの成功

- 【対象箇所】: [ui/screens/SettingsScreen.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/SettingsScreen.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 書き出しと初期化はToastだけで実データを変更していませんでした。CSV/JSONを指定先へ書き出し、確認後の初期化は端末内データを実際に削除して空状態を保存します。問い合わせは実通信がなく入力を消していたため、送信を装わず暗号化した下書きを保存します。CSVの数式開始文字も保護します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 16. 未実装のセキュリティ設定が有効に見える

- 【対象箇所】: [ui/screens/SettingsScreen.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/SettingsScreen.kt)
- 【カテゴリ】: セキュリティ
- 【重大度】: 高
- 【原因分析】: 生体認証・プッシュ通知・米国現地税の切替が表示状態だけでした。未実装のものは準備中として無効にし、国内税引後表示と起動時マスクは実設定に接続しました。バージョンはBuildConfigの値を表示します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 17. 金額非表示中の投資元本の露出

- 【対象箇所】: [ui/screens/AnnualDividendScreen.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt)
- 【カテゴリ】: バグ
- 【重大度】: 中
- 【原因分析】: 資産カードの投資元本が金額非表示モードでも表示されていました。現在値カードと選択年カードの元本にもマスクを適用しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 18. 空データの巨大な資産と架空の実績・通知

- 【対象箇所】: [ui/screens/AnnualDividendScreen.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt)
- 【カテゴリ】: バグ
- 【重大度】: 高
- 【原因分析】: 空ポートフォリオでも固定の約2.5億円や過去年の配当を表示し、固定通知を達成済みのように表示していました。空データは0、過去年は参考推計と明記、通知は実際の保有・監視状態から生成します。銘柄診断文章は未取得API指標を欠損として扱います。財務カードの参考値が実績データでないことも明記しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 19. 不要な再描画・高負荷処理

- 【対象箇所】: [ui/screens/AnnualDividendScreen.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt)
- 【カテゴリ】: パフォーマンス
- 【重大度】: 中
- 【原因分析】: スクロール位置をComposable本体で読み、毎ピクセルLaunchedEffectを作り直していました。snapshotFlowによる監視へ変更し、StateFlow収集をライフサイクルに合わせます。CSV/DVM解析・銘柄照合・保存はバックグラウンドへ、選択行の所属判定はSetへ変更しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 20. 初回登録時の過剰なAPI呼出し

- 【対象箇所】: [viewmodel/DividendViewModel.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt)
- 【カテゴリ】: パフォーマンス
- 【重大度】: 中
- 【原因分析】: 人気銘柄を1件ずつ20本の並列通信で取得していました。現在のWorker実装で20件のページ取得が可能なため、ページ単位に集約しました。50件が最初の60候補に含まれる場合は60回から3回になります。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 21. 表示名の重複で欠損指標を判定

- 【対象箇所】: [model/PortfolioDiagnosis.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/PortfolioDiagnosis.kt)
- 【カテゴリ】: バグ
- 【重大度】: 低
- 【原因分析】: 欠損判定を表示ラベルで行っており、同じ表示ラベルを持つ異なる指標を区別できませんでした。一意な指標キーで判定します。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 22. 公開URL・互換鍵・API資格情報の確認

- 【対象箇所】: [data/DvmBackupImporter.kt](C:/Codex/Diview/kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/DvmBackupImporter.kt)
- 【カテゴリ】: セキュリティ
- 【重大度】: 中
- 【原因分析】: Androidにサーバー用APIキーは見つかりませんでした。React参照実装の固定ダミー資格情報と認証ヘッダーは削除しました。DVMの固定鍵は既存ファイルの復号専用の互換仕様として残しています。新しい端末内保存はAndroid Keystoreを使用します。署名ファイルはGit対象外にしました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

### 23. ビルド時のメモリ不足

- 【対象箇所】: [../gradle.properties](C:/Codex/Diview/kurogane-dividend/android/gradle.properties)
- 【カテゴリ】: パフォーマンス
- 【重大度】: 中
- 【原因分析】: Gradleの既定512MiBのヒープがリリースと検査を同時実行した際に不足しました。ヒープを2GiB、Metaspaceを768MiB、ワーカー数を2に設定し、ビルドを再実行しました。
- 【修正案（リファクタリング後コード）】: [完全な修正ファイルを収録したレポート](release-quality-review-full.md)。末尾の実装全文欄に、現在のファイルをコピーできる形で収録しています。

## リリース条件として残る項目

- 財務カードと過去の資産・配当グラフには参考値・推計が残っています。実績表示として出荷するには、過去時点の保有・配当・財務データへの接続が必要です。今回、未取得指標で成功した診断を出さず、参考値の表示を明記しました。
- 米国株の現地源泉徴収、外国税額控除、iDeCoの税扱いは完成していません。国内税の概算に混在口座の非課税数量を反映する修正を行いました。為替換算は既存実装の154円/USDの固定値が残っています。
- 生体認証とOSのプッシュ通知は準備中です。利用者に有効な機能であると表示しないよう変更しました。問い合わせの送信先も未設定で、今回実装したのは端末内の下書き保存です。
- DVMの共通鍵は旧形式の互換性のため残しています。旧DVMファイルを安全な新規保存形式として扱えません。新規保存はKeystoreの鍵を使用します。
- JSON書き出しは保有情報の保存です。JSONからの復元、全設定・履歴を含むバックアップは未実装です。CSV復元では取込項目の選択が必要です。株数の整数モデルのため、米国株の端数数量を取込対象として扱えません。
- Lintの警告には表示文字列のLocale、依存ライブラリの新しい版、アイコン等が残っています。今回の検査で0件になったのはスクロール位置の直接読取警告です。依存ライブラリ全体の更新・外部監査・性能の実測は実施していません。
- 検証したAndroid環境はAPI35のエミュレーターです。物理端末、API26〜34、低メモリ端末、証券会社が出力した全CSVの実ファイル、課税実務の全例は検証していません。React参照実装は依存パッケージが未導入のためビルドしていません。
- Release APKは未署名です。配布用署名・Play提出・本番公開は行っていません。

## セキュリティ確認の範囲と根拠

現行アプリ、関連スクリプト・ワークフロー、Workerソースの資格情報参照を確認しました。固定APIキー、AWS/GitHub/OpenAI形式の資格情報、秘密鍵本体、署名用鍵・ローカル環境ファイルのGit追跡は見つかりませんでした。Workerの `BEGIN PRIVATE KEY` という文字列は、環境から受け取った鍵のPEMを解析する正規表現でした。Release APKのDEXにも上記資格情報パターンは見つかりませんでした。Git履歴全体や稼働サーバーの秘密設定を監査した結果ではありません。

キャンセルの再送出は[Kotlin公式資料](https://kotlinlang.org/docs/exception-handling.html)、クラウド/端末移行の除外設定は[Android公式資料](https://developer.android.com/identity/data/autobackup)を確認しています。

## ■ 運用フェーズ（テストコードの生成）

ユニットテストは `android/app/src/test/java/`、Android/Composeテストは `android/app/src/androidTest/java/` に配置しました。テストは実ファイルとして保存済みです。完全なテストコードは[全文レポート](release-quality-review-full.md)の最終セクションに収録しています。

| テスト対象 | 主な確認項目 |
| --- | --- |
| PortfolioMath / Holding | 投信単位、混在口座の税額、数量境界、非有限値、ドイツ語ロケール、連続加算 |
| PortfolioImporter | 同一コード複数口座の合算、加重平均、二重取込、項目未選択、零数量、異常値、元データ保持 |
| BrokerCsvImporter / FundNameMatcher | 引用符、文字コード、全角数値、空データ、異常行、ヘッジ違い、かなの正規化 |
| DvmBackupImporter | 復号、認証タグ改ざん、切断ファイル、誤形式、異常数量、加重平均、非課税数量 |
| CloudflareMarketRepository | 正常JSON、null、404/503、タイムアウト、誤JSON、HTTPS、認証情報混入、リダイレクト、キャンセル、巨大応答 |
| InputSafety | サイズ上限、空ファイル、過大ストリーム、0バイト読取、Int最大上限、キャンセルとVMエラー |
| PortfolioExporter | CSV/JSON、口座合算の往復、引用符/改行、数式開始文字、投信再照合、空データ |
| StockDetailResolver | 別銘柄の混入防止、未保有銘柄、API応答のコード不一致 |
| Android Keystore / ViewModel | 保存の暗号化、復元、連続編集、取込確定、監視設定、初期化、起動時マスク、破損データ |
| Compose画面 | 未知銘柄の失敗表示、検索失敗、検索途中変更、登録連打 |

再実行はAndroidプロジェクトから行います。Java17とAndroid SDKが必要です。

```powershell
cd C:\Codex\Diview\kurogane-dividend\android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
# 接続した検証用Android端末で実行（今回実際に検証したのはエミュレーター）
.\gradlew.bat :app:connectedDebugAndroidTest
```

今回の実行ログは `C:/Codex/Diview/.buildlog/release-review-final.log` と `release-review-android-tests-final.log` です。生成されたAPKはGitに含めていません。

## 最終検証結果

- ユニットテスト：74件成功、失敗0件（既存20件に54件追加）。
- Android/Composeテスト：12件成功、失敗0件。API35のエミュレーターで実行。
- Debug APK：生成成功。
- Release APK：未署名APKの生成成功。
- Lint：エラー0件、警告89件。Navigationの独自Lint 3件は旧APIのため検査がスキップされています。このため、画面遷移に関する静的検査をすべて実行できた結果ではありません。
- Git差分の空白検査：成功。archiveの変更なし。生成APK・署名鍵の追跡なし。

[Debug APK](../android/app/build/outputs/apk/debug/app-debug.apk) / [Release APK（未署名）](../android/app/build/outputs/apk/release/app-release-unsigned.apk)
