# IRBANK → Cloudflare D1

このディレクトリには、整理済みのIRBANKデータをCloudflare D1へ登録するスキーマとWorker APIがあります。銘柄・財務情報はD1、株価スナップショットはR2から配信します。

## Android財務・問い合わせAPI

`GET /api/v1/financial-data/7203` はR2の概要から年次財務・分割調整済み配当・評価指標を返します。概要の配当履歴に有効な実績がない銘柄では、`market/securities/v1/{code}.json` のYahoo Finance分配イベントを暦年単位で合計し、確定済み年の履歴を返します。この場合は `dividends.history_basis=calendar_year_observed_distributions` と出所を付け、決算期配当と区別します。当年は年途中のため対象外です。どちらのデータもない銘柄は404です。同一日・同一金額の重複イベント、不正日付、0以下の金額は除外します。Androidは欠損指標を固定値で補完せず、取得済みの値を端末へキャッシュします。

問い合わせテーブルを初めて導入する場合は、次の移行SQLを実行します。既存テーブルとデータを削除せず、再実行できます。

```powershell
npx wrangler d1 execute diview-irbank --remote --file migrations/0001_support_feedback.sql
```

`POST /api/v1/feedback` はJSONの `requestId`（UUID v4）、`category`、`message`、任意の `name` / `email` / `diagnostics` を検証し、D1の `support_feedback` に保存します。保存を確認できた場合だけ201と `accepted: true` / `receiptId` を返します。同じ受付番号と内容の再送は1件にまとめ、別内容の衝突は409、保存失敗は503です。本文は2万文字、要求全体は100KBまで。IP単位で既定60秒に5回までです。

管理者はD1管理画面で内容を参照できます。`GET /api/v1/admin/feedback` は最新100件、`DELETE /api/v1/admin/feedback?id=受付番号` は指定した1件を削除します。どちらも既存の `ADMIN_SECRET` を `X-Admin-Secret` ヘッダーへ指定してください。鍵をソースやAndroidアプリへ保存しないでください。

90日超の問い合わせは受付・管理参照および既存の定期処理で削除します。Cron枠が不足するアカウントでは、コードだけを公開する `npx wrangler versions upload` と、その返却バージョンを指定する `npx wrangler versions deploy バージョンID@100% --yes` を使えます。この方式はトリガーを変更しません。新しい定期トリガーが必要な場合は別途Cron枠を確保してください。

```powershell
node --test test/*.test.mjs
```

## Cloudflareリソース

`wrangler.toml` は `diview-irbank` D1と `diview-market-data` R2を参照します。Workerを公開するには次を実行します。

```powershell
npx wrangler deploy
```

## スキーマとアプリ定義

```powershell
npx wrangler d1 execute diview-irbank --remote --file ..\organized\d1\schema.sql
npx wrangler d1 execute diview-irbank --remote --file ..\organized\d1\seed_app_definitions.sql
```

診断基準・評価シナリオおよび分析定義はD1から取得します。`seed_app_definitions.sql` は `organized/d1` 内にあります。

## D1とR2のデータ配置

D1には銘柄マスター、API・分析・Watchlistなどの運用データ、株式分析に使う年次財務を保持します。四半期・半期財務はアプリとWorkerの現在の処理では参照されないため、R2の `market/irbank/v1/` 配下を配信元とし、D1の原本は `market/irbank/v1/archive/d1-financial-periods-qs-20261009.json` に全32,442行を退避しています。退避オブジェクトは取得ファイルのSHA-256と行数を照合済みです。

D1の財務インポートは年次だけを生成します。D1には銘柄マスターを保持しますが、現在の `/api/v1/security-search` はD1の部分一致SQLを使わず、R2の `market/irbank/v1/catalog.json` を検索します。D1の `dividend_events` と年次 `financial_periods` は分析APIが参照し、価格・配当履歴など大量の市場スナップショットはR2から配信します。

## J-Quants調整済み日足API

`GET /api/v1/jquants/daily-quotes` はR2の `debug/jquants/v1/adjusted_daily_quotes.csv` をCSVストリームで返します。対象期間は2024-07-22〜2026-07-17、2,146,603行、4,736銘柄です。列は `Date,Code,AdjFactor,AdjO,AdjH,AdjL,AdjC,AdjVo` です。取得済みスナップショットを返すため、自動更新はありません。`HEAD` と単一バイト範囲の `Range` 要求に対応し、IPごとに60秒5回までです。R2の公開URLは無効のまま、Worker API経由で配信します。

```powershell
curl.exe -fL "https://diview-irbank.omasa.workers.dev/api/v1/jquants/daily-quotes" -o adjusted_daily_quotes.csv
curl.exe -fL -r 0-1023 "https://diview-irbank.omasa.workers.dev/api/v1/jquants/daily-quotes" -o adjusted_daily_quotes_part.csv
```

## 銘柄・財務・配当データのインポート

```powershell
node ..\scripts\build-d1-import.cjs
powershell -ExecutionPolicy Bypass -File .\import-d1.ps1
```

生成したSQLを分割して実行し、再実行時は `import-state.json` の成功記録から続けます。

## Android検証

AndroidアプリはWorker APIを使って銘柄を検索・照合します。`/api/v1/security-search?q=...` はR2の日本語銘柄カタログを部分一致検索し、`/api/v1/securities/{銘柄コード}?include_market=false` はR2の銘柄概要から登録用情報を返します。`/api/v1/financial-data/{銘柄コード}` もR2の概要を読みます。これらの読み取りはD1の無料枠や再設定に依存しません。Worker URLはGradleプロパティ `diviewD1ApiBaseUrl` で上書きできます。既定値は `https://diview-irbank.omasa.workers.dev` です。

株価一覧は `/api/v1/search-catalog` から取得し、Android端末のSharedPreferencesへ保存します。通常は日本時間の日付ごとに1回取得し、日付が変わっていても前回取得から12時間未満なら見送ります。18時以降は、Workerの当日更新完了日が確認できるまで30分間隔で再確認します。完了前の一覧は端末キャッシュへ反映しません。

### Cloudflare上での株価更新

UTC毎時17分（日本時間の毎時17分）のCronを1本使います。まだ初回同期が終わっていない場合は次のCronから同期を開始し、以後は平日17時以降に最大12銘柄ずつのQueueメッセージを投入します。価格取得Workerは1銘柄につき1リクエストでYahoo Finance Chart APIを呼び、同時実行数を6に抑えて最新値を `market/yfinance/live_quotes/{銘柄コード}.json` へ保存します。日次終値は `market/yfinance/daily_prices/{銘柄コード}/{年月}.json` に追記し、月次履歴APIは従来の履歴とこの追加データを合わせて返します。検索一覧の `market_data_date` は全バッチの成功または失敗確定後に更新されます。失敗バッチはQueueの再試行後にDead Letter Queueへ移し、完了状態と失敗件数を記録します。

Queueは `diview-market-prices` と `diview-market-prices-dlq` を使います。初回設定時は `npx wrangler queues create diview-market-prices` を実行してからWorkerをデプロイします。約3,960銘柄なら330メッセージ/平日が目安で、通常のQueue操作は送信・読取・削除の約990回/日です。再試行分は追加されます。QueueとCronの設定は同じWorkerにあり、日次開始のための追加Cron枠は使いません。`/api/v1/health` の `market.live_refresh_batches_total`、`market.live_refresh_batches_enqueued`、`market.live_refresh_last_completed_date`、`market.live_refresh_failed_batches` で状態を確認できます。

このWorkerは価格取得専用です。配当・財務は既存のPython版 `yfinance` ワークフロー（`.github/workflows/yfinance-r2.yml`）で週次更新します。実行時刻は毎週月曜03:30（日本時間）で、東京市場の休場日は市場カレンダーの判定で更新をスキップします。日次の株価更新はCloudflare Workerが担当し、週次ワークフローは配当・財務を含む市場データの更新とR2公開を担当します。GitHub Actionsの月間実行枠が上限に達している間は実行できないため、枠のリセット後に再開します。スケジュールはGitHubの既定ブランチにこの設定を反映した後に有効になります。この構成では週次処理用のCloudflare Cron枠は追加しません。

更新は直近の約定価格を含むYahoo Chart応答を使い、リアルタイム配信は保証しません。Yahooの非公式Chart APIへの直接アクセスを使うため、利用条件や可用性の変更により取得できなくなる可能性があります。Workers Freeの1実行あたり50サブリクエスト・同時外向き通信6件の上限内に収めるため、同時取得は6銘柄までにしています。Workers FreeのCPU時間は1実行10msです。

D1 APIは `/api/v1/securities/{銘柄コード}`、定義APIは `/api/v1/diagnosis-definitions`、`/api/v1/valuation-scenarios`、`/api/v1/stock-analysis-definitions`、`/api/v1/watchlist-analysis-definitions` です。現在のR2スナップショットが未配置の場合、株価・PER・PBRは未収録として返されます。

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

## 全銘柄サジェスト

`/api/v1/search-catalog` は R2 の `market/yfinance/search_catalog.json` を返します。検索用一覧は銘柄マスター全件から作り、株価データがある銘柄には直近20営業日の終値×出来高から計算した平均推定売買代金・株価・配当利回りを付けます。日次の yfinance 集計で更新されます。初回だけ先に公開する場合は、R2 の認証情報を設定して `python scripts/yfinance_r2/fetch_and_upload.py --publish-catalog` をリポジトリのルートから実行します。市場スナップショットが未配置でも銘柄名とコードは全件公開されますが、人気順の値は市場データ公開後に入ります。

React の検索画面はこの一覧を1日端末にキャッシュし、銘柄名2文字・コード3文字から端末内で絞り込んで最大20件を表示します。別の Worker を使う場合は `VITE_MARKET_API_BASE_URL` を設定してください。現在のマスターは日本市場の3,960件で、米国株・投資信託の別マスターはまだ含まれません。

`GET /api/v1/monthly-price-history?codes=7203,9984&months=2025-11,2025-12` は、指定した国内株（最大20銘柄、最大12か月）について、R2の日次終値から各月の最終取引日価格を返します。日次Workerが追加した終値も履歴に含めます。月中の指定月は当日までに取得できた最新終値を返し、株式分割後の価格は現在の株数単位に換算します。月次資産推移はこの価格と端末内の現在保有株数から参考評価額を算出します。端末から送るのは銘柄コードと年月のみで、株数や取得単価は送信しません。

`GET /api/v1/dividend-months?symbols=7203.T,AAPL` は、R2 の `market/yfinance/securities/{symbol}.json` にある配当イベントから、直近365日（日本時間の当日を基準）に記録された月を銘柄ごとに返します。1回に最大50銘柄を指定できます。アプリは銘柄コードと取得日を端末内に保存し、日本時間の日付が変わるまで同じ配当月を再取得しません。保有数や取得単価は送信しません。履歴のない銘柄は空の月一覧を返します。


## yfinance v2市場データ移行（ローカル実装）

`.github/workflows/yfinance-r2.yml` の定期実行は日本時間21時に起動し、`XTKS` 営業日を確認してからJ-Quants v2の銘柄一覧とyfinanceデータをR2の `market/yfinance/v2/` へ保存します。検索カタログと銘柄登録APIは公開済みのv2カタログだけを使い、初回yfinance取得に成功する前は旧カタログへフォールバックしません。旧Workerの価格取得キューは、新しい価格を取得せず既存メッセージを処理終了します。

この移行のローカルコードはまだWorkerへデプロイされておらず、v2初回データのR2読戻しも未実施です。GitHub Actionsには `JQUANTS_API_KEY`、`R2_ACCESS_KEY_ID`、`R2_SECRET_ACCESS_KEY` が必要です。移行仕様とR2キー構造はリポジトリ直下の `ai_docs/r2-yfinance-target-spec.md` を参照してください。

## バックエンド運用（分析・通知）

匿名クライアント登録は `POST /api/v1/clients/register` です。返却される `client_id` と `client_secret` は端末内に保存し、Watchlist条件・通知履歴・Push端末登録では `X-Client-Id` / `X-Client-Secret` ヘッダーを送ります。`GET /api/v1/watchlist-targets` はそのクライアントの監視条件だけを返します。保有数量、取得単価、保有金額などのポートフォリオ情報はバックエンドへ送信しません。

ポートフォリオ診断の閾値・重みは KV の `portfolio-analysis-definitions:v1` で配信します。`scripts/seed-kv.ps1` で他の定義とまとめて登録します。`wrangler.toml` には `APP_DEFINITIONS` KV binding を本番 namespace ID とともに設定してください。

Cron は市場スナップショットまたは分析定義が変わった場合に全銘柄を再評価し、それ以外の時間は通知対象のWatchlist銘柄だけを確認します。市場データが3日超古い場合は買場通知を抑止します。分析履歴は30日までは詳細を保持し、30日超は日次1件、365日超は月次1件へ圧縮します。

定期分析では市場スナップショット・銘柄別の分析入力・Watchlist条件を実行内で再利用し、銘柄処理を最大8件ずつ並列化します。実行ログの `metadata_json.durations_ms` に分析・履歴整理・通知処理の所要時間を記録し、実データの負荷を見ながら次の調整を行います。

公開読み取りAPIはIP単位、クライアントAPIは匿名クライアント単位でKV設定の回数制限を適用します。制限応答には `Retry-After` が付きます。JSON APIは既定で `private, no-store` とし、ブラウザの事前要求（CORS OPTIONS）にも応答します。`GET /api/v1/health` ではR2市場データの有無と経過日数、分析定義版、直近Cron結果を確認できます。Cron実行結果は `scheduled_run_log` に記録されます。

FCM送信は `FCM_PROJECT_ID` と `FCM_SERVICE_ACCOUNT_JSON` が設定された場合に動作します。WorkerはGoogle OAuthの短期アクセストークンをサービスアカウント鍵で取得します。端末ごとの送達・再試行状態をD1へ記録し、無効になったFCM端末トークンを停止します。未設定時は通知イベントを保持し、Android端末内のWorkManager通知を引き続き利用します。サービスアカウントJSONはソースや `wrangler.toml` に直書きせず、Cloudflareのsecretとして設定してください。
