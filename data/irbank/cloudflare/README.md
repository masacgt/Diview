# IRBANK → Cloudflare D1

このディレクトリには、Cloudflare D1/R2を使うWorker APIがあります。D1は運用・分析データ、R2はIRBANKの保存データと市場データを保持します。検索・日次価格・月次価格・暦年の配当イベントは `market/yfinance/v2/` を参照します。現在の年間DPSと利回りは同じv2名前空間のIRBANK配当インデックスを優先し、IRBANK会社予想、次に分割調整済み実績を使います。R2バケットは非公開で、アプリにはWorker APIを通じて配信します。

## Android財務・問い合わせAPI

新規問い合わせの管理者メール通知と再試行については [問い合わせメール通知](docs/feedback-email.md) を参照してください。Worker公開前に追加の `0006_feedback_email.sql` を適用します。

`GET /api/v1/financial-data/7203` は、v2のプロフィール・現在値・年次財務・配当履歴を返します。`dividends.records` はyfinanceの日足履歴から取得した修復済み配当イベントを暦年単位で集計し、完了した年だけを `calendar_year` と `calendar_year_distribution_per_share` で返します。金額基準は `dividends.amount_basis=yfinance_split_adjusted_assumption` で、IRBANKの決算期DPSとは別の履歴です。一方、現在の年間DPSは `market/yfinance/v2/issuer_dividends/current.json` のIRBANKデータから選び、予想がなければ最新の分割調整済み実績を返します。予想利回りはIRBANKスクリーニングの `dividendYield` と基準日を優先し、IRBANK値がない場合だけ選択した終値とIRBANKのDPSから計算します。`issuer_dividends.records` はIRBANK v1の銘柄別年間実績を返し、最新年度は配当インデックスの更新値で上書きします。会社予想、最新実績、出典も分けて返します。IRBANKの配当額が未取得の場合はyfinanceの配当額に戻さず、状態を `unknown` として返します。暦年の実配当イベント履歴は引き続きyfinance由来です。v2配当履歴が空でも404にせず項目状態を併記します。銘柄情報の8期編集値は端末内に暗号化保存し、R2の共有データを変更しません。

`GET /api/v1/securities/7203` は `data_retrieval` にyfinance ticker、取得元、通貨、最終取得試行日時、同期状態、失敗理由を含めます。`company_info` には会社情報・統計の最終取得日、直近の試行状態、再試行予定、失敗分類・理由を返します。個別の取得日時が保存されていない既存レコードでは、最終成功同期日を参考値として返し、`fetch_date_basis=last_successful_sync_fallback` と状態 `stale` で区別します。この場合、個別取得時刻を誤認させないよう `last_fetched_at` はnullです。新しい個別取得日時には `fetch_date_basis=company_info_fetch` を付けます。`valuation` のPER/PBRは日次終値をyfinanceの保存済みEPS/BPSで割ってWorkerでも再計算し、Yahooの報告倍率をそのまま返しません。`dividend_history` は履歴取得状態、取得元、直近取得範囲、保存済み配当イベントの最古日・最新日を返します。保存期間はR2の通常・アーカイブ年次パーティション内のイベント日から求め、取得範囲とは区別します。Androidの銘柄情報画面はこの内容を「データ取得情報」として表示します。

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

D1の財務インポートは年次だけを生成します。D1の `dividend_events` と年次 `financial_periods` は分析APIや互換処理で使われます。検索・日次価格・暦年配当イベント・月次価格の新しいデータ経路は、yfinance v2のR2カタログと銘柄別オブジェクトです。年間DPS・利回りはIRBANK APIの実績と開示予想をv2のIRBANK配当インデックスへ集約して配信します。IRBANKのカタログと銘柄概要APIは、IRBANKデータの参照用に引き続き提供します。

## J-Quants調整済み日足API

`GET /api/v1/jquants/daily-quotes` はR2の `debug/jquants/v1/adjusted_daily_quotes.csv` をCSVストリームで返します。対象期間は2024-07-22〜2026-07-17、2,146,603行、4,736銘柄です。列は `Date,Code,AdjFactor,AdjO,AdjH,AdjL,AdjC,AdjVo` です。取得済みスナップショットを返すため、自動更新はありません。`HEAD` と単一バイト範囲の `Range` 要求に対応し、IPごとに60秒5回までです。R2の公開URLは無効のまま、Worker API経由で配信します。

```powershell
curl.exe -fL "https://diview-irbank.omasa.workers.dev/api/v1/jquants/daily-quotes" -o adjusted_daily_quotes.csv
curl.exe -fL -r 0-1023 "https://diview-irbank.omasa.workers.dev/api/v1/jquants/daily-quotes" -o adjusted_daily_quotes_part.csv
```

## 銘柄・財務・配当データのインポート

```powershell
node ..\scripts\build-d1-import.cjs
powershell -ExecutionPolicy Bypass -File .\import-d1.ps1 -ImportDir '<生成処理が表示した新規ディレクトリ>'
```

旧 `import-d1.ps1` 単独実行は使用しません。生成処理が表示した新規ディレクトリを
`powershell -ExecutionPolicy Bypass -File .\import-d1.ps1 -ImportDir <生成ディレクトリ>` に渡します。
先にレビュー済みのマイグレーション（0005を含む）を適用してください。
全SQLのマニフェストとSHA-256を検証してから投入し、再開状態はそのディレクトリ内の
`import-state-v2.json` にファイルハッシュ付きで保存します。旧SQL・旧完了状態は再利用しません。

期間キー破損の修復には、先に `wrangler d1 export` でバックアップを取得します。
`scripts/plan-financial-repair.py --snapshot <SQLバックアップ> --source <元CSV> --output <新規出力先>`
は本番へ接続せず、元CSVとの一致、復元、再実行の安全性をバックアップ上で検証します。
生成後に `apply-financial-repair.ps1 -PlanDir <出力先>` を実行し、改めて本番をエクスポートして結果を照合します。
この修復は確認できた破損キーのグループだけを復元し、元CSV全体の無条件再投入はしません。

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

`GET /api/v1/security-search?q=...` はv2カタログのコード・表示名を検索し、価格、価格日、直近12か月の配当合計、利回りを返します。利回りはyfinanceの `dividendRate`（Yahoo Financeが示す今後12か月の年間配当見積もり）を終値で割り、この値がない場合は取得日までに記録された今期の配当イベント合計を使います。`dividendRate` は会社が確定した次会計年度DPSとは区別します。検索時にyfinanceへ問い合わせることはありません。空の検索欄に表示する既存候補の価格も、`GET /api/v1/live-quotes?codes=7203,8306` でR2カタログから読みます。

候補を選んで銘柄詳細・登録情報を開くと、`GET /api/v1/securities/{code}` がv2の `profile.json` と `current.json` を読みます。J-Quantsの有効ユニバースとv2カタログにない銘柄は返しません。業種表示はAndroid同梱の業種マスターを使います。

### 端末の日次価格一覧

`GET /api/v1/search-catalog` はv2の `catalog/current.json` を返します。`GET /api/v1/live-quotes` は同じ公開世代の価格をページ単位で返し、`codes` を指定した場合は候補コードだけを返します。どちらもWorkerからYahoo Financeへ接続せず、yfinance更新ジョブが公開した日次データを配信します。

Androidは端末ごとに最終試行時刻、最終成功時刻、成功した日本時間の日付、取得状態を保存します。同じ日本時間の日に成功済みなら全件一覧を再取得せず、前回成功から12時間未満の場合も取得を見送ります。失敗時は端末内の前回データと成功時刻を維持し、後のアプリ起動時に再試行できます。個別の検索・登録時は該当銘柄の公開済みR2データを読みます。

### 配当・月次価格

`GET /api/v1/monthly-price-history?codes=7203,9984&months=2025-11,2025-12` は指定月の月末最終営業日価格をv2から返します。通常領域に直近13か月、アーカイブに過去10年分を保持します。価格にはyfinanceの `Close` を使い、株式分割比率を再適用しません。要求に含めるのは銘柄コードと年月だけです。

`GET /api/v1/dividend-months?symbols=7203.T` はv2の年別配当イベントから直近365日のイベント、配当月、金額基準、yfinance修復フラグを返します。権利日派生値は `market/yfinance/v2/derived/rights_dates/current.json` から読みます。将来の権利落ち日は、過去5年以内に同じ月の配当イベントが2年以上ある場合は履歴からの推定を優先し、該当しない場合に `Ticker.calendar`、最後に `Ticker.get_info` の日付を使います。履歴推定状態と情報源を応答に含めます。権利付き最終日と推定権利確定日は `.T` 銘柄にXTKS、米国銘柄にXNYSカレンダーを使って計算します。履歴推定イベントは権利日通知に使いません。正常に取得した空履歴と未取得状態は `history_status` で区別します。定期取得は固定したyfinance 1.7.0・SciPy 1.18.1で `history(period="max", interval="1d", actions=True, repair=True)` を使います。成功した取得範囲を正本として既存イベントを置き換え、取得失敗時は前回イベントを維持して失敗状態を記録します。配当・分割イベントは直近10年を通常領域、それより古いイベントを無期限アーカイブに保持します。

財務APIには財務諸表・評価値の項目単位の旧データ互換処理が残っています。配当履歴はyfinance v2イベントだけを使い、v2公開前は `not_fetched` として扱います。分析のD1依存は移行完了まで維持します。IRBANK全体を一括アーカイブ・削除する処理は行いません。

### 本番反映状況と有効化

2026-10-14時点でv2同期、Worker、Androidの変更はコミット `edcd12c2` として `main` にプッシュ済みです。R2への初回v2公開とWorker本番デプロイは未実施です。本番APIを読み取り確認したところ、`/api/v1/security-search?q=7203` は名称だけを返し、`/api/v1/live-quotes` は `legacy_unpublished` を返しました。そのため、端末にインストールした最新APKでも検索候補の株価・利回りを本番データで確認できていません。

有効化にはGitHub Actionsの `JQUANTS_API_KEY`、`R2_ACCESS_KEY_ID`、`R2_SECRET_ACCESS_KEY` を設定し、営業日にv2同期を実行してR2の公開データを読戻し確認した後、Workerをデプロイします。デプロイ後は検索候補、銘柄詳細、配当履歴、月次価格、端末の日次一覧をそれぞれ確認してください。詳細な仕様はリポジトリ直下の `ai_docs/r2-yfinance-target-spec.md` に記載しています。

## バックエンド運用（分析・通知）

匿名クライアント登録は `POST /api/v1/clients/register` です。返却される `client_id` と `client_secret` は端末内に保存し、Watchlist条件・通知履歴・Push端末登録では `X-Client-Id` / `X-Client-Secret` ヘッダーを送ります。`GET /api/v1/watchlist-targets` はそのクライアントの監視条件だけを返します。保有数量、取得単価、保有金額などのポートフォリオ情報はバックエンドへ送信しません。

ポートフォリオ診断の閾値・重みは KV の `portfolio-analysis-definitions:v1` で配信します。`scripts/seed-kv.ps1` で他の定義とまとめて登録します。`wrangler.toml` には `APP_DEFINITIONS` KV binding を本番 namespace ID とともに設定してください。

Cron は市場スナップショットまたは分析定義が変わった場合に全銘柄を再評価し、それ以外の時間は通知対象のWatchlist銘柄だけを確認します。市場データが3日超古い場合は買場通知を抑止します。分析履歴は30日までは詳細を保持し、30日超は日次1件、365日超は月次1件へ圧縮します。

定期分析では市場スナップショット・銘柄別の分析入力・Watchlist条件を実行内で再利用し、銘柄処理を最大8件ずつ並列化します。実行ログの `metadata_json.durations_ms` に分析・履歴整理・通知処理の所要時間を記録し、実データの負荷を見ながら次の調整を行います。

公開読み取りAPIはIP単位、クライアントAPIは匿名クライアント単位でKV設定の回数制限を適用します。制限応答には `Retry-After` が付きます。JSON APIは既定で `private, no-store` とし、ブラウザの事前要求（CORS OPTIONS）にも応答します。`GET /api/v1/health` ではR2市場データの有無と経過日数、分析定義版、直近Cron結果を確認できます。Cron実行結果は `scheduled_run_log` に記録されます。

FCM送信は `FCM_PROJECT_ID` と `FCM_SERVICE_ACCOUNT_JSON` が設定された場合に動作します。WorkerはGoogle OAuthの短期アクセストークンをサービスアカウント鍵で取得します。端末ごとの送達・再試行状態をD1へ記録し、無効になったFCM端末トークンを停止します。未設定時は通知イベントを保持し、Android端末内のWorkManager通知を引き続き利用します。サービスアカウントJSONはソースや `wrangler.toml` に直書きせず、Cloudflareのsecretとして設定してください。
