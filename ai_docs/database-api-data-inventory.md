# Diview データ保存先と API 取得項目

## 調査範囲

2026-10-25 時点の作業ツリーにある Android、Cloudflare Worker、D1/R2 のスキーマ、データ取込スクリプト、GitHub Actions を確認した。ここで「データベース」は、関係テーブルを持つ Cloudflare D1 と、市場データの JSON/CSV オブジェクトを保存する Cloudflare R2 を指す。大量の株価・財務データは主に R2 にあり、D1 がすべての市場データを取り込む構成ではない。

この資料はコード上の取得・保存仕様を記録する。今回、本番 D1/R2 の全レコードを照会していないため、本番の件数、最新日、銘柄ごとの取得成否は示さない。

## データの流れ

- J-Quants API の銘柄一覧から日本株の対象コードと商品区分を作る。応答には33業種名も含まれるが、現行の R2 ユニバース生成ではその列を引き継がず、業種表示には Android 同梱マスターを使う。
- Yahoo Finance のデータを yfinance で取得し、銘柄別の価格、配当・分割履歴、年次財務などを R2 に保存する。
- IRBANK Connect の REST API と保存済みスナップショットから、発行体の決算期別配当、会社予想、利回り、IRBANK 詳細データを作る。更新中の発行体配当インデックスは R2 に保存する。初期・互換データの一部は D1 に取り込む。
- Worker は R2/D1 の保存済みデータをアプリ向け API として返す。Android はレスポンスを端末内キャッシュに保存する。
- Android が直接取得する USD/JPY レートと Google Drive のバックアップは、Cloudflare D1/R2 の市場データには取り込まない。

## 上流データ API と取得項目

| 提供元・API | 取得するデータ | 保存先・用途 |
| --- | --- | --- |
| J-Quants v2 /equities/master | 応答日の Date、銘柄コード Code、33業種名 S33Nm、商品区分 ProdCat。商品区分から株式、ETF、REIT 等を分類する。 | 同期入力に対象コードと商品区分を渡す。取得実装は scripts/yfinance_r2/fetch_jquants_universe.py。S33Nm は取得 CSV に出力されるが、現行の R2 ユニバース生成では保存項目に採用せず、業種表示は Android 同梱マスターを使う。 |
| Yahoo Finance（yfinance）日足 | 各銘柄の完了済み日足 Close と日付。前日比を計算するための当日・前営業日データも読む。通常の更新では日足履歴から配当・分割候補も確認する。 | R2 の銘柄別 current.json、年別 dividends/・splits/。カタログ、検索、日次価格 API の基礎データ。 |
| Yahoo Finance（yfinance）Ticker.get_info() | 銘柄名と、現在値の計算に使う trailingEps、forwardEps、bookValue、dividendRate、dividendYield、payoutRatio、exDividendDate、lastFiscalYearEnd、mostRecentQuarter。取得状態と取得日も記録する。 | R2 の profile.json と current.json。Yahoo の PER/PBR はそのまま採用せず、終値 ÷ EPS、終値 ÷ BPS で再計算する。 |
| Yahoo Finance（yfinance）年次財務諸表 | 損益計算書の売上高・営業利益、貸借対照表の自己資本・総資産・有利子負債・ネット有利子負債・現金等、キャッシュフロー計算書の営業/投資 CF・現金配当支払額。 | R2 の銘柄別 financials/ 年次レコード。期間末日、項目別状態、取得失敗理由を保存する。DOE とネット D/E は同じ期間の入力値が揃う場合に計算する。 |
| Yahoo Finance（yfinance）配当・分割履歴 | 1株当たり配当イベントの日付と金額、株式分割・併合の日付と比率。イベント履歴を修復して年別に保存する。返却配当額は yfinance が分割調整済みと仮定し、Diview 側で比率を再適用しない。 | R2 の年別 dividends/ と splits/。配当月、暦年配当合計、増配・非減配年数、分割履歴 API に使う。 |
| Yahoo Finance（yfinance）月足 | 12月の月末 Close。初回等の対象処理では過去8年分を取得し、分割検出時などに再取得する。 | R2 の月次価格パーティション。Stock Detail の年末価格履歴と monthly-price-history API に使う。 |
| Yahoo Finance（yfinance）配当カレンダー | Ticker.calendar に含まれる将来の権利落ち日候補。値、出典、取得日時、状態を保存する。 | R2 の current.json。配当イベント履歴から推定した日付とは出典を区別する。 |
| IRBANK Connect REST /v1/dividends | 銘柄・EDINETコード・開始決算年度を指定して発行体の配当実績を取得し、最新の決算期と DPS を抽出する。 | R2 の market/yfinance/v2/issuer_dividends/current.json。IRBANK の会社予想や利回りと同じ銘柄別インデックスで管理する。 |
| IRBANK Connect REST /v1/securities/{code}/disclosures と /v1/disclosures/{doc_id} | 直近約3年の開示一覧から配当予想候補を探し、開示文書の meta と xbrl_facts を読み、次期 DPS、開示日、文書 ID、出典、株数基準の正規化情報を抽出する。 | 同じ issuer_dividends/current.json の forecast 項目。文書が見つからない場合は予想なし／未取得の状態を区別する。 |
| IRBANK Connect REST /v1/screening | dividendYield 指標のパーセント値、as_of 日付、銘柄識別情報、出典表示をページング取得する。 | 同じ issuer_dividends/current.json の yield 項目。API の /v1/usage は日次利用枠の確認に使い、銘柄の市場データとしては扱わない。 |
| IRBANK Connect の保存済み取得スナップショット | 銘柄マスター、基本情報、年次・四半期・半期財務、評価値、決算期配当、株主、セグメント、開示、役員・組織情報、日次価格、週次信用残、JSF信用残など、取得済みのファミリーを整形する。 | R2 の market/irbank/v1/ 銘柄別 overview.json、dividends.json、daily_prices.json、weekly_margin.json、jsf_margin.json 等。Worker はこれらを配信するが、銘柄 API の GET ごとに IRBANK へ問い合わせない。 |

### 取得元ではなく計算に使うデータ

- XTKS/XNYS の営業日カレンダーは exchange_calendars パッケージを使って権利日を計算する。外部 API から市場データとして取得するものではない。
- IRBANK データの attribution に EDINET/TDnet 等が記録される場合があるが、現行の定期取得スクリプトが呼ぶ市場データ API は IRBANK Connect REST API である。D1 の CSV 取込処理も外部 API ではなくローカル生成済みファイルを読む。
- 年間 DOE、ネット D/E、PER/PBR、配当利回り、増配年数などの一部は保存した入力値から Diview が計算する。API 提供元の返却値と計算値を区別する。
- J-Quants の調整済み日足 CSV は R2 の debug/jquants/v1/adjusted_daily_quotes.csv に別途保存された静的スナップショットである。Worker の /api/v1/jquants/daily-quotes はこの CSV を返すだけで、自動更新処理はない。Android の現在の日次価格取得にも使わない。

## D1 に保存するデータ

D1 は銘柄マスター、互換用の初期財務・配当データ、ユーザー設定、分析結果、運用状態を格納する。

| テーブル群 | 保存するデータ | 主な投入元 |
| --- | --- | --- |
| securities | 正規・表示用銘柄コード、EDINETコード、銘柄名、指標メタデータ | data/irbank/organized/d1/securities.csv の検証済みインポート。IRBANK Connect 保存データをもとに生成する。 |
| financial_periods | 財務期間、会計基準、連結区分、財務項目 JSON | financial_periods.csv のインポート。現在の build-d1-import.cjs は annual 以外を除外する。四半期・半期の保存済み詳細は R2 側の IRBANK オブジェクトを参照する。 |
| dividend_events | 銘柄、年度、配当イベント種別、年間 DPS、分割調整済み DPS、権利日関連欄、原本 JSON | dividend_events.csv のインポート。現行の CSV では基準日・権利付最終日が不明な行を推定値で埋めない。 |
| issuer_dividend_periods | 決算期単位の配当種別と原本 JSON。年だけのキーに潰さず、期末日の精度を保持する。 | dividend_events の同じインポート処理で生成。dividend_events は旧来の年度単位投影として残す。 |
| app_definition_documents、diagnosis_bands、valuation_scenarios、app_definition_audit | バックエンド設定、診断・評価・分析ルールと変更履歴 | Cloudflare migrations の既定値、管理用更新。市場提供 API の取得値ではない。 |
| clients、push_devices、watchlist_targets | クライアント認証ハッシュ、端末のプッシュトークン、利用者ごとの銘柄・目標利回り・通知設定 | Android からの登録・更新 API。 |
| support_feedback、support_feedback_email | 問い合わせ本文・診断情報・受付番号と、管理者メール送信の再試行状態 | Android の POST /api/v1/feedback。メール送信キューは新規問い合わせから作る。 |
| stock_analysis_state、stock_analysis_history、notification_events、notification_deliveries | Worker が算出した銘柄分析、履歴、通知イベント、端末別配信結果 | 保存済み R2 市場データ、D1 の銘柄・目標値、分析定義から Worker が生成する。 |
| scheduled_run_log、backend_state、api_request_log、market_price_refresh_runs、market_price_refresh_batches | 定期処理結果、分析入力世代、レート制限カウンター、旧価格更新処理の実行状況 | Worker の API、Cron、分析・運用処理。市場提供元の一次データではない。 |

D1 の securities、financial_periods、dividend_events は、API を呼ぶたびに上書きされるライブ市場テーブルではない。CSV 取込スクリプトが検証済み SQL を生成し、別途インポートする。現在の IRBANK 配当実績・予想・利回りの定期更新先は R2 の issuer_dividends インデックスである。

## R2 に保存するデータ

| R2 プレフィックス | 内容 |
| --- | --- |
| market/yfinance/v2/catalog/current.json、universe/current.json | 公開対象銘柄と有効ユニバース。コード、名称、商品区分、公開世代、価格・配当の検索用値を含む。J-Quants の S33Nm は現行ユニバース保存項目に含めない。 |
| market/yfinance/v2/securities/{symbol}/profile.json | 銘柄名などのプロフィール情報。 |
| market/yfinance/v2/securities/{symbol}/current.json | 最新完了日の価格、現在統計、配当集計、カレンダー候補、取得状態。 |
| market/yfinance/v2/securities/{symbol}/dividends/{year}.json と splits/{year}.json | 年ごとの配当イベントと株式分割・併合イベント。古いイベントはアーカイブ領域にも保持する。 |
| market/yfinance/v2/securities/{symbol}/financials/{period_end}.json | 期間末日で識別する年次財務値、計算値、出典別値、項目状態。 |
| market/yfinance/v2/securities/{symbol}/prices/monthly/{year}.json | 年末月次終値。直近期間と過年度アーカイブに分ける。 |
| market/yfinance/v2/issuer_dividends/current.json | IRBANK の決算期 DPS 実績、会社予想、スクリーニング利回り、出典・取得状態。名前空間は yfinance v2 配下だが、これらの値の提供元は IRBANK。 |
| market/yfinance/v2/derived/rights_dates/current.json、income_advice/current.json | 配当イベントと営業日カレンダーから計算した権利日、および保存済み市場データから生成した配当候補。 |
| market/irbank/v1/ | 保存済み IRBANK Connect スナップショットを整形した旧・詳細データ。 |
| debug/jquants/v1/adjusted_daily_quotes.csv | 自動更新されない J-Quants 調整済み日足スナップショット。 |

## Worker が提供する市場・アプリ API

これらは主に保存済み D1/R2 データをクライアントへ返す API であり、検索や画面表示のたびに J-Quants、Yahoo Finance、IRBANK へライブ照会する API ではない。

| Worker API | 主な返却値 | 主な読み取り元 |
| --- | --- | --- |
| GET /api/v1/security-search、/api/v1/search-catalog、/api/v1/live-quotes | 検索候補、価格、価格日、IRBANK の年間 DPS・利回り | yfinance v2 カタログ/current と IRBANK issuer_dividends インデックス |
| GET /api/v1/securities/{code} | 銘柄情報、最新価格、配当、評価指標、取得日時・成功/失敗状態 | yfinance v2 profile/current/sync、IRBANK インデックス、保存イベントの期間情報 |
| GET /api/v1/financial-data/{code} | 年次財務、出典別の seed/yfinance 値、計算値、暦年配当イベント、決算期別 IRBANK 配当 | yfinance v2 financials/dividends、market/irbank/v1 overview、IRBANK インデックス |
| GET /api/v1/dividend-months、/api/v1/share-split-history | 配当イベント、月別の支払情報、分割・併合履歴、権利日情報 | yfinance v2 年別イベントと derived/rights_dates |
| GET /api/v1/monthly-price-history | 指定銘柄・月の終値 | yfinance v2 月次価格パーティション |
| GET /api/v1/income-advice-candidates、/api/v1/popular-holdings | 事前生成済み配当候補、公開カタログから算出する人気銘柄 | R2 の derived カタログと公開価格データ |
| GET /api/v1/irbank/manifest、/api/v1/irbank/catalog、/api/v1/irbank/securities/{code} | IRBANK データの目録、銘柄詳細、日次価格・信用残 | R2 の market/irbank/v1 |
| GET /api/v1/jquants/daily-quotes | 静的な調整済み日足 CSV | R2 の debug/jquants/v1/adjusted_daily_quotes.csv |
| GET /api/v1/analysis/{code}、/api/v1/analysis-history/{code} | 銘柄分析の現況・履歴 | D1 の銘柄／分析記録と R2 の現在市場・財務データ |
| GET /api/v1/*-definitions、/api/v1/definition-audit | 診断・評価・ポートフォリオ分析の定義と監査情報 | D1 |

### Android から D1 に書き込む API

- POST /api/v1/clients/register はクライアント ID と secret のハッシュを登録する。
- PUT/DELETE /api/v1/push-devices は端末トークンと有効状態を保存する。
- PUT/GET/DELETE /api/v1/watchlist-targets はクライアントごとの目標利回り・通知設定を保存・返却・削除する。
- POST /api/v1/feedback は問い合わせ本文、カテゴリ、任意の氏名・メール・診断情報を受付番号とともに保存する。送信先メールの処理状態は D1 の outbox に記録する。

## サーバー D1/R2 に入らないアプリ API

- Frankfurter の GET https://api.frankfurter.dev/v2/rate/USD/JPY は基準通貨、対象通貨、為替レート、日付を返す。Android が SharedPreferences にキャッシュし、D1/R2 には送らない。
- Google Drive API は利用者が選んだ暗号化 DVM バックアップをアップロード・ダウンロードする。保有銘柄や端末内編集値の個人バックアップであり、Diview の共有市場データではない。
- Yahoo Finance の直接 Chart API 呼び出しを行う旧 Worker Queue 実装はソース内に残るが、現行の queue handler は受信メッセージを ACK し、Cron からも価格収集を開始しない。アプリ向けの通常価格経路は yfinance v2 の公開データを読む。

## 主な確認箇所

- 上流の取得・変換: scripts/yfinance_r2/fetch_jquants_universe.py、scripts/yfinance_r2/sync_v2.py、scripts/irbank_r2/refresh_v2_dividends.py、scripts/irbank_r2/refresh_v2_dividend_yields.py、scripts/irbank_r2/prepare_dataset.py
- D1 スキーマ・インポート: data/irbank/organized/d1/schema.sql、data/irbank/cloudflare/migrations/、data/irbank/scripts/build-d1-import.cjs
- Worker API: data/irbank/cloudflare/src/index.js
- Android API 呼び出し: kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/CloudflareMarketRepository.kt、ServiceHttp.kt、GoogleDriveBackupClient.kt
- 定期処理: .github/workflows/yfinance-r2.yml、.github/workflows/irbank-v2-dividend-batch.yml
