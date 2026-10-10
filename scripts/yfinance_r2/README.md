# Yahoo Finance data collection to Cloudflare R2

The scheduled GitHub Actions run updates only app-used Yahoo dividend and current valuation fields for the saved IRBANK Connect listed-security universe (`data/irbank/organized/d1/securities.csv`). It does not retrieve price history or financial statements: the Cloudflare Worker refreshes market prices, and the app's financial-data API serves IRBANK annual data. A separate, manually dispatched `full` mode remains available for initial population or a full historical refresh. This workflow does not call J-Quants API.

## Collected data

- Weekly: current PER/PBR, annual dividend per share, and dividend yield from the yfinance company-statistics response. Only these fields are refreshed in the saved market snapshots.
- Weekly: dividend and split actions observed over the latest five trading days, merged into the existing event history. This keeps recent dividend events current without fetching OHLCV history again.
- Full manual mode: daily, weekly, and monthly OHLCV history, broad company statistics, historical valuation measures, available income statements/balance sheets/cash-flow statements, earnings history, and share-count observations.

Yahoo's company-statistics response is broad; the weekly collector stores and republishes only the fields used for current PER/PBR and dividend data. Its action request is limited to five recent trading days, and new dividend/split events are merged with the existing event history. Full historical prices and financial history remain available through manual `full` mode; the normal weekly run does not refresh them.

The weekly run processes the complete committed listed-security universe in eight shards, with at most two shards running concurrently and a two-second pause between symbols. It writes per-security JSON and compact dividend objects only when their contents change. The aggregate preserves current Cloudflare prices and search-catalog fields while replacing only the refreshed dividend and valuation values. The monthly rights-date workflow reads the compact dividend history already in R2 and does not call yfinance.

Each symbol is stored at `market/yfinance/securities/{symbol}.json`. Dividend events are also written as compact objects at `market/yfinance/dividends/{symbol}.json` so the cross-provider publisher need not download full price and financial histories. The summary at `market/yfinance/latest_market_data.json` contains current valuation/dividend statistics and the previously published price. After all weekly shards complete, the workflow updates the summary and search catalog, publishes source-attributed per-security dividend records at `market/securities/v1/{code}.json`, and triggers backend analysis. The bucket is private; this workflow does not publish a public URL.

Manual `mode=weekly-minimal` runs the same narrow refresh as the schedule. Manual `mode=full` performs a complete yfinance collection after checking the Tokyo Stock Exchange calendar. To publish the combined dividend view from data already in R2 without fetching a market refresh, use `mode=publish-dividends`; it migrates IRBANK dividend rows, fills eligible IRBANK gaps from stored Yahoo dividend actions, and writes the per-security bundles.

## GitHub configuration

Repository secrets required: `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`. The R2 keys must be S3-compatible credentials with Object Read & Write access scoped to the `diview-market-data` bucket. The account ID is configured in the workflow. The listed-security universe is the version of the IRBANK master committed in the repository; refresh that snapshot before running when new listings or delistings need to be reflected.

The full-mode universe job fails if the IRBANK master is missing, malformed, or contains fewer than 1,000 unique issue codes. New issues enter after the committed IRBANK master is refreshed; previously stored per-symbol R2 history is retained. A Yahoo 404 by itself does not remove an issue from the IRBANK universe, so listed issues can appear in the catalog without Yahoo price coverage. The weekly mode requires existing `latest_market_data.json` and `search_catalog.json` baselines; run `mode=full` once if either is absent.

The scheduled weekly refresh runs at 03:30 JST each Monday and is independent of the JPX trading calendar because it updates dividends and current company statistics. Manual `full` mode checks the Tokyo Stock Exchange session using `exchange_calendars` (`XTKS`); weekends and JPX holidays skip that full collection.

The yfinance `Ticker.calendar` and `Calendars` interfaces describe company events (earnings, dividends, IPOs, splits, and economic events), not Japanese exchange sessions. yfinance's market-status endpoint currently reports U.S. status only, so it cannot reliably identify JPX holidays; the workflow uses the dedicated Tokyo exchange calendar instead.

The weekly collector uses bounded yfinance retries and explicit timeouts for recent action history. Persistent rate limiting stops the affected shard. Publication refuses to update the aggregate if any shard is incomplete, duplicated, or has failed symbols. Weekly dependencies are limited to `yfinance` and `boto3`; the full and monthly-calculation workflows use `requirements.txt` as needed.

The data source is Yahoo Finance accessed through yfinance, under the access permission the user stated they have. Verify that the permission scope covers full-universe automated retrieval, persistent R2 storage, and any later app use or delivery.

## Calculated Japanese last rights dates

`calculate_rights_dates.py` derives a historical last rights date for every Yahoo dividend event by selecting the last XTKS trading session strictly before Yahoo's observed ex-dividend date. The calculated history is published as `market/yfinance/rights_dates.json`; the Cloudflare dividend-month API joins it to the existing event response, and the app labels the displayed value as the latest calculated historical last rights date. It is not a forecast or an issuer-announced future date. The GitHub Actions workflow `rights-dates-monthly.yml` recomputes and publishes the bundle on the first calendar day of each month from the current compact Yahoo dividend histories in R2. The XTKS calendar begins in 1997; earlier events are skipped and counted in `skipped_events` rather than assigned guessed dates.

To prepare a dated local snapshot from the acquired CSV:

```sh
python scripts/yfinance_r2/calculate_rights_dates.py \
  --input-csv data/market/yfinance/ex_dividend_history_20261009.csv \
  --output-csv data/market/yfinance/rights_dates_20261010.csv \
  --output-json data/market/yfinance/rights_dates_20261010.json
```

The monthly workflow requires the existing bucket-scoped `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` repository secrets. It refuses to publish when the source history produces no valid events and verifies the uploaded object size.
