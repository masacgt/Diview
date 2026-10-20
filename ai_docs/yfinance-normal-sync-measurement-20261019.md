# 2026-10-19 通常営業日同期の計測票

## 目的

営業日21時台に自動起動する通常同期の実測値を記録し、GitHub Actionsの月間2,000 runner minutes以内に収まる見込みかを確認する。手動同期や同一営業日の再実行は通常同期の測定に含めない。

## 実行情報

| 項目 | 記録 |
|---|---|
| Actions run URL / ID | [run #159 / 39304823002](https://github.com/masacgt/Diview/actions/runs/39304823002) |
| workflow / job | `yfinance-r2.yml` / `market-calendar` + `v2-daily-sync` |
| commit SHA | `f172861b4cd060ebde1bc0b28322e9af270f4861` |
| 起動種別 | scheduledであることを確認 |
| 日本市場営業日 | 2026-10-20 JSTをXTKS営業日・市場OPENと判定 |
| 対象価格日 | 2026-10-19 |
| workflow結果 | 部分失敗。全ユニバース処理・公開後、ticker失敗で終了コード1 |
| GitHub Actions実行時間 | 同期job 666秒、calendar job 25秒。job実行時間合計11分31秒。run timing APIのworkflow wallは697秒、`billable.UBUNTU.total_ms=0`（公開リポジトリの課金値であり、処理時間の代用にはしない） |
| 同月のrunner minutes累計 | 未取得。run #159単独のwall時間だけでは月合計を判定しない |

## 同期計測

| 指標 | 実測値 |
|---|---:|
| J-Quantsユニバース銘柄数 | 4,442 |
| 銘柄収集スクリプト時間 | 624.886秒 |
| 銘柄ごとのyfinance取得時間 | yfinance active wall 89.907秒（R2との重複を除いた時間は0秒） |
| R2取得専用時間 | 420.313秒 |
| yfinance/R2重複時間 | 89.907秒 |
| yfinance取得専用時間 | 0秒 |
| 待機・その他時間 | 0.012秒 |
| カタログ生成時間 | 97.985秒 |
| R2公開時間 | 9.130秒 |
| 高水準yfinance呼び出し数（メソッド別） | 合計622。daily 5d+actions 237、ticker_get_info 55、year-end 8y初回43、daily 1y+actions 41、actions max 19、annual income/balance/cashflow/valuation各55、actions 1y 7 |
| R2操作数（get/list/put別） | get_object 12,114、get_body 10,398、list_objects_v2 1,945、put_object 5,192、delete_object 7 |
| R2操作時間（合計） | active wall 510.220秒（R2操作durationの並列合算は11,344.899秒） |
| HTTP 429件数 | 0 |
| 期待価格日で終値取得成功 | 68銘柄で対象日の終値未取得ログ。成功件数の独立した要約値はログにない |
| 取得成功 / 部分失敗 / 全失敗 | 成功4,335、失敗107（部分失敗状態を含むカタログを公開） |
| no_data / transient / unknown | 同一日の404再試行抑止52件、AttributeError不明分類55件 |
| 年次財務の更新対象 / 実取得 | ローテーション3/5。yfinance各財務系統呼び出し55回（成功数の要約なし） |
| 12月株価の更新対象 / 実取得 | 年末一括更新なし。初回8年価格取得43回 |
| 配当・分割履歴のローテーション対象 / 実取得 | ローテーション3/20。全期間actions max 19回、1年actions 7回 |
| 同一営業日データ再利用 | 手動run #158が同じセッション2026-10-19を先に公開していた。run #159は独立した新規セッション負荷の測定対象外 |
| 公開カタログ件数 | 4,356（generation `2026-10-19`） |

## 月間2,000分判定

通常営業日のworkflow runner minutesを、同月の既実行workflow分数に加算して判定する。同期スクリプト秒数だけでrunner minutesを推定しない。Actions設定・依存関係取得などを含むジョブの実測を使う。

| 算定 | 値 |
|---|---:|
| 今月の実行済みrunner minutes（今回を除く） | 未取得 |
| 10月19日通常同期runner minutes | active job時間11分31秒。run timing APIはworkflow wall 697秒、`billable.UBUNTU.total_ms=0`。同一セッション再実行のため月間推定には不採用 |
| 今月の合計runner minutes | 未実施 |
| 2,000分までの残り | 未実施 |
| 残る営業日の実測基準予測 | 未実施 |
| 判定 | 実測複数回後に判定 |

## 判定条件と留意点

- 初回移行、手動 `sync`、同一営業日の再実行、ベンチマークを通常同期と混ぜない。
- 日次終値は対象営業日の終値か確認する。前営業日へフォールバックした測定は通常成功として数えない。
- yfinance、R2、カタログ、公開の時間と呼び出し数を同じrunから記録する。
- 1回のrunだけで月間予測を確定しない。通常営業日の複数回を比較し、更新ローテーション群や失敗再試行の差を含める。
- 銘柄データとカタログは失敗時に前回値が残り、今回の公開世代に反映されたことを確認する。

## 実施状態

run #159は2026-10-20 JSTに通常scheduleでカレンダーゲートを通過し、全4,442コードを走査して公開した。ただし同じセッションを先行する手動run #158が処理済みだったため、複数の独立した通常営業日実測が揃うまで月2,000分の判定は保留する。HTTP 429は0件。2060.Tの `Ticker.calendar` は保存元・状態・日付を確認できたが、最終取得日時は2026-10-17のままで、run #159による再取得ではない。

## 2026-10-20 追跡実測（手動run #161、月間通常同期の標本外）

| 項目 | 実測 |
|---|---:|
| Actions | [run #161 / 39328353180](https://github.com/masacgt/Diview/actions/runs/39328353180) |
| 起動種別 / 対象セッション | workflow_dispatch / 2026-10-19 |
| J-Quantsコード数 | 4,442 |
| 成功 / 失敗 / カタログ | 4,335 / 107 / 4,356 |
| yfinance高水準呼び出し | 454、HTTP 429は0 |
| 同期job / カレンダーjob | 5,133秒（85分33秒）/ 25秒 |
| 合計active runner time | 5,158秒（85分58秒） |
| workflow wall | 5,795秒（96分35秒）、billable.UBUNTU.total_msは0 |
| 同期スクリプト / security collection | 5,089.015秒 / 4,954.814秒 |
| catalog build / publication | 116.187秒 / 11.310秒 |
| R2 active wall / R2-only / yfinance-active overlap | 4,954.798秒 / 4,851.527秒 / 103.271秒 |
| R2操作数 | get_object 84,531、get_body 79,601、list_objects_v2 14,173、put_object 66,874 |
| 失敗内訳 | AttributeError 55件、同一営業日HTTP 404再試行抑止52件 |
| 高水準呼び出し内訳 | 年次財務4系統各55、ticker_get_info 51、daily 5d+actions 83、year-end 8y 39、daily 1y+actions 40、actions max 16、actions 1y 5 |

この手動runはrun #159と同じ対象セッション日を再処理している。公開自体は完了したが、ticker失敗によりjob結論はfailureとなったため、月間の通常schedule計測には数えない。85分58秒を23営業日で単純換算すると1,977.2 runner minutesだが、独立した通常営業日複数回の結果ではない。360分のtimeoutを維持し、月2,000分の判定を保留する。

## run #161後の2060.T読み戻しとsplit/dividend監査

本番security APIからcurrent.json由来のTicker.calendarを確認した。日付2027-03-30、状態available、取得元Yahoo Finance via yfinance Ticker.calendar、fetched_atは2026-10-17T14:31:41+09:00。run #161のyfinance呼び出し内にticker_calendarはなく、再取得されていない。general last_attempt_atは2026-10-20T06:50:51+09:00。画面用dividend-months APIは同じ将来日をhistory eventからestimated_from_dividend_historyとして返し、Ticker.get_info fallbackとは別経路。

`Ticker.calendar` は前回取得から30日経過した銘柄だけ更新する。2060.Tの前回取得日2026-10-17からみて通常同期での次回確認は最短2026-11-16以降となるため、それ以前のrunで `ticker_calendar` 呼び出しがないことは取得失敗を意味しない。

同じ本番APIの2060.T event_year=2026応答は、3月30日¥24.5と9月29日¥26を各10行返した。この要求は2026年partitionだけを読み込むため、重複はそのpartition内の返却行に存在する。current.jsonのtrailing_12_month_per_share=505は重複行合計と一致する。追跡済みの2026-10-09時点の `data/market/yfinance/ex_dividend_history_20261009.csv` には各日1行ずつ同額がある。同期コードは日次候補に変化がないと保存済みイベントを再利用し、partitionを書き直さないため、通常の無変更同期では重複が残る。最初に重複を保存した実行は未特定。yfinanceから該当partitionを再構築し、本番R2/APIで再確認する。

ローカルcollectorには重複イベント日検出を追加し、検出した銘柄だけ全期間履歴をyfinanceから再取得する修正を入れた。固定データのcollector test 65件とPython構文確認は通過。コードは未公開のため、この修復によるR2の書換えやWorker APIの正常化はまだ確認していない。

read-only Actions run #162（[実行](https://github.com/masacgt/Diview/actions/runs/39331846984)、[監査artifact](https://github.com/masacgt/Diview/actions/runs/39331846984/artifacts/12227883474)）は2026-10-20のactive universe 4,435銘柄から分割対象1,068銘柄・1,270イベントを監査した。7,345件のissuer fiscal-year比較中、6,206件一致、1,139件不一致、160銘柄はissuer DPS unavailable、技術的失敗0件。スクリプトのR2操作はget_objectのみ。

## 先行する手動全銘柄同期との比較

| 項目 | run #158 |
|---|---:|
| Actions | [run #158 / 39258489241](https://github.com/masacgt/Diview/actions/runs/39258489241) |
| 実行種別・対象日 | 手動 `sync`、2026-10-19 |
| J-Quants銘柄数 | 4,444 |
| 成功 / 失敗 | 4,331 / 113 |
| yfinance高水準呼び出し | 6,186 |
| active job時間 | 同期job 4,395秒 + calendar job 22秒 = 4,417秒（73分37秒） |
| workflow wall time | `run_duration_ms=4,508,000`（75分08秒） |
| `billable.UBUNTU.total_ms` | 0 |
| 23営業日換算 | active job時間が毎回同じと仮定した場合1,693.2分。ただし手動の単一runなので予算達成の判定には使わない |

run #158はrun #159と同じ対象セッション日を先に公開している。#158の所要時間と呼び出し数は新規セッションの手動同期実測として記録するが、通常scheduleの複数回測定や月間実績とは分ける。GitHub timing APIの `billable.UBUNTU.total_ms=0` は課金量であり、active job時間の代替値として扱わない。

## 2026-10-20 5桁種類株式の取得・Worker API読み出し確認

`yfinance==1.7.0` で7銘柄を個別に `Ticker.history(period="max", actions=True, auto_adjust=False, repair=True)` 取得した。全銘柄で価格行を得て、分割行はなかった。観測した配当イベントは次のとおり（いずれもyfinance返却値であり、発行体公表値との完全照合を示さない）。

| コード | yfinance配当履歴 |
|---|---|
| 25935.T | 2010–2013は概ね¥24、2014–2022は各半期¥25、2023-04 ¥25・2023-10 ¥27、2024 ¥27/¥28、2025 ¥28/¥30、2026-04 ¥30 |
| 50765.T | 2024-09 ¥21.72、2025-03以降2026-09まで各半期¥65 |
| 75505.T | 2026-03 ¥99.72、2026-09 ¥100.27 |
| 92015.T | 2026-10-20取得時点でイベント行なし |
| 92025.T | 2026-03 ¥52.73、2026-09 ¥87.50 |
| 94345.T | 2024-03 ¥41.53、2024-09以降2026-09まで各半期¥50 |
| 94346.T | 2025-03 ¥126.24、2025-09以降2026-09まで各半期¥128 |

Workerの本番 `security-search` はカタログ世代 `2026-10-19` で7コードすべての検索候補を返した。しかし `dividend-months` の `event_year=2025` と `2026` は7コードとも `events: []` を返し、`/api/v1/securities/25935` はHTTP 400だった。空イベントの一部で `history_status: available` が返るが、Workerの状態決定は将来権利日推定から `available` に昇格させる経路があるため、R2イベント行を読み出した証拠ではない。

コード調査では、Worker v2の `yfinanceV2Symbol` が旧IRBANK用 `irbankR2Code` を呼び、4桁コードか末尾0の4桁正規化コードしか受け付けていなかった。種類株式の正確な5桁コードを `25935.T` 等のR2キーに解決できない。専用 `yfinanceV2Code` を追加してv2ルートを切り替え、詳細・財務・配当・分割・月次価格・指定銘柄価格の経路を対象としたWorkerテストは24/24成功した。現時点では変更ブランチ内の確認であり、本番Workerは未更新。手動全銘柄sync #170も2026-10-20 01:23 UTC時点ではpendingで、先行するmigration #167の終了待ちだった。したがってR2にイベントが保存済みか、修正後の本番APIで読めるかは未確認として残す。
