# 配当管理 Android APK 16.5: CSV取込・保存・バックアップ仕様

## 1. 対象と確認範囲

本書は、利用者が指定した `配当管理.apk のコピー` の取込・保存・バックアップ機能を記述する。対象APKは application ID `com.haitou.bottomtest`、version name `16.5`、version code `228`。APKのSHA-256は `FB8D3FD3F944E970380A85860D1031A7491458BF1EDD2F2C6D87ADDAFA81C3DF`。

このAPKは、リポジトリの現行Diviewアプリ (`com.kurogane.dividend`) とは別のアプリである。本書はAPKの仕様だけを扱う。

APKマニフェストは `base__abi` と `base__density` のsplitを必須としている。受領ファイルは単体インストールで `INSTALL_FAILED_MISSING_SPLIT` となったため、画面を実行しての操作確認はできていない。以下はマニフェスト、DEX、リソース、APK内サンプルCSVから確定できた事項であり、画面配置や実行時の一部手順は「【要確認】」とした。

## 2. 取込後の端末保存

6社のCSV取込画面とSBI米国株テキスト取込画面は、Realmの既定データベースを開き、`executeTransaction` 内で登録処理を行う。APK起動時はRealmの既定設定を使い、スキーマバージョン19とマイグレーションを設定する。Realmの暗号鍵は設定されていない。

取込はファイルを選ぶだけでは完了しない。アプリは解析した銘柄を一覧にし、利用者がインポートを実行する。結果には銘柄件数、投資信託の照合失敗、スキップ、削除件数などが表示される。既存と同じ銘柄コードのデータを上書きする旨の表示がある。CSVに含まれない銘柄を削除する選択肢もある。

取込レコードには、銘柄コードまたはティッカー、銘柄名、銘柄区分、保有数量、取得単価、現在値、通貨、口座区分などが含まれる。口座区分には一般口座、特定口座、NISA、新NISA成長投資枠、新NISAつみたて投資枠等の表示がある。証券会社CSVから読み取れない情報の初期値や、インポート先ポートフォリオの決定規則は【要確認】。

解析・取込の後に株価更新を始める表示が含まれる。CSVそのものやCSVのどの列が外部通信へ送信されるかは、このAPKの静的解析だけでは確定できない。

## 3. 対応する証券会社と商品

| 証券会社（APK内表示） | CSVから取り込める商品 | 備考 |
|---|---|---|
| SBI証券 | 日本株、投資信託 | 米国株はCSVではなく、画面の指示に従って口座管理の「口座（外貨建）」から表をコピーして貼り付ける別機能 |
| 楽天証券 | 日本株、米国株、投資信託 | 専用CSV取込画面 |
| マネックス証券 | 日本株、米国株、投資信託 | ファイル名やヘッダーから商品種別を判定する |
| 松井証券 | 日本株、米国株 | 投資信託のCSV取込は表示されていない |
| 三菱UFJeスマート証券 | 日本株、米国株、投資信託 | APK内の表記をそのまま記載 |
| 野村證券 | 日本株、投資信託 | 「預り資産（預り証券）」CSVに対応 |

アプリは証券口座のID・パスワードを使った直接連携ではなく、利用者が選択したCSVまたは貼り付けたテキストを読み込む。

### 3.1 CSVの識別と解析

各社専用の取込画面を使う。マネックス証券では次のCSV名を商品別に判定する旨がエラー案内に記載されている。

- 日本株: `stockposition_*.csv`（保有残高一覧）
- 米国株: `us_spotbalance_*.csv`（外国株式 保有残高）
- 投資信託: `fundsellamountposition_*.csv`（投資信託 保有残高）

三菱UFJeスマート証券では、次のファイル名を商品別に判定する旨が記載されている。

- 日本株: `残高一覧_現物株式_*.csv`
- 米国株: `残高一覧_米国株式_*.csv`
- 投資信託: `残高一覧_投資信託_*.csv`

野村證券は「預り資産（預り証券）」CSVを対象とする。ファイルの種別を特定できない、銘柄行がない、投資信託をマスターと照合できない場合は、エラーまたはスキップとして利用者に知らせる。投資信託の名称照合率、重複行の集約方法、文字コード判定順、ファイルサイズ上限は【要確認】。

APKに同梱されたサンプルCSVはUTF-8 BOM付き、BOMなしUTF-8、CP932を含む。これらを再実装時の入力例として [サンプルCSV一覧](samples/haitou-apk-v16.5/README.md) に保存した。

サンプルCSVで確認できる表の構造は次のとおり。これはサンプル内の見出しであり、パーサーが必須とする全列や列番号を示すものではない。文字コードは前掲READMEに記載した。

| サンプルファイル | サンプルから読める構造 | 証券会社の識別 |
|---|---|---|
| `SBI_csv_data.csv` | 「保有証券一覧」内に預り区分ごとの小計と明細。明細列は銘柄コード、銘柄名称、保有株数、売却注文中、取得単価、現在値、取得金額、評価額 | ファイル名と内容はSBI用サンプルを示す |
| `SaveFile (20)-1.csv` | SBIサンプルと同じ「保有証券一覧」型。特定預り、NISA成長投資枠等の区分ごとに見出し・明細が繰り返される | CSV本文だけでは証券会社を確定できない |
| `assetbalance(all)_*.csv`（4件） | 資産合計、商品区分別評価額、保有商品詳細の複数セクション。詳細列に種別、コード／ティッカー、銘柄、口座、数量、平均取得価額などがある。「楽天銀行普通預金残高」の見出しも含む | 楽天証券関連の形式と推定できるが、当該ファイルとアプリ内取込画面の対応は【要確認】 |
| `assetbalance(JP)_*.csv`（2件） | 現在評価額と損益の概要の後に、NISA口座・特定口座等の明細表が続く。列はコード、銘柄名、数量、執行中数量、通常／積立数量、平均取得価額、取得総額 | ファイル内容だけでは証券会社を確定できない |
| `New_file_nomura.csv` | 「預り資産（預り証券）」形式。商品分類、残高、未精算金、評価額、評価レートの概要と、株式・投信の明細がある | 野村證券用。ファイル名と先頭見出しが一致 |
| `spotbalance_20260127.csv` | 1行目が列見出し。銘柄コード、銘柄名、市場、口座区分、預り区分、保有数、取得平均、評価単価、時価評価額、評価損益等 | サンプル本文だけでは証券会社を確定できない |
| `stockposition_*.csv`（2件） | 日付、時刻、銘柄名、コード、市場、口座区分、預り区分、現在値、平均取得単価、保有数、時価評価額、評価損益 | APKのエラー案内にあるマネックス証券の日本株ファイル名 |
| `us_spotbalance_20260128.csv` | ティッカー、銘柄、市場、口座区分、保有数、取得平均（ドル／円）、評価単価、時価評価額、評価損益等 | APKのエラー案内にあるマネックス証券の米国株ファイル名 |

`assetbalance` のファイル名が同じでも、`all`と`JP`ではセクション構造が異なる。先頭行を単一ヘッダーとみなす実装ではなく、サンプルのようなタイトル行・小計行・口座別ヘッダーを含む構造を扱う必要がある。証券会社とファイル形式の全対応はAPK内の各専用パーサーで判定する。

### 3.2 CSV取込の操作と結果

1. メニューから対象証券会社のCSV取込画面を開く。
2. ファイル選択を押して、Androidのファイル選択画面からCSVを指定する。複数ファイル選択が可能な画面もあるが、どの証券会社が対象かは【要確認】。
3. アプリは専用パーサーでCSVを解析し、日本株・米国株・投資信託の種別と行を表示する。
4. 取り込む行を選び、「インポート実行（件数）」を押す。
5. アプリはRealmのトランザクションで登録し、完了件数、スキップ、投信マッチ失敗、削除件数等を表示する。

APKの文字列には「スマートマージ」「上書き」「新しいデータのみ取り込み、古いデータは保持」「全てのデータを入れ替え」の選択肢がある。各選択肢がどのCSV画面に表示されるか、フィールド単位のマージ規則は【要確認】。また、CSVにない日本株・投資信託、または日本株・米国株・投資信託を削除する確認文がある。削除対象の範囲は確認文に示された商品区分に従う。

### 3.3 SBI米国株のテキスト取込

SBI証券の米国株は専用画面「テキストから入力（SBI米国株）」を使う。利用者は口座管理の「口座（外貨建）」ページから米国株式の表をコピーし、アプリのテキスト欄へ貼り付ける。解析結果から銘柄を選択し、インポートを実行する。通常のCSV取込とは別のパーサーを使う。

## 4. GoogleアカウントとGoogle Driveバックアップ

このAPKはAndroid OSの自動バックアップに任せる方式ではなく、アプリ内でGoogleアカウントを選び、Google Drive APIを呼び出してファイルを作成・読み込みする。Googleログインはメールアドレスと `drive.file` スコープを要求する。バックアップ機能のために、Android設定で「Google Oneバックアップ」を有効にする手順はない。

### 4.1 Driveへの保存手順

1. アプリのメニューから、Googleアカウントを選ぶ項目を開く。
2. 利用するGoogleアカウントでサインインする。端末にアカウントがない場合は、先にAndroidのアカウント設定へ追加する。
3. 「バックアップファイルを作成」を選ぶ。
4. 次のバックアップ対象から作成する種類を選ぶ。
   - 銘柄一覧
   - 資産・配当推移グラフ
   - カスタム銘柄
   - 目標利回りアラート
5. 同名のバックアップがある場合は、上書き確認を表示する。確認後、Driveへの作成または更新を行う。
6. 成功時は「バックアップファイルの作成に成功しました」、失敗時は作成失敗のメッセージを表示する。

APKが扱うAndroidバックアップの基本ファイル名は次のとおり。

| データ種別 | 暗号化形式のファイル名 |
|---|---|
| 銘柄一覧 | `BackUpStockInfoAndroid.dvm` |
| 資産・配当推移グラフ | `BackUpRecordInfoAndroid.dvm` |
| カスタム銘柄 | `BackUpCustomStockInfoAndroid.dvm` |
| 目標利回りアラート | `BackUpYieldAlertAndroid.dvm` |

Google Drive APIの検索では `drive` space と `root` が使われる。したがって、APKの実装はDriveのルート領域を検索・保存先として扱うように見える。ユーザーが保存先フォルダーを選ぶ画面は確認できていない。`.json` ファイルも復元対象として検索されるが、Driveバックアップ作成時にJSONを選ぶUIがあるかは【要確認】。

### 4.2 Driveからの復元手順

1. Googleアカウントを選択し、バックアップを作成したアカウントでサインインする。
2. 「バックアップファイルから復元」を選ぶ。
3. Driveから読み込むバックアップファイルを選択する。
4. アプリは`.dvm`を復号し、または`.json`を解析する。対応する銘柄、資産・配当推移、カスタム銘柄、目標利回りアラートの種別と件数を確認する。
5. 「データを読み込みますか？」などの確認を出し、読み込み操作が取り消せない場合はその旨を示す。
6. 成功時は「バックアップファイルの読み込みに成功しました」等を表示する。ファイルがない、復号に失敗する、JSON形式が不正、復元対象がない場合はエラーを表示する。

どのデータ種別を復元するときに既存データを置き換えるか、Googleアカウントに複数の同種ファイルがある場合の選択順は【要確認】。

## 5. バックアップ形式と暗号化

### 5.1 `.dvm` 暗号化形式

APK内の`DvmCryptor`はAES-GCMを使って`.dvm`を暗号化・復号する。

| 項目 | 仕様 |
|---|---|
| ファイル先頭 | ASCII `DVMV1`（5バイト） |
| 暗号 | `AES/GCM/NoPadding` |
| 鍵長 | 256ビット（32バイト） |
| nonce / IV | 生成時に`SecureRandom`で作る12バイト |
| 認証タグ | 128ビット（16バイト） |
| ファイル構造 | `DVMV1` + nonce + ciphertext + GCM tag |
| 復号後 | UTF-8 JSONとして処理 |
| 圧縮 | 暗号化前の圧縮処理は確認できない |

鍵はAPKに固定値として埋め込まれ、ユーザーのパスワード、Googleアカウント、端末Keystoreから導出されない。互換性のための鍵は32バイトのASCII文字列 `DvMgSyncAES256GCMKey2026OttiLab!`。この設計ではAPKを入手できる人が鍵を抽出できるため、`.dvm`の暗号化は第三者に対する強い秘密保持とはならない。新規実装では既存ファイル互換のための鍵と、将来の暗号化設計を分けて扱う。

鍵が異なる、nonceやタグが壊れている、または内容が改変された場合、GCM認証に失敗して復号できない。ファイルの復号に失敗した場合は「ファイルの復号に失敗しました。正しいファイルを選択してください。」を表示する。

### 5.2 暗号化されない保存物

- `.json`形式のバックアップ／エクスポートはJSON本文をそのまま保存する。APKはJSON出力をプレミアム会員向けと表示する。
- CSVエクスポートと証券会社CSVは平文ファイルである。
- 端末内のRealmデータベースは既定設定で初期化され、暗号鍵を設定していない。APKから確認できる範囲では、端末内データベースにアプリ独自の暗号化はない。
- Google Drive上の保存データに対するGoogle側の保護方式は本APKからは確認できない。アプリ側で暗号化されるのは`.dvm`形式だけである。

### 5.3 AndroidバックアップJSONの構造

APK内には4種類の同名AndroidバックアップJSONがあり、いずれもトップレベルは配列である。次の件数はAPK内サンプルの行数であり、実データの固定件数ではない。

| ファイル | サンプル行数 | 主なフィールド |
|---|---:|---|
| `BackUpStockInfoAndroid.json` | 131 | 銘柄・ポートフォリオID、国、保有数と取得単価の通常／NISA／新NISA／つみたて区分、米国株のUSD取得単価、現在値、配当・利回り、権利月、優待、セクター、景気感応度、メモ、並び順 |
| `BackUpRecordInfoAndroid.json` | 3,842 | ポートフォリオID、記録日、日本株・米国株・投資信託の評価額、税引前後別・商品区分別の配当合計、優待合計 |
| `BackUpCustomStockInfoAndroid.json` | 6 | コード、銘柄名、国、価格・利回り通貨、配当月・権利月、分配金、価格、利回り、景気感応度、セクター、順序 |
| `BackUpYieldAlertAndroid.json` | 5 | アラートID、コード、銘柄名、国、作成日時、有効状態、現在価格・配当・利回り、目標利回り |

次のキーと型はAPK内JSONサンプルを実際に解析して確認したもの。型はサンプル配列に現れたJSON型であり、すべてのレコードに全キーがあるとは限らない。

- 銘柄一覧: 文字列=`acquisitionUnitPrice`, `acquisitionUnitPriceNisa`, `acquisitionUnitPriceNisaNew`, `acquisitionUnitPriceNisaNewTsumitate`, `acquisitionUnitPriceUSD`, `acquisitionUnitPriceUSDNisa`, `acquisitionUnitPriceUSDNisaNew`, `acquisitionUnitPriceUSDNisaNewTsumitate`, `code`, `country`, `economicSensitivity`, `economicSensitivityCustom`, `etfFlagKokunai`, `haitouShikyu1`, `haitouShikyu2`, `kenriochi1`, `kenriochi2`, `memo`, `monthOfHaitou`, `monthOfHaitouVesting`, `name`, `nenriForSyutoku`, `numOfHaitou`, `numOfStocksHeld`, `numOfStocksHeldNisa`, `numOfStocksHeldNisaNew`, `numOfStocksHeldNisaNewTsumitate`, `portfolioId`, `priceOfStock`, `sector`, `shareholderBenefitAmount`, `shareholderBenefitAmountPerMonth`, `shareholderBenefitDescription`, `shareholderBenefitPaymentMonth`, `shareholderBenefitVestingMonth`, `totalYield`, `totalYieldNisa`, `totalYieldNisaNew`, `totalYieldNisaNewTsumitate`, `yield`, `yieldRimawari`; 整数=`id`, `order`; 小数=`nenriForSyutokuDouble`, `totalYieldAllDouble`, `valuation`, `valuationGainLoss`, `yieldRimawariDouble`; 真偽値=`isUSD`, `isUSDNisa`, `isUSDNisaNew`, `isUSDNisaNewTsumitate`。
- 資産・配当推移: 文字列=`portfolioId`, `recordDate`; 小数=`investmentTrustValue`, `japaneseStockValue`, `usStockValue`; 整数=`totalHaitouKin`, `totalHaitouKinTax1`, `totalHaitouKinTax1WithoutInvestmentTrust`, `totalHaitouKinTax1WithoutInvestmentTrust_JP`, `totalHaitouKinTax1_JP`, `totalHaitouKinTax1_JP_TRUST`, `totalHaitouKinTax1_US`, `totalHaitouKinTax2`, `totalHaitouKinTax2WithoutInvestmentTrust`, `totalHaitouKinTax2WithoutInvestmentTrust_JP`, `totalHaitouKinTax2_JP`, `totalHaitouKinTax2_JP_TRUST`, `totalHaitouKinTax2_US`, `totalHaitouKinWithoutInvestmentTrust`, `totalHaitouKinWithoutInvestmentTrust_JP`, `totalHaitouKin_JP`, `totalHaitouKin_JP_TRUST`, `totalHaitouKin_US`, `totalShareholderBenefit`, `totalShareholderBenefitWithoutInvestmentTrust`。
- カスタム銘柄: 文字列=`corporateCode`, `corporateName`, `country`, `economicSensitivity`, `monthOfHaitou`, `monthOfHaitouVesting`, `paymentMonthJson`, `priceOfStock`, `sector`, `vestingMonthJson`, `yield`, `yieldRimawari`; 整数=`index`, `numOfHaitou`; 真偽値=`isPriceInUSD`, `isYieldInUSD`。
- 目標利回りアラート: 文字列=`alertId`, `corporateCode`, `corporateName`, `country`; 整数=`createdAt`; 小数=`currentDividend`, `currentPrice`, `currentYieldRimawari`, `targetYieldRimawari`; 真偽値=`isEnabled`。

各フィールドの必須性、意味、欠落時の既定値、日付・金額の単位、将来バージョンとの互換規則は【要確認】。このサンプルにないフィールドを追加フィールドとして許容するかも【要確認】。

## 6. その他のバックアップ・インポート経路

### 6.1 iOS版バックアップのインポート

Androidアプリには「iOS版データインポート」があり、ファイルの種類を自動判別する。案内上の標準的な場所は `iCloud Drive → iCloudDocument`。

| データ種別 | iOSファイル名 |
|---|---|
| 登録銘柄 | `BackUpStockInfo.dvm` / `.json` |
| カスタム銘柄 | `BackUpCustomStock.dvm` / `.json` |
| 配当推移・資産推移 | `BackUpTransitionGraph.dvm` / `.json` |
| 目標利回りアラート | `BackUpYieldAlert.dvm` / `.json` |

ファイル選択後、アプリは形式・種別を判定し、登録銘柄、カスタム銘柄、アラート、推移データの件数を確認画面に表示する。確認後にインポートし、成功件数とスキップ件数を報告する。暗号化`.dvm`は復号し、`.json`はJSONとして読む。

APKの選択肢には「スマートマージ」「上書き」「全てのデータを入れ替え」がある。「全てのデータを入れ替え」では、現在のデータが全て削除される警告を表示する。「新しいデータのみ取り込み、古いデータは保持」という説明もある。スマートマージが各フィールドをどの規則で選ぶかは【要確認】。

### 6.2 ファイルへのエクスポート

APKはファイル出力にCSV、JSON、暗号化`.dvm`の形式を持つ。ユーザーが保存先を選ぶ操作を提供する。JSON出力はプレミアム会員向けと表示される。出力対象ごとの列・JSONスキーマ、CSVの全ポートフォリオ／単一ポートフォリオの違い、`.dvm`の出力対象とGoogle Driveバックアップの同一性は【要確認】。

### 6.3 WEB版とのデータ連携

メニューには「WEB版からデータを読み込み」「WEB連携データを出力」「WEB連携コードの期限を更新」がある。これはGoogle Driveバックアップとは別の連携経路である。コードの発行・有効期限・サーバー側の保存形式と、インポート時の競合処理は【要確認】。

## 7. エラーと利用者への結果表示

- CSVでデータを検出できない場合は「CSVファイルからデータが見つかりませんでした」等を表示する。
- CSV形式を判定できない場合は、対応する証券会社・商品・ファイル名の例と、ヘッダー欠落や文字コードなどの原因候補を示す。
- 投資信託がマスターと一致しない場合は「投信マッチ失敗」として件数を報告し、対象をスキップできる。
- iOSバックアップに対応ファイルがない場合は、4種のファイル名を列挙する。
- `.dvm`復号失敗、JSON形式不正、読み込みエラーは、保存処理を完了扱いにせずエラーを表示する。
- バックアップ作成・読み込みに成功した場合は、専用の成功メッセージを表示する。
- 操作中の二重実行制御、キャンセル時にどこまで処理を戻すか、部分成功後のロールバックは【要確認】。

## 8. 保存したサンプルCSV

APKの `assets/sampleCsv/` に含まれていた13ファイルを、原本のファイル名とバイト内容を保って [サンプルフォルダー](samples/haitou-apk-v16.5/sampleCsv/) に保存した。元の文字コードも変換していない。出所・SHA-256・文字コードの一覧は [README](samples/haitou-apk-v16.5/README.md) を参照。

## 9. 再実装前に確認する事項

1. 画面の実際の遷移順、各確認ダイアログ、表示件数。
2. CSV行の選択状態、複数ファイル選択が可能な証券会社、各社の正確な解析列と文字コード判定。
3. スマートマージの列単位の優先順位、同一コードや同名投信の重複規則。
4. CSVのインポート先ポートフォリオと口座別数量の保存規則。
5. Driveで`.json`も作成できるか、同名ファイルの更新方法、Drive内の正確な保存先。
6. 全件置換と各カテゴリ別復元の削除範囲、失敗時のトランザクション境界。
7. WEB連携コードを使う同期のサーバー契約。

上記は、必須splitを含むAPK一式または動作する配布版を用意して画面操作を確認できれば確定できる。
