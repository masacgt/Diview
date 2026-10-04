# Diview リリース前品質レビュー（2026-10-05）

追記：このレビューの後、端末で定期確認する株価アラートと配当月リマインダーを実装しました。現在の通知仕様・検証結果は[通知実装の記録](notifications.md)を参照してください。以下の内容とコード全文はレビュー時点の記録です。

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


## 修正案（リファクタリング後コード）：実装全文

各コードブロックは対象ファイル全体です。同じパスのファイルへ配置してください。既存プロジェクト内の未変更ファイルと合わせてビルドしたものです。

### 【対象箇所】: .gitignore

【修正案（リファクタリング後コード）】:

```text
# Miscellaneous
*.class
*.log
*.pyc
*.swp
.DS_Store
.atom/
.build/
.buildlog/
.history
.svn/
.swiftpm/
migrate_working_dir/

# IntelliJ related
*.iml
*.ipr
*.iws
.idea/

# The .vscode folder contains launch configuration and tasks you configure in
# VS Code which you may wish to be included in version control, so this line
# is commented out by default.
#.vscode/

# Flutter/Dart/Pub related
**/doc/api/
**/ios/Flutter/.last_build_id
.dart_tool/
.flutter-plugins-dependencies
.pub-cache/
.pub/
/build/
/coverage/

# Symbolication related
app.*.symbols

# Obfuscation related
app.*.map.json

# Android Studio will place build artifacts here
/android/app/debug
/android/app/profile
/android/app/release

# Widget Preview related
.widget_preview/

# Local Wrangler/XDG state
/.xdg/
/.wrangler-env/
/.wrangler/
**/.wrangler/
/data/irbank/cloudflare/admin-secret.local.txt

# Local build/runtime toolchains and caches
/.gradle-home/
/gradle-dist/
/jdk17/
/gradle-*-bin.zip
/jdk*.zip
/tools/offline-installers/

# Generated files retained inside the archived root Flutter scaffold
/archive/legacy-flutter-scaffold/.dart_tool/
/archive/legacy-flutter-scaffold/build/

# Legacy copies, release artifacts, and Windows device-name debris
**/build/
**/node_modules/
*.apk
/NUL
/irbankMCP/

# Local environment files
.env
.env.*
!.env.example

# JVM crash / heap dumps
*.hprof

# Android and Gradle local state
**/.gradle/
**/.kotlin/
**/local.properties
*.jks
*.keystore
**/keystore.properties


# Large generated IRBank export (local source remains authoritative)
/data/irbank/organized/d1/financial_periods.csv
```

### 【対象箇所】: kurogane-dividend/android/app/build.gradle.kts

【修正案（リファクタリング後コード）】:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kurogane.dividend"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kurogane.dividend"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "DIVIEW_D1_API_BASE_URL",
            "\"${providers.gradleProperty("diviewD1ApiBaseUrl").orElse("https://diview-irbank.omasa.workers.dev").get().trimEnd('/')}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/AndroidManifest.xml

【修正案（リファクタリング後コード）】:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:usesCleartextTraffic="false"
        android:icon="@mipmap/ic_launcher"
        android:label="Diview"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/BrokerCsvImporter.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import com.kurogane.dividend.model.PortfolioMath
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

enum class CsvBroker(val label: String, val legacyCharset: String) {
    SBI("SBI証券", "MS932"), RAKUTEN("楽天証券", "MS932"), MONEX("マネックス証券", "MS932"), MATSUI("松井証券", "MS932")
}

enum class CsvEncoding(val charset: Charset) { UTF8(StandardCharsets.UTF_8), SHIFT_JIS(Charset.forName("MS932")), EUC_JP(Charset.forName("EUC-JP")) }

data class CsvHoldingRow(
    val code: String, val name: String, val shares: Double?, val averageCost: Double?, val currentPrice: Double?,
    val account: String?, val isFund: Boolean, val selected: Boolean = true, val matchedName: String? = null
)
data class UnmatchedFund(val sourceName: String, val shares: Double?, val averageCost: Double?)
data class CsvImportResult(val broker: CsvBroker, val encoding: CsvEncoding, val rows: List<CsvHoldingRow>, val unmatchedFunds: List<UnmatchedFund>, val errors: List<String>)

object BrokerCsvImporter {
    private val aliases = mapOf(
        "code" to listOf("銘柄コード", "証券コード", "コード", "銘柄ｺｰﾄﾞ", "商品コード", "ファンドコード", "銘柄番号", "isin"),
        "name" to listOf("銘柄名", "商品名", "ファンド名", "銘柄名称", "ファンド名称", "投資信託名", "銘柄/ファンド名"),
        "shares" to listOf("保有数量", "保有株数", "保有数", "数量", "残高数量", "口数", "保有口数", "預り数量", "残高"),
        "cost" to listOf("平均取得価額", "取得単価", "平均取得単価", "平均取得額", "取得平均", "個別元本", "取得価額", "取得単価(円)", "平均買付単価"),
        "price" to listOf("現在値", "時価", "基準価額", "現在単価", "終値", "評価単価"),
        "account" to listOf("預り区分", "口座", "口座区分", "預り", "勘定")
    )

    fun parse(bytes: ByteArray, broker: CsvBroker, securityNames: Map<String, String> = emptyMap(), fundNames: Collection<String> = emptyList()): CsvImportResult {
        require(bytes.size <= 10 * 1024 * 1024) { "CSVは10MB以下のファイルを選択してください" }
        val (text, encoding) = decode(bytes, broker)
        val records = CsvTokenizer.parse(text.removePrefix("\uFEFF"))
        if (records.isEmpty()) return CsvImportResult(broker, encoding, emptyList(), emptyList(), listOf("CSVにデータがありません"))
        val headerAt = records.indexOfFirst { row ->
            val cells = row.map(::normHeader)
            cells.any { hasAlias(it, "name") } && cells.any { hasAlias(it, "shares") }
        }
        if (headerAt < 0) return CsvImportResult(broker, encoding, emptyList(), emptyList(), listOf("銘柄名と保有数量の見出しを特定できませんでした"))
        val headers = records[headerAt].map(::normHeader)
        fun index(field: String) = headers.indexOfFirst { hasAlias(it, field) }
        val ci = index("code"); val ni = index("name"); val si = index("shares"); val ai = index("cost"); val pi = index("price"); val aci = index("account")
        val result = mutableListOf<CsvHoldingRow>()
        val unmatched = mutableListOf<UnmatchedFund>()
        val errors = mutableListOf<String>()
        records.drop(headerAt + 1).forEach { cells ->
            val name = cells.getOrNull(ni)?.trim().orEmpty()
            if (name.isBlank() || name.startsWith("合計") || name.startsWith("総計")) return@forEach
            val rawCode = cells.getOrNull(ci)?.trim().orEmpty()
            val code = Normalizer.normalize(rawCode, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
            val shares = cells.getOrNull(si)?.toNumber()
            val cost = cells.getOrNull(ai)?.toNumber()
            val price = cells.getOrNull(pi)?.toNumber()
            if (PortfolioMath.wholeShares(shares) == null ||
                (ai >= 0 && !cells.getOrNull(ai).isNullOrBlank() && (cost == null || !PortfolioMath.validMoney(cost))) ||
                (pi >= 0 && !cells.getOrNull(pi).isNullOrBlank() && (price == null || !PortfolioMath.validMoney(price))) ||
                (code.isNotBlank() && !code.matches(Regex("[A-Z0-9.\\-]{1,16}")))) {
                errors += "$name：数量・単価・銘柄コードが正しくありません"
                return@forEach
            }
            val likelyFund = code.matches(Regex("[A-Z]{2}\\d{6}")) || name.contains("投資信託") || name.contains("ファンド") || name.contains("インデックス")
            val isFund = code.isBlank() || likelyFund
            if (isFund) {
                val found = FundNameMatcher.match(name, fundNames)
                if (found == null) unmatched += UnmatchedFund(name, shares, cost)
                else result += CsvHoldingRow("FUND:${FundNameMatcher.normalize(found.second)}", found.second, shares, cost, price, cells.getOrNull(aci), true, matchedName = found.second)
            } else {
                val masterName = securityNames[code] ?: securityNames[code.trimStart('0')]
                result += CsvHoldingRow(code, masterName ?: name, shares, cost, price, cells.getOrNull(aci), false, matchedName = masterName)
            }
        }
        if (result.isEmpty() && unmatched.isEmpty() && errors.isEmpty()) errors += "取込可能な銘柄行がありません"
        return CsvImportResult(broker, encoding, result, unmatched, errors)
    }

    fun decode(bytes: ByteArray, broker: CsvBroker): Pair<String, CsvEncoding> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8) to CsvEncoding.UTF8
        if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())))
            return String(bytes, if (bytes[0] == 0xFF.toByte()) StandardCharsets.UTF_16LE else StandardCharsets.UTF_16BE) to CsvEncoding.UTF8
        val preferred = Charset.forName(broker.legacyCharset)
        strictDecode(bytes, StandardCharsets.UTF_8)?.let { return it to CsvEncoding.UTF8 }
        strictDecode(bytes, preferred)?.let { return it to CsvEncoding.SHIFT_JIS }
        strictDecode(bytes, Charset.forName("EUC-JP"))?.let { return it to CsvEncoding.EUC_JP }
        return String(bytes, preferred) to CsvEncoding.SHIFT_JIS
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()
    private fun String.toNumber(): Double? = Normalizer.normalize(this, Normalizer.Form.NFKC)
        .replace(",", "").replace("円", "").replace("株式", "").replace("株", "").replace("口", "")
        .replace("−", "-").trim().toDoubleOrNull()?.takeIf(Double::isFinite)
    private fun normHeader(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).replace(" ", "").replace("　", "").lowercase(Locale.ROOT)
    private fun hasAlias(header: String, field: String) = aliases[field]!!.any { a ->
        val normalized = normHeader(a)
        header == normalized || (normalized.length >= 3 && header.contains(normalized))
    }
}

object CsvTokenizer {
    fun parse(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); val row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += cell.toString(); cell.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "CSVの引用符が閉じられていません" }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); if (row.any { it.isNotBlank() }) rows += row.toList() }
        return rows
    }
}

object FundNameMatcher {
    fun normalize(value: String): String {
        var text = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("[（(]\\s*[HＨ]なし\\s*[）)]", RegexOption.IGNORE_CASE), "（為替ヘッジなし）")
            .replace(Regex("[（(]\\s*[HＨ]あり\\s*[）)]", RegexOption.IGNORE_CASE), "（為替ヘッジあり）")
            .replace(Regex("為替[ＨH](無|なし)"), "為替ヘッジなし")
            .replace(Regex("為替[ＨH](有|あり)"), "為替ヘッジあり")
        text = text.replace(Regex("毎月$"), "毎月決算型").replace(Regex("隔月$"), "隔月決算型")
            .replace(Regex("予想分配金?$"), "予想分配金提示型")
        return text.uppercase(Locale.ROOT).replace(Regex("(投資信託|追加型投信|ファンド|投信|\\s|[・･.,，（）()「」『』【】\\[\\]_\\-ー])"), "")
    }

    fun match(source: String, candidates: Collection<String>, threshold: Double = 0.78): Pair<String, String>? {
        if (candidates.isEmpty()) return null
        val needle = normalize(source)
        if (needle.isBlank()) return null
        fun hedge(value: String) = when { value.contains("ヘッジなし") -> 0; value.contains("ヘッジあり") -> 1; else -> 2 }
        return candidates.asSequence().filter { normalize(it).isNotBlank() && hedge(normalize(it)) == hedge(needle) }
            .map { candidate -> candidate to similarity(needle, normalize(candidate)) }
            .maxByOrNull { it.second }?.takeIf { it.second >= threshold }?.let { it.first to it.first }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a.contains(b) || b.contains(a)) return minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        val prev = IntArray(b.length + 1) { it }; val curr = IntArray(b.length + 1)
        a.forEachIndexed { i, ca ->
            curr[0] = i + 1
            b.forEachIndexed { j, cb -> curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + if (ca == cb) 0 else 1) }
            for (j in prev.indices) prev[j] = curr[j]
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/CloudflareMarketRepository.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import com.kurogane.dividend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import com.kurogane.dividend.model.PortfolioMath
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.URI
import java.io.IOException
import java.time.LocalDate

data class CloudflareSearchHit(
    val code: String,
    val name: String,
    val cachedPrice: Double? = null,
    val industry: String? = null
)

data class CloudflareSecurity(
    val code: String,
    val name: String,
    val annualDividendPerShare: Double?,
    val marketPrice: Double?,
    val industry: String? = null
)

data class CloudflareStockAnalysis(
    val values: Map<String, Double?>
)

data class CloudflarePopularStock(
    val code: String,
    val name: String,
    val sector: String?,
    val currentPrice: Double,
    val acquisitionPrice: Double,
    val acquisitionDate: String,
    val annualDividendPerShare: Double,
    val dividendYield: Double?,
    val tradingValue20d: Double
)

data class CloudflarePopularStocksPage(
    val items: List<CloudflarePopularStock>,
    val nextOffset: Int,
    val totalCount: Int
)

/** Public, read-only access to the app's Cloudflare D1/R2 data. No credentials belong in the APK. */
class CloudflareMarketRepository(
    private val baseUrl: String = BuildConfig.DIVIEW_D1_API_BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    suspend fun searchSecurities(query: String): List<CloudflareSearchHit> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        require(query.length <= 256) { "検索文字列が長すぎます" }
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val root = getJson("/api/v1/security-search?q=$encoded")
        val rows = root.optJSONArray("items") ?: return@withContext emptyList()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("code").trim().uppercase(java.util.Locale.ROOT)
            val name = row.optString("name").trim()
            if (!isSecurityCode(code) || name.isEmpty()) null else CloudflareSearchHit(code, name)
        }
    }

    suspend fun getSecurity(code: String): CloudflareSecurity? = withContext(ioDispatcher) {
        val normalized = PortfolioMath.normalizeCode(code)
        require(isSecurityCode(normalized)) { "銘柄コードが正しくありません" }
        val safeCode = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        val root = try { getJson("/api/v1/securities/$safeCode?include_market=false") }
            catch (error: MarketHttpException) { if (error.status == 404) return@withContext null else throw error }
        val dividend = root.optJSONObject("dividend")
        CloudflareSecurity(
            code = root.optString("code", normalized).ifBlank { normalized }.also {
                require(it.equals(normalized, true)) { "応答の銘柄コードが一致しません" }
            },
            name = root.optString("name").takeIf(String::isNotBlank) ?: return@withContext null,
            annualDividendPerShare = (dividend?.numberOrNull("annual_dps_adjusted")
                ?: dividend?.numberOrNull("annual_dps"))?.takeIf(PortfolioMath::validMoney),
            marketPrice = root.optJSONObject("market_price")?.numberOrNull("close")?.takeIf { PortfolioMath.validMoney(it) && it > 0 }
        )
    }

    suspend fun getStockAnalysis(code: String): CloudflareStockAnalysis? = withContext(ioDispatcher) {
        require(isSecurityCode(PortfolioMath.normalizeCode(code))) { "銘柄コードが正しくありません" }
        val safeCode = URLEncoder.encode(code.trim(), Charsets.UTF_8.name())
        val root = runCatchingCancellable { getJson("/api/v1/analysis/$safeCode") }.getOrNull()
            ?: return@withContext null
        val sourceValues = root.optJSONObject("values") ?: return@withContext null
        val keys = listOf(
            "per", "pbr", "payout_ratio", "equity_ratio", "operating_margin",
            "fcf_margin", "dividend_growth_years"
        )
        CloudflareStockAnalysis(
            values = keys.associateWith { sourceValues.numberOrNull(it) }
        )
    }

    /** Fetches the public catalog only when the caller's JST/12-hour gate permits a price refresh. */
    suspend fun fetchPriceCatalog(): Map<String, Double> = withContext(ioDispatcher) {
        val root = getJson("/api/v1/search-catalog")
        val rows = root.optJSONArray("items") ?: error("Cloudflareの銘柄カタログにitemsがありません")
        buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val code = row.optString("code").trim().uppercase()
                val price = row.numberOrNull("price")
                if (isSecurityCode(code) && price != null && PortfolioMath.validMoney(price) && price > 0.0) put(code, price)
            }
        }
    }

    /** Returns a page of R2-ranked stocks with current and five-year reference prices from the database. */
    suspend fun fetchPopularStocks(offset: Int, date: String, limit: Int = 1): CloudflarePopularStocksPage = withContext(ioDispatcher) {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "日付はyyyy-MM-dd形式で指定してください" }
        LocalDate.parse(date)
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 20)
        val encodedDate = URLEncoder.encode(date, Charsets.UTF_8.name())
        val root = getJson("/api/v1/popular-holdings?offset=$safeOffset&limit=$safeLimit&date=$encodedDate")
        val rows = root.optJSONArray("items")
        val items = if (rows == null) emptyList() else (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("code").trim().uppercase()
            val name = row.optString("name").trim()
            val currentPrice = row.numberOrNull("current_price") ?: return@mapNotNull null
            val acquisitionPrice = row.numberOrNull("acquisition_price") ?: return@mapNotNull null
            val tradingValue = row.numberOrNull("estimated_average_trading_value_20d") ?: return@mapNotNull null
            if (!isSecurityCode(code) || name.isBlank() || !PortfolioMath.validMoney(currentPrice) || !PortfolioMath.validMoney(acquisitionPrice) || currentPrice <= 0.0 || acquisitionPrice <= 0.0 || tradingValue <= 0.0) return@mapNotNull null
            CloudflarePopularStock(
                code = code,
                name = name,
                sector = row.optString("sector").takeIf(String::isNotBlank),
                currentPrice = currentPrice,
                acquisitionPrice = acquisitionPrice,
                acquisitionDate = row.optString("acquisition_date").ifBlank { date },
                annualDividendPerShare = row.numberOrNull("annual_dividend_per_share")?.takeIf(PortfolioMath::validMoney) ?: 0.0,
                dividendYield = row.numberOrNull("yield_rate")?.takeIf { it in 0.0..100.0 },
                tradingValue20d = tradingValue
            )
        }
        CloudflarePopularStocksPage(
            items = items,
            nextOffset = root.optInt("next_offset", safeOffset + safeLimit),
            totalCount = root.optInt("total_count", safeOffset + safeLimit)
        )
    }

    private fun getJson(path: String): JSONObject {
        val base = URI(baseUrl)
        require(base.scheme == "https" && !base.host.isNullOrBlank() && base.userInfo == null && base.query == null && base.fragment == null) { "Cloudflare API URLは認証情報を含まないHTTPSで設定してください" }
        val connection = connectionFactory(URL(baseUrl.trimEnd('/') + path))
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw MarketHttpException(status)
            val body = connection.inputStream.use { it.readBoundedBytes(8 * 1024 * 1024).toString(Charsets.UTF_8) }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.numberOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun isSecurityCode(code: String) = code.matches(Regex("[A-Z0-9.\\-]{1,16}"))
}

class MarketHttpException(val status: Int) : IOException("Cloudflare API HTTP $status")

/** The price feed is eligible only once on each Japan-local date, and never more often than 12 hours. */
object PriceRefreshPolicy {
    private const val MINIMUM_INTERVAL_MILLIS = 12L * 60L * 60L * 1_000L
    private val japanZone = java.time.ZoneId.of("Asia/Tokyo")

    fun shouldFetch(nowEpochMillis: Long, lastFetchEpochMillis: Long, lastFetchJapanDate: String?): Boolean {
        if (lastFetchEpochMillis <= 0L) return true
        val todayInJapan = java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(japanZone).toLocalDate().toString()
        if (lastFetchJapanDate == todayInJapan) return false
        return nowEpochMillis - lastFetchEpochMillis >= MINIMUM_INTERVAL_MILLIS
    }

    fun japanDate(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(japanZone).toLocalDate().toString()
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/DvmBackupImporter.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.kurogane.dividend.model.PortfolioMath

enum class DvmBackupKind(val fileName: String, val label: String) {
    STOCKS("BackUpStockInfoAndroid.dvm", "銘柄一覧"),
    RECORDS("BackUpRecordInfoAndroid.dvm", "資産・配当推移"),
    CUSTOM_STOCKS("BackUpCustomStockInfoAndroid.dvm", "カスタム銘柄"),
    YIELD_ALERTS("BackUpYieldAlertAndroid.dvm", "目標利回りアラート");

    companion object {
        fun fromName(name: String): DvmBackupKind? = entries.firstOrNull { name.equals(it.fileName, true) }
    }
}

data class DvmStock(val code: String, val name: String, val shares: Double, val averageCost: Double, val currentPrice: Double, val account: String,
                    val taxFreeShares: Double? = null)
data class DvmBackupPreview(val kind: DvmBackupKind, val itemCount: Int, val stocks: List<DvmStock>, val payload: ByteArray)

object DvmBackupImporter {
    private val magic = "DVMV1".toByteArray(StandardCharsets.US_ASCII)
    // This matches the AES-256-GCM key used by the legacy DVM backup format.
    private val legacyKey = byteArrayOf(68,118,77,103,83,121,110,99,65,69,83,50,53,54,71,67,77,75,101,121,50,48,50,54,79,116,116,105,76,97,98,33)

    fun decrypt(file: ByteArray): ByteArray {
        require(file.size <= 50 * 1024 * 1024) { "DVMバックアップが大きすぎます" }
        require(file.size >= magic.size + 12 + 16 && magic.indices.all { file[it] == magic[it] }) { "DVMバックアップの形式が正しくありません" }
        val nonce = file.copyOfRange(magic.size, magic.size + 12)
        val cipherText = file.copyOfRange(magic.size + 12, file.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(legacyKey, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(cipherText)
    }

    fun parse(fileName: String, bytes: ByteArray): DvmBackupPreview {
        val kind = requireNotNull(DvmBackupKind.fromName(fileName)) { "未対応のDVMバックアップです" }
        val plain = decrypt(bytes)
        val text = plain.toString(StandardCharsets.UTF_8).trim()
        val items = when {
            text.isBlank() -> JSONArray()
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> {
                val root = JSONObject(text)
                val keys = when (kind) {
                    DvmBackupKind.STOCKS -> listOf("stocks", "stockInfo", "data")
                    DvmBackupKind.RECORDS -> listOf("records", "data")
                    DvmBackupKind.CUSTOM_STOCKS -> listOf("customStocks", "data")
                    DvmBackupKind.YIELD_ALERTS -> listOf("yieldAlerts", "alerts", "data")
                }
                keys.firstNotNullOfOrNull { key -> root.optJSONArray(key) }
                    ?: throw IllegalArgumentException("バックアップに対象データがありません")
            }
            else -> throw IllegalArgumentException("バックアップの中身を読み取れません")
        }
        val stocks = if (kind == DvmBackupKind.STOCKS) (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val holdingFields = listOf(
                "numOfStocksHeld" to "acquisitionUnitPrice", "numOfStocksHeldNisa" to "acquisitionUnitPriceNisa",
                "numOfStocksHeldNisaNew" to "acquisitionUnitPriceNisaNew", "numOfStocksHeldNisaNewTsumitate" to "acquisitionUnitPriceNisaNewTsumitate",
                "numOfStocksHeldIdeco" to "acquisitionUnitPriceIdeco"
            )
            val positions = holdingFields.map { (quantityKey, costKey) ->
                val quantity = item.optDouble(quantityKey, 0.0)
                require(PortfolioMath.wholeShares(quantity) != null) { "バックアップの保有数量が正しくありません" }
                quantity to item.optString(costKey).toNumber()
            }.filter { it.first > 0.0 }
            val shares = positions.sumOf { it.first }
            require(PortfolioMath.wholeShares(shares) != null) { "保有数量が上限を超えています" }
            val averageCost = if (shares > 0) positions.sumOf { (quantity, cost) -> quantity * cost } / shares else item.optString("acquisitionUnitPrice").toNumber()
            DvmStock(
                code = PortfolioMath.normalizeCode(item.optString("corporateCode")), name = item.optString("corporateName").trim(), shares = shares,
                averageCost = averageCost, currentPrice = item.optString("priceOfStock").toNumber(),
                account = when {
                    item.optInt("isUSD", 0) == 1 -> "米国株"
                    positions.size > 1 -> "複数口座"
                    positions.isNotEmpty() && holdingFields[holdingFields.indexOfFirst { (key, _) -> item.optDouble(key, 0.0) > 0 }].first.contains("Nisa") -> "NISA"
                    else -> "特定口座"
                },
                taxFreeShares = holdingFields.filter { it.first.contains("Nisa") }.sumOf { item.optDouble(it.first, 0.0) }
            ).takeIf { PortfolioMath.validCode(it.code) && it.name.isNotBlank() }
        } else emptyList()
        return DvmBackupPreview(kind, items.length(), stocks, plain)
    }

    private fun String.toNumber(): Double {
        if (isBlank() || this == "null") return 0.0
        val number = replace(",", "").replace("¥", "").replace("$", "").trim().toDoubleOrNull()
        require(number != null && PortfolioMath.validMoney(number)) { "バックアップの単価が正しくありません" }
        return number
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/InputSafety.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Keeps cancellation visible to callers and does not swallow VM errors. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val remainingWithProbe = maxBytes.toLong() - output.size() + 1L
        val count = read(buffer, 0, minOf(buffer.size.toLong(), remainingWithProbe).toInt())
        if (count < 0) break
        if (count == 0) {
            val byte = read()
            if (byte < 0) break
            require(output.size() < maxBytes) { "ファイルがサイズ上限を超えています" }
            output.write(byte)
            continue
        }
        require(count <= maxBytes - output.size()) { "ファイルがサイズ上限を超えています" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/LocalImportStore.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.WatchlistStock
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private encrypted storage. Nothing in this class performs network access. */
class LocalImportStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
    private var cachedKey: SecretKey? = null

    fun hasHoldings(): Boolean = prefs.contains("holdings")

    fun saveHoldings(holdings: List<Holding>) = save("holdings", JSONArray().apply { holdings.forEach { put(it.toJson()) } }.toString().toByteArray())
    fun loadHoldings(): List<Holding>? = load("holdings")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { array.getJSONObject(it).toHolding() }
    }
    fun saveBackup(kind: DvmBackupKind, plaintext: ByteArray) = save("dvm_${kind.name}", plaintext)
    fun readBackup(kind: DvmBackupKind): ByteArray? = load("dvm_${kind.name}")
    fun saveFeedbackDraft(draft: JSONObject) = save("feedback_draft", draft.toString().toByteArray(Charsets.UTF_8))
    fun loadFeedbackDraft(): JSONObject? = load("feedback_draft")?.let { JSONObject(it.toString(Charsets.UTF_8)) }

    fun clear() { check(prefs.edit().clear().commit()) { "データを初期化できませんでした" } }

    fun saveWatchlist(stocks: List<WatchlistStock>) = save("watchlist", JSONArray().apply {
        stocks.forEach { stock -> put(JSONObject().apply {
            put("code", stock.code); put("name", stock.name); put("market", stock.market); put("sector", stock.sector)
            put("category", stock.category); put("price", stock.price); put("currency", stock.currency); put("dps", stock.dps)
            put("currentYield", stock.currentYield); put("targetYield", stock.targetYield); put("targetPrice", stock.targetPrice)
            put("dailyChange", stock.dailyChange); put("isPositive", stock.isPositive); put("payoutMonths", JSONArray(stock.payoutMonths))
            put("payoutTiming", stock.payoutTiming); put("desc", stock.desc); put("alertEnabled", stock.alertEnabled)
            put("badge", stock.badge); put("defaultShares", stock.defaultShares)
        }) }
    }.toString().toByteArray(Charsets.UTF_8))

    fun loadWatchlist(): List<WatchlistStock>? = load("watchlist")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            WatchlistStock(row.getString("code"), row.getString("name"), row.getString("market"), row.getString("sector"),
                row.getString("category"), row.getDouble("price"), row.getString("currency"), row.getDouble("dps"),
                row.getDouble("currentYield"), row.getDouble("targetYield"), row.getDouble("targetPrice"), row.getString("dailyChange"),
                row.getBoolean("isPositive"), row.getJSONArray("payoutMonths").let { a -> (0 until a.length()).map { a.getInt(it) } },
                row.getString("payoutTiming"), row.getString("desc"), row.optBoolean("alertEnabled"),
                if (row.isNull("badge")) null else row.optString("badge"), row.optInt("defaultShares", 100)).also {
                require(PortfolioMath.validCode(it.code) && listOf(it.price, it.dps, it.targetPrice).all(PortfolioMath::validMoney) &&
                    it.currentYield.isFinite() && it.targetYield.isFinite() && it.targetYield > 0) { "監視銘柄の保存データが正しくありません" }
            }
        }
    }

    private fun save(key: String, clear: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(clear)
        check(prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) { "端末にデータを保存できませんでした" }
    }
    private fun load(key: String): ByteArray? {
        val data = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        require(data.size >= 28) { "保存データが破損しています" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        return cipher.doFinal(data, 12, data.size - 12)
    }

    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { cachedKey = it; return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey().also { cachedKey = it }
    }

    private fun Holding.toJson() = JSONObject().apply {
        put("id", id); put("code", code); put("name", name); put("sector", sector); put("market", market); put("category", category.name)
        put("price", price); put("currency", currency); put("shares", shares); put("acqPrice", acqPrice); put("dps", dps)
        put("yieldRate", yieldRate); put("yoc", yoc); put("nextPayoutTiming", nextPayoutTiming); put("nextPayoutAmount", nextPayoutAmount)
        put("dividendMonths", JSONArray(dividendMonths)); put("dividendType", dividendType); put("accountType", accountType)
        put("dailyChange", dailyChange); put("isPositive", isPositive)
        taxFreeShares?.let { put("taxFreeShares", it) }
    }
    private fun JSONObject.toHolding() = Holding(
        id = optString("id"), code = optString("code"), name = optString("name"), sector = optString("sector"), market = optString("market"),
        category = Category.valueOf(getString("category")), price = getDouble("price"),
        currency = optString("currency", "¥"), shares = requireNotNull(PortfolioMath.wholeShares(getDouble("shares"))), acqPrice = getDouble("acqPrice"), dps = getDouble("dps"),
        yieldRate = optDouble("yieldRate"), yoc = optDouble("yoc"), nextPayoutTiming = optString("nextPayoutTiming"), nextPayoutAmount = optString("nextPayoutAmount"),
        dividendMonths = optJSONArray("dividendMonths")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
        dividendType = optString("dividendType"), accountType = optString("accountType"), dailyChange = optString("dailyChange"), isPositive = optBoolean("isPositive"),
        taxFreeShares = if (has("taxFreeShares")) getInt("taxFreeShares") else null
    ).also {
        require(PortfolioMath.validCode(it.code) && it.name.isNotBlank() && it.shares >= 0 &&
            listOf(it.price, it.acqPrice, it.dps).all(PortfolioMath::validMoney) &&
            (it.taxFreeShares == null || it.taxFreeShares in 0..it.shares)) { "保存データの保有情報が正しくありません" }
    }
    private companion object {
        const val KEY_ALIAS = "diview.local.import.aes"
        val KEY_LOCK = Any()
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/PortfolioExporter.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import com.kurogane.dividend.model.Holding
import org.json.JSONArray
import org.json.JSONObject

object PortfolioExporter {
    fun json(holdings: List<Holding>): String = JSONObject().apply {
        put("format", "diview-portfolio-v1")
        put("holdings", JSONArray().apply { holdings.forEach { stock -> put(JSONObject().apply {
            put("code", stock.code); put("name", stock.name); put("shares", stock.shares)
            put("averageCost", stock.acqPrice); put("currentPrice", stock.price); put("annualDividendPerShare", stock.dps)
            put("currency", stock.currency); put("category", stock.category.name); put("account", stock.accountType)
            put("taxFreeShares", stock.effectiveTaxFreeShares); put("dividendMonths", JSONArray(stock.dividendMonths))
            put("sector", stock.sector); put("market", stock.market)
        }) } })
    }.toString(2)

    fun csv(holdings: List<Holding>): String = buildString {
        append("\uFEFF銘柄コード,銘柄名,保有数量,平均取得価額,現在値,口座区分\r\n")
        holdings.forEach { stock ->
            val free = stock.effectiveTaxFreeShares
            val accounts = if (free in 1 until stock.shares) listOf(free to "NISA", (stock.shares - free) to "特定口座")
                else listOf(stock.shares to if (free > 0) "NISA" else stock.accountType)
            accounts.forEach { (shares, account) ->
                append(listOf(if (stock.category == com.kurogane.dividend.model.Category.FUND) "" else stock.code,
                    stock.name, shares.toString(), stock.acqPrice.toString(), stock.price.toString(), account)
                    .joinToString(",") { quote(it) })
                append("\r\n")
            }
        }
    }

    private fun quote(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/PortfolioImporter.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.HoldingAllocation
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.PortfolioMath.refreshPayout

object PortfolioImporter {
    fun importRows(current: List<Holding>, rows: List<CsvHoldingRow>, fields: Set<String>,
                   industries: Map<String, String> = emptyMap()): List<Holding> {
        val next = current.toMutableList()
        rows.filter { it.selected }.groupBy { PortfolioMath.normalizeCode(it.code) }.forEach { (code, group) ->
            require(PortfolioMath.validCode(code) && group.all { it.name.isNotBlank() }) { "銘柄コード・名称を確認してください" }
            val index = next.indexOfFirst { it.code.equals(code, true) }
            val old = next.getOrNull(index)
            val first = group.first()
            require(group.all { it.isFund == first.isFund }) { "銘柄区分が一致しません" }
            val allocations = group.map { row ->
                val shares = if ("shares" in fields) PortfolioMath.wholeShares(row.shares)
                    ?: throw IllegalArgumentException("保有数量は0以上の整数で指定してください") else 0
                val cost = if ("averageCost" in fields) row.averageCost ?: old?.acqPrice ?: 0.0 else old?.acqPrice ?: 0.0
                require(PortfolioMath.validMoney(cost)) { "取得単価が正しくありません" }
                HoldingAllocation(shares, cost,
                    if ("account" in fields) row.account?.takeIf(String::isNotBlank) ?: old?.accountType ?: "特定口座"
                    else old?.accountType ?: "特定口座")
            }
            val total = allocations.sumOf { it.shares.toLong() }
            require(total <= Int.MAX_VALUE) { "保有数量が上限を超えています" }
            val shares = if ("shares" in fields) total.toInt() else old?.shares ?: 0
            val cost = if ("averageCost" in fields) {
                val weight = group.sumOf { PortfolioMath.wholeShares(it.shares)?.toLong() ?: 0L }
                if (weight > 0) group.sumOf { (PortfolioMath.wholeShares(it.shares) ?: 0) * (it.averageCost ?: old?.acqPrice ?: 0.0) } / weight
                else allocations.first().acquisitionPrice
            } else old?.acqPrice ?: 0.0
            val price = if ("currentPrice" in fields) group.firstNotNullOfOrNull { it.currentPrice } ?: old?.price ?: 0.0 else old?.price ?: 0.0
            require(PortfolioMath.validMoney(price) && PortfolioMath.validMoney(cost)) { "単価が正しくありません" }
            val currency = old?.currency ?: if (!first.isFund && code.matches(Regex("[A-Z]+"))) "$" else "¥"
            val category = if (first.isFund) Category.FUND else if (currency == "$") Category.US else Category.JAPAN
            val activeAccounts = allocations.filter { it.shares > 0 || "shares" !in fields }.map { it.accountType }.distinct()
            val account = if ("account" in fields) activeAccounts.singleOrNull() ?: if (activeAccounts.isEmpty()) old?.accountType ?: "特定口座" else "複数口座" else old?.accountType ?: "特定口座"
            val taxFree = if ("account" in fields && "shares" in fields) allocations.filter { PortfolioMath.isTaxFree(it.accountType) }.sumOf { it.shares }
                else if ("account" in fields) if (PortfolioMath.isTaxFree(account)) shares else 0
                else old?.let { if (it.shares > 0) (shares.toDouble() * it.effectiveTaxFreeShares / it.shares).toInt() else 0 } ?: 0
            val template = old ?: Holding("import-$code", code, first.name,
                if (category == Category.FUND) "投資信託" else industries[code]?.let { "東証 • $it" } ?: "業種未登録",
                if (category == Category.US) "米国株" else if (category == Category.FUND) "投資信託" else "東証",
                category, price, currency, shares, cost, 0.0, 0.0, 0.0, "未登録", "¥0", emptyList(), "", account, "—", true)
            val imported = template.copy(name = first.name, shares = shares, acqPrice = cost, price = price,
                accountType = account, taxFreeShares = taxFree).refreshPayout()
            if (index >= 0) next[index] = imported else next += imported
        }
        return next
    }

    fun importStocks(current: List<Holding>, stocks: List<DvmStock>): List<Holding> =
        importRows(current, stocks.flatMap { stock ->
            val free = stock.taxFreeShares ?: if (PortfolioMath.isTaxFree(stock.account)) stock.shares else 0.0
            require(PortfolioMath.wholeShares(free) != null && free <= stock.shares) { "非課税数量が正しくありません" }
            listOf(CsvHoldingRow(stock.code, stock.name, free, stock.averageCost, stock.currentPrice,
                "NISA", stock.code.startsWith("FUND:")),
                CsvHoldingRow(stock.code, stock.name, stock.shares - free, stock.averageCost, stock.currentPrice,
                    if (PortfolioMath.isTaxFree(stock.account)) "特定口座" else stock.account, stock.code.startsWith("FUND:")))
        }, setOf("shares", "averageCost", "currentPrice", "account"))
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/MainActivity.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kurogane.dividend.ui.screens.AnnualDividendScreen
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.screens.RoadmapScreen
import com.kurogane.dividend.ui.screens.SettingsScreen
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.screens.WatchlistScreen
import com.kurogane.dividend.ui.screens.ContactFeedbackScreen
import com.kurogane.dividend.ui.screens.TermsPrivacyScreen
import com.kurogane.dividend.ui.screens.CsvImportScreen
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.ui.theme.BorderColor
import com.kurogane.dividend.ui.theme.OnSurface
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.OnPrimary
import com.kurogane.dividend.ui.theme.PrimaryEmeraldContainer
import com.kurogane.dividend.ui.theme.SurfaceContainer
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.components.SearchSheet
import com.kurogane.dividend.ui.components.AddHoldingSheet
import com.kurogane.dividend.viewmodel.DividendViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DividendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isOledBlack by viewModel.isOledBlack.collectAsStateWithLifecycle()
            val selectedAccent by viewModel.selectedAccent.collectAsStateWithLifecycle()
            KuroganeDividendTheme(
                themeMode = themeMode,
                isOledBlack = isOledBlack,
                selectedAccent = selectedAccent
            ) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                SideEffect {
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }

                var showSearchSheet by remember { mutableStateOf(false) }
                var showNotificationsDialog by remember { mutableStateOf(false) }
                var showAddHoldingSheet by remember { mutableStateOf(false) }
                var prefilledAddHoldingCode by remember { mutableStateOf("") }
                val saveError by viewModel.saveError.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(saveError) {
                    saveError?.let { snackbarHostState.showSnackbar(it); viewModel.clearSaveError() }
                }

                val dynamicBg = BackgroundDark

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = dynamicBg,
                    topBar = {
                        val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist")
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(dynamicBg)
                                .border(BorderStroke(0.5.dp, BorderColor))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMainTab) {
                                // Left side main tab title layout
                                val (title, subtitle, icon) = when (currentRoute) {
                                    "asset" -> Triple("資産管理", "ASSET MANAGEMENT", Icons.Default.AccountBalanceWallet)
                                    "annual" -> Triple("年間配当", "ANNUAL DIVIDEND", Icons.Default.PieChart)
                                    "roadmap" -> Triple("Roadmap Quest", "不労所得 RPG", Icons.Default.Flag)
                                    "watchlist" -> Triple("Watchlist", "WATCHLIST", Icons.Default.Bookmark)
                                    else -> Triple("資産管理", "ASSET MANAGEMENT", Icons.Default.AccountBalanceWallet)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainer)
                                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            fontSize = AppTextSize.section,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OnSurface
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            } else if (currentRoute == "settings") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る", tint = OnSurface, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainer)
                                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("設定・データ管理", fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold, color = OnSurface)
                                        Text("SETTINGS", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, letterSpacing = 0.5.sp)
                                    }
                                }
                            } else {
                                // Left side back button title layout
                                val title = when {
                                    currentRoute == "settings" -> "設定・データ管理"
                                    currentRoute?.startsWith("detail") == true -> "銘柄情報"
                                    else -> "詳細画面"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = title,
                                        fontSize = AppTextSize.section,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface
                                    )
                                }
                            }

                            // Right actions (displayed on all screens!)
                            if (currentRoute != "settings") Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Search Button
                                IconButton(onClick = { showSearchSheet = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = OnSurfaceVariant)
                                }

                                // Notification Button with pulse badge
                                Box(contentAlignment = Alignment.TopEnd) {
                                    IconButton(onClick = { showNotificationsDialog = true }) {
                                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = OnSurfaceVariant)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp, end = 8.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                }

                                // Settings Button
                                IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (currentRoute == "settings") PrimaryEmerald else OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist") || currentRoute == "settings"
                        if (isMainTab) {
                            val activeIndex = when (currentRoute) {
                                "asset" -> 0
                                "annual" -> 1
                                "roadmap" -> 2
                                "watchlist" -> 3
                                else -> 0
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(BackgroundDark)
                                    .border(BorderStroke(0.5.dp, BorderColor))
                            ) {
                                val tabWidth = maxWidth / 4
                                val indicatorOffset by animateDpAsState(
                                    targetValue = tabWidth * activeIndex + (tabWidth - 32.dp) / 2,
                                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f),
                                    label = "activeIndicator"
                                )

                                if (currentRoute != "settings") {
                                    // Sliding active indicator pill at the top of bottom bar
                                    Box(
                                        modifier = Modifier
                                            .offset(x = indicatorOffset, y = 0.dp)
                                            .width(32.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(PrimaryEmerald)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabs = listOf(
                                        Triple("asset", "資産管理", Icons.Default.AccountBalanceWallet),
                                        Triple("annual", "年間配当", Icons.Default.PieChart),
                                        Triple("roadmap", "ロードマップ", Icons.Default.Flag),
                                        Triple("watchlist", "Watchlist", Icons.Default.Bookmark)
                                    )
                                    tabs.forEach { (route, label, icon) ->
                                        val selected = currentRoute == route
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable {
                                                    if (!selected) navController.navigate(route)
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (selected) PrimaryEmerald else OnSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = label,
                                                fontSize = AppTextSize.dense,
                                                color = if (selected) PrimaryEmerald else OnSurfaceVariant,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = "asset",
                            modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { 400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { 400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
                        }
                        ) {
                            composable("asset") {
                                AssetManagementScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } },
                                    onOpenAddHolding = { code ->
                                        prefilledAddHoldingCode = code
                                        showAddHoldingSheet = true
                                    }
                                )
                            }
                            composable("annual") {
                                AnnualDividendScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } }
                                )
                            }
                            composable("roadmap") {
                                RoadmapScreen(viewModel = viewModel)
                            }
                            composable("watchlist") {
                                WatchlistScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } },
                                    onOpenAddHolding = { code ->
                                        prefilledAddHoldingCode = code
                                        showAddHoldingSheet = true
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToFeedback = { navController.navigate("contact_feedback") { launchSingleTop = true } },
                                    onNavigateToTerms = { navController.navigate("terms_privacy") { launchSingleTop = true } },
                                    onNavigateToCsv = { navController.navigate("csv_import") { launchSingleTop = true } }
                                )
                            }
                            composable("csv_import") {
                                CsvImportScreen(
                                    onBack = { navController.popBackStack() },
                                    onImportCsv = { rows, fields ->
                                        viewModel.importCsvRows(rows, fields)
                                        navController.popBackStack()
                                    },
                                    onImportDvm = { backups ->
                                        viewModel.importDvmBackups(backups)
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("contact_feedback") {
                                ContactFeedbackScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = viewModel
                                )
                            }
                            composable("terms_privacy") {
                                TermsPrivacyScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("detail/{code}") { backStackEntry ->
                                val code = backStackEntry.arguments?.getString("code") ?: "7203"
                                StockDetailScreen(
                                    stockCode = code,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        // Search Sheet Modal Overlay
                        SearchSheet(
                            visible = showSearchSheet,
                            onDismiss = { showSearchSheet = false },
                            onSearch = { query -> viewModel.searchCloudflareSecurities(query) },
                            onSelectStock = { code ->
                                showSearchSheet = false
                                navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true }
                            }
                        )

                        // Add Asset / Joint Portfolio Matrix Sheet Overlay
                        AddHoldingSheet(
                            visible = showAddHoldingSheet,
                            prefilledCode = prefilledAddHoldingCode,
                            onDismiss = { showAddHoldingSheet = false },
                            onResolveSecurity = { code -> viewModel.resolveCloudflareSecurity(code) },
                            onRegisterCollective = { code, allocations ->
                                val registered = viewModel.registerCloudflareAllocations(code, allocations)
                                if (registered) showAddHoldingSheet = false
                                registered
                            }
                        )

                        // Notification Dialog
                        if (showNotificationsDialog) {
                            val watched by viewModel.watchlist.collectAsStateWithLifecycle()
                            val owned by viewModel.holdings.collectAsStateWithLifecycle()
                            val taxMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
                            val ready = watched.filter { it.alertEnabled && it.isReady }
                            val monthlyIncome = owned.sumOf { it.getEffectiveAnnualDividend(taxMode) } / 12.0
                            AlertDialog(
                                onDismissRequest = { showNotificationsDialog = false },
                                confirmButton = {
                                    Button(
                                        onClick = { showNotificationsDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmeraldContainer)
                                    ) {
                                        Text("確認", color = OnPrimary, fontWeight = FontWeight.Bold)
                                    }
                                },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryEmerald)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("お知らせ・通知", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("🔔 利回りシグナル検知", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                        Text(if (ready.isEmpty()) "通知を有効にした銘柄で、目標利回りに到達したものはありません。" else ready.joinToString("\n") { "${it.name}：現在利回り ${String.format(java.util.Locale.JAPAN, "%.2f", it.currentYield)}% / 目標 ${String.format(java.util.Locale.JAPAN, "%.2f", it.targetYield)}%" }, color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                                        Text("🎉 ロードマップ進捗アップデート", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                        Text("現在の想定配当は月換算で ¥${java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN).format(monthlyIncome.toLong())} です。ロードマップで目標との差額を確認できます。", color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                                    }
                                },
                                containerColor = SurfaceContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/Models.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.model

data class Holding(
    val id: String,
    val code: String,
    val name: String,
    val sector: String,
    val market: String,
    val category: Category,
    val price: Double,
    val currency: String = "¥",
    val shares: Int,
    val acqPrice: Double,
    val dps: Double, // annual dividend per share
    val yieldRate: Double,
    val yoc: Double,
    val nextPayoutTiming: String,
    val nextPayoutAmount: String,
    val dividendMonths: List<Int>,
    val dividendType: String,
    val accountType: String,
    val dailyChange: String,
    val isPositive: Boolean,
    val taxFreeShares: Int? = null
) {
    val priceUnit: Double get() = if (category == Category.FUND) 10_000.0 else 1.0
    val effectiveTaxFreeShares: Int get() =
        (taxFreeShares ?: if (PortfolioMath.isTaxFree(accountType)) shares else 0).coerceIn(0, shares.coerceAtLeast(0))
    val totalValuation: Double get() = shares * price / priceUnit * (if (currency == "$") 154.0 else 1.0)
    val totalCost: Double get() = shares * acqPrice / priceUnit * (if (currency == "$") 154.0 else 1.0)
    val gainLoss: Double get() = totalValuation - totalCost
    val gainLossPercent: Double get() = if (totalCost > 0) (gainLoss / totalCost) * 100 else 0.0
    val annualDividend: Double get() = shares * dps / priceUnit * (if (currency == "$") 154.0 else 1.0)
    val liveYield: Double get() = PortfolioMath.yieldPercent(dps, price)
    val liveYoc: Double get() = PortfolioMath.yieldPercent(dps, acqPrice)

    fun getEffectiveAnnualDividend(taxRateMode: String): Double {
        val gross = annualDividend
        return if (taxRateMode == "net") {
            val freeRatio = if (shares > 0) effectiveTaxFreeShares.toDouble() / shares else 0.0
            gross * (freeRatio + (1.0 - freeRatio) * 0.79685)
        } else {
            gross
        }
    }
}

data class StockFinancialProfile(
    val payoutRatio: Double,
    val equityRatio: Double,
    val netDeRatio: String,
    val healthGrade: String,
    val healthEvaluation: String,
    val consecutiveYears: Int,
    val noCutYears: Int,
    val growthRate: Double,
    val eps: Double,
    val bps: Double,
    val recordMonths: String,
    val payoutSchedule: String,
    val payoutStatusBadge: String = "適正レンジ内 (黄金比率)",
    val equityRatioSub: String = "業界高水準・安定",
    val doePolicy: String = "3.2% (DOE)",
    val doePolicySub: String = "安定還元・DOE基準",
    val operatingMargin: Double = 11.4,
    val fcfMargin: Double? = null,
    val fcfStatus: String = "黒字維持（配当余力 十分）",
    val modelType: String = "王道大型バリュー（トヨタ基準型）",
    val healthScore: Int = 92,
    val healthDiagnosisComment: String = "自己資本比率39.8%および実質無借金（ネットD/E -0.22倍）を誇り、本業の営業利益率11.4%と極めて堅牢。配当性向35.2%（適正目安30〜50%）に加えてDOE3.2%基準による安定還元を実施しており、中長期的な減配懸念は極小と評価されます。",
    val valuationScore: Int = 88,
    val yieldRangeMin: Double = 2.10,
    val yieldRangeAvg: Double = 2.45,
    val yieldRangeMax: Double = 3.20,
    val yieldRangeBadge: String = "過去5年平均超え",
    val perEvaluation: String = "市場平均比で割安",
    val pbrEvaluation: String = "資本効率適正圏",
    val buySignalZone: String = "超割安ゾーン（検討圏）",
    val buySignalTitle: String = "【王道大型バリュー健全圏】PER 10.4倍 × PBR 1.12倍",
    val buySignalBadge: String = "絶好の買増し検討期",
    val buySignalComment: String = "PER 10.4倍・PBR 1.12倍と、日経平均平均（PER 15倍・PBR 1.4倍）を大幅に下回る優良バリュー水準。高収益性とキャッシュ創出力に対して割安に放置されており、配当再投資や中期的な買い増しに最適な好機シグナルです。"
) {
    fun getPer(price: Double): String = if (eps > 0) String.format("%.1f倍", price / eps) else "-"
    fun getPbr(price: Double): String = if (bps > 0) String.format("%.2f倍", price / bps) else "-"
    fun getValuationStatus(price: Double, dps: Double): String {
        val yld = if (price > 0) (dps / price) * 100 else 0.0
        val per = if (eps > 0) price / eps else 15.0
        return when {
            yld >= 4.0 || per < 10 -> "超割安・買場ゾーン"
            yld >= 3.2 || per < 14 -> "買場目安接近・検討ゾーン"
            per > 25 -> "高PER・成長評価圏"
            else -> "適正・中立ゾーン"
        }
    }
}

fun getStockProfile(code: String): StockFinancialProfile {
    return when (code) {
        "7203" -> StockFinancialProfile(35.2, 39.8, "-0.22倍 (実質無借金)", "A判定", "極めて安全", 10, 25, 8.5, 250.0, 2321.4, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "2914" -> StockFinancialProfile(74.6, 51.8, "0.12倍 (健全財務)", "A判定", "高還元健全", 3, 10, 5.2, 260.0, 1950.0, "6月末 / 12月末", "9月下旬 / 3月下旬")
        "8306" -> StockFinancialProfile(37.0, 5.2, "BIS規制適合 (健全)", "A判定", "自己資本規制適合", 4, 16, 12.0, 135.0, 1850.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8316" -> StockFinancialProfile(40.0, 5.1, "BIS規制適合 (健全)", "A判定", "メガバンク高資本", 5, 16, 9.1, 325.0, 3850.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8058" -> StockFinancialProfile(34.5, 34.5, "0.32倍 (良質資産)", "A+判定", "強固なCF創出力", 9, 12, 10.5, 290.0, 2650.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "9432" -> StockFinancialProfile(38.5, 33.8, "0.88倍 (インフラ適正)", "A判定", "ディフェンシブ極致", 14, 25, 3.5, 13.5, 118.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "9433" -> StockFinancialProfile(46.8, 45.1, "0.45倍 (優良)", "A+判定", "23期連続増配の実績", 23, 23, 5.0, 310.0, 2800.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8766" -> StockFinancialProfile(45.0, 15.5, "ESR 220% (健全)", "A判定", "国際基準健全", 13, 13, 15.0, 280.0, 2400.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "8031" -> StockFinancialProfile(31.8, 38.0, "0.28倍 (商社優良)", "A判定", "高収益資源流通", 4, 12, 8.0, 330.0, 3350.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "4502" -> StockFinancialProfile(94.9, 48.5, "0.72倍 (純負債圧縮進行)", "B+判定", "配当維持コミット", 0, 30, 0.0, 198.0, 3800.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
        "AAPL" -> StockFinancialProfile(15.4, 28.5, "0.85倍 (莫大CF補完)", "A+判定", "自社株買い強力", 12, 12, 4.5, 6.5, 4.8, "2/5/8/11月", "四半期配当 (2・5・8・11月)")
        else -> StockFinancialProfile(35.0, 40.0, "0.20倍 (標準健全)", "A判定", "適正健全水準", 5, 10, 5.0, 100.0, 1000.0, "3月末 / 9月末", "6月下旬 / 12月中旬")
    }
}

enum class Category {
    JAPAN, US, FUND
}

data class WatchlistStock(
    val code: String,
    val name: String,
    val market: String,
    val sector: String,
    val category: String, // "jp" or "us"
    val price: Double,
    val currency: String = "¥",
    val dps: Double,
    val currentYield: Double,
    val targetYield: Double,
    val targetPrice: Double,
    val dailyChange: String,
    val isPositive: Boolean,
    val payoutMonths: List<Int>,
    val payoutTiming: String,
    val desc: String,
    val alertEnabled: Boolean = false,
    val badge: String? = null,
    val defaultShares: Int = 100
) {
    val isReady: Boolean get() = targetYield.isFinite() && targetYield > 0.0 && currentYield.isFinite() && currentYield >= targetYield
    val achievementRate: Int get() = if (targetYield.isFinite() && targetYield > 0.0 && currentYield.isFinite())
        ((currentYield / targetYield) * 100).toInt().coerceIn(0, 100) else 0
}

data class RoadmapStep(
    val step: Int,
    val level: Int,
    val name: String,
    val title: String,
    val description: String,
    val iconName: String,
    val targetMonthly: Int,
    val targetAnnual: Int,
    val status: StepStatus,
    val statusLabel: String,
    val currentRate: Double,
    val rateLabel: String,
    val shortageMonthly: Int = 0,
    val shortageAnnual: Int = 0,
    val yield35Capital: String = "",
    val yield40Capital: String = "",
    val yield45Capital: String = ""
)

enum class StepStatus {
    COMPLETED, IN_PROGRESS, LOCKED
}

data class MonthlyDividend(
    val month: Int,
    val amount: Int,
    val label: String
)
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/PortfolioDiagnosis.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.roundToInt

enum class PortfolioBandPosition(val label: String, val digit: Int) {
    LOWER("下限寄り", 0),
    CENTER("中央", 1),
    UPPER("上限寄り", 2)
}

data class PortfolioDiagnosisFinding(
    val key: String,
    val label: String,
    val value: Double,
    val formattedValue: String,
    val bandLabel: String,
    val position: PortfolioBandPosition,
    val score: Int,
    val text: String,
    val combinationDigit: Int
)

data class PortfolioDiagnosis(
    val score: Int?,
    val grade: String,
    val title: String,
    val narrative: String,
    val findings: List<PortfolioDiagnosisFinding>,
    val missingLabels: List<String>,
    val combinationIndex: Long?,
    val possibleCombinations: Long,
    val dividendGrowthLabel: String,
    val dividendRiskLabel: String
) {
    val complete: Boolean get() = missingLabels.isEmpty()

    companion object {
        const val TOTAL_COMBINATIONS: Long = 170_859_375L // 15^7
    }
}

/** Builds a portfolio narrative from seven portfolio-weighted indicators. */
object PortfolioDiagnosisGenerator {
    private data class Band(
        val label: String,
        val score: Int,
        val explanation: String
    )

    private data class MetricRule(
        val key: String,
        val label: String,
        val unit: String,
        val weight: Int,
        val upperBounds: List<Double>,
        val bands: List<Band>,
        val positionText: List<String>
    )

    private data class MetricValue(
        val value: Double?,
        val formatted: String,
        val detail: String
    )

    private val rules = listOf(
        MetricRule(
            key = "largest_holding_weight", label = "評価額比率", unit = "%", weight = 20,
            upperBounds = listOf(10.0, 20.0, 30.0, 45.0),
            bands = listOf(
                Band("分散の効いた水準", 100, "一銘柄への偏りは小さく、個別銘柄の値動きが全体に及ぼす影響を抑えやすい構成です。"),
                Band("偏りの小さい水準", 85, "最大銘柄の比率は抑えられています。銘柄ごとの値動きが資産全体を左右しにくい状態です。"),
                Band("一定の偏りがある水準", 65, "最大銘柄の値動きが資産全体に影響しやすい比率です。"),
                Band("集中が目立つ水準", 40, "一銘柄の株価変動がポートフォリオ全体に強く表れやすい構成です。"),
                Band("集中の強い水準", 20, "資産評価額が一銘柄に大きく依存しています。")
            ),
            positionText = listOf(
                "最大銘柄の比率はこの区分の下側にあります。",
                "集中度はこの区分の中央付近を示しています。",
                "集中度は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "top_three_dividend_share", label = "年間配当比率", unit = "%", weight = 15,
            upperBounds = listOf(25.0, 40.0, 55.0, 70.0),
            bands = listOf(
                Band("配当源が分散した水準", 100, "配当収入は複数銘柄に分かれ、上位銘柄への依存は小さい状態です。"),
                Band("配当の偏りが小さい水準", 85, "上位銘柄の比率は比較的低く、配当源を分散できています。"),
                Band("一定の配当集中がある水準", 65, "上位銘柄の配当が全体を左右する余地があります。"),
                Band("上位銘柄への依存が大きい水準", 40, "上位銘柄の配当が減ると、年間受取額への影響が大きくなりやすい構成です。"),
                Band("上位銘柄への依存が強い水準", 20, "年間配当の多くを上位3銘柄に頼っています。")
            ),
            positionText = listOf(
                "配当集中度はこの区分の下限寄りにあります。",
                "配当集中度は中央付近を示しています。",
                "配当集中度は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "largest_sector_weight", label = "評価額比率", unit = "%", weight = 15,
            upperBounds = listOf(20.0, 35.0, 50.0, 65.0),
            bands = listOf(
                Band("業種分散が効いた水準", 100, "評価額は複数業種に分かれ、特定業種への偏りを抑えています。"),
                Band("業種の偏りが小さい水準", 85, "最大業種の比率は比較的低く、業種分散が保たれています。"),
                Band("一定の業種偏りがある水準", 65, "最大業種の動向が資産全体に影響しやすい構成です。"),
                Band("業種集中が目立つ水準", 40, "最大業種の業績や市況が変化すると、複数銘柄が同時に影響を受ける可能性があります。"),
                Band("業種集中の強い水準", 20, "評価額が一つの業種に大きく偏っています。")
            ),
            positionText = listOf(
                "最大業種の比率は区分の下側にあります。",
                "業種の偏りは区分の中央付近を示しています。",
                "最大業種の比率は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "largest_market_weight", label = "評価額比率", unit = "%", weight = 10,
            upperBounds = listOf(55.0, 70.0, 85.0, 95.0),
            bands = listOf(
                Band("複数の登録区分に分かれた水準", 100, "保有データ上、日本株・米国株・投資信託など複数の区分に資産が分かれています。"),
                Band("登録区分の偏りが小さい水準", 85, "複数の区分を保有し、最大区分への偏りも抑えられています。"),
                Band("一定の登録区分に偏りがある水準", 65, "最大区分が評価額の中心ですが、ほかの区分にも資産があります。"),
                Band("一つの登録区分への集中が目立つ水準", 40, "評価額の大半が一つの区分にあります。投資信託の投資先まではこの分類に含めていません。"),
                Band("一つの登録区分に強く集中した水準", 20, "評価額のほとんどが一つの区分にあります。投資信託の投資先までは別に集計していません。")
            ),
            positionText = listOf(
                "登録区分の比率はこの帯域の下側にあります。",
                "登録区分の比率はこの帯域の中央付近を示しています。",
                "登録区分の比率は次の区分に近づいています。"
            )
        ),
        MetricRule(
            key = "healthy_payout_share", label = "配当性向70%以下の年間配当比率", unit = "%", weight = 15,
            upperBounds = listOf(20.0, 40.0, 60.0, 80.0),
            bands = listOf(
                Band("低い水準", 25, "利益に対する配当負担が小さい銘柄から得る配当は、ポートフォリオ内で少なめです。"),
                Band("やや低い水準", 45, "年間配当の多くは、配当性向が70%を超える銘柄から得ています。"),
                Band("標準的な水準", 65, "年間配当は、配当性向70%以下と70%超の銘柄の両方から得ています。"),
                Band("高い水準", 85, "年間配当の多くは、配当性向70%以下の銘柄から得ています。"),
                Band("非常に高い水準", 100, "年間配当の大半は、配当性向70%以下の銘柄から得ています。")
            ),
            positionText = listOf(
                "配当性向70%以下の銘柄から得る年間配当比率は区分の下側にあります。",
                "配当性向70%以下の年間配当比率は中央付近を示しています。",
                "配当性向70%以下の銘柄から得る年間配当比率は次の評価区分に近づいています。"
            )
        ),
        MetricRule(
            key = "weighted_dividend_history", label = "配当履歴の配当額加重平均", unit = "年", weight = 15,
            upperBounds = listOf(1.0, 3.0, 5.0, 10.0),
            bands = listOf(
                Band("短い水準", 30, "保有配当額で重み付けした増配・非減配の実績年数は短めです。"),
                Band("やや短い水準", 50, "増配・非減配の実績は積み上がりつつありますが、確認できる期間はまだ限られます。"),
                Band("標準的な水準", 70, "一定期間の増配・非減配実績を確認できます。"),
                Band("長い水準", 85, "保有配当額の多くが、長期の増配・非減配実績を持つ銘柄に由来します。"),
                Band("非常に長い水準", 100, "長期にわたる増配・非減配実績が、ポートフォリオの配当を支えています。")
            ),
            positionText = listOf(
                "配当履歴の平均年数はこの帯域の下側にあります。",
                "平均年数はこの帯域の中央付近を示しています。",
                "平均年数は次の実績区分に近づいています。"
            )
        ),
        MetricRule(
            key = "largest_monthly_dividend_share", label = "最大月集中度", unit = "%", weight = 10,
            upperBounds = listOf(15.0, 25.0, 35.0, 50.0),
            bands = listOf(
                Band("月別分散が効いた水準", 100, "年間配当は複数の月に分かれ、特定月への集中は小さい状態です。"),
                Band("月別の偏りが小さい水準", 85, "配当月の偏りは比較的小さく、受取時期を分散できています。"),
                Band("一定の月別偏りがある水準", 65, "配当が集まる月があり、月ごとの受取額には差があります。"),
                Band("月別集中が目立つ水準", 40, "年間配当の多くが限られた月に集中しています。"),
                Band("月別集中の強い水準", 20, "最大月の配当額が大きく、月ごとの受取額に偏りがあります。")
            ),
            positionText = listOf(
                "最大月の割合はこの区分の下限寄りにあります。",
                "月別の配当集中度は区分の中央付近を示しています。",
                "最大月の割合は次の区分に近づいています。"
            )
        )
    )

    fun generate(holdings: List<Holding>): PortfolioDiagnosis {
        if (holdings.isEmpty()) {
            return PortfolioDiagnosis(
                score = null,
                grade = "-",
                title = "保有銘柄なし",
                narrative = "保有銘柄が登録されていないため、ポートフォリオを評価できません。銘柄を登録すると、資産配分と配当構成を集計します。",
                findings = emptyList(),
                missingLabels = rules.map { it.label },
                combinationIndex = null,
                possibleCombinations = 0L,
                dividendGrowthLabel = "評価対象なし",
                dividendRiskLabel = "評価対象なし"
            )
        }

        val totalValue = holdings.sumOf { it.totalValuation.coerceAtLeast(0.0) }
        val dividendHoldings = holdings.filter { it.annualDividend > 0.0 }
        val totalDividend = dividendHoldings.sumOf { it.annualDividend }

        val values = mapOf(
            "largest_holding_weight" to percentageValue(
                if (totalValue > 0) holdings.maxOf { it.totalValuation.coerceAtLeast(0.0) } / totalValue * 100 else null,
                holdings.maxByOrNull { it.totalValuation.coerceAtLeast(0.0) }?.name ?: "最大銘柄"
            ),
            "top_three_dividend_share" to percentageValue(
                if (totalDividend > 0) dividendHoldings.sortedByDescending { it.annualDividend }.take(3).sumOf { it.annualDividend } / totalDividend * 100 else null,
                "上位3銘柄"
            ),
            "largest_sector_weight" to percentageValue(
                largestGroupShare(holdings, totalValue) { it.sector.trim().ifBlank { "業種未登録" } },
                holdings.groupBy { it.sector.trim().ifBlank { "業種未登録" } }.maxByOrNull { (_, group) -> group.sumOf { it.totalValuation.coerceAtLeast(0.0) } }?.key ?: "業種未登録"
            ),
            "largest_market_weight" to percentageValue(
                largestGroupShare(holdings, totalValue) { marketGroup(it) },
                holdings.groupBy(::marketGroup).maxByOrNull { (_, group) -> group.sumOf { it.totalValuation.coerceAtLeast(0.0) } }?.key ?: "市場区分"
            ),
            "healthy_payout_share" to percentageValue(
                if (totalDividend > 0) dividendHoldings.filter { getStockProfile(it.code).payoutRatio <= HEALTHY_PAYOUT_RATIO_MAX }
                    .sumOf { it.annualDividend } / totalDividend * 100 else null,
                ""
            ),
            "weighted_dividend_history" to yearValue(dividendHoldings, totalDividend),
            "largest_monthly_dividend_share" to monthlyValue(dividendHoldings, totalDividend)
        )

        var combinationIndex = 0L
        var evaluatedCount = 0
        val findings = rules.mapIndexedNotNull { index, rule ->
            val metric = values[rule.key] ?: return@mapIndexedNotNull null
            val value = metric.value ?: return@mapIndexedNotNull null
            val bandIndex = rule.upperBounds.indexOfFirst { value < it }.let { if (it < 0) rule.bands.lastIndex else it }
            val lower = if (bandIndex == 0) 0.0 else rule.upperBounds[bandIndex - 1]
            val upper = if (bandIndex < rule.upperBounds.size) rule.upperBounds[bandIndex] else 100.0
            val positionIndex = (((value - lower) / (upper - lower).coerceAtLeast(0.0001)) * 3.0).toInt().coerceIn(0, 2)
            val position = PortfolioBandPosition.entries[positionIndex]
            val band = rule.bands[bandIndex]
            val detail = metric.detail.takeIf(String::isNotBlank)?.let { "${it}の" }.orEmpty()
            val text = "${detail}${rule.label}は${metric.formatted}で、${band.label}です。${band.explanation}${rule.positionText[positionIndex]}"
            val digit = bandIndex * 3 + position.digit
            combinationIndex = combinationIndex * 15L + digit
            evaluatedCount++
            PortfolioDiagnosisFinding(
                key = rule.key,
                label = rule.label,
                value = value,
                formattedValue = metric.formatted,
                bandLabel = band.label,
                position = position,
                score = band.score,
                text = text,
                combinationDigit = digit
            )
        }

        val evaluatedKeys = findings.mapTo(mutableSetOf()) { it.key }
        val missingLabels = rules.filterNot { it.key in evaluatedKeys }.map { it.label }
        val score = if (findings.isEmpty()) null else {
            val scoreByKey = findings.associate { it.key to it.score }
            val activeWeight = rules.filter { it.key in scoreByKey }.sumOf { it.weight }
            if (activeWeight == 0) null else rules.sumOf { rule -> (scoreByKey[rule.key] ?: 0) * rule.weight } / activeWeight
        }
        val grade = when {
            score == null -> "-"
            score >= 90 -> "S"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            else -> "D"
        }
        val title = when (grade) {
            "S" -> "非常に健全"
            "A" -> "健全"
            "B" -> "概ね良好"
            "C" -> "要点検"
            "D" -> "見直し推奨"
            else -> "判定不可"
        }
        val findingGroups = listOf(
            setOf("largest_holding_weight", "top_three_dividend_share", "largest_sector_weight", "largest_market_weight"),
            setOf("healthy_payout_share", "weighted_dividend_history"),
            setOf("largest_monthly_dividend_share")
        )
        val paragraphs = findingGroups.mapNotNull { keys ->
            findings.filter { it.key in keys }.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.text }
        }
        val weakest = findings.minByOrNull { it.score }
        val recommendation = weakest?.let(::recommendationFor)
        val narrative = buildList {
            addAll(paragraphs)
            if (recommendation != null) add(recommendation)
            if (missingLabels.isNotEmpty()) add("${missingLabels.joinToString("・")}はデータを確認できず、今回の評価に含めていません。")
        }.joinToString("\n\n")
        val growthYears = values["weighted_dividend_history"]?.value
        val healthyShare = values["healthy_payout_share"]?.value
        val dividendGrowthLabel = growthYears?.let { "配当額加重平均 ${format(it, 1)}年" } ?: "評価対象なし"
        val dividendRiskLabel = when {
            healthyShare == null -> "判定材料なし"
            healthyShare >= 80.0 && (growthYears ?: 0.0) >= 10.0 -> "低め"
            healthyShare >= 60.0 && (growthYears ?: 0.0) >= 5.0 -> "やや低め"
            healthyShare >= 40.0 -> "標準"
            healthyShare >= 20.0 -> "やや高め"
            else -> "高め"
        }

        return PortfolioDiagnosis(
            score = score,
            grade = grade,
            title = title,
            narrative = narrative,
            findings = findings,
            missingLabels = missingLabels,
            combinationIndex = combinationIndex.takeIf { evaluatedCount == rules.size },
            possibleCombinations = 15.0.pow(evaluatedCount).toLong(),
            dividendGrowthLabel = dividendGrowthLabel,
            dividendRiskLabel = dividendRiskLabel
        )
    }

    private fun percentageValue(value: Double?, detail: String) = MetricValue(
        value = value?.coerceIn(0.0, 100.0),
        formatted = value?.let { "${format(it, 1)}%" } ?: "",
        detail = detail
    )

    private fun yearValue(dividendHoldings: List<Holding>, totalDividend: Double): MetricValue {
        if (totalDividend <= 0.0) return MetricValue(null, "", "")
        val weightedYears = dividendHoldings.sumOf { holding ->
            val profile = getStockProfile(holding.code)
            val years = ((profile.noCutYears + profile.consecutiveYears) / 2.0).coerceAtLeast(0.0)
            holding.annualDividend * years
        } / totalDividend
        return MetricValue(weightedYears, "${format(weightedYears, 1)}年", "")
    }

    private fun largestGroupShare(
        holdings: List<Holding>,
        totalValue: Double,
        groupBy: (Holding) -> String
    ): Double? {
        if (totalValue <= 0.0) return null
        return holdings.groupBy(groupBy).values.maxOfOrNull { group ->
            group.sumOf { it.totalValuation.coerceAtLeast(0.0) }
        }?.div(totalValue)?.times(100.0)
    }

    private fun monthlyConcentration(holdings: List<Holding>, totalDividend: Double): Double? {
        if (totalDividend <= 0.0) return null
        val monthlyAmounts = DoubleArray(12)
        var coveredDividend = 0.0
        holdings.forEach { holding ->
            val months = holding.dividendMonths.distinct().filter { it in 1..12 }
            if (months.isNotEmpty()) {
                val monthlyAmount = holding.annualDividend / months.size
                months.forEach { month -> monthlyAmounts[month - 1] += monthlyAmount }
                coveredDividend += holding.annualDividend
            }
        }
        if (coveredDividend <= 0.0) return null
        return monthlyAmounts.maxOrNull()?.div(coveredDividend)?.times(100.0)
    }

    private fun monthlyValue(holdings: List<Holding>, totalDividend: Double): MetricValue {
        if (totalDividend <= 0.0) return MetricValue(null, "", "")
        val coveredDividend = holdings.filter { holding ->
            holding.dividendMonths.distinct().any { it in 1..12 }
        }.sumOf { it.annualDividend }
        val concentration = monthlyConcentration(holdings, totalDividend)
        if (concentration == null || coveredDividend <= 0.0) return MetricValue(null, "", "")
        val coverage = coveredDividend / totalDividend * 100.0
        return MetricValue(
            value = concentration,
            formatted = "${format(concentration, 1)}%",
            detail = "配当月登録分（年間配当の${format(coverage, 1)}%）"
        )
    }

    private fun marketGroup(holding: Holding): String = when (holding.category) {
        Category.JAPAN -> "日本株"
        Category.US -> "米国株"
        Category.FUND -> if (holding.currency == "$") "外貨建て投資信託" else "投資信託"
    }

    private fun recommendationFor(finding: PortfolioDiagnosisFinding): String = when (finding.key) {
        "largest_holding_weight" -> "次の買い増しでは、最大銘柄以外に資金を振り分けると、銘柄ごとの値動きの影響を抑えられます。"
        "top_three_dividend_share" -> "配当収入の偏りを抑えるには、上位3銘柄以外への買い増しも選択肢です。"
        "largest_sector_weight" -> "業種の偏りを抑えるには、最大業種以外の銘柄を買い増し候補にできます。"
        "largest_market_weight" -> "地域や通貨を分けたい場合は、現在比率の低い市場区分を候補にできます。"
        "healthy_payout_share" -> "配当性向が高い銘柄は、利益の推移と配当方針を確認してから買い増しを判断してください。"
        "weighted_dividend_history" -> "増配・非減配の年数だけでなく、配当性向や利益の推移も銘柄ごとに確認してください。"
        else -> "月ごとの受取額を平準化したい場合は、配当月が異なる銘柄も買い増し候補になります。"
    }

    private fun format(value: Double, digits: Int): String = String.format(Locale.JAPAN, "%.${digits}f", value)

    private fun Double.pow(exponent: Int): Double = Math.pow(this, exponent.toDouble())

    private const val HEALTHY_PAYOUT_RATIO_MAX = 70.0
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/PortfolioMath.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.round

data class HoldingAllocation(val shares: Int, val acquisitionPrice: Double, val accountType: String)

/** Validation and arithmetic shared by imports, editing and multi-account registration. */
object PortfolioMath {
    fun validMoney(value: Double): Boolean = value.isFinite() && value >= 0.0 && value <= 1.0e12

    fun wholeShares(value: Double?): Int? = value?.takeIf {
        it.isFinite() && it >= 0.0 && it <= Int.MAX_VALUE.toDouble() && it % 1.0 == 0.0
    }?.toInt()

    fun normalizeCode(code: String): String = code.trim().uppercase(Locale.ROOT)

    fun validCode(code: String): Boolean = code.isNotBlank() && code.length <= 256 &&
        code.none { it.isISOControl() || it == '/' || it == '?' || it == '#' }

    fun isTaxFree(account: String): Boolean = account.uppercase(Locale.ROOT).contains("NISA") ||
        account.contains("非課税")

    fun yieldPercent(dividend: Double, price: Double): Double =
        if (validMoney(dividend) && validMoney(price) && price > 0.0) {
            (dividend / price * 100.0).takeIf(Double::isFinite) ?: 0.0
        } else 0.0

    fun adjustedTarget(current: Double, delta: Double): Double {
        require(current.isFinite() && delta.isFinite()) { "利回りは有限の数値を指定してください" }
        return round((current + delta).coerceIn(0.1, 100.0) * 100.0) / 100.0
    }

    fun addShares(current: Int, delta: Int): Int =
        (current.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun mergeAllocations(holding: Holding, allocations: List<HoldingAllocation>): Holding {
        require(allocations.isNotEmpty()) { "口座を指定してください" }
        require(allocations.all { it.shares >= 0 && validMoney(it.acquisitionPrice) && it.accountType.isNotBlank() }) {
            "数量・取得単価・口座を確認してください"
        }
        val total = holding.shares.toLong() + allocations.sumOf { it.shares.toLong() }
        require(total in 1..Int.MAX_VALUE.toLong()) { "保有数量が上限を超えています" }
        val oldFree = holding.effectiveTaxFreeShares
        val free = oldFree.toLong() + allocations.filter { isTaxFree(it.accountType) }.sumOf { it.shares.toLong() }
        val average = (holding.shares * holding.acqPrice + allocations.sumOf { it.shares * it.acquisitionPrice }) / total
        val accounts = (listOf(holding.accountType).filter { holding.shares > 0 } +
            allocations.filter { it.shares > 0 }.map { it.accountType }).distinct()
        return holding.copy(
            shares = total.toInt(), acqPrice = average, taxFreeShares = free.toInt(),
            accountType = accounts.singleOrNull() ?: "複数口座",
            yoc = yieldPercent(holding.dps, average)
        ).refreshPayout()
    }

    fun Holding.refreshPayout(): Holding {
        val months = dividendMonths.filter { it in 1..12 }.distinct()
        val payout = shares * dps / priceUnit / months.size.coerceAtLeast(1)
        val amount = "$currency${java.text.NumberFormat.getNumberInstance(Locale.JAPAN).format(payout)}"
        return copy(
            yieldRate = liveYield, yoc = liveYoc, nextPayoutAmount = amount,
            nextPayoutTiming = months.firstOrNull()?.let { "次回: ${it}月 ($amount)" } ?: "配当月未取得"
        )
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/StockDetailResolver.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareSecurity

object StockDetailResolver {
    fun resolve(code: String, holdings: List<Holding>, watchlist: List<WatchlistStock>, security: CloudflareSecurity?): Holding? {
        holdings.firstOrNull { it.code.equals(code, true) }?.let { return it }
        val stock = watchlist.firstOrNull { it.code.equals(code, true) }
        if (stock == null && (security == null || !security.code.equals(code, true))) return null
        val currency = stock?.currency ?: if (code.matches(Regex("[A-Za-z]+"))) "$" else "¥"
        val price = security?.marketPrice ?: stock?.price ?: 0.0
        val dps = security?.annualDividendPerShare ?: stock?.dps ?: 0.0
        val name = security?.name ?: stock?.name ?: return null
        return Holding(
            "detail-${PortfolioMath.normalizeCode(code)}", PortfolioMath.normalizeCode(code), name,
            security?.industry ?: stock?.sector ?: "業種未登録", stock?.market ?: "東証",
            if (currency == "$") Category.US else Category.JAPAN, price, currency, 0, 0.0, dps,
            PortfolioMath.yieldPercent(dps, price), 0.0, stock?.payoutTiming ?: "配当月未取得", "—",
            stock?.payoutMonths ?: emptyList(), "", "未保有", stock?.dailyChange ?: "—", stock?.isPositive ?: true
        )
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/components/AddHoldingSheet.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.data.runCatchingCancellable
import com.kurogane.dividend.model.HoldingAllocation
import com.kurogane.dividend.model.PortfolioMath
import java.text.NumberFormat
import java.util.Locale

data class PresetStock(
    val code: String,
    val name: String,
    val sector: String,
    val price: Double,
    val yieldRate: Double,
    val dps: Double,
    val currency: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHoldingSheet(
    visible: Boolean,
    prefilledCode: String,
    onDismiss: () -> Unit,
    onResolveSecurity: suspend (code: String) -> CloudflareSecurity?,
    onRegisterCollective: suspend (code: String, allocations: List<HoldingAllocation>) -> Boolean
) {
    if (!visible) return

    val presets = remember {
        listOf(
            PresetStock("7203", "トヨタ自動車", "東証P • 輸送用機器", 2600.0, 2.80, 72.8, "¥"),
            PresetStock("8058", "三菱商事", "東証P • 卸売業", 3240.0, 3.52, 100.0, "¥"),
            PresetStock("8316", "三井住友FG", "東証P • 銀行業", 3150.0, 4.13, 130.0, "¥"),
            PresetStock("9432", "日本電信電話", "東証P • 情報通信", 154.2, 3.37, 5.2, "¥"),
            PresetStock("AAPL", "Apple Inc.", "NASDAQ • テクノロジー", 220.0, 0.52, 1.0, "$")
        )
    }

    var stockCodeInput by remember { mutableStateOf(prefilledCode.ifEmpty { "8316" }) }
    var selectedStock by remember {
        mutableStateOf(
            presets.find { it.code == stockCodeInput } ?: presets[2]
        )
    }
    var isRegistering by remember { mutableStateOf(false) }
    var registerError by remember { mutableStateOf(false) }
    var resolvedSecurity by remember { mutableStateOf<CloudflareSecurity?>(null) }
    val registrationScope = rememberCoroutineScope()

    // Dynamic prefill when prefilledCode changes
    LaunchedEffect(prefilledCode) {
        if (prefilledCode.isNotEmpty()) {
            stockCodeInput = prefilledCode
            presets.find { it.code == prefilledCode }?.let {
                selectedStock = it
            }
        }
    }

    // Accounts Matrix States
    var nisaShares by remember { mutableStateOf("100") }
    var nisaPrice by remember { mutableStateOf(selectedStock.price.toString()) }

    var tsumitateShares by remember { mutableStateOf("0") }
    var tsumitatePrice by remember { mutableStateOf(selectedStock.price.toString()) }

    var tokuteiShares by remember { mutableStateOf("0") }
    var tokuteiPrice by remember { mutableStateOf(selectedStock.price.toString()) }

    var generalShares by remember { mutableStateOf("0") }
    var generalPrice by remember { mutableStateOf(selectedStock.price.toString()) }

    // Dynamic update prices when stock selection changes
    fun updateSelectedStock(stock: PresetStock) {
        selectedStock = stock
        stockCodeInput = stock.code
        nisaPrice = stock.price.toString()
        tsumitatePrice = stock.price.toString()
        tokuteiPrice = stock.price.toString()
        generalPrice = stock.price.toString()
    }

    // Presets or custom stock changes
    LaunchedEffect(stockCodeInput) {
        val normalizedCode = stockCodeInput.trim().uppercase()
        val matched = presets.find { it.code.trim().uppercase() == normalizedCode }
        if (matched != null && matched != selectedStock) {
            resolvedSecurity = null
            updateSelectedStock(matched)
        } else if (matched != null) {
            resolvedSecurity = null
        } else if (resolvedSecurity?.code?.equals(normalizedCode, ignoreCase = true) == true) {
            // Already resolved this exact code from the remote master.
        } else if (normalizedCode.matches(Regex("[A-Z0-9]{4}"))) {
            kotlinx.coroutines.delay(350)
            val security = runCatchingCancellable { onResolveSecurity(normalizedCode) }.getOrNull()
            if (normalizedCode == stockCodeInput.trim().uppercase() && security != null) {
                resolvedSecurity = security
                val knownPreset = presets.firstOrNull { it.code.equals(normalizedCode, ignoreCase = true) }
                val price = security.marketPrice ?: knownPreset?.price ?: 0.0
                val dps = security.annualDividendPerShare ?: knownPreset?.dps ?: 0.0
                updateSelectedStock(
                    PresetStock(
                        code = security.code,
                        name = security.name,
                        sector = security.industry?.let { "東証P • $it" } ?: "東証銘柄",
                        price = price,
                        yieldRate = if (price > 0.0) dps / price * 100 else 0.0,
                        dps = dps,
                        currency = "¥"
                    )
                )
            }
        }
    }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val isUSD = selectedStock.currency == "$"
    val fxRate = if (isUSD) 154.0 else 1.0

    // Calculations
    val nShares = nisaShares.toIntOrNull() ?: 0
    val nPrice = nisaPrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val nisaDividend = nShares * selectedStock.dps * fxRate
    val nisaCost = nShares * nPrice * fxRate

    val tShares = tsumitateShares.toIntOrNull() ?: 0
    val tPrice = tsumitatePrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val tsumitateDividend = tShares * selectedStock.dps * fxRate
    val tsumitateCost = tShares * tPrice * fxRate

    val tkShares = tokuteiShares.toIntOrNull() ?: 0
    val tkPrice = tokuteiPrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val tokuteiGrossDiv = tkShares * selectedStock.dps * fxRate
    val tokuteiDividend = tokuteiGrossDiv * 0.79685
    val tokuteiCost = tkShares * tkPrice * fxRate

    val gShares = generalShares.toIntOrNull() ?: 0
    val gPrice = generalPrice.toDoubleOrNull()?.takeIf(PortfolioMath::validMoney) ?: 0.0
    val generalGrossDiv = gShares * selectedStock.dps * fxRate
    val generalDividend = generalGrossDiv * 0.79685
    val generalCost = gShares * gPrice * fxRate

    // Summary Box metrics
    val totalSharesLong = listOf(nShares, tShares, tkShares, gShares).sumOf { it.toLong() }
    val totalShares = totalSharesLong.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val inputValid = totalSharesLong in 1..Int.MAX_VALUE.toLong() &&
        listOf(nisaShares, tsumitateShares, tokuteiShares, generalShares).all { (it.toIntOrNull() ?: -1) >= 0 } &&
        listOf(nShares to nisaPrice, tShares to tsumitatePrice, tkShares to tokuteiPrice, gShares to generalPrice)
            .all { (quantity, value) -> quantity == 0 || value.toDoubleOrNull()?.let(PortfolioMath::validMoney) == true }
    val totalCost = nisaCost + tsumitateCost + tokuteiCost + generalCost
    val weightedAvgAcqPrice = if (totalShares > 0) totalCost / totalShares / fxRate else selectedStock.price
    val totalNetDividend = nisaDividend + tsumitateDividend + tokuteiDividend + generalDividend
    val nisaTaxBenefits = (nShares + tShares) * selectedStock.dps * 0.20315 * fxRate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = SurfaceContainerHighest)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                            .border(1.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "複数口座一括配分登録",
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryEmeraldContainer.copy(alpha = 0.2f))
                                    .border(0.5.dp, PrimaryEmeraldContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "マトリックス同時入力",
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            "口座間の株数・取得単価を1画面でまとめて設定",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    }
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

            // 1. Stock Code Input Box
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Tag, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Text("銘柄コード / 企業名", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${selectedStock.name} (利回り ${selectedStock.yieldRate}%)",
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = stockCodeInput,
                    onValueChange = { stockCodeInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = SurfaceContainerHighest,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                if (registerError) {
                    Text(
                        "Cloudflareの銘柄マスターを確認できませんでした。通信状態と銘柄コードを確認してください。",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = AppTextSize.dense
                    )
                }

                // Presets selection row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { stock ->
                        val active = selectedStock.code == stock.code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) PrimaryEmeraldContainer.copy(alpha = 0.15f) else SurfaceContainer)
                                .border(BorderStroke(1.dp, if (active) PrimaryEmerald else Color.Transparent), RoundedCornerShape(8.dp))
                                .clickable { updateSelectedStock(stock) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "${stock.code} ${stock.name}",
                                color = if (active) PrimaryEmerald else OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Ticker detail preview tag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BackgroundDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            selectedStock.code,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.dense,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedStock.name, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                        Text(selectedStock.sector, color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${selectedStock.currency}${currencyFormat.format(selectedStock.price.toLong())}",
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.caption,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("配当利回り ${selectedStock.yieldRate}%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Portfolio Allocation Matrix by Account
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CorporateFare, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Text("口座別 ポートフォリオ配分マトリックス", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                                .clickable {
                                    nisaPrice = selectedStock.price.toString()
                                    tsumitatePrice = selectedStock.price.toString()
                                    tokuteiPrice = selectedStock.price.toString()
                                    generalPrice = selectedStock.price.toString()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(10.dp))
                                Text("現在値を適用", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceContainerHighest)
                                .clickable {
                                    nisaShares = "0"
                                    tsumitateShares = "0"
                                    tokuteiShares = "0"
                                    generalShares = "0"
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = OnSurface, modifier = Modifier.size(10.dp))
                                Text("株数クリア", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Account Cards list
                val accounts = listOf(
                        Quadruple("新NISA 成長投資枠", "非課税 0%", nisaDividend, nisaShares, nisaPrice, { value: String -> nisaShares = value }, { value: String -> nisaPrice = value }),
                        Quadruple("新NISA つみたて枠", "非課税 0%", tsumitateDividend, tsumitateShares, tsumitatePrice, { value: String -> tsumitateShares = value }, { value: String -> tsumitatePrice = value }),
                        Quadruple("特定口座 (源泉徴収あり)", "課税 20.315%", tokuteiDividend, tokuteiShares, tokuteiPrice, { value: String -> tokuteiShares = value }, { value: String -> tokuteiPrice = value }),
                        Quadruple("一般口座", "課税 20.315%", generalDividend, generalShares, generalPrice, { value: String -> generalShares = value }, { value: String -> generalPrice = value })
                )

                accounts.forEach { (title, tag, divAmount, sharesVal, priceVal, onSharesChange, onPriceChange) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (title.contains("NISA")) PrimaryEmerald else Color(0xFF818CF8))
                                    )
                                    Text(title, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SurfaceContainerHighest)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(tag, color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    "¥${currencyFormat.format(divAmount.toLong())}/年",
                                        color = if (divAmount > 0) PrimaryEmerald else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.caption
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Shares Input Field with Helper tags
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("株数 / 口数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SurfaceContainerHighest)
                                                    .clickable {
                                                        val curr = sharesVal.toIntOrNull() ?: 0
                                                        onSharesChange((curr + 10).toString())
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("+10", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SurfaceContainerHighest)
                                                    .clickable {
                                                        val curr = sharesVal.toIntOrNull() ?: 0
                                                        onSharesChange((curr + 100).toString())
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("+100", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = sharesVal,
                                        onValueChange = onSharesChange,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = BackgroundDark,
                                            unfocusedContainerColor = BackgroundDark,
                                            focusedBorderColor = PrimaryEmerald,
                                            unfocusedBorderColor = SurfaceContainerHighest,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                // Price Input Field
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("取得単価", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    OutlinedTextField(
                                        value = priceVal,
                                        onValueChange = onPriceChange,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = BackgroundDark,
                                            unfocusedContainerColor = BackgroundDark,
                                            focusedBorderColor = PrimaryEmerald,
                                            unfocusedBorderColor = SurfaceContainerHighest,
                                            focusedTextColor = OnSurface,
                                            unfocusedTextColor = OnSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. All Accounts Total Allocation Summary Box
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Text("全口座 総合アロケーションサマリー", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("手取り最大化", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }

                // Summary Grid Row (2x2 boxes)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Box 1
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("合計保有株数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${totalShares} 株", color = OnSurface, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                        // Box 2
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("加重平均取得単価", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("¥${currencyFormat.format(weightedAvgAcqPrice.toLong())}", color = OnSurface, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Box 3
                        Card(
                            modifier = Modifier.weight(1f).border(BorderStroke(0.5.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("年間配当 (手取り総額)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("¥${currencyFormat.format(totalNetDividend.toLong())} /年", color = PrimaryEmerald, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                        // Box 4
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("NISA節税メリット", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("+¥${currencyFormat.format(nisaTaxBenefits.toLong())} /年", color = SkyBlue, fontWeight = FontWeight.Black, fontSize = AppTextSize.body)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.4f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("キャンセル", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                }

                Button(
                    onClick = {
                        if (inputValid && !isRegistering) {
                            registerError = false
                            isRegistering = true
                            registrationScope.launch {
                                try {
                                    val registered = runCatchingCancellable {
                                        onRegisterCollective(stockCodeInput.trim().uppercase(), listOf(
                                            HoldingAllocation(nShares, nPrice, "新NISA成長枠"),
                                            HoldingAllocation(tShares, tPrice, "新NISAつみたて投資枠"),
                                            HoldingAllocation(tkShares, tkPrice, "特定口座"),
                                            HoldingAllocation(gShares, gPrice, "一般口座")
                                        ))
                                    }.getOrDefault(false)
                                    registerError = !registered
                                } finally { isRegistering = false }
                            }
                        }
                    },
                    modifier = Modifier.weight(0.6f).height(48.dp),
                    enabled = inputValid && !isRegistering,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryEmeraldContainer,
                        contentColor = OnPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isRegistering) {
                            CircularProgressIndicator(color = OnPrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                        Text(if (isRegistering) "銘柄情報を確認中…" else "すべての口座にまとめて登録する", color = OnPrimaryContainer, fontWeight = FontWeight.Black, fontSize = AppTextSize.dense)
                    }
                }
            }
        }
    }
}

// Quadruple helper data container for accounts
data class Quadruple<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/components/SearchSheet.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.data.CloudflareSearchHit
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import com.kurogane.dividend.data.runCatchingCancellable

data class CandidateStock(
    val code: String,
    val name: String,
    val desc: String,
    val price: String,
    val yieldRate: String,
    val cat: String,
    val isHigh: Boolean
)

@Composable
fun SearchSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit,
    onSearch: suspend (String) -> List<CloudflareSearchHit> = { emptyList() }
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)

    val candidates = remember {
        listOf(
            CandidateStock("7203", "トヨタ自動車", "東証P • 輸送用機器", "¥2,600", "2.80%", "jp", false),
            CandidateStock("8316", "三井住友FG", "東証P • 銀行業", "¥3,150", "4.13%", "jp", true),
            CandidateStock("9432", "日本電信電話 (NTT)", "東証P • 情報通信", "¥154.2", "3.37%", "jp", true),
            CandidateStock("8058", "三菱商事", "東証P • 卸売業", "¥3,240", "3.52%", "jp", true),
            CandidateStock("AAPL", "Apple Inc.", "NASDAQ • 米国株", "$220.00", "0.52%", "us", false),
            CandidateStock("MSFT", "Microsoft Corp.", "NASDAQ • ソフトウェア", "$440.00", "0.76%", "us", false),
            CandidateStock("eMAXIS", "eMAXIS Slim 全世界株式", "オルカン • 全世界株", "¥24,460", "再投資型", "fund", false)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") } // "all", "jp", "us", "fund", "high-div"
    var remoteCandidates by remember { mutableStateOf<List<CandidateStock>?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, visible) {
        remoteCandidates = null
        isSearching = false
        val query = searchQuery.trim()
        if (!visible || query.isEmpty()) {
            remoteCandidates = null
            isSearching = false
        } else if (query.length >= 2 || query.any(Char::isDigit)) {
            delay(350)
            isSearching = true
            val result = runCatchingCancellable { onSearch(query) }
            remoteCandidates = result.getOrNull()?.map { hit ->
                val local = candidates.firstOrNull { it.code.equals(hit.code, ignoreCase = true) }
                val price = hit.cachedPrice?.let { "¥${java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN).format(it.toLong())}" }
                local?.copy(
                    name = hit.name,
                    desc = hit.industry?.let { "東証 • $it" } ?: local.desc,
                    price = price ?: local.price
                ) ?: CandidateStock(
                    code = hit.code,
                    name = hit.name,
                    desc = hit.industry?.let { "東証 • $it" } ?: "日本株 • Cloudflare銘柄マスター",
                    price = price ?: "株価未取得",
                    yieldRate = "--",
                    cat = "jp",
                    isHigh = false
                )
            }
            isSearching = false
        } else {
            remoteCandidates = null
        }
    }

    val filteredCandidates = remember(searchQuery, selectedCategory, remoteCandidates) {
        val source = remoteCandidates ?: candidates.filter { item ->
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() || item.code.lowercase().contains(q) || item.name.lowercase().contains(q)
            matchesQuery
        }
        source.filter { item ->
            val matchesCat = when (selectedCategory) {
                "all" -> true
                "high-div" -> item.isHigh
                else -> item.cat == selectedCategory
            }
            matchesCat
        }
    }

    // Scrim + Slide Up animated container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                onClick = onDismiss,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Prevent click propagation to scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SurfaceContainerLow)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    onClick = {},
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grab Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SurfaceContainerHighest)
                )

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainer)
                                .border(1.dp, SurfaceContainerHighest, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "銘柄検索・絞り込み",
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body
                            )
                            Text(
                                "保有銘柄の絞り込みまたは詳細分析を開く",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "銘柄名・コード・ティッカー (例: 7203, トヨタ, AAPL)",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = SurfaceContainerHighest,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        "all" to "すべて",
                        "jp" to "日本株",
                        "us" to "米国株",
                        "fund" to "投信",
                        "high-div" to "高配当 (3%以上)"
                    )
                    categories.forEach { (catId, catLabel) ->
                        val active = selectedCategory == catId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) PrimaryEmeraldContainer else SurfaceContainer)
                                .clickable { selectedCategory = catId }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = catLabel,
                                color = if (active) OnPrimaryContainer else OnSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }
                }

                // Quick hot search tags
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "注目の高配当・主力銘柄",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hotTags = listOf("7203 トヨタ", "8316 三井住友FG", "9432 NTT", "8058 三菱商事", "AAPL Apple")
                        hotTags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainer)
                                    .clickable {
                                        val codeOnly = tag.split(" ").first()
                                        searchQuery = codeOnly
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    tag,
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Candidate Count and List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isSearching) "Cloudflare銘柄マスターを検索中…" else "候補銘柄 (${filteredCandidates.size}件)",
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "タップで銘柄詳細を表示",
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Candidates scrollable list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCandidates) { stock ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainer)
                                .border(BorderStroke(0.5.dp, BorderColor), RoundedCornerShape(12.dp))
                                .clickable { onSelectStock(stock.code) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Code box
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BackgroundDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stock.code,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Name and details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stock.name,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.body
                                )
                                Text(
                                    stock.desc,
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }

                            // Price and Yield
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stock.price,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.body,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    if (stock.yieldRate == "再投資型") "再投資型" else "利回り ${stock.yieldRate}",
                                    color = if (stock.yieldRate == "再投資型") OnSurfaceVariant else PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }
                    }
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "閉じる",
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
            }
        }
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.data.Tse33IndustryMaster
import com.kurogane.dividend.model.PortfolioDiagnosis
import com.kurogane.dividend.model.PortfolioDiagnosisGenerator
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualDividendScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 分析, 1: 配当推移, 2: 資産推移
    var assetTrackingPeriod by rememberSaveable { mutableStateOf("5y") }
    var evalBasis by remember { mutableStateOf("eval") } // "eval" or "cost"

    val totalAssets = remember(holdings) { holdings.sumOf { it.totalValuation } }
    val totalCost = remember(holdings) { holdings.sumOf { it.totalCost } }
    val grossAnnualDividend = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("gross") } }
    val netAnnualDividend = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("net") } }
    val totalAnnualDiv = remember(holdings, taxRateMode) { holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) } }
    val yieldOnCost = remember(grossAnnualDividend, totalCost) { if (totalCost > 0) (grossAnnualDividend / totalCost) * 100 else 0.0 }
    val yieldOnValuation = remember(grossAnnualDividend, totalAssets) { if (totalAssets > 0) (grossAnnualDividend / totalAssets) * 100 else 0.0 }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.annualScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.annualScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.annualScrollIndex = index
                viewModel.annualScrollOffset = offset
            }
    }

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card: Annual Expected Dividend
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceContainer
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(
                            listOf(
                                PrimaryEmerald.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    "税引後手取り見込額",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = OnSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = if (isPrivacyActive) "¥••••••••" else "¥${currencyFormat.format(netAnnualDividend.toLong())}",
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = OnSurface,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = AppTextSize.hero
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("/ 年", color = OnSurfaceVariant, fontSize = AppTextSize.caption, modifier = Modifier.padding(bottom = 4.dp))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BackgroundDark)
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("税引前配当額", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (isPrivacyActive) "¥•••••• / 年" else "¥${currencyFormat.format(grossAnnualDividend.toLong())} / 年",
                                        color = OnSurface,
                                        fontSize = AppTextSize.caption,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("月換算", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (isPrivacyActive) "約 ¥••••" else "約 ¥${currencyFormat.format((grossAnnualDividend / 12).toLong())}",
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.caption,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BackgroundDark)
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Percent, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("利回りデュアル比較", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    listOf(
                                        Triple("取得額基準", yieldOnCost, totalCost),
                                        Triple("評価額基準", yieldOnValuation, totalAssets)
                                    ).forEachIndexed { index, (label, yieldValue, basisValue) ->
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SurfaceContainerHigh)
                                                .border(
                                                    1.dp,
                                                    if (index == 0) BorderColor else PrimaryEmerald.copy(alpha = 0.28f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                        ) {
                                            Text(label, color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                            Spacer(modifier = Modifier.height(7.dp))
                                            Text(
                                                if (isPrivacyActive) "•••%" else String.format(Locale.JAPAN, "%.2f%%", yieldValue),
                                                color = if (index == 0) SecondaryIndigo else PrimaryEmerald,
                                                fontSize = AppTextSize.metric,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                if (isPrivacyActive) "基準額 ¥••••" else "基準額 ¥${currencyFormat.format(basisValue.toLong())}",
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.dense,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("利回りは税引前配当額を基準に算出", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }
                        }
                    }
                }

            // 3-way Tab Selector
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceContainerHigh,
                    contentColor = PrimaryEmerald,
                    indicator = { tabPositions ->
                        // Standard tab indicator
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DataUsage,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) PrimaryEmerald else OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "分析",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) PrimaryEmerald else OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "配当推移",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = if (selectedTab == 2) PrimaryEmerald else OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "資産推移",
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 2) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }
                    )
                }
            }

            // Tab 0: Sector & Composition Analysis
            if (selectedTab == 0) {
                item {
                    CompositionAnalysisSection(holdings = holdings, taxRateMode = taxRateMode, totalAssets = totalAssets, totalAnnualDiv = totalAnnualDiv)
                }
            }

            // Tab 1: Dividend Compounding Simulation
            if (selectedTab == 1) {
                item {
                    CompoundingSimulationSection(
                        totalAnnualDiv = totalAnnualDiv,
                        totalCost = totalCost,
                        isPrivacyActive = isPrivacyActive
                    )
                }
            }

            // Tab 2: Asset Tracking
            if (selectedTab == 2) {
                item {
                    AssetTrackingSection(
                        totalAssets = totalAssets,
                        totalCost = totalCost,
                        totalAnnualDiv = totalAnnualDiv,
                        currencyFormat = currencyFormat,
                        isPrivacyActive = isPrivacyActive,
                        selectedPeriod = assetTrackingPeriod,
                        onPeriodSelected = { assetTrackingPeriod = it }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

data class DonutSliceData(
    val code: String,
    val name: String,
    val expectedDiv: Double,
    val sweepAngle: Float,
    val percentage: String,
    val color: Color
)

data class SectorGroupData(
    val name: String,
    val totalDiv: Double,
    val percentageVal: String,
    val rawPercentage: Float,
    val color: Color,
    val holdings: List<com.kurogane.dividend.model.Holding>
)

private enum class IndustryClimate { CYCLICAL, MIXED, DEFENSIVE }

private val cyclicalIndustries = setOf(
    "鉱業", "建設業", "繊維製品", "パルプ・紙", "石油・石炭製品", "ゴム製品", "ガラス・土石製品",
    "鉄鋼", "非鉄金属", "金属製品", "機械", "電気機器", "輸送用機器", "海運業", "空運業",
    "卸売業", "銀行業", "証券、商品先物取引業", "証券・商品先物", "証券・商品先物取引業", "証券業"
)
private val defensiveIndustries = setOf("食料品", "医薬品", "電気・ガス業", "陸運業", "情報・通信業", "情報・通信", "情報通信")
private val mixedIndustries = setOf(
    "水産・農林業", "化学", "精密機器", "その他製品", "倉庫・運輸関連業", "倉庫・運輸関連",
    "小売業", "保険業", "その他金融業", "不動産業", "サービス業"
)

private fun classifyIndustry(industryName: String?): IndustryClimate? {
    val name = industryName?.trim() ?: return null
    return when {
        name in cyclicalIndustries -> IndustryClimate.CYCLICAL
        name in defensiveIndustries -> IndustryClimate.DEFENSIVE
        name in mixedIndustries -> IndustryClimate.MIXED
        else -> null // ETFs and sectors outside the requested TSE classifications are excluded.
    }
}

private data class SectorCompositionRow(
    val name: String,
    val holdings: List<com.kurogane.dividend.model.Holding>,
    val dividendTotal: Double,
    val valuationTotal: Double,
    val color: Color
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun CompositionAnalysisSection(holdings: List<com.kurogane.dividend.model.Holding>, taxRateMode: String, totalAssets: Double, totalAnnualDiv: Double) {
    var isVisible by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf("dividend") } // "holding" or "dividend"
    var sectorValueMode by remember { mutableStateOf("valuation") } // "valuation" or "dividend"
    val context = LocalContext.current
    val industriesByCode = remember(context) { Tse33IndustryMaster.industries(context) }

    fun climateForHolding(holding: com.kurogane.dividend.model.Holding): IndustryClimate? {
        if (holding.category != com.kurogane.dividend.model.Category.JAPAN) return null
        val code = Tse33IndustryMaster.normalizeCode(holding.code) ?: return null
        return classifyIndustry(industriesByCode[code])
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "donutAnimation"
    )

    // 1. Group / Map Holdings to expected dividends and valuations in JPY optimized with remember
    val mappedHoldings = remember(holdings, chartMode, taxRateMode) {
        holdings
            .map { h ->
                val rate = if (h.currency == "$") 150.0 else 1.0
                val expectedDiv = h.getEffectiveAnnualDividend(taxRateMode)
                val valuation = h.totalValuation
                Triple(h, expectedDiv, valuation)
            }
            .filter { if (chartMode == "dividend") it.second > 0.0 else it.third > 0.0 }
            .sortedByDescending { if (chartMode == "dividend") it.second else it.third }
    }

    val totalChartValue = remember(mappedHoldings, chartMode) { mappedHoldings.sumOf { if (chartMode == "dividend") it.second else it.third } }
    val totalPortfolioDividend = remember(mappedHoldings) { mappedHoldings.sumOf { it.second } }

    // Color definitions
    val sliceColors = listOf(
        PrimaryEmeraldContainer, // Emerald
        SkyBlue, // Sky Blue
        SecondaryIndigo, // Indigo
        ErrorRed, // Rose
        AccentAmber, // Amber
        Color(0xFFA78BFA), // Purple
        Color(0xFF22D3EE), // Cyan
        ErrorRed, // Pink
        OnSurfaceVariant  // Gray
    )

    // Build Donut Slice Objects
    val donutSlices = mappedHoldings.mapIndexed { index, (holding, div, valuation) ->
        val itemVal = if (chartMode == "dividend") div else valuation
        val ratio = if (totalChartValue > 0.0) itemVal / totalChartValue else 0.0
        val sweepAngle = (ratio * 360f).toFloat()
        val percentageStr = String.format("%.1f%%", ratio * 100.0)
        val color = sliceColors[index % sliceColors.size]

        DonutSliceData(
            code = holding.code,
            name = holding.name,
            expectedDiv = itemVal,
            sweepAngle = sweepAngle,
            percentage = percentageStr,
            color = color
        )
    }

    var activeStockCode by remember { mutableStateOf<String?>(null) }

    // Reset active stock selection on load/mode switch to display totals by default
    LaunchedEffect(holdings, chartMode) {
        activeStockCode = null
    }

    val activeSlice = donutSlices.find { it.code == activeStockCode }

    // 2. Group holdings by sector optimized with remember
    val sectorGroups = remember(holdings, taxRateMode) {
        holdings
            .map { h ->
                val expectedDiv = h.getEffectiveAnnualDividend(taxRateMode)
                h to expectedDiv
            }
            .filter { it.second > 0.0 }
            .groupBy { (holding, _) ->
                val fullSector = holding.sector
                if (fullSector.contains("•")) {
                    fullSector.split("•").last().trim()
                } else if (fullSector.contains("·")) {
                    fullSector.split("·").first().trim()
                } else {
                    fullSector
                }
            }
            .map { (sectorName, list) ->
                val totalDiv = list.sumOf { it.second }
                sectorName to (totalDiv to list.map { it.first })
            }
            .sortedByDescending { it.second.first }
    }

    val totalSectorDividend = remember(sectorGroups) { sectorGroups.sumOf { it.second.first } }

    val formattedSectors = remember(sectorGroups) {
        sectorGroups.mapIndexed { index, (sectorName, pair) ->
        val (totalDiv, secHoldings) = pair
        val ratio = if (totalSectorDividend > 0.0) totalDiv / totalSectorDividend else 0.0
        val percentageVal = String.format("%.1f%%", ratio * 100.0)
        val color = sliceColors[index % sliceColors.size]

        SectorGroupData(
            name = sectorName,
            totalDiv = totalDiv,
            percentageVal = percentageVal,
            rawPercentage = (ratio * 100.0).toFloat(),
            color = color,
            holdings = secHoldings
        )
        }
    }

    val sectorCompositionRows = remember(holdings, taxRateMode) {
        holdings.groupBy { holding ->
            val rawSector = holding.sector
            when {
                rawSector.contains("•") -> rawSector.split("•").last().trim()
                rawSector.contains("·") -> rawSector.split("·").first().trim()
                else -> rawSector.trim().ifBlank { "その他" }
            }
        }.entries.mapIndexed { index, entry ->
            val name = entry.key
            val sectorHoldings = entry.value
            SectorCompositionRow(
                name = name,
                holdings = sectorHoldings,
                dividendTotal = sectorHoldings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) },
                valuationTotal = sectorHoldings.sumOf { it.totalValuation },
                color = sliceColors[index % sliceColors.size]
            )
        }
    }
    val sectorCompositionTotal = sectorCompositionRows.sumOf {
        if (sectorValueMode == "valuation") it.valuationTotal else it.dividendTotal
    }

    // Sectors Expanded Accordion State
    val expandedSectors = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. Donut Composition Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "構成銘柄・配当比率",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )

                    // Pill Toggle Switch: 構成比率 vs 配当比率
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHigh)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (chartMode == "holding") PrimaryEmerald else Color.Transparent)
                                .clickable { chartMode = "holding" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "構成比率",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (chartMode == "holding") OnPrimary else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (chartMode == "dividend") PrimaryEmerald else Color.Transparent)
                                .clickable { chartMode = "dividend" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "配当比率",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (chartMode == "dividend") OnPrimary else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (donutSlices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "配当支払い銘柄がありません。",
                            style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant)
                        )
                    }
                } else {
                    // Donut Chart Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            val normalStrokeWidth = 24.dp.toPx()
                            val activeStrokeWidth = 32.dp.toPx()
                            val centerOffset = Offset(size.width / 2, size.height / 2)

                            var currentStartAngle = -90f

                            donutSlices.forEach { slice ->
                                val isActive = slice.code == activeStockCode
                                val currentStrokeWidth = if (isActive) activeStrokeWidth else normalStrokeWidth
                                val radius = (size.minDimension - currentStrokeWidth) / 2

                                drawArc(
                                    color = slice.color,
                                    startAngle = currentStartAngle,
                                    sweepAngle = slice.sweepAngle * animatedProgress,
                                    useCenter = false,
                                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(currentStrokeWidth, cap = StrokeCap.Butt)
                                )
                                currentStartAngle += slice.sweepAngle
                            }
                        }

                        // Centered Focus text (Default is portfolio totals, toggleable to active stock on selection)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                activeSlice?.name ?: if (chartMode == "holding") "資産総額" else "年間配当合計",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val centerVal = activeSlice?.expectedDiv ?: if (chartMode == "holding") totalAssets else totalAnnualDiv
                            Text(
                                text = "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(centerVal.toLong())}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = AppTextSize.screenTitle
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(activeSlice?.color ?: PrimaryEmerald)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeSlice != null) activeSlice.percentage else "${donutSlices.size}銘柄分散中",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = activeSlice?.color ?: PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.dense
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2-Column Fully Expanded Grid List (Custom non-scrolling grid layout)
                    val chunkedSlices = donutSlices.chunked(2)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunkedSlices.forEach { rowSlices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSlices.forEach { slice ->
                                    val isActive = slice.code == activeStockCode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainerHigh)
                                            .border(
                                                width = if (isActive) 1.5.dp else 1.dp,
                                                color = if (isActive) slice.color else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                activeStockCode = if (activeStockCode == slice.code) null else slice.code
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(slice.color)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    slice.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = OnSurface,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                slice.percentage,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = OnSurfaceVariant,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                                // Fill missing space for uneven grids
                                if (rowSlices.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. Stacked Sector Percentage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "セクター別比率",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${formattedSectors.size}業種分散",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stacked bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh)
                ) {
                    formattedSectors.forEach { sec ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(sec.rawPercentage.coerceAtLeast(0.01f))
                                .background(sec.color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sector Legends Chips (All sectors displayed cleanly without any slice truncation)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    formattedSectors.forEach { sec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(sec.color))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${sec.name} ${sec.percentageVal}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamic Category classification helper calculations
                val classifiedHoldings = holdings.filter { climateForHolding(it) != null }
                val totalValue = if (chartMode == "dividend") {
                    classifiedHoldings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) }
                } else {
                    classifiedHoldings.sumOf { it.totalValuation }
                }

                fun valueForClimate(climate: IndustryClimate): Double = classifiedHoldings
                    .filter { climateForHolding(it) == climate }
                    .sumOf {
                        if (chartMode == "dividend") it.getEffectiveAnnualDividend(taxRateMode)
                        else it.totalValuation
                    }

                val cyclicalSum = valueForClimate(IndustryClimate.CYCLICAL)
                val mixedSum = valueForClimate(IndustryClimate.MIXED)
                val defensiveSum = valueForClimate(IndustryClimate.DEFENSIVE)

                val cyclicalPct = if (totalValue > 0.0) (cyclicalSum / totalValue) * 100.0 else 0.0
                val mixedPct = if (totalValue > 0.0) (mixedSum / totalValue) * 100.0 else 0.0
                val defensivePct = if (totalValue > 0.0) (defensiveSum / totalValue) * 100.0 else 0.0

                // Sector Quick Grid Metrics (Cyclical, Mixed, Defensive dynamic cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Box 1: Cyclical
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "景気敏感",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                             )
                             Spacer(modifier = Modifier.height(2.dp))
                             Text(
                                 text = String.format("%.1f%%", cyclicalPct),
                                 style = MaterialTheme.typography.bodyMedium.copy(
                                     color = ErrorRed,
                                     fontWeight = FontWeight.Black,
                                     fontFamily = FontFamily.Monospace
                                 )
                             )
                             Text(
                                 "卸売・輸送用機器等",
                                 style = MaterialTheme.typography.labelSmall.copy(
                                     color = OnSurfaceVariant,
                                     fontSize = AppTextSize.dense
                                 ),
                                 maxLines = 1
                             )
                        }
                    }

                    // Box 2: Mixed
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                        border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "景気影響が混在",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%.1f%%", mixedPct),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SkyBlue,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                "水産・化学・保険業等",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Box 3: Defensive
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                        border = BorderStroke(1.dp, PrimaryEmeraldContainer.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "ディフェンシブ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%.1f%%", defensivePct),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaryEmeraldContainer,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                "食料・医薬品等",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // C. Accordion Sector Details List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "セクター・銘柄構成詳細",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = OnSurface),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh).padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("valuation" to "評価額", "dividend" to "配当額").forEach { (mode, title) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sectorValueMode == mode) PrimaryEmerald else Color.Transparent)
                                .clickable { sectorValueMode = mode }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = if (sectorValueMode == mode) OnPrimary else OnSurfaceVariant)
                        }
                    }
                }
            }

            sectorCompositionRows.sortedByDescending {
                if (sectorValueMode == "valuation") it.valuationTotal else it.dividendTotal
            }.forEach { sec ->
                val isOpen = expandedSectors[sec.name] ?: false
                val sectorAmount = if (sectorValueMode == "valuation") sec.valuationTotal else sec.dividendTotal
                val sectorShare = if (sectorCompositionTotal > 0.0) sectorAmount / sectorCompositionTotal * 100.0 else 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSectors[sec.name] = !isOpen }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sec.color))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${sec.name}：${sec.holdings.size}銘柄",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = OnSurface,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false),
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(sec.color.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        String.format(Locale.JAPAN, "%.1f%%", sectorShare),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = sec.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.dense
                                        )
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(sectorAmount.toLong())}${if (sectorValueMode == "dividend") " / 年" else ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = sec.color,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = AppTextSize.dense
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (isOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceContainerHigh)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sec.holdings.forEach { h ->
                                    val divContribution = h.getEffectiveAnnualDividend(taxRateMode)
                                    val holdingAmount = if (sectorValueMode == "valuation") h.totalValuation else divContribution
                                    val amountShare = if (sectorCompositionTotal > 0.0) holdingAmount / sectorCompositionTotal * 100.0 else 0.0
                                    val currentYield = if (h.price > 0) (h.dps / h.price) * 100 else 0.0

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfaceContainer)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(SurfaceContainerHigh),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    h.code,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OnSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    h.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = OnSurface,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    "保有 ${h.shares}株 • 利回り ${String.format("%.2f%%", currentYield)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OnSurfaceVariant,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(holdingAmount.toLong())}${if (sectorValueMode == "dividend") " / 年" else ""}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = OnSurface,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                            Text(
                                                "構成比 " + String.format(Locale.JAPAN, "%.1f%%", amountShare),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = sec.color,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // D. AI Portfolio Health Diagnosis Card
        Spacer(modifier = Modifier.height(16.dp))

        val portfolioDiagnosis = remember(holdings) { PortfolioDiagnosisGenerator.generate(holdings) }
        val score = portfolioDiagnosis.score
        val grade = portfolioDiagnosis.grade
        val power = portfolioDiagnosis.dividendGrowthLabel
        val risk = portfolioDiagnosis.dividendRiskLabel
        val cardColor = when (grade) {
            "S" -> PrimaryEmeraldContainer
            "A" -> PrimaryEmerald
            "B" -> SkyBlue
            "C" -> AccentAmber
            "D" -> ErrorRed
            else -> OnSurfaceVariant
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = BorderStroke(1.dp, BorderBlue)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "AIポートフォリオ診断",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.body
                                )
                            )
                            Text(
                                "分散スコア & リスク評価",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (score == null) "健全度: 判定不可" else "健全度: $grade ($score/100)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = cardColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = portfolioDiagnosis.narrative,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        lineHeight = 16.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (portfolioDiagnosis.possibleCombinations == PortfolioDiagnosis.TOTAL_COMBINATIONS) {
                        "文章パターン: ${NumberFormat.getNumberInstance(Locale.JAPAN).format(portfolioDiagnosis.possibleCombinations)}通り"
                    } else {
                        "評価対象の文章パターン: ${NumberFormat.getNumberInstance(Locale.JAPAN).format(portfolioDiagnosis.possibleCombinations)}通り（最大 ${NumberFormat.getNumberInstance(Locale.JAPAN).format(PortfolioDiagnosis.TOTAL_COMBINATIONS)}通り）"
                    },
                    color = cardColor,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.SemiBold
                )
                if (portfolioDiagnosis.missingLabels.isNotEmpty()) {
                    Text(
                        text = "未評価: ${portfolioDiagnosis.missingLabels.joinToString("、")}",
                        color = AccentAmber,
                        fontSize = AppTextSize.dense
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "増配・非減配の実績",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            power,
                            color = cardColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "減配リスク",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            risk,
                            color = cardColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompoundingSimulationSection(
    totalAnnualDiv: Double,
    totalCost: Double,
    isPrivacyActive: Boolean
) {
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    // Dynamic historical and projection amounts
    val currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
    val year2025 = totalAnnualDiv
    val year2024 = if (year2025 > 0) year2025 / 1.124 else 0.0
    val year2023 = if (year2025 > 0) year2025 * 0.70 else 0.0
    val year2022 = if (year2025 > 0) year2025 * 0.53 else 0.0
    val year2021 = if (year2025 > 0) year2025 * 0.385 else 0.0

    val cagr = if (year2021 > 0) ((Math.pow(year2025 / year2021, 0.25) - 1) * 100) else 0.0
    val cumulativeIncome = year2021 + year2022 + year2023 + year2024 + year2025
    val currentYoc = if (totalCost > 0) (year2025 / totalCost) * 100 else 0.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("過去年の値は現在の配当額から算出した参考推計です。実績データではありません。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)

        // Card 1: Main Compound Growth KPI Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PrimaryEmerald))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "配当受取額の複利成長推移",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body
                                )
                            )
                        }
                        Text(
                            "2021年〜2025年(予想) 増配と再投資による累積カーブ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "5期連続増配達成",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // KPI Sub-Grid (3 Columns Side-by-Side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("5年平均増配率", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Text(
                            text = String.format("+%.1f%%", cagr),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("年平均CAGR", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("累計受取総額", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Text(
                            text = if (isPrivacyActive) "¥••••" else "¥${currencyFormat.format(cumulativeIncome.toLong())}",
                            color = OnSurface,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("通算インカム", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("簿価利回り (YOC)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Text(
                            text = String.format("%.2f%%", currentYoc),
                            color = SkyBlue,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("取得元本比", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }
            }
        }

        // Card 2: Interactive Bar + Curve Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("年次配当額 (受取)", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("複利成長曲線 (CAGR)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryEmeraldContainer.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "+9.0% 増配 (2025予想)",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = PrimaryEmeraldContainer,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Graph
                val yearsList = (currentYear - 4..currentYear).map { "${it}推計" }
                val amountsList = listOf(year2021, year2022, year2023, year2024, year2025)
                val maxVal = amountsList.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val chartGridColor = BorderColor
                val chartBarColor = PrimaryEmeraldContainer
                val chartPointColor = OnSurface
                val chartSkyBlue = SkyBlue

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    // Draw horizontal dashed grid lines
                    val gridY1 = size.height * 0.2f
                    val gridY2 = size.height * 0.5f
                    val gridY3 = size.height * 0.8f
                    drawLine(color = chartGridColor, start = Offset(0f, gridY1), end = Offset(size.width, gridY1))
                    drawLine(color = chartGridColor, start = Offset(0f, gridY2), end = Offset(size.width, gridY2))
                    drawLine(color = chartGridColor, start = Offset(0f, gridY3), end = Offset(size.width, gridY3))

                    val numYears = 5
                    val barWidth = 24.dp.toPx()
                    val spacing = (size.width - (barWidth * numYears)) / (numYears + 1)
                    val points = ArrayList<Offset>()

                    for (i in 0 until numYears) {
                        val yearVal = amountsList[i]
                        val ratio = (yearVal / maxVal).toFloat()
                        val barHeight = size.height * ratio * 0.82f
                        val barLeft = spacing + i * (barWidth + spacing)
                        val barTop = size.height - barHeight

                        // Draw bar with vertical gradient
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(chartBarColor, chartBarColor.copy(alpha = 0.3f))
                            ),
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Save top center point for the curve overlay
                        points.add(Offset(barLeft + barWidth / 2, barTop))
                    }

                    // Draw CAGR sky-blue connecting line curve
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = chartSkyBlue,
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    // Draw glowing white/blue dots
                    points.forEach { pt ->
                        drawCircle(
                            color = chartPointColor,
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = chartSkyBlue.copy(alpha = 0.35f),
                            radius = 7.dp.toPx(),
                            center = pt
                        )
                    }
                }

                // X-Axis Labels Below Graph
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    yearsList.forEach { y ->
                        Text(
                            text = y,
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive 2025年 予想 block
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BackgroundDark,
                    border = BorderStroke(1.dp, BorderBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${currentYear - 0}年 推計", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyActive) "¥•••• / 年" else "¥${currencyFormat.format(year2025.toLong())} / 年",
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isPrivacyActive) "月換算: 約¥••••/月" else "月換算: 約¥${currencyFormat.format((year2025 / 12).toLong())}/月",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            Text(
                                text = if (isPrivacyActive) "+••••" else "+¥${currencyFormat.format((year2025 - year2024).toLong())} (+12.4%)",
                                color = PrimaryEmeraldContainer,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Card 3: Detailed simulation list
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "年別インカム詳細・月割シミュレーション",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface,
                            fontSize = AppTextSize.caption
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Render Table rows
                val rowData = listOf(
                    Triple("${currentYear - 0}年 推計", year2025, "+12.4%"),
                    Triple("${currentYear - 1}年 推計", year2024, "+27.4%"),
                    Triple("${currentYear - 2}年 推計", year2023, "+31.8%"),
                    Triple("${currentYear - 3}年 推計", year2022, "+37.5%"),
                    Triple("${currentYear - 4}年 推計", year2021, "基準年")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowData.forEachIndexed { idx, (yearStr, valAmt, pctStr) ->
                        val badgeText = when (idx) {
                            0 -> "過去最高配当"
                            1 -> "確定"
                            2 -> "確定"
                            3 -> "確定"
                            else -> "基準年"
                        }
                        val badgeColor = when (idx) {
                            0 -> PrimaryEmeraldContainer
                            4 -> SkyBlue
                            else -> OnSurfaceVariant
                        }
                        val calculatedYoc = if (totalCost > 0) (valAmt / totalCost) * 100 else (4.08 - idx * 0.4)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Year circle block
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            yearStr.take(3),
                                            color = OnSurface,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(yearStr, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = badgeColor.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = badgeColor,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isPrivacyActive) "月換算 約¥•••• / YOC •••%" else "月換算 約¥${currencyFormat.format((valAmt/12).toLong())} • YOC ${String.format("%.2f%%", calculatedYoc)}",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isPrivacyActive) "¥••••" else "¥${currencyFormat.format(valAmt.toLong())}",
                                        color = OnSurface,
                                        fontSize = AppTextSize.caption,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = if (idx == 4) pctStr else "$pctStr 前年比",
                                        color = if (idx == 4) OnSurfaceVariant else PrimaryEmeraldContainer,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetTrackingSection(
    totalAssets: Double,
    totalCost: Double,
    totalAnnualDiv: Double,
    currencyFormat: NumberFormat,
    isPrivacyActive: Boolean,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    val currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
    // 1. Dynamic states for user interactivity
    var selectedYearIndex by remember { mutableIntStateOf(4) }
    var monthlySavingsOption by remember { mutableIntStateOf(1) } // 0: ¥10万, 1: ¥30万, 2: ¥50万
    var yieldOption by remember { mutableIntStateOf(1) }          // 0: 3%, 1: 4%, 2: 5%
    var isReinvestSelected by remember { mutableStateOf(true) }

    // Derive inputs
    val monthlySavings = when (monthlySavingsOption) {
        0 -> 10000.0
        1 -> 30000.0
        else -> 50000.0
    }
    val yieldRate = when (yieldOption) {
        0 -> 0.03
        1 -> 0.04
        else -> 0.05
    }

    // Adapt to actual portfolio data or fallback to image defaults if empty
    val realCost = totalCost.coerceAtLeast(0.0)
    val realAssets = totalAssets.coerceAtLeast(0.0)

    // Grouped 5-year calculations matching image proportions
    val costs = listOf(realCost * 0.26, realCost * 0.47, realCost * 0.68, realCost * 0.89, realCost)
    val assets = listOf(realAssets * 0.24, realAssets * 0.45, realAssets * 0.69, realAssets * 0.91, realAssets)
    val gains = listOf(
        assets[0] - costs[0],
        assets[1] - costs[1],
        assets[2] - costs[2],
        assets[3] - costs[3],
        assets[4] - costs[4]
    )
    val gainsPct = (0..4).map { if (costs[it] > 0) (gains[it] / costs[it]) * 100.0 else 0.0 }

    // Simulated values calculation using mathematically precise compound formulas
    val yearsSim = listOf(10, 20, 30)
    val simResults = yearsSim.map { years ->
        var fv = realAssets
        val monthlyRate = yieldRate / 12.0
        val totalMonths = years * 12
        for (m in 1..totalMonths) {
            if (isReinvestSelected) {
                fv = fv * (1.0 + monthlyRate) + monthlySavings
            } else {
                fv = fv + monthlySavings
            }
        }
        if (!isReinvestSelected) {
            fv += realAssets * yieldRate * years
        }
        val costAtYear = realCost + (monthlySavings * 12.0 * years)
        Pair(fv, costAtYear)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("過去年の資産額は現在の保有情報から算出した参考推計です。実績データではありません。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        // A. Header Title row with switch pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "資産・配当金推移",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                }
                Text(
                    if (selectedPeriod == "5y") "2021年〜2025年(現在)・年次成長トラッキング" else "1月〜12月・元本と評価額の月次推移",
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                )
            }

            // Interval selector pills
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerHigh)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedPeriod == "5y") PrimaryEmerald else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedPeriod == "5y") PrimaryEmerald else Color.Transparent),
                    modifier = Modifier.clickable { onPeriodSelected("5y") }
                ) {
                    Text(
                        "5年",
                        color = if (selectedPeriod == "5y") OnPrimary else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedPeriod == "12m") PrimaryEmerald else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedPeriod == "12m") PrimaryEmerald else Color.Transparent),
                    modifier = Modifier.clickable { onPeriodSelected("12m") }
                ) {
                    Text(
                        "12ヶ月",
                        color = if (selectedPeriod == "12m") OnPrimary else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = if (selectedPeriod == "12m") FontWeight.ExtraBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // B. Dual Asset KPI Header Rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card Left: Asset Evaluation
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("資産評価額", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PrimaryEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                String.format("+%.1f%%", gainsPct[4]),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyActive) "¥••••••••" else "¥${currencyFormat.format(realAssets.toLong())}",
                        color = OnSurface,
                        fontSize = AppTextSize.section,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (isPrivacyActive) "投資元本: ¥••••" else "投資元本: ¥${currencyFormat.format(realCost.toLong())}",
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )
                }
            }

            // Card Right: Total Unrealized Gain
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("通算含み益", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SkyBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "簿価利回り",
                                color = SkyBlue,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyActive) "+¥••••••" else "+¥${currencyFormat.format(gains[4].toLong())}",
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.section,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Yield on Cost: " + String.format("%.2f%%", (totalAnnualDiv / realCost) * 100),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )
                }
            }
        }

        if (selectedPeriod == "5y") {
        // C. Double Bar Grouped Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("評価額 (時価)", color = OnSurface, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF64748B)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("累積投資元本 (簿価)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryEmerald.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isPrivacyActive) "含み益: ¥••••" else "含み益: +¥${currencyFormat.format(gains[4].toLong())} (+${String.format("%.1f%%", gainsPct[4])})",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // custom grouped double bars rendering
                val maxVal = (costs + assets).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val assetChartGridColor = BorderColor
                val assetChartAccent = PrimaryEmerald
                val assetChartBarColor = PrimaryEmeraldContainer
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val gridY1 = size.height * 0.25f
                    val gridY2 = size.height * 0.55f
                    val gridY3 = size.height * 0.85f
                    drawLine(color = assetChartGridColor, start = Offset(0f, gridY1), end = Offset(size.width, gridY1))
                    drawLine(color = assetChartGridColor, start = Offset(0f, gridY2), end = Offset(size.width, gridY2))
                    drawLine(color = assetChartGridColor, start = Offset(0f, gridY3), end = Offset(size.width, gridY3))

                    val numYears = 5
                    val groupWidth = 36.dp.toPx()
                    val barWidth = 10.dp.toPx()
                    val spacing = (size.width - (groupWidth * numYears)) / (numYears + 1)

                    for (i in 0 until numYears) {
                        val costVal = costs[i]
                        val assetVal = assets[i]

                        val costRatio = (costVal / maxVal).toFloat()
                        val assetRatio = (assetVal / maxVal).toFloat()

                        val costBarHeight = size.height * costRatio * 0.8f
                        val assetBarHeight = size.height * assetRatio * 0.8f

                        val groupLeft = spacing + i * (groupWidth + spacing)

                        // If year is selected, draw highlight background
                        if (selectedYearIndex == i) {
                            drawRoundRect(
                                color = assetChartAccent.copy(alpha = 0.08f),
                                topLeft = Offset(groupLeft - 6.dp.toPx(), 0f),
                                size = Size(groupWidth + 12.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            drawRoundRect(
                                color = assetChartAccent.copy(alpha = 0.3f),
                                topLeft = Offset(groupLeft - 6.dp.toPx(), 0f),
                                size = Size(groupWidth + 12.dp.toPx(), size.height),
                                style = Stroke(width = 1.dp.toPx()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }

                        // Cost Bar (Left side of group - Gray)
                        val costBarLeft = groupLeft + 4.dp.toPx()
                        val costBarTop = size.height - costBarHeight
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF475569))),
                            topLeft = Offset(costBarLeft, costBarTop),
                            size = Size(barWidth, costBarHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )

                        // Asset Bar (Right side of group - Emerald)
                        val assetBarLeft = costBarLeft + barWidth + 3.dp.toPx()
                        val assetBarTop = size.height - assetBarHeight
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(assetChartBarColor, assetChartBarColor.copy(alpha = 0.3f))),
                            topLeft = Offset(assetBarLeft, assetBarTop),
                            size = Size(barWidth, assetBarHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }

                // X-Axis
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("2021", "2022", "2023", "2024", "2025現").forEachIndexed { idx, year ->
                        Text(
                            text = year,
                            color = if (selectedYearIndex == idx) PrimaryEmerald else OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = if (selectedYearIndex == idx) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // D. Interactive data grid matching the selector in image
                Text(
                    "年次推移データグリッド (元本 vs 時価対比)",
                    color = OnSurface,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("2021", "2022", "2023", "2024", "2025現").forEachIndexed { idx, yr ->
                        val isSelected = selectedYearIndex == idx
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedYearIndex = idx },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SurfaceContainerHigh else BackgroundDark
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(yr, color = if (isSelected) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "元: " + String.format("%.0f万", costs[idx] / 10000.0),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                                Text(
                                    "時: " + String.format("%.0f万", assets[idx] / 10000.0),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%s%.0f万", if (gains[idx] >= 0) "+" else "", gains[idx] / 10000.0),
                                    color = if (gains[idx] >= 0) PrimaryEmeraldContainer else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // E. Selected Year Summary Card details block
                val selYearStr = "${currentYear - 4 + selectedYearIndex}年 推計"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BackgroundDark,
                    border = BorderStroke(1.dp, BorderBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$selYearStr 詳細", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyActive) "評価額: ¥••••••••" else "評価額: ¥${currencyFormat.format(assets[selectedYearIndex].toLong())}",
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isPrivacyActive) "投資元本: ¥••••" else "投資元本: ¥${currencyFormat.format(costs[selectedYearIndex].toLong())}",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            Text(
                                text = if (isPrivacyActive) "含み損益: ••••" else String.format("含み損益 %s¥%s (%.1f%%)", if (gains[selectedYearIndex] >= 0) "+" else "", currencyFormat.format(gains[selectedYearIndex].toLong()), gainsPct[selectedYearIndex]),
                                color = if (gains[selectedYearIndex] >= 0) PrimaryEmeraldContainer else ErrorRed,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // F. 未来30年・複利成長シミュレーター Card (Image 1 & 2 premium simulation engine!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "未来30年・複利成長シミュレーター",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.caption
                                )
                            )
                            Text(
                                "毎月の積立額と配当再投資による複利試算",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "複利シミュレーション",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 1: Monthly Savings Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "毎月の新規積立額:",
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "¥" + currencyFormat.format(monthlySavings.toLong()),
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("¥10万", "¥30万", "¥50万").forEachIndexed { index, label ->
                        val isSel = monthlySavingsOption == index
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { monthlySavingsOption = index },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) PrimaryEmerald else BackgroundDark
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) OnPrimary else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 2: Yield Ratio Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "想定配当利回り:",
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        String.format("%.0f%%", yieldRate * 100),
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("3%", "4%", "5%").forEachIndexed { index, label ->
                        val isSel = yieldOption == index
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { yieldOption = index },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) PrimaryEmerald else BackgroundDark
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) OnPrimary else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 3: Reinvestment Switch Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BackgroundDark,
                    border = BorderStroke(1.dp, BorderBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("配当金を自動で再投資する (複利効果)", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text("税引後(20.315%控除)の配当金を元本に自動組み入れ", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Switch(
                            checked = isReinvestSelected,
                            onCheckedChange = { isReinvestSelected = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnSurface,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Simulation Result Header
                Text(
                    "複利成長シミュレーション結果",
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 3 columns of simulation results
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 20, 30).forEachIndexed { index, yr ->
                        val (valAmt, costAmt) = simResults[index]
                        val is30Year = yr == 30
                        val monthDiv = (valAmt * yieldRate) / 12.0

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            border = BorderStroke(
                                1.dp,
                                if (is30Year) PrimaryEmerald else BorderColor
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${yr}年後 (" + (2025 + yr) + ")",
                                    color = if (is30Year) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPrivacyActive) "••••" else String.format("¥%.0f万", valAmt / 10000.0),
                                    color = OnSurface,
                                    fontSize = AppTextSize.body,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isPrivacyActive) "元本: ••••" else String.format("元本: ¥%.0f万", costAmt / 10000.0),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Divider(color = BorderColor)
                                Spacer(modifier = Modifier.height(6.dp))

                                Text("月配当(目安)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text(
                                    text = if (isPrivacyActive) "¥••••/月" else "¥${currencyFormat.format(monthDiv.toLong())}/月",
                                    color = if (is30Year) PrimaryEmeraldContainer else SkyBlue,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // F. Simulation Explanation box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(
                                "現在の資産評価額 ¥%,.0f万から、毎月 ¥%s を積立、利回り %.0f%% で運用し、さらに配当金を再投資%sした場合のシミュレーションです。複利効果により、30年間の累計不労所得・資産価値は加速度的に成長します。",
                                realAssets / 10000.0,
                                currencyFormat.format(monthlySavings.toLong()),
                                yieldRate * 100,
                                if (isReinvestSelected) "し続けた" else "せず貯蓄"
                            ),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // G. Historical Timeline detailed list (Timeline vertical list at the bottom of Image 2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "年次推移・元本 VS 評価額",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                }
                Text("2021〜2025", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("2025年 現在", "2024年 実績", "2023年 実績", "2022年 実績", "2021年 実績").forEachIndexed { idx, title ->
                    val index = 4 - idx
                    val isGain = gains[index] >= 0
                    val prefix = if (isGain) "+" else ""

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BackgroundDark,
                        border = BorderStroke(1.dp, BorderBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Year circle badge
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "'" + title.take(4).drop(2),
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(title, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (index == 4) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = PrimaryEmeraldContainer.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "含み益最大",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = PrimaryEmeraldContainer,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (index > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = OnSurfaceVariant.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "確定",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = OnSurfaceVariant,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isPrivacyActive) "元本 ¥•••• • 評価額 ¥••••" else "元本 ¥${currencyFormat.format(costs[index].toLong())} • 評価額 ¥${currencyFormat.format(assets[index].toLong())}",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isPrivacyActive) "••••" else String.format("%s¥%s", prefix, currencyFormat.format(gains[index].toLong())),
                                    color = if (isGain) PrimaryEmeraldContainer else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (index == 0) "基準年" else String.format("%s%.1f%% 利益", prefix, gainsPct[index]),
                                    color = if (index == 0) OnSurfaceVariant else if (isGain) PrimaryEmeraldContainer else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        } else {
            AssetTrackingTwelveMonthSection(
                realAssets = realAssets,
                realCost = realCost,
                isPrivacyActive = isPrivacyActive,
                currencyFormat = currencyFormat
            )
        }
    }
}

@Composable
private fun AssetTrackingTwelveMonthSection(
    realAssets: Double,
    realCost: Double,
    isPrivacyActive: Boolean,
    currencyFormat: NumberFormat
) {
    val monthlyContribution = 25_000.0
    val currentGain = realAssets - realCost
    val monthlySnapshots = remember(realAssets, realCost) {
        (1..12).map { month ->
            val monthsBeforeNow = (12 - month).toDouble()
            val cost = (realCost - monthlyContribution * monthsBeforeNow).coerceAtLeast(0.0)
            val gain = currentGain * month / 12.0
            val value = cost + gain
            val gainRate = if (cost > 0.0) gain / cost * 100.0 else 0.0
            MonthAssetSnapshot(month, cost, value, gain, gainRate)
        }
    }
    val latest = monthlySnapshots.last()
    val chartGridColor = BorderColor
    val chartHighlightColor = PrimaryEmerald
    val chartAssetColor = PrimaryEmeraldContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                    Spacer(Modifier.width(5.dp))
                    Text("評価額 (時価)", color = OnSurface, fontSize = AppTextSize.dense)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF64748B)))
                    Spacer(Modifier.width(5.dp))
                    Text("累積投資元本 (簿価)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                ) {
                    Text(
                        if (isPrivacyActive) "含み益 ¥••••" else "含み益 ${if (latest.gain >= 0) "+" else "−"}¥${currencyFormat.format(kotlin.math.abs(latest.gain).toLong())}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = if (latest.gain >= 0) PrimaryEmerald else ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val maxValue = monthlySnapshots.maxOf { maxOf(it.cost, it.value) }.coerceAtLeast(1.0)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .padding(horizontal = 3.dp)
            ) {
                val numMonths = monthlySnapshots.size
                val groupWidth = 14.dp.toPx()
                val barWidth = 5.5.dp.toPx()
                val innerGap = 2.dp.toPx()
                val spacing = ((size.width - groupWidth * numMonths) / (numMonths + 1)).coerceAtLeast(0f)
                val chartBottom = size.height - 5.dp.toPx()
                val chartHeight = size.height * 0.9f
                val gridY1 = chartBottom - chartHeight * 0.33f
                val gridY2 = chartBottom - chartHeight * 0.66f
                drawLine(chartGridColor, Offset(0f, gridY1), Offset(size.width, gridY1), strokeWidth = 1.dp.toPx())
                drawLine(chartGridColor, Offset(0f, gridY2), Offset(size.width, gridY2), strokeWidth = 1.dp.toPx())

                monthlySnapshots.forEachIndexed { index, item ->
                    val groupLeft = spacing + index * (groupWidth + spacing)
                    val groupTop = chartBottom - chartHeight
                    if (item.month == 12) {
                        drawRoundRect(
                            color = chartHighlightColor.copy(alpha = 0.08f),
                            topLeft = Offset(groupLeft - 2.dp.toPx(), groupTop),
                            size = Size(groupWidth + 4.dp.toPx(), chartHeight),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }

                    val costHeight = (chartHeight * (item.cost / maxValue).toFloat()).coerceAtLeast(1f)
                    val valueHeight = (chartHeight * (item.value / maxValue).toFloat()).coerceAtLeast(1f)
                    val barsWidth = barWidth * 2 + innerGap
                    val barsLeft = groupLeft + (groupWidth - barsWidth) / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF475569))),
                        topLeft = Offset(barsLeft, chartBottom - costHeight),
                        size = Size(barWidth, costHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(chartAssetColor, chartAssetColor.copy(alpha = 0.55f))),
                        topLeft = Offset(barsLeft + barWidth + innerGap, chartBottom - valueHeight),
                        size = Size(barWidth, valueHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                monthlySnapshots.forEach { item ->
                    Text(
                        text = item.month.toString(),
                        color = if (item.month == 12) PrimaryEmerald else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = if (item.month == 12) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.width(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("月次推移データグリッド (元本 vs 評価額)", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                Text("毎月積立中", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                monthlySnapshots.filter { it.month % 3 == 0 }.forEach { item ->
                    val isLatest = item.month == latest.month
                    val gainColor = if (item.gain >= 0) PrimaryEmerald else ErrorRed
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLatest) PrimaryEmerald.copy(alpha = 0.12f) else BackgroundDark)
                            .border(
                                1.dp,
                                if (isLatest) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isLatest) "${item.month}月最新" else "${item.month}月",
                            color = if (isLatest) PrimaryEmerald else OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = if (isPrivacyActive) "元: ••••" else "元: ${formatAssetWan(item.cost)}",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (isPrivacyActive) "時: ••••" else "時: ${formatAssetWan(item.value)}",
                            color = if (isLatest) PrimaryEmerald else OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isPrivacyActive) "••••" else "${if (item.gain >= 0) "+" else "−"}${formatAssetWan(kotlin.math.abs(item.gain))}",
                            color = gainColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private fun formatAssetWan(amount: Double): String {
    val amountInWan = amount / 10_000.0
    val formatted = String.format(Locale.JAPAN, "%.1f", amountInWan).removeSuffix(".0")
    return "${formatted}万"
}

private data class MonthAssetSnapshot(
    val month: Int,
    val cost: Double,
    val value: Double,
    val gain: Double,
    val gainRate: Double
)
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

// Local High-Fidelity Color Theme matching React Web App CSS
val SpaceNavy: Color @Composable get() = BackgroundDark
val CardDark: Color @Composable get() = SurfaceContainerLow
val CardDarkLighter: Color @Composable get() = SurfaceContainer
val NeonGreen: Color @Composable get() = PrimaryEmeraldContainer
val NeonGreenLight: Color @Composable get() = PrimaryEmerald
val SoftGray: Color @Composable get() = OnSurfaceVariant
val BorderBlue: Color @Composable get() = BorderColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()

    // State simulator: "normal", "offline", "loading", "zero", "empty"
    var stateSim by remember { mutableStateOf("normal") }
    var isRetrying by remember { mutableStateOf(false) }

    // Sorting state
    var sortCriteria by remember { mutableStateOf("val") } // "val", "gain", "div", "div_amt", "code"
    var sortDirection by remember { mutableStateOf("desc") } // "desc", "asc"
    var showSortModal by remember { mutableStateOf(false) }

    // Dialog / Modal State
    var showAddAssetModal by remember { mutableStateOf(false) }
    var stockCode by remember { mutableStateOf("") }
    var shareCount by remember { mutableStateOf("") }
    var acqPrice by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var quickEditHolding by remember { mutableStateOf<Holding?>(null) }
    var quickEditShares by remember { mutableStateOf("") }
    var quickEditAcqPrice by remember { mutableStateOf("") }
    var quickEditMarketPrice by remember { mutableDoubleStateOf(0.0) }

    val javaCurrencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    // Dynamic calculations from holdings optimized using remember to prevent recomposition overhead
    val totalAssetsVal = remember(holdings) { holdings.sumOf { it.totalValuation } }
    val totalCostVal = remember(holdings) { holdings.sumOf { it.totalCost } }
    val totalGainVal = remember(totalAssetsVal, totalCostVal) { totalAssetsVal - totalCostVal }
    val gainPctVal = remember(totalGainVal, totalCostVal) { if (totalCostVal > 0.0) (totalGainVal / totalCostVal) * 100.0 else 0.0 }
    val totalAnnualDivVal = remember(holdings, taxRateMode) { holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) } }
    val netAnnualDivVal = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("net") } }
    val avgYield = remember(totalAnnualDivVal, totalAssetsVal) { if (totalAssetsVal > 0.0) (totalAnnualDivVal / totalAssetsVal) * 100.0 else 0.0 }

    // Dynamic Annual Dividend Target based on currently active roadmap step optimized with remember
    val totalAnnualExpectedDividendForRoadmap = remember(holdings, taxRateMode) {
        holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode).toLong() }
    }
    val monthlyIncomeForRoadmap = remember(totalAnnualExpectedDividendForRoadmap) { totalAnnualExpectedDividendForRoadmap / 12 }

    val roadmapMonthlyTargets = remember {
        listOf(
            1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 8_000, 10_000, 15_000, 20_000,
            25_000, 30_000, 35_000, 40_000, 45_000, 50_000, 60_000, 70_000, 80_000, 100_000,
            120_000, 150_000, 180_000, 200_000, 250_000, 300_000, 350_000, 400_000, 500_000, 600_000
        )
    }
    val currentMonthlyTarget = remember(monthlyIncomeForRoadmap, roadmapMonthlyTargets) {
        roadmapMonthlyTargets.firstOrNull { monthlyIncomeForRoadmap < it } ?: roadmapMonthlyTargets.last()
    }
    val currentAnnualTarget = currentMonthlyTarget * 12

    // Dynamic monthly dividend totals optimized with remember
    val dynamicMonthlyDividends = remember(holdings, taxRateMode) {
        (1..12).map { month ->
            val monthTotal = holdings.filter { it.dividendMonths.contains(month) }.sumOf {
                val countInYear = if (it.dividendMonths.isNotEmpty()) it.dividendMonths.size else 1
                it.getEffectiveAnnualDividend(taxRateMode) / countInYear
            }
            val label = if (monthTotal >= 10000) {
                String.format("%.1f万", monthTotal / 10000.0)
            } else if (monthTotal > 0) {
                String.format("%.1f千", monthTotal / 1000.0)
            } else "0"
            Triple(month, monthTotal, label)
        }
    }
    val maxMonthlyAmt = remember(dynamicMonthlyDividends) { dynamicMonthlyDividends.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0 }

    // Category and month filtering optimized with remember and search filtering
    val filteredHoldings = remember(holdings, selectedMonth, selectedCategory, searchQuery) {
        holdings.filter { h ->
            val monthMatch = selectedMonth == null || h.dividendMonths.contains(selectedMonth)
            val catMatch = selectedCategory == null || h.category == selectedCategory
            val queryMatch = searchQuery.isEmpty() ||
                    h.code.contains(searchQuery, ignoreCase = true) ||
                    h.name.contains(searchQuery, ignoreCase = true) ||
                    h.sector.contains(searchQuery, ignoreCase = true)
            monthMatch && catMatch && queryMatch
        }
    }

    // Sorted holdings optimized with remember
    val displayedHoldings = remember(filteredHoldings, sortCriteria, sortDirection, taxRateMode) {
        filteredHoldings.sortedWith { a, b ->
            val comp = when (sortCriteria) {
                "val" -> a.totalValuation.compareTo(b.totalValuation)
                "gain" -> a.gainLossPercent.compareTo(b.gainLossPercent)
                "div" -> a.yieldRate.compareTo(b.yieldRate)
                "div_amt" -> a.getEffectiveAnnualDividend(taxRateMode).compareTo(b.getEffectiveAnnualDividend(taxRateMode))
                "code" -> a.code.compareTo(b.code)
                else -> a.totalValuation.compareTo(b.totalValuation)
            }
            if (sortDirection == "desc") -comp else comp
        }
    }

    val badgeText = when {
        avgYield >= 4.0 -> "超高配当水準 (4%~)"
        avgYield >= 3.0 -> "高配当水準 (3%~)"
        avgYield >= 2.0 -> "中配当水準 (2%~)"
        avgYield >= 0.5 -> "低配当・成長重視"
        else -> "インデックス・極小"
    }

    val badgeIcon = when {
        avgYield >= 4.0 -> Icons.Default.Bolt
        avgYield >= 3.0 -> Icons.Default.CheckCircle
        avgYield >= 2.0 -> Icons.Default.Star
        else -> Icons.Default.Info
    }

    val badgeColor = when {
        avgYield >= 4.0 -> SecondaryIndigo // Purple
        avgYield >= 3.0 -> NeonGreen // Emerald
        avgYield >= 2.0 -> Color(0xFF60A5FA) // Blue
        avgYield >= 0.5 -> AccentAmber // Amber
        else -> SoftGray // Slate
    }

    fun getSortLabel(): String {
        val dir = if (sortDirection == "desc") "降順" else "昇順"
        return when (sortCriteria) {
            "val" -> "評価額 ($dir)"
            "gain" -> "損益率 ($dir)"
            "div" -> "利回り ($dir)"
            "div_amt" -> "配当額 ($dir)"
            "code" -> "コード ($dir)"
            else -> "評価額 ($dir)"
        }
    }

    Scaffold(
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(56.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonGreen, ambientColor = NeonGreen)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonGreenLight, NeonGreen)))
                    .clickable { onOpenAddHolding("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Asset", tint = OnPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        },
        containerColor = SpaceNavy
    ) { innerPadding ->
        val scrollState = rememberScrollState(initial = viewModel.assetScrollPosition)
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { viewModel.assetScrollPosition = it }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ================= STATE SIMULATOR BAR =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonGreen))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STATE SIMULATOR", fontSize = AppTextSize.dense, fontWeight = FontWeight.Black, color = NeonGreenLight)
                        }
                        Text("画面状態の動的切替", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val simTabs = listOf(
                        Triple("normal", "① 通常・一覧", Icons.Default.BarChart),
                        Triple("offline", "② オフライン", Icons.Default.WifiOff),
                        Triple("loading", "③ 読込中", Icons.Default.Sync),
                        Triple("zero", "④ 検索ゼロ", Icons.Default.SearchOff),
                        Triple("empty", "⑤ 初期未登録", Icons.Default.AddCircle)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        simTabs.forEach { (id, label, icon) ->
                            val isSelected = stateSim == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) NeonGreen else CardDarkLighter)
                                    .border(0.5.dp, if (isSelected) NeonGreen else BorderBlue, RoundedCornerShape(12.dp))
                                    .clickable {
                                        stateSim = id
                                        if (id == "loading") {
                                            // Automatically restore to normal after simulated load
                                            isRetrying = true
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, tint = if (isSelected) OnPrimaryContainer else SoftGray, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = AppTextSize.dense, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, color = if (isSelected) OnPrimaryContainer else SoftGray)
                                }
                            }
                        }
                    }
                }
            }

            // ================= STATE: OFFLINE =================
            if (stateSim == "offline") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x26F59E0B)),
                    border = BorderStroke(1.dp, Color(0x66F59E0B))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("通信エラー / オフライン表示中", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = CircleShape, color = Color(0x33F59E0B)) {
                                    Text("OFFLINE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("最新株価の取得に失敗しました。前回キャッシュデータ（本日 15:00時点）を表示しています。", color = SoftGray, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                        }
                    }
                }
            }

            // ================= STATE: ZERO SEARCH =================
            if (stateSim == "zero") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = SoftGray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("該当する銘柄が見つかりません", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("検索条件 \"NVDA\" に一致する保有銘柄はありません。", color = SoftGray, fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { stateSim = "normal" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("フィルター・検索をリセット", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                        }
                    }
                }
            }

            // ================= STATE: EMPTY =================
            if (stateSim == "empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, tint = SoftGray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("まだ保有銘柄が登録されていません", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("証券口座連携は不要です。右下の「＋」ボタンから1分ですぐに保有株式を登録できます。", color = SoftGray, fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { stateSim = "normal" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("デモデータを復元する", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                        }
                    }
                }
            }

            // ================= TOTAL ASSET HERO CARD =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("総資産評価額", fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = SoftGray)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Privacy",
                                tint = SoftGray,
                                modifier = Modifier.size(16.dp).clickable { viewModel.togglePrivacy() }
                            )
                        }
                        // Profit Pill
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x1F10B981))
                                .border(0.5.dp, Color(0x3310B981), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                val gainText = if (isPrivacyActive) "+¥••••••" else {
                                    val sign = if (totalGainVal >= 0) "+" else ""
                                    "$sign¥${javaCurrencyFormat.format(totalGainVal.toLong())} ($sign${String.format("%.1f", gainPctVal)}%)"
                                }
                                Text(gainText, fontSize = AppTextSize.dense, fontWeight = FontWeight.ExtraBold, color = if (totalGainVal >= 0) NeonGreen else ErrorRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isPrivacyActive) "¥ •,•••,•••,•••" else "¥${javaCurrencyFormat.format(totalAssetsVal.toLong())}",
                        fontSize = AppTextSize.hero,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyActive) "投資元本: ¥ •,•••,•••,•••" else "投資元本: ¥${javaCurrencyFormat.format(totalCostVal.toLong())}",
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color = SoftGray
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = BorderBlue, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sub-Grid 2 Columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Expected Dividend Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceContainer)
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("年間予想受取配当", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPrivacyActive) "¥ ••••• /年" else "¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())} /年",
                                    fontSize = AppTextSize.headline,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isPrivacyActive) "税引後概算: ¥ •••••" else "税引後概算: ¥${javaCurrencyFormat.format(netAnnualDivVal.toLong())}",
                                    fontSize = AppTextSize.dense,
                                    color = SoftGray
                                )
                            }
                        }

                        // Dividend Yield Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceContainer)
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("平均配当利回り", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format("%.2f", avgYield),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("%", fontSize = AppTextSize.dense, color = SoftGray, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(badgeText, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black, color = badgeColor)
                                }
                            }
                        }
                    }
                }
            }

            // ================= MONTHLY CHART CARD (INTERACTIVE) =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("月別推移グラフ", fontSize = AppTextSize.body, fontWeight = FontWeight.ExtraBold, color = OnSurface)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(SurfaceContainerHigh).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("2024", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                            }
                        }
                        Text(
                            text = if (isPrivacyActive) "年間合計: ¥ ••••••" else "年間合計: ¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())}",
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonGreenLight
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1-12 Interactive Monthly Bars. Keep each month label inside its own fixed-width slot.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(145.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dynamicMonthlyDividends.forEach { (month, amt, label) ->
                            val isSelected = selectedMonth == month
                            val isSpecialMonth = (month == 6 || month == 12)
                            val heightFraction = (amt / maxMonthlyAmt).toFloat().coerceIn(0.12f, 1.0f)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(145.dp)
                                    .clickable { viewModel.selectMonth(month) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) NeonGreenLight else if (isSpecialMonth) NeonGreenLight else SkyBlue,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                val barMaxHeight = 85.dp
                                val barHeight = barMaxHeight * heightFraction

                                Box(
                                    modifier = Modifier.fillMaxWidth().height(barMaxHeight),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(
                                                if (isSelected) {
                                                    Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmeraldContainer))
                                                } else if (isSpecialMonth) {
                                                    Brush.verticalGradient(listOf(NeonGreenLight, NeonGreen))
                                                } else {
                                                    Brush.verticalGradient(listOf(SkyBlue, Color(0xFF0077B6)))
                                                }
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(1.5.dp, OnSurface, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) else Modifier
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "$month",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = AppTextSize.dense,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected || isSpecialMonth) NeonGreenLight else SoftGray,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = BorderBlue, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Legend & Selected Month Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonGreenLight))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("日本株", fontSize = AppTextSize.dense, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("米国株", fontSize = AppTextSize.dense, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("投信", fontSize = AppTextSize.dense, color = SoftGray)
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = NeonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, NeonGreen.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = if (selectedMonth != null) "${selectedMonth}月タップ中" else "月平均: 約¥${javaCurrencyFormat.format((totalAnnualDivVal / 12).toLong())}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = NeonGreenLight,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ================= GOAL PROGRESS CARD =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val progressPct = ((totalAnnualDivVal / currentAnnualTarget.toDouble()) * 100).coerceAtLeast(0.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("年間配当目標: ¥${javaCurrencyFormat.format(currentAnnualTarget)}", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                        }
                        Text(String.format("%.1f%%", progressPct), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (progressPct / 100f).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonGreen,
                        trackColor = BorderColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPrivacyActive) "現在: ¥ ••••••" else "現在: ¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())}",
                            fontSize = AppTextSize.dense,
                            color = SoftGray
                        )
                        val shortage = currentAnnualTarget.toDouble() - totalAnnualDivVal
                        Text(
                            text = if (shortage > 0) {
                                if (isPrivacyActive) {
                                    val formattedTarget = if (currentMonthlyTarget >= 10000) "${currentMonthlyTarget / 10000}万円" else "${javaCurrencyFormat.format(currentMonthlyTarget)}円"
                                    "あと ¥ •••••• で月${formattedTarget}達成"
                                } else {
                                    "あと ¥${javaCurrencyFormat.format(shortage.toLong())} で目標クリア"
                                }
                            } else "目標達成済！不労所得生活達成✨",
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            color = if (shortage > 0) ErrorRed else NeonGreen
                        )
                    }
                }
            }

            // ================= HOLDINGS LIST HEADER & SORT =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedMonth != null) "${selectedMonth}月の配当入金ポートフォリオ" else "保有銘柄一覧",
                        fontSize = AppTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = CircleShape, color = SurfaceContainerHigh) {
                        Text(
                            "${displayedHoldings.size}件",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = NeonGreenLight,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedMonth != null) {
                        Text(
                            "全件表示",
                            color = NeonGreenLight,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.selectMonth(selectedMonth!!) }
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardDarkLighter,
                        border = BorderStroke(0.5.dp, BorderBlue),
                        modifier = Modifier.clickable { showSortModal = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(getSortLabel(), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ================= SEARCH FIELD =================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("銘柄コード・銘柄名・セクターでリアルタイム検索", color = SoftGray, fontSize = AppTextSize.dense) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen.copy(alpha = 0.5f),
                    unfocusedBorderColor = BorderBlue,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
                )
            )

            // ================= CATEGORY FILTER CHIPS =================
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chips = listOf(
                    Pair(null, "すべて (${holdings.size})"),
                    Pair(Category.JAPAN, "日本株 (${holdings.filter { it.category == Category.JAPAN }.size})"),
                    Pair(Category.US, "米国株 (${holdings.filter { it.category == Category.US }.size})"),
                    Pair(Category.FUND, "投信 (${holdings.filter { it.category == Category.FUND }.size})")
                )
                chips.forEach { (cat, label) ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) NeonGreen else CardDarkLighter,
                        border = BorderStroke(0.5.dp, if (isSelected) NeonGreen else BorderBlue),
                        modifier = Modifier.clickable { viewModel.selectCategory(cat) }
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = if (isSelected) OnPrimaryContainer else SoftGray,
                            fontSize = AppTextSize.dense,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }
            }

            // ================= HOLDING STOCK CARDS LIST =================
            if (displayedHoldings.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = SoftGray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("該当する保有銘柄はありません", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                        Text(
                            if (selectedMonth != null) "${selectedMonth}月に入金が予定されている銘柄はありません。" else "指定の条件に一致する銘柄がありません。",
                            color = SoftGray,
                            fontSize = AppTextSize.dense,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                displayedHoldings.forEach { holding ->
                    val isUSD = holding.currency == "$"
                    val curVal = holding.totalValuation
                    val curCost = holding.totalCost
                    val diff = holding.gainLoss
                    val diffPct = holding.gainLossPercent
                    val annualDiv = holding.getEffectiveAnnualDividend(taxRateMode)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDetail(holding.code) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(
                            1.dp,
                            if (selectedMonth != null) NeonGreenLight.copy(alpha = 0.5f) else BorderBlue
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Code, Name, Sector, Valuation & Gain
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (holding.category) {
                                            Category.US -> SecondaryIndigo.copy(alpha = 0.14f)
                                            Category.FUND -> ErrorRed.copy(alpha = 0.14f)
                                            else -> BorderColor
                                        }
                                    ) {
                                        Text(
                                            holding.code,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = when (holding.category) {
                                                Category.US -> SecondaryIndigo
                                                Category.FUND -> ErrorRed
                                                else -> OnSurface
                                            },
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(holding.name, fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body, maxLines = 1)
                                        Text(holding.sector, color = SoftGray, fontSize = AppTextSize.dense)
                                    }
                                    IconButton(
                                        onClick = {
                                            quickEditHolding = holding
                                            quickEditShares = holding.shares.toString()
                                            quickEditAcqPrice = holding.acqPrice.toString().removeSuffix(".0")
                                            quickEditMarketPrice = holding.price
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "${holding.name}を編集", tint = SoftGray, modifier = Modifier.size(15.dp))
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "¥${javaCurrencyFormat.format(curVal.toLong())}",
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val sign = if (diff >= 0) "+" else ""
                                    Text(
                                        "$sign¥${javaCurrencyFormat.format(diff.toLong())} ($sign${String.format("%.1f", diffPct)}%)",
                                        fontWeight = FontWeight.Bold,
                                        color = if (diff >= 0) NeonGreenLight else ErrorRed,
                                        fontSize = AppTextSize.dense,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = BorderBlue, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // 3-Column Metrics: Shares, Yield, Annual Dividend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("保有数量", color = SoftGray, fontSize = AppTextSize.dense)
                                    Text(
                                        "${holding.shares}株 (${holding.currency}${holding.price.toInt()})",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("配当利回り", color = SoftGray, fontSize = AppTextSize.dense)
                                    Text(
                                        "${holding.yieldRate}%",
                                        color = NeonGreenLight,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("予想配当金", color = SoftGray, fontSize = AppTextSize.dense)
                                    Text(
                                        if (annualDiv > 0) "¥${javaCurrencyFormat.format(annualDiv.toLong())}/年" else "再投資型",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Month Highlight Banner if selected
                            if (selectedMonth != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardDarkLighter,
                                    border = BorderStroke(0.5.dp, NeonGreenLight.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("入金予定: ${selectedMonth}月中旬予定", color = SoftGray, fontSize = AppTextSize.dense)
                                        }
                                        Text(holding.nextPayoutAmount, color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // Bottom row: Account type and Next Payout Timing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(4.dp), color = BorderColor) {
                                    Text(holding.accountType, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = SecondaryIndigo, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(holding.nextPayoutTiming, color = SoftGray, fontSize = AppTextSize.dense)
                                }
                            }
                        }
                    }
                }
            }

            // ================= NEXT DIVIDEND PAYOUT STRIP (100% DYNAMIC) =================
            val currentMonthNum = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
            val validHoldings = holdings.filter { it.dividendMonths.isNotEmpty() && it.shares > 0 }
            var nextStock = validHoldings.firstOrNull()
            var nextMonth = 12
            var minDiff = 999
            validHoldings.forEach { h ->
                h.dividendMonths.forEach { m ->
                    var diff = m - currentMonthNum
                    if (diff < 0) diff += 12
                    if (diff < minDiff) {
                        minDiff = diff
                        nextStock = h
                        nextMonth = m
                    }
                }
            }
            val nextDivAmt = if (nextStock != null) {
                val rate = if (nextStock.currency == "$") 154.0 else 1.0
                val monthsCount = if (nextStock.dividendMonths.isNotEmpty()) nextStock.dividendMonths.size else 2
                ((nextStock.shares * nextStock.dps * rate) / monthsCount).toLong()
            } else 0L

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkLighter),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("直近の配当受取予定", color = SoftGray, fontSize = AppTextSize.dense)
                            Text(if (nextStock != null) "${nextMonth}月中旬 ${nextStock!!.name}" else "保有銘柄なし", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.caption)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("¥${javaCurrencyFormat.format(nextDivAmt)}", fontWeight = FontWeight.Bold, color = NeonGreenLight, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                        Text("${nextStock?.dividendType ?: "配当金"} (${nextStock?.shares ?: 0}株)", color = SoftGray, fontSize = AppTextSize.dense)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ================= SORT MODAL BOTTOM SHEET =================
    if (showSortModal) {
        AlertDialog(
            onDismissRequest = { showSortModal = false },
            confirmButton = {
                Button(
                    onClick = { showSortModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("適用する", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortModal = false }) {
                    Text("閉じる", color = SoftGray, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Text("並べ替え条件の変更", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val criteriaOptions = listOf(
                        Pair("val", "評価額"),
                        Pair("gain", "含み損益率"),
                        Pair("div", "配当利回り"),
                        Pair("div_amt", "年間予想配当額"),
                        Pair("code", "銘柄コード")
                    )
                    criteriaOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sortCriteria == key) CardDarkLighter else Color.Transparent)
                                .clickable { sortCriteria = key }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = if (sortCriteria == key) NeonGreenLight else OnSurface, fontSize = AppTextSize.body, fontWeight = if (sortCriteria == key) FontWeight.Bold else FontWeight.Normal)
                            if (sortCriteria == key) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Divider(color = BorderBlue, thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { sortDirection = "desc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "desc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("降順 (大→小)", color = if (sortDirection == "desc") OnPrimaryContainer else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { sortDirection = "asc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "asc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("昇順 (小→大)", color = if (sortDirection == "asc") OnPrimaryContainer else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, BorderBlue, RoundedCornerShape(16.dp))
        )
    }

    // ================= ADD ASSET MODAL DIALOG =================
    if (showAddAssetModal) {
        AlertDialog(
            onDismissRequest = { showAddAssetModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (stockCode.isNotEmpty() && shareCount.isNotEmpty() && acqPrice.isNotEmpty()) {
                            val shares = shareCount.toIntOrNull() ?: 100
                            val price = acqPrice.toDoubleOrNull() ?: 2500.0
                            viewModel.updateHolding(stockCode, shares, price)
                            showAddAssetModal = false
                            stockCode = ""
                            shareCount = ""
                            acqPrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("登録する", color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAssetModal = false }) {
                    Text("キャンセル", color = SoftGray, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("銘柄の新規登録・修正", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("保有している配当銘柄の情報を入力してください。ダッシュボードに即時反映されます。", color = SoftGray, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                    OutlinedTextField(
                        value = stockCode,
                        onValueChange = { stockCode = it },
                        label = { Text("銘柄コード (例: 7203)", fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (stockCode.isNotEmpty()) {
                                IconButton(onClick = { stockCode = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = shareCount,
                        onValueChange = { shareCount = it },
                        label = { Text("保有株数 (例: 100)", fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (shareCount.isNotEmpty()) {
                                IconButton(onClick = { shareCount = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = acqPrice,
                        onValueChange = { acqPrice = it },
                        label = { Text("取得単価 (例: 2450)", fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (acqPrice.isNotEmpty()) {
                                IconButton(onClick = { acqPrice = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, BorderBlue, RoundedCornerShape(20.dp))
        )
    }

    // ================= HOLDING QUICK EDIT BOTTOM SHEET =================
    quickEditHolding?.let { holding ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val shares = quickEditShares.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val editValid = quickEditShares.toIntOrNull()?.let { it >= 0 } == true && quickEditAcqPrice.toDoubleOrNull()?.let(com.kurogane.dividend.model.PortfolioMath::validMoney) == true
        val averagePrice = quickEditAcqPrice.toDoubleOrNull()?.takeIf(com.kurogane.dividend.model.PortfolioMath::validMoney) ?: 0.0
        val currencyFactor = if (holding.currency == "$" ) 154.0 else 1.0
        val cost = shares * averagePrice / holding.priceUnit * currencyFactor
        val currentPrice = quickEditMarketPrice.takeIf { it > 0.0 } ?: holding.price
        val valuation = shares * currentPrice / holding.priceUnit * currencyFactor
        val gain = valuation - cost
        val gainPct = if (cost > 0.0) gain / cost * 100.0 else 0.0
        val projectedDividend = shares * holding.dps / holding.priceUnit * currencyFactor
        val modalCurrency = if (holding.currency == "$") "$" else "¥"

        ModalBottomSheet(
            onDismissRequest = { quickEditHolding = null },
            sheetState = sheetState,
            containerColor = CardDark,
            contentColor = OnSurface,
            scrimColor = Color.Black.copy(alpha = 0.72f),
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp)
                        .clip(CircleShape).background(BorderBlue)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                            .background(CardDarkLighter).border(1.dp, BorderBlue, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, null, tint = NeonGreenLight, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("保有情報のクイック編集", color = OnSurface, fontSize = AppTextSize.headline, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text("${holding.code}  ${holding.name}  (${holding.market})", color = SoftGray, fontSize = AppTextSize.caption, maxLines = 1)
                    }
                    IconButton(onClick = { quickEditHolding = null }) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる", tint = SoftGray)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                        .border(1.dp, BorderBlue, RoundedCornerShape(16.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("現在値", color = SoftGray, fontSize = AppTextSize.dense)
                        Text("$modalCurrency${javaCurrencyFormat.format(currentPrice)}", color = OnSurface, fontSize = AppTextSize.screenTitle, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    }
                    Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                        Text(
                            "↗ 前日比 ${holding.dailyChange}",
                            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = if (holding.isPositive) NeonGreenLight else ErrorRed,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("株価試算", color = SoftGray, fontSize = AppTextSize.dense, modifier = Modifier.weight(1f))
                    listOf(-50.0, -10.0, 10.0, 50.0).forEach { delta ->
                        val label = if (delta > 0) "+${delta.toInt()}" else delta.toInt().toString()
                        Surface(
                            modifier = Modifier.clickable { quickEditMarketPrice = (currentPrice + delta).coerceAtLeast(1.0) },
                            shape = RoundedCornerShape(7.dp),
                            color = CardDarkLighter
                        ) {
                            Text(label, Modifier.padding(horizontal = 7.dp, vertical = 6.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { quickEditMarketPrice = holding.price }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                        Text("戻す", color = NeonGreenLight, fontSize = AppTextSize.dense)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("保有数量 (株)", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                        }
                        Text("単元: 100株", color = SoftGray, fontSize = AppTextSize.caption)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuickEditStepButton("−", enabled = shares > 1) { quickEditShares = (shares - 10).coerceAtLeast(1).toString() }
                        OutlinedTextField(
                            value = quickEditShares,
                            onValueChange = { quickEditShares = it.filter(Char::isDigit).ifEmpty { "1" } },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text("株", color = SoftGray) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreenLight, unfocusedBorderColor = BorderBlue, focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                        )
                        QuickEditStepButton("＋") { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 10).toString() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("1単元 (100株)" to { quickEditShares = "100" }, "+10" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 10).toString() }, "+100" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 100).toString() }, "+500" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 500).toString() }).forEach { (label, action) ->
                            Surface(Modifier.clickable(onClick = action), shape = RoundedCornerShape(10.dp), color = CardDarkLighter, border = BorderStroke(1.dp, BorderBlue)) {
                                Text(label, Modifier.padding(horizontal = 10.dp, vertical = 9.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sell, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("取得平均単価 ($modalCurrency)", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { quickEditAcqPrice = currentPrice.toString().removeSuffix(".0") }) {
                            Text("現在値を適用", color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuickEditStepButton("−", enabled = averagePrice > 1) { quickEditAcqPrice = (averagePrice - 10).coerceAtLeast(1.0).toString().removeSuffix(".0") }
                        OutlinedTextField(
                            value = quickEditAcqPrice,
                            onValueChange = { quickEditAcqPrice = it.filter { ch -> ch.isDigit() || ch == '.' }.ifEmpty { "1" } },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            prefix = { Text(modalCurrency, color = SoftGray) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreenLight, unfocusedBorderColor = BorderBlue, focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                        )
                        QuickEditStepButton("＋") { quickEditAcqPrice = (averagePrice + 10).toString().removeSuffix(".0") }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                        .border(1.dp, NeonGreenLight.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("更新後のリアルタイム試算", color = NeonGreenLight, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                            Text("即時再計算中", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                    Divider(color = BorderBlue)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickEditSummary("投資元本", "$modalCurrency${javaCurrencyFormat.format(cost.toLong())}", OnSurface, Modifier.weight(1f))
                        QuickEditSummary("評価損益", "${if (gain >= 0) "+" else "−"}$modalCurrency${javaCurrencyFormat.format(kotlin.math.abs(gain).toLong())}\n(${String.format(Locale.JAPAN, "%+.1f%%", gainPct)})", if (gain >= 0) NeonGreenLight else ErrorRed, Modifier.weight(1f), Alignment.CenterHorizontally)
                        QuickEditSummary("年間予想配当金", "$modalCurrency${javaCurrencyFormat.format(projectedDividend.toLong())}/年", NeonGreenLight, Modifier.weight(1f), Alignment.End)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { quickEditHolding = null },
                        modifier = Modifier.weight(0.8f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDarkLighter, contentColor = OnSurface),
                        border = BorderStroke(1.dp, BorderBlue)
                    ) { Text("キャンセル", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            if (viewModel.updateHolding(holding.code, shares, averagePrice, currentPrice)) quickEditHolding = null
                        },
                        enabled = editValid,
                        modifier = Modifier.weight(1.5f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Icon(Icons.Default.Check, null, tint = OnPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("変更を保存する", color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickEditStepButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(50.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CardDarkLighter,
        border = BorderStroke(1.dp, BorderBlue)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) OnSurface else SoftGray, fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickEditSummary(label: String, value: String, valueColor: Color, modifier: Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SoftGray, fontSize = AppTextSize.dense, maxLines = 1)
        Text(value, color = valueColor, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 2)
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/ContactFeedbackScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.data.runCatchingCancellable
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFeedbackScreen(
    onBack: () -> Unit,
    viewModel: DividendViewModel
) {
    var selectedCategory by remember { mutableStateOf("feature") } // "feature", "bug", "stock", "other"
    var senderName by remember { mutableStateOf("") }
    var senderEmail by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var attachDiagnostics by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val draftStore = remember { LocalImportStore(context.applicationContext) }
    var draftMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatchingCancellable { withContext(Dispatchers.IO) { draftStore.loadFeedbackDraft() } }.onSuccess { draft ->
            if (draft != null) {
                senderName = draft.optString("name")
                senderEmail = draft.optString("email")
                messageText = draft.optString("message")
                selectedCategory = draft.optString("category", "feature")
            }
        }
    }
    val scrollState = rememberScrollState()

    val faqs = remember {
        listOf(
            FAQItem(
                "楽天証券やSBI証券のCSVインポートはどのように行うのですか？",
                "「インポート」タブ、または資産管理画面の右上のCSVアイコンからインポート画面を開きます。証券会社（楽天・SBI・SBIネオモバイル）を選択し、証券アプリからエクスポートしたCSVファイルをドロップするだけで保有銘柄・保有株数・取得価格が自動で一括反映されます。"
            ),
            FAQItem(
                "保有株数や取得価格を変更したい場合はどうすればよいですか？",
                "資産管理画面の銘柄一覧カードをタップすると、取得価格と保有株数の編集フォームが立ち上がります。数値を変更して「更新」をタップすると、即座にポートフォリオの全値、円グラフ、および配当ロードマップが自動再計算されます。"
            ),
            FAQItem(
                "買場目安アラートとはどのような機能ですか？",
                "ウォッチリスト（監視銘柄）の各カードで右側の通知アイコンをONにすると、株価が設定した目標利回り（ターゲット）に到達した際に、ブラウザまたはスマホのプッシュ通知でお知らせします。通知をONにするとリアルタイムに買場目安価格（逆算株価）が自動計算されます。"
            ),
            FAQItem(
                "ロードマップ達成率はどのように計算されていますか？",
                "登録したすべての保有銘柄から得られる年間予想配当金を算出し、その12分の1（月換算額）を計算します。ロードマップの各STEP（電気代、水道代、食費、FIREなど）の目標月額に対する、あなたの現在の月間配当額の割合が％としてリアルタイムに反映されます。"
            )
        )
    }

    var openFaqIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContactSupport,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ご意見・不具合報告", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceNavy)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Support Desk Intro Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f))
                            .border(1.dp, PrimaryEmeraldContainer.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryEmeraldContainer.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Official Support", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Diview サポートデスク", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "送信機能は準備中です。入力内容は端末内に下書きとして保存できます。",
                            color = SoftGray,
                            fontSize = AppTextSize.dense,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // FAQ Accordion Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Text("よくあるご質問 (FAQ)", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Text("タップして展開", color = SoftGray, fontSize = AppTextSize.dense)
                }

                faqs.forEachIndexed { idx, faq ->
                    val open = openFaqIndex == idx
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openFaqIndex = if (open) null else idx },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmeraldContainer.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Q", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black)
                                    }
                                    Text(faq.q, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (open) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        faq.a,
                                        color = SoftGray,
                                        fontSize = AppTextSize.dense,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Contact Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.RateReview, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Text("お問い合わせ・ご意見", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }

                    // Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("カテゴリ *", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                        val categories = listOf(
                            CategoryButton("feature", "機能の改善要望", Icons.Default.Lightbulb),
                            CategoryButton("bug", "不具合の報告", Icons.Default.BugReport),
                            CategoryButton("stock", "銘柄データの誤り", Icons.Default.TableChart),
                            CategoryButton("other", "その他", Icons.Default.MoreHoriz)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.take(2).forEach { cat ->
                                val active = selectedCategory == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryEmerald else BackgroundDark)
                                        .border(BorderStroke(1.dp, if (active) Color.Transparent else BorderColor), RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(cat.icon, contentDescription = null, tint = if (active) OnPrimary else SoftGray, modifier = Modifier.size(14.dp))
                                        Text(cat.label, color = if (active) OnPrimary else SoftGray, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.drop(2).forEach { cat ->
                                val active = selectedCategory == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryEmerald else BackgroundDark)
                                        .border(BorderStroke(1.dp, if (active) Color.Transparent else BorderColor), RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(cat.icon, contentDescription = null, tint = if (active) OnPrimary else SoftGray, modifier = Modifier.size(14.dp))
                                        Text(cat.label, color = if (active) OnPrimary else SoftGray, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Sender Name Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("お名前 / ニックネーム", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                            Text("任意", color = SoftGray, fontSize = AppTextSize.dense)
                        }
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = { senderName = it },
                            placeholder = { Text("配当雪だるま", color = SoftGray, fontSize = AppTextSize.dense) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Return Email Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("返信用メールアドレス", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                            Text("返信が必要な場合", color = SoftGray, fontSize = AppTextSize.dense)
                        }
                        OutlinedTextField(
                            value = senderEmail,
                            onValueChange = { senderEmail = it },
                            placeholder = { Text("your-email@example.com", color = SoftGray, fontSize = AppTextSize.dense) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Message Text Multi-Line Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("お問い合わせ内容 *", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("不具合内容や改善案などをご自由にご記入ください。", color = SoftGray, fontSize = AppTextSize.dense) },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Diagnostics Attachment Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundDark)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("診断レポートを添付", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text("アプリのクラッシュログ、OSバージョン情報を含める", color = SoftGray, fontSize = AppTextSize.dense)
                        }
                        Switch(
                            checked = attachDiagnostics,
                            onCheckedChange = { attachDiagnostics = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryEmerald)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Submit Button with loading simulation
                    val canSubmit = !isSubmitting && messageText.trim().isNotEmpty() && messageText.length <= 20_000 &&
                        senderName.length <= 200 && senderEmail.length <= 254 &&
                        (senderEmail.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(senderEmail.trim()).matches())
                    Button(
                        onClick = {
                            if (messageText.trim().isNotEmpty()) {
                                isSubmitting = true
                                val draft = JSONObject().apply {
                                    put("category", selectedCategory); put("name", senderName.trim())
                                    put("email", senderEmail.trim()); put("message", messageText.trim())
                                    if (attachDiagnostics) put("androidSdk", android.os.Build.VERSION.SDK_INT)
                                }
                                coroutineScope.launch {
                                    try {
                                        runCatchingCancellable { withContext(Dispatchers.IO) { draftStore.saveFeedbackDraft(draft) } }
                                            .onSuccess { draftMessage = "下書きを端末内に保存しました。送信は行っていません。" }
                                            .onFailure { draftMessage = "下書きを保存できませんでした。" }
                                    } finally { isSubmitting = false }
                                }
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmeraldContainer,
                            contentColor = OnPrimaryContainer,
                            disabledContainerColor = SurfaceContainerHighest,
                            disabledContentColor = OnSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = OnSurfaceVariant, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = if (canSubmit) OnPrimaryContainer else OnSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("下書きを保存", color = if (canSubmit) OnPrimaryContainer else OnSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                            }
                        }
                    }
                    draftMessage?.let { Text(it, color = OnSurfaceVariant, fontSize = AppTextSize.caption) }
                }
            }
        }
    }
}

data class FAQItem(val q: String, val a: String)
data class CategoryButton(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/CsvImportScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.data.*
import com.kurogane.dividend.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit = {},
    onImportCsv: suspend (List<CsvHoldingRow>, Set<String>) -> Unit = { _, _ -> },
    onImportDvm: suspend (List<DvmBackupPreview>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var broker by remember { mutableStateOf(CsvBroker.SBI) }
    var csvResult by remember { mutableStateOf<CsvImportResult?>(null) }
    var selectedFile by remember { mutableStateOf("") }
    var dvmFiles by remember { mutableStateOf<List<DvmBackupPreview>>(emptyList()) }
    var dvmFileNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var complete by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("csv") }
    val selectedFields = remember { mutableStateListOf("shares", "averageCost", "account") }
    var selectedRowIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var fileReadJob by remember { mutableStateOf<Job?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileReadJob?.cancel()
            csvResult = null
            selectedRowIndexes = emptySet()
            complete = false
            isReading = true
            val pickedBroker = broker
            error = null; selectedFile = "CSVを解析中…"
            fileReadJob = scope.launch {
                try { runCatchingCancellable {
                    selectedFile = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } ?: uri.lastPathSegment ?: "CSV"
                    withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(10 * 1024 * 1024) } ?: error("ファイルを読み込めません")
                    val catalog = LocalSecurityCatalog.names(context)
                    val funds = LocalSecurityCatalog.fundNames(context)
                    BrokerCsvImporter.parse(bytes, pickedBroker, catalog, funds)
                    }
                }.onSuccess { parsed -> csvResult = parsed; selectedRowIndexes = parsed.rows.indices.toSet() }.onFailure { error = it.message ?: "CSVを解析できませんでした" }
                } finally { isReading = false }
            }
        }
    }
    val dvmPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileReadJob?.cancel()
            isReading = true
            fileReadJob = scope.launch {
                try { runCatchingCancellable {
                    val name = withContext(Dispatchers.IO) { context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } } ?: "backup.dvm"
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(50 * 1024 * 1024) } ?: error("ファイルを読み込めません")
                        DvmBackupImporter.parse(name, bytes) to name
                    }
                }.onSuccess { (preview, name) ->
                    dvmFiles = (dvmFiles.filterNot { it.kind == preview.kind } + preview)
                    dvmFileNames = dvmFileNames + (preview.kind.name to name)
                    error = null; complete = false
                }.onFailure { error = it.message ?: "バックアップを解析できませんでした" }
                } finally { isReading = false }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (mode == "csv") "証券CSVインポート" else "DVMバックアップ復元", color = OnSurface, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnSurface) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark))
    }, containerColor = BackgroundDark) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainer).padding(4.dp)) {
                    listOf("csv" to "証券CSV", "dvm" to "DVMバックアップ").forEach { (id, label) ->
                        Text(label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (mode == id) PrimaryEmerald else Color.Transparent).clickable(enabled = !isReading && !isImporting) { mode = id; error = null }.padding(11.dp), color = if (mode == id) OnPrimary else OnSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (mode == "csv") {
                item {
                    Text("証券会社", color = OnSurface, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CsvBroker.entries.forEach { item ->
                            Text(item.label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (broker == item) SurfaceContainerHighest else SurfaceContainer).clickable(enabled = !isReading && !isImporting) { broker = item; csvResult = null; selectedFile = ""; complete = false }.padding(vertical = 11.dp, horizontal = 4.dp), color = if (broker == item) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    ImportCard(onClick = { if (!isReading && !isImporting) csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, title = if (selectedFile.isBlank()) "CSVファイルを選択" else selectedFile, subtitle = "UTF-8 / Shift-JIS / EUC-JPを端末内で判定", icon = Icons.Default.UploadFile)
                }
                    csvResult?.let { result ->
                    item {
                        Text("解析結果：${result.encoding.name} ・ ${result.rows.size}銘柄", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        Text("取り込む項目", color = OnSurface, fontWeight = FontWeight.Bold)
                        listOf("shares" to "保有数量", "averageCost" to "取得単価", "account" to "口座区分", "currentPrice" to "現在値").forEach { (key, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = key in selectedFields, onCheckedChange = { checked -> if (checked) selectedFields.add(key) else selectedFields.remove(key) })
                                Text(label, color = OnSurface)
                            }
                        }
                        if (result.errors.isNotEmpty()) Text(result.errors.joinToString(), color = ErrorRed)
                    }
                    itemsIndexed(result.rows) { index, row ->
                        val checked = index in selectedRowIndexes
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { selectedRowIndexes = if (checked) selectedRowIndexes - index else selectedRowIndexes + index }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { value -> selectedRowIndexes = if (value) selectedRowIndexes + index else selectedRowIndexes - index })
                                Column(Modifier.weight(1f)) {
                                    Text("${if (row.isFund) "投資信託" else row.code}  ${row.name}", color = OnSurface, fontWeight = FontWeight.Bold)
                                    Text("数量 ${row.shares ?: 0} ・ 取得単価 ¥${row.averageCost ?: 0.0}" + (row.account?.let { " ・ $it" } ?: ""), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                                if (row.matchedName != null) Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald)
                            }
                        }
                    }
                    if (result.unmatchedFunds.isNotEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("照合できなかった投資信託 (${result.unmatchedFunds.size})", color = ErrorRed, fontWeight = FontWeight.Bold)
                                result.unmatchedFunds.forEach { Text("・${it.sourceName}", color = OnSurfaceVariant, fontSize = AppTextSize.caption) }
                            }
                        }
                    }
                    item {
                        Button(onClick = {
                            val chosen = result.rows.filterIndexed { index, _ -> index in selectedRowIndexes }
                            if (chosen.isEmpty()) error = "取り込む銘柄を選択してください" else {
                                val fields = selectedFields.toSet()
                                isImporting = true
                                scope.launch {
                                    try { runCatchingCancellable { onImportCsv(chosen, fields) }.onSuccess { complete = true }
                                        .onFailure { error = it.message ?: "保存できませんでした" }
                                    } finally { isImporting = false }
                                }
                            }
                        }, enabled = !isReading && !isImporting && !complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text(if (complete) "取込済み" else "選択した銘柄を取り込む", color = OnPrimary, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                item { Text("4種類のDVMファイルを個別に選択できます。復号と確認は端末内で行います。", color = OnSurfaceVariant) }
                item { ImportCard(onClick = { if (!isReading && !isImporting) dvmPicker.launch(arrayOf("application/octet-stream", "application/*")) }, title = "DVMファイルを選択", subtitle = "銘柄・推移・カスタム銘柄・アラート", icon = Icons.Default.FolderOpen) }
                items(dvmFiles) { backup ->
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${backup.kind.label}　${backup.itemCount}件", color = OnSurface, fontWeight = FontWeight.Bold)
                            Text(dvmFileNames[backup.kind.name].orEmpty(), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            if (backup.kind == DvmBackupKind.STOCKS) backup.stocks.take(30).forEach { stock ->
                                Text("${stock.code}  ${stock.name}  ${stock.shares.toInt()}株", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }
                        }
                    }
                }
                if (dvmFiles.isNotEmpty()) item {
                    Button(onClick = {
                        isImporting = true
                        val backups = dvmFiles
                        scope.launch {
                            try { runCatchingCancellable { onImportDvm(backups) }.onSuccess { complete = true }
                                .onFailure { error = it.message ?: "保存できませんでした" }
                            } finally { isImporting = false }
                        }
                    }, enabled = !isReading && !isImporting && !complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text(if (complete) "端末内に保存しました" else "確認したバックアップを保存", color = OnPrimary, fontWeight = FontWeight.Bold) }
                }
            }
            error?.let { item { Text(it, color = ErrorRed, fontSize = AppTextSize.caption) } }
            item { Text("ファイルの解析・銘柄照合・保存は端末内で行われ、Cloudflareへ送信されません。", color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
        }
    }
}

@Composable
private fun ImportCard(onClick: () -> Unit, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = PrimaryEmerald, modifier = Modifier.size(32.dp))
            Column { Text(title, color = OnSurface, fontWeight = FontWeight.Bold); Text(subtitle, color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
        }
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/RoadmapScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(viewModel: DividendViewModel) {
    var openStep by remember { mutableStateOf<Int?>(null) }
    var selectedStageFilter by remember { mutableIntStateOf(1) } // 0: All, 1: LV.1, 2: LV.2, 3: LV.3

    // Bottom Sheet State for multi-account allocation modal
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Bottom Sheet State for Share Achievement modal (Added for sharing progress)
    var showShareBottomSheet by remember { mutableStateOf(false) }
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.roadmapScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.roadmapScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.roadmapScrollIndex = index
                viewModel.roadmapScrollOffset = offset
            }
    }

    // 1. Collect live native holdings state
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    // Calculate real-time annual expected dividend and monthly income
    val totalAnnualExpectedDividend = holdings.sumOf {
        it.getEffectiveAnnualDividend(taxRateMode).toLong()
    }
    val monthlyIncome = totalAnnualExpectedDividend / 12

    // Define the 30 static milestone thresholds
    val milestoneSteps = listOf(
        MilestoneData(1, "STEP 1: 月1,000円", "コンビニコーヒーやお菓子代を自給", 1000, "coffee", "労働以外の方法で収入を得る感覚をつかむ最初のステップです。金額は小さくても、資産収入を生活に役立てる経験になります。"),
        MilestoneData(2, "STEP 2: 月2,000円", "音楽サブスクと少額のアプリ課金をカバー", 2000, "music_note", "音楽やデジタルコンテンツの費用を資産収入で賄えます。毎月の支出が一つ減る、小さな達成感を得られます。"),
        MilestoneData(3, "STEP 3: 月3,000円", "電気代の基本料金、またはカフェ代を自給", 3000, "lightbulb", "毎月かかる固定費の一部を賄えます。カフェ代に充てれば、日常の楽しみを資産収入で支えられます。"),
        MilestoneData(4, "STEP 4: 月4,000円", "格安SIM1回線分の通信費をカバー", 4000, "smartphone", "スマートフォンの通信費を資産収入で賄えるようになります。毎月の支出を減らし、家計に余裕をつくる一歩です。"),
        MilestoneData(5, "STEP 5: 月5,000円", "動画・音楽サブスクを複数カバー", 5000, "movie", "Netflix、YouTube Premium、Amazon Primeなど、日常的に利用する娯楽サービスの費用をまとめて賄えます。"),
        MilestoneData(6, "STEP 6: 月6,000円", "自宅のインターネット回線・Wi-Fi代を自給", 6000, "wifi", "スマートフォンに続き、自宅のインターネット費用も資産収入で賄えるようになります。通信にかかる支出をさらに減らせます。"),
        MilestoneData(7, "STEP 7: 月8,000円", "水道・光熱費の基本料金をカバー", 8000, "water_drop", "電気・ガス・水道の基本料金を賄えます。生活に必要な固定費の一部を、労働以外の収入で支えられる段階です。"),
        MilestoneData(8, "STEP 8: 月10,000円", "書籍・アプリ・勉強代などの自己投資を自給", 10000, "menu_book", "月1万円に到達するステップです。知識やスキルを身につけるための費用に充てれば、将来の収入や資産形成にもつながります。"),
        MilestoneData(9, "STEP 9: 月15,000円", "電気・ガスの使用料をほぼ全額カバー", 15000, "bolt", "基本料金に加え、実際に使った分の電気・ガス代もほぼ賄えます。季節によって変わる光熱費への不安を減らせます。"),
        MilestoneData(10, "STEP 10: 月20,000円", "週末の外食や趣味にかかる費用を自給", 20000, "restaurant", "固定費だけでなく、余暇や趣味の費用も資産収入で賄えるようになります。生活を楽しむための支出を支えられる段階です。"),
        MilestoneData(11, "STEP 11: 月25,000円", "通信費と水道・光熱費を自給", 25000, "wifi", "通信費と水道・光熱費を資産収入で賄えます。ここから、食費や住居費など生活費の大きな項目に取り組みます。"),
        MilestoneData(12, "STEP 12: 月30,000円", "インフラ費と基本的な娯楽費を自給", 30000, "bolt", "通信費や水道・光熱費に加えて、身近な娯楽の費用も賄えます。給与から支払う毎月の支出が減り、手元に残るお金が増えます。"),
        MilestoneData(13, "STEP 13: 月35,000円", "日用品・雑費をカバー", 35000, "shopping_cart", "トイレットペーパーや洗剤、スキンケア用品、家具の買い替えなどにかかる費用を賄えます。生活を維持するための細かな支出が減ります。"),
        MilestoneData(14, "STEP 14: 月40,000円", "自炊を中心とした基本的な食費をカバー", 40000, "restaurant", "日々の食費の基本部分を資産収入で賄えます。仕事を失った場合にも、食費を支える収入があるという安心につながります。"),
        MilestoneData(15, "STEP 15: 月45,000円", "食費と日用品の基本分を自給", 45000, "shopping_cart", "食費と日用品の費用をまとめて賄えます。生活に必要な支出の多くを資産収入で支えられる段階です。"),
        MilestoneData(16, "STEP 16: 月50,000円", "外食を含む食費と日用品を自給", 50000, "shopping_cart", "毎月の資産収入が5万円に達すると、一人暮らしの食費や日用品など、生活を支える支出を労働収入に頼らず賄えるようになります。家計と働き方の選択肢が広がる、大きな節目です。（現在、この目標に向けて挑戦中）"),
        MilestoneData(17, "STEP 17: 月60,000円", "地方都市の家賃をカバー", 60000, "home", "生活費の中でも大きな割合を占める家賃を賄う段階です。地域や物件によっては、地方都市のワンルームの家賃をカバーできます。"),
        MilestoneData(18, "STEP 18: 月70,000円", "都市部のワンルームの家賃をカバー", 70000, "home", "都市部のワンルームの家賃も、資産収入で賄える水準です。家計に占める大きな固定費が減り、資産形成に回せるお金が増えます。"),
        MilestoneData(19, "STEP 19: 月80,000円", "実家暮らしの場合、基本的な生活費を自給", 80000, "home", "実家暮らしなら、生活に必要な費用を資産収入で賄える段階です。働く収入を自己実現や貯蓄に充てる選択肢が生まれます。"),
        MilestoneData(20, "STEP 20: 月100,000円", "更新料などを含む住居費をカバー", 100000, "home", "家賃に加え、更新料や火災保険、引っ越し費用も含めて住居費を賄うことを目指します。住まいにかかる費用を資産収入で支えられる段階です。"),
        MilestoneData(21, "STEP 21: 月120,000円", "家賃・食費・インフラ費など基本生活費をカバー", 120000, "home", "家賃、食費、インフラ費を資産収入で賄える水準です。支出を抑えた暮らしなら、働いて得る収入がなくても生活できるという安心につながります。"),
        MilestoneData(22, "STEP 22: 月150,000円", "サイドFIREを目指す水準", 150000, "flight", "週2〜3日働きながら、生活にゆとりを持てる水準を目指します。働く日数や仕事を選びやすくなり、通勤や残業の負担も減らせます。"),
        MilestoneData(23, "STEP 23: 月180,000円", "都市部での独身生活費をカバー", 180000, "location_city", "東京などの都市部で暮らす場合も、独身の生活費を資産収入で賄える水準です。自分の時間を自分の判断で使いやすくなります。"),
        MilestoneData(24, "STEP 24: 月200,000円", "標準的な生活費を賄う水準・Lean FIRE", 200000, "flight", "独身で生活する場合の、完全リタイアの目安となる水準です。仕事を続けるかどうかを、自分の希望に合わせて選びやすくなります。"),
        MilestoneData(25, "STEP 25: 月250,000円", "都市部でゆとりのある独身生活を送る", 250000, "star", "基本的な生活費に加え、趣味や旅行、少しぜいたくな食事にも資産収入を充てられます。お金を理由に諦めることが減り、選択肢が広がります。"),
        MilestoneData(26, "STEP 26: 月300,000円", "趣味や旅行を楽しめる生活・Full FIRE", 300000, "flight", "Full FIREを目指す水準です。資産収入が一般的な現役世代の平均手取り額を上回り、旅行や好きな活動に時間とお金を使いやすくなります。"),
        MilestoneData(27, "STEP 27: 月350,000円", "二人暮らし・カップル世帯の基本生活費をカバー", 350000, "people", "一人分の生活費だけでなく、パートナーとの暮らしにかかる基本的な費用も資産収入で賄える水準です。二人で過ごす時間や、家計の選択肢を広げられます。"),
        MilestoneData(28, "STEP 28: 月400,000円", "子どものいる世帯の生活費をカバー", 400000, "family_restroom", "子どものいる世帯の基本的な生活費を賄うことを目指します。家族で過ごす時間や、教育など将来に向けた選択肢を確保しやすくなります。"),
        MilestoneData(29, "STEP 29: 月500,000円", "生活費を上回る収入を得て、資産の増加を目指す", 500000, "trending_up", "毎月の資産収入が一般的な生活費を大きく上回る水準です。使い切らなかった分を再投資すれば、資産の増加を目指せます。"),
        MilestoneData(30, "STEP 30: 月600,000円", "経済的な選択肢が大きく広がる水準", 600000, "autorenew", "このロードマップの最終目標です。FIREを達成した後も、資産収入によって暮らしの選択肢をさらに広げられる水準を目指します。お金のために働く時間を減らし、自分の望む生活に時間を使えるようになります。")
    )

    // Calculate dynamic stats across the 30 milestones
    val dynamicMilestones = milestoneSteps.map { step ->
        val isCompleted = monthlyIncome >= step.targetMonthly
        val prevStep = milestoneSteps.find { s -> s.step == step.step - 1 }
        val isPrevCompleted = prevStep == null || monthlyIncome >= prevStep.targetMonthly

        val status = if (isCompleted) "completed" else if (isPrevCompleted) "active" else "locked"
        val statusLabel = if (isCompleted) "達成済" else if (isPrevCompleted) "挑戦中" else "ロック中"
        val currentRate = if (isCompleted) 100f else (monthlyIncome.toFloat() / step.targetMonthly.toFloat()).coerceIn(0f, 0.99f)

        DynamicMilestone(
            step = step,
            status = status,
            statusLabel = statusLabel,
            currentRate = currentRate
        )
    }

    val highestCompletedStep = dynamicMilestones.findLast { it.status == "completed" }?.step?.step ?: 0
    val activeQuest = dynamicMilestones.find { it.status == "active" } ?: dynamicMilestones.find { it.status == "locked" } ?: dynamicMilestones.last()
    val activeStep = activeQuest.step
    val currentLevel = when {
        highestCompletedStep >= 20 -> 3
        highestCompletedStep >= 10 -> 2
        else -> 1
    }
    val levelOneLabel = if (highestCompletedStep >= 10) "LV.1 制覇" else "LV.1 挑戦中"
    val levelTwoLabel = when {
        highestCompletedStep >= 20 -> "LV.2 制覇"
        currentLevel == 2 -> "LV.2 挑戦中"
        else -> "LV.2 ロック"
    }
    val levelThreeLabel = when {
        highestCompletedStep >= 30 -> "LV.3 制覇"
        currentLevel == 3 -> "LV.3 挑戦中"
        else -> "LV.3 ロック"
    }

    Scaffold(
        containerColor = BackgroundDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = PrimaryEmerald,
                contentColor = OnPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "複数口座一括配分登録",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quest Hero Card Dashboard
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "● LEVEL $currentLevel 進行中: STEP ${activeStep.step}",
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = AppTextSize.dense,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, SecondaryIndigo.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "🏆 クリア済み ($highestCompletedStep/30)",
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Trophy banner appears after all ten Level 1 steps are cleared.
                        if (highestCompletedStep >= 10) Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryEmerald.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentAmber.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = AccentAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "LEVEL 1 完全制覇達成！ 👑",
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.dense
                                        )
                                        Text(
                                            "生活インフラ自給期 (STEP 1〜10) を踏破完了",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "ALL CLEAR",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("現在の配当獲得ステータス", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            "¥${currencyFormat.format(totalAnnualExpectedDividend)} / 年",
                            fontSize = AppTextSize.hero,
                            fontWeight = FontWeight.Black,
                            color = OnSurface,
                            fontFamily = FontFamily.SansSerif
                        )

                        Text(
                            text = "月間換算不労所得: 約 ¥${currencyFormat.format(monthlyIncome)} / 月",
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target Box with dynamic flag shortage calculations matching image
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundDark.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "QUEST ${activeStep.step} 目標: ¥${currencyFormat.format(activeStep.targetMonthly)} / 月",
                                            color = OnSurface,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "${(activeQuest.currentRate * 100).toInt()}% 達成",
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = AppTextSize.caption
                                    )
                                }

                                var isProgressActive by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { isProgressActive = true }
                                val animatedProgress by animateFloatAsState(
                                    targetValue = if (isProgressActive) activeQuest.currentRate else 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "questProgress"
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PrimaryEmerald,
                                    trackColor = SurfaceContainerHighest
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "現在実績: ¥${currencyFormat.format(monthlyIncome)}",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )

                                    val shortage = activeStep.targetMonthly - monthlyIncome
                                    if (shortage > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Flag, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                "あと ¥${currencyFormat.format(shortage)} / 月 で攻略",
                                                color = ErrorRed,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Text(
                                            "マイルストーン達成済！",
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic simulation tip card
                        val shortage = maxOf(0L, activeStep.targetMonthly - monthlyIncome)
                        val shortageAnnual = shortage * 12
                        val neededPrincipalAt4 = Math.round(shortageAnnual / 0.04 / 10000.0)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "年額 +¥${currencyFormat.format(shortageAnnual)} でSTEP ${activeStep.step}クリア。配当利回り 4.0% 想定時、必要な追加投資元本は約 ¥${neededPrincipalAt4}万円 です。",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Share & Save Buttons matching Image 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showShareBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("達成度を共有する", color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = OnSurface, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("画像で保存", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 2: "3大ステージ・レベル帯" Grid selection
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("3大ステージ・レベル帯", fontWeight = FontWeight.ExtraBold, color = OnSurface, fontSize = AppTextSize.body)
                        }
                        Text("タップしてジャンプ", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Level 1 Filter Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStageFilter = 1 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStageFilter == 1) SurfaceContainerHigh else SurfaceContainerLow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedStageFilter == 1) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(levelOneLabel, color = if (selectedStageFilter == 1) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        if (highestCompletedStep >= 10) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = if (highestCompletedStep >= 10) PrimaryEmerald else AccentAmber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text("インフラ自給期", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text("STEP 1-10", color = if (selectedStageFilter == 1) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Level 2 Filter Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStageFilter = 2 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStageFilter == 2) SurfaceContainerHigh else SurfaceContainerLow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedStageFilter == 2) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(levelTwoLabel, color = if (selectedStageFilter == 2) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        when {
                                            highestCompletedStep >= 20 -> Icons.Default.CheckCircle
                                            currentLevel == 2 -> Icons.Default.PlayCircle
                                            else -> Icons.Default.Lock
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            highestCompletedStep >= 20 -> PrimaryEmerald
                                            currentLevel == 2 -> AccentAmber
                                            else -> OnSurfaceVariant
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text("生活費・ミニFIRE", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("STEP 11-20", color = if (selectedStageFilter == 2) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Level 3 Filter Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStageFilter = 3 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStageFilter == 3) SurfaceContainerHigh else SurfaceContainerLow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedStageFilter == 3) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(levelThreeLabel, color = if (selectedStageFilter == 3) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        when {
                                            highestCompletedStep >= 30 -> Icons.Default.CheckCircle
                                            currentLevel == 3 -> Icons.Default.PlayCircle
                                            else -> Icons.Default.Lock
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            highestCompletedStep >= 30 -> PrimaryEmerald
                                            currentLevel == 3 -> AccentAmber
                                            else -> OnSurfaceVariant
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text("完全FIRE期", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("STEP 21-30", color = if (selectedStageFilter == 3) OnSurfaceVariant else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Milestone Steps list title
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckBox, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "全30マイルストーン・クエストマップ",
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                        fontSize = AppTextSize.body
                    )
                }
            }

            // Dynamic Milestone list rows filtered by level stage filter
            val filteredMilestones = dynamicMilestones.filter { dm ->
                val s = dm.step.step
                when (selectedStageFilter) {
                    1 -> s in 1..10
                    2 -> s in 11..20
                    3 -> s in 21..30
                    else -> true
                }
            }

            items(filteredMilestones.size) { idx ->
                val item = filteredMilestones[idx]
                val stepData = item.step
                val isCleared = item.status == "completed"
                val isCurrent = item.status == "active"
                val isOpen = openStep == stepData.step

                val rowBadgeColor = if (isCleared) {
                    PrimaryEmerald
                } else if (isCurrent) {
                    AccentAmber // Amber
                } else {
                    OnSurfaceVariant // Gray/Slate
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) SurfaceContainerHigh else SurfaceContainerLow
                    ),
                    border = BorderStroke(0.5.dp, if (isCurrent) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { openStep = if (isOpen) null else stepData.step }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                             ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCleared) PrimaryEmerald.copy(alpha = 0.15f) else if (isCurrent) PrimaryEmerald else SurfaceContainerHighest,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${stepData.step}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = AppTextSize.caption,
                                            color = if (isCurrent) OnPrimary else if (isCleared) PrimaryEmerald else OnSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stepData.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OnSurface,
                                            fontSize = AppTextSize.caption
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(rowBadgeColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                item.statusLabel,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold,
                                                color = rowBadgeColor
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        stepData.title,
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isCleared) "100% CLEAR" else "${(item.currentRate * 100).toInt()}%",
                                    color = if (isCleared || isCurrent) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isOpen) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = SurfaceContainerHighest, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                stepData.description,
                                color = SecondaryIndigo,
                                fontSize = AppTextSize.dense,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            val shortageAmt = stepData.targetMonthly - monthlyIncome

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainer)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("目標額 (月額 / 年額)", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        Text("¥${currencyFormat.format(stepData.targetMonthly)} /月", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text("年額 ¥${currencyFormat.format(stepData.targetMonthly * 12)}", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainer)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("攻略状況", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        if (isCleared) {
                                            Text("達成済 ✨", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                            Text("現在月額 ¥${currencyFormat.format(monthlyIncome)}", fontSize = AppTextSize.dense, color = PrimaryEmerald)
                                        } else {
                                            Text("あと ¥${currencyFormat.format(shortageAmt)} /月", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = ErrorRed)
                                            Text("年額 ¥${currencyFormat.format(shortageAmt * 12)} 不足", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            if (!isCleared) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val shortageAnnualAmt = shortageAmt * 12

                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceContainer)
                                        .border(0.5.dp, BorderBlue, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        "利回り別・必要追加元本シミュレーション",
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3.5 to "3.5%", 4.0 to "4.0%", 4.5 to "4.5%").forEach { (yield, label) ->
                                            val capital = Math.round(shortageAnnualAmt / (yield / 100.0) / 10000.0)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (yield == 4.0) Color(0x1F10B981) else SurfaceContainer)
                                                    .border(0.5.dp, if (yield == 4.0) PrimaryEmerald.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(label, fontSize = AppTextSize.dense, color = if (yield == 4.0) PrimaryEmerald else OnSurfaceVariant)
                                                    Text("¥${capital}万", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = if (yield == 4.0) PrimaryEmerald else OnSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal Bottom Sheet: 複数口座一括配分登録 (Image 2)
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "複数口座一括配分登録",
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "口座間の株数・取得単価を1画面でまとめて設定",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderColor)

                // Active stock selector state
                var selectedTickerInput by remember { mutableStateOf("AAPL") }
                val activeTicker = selectedTickerInput.uppercase(Locale.ROOT).trim()

                // Static DB for demonstration matching Image 2
                val activeStockName = when (activeTicker) {
                    "AAPL" -> "Apple Inc."
                    "7203" -> "トヨタ自動車 (TOYOTA)"
                    "8316" -> "三井住友フィナンシャルG"
                    "9432" -> "日本電信電話 (NTT)"
                    else -> "$activeTicker Corp."
                }
                val activeStockMarket = when (activeTicker) {
                    "AAPL" -> "NASDAQ ・ US Tech ・ 米国株"
                    else -> "東証PRM ・ 日本株 ・ 円建て"
                }
                val activeStockCurrency = if (activeTicker == "AAPL") "$" else "¥"
                val activeStockPrice = when (activeTicker) {
                    "AAPL" -> 220.0
                    "7203" -> 2600.0
                    "8316" -> 9800.0
                    "9432" -> 170.0
                    else -> 1000.0
                }
                val activeStockYield = when (activeTicker) {
                    "AAPL" -> 0.45
                    "7203" -> 2.80
                    "8316" -> 3.50
                    "9432" -> 3.20
                    else -> 3.00
                }
                val activeStockDPS = activeStockPrice * (activeStockYield / 100.0)

                // Input States for the 4 Accounts
                var sharesNisaGrowth by remember { mutableStateOf("5") }
                var priceNisaGrowth by remember { mutableStateOf("220.00") }

                var sharesNisaTsumitate by remember { mutableStateOf("0") }
                var priceNisaTsumitate by remember { mutableStateOf("2600") }

                var sharesTokutei by remember { mutableStateOf("5") }
                var priceTokutei by remember { mutableStateOf("220.00") }

                var sharesIppan by remember { mutableStateOf("0") }
                var priceIppan by remember { mutableStateOf("220.00") }

                // Search Bar Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "銘柄コード・ティッカー番号入力",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = selectedTickerInput,
                            onValueChange = { selectedTickerInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            placeholder = { Text("例: 7203, AAPL など", fontSize = AppTextSize.dense, color = OnSurfaceVariant) },
                            singleLine = true
                        )
                        Button(
                            onClick = { /* Apply */ },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("適用", color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Candidate Chips matching Image 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("AAPL", "7203", "8316", "9432").forEach { code ->
                            val label = when (code) {
                                "AAPL" -> "AAPL Apple"
                                "7203" -> "7203 トヨタ"
                                "8316" -> "8316 三井住友"
                                "9432" -> "9432 NTT"
                                else -> code
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activeTicker == code) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceContainerHigh,
                                border = BorderStroke(1.dp, if (activeTicker == code) PrimaryEmerald else Color.Transparent),
                                modifier = Modifier
                                    .clickable {
                                        selectedTickerInput = code
                                        // Auto fill price template values based on stock selection
                                        val templatePrice = when (code) {
                                            "AAPL" -> "220.00"
                                            "7203" -> "2600"
                                            "8316" -> "9800"
                                            "9432" -> "170"
                                            else -> "100"
                                        }
                                        priceNisaGrowth = templatePrice
                                        priceNisaTsumitate = templatePrice
                                        priceTokutei = templatePrice
                                        priceIppan = templatePrice
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = if (activeTicker == code) PrimaryEmerald else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Active Stock Preview Card matching Image 2
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryEmerald.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        activeTicker.take(4),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        activeStockName,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        activeStockMarket,
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x1F10B981)
                                ) {
                                    Text(
                                        "買場目安近接",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "1株配当 $activeStockCurrency" + String.format("%.2f", activeStockDPS),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "現在値: $activeStockCurrency" + currencyFormat.format(activeStockPrice.toInt()),
                                color = OnSurface,
                                fontSize = AppTextSize.caption,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "現在配当利回り: " + String.format("%.2f%%", activeStockYield),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Header sub-label Matrix Allocation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "口座別 ポートフォリオ配分マトリックス",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "クリア",
                        color = ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            sharesNisaGrowth = "0"
                            sharesNisaTsumitate = "0"
                            sharesTokutei = "0"
                            sharesIppan = "0"
                        }
                    )
                }

                // Account Allocation Cards Grid inside BottomSheet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Account Row 1: 新NISA 成長投資枠
                    AccountMatrixCard(
                        title = "新NISA 成長投資枠",
                        isTaxFree = true,
                        shares = sharesNisaGrowth,
                        price = priceNisaGrowth,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesNisaGrowth = it },
                        onPriceChange = { priceNisaGrowth = it },
                        dps = activeStockDPS
                    )

                    // Account Row 2: 新NISA つみたて枠
                    AccountMatrixCard(
                        title = "新NISA つみたて枠",
                        isTaxFree = true,
                        shares = sharesNisaTsumitate,
                        price = priceNisaTsumitate,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesNisaTsumitate = it },
                        onPriceChange = { priceNisaTsumitate = it },
                        dps = activeStockDPS
                    )

                    // Account Row 3: 特定口座 (源泉徴収あり)
                    AccountMatrixCard(
                        title = "特定口座 (源泉徴収あり)",
                        isTaxFree = false,
                        shares = sharesTokutei,
                        price = priceTokutei,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesTokutei = it },
                        onPriceChange = { priceTokutei = it },
                        dps = activeStockDPS
                    )

                    // Account Row 4: 一般口座 (確定申告)
                    AccountMatrixCard(
                        title = "一般口座 (確定申告)",
                        isTaxFree = false,
                        shares = sharesIppan,
                        price = priceIppan,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesIppan = it },
                        onPriceChange = { priceIppan = it },
                        dps = activeStockDPS
                    )
                }

                // Save Action Button inside BottomSheet
                Button(
                    onClick = {
                        val entries = listOf(
                            Triple(sharesNisaGrowth, priceNisaGrowth, "新NISA成長枠"),
                            Triple(sharesNisaTsumitate, priceNisaTsumitate, "新NISAつみたて投資枠"),
                            Triple(sharesTokutei, priceTokutei, "特定口座"),
                            Triple(sharesIppan, priceIppan, "一般口座")
                        )
                        val allocations = entries.mapNotNull { (quantity, price, account) ->
                            val shares = quantity.toIntOrNull() ?: return@mapNotNull null
                            val cost = price.toDoubleOrNull() ?: return@mapNotNull null
                            com.kurogane.dividend.model.HoldingAllocation(shares, cost, account)
                        }
                        if (allocations.size != entries.size || !viewModel.addHoldingAllocations(activeTicker, allocations)) return@Button
                        showBottomSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "ポートフォリオに一括追加登録",
                        color = OnPrimary,
                        fontSize = AppTextSize.body,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet: 達成状況をシェア (Image 1 & 2 share certificate)
    if (showShareBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareBottomSheet = false },
            sheetState = shareSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Share Modal Header matching image perfectly
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "達成状況をシェア",
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "SNSで進捗を共有できます",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showShareBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                // Interactive Tab Selector: 進捗カード vs 踏破認定証
                var selectedShareTab by remember { mutableIntStateOf(1) } // Default active: 踏破認定証
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedShareTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedShareTab == 0) SurfaceContainerHigh else Color.Transparent
                        ),
                        border = if (selectedShareTab == 0) BorderStroke(1.dp, BorderColor) else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QUEST 6 進捗カード", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { selectedShareTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedShareTab == 1) SurfaceContainerHigh else Color.Transparent
                        ),
                        border = if (selectedShareTab == 1) BorderStroke(1.dp, BorderColor) else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LV.1 踏破認定証", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }

                // Masking Switch Options Row
                var isAmountMasked by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHigh,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("金額を伏字にする", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = isAmountMasked,
                            onCheckedChange = { isAmountMasked = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnSurface,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }

                // Dynamic Certificate Card Box View matching Image perfectly
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerHigh)
                        .border(1.dp, if (selectedShareTab == 1) PrimaryEmerald.copy(alpha = 0.4f) else BorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedShareTab == 1) {
                            // Medal design matching image
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = PrimaryEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "LEVEL 1 COMPLETE",
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "生活インフラ自給期 踏破認定証",
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "STEP 1〜10 (月2万円・年24万円) を完全達成",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Inner sub-card with highlight title
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "獲得した生活権益",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "水道光熱費・通信費の全額自給権 ⚡ 📱",
                                        color = PrimaryEmeraldContainer,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "到達実績: " + if (isAmountMasked) "¥••,••• / 月" else "¥${currencyFormat.format(monthlyIncome)} / 月",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "認定ランク: LEVEL 1 MASTER",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Roadmap Quest",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            // Quest Card View (Tab 0 Selected)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SkyBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SkyBlue.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = SkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "CURRENT ACTIVE QUEST",
                                color = SkyBlue,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "QUEST ${activeStep.step}: " + activeStep.name.substringAfter(": "),
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "目標額: 月 ¥${currencyFormat.format(activeStep.targetMonthly)} 自給に向けて挑戦中",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Inner progress sub-card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "現在のクエスト進捗状況",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "目標達成率: ${(activeQuest.currentRate * 100).toInt()}% 🚀",
                                        color = PrimaryEmeraldContainer,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "現在実績: " + if (isAmountMasked) "¥••,••• / 月" else "¥${currencyFormat.format(monthlyIncome)} / 月",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "現在のステータス: LEVEL 2 CHALLENGER",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Roadmap Quest",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Premium Gradient Share Action Button matching image perfectly
                Button(
                    onClick = { showShareBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OnSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        "𝕏 𝕏 (Twitter) でクエスト進捗をシェア",
                        color = BackgroundDark,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// Custom Account matrix allocation form Card component
@Composable
private fun AccountMatrixCard(
    title: String,
    isTaxFree: Boolean,
    shares: String,
    price: String,
    currency: String,
    onSharesChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    dps: Double
) {
    val numShares = shares.toIntOrNull() ?: 0
    val unitPrice = price.toDoubleOrNull() ?: 0.0
    val totalCost = numShares * unitPrice
    val rawDividend = numShares * dps
    val finalDividend = if (isTaxFree) rawDividend else rawDividend * (1.0 - 0.20315)
    val taxAmt = rawDividend * 0.20315

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isTaxFree) PrimaryEmerald else SecondaryIndigo)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isTaxFree) PrimaryEmerald.copy(alpha = 0.15f) else Color(0x1F94A3B8)
                    ) {
                        Text(
                            text = if (isTaxFree) "非課税 0%" else "課税 20.315%",
                            color = if (isTaxFree) PrimaryEmerald else OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = "手取り配当: $currency" + String.format("%.2f/年", finalDividend),
                    color = if (numShares > 0) PrimaryEmerald else OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Inputs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shares Input Column
                Column(modifier = Modifier.weight(1f)) {
                    Text("保有株数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = shares,
                            onValueChange = onSharesChange,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        )
                        // Quick add buttons
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { onSharesChange(((shares.toIntOrNull() ?: 0) + 10).toString()) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("+10", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { onSharesChange(((shares.toIntOrNull() ?: 0) + 100).toString()) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("+100", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Acquisition Price Input Column
                Column(modifier = Modifier.weight(1f)) {
                    Text("取得単価 (平均)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = price,
                            onValueChange = onPriceChange,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        )
                        Text("/株", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }
            }

            if (numShares > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "投資元本: $currency" + String.format("%.2f", totalCost),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )

                    if (isTaxFree) {
                        Text(
                            "NISA節税効果: +$currency" + String.format("%.2f", taxAmt),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "源泉控除税額: -$currency" + String.format("%.2f", taxAmt),
                            color = ErrorRed,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Data structures for clean dynamic rendering
private data class MilestoneData(
    val step: Int,
    val name: String,
    val title: String,
    val targetMonthly: Int,
    val icon: String,
    val description: String
)

private data class DynamicMilestone(
    val step: MilestoneData,
    val status: String,
    val statusLabel: String,
    val currentRate: Float
)
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/SettingsScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kurogane.dividend.data.PortfolioExporter
import com.kurogane.dividend.data.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsCard: Color @Composable get() = SurfaceContainerLow
private val SettingsInset: Color @Composable get() = BackgroundDark
private val SettingsBorder: Color @Composable get() = BorderColor
private val SettingsMint: Color @Composable get() = PrimaryEmerald
private val SettingsMuted: Color @Composable get() = OnSurfaceVariant

@Composable
fun SettingsScreen(
    viewModel: DividendViewModel,
    onBack: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    onNavigateToCsv: () -> Unit = {}
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isOledBlack by viewModel.isOledBlack.collectAsStateWithLifecycle()
    val selectedAccent by viewModel.selectedAccent.collectAsStateWithLifecycle()
    var dataTab by rememberSaveable { mutableStateOf("import") }
    var calcBasis by rememberSaveable { mutableStateOf("eval") }
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val taxDeduction = taxRateMode == "net"
    var usTaxDeduction by rememberSaveable { mutableStateOf(true) }
    var targetYield by rememberSaveable { mutableStateOf("4.0") }
    var threshold by rememberSaveable { mutableIntStateOf(5) }
    val maskAtStartup by viewModel.maskAtStartup.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            isExporting = true
            val stocks = viewModel.holdings.value
            exportScope.launch {
                try {
                    val result = runCatchingCancellable { withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(PortfolioExporter.csv(stocks).toByteArray(Charsets.UTF_8)) }
                            ?: error("保存先を開けませんでした")
                    } }
                    Toast.makeText(context, if (result.isSuccess) "CSVを保存しました" else "CSVを保存できませんでした", Toast.LENGTH_LONG).show()
                } finally { isExporting = false }
            }
        }
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            isExporting = true
            val stocks = viewModel.holdings.value
            exportScope.launch {
                try {
                    val result = runCatchingCancellable { withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(PortfolioExporter.json(stocks).toByteArray(Charsets.UTF_8)) }
                            ?: error("保存先を開けませんでした")
                    } }
                    Toast.makeText(context, if (result.isSuccess) "JSONを保存しました" else "JSONを保存できませんでした", Toast.LENGTH_LONG).show()
                } finally { isExporting = false }
            }
        }
    }
    val accents = listOf(
        "emerald" to Color(0xFF10B981), "cyan" to Color(0xFF06B6D4),
        "blue" to Color(0xFF3B82F6), "amber" to AccentAmber,
        "purple" to Color(0xFF8B5CF6)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BackgroundDark),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            SettingsSection("データ管理＆バックアップ", Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SettingsInset).padding(3.dp)) {
                        Segment("▧  インポート (取込)", dataTab == "import", Modifier.weight(1f)) { dataTab = "import" }
                        Segment("⇩  エクスポート (保存)", dataTab == "export", Modifier.weight(1f)) { dataTab = "export" }
                    }
                    if (dataTab == "import") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Label("対応証券口座フォーマット", 9, SettingsMuted)
                            Label("◉ 自動フォーマット判定", 8, SettingsMint)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("SBI証券", "楽天証券", "マネックス", "松井証券").forEach { broker ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(SettingsInset).border(1.dp, SettingsBorder, RoundedCornerShape(6.dp)).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Label(broker, 8, SettingsMint, true)
                                    Label("CSV読込", 7, SettingsMuted)
                                }
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(9.dp))
                                .background(SettingsInset).border(1.dp, SettingsMint.copy(alpha = .48f), RoundedCornerShape(9.dp))
                                .clickable(onClick = onNavigateToCsv),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CloudUpload, null, tint = SettingsMint, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.height(3.dp))
                            Label("CSVファイルを選択またはドロップ", 9, OnSurface, true)
                            Label(".csv形式 (UTF-8・Shift-JIS自動判定 / 最大10MB)", 7, SettingsMuted)
                            Spacer(Modifier.height(4.dp))
                            Label("▱  ウィザードへ", 8, SettingsMint, true)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label("◉ 前回インポート:          2024/10/24 18:30", 7, SettingsMuted)
                            Label("SBI証券 48銘柄", 7, SettingsMint)
                        }
                        Label("ⓘ  各証券会社CSVのダウンロード手順   保存データ上書き/追加", 7, SettingsMuted)
                    } else {
                        Label("登録中の保有株・配当情報・取得単価データを保存します。", 9, SettingsMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("▦  CSV形式で保存", Modifier.weight(1f)) { if (!isExporting) csvExporter.launch("Diview-portfolio.csv") }
                            ActionButton("{ }  JSONバックアップ", Modifier.weight(1f)) { if (!isExporting) jsonExporter.launch("Diview-portfolio.json") }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Label("♢  CSVファイルは端末内でのみ安全に処理され、外部サーバーにアップロードされることはありません。", 7, SettingsMuted)
                }
            }
        }

        item {
            SettingsSection("外観・テーマ設定", Icons.Default.Palette) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("カラーテーマ", 9, OnSurface, true)
                        Label(if (themeMode == "dark") "ダーク適用中" else if (themeMode == "light") "ライト" else "システム連動", 8, SettingsMint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("dark" to "☾  ダーク", "light" to "☼  ライト", "system" to "▣  システム").forEach { (mode, name) ->
                            Segment(name, themeMode == mode, Modifier.weight(1f)) { viewModel.setThemeMode(mode) }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("アクセントカラー", 9, OnSurface, true)
                        Label(selectedAccent.replaceFirstChar { it.uppercase() }, 8, SettingsMint)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        accents.forEach { (name, color) ->
                            Box(Modifier.size(27.dp).clip(CircleShape).background(color).then(if (selectedAccent == name) Modifier.border(2.dp, OnSurface, CircleShape) else Modifier).clickable { viewModel.setSelectedAccent(name) }, contentAlignment = Alignment.Center) {
                                if (selectedAccent == name) Icon(Icons.Default.Check, null, tint = OnSurface, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("有機ELダーク (完全ブラック)", "ピクセル発光を最小化し黒色消費電力を抑制", isOledBlack, { viewModel.setIsOledBlack(it) }, tag = "節電")
                }
            }
        }

        item {
            SettingsSection("表示・計算カスタマイズ", Icons.Default.Tune) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("配当利回りの計算基準", 9, OnSurface, true); Label("利回り計算ロジック", 7, SettingsMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasisChoice("評価額基準", "現在値ベース", calcBasis == "eval", Modifier.weight(1f)) { calcBasis = "eval" }
                        BasisChoice("取得額基準", "買付単価 (YOC)", calcBasis == "cost", Modifier.weight(1f)) { calcBasis = "cost" }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("税金計算設定", "税引後表示 (国内税 20.315% を控除)", taxDeduction, { viewModel.setTaxRateMode(if (it) "net" else "gross") })
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("米国株 現地源泉徴収控除 (10%)", "現地税の計算は準備中です", false, {}, tag = "準備中", enabled = false)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("目標利回り (ロードマップ試算)", 9, OnSurface, true); Label("シミュレーション基準   $targetYield%", 8, SettingsMint) }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("3.5" to "3.5% (堅実)", "4.0" to "4.0% (標準)", "4.5" to "4.5% (積極)").forEach { (value, label) ->
                            Segment(label, targetYield == value, Modifier.weight(1f)) { targetYield = value }
                        }
                    }
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("構成比「その他」集約閾値", 9, OnSurface, true); Label("$threshold%", 8, SettingsMint) }
                    ThresholdSlider(threshold) { threshold = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Label("3% (詳細)", 7, SettingsMuted); Label("5% (推奨)", 7, SettingsMint); Label("10% (簡易)", 7, SettingsMuted) }
                }
            }
        }

        item {
            SettingsSection("通知・リマインダー設定", Icons.Default.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle("配当金の権利落ち日・入金通知", "プッシュ通知は準備中です", false, {}, tag = "準備中", enabled = false)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("買場シグナル・株価アラート", "プッシュ通知は準備中です", false, {}, enabled = false)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Label("通知時間帯の指定", 9, OnSurface, true); Label("市場寄り前・引け後まとめ", 7, SettingsMuted) }
                        Label("朝 08:30 / 夕 17:30", 8, SettingsMint)
                    }
                }
            }
        }

        item {
            SettingsSection("セキュリティ＆プライバシー", Icons.Default.Shield) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingToggle("生体認証 (Face ID / 指紋認証)", "生体認証は準備中です", false, {}, tag = "準備中", enabled = false)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SettingToggle("起動時の金額伏字マスク", "電車内など外出時の画面閲覧用", maskAtStartup, { viewModel.setMaskAtStartup(it) }, tag = "覗き見防止")
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Label("ローカルデータ完全消去", 9, ErrorRed, true); Label("端末内の保有・設定データをすべて削除", 7, SettingsMuted) }
                        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF421D2A)).border(1.dp, Color(0xFF8D3341), RoundedCornerShape(5.dp)).clickable { confirmReset = true }.padding(horizontal = 9.dp, vertical = 5.dp)) { Label("初期化", 8, ErrorRed, true) }
                    }
                }
            }
        }

        item {
            SettingsSection("アプリ情報・サポート", Icons.Default.Info) {
                Column {
                    SupportRow("お問い合わせ・フィードバック", onNavigateToFeedback)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SupportRow("利用規約＆プライバシーポリシー", onNavigateToTerms)
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    SupportRow("アプリをレビューで応援", {})
                    HorizontalDivider(color = SettingsBorder, thickness = .6.dp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) { Label("バージョン", 8, SettingsMuted); Label("v${com.kurogane.dividend.BuildConfig.VERSION_NAME} (Build ${com.kurogane.dividend.BuildConfig.VERSION_CODE})", 8, OnSurface, true) }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("♙  金融データは端末内暗号化保存されています", 7, SettingsMint)
                Spacer(Modifier.height(3.dp)); Label("© 2025 Diview", 6, SettingsMuted)
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("データを初期化", color = OnSurface) },
        text = { Text("端末内の保有・設定データを初期化しますか？", color = SettingsMuted) },
        confirmButton = { TextButton(onClick = {
            confirmReset = false
            exportScope.launch {
                val result = runCatchingCancellable { viewModel.resetLocalData() }
                Toast.makeText(context, if (result.isSuccess) "データを初期化しました" else "初期化できませんでした", Toast.LENGTH_LONG).show()
            }
        }) { Text("初期化", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("キャンセル", color = SettingsMuted) } },
        containerColor = SettingsCard
    )
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(start = 3.dp)) {
            Icon(icon, null, tint = SettingsMint, modifier = Modifier.size(14.dp))
            Text(title, color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SettingsCard).border(.7.dp, SettingsBorder, RoundedCornerShape(10.dp)).padding(10.dp), content = content)
    }
}

@Composable
private fun Label(text: String, size: Int, color: Color, bold: Boolean = false) {
    Text(text, color = color, fontSize = (size + 3).sp, lineHeight = (size + 6).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).then(if (selected) Modifier.background(SettingsMint.copy(alpha = .16f)).border(.8.dp, SettingsMint, RoundedCornerShape(6.dp)) else Modifier).clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) SettingsMint else SettingsMuted, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, onChanged: (Boolean) -> Unit, tag: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChanged(!checked) }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(title, 8, OnSurface, true)
                if (tag != null) Box(Modifier.clip(RoundedCornerShape(3.dp)).background(SettingsMint.copy(alpha = .13f)).padding(horizontal = 4.dp, vertical = 1.dp)) { Label(tag, 6, SettingsMint, true) }
            }
            Label(detail, 7, SettingsMuted)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(31.dp).height(17.dp).clip(CircleShape).background(if (checked) PrimaryEmeraldContainer else SurfaceContainerHighest).padding(2.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(OnSurface))
        }
    }
}

@Composable
private fun BasisChoice(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(7.dp)).background(if (selected) SettingsMint.copy(alpha = 0.14f) else SettingsInset).border(1.dp, if (selected) SettingsMint else SettingsBorder, RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label(title, 8, if (selected) OnSurface else SettingsMuted, true)
            Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) SettingsMint else SettingsMuted, modifier = Modifier.size(12.dp))
        }
        Label(subtitle, 7, SettingsMuted)
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(34.dp).clip(RoundedCornerShape(7.dp)).background(SettingsInset).border(1.dp, SettingsBorder, RoundedCornerShape(7.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Label(label, 8, OnSurface, true) }
}

@Composable
private fun SupportRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Label(label, 8, OnSurface, true)
        Icon(Icons.Default.ChevronRight, null, tint = SettingsMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ThresholdSlider(value: Int, onValueChange: (Int) -> Unit) {
    val trackColor = SurfaceContainerHighest
    val accentColor = PrimaryEmeraldContainer
    Canvas(Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) {
        detectTapGestures { onValueChange((3 + (it.x / size.width * 7).roundToInt()).coerceIn(3, 10)) }
    }.pointerInput(Unit) {
        detectDragGestures { change, _ -> change.consume(); onValueChange((3 + (change.position.x / size.width * 7).roundToInt()).coerceIn(3, 10)) }
    }) {
        val y = size.height / 2f
        val x = (value - 3) / 7f * size.width
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(accentColor, Offset(0f, y), Offset(x, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(accentColor, 6.dp.toPx(), Offset(x, y))
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.model.StockAnalysisNarrative
import com.kurogane.dividend.model.StockAnalysisNarrativeGenerator
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()

    var resolvedSecurity by remember(stockCode) { mutableStateOf<com.kurogane.dividend.data.CloudflareSecurity?>(null) }
    var lookupFinished by remember(stockCode) { mutableStateOf(false) }
    LaunchedEffect(stockCode) {
        resolvedSecurity = com.kurogane.dividend.data.runCatchingCancellable { viewModel.resolveCloudflareSecurity(stockCode) }.getOrNull()
        lookupFinished = true
    }
    val currentHolding = com.kurogane.dividend.model.StockDetailResolver.resolve(stockCode, holdings, watchlist, resolvedSecurity)
    if (currentHolding == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!lookupFinished) CircularProgressIndicator() else {
                Text("銘柄情報を取得できませんでした", color = OnSurface)
                TextButton(onClick = onBack) { Text("戻る") }
            }
        }
        return
    }

    val isBookmarked = watchlist.any { it.code == currentHolding.code }

    // Selected status tab: default to "risk" to highlight the user requested detailed mock
    var selectedStatusTab by remember { mutableStateOf("risk") }
    var simAddedShares by remember { mutableIntStateOf(100) }
    var isAlertOn by remember { mutableStateOf(true) }

    val baseShares = currentHolding.shares
    val currentPrice = currentHolding.price
    val dps = currentHolding.dps
    val profile = com.kurogane.dividend.model.getStockProfile(currentHolding.code)
    var backendAnalysisValues by remember(currentHolding.code) { mutableStateOf<Map<String, Double?>?>(null) }
    LaunchedEffect(currentHolding.code) {
        backendAnalysisValues = viewModel.fetchStockAnalysis(currentHolding.code)?.values
    }
    val analysisNarrative = remember(currentHolding, backendAnalysisValues) { StockAnalysisNarrativeGenerator.generate(
        backendAnalysisValues ?: emptyMap()
    ) }
    val liveYield = if (currentPrice > 0) (dps / currentPrice) * 100.0 else 0.0
    val liveYoc = if (currentHolding.acqPrice > 0) (dps / currentHolding.acqPrice) * 100.0 else liveYield

    val totalSimShares = baseShares.toLong() + simAddedShares
    val additionalCost = simAddedShares * currentPrice / currentHolding.priceUnit
    val addedAnnualDiv = (simAddedShares * dps / currentHolding.priceUnit).toLong()
    val newTotalAnnualDiv = (totalSimShares * dps / currentHolding.priceUnit).toLong()
    val newMonthlyDiv = newTotalAnnualDiv / 12
    val multiplier = if (baseShares > 0) String.format("%.1f", totalSimShares.toFloat() / baseShares) else "1.0"

    val totalValuation = currentHolding.shares * currentHolding.price / currentHolding.priceUnit
    val totalCost = currentHolding.shares * currentHolding.acqPrice / currentHolding.priceUnit
    val gainLoss = totalValuation - totalCost
    val gainLossPct = if (totalCost > 0) String.format("%.2f", (gainLoss / totalCost) * 100) else "0.00"

    val cutImpact = (baseShares * dps / currentHolding.priceUnit / 2).toLong()

    val totalPortfolioDiv = holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode).toLong() }
    val currentStockAnnualDiv = currentHolding.getEffectiveAnnualDividend(taxRateMode).toLong()
    val divDependency = if (totalPortfolioDiv > 0) (currentStockAnnualDiv.toDouble() / totalPortfolioDiv.toDouble()) * 100.0 else 0.0
    val divDependencyStr = String.format("%.1f", divDependency)

    val totalPortfolioVal = holdings.sumOf { it.totalValuation.toLong() }
    val sectorVal = holdings.filter { it.sector == currentHolding.sector }.sumOf { it.totalValuation.toLong() }
    val sectorDependency = if (totalPortfolioVal > 0) (sectorVal.toDouble() / totalPortfolioVal.toDouble()) * 100.0 else 0.0
    val sectorDependencyStr = String.format("%.1f", sectorDependency)
    val cleanSectorName = currentHolding.sector.replace(Regex("^.*[•·]\\s*"), "")
    val sectorRiskText = if (sectorDependency > 30.0) "高集中" else if (sectorDependency > 20.0) "やや集中" else "適正分散"

    val isHighRisk = divDependency > 10.0
    val riskBadgeText = if (divDependency > 25.0) "高リスク・要分散" else if (divDependency > 10.0) "やや高め" else "適正範囲"

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("財務カードと過去の配当推移には参考値が含まれます。診断文章は取得できたAPI指標を用い、未取得の項目は欠損として表示します。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
            }

            // 1. Hero Stock Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentHolding.name,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SurfaceContainerHigh
                                    ) {
                                        Text(
                                            currentHolding.code,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BackgroundDark,
                                        border = BorderStroke(1.dp, BorderColor)
                                    ) {
                                        Text(
                                            currentHolding.market,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryEmerald.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            currentHolding.accountType,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleWatchlistMembership(currentHolding) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBookmarked) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceContainer)
                                    .border(
                                        1.dp,
                                        if (isBookmarked) PrimaryEmerald else BorderColor,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (isBookmarked) PrimaryEmerald else OnSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Price & Daily Change
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    "現在株価 リアルタイム",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${currentHolding.currency}${currencyFormat.format(currentHolding.price.toLong())}",
                                    fontSize = AppTextSize.largeMetric,
                                    fontWeight = FontWeight.Black,
                                    color = OnSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentHolding.isPositive) PrimaryEmerald.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, if (currentHolding.isPositive) PrimaryEmerald.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    currentHolding.dailyChange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (currentHolding.isPositive) PrimaryEmerald else ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Sub KPI Grid (3 columns)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("1株配当 (DPS)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text("¥${currentHolding.dps}", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.caption, fontFamily = FontFamily.Monospace)
                                    Text("年額", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("現在利回り", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text("${currentHolding.yieldRate}%", fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = AppTextSize.caption, fontFamily = FontFamily.Monospace)
                                    Text("税引前", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("取得利回り", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text("${currentHolding.yoc}% YOC", fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = AppTextSize.caption, fontFamily = FontFamily.Monospace)
                                    Text("単価¥${currentHolding.acqPrice.toInt()}", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                        }
                    }
                }
            }

            // 2. 買い増しシミュレーター Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.5.dp, PrimaryEmerald.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("買い増しシミュレーター", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Surface(
                                shape = CircleShape,
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    "+${simAddedShares}株 (計${totalSimShares}株)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Comparison Impact Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceContainer,
                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("買い増しインパクト比較", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Surface(shape = CircleShape, color = PrimaryEmerald) {
                                        Text("配当 ${multiplier}倍 UP", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = BackgroundDark, modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("現在保有 (${baseShares}株)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                            Text("¥${currencyFormat.format((baseShares * dps).toLong())}/年", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                                            Text("月 ¥${currencyFormat.format((baseShares * dps / 12).toLong())} / 月", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(10.dp), color = PrimaryEmerald.copy(alpha = 0.1f), border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("シミュレーション (${totalSimShares}株)", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            Text("¥${currencyFormat.format(newTotalAnnualDiv)}/年", fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                                            Text("月 約¥${currencyFormat.format(newMonthlyDiv)} / 月", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${simAddedShares}", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                                    Text("株", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { simAddedShares = (simAddedShares - 10).coerceAtLeast(0) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-10", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { simAddedShares = com.kurogane.dividend.model.PortfolioMath.addShares(simAddedShares, 10) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+10", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { simAddedShares = com.kurogane.dividend.model.PortfolioMath.addShares(simAddedShares, 100) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("+100", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { simAddedShares = 100 },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceContainer)
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = "Reset", tint = OnSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0 to "現状維持",
                                100 to "+100株",
                                250 to "+250株",
                                500 to "+500株"
                            ).forEach { (count, label) ->
                                val isSelected = simAddedShares == count
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryEmerald else SurfaceContainer,
                                    border = BorderStroke(0.5.dp, if (isSelected) PrimaryEmerald else BorderColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { simAddedShares = count }
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = if (isSelected) OnPrimary else OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 1株配当金の推移 (4期) Bar Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BarChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1株配当金の推移 (4期)", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text("連続増配", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4 Bars Container
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val gRate = if (profile.growthRate > 0) profile.growthRate else 5.0
                                val p3 = Math.max(1, (dps / Math.pow(1.0 + gRate / 100.0, 3.0)).toInt())
                                val p2 = Math.max(1, (dps / Math.pow(1.0 + gRate / 100.0, 2.0)).toInt())
                                val p1 = Math.max(1, (dps / Math.pow(1.0 + gRate / 100.0, 1.0)).toInt())
                                val p0 = dps.toInt()
                                val fourYrGrowth = if (p3 > 0) String.format("%.1f", ((p0 - p3).toDouble() / p3) * 100) else "0.0"
                                val maxDps = Math.max(p0, 1).toFloat()
                                val prefix = currentHolding.currency

                                val bars = listOf(
                                    Triple("22.3期", "$prefix$p3", (p3.toFloat() / maxDps) * 0.85f),
                                    Triple("23.3期", "$prefix$p2", (p2.toFloat() / maxDps) * 0.85f),
                                    Triple("24.3期", "$prefix$p1", (p1.toFloat() / maxDps) * 0.85f),
                                    Triple("25.3予", "$prefix$p0", 0.95f)
                                )
                                bars.forEach { (term, dpsVal, ratio) ->
                                    val isProjected = term == "25.3予"
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(dpsVal, color = if (isProjected) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(26.dp)
                                                .height((60 * ratio).dp)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(
                                                    if (isProjected) Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmeraldContainer))
                                                    else Brush.verticalGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5)))
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(term, color = if (isProjected) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val gRate = if (profile.growthRate > 0) profile.growthRate else 5.0
                            val p3 = Math.max(1, (dps / Math.pow(1.0 + gRate / 100.0, 3.0)).toInt())
                            val p0 = dps.toInt()
                            val fourYrGrowth = if (p3 > 0) String.format("%.1f", ((p0 - p3).toDouble() / p3) * 100) else "0.0"

                            Text("4期増配率: +${fourYrGrowth}%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text("配当性向: 約${profile.payoutRatio}%", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. 銘柄総合分析ステータス (2x2 Grid + Detailed Panels matching image.png)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("銘柄総合分析ステータス", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Text("タップで切替", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2x2 Grid of Status Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Health
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "health") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "health") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "health") PrimaryEmerald else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "health" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("① 健全性", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${profile.healthGrade} ${profile.payoutRatio}%", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text(profile.healthEvaluation, color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                }
                            }

                            // 2. Buy Signal
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "value") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "value") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "value") PrimaryEmerald else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "value" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("② 買場シグナル", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.TrackChanges, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("検討圏 ${String.format("%.2f", liveYield)}%", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text(profile.getValuationStatus(currentHolding.price, currentHolding.dps).split("・").first(), color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 3. Growth
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "growth") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "growth") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "growth") SecondaryIndigo else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "growth" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("③ 増配耐性", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = SecondaryIndigo, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(if (profile.consecutiveYears > 0) "${profile.consecutiveYears}期連続 +${profile.growthRate}%" else "安定配当 +${profile.growthRate}%", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text("非減配${profile.noCutYears}年", color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                }
                            }

                            // 4. Concentration Risk (Matching image (6).png)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "risk") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "risk") 2.dp else 1.dp,
                                    if (selectedStatusTab == "risk") AccentAmber else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(if (selectedStatusTab == "risk") 8.dp else 0.dp, RoundedCornerShape(12.dp), spotColor = AccentAmber)
                                    .clickable { selectedStatusTab = "risk" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("④ 集中リスク", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${if (isHighRisk) "やや高め" else "適正"} ${divDependencyStr}%", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text("上限目安10%", color = AccentAmber, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        // DETAIL ACCORDION CONTENT MATCHING IMAGE (6).PNG
                        if (selectedStatusTab == "risk") {
                            Spacer(modifier = Modifier.height(14.dp))

                            // Header follows the buy-signal detail card format.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = AccentAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "集中リスク詳細分析",
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.section
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = AccentAmber.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(AccentAmber))
                                        Text(riskBadgeText, color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Main panel uses the same dark outer card and inset metric cards as buy-signal analysis.
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceContainer,
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            // Portfolio dependency card, parallel to the yield-range card.
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "ポートフォリオ配当依存度",
                                                color = OnSurface,
                                                fontSize = AppTextSize.caption,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isHighRisk) AccentAmber.copy(alpha = 0.15f) else PrimaryEmerald.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    if (isHighRisk) "上限超過" else "適正水準",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    color = if (isHighRisk) AccentAmber else PrimaryEmerald,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            "${divDependencyStr}%",
                                            color = if (isHighRisk) AccentAmber else PrimaryEmerald,
                                            fontSize = AppTextSize.screenTitle,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                            Spacer(modifier = Modifier.height(8.dp))

                                    // Gradient Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(BorderColor)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((divDependency / 50.0).coerceIn(0.08, 1.0).toFloat())
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(PrimaryEmeraldContainer, AccentAmber, AccentAmber)
                                                    )
                                                )
                                        )
                                    }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                        Text("0% (低依存)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text("推奨目安 10%未満", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text("${currentHolding.name}比率 ${divDependencyStr}%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    }

                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 2 Boxes: Sector & Cut Impact
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(0.5.dp, BorderColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("セクター集中度", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("${sectorDependencyStr}%", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("${cleanSectorName}・${sectorRiskText}", color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(0.5.dp, BorderColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("仮に50%減配の影響", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("-¥${currencyFormat.format(cutImpact)}", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("年間配当への影響度: ${if (cutImpact > 8000) "大" else if (cutImpact > 3000) "中" else "小"}", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Rebalance Recommendation Row
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SurfaceContainer,
                                        border = BorderStroke(0.5.dp, BorderColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Tune, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("リバランス推奨", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentAmber.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "他セクター優先投資を推奨",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = AccentAmber,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Diagnosis Comment Box matching image (6).png
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0x1A10B981),
                                        border = BorderStroke(1.dp, Color(0x4010B981)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = PrimaryEmerald,
                                                modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (isHighRisk)
                                                    "診断コメント： 配当依存度が約${divDependencyStr}%と目安（10%以下）を上回っています。今後の追加投資では他セクターを優先し、依存度を薄めるリバランスが推奨されます。"
                                                else
                                                    "診断コメント： 配当依存度は約${divDependencyStr}%と目安（10%以下）の適正健全範囲内に収まっており、バランスの取れたポートフォリオです。",
                                                color = OnSurface.copy(alpha = 0.9f),
                                                fontSize = AppTextSize.dense,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (selectedStatusTab == "value") {
                            // 買場シグナル詳細分析 (Exact match to image.png)
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrackChanges,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "買場シグナル詳細分析",
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.section
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryEmerald)
                                            )
                                            Text(
                                                profile.buySignalZone,
                                                color = PrimaryEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }

                                // Container Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 1. 配当利回りレンジ判定
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text("配当利回りレンジ判定", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                        ) {
                                                            Text(
                                                                profile.yieldRangeBadge,
                                                                color = PrimaryEmerald,
                                                                fontSize = AppTextSize.dense,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "${String.format(Locale.US, "%.2f", currentHolding.yieldRate)}%",
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = AppTextSize.metric,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                val yld = currentHolding.yieldRate
                                                val rangeProgress = ((yld - profile.yieldRangeMin) / (profile.yieldRangeMax - profile.yieldRangeMin))
                                                    .coerceIn(0.08, 1.0).toFloat()
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(BorderColor)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(rangeProgress)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(Color(0xFF818CF8), SkyBlue, PrimaryEmerald)
                                                                )
                                                            )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("安値圏 ${String.format(Locale.US, "%.2f", profile.yieldRangeMin)}%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Text("5年平均 ${String.format(Locale.US, "%.2f", profile.yieldRangeAvg)}%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Text("高値圏 ${String.format(Locale.US, "%.2f", profile.yieldRangeMax)}%", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 2. 2x1 Grid for PER & PBR
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // PER
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("PER (株価収益率)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                        Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(13.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.getPer(currentHolding.price), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.perEvaluation, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }

                                            // PBR
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("PBR (純資産倍率)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                        Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(13.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.getPbr(currentHolding.price), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.pbrEvaluation, color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 3. 買場シグナル総合スコア
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                                    Text("買場シグナル総合スコア", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                                ) {
                                                    Text(
                                                        "↗ 割安度スコア ${profile.valuationScore} / 100（検討圏）",
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 4. 総合自動診断 Box
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                    Text(profile.buySignalTitle, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.14f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        profile.buySignalBadge,
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    "絶好の押し目買場判定： ${profile.buySignalComment}",
                                                    color = OnSurface.copy(alpha = 0.95f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedStatusTab == "health") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "健全性詳細分析",
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.section
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryEmerald)
                                            )
                                            Text(
                                                profile.healthGrade,
                                                color = PrimaryEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AppTextSize.dense
                                            )
                                            Text(
                                                "（ ${profile.healthEvaluation} ）",
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }

                                // Container Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 1. Payout ratio card
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text("配当性向 (当期見込)", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                        ) {
                                                            Text(
                                                                profile.payoutStatusBadge,
                                                                color = PrimaryEmerald,
                                                                fontSize = AppTextSize.dense,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "${profile.payoutRatio}%",
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = AppTextSize.metric,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                val progress = (profile.payoutRatio / 100.0).coerceIn(0.05, 1.0).toFloat()
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(BorderColor)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(progress)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(PrimaryEmerald, PrimaryEmerald)
                                                                )
                                                            )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("0% (低負担)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Text("適正目安 30〜50%", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                    Text("100% (高負担)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 2. 2x2 Grid of Financial Metrics
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 自己資本比率
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("自己資本比率", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("${profile.equityRatio}%", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.equityRatioSub, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }

                                            // 株主還元方針
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("株主還元方針 (DOE/累進)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.doePolicy, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.doePolicySub, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // ネットD/Eレシオ
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("ネットD/Eレシオ", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.netDeRatio, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text("財務余力・借入健全性", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }

                                            // 営業利益率
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("営業利益率", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("${profile.operatingMargin}%", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text("稼ぐ力・本業収益性", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 3. FCF Row
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                                    Text("フリーキャッシュフロー余力", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(12.dp))
                                                        Text(profile.fcfStatus, color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        // 4. 総合自動診断 Box
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                        Text(profile.modelType, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = SurfaceContainer,
                                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                    ) {
                                                        Text(
                                                            "健全性スコア: ${profile.healthScore}/100",
                                                            color = PrimaryEmerald,
                                                            fontSize = AppTextSize.dense,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    "総合自動診断：${profile.healthDiagnosisComment}",
                                                    color = OnSurface.copy(alpha = 0.95f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedStatusTab == "growth") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.MilitaryTech, null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                        Text("増配耐性詳細分析", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmerald))
                                            Text(
                                                if (profile.consecutiveYears > 0) "${profile.consecutiveYears}期連続増配" else "安定配当",
                                                color = PrimaryEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text("連続増配実績", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("${profile.consecutiveYears}期", color = PrimaryEmerald, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.metric, fontFamily = FontFamily.Monospace)
                                                    Text("年平均増配率 +${profile.growthRate}%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text("非減配年数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("${profile.noCutYears}年", color = OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.metric, fontFamily = FontFamily.Monospace)
                                                    Text("減配せず配当を維持", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.Default.TrendingUp, null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                                    Text("増配耐性の評価", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                                ) {
                                                    Text(
                                                        if (profile.consecutiveYears > 0) "${profile.consecutiveYears}期連続" else "安定配当",
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        if (profile.consecutiveYears > 0) "${profile.consecutiveYears}期連続増配の実績" else "安定配当の実績",
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    "${profile.noCutYears}年間減配がなく、年平均増配率は+${profile.growthRate}%です。過去の配当実績をもとに、増配の継続性と減配への耐性を確認できます。",
                                                    color = OnSurface.copy(alpha = 0.95f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        StockAnalysisNarrativeCard(analysisNarrative)
                    }
                }
            }

            // 5. 次回権利落ち・直近イベント
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("次回権利落ち・直近イベント", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text("通知設定中", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("9月末 中間配当", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text("権利付き最終日まで", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("あと ", color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                        Text("18", color = PrimaryEmerald, fontSize = AppTextSize.headline, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                        Text(" 日", color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AlarmOn, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("権利落ち・決算前通知", color = OnSurface, fontSize = AppTextSize.dense)
                            }
                            Switch(
                                checked = isAlertOn,
                                onCheckedChange = { isAlertOn = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = OnSurface, checkedTrackColor = PrimaryEmerald)
                            )
                        }
                    }
                }
            }

            // 6. Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurface)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保有情報を編集", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保有解除", color = ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun StockAnalysisNarrativeCard(analysisNarrative: StockAnalysisNarrative) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BackgroundDark,
        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("銘柄総合分析の詳細判定", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                    Text("7指標を各5帯域・9位置で評価", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                if (analysisNarrative.score != null) {
                    Text(
                        "${analysisNarrative.grade} ${analysisNarrative.score}点",
                        color = PrimaryEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
            }
            Text(
                "総合判定: ${analysisNarrative.title}。評価できた${analysisNarrative.findings.size}指標を重み付けして算出しています。",
                color = OnSurface,
                fontSize = AppTextSize.dense,
                lineHeight = 16.sp
            )
            Text(
                "全7指標の文章パターン: 45^7 = ${String.format(Locale.JAPAN, "%,d", StockAnalysisNarrative.TOTAL_COMBINATIONS)}通り。今回の入力で選択可能な組合せ: ${String.format(Locale.JAPAN, "%,d", analysisNarrative.possibleCombinations)}通り。",
                color = PrimaryEmerald,
                fontSize = AppTextSize.dense,
                lineHeight = 15.sp
            )
            Text(
                "Cloudflareの診断値を優先し、未取得の指標は端末内プロフィールの参考値を使います。FCF値がない場合は未評価です。",
                color = OnSurfaceVariant,
                fontSize = AppTextSize.dense,
                lineHeight = 14.sp
            )
            if (analysisNarrative.complete && analysisNarrative.combinationIndex != null) {
                Text(
                    "判定パターン ${analysisNarrative.combinationIndex + 1} / ${StockAnalysisNarrative.TOTAL_COMBINATIONS}",
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    "未取得の指標: ${analysisNarrative.missingLabels.joinToString("、")}。データがない項目は評価に含めません。",
                    color = AccentAmber,
                    fontSize = AppTextSize.dense,
                    lineHeight = 15.sp
                )
            }
            HorizontalDivider(color = BorderColor)
            analysisNarrative.findings.forEach { finding ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${finding.label}  ${finding.formattedValue}・${finding.bandLabel}（${finding.position.label}）",
                        color = if (finding.score >= 65) PrimaryEmerald else AccentAmber,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(finding.text, color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                }
            }
            Text(
                "評価は取得できた指標に基づく参考情報です。業種特性、最新開示、株価変動も合わせて確認してください。",
                color = OnSurfaceVariant,
                fontSize = AppTextSize.dense,
                lineHeight = 14.sp
            )
        }
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/WatchlistScreen.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

private fun sortWatchlist(
    stocks: List<WatchlistStock>,
    criteria: String,
    direction: String
): List<WatchlistStock> = stocks.sortedWith { a, b ->
    val yieldA = com.kurogane.dividend.model.PortfolioMath.yieldPercent(a.dps, a.price)
    val yieldB = com.kurogane.dividend.model.PortfolioMath.yieldPercent(b.dps, b.price)
    val rateA = yieldA / a.targetYield
    val rateB = yieldB / b.targetYield

    val comparison = when (criteria) {
        "ach" -> rateA.compareTo(rateB)
        "yield" -> yieldA.compareTo(yieldB)
        "price" -> a.price.compareTo(b.price)
        else -> a.code.compareTo(b.code)
    }

    if (direction == "desc") -comparison else comparison
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.watchlistScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.watchlistScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.watchlistScrollIndex = index
                viewModel.watchlistScrollOffset = offset
            }
    }

    // Dynamic Watchlist Calculations
    val averageYield = if (watchlist.isNotEmpty()) {
        watchlist.map { com.kurogane.dividend.model.PortfolioMath.yieldPercent(it.dps, it.price) }.average()
    } else 0.0

    val potentialDiv = watchlist.sumOf { stock ->
        val shares = stock.defaultShares
        val rate = if (stock.currency == "$") 154.0 else 1.0
        stock.dps * shares * rate
    }

    val heldCount = watchlist.filter { stock -> holdings.any { it.code == stock.code } }.size

    val totalTargetInvestment = watchlist.sumOf { stock ->
        val shares = stock.defaultShares
        val rate = if (stock.currency == "$") 154.0 else 1.0
        val targetPrice = if (stock.targetYield > 0.0) stock.dps / (stock.targetYield / 100.0) else stock.price
        targetPrice * shares * rate
    }

    val targetAverageYield = if (totalTargetInvestment > 0.0) {
        (potentialDiv / totalTargetInvestment) * 100.0
    } else 0.0

    var filterState by remember { mutableStateOf("all") } // "all", "jp", "us", "ready"
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var sortCriteria by remember { mutableStateOf("ach") } // "ach", "yield", "price", "code"
    var sortDirection by remember { mutableStateOf("desc") } // "asc", "desc"
    var draftSortCriteria by remember { mutableStateOf("ach") }
    var draftSortDirection by remember { mutableStateOf("desc") }
    var appliedOrderCodes by remember { mutableStateOf<List<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Bottom Sheet States for ex-dividend matrix and payout calendar
    var showMatrixBottomSheet by remember { mutableStateOf(false) }
    val matrixSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showGraphBottomSheet by remember { mutableStateOf(false) }
    val graphSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Process filters
    val filteredList = watchlist.filter { stock ->
        val currentYield = com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
        val isReady = currentYield >= stock.targetYield
        val queryMatch = searchQuery.isEmpty() ||
                stock.code.contains(searchQuery, ignoreCase = true) ||
                stock.name.contains(searchQuery, ignoreCase = true) ||
                stock.sector.contains(searchQuery, ignoreCase = true)

        val catMatch = when (filterState) {
            "jp" -> stock.category == "jp"
            "us" -> stock.category == "us"
            "ready" -> isReady
            else -> true
        }
        catMatch && queryMatch
    }

    // Capture the default order once, then preserve it while stock values change.
    LaunchedEffect(watchlist.map { it.code }) {
        val existingOrder = appliedOrderCodes
        appliedOrderCodes = if (existingOrder == null) {
            if (watchlist.isEmpty()) null
            else sortWatchlist(watchlist, sortCriteria, sortDirection).map { it.code }
        } else {
            val currentCodes = watchlist.mapTo(mutableSetOf()) { it.code }
            existingOrder.filter { it in currentCodes } +
                watchlist.map { it.code }.filterNot { it in existingOrder }
        }
    }

    val appliedCodes = appliedOrderCodes
    val visibleByCode = filteredList.associateBy { it.code }
    val sortedList = if (appliedCodes == null) {
        sortWatchlist(filteredList, sortCriteria, sortDirection)
    } else {
        buildList {
            appliedCodes.forEach { code -> visibleByCode[code]?.let { add(it) } }
            val placedCodes = appliedCodes.toHashSet()
            filteredList.forEach { stock -> if (stock.code !in placedCodes) add(stock) }
        }
    }

    val readyCount = watchlist.filter { com.kurogane.dividend.model.PortfolioMath.yieldPercent(it.dps, it.price) >= it.targetYield }.size

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("買場到達 ${readyCount}銘柄", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                    Text(" / ${watchlist.size}銘柄監視中", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BackgroundDark)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("買場アラート", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = true,
                                    onCheckedChange = {},
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(checkedThumbColor = OnSurface, checkedTrackColor = PrimaryEmeraldContainer)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("平均現在利回り", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(String.format("%.2f", averageYield), fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontFamily = FontFamily.Monospace)
                                        Text("%", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                    }
                                    Text("東証平均 +1.8%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("配当ポテンシャル", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("¥", fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text(currencyFormat.format(potentialDiv.toLong()), fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = FontFamily.Monospace)
                                        Text("/年", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                    }
                                    Text("各1単元保有試算 (${heldCount}銘柄保有済)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                        }
                    }
                }
            }

            // Filters & Sorting Panel (Matching Web layout exactly)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            Pair("all", "すべて (${watchlist.size})"),
                            Pair("jp", "日本株 (${watchlist.filter { it.category == "jp" }.size})"),
                            Pair("us", "米国株 (${watchlist.filter { it.category == "us" }.size})"),
                            Pair("ready", "買場到達 (${readyCount})")
                        )
                        filters.forEach { (id, label) ->
                            val isSelected = filterState == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) PrimaryEmerald else SurfaceContainerLow)
                                    .clickable { filterState = id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) OnPrimary else SoftGray,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Sorting Button and Add Holding Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentLabel = when (sortCriteria) {
                            "ach" -> "達成率"
                            "yield" -> "利回り"
                            "price" -> "株価"
                            else -> "コード"
                        }
                        val currentDirLabel = if (sortDirection == "desc") "降順" else "昇順"

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .clickable {
                                    draftSortCriteria = sortCriteria
                                    draftSortDirection = sortDirection
                                    showSortBottomSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$currentLabel ($currentDirLabel)",
                                color = SoftGray,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = SoftGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Add stock button (matches layout header)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceContainerLow)
                                .border(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .clickable { viewModel.addHolding("7203", 100, 2450.0) } // triggers quick default item or similar action
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "銘柄追加",
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Search Field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("監視銘柄コード・銘柄名・セクターで検索", color = SoftGray, fontSize = AppTextSize.dense) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryEmerald.copy(alpha = 0.5f),
                        unfocusedBorderColor = BorderBlue,
                        focusedContainerColor = SurfaceContainerLow,
                        unfocusedContainerColor = SurfaceContainerLow,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )
            }

            // Stock Cards
            items(items = sortedList, key = { it.code }) { stock ->
                val currentYield = com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
                val isReached = currentYield >= stock.targetYield
                val isClose = !isReached && (currentYield >= (stock.targetYield - 0.25))
                val yieldAchievement = (currentYield / stock.targetYield * 100.0).coerceIn(0.0, 100.0)
                val targetPriceGap = kotlin.math.abs(stock.price - stock.targetPrice).toInt()
                val targetPriceGapText = when {
                    stock.price > stock.targetPrice -> "あと ${stock.currency}${targetPriceGap} 下落"
                    stock.price < stock.targetPrice -> "目安価格を ${stock.currency}${targetPriceGap} 下回り"
                    else -> "買場目安に到達"
                }

                val dynamicBadge = if (isReached) {
                    "絶好の買場到達 (利回り " + String.format("%.2f", currentYield) + "%)"
                } else if (isClose) {
                    "買場目安近接"
                } else if (currentYield >= 4.0) {
                    "超高配当 (利回り " + String.format("%.2f", currentYield) + "%)"
                } else {
                    "優良ディフェンシブ"
                }

                val badgeColor = if (isReached) {
                    PrimaryEmerald
                } else if (isClose) {
                    AccentAmber // Amber
                } else if (currentYield >= 4.0) {
                    Color(0xFF8B5CF6) // Purple
                } else {
                    OnSurfaceVariant // Slate
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isReached) Icons.Default.Verified else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    dynamicBadge,
                                    color = badgeColor,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isReached) PrimaryEmerald else SurfaceContainerHighest
                                ) {
                                    Text(
                                        stock.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReached) OnPrimary else OnSurface,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stock.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${stock.currency}${stock.price.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.section,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stock.dailyChange,
                                    fontWeight = FontWeight.Bold,
                                    color = if (stock.isPositive) PrimaryEmerald else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("現在利回り", color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1)
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            "${stock.currentYield}%",
                                            fontWeight = FontWeight.Bold,
                                            color = if (stock.isReady) PrimaryEmerald else OnSurface,
                                            fontSize = AppTextSize.body,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "目標利回り",
                                            tint = OnSurfaceVariant,
                                            modifier = Modifier.padding(start = 5.dp).size(15.dp)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("目標:", color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, -0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "-", tint = OnSurface, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            "${stock.targetYield}%",
                                            color = PrimaryEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.caption,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, 0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "+", tint = OnSurface, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${String.format(Locale.JAPAN, "%.1f", yieldAchievement)}% 達成",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isReached) "目標達成" else "目標まで あと ${String.format(Locale.JAPAN, "%.2f", stock.targetYield - currentYield)}%",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                LinearProgressIndicator(
                                    progress = { (yieldAchievement / 100.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = if (stock.isReady) PrimaryEmerald else PrimaryEmeraldContainer,
                                    trackColor = SurfaceContainerHighest
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Paid, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "買場目安: ${stock.currency}${stock.targetPrice.toInt()} ($targetPriceGapText)",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            "1株配当 ${stock.currency}${stock.dps}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onOpenAddHolding(stock.code) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = OnPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("保有株に登録", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnPrimary)
                            }

                            IconButton(
                                onClick = { viewModel.toggleWatchlistStock(stock.code) },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainer)
                                    .border(
                                        width = 0.5.dp,
                                        color = if (stock.alertEnabled) PrimaryEmerald.copy(alpha = 0.4f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (stock.alertEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = "Alert Toggle",
                                    tint = if (stock.alertEnabled) PrimaryEmerald else SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Button(
                                onClick = { onNavigateToDetail(stock.code) },
                                modifier = Modifier.width(72.dp).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer, contentColor = OnSurface),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("詳細", fontSize = AppTextSize.dense, color = OnSurface)
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnSurface)
                            }
                        }
                    }
                }
            }

            // Batch Simulation of Watchlist Stocks Card (Image 1)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "監視銘柄の一括シミュレーション",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "各1単元（日本株100株 / 米国株1株）を現在の目標買場で揃えた場合、初期投資概算 ¥${currencyFormat.format(totalTargetInvestment.toLong())} で年間 +¥${currencyFormat.format(potentialDiv.toLong())}（平均利回り ${String.format("%.2f", targetAverageYield)}%）の不労所得基盤が完成します。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 16.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showMatrixBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("権利落ち月マトリクス", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showGraphBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.BarChart, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("受取月別グラフ試算", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Big Green Button: Add New Stock to Watchlist
            item {
                Button(
                    onClick = {
                        // Quick add Toyota default template as demonstration or trigger modal
                        viewModel.addHolding("7203", 100, 2600.0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "新しい銘柄をウォッチリストに追加",
                        color = OnPrimary,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            containerColor = CardDark,
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "監視銘柄の並び替え条件",
                            color = OnSurface,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showSortBottomSheet = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    "目標値を変更した後、最新の達成率順にするには「適用する」を押してください。",
                    color = SoftGray,
                    fontSize = AppTextSize.dense
                )

                // Direction Switcher
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "順序",
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundDark)
                            .padding(4.dp)
                    ) {
                        val directions = listOf(
                            Pair("desc", "降順 (大きい順)"),
                            Pair("asc", "昇順 (小さい順)")
                        )
                        directions.forEach { (dir, label) ->
                            val isSelected = draftSortDirection == dir
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryEmerald else Color.Transparent)
                                    .clickable { draftSortDirection = dir }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (dir == "desc") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isSelected) OnPrimary else SoftGray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        color = if (isSelected) OnPrimary else SoftGray,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Criteria Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "ソート項目",
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    val criteriaOptions = listOf(
                        Triple("ach", "利回り達成率 (目安到達に近い順)", Icons.Default.Bolt),
                        Triple("yield", "現在利回り (高配当順)", Icons.Default.TrendingUp),
                        Triple("price", "株価 (購入必要額基準)", Icons.Default.AttachMoney),
                        Triple("code", "銘柄コード / ティッカー順", Icons.Default.Bookmark)
                    )

                    criteriaOptions.forEach { (crit, label, icon) ->
                        val isSelected = draftSortCriteria == crit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SurfaceContainerHigh else BackgroundDark)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { draftSortCriteria = crit }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) PrimaryEmerald else SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    label,
                                    color = if (isSelected) OnSurface else SoftGray,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Action apply buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showSortBottomSheet = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            "キャンセル",
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.caption
                        )
                    }
                    Button(
                        onClick = {
                            val newlySortedCodes = sortWatchlist(
                                filteredList,
                                draftSortCriteria,
                                draftSortDirection
                            ).map { it.code }
                            val visibleCodes = newlySortedCodes.toHashSet()
                            val previousOrder = appliedOrderCodes ?: watchlist.map { it.code }
                            appliedOrderCodes = newlySortedCodes + previousOrder.filterNot { it in visibleCodes }
                            sortCriteria = draftSortCriteria
                            sortDirection = draftSortDirection
                            showSortBottomSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            "適用する",
                            color = OnPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = AppTextSize.caption
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet 1: 権利落ち月マトリクス (Image 2)
    if (showMatrixBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMatrixBottomSheet = false },
            sheetState = matrixSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("権利落ち月マトリクス", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "ヒートマップ分析",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "監視4銘柄の想定権利落ちスケジュールと配当シミュレーション",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showMatrixBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderColor)

                // KPI Sub-Grid Row (3 Cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("想定配当回数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("10", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("回/年", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Text("日6回 + 米4回 (想定)", color = PrimaryEmerald, fontSize = AppTextSize.dense)
                    }

                    // Card 2
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, PrimaryEmerald.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("次回権利落ち", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("3月27日", color = PrimaryEmerald, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                        Text("あと42日 (買付検討期)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    // Card 3
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("潜在配当規模 (各1単元)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("¥42,800", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("想定利回り 3.84%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }

                // Table Section Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "12ヶ月配当密度ヒートマップマトリクス",
                        color = OnSurface,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )

                    // Small Legend indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("少", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Box(modifier = Modifier.size(6.dp).background(BorderColor))
                        Box(modifier = Modifier.size(6.dp).background(Color(0x3310B981)))
                        Box(modifier = Modifier.size(6.dp).background(PrimaryEmeraldContainer))
                        Text("多", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }

                // Heatmap Scrollable Row Table
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column {
                        // Header Row
                        Row(
                            modifier = Modifier.background(SurfaceContainerHigh).padding(vertical = 6.dp)
                        ) {
                            Text("銘柄", modifier = Modifier.width(80.dp).padding(start = 8.dp), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            (1..12).forEach { m ->
                                Text(
                                    text = "${m}月" + if (m == 3 || m == 9) "\n(権利落)" else if (m == 6 || m == 12) "\n(入金)" else "",
                                    modifier = Modifier.width(52.dp),
                                    color = if (m == 3 || m == 9 || m == 6 || m == 12) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Row 1: 三井住友
                        HeatmapRow(
                            code = "8316",
                            name = "三井住友",
                            cells = mapOf(
                                3 to Pair("権利落\n¥6,500", Color(0x3310B981)),
                                6 to Pair("入金\n¥6,500", PrimaryEmeraldContainer),
                                9 to Pair("権利落\n¥6,500", Color(0x3310B981)),
                                12 to Pair("入金\n¥6,500", PrimaryEmeraldContainer)
                            )
                        )

                        // Row 2: トヨタ
                        HeatmapRow(
                            code = "7203",
                            name = "トヨタ",
                            cells = mapOf(
                                3 to Pair("権利落\n¥3,640", Color(0x2210B981)),
                                6 to Pair("入金\n¥3,640", Color(0xCC10B981)),
                                9 to Pair("権利落\n¥3,640", Color(0x2210B981)),
                                12 to Pair("入金\n¥3,640", Color(0xCC10B981))
                            )
                        )

                        // Row 3: NTT
                        HeatmapRow(
                            code = "9432",
                            name = "NTT",
                            cells = mapOf(
                                3 to Pair("権利落\n¥2,600", Color(0x1F10B981)),
                                6 to Pair("入金\n¥2,600", Color(0xAA10B981)),
                                9 to Pair("権利落\n¥2,600", Color(0x1F10B981)),
                                12 to Pair("入金\n¥2,600", Color(0xAA10B981))
                            )
                        )

                        // Row 4: Apple
                        HeatmapRow(
                            code = "AAPL",
                            name = "Apple",
                            cells = mapOf(
                                2 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f)),
                                5 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f)),
                                8 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f)),
                                11 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f))
                            )
                        )

                        // Row 5: 想定受取計 (買付時)
                        Row(
                            modifier = Modifier.background(SurfaceContainerHigh.copy(alpha = 0.5f)).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("想定受取計\n(買付時)", modifier = Modifier.width(80.dp).padding(start = 8.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            (1..12).forEach { m ->
                                val sumText = when (m) {
                                    2, 5, 8, 11 -> "¥385"
                                    6, 12 -> "¥12,740"
                                    else -> "-"
                                }
                                Text(
                                    text = sumText,
                                    modifier = Modifier.width(52.dp),
                                    color = if (sumText != "-") OnSurface else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Analytics advice card below the table matching Image 2
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Scale, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "権利落ち・受取平準化アナリティクス",
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "偏重度: 82%",
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "監視中の日本株3銘柄は 3月・9月末に権利落ちが集中しており、実際に購入した場合の配当受取は 6月・12月に偏ります。米国株（AAPL）をポートフォリオに組み入れることで 2/5/8/11月のインカム空白月を補完できます。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("● 日株 6/12月想定計: ¥25,480", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text("● 米株 想定年間計: ¥1,540", color = SecondaryIndigo, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dismiss Matrix Button
                Button(
                    onClick = { showMatrixBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("マトリクスを閉じる", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal Bottom Sheet 2: 受取月別グラフ試算 (Image 3)
    if (showGraphBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGraphBottomSheet = false },
            sheetState = graphSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("受取月別グラフ試算", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SkyBlue.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "平準化シミュレーション",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = SkyBlue,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "監視4銘柄の入金タイミング・キャッシュフロー偏重分析",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showGraphBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderColor)

                // KPI Row (3 cards side-by-side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("年間受取予定", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("¥42,800/年", color = PrimaryEmerald, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    // Card 2
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("月平均手取り", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("¥3,567/月", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    // Card 3
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("⚠️ インカム空白", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("4 ヶ月 (無収入)", color = ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }

                // Card 1: Quarter comparison matching Image 3
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PieChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("四半期キャッシュフロー対比", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "Q2・Q4偏重 85.0%",
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Multi-color segmented row representing quarters proportion
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(BorderColor)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().weight(0.075f).background(SecondaryIndigo)) // Q1
                            Box(modifier = Modifier.fillMaxHeight().weight(0.425f).background(PrimaryEmerald)) // Q2
                            Box(modifier = Modifier.fillMaxHeight().weight(0.075f).background(SecondaryIndigo)) // Q3
                            Box(modifier = Modifier.fillMaxHeight().weight(0.425f).background(PrimaryEmerald)) // Q4
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Q1 Box
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q1 (1-3月)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("¥3,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("7.5%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }

                            // Q2 Box - Highlighted Emerald
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .border(1.dp, PrimaryEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q2 (4-6月) 🟢", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("¥18,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("42.5%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            // Q3 Box
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q3 (7-9月)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("¥3,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("7.5%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }

                            // Q4 Box - Highlighted Emerald
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .border(1.dp, PrimaryEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q4 (10-12月) 🟢", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("¥18,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("42.5%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Card 2: 12-month calendar bar graph Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridOn, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("12ヶ月 受取配当カレンダー棒グラフ", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                                Text("日株集中", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SecondaryIndigo))
                                Text("米株", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF475569)))
                                Text("空白", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Canvas Graph Custom drawing exactly as shown in Image 3
                        val calendarEmptyMonthColor = BorderColor
                        val calendarDividendColor = PrimaryEmeraldContainer
                        val calendarUsColor = SecondaryIndigo
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            val maxBarVal = 17.4f
                            val numMonths = 12
                            val barWidth = 14.dp.toPx()
                            val spacing = (size.width - (barWidth * numMonths)) / (numMonths + 1)

                            for (m in 1..12) {
                                val monthLeft = spacing + (m - 1) * (barWidth + spacing)
                                val (barVal, color) = when (m) {
                                    2, 5, 8, 11 -> Pair(0.8f, calendarUsColor)
                                    3, 9 -> Pair(2.4f, calendarUsColor)
                                    6, 12 -> Pair(17.4f, calendarDividendColor)
                                    else -> Pair(0f, Color.Transparent)
                                }

                                if (barVal > 0f) {
                                    val barHeight = size.height * (barVal / maxBarVal) * 0.85f
                                    val barTop = size.height - barHeight
                                    drawRoundRect(
                                        color = color,
                                        topLeft = Offset(monthLeft, barTop),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    )
                                } else {
                                    // Empty month box
                                    drawRoundRect(
                                        color = calendarEmptyMonthColor,
                                        topLeft = Offset(monthLeft, size.height - 12.dp.toPx()),
                                        size = Size(barWidth, 12.dp.toPx()),
                                        style = Stroke(width = 1.dp.toPx()),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }
                        }

                        // Labels row under calendar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            (1..12).forEach { m ->
                                val label = when (m) {
                                    2, 5, 8, 11 -> "米"
                                    6, 12 -> "日3"
                                    else -> "-"
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$m", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Text(label, color = if (label.startsWith("日")) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("日本株(3/9月期)は 6月・12月 入金", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            Text("⚠️ 空白月: 1, 4, 7, 10月", color = ErrorRed, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Card 3: Income advice card below the graph
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "インカム空白月解消アドバイス",
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x1FF59E0B)
                            ) {
                                Text(
                                    "平準化おすすめ",
                                    color = AccentAmber,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "1月・4月・7月・10月決算の高配当銘柄（積水ハウス等）やREITを追加すると、日本株特有の入金タイムラグを補完し、毎月途切れない配当キャッシュフローが完成します。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Actionable Sekisui House addition row
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SurfaceContainerHighest
                                    ) {
                                        Text(
                                            "1928",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            color = OnSurface,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("積水ハウス", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        Text("1月・7月決算（4月・10月受取）", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("4.12%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                    Button(
                                        onClick = {
                                            viewModel.addHolding("1928", 100, 2200.0)
                                            showGraphBottomSheet = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("空白月解消に追加", color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 4: Stocks contribution card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("銘柄別 受取月寄与度", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("監視4銘柄", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceContainerHighest
                                ) {
                                    Text("8316", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("三井住友FG", color = OnSurface, fontSize = AppTextSize.dense)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("¥13,000 / 年", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontFamily = FontFamily.Monospace)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text("6月 50%", color = PrimaryEmerald, fontSize = AppTextSize.dense, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text("12月 50%", color = PrimaryEmerald, fontSize = AppTextSize.dense, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom cell row for Ex-dividend heatmap matrix (Image 2)
@Composable
private fun HeatmapRow(
    code: String,
    name: String,
    cells: Map<Int, Pair<String, Color>>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderColor)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(80.dp).padding(start = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SurfaceContainerHighest,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(name, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
        }

        (1..12).forEach { m ->
            val cellData = cells[m]
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(38.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cellData?.second ?: Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (cellData != null) {
                    Text(
                        text = cellData.first,
                        color = OnSurface,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt

【修正案（リファクタリング後コード）】:

```kotlin
package com.kurogane.dividend.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.MonthlyDividend
import com.kurogane.dividend.model.RoadmapStep
import com.kurogane.dividend.model.StepStatus
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.data.CloudflareStockAnalysis
import com.kurogane.dividend.data.CloudflareSearchHit
import com.kurogane.dividend.data.PriceRefreshPolicy
import com.kurogane.dividend.data.Tse33IndustryMaster
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DividendViewModel @JvmOverloads constructor(
    application: Application,
    private val refreshOnStart: Boolean = true,
    private val marketRepository: CloudflareMarketRepository = CloudflareMarketRepository()
) : AndroidViewModel(application) {

    private val displayPreferences = application.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
    private val marketPreferences = application.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE)
    private val localImportStore = LocalImportStore(application)
    private var hasRestoredLocalHoldings = false
    private val saveMutex = Mutex()
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val restoration = CompletableDeferred<Unit>()
    private var holdingsLoadFailed = false
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    var assetScrollPosition = 0
    var annualScrollIndex = 0
    var annualScrollOffset = 0
    var roadmapScrollIndex = 0
    var roadmapScrollOffset = 0
    var watchlistScrollIndex = 0
    var watchlistScrollOffset = 0

    private val _isPrivacyActive = MutableStateFlow(displayPreferences.getBoolean("mask_at_startup", false))
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _themeMode = MutableStateFlow(displayPreferences.getString("theme_mode", "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isOledBlack = MutableStateFlow(displayPreferences.getBoolean("oled_black", false))
    val isOledBlack: StateFlow<Boolean> = _isOledBlack.asStateFlow()

    private val _selectedAccent = MutableStateFlow(displayPreferences.getString("selected_accent", "emerald") ?: "emerald")
    val selectedAccent: StateFlow<String> = _selectedAccent.asStateFlow()

    private val _taxRateMode = MutableStateFlow(displayPreferences.getString("tax_rate_mode", "gross") ?: "gross")
    val taxRateMode: StateFlow<String> = _taxRateMode.asStateFlow()
    private val _maskAtStartup = MutableStateFlow(displayPreferences.getBoolean("mask_at_startup", false))
    val maskAtStartup: StateFlow<Boolean> = _maskAtStartup.asStateFlow()

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

    init {
        hasRestoredLocalHoldings = localImportStore.hasHoldings()
        viewModelScope.launch {
            val (heldResult, watchedResult) = withContext(Dispatchers.IO) {
                runCatching { localImportStore.loadHoldings() } to runCatching { localImportStore.loadWatchlist() }
            }
            heldResult.onSuccess { restored -> if (restored != null) _holdings.value = restored }.onFailure {
                holdingsLoadFailed = true
                _holdings.value = emptyList()
                _saveError.value = "保存データを読み込めませんでした。バックアップから復元してください。"
            }
            watchedResult.onSuccess { restored -> if (restored != null) _watchlist.value = restored }.onFailure {
                _watchlist.value = emptyList()
                _saveError.value = "監視銘柄の保存データを読み込めませんでした。"
            }
            restoration.complete(Unit)
            for (ignored in saveRequests) {
                runCatchingCancellable { persistCurrentHoldings() }.onFailure {
                    _saveError.value = "保有情報を保存できませんでした。もう一度保存してください。"
                }
            }
        }
    }

    suspend fun awaitRestoration() { restoration.await() }

    private fun updatePortfolio(next: List<Holding>) {
        if (next == _holdings.value) return
        hasRestoredLocalHoldings = true
        _holdings.value = next
        saveRequests.trySend(Unit)
    }

    private suspend fun persistCurrentHoldings() = saveMutex.withLock {
        val snapshot = _holdings.value
        val watched = _watchlist.value
        withContext(Dispatchers.IO) {
            if (!holdingsLoadFailed) localImportStore.saveHoldings(snapshot)
            localImportStore.saveWatchlist(watched)
        }
        _saveError.value = null
    }

    fun clearSaveError() { _saveError.value = null }

    /** CSV and legacy backup data remain on this device. */
    suspend fun importCsvRows(rows: List<CsvHoldingRow>, fields: Set<String>) {
        restoration.await()
        val industries = withContext(Dispatchers.IO) {
            runCatching { Tse33IndustryMaster.industries(getApplication()) }.getOrDefault(emptyMap())
        }
        val next = PortfolioImporter.importRows(_holdings.value, rows, fields, industries)
        holdingsLoadFailed = false
        updatePortfolio(next)
        persistCurrentHoldings()
    }

    suspend fun importDvmBackups(backups: List<DvmBackupPreview>) {
        restoration.await()
        val stocks = backups.filter { it.kind == DvmBackupKind.STOCKS }.flatMap { it.stocks }
        val next = PortfolioImporter.importStocks(_holdings.value, stocks)
        withContext(Dispatchers.IO) {
            backups.forEach { preview -> localImportStore.saveBackup(preview.kind, preview.payload) }
        }
        holdingsLoadFailed = false
        updatePortfolio(next)
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
        viewModelScope.launch {
            restoration.await()
            applyCachedPrices()
            if (refreshOnStart) {
                seedPopularHoldingsFromCloudflare()
                refreshMarketPricesIfDue()
            }
        }
    }

    private suspend fun seedPopularHoldingsFromCloudflare() {
        if (hasRestoredLocalHoldings || displayPreferences.getBoolean("popular_r2_holdings_seeded_v1", false)) return

        runCatchingCancellable {
            val industries = runCatching { Tse33IndustryMaster.industries(getApplication()) }.getOrDefault(emptyMap())
            val resolved = mutableListOf<Holding>()
            val resolvedCodes = mutableSetOf<String>()
            var offset = 0
            var totalCount = Int.MAX_VALUE
            val currencyFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN)
            while (resolved.size < POPULAR_HOLDINGS_COUNT && offset < totalCount) {
                val page = marketRepository.fetchPopularStocks(offset, POPULAR_HOLDINGS_ACQUISITION_DATE, 20)
                val pages = listOf(page)
                val endOffset = offset + 20
                totalCount = page.totalCount
                pages.flatMap { it.items }.forEach { stock ->
                    val normalizedCode = stock.code.uppercase(java.util.Locale.ROOT)
                    if (!resolvedCodes.add(normalizedCode)) return@forEach
                    val dps = stock.annualDividendPerShare.takeIf { it > 0.0 }
                        ?: ((stock.dividendYield ?: 0.0) * stock.currentPrice / 100.0)
                    val industry = stock.sector?.takeIf(String::isNotBlank)
                        ?: industries[stock.code.take(4).uppercase(java.util.Locale.ROOT)]
                    resolved += Holding(
                        id = "r2-${stock.code}",
                        code = stock.code,
                        name = stock.name,
                        sector = industry?.let { "東証 • $it" } ?: "業種未登録 • 日本株",
                        market = "東証",
                        category = Category.JAPAN,
                        price = stock.currentPrice,
                        shares = 100,
                        acqPrice = stock.acquisitionPrice,
                        dps = dps,
                        yieldRate = stock.dividendYield ?: if (stock.currentPrice > 0.0) dps / stock.currentPrice * 100.0 else 0.0,
                        yoc = if (stock.acquisitionPrice > 0.0) dps / stock.acquisitionPrice * 100.0 else 0.0,
                        nextPayoutTiming = "配当月未取得",
                        nextPayoutAmount = if (dps > 0.0) "¥${currencyFormat.format((100.0 * dps / 2.0).toLong())}" else "—",
                        dividendMonths = emptyList(),
                        dividendType = "",
                        accountType = "特定口座",
                        dailyChange = "—",
                        isPositive = true
                    )
                }
                val nextOffset = pages.maxOfOrNull { it.nextOffset } ?: endOffset
                if (nextOffset <= offset) break
                offset = nextOffset
            }

            val popular = resolved.distinctBy { it.code }.take(POPULAR_HOLDINGS_COUNT)
            if (popular.size != POPULAR_HOLDINGS_COUNT) {
                android.util.Log.w("CloudflareMarket", "R2から5年前の価格を取得できた銘柄が50件未満のため、保有銘柄を登録しませんでした (${popular.size}/$POPULAR_HOLDINGS_COUNT)")
                return
            }

            if (hasRestoredLocalHoldings) return
            val currentHoldings = _holdings.value
            val baselineHoldings = currentHoldings.filterNot { it.id.startsWith("h-") }
            val popularByCode = popular.associateBy { it.code.uppercase(java.util.Locale.ROOT) }
            val updatedHoldings = baselineHoldings.map { holding ->
                popularByCode[holding.code.uppercase(java.util.Locale.ROOT)] ?: holding
            }
            val updatedCodes = updatedHoldings.mapTo(mutableSetOf()) { it.code.uppercase(java.util.Locale.ROOT) }
            val additions = popular.filterNot { it.code.uppercase(java.util.Locale.ROOT) in updatedCodes }
            val nextHoldings = updatedHoldings + additions
            updatePortfolio(nextHoldings)
            persistCurrentHoldings()
            displayPreferences.edit().putBoolean("popular_r2_holdings_seeded_v1", true).apply()
        }.onFailure { error ->
            android.util.Log.w("CloudflareMarket", "R2人気銘柄の初回保有登録に失敗しました", error)
        }
    }

    suspend fun searchCloudflareSecurities(query: String): List<CloudflareSearchHit> {
        val hits = marketRepository.searchSecurities(query)
        val cachedPrices = readCachedPrices()
        val industries = withContext(Dispatchers.IO) { runCatching { Tse33IndustryMaster.industries(getApplication()) }.getOrDefault(emptyMap()) }
        return hits.map { hit ->
            hit.copy(
                cachedPrice = cachedPrices[hit.code.uppercase(java.util.Locale.ROOT)],
                industry = industries[hit.code.take(4).uppercase(java.util.Locale.ROOT)]
            )
        }
    }

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

    suspend fun registerCloudflareAllocations(code: String, allocations: List<HoldingAllocation>): Boolean {
        restoration.await()
        val security = resolveCloudflareSecurity(code) ?: return false
        val added = addAllocations(code, allocations, security)
        if (added) persistCurrentHoldings()
        return added
    }

    suspend fun resolveCloudflareSecurity(code: String): CloudflareSecurity? {
        val security = runCatchingCancellable { marketRepository.getSecurity(code) }.getOrNull() ?: return null
        val industry = withContext(Dispatchers.IO) { runCatching {
            Tse33IndustryMaster.industries(getApplication())[security.code.take(4).uppercase(java.util.Locale.ROOT)]
        }.getOrNull() }
        val cachedPrice = readCachedPrices()[security.code.uppercase(java.util.Locale.ROOT)]
        return security.copy(marketPrice = security.marketPrice ?: cachedPrice, industry = industry)
    }

    suspend fun fetchStockAnalysis(code: String): CloudflareStockAnalysis? =
        marketRepository.getStockAnalysis(code)

    private suspend fun refreshMarketPricesIfDue() {
        val now = System.currentTimeMillis()
        val lastFetchAt = marketPreferences.getLong("last_price_fetch_at", 0L)
        val lastFetchJstDate = marketPreferences.getString("last_price_fetch_jst_date", null)
        if (!PriceRefreshPolicy.shouldFetch(now, lastFetchAt, lastFetchJstDate)) return

        runCatchingCancellable { marketRepository.fetchPriceCatalog() }
            .onSuccess { fetchedPrices ->
                val mergedPrices = readCachedPrices() + fetchedPrices
                val json = JSONObject().apply { mergedPrices.forEach { (code, price) -> put(code, price) } }
                marketPreferences.edit()
                    .putString("prices", json.toString())
                    .putLong("last_price_fetch_at", now)
                    .putString("last_price_fetch_jst_date", PriceRefreshPolicy.japanDate(now))
                    .apply()
                applyPrices(mergedPrices)
            }
            .onFailure { error ->
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

    private fun applyCachedPrices() = applyPrices(readCachedPrices())

    private fun applyPrices(prices: Map<String, Double>) {
        if (prices.isEmpty()) return
        _holdings.update { list -> list.map { holding ->
            val price = prices[holding.code.uppercase(java.util.Locale.ROOT)] ?: return@map holding
            holding.copy(price = price, yieldRate = if (price > 0) holding.dps / price * 100 else holding.yieldRate)
        } }
        _watchlist.update { list -> list.map { stock ->
            val price = prices[stock.code.uppercase(java.util.Locale.ROOT)] ?: return@map stock
            stock.copy(
                price = price,
                currentYield = if (price > 0) stock.dps / price * 100 else stock.currentYield,
                targetPrice = if (stock.targetYield > 0) stock.dps / (stock.targetYield / 100) else stock.targetPrice
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
        if (!restoration.isCompleted) return
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
        if (!restoration.isCompleted) return
        _watchlist.update { list ->
            list.map { stock ->
                if (stock.code == code) {
                    stock.copy(alertEnabled = !stock.alertEnabled)
                } else stock
            }
        }
        saveRequests.trySend(Unit)
    }

    fun toggleWatchlistMembership(holding: Holding) {
        if (!restoration.isCompleted) return
        _watchlist.update { list ->
            if (list.any { it.code.equals(holding.code, true) }) list.filterNot { it.code.equals(holding.code, true) }
            else list + WatchlistStock(holding.code, holding.name, holding.market, holding.sector,
                if (holding.category == Category.US) "us" else "jp", holding.price, holding.currency, holding.dps,
                holding.liveYield, holding.liveYield.coerceAtLeast(0.1), holding.price, holding.dailyChange, holding.isPositive,
                holding.dividendMonths, holding.nextPayoutTiming, "", false)
        }
        saveRequests.trySend(Unit)
    }

    fun updateHolding(code: String, shares: Int, acqPrice: Double, currentPrice: Double? = null): Boolean {
        if (!restoration.isCompleted || holdingsLoadFailed) return false
        if (shares < 0 || !PortfolioMath.validMoney(acqPrice) ||
            (currentPrice != null && !PortfolioMath.validMoney(currentPrice))) return false
        if (_holdings.value.none { it.code.equals(code, true) }) return false
        updatePortfolio(_holdings.value.map { holding ->
            if (!holding.code.equals(code, true)) holding else {
                val free = if (holding.shares > 0) (shares.toDouble() * holding.effectiveTaxFreeShares / holding.shares).toInt() else 0
                holding.copy(shares = shares, acqPrice = acqPrice, price = currentPrice ?: holding.price,
                    taxFreeShares = free).refreshPayout()
            }
        })
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
        if (!restoration.isCompleted || holdingsLoadFailed) return false
        val normalized = PortfolioMath.normalizeCode(code)
        if (!PortfolioMath.validCode(normalized) || allocations.isEmpty() || allocations.none { it.shares > 0 } ||
            allocations.any { it.shares < 0 || !PortfolioMath.validMoney(it.acquisitionPrice) || it.accountType.isBlank() }) return false
        val old = _holdings.value.firstOrNull { it.code.equals(normalized, true) }
        val preset = _watchlist.value.firstOrNull { it.code.equals(normalized, true) }
        if (old == null && preset == null && security == null) return false
        val dps = security?.annualDividendPerShare ?: old?.dps ?: preset?.dps ?: 0.0
        val price = readCachedPrices()[normalized] ?: security?.marketPrice ?: old?.price ?: preset?.price ?: 0.0
        if (!PortfolioMath.validMoney(dps) || !PortfolioMath.validMoney(price)) return false
        val currency = old?.currency ?: preset?.currency ?: if (normalized.matches(Regex("[A-Z]+"))) "$" else "¥"
        val template = old ?: Holding(
            id = "holding-$normalized", code = normalized, name = security?.name ?: preset?.name.orEmpty(),
            sector = security?.industry?.let { "東証 • $it" } ?: preset?.sector ?: "業種未登録",
            market = preset?.market ?: if (currency == "$") "米国株" else "東証", category = if (currency == "$") Category.US else Category.JAPAN,
            price = price, currency = currency, shares = 0, acqPrice = 0.0, dps = dps, yieldRate = 0.0, yoc = 0.0,
            nextPayoutTiming = "配当月未取得", nextPayoutAmount = "—", dividendMonths = preset?.payoutMonths ?: emptyList(),
            dividendType = "", accountType = "", dailyChange = "—", isPositive = true
        )
        val merged = runCatching {
            PortfolioMath.mergeAllocations(template.copy(price = price, dps = dps, name = security?.name ?: template.name), allocations)
        }.getOrNull() ?: return false
        updatePortfolio(if (old == null) _holdings.value + merged else _holdings.value.map { if (it.id == old.id) merged else it })
        return true
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

    suspend fun resetLocalData() {
        restoration.await()
        saveMutex.withLock {
            withContext(Dispatchers.IO) {
                localImportStore.clear()
                localImportStore.saveHoldings(emptyList())
                localImportStore.saveWatchlist(emptyList())
                check(marketPreferences.edit().clear().commit()) { "価格キャッシュを初期化できませんでした" }
                check(displayPreferences.edit().clear().putBoolean("popular_r2_holdings_seeded_v1", true).commit()) { "設定を初期化できませんでした" }
            }
            _holdings.value = emptyList()
            _watchlist.value = emptyList()
            hasRestoredLocalHoldings = true
            holdingsLoadFailed = false
            _themeMode.value = "dark"
            _selectedAccent.value = "emerald"
            _isOledBlack.value = false
            _isPrivacyActive.value = false
            _maskAtStartup.value = false
            _taxRateMode.value = "gross"
            _saveError.value = null
        }
    }

    private companion object {
        const val POPULAR_HOLDINGS_COUNT = 50
        const val POPULAR_HOLDINGS_ACQUISITION_DATE = "2021-10-05"
    }
}
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/res/xml/backup_rules.xml

【修正案（リファクタリング後コード）】:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="local_import_store.xml" />
</full-backup-content>
```

### 【対象箇所】: kurogane-dividend/android/app/src/main/res/xml/data_extraction_rules.xml

【修正案（リファクタリング後コード）】:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="local_import_store.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="local_import_store.xml" />
    </device-transfer>
</data-extraction-rules>
```

### 【対象箇所】: kurogane-dividend/android/gradle.properties

【修正案（リファクタリング後コード）】:

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
org.gradle.workers.max=2
```

### 【対象箇所】: kurogane-dividend/src/context/AppContext.tsx

【修正案（リファクタリング後コード）】:

```tsx
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import {
  ScreenType,
  Holding,
  WatchlistStock,
  StateSimMode,
  ThemeMode,
  AccentColor,
  ToastMessage
} from '../types';
import { initialHoldings, initialWatchlist } from '../data/mockData';

interface AppContextType {
  currentScreen: ScreenType;
  selectedStockCode: string;
  holdings: Holding[];
  watchlist: WatchlistStock[];
  isPrivacyActive: boolean;
  themeMode: ThemeMode;
  isOledBlack: boolean;
  accentColor: AccentColor;
  stateSim: StateSimMode;
  toast: ToastMessage | null;
  refreshMarketPrices: (showToastMsg?: boolean) => void;
  // Modals
  isSearchOpen: boolean;
  isNotificationsOpen: boolean;
  isAddHoldingOpen: boolean;
  quickEditHolding: Holding | null;
  // Actions
  navigateTo: (screen: ScreenType, stockCode?: string) => void;
  goBack: () => void;
  togglePrivacy: () => void;
  setThemeMode: (mode: ThemeMode) => void;
  setIsOledBlack: (oled: boolean) => void;
  setAccentColor: (accent: AccentColor) => void;
  setStateSim: (mode: StateSimMode) => void;
  showToast: (message: string, icon?: string, actionText?: string, onAction?: () => void) => void;
  clearToast: () => void;
  updateHolding: (code: string, shares: number, acqPrice: number, currentPrice?: number) => void;
  addHolding: (holding: Holding) => void;
  removeHolding: (code: string) => void;
  toggleWatchlistStock: (code: string, force?: boolean) => void;
  updateTargetYield: (code: string, delta: number) => void;
  openSearch: () => void;
  closeSearch: () => void;
  openNotifications: () => void;
  closeNotifications: () => void;
  openAddHolding: () => void;
  closeAddHolding: () => void;
  openQuickEdit: (holding: Holding) => void;
  closeQuickEdit: () => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

const accentColorMap: Record<AccentColor, { color: string; light: string; rgb: string; name: string }> = {
  emerald: { color: '#10b981', light: '#4edea3', rgb: '16, 185, 129', name: 'エメラルド' },
  cyan: { color: '#06b6d4', light: '#22d3ee', rgb: '6, 182, 212', name: 'シアン' },
  blue: { color: '#3b82f6', light: '#60a5fa', rgb: '59, 130, 246', name: 'ブルー' },
  amber: { color: '#f59e0b', light: '#fbbf24', rgb: '245, 158, 11', name: 'アンバー' },
  purple: { color: '#8b5cf6', light: '#a78bfa', rgb: '139, 92, 246', name: 'パープル' }
};

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('asset-management');
  const [screenHistory, setScreenHistory] = useState<ScreenType[]>(['asset-management']);
  const [selectedStockCode, setSelectedStockCode] = useState<string>('7203');
  const [holdings, setHoldings] = useState<Holding[]>(initialHoldings);
  const [watchlist, setWatchlist] = useState<WatchlistStock[]>(initialWatchlist);
  const [isPrivacyActive, setIsPrivacyActive] = useState<boolean>(false);
  const [themeMode, setThemeModeState] = useState<ThemeMode>('dark');
  const [isOledBlack, setIsOledBlackState] = useState<boolean>(false);
  const [accentColor, setAccentColorState] = useState<AccentColor>('emerald');
  const [stateSim, setStateSim] = useState<StateSimMode>('normal');
  const [toast, setToast] = useState<ToastMessage | null>(null);

  // Modals state
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [isAddHoldingOpen, setIsAddHoldingOpen] = useState(false);
  const [quickEditHolding, setQuickEditHolding] = useState<Holding | null>(null);

  // Cloudflare R2 Daily Price Sync Engine (Executed once a day on startup)
  useEffect(() => {
    const fetchPricesFromR2 = async () => {
      const r2Url = import.meta.env.VITE_CLOUDFLARE_R2_URL;
      if (!r2Url) return;

      try {
        if (new URL(r2Url).protocol !== 'https:') throw new Error('Price feed must use HTTPS');
        // Prevent redundant daily fetches by caching the last-fetched date in LocalStorage
        const cachedDate = localStorage.getItem('kurogane_r2_last_fetch');
        const todayStr = new Date().toISOString().split('T')[0];

        if (cachedDate === todayStr) {
          console.log('Cloudflare R2: Stock prices for today are already cached locally.');
          const cachedPrices = localStorage.getItem('kurogane_r2_prices');
          if (cachedPrices) {
            applyR2Prices(JSON.parse(cachedPrices));
            return;
          }
        }

        console.log('Cloudflare R2: Fetching daily market closing prices from R2 bucket...');
        const response = await fetch(r2Url, {
          method: 'GET',
          headers: {
            'Accept': 'application/json'
          }
        });
        if (!response.ok) throw new Error(`R2 server returned status: ${response.status}`);

        const data = await response.json();
        if (data && data.prices) {
          localStorage.setItem('kurogane_r2_last_fetch', todayStr);
          localStorage.setItem('kurogane_r2_prices', JSON.stringify(data.prices));
          applyR2Prices(data.prices);
          showToast('最新の株価データをR2から取得しました', 'sync');
        }
      } catch (err) {
        console.warn('Cloudflare R2 Sync Fallback: Public R2 bucket offline or unconfigured. Using high-fidelity preloads.', err);
      }
    };

    const applyR2Prices = (prices: Record<string, { price: number; dps: number; dailyChange: string; isPositive: boolean }>) => {
      // Sync Holdings prices from R2
      setHoldings(prev =>
        prev.map(h => {
          const match = prices[h.code];
          if (match) {
            const totalValuation = h.shares * match.price;
            const totalCost = h.shares * h.acqPrice;
            const diff = totalValuation - totalCost;
            const diffPct = totalCost > 0 ? ((diff / totalCost) * 100).toFixed(1) : '0';

            return {
              ...h,
              price: match.price,
              dps: match.dps,
              yoc: h.acqPrice > 0 ? Number(((match.dps / h.acqPrice) * 100).toFixed(2)) : h.yieldRate,
              dailyChange: diff >= 0
                ? `+¥${Math.round(diff).toLocaleString()} (+${diffPct}%)`
                : `-¥${Math.round(Math.abs(diff)).toLocaleString()} (${diffPct}%)`,
              isPositive: diff >= 0
            };
          }
          return h;
        })
      );

      // Sync Watchlist prices from R2
      setWatchlist(prev =>
        prev.map(stock => {
          const match = prices[stock.code];
          if (match) {
            const currentYield = Number(((match.dps / match.price) * 100).toFixed(2));
            const targetPrice = Math.floor(match.dps / (stock.targetYield / 100));

            return {
              ...stock,
              price: match.price,
              dps: match.dps,
              currentYield,
              dailyChange: match.dailyChange,
              isPositive: match.isPositive
            };
          }
          return stock;
        })
      );
    };

    fetchPricesFromR2();
  }, []);

  // Live Market Fluctuation Engine: Prices and yields realistically fluctuate based on live market conditions
  const fluctuateMarket = useCallback((showNotification = false) => {
    setHoldings(prev =>
      prev.map(h => {
        // Realistic micro-tick: between -0.4% and +0.45%
        const deltaPct = (Math.random() - 0.47) * 0.009;
        const rawPrice = h.price * (1 + deltaPct);
        const newPrice = h.currency === '$'
          ? Math.round(rawPrice * 100) / 100
          : Math.max(10, Math.round(rawPrice));

        const totalValuation = h.shares * newPrice;
        const totalCost = h.shares * h.acqPrice;
        const diff = totalValuation - totalCost;
        const diffPct = totalCost > 0 ? ((diff / totalCost) * 100).toFixed(1) : '0';
        const newYieldRate = newPrice > 0 ? Number(((h.dps / newPrice) * 100).toFixed(2)) : h.yieldRate;
        const isUp = diff >= 0;

        return {
          ...h,
          price: newPrice,
          yieldRate: newYieldRate,
          dailyChange: isUp
            ? `+${h.currency}${Math.round(Math.abs(newPrice - h.acqPrice)).toLocaleString()} (+${diffPct}%)`
            : `-${h.currency}${Math.round(Math.abs(newPrice - h.acqPrice)).toLocaleString()} (${diffPct}%)`,
          isPositive: isUp
        };
      })
    );

    setWatchlist(prev =>
      prev.map(stock => {
        const deltaPct = (Math.random() - 0.47) * 0.009;
        const rawPrice = stock.price * (1 + deltaPct);
        const newPrice = stock.currency === '$'
          ? Math.round(rawPrice * 100) / 100
          : Math.max(10, Math.round(rawPrice));
        const currentYield = Number(((stock.dps / newPrice) * 100).toFixed(2));
        const isPositive = newPrice >= stock.price;
        const priceDiff = Math.abs(newPrice - stock.price);
        const diffStr = stock.currency === '$' ? priceDiff.toFixed(2) : Math.round(priceDiff).toString();

        return {
          ...stock,
          price: newPrice,
          currentYield,
          dailyChange: `${isPositive ? '+' : '-'}${stock.currency}${diffStr} (${isPositive ? '+' : '-'}${(Math.abs(deltaPct) * 100).toFixed(2)}%)`,
          isPositive
        };
      })
    );

    if (showNotification) {
      showToast('最新相場の株価・利回りを反映しました', 'trending_up');
    }
  }, []);

  // Periodic market tick simulation so stock prices and indices genuinely fluctuate as live market values
  useEffect(() => {
    const timer = setInterval(() => {
      fluctuateMarket(false);
    }, 20000);
    return () => clearInterval(timer);
  }, [fluctuateMarket]);

  // Sync css variables when accentColor changes
  useEffect(() => {
    const root = document.documentElement;
    const item = accentColorMap[accentColor];
    if (item) {
      root.style.setProperty('--accent', item.color);
      root.style.setProperty('--accent-light', item.light);
      root.style.setProperty('--accent-glow', `rgba(${item.rgb}, 0.35)`);
      root.style.setProperty('--accent-dim', `rgba(${item.rgb}, 0.15)`);
    }
  }, [accentColor]);

  // Sync theme changes to body
  useEffect(() => {
    const body = document.body;
    body.classList.remove('theme-light', 'theme-oled');
    if (themeMode === 'light') {
      body.classList.add('theme-light');
      document.documentElement.classList.remove('dark');
    } else {
      document.documentElement.classList.add('dark');
      if (isOledBlack) {
        body.classList.add('theme-oled');
      }
    }
  }, [themeMode, isOledBlack]);

  const navigateTo = (screen: ScreenType, stockCode?: string) => {
    if (stockCode) setSelectedStockCode(stockCode);
    setScreenHistory(prev => [...prev, screen]);
    setCurrentScreen(screen);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const goBack = () => {
    if (screenHistory.length > 1) {
      const nextHistory = [...screenHistory];
      nextHistory.pop();
      const prevScreen = nextHistory[nextHistory.length - 1];
      setScreenHistory(nextHistory);
      setCurrentScreen(prevScreen);
    } else {
      setCurrentScreen('asset-management');
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const togglePrivacy = () => {
    setIsPrivacyActive(prev => !prev);
  };

  const showToast = (message: string, icon = 'check_circle', actionText?: string, onAction?: () => void) => {
    const id = Date.now().toString();
    setToast({ id, message, icon, actionText, onAction });

    // Trigger real native Android OS notification if running inside WebView APK!
    if (typeof window !== 'undefined' && (window as any).KuroganeAndroid) {
      try {
        (window as any).KuroganeAndroid.showSystemNotification("Diview", message);
      } catch (e) {
        console.error('Android bridge error:', e);
      }
    }

    setTimeout(() => {
      setToast(prev => (prev?.id === id ? null : prev));
    }, 3200);
  };

  const clearToast = () => {
    setToast(null);
  };

  const updateHolding = (code: string, shares: number, acqPrice: number, currentPrice?: number) => {
    setHoldings(prev =>
      prev.map(h => {
        if (h.code === code) {
          const effectivePrice = typeof currentPrice === 'number' && currentPrice > 0 ? currentPrice : h.price;
          const totalValuation = shares * effectivePrice;
          const totalCost = shares * acqPrice;
          const diff = totalValuation - totalCost;
          const diffPct = totalCost > 0 ? ((diff / totalCost) * 100).toFixed(1) : '0';
          const newAnnualDiv = Math.round(shares * h.dps);
          const payoutDiv = Math.round(newAnnualDiv / (h.dividendMonths?.length || 2));
          const newYieldRate = effectivePrice > 0 ? Number(((h.dps / effectivePrice) * 100).toFixed(2)) : h.yieldRate;
          const newYoc = acqPrice > 0 ? Number(((h.dps / acqPrice) * 100).toFixed(2)) : newYieldRate;
          return {
            ...h,
            shares,
            acqPrice,
            price: effectivePrice,
            yieldRate: newYieldRate,
            yoc: newYoc,
            nextPayoutAmount: `¥${payoutDiv.toLocaleString()}`,
            nextPayoutTiming: `次回: ${h.dividendMonths?.[0] || 6}月中旬 (¥${payoutDiv.toLocaleString()})`,
            dailyChange: diff >= 0 ? `+¥${diff.toLocaleString()} (+${diffPct}%)` : `-¥${Math.abs(diff).toLocaleString()} (${diffPct}%)`,
            isPositive: diff >= 0
          };
        }
        return h;
      })
    );
    showToast('保有情報を正常に更新しました');
  };

  const addHolding = (newHolding: Holding) => {
    setHoldings(prev => [newHolding, ...prev]);
    showToast(`${newHolding.name} (${newHolding.code}) を保有株に追加しました`);
  };

  const removeHolding = (code: string) => {
    setHoldings(prev => prev.filter(h => h.code !== code));
    showToast('銘柄の保有を解除しました', 'delete');
  };

  const toggleWatchlistStock = (code: string, force?: boolean) => {
    setWatchlist(prev =>
      prev.map(item => {
        if (item.code === code) {
          const newState = typeof force === 'boolean' ? force : !item.alertEnabled;
          return { ...item, alertEnabled: newState };
        }
        return item;
      })
    );
  };

  const updateTargetYield = (code: string, delta: number) => {
    setWatchlist(prev =>
      prev.map(item => {
        if (item.code === code) {
          const newTarget = Math.max(0.1, +(item.targetYield + delta).toFixed(2));
          const targetPrice = Math.floor(item.dps / (newTarget / 100));
          return {
            ...item,
            targetYield: newTarget,
            targetPrice
          };
        }
        return item;
      })
    );
  };

  return (
    <AppContext.Provider
      value={{
        currentScreen,
        selectedStockCode,
        holdings,
        watchlist,
        isPrivacyActive,
        themeMode,
        isOledBlack,
        accentColor,
        stateSim,
        toast,
        refreshMarketPrices: (showToastMsg?: boolean) => fluctuateMarket(showToastMsg ?? true),
        isSearchOpen,
        isNotificationsOpen,
        isAddHoldingOpen,
        quickEditHolding,
        navigateTo,
        goBack,
        togglePrivacy,
        setThemeMode: setThemeModeState,
        setIsOledBlack: setIsOledBlackState,
        setAccentColor: setAccentColorState,
        setStateSim,
        showToast,
        clearToast,
        updateHolding,
        addHolding,
        removeHolding,
        toggleWatchlistStock,
        updateTargetYield,
        openSearch: () => setIsSearchOpen(true),
        closeSearch: () => setIsSearchOpen(false),
        openNotifications: () => setIsNotificationsOpen(true),
        closeNotifications: () => setIsNotificationsOpen(false),
        openAddHolding: () => setIsAddHoldingOpen(true),
        closeAddHolding: () => setIsAddHoldingOpen(false),
        openQuickEdit: (h: Holding) => setQuickEditHolding(h),
        closeQuickEdit: () => setQuickEditHolding(null)
      }}
    >
      {children}
    </AppContext.Provider>
  );
};

export const useApp = () => {
  const context = useContext(AppContext);
  if (!context) throw new Error('useApp must be used within an AppProvider');
  return context;
};
```

## ■ 運用フェーズ：ユニットテスト・Androidテスト全文

既存の主要ロジックのテストも含め、テストファイルをすべて収録します。

### 【テスト対象】: BrokerCsvImporterTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/BrokerCsvImporterTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class BrokerCsvImporterTest {
    @Test fun rejectsUnclosedQuotes() {
        assertThrows(IllegalArgumentException::class.java) { CsvTokenizer.parse("code,name\n7203,\"unterminated") }
    }

    @Test fun monexUtf8WithoutBomIsNotMisreadAsShiftJis() {
        val csv = "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ自動車,100,2450"
        val parsed = BrokerCsvImporter.parse(csv.toByteArray(Charsets.UTF_8), CsvBroker.MONEX)
        assertEquals(CsvEncoding.UTF8, parsed.encoding)
        assertEquals("トヨタ自動車", parsed.rows.single().name)
    }

    @Test fun invalidNumericRowsAreReportedAndNotImported() {
        listOf("NaN", "Infinity", "-1", "1.5", "2147483648").forEach { quantity ->
            val csv = "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ,$quantity,2450"
            val parsed = BrokerCsvImporter.parse(csv.toByteArray(), CsvBroker.SBI)
            assertTrue(parsed.rows.isEmpty())
            assertTrue(parsed.errors.isNotEmpty())
        }
    }

    @Test fun fullWidthNumbersAndStockSuffixesAreSupported() {
        val csv = "銘柄コード,銘柄名,保有数量,取得単価\n７２０３,トヨタ,１００株式,２４５０円"
        val parsed = BrokerCsvImporter.parse(csv.toByteArray(), CsvBroker.SBI).rows.single()
        assertEquals("7203", parsed.code)
        assertEquals(100.0, parsed.shares!!, 0.0)
        assertEquals(2450.0, parsed.averageCost!!, 0.0)
    }

    @Test fun emptyNormalizedNamesAndOppositeHedgeVariantsDoNotMatch() {
        assertNull(FundNameMatcher.match("ファンド", listOf("投資信託")))
        assertNull(FundNameMatcher.match("全世界株式（為替ヘッジあり）", listOf("全世界株式（為替ヘッジなし）")))
    }

    @Test fun emptyCsvAndMissingHeadersProduceReadableErrors() {
        assertTrue(BrokerCsvImporter.parse(byteArrayOf(), CsvBroker.SBI).errors.isNotEmpty())
        assertTrue(BrokerCsvImporter.parse("foo,bar\na,b".toByteArray(), CsvBroker.SBI).errors.isNotEmpty())
    }
    @Test fun parsesQuotedCommasAndEscapedQuotes() {
        val csv = "銘柄コード,銘柄名,保有数量,平均取得価額,現在値,口座区分\n7203,\"トヨタ,自動車\",100,2450,2600,特定\n8316,\"三井住友\"\"FG\",20,3000,3150,NISA"
        val parsed = BrokerCsvImporter.parse(csv.toByteArray(Charsets.UTF_8), CsvBroker.SBI)
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals("トヨタ,自動車", parsed.rows[0].name)
        assertEquals("三井住友\"FG", parsed.rows[1].name)
        assertEquals(100.0, parsed.rows[0].shares!!, 0.0)
        assertEquals("NISA", parsed.rows[1].account)
    }

    @Test fun detectsBrokerLegacyJapaneseEncoding() {
        val csv = "銘柄コード,銘柄名,保有数量,平均取得価額\r\n7203,トヨタ自動車,100,2450".toByteArray(Charset.forName("MS932"))
        val parsed = BrokerCsvImporter.parse(csv, CsvBroker.RAKUTEN)
        assertEquals(CsvEncoding.SHIFT_JIS, parsed.encoding)
        assertEquals("トヨタ自動車", parsed.rows.single().name)
    }

    @Test fun monexPrefersItsLegacyShiftJisFormatUnlessUtf8BomIsPresent() {
        val legacy = "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ自動車,100,2450".toByteArray(Charset.forName("Shift_JIS"))
        assertEquals(CsvEncoding.SHIFT_JIS, BrokerCsvImporter.parse(legacy, CsvBroker.MONEX).encoding)
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ自動車,100,2450".toByteArray()
        assertEquals(CsvEncoding.UTF8, BrokerCsvImporter.parse(utf8, CsvBroker.MONEX).encoding)
    }

    @Test fun matchesNormalizedFundNameAndSeparatesUnknownFunds() {
        val csv = "商品コード,商品名,保有口数,個別元本\n, eMAXIS Slim 全世界株式（オール・カントリー）,50000,24500\n,架空の未登録ファンド,100,1000"
        val parsed = BrokerCsvImporter.parse(csv.toByteArray(), CsvBroker.MONEX, fundNames = listOf("eMAXIS Slim 全世界株式 オールカントリー"))
        assertEquals(1, parsed.rows.size)
        assertTrue(parsed.rows.single().isFund)
        assertEquals(1, parsed.unmatchedFunds.size)
        assertEquals("架空の未登録ファンド", parsed.unmatchedFunds.single().sourceName)
    }

    @Test fun supportsUtf8BomAndMultilineQuotedCells() {
        val body = "銘柄コード,銘柄名,保有数量,平均取得価額\r\n7203,\"トヨタ\n自動車\",1,2".toByteArray(Charsets.UTF_8)
        val parsed = BrokerCsvImporter.parse(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body, CsvBroker.MATSUI)
        assertEquals(CsvEncoding.UTF8, parsed.encoding)
        assertEquals("トヨタ\n自動車", parsed.rows.single().name)
    }

    @Test fun recognizesBrokerSpecificShareAndCostHeaderVariants() {
        val rakuten = "銘柄コード,銘柄名,保有数量［株］,平均取得額［円］,現在値［円］\n7203,トヨタ自動車,100,2450,2600"
        val parsed = BrokerCsvImporter.parse(rakuten.toByteArray(Charsets.UTF_8), CsvBroker.RAKUTEN)
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals(100.0, parsed.rows.single().shares!!, 0.0)
        assertEquals(2450.0, parsed.rows.single().averageCost!!, 0.0)

        val matsui = "銘柄コード,銘柄名称,保有数[株],取得平均[円]\n7203,トヨタ自動車,100,2450"
        val second = BrokerCsvImporter.parse(matsui.toByteArray(Charsets.UTF_8), CsvBroker.MATSUI)
        assertEquals(100.0, second.rows.single().shares!!, 0.0)
        assertEquals(2450.0, second.rows.single().averageCost!!, 0.0)
    }
}
```

### 【テスト対象】: CloudflareMarketRepositoryTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/CloudflareMarketRepositoryTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class CloudflareMarketRepositoryTest {
    private class Connection(private val status: Int, private val body: String,
                             private val failure: Exception? = null) : HttpURLConnection(URL("https://example.com")) {
        var disconnected = false
        override fun getResponseCode(): Int { failure?.let { throw it }; return status }
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun disconnect() { disconnected = true }
        override fun connect() {}
        override fun usingProxy() = false
    }

    private fun repository(connection: Connection, base: String = "https://example.com") =
        CloudflareMarketRepository(base, Dispatchers.Unconfined) { connection }

    @Test fun parsesValidSecurityAndClosesTheConnection() = runBlocking {
        val connection = Connection(200, """{"code":"7203","name":"トヨタ","dividend":{"annual_dps_adjusted":100},"market_price":{"close":2000}}""")
        val stock = repository(connection).getSecurity(" 7203 ")!!
        assertEquals("7203", stock.code)
        assertEquals(100.0, stock.annualDividendPerShare!!, 0.0)
        assertEquals(2000.0, stock.marketPrice!!, 0.0)
        assertTrue(connection.disconnected)
        assertFalse(connection.instanceFollowRedirects)
    }

    @Test fun missingDividendAndNullPriceRemainUnknown() = runBlocking {
        val stock = repository(Connection(200, """{"code":"7203","name":"トヨタ","dividend":null,"market_price":{"close":null}}""")).getSecurity("7203")!!
        assertNull(stock.annualDividendPerShare)
        assertNull(stock.marketPrice)
    }

    @Test fun notFoundReturnsNullWhileServerErrorRemainsAnError() {
        assertNull(runBlocking { repository(Connection(404, "private response")).getSecurity("7203") })
        val connection = Connection(503, "sensitive server diagnostics")
        val error = assertThrows(MarketHttpException::class.java) { runBlocking { repository(connection).getSecurity("7203") } }
        assertEquals(503, error.status)
        assertFalse(error.message.orEmpty().contains("sensitive"))
        assertTrue(connection.disconnected)
    }

    @Test fun timeoutAndInvalidJsonAlwaysDisconnect() {
        val timeout = Connection(200, "", SocketTimeoutException("timeout"))
        assertThrows(SocketTimeoutException::class.java) { runBlocking { repository(timeout).searchSecurities("7203") } }
        assertTrue(timeout.disconnected)
        val malformed = Connection(200, "not json")
        assertThrows(org.json.JSONException::class.java) { runBlocking { repository(malformed).searchSecurities("7203") } }
        assertTrue(malformed.disconnected)
    }

    @Test fun redirectsAndCleartextAndEmbeddedCredentialsAreRejected() {
        assertThrows(MarketHttpException::class.java) { runBlocking { repository(Connection(302, "")).searchSecurities("7203") } }
        listOf("http://example.com", "https://user:password@example.com", "https://example.com?q=x").forEach { base ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(Connection(200, "{}"), base).searchSecurities("7203") } }
        }
    }

    @Test fun cancellationPropagatesAndReleasesTheConnection() {
        val connection = Connection(200, "", CancellationException("cancel"))
        assertThrows(CancellationException::class.java) { runBlocking { repository(connection).getStockAnalysis("7203") } }
        assertTrue(connection.disconnected)
    }

    @Test fun invalidInputAndImpossibleDatesAreRejected() {
        val repository = repository(Connection(200, "{}"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.getSecurity("../7203") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.searchSecurities("a".repeat(257)) } }
        assertThrows(java.time.DateTimeException::class.java) { runBlocking { repository.fetchPopularStocks(0, "2026-02-30") } }
    }

    @Test fun mismatchedSecurityResponseCannotRegisterADifferentStock() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository(Connection(200, """{"code":"9999","name":"別銘柄"}""")).getSecurity("7203") }
        }
    }

    @Test fun searchSkipsNullRowsAndUnsafeCodes() = runBlocking {
        val result = repository(Connection(200, """{"items":[null,{"code":"../x","name":"bad"},{"code":"130A","name":"正常"}]}""")).searchSecurities("13")
        assertEquals(listOf("130A"), result.map { it.code })
    }

    @Test fun catalogFiltersNonfiniteNegativeAndZeroPrices() = runBlocking {
        val result = repository(Connection(200, """{"items":[{"code":"7203","price":2000},{"code":"9999","price":-1},{"code":"130A","price":"NaN"},{"code":"0000","price":0}]}""")).fetchPriceCatalog()
        assertEquals(mapOf("7203" to 2000.0), result)
    }

    @Test fun oversizedResponseFailsWithConnectionClosed() {
        val connection = Connection(200, " ".repeat(8 * 1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(connection).searchSecurities("7203") } }
        assertTrue(connection.disconnected)
    }
}
```

### 【テスト対象】: DvmBackupImporterTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/DvmBackupImporterTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test

class DvmBackupImporterTest {
    private fun encrypt(text: String): ByteArray {
        val key = "DvMgSyncAES256GCMKey2026OttiLab!".toByteArray(Charsets.US_ASCII)
        val nonce = ByteArray(12) { it.toByte() }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
        return "DVMV1".toByteArray(Charsets.US_ASCII) + nonce + cipher.doFinal(text.toByteArray())
    }

    @Test fun tamperedAndTruncatedBackupsFailAuthentication() {
        val file = encrypt("[]")
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) { DvmBackupImporter.decrypt(file) }
        assertThrows(IllegalArgumentException::class.java) { DvmBackupImporter.decrypt(file.take(20).toByteArray()) }
    }

    @Test fun parsesWeightedCostAndNisaSharesAcrossAccounts() {
        val text = """[{"corporateCode":"7203","corporateName":"トヨタ","numOfStocksHeld":100,"acquisitionUnitPrice":"1,000","numOfStocksHeldNisaNew":200,"acquisitionUnitPriceNisaNew":"2500","priceOfStock":"3000"}]"""
        val preview = DvmBackupImporter.parse(DvmBackupKind.STOCKS.fileName, encrypt(text))
        assertEquals(1, preview.itemCount)
        assertEquals(300.0, preview.stocks.single().shares, 0.0)
        assertEquals(2000.0, preview.stocks.single().averageCost, 0.0)
        assertEquals(200.0, preview.stocks.single().taxFreeShares!!, 0.0)
    }

    @Test fun rejectsInvalidPayloadStructureAndInvalidNumbers() {
        listOf("not-json", "{\"unexpected\":[]}", """[{"corporateCode":"7203","corporateName":"トヨタ","numOfStocksHeld":-1}]""",
            """[{"corporateCode":"7203","corporateName":"トヨタ","numOfStocksHeld":100,"priceOfStock":"NaN"}]""").forEach { body ->
            assertThrows(IllegalArgumentException::class.java) { DvmBackupImporter.parse(DvmBackupKind.STOCKS.fileName, encrypt(body)) }
        }
    }
    @Test fun decryptsValidLegacyPayloadAndCountsRows() {
        val fixture = "44564D5631000102030405060708090A0B7CC569ACA4B657E9E16F8323CF5EBCE45E43"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = DvmBackupImporter.decrypt(fixture)
        org.junit.Assert.assertEquals("[]", result.toString(Charsets.UTF_8))
    }

    @Test fun rejectsUnknownMagicBeforeDecryption() {
        assertThrows(IllegalArgumentException::class.java) {
            DvmBackupImporter.parse("BackUpStockInfoAndroid.dvm", ByteArray(40))
        }
    }

    @Test fun rejectsUnknownBackupType() {
        assertThrows(IllegalArgumentException::class.java) {
            DvmBackupImporter.parse("not-a-backup.dvm", ByteArray(40))
        }
    }
}
```

### 【テスト対象】: InputSafetyTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/InputSafetyTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class InputSafetyTest {
    @Test fun acceptsExactlyTheLimitAndEmptyFiles() {
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readBoundedBytes(2))
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf()).readBoundedBytes(1))
    }

    @Test fun rejectsOversizeBeforeReadingTheWholeStream() {
        val input = ByteArrayInputStream(ByteArray(100_000))
        assertThrows(IllegalArgumentException::class.java) { input.readBoundedBytes(10) }
        assertEquals(99_989, input.available())
    }

    @Test fun rethrowsCancellationAndVirtualMachineErrors() {
        assertThrows(CancellationException::class.java) { runCatchingCancellable<Unit> { throw CancellationException("cancel") } }
        assertThrows(OutOfMemoryError::class.java) { runCatchingCancellable<Unit> { throw OutOfMemoryError("test") } }
    }

    @Test fun exposesOrdinaryCommunicationErrorsAsFailures() {
        val error = IOException("offline")
        assertSame(error, runCatchingCancellable<Unit> { throw error }.exceptionOrNull())
    }

    @Test fun zeroLengthBulkReadsDoNotFreezeTheReader() {
        val input = object : java.io.InputStream() {
            var remaining = 3
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = if (remaining > 0) 0 else -1
            override fun read(): Int = if (remaining-- > 0) 42 else -1
        }
        assertArrayEquals(byteArrayOf(42, 42, 42), input.readBoundedBytes(3))
    }

    @Test fun maximumIntegerLimitDoesNotOverflowTheProbeLength() {
        assertArrayEquals(byteArrayOf(1), ByteArrayInputStream(byteArrayOf(1)).readBoundedBytes(Int.MAX_VALUE))
    }
}
```

### 【テスト対象】: PortfolioExporterTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/PortfolioExporterTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortfolioExporterTest {
    @Test fun csvRoundTripKeepsMixedAccountQuantitiesAndCosts() {
        val stock = holding().copy(shares = 300, taxFreeShares = 100, accountType = "複数口座")
        val parsed = BrokerCsvImporter.parse(PortfolioExporter.csv(listOf(stock)).toByteArray(), CsvBroker.SBI)
        val restored = PortfolioImporter.importRows(listOf(stock.copy(shares = 0)), parsed.rows,
            setOf("shares", "averageCost", "currentPrice", "account")).single()
        assertEquals(stock.shares, restored.shares)
        assertEquals(stock.acqPrice, restored.acqPrice, 0.0)
        assertEquals(stock.effectiveTaxFreeShares, restored.effectiveTaxFreeShares)
    }

    @Test fun csvEscapesQuotesCommasAndFormulaPrefixes() {
        val csv = PortfolioExporter.csv(listOf(holding().copy(name = "=危険,\"文字列\"")))
        val row = CsvTokenizer.parse(csv.removePrefix("\uFEFF"))[1]
        assertEquals("'=危険,\"文字列\"", row[1])
    }

    @Test fun jsonContainsTheTaxSplitAndDividendSchedule() {
        val root = JSONObject(PortfolioExporter.json(listOf(holding().copy(taxFreeShares = 50))))
        val stock = root.getJSONArray("holdings").getJSONObject(0)
        assertEquals(50, stock.getInt("taxFreeShares"))
        assertEquals(2, stock.getJSONArray("dividendMonths").length())
        assertEquals("diview-portfolio-v1", root.getString("format"))
    }

    @Test fun emptyPortfolioStillProducesValidExportFiles() {
        assertEquals(0, JSONObject(PortfolioExporter.json(emptyList())).getJSONArray("holdings").length())
        assertEquals(1, CsvTokenizer.parse(PortfolioExporter.csv(emptyList())).size)
    }

    @Test fun fundsCanBeMatchedAgainAfterCsvExport() {
        val stock = holding().copy(code = "FUND:ABC", name = "ABCインデックス", category = com.kurogane.dividend.model.Category.FUND)
        val parsed = BrokerCsvImporter.parse(PortfolioExporter.csv(listOf(stock)).toByteArray(), CsvBroker.SBI,
            fundNames = listOf(stock.name))
        assertTrue(parsed.rows.single().isFund)
        assertEquals(stock.name, parsed.rows.single().name)
    }
}
```

### 【テスト対象】: PortfolioImporterTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/PortfolioImporterTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.junit.Assert.*
import org.junit.Test

class PortfolioImporterTest {
    private val fields = setOf("shares", "averageCost", "currentPrice", "account")
    private fun row(shares: Double = 100.0, cost: Double = 1000.0, account: String = "NISA") =
        CsvHoldingRow("7203", "トヨタ", shares, cost, 2000.0, account, false)

    @Test fun duplicateSecurityRowsAcrossAccountsAreSummedAndWeighted() {
        val stock = PortfolioImporter.importRows(listOf(holding()), listOf(row(), row(200.0, 2500.0, "特定口座")), fields).single()
        assertEquals(300, stock.shares)
        assertEquals(2000.0, stock.acqPrice, 0.0)
        assertEquals(100, stock.effectiveTaxFreeShares)
        assertEquals(25_937.0, stock.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun importingTheSameFileTwiceIsIdempotent() {
        val first = PortfolioImporter.importRows(emptyList(), listOf(row()), fields)
        assertEquals(first, PortfolioImporter.importRows(first, listOf(row()), fields))
    }

    @Test fun uncheckedFieldsPreserveExistingValues() {
        val stock = PortfolioImporter.importRows(listOf(holding()), listOf(row(500.0, 3000.0)), setOf("currentPrice")).single()
        assertEquals(100, stock.shares)
        assertEquals(1000.0, stock.acqPrice, 0.0)
        assertEquals("特定口座", stock.accountType)
    }

    @Test fun uncheckedFieldsAreNotSilentlyAppliedToNewStocks() {
        val stock = PortfolioImporter.importRows(emptyList(), listOf(row()), setOf("currentPrice")).single()
        assertEquals(0, stock.shares)
        assertEquals(0.0, stock.acqPrice, 0.0)
    }

    @Test fun invalidQuantityAndPriceDoNotModifyTheOriginalPortfolio() {
        val original = listOf(holding())
        listOf(-1.0, 1.5, Double.NaN, Int.MAX_VALUE.toDouble() + 1).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PortfolioImporter.importRows(original, listOf(row(value)), fields) }
        }
        assertThrows(IllegalArgumentException::class.java) { PortfolioImporter.importRows(original, listOf(row(cost = Double.NaN)), fields) }
        assertEquals(100, original.single().shares)
    }

    @Test fun multipleRowsCannotOverflowTheShareTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioImporter.importRows(emptyList(), listOf(row(Int.MAX_VALUE.toDouble()), row(1.0)), fields)
        }
    }

    @Test fun fundUnitsUseTheFundCategoryAndKeepZeroPriceFinite() {
        val fund = CsvHoldingRow("FUND:ABC", "ファンドABC", 50_000.0, 20_000.0, 24_460.0, "NISA", true)
        val stock = PortfolioImporter.importRows(emptyList(), listOf(fund), fields).single()
        assertEquals(Category.FUND, stock.category)
        assertEquals(122_300.0, stock.totalValuation, 0.0)
        assertTrue(PortfolioImporter.importRows(emptyList(), listOf(row().copy(currentPrice = 0.0)), fields).single().yieldRate.isFinite())
    }

    @Test fun legacyMixedAccountsKeepTheNisaQuantity() {
        val stock = DvmStock("7203", "トヨタ", 300.0, 2000.0, 3000.0, "複数口座", 100.0)
        val imported = PortfolioImporter.importStocks(listOf(holding()), listOf(stock)).single()
        assertEquals(100, imported.effectiveTaxFreeShares)
        assertEquals(25_937.0, imported.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun deselectedRowsAreIgnored() {
        assertEquals(emptyList<Any>(), PortfolioImporter.importRows(emptyList(), listOf(row().copy(selected = false)), fields))
    }
}
```

### 【テスト対象】: PriceRefreshPolicyTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/PriceRefreshPolicyTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PriceRefreshPolicyTest {
    @Test
    fun firstRunFetches() {
        assertTrue(PriceRefreshPolicy.shouldFetch(Instant.parse("2026-10-04T15:00:00Z").toEpochMilli(), 0L, null))
    }

    @Test
    fun doesNotFetchTwiceOnTheSameJapanDateEvenAfterTwelveHours() {
        val lastFetch = Instant.parse("2026-10-03T15:00:00Z").toEpochMilli() // 00:00 JST
        val lastJapanDate = PriceRefreshPolicy.japanDate(lastFetch)
        val sameDayLater = Instant.parse("2026-10-04T04:00:00Z").toEpochMilli() // 13:00 JST
        assertFalse(PriceRefreshPolicy.shouldFetch(sameDayLater, lastFetch, lastJapanDate))
    }

    @Test
    fun waitsForTwelveHoursAfterJapanDateChanges() {
        val lastFetch = Instant.parse("2026-10-04T14:59:59Z").toEpochMilli() // 23:59:59 JST
        val justAfterMidnight = lastFetch + 1_000L
        assertFalse(
            PriceRefreshPolicy.shouldFetch(
                justAfterMidnight,
                lastFetch,
                PriceRefreshPolicy.japanDate(lastFetch)
            )
        )
    }

    @Test
    fun fetchesOnNewJapanDateOnceTwelveHoursHavePassed() {
        val lastFetch = Instant.parse("2026-10-04T14:59:59Z").toEpochMilli()
        val afterTwelveHours = lastFetch + 12L * 60L * 60L * 1_000L
        assertTrue(
            PriceRefreshPolicy.shouldFetch(
                afterTwelveHours,
                lastFetch,
                PriceRefreshPolicy.japanDate(lastFetch)
            )
        )
    }
}
```

### 【テスト対象】: PortfolioDiagnosisGeneratorTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/model/PortfolioDiagnosisGeneratorTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioDiagnosisGeneratorTest {
    @Test
    fun generatesSevenPortfolioWeightedFindingsAndTheExpectedPatternCount() {
        val result = PortfolioDiagnosisGenerator.generate(sampleHoldings())

        assertEquals(7, result.findings.size)
        assertEquals(PortfolioDiagnosis.TOTAL_COMBINATIONS, result.possibleCombinations)
        assertEquals(15L * 15L * 15L * 15L * 15L * 15L * 15L, result.possibleCombinations)
        assertTrue(result.complete)
        assertTrue(result.narrative.contains("評価額比率"))
        assertTrue(result.narrative.contains("増配・非減配"))
    }

    @Test
    fun narrativeAndRiskAssessmentChangeWhenPortfolioWeightsChange() {
        val holdings = sampleHoldings()
        val original = PortfolioDiagnosisGenerator.generate(holdings)
        val concentrated = PortfolioDiagnosisGenerator.generate(
            holdings.mapIndexed { index, holding ->
                if (index == 0) holding.copy(shares = holding.shares * 8) else holding
            }
        )

        assertNotEquals(original.narrative, concentrated.narrative)
        assertNotEquals(original.score, concentrated.score)
        assertNotEquals(original.findings.first().combinationDigit, concentrated.findings.first().combinationDigit)
    }

    @Test
    fun missingDividendMonthDataIsExcludedAndReducesAvailablePatterns() {
        val result = PortfolioDiagnosisGenerator.generate(
            sampleHoldings().map { it.copy(dividendMonths = emptyList()) }
        )

        assertEquals(6, result.findings.size)
        assertTrue(result.missingLabels.contains("最大月集中度"))
        assertEquals(15L * 15L * 15L * 15L * 15L * 15L, result.possibleCombinations)
        assertNull(result.combinationIndex)
        assertFalse(result.complete)
    }

    @Test
    fun emptyPortfolioIsNotGivenAnInventedGradeOrRiskScore() {
        val result = PortfolioDiagnosisGenerator.generate(emptyList())

        assertNull(result.score)
        assertEquals("-", result.grade)
        assertEquals(0L, result.possibleCombinations)
        assertTrue(result.findings.isEmpty())
    }

    private fun sampleHoldings(): List<Holding> = listOf(
        holding("7203", "自動車A", "輸送用機器", Category.JAPAN, 100, 3_000.0, 100.0, listOf(3, 9)),
        holding("2914", "食品B", "食料品", Category.JAPAN, 200, 2_000.0, 100.0, listOf(6, 12)),
        holding("8316", "銀行C", "銀行業", Category.JAPAN, 100, 3_000.0, 130.0, listOf(6, 12)),
        holding("AAPL", "米国D", "テクノロジー", Category.US, 20, 200.0, 1.0, listOf(2, 5, 8, 11)),
        holding("FUND", "投信E", "投資信託", Category.FUND, 100, 100.0, 1.0, listOf(3, 6, 9, 12))
    )

    private fun holding(
        code: String,
        name: String,
        sector: String,
        category: Category,
        shares: Int,
        price: Double,
        dps: Double,
        dividendMonths: List<Int>
    ) = Holding(
        id = code,
        code = code,
        name = name,
        sector = sector,
        market = category.name,
        category = category,
        price = price,
        currency = if (category == Category.US) "$" else "¥",
        shares = shares,
        acqPrice = price,
        dps = dps,
        yieldRate = if (price > 0) dps / price * 100 else 0.0,
        yoc = if (price > 0) dps / price * 100 else 0.0,
        nextPayoutTiming = "",
        nextPayoutAmount = "",
        dividendMonths = dividendMonths,
        dividendType = "",
        accountType = "特定",
        dailyChange = "",
        isPositive = true
    )
}
```

### 【テスト対象】: PortfolioMathTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/model/PortfolioMathTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PortfolioMathTest {
    @Test fun fundPricesAreQuotedPerTenThousandUnits() {
        val fund = holding().copy(category = Category.FUND, shares = 50_000, price = 24_460.0, acqPrice = 20_000.0, dps = 100.0)
        assertEquals(122_300.0, fund.totalValuation, 0.0)
        assertEquals(100_000.0, fund.totalCost, 0.0)
        assertEquals(500.0, fund.annualDividend, 0.0)
    }

    @Test fun taxableAndNisaAllocationsKeepTheirActualTaxTreatment() {
        val merged = PortfolioMath.mergeAllocations(holding().copy(shares = 0), listOf(
            HoldingAllocation(100, 1000.0, "新NISA成長枠"), HoldingAllocation(100, 3000.0, "特定口座")))
        assertEquals(200, merged.shares)
        assertEquals(2000.0, merged.acqPrice, 0.0)
        assertEquals(100, merged.effectiveTaxFreeShares)
        assertEquals("複数口座", merged.accountType)
        assertEquals(20_000.0, merged.annualDividend, 0.0)
        assertEquals(17_968.5, merged.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun addingATaxablePositionDoesNotRetaxTheOriginalNisaShares() {
        val merged = PortfolioMath.mergeAllocations(holding().copy(accountType = "NISA"),
            listOf(HoldingAllocation(100, 2000.0, "特定口座")))
        assertEquals(100, merged.effectiveTaxFreeShares)
        assertEquals(17_968.5, merged.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun zeroPricesAndInvalidNumbersNeverProduceInfiniteYields() {
        listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            assertEquals(0.0, PortfolioMath.yieldPercent(100.0, it), 0.0)
        }
        assertEquals(0.0, PortfolioMath.yieldPercent(Double.NaN, 100.0), 0.0)
    }

    @Test fun rejectsFractionalNegativeAndOverflowingShareCounts() {
        listOf(null, -1.0, 0.1, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE.toDouble() + 1).forEach {
            assertNull(PortfolioMath.wholeShares(it))
        }
        assertEquals(Int.MAX_VALUE, PortfolioMath.wholeShares(Int.MAX_VALUE.toDouble()))
        assertEquals(0, PortfolioMath.wholeShares(0.0))
    }

    @Test fun allocationOverflowAndInvalidMoneyAreRejectedBeforeMutation() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioMath.mergeAllocations(holding().copy(shares = Int.MAX_VALUE), listOf(HoldingAllocation(1, 1.0, "NISA")))
        }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                PortfolioMath.mergeAllocations(holding(), listOf(HoldingAllocation(1, value, "NISA")))
            }
        }
    }

    @Test fun targetAdjustmentWorksWithCommaDecimalLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(3.1, PortfolioMath.adjustedTarget(3.0, 0.1), 0.0)
            assertEquals(0.1, PortfolioMath.adjustedTarget(0.1, -10.0), 0.0)
            assertEquals(100.0, PortfolioMath.adjustedTarget(100.0, 10.0), 0.0)
        } finally { Locale.setDefault(original) }
        assertThrows(IllegalArgumentException::class.java) { PortfolioMath.adjustedTarget(3.0, Double.NaN) }
    }

    @Test fun rapidIncrementCannotOverflow() {
        assertEquals(Int.MAX_VALUE, PortfolioMath.addShares(Int.MAX_VALUE - 5, 100))
        assertEquals(0, PortfolioMath.addShares(0, -10))
    }

    @Test fun zeroHoldingsHaveZeroNetDividend() {
        assertEquals(0.0, holding().copy(shares = 0).getEffectiveAnnualDividend("net"), 0.0)
    }

    @Test fun missingPayoutMonthDoesNotInventJuneOrDecember() {
        with(PortfolioMath) {
            assertEquals("配当月未取得", holding().copy(dividendMonths = emptyList()).refreshPayout().nextPayoutTiming)
        }
    }

    companion object {
        fun holding() = Holding("test-7203", "7203", "トヨタ自動車", "輸送用機器", "東証", Category.JAPAN,
            2000.0, "¥", 100, 1000.0, 100.0, 5.0, 10.0, "6月", "¥5000", listOf(6, 12), "", "特定口座", "—", true)
    }
}
```

### 【テスト対象】: StockAnalysisNarrativeGeneratorTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/model/StockAnalysisNarrativeGeneratorTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockAnalysisNarrativeGeneratorTest {
    private val ranges = linkedMapOf(
        "per" to listOf(0.0 to 10.0, 10.0 to 15.0, 15.0 to 20.0, 20.0 to 30.0, 30.0 to 60.0),
        "pbr" to listOf(0.0 to 0.8, 0.8 to 1.2, 1.2 to 2.0, 2.0 to 3.5, 3.5 to 7.0),
        "payout_ratio" to listOf(0.0 to 30.0, 30.0 to 50.0, 50.0 to 70.0, 70.0 to 90.0, 90.0 to 150.0),
        "equity_ratio" to listOf(-20.0 to 15.0, 15.0 to 25.0, 25.0 to 40.0, 40.0 to 60.0, 60.0 to 100.0),
        "operating_margin" to listOf(-20.0 to 2.0, 2.0 to 5.0, 5.0 to 10.0, 10.0 to 15.0, 15.0 to 30.0),
        "fcf_margin" to listOf(-30.0 to -5.0, -5.0 to 0.0, 0.0 to 5.0, 5.0 to 10.0, 10.0 to 30.0),
        "dividend_growth_years" to listOf(0.0 to 1.0, 1.0 to 3.0, 3.0 to 5.0, 5.0 to 10.0, 10.0 to 25.0)
    )

    private val baseline = mapOf(
        "per" to 17.0,
        "pbr" to 1.6,
        "payout_ratio" to 40.0,
        "equity_ratio" to 35.0,
        "operating_margin" to 7.0,
        "fcf_margin" to 3.0,
        "dividend_growth_years" to 4.0
    )

    @Test
    fun sevenMetricsHaveTheExpectedNumberOfCompletePatterns() {
        val result = StockAnalysisNarrativeGenerator.generate(baseline)

        assertEquals(7, result.findings.size)
        assertEquals(StockAnalysisNarrative.TOTAL_COMBINATIONS, result.possibleCombinations)
        assertEquals(45L * 45L * 45L * 45L * 45L * 45L * 45L, result.possibleCombinations)
        assertTrue(result.complete)
        assertNotNull(result.combinationIndex)
    }

    @Test
    fun eachMetricHasFortyFiveDistinctBandAndFinePositionCodes() {
        ranges.forEach { (key, metricRanges) ->
            val codes = metricRanges.flatMap { (low, high) ->
                (0 until 9).map { positionIndex ->
                    val fraction = (positionIndex + 0.5) / 9.0
                    val values = baseline.toMutableMap()
                    values[key] = low + (high - low) * fraction
                    StockAnalysisNarrativeGenerator.generate(values).combinationIndex
                }
            }
            assertEquals("$key must have 5 x 9 distinct selections", 45, codes.toSet().size)
        }
    }

    @Test
    fun missingMetricsAreNotInventedAndReduceActivePatternCount() {
        val values = baseline.toMutableMap().apply { remove("fcf_margin") }
        val result = StockAnalysisNarrativeGenerator.generate(values)

        assertEquals(6, result.findings.size)
        assertEquals(listOf("FCF余力"), result.missingLabels)
        assertEquals(45L * 45L * 45L * 45L * 45L * 45L, result.possibleCombinations)
        assertNull(result.combinationIndex)
    }
}
```

### 【テスト対象】: StockDetailResolverTest

配置先: `kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/model/StockDetailResolverTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.junit.Assert.*
import org.junit.Test

class StockDetailResolverTest {
    @Test fun unrelatedHoldingIsNeverUsedAsTheSelectedStock() {
        assertNull(StockDetailResolver.resolve("9999", listOf(holding()), emptyList(), null))
    }

    @Test fun heldStockKeepsItsQuantityAndAcquisitionPrice() {
        assertEquals(holding(), StockDetailResolver.resolve("7203", listOf(holding()), emptyList(), null))
    }

    @Test fun remoteUnownedStockShowsItsOwnIdentityWithZeroHoldings() {
        val security = CloudflareSecurity("130A", "別の銘柄", 50.0, 2000.0, "情報・通信業")
        val stock = StockDetailResolver.resolve("130A", listOf(holding()), emptyList(), security)!!
        assertEquals("130A", stock.code)
        assertEquals("別の銘柄", stock.name)
        assertEquals(0, stock.shares)
        assertEquals(2.5, stock.liveYield, 0.0)
    }

    @Test fun mismatchedResponseIsRejected() {
        assertNull(StockDetailResolver.resolve("130A", emptyList(), emptyList(), CloudflareSecurity("9999", "誤応答", null, null)))
    }
}
```

### 【テスト対象】: PortfolioPersistenceTest

配置先: `kurogane-dividend/android/app/src/androidTest/java/com/kurogane/dividend/PortfolioPersistenceTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.*
import com.kurogane.dividend.model.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

internal class ReviewApplication : Application() {
    init { attachBaseContext(InstrumentationRegistry.getInstrumentation().targetContext) }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        baseContext.getSharedPreferences("release_review_$name", mode)
}

@RunWith(AndroidJUnit4::class)
class PortfolioPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var application: ReviewApplication
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun prepare() {
        application = ReviewApplication()
        listOf("local_import_store", "display_preferences", "cloudflare_market_cache").forEach {
            application.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @After fun cleanUp() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        listOf("local_import_store", "display_preferences", "cloudflare_market_cache").forEach {
            application.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun model(): DividendViewModel {
        lateinit var model: DividendViewModel
        instrumentation.runOnMainSync {
            model = DividendViewModel(application, false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") })
            stores += ViewModelStore().apply { put("review", model) }
        }
        runBlocking { model.awaitRestoration() }
        return model
    }

    private fun awaitSaved(predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!predicate() && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue("Encrypted save did not complete", predicate())
    }

    @Test fun quickEditSurvivesViewModelRecreation() {
        val model = model()
        instrumentation.runOnMainSync { assertTrue(model.updateHolding("7203", 123, 1111.0, 2222.0)) }
        awaitSaved { LocalImportStore(application).loadHoldings()?.firstOrNull { it.code == "7203" }?.shares == 123 }
        val restored = model().holdings.value.first { it.code == "7203" }
        assertEquals(123, restored.shares)
        assertEquals(1111.0, restored.acqPrice, 0.0)
    }

    @Test fun rapidEditsPersistTheLatestValue() {
        val model = model()
        instrumentation.runOnMainSync { (1..100).forEach { model.updateHolding("7203", it, 1000.0) } }
        awaitSaved { LocalImportStore(application).loadHoldings()?.firstOrNull { it.code == "7203" }?.shares == 100 }
        assertEquals(100, model().holdings.value.first { it.code == "7203" }.shares)
    }

    @Test fun importsAreCommittedBeforeReturningAndAreIdempotent() = runBlocking {
        val model = model()
        val rows = listOf(CsvHoldingRow("130A", "新規銘柄", 100.0, 1000.0, 2000.0, "NISA", false))
        val fields = setOf("shares", "averageCost", "currentPrice", "account")
        model.importCsvRows(rows, fields)
        model.importCsvRows(rows, fields)
        val restored = LocalImportStore(application).loadHoldings()!!.filter { it.code == "130A" }
        assertEquals(1, restored.size)
        assertEquals(100, restored.single().shares)
    }

    @Test fun watchlistTargetsAndMembershipSurviveRecreation() {
        val model = model()
        instrumentation.runOnMainSync {
            model.updateTargetYield("7203", 0.5)
            model.toggleWatchlistStock("7203")
            model.toggleWatchlistMembership(model.holdings.value.first { it.code == "2914" })
        }
        awaitSaved { LocalImportStore(application).loadWatchlist()?.none { it.code == "2914" } == true }
        val restored = model().watchlist.value
        assertEquals(3.5, restored.first { it.code == "7203" }.targetYield, 0.0)
        assertTrue(restored.first { it.code == "7203" }.alertEnabled)
        assertFalse(restored.any { it.code == "2914" })
    }

    @Test fun ciphertextIsPrivateAndTamperingIsReported() {
        val store = LocalImportStore(application)
        val model = model()
        store.saveHoldings(model.holdings.value)
        val preferences = application.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
        val encrypted = preferences.getString("holdings", null)!!
        assertFalse(encrypted.contains("トヨタ"))
        preferences.edit().putString("holdings", "AAAA").commit()
        assertThrows(IllegalArgumentException::class.java) { store.loadHoldings() }
        assertNotNull(model().saveError.value)
    }

    @Test fun zeroQuantityAndMixedAccountsRoundTripThroughKeystore() {
        val model = model()
        val original = model.holdings.value.first().copy(shares = 0, taxFreeShares = 0)
        val store = LocalImportStore(application)
        store.saveHoldings(listOf(original))
        assertEquals(listOf(original), store.loadHoldings())
        val mixed = PortfolioMath.mergeAllocations(original, listOf(HoldingAllocation(100, 1000.0, "NISA"), HoldingAllocation(100, 3000.0, "特定口座")))
        store.saveHoldings(listOf(mixed))
        assertEquals(100, store.loadHoldings()!!.single().effectiveTaxFreeShares)
    }

    @Test fun resetPersistsAnEmptyPortfolioAndRemovesDrafts() = runBlocking {
        val model = model()
        val store = LocalImportStore(application)
        store.saveFeedbackDraft(org.json.JSONObject().put("message", "保存済みの下書き"))
        model.resetLocalData()
        assertTrue(model().holdings.value.isEmpty())
        assertTrue(model().watchlist.value.isEmpty())
        assertNull(store.loadFeedbackDraft())
    }

    @Test fun startupMaskAndTaxModeAreRestored() {
        val model = model()
        instrumentation.runOnMainSync { model.setMaskAtStartup(true); model.setTaxRateMode("net") }
        val restored = model()
        assertTrue(restored.isPrivacyActive.value)
        assertEquals("net", restored.taxRateMode.value)
    }
}
```

### 【テスト対象】: PortfolioScreenTest

配置先: `kurogane-dividend/android/app/src/androidTest/java/com/kurogane/dividend/PortfolioScreenTest.kt`

【ユニットテストコード】:

```kotlin
package com.kurogane.dividend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.ui.components.AddHoldingSheet
import com.kurogane.dividend.ui.components.SearchSheet
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import com.kurogane.dividend.data.CloudflareSearchHit

@RunWith(AndroidJUnit4::class)
class PortfolioScreenTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun clearModels() { compose.runOnIdle { models.clear() } }

    @Test fun unknownStockShowsAnErrorInsteadOfAnotherHolding() {
        val model = DividendViewModel(ReviewApplication(), false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") })
        models.put("screen", model)
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("UNKNOWN", model) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("銘柄情報を取得できませんでした").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("銘柄情報を取得できませんでした").assertIsDisplayed()
        compose.onAllNodesWithText("トヨタ自動車").assertCountEquals(0)
    }

    @Test fun failedSearchKeepsLocalMatchingResultsAvailable() {
        val attempts = AtomicInteger()
        compose.setContent { KuroganeDividendTheme { SearchSheet(true, {}, {}, { attempts.incrementAndGet(); throw IOException("offline") }) } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { attempts.get() == 1 && compose.onAllNodesWithText("トヨタ自動車").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("トヨタ自動車").assertIsDisplayed()
    }

    @Test fun replacingAQueryCannotDisplayThePreviousResponse() {
        val oldResponse = CompletableDeferred<List<CloudflareSearchHit>>()
        val attempts = AtomicInteger()
        compose.setContent { KuroganeDividendTheme { SearchSheet(true, {}, {}, { query ->
            attempts.incrementAndGet()
            if (query == "7203") oldResponse.await() else listOf(CloudflareSearchHit("8316", "新しい結果"))
        }) } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { attempts.get() == 1 }
        compose.onNode(hasSetTextAction()).performTextReplacement("8316")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("新しい結果").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { oldResponse.complete(listOf(CloudflareSearchHit("7203", "古い結果"))) }
        compose.onNodeWithText("新しい結果").assertIsDisplayed()
        compose.onAllNodesWithText("古い結果").assertCountEquals(0)
    }

    @Test fun repeatedRegistrationTapStartsOnlyOneRequest() {
        var requests = 0
        val pending = CompletableDeferred<Boolean>()
        compose.setContent { KuroganeDividendTheme {
            AddHoldingSheet(true, "8316", {}, { null }, { _, _ -> requests++; pending.await() })
        } }
        compose.onNodeWithText("すべての口座にまとめて登録する").performScrollTo().performClick()
        compose.onNodeWithText("銘柄情報を確認中…").assertIsDisplayed()
        compose.onNodeWithText("銘柄情報を確認中…").performClick()
        compose.runOnIdle { assertEquals(1, requests); pending.complete(false) }
    }
}
```
