# Diview アプリの表示データと計算区分

## 調査範囲

2026-10-25 時点の作業ツリーにある Android ネイティブアプリを対象に、画面に出る主な投資・配当データと、その値がどこで作られるかを整理する。画面の挙動は `kurogane-dividend/android/` の実装を根拠とし、`archive/` の旧画面や APK は対象にしない。

ここでいう「取得値を表示」は、Android が値を受け取り、単位や桁を整えて表示する場合を指す。Worker が API を返す前に計算した値も、Android が再計算しなければこの区分に含め、「API側で計算済み」と注記する。「計算表示」は Android が保有数、価格、配当、税設定などから計算した値を指す。「混在」は、同じ表示欄または一つの画面領域に両方が含まれる場合を指す。

本資料はコードから表示経路を確認したもので、本番 API の全銘柄・全レコードを照会したものではない。値の最新性や銘柄ごとの提供状況は保証しない。

## データが画面に届くまで

株価・配当・財務の多くは、Yahoo Finance や IRBANK 由来のデータを同期処理が Cloudflare R2 などに保存し、Worker API が Android に返す。画面の株価は公開済みの価格データを使い、画面を開いた時点のリアルタイム値とは限らない。Android は取得結果を端末キャッシュにも保持する。銘柄名や業種は銘柄カタログや同梱マスターも使う。USD/JPY は Cloudflare の市場データ経路とは別に Android が取得し、端末に保存する。

API の値には、外部データの転送値だけでなく、サーバー側で集計・計算した指標も含まれる。画面側の計算区分と、上流での計算有無は分けて読む。

## 画面別の表示データ

### 資産管理

| 表示項目 | 区分 | 内容 |
| --- | --- | --- |
| 銘柄名、コード、商品区分、業種 | 取得値を表示／利用者登録 | 銘柄カタログや端末内マスターの情報を表示する。保有銘柄は利用者が追加・編集・取込できる。 |
| 保有数量、取得単価、口座区分 | 利用者入力・端末保存 | 保有登録や CSV/DVM 取込で保存する値。 |
| 現在株価、1株配当、配当月、USD/JPY | 取得値を表示 | Worker API、配当イベント、為替取得結果を使う。取得した値はキャッシュから表示される場合がある。 |
| 評価額、投資元本、含み損益、損益率 | 計算表示 | 保有数・価格・取得単価から計算する。投資信託は保有口数を 10,000 口単位で換算し、米ドル資産は為替を掛ける。 |
| 銘柄ごとの利回り | 混在 | 現在利回りは yfinance の `dividendYield` を優先する。未取得時は DPS と株価から基準が一致する IRBANK 利回りを使い、それもなければ DPS ÷ 株価で計算する。取得単価比利回りは DPS ÷ 取得単価で計算する。 |
| 年間配当見込み、税引後概算、平均利回り | 計算表示 | DPS・保有数・通貨換算を集計する。税引後表示には口座区分、非課税保有割合、米国株の現地源泉徴収設定を適用する。平均利回りの分母は評価額または取得額の設定に従う。 |
| 次回配当の月・金額目安 | 計算表示 | 登録済み配当月から次の月を選び、年間 DPS と保有数を配当月数で割って目安を計算する。実際の入金予定額ではない。 |

### 配当分析

| 表示項目 | 区分 | 内容 |
| --- | --- | --- |
| 現在の年間配当見込み、税引前後の合計 | 計算表示 | 現在の保有銘柄すべてについて、現在の DPS と保有数を使って合計する。税引後額は端末の口座・源泉徴収設定を反映する。 |
| 取得額配当利回り、評価額配当利回り | 計算表示 | 年間配当見込みを現在の投資元本または評価額で割る。 |
| 月別配当グラフ、四半期比較 | 混在 | 取得済み配当イベントの日付・金額を月別に集計し、現在の保有数と現在の税設定で換算する。月別・四半期額はアプリ計算値。イベント日を実際の入金日として確認した値ではない。 |
| 銘柄別・業種別・商品区分別の構成比 | 計算表示 | 保有銘柄の配当額または評価額を集計し、合計に対する比率を計算する。小口を「その他」にまとめる表示もある。 |
| 過年度の年間配当推移 | 混在 | 過年度は取得できた国内株の暦年配当イベントを現在の保有数で換算する。選択中の年のうち現年は、国内株・米国株・投資信託を含む現在の年間配当見込みに置き換える。過年度実績と現年予想では対象と基準が異なる。 |
| 資産・元本の年次／月次推移 | 混在 | 月末株価は API 取得値。現在保有する国内株数を各時点の月末株価に掛け、現在の保有数を過去に当てはめた参考評価額を計算する。元本も現在の保有分を比較基準にするため、過去時点の実際の保有資産額ではない。 |
| ポートフォリオ診断、分散スコア、偏り、推奨文 | 計算表示 | 保有評価額・配当・財務指標・配当履歴から Android が評価帯、スコア、説明文を作る。必要な入力がない項目は評価対象から外す。 |

### Watchlist

| 表示項目 | 区分 | 内容 |
| --- | --- | --- |
| 銘柄名、コード、現在株価、年間 DPS、配当月 | 取得値を表示／利用者登録 | 銘柄情報は API・端末キャッシュから取得する。目標利回りや通知設定は利用者が登録する。 |
| 現在利回り、目標達成率、買い場到達数 | 混在 | yfinance の `dividendYield` を優先し、未取得時は DPS の基準が一致する IRBANK 利回り、それもなければ DPS ÷ 株価で計算する。取得した現在利回りを利用者の目標利回りと比較する。 |
| 目標株価、現在株価との差 | 計算表示 | 現在 DPS と利用者の目標利回りから目標株価を計算し、現在株価との差を表示する。 |
| 目安購入額・年間配当額 | 計算表示 | 日本株は 100 株、米国株は 1 株を仮定し、株価・DPS・為替から計算する。税引前の試算。 |
| 月別・四半期別の配当試算、権利落ち月マトリクス | 計算表示 | 取得済みのイベント月に年間配当見込みを均等配分する。配当月が不明な銘柄は月別計算から除外する。実際の権利日・入金月・将来配当額を示すデータではない。 |
| 配当候補・空白月の補完候補 | 混在 | 候補情報や銘柄指標は API から取得し、Watchlist の配当月や登録済み銘柄との照合・並べ替えをアプリ側で行う。 |

### 銘柄詳細

| 表示項目 | 区分 | 内容 |
| --- | --- | --- |
| 銘柄名、業種、現在株価、現在 DPS | 取得値を表示／混在 | 銘柄情報・株価・DPS は API またはキャッシュを優先する。保有・Watchlist 登録値が補完に使われる場合がある。配当未取得と無配確認済みは別状態として扱う。 |
| 現在利回り | 混在 | yfinance の `dividendYield` を優先する。未取得時は現在 DPS と基準が一致する IRBANK 利回りを使い、それもなければ DPS ÷ 株価で計算する。 |
| 決算期別配当実績・会社予想 | 取得値を表示 | IRBANK の発行体別配当記録を表示する。分割調整後 DPS があればそちらを表示に使う。 |
| 暦年配当イベント・配当月・分割履歴 | 取得値を表示／計算表示 | イベント日・1株当たり金額・分割比率は取得値。イベントを年・月ごとにまとめた合計やグラフはアプリ計算値。暦年イベント集計を決算期配当実績と同一視しない。 |
| PER・PBR | 混在 | yfinance の `trailingPE`・`priceToBook` を優先する。未取得時は現在株価÷決算 EPS/BPS で計算する。 |
| 営業利益率 | 混在 | yfinance の `operatingMargins` を百分率にして優先する。未取得時は最新年次の営業利益÷売上高で計算する。 |
| フリーキャッシュフロー率 | 計算表示 | 営業 CF と投資 CF の合計を売上高で割って計算する。 |
| 配当性向 | 取得値を表示（表示単位を調整） | API が返す値を使い、比率で返る値は画面表示用に百分率へ換算する。 |
| 自己資本比率、DOE、ネット D/E など | 取得値を表示（API側で計算済みを含む） | API の財務レコードに含まれる値を画面に使う。特に DOE・ネット D/E はサーバー側で計算された値を受け取り、画面側では再計算しない。 |
| 過去利回りの最小・平均・最大 | 計算表示 | 財務・評価履歴 API から得た各期の利回り値を使い、直近の比較可能な期間についてアプリが範囲と平均を計算する。 |
| 増配率・連続年数・財務健全性／買い場診断 | 混在 | 配当履歴・財務指標・分析 API の値を使う。CAGR、評価スコア、判定帯、説明文などはアプリが入力状況に応じて計算・生成する。未取得項目は推定で補わない。 |
| 買い増しシミュレーター | 計算表示 | 画面で指定した追加株数と現在株価・DPSから購入額や配当増加額を計算する。 |

### Roadmap

| 表示項目 | 区分 | 内容 |
| --- | --- | --- |
| 月間・年間配当の現在値 | 計算表示 | 保有銘柄の年間配当見込みを集計し、12 で割って月額にする。税引前後はアプリの配当計算設定に従う。 |
| 目標額、達成段階、進捗率、残額 | 計算表示 | アプリ内に定義された段階別目標と現在の月額を比較して算出する。目標額・説明文は固定コンテンツで、取得市場データではない。 |
| 成長シミュレーター | 利用者入力＋計算表示 | 利用者が設定する毎月の積立額や想定利回りを保存し、将来値を試算する。市場から取得した将来予測値ではない。 |

### 設定・取込・補助画面

| 表示項目 | 区分 | 内容 |
| --- | --- | --- |
| 税計算、表示設定、通知、Watchlist 目標値 | 利用者入力・端末保存 | 利用者が変更する設定値を端末またはアプリの保存先に保持する。 |
| 為替レート | 取得値を表示 | Android の為替取得機能が返す USD/JPY と日付を表示・保存する。円換算額はこの値を使った計算表示。 |
| CSV/DVM の取込プレビュー | 混在 | ファイル内の利用者データを読み込み、銘柄数や取込可否などをアプリが集計して表示する。 |
| 問い合わせ、通知履歴、バックアップ状態 | 利用者入力／取得状態 | 入力本文や設定は利用者由来。受付番号、送信・取得結果などは API や処理結果に基づいて表示する。 |

## 表示を読むときの注意

- **表示値が 0 でも、無配が確定したとは限らない。** DPS が未取得の状態と、無配を確認した状態は分けて扱う。画面・APIの状態表示を併せて確認する。
- **金額の円換算には為替が必要である。** USD/JPY が未取得なら米ドル資産を円合計から除外する画面や、円換算を未計算とする画面がある。
- **予想・試算と実績を区別する。** 現在 DPS に基づく年間配当見込み、取得イベントから作る履歴集計、決算期別の会社実績・予想は、対象期間と基準が異なる。
- **プライバシー表示は金額を隠す表示制御である。** 元データや計算自体を置き換えるものではない。
- **端末キャッシュや取得状況が表示結果に影響する。** API が未取得・失敗の場合、保存済み値を表示する画面がある。取得失敗を「0」や無配に読み替えない。

## 主な実装箇所

- 画面遷移と画面構成: [MainActivity.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/MainActivity.kt)
- 保有銘柄・評価額・利回りモデル: [Models.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/Models.kt)、[PortfolioMath.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/PortfolioMath.kt)、[DividendTax.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/DividendTax.kt)
- 財務 API の変換と財務指標: [FinancialData.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/FinancialData.kt)
- 資産管理集計: [DividendViewModel.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt)、[AssetManagementScreen.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt)
- 配当分析: [AnnualDividendScreen.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt)、[DividendHistory.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/DividendHistory.kt)
- Watchlist: [WatchlistScreen.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/WatchlistScreen.kt)、[WatchlistProjection.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/WatchlistProjection.kt)
- 銘柄詳細・診断: [StockDetailScreen.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt)、[StockAnalysisNarrative.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/StockAnalysisNarrative.kt)
- ロードマップ: [RoadmapScreen.kt](../kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/RoadmapScreen.kt)
- API の提供元・保存先・項目一覧: [database-api-data-inventory.md](database-api-data-inventory.md)
