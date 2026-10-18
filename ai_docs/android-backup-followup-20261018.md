# Android バックアップ互換性の追加調査・修正

本書は追加検証前の記録。3 項目の自動復元、スクロール、再試行、保存・非同期処理の後続修正と検証結果、最大 DVM の再現・判断は [追加検証レポート](android-release-acceptance-20261018.md) を参照。現行 branch の再実行コマンドもそちらに記載する。

## 方針と範囲

旧 DVM の読み込みと、機種変更時の OS 自動復元を両方維持する。前回の[リリース前調査](android-release-audit-20261018.md)の R1 / R4 / R5 を中心とした追補である。

旧 DVM の復号処理、スナップショットのファイル名 `diview_auto_backup.json` と version 1 の JSON 項目、Auto Backup / 端末間転送の対象設定、minSdk 26、依存バージョン、画面のデザインと操作は維持する。固定鍵を新しい秘密情報として扱う変更や、端末固有の鍵だけで移行用ファイルを暗号化する変更は行わない。

## 確認した問題と差分

| ID | 箇所 | 根拠・影響 | 対応 |
| --- | --- | --- | --- |
| B1 | `LocalImportStore` の自動復元読み込み | 書き込みにない 10 MiB 制限によって、保存した JSON を復元時に無視する経路がある。大容量時の復元に影響するため高。ソース上の条件を確認 | `AutoBackupSnapshot` へ読み書きを分離し、必要な配列・DVM 種別だけを読み込む。ファイル全体を文字列・JSONObject に展開する経路と 10 MiB の除外をなくす |
| B2 | `LocalImportStore.save` / `load` | API 26 の 8 MiB 超 DVM payload の保存後、読み戻しで AEADBadTagException。変更前・初回修正後の両方で再現。API 35 では同じテストが通った。大容量保存の完全性に影響するため高 | Keystore へ 64 KiB ずつ入力し、各 update の出力をすべて集めて doFinal する。IV / 暗号文 / 認証タグの形式と検証は維持。API 26 の大容量復元・完全性・改ざん検出の集中テスト 2/2、最終回帰 15/15 に合格 |
| B3 | `saveWatchlist` と既存 `WatchlistStock.toJson` | 通常保存側だけ `isNoDividendConfirmed` を書かず、暗号化データの再読込で true が false になる。変更前の回帰テストで再現。中・確認済み | 既存の共通 JSON 変換を通常保存にも使う。旧データで項目がない場合の false は維持 |
| B4 | スナップショットの置換・破損処理 | rename 失敗時は本体へ直接書き込み、破損 JSON は null に置き換えるため、最後の有効データを失う経路がある。破損検出漏れは変更前テストで再現。中 | `AtomicFile` で失敗時の旧ファイルを保持する。破損・未対応 version は原因を保持した IOException とし、既存の復元エラー処理へ返す |
| B5 | 自動復元時の監視銘柄の検証 | 暗号化 preferences を読む経路の数値検証が、スナップショットからの復元時には適用されていなかった。中・ソースで確認 | 既存の検証を共通の JSON 変換へ移し、不正な利回りを暗号化保存する前に拒否する。元ファイルを変更しないこともテスト |

Android 8.0 の [AOSP ChunkedStreamer](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-8.0.0_r1/keystore/java/android/security/keystore/KeyStoreCryptoOperationChunkedStreamer.java#164) では、複数チャンクの出力を結合する際、最初と最後の間の出力を追加しない経路がある。アプリ側でチャンクを分ける修正後、観測した API 26 の認証失敗が解消し、暗号文の期待全長・元データの全バイト一致・改ざん拒否を確認した。認証タグを無視したり復号失敗を成功扱いしたりする対応は行っていない。過去に欠落して保存された暗号文自体を修復する変更ではなく、該当する場合は完全な元 DVM やバックアップからの再取り込みが必要になる。

`AtomicFile` は自身で排他制御しないため、同一プロセスのスナップショット読み書きを同期する。API 26 では書き込み中に `openRead()` が `.bak` を復元しないよう、移行してきた DVM payload の読み戻しを置換開始前に終える。[AtomicFile の仕様](https://developer.android.com/reference/android/util/AtomicFile)

重要な暗号処理の差分は次のとおり。固定鍵やデータ本体の値は含めない。ループ内で各 `update` の出力を集め、最後の `doFinal` による認証を必ず通す。

```diff
- val sealed = cipher.iv + cipher.doFinal(clear)
+ val sealed = cipher.iv + cipher.processChunks(clear)
- return cipher.doFinal(data, 12, data.size - 12)
+ return cipher.processChunks(data, 12, data.size - 12)
```

スナップショット読み込みは、`length() > 10 * 1024 * 1024` で `null` にする処理から、`JsonReader` で必要なフィールドを読み、他の payload を `skipValue()` する処理へ変更した。書き込みも、全 DVM を含む巨大な JSONObject を作る方式から、同じ version 1 の JSON を payload ごとに出力する方式へ変更した。

## 検証記録

- 変更前の追加 5 ケース: API 26 で 2 合格 / 3 失敗。失敗は無配フラグの欠落、破損 JSON の未検出、大容量 DVM の Keystore 認証失敗。大容量テストは JSON のサイズ判定へ到達する前に失敗しており、10 MiB 除外の端末再現とは区別する。
- 初回の修正後: 新規バックアップ 7 ケースと前回の回帰 5 ケースを実行。API 35 は 12/12 合格、API 26 は 11/12 合格。API 26 の大容量 Keystore 問題を継続調査した。既存のアプリ保存ファイルのハッシュはテスト前後で一致。
- JVM 単体テスト: 137/137 合格。初回の Lint は 0 errors / 239 warnings / 3 hints。
- 最新 `main` の DOE 対応 `1739d69b` を取り込んだ debug / androidTest APK の JDK 17 ビルド成功。
- 最終回帰: API 26 / API 35 / 実機 SCG01 API 33 のすべてで 15/15 合格（新規バックアップ 10 ケース＋前回の回帰 5 ケース）。テスト前後で実機の保存ファイル 5 件、各 AVD の 3 件のハッシュが一致。通常 MainActivity の起動と 5 秒後のプロセス生存も各端末で確認。実機のスリープ抑止設定は元へ戻した。
- 既存 `ReleaseReadinessTest`: API 26 / 35 ともに 6/6 合格。
- 既存 `AssetManagementPerformanceTest`: API 26 / 35 ともに 2/3 合格。300 銘柄で表示領域だけを構築して末尾を開くケース、検索状態の復元と月フィルタのケースは通過。再表示後の銘柄 150 の表示 assertion が失敗。保存していた変更前 APK でも API 35 で同じ 1 ケースが失敗し、対象 `AssetManagementScreen.kt` に今回の差分はない。製品の復元処理かテストの同期待ちかは未特定で、テストの削除・期待値変更・範囲外の実装修正は行っていない。
- OS 自動復元経路: API 26 の専用 AVD で、オフラインの `android/com.android.internal.backup.LocalTransport` に 11,185,690 bytes の version 1 スナップショットを含む full backup を保存。アプリデータ消去後に同アプリだけを復元し、起動前のスナップショット SHA-256 一致、端末暗号化 preferences の除外、起動後の保有銘柄表示と暗号化 preferences の再作成を確認。バックアップの有効設定を元に戻した。Google クラウドや実機同士の転送を検証したものではない。
- 最終 JVM / Lint / release ビルド: JDK 17 で 137/137 合格、0 errors / 239 warnings / 3 hints、R8 / リソース縮小 / lintVitalRelease を含む未署名 release ビルド成功。検査の無効化や依存更新はない。
- release 実行: 未署名 APK のローカルコピーを検証用 debug 証明書で署名し、`apksigner verify` 成功。API 26 / 35 で release のインストール、非 debuggable の確認、MainActivity の起動、7 秒後のプロセス生存、資産画面の表示を確認。debug APK へ戻して読み出し、version / 保有・監視件数 / 無配フラグ / DVM payload の SHA-256 が実行前後で一致。API 26 は 11 MiB 超の復元済みデータ、API 35 は 300 銘柄の合成データを使用した。本番署名情報は使用せず、APK の配布・ストア公開は行っていない。
- TalkBack: API 35 の release APK で実サービス（15.0.0.639625893）の接続と `touchExplorationEnabled=true` を確認。集中確認は 3 回で終了。最初の 2 回は Android Accessibility Suite の通知許可ダイアログが操作を遮った。3 回目はダイアログがなく、フォーカスはアクセント色の項目に残り、スライダーはタップ後の 6% のままで上下スワイプによる変更を確認できなかった。したがって対象スライダーの TalkBack 操作成功とは記載しない。3 個の secure settings の復元を確認。音声の録音・聴取、読み上げ品質の評価は未実施。次は Android のユーザー補助設定から手動で有効化して対象操作を確認するか、音声を評価できる環境で検証する。

追加テストは `AuditApplication` の UUID 付き設定名と cache 内の専用ディレクトリを使う。実ユーザーの保存データを初期化しない。実行・ビルド・インストールには JDK 17 を明示する。

重要な変更箇所は `AutoBackupSnapshot.readField` / `write`、`LocalImportStore.writeAutoBackupSnapshot` / `Cipher.processChunks` / `WatchlistStock.toJson` / `JSONObject.toWatchlistStock`。全差分は [PR #7](https://github.com/masacgt/Diview/pull/7) を参照する。ログには処理段階と例外のクラス名だけを記録し、ファイル内容・パス・例外文・スタックトレースは出さない。

代表的な再実行コマンド（SDK 設定済み、対象は専用 API 26 AVD）。既存の非隔離テストを実ユーザーの保存データがある端末で実行しない。

```powershell
$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = 'C:\Android\sdk'
Set-Location 'C:\dv-ra-1017\kurogane-dividend\android'
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5558 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5558 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5558 shell am instrument -w -r -e class com.kurogane.dividend.BackupCompatibilityRegressionTest,com.kurogane.dividend.ReleaseAuditRegressionTest com.kurogane.dividend.test/androidx.test.runner.AndroidJUnitRunner
```

## 保護と復元の制約

- **R1: 旧 DVM 互換性を維持する方針。** 共有固定鍵が公開済みである制約は残る。これはサービスの API トークンではなく、旧ファイルの暗号鍵である。アプリの設定ファイルへ移しても秘匿できず、過去のファイルを遠隔で失効させることもできない。サーバー変換や新しい復元用パスワードの導入は本変更に含めない。
- **R4: OS 自動復元を維持する方針。** スナップショットは引き続きアプリ専用領域の平文 JSON。端末の Keystore で暗号化された preferences は従来どおりバックアップ対象外。API 31 以降のクラウドバックアップの暗号化能力条件も維持する。API 26 を含む全端末でアプリ独自の端末間暗号化を保証するものではなく、外部からの取得・流出は確認していない。
- **R5: 読み戻しと保護方式の問題は別に扱う。** OS 標準のクラウドバックアップには 25 MB/アプリの上限があり、上限超過時はクラウドへ保存されない。50 MiB まで受け付ける旧 DVM を含む全データのクラウド復元を、今回の読み込み修正だけで保証することはできない。容量削減、別の移行方式、対応容量の明確化は別途設計が必要。[Auto Backup の仕様](https://developer.android.com/identity/data/autobackup)
- 個々の DVM payload を復号・再暗号化するメモリは必要であり、受け付け上限の 50 MiB と 4 種すべての最大入力を組み合わせた負荷は別途検証する。

既存テスト `DvmBackupImporterTest.kt` にも旧形式鍵の直書きがあることを追加確認した。本追補の初期読み取りでツール出力へ出たため、以後は値を含む箇所を出力しない方法へ切り替えた。鍵の値は本レポート・新規ソース・PR に転載しない。限定走査だけで機密情報が存在しないとは判断しない。

## 残る作業

同期 IO の負荷と保存完了保証（R2）、通知 Worker の再試行方針（R3）、財務取得結果の再生成時の区別（R6）は本追補で修正していない。上記の既存スクロール復元テストの失敗も未解決。Google のクラウドから実際の別端末への自動復元、最大容量の負荷、全画面の画像比較、TalkBack の対象操作・音声、全 OS・メーカーの網羅、長期間の通知・省電力条件は未検証または未解決として残す。

検証ログ・スクリーンショット・ローカル検証用 APK は `C:\Users\htdn\.codex\tmp\diview-release-audit-20261017` に保存し、Git に含めない。今回の branch push で市場データの同期テストが起動する場合でも、本番書き込みジョブは schedule / 明示 dispatch / 専用 commit marker で制限されていることを workflow で確認した。本作業ではそれらを起動しない。
