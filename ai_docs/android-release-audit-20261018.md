# Diview Android リリース前調査・修正レポート

最新の追加修正・最終候補の検証・判断済みの残課題は [追加検証レポート](android-release-acceptance-20261018.md) を参照。本書の件数・実行例は初回時点の記録であり、現行 branch の再実行コマンドは追加検証レポートに記載する。

調査開始: 2026-10-17 / 報告日: 2026-10-18（JST）

追補: 旧 DVM の読み込みと機種変更時の自動復元を両方維持する方針を受け、バックアップの追加修正・検証を実施した。R1 / R4 は互換性を維持し保護上の制約を残す。R5 の 10 MiB 読み戻し制限と、追加で再現した API 26 の大容量暗号処理の欠落を修正した。最新の結果と残課題は [バックアップ追補](android-backup-followup-20261018.md) を参照。本書の F1〜F6、検証表、ファイル一覧は初回調査時点の記録である。

## 対象と作業の分離

対象は `kurogane-dividend/android/`。開始時の `main` / `origin/main` は `2febde27` で一致していた。元の `C:\Codex\Diview` にある未追跡の添付資料、CSV、R2 調査資料を残し、`C:\dv-ra-1017` の専用 worktree と `codex/android-release-audit-20261017` ブランチで作業した。

終了前の確認でも元の未追跡 4 項目は残っており、並行作業による `ai_docs/as-is-specification.md` の未コミット変更にも触れていない。

初回の仕上げでは `2fd7b23a` へ rebase した。追補では別途入った DOE 対応 `1739d69b` をベースに取り込み、初回コード修正のコミットは `5e15feac` と `08049e1b` へ更新された。バックアップ修正は `9782a5ba`。並行作業の機能・文言を保持し、その変更自体を PR の差分へ混入させていない。初回統合後のクリーンビルドと、追補統合後の関連検査をそれぞれ実行した。

`minSdk = 26`、依存関係、署名設定、保存形式、API のリクエスト・レスポンス形式、通知設定は変更していない。今回の差分ではレイアウト、色、サイズ、余白、フォント、アイコン、表示項目、操作方法を変更していない。取り込みエラーでは既存の表示領域・スタイルを使い、表示する文面を限定した。

## 調査方法と検出範囲

- Kotlin コンパイル、Android Lint、JVM 単体テスト、debug APK / androidTest APK の作成、未署名 release APK の縮小ビルドを JDK 17 で実行。
- 通信、インポート、暗号化保存、ViewModel の復元・保存、Compose の Effect、通知 Worker、認証、Manifest / FileProvider / バックアップ設定を重点的に確認。
- `rg` で例外処理、ユーザー画面への例外文の代入、`!!` / 型キャスト、ループ、CoroutineScope、タイマー、ファイル・ストリーム処理、ログを検索し、呼び出し箇所と既存テストを追跡。
- 機密情報は Python 3.13 の正規表現で限定走査。現行 Android / Web 参照ソース、アセット・サンプル、共有スクリプト、ビルド設定、ルート設定、`.github/`、現行ドキュメントのテキストを開始時点で 201 ファイル、修正後に 204 ファイル確認した。秘密鍵、既知のトークン形式、認証情報の代入、認証情報付き URL、固定暗号鍵のバイト配列を検出対象とし、結果には種類・ファイル・行だけを出力した。今回の差分・新規ファイルについては既存の固定鍵の値が含まれないことも照合した。
- `gitleaks` / `trufflehog` は利用可能な PATH に存在しなかった。正規表現走査は検証済みの専用シークレット検出器の代替を保証しない。Git 履歴全体、`archive/`、市場データ保管用のルート `data/`、ローカル環境・認証ファイル、バイナリは走査対象外。

静的解析と選択したテストで確認した範囲の報告であり、全不具合の検出やリリース品質の保証を意味しない。

## 修正した指摘

位置は本ブランチのファイル・関数を示す。影響度は本調査での評価であり、発生頻度の実測値ではない。

| ID | ファイル・箇所 | 発生条件・根拠 | 影響度 / 確度 | 修正と検証 |
| --- | --- | --- | --- | --- |
| F1 | `MainActivity.kt` の AddHoldingSheet 呼び出し | composition 中に `viewModel.holdings.value` を直接参照。Lint の `StateFlowValueCalledInComposition` エラーを再現。保有一覧の変更だけでは再描画が保証されない | 中 / 静的解析で確認 | 隣接する他の状態と同じ `collectAsStateWithLifecycle()` で購読。シートの UI は維持 |
| F2 | `CsvImportScreen.kt` の CSV / DVM 読み込み・保存失敗、`ImportMessages.kt` | 8 経路で例外の `message` を画面へ直接代入。DocumentProvider の内部パス、JSON の入力断片、暗号処理の詳細を表示する可能性 | 中 / 直接代入は確認済み。実データ漏えいは誘発していない | 固定の既存検証文言だけを許可し、その他は操作に合う既存の日本語メッセージに置換。ログは段階と例外型のみ。単体テストで入力断片・パス・暗号例外を表示しないことを確認 |
| F3 | `DividendViewModel.kt::fetchFinancialData`、`StockDetailScreen.kt` の財務データ再試行 | 取得前に試行日時を保存し、`force` が制限判定に使われていなかった。通信失敗・キャンセル後に明示的に再試行しても取得せず `null` が返る | 中 / 修正前の端末テスト 2 件で再現 | 明示的な再試行だけ試行制限を迂回。正常に保存された当日キャッシュと通常の自動取得制限は維持 |
| F4 | `StockDetailScreen.kt` の分割履歴 Effect、`DividendViewModel.kt::loadShareSplitHistory` | `IOException()` など `message == null` の失敗でエラー状態が消える。再試行も保存済み試行日時で抑止される。再表示ではキャッシュのない失敗を正常な「未取得」として返していた | 中 / 修正前の Compose テストで再現 | 例外の存在からエラー文を決定し、既存のエラー・再試行 UI を表示。失敗後の再表示は通信せずエラーを維持し、明示的な再試行だけ通信する。成功後に再度シートを開く際は通常キャッシュを使う |
| F5 | `StockDetailScreen.kt` の年末株価 Effect / 分割履歴 Effect | suspend 呼び出しを通常の `runCatching` で囲み、キャンセルを失敗値へ変換していた。分割履歴の読み込みフラグに `finally` がなかった | 低〜中 / ソースで確認 | 既存の `runCatchingCancellable` を使い、分割履歴のフラグを `finally` で解除。HTTP の即時中断まで検証したものではない |
| F6 | `SettingsScreen.kt:463` の ThresholdSlider semantics | `steps = value - 1` のため、現在値 1 は連続値、8 は 7 刻みとして伝わり、現在値を変えるたびに値域の説明が変わる。中間検査で値 8 / range 1–10 / steps 7 を API 26・35 の両方で確認 | 中（アクセシビリティ）/ 実行時とソースで確認 | 1〜10 の整数を表す中間 8 刻みに固定。Canvas の描画、位置計算、タップ・ドラッグ・保存処理は変更しない。1・5・10 の各値で一定の range / steps を検証するテストを追加 |
| T1 | `FinancialDataTest.kt` の暦年配当 CAGR 期待値 | 変更前の 131 テスト中 1 件が失敗。既存実装・`ai_docs/todo.md`・コミット `05c513e4` は暦年配当 CAGR を計算する仕様だが、旧テストは NaN を要求 | 中 / 変更前テスト・変更履歴で確認 | 製品の計算を変えず、10% の数値を検証。最新連続 5 年のみ使用すること、開始配当ゼロなら算出しないことを追加検証。既存の欠年・非減配年数の検証も維持 |
| T2 | `ReleaseReadinessTest.kt` の設定 / 監視リスト検索 | OLED テストは有効条件のダークテーマを指定せず、検索テストは既定データの末尾へ追加した未描画行を直接ノード検索していた。スライダー期待値には旧 range 3–10 が残っていた | 中 / 変更前の API 26 で再現し、ソースで確認 | OLED の無効・有効を明示し、LazyColumn 自体をスクロールして対象行を探す。現行の入力範囲 1–10 と F6 の正しい固定刻み数を検証。既存の表示・検索・選択・保存の assertion は維持 |

### 重要な差分

全体の差分は [本ブランチの比較](https://github.com/masacgt/Diview/compare/main...codex/android-release-audit-20261017) を参照する。

```diff
- existingHoldings = viewModel.holdings.value
+ val existingHoldings by viewModel.holdings.collectAsStateWithLifecycle()
+ // AddHoldingSheet へ購読済みの existingHoldings を渡す

- error = it.message ?: resources.getString(R.string.csv_import_label_8)
+ error = reportImportFailure(it, resources.getString(R.string.csv_import_label_8))

- if (!PriceRefreshPolicy.shouldFetch(...)) { /* キャッシュを返す */ }
+ if (!force && !PriceRefreshPolicy.shouldFetch(...)) { /* キャッシュを返す */ }

- shareSplitHistoryError = result.exceptionOrNull()?.message
+ shareSplitHistoryError = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }

- ProgressBarRangeInfo(value.toFloat(), 1f..10f, value - 1)
+ ProgressBarRangeInfo(value.toFloat(), 1f..10f, 8)
```

上記は重要箇所の抜粋である。取り込みエラーの許可リストは部分一致を使わず、`IllegalArgumentException` と固定文言の完全一致だけを認める。キャンセルや VM エラーをユーザー向けエラーへ変換しない。

F6 はアクセシビリティに渡す値域情報の修正である。[ProgressBarRangeInfo の API](https://developer.android.com/reference/kotlin/androidx/compose/ui/semantics/ProgressBarRangeInfo)と[刻み数の定義](https://developer.android.com/reference/kotlin/androidx/compose/material3/Slider.composable)を参照。既存 Canvas の見た目やジェスチャー処理を変更する修正ではない。

## 初回の未修正指摘と判断事項（最新状況は追補参照）

| ID | ファイル・箇所 | 確認できた事実・発生条件 | 影響度 / 確度 | 未修正の理由・選択肢 |
| --- | --- | --- | --- | --- |
| R1 | `DvmBackupImporter.kt:29`、既存 `kurogane-dividend/docs/release-quality-review-full.md:879` | 旧 DVM 互換用の固定 AES 鍵がソースと既存文書にある。GitHub の repository metadata は公開リポジトリと示した | 高（旧バックアップの秘密性）/ 固定鍵・公開状態は確認済み | 鍵の変更だけでは既存バックアップを復元できなくなる。旧形式の読み込み継続と制約の明示、新形式への移行、サーバー側変換などから方針を選ぶ必要がある |
| R2 | `DividendViewModel.kt:131–139,206`、`loadCachedCloudflareSecurityData` / 起動時の JPX マスター読み込み、`ContactFeedbackScreen.kt` の `onDispose` | ViewModel 構築時に暗号化データやキャッシュ・アセットを同期で読み、設定保存や画面破棄時の下書き保存に同期書き込みを使う | 中 / 同期処理は確認済み。長時間停止・ANR は未測定 | 最大データで計測してから、既存の復元完了前に IO へ移す案、Application / ViewModel に保存を移す案を検討。保存完了・破棄時の保証が変わるため一括非同期化していない |
| R3 | `DividendChangeRefreshWorker.kt:57,61` | 失敗時は試行回数にかかわらず `Result.retry()`。他の通知 Worker には `runAttemptCount < 3` の制限がある | 中 / アプリ側に上限がないことは確認済み。長期間の消費電力は未測定 | 最大回数で停止する案、通信エラーだけ再試行して恒久的エラーは停止する案がある。通知の到達性・復旧方針を判断してから変更する |
| R4 | `LocalImportStore.kt::writeAutoBackupSnapshot`、バックアップ XML | 保有一覧・監視一覧・旧 DVM の復号データを含む自動復元用ファイルを平文 JSON で保存する。アプリ専用領域にある。API 31 以降の cloud-backup には暗号化能力の条件がある | 中 / 保存形式は確認済み。外部からの取得は確認していない | 自動復元を維持する保護方式、バックアップ対象から外す方式、ユーザー指定の復元用秘密で暗号化する方式を選ぶ必要がある。Keystore だけに変えると別端末で復元できないため保存形式を変更していない |
| R5 | `LocalImportStore.kt:186–205`、`DvmBackupImporter.kt::decrypt` | 自動復元用 JSON の書き込みにはサイズ制限がないが、読み込みは 10 MiB 超で `null` を返す。旧 DVM 入力は 50 MiB まで許可され、Base64 化した payload も JSON に入る | 高（大容量バックアップからの復元）/ 条件はソースで確認、実バックアップで未再現 | 保存時に上限を設ける案は現行の入力許容量を縮小する。大容量復元の分割・ストリーム処理は設計変更になる。許容量と復元要件を決める必要がある |
| R6 | `DividendViewModel.kt::fetchFinancialData` の試行制限時の戻り値、`StockDetailScreen.kt` の財務取得状態 | 当日試行後にキャッシュがない場合、正常な対象なしと以前の通信失敗・キャンセルがともに `null` になり得る。画面再生成時にはエラー状態を初期化するため、以前の失敗との区別を失う経路がある | 中 / ソース上の条件を確認。端末での画面再生成ケースは未再現 | 今回の F3 は明示的な再試行の復旧を対象とする。404 と一時失敗を区別する取得結果を ViewModel 内で保持する案、結果状態を永続化する案があり、再表示・再起動時の表示と再取得制限の方針を決める必要がある。対象なしを通信エラーと推測する変更はしていない |

R1 はサービスへアクセスする API トークンではなく旧ファイル形式の共有暗号鍵である。本レポート・PR に鍵の値を含めない。初期のソース読み取りでは既存定数を含む箇所がツール出力に出たため、その後の走査を種類と位置だけの出力に切り替えた。アプリ設定・環境変数・リソースへの移動では APK からの取得を防げないため、安全化とは扱わない。[Android の固定暗号鍵の説明](https://developer.android.com/privacy-and-security/risks/hardcoded-cryptographic-secrets)も参照。サーバー側で保持する方式を選ぶ場合も、バックアップ内容のアップロードと運用・プライバシーの変更に事前判断が必要である。

既存の固定鍵は公開済みのものとして扱う必要がある。旧ファイルの鍵はリモートで失効できず、単純な鍵変更では過去ファイルを保護できない。別サービスの認証にも流用していた場合は、管理者がその認証情報を失効・再発行すべきである。流用や外部サービスへの不正アクセスは調査していない。失効・再発行、Git 履歴の改変、既存公開文書の書き換えは実行していない。

R3 の再試行は OS 上でバックオフされる処理であり、CPU を回し続ける即時ループとは区別する。[WorkManager の再試行仕様](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#retry_and_backoff_policy)を参照。

認証情報付き URL の検出候補は `CloudflareMarketRepositoryTest.kt:122` と既存の同コード掲載文書にある不正 URL の検証用入力だった。公開 API の base URL は接続先であり、認証用秘密とは扱っていない。限定走査では、これら以外の既知形式のサービス認証情報を検出しなかった。未検出を機密情報の不存在の保証とはしない。

## その他の確認と限界

- 通信は `withContext(Dispatchers.IO)` にあり、接続・読み取りタイムアウト、リダイレクト禁止、応答サイズ上限、`finally` の切断、ストリームの `use` がある。キャンセル時もブロッキング IO はタイムアウトまで残る可能性があり、即時切断は未検証。
- 保有情報・監視一覧の主要な復元と保存は IO 上で実行し、ViewModelScope の保存要求を直列化している。R2 の同期経路と、NonCancellable 内の保存・CAS 再試行は別途負荷計測が必要。
- メインの戻る操作ループには popBackStack 失敗時の終了条件がある。長押しステッパーは Effect と `delay`、ポインター処理は pointerInput の寿命に従う。分岐だけで全画面遷移・すべてのタイミングを検証したものではない。
- 型キャスト・`!!` の検索では、確認した多くの箇所に直前の型・null・要素数条件があった。本修正とは独立した型のクラッシュは確定していない。
- API 26 を超える無保護の API 使用は今回の Lint で検出されなかった。Lint はすべての実行時互換性問題を検出するものではない。
- Lint の依存更新提案、未使用リソース、文字組み・Modifier・不要な API 条件、KTX 提案などは、依存更新や見た目・仕様の変更を伴うため自動対応していない。

## 初回の検証結果

| 検査 | 変更前 | 修正後 |
| --- | --- | --- |
| `testDebugUnitTest` | 131 件、1 失敗（T1） | 137 件、失敗・エラー 0。統合後のクリーンビルドと、追加修正後の再実行で確認 |
| `lintDebug` | 1 error / 239 warnings / 3 hints（F1） | 0 errors / 239 warnings / 3 hints。検出の無効化・baseline 追加なし |
| `assembleDebug` / `assembleDebugAndroidTest` | 両方作成成功。`--continue` の全体終了は上記 2 検査の失敗で非ゼロ | 両方作成成功。追加したクラス・テストが DEX に含まれることも確認 |
| 未署名 `assembleRelease` | 未実施 | R8 / リソース縮小 / lintVitalRelease を含めて成功。署名用環境変数 4 個はいずれも未設定 |
| 新規の回帰テスト / API 35 | 最初の 3 件はすべて失敗を再現。後から追加した 2 件は変更前未実施 | 5/5 合格 |
| 新規の回帰テスト / API 26 | 未実施 | 5/5 合格 |
| 既存 `ReleaseReadinessTest` / API 26 | 6 件中 4 合格 / 2 失敗。失敗した 2 件の単独再実行でも同じ結果（T2） | 6/6 合格 |
| 既存 `ReleaseReadinessTest` / API 35 | 未実施 | 6/6 合格 |
| 実機 SCG01 / API 33 | 今回の変更前確認は未実施 | 新規回帰 5/5 合格。既存保存ファイル 5 件のテスト前後 SHA-256 が一致 |
| debug APK / test APK インストール | 今回の変更前は専用 AVD のみ実施 | API 26・35 の専用 AVD と実機 API 33 の 3 台すべて成功 |
| 通常 MainActivity の起動 | 今回の変更前確認は未実施 | 3 台すべて `Status: ok`、起動 5 秒後のアプリプロセス生存を確認 |

新規テストは毎回異なる SharedPreferences 名と専用のファイル領域を使い、`applicationContext` 経由の保存も隔離する。ネットワークは注入した HttpURLConnection で模擬し、実サービスへ送信しない。既存テストは今回新規に作った専用 AVD でのみ実行する。

既存の共通 `ReviewApplication` は SharedPreferences 名の変更だけで、`applicationContext` / `filesDir` まで分離していない。既存一式を実機へ適用する前に保存先の分離を拡張する必要がある。本作業では既存テストを専用 AVD に限定し、実機では新規の分離済みテストだけを使った。

専用 AVD は `DiviewReleaseAudit26`（`emulator-5558`）と `DiviewReleaseAudit35`（`emulator-5556`）。検証後はこの 2 台だけを停止し、元から動いていた `emulator-5554` には触れていない。実機の最終検査では一時的にスリープ抑止を有効にし、検査後に元の設定値へ戻ったことも確認した。

ビルド途中に追加ヘルパーを import 文の途中に置きコンパイルが失敗した。配置を直して再検査した経過を、変更前の既存失敗とは分けて記録している。実機の初回はスリープ状態で UI の 1 件が `No compose hierarchies` となり、ロジック 3 件は合格した。端末の wake 状態を確認して画面を起こした後、同じ APK の 4 件がすべて合格した。失敗した初回ログも残している。

T2 の前提を整えた中間版では、既存 6 件中 5 件が API 26・35 の両方で合格した。残る設定テストを調べて F6 の値域情報の不具合を確認し、製品コードとテストの旧値域を修正した。この中間失敗ログも最終結果で上書きせず残した。

ビルド、インストール、起動、Compose の対象操作の成否は別々に記録する。未署名 release APK のインストール・操作、ストア署名、リリース公開、本番デプロイは行わない。

### 再実行用コマンド

以下の端末 ID は本作業で利用した対象。署名用の `DIVIEW_RELEASE_*` 4 変数が未設定の PowerShell を使い、対象端末・専用 AVD を起動してから実行する。既存テストを実ユーザーの保存データがある端末で実行しないこと。実機に対して記載している新規テストは保存先を隔離している。

```powershell
$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = 'C:\Android\sdk'
Set-Location 'C:\dv-ra-1017\kurogane-dividend\android'
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4096m' '-Pkotlin.compiler.execution.strategy=in-process' :app:clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
adb devices -l
foreach ($auditDevice in @('emulator-5558', 'emulator-5556', 'RFCNC0E4H8J')) {
    adb -s $auditDevice shell input keyevent 224
    adb -s $auditDevice install -r app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw "Install failed: $auditDevice" }
    adb -s $auditDevice install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw "Test APK install failed: $auditDevice" }
    adb -s $auditDevice shell am instrument -w -r -e class com.kurogane.dividend.ReleaseAuditRegressionTest com.kurogane.dividend.test/androidx.test.runner.AndroidJUnitRunner
    # adb の終了コードだけで合格とせず、出力の OK / FAILURES を確認する
}
```

## 初回の未検証範囲と引き継ぎ

R1〜R6 の判断・修正、実サービスの障害や認証失効、通知の長期間の配信・省電力条件、画面離脱と HTTP 中断の厳密なタイミング、最大データでのメモリ・フレーム時間、全画面の画像比較、実際の TalkBack サービスでの読み上げ・ジェスチャーは未検証。API 27〜32、34、36 や各メーカー全機種の動作も網羅していない。

## 初回の変更ファイルと成果物

| 区分 | ファイル |
| --- | --- |
| Android 本体 | `MainActivity.kt`、`ui/ImportMessages.kt`、`ui/screens/CsvImportScreen.kt`、`ui/screens/SettingsScreen.kt`、`ui/screens/StockDetailScreen.kt`、`viewmodel/DividendViewModel.kt` |
| 自動テスト | `ReleaseAuditRegressionTest.kt`、`ReleaseReadinessTest.kt`、`FinancialDataTest.kt`、`ImportMessagesTest.kt` |
| 調査・開発記録 | `ai_docs/android-release-audit-20261018.md`、`ai_docs/todo.md`、`ai_docs/trouble.md` |

[PR #7](https://github.com/masacgt/Diview/pull/7) を `codex/android-release-audit-20261017` から `main` 向けに作成した。検証ログ・変更前 XML・限定走査結果はローカルの `C:\Users\htdn\.codex\tmp\diview-release-audit-20261017` に保存した。APK・Gradle キャッシュ・生成された検査レポート・端末ログはコミット対象に含めない。
