# Diview Android リリース前の追加検証

2026-10-18。初回の [調査](android-release-audit-20261018.md) と [バックアップ追補](android-backup-followup-20261018.md) に続く記録。過去の実行結果はそのまま残し、今回の候補については本書を参照する。[PR #7](https://github.com/masacgt/Diview/pull/7)。

## 対象と判断

- 隔離 worktree `C:\dv-ra-1017`、branch `codex/android-release-audit-20261017`。基準の製品コードは `d0681f21`（取り込んだ main は `cf22ffb1`）、テスト基盤は `2bebaf93`、CI 起動修正は `08cbf5aa`。その後、承認された小画面ソート修正を追加し、再ビルド・再テストした。元 checkout の未コミット変更は取り込んでいない。
- Android のビルド元は `kurogane-dividend/android/`。すべて JDK 17 (`C:\Users\htdn\.jdks\ms-17.0.20.1`) を明示し、Gradle は直列実行した。API 26 以上、既存の依存バージョン、通常画面のデザイン・操作方法、API、通知設定、既存の保存キーを維持した。ユーザー判断により小画面のソートシートだけ縦スクロールを許可した。main 側の並行した画面変更も保持している。
- ユーザー判断により旧 DVM 読み込みと OS 自動復元を維持する。株主優待・景気区分上書き・配当履歴上書きの 3 項目を移行対象へ追加した。問い合わせ下書きは対象外。
- 追加の検証用実機・Google アカウントと Play Console は用意できないとの回答を受領。実際のクラウドによる別端末復元と Play 内部テストは実施していない。既存の SCG01 API 33 は途中で再接続されたため、隔離テストを実施した。
- 並行作業の main `cf1be44c` には配当取得間隔の更新があり、最新 fetch の `d736c216` には R2 専用 income advice 起動処理が加わっている。検証ブランチを最新 main と仮統合した merge-tree は競合なし。今回それらを候補へ取り込まず、元実機のアプリ・テスト APK は保存・復元して実データを保持した。候補の通常 MainActivity は実機で起動していない。実機データの初期化・root 化はしていない。
- API 26 の最大 DVM 負荷で確認したメモリ不足については、ユーザーが「再現結果と設計案を記録し、他の修正・検証を完了する」を選択した。保存方式・受け付け上限の変更と追加の最大負荷試験は行わない。

## 指摘と差分

確度の「確認」はコードと再現・自動テストによるもの。影響度はリリース判断の目安であり、発生頻度の実測ではない。

| ID | 場所・発生条件／根拠 | 影響度・確度 | 対応 |
|---|---|---|---|
| B6 | `LocalImportStore.saveShareholderBenefits/saveStockClimateOverrides/saveDividendHistoryOverrides`。端末固有の暗号化設定には保存されるが、旧移行 JSON に 3 項目がない | 高・確認 | 修正。version 1 に任意の `overrides` オブジェクトを追加し、旧ファイルの読み込みを維持。転送直後の編集で残る 2 項目を消さず、リセット後も復活させない |
| R6 | `DividendViewModel.fetchFinancialData`。失敗後に当日取得制限へ入り、画面／ViewModel 再生成後に失敗と 404 がともに null になる | 中・確認 | 修正。安全な失敗種別だけを保存し、再取得前も失敗を再現。成功と 404 は失敗マーカーを消去。明示的な再試行とキャンセル例外の再送出を維持 |
| P1 | `AssetManagementScreen` の初期 list flow とスクロール保存。再表示時にヘッダーだけのリストが保存位置を 0 に戻す | 中・確認 | 修正。初回の実データを待ち、保存済みの index/offset を戻してから位置保存を開始。300 件の既存テストの assertion は維持 |
| R3 | `DividendChangeRefreshWorker`。恒久的な HTTP エラー等でも無期限に retry し得る | 中・確認 | 修正。通信例外・408・429・5xx を含むバッチだけ、初回に加えて最大 3 回再試行。恒久的な 4xx／形式エラーだけなら終了。混在時は既存の方式に沿ってバッチ全体を再処理。次の定期実行は維持 |
| R2 | `DividendViewModel` の初期暗号化データ読出し・銘柄マスター読込 | 中・確認 | 部分修正。既存の復元 coroutine の IO 区間へ移動し、復元完了前に 3 項目を反映。設定の小さな同期 commit、問い合わせ下書きの破棄時保存等は保存順序・完了保証を維持して残した |
| R5-M | `AutoBackupSnapshot.readBackup` と `LocalImportStore.saveBackup`。API 26 で約 50 MiB の旧 DVM 相当 payload を複数種類保存すると OOM | 高・確認 | 未修正。ユーザー判断により記録で終了。下記の再現と設計案を参照 |
| R1 | `DvmBackupImporter.kt` の鍵定義、`DvmBackupImporterTest.kt`、`docs/release-quality-review-full.md` | 高・確認 | 旧互換性のため残す。共有固定の旧ファイル暗号鍵であり、サーバー用 API トークンではない。値は本書・PR に記載しない |
| R4/R5 | `LocalImportStore.writeAutoBackupSnapshot` と backup XML。移行 JSON はアプリ専用領域の平文。OS クラウドにはアプリごとの容量制約 | 高・確認 | 自動復元を維持。端末固有の暗号化 preferences のバックアップ除外、API 31 以降のクラウド暗号化能力条件を維持。全端末・全データの安全なクラウド復元を保証したものではない |
| V1 | `ReleaseReadinessTest.watchlistSortOrderSurvivesLeavingAndReturningToTab` と `WatchlistScreen` のソートシート。API 26 の小画面でカードとシート末尾が見切れる。CI/全件 suite では初期カード順の待機が timeout | 中・小画面操作は再現、全件失敗は確認 | 部分修正。ユーザー承認によりソートシート Column だけ縦スクロールを追加。480×800/密度240 の対象テストは1/1成功し、適用・タブ再表示・ViewModel再生成後の順序復元まで確認。全件 API 26 は89件中1件失敗し、同じテストのシート表示前の初期カード待機（164行）で timeout。標準画面の表示は API 35 の33枚すべて一致、API 26 は Watchlist の全5枚一致し、別画面3枚だけ各1–2ピクセル・最大色差1/255。CI の API 26 結果は未解決として追跡 |

主なコードは [AutoBackupSnapshot](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/AutoBackupSnapshot.kt)、[LocalImportStore](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/LocalImportStore.kt)、[CachedFetchFailure](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/CachedFetchFailure.kt)、[DividendViewModel](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt)、[AssetManagementScreen](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt)、[WatchlistScreen](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/WatchlistScreen.kt)、[ReleaseReadinessTest](../kurogane-dividend/android/app/src/androidTest/java/com/kurogane/dividend/ReleaseReadinessTest.kt)、[NotificationRetryPolicy](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/notifications/NotificationRetryPolicy.kt)。差分全体は PR を参照する。

重要な変更前後は次のとおり。鍵やファイル全体は再掲しない。

```diff
 // 財務データがなく当日再取得できない場合
-return cached
+if (cached == null) CachedFetchFailure.decode(savedCategory)?.let { throw it }
+return cached

 // 移行 JSON (version 1)
 { version, holdings, watchlist, dvmBackups,
+  overrides: { shareholder_benefits, stock_climate_overrides, dividend_history_overrides }
 }

 // スクロール復元
-ヘッダーだけの初期状態から現在位置を保存
+初回 list flow の値を待ち、保存位置を復元してから現在位置を保存

 // 小画面のソートシート
 .fillMaxWidth()
- .padding(16.dp)
+ .verticalScroll(rememberScrollState())
+ .padding(16.dp)
```

失敗種別は timeout/offline/HTTP status/generic のみで、レスポンス本文・例外文・保有銘柄・認証情報を含めない。ユーザー表示は既存のエラー領域と日本語メッセージを利用する。保存を非同期化した箇所では、既存の `awaitRestoration` と最初の永続保存の順序を維持している。

## 検証結果

以下は別々の検証段階である。ビルドだけでインストール・起動・操作の成功とは扱わない。

GitHub Actions run `39089999129` の完了結果は build 成功、API 26 が89件・failure 1・error 0・skip 1、API 35 が89件・failure 1・error 0・skip 0。秘匿済みログでは両方とも `watchlistSortOrderSurvivesLeavingAndReturningToTab` の `ComposeTimeoutException` を確認したが、どの待機条件かは特定できない。ローカル API 26 全件では初期カード順待機（テスト行164）、SCG01 API 33 では Apply 操作（テスト行178）で失敗し、各環境で待機箇所が一致するとは断定しない。この CI は製品コード commit `9952cd7a` を検証し、後続の `0485440c` はレポート更新のみ。

追加の SCG01 API 33 確認では、候補 debug APK と計装 APK を一時インストールし、`watchlistSortOrderSurvivesLeavingAndReturningToTab` を実行した。実機の物理解像度は 1440×3200、density override は 460（論理サイズは約502×1113）で、480×800 条件ではない。テストは「適用」ボタンの `performScrollTo()`（テスト行178）で `AssertionError` となり、実機でのソート完了は確認できなかった。元のアプリ／計装 APK は保存した各 SHA-256 と一致する状態へ復元した。確認したアプリデータ7項目のうち6項目はハッシュ一致し、`files/profileInstalled` のみ変化した。プロファイル導入時のランタイムマーカーである可能性はあるが、原因は未確定として扱う。通常 MainActivity の手動起動や小画面の実機操作はしていない。

| 項目 | 変更前／切り分け | 今回の候補 |
|---|---|---|
| JVM | main `2479fb59`: 131 件中 1 件失敗（暦年 CAGR の古い期待値） | 142/142 成功 |
| Lint | 初回調査では 1 error | 最終 main 取込後 0 errors / 240 warnings / 3 hints。検査抑制なし |
| ビルド | 旧候補とは別に実施 | 最終 UI 修正後も JDK 17 で debug、androidTest、R8・resource shrink を含む未署名 release APK、release AAB 成功。AAB bundletool validate 成功 |
| 財務エラー回帰 | 6 件中 4 件の失敗を変更前に再現 | API 26/35/実機 API 33 の各 6 件成功。最終全件にも含む |
| 全 instrumentation | 最初の API 35: 89 件中 16 失敗、API 26: 17 失敗。うち既存 15 件は変更前 main APK でも同じ失敗 | 修正後 API 35: 89/89 成功。API 26 通常 viewport: 89 件中1失敗（ソート画面前の初期カード順待機）。別途480×800/密度240 の対象ソートテストは1/1成功。API 26 の全件失敗を成功扱いにはしない |
| 実機（既存の分離試験） | 実データを保持して対象を限定 | SCG01 API 33 の隔離 32 件成功。通常データ 5 ファイルのハッシュがテスト前後・元 APK 復元後とも一致し、スリープ抑止設定も復元 |
| 画面画像 | main `cf22ffb1` と同じ API・構成・合成データで比較 | 修正後 API 35: 33/33 完全一致。API 26: Watchlist の全5枚を含む30/33 完全一致。別画面3枚に各1–2画素、最大色差1/255。完全一致として数えない |
| debug 更新 | 旧候補 `e17fea41` を検証用データで実行 | API 26/35 で上書きインストール・起動・資産画面を確認。保有／監視／無配フラグ／4 種 DVM／表示設定を維持し、3 項目の移行 JSON への保存も確認 |
| プロセス・書込中断 | 通常の `am kill` では終了せず未成立 | 専用 AVD で SIGKILL による再生成、および AtomicFile 置換中の終了を API 26/35 で実施。端末 preferences を除去して JSON から復元しても件数・ハッシュ・3 項目が一致 |
| 容量不足 | エラー注入とは別に実施 | API 26/35 のアプリ cache に限定した 1 MiB tmpfs で実際の ENOSPC を再現。失敗後も以前の完全な snapshot が残る。試験用 mount は解除済み |
| OS 容量超過 | 初回は未起動の package のため backup 対象外 | API 26 の LocalTransport で 29,361,069-byte の合成 snapshot が `Size quota exceeded`。端末内の元ファイルは不変。全体の Success と package の結果を区別 |
| OS full backup/restore | 以前の OS 復元試験は 3 項目追加前の候補 | 最終 AAB 版の API 26 で LocalTransport へ保存→専用アプリデータ消去→同アプリだけ復元。起動前の JSON が完全一致し、暗号化 preferences は除外。起動後に保有・監視・4 種 DVM・設定・3 項目が一致し、端末用 preferences の再作成を確認。Google クラウドではない |
| 通知 | 恒久障害と一時障害の retry を JVM で検査 | API 26/35 の実際の IDLE 移行中に同じ OS job 2 件を保持し、再起動後も WorkManager の同じ UUID 2 件と opt-in を保持。API 35 は OS 権限の拒否／許可の各 1 件も成功。配送時刻の保証とは扱わない |
| TalkBack | 過去の盲目的な操作では対象値の変更を確認できなかった | API 35 で設定画面から実サービスを有効化し、接続と対象の focus・setProgress・増減・上下限を検証。タッチスワイプの追加試験 2 回は値の変更を待つ箇所で各 15 秒の timeout。スワイプの成功・音声評価は確認できていない |

既存テストの修正は製品コードを期待値へ合わせるものではない。空の新規インストールを前提に、必要な 7203/2914/AAPL と監視データをテスト内で明示保存する。取得拒否時は現行 API の戻り値 false と保存データ不変を確認し、0.05 刻みの操作、現行の暦年配当 JSON、削除済みの検索候補に依存しない入力、API 33 以降だけの通知権限を使う。TSE の分類済み 33 業種と、銘柄名マスターから補った未分類の扱いを別々に確認する。assertion の削除、失敗の除外、`continue-on-error` の追加はない。既存の API 29 未満の画像保存テストの assumption はそのまま残す。

修正前の CI `08cbf5aa` / `68124e27` は API 26 の並べ替えテストが失敗した。ユーザー承認後、ソートシート Column だけへ縦スクロールを加え、既定の画面寸法・期待順・10秒 timeout は維持した。対象テストは専用 API 26 AVD の480×800/密度240で成功し、銘柄コード昇順の選択・適用・タブ再表示・ViewModel 再生成後の復元を通した。API 35 全件は89/89成功した。一方、修正後の API 26 全件は89件中1件が同じテストで失敗し、行164の初期カード順待機で timeout した。これはソートシートを開く前の失敗であり、小画面の対象テスト成功とは分けて扱う。さらに、画面寸法を変えた同クラス全件では検索再試行・設定スクロールの別テストも失敗したため、該当する UI やテスト順序を新たな証拠なしに推測修正しない。テストを除外したり、CI を大きな画面へ変更したりして成功扱いにはしていない。リモート CI は今回の commit を含む PR Checks を正本とする。

画像は 9 画面（資産、年間配当、ロードマップ、監視、設定、銘柄詳細、CSV、問い合わせ、利用規約）の light/dark/OLED と、資産・設定・監視の 1.3/2.0 倍文字の初期 viewport、合計 33 枚。全スクロール位置、全モーダル、全状態、全 OS・メーカーの網羅ではない。

最終比較での API 26 の画像差は `assets_dark_1.0`（1 画素）、`assets_oled_1.0`（1 画素）、`feedback_light_1.0`（2 画素）で、すべて最大色成分差 1。Watchlist の5画像はすべて一致。ラスタライズによる微小差の可能性はあるが、原因を断定したり許容閾値で完全一致へ置き換えたりしていない。初回比較と最終比較はローカル `final-visual-comparisons.json`、`visual-scroll-fix-26-comparison.json`、`visual-scroll-fix-35-comparison.json` に残した。

実機の 32 件は、元の回帰 5 件、バックアップ 17 件、財務失敗 6 件、起動時復元 1 件、300 銘柄の表示・再表示 3 件。各テストの Application/preferences/files を隔離し、実ユーザーの画面設定・通知操作を伴う全スイートは実機で実行していない。

単回の起動観測（Wi-Fi/mobile data を一時無効化して復元、仮想化ホストと software GPU）:

| OS | 保有件数 | am start TotalTime (ms) | 起動後 PSS (KiB) | janky/全 frames |
|---|---:|---:|---:|---:|
| API 26 | 0 | 2475 | 61324 | 28/38 |
| API 26 | 300 | 2184 | 77856 | 30/36 |
| API 26 | 2000 | 2356 | 76290 | 37/37 |
| API 35 | 0 | 2196 | 149710 | 8/27 |
| API 35 | 300 | 2406 | 152343 | 13/33 |
| API 35 | 2000 | 2666 | 177373 | 14/31 |

候補は全件数で起動後の保存件数も一致。API 35 の変更前も 0/300/2000 件で起動した。API 26 の変更前は 0/300 件で起動したが、2000 件の準備で既知の bulk Keystore 出力欠落による `AEADBadTagException` となり、速度比較は成立しなかった。候補だけの独立した 2000 件確認は成功。各 1 回、ホスト負荷あり、遅延フレームも多いため、速度改善や実機の滑らかさの保証には使わない。実機での frame trace・許容値判定は未実施。

## AAB・機密情報・依存関係・CI

- bundletool 1.18.3 で最終 UI 修正後の release AAB を validate し、manifest minSdk 26 / targetSdk 36 と `PAGE_ALIGNMENT_16K` を確認。ローカル debug 証明書の APK set を API 26/35 に更新インストールし、両方で release MainActivity 起動・非 debuggable・資産画面表示を確認。現 AAB の全画面操作・保存事実維持の再確認は未実施。前回候補では主要画面 smoke と保存事実を確認済み。本番署名情報は使用していない。Play から配信された split やストア審査の検証ではない。
- 最終 AAB の manifest は minSdk 26 / targetSdk 36、debuggable 属性なし（Android の既定 false）、`usesCleartextTraffic=false`。FileProvider は非公開で roadmap cache の一時 URI 読取権限を使用。公開された WorkManager/ProfileInstaller コンポーネントには system 権限が付き、BootReceiver は BOOT_COMPLETED を検査する。診断 runner はテスト APK のみで、release に instrumentation はない。
- 最終 AAB の設定は `PAGE_ALIGNMENT_16K`。前回候補に同梱された4 ABI の `libandroidx.graphics.path.so` は全 LOAD segment が16,384-byte alignmentで、生成 native split は `zipalign -c -P 16` 成功。今回の変更は Kotlin UI のみで native library は変わらない。実際の16 KiB OS での実行は未検証。[公式の検査方法](https://developer.android.com/guide/practices/page-sizes)
- Gitleaks 8.30.1 の redacted 走査では、現行 `kurogane-dividend/`・`.github/`・AGENTS の追跡済み 201 ファイル（約 5.44 MB）と、Android 関連の履歴 263 commit にルール一致はなかった。ただし既知の旧 DVM 固定鍵も検出できない。配列や独自形式、未追跡ファイル、他 branch、全履歴、実行環境、サーバー側を網羅した証明ではない。値を転載せず、鍵の種類と場所のみ記録した。
- 解決済み release runtime Maven artifact 87 件を OSV API と 2026-10-18 00:42 UTC に照合し、登録された該当項目は返らなかった。OSV にない欠陥、利用方法に起因する欠陥、build/test 専用依存や OS 自体はこの結果で保証しない。[OSV API](https://google.github.io/osv.dev/api/)。依存の更新はしていない。
- [Android release checks](../.github/workflows/android-release-checks.yml) を追加。JDK 17、JVM、Lint、未署名 release APK/AAB、API 26/35 の全 instrumentation を実行する。action は commit に固定し、repository permission は read のみ。APK・署名情報・raw Lint report は upload せず、source snippet と例外文を除いた件数・場所の JSON だけを artifact にする。初回は `sdkmanager` が PATH になく Gradle 実行前に失敗し、CLI tools 16.0 を明示して修正。[08cbf5aa の build job](https://github.com/masacgt/Diview/actions/runs/39084186727/job/117547200286) は成功した。更新ごとのリモート CI 結果は PR の Checks と PR 本文を正本とする。

最終製品成果物（本番配布用ではない）:

| 成果物 | SHA-256 |
|---|---|
| debug APK | `9466045edc8de0698c19b080383d1f7ec335d133919d5d3a3332c74139fdc84b` |
| 未署名 release APK | `db433528f8f1c2f3a378839b1ab710b0654d301fc840fdcca225573cd514967c` |
| release AAB | `3ca08f793076918612b7c0706fc5936b67d0bf374926ba6107a060a927f3ae99` |

## 最大 DVM と復元の残る制約

API 26、heap growth limit 512 MiB。52,428,767 bytes の JSON payload を複数の DVM 種類へ順に保存した試験は 16.543 秒で `OutOfMemoryError`。216,399,880 bytes の確保に失敗し、stack は `StringBuilder` → Android `JsonReader.nextString/skipValue` → `AutoBackupSnapshot.readBackup` → `LocalImportStore.writeAutoBackupSnapshot/saveBackup`。STOCKS の保存までをログで確認し、その後の種類の処理で失敗した。前追補のストリーミング化だけでは API 26 の長い文字列の skip 時の一時確保を防げない。暗号化 preferences に複数の大きな Base64 文字列を保持する方式もメモリを消費する。

未実装の設計案は、(1) 端末用 DVM 暗号文を個別の AtomicFile に保存し、旧 preferences の移行を検証後に完了させる、(2) 移行 JSON の旧形式を読み続けながら、Base64 の読出し・読み飛ばしを上限付きメモリで処理できる形式／実装を設計する、(3) 一時ディスク容量、キャンセル、中断復旧、保存完了の順序を含めて移行テストを行う、の組合せ。ファイルへ移すだけで解消すると断定しない。

これとは独立して、OS のクラウド Auto Backup には 25 MB/アプリの上限がある。大容量を受け付ける現仕様と、すべてをクラウドで復元できることは両立が未確認。容量削減、手動転送、バックアップ対象や容量表示の方針は別途判断が必要。[Auto Backup](https://developer.android.com/identity/data/autobackup)

固定鍵を環境変数や設定へ移してもクライアントで秘匿できない。旧形式の鍵と過去のファイルは遠隔失効できない。本調査でサーバー用認証情報への転用は確認していないが、転用されていれば別途失効・再発行の判断が必要。本作業では認証情報への外部操作を行っていない。

## 再実行と未完了の判断

生ログ、画像、検証用 APK は `C:\Users\htdn\.codex\tmp\diview-release-acceptance-20261018` に保存し、Git に含めない。ローカル検証ツール [android_release_acceptance.py](../kurogane-dividend/scripts/android_release_acceptance.py) は JDK 17 を確認し、今回作った AVD 名と serial の両方が完全一致するときだけ実行する。元の AVD、似た名前、JDK 21 を root 前に拒否するホスト側テスト 4 件も追加した。Windows の bundletool には実在する `adb.exe` のパスを渡す。clear/root/reboot/低容量試験を既存の実機や元の AVD に適用しない。

```powershell
$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:ANDROID_HOME = 'C:\Android\sdk'
$env:Path = "$env:JAVA_HOME\bin;C:\Android\sdk\platform-tools;$env:Path"
Set-Location 'C:\dv-ra-1017\kurogane-dividend\android'
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease
# 今回作った、検証用データだけの専用 AVD に限定する。
adb -s emulator-5558 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5558 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5558 shell am instrument -w -r com.kurogane.dividend.test/com.kurogane.dividend.ReleaseFixtureInstrumentation
```

未完了の判断は、API 26 全件テストの初期カード待機失敗（V1）、最大 DVM の保存方式、固定鍵と移行データの保護、小さな同期保存の完了保証、実際の別端末クラウド復元、Play 配信、現 AAB の全画面操作と保存事実の再検証、TalkBack のスワイプ・音声と端末での人による操作、API 26 の画像保存（既存テストの assumption 対象）、長期の通知配送・省電力、全画面・全状態・全機種の網羅。これらを解決済み、またはリリース許可済みとは記載しない。本番デプロイ・公開・署名済み APK の配布は行っていない。すべての不具合を検出したという意味でもない。
