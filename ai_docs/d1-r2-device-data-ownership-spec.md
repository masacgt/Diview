# Diview D1・R2・端末データ所有権と移行仕様

調査日: 2026-11-06
状態: 移行方針の仕様。ウォッチリストのWorker切替は本番反映済み（2026-11-03、Version ID `4981c1e0-e337-4392-9f5b-909b52c5bc0c`）。その他のR2移行は未実施。

## 目的

読み取り中心の市場データ、分析結果、履歴、完了済み処理成果物をR2へ集約し、D1の行読み取り量を抑える。ユーザー個人のウォッチリストと価格通知状態はAndroid端末で管理する。D1は、関係検索、整合性、更新状態を必要とする小規模データに残してよい。D1全廃は目標にしない。

本書は、ここまでのコード調査と利用者が決定した保存先を、現在の実装と目標仕様に分けて記録する。既存の市場データ仕様は [database-api-data-inventory.md](database-api-data-inventory.md) と [r2-yfinance-target-spec.md](r2-yfinance-target-spec.md) を参照する。

## 実装状態（2026-11-05）

- 2026-11-04 15:28 UTC時点の読み取り専用dry-runでは、analysis-history 37,972行、analysis-current 248行、definition-audit 2行を全件処理し、analysis-sourceは0行、scheduled-runsは移行対象0件だった。definitionsは6定義から12 version/current objectsを計画した。市場監査はsecurities 3,960行、financial_periods 49,592行まで完了し、dividend_eventsとissuer_dividend_periodsは0行。全phaseでR2書き込み0。
- 同日15:25:11 UTCのAccount Analyticsは全DB合計216,290 rows read・残り4,783,710行を返した。その後のD1 phaseは合計250 rows_readを報告したが、Analytics集計は未反映のため、次のD1 SQL前に再取得し100,000行の予備枠を維持する。
- 市場差異の集計（R2カタログ外65コード、名称差異552件、financial_periodsの期間欠落17件・値差異4件）は個別分類・正規値のレビューが未完了であり、集計だけを根拠にR2を書き換えない。古いrun/Queue記録はQueue backlog・保持要件の確認までD1に残す。
- 本番R2 write/readback、Worker/API parity、GitHub Actions公開・実行、Worker deploy、物理端末install、commit/pushは未実施。GitHub APIの最新確認では`main`のbranch metadataが`protected=false`、repository rulesetsは空だった。詳細なbranch-protection endpointは403で、Environment設定も未確認のため、切替条件は満たしていない。Queue backlogは`CLOUDFLARE_QUEUE_READ_TOKEN`未設定で未確認。
- 2026-11-04 03:35 UTCのWrangler読み取りで、本番Workerの最新Versionは引き続き `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%配信中だった。したがって、今回のR2/DO切替・新Workflowは本番反映されていない。同時刻のGitHub `main` はHEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b` で、Worker CIと手動D1→R2 Workflowは未公開。Queue metadataはproducer/consumer登録のみを返し、backlog件数は未確認。
- 2026-11-04 03:35 UTCの `wrangler d1 info diview-irbank --json` はCloudflare API認証エラー10000で失敗した。行データクエリは実行していない。日次Rows Readと残り枠はDashboardで再確認できておらず、D1監査再開の条件を満たしていない。
- このworktreeでは、Workerの市場データ・分析・定義のR2参照、分析結果と完了状態のR2保存、Durable Objectsの共有レート制限、手動D1→R2移行Workflow、Worker CI、Androidの日本株オフライン価格通知キャッシュを実装している。分析の現在値・履歴・実行ポインター・実行状態と定義監査にR2条件付き書き込みを使い、入力世代が処理中に変わった場合は処理済みポインターを進めない。D1の財務・配当期間とIRBANK R2原本を比較する読み取り専用の `market-source-audit` も追加した。
- `import-d1.ps1` と `apply-financial-repair.ps1` は、引数や確認値にかかわらず冒頭で停止する。旧定義seedは `data/irbank/organized/d1/seed_app_definitions.sql.disabled` に移し、SQLとして実行されない形で保管した。ローカル銘柄CSVとオフライン生成・修復計画は保持する。移行Workflowは手動実行専用で、まだ起動していない。
- 本番に反映済みなのは旧サーバー通知APIを端末管理扱いにしたWorker変更（Version ID `4981c1e0-e337-4392-9f5b-909b52c5bc0c`）まで。今回のR2参照・分析状態・Durable Objectsレート制限、Android価格キャッシュ、2つの新Workflowはローカル差分で、本番には反映していない。移行Workflowも起動していない。2026-11-03 15:28 UTCの `wrangler d1 info` は `rows_read_24h=6,934,104`、`read_queries_24h=3,294`、DBサイズ100,163,584 bytesを返した。日次上限5,000,000行を超えており、メール記載のリセット時刻2026-11-04 00:00 UTCより前だったため、この時点ではD1のSELECTを実行していない。
- 同時刻の `wrangler d1 insights`（過去1日）では、銘柄検索の `SELECT security_code, security_code_display, name, metrics_json FROM securities WHERE security_code = ? OR security_code_display = ? LIMIT ?` が1,786回、平均3,923行、集計7,007,929行として最大の読取クエリに出た。カタログ一覧の `SELECT ... FROM securities ORDER BY security_code` も18回、集計71,280行だった。InsightsはGraphQL Analytics由来のクエリランキングであり、D1 infoの24時間使用量と集計値は一致していないため、両者を同じ精度・時間窓の数値として扱わない。CloudflareはD1 Insightsの基礎データがサンプルされることを説明している（[D1の高コストクエリ調査](https://developers.cloudflare.com/d1/observability/find-expensive-queries/)）。
- `wrangler deployments list` ではWorker Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が2026-11-03 12:26:30 UTCから100%配信中と報告された。同時刻からの直近3時間Insightsには銘柄検索SELECTは現れなかった一方、`api_request_log` のレート制限upsert、`scheduled_run_log` の実行ログ、`app_definition_audit` のD1書込SQLが記録された。ランキングにないことだけでは市場データD1アクセスの完全な不在を証明しないが、レート制限や定期処理ログのD1経路は本番で残っている証拠である。
- D1 infoは23テーブルと報告し、2026-11-04 00:02 UTC以降の読み取り専用sqlite_schema照会でも本番に23テーブルと2つのsupport-feedback email triggerがあることを確認した。基底schemaの22アプリテーブルとの対応を含むテーブル名一覧は、保存済み監査記録とmigration一覧で最終照合する。schema照会の `meta.rows_read` は90行と92行だった。
- 2026-11-04 JSTの先行R2読み戻しでは、市場カタログは世代 `2026-11-02-15aa0f2576d649b2aa0da838f9c68544`・4,368銘柄、空白月候補は同世代・3,201件だった。7203・8306・1301・2708・2914のプロフィールと現在値オブジェクトも存在し、価格日は2026-11-02だった。この値は後刻の読み戻しで更新されており、現行値として扱わない。後段の2026-11-04 11:22 UTCのローカルWorker/remote R2確認では、現行generation `2026-11-02-daa434e899b0493a9fdf346067419041`、カタログ4,390件、候補3,203件を確認した。
- 2026-11-04の本番読み取り専用監査では、D1 `securities` の監査チェックポイントはcursor 3,960で完了し、R2書き込みは0件だった。集計ではR2カタログにないD1コード65件、銘柄名差異552件、EDINET差異0件、issuer index欠落0件だった。`financial_periods` は49,592行を全件比較し、49,571一致、R2原本側の期間欠落17件、値の差異4件だった。比較で読んだD1行数は99,184。`dividend_events` と `issuer_dividend_periods` はそれぞれ空で、照会ごとの `meta.rows_read` は1だった。これらの監査はdry-runで行い、R2書き込みは0件。差異を正規データと照合する作業は未完了であり、集計報告だけで補完対象を決めない。
- `analysis-current` のdry-runは248行を読み、248オブジェクトを計画して完了した。R2書き込みは0件。`analysis-history` はチェックポイントcursor 1,500まで計画・読取を進めたが未完了である。最後に保存されたレポートは500行のページ完了を示す一方、その後のWrangler呼び出しは終了コード1・空のstderrとなり、失敗した呼び出しの読取結果は確定できない。自動再試行はしておらず、本番D1の追加読取は停止している。
- リセット後の `wrangler d1 info` は00:02 UTCに24時間値0を示したが、00:55 UTCには `rows_read_24h=7,004,824` を返した。この項目は24時間移動値であり、UTC日次上限の使用量とは同一視しない。日次使用量を確定できるDashboard情報は得られておらず、個別クエリの `meta.rows_read` を記録した。最終の失敗呼び出し結果が不明なため、Dashboardで利用可能枠を確認するまで追加の本番D1行読取を行わない。
- 下記の「移行前の本番処理と移行対象」は作業開始時の調査内容を記録する。変更後のローカルコードや過去の本番デプロイ記録を、現在の本番状態の証拠として扱わない。
- 旧作業ツリーではWorker 70件、移行ツール26件、JDK 17のAndroidユニットテストが成功した記録がある。最新の分離worktreeでの検証は以下の追記を参照。移行ツールはD1クエリPOSTを自動再試行せず、読み取り数メタデータ欠落・予算超過・不明な失敗を報告して停止する。WorkerテストではR2同時更新時に新しい値を保持すること、入力世代の途中変更時にポインターを進めないこと、実行ポインターを巻き戻さないことを確認した。移行ツールでは、再開前後でR2原本オブジェクトが変わった場合に監査を停止すること、銘柄監査がrowidページングで同一表示コードや無効コードを取りこぼさないことを確認した。通知関連instrumentation 15件は成功したが、全103件では102件成功・1件失敗した。失敗した `ReleaseReadinessTest.watchlistSortOrderSurvivesLeavingAndReturningToTab` は単独実行でも30秒のCompose待機がタイムアウトした。JDK 17でエミュレータに入れたAPKとビルドAPKのSHA-256は一致した。GitHub Actions実行、本番D1使用量比較は未完了である。

- 2026-11-03 18:50 UTCの継続確認では、分離worktreeのWorkerテスト110/110件、移行ツール27/27件、`node --check`、`git diff --check` が成功した。AndroidはJDK 17を明示して `testDebugUnitTest assembleDebug --rerun-tasks` を完了し、最新XMLは266件・失敗0・エラー0、Debug APK SHA-256は `1D613146B23CE0A2581D4A1428564ED00DA3E0E5B9E2854D4BA6349B9400E72A`。実機・UI確認は行っていない。
- 同日の読み取り専用Worker API再確認では検索カタログ4,368件、空白月候補3,201件がともに200で、generationは `2026-11-02-15aa0f2576d649b2aa0da838f9c68544`。過去の候補API 1101は再現しなかった。Cloudflare Queue metrics APIでは通常キュー・DLQのbacklogがともに0だったが、切替直前の再確認と外部producerの利用確認は残る。
- 2026-11-03 18:50 UTC時点ではメール記載のD1リセット時刻2026-11-04 00:00 UTC前のため、本番D1行データは読んでいない。リセット後に使用量と読み取り可否を再確認してから、小予算の監査を始める。
- 2026-11-03 19:09 UTCの継続検証では、Workerテスト111/111件、移行ツール27/27件、`node --check src/index.js`、`git diff --check`、JDK 17のAndroidユニットテスト266/266件、`assembleDebug` が成功した。APK SHA-256は `1a987cb048bc635f7567d90a037d11b1a8827720dab137abe742ca580b84b469`。エミュレータのADB transportは応答するが、`adb shell` が応答せず、今回ビルドしたAPKのインストール・端末SHA照合・最新UI確認は未完了。過去に別のAPKで成功した通知instrumentation 15件の結果を、今回のAPKの検証と混同しない。
- GitHubのremote `main`には `.github/workflows/cloudflare-worker-ci.yml` と `.github/workflows/d1-r2-migration.yml` がなく、作業worktreeでは未追跡ファイルであるため、GitHub Actions画面にはまだ表示されない。GitHub Environmentsの読み取り確認でも `d1-r2-migration` は未作成で、存在したのは `IRBANK_API_KEY` のみ。公開後も専用Environmentの作成・main限定・独立レビュー・必要なEnvironment secrets登録が済むまでは移行Workflowのpreflightは通らない。
- 同日19:09 UTC時点でも、メール記載のD1リセット時刻2026-11-04 00:00 UTC前だった。直近の行数メタデータは18:53 UTCの `rows_read_24h=6,921,235`、`read_queries_24h=3,085`、`write_queries_24h=6,179`、`rows_written_24h=18,586`。本番D1の行SELECT・移行・R2本番書き込み・Workerデプロイ・Workflow起動は行っていない。

- 2026-11-03 19:22 UTCのD1使用量メタデータは `rows_read_24h=6,916,954`、`read_queries_24h=3,016`、`write_queries_24h=6,052`、`rows_written_24h=18,205` で、5,000,000行上限を超えていた。19:18 UTCごろの `d1 info` が0を返した後に数値が戻り、10行限定の `SELECT security_code FROM securities ORDER BY rowid LIMIT 10` は `meta.rows_read=10` で成功したが、メールの制限通知と使用量メタデータが矛盾している。これはリセットや継続読取の安全性を証明しないため、2026-11-04 00:00 UTC以降に使用量を再確認するまで本番D1の行読取を停止する。このプローブは全表監査ではない。
- 2026-11-03 19:24 UTCのR2読み戻しでは、市場カタログがgeneration `2026-11-02-15aa0f2576d649b2aa0da838f9c68544`・4,368件・重複/形式不正0件、インカム空白月候補が同じgeneration・3,201件・重複/空コード/カタログ外0件だった。両オブジェクトのgenerationとSHA-256を記録し、本番API `/api/v1/search-catalog`（200・4,368件）と `/api/v1/income-advice-candidates`（200・3,201件）もR2と一致した。分析current/history/state、完了run、定義current、定義監査の各R2 prefixはすべて0件で、D1からの移行とR2書込/読み戻しは未完了。
- 2026-11-03 19:24 UTCまでのローカル検証ではWorker 111/111件、移行ツール27/27件、`node --check src/index.js` が成功した。JDK 17の `testDebugUnitTest` と `assembleDebugAndroidTest` は成功し、最新Debug APK `1a987cb048bc635f7567d90a037d11b1a8827720dab137abe742ca580b84b469` を専用エミュレータ `emulator-5556` にインストール、端末 `base.apk` のSHA-256一致を確認し、`NotificationDeliveryTest` 13/13件が通った。物理端末は変更していない。
- GitHubの `masacgt/Diview` `main`を読み取り確認し、`.github/workflows/cloudflare-worker-ci.yml` と `.github/workflows/d1-r2-migration.yml` は404で未公開だった。公開リポジトリのEnvironment一覧は `IRBANK_API_KEY` のみで、`d1-r2-migration` は未作成。Actions実行、本番D1対R2監査、R2移行書込、Workerデプロイは未実施。
- 2026-11-03 19:49 UTCのローカル継続検証では、Workerテスト113/113件、D1→R2移行ツール30/30件、`node --check src/index.js`、`git diff --check` が成功した。ヘルス確認は移行済み実行ポインターの読取と新しい通常R2実行の優先を確認し、D1呼出しは0件。移行回帰テストはdry-run完了チェックポイントから書込みに切り替えると先頭から再開し、定期実行ポインターはID順ではなく正規化した開始時刻、同時刻なら数値ID順で選ぶこと、および移行全体が完了する前にポインターを公開しないこと、新しいR2実行より後退しないことを確認する。最新Android差分はJDK 17の単体テスト266件とビルドが成功し、emulator-5556上でAPK/`base.apk` SHA-256 `959eef683bd3afc55c36387f7ef978772012d81c9e8e08d0a01806793d2ec733` が一致、`ReleaseNotificationPlatformTest` 2/2件と `NotificationDeliveryTest` 13/13件が成功した。物理端末は変更していない。19:49 UTCはD1リセットの2026-11-04 00:00 UTC前なので、この継続中に本番D1行データは読んでいない。GitHub Actionsファイルはローカル未追跡で未公開、移行Environmentの前回確認は未作成のまま。本番D1監査・データ移行・R2書込み・Workflow実行・デプロイは未実施。

### 2026-11-04 UTCリセット後の追加検証

- 分離実装worktreeではWorker 114/114件、移行ツール37/37件、JDK 17 Android JVM 267/267件、emulator-5556 instrumentation 35/35件が成功したとの直近検証記録がある。Android Debug APKとエミュレータの `base.apk` はSHA-256 `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` で一致。今回の文書更新後にコードは変更していない。GitHub上のWorkflow実行、移行Environment設定、本番切替は未確認・未実施。
- 完全な `market-audit` レポートは累積3,960行・cursor 3,960・completeで、最後の再開レポートが追加読取3,860行を記録する。R2カタログ4,390件、generation `2026-11-02-daa434e899b0493a9fdf346067419041`。report SHA-256は `7cbf7be38c1efa8b51e71cac6b9aef412830fd5e2bcc0e4e00d08b39d612fe11`。
- `financial_periods` の完了レポートは49,592件、累積D1読取99,184行、R2原本照合49,571一致・17期間欠落・4値差異、R2書き込み0件。report SHA-256は `f46bad2d1ceaecc6606a2973e68e8bc901d06222ea36c3bafcd3a8cdc29aec13`。17期間と4行の差異を示す銘柄・期間・値は集計レポートに保存していない。D1利用可能枠を確認した後に限り、公開レポートへ機密値を出さない個別調査が必要。
- 監査レポートとチェックポイントは `C:\Codex\r2-d1-ownership-audit-20261104\` に保存した。いずれも読み取り専用またはdry-runで、R2へのデータ書き込みはない。
- 2026-11-04のGitHub REST API確認では、`main`のWorkflow一覧に既存の9ファイルがあり、`.github/workflows/cloudflare-worker-ci.yml` と `.github/workflows/d1-r2-migration.yml` は404だった。従って新Workflowはローカル差分にあり、GitHub Actions画面には表示されず、GitHub上での実行もない。`d1-r2-migration` Environmentは同日の前回読み取りで未作成と確認したが、この再確認ではEnvironment設定を読めていない。資格情報の読み取り・設定変更は行っていない。

## 目的達成後の状態

- 検索・分析・定期分析・履歴・定義取得の通常経路から、市場データ用途のD1読み書きをなくす。銘柄カタログや分析状態の正本はR2、共有レート制限はDurable Objectsに置く。
- ユーザー単位のウォッチリスト、通知設定、通知済み状態、通知履歴、価格通知用キャッシュはAndroid端末を正本とし、価格通知はネットワークなしで実行できる。
- D1には問い合わせ・メール再試行状態など、関係性と小さな可変状態を必要とするデータだけを残す。D1を全廃することや、要件を満たさないまま残存テーブルを削除することはゴールにしない。
- GitHub Actionsの定期R2公開、Worker Cron、Androidの更新経路が新しい保存先に一致する。D1読み取り量を移行前後で比較し、残るD1アクセスを説明できる。

## 確定した方針

- 分析結果・履歴はR2で管理し、D1には保存しない。
- 完了した定期処理のログ・成果物はR2で管理し、D1には保存しない。
- ウォッチリスト、通知設定、目標値、通知済み状態、通知履歴は端末で管理する。
- 価格通知の対象は日本株とする。アプリ起動時に更新を判定し、通常取得は日本時間で同日に重ねず、前回の試行から12時間以上経過している場合に行う。取得結果を端末に保存し、指定時刻の価格通知は保存済みデータだけで判定する。アプリが起動されない日は新しい価格を取得できないため、通知時の鮮度判定で扱う。
- 通常の価格取得は保有・ウォッチリスト対象の日本株に限定し、価格と価格日を端末へ保存する。IRBANK利回り契約の更新が必要な場合のみ、通常のJST日付・12時間ゲート内で全カタログを取得する。通知処理は端末キャッシュを使い、ネットワークへ接続しない。
- 保存価格が有効な価格日から東証の3営業日を超えて古い場合、または価格がない場合は価格通知を見送る。更新失敗時もこの条件で判定し、古い値を新しい値として扱わない。
- 分析APIの共有レート制限はDurable Objectsで厳密に維持する。
- インカム空白月候補は既存のR2カタログ・API・端末内順位付けの流れを維持する。D1への移行対象にはしない。
- D1が得意な小規模な関係データや処理中の制御状態は、用途に応じてD1に残してよい。

## 2026-11-04の追加再検証

- Wrangler 4.136.1で本番R2を読み取り、yfinanceカタログとインカム空白月候補の現行値を確認した。両方のgenerationは `2026-11-02-daa434e899b0493a9fdf346067419041`、件数は4,390/3,203、コード重複は0。候補3,203件すべての `source_catalog_generation` が一致し、候補コードのカタログ欠落も0。オブジェクトSHA-256はカタログ `5830777b686024bc313eed59916fe4db4cbd26b834eff9486f2c8c921e367a1e`、候補 `964abd8458e6ac2757274b36db6ce691f1ddee86b9931f0f423397a5ccc8d1ab`。
- R2の7203プロフィールはYahoo Finance via yfinance由来で、現在値の価格は¥3,051、JPY、価格日は2026-11-02、配当状態は `has_dividend`。プロフィール/現在値オブジェクトは存在した。Worker APIの現行応答との突合は未実施。
- ローカルWorkerの公開GET処理を再点検し、R2を読む経路が `d1IndependentRead` によりDurable Objectレート制限を迂回する欠陥を発見した。除外を削除し、公開GETにルート別共有DO制限を適用する回帰テストを追加した。12ルートの上限300/60秒、429、Retry-After、D1未呼出しを確認し、Worker 115/115件成功。本番デプロイは未実施。
- JDK 17.0.20.1でAndroid JVMテスト267/267件とDebug/AndroidTest APK buildが成功し、emulator-5556でinstrumentation 136/136件成功。生成APKと同エミュレータの `base.apk` はSHA-256 `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` で一致。物理端末にはインストールしていない。
- Cloudflare Dashboardの日次D1使用量・残枠は未確認で、本番D1への追加行読み取りは停止中。確認先はD1データベースのMetrics画面またはBilling > Billable Usage ([D1 metrics](https://developers.cloudflare.com/d1/observability/metrics-analytics/)、[D1 billing](https://developers.cloudflare.com/d1/observability/billing/))。`rows_read_24h` は24時間移動値なので、日次上限の残枠とは同一視しない。

## 保存先の目標

| データ | 正本・保存先 | 要件 |
| --- | --- | --- |
| 銘柄カタログ、プロフィール、株価、財務・配当データ | R2 | コードや期間で既知のオブジェクトを取得する。カタログを銘柄検索・一括処理の対象一覧にも使い、D1全表走査を残さない。 |
| インカム空白月候補カタログ | R2 | 既存の `market/yfinance/v2/derived/income_advice/current.json` を正本とする。Worker APIはR2から提供し、Androidは取得結果をキャッシュしてウォッチリストの空白月候補を端末内で順位付けする。 |
| 分析APIの応答 | R2の市場データからオンデマンド計算 | 現在の `/api/v1/analysis/{code}` はリクエスト時に分析を計算する。この応答意味と鮮度を維持し、保存済みの定期スナップショットを返す方式へ暗黙に変更しない。 |
| 定期分析の最新状態・履歴 | R2 | 銘柄ごとの最新状態と履歴を保存する。変更のない同一スナップショットは履歴を増やさず、既存の保持期間を引き継ぐ。 |
| 完了した定期処理ログ・成果物 | R2 | 実行ID、処理種別、完了状態、件数、失敗概要、時間、成果物を保存する。D1には完了ログを残さない。 |
| 分析実行中の状態・実行ポインター | R2 | 実行IDごとの要約を `running` で作成し、完了・失敗時に同じオブジェクトを更新する。最新実行ポインターもR2に置く。将来、同時実行の排他が必要と確認された場合はDurable Objectsを別途検討する。 |
| ウォッチリスト、価格通知設定・判定状態、取得済み価格 | Android端末 | ウォッチリストと通知状態をサーバーに二重保存しない。日本株価格スナップショットには銘柄コードごとの価格・価格日・取得日時と、カタログの市場データ日・取得状態を含め、銘柄単位で3東証営業日の鮮度を判定できるようにする。 |
| 旧サーバー通知のクライアント認証情報 | 旧D1データ（保持中） | 関連APIはWorkerソースで410を返す。既存行は削除せず、バックアップ・保持要件の確認後に別途整理する。 |
| 問い合わせとメール再試行状態 | D1 | 受付番号の重複防止、問い合わせとoutboxの関連、リース・再試行状態を維持する。本文と送信状態の分割は、容量上の必要が確認された場合に限り検討する。 |
| APIレート制限 | Durable Objects | 現行D1カウンター相当の厳密な共有制限を維持する。ルート・クライアント/IP単位のキー、期間、上限、拒否応答を引き継ぐ。Workers Rate Limitingの近似制限には変更しない。 |
| 定義・設定・診断帯・評価シナリオ | R2 | バージョン付きJSONを正本にする。KVを配信キャッシュに使う場合も、正本を二重化しない。 |
| 定義監査履歴 | R2 | 定義キー・バージョン・ハッシュ・観測日時を追記型オブジェクトとして保存する。API呼び出しのたびにD1へ監査書き込みをしない。 |
| 分析元データの処理済み世代 | R2 | `analysis/v1/state/source.json` に入力世代を保持する。R2カタログと財務リビジョンを比較し、同一世代を再処理しない。 |

R2のキー案は次のとおり。実装時に既存の命名と衝突しないことを確認して確定する。

```text
market/yfinance/v2/catalog/current.json                 # 既存の公開銘柄カタログ
market/yfinance/v2/derived/income_advice/current.json   # 既存のインカム空白月候補
analysis/v1/current/{security_code}.json                # 銘柄ごとの最新定期スナップショット
analysis/v1/history/{security_code}/{yyyy}/{mm}/{id}.json
operations/v1/runs/{run_type}/{run_id}/summary.json     # 完了済み実行ログ
operations/v1/runs/{run_type}/{run_id}/artifacts/{key}
definitions/v1/{definition_key}/{version}.json          # バージョン付き定義
audit/v1/definitions/{definition_key}/{date}/{hash}.json
```

同一キーを複数処理が更新する場合、無条件の上書きに依存しない。条件付き更新、決定的な実行ID、またはDurable Objectsによる直列化を使い、再試行で履歴や通知を重複生成しない。

R2への複数オブジェクト公開は、世代ID付きの不変オブジェクトを書いた後に、小さな `current.json` ポインターを切り替える。途中失敗時に利用者が部分世代を読まないこと、再実行が同じ世代を重複作成しないことを実装時に保証する。

## 移行前の本番処理と移行対象（調査時点）

### 市場データと銘柄一覧

R2にはyfinance v2のカタログ、プロフィール、現在値、財務、配当のオブジェクトがある。IRBANKの詳細データもR2にある。一方、D1には `securities`、`financial_periods`、`dividend_events`、`issuer_dividend_periods` のスキーマと投入スクリプトが残る。

Workerの分析APIは銘柄の存在確認にD1の `securities` を検索し、R2プロフィールも取得する。定期分析では、入力世代が変わるとD1の銘柄一覧全件を取得する。価格更新処理にもD1の銘柄数・銘柄一覧を使う経路がある。移行時は単発検索だけでなく、全銘柄処理とキュー生成の一覧取得もR2カタログへ切り替える。

D1再投入につながる経路を棚卸しし、`import-d1.ps1` と `apply-financial-repair.ps1` は実行時に停止する。旧定義seedは `seed_app_definitions.sql.disabled` に移して通常のSQL対象から外した。オフライン修復計画はファイル生成のみで、D1適用スクリプトは停止している。R2データ作成で読む `organized/d1/securities.csv` はローカル入力でありCloudflare D1ではない。代替銘柄マスターが用意されるまでは保持する。2026-11-04時点でローカルCSVは3,963行、本番市場監査は3,960行だったが、時点が異なるため個別差異の分類には使わない。

既存D1の財務・配当テーブルが現行Workerのv2 APIで参照されているとは限らない。R2側との銘柄・期間・出典の充足率を確認し、欠損分だけを移行する。投入スクリプトがD1へ継続書き込みしていないことを確認してから、D1への重複投入を止める。

### 分析結果・履歴

`/api/v1/analysis/{code}` は現在、D1の銘柄行とR2の市場・財務データを組み合わせ、リクエストごとに分析を計算する。目標利回りが指定された場合は、その値を使った買い場判定を返す。移行後もこのオンデマンド応答を維持し、定期スナップショットを返すAPIへ勝手に置き換えない。

定期処理は `stock_analysis_state` から前回状態を読み、`stock_analysis_history` に変更時の履歴を保存する。履歴APIは銘柄ごとに最大90件を返す。R2移行では最新状態と履歴の両方を対象にし、定期処理からの書き込み、状態比較、履歴APIの読み取りを切り替える。

保持期間は現行規則を引き継ぐ。直近30日は履歴を保持し、それより古く365日以内は銘柄ごとに日次1件、365日より古い記録は月次1件を残す。R2の世代・パーティション設計で同じ検索結果と保持規則を再現する。

### 定期処理・運用状態

- Worker Cron は `17 * * * *` で毎時起動する。定期分析はR2カタログと財務リビジョンの世代を比較し、入力が変わった場合は銘柄一覧を再分析する。今回のWorkerソース変更後は、世代が変わらない実行では個別銘柄を処理せず、D1のサーバー側ウォッチリストを参照しない。個人別の目標値判定と通知はサーバー処理に依存させない。
- `scheduled_run_log` は開始中の状態と完了ログを兼ねる。移行時に完了ログ・成果物をR2へ移し、実行中状態はD1またはDurable Objectsに分離する。
- 旧価格更新キュー用のD1ヘルパーとバインディングは残っているが、現在のCron経路はキューを投入・消費していない。キューコンシューマーは受信メッセージをACKする実装であるため、廃止前に本番の未処理メッセージと外部投入元を確認する。`market_price_refresh_runs` は `/api/v1/health` が引き続き参照するため、現行のD1読み取りとして扱う。`market_price_refresh_batches` などの旧キュー表は、Worker・Actions・運用スクリプトの書き込みがないことを本番状態と合わせて確認してから整理する。
- `/api/v1/health` は現在、D1から最新の定期分析・価格更新状態を読む。完了状況はR2、実行中状態は選定した制御ストアから読むようにする。
- `backend_state.analysis_source` は、R2カタログと財務リビジョンを前回処理した世代と比較するチェックポイントである。履歴ログと混ぜず、重複実行を防げる小さな状態として維持する。
- 現行の保持処理は、`api_request_log` を1日、`support_feedback` を90日、`scheduled_run_log` と対象の `notification_events` を365日で整理する。R2へ移すログ・履歴にも、該当する保持規則を維持する。

### GitHub Actionsとデプロイ経路

現行 `.github/workflows/` を確認した範囲では、GitHub Actionsから本番D1へ `wrangler d1 execute` を実行する処理はない。R2データ生成のActionsは移行後も必要であり、D1移行と混同して止めない。

| ワークフロー | 現在の役割 | 移行時の扱い |
| --- | --- | --- |
| `yfinance-r2.yml` | 平日21:17 JSTに市場カレンダーを確認してv2データをR2へ公開する。手動の候補再生成・財務更新・旧データ移行モードも持つ。 | 継続する。`migrate-financials` と `migrate-irbank-dividends` は既存R2名前空間からの移行であり、本番D1からの移行ではない。 |
| `irbank-v2-dividend-batch.yml` | 毎日02:17 JSTにIRBANK配当データを取得してR2へ公開する。 | 継続する。 |
| `rights-dates-weekly.yml` | 毎週月曜09:17 JSTに権利日・履歴ベースの予想を計算してR2へ公開する。 | 継続する。 |
| `android-release-checks.yml` | AndroidをJDK 17でビルドし、API 26・35の端末テストを行う。 | 継続し、価格キャッシュとオフライン通知の回帰確認を加える。 |
| Cloudflare Worker CI | remote `main`には未配置。作業worktreeには `.github/workflows/cloudflare-worker-ci.yml` があるが未追跡で、GitHub画面にはまだ出ない。 | 公開後、`data/irbank/cloudflare/**` の変更で起動し、Worker・移行ツール契約テストを実行する。CIから本番デプロイしない。 |
| D1→R2移行処理 | remote `main`には専用Actionsがない。作業worktreeには `.github/workflows/d1-r2-migration.yml` があるが未追跡で、GitHub画面にはまだ出ない。`d1-r2-migration` Environmentも未作成。 | Workflowは `workflow_dispatch` 専用。ドライラン、表/フェーズ単位、読み取り予算、再開チェックポイント、照合レポートを備える。公開後にmain限定・独立レビュー・専用Environment secretsを設定し、まず小予算のドライランをレビューする。 |

現行のWorker公開手順はREADME記載の `npx wrangler deploy` であり、専用のGitHub Actionsデプロイはない。移行データの読み戻し、CI、承認を通過した後に、既存の手動デプロイ手順でWorkerを切り替える。自動本番デプロイはこの移行の前提にしない。

GitHub ActionsのD1移行ジョブは、対象データだけを読み、D1読取専用のCloudflare API資格情報とR2書込資格情報を分けて使う。`workflow_dispatch` は既定ブランチ以外のrefからも起動できるため、shell内のref検査だけを秘密情報の保護境界にしない。ジョブはmain refだけに制限し、専用Environmentを参照する。Environmentはmainのみを許可し、独立したレビューを必須に設定し、資格情報はEnvironment secretsだけに登録する。再開用run IDは同一リポジトリのmain上で実行された移行workflowの完了runであることを確認する。これらのGitHub設定はリポジトリ管理画面で別途確認する。データ本体や秘密情報をActionsログ・公開アーティファクトに出さず、成果物には件数・ハッシュ・対象世代などの検証結果だけを保存する。D1の読み取り上限を超えている間は実行せず、リセット後も一度の実行で読み取り枠を使い切らないように、上限と進捗チェックポイントを設ける。

`import-d1.ps1` と `apply-financial-repair.ps1` は冒頭で停止し、旧D1定義seedは `.sql.disabled` 拡張子へ移して本番再投入経路から外した。問い合わせ等D1に残すスキーマ操作と、`dev-local.ps1` のローカル専用schema適用は別扱いにする。

### 定義・レート制限

分析・設定APIには市場データ以外のD1アクセスも残る。`appDefinitionDocument` と `appDefinitionItems` はKVに値がない場合D1へフォールバックする。`auditDefinition` は分析実行時にD1へ監査記録を書く。これらをR2定義・監査へ移し、D1フォールバックとリクエストごとのD1監査書き込みをなくす。監査は定義変更時のイベントとして追記し、通常の分析リクエストごとには書かない。

`enforceRateLimit` はルートとクライアントまたはIPごとのカウンターを `api_request_log` に更新する。分析APIなど、このレート制限の対象APIは、銘柄検索をR2へ移した後もD1アクセスが残る。決定済みの方式はDurable Objectsで、既存の共有制限のキー・窓幅・上限・拒否動作を保つ。切替後に `api_request_log` はレート制限カウンターとして使わず、現行の1日保持期間を経過したカウンター履歴の移行は不要とする。

### 端末通知と旧サーバーFCM

Androidは `LocalImportStore` にウォッチリストを保存し、`NotificationStore` に通知設定、価格通知状態、保留中通知、通知履歴を保存する。価格通知自体も端末内で投稿する。

現在の価格取得・通知実装と目標仕様の差、更新失敗時と価格鮮度の扱いは「価格取得とオフライン通知」に記載する。配当権利日・配当変更の更新Workerは別系統で通信を必要とするため、このオフライン価格通知の仕様と混同しない。

Workerソースでは `/api/v1/clients/register`、`/api/v1/push-devices`、`/api/v1/watchlist-targets`、`/api/v1/notification-events` が `410 device_managed` を返す。定期処理からも `watchlist_targets` の読み取り、`notification_events` への書き込み、FCM配信を外した。Androidソース内にこれらのAPI呼び出しはない。D1の関連テーブルと既存行は変更・削除していない。このWorker変更は本番反映済みだが、外部クライアントの利用と本番トラフィックは未確認であり、これらAPIを利用する外部連携があれば停止している。

### 価格取得とオフライン通知

価格カタログ取得と価格通知判定を別ジョブにする。アプリ起動時に更新を判定し、通常取得は日本時間で同日に重ねず、前回の試行から12時間以上経過している場合に日本株カタログを取得する。これは「暦日で最大1回」と「前回試行から最低12時間」の両方を満たす条件である。取得失敗は通常の成功時ゲートとは分け、現行の30秒再試行間隔を引き継ぐ。アプリが起動されない日は新しい価格を取得しない。取得結果は端末に保存し、銘柄ごとの価格・価格日・取得日時とカタログの市場データ日・取得状態を記録する。失敗時は最後に成功した値を残すが、その価格日を更新したり新鮮な値と見なしたりしない。

アプリ起動時の通常価格更新では、保有中またはウォッチリスト登録中の日本株コードだけを取得する。API呼び出しは最大50コード単位で、すべて同じ公開済みgenerationと市場データ日に固定する。取得した価格・価格日を銘柄別端末キャッシュへ保存し、応答に含まれない銘柄の既存値は消さない。IRBANK利回り契約が旧版の場合だけ、通常のJST日付・12時間ゲートを満たした価格更新時に全カタログを一度取得して契約を更新する。この契約更新は同日・12時間未満の追加取得を発生させない。

指定時刻の価格通知はネットワーク制約を持たず、ウォッチリストと端末キャッシュだけで動作する。日本株のみを対象にし、有効な価格がない銘柄、または価格日が東証の完了済み営業日から3営業日を超えて古い銘柄は通知を見送る。銘柄ごとに価格日を判定し、カタログ全体の取得時刻だけで鮮度を判断しない。同一アプリプロセス内で起動時処理と保有銘柄追加処理が重なっても二重更新しないよう、更新判定と取得を直列化する。ネットワーク失敗、プロセス中断、世代競合で結果を公開できなかった試行は、30秒以上空けて再試行できる。米国株はこのオフライン価格通知と日次価格カタログ取得の対象外とする。

このworktreeの価格通知Workerは端末キャッシュだけを読み、ネットワーク制約もAPIフォールバックも使わない。Workerの定期実行時刻はOSにより遅れることがあり、指定時刻ちょうどの実行は保証しない。アプリの価格更新間隔とOSの通知実行精度は別の要件として扱う。配当権利日・配当変更の更新Workerは別系統のオンライン処理として維持する。

端末のバックアップ規則では `local_import_store` と `notification_delivery` のSharedPreferencesがAndroid Auto Backup対象外になっている。別のアプリ内スナップショットは保有情報とウォッチリストを扱うが、通知配信状態は含まれない。今回の保存先移行だけでは復元対象を変更せず、端末移行時にウォッチリスト・価格キャッシュ・通知済み状態のどれを復元するかは、バックアップ仕様として別途決める。

### インカム空白月候補

空白月候補はD1からR2への新規移行対象ではない。既存処理は、定期同期が `market/yfinance/v2/derived/income_advice/current.json` を公開し、Workerの `/api/v1/income-advice-candidates` が配信する。Androidは起動時に取得結果をキャッシュし、JST日付と12時間の更新判定を使ってローカル保存する。ウォッチリストの銘柄と配当月を端末で照合し、空白月候補を順位付けして最大3件を表示する。候補の生成・API提供・端末キャッシュ・ランキングの各段階でD1を参照しないことを維持する。

既存の作業記録には本番APIが世代 `2026-10-19`、候補2,993件を返した読み戻し記録がある一方、候補スコアの欠損補完変更後に派生一覧を再生成する必要があるとの記録もある。したがって、その件数を現行成果物の保証値とはみなさず、移行実装時にR2オブジェクトの生成日時・世代・件数とAPI応答が一致し、現在の採点仕様で生成されていることを読み戻して確認する。価格カタログの3営業日ルールを候補データへ適用しない。候補データの鮮度はその同期元の生成仕様で別途検証する。

### 問い合わせ・認証

`clients` はクライアントID、シークレットハッシュ、無効化状態、最終利用時刻を保持する。`support_feedback` は受付番号での重複を防ぎ、`support_feedback_email` は送信試行、次回時刻、リース、送信結果を保持する。トリガーと外部メール送信を含むため、D1に残す候補とする。

## 移行手順

1. **移行前の基準を取る。** D1上限・行読み取り量、R2世代と件数、API応答、Worker Cron状態、外部通知APIのトラフィックを記録する。本番D1はメールに記載された上限リセット（2026-11-04 00:00 UTC）後に読み戻し可能となるまで一括読取を始めない。
2. **データと書き込み元を棚卸しする。** D1表ごとにWorker API/Cron、Android、GitHub Actions、手動スクリプトの読書き元を列挙する。`organized/d1/*.csv` のようなローカル入力はCloudflare D1と区別する。R2に既に存在するデータは、生成日時・世代・銘柄期間の被覆・代表値・API応答を確認し、欠損データだけを移行対象にする。
3. **受け入れ条件と移行道具を準備する。** R2キーとJSON schema、保持期間、履歴ID、世代ポインター、冪等性、再開方式を確定する。Workerの単体/ルートテストと、移行対象・件数・ハッシュを確認するドライランを用意する。Worker CIとD1→R2の手動Workflowはローカルに追加済みなので、公開後にGitHub Actions上の実行結果を確認する。既存の定期R2公開Actionsは変更せず継続する。
4. **データ更新との競合を止める。** 最終差分と切替の短い時間だけ、レビュー済みWrangler設定の `[triggers] crons = []` でWorker Cronを停止する。Cron無効化後も認証付き `POST /api/v1/admin/run-analysis` は呼び出せるため、リポジトリ外も含む呼出元を棚卸しし、管理・自動実行を同じ停止窓で止める。Worker Logsのinvocation loggingが有効かつsampling rate 1であることを事前確認し、Cron EventsとWorker Logsで実行中のCron/管理分析の完了と、停止窓内に新たな管理実行がないことを確認してから最終読み取りとR2照合を行う。ログの有効性・全件性を確認できない場合は、別途レビュー済みの管理API停止手段を用意するまで最終スナップショットへ進まない。R2公開Actionsは止めない。Cron停止中は問い合わせメール再試行・保持期限削除も遅れるため、outboxを確認して停止を短くする。切替結果とoutboxを確認した後、通常Cron設定へ戻し、管理分析の呼び出しを再開する。
5. **対象ごとにバックフィルする。** 手動Actionsで表ごとに小さい再開可能な単位で読み出し、R2に公開する。`securities`・財務・配当はR2の被覆を突き合わせて不足分だけ扱う。分析状態/履歴は保持規則を反映し、完了済み実行ログはR2へ、実行中制御状態は選定先へ分ける。データ本体をActionsログや通常アーティファクトへ保存しない。既存R2を無条件に上書きせず、D1からの読み取り量と残り枠を各バッチで監視する。
6. **R2の読み戻しと差分を確認する。** バッチごとの件数・ハッシュ・期間範囲を照合し、代表銘柄でAPI応答を比較する。途中失敗は同じチェックポイントから再開し、成功扱いにしない。R2側のcurrentポインターは全データがそろった後に切り替える。
7. **Workerを切り替える。** `/api/v1/analysis/{code}` の銘柄確認、定期分析の一覧・状態・履歴、定義・監査、health表示をR2へ切り替える。Durable Objectsのレート制限を有効にする際は、現行 `api_request_log` の有効期間中のカウンターを引き継ぐか、各制限窓が切れるまで旧制限を維持する。実装は各DOの初回利用時に対象カウンターを一度だけ読み取り、DO状態へ移す。切替直後にカウンターを空にして制限を一時解除しない。読み取り・書き込みを確認後、手順承認を経てREADME記載のWorkerデプロイを行う。
8. **Androidの価格取得と通知を切り替える。** 起動時の通常更新は保有・ウォッチ対象の日本株コードに限定し、50コード単位で取得した価格と価格日を端末キャッシュへ保存する。通常更新のJST同日1回・前回試行から12時間以上というゲートを維持する。価格なし／3東証営業日超過時の見送り、欠落銘柄の既存価格保持、端末バックアップ範囲をエミュレータで検証する。
9. **全体を受け入れてから旧経路を整理する。** CI、Worker API・Cron、Android、R2読み戻し、D1使用量を検証し、旧サーバー通知API・キュー・スクリプトの外部利用と未処理メッセージを確認する。問い合わせ等で必要なD1は維持する。バックアップ・保持要件・外部利用を確認するまでは、移行元データやテーブルを削除しない。市場データのD1再投入スクリプトだけを停止・修正する。

## 完了条件

- 市場データの正本がR2で、Worker/API・定期処理・投入スクリプトが同じデータをD1と二重管理しない。
- `/api/v1/income-advice-candidates` が既存R2オブジェクトと一致し、Androidの端末キャッシュと空白月順位付けがD1なしで動作する。
- 分析履歴APIがR2から従来の順序・上限・データ形式で履歴を返す。定期分析の最新状態と変更判定もR2の状態を使う。
- 完了済みの定期処理ログ・成果物はR2にのみ保存され、ヘルス確認も切替後の保存先を読む。
- DO初期化後の分析API呼び出しで市場データ目的のD1検索、D1定義フォールバック、D1監査書き込み、D1レート制限更新を行わない。初期化時の一度限りの有効カウンター読み取りはDOへ状態を引き継ぐためのものとし、通常リクエストで繰り返さない。
- Worker変更を検査するGitHub Actionsが関連パスで起動し、D1→R2の移行Actionsは手動実行・再開可能・照合可能である。既存の定期R2公開Actionsは維持し、本番D1への意図しない書き込みがない。
- Durable Objectsへの切替時に有効なレート制限窓を引き継ぐか、旧窓の期限切れを待ち、一時的な制限リセットが起きない。
- 価格取得はアプリ起動時に判定し、通常取得は日本時間で同日に1回まで、かつ前回試行から12時間以上空ける。失敗後は30秒間隔で再試行できる。対象は日本株。価格通知はネットワークなしで端末保存データから判定し、価格欠損または3東証営業日を超える古い価格では通知を見送る。WorkManagerの実行遅延を含め、指定時刻ちょうどの実行を保証しない。
- 分析APIのレート制限はDurable Objectsの共有状態を使い、現行のキー・上限・期間・拒否応答が維持される。
- 問い合わせと必要な処理中制御状態が従来どおり動く。端末管理へ切り替えたWorkerソースは個人別ウォッチリストを読み書きせず、FCMを送らない。旧D1テーブル・データは別途バックアップと保持要件を確認するまで削除しない。
- D1の行読み取り量を移行前後で比較し、残存するD1クエリの用途と量を説明できる。

## 実装時に検証・決定する事項

- D1上限リセット後に `market-source-audit` を表ごとに実行し、市場カタログ・財務・配当のR2網羅率、期間・値・出典、表現できない粒度を確定する。報告された不足分は、既存API契約に合うR2正規形への補完方法をレビューしてから移行する。
- 通常価格取得は保有・ウォッチ対象の日本株コードのみとし、応答にない銘柄の端末価格キャッシュは消さず、銘柄ごとの価格日で鮮度を判定する。IRBANK利回り契約更新時の全カタログ取得も通常の日付・12時間ゲートを守る。
- 旧`market_price_refresh_runs`/`market_price_refresh_batches`をDurable Objectsへ移さない。現行Workerにはキュー送信呼び出しがなく、consumerは旧価格更新メッセージをACKするだけで、これらのD1表を処理状態として読書きしない。DOは共有レート制限専用とする。キュー実測backlog、稼働中Versionのproducer、バックアップと保持要件を確認するまではQueue bindingとD1行を保持し、完了済み行が必要なら`legacy-market-archive`でR2へ保全する。
- R2履歴キー、世代ポインター、最新状態の同時更新、再試行時の冪等性。
- 最終差分時はCronの短時間停止を使う。切替前に実行中呼び出しの完了、feedback outboxの再試行、DOレート制限を維持できるロールバックWorkerを確認する。
- D1→R2の手動Actionsに設定する表/フェーズ単位、1回あたりの読み取り予算、チェックポイント形式と承認方法。
- 旧サーバーFCM APIの外部利用と、本番リクエストの残存状況。
- Androidバックアップ／復元の現行設定（2026-11-04のコード確認）: `allowBackup=true` で、API 31以上向け `data_extraction_rules.xml` と旧API向け `backup_rules.xml` は `local_import_store.xml`・`notification_delivery.xml` だけを除外する。Android Auto Backupの規則上、それ以外のアプリファイルとSharedPreferencesはバックアップ／端末間転送の対象になり得るが、実際の転送成功は端末設定・OS・容量に依存し、コード確認だけでは証明できない。
  - `local_import_store.xml` 自体は除外されるが、`filesDir/diview_auto_backup.json` は除外されず、保有銘柄・ウォッチリスト・DVMバックアップ・携帯可能な上書き設定を含む。復元後、`LocalImportStore` はこのスナップショットから保有銘柄／ウォッチリスト等を遅延復元する。
  - `notification_price_cache` と `notification_preferences` は除外されない。価格キャッシュと通知設定はバックアップ／端末間転送の対象になり得る。`notification_delivery.xml` は除外され、通知済み・保留中配信状態は転送されないため、復元後の重複通知に関するプロダクト意図と実端末復元は未確認。
  - `cloudflare_market_cache` など他のSharedPreferencesも個別の除外指定がない。実データ容量とバックアップ実行結果は未確認。移行だけを理由に既存の除外設定は変更しない。
- インカム空白月候補R2成果物の生成日時・件数とAPI応答の一致。既存調査記録に件数・鮮度の差があるため、本番読み戻しで基準を確定する。

## 調査時点の注意

2026-11-03に確認したD1分析クエリは、インデックス追加前に `SCAN securities` を行っていた。本番では `security_code_display` 用インデックスを追加し、実行計画が主キーと同インデックスを使う形になった。日次読み取り制限のため、追加後の実使用量は未測定である。このインデックスは移行中の性能対策であり、R2移行や残存D1アクセス整理の代替ではない。

## 2026-11-03 19:53 UTCの継続確認

- 読み取り専用 `wrangler d1 info diview-irbank --json` は `rows_read_24h=6,916,966`、`read_queries_24h=3,017`、`write_queries_24h=6,053`、`rows_written_24h=18,207`、DBサイズ100,163,584 bytesを返した。5,000,000行上限を超え、メール記載のリセット時刻2026-11-04 00:00 UTCより前のため、本番D1行SELECTは実行していない。
- 移行監査のレポートとチェックポイントから銘柄コード・rowid等の行サンプルを除き、既存形式のチェックポイントを再開するときもサンプルを破棄するよう修正した。Worker 113/113件、移行ツール31/31件、`node --check src/index.js`、`git diff --check` が成功した。変更はローカルの未コミット差分。
- GitHub `main` のworkflow一覧には既存の9ファイルがあり、追加予定のWorker CIと手動移行workflowは未公開。workflow実行、本番R2書込、Workerデプロイ、実機変更は行っていない。移行Environment設定はこの確認では再照会できていないため、前回読み取り結果を最新状態の証明に使わない。

## 2026-11-03 23:30 UTCのR2インベントリとローカル検証

- R2の読み取り専用全件一覧（23:19 UTC生成）は231,237オブジェクト、合計3,503,023,470 bytesを報告した。内訳は `market/irbank/` 11,665、`market/securities/` 3,965、`market/yfinance/` 215,604、`debug/jquants/` 3。`analysis/`、`definitions/`、`operations/`、`audit/` は0件であり、分析履歴・状態、R2定義、完了ログ、監査履歴を本番Worker切替前に用意する必要がある。
- `market/irbank/v1/manifest.json` は2026-10-25T02:39:09.336Z生成、3,963 securities、3,965 manifest objects。参照キーは一覧上ですべて存在したが、各オブジェクトの内容ハッシュを再計算したわけではない。`overview.json` と `dividends.json` は各3,963件。
- `market/yfinance/v2/catalog/current.json` と `derived/income_advice/current.json` を読み戻し、generation `2026-11-02-daa434e899b0493a9fdf346067419041`、4,390/3,203件、重複0、候補参照generation欠落・不一致0を確認した。Worker APIとD1移行元との完全比較は未実施。
- 端末の通常価格更新は保有・ウォッチ対象の日本株を重複排除し、最大50コード単位で同じgeneration・市場日に固定して取得する。対象N件の要求数は `ceil(N / 50)`、対象0件なら0。旧IRBANK利回り契約の全カタログ取得は通常のJST日・12時間ゲート内の一度限りの更新に限定され、通知Workerは端末キャッシュのみを読む。テストは51コードで2要求、対象選別・重複排除、現行契約での全カタログ取得なしを確認した。実ユーザーの本番リクエスト量は未計測。
- `masacgt/Diview` のmainでは新Worker CIとD1→R2移行Workflowの取得が引き続き404。Environmentの現在設定は読み取りできず、GitHub Actionsは実行していない。
- ローカル検証はWorker 114/114件、移行ツール37/37件、JDK 17.0.20.1 Android JVM 267/267件、emulator-5556 instrumentation 35/35件。Debug/AndroidTest APKをビルド・インストールし、生成APKとemulator-5556のbase.apkはSHA-256 `b0547594844800650b272f2517e176686509fd6d577966c530403cfa729bd156` で一致した。物理端末、本番D1行、本番R2書き込み、Workerデプロイは変更していない。
- UTC深夜近くに保持テストが失敗した原因は、+1時間のfixtureが日付をまたいで別の日次保持枠に入ったこと。UTC正午を基準にするテスト修正後、Worker全件が成功した。製品の保持処理は変更していない。
- D1使用量の最終確認は2026-11-03 22:47 UTCの6,904,136 rows/24h。2026-11-04 00:00 UTCのリセット後に新しい使用量を確認するまで、本番D1行SELECTを行わない。

## 2026-11-03 19:59 UTCのActions確認

- GitHub REST APIの仕様では、Environment secrets一覧の取得にEnvironmentsリポジトリ権限が必要だが、その権限はWorkflow `GITHUB_TOKEN`の設定可能範囲にない。migration workflowのpreflightから当該API呼び出しを削除し、保護されたEnvironmentの承認後、migration job内で5つのsecret値の存在だけを検査するよう変更した。値はログへ出さない。
- 両WorkflowのYAML解析と構造検査に成功した。移行Workflowは `workflow_dispatch` のみで、`migrate` は `preflight` 成功と `d1-r2-migration` Environmentを必要とする。GitHub mainには未公開でActions実行は未確認。本番D1/R2操作、デプロイ、実機変更は行っていない。

## 2026-11-03 20:06 UTCの監査出力修正

- ローカルコードレビューで、`market-source-audit` が最大20件の銘柄コード・期間・R2キーを監査レポートと再開チェックポイントへ含めることを確認した。これはaggregate-only出力の要件に反するため、行サンプルを削除し、欠損/差分フィールド名ごとの集計件数を出すよう修正した。チェックポイント内のR2ソースキーはSHA-256フィンガープリントへ置換し、以前のチェックポイントに含まれるサンプルも再開時に破棄・変換する。
- 回帰テストはレポートとチェックポイントの双方に銘柄コード、R2キー、`sample` が出ないこと、および従来の監査差分を集計フィールド件数で確認する。Worker 113/113件、移行ツール32/32件、`node --check src/index.js`、`git diff --check`、2つのWorkflowのYAML構造検査が成功した。
- 確認時刻はD1通知の上限リセット2026-11-04 00:00 UTCより前。本番D1行SELECT、本番R2書き込み、Actions実行、Workerデプロイ、実機変更は行っていない。市場source監査、本番データ補完、R2移行は引き続き未完了。

## 2026-11-03 20:09 UTCのチェックポイント検証

- チェックポイントの `market_source_object_hashes` がキーそのものを持っていたため、R2オブジェクトキーのSHA-256フィンガープリントで照合するよう変更した。以前の生キー形式のチェックポイントも読み込み時にフィンガープリントへ変換し、再開時のR2変更検出を維持する。エラー出力からも銘柄コード・生R2キーを除いた。
- Worker 113/113件、移行ツール33/33件、`node --check src/index.js`、`git diff --check`、GitHub Actions YAML構造検査が成功。実装はローカル未コミット。D1上限リセット前のため本番行SELECTを行わず、本番R2書込・Workflow実行・Workerデプロイもなし。

## 2026-11-03 20:11 UTCのテーブル対応確認

- リポジトリのD1基底schemaとmigration SQLから22個のアプリテーブルを抽出し、runbookの移行表に全22件の名前があることを確認した。D1の使用量メタデータが示した23件目が `d1_migrations` かは本番 `sqlite_schema` 未確認のため未確定とし、あればWranglerの管理メタデータとして保持し、R2データ移行から除外する記載を追加した。
- 本番D1行読取はリセット前のため未実施。テーブル名比較はローカルschemaと文書だけの検査であり、本番の実テーブル構成の証明ではない。

## 2026-11-03 20:22 UTCの候補APIとレート制限確認

- R2の候補オブジェクトはgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`、3,203件、重複0で、全候補の `source_catalog_generation` が同世代だった。カタログは同generation・4,390件・重複0。Worker APIは同世代・同件数を返す200と500が混在した。
- `wrangler tail`で記録した本番Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` の候補API 500は、R2読み取りではなく `enforceRateLimit` 内のD1行読み取り上限エラーだった。従ってこの500をR2欠損の根拠にしない。成功したAPI応答の件数・世代はR2と一致したが、先行する内容比較ではフィールド差が報告されており、完全一致は未確認。D1上限リセット前のため追加の本番D1行SELECTは行っていない。
- ローカルコードでは候補ルートが共通の公開レート制限処理より前に応答していた。既存契約の `public:income-advice-candidates`、300回/60秒をR2取得前にDurable Objectで適用し、上限超過は既存どおり429と `Retry-After` で拒否するよう修正した。ルートテストはDOのキー・窓・上限、拒否時のR2未読込、ハンドラからのD1未呼び出しを確認する。DO初回の旧カウンター引継ぎ用D1読み取りは別の一回限りの処理として維持する。
- GitHub CIと同じWorkerテスト114/114件、移行ツール33/33件、`node --check src/index.js`、`git diff --check` が成功した。変更はローカルのみ。本番Workerデプロイ、R2書き込み、移行Workflow実行は行っていない。

## 2026-11-03 20:27 UTCのGitHub Actionsリモート状態

- `git ls-remote origin refs/heads/main` は `b3f3a7b75bc54d30ec468fc085922791f99e340b` を返し、実装worktreeのHEADと一致した。GitHub APIで既存の `yfinance-r2.yml` は取得できたが、追加した `cloudflare-worker-ci.yml` と `d1-r2-migration.yml` はmain上で404だったため未公開と確認した。新Workflowファイルはローカルworktree内にあり、GitHub Actionsはまだ実行されていない。
- CIと同一のWorkerテストコマンド114/114件と移行ツール33/33件がローカルで成功した。GitHubのmigration Environment設定はこの接続から読み取れず、Workflowのpreflightで保護reviewerとmain限定branch policyを確認する設計のまま、実設定の受入れは未確認。Workflowの公開・起動はしていない。

## 2026-11-03 20:35 UTCのAndroid価格再試行確認

- 起動時の共通更新ゲートが同日再実行を抑止すると、価格取得が失敗していても独立した30秒再試行判定へ到達しない経路を確認した。価格取得の失敗状態があり、前回試行から30秒以上経過した場合だけ、共通ゲートを越えて価格更新を再試行するよう実装用worktreeのAndroidコードを修正した。価格取得成功後の同日重複取得は引き続き抑止される。
- 新規の `failedPriceRefreshRetriesAfterCooldownWhenStartupRefreshIsSuppressed` と既存の `startupSkipsTheSameDayEvenAfterTwelveHours` をJDK 17.0.20.1で各1/1件、`emulator-5556` 上で実行し成功した。Debug APKとエミュレータにインストールした `base.apk` のSHA-256は `ed341dab1c57f8c8a737fe5ec65e44a52e97134c6b6f0f9a8afa3d92e7f620bf` で一致した。
- これは未コミットのローカル実装とエミュレータ確認であり、物理端末、Cloudflare本番D1/R2、Actions、Worker本番環境には変更を加えていない。

## 2026-11-03 20:38 UTCのGitHub Environment確認

- `https://github.com/masacgt/Diview/settings/environments` をブラウザーで読み取り確認した。Environment一覧は `IRBANK_API_KEY` のみで、移行Workflowが要求する `d1-r2-migration` は存在しない。シークレット値の閲覧や設定変更はしていない。
- したがって移行Workflowは現在の設定ではpreflightで停止する。管理者によるEnvironment作成、main限定、独立reviewer・自己承認防止の設定と確認は未完了であり、移行Workflowは起動しない。

## 2026-11-03 20:52 UTCの継続検証

- 読み取り専用の `wrangler d1 info diview-irbank --json` は `rows_read_24h=6,912,701`、`read_queries_24h=2,948`、`write_queries_24h=5,934`、`rows_written_24h=17,842`、サイズ100,163,584 bytes、23テーブルを報告した。5,000,000行を超過し、リセット時刻2026-11-04 00:00 UTCより前だったため、本番D1行SELECTは行っていない。
- ローカル検証はWorker 114/114件、D1→R2移行ツール33/33件、`node --check src/index.js`、JDK 17.0.20.1のAndroid単体テスト266/266件で成功した。`assembleDebug` と `assembleDebugAndroidTest` も成功し、`emulator-5556` だけにインストールしたAPKとビルドAPKのSHA-256は `ed341dab1c57f8c8a737fe5ec65e44a52e97134c6b6f0f9a8afa3d92e7f620bf` で一致した。通知instrumentationは13/13件、価格取得失敗後の再試行と同日・12時間ゲートの各1/1件が成功した。
- GitHub REST APIの読み取り確認では、main上の新Worker CIと手動移行Workflowは引き続き404、Environmentは `IRBANK_API_KEY` のみだった。Environment設定・secret値は変更・閲覧していない。
- 以上は未コミットのローカル実装とエミュレータの証拠である。物理端末、本番D1/R2、GitHub Actions、Worker本番環境は変更していない。D1のテーブル・データ監査、R2現行世代とAPIの再照合、正本形式への不足データ補完、Actionsの公開・環境保護設定、本番移行・切替は未完了である。

## 2026-11-03 21:12 UTCの銘柄metrics照合確認

- `securities.metrics_json` の非空値を、IRBANK v1の銘柄別 `overview.json` にある `security.metrics` と比較する処理を `market-audit` に追加した。マニフェスト記載のSHA-256と比較対象オブジェクトのハッシュを照合し、レポート/チェックポイントには比較・差分・欠損・不正件数のみを残す。移行ツールの回帰テスト35/35件が成功した。
- リポジトリ内のsecurities importは3,963行すべてJSONとして有効で、非空値3件は現行R2の対応する銘柄別metricsと一致した。これはチェックイン済みimportと現行R2の比較であり、本番D1全行の一致を示すものではない。
- 現行Workerの分析入力はR2から構築され、`securities.metrics_json` は使われていない。銘柄APIも `metrics: []` を返しており、この列を照合のために保持してもAPI応答の変更はない。過去のD1 SELECTに同列が含まれたことは、分析計算での利用を意味しない。
- 現行D1使用量はリセット前のため、本番D1行SELECTと全件監査は行っていない。本番R2書き込み、Workflow実行、Workerデプロイも行っていない。リセット後に `d1 info` の使用量を確認し、小さい予算で本番 `market-audit` を実行する必要がある。

## 2026-11-03 21:28 UTCの移行チェックポイント監査

- `analysis-current` のチェックポイントが銘柄コードをカーソルに保存し、定期実行の再開状態がR2オブジェクトキーを保存していた。分析カーソルを数値rowidへ変更し、旧コードカーソルからは既存R2書き込みの冪等性を使って先頭から再開する。定義フェーズは既存の数値indexだけを使い、定期実行キーは完了時にrun ID/typeから再構築する。旧チェックポイントを読み込む際にも既存のコードカーソル・定義キー・R2キーを取り除く。
- レポート/チェックポイントの回帰テストは37/37件成功。チェックポイントにはページ再開用の数値カーソルと定期実行の最小メタデータが残るが、行本文・銘柄コード・定義キー・R2キーは残さない。本番D1行読み取り・R2書き込み・Workflow実行・Workerデプロイは未実施。

## 2026-11-03 21:54 UTCのR2読み戻しと再検証

- Wranglerで本番R2の `market/yfinance/v2/catalog/current.json` と `market/yfinance/v2/derived/income_advice/current.json` を直接読み戻した。generationはいずれも `2026-11-02-daa434e899b0493a9fdf346067419041`。カタログ4,390件、空白月候補3,203件、重複コード0で、候補の `source_catalog_generation` は全件同世代だった。
- 既知キー `analysis/v1/current/7203.json`、`analysis/v1/state/source.json`、分析run/retention currentポインター、株式/ウォッチリスト定義currentポインターはNot Found。これは確認した個別キーの結果で、各R2 prefix全体が空である証拠ではない。Worker API応答との照合、D1由来データの不足判定・補完は未実施。Worker API読み戻し、本番D1行SELECT、R2書き込み、デプロイは行っていない。
- 現行差分のWorkerテスト114/114件、移行ツール37/37件が成功。JDK 17.0.20.1でAndroid JVMテスト266/266件、`AssetDataRefreshTest` 20/20件、`NotificationDeliveryTest` 13/13件、`ReleaseNotificationPlatformTest` 2/2件が成功した。初回の通常価格更新テストは契約版0のfixtureが旧IRBANK契約用の一回限りカタログ更新を起動して失敗したため、現行契約版1をfixtureへ設定して通常更新経路を再現した。生成APKと`emulator-5556`の`base.apk` SHA-256は `ed341dab1c57f8c8a737fe5ec65e44a52e97134c6b6f0f9a8afa3d92e7f620bf` で一致した。


## 2026-11-03 22:16 UTCのQueue切替前確認

- 読み取り専用Wrangler 4.136.1で、diview-market-pricesはproducer 1件・consumer 1件、どちらもworker:diview-irbankと確認した。DLQはproducer 0件・consumer 1件で、consumerは同じWorker。origin/mainにはsendBatch呼び出し箇所が3つあるが、enqueueDailyMarketQuotes、splitLegacyMarketQueueMessage、refreshLiveMarketQuotesの呼び出し元は現在のソース内に見つからなかった。
- キューhandlerのACK処理はorigin/mainにも存在し、今回の差分で変更していない。作業中Workerは送信helperを削除するが、Wranglerのproducer/consumer bindingは残る。最新deployment listではVersion 4981c1e0-e337-4392-9f5b-909b52c5bc0cが100%配信中（2026-11-03 12:26:30 UTC作成）だが、稼働VersionとソースSHAの対応は未確認。wrangler queues infoはbacklog_count等を返さず、キュー残量は未確認。
- 本番切替前に通常キューとDLQのbacklog metricsを取得し、稼働Versionの送信経路、producer binding、処理中の送信、未処理メッセージの扱いを確認する。送信helperが現在のソースから到達不能でも、Versionとの対応とbacklogが未確認ならQueueを廃止・削除せず、D1のrun/batch記録を保持する。


## 2026-11-03 22:41 UTCの価格通知カレンダー再検証

- NotificationPolicyTest.officialTseClosuresFor2027AndThreeSessionFreshnessAreCorrectを追加し、JPX公表の2027年東証現物市場休業日とGW後の3営業日目/4営業日目の鮮度境界を確認した。2026年休業日の既存テストも維持した。日程の根拠は[JPX市場休業日](https://www.jpx.co.jp/corporate/about-jpx/calendar/index.html)。
- JDK 17.0.20.1のtestDebugUnitTestは267/267件成功。追加テストを含むNotificationPolicyTestを再実行して成功し、assembleDebugとassembleDebugAndroidTestも成功した。
- emulator-5556だけにAPKをインストールし、関連instrumentation 35/35件が成功。生成app-debug.apkと端末base.apkのSHA-256はb0547594844800650b272f2517e176686509fd6d577966c530403cfa729bd156で一致した。物理SCG01にはインストールしていない。

## 2026-11-03 22:47 UTCのD1使用量確認とR2現行読み戻し

- wrangler d1 info diview-irbank --json は rows_read_24h=6,904,136、read_queries_24h=2,809、write_queries_24h=5,679、rows_written_24h=17,077、DBサイズ100,163,584 bytes、23テーブルを報告した。取得時刻は22:47 UTCで、通知された2026-11-04 00:00 UTCのリセット前。上限超過のままなので、本番D1行SELECTは実施していない。リセット後に最新メタデータで再確認してから、小予算で監査を始める。
- その後Wrangler 4.136.1で本番R2のcatalog/current.jsonとincome_advice/current.jsonを読み戻した。両オブジェクトのgenerationは 2026-11-02-daa434e899b0493a9fdf346067419041、生成時刻は2026-11-04 04:41 JST。items[].code で数えた結果は4,390件と3,203件、重複コード0。候補のsource_catalog_generation欠落0・現行generation不一致0だった。items[].security_codeという誤った前提での最初の集計は0件を返したため破棄し、JSONフィールドを確認して再集計した。
- 現行Workerテスト114/114件、移行ツールテスト37/37件が成功し、両worktreeのgit diff --checkに空白エラーはなかった。GitHub mainから新Worker CIと手動移行Workflowのファイルを取得すると404。GitHub Environmentの設定は現在の接続手段から読み取れず、前回確認時に未作成だったd1-r2-migrationの現状は未確認。Workflowは起動していない。

## 2026-11-04 D1棚卸しフェーズ追加

- 移行ツールにdry-run専用の`d1-schema-audit`と`d1-operational-audit`を追加した。前者はSQLiteのsystem objectを含む全table/trigger名を読み取り予算内で列挙する。後者は`backend_state`、`scheduled_run_log`、`market_price_refresh_runs`、`market_price_refresh_batches`をnumeric rowidでページングし、状態キー・status/date/countの要約だけを出力する。`value_json`、run ID、run metadata、元行本文は取得対象・レポートに含めず、非定型state keyはSHA-256表示に置き換える。2026-11-04に追加したscheduled-run監査は`finished_at`欠落数も集計する。
- 2026-11-04の作業差分では、mainの旧Worker sourceがhealthとライブ価格更新に`market_price_refresh_runs`を読み書きしていた経路をR2 quote state/completionへ移し、旧Queue handlerは受信メッセージをACKする。これらのD1行は履歴として残り、任意のraw archiveはcanonicalな稼働状態ではない。queue backlog、外部producer、保持・バックアップを確認するまではqueue・D1行を削除しない。
- D1専用フェーズにはD1 read credentialだけを渡し、R2-backed phasesは別stepでR2 S3 credentialsを要求する。ローカルのWorker suite 115/115件、移行ツール44/44件、Workflow YAML parseとphase/default/secret-scope整合チェックが成功した。新フェーズは本番で未実行。Dashboardの日次D1残枠、schema/operational inventoryの本番結果、各ログの保持先・正本形式、GitHub Actionsの公開・実行は未確認。
- 現行ローカル`src/`のSQL参照はDurable Object初回の`api_request_log` lookupと問い合わせ・メールoutbox処理に限られる。healthは`d1: !!env.DB`を返すだけで、カタログ・分析・定期run・定義・通知状態をD1から読んでいない。これは未デプロイの作業差分の静的確認であり、本番Versionのアクセス実績を示さない。

## 2026-11-04 UTCの追加R2読み戻し

- R2から `market/yfinance/v2/catalog/current.json` と `market/yfinance/v2/derived/income_advice/current.json` を直接読み戻した。どちらもgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`、生成時刻 `2026-11-03T19:41:21Z`。カタログは市場データ日 `2026-11-02`、4,390件、重複コード0。候補は3,203件、重複コード0で、全件の `source_catalog_generation` が一致。オブジェクトSHA-256はカタログ `5830777b686024bc313eed59916fe4db4cbd26b834eff9486f2c8c921e367a1e`、候補 `964abdc8458e6ac2757274b36db6ce691f1dde86b9931f0f423397a5ccc8d1ab`。
- この確認はR2 GETのみで、D1読み取り・R2書き込みは行っていない。現行Worker API応答との比較は未実施であり、Dashboardの日次D1残枠を確認するまで本番D1行監査を再開しない。

## 2026-11-04 04:30 UTCの監査・移行テスト追記

- リセット後の本番D1読み取り専用監査は合計4,276行（各クエリの`meta.rows_read`合算）、D1書き込み0。`sqlite_schema`はテーブル25行・trigger 2行を返し、Cloudflare管理の`_cf_KV`とSQLiteの`sqlite_sequence`を除く22アプリテーブルに`d1_migrations`を加えるとWranglerの23テーブルと一致する。`support_feedback_email_insert/delete` triggerが存在する。
- `backend_state`は1行読み取りで対象キーなし。`market_price_refresh_runs`は2026-10-10の`partial`（1,980 batch、57 failed）と2026-10-12の`running`（1,980予定、100 enqueued/completed、3 failed、完了時刻なし）。`market_price_refresh_batches`は当初4,160行と記録したが、2026-11-04 14:40 UTCの保存済み直接監査レポートで2,080行（2,020 complete・60 failed）と確認し、旧数値を訂正した。処理済み銘柄は4,040。これらの古い実行状態はQueue残量0という別時点のメトリクスだけでは説明できないため、保持先と復旧・保持要件は未確定。行は削除しない。
- 最新の`stock_analysis_history`はid 37,684、observed_at `2026-11-04T03:17:51.685Z`、market_date `2026-11-02`。`C:\Codex\r2-d1-ownership-audit-20261104\analysis-history-dry-checkpoint.json` が存在し、`analysis-history`/`securities`、cursor 1,500、dry-run、累計1,500行、`complete=false`を確認した。レポートは1,000行の初回と500行の再開を記録し、続くWrangler呼出しは終了コード1・空のstderrで結果不明だった。自動再試行はせず、正確なD1日次残枠を確認してからこのcheckpointで再開する。
- Dashboardの2026-11-04 00:00–04:11 UTC相当範囲はRows Readカード約113k、グラフ約126.18kで一致しない。同時刻のWrangler `rows_read_24h=7,004,607`はローリング24時間値で、日次上限の残量に転用できない。正確な日次残枠は未確定のため、大きなD1監査を続ける前に表示差を解消する。
- 2026-11-04 04:08 UTCのQueues画面では通常/DLQのrealtime・delayed backlogはいずれも0、lag 0秒だった。これは時点のメトリクスであり、古いD1 run/batchの保持要件や過去のメッセージ処理完了を証明しない。
- 2026-11-04 04:27 UTC、移行ツール48/48件、Worker 115/115件、Python `py_compile`、`git diff --check`が成功。実Botocore 1.43.123では移行ツールの`before-parameter-build.s3.PutObject`/`before-call.s3.PutObject`フックは正常に発火する。先のStubber確認はStubberが同イベントの先頭で未知引数を先に検証するための順序差であり、本番クライアントの不具合ではなかった。HTTP送信前を捕捉し、HTTP transportを禁止した統合テストで`If-None-Match: *`と引用符付き`If-Match`を確認する。CIは移行用boto3要件を入れてこの検証を実行する。
- この追記時点でD1/R2本番書き込み、Workerデプロイ、GitHub Actions起動はしていない。R2データとWorker APIの内容比較、未完了移行フェーズ、差異分類、Environment保護設定、Cron/Queue切替前確認は未完了。

## 2026-11-04 05:09 UTCの継続検証

- `RateLimitCounter`の初回D1カウンター読取が一時失敗した後、失敗Promiseを破棄して再試行できる経路を回帰テストに追加した。初回失敗でDOに状態行が作られず、次の要求で有効なD1カウンターを読み直してcount 5から継続することを確認した。Workerテスト116/116件、`node --check src/index.js`、`git diff --check`が成功した。
- 05:03 UTCの既存Dashboardタブは期間末尾が13:11 JSTのままで、Rows Readカード約113kとグラフ126.18kの不一致も同じだった。新しい使用量値としては扱わない。ブラウザーURLを安全に確認できず画面更新へ進まなかったため、現在の日次残枠は未確定。D1行クエリ、設定変更、R2書き込みは行っていない。
- 本番D1/R2書き込み、Workerデプロイ、GitHub Actions起動、コミット、プッシュは行っていない。R2とWorker API本文照合、未完了移行フェーズ、GitHub Environment、Cron/Queue切替条件は未完了。
- 05:11 UTCの読み取り専用Wrangler確認では、本番Workerは引き続きVersion `4981c1e0-e337-4392-9f5b-909b52c5bc0c` の100%配信。通常QueueはWorker `diview-irbank` のproducer/consumer各1、DLQはproducer 0・consumer 1だった。`wrangler queues info` はbacklog件数・byte数・最古メッセージ時刻を返さず、Queue backlogは未確認。`wrangler d1 info diview-irbank --json` はAuthentication error 10000で失敗し、D1行クエリは行っていない。`git ls-remote` ではmainがHEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b` のまま。ローカルの新Workflowは未公開で、起動していない。

## 2026-11-04 05:21 UTCのD1・Worker再確認

- 読み取り専用 `wrangler d1 info diview-irbank --json` は23テーブル、database size 100,532,224 bytes、`rows_read_24h=0`、read/write query数0を返した。しかし、同日04:11 UTCのDashboard記録はカード約113k・グラフ126.18kで不一致だった。0を日次Rows Readや残枠とみなさず、追加D1 SQLは実行していない。Cloudflareの請求使用量はDashboardまたはGraphQL Analyticsの`rowsRead`で確認する仕様である ([D1 billing metrics](https://developers.cloudflare.com/d1/observability/billing/)、[D1 metrics and analytics](https://developers.cloudflare.com/d1/observability/metrics-analytics/))。
- `wrangler d1 insights --time-period 1d --sort-type sum --sort-by reads --limit 10 --json` は、銘柄検索の`security_code = ? OR security_code_display = ?`を1,892回・合計6,978,979行・平均3,688行/回、全銘柄カタログSELECTを10回・合計39,600行と記録した。この1日窓はUTCの日次リセット区間とは一致せず、呼出し元も識別しないため、今日の正確な使用量ではない。一方、直近1日窓にも銘柄検索SQLが存在しており、本番旧経路の停止は確認できていない。
- 同時刻の5時間Insights（開始はUTC 00:21）は、銘柄検索SQLを118回・合計90行、全銘柄SELECTを1回・3,960行と記録した。これらは分析上の集計値で呼出し元を特定しないため日次請求総数には使えないが、UTCリセット後にも旧D1検索SQLが記録されている。ローカル差分を本番切替前の状態として扱う。
- 同時刻のWrangler deployment listではVersion `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%配信中。`git ls-remote`のmain SHAは`b3f3a7b75bc54d30ec468fc085922791f99e340b`。GitHubコネクターでmain上のWorker CIと手動移行Workflowをそれぞれ取得すると404であり、両Workflowは未公開・未実行。Environment保護は今回再確認できず、最後に確認できた2026-11-03 20:38 UTCの未作成記録を超える証拠はない。
- R2経由の本番Worker APIは要求ごとにDurable Object rate limiterを更新するため、現在の切替前バージョンへ内容照合GETを行っていない。API本文の本番比較、最新D1日次使用量、Queue backlog、GitHub Environment保護は未確認のまま。本番行クエリ・R2書き込み・デプロイ・Actions実行は行っていない。

## 2026-11-04 06:13 UTCの本番R2とローカルAPI照合

Wrangler 4.157.0でローカルWorkerを起動し、MARKET_DATAだけをremote R2 binding、RATE_LIMITERをlocal Durable Object、D1 bindingなし、Cron無効にした。実行したのは4つのGET経路だけで、本番Worker・本番DO・本番D1には接続していない。remote binding自体はR2への書き込み権限を持つため、読み取り専用と確認した経路だけを呼び出した。

R2から直接取得したカタログと候補はgeneration 2026-11-02-daa434e899b0493a9fdf346067419041。カタログは4,390件、コード重複0で、ローカルWorkerのsearch-catalog APIとコード集合・コード/名称/価格が全件一致した。空白月候補は3,203件、重複0、カタログ外コード0、各候補のsource catalog generationは全件一致し、income-advice-candidates APIと全JSON値が一致した。JSONを意味比較する際は整数/小数の表記差を正規化し、semantic SHA-256 1d8a08e080be46ccf0267ff834f06870c02f7bd1e6d963bf4e526c9583fcc303を得た。security-search?q=7203とlive-quotes?codes=7203は各HTTP 200で、価格は¥3,051。詳細な集計は C:/Codex/r2-d1-ownership-audit-20261104/r2-worker-api-parity-20261104.json にある。

この確認はローカルWorkerコードと現行R2の対応を示すもので、現在デプロイ中のWorkerを検証した証拠ではない。analysis current/history、定義・監査、財務・配当API、残る移行dry-runは引き続き未確認である。

D1日次残枠は未確定のまま。Workers Freeの場合はD1 GraphQL Analyticsのd1AnalyticsAdaptiveGroupsをUTC日付で絞り、全databaseIdのrowsReadを合算して確認する。Billing > Billable UsageはPay-as-you-go account向けであり、D1 Metricsの既定24時間窓とrows_read_24hは日次枠と同じではない。([D1 metrics](https://developers.cloudflare.com/d1/observability/metrics-analytics/)、[Billable Usage](https://developers.cloudflare.com/billing/manage/billable-usage/))

## 2026-11-04 06:29 UTCの移行Workflow table入力検査

- `table` は `d1-operational-audit`、`market-source-audit`、`legacy-market-archive` だけで選択可能にした。それ以外のフェーズでは既定値 `securities` 以外をCLIが拒否する。テーブル選択フェーズも既存の許可テーブル集合以外を拒否し、D1リクエスト前に終了する。workflowの説明と移行手順書を合わせた。
- CLIのphase/table検査は、tableを使わない4フェーズでの誤指定拒否と、許可された3種類のphase/table組合せを確認した。移行ツール51件、Worker 116件、`node --check src/index.js`、`py_compile`、両worktreeの`git diff --check`が成功した。テストはローカルで実行し、本番D1/R2、GitHub Actions、Workerには接続・書き込みしていない。

## 2026-11-04 GitHub Actions権限監査

- 公式GitHub資料では、Environment情報とdeployment branch policyのGET APIは `actions: read` を要求する。実Workflowはcheckout用の `contents: read`、これらの読み取りとrun/artifact確認用の `actions: read` を使い、Deployments REST APIは呼び出さない。
- 実装worktreeの手動移行Workflowにあった不要な `deployments: read` を削除し、仕様worktreeのWorkflowと同じ最小権限に揃えた。Environmentのrequired reviewerとmain限定ポリシーは維持する。Workflowは起動しておらず、本番移行・デプロイも未実施。

## 2026-11-04 07:03 UTCのD1メトリクス再確認

- Cloudflare Dashboardを再読込し、`diview-irbank` 個別DBの最後の24時間は概要カード7M、行読取チャート6.93M、銘柄検索SQLは1,886回・6.96M行と表示された。期間をUTC日次境界に合わせた00:00–07:03 UTC（09:00–16:03 JST）へ指定すると、同DBの行読取チャートは138.55kだった。
- 個別DBの期間指定メトリクスとローリング24時間値は、アカウント内10 DBの合計日次使用量・残枠を示さない。したがって日次5M枠の正確な残量は未確定。D1 SQL・書込みは行わず、追加の本番D1行読取は保留した。

## 2026-11-04 06:39 UTCのAndroidエミュレータ検証

- JDK 17.0.20.1を明示して同一worktreeの `testDebugUnitTest --rerun-tasks`、`assembleDebug`、`assembleDebugAndroidTest` を実行した。JVMテスト267/267件成功。`AssetDataRefreshTest` 20件、`NotificationDeliveryTest` 13件、`ReleaseNotificationPlatformTest` 2件はemulator-5556で合計35/35件成功した。
- 同じworktreeの `app-debug.apk` のSHA-256 `939B578CEF0D715702640CEB582D3016A75977E7495C5CC1D077CAC81C758B80` は、emulator-5556のインストール済み `base.apk` と一致し、`MainActivity` がforegroundになることを確認した。物理端末SCG01にはインストールしていない。

## 2026-11-04 06:47 UTCのR2-backed API読み戻し

- Wrangler 4.157.0のローカルWorkerをR2 remote binding、Durable Object local、D1 bindingなし、Cron無効で起動した。読み取り専用のGETを4件だけ実行し、`analysis-history/7203` と `definition-audit` はHTTP 200・`items=0`、`financial-data/7203` と `financial-data/revision` はHTTP 200を返した。財務応答は `annual=1`、`quarterly=1` を含む形だった。
- `/api/v1/analysis/{code}` は `buildAnalysisSnapshot` から `auditDefinition` を呼び、定義ハッシュごとにR2監査オブジェクトを条件付き作成するため、このリモートR2照合では呼び出していない。既存の `scheduled-analysis.test.mjs` はFakeR2上で現行入力による分析計算、R2履歴応答、D1 read count 0を確認するが、デプロイ済みWorkerとの一致は示さない。
- これらは作業中のローカルWorkerと現行R2の読み戻しであり、本番Workerの応答ではない。D1・本番DO・R2書き込み・GitHub Actions・デプロイは行っていない。

## 2026-11-04 07:32 UTCの継続検証

- 分離実装worktree `codex/r2-d1-ownership-20261104-final` でWorkerテスト116/116件、D1→R2移行CLIテスト51/51件、`node --check src/index.js`、`py_compile`、`git diff --check` が成功した。Worker/CLIテストはローカルfixtureを使い、本番移行・書込みは行っていない。
- Microsoft JDK 17.0.20.1を明示してAndroid JVMテスト267/267件、`assembleDebug`、`assembleDebugAndroidTest` を再実行し成功した。`emulator-5556` へこのworktreeのAPKをインストールし、価格更新・オフライン通知関連のinstrumentation 35/35件が成功した。APKと端末の `base.apk` SHA-256は `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` で一致。物理端末は変更していない。
- 最新の日次Rows Readとアカウント全DB合計・残枠を再確認するため既存ブラウザーのCloudflare画面を読み取り確認しようとしたが、Computer UseがブラウザーURLを十分な確度で特定できず停止した。画面操作は行っていない。正確な日次使用量・残枠は未確定のため、本番D1行クエリを再開していない。
- この継続検証ではGitHub Actions、本番D1/R2、Workerデプロイ、物理端末に対する操作は行っていない。

## 2026-11-04 07:42 UTCのD1使用量取得準備

- Dashboardと `rows_read_24h` の値が一致しないため、`scripts/d1_daily_usage.py` を追加した。Cloudflare公式の `d1AnalyticsAdaptiveGroups` をUTC日付・アカウントで問い合わせ、返されたdatabase IDごとの `rowsRead` を合算する。D1 SQL APIは呼ばず、トークンは `CLOUDFLARE_API_TOKEN` 環境変数だけから読み込む。Workers Freeの日次枠5,000,000を既定値とし、別枠は引数指定できる。
- 8件のGraphQLリクエスト/集計テストを含むPython検証59/59件、Worker 116/116件、`node --check`、`py_compile`、`git diff --check` が成功した。Worker CIにこのヘルパーのテストを加え、PyYAMLで両新Workflowの構文を読み取れることも確認した。workflow_dispatchのみの移行Workflow、CIの3トリガーを確認した。GitHub上での実行はしていない。
- 実行環境に `CLOUDFLARE_API_TOKEN` はなく、Account Analytics Read権限を持つ既存トークンによるGraphQL照会は未実施。DashboardはComputer Useが現在のブラウザーURLを特定できず停止したため、現在のアカウント日次使用量と残枠はなお未確定。本番D1行SQL、R2書き込み、Workerデプロイ、Actions、物理端末操作は行っていない。

## 2026-11-04 07:56 UTCの継続確認

- 実装worktreeでWorker 116/116件、D1→R2移行CLIとD1使用量ヘルパーの合計59/59件を再実行して確認した。`git diff --check`も共有・仕様・実装worktreeの各作業ツリーで成功した。
- 07:54 UTCの読み取り確認では`CLOUDFLARE_API_TOKEN`が環境になく、Account Analytics ReadのGraphQL照会はできなかった。GitHub mainの公開Workflowは9件で、新Worker CIと手動移行Workflowは未公開。Environment一覧に`IRBANK_API_KEY`はあるが、`d1-r2-migration`はない。既存Android release checkはAndroidソース変更を対象とし、JDK 17を指定する。
- WorkerソースのSQL参照は、DO初回利用時の有効な`api_request_log`カウンター引継ぎと、保持対象の問い合わせ・メール再試行処理に限られることを静的確認した。旧D1市場データimport/repairスクリプトは実行を拒否する。定期処理はR2分析と問い合わせメール再試行を続け、旧価格QueueはACKするが、Queue backlogの最新値と外部producerは未確認。
- この確認でD1行SQL、R2書込み、本番Worker呼出し、Actions実行、デプロイ、端末インストール、コミット、pushは行っていない。正確な日次使用量、Queue backlog、分析API本番本文との照合、移行Environment設定、production移行は未完了。

## 2026-11-04 08:02 UTCのR2/API空白月候補確認

- Wrangler 4.157.0のローカルWorkerをremote R2、local Durable Object、D1 bindingなし、Cron無効で起動し、`/api/v1/search-catalog` と `/api/v1/income-advice-candidates` のGETを実行した。両方HTTP 200でgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`。カタログは4,390件・重複0、候補は3,203件・重複0・カタログ外0・`source_catalog_generation`不一致0だった。候補生成コードはカタログgenerationを各候補に記録し、APIは現行R2候補オブジェクトを返す。
- API応答bodyのSHA-256は検索カタログ `64827be7a21a6c527d37f5b366e1f95bfa674c64a8b7f106fe97f7823f83338e`、候補 `8dda1c1f679a0dd547357230550e3013fef2e2af6d090abda71659876ea0e7d2`。候補オブジェクトの生成時刻は `2026-11-04T04:41:21+09:00`、カタログの市場データ日は `2026-11-02`。
- これはローカルWorkerから現行R2への読み取り結果で、本番Workerの応答・本番分析API・移行完了の証拠ではない。D1行SQL、R2書込み、本番Worker呼出し、Actions実行、デプロイ、端末インストールは行っていない。

## 2026-11-04 08:10 UTCのR2実体/API構造照合

- Wrangler 4.157.0でremote R2からcatalogとincome-adviceのcurrent objectを直接GETした。catalogは4,390件・重複0、generation `2026-11-02-daa434e899b0493a9fdf346067419041`、market data date `2026-11-02`、SHA-256 `5830777b686024bc313eed59916fe4db4cbd26b834eff9486f2c8c921e367a1e`。income-adviceは3,203件・重複0・catalog外0・source generation不一致0、同generation、generated_at `2026-11-04T04:41:21.044036+09:00`、SHA-256 `964abdc8458e6ac2757274b36db6ce691f1dde86b9931f0f423397a5ccc8d1ab`。
- local Workerの`/api/v1/income-advice-candidates`と直接取得したcurrent objectを全フィールド・全配列要素で比較し、構造差0件、両方3,203件、generation一致を確認した。Workerはremote R2、local Durable Object、D1なし、Cron無効で動作させた。
- これは現在のR2実体とローカルWorker APIの比較であり、本番Worker API・D1移行完了の証拠ではない。R2書込み、D1 SQL、本番DO利用、本番Worker呼出し、Actions、デプロイは行っていない。

## 2026-11-04 08:12 UTCの端末向け価格API確認

- D1 bindingなし・Cron無効・local Durable ObjectのWorkerから`GET /api/v1/live-quotes?codes=7203`を実行し、HTTP 200、run ID `2026-11-02-daa434e899b0493a9fdf346067419041`、market data dateとquote date `2026-11-02`、`complete=true`、1件の価格ありを確認した。価格値は記録しない。
- これはAndroidの対象銘柄価格更新が呼び出すR2-backed endpointの読み取り確認であり、本番Workerや端末への取得・保存を証明しない。D1行SQL、R2書込み、本番Worker/DO、Actions、デプロイ、端末インストールは行っていない。

## 2026-11-04 08:24 UTCのD1読取予算ゲート

- 手動移行Workflowに、Account Analytics Read専用secretを使うアカウント全DB・UTC日次Rows Read事前確認を追加した。`read_reserve_rows`は既定100,000で、`max_rows + read_reserve_rows`分の残枠がなければ、またはGraphQL確認が失敗した場合はD1 SQLより前に停止する。D1読取tokenとR2 S3資格情報は別step・別secretのまま。
- `d1_daily_usage.py`は`--minimum-rows-remaining`を追加し、上限超過または指定残枠不足で非0終了する。Worker 116/116、移行・使用量Python 62/62、`node --check`、Python構文検査、Workflow YAML構文と事前確認step順序の検査が成功した。GitHub mainに新Workflowは未公開で、Actionsは未実行。
- 現環境にAccount Analytics Read tokenはなく、アカウントの実使用量・残枠と移行Environmentは未確認。D1行SQL、R2書込み、Actions実行、本番デプロイは行っていない。

## 2026-11-04 08:29 UTCの予算ゲート再検証

- 移行・使用量Pythonテスト63/63件、Workerテスト116/116件が成功した。追加テストは、移行Workflowのアカウント残量確認がD1・R2の移行フェーズより先に置かれ、Analytics Read token、reserve入力、`--minimum-rows-remaining`を参照することを確認する。
- テストはローカルfixtureを使用した。アカウントの実使用量確認、本番D1/R2アクセス、GitHub Actions実行、デプロイは行っていない。

## 2026-11-04 08:43 UTCのGitHub Actions確認

- GitHub読み取りAPIで`main` SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、workflow一覧11件を確認した。既存のyfinance、IRBANK、権利日workflowは公開されている。追加した`cloudflare-worker-ci.yml`と`d1-r2-migration.yml`はmain上で404のため未公開。
- `main` branch APIは`protected=false`を返した。Environment一覧APIはこのコネクターでは取得できず、`d1-r2-migration`の現在の承認者・ブランチ制限・secret設定は未確認。Actions実行、設定変更、データ操作はしていない。

## 2026-11-04 09:06 UTC GitHub Actions公開状態の再確認

- GitHub APIのmainブランチ情報はSHA b3f3a7b75bc54d30ec468fc085922791f99e340b、protected=false。mainのworkflow一覧は9件で、追加したcloudflare-worker-ci.ymlとd1-r2-migration.ymlは内容取得が404となり未公開。ローカルのWorker CIはdata/irbank/cloudflare/**変更時のpush/PRと手動起動、移行workflowはworkflow_dispatchのみでmainに限定される。移行jobは保護Environmentの独立reviewer・self-review禁止・main限定を事前確認し、D1/Analytics/R2資格情報を分離して予算確認後に移行フェーズへ進む。
- workflowは未公開・未実行。Environmentの現在のreviewer、branch policy、secrets設定は未確認であり、mainのbranch protectionも有効でない。本番移行・設定変更は行っていない。
## 2026-11-04 09:08 UTC Durable Objectレート制限の引継ぎ確認

- 親のmain実装と作業中Workerを比較し、バケットキーrl:{route}:{SHA-256(clientIdまたはIP)}、public上限300件/60秒、J-Quants日足APIの5件上限、feedbackの5件上限を確認した。現行DOは同じキー・窓幅・拒否応答を使う。RATE_LIMITER binding欠落、DO応答失敗、初回D1 seed失敗はリクエストを失敗させ、制限を解除しない。
- D1 api_request_logは各DOの初回利用時に対象行を一度SELECTし、有効窓のカウンターだけDO SQLiteへ引き継ぐ。通常リクエストではD1更新を行わない。既存テストには初回引継ぎ、D1一時失敗後の再試行、並行初回リクエスト、窓境界、再起動後の遅延リセット、および旧UPSERT等を止めるD1フェンス検証がある。コードは未デプロイで、DO実利用量とプランは未確認。

## 2026-11-04 09:34 UTC ローカル検証の継続

- Androidの通常価格更新は保有中またはウォッチリスト中の日本株を抽出し、最大50コード単位で同じR2カタログgenerationに固定して取得する。通知用価格キャッシュには対象コードだけを保存する。全カタログ取得はIRBANK利回り契約の旧版更新時に限られ、通常更新の通信対象とは分離されている。
- 実装worktree `codex/r2-d1-ownership-20261104-final` でWorker 116/116件、D1→R2移行CLIと日次使用量ヘルパー63/63件を再実行して成功。D1参照ソースの再棚卸しでは残るWorker SQLはDurable Object初回の`api_request_log` SELECTと問い合わせ・メールoutbox/保持処理で、市場データ目的のD1参照は見つからない。
- JDK 17.0.20.1でAndroid JVM 267/267件、Debug/AndroidTest APK build成功、emulator-5556 instrumentation 136/136件成功。生成APKとインストール済み`base.apk`のSHA-256は`939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80`で一致。物理端末にはインストールしていない。
- GitHub `main`は引き続き`b3f3a7b75bc54d30ec468fc085922791f99e340b`。Worker CIと手動移行Workflowはmain上で404。ローカルworkflowの公開・実行、本番D1/R2操作、Workerデプロイ、commit/pushはなし。
- 現プロセス環境には`CLOUDFLARE_ACCOUNT_ID`のみ確認され、Account Analytics tokenは見つからない。Edgeの現在URLも安全に特定できずComputer Useが停止したため、正確なD1日次Rows Readと残枠は未確認。追加の本番D1行SQLは実行していない。

## 2026-11-04 09:40 UTC D1書込経路とGitHub再確認

- GitHub branch APIは`main`のSHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`と`protected=false`を返した。ローカルWorker CI・移行Workflowはmain上で404、branch-protection詳細APIはconnector permissionで403。Environment設定の読み取りも未確認。
- コード検索でD1実行コマンドを再確認した。`dev-local.ps1`のwrangler実行は`--local`のみ、`import-d1.ps1`と`apply-financial-repair.ps1`は冒頭で停止し、定義seedは`.sql.disabled`に移した。本番の移行経路は手動migration CLI/Workflowのみで、まだ実行していない。
- mainブランチ保護、migration Environmentのreviewer・branch policy・secrets、D1日次残量は未確認のまま。本番設定変更や移行実行は行っていない。

## 2026-11-04 09:42 UTC 移行読取予算のコード照合

- 手動移行WorkflowはD1 SQLフェーズより先にアカウント全体のUTC日次Rows Readを集計し、残枠が`max_rows + read_reserve_rows`以上の場合だけ続行する。移行CLIはphase全体でCloudflare `meta.rows_read`を累積し、次のquery前に残量を制限する。複数queryを使うフェーズも個別上限を分けず同じ累積budgetを使い、D1 JOINを読む市場監査は1出力あたり最大2行を見込む。
- Worker 116件と移行/usage Python 63件のローカルテストが成功。予算ゲート追加後の過剰読取・budget枯渇でのquery継続がないことをfixtureで確認した。実アカウントの残枠と本番query可否は未確認。

## 2026-11-04 09:57 UTC CLI単体のD1使用量ゲート修正

- 09:42の確認はWorkflowの事前ゲートとCLI内のphase読取budgetを対象としていたが、CLI単体の起動にはアカウント全体の残量ゲートがなく、直接D1へ進める経路が残っていた。実装worktreeのCLIにAccount AnalyticsのUTC日次Rows Read確認を追加し、D1 client生成前に`max_rows + read_reserve_rows`の残量を要求する。確認失敗・token欠落・残量不足はfail-closedする。
- Analytics tokenは`CLOUDFLARE_API_TOKEN`、D1読取tokenは`CLOUDFLARE_MIGRATION_READ_TOKEN`として分離した。Workflowは両方をCLIへ渡し、CLI自身の直前再確認結果を移行レポートへ追加する。手順書も直接CLI実行時の条件を明記した。
- 移行・usage Python fixtureテスト66/66件が成功。token欠落と残量不足でD1 clientを生成しないこと、Analytics確認がD1 clientより先に別credentialで呼ばれること、資格情報がレポートに含まれないことを確認した。実Account Analytics照会、本番D1/R2操作、GitHub Actions実行は行っていない。

## 2026-11-04 10:09 UTC 事前確認修正の再検証

- 移行・usage Python 66/66件とWorker 116/116件が成功し、移行CLIはリポジトリ外のcurrent directoryから`--help`で起動できた。`git diff --check`と対象ファイル末尾空白検査も成功。Androidソースは今回変更していないためJDK 17 build/installは再実行していない。
- GitHub REST read-only確認でmain SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`、Worker CIと手動移行Workflowは404。Workflowは未公開・未実行で、設定変更も行っていない。
- 現プロセスに`CLOUDFLARE_API_TOKEN`と移行専用read tokenはない。Wrangler CLIはOAuth loginを報告したが、Account Analytics GraphQLをそのOAuth権限で照会していない。日次使用量・残枠は未確認で、本番D1 SQL・R2書込みはなし。

## 2026-11-04 10:17 UTC 稼働状態とデプロイCLI

- Workerソースを再検索した結果、D1 SQLはDurable Object初回の有効な`api_request_log`カウンター引継ぎSELECTと、保持対象の問い合わせ・メールoutbox処理だけだった。市場データ目的のD1 SQLは見つからない。
- Wrangler読み取りで本番Workerは旧Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%配信中。`wrangler d1 info`は`diview-irbank`単体で23テーブル、`rows_read_24h=165045`を示した。これはローリング24時間値であり、アカウント全DBの日次残量を示さない。行SQLは実行していない。
- PATH上のWrangler 4.136.1は過去のlocal workerd検証でcompatibility date制約が確認され、4.157.0でローカルAPI読み戻しを実施した。READMEのD1 execute・versions upload/deploy・Worker deployコマンドを4.157.0に固定した。固定版による本番デプロイは未実施。


## 2026-11-04 10:40 UTC ローカル回帰検証

- JDK 17.0.20.1でAndroidのDebug APKとAndroidTest APKをビルドし、`:app:testDebugUnitTest --rerun-tasks` を実行して267件すべて成功した。`emulator-5556` のinstrumentationは136件成功し、インストール済み `base.apk` のSHA-256 `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` は同じworktreeの生成APKと一致した。物理端末は変更していない。
- Workerテスト116件、移行テスト54件、日次使用量テスト12件が成功した。GitHub CIと同じPython unittestのパス形式、`node --check src/index.js`、移行スクリプトの`py_compile`も通過した。
- GitHub REST read-only確認でmainはSHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`。新Worker CIと移行Workflowはいずれもmainで404。ローカルの移行Workflowと静的テストにはmain保護確認を追加し、未保護なら失敗することを確認した。
- Actionsの起動、本番D1行SQL、R2書き込み、Workerデプロイ、commit/pushは行っていない。アカウント全体の日次使用量、Environment設定、残りの本番監査と移行フェーズ、Cron/Queue切替条件は未完了。


## 2026-11-04 10:51 UTC R2/API再照合とhealth状態

- Wrangler 4.157.0のローカルWorkerを使い、R2バインディングのみremote、D1なし、Durable Objects/KVはlocal、Cron無効で読み戻した。`income-advice-candidates` はHTTP 200、generation `2026-11-02-daa434e899b0493a9fdf346067419041`、3,203件を返し、7203の候補も同じsource generationを参照した。`live-quotes` と`security-search` の7203要求もHTTP 200で同じgeneration、株価¥3,051、quote_date 2026-11-02、年間配当¥100を返した。
- 同じR2読み戻しで`analysis-history/7203`と`definition-audit`は各0件、R2定義とlocal KV fallbackを読む`stock-analysis-definitions`は`{}`だった。これは全分析履歴が存在しないことを証明するものではないが、少なくとも7203のR2履歴と定義R2公開は切替前に移行・照合が必要である。
- healthが旧market snapshotだけを見ていたため、旧snapshot 25.1日経過・quote 0件と報告する一方、v2カタログは4,390件で全件価格ありという不整合を確認した。healthにv2 generation、market date、生成日時、件数、旧snapshot年齢を分けて追加し、v2の鮮度を優先表示するよう修正した。Workerテスト117/117件が成功し、remote R2読み戻し後のhealthはv2 generationと4,390件を報告した。
- 読み戻しはlocal Worker経由で本番R2をGETしたもので、デプロイ済みWorkerの応答とは区別する。R2への書き込み、D1行SQL、Workerデプロイは行っていない。


## 2026-11-04 10:57 UTC 定義KV読み戻し

- Wrangler 4.157.0で本番KVの`stock-analysis-definitions:v1`、`watchlist-analysis-definitions:v1`、`portfolio-analysis-definitions:v1`を読み取り、各値をチェックイン済み`data/irbank/cloudflare/kv/`のJSONと意味的SHA-256で比較した。3件すべて一致し、ハッシュは順に`938711afd1b1ebadc7c5b97776e29f1c3d6a8af9695d4208cc08f49845e448e4`、`171df41e25890c84a7e87643eadfcbf4040b3b2336c2648637ba4f886c5998e0`、`2ea3a7ae5c1afab526413ab6afe2e9f43187a6ff571366275d2635a9777b51ad`だった。
- 移行CLIのdefinitions phaseは本番KVを先に読み、その後にD1文書・表、最後にローカルseedを使う。runbookのD1 ReadとWorkers KV Storage Readの資格要件と実装は一致する。R2 versioned definitionが未公開である点は残り、R2への移行・読み戻しは必要。
- KVアクセスは読み取り専用。D1行SQL、R2書き込み、GitHub Actions、Workerデプロイは行っていない。

## 2026-11-04 11:16 UTC 定義ソース優先順位の回帰確認

- 移行CLIのdefinitions phaseについて、チェックインseedと一致する現行KV値が古い`app_definition_documents`値より優先されることをFakeReader/FakeR2テストで確認した。D1 queryは0件で、R2 versioned objectとpointerにはKVの値が入る。CIと同じローカル検証はWorker 117/117件、移行CLI 55/55件、日次使用量12/12件で成功。
- GitHub読み取りAPIの再確認ではmain SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`、追加Worker CI/手動移行Workflowは404。ローカルpreflightは`actions: read`を宣言し、[Environment API](https://docs.github.com/en/rest/deployments/environments)で求められるActions read権限を静的テストで確認した。実Environment設定はconnectorがAPI pathを拒否したため未確認。Workflowは未公開・未実行で、本番設定変更もしていない。
- R2定義の実体・履歴/監査の移行dry-run・正確なD1日次残量・Environment保護・Cron/Queue切替条件は未確認であり、本番移行準備の未完了事項として残る。

## 2026-11-04 11:22 UTC 現行R2とWorker APIの再読み戻し

- Wrangler 4.157.0のlocal Worker設定で`MARKET_DATA`だけをremote R2に接続し、D1 bindingを含めず、Durable Object/KVをlocal、Cronを空にしてGET APIを確認した。`search-catalog`と`income-advice-candidates`はHTTP 200でgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`、4,390件/3,203件。候補項目のsource catalog generationは全件一致した。`live-quotes`と`security-search`の7203要求も200で、価格日は2026-11-02だった。healthのv2 catalogは同generationで4,390銘柄、全銘柄に価格があると報告した。
- 同じ読戻しで`analysis-history/7203`と`definition-audit`は各0件、`stock-analysis-definitions`は空だった。これらはR2未移行の切替条件であり、analysis GETは監査オブジェクトを書き込むため呼び出していない。
- これはlocal Workerからのremote R2 GETであり、本番Worker/DOの動作確認ではない。D1 SQL、R2書込み、Actions実行、Workerデプロイ、実機インストールは行っていない。local Workerは読戻し後に停止した。

## 2026-11-04 11:44 UTC Cloudflare使用量とDurable Objects容量

- Cloudflare D1のアカウント使用量パネルを更新し、November 4の日次値を確認した。データベース10/10、読み取り159.29K/5M、書き込み2.43K/100K、総ストレージ105.03MB/5GB。読み取り・書き込みの説明はUTC 00:00からの値。表示精度で残る読み取り枠は約4.84M。これは確認時点のDashboardスナップショットであり、移行実行直前に再確認する。Account Analytics GraphQL tokenは実行環境になく、CLIからの独立照合は未実施。
- 同アカウントのWorkers Free日次リクエストは1,221/100,000。`diview-irbank`の詳細画面では直近24時間の呼び出し約1.24K、エラー554、CPU時間2msを表示し、本番Version `4981c1e0` が100%配信中だった。現在のProduction bindingにD1、R2、KV、QueueはあるがRATE_LIMITER/DO bindingはなく、DO使用量を本番で測ったものではない。554件すべてがD1上限によるエラーと断定しない。
- Cloudflareの公式料金表はWorkers FreeのDOをSQLite backendに限定し、DO request 100,000/日・compute 13,000 GB-s/日、SQLite行読取5M/日・書込100K/日・保存5GBを示す。超過した種類の操作は失敗し、日次枠はUTC 00:00にリセットされる ([DO pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/)、[Workers limits](https://developers.cloudflare.com/workers/platform/limits/))。
- 直近24時間のWorker呼び出し約1.24Kを切替後のrate-limit DO呼び出し数の保守的な初期見積りとする。現コードは要求ごとにDOで1回の読取・1回の書込を行うため、現在の水準ならDO request約1.24%/日、SQL書込約1.24%/日、SQL読取約0.025%/日のFree枠に相当する。ただしProduction DOは未稼働で、ピーク・他DO namespaceとのアカウント共有・compute durationは未測定。切替後に実測し、無料枠超過時にエラーとなる条件を監視する。

## 2026-11-04 12:08 UTC 手動移行Workflowの誤成功表示防止

- 非`main`ブランチから手動起動すると、`preflight`と`migrate`のjob-level条件により全jobがskipされ、workflow run自体は成功のように見える経路があった。
- `preflight`を全refで実行し、GitHub API照会より先に`GITHUB_REF`が`refs/heads/main`であることを検査して失敗させる。`migrate`は引き続きmain限定で、preflight成功を必須とする。
- 標準ライブラリだけを使う3件のworkflow policy testをCIへ追加し、非main起動のfail-closed条件を固定した。ローカルテストは成功。workflow YAML lintとGitHub上の実workflow実行は未確認であり、本番操作はしていない。

## 2026-11-04 12:18 UTC 定義のWorker実行時参照をR2に限定

- `appDefinitionItems`と`appDefinitionDocument`からWorker実行時のKVフォールバックを削除し、定義監査のsourceも`r2`にした。移行CLIは既存KV値をD1より優先する移行入力として引き続き読む。
- Workerテスト118/118件、移行・日次使用量・workflow policyテスト70/70件、`node --check`、Python compile、`git diff --check`が成功。R2ポインター欠落時にKVを読まない契約をテストした。
- 2026-11-04 11:22 UTCのremote R2読み戻しではstock-analysis定義が空だった。今回のWorkerは未デプロイであり、定義オブジェクトの移行・全件読み戻し・API照合が終わるまで本番へ切り替えない。

## 2026-11-04 12:30 UTC healthの定義欠落検出

- `/api/v1/health`も必須のstock/watch分析定義をR2から読み、ポインターまたはversion objectが欠けていれば`503 definitions_unavailable`を返す。空オブジェクトで欠落を隠さず、KV/D1へフォールバックしない。
- 定義欠落時のhealth応答とKV/D1非参照をWorker回帰テストで検証する。R2定義の実体移行と本番読み戻しは引き続きデプロイ前提条件。

## 2026-11-04 12:41 UTC Queueと設定KVの読み取り確認

- Wrangler 4.157.0の読み取り専用`queues info`で、`diview-market-prices`はWorker登録producer/consumer各1件、DLQはproducer 0件・consumer 1件を確認した。これはbacklog件数を示さず、実際の未処理数・稼働中Version・外部送信元は未確認。現行作業ツリーにはQueue送信呼び出しがない。
- `APP_DEFINITIONS`の`backend-config:v1`読み取りはCloudflare API 401で失敗した。チェックインseedの値は確認済みだが、本番KV/R2の実値とは扱わない。backend-configのR2移行・読み戻しとrate limit設定の本番一致は未確認。

## 2026-11-04 13:00 UTC 本番WorkerとR2定義pointerの確認

Wrangler 4.136.1の読み取り専用`deployments status`と`versions view`で、production deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`がVersion `4981c1e0-e337-4392-9f5b-909b52c5bc0c`を100%配信し、作成時刻は2026-11-03 12:26:30 UTCであることを確認した。Version metadataのbinding型一覧には`DB`(D1)、`MARKET_DATA`(R2)、`MARKET_PRICE_QUEUE`(Queue)、`APP_DEFINITIONS`(KV)等があり、`RATE_LIMITER`(Durable Objects)はない。

続けてWranglerのremote R2 GETで、Workerが使う6定義（`backend-config:v1`、`diagnosis-definitions:v1`、`valuation-scenarios:v1`、`stock-analysis-definitions:v1`、`watchlist-analysis-definitions:v1`、`portfolio-analysis-definitions:v1`）の各`definitions/v1/{URL-encoded-key}/current.json`を個別に照会し、すべて404「The specified key does not exist」を確認した。D1 query・R2 writeは行っていない。現WorkerコードはR2 pointerがない定義を利用不可として返すため、6定義を移行して全件読み戻し・API/health照合するまで本番切替不可。`backend-config`の実値も未確認なので、ローカル既定値のみでrate-limit parityを主張しない。binding名と型だけを確認し、secret値は出力・記録していない。

## 2026-11-04 13:07 UTC 現行R2に対するローカルWorker API確認

Wrangler 4.157.0で、D1 bindingなし・remote R2のみ・local Durable Objects/KV・Cron無効の読み取り用設定を使ってWorkerを起動し、R2読取のみのGET経路を確認した。healthとstock/watch/portfolio定義APIはいずれも503 `definitions_unavailable`、`/api/v1/definition-audit`は200で`items: []`、`/api/v1/analysis-history/7203`は200で`items: []`だった。analysis APIは呼び出していないため、定義監査オブジェクトを書いていない。結果は現ローカルコードと本番R2の組み合わせであり、本番Worker/Durable Objectsの応答や7203以外の全履歴を証明しない。D1 query・R2 writeなし。

## 2026-11-04 13:10 UTC インカム空白月候補R2/API再読み戻し

同じD1なし・remote R2のローカルWorkerから`/api/v1/income-advice-candidates`をGETし、HTTP 200、generation `2026-11-02-daa434e899b0493a9fdf346067419041`、生成時刻`2026-11-03T19:41:21.044036+00:00`、3,203件を確認した。全3,203件の`source_catalog_generation`が同じgenerationを参照し、7203候補も存在した。これは現行R2成果物とローカルWorker APIの読み戻しであり、Android端末キャッシュ・順位付け・表示や本番Workerの応答を証明しない。D1 query・R2 writeなし。

## 2026-11-04 13:17 UTC GitHub Actions Workflow静的検証

公式`rhysd/actionlint` v1.7.12 Windows AMD64リリースのSHA-256を公開checksumsと照合し、ローカルの2つのWorkflowにactionlintを実行した。`cloudflare-worker-ci.yml`と`d1-r2-migration.yml`の両方がエラーなしで通過した。これはローカルWorkflowファイルの静的検証であり、GitHub mainへの公開、Actions実行、branch protection、`d1-r2-migration` Environment設定を確認したものではない。

## 2026-11-04 13:29 UTC D1 Account Analytics使用量確認

`scripts/d1_daily_usage.py`でAccount Analytics GraphQLを読み取り、2026-11-04 UTCの日次Rows Read合計167,531行、Rows Written 2,783行、使用のあるdatabase 3件、5,000,000行枠の残り4,832,469行を確認した（13:28:58 UTC時点）。この照会はD1 SQLを発行していない。移行CLIに必要な別の`CLOUDFLARE_MIGRATION_READ_TOKEN`は環境になく、Account Analytics専用トークンをD1読取に流用しない。analysis-historyのdry-runはcheckpoint cursor 1,500から再開する前に、D1 read-only資格情報と再度の予算確認が必要。

## 2026-11-04 13:35 UTC インカム空白月候補のAndroid回帰テスト

`CloudflareMarketRepositoryTest`に候補APIのHTTP取得・世代・候補値と保存JSONからの再解析、`IncomeAdviceRankingTest`に空白月がない場合、保有済み銘柄の除外、月の検証・重複排除、未充足月を優先する順位付けと最大3件を検証するテストを追加した。JDK 17.0.20.1でDebug JVMテスト271/271件成功。これで候補APIのローカル解析・順位付けを検証したが、Android実機上のSharedPreferences復元とWatchlist画面表示は未確認。

## 2026-11-04 13:55 UTC D1使用量とオフライン候補表示の確認

設定後の`CLOUDFLARE_API_TOKEN`を使い、Account Analytics GraphQLから2026-11-04 UTCの日次使用量を読み取った。13:55:06 UTC時点でRows Read 167,539、Rows Written 2,784、使用のあるdatabase 3件、5,000,000行枠の残り4,832,461行だった。これはUTC 00:00以降の当日分であり、前日の上限超過通知とは別の集計日である。D1 SQLは実行していない。`CLOUDFLARE_MIGRATION_READ_TOKEN`は別途必要で、未設定のため移行CLIのD1 dry-runは引き続き未実施。

候補APIキャッシュのオフラインUI instrumentation testを追加し、JDK 17.0.20.1でDebug APKとandroidTest APKをビルドした。`emulator-5554`だけにAPKをインストールし、SharedPreferencesに保存した候補がWatchlistの受取月別試算シートでコード・名称・配当月とともに表示され、候補表示に伴うネットワーク要求が0件であることを確認した（1/1 test）。生成Debug APKとエミュレータのinstalled `base.apk`のSHA-256は一致した。物理端末、本番Worker、production D1/R2、GitHub Actionsは変更・実行していない。

## 2026-11-04 14:08 UTC Queue backlog権限確認

Queue `diview-market-prices` と `diview-market-prices-dlq` のpoint-in-time REST metricsを、既存Account Analytics tokenで読み取ろうとしたところ、両方HTTP 403だった。Cloudflare REST APIはQueues ReadまたはWorkers Scripts Readを要求するため、runbookの環境変数を専用の`CLOUDFLARE_QUEUE_READ_TOKEN`に分けた。Account Analytics GraphQL backlog datasetは2026-11-03 14:00–2026-11-04 14:01 UTCに両Queueともサンプル0件だったが、これは現在のbacklogが0である証拠ではない。許可スコープのtokenで実測するまでQueueと関連D1状態を保持する。

同時刻の環境確認では`CLOUDFLARE_MIGRATION_READ_TOKEN`はProcess/User/Machine環境のいずれにも見えず、D1 row SQLやmigration dry-runは実行していない。設定後にAccount AnalyticsでUTC日次残枠を再確認し、小さいdry-runから再開する。

## 2026-11-05 JST（2026-11-04 16:00–16:39 UTC）定義読取と金融期間監査

Workers KV Storage Read権限を追加した移行用read-only tokenで、6定義の読み取りとdefinitions全件dry-runが成功した。12個のversion/current R2 objectを計画し、D1 query/rows 0、conflicts 0、R2 writes 0。既存のR2定義objectはなく、seed・readback・API parityは未完了。report SHA-256はcf1c9762699d023e2c05cae718fc4b497f7f08044185ee927fecd4dd933c3526。

financial_periodsの出所監査では、D1にfield_unitsがないため、確認済みIRBANK契約（金額million_jpy、EPS jpy_per_share、計算済み自己資本比率percent）を使い、R2の行別単位へ正規化して比較した。0.5 source unit以内を丸め差として扱い、checkpoint comparison versionを2にした。全49,592行中49,571一致、4差分、17期間未存在、42項目をmillion_jpyからjpyへ換算、単位情報不足0。累計D1 rows_read 99,184、R2 source lookup 3,062、R2 write 0。レポートSHA-256はb2bd01c0982d86a7375d3a1f574ad7929a2c29beb757f69444b4d1d2fb622f3f。

4差分は1301の3期間（FY2017/03、FY2023/03、FY2024/03）とpartial contamination fingerprintに該当する2708 FY2014/03。R2値と公式資料が整合する1301の行をD1で上書き・R2へ再投入しない。17未存在期間は1301のFY2025/03・FY2026/03と、出所未解決の2708の10月期15件。値の正本・保持・バックアップ方針を確認するまで、これらをR2へseedせずD1から削除しない。

2026-11-04 16:39:43 UTCのAccount Analyticsは全3 DB合計419,056 rows read、残り4,580,944。100,000行reserveを維持し、追加D1 SQL直前に再取得する。移行・監査は読み取り専用で、本番R2書込み、Worker deploy、production API切替は未実施。


## 2026-11-05 JST（2026-11-04 17:06–17:10 UTC）最新の読み取り専用確認

Workers KV Storage Read権限を追加した移行用トークンで、`definitions` dry-runを再実行した。6定義からversion/currentの12 R2 objectを計画し、D1 query 0・D1 rows_read 0・conflicts 0・R2 writes 0で完了した。17:10:43 UTCのAccount Analytics preflightは全3 DBで419,064 rows read、残り4,580,936行。レポートは `C:\Codex\r2-d1-ownership-audit-20261105\definitions-kv-read-dry-run-20261104T171042Z.json`、外部ファイルSHA-256 `E40C489DC26A09FD48974D9EE0651AB32B1B0A55DB2583C1978D9E2C34972C18`。このdry-runはR2へ書き込まず、定義objectはまだseedされていない。

2026-11-04 17:06:54 UTCに保存した読み取りレポートは、ローカルWorkerコードをWrangler 4.157.0で起動し、本番R2 bindingだけをremoteにして照合した。source branchは `codex/r2-d1-ownership-20261104-final`、HEADは `b3f3a7b75bc54d30ec468fc085922791f99e340b`。個別GET時刻は記録されていないため、時刻はレポート取得時刻を示す。市場カタログはHTTP 200・4,390件、空白月候補はHTTP 200・3,203件で、どちらもgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`。7203の価格APIはHTTP 200・complete、履歴と定義監査APIはHTTP 200で各0件だった。healthとstock/watchlist/portfolioの各analysis definitions APIはHTTP 503 `definitions_unavailable`。分析APIは定義監査オブジェクトへ書き込むため対象外とした。これは本番Workerの応答ではなく、ローカルWorkerからのR2読み戻しである。レポートSHA-256は `b29293643e147eddd361211c2ba0ceb1091b06cef2999bd7350b93deeacf6870`、保存先は `C:\Codex\r2-d1-ownership-audit-20261105\local-worker-remote-r2-readback-20261105.json`。

同じ実装worktreeではWorkerテスト119/119件、Python migration/usage/workflow tests 78/78件、JDK 17 JVM test 271/271件、emulator-5554の通知instrumentation 13/13件が成功済み。Debug APK SHA-256は `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80`。エミュレータではAndroidTest APKのみを入れ、アプリデータを使わない隔離fixtureで実行した。実機は未使用。GitHub Actions公開・実行、本番R2書込み、Worker deploy、production API切替、commit/pushはいずれも未実施。

未完了条件は、12定義objectのseedとAPI parity、Queue backlogと古い状態の保持判断、GitHub main/Environment保護とWorkflow公開、本番Durable Objectsのcounter handoff、financial_periodsの4差分・17不足期間の正本性確認である。これらの確認・承認が終わるまで本番データの書込み・削除と本番切替を行わない。

## 2026-11-05 JST（2026-11-04 17:30–17:38 UTC）DO row cleanupと切替状態

Durable Objectのrate-limitカウンターは、各window期限にalarmを予約する。alarmはSQLite rowを再読込し、有効windowなら再予約し、期限切れなら削除する。alarmが遅れても通常リクエスト時の期限判定でカウンターは正しくresetされる。rate-limit/handoff 8/8、Worker 120/120、Python migration/usage/workflow 78/78件が成功した。これはローカルコードとfixtureの検証であり、本番DO bindingの動作確認ではない。

17:38:02 UTCの読み取り監査では、本番Workerは旧Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`を100%配信し、D1とR2はあるが`RATE_LIMITER` Durable Object bindingはない。GitHub `main`は保護されておらず、`d1-r2-migration` Environmentと新Workflowは未公開。Queue Read tokenがなくbacklogは不明。12個の定義objectのseed/API parity、金融期間4差分・17不足の出所も未解決。したがって、DOの本番handoffを含むcutoverは未実施であり、最終レビュー・承認までR2書込み、deploy、Actions dispatchは保留する。監査reportは `C:\Codex\r2-d1-ownership-audit-20261105\runtime-cutover-gates-20261104T173511Z.json`（SHA-256 `6A97C9EB3956C43679AEFC195945A814E46DD014110F1A91AC5A54948E61B231`）。

## 2026-11-05 JST（2026-11-04 17:48–17:55 UTC）DO初回D1読取の一回性

期限切れcounter rowをAlarmで削除した後、Durable Objectが再生成されると、rowの不存在だけを見てD1 seedを再読取する回帰を発見して修正した。DO SQLiteに`rate_limit_legacy_seed` markerを追加し、初回D1照会結果とcounter rowを同一SQLite transactionで保存する。Alarmはmarkerを保持してcounter rowだけ削除し、markerのない旧actorを処理するときは削除前にmarkerを作る。これでactive windowのcounter引継ぎとwindow間のD1再読取防止を両立する。rate-limit/handoff 8/8、Worker全体120/120、`node --check src/index.js`成功。

このmarkerはDO bucketごとに1行残るため、累積markerとAlarm実行を含む本番DO保存量・リクエスト利用量は未測定。production DO binding/handoff前にWorkers planと利用見込みを確認する。この修正はローカルであり、本番には反映していない。

## 2026-11-05 JST（2026-11-04 20:51 UTC）GitHub remote state再確認

GitHub読み取りAPIで`masacgt/Diview`の`main`が`b3f3a7b75bc54d30ec468fc085922791f99e340b`を指し、実装worktreeのHEADと一致することを確認した。branch metadataは`protected=false`、repository rulesets endpointは空配列だった。一方、branch-protection詳細endpointは403を返したためrequired checks等の詳細は取得できていない。

Workflow一覧は9件で、`.github/workflows/cloudflare-worker-ci.yml`と`.github/workflows/d1-r2-migration.yml`はGitHub上にない。GitHub連携のallowlistがEnvironment一覧endpointを拒否し、ローカルに`gh` CLIもない。ブラウザはURLを確実に検証できなかったため設定画面の確認を停止した。よってEnvironmentの存在・reviewer・branch policy・secret設定は未確認であり、「未作成」とは断定しない。

環境変数の有無だけを確認し、`CLOUDFLARE_API_TOKEN`と`CLOUDFLARE_MIGRATION_READ_TOKEN`は設定済み、`CLOUDFLARE_QUEUE_READ_TOKEN`と`CLOUDFLARE_WORKERS_READ_TOKEN`は未設定だった。トークン値は表示していない。Queue backlogと本番Worker versionのソース対応は未確認。Workflow dispatch、R2書き込み、Worker deploy、commit/push、GitHub設定変更は行っていない。

移行Workflowのテストを拡張し、D1 read token、Analytics read token、R2 keyの分離、D1-only phaseへのR2 key非注入、D1 usage gateの順序、report/checkpointだけの14日artifact、Worker CIにdeployがないことを検査する。既存migration CLIテストは報告・checkpointからデータ行body、業務コード、raw R2 key sampleを除くことを確認する。Python migration/usage/workflow 82/82、Worker 120/120、`node --check src/index.js`成功。これはローカル検証であり、GitHub上のWorkflow実行結果ではない。

## 2026-11-05 JST（2026-11-04 20:54 UTC）KV Read権限追加後の再検証

追加後のCloudflare KV Read権限で`definitions` dry-runを再実行した。20:54:54 UTCのAccount Analytics preflightは全3 DB合計435,464/5,000,000 rows read、残り4,564,536行を報告し、100,500行の必要残量ゲートを満たした。実行結果はcomplete、12 objects planned、D1 query 0・rows read 0、conflicts 0、R2 writes 0だった。

レポート: `C:\Codex\r2-d1-ownership-audit-20261105\definitions-kv-read-after-user-confirmation-20261104T2052Z-report.json`。report内SHA-256は`27566c261bcdf79b20e19d3c7884e791dd42bc9b7846783d839b0640c5963660`、保存ファイルSHA-256は`B153CD98AD4BEE6F6B4CA4515C7B2E5331ECA986F04843A40A4A40EFC26348B5`。KV定義は読み取れたがR2へのseedはまだ行っていない。

## 2026-11-05 JST（2026-11-04 21:01–21:05 UTC）本番D1 operational inventory再確認

最新の読み取り専用`d1-schema-audit`は27 schema objects（tables 25、triggers 2）を返した。Account Analyticsは21:05:22 UTCに438,274/5,000,000 rows read、残り4,561,726行を報告した。監査フェーズのD1 `meta.rows_read` 合計2,802行と、21:02 UTCのpreflightからのAnalytics増分が一致し、100,000行の予備枠を保った。

`d1-operational-audit`は次を確認した。`backend_state`は0行。`scheduled_run_log`は627行すべて`running`で`finished_at`がなく、開始範囲は2026-10-04 19:49:05.538 UTCから2026-11-04 20:17:34.266 UTC。前回14:41 UTCの621行から6行増え、現在のCron時刻と整合するが、各行を完了履歴として扱わない。`market_price_refresh_runs`は2行（`partial` 1、`running` 1、完了時刻なし1）で、total/enqueued/completed/failed batchesは3,960/2,080/2,080/60、processed symbolsは4,040。`market_price_refresh_batches`は2,080行（`complete` 2,020、`failed` 60）、全行のmarket dateは2026-10-09。

schemaと4 operational reportは`C:\Codex\r2-d1-ownership-audit-20261105\`の`d1-schema-audit-after-kv-read-20261104T210126Z.report.json`、`d1-operational-backend_state-20261104T210247Z.report.json`、`d1-operational-scheduled_run_log-20261104T210247Z.report.json`、`d1-operational-market_price_refresh_runs-20261104T210247Z.report.json`、`d1-operational-market_price_refresh_batches-20261104T210247Z.report.json`。すべてdry-runで、R2 objects written 0。進行中run、価格更新履歴、Queueの保持・復旧要件とライブbacklogが解決するまで、該当D1行やQueueを移動・削除しない。

## 2026-11-05 JST（2026-11-04 21:09–21:27 UTC）移行対象の再判定と出力ハッシュ

`scheduled-runs` dry-runは627 D1行を読み、`source_rows=0`、objects planned/writtenとも0件で完了した。現時点の `scheduled_run_log` は全件が未完了であり、完了履歴としてR2へ移さない。`origin/main` commit `b3f3a7b75bc54d30ec468fc085922791f99e340b`はCronを`17 * * * *`に設定し、定期分析の開始時に`running`をinsert、正常・例外経路で完了statusをupdateする。開始分は現行sourceと整合するが、配信中Workerとのsource対応が未確認のため原因は断定できない。同commitには旧market-price runs/batchesのD1書込みもある。Account Analyticsは21:24:12 UTCに全3 DB合計443,027/5,000,000 rows read、残り4,556,973行を報告した。21:05:22からの増分4,753行のうち、このdry-runで計測した627行以外の発生元は特定できていないため、当作業の読取として一括計上しない。

Workers KV Read権限を使った`definitions` dry-runは、6定義のversion/current object計12件を計画し、D1 queries/rows read 0、conflicts 0、R2 writes 0で完了した。新しい `rolling-sha256-v1` はkeyとbodyの組を順序付きで指紋化し、完全なdry-run planを `write_set_sha256=f1b9dc415b9858deb49ec71473d532e96f873ba8d87578eaa83c7cbec44b08b4` として記録する。raw key/bodyはreportとcheckpointに含めない。migration-focused tests 64/64、Python全suite 84/84が成功した。空のfixture namespaceでdry-run、R2 fake write、readbackのdigest一致も確認した。

レポートは `C:\Codex\r2-d1-ownership-audit-20261105\definitions-write-set-20261104T212434Z.report.json`（report SHA-256 `2b622bfb5f9076cd751cfba3ca6763014ccaa06a97a4532e286c3a28efeb0490`、ファイルSHA-256 `82CABFE0C997BE8D92B9F1166E8B131BE87AE920A6A3BF683236F35B74DDB389`）。scheduled-runsレポートは `C:\Codex\r2-d1-ownership-audit-20261105\scheduled-runs-current-20261104T210926Z.report.json`。定義のseed、R2 API readback、Worker/API parityはまだ未実施であり、本番R2書込・deploy・Actions dispatchも行っていない。

## 2026-11-04 23:05 UTC GitHub保護・本番Worker source取得経路の確認

ChromeでGitHubの`https://github.com/masacgt/Diview/settings/branches`と`/settings/environments`を読み取り確認した。Branch settingsには保護ルール作成操作のみが表示され、既存ruleset/classic protectionは見当たらなかった。GitHub branch metadataも`main`を`protected=false`、rulesetsを空配列と報告している。Environment一覧には`IRBANK_API_KEY`だけがあり、`d1-r2-migration` Environmentは存在しない。設定変更・secret値の閲覧・Workflow公開/実行は行っていない。ローカルのWorker CIとmigration Workflowは未公開のままである。

`CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`は、ユーザーから設定済みとの連絡があるが、今回のCodex ProcessおよびWindows User/Machine環境からは見えない。値は出力していない。Wrangler OAuthで一覧を確認した限りdeployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`はVersion `4981c1e0-e337-4392-9f5b-909b52c5bc0c`を100%配信し、新しいdeploymentはない。`wrangler versions view`応答にはversion ID・metadata・annotations・resourcesがあり、code modulesは含まれなかったため、source commit mappingは未確定。

Cloudflare APIのversion-specific source取得先は `GET /accounts/{account_id}/workers/workers/{worker_id}/versions/{version_id}?include=modules`（[Get Worker Version](https://developers.cloudflare.com/api/resources/workers/subresources/beta/subresources/workers/subresources/versions/methods/get/)）。Workers Scripts Readを使って対象Versionのmoduleを取得し、候補bundleとの対応を調べる。Queueは既存runbook記載のQueues metrics endpointで再確認する。

Account Analyticsの最新読み取りは2026-11-04 22:55:09 UTC、3 DB合計485,372 rows read・4,246 rows written・残り4,514,628行で、22:32 snapshotから変化なし。D1 SQLは発行していない。R2 definitions 12 objectsのseed/API parity、Queue backlog、source identity、GitHub保護・Environment、DO handoff、financial_periodsの17期間欠落・4値差異の正本性は未解決。本番R2書込・Actions dispatch・Worker deployは未実施。
## 2026-11-05 JST（2026-11-04 23:19 UTC）レート制限設定と本番D1インデックス確認

- 23:18:19 UTCのAccount Analyticsは3 DB合計485,380 rows read・4,247 rows written、5,000,000行中4,514,620行残り。100,000行reserveを維持してから限定クエリを実行した。
- 本番 api_request_log の直近60秒窓を window_started_at 条件と LIMIT 10001 で確認した結果、該当bucketは0件、D1 meta.rows_read=1。範囲は2026-11-04 23:17:19.799〜23:18:19.799 UTC。これはその時点の一分間の観測であり、切替時のD1 write fenceを不要にする証拠ではない。
- 同じクエリの EXPLAIN QUERY PLAN は SEARCH api_request_log USING INDEX idx_api_request_log_window (window_started_at>? AND window_started_at<?) を返し、meta.rows_read=0。時刻範囲検索が既存indexを使うことを確認した。
- 読み取り専用KV GETで得た本番 backend-config:v1 は rate_limit_window_seconds=60、rate_limit_register=10、rate_limit_authenticated=120、rate_limit_public=300、market_stale_days=3。チェックイン済みKV seedとD1 migration defaultと一致した。
- CLOUDFLARE_WORKERS_READ_TOKENとCLOUDFLARE_QUEUE_READ_TOKENはユーザー設定済みとの連絡後も今回のCodex実行環境に見えず、正確なWorker source modulesとQueue metricsは未取得。秘密値は表示していない。
- snapshot reportは C:\Codex\r2-d1-ownership-audit-20261105\active_limiter_snapshot.json、EXPLAIN reportは同ディレクトリの active_limiter_explain.json。D1は読み取りのみ。R2 write、Workflow dispatch、Worker deploy、D1 migration、commit/pushは行っていない。

## 2026-11-05 JST（2026-11-04 23:22 UTC）クエリ後Analyticsの再確認

23:22:15 UTCのAccount Analyticsは489,507 rows read・4,444 rows written、当日残り4,510,493行だった。23:19:23の485,380行から4,127行増えているが、この監査で実行したD1限定queryのmeta.rows_readは1、EXPLAINは0であり、4,126行の差は帰属できない。Analyticsの遅延反映か別の利用かを切り分けられていない。report SHA-256は08516CDC1D5FEED7BC0E0277EFA57C9A876C48E3913D92065DE43C984580BE72。次のD1 SQL前にAccount Analyticsを再確認し、監査クエリ由来の増分と混同しない。

## 2026-11-05 JST Android検証結果

- 候補worktree `codex/r2-d1-ownership-20261104-final`（HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`）でJDK 17.0.20.1を使用し、Android JVMテスト274件が成功した。
- 隔離API 35 AVD `DiviewR2Audit35` / `emulator-5580`で `ReleaseFixtureInstrumentation` の137件が成功した。APKは同じworktreeでビルドし、アプリAPK SHA-256 `D88D31736FF287AACC5A305DE110524B6DB93DAFA6CD220F0513F59C8E451495`、androidTest APK SHA-256 `83557BEA18216DFD587AFD47AFD5E0DDB0BC9F0BFD3B2A00DFDF10F953742E99`は、各AVD導入物と一致した。
- Gradleの `connectedDebugAndroidTest` は端末フィルター処理 `DeviceProviderInstrumentTestTask.getFilteredDevices` で導入前に例外終了した。これはテスト失敗ではない。同じビルド済みAPKをADBで隔離AVDへ導入し、instrumentation runnerを直接実行して137件を確認した。既存 `emulator-5554` のアプリ導入状態は変えていない。
- 再現記録 `C:\Codex\android-avd-r2-d1-ownership-20261105\android-validation-summary.json` のSHA-256は `BD07BFD8656D631D470B1AA07452E91B0CF5912A34A544E882B928AF4F3A3753`。Gradle接続タスクの例外は個別の環境・プラグイン問題として未解決。

## 2026-11-05 JST（2026-11-04 23:57 UTC）Analyticsと認証環境の再確認

- Account Analyticsは3 D1 database合計489,619 rows read・4,529 rows written、当日残り4,510,381行だった。100,000行reserveを維持している。23:22:15 UTCの489,507行から112行増えたが、今回D1 SQLは実行しておらず、この増分の発生元は未確定。
- トークン値を表示せず環境変数の有無を照合した。CLOUDFLARE_WORKERS_READ_TOKENとCLOUDFLARE_QUEUE_READ_TOKENはProcess/User/Machineの全scopeで未検出。CLOUDFLARE_API_TOKENとCLOUDFLARE_MIGRATION_READ_TOKENはProcess/Userで検出した。ユーザーはread token設定済みと連絡しているが、現在のCodex実行環境からは読めないためWorker version modulesとQueue backlogの取得は未実施。
- Analytics reportは `C:\Codex\r2-d1-ownership-audit-20261105\d1-daily-usage-20261104T2357Z.json`、SHA-256 `44E6C7B07C26BFC60A8AB4A198E3D58919597DC66AD8E77288DC12024DA3DCA6`。D1 SQL、R2 write、Workflow dispatch、Worker deploy、commit/pushは行っていない。


## 2026-11-05 JST（2026-11-05 00:07–00:35 UTC）financial_periods 年次データ再監査

- UTC日付更新後、同じmarket-source-auditを独立に2回dry-runした。各回49,592 D1 source rows、meta.rows_read合計99,184、496 queries、R2 source object 3,061件を確認し、49,571件一致・17件R2期間なし・4件値差異、R2 writes 0、conflicts 0で完了した。Account Analyticsの00:35:01 UTC時点は202,509 rows read / 200 rows written、残り4,797,491行。account-wideの差分はこの監査だけに帰属させない。
- R2/IRBANKの年次期間をメタデータだけで照合した。1301のR2 overviewは2008/03–2024/03の17期間で、ローカルraw sourceには2025/03・2026/03も存在する。1301の不足2期間は、単位・連結範囲・会計基準を検証したsourceからoverviewと個別financial objectを作成し、dry-run/readbackするまで未解決とする。[Kyokuyo IR reports](https://www.kyokuyo.co.jp/ir/library/library_reports01/)
- 2708のR2 overviewは2008/03–2024/03。ローカルraw sourceには2011/10–2025/10の15個の10月期ラベルがあるが、JPXと久世IRの現行情報は3月期を示す。由来・期間対応が確認できるまで15行をR2へ移さない。[JPX listing](https://www2.jpx.co.jp/tseHpFront/StockSearch.do?method=topsearch&topSearchStr=2708) / [Kuze IR](https://www.kuze.co.jp/ir/calendar/)
- 値差異4件は1301の2017/03・2023/03・2024/03、2708の2014/03。値や会社データはこの記録に転記しない。2708 FY2014の既知の混在発行体候補を含み、正本性・field mappingが確定するまで修正・移行しない。
- `prepare_dataset.py`はローカルsnapshotを整形し、既存`prepare_verified_annual_backfill.py`は2008/03–2024/03の歴史分を対象にする。現状のままでは1301の2025/03・2026/03を埋めないため、既存annual override検証と単位契約を保った追加準備・テストが必要。
- レポート: `C:\Codex\r2-d1-ownership-audit-20261105\financial-periods-reaudit-20261105T0010Z.report.json` (SHA-256 `c2644204f5e01cebc19804f1507faf9e295b97d47ddbd85031998048adf53d79`); `financial-periods-anomaly-inspect-20261105T0020Z.report.json` (`7b52383535e601d76a3fafb315b73866e4c3f9c9a15790d7a507091dd99c09ac`); R2 metadata-only report `financial-periods-r2-metadata-20261105.json` (`a9ffc7f76e8da8e4c42eba2ca92c1a74632e0472a86977ac62a8186cb3d95faf`); usage snapshot `d1-daily-usage-post-anomaly-trace-20261105.json` (`2bea578377e96e1bac2ebd4f1ed9a9fde7c401f5d2fbe41aa0b53e6efd25d1b8`).
- これらは読み取り監査とローカルmetadata確認のみ。D1/R2への書込み、Worker deploy、Actions dispatch、commit/pushは行っていない。


## 2026-11-05 JST（2026-11-05 00:46–01:15 UTC）起動時の価格再確認

通常の起動更新記録と価格取得記録は別々に進む。通常の起動処理が先に当日分を記録した後、価格更新が12時間制限で見送られた場合、次の同日起動は通常処理を抑止され、従来は価格取得失敗時しか再確認しなかった。これでは成功済みの前回価格試行から12時間を超えた後の起動でも価格更新を逃す。

候補実装では、通常起動の同日抑止中も保存済み価格試行の時刻・JST日付と状態を再評価し、成功済み試行なら12時間経過かつJST日付が変わったときに限り価格更新を許可する。失敗状態の30秒再試行は維持し、価格試行記録がない場合は通常の日次起動抑止を維持する。23:30 JSTの取得後、翌日10:00 JSTは見送り、12:00 JSTの起動では取得するケースを固定時刻テストで確認した。

検証はJDK 17.0.20.1でAndroid JVM 276/276、`AssetDataRefreshTest` 21/21。Debug/AndroidTest APKを検証専用 `DiviewR2Audit35`（`emulator-5580`）に入れ、生成物とインストール済みbase.apkのSHA-256一致を確認した。app APK `a942ef1897da56eab4f37f3450fa264d09291d3c3c7c827fb473dd1906dc27b4`、AndroidTest APK `1ecbbf654549c8409cbad8d9fef7b8ad30b9a5284e889cb5e8564b076ad80bb9`。共有 `emulator-5554` と物理端末にはインストールしていない。

## 2026-11-05 JST（DOレート制限引継ぎを期限付きに修正）

従来の `rate_limit_legacy_seed` marker は同じDO actorによる再読取を防ぐ一方、新しいbucket keyが増えるたびに初回D1 SELECTを許し続けていた。候補Workerとrollback bridgeに `RATE_LIMITER_D1_FENCE_AT` / `RATE_LIMITER_D1_IMPORT_UNTIL` を必須化し、Unix秒のdeadlineが過ぎた後は新規actorもD1を読まずに新しいwindowを開始する。D1 bindingがある状態で値が欠落・不正、fence timestampが未来、または期限がfenceから最低1 window未満ならfail closedにする。

handoffではD1 write fenceの3 triggerを読み戻した後にfence timestampを記録し、deadlineを現在の最大window + 60秒の安全余裕に設定する。現在確認できたwindowは60秒。両timestampをbridge、最終Worker、rollbackに同じ値で渡す。Workerコードはすべての有効リクエストで設定を検証し、未来のfence時刻や現在のroute windowを覆わないdeadlineを、保存済みcounter rowまたはseed markerの有無にかかわらず拒否する。既存actorとcleanup済みcounter markerの回帰ケースを加えたWorker全体suiteはbridge 111/111、最終候補127/127で成功し、Wrangler 4.136.1 dry-runは最終・通常bridge・Cron停止bridgeの各configで成功した。これはローカル検証であり、本番DO handoffは未実施。

ユーザーからWorker/Queue読み取りtokenを設定したとの連絡があったが、2026-11-05 02:50 UTC時点で両変数は現在のCodex Process/User/Machine環境に見えない。値は表示していない。配信Workerのsource identityとQueue backlogは未取得。Codex再起動後またはCodex起動元環境に反映した後、読み取り監査を行う。本番設定値の再確認、source identity、Queue backlog、D1 write fence、実運用でのDO usage確認が済むまでdeployしない。

## 2026-11-05 JST（本番D1運用状態とR2カタログの再読取）

Account AnalyticsのUTC日使用量は02:50 UTCに210,753/5,000,000行読取、残り4,789,247行だった。02:36以降のread-only migration監査でD1 `meta.rows_read` 合計732を確認したが、02:50のAnalytics表示は増えていない。HTTP 400で失敗した一回の照会とWorker health/live-quotes GETのD1読取量も不明なため、監査後の正確なaccount-wide使用量は未確定。次のD1 SQL前に最新のAnalyticsを再確認する。

本番D1 schemaは27 object（25 table、support-feedback trigger 2件）。`api_request_log_handoff_block_*` triggerはなく、D1 write fenceは未設定。`backend_state` は0行。`scheduled_run_log` は633行すべて `running`、`finished_at`なし、processed/failed合計0。最新開始は `2026-11-05T02:17:32.819Z` で、前日21:02 UTCの627行から6件増えた。`scheduled-runs`移行は完了済み行のみを対象とするため、この633行は履歴として移行せず、原因確認と保持判断が残る。`market_price_refresh_runs`は2行（10月10日partial、10月12日running）。10月12日runは1980 batch中100をenqueue/completed、194銘柄処理、3失敗。前回のbatch監査（11月4日21:02 UTC）は2080行、2020 complete・60 failed・未完了時刻なし。

02:44:54 UTCの本番health GETはD1 bindingあり、最新scheduled runは02:17 startのrunning、legacy snapshotのアップロードは10月10日（3日stale thresholdに対し25.76日）、旧refresh状態は10月12日を返した。これは最新v2価格カタログの有効性を表す指標ではない。02:54 UTCのR2 current pointerとimmutable generation objectは同一SHA-256 `681e8e58df1986ba9bca4082ac231c087610179dc109bb0a2912f9be274aaf72`、generation `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`、market date 11月4日、4391件で一致した。`GET /api/v1/live-quotes?codes=7203` も同generation/dateを返し、7203の¥3,051（quote date 11月2日）がR2と一致した。詳細レポートは `C:\Codex\r2-d1-ownership-audit-20261105\live-quotes-r2-readback-20261105T0254Z.json`（SHA-256 `84294E6FE195C69D5377677A188EF3A77A2A98C90605F36E5FEC7FAE4B7E36F1`）。これは1銘柄のAPI/R2照合であり、全カタログ一致やWorker moduleのsource identityの証明ではない。

schema・運用監査レポートは `C:\Codex\r2-d1-ownership-audit-20261105\d1-schema-readonly-20261105T0236Z.report.json`（SHA-256 `8EABB2B2FB678B888A49DC1C71D016A022A633191995CA3845930AD62E4B4B46`）、`d1-operational-backend_state-20261105T0237Z.report.json`（`DD083325D946EE7D52B9928508A3F99B09B0B49AB153336E897F2584AC55F520`）、`d1-operational-scheduled_run_log-20261105T0240Z.report.json`（`C386A11AAEA4232E4B8F24B65C233B1FE5327284F8439AB339EC32AA026C9FC8`）、`d1-operational-market_price_refresh_runs-20261105T0242Z.report.json`（`11AD791FB746F010BAD85DE37FC43B36BFEC1C32EB9C8E1785EE81AF0475C6B5`）。

Worker/Queue read tokenは02:50 UTCにもProcess/User/Machine環境から見えず、配信Worker version modulesとlive Queue backlogは未取得。health/live-quotes HTTP要求のD1 `meta.rows_read`も得ていない。D1 write fence、Worker Logsの未完了分析run原因、Queue backlogと外部producer、旧D1データの保持条件を確かめるまで、データ削除やproduction cutoverは行わない。

## 2026-11-05 JST（Account Analytics再確認と定期run経路点検、03:06 UTC）

03:06:00 UTCのAccount Analyticsは当日211,495行読取・551行書込、残り4,788,505行。対象D1は211,440行読取・543行書込、別DBは55行読取・8行書込だった。02:54の210,753行から742行増えたが、このAnalytics更新ではD1 SQLを実行していないため、増加元は特定できない。レポート `C:\Codex\r2-d1-ownership-audit-20261105\d1-daily-usage-20261105T030600Z.json`、SHA-256 `40D1B4D0AD0801873D0523137664AA93094BF3E3A89A8B6A6676FEEEF401631F`。

専用Worker/Queue read tokenはこのターンの確認でもProcess/User/Machine環境に見えない。既存のAccount Analytics tokenによるWorker version modules取得とQueue一覧取得はどちらもHTTP 403で、module内容やQueue metricsは得られなかった。この403は専用tokenの有効性を確認したものではない。token値は表示していない。

ローカル `origin/main` commit `b3f3a7b75bc54d30ec468fc085922791f99e340b` の `runScheduledAnalysis` は、`running`行をtryブロック前にD1へ追加し、正常完了とcatchされた例外の両方でfinished statusへ更新する。ローカルWrangler設定のCronは毎時17分。このコードからは、通常catchされる分析例外だけで633行がrunningのまま残ることを説明できない。更新失敗、実行の強制終了、配信コードとの差分などは可能性として残るが未確認であり、配信version modulesとWorker Logsが原因特定に必要。本番Workerのsource identityは未検証のまま。


## GitHubとAndroidの再確認（2026-11-05 04:31 UTC）

Account Analyticsは219,981/5,000,000行読取、1,205行書込、残り4,780,019行。対象D1は219,867行読取。今回の確認ではD1 SQLを発行していない。

GitHub公開REST APIで `masacgt/Diview` はpublic、`main` はSHA `b3f3a7b75bc54d30ec468fc085922791f99e340b` で `protected=false` と再確認した。候補の手動migration workflowはまだローカルにあり、GitHub main保護、独立reviewer付きEnvironment、CI実行、workflow dispatchは未実施。Cloudflare read tokenが存在することとは別に、workflow用GitHub EnvironmentへAccount/D1/Analytics/R2 credentialsを用意する必要がある。

Worker/Queue read tokenを設定したとのユーザー連絡後も、04:30 UTC時点のCodex Process/User/Machine環境から両変数を検出できなかった。値は出力していない。実行環境から参照できるようにした後、配信Worker modules、Queueのpoint-in-time backlog metrics、Worker Logsを読み取る。これらが未確認の間、未完了runの原因、外部Queue producer、DO handoffの安全性は確定しない。

候補Android APKはJDK 17で隔離AVD `emulator-5580` に導入し、instrumentation全138件が成功した。構築APKとAVD上の `base.apk` はSHA-256 `A942EF1897DA56EAB4F37F3450FA264D09291D3C3C7C827FB473DD1906DC27B4` で一致した。これは実機確認ではない。

Cloudflare R2の公式仕様では、S3互換 `PutObject` の `If-Match` / `If-None-Match` はcommit時に原子的に評価される。根拠: [Conditional operations](https://developers.cloudflare.com/r2/api/s3/extensions/) と [Consistency model](https://developers.cloudflare.com/r2/reference/consistency/)。実環境へのconditional write/readbackはまだ行っていない。production write/deploy、GitHub設定変更、Actions dispatch、commit/pushは未実施。

## 2026-11-05 04:58 UTC 銘柄検索カタログと移行dry-run再監査

本番D1 securitiesの3,960行・3,960 unique display codeを、現在のR2 YFinance catalog（4,391件）とIRBANK catalog（3,963件）に照合した。YFinance catalogにないD1コード65件はIRBANK catalogに全て存在し、両方にないコードは0件だった。照合report C:/Codex/r2-d1-ownership-audit-20261105/security-catalog-coverage-20261105T0456Z.report.jsonのSHA-256は57d5229a1a54dc4c68c1079ee2fcfefc846db3a8d4465e0daa9641acc4825827。D1 SELECTはmeta.rows_read=3,960。R2/D1書込みなし。

候補Workerの /api/v1/security-search はYFinance catalogに加えてR2 IRBANK catalogからcode/nameを検索し、株価・利回りがないIRBANK-only項目にはnullを返す。D1 fallbackはない。対象route suite 11/11、Worker suite 128/128成功。ただし候補実装の結果であり、本番Workerへのdeploy/source identity確認は未実施。

04:58:00 UTCのAnalytics preflightは224,264 rows readで、100,000行reserveを維持した。直後のAnalyticsは同値だったが、05:03:11 UTCに対象D1が224,079から228,039へ+3,960となり、SELECTのmeta.rows_readと一致した。account-wideは228,251、残り4,771,749。別DBは合計46行増で、同時アクセスとの区別はできない。最新usage reportはC:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T050310Z.json（SHA-256 D781980A3651ABFA88F403B4219629D2124EAC34A095954D83AEBEDDFAC0A3BB）。以後のD1 SQL前に再度preflightする。別の6 dry-run reportは合計4,212 rows_read、0 conflict、R2 writes 0。analysis-currentは248 object、definitionsは2 objectを計画し、market auditは3,960銘柄を読み取った。analysis-historyは38,156件のcomplete dry-runがあり、以前のcursor 1,500 checkpointは後続のcomplete runで置き換えられている。各report hashとAccount Analytics数値はcandidate migration runbookに記録した。

CLOUDFLARE_WORKERS_READ_TOKEN と CLOUDFLARE_QUEUE_READ_TOKEN はユーザー設定連絡後も05:10:23 UTC時点でこのCodex Process/User/Machine環境から見えなかった。値は表示していない。配信Worker modules、Queue backlog、Worker Logs、GitHub migration CIは未取得。main protection/Environment、D1 write fence、DO handoff、未完了runの原因、definitions API parity、financial_periodsの不足/差異が解決するまでproduction cutoverは保留する。本番write/deploy、GitHub設定変更、Actions dispatch、commit/pushなし。
## 2026-11-05 05:12 UTC R2定義dry-run

6個の論理定義をKV優先で読み取り、R2 migrationをdry-runした。D1 queries attempted=0、R2 version/current objectの計画12件、unchanged 0、conflict 0、write 0。必要な定義objectが現R2にないため、候補Workerのdefinition-backed APIをdeployする前にR2 seed・readback・API parityが必要。report C:/Codex/r2-d1-ownership-audit-20261105/definition-source-20261105T051152Z.report.json、report_sha256 8024eec200414fd0d4655c3ac887fc5a33c1ecf433048bd80ffccba72ec62472。Account Analytics preflightは228,274 reads、残り4,771,726行、100,000行reserveを確保。D1 query/R2 write/Worker deployなし。

## 2026-11-05 05:39 UTC インカム空白月候補の現行データと端末処理

空白月候補はD1移行対象ではなく、既存R2の `market/yfinance/v2/derived/income_advice/current.json` をWorker APIで配り、Androidが端末保存した候補をウォッチリストの空白月と照合する処理である。候補カタログはSharedPreferencesにraw JSONで保存し、ViewModel生成時に読み込む。ウォッチリストがある場合だけ起動時更新を試み、更新成功はJST日付あたり最大1回かつ前回から12時間以上空ける。取得失敗時は保存済みカタログを保持し、ランキングは端末で行う。ウォッチリスト登録済み銘柄を外し、空白月を多く埋める候補を最大3件選ぶ。これは配当通知の価格キャッシュとは別機能である。

2026-11-05の読み取り監査で、R2 market catalogとincome-advice currentは同じgeneration `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`。市場カタログ4,391件、候補3,203件。候補はequity 2,889、ETF 253、REIT 61で、カタログ外コード0、重複0、`source_catalog_generation`不一致0。カタログSHA-256 `681e8e58df1986ba9bca4082ac231c087610179dc109bb0a2912f9be274aaf72`、候補SHA-256 `e8daab1ea567dbe1f9acfef0ca663357881ec4565a3c4234451f5750a6dfc31d`。現行候補の253 ETFはETFを除外する選択済み仕様と不一致である。

定期V2同期、復旧経路、独立候補生成に `security_type=etf` の除外を入れた。既存generationの候補を再利用してもETF候補が残らないよう、カタログ公開直前にも除外する。equityとREITは維持する。候補生成、ETFの既存候補除去、同期の反復公開テストは成功した。現行R2成果物はまだ再生成していない。

現行R2 income-advice JSONをローカルWorker routeの制御されたR2 bindingから返し、HTTP 200、全JSON構造一致、generationと3,203件の一致を確認した。rate-limit bindingはローカルstub、D1 bindingはアクセス検知proxyであり、アクセス0回。R2 GET 2回、R2 write 0回。この検査は候補Worker routeがR2成果物を返すことの候補コード検証であり、本番Workerへの配信や本番API応答を証明しない。

Androidの既存 `IncomeAdviceCatalogCacheTest` はネットワークなしでも保存済み候補をウォッチリストUIに表示する経路を持つ。全instrumentation 138件の成功記録は候補APKの隔離AVD確認に含まれるが、このターンでは同testを個別再実行していない。R2候補の再生成・readback・Worker API parity・実機表示は未完了で、承認済みのproduction write/deployまでは実施しない。

### 再現用レポート

R2 category coverage: C:/Codex/r2-d1-ownership-audit-20261105/income-advice-category-coverage-20261105.json (SHA-256 061e4e0122180b637999b6a4896356169e9f91fe1074f61cd4e0c6cc2883cfdd). Local Worker route check: C:/Codex/r2-d1-ownership-audit-20261105/income-advice-worker-route-verification-20261105.json (SHA-256 515862b07edab66c9b0c31d19d447b4a7da874320fc81c028b3c6d04d653b075).
## 2026-11-05 06:03 UTC GitHub Actions・端末価格更新・使用量の再監査

GitHubのmainは b3f3a7b75bc54d30ec468fc085922791f99e340b で候補作業ツリーの基準commitと一致し、branch APIは protected=false を返した。候補の cloudflare-worker-ci.yml と d1-r2-migration.yml は未追跡で、GitHub mainからの取得は404。したがって現状はGitHub Actionsに掲載・実行されていない。Worker CIはCloudflareコードのpush/PRに対するテストだけを行いdeployしない。D1移行Workflowはmanual workflow_dispatch 限定で、protected main・独立reviewer付きEnvironment・main限定policyをpreflightする。今のbranch protectionではpreflightが失敗する。環境側にはD1読取、Analytics読取、R2資格情報を設定する必要がある。既存YFinance平日更新（12:17 UTC）とIRBANK日次更新（17:17 UTC）は変更していない。

Android価格更新は refreshOnStart=true のViewModel初期化から、ローカル復元後に起動する。通常更新ゲートが未達でも、MarketFetchPolicy.shouldFetchOnStartup が許可すれば価格更新だけを開始する。日本株の保有・ウォッチ銘柄を対象とし、通知Workerは端末内 NotificationPriceCache を読む。05:39 UTCに記録した隔離AVD instrumentation 138件成功は既存証拠で、この監査ではAndroidビルド・端末実行をしていない。

Account Analyticsは2026-11-05 05:57:30 UTC時点で232,422 reads / 2,000 writes、残り4,767,578行。対象D1 232,165 reads、他D1 257。100,000行reserveを確保。レポート C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T055730Z.sanitized.json SHA-256 BF637F50BBBCC23CA3264462F53E1CA243F7399F8BDFD65672C5DEB49DB038F7。AnalyticsのみでD1 SQL/R2書込みなし。

06:03 UTCの値なし環境確認で CLOUDFLARE_WORKERS_READ_TOKEN と CLOUDFLARE_QUEUE_READ_TOKEN はCodex Process/User/Machineいずれにも見えなかった。Worker deployed source identity、Queue metrics/consumers、Worker Logsは未確認。本番read-only監査を再開するにはCodexプロセスから両tokenが参照できる必要がある。branch protection、GitHub Environment設定、Workflow掲載/実行、R2/D1 writes、Worker deploy、commit/pushは行っていない。

## Android候補の隔離AVD再検証（2026-11-05 06:31 UTC）

候補作業ツリー `b3f3a7b75bc54d30ec468fc085922791f99e340b` をJDK 17でビルドし、専用AVD `emulator-5580` で `:app:connectedDebugAndroidTest --rerun-tasks` を実行した。JUnit XML、AndroidJUnitRunnerログ、test-result.textprotoは138件（137 passed、1 ignored）、失敗0で一致した。skipは `MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults` で、JUnit XMLに理由文字列は記録されていない。一方、Gradle UTP最終コンソール行は139 testsと表示した。この集計差は未解決。結果XMLは `kurogane-dividend/android/app/build/outputs/androidTest-results/connected/debug/TEST-DiviewR2Audit35-20261105(AVD) - 15-_app-.xml`。

テストタスク終了後にGradleがアプリをAVDから外したため、同じ専用AVDで候補APKを明示パスから再導入した。`MainActivity` の起動を確認し、ビルドAPKと導入後 `base.apk` のSHA-256はともに `A942EF1897DA56EAB4F37F3450FA264D09291D3C3C7C827FB473DD1906DC27B4`。導入APKの監査コピーは `C:/Codex/r2-d1-ownership-audit-20261105/android-base-emulator-5580-20261105.apk`。この確認は本番Worker/R2/D1状態、実機動作、skipされたlive repositoryケースを証明しない。

## Account Analyticsと認証環境の再確認（2026-11-05 06:36 UTC）

Account Analyticsは06:36:07 UTC時点で3 D1合計236,663 rows read、2,263 rows written、残り4,763,337 rowsだった。100,000行の余裕を確保した。レポート `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T063607Z.sanitized.json`、SHA-256 `73A57C7D3162231F723FF969219F429D79E24172DF90A9AF3B19407F3912445D`。これはGraphQL Analytics照会でありD1 SQLは発行していない。

06:34:40 UTCの値なし環境確認では `CLOUDFLARE_WORKERS_READ_TOKEN` と `CLOUDFLARE_QUEUE_READ_TOKEN` はCodex Process/User/Machine scopeのいずれにも見えなかった。値は出力していない。Worker deployed modules/source identity、Queue backlog/consumers、Worker Logsは未取得。

## opt-in公開API acceptance（2026-11-05 06:49 UTC）

`MarketFetchEfficiencyLiveTest` のskip guardは `allowLiveMarketReads=true` のrunner argumentがないことだった。引数を明示して専用AVDから単独実行したところ、公開GET `/api/v1/securities/285A?include_history_bounds=false` の解析後、`storedHistoryStatus` は期待値`not_requested`に対して実測`available`となり失敗した。この時点でtestが終了し、後続のanalysis endpoint assertionsは実行されていない。

候補Worker実装では同query parameterが `readV2DividendHistoryBounds` の呼び出しを省略して`not_requested`を返す。`market-read-routes.test.mjs` はbounds省略時にR2 object-list readsが0であることを検証する。一方、本番のdeployed module/versionはread tokenが現在のCodex環境に見えないため未確認であり、応答差だけからD1アクセス有無を断定できない。このlive acceptance failureを本番切替前の未解決ゲートとして扱う。

Analytics preflight 06:43:48 UTCとpostflight 06:48:10 UTCはいずれもaccount-wide 236,663 reads / 2,263 writes、残り4,763,337行だった。postflight report `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T064810Z.sanitized.json` SHA-256 `0F7EBE0A9209E7735E5A4804BCF6883CFAACB09DD23E9EA20B6B09291E729ED7`。Analyticsの集計遅延があり得るため、この同値だけを個々のHTTP requestがD1/R2を読書きしていない証拠とはしない。直接D1 SQL、移行R2 write、Worker deployは行っていない。

06:49:58 UTCの値なし確認でも `CLOUDFLARE_WORKERS_READ_TOKEN` と `CLOUDFLARE_QUEUE_READ_TOKEN` はProcess/User/Machineいずれにも見えなかった。値は表示していない。deployed Worker source identity、Queue metrics/consumers、Worker logsは未取得。

## 候補WorkerのD1呼び出し再棚卸し（2026-11-05 06:57 UTC）

候補 `data/irbank/cloudflare/src/` 全体でD1呼び出しを検索した。D1は、各Durable Object bucketの初回counter seed時に限った `api_request_log` のread-only SELECTと、残置対象である `support_feedback` / `support_feedback_email` の受付・管理・retry/cleanupに使われる。health routeはD1 bindingの有無のみを表示する。銘柄検索、市場カタログ、銘柄詳細、分析、定期分析、定義取得の直接D1 SQLは見つからなかった。DO内SQLiteのcounter reads/writesはD1操作とは分けて扱う。

これは候補コードの静的棚卸しであり、配信Workerのsource identityや本番リクエストの実アクセスを証明しない。Worker read tokenが参照可能になった後、deployed modulesとWorker logsで同じ呼び出し範囲を確認する。

## Account Analytics追跡（2026-11-05 07:02 UTC）

07:02:11 UTC時点のAccount Analyticsは3 D1合計236,671 rows read、2,264 rows written、残り4,763,329 rowsだった。06:48:10 UTCからaccount-wideで8 reads / 1 write増加したが、発生元は特定できない。report `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T070211Z.sanitized.json` SHA-256 `D98DC30A067BC1437159DE547FE83E6DEF37110BBA9060525525C236F749444A`。この照会はGraphQL AnalyticsのみでD1 SQLは実行していない。

07:02:10 UTCの値なし確認でも `CLOUDFLARE_WORKERS_READ_TOKEN` と `CLOUDFLARE_QUEUE_READ_TOKEN` はProcess/User/Machine scopeに見えなかった。値は表示していない。

## 本番securities検索計画の再確認（2026-11-05 07:12–07:14 UTC）

Account Analytics preflightは236,671 rows read / 2,264 rows written、残り4,763,329行で、100,000行reserveを維持していた。Production `EXPLAIN QUERY PLAN` は`MULTI-INDEX OR`を示し、`security_code`は`sqlite_autoindex_securities_1`、`security_code_display`は`idx_securities_code_display`を使用した。EXPLAIN自体は`meta.rows_read=0`、`rows_written=0`、`changed_db=false`。実際の7203検索は1行返却、`meta.rows_read=1`、`rows_written=0`、`changed_db=false`だった。SQL後のAnalytics集計値は同じままで、単発SQLの遅延計上の可能性があるため個別SQLの課金行数とは断定しない。

レポート: usage preflight `d1-daily-usage-20261105T0711Z.sanitized.json` SHA-256 `6994636B970BD02B774DEA10188921D29F0BCD6D1EA6392C1B23748A13BCE68D`、query plan `securities-explain-20261105T0713Z.sanitized.json` SHA-256 `84D01F5F2AEAFD4D3E720BC0D43171139C82100014BB4DC4373AD84CA86D2872`、lookup `securities-actual-query-20261105T0713Z.sanitized.json` SHA-256 `28C86C307DC3126FA5AADECACC7A8CD2DC39C3F1D21DE6FD840A01A1FE5F6C14`、usage postflight `d1-daily-usage-20261105T0714Z.sanitized.json` SHA-256 `DD3240740D0C53FEC3A2448D6A8538A9F0EB394A239546DBF3ED854E162F7C6D`。すべてC:/Codex/r2-d1-ownership-audit-20261105/に保存し、raw row bodyと認証値は保存していない。

この確認は`securities`検索経路の現在のindex利用を示すが、2026-11-03 dashboardに出た7.01M readsの過去原因や現在の全Worker経路を説明しない。配信Worker source mappingとQueue/Worker logsの確認は継続する。

## D1 scheduled runの未完了状態とread-only監査（2026-11-05 12:23–12:29 UTC）

Account AnalyticsのD1全体使用量は、dry-run前の12:23:09 UTCに327,223 rows read / 5,310 rows written（残り4,672,777行）だった。100,000行のread reserveを確保してから本番D1を照会した。dry-run後の12:29:34 UTCは328,509 rows read / 5,310 rows written（残り4,671,491行）で、readの増分1,286行は2回の643行query合計と一致する。ただしAccount Analyticsはアカウント全体の集計なので、この増分を個別queryに確定帰属しない。両レポートは`d1-daily-usage-20261105T122309Z.sanitized.json`（SHA-256 `474541316813FA13455615FB38C0F9A0409F5D6189BD849EE534B34CBEB0E8B8`）と`d1-daily-usage-postflight-20261105T1228Z.sanitized.json`（SHA-256 `8329C237B87D29D5648021A6000E3692B3676206E72442CC6D4B722284067331`）。

`scheduled-runs`移行phaseのdry-runは1 query / 643 rows readで、完了runのsource rows、planned R2 objects、書込みはいずれも0だった。別のoperational auditでは7 queries / 643 rows readで`scheduled_run_log`の643行を確認し、全件が`analysis/running`、`finished_at`なし、processed/failed合計0だった。開始時刻は`2026-10-04T19:49:05.538Z`から`2026-11-05T12:17:32.833Z`。レポートは`scheduled-runs-current-20261105T1225Z.report.json`（SHA-256 `8BC722FAB4DBD9D35E5E6DCF5D4E72A28C3F4DF05F699E8532C5910F3B715DD4`）と`operational-scheduled-runs-current-20261105T1224Z.report.json`（SHA-256 `C5691A5E9258F183174B0B12D846613264A74D9EC045E1FD04C7722665535845`）。

前回の638行から5行増えたが、作成元との因果関係は特定できていない。配信Workerの正確なsource identity、Queue consumer設定、Cron履歴との照合が済むまで、未完了行を完了履歴としてR2に移行したりD1から削除したりしない。このdry-runと監査ではD1/R2への書込みを行っていない。

## 本番Worker/Queueと定期run状態（2026-11-05 07:20–07:32 UTC）

Cloudflare Dashboardの読み取り表示では、`diview-irbank` version prefix `4981c1e0`が100% activeで、D1、R2、KV、Queue bindingsが見える一方、Durable Object rate-limiter bindingは見えなかった。QueueとDLQのrealtime/delayed backlog、consumer delay、concurrencyはいずれも0。Queueのsubscription画面の空表示はconsumer bindingがない証拠とは扱わない。Worker Observabilityの0 success/0 errorも選択した直近1時間だけであり、全期間のログや本番source identityを証明しない。Dashboardはmodule SHA/commitを表示しなかった。証跡 `worker-queue-dashboard-readback-20261105T0720Z.sanitized.json` SHA-256 `7D2556086B68A317C0D94FCCD46E871422FF72DA6967CE1E25F51D23C743A4C7`。

本番D1の`scheduled_run_log`は638行すべてが`analysis/running`で、`finished_at`なし、processed/failed合計0。範囲集計は各1,276行を読み、書込みなし。`backend_state.analysis_source`は存在しなかった。sanitized reportsは`scheduled-run-summary-20261105T0727Z.sanitized.json` SHA-256 `A0F4B3A855B684E78362709C841DDE700E273500BA9F9B369D019DFCD92E92BA`と`analysis-source-state-20261105T0729Z.sanitized.json` SHA-256 `4B5651477EE90C156D13BD222D307DAEF4D8CE4B9075BF7819BA26352105B59D`。

候補移行スクリプトは`finished_at IS NOT NULL`かつ365日以内のrunだけを完了履歴として選ぶため、これら638行は移行対象にならない。この除外を維持し、runの原因と保持方針が判明するまでD1から削除しない。確認した候補sourceには毎時のCron、`analysis_source`を使った全銘柄分析選択、完了時のstatus更新があるが、Dashboardから配信Workerの正確なcommit/moduleを特定できないため、反復runとの因果関係は未確定である。

Account Analyticsの07:32 UTC readbackは全3 DBで243,318 rows read / 2,414 rows written、残り4,756,682 rowsを示した。Diviewは243,029 / 1,687。07:24からのDiview +2,552 readsは2つのaggregate queryの`1276 × 2`と一致するが、集計遅延や同時trafficを除外できないため、個別原因の確定値とは扱わない。report `d1-daily-usage-20261105T0732Z.per-db.sanitized.json` SHA-256 `A08BB08C9BE22E03850FD8DF232D9B4E788A070544DAD8A92CBF2C32D00EDADE`。

この時点で`CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`は、設定済みとの連絡を受けた後もCodexのProcess/User/Machine環境から参照できなかった（値は出力していない）。配信module、正確なsource commit、直近1時間を超えるWorker logsは未確認。本番D1 write fence、DO counter handoff、definition seed/API parity、financial period差分も未完了であり、本番切替・削除の根拠にはしない。

## Cronイベント履歴とD1 runの照合（2026-11-05 07:51 UTC）

DashboardのCron event historyは、過去1週間に実行イベントなしと表示した。Worker設定画面にはCron triggerがあり、次回時刻として`Thu, 05 Nov 2026 08:17:00`が表示されたが、設定画面にtimezone labelはなかった。一方、D1の最新`scheduled_run_log`開始時刻は`2026-11-05T07:17:32.941Z`。これらの表示は整合しておらず、どの経路がD1 runを作ったかは判明していない。Dashboardには新規作成または改名WorkerのCron履歴表示に最大30分かかる場合があるとの注記があるが、Workerの作成/改名時刻は確認できていない。report `cron-events-dashboard-readback-20261105T0751Z.sanitized.json` SHA-256 `1FFE3BEFD22F7C6AA1E3357C81E3B16FF89EC9613132DD2B1BFCDF10752BFE00`。

Worker editorを開いた際、Previewが07:50:04 UTCに本番root URLへGETした。応答は画面に出ず、D1 rows-readへの影響も不明であるため、Account Analyticsの増分には帰属させない。この呼び出しはsource moduleを表示できず、配信source identityも解決しなかった。

## Production Worker・Queue・Cronの実測（2026-11-05 12:10–12:15 UTC）

Cloudflare Dashboardでは本番`diview-irbank` version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`がtraffic 100%で、表示上のdeploy messageは`Move watchlist ownership to device`、sourceはWranglerだった。確認できたbindingはAPP_DEFINITIONS KV、DB D1、FEEDBACK_EMAIL、MARKET_DATA R2、MARKET_PRICE_QUEUEの5件。Durable Object bindingは一覧に見えなかった。Dashboardにsource Git SHAまたはdeployed module bytesは表示されず、候補コードとの一致は未確認である。したがって、候補WorkerにあるDO rate limiterが本番で稼働しているとは判断できない。

価格QueueとDLQはbacklog/delayed backlogとも0だった。価格Queueのsubscription tabは未設定表示だったが、ここはevent subscriptionの画面であり、Queue consumer bindingがないことの証拠にはしない。consumerの実設定とWorker sourceの照合は残る。

DashboardのWorker overviewは直近24時間で430 invocations / 23 errorsを表示した。7日間のObservability aggregateは7,038 success / 1,271 errorsだったが、結果不完全の警告があり、error filterの可視ページ26行（info 25 / error 1）と集計値が一致しなかった。完全なエラー分類として扱わない。

Cron `17 * * * *`について、2026-11-05 14:18:04.648 JST（wall 31,889 ms、CPU 2,050 ms）と20:18:08.957 JST（wall 35,941 ms、CPU 2,050 ms）の2実行で`exceededCpu`を確認した。後者のtraceにはspansがなかった。定期分析・未完了runとの因果関係はsource mappingとD1状態を照合するまで未確定とする。

証跡は `C:/Codex/r2-d1-ownership-audit-20261105/worker-queue-observability-20261105T1215Z.sanitized.json`（SHA-256 `CD328081F37F149526DC97C726F9678E8A6B3B26E9CD82943F711CB1B1B56EBE`）。このDashboard readbackではD1/R2書込み、Worker deploy、Actions dispatchを行っていない。production切替・run履歴の移行/削除は、配信source/DO/Queue設定、Cron CPU超過原因、保持要件を解決するまで保留する。


## 2026-11-05 JST（13:19–13:28 UTC）財務期間監査v3と本番Worker/Queue再確認

移行監査の比較版を3へ更新し、D1のannual行がR2 annualにない場合、同じ期間・会計基準・連結区分を持つ通期行がR2 quarterlyまたはsemiannualにあれば「期間欠落」ではなく「source category mismatch」と分類する。移行監査テストは66/66成功した。新しいread-only dry-runはfinancial_periods 49,592行を照合し、D1 meta.rows_read=99,184（496 queries）、R2 source lookup 3,061回、49,571一致、4値差、2カテゴリ差、15期間欠落だった。42件にmillion_jpy→jpy変換を適用し、unit metadata incompleteは0。D1/R2 writes、planned migration objectsはいずれも0。report C:/Codex/r2-d1-ownership-audit-20261105/financial-periods-anomaly-trace-v3-20261105T1319Z.report.json のSHA-256は 45BEA50E510DF502403158D9015B670940F4C186B006E9C84FA53095A4F9E262。銘柄コードや財務数値は記録していない。

Account Analyticsはpreflight 13:19 UTCに427,935 reads、残り4,572,065行、postflight 13:28 UTCに508,061 reads / 5,512 writes、残り4,491,939行を示した。query-level metaの99,184 readsとaccount-wide差分は一致せず、集計遅延または他trafficの可能性を考慮して、差分をこの監査だけに帰属させない。

Cloudflare Dashboardのproduction settingsはdiview-irbank version prefix 4981c1e0、traffic 100%を表示した。bindingはAPP_DEFINITIONS KV、DB D1、FEEDBACK_EMAIL、MARKET_DATA R2、MARKET_PRICE_QUEUEで、Durable Object bindingは表示されなかった。DashboardからGit SHA/deployed module bytesは特定できない。価格Queueは22:26 JSTのmetricsでrealtime backlog 0、delayed backlog 0、consumer average delay 0秒。DLQの現在値はこの再確認では読み取れていないため、過去の0表示を最新値として扱わない。

CLOUDFLARE_WORKERS_READ_TOKENとCLOUDFLARE_QUEUE_READ_TOKENはユーザーから設定済みとの連絡後も、この実行環境のProcess/User/Machine scopeで見つからなかった（値は表示していない）。Dashboardで確認できたbindingとqueue metrics以外に、Cloudflare APIによるmodule/source・logs・current DLQのreadbackは未完了。環境を再起動してtokenが見える状態で再確認する。DO bindingとdeployed source identityを解決するまで、本番切替・移行write・D1削除は行わない。

## 2026-11-05 13:46 UTC 候補テストとWorkflow公開状態

候補Workerテストは130/130、移行・使用量・Workflow Pythonテストは88/88成功した。GitHub `main`のWorkflow一覧に`cloudflare-worker-ci.yml`と`d1-r2-migration.yml`はなく、候補作業ツリー内にのみ存在する。branch APIは`protected=false`を返した。Workflowの公開・実行、branch設定の変更はしていない。

ユーザーから設定済みとの連絡後の環境確認でも、`CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`はProcess/User/Machineのいずれにも見えなかった。値は表示していない。Cloudflare APIでのWorker source/modules・logs・Queue/DLQ readbackを続けるには、Codexを再起動してtokenが実行環境から見える必要がある。

## 2026-11-05 14:07 UTC Android通知候補のJDK 17・隔離AVD検証

候補worktree（`codex/r2-d1-ownership-20261104-final`, base `b3f3a7b75bc54d30ec468fc085922791f99e340b`）でMicrosoft OpenJDK 17.0.20.1を明示し、`testDebugUnitTest --rerun-tasks`を実行した。277件成功、失敗・error・skipは0。`assembleDebugAndroidTest --rerun-tasks`も成功した。監査専用AVD `DiviewR2Audit35-20261105`に候補app/test APKをインストールし、`NotificationDeliveryTest` 13/13件成功。ローカル価格欠落時に通知処理がnetwork fallbackしないこと、古い価格でpending通知を送らないことを確認した。built app APKとinstalled `base.apk`のSHA-256は双方 `F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351E`。通常/共有AVD・物理端末は使っていない。

同時点の環境確認ではWorker/Queue read tokenがProcess/User/Machine scopeのいずれにも見えず、値は表示していない。Worker source/modules・logs・Queue/DLQのCloudflare API readbackは未完了。本番binding/source identity、Cron/Queue運用、GitHub保護、データ保持・最終cutoverは別途未完了のまま。

## 2026-11-05 14:17 UTC D1参照境界とAndroid通知の追加検証

候補Worker `src/index.js`のD1 SQL呼び出しを再点検した。レート制限のDO初回seedで行う旧`api_request_log`のread-only lookup、support-feedbackのCRUD/保持処理がD1残置経路で、health応答はbinding有無のみを返す。市場データ検索・分析・定義・定期分析からのD1 SQLは候補ソース内に見つからない。Workerテスト130/130、移行/使用量/Workflow Pythonテスト88/88も成功済み。ただしローカル候補の検査であり、本番配信sourceとの一致を示すものではない。

AndroidはJDK 17の単体テスト277/277、instrumentation APK build成功。監査専用AVDでは`NotificationDeliveryTest` 13/13、`AssetDataRefreshTest` 24/24、`IncomeAdviceCatalogCacheTest` 1/1が成功した（計38/38）。対象には端末内価格のみで通知すること、価格欠落時にAPIへfallbackしないこと、古い価格によるpending通知を取り消すこと、アプリ起動の12時間ゲートと失敗後再試行が含まれる。built app APKとinstalled `base.apk`のSHA-256は `F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351E` で一致した。

Worker modules/source、Logs、Queue/DLQ、DO利用量の本番readbackは、Cloudflare read tokenが実行環境から参照できず未完了。候補コードのテスト・静的検査はproduction parityの代わりにならないため、source identityと本番bindingを照合するまで本番切替を保留する。

東証営業日カレンダーの回帰テストに記載した2026・2027年の現物市場休業日を、[JPX営業時間・休業日一覧](https://www.jpx.co.jp/corporate/about-jpx/calendar/index.html)の公開日程と照合した。祝日と振替日を含むテスト対象日は一致した。

## 2026-11-05 14:24 UTC Cloudflare token可視性とGitHub公開ゲート

Cloudflare tokenの値を出力せず環境scopeを調べたが、CLOUDFLARE_WORKERS_READ_TOKENとCLOUDFLARE_QUEUE_READ_TOKENはProcess/User/Machineすべてから見えなかった。Wrangler 4.136.1 whoamiもaccount IDを取得できず終了した。したがって配信Workerのmodule/version/source、QueueとDLQの現在backlog、Worker logsのAPI readbackは未完了であり、以前のDashboard表示を現在値として代用しない。Codexを完全終了して再起動し、tokenが読める環境でread-only確認を続ける。

GitHub APIでmainのSHA b3f3a7b75bc54d30ec468fc085922791f99e340b、protected=false、rulesets 0件を確認した。mainのworkflow一覧は9件で、候補のcloudflare-worker-ci.ymlとd1-r2-migration.ymlは公開されていない。候補のmigration workflowは保護mainと独立reviewer付きd1-r2-migration Environmentを要求する。Environmentの状態は接続済みGitHub APIで読めず、ローカルにghもなかった。branch protection、Environment、workflowの公開・dispatchは行っていない。公開前にbranch protectionとEnvironmentのreviewer/main限定設定を別途確認する。

候補Workerの静的再確認では、市場・分析・定義・scheduled analysisのD1 SQL callsiteは見つからなかった。残るD1経路は、DO初回seed時の旧rate-limit row lookup、feedback receipt/email outboxと保持処理、healthのbinding有無表示である。これは候補sourceの事実であり、production source identityが照合できるまでは本番挙動と同一とは扱わない。

## 2026-11-05 14:33 UTC レート制限DO bridge設定の追加

移行ランブックはd1 fence後のDurable Objects bridgeとしてwrangler.cutover-paused.tomlを参照していたが、正本・最終候補の両作業ツリーにファイルがなかった。rate-limit-do-bridge作業ツリーの設定を読み、各作業ツリーの現在のwrangler.tomlと比較した。Durable Object class/migration、KV、D1、R2、Queue、email bindingは一致し、Cronのみ空になっていることを確認した後、両方へ追加した。SHA-256は86CE26317150BA670E96584C9EFADA3C25EAE74ABE14A8E5E378681C031D6583。

最終候補からnode --check src/index.jsとrate-limit.test.mjs / rate-limit-handoff.test.mjsを実行し15/15成功した。wrangler deploy --dry-run --config wrangler.cutover-paused.tomlも成功し、期待するbinding一覧を出した。本番deployやD1 fence、DO trafficは実行していない。この設定はrunbook上のd1 fence後のbridgeであり、Cron停止だけを行うpre-fence deploymentに流用してはならない。pre-fence側はproductionで配信中の同一source/configurationを確認して作成する。

## 2026-11-05 14:35 UTC Account Analytics日次D1 readback

2026-11-05T14:34:57ZにAccount Analytics GraphQLだけを読み、D1 SQLは実行していない。3 database合計は535,404 rows read、5,747 rows written、5,000,000行の1日上限に対する残りは4,464,596行だった。databaseごとのreadは257、569、534,578、writeは31、2,835、2,881。集計のdatabase IDは記録せず、個別databaseへのtraffic原因の帰属もしない。移行処理前には毎回最新使用量と予算を確認する。

## 2026-11-05 14:44 UTC インカム空白月候補のR2/API現状照合

R2からmarket/yfinance/v2/catalog/current.jsonとmarket/yfinance/v2/derived/income_advice/current.jsonを読み、世代と内容を比較した。catalogはgeneration 2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e、4,391件。income adviceも同世代で3,203件、重複0、カタログ外コード0、全件のsource_catalog_generation一致。カタログのsecurity_typeと照合すると候補はequity 2,889、ETF 253、REIT 61だった。income advice objectは2026-11-04T20:40:32Z生成。R2へ書込みは行っていない。

公開WorkerのGET https://diview-irbank.omasa.workers.dev/api/v1/income-advice-candidates はHTTP 200、3,203件、重複コード0で、応答全体が同時点のR2 current payloadと一致した。公開GETはproduction Workerを1回呼び出すため、現在のrate-limit実装によるD1操作の可能性がある。Account Analyticsは14:34:57Zに535,404 reads / 5,747 writes、14:43:51Zに535,406 reads / 5,749 writesを示したが、その増分をこのGETだけに帰属させない。

候補生成コードではsecurity_type=etfをR2候補生成読取前に除外する。既存yfinance workflowが実行するunittest discoveryに含まれるtest_generate_income_advice.pyを追加し、全173テスト成功を確認した。このテストはETFを候補読取へ渡さず、equityのみを出力することをmockで検証し、R2とYahooへアクセスしない。現在のproduction R2 objectに残る253 ETFは、コード変更だけでは修正されない。既存の承認済みyfinance-r2手動生成・公開を別途実施した後にR2 readbackとWorker GETを再照合するまで、現物オブジェクトは更新待ちとする。


## 2026-11-06 JST（2026-11-05 UTC 17:16–17:29）本番source・Queue・rate-limit状態の更新

- 本番`diview-irbank`はdeployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`で100%配信中。Worker Scripts Readで取得した`index.js`は120,935 bytes / SHA-256 `262388d871931f4321bb3946937cd9faa2e72313297ef51ef60f4629bea4ad9a`で、commit `f2f2d831e9e82b0567e56127446706fed764e02d`からWrangler 4.157.0で作ったbundleと全byteが一致した。Cloudflare APIはGit SHAを返していないため、これは強いbundle/source mappingでありAPIによるcommit attestationではない。以前の16:54 UTC記録の120,934 bytes / SHA `20e7a40b...`はmultipart境界処理が末尾改行を除去した誤値で、本項の値が訂正値。
- active bindingはD1、R2、KV、email、Queueを含み、Durable Object rate limiterはまだ存在しない。現在の候補bundleは189,509 bytes / SHA-256 `48ce68c880688d0591a52d1342548f7c1f343d635aa358bd246eab0636c5faab`で本番とは異なる。候補の`RATE_LIMITER`参照を本番状態とみなさない。
- 本番sourceと一致するCron停止用pre-fence設定はローカルdry-run済み。Cronだけを空にし、bundle hashは配信中moduleと一致した。本番へはdeployしていない。候補側の`wrangler.cutover-paused.toml`はfence後のDO bridge設定であり、pre-fenceには使わない。
- 17:29:40 UTCのQueues Read取得では、`diview-market-prices`と`diview-market-prices-dlq`のbacklogは各0件・0 bytes・oldest timestamp 0だった。これは近似的な時点値なのでcutover直前に再確認する。
- 17:16:49 UTCのAccount Analyticsは日次読み取り582,602行、残り4,417,398行。60秒の`api_request_log` snapshotはactive bucket 0、fence trigger 0で、query planは`idx_api_request_log_window`を使った。read reserve 100,000行を含む予算条件を満たすが、いずれも時点値でありcutover時に再取得する。訂正版レポートでは当時未取得だった`rows_written`を除外した。
- 旧本番Workerのrate-limit処理はD1 `api_request_log`へUPSERTし、fetch経路はその失敗をcatchしない。fence適用からDO bridgeがtrafficを受けるまでに到達するrate-limited requestは、未処理例外となり5xxになるとコードから推定される。停止時間はdeploy順序と所要時間次第で、このhandoffはzero-downtimeではない。承認済みmaintenance windowを必要条件とし、bridgeの稼働確認後に続行する。production requestやfenceで再現したものではない。
- この確認で行ったCloudflare操作はWorker deployments/versionとQueue metricsのREST GETのみ。D1 fence/SQL、R2 read/write、Worker invocation/deploy、Actions dispatch、commit/pushは未実施。Worker logs/telemetry、呼出元一覧、DO counterの本番移行状況、GitHub main protectionとmigration Environmentは未確認のままである。

監査記録（C:\Codex\r2-d1-ownership-audit-20261105、いずれも値を含まないsanitized report）:
- `worker-source-exact-comparison-20261105T1722Z.sanitized.json` — SHA-256 `1e4f04f6effa806c42e96e51d1e082743df124655d4e100f026f79a3029cf332`。
- `worker-queue-live-read-audit-20261105T172942Z.sanitized.json` — SHA-256 `9570c70e21a780729d69fd75cf42e82fa882d60f22aa430868699a3b7e3098f6`。
- `rate-limit-cutover-readiness-20261105T1716Z.corrected.sanitized.json` — SHA-256 `9c4936176e6852323749268aa183abdd868f86b5a00e44fd638ecd7e46a3c2b3`。

詳細な切替・復旧手順は`data/irbank/cloudflare/docs/d1-r2-migration.md`を参照する。本番移行とdeploymentは最終承認待ち。

## 2026-11-06 JST（2026-11-05 UTC 17:51–17:55）読み取りトークン再確認・候補D1境界・GitHub Actions状態

### Cloudflare本番の再確認

ユーザーが設定した`CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`は、この実行のProcessおよびWindows User環境から参照できた。値は出力・保存していない。Workers Scripts Readでdeployment/settingsを、Queues Readでmetricsを取得した。2026-11-05 17:51:14 UTC時点で`diview-irbank`はdeployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100% active。bindingはD1 `DB`、R2 `MARKET_DATA`、KV `APP_DEFINITIONS`、Queue `MARKET_PRICE_QUEUE`、email関連で、Durable Object rate limiter bindingはない。17:22 UTCに配信moduleとhistorical sourceのbundle byte一致を確認済みであり、今回のactive version IDは変わっていない。

`diview-market-prices`と`diview-market-prices-dlq`はどちらもbacklog 0 messages / 0 bytes / oldest timestamp 0だった。これは時点値であり、cutover直前に再取得する。Cloudflareの操作はGETのみ。D1 SQL、R2 read/write、Queue acknowledgement/purge、Worker invocation/deploymentは行っていない。

### 候補WorkerのD1残置範囲

最終候補branch `codex/r2-d1-ownership-20261104-final` の`src/`を静的検索した。レート制限のrequest counter本体はDurable Object内のSQLiteを使う。D1は、DO初回seedのimport window内に旧`api_request_log`からbucketを一度読む経路として残る。別系統では、`support_feedback`の受付・重複防止・管理読取/削除・90日保持と、`support_feedback_email`の送信lease/retry状態にD1を使う。health routeはD1 bindingの有無を返すだけでSQLを発行しない。銘柄検索・市場カタログ・分析・定期分析・定義取得の直接D1 SQL callsiteは候補ソースで見つからない。DO内SQLiteの操作はD1利用量には含めない。

これは候補コードの確認であり、本番の現状を表さない。本番は上記のhistorical source bundleを引き続き100%配信し、D1 bindingと旧D1 rate limiterを保持している。切替後に本番でも候補と同じ参照境界になるかは、seed/readback/API検証後に確認する。

### GitHub Actionsの現状と独立した既存Workflow障害

2026-11-05 17:55:01 UTCのGitHub読み取りでは、`main`は`b3f3a7b75bc54d30ec468fc085922791f99e340b`、API上`protected=false`、repository rulesets 0件。`main`には9 workflowsがあり、候補の`cloudflare-worker-ci.yml`と`d1-r2-migration.yml`は存在しない。Environment一覧は`IRBANK_API_KEY`のみで、`d1-r2-migration`はない。そのため今のGitHub上では候補migration workflowを実行できない。candidate workflowが要求するmain保護、独立reviewer必須・self-review禁止、main限定Environment、および専用のD1/Analytics/R2 credentialsを設定する必要がある。設定変更・workflow dispatchはしていない。

既存の日次公開Workflowも別途確認した。IRBANK run #17はusage制限内refresh stepで失敗し、前回取得したjob logには`/v1/usage` HTTP 403、universe 4,576件はfresh、`api_requests=0`と記録されていた。YFinance run #388は4,420成功・156失敗（HTTP 404が155、401が1）で、R2 generationは公開後に失敗終了している。期待された2026-11-05の平日schedule runは17:55 UTC時点で見つからない。これら既存Workflowは変更・再実行していないため、別途原因とR2鮮度を調べる。

sanitized evidence（Git外）:
- Cloudflare Worker/Queue readback `C:\Codex\r2-d1-ownership-audit-20261105\worker-queue-live-read-audit-20261105T175114Z.sanitized.json` — SHA-256 `b138f5080112a4f937e449e817b1fd05e8e9deec62f53aad4ee592b69d5ef0fc`。
- GitHub readiness `C:\Codex\r2-d1-ownership-audit-20261105\github-actions-readiness-20261105T175501Z.sanitized.json` — SHA-256 `45dc8237684d99919d8cc8c47f0d665d4dd37d48eff4a02671aa5fb98189870c`。

本番D1/R2変更、Worker deploy、GitHub設定変更、Actions dispatch、commit/pushは行っていない。

## 2026-11-06 JST（2026-11-05 UTC 18:02–18:06）D1使用量・R2 current・空白月候補API再確認

Account Analyticsのpreflight（18:02:47 UTC）はUTC日付2026-11-05で3 D1合計586,744 rows read / 6,246 rows written、残り4,413,256行を示した。API確認後の18:06:21 UTCも同じ値だった。D1 `rows_read`の100,000行reserveを確保してから公開Workerを1回GETした。Analyticsはaccount-wide/database aggregateで遅延・同時trafficがあり得るため、同値または差分を当該requestへ帰属させない。

R2を直接読み、catalog currentはgeneration `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`、market data date `2026-11-04`、4,391件。income advice currentも同世代で3,203件、generated_at `2026-11-04T20:40:32.214274Z`。重複0、catalog外0、source generation不一致0。分類はequity 2,889、ETF 253、REIT 61。候補コードがETFを生成前に除外するルールを含むため、現在のR2 objectは次回生成時の候補ルールと253件分一致しない。現行データ全体に対する候補再計算は行っていないため、その253件以外の完全な新旧差分は未確定である。既存R2 objectやAPI/AndroidキャッシュをD1移行対象にせず、R2を更新しない。

公開Worker `GET /api/v1/income-advice-candidates` はHTTP 200、3,203件・重複0で、JSON全体が直前に読み戻したR2 currentと一致した。active production sourceは旧D1 rate-limit処理を通るため、この1回のGETはその経路を実行した。Analytics pre/postは同値だが、個別requestのD1行数は計測できておらず、0行とは断定しない。production Worker/APIはD1 rate limiterからまだ切り替わっていない。

sanitized reports（Git外）:
- R2 object readback `C:\Codex\r2-d1-ownership-audit-20261105\r2-income-advice-current-readback-20261105T180453Z.sanitized.json` — SHA-256 `E7DAB40E32AA4DD5452765D562508EEACA4A6779360C1841FB6649053016CBC2`。
- R2/API/Analytics pre-post readback `C:\Codex\r2-d1-ownership-audit-20261105\income-advice-worker-live-readback-20261105T180621Z.sanitized.json` — SHA-256 `2125ECA3F8AB11B25ED3B1109E2EAA2E29A5599821EBBA84A5C5E45FF73A28EC`。

この確認ではAccount Analytics GraphQL、R2 GetObject、公開Worker GETのみ。D1 SQLを直接発行せず、R2 write、Worker deployment、Actions dispatch、Queue変更は行っていない。既存オブジェクトと現在の生成ルールの差を解消する公開更新は別途承認待ち。

## 2026-11-06 JST（2026-11-05 UTC 18:16）Workers/Queues readback

ユーザーが設定した`CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`はProcess環境から参照できた。値は表示・保存していない。2026-11-05 18:16:03 UTCのCloudflare REST GETでは、`diview-irbank` deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100% active。bindingsはD1 `DB`、R2 `MARKET_DATA`、KV `APP_DEFINITIONS`、Queue `MARKET_PRICE_QUEUE`、emailで、Durable Object rate limiter bindingはない。Worker source/deploymentは今回も旧D1-backed版のままで、候補Workerへの切替は未実施。

`diview-market-prices`と`diview-market-prices-dlq`はいずれもbacklog 0 messages / 0 bytes / oldest timestamp 0。Queue metricsはbest-effortの時点値であり、cutover直前に再取得する。Worker invocation logs/tracesは今回の権限では未取得。

sanitized report（Git外）`C:\Codex\r2-d1-ownership-audit-20261105\worker-queue-live-read-audit-20261105T181604Z.sanitized.json` — SHA-256 `37044AA71A2F3801B09C2952DA6DD0B3B7F444AF8B0AA6B71195BF6F5762DE8C`。

今回のCloudflare操作はdeployment/settings/Queue metricsのREST GETのみ。D1 SQL、R2 read/write、Queue acknowledgement/purge、Worker invocation/deployment、GitHub Actions dispatch、commit/pushは行っていない。

## 2026-11-06 JST（2026-11-05 UTC 18:21）既存R2更新Workflowの実行状況

GitHub Actions APIの最新20 runを18:21:26 UTCにread-only確認した。最新のYFinance schedule runは#388で、2026-11-04 19:44:42 UTC開始・20:40:53 UTC終了、failure。mainの現行workflow定義は平日`12:17 UTC`（`17 12 * * 1-5`）だが、前回run自体が宣言cronより後に開始しているため、18:21 UTC時点で11/05 runが見つからない事実だけで「永久に未実行」と断定しない。R2 catalogの最新readbackはmarket date 2026-11-04であり、新しいYFinance runとR2 readbackを確認するまで市場データの鮮度は未確認として切替条件に残す。

IRBANK schedule runは#17（2026-11-04 22:38:05 UTC、failure）。Workflow dispatch、settings変更、repository writeは行っていない。sanitized report（Git外）`C:\Codex\r2-d1-ownership-audit-20261105\github-actions-data-freshness-20261105T182126Z.sanitized.json` — SHA-256 `C25405FAB294CF1266EF70C9B4264012322A2601BE691CD963527A90BD48DCC1`。

## 2026-11-06 JST（2026-11-05 UTC 18:43–18:59）D1使用量・income-advice全件再計算

Account Analyticsの日次集計はUTC 2026-11-05、3つのD1合計で590,802 rows read / 6,356 rows written、500万行上限まで4,409,198 rows remainingだった。Account Analyticsの集計だけを読み、D1 SQLは発行していない。

最終候補worktreeの現行`generate_income_advice.build_catalog()`を使い、R2のcatalog currentと保存済みfinancial/dividend sourceのみから全3,938銘柄を再計算した。処理時間は1,737.711秒。R2 GetObject 52,334回、ListObjects 3,938ページ、読取失敗0。YFinance、公開Worker、Actionsは呼び出さず、D1 SQL・R2 PUT/DELETEも0。

既存`income_advice/current.json`は3,203件（equity 2,889、ETF 253、REIT 61、重複0）。再計算は2,971件（equity 2,909、REIT 61、`listed_security` 1、重複0）だった。既存から除外された253件はすべてETF、追加は21件（equity 20、`listed_security` 1）、共通2,950件。共通候補2,950件すべてで`computed_at`が異なり、さらに`monthly_estimate_per_100_shares`と`observed_months`が各14件、score metrics/status/imputed pointsが各1件異なる。再計算時の除外数はETF 453、配当履歴なし768、継続配当月なし199、読取失敗0。

追加候補の`1773`はcatalogの`jquants_product_category=021`、`security_type=listed_security`だった。アプリ内分類表は011/013/014をequity/REIT/ETFに対応付け、その他を`listed_security`へfallbackする。021の扱いと、この候補をincome-advice対象に含めるかは未決なので、R2公開前に仕様判断を残す。

2026-11-05 18:59:17 UTCのR2再読取で、catalog generation `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`、market date `2026-11-04`、advice `generated_at=2026-11-04T20:40:32.214274Z`とitem hashが比較開始時と一致した。financial_revisionはschema 1の有効な値で`updated_at=2026-11-04T20:36:33Z`。ただし比較開始前のrevisionを保存していないため、約29分の全処理中にfinancial source群が不変だったことまでは証明できない。比較時に使ったcatalog/advice currentが終了時も同一であることは確認済み。

現在のR2 advice objectはこのworktreeの現行generatorのETF除外規則・再計算結果と一致しない。今回R2を公開していない。候補1773の分類判断、次回R2更新の承認、更新後のR2/API/Androidキャッシュ照合は未完了。

sanitized evidence（Git外、C:/Codex/r2-d1-ownership-audit-20261105）:
- D1 daily usage `d1-daily-usage-20261105T184323Z.sanitized.json` — SHA-256 `70E6CE9134C9D67E8667F576F7332FA2D21936C456E28E267AFCEA750E7B7A0D`。
- 全件再計算 `income-advice-generator-readonly-comparison-20261105T185731Z.sanitized.json` — SHA-256 `60E73831D2543801DE0B6EE358EC8EE26AA7C7D46895D0E550B3AB160CEE45AE`。
- R2 post-readback `income-advice-post-comparison-readback-20261105T1900Z.sanitized.json` — SHA-256 `03C5BBB1459EB78332059E2C793AB1BA1EB42034DD25E73E940F140537FA214B`。

## 2026-11-06 JST（2026-11-05 UTC 19:29–19:51）海外ETF分類・本番readback・候補dry-run再確認

前節で未確定としていたJ-Quants Product Category 021の意味は、公式仕様でForeign Stocksと確認した（[J-Quants Product category codes and names](https://jpx-jquants.com/en/spec/eq-master/product-category)）。未決なのは意味ではなく、外国普通株をincome-advice候補に含めるかである。今回の変更では対象を広げず、国内ETF 014と海外ETF 023だけをETFとして分類する。021、022、024は従来どおりlisted_securityへfallbackする。海外ETFはR2 catalogに31件あり、現行ラベルはすべてlisted_securityだが、候補生成・通常catalog生成・復元・公開ではproduct category 023も見て除外する。

2026-11-05 19:51:51 UTCの全件dry-runでは、4,391 catalog rowsから484 ETFs（国内453・海外category 023が31）を除外し、3,907候補を処理して2,971件を生成した。現行R2は3,203件で、253件除外・21件追加、共通2,950件にfield差分があった。R2 GetObject 52,214回、ListObjects pages 3,907、失敗0、785.963秒。開始と終了でcatalog current、income-advice current、financial revisionのSHA-256が一致した。revision管理外の個々のsource objectの不変までは証明しない。R2 PUT/DELETE、D1 SQL、Worker/YFinance呼出し、Actions dispatchは0。R2は更新していない。

2026-11-05 19:29:56 UTCのCloudflare REST GETでは、diview-irbank deployment 7731b2d6-75e8-4b13-8985-9f47a4960f95、version 4981c1e0-e337-4392-9f5b-909b52c5bc0c（84）が100%配信中。D1/R2/KV/Queue bindingsはあるが、Durable Object rate-limiter bindingはない。価格Queueはretry 3、max concurrency 1、retention 24時間、DLQはdiview-market-prices-dlq。QueueとDLQのbacklogはいずれも0件・0 bytes。metricsはbest-effortの時点値であり、cutover時には再確認する。現production WorkerはD1-backed rate limiterのままである。

UTC 2026-11-05のAccount Analyticsは3 DB合計594,904 rows read / 6,507 rows written、残り4,405,096 rows。100,000行の予備を満たし、D1 SQLは発行していない。dry-run前のGitHub run listに11/05 YFinance schedule runはなく、最新YFinance #388（11/04開始）はfailure。R2 market dateは2026-11-04のため、freshnessをcutover条件に残す。

sanitized reports（Git外、C:\Codex\r2-d1-ownership-audit-20261106）:
- Worker/Queue worker-queue-live-read-audit-20261105T192958Z.sanitized.json — SHA-256 76FE142E0CEAACFB75FBC62B8DA28B202208430730E84AF263335F308B351278。
- D1 usage d1-daily-usage-20261105T193448Z.sanitized.json — SHA-256 A357D706645710B28D1F1E1D41488D736BE2E19B19C8080A5A655228A5AEE8C2。
- Full dry-run income-advice-generator-readonly-comparison-20261105T195151Z.sanitized.json — SHA-256 590DB67F33B359DD6463C51582E90C56024EEFF884BF7243C23793F51C326F3。

#### Android instrumentation and scheduled R2 publisher status (2026-11-05 20:18 UTC)
The JDK 17 debug candidate completed Android instrumentation on isolated AVD emulator-5580. The JUnit XML records 141 cases, zero failures/errors, and one skipped optional live-market-read test: MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults. After Gradle removed the app, the same built APK was reinstalled on the AVD and MainActivity resumed. Built APK and installed base.apk SHA-256 both equal F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351E. This is emulator evidence only.
GitHub YFinance scheduled run #389 on main SHA b3f3a7b75bc54d30ec468fc085922791f99e340b was still in_progress at 2026-11-05 20:18 UTC. Its v2-daily-sync publication step was active, so no R2 readback was made during the write window. Wait for the existing run to finish, then read back R2 generation and market date. No rerun or manual dispatch: https://github.com/masacgt/Diview/actions/runs/42098641865
The same JUnit XML confirms the candidate cases passed for targeted daily price refresh, offline notification from the local price catalog, cancellation of a stale queued alert, and cached income-advice display while offline.

At 2026-11-05 20:30 UTC, GitHub still reported scheduled run #389 in progress. Its last available log timestamp was 20:19:36 UTC at 2,600/4,576 securities (2,516 success, 84 failed); the snapshot had not advanced by 20:30. At the run's exact main SHA, sync_v2.py publishes R2 objects after collecting per-security outcomes and then exits 1 when failures exist. Therefore, a failed run may still have advanced R2 and must be followed by readback.
The GitHub integration returned 403 for main branch protection and does not support the repository Environment endpoint. These access limits leave branch protections and independent deployment approval unverified; do not dispatch the migration workflow until confirmed.

## 2026-11-06 JST（2026-11-05 UTC 21:02–21:40）read token再確認・財務期間照合・候補規則修正

設定済みのCloudflare read tokenは環境変数名の存在だけを確認し、値は表示・保存していない。Cloudflare REST GETでWorker scripts/deployments/version detailsとQueue metadata/metricsを取得した。配信Workerはversion 84（`4981c1e0-e337-4392-9f5b-909b52c5bc0c`）が100%。Version detailsのbindingsにD1/R2/KV/Queue/emailがあり、Durable Object rate limiterはまだbindingされていない。価格QueueとDLQはともにbacklog 0 messages / 0 bytes / oldest timestamp 0で、best-effortの時点値である。Deployment list単体にはbindingsが返らず、Version detailsを追加GETして確定した。暫定readbackは修正版で置き換えた。

UTC 2026-11-05 21:28:24のAccount Analyticsは603,217 rows read / 6,890 rows written、残り4,396,783行。100,000行のreserveを確保したうえで、21:34 UTCに対象2銘柄の年次financial_periodsのみを2回SELECTし、query metadataの合計は31 rows_readだった。Analyticsは処理後に再取得しておらず、メタデータの遅延があるため、開始時集計に31を足して処理後使用量とはみなさない。D1 write、R2 writeは0。

R2 `overview.json`の期間欠落17件をYFinance v2の財務ファイルも含めて照合した。2件（2025/03、2026/03）はYFinance v2側に存在し、Workerの分析入力が参照できる。残る15件は2011/10から2025/10までの年次行で、IRBANK overviewとYFinance財務ファイルのどちらにもない。対象の銘柄名はR2 profileと一致するが、発行会社公式FAQは決算期を3月31日とし、公式IR資料も2018–2026年の3月期、過年度の決算説明資料も3月期を示す（[公式FAQ](https://www.kuze.co.jp/faq/)、[有価証券報告書等](https://www.kuze.co.jp/ir/library/securities.html)、[公式IR資料](https://www.kuze.co.jp/ir/library/pdf/20161214.pdf)）。この15件は現在のR2 servingデータへ足さず、出所確認までD1全行archive内で保持する。元監査の4つの値差異は別途未解決で、R2を上書きする根拠にしない。

現行income-advice候補は既存の`category=jp`・`currency=JPY`条件を維持する。候補ブランチに追加されていたETF一律除外はこの移行仕様に含まれないため取り消した。従って、以前のETF除外時点で得た2,971件のgenerator dry-runは現行コードの結果ではない。公開R2 advice 3,201件は更新していない。国内ETF・REIT等の現在の候補適格性を変える新しいルールは追加していない。J-Quantsカテゴリ021の外国普通株をincome-adviceへ含めるかは、オフライン価格通知の市場選択とは別に判断する。

GitHub `main`はSHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、branch metadata `protected=false`。`cloudflare-worker-ci.yml`と`d1-r2-migration.yml`はmain上で404となり未公開。`d1-r2-migration` Environmentへの個別GETは404だったため、一覧APIの再確認待ちとしていた。移行Workflowのpreflightはmain保護と専用Environmentが確認できない限りfail-closedのままにする。

### GitHub設定の最新読み取り（2026-11-05 UTC 21:45）

匿名REST GETで`main`の`protected=false`、repository rulesets 0件を再確認した。`GET /repos/masacgt/Diview/environments`は環境名`IRBANK_API_KEY`のみを返し、protection rulesは0件だった。`d1-r2-migration` Environmentは存在しない。これはEnvironment secretの有無を確認した結果ではない。専用Environmentと独立reviewerが未設定のため、migration workflowは引き続きdispatchしない。直前の`d1-r2-migration`個別GETの404は、一覧GETの結果と整合する。

sanitized reports（Git外、`C:\Codex\r2-d1-ownership-audit-20261106`）:
- Worker/Queue corrected readback `worker-queue-live-read-audit-20261105T212218Z.corrected.sanitized.json` — SHA-256 `56810AE04F3C5C06A3500BCA75CEFAEE4A0208141D1ADCC69E9990919F58E8BE`。
- R2 IRBANK period keys `financial-period-keys-latest-readback.sanitized.json` — SHA-256 `A32C363A437CC17D7ECC274AEDBBD1F1E05F19166AF4891915C1E082EB15CB26`。
- R2 YFinance period coverage `yfinance-financial-period-coverage.sanitized.json` — SHA-256 `9C2AD142FC83488790AE4FCEAC99BC702DFFE40B0834DAD6B6048DB4230D428D`。
- Targeted D1/R2 identity audit `financial-period-d1-targeted-identity-audit.sanitized.json` — SHA-256 `57C3D152F58D2D1B64AF997D57A03524A525BDF6EE907A6CA304A1359EE9FEF4`。
- D1 usage snapshot `d1-daily-usage-20261105T210212Z.sanitized.json` — SHA-256 `C129BC08A4AD977F9EA0E0EB1AC01C52A88D62E2591CA5153CDF213F48D7B928`。

Income-advice関連を含むYFinance Python suiteは174/174 testsで成功した。GitHub Actions dispatch、production R2 write、Worker deployment、D1 fence、Queue mutationは行っていない。main protection/Environmentの有効化、DO rate-limit handoff、Worker invocation telemetry、production切替前後のD1使用量比較、物理Android端末検証、残る4値差異の分類、1773方針は未完了。

## 2026-11-06 JST（2026-11-05 UTC 22:21–22:26）income-advice再計算と現在R2 route照合

R2 current generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`（market date `2026-11-05`）から現行`generate_income_advice.build_catalog()`を全件実行した。R2 currentは3,201件（equity 2,887 / ETF 253 / REIT 61）、再計算は3,223件（equity 2,908 / ETF 253 / listed_security 1 / REIT 61）、候補追加22件・除外0件、重複0。共通3,201件は`computed_at`全件、monthly estimateとobserved months各21件、score-related fields 1件に差があった。catalog current、advice current、financial revisionは処理開始と終了で一致したが、すべての証券current/dividend source objectの個別不変までは証明していない。読取に936.146秒、R2 GetObject 54,150回、ListObjects pages 4,391。D1 SQL、R2 PUT/DELETE、Worker/YFinance呼出し、Actions dispatchは0。sanitized report `income-advice-current-generator-readonly-20261105T222105Z.sanitized.json` SHA-256 `9B8D9C173DE62A07324738F4E080579A6AEA69D2885C934DCAFA25A1973A4D32`。

対象候補の追加読取では`jquants_product_category=021`、`security_type=listed_security`、`category=jp`、`currency=JPY`で、既存条件からcandidateを再生成でき、現行adviceには未掲載だった。従って外国普通株専用の除外ルールは追加せず、既存JP/JPY条件を維持する。R2 currentとの差分22件にはこの候補が含まれる。これは候補資格を確認した結果であって、本番R2への公開承認ではない。対象別readbackはR2 GetObject 11回・ListObjects 1 page、D1/R2 write 0。

R2 current objectを1回GETし、現在のWorker `fetch` routeをNode上のローカル環境で呼び出した。HTTP 200、3,201件、JSON payloadはR2 objectと完全一致、rate-limitはローカルstub、D1 SQL 0、R2 write 0。本番Worker endpointへの今回のGETではなく、deployed sourceのparityを証明するものではない。sanitized report `income-advice-worker-route-parity-20261105T222640Z.sanitized.json` SHA-256 `B54C301386BC3B5D6F53A6813856CD9A0F1FD68AC971C3CC77D29DB4E5FC9BD9`。

Wrangler 4.157.0のreadback configではDurable Objects local / R2 remoteを確認したが、remote binding開始時に現在のread-only API tokenでは認証できなかった。production Workerを起動せず、R2 objectをNode harnessへ渡すlocal route検証に切り替えた。R2の現行3,201件を3,223件へ更新するwrite、Production API request、Worker deployはしていない。22件の候補追加と共通21件のデータ差異は、公開前にsource provenanceを確認する。

## 2026-11-06 JST（2026-11-05 UTC 22:30）candidate WorkerのD1参照範囲

`codex/r2-d1-ownership-20261104-final`の現行`data/irbank/cloudflare/src/`を静的検索した。市場検索、カタログ、銘柄詳細、分析、定期分析、定義ハンドラーにD1 SQLは見つからない。残るWorker D1処理は、新規Durable Object counterがhandoff期間内に行う`api_request_log`の一回限りread、および保持する`support_feedback`と`support_feedback_email`の受付・管理・再試行・90日cleanupである。health responseの`d1: !!env.DB`はbinding有無を返すだけでSQLを実行しない。これは候補sourceの静的確認で、本番配信version 84は旧D1 limiterのままなので、本番からD1 readがなくなった証拠ではない。


## 2026-11-06 JST（2026-11-05 UTC 22:18–22:54）Cron定期分析の終了条件監査

ユーザーが設定したWorkers ReadとQueues Read tokenを値を表示せずに読み取り監査で使用した。本番のdiview-irbankはversion 84、traffic 100%、Cronは17 * * * *。Durable Object bindingはなく、価格QueueとDLQの読み取り時点のbacklogはいずれも0。UTC 2026-11-05のAccount Analyticsは22:38:16時点で3 DB合計607,398 rows read / 7,090 rows written、日次上限まで4,392,602 rowsだった。この集計はaccount/database単位であり、Workerや呼出元には帰属できない。

Cloudflare Observability Eventsに2026-11-05 22:18:03.249 UTC（JST 11月6日07:18:03）のCron errorを確認した。結果はexceededCpu、wall time 30,494 ms、CPU time 2,050 ms。version 84のtraceにspanはなく、表示ログはCron式のみだった。直後の時間帯に対応する本番D1 scheduled_run_logをread-onlyで引き、analysis、started_at 2026-11-05T22:17:32.836Z、finished_atなし、status running、processed/failed 0の1行を確認した。query metadata rows_readは2、書込み0。EXPLAIN QUERY PLANはidx_scheduled_run_log_startedを使用しrows_read 0。backend_state.analysis_sourceは存在せず、rows_read 0。D1 SQLは計3回、metadata rows_read合計2、書込み0。未完了runは完了履歴として移行せず、D1から削除しない。

本番に対応するsource f2f2d831e9e82b0567e56127446706fed764e02dでは、Cron handlerがrunScheduledAnalysisのPromiseをctx.waitUntilへ登録して即時にreturnしていた。Cloudflareの[Scheduled Handler](https://developers.cloudflare.com/workers/runtime-apis/handlers/scheduled/)はscheduled()から返されたPromiseを最大15分待つ一方、[Workers limits](https://developers.cloudflare.com/workers/platform/limits/)ではwaitUntilの延長はhandler応答後最大30秒と説明される。観測された約30秒の実行と未完了行はこの実装と整合するが、traceや詳細errorがなく、今回の停止原因の確定証拠ではない。配信中の本番version 84は変更していない。

候補および正本の作業ツリーでは、scheduled handlerが定期分析Promiseを直接返すよう修正し、メール再試行と90日超過問い合わせ削除だけをwaitUntilに残した。Worker全テストは候補作業ツリー131/131、正本作業ツリー71/71で成功した。これらのfixtureは1銘柄を使うため、4,000件超を処理する本番相当の実行時間や15分以内の完了は未検証である。承認済み切替の前に、大量処理の時間を測るか、超過する場合は再開可能な分割処理を設計・検証する。

監査reportはGit外のC:\Codex\r2-d1-ownership-audit-20261106\scheduled-cron-d1-read-audit-20261105T2238Z.sanitized.json（SHA-256 912AE3F0487CB24FF3233D4184185F9B0C8E10B36F39BEEFEE5D42D528267091）。本監査によるWorker invocation、D1/R2 write、Queue mutation、Actions dispatch、deploy、物理端末installはいずれも0。


## 2026-11-06 JST（2026-11-05 UTC 23:15）定期分析のR2 subrequest容量

Cloudflareの[Workers limits](https://developers.cloudflare.com/workers/platform/limits/)では、有料Workersのsubrequestは既定10,000回/ invocationで、Wranglerのlimits.subrequests設定により最大10,000,000回まで変更できる。R2・KV・D1など内部サービスへの要求も上限に含まれる。今回の候補設定は`wrangler.toml`と`wrangler.cutover-paused.toml`の両方で250,000回に設定した。

候補Workerをローカルのin-memory R2 fixtureで、カタログ4,391銘柄、財務object 36,897件、配当object 45,646件、移行済み分析履歴38,156件の規模で実行した。4,391件を処理、失敗0、source tag更新あり、分析経路のD1 statement 0。計測したR2 subrequestは173,460回（GET 155,838、PUT 8,790、LIST 8,826、HEAD 6、DELETE 0）で、設定上限まで76,540回の余裕がある。ローカル経過1,388msはin-memory mockの値であり、Cloudflare上のレイテンシ・Cron完了時間・CPU使用量を示さない。

両設定のWrangler deploy dry-runは成功し、設定を含むbundleを検証した。Worker testでは両設定のsubrequest上限が173,460回以上であることを固定の測定基準として確認する。合成試験は呼出し数とD1非依存を示すもので、本番Cron実行や15分以内の完了を証明しない。production Workerは未変更であり、本番切替前に実環境のwall time・CPU・subrequest countを観測し、失敗時に再開できるかを確認する。

sanitized report（Git外）: C:\Codex\r2-d1-ownership-audit-20261106\analysis-scale-probe-20261105T2315Z.sanitized.json、SHA-256 A24C35D6063F568FD97FE00D999EE0C9B7564FEDED59D13322A94554FDE224ED。


### 検証

容量基準更新後の全Worker suiteを`node --test`で実行し、候補作業ツリー133/133、正本作業ツリー72/72が成功した。候補の総数にはWorker SQL validationとCJS/MJSテストを含む。


## 2026-11-06 JST（2026-11-05 UTC 23:35–23:38）Cloudflare read-only状態の再確認

Workers Read tokenを使ったCloudflare API GETで、diview-irbankのdeployment 7731b2d6-75e8-4b13-8985-9f47a4960f95はversion 4981c1e0-e337-4392-9f5b-909b52c5bc0c（84）が100%配信中と再確認した。version resources.bindingsにはD1、R2、KV、Queue、emailがあり、Durable Objects rate limiterはまだbindingされていない。Queues Read tokenによるQueue一覧・metrics GETでは、diview-market-pricesとDLQはいずれもbacklog 0 messages / 0 bytes、oldest timestamp 0だった。Queue metricsはbest-effortのpoint-in-time値である（[Cloudflare Get Queue Metrics](https://developers.cloudflare.com/api/resources/queues/methods/get_metrics/)）。Queueはmax_retries 3、max_concurrency 1、retention 24時間。

2026-11-05 UTCのAccount Analyticsは23:35:47 UTC時点で3 DB合計611,542 rows read / 7,289 rows written、日次上限5,000,000まで4,388,458 rows。100,000行reserveを満たす。これはAccount Analyticsのaccount-wide集計で、Worker個別の使用量ではない。分析はCloudflare GraphQLのみで、D1 SQL 0。これらは読み取り確認のみで、Worker invocation、D1/R2 write、Queue mutation、deploy、Actions dispatchは0。

sanitized reports（Git外、C:\Codex\r2-d1-ownership-audit-20261106）:
- Worker/Queue `worker-queue-live-read-audit-20261105T233753Z.sanitized.json` — SHA-256 `5FBB742EFD0BDD2D11CA948E6855B5944F18E9E41FE99FA8F3A5F6C039BB8C28`。
- D1 daily usage `d1-daily-usage-20261105T233547Z.sanitized.json` — SHA-256 `E40E6864707AB0A73BF19ED72907213AB488B2FDAFA87B763D83BE16310E5F0E`。


## 2026-11-06 JST（2026-11-05 UTC 23:48–23:52）候補CLIのanalysis-current bounded dry-run

candidate worktree（HEAD b3f3a7b75bc54d30ec468fc085922791f99e340b）にある migrate_d1_to_r2.py（SHA-256 26A53CC08B8B5995ADD0570C3FABE3F7EEBB9EAF21C8A742E54270F005F3888D）を使い、analysis-currentをmax-rows 100、batch-size 25、read-reserve-rows 4,200,000で実行した。dry-runはSELECT専用D1 readerとR2 GET経路を使い、固有のGit外チェックポイントに出力した。23:48:50 UTCのAccount Analytics preflightは611,542/5,000,000行、残り4,388,458行で、実行後にも最低4,200,000行を残す条件を満たした。

意図した100行上限で停止し、結果はstatus=incomplete、complete=false。4 SELECTで100 D1行を読み、R2移行候補100 object、conflict 0、R2 write/delete 0だった。23:52:01 UTCのAccount Analyticsは611,642行、残り4,388,358行を報告し、dry-runの100行と同じ増分だった。集計はアカウント全体であり、この増分を本処理だけに確定帰属しない。

この結果は先頭rowid範囲のbounded dry-runであり、全行の一致、全履歴の完全性、R2書き込み結果を示さない。レポートは C:\Codex\r2-d1-ownership-audit-20261106\analysis-current-dry-run-20261105T234850Z.report.json（SHA-256 DFD8349A9049ED1FF53C9096C93B9409D2A66F0286BB2EA428E9E866CBA502E5）。postflight usageは d1-daily-usage-post-dry-run-20261105T235201Z.sanitized.json（SHA-256 44EF1B803733CC92886A719E9912D0F171DB805246736B2103BCA902A3CCE539）。どちらもGit外に保存した。


## 2026-11-06 JST（2026-11-05 UTC 23:56）本番D1 schema audit

candidate migration CLI（HEAD b3f3a7b75bc54d30ec468fc085922791f99e340b、SHA-256 26A53CC08B8B5995ADD0570C3FABE3F7EEBB9EAF21C8A742E54270F005F3888D）からd1-schema-auditを実行した。SELECTは1回でsqlite_schemaの27 objectを取得し、Cloudflare metadata.rows_readは92。phaseはcomplete。schema-only phaseのためR2へはアクセスしていない。実行前Account Analyticsは611,642 rows read / 4,388,358 remainingで、最低4,200,000 rowsのreserveを保つ条件を確認した。

23:56:40 UTCのpostflight Account Analyticsも611,642 rows readで変化なしだった。従って92行の増分は照会時点でAnalyticsに未反映か、他の計測差がある。累積値に92を加算せず、後続の集計反映を確認する。レポートはC:\Codex\r2-d1-ownership-audit-20261106\d1-schema-audit-20261105T235627Z.report.json（SHA-256 DB4AD4D0848B71AFDD8B59D320AAE7CCC20266BA0CE7E3F835713B56291F5146）。postflight usageはd1-daily-usage-post-schema-20261105T235640Z.sanitized.json（SHA-256 7CBB450682022D4EBB8EB3D240980E9FE48844A467DE4488B5C473C73D47B8C8）。両方Git外。


## 2026-11-06 JST（2026-11-05 UTC 23:58–23:59）analysis-current全件dry-run

candidate migration CLI（HEAD b3f3a7b75bc54d30ec468fc085922791f99e340b、CLI SHA-256 26A53CC08B8B5995ADD0570C3FABE3F7EEBB9EAF21C8A742E54270F005F3888D）でanalysis-current全件をread-only dry-runした。500行上限、100行batch、最低4,200,000行reserveを設定。preflightは611,734 rows read / 4,388,266 remaining、要求4,200,500を満たした。結果はcompleteで、3 SELECT / 248 D1 rows / 248 planned R2 objects / conflicts 0 / R2 write-delete 0。analysis-currentの現在の全248行をこの実行で読み切った。

23:59:59 UTCのpostflight Account Analyticsは611,734 rows readでpreflightと同じだった。従ってこの248行は照会時点のアカウント集計に未反映である。増分と残量を推定加算せず、次のUTC日次集計と混ぜない。これはanalysis-current phaseのdry-run完了であり、他の移行フェーズ、R2書込、書込後readback、Worker切替の完了を意味しない。

レポートはC:\Codex\r2-d1-ownership-audit-20261106\analysis-current-full-dry-run-20261105T235826Z.report.json（SHA-256 F57730161C2396B24C4E371D67727369A2B79EEDEE9A1BCB5F1E38BF936566DD）。postflight usageはd1-daily-usage-post-analysis-full-20261105T235958Z.sanitized.json（SHA-256 1DD2DD08A8BA0A2CE3EBFEF722223C15BC9B92902708966B5896A6B2E134FB0A）。Git外に保存。


## 2026-11-06 UTC 00:02–00:03 analysis-history全件dry-run

candidate CLI（HEAD b3f3a7b75bc54d30ec468fc085922791f99e340b、SHA-256 26A53CC08B8B5995ADD0570C3FABE3F7EEBB9EAF21C8A742E54270F005F3888D）でanalysis-historyを全件dry-runした。max_rows 50,000、batch_size 1,000、read reserve 4,500,000。UTC当日開始直後のpreflightは0/5,000,000 rows read。結果はcompleteで、39 SELECT / 38,855 D1 rows、38,855 planned R2 history objects、conflict 0、R2 write/delete 0。

00:03:13 UTCの当日Account Analyticsは依然0行で、dry-runのrows_readは未反映だった。D1 read metadataとAccount Analyticsの即時値は一致しない場合があるため、実行前budget gateを優先し、postflight totalは集計反映後に確認する。ReportはC:\Codex\r2-d1-ownership-audit-20261106\analysis-history-full-dry-run-20261106T000213Z.report.json（SHA-256 42C59FB53309055B98ACA15640CCF1FA7DBDB11C1D48A559627F70037610BE45）。postflight usageはd1-daily-usage-post-analysis-history-20261106T000313Z.sanitized.json（SHA-256 2273BC4BE9DDF287D7F4519397074BC6ABDAE8C6205EE2E2A5A63404C605871B）。Git外。


## 2026-11-06 UTC 00:05–00:12 financial_periods全件source audit

最新candidate CLI（HEAD b3f3a7b75bc54d30ec468fc085922791f99e340b、SHA-256 26A53CC08B8B5995ADD0570C3FABE3F7EEBB9EAF21C8A742E54270F005F3888D）でmarket-source-audit financial_periods全件を完了した。49,592 source rows、248 SELECT、D1 metadata.rows_read 99,184、R2 source lookup 3,061。結果は49,571一致、15期間欠落、4値差、2 source-category mismatch、conflict 0、R2 write/delete 0。欠落期間と値差は未解決のためserving R2へ補完しない。

preflight Account AnalyticsはUTC 2026-11-06の38,862 rows read / 4,961,138 remaining、4,500,000 reserveを確保。00:12:45 UTC postflightは116,862 rows / 4,883,138 remaining。差分の更新タイミングと帰属はaccount-wide集計だけでは確定できず、D1 metadata.rows_readとも一致しない。正確な日次総数としてquery metadataを加算しない。

レポートはC:\Codex\r2-d1-ownership-audit-20261106\financial-periods-source-audit-20261106T000507Z.report.json（SHA-256 B5EBA4CEBB3518C21816418CDE1075F587D4BC2D07F831F229BF922C84745EEB）、postflight usageはd1-daily-usage-post-financial-source-20261106T001244Z.sanitized.json（SHA-256 DB0098C658B4099E4C98B16C0757E36B0F1F5350EC063C8252CED28C9290077D）。Git外。


## 2026-11-06 UTC 00:17–00:26 本番Cron失敗と未完了行の再確認

Cloudflare Workers AnalyticsのworkersInvocationsAdaptiveは、diview-irbankについてdatetime 00:17:32Z、status exceededResources、requests 1、errors 1、subrequests 0、cpuTimeP50/P99各2,050,000を返した。Cron専用workersInvocationsScheduledの同時間帯queryは0 recordsだったが、一般invocation datasetとD1 rowが同時刻で一致するため、0件は未実行を示さない。Worker Analytics APIはrequest/error/CPU quantileを提供する（[Cloudflare Workers Metrics GraphQL](https://developers.cloudflare.com/analytics/graphql-api/tutorials/querying-workers-metrics/)）。

D1 scheduled_run_logのread-only operational auditは、00:18:20 UTCと00:26:09 UTCの両方で655行を報告し、全件status=running、finished_atなし、processed/failed合計0。最新started_atは00:17:32.801Zで、Workers Analyticsの失敗時刻と一致した。現在の本番Cronはresource limitで失敗し、そのrun rowが未完了のまま残っている。これらを完了済みrunとしてR2へ移行しない。Cloudflare Workers Readで確認した直近のproduction version 84 / 100% trafficにはDO rate limiter bindingもない。候補コードのawait/subrequest変更は未deployで、本番動作を改善した証拠はない。

00:24:49 UTCのAccount Analyticsは当日116,862 rows read / 2 rows written / 残り4,883,138。アカウント全体値のため、Workers invocationまたは個別のD1監査へ帰属しない。監査時のD1 SELECT、Analytics queryはread-only。R2 write、Queue mutation、Worker deploy、Actions dispatchはなし。

reports（Git外、C:\Codex\r2-d1-ownership-audit-20261106）:
- Worker invocation metrics worker-invocations-analytics-20261106T0016-0020Z.sanitized.json — SHA-256 A73714737509A302185B7472A555B0324CA5E4E61CE1EB9DF4CE7809D1FA3CDA。
- D1 operational audit operational-scheduled-runs-post-cron-20261106T002609Z.report.json — SHA-256 AA5038983054CEBAB4FB53D25FE674E0B24E13D0558B0E55D946727BB2538263。
- D1 Account Analytics d1-daily-usage-post-cron-20261106T002448Z.sanitized.json — SHA-256 6B22B48EA71067B8AF71383D2712FE37A3023F8FFCA4651F41BA5F8AA7CAE537。


## 2026-11-06 UTC 00:28–00:29 定期runとlegacy価格更新状態の監査

最新candidate CLIのread-only監査で、scheduled-runs phaseは655 D1 rows_read / 1 SELECT、completed source rows 0、planned R2 objects 0、complete。D1 operational auditではbackend_state 0 rows、market_price_refresh_runs 2 rows（partial 1、running 1、完了時刻なし1）、market_price_refresh_batches 2,080 rows（complete 2,020、failed 60、未完了0）だった。run集計はtotal_batches 3,960、enqueued/completed batches各2,080、failed batches 60、processed_symbols 4,040。market data dateは2026-10-09、run dateは2026-10-10〜12。個別run/batchの対応関係やrunning行の原因は集計だけでは特定しない。古いrunning行を完了扱いにしたり削除したりせず、Queue外部producerの確認と保持方針確定までD1に残す。

00:29:30 UTCのAccount Analyticsは116,862 rows read / 2 written / 残り4,883,138で、00:24:49から変化なし。個別auditのD1 metadata rows_readとの一致や帰属は確認できない。実行前の4,500,000行reserveを維持し、Account Analyticsの累積値へquery metadataを加算しない。全phaseはread-onlyで、R2 write/deleteなし。

reports（Git外、C:\Codex\r2-d1-ownership-audit-20261106）:
- scheduled-runs dry-run: scheduled-runs-dry-run-20261106T002825Z.report.json — SHA-256 0DA3E494CA419AE99CC927816B8F41AFAE0070EBA9EB1AD85DF86E5C5E759DE8。
- backend_state audit: operational-backend_state-20261106T002846Z.report.json — SHA-256 0F99C9523DC1F0F44568881A03902909F8259506A7ABD2E76570D628A0448339。
- refresh runs audit: operational-market_price_refresh_runs-20261106T002848Z.report.json — SHA-256 E79A6339830E7C2983AE97FF7CB97F2623587EC19107981B36DA07E83A29D122。
- refresh batches audit: operational-market_price_refresh_batches-20261106T002849Z.report.json — SHA-256 10B0F1A9E6890FF12DF4572CA1C9472346979C85392577AD6C8A54C2E52D2F25。
- usage: d1-daily-usage-post-operational-audits-20261106T002930Z.sanitized.json — SHA-256 692E742F7A63C2899A64E3C6CC178CDBDE3185DC8562A4944A4093B6C281E5D0。

## 2026-11-06 JST 09:32 Workers / Queues read-only再確認

Workers Read / Queues Read資格情報でCloudflare APIをGETし、09:32:06 JST時点のdeploymentを再確認した。diview-irbankはdeployment 7731b2d6-75e8-4b13-8985-9f47a4960f95、active version 4981c1e0-e337-4392-9f5b-909b52c5bc0cがtraffic 100%。bindingsはD1、R2、KV、Queue、emailで、Durable Objectsのrate limiter bindingは存在しない。価格QueueとDLQはそれぞれbacklog 0 messages / 0 bytes、oldest message timestamp 0。Queue設定はmax_retries 3、max_concurrency 1、retention 24時間。Queue metricsはbest-effortの観測時点の値として扱う（[Cloudflare Queue metrics API](https://developers.cloudflare.com/api/resources/queues/methods/get_metrics/)）。

この確認で実施したのはdeployment・version・Queue一覧・Queue metricsのGETのみ。Worker invocation、D1/R2/Queueの変更、Actions dispatch、deployは行っていない。これはread-only状態の再確認であり、DO limiterの実装・本番切替やCron runtimeの正常性を証明しない。

sanitized report（Git外）: C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T003206Z.sanitized.json — SHA-256 0AFB38D3D6467397966B409B2106B838FAE6094394BC7892771165EC8FAAC489。
## 2026-11-06 GitHub migration preflight readback

GitHub REST GETでmain branchを確認した。remote mainのHEADはb3f3a7b75bc54d30ec468fc085922791f99e340bで候補worktreeのbaseと一致し、branch responseはprotected=false、protection.enabled=false。repository rulesets endpointも空配列だった。移行workflowは現在ローカルworktreeの未コミットファイルで、remote mainにはまだない。従ってGitHub上でdispatch可能な状態ではない。

候補workflowのpreflightはmain branchが保護されていなければD1照会前に終了する。d1-r2-migration EnvironmentのAPI参照は接続済みGitHub APIの許可対象外で、端末にgh CLIもなく、ブラウザーUIのwindow activationも再試行後に失敗したため設定状態は未確認のまま。Environmentが未作成・未保護なら、独立reviewer、self-review防止、main限定deployment branch policyを整える必要がある。GitHub設定変更、push、workflow dispatchは行っていない。
## 2026-11-06 UTC 00:30–00:45 Account Analyticsと定期run再確認

00:42:55 UTCのAccount Analyticsは3つのD1 DB中2 DBに利用があり、150,193 rows read / 200 rows written、残り4,849,807 rows。前回00:29:30 UTCの116,862 / 2からreads +33,331、writes +198だが、account-wide集計であり個別のWorker・API・監査へ帰属させない。内訳はDB 516c0458…が15 read / 3 written、DB e5046cbb…が150,178 read / 197 written。preflightは4,500,000行reserveを満たした。00:45:48 UTC postflightも150,193 / 200で変化なし。read-only D1 audit自身のmetadata.rows_readをAccount Analyticsへ加算しない。

diview-irbankのworkersInvocationsAdaptiveは00:30–00:43 UTCに0 records、workersInvocationsScheduledも0 records。D1 operational auditはscheduled_run_log 655 rows、7 SELECT、全てrunning、finished_atなし、processed/failed 0、latest started_atは00:17:32.801Zで不変だった。該当区間のWorker metrics 0件だけで、Account Analyticsの読み取り増分の原因やD1を読む他のWorkerが存在しないことまでは証明しない。Worker metricsの指標は[Cloudflare Workers Metrics GraphQL](https://developers.cloudflare.com/analytics/graphql-api/tutorials/querying-workers-metrics/)を参照。

scheduled-run auditはdry-run complete、R2 writes/deletes 0。初回コマンドは既定のmigration-checkpoint.json（definitions/securities用）を読み、phase/table不一致でD1 query前に停止した。既存checkpointは変更せず、専用の新規checkpoint-in/outを指定して再実行した。今後phaseごとのCLI実行ではcheckpoint-in/outを明示する。

reports（Git外、C:\Codex\r2-d1-ownership-audit-20261106）:
- D1 usage 00:42:55: d1-daily-usage-post-worker-queue-readback-20261106T004255Z.sanitized.json — SHA-256 8038E5C4FC3FF0DE3EC97E6B8D78FF306A2EF6FB3EFEAB0C6D7800A18650ED0B。
- diview-irbank adaptive invocations: worker-invocations-analytics-20261106T0030-0043Z.sanitized.json — SHA-256 14B7A145E069D529171F20D3C47F22D0A47FBF3A12F1947897B94B5483F5ECD1。
- scheduled invocations: worker-cron-analytics-20261106T0030-0043Z.sanitized.json — SHA-256 5CFBE05DA581B49DE585CDBDF045E22B2845FD777A1897071ED8AF4899F859B2。
- scheduled_run_log: operational-scheduled-runs-20261106T004527Z.report.json — SHA-256 F4EB1A1A9B3F4AEE16BE5B1C4CA80AC67D6BC6CA45FDF1677BB19C7BCE08628F。
- D1 usage 00:45:48: d1-daily-usage-post-scheduled-run-audit-20261106T004548Z.sanitized.json — SHA-256 54ADB6C332CEEA9FBCD04CDCE800493E07EB2CF1A04E6826B4CAF33B4649CCF0。


## 2026-11-06 JST 10:04 Workers / Queues read-only readback

2026-11-06T01:04:16Z（JST 10:04）にWorkers Scripts Read / Queues Read資格情報を使い、Cloudflare APIをGETした。両APIはHTTP 200で、トークン値は出力していない。diview-irbankはdeployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%配信中。bindingsはD1/R2/KV/Queue/emailで、Durable Objects rate limiter bindingはない。

`diview-market-prices`はproducer 1 / consumer 1、backlog 0 messages / 0 bytes、retention 24時間。DLQはproducer 0 / consumer 1、backlog 0 messages / 0 bytes、retention 24時間。consumerはいずれもbatch size 1、max retries 3、max concurrency 1。Queue metricsは観測時点の値で、処理履歴や移行完了の証明にはしない。

この確認はdeployment・version・Queue一覧・Queue metricsのGETのみ。Worker invocation、D1 SQL、D1/R2/Queueの変更、Actions dispatch、deployは行っていない。DO limiterの本番切替とCron正常性は未検証。

Sanitized report（Git外）: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T010416Z.sanitized.json`; SHA-256 `e70c6bc459752863b1c752e4b228c1ce940c79de76a1fb9e1a7525b5265ef0a9`。


## 2026-11-06 JST 10:04 candidate source D1 call-site scan

作業ツリー `C:\Codex\Diview-worktrees\r2-d1-ownership-final-20261104`（branch `codex/r2-d1-ownership-20261104-final`、HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`。対象ソースには未コミット変更あり）の`data/irbank/cloudflare/src/`をD1 binding・SQL call-siteで検索した。`securities`、`financial_periods`、`dividend_events`、`stock_analysis_state/history`等の市場データ参照は検出されなかった。

検出した残存経路は、DO rate limiterの移行期間中に`api_request_log`から当該bucketを一度読むbootstrap、問い合わせの保存・管理・90日保持とメール配信状態、health応答のD1 binding有無表示。レート制限bootstrapは設定した期限・fenceに依存し、D1読取を伴う移行用経路として残る。これはソース検索の結果であってproduction runtimeの計測ではない。現行production version 84にはDO bindingがなく、切替後の市場API・CronのD1非依存性は未確認。


## 2026-11-06 JST 10:15 candidate local verification

候補作業ツリー `C:\Codex\Diview-worktrees\r2-d1-ownership-final-20261104`（HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`、変更は未コミット）でローカル検証した。

- Worker: `node --test test/*.test.mjs test/*.test.cjs` — 132 passed / 0 failed。
- Migration・D1 usage Python tests: `py -m unittest scripts/test_migration_workflow.py scripts/test_d1_daily_usage.py scripts/test_migrate_d1_to_r2.py` — 88 passed。
- yfinance/R2-generation tests: `py -m unittest discover -s scripts/yfinance_r2 -p 'test_*.py'` — 174 passed。
- Android JVM tests: JDK `C:\Users\htdn\.jdks\ms-17.0.20.1`を明示し、`testDebugUnitTest --no-daemon --rerun-tasks`を実行。XML集計は277 tests / 0 failures / 0 errors / 0 skipped。
- Android instrumentation source: 同じJDK 17で`compileDebugAndroidTestKotlin --no-daemon --rerun-tasks`が成功。端末導入とinstrumentation実行はしていない。

GradleはAndroid SDK XML v4の互換警告と既存deprecated API警告、Python suiteはpandas timezone変換deprecation warningを出したが、各コマンドは成功した。これらはローカルテスト・コンパイル結果であり、production deployment、R2公開更新、端末上の動作、本番D1削減量を証明しない。最終承認待ちのproduction/physical checksは未完了。


## 2026-11-06 JST 10:15 financial-period discrepancy disposition

本番D1の銘柄コードは`security_code`が末尾0付き（例: `13010`）であるため、先行した表示コードを使うSQL probeの0件結果はjoin条件誤りだった。`securities.security_code_display`で結合して訂正し、2 SELECT / 27 rows returned / D1 metadata 60 rows_readで1301・2708を再確認した。対応するR2 IRBANK overviewを2 object GETし、書き込みはない。Git外report `financial-period-d1-r2-values-by-display-20261106T005713Z.sanitized.json` SHA-256 `B9441C69C9BD9CF26C4B34200803319F23488C0C5CBC1F8FECF533762103BBD3`。

全49,592行の集計auditは49,571 values match、15 `source_period_missing`、4 `source_values_differ`、2 `source_category_mismatch`。過去記録の「17 missing」はIRBANK annual overviewにない17行と全source audit statusを混同していた。対象2銘柄のoverview照合では1301 FY2025/03・FY2026/03と2708 FY2011/10–FY2025/10が見つからない一方、source auditでは一部を別source categoryとして区別する。aggregate reportに2 category mismatchの銘柄・期間キーはなく、個別の帰属は未確定。

4値差は1301 FY2017/03・FY2023/03・FY2024/03、2708 FY2014/03。既存の`ai_docs/cross-issuer-financial-repair-20261031.md`が確認した他社混入を含むD1値は移行しない。R2側の修復済み値は保持する。2708の15 October-ending行は2695との混入として既存資料に記録され、serving R2へ追加しない。1301 FY2025/03・FY2026/03のD1値は保存済みraw annual batch・公式資料との矛盾が記録されており、source identityが解決するまで移行しない。

このレビューで解決したのは移行可否の判定であり、D1行の削除・修正やarchive保持期限の決定ではない。2件のcategory mismatchの個別キー、非serving D1行の保持・archive方針、必要なsource coverageは未完了。Serving R2への補完・上書き、D1削除は行わない。


## 2026-11-06 JST 10:27 production Worker source / D1 usage readback

Workers Scripts Readで`GET /accounts/{account_id}/workers/scripts/diview-irbank/content/v2`を行い、同時刻のdeployment一覧GETではdeployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`のversion `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%。source moduleは1件、SHA-256 `262388d871931f4321bb3946937cd9faa2e72313297ef51ef60f4629bea4ad9a`。本文と秘密値は表示・保存していない。

配信ソースの静的スキャンではD1 `DB.prepare` SQL call site 31件（SELECT 12、DELETE 8、INSERT 6、UPDATE 5）。参照テーブルには`securities`（SELECT 2箇所）、`stock_analysis_state` / `stock_analysis_history`、`scheduled_run_log`、`backend_state`、`app_definition_documents` / `app_definition_audit`、`api_request_log`、`market_price_refresh_runs`、support feedback tablesが含まれる。`RATE_LIMITER` binding名と`RateLimitCounter` classはソースに見つからず、先のversion metadataで確認したDO bindingなしと一致する。これはSQL call-siteの静的棚卸しで、runtime回数・行数の測定ではない。ローカル候補ソースのD1境界を本番配信コードの状態と混同しない。

同日01:27:16 UTCのAccount Analyticsは155,004 reads / 350 writes / 4,844,996 reads remaining。内訳はdatabase `e5046cbb…`が154,981 reads / 346 writes、`516c0458…`が23 reads / 4 writes。アカウント全体の値でありWorkerや今回のGET監査には帰属させない。前回00:42:55 UTCからの増加もaccount-wide。Analytics照会はGraphQLのみでD1 SQLは実行していない。

Sanitized reports（Git外）: source SQL inventory `worker-source-sql-audit-20261106T012645Z.sanitized.json` SHA-256 `5ba33954e3a8f4b49c1dbb36e70ccf3b5eb8f0e78401e2161270b18f3e4ef482`; usage `d1-daily-usage-source-read-audit-20261106T012716Z.sanitized.json` SHA-256 `559a3d52ca913a144574ffb9be62c8d85f532857328df118d6d42c01796cc085`。

この読取ではWorker invocation、D1 SQL、R2/Queue mutation、Workflow dispatch、deployは行っていない。production D1 cutover、DO counter handoff、Cron成功、D1行読み取り減少は未検証。


## 2026-11-06 JST 10:33 Android candidate build

候補作業ツリー（HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`）の`kurogane-dividend/android/`をJDK 17 (`C:\Users\htdn\.jdks\ms-17.0.20.1`)で強制buildした。`assembleDebug --no-daemon --rerun-tasks`は37 tasks executedで成功。APK: `C:\Codex\Diview-worktrees\r2-d1-ownership-final-20261104\kurogane-dividend\android\app\build\outputs\apk\debug\app-debug.apk`、21,485,631 bytes、SHA-256 `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`。

以前から存在した同じ出力先のAPKは、上書き前にGit外へコピーしSHA-256 `F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351E`を照合した。今回のAPKはビルド成果物であり、Android instrumentation実行、device/emulator install、画面確認はしていない。SDK XML v4、native library strip、既存deprecated APIに関するwarningは非致命でbuildは成功。


## 2026-11-06 JST 10:36 Wrangler cutover configuration dry-run

候補WorkerでCloudflare Wrangler `4.136.1`の`deploy --dry-run`を、通常`wrangler.toml`とCron停止`wrangler.cutover-paused.toml`の双方に実行した。双方成功し、`RATE_LIMITER` DO、KV、D1、R2、Queue、email bindingsと、`RATE_LIMITER_D1_FENCE_AT` / `RATE_LIMITER_D1_IMPORT_UNTIL`がplain-text varsとしてbundleに含まれることを確認した。値はdry-run専用のplaceholderで、本番fence timestampではない。

この確認はlocal bundle/config validationのみ。remote upload/deploy、DO migration実行、D1 SQL、Cron変更、production route/invocation確認は行っていない。`wrangler.cutover-paused.toml`の`crons=[]`と通常configのschedule復帰は、別々の明示承認ゲートとして維持する。


## 2026-11-06 JST 10:42 live rate-limit handoff snapshot

D1 SELECT前のAccount Analytics preflightは155,012 reads / 4,844,988 remainingで、4,500,000行reserveを維持。KV `backend-config:v1`をGETし、`rate_limit_window_seconds=60`を確認した。次にD1 `api_request_log`を対象に、直近60秒の有効windowを集計するSELECTを1回実施。active bucket 0、request_count合計0、D1 `meta.rows_read=1`、D1 writes 0。bucket keysは取得・出力していない。postflight Analyticsも同じ155,012 reads / 4,844,988 remainingだったが集計遅延の可能性があり、1行readをAnalytics差分へ帰属させない。

Sanitized report（Git外）: `C:\Codex\r2-d1-ownership-audit-20261106\rate-limit-active-counter-snapshot-20261106T014225Z.sanitized.json` SHA-256 `60aa1b3d6cd5af4ceb43a7f97a5aee48003c7d3352d38f1221e0b0d37b54a49d`。これは1時点のsnapshotで、切替までに新しいbucketが生じうる。fenceを適用する直前に同じactive-counter queryとAnalytics preflightをやり直す。

## 2026-11-06 JST 10:47 Cloudflare read-token and GitHub gate readback

CLOUDFLARE_API_TOKEN, CLOUDFLARE_MIGRATION_READ_TOKEN, CLOUDFLARE_WORKERS_READ_TOKEN, and CLOUDFLARE_QUEUE_READ_TOKEN were confirmed present in the Codex process environment without displaying their values. Workers Scripts Read and Queues Read GETs both returned HTTP 200. The production Worker remains version 84, and Queue/DLQ backlog was zero at the prior readback. These were read-only checks; no D1/R2 update, workflow dispatch, or Worker deployment occurred.

A read-only GitHub Settings inspection found only the IRBANK_API_KEY Environment; d1-r2-migration does not exist. The Branches page showed no protection rules, and the repository rulesets API returned an empty list. At 01:47 UTC, the GitHub main-branch endpoint returned SHA b3f3a7b75bc54d30ec468fc085922791f99e340b with protected=false; the migration workflow remains only in the uncommitted candidate worktree. Its preflight requires protected main, an independent required reviewer with self-review prevented, and a d1-r2-migration Environment restricted to main. The workflow cannot proceed in the current settings. No GitHub settings were changed.

The official GitHub REST documentation confirms that read-only Environment and deployment-branch-policy inspection uses Actions repository permission read, which the candidate workflow already grants. No extra write permission is needed for this preflight.

The Actions Environment requires separate Environment secrets: D1_R2_MIGRATION_ACCOUNT_ID, D1_R2_MIGRATION_READ_TOKEN, D1_R2_MIGRATION_ANALYTICS_READ_TOKEN, D1_R2_MIGRATION_R2_ACCESS_KEY_ID, and D1_R2_MIGRATION_R2_SECRET_ACCESS_KEY. R2 credentials should be limited to the diview-market-data migration target. These local process credentials do not automatically transfer to GitHub Actions. Environment configuration, workflow publication, and production migration writes remain pending.

## 2026-11-06 JST 11:13 legacy D1 state and definition precedence audit

A bounded, value-free inventory issued seven D1 SELECTs with LIMIT 501 per table: clients 1, push_devices 0, watchlist_targets 0, notification_events 110, notification_deliveries 0, diagnosis_bands 15, valuation_scenarios 100. Aggregate metadata rows_read was 229. No device tokens, client identifiers, credential hashes, event payloads, or definition values were emitted. A separate aggregate read found one active client row, created and last seen on 2026-10-04; notification_events has 110 rows with delivered_at NULL, occurred between 2026-10-05 and 2026-11-02. notification_deliveries is empty. Do not interpret the event rows as deliverable notifications: there are no device registrations or delivery records. Preserve these legacy rows until archive/retention and external-use requirements are decided.

The D1 definition tables do not match the same-named KV definitions. diagnosis_bands has 15 rows but only 10 unique min_score values; five keys (90, 80, 70, 60, 0) are duplicated. Its 10 unique keys match the 10-row KV definition. valuation_scenarios has 100 D1 and 100 KV rows with the same IDs, but 50 IDs differ in one or more fields; differing-field counts are badge 45, comment 50, pbr_max 45, per_max 50, title 45. Values were not emitted. Report outside Git: definition-d1-kv-detail-20261106T021107Z.sanitized.json, SHA-256 d5fe4c1ac80a3e666ceeb0b70a530cd593131d6fe437ded0f3e7697550586245.

The deployed Worker source (module SHA-256 262388d871931f4321bb3946937cd9faa2e72313297ef51ef60f4629bea4ad9a) first reads APP_DEFINITIONS KV and returns that value when present; only a missing KV value falls back to the two D1 tables. Both KV keys were present in the migration dry-run, and the dry-run planned 12 immutable R2 objects with no conflicts and zero writes. Therefore migrate the currently active KV definitions to R2; do not replace them with the differing D1 fallback rows. The five duplicate diagnosis rows and D1 valuation differences remain legacy D1 data for retention/archive review, not a reason to overwrite the active KV values.

The 2026-11-06 02:13:39 UTC Account Analytics postflight reported 155,683 account-wide rows read and 4,844,317 remaining. This total covers two databases and cannot be attributed to the audit queries. No D1 or R2 writes, workflow dispatch, Worker deployment, or device install occurred.

Supplemental CLI schema audit at 2026-11-06 01:56:07 UTC completed with one D1 SELECT, meta.rows_read 92, 27 schema objects, and zero R2 operations. The report SHA-256 is a50851a12995e1df9be68640244fa380a533ea2bfa078b700a883824c2fb6e43. The default shared checkpoint belonged to another phase and stopped the first attempt before D1 SQL; that checkpoint was preserved. The successful rerun used unique checkpoint/report paths outside Git.

## 2026-11-06 JST 11:20 production Worker and Queue readback

After the user configured `CLOUDFLARE_WORKERS_READ_TOKEN` and `CLOUDFLARE_QUEUE_READ_TOKEN`, both remained available in the Codex process environment; values were not printed. Read-only GETs at 2026-11-06 02:19:58 UTC returned Worker `diview-irbank` deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, with version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` receiving 100% traffic. Active bindings include D1 (`DB`), KV (`APP_DEFINITIONS`), R2 (`MARKET_DATA`), and Queue (`MARKET_PRICE_QUEUE`); no Durable Object rate-limiter binding is present.

The `diview-market-prices` queue had one producer and one consumer; its DLQ had zero producers and one consumer. Both retain messages for 86,400 seconds. At observation time both backlog count and bytes were zero. These are point-in-time metrics, not evidence of historical processing or a completed migration.

No Cloudflare write/deploy, D1 SQL, GitHub Actions dispatch, or device installation occurred. The production Worker remains D1-bound and the DO limiter/Cron cutover is pending. Sanitized read report outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T021957Z.sanitized.json`, SHA-256 `9D019C77ECA4AE7D420DB79F72CAC938AB1CCB4A2156D8F2C17EA93B6C8C35A5`.
## 2026-11-06 JST 11:30–11:41 financial-period source audit

Completed a read-only `market-source-audit` dry-run for all 49,592 D1 `financial_periods` rows. It consumed D1 metadata `rows_read=99,184`, checked all 49,592 source rows against 3,061 R2 objects, planned/wrote 0 objects, and reported 0 verification failures. Statuses: 49,571 match, 15 source-period-missing, 4 source-values-differ, 2 source-category-mismatch. The two transition counts (`annual->quarterly` and `annual->semiannual`) each count both mismatched D1 rows because each has a full-year candidate in both categories.

The exact category-mismatch rows are D1 security `13010` (display code 1301), consolidated J-GAAP annual FY2025/03 and FY2026/03. A separate bounded probe read five annual rows for 1301 (one D1 SELECT, metadata `rows_read=13`) and one R2 overview object. For each mismatched year, both the quarterly and semiannual full-year R2 candidates have 15 common comparable fields; 13 fields differ, none are missing, and no unit conversion or incomplete-unit metadata explains the difference. The targeted report stores field names/status only, not metric values. Keep these D1 annual rows out of serving R2 until their source identity/provenance is resolved; retain the D1 rows pending backup and retention decisions. This confirms they cannot be copied into either R2 category as equivalent records.

The full-audit CLI preflight at 02:30:05 UTC had 159,809 account-wide reads and 4,840,191 remaining, requiring 4,600,000 remaining (100,000 phase cap plus 4,500,000 reserve). At 02:38:31 UTC Analytics reported 245,817 reads / 4,754,183 remaining. The separate targeted-probe preflight and postflight at 02:41:09 and 02:41:39 UTC both reported 259,001 reads / 4,740,999 remaining. Analytics is account-wide across two databases and is not attributable to these individual SELECTs; do not equate its delta with D1 query metadata. All readings stayed within the requested 4.5M reserve.

No D1/R2 writes, deletes, Worker invocation/deploy, Actions dispatch, or device installation occurred. Dry-run report outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\financial_period_key_audit_20261106T023004Z.report.json`, file SHA-256 `7C89486518D93F46E1F5E3A557C12F7F1EB01934825D6C021F3C3BDF53CD90DD`. Targeted field-name-only report outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\financial-period-d1-r2-values-by-display-20261106T024111Z.sanitized.json`, SHA-256 `15E7DBBB508AAC259967C08798EABE347696491D24059F3EFC99CB0859718E66`.
## 2026-11-06 JST 11:48–12:03 income-advice current-state verification

Read the R2 catalog, income-advice pointer, and financial-revision object with three GETs at 2026-11-06 02:48:38 UTC. Catalog generation is `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`, market-data date `2026-11-05`, and 4,391 catalog codes are unique. The advice object has the same generation, 3,201 unique codes, 0 duplicates, 0 codes outside the catalog; its codes map to 2,887 equity, 253 ETF, and 61 REIT entries. The advice schema has no `market_data_date` field; compare the shared generation ID and take the date from the catalog. The advice object's SHA-256 is `a7fcd2332d9e9a470c76eb837293d2863626096411e18f9e4401bf5c84caec19`, equal to the prior full-recomputation route readback; R2 last-modified time is 2026-11-05 20:40:38 UTC. Financial revision was last modified 20:37:17 UTC. The corrected v2 pointer report SHA-256 is `A4A14EC15FDFA1BA22BE5A6E550C3B2EADA9DC51C82C4025EEA5EB64145FD527`. The initial v1 probe compared an absent advice `market_data_date` field and exact `generated_at` timestamps; do not treat its false comparison as a generation mismatch.

The prior full recomputation at 2026-11-05 22:21 used the main-based candidate worktree, whose generator SHA-256 is `40B5763FFD1006504C19D7ED3D67842C2BF1B0DB211E498E45234D3EA2E59E66`; that file is unchanged from candidate HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`. The newly read advice object is byte-for-byte the same as the object checked then, so the recorded recomputation still applies to this R2 snapshot: 3,223 generated candidates versus 3,201 published, 22 additions, no removals, 0 duplicate candidates, and changes among all 3,201 common rows (computed_at 3,201; monthly estimate/observed months 21 each; score-related fields 1). The full report recorded catalog/advice/financial-revision pointer stability during its run, but did not pin every individual source object's version. No R2 update is authorized or performed.

At 02:49:28 UTC, a local Node Worker route harness returned HTTP 200 and the exact current R2 payload: 3,201 items, 3,201 unique, generation matched, `cache-control: public, max-age=300`, D1 SQL calls 0, one local rate-limit stub call. This used the candidate `fetch` route and a local R2 stub; it was not a production Worker request. Report SHA-256 `C9ED43C11D50493D609D6F942A5ED6C8F01869097D8CA8C585C0821E041A6F44`.

A targeted read at 03:02:57 UTC confirmed catalog code 1773 is a `listed_security` (`jquants_product_category=021`) with `category=jp` and currency JPY. Current candidate generation recomputes it, but the published advice does not contain it. The check used 11 R2 GETs and one list page, with no D1 SQL or R2 writes. Report SHA-256 `5F6BF3670B725377D04129608A906CC438F54FBB2A1D4659654C69EC84A14C74`. Its inclusion remains a separately reviewable candidate-output difference; it was not published.

The specification's 2026-11-05 section records the current candidate rule as JP/JPY and says the earlier 2,971-item ETF-exclusion dry-run is obsolete. The divergent 2026-11-03 worktree still has an uncommitted ETF-exclusion change (generator SHA-256 `302A3D6F640E76ABE237A10EE9F51A24975D457B8F8B6ABA6A8BA195C0288510`); preserve it but do not treat it as the current main-based generator or merge it without review. A read-only full recomputation briefly started with that stale worktree and was stopped at 2,300/3,907 candidates after the branch/source mismatch was found; it produced no final report and made no writes. The authoritative complete recomputation remains the candidate-worktree report below.

Evidence outside Git: current-pointer report `C:\Codex\r2-d1-ownership-audit-20261106\income-advice-current-pointer-readback-20261106T024838Z.sanitized.json`; local route report `income-advice-worker-route-parity-20261106T024928Z.sanitized.json`; targeted 1773 report `income-advice-targeted-candidate-check-20261106T030257Z.sanitized.json`; prior full recomputation `income-advice-current-generator-readonly-20261105T222105Z.sanitized.json` SHA-256 `9B8D9C173DE62A07324738F4E080579A6AEA69D2885C934CDAFA25A1973A4D32`.

## 2026-11-06 JST 12:30 isolated Android instrumentation verification

The main-based candidate at HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b` was built and checked with JDK 17.0.20.1 on a newly created API 35 Google APIs AVD (`DiviewDisposableAudit20261106`, serial `emulator-5554`). `connectedDebugAndroidTest` completed successfully. The connected-test XML reports 141 cases, 0 failures, 0 errors, and 1 skipped; the skipped case is `MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults` and the XML has no skip reason. Instrumentation cases `IncomeAdviceCatalogCacheTest.cachedIncomeAdviceRemainsVisibleInWatchlistWhenOffline` and `NotificationDeliveryTest.offlineNotificationUsesOnlyTheLocallySavedPriceCatalog` passed.

The debug APK SHA-256 and the pulled installed `base.apk` SHA-256 both equal `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`. `com.kurogane.dividend/.MainActivity` launched on the disposable AVD. Screenshot evidence is outside Git at `C:\Codex\r2-d1-ownership-audit-20261106\diview-disposable-screen.png`, SHA-256 `B15BDB63DAFCF07E9B7F02A79A66CBB63759BDC9F59B125D6D08D77C5A2B5844`. Sanitized test record: `C:\Codex\r2-d1-ownership-audit-20261106\android-instrumentation-audit-20261106.json`, SHA-256 `EB34A22C73FC7ED7EC24C0363F5534F46BDE895386D81F7CF91172D66FC80206`.

The Gradle console reported 142 finished tests while the XML reports 141 test cases (1 skipped). Failures and errors are zero, but this reporting-count difference remains an open follow-up. No physical-device install, production D1/R2 write, Worker deployment, or GitHub migration workflow dispatch occurred.


## 2026-11-06 JST 12:46 GitHub migration workflow gate review

The candidate `.github/workflows/d1-r2-migration.yml` remains local and manual-dispatch-only. Its preflight verifies `main`, branch protection, an existing `d1-r2-migration` Environment with an independent reviewer/self-review prevention, and an exact main-only deployment branch policy before the credential-bearing job. It uses `contents: read` and `actions: read`; GitHub REST documentation confirms those read permissions cover the branch, environment, and deployment-branch-policy GETs. The focused workflow guard suite passed 10/10. `actionlint` is not installed.

The live public repository API still reports `main` as unprotected. The last direct Environment read found `d1-r2-migration` absent at 2026-11-06 JST 10:47; the connected GitHub read tool used in this turn rejected Environment endpoints, so current Environment state was not reverified. No workflow was dispatched. Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\github-migration-workflow-static-audit-20261106.json`, SHA-256 `B2ED7E208FCBA2B0EA70BB2309AB7D319CAA2CB4953622AB1B72618D8DDCB3A6`.
## 2026-11-06 03:52 UTC live Worker and Queue state

Workers Scripts Read and Queues Read GETs confirmed that `diview-irbank` deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`, still receives 100% traffic. Its bindings include D1, KV, R2, Queue, and email; no Durable Object rate-limiter binding is deployed. The market-prices queue and its DLQ each reported zero backlog messages/bytes at the observation time. The metrics are point-in-time only. Sanitized report: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T035235Z.sanitized.json` (SHA-256 `61FDAC860E5D5CD4B29F82DBD109E2E41CAAFD60E746D25196A04549584E67FD`). No D1 query, R2 write, queue mutation, Worker invocation/deploy, workflow dispatch, or device installation occurred.

The candidate `wrangler.toml` binds both D1 and `RateLimitCounter`; the two D1 handoff timestamps are injected by CLI at cutover and intentionally are not static config. A plain redeploy without the original recorded pair would remove them and make rate-limited DO requests fail closed. The candidate README now prompts for the recorded pair and forwards both values on each redeploy/rollback. Production remains on the D1-backed version; this documentation correction does not deploy or change production.

During 2026-11-06 03:52–04:03 UTC, a read-only Worker settings GET returned `usage_model=standard`, observability enabled with `head_sampling_rate=1`, the hourly Cron, and no explicit `limits.cpu_ms`. It does not establish the account's effective Workers plan or CPU ceiling. Prior hourly Cron events were marked `exceededCpu` with about 2,050 ms CPU and 30–36 seconds wall time. A local synthetic run processed all 4,391 securities, used 173,460 mocked R2 subrequests, and reported 1,388 ms local elapsed; it is not a production CPU or Cron-completion measurement. The candidate config requests 250,000 subrequests per invocation; the measured call count is above the Workers Free cap of 1,000 internal-service subrequests. Confirm plan entitlements and validate full-catalog runtime before restoring Cron, or implement bounded resumable chunks if the invocation cap is insufficient. Cloudflare describes the plan-specific [Workers CPU and invocation limits](https://developers.cloudflare.com/workers/platform/limits/). Sanitized settings report outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-settings-cpu-read-audit-20261106.sanitized.json` (SHA-256 `AFDB28BD74B92C66F17B52FCFC7F399E19EC25805DD7EA3258B7DD995EADF826`).


## Unfinished scheduled-run migration safeguard

The migration query reads only rows with `finished_at IS NOT NULL` within the 365-day retention window. It publishes the legacy current-run pointer only after the entire phase completes. Focused regression tests passed for excluding an unfinished `running` row and delaying pointer publication until completion. This prevents incomplete D1 rows from being presented as completed R2 history; it does not establish why the production Cron previously ended as `exceededCpu` or prove full-catalog runtime capacity.

## 2026-11-06 04:49 UTC production Worker and catalog readback

The latest active `diview-irbank` deployment is version 84 at 100% traffic. Its version metadata reports `usage_model=standard`, no explicit `limits.cpu_ms`, and bindings for D1, KV, R2, and Queue; it has no Durable Object rate-limiter binding.

The deployed source `GET /api/v1/search-catalog` path is D1-independent. It is included in `d1IndependentRead`, which bypasses the D1-backed public rate limiter. Its catalog and dividend-index reads use the `MARKET_DATA` R2 binding. The shared `backendConfig` call reads `backend-config:v1` from the bound KV namespace; the key was present, so the D1 fallback was not reached. A production GET returned HTTP 200 with generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`, 4,391 catalog items and 4,391 unique codes. This verifies the catalog endpoint against the current generation only.

The price queue and its DLQ each reported a point-in-time backlog count of zero. The 24-hour Workers Analytics read returned 24 failed invocations labeled `exceededResources`, each with raw `cpuTimeUs=2050000`. Version metadata has no explicit CPU limit; neither value establishes the effective account plan limit or the exact resource that caused termination. Full-catalog Cron feasibility therefore remains open.

The live income-advice endpoint was not called in this audit: version 84 routes its GET through a D1-backed rate-limit write. The earlier 02:48 UTC current-pointer read remains the evidence for the published advice object. No D1 query/write, R2 write, queue mutation, Worker deployment, workflow dispatch, or physical-device installation occurred; one public Worker GET was made.

Sanitized evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-r2-readback-20261106T045131Z.sanitized.json` (SHA-256 `8E8E629BFBE2742F7F5F43959F54DE0D02815B9425AF19686279A1485C94ABCC`).



## 2026-11-06 04:57 UTC account-wide D1 usage read

Account Analytics reported 267,326 D1 rows read and 956 rows written for the UTC day across three databases. Of the 5,000,000-row daily allowance, 4,732,674 remained, passing the 4,500,000-row reserve gate. This account-wide metric cannot identify which requests consumed the rows. The check used Analytics only and ran no D1 SQL. Sanitized report outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\d1-daily-usage-20261106T045710Z.sanitized.json` (SHA-256 `92D9DA78310C6C3775797FB4207C195E5FB8AF12B00983A9FF2BF3406C5C77EB`).



## 2026-11-06 JST legacy notification/client route review

The active production Worker v84 bundle has no SQL references to `clients`, `push_devices`, `watchlist_targets`, `notification_events`, or `notification_deliveries`. Its legacy client, push-device, watchlist-target, and notification-event paths return `410 device_managed` before accessing D1. The current Android source tree also has no references to these server routes.

The earlier bounded D1 inventory found one client last seen on 2026-10-04, no push devices, no watchlist targets, 110 undelivered events dated 2026-10-05 through 2026-11-02, and no delivery rows. The inventory emitted no client IDs, device tokens, credential hashes, or event payloads. The deployed Worker and current Android source provide no consumer or producer for these rows, but older installed clients and external D1 writers are not ruled out. Do not replay these events or move them into serving R2. Retain the source rows until external use, backup, and retention requirements are resolved.

Production source response outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\production-worker-content-20261106.bin` (SHA-256 `8E1DA719D13DCA5FD3F349A70A2A84CE287265B1ACADFB60B4D250390AE90C7B`). No D1 SQL or mutation was made for this route review.


## 2026-11-06 05:08 UTC Worker entitlement follow-up

The Workers API returned `limits=null` for the Worker resource and version 84 has no explicit `limits.cpu_ms`. Account Worker settings report `default_usage_model=standard`; these values do not establish the account's effective plan or customized limits. The subscriptions endpoint returned HTTP 403 with the configured Analytics token because Cloudflare requires Billing Read; no subscription data was received.

Cloudflare's [current Workers limits documentation](https://developers.cloudflare.com/workers/platform/limits/) says internal-service subrequests default to 1,000 on Free and 10,000 on Paid, with Paid configurable up to 10 million. The candidate requests 250,000, while its synthetic full pass used 173,460. This exceeds the Paid default and requires verification of the account's entitlement and deployed setting. For a Paid Cron that runs hourly, the documented CPU limit can be as high as 15 minutes. These plan defaults do not explain the production failure by themselves.

The 24-hour Analytics status remains `exceededResources`; an earlier detailed event was labeled `exceededCpu`. Correlate the invocation trace and effective plan before changing or resuming the full-catalog Cron. Sanitized readback outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-entitlement-readback-20261106T050840Z.sanitized.json` (SHA-256 `7FAB8DC04473D3FA16FBE598092DE8131755F35FE028DB9E749D2BC1875B2350`).

## 2026-11-06 JST 14:47–14:54 最新の本番読取・運用状態

Workers Scripts Read と Queues Read の設定を値を出さず確認し、Cloudflare REST GETを再実行した。05:51:22 UTC時点で本番Workerはdeployment 7731b2d6-75e8-4b13-8985-9f47a4960f95、version 4981c1e0-e337-4392-9f5b-909b52c5bc0cが100%配信中。D1/KV/R2/Queue bindingがあり、RATE_LIMITER Durable Object bindingはない。価格QueueとDLQのretentionは各86,400秒、backlogは各0件だった。backlogは観測時点だけの値である。

05:54:01 UTCのAccount Analyticsは当日272,313行読取、4,727,687行残り（アカウント内3 database）。これはアカウント集計であり、個別WorkerやAPI要求への帰属を示さない。D1運用監査dry-runはscheduled_run_logの660行を7 SELECTで読取り、metadata.rows_read 660、R2操作0。660行すべてstatus=running、finished_atなし、processed/failed合計0、最新started_atは05:17:32.891Z。監査前後で対象D1のAnalytics readが同じ660行増え、他の2 databaseは変化しなかった。未完了Cron記録を完了履歴としてR2へ移行しない。

候補Workerは250,000 subrequestsを要求し、合成全件パスで173,460を使用した。Cloudflare公式制限資料はFreeの設定可能上限50と別枠の内部サービス上限1,000、Paidの既定10,000・設定上限10,000,000を示す。R2もsubrequestに含まれる。アカウントの契約プランはBilling Read不足で未確認のため、Freeでも全件Cronが実行できるとは判断しない。プラン確認、現在のCron実行失敗の原因解明、および必要なら再開可能な分割処理が本番切替前に残る。根拠: https://developers.cloudflare.com/workers/platform/limits/

本番データ変更、Worker deploy、Actions dispatch、物理端末installは行っていない。読み取り証跡は C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T055124Z.sanitized.json（SHA-256 569554E39A686FB77021DEE37B18B26046B3B07DECDA67422D06EB27EC3998C2）、scheduled-runs-current-20261106T055016Z.report.json（SHA-256 E0D8D266AEDB4625F1237453497AFE7AC9F1B9435F1154C3636DA1474284B449）、d1-daily-usage-20261106T055400Z.sanitized.json（SHA-256 3CE14832650BF1FBFC2CB29D34AB7A0027E642E45AA19E36E5D5E10395028069）。

## 2026-11-06 JST 16:11 Cloudflare Worker and Queue read-only verification

At 2026-11-06 07:11:10 UTC, Cloudflare API GET requests succeeded using the Workers Scripts Read and Queues Read credentials. Token values were not displayed or saved. Production deployment 7731b2d6-75e8-4b13-8985-9f47a4960f95 serves version 4981c1e0-e337-4392-9f5b-909b52c5bc0c at 100%. Bindings include D1, KV, R2, Queue, and Email; there is no Durable Object rate-limiter binding.

The price Queue has one producer and one consumer; the DLQ has zero producers and one consumer. Both have 86,400-second retention. At observation time, each had zero backlog messages and bytes and oldest-message timestamp zero. Queue metrics are point-in-time evidence; they do not establish processing history or absence of external producers.

No D1 SQL, Cloudflare write, queue operation, Worker deployment, or GitHub Actions dispatch occurred. Production DO cutover and the other approval gates remain incomplete. Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T071035Z.sanitized.json (SHA-256 4DCB7329FED795AA478D0D1684B4E9D8E2C3F3425DD7EE6259F0F5D4AE548A64).

## 2026-11-06 07:42 UTC: Local candidate Cron design

The final migration candidate now processes the analysis catalog in resumable batches of eight securities on a one-minute Cron. An ETag-guarded R2 lease prevents overlapping batches; failures retry with exponential backoff; the source tag covers catalog and financial-revision ETags, definition content, and the analysis freshness setting; and the source pointer advances only after the final batch succeeds and revalidates its inputs. At 4,391 catalog entries, an uninterrupted pass is about 549 minute invocations (approximately 9 hours).

Retention scans 200 objects per five-minute invocation and resumes its R2 cursor. It reads old history records only when their key date is past the daily cutoff, keeps the latest row per retention period, and deletes run summaries only when their status is terminal and `finished_at` is present. The one-time 250,000 subrequest uplift was removed from the candidate Wrangler config. Worker tests passed 136/136 and migration-tool tests passed 88/88. Production still serves version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`; production Cron CPU/subrequest behavior and account plan remain unverified. This is local implementation evidence, not deployment evidence.

## 2026-11-06 UTC: Billing plan and Cron log authorization

A Billing Read token successfully read the account subscription endpoint at 11:22:38 UTC. The sanitized response contains two account-scoped subscriptions: `teams_free` (`Teams Free Base`) and `r2_paid` (`R2 Paid`); the state field was absent. This is not sufficient to identify the effective Workers plan. The account and active Worker both report `usage_model=standard`, while the Worker resource has no explicit `limits.cpu_ms`; these fields do not reveal the plan’s effective CPU ceiling. A separate read-only request to `/accounts/{account_id}/billable/usage` for 2026-11-01 through 2026-11-06 returned HTTP 403, so no Workers billable usage rows were obtained.

Worker logging settings report observability, persisted logs, and invocation logs enabled. Account Analytics token introspection found `logs.workersLogs` with adaptive sampling, but a read-only SQL query to that dataset returned HTTP 403 `Authorization error`. The token value and query body were not printed or stored. Exact per-invocation failure logs still require `CLOUDFLARE_WORKER_OBSERVABILITY_READ_TOKEN`.

The latest 24-hour Worker Analytics aggregation has 24 Cron invocations labeled `exceededResources`; each reports CPU P50/P99 of 2,050 ms and zero subrequests. This label does not identify the resource subtype. An earlier detailed event was labeled `exceededCpu`, but it does not establish the subtype for all current failures. Cloudflare’s current limits documentation lists 10 ms Cron CPU on Workers Free and up to 15 minutes for a Workers Paid Cron with an interval of at least one hour. Keep the effective plan and exact failure cause unresolved until the per-invocation logs are read. The local candidate now uses resumable analysis batches of eight securities; its production runtime has not been measured.

Sanitized reports outside Git: subscriptions GET `C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-billing-read-20261106T112238Z.sanitized.json` (SHA-256 `96EF395DE6FCEEFAE0F4FD96E11B45D6170DBE476349EFAD9EB10A3717999C06`); billable usage HTTP 403 `C:\Codex\r2-d1-ownership-audit-20261106\worker-billable-usage-read-20261106T112707Z.sanitized.json` (SHA-256 `9DD056FBDAF4D69864CA87F96E28BC88D7DF7E1F4CE36572C423E0B10AC22853`); log-query access `C:\Codex\r2-d1-ownership-audit-20261106\worker-log-query-access-read-20261106T113103Z.sanitized.json` (SHA-256 `533A27DD63ADDEA98BC75A83D526854789018B70AC36F49A352A0CD733E8F2B1`). No D1 query, Cloudflare write, Worker invocation/deploy, Actions dispatch, device installation, commit, or push occurred for this read-only review.

## 2026-11-06 UTC 11:43 Workers Observability Issues read-only follow-up

- [x] The configured Workers Observability Read token successfully called the read-only Issues list endpoint. Both the service=diview-irbank query and the account-wide query returned HTTP 200, success=true, and total_count=0. No issue payloads, IDs, account identifiers, or token values were stored.
- [ ] Keep the recurring Cron failure subtype and effective Workers CPU entitlement unresolved. Issues is a detected-error aggregation, not a raw invocation-log query; zero detected issues does not negate the existing 24-hour Analytics result of 24 exceededResources Cron invocations. No occurrences request was possible because the list returned no issue IDs.
- Cloudflare documents the read-only Issues and Occurrences endpoints here: [List detected issues](https://developers.cloudflare.com/api/resources/workers/subresources/observability/subresources/issues/methods/list/) and [List issue occurrences](https://developers.cloudflare.com/api/resources/workers/subresources/observability/subresources/issues/methods/occurrences/). The latter can expose invocation type, Cron expression, and timestamp for a retained issue occurrence; this audit received none.
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-observability-issues-20261106T114332Z.sanitized.json (SHA-256 CD63C0EE0C51A8C2CC7DFB3588F69A8B504EA4C8533104F6C0489866EAF805EE).

## 2026-11-06 UTC 11:48 Worker Cron Observability event review

- [x] Read the Cloudflare Dashboard Events view for the production diview-irbank Worker, using its last-24-hours preset. It displayed 423 success events and 23 error events. These are Observability event counts, not invocation counts.
- [x] Expanded one error event at 2026-11-06 11:17:58.488 UTC (20:17:58.488 JST). Metadata identifies eventType=scheduled, Cron 17 * * * *, outcome=exceededCpu, cpuTimeMs=2050, wallTimeMs=25633, Worker v84. This confirms CPU-limit exhaustion for that invocation. The event message itself is the Cron expression, not a stack trace.
- [ ] Keep the effective CPU threshold and cause of the high CPU work unresolved. The later 12:54 UTC Dashboard grouping found outcome=exceededCpu for all 23 last-24-hours Events error rows. The separate 24-hour Analytics aggregate reported 24 exceededResources invocation groups; its window and aggregation/sampling semantics differ, so the counts remain unreconciled.
- Cloudflare describes the Events Query Builder at [Workers Observability Query Builder](https://developers.cloudflare.com/workers/observability/query-builder/). The REST telemetry query endpoint is POST and its documented accepted permission is Workers Observability Write; it was not called with the Read token. Dashboard inspection did not save a query or change Worker settings.
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-observability-20261106T114845Z.sanitized.json (SHA-256 64C0042144B99283803F0118B7462DAB53F0B6F5263EC3CF6EC755DA23ED1DBA). No identifiers, raw event payload, request/trace IDs, account ID, or credentials were saved.

## 2026-11-06 UTC 11:53 Worker Cron CPU error metrics cross-check

- [x] The production Worker Metrics page, last-24-hours view, showed 453 invocations and 24 errors (5.3%) for version 4981c1e0 at 100% traffic. Invocation Statuses classified all 24 as CPU time limit errors; memory, internal, load-shedding, and uncaught-exception counts were each zero.
- [x] This corroborates the separate 24-hour Analytics result of 24 exceededResources invocation groups and the inspected 2026-11-06 11:17:58.488 UTC scheduled event with outcome=exceededCpu, CPU 2,050 ms, wall time 25,633 ms. At least one event is confirmed in detail; the Metrics status breakdown classifies all 24 Worker errors as CPU-limit errors.
- [ ] Keep the effective CPU threshold and cause of the CPU-heavy work unresolved. The production Settings page did not expose a numeric CPU limit, and the earlier Worker API readback reported limits=null. The observed 2,050 ms cutoff cannot be reconciled to the effective plan or a custom per-Worker setting from current evidence. The Events view had 23 error events while Metrics had 24 invocation errors; that one-count discrepancy remains unexplained.
- The direct REST Telemetry Query endpoint was not called because Cloudflare documents it as requiring Workers Observability Write. Dashboard inspection was read-only and did not change Worker settings or triggers. Cloudflare CPU-limit documentation: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) and [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/).
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-cpu-status-20261106T115316Z.sanitized.json (SHA-256 05970CA7556F18315D28AE04908CAEDA34F2CAEB4B3401D6624BD84B817DFBB9). No account, request, trace, or event IDs, raw payloads, or credentials were saved.

## 2026-11-06 UTC 11:58 Workers plan and settings cross-check

- [x] The Cloudflare Workers Plans page labels Free as the current plan. Read-only GET of the active script settings returned usage_model=standard, no limits field, and no explicit cpu_ms. The script-settings GET returned Logs enabled, invocation logs enabled, persistence enabled, and Traces disabled. Issues configuration was omitted by the API and the production Settings page showed the Issues switch off.
- [ ] Reconcile the Free plan label with usage_model=standard and the observed 2,050 ms exceededCpu event. The numeric effective CPU limit is not visible in Settings and no explicit CPU limit is returned by the current script settings API. Do not infer the active threshold from either signal alone. Cloudflare's current published limits list 10 ms per Cron invocation on Free and up to 15 minutes for an hourly Cron on Paid; the observed event does not fit those defaults.
- The earlier Issues list returned total_count=0 while Issues detection is off. Treat that as unavailable issue monitoring, not as evidence of no failures; the Metrics page's 24 CPU-limit errors remains the failure evidence. No settings, plan, or trigger were changed.
- Cloudflare references: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [Get Worker Script and Version Settings](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/script_and_version_settings/methods/get/), and [Get Worker Script Settings](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/settings/methods/get/).
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-settings-crosscheck-20261106T115857Z.sanitized.json (SHA-256 8FC0C46938A444465BD6976DFAA9B473A5AA11B1419004B2E39AC8AD5A7F6871); no account IDs, binding IDs, tokens, or secret values were saved.

## 2026-11-06 UTC 12:08 Workers plan and Cron resource evidence

- The Workers Plans page labels the account Workers plan Free. A read-only billing subscription response lists `Teams Free Base` and `R2 Paid`, with no Workers Paid subscription entry. This distinguishes the R2 subscription from Workers execution entitlements. The Worker settings API's `usage_model=standard` is not sufficient evidence to override the Free plan display.
- Cloudflare's current limits documentation lists 10 ms CPU per Free Cron invocation and up to 1,000 internal-service subrequests per invocation. Paid Cron triggers allow up to 30 seconds when scheduled more frequently than hourly and 15 minutes at hourly or longer intervals. See [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) and [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/).
- The current local candidate processes analysis in eight-security batches each minute and has no `[limits]` subrequest override. The earlier 4,391-security synthetic single-pass probe measured 173,460 R2 requests with a 250,000 cap; that is not a per-invocation measurement of the current batch implementation. The current worst-case batch request count and Cloudflare CPU use remain unverified.
- Production metrics report 24 CPU-time-limit errors among 453 invocations in the prior 24-hour window; one expanded scheduled event used 2,050 ms CPU. This conflicts with the documented Free Cron limit and the settings API omits an explicit CPU value. Keep effective entitlement and cause unresolved; do not restore the full-catalog Cron based on local elapsed-time measurements.

## 2026-11-06 UTC 12:15 GitHub Actions publication gate

- GitHub read-only branch metadata shows `main` at `b3f3a7b75bc54d30ec468fc085922791f99e340b`, `protected=false`, and no active required status checks. The repository rulesets list is empty.
- The `main` workflow directory contains nine published workflows; `cloudflare-worker-ci.yml` and `d1-r2-migration.yml` are absent. Their local candidate copies have not been published or dispatched.
- The connected read API does not expose the repository Environments endpoint in this session. Required `d1-r2-migration` reviewer/self-review/branch-policy/secrets remain unverified. The local workflow intentionally fails preflight unless `main` protection and that Environment are verified. Do not publish or dispatch until those settings are confirmed through an authorized settings view.
- No GitHub settings or workflow state was changed.

## 2026-11-06 UTC 12:17 GitHub Environment verification

The repository Settings → Environments page lists only `IRBANK_API_KEY`; there is no `d1-r2-migration` Environment. Therefore its independent reviewer, self-review prevention, main-only deployment branch policy, and dedicated migration secrets are not configured. Create and verify that protected Environment before publishing or dispatching the manual migration workflow. This was a read-only listing; no existing environment's secrets were opened and no setting changed.

## 2026-11-06 UTC 12:28 Android notification-cache validation

The main-based candidate's Android `:app:testDebugUnitTest --rerun-tasks` ran with explicit Microsoft OpenJDK 17.0.20.1 and completed successfully. The generated 39 XML reports contain 277 tests, with zero failures, errors, or skips. The relevant price-cache/notification tests passed: `NotificationPriceCacheTest` 2, `NotificationPriceTargetsTest` 1, `NotificationPolicyTest` 19, `MarketCatalogRefreshGateTest` 1, and `MarketFetchPolicyTest` 7. This is JVM unit-test evidence only. ADB found no connected device; no APK install, emulator acceptance, or instrumentation run occurred. The earlier `No compose hierarchies found` instrumentation failure remains open.

## 2026-11-06 12:43–12:54 UTC: production Cron outcome and Android follow-up

The authenticated Cloudflare Dashboard Events view showed 423 success events and 23 errors over the last 24 hours. All 23 listed errors were scheduled events with Cron `17 * * * *`, from 2026-11-05 22:18 JST through 2026-11-06 21:18 JST. Dashboard Visualizations then grouped those 23 error rows by `$workers.outcome` and showed `exceededCpu` for all 23. Two expanded examples (20:17:58.488 and 21:18:05.379 JST) each reported 2,050 ms CPU, with wall times of 25,633 ms and 32,287 ms. The read-only GET Observability Issues request returned zero issue records; this is a separate dataset and does not negate the Events results. The REST telemetry query was not called because its documented permission is Workers Observability Write.

An earlier 11:53 UTC Metrics snapshot showed 453 invocations and 24 CPU-time-limit errors. Its window and aggregation differ from the later 24-hour Events view, so the counts remain unreconciled. The effective CPU entitlement remains unresolved: the Workers plan display is Free while observed CPU is 2,050 ms, and the active numeric limit is not exposed. The cause of the high CPU work remains unknown. Do not restore the production Cron based solely on synthetic local request counts.

Sanitized reports outside Git: event-level readback `C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-observability-20261106T1243Z.sanitized.json` (SHA-256 `AAA7107D6BBADFEB1DACE79121DC394B4A3C07CB1974116EC55B2A703355A18C`) and outcome aggregate `C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-outcome-summary-20261106T1254Z.sanitized.json` (SHA-256 `722005895C5F8C4945BF5D8BB7BDDCED82033674739DE7F52A0C4486597522E2`). Neither retains event/request/trace IDs, raw logs, account IDs, or credentials.

With JDK 17.0.20.1, the task-specific Android 35 emulator passed `AssetDataRefreshTest#unknownQuoteDoesNotBecomeNoDividendOnAssetAndDetailScreens` (1/1) and `ReleaseNotificationPlatformTest` (2/2). The previously failing focused Compose case now passes. The candidate debug APK was reinstalled from this worktree, and the built APK hash matched the installed `base.apk`: `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`. No physical device or manual app launch was used.

## 2026-11-06 UTC 19:41: candidate lease and GitHub Environment recheck

Cloudflare documents a 15-minute maximum wall time for Cron invocations. The candidate's analysis and retention R2 leases were shorter at 5 and 10 minutes, respectively. They are now both 16 minutes, with assertions; the scheduled-analysis suite passed 22/22, the full Worker suite passed 123/123, and all 91 migration/workflow utility tests passed. A hard termination can delay recovery until the lease expires, while the longer lease prevents another Cron from claiming the same job during the documented maximum execution window. Cron entitlement and production CPU behavior are still unresolved, so this does not authorize schedule restoration.

The `wrangler.cutover-paused.toml` file sets `crons = []` but retains both Queue consumers; the current Queue handler acknowledges every message. This is a Cron pause, not a Queue pause. No `MARKET_PRICE_QUEUE.send` caller exists in the inspected Worker source, but an external producer has not been excluded. The last live zero-backlog observation is from 2026-11-06 and is not a current guarantee. Recheck backlog and producers before using this configuration where Queue messages must be preserved.

GitHub Environment UI readback confirmed administrator bypass is already disabled, `main` is the sole deployment branch, and required reviewers, wait timer, and Environment secrets are absent. No setting change or secret transfer occurred. A trusted independent reviewer remains unidentified; workflow publication/dispatch and production migration remain gated.

## 2026-11-06 UTC 20:00 Android verification

Microsoft OpenJDK 17.0.20.1 `:app:assembleDebug` succeeded. `:app:testDebugUnitTest --rerun-tasks` passed 277/277, and the Android 35 audit emulator passed all 24 `AssetDataRefreshTest` instrumentation cases, including the visible automatic retry of missing Japanese prices. The APK installed from this worktree matched the emulator's `base.apk` at SHA-256 `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`; `MainActivity` resumed. No physical device was connected or used.
