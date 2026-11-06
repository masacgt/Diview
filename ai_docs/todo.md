# Todo

- [x] 2026-11-05 JST（2026-11-04 UTC）にKV Read反映後の定義dry-runと回帰検証を実施した。根拠: `definitions` dry-runはKV定義6件を読み、R2オブジェクト12件を計画し、D1クエリ/行読取0・R2書込0（レポートSHA-256 `f9202d7c355650bb7afb2a2da34f91ccbec73bc58541984ade3a91c873000008`）。19:51 UTCのAccount Analyticsは431,362/5,000,000行、残り4,568,638行。Worker 120/120、移行/使用量/Workflow Python 82/82、JDK 17 Androidユニットテスト274/274が成功し、Debug APKとAndroidTest APKもビルド成功。`node --check` と `git diff --check` 成功。
- [ ] 本番切替前の運用ゲートを解消する。根拠: 2026-11-04 17:38 UTCの本番読み取りではWorkerにD1 bindingが残りRate Limiter DO bindingがなく、定義API 3経路が503。定義R2オブジェクト12件は未作成。GitHub mainは保護されず、`d1-r2-migration` Environmentも未設定。Queue Read tokenがなく未処理キュー確認も未完了。R2書込・Actions実行・Worker deployは未実施。

- [x] 2026-11-04にD1→R2移行CLIのテーブル指定をフェーズごとに検証するよう修正した。根拠: テーブル不要の4フェーズの誤指定と3種類の有効な組合せを回帰テストし、移行ツール51/51件、Worker 116/116件が成功した。
- [x] 2026-11-04に手動移行WorkflowのGITHUB_TOKEN権限を最小化した。根拠: 公式GitHub資料でEnvironment/branch-policy GETに`actions: read`が必要と確認し、Workflow内にDeployments API呼出しがないことを監査して不要な`deployments: read`を削除。Workflowは未実行。
- [x] 2026-11-04 07:03 UTCにCloudflare D1 Metricsを読み取り確認した。根拠: `diview-irbank`個別DBのlast-24h表示は7M（チャート6.93M）、銘柄検索SQLは1,886回・6.96M行。UTC 00:00–07:03の指定範囲では同DBのチャートは138.55k行。D1 SQL・書込みなし。
- [x] 2026-11-04 07:56 UTCにWorkerとPython検証を再実行した。根拠: Worker 116/116件、D1→R2移行CLIとD1使用量ヘルパーの合計59/59件が成功。
- [x] 2026-11-04 07:54 UTCにGitHub Actionsの公開状態を読み取り確認した。根拠: mainには既存Workflowが9件あり、新Worker CI・手動移行Workflowは未公開。Environment一覧は`IRBANK_API_KEY`のみで`d1-r2-migration`はない。既存AndroidチェックはAndroidパスを対象としJDK 17を指定する。Actions実行・設定変更なし。
- [x] 2026-11-04 08:10 UTCにR2実体とインカム空白月候補APIを照合した。根拠: generation `2026-11-02-daa434e899b0493a9fdf346067419041`で一致し、3,203件の構造差0。R2実体SHA-256も記録値と一致。カタログ4,390件、候補の重複・カタログ外・source generation不一致0。D1なしのローカルWorkerとremote R2のみ使用。
- [x] 2026-11-04 08:12 UTCに端末の日次価格取得APIを読み取り確認した。根拠: `/api/v1/live-quotes?codes=7203` はHTTP 200、同generation、`complete=true`、1銘柄の価格あり・quote date `2026-11-02`。D1なしのローカルWorker＋remote R2 GET。
- [x] 2026-11-04 08:24 UTCに移行WorkflowのD1日次残枠ゲートを追加・検証した。根拠: UTCアカウント使用量確認がD1 SQLより前に実行され、`max_rows`と追加reserve（既定100,000）の残枠を要求する。Analytics Read資格情報をD1読取・R2書込資格情報から分離。Worker 116/116、Python 62/62、Workflow YAML構文と順序検査が成功。Workflowは未公開・未実行。
- [x] 2026-11-04 08:29 UTCに予算ゲート追加後の検証を再実行した。根拠: 移行・使用量Python 63/63件（WorkflowがD1/R2各フェーズより前に残量を確認するテストを含む）、Worker 116/116件が成功。テストはローカルfixtureを使い、本番へのアクセス・書き込みは行っていない。
- [x] 2026-11-04 08:43 UTCにGitHub mainとActionsを読み取り確認した。根拠: main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、Workflow 11件。yfinance・IRBANK・権利日workflowは残り、新Worker CIと手動移行workflowは404。mainのbranch APIは`protected=false`を返した。Environment一覧はこのコネクターで取得できず、設定は未確認。書込み・workflow実行なし。
- [x] 2026-11-04 08:30 UTCにAccount Analytics tokenの有無を再確認した。根拠: 実行環境にtokenがなく、GraphQL照会と本番D1行SQLは実行していない。
- [x] アカウント全体のUTC日次Rows Read合計・残枠を確認した。根拠: 2026-11-04 19:51:45 UTCのAccount Analyticsはusage group 3件を返し、431,362/5,000,000行、残り4,568,638行で100,000行reserveを確保。D1 SQLなし。
- [x] 2026-11-04 08:40 UTCにAndroidの価格通知キャッシュ仕様を `as-is-specification.md` と `architecture.md` に反映した。根拠: 端末価格キャッシュ、対象コード50件単位、generation pinning、3東証営業日の通知鮮度、30秒後の失敗再試行を現行ローカル実装に合わせて記載した。
- [x] 2026-11-04にJDK 17でAndroid変更を再検証した。根拠: JVM 267/267件、価格更新・通知instrumentation 35/35件が成功し、emulator-5556の起動とAPK/`base.apk` SHA-256一致を確認した。
- [x] 2026-11-04にR2-backed APIの安全な読み戻しを行った。根拠: local Worker + remote R2 + local DO + D1なしで履歴・定義監査・財務の4 GETが成功し、分析GETがR2監査を書き込むことをコードで確認して対象外にした。
- [ ] 分析APIの本番R2本文照合は未完了。既存のFakeR2テストはD1未読取と現行R2入力による計算を確認するが、デプロイ済みWorkerとの一致は証明しない。remote R2 GETは定義監査オブジェクトを書くため、追加fixtureまたは承認済みの検証方法が必要。

- [x] 2026-11-03 21:28 UTCに移行チェックポイントから銘柄コードカーソルと定期実行R2キーが残る問題を修正した。根拠: `analysis-current` はrowidカーソルに変更し旧形式からは先頭再開、定義フェーズは数値indexのみ、定期実行はR2キーを再構築する。レポート/チェックポイントの回帰テスト37/37件成功。本番D1行読み取り・R2書き込みは未実施。
- [x] 2026-11-03 21:12 UTCに `securities.metrics_json` のR2対応を監査へ追加した。根拠: 非空値をIRBANK v1 `overview.json` の `security.metrics` と比較し、manifestと各概要のSHA-256を確認する。レポート/チェックポイントには集計件数のみを記録し、回帰テスト35/35件が成功。ローカルのsecurities import 3,963行中、非空3行は現行R2の対応値と一致した。これは本番D1全行の監査結果ではなく、リセット後の小予算監査は未実施。本番D1行読み取り・R2書き込みなし。

- [x] 2026-11-03 20:52 UTCにローカル実装の検証を完了した。根拠: Worker 114/114件、移行ツール33/33件、Android JDK 17単体テスト266/266件、通知instrumentation 13/13件と価格再試行・同日ゲート各1/1件が成功。エミュレータAPKのSHA-256一致を確認した。
- [x] 2026-11-03 20:52 UTCのGitHubリモート状態を読み取り確認した。根拠: main上の追加Workflowは2件とも404、Environment一覧は `IRBANK_API_KEY` のみ。secret値の閲覧・設定変更やWorkflow実行はしていない。
- [x] 2026-11-03 20:47 UTCにD1使用量メタデータを再確認した。根拠: `rows_read_24h=6,912,701` で5,000,000行を超過。リセット前のため行SELECTは行わず、リセット後の使用量・読み取り可否確認と小予算監査を未完了項目として維持する。

- [ ] 2026-11-03 22:47 UTCのD1確認と続くR2読み戻し。根拠: wrangler d1 info diview-irbank --json は rows_read_24h=6,904,136、read queries 2,809、write queries 5,679、rows written 17,077、23 tablesを報告し、5,000,000行超過。2026-11-04 00:00 UTCのリセット前なので本番行SELECTなし。R2 catalog/candidatesはgeneration 2026-11-02-daa434e899b0493a9fdf346067419041、4,390/3,203件、重複0、候補世代欠落・不一致0。Worker 114/114、移行ツール37/37成功。リセット後の使用量確認、小予算D1監査、GitHub Environment保護の再確認は未完了。

- [x] 2026-11-03 20:35 UTCの価格取得失敗後の起動時再試行を修正した。根拠: `DividendViewModel.kt` は同日価格更新成功を保ちながら、失敗状態かつ30秒経過後の次回アプリ起動で再試行する。新規リトライ計装テストと既存の同日抑止テストがJDK 17.0.20.1・`emulator-5556` で各1/1件成功し、APK/`base.apk` SHA-256も一致した。

- [x] 2026-11-03 20:38 UTCにGitHub Environmentの現状を読み取り確認した。根拠: `masacgt/Diview/settings/environments` に `IRBANK_API_KEY` だけが表示され、`d1-r2-migration` は存在しない。シークレット値の閲覧・設定変更はしていない。
- [ ] 移行Workflowの利用前に、管理者が `d1-r2-migration` Environmentをmain限定・独立reviewer必須・自己承認不可で作成し、承認済みの資格情報を設定して保護内容を確認する。現状ではWorkflowを実行しない。

- [ ] 2026-11-03 20:22 UTCのR2/API・レート制限確認: R2カタログ/空白月候補はgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`、4,390/3,203件で重複0。候補のsource generationも一致。Worker APIは200/500が混在し、`wrangler tail`で本番Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` の500がD1行読み取り上限による `enforceRateLimit` 失敗と判明した。これはR2欠損の証拠ではない。200時の件数・世代は一致したが、以前のフィールド比較では差が報告され、完全一致は未確認。ローカルでは同じ公開バケット `public:income-advice-candidates`、300回/60秒のDO制限をR2取得前に適用し、拒否時は429を返すよう修正。Workflowと同じWorkerテスト114/114件、移行ツール33/33件、`node --check` と `git diff --check` が成功。本番D1行読取、R2書込、Workflow実行、デプロイなし。

- [ ] 2026-11-03 20:27 UTCのGitHub Actionsリモート確認: `origin/main` は実装worktreeのHEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b` と一致。既存Workflowの取得は成功し、新Worker CIとD1→R2手動Workflowの2パスはmainで404となった。新Workflowはまだローカル未追跡で、GitHub Actions実行・移行Environmentの実設定は未確認。CIと同じコマンドはローカルでWorker 114/114件、移行ツール33/33件。mainへの公開やWorkflow起動はしていない。

- [ ] 2026-11-03 20:11:59 UTCの読み取り専用メタデータ確認: `wrangler d1 info diview-irbank --json` は `rows_read_24h=6,912,681`、`read_queries_24h=2,946`、`write_queries_24h=5,925`、`rows_written_24h=17,823`、DBサイズ100,163,584 bytes、23テーブルを報告。5M上限超過で、2026-11-04 00:00 UTCのリセット前。本番行SELECTは実行していない。

- [ ] 2026-11-03 20:11 UTCのテーブル棚卸し: リポジトリのD1基底schemaと全migrationから22個のアプリテーブルを抽出し、移行runbookのマッピングに22/22件あることを確認。D1 infoが示した23件目は `d1_migrations` の可能性があるが、本番 `sqlite_schema` の確認前に一致とは扱わない。runbookに条件付きの保持対象として記載した。本番行SELECTは未実施。

- [ ] 2026-11-03 20:09 UTCの修正後検証: 移行ツール33/33件、Worker 113/113件、`node --check src/index.js`、`git diff --check`、Actions YAML構造検査が成功。監査レポートは集計フィールド件数のみ、チェックポイントはR2キーのSHA-256フィンガープリントを使い、従来の生キーを含むチェックポイントから再開してもR2変更検出を維持する。エラー文からも銘柄コード・R2キーを除いた。本番D1行読取、R2書込、Actions実行、デプロイはなし。

- [ ] 2026-11-03 20:03 UTCの監査出力レビューで、`market-source-audit` の旧実装が銘柄コード・期間・R2キーを最大20件レポート/チェックポイントへ含めることを発見。行サンプルを削除し、欠損/差分フィールド名の集計件数に置換。旧チェックポイントのサンプルも再開時に除去するテストを追加。Worker 113/113件、移行ツール32/32件、`node --check src/index.js`、`git diff --check`、Actions YAML構造検査が成功。UTC 00:00リセット前のため本番D1行読取なし。

- [ ] 2026-11-03 19:59 UTCのActions事前確認修正: GitHub REST docsを照合し、`GITHUB_TOKEN`の権限ではEnvironment secrets一覧APIを呼べないため、preflightから同APIを削除。保護されたmigration jobのEnvironment承認後、4つの専用secret値が空でないことを値を出さずに検査する。両WorkflowをローカルYAMLパーサーで解析し、移行workflowがworkflow_dispatchのみ、migration jobがpreflight依存・専用Environment付きであることを確認。GitHub Actions自体は未実行。D1使用量は19:53 UTCの6,916,966行/24hが直近値で、リセット前のため行データ照会なし。

- [ ] 2026-11-03 19:53 UTCの継続確認: `wrangler d1 info diview-irbank --json` は `rows_read_24h=6,916,966`、`read_queries_24h=3,017`、`write_queries_24h=6,053`、`rows_written_24h=18,207` を返し、5,000,000行上限を超過。リセット時刻2026-11-04 00:00 UTCより前のため、本番D1行SELECTは行っていない。移行レポート/チェックポイントから行サンプルを除き、古いチェックポイントのサンプルも再開時に破棄する回帰テストを追加。Worker 113/113件、移行ツール31/31件、`node --check src/index.js` と `git diff --check` が成功。GitHub mainのworkflow一覧に新CI/移行workflowはまだなく、Actions実行・本番R2書込・Workerデプロイ・実機変更はなし。

- [ ] 2026-11-03 19:49 UTCの移行継続確認: Worker 113/113件、移行ツール30/30件、`node --check src/index.js`、`git diff --check` が成功。ヘルスのlegacy/current R2実行選択とD1呼出し0件、dry-run完了チェックポイントのwrite-mode先頭再開、定期実行ポインターの開始時刻優先、未完了時の非公開、新しいR2実行からの後退防止を回帰テストで確認。Androidの最新差分はJDK 17単体テスト266件、ビルド、emulator-5556 APK hash照合、通知instrumentation 15/15件が成功。UTC 00:00リセット前のため本番D1行読取なし。Actionsは未公開で、Environment未設定。本番D1監査、R2差分補完・移行、Actions実行、本番切替は未完了。

- [ ] 2026-11-03 19:24 UTCの再開確認: `d1 info` は19:22 UTCに6,916,954行/24hを報告し、UTC 00:00の通知リセット前だった。19:18ごろの0表示と矛盾し、10行限定SELECTは `rows_read=10` で成功したものの、利用可能枠の根拠にはならないため追加の本番D1行読取は停止中。R2/APIは市場カタログ4,368件と空白月候補3,201件で同一generation・一致、しかし分析/履歴/状態・run・定義・監査の移行先prefixは0件。Worker 111/111、移行27/27、JDK 17 Androidテストとビルド、emulator-5556へのAPK hash照合、通知instrumentation 13/13は成功。新Workflowはremote mainに未公開、GitHub EnvironmentはIRBANK_API_KEYのみ。全表監査・不足データの正規R2補完・Actions実行・本番切替は未完了。
- [ ] 銘柄検索の株価・利回りの24時間キャッシュについて実機の検索・再表示時の挙動を確認する。銘柄検索結果の24時間保持とは別に市場値を保持する。単体テストで期限・未取得の再試行・無配と未取得を確認し、エミュレータテストでキャッシュ済み株価と利回りの先行表示を確認。

- [ ] 配当月とイベント履歴の補完について、異なる銘柄や取得失敗時の実機表示を追加確認する。8306は実機の資産管理と銘柄情報の両方で「3月・9月」を確認。月だけの場合はイベント日時や金額を生成せず、履歴からの月は直近の記録を基準に補完する。単体テストと隔離エミュレータテストで空の月一覧・履歴のみ・月のみ・古い年の上書き防止を確認。

- [ ] 銘柄検索結果から詳細を開いた後、戻ると検索内容を保ってモーダルが再表示される動作を実機で確認する。エミュレータでは7203を検索・詳細表示・戻る操作で検索語と候補が復元され、Composeテストで明示的な閉じ操作も確認。

- [ ] 銘柄検索中のキャッシュ候補の先行表示を実機で確認する。エミュレータのComposeテストで、通信待機中の候補表示と完了後の入れ替え、失敗・検索語変更の回帰を確認。初回通信はAPIの一括応答を待つ。

- [ ] 銘柄検索結果の24時間キャッシュについて、実機での表示と価格更新を確認する。JDK 17の単体テストで成功・空結果の再利用、失敗後の再試行、期限・上限を確認。エミュレータでは7203の検索とモーダル再表示後の結果・価格を確認。

- [ ] Verify searched-stock dividend months after retrieval and cache reload on a physical device; an emulator displayed 7203 as 3月・9月 from the prior task checkout.
- [ ] Verify dividend chart loading skeleton and failure state on a physical device; an emulator test in the prior task checkout confirmed the loading-to-data transition.

- [x] Switch the daily yfinance universe from J-Quants to a weekly complete IRBANK + JPX ETF snapshot. Evidence: source audit found 4,573 IRBANK codes and 417 current JPX ETFs; schema-v2 union produced 4,576 codes after adding 650A, 651A and 659A. PR #45 merged as `ced28fc5`; PR #61's live-source audit merged as `18fed178`. Earlier CI evidence records 160 passing sync tests; the updated 2026-11-03 worktree ran 170 passing tests, including the structured metrics artifact test. IRBANK update run #13 and its snapshot artifact exactly match R2 `irbank_source.json` (4,576 rows, 4,576 unique codes, code hash `c6337a4b…72b650d`, raw SHA-256 `f3030429…18e74e77`). Run #314 was the first manual publication from this source; its then-current generation had 4,358 catalog entries. Later run #367 published generation `2026-11-02-15aa0f2576d649b2aa0da838f9c68544`, and current Worker read-back returned 4,368 catalog entries. The 2026-11-02 session returned 2060.T at ¥1,364, 650A at ¥847 and 659A at ¥2,027. Run #367 published after processing 4,420 successful records (732 partial), 156 failed records, 374 yfinance calls and zero HTTP 429; it ended failed after publication.
- [x] Produce a read-only, same-day retrieval comparison of the IRBANK and J-Quants universes, including code normalization, product categories, code hashes and each difference. Evidence: the snapshots were fetched 16 seconds apart; report `ai_docs/audits/irbank-jquants-universe-diff-20261103.json` records 4,406 common, 167 IRBANK-only and 38 J-Quants-only codes with zero duplicates. JPX dated evidence identifies 30 delisted codes and a separate 2026-09-30 TSE snapshot contains 42 difference codes (25 IRBANK-only, 17 J-Quants-only); seven J-Quants-only rows are named class shares. Membership reasons remain open where these dated facts do not explain the source difference.
- [x] Distinguish the IRBANK universe refresh result from the co-scheduled dividend batch. Evidence: run #13 refreshed and R2-read-verified the 4,576-code snapshot using 11 API requests (code hash `c6337a4b…72b650d`, raw read-back hash `f3030429…18e74e77`), while the separate dividend batch made one request, reported zero updates, 3,963 failures, and `coverage_complete=false`; workflow success confirms only the successful universe-refresh path, not dividend coverage.
- [x] Verify production Worker and search-catalog responses for a legacy equity, JPX-added ETFs, and a REIT. Evidence: run #367 published generation `2026-11-02-15aa0f2576d649b2aa0da838f9c68544`; production `/api/v1/search-catalog` returned 4,368 rows and included 650A at ¥847 and 659A at ¥2,027, both dated 2026-11-02, alongside 651A and REIT 8951. Both new ETF detail responses identify `Yahoo Finance via yfinance`; `Ticker.calendar` is separately recorded as `status=failed`, `date=null`, `fetched_at=null`, so their available price/search rows do not imply calendar data was obtained. 2060.T reports calendar 2027-03-30, source `Yahoo Finance via yfinance Ticker.calendar`, fetched 2026-10-17; this differs from its market quote/statistics dates. The current production read-back is summarized in `ai_docs/audits/production-etf-and-metrics-followup-20261103.json`.
- [x] Recheck JPX-added ETFs 650A and 659A in the next available published generation after the earlier run #314 omission. Evidence: manual run #367 wrote the 2026-11-02 prices into the R2-backed generation above and both codes are now in the search catalog. The quote date is the same XTKS session as run #314's no-data observation, demonstrating a successful later fetch/reprocess rather than a later market session; calendar fields remain failed.
- [x] Cross-check universe differences against the JPX delisted-stocks table. Evidence: 28 J-Quants-only codes were delisted after its 2026-08-10 effective date and by the IRBANK 2026-11-02 retrieval; two IRBANK-only codes (6565 and 5386) were already delisted before both snapshots but remained in the IRBANK response. Per-code dates and limitations are in `ai_docs/audits/irbank-jquants-universe-diff-20261103.json`.
- [ ] Classify the remaining 146 membership differences using date-aligned official listing/delisting records and product-scope evidence; the 2026-09-30 TSE snapshot proves presence only on that date, while absence does not prove non-listing on another exchange. As of 2026-11-03 the JPX monthly table still exposes September month-end and says the prior-month file is posted after the third business day; October's dated snapshot is expected no earlier than 2026-11-05. JPX also points to its Data Portal full-list CSV; anonymous file download remains unverified.
- [x] Probe Yahoo price-history availability for the seven JPX-listed class-share codes absent from the IRBANK snapshot. Evidence: one read-only yfinance 1.7.0 batch returned some 5-day history for all seven, but only 75505.T and 92025.T returned a 2026-11-02 close; the other five returned 2026-10-30. Results and the no-R2-write limitation are recorded in the universe-difference audit JSON.
- [x] Resolve the class-share cohort against the selected universe boundary. The approved daily source is IRBANK plus missing JPX ETFs and REIT classification; J-Quants-only five-digit class-share codes are not added because that would expand the approved source scope. The read-only Yahoo probe returned a 2026-11-02 close for 2/7 only and did not write R2. Any broader inclusion requires an explicit source-scope change.
- [x] Inventory the scheduled-workflow overhead that must be included in the monthly runner budget. Evidence: Actions runs from 2026-10-26 through 2026-11-02 show yfinance sync on XTKS sessions, IRBANK refresh/dividend daily, and rights-date calculation weekly; the rights job took 17m51s on run #4 and 24m01s on run #5, while scheduled IRBANK jobs #9-#12 took 5m08s, 6m04s, 6m15s, and 2m22s.
- [ ] Verify several independent normal scheduled sessions with the IRBANK+JPX universe, and calculate the full monthly Actions runner total. Run #314 is a manual 2026-11-02 sync (53m17s summed active jobs), not a normal-schedule sample. The IRBANK workflow is daily, including non-market days, and the rights-date job is weekly; therefore the prior 23-day estimate of 1,281.1 minutes undercounted these schedules. A rough scenario using #314, run #13's 2m28s IRBANK job, and the two rights-job measurements is about 1,391 minutes/month, but none of those post-switch daily timings is a repeated normal sample. Pre-switch normal run #290 plus prior IRBANK scheduled averages and weekly rights-date averages extrapolates to about 2,120 minutes/month, showing the budget could be exceeded. Keep timeout 360 minutes until post-switch normal runs and all scheduled workflow minutes are measured.

## 2026-11-03 follow-up checks

- [x] Check the scheduled IRBANK run after the manual full-universe publication. Evidence: scheduled run #15 completed in 2m24s; the universe snapshot was fresh at 4,576 codes and required zero IRBANK list calls. Its separate dividend batch made one API request including usage lookup, updated zero values, left 3,963 forecasts pending, and reported 3,963 failures with `coverage_complete=false`. The run artifact has no failure-class breakdown; see `ai_docs/audits/irbank-weekly-run15-jpx-access-followup-20261103.json`.
- [ ] Validate the new IRBANK dividend-batch failure diagnostics on a subsequent scheduled run before attributing run #15's 3,963 failures or changing retry policy. The collector now emits per-kind, HTTP-status-specific failure counts and up to 20 code-specific samples with truncated, URL-query-redacted messages; this change has not yet been run against the scheduled workflow.
- [x] Check whether JPX's Data Portal can supply the unclassified universe differences anonymously. Evidence: the official portal page rendered without signing in and displayed 300/4,613 rows plus a full-CSV control, but clicking the control yielded no verified file or on-page confirmation. This is inconclusive about anonymous download availability; no CSV-based reclassification was made.
- [ ] Recheck the JPX listed-issues page after 2026-11-05 for the October month-end workbook, then compare all 146 remaining membership differences against that dated snapshot. Continue to treat absence from a TSE-only workbook as insufficient proof of delisting or non-listing on another exchange.
- [ ] On the next XTKS schedule after the market holiday, confirm the regular-session calendar gate, complete IRBANK universe scan, publication generation, and the new yfinance metrics artifact. Manual runs and holiday skips do not count toward the monthly runtime assessment; keep timeout 360 minutes until several normal sessions are measured.
- [x] Reconcile run #314's 156 top-level security failures by symbol and separate them from field-level missing prices. Evidence: the full job log has 112 explicit `FAILED` HTTP 404 records plus 44 same-session retry suppressions whose stored reason is also HTTP 404; these are 156 unique codes. Separately, 270 codes logged an unavailable daily close (237 no-close-for-session, 33 `YFPricesMissingError`), with zero overlap with the 156 top-level failures. Per-code results are in `ai_docs/audits/yfinance-run314-failure-classification-20261102.json`.
- [x] Cross-check run #314 failures against JPX's public delisted-stocks table. Evidence: 2 of 156 failed codes are confirmed delisted by the 2026-11-02 market date (6565 on 2026-05-01, 5386 on 2026-03-17); the table did not contain the other 154, which remain unclassified. Per-code evidence and limitations are in `ai_docs/audits/yfinance-run314-failure-classification-20261102.json`.
- [x] Reprobe all 156 run #314 top-level HTTP 404 codes through yfinance history without R2 writes. Evidence: the 2026-11-03 read-only 1-month probe queried 156/156 at 0.5-second minimum intervals; all returned `YFPricesMissingError`/no history and HTTP 429 count was zero. Results are in `ai_docs/audits/yfinance-run314-yahoo-reprobe-20261103.json`; current no-data does not prove their historical listing status.
- [ ] Classify the remaining 154 run #314 HTTP 404 codes using date-aligned JPX/IRBANK listing evidence; do not treat absence from JPX's delisted table or current Yahoo no-data as proof of active/delisted status or permanent Yahoo coverage.
- [x] Verify that failed daily-price fields retain their last published value in production. Evidence: Worker reads for 7863, 8139, 9012, 9214 and 9222 report `processing_status=partial`, `failure_reason=Field fetch failed: price`, and retain their 2026-10-30 close after run #314 failed to fetch the 2026-11-02 close; 9555 has no API-visible record and is not counted as a retained-value example.
- [x] Implement a structured JSON sync metrics artifact for R2 operations, yfinance calls, failures, universe provenance and phase timings. Evidence: `sync_v2.py` writes an atomic local report after publication, the workflow uploads it on `always()`, and the new artifact-writer unit test passes within the 170-test suite; `git diff --check` passes.
- [ ] Verify a real scheduled or manual main sync uploads `yfinance-sync-metrics-{run_id}` and that its final R2 counts, 429 count, yfinance calls, failure rows and elapsed time agree with the workflow log and R2 generation. The implementation has only been tested locally so far.

- [x] Integrate and validate J-REIT descriptions, Yahoo fallback, zero-dividend chart and event-date forecast transitions. Evidence: JDK 17 build, 240 unit tests, 79 Worker tests, 20 IRBANK tests and 10 emulator UI cases pass; 8953, 2914 and 6721 were checked on emulator, 8214 on SCG01, and both installed APKs match SHA-256 `ff6353e8db71cb7255c698fc53c7b0305f57545c6c85434a435bb35ec9798ba7`. See `jreit-8953-investigation-20261031.md` for source and production evidence.
- [ ] Verify a real dividend-event date rollover after the scheduled provider update; date boundaries are covered by injected unit-test dates and device clocks were not changed.
- [ ] Investigate J-REIT indicator period and freshness: 8953's financial snapshot predates its latest company disclosure and Yahoo payout-ratio period is unconfirmed; the user requested no additional source/date display.
- [ ] Refresh bundled JPX fund-market lists when listings change and expand live J-REIT verification.

- [ ] Verify a subsequent scheduled IRBANK refresh after parser fix `8693b466` reached main via `80fa14fe`. Local tests and direct IRBANK Connect validation pass; the 8214-only R2 refresh was verified through the active shard, production API, emulator and SCG01, which show 2027期 forecast 90 JPY.

- [ ] Complete cross-issuer annual-data protection and repair: R2 repairs used J-Quants for 2025/03–2026/03 and IRBANK Connect for missing 2008/03–2024/03; guarded writes and read-back passed for 19 plus 36 objects. Production API comparisons pass for 34 IRBANK periods; Worker v79 and main commit `9ea3c59f` contain the safeguards. An isolated Android 8.0 AVD and Android 13 physical device show the two current ratios and matching financial revisions. Full affected-universe coverage, individual historical filings, subsequent scheduled refresh and broader Android checks remain unverified. See `cross-issuer-financial-repair-20261031.md`.

- [ ] Investigate stale Japanese quotes after scheduled sync #290: production 1301 retained the 2026-10-26 close and 2708 retained the 2026-10-28 close after price-fetch failures, while the same fetch function returned valid 2026-10-30 closes for both in a later local read-only check. Identify the runner-time failure cause, repair safely, and verify current price, quote dates, yields and portfolio values across screens before release. See `cross-issuer-financial-repair-20261031.md`.

- [ ] Verify remaining forecast acquisition and release coverage: targeted run 40600703726 published 8984 as 7,110 JPY with two dated components and preserved other values and the batch cursor; production Worker `93800f39-4dc0-443f-baf3-13a583b92946` returns consistent 8984 and 4502 search values. A scheduled batch, broader issuer forecast coverage, and final release acceptance remain unverified.

- [ ] Complete release-audit fixes and production acceptance for missing dividend/price data, forecast history and Watchlist ordering. Worker production version e6196109 treats empty issuer feeds as unknown; Android distinguishes missing valuation/dividend data. Isolated emulator and production API checks pass for representative cases; physical-device validation and full release acceptance remain open.

- [x] Integrate the verified fetch-efficiency branch into current main while preserving dirty worktrees and CSV history. Evidence: the three commits through `c65468da` fast-forward without conflict; JDK 17 build/220 unit tests, 67 Worker tests, 24 emulator cases and 25 physical cases succeed; both installed APKs match and all 66 PNGs match baseline. The concurrent Worker v70 publication was preserved while restoring this task's bounded optimizations as v71; 30 production response checks pass. See `fetch-efficiency-main-integration-20261027.md`.

- [x] Reinstall the pushed fetch-efficiency APK on SCG01 and verify modified behavior against production. Evidence: JDK 17 build and installed APK hashes match; all 24 isolated regression cases plus one opt-in live test pass, 33/33 device PNGs match baseline, and production tail proves shared default-Repository HTTP counts. Worker v69 removes unnecessary large-document copies; 67 Worker tests and 30 production comparisons pass. See `data-fetch-efficiency-device-check-20261027.md`.
- [ ] Monitor sustained production CPU-limit behavior after v69. Evidence: a v68 fresh 285A financial request returned `exceededCpu`/503 during live acceptance; v69's live device test and 27 production read-backs all succeeded after narrowing the copy scope, but intermittent CPU-limit behavior outside those samples is not ruled out.

- [x] Implement bounded fetch-efficiency improvements while preserving display/notification contracts. Evidence: Worker 66/66, Android JDK 17 unit tests 220/220, each device 24 instrumentation tests, and 66/66 before/after PNGs pixel-identical; source and installed APK hashes match. See `data-fetch-efficiency-implementation-20261027.md`.
- [x] Publish the tested production-only Worker delta after explicit approval, then compare 27 production API responses and confirm the opt-out path. Evidence: initially v68, followed by v69 for the CPU-copy follow-up; main integration preserves the intervening v70 changes and restores optimizations as v71 `234d8184-ecb2-4720-a08b-0284de248270` at 100%. All 27 responses match except observation time/elapsed age, and three history-opt-out comparisons preserve every other field. See `fetch-efficiency-main-integration-20261027.md`.

- [x] Share Stock Detail forecast yield and no-dividend status with the buy-signal summary/detail and yield comparisons. Evidence: `StockDetailScreen.kt`; JDK 17 build and 216 unit tests pass, and emulator Compose regression confirms 2.67% under a stale zero quote; both installed APK hashes match. See `ai_docs/stock-signal-yield-20261027.md`.
- [ ] Visually verify live 8953 production buy-signal yield on physical SCG01; the final integrated build passes 216 unit tests and isolated buy-signal UI regression on both SCG01 and emulator, with matching installed APK hashes, but live 8953 values remain unverified.

- [x] Prepare the shared asset/Stock Detail forecast resolver for main integration after reviewing local/remote branches and preserving dirty/CSV worktrees. Evidence: source matches validated `fd52cbf2`; JDK 17 unit/build tasks succeed with all 213 prior unit results current, all 12 prior UI/refresh cases pass, and both devices install/launch with matching APK hashes. See `ai_docs/asset-dividend-source-main-integration-20261027.md`.

- [x] Unify Asset Management annual DPS and yields with the Stock Detail forecast sources for all supported non-fund holdings. Evidence: `AnnualDividendResolver.kt` shares issuer forecast, current-year event estimate, and Yahoo forward annual DPS; asset refresh loads the financial/event sources and applies forecasts after quote updates. JDK 17 build and all 213 unit tests pass; eight asset acquisition/UI cases and four tab navigation cases pass; installed APK hashes match on SCG01 and emulator-5560. Final emulator 8984 shows 5.93% and ¥20,944/year for four shares after withholding. See `ai_docs/asset-dividend-source-20261027.md`.
- [ ] Verify the updated asset dividend display on the unlocked physical SCG01. Installation, launch request, and APK hash are verified; physical UI acceptance remains unverified while the device is locked.

- [x] Integrate the four pending evaluation/comment commits with current main labels and explanation wording. Evidence: all 208 Android unit tests and the JDK 17 build pass; physical RFCNC0E4H8J and emulator-5560 show all four evaluation panels and the dividend explanation, with matching installed APK hashes and no fresh fatal exceptions. See `ai_docs/branch-integration-20261027.md`; unrelated dirty work and account CSV history are preserved.

## In Progress

- [ ] Verify stale-yfinance yield/PER/PBR fix through a normal production sync and API/R2 read-back. Local tests cover fresh, stale, missing-input and failed-refresh cases; production verification remains pending.
- [ ] Investigate stale 2060 quote acquisition after scheduled sync failures. Read-only production API on 2026-10-30 JST returned 2060 price 1,447 JPY dated 2026-10-26 with `price` status `failed`, while 7203, 8316 and 8783 returned 2026-10-29 prices with `available` status. GitHub run 40931121973 reports `v2-daily-sync` failure; job-log retrieval failed three times with `Transport closed`, so cause and actual app presentation remain unverified. Do not change the agreed price refresh cadence without a separate decision.
- [ ] Investigate automatic retry of missing Japanese prices after repeated API 35 test failures. On 2026-10-30, `visibleAssetScreenAutomaticallyRetriesMissingJapanesePrice` timed out in a 32-test suite and in two focused runs; the third run recorded lifecycle `RESUMED`, one request, failed marker true, 46 seconds elapsed and a still-missing price. Manual cooldown/retry passed. Whether this is application scheduling or Compose test-clock behavior remains unconfirmed; pause further focused attempts under the repository three-attempt rule.
- [ ] Verify dividend-index revision recovery in a safe deployed test environment. Local full, batch and yield refresh now attempt to advance the financial revision after an index PUT attempt, including lost PUT responses and later read-back or shard activation failures. Tests use fake storage; live R2 failures and Android cache refresh after such failures remain unverified.
- [ ] Verify legacy issuer-dividend migration revision recovery in a safe deployed test environment. Local migration tracks write attempts independently of per-security outcomes; a lost PUT response or failed read-back no longer suppresses the revision bump. Mock tests cover failed read-back, lost PUT responses and dry-run, while real R2 error recovery remains unverified.
- [ ] Verify legacy financial migration end-to-end in a safe R2 and Android test environment. A mock reproduction showed a valid same-source `yfinance_native` equity ratio was removed; migration now retains it only when both inputs and units are available and declared currencies do not conflict. The yfinance suite passes 135 tests including conflicting-currency seed fallback, zero and overflow boundaries and mock migration/read-back; read-only production API samples for FY2026 8316/7203/8058 retain their values in memory, while the old 8316 path substitutes 4.805151131 for 4.805331338149883. Production migration was not run.
- [ ] Verify the stale-DPS fix against a safely isolated Worker and real Android data before release. On 2026-10-29, production security API 8783 selected FY2010 adjusted DPS 16.6667 while its financial API reported no dividend; isolated API 35 search and detail displayed 45.05%. Local Worker now preserves valid forecasts but uses explicit `no_dividend` as zero and `unknown` as unavailable instead of substituting issuer actuals; 73 Worker tests pass with synthetic 8783, including search, catalog, quote and detail. The production zero yield is marked stale and issuer forecast was not requested; no production change was made.
- [ ] Verify the dividend-status changes on a physical device and through a locally hosted Worker with representative provider data. JDK 17 unit tests and debug/test APK builds passed; isolated API 35 passed four UI tests for unknown, no dividend, valid forecast and stale search-result fallback. Built and installed APK SHA-256 matched `1780ec216c21ad4f5bd749ff927fd20d3c25031aa7c366e96bdeb86e7793fd3b`.
- [ ] Complete release regression after resolving the stale-DPS and price-freshness policies. On isolated API 35, 14/15 additional audit, quality, notification and accessibility tests passed; the failed quality assertion incorrectly expected a 2025 calendar-year distribution to become current DPS. Its corrected offline-history test and three related quality tests passed after rebuilding the test APK with JDK 17; actual TalkBack service and real-device behavior remain unverified.
- [ ] Verify recovery of missing Japanese holding prices with real provider failures and on a physical device. The foreground asset screen retries missing Japanese quotes after the cooldown; 14 isolated API 35 asset/forecast cases passed, including empty response, cooldown, automatic recovery and saved data.
- [ ] Decide and verify same-day price freshness after upstream publication. On isolated API 35, 7203 cached at 2026-10-29 00:23 JST remained 3,102 JPY on restart at 07:19, although the API showed 3,051 JPY from the 2026-10-28 session after a 04:06 JST upstream update. The startup/daily attempt gates prevented another fetch.
- [ ] Verify the local Worker currency-compatibility guard against representative source data and Android without deploying. The local Worker suite passed 72 cases; explicit USD/JPY conflicts cannot feed either a calculated ratio or its recomputation.
- [ ] Verify fiscal-period dividend charts for missing issuer data, multiple periods in one year, and representative equities on real devices. Japanese-stock charts now use issuer fiscal records/forecasts without substituting calendar events; isolated API 35 shows 7203 FY2026 actual 95 JPY and labels the separate 2026 calendar event list.

- [ ] Complete content-level review of the remaining branch and worktree differences. Evidence: `branch-worktree-audit-20261027.md` records the reference comparison and 13 local snapshots; mixed financial-cache changes and two large deletion states remain unverified and were not merged.
- [x] Add the complete-universe Worker security-category API and Android's 45-day per-code cache, displaying REIT/ETF only for unclassified Japanese holdings. Evidence: Worker 58 tests and Android 178 unit tests pass; JDK 17 builds and five dedicated-emulator category/display/list regression tests pass with matching APK hashes; the new API logic returns all 4,451 codes from a production R2 snapshot. See `ai_docs/security-product-categories.md`.
- [x] Verify production security-category API-to-Android communication and refresh categories for newly added holdings. Evidence: JDK 17 build and 220 unit tests pass; 19 category/asset emulator cases pass; SCG01 receives `8953: 013` and displays `REIT（不動産投資信託）` with the installed APK matching SHA-256 `5bfd454aa7b6ce588e5aac7fffc1183421bb2e48184be999bd12ebf095c5c4a7`. See `security-product-categories.md`.
- [x] Deliver Stock Detail contextual comments with same-response financial history, extreme-value distinctions, comparable annual changes, and same-period related-indicator checks. Evidence: all 186 Android unit tests and the JDK 17 debug build passed; the emulator fetched cache version 7 with ten financial periods on 2026-10-26, and both SCG01 and emulator showed the five-period payout trend without repeating the numeric row label; installed APK hashes matched `01f7f6176701928cdbea85a99dc1dd82cb69742149c1956855c98f8367af4c82`.
- [ ] Prevent concurrent full-universe refreshes from reporting failures when another shard replaces the shared `financial_revision.json` between PUT and read-back. Evidence: Actions run #272 shard 4 saved and verified financial data for 518 securities, then reported 37 `Financial revision read-back mismatch` errors; production API confirms sampled affected code 1446 has current `yfinance_native` financials. The revision writer now accepts a different valid token as a concurrent publication, and its focused regression tests pass 3/3; the related refresh suite could not import because `boto3` is missing from the active Python environment. Remaining shards and final API/device-cache checks are still running.

- [ ] Apply the agreed no-dividend display fallback to the Stock Detail top card when no valid forecast or current annual DPS is available, then verify representative securities across the shared forecast paths. Evidence: `as-is-specification.md` records ¥0 and 0.00% as the user-facing no-dividend display; only 8984's ¥6,571 / 6.01% / 5.33% card-to-chart match has device evidence, and the no-value path remains to be implemented and checked.

- [x] Use forecast DPS and matching forecast yields in the Stock Detail top card. Evidence: `FinancialData.kt` accepts Yahoo `dividendRate` only with `jpy_per_share` units, and `StockDetailScreen.kt` prioritizes issuer forecasts then the existing dividend-event estimate; JDK 17 `assembleDebug` succeeded, 8984 displays ¥6,571 / 6.01% / 5.33% matching the chart on `emulator-5560`, and the installed APK SHA-256 matches on the emulator and SCG01.

- [x] Normalize Android payout ratios from their declared units and prevent percent values from being multiplied again. Evidence: production `/api/v1/financial-data/8984` declares `dividends.payout_ratio=100` as `percent`, while 7203 returns `27.040002` as `percent`; the JDK 17 debug APK built and installed with matching SHA-256 on `emulator-5560` and `RFCNC0E4H8J`, and the emulator now displays 8984 at `100.00%` instead of `10000.00%`.

- [ ] Publish and verify the financial statement unit contract end to end. Evidence: Yahoo V2 money now uses `yfinance_native`, reported currency is marked unverified, and ratio/margin inputs retain period/source/unit provenance. Worker routes pass 40/40, Python sync tests pass 120/120, Android unit tests pass 159/159, JDK 17 debug build succeeds, and the APK installs and launches on SCG01 and emulator-5554. Worker version `4d0c0767-e004-4b90-831a-f9236b1afa37` is live at 100%; production reads for 8058/6721/1301/6269 return contract 6 and unit metadata. The `financial_revision` token remains `2d6dd979696e4992a4d61a916af62228`, so full R2 refresh, device-cache renewal, and full-universe API audit remain pending.

- [x] Run the existing financial-unit parser and portfolio aggregation regression checks before closing the financial integrity work. Evidence: JDK 17 `:app:testDebugUnitTest` passed after merging current main, and the Worker route suite passes 50/50 cases.

- [ ] Complete the 4,355-security financial API unit audit after deploying Worker financial contract version 6 and refreshing R2 records. Previous post-shard runs still had 503 responses from the production Worker CPU limit, so the new version must be scanned again; no v6 production results are available yet.

- [ ] Verify multi-year dividend-event preloading end to end after Worker publication. Evidence: Worker `dividend-months` now accepts up to ten `event_years`; Android batches and caches requested years, and falls back to single-year requests on older Workers. The JDK 17 debug build installed and launched on SCG01 and the emulator; production publication, live API acknowledgement, and event-loading behavior remain unverified.

- [ ] Verify the 20-session company-info rotation and same-session catalog recovery on a regular scheduled sync. Evidence: `sync_v2.py` now rotates company info across 20 groups, reuses successful catalog entries for the same session, reconstructs interrupted entries from R2, and records action-history repair triggers; scheduled-run behavior and publication read-back remain unverified.

- [ ] Review the expanded per-metric stock evaluation explanations on device. Evidence: detailed mode now gives each indicator's formula, evaluation band, position, boundary distance, and interpretation limits; on-screen readability remains unverified.

- [ ] Run the GitHub Actions `refresh-company-info` mode across the active V2 universe and inspect the publication summary plus R2 catalog read-back. The isolated workflow and collector change are prepared; production results are pending.

- [x] Publish and backfill IRBANK financial-unit corrections. Evidence: PR #29 (`02b1e6d`) is live as Worker version `8ee553fa-3e66-49fd-99b0-d2f4a0d00d65`; the IRBANK v1 unit-metadata overlay covers 3,963 securities with zero hash mismatches; Actions run `40154919113` completed all four shards for 4,444 securities (3,693 migrated, 517 legacy records missing, 227 with no app fields, 7 unsupported codes), repaired 14,583 derived fields, and verified unit metadata on all 36,017 migrated documents; production 6721 FY2025 reports sales ¥429,000,000, assets ¥919,000,000, and equity ratio 37.362826218% with no equity-ratio anomaly; JDK 17 build, install, and launch checks succeeded on SCG01 and the emulator.

- [x] Classify the seven preferred-share codes and avoid a generic retry banner when optional financial metrics are unavailable. Evidence: PR #31 (`587b719`) is merged; latest-main JDK 17 build and installs succeeded on SCG01 and emulator, where 6721 shows equity ratio 37.4% for 2025/12, the DOE-specific missing-data reason, and no generic retry banner.
- [x] Verify production R2 migration run `40161333598`. Evidence: all four `migrate-financials` shards completed successfully and processed 4,444 securities: 3,693 already seeded, 517 legacy records missing, 227 with no app fields, and 7 preferred-share exclusions (`25935`, `50765`, `75505`, `92015`, `92025`, `94345`, `94346`); no unsupported codes remained, and 6721 was already seeded. Production API read-back confirms 2025/12 sales ¥429,000,000, operating loss ¥1,219,000,000, total assets ¥919,000,000, equity ratio 37.362826218%, with no equity-ratio anomaly.
- [x] Restore green yfinance sync tests. Evidence: commit 6da494fd passed all 115 tests in Actions run 40199242895, including the four dividend-audit tests; all production sync jobs were skipped.

- [x] Create a local J-Quants-only v3 database with project-local two-year daily history and API master, latest valuation, one-day financial summary, and calendar data. Evidence: `local_artifacts/jquants-v3-acquisition-20261025/jquants_v3.sqlite` contains 2,186,594 daily rows through 2026-07-31 and passes SQLite `integrity_check`; its report records coverage and source hashes.
- [ ] Complete full historical valuation and financial-summary acquisition and add J-Quants dividend/financial details if the account gains access. Current key only produced one valuation and summary date and returned HTTP 403 for Premium-only dividends and statements; exact standalone split-event history is not provided by the personal API. See `scripts/jquants_v3/README.md`.
- [x] Shorten the four Stock Detail metric-analysis sections, add position-specific comment variants, and show dividend-continuity explanations for every available indicator in 銘柄総合分析. Evidence: `StockAnalysisNarrative.kt` and `StockDetailCommentGenerator.kt` produce tested 100–140-character comments; 175 Android unit tests and the JDK 17 debug build passed; matching APK hashes were verified on `RFCNC0E4H8J` and `emulator-5560`, where the four headings and updated explanations were confirmed in the UI.
- [ ] Improve the generated copy in the detailed dividend-indicator explanation with yomiyasu. Evidence: the seven-indicator band text, extreme-value comments, trend/relationship text, and limitations were revised; JDK 17 `assembleDebug` succeeded, the card rendered on `emulator-5560`, and installed APK hashes matched on `emulator-5560` and `RFCNC0E4H8J`. Follow-up: review yomiyasu's remaining certainty candidate in the operating-margin limitation before closing; Android unit tests were not run.

- [ ] Deploy multi-year dividend-event support and verify Stock Detail preloading through the production API and on device. Evidence: Android now requests and caches the eight-year range during detail initialization and shows loading/failure/empty states; Worker source supports `event_years` with a fallback for older deployments, but publication and live-data verification remain pending.

- [x] Verify the yfinance `Ticker.calendar` refresh policy under success, near-event, and failure cases. Evidence: `sync_v2.py` implements a 30-day normal interval, 7 days when a stored ex-dividend date is within 45 days, and retries a failed request on the next XTKS session; Actions #214 on `d674fe7` passed all 111 tests, including the calendar-policy regressions. Actions #227 used byte-identical sync/workflow files and published R2 generation `2026-10-23-6d5e222d5ea843feb349574dd3c7e6e5` (4,380 successes, 64 failed securities; overall job failed). The Worker catalog returned that generation with 4,362 items; API reads for 2060, 7203, 2914, and 8058 returned available calendars whose 2026-10-17 fetch times were correctly retained because they were not due.

- [ ] Resolve the repeated 64 failures in Actions #215 and #227 before another full sync. Evidence: #227 logs show 16 symbols with 22 legacy dividend dates missing from V2 (1–4 per symbol); the guard checks V2 event partitions and archives before legacy cleanup, and production API reads show retained dividend history for all 16. The public API exposes only stored date bounds, so exact legacy event dates and amounts still require a read-only R2 audit. The 48 Yahoo HTTP 404 symbols all appear in the #227 J-Quants snapshot dated 2026-07-31; the official Free-plan documentation specifies a 12-week delay. JPX's current delisted list confirms 4171 (delisted 2026-08-13) and 6197 (2026-08-06); the other 46 are not listed there and still need current-listing/provider-coverage classification. Next: inspect the 22 event rows through read-only R2 access, then use a current universe source and classify the remaining Yahoo gaps before retrying the full sync.

- [ ] Publish and run the `refresh-company-info` GitHub Actions mode to backfill current yfinance statistics across the active universe. The local collector and workflow now support a forced full-universe `Ticker.get_info()` refresh; GitHub/R2 execution and read-back remain pending.

- [ ] Verify the provider-value priority for PER/PBR, current yield, and operating margin through the deployed API and Android UI. Local Python/Worker syntax checks and the JDK 17 debug build passed; production API and device behavior were not checked.

- [ ] Publish and backfill IRBANK financial-unit corrections. Local code now preserves source units, normalizes known IRBANK annual money values for app consumers, and repairs unproven equity ratios; production Worker publication, full-universe `migrate-financials` read-back, and regeneration/upload of the IRBANK v1 unit metadata are still pending.

- [x] Create a local J-Quants-only v3 database with project-local two-year daily history and API master, latest valuation, one-day financial summary, and calendar data. Evidence: `local_artifacts/jquants-v3-acquisition-20261025/jquants_v3.sqlite` contains 2,186,594 daily rows through 2026-07-31 and passes SQLite `integrity_check`; `local_artifacts/jquants-v3-acquisition-20261025/jquants_v3_report.json` records coverage and source hashes.
- [ ] Complete full historical valuation and financial-summary acquisition and add J-Quants dividend/financial details if the account gains access. Current key only produced one valuation and summary date and returned HTTP 403 for Premium-only dividends and statements; exact standalone split-event history is not provided by the personal API. See `scripts/jquants_v3/README.md`.

- [x] Add Japanese holding-data completion after existing CSV/DVM imports and Asset Management entry, with same-day retries after failures. Evidence: JDK 17 build and 165 JVM tests passed; 12 isolated emulator tests passed, including import/Compose-entry triggers, short failure cooldown, successful-empty caching and US exclusion. See `asset-data-refresh-fix.md`.
- [ ] Verify real-network recovery and production data completeness after reimport on SCG01; APK installation/startup passed, but the recovery scenarios were exercised with isolated fake HTTP on the emulator.

- [ ] Complete device and production acceptance for the five cross-stack review fixes. Local fixes cover interrupted publication recovery, fiscal-period dividend merging, withdrawn-DPS cache invalidation, five-character security codes, and nullable D1 keys; see `ai_docs/review-fixes-20261025.md` for test evidence and scoped publication/install steps. Production deployment, D1 migration, and device interactions remain unverified.

- [ ] Deploy multi-year dividend-event support and verify Stock Detail preloading through the production API and on device. Evidence: Android now requests and caches the eight-year range during detail initialization and shows loading/failure/empty states; Worker source supports `event_years` with a fallback for older deployments, but publication and live-data verification remain pending.

- [x] Consolidate remaining Android typography by semantic role: supporting 12sp, body 14sp, headings 18sp, and key metrics 24sp, while retaining 9sp chart labels. Evidence: `Typography.kt` centralizes the five sizes and affected financial values use the 24sp metric role; JDK 17 `assembleDebug` succeeds, fresh APKs install and launch on SCG01 (font scale 1.7) and emulator (1.0), and Asset Management, Annual Dividend, Watchlist projection, Stock Detail, Settings, Terms, and Feedback were visually checked.
- [ ] Review dense chart-label wrapping at font scale 1.7. Evidence: SCG01 Annual Dividend and Stock Detail screens show adjacent chart values wrapping or colliding; chart labels remain 9sp.
- [ ] Verify new-holding data hydration through manual registration, CSV/DVM import, and app restart on device. Evidence: new positive-share holdings now schedule security/quote, financial/analysis, dividend-month, and seven year-end price fetches into app-private caches; JDK 17 debug build succeeds, but add/import/cache-restore interaction was not exercised.
- [x] Source current annual DPS from IRBANK v2 data. Evidence: the Worker and Android use the IRBANK actual/forecast index and fiscal-year history; 8766 returns FY2026 adjusted actual ¥14.5333 and FY2027 forecast ¥16.34. The earlier 3.002% was calculated against the 2026-10-20 quote; direct screening yield is now tracked in the next completed item.
- [x] Source forecast yield from IRBANK screening and propagate it through the Worker and Android. Evidence: the paged refresh stored 3,779 unique values (3,781 rows, including two exact duplicate rows) with attribution and as-of dates; R2 SHA-256 read-back matched. Production Worker `1e65fe3c-5a9e-4a0c-b317-2a683cb85f71` returns 8766 at 2.855062% as of 2026-10-24, and the JDK 17 debug APK shows 2.86% in search and Stock Detail after migrating the old detail cache.
- [x] Install the updated APK on the physical Android device. Evidence: JDK 17 APK installation and launch succeeded on SCG01 (`RFCNC0E4H8J`); Stock Detail visibly shows 8766 at ¥544.3, DPS ¥16.3, and IRBANK yield 2.86%.
- [ ] Backfill IRBANK forecast data for the saved 4,000-code partial snapshot under the daily request limit. Evidence: ten prior batches checked 339 codes using 1,866 quota-counted calls (36.3 codes per 200 quota calls; 36.1 per 200 requests including usage checks); all ten had zero failures and their R2 read-back hashes matched. The saved snapshot has a next cursor, but this task now intentionally excludes fetching further catalog pages. Read-only R2 comparison on 2026-11-02 found 3,446 snapshot codes already present and 554 missing; 577 existing R2 codes fall outside this partial snapshot and will be preserved. Within the snapshot, 1,006 have a forecast (154 explicit zero), 551 are empty, and 1,889 are not requested; none has the new audit version, so the revised workflow will recheck all 4,000 snapshot codes (the prior 2,410 pending count is not the revised-workflow count). Applying the 339-code average to all 4,000, as approved by the user, estimates 22,018 quota calls, about 111 batches of 200, and six 20-batch daily windows (about 727 codes per full 4,000-call day). A local read-only audit found all 1,010 existing available forecasts have TDnet source refs, disclosure dates, and period metadata (1,009 disclosed in 2026 and one in 2025), while all remain unclassified by the new audit version. Added partial-snapshot mode, which records scope and keeps global `coverage_complete` false, plus an auditor that labels results `partial_snapshot_only`. The runner skips catalog paging and exits before R2 publication at zero quota; this was confirmed on 2026-11-02, with reset shown as 2026-11-03 00:00 JST. Revised extraction and R2 writes remain pending after quota recovery. The original three-issuer `doc_cache` pilot reused a cached ID before checking newer candidates; its 5.0-call average is optimistic. Production now reuses cached forecasts only after newer listed candidates are inspected, and the measurement script is updated to measure that guarded path on the next API window. Normative rules are in `ai_docs/irbank-dividend-forecast-acquisition-spec.md`.
- [ ] Prefer a known listed-security code match over the fund-name heuristic during CSV import. Evidence: `BrokerCsvImporter.kt` now resolves the local security master before classifying a row; JDK 17 `assembleDebug` succeeds. CSV behavior and regression cases remain unverified.
- [ ] Verify the attached SBI CSV imports code 8953 as `日本都市ファンド投資法人`, and confirm mutual-fund rows without a listed-security code still use fund-name matching.
- [ ] Unify Android financial-number typography as sans-serif with tabular figures, keeping monospace for identifiers and technical literals. Evidence: shared numeric styles and explicit financial-value overrides are updated; JDK 17 debug build succeeds, and the APK installs and launches on SCG01 and the emulator. Visual comparison on Watchlist and Total Assets remains pending.
- [ ] Visually compare Watchlist and Total Assets numerals, then inspect the affected financial values and input fields on device.
- [ ] Validate sharded legacy financial migration, including partition coverage, mixed-source ratio cleanup, report checkpoints, and R2 read-back. Evidence: the migration script and workflow now support four deterministic shards and resumable progress reports; this migration path was not run in the current integration.
- [x] Add the annual dividend chart's receipt-month/payment-start-month switch and remove the chart's unacquired-month count and annual-total label. Evidence: Japanese receipt month uses payment start date, then record date plus three months, then ex-dividend date plus three months when both are absent; the JDK 17 build succeeded, and after restoring the approved 18-stock DVM both chart modes showed the same ¥81,585.8 monthly average on emulator after the 280 ms transition.
- [x] Animate monthly dividend chart changes between ex-dividend month and receipt month. Evidence: 12 month positions and the common scale remain fixed while bar values interpolate over 280ms, labels crossfade, and monthly/quarterly summaries follow the transition; the JDK 17 debug APK built, installed, launched, and both modes were visually checked on the emulator and SCG01.
- [x] Keep the monthly average fixed while switching chart bases with nonzero event data. Evidence: on an isolated read-only API 35 emulator, both directions showed ¥87,070.3 during the transition and after it, while the bars moved between ex-dividend months 3/9 and receipt months 6/12; the JDK 17 APK built from latest `origin/main` matched the installed APK hash after interaction, and no fatal app exception was logged.
- [x] Limit the monthly chart to the most recent 12 months of dividend events for its upcoming-year estimate. Evidence: `AnnualDividendScreen.kt` applies one JST event-date window to both bases, and the chart explanation plus as-is specification describe the period; JDK 17 `assembleDebug` succeeded and the installed APK hash matched. On the isolated read-only API 35 emulator, the supplied SBI CSV preview had 23 selected securities (3 unmatched funds excluded), nonzero bars appeared, and switching basis redistributed months while the average stayed at ¥79,023.7; no fatal app exception was logged.
- [ ] Verify production per-event payment-start-date coverage. The existing yfinance calendar field is only the next announced date; the current device event history used the agreed record-date-plus-three-month fallback.

## Completed

- [x] Integrate the asset-registration copy change after its build and device checks. Evidence: only branch commit `916a5c12` was cherry-picked onto latest `origin/main` as `1b6ed769` and pushed because the source branch contained unrelated commits; JDK 17 `assembleDebug` passed, the installed APK hash matched on SCG01 and the emulator, and the revised labels were confirmed on the emulator.

- [x] Place the Asset Management “キャンセル” and “登録する” buttons side by side like the two-column allocation summary. Evidence: branch commit `48286a14` was rebased onto latest `origin/main` (`ee66d91b`) as `f3f32ec0`; the footer uses the same full-width `AdaptiveRow`, 6 dp spacing, and equal weights as the summary cards, the emulator showed the buttons side by side, JDK 17 `clean assembleDebug` succeeded, and the APK SHA-256 (`f42f6dac…d8d7345`) matched on SCG01 and the emulator.

- [x] Keep acquisition-price inputs ungrouped for all stocks and all four registration accounts, including stock selection, prefill, current-price application, and pasted commas. Evidence: `AcquisitionPriceInputTest` passes 2 tests, the emulator registration UI regression passes, JDK 17 builds and both device installs pass; SCG01 8008 displays `2015` in all accounts and enables registration with 100 shares. No real holding was added during validation.

- [x] Integrate feedback email notifications (`708d165c`) and the month-filter header fix (`ffd185ff`). Evidence: isolated integration preserves both completion records, all 59 Worker/D1 tests pass, JDK 17 `assembleDebug` succeeds, and the fresh APK installs/launches on SCG01 and emulator with live resumed activities and no process fatal errors. Targeted visual/interaction checks were not repeated during integration; original branch evidence remains above/below.

- [x] Prevent the selected-month Asset Management heading from squeezing the sort button into a tall sliver. Evidence: `AssetManagementScreen.kt` wraps header actions with FlowRow; JDK 17 debug build/install passed on SCG01 (font scale 1.7) and emulator (1.0), June-chart selection followed by Asset Management renders correctly, both clear to the full list, and SCG01 opens the sort sheet.

- [x] Add administrator email notifications for new support feedback. Evidence: migration 0006 records a transactional outbox; 48 isolated Worker tests pass, flipsave.org Email Routing is ready, and production test receipt `7fe56e6a-51ca-419c-9c2f-c6329b692fcb` has one successful send and a matching Gmail INBOX message. No Android changes are needed.

- [x] Unify heading, metric, and supporting-label sizes while retaining 14sp body text and 9sp chart labels. Evidence: shared Compose typography tokens and settings/distribution labels use 18sp, 24sp, and 12sp roles; the JDK 17 debug build succeeds, and SCG01 at 1.3 font scale was visually checked across all four main tabs and Settings from top to bottom.

- [x] Prioritize income-gap months not yet represented when selecting the three advice candidates. Evidence: `rankIncomeAdviceCandidates` greedily chooses the candidate covering the most still-uncovered empty months, then applies score and tie-breakers; the JDK 17 debug APK builds and is installed on emulator and SCG01.

- [x] Remove the calendar chart legend and reduce its unused top spacing while preserving monthly amounts and empty-month markers. Evidence: the legend row is removed and the plot/amount labels use a reduced top margin; the JDK 17 debug APK builds and is installed on emulator and SCG01.

- [x] Match the covered-month card's corner radius to the add button at 6dp. Evidence: the income-advice `Surface` uses `RoundedCornerShape(6.dp)`; the JDK 17 debug APK builds and installs on emulator and SCG01.

- [x] Remove the excess vertical gap in income-gap candidate cards and equalize the top, middle, and bottom vertical spacing around the add button and covered-month tile. Evidence: JDK 17 `assembleDebug` succeeds; emulator screenshot confirms the spacing, and the APK installs and opens on emulator and SCG01.

- [x] Group notification reminders into timing, notification types, and device permission, with a collapsible notification list and the device-settings button beside its permission summary. Evidence: JDK 17 debug build succeeds; fresh SCG01 install shows the requested layout and the notification list expands/collapses.
- [ ] Recheck the notification settings screen on the emulator after resolving its launch ANR; the APK installed, but the first frame took over 73 seconds and input timed out.

- [x] Show the selected forecast-period DPS in the dividend-event row, keep it visible when the selected bar is tapped again, and remove the always-visible forecast row. Evidence: JDK 17 debug build and SCG01/emulator installs succeed; tapping Takeda 4502's 2027期 bar twice leaves `2027年の配当イベント` with `予想¥204/株` visible, and the standalone 2027年3月期 forecast row is absent.

- [x] Source fiscal-period dividend bars from issuer-reported fiscal DPS when available. Evidence: the production 4502 payload has FY2026 DPS ¥200 and next-year forecast ¥204; the JDK 17 debug APK builds and installs on SCG01 and the emulator, and SCG01 shows ¥200 at 2026期 and ¥204 at 2027期 while its calendar-year 2026 event row remains separate.

- [x] Preserve the three approved portable override groups without changing snapshot version 1 or including feedback drafts. Evidence: 17 backup cases pass on API 26/35/33, and the final AAB survives offline OS full-backup/clear/restore on API 26 with all three groups and existing saved facts unchanged; see `android-release-acceptance-20261018.md`.
- [x] Retain financial failure categories across recreation, bound transient dividend-change retries, restore the asset-list scroll position, and move the initial override/security reads into restoration IO. Evidence: JDK 17 passes 142 JVM cases and Lint without errors; the full suite has 89 passes on API 35 and 88 passes plus one existing API-specific assumption on API 26, with all 32 isolated device cases passing and five production files unchanged.

- [x] Fix the reproduced API 26 Keystore chunk loss, missing no-dividend flag, and damaged auto-backup replacement path while retaining legacy DVM import and portable version-one JSON. Evidence: JDK 17 passes 137 JVM tests, Lint with zero errors, debug/test/release builds, 15 isolated regressions on API 26/33/35, and 6 existing UI cases on API 26/35; an 11,185,690-byte snapshot survives an offline OS full-backup/clear/restore and minified release startup preserves backup facts. See `android-backup-followup-20261018.md` for the unresolved limits.

- [x] Fix the confirmed Android release-audit error, retry, lifecycle, StateFlow, and threshold accessibility regressions without changing rendered UI. Evidence: the JDK 17 build passes 137 JVM tests, Lint with zero errors, debug/test APK assembly and unsigned R8 release assembly; API 26/35 each pass five new regressions plus six existing UI cases, and SCG01 API 33 passes all five new cases with five existing production-file hashes unchanged. See `android-release-audit-20261018.md`.

- [x] Correct the annual DPS field contract, stop TTM totals from inflating the 2026 portfolio chart, and migrate held-security cache values. Evidence: all 43 yfinance sync tests and 3 focused Worker tests pass; the JDK 17 repository unit test and debug APK build pass; Worker version `f154c6b2-85e1-408b-9474-862c745dc3a0` returns 8214/8058/6493/6721/2914/8306 at ¥90/¥125/¥20/¥0/¥272/¥96 per share with TTM totals separate; SCG01 cache contract v2 stores those six values and the chart displays ¥693,000 for 2026.

- [x] Show each security's next fiscal-year DPS forecast in the final Stock Detail chart bar. Evidence: the JDK 17 debug build succeeds; SCG01 displays `予想 ¥272` at `2026期` for 2914, and emulator UI state displays `予想 ¥82.5` at `2027期` for 4626.

- [x] Calculate and label Stock Detail's calendar-year dividend-total CAGR from yfinance events. Evidence: `FinancialData.kt` selects the latest two to five consecutive annual totals, and the JDK 17 debug APK displayed `暦年配当合計CAGR 13.7%` for 2914 on device `RFCNC0E4H8J`; consecutive-increase and no-cut counts remain unavailable for this history basis.
- [x] Publish yfinance retrieval metadata through the security API and show it on Android Stock Detail. Evidence: Worker version `3cdc544d-c1f4-404b-9246-76c5a04b46ca` returned the added ticker, currency, source/status, and dividend-history bounds in production; all 20 Worker tests passed, and the JDK 17 debug APK installed and displayed the information card on device `RFCNC0E4H8J` and emulator `emulator-5554`.
- [x] Publish the J-Quants adjusted daily quote subset through the Worker API. Evidence: `src/index.js` streams the selected R2 object; production returned HEAD 200, ranged GET 206, full GET 200 with 93,355,497 bytes, and existing health/search-catalog routes returned 200.
- [x] Fill missing quote fields in search suggestions from the published R2 catalog. Evidence: `SearchSheet.kt` requests quotes for incomplete search hits and `CloudflareMarketRepository.kt` falls back to `/api/v1/search-catalog` with a five-minute in-memory cache; the JDK 17 build succeeded, and emulator plus device `RFCNC0E4H8J` showed query `80` results with prices/yields (for example 8056: ¥4,790 / 2.99%). The current catalog quote date is 2026-10-09; one of 30 results has no yield value and correctly remains unavailable.
- [x] Remove the yfinance-specific calendar-year label from the dividend analysis card. Evidence: the label composable was removed from `DividendChangeAnalysisCards.kt`; JDK 17 `assembleDebug` succeeded and the APK installed/launched on emulator `emulator-5554` and device `RFCNC0E4H8J`.
- [x] Compare repaired yfinance dividend events with issuer disclosures for five forward splits and five reverse splits. Evidence: 5/5 forward-split samples matched; only 2/5 reverse-split samples were consistent across the audited events, while Feed One (2060), Kawasaki Geological (4673), and Jimoto Holdings (7161) had transition-date or same-year event mismatches.
- [x] Audit the 20 split and reverse-split securities added to the emulator. Evidence: the final JDK 17 APK opened all 20 detail screens; 15 split and 15 reverse-split graph cases were visually checked, and 20 sampled pre-event December closes matched R2. After v2 publication, production 7203 history returned the matching 2018–2025 dividend values and its 2021 5:1 split; production 2060 returned its 2020 1:5 consolidation. DPS state handling was rechecked on 1326 and 8783.
- [x] Require same-session daily closes and refresh eight December prices after a newly reported split; recreate the yfinance sync test workflow. Evidence: `completed_close_for_session`, split-event comparison, and the forced full December-price refresh are covered by eight passing offline unittest cases; `benchmark_v2.py` now measures the same price refresh path, and GitHub Actions runs deterministic tests on relevant pushes/PRs and on manual `test` dispatch.
- [x] Migrate app-used legacy financial and valuation fallback data into v2 and verify the production API. Evidence: Actions run #74 processed 4,444 securities with status `complete`, migrated 2,982 fields for 71 securities, and reported zero failures; 519 legacy objects were missing, 227 securities had no app-used fields, 3,620 were already seeded, and 7 codes were unsupported. The migration read back and verified each changed R2 object. Worker version `e3584082-fa47-4d72-a90c-4f8d9e068382` serves the v2-only API; five production samples returned 10 annual rows and 10 valuation rows, and 1605 retained valuation periods 2019-03-31 and 2019-12-31 as distinct records.
- [x] Expose honest company-information freshness in the Worker and Stock Detail, including a one-time refresh for older device caches missing the new metadata. Evidence: Worker version `9d64ad74-e209-4ef4-8b60-1beb616fbf5f` returns legacy `last_fetched_at: null` with the explicitly labeled sync-date fallback; production 7203 PER/PBR remain 8.805282/0.982036 from close ÷ EPS/BPS; Worker tests pass 29/29; JDK 17 unit tests and debug build pass, and emulator Stock Detail displays `取得日時未記録（最終同期 2026-10-16）` after refreshing the old cache. The final APK installs and launches on SCG01 and emulator.

## Next

- [ ] Unify Android financial-number typography as sans-serif with tabular figures, keeping monospace for identifiers and technical literals. Evidence: the shared numeric style and financial-value overrides are updated; JDK 17 debug build succeeds and the APK installs and launches on SCG01 and the emulator. Screen-specific visual comparison remains pending.
- [ ] Visually compare Watchlist and Total Assets numerals, then review the changed financial values and numeric inputs on device.

- [ ] Verify automatic restore of the user's existing Google Drive `.dvm` on device. The first successful picker import now stores persistable document access and later taps read the saved URI; actual access to the user's Drive file still needs device confirmation.

- [ ] Reconcile the requested five-session `Ticker.get_info()` cadence with the current implementation. Evidence: `sync_v2.py` currently sets `COMPANY_INFO_BUCKETS = 20`; scheduled run [#285](https://github.com/masacgt/Diview/actions/runs/40379903489) recorded one aggregate `ticker_get_info` call and does not establish per-security cadence. Confirm the intended freshness policy and test distinct sessions before changing or accepting the rotation.
- [x] Run the eight-shard `refresh-all-financials` workflow for all active V2 securities and inspect the reports plus production API read-back. Evidence: Actions run #155 (`39132130695`) completed all eight shards successfully for 4,016 securities and 35,191 annual rows; each shard reported 502 successes and zero failures or partial records.
- [x] Confirm `field_status_reason` appears in the production financial-data API after the V2 refresh. Evidence: run #155 read back its shard objects; production `/api/v1/financial-data/4502` returns `field_status_reason` with calculated DOE/net D/E inputs and missing-input reasons on older periods.

- [x] Add annual net debt-to-equity collection and expose it through the financial API and Android. Evidence: targeted refresh run #121 wrote and read back 10 rows for 7203; production API reports DOE and net D/E available for 2023/03–2026/03, and SCG01 displays 3.10% DOE and 0.75倍 net D/E for 2026/03; JDK 17 passed 143 Android unit tests and the debug APK build/install, and Actions run #124 passed 55 yfinance collector tests.
- [ ] Complete the policy-v2 first pass across all securities over five successful scheduled Tokyo-market sessions; verify a non-7203 security's financial values in R2, API, and Android.
- [x] Correct cross-source equity-ratio calculation and verify production/API/app values. Evidence: migration run #175 (`39378087521`) completed 4,442 securities with zero failures, adding 1,219 missing seed fields for 125 securities after verifying each changed R2 object. Production 2026-03-31 API rows now show 4502 at 47.896322897% and 7203 at 37.829768942%, both retained from `source_values.seed.values.equity_ratio_computed`; their yfinance monetary inputs remain separately sourced and the field is marked `stale`. The emulator Stock Detail screen displayed 4502 at 47.9% and 7203 at 37.8%, matching the API. The local collector suite passed 64 tests and Python syntax compilation before migration.
- [x] Run the full yfinance collector test suite in its pinned Python environment. Evidence: Actions run #124 (`39115343966`) passed all 55 deterministic tests.

- [x] Publish and verify the V2 income-advice catalog through the Worker and Android. Evidence: production `/api/v1/income-advice-candidates` returned generation `2026-10-19` with 2,993 candidates; with four uncovered months on the emulator, the JDK 17-selected app displayed the ranked candidates 4465, 9678, and 4992 with covered months and per-share forecast amounts. Specification: `ai_docs/watchlist-income-gap-advice-spec.md`.

- [x] Publish and run `migrate-irbank-dividends`, then verify R2 read-back and `/api/v1/financial-data/8303` exposes IRBANK fiscal-year DPS separately from yfinance calendar-year events. Evidence: Actions run #91 migrated 36,609 records across 2,708 symbols with zero failures; 643 were already present and 1,086 had no legacy records. Follow-up Actions run #205 checked all 4,444 active securities and 45,646 source records, inserted the one missing 604A.T row, confirmed 3,351 securities already matched, and reported zero failures; the migration script read back the inserted v2 record. Production API read-backs show 8303 FY2019 DPS 10 separately from yfinance CY2019 zero.
- [x] Review seven active five-character security codes reported as unsupported by the dividend migration. Evidence: exact-code legacy IRBANK dividend objects are absent and only four-character parent records are available; because J-Quants defines the fifth character as a share class and parent-code data is common-share DPS, no parent values are copied. The seven remain unsupported until class-specific history or verified equivalence is available; their exact-history lookup remains a separate open item below.
- [x] Run the targeted V2 financial refresh for 7203 and verify the stored annual numerator, stockholders' equity, calculated DOE, and API response. Evidence: Actions run #85 (`39082268464`) completed successfully for `7203.T`; it wrote and read back 10 annual financial rows, with DOE available for 2023/03–2026/03. The production Worker API returns the dividend total, period-end stockholders' equity, calculated DOE, and `available` status for those four periods.
- [x] Verify that the refreshed 7203 DOE value appears in the Android app. Evidence: the JDK 17 APK displays `3.10%` and `2026/03 計算値` on SCG01; the production API row reports `3.1037313846735177` for that period.
- [x] Publish and verify history-based upcoming ex-dividend estimates and exchange-calendar rights dates. Evidence: the rights-date workflow published 15,265 records from 330,359 input events, including 3,423 history estimates for 3,713 symbols with zero skipped events; production API checks for 7203, 2914, and 4626 return `estimated_from_history: true`, and the JDK 17 emulator displays 4626's 2027-03-30 estimate, 2027-03-29 final-rights date, and 2027-03-31 calculated record date. Python tests pass 5/5 and focused Worker tests pass 6/6; Android unit tests and clean JDK 17 build pass. The full Worker suite passes 18/20; two unrelated existing assertions fail for the security metadata fixture and legacy financial route. Worker version `85b22aae-50bc-47c0-9d40-d769be051d6a` is deployed.

- [x] Verify V2 calendar-year dividend streak initialization, daily sync updates, Worker API propagation, and Android display. Evidence: `sync_v2.py` recomputes `calendar_year_metrics` from successful stored event history and retains the prior metrics as stale on event-fetch failure; the Worker reads those fields from V2 `current.json`. On 2026-10-20, production `/api/v1/financial-data/2060` returned source `Yahoo Finance via yfinance`, history basis `calendar_year_observed_distributions`, metrics status `available`, as-of year 2025, consecutive increase 2 and no-cut 4. The emulator Stock Detail for 2060 displayed “連続増配年数は2年” in the analysis comment and “非減配4年”; the same screen showed the live ¥1,462 close and 3.56% yield.

- [x] Run the read-only full-universe rights-date coverage audit and record the artifact results. Evidence: [Actions run 39377399629](https://github.com/masacgt/Diview/actions/runs/39377399629) succeeded on 2026-10-20 and saved [the 4,442-security JSON report](https://github.com/masacgt/Diview/actions/runs/39377399629/artifacts/12245131125). It found 3,423 history estimates, 680 empty histories, 74 available histories without positive events, 186 histories without a recurring month, 24 failed histories without an estimate, 52 missing current documents, and 3 empty-status histories that still have positive event rows. The workflow reported zero R2 writes; it took 25m45s and was a one-off manual audit, not a scheduled sync sample.
- [x] Explain the three `empty_status_with_event_rows` audit results without changing R2. Evidence: production `/api/v1/securities/{code}` reports `dividend_history.status=empty`, `last_attempt_status=success`, `period=1y`, and `last_coverage_event_count=0`, while `stored_history_status=available` and stored date bounds exist. `/api/v1/dividend-months?symbols=...&event_year=...` returned 5138.T 2025-03-28 ¥35, 6034.T 2022-12-29 ¥30, and 7578.T 2021-03-30 ¥6. Thus `empty` describes the latest one-year fetch, while older event partitions remain available by year; R2 was not changed.

- [x] Validate the published v2 upcoming ex-dividend path, source/status metadata, and Android detail display. Evidence: production `/api/v1/dividend-months?symbols=2060.T` returned `2027-03-30`, `available`, source `Yahoo Finance via yfinance Ticker.get_info`; the emulator and device displayed `2027年3月30日`. Opening Stock Detail now reads this route for the viewed security, subject to the existing per-JST-day cache. `1326.T` returned `history_status: empty` with no upcoming event and the detail showed `権利落ち日未取得`. Code review confirms that a request failure is logged without replacing cached data or advancing fetch markers, so a later detail view can retry; no live HTTP failure was induced. At the 2026-10-17 check, the security response reported `ex_dividend_calendar.status: not_fetched` and the date came from the `get_info` fallback. The 2026-10-20 production read-back now shows the saved `Ticker.calendar` metadata and separately labels the display API date as a historical estimate; see the later evidence below.

- [x] Verify the first normal scheduled XTKS sync passes the calendar gate and processes the complete J-Quants universe. Evidence: scheduled Actions run #159 (`39304823002`) began at 2026-10-20 03:42:51 JST, about 6h26m after its configured 21:17 JST cron; with `ALLOW_CLOSED_MARKET=false`, it selected completed XTKS session `2026-10-19` and processed all 4,442 codes, publishing generation `2026-10-19` with 4,356 items (4,335 success, 107 failed).
- [x] Compare available J-Quants master snapshots to explain the #158/#159 universe-count difference. Evidence: the API returned 4,444 normalized codes with source Date=2026-07-27 and 4,442 with Date=2026-07-28; only 191A and 3271 disappeared, with no additions. This matches the two-code count change, but #158/#159 did not retain their response date or exact code list, so the mapping to those specific runs remains an inference.
- [ ] Capture and compare the exact J-Quants response date and full normalized code list for a future normal sync. Evidence: normal and full-universe workflows now save a 30-day artifact containing response dates, count, SHA-256, and sorted codes; a future run is required because historical #158/#159 artifacts contain no snapshot.
- [ ] Verify `Ticker.calendar` is freshly fetched during a normal sync. Evidence: production read-backs after scheduled #159, manual #161/#166/#177, scheduled #195, and the 2026-10-26 check still report 2060.T `calendar.date=2027-03-30`, `status=available`, source `Yahoo Finance via yfinance Ticker.calendar`, and `fetched_at=2026-10-17T14:31:41+09:00`. Run #195 logged two aggregate `ticker_calendar` calls without symbol attribution, so it does not prove a refresh for 2060.T. Earliest expected refresh under the 30-day policy is XTKS session 2026-11-16. `/api/v1/dividend-months` selecting the same date as `estimated_from_dividend_history` is distinct from both the stored `Ticker.calendar` value and the `Ticker.get_info` fallback.
- [x] Run the read-only `check-calendar` workflow before relying on the delayed-session recovery. Evidence: Actions run #146 (`39127924625`) successfully read R2, selected the latest completed XTKS session `2026-10-16`, found it already published on the closed day `2026-10-18`, and skipped `v2-daily-sync`; no J-Quants/yfinance collection or R2 write ran.

- [x] Validate daily-chart dividend and split candidates against repaired yfinance history on a representative 500-security sample that includes split and reverse-split events. Evidence: for 2026-10-16, 255 recent event codes from the J-Quants universe were included (254 split, one reverse split); 489 of 499 successful method pairs matched, ten differed, and 1432.T had no data from either method. Eight differences were split-day dividend amounts requiring repair; two were ETF distribution-date corrections. On Actions run #49, 16 versus 24 workers processed the same 500-code sample in 358.080 versus 297.187 seconds with 475/500 closes (95.0%) and zero HTTP 429s; repaired history remains the stored source of truth.
- [x] Classify synchronization failures by retryability, including mapping known Yahoo HTTP 404/no-data responses to `no_data`, while keeping transport/server failures retryable and preserving empty-versus-failed state. Evidence: `sync_v2.py` now classifies missing-data, transient, and unknown errors; same-session `no_data` retries are suppressed while last successful values remain published. Unit tests verify HTTP 404/429/503/timeout classification, retry-session behavior, state persistence, and empty-versus-failed event handling.
- [x] Capture yfinance/R2 active wall-clock measurements during a production-shaped full-universe Actions run. Evidence: Actions run #49 processed all 4,444 J-Quants codes in 4,973.833 seconds (82.90 minutes), including 4,808.925 seconds of security collection, 151.735 seconds for catalog build, and 6.166 seconds for publication. R2-only activity was 4,568.040 seconds, yfinance-only 0.010 seconds, and overlap 240.856 seconds. It published generation `2026-10-16` with 4,341 catalog items; 4,390 securities succeeded and 54 failed, so the job correctly ended with a partial-failure status after publishing. The run was a forced same-session reprocess, not an ordinary next-session measurement.

- [x] Publish and verify the eight-year December-price R2 shape, including final-session date labels. Evidence: Actions run #51 scanned 28,950 monthly-price year partitions, normalized 25,680 December points, recorded zero failures, and made zero yfinance requests; each changed object was read back. Production `/api/v1/monthly-price-history` returned all eight expected 7203 dates, and a 20-security split/reverse-split sample returned 142 price points with every date matching the final XTKS session; 1491 had no stored price history and remained empty. Idempotence rerun #160 scanned 28,999 partitions with zero updates, normalized points, Yahoo requests, or failures. After #160, production API reread still returned all eight 7203 dates from 2018-12-28 through 2025-12-30. A 2026-10-20 production API reread also confirmed all eight December dates for both 7203 and 2060: 2018-12-28, 2019-12-30, 2020-12-30, 2021-12-30, 2022-12-30, 2023-12-29, 2024-12-30, and 2025-12-30.
- [x] Measure the staged yfinance v2 acquisition on a small sample. Evidence: `benchmark_v2.py` processed 40 J-Quants securities (10 each across equities, ETFs, REITs, and other listed securities) in 66.45 seconds with 142 high-level yfinance method calls; two requests returned `YFPricesMissingError`; no R2 access or writes occurred.
- [x] Read back the production catalog after full-universe run #233. Evidence: Worker `/api/v1/search-catalog` returned generation `2026-10-23-bd000f1395fc483ebc02971bf830ba23`, market data date `2026-10-23`, and 4,362 entries, matching the generation published in the run log.
- [x] Rerun the read-only dividend-event audit after #233 with the corrected legacy-missing path. Evidence: [Action #40194212582](https://github.com/masacgt/Diview/actions/runs/40194212582) passed; all 16 sampled codes had missing legacy baselines but readable V2 records (131 current objects / 206 events and 75 archive objects / 91 events), with zero R2 read failures.
- [ ] Recover or independently verify the 16 legacy dividend baselines and recheck the four previously reported event-date gaps. Evidence: audit #40194212582 found no legacy baseline for any sampled code, so V2 presence is confirmed but historical date completeness against the old source remains unverified.
- [x] Cross-check the 48 Yahoo HTTP 404 failures from full-universe run #233 against official exchange listings. Evidence: JPX sources identify 3450 (TOKYO PRO Market), 4171, and 6197 as delisted; 5617 is listed on TOKYO PRO Market; the remaining 44 appear in the JPX 2026-09-30 TSE list. Sources: [JPX stock delistings](https://www.jpx.co.jp/listing/stocks/delisted/index.html), [JPX TOKYO PRO Market delistings](https://www.jpx.co.jp/equities/products/tpm/delisting/), [JPX 5617 listing](https://www2.jpx.co.jp/tseHpFront/StockSearch.do?topSearchStr=5617), and [JPX TSE list](https://www.jpx.co.jp/markets/statistics-equities/misc/01.html).
- [ ] Determine why Yahoo returned HTTP 404 for the 45 non-delisted securities in run #233 before changing retry or retention behavior. Evidence: the run logged 48 unique same-session no-data retries and published the 4,362-item catalog with 4,396 successful and 48 failed securities; official listing status does not explain the 44 current TSE listings or active TOKYO PRO Market listing 5617.
- [ ] Confirm repeated normal-session full-universe syncs stay within the 2,000-minute monthly Actions budget, then set an evidence-based timeout. Evidence: the initial 2026-10-16 legacy migration processed 4,444 securities in 8,067.56 seconds (134.46 minutes). Forced same-session run #49 took 4,973.833 seconds (82.90 minutes), used 1,096 high-level yfinance calls, and recorded 86,822 `get_object`, 78,542 `get_body`, 14,855 `list_objects_v2`, and 74,967 `put_object` calls. After the R2-read/write reduction, forced same-session run #56 completed its script in 4,779.87 seconds and its Actions job in 80m11s; it processed 4,444 codes, published a 4,341-item catalog, and recorded 4,390 successful and 54 failed securities. It used 68 high-level yfinance calls and recorded 82,568 `get_object`, 78,486 `get_body`, 14,850 `list_objects_v2`, and 70,468 `put_object` calls. Run #72 on 2026-10-17 took 4,682.131 seconds; its 4,323 high-level yfinance calls were all `Ticker.calendar`, because other successful fields were reused from the same market session. It processed 4,390 securities successfully and 54 with failures, and published the 4,341-item catalog. These forced/same-session runs do not confirm ordinary monthly workload. Keep the 360-minute timeout until multiple normal-session runs are measured.
- [ ] Continue the monthly-budget measurement with independent normal sessions. Evidence: scheduled #159 used 11m31s active runner time (including the calendar job), processed 4,442 codes (4,335 success, 107 failed), used 622 high-level yfinance calls, and recorded zero HTTP 429s; the detailed R2 metrics are in `ai_docs/r2-yfinance-target-spec.md` and `ai_docs/yfinance-normal-sync-measurement-20261019.md`; #158 was a same-session follow-up and is not an independent sample. Later normal schedules #195–#197 used 77m59s, 82m31s, and 87m00s active runner time, but #196/#197 logs did not expose complete outcomes or telemetry. These runs predate the current 20-session company-info rotation. Keep timeout 360 minutes until multiple complete normal-session measurements of current code are available.
- [x] Record the XTKS decisions, universe completion, and active runner times for scheduled runs #195–#197. Evidence: #195 passed the gate for session 2026-10-21, processed 4,442/4,442 J-Quants codes, and published generation `2026-10-21-f348fe9d31054a7a94665f0494777dc1`; #196 passed for session 2026-10-22; #197 detected the 2026-10-24 market closure and selected unpublished session 2026-10-23. The detailed call, R2, failure, publication, and artifact evidence is recorded in `ai_docs/r2-yfinance-target-spec.md` and `scripts/yfinance_r2/README.md`.
- [ ] Verify monthly Actions usage under the current 20-session company-info rotation. Evidence: #195–#197 averaged 82m30s, projecting to 1,897.5 minutes across 23 sessions, while the slowest 87-minute run projects to 2,001 minutes. #196/#197 have incomplete run logs, and all three predate the current rotation; therefore this projection does not confirm the current implementation is within 2,000 minutes. Keep timeout 360 minutes until multiple complete post-change scheduled runs are measured.
- [x] Resolve the failing yfinance tests before starting the 500-security benchmarks. Evidence: commit `bb7e6cfd` corrected the stale-yield fallback and aligned regression expectations with the current field/unit metadata; push run [#240](https://github.com/masacgt/Diview/actions/runs/40193506451) passed all 112 tests, and `v2-daily-sync` was skipped.
- [x] Read back the production catalog after full-universe run #233. Evidence: Worker `/api/v1/search-catalog` returned generation `2026-10-23-bd000f1395fc483ebc02971bf830ba23`, market data date `2026-10-23`, and 4,362 entries, matching the generation published in the run log.
- [x] Rerun the read-only dividend-event audit after #233 using the corrected legacy-missing path. Evidence: [Action #40194212582](https://github.com/masacgt/Diview/actions/runs/40194212582) passed; all 16 codes had missing legacy baselines but readable V2 records (131 current objects / 206 events and 75 archive objects / 91 events), with zero R2 read failures.
- [x] Verify the corrected audit tests in GitHub Actions. Evidence: push run [#40194212580](https://github.com/masacgt/Diview/actions/runs/40194212580) passed all 116 tests, including the four new audit cases; `v2-daily-sync` was skipped.
- [ ] Recover or independently verify the 16 legacy dividend baselines and recheck the four previously reported event-date gaps. Evidence: audit #40194212582 found no legacy baseline for any sampled code, so V2 presence is confirmed but historical date completeness against the old source remains unverified.
- [x] Cross-check the 48 Yahoo HTTP 404 failures from full-universe run #233 against official exchange listings. Evidence: JPX sources identify 3450 (TOKYO PRO Market), 4171, and 6197 as delisted; 5617 is listed on TOKYO PRO Market; the remaining 44 appear in the JPX 2026-09-30 TSE list. Sources: [JPX stock delistings](https://www.jpx.co.jp/listing/stocks/delisted/index.html), [JPX TOKYO PRO Market delistings](https://www.jpx.co.jp/equities/products/tpm/delisting/), [JPX 5617 listing](https://www2.jpx.co.jp/tseHpFront/StockSearch.do?topSearchStr=5617), and [JPX TSE list](https://www.jpx.co.jp/markets/statistics-equities/misc/01.html).
- [ ] Determine why Yahoo returned HTTP 404 for the 45 non-delisted securities in run #233 before changing retry or retention behavior. Evidence: the run logged 48 unique same-session no-data retries and published the 4,362-item catalog with 4,396 successful and 48 failed securities; official listing status does not explain the 44 current TSE listings or active TOKYO PRO Market listing 5617.
- [x] Identify the repeated valuation-serialization `AttributeError` source. Evidence: manual run #177 recorded the traceback at `sync_v2.py:2622`; `collect_financials()` returns valuation rows as a mapping keyed by ISO `period_end`, but `collect_symbol()` iterated the mapping as row objects. This explains the 58 such failures in run #177; retry classification and source values were not implicated.
- [ ] Verify the valuation lookup correction in a normal scheduled XTKS sync and check that the previous failure mode is absent. The collector now selects `valuation.get(period_end)`, matching the mapping contract; push Actions run #39395495665 passed `yfinance-sync-tests`. Manual #177 predates the edit but published session `2026-10-20`; code inspection shows its failed symbols start that session at attempt 1, so a same-session scheduled follow-up can retry within the scheduled four-attempt cap and verify the fix. Production baseline after #177: `/api/v1/securities/2972` reports `processing_status=failed`, the valuation `AttributeError`, and last attempt `2026-10-20T16:34:02.951968+09:00`; `/api/v1/financial-data/2972` retains four annual records through `2025-08-31`, while `valuation.history` is empty. Compare these API fields after retry. Exclude the same-session follow-up from independent monthly-budget samples because #177 already processed the session.
- [x] Confirm the manual sync's extra per-security attempt is intentional. Evidence: on 2026-10-18 the user confirmed that manual sync should retain up to five total attempts while scheduled runs retain the four-attempt cap; workflow settings remain unchanged.
- [x] Record the completed manual full-universe rerun #161 and separate it from normal monthly measurements. Evidence: Actions run #161 (https://github.com/masacgt/Diview/actions/runs/39328353180) scanned all 4,442 J-Quants codes and published generation 2026-10-19 with 4,335 successes, 107 failures, and a 4,356-item catalog. It made 454 high-level yfinance calls and had zero HTTP 429s; the failures were 55 AttributeErrors and 52 same-session HTTP 404 suppressions. The sync job ran 85m33s plus 25s for the calendar job (85m58s total active runner time); R2 active wall time was 4,954.798s, with 84,531 get_object, 79,601 get_body, 14,173 list_objects_v2, and 66,874 put_object calls. Publication completed before the job returned failure for partial ticker errors. This manual run repeated session 2026-10-19 and is not an independent scheduled-session sample.
- [x] Repeat the split-transition dividend audit without modifying R2. Evidence: read-only Actions run #162 (https://github.com/masacgt/Diview/actions/runs/39331846984) audited 1,068 securities and 1,270 split events, covering 7,345 issuer fiscal-year comparisons: 6,206 matched, 1,139 mismatched, 160 securities had no issuer DPS snapshot, and zero securities failed technically. The audit script reads R2 through get_object and writes only local report files; artifact: https://github.com/masacgt/Diview/actions/runs/39331846984/artifacts/12227883474.
- [x] Distinguish the stored 2060.T Ticker.calendar value from the date selected by the display API. Evidence: after run #161, the production security response exposes current.json calendar date 2027-03-30, status available, source Yahoo Finance via yfinance Ticker.calendar, and fetched_at 2026-10-17T14:31:41+09:00. No ticker_calendar call was present in run #161's 454-call breakdown. The 2026-10-20 dividend-months response selected the same upcoming date from Yahoo historical dividend events with status estimated_from_dividend_history; this is neither a fresh calendar fetch nor the Ticker.get_info fallback.
- [x] Repair duplicate 2060.T dividend events through the yfinance history rebuild. Evidence: merged main commit `1d7ce0a` ran in manual Actions run #166; production API read-back at `last_attempt_at=2026-10-20T08:42:24+09:00` shows 2026-03-30 ¥24.5 and 2026-09-29 ¥26 once each, with `trailing_12_month_per_share=50.5` (previously 505). The collector’s 65 fixed-data tests and Python compilation passed before merge. Run #166 completed after publishing generation `2026-10-19`, with 4,334 successes and 108 failures; see the separate full-universe measurement.
- [x] Compare the repeated `AttributeError` cohort across full-universe runs. Evidence: complete logs for #158, #159, #161, and #166 contain the exact same 55 failed codes (51 `A`-suffix codes plus 2972, 4616, 6085, and 7085); this establishes a stable affected cohort but does not identify the failing call or justify a parser/retry change.
- [x] Record optimized full-universe measurement run #174 and distinguish it from normal-session budget evidence. Evidence: [Actions run #174](https://github.com/masacgt/Diview/actions/runs/39369145041) scanned all 4,442 saved J-Quants codes and published generation `2026-10-19` with 4,356 catalog entries; 4,227 succeeded and 215 failed (163 attempt-limit, 52 same-session HTTP 404 suppressions). It used 2,872 high-level yfinance calls, all `history_actions_max`, with zero HTTP 429s. The manual same-session runner duration is recorded in the target spec and README, but does not count as an independent normal-session monthly-budget sample. Production 2060.T `current.json` still has its `Ticker.calendar` value fetched on 2026-10-17; the equal date returned by `/api/v1/dividend-months` is a historical-event estimate.
- [x] Record manual full-universe sync #177, exact J-Quants snapshot, publication, and production read-back. Evidence: [Actions run #177](https://github.com/masacgt/Diview/actions/runs/39385915371) uploaded artifact `12250424117` with 4,442 unique codes from `/equities/master` (response date 2026-07-28; SHA-256 `8bcde9ed5025eaa81631886172e4a733e8be204afe4fee2aa4a2b919511b35d3`; includes 2060). Generation `2026-10-20` published with 4,327 successes, 115 failures, and catalog size 4,356; HTTP 429 count was zero. The 9,372 yfinance calls comprised 58 valuation-map `AttributeError`s, 52 HTTP 404 failures, and 5 `RuntimeError`s. Sync job duration was 67m05s plus 28s calendar job (67m33s combined); R2 active wall was 3,885.386s (2,046.139s R2-only; 1,839.247s overlapping yfinance), with 80,165 `get_body`, 86,058 `get_object`, 14,913 `list_objects_v2`, 69,505 `put_object`, and 210 deletes. 2060.T production now shows close ¥1,448 for 2026-10-20 and processing `ok`; its stored `Ticker.calendar` remains 2027-03-30 fetched 2026-10-17, while `/api/v1/dividend-months` labels the same date `estimated_from_dividend_history`. This was a manual dispatch with the closed-market override and is not a normal-schedule monthly-budget sample; keep the 360-minute timeout and 2,000-minute conclusion open.
- [x] Reconcile all ten event-method mismatches individually from the retained per-symbol benchmark report. Evidence: Actions run #49 artifact `benchmark-action-500-24.json` records all event rows: eight same-date split dividend adjustments (1904.T, 3106.T, 4471.T, 4762.T, 6368.T, 7433.T, 7608.T, 8766.T), one duplicate ETF distribution date (1495.T), and one repaired ex-date change (2098.T). The repaired response remains authoritative; no R2 data was changed.
- [x] Preserve issuer/IRBANK seed data while adding newly fetched yfinance records with separate provenance. Evidence: `sync_v2.py` and `migrate_legacy_financials.py` keep seed and yfinance values side by side, Worker exposes seed rows separately while retaining yfinance-preferred compatibility rows, all 59 Python yfinance tests and all 22 Worker route tests pass.
- [x] Verify source-separated seed and yfinance values after an R2 financial update. Evidence: production API read-back after Actions run #155 shows seed assets and yfinance statement values separately for 4502 and 7203; this also exposed the mixed-unit ratio defect tracked above.

- [x] Verify the first scheduled full-universe sync after the R2-aware calendar gate. Evidence: run #159 (`39304823002`) ran as `schedule`, used `ALLOW_CLOSED_MARKET=false`, passed the XTKS gate for 2026-10-20 JST, selected completed session `2026-10-19`, and scanned all 4,442 J-Quants codes before publishing generation `2026-10-19` (4,335 success, 107 failed).
- [ ] Verify the R2-aware closed-day recovery path on a scheduled run: prove it catches up an unpublished completed session or skips an already-published session without starting the collector. Run #159 covered an open market session; the closed-day branch still lacks a scheduled-run observation. Measurement for #159 is recorded in [the measurement sheet](yfinance-normal-sync-measurement-20261019.md), but it is not a fresh-session monthly sample.

- [x] Complete and validate the app-wide one-decimal stock-price/dividend display, including the forced `.0` display for securities with a 0.1-yen tick. Evidence: `formatStockPriceDisplay` is applied to search, holdings, stock detail, watchlist, roadmap, and notification prices; the current JDK 17 APK displayed eligible ETF 1615 at ¥723.0 in the emulator search modal. Refresh the bundled schedule from JPX's official liquidity-based table before 2027-03-01.
- [x] Verify saved override persistence/reset-to-R2 behavior, split/reverse-split labels, and the `0.00%` missing-yield display. Evidence: the emulator showed all eight editor rows without scrolling, saved an edit and showed `ユーザー編集`, then reset and reopened the initial value; the published 7203 split event appeared in the editor. 1326 displayed `未取得` DPS with `0.00%`; 8783 displayed ¥0 and `0.00%（無配）`.
- [x] Verify no-dividend and unknown-yield display states with the published R2 catalog. Evidence: emulator search and detail checks distinguished 1326 (`unknown`, yield `0.00%`, DPS `未取得`) from 8783 (`no_dividend`, yield `0.00%（無配）`, DPS ¥0).
- [x] Verify v2 Worker routes and Android behavior against generation 2026-10-16. Evidence: production read-back returned 4,341 catalog items; search, live quotes, security detail, dividend-months, financial-data, split-history, monthly-price-history, and analysis routes responded. Toyota's eight annual distribution values matched the detail graph; monthly close values were correct, with date-label normalization tracked separately above. Worker tests pass 13/13 and Android unit tests pass 129/129.
- [x] Audit representative split and reverse-split cases. Evidence: the additional 10 split and 10 reverse-split samples brought visual stock-detail graph checks to 15+15; all 20 sampled pre-event monthly prices matched R2 yfinance closes. Issuer-side numeric overrides from that audit were removed from the local canonical JSON and D1 input; remote legacy R2 read-back remains unverified.
- [x] Run the read-only split-transition audit across the active securities with a split or reverse split in the latest eight years. Evidence: [Actions run #176](https://github.com/masacgt/Diview/actions/runs/39383687304) audited 1,068 securities and 1,270 split events from an active universe of 4,435; all 7,345 rows were emitted, with zero technical failures and no R2 writes. Artifact #12246248712 (SHA-256 `3bc71bfa1900f474297dcbe634007c4007151aab24e55525ab5d2ba1bf80fc6f`) contains the per-security, per-year, and summary reports.
- [x] Triage every numerically comparable fiscal-year mismatch without changing yfinance data or issuer values. Evidence: in run #176, 7,336 rows had status `compared`: 6,206 were within ¥0.05/share and 1,130 exceeded tolerance across 531 securities; 54 securities differed in every compared year and 477 had both matches and mismatches. Among mismatches, yfinance was lower in 926 rows and higher in 204; 106 rows across 40 securities had positive issuer DPS and zero yfinance event total. Nine securities had issuer DPS at or above ¥1,000 in a compared year, one had a yfinance event total at or above ¥1,000, and 94 mismatch rows belonged to FY2026. Nine rows for 8303 were marked `yfinance_failed` and are excluded from these comparable counts, although `summary.json` includes them in its headline 6,207/1,138 totals. These are review candidates, not source corrections or proof of full-market accuracy; the artifact is linked from [Actions run #176](https://github.com/masacgt/Diview/actions/runs/39383687304).
- [x] Fix the comparison basis for split-transition dividends without hard-coded issuer values. Evidence: the app sums repaired yfinance events by calendar year and, per the accepted specification, treats those amounts as already split-adjusted without applying ratios again. The prior comparisons mixed these calendar-year event totals with company fiscal-year DPS; issuer materials explicitly document split-adjusted fiscal figures and split dates (Feed-One [dividend history](https://www.feed-one.co.jp/ir/investors/shareholder_dividend.html), Jimoto [10:1 consolidation](https://www.jimoto-hd.co.jp/cms/view.php?no=20200930114343), Kawasaki Geological [FY2017 dividend schedule and planned consolidation](https://www2.jpx.co.jp/disc/46730/140120171220439310.pdf)). The app continues to label the series as calendar-year history and does not substitute issuer totals. This resolves period-basis comparisons, but does not establish full-market accuracy of every Yahoo event amount; the separate full-market audit remains open.
- [x] Publish repaired yfinance dividend/split history and verify the historical chart API. Evidence: production Worker generation `2026-10-16` serves 7203's yfinance calendar-year rows and 2021 split event; 2018–2025 values match the emulator graph, 2060's reverse-split event is available, and Worker tests confirm the v2-only history path. Full-market share-basis accuracy remains the separate open item above.
- [x] Keep the static J-Quants snapshot unchanged until a newer date range is requested and the distribution permission is confirmed. Evidence: the Android market-data flow uses yfinance v2; the static J-Quants snapshot is a separate historical debug endpoint, and this task did not request a newer snapshot range.
- [x] Fix the v2 dividend-month route for `.T` symbols and split-history status/data duplication. Evidence: Worker tests pass 13/13; production `dividend-months?symbols=7203.T` returns months 3 and 9, and the 7203 split route returns one 2021-09-29 5:1 event with `history_status: available`.
- [x] Verify the Stock Detail `決算期別DPS（参考）` card for migrated issuer-dividend data, including 8303 FY2019 at ¥10, and confirm the existing calendar-year chart remains separate. Evidence: on the emulator, opening 8303 from search displayed FY2019 ¥10 in the issuer fiscal-year card while the separate calendar-year chart showed 2019 ¥0; no holding or watchlist entry was added.
- [x] Audit 8303 issuer-dividend per-share values across its documented consolidation and split history. Evidence: SBI Shinsei's official filings report ordinary DPS of ¥40,000,000 for FY2024 and FY2025 on the actual pre-2025-07-27 split basis, and ¥42 for FY2026 after the 1-for-14,000,000 split; the comparable FY2024/25 amount is ¥40,000,000 ÷ 14,000,000 = ¥2.857142857 per current share. FY2023's reported ¥12 predates the 20,000,000-for-1 consolidation and 6-for-1 split. `yfinance==1.7.0` `Ticker.splits` omits the 2024 and 2025 actions, and `Ticker.dividends` has no FY2024/25 event rows, so it cannot independently normalize this history. The difference is both share-basis mismatch and a subsequent issuer-reported dividend increase; keep source amounts intact and explain their reported basis rather than replacing them with hand-entered values. Sources: SBI Shinsei [2025 IPO filing](https://corp.sbishinseibank.co.jp/ja/news/news/20251113c.html), [FY2026 earnings release](https://corp.sbishinseibank.co.jp/ja/news/news/auto_20260416505343/pdfFile.pdf), and [FY2026 dividend resolution](https://corp.sbishinseibank.co.jp/ja/news/news/auto_20260513528625/pdfFile.pdf); yfinance probe performed 2026-10-20.
- [x] Show the 8303 historical dividend share basis as unverified while preserving raw values. Evidence: production Worker version `80d7ad93-e704-46a4-a84f-dc166dddb0c4` returns `unverified_incomplete_corporate_action_history` for both event and issuer histories; a current production API read confirms FY2024–FY2025 raw values remain ¥40,000,000 with that status. PR #25 merged as `0e8a8e8`; Worker tests passed 31/31, JDK 17 Android unit tests and build passed, and the warning was verified on the emulator and SCG01.
- [ ] Restore green Android API 26 and API 35 release-readiness checks. Evidence: PR Actions run #55 (`39356043086`) and merge push run #56 (`39368873696`) both failed only at `ReleaseReadinessTest.watchlistSortOrderSurvivesLeavingAndReturningToTab` with `ComposeTimeoutException` after 30 seconds; this CI follow-up is separate from the verified 8303 share-basis behavior.
- [x] Verify manual sync #166 repaired duplicate 8303.T and 2060.T 2026 dividend events. Evidence: production returned one 8303 event each at ¥42 and ¥10 (TTM ¥52), and one 2060 event each at ¥24.5 and ¥26 (TTM ¥50.5); this replaced duplicated sums of ¥468 and ¥505.
- [x] Retrieve exact yfinance dividend histories for all seven active five-character class-share codes without substituting parent common-share values. Evidence: pinned yfinance 1.7.0 returned dated history for 25935.T, 50765.T, 75505.T, 92015.T, 92025.T, 94345.T, and 94346.T; 92015 returned no event rows.
- [x] Publish and read back exact five-character histories through the production Worker. Evidence: the 2026-10-20 `dividend-months?event_year=2026` response returned exact-code events for six securities and `history_status: empty` with zero events for 92015.T; values included 25935 ¥30, 50765 ¥65, 75505 ¥99.72/¥100.27, 92025 ¥52.73/¥87.50, 94345 ¥50/¥50, and 94346 ¥128/¥128.

## 2026-10-20 follow-up rollout

- [x] Complete production rollout of follow-up consistency fixes. Evidence: integrated Python 95, Worker/D1 53, repair planner 1, Android JVM 155 and JDK 17 build passed; live D1 has 49,592 annual rows with zero period mismatches; Worker deployment and R2 run #192 read-back succeeded, and both devices passed startup checks. See `review2-rollout-20261020.md` for scope and remaining UI checks.
- [ ] Verify revised financial/cache behavior visually on device, including same-year multiple fiscal periods and split-adjusted history.

## 2026-10-25 analysis explanation length

- [x] Keep position explanations to 60–90 characters and about two sentences in the health, buy-signal, annual-dividend-change, and concentration-risk tabs. Evidence: generator tests cover 315 band/position combinations; Android unit tests, Debug build, and release visual regression passed, and physical-device hierarchy checks confirmed two-sentence comments within range in all four tabs.
- [x] Select evaluation-position wording by financially meaningful sign states without changing the 45-position evaluation code. Evidence: `StockAnalysisNarrative.kt` distinguishes negative/near-zero/positive operating margin and FCF plus non-positive PER/PBR/payout cases; unit tests cover boundary values, text length, two sentences, and unchanged combination codes. JDK 17 unit tests and Debug build passed; the installed APK hashes match on the emulator and SCG01, and the physical detail screen displays both positive-state explanations.
- [x] Expand detailed comments in all four Stock Detail analysis tabs to about 200–250 characters while preserving evaluation scores and position codes. Evidence: tests cover all 315 model band/position cases and custom state variants; JDK 17 unit tests and Debug build pass, the same APK hash is installed on SCG01 and emulator, and device UI confirms 200–250-character comments across the four tabs.
- [ ] Investigate the Stock Detail payout-ratio source/unit discrepancy for 8058. Evidence: after the comment update, SCG01 showed forecast payout as unavailable while the detailed metric used 4593.00%; prose generation did not change this input or its score.

## 2026-10-26 リモートブランチ整理

- GitHubで開いているPRがないことを確認し、mainとの差分と作業ツリーを照合した。mainに同等の変更があるか、より新しい実装・資料に置き換わっていた10本のリモート参照を削除した: `asset-action-width`, `financial-index-shards-20261026`, `fix-8984-forecast-card-20261026`, `stock-health-payout-fallback-20261026`, `yfinance-legacy-event-audit-main-20261025`, `yfinance-legacy-event-transfer-20261025`, `asset-management-copy`, `fix-yfinance-main-ci-20261025`, `yfinance-legacy-event-audit-20261025`, `portfolio-import-main-merge-20261018`. `fix-yfinance-main-ci` の混在元財務比率ガードは現行mainの移行処理と逆向きで、同ブランチの日本語APK調査仕様とポートフォリオ資料もmain側により新しい版が存在するため、古い差分は再適用していない。
- 5本は保留: `6721-production-evidence-20261025`, `integrate-selected-branches-20261025`, `irbank-rollout-evidence-20261025`, `irbank-unit-normalization-20261025`, `yfinance-financial-coverage-20261018`. 前4本の作業ツリーには各13件のステージ済みCSVがあり、最後のブランチは共有ルートに未コミット変更とCSVサンプルがある。これらの作業ツリー・CSVは変更、統合、削除していない。
- yfinance同期測定記録 `e3dc666b` はmainに含まれることを確認した。今回追加のコード変更はなく、ブランチ削除後のリモート確認では未統合扱いの5本が残っている。

- [x] Integrate pending category labels and contextual financial-history comments while retaining current main labels and unrelated dirty worktrees. Evidence: JDK 17 build, 191 Android unit tests, 70 Worker tests, five emulator UI tests, and matching physical/emulator APK hashes; see `ai_docs/branch-integration-20261026.md`.

- [x] Separate the four Stock Detail evaluation panels into numeric band/boundary/position/distance comments, retaining dividend-continuity explanations in the dedicated card. Evidence: `StockEvaluationPositionComment` uses real cutoffs and percentage-point gaps, with explicit open-ended/equality handling; all 198 unit tests and the JDK 17 build pass. Physical RFCNC0E4H8J and emulator-5560 show all four numeric panels and the unchanged continuity explanation; installed APK SHA-256 matches the build (`1b8b5eaf1a5786ac4df6e2f327e328c749df93283639c32a18a16c59b8b25cc9`), with no fresh AndroidRuntime fatal entries.

- [x] Expand dividend-indicator explanations within the former per-input character ceiling, retaining numeric evaluation panels. Implementation complete in `DividendExplanationVariants`; all 206 unit tests and the JDK 17 build pass. Emulator-5560 confirms expanded payout/trend explanations and unchanged numeric panels; final installed APK SHA-256 matches the build (`f468d06cb1d5c0ee0fdccc9aad92208adc251e69bdcb097e6d1e11c2ac49cdc8`), with no fresh AndroidRuntime fatal entries. Physical verification is now complete on the successor position-variants build below; the dividend-explanation generator is unchanged.
- [x] Verify the previously disconnected physical device using the successor build containing the explanation variants. Evidence: RFCNC0E4H8J and emulator-5560 both display the expanded dividend explanation; installed APK hashes match the position-variants build below, with no fresh AndroidRuntime fatal entries.

- [x] Expand the four evaluation panels to indicator-specific 80–160-character position explanations without repeating band ranges. Evidence: `StockEvaluationPositionComment` covers thirteen indicators, nine interior positions, equality, just-crossed/open-ended distances, negative-value semantics and zero-domain minima. All 208 unit tests and the JDK 17 debug build pass. Physical RFCNC0E4H8J and emulator-5560 both show all four panels and the retained dividend explanation; installed base.apk SHA-256 matches the build (`be2daca93af39ed05b6b2b3dc568d1432459618396d6fbad45e734161c0e593b`), with no fresh AndroidRuntime fatal entries.

- [x] Apply yomiyasu to the four evaluation panels while preserving 80–160 characters, numeric slots and rating selection. Evidence: lint/diff/material/final-hash checks, literal-only code comparison, all 208 unit tests and JDK 17 build pass. Physical RFCNC0E4H8J and emulator-5560 both show the four revised panels with matching APK hashes before and after the checks; see `ai_docs/yomiyasu-evaluation-comments-20261027.md`.

## 2026-10-27 Startup acquisition and Asset Management cache

- [x] Gate automatic startup refresh by JST date and 12-hour interval, restrict quote requests to held/watchlisted codes, remove Asset Management entry refresh and speculative history preloads, and reuse parsed preference JSON. Evidence: `DividendViewModel.kt`, `AssetManagementScreen.kt`, `CloudflareMarketRepository.kt`, and `ParsedPreferenceCache.kt`; JDK 17 `testDebugUnitTest` (215 tests), debug/test APK builds, and 26 isolated emulator acquisition/UI/failure tests passed; debug APK installed on `RFCNC0E4H8J` and `emulator-5560`, with both installed base APK hashes matching the build (`b89ece18...cb15951`). Real-device display acceptance remains open below.
- [ ] Verify startup/revisit behavior on real production data and measure Asset Management transition latency after the change.

## 2026-10-27 Asset card acquisition yield

- [x] Replace the card's lower yield caption with `取得利回り5.19％` using each holding's `liveYoc`; preserve the upper yield and existing text styles. Evidence: `AssetManagementScreen.kt` and a formatted string resource; JDK 17 `assembleDebug` and existing `PortfolioMathTest` pass. Installed APK SHA-256 `3956ee9288afcdcf7987df1443332b541126a85af6fce98976f434425d964c4b` matches on SCG01 and emulator-5560; both screenshots show the caption fitting, and emulator Takeda displays exactly `取得利回り5.19％` (physical-device holdings differ).

### Startup refresh branch integration review (2026-10-27)

- Reviewed 97 local/remote refs not ancestral to `origin/main` and the registered worktrees. Integrate `1448897d` from `codex/startup-fetch-policy-20261027` onto `a1b7fb9f`; its effective diff is nine intended Android/test/note files, without CSVs, credentials, or generated APKs. Thirteen refs are patch-equivalent; remaining historical candidates contain the old CSV-bearing `67a63751` lineage or code/notes already represented or superseded on current main (including category labels, financial metric fallbacks, evaluation comments, yfinance tests, and the expanded legacy audit). Preserve those refs and all dirty worktrees.
- Integration validation: application source is identical to the previously tested `1448897d`; explicit-JDK-17 `testDebugUnitTest` / `assembleDebug` recheck succeeded with tasks up-to-date. Existing evidence remains 215 JVM tests and 26 isolated emulator tests passing, plus installation and matching base-APK SHA-256 on the real device and emulator. Production-data transition timing and real-device visual acceptance remain open in the follow-up above.

### Buy-signal yield main integration (2026-10-27)

- [x] Integrate the forecast-based buy-signal fix onto latest main including the acquisition-yield caption. Evidence: `c90b58f2` on main base `594dd116`; 216 JDK 17 unit tests and both device UI regressions pass, and installed hashes match `6c165cde...a6056c1`. Reviewed 99 non-ancestor refs and 103 worktrees; historical/CSV-bearing and dirty work is preserved. See `stock-signal-yield-20261027.md`.

- [x] 銘柄別の平均配当利回り除外設定。根拠: JDK 17で単体テスト222件とビルドが成功し、SCG01とエミュレータで各7件の画面操作・設定保持・DVMおよび自動バックアップ復元テストが成功した（検証データを使用）。
- [ ] 銘柄別の平均配当利回り除外設定について、Google Driveの画面操作を含むバックアップ・復元を実データに影響しない環境で確認する。

- [x] 平均配当利回り除外設定を景気特性と同じカード枠に変更し、タイトルに利回りアイコンを追加。根拠: JDK 17のビルド・単体テストと、SCG01・エミュレータでの画面表示およびスイッチ操作を確認。

- [x] R2アクセスをファイル種別・操作別に集計し、同一クライアントで書込みを観測していない再読込みを診断する。`test_sync_v2.py` とyfinance R2の139テストが成功。
- [x] 直近分割履歴は存在する年だけ読む。R2の無作為100銘柄・読取専用比較で結果100/100一致、300 GETから100 LIST＋17 GETに減少。
- [ ] 上記変更を本番へ反映後、通常営業日同期で種別ごとの実測・全体処理時間・データ整合性を確認する。再読込み件数は他クライアントの更新を考慮し、無条件に削減可能と見なさない。

- [x] 1,000銘柄で直近分割履歴の読取専用比較と種類別読取を計測。2026-11-01、旧3,000 GET・20.472秒、新1,000 LIST＋162 GET・8.049秒、取得結果1,000件一致・エラー0。通常同期全体や書込みの実測は未実施。詳細は `scripts/yfinance_r2/README.md`。

- [ ] 旧yfinanceイベントとv2の正常な重なりによる全期間再取得を削減する。試作は7239・6258・1723の実yfinance取得と本番R2スナップショットの書込み隔離比較で各銘柄のmax取得が1回から0回、主要表示値と書込み対象のイベント行は一致したが、同日異値の解決状態の持続・例外時はテスト済み、通常営業日での本番検証は未完了。R2読書き回数は変化なし。詳細はREADME参照。

- [x] 旧・v2イベントの同日異値について修復済みyfinance履歴での照合結果を保存し、入力変更時に再照合する。143テスト成功、実yfinanceと書込み隔離したR2スナップショットで確認済み状態の再利用・値変更後の通信失敗・対象日欠落を検証。
- [ ] イベント照合変更を本番反映後、通常営業日で取得件数・R2読み書き・表示値を検証する。

- [x] 2026-11-01に本番全銘柄手動同期を実行し、R2公開後のgeneration・catalog・universeの読み戻しを照合。Actions run #296 は4,445コードを走査して4,398成功・47失敗、カタログ4,364件、HTTP 429なしで世代 `2026-10-30-dd035617a06641eb9b93ffde9c7483a0` を公開した。同期jobは46分22秒で、直近通常run #290の81分17秒より34分55秒短い。ただし同一セッション日の再実行で成功済み項目を再利用しているため、日次短縮の因果・月間2,000分達成の証拠にはしない。詳細と次の確認事項は `r2-yfinance-target-spec.md` と `scripts/yfinance_r2/README.md`。
- [ ] 複数の異なるXTKS営業日の通常schedule runで、全件処理・R2読み書き・yfinance呼出し・失敗・HTTP 429・runner時間を比較し、月23営業日換算で2,000分以内か判定する。手動run #296は同日再利用を含むため標本から除外し、Actions timeout 360分は通常実測が揃うまで維持する。

## 2026-11-03 権利日計算のR2履歴読取削減

- [x] 権利日推定・表示に必要な配当イベント年だけを読むよう変更。`calculate_rights_dates.py` は当年から5年前までの6暦年（推定境界年を含む）を対象にし、R2に保持する10年分の履歴は変更しない。`test_calculate_rights_dates.py` の8テスト、`py_compile`、`git diff --check` が成功。production R2 read counts and scheduled runtime are not yet measured.
- [ ] 権利日workflowへR2操作別カウンターを追加し、変更後の週次Actionsで出力件数・内容、R2 GET回数、job時間を前回実績と比較する。現状のログにはR2操作回数がない。実測前にrunner分の削減量や月2,000分達成を確定しない。

## 2026-11-03 IRBANK / J-Quants source audit

- [x] Check the next scheduled workflow event after manual run #367. Evidence: scheduled Actions run #377 (`2026-11-03` JST) completed `market-calendar` successfully and skipped `v2-daily-sync`; it ran on the Japanese market holiday and is not a full-universe sync or a monthly runtime sample.
- [x] IRBANKとJ-Quantsの全一覧を読み取り専用で正規化比較し、件数、重複、共通・片側のみの全コード、ProdCat、raw/display code変換、SHA-256を保存。2回目の取得はIRBANK 11要求/10秒、J-Quants 2秒。確定した監査JSONに全205差分の行別理由と根拠URLを収録。
- [x] 公式日付・商品範囲と差分を個別照合し、59件を分類した。内訳はJ-Quants有効日後の上場廃止28、IRBANKに残る廃止済み2、J-Quants有効日後の新規上場15、IRBANK取得時点で未上場6、種類株式7、名証ETF1。残る146件は未分類と明記し、差分の不在のみから上場状態を推定しない。根拠とコード一覧: `ai_docs/audits/irbank-jquants-universe-diff-20261103.json`。
- [ ] 残る146差分について、同じ有効日の公式一覧または個別の公式上場資料が得られた場合に理由を更新する。2026-11-03時点のJPX月末一覧は9月末版で、10月末版は11月5日以降の公開予定。東証上場銘柄一覧ページはJPX Data Portalの全件CSVも案内しているが、そのファイルを匿名で取得・照合できるかは未確認。
- [x] 既存のJ-Quants ETF/REITカテゴリ保持と、新規ETFのJPX補完を回帰テストで確認する。変更後のyfinance同期テスト全160件が成功。新規IRBANKコードで既存J-Quants分類もJPX ETF補完もない場合の分類ソースは未対応として仕様書に記録。
- [ ] JPX公式REIT一覧補完の通常schedule運用を確認する。実装はPR #64（全166件の同期テスト成功）でmainへ統合済み。手動run #367後のWorker読み戻しで401A・8951・8952のREIT/category 013を確認したが、通常scheduleでの補完更新と読み戻しは未確認。ライブJPX表は58件で、将来上場の666Aは基準日より後のため対象外。

## 2026-11-03 IRBANK週次snapshotから本番R2公開までの手順

- [x] main統合後にIRBANK snapshotを更新し、日次yfinance手動同期から本番R2/Worker読み戻しまで一巡確認。PR #63/#64を含むmain `80b631c9`、手動IRBANK refresh run #14（4,573 IRBANK + 3 ETF = 4,576）、手動sync run #367（全4,576走査、generation `2026-11-02-15aa0f2576d649b2aa0da838f9c68544`、カタログ4,368）の証跡を仕様書とREADMEに追記した。R2直接GETでpublication/catalog/universe/immutable catalogのgeneration一致、source code hash一致、4,576 active＋46 inactive、2060.T価格日とcalendar source/fetched_atを確認。新規IRBANK銘柄634Aもyfinance由来価格¥2,168（11/2）と英語名fallbackをR2/Workerで確認。同期は公開後156銘柄失敗でfailure。main相当worktreeの同期テスト166/166成功、`git diff --check`成功。いずれも手動runで、通常scheduleの証明ではない。
- [ ] 次の通常XTKS scheduleでカレンダーゲート、IRBANK snapshot hash/件数、全active銘柄走査、公開generation、Worker read-backを確認する。run #367は同一市場日の手動再処理なので通常運用の成功・速度標本から除外する。
- [x] run #367の失敗を全ログから分類し、失敗データの保持をR2で照合する。最上位失敗156件は全て一意な `SKIP same-session no-data retry` のHTTP 404で、run #314の156コード全件と一致する。既存 `ai_docs/audits/yfinance-run314-failure-classification-20261102.json` のJPX照合では2件が同期市場日までの上場廃止、154件はJPX廃止一覧に未掲載（現役・Yahoo対応の証明ではない）。別枠の当日終値欠損234件（no-close 197、YFPricesMissingError 37）は最上位失敗と重複なし。R2は例として1438.T・7509.Tの価格日と前回終値（2026-10-30）を保持し、当日欠損を11-02価格に偽装していない。
- [ ] 複数の異なる通常schedule日と同月のIRBANK・権利日scheduled jobのactive job実時間を合算し、23営業日換算で月2,000 runner分以内か判定する。run #367の45m43s×23は参考算術に限り、手動同日再処理なので達成根拠にしない。timeout 360分は通常実測が揃うまで維持する。

## 2026-11-03 issuer share-action basis correction

- [x] IRBANK disclosure-section extraction, Yahoo raw dividend normalization, Worker API integration, and 8303 production read-back through the generic security-code path. Evidence: Worker `2a1cf07e-9c75-4938-9ce9-a57c4e638bfa` serves issuer-confirmed actions on 2017-10-01, 2023-10-02, 2024-03-15, and 2025-07-27; R2 read-back SHA-256 is `b19f3240d75726adbbe3f8dff2fdff6cbf533842a451e3fa36ed439b410191` for actions and `71020a16efc59308fe43d3364e4a16a58d7413394ced9bfca98ced60f53d750b` for normalized dividends; FY2022/23 Yahoo ¥12 events and issuer FY2021–FY2025 DPS resolve to current-basis ¥2.85714286 while raw issuer amounts remain intact, and missing later event years produce `insufficient_history`. Worker tests 84/84, Python tests 4/4, Android unit tests and JDK 17 debug build pass; identical APK SHA-256 `208F0804B33CB1AA1E0358B537A6B133089F86F59AE80B1F716C52F8FF5FB4E2` was installed on emulator and SCG01, both showing FY2022–FY2025 at ¥2.9 and FY2026 at ¥42. Source commits: `263a5be6`, `405d76d5`, `70e34df0`.

## D1・R2・端末データ所有権移行

- [x] 2026-11-03 21:54 UTCに本番R2の現行カタログと空白月候補を読み取り確認した。両方generation `2026-11-02-daa434e899b0493a9fdf346067419041`、4,390/3,203件で重複コード0、候補の参照generation欠落0・不一致0。分析current/source/run/retentionと株式・ウォッチリスト定義の既知キーはNot Found。確認したキーに限る結果で、全prefix一覧ではない。Worker API読み戻しとD1監査は未実施。
- [x] 2026-11-03 21:56 UTCにWorker 114/114、移行ツール37/37、JDK 17 Android JVM 266/266、エミュレータ計装35/35を確認した。`AssetDataRefreshTest` は通常更新端末fixtureの契約版を1に設定後20/20成功。`NotificationDeliveryTest` 13件と`ReleaseNotificationPlatformTest` 2件も成功し、生成APKと`emulator-5556`の`base.apk` SHA-256が `ed341dab1c57f8c8a737fe5ec65e44a52e97134c6b6f0f9a8afa3d92e7f620bf` で一致した。
- [ ] 分離worktreeのR2移行差分を最終レビューする。ローカル検証はWorker 114/114、移行ツール37/37、JDK 17 Android JVM 267/267、関連instrumentation 35/35。JDK 17でDebug/AndroidTest APKをbuildし、emulator-5556のbase.apkと生成APKのSHA-256 b0547594844800650b272f2517e176686509fd6d577966c530403cfa729bd156が一致。2027年JPX休業日と価格鮮度境界もテストした。本番D1/R2監査、Actions実行、Queue backlog、物理端末確認は未完了。
- [x] Worker/API/Cronから市場データ目的のD1読書きを除去し、残存アクセスを棚卸しした。根拠: 現行 `src/index.js` のD1参照はDO初回の旧レート制限カウンターSELECT、問い合わせ/メール処理・整理に限られ、healthはbinding有無のみ返す。Worker 114/114件成功。GitHub ActionsのD1呼出しは新しい手動migration workflowに限定した。
- [ ] Durable Objectsの共有レート制限で現行キー・期間・上限・応答と有効カウンター引継ぎを確認する。Worker 113件で公開bucket key、300回/60秒、429/Retry-After、初回D1読取のみの引継ぎ、直列化、窓境界、DO再起動後の期限切れを確認。一時D1書込みフェンスはSQLite/Wrangler local D1で確認済み。remote schema、実トラフィック、切替は未確認。
- [x] Android端末管理のウォッチリスト・通知状態・価格通知キャッシュを実装し検証した。JDK 17 JVM 267/267、AssetDataRefreshTest 20/20、NotificationDeliveryTest 13/13、ReleaseNotificationPlatformTest 2/2が成功。NotificationPolicyTestでJPX公式2027休業日とGW後の3・4営業日境界を確認し、Debug/AndroidTest APK build、emulator-5556インストール、SHA-256一致も確認した。実機確認は最終承認後。
- [ ] R2の分析履歴・実行状態・定義・監査・空白月候補のキー、形式、世代、保持、冪等性、API読み戻しを確認する。2026-11-03 21:54 UTCの直接読み戻しでカタログ4,390件・候補3,203件がgeneration `2026-11-02-daa434e899b0493a9fdf346067419041` に一致し、候補参照generation欠落・不一致・重複コード0。確認した分析current/source/run/retentionと株式・ウォッチリスト定義キーはNot Found。全prefix一覧、D1監査、不足分補完、Worker API読み戻しは未実施。
- [x] 手動 `workflow_dispatch` 移行WorkflowとWorker CIのローカル定義をレビューした。根拠: CIは対象Workerパスのpush/PRでWorker・D1・R2・DOテストと移行ツールテストを実行し、デプロイしない。移行workflowはmain限定、dry-run既定、専用Environment・読み取り予算・再開run provenanceを要求する。YAML構造検査とローカルテストが成功。2026-11-04のGitHub API再確認でmainの両ファイルは404、Actions未実行。今回の再確認でもmain SHAは`b3f3a7b75bc54d30ec468fc085922791f99e340b`で、workflow一覧に両ファイルはなく、既存yfinance/IRBANK更新Workflowは残っている。Environment作成・設定とQueue再確認は未完了。
- [ ] D1→R2移行、Worker本番デプロイ、GitHub Actions本番実行、実機インストールは承認前に行わない。移行前後使用量、復旧・Cron競合策、残存D1経路を整理する。
- [x] 2026-11-03 23:19 UTCに本番R2全件一覧を読み取り確認した。根拠: 231,237 objects。IRBANK v1 manifestの3,965参照キーはすべて一覧に存在し、overview/dividends各3,963件。yfinance catalog/candidatesは同一generationで4,390/3,203件、重複0、候補generation不一致0。analysis/definitions/operations/audit prefixは0件と判明したため、D1の分析・定義・完了ログの移行は引き続き必要。本番D1 SELECT・R2書き込みなし。
- [x] 2026-11-03 23:30 UTCにローカル検証を再実行した。根拠: Worker 114/114、移行ツール37/37、JDK 17.0.20.1 Android JVM 267/267、emulator-5556 instrumentation 35/35、APKとbase.apkのSHA-256一致。UTC深夜に日付跨ぎした保持テストfixtureを正午固定にし、Worker全件が成功した。
- [x] 2026-11-04 UTCリセット後に本番D1の小予算schema・市場データ監査を実施した。根拠: schema 23テーブル・2つのsupport-feedback email trigger、securities累積3,960行、financial_periods 49,592行、配当期間表2つは空。R2書き込みなし。分析currentは248件dry-run完了、analysis-historyはcursor 1,500で未完了。
- [x] ローカル銘柄CSVが現在の本番D1監査を代替しないことを確認した。根拠: CSVは2026-11-03 16:46 UTC更新・3,963件、本番市場監査は累積3,960行のため、時点・件数が一致せず差異分類には転用しない。
- [ ] `securities` のR2カタログにない65コードと552名称差異を正規ソースに照合する。財務の17期間欠落と4値差異も個別調査し、根拠を確認するまでR2へ補完しない。
- [x] 2026-11-04 04:11 UTCまでのDashboard日次範囲と読み取り専用D1監査を確認した。根拠: Dashboardはカード約113k・グラフ約126.18kで不一致、Wranglerの7,004,607行はローリング24時間値。D1監査の`meta.rows_read`合計4,276、書き込み0。正確な日次残枠は未確定。
- [ ] Dashboardの日次Rows Read表示のカード・グラフ差をCloudflare側で解消し、正確な残枠を記録する。矛盾が解消するまでは追加の大規模D1行監査を実行しない。
- [ ] analysis-history全件dry-runを再開する。checkpointは `C:\Codex\r2-d1-ownership-audit-20261104\analysis-history-dry-checkpoint.json` にあり、cursor 1,500・累計1,500行・dry-run・`complete=false`。続くWrangler呼出しは終了コード1・空のstderrで結果不明のため自動再試行していない。正確なD1日次残枠を確認後、同じcheckpointから予算内で再開する。
- [ ] GitHub main上のCI/移行Workflowは2026-11-03 23:11 UTC時点で404。`d1-r2-migration` Environment保護の現在設定は未確認。workflowを公開・実行せず、最終切替前に管理者設定とQueue backlog/稼働Versionを確認する。


- [ ] 2026-11-03 22:16 UTCのQueue登録確認では通常キューのproducer/consumer各1件（worker:diview-irbank）、DLQはproducer 0件・consumer 1件（同Worker）。origin/mainのsendBatch 3箇所に現在のソース内の呼び出し元はなく、ACK handlerは既にorigin/mainにある。作業差分は送信helperを削除するがQueue bindingは残す。稼働Version 4981…のソース対応とbacklog metricsが未確認なので、QueueとD1のrun/batch記録を保持する。

## 2026-11-04 継続検証

- [x] 本番R2のyfinanceカタログ、空白月候補、7203プロフィール/現在値を読み取り確認した。根拠: generation `2026-11-02-daa434e899b0493a9fdf346067419041`、カタログ4,390件・候補3,203件、重複・候補generation不一致・カタログ外候補0。7203は価格¥3,051 JPY、価格日2026-11-02。Worker APIとの内容比較は未実施。
- [x] R2経由の公開GETがDO共有制限を迂回していた経路を修正した。根拠: Worker dispatcherから `d1IndependentRead` 例外を除き、12のR2 APIルートでDOの300回/60秒・429/Retry-After・D1非呼出しを検証。Workerテスト115/115件成功。本番未デプロイ。
- [x] AndroidをJDK 17.0.20.1で強制再ビルド・再テストした。根拠: JVM 267/267件、Debug/AndroidTest APK build、emulator-5556 instrumentation 136/136件。生成APKとインストール済み `base.apk` のSHA-256は `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` で一致。物理SCG01には触れていない。
- [ ] DashboardのD1残枠を確認後、`C:\Codex\r2-d1-ownership-audit-20261104\analysis-history-dry-checkpoint.json` のcursor 1,500からanalysis-historyを再開し、scheduled-runs、definitions、definition-audit、analysis-sourceのdry-runとR2読み戻しを行う。市場差異は正規ソースに基づいて分類する。
- [ ] 市場監査の65コード欠落・552名称差異・17期間欠落・4値差異を正規ソースで分類し、必要な場合だけ既存R2のcanonical schemaへ反映する。現在の `market-audit` / `market-source-audit` は読み取り専用で、`legacy-market-archive` は保全用でありcanonical移行の代わりにはならない。
- [x] production `sqlite_schema` のテーブル数をWrangler情報と照合した。根拠: 22 app tablesと`d1_migrations`の合計23が一致し、Cloudflare/SQLite system tablesと2つのfeedback triggerも記録した。

## 2026-11-04 D1棚卸しフェーズ

- [x] productionメタデータを小予算で再確認するための読み取り専用フェーズを追加した。根拠: `d1-schema-audit` は全SQLiteテーブル・trigger名を読み、`d1-operational-audit` は `backend_state`、`scheduled_run_log`、市場価格run/batch表をrowidで分割集計する。`value_json`・run ID・行本文をレポートへ出さず、budget超過時の停止とcheckpoint再開を既存機構で扱う。D1専用フェーズにはD1 read credentialだけを渡し、R2-backed phasesとは別にR2 keysを要求する。Worker 115/115件、移行ツール44/44件、Workflow YAMLとphase/secret-scope整合チェックが成功。production D1、R2、GitHub Actionsには接続・書込・実行していない。
- [x] `scheduled_run_log` の未完了行もD1-onlyのoperational auditで集計できるようにした。根拠: `d1-operational-audit` が `status`、開始/終了日時、processed/failed、`finished_at`欠落数だけをrowidページングし、IDやmetadataを読まないテストを追加。移行ツール49/49件成功。実D1監査は日次使用量確認後まで未実施。
- [x] 現行ローカルWorkerのD1参照元を再棚卸しした。根拠: `src/` のSQL検索で市場・分析・定期処理・定義・通知状態のD1参照は見つからず、残るSQLはDurable Object初回の`api_request_log`読取と問い合わせ/メールoutboxのCRUD・整理だけだった。healthの`d1`値はbinding有無。これはローカル変更後のsource確認であり、本番稼働Versionの検証ではない。
- [x] 本番D1のschemaとoperational inventoryを読み取り専用で確認した。根拠: sqlite_schemaの25 table rowsからCloudflare `_cf_KV`とSQLite `sqlite_sequence`を除く22 app tablesに`d1_migrations`を加えるとWranglerの23 tablesに一致し、feedback email trigger 2件を確認。`backend_state`は対象状態キーなし。株価batch数は初期記録の4,160を保存済み14:40 UTC直接監査レポートの2,080（complete 2,020、failed 60）に訂正した。監査合計4,276行、D1書き込み0。
- [ ] 古い`partial`/`running` market-price runの状態、保持先、バックアップ・外部利用・復旧要件を確定する。2026-11-04 04:08 UTCのQueue metricsはbacklog 0だったが、過去の処理完了や保持要件の証明ではない。D1行・Queueは削除しない。
- [x] 2026-11-04 UTCにR2の現行カタログと空白月候補を再読み戻しした。根拠: generation `2026-11-02-daa434e899b0493a9fdf346067419041`、生成時刻 `2026-11-03T19:41:21Z`、4,390/3,203件、重複0、候補の参照generation不一致0。SHA-256を仕様書に記録し、D1読取・R2書込は行っていない。Worker APIとの比較は未完了。
- [x] 2026-11-04 UTCにJDK 17でAndroidのビルド・instrumentationを再確認した。根拠: Gradle build成功、接続テストXMLは136件・失敗0・スキップ1。`emulator-5556`へ同じworktreeのDebug APKを入れ、installed `base.apk` SHA-256 `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` が生成APKと一致。SCG01実機は未変更。

## 2026-11-04 03:30 UTC 継続検証

- [x] 分離実装worktree `codex/r2-d1-ownership-20261104-final`（HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`）でWorker 115/115件、移行ツール44/44件、`node --check src/index.js` を再実行し成功した。`git diff --check` も成功。本番D1クエリ、R2書き込み、Workflow起動は行っていない。
- [x] 同worktreeのAndroidをMicrosoft JDK 17.0.20.1で強制再ビルド・再テストした。根拠: JVM 267/267件、Debug/AndroidTest APK build成功、`emulator-5556` instrumentation 136件・失敗0。opt-inのライブ市場読取テストは明示引数がないためAssumption skip。ビルドAPKとemulatorの`base.apk` SHA-256は `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` で一致。SCG01にはインストールしていない。
- [x] 03:30 UTCに本番R2を直接GETで読み戻した。市場カタログと空白月候補はgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`、4,390件/3,203件、重複0、候補のカタログ外コード0、候補アイテムの参照generation欠落0・不一致0。両オブジェクトのSHA-256はそれぞれ `5830777b686024bc313eed59916fe4db4cbd26b834eff9486f2c8c921e367a1e` と `964abdc8458e6ac2757274b36db6ce691f1dde86b9931f0f423397a5ccc8d1ab`。7203.Tのprofile/currentも存在し、current価格日は2026-11-02、終値¥3,051。Worker APIとの本文比較は未実施。D1読取・R2書込なし。
- [x] GitHub REST読み取りで `main` がHEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b` と一致し、既存Workflowは9件、新Worker CIと手動D1→R2移行Workflowは未公開と確認した。Workflow実行・Environment設定・秘密情報の変更なし。
- [x] 03:35 UTCの読み取り専用Wrangler確認で、本番Workerの最新Versionは引き続き `4981c1e0-e337-4392-9f5b-909b52c5bc0c`（100%、2026-11-03 12:26 UTC）だった。Queue metadataは通常/DLQのproducer・consumer登録を返したがbacklog件数は含まず、Queue backlogは未確認。
- [ ] 03:35 UTCの `wrangler d1 info diview-irbank --json` はCloudflare API認証エラー10000で失敗した。D1行クエリは実行していない。Dashboardの日次Rows Read/残り枠は未確認のため、追加の本番D1行読取は止めている。
- [ ] DashboardのRows Read不一致の解消、analysis-historyを含む残る移行フェーズのdry-run、R2とWorker APIの本文比較、市場データ差異の正規ソース照合、GitHub Environment保護、Cron/Queue切替前確認は未完了。承認前の本番移行・デプロイ・物理端末インストールは行わない。

## 2026-11-04 05:09 UTC 継続検証

- [x] Durable Objectの初回D1カウンター読取が一時失敗した後に再試行できることを回帰テストで固定した。根拠: `rate-limit.test.mjs`で失敗時に状態行ができず、次の要求が旧D1カウンターから継続することを確認。Worker 116/116件、`node --check src/index.js`、`git diff --check`成功。
- [ ] D1日次使用量の最新値と残枠は未確定。05:03 UTCの既存Dashboardタブは13:11 JSTまでの表示（カード約113k、グラフ126.18k）から更新されず、URL安全性を確認できずUI更新を行っていない。表示不一致が解消するまでD1行クエリを追加しない。
- [ ] R2とWorker APIの本文照合、残る移行フェーズのdry-run、市場データ差異の正規ソース分類、GitHub Environment保護、Cron/Queue切替前条件は未完了。本番操作・コミット・プッシュは行わない。
- [x] 本番WorkerとQueueの読み取り専用メタデータを再確認した。根拠: 05:11 UTCのWrangler結果でVersion `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%、通常Queueのproducer/consumer各1、DLQのproducer 0・consumer 1。mainのremote SHAはHEADと同じ。Queue backlog数値は取得できず、D1 infoはAuthentication error 10000で失敗したため、現在の日次使用量とQueue backlogは未確認のまま。

## 2026-11-04 05:21 UTC 継続監査

- [x] D1使用状況を読み取り専用で再確認した。根拠: `wrangler d1 info` は`rows_read_24h=0`を返した一方、同日04:11 UTCのDashboardカード約113kとグラフ126.18kは不一致。1日Insightsでは銘柄検索SQL 1,892回・合計6,978,979行、全銘柄SELECT 10回・39,600行。Insightsの1日窓はUTCの日次枠と異なり、`d1 info`の0もDashboardと矛盾するため、今日の正確な使用量・残枠は未確定。D1 SQLは追加していない。
- [x] UTCリセット後のD1 query signaturesも確認した。根拠: 05:21 UTCに終わる5時間Insightsでは、銘柄検索SQLが118回・90行、全銘柄SELECTが1回・3,960行と記録された。これは正確な請求総数や呼出し元を示さないが、検索D1経路が止まったとは確認できない。
- [x] 公開状態を読み取り専用で再確認した。根拠: Worker Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%、main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`。GitHubコネクターはmain上の新CI/移行Workflowをともに404と返した。どちらも未公開・未実行。
- [ ] D1日次使用量の正確な確認、残る移行フェーズdry-runと差異分類、R2/API本文比較、Queue backlog、Environment保護、Cron切替条件は未完了。曖昧なメトリクスを根拠に追加D1行クエリを行わず、本番移行・デプロイ・Actions・commit/pushは承認前に実施しない。
## 2026-11-04 05:48 UTC ローカル再検証

- [x] 実装worktreeのWorker・D1/R2/DO・移行ツール検証を再実行した。根拠: Node tests 116/116件、Python migration tests 49/49件、node --check src/index.js、Python py_compile、git diff --check が成功。テストはローカルfixture/FakeReaderを使用し、本番D1/R2、Actions、Workerには接続・書き込みしていない。
- [ ] D1日次使用量の正確な残枠と scheduled_run_log を含む本番operational auditは未確認。使用量の矛盾が解消するまで、本番D1行クエリは保留する。

## 2026-11-04 06:13 UTC R2/API照合

- [x] 現行R2カタログ・空白月候補とローカルWorker APIを読み取り比較した。根拠: generation 2026-11-02-daa434e899b0493a9fdf346067419041、カタログ4,390件のコード集合とコード・名称・価格が全件一致、候補3,203件は重複0・カタログ外0・全件で参照generation一致。候補JSONは数値の整数/小数表記を正規化したsemantic SHA-256 1d8a08e080be46ccf0267ff834f06870c02f7bd1e6d963bf4e526c9583fcc303がR2/APIで一致。security-searchとlive-quotesの7203要求は各HTTP 200、価格¥3,051。詳細は C:/Codex/r2-d1-ownership-audit-20261104/r2-worker-api-parity-20261104.json。ローカルWorkerでR2だけremote、D1なし、DO local、Cron無効とし、本番Worker/DOには接続していない。
- [ ] この照合はローカルコードと本番R2のカタログ・候補・7203検索/価格だけを対象とする。analysis current/history、定義・監査、財務・配当API、実運用中WorkerのAPI応答は未確認。
- [ ] 正確なD1日次残枠は未確認。Workers Free利用時はUTC日付でD1 GraphQL Analyticsのd1AnalyticsAdaptiveGroupsを絞り、全databaseIdのrowsReadを合算する。Billing > Billable UsageはPay-as-you-go account向けで、D1 Metricsはデータベース別の時間窓表示。rows_read_24hは移動24時間値なので日次残枠には使わない。([D1 metrics](https://developers.cloudflare.com/d1/observability/metrics-analytics/)、[Billable Usage](https://developers.cloudflare.com/billing/manage/billable-usage/))

## 2026-11-04 07:32 UTC 継続検証

- [x] 分離実装worktreeでWorker・移行CLI検証を再実行した。根拠: Worker 116/116件、移行CLI 51/51件、`node --check src/index.js`、`py_compile`、`git diff --check` が成功。テストはローカルfixtureを使用した。
- [x] AndroidのJDK 17ビルド・エミュレータ検証を再実行した。根拠: JVM 267/267件、Debug/AndroidTest APK build成功、emulator-5556の関連instrumentation 35/35件、生成APKとinstalled `base.apk` のSHA-256一致（`939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80`）。物理端末は変更していない。
- [ ] 正確なD1日次使用量と残枠は未確認。既存ブラウザーでDashboardを読み取り確認しようとしたが、Computer UseがブラウザーURLを十分な確度で特定できず停止し、画面操作は行っていない。全DB合計と残枠が確定するまで本番D1行クエリを行わない。
- [ ] analysis-historyなど残る移行フェーズ、市場データ差異、Environment保護、Queue/Cron切替条件は引き続き未完了。本番移行・デプロイ・Actions実行・物理端末インストール・commit/pushは別途承認まで行わない。

## 2026-11-04 07:42 UTC D1使用量確認準備

- [x] アカウント全体のUTC日次Rows ReadをD1行クエリなしで確認するCLIを追加した。根拠: `scripts/d1_daily_usage.py` はGraphQL `d1AnalyticsAdaptiveGroups` を日付・accountTagで照会し、database IDごとに集計、5M既定枠との差を出す。認証値は環境変数からのみ読む。専用8テストを含むPython 59/59件、Worker 116/116件、`node --check`、`py_compile`、`git diff --check`成功。CIにも専用Pythonテストを追加し、新Workflow両方のYAML構文を検証した。
- [ ] 実際のGraphQL照会は未実施。環境に `CLOUDFLARE_API_TOKEN` がなく、Account Analytics Read権限の既存トークンを利用できない。Dashboardの再確認もComputer UseがURL判定で停止した。したがって本番アカウントの日次合計・残枠は未確定で、本番D1行SQLを再開しない。
- [ ] R2移行フェーズ、差異分類、Environment保護、Queue/Cron切替条件を続けて確認する。本番移行・Workerデプロイ・GitHub Actions実行・物理端末インストール・commit/pushは別途承認まで行わない。

## 2026-11-04 08:55 UTC D1参照・移行手順照合

- [x] D1スキーマ、Worker SQL参照、移行対応表を再照合し、22個のアプリテーブルが全件対応付けられていることを確認して、正本の移行手順書を実装側の最新記録に同期した。根拠: 残るWorker SQLはapi_request_logのDurable Object初回引継ぎと問い合わせ・メールoutbox/保持処理で、期限切れカウンターはDOの次回リクエスト時に更新し、alarmを使わない。
## 2026-11-04 09:06 UTC GitHub Actions公開状態の再確認

- [x] GitHubのmainとローカルWorkflowの公開・起動条件を照合した。根拠: mainはSHA b3f3a7b75bc54d30ec468fc085922791f99e340bでWorkflow 9件、新Worker CIと手動移行Workflowは404、mainはprotected=false。ローカルworkflowの起動条件・Environment preflight・権限・資格情報分離・D1予算ゲートを静的確認し、Actionsは実行していない。
- [ ] d1-r2-migration Environmentのreviewer、main限定policy、secrets設定は未確認。Environmentを確認できて保護条件が整うまで、移行Workflowの実行・production移行は保留する。
## 2026-11-04 09:08 UTC Durable Objectレート制限の引継ぎ確認

- [x] Durable Object化で旧レート制限のキー・窓幅・上限・拒否挙動が保たれることをソース比較した。根拠: HEADのapi_request_log UPSERTと作業中のrequestBucketKeyが同じキー形式で、公開300/60秒、J-Quants 5件、feedback 5件を確認。欠落bindingやDO/D1 seed失敗はfail-closedで、テストに引継ぎ・並行・窓境界・フェンスケースがある。
- [ ] productionのDOプラン・実利用量とhandoff後のカウンター引継ぎは未確認。アカウント使用量、D1読み取り可否、Environment保護を確認し、承認されるまで切替を実行しない。

## 2026-11-04 09:34 UTC 継続検証

- [x] 実装worktreeのWorker・移行CLI・使用量ヘルパーを再実行した。根拠: Worker 116/116件、Python 63/63件が成功。ローカルWorkerのD1参照はDurable Object初回の`api_request_log`読取と問い合わせ/メールoutbox/保持処理だけで、市場データ用SQLは見つからない。
- [x] Android価格通知の取得範囲をコードとテストで確認した。根拠: 通常更新は保有・ウォッチリストの日本株コードのみを最大50件ずつ同一generationから取得し、通知キャッシュも対象コードだけを保存する。全カタログ取得はIRBANK利回り契約更新時の例外。
- [x] JDK 17.0.20.1でAndroid JVM 267/267件、Debug/AndroidTest build、emulator-5556 instrumentation 136/136件が成功。生成Debug APKとemulatorのinstalled `base.apk` SHA-256が`939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80`で一致。物理端末は未変更。
- [ ] D1の日次Rows Readと残枠は未確認。現プロセス環境にAccount Analytics tokenはなく、Edgeの現在URLを安全に特定できずComputer Useが停止した。追加の本番D1行SQLは実行しない。
- [ ] GitHub mainの新Worker CI/移行Workflowは未公開（両ファイル404、main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`）。Environmentの保護設定・secrets、DOの本番プラン/利用量は未確認。analysis-history等の残りdry-run、差異分類、Queue/Cron切替条件も未完了。

## 2026-11-04 09:40 UTC D1書込経路とGitHub再確認

- [x] GitHub mainの状態とWorkflow公開を再確認した。根拠: branch APIはSHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`。新Worker CI/移行Workflowは404。branch-protection詳細APIはconnector permissionで403となり、Environment設定も確認できていない。
- [x] 手動D1書込経路を再棚卸しした。根拠: `dev-local.ps1`は`--local`、旧`import-d1.ps1`と`apply-financial-repair.ps1`は実行時に即停止。D1定義seedは`seed_app_definitions.sql.disabled`へ移し、内容SHA-256を維持。offline repair planはローカル生成のみで、適用scriptは停止。
- [ ] main保護、migration Environment設定、D1日次残量、残り移行dry-run/差異分類、Queue/Cron切替条件は未確認。

## 2026-11-04 09:42 UTC 移行読取予算のコード照合

- [x] Workflowと移行CLIの読取予算を照合した。根拠: workflowはD1 SQL前にaccount-wide `rows_read`を集計し、`max_rows + read_reserve_rows`が残る場合だけ続行する。CLIは各SELECTのCloudflare `meta.rows_read`をphase内で累積し、残量なしなら次のqueryを発行しない。複数テーブル監査も共通budgetを使い、JOIN監査のページは1結果あたり最大2行を見込む。
- [x] budgetゲートのfixtureテストを再実行した。根拠: Worker 116/116件、移行・usage Python 63/63件が成功。実アカウントの残枠は未確認で、本番D1 SQLは実行していない。

## 2026-11-04 09:57 UTC CLI単体のD1使用量事前確認

- [x] Workflow経由ではない移行CLI単体の起動も、D1クライアント生成前にアカウント全体のUTC日次Rows Readを確認するよう修正した。根拠: Analytics token欠落時と残枠不足時はD1クライアントが呼ばれず、別々のAnalytics/D1読取tokenで事前確認後にのみD1へ進むテストを追加し、移行・usage Python 66/66件が成功。
- [x] 手動WorkflowからAnalytics tokenと`read_reserve_rows`を各CLI実行へ渡し、CLIの再確認結果をサニタイズしたレポートに含める。移行手順書にCLI直接実行時の必須環境変数とフェイルクローズ条件を記録した。
- [ ] Account Analytics tokenが現環境にないため、実アカウントの日次使用量と残枠、GitHub上のWorkflow公開・設定・実行は未確認。本番D1 SQL、R2書込み、Actions実行は行っていない。

## 2026-11-04 10:09 UTC 事前確認修正の再検証

- [x] 移行CLIとWorkerの回帰検証を再実行した。根拠: 移行・usage Python 66/66件、Worker 116/116件が成功し、CLIを別ディレクトリから`--help`で起動できる。`git diff --check`と対象ファイルの末尾空白検査も成功した。
- [x] GitHub mainの公開状態を再確認した。根拠: main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`、Worker CIと移行WorkflowはAPIで404。書込み・push・Actions実行なし。
- [ ] `CLOUDFLARE_API_TOKEN`と移行専用read tokenは現プロセス環境にない。Wrangler CLIはOAuthログインを報告するが、その権限でAccount Analyticsを照会していないため、アカウント日次使用量・残枠は未確認。

## 2026-11-04 10:17 UTC 稼働状態とデプロイ手順の確認

- [x] D1読み取り経路と本番Workerを再確認した。根拠: Worker SQLはDO初回の旧`api_request_log`カウンターSELECTと、問い合わせ・メール再試行/保持処理に限られる。`wrangler deployments list`は旧Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%と報告した。
- [x] `wrangler d1 info`は読み取り専用メタデータとして取得した。根拠: `diview-irbank`は23テーブル、`rows_read_24h=165,045`。これは単一DBのローリング24時間値で、全10 DBのUTC日次使用量・残枠とは扱わない。D1行SQLは実行していない。
- [x] READMEのD1 execute・versions upload/deploy・Worker deployを、ローカル検証で使ったWrangler 4.157.0に固定した。根拠: 現PATHのグローバルWranglerは4.136.1で、過去のcompatibility date検証で古いworkerdの制約を確認している。固定コマンドはまだ本番実行していない。
- [ ] 本番Workerは旧Versionのまま。全DBの正確な日次使用量、移行Workflowの公開/保護設定、残りの本番データ監査、Queue/Cron切替条件は未確認。


## 2026-11-04 10:40 UTC 回帰検証

- [x] JDK 17.0.20.1でAndroid単体テスト267/267件を強制再実行し、Debug/AndroidTest APKをビルドした。根拠: `testDebugUnitTest --rerun-tasks` が成功し、`emulator-5556` instrumentation 136/136件、生成APKとインストール済み `base.apk` のSHA-256 `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80` の一致を確認した。物理端末は未変更。
- [x] Worker・移行・使用量テストをCIと同じコマンド形式で実行した。根拠: Node 116/116件、Python 54/54件と12/12件が成功し、`node --check` と`py_compile`も成功した。
- [x] 移行Workflowのブランチ保護preflightをローカルで再検証した。根拠: 静的テストは手動起動のみとmain保護必須を確認し、GitHub RESTではmainが`protected=false`、両Workflowが404のため未公開と確認した。
- [ ] Account Analyticsによる全DB合算の日次Rows Read、Environment保護/secrets、残りの本番監査・dry-run、DO本番プラン/利用量、Cron/Queue切替条件は未確認。D1行SQL、R2書き込み、Actions起動、Workerデプロイ、物理端末インストール、commit/pushは行っていない。


## 2026-11-04 10:51 UTC R2/API照合とhealth

- [x] 現行R2カタログとローカルWorker APIを再照合した。根拠: R2 remote/local D1なしのWorkerで候補APIがgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`・3,203件、検索/価格APIが7203・¥3,051・2026-11-02を返した。
- [x] healthが旧snapshotを現行v2として誤って表す状態を修正した。根拠: healthにv2 generation/date/item/quote countとlegacy snapshot ageを追加し、Workerテスト117/117件成功。remote R2読み戻し後に4,390件・現行generationがhealthへ出ることを確認した。
- [ ] R2のanalysis-history/7203は0件、definition-auditは0件、stock-analysis-definitionsはR2/local KVで`{}`。D1の全履歴・定義監査の移行dry-run、欠損/差異分類、R2へ必要データを移した後のAPI応答照合が必要。
- [ ] Account Analytics日次残量、GitHub Environment/main保護、Queues backlog、DO本番plan/usage、Cron停止・復旧条件は未確認。本番D1 SQL、R2書き込み、Actions、Worker deploy、物理端末install、commit/pushは未実施。


## 2026-11-04 10:57 定義KV読み戻し

- [x] 本番KVで使われている3定義をチェックイン済みseedと照合した。根拠: Wrangler remote KV GETの意味的SHA-256がstock-analysis、watchlist-analysis、portfolio-analysisの各seedと一致し、移行CLIもKVを最優先で読む。
- [ ] 3定義のR2 versioned object/pointerは未確認または未配置。D1 usage gateを通過後にdefinition migration dry-run/write/readbackでR2正本を作り、API・health・分析応答を照合する。

## 2026-11-04 11:16 UTC 定義ソース優先順位とGitHub状態

- [x] 定義移行で現在のKV値を古いD1文書より優先する回帰テストを追加した。根拠: FakeReaderに異なるD1値を設定し、KV値がR2 version/pointerに保存され、D1 queryが0件となるテストが成功した。CIと同じ実行でWorker 117/117件、移行CLI 55/55件、日次使用量12/12件が成功。
- [x] GitHubの読み取り専用APIで公開状態とWorkflowのEnvironment read権限を確認した。根拠: main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`、Worker CIと手動移行Workflowはmainで404。ローカルWorkflowは`actions: read`を宣言し、API仕様に必要な権限を満たすことを静的テストで確認した（[GitHub Environment API](https://docs.github.com/en/rest/deployments/environments)）。実Environment設定の取得はconnectorで拒否されたため未確認。Workflow実行・設定変更・書込みなし。
- [x] 移行中のCron競合対策を短時間停止方式に決め、手順と復旧条件を記録した。根拠: runbookでは安全な不変データを先行移行後、最終差分時にCronを止めて実行中処理の完了を待ち、mutableな分析・完了runを再走査してから新WorkerでCronを戻す。二重書き実装は追加しない。
- [ ] R2定義オブジェクト、全履歴/監査の移行dry-run、正確なD1日次使用量、Environment保護、Queue backlogとCron停止前の実運用条件は未確認。承認前の本番移行・デプロイは行わない。

## 2026-11-04 11:22 UTC 現行R2とWorker APIの再読み戻し

- [x] D1 bindingなし、Cron無効、remote R2のみのローカルWorkerから読み取りAPIを再確認した。根拠: search-catalogとincome-advice-candidatesはHTTP 200で同generation `2026-11-02-daa434e899b0493a9fdf346067419041`、4,390件/3,203件を返し、候補のsource generation不一致は0。7203検索/価格も200で価格日2026-11-02。healthはv2 catalogの4,390価格付き銘柄を示した。
- [ ] 同じAPI読み戻しでは7203 analysis-historyが0件、definition-auditが0件、stock-analysis-definitionsが空。全履歴/監査/定義のR2移行・読戻しと本番Workerでの確認は未完了。これはローカルWorkerからのR2 readで、本番D1 SQL・R2 write・本番Worker/DO・Actions・デプロイは行っていない。

## 2026-11-04 11:44 UTC Cloudflare使用量・DO容量確認

- [x] Account DashboardのD1日次値を再確認した。根拠: 10 DB、Rows Read 159.29K/5M、Rows Written 2.43K/100K、Storage 105.03MB/5GB。UTC 00:00からの表示で、読み取り残枠は約4.84M。移行CLIのAnalytics APIゲートは別途必要なので、本番D1 queryは実行していない。
- [x] Production Workerの実トラフィックからDO Free枠の初期需要を見積もった。根拠: Workers Free日次リクエスト1,221/100,000、`diview-irbank`直近24時間約1.24K invocation・554 errors・CPU 2ms。現Production Version `4981c1e0` は100%でDO bindingはないため、DO実使用量の測定ではなく切替後リクエスト需要の見積りとして記録。
- [ ] ProductionでのDO実測値、ピーク時間帯、他namespace分を含むアカウント合算利用量は未確認。切替承認後、1要求1 DO request・1 row read/write設計の実測と日次上限監視を確認する。
- [ ] Dashboard上の残枠は確認できたが、Account Analytics GraphQL tokenがないため移行CLIの使用量ゲートを満たせず、全履歴・定義監査等のD1 dry-runは未実施。R2定義・全履歴の移行と照合、Queue backlog、Cron停止前条件、GitHub Environment保護は引き続き未完了。

## 2026-11-04 11:52 UTC ローカル回帰検証

- [x] 実装worktreeのWorker・D1/R2/DO契約テストを実行した。根拠: `node --test test/*.test.mjs test/*.test.cjs` が117/117件成功し、`node --check src/index.js` も成功。
- [x] 移行CLIと日次使用量ヘルパーの回帰テストを実行した。根拠: `python -m unittest scripts/test_migrate_d1_to_r2.py scripts/test_d1_daily_usage.py` が67件成功、両スクリプトの`py_compile`が成功。テストはfixture/FakeReaderを使い、本番D1/R2へ接続していない。
- [ ] GitHub Actionsはローカルのままで未公開・未実行。`actionlint`とPython YAML parserはこの環境にない。workflow dispatch、Environment保護、branch protectionの本番受け入れは未完了。

## 2026-11-04 12:08 UTC 手動移行Workflowのfail-closed検査

- [x] 非`main`から起動した移行workflowが成功扱いでskipされないよう修正し、policy testを追加した。根拠: preflight jobは全refで実行され、GitHub API照会前に`GITHUB_REF`を検査する。`python -m unittest scripts/test_migration_workflow.py` は3/3件成功し、`git diff --check`も成功。
- [ ] GitHub Actions上のYAML検証・workflow実行、main branch protection、migration Environmentの設定は未確認。Workflowは未公開であり、本番移行は未実施。

## 2026-11-04 12:18 UTC 定義のWorker実行時参照をR2に限定

- [x] Workerの定義取得をR2 version/currentオブジェクトのみにした。根拠: R2 pointerがない場合に旧KVを読まない契約テストを追加し、Worker 118/118件、移行系70/70件が成功。移行CLIのKV優先読み込みはR2 seed用に維持。
- [ ] 定義のR2 version/currentオブジェクト、definition-audit全件を移行・読み戻しし、定義APIと分析応答を照合する。現行R2は空の読み戻し記録があり、Workerを本番デプロイしない。

## 2026-11-04 12:30 UTC healthの定義欠落検出

- [x] health APIが必須R2分析定義の欠落を`503 definitions_unavailable`として報告するよう修正した。根拠: `/api/v1/health`の欠落・KV/D1非参照テストを追加し、`scheduled-analysis.test.mjs` 16/16件成功。
- [x] Worker全体と移行系Pythonの回帰テストを再実行した。根拠: Node Worker 119/119件、移行・usage・workflow policy Python 70/70件、`node --check`、`git diff --check`が成功。
- [ ] R2定義の実体移行・読み戻しと、本番切替前のAPI照合を完了する。

## 2026-11-04 12:30 UTC 旧価格Queue状態の保存先決定

- [x] 旧`market_price_refresh_runs`/`market_price_refresh_batches`をDOへ移さない方針を仕様とrunbookへ反映した。根拠: 現行WorkerにQueue送信呼び出しはなく、consumerは旧メッセージをACKし、D1 run/batch表へのruntime read/writeもない。DOは共有レート制限に限定する。
- [ ] 本番Queue backlog、稼働中producer/Version、バックアップ・保持要件を確認するまでQueue bindingとD1行は保持する。完了済み行が必要なら承認後にR2 archiveへ保全する。

## 2026-11-04 12:41 UTC Queueと設定KVの読み取り確認

- [x] Queueの登録metadataと現行ソースを読み取り確認した。根拠: 通常Queueはproducer/consumer各1、DLQはproducer 0・consumer 1として登録され、現行ソースにsendBatch呼び出しはない。
- [ ] Queue backlog・稼働中Worker Version・外部producerは未確認。WranglerのQueue情報はbacklogを返さないため、Queue metrics APIの読み取りが必要。
- [ ] `backend-config:v1`のremote KV readは401で失敗した。R2移行前に承認済み資格情報で現在値を検証できない限り、rate-limit設定のproduction parityは未確認として扱う。

## 2026-11-04 12:50 UTC Androidバックアップ／復元経路の監査

- [x] 端末管理データのOSバックアップ設定と復元コードを棚卸しした。根拠: Manifestは`allowBackup=true`、両ルールファイルは`local_import_store.xml`と`notification_delivery.xml`のみ除外。`filesDir/diview_auto_backup.json`経由で保有銘柄・ウォッチリスト・DVMバックアップ・携帯可能な上書き設定を復元し、`notification_price_cache`と`notification_preferences`は除外されていない。
- [ ] Androidの端末間／クラウド復元を実機で検証し、`notification_delivery`の除外による通知履歴リセットと、`cloudflare_market_cache`を含むバックアップ容量・復元方針を確認する。既存除外ルールはこの移行だけを理由に変更しない。

## 2026-11-04 12:57 UTC Queue backlogの読み取り方法

- [x] Queueの実時間backlog APIと対象IDを確認した。根拠: Cloudflare公式metrics endpointの仕様とWrangler読み取りmetadataでQueue IDを特定し、runbookにread-only照会例を追加。
- [x] 12:57時点で未設定だったAccount Analytics Read tokenは13:55 UTCまでに環境変数として利用可能になり、日次使用量を再取得した。根拠: 2026-11-04 UTCのRows Read 167,539、残り4,832,461行。値は出力していない。
- [ ] Queue backlog count/oldest timestampは未取得。現行Worker Version、外部producer、バックアップ・保持要件と合わせて確認するまでQueue bindingやD1行を削除しない。

## 2026-11-04 13:04 UTC 本番bindingとR2定義の読み戻し

- [x] 本番deploymentとWorker bindingを読み取り確認した。根拠: Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%で、D1/R2/KV/Queue bindingあり・`RATE_LIMITER`(DO)なし。
- [x] 6つの定義pointerを本番R2から個別GETした。根拠: backend-config、diagnosis、valuation、stock/watch/portfolio analysis definitions の全`current.json`が404。D1 query・R2 writeは0。
- [ ] 6定義の正しい移行元・値・版を確認し、承認後にversion objectとcurrent pointerを公開して読み戻す。特にbackend-configの本番値、analysis API/health parityは未確認。

## 2026-11-04 13:07 UTC 現行R2に対するローカルWorker API確認

- [x] D1なし・remote R2・local DO/KVのWorkerで読み取りGET経路を照合した。根拠: healthとstock/watch/portfolio definitionsは503 `definitions_unavailable`、definition-auditとanalysis-history/7203は200 `items: []`。analysis API・D1 query・R2 writeなし。
- [ ] これは現ローカルコードの本番R2 readbackであり、本番Worker/DOや全銘柄の履歴確認ではない。定義の移行、全履歴・監査のD1 dry-runと差異分類、対象範囲の読み戻しを行う。

## 2026-11-04 13:10 UTC インカム空白月候補R2/API再読み戻し

- [x] 現行R2候補とWorker APIの世代・件数を再照合した。根拠: APIは200でgeneration `2026-11-02-daa434e899b0493a9fdf346067419041`、3,203件、生成時刻`2026-11-03T19:41:21.044036+00:00`を返し、全候補3,203件の`source_catalog_generation`が一致、7203候補あり。D1 query・R2 writeなし。
- [ ] Android側のキャッシュ後の端末内ランキング・表示、および本番Worker応答はこの読み戻しでは検証していない。現行Androidテストと後続の本番承認後確認を分けて記録する。

## 2026-11-04 13:17 UTC GitHub Actions Workflow静的検証

- [x] 公式actionlint v1.7.12のWindows AMD64アーカイブを公開SHA-256と照合し、ローカルWorker CI・手動移行Workflowの静的検査が両方成功した。
- [ ] WorkflowはGitHub mainで未公開・未実行。main保護と`d1-r2-migration` Environment設定も未確認のため、本番移行Workflowの実行条件は満たしていない。

## 2026-11-04 13:29 UTC D1 Account Analytics使用量確認

- [x] Account Analytics GraphQLで2026-11-04 UTCのRows Readを確認した。根拠: 13:28:58 UTCに全DB合計167,531行、Rows Written 2,783行、使用DB 3件、5M枠残り4,832,469行。D1 SQLなし。
- [ ] 移行CLI用の別個の`CLOUDFLARE_MIGRATION_READ_TOKEN`が未設定のため、analysis-historyと残りフェーズのD1 dry-runは未実施。Account Analytics tokenをD1クエリに使わず、同じcheckpointから再開する前に read-only token と最新予算を確認する。

## 2026-11-04 13:35 UTC インカム空白月候補のAndroid回帰テスト

- [x] 候補API解析・JSONキャッシュ再解析・端末内順位付けの回帰テストを追加し、JDK 17.0.20.1でDebug JVMテスト271/271件が成功した。
- [x] Watchlist画面の実表示とSharedPreferencesからのキャッシュ復元をエミュレータで検証した。根拠: オフライン状態で候補コード・名前・配当月を表示し、API要求0件。JDK 17 Debug APKとエミュレータのinstalled APK SHA-256が一致。
- [ ] 物理端末上の確認と本番Workerの候補API確認は未実施。今回の画面検証はエミュレータ内に限る。

## 2026-11-04 13:55 UTC D1使用量とオフライン候補表示の再確認

- [x] 設定済みAccount Analytics Read tokenで日次使用量を確認した。根拠: 13:55:06 UTC、Rows Read 167,539/5,000,000、Rows Written 2,784、使用DB 3件、残り4,832,461行。D1 SQLは未実行。
- [x] Androidのインカム空白月候補キャッシュをJDK 17でビルドし、emulator-5554だけにインストールして1件のinstrumentation testを実行した。候補の画面表示・ローカル順位付け・ネットワーク要求なしを確認し、APK SHA-256一致。
- [ ] Queue backlog、全履歴/定義のD1 dry-runとR2 readback、GitHub保護設定は未確認。production D1/R2書込み、Workflow実行、Worker deploy、物理端末install、commit/pushは未実施。

## 2026-11-04 14:08 UTC Queue backlog権限確認

- [x] Queue backlog APIの必要権限を検証した。根拠: 既存Account Analytics tokenで2 Queue IDのREST GETはHTTP 403、GraphQL backlog集計は過去24時間に両Queueとも0サンプル。Cloudflare公式REST仕様はQueues ReadまたはWorkers Scripts Readを要求する。
- [ ] 0サンプルは現在のQueue backlogが0である証拠ではない。`CLOUDFLARE_QUEUE_READ_TOKEN`を設定して両Queueのpoint-in-time metricsを再取得するまで、Queueと旧D1状態を保持する。
- [ ] 14:08 UTCの環境確認では`CLOUDFLARE_MIGRATION_READ_TOKEN`もまだ利用可能でなかったため、D1 migration dry-runは実行していない。
## 2026-11-04 14:25 UTC D1 Read tokenと分析履歴dry-run

- [x] D1 Read tokenを値を表示せずに認証確認した。根拠: 14:20 UTCの`d1-schema-audit`が1 D1 query・92 rows_read・27 schema objectsで完了し、D1/R2 writesは0。Account Analytics preflightは171,633/5,000,000 rows_read、残り4,828,367行。
- [x] `analysis-history`を既存cursor 1,500から500行だけ再開した。根拠: 14:24 UTC preflightで171,725行・残り4,828,275行、5 D1 queries・500 rows_read、累積cursor 2,000、500 objects planned、objects_written 0、conflicts 0。報告SHA-256 `025b906aa67e5a30ab08afd1922b317953e7ddded2345b2b2f74cb5e2d390ed6`。report/checkpointは`C:\Codex\r2-d1-ownership-audit-20261104\analysis-history-resume-20261104T1425Z-*`。
- [ ] `analysis-history`はcursor 2,000で未完了。後続履歴、定義・監査・運用データのdry-runとR2読み戻しを未完了として継続し、dispatchごとにAccount Analytics残枠を確認する。
- [ ] Queue backlogとGitHub Environment/branch protectionは未確認。本番D1/R2書込み、Workflow実行、Worker deploy、物理端末install、commit/pushは未実施。## 2026-11-04 14:32 UTC 分析履歴dry-run継続

- [x] `analysis-history`をcursor 2,000からさらに500行dry-runした。根拠: 14:31:01 UTCのAccount Analytics preflightは172,225行・残り4,827,775行。5 D1 queries・500 rows_readでcursor 2,500へ進み、500 objects planned、objects_written 0、conflicts 0。report SHA-256 `445eb0cf3887ff7ca026dd53c169b981d72c93c8ee6cc0dcb1967e93b161b6da`。
- [ ] `analysis-history`全体はcursor 2,500で未完了。追加500行ごとのR2 GETを含むdry-runは時間がかかるため、使用量を確認しながら小分けで続ける。
- [x] `definitions` dry-runに必要なWorkers KV Storage Readを特定した。根拠: CLIは6つの定義ごとにKVを先に読む。D1/Workers KVのWrite権限は付けない。
- [ ] definition-audit/他の未確認フェーズ、Queue backlog、GitHub保護設定は未完了。本番書込み・Workflow実行・Worker deploy・物理端末install・commit/pushは未実施。## 2026-11-04 14:41 UTC D1 operational-state audit

- [x] `scheduled_run_log`の実態を読み取り監査した。根拠: 621行すべて`running`、`finished_at`なし、processed/failed合計0。別dry-runの`scheduled-runs`は完了対象0件だった。IDやmetadata本文は取得・保存していない。
- [x] 株価更新の旧運用表と`backend_state`を読み取り監査した。根拠: `market_price_refresh_runs`は2行（partial 1、running 1、finished_atなし1）、`market_price_refresh_batches`は2,080行（complete 2,020、failed 60）、`backend_state`は0行。D1/R2 writesなし。
- [ ] 旧`scheduled_run_log`の621件と進行中の株価run/Queue記録はR2の完了履歴として移さない。未完了理由、Queue backlog、保持・バックアップを確認するまでD1行を保持する。
- [x] 株価batch数の記録差を保存済みレポートで照合した。根拠: `d1-operational-market-price-batches-20261104T1440Z-report.json`は`row_count=2,080`、status内訳2,020 complete・60 failedを示すため、旧メモの4,160を訂正した。古いrun/Queue状態の保持要件は別途未解決。
- [ ] Analytics GraphQLは14:34〜14:41 UTCに172,733行のまま変化せず、その間にCLIのD1応答は合計3,948 rows_readを報告した。正確な最新残枠は未確定のため、追加D1 SQL前に再確認する。
- [x] 14:48 UTCにD1/KV Read権限を読み取り検証した。根拠: `backend-config:v1` KV GETはHTTP 200、`definitions` dry-runは12 objects planned・D1 queries 0・R2 writes 0。D1/Workers KV Write権限は付与していない。
## 2026-11-04 14:48 UTC 定義のKV読取とdry-run

- [x] `CLOUDFLARE_MIGRATION_READ_TOKEN`のKV Read権限を検証した。根拠: `backend-config:v1`のKV GETがHTTP 200。レスポンス本文は表示・保存していない。
- [x] `definitions`を全件dry-runした。根拠: 6定義からversion/currentの12 R2 objectsを計画、D1 queries 0・rows_read 0、objects_written 0。R2側の既存objectは0件のため、まだ本番の定義提供は切り替わっていない。
- [x] Account Analyticsの遅延した使用量集計が追いついた。根拠: 14:47:37 UTCに176,681行、残り4,823,319行を確認し、前回172,733から3,948行増えてCLIのD1読取報告と一致。
- [ ] 定義12 objectsのR2 seed/write・readback・API parityは未実施。本番R2書込みの最終承認前に行わない。
- [ ] `analysis-history`はcursor 2,500で未完了。Queue backlog、GitHub保護設定、R2読み戻し・cutover前検証も未完了。## 2026-11-04 14:52 UTC 分析履歴dry-run継続

- [x] `analysis-history`をcursor 2,500から500行dry-runした。根拠: 14:50:12 UTC preflight 176,681 rows、今回5 D1 queries・500 rows_read、cursor 3,000、500 objects planned、writes 0。report SHA-256 `f8b08f8809770742cb667bece261a8e9204080d88691e7039bfe9471b1b56d28`。
- [ ] 14:52:28 UTCの次回Analyticsは176,884行で、直前のdry-runの500行増加をまだ全て反映していない。日次の厳密な残枠は再び未確定。次のD1 SQL前に再確認し、予備枠100,000行を維持する。
- [x] 14:52時点でcursor 3,000だった`analysis-history` dry-runを後続実行で完了した。詳細は2026-11-04 15:03 UTCの記録を参照。R2 readback/seedと本番切替は別途未完了。

## 2026-11-04 15:03 UTC（2026-11-05 JST）分析履歴dry-run完了

- [x] `analysis-history`の存在確認をR2 prefix listingにまとめた。根拠: `migrate_d1_to_r2.py`が履歴prefixをdispatchごとに一度列挙し、既存キーのみGET、未登録キーはdry-runでGETを省略する。write modeは条件付きPUT、競合時GET、成功後readbackを維持。
- [x] 移行CLIテストと構文確認に成功した。根拠: `test_migrate_d1_to_r2.py` 58/58件成功、migration scriptとtest scriptの`py_compile`成功。
- [x] cursor 3,000から全履歴を読み取りdry-runした。根拠: 500行を約3秒、残り34,472行を35 queries・約12秒で処理しcursor 37,972で完了。累積37,972 objects planned、objects_unchanged 0、conflicts 0、objects_written 0。report SHA-256 `43374eebf23a5ff2a42e0ca8972df34b1b9a8adefc05167d8ce07965e4d950b`。
- [x] Analyticsは15:05:06 UTCに212,164行read・残り4,787,836行を返した。これは今回のdry-run後のAccount Analytics確認で、次のSQL前にも再確認し100,000行reserveを維持する。
- [x] 最終ローカル回帰検証を実行した。根拠: Worker 119/119件、Python migration/usage/workflow 74/74件、`node --check`、`py_compile`、`actionlint`、`git diff --check`が成功。
- [ ] write modeの実所要時間、R2 object readbackとWorker/API parityは未検証。本番R2 writeとcutoverは別途承認後に行う。

## 2026-11-04 15:06 UTC GitHub Actions公開・保護状態

- [x] GitHubのremote `main`とWorkflow公開状態を再確認した。根拠: `main` SHAは`b3f3a7b75bc54d30ec468fc085922791f99e340b`で作業HEADと一致。`d1-r2-migration.yml`と`cloudflare-worker-ci.yml`はmainで404。
- [x] mainとEnvironmentの保護状態を読み取り確認した。根拠: branch APIは`protected=false`、repository rulesetsは空。公開Environment一覧は`IRBANK_API_KEY`のみで、`d1-r2-migration`は存在しない。既存Environmentにprotection rulesとdeployment branch policyはない。
- [x] 未保護mainを移行Workflowのpreflightで拒否する条件と回帰テストを確認した。根拠: preflightはmainの`protected=true`、独立reviewer、自己承認禁止、main限定Environmentを要求し、policy test 4/4件と`actionlint`が成功。
- [x] 最終ローカル回帰検証を実行した。根拠: Worker 119/119件、Python migration/usage/workflow 74/74件、`node --check`、`py_compile`、`actionlint`、`git diff --check`が成功。
- [ ] Actionsはまだ未公開・未実行。Workflow公開前にmain保護を設定し、`d1-r2-migration` Environmentを独立reviewer必須・自己承認不可・main限定で作成する。設定を読み戻すまで移行Workflowは起動しない。
- [ ] `CLOUDFLARE_QUEUE_READ_TOKEN`はProcess/User/Machineすべて未設定。Queue backlog APIのpoint-in-time状態を確認するまで、古いD1運用状態とQueue関連処理の整理は保留する。

## 2026-11-04 15:28 UTC 読み取り専用dry-run継続

- [x] 当日Account Analytics usage gateを再確認した。根拠: 15:25:11 UTCに3 DB合計216,290 rows read、残り4,783,710行、100,000行reserveを上回った。各D1 phaseもSQL直前にusage gateを通過した。
- [x] `definition-audit`全件をdry-runした。根拠: D1 2行・1 queryからR2 2 objectsを計画、conflicts 0・R2 writes 0。report SHA-256 `ecc9e1465f6683a21721fae0b846816367ca334318f6a0d324f1e687cff0a29b`。
- [x] `analysis-current`全件をdry-runした。根拠: D1 248行・3 queries、248 objects planned、conflicts 0・R2 writes 0。report SHA-256 `c711efef6a47d828a137cde4a6f6453566e1e939067bff57f06d5dd59ee72589`。
- [x] `analysis-source`をdry-runした。根拠: 1 queryでsource rows 0、objects planned 0、R2 writes 0。
- [ ] Queue backlogは引き続き未確認。`CLOUDFLARE_QUEUE_READ_TOKEN`がないため、古い運用状態の保持・整理判断は保留する。
- [ ] Analytics集計は直近の250 D1 rows_readをまだ反映していない。次のD1 SQL前に新しいAccount Analytics値を確認し、100,000行reserveを維持する。
- [ ] 本番R2 object seed/write/readback、差分の銘柄別分類と修正、本番Worker/API parity、GitHub main/Environment保護設定は未完了。最終承認前のR2本番書込み・Workflow実行・Worker deploy・端末install・commit/pushは行わない。

## 2026-11-05 JST（2026-11-04 16:00 UTC）KV読取権限の確認

- [x] KV読取権限を確認し、6件の定義を全件dry-runした。根拠: D1 query/rows 0、R2 objects planned 12、conflicts 0、R2 writes 0。Account Analyticsは315,734 rows read、残り4,684,266 rows。report SHA-256 `cf1c9762699d023e2c05cae718fc4b497f7f08044185ee927fecd4dd933c3526`。ローカルレポート: `C:\Codex\r2-d1-ownership-audit-20261105\definitions-kv-read-dry-run-20261104T1600Z.json`。
- [ ] 12件のR2定義objectは未作成のためseed/readback/API parityは未完了。本番R2書込みと切替は最終承認前に実施しない。
- [ ] 次のD1 SQL前にAccount Analyticsを再取得し、100,000 rows reserveを維持する。`CLOUDFLARE_QUEUE_READ_TOKEN`も未設定のためQueue backlogは未確認。
## 2026-11-05 JST（2026-11-04 16:00 UTC）金融期間の出所再確認

- [x] D1取込元とR2の金額単位差を確認した。根拠: `data/irbank/raw/financials/annual/batch_0000.json`のIRBANK定義は金額を`million_jpy`、本番R2 `overview.json`の該当行は`jpy`として保持する。既存`market-source-audit`は数値を直接比較するため、監査statusは単位正規化後の再判定が必要。
- [x] 極洋1301のFY2017/03・FY2023/03・FY2024/03は、R2値が公式資料と一致し、D1側は単位換算しても一致しない。公式値は売上高236,561・272,167・261,604百万円。参照: [2017年3月期の公式資料](https://www.kyokuyo.co.jp/files/2018CSRd.pdf)、[2024年3月期決算短信](https://www.kyokuyo.co.jp/files/202405101r.pdf)。このD1行をR2へ上書き・移行しない。
- [x] 久世2708のR2年次データは会社IRの2017/03〜2026/03期の売上高系列と整合する。D1には2011/10〜2025/10の行があり、2024/10・2025/10の値を含め期間と値の出所が不整合。FY2014/03は`audit_cross_issuer_financials.py`の既知partial contamination fingerprintに該当する。参照: [久世公式財務情報](https://www.kuze.co.jp/ir/financial/)。これらのD1行は個別に照合するまで移行・削除しない。
- [x] market-source-auditを単位と出所を考慮して再判定した。根拠: version 2の全49,592行監査で49,571一致、4差分、17期間未存在。million_jpyからjpyへの換算42項目、単位不明0。レポートSHA-256 b2bd01c0982d86a7375d3a1f574ad7929a2c29beb757f69444b4d1d2fb622f3f。
- [x] 金融期間監査はD1 SELECT/R2 GETのみで完了した。根拠: source rows 49,592、累計D1 rows_read 99,184、R2 source lookups 3,062、objects_written 0。レポート C:\Codex\r2-d1-ownership-audit-20261105\financial-periods-unit-aware-20261104T1630Z.json。R2書込み・Worker deploy・production API切替なし。

## 2026-11-05 JST WorkerのD1参照確認

- [x] Worker runtimeのD1 SQL参照を静的に確認した。根拠: `src/index.js`で市場データ、銘柄検索、分析定義・履歴、定期分析に使うD1 SQLは見つからない。定期分析はR2 catalog/definitions/inputとR2履歴を使う。
- [x] 残るD1用途を確認した。`api_request_log`は空のDurable Object状態を一度だけ引き継ぐ読取、`support_feedback`は問い合わせ保存・管理読取・90日保持処理。health routeはD1 bindingの有無だけを返す。これらは市場データ読取とは別用途。
- [ ] `api_request_log`のDO引継ぎ完了・有効window中カウンター保持と、旧ログの保持条件を本番切替前に確認する。Queue backlog未確認のため、運用ログや処理状態の整理・削除は保留する。

## 2026-11-05 JST（2026-11-04 16:39 UTC）単位正規化監査と最新使用量

- [x] financial_periodsの比較をIRBANKの金額単位とR2の行別field_unitsに合わせて再実行した。根拠: 49,592行中49,571一致、4差分、17期間未存在。42項目をmillion_jpyからjpyへ換算し、単位メタデータ不足0。レポートSHA-256 b2bd01c0982d86a7375d3a1f574ad7929a2c29beb757f69444b4d1d2fb622f3f。
- [x] D1日次使用量をAccount Analyticsから再取得した。根拠: 16:39:43 UTC時点で全3 DB合計419,056 rows read / 5,000,000、残り4,580,944。Rows Writtenは3,292。Analytics照会自体はD1 SQLを発行していない。
- [x] 移行ツール62件、日次使用量12件、Workflow policy 4件のローカルテストが成功した。
- [ ] 17件の未存在期間（1301のFY2025/03・FY2026/03、2708の10月期15件）と4差分（1301の3行、2708のFY2014/03）は出所・内容が未解決。R2へ反映せず、D1行も削除せず、保持・アーカイブ方針と金融期間のfallback要否を別途確認する。
- [ ] 最新Analytics値にも100,000行reserveを維持し、次のD1 SQL直前に再取得する。Queue backlog、定義R2 seed/readback、GitHub保護、production切替は未完了。


## 2026-11-05 JST（2026-11-04 17:06–17:10 UTC）KV権限反映と最新readback

- [x] Workers KV Storage Readを反映した移行用tokenでdefinitions dry-runを再確認した。根拠: 6定義・12 R2 objects planned、D1 query/rows 0、conflicts 0、R2 writes 0。17:10:43 UTCのAccount Analyticsは419,064/5,000,000 rows read、残り4,580,936、3 DB。レポートSHA-256 `E40C489DC26A09FD48974D9EE0651AB32B1B0A55DB2583C1978D9E2C34972C18`。
- [x] local Worker + remote R2のGET結果をレポートに出所情報付きで保存した。根拠: カタログ4,390件・候補3,203件・generation `2026-11-02-daa434e899b0493a9fdf346067419041`、7203価格complete。履歴/監査は200で0件。healthと3種類のanalysis definitionsは503 `definitions_unavailable`。本番Worker API parityは未証明。
- [x] ローカル検証結果を集約した。根拠: Worker 119/119、Python 78/78、JDK 17 JVM 271/271、emulator-5554 notification instrumentation 13/13。物理端末未使用。APK SHA-256 `939b578cef0d715702640ceb582d3016a75977e7495c5cc1d077cac81c758b80`。
- [ ] 12件の定義R2 objectは未seedでhealth/definitions APIが503。Queue backlog、GitHub保護/Environment、本番DO counter handoff、金融期間4差分・17不足の正本性も未解決。承認前のR2書込み・本番deploy・Workflow実行・実機install・commit/pushは未実施。

## 2026-11-05 JST（2026-11-04 17:30–17:38 UTC）DOカウンター期限切れ処理とcutover gate

- [x] Durable Objectのrate-limit rowをwindow期限後に削除するalarm処理を追加した。根拠: active windowの再予約、期限切れrow削除を含むrate-limit/handoff 8/8、Worker全体120/120。Python migration/usage/workflow 78/78、`node --check src/index.js`、`git diff --check`も成功。
- [x] 本番・GitHubのcutover前提を読み取り確認した。根拠: 17:38:02 UTCの本番WorkerはVersion `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%で、D1/R2あり・`RATE_LIMITER`なし。`main`は非保護、Environmentは`IRBANK_API_KEY`のみ、新Workflowは未公開。Queue Read tokenなし。
- [ ] 本番DO handoff、Queue backlog、main保護と`d1-r2-migration` reviewer Environment、12定義R2 seed/readback/API parity、financial_periodsの4差分・17不足の出所確認が残る。R2書込み、deploy、Actions実行は最終レビュー前に行わない。
- [ ] アカウント状態レポート: `C:\Codex\r2-d1-ownership-audit-20261105\runtime-cutover-gates-20261104T173511Z.json`（SHA-256 `6A97C9EB3956C43679AEFC195945A814E46DD014110F1A91AC5A54948E61B231`）。確認中にD1 SQL、R2 write、deploy、Actions dispatchなし。

## 2026-11-05 JST（2026-11-04 17:48–17:55 UTC）DOの一回限りD1 seed保持

- [x] Alarmでcounter rowを削除しても、actor restart後にD1を再照会しないよう`rate_limit_legacy_seed` SQLite markerを追加した。根拠: markerなしの旧actorのAlarm補修も含め、D1 import一度・期限切れcounter削除・再起動後の再利用をrate-limit/handoff 8/8、Worker全体120/120で検証。
- [x] D1 SQL契約テストを更新し、D1 schemaと別DBで実行されるDO SQLite文をD1 query listから除外した。Worker全13 D1 SQL literalとmigration schemaのcompile check成功。
- [ ] expiry後もmarkerはDOごとに1行残る。distinct bucket数の増加を含めた本番DOの保存量・無料枠は未測定。production binding/handoff前にアカウントのWorkers planと予測を確認する。
- [ ] GitHub main/Environment保護、Queue backlog、R2 definitions seed/readback/API parity、financial_periodsの4差分・17不足の正本性、本番DO handoffは未完了。R2 write、Actions dispatch、deployは承認前に実施しない。

## 2026-11-05 JST（2026-11-04 17:55–18:01 UTC）移行Workflowの資格情報・artifact境界

- [x] 手動移行Workflowの静的ポリシーテストを4件から8件へ拡張した。根拠: D1 read/Analytics/R2資格情報の分離、D1-only phaseへR2 keyを渡さないこと、usage gate順、14日artifactにreport/checkpointだけを含めること、Worker CIにdeploy手順がないことを検査。
- [x] Python migration/usage/workflow suite 82/82、Worker suite 120/120、`node --check src/index.js`成功。既存CLI testsはreport/checkpointから業務コード・raw R2 key・row body sampleを除くことを確認する。
- [ ] これらはローカル試験であり、GitHub上のWorkflowは未公開・未実行。main保護とreviewer Environmentを設定し、Queue backlogと残るデータ差異を解決する必要がある。

## 2026-11-05 JST（2026-11-04 18:08–18:10 UTC）KV権限と実装検証の再確認

- [x] Workers KV Storage Read追加後のdefinitions dry-runを再実行した。根拠: 6定義から12 R2 objectsを計画し、D1 queries/rows read 0、conflicts 0、R2 writes 0。18:08:40 UTCのAccount Analyticsは423,126/5,000,000 rows read、残り4,576,874。レポートSHA-256 2F635062EBC710C107967D8B53B1DB02EF905E839AF453C08825BB1B0141A5F5。
- [x] JDK 17でAndroid JVM unit testsを強制再実行した。根拠: testDebugUnitTest --rerun-tasks 271件成功、failures/errors/skipped 0。
- [x] Worker、migration/usage/workflow検証を再実行した。根拠: Worker suite終了コード0、Python 82/82件成功。
- [ ] R2定義12件は未seedでAPI parityは未確認。R2 write、本番deploy、GitHub Actions実行、実機install、commit/pushは未実施。

## 2026-11-05 JST（2026-11-04 18:19–18:30 UTC）公開状態とアクセスゲートの再確認

- [x] リモートmainとWorkflow公開状態を照合した。根拠: mainはb3f3a7b75bc54d30ec468fc085922791f99e340b。既存Workflowは9件で、新Worker CI・D1→R2 WorkflowはContents APIで404。GitHub上の実行なし。
- [x] main保護設定の読み取り経路を確認した。根拠: GitHub連携のbranch protection APIは403。ブラウザ確認は現在URLを十分な確度で判定できず停止し、設定値は未確認。設定変更なし。
- [x] Queue tokenの設定状態を再確認した。根拠: CLOUDFLARE_QUEUE_READ_TOKENは未設定。point-in-time backlogとoldest-message時刻は未取得のため、Queue bindingと関連D1状態を保持する。
- [x] 正本仕様用・実装用worktreeの基点を比較した。根拠: canonical HEAD 6ea575a307cb7b73c1bcf6fcd69fc77ea307a452、実装HEAD b3f3a7b75bc54d30ec468fc085922791f99e340bで、互いにancestorではない。統合操作なし。
- [ ] Queue backlog、GitHub main/Environment設定、R2定義seed/API parity、DO本番handoff、金融期間差分の正本性、本番切替は未完了。

## 2026-11-05 JST（2026-11-04 18:34–18:35 UTC）KV権限と最終スナップショット停止範囲

- [x] KV Read権限追加後のdefinitions dry-runを再確認した。根拠: 6定義から12 R2 objectsを計画し、D1 query/rows 0、conflicts 0、R2 writes 0。Account Analyticsは427,260/5,000,000行 read、残り4,572,740行。レポートは `C:\Codex\r2-d1-ownership-audit-20261105\definitions-kv-read-dry-run-20261104T183457Z.json`。
- [x] 最終移行snapshotのwriter範囲を再点検した。根拠: Cron停止だけでは認証付き `POST /api/v1/admin/run-analysis` は停止しない。リポジトリ内に呼び出し元はなく、Worker routeとテストだけを確認。仕様・runbookに外部呼び出し元の棚卸し、手動/自動要求の停止、Cron Events/Worker Logs確認と、ログの有効化・全件sampling条件を追加した。
- [ ] 外部の管理API呼び出し元、本番のin-flight状態、Worker invocation logging設定は未確認。実際の切替窓でログの全件性と静止を保証できなければ、別途レビュー済みの管理API停止手段を用意するまで最終snapshotへ進まない。Queue backlog、GitHub保護、R2 definition seed/API parity、DO本番handoff、金融期間差分の正本性も未完了。

## 2026-11-05 JST Android emulator instrumentation follow-up

- [x] JDK 17 local verification and build completed. Evidence: Android JVM tests 274/274, Worker tests 120/120, migration/usage/workflow tests 82/82, and `assembleDebug assembleDebugAndroidTest` succeeded.
- [x] Isolated API 35 instrumentation passed 20/20 cases. Evidence: `NotificationDeliveryTest`, `ReleaseNotificationPlatformTest`, `IncomeAdviceCatalogCacheTest` passed 16/16; selected 12-hour gate, retry and due-startup cases passed 4/4. `emulator-5566` installed app APK SHA-256 matches the build (`43A0CC9E54470755366F066FCB7C0F447EE7CA3BF111816F84DE4350971AADDC`).
- [x] Full 137-case Android instrumentation is now verified. Evidence: after the earlier high-load run was inconclusive, `ReleaseFixtureInstrumentation` completed `OK (137 tests)` on the task-only API 35 AVD `emulator-5580` with JDK 17; app/test APK SHA-256 values matched the installed APKs, and `emulator-5554` kept its pre-test install timestamp. The Gradle connected task failed before install in device filtering, so the same built APKs were installed and run through ADB. Report: `C:\Codex\android-avd-r2-d1-ownership-20261105\android-validation-summary.json` (SHA-256 `BD07BFD8656D631D470B1AA07452E91B0CF5912A34A544E882B928AF4F3A3753`).
- [ ] Investigate the separate Gradle `connectedDebugAndroidTest` device-filter exception. The app/test behavior is verified by the same APKs passing all 137 cases through ADB on the isolated API 35 AVD; the Gradle task itself remains unresolved.
- [x] Emulator cleanup completed. Evidence: temporary user 10 was removed after switching to user 0; only user 0 remains, and its installed app APK SHA-256 matches the saved pretest APK (`939B578CEF0D715702640CEB582D3016A75977E7495C5CC1D077CAC81C758B80`). Physical device was not used.
- [x] The new isolated emulator was shut down after testing. Evidence: `adb devices` listed only the pre-existing `RFCNC0E4H8J` and `emulator-5554`; neither was targeted.


## 2026-11-05 JST（2026-11-04 19:26 UTC）KV Read再確認と金融期間差異追跡

- [x] 追加されたWorkers KV Storage Read権限で`definitions` dry-runを再実行した。根拠: 6定義から12 R2 objects planned、D1 queries/rows read 0、conflicts 0、R2 writes 0。全12 objectは未作成のまま。report SHA-256 `f9202d7c355650bb7afb2a2da34f91ccbec73bc58541984ade3a91c873000008`。
- [x] Account Analyticsで日次使用量を確認した。根拠: 19:26:28 UTC、3 DB合計431,354/5,000,000 rows read、残り4,568,646。dry-run直前の予備枠100,000行を満たした。
- [x] 2708 FY2014/03のsource discrepancyを元資料の主要項目と単位候補で再比較した。根拠: private detailの一致フラグでは、D1のsales/operating_profitはmillion JPY換算で一致し、R2はEPSのみ一致。その他の確認項目は今回試したJPY/million/thousand単位で一致せず、差異は未解決。生値は記録へ転記していない。
- [ ] 2708 FY2014/03の値・field mappingと、同社の3月期に対するD1の10月期行の扱いは未解決。根拠・期間対応・保持要件が確定するまで該当D1行を削除せず、未検証値をR2へ移さない。
- [ ] `CLOUDFLARE_QUEUE_READ_TOKEN`は未設定のまま。Queue backlog/oldest-messageのpoint-in-time確認、定義R2 seed/readback/API parity、GitHub保護、DO handoff、本番cutoverは未完了。

## 2026-11-05 JST（2026-11-04 20:35 UTC）KV Read再確認とDO rollback bridge準備

- [x] 空の一時checkpointから`definitions` dry-runを再実行した。根拠: KV定義6件、R2 object 12件を計画、D1 queries/rows read 0、R2 writes 0、conflicts 0。20:35:07 UTCに3 DB合計435,464/5,000,000 rows read、残り4,564,536。report SHA-256 `41656ad40401097421e3e14eaa6b392a37308dd60b66d4fcbe08dde6ed0bee61`。
- [x] `codex/rate-limit-do-bridge-20261105` worktreeに旧D1経路を維持するDO limiter bridgeを準備した。根拠: `api_request_log`は初回seed時のSELECTだけ、Cron削除・D1 UPSERTなし。Worker 105/105、`node --check`、`git diff --check`、Wrangler 4.136.1の通常/cron-paused両config dry-run成功。
- [x] migration runbookへbridge先行deploy、DO利用後は同じDO class/migrationを使うbridgeへrollback、bridge中のD1変更をR2復帰前に照合する手順を追加した。
- [ ] Bridgeはlocal uncommittedで未公開・未deploy。production Worker sourceとbase SHAの一致、temporary D1でのfence SQL確認、Cloudflare deploy権限と本番切替は未確認。
- [ ] `CLOUDFLARE_QUEUE_READ_TOKEN`は未設定。Queue backlog/oldest-message確認、R2 definition seed/readback/API parity、GitHub protection/Environment、金融期間差異、DO production handoffは未完了。R2書込、Actions dispatch、deploy、commit/pushは未実施。

## 2026-11-05 JST（2026-11-04 20:51 UTC）GitHub remote状態確認

- [x] GitHub `main`とWorkflow公開状態を読み取り確認した。根拠: `main` SHAは`b3f3a7b75bc54d30ec468fc085922791f99e340b`で実装worktree HEADと一致。branch metadataは`protected=false`、repository rulesetsは空。Workflowは9件で、Worker CIと移行Workflowは未公開。
- [x] GitHub保護設定とEnvironment確認の到達範囲を記録した。根拠: branch-protection詳細endpointは403、Environment一覧endpointは連携allowlistで拒否、`gh` CLIなし、ブラウザURLの確証が取れず停止。Environment設定は不明であり、未作成とは断定しない。
- [x] Cloudflare tokenの設定有無を秘密値を表示せず確認した。根拠: API tokenとmigration read tokenは設定済み、Queue Read tokenとWorkers Scripts Read tokenは未設定。
- [ ] Queue backlog/oldest-message、本番Worker versionとsourceの対応、GitHub Environmentのreviewer/branch policy/secrets、R2 definition seed/API parity、DO production handoff、financial period差異、本番cutoverは未完了。R2書込、Actions dispatch、deploy、GitHub設定変更、commit/pushは未実施。

## 2026-11-05 JST（2026-11-04 20:54 UTC）KV Read権限追加後の再検証

- [x] 追加後のKV Read権限で`definitions` dry-runを再実行した。根拠: complete、12 objects planned、D1 queries/rows read 0、conflicts 0、R2 writes 0。20:54:54 UTCのAccount Analyticsは435,464/5,000,000行、残り4,564,536行。レポートSHA-256 `27566c261bcdf79b20e19d3c7884e791dd42bc9b7846783d839b0640c5963660`。
- [ ] R2へのdefinition seed/readbackとAPI parityは未実施。本番write、Workflow dispatch、Worker deploy、commit/pushは行っていない。
- [x] 実装用worktreeの仕様コピーを正本と同期した。根拠: 最新監査追記後も両ファイルのSHA-256が`043E4A30BD564EB544D42F4782981097AB2EB4D77198B1DA2079FF69E65AA369`で一致。

## 2026-11-05 JST（2026-11-04 21:01–21:05 UTC）本番D1 operational inventory再確認

- [x] production schema auditを再実行した。根拠: 27 objects（tables 25、triggers 2）、D1 meta.rows_read 92。report file SHA-256 `B8A29E21AF851652598B324DCB840F3854CBC084C16C5D5EDAF2E46493678E35`。
- [x] operational auditを4表で完了した。根拠: `backend_state` 0行、`scheduled_run_log` 627行（すべてrunning・finished_atなし）、price-refresh run 2行（partial/running）、batch 2,080行（complete 2,020・failed 60）。D1 meta.rows_read合計2,710、R2 writes 0。
- [x] Account Analyticsを再取得し、今回の直接監査読取と増分が一致することを確認した。根拠: 21:05:22 UTCで438,274/5,000,000、残り4,561,726行。21:02 preflight 435,472から2,802増。
- [ ] 前回snapshotから`scheduled_run_log`が621行から627行へ増え、開始時刻が20:17 UTCまで続く。Cron起動行のwriter/source version、runningの意味、in-flight callerを確認する。627行を完了履歴へ移さず、D1/Queueデータを保持する。
- [ ] Queue backlog/oldest-message、本番Worker versionとsourceの対応、進行中runの復旧・保持方針は未確認。削除、R2 archive、Worker deploy、Actions dispatchは行わない。

## 2026-11-05 JST（2026-11-04 21:09–21:27 UTC）移行ハッシュと使用量再確認

- [x] `scheduled-runs`対象を再評価した。根拠: 627行を走査したが`source_rows=0`、R2 objects planned/written 0で完了。実行中・finished_atなしの行を完了履歴として移行しない。
- [x] Account Analyticsを再確認した。根拠: 21:24:12 UTCに3 DB合計443,027/5,000,000 rows、残り4,556,973。21:05:22の値から4,753増えたが、scheduled-runsで計測した627行以外の読取元は未特定。
- [x] 移行CLIに再開可能なwrite-set SHA-256を追加した。根拠: `rolling-sha256-v1`をcheckpoint/reportへ保存し、旧checkpointで過去の出力件数があるのにdigestが欠ける場合はhash incompleteとする。migration tests 64/64、Python suite 84/84。
- [x] KV Read権限で定義dry-runと出力hashを確認した。根拠: 6定義・12 object、D1 query/rows 0、conflicts 0、R2 writes 0、hash complete。write-set SHA-256 `f1b9dc415b9858deb49ec71473d532e96f873ba8d87578eaa83c7cbec44b08b4`。空のfixture namespaceでdry-run/write/readbackのhash一致、raw key/body不出力をテストした。
- [x] 運用runbookを更新し、21:01以降の監査記録を時系列順にした。実装worktreeと仕様worktreeのrunbookを同期した。
- [x] repository `origin/main` のscheduled-run writerを照合した。根拠: commit `b3f3a7b75bc54d30ec468fc085922791f99e340b` のCronは`17 * * * *`で、開始時にrunning行をinsertし、正常・catch経路で終了statusをupdateする。market-price run/batchにもD1 insert/updateがある。
- [ ] productionの配信Versionとsourceの対応、627件が完了しない理由、in-flight管理API caller、Queue backlogは未確認。627件を完了履歴としてR2へ移さず、D1/Queueの保持・復旧要件を調べる。
- [ ] R2 definitions seed/readback/API parity、GitHub main/Environment保護、production DO handoff、financial-period差分の正本性、本番cutoverは未完了。今回R2 write、Actions dispatch、Worker deploy、physical install、commit/pushは行っていない。

## 2026-11-05 JST（2026-11-04 21:47–22:00 UTC）KV権限・API・本番bindingの再確認

- [x] 追加されたWorkers KV Storage Read権限で`definitions` dry-runを再実行した。根拠: 6定義から12 objects、D1 query/rows 0、conflicts 0、R2 writes 0、write-set hash complete (`f1b9dc415b9858deb49ec71473d532e96f873ba8d87578eaa83c7cbec44b08b4`)。report file SHA-256 `E1F8BD99AB3F3825153A6D7DF010C193C4A402E8CC1021094CF083BD8B0B26EA`。
- [x] 現行R2をbranch Workerのread-only API経由で確認した。根拠: catalog 4,391 unique、7203検索1件、income候補3,203 uniqueで全件catalog内、価格取得200。分析・定義・healthは定義12 objects未seedのため503 `definitions_unavailable`。API/世代/件数のreadbackであり全行値のparityは未証明。report SHA-256 `4D228EE4F89A1E09B7E8F284DE8A41DD6032B4FE2590B416599EEEE5BC49E1AE`。
- [x] 履歴全件の新形式hash付きdry-runを完了した。根拠: 38,156 rows/77 D1 queries、38,156 objects planned、zero writes/conflicts、source hash `77ce0b1be6daa99c8b44209964af7ca5e763201da67dbc922ed763696647f7f0`、write-set hash `42e785f735c9b8b285d447aa8f74c2e2730cf7eafd463b2c66ca5bf0fb517c27`。21:53:11 UTCのAnalyticsは481,191/5,000,000、残り4,518,809で、履歴38,156読取分と一致した。次のD1 query前に再確認する。
- [x] 本番Workerの最新version/bindingを読み取り確認した。根拠: version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` が100%、D1・KV・R2・Queue bindingあり、`RATE_LIMITER`なし。version message/timeはcommit `f2f2d831e9e82b0567e56127446706fed764e02d`に一致するが、commit SHA/source hashはCloudflare metadataになく、mainの祖先でもないためsource mappingは未確定。report SHA-256 `94AA21011AB522385EB1A46F6392F05C39551ACD911B32A0EA5F9E23B13B4600`。
- [x] Queue backlogをpoint-in-time read-onlyで取得した。根拠: `diview-market-prices`とDLQともbacklog 0 messages/0 bytes、oldest timestampなし。messagesのread/send/consumeなし。Cloudflare metricsはbest-effortのためcutover直前に再確認する。report SHA-256 `B6B307FFD1B907ABA8CD240B7A1C325A8D9310DE6D3E458D4D631D1CFEAB912F`。
- [ ] 12 definition objectsは未seedで、analysis/healthは503。seed後のR2 readback、分析・定義API parity、最終snapshotとD1 usage、Worker/Queue状態の再確認は未完了。
- [ ] 本番Workerのsource SHA確定、GitHub main protection/Environmentと未公開Workflowの承認・実行、DOの本番handoff、financial-period差異の正本性、cutover reviewは未完了。production R2 write、deploy、Actions dispatch、D1削除、commit/pushは未実施。

## 2026-11-05 JST（2026-11-04 22:10–22:17 UTC）本番D1索引・migration ledger確認

- [x] 本番`securities`の現在の索引とlookup planを読取確認した。根拠: `MULTI-INDEX OR`でsecurity_code primary-key indexと`idx_securities_code_display`を使用し、実際の7203 lookupは1行返却・`meta.rows_read=1`・write 0。report SHA-256 `3FBEFA26921C5196BCC879598EFD04CCD267FD49A4625E74B347AE55DF09F0B4`。
- [x] 0008 index migrationを実装worktreeとcanonical schemaへ追加し、回帰テストを加えた。根拠: `node --test test/d1.test.cjs` 13/13。共有`C:\Codex\Diview` checkoutは編集していない。
- [x] remote migration ledgerを読取確認した。根拠: `0006_feedback_email.sql`、`0007_financial_period_key_guard.sql`、`0008_securities_code_display_index.sql`がpending。physical 0008 indexは存在するがledgerは未記録。migrationは実行していない。
- [ ] Nov 3 dashboardの7.01M/1,783回は現在のindexed lookupでは説明できない。過去の集計時点/実行版と、定期分析の全銘柄SELECT・履歴SELECTを区別して原因を確定する。
- [ ] 0006–0008のpending ledgerを、0006/0007の副作用をレビューしてから解消する。直前のAnalyticsは481,273/5,000,000だが直近D1 read metadataと未reconcileのため、次のSQL前に再取得する。

- [x] pending migration ledgerを実体schemaと照合した。根拠: 21:01 UTC schema auditでsupport_feedback_email表とinsert/delete triggerは存在、0007のfinancial_periods key guard triggerは不在。0006/0008は物理適用済みだがledger未記録の可能性があり、0007は未適用。production migration実行はしていない。
- [ ] 0006/0008の適用記録と0007の制約追加を分けてレビューし、financial_periodsの既存書込契約を確認してからmigration ledgerを整合させる。

## 2026-11-05 JST（2026-11-04 22:26 UTC）Worker source hash照合・認証変数の可視性

- [x] 本番Worker metadataと候補ソースのローカルbundle hashを比較した。根拠: 本番script ETag f30d7b9ba048e43219856b14e1ff5cc7aeb5b502b76683be245527932036f1a9、commit f2f2d831e9e82b0567e56127446706fed764e02dからのWrangler 4.157.0 dry-run bundle SHA-256 262388D871931F4321BB3946937CD9FAA2E72313297EF51EF60F4629BEA4AD9A。表現・ETag算出法が一致している証拠はなく、完全なsource identityは未証明。report SHA-256 4B3A675D898CBEFE8D53FDCCD95A6EE6190038E6B8571B5F108BC6AA935D8D33。
- [x] ユーザーからWorkers Scripts Read/Queues Read token設定完了の連絡を受けた。確認時に値は表示せず、CodexプロセスおよびWindows User/Machine環境変数からは両方とも参照できなかった。Worker version/Queue metricsの読み取りは既存Wrangler認証で完了しており、追加tokenの利用確認とは区別する。
- [ ] 正確な本番Worker source mapping、Queue/R2/API cutover条件、DO handoff、GitHub保護、financial_periods差分、最終snapshot/usage確認は未完了。production write/deploy/Actions dispatch/commit/pushは未実施。
## 2026-11-05 JST（2026-11-04 22:32 UTC）Account Analytics再取得

- [x] UTC日次使用量をAnalytics APIで読み直した。根拠: 2026-11-04の3 DB合計485,372 rows read、4,246 rows written、残り4,514,628行。100,000行reserveを維持。D1 SQLは発行していない。report SHA-256 3326FB61EB8642A5515D3864A65BA8553A6D6313F776EF3D2D6CD903852E7A18。
- [ ] 22:17:29の481,273行からの4,099行増加は、22:17以降この監査でD1 SQLを発行しておらず帰属不明。遅延集計または他のアカウント処理の可能性があるため、監査へ計上せず、次のD1 SQL直前に再確認する。
## 2026-11-05 JST（2026-11-04 22:55 UTC）読み取り専用preflight再確認

- [x] Account Analyticsを更新した。根拠: 3 DB合計485,372 rows read / 4,246 rows written、残り4,514,628行、100,000行reserve維持。22:32 snapshotから変化なし。GraphQL AnalyticsのみでD1 SQLなし。出力SHA-256 `E3FE2A1B3EE58FEEABB504815AC61E8B913AFF462B7D89C7512B70AC80B0B11F`。
- [x] Wrangler OAuthでdeployment一覧をread-only確認した。根拠: 最新deployment/versionは前回と同じ、`7731b2d6-75e8-4b13-8985-9f47a4960f95` / `4981c1e0-e337-4392-9f5b-909b52c5bc0c`、100%。source commit SHAは未確定。
- [ ] ユーザーはWorkers/Queues read token設定済みと連絡したが、現在のCodex ProcessおよびWindows User/Machine環境変数からは両方とも見えない。値は表示していない。新tokenでのQueue backlog再確認とWorker source readは未実施。Codexを再起動して環境を読み直すか、現在のCodexから参照できる形で設定後に続行する。
- [ ] Queue snapshot、正確な本番Worker source mapping、R2 definitions seed/readback/API parity、GitHub保護、DO handoff、financial-period差分、最終cutover reviewは未完了。今回D1 SQL/R2 write/Worker invocation・deploy/Actions dispatch/commit/pushは未実施。
## 2026-11-05 JST（2026-11-04 22:57 UTC）候補Workerの残存D1経路を分類

- [x] ownership候補WorkerのD1参照を分類した。根拠: 銘柄検索・分析・履歴・定義・定期分析のD1 market-data readはなく、共有レート制限DOの初回bucket import readと、support feedback/email outboxのtransactional read/write・retentionだけが残る。healthはbinding有無の表示のみ。既存WorkerテストはR2-backed GETとscheduled analysisがD1を使わないことを確認する。
- [ ] 本番Workerの正確なsource identityとDO handoffは未確認。この分類はcandidate sourceの説明であり、本番切替の証明ではない。D1 limiter table/bindingはDO移行が安全に完了するまで保持する。
## 2026-11-05 JST（2026-11-04 23:05 UTC）GitHub設定とWorker source取得経路

- [x] GitHub設定を画面でread-only確認した。根拠: `main`のBranch settingsに既存protection rule/rulesetは表示されず、branch metadataは`protected=false`、rulesets APIは空。Environment一覧は`IRBANK_API_KEY`のみで、`d1-r2-migration`は存在しない。設定変更・secret閲覧・Workflow実行なし。
- [x] Cloudflare version detail応答にsource modulesが含まれないことを確認し、公式version-specific API endpointを特定した。Workers Scripts Read tokenが実行環境で見えるようになったら、対象versionの`?include=modules`をread-only取得してlocal candidateとの対応を確認する。
- [x] 最新deployment一覧は前回から変わらず、deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95` / Version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%。Wrangler version detailだけではmodule/source SHAを得られず、正確なsource mappingは未確定。
- [ ] `CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`はユーザーが設定したと連絡したが、Codex Process/Windows User/Machineからは参照できない。Queue metricsとversion modulesの取得は未実施。secret値をチャットへ求めず、環境に見えるようになった後に読み取る。
- [ ] Environment作成・protection/secrets設定、main保護、Workflow公開、12 R2 definitions seed/readback/API parity、Queue状態、DO handoff、financial_periods差分の正本性は未解決。今回は外部設定変更、R2書込、Actions dispatch、deploy、D1 SQL、commit/pushなし。
## 2026-11-05 JST（2026-11-04 23:19 UTC）本番レート制限の読み取り確認

- [x] 現在の60秒レート制限窓と実行計画を確認した。根拠: 23:18:19 UTCの限定D1 queryはactive bucket 0件・meta.rows_read=1。EXPLAINはidx_api_request_log_windowを使い、meta.rows_read=0。
- [x] 最新のAccount Analytics予算をクエリ直前に確認した。根拠: 485,380/5,000,000行、残り4,514,620行。100,000行reserve維持。
- [x] 本番rate-limit設定をremote KVから読み、checked-in defaultsと照合した。根拠: window 60秒、register 10、authenticated 120、public 300、market stale 3日で一致。
- [ ] Workers Scripts Read/Queues Read tokenは設定連絡を受けたが、Codex Process/User/Machineの環境変数にはまだ見えない。version modulesとQueue metricsを取得できていない。値はチャットに求めず、Codex再起動後に確認する。
- [ ] 本番Worker source mapping、Queue backlog、DO handoff、12 R2 definitions、GitHub保護/Environment、financial_periods差分の正本性は未解決。今回の操作はread-onlyで、本番書込・dispatch・deploy・commit/pushなし。

## 2026-11-05 JST（2026-11-04 23:22 UTC）D1使用量の未帰属増分

- [x] 限定D1 query後にAccount Analyticsを再取得した。根拠: 23:22:15 UTCで489,507 rows read、4,444 rows written、残り4,510,493行。
- [ ] 23:19:23 UTCの485,380行から4,127行増えたが、今回の限定queryのmeta.rows_readは1、EXPLAINは0。少なくとも4,126行の増加をこの監査には帰属できない。Analytics反映遅延か別利用かは未確定。次回D1 SQL前に再確認する。
- [ ] Worker version modulesとQueue metricsは未取得。CLOUDFLARE_WORKERS_READ_TOKENとCLOUDFLARE_QUEUE_READ_TOKENは設定連絡後も実行環境に見えない。R2 seed、deploy、Actions dispatch、commit/pushは未実施。

## 2026-11-05 JST（2026-11-04 23:57 UTC）Analyticsとread token環境確認

- [x] Account Analyticsを再確認した。根拠: 489,619/5,000,000 rows read、4,529 rows written、残り4,510,381行。100,000行reserveあり。report SHA-256 `44E6C7B07C26BFC60A8AB4A198E3D58919597DC66AD8E77288DC12024DA3DCA6`。この確認でD1 SQLは実行していない。
- [ ] 23:22:15 UTCの489,507行から112行増加した出所は未特定。Analytics反映遅延または別利用の可能性を残す。
- [ ] ユーザーの設定連絡後も `CLOUDFLARE_WORKERS_READ_TOKEN` と `CLOUDFLARE_QUEUE_READ_TOKEN` はProcess/User/Machineの全scopeで不在。値をチャットに求めない。Codex再起動後またはUser scopeへ登録後に再確認し、Worker version modulesとQueue point-in-time metricsを取得する。


## 2026-11-05 JST（2026-11-05 00:07–00:35 UTC）financial_periods再監査

- [x] annual financial_periodsの移行対象を再監査した。根拠: 2 dry-runs各49,592 source rows / 99,184 D1 rows read、49,571一致・17不足・4差異、R2 writes 0。実レポートのSHA-256はspec記載の4ファイルを参照。
- [ ] 1301の2025/03・2026/03を出所・単位・範囲・会計基準確認後にR2 overview/V2へ準備し、差分検証とreadbackを行う。現在のverified backfillは2008/03–2024/03まで。
- [ ] 1301の2017/03・2023/03・2024/03と2708の2014/03の値差異、2708の2011/10–2025/10の15期間の出所を解決する。未確認行はD1に保持し、R2へ移さない。
- [ ] Workers Scripts/Queue read tokenはユーザーが設定済みと連絡したが、現在のCodex ProcessおよびWindows User/Machine環境からは検出されない。値を出さず、Codex起動後に再確認してWorker modulesとQueue metricsを取得する。


## 2026-11-05 JST（2026-11-05 00:46–01:15 UTC）価格更新ゲートの再確認

- [x] 日次の共通起動抑止後も、前回価格試行が記録されていれば価格専用ゲートを再判定する。根拠: `MarketFetchPolicy.kt` と `DividendViewModel.kt` の候補実装に失敗30秒再試行・成功12時間/JST日ゲート・記録欠損時の日次抑止を実装し、JDK 17 JVM 276/276、`AssetDataRefreshTest` 21/21が成功。
- [x] Androidのビルド・検証用AVDを確認した。根拠: `assembleDebug assembleDebugAndroidTest`成功、APK/base.apk SHA-256一致（app `a942ef1897da56eab4f37f3450fa264d09291d3c3c7c827fb473dd1906dc27b4`、test `1ecbbf654549c8409cbad8d9fef7b8ad30b9a5284e889cb5e8564b076ad80bb9`）。検証専用AVDを終了し、共有AVD・物理端末には触れていない。

## 2026-11-05 JST（2026-11-05 01:24 UTC）migration preflight testの日付境界

- [x] UTC日付が変わっても移行予算preflightテストが安定するよう、テスト内で `migration.datetime.now()` を固定した。根拠: candidateのPython 3.13でmigration/workflow/daily-usage suite 84/84成功。
- [ ] CI指定のPython 3.12ではまだ未実行。CI結果で確認する。
- [ ] Workers/Queue tokenは設定連絡後もProcess/User/Machine環境に見えない（01:24 UTC確認）。Worker version modulesとQueue metricsは未取得。

## 2026-11-05 JST（2026-11-05 01:28 UTC）Account Analytics再確認

- [x] D1 account-wide usageをGraphQL Analyticsで確認した。根拠: 206,643/5,000,000 rows read、398 rows written、残り4,793,357行、100,000行reserve充足。今回D1 SQLは実行していない。
- [ ] 00:35 UTCの202,509から4,134行増加した出所は未特定。Worker/Queue read tokenが実行環境に見えた後、配信Worker sourceとQueue metricsを確認する。

## 2026-11-05 JST（2026-11-05 01:30 UTC）GitHub Actions移行ゲートの静的レビュー

- [x] 手動移行workflowの停止条件と権限をレビューした。根拠: workflow_dispatch限定、main保護・reviewer付きmigration Environment・main専用branch policyを事前確認し、R2書込には `WRITE_R2` を要求、日次D1予算をSQL前に確認。CIはdeployしない。関連suite 84/84成功。
- [ ] GitHub上のCI実行結果は未取得。前回のremote確認でmain protectionとmigration Environmentが存在しなかったため、workflowは現状fail-closed。公開前の設定反映とCI実行が必要。

## 2026-11-05 JST（2026-11-05 01:33 UTC）GitHub migration前提のread-only再確認

- [x] GitHub REST APIでmainとEnvironmentを再取得した。根拠: main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`、Environmentは`IRBANK_API_KEY`のみで`d1-r2-migration`なし。現在のmainにmigration workflowも未掲載（404）。
- [ ] Branch protection、reviewer付きEnvironment/branch policy、workflow公開、Actions CI実行が未完了。環境設定やGitHubへの書込は行っていない。

## 2026-11-05 JST（2026-11-05 01:34 UTC）候補WorkerのD1参照再点検

- [x] Worker本体とfeedback/email moduleを再検索し、候補コードのD1呼び出しを分類した。根拠: 市場API、分析、定義、scheduled processingはR2経路。D1はRateLimitCounterの初回レガシーcounter import readと、support-feedback/emailのtransactional read/write・retentionに限定される。`appDefinitionDocument`はR2定義読取。
- [ ] 候補コードの分類は配信本番versionの証明ではない。Cloudflare Worker source mapping、DO本番binding/handoff、Queue稼働状態は実環境からのread token待ち。

## 2026-11-05 JST（2026-11-05 01:44 UTC）旧D1定義seedの実行防止

- [x] 旧D1 seed SQLを `.sql.disabled` へ移し、元ファイルのSHA-256 `A6F95AC61F9354C773CD0F224A10B7F33BEC7843500895BAE9BBD562CB268ED4` が候補・仕様worktree双方で一致することを確認した。
- [x] README/specを更新し、旧seed、D1 market importer、financial repair適用経路を実行しない方針を明記した。回帰テストをmigration workflow suiteへ追加し、3 Python suite 86/86成功。
- [ ] Actions CI上のPython 3.12実行は未確認。

## 2026-11-05 JST（financial_periods出典の照合）

- [x] 1301の2025/03・2026/03について、IRBANK raw annual batchのE00012行が保存済みsampleおよび会社発表と矛盾することを確認した。根拠: `batch_0000.json` は売上7,957/8,283百万円・総資産7,126/6,973百万円だが、`financials_13010.json` と[極洋の2026年3月期決算短信](https://www.kyokuyo.co.jp/files/20260501.pdf)は売上302,681/334,612百万円・総資産182,125/214,128百万円。batch行は移行元として使用しない。
- [ ] annual batch行の生成・EDINET対応付けの出所を特定し、sample/API値の単位・連結範囲・自己資本計算項目を照合するまで1301の2025/03・2026/03をR2へ書かない。現行backfill helperは2008/03–2024/03を対象にし、`owners_equity`を必須としている。
- [ ] `CLOUDFLARE_WORKERS_READ_TOKEN` と `CLOUDFLARE_QUEUE_READ_TOKEN` は設定連絡を受けたが、2026-11-05 02:29 UTCの確認でもCodex Process/User/Machine環境に見えない。値は表示していない。Codex再起動または起動元環境への反映後にWorker version modulesとQueue live metricsを取得する。

## 2026-11-05 JST（DOレート制限D1引継ぎを期限付きに修正）

- [x] 最終Worker候補とrollback bridgeで、legacy counter importをUnix秒のD1 fence/import deadlineで制限した。根拠: 変数をすべての有効リクエストで保存済みstateより先に検証し、欠落・不正・未来fence・短い期限はfail closed。既存counter rowとcleanup済みseed markerでも未来fenceを拒否する回帰テストを追加。Worker全体127/127と111/111、Wrangler 4.136.1の最終/通常bridge/Cron停止bridge dry-runが成功。
- [ ] 本番cutoverではD1 fenceを確認後に最大window + 60秒余裕の時刻を作り、同じ2変数をbridge・最終Worker・rollbackに渡し、429動作と期限後のD1非参照を確認する。Worker/Queue read tokenは設定連絡を受けたが、2026-11-05 02:29 UTC時点でこのCodex Process/User/Machine環境に見えず、配信版とQueue backlogは未確認。

## 2026-11-05 JST（本番D1運用状態とR2カタログの再読取）

- [x] 02:50 UTCにAccount AnalyticsでUTC日使用量を確認した。根拠: 210,753/5,000,000行、残り4,789,247。対象D1は210,706行、別DBは47行。02:36以降のmigration監査の直接D1 `meta.rows_read` は合計732だがAnalytics表示は増えておらず、失敗QueryとWorker HTTP GETの行読取量も不明。正確な監査後合計は未照合。
- [x] 本番D1 schemaと小さい運用inventoryをread-onlyで確認した。根拠: 27 schema objects（25 table、support-feedback trigger 2）、handoff write-fence triggerなし、`backend_state` 0行。schema report `d1-schema-readonly-20261105T0236Z.report.json` SHA-256 `8EABB2B2FB678B888A49DC1C71D016A022A633191995CA3845930AD62E4B4B46`、backend-state report SHA-256 `DD083325D946EE7D52B9928508A3F99B09B0B49AB153336E897F2584AC55F520`。
- [x] `scheduled_run_log`の全件再監査を完了した。根拠: 633行すべて`running`、`finished_at`なし、processed/failed 0、最新開始 `2026-11-05T02:17:32.819Z`。前日監査より6件増えた。completed-run移行queryは`finished_at IS NOT NULL`なので対象0件。report SHA-256 `C386A11AAEA4232E4B8F24B65C233B1FE5327284F8439AB339EC32AA026C9FC8`。
- [x] `market_price_refresh_runs`の2行と最新R2 catalog/APIの代表値を照合した。根拠: 10/10 partial・10/12 runningの2 run。R2 currentとgeneration objectは同SHA、generation `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`、4,391件。API run_id/dateと7203の¥3,051（2026-11-02）が一致。readback report SHA-256 `84294E6FE195C69D5377677A188EF3A77A2A98C90605F36E5FEC7FAE4B7E36F1`。
- [ ] 633件の未完了analysis logの原因と新規作成元をWorker source modules、Cron event、Worker Logsで確認する。設定連絡を受けた`CLOUDFLARE_WORKERS_READ_TOKEN`/`CLOUDFLARE_QUEUE_READ_TOKEN`は02:50 UTC時点でProcess/User/Machineに見えず、Queue backlogとsource identityも未確認。完了済みとして移行せず、ログを削除しない。
- [ ] Account Analyticsが監査前値から増えていない。次のD1 SQL前にUTC日使用量を再取得し、失敗Query/Worker GET分を含む現在値を照合する。今回の専用migration query以外（health/live-quotes）のCloudflare `meta.rows_read`は得ていない。
- [ ] 本番D1 write fenceは未設定。Queue backlog、external producer、未完了run、旧通知データ・backup・retentionを確認するまでD1データを削除しない。Worker version modules、全カタログAPI parity、12定義のR2 seed/API parity、DO実使用量も未確認。

## 2026-11-05 JST（Analytics再確認と定期runコード監査、03:06 UTC）

- [x] Account Analyticsを再確認した。根拠: 03:06 UTCに211,495/5,000,000 reads、残り4,788,505。対象D1 211,440、別DB 55。02:54から742行増えたが、この更新でD1 SQLは実行しておらず、増加元は未特定。report SHA-256 `40D1B4D0AD0801873D0523137664AA93094BF3E3A89A8B6A6676FEEEF401631F`。
- [x] ローカル`origin/main`の定期run処理を確認した。根拠: `running`行のinsert後、正常終了とcatchされた例外でfinished statusへ更新し、Cronは毎時17分。本番配信version modulesとWorker Logsは読めていないため、633件の原因は未確定。
- [ ] 633件の未完了analysis logの本番原因と新規作成元を配信Worker source modules、Cron event、Worker Logsで確認する。専用`CLOUDFLARE_WORKERS_READ_TOKEN`/`CLOUDFLARE_QUEUE_READ_TOKEN`はProcess/User/Machine環境に見えない。Account Analytics tokenを使ったWorker modules・Queue一覧APIはHTTP 403で、Queue backlogとproduction source identityは未確認。完了済みとして移行せず、ログを削除しない。
- [ ] 次にD1 SQLを実行する前にAccount Analyticsを再取得し、監査SQLやWorker HTTP GETの行数とAccount Analyticsを照合する。health/live-quotes HTTP要求のCloudflare `meta.rows_read`は未取得で、監査後のaccount-wide usageを完全照合できていない。

## 2026-11-05 JST（Android全体試験とcutover gate再確認、04:31 UTC）

- [x] JDK 17で候補APKを隔離AVD `emulator-5580` に導入し、全Android instrumentation 138件が成功した。根拠: `JreitStockDetailTest` と `PortfolioScreenTest` を含む全suiteが `OK (138 tests)`、構築APKと端末 `base.apk` のSHA-256は `A942EF1897DA56EAB4F37F3450FA264D09291D3C3C7C827FB473DD1906DC27B4` で一致。実機は未確認。
- [x] Account AnalyticsのUTC日使用量を更新した。根拠: 04:31:22 UTCに219,981/5,000,000 reads、残り4,780,019、1,205 writes。対象D1は219,867 reads、残り2 DB合計114 reads。今回D1 SQLは発行していない。
- [x] GitHub main保護状態を公開REST APIで再確認した。根拠: `masacgt/Diview` はpublic、mainはSHA `b3f3a7b75bc54d30ec468fc085922791f99e340b` で `protected=false`。候補workflowはローカル変更のままで、GitHub設定、workflow公開・dispatchは未実施。
- [ ] ユーザーはWorker/Queue read tokenを設定済みと連絡したが、04:30 UTCの値なし環境確認では両変数ともCodex Process/User/Machineに存在しなかった。値は出力していない。新しいCodexプロセスから認識できる状態でWorker source modules、Queue live metrics、Worker Logsを取得する。
- [ ] main保護、独立reviewer付き `d1-r2-migration` Environment、workflow CI、Worker/Queue実測、D1 write fence、期限付きDO counter handoff、未完了runの原因と保持方針が未完了。これらと残る金融期間差分の正本性が確認されるまでproduction write/deploy/dispatchは保留する。

## 2026-11-05 JST（カタログ照合と移行dry-run再監査、04:58 UTC）

- [x] read-only dry-runを再実行した。根拠: market-audit 3,960行、analysis-current 248行/248 object計画、definition-audit 2行/2 object計画、2つの市場source auditは各0 source row、analysis-sourceは0行。6レポートの rows_read 合計4,212、R2 writesは全て0。各 report_sha256 はmigration runbookに記録した。
- [x] 銘柄検索用R2カタログのコードcoverageを本番read-onlyで照合した。根拠: D1 securities 3,960行/3,960 unique display codes、YFinanceカタログ4,391件、IRBANKカタログ3,963件。D1のYFinance欠落65件はIRBANKに全て存在し、両カタログにないコードは0件。D1 query meta.rows_read=3,960、R2/D1 writes 0。report C:/Codex/r2-d1-ownership-audit-20261105/security-catalog-coverage-20261105T0456Z.report.json SHA-256 57d5229a1a54dc4c68c1079ee2fcfefc846db3a8d4465e0daa9641acc4825827。
- [x] candidate security-searchをR2 YFinance catalogに加えてIRBANK catalogのcode/nameでも検索し、IRBANK-only結果に株価・利回りを捏造せずnullを返す実装を確認した。根拠: route suite 11/11、Worker suite 128/128成功。D1 fallbackなし。この実装は未deploy。
- [x] Account Analyticsを更新した。根拠: 04:55:47 UTCにaccount-wide 224,245 reads/1,426 writes、残り4,775,755。対象D1は224,079 reads。report d1-daily-usage-20261105T045547Z.json SHA-256 CAA495B7C1A16EE9841C94C751CC9549851C4899199733C495C97E36DA2C6242。04:58:00 UTCの次のD1 SQL前preflightは224,264 reads、100,000行reserveを確保。04:58直後は集計遅延したが、05:03:11 UTCに対象D1が224,079から228,039へ+3,960となり、直前SELECTのmeta.rows_readと一致した。account-wideは228,251、残り4,771,749。別DBは合計46行増で、同時アクセスとの区別はできない。最新usage report C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T050310Z.json SHA-256 D781980A3651ABFA88F403B4219629D2124EAC34A095954D83AEBEDDFAC0A3BB。以後のD1 SQL前に再度preflightする。
- [ ] CLOUDFLARE_WORKERS_READ_TOKEN と CLOUDFLARE_QUEUE_READ_TOKEN は設定連絡後も05:10:23 UTC時点でCodex Process/User/Machine環境に見えない。値は表示していない。配信Worker module/source identity、Queue backlog、Worker Logsは未取得。Codexから参照できる環境へ反映後に再確認する。
- [ ] main protectionは未設定、migration Environmentとremote workflowは未掲載、Actions CI未実行。Worker production source、D1 write fence、DO handoff、未完了run原因、financial_periodsの不足/差異、definitionsのR2/API parityも未解決。production write/deploy、GitHub設定変更、dispatch、commit/pushは行っていない。
## 2026-11-05 JST（R2定義dry-run、05:12 UTC）

- [x] 6個の論理定義のR2移行dry-runを完了した。根拠: KV優先読取が成功し、D1 queries attempted=0、R2 version/current objectを12件計画、unchanged 0、conflicts 0、writes 0。つまり現在のR2には候補が必要とする12 objectがなく、定義をR2へseedするまで候補Workerの定義API/分析APIをdeployできない。report C:/Codex/r2-d1-ownership-audit-20261105/definition-source-20261105T051152Z.report.json、report_sha256 8024eec200414fd0d4655c3ac887fc5a33c1ecf433048bd80ffccba72ec62472。
- [ ] 定義12 objectのR2書込み、readback、Worker API parityは未実施。必要なD1 migration queryは0件だった。dry-runのAccount Analytics preflightは228,274 reads、残り4,771,726行、reserve 100,000行。seedはprotected workflow/reviewer gateと明示的なcutover判断後に実施する。
## 2026-11-05 JST（Cloudflare read token可視性の再確認、05:19 UTC）

- [ ] ユーザーはWorkers/Queue read tokenを設定済みと連絡したが、05:19:46 UTCの値なし確認でも両変数はCodex Process/User/Machine scopeに存在しない。値は表示していない。新しいCodexプロセスから参照できるまで、配信Worker modules、Queue metrics、Worker Logsの読み取り監査を保留する。

## 2026-11-05 JST（インカム空白月候補のR2・Worker・端末監査、05:39 UTC）

- [x] 現行R2候補と銘柄カタログを読み取り照合した。根拠: 同じgeneration `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`、候補3,203件（equity 2,889、ETF 253、REIT 61）、カタログ4,391件。候補のカタログ外コード0、source generation不一致0、候補重複0。カタログSHA-256 `681e8e58df1986ba9bca4082ac231c087610179dc109bb0a2912f9be274aaf72`、候補SHA-256 `e8daab1ea567dbe1f9acfef0ca663357881ec4565a3c4234451f5750a6dfc31d`。読み取り専用でR2/D1 writeなし。
- [x] ETF除外を定期V2同期と独立候補生成の両経路へ反映した。根拠: `security_type=etf` を候補対象から外し、通常同期・復旧・既存カタログ再利用でも既存ETF候補を公開カタログから消す。REITは対象に残す。候補生成テスト、既存候補のETF除去テスト、通常同期の反復公開テストが成功し、`py_compile`も成功。
- [x] 候補Worker経路とAndroid側処理を確認した。根拠: 現行R2オブジェクトを供給するローカルWorker harnessはHTTP 200で全JSON一致、R2 GET 2回、D1アクセス0、R2 write 0、Durable Objectのローカルstub呼出し1回。Androidは取得JSONをSharedPreferencesに保存して起動時に読み込み、ウォッチリストがある場合にJST日付・12時間制限で更新し、空白月との照合・最大3件の順位付けを端末内で行う。既存 `IncomeAdviceCatalogCacheTest` はオフライン時のキャッシュ表示を検証する。これは本番Workerのsource identity/APIを証明しない。
- [ ] 現行R2候補にETFが253件ある。候補生成コードは修正済みだがR2成果物は未再生成のため、次回の承認済みR2生成後に件数・カテゴリ・R2 readback・Worker API・Android表示を再照合する。Production workflow dispatch、R2 write、Worker deployは未実施。
- [ ] ユーザーはWorker/Queue read tokenを設定済みと連絡したが、このターンの値なし確認でも両変数はCodex Process/User/Machine環境に現れない。値は表示していない。配信Worker modules、Queue metrics、Worker Logsの取得は新しいCodexプロセスから両変数を確認できるまで未完了。

- [x] 再現レポートを保存した。カテゴリ照合 C:/Codex/r2-d1-ownership-audit-20261105/income-advice-category-coverage-20261105.json (SHA-256 061e4e0122180b637999b6a4896356169e9f91fe1074f61cd4e0c6cc2883cfdd)、ローカルWorker route C:/Codex/r2-d1-ownership-audit-20261105/income-advice-worker-route-verification-20261105.json (SHA-256 515862b07edab66c9b0c31d19d447b4a7da874320fc81c028b3c6d04d653b075)。
## 2026-11-05 JST（GitHub Actions・端末価格更新・D1使用量の再監査、06:03 UTC）

- [x] GitHub main とWorkflowの実在状態を確認した。根拠: main HEAD は b3f3a7b75bc54d30ec468fc085922791f99e340b、GitHub API の protected=false。候補作業ツリーの cloudflare-worker-ci.yml と d1-r2-migration.yml は未追跡で、main上のファイル取得は404。未pushのためActions画面にはまだ現れない。
- [x] Actionsの実行範囲を確認した。根拠: Worker CIはCloudflare配下のpush/PRでテストのみ実行しdeployしない。手動移行Workflowはworkflow_dispatch専用。既存YFinance更新（平日12:17 UTC）とIRBANK配当更新（毎日17:17 UTC）のscheduleは候補ブランチでも維持される。
- [x] 端末価格のアプリ起動経路を確認した。根拠: refreshOnStart=true の初期化がローカル復元後に通常更新ゲートを確認し、通常更新対象外でも MarketFetchPolicy.shouldFetchOnStartup が許可すれば価格更新を開始する。対象は日本株の保有・ウォッチ銘柄、通知処理は端末価格キャッシュを読む。05:39 UTC記録の隔離AVD instrumentation 138件成功あり。
- [x] Account Analyticsを再取得した。根拠: 05:57:30 UTC、UTC日2026-11-05、全D1 232,422 reads / 2,000 writes、残り4,767,578行。対象D1 232,165、他D1合計257。100,000行reserveを満たす。レポート C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T055730Z.sanitized.json SHA-256 BF637F50BBBCC23CA3264462F53E1CA243F7399F8BDFD65672C5DEB49DB038F7。AnalyticsのみでD1 SQLは未実行。
- [ ] 06:03 UTCの値なし確認でも CLOUDFLARE_WORKERS_READ_TOKEN と CLOUDFLARE_QUEUE_READ_TOKEN はCodex Process/User/Machine環境に見えない。値は表示していない。Worker deployed modules/source identity、Queue backlog/consumers、Worker Logsを取得できていない。
- [ ] main protectionはGitHub APIで無効と確認した。移行Workflowはprotected main、独立reviewer付き d1-r2-migration Environment、main限定deployment policyをpreflightで必須とするため現状はfail closed。両workflowは未pushでmainに存在せず、Environment secrets/CI実行も未確認。GitHub設定、workflow dispatch、R2/D1 write、Worker deploy、commit/pushは未実施。

## 2026-11-05 JST（Android候補の隔離AVD再検証、06:31 UTC）

- [x] JDK 17でAndroid instrumentationを全実行した。根拠: `:app:connectedDebugAndroidTest --rerun-tasks` のJUnit XMLとAndroidJUnitRunnerログは138件（137 passed、1 ignored）、失敗0で一致した。skipは `MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults`。JUnit XMLは `kurogane-dividend/android/app/build/outputs/androidTest-results/connected/debug/TEST-DiviewR2Audit35-20261105(AVD) - 15-_app-.xml`。
- [x] 候補debug APKを専用AVDへ明示パスで導入し、起動と成果物同一性を確認した。根拠: `MainActivity` がResumed状態となり、ビルドAPKと `base.apk` のSHA-256はともに `A942EF1897DA56EAB4F37F3450FA264D09291D3C3C7C827FB473DD1906DC27B4`。導入済みAPKの監査コピーは `C:/Codex/r2-d1-ownership-audit-20261105/android-base-emulator-5580-20261105.apk`。
- [x] 全suiteでskipされたlive repository testの条件を確認し、`allowLiveMarketReads=true` を明示して専用AVDで単独実行した。公開APIへ到達したが `storedHistoryStatus` の照合で失敗し、後続のanalysis endpoint検証には到達しなかった。
- [ ] `/api/v1/securities/285A?include_history_bounds=false` の候補契約と本番応答差（`not_requested`期待、`available`実測）を、Worker deployed source identityと照合して解決する。analysis endpointのlive acceptanceも未実施。実機導入は最終承認待ち。
- [ ] Gradle UTPの最終コンソール行は139 testsと表示したが、JUnit XML、AndroidJUnitRunnerログ、test-result.textprotoは138件を示した。139と138の集計差を解消する。

## 2026-11-05 JST（Account Analyticsと認証環境の再確認、06:36 UTC）

- [x] Account Analyticsを読み取り更新した。根拠: 06:36:07 UTC、全3 D1で236,663 reads / 2,263 writes、残り4,763,337 reads。100,000行reserveを満たす。レポート `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T063607Z.sanitized.json` SHA-256 `73A57C7D3162231F723FF969219F429D79E24172DF90A9AF3B19407F3912445D`。GraphQL AnalyticsのみでD1 SQLなし。
- [ ] 06:34:40 UTCの値なし確認で CLOUDFLARE_WORKERS_READ_TOKEN と CLOUDFLARE_QUEUE_READ_TOKEN はCodex Process/User/Machine scopeすべてに見えなかった。Worker modules/source identity、Queue backlog、Worker Logsは未取得。値は出力していない。

## 2026-11-05 JST（opt-in公開APIテストと使用量再照合、06:49 UTC）

- [x] 専用AVDからlive API acceptanceを実行した。根拠: `MarketFetchEfficiencyLiveTest` は `GET /api/v1/securities/285A?include_history_bounds=false` まで到達したが `storedHistoryStatus` が`not_requested`ではなく`available`で失敗し、後続analysis endpoint assertionsは実行されなかった。候補Workerはこのquery parameterでhistory bounds listingを省略して`not_requested`を返し、local route testでもR2 list reads 0を確認している。実際のdeployed Worker module/sourceは未確認。
- [x] Analytics preflight/postflightを照合した。根拠: 06:43:48と06:48:10 UTCはいずれも236,663 reads / 2,263 writes、残り4,763,337行で変化なし。postflight report `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T064810Z.sanitized.json` SHA-256 `0F7EBE0A9209E7735E5A4804BCF6883CFAACB09DD23E9EA20B6B09291E729ED7`。Analytics集計遅延の可能性があるため、差分なしを個別requestの無読書き証明とは扱わない。
- [ ] 06:49:58 UTCでもWorker/Queue read tokenはProcess/User/Machine scopeすべてに見えず、deployed source、Queue、Worker Logsは未確認。値は表示していない。

## 2026-11-05 JST（候補WorkerのD1呼び出し再棚卸し、06:57 UTC）

- [x] `data/irbank/cloudflare/src/` のD1呼び出し箇所を再確認した。根拠: D1 queryは `api_request_log` のDO初回bucket seed SELECTと、残置対象の `support_feedback` / `support_feedback_email`（受付・管理・メールretry/cleanup）のみ。healthはD1 binding有無を返すだけ。市場検索・銘柄・分析・定期処理・定義の直接D1 SQLは候補sourceに見つからなかった。
- [ ] これは候補sourceの静的棚卸しであり、配信Worker source identityや本番D1アクセスの証明ではない。Worker read token反映後にdeployed modulesとlogsを照合する。

## 2026-11-05 JST（Account Analytics追跡、07:02 UTC）

- [x] live API確認後のAccount Analyticsを再取得した。根拠: 07:02:11 UTC時点で全3 D1合計236,671 reads / 2,264 writes、残り4,763,329 reads。06:48:10 UTCからaccount-wideで+8 reads / +1 writeだが、他の利用との区別はできない。レポート `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T070211Z.sanitized.json` SHA-256 `D98DC30A067BC1437159DE547FE83E6DEF37110BBA9060525525C236F749444A`。AnalyticsのみでD1 SQLなし。
- [ ] 07:02:10 UTCの値なし確認でもWorker/Queue read tokenはProcess/User/Machine scopeすべてに見えない。deployed Worker source、Queue backlog、Worker logsは未取得。

## 2026-11-05 JST（本番securities検索計画の再確認、07:12–07:14 UTC）

- [x] D1 SQL直前のAccount Analytics preflightを実施した。根拠: 07:12:01 UTCに全3 D1で236,671 rows read / 2,264 rows written、残り4,763,329行。100,000行reserveを満たす。レポート `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T0711Z.sanitized.json` SHA-256 `6994636B970BD02B774DEA10188921D29F0BCD6D1EA6392C1B23748A13BCE68D`。
- [x] 本番`securities`のlookup planを読み取り確認した。根拠: `MULTI-INDEX OR`が主キーの`sqlite_autoindex_securities_1`と`idx_securities_code_display`を使い、EXPLAINはrows_read=0 / rows_written=0 / changed_db=false。レポート `C:/Codex/r2-d1-ownership-audit-20261105/securities-explain-20261105T0713Z.sanitized.json` SHA-256 `84D01F5F2AEAFD4D3E720BC0D43171139C82100014BB4DC4373AD84CA86D2872`。
- [x] 実際の7203検索を読み取り確認した。根拠: 1行返却、`meta.rows_read=1`、rows_written=0、changed_db=false。レポート `C:/Codex/r2-d1-ownership-audit-20261105/securities-actual-query-20261105T0713Z.sanitized.json` SHA-256 `28C86C307DC3126FA5AADECACC7A8CD2DC39C3F1D21DE6FD840A01A1FE5F6C14`。
- [x] D1 SQL後のAccount Analyticsを照合した。根拠: 07:13:54 UTC時点でも236,671 reads / 2,264 writes、残り4,763,329行で集計値は変化なし。単発SQLの集計反映が遅れている可能性があり、account-wide差分を個別クエリの計上根拠にはしない。レポート `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T0714Z.sanitized.json` SHA-256 `DD3240740D0C53FEC3A2448D6A8538A9F0EB394A239546DBF3ED854E162F7C6D`。
- [ ] 07:08 UTCの値なし確認でも`CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`はProcess/User/Machine scopeに見えない。Worker modules/source identity、live Queue backlog、Worker Logsの再確認は未実施。

## 2026-11-05 JST（本番Worker/Queue・未完了定期runの確認、07:20–07:32 UTC）

- [x] Cloudflare Dashboardで本番Worker/Queueの表示を読み取り確認した。根拠: `diview-irbank` version prefix `4981c1e0`が100% active、D1/R2/KV/Queue bindingあり、Durable Object rate-limiter bindingなし。Queue/DLQの表示backlogは0。sanitized report `C:/Codex/r2-d1-ownership-audit-20261105/worker-queue-dashboard-readback-20261105T0720Z.sanitized.json` SHA-256 `7D2556086B68A317C0D94FCCD46E871422FF72DA6967CE1E25F51D23C743A4C7`。
- [x] 本番`scheduled_run_log`と`backend_state.analysis_source`を読み取り確認した。根拠: 638件すべて`analysis/running`、`finished_at`なし、processed/failed合計0。aggregate query各1回は`meta.rows_read=1276`、書込み0。`analysis_source`は不在でrows_read/writtenとも0。reports `scheduled-run-summary-20261105T0727Z.sanitized.json` SHA-256 `A0F4B3A855B684E78362709C841DDE700E273500BA9F9B369D019DFCD92E92BA`、`analysis-source-state-20261105T0729Z.sanitized.json` SHA-256 `4B5651477EE90C156D13BD222D307DAEF4D8CE4B9075BF7819BA26352105B59D`。
- [x] 候補移行コードの完了履歴条件を確認した。根拠: `scheduled-runs` queryは`finished_at IS NOT NULL`かつ開始から365日以内に限定し、実行中の638件をR2完了履歴に含めない。
- [x] Account Analyticsを再照合した。根拠: 07:32 UTC時点で全3 D1合計243,318 reads / 2,414 writes、残り4,756,682 reads。Diviewは243,029 / 1,687。07:24–07:32のDiview増分2,552 readsは2つのscheduled-run aggregate queryの`1276 × 2`と一致するが、Analytics遅延・他trafficもあり、原因の確定には使わない。report `d1-daily-usage-20261105T0732Z.per-db.sanitized.json` SHA-256 `A08BB08C9BE22E03850FD8DF232D9B4E788A070544DAD8A92CBF2C32D00EDADE`。
- [ ] 638件の未完了runの原因、新規作成元、配信Workerの正確なsource commitは未解決。完了履歴としてR2へ移行せず、D1から削除しない。Dashboardの直近1時間のWorker event表示0件は全期間のログ証明ではない。
- [ ] Userから設定済みと連絡のあった`CLOUDFLARE_WORKERS_READ_TOKEN` / `CLOUDFLARE_QUEUE_READ_TOKEN`は、このCodex環境のProcess/User/Machineいずれにも見えない。値は表示していない。Worker modules/source mappingと履歴ログのAPI確認は環境から参照可能になるまで未完了。

## 2026-11-05 JST（Cron履歴との照合、07:51 UTC）

- [x] CloudflareのCronイベント履歴を読み取り確認した。根拠: 画面は過去1週間にCronイベントなしと表示した一方、Worker設定にはCron triggerと次回時刻の表示があり、D1には07:17 UTC開始のrunが記録されている。設定画面の時刻にはtimezone labelがない。report `C:/Codex/r2-d1-ownership-audit-20261105/cron-events-dashboard-readback-20261105T0751Z.sanitized.json` SHA-256 `1FFE3BEFD22F7C6AA1E3357C81E3B16FF89EC9613132DD2B1BFCDF10752BFE00`。
- [ ] Cron履歴、配信Worker Logs、D1 runの作成元の不一致は未解決。Dashboardは新規作成/改名Workerの履歴反映に最大30分かかる場合があると表示するが、このWorkerの作成/改名時刻は未確認。今回のEditor Previewが07:50:04 UTCに本番Worker rootへGETし、応答とD1への影響は不明。Analytics増分には帰属させない。

## 2026-11-05 JST（12:10–12:15 UTC）本番Worker・Queue・CronのDashboard再読取

- [x] Cloudflare Dashboardで本番`diview-irbank`を読み取り確認した。`4981c1e0-e337-4392-9f5b-909b52c5bc0c`がtraffic 100%、表示上のdeploy messageは`Move watchlist ownership to device`、sourceはWrangler。KV、D1、Mail、R2、Queueの5 bindingsを確認し、Durable Object bindingは表示されなかった。Git commit SHAやmodule bytesはDashboardから得られず、本番source identityは未確定。
- [x] 価格QueueとDLQのmetricsを確認した。両者backlog/delayed backlogは0。価格Queueのsubscription tabは空だったが、これはevent subscription表示であり、consumer binding不在の証明にはしない。Queue consumer設定は未確定。
- [x] Dashboardの24時間Worker overviewは430 invocations / 23 errorsを表示した。7日間Observability aggregateは7,038 success / 1,271 errorsだったが、結果不完全の警告があり、error filterの表示26行（info 25 / error 1）とも整合しなかったため、7日分のerror分類完了とは扱わない。
- [x] 少なくとも2件のCron `17 * * * *` が`exceededCpu`で終了した記録を確認した。2026-11-05 14:18:04.648 JSTはwall 31,889 ms / CPU 2,050 ms、20:18:08.957 JSTはwall 35,941 ms / CPU 2,050 ms。後者のtraceはspansなし。
- [x] Sanitized report `C:/Codex/r2-d1-ownership-audit-20261105/worker-queue-observability-20261105T1215Z.sanitized.json` SHA-256 `CD328081F37F149526DC97C726F9678E8A6B3B26E9CD82943F711CB1B1B56EBE`。
- [ ] 配信moduleと候補sourceのhash/commit照合、DOの本番binding、Cron CPU超過の原因、Queue producer/consumer bindingの確定が残る。これらを確認するまで本番切替・runログの移行/削除は保留する。
- [ ] Process/User/MachineのどこからもWorker/Queue tokenは見えなかった。Queue token作成直後の一度限り表示画面がbrowser accessibility outputに値を含めたため、そのtokenは露出扱いとしてCloudflareで失効・再発行する。値は保存・再掲しない。新しいtokenはCodex起動環境に設定後、存在有無だけ再確認する。

## 2026-11-05 JST（12:23–12:29 UTC）scheduled_run_log dry-runと使用量照合

- [x] D1クエリ直前にAccount Analyticsを確認した。根拠: 12:23:09 UTC時点で全3 D1合計327,223 rows read / 5,310 rows written、残り4,672,777行。100,000行reserveを満たす。レポート `C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T122309Z.sanitized.json` SHA-256 `474541316813FA13455615FB38C0F9A0409F5D6189BD849EE534B34CBEB0E8B8`。
- [x] `scheduled-runs`移行phaseのdry-runを行った。根拠: 1 query / 643 rows_read / source_rows 0、planned objects 0、writes 0。完了条件を満たすrunはなかった。report `scheduled-runs-current-20261105T1225Z.report.json` SHA-256 `8BC722FAB4DBD9D35E5E6DCF5D4E72A28C3F4DF05F699E8532C5910F3B715DD4`。
- [x] `scheduled_run_log`のread-only operational auditを行った。根拠: 7 queries / 643 rows_read / 643 rows、全件`analysis/running`、全件`finished_at`なし、processed/failed合計0。開始時刻は`2026-10-04T19:49:05.538Z`から`2026-11-05T12:17:32.833Z`。report `operational-scheduled-runs-current-20261105T1224Z.report.json` SHA-256 `C5691A5E9258F183174B0B12D846613264A74D9EC045E1FD04C7722665535845`。
- [x] Account Analyticsを再照合した。根拠: 12:29:34 UTC時点で328,509 reads / 5,310 writes、残り4,671,491行。preflightからreadが1,286増え、2つのqueryの643 + 643と一致するが、account-wide集計のため個々のqueryへの帰属は断定しない。100,000行reserveを維持。report `d1-daily-usage-postflight-20261105T1228Z.sanitized.json` SHA-256 `8329C237B87D29D5648021A6000E3692B3676206E72442CC6D4B722284067331`。
- [ ] 643件すべて未完了であり、完了履歴としてR2へ移行せず、D1から削除しない。件数が前回638件から5件増えた理由とrun作成元は未確定。本番source identity、Cron履歴、Worker logsの照合が残る。
- [ ] `CLOUDFLARE_WORKERS_READ_TOKEN`と`CLOUDFLARE_QUEUE_READ_TOKEN`は設定済みとの連絡後も、このCodexのProcess/User/Machine環境すべてから見えない。トークン値は表示していない。Queue tokenは作成直後の表示が取得されたため、失効・再発行済みの新しい値がCodex起動環境に反映された後、読み取り監査を続ける。


## 2026-11-05 JST（13:19–13:28 UTC）財務期間dry-runと本番bindingの確認

- [x] annual D1行のR2カテゴリ差を監査で区別した。根拠: FINANCIAL_AUDIT_VERSION=3と回帰テストを追加し、移行監査66/66成功。新しいdry-runで49,592行、99,184 meta rows_read、3,061 R2 lookupsを照合し、49,571一致、4値差、2カテゴリ差、15期間欠落、migration/D1/R2 write 0。report C:/Codex/r2-d1-ownership-audit-20261105/financial-periods-anomaly-trace-v3-20261105T1319Z.report.json SHA-256 45BEA50E510DF502403158D9015B670940F4C186B006E9C84FA53095A4F9E262。
- [x] Cloudflare Dashboardで本番Worker bindingと価格Queueを読み取り確認した。根拠: version prefix 4981c1e0が100% active。KV/D1/Mail/R2/Queueの5 bindingが表示され、Durable Object bindingは表示されなかった。22:26 JSTの価格Queueはrealtime/delayed backlog 0、平均遅延0秒。DashboardでGit SHA/module bytesは確認できず、DLQの現在値も未取得。
- [x] Account Analyticsをpostflight確認した。根拠: 13:28 UTC、全3 D1で508,061 reads / 5,512 writes、残り4,491,939 reads。dry-runのquery-level meta.rows_read=99,184とaccount-wide差分は一致しないため、Analytics集計遅延や他trafficの可能性を残し、個別queryへ差分を帰属していない。
- [ ] ユーザーから設定済みとの連絡後もWorker/Queue tokenはProcess/User/Machine環境に見えない。値は表示していない。Codexを完全終了・再起動して存在だけ再確認し、見える環境でdeployed module/source、Worker logs、Queue/DLQを読み取り確認する。
- [ ] 本番DO rate-limiter bindingとdeployed source identityが未確定。候補Workerとの一致、DO状態のhandoff、API parityを確認するまでproduction cutover・R2移行write・D1データ削除を保留する。

## 2026-11-05 UTC 13:46 候補テストとGitHub Workflow公開状態

- [x] 候補Cloudflare Workerの全テストを実行した。根拠: `node --test test/*.test.mjs test/*.test.cjs` が130/130成功。
- [x] 移行・使用量・WorkflowのPythonテストを実行した。根拠: `python -m unittest scripts.test_d1_daily_usage scripts.test_migrate_d1_to_r2 scripts.test_migration_workflow` が88/88成功。
- [x] GitHub `main`のWorkflow一覧を読み取り確認した。根拠: `cloudflare-worker-ci.yml` と `d1-r2-migration.yml` はmainに存在せず、候補作業ツリー内の未追跡ファイル。branch APIの`protected`はfalse。Workflowの掲載・dispatch、設定変更はしていない。
- [ ] ユーザーから設定済みとの連絡後も、今回のCodex実行環境で2つのWorker/Queue tokenはProcess/User/Machineのいずれにも見えない。値は表示していない。Cloudflare APIでのdeployed module/source、logs、Queue/DLQ確認は、Codex再起動後にtokenを参照できる状態で再開する。

## 2026-11-05 JST（14:07 UTC）Android通知候補のJDK 17・隔離AVD検証

- [x] 候補AndroidをJDK 17で再ビルド・テストした。根拠: Microsoft OpenJDK 17.0.20.1を明示し、`testDebugUnitTest --rerun-tasks`が277 tests、failures/errors/skipped 0で成功。`assembleDebugAndroidTest --rerun-tasks`も成功。
- [x] 監査専用AVD `DiviewR2Audit35-20261105`で通知配送instrumentationを実行した。根拠: `NotificationDeliveryTest` 13/13成功（ローカル価格なしでnetwork fallbackしない、古い価格のpending通知取消を含む）。同一候補worktreeのbuilt app SHA-256とinstalled `base.apk` SHA-256が`F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351E`で一致。通常/共有AVDと物理端末は使用していない。
- [ ] ユーザーから設定済みとの連絡後の再確認でもWorker/Queue tokenはProcess/User/Machine scopeに見えない。値は表示していない。Windows User environmentに設定され、Codexから参照可能になった後にCloudflare API監査を再開する。

## 2026-11-05 JST（14:17 UTC）D1参照境界・Android価格通知の追加検証

- [x] 候補WorkerのD1呼び出し元を再棚卸しした。根拠: `src/index.js`のD1 SQLは、DOへの初回レート制限状態引継ぎSELECTとsupport-feedbackのCRUD/保持処理。healthはD1 bindingの有無だけを返す。市場データ検索・分析・定義・定期分析のD1 SQLは見つからず、Worker 130/130テストのR2経路ケースも成功。これは候補ソースの検査であり、本番配信sourceとの一致は未確認。
- [x] Android価格取得・通知・インカム候補キャッシュのinstrumentationを追加実行した。根拠: 監査専用AVD `DiviewR2Audit35-20261105`で`AssetDataRefreshTest` 24/24、`IncomeAdviceCatalogCacheTest` 1/1が成功。既実行`NotificationDeliveryTest` 13/13と合わせ38/38。テストは専用のFake HTTPまたは端末内fixtureを使用。
- [ ] Cloudflare APIのdeployed source/modules、Worker logs、Queue/DLQ、DO利用量はread token不在で未確認。production parityと運用ゲートを解消するまでcutover・R2本番write・D1削除を保留する。
- [x] 東証営業日カレンダーの2026・2027年を公式日程と照合した。根拠: JPXが公開する現物市場の休業日が`TseTradingCalendar`の既存回帰テストと一致する（[JPX営業時間・休業日一覧](https://www.jpx.co.jp/corporate/about-jpx/calendar/index.html)）。

## 2026-11-05 14:24 UTC Cloudflare認証環境・GitHub設定の追加確認

- [x] Cloudflare read-tokenの環境可視性を値なしで確認した。根拠: CLOUDFLARE_WORKERS_READ_TOKENとCLOUDFLARE_QUEUE_READ_TOKENはProcess/User/Machineすべてfalse。Wrangler 4.136.1のwhoamiもaccount IDを取得できず終了した。Worker module/source、Queue/DLQ backlog、Worker logsのAPI読取は未実施。
- [x] GitHub mainとworkflow一覧を再確認した。根拠: main SHAはb3f3a7b75bc54d30ec468fc085922791f99e340b、branch APIはprotected=false、rulesetsは0件。公開済みworkflowは9件で、cloudflare-worker-ci.ymlとd1-r2-migration.ymlは含まれない。
- [x] 候補の移行workflow guardと現設定を比較した。根拠: guardは保護されたmainと独立reviewer付きd1-r2-migration Environmentを要求するが、現在のmainは未保護。Environment状態の取得はGitHub connector非対応で、gh CLIも未導入。branch protection、Environment、workflow公開/実行の変更はしていない。
- [ ] Codexを完全終了して再起動し、2つのCloudflare tokenがプロセスから参照できることを値なしで確認する。参照可能になった後にproduction module/version、Queue/DLQ backlog、Worker logsをread-onlyで再取得する。
- [ ] main branch protectionとd1-r2-migration Environmentの独立承認・main限定deploy policyを設定・確認するまで、移行workflowのpreflight通過やdispatchはできない。

## 2026-11-05 14:33 UTC レート制限ブリッジ設定の欠落修正

- [x] 移行手順が参照するCron停止済みDO bridge設定を、正本作業ツリーと最終候補作業ツリーに追加した。根拠: wrangler.cutover-paused.tomlは各wrangler.tomlと照合し、DO/KV/D1/R2/Queue/email bindingを保ったままcronだけ空にした内容で、元ファイルとSHA-256が一致。
- [x] 追加設定とレート制限移行契約を検証した。根拠: node --check成功、rate-limit.test.mjsとrate-limit-handoff.test.mjsは15/15成功、wrangler deploy --dry-run --config wrangler.cutover-paused.tomlはbinding一覧を生成し、--dry-runで終了した。本番deploy、D1 fence実行、DOへの実traffic移行はしていない。
- [ ] 手順書のpre-fence Cron停止設定は別途、production source/configurationと一致する構成から作る。今回追加したpaused configはD1 fence確認後にDO bridgeへ切り替える段階用である。

## 2026-11-05 14:35 UTC D1日次使用量の再確認

- [x] D1 SQLを実行せずAccount Analyticsで当日使用量を再取得した。根拠: 2026-11-05 UTCの3 database合計は535,404 rows read / 5,747 rows written、5,000,000上限に対して4,464,596 rows残。3 database別readは257 / 569 / 534,578、writeは31 / 2,835 / 2,881。照会は2026-11-05T14:34:57ZのAnalytics APIのみで、個別databaseへの原因帰属は行っていない。
- [ ] 既存の未完了R2/Worker/Queue/Actions確認と最終切替は、このAnalytics集計だけでは完了にならない。各移行・監査dispatch前にも現在の予算と読み取り可否を再確認する。

## 2026-11-05 14:44 UTC インカム候補R2実体・本番API確認

- [x] R2の市場カタログとインカム候補currentを読み戻した。根拠: catalogはgeneration 2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e・4,391件、income adviceは同generation・3,203件・重複0・カタログ外コード0・source generation不一致0。候補内訳はequity 2,889、ETF 253、REIT 61。income advice objectは2026-11-04T20:40:32Z生成で、R2書込みは0。
- [x] 公開Workerのincome-advice GETと現行R2 payloadを照合した。根拠: HTTP 200、3,203件、重複0で、応答全体がR2 currentと一致。これは一度の公開GETであり、現行production rate-limit経路がD1を使う可能性がある。14:34:57Zから14:43:51ZのAccount Analyticsは+2 rows read/+2 rows writtenだが、このGETだけへの帰属はできない。
- [x] income advice generatorのETF除外に回帰テストを追加し、既存yfinance test discoveryを実行した。根拠: test_generate_income_advice.pyを含む173/173成功。新テストはETFを候補R2読取へ渡さず、equity候補だけを含むことをmockで確認し、R2アクセス・書込みなし。
- [ ] production R2 currentにはETF 253件が残る。候補生成コードの修正・テストだけでは公開オブジェクトは変わらない。既存yfinance-r2 workflowの承認済み手動生成/公開後にR2 readbackとWorker GETを再照合する。今回そのWorkflow dispatchやR2 writeは行っていない。

## 2026-11-06 JST（2026-11-05 UTC 15:03–15:04）読み取り監査の追加結果

- [x] Account AnalyticsでD1の日次使用量を再確認した。根拠: `scripts/d1_daily_usage.py` が2026-11-05 UTCについて3 database合計535,414行read / 5,750行write、上限5,000,000に対して4,464,586行残を返した。database別readは265 / 569 / 534,580、writeは32 / 2,835 / 2,883。D1 SQLは実行していない。
- [x] 読み取りAPIに必要な権限をCloudflare公式仕様で照合した。根拠: Worker deployments/source取得はWorkers Scripts Read、Queue metricsはQueues ReadまたはWorkers Scripts Readで可能。Observability query APIはWorkers Observability Writeを要求するため、その権限を追加せずに扱う。
- [x] Account AnalyticsのworkersInvocationsScheduledを2026-10-29 14:50 UTC〜2026-11-05 14:50 UTCで照会した。根拠: datetimeとscheduledDatetimeの範囲照会はGraphQLエラーなしで0件。公式資料上このデータセットはUS-onlyかつZone配信Workerの範囲で、現在のworkers.dev endpointに対するCron不在の証明にはならない。
- [x] 2026-11-05 UTC 19:29の再確認でWORKERS_READ_TOKENとQUEUE_READ_TOKENがProcess環境から利用可能と確認し、値は記録せずCloudflare GET監査を完了した。根拠: `worker-queue-live-read-audit-20261105T192958Z.sanitized.json`。
- [x] 稼働Worker Version/source、Queue/DLQ backlogとconsumer、DO bindingを読み取り確認した。根拠: version 84が100%配信、Queue/DLQ backlogは各0、DO bindingなし。sourceはcommit `f2f2d831e9e82b0567e56127446706fed764e02d` のWrangler bundleとbyte一致。
- [ ] DO counter handoff・deploy後の状態、実際のWorker invocation/Cron起動元のtelemetryは未確認。production cutoverは保留する。

## 2026-11-06 JST（2026-11-05 UTC 15:11）R2 current再確認

- [x] R2のcurrent catalogとincome-adviceを読み戻した。根拠: 監査レポート C:\Codex\r2-d1-ownership-audit-20261105\r2-income-advice-current-readback-20261105T151146Z.sanitized.json（SHA-256 3D17F157777A947481238F27F060F85957F054AB2A896AD1EEC08115BA8AE92F）で、両方ともgeneration 2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e。catalog 4,391件、income-advice 3,203件、重複0、カタログ外コード0、source generation一致。
- [x] current候補の種類別件数をcatalogのsecurity_typeで再集計した。根拠: equity 2,889、ETF 253、REIT 61。現行R2にETFが残るため、ローカル生成コードの除外修正はまだ公開データへ反映されていない。
- [x] 公開Worker sourceとR2 currentを再照合した。根拠: 2026-11-05 UTC 20:49のincome-advice GETは`HTTPError`となったが、保存レポートにHTTP status/bodyがなく原因は未特定。API parityの成功とは扱わない。
- [ ] API parityは未確認。旧配信版のGET rate-limit処理がD1を使う可能性があるため、候補Worker配信後にD1影響を管理した状態で再検証する。今回のR2 readbackは4 GETで、R2 writeは0。


## 2026-11-06 JST（2026-11-05 UTC 15:18）GitHub Actions定期更新の照合

- [x] YFinance定期実行#388の失敗理由とR2公開結果を照合した。根拠: mainのcommit b3f3a7b75bc54d30ec468fc085922791f99e340bで、4,576銘柄のうち4,420成功・156失敗（HTTP 404が155、HTTP 401が1）。R2 generation `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e` は公開済みで、その後スクリプトが失敗銘柄ありとして終了コード1を返した。
- [x] 翌営業日のschedule run有無を確認した。根拠: yfinance-r2.ymlは平日12:17 UTC実行だが、2026-11-05 15:18 UTC時点の最新Actions runは2026-11-04 22:38 UTCで、11/05の実行記録がない。schedule欠落の原因は未確定。GitHub公式資料は高負荷時のschedule遅延・欠落を記載するが、今回の原因と断定できない。
- [x] YFinance schedule workflowが実行されR2 generationを公開することを確認した。根拠: run #389が2026-11-05T20:40:50Zにfailure終了したが、4,420成功・156失敗を含むgenerationを公開。
- [ ] actor権限、部分失敗時の通知・freshness監視は未確認。失敗終了とR2公開の双方を表示する運用を決める。workflow設定変更・dispatchは未実施。
## 2026-11-06 JST（2026-11-05 UTC 15:29–15:34）定義・定義監査の読み取りdry-run

- [x] Cloudflare KV上の現行定義を値を表示せず確認した。根拠: `DEFINITION_FILES` の6キーすべてが読み取り可能で、定義値そのものはログに出していない。
- [x] `definitions` dry-runとR2現物を照合した。根拠: D1 query 0回、R2 `definitions/v1/` は0 object、6定義分のversion object/current pointer計12 objectを計画、conflict 0、`objects_written=0`。移行workflowを使わずcandidate CLIをread-only dry-runで実行した。
- [x] `definition-audit` dry-runを実施した。根拠: D1 2行・1 SELECT、R2 `audit/v1/definitions/` は0 object、監査2 objectを計画、conflict 0、`objects_written=0`。D1/R2 write、Worker GET/deploy、Actions dispatchは行っていない。
- [x] 読み取り予算を前後照合した。根拠: Account Analyticsは15:31:20 UTCに539,500 reads、15:34:09 UTCに539,508 readsを返した。definition-auditのD1 `meta.rows_read` は2で、account-wideの+8全体をこの監査には帰属させない。いずれのpreflightも100,500行の予約条件を満たした。
- [ ] R2定義12 objectと定義監査2 objectの本番seedは承認後に行い、書込み後readbackとWorker API parityを確かめる。candidate WorkerはR2定義を読む。現行production source identityは照合済みだが、定義seed後の本番API parityは未確認。
## 2026-11-06 JST（2026-11-05 UTC 15:38–15:40）分析履歴・現在状態の全件dry-run

- [x] D1分析履歴を全件dry-runした。根拠: `stock_analysis_history` 38,660行を39 SELECTで読取り、38,660 R2 objectを計画。期間はmarket_date 2026-10-02〜2026-11-04、observed_at 2026-10-04T19:49:07.648Z〜2026-11-05T15:17:55.716Z。complete=true、conflict 0、R2 write 0。
- [x] 分析の現在状態を全件dry-runした。根拠: `stock_analysis_state` 248行を1 SELECTで読取り、248 R2 objectを計画。last_observed_atは2026-10-04T19:49:21.527Z〜2026-11-05T15:17:55.716Z。complete=true、conflict 0、R2 write 0。
- [x] R2 actual listingで `analysis/v1/history/`、`analysis/v1/current/`、`analysis/v1/state/` がすべて0 objectであることを確認した。両phaseの計38,908 objectはdry-run上のwrite setであり、本番R2へは書いていない。
- [x] D1使用量のpre/postを確認した。根拠: 15:38:47 UTC preflight 539,511 reads、15:40:46 UTC postflight 578,173 reads（残り4,421,827）。2 phaseの `meta.rows_read` 合計は38,908だが、account-wide増分38,662とは一致しない。差分はこのdry-runへ帰属させず、Analytics遅延/他trafficを区別できないまま記録する。
- [ ] 38,908 objectのR2書込み・readback・analysis-history API parityは未実施。承認後のseedとWorker切替前検証に残す。analysis-source stateは別phaseで確認し、Cronが再分析する条件とD1残存参照を切替前に確定する。

## 2026-11-06 JST（2026-11-05 UTC 16:04–16:11）Android instrumentation・Cloudflare read token再確認

- [x] 候補Androidのinstrumentation 141件を専用AVDで完走した。根拠: `DiviewR2Audit35-20261105`、JDK 17、runner exit 0、`OK (141 tests)`。ログ `C:\Codex\r2-d1-ownership-audit-20261105\android-instrumentation-direct-20261105T1604Z.txt` のSHA-256は `75606C77C600C61A7676809B823C3C84FC555722C275CB1BAC296A8660C85CD9`。アプリAPK/AndroidTest APKと再起動後の各installed `base.apk` がそれぞれSHA-256 `F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351E` / `F5B9100508B404A296C71C18651B0D269F28E6FD2298B7857CB3918FDDF3DC32` で一致した。

- [x] 2026-11-05 UTC 16:03時点では両read tokenが未設定だったが、ユーザー設定後の16:45–16:54 UTCに両トークンのProcess可視性とCloudflare Workers/Queues read-only監査を確認した。値は取得・表示していない。Worker content、配信版、bindings、DO namespace、Queue consumers/metricsを読み戻した。実行ログとGit commit identityは未確認のため別のcutover前follow-upに残す。

## 2026-11-06 JST（2026-11-05 UTC 16:15–16:21）Worker CI・移行CLIローカル検証

- [x] Candidate Worker CIと同じNode suiteを実行した。根拠: `node --test test/*.test.mjs test/*.test.cjs` は130 tests、130 pass、0 fail、0 skip、exit 0。ログ `C:\Codex\r2-d1-ownership-audit-20261105\worker-tests-20261105T1615Z.log` SHA-256 `895FE5DEAAB7AB56A486210D79570715B7CEFBEBF7418CC212CFF363913980EA`。

- [x] CIに含めた移行workflow fail-closed、D1→R2移行CLI、日次使用量検査のPython suiteを実行した。根拠: `python -m unittest scripts/test_migration_workflow.py scripts/test_migrate_d1_to_r2.py scripts/test_d1_daily_usage.py` は88 tests、exit 0。ログ `C:\Codex\r2-d1-ownership-audit-20261105\python-migration-tests-20261105T1615Z.log` SHA-256 `23FDF50F0B7D2644AD3023010B4FF3C35D8C21A4FB58D1C33D1DAF0F484E286A`。

- [ ] `cloudflare-worker-ci.yml` と `d1-r2-migration.yml` はcandidate worktreeの未追跡ファイルで、GitHub mainには未公開。GitHub上のrun、main protection、`d1-r2-migration` Environmentの独立承認設定は未確認。push/dispatch/本番writeは未実施。

## 2026-11-06 JST（2026-11-05 UTC 16:30）GitHub Actions guard再確認

- [x] GitHubの読み取りAPIで現状を記録した。根拠: `main` は `protected=false`、`d1-r2-migration` Environmentなし、workflowは9件で新Worker CI/移行workflowはmainに未公開。sanitized report `C:\Codex\r2-d1-ownership-audit-20261105\github-state-20261105T1630Z.json` SHA-256 `D82C2402211F52D07F28A1BA850199FBA9E35602315F2C6F4B65BE5E74686138`。設定変更・dispatchなし。

- [ ] main protectionと独立reviewer付きmain限定Environmentを用意するまで、手動移行workflowのpreflightは通らない。設定とworkflow公開後に再読取確認し、production dispatch前に別途承認を得る。

- [ ] `yfinance-r2` はactiveだが、`17 12 * * 1-5` UTC scheduleは2026-11-05 16:30 UTC時点でrunなし。最新schedule run #388はfailure（job `v2-daily-sync`）。欠落scheduleの原因と鮮度監視は未解決。既存workflowのdispatch・設定変更なし。

## 2026-11-06 JST（2026-11-05 UTC 16:45–16:54）Cloudflare本番Worker・Queue・Durable Objects読み取り監査

- [x] ユーザー設定後のWorkers/Queues読取専用トークンを値を出さずに確認し、本番Worker設定・配信版・コード内容を読み戻した。根拠: diview-irbankのversion 4981c1e0が100%配信中、D1/R2/KV/Queue bindingあり、Durable Object bindingなし。取得したindex.jsは120,934 bytes、SHA-256 20e7a40b598f5474d490936fa230701bc1cd43d436640328202ef884826f47ab。
- [x] 本番モジュールの静的スキャンでD1経路を再確認した。根拠: DB.prepare 32箇所、DB.batch 1箇所、DB.exec 0箇所。認識できたSQL literal 31件はSELECT 12、DELETE 8、INSERT 6、UPDATE 5。api_request_log、securities、stock_analysis_history、stock_analysis_state、scheduled_run_log等のテーブル識別子を検出。これはコード内の静的件数で、実行頻度・D1読取行数ではない。RATE_LIMITER binding名とRateLimitCounter class名は見つからなかった。
- [x] Queue consumerとpoint-in-time metricsを確認した。根拠: diview-market-pricesはdiview-irbank consumer 1件、DLQ diview-market-prices-dlq、max retries 3。DLQにも同Workerのconsumer 1件。読み取り時点の両backlog_countは0。Queue metricsは近似値であり、次のcutover前にも再確認する。
- [x] Durable Objects namespaceを確認した。根拠: account-wideで1 namespaceのみで、別WorkerのLiveChatRoom。Diviewのrate-limit namespaceは存在しない。
- [ ] 実行ログ・実際のD1行読取数・レート制限カウンター移行状態は未確認。Worker Observability logsは有効・永続化設定だが、telemetry queryは実行していない。Cloudflare APIの当該queryにはWorkers Observability Write権限が必要。Cloudflare APIはGit commit SHAを返さなかったが、後続の17:22 UTC検証で配信module bytesとcommit f2f2d831e9e82b0567e56127446706fed764e02dのWrangler 4.157.0 dry-run bundleが完全一致した。これは強いsource mappingであり、APIによるcommit attestationではない。Worker invocation telemetry、実際のD1読取行数、DO counter importの運用結果は未確認。
- [x] sanitized reportを保存した。根拠: C:/Codex/r2-d1-ownership-audit-20261105/worker-production-read-audit-20261105T165423Z.sanitized.json、SHA-256 ab753a6e06b56754c6c6834b053854045e2c7ed659e1a5501435ba6548d9c4a7。Cloudflare API GETのみ。D1 SQL、R2 read/write、Worker invocation/deployment、GitHub Actions dispatchは未実施。

## 2026-11-06 JST（2026-11-05 UTC 17:01）Account Analyticsの日次D1使用量

- [x] UTC 2026-11-05のAccount Analytics D1集計を読み取った。根拠: 3 database合計582,527行読み取り、6,046行書き込み、500万行上限に対して4,417,473行残り。
- [x] production WorkerのDB binding先とdatabase集計を照合した。根拠: binding先database ID e5046cbb-dd81-4f7c-aa25-1e7a2616d8d7の読み取り581,667行・書き込み3,172行。他2 databaseは合計860行読み取り。D1 Analyticsはdatabase単位であり、特定Workerや呼出元への帰属を示さない。
- [x] 読み取り結果を保存した。根拠: C:/Codex/r2-d1-ownership-audit-20261105/d1-daily-usage-20261105T1701Z.sanitized.json SHA-256 404adff618620ae0080a0114ecd863b577357adab1375ca87a17bb2e2a6e3936、Worker binding対応表 C:/Codex/r2-d1-ownership-audit-20261105/worker-d1-usage-match-20261105T1701Z.sanitized.json SHA-256 cd67c6c0a535ce3708955441f6efc72fd488cd058ff8135c4849b791e02ae15f。Account Analytics照会でありD1 SQLは実行していない。
- [ ] これらの集計値から本番Workerの単独読取量や個別クエリへの帰属はできない。Workerの実行telemetry取得または呼出元別計測が別途必要。

## 2026-11-06 JST（2026-11-05 UTC 17:22–17:29）本番source照合・Queue再読取・rate-limit切替評価

- [x] 本番`diview-irbank`のactive moduleをローカルbundleとbyte単位で照合した。根拠: deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%配信中。production `index.js` 120,935 bytes / SHA-256 `262388d871931f4321bb3946937cd9faa2e72313297ef51ef60f4629bea4ad9a` は、commit `f2f2d831e9e82b0567e56127446706fed764e02d`をWrangler 4.157.0でdry-runしたbundleと完全一致し、共通byte不一致は0。APIはGit SHAを返していないため、commit attributionはbyte一致に基づく強いmappingとして扱う。以前の16:54記録にある120,934 bytes / SHA `20e7a40b...`はmultipart抽出時に末尾改行を除去した誤値で、この比較結果が訂正値。
- [x] 現在の候補bundleとの差を確認した。根拠: candidate branchのbundleは189,509 bytes / SHA-256 `48ce68c880688d0591a52d1342548f7c1f343d635aa358bd246eab0636c5faab`で、本番moduleとは一致しない。candidateは`RATE_LIMITER`を参照するが、本番bindingsにはDO rate limiterがない。
- [x] Cron停止用pre-fence設定をproduction sourceからdry-runした。根拠: `wrangler.pre-fence-paused.toml`はCronだけを空にし、bundle 120,935 bytes / SHA-256 `262388d871931f4321bb3946937cd9faa2e72313297ef51ef60f4629bea4ad9a`で本番moduleと一致。これはローカルdry-runのみで、本番deployはしていない。
- [x] QueueとDLQのmetricsを再読取した。根拠: 2026-11-05 17:29:40 UTC時点で`diview-market-prices`と`diview-market-prices-dlq`はそれぞれbacklog 0 messages / 0 bytes / oldest timestamp 0。best-effortのpoint-in-time値であり、cutover時に再確認する。
- [x] rate-limit D1の最新取得状態を整理した。根拠: 17:16:49 UTC Account Analyticsは582,602 rows read、4,417,398 remaining。60秒snapshotはactive bucket 0、fence trigger 0、query planは`idx_api_request_log_window`を利用。17:16レポートの`rows_written`は別時刻の値だったため訂正版から除外した。これらはcutover時に再取得する必要がある。
- [x] fence適用中の可用性影響を明記した。現行本番sourceはrate limitでD1 `api_request_log`へUPSERTし、呼出し元にcatchがない。fence後・DO bridge有効化前のrate-limited API requestはWorkerの未処理例外となり5xxになるとソースから推定される。継続時間は手順時間次第で未確定。ゼロ停止とは扱わず、承認済みmaintenance windowを切替条件に含める。
- [x] sanitized reportsを保存した。source比較 `worker-source-exact-comparison-20261105T1722Z.sanitized.json` SHA-256 `1e4f04f6effa806c42e96e51d1e082743df124655d4e100f026f79a3029cf332`、Queue/Worker再読取 `worker-queue-live-read-audit-20261105T172942Z.sanitized.json` SHA-256 `9570c70e21a780729d69fd75cf42e82fa882d60f22aa430868699a3b7e3098f6`、訂正版D1 snapshot `rate-limit-cutover-readiness-20261105T1716Z.corrected.sanitized.json` SHA-256 `9c4936176e6852323749268aa183abdd868f86b5a00e44fd638ecd7e46a3c2b3`。
- [ ] Worker invocation logs/telemetry、呼出元一覧、DO運用時の実カウンター移行、GitHub main protection / migration Environmentは未確認。production R2 write、D1 fence/write、Worker deploy、Actions dispatch、commit/pushは未実施。各読み取り・cutover前preflightを実施し、production変更の最終承認を待つ。

## 2026-11-06 JST（2026-11-05 UTC 17:51–17:55）read token・本番Worker・Actions再確認

- [x] 設定済みのWorkers/Queues read tokenを値を出さずに確認し、本番Worker settings/deploymentとQueue metricsを再読取した。根拠: `worker-queue-live-read-audit-20261105T175114Z.sanitized.json`（SHA-256 `b138f5080112a4f937e449e817b1fd05e8e9deec62f53aad4ee592b69d5ef0fc`）。deployment/versionは17:22 UTC確認時と同一、traffic 100%、DO rate limiter bindingなし。main Queue/DLQ backlogはそれぞれ0。CloudflareはREST GETのみ。
- [x] 最終候補WorkerのD1 callsite境界を静的に確認した。根拠: D1の残置はDO初回seedの旧`api_request_log` SELECT、feedback受付/管理/保持、email outbox lease/retry、healthのbinding表示。市場・分析・定義のD1 SQL callsiteは候補`src/`にない。これは候補コードのみであり、現本番Workerは旧D1 rate limiter/sourceを継続中。
- [x] GitHub mainとActions readinessを読み取った。根拠: `github-actions-readiness-20261105T175501Z.sanitized.json`（SHA-256 `45dc8237684d99919d8cc8c47f0d665d4dd37d48eff4a02671aa5fb98189870c`）。main `b3f3a7b75bc54d30ec468fc085922791f99e340b` は`protected=false`、repository rulesets 0、workflow 9件。Worker CI / migration workflowと`d1-r2-migration` Environmentがmainにない。設定・dispatchなし。
- [ ] GitHub migration workflowを公開・実行する前にmain protectionと、required independent reviewer / prevent self-review / main-only deployment policy付きEnvironmentを作り、D1 read・Analytics read・必要なR2資格情報をEnvironment secretsへ設定して再読取する。ローカル環境のtokenはGitHub Actionsへ自動共有されない。
- [ ] 既存R2定期公開の失敗を別途調査する。IRBANK run #17は`/v1/usage` HTTP 403でrefresh前に失敗、YFinance run #388は4,420成功・156失敗（155件404・1件401）後にR2 generationを公開して失敗終了。2026-11-05平日YFinance schedule runも17:55 UTC時点で確認できない。既存Workflow変更・再実行はしていない。
- [ ] Worker telemetry、呼出元別D1 read量、DOへのcounter import後readback、本番R2 write/readback、D1 fence、本番Worker deploy、物理端末確認は未実施。maintenance windowとfresh preflightを含むproduction cutoverは最終承認待ち。

## 2026-11-06 JST（2026-11-05 UTC 18:02–18:06）D1 usage・R2 current・income-advice API parity

- [x] 公開Worker GETの直前・直後でD1日次Account Analyticsを確認した。根拠: UTC date 2026-11-05の3 DB合計は両方586,744 rows read / 6,246 rows written、残り4,413,256。集計値はaccount-wideであり、同値からこのrequestのD1行数を0と断定しない。
- [x] R2のcatalog/currentとincome_advice/currentを現物から再読込した。根拠: generation `2026-11-04-2b4b667ad4674ff7943a98c3ac13e54e`、4,391/3,203件、候補は重複0・catalog外0・source generation mismatch 0。カテゴリはequity 2,889、ETF 253、REIT 61。R2 writeなし。
- [x] 公開Workerのincome-advice endpointを1回GETしR2 currentと完全比較した。根拠: HTTP 200、3,203件、重複0、JSON payload一致。productionは旧D1レート制限経路を通った。Account Analytics pre/post同値だがrequest単位のD1 rowsは不明。
- [x] 現R2 objectとの差を現行generatorで全件dry-runし確定した。根拠: 4,391 catalog rowsから2,971件を再生成し、現行3,203件との差はETF 253件除外・21件追加・共通2,950件に差分。`ProdCat=023`の海外ETF 31件もlegacy `listed_security`表記にかかわらず除外した。開始/終了時にcatalog、advice current、financial revisionのSHA-256が一致し、読取失敗0。R2/D1/Worker/YFinance/Actionsの書込み・実行なし。report SHA-256 `590DB67F33B359DD6463C51582E90C56024EEFF884BF7243C23793F51C326F3B`。
- [ ] 新Workerへの切替、DOレート制限のcounter handoff、物理Android端末での確認、D1削減量の移行前後比較は未完了。公開Worker GETは一回の確認に留まり、D1旧経路から切替後の測定ではない。

Reports: `r2-income-advice-current-readback-20261105T180453Z.sanitized.json`（SHA-256 `E7DAB40E32AA4DD5452765D562508EEACA4A6779360C1841FB6649053016CBC2`）、`income-advice-worker-live-readback-20261105T180621Z.sanitized.json`（SHA-256 `2125ECA3F8AB11B25ED3B1109E2EAA2E29A5599821EBBA84A5C5E45FF73A28EC`）、`C:\Codex\r2-d1-ownership-audit-20261105`。

## 2026-11-06 JST（2026-11-05 UTC 18:16）Workers/Queues readback

- [x] ユーザー設定のWorkers/Queues read tokenがProcessから参照でき、値を出さずCloudflare GETを完了した。根拠: sanitized report `worker-queue-live-read-audit-20261105T181604Z.sanitized.json` SHA-256 `37044AA71A2F3801B09C2952DA6DD0B3B7F444AF8B0AA6B71195BF6F5762DE8C`。
- [x] 配信WorkerとQueue/DLQの時点状態を読み戻した。根拠: deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95` / version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%、DO rate limiter bindingなし。`diview-market-prices`とDLQは各0 messages / 0 bytes / oldest timestamp 0。
- [ ] productionは旧D1-backed Workerのまま。DO counter handoffとcutover、Worker invocation telemetry、Queueのcutover直前再確認は未完了。今回D1 SQL/R2 read-write/Queue mutation/Worker invocation-deploy/Actions dispatchは0。

## 2026-11-06 JST（2026-11-05 UTC 18:21）既存R2更新Workflowの実行状況

- [x] GitHub Actionsの最新20 runを読み、11/05時点のYFinance/IRBANK実行を確認した。根拠: sanitized report `github-actions-data-freshness-20261105T182126Z.sanitized.json` SHA-256 `C25405FAB294CF1266EF70C9B4264012322A2601BE691CD963527A90BD48DCC1`。最新runはYFinance #388（11/04 19:44:42Z開始、failure）とIRBANK #17（11/04 22:38:05Z開始、failure）。
- [ ] mainのYFinance cronは平日12:17 UTCだが、#388はcronより後の19:44 UTCに開始した。11/05 runは18:21 UTC時点で未観測。schedule delayの可能性を残し、run完了とR2 catalog/currentのmarket date readbackが揃うまでR2 freshnessを切替条件として保留する。Workflowを手動dispatchしていない。

## 2026-11-06 JST（2026-11-05 UTC 18:43–18:59）D1 usage・income-advice generation dry-run

- [x] UTC 2026-11-05のAccount Analyticsを読み取った。根拠: 3 D1合計590,802 rows read / 6,356 rows written、残り4,409,198、D1 SQL 0。sanitized reportのSHA-256は`70E6CE9134C9D67E8667F576F7332FA2D21936C456E28E267AFCEA750E7B7A0D`。
- [x] 現行generatorでR2保存データから3,938銘柄を全件再計算した。根拠: 52,334 GetObject、3,938 ListObjects pages、読取失敗0、R2 write/Worker/YFinance/Actions/D1 SQL 0。候補は現行3,203件から2,971件へ変わり、ETF 253件を除外、21件追加、共通2,950件にfield差分。report SHA-256 `60E73831D2543801DE0B6EE358EC8EE26AA7C7D46895D0E550B3AB160CEE45AE`。
- [x] 処理後にcatalog/advice currentと照合した。根拠: generation、market date、advice generated_at、候補item hashがすべて比較時と一致。financial_revisionは有効なschema 1だが処理前snapshotがないため、全source不変の証明には使えない。post-readback SHA-256 `03C5BBB1459EB78332059E2C793AB1BA1EB42034DD25E73E940F140537FA214B`。
- [ ] 新規候補1773の`jquants_product_category=021`はJ-Quants仕様でForeign Stocksと確認済み。income-advice対象に外国普通株を含めるかは未決。判断までは候補の本番公開を保留する。
- [ ] R2公開更新は明示的な実施判断とfresh preflight後に行い、catalog/current、income_advice/current、公開Worker API、Android cacheの世代・候補一致をreadbackする。今回のread-only確認ではR2を変更していない。

## 2026-11-06 JST（2026-11-05 UTC 19:29–19:51）Worker/Queue・D1使用量・income-advice再確認

- [x] ユーザー設定のWorkers/Queues read tokenを値を出さずに使い、稼働Workerのdeployment/version/bindingsとQueue設定・metricsをGETした。根拠: version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%配信中、Durable Object bindingなし。価格QueueとDLQはともにbacklog 0。価格Queueのretry上限3、max concurrency 1、retention 24時間、DLQは`diview-market-prices-dlq`。Cloudflare REST GETのみ。
- [x] D1 Account Analyticsを再取得した。根拠: UTC 2026-11-05の3 DB合計594,904 rows read / 6,507 rows written、残り4,405,096。予備100,000行を確保。D1 SQL 0。
- [x] 最新のincome-advice全件dry-runを行った。根拠: 4,391 catalog rows、484 ETF除外（国内453・海外カテゴリ023が31）、3,907候補を読み、2,971件を生成。R2 GetObject 52,214回 / ListObjects pages 3,907、読取失敗0、所要785.963秒。catalog/advice/revisionの開始・終了SHAがすべて一致。現R2 3,203件から253件除外、21件追加、共通2,950件に差分。R2 write/delete、D1 SQL、Worker/YFinance、Actions dispatchは0。
- [x] 海外ETFをlegacy `listed_security`からも確実に識別する修正とテストを両migration worktreeへ反映した。根拠: J-Quants `ProdCat=023`のみを既存分類へ`etf`として追加し、income-advice generator・通常catalog生成/復元/公開で`014`/`023`または`security_type=etf`を除外。foreign stock等のincome-advice対象へは自動拡張していない。final candidate 101 tests、canonical worktree 88 tests成功。
- [ ] 現R2 adviceは依然253 ETFを含むため、全件dry-run結果は公開済みR2と異なる。R2更新、Worker/API/Android cacheのreadbackは本番反映の明示承認後に行う。
- [ ] 1773はカテゴリ021=Foreign Stocksだが、income-advice対象可否は未決。#389が2026-11-05のR2 currentを公開した。11月2日価格は11月6日時点で3東証営業日経過、期限内。次回の市場営業日までに更新がなければ3営業日超となり通知対象から外れる。
- [ ] 本番Workerは旧D1-backed rate limiterのままでDO bindingなし。counter handoff、Worker telemetry、D1 read削減の移行前後測定、Queue cutover直前確認、本番R2更新/Worker deploy、物理端末検証は未完了。最終承認前の本番書込み・deploy・Actions dispatch・physical installは行っていない。

Reports（Git外、`C:\Codex\r2-d1-ownership-audit-20261106`）:
- Worker/Queue `worker-queue-live-read-audit-20261105T192958Z.sanitized.json` — SHA-256 `76FE142E0CEAACFB75FBC62B8DA28B202208430730E84AF263335F308B351278`。
- D1 usage `d1-daily-usage-20261105T193448Z.sanitized.json` — SHA-256 `A357D706645710B28D1F1E1D41488D736BE2E19B19C8080A5A655228A5AEE8C2`。
- Full dry-run `income-advice-generator-readonly-comparison-20261105T195151Z.sanitized.json` — SHA-256 `590DB67F33B359DD6463C51582E90C56024EEFF884BF7243C23793F51C326F3B`。

## 2026-11-06 JST（2026-11-05 UTC 20:18）Android instrumentationと既存R2更新runの監視
- [x] JDK 17でDebug/AndroidTestをビルドし、専用AVD emulator-5580でinstrumentationを実行した。根拠: JUnit XMLは141件、failure/error 0、1件skip（MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults）。テスト後にGradleが除去したアプリを同じAVDへ再導入し、MainActivityの起動を確認。ビルドAPKとinstalled base.apkはSHA-256 F6A4B258BA84A82E4999130DA7E080B0AFAFA9FF40FB029D51D955916467351Eで一致。実機・本番状態の確認ではない。
- [x] GitHub Actions YFinance schedule run #389（main b3f3a7b75bc54d30ec468fc085922791f99e340b）の完了を確認した。根拠: 2026-11-05T20:40:50Zにfailure終了。run: https://github.com/masacgt/Diview/actions/runs/42098641865
- [x] run終了後にR2 currentを読み戻した。根拠: catalog/advice/publication/manifestが同一generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`、market date `2026-11-05`。失敗runでもgenerationが公開された。
- [x] XML上で価格通知・オフライン動作の主要ケースも成功を確認した。例: duePriceAndAnnualDpsRefreshShareOneTargetedQuoteSnapshot、offlineNotificationUsesOnlyTheLocallySavedPriceCatalog、staleQuoteCancelsAQueuedAlertBeforeDelivery、cachedIncomeAdviceRemainsVisibleInWatchlistWhenOffline。

### 2026-11-05 UTC 20:30 GitHub #389と移行workflowガード
- [x] Actions APIはrun #389をin_progressと報告。ログの最終時刻は20:19:36 UTCで、進捗2600/4576、success 2516、failed 84だった。ログが20:30時点で更新されていないため進捗・最終件数は未確定。
- [x] main SHA b3f3a7b75bc54d30ec468fc085922791f99e340bのsync_v2.pyは、個別失敗を記録してもpublication objectsをR2へ書いた後、失敗があればexit 1を返す。runがfailure終了してもR2が未更新とは限らないため、終了後のreadbackを必須とする。
- [ ] GitHub integrationのmain branch protection読取は403 Resource not accessible by integration、migration Environment endpointはconnector未対応で確認できなかった。protection/independent approvalを確認できるまでmigration workflow dispatchをしない。

### 2026-11-06 JST（2026-11-05 UTC 20:49–20:54）run #389後の本番R2・トークン監査

- [x] 設定されたWorkers/Queue Read tokenを値を記録せず利用し、Worker version・bindings・Queue consumer/backlogを読み取り監査した。根拠: `worker-queue-live-read-audit-20261105T192958Z.sanitized.json` SHA-256 `76FE142E0CEAACFB75FBC62B8DA28B202208430730E84AF263335F308B351278`。version 84が100%配信中、DO bindingなし、Queue/DLQ backlogはいずれも0。
- [x] 11月5日のYFinance run #389はfailure終了したがR2 currentを更新した。根拠: generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`、4,576対象中4,420 success・156 failure、4,416 partial。catalog 4,391件・advice 3,201件、4 current objectのgeneration一致。
- [x] R2 catalogの価格・営業日鮮度を実データで集計した。根拠: 4,391件すべて正数価格とquote_dateあり。11/05基準のquote dateは11/02が3,892件（経過2東証営業日）、11/04が465件（1日）、11/05が30件（0日）。10/15・10/16・10/28の4件は3営業日超で、Android通知では除外される。11/06時点で11/02価格は3営業日経過のため有効期限内。
- [x] 公開income-adviceの分類を照合した。根拠: ETF 253件は国内カテゴリ014、外国ETFカテゴリ023は0件。候補コード側の除外修正は未公開のため、公開データの変更とは扱わない。
- [x] sanitized readback report `C:\Codex\r2-d1-ownership-audit-20261106\r2-publication-readback-20261105T205413Z.sanitized.json` は4回のR2 GETだけで生成、D1 SQL・R2 write 0。SHA-256 `ADEAF249213C27E8553C6CBFF248D7509758CC64733776C040588B02CAE2FA55`。
- [x] Account Analyticsで同じUTC日のD1使用量を再取得した。根拠: 2026-11-05T21:02:12Z、3 DB合計599,171 rows read / 6,789 written、5,000,000上限に対し4,400,829 rows remaining。D1 SQLは発行していない。report `C:\Codex\r2-d1-ownership-audit-20261106\d1-daily-usage-20261105T210212Z.sanitized.json` SHA-256 `C129BC08A4AD977F9EA0E0EB1AC01C52A88D62E2591CA5153CDF213F48D7B928`。
- [ ] 20:49 UTCの公開income-advice API GETはHTTPErrorだったが、当時のレポートは例外型だけを保存しstatus/bodyを残していない。API parityは未確認。旧配信版のrate-limitがD1を触る可能性があるため、追加GETを繰り返さない。
- [ ] main branch protectionとmigration Environmentのreviewer/branch policyはGitHub integrationで403/未対応、ローカルに`gh` CLIもなく未確認。独立承認ゲートを確認するまで移行workflowをdispatchしない。
- [ ] 1773（J-Quantsカテゴリ021）income-advice対象方針、部分失敗監視、DO counter handoff、production R2 write/Worker deploy、物理端末検証は未完了。最終切替承認前の本番書込み・deploy・Actions dispatch・D1削除・commit/pushは行っていない。

## 2026-11-06 JST（2026-11-05 UTC 21:02–21:40）read token・財務期間・income-advice scope再監査

- [x] Workers/Queues read tokenで本番状態を再取得した。根拠: sanitized corrected report `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261105T212218Z.corrected.sanitized.json` SHA-256 `56810AE04F3C5C06A3500BCA75CEFAEE4A0208141D1ADCC69E9990919F58E8BE`。Version 84が100%でDO bindingなし、price Queue/DLQはbacklog 0。Worker version詳細GETからbindingを確定した。
- [x] D1予算を再確認し、対象の2銘柄のみindexed annual queryを行った。根拠: 21:28 UTC Account Analyticsは603,217 rows read / 4,396,783 remaining。21:34 UTCの2 SELECTの`meta.rows_read`合計31。D1/R2 write 0。
- [x] D1 `financial_periods`の17欠損期間を現行R2 IRBANKとYFinance sourcesに照合した。根拠: 2期間はYFinance v2にあり、15件の10月期はどちらのR2 sourceにもない。公式発行会社の決算期は3月31日。期間キー・件数のみを記録し、財務数値は出力していない。sanitized reportsはspecの一覧を参照。
- [x] Income-advice generatorのETF一律除外を取り消し、仕様どおり既存のJP/JPY候補条件を維持した。根拠: `py -m unittest discover -s scripts/yfinance_r2 -p 'test_*.py'` 174/174成功。過去のETF除外時dry-run 2,971件は現行コードを表さない。公開R2 current 3,201件は未変更。
- [x] GitHub remote main readinessを再確認した。根拠: main SHA `b3f3a7b75bc54d30ec468fc085922791f99e340b`、`protected=false`、2つの新workflowはGitHub APIで404。Environmentは未認証GET 404のため独立承認・branch policy・secretsを確認できていない。
- [ ] 4つのfinancial value mismatchと、15件の非3月期D1 rowsの根拠を確認し、serving sourceへ混ぜるかarchive専用にするかレビューする。現時点ではlive R2へ補完しない。
- [ ] main protection・専用Environmentとrequired independent reviewer / main-only branch policy / Environment secretsをGitHub上で確認する。Workflowはmain未公開のためdispatchしない。
- [ ] DO counter handoff、Worker invocation telemetry、Queue cutover直前metrics、production R2 write/readback、Worker deployment、D1前後使用量比較、物理端末検証、1773対象判断は未完了。切替時はmaintenance windowとfresh preflight後、最終承認を待つ。

### 2026-11-06 JST（2026-11-05 UTC 21:45）GitHub Environmentとmain保護の最新読み取り

- [x] 匿名GitHub REST GETでmain protection・Environment一覧・repository rulesetsを確認した。`main`は`protected=false`、ruleset 0件。Environment一覧には`IRBANK_API_KEY`のみ（protection rules 0件）で、`d1-r2-migration` Environmentは存在しない。Environment secretの有無はこのGETでは確認していない。
- [ ] main保護、専用Environment、独立reviewer、main限定deployment policy、必要なEnvironment secretsを設定・確認するまではmigration workflowをdispatchしない。

## 2026-11-06 JST（2026-11-05 UTC 22:21–22:26）income-advice current再計算

- [x] 現行candidate generatorでR2全4,391 catalog rowsを再計算した。現R2 3,201件に対し3,223件、追加22件・除外0件・重複0。共通3,201件でcomputed_at全件、monthly estimate/observed months各21件、score fields 1件に差がある。catalog/advice currentとfinancial revisionは前後一致。D1 SQL/R2 write 0。report SHA-256 `9B8D9C173DE62A07324738F4E080579A6AEA69D2885C934DCAFA25A1973A4D32`。
- [x] 外国普通株候補1件は`category=jp`, `currency=JPY`, J-Quants category 021, `security_type=listed_security`で、現行generatorが候補として再生成し、R2 currentにはまだないことを確認。既存JP/JPY条件を維持し、新たな除外ルールは作らない。
- [x] ローカルWorker routeへcurrent R2 objectを渡してGET応答を照合した。HTTP 200、3,201件、JSON payload完全一致、D1 SQL 0。これはproduction Worker応答の検証ではない。report SHA-256 `B54C301386BC3B5D6F53A6813856CD9A0F1FD68AC971C3CC77D29DB4E5FC9BD9`。
- [ ] 22件の追加候補と共通21件の値差異についてsource provenanceをレビューし、現R2に公開する対象を確定する。production R2 write/API refresh/Worker deployは最終承認後。
- [ ] production endpointは新generationに対して未確認。Worker v84のD1-backed limiterと本番Workerの未変更状態を考慮し、cutover承認時の具体的なreadback手順とD1 usage preflightを維持する。

Reports（Git外、`C:\Codex\r2-d1-ownership-audit-20261106`）:
- `income-advice-current-generator-readonly-20261105T222105Z.sanitized.json` — SHA-256 `9B8D9C173DE62A07324738F4E080579A6AEA69D2885C934DCAFA25A1973A4D32`。
- `income-advice-worker-route-parity-20261105T222640Z.sanitized.json` — SHA-256 `B54C301386BC3B5D6F53A6813856CD9A0F1FD68AC971C3CC77D29DB4E5FC9BD9`。

## 2026-11-06 JST（2026-11-05 UTC 22:30）candidate Worker D1 call-site再監査

- [x] 候補Workerの`src/`全体でD1 binding/SQL callを静的検索した。市場検索・カタログ・銘柄詳細・分析・定期分析・定義のD1 SQLは0。残存はDO初回counter seedの一回限りreadと、support_feedback/email outboxの保存・管理・再試行・retention。healthのD1表示はbinding有無だけ。根拠: `src/index.js`のrate-limit guard/read、`feedback.mjs`、`feedback-email.mjs`の呼出し箇所。
- [ ] 本番version 84はD1-backed limiterのまま。本番から市場データ目的D1 readがなくなったことを確認するには、counter handoffとWorker deploy後のreadback、invocation telemetryが必要。


## 2026-11-06 JST（2026-11-05 UTC 22:18–22:54）Cron定期分析の終了条件監査
- [x] Workers Read / Queues Read tokenを値を表示せずに使い、本番WorkerとQueueを読み取り確認した。根拠: diview-irbank version 84、traffic 100%、Cron 17 * * * *、Durable Object bindingなし。価格QueueとDLQのpoint-in-time backlogは0。
- [x] Cron errorと対応するD1 run状態を照合した。根拠: 22:18:03.249 UTCにexceededCpu、wall 30,494 ms / CPU 2,050 ms。analysis runは22:17:32.836Z開始、finished_atなし、running、processed/failed 0。D1の3 read-only statementsはmetadata rows_read合計2、writes 0。query planはidx_scheduled_run_log_startedを使用し、backend_state.analysis_sourceは不在。
- [x] 応答後30秒のwaitUntil延長と約30秒のCron eventを照合し、候補・正本のscheduled handlerから定期分析を直接awaitする修正を追加した。根拠: Cloudflare公式Scheduled Handler / Workers limits資料と一致する実装。候補Worker tests 131/131、正本Worker tests 71/71成功。
- [ ] event outcome exceededCpuだけでは正確な停止原因を確定できず、span/detail errorもない。未完了D1 rowを完了履歴として移行せず保持する。
- [ ] 修正版の本番動作は未検証。production version 84は未変更。fixtureは1銘柄のみのため、4,000件超の処理がCronの実行上限内に終わるかを測定し、必要なら再開可能な分割処理を設計・試験してから最終切替承認へ進む。D1/R2 write、Actions dispatch、Worker deploy、commit/pushは行っていない。
- 監査report（Git外）scheduled-cron-d1-read-audit-20261105T2238Z.sanitized.json、SHA-256 912AE3F0487CB24FF3233D4184185F9B0C8E10B36F39BEEFEE5D42D528267091。22:38 UTC Analyticsは3 DB合計607,398 read / 7,090 written、残り4,392,602。account単位でありWorkerへの帰属はできない。


## 2026-11-06 JST（2026-11-05 UTC 23:15）Cron分析のsubrequest容量
- [x] 4,391銘柄相当のローカル合成R2分析で呼出し数を実測した。4,391 processed / 0 failed、R2 subrequests 173,460、D1 statements 0。wrangler.tomlとwrangler.cutover-paused.tomlを250,000に設定し、両方のWrangler dry-runが成功。
- [x] Worker契約テストを合成実測173,460回以上の上限確認に更新。
- [ ] in-memory mockは実時間・本番CPU・Cloudflare Cron完了を検証しない。production切替前に本番相当のwall time、CPU、subrequest countを測り、15分上限内での完了と再開方法を確認する。production Workerは未変更。
- 合成計測report（Git外）analysis-scale-probe-20261105T2315Z.sanitized.json — SHA-256 A24C35D6063F568FD97FE00D999EE0C9B7564FEDED59D13322A94554FDE224ED。

- [x] 容量基準更新後に候補・正本のWorker全suiteを再実行した。候補133/133、正本72/72成功。


## 2026-11-06 JST（2026-11-05 UTC 23:35–23:38）本番読み取り再確認
- [x] 設定済みWorkers Read / Queues Read tokenで配信versionとQueue/DLQを再確認した。根拠: version 84が100%、Durable Objects bindingなし、価格Queue/DLQはbacklog 0。version bindingsはversion detailのresourcesからname/typeだけを記録。
- [x] Account Analyticsの日次使用量を再取得した。根拠: UTC 2026-11-05、3 DB、611,542 read / 7,289 written、残り4,388,458、100,000 reserve確保。account-wide集計でありWorker帰属はできない。D1 SQLなし。
- [ ] production DO rate limiter handoff・Worker deploy・切替後のD1比較は未完了。migration Actionsとproduction write/deployは行わない。
- Worker/Queue report（Git外）worker-queue-live-read-audit-20261105T233753Z.sanitized.json — SHA-256 5FBB742EFD0BDD2D11CA948E6855B5944F18E9E41FE99FA8F3A5F6C039BB8C28。
- D1 usage report（Git外）d1-daily-usage-20261105T233547Z.sanitized.json — SHA-256 E40E6864707AB0A73BF19ED72907213AB488B2FDAFA87B763D83BE16310E5F0E。


## 2026-11-06 JST（2026-11-05 UTC 23:48–23:52）migration dry-run

- [x] 最新candidate CLIのanalysis-currentを100行上限でread-only dry-runした。根拠: HEAD b3f3a7b75bc54d30ec468fc085922791f99e340b、CLI SHA-256 26A53CC08B8B5995ADD0570C3FABE3F7EEBB9EAF21C8A742E54270F005F3888D。4 SELECT / 100 D1 rows / 100 planned R2 objects / conflict 0 / R2 write-delete 0。上限で止めたのでincomplete。Account Analyticsは事前611,542、事後611,642 rows read（+100、account-wide）。レポートSHA-256 DFD8349A9049ED1FF53C9096C93B9409D2A66F0286BB2EA428E9E866CBA502E、postflight usage SHA-256 44EF1B803733CC92886A719E9912D0F171DB805246736B2103BCA902A3CCE539。
- [ ] このbounded runを全件移行完了の証拠と扱わない。残りの全行・全フェーズのdry-run照合、R2書き込みの承認、Worker切替前確認は未完了。


## 2026-11-06 JST（2026-11-05 UTC 23:56）D1 schema audit

- [x] migration CLIのd1-schema-auditを本番read-onlyで完了した。根拠: 1 SELECT / 92 rows_read / schema object 27件 / R2 access 0。実行前のAccount Analyticsは611,642 reads、残り4,388,358。report SHA-256 DB4AD4D0848B71AFDD8B59D320AAE7CCC20266BA0CE7E3F835713B56291F5146。
- [ ] 23:56:40 UTCのpostflight Analyticsは事前値から変化なし。92行の増分はまだ集計で確認できないため、後続照会で反映を確認してから累積使用量を報告する。


## 2026-11-06 JST（2026-11-05 UTC 23:58–23:59）analysis-current

- [x] 最新candidate CLIでstock_analysis_state全248行のdry-runを完了した。根拠: complete=true、3 SELECT、248 rows read、248 planned R2 objects、conflicts 0、R2 write-delete 0。report SHA-256 F57730161C2396B24C4E371D67727369A2B79EEDEE9A1BCB5F1E38BF936566DD。
- [ ] postflight Account Analytics（23:59:59 UTC）はpreflightと同じ611,734 rowsで、今回の248行は未反映。UTC日次の切替後に前日分へ正しく反映したか照合するまでは、増分を推定加算しない。他フェーズ・R2書込・書込後readback・Worker切替は別の未完了ゲート。


## 2026-11-06 UTC 00:02–00:03 analysis-history

- [x] 最新candidate CLIでstock_analysis_history全38,855行のdry-runを完了した。根拠: 39 SELECT、38,855 planned R2 objects、conflict 0、R2 write-delete 0、complete=true。report SHA-256 42C59FB53309055B98ACA15640CCF1FA7DBDB11C1D48A559627F70037610BE45。
- [ ] 00:03:13 UTCのAccount Analyticsは当日0行のままで、dry-runのrows_readは未反映。budget preflightを維持し、集計反映後にpostflight totalを確認する。


## 2026-11-06 UTC 00:05–00:12 financial_periods

- [x] 最新candidate CLIでfinancial_periods全件をR2 sourceと監査した。根拠: 49,592 source rows / 99,184 D1 metadata rows_read / 248 SELECT / 3,061 R2 lookups。49,571一致、15期間欠落、4値差、2カテゴリ差、conflict 0、R2 write 0。report SHA-256 B5EBA4CEBB3518C21816418CDE1075F587D4BC2D07F831F229BF922C84745EEB。
- [ ] 15期間欠落と4値差の出典をレビューし、serving R2へ反映するか決める。解決までは既存R2データを上書きしない。
- [x] 実行前後のAccount Analyticsを記録した。根拠: preflight 38,862 / postflight 116,862 rows read、postflight残り4,883,138。account-wideの変動はquery metadata合計と一致せず、監査への帰属は未確定。report SHA-256 DB0098C658B4099E4C98B16C0757E36B0F1F5350EC063C8252CED28C9290077D。


## 2026-11-06 UTC 00:17–00:26 production Cron

- [x] production Cronの失敗とD1未完了状態をread-onlyで照合した。根拠: Workers Analyticsは00:17:32Z、status exceededResources、requests 1 / errors 1 / subrequests 0。D1 operational auditは655行すべてrunning、finished_atなし、processed/failed 0。最新started_at 00:17:32.801Z。Worker report SHA-256 A73714737509A302185B7472A555B0324CA5E4E61CE1EB9DF4CE7809D1FA3CDA、D1 report SHA-256 AA5038983054CEBAB4FB53D25FE674E0B24E13D0558B0E55D946727BB2538263。
- [ ] 655件のrun行は未完了のまま保持し、完了履歴としてR2へ移行しない。候補Worker修正の本番runtime検証とDO limiter切替は未完了。
- [x] 当日D1 usageを照会した。根拠: 00:24:49 UTCに116,862 read / 2 written / 4,883,138 remaining。account-wide値である。report SHA-256 6B22B48EA71067B8AF71383D2712FE37A3023F8FFCA4651F41BA5F8AA7CAE537。


## 2026-11-06 UTC 00:28–00:29 operational migration audits

- [x] scheduled-runs dry-runで完了済み行がないことを確認した。根拠: 655 rows_read、1 SELECT、source rows 0、planned R2 objects 0、complete=true。report SHA-256 0DA3E494CA419AE99CC927816B8F41AFAE0070EBA9EB1AD85DF86E5C5E759DE8。
- [x] backend_state、market_price_refresh_runs、market_price_refresh_batchesを集計監査した。根拠: backend_state 0行、run 2行（partial 1 / running 1）、batch 2,080行（complete 2,020 / failed 60）。run report SHA-256 E79A6339830E7C2983AE97FF7CB97F2623587EC19107981B36DA07E83A29D122、batch report SHA-256 10B0F1A9E6890FF12DF4572CA1C9472346979C85392577AD6C8A54C2E52D2F25。
- [ ] running価格更新runとbatch・Queueの関係、外部producer、保持方針を確認するまで古いrunを移行済み完了履歴と扱わずD1に保持する。
- [x] Account Analyticsを再確認した。根拠: 00:29:30 UTC 116,862 reads / 2 writes / 4,883,138 remaining。00:24から変化なしで、個別auditへの帰属は未確定。report SHA-256 692E742F7A63C2899A64E3C6CC178CDBDE3185DC8562A4944A4093B6C281E5D0。

## 2026-11-06 JST 09:32 Workers / Queues

- [x] Workers deployment/versionと価格Queue/DLQをread-onlyで再確認した。根拠: version 4981c1e0-e337-4392-9f5b-909b52c5bc0cが100%、D1/R2/KV/Queue/email binding、DO limiter bindingなし、両Queue backlog 0。sanitized report SHA-256 0AFB38D3D6467397966B409B2106B838FAE6094394BC7892771165EC8FAAC489。
- [ ] 本番WorkerへのDO rate limiter切替とCron runtime検証は未実施。Queueのゼロbacklogは一時点の観測で、移行・処理成功の証明にしない。
## 2026-11-06 GitHub migration protection

- [x] main branch protectionとrepository rulesetsをread-onlyで照会した。根拠: remote main b3f3a7b75bc54d30ec468fc085922791f99e340b、protected=false、protection.enabled=false、rulesets空。候補migration workflowはローカル未コミットでremote mainに未反映。
- [ ] GitHub上でmain protectionを有効にし、d1-r2-migration Environmentのrequired reviewer / prevent self-review / main限定branch policyを確認するまでdispatchしない。Environmentの実設定は今回確認できていない。workflow preflightは未保護branchやEnvironment条件不足をD1 query前に拒否する。
## 2026-11-06 UTC 00:30–00:45 usage / Cron follow-up

- [x] D1 account usageを再取得し、4.5M read reserveを確認した。根拠: 00:42:55 UTC 150,193 reads / 200 writes / 4,849,807 remaining。00:29:30から増えた33,331 readsと198 writesの帰属はaccount-wide集計から確定できず、00:45:48 postflightは同値。report SHA-256 8038E5C4FC3FF0DE3EC97E6B8D78FF306A2EF6FB3EFEAB0C6D7800A18650ED0B、postflight SHA-256 54ADB6C332CEEA9FBCD04CDCE800493E07EB2CF1A04E6826B4CAF33B4649CCF0。
- [x] diview-irbank invocationとscheduled_run_logをread-onlyで照合した。根拠: 00:30–00:43 UTCのadaptive / scheduled metricsはいずれも0 records、scheduled_run_logは引き続き655件全てrunningでlatest started_at 00:17:32.801Z。audit report SHA-256 F4EB1A1A9B3F4AEE16BE5B1C4CA80AC67D6BC6CA45FDF1677BB19C7BCE08628F。metrics SHA-256 14B7A145E069D529171F20D3C47F22D0A47FBF3A12F1947897B94B5483F5ECD1 / 5CFBE05DA581B49DE585CDBDF045E22B2845FD777A1897071ED8AF4899F859B2。
- [ ] 増分の発生元は未特定。Analytics集計値だけで監査・Cronへ帰属させない。


## 2026-11-06 JST 10:04 Workers / Queues read-only readback

- [x] Workers Scripts Read / Queues Read credentials are process-visible and both Cloudflare GET APIs returned HTTP 200; values were not printed. Current deployment is version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%, without a Durable Objects rate limiter binding.
- [x] `diview-market-prices` and its DLQ each had zero backlog messages/bytes at the observation time. This point-in-time metric does not establish processing history or migration completion.
- [ ] DO limiter production cutover and Cron runtime verification remain pending. No Worker invocation, D1 SQL, mutation, Actions dispatch, or deploy was performed.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T010416Z.sanitized.json`, SHA-256 `e70c6bc459752863b1c752e4b228c1ce940c79de76a1fb9e1a7525b5265ef0a9`.


## 2026-11-06 JST 10:04 candidate source D1 call-site scan

作業ツリー `C:\Codex\Diview-worktrees\r2-d1-ownership-final-20261104`（branch `codex/r2-d1-ownership-20261104-final`、HEAD `b3f3a7b75bc54d30ec468fc085922791f99e340b`。対象ソースには未コミット変更あり）の`data/irbank/cloudflare/src/`をD1 binding・SQL call-siteで検索した。`securities`、`financial_periods`、`dividend_events`、`stock_analysis_state/history`等の市場データ参照は検出されなかった。

検出した残存経路は、DO rate limiterの移行期間中に`api_request_log`から当該bucketを一度読むbootstrap、問い合わせの保存・管理・90日保持とメール配信状態、health応答のD1 binding有無表示。レート制限bootstrapは設定した期限・fenceに依存し、D1読取を伴う移行用経路として残る。これはソース検索の結果であってproduction runtimeの計測ではない。現行production version 84にはDO bindingがなく、切替後の市場API・CronのD1非依存性は未確認。


## 2026-11-06 JST 10:15 candidate local verification

- [x] Candidate Worker tests: Node 24.21.0で132 passed / 0 failed。`node --test test/*.test.mjs test/*.test.cjs`。
- [x] Migration CLI / D1 usage / workflow-guard Python tests: 88 passed。`py -m unittest scripts/test_migration_workflow.py scripts/test_d1_daily_usage.py scripts/test_migrate_d1_to_r2.py`。
- [x] `scripts/yfinance_r2`全テスト: 174 passed。`py -m unittest discover -s scripts/yfinance_r2 -p 'test_*.py'`。
- [x] Android JVM tests: JDK 17 (`C:\Users\htdn\.jdks\ms-17.0.20.1`)を指定して強制再実行、277 tests / failures 0 / errors 0 / skipped 0。Android instrumentation Kotlin compileも同JDKで成功。
- [ ] 端末/emulatorでのinstrumentation実行、APK install、production readback・D1削減量測定、R2 write/Worker deployは未実施。Gradle SDK XML v4とdeprecated API、pandas timezoneのwarningはあるがテストは成功。


## 2026-11-06 JST 10:15 financial-period discrepancy disposition

- [x] Financial period anomaliesを移行判断用に再分類した。根拠: 全49,592行のaggregate statusは49,571一致 / 15 `source_period_missing` / 4値差 / 2 category mismatch。by-display補正probeは2 SELECT / 27 rows / meta rows_read 60、R2 overview 2 GET、write 0。report SHA-256 `B9441C69C9BD9CF26C4B34200803319F23488C0C5CBC1F8FECF533762103BBD3`。
- [x] 1301のFY2017/03・FY2023/03・FY2024/03、2708 FY2014/03のD1差分と、2708の2011/10–2025/10他社混入行をserving R2へ移行しない。1301 FY2025/03・FY2026/03のD1値もsource矛盾が解決するまで移行しない。既存R2修復値は保持。
- [ ] 「17 missing」は対象2銘柄のIRBANK overview欠落17行とaggregate audit statusを混同した表現。全source statusの15欠落と2 category mismatchを区別する。2 category mismatchの銘柄/期間特定、D1 retention/archive判断、全sourceの必要coverageは未完了。D1/R2の本番データ変更は行わない。


## 2026-11-06 JST 10:27 production Worker source / D1 usage readback

- [x] 配信中Workerのソース本文をread-only GETで静的確認。deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` 100%。D1 `DB.prepare` call sites 31（SELECT 12 / DELETE 8 / INSERT 6 / UPDATE 5）。`securities`、analysis state/history、scheduled runs、definitions、`api_request_log`等が残り、`RATE_LIMITER` / `RateLimitCounter`なし。本文・秘密値は出力せず、module SHA-256 `262388d871931f4321bb3946937cd9faa2e72313297ef51ef60f4629bea4ad9a`を記録。
- [x] Account Analyticsを再照会。01:27:16 UTCに155,004 reads / 350 writes / 4,844,996 remaining。database別内訳とsanitized reportsはspec記載。account-wide値なのでWorkerへ帰属しない。
- [ ] 本番は旧D1-backed版のまま。candidateコードのD1整理は未deployで、実行回数・本番D1行読み取り削減、DO handoff、Cron正常性は未検証。Worker deployや本番更新は最終承認待ち。


## 2026-11-06 JST 10:33 Android candidate build

- [x] JDK 17 (`C:\Users\htdn\.jdks\ms-17.0.20.1`)で`assembleDebug --no-daemon --rerun-tasks`を実行し、37 tasks executedで成功。APK SHA-256 `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`。
- [ ] Device/emulator installとinstrumentation実行・UI確認は未実施。最終承認後に実施する。既存APKは上書き前に別保存しhashを確認済み。


## 2026-11-06 JST 10:36 Wrangler cutover configuration dry-run

- [x] Wrangler 4.136.1で`wrangler.toml`と`wrangler.cutover-paused.toml`の`deploy --dry-run`が両方成功。DO/D1/R2/KV/Queue/emailとhandoff plain-text varsのbundle bindingを確認。
- [ ] Dry-runはlocal validationだけ。DO migration・Cron変更・本番deploy/route検証は未実施で、最終承認待ち。


## 2026-11-06 JST 10:42 live rate-limit handoff snapshot

- [x] Read-only KV/D1 handoff snapshot: active limiter window 60s、直近60秒の`api_request_log` active bucket 0、D1 query metadata rows_read 1、D1 writes 0。Account Analytics pre/postは155,012 / 4,844,988 remainingで同値だが1行のreadを帰属しない。bucket keyは取得・出力していない。
- [ ] Active bucket countは瞬間値。D1 fence適用直前にfresh Analytics preflightと同じbounded SELECTを再実行し、DO import deadlineを新しいfence時刻から計算する。
- Evidence outside Git: `rate-limit-active-counter-snapshot-20261106T014225Z.sanitized.json`, SHA-256 `60aa1b3d6cd5af4ceb43a7f97a5aee48003c7d3352d38f1221e0b0d37b54a49d`.

## 2026-11-06 JST 10:47 Cloudflare tokens and GitHub migration gate

- [x] Workers Scripts Read / Queues Read token were confirmed available in the Codex process environment without printing values; production Worker content/deployment and Queue/DLQ GETs succeeded. The deployment is version 84; Queue backlog was zero at the prior readback.
- [x] Read-only GitHub Settings check: IRBANK_API_KEY is the only Environment; d1-r2-migration is absent. The Branches page has no protection rules, repository rulesets API is empty, and main API returns protected=false at SHA b3f3a7b75bc54d30ec468fc085922791f99e340b. The migration workflow remains local/uncommitted; no workflow was dispatched.
- [x] Verified the candidate workflow already grants Actions:read, which is the GitHub REST permission required for its read-only Environment and deployment-branch-policy preflight. No additional write permission is needed for those checks.
- [ ] Before publishing or dispatching the migration workflow, configure main branch protection, an independent required reviewer with self-review prevented, and a d1-r2-migration Environment restricted to main. Required Environment secrets: D1_R2_MIGRATION_ACCOUNT_ID, D1_R2_MIGRATION_READ_TOKEN, D1_R2_MIGRATION_ANALYTICS_READ_TOKEN, D1_R2_MIGRATION_R2_ACCESS_KEY_ID, D1_R2_MIGRATION_R2_SECRET_ACCESS_KEY. Limit R2 access to the diview-market-data target. Local process environment values do not transfer to GitHub Actions.
- [ ] Obtain final approval before production R2 writes, Worker deployment, or physical-device installation. Only local candidate verification is complete; no remote or device change was made.

## 2026-11-06 JST 11:13 legacy D1 state and definition precedence

- [x] Bounded D1 inventory for legacy/device/definition tables: clients 1, push_devices 0, watchlist_targets 0, notification_events 110, notification_deliveries 0, diagnosis_bands 15, valuation_scenarios 100; 7 SELECTs, metadata rows_read 229, per-table LIMIT 501. No raw user fields or definition values were emitted.
- [x] Read-only aggregate: the single client record is active; all 110 notification_events have delivered_at NULL and occurred 2026-10-05 through 2026-11-02; delivery rows are zero. Do not delete or deliver these events by assumption.
- [x] Confirmed live Worker definition precedence: present KV keys are returned before D1 fallback. The 10 unique diagnosis KV rows match D1's 10 unique keys, while D1 has five duplicate keys; valuation has 100 shared IDs but 50 differing rows. Keep KV as migration source to preserve current active behavior; do not overwrite KV/R2 with D1 fallback values.
- [ ] Decide how to retain/archive the 110 legacy notification events and one client registration. Keep D1 rows unchanged until external-use, backup, and retention requirements are resolved.
- [ ] Review whether duplicated diagnosis rows and differing D1 valuation fallback rows need a non-serving R2 archive or can remain in D1 under retention policy. Serving definitions remain the active KV values until their verified R2 readback.
- [x] Fresh D1 schema audit: one SELECT, meta.rows_read 92, 27 schema objects, zero R2 operations. A mismatched shared checkpoint stopped the first attempt before D1 SQL; the successful rerun used a unique external checkpoint and did not overwrite existing state. Report SHA-256 a50851a12995e1df9be68640244fa380a533ea2bfa078b700a883824c2fb6e43.
- Evidence outside Git: definition comparison SHA-256 d5fe4c1ac80a3e666ceeb0b70a530cd593131d6fe437ded0f3e7697550586245; bounded legacy-state report SHA-256 84f3504257f8514539e7f608dae93c102a254917994569f718cbca84107e44b1; client/event aggregate report SHA-256 708000c9ec6b04d478f45f72183dc264351919419879b65da67a56949500b561.

## 2026-11-06 JST 11:20 final Workers/Queue readback after token setup

- [x] CLOUDFLARE_WORKERS_READ_TOKEN と CLOUDFLARE_QUEUE_READ_TOKEN がCodexプロセス環境にあり、値を表示せず本番GETを再確認した。2026-11-06 02:19:58 UTC時点でWorker `diview-irbank` deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`、version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`が100%配信中。D1/KV/R2/Queue bindingsが残り、Durable Object rate limiter bindingはない。
- [x] `diview-market-prices` はproducer 1 / consumer 1、DLQ `diview-market-prices-dlq` はproducer 0 / consumer 1。両Queueのretentionは86,400秒、観測時のbacklog_count/bytesは0。
- [ ] 本番WorkerはD1 bindingを保持し、DO limiter切替・Cron runtime・移行は未完了。Queue値は時点観測。Cloudflare書込み/deploy、D1 SQL、Actions dispatch、端末installは行っていない。
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T021957Z.sanitized.json`, SHA-256 `9D019C77ECA4AE7D420DB79F72CAC938AB1CCB4A2156D8F2C17EA93B6C8C35A5`.
## 2026-11-06 JST 11:30–11:41 financial-period source audit

- [x] Re-ran the complete read-only financial-period source audit with a 100,000 D1 row-read cap and 4,500,000 account-wide reserve. Evidence: 49,592 rows checked, metadata rows_read 99,184, 3,061 R2 GET lookups, 0 objects planned/written, 0 verification failures; 49,571 match, 15 source-period-missing, 4 source-values-differ, 2 source-category-mismatch. Report file SHA-256 `7C89486518D93F46E1F5E3A557C12F7F1EB01934825D6C021F3C3BDF53CD90DD`.
- [x] Identified and compared the two category-mismatch keys using one bounded D1 SELECT (13 metadata rows read) and one R2 GET. Both are consolidated J-GAAP annual rows for 1301 FY2025/03 and FY2026/03. Their quarterly and semiannual full-year R2 candidates each differ in 13 of 15 common fields; no missing fields or unit conversion explains the difference. Targeted report SHA-256 `15E7DBBB508AAC259967C08798EABE347696491D24059F3EFC99CB0859718E66`.
- [ ] Keep those D1 rows out of serving R2 until source identity/provenance is resolved; retain the source rows until backup and retention requirements are decided. Account Analytics remained above the 4.5M reserve (259,001 reads / 4,740,999 remaining at 02:41:39 UTC); account-wide totals cannot be assigned to the audit queries. No writes, deploy, dispatch, or install occurred.
## 2026-11-06 JST 11:48–12:03 income-advice current-state verification

- [x] Re-read current R2 catalog/advice/financial-revision objects. Evidence: generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba` remains current; catalog date 2026-11-05, 4,391 unique catalog codes, and 3,201 unique published advice codes (2,887 equity / 253 ETF / 61 REIT). Advice object SHA-256 `a7fcd2332d9e9a470c76eb837293d2863626096411e18f9e4401bf5c84caec19`, unchanged from prior full recomputation route readback. Corrected pointer report SHA-256 `A4A14EC15FDFA1BA22BE5A6E550C3B2EADA9DC51C82C4025EEA5EB64145FD527`.
- [x] Verified current local Worker route parity against the R2 payload: HTTP 200, exact payload match, 3,201 unique items, D1 SQL 0. This used a local harness, not production Worker. Report SHA-256 `C9ED43C11D50493D609D6F942A5ED6C8F01869097D8CA8C585C0821E041A6F44`.
- [x] Rechecked code 1773: JP/JPY, listed_security, product category 021; candidate recomputes but is not in published advice. No D1 SQL or writes. Report SHA-256 `5F6BF3670B725377D04129608A906CC438F54FBB2A1D4659654C69EC84A14C74`.
- [ ] Preserve current R2 advice object; its full-computation comparison has 22 additions and changes among all 3,201 common entries. Do not publish those changes or 1773 without separate review. R2/current pointer remains unchanged.
- [ ] Keep the stale ETF-exclusion edit in the divergent 2026-11-03 worktree intact pending branch review; current main-based candidate generator at `b3f3a7b75bc54d30ec468fc085922791f99e340b` is unchanged and uses the documented JP/JPY filter. The partial stale-tree scan was stopped at 2,300/3,907; it is not a completed verification and has no final report.

## 2026-11-06 JST 12:30 isolated Android instrumentation verification

- [x] Ran `connectedDebugAndroidTest` from the main-based candidate with JDK 17.0.20.1 on a new API 35 Google APIs AVD. Gradle completed successfully; the XML result reports 141 test cases, 0 failures, 0 errors, and 1 skipped. The skipped case was `MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults`; the XML has no skip reason. Feature cases for cached offline watchlist advice and offline notification from the local price catalog passed.
- [x] Installed the candidate debug APK on the disposable AVD, launched `com.kurogane.dividend/.MainActivity`, and verified the installed `base.apk` SHA-256 matches the built APK: `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`. Screenshot: `C:\Codex\r2-d1-ownership-audit-20261106\diview-disposable-screen.png` (SHA-256 `B15BDB63DAFCF07E9B7F02A79A66CBB63759BDC9F59B125D6D08D77C5A2B5844`). Full sanitized record: `C:\Codex\r2-d1-ownership-audit-20261106\android-instrumentation-audit-20261106.json` (SHA-256 `EB34A22C73FC7ED7EC24C0363F5534F46BDE895386D81F7CF91172D66FC80206`).
- [ ] Reconcile the test-count difference: Gradle console reported 142 finished tests while the XML contains 141 test cases (1 skipped). Both report zero failures/errors; retain the discrepancy until the reporting difference is understood.
- [ ] Physical-device install, production D1/R2 writes, Worker deployment, and migration workflow dispatch remain unperformed pending the specified final approval.


## 2026-11-06 JST 12:46 GitHub migration workflow gate review

- [x] Rechecked the local candidate workflow: `workflow_dispatch` only, main-only dispatch, branch-protection preflight, required Environment reviewer/self-review prevention, main-only deployment branch policy, bounded D1 row budget, separate D1/Analytics/R2 credentials, and exact `WRITE_R2` confirmation. The focused workflow guard suite passed 10/10.
- [x] Revalidated permission scope against GitHub REST docs: branch GET uses `Contents: read`; Environment and deployment-branch-policy GETs use `Actions: read`, matching the workflow. `actionlint` is not installed. The public repository's live `main` branch still reports `protected=false`; the prior direct Environment read found `d1-r2-migration` absent at 2026-11-06 JST 10:47. This turn's GitHub connector did not permit refreshing the Environment endpoint.
- [ ] Configure main protection and the `d1-r2-migration` Environment with an independent reviewer, self-review prevention, and main-only deployment policy before any workflow dispatch.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\github-migration-workflow-static-audit-20261106.json`, SHA-256 `B2ED7E208FCBA2B0EA70BB2309AB7D319CAA2CB4953622AB1B72618D8DDCB3A6`.

## 2026-11-06 JST 12:52 live Worker/Queue readback and deploy guard

- [x] Workers Scripts Read and Queues Read GETs at 03:52:37 UTC confirmed deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%, D1/KV/R2/Queue/email bindings, and no Durable Object limiter binding. Both price queue and DLQ had zero backlog messages/bytes at read time; these metrics are point-in-time only.
- [x] Found the generic README deploy command omitted the two required handoff vars even though `wrangler.toml` binds D1 and the DO limiter. Updated the deploy guide to prompt for the exact recorded timestamps and pass them on every deploy/rollback; documented that omission fails closed.
- [ ] Production remains on the D1-backed version. Do not deploy until the D1 fence, handoff pair, migration data, and final approval gates are ready. No D1 query, R2 write, queue mutation, Worker invocation/deploy, Actions dispatch, or device installation occurred in this readback.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T035235Z.sanitized.json`, SHA-256 `61FDAC860E5D5CD4B29F82DBD109E2E41CAAFD60E746D25196A04549584E67FD`.

## 2026-11-06 JST 12:52–13:03 Worker CPU-limit readback

- [x] Read-only Worker settings GET during 03:52–04:03 UTC reports `usage_model=standard`, observability enabled with 100% head sampling, the hourly Cron, and no explicit `limits.cpu_ms`. The account's effective Workers plan/CPU ceiling is not established by this endpoint.
- [x] Reconciled scale evidence: local synthetic R2 analysis processed 4,391/4,391 with 173,460 subrequests and zero D1 statements; elapsed 1,388 ms is local mock time only. It does not prove deployed CPU usage or Cron completion.
- [ ] Resolve the Workers plan CPU and subrequest entitlements before restoring Cron. The candidate requests a 250,000 subrequest cap; the 4,391-security mock used 173,460 R2 subrequests, above the Free internal-service cap. The settings GET returned no explicit CPU limit; prior hourly `exceededCpu` events reported about 2,050 ms CPU and 30–36 seconds wall. Validate the full-catalog pass under a production-equivalent runtime or implement bounded resumable chunks if the plan cannot support it. Do not use incomplete D1 runs as completed migration history.
- No Worker invocation, D1 query, Cloudflare mutation/deploy, R2 write, queue change, Actions dispatch, or device install was made for this settings check.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-settings-cpu-read-audit-20261106.sanitized.json`, SHA-256 `AFDB28BD74B92C66F17B52FCFC7F399E19EC25805DD7EA3258B7DD995EADF826`.


## 2026-11-06 JST 13:14 scheduled-run migration guard

- [x] Confirmed the scheduled-runs query filters `finished_at IS NOT NULL` and a 365-day window. The two focused tests passed: `test_scheduled_run_migration_preserves_the_365_day_retention_window` excludes a fixture with status=running and `finished_at=null`; `test_scheduled_run_pointer_is_not_published_until_the_phase_is_complete` keeps the current pointer absent until the phase completes. No production D1 or R2 was accessed.
- [ ] The production Cron invocation was reported as `exceededResources`; its exact resource cause and full-catalog CPU/subrequest feasibility remain unresolved. Keep incomplete D1 run rows out of completed history.

## 2026-11-06 JST 13:51 production Worker, Queue, and R2 catalog readback

- [x] Read-only Cloudflare GETs confirmed `diview-irbank` deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, version 84 (`4981c1e0-e337-4392-9f5b-909b52c5bc0c`) at 100%. Runtime is `standard`, `limits.cpu_ms` is unset, and deployed bindings include D1/KV/R2/Queue with no Durable Object limiter.
- [x] Downloaded the deployed Worker source and verified the live `/api/v1/search-catalog` path is D1-independent: the path is allowlisted by `d1IndependentRead`, its catalog and dividend-index helpers read `MARKET_DATA` R2, and `backend-config:v1` exists in the bound KV namespace (HTTP 200), so `backendConfig` returns before its D1 fallback.
- [x] One public GET to `/api/v1/search-catalog` returned HTTP 200, generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`, 4,391 items / 4,391 unique codes, generated at `2026-11-06T05:40:28.679508+09:00`. This confirms the production Worker serves the current catalog generation.
- [x] Queue metrics GETs returned backlog_count 0 for `diview-market-prices` and `diview-market-prices-dlq`; these are point-in-time metrics.
- [x] The 24-hour Workers Analytics report (2026-11-05T04:25:00Z–2026-11-06T04:25:00Z) contains 24 failed invocations, all labeled `exceededResources`, each with raw `cpuTimeUs=2050000`. The exact exceeded resource remains unresolved; an unset per-version CPU limit does not establish the account's effective plan limit.
- [ ] Keep full-catalog Cron feasibility open. Do not infer that 173,460 mocked subrequests or local elapsed time proves production runtime capacity.
- [ ] Do not call the live `/api/v1/income-advice-candidates` route for this readback: version 84 applies its D1-backed rate-limit write to that GET. Its current R2 object was last independently verified in the earlier 02:48 UTC pointer read; no new advice endpoint call was made here.
- This audit made one public Worker GET and read-only control-plane/KV/Analytics requests. D1 queries/writes, R2 writes, queue mutations, Worker deployment, workflow dispatch, and physical-device installation: 0.
- Sanitized evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-r2-readback-20261106T045131Z.sanitized.json`, SHA-256 `8E8E629BFBE2742F7F5F43959F54DE0D02815B9425AF19686279A1485C94ABCC`.


## 2026-11-06 JST 13:57 account-wide D1 usage read

- [x] Account Analytics GraphQL reported 267,326 rows read and 956 rows written on 2026-11-06 UTC across 3 databases. Against the 5,000,000-row daily allowance, 4,732,674 rows remained; the 4,500,000-row reserve gate passed.
- This is account-wide usage and cannot be attributed to a specific Worker/API request. The query used Account Analytics only; D1 SQL statements executed: 0.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\d1-daily-usage-20261106T045710Z.sanitized.json`, SHA-256 `92D9DA78310C6C3775797FB4207C195E5FB8AF12B00983A9FF2BF3406C5C77EB`.


## 2026-11-06 JST 14:01 legacy notification/client route review

- [x] The active production Worker v84 source bundle contains no SQL references to `clients`, `push_devices`, `watchlist_targets`, `notification_events`, or `notification_deliveries`. Its `/api/v1/clients/register`, `/push-devices`, `/watchlist-targets`, and `/notification-events` paths return `410 device_managed` before accessing D1. The current Android source tree has no references to these server routes.
- [x] The earlier bounded D1 inventory found 1 client last seen 2026-10-04, 0 push devices, 0 watchlist targets, 110 undelivered events dated 2026-10-05 through 2026-11-02, and 0 delivery rows; it emitted no client IDs, device tokens, credentials, or payloads.
- [ ] No current Worker/app consumer was found, but older installed apps and external D1 writers are not proven absent. Do not replay the undelivered events or move them into serving R2. Preserve the D1 rows until external-use, backup, and retention requirements are explicitly resolved.
- Production source response outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\production-worker-content-20261106.bin`, SHA-256 `8E1DA719D13DCA5FD3F349A70A2A84CE287265B1ACADFB60B4D250390AE90C7B`. No D1 SQL or data mutation was made for this route review.


## 2026-11-06 JST 14:08 Worker entitlement follow-up

- [x] Worker details GET returned `limits=null`; version 84 reports `usage_model=standard` and no explicit `limits.cpu_ms`. Account Worker settings report `default_usage_model=standard`. These fields do not establish the effective plan or customized limits.
- [x] The account subscriptions GET returned HTTP 403 with the configured Analytics token; Cloudflare requires Billing Read for that endpoint. No subscription/billing data was returned.
- [x] Current Cloudflare limits documentation says internal service subrequests default to 1,000 on Free and 10,000 on Paid, with Paid configurable up to 10 million. Candidate requests 250,000; its local synthetic full pass used 173,460. That exceeds the Paid default and needs the account's actual entitlement/configuration verified. The documentation also sets hourly-or-less-frequent Paid Cron CPU allowance up to 15 minutes. Source: https://developers.cloudflare.com/workers/platform/limits/ (updated 2026-10-29).
- [ ] Resolve the generic 24-hour `exceededResources` results and correlate the earlier detailed `exceededCpu` event; do not infer the cause or resume the full-catalog Cron before the effective plan and invocation trace are known.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-entitlement-readback-20261106T050840Z.sanitized.json`, SHA-256 `7FAB8DC04473D3FA16FBE598092DE8131755F35FE028DB9E749D2BC1875B2350`.

## 2026-11-06 JST 14:47–14:54 latest Cloudflare and scheduled-run audit

- [x] Workers Scripts Read / Queues Read token visibility and read-only API access were confirmed without printing token values. At 05:51:22 UTC, deployment 7731b2d6-75e8-4b13-8985-9f47a4960f95 served version 4981c1e0-e337-4392-9f5b-909b52c5bc0c at 100%; bindings include D1/KV/R2/Queue and no RATE_LIMITER Durable Object. Both market-prices queues had 86,400-second retention and point-in-time backlog 0.
- [x] Refreshed account-wide D1 usage at 05:54:01 UTC: 272,313 rows read of 5,000,000; 4,727,687 remained across three databases. The Analytics total is not attributable to an individual Worker request.
- [x] Rechecked scheduled_run_log using the read-only dry-run audit: seven SELECTs, 660 D1 rows read, no R2 operations; all 660 rows remain running with no finish time and processed/failed totals 0. Latest started_at is 2026-11-06T05:17:32.891Z. Account Analytics for the D1 database increased by the same 660 rows between preflight and postflight; the other two database totals did not change.
- [ ] Production Cron and plan remain blockers. Candidate requests 250,000 subrequests and its synthetic full pass used 173,460. Cloudflare documentation sets Free maximum subrequests to 50 with a separate 1,000 internal-service cap; Paid defaults to 10,000 and permits configuration up to 10,000,000. The account plan is unverified (Billing Read was denied), so do not claim the full-catalog Cron works on Free or deploy it before confirming entitlement and resolving the recurring incomplete runs. Source: https://developers.cloudflare.com/workers/platform/limits/
- Direct instrumentation on disposable emulator-5554 completed with OK (141 tests), zero failures/errors, and one live-network assumption skip. JDK 17.0.20.1 was used; no physical device was touched.
- Evidence outside Git: Worker/Queue readback C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T055124Z.sanitized.json (SHA-256 569554E39A686FB77021DEE37B18B26046B3B07DECDA67422D06EB27EC3998C2); scheduled-run report scheduled-runs-current-20261106T055016Z.report.json (SHA-256 E0D8D266AEDB4625F1237453497AFE7AC9F1B9435F1154C3636DA1474284B449); usage report d1-daily-usage-20261106T055400Z.sanitized.json (SHA-256 3CE14832650BF1FBFC2CB29D34AB7A0027E642E45AA19E36E5D5E10395028069).
## 2026-11-06 UTC 06:11–06:13 refreshed Worker, Queue, and GitHub gate readback

- [x] After `CLOUDFLARE_WORKERS_READ_TOKEN` and `CLOUDFLARE_QUEUE_READ_TOKEN` were configured, read-only GETs succeeded without printing token values. Deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95` still serves version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%; no Durable Object rate-limiter binding is present. Both `diview-market-prices` and its DLQ had backlog 0 and 86,400-second retention at 06:11:35 UTC.
- [x] Public GitHub GETs at 06:13 UTC found `main` at `b3f3a7b75bc54d30ec468fc085922791f99e340b`, `protected=false`, zero repository rulesets, and only the `IRBANK_API_KEY` Environment. The migration and Worker CI workflow files both return 404 on `main`; the local migration workflow requires protected `main`, an independent reviewer with self-review disabled, and a main-only deployment policy.
- [ ] Do not dispatch or publish migration workflow until the repository protections and `d1-r2-migration` Environment exist and are read back. The live production Worker also lacks the candidate DO binding; this audit made no deployment or queue mutation.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T061131Z.sanitized.json`, SHA-256 `507A3F191A217B33731ED7CAA8D35796C92F55DE718B53A7F21ABC3C21DE53A8`.
## 2026-11-06 UTC 06:25 Account Analytics D1 usage preflight

- [x] The read-only GraphQL helper reported 276,367 account-wide D1 rows read and 1,405 written for 2026-11-06 UTC across three databases, leaving 4,723,633 of 5,000,000 rows. The 4,500,000-row preflight reserve passed. This data is account-wide and does not attribute reads to a specific request.
- The query used Account Analytics only; D1 SQL statements and R2 operations: 0. Recheck immediately before every D1-reading migration dispatch.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\d1-daily-usage-20261106T062512Z.sanitized.json`, SHA-256 `52834AB7538CCAE53DC4B704BDC49F0769B6E375A311D9F48391C81BF2DE6CBE`.
## 2026-11-06 UTC 06:27–06:28 current R2 generation and local API parity

- [x] Four read-only R2 `GetObject` calls confirmed catalog, income-advice, publication, and manifest share generation `2026-11-05-0523fcc6c66545b48331cf28ebdef4ba`; catalog and publication market-data date is 2026-11-05. The catalog has 4,391 unique codes and positive prices; four quotes are over three TSE sessions old as of the catalog date and must remain notification-ineligible under the Android policy.
- [x] Current R2 income-advice object contains 3,201 unique items, zero codes absent from the catalog, zero duplicate codes, zero generation mismatches, and 253 ETF items. The existing object remains unmodified.
- [x] The local candidate Worker `GET /api/v1/income-advice-candidates`, supplied with the current R2 object and a local DO limiter stub, returned HTTP 200 and a payload exactly matching that object; its D1 SQL access was blocked and counted as zero. This validates the candidate route against live R2 data, not the deployed production route.
- [ ] Do not call the production income-advice route during this audit: deployed v84 records its rate limit through D1. Production API parity remains unverified until a DO-backed Worker is deployed under the approved cutover or a separately approved request is made.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\r2-publication-readback-20261106T062825Z.sanitized.json` (SHA-256 `791D41B516EBCCE002C04E127777AD983B1E20F5E7D54D92B0B2048261B51E5B`); `C:\Codex\r2-d1-ownership-audit-20261106\income-advice-worker-route-parity-20261106T062745Z.sanitized.json` (SHA-256 `8F2F8D72ED07AA75A2AAA1097663FE89F7C299B6D4890373637CC766D00DF8A1`).
## 2026-11-06 UTC 06:30 scheduled-run log refresh

- [x] A bounded `d1-operational-audit` dry-run read `scheduled_run_log` with seven SELECTs and `meta.rows_read=661`; all 661 rows are `running`, have no completion time, and show processed=0/failed=0. Latest `started_at` is `2026-11-06T06:17:32.757Z`. It planned or wrote zero R2 objects.
- [x] Account Analytics preflight at 06:30:47 UTC reported 276,367 account-wide rows read and 4,723,633 remaining; the configured requirement was 4,501,000 rows remaining. A GraphQL postflight at 06:31:07 still returned the pre-query aggregate despite the D1 response reporting 661 rows_read. Keep this reconciliation lag explicit; do not use that immediate Analytics result as an exact post-query balance. No more D1 SQL until usage is freshly checked before the next query.
- [ ] These 661 rows are unfinished attempts, not completed history. Keep them out of completed-run R2 history and retain the source rows until the final quiescent snapshot and retention review.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\scheduled-run-log-audit-20261106T063046Z.report.json` (SHA-256 `969C737FAB72E777A36CB3431E95584C9963DABD2D8C020312F35564EBF57A95`); checkpoint `scheduled-run-log-audit-20261106T063046Z.checkpoint.json` (SHA-256 `3FBA02BFA2D0577B60EF6286F33D238E398EA3010CC5FFC4E2ECF8353BCAE12B`).

## 2026-11-06 UTC 06:54 refreshed Worker/Queue and Android emulator readback

- [x] `CLOUDFLARE_WORKERS_READ_TOKEN` and `CLOUDFLARE_QUEUE_READ_TOKEN` were visible in the Codex environment; values were not printed. Read-only Cloudflare GETs at 06:54:22 UTC reconfirmed deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%, D1/KV/R2/Queue bindings, and no Durable Object rate-limiter binding. Both market-prices Queue and DLQ reported point-in-time backlog 0, 0 bytes, and 86,400-second retention.
- [x] With explicit JDK 17.0.20.1, verified that the candidate debug APK and `emulator-5554` installed `base.apk` have the same SHA-256: `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`. The disposable API 35 emulator resumed `com.kurogane.dividend/.MainActivity`; screenshot shows the Asset Management landing screen. This is launch/screen evidence, not notification-flow acceptance.
- [x] Android instrumentation on the disposable emulator had already completed `OK (141 tests)` with no failures; one live-network assumption was skipped. No physical device was used.
- [ ] The account plan remains unverified because `CLOUDFLARE_BILLING_READ_TOKEN` is not configured. Production still lacks the candidate DO binding; GitHub main remains unprotected and the required migration Environment/workflows are unpublished. Do not deploy or dispatch until those gates are configured and verified.
- No D1 SQL, R2 write, Worker deployment, Queue mutation, GitHub workflow dispatch, commit, push, or physical-device install occurred in this readback.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T065403Z.sanitized.json` (SHA-256 `58FB96D2148FBFD94216D05B239D4AF82895B8AF17C8A310BCD272FCEEA53254`); emulator screenshot `C:\Codex\r2-d1-ownership-audit-20261106\diview-emulator-5554-20261106T0654Z.png` (SHA-256 `1B6AFB4B2ABBC9ABC5A5668F6C617012D6D4FE971695E1D269A9D0D63525068F`).

## 2026-11-06 UTC 07:11 Worker/Queue read-only readback

- [x] User-configured Workers Scripts Read and Queues Read credentials passed read-only Cloudflare GETs. At 07:11:10 UTC production still serves version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%; D1/KV/R2/Queue/Email bindings remain, with no rate-limit Durable Object binding. Both price Queue and DLQ had point-in-time backlog 0 and 86,400-second retention.
- [ ] The production DO cutover, account-plan verification, GitHub protection/Environment setup, and migration workflow publication remain outstanding. No D1 SQL, Cloudflare write, Queue mutation, deploy, or workflow dispatch was performed.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T071035Z.sanitized.json`, SHA-256 `4DCB7329FED795AA478D0D1684B4E9D8E2C3F3425DD7EE6259F0F5D4AE548A64`.

## 2026-11-06 07:42 UTC Worker/Queue readback and bounded Cron candidate

- [x] User-configured Workers Scripts Read and Queues Read tokens were visible to the Codex process; values were not printed. Read-only GETs reconfirmed deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%, D1/KV/R2/Queue/Email bindings, and no Durable Object rate-limiter binding. Queue and DLQ backlog were both 0 messages / 0 bytes at 07:42:29 UTC. This is point-in-time evidence; this refresh did not retrieve retention settings.
- [x] The local candidate now processes analysis in resumable eight-security batches with an R2 lease, exponential retry backoff, definition/configuration content fingerprints, and source-pointer advancement only after the complete catalog succeeds. A changed source supersedes the prior run instead of advancing its pointer.
- [x] Candidate retention now scans 200 R2 objects per invocation, resumes from stored cursors every five minutes, and deletes only completed terminal run summaries beyond the retention window. Hourly feedback retries and support-feedback cleanup remain on the separate hourly Cron. Removed the 250,000 subrequest uplift from the candidate Wrangler files.
- [x] Verification passed: `node --check src/index.js`; Worker suite 136/136; migration script suite 88/88; `git diff --check`. The migration Python suite was rerun from `data/irbank/cloudflare/`, matching CI's working directory.
- [ ] Production remains on version `4981c1e0-e337-4392-9f5b-909b52c5bc0c`; the new Cron schedule and bounded runtime are local candidate changes only. Production Cron CPU/subrequest distribution, account plan, Durable Object handoff, GitHub Environment/protection, and action publication remain unverified. Do not treat local tests as production runtime proof.
- No D1 SQL, R2 writes, Queue mutation, Worker deploy, Actions dispatch, commit, push, or physical-device install occurred.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T074229Z.sanitized.json`, SHA-256 `1427C65453404D3DC4FA94E21CF9B4476482C922E92535695565B1C225376F6B`.

## 2026-11-06 UTC 08:31 candidate Android build and emulator verification

- [x] With explicit JDK 17.0.20.1, `:app:testDebugUnitTest` passed (39 suites, 277 tests, zero failures/skips); `assembleDebug` and `assembleDebugAndroidTest` succeeded in this candidate worktree.
- [x] Installed the candidate app and instrumentation APKs only on disposable API 35 `emulator-5554`. Instrumentation reported `OK (141 tests)`, zero failures/errors, and one skipped live-network assumption (`MarketFetchEfficiencyLiveTest.defaultRepositoriesShareLiveReadsAndPreserveIndependentResults`). The new offline income-advice cache test was discovered and passed.
- [x] The built app APK and installed `base.apk` SHA-256 match: `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`. After the test runner was stopped and `MainActivity` launched explicitly, the Asset Management screen rendered. The first post-run screenshot showed the splash screen; startup latency was not measured as an acceptance criterion.
- [ ] Physical-device installation, production D1/R2 mutation, Worker deployment, migration workflow dispatch, and merge/publication remain pending. Do not treat emulator acceptance as production cutover proof.
- Evidence outside Git: emulator screenshot `C:\Codex\r2-d1-ownership-audit-20261106\emulator-main-ready.png`, SHA-256 `4CD0319B348E717DD9E4014F296D063B685D22C6F430595CA13C14F2FFD7D4AE`; test APK SHA-256 `F5B9100508B404A296C71C18651B0D269F28E6FD2298B7857CB3918FDDF3DC32`.

## 2026-11-06 UTC 08:38 account-wide D1 usage preflight

- [x] Read-only Cloudflare GraphQL Analytics reported 285,404 rows read and 1,966 rows written account-wide for 2026-11-06 UTC, leaving 4,714,596 of the configured 5,000,000 daily rows. The 4,500,000-row reserve check passed. This was one Analytics request and ran no D1 SQL; account-wide usage cannot be assigned to a specific Worker or workflow.
- [ ] Refresh the usage preflight immediately before any future bounded D1 query or migration dispatch. Account plan entitlement remains unverified; the 5,000,000-row cap is based on the D1 Free-plan alert.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\d1-usage-20261106T083814Z.json`, SHA-256 `2CFBF156461476477ABD14B811B45DC088338EC983F7EE63B2BEC86C84CBE80E`.

## 2026-11-06 UTC 08:43 bounded scheduled-run audit refresh

- [x] A read-only `d1-operational-audit` for `scheduled_run_log` completed within `max_rows=1000` and a 4,500,000-row reserve. It used seven SELECTs and `meta.rows_read=663`; all 663 rows remain `running`, with no completion time and processed=failed=0. `started_at` spans 2026-10-04 19:49:05.538Z through 2026-11-06 08:17:33.117Z. No R2 objects were planned, read, or written.
- [ ] Keep these incomplete rows out of completed-run history. The 08:44:57 UTC Account Analytics postflight still showed the preflight total (285,404 rows read), so it has not yet reflected this 663-row audit; do not infer or add the delta. Recheck after analytics propagation before reporting a post-query account total.
- Evidence outside Git: report `C:\Codex\r2-d1-ownership-audit-20261106\scheduled-run-log-current-20261106T084353Z.report.json` (SHA-256 `D6A9DB19983CC0EE036CD59991EB589C11C7E3BA5AC4F0FB77401C0060D0059E`), checkpoint `C:\Codex\r2-d1-ownership-audit-20261106\scheduled-run-log-current-20261106T084353Z.checkpoint.json` (SHA-256 `E499A44A53C0ED7B0DF2E56AB4E3F20393BE2F5463FFEA07105A05CE204EA071`), postflight usage report `C:\Codex\r2-d1-ownership-audit-20261106\d1-usage-postflight-20261106T084456Z.json` (SHA-256 `BFD8ABE6F183E976308D708FE4929CB02D1DF1678AF6A9A035D4F19E58D61662`).

## 2026-11-06 UTC 08:46 production Worker/Queue and GitHub branch readback

- [x] Read-only Cloudflare GETs with the configured Workers Scripts Read and Queues Read tokens confirmed the active deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95`, version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` at 100%. The binding names are ADMIN_SECRET, APP_DEFINITIONS KV, DB D1, FEEDBACK_EMAIL, MARKET_DATA R2, and MARKET_PRICE_QUEUE; no RATE_LIMITER Durable Object binding is present.
- [x] `diview-market-prices` has one producer and one consumer; its DLQ has zero producers and one consumer. Both have 86,400-second retention and point-in-time metrics of 0 backlog messages / 0 bytes. No messages were read or modified.
- [x] GitHub's read-only `GET /branches/main` reports `protected=false` at `b3f3a7b75bc54d30ec468fc085922791f99e340b`; repository rulesets are empty. The branch-protection detail endpoint returns 403 to the connected integration. The migration Environment was not verifiable through that integration in this readback; retain the earlier finding that it was absent until an authorized current check succeeds.
- [ ] Do not publish or dispatch the migration workflow until main protection, an independent required reviewer, a main-only `d1-r2-migration` Environment, and its dedicated secrets are configured and verified. Production Worker remains on the D1-backed version; no deploy, queue mutation, workflow dispatch, or commit/push occurred.
- Evidence outside Git: sanitized Worker/Queue readback `C:\Codex\r2-d1-ownership-audit-20261106\worker-queue-live-read-audit-20261106T084629Z.json`, SHA-256 `5C8C11D208F5C03A75F5388D4734CB09F4DB757A6163689E8EDC6624DBD6CB87`.

## 2026-11-06 UTC 08:50 D1 usage propagation recheck

- [x] Account Analytics at 08:50:25 UTC reported 286,082 rows read and 2,041 rows written for the UTC day, leaving 4,713,918 rows. The 4,500,000-row reserve still passes. Since the 08:43:54 preflight, the account total increased by 678 reads and 75 writes; the bounded audit itself reported 663 `meta.rows_read`. Account-wide activity and analytics aggregation prevent assigning the exact 678-row interval change to that audit.
- [ ] Continue using a fresh account-wide preflight before each D1 query. The current aggregate is a baseline for this readback; do not claim a per-query postflight delta from account-wide totals.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\d1-usage-recheck-20261106T085024Z.json`, SHA-256 `7D5BCD4BE5C8F05303D65F00122EA9836AB088F252333972A1B5116CFD9F8763`.

## 2026-11-06 UTC 08:59 production Worker module and Queue readback

- [x] The configured Workers Scripts Read and Queues Read tokens passed Cloudflare GET requests; values were never printed or saved. Deployment `7731b2d6-75e8-4b13-8985-9f47a4960f95` serves version `4981c1e0-e337-4392-9f5b-909b52c5bc0c` (v84) at 100%. Its fetched `index.js` module is 120,935 bytes, SHA-256 `262388D871931F4321BB3946937CD9FAA2E72313297EF51EF60F4629BEA4AD9A`; static checks find D1 prepare calls, a securities search query, analysis D1 table names, and `api_request_log`. The live version has D1/KV/R2/Queue/Email bindings and no Durable Object rate-limiter binding.
- [x] At 08:59:10 UTC, both `diview-market-prices` and its DLQ reported backlog_count=0, backlog_bytes=0, oldest_message_timestamp_ms=0. These are approximate point-in-time metrics and do not establish processing history.
- [ ] The production Worker is still D1-backed; do not call D1-logged production API routes or dispatch/deploy the candidate until the DO limiter, protected `main`, and reviewed `d1-r2-migration` Environment are in place. Static bundle string checks do not quantify live query frequency. No D1 SQL, R2 operation, Queue mutation, deployment, workflow dispatch, commit, or push occurred.
- Evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-module-queue-read-20261106T085911Z.sanitized.json`, SHA-256 `AE7DC3D32C21DA178166B2C28EFEC3BA5B95801A4734CCE218A97BB6CE2600DB`.

## 2026-11-06 UTC 09:10 CI-equivalent local suite verification

- [x] Exact Worker CI test command passed: 136/136. The three workflow Python commands passed: migration workflow guard 10/10, migration tool 66/66, account-wide D1 usage helper 12/12 (88 Python tests total).
- [x] Worker coverage includes SQL/migration compilation, indexed securities search, R2-backed routes and analysis, and Durable Object counter import, exact-window reset, deadline, and failure behavior.
- [ ] GitHub Actions has not run because these workflows are not published on main and the branch/environment protections are not ready. Local tests are not production migration or deploy evidence.
- The first manual unittest-discovery command used the nested scripts directory and failed package import; the CI commands from data/irbank/cloudflare then passed.

## 2026-11-06 UTC 09:20 current R2 income-advice and candidate API parity

- [x] R2 current catalog and income-advice objects share generation 2026-11-05-0523fcc6c66545b48331cf28ebdef4ba. The catalog contains 4,391 unique codes; income-advice contains 3,201 unique codes, no duplicates, and no codes absent from the catalog.
- [x] The candidate local income-advice route returned HTTP 200 with a body exactly matching the current R2 object and 0 D1 prepare calls. The rate-limit stub allowed one request at the candidate default 60-second/300-request settings, matching current KV backend-config:v1 values.
- [ ] R2 definitions/v1 is empty although backend-config:v1 exists in KV. Complete the definition migration and read back required R2 documents before deploying the candidate; recompute the current income-advice generator and compare full output before claiming generator parity.
- [ ] Production Worker was not invoked because its public rate limiter still writes to D1. No D1 SQL, R2 write, Queue operation, Worker deploy, or workflow dispatch occurred.
- Evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\income-advice-current-r2-candidate-route-20261106T092042Z.sanitized.json, SHA-256 89e2eeccf28b8afa2734c9ac25f00670a410ae05340af8eb7e32f067fbfba154.

## 2026-11-06 UTC 11:22–11:30 Billing and Worker log access

- [x] After `CLOUDFLARE_BILLING_READ_TOKEN` was configured, a read-only `GET /accounts/{account_id}/subscriptions` succeeded at 11:22:38 UTC. It returned two account-scoped subscriptions, `teams_free` / `Teams Free Base` and `r2_paid` / `R2 Paid`; state was omitted. This list does not establish the Worker’s effective plan. `default_usage_model=standard`, version `usage_model=standard`, and `limits=null` also do not state the effective CPU ceiling.
- [x] The read-only `GET /accounts/{account_id}/billable/usage?from=2026-11-01&to=2026-11-06` returned HTTP 403 with this Billing Read token. No billable usage rows were read or saved; the response body, cost, account identifiers, and token were omitted.
- [x] Worker observability settings had logs, invocation logs, and persistence enabled. Account Analytics token could introspect `logs.workersLogs` (schema reports adaptive sampling), but the read-only SQL query returned HTTP 403 `Authorization error`. The environment does not contain `CLOUDFLARE_WORKER_OBSERVABILITY_READ_TOKEN`.
- [ ] Keep the effective Workers plan/CPU ceiling and exact Cron failure subtype open. The latest 24-hour Analytics aggregation reports 24 `exceededResources` invocations, each with CPU P50/P99 2,050 ms and zero subrequests. This aggregate does not identify the resource subtype. Cloudflare documents 10 ms Cron CPU on Workers Free and up to 15 minutes for an hourly Cron on Workers Paid; obtain per-invocation logs before assigning the failure to CPU, memory, or another limit. The candidate Cron uses resumable batches of eight; production runtime is unverified.
- No D1 query, Cloudflare write, R2 write, Queue mutation, Worker invocation/deploy, Actions dispatch, device installation, commit, or push occurred for these entitlement/log checks.
- Evidence outside Git: subscription report `C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-billing-read-20261106T112238Z.sanitized.json` (SHA-256 `96EF395DE6FCEEFAE0F4FD96E11B45D6170DBE476349EFAD9EB10A3717999C06`); billable-usage 403 report `C:\Codex\r2-d1-ownership-audit-20261106\worker-billable-usage-read-20261106T112707Z.sanitized.json` (SHA-256 `9DD056FBDAF4D69864CA87F96E28BC88D7DF7E1F4CE36572C423E0B10AC22853`); log-access report `C:\Codex\r2-d1-ownership-audit-20261106\worker-log-query-access-read-20261106T113103Z.sanitized.json` (SHA-256 `533A27DD63ADDEA98BC75A83D526854789018B70AC36F49A352A0CD733E8F2B1`).

## 2026-11-06 UTC 11:43 Workers Observability Issues read-only follow-up

- [x] The configured Workers Observability Read token successfully called the read-only Issues list endpoint. Both the service=diview-irbank query and the account-wide query returned HTTP 200, success=true, and total_count=0. No issue payloads, IDs, account identifiers, or token values were stored.
- [ ] Keep the recurring Cron failure subtype and effective Workers CPU entitlement unresolved. Issues is a detected-error aggregation, not a raw invocation-log query; zero detected issues does not negate the existing 24-hour Analytics result of 24 exceededResources Cron invocations. No occurrences request was possible because the list returned no issue IDs.
- Cloudflare documents the read-only Issues and Occurrences endpoints here: [List detected issues](https://developers.cloudflare.com/api/resources/workers/subresources/observability/subresources/issues/methods/list/) and [List issue occurrences](https://developers.cloudflare.com/api/resources/workers/subresources/observability/subresources/issues/methods/occurrences/). The latter can expose invocation type, Cron expression, and timestamp for a retained issue occurrence; this audit received none.
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-observability-issues-20261106T114332Z.sanitized.json (SHA-256 CD63C0EE0C51A8C2CC7DFB3588F69A8B504EA4C8533104F6C0489866EAF805EE).

## 2026-11-06 UTC 11:48 Worker Cron Observability event review

- [x] Read the Cloudflare Dashboard Events view for the production diview-irbank Worker, using its last-24-hours preset. It displayed 423 success events and 23 error events. These are Observability event counts, not invocation counts.
- [x] Expanded one error event at 2026-11-06 11:17:58.488 UTC (20:17:58.488 JST). Metadata identifies eventType=scheduled, Cron 17 * * * *, outcome=exceededCpu, cpuTimeMs=2050, wallTimeMs=25633, Worker v84. This confirms CPU-limit exhaustion for that invocation. The event message itself is the Cron expression, not a stack trace.
- [ ] Keep the effective CPU threshold and cause of the high CPU work unresolved. The later 12:54 UTC Dashboard grouping found outcome=exceededCpu for all 23 last-24-hours Events error rows. The separate 24-hour Analytics aggregate reported 24 exceededResources invocation groups; its window and aggregation/sampling semantics differ, so the counts remain unreconciled.
- Cloudflare describes the Events Query Builder at [Workers Observability Query Builder](https://developers.cloudflare.com/workers/observability/query-builder/). The REST telemetry query endpoint is POST and its documented accepted permission is Workers Observability Write; it was not called with the Read token. Dashboard inspection did not save a query or change Worker settings.
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-observability-20261106T114845Z.sanitized.json (SHA-256 64C0042144B99283803F0118B7462DAB53F0B6F5263EC3CF6EC755DA23ED1DBA). No identifiers, raw event payload, request/trace IDs, account ID, or credentials were saved.

## 2026-11-06 UTC 11:53 Worker Cron CPU error metrics cross-check

- [x] The production Worker Metrics page, last-24-hours view, showed 453 invocations and 24 errors (5.3%) for version 4981c1e0 at 100% traffic. Invocation Statuses classified all 24 as CPU time limit errors; memory, internal, load-shedding, and uncaught-exception counts were each zero.
- [x] This corroborates the separate 24-hour Analytics result of 24 exceededResources invocation groups and the inspected 2026-11-06 11:17:58.488 UTC scheduled event with outcome=exceededCpu, CPU 2,050 ms, wall time 25,633 ms. At least one event is confirmed in detail; the Metrics status breakdown classifies all 24 Worker errors as CPU-limit errors.
- [ ] Keep the effective CPU threshold and cause of the CPU-heavy work unresolved. The production Settings page did not expose a numeric CPU limit, and the earlier Worker API readback reported limits=null. The observed 2,050 ms cutoff cannot be reconciled to the effective plan or a custom per-Worker setting from current evidence. The Events view had 23 error events while Metrics had 24 invocation errors; that one-count discrepancy remains unexplained.
- The direct REST Telemetry Query endpoint was not called because Cloudflare documents it as requiring Workers Observability Write. Dashboard inspection was read-only and did not change Worker settings or triggers. Cloudflare CPU-limit documentation: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) and [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/).
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-cpu-status-20261106T115316Z.sanitized.json (SHA-256 05970CA7556F18315D28AE04908CAEDA34F2CAEB4B3401D6624BD84B817DFBB9). No account, request, trace, or event IDs, raw payloads, or credentials were saved.

## 2026-11-06 UTC 11:58 Workers plan and settings cross-check

- [x] The Cloudflare Workers Plans page labels Free as the current plan. Read-only GET of the active script settings returned usage_model=standard, no limits field, and no explicit cpu_ms. The script-settings GET returned Logs enabled, invocation logs enabled, persistence enabled, and Traces disabled. Issues configuration was omitted by the API and the production Settings page showed the Issues switch off.
- [ ] Reconcile the Free plan label with usage_model=standard and the observed 2,050 ms exceededCpu event. The numeric effective CPU limit is not visible in Settings and no explicit CPU limit is returned by the current script settings API. Do not infer the active threshold from either signal alone. Cloudflare's current published limits list 10 ms per Cron invocation on Free and up to 15 minutes for an hourly Cron on Paid; the observed event does not fit those defaults.
- The earlier Issues list returned total_count=0 while Issues detection is off. Treat that as unavailable issue monitoring, not as evidence of no failures; the Metrics page's 24 CPU-limit errors remains the failure evidence. No settings, plan, or trigger were changed.
- Cloudflare references: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [Get Worker Script and Version Settings](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/script_and_version_settings/methods/get/), and [Get Worker Script Settings](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/settings/methods/get/).
- Sanitized evidence outside Git: C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-settings-crosscheck-20261106T115857Z.sanitized.json (SHA-256 8FC0C46938A444465BD6976DFAA9B473A5AA11B1419004B2E39AC8AD5A7F6871); no account IDs, binding IDs, tokens, or secret values were saved.

## 2026-11-06 UTC 12:08 Workers subscription and Cron quota follow-up

- [x] Read the configured billing token through the read-only subscriptions endpoint. The account subscription list returned `Teams Free Base` and `R2 Paid`; the Workers Plans page labels Workers as Free. No Workers Paid subscription appeared in the response. R2 Paid does not establish paid Workers CPU or subrequest entitlements. Sanitized report: `C:\Codex\r2-d1-ownership-audit-20261106\worker-plan-billing-read-20261106T112238Z.sanitized.json` (SHA-256 `96EF395DE6FCEEFAE0F4FD96E11B45D6170DBE476349EFAD9EB10A3717999C06`).
- [x] Current Cloudflare Workers limits documentation lists Free Cron CPU at 10 ms and up to 1,000 internal-service subrequests per invocation; Paid Cron permits 30 seconds for intervals shorter than one hour and 15 minutes for intervals of one hour or longer. The account's `usage_model=standard` response does not resolve the contradictory plan/runtime evidence. Sources: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) and [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/).
- [x] The main-based candidate uses eight-security analysis batches on a one-minute Cron and its active Wrangler config has no `[limits]` subrequest override. The earlier 4,391-security synthetic probe's 173,460 R2 requests and 250,000 configured cap belong to the previous single-pass probe; they do not establish the current per-invocation maximum. No production settings or schedule were changed.
- [x] Measured the current candidate on an in-memory synthetic catalog: 549 analysis invocations over 4,391 securities peaked at 307 R2 calls/invocation; 214 retention invocations peaked at 184; none exceeded 1,000 and D1 SQL calls were zero. Report: `C:\Codex\r2-d1-ownership-audit-20261106\analysis-batch-scale-probe-20261106T1218Z.sanitized.json` (SHA-256 `7E79E617A3D8CFA4195EFBE8532E9CED5FF906AF0AEB8C816E544E47BCBB9DCF`).
- [ ] Validate request-count skew against the actual current R2 object distribution and verify CPU under a production-compatible runtime. The synthetic fixture distributes object counts and cannot establish actual per-batch peak or Cloudflare CPU.
- [ ] Keep Cron restoration blocked until the effective CPU entitlement and the 24 production CPU-limit errors are reconciled. One event used 2,050 ms CPU, which does not match the documented Free Cron limit; current script settings expose no explicit `cpu_ms`. Do not infer that the candidate batches fit from local wall-clock or total-request measurements.

## 2026-11-06 UTC 12:15 GitHub publication-gate readback

- [x] Read-only GitHub branch metadata reports `main` at `b3f3a7b75bc54d30ec468fc085922791f99e340b` with `protected=false`; the repository rulesets endpoint returned an empty list. This confirms the required branch protection is absent.
- [x] The `main` workflow directory lists nine workflows, but neither `cloudflare-worker-ci.yml` nor `d1-r2-migration.yml` is published there. The candidate files remain local worktree changes; no workflow was dispatched.
- [ ] The connected GitHub read API rejected the Environments endpoint as unsupported, so `d1-r2-migration` reviewers, self-review prevention, main-only deployment policy, and secrets remain unverified. The local workflow's preflight requires these protections and a protected `main`; keep publication/dispatch gated until an authorized settings readback confirms them.
- No GitHub settings or workflow state was changed. Read-only evidence: branch metadata and workflow listing from `https://api.github.com/repos/masacgt/Diview/branches/main` and `/contents/.github/workflows?ref=main`; rulesets response was `[]`.

## 2026-11-06 UTC 12:17 GitHub Environment UI readback

- [x] Read the repository Settings → Environments list. It contains only `IRBANK_API_KEY`; `d1-r2-migration` does not exist. This supersedes the earlier connected-API limitation note and confirms the migration Environment's reviewers, branch policy, and secrets are not configured.
- [ ] Create and configure the dedicated `d1-r2-migration` Environment with an independent required reviewer, prevent self-review, restrict deployment to `main`, and add only the dedicated migration credentials. Then read back its protection settings before publishing or dispatching the workflow.
- No GitHub setting was changed, no secret values were opened, and no workflow was dispatched.

## 2026-11-06 UTC 12:28 Android JDK 17 unit-test run

- [x] Forced `:app:testDebugUnitTest` with JDK `17.0.20.1` in the main-based candidate worktree. Gradle completed successfully; 39 XML reports contain 277 tests, zero failures, zero errors, and zero skipped. Relevant results: `NotificationPriceCacheTest` 2/2, `NotificationPriceTargetsTest` 1/1, `NotificationPolicyTest` 19/19, `MarketCatalogRefreshGateTest` 1/1, `MarketFetchPolicyTest` 7/7. The first non-forced run was `UP-TO-DATE`; the recorded result is from the forced rerun.
- [x] The 12:28 UTC snapshot had no connected emulator. That point-in-time gap was later closed on the task emulator; focused instrumentation and APK/hash evidence are recorded under 12:43–12:54 UTC and 13:28 UTC.

## 2026-11-06 UTC 12:43–12:54 Cloudflare Cron and Android instrumentation follow-up

- [x] `CLOUDFLARE_WORKER_OBSERVABILITY_READ_TOKEN` was available for read-only access. The GET Observability Issues endpoint returned zero issue records for `diview-irbank`; Issues is separate from the Events dataset. The REST telemetry query endpoint was not called because Cloudflare documents it as requiring Workers Observability Write. Dashboard Events and Visualizations were inspected read-only; no settings changed.
- [x] The last-24-hours Events view showed 423 success events and 23 error events. Filtering for `$metadata.error` listed 23 scheduled events, each showing Cron `17 * * * *`, from 2026-11-05 22:18 JST through 2026-11-06 21:18 JST.
- [x] Two expanded events, at 20:17:58.488 and 21:18:05.379 JST, both reported `outcome=exceededCpu` and `cpuTimeMs=2050`; their wall times were 25,633 ms and 32,287 ms.
- [x] Dashboard Visualizations grouped the 23 last-24-hours Events error rows by `$workers.outcome`: all 23 had `exceededCpu`. The two expanded examples reported 2,050 ms CPU, with wall times of 25,633 ms and 32,287 ms.
- [ ] An earlier Metrics snapshot at 11:53 UTC showed 453 invocations and 24 CPU-limit errors. Its window differs from the later Events view and the counts remain unreconciled. The effective CPU entitlement and CPU-heavy work remain unresolved. Keep production Cron restoration gated.
- [x] With JDK 17.0.20.1, the task-specific Android 35 emulator passed `AssetDataRefreshTest#unknownQuoteDoesNotBecomeNoDividendOnAssetAndDetailScreens` (1/1) and `ReleaseNotificationPlatformTest` (2/2). The focused case that previously reported `No compose hierarchies found` now passes.
- [x] Reinstalled the candidate debug APK on the task emulator from this worktree after instrumentation. Built and installed `base.apk` SHA-256 both equal `78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`.
- [ ] No physical-device install or manual app launch/UI review was performed. The changes tested here concern background price caching and notification policy; no unique visual UI change was requested. Avoiding launch also avoids an unneeded production API refresh.
- No D1 SQL, R2 write, Queue mutation, Worker deploy, production trigger/settings change, GitHub Actions dispatch, commit, push, or physical-device install occurred.
- Sanitized evidence outside Git: event-level readback `C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-observability-20261106T1243Z.sanitized.json` (SHA-256 `AAA7107D6BBADFEB1DACE79121DC394B4A3C07CB1974116EC55B2A703355A18C`); outcome aggregate `C:\Codex\r2-d1-ownership-audit-20261106\worker-cron-outcome-summary-20261106T1254Z.sanitized.json` (SHA-256 `722005895C5F8C4945BF5D8BB7BDDCED82033674739DE7F52A0C4486597522E2`). Neither retains event/request/trace IDs, raw logs, account IDs, or credentials.

## 2026-11-06 UTC 13:14 Cloudflare active Worker version CPU settings

- [x] Read the latest production deployment and its active version with Workers Scripts Read. Version 84 was serving 100% of traffic; the deployment and version were created at 2026-11-03 12:26:30 UTC. Version `usage_model=standard`; the version response exposed no explicit `limits.cpu_ms`, and the script settings GET returned no `limits` field.
- [ ] Effective CPU entitlement remains unresolved. `usage_model=standard` and the absent custom override do not explain why the Workers plan page says Free while recent Cron events report 2,050 ms CPU and `exceededCpu`; keep Cron restoration gated and preserve the separate 23 Events versus 24 Metrics count discrepancy until reconciled.
- Read-only API references: [List Worker Deployments](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/deployments/methods/list/), [Get Worker Script Version](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/versions/methods/get/), and [Get Worker Script and Version Settings](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/script_and_version_settings/methods/get/).
- No production configuration, Cron trigger, Worker, D1, R2, Queue, or GitHub state was changed. No commit or push occurred.
- Sanitized evidence outside Git: `C:\Codex\r2-d1-ownership-audit-20261106\worker-active-version-cpu-settings-20261106T1314Z.sanitized.json` (SHA-256 `a2ace55906ac9068e1941fd7dd9b6dd87f830e91f543517b8928f89fe76a003f`); no account identifier, version ID, token, or binding values were saved.

## 2026-11-06 UTC 13:28 offline income-advice cache instrumentation

- [x] `IncomeAdviceCatalogCacheTest#cachedIncomeAdviceRemainsVisibleInWatchlistWhenOffline` passed 1/1 on the task-specific Android 35 emulator, with JDK 17.0.20.1. It rendered the cached income-advice candidate, its code/name/months, and asserted that no network request was made.
- [x] Installed the candidate debug APK from this worktree on `emulator-5554`; built and installed `base.apk` SHA-256 both equal `78fe1d70dd3b508fb1e62ae4150795efe3ffb9fb871d2a501be162ed08261f08`.
- [ ] Physical-device installation and manual device review remain pending. No production API call, D1 query, R2 write, migration workflow dispatch, Worker deployment, commit, or push occurred.

## 2026-11-06 UTC 13:33 D1 usage and Worker/workflow source audit

- [x] Account-wide Account Analytics preflight reported 311,791 rows read and 4,688,209 remaining for the UTC day, above the 4,500,000-row reserve. This was a GraphQL analytics read; it issued no D1 SQL. Sanitized report: `C:\Codex\r2-d1-ownership-audit-20261106\d1-daily-usage-preflight-20261106T133326Z.sanitized.json` (SHA-256 `5c9c2feac9b0286b2a14fc628636d615c3aefb7cc49a72237be4385dd1e80624`).
- [x] Re-scanned the candidate Worker D1 call sites. The D1 binding is used for the fenced one-time `api_request_log` rate-limit bootstrap and support-feedback persistence, administration, email retry, and 90-day cleanup. Market search, analysis, definition reads, and market-data Cron paths contain no D1 SQL in this source snapshot. This is static source evidence; production remains on version 84.
- [x] Reviewed the local CI and migration workflows: Worker CI runs the Worker/D1/R2/DO tests and migration/unit suites; the migration workflow is `workflow_dispatch` only, validates protected `main` and the dedicated Environment, and does not auto-deploy. Neither workflow is published on `main`.
- [ ] GitHub `main` protection and `d1-r2-migration` Environment are still absent; publishing/dispatch remains gated. Production D1/R2 writes and Worker deployment still require final review and approval.
- No D1 SQL, R2 write, Queue mutation, GitHub setting change, workflow dispatch, Worker deployment, commit, or push occurred.

## 2026-11-06 UTC 13:37 legacy D1 write-script guard review

- [x] Confirmed `data/irbank/cloudflare/import-d1.ps1` and `apply-financial-repair.ps1` stop immediately with an explicit disabled message before any generated SQL can run. Their acknowledgment parameters do not bypass the unconditional `throw`; old imports and repairs are retired in favor of the reviewed R2 pipeline.
- [x] This was a read-only source review. Neither script was executed, and no D1 SQL, R2 write, workflow dispatch, or deployment occurred.

## 2026-11-06 UTC 13:57 GitHub migration-gate setup

- [x] Applied and read back `main` branch protection: PR required, enforced for administrators, conversations must be resolved, force pushes and deletion disabled. Required approvals are 0 and status checks are not required because `masacgt` is the only repository collaborator and the candidate Worker CI is not published.
- [x] Created the `d1-r2-migration` Environment and read back a single deployment branch policy for `main`.
- [ ] Add an independent repository collaborator as the Environment reviewer, then enable `prevent_self_review`. The Environment currently has no required reviewer, so the migration workflow preflight must fail closed.
- [ ] Add the five Environment secrets only after the values are intentionally transferred to GitHub: `D1_R2_MIGRATION_ACCOUNT_ID`, `D1_R2_MIGRATION_READ_TOKEN`, `D1_R2_MIGRATION_ANALYTICS_READ_TOKEN`, `D1_R2_MIGRATION_R2_ACCESS_KEY_ID`, and `D1_R2_MIGRATION_R2_SECRET_ACCESS_KEY`. No credential values were transmitted.
- User authorized Codex to invite an independent reviewer and to transfer the five stored values to GitHub after the reviewer is configured. The invitee's exact login is still unknown; no invite or secret upload has occurred.
- [ ] Publish the local `cloudflare-worker-ci.yml` and `d1-r2-migration.yml` before requiring their CI checks. Neither workflow is currently published on `main`.
- [x] Traced the Android first-price-fetch path. A fresh daily startup reaches `refreshMarketPricesIfDue`; when startup work was already recorded, newly added holdings call the same refresh path. `PriceRefreshPolicyTest.firstRunFetches` and the `MarketFetchPolicyTest` cases match this behavior; no code change was needed.
- [x] The configured Observability Read token successfully read the production Worker Issues endpoint, which returned zero issue records for `diview-irbank`. This does not negate the previously captured Cron CPU errors; the Events telemetry query API requires Workers Observability Write, as recorded above.
- No workflow was dispatched, no migration secret was created, and no Cloudflare, D1, R2, Queue, Worker, commit, or push change was made.

## 2026-11-06 UTC 18:50 Android instrumentation and GitHub reviewer gate follow-up

- [x] With JDK 17.0.20.1, the Android 35 task emulator passed the focused `AssetDataRefreshTest#visibleAssetScreenAutomaticallyRetriesMissingJapanesePrice` case and the full `:app:connectedDebugAndroidTest` run. Gradle reported 142 tests finished; the XML report contained 141 testcase nodes, one skipped live-network test, zero failures, and zero errors. The focused test now sets the failed-fetch cooldown timestamp to an already-expired value before rendering; cooldown behavior remains covered separately.
- [x] The `d1-r2-migration` Environment admin-bypass option was already saved off in the prior UI readback; the user reconfirmed that this is desired. No additional setting change was needed.
- [ ] No independent reviewer is available/configured. The repository has only `masacgt` as a collaborator, and no invitee login was provided. GitHub connection tools here do not expose collaborator invitations. Keep migration Environment secrets unpublished and the workflow undispatched until an independent reviewer can be added, or the user chooses a different review gate.
- No production D1/R2 writes, Worker deployment, GitHub workflow dispatch, physical-device install, commit, or push occurred.

## 2026-11-06 UTC 19:41 Cron lease and Environment follow-up

- [x] Increased both R2 Cron leases to 16 minutes, one minute above Cloudflare's documented 15-minute Cron wall-time ceiling; added configuration assertions. The scheduled-analysis suite passed 22/22, the full Worker suite passed 123/123, and `python -m unittest discover -s scripts -p "test_*.py"` passed 91/91.
- [x] Read the `d1-r2-migration` Environment in GitHub Settings after the user's confirmation: administrator bypass was already off and `main` is the only deployment branch. No setting change was needed.
- [ ] The Environment still has no required reviewer or secrets. An independent reviewer has not been identified; defer secret transfer, workflow dispatch, and production migration until that gate is satisfied.
- [ ] `wrangler.cutover-paused.toml` disables Cron but retains two Queue consumers; the Worker acknowledges every received message. Refresh backlog metrics and identify external producers before using it when Queue preservation matters.
- No production D1/R2 write, Queue mutation, Worker deployment, workflow dispatch, secret transfer, commit, or push occurred.

## 2026-11-06 UTC 20:00 Android verification

- [x] With Microsoft OpenJDK 17.0.20.1, `:app:assembleDebug` succeeded and `:app:testDebugUnitTest --rerun-tasks` passed 277/277 with no failures, errors, or skips.
- [x] Android 35 audit emulator instrumentation passed `AssetDataRefreshTest` 24/24, including `visibleAssetScreenAutomaticallyRetriesMissingJapanesePrice`. Installed the explicit Debug APK from this worktree using `adb install -r`; its SHA-256 matched emulator `base.apk` (`78FE1D70DD3B508FB1E62AE4150795EFE3FFB9FB871D2A501BE162ED08261F08`) and `MainActivity` resumed. No uninstall or physical-device install occurred.
