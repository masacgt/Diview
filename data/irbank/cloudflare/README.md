# IRBANK → Cloudflare D1

このディレクトリには、整理済みのIRBANKデータをCloudflare D1へ登録するスキーマとWorker APIがあります。銘柄・財務情報はD1、株価スナップショットはR2から配信します。

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

## 銘柄・財務・配当データのインポート

```powershell
node ..\scripts\build-d1-import.cjs
powershell -ExecutionPolicy Bypass -File .\import-d1.ps1
```

生成したSQLを分割して実行し、再実行時は `import-state.json` の成功記録から続けます。

## Android検証

Android debugビルドはD1検証モードが既定で有効です。起動ごとにWorker APIを再取得し、ローカルのダミー株価・配当値をデータ取得失敗時の代替値にしません。Worker URLはGradleプロパティ `diviewD1ApiBaseUrl` で上書きできます。releaseビルドではこの動作は無効です。

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

## 全銘柄サジェスト

`/api/v1/search-catalog` は R2 の `market/yfinance/search_catalog.json` を返します。検索用一覧は銘柄マスター全件から作り、株価データがある銘柄には直近20営業日の終値×出来高から計算した平均推定売買代金・株価・配当利回りを付けます。日次の yfinance 集計で更新されます。初回だけ先に公開する場合は、R2 の認証情報を設定して `python scripts/yfinance_r2/fetch_and_upload.py --publish-catalog` をリポジトリのルートから実行します。市場スナップショットが未配置でも銘柄名とコードは全件公開されますが、人気順の値は市場データ公開後に入ります。

React の検索画面はこの一覧を1日端末にキャッシュし、銘柄名2文字・コード3文字から端末内で絞り込んで最大20件を表示します。別の Worker を使う場合は `VITE_MARKET_API_BASE_URL` を設定してください。現在のマスターは日本市場の3,960件で、米国株・投資信託の別マスターはまだ含まれません。


## バックエンド運用（分析・通知）

匿名クライアント登録は `POST /api/v1/clients/register` です。返却される `client_id` と `client_secret` は端末内に保存し、Watchlist条件・通知履歴・Push端末登録では `X-Client-Id` / `X-Client-Secret` ヘッダーを送ります。保有数量、取得単価、保有金額などのポートフォリオ情報はバックエンドへ送信しません。

ポートフォリオ診断の閾値・重みは KV の `portfolio-analysis-definitions:v1` で配信します。`scripts/seed-kv.ps1` で他の定義とまとめて登録します。`wrangler.toml` には `APP_DEFINITIONS` KV binding を本番 namespace ID とともに設定してください。

Cron は市場スナップショットまたは分析定義が変わった場合に全銘柄を再評価し、それ以外の時間は通知対象のWatchlist銘柄だけを確認します。市場データが3日超古い場合は買場通知を抑止します。分析履歴は30日までは詳細を保持し、30日超は日次1件、365日超は月次1件へ圧縮します。

`GET /api/v1/health` ではR2市場データの有無と経過日数、分析定義版、直近Cron結果を確認できます。Cron実行結果は `scheduled_run_log` に記録されます。

FCM送信処理は `FCM_PROJECT_ID` と `FCM_ACCESS_TOKEN` が設定された場合だけ動作します。未設定時は通知イベントを保持し、Android端末内のWorkManager通知を引き続き利用します。FCMの認証情報はソースや `wrangler.toml` に直書きせず、Cloudflareのsecretとして設定してください。
