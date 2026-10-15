# IRBANK Connect データ

取得データを原本、銘柄別データ、Cloudflare D1取り込み用に分けて保存しています。再生成処理はローカルのデータだけを読み込み、IRBANKへの通信やCloudflareへの登録は行いません。

このIRBANKスナップショットは保存資料と一部の財務互換処理向けです。現行の上場銘柄ユニバース、価格、配当・分割イベント、月次価格の取得・公開元はyfinance v2で、J-Quants v2は銘柄コード一覧に使います。最新のR2構造とアプリ取得規則は `ai_docs/r2-yfinance-target-spec.md` を参照してください。

## フォルダー構成

- `catalog/`: 銘柄一覧と取得時のページ情報 (`securities.json`, `manifest.json`)
- `raw/securities_pages/`: 銘柄一覧取得時のページ応答・バッチ原本
- `raw/financials/`: 年次・四半期・半期の財務バッチ、EDINETコード一覧、取得状態
- `raw/valuation/`: EDINETコード別の評価データ
- `raw/samples/`: IRBANK各APIの取得例
- `raw/acquisition/`: 取得スナップショット、実行ログ・状態
- `organized/by_security/`: 銘柄コードをファイル名にした企業別JSON
- `organized/by_security/validation.json`: 生成物の検証結果
- `organized/d1/`: D1用CSV、テーブル定義、項目・出典情報
- `organized/manifest.json`: 銘柄別データ生成時の件数一覧
- `scripts/`: 原本から一覧や銘柄別データを再生成するスクリプト

## 銘柄コードからデータを探す

例: `organized/by_security/72030.json`。ファイル名には一覧に保存された `security_code` をそのまま使っています。4桁表示コードは `security_code_display`、EDINETコードは `edinet_code` にあります。各JSONには `financials.annual`、`financials.quarterly`、`financials.semiannual` と、取得済みの場合は `valuation` が入ります。

## D1への取り込み

`organized/d1/schema.sql` で `securities`、`financial_periods`、`dividend_events` テーブルを作成し、同名のCSVを取り込みます。配当テーブルには1株当たり年間配当、分割調整後配当、配当基準日、権利付最終日の列があります。現在の保存原本で得られる配当額を登録し、基準日と権利付最終日は不明として空欄にします。権利日はJ-Quantsの決算短信と市場カレンダーを使う取得処理が必要です。`metadata.json` の出典表示・項目定義も保持してください。ここにあるファイルを作成しただけで、Cloudflareへの登録は行っていません。

## 再生成

プロジェクトのルート (`C:\Codex\Diview`) から次を実行します。

```powershell
node data/irbank/scripts/merge-securities.cjs
node data/irbank/scripts/organize-by-security.cjs
```

## 現在のスナップショット

2026-09-27時点の保存データです。銘柄一覧は3,960件、年次財務期間は61,407件、四半期は257,770件、半期は104,025件です。合計422,987期間を銘柄別JSONとD1用CSVで照合済みです。評価データは1,683銘柄分あります。財務期間がない銘柄も銘柄JSONに含めています。銘柄一覧取得元は4,211件を報告していますが、保存応答は3,973行・重複除去後3,960銘柄で、全件取得を保証しません。

年間配当データは現在18,906件（分割調整後DPS）あります。未調整の1株当たり年間配当はサンプルとして17件です。権利付最終日は原本にないため0件で、推定していません。

## 出典と利用条件

データに含まれる出典・利用条件の注記を維持してください。財務・評価データは金融庁EDINET、TDnet、JPX等を出典とする情報を含み、再配信時に出典明示が必要です。株価・信用取引データの利用登録や第三者再配布条件を確認してから公開・再配布してください。
