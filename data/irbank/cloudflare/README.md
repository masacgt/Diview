# IRBANK → Cloudflare Worker (D1 / R2)

このディレクトリにはCloudflare Worker APIがあります。合意済みの所有権変更では、市場データ・分析状態と履歴・完了ログ・定義をR2 `diview-market-data`、共有レート制限をDurable Objectsで管理し、問い合わせとメール再試行状態をD1に残します。旧テーブルと既存行は保持し、用途と移行を確認するまで削除しません。検索・日次価格・月次価格・暦年の配当イベントは `market/yfinance/v2/` を参照します。現在の年間DPSと利回りは同じv2名前空間のIRBANK配当インデックスを優先し、IRBANK会社予想、次に分割調整済み実績を使います。銘柄別APIは全銘柄インデックスではなく、`issuer_dividends/shards/current.json` が示すコード先頭2文字のシャードを読みます。R2バケットは非公開で、アプリにはWorker APIを通じて配信します。


この作業ツリーでは所有権変更、手動移行Workflow、Worker CI、Androidのオフライン価格通知を準備しています。ローカル変更は本番Workerへデプロイしておらず、移行Workflowも起動していません。手順と未検証事項は [D1 to R2 migration runbook](docs/d1-r2-migration.md) と `ai_docs/d1-r2-device-data-ownership-spec.md` を参照してください。

## Android財務・問い合わせAPI

新規問い合わせの管理者メール通知と再試行については [問い合わせメール通知](docs/feedback-email.md) を参照してください。Worker公開前に追加の `0006_feedback_email.sql` を適用します。

`GET /api/v1/financial-data/7203` は、v2のプロフィール・現在値・年次財務・配当履歴を返します。`dividends.records` はyfinanceの日足履歴から取得した修復済み配当イベントを暦年単位で集計し、完了した年だけを `calendar_year` と `calendar_year_distribution_per_share` で返します。金額基準は `dividends.amount_basis=yfinance_split_adjusted_assumption` で、IRBANKの決算期DPSとは別の履歴です。一方、現在の年間DPSは `market/yfinance/v2/issuer_dividends/current.json` のIRBANKデータから選び、予想がなければ最新の分割調整済み実績を返します。検索と銘柄指定ライブ価格は、選択した配当と株価から利回りを計算します。`issuer_dividends.records` はIRBANK v1の銘柄別年間実績を返し、最新年度は配当インデックスの更新値で上書きします。会社予想、最新実績、出典も分けて返します。IRBANKの配当額が未取得の場合はyfinanceの配当額に戻さず、状態を `unknown` として返します。暦年の実配当イベント履歴は引き続きyfinance由来です。v2配当履歴が空でも404にせず項目状態を併記します。銘柄情報の8期編集値は端末内に暗号化保存し、R2の共有データを変更しません。

IRBANKの配当実績レコードが空の場合は `dividend_status=unknown` とし、明示的なゼロの実績または予想のみ無配と扱います。一般株式の取得日時を確認できる予想で補完できる場合を除き、未取得をゼロに変換しません。

`GET /api/v1/securities/7203` は `data_retrieval` にyfinance ticker、取得元、通貨、最終取得試行日時、同期状態、失敗理由を含めます。`company_info` には会社情報・統計の最終取得日、直近の試行状態、再試行予定、失敗分類・理由を返します。個別の取得日時が保存されていない既存レコードでは、最終成功同期日を参考値として返し、`fetch_date_basis=last_successful_sync_fallback` と状態 `stale` で区別します。この場合、個別取得時刻を誤認させないよう `last_fetched_at` はnullです。新しい個別取得日時には `fetch_date_basis=company_info_fetch` を付けます。`valuation` のPER/PBRは日次終値をyfinanceの保存済みEPS/BPSで割ってWorkerでも再計算し、Yahooの報告倍率をそのまま返しません。`dividend_history` は履歴取得状態、取得元、直近取得範囲、保存済み配当イベントの最古日・最新日を返します。保存期間はR2の通常・アーカイブ年次パーティション内のイベント日から求め、取得範囲とは区別します。Androidの銘柄情報画面はこの内容を「データ取得情報」として表示します。

問い合わせテーブルを初めて導入する場合は、次の移行SQLを実行します。既存テーブルとデータを削除せず、再実行できます。

```powershell
npx --yes wrangler@4.157.0 d1 execute diview-irbank --remote --file migrations/0001_support_feedback.sql
```

`POST /api/v1/feedback` はJSONの `requestId`（UUID v4）、`category`、`message`、任意の `name` / `email` / `diagnostics` を検証し、D1の `support_feedback` に保存します。保存を確認できた場合だけ201と `accepted: true` / `receiptId` を返します。同じ受付番号と内容の再送は1件にまとめ、別内容の衝突は409、保存失敗は503です。本文は2万文字、要求全体は100KBまで。IP単位で既定60秒に5回までです。

管理者はD1管理画面で内容を参照できます。`GET /api/v1/admin/feedback` は最新100件、`DELETE /api/v1/admin/feedback?id=受付番号` は指定した1件を削除します。どちらも既存の `ADMIN_SECRET` を `X-Admin-Secret` ヘッダーへ指定してください。鍵をソースやAndroidアプリへ保存しないでください。

90日超の問い合わせは受付・管理参照および既存の定期処理で削除します。Cron枠が不足するアカウントでは、コードだけを公開する `npx --yes wrangler@4.157.0 versions upload` と、その返却バージョンを指定する `npx --yes wrangler@4.157.0 versions deploy バージョンID@100% --yes` を使えます。この方式はトリガーを変更しません。新しい定期トリガーが必要な場合は別途Cron枠を確保してください。

```powershell
node --test test/*.test.mjs test/*.test.cjs
```

## Cloudflareリソース

`wrangler.toml` は `diview-irbank` D1、`diview-market-data` R2、および共有レート制限用Durable Objectを参照します。D1引継ぎ後のWorkerには、引継ぎ時に記録した `RATE_LIMITER_D1_FENCE_AT` と `RATE_LIMITER_D1_IMPORT_UNTIL` の両方が必要です。どちらもGit管理の設定値ではありません。値を省略してデプロイするとレート制限処理がfail-closedになり、対象APIがエラーになります。通常の再デプロイやロールバックにも同じ記録済みの値を渡してください。切替手順と値の決め方は [D1/R2移行手順](docs/d1-r2-migration.md#bounded-d1-counter-import) を参照してください。

次のコマンドは、承認済みの引継ぎ記録から同じ2値を入力して実行します。移行ブリッジがリクエストを受けた後は、値を再計算しないでください。公開・D1操作コマンドは、ローカル検証で使用したWrangler 4.157.0を明示します。グローバルにインストールされた別バージョンや未固定の最新版に切り替えないでください。

```powershell
$rateLimiterD1FenceAt = Read-Host '承認済み記録の RATE_LIMITER_D1_FENCE_AT (Unix秒)'
$rateLimiterD1ImportUntil = Read-Host '同じ記録の RATE_LIMITER_D1_IMPORT_UNTIL (Unix秒)'
if ($rateLimiterD1FenceAt -notmatch '^\d+$' -or $rateLimiterD1ImportUntil -notmatch '^\d+$') { throw '引継ぎ記録のUnix秒を入力してください' }
npx --yes wrangler@4.157.0 deploy --var "RATE_LIMITER_D1_FENCE_AT:$rateLimiterD1FenceAt" --var "RATE_LIMITER_D1_IMPORT_UNTIL:$rateLimiterD1ImportUntil"
```

## スキーマとアプリ定義

`../organized/d1/schema.sql` は `dev-local.ps1` が `--local` で使う開発用スキーマで、本番D1には実行しないでください。旧定義seed `../organized/d1/seed_app_definitions.sql` は `../organized/d1/seed_app_definitions.sql.disabled` に移し、通常のSQLファイルとして扱われないようにしました。これは履歴確認用であり、本番・ローカルどちらのseedにも使いません。問い合わせに必要なD1スキーマは、用途を確認したうえで該当する `migrations/` のSQLだけを適用します。

この作業ツリーのWorkerはアプリ定義をR2から読み、移行期間のKV読み取りフォールバックを使います。D1定義の読み取りとフォールバックはありません。

## D1とR2のデータ配置

移行前のD1には銘柄マスター、互換財務・配当、分析、定義、旧通知状態がありました。四半期・半期財務32,442行を含む過去のR2退避記録は当時の件数・SHA-256検証結果を示すもので、現在の全データ網羅性を保証しません。

この作業ツリーでは検索・価格・財務・配当・分析最新値と履歴・完了ログ・定義をR2から読み書きし、共有レート制限をDurable Objectsで扱います。ウォッチリストと端末通知状態はAndroid内に置きます。D1には問い合わせとメール再試行を残し、DOの初回状態引継ぎで有効な旧 `api_request_log` カウンターを一度だけ読み取ります。旧市場・分析・通知テーブルと既存行は保持します。現行本番の保存先・データ網羅性は本番切替前に再確認が必要です。

## J-Quants調整済み日足API

`GET /api/v1/jquants/daily-quotes` はR2の `debug/jquants/v1/adjusted_daily_quotes.csv` をCSVストリームで返します。対象期間は2024-07-22〜2026-07-17、2,146,603行、4,736銘柄です。列は `Date,Code,AdjFactor,AdjO,AdjH,AdjL,AdjC,AdjVo` です。取得済みスナップショットを返すため、自動更新はありません。`HEAD` と単一バイト範囲の `Range` 要求に対応し、IPごとに60秒5回までです。R2の公開URLは無効のまま、Worker API経由で配信します。

```powershell
curl.exe -fL "https://diview-irbank.omasa.workers.dev/api/v1/jquants/daily-quotes" -o adjusted_daily_quotes.csv
curl.exe -fL -r 0-1023 "https://diview-irbank.omasa.workers.dev/api/v1/jquants/daily-quotes" -o adjusted_daily_quotes_part.csv
```

## 旧D1市場データ取込の停止

銘柄・財務・配当のD1インポートとD1向け財務修復は停止しました。`import-d1.ps1` と
`apply-financial-repair.ps1` は誤投入防止のため直ちに失敗します。以前生成したSQLや
修復プランもD1へ適用しないでください。オフラインの `plan-financial-repair.py` は
履歴資料の照合用として残しています。

D1既存レコードからR2への移行準備は [移行runbook](docs/d1-r2-migration.md) を参照します。
移行Workflowは手動実行専用で、本番実行・書き込み・Workerデプロイはまだ行っていません。
市場データを今後更新する場合は既存のR2公開経路を使い、D1へ再投入しないでください。

## AndroidとWorker API

AndroidはWorker APIを通じて検索、価格、配当、財務データを取得します。Worker URLはGradleプロパティ `diviewD1ApiBaseUrl` で上書きでき、既定値は `https://diview-irbank.omasa.workers.dev` です。分析定義API、問い合わせAPI、IRBANK固有のAPIはそれぞれの既存経路を使います。市場データAPIのv2仕様と本番反映状況は、後述の「yfinance v2市場データとAndroid取得」を参照してください。

## R2のIRBANKデータ

IRBANKの取得スナップショットをアプリ向けにまとめるときは、リポジトリルートから次を実行します。

```powershell
python scripts/irbank_r2/prepare_dataset.py
```

既定の出力先は `%LOCALAPPDATA%\Temp\diview-irbank-r2-ready` です。銘柄ごとの概要JSONには、企業情報、年次・四半期・半期財務、配当、評価指標、セグメント、大株主をまとめ、出典・帰属情報も保持します。大きな日次株価と週次信用取引は独立したオブジェクトに分けます。JPX由来の価格・信用取引を含めるには、R2保存とアプリ利用の許諾を確認したうえで `--include-jpx` を付けてください。

アップロードはS3互換の `R2_ACCESS_KEY_ID` と `R2_SECRET_ACCESS_KEY` を環境変数に設定し、先にドライランで対象件数・容量を確認してから `--upload` を指定します。JPX由来ファイルが準備されている場合は、さらに `--allow-jpx-data` が必要です。オブジェクト、一覧、マニフェストの順に公開し、既存オブジェクトの削除は行いません。

```powershell
python -m pip install -r scripts/irbank_r2/requirements.txt
python scripts/irbank_r2/upload_r2.py
python scripts/irbank_r2/upload_r2.py --upload
```

R2キーは `market/irbank/v1/` 配下です。Worker APIは `/api/v1/irbank/catalog`、`/api/v1/irbank/manifest`、`/api/v1/irbank/securities/{銘柄コード}` を提供します。日次価格は `/prices`、週次信用取引は `/weekly-margin` を付けて個別取得できます。数字4桁コード、英字入り4桁コード、末尾0付き5桁コードを受け付けます。

### 一時データをローカルWorkerから読む

`wrangler.dev.toml` はローカル開発専用設定です。`MARKET_DATA` は本番と同じ `diview-market-data` へのリモートバインディング、D1とKVはローカルシミュレーションを使います。これにより開発Workerと本番Workerが同じR2データを読みます。通常の `wrangler.toml` と本番Workerの設定は変えず、デプロイ操作も行いません。

ローカルD1スキーマを準備して開発Workerを起動します。スキーマ作成は再実行できます。

```powershell
.\dev-local.ps1
```

起動後、`http://localhost:8787/api/v1/irbank/manifest`、`/api/v1/irbank/catalog`、`/api/v1/irbank/securities/1301` でデータを確認できます。リモートR2バインディングにはCloudflareへログインしたWrangler認証が必要です。

## yfinance v2市場データとAndroid取得

### 検索・登録と候補価格

`.github/workflows/yfinance-r2.yml` は、平日の日本時間21時に起動し、XTKS営業日を確認してからJ-Quants v2の銘柄一覧を取得します。株式、ETF、REITを含むコードに `.T` を付けてyfinanceデータを取得し、プロフィールと完了済み終値を保存できた銘柄だけを `market/yfinance/v2/catalog/current.json` に掲載します。J-Quants一覧にあっても、初回取得に成功していない銘柄は検索・登録できません。

`GET /api/v1/security-search?q=...` と銘柄指定 `GET /api/v1/live-quotes` は、IRBANKの有効な予想を優先し、一般株式では直近45日以内の取得日時が確認できるyfinance年間予想を補完できます。利回りは選択した配当額と株価から計算します。REITのyfinance年間推定値は発行体の2期分配金の代わりにしません。IRBANK予想がない場合は最新実績または未取得の状態を返し、欠損をゼロに変換しません。

候補を選んで銘柄詳細・登録情報を開くと、`GET /api/v1/securities/{code}` がv2の `profile.json` と `current.json` を読みます。J-Quantsの有効ユニバースとv2カタログにない銘柄は返しません。業種表示はAndroid同梱の業種マスターを使います。

### 端末の日次価格一覧

`GET /api/v1/search-catalog` はv2の `catalog/current.json` を返します。`GET /api/v1/live-quotes` は同じ公開世代の価格をページ単位で返し、`codes` を指定した場合は候補コードだけを返します。どちらもWorkerからYahoo Financeへ接続せず、yfinance更新ジョブが公開した日次データを配信します。

Androidは端末ごとに最終試行時刻、最終成功時刻、成功した日本時間の日付、取得状態を保存します。同じ日本時間の日に成功済みなら全件一覧を再取得せず、前回成功から12時間未満の場合も取得を見送ります。失敗時は端末内の前回データと成功時刻を維持し、後のアプリ起動時に再試行できます。個別の検索・登録時は該当銘柄の公開済みR2データを読みます。

### 配当・月次価格

`GET /api/v1/monthly-price-history?codes=7203,9984&months=2025-11,2025-12` は指定月の月末最終営業日価格をv2から返します。通常領域に直近13か月、アーカイブに過去10年分を保持します。価格にはyfinanceの `Close` を使い、株式分割比率を再適用しません。要求に含めるのは銘柄コードと年月だけです。

`GET /api/v1/dividend-months?symbols=7203.T` はv2の年別配当イベントから直近365日のイベント、配当月、金額基準、yfinance修復フラグを返します。`event_year=YYYY` は従来どおり1年分、`event_years=YYYY,...,YYYY` は最大10年分のイベントを1回で返し、要求した年を `requested_event_years` で応答します。配当月は引き続き直近365日から計算します。Androidの銘柄詳細画面は初期読み込みでグラフ用の暦年イベントをまとめて取得してキャッシュし、年の選択時はキャッシュを表示します。古いWorkerが年範囲を応答しない場合は、Androidが従来の年別要求へフォールバックします。権利日派生値は `market/yfinance/v2/derived/rights_dates/current.json` から読みます。将来の権利落ち日は、過去5年以内に同じ月の配当イベントが2年以上ある場合は履歴からの推定を優先し、該当しない場合に `Ticker.calendar`、最後に `Ticker.get_info` の日付を使います。履歴推定状態と情報源を応答に含めます。権利付き最終日と推定権利確定日は `.T` 銘柄にXTKS、米国銘柄にXNYSカレンダーを使って計算します。履歴推定イベントは権利日通知に使いません。正常に取得した空履歴と未取得状態は `history_status` で区別します。定期取得は固定したyfinance 1.7.0・SciPy 1.18.1で `history(period="max", interval="1d", actions=True, repair=True)` を使います。成功した取得範囲を正本として既存イベントを置き換え、取得失敗時は前回イベントを維持して失敗状態を記録します。配当・分割イベントは直近10年を通常領域、それより古いイベントを無期限アーカイブに保持します。

財務APIには財務諸表・評価値の項目単位の旧データ互換処理が残っています。配当履歴はyfinance v2イベントだけを使い、v2公開前は `not_fetched` として扱います。この作業ツリーでは分析の市場データ・状態・履歴・定義のD1参照を取り除いていますが、本番反映とデータ網羅性は未確認です。IRBANK全体を一括アーカイブ・削除する処理は行いません。

### 本番反映状況と有効化

2026-10-14時点でv2同期、Worker、Androidの変更はコミット `edcd12c2` として `main` にプッシュ済みです。R2への初回v2公開とWorker本番デプロイは未実施です。本番APIを読み取り確認したところ、`/api/v1/security-search?q=7203` は名称だけを返し、`/api/v1/live-quotes` は `legacy_unpublished` を返しました。そのため、端末にインストールした最新APKでも検索候補の株価・利回りを本番データで確認できていません。

有効化にはGitHub Actionsの `JQUANTS_API_KEY`、`R2_ACCESS_KEY_ID`、`R2_SECRET_ACCESS_KEY` を設定し、営業日にv2同期を実行してR2の公開データを読戻し確認した後、Workerをデプロイします。デプロイ後は検索候補、銘柄詳細、配当履歴、月次価格、端末の日次一覧をそれぞれ確認してください。詳細な仕様はリポジトリ直下の `ai_docs/r2-yfinance-target-spec.md` に記載しています。

## バックエンド運用（分析・通知）

ウォッチリスト、目標利回り、通知設定・状態はAndroid端末内で管理します。旧クライアント登録・Push端末・ウォッチリスト・通知履歴API（`/api/v1/clients/register`、`/api/v1/push-devices`、`/api/v1/watchlist-targets`、`/api/v1/notification-events`）は `410 device_managed` を返します。対応するD1テーブルと既存データは削除せず、Workerからの読み書きとFCM配信を停止しています。

ポートフォリオ診断の閾値・重みは KV の `portfolio-analysis-definitions:v1` で配信します。`scripts/seed-kv.ps1` で他の定義とまとめて登録します。`wrangler.toml` には `APP_DEFINITIONS` KV binding を本番 namespace ID とともに設定してください。

Cron は市場スナップショットまたは分析定義が変わった場合にR2の銘柄カタログを使って全銘柄を再評価します。個人別Watchlistとの結合やサーバーFCM配信は行いません。この作業ツリーでは分析最新値・履歴・完了済み実行ログをR2へ保存します。既存D1の行は移行・保持確認が終わるまで変更・削除しません。実装は本番未デプロイです。

定期分析では市場スナップショットと銘柄別の分析入力を実行内で再利用し、銘柄処理を最大8件ずつ並列化します。実行ログの `metadata_json.durations_ms` に分析・履歴整理の所要時間を記録します。

公開読み取りAPIは現行のキー・時間窓・上限と `Retry-After` 応答を維持する共有レート制限を適用します。この作業ツリーではDurable Objectsを使い、初回のみ有効な旧D1カウンターを読み取って引き継ぐ設計です。JSON APIは既定で `private, no-store` とし、ブラウザの事前要求（CORS OPTIONS）にも応答します。`GET /api/v1/health` はR2市場データ、分析定義版、直近Cron結果を確認します。market内の`v2_catalog`には現行generation・市場日・生成日時・銘柄数・価格件数が入り、旧snapshot年齢とは分けて返します。実行ログの正本はR2です。

サーバーFCM配信は停止しています。株価などの端末通知はAndroidのWorkManagerがローカル設定・状態を使って処理します。旧 `clients`、`push_devices`、`watchlist_targets`、`notification_events`、`notification_deliveries` テーブルは現時点ではスキーマ・既存データを保持し、Workerは利用しません。
