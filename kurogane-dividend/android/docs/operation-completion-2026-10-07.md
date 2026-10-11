# 未実装操作の調査・実装記録（2026-10-07）

対象は `kurogane-dividend/android/`。実装・ビルドともに共通の Android ソースを使用した。`archive/` は参照・変更していない。既存の配色・カード・タブ・口座入力欄を維持し、共有・口座登録の既存シートはそれぞれ独立した Kotlin ファイルに移した。依存ライブラリ・Android 権限の追加はない。

## 発見した操作と採用した動作

| 操作 | 調査結果・根拠 | 実装した動作 |
| --- | --- | --- |
| ロードマップ「画像で保存」 | クリック処理が空。既存の共有シートに進捗・認定証・金額マスク・カードが存在 | 同じシートを保存モードで開く。選択したカード部分だけを PNG 化し、Android 10以降は `Pictures/Diview`、Android 8・9はシステムの保存先選択を使用。成功・失敗は既存画面と同じ Toast で通知 |
| ロードマップ「達成状況をシェア」の実行 | 既存の実行ボタンはシートを閉じるだけ | 同じ PNG を一時ファイルとして作成し、Android の共有先選択画面を開く。共有先の選択・送信はユーザーが行う。共有先選択のキャンセルを送信成功扱いにしない |
| 進捗カード・踏破認定証の選択とマスク | QUEST 6・LV.1・LEVEL 2など固定表示。未達成でも認定証を選択でき、設定はシートを閉じると消失 | 現行30段階の進捗・完了レベル・目標額から内容を生成。10 STEP未満は認定証を無効化。最後のSTEP達成時は100%で全達成表示。選択・マスクは設定へ永続化し、保存と共有で共用。画像内の実績額・目標額・認定証の月年額をマスク。アプリの金額非表示中に開いた場合もマスクを有効化 |
| ロードマップ銘柄コード「適用」・候補チップ | 「適用」はコメントのみ。名前・価格・配当は仮の固定値で、未知のコードにも架空の名前を表示 | 既存の保有・監視データを優先し、なければ既存の銘柄取得 API で解決。選択できた銘柄の名前・価格・配当を表示し、取得単価欄に現在値を反映。通信失敗・未解決コードを表示して登録を止める |
| ロードマップ「ポートフォリオに一括追加登録」 | ローカルにない銘柄は登録不能。数値エラーは無言で終了し、保存完了を待たなかった | 4口座の数量・価格を検証し、既存の口座合算・暗号化保存を再利用。保存後に画面へ反映してシートを閉じる。保存失敗時に株数を増やさず、再試行で二重加算しない。処理中の連打を抑止 |
| 監視銘柄「銘柄追加」「新規追加」 | どちらも固定のトヨタ株を保有株に追加する仮処理 | 既存の検索シートで銘柄を選び、監視リストへ追加・永続化。重複登録はせず、保有株数・取得額は変更しない |
| 受取月別試算「空白月解消に追加」 | 候補の積水ハウスを、固定の100株・取得単価で保有株へ追加していた | 表示された候補コード1928を既存データ/APIで解決して監視リストへ追加。保有株への登録は既存の「保有株に登録」からユーザーが数量を入力する |
| 監視銘柄の「買場アラート」スイッチ | 常にON、変更処理が空 | 既存の全体の株価通知設定と接続。ON時は既存の通知権限処理、OFF時は既存のスケジュール停止を使用。各銘柄の個別ON/OFFは保持 |
| 「権利落ち月マトリクス」「受取月別グラフ試算」 | 開く操作自体は接続されていたが、4銘柄・月別金額・四半期比率・寄与度がサンプル固定で監視内容に反応しなかった | 現在の監視銘柄・配当・換算レート・取得済み配当月から試算。既存の購入単位（日本株100株・米国株1株）を使う。取得済み配当月へ年額を均等配分し、未知の月・為替を補完しない。権利落ち日と実際の入金日はAPIにないため未取得と明記 |
| 設定「配当利回りの計算基準」 | 設定画面内の変数だけを変更 | 永続化し、資産管理の平均配当利回り・銘柄別利回り・利回り順に評価額/取得額の選択を反映。両方を明示して併記する年間配当・詳細画面の数値は元の意味を保持 |
| 設定「目標利回り（ロードマップ試算）」 | 設定画面内の変数だけを変更。ロードマップは4%固定 | 3.5/4.0/4.5%を永続化し、必要追加元本と試算表の強調する利回りへ反映 |
| 設定「構成比『その他』集約閾値」 | スライダーは動くが、構成比と未接続 | 3〜10%を永続化し、年間配当画面の銘柄別構成比（保有額/配当）で閾値未満を「その他」へ集約。合計額・銘柄数を保持。業種の詳細一覧は元データを引き続き表示 |
| 設定「アプリをレビューで応援」 | コールバックが空 | 現行アプリIDのストア詳細画面を開く。ストアアプリがなければブラウザへ、どちらも開けなければ失敗通知。Web版の現行ソースにも同じGoogle Play URLがある。公開済み掲載ページの存在までは確認できていない |

画像生成には既存 Compose の GraphicsLayer を使用。描画済みの設定と現在のマスク設定が一致するまで出力を許可せず、同一フレームの二度押しも拒否する。共有は専用キャッシュディレクトリだけを FileProvider へ公開し、選択先へ一時的な読み取り権限を渡す。ギャラリーへの書き込みは完了前の画像を非公開にし、失敗した作成先を削除する。Android 8・9の保存先選択をキャンセルした場合は一時画像を破棄する。

## 全画面の確認範囲と対象外とした操作

コード中のボタン・アイコン・クリック・選択・スイッチ・入力・ジェスチャーを列挙し、実際の呼び出し元から ViewModel、保存、遷移、通信まで追跡した。静的な試算セルやラベルをボタン扱いにはしていない。

| 範囲 | 正常接続を確認した経路・対象外の判断 |
| --- | --- |
| MainActivity・共通ナビゲーション | 4タブ、戻る、設定、検索、銘柄詳細、CSV、サポート、利用規約へ遷移。通知ダイアログは既存の条件抽出を表示。認証解除・認証設定は既存の認証処理 |
| 資産管理・並び替え・クイック編集 | 検索・分類・配当月フィルター・並び替えは一覧へ反映。編集は `updateHolding` → 保存キュー → 暗号化保存。削除は保存とSnackbarの取り消しへ接続 |
| 年間配当・分析・資産推移 | タブ、年/月の選択、銘柄/業種選択、展開、更新・再試行、積立額/利回り/再投資試算を確認。試算の一時的な選択は永続設定と誤認しない |
| ロードマップ | ステージフィルター・STEP展開は接続済み。3つの利回りセルは静的な比較結果で、クリック処理の欠落ではない |
| 監視銘柄・並び替え | 検索/分類、利回り目標変更、銘柄ごとの通知、詳細、保有株登録、削除と取り消しを確認。並び替えの下書きは「適用する」で反映 |
| 銘柄詳細 | 読み込み失敗の再試行、分析区分、追加株数シミュレーション、ブックマーク追加、配当通知、編集、確認付き削除を追跡。未保有銘柄の編集/削除無効は意図的 |
| 検索シート・共通保有株登録シート | API検索/端末候補/分類/選択と登録先を確認。登録は実際の銘柄解決と数量検証を通る。空のデフォルト引数がある部品も、MainActivityの本番呼び出しでは実処理を渡している |
| CSV・バックアップ取込 | ファイル選択 → サイズ・形式検証 → プレビュー選択 → ViewModel → 暗号化保存 → 元画面へ遷移。キャンセル・失敗・読み取り中の無効化を確認 |
| 設定・データ書き出し | CSV/JSONはシステム保存先選択とストリーム書き込み。テーマ・アクセント・税・為替・起動時マスク・通知・認証・確認付き初期化は既存処理と保存へ接続 |
| 問い合わせ・FAQ | FAQ展開、分類・入力・診断情報選択、暗号化下書き保存、既存 `POST /api/v1/feedback`、受付番号確認まで実装済み。仮の送信通知ではない。本番への問い合わせ送信は行っていない |
| 利用規約・プライバシー | 表示タブと条項の開閉は接続済み |
| 未使用の部品 | `ExDividendMatrixDialog` は本番ソースからの呼び出しがなく、画面の有効な操作に数えない。資産管理の旧 `showAddAssetModal` 分岐も開く経路がなく、現行追加シートと区別した |

## 変更ファイル

すべてこの Android ディレクトリ配下。

- `app/src/main/java/com/kurogane/dividend/ui/screens/`: `RoadmapScreen.kt`、`RoadmapShareSheet.kt`（追加）、`RoadmapAllocationSheet.kt`（追加）、`WatchlistScreen.kt`、`SettingsScreen.kt`、`AssetManagementScreen.kt`、`AnnualDividendScreen.kt`。
- `app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt`: 設定保存・復元・初期化、監視銘柄登録、保存完了前に二重加算を起こさない口座登録。
- `app/src/main/java/com/kurogane/dividend/model/`: `PortfolioMath.kt`、`RoadmapAchievement.kt`（追加）、`WatchlistProjection.kt`（追加）。
- `app/src/main/java/com/kurogane/dividend/data/RoadmapImageStore.kt`（追加）、`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/roadmap_image_paths.xml`（追加）、`app/src/main/res/values/strings.xml`。
- `app/src/test/java/com/kurogane/dividend/model/OperationCompletionTest.kt`（追加）、`app/src/androidTest/java/com/kurogane/dividend/OperationCompletionTest.kt`（追加）、`app/src/androidTest/java/com/kurogane/dividend/ReleaseReadinessTest.kt`。
- 本記録。

## 検証・成果物

### ビルド・静的検査

- JDK 17 (`C:/Users/htdn/.jdks/ms-17.0.20.1`) を使用。端末の既定JDK 25ではGradleが起動できなかったため、ビルド時だけ既存のJDK 17を指定した。
- 共通の `app/src/main/` から `assembleDebug`、`assembleDebugAndroidTest`、`assembleRelease`、`testDebugUnitTest`、`testReleaseUnitTest`、`lintRelease` がすべて成功。リリースはR8による縮小・最適化まで完了。
- 単体テストは debug / release それぞれ119件、失敗・エラー・スキップ0件。達成段階、100%の上限、利回り基準、構成比閾値、月別試算の未知データ処理を追加検証した。
- リリースLintはエラー0件・警告135件。未使用リソース、KTXや依存定義への提案、依存更新などの警告は残している。
- `git diff --check` は成功。APKと `app/build/` はGitの除外対象であり、署名情報・生成物はGitへ追加していない。
- 主なログ: `app/build/operation-final-verification.log`、`app/build/operation-final-test-build.log`。Lintの詳細: `app/build/reports/lint-results-release.html`。

### エミュレーターでの確認

Android 15 / API 35 の `Diview_API35` を使用。実機での確認ではない。

- 既存テストと追加テストを含む全体53件が成功（`app/build/operation-all-instrumentation.log`）。
- 利回りの表示ラベル修正後、関連画面7件が成功（`app/build/operation-final-screen-tests.log`）。
- 最終の文字切れ補修後、操作8件と拡大文字表示1件の計9件が成功（`app/build/operation-final-focused-tests.log`）。この実行には、その後追加した銘柄適用→不正入力拒否→口座登録のUIテスト1件を含む。53件の全体実行とこの追加1件を合わせた54件について実行結果があるが、最終状態で全54件を一括再実行したという意味ではない。
- PNGが実際にMediaStoreへ1件保存され、画像として読み出せることを確認。同一フレームで保存処理を2回呼んでも1件のみ生成された。金額マスク切り替え後の画像も取得して目視確認した。
- 認定証を選択した共有操作からAndroidの共有先選択画面が開くことと、戻る操作でキャンセルできることを確認。送信先アプリは選んでいない。
- 未達成認定証の無効化、一時読取権限付き共有URI、保存先書き込み成功/失敗、設定と監視銘柄の復元、保有株数を変えない監視追加を確認。
- 口座登録の保存失敗を注入し、失敗時に株数が増えず、再試行時に1回分だけ加算されることを確認。
- ライト/ダーク両テーマ、文字サイズ2倍・表示密度約115%で14画面/シートのスクロールを検証。追加した共有・口座登録シートで見つかった固定高さによる文字切れを修正し、更新後の画面画像も目視確認した。操作ボタンまでスクロールできることをテストしている。
- 確認用画像: `app/build/operation-check-masked.png`、`app/build/operation-screens/`。これらも生成物としてGitへ追加していない。

### APK

| 種別 | パス（このAndroidディレクトリからの相対パス） | サイズ | 状態 |
| --- | --- | --- | --- |
| デバッグ | `app/build/outputs/apk/debug/app-debug.apk` | 20,906,837 bytes | デバッグ署名検証成功。API 35へインストールして上記テストを実行 |
| リリース | `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,125,540 bytes | ビルド・16KBアラインメント検査成功。製品署名情報がないため未署名 |

アプリIDは `com.kurogane.dividend`、versionCode 1 / versionName 1.0.0、minSdk 26 / targetSdk 36。追加のストレージ権限はない。

SHA-256:

- Debug: `a1f0c4b55616511e63a9476aec447cc1dce15eeaceb7825623d9955701181b12`
- Release unsigned: `0ed0fc81aaf62e987d789e24c9217a23e9f002e6b59f73345a3331ddf8c97da8`

## 残る制約

- 権利落ちの正確な日付・実際の入金予定日は、現行のAPI/データ構造にない。これらを確定表示するには、その情報を取得するAPI仕様・項目と実データが必要。現在の月別表示は取得済み配当月への均等配分試算である。
- Google Playの公開済み掲載先を確認できていない。公開後、`com.kurogane.dividend` の掲載先が正しいことを確認する必要がある。
- Android 8・9でのシステム保存先選択、実機、受け取り先アプリでの実際の送信完了、製品署名付きリリースの端末動作は未確認。API 35での検証と区別する。
- 製品署名に必要な `DIVIEW_RELEASE_STORE_FILE`、`DIVIEW_RELEASE_STORE_PASSWORD`、`DIVIEW_RELEASE_KEY_ALIAS`、`DIVIEW_RELEASE_KEY_PASSWORD` は未設定。署名済み製品APKを作るにはこれらを外部から設定して再ビルドする。鍵・パスワードはGitへ入れない。

実装方法の確認には Android 公式の [Compose画像化](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)、[MediaStore](https://developer.android.com/training/data-storage/shared/media)、[保存先選択](https://developer.android.com/training/data-storage/shared/documents-files)、[一時的な共有権限](https://developer.android.com/training/secure-file-sharing/share-file) を参照した。
