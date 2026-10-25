# 資産管理の不足データ補完（2026-10-25）

## 変更内容

- CSV/DVM取込後、新規保有だけでなく既存の正数保有の日本株も不足確認の対象にした。
- 資産管理画面の表示時と保有コード一覧変更時に、価格・年間配当・配当月・増配率用財務履歴の不足を確認する。銘柄情報、財務、配当月は独立して取得し、既存キャッシュとAPIごとの取得制限を利用する。
- 財務、配当月、銘柄情報、月末価格、分割履歴、価格カタログ、空白月解消候補の失敗を、正常取得後の日次キャッシュと区別した。失敗後は前回試行から30秒以上経過した次の取得呼び出しで再試行できる。30秒ごとの自動通信は追加していない。
- 正常応答で履歴が空の場合は日次制限を維持する。財務の明示的な強制再試行は引き続き通常の制限を回避できる。分析は従来から取得不成立時に試行日時を消しており、その動作を維持する。
- 新しい補完処理は日本株だけを対象にする。米国株の取得経路・為替更新の追加や、未取得時の表示変更は今回の対象外。

## 差分の範囲

- `DividendViewModel.kt`: 共通補完処理、取込後の呼び出し、失敗状態の保存と再取得判定。
- `AssetManagementScreen.kt`: 画面表示・保有コード変更時の補完呼び出しのみ。既存の別作業によるレイアウト変更は保持。
- `MarketFetchPolicy.kt`: 失敗時30秒、成功時既存の日次・12時間制限という判定。
- `MarketFetchPolicyTest.kt` / `AssetDataRefreshTest.kt`: 成功/失敗の制限、既存銘柄の再取込、画面表示、米国株除外、空履歴、月末価格・分割の回復を検証するテスト。

## 検証

- JDK 17で `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 成功。単体テスト165件、失敗0。
- エミュレータで `AssetDataRefreshTest` 6件と既存の `ReleaseFailureRegressionTest` 6件、計12件成功。実際の資産管理Composableを開くテスト、既存CSV/DVM取込の取得契機、失敗後の同日再取得、成功後の重複抑制、空履歴、米国株除外を確認。
- テストは独立したSharedPreferences/ファイル領域と偽HTTPを使用し、利用者の保有データを置換していない。30秒の回復条件は保存試行日時を進めて検証し、端末時計は変更していない。
- エミュレータ `emulator-5554` と実機SCG01 `RFCNC0E4H8J` に生成したAPKを上書きインストール。実機起動後のプロセスと前面Activityを確認し、確認したAndroidRuntimeログに該当クラッシュなし。
- 実運用の通信復旧や個別銘柄の本番データ充足は未検証。今回のAPKには作業ツリーに既存だった別作業の変更も含まれる。
- コミット・プッシュは未実施。既存の変更と同じファイルを編集しているため、公開時は変更箇所単位の選択と依存変更の確認が必要。

## 公開・再インストール手順

下記は未実行の公開手順。`git add -p` では今回の補完・再試行の変更だけを選ぶ。既存のデータ契約変更などへの依存を別途統合し、ステージ済み差分がビルド可能なまとまりであることを確認する。今回の追加テストで使う `AuditApplication` 等は既存テストコードを利用する。

```powershell
Set-Location C:\Codex\Diview
git status --short
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw '既にステージされた変更があります。混在を解消してから進めてください。' }
git add -p -- kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt ai_docs/architecture.md ai_docs/todo.md
git add -- kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/MarketFetchPolicy.kt kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/MarketFetchPolicyTest.kt kurogane-dividend/android/app/src/androidTest/java/com/kurogane/dividend/AssetDataRefreshTest.kt ai_docs/asset-data-refresh-fix.md
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'ステージ済み差分のエラーを修正してください。' }
git diff --cached
if ((Read-Host '無関係な変更が含まれず、必要な依存変更が統合済みであることを確認した場合のみ YES を入力') -ne 'YES') { throw '公開を中止しました。' }
git commit -m 'Fill missing Japanese holding data and allow retries after fetch failures'
if ($LASTEXITCODE -ne 0) { throw 'コミット失敗。プッシュを中止します。' }
git push origin HEAD
if ($LASTEXITCODE -ne 0) { throw 'プッシュ失敗。' }

$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location C:\Codex\Diview\kurogane-dividend\android
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'ビルド失敗。インストールを中止します。' }
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = 'C:\Codex\Diview\kurogane-dividend\android\app\build\outputs\apk\debug\app-debug.apk'
& $adb -s emulator-5554 install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'エミュレータへのインストール失敗。' }
& $adb -s RFCNC0E4H8J install -r $apk
if ($LASTEXITCODE -ne 0) { throw '実機へのインストール失敗。' }
```
