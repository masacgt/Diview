# 2026-10-19 通常営業日同期の計測票

## 目的

設定cronを21:17 JSTとする通常同期の実測値を記録し、GitHub Actionsの月間2,000 runner minutes以内に収まる見込みかを確認する。実起動は遅延する場合があるため、cron時刻とrun開始時刻を分けて記録する。手動同期や同一営業日の再実行は通常同期の測定に含めない。

## 実行情報

| 項目 | 記録 |
|---|---|
| Actions run URL / ID | [run #159 / 39304823002](https://github.com/masacgt/Diview/actions/runs/39304823002) |
| workflow / job | `yfinance-r2.yml` / `market-calendar` + `v2-daily-sync` |
| commit SHA | `f172861b4cd060ebde1bc0b28322e9af270f4861` |
| 起動種別 | scheduledであることを確認 |
| 設定cron / 実起動 | 21:17 JST / 2026-10-20 03:42:51 JST（約6時間26分遅延） |
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

run #159は設定cronの21:17 JSTから約6時間26分後の2026-10-20 03:42:51 JSTに通常scheduleで起動した。カレンダーゲートを通過し、全4,442コードを走査して公開した。ただし同じセッションを先行する手動run #158が処理済みだったため、複数の独立した通常営業日実測が揃うまで月2,000分の判定は保留する。HTTP 429は0件。2060.Tの `Ticker.calendar` は保存元・状態・日付を確認できたが、最終取得日時は2026-10-17のままで、run #159による再取得ではない。

手動run #177は修正前コードでセッション `2026-10-20` を公開した。#177の対象銘柄はそのセッションで初回試行として保存され、試行管理上、同日内の次の通常実行では失敗銘柄を再試行できる。これは修正確認用のフォローアップとして扱い、月間負荷の独立標本からは除外する。独立した負荷標本は、全銘柄を先行処理していない別の営業日セッションから採る。

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

run #166より前の修復前読み戻しでは、2060.Tのevent_year=2026が3月30日¥24.5と9月29日¥26を各10行返し、`trailing_12_month_per_share=505` だった。2026-10-09時点のCSVは両日1行ずつで、元の重複書込み実行は未特定だった。修復後の値を下記の2026-10-20読み戻しに記録する。

重複イベント日を検出して該当銘柄だけ履歴を再構築する修正はPR #22でmain (`1d7ce0a`) に入り、固定データcollector 65件とPython構文確認を通過した。run #166でR2の該当履歴が再構築され、Worker API読戻しで修復を確認した。

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

## 2026-10-20 五桁種類株式のyfinance履歴とWorker読み戻し

| コード | Production Workerの2026年イベント |
|---|---|
| 25935.T | 2026-04-28 ¥30 |
| 50765.T | 2026-03-30、2026-09-29 各¥65 |
| 75505.T | 2026-03-30 ¥99.72、2026-09-29 ¥100.27 |
| 92015.T | `empty`、イベント行なし |
| 92025.T | 2026-03-30 ¥52.73、2026-09-29 ¥87.50 |
| 94345.T | 2026-03-30、2026-09-29 各¥50 |
| 94346.T | 2026-03-30、2026-09-29 各¥128 |

本番 `/api/v1/dividend-months?symbols=...&event_year=2026` は上記の6コードについて exact-code のイベントと `available` を返し、92015.Tは `empty` を返した。親普通株の値は代用していない。Worker側の5桁コード解決はPR #24で反映済みであり、2026-10-20の読み戻しでR2イベントを確認した。

## 2026-10-20 本番R2読み戻し：12月株価と配当イベント修復

本番 `/api/v1/monthly-price-history?codes=7203,2060,1491&months=2018-12,...,2025-12` は7203と2060の12月価格を8期ずつ返した。両コードの価格日は2018-12-28、2019-12-30、2020-12-30、2021-12-30、2022-12-30、2023-12-29、2024-12-30、2025-12-30で、XTKS最終営業日と一致した。1491は保存履歴なしで0件だった。run #51で20銘柄の142点すべてが最終営業日と一致し、修復後のrun #160は28,999パーティションを走査して更新0、正規化0、yfinance要求0、失敗0だった。公開R2を読むWorker APIでも修復済み日付を再確認した。

手動Actions run #166後の本番APIでは、2060.Tの3月¥24.5・9月¥26、8303.Tの3月¥42・9月¥10が各1件になった。TTMは2060が重複時の¥505から¥50.5へ、8303が¥468から¥52へ修復された。2060の保存済み `current.json` の `Ticker.calendar` は2027-03-30 / `available` / `Yahoo Finance via yfinance Ticker.calendar`、`fetched_at=2026-10-17T14:31:41+09:00` のまま。run #166は `ticker_calendar` を呼んでいない。画面APIの2027-03-30は履歴由来の `estimated_from_dividend_history` で、保存calendarの再取得や `Ticker.get_info` フォールバックではない。

## 2026-10-20 手動全銘柄同期 run #166（通常予算標本外）

| 項目 | 実測 |
|---|---:|
| Actions | [run #166 / 39342594082](https://github.com/masacgt/Diview/actions/runs/39342594082) |
| 起動種別 / 対象セッション | `workflow_dispatch` / 2026-10-19。手動再処理のため通常schedule計測には含めない |
| J-Quantsコード数 | 4,442 |
| 成功 / 失敗 / 公開カタログ | 4,334 / 108 / 4,356 |
| Actions job時間 | 同期job 1時間10分40秒 + calendar job 28秒 = 1時間11分08秒 |
| 同期スクリプト時間 | 4,201.176秒 |
| yfinance高水準呼び出し / HTTP 429 | 3,715 / 0 |
| yfinance呼び出し内訳 | `history_actions_max` 3,309、daily 5d+actions 56、daily 1y+actions 40、year-end 8y 39、`ticker_get_info` 51、年次income/balance/cashflow/valuation各55 |
| 収集 / カタログ生成 / 公開 | 4,102.967秒 / 82.593秒 / 7.996秒 |
| R2 active wall / R2-only / yfinance重複 | 4,102.957秒 / 2,796.904秒 / 1,306.053秒 |
| R2操作数 | get_object 84,800、get_body 79,835、list_objects_v2 14,254、put_object 67,159、delete_object 7 |
| 失敗内訳 | `AttributeError: 'str' object has no attribute 'get'` 55件、同一セッションHTTP 404再試行抑止52件、1319.Tのイベント欠落安全停止1件 |
| job結論 | failure。ただし全4,442コードを処理し、成功銘柄の新データと失敗銘柄の前回値を含むgeneration `2026-10-19` を公開済み |

run #166はrun #159と同じ対象セッションを再処理した手動実行である。71分08秒を23営業日に掛けた値を通常月間負荷の推定に使わない。通常scheduleの複数の異なるセッションを計測するまでは2,000分以内か判定せず、Actions timeout 360分を維持する。

`AttributeError` は #158・#159・#161・#166で同じ55コードに再発した。51コードは `A` 終端、残りは2972・4616・6085・7085。発生箇所を確認するため `sync_v2.py` に異なるoriginごと最大8件のtraceback出力を追加し、同一originの重複抑止テストも加えた。再試行やデータ変換は変えていない。Actions回帰テストと、診断を本番同期へ反映した後のtraceback採取は未完了。

## 2026-10-23 手動全銘柄同期 run #233 と読み取り専用監査

| 項目 | 実測 |
|---|---|
| Actions | [run #233 / 40186701833](https://github.com/masacgt/Diview/actions/runs/40186701833) |
| 起動種別 / 対象 | 手動 `sync` / 2026-10-23。`--reprocess-successful`を含む再処理 |
| 成功 / 失敗 / 公開カタログ | 4,396 / 48 / 4,362 |
| workflow / 同期job時間 | 69分26秒 / 68分58秒 |
| job結論 | failure。generation `2026-10-23-bd000f1395fc483ebc02971bf830ba23`を公開後、48件の部分失敗で終了 |

48件はそれぞれ異なる銘柄で、同期ログ上は同一セッション内のYahoo HTTP 404を`no_data`として再試行抑止した。JPX情報との照合では、3450・4171・6197は上場廃止、5617はTOKYO PRO Market上場中、ほか44銘柄は2026年9月末の東証上場銘柄一覧に掲載されている。上場情報だけでは残る45銘柄の404原因を説明できず、再試行や保持方針は変更していない。

同generationを本番Worker APIで読み戻し、2026-10-23・4,362件を確認した。続く[読み取り専用監査run #40194212582](https://github.com/masacgt/Diview/actions/runs/40194212582)は対象16銘柄のV2現行・アーカイブを読み、131現行オブジェクト（206イベント）と75アーカイブオブジェクト（91イベント）を確認した一方、16銘柄すべてで旧形式の基準オブジェクトを確認できなかった。したがって以前の4つの日付差は未解決であり、旧データとの完全照合を示す結果ではない。
## 2026-10-25 参考Actions実行（通常同期の標本外）

| Run | 対象 / SHA | 結果 | 実行時間 | 月間計測での扱い |
|---|---|---|---:|---|
| [#233](https://github.com/masacgt/Diview/actions/runs/40186701833) | 手動 `sync`、`main` / `5d787342` | 4,396成功、48失敗、カタログ4,362件を公開後に失敗終了 | workflow 69分26秒、同期job 68分58秒 | `--reprocess-successful` を含む手動再処理で、最適化ブランチSHA `2bfdcf54` と異なるため効果比較には不採用。Actions使用時間には含む |
| [#239](https://github.com/masacgt/Diview/actions/runs/40191902472) | `workflow_dispatch`, `run_mode=test`、最適化ブランチ / `001de56` | 112件中8件失敗 | workflow 約38秒、テストjob 約31秒 | 同期・ベンチマークの標本外。テスト成功まで次のベンチマークを保留 |

Run #239の失敗は、同一出所の比率状態補完1件、DPS不明時の利回り再利用1件、IRBANK seedの`field_units`期待値4件、使用中統計値のアーカイブ期待値1件、`CURRENT_FIELDS`期待値1件。コード側の利回り再利用を修正し、現行の状態・単位メタデータに合うテスト期待値を更新してから再実行する。

## J-Quantsユニバース差分と今後のスナップショット記録

2026-10-20に取得可能なJ-Quants v2 /equities/master の取得日別応答を比較した。Date=2026-07-27 は4,444コード、Date=2026-07-28 は4,442コードで、差分は191Aと3271が上場銘柄一覧から外れたものであり、追加はなかった。run #158と#159の件数差と整合する。ただし、両runのartifactに応答日付またはコード一覧がないため、それぞれがどちらの日付の応答を使ったかは未確定であり、断定しない。

この欠落を防ぐため、通常同期と全銘柄studyに jquants-universe-snapshot.json の30日artifact保存を追加した。artifactには全ページから返ったDate、正規化後の件数、辞書順コード一覧、コード一覧のSHA-256を記録する。以後の同期ではartifactのコード数とログの処理数を同run内で照合する。
