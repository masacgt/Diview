# Yahoo Finance data collection to Cloudflare R2

This scheduled GitHub Actions workflow uses the saved IRBANK Connect listed-security master (`data/irbank/organized/d1/securities.csv`) as its universe. It converts codes to Yahoo `.T` symbols to collect price and financial data. All 24 fetch shards and the aggregate job use the same exported universe artifact. The IRBANK security master supplies display names; Yahoo sector and industry labels are not stored or used. This workflow does not call J-Quants API.

## Collected data

- Daily, weekly, and monthly OHLCV price history for the maximum period Yahoo makes available (`period="max"` on the first run). Adjusted close and Yahoo's dividend, split, and capital-gain actions are retained when returned.
- Dividend and split events derived from that full daily history.
- Current company profile and quote statistics, including current/trailing/forward PER, PBR, EPS, market capitalization, dividend rate/yield, and payout ratio when Yahoo supplies them.
- Monthly, quarterly, and yearly historical valuation measures from yfinance's `get_valuation_measures(periods=None)` when available. These rows are the source for historical PER/PBR; the workflow does not estimate old PER from current values.
- Available annual, quarterly, and trailing income statements, balance sheets, and cash-flow statements; available earnings history; and historical share-count observations.

Yahoo does not provide unlimited history for every data family. Price/actions can go back to the earliest date returned for each symbol; valuation measures and financial statements only cover the periods Yahoo exposes, and profile/quote statistics are current snapshots. Missing fields or unavailable symbol histories remain absent/null rather than being inferred. The `YFINANCE_INITIAL_PERIOD=max` setting controls first-time price history; existing R2 histories are incrementally refreshed and merged. Daily, weekly, and monthly prices request only the latest 5 days, 1 month, and 3 months respectively once history exists, then merge by date so recent corrections and new observations are captured without replacing the full stored history.

Current profile and quote statistics are checked each market day. Valuation measures, statements, earnings, and share-count history are refreshed every 7 days by default (`YFINANCE_FUNDAMENTALS_REFRESH_DAYS`), since yfinance exposes these as history queries rather than reliable “since last date” endpoints. Existing per-security JSON is written only when its content changes. The aggregate and search catalog also retain their previous `generated_at` timestamp and are not rewritten when their contents are unchanged, so unchanged market data does not trigger downstream full-universe analysis just because the scheduled job ran.

Each symbol is stored at `market/yfinance/securities/{symbol}.json`. Dividend events are also written as compact objects at `market/yfinance/dividends/{symbol}.json` so the cross-provider publisher need not download full price and financial histories. The summary at `market/yfinance/latest_market_data.json` contains current statistics and latest price/dividend per symbol. After all shards complete, the workflow publishes source-attributed per-security records at `market/securities/v1/{code}.json`. The bucket is private; this workflow does not publish a public URL.

To publish the combined dividend view from data already in R2 without running a full market refresh, manually run this workflow with `mode=publish-dividends`. That mode migrates IRBANK dividend rows from existing overview objects, fills IRBANK gaps from stored Yahoo dividend actions when available, and writes the per-security bundles.

## GitHub configuration

Repository secrets required: `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`. The R2 keys must be S3-compatible credentials with Object Read & Write access scoped to the `diview-market-data` bucket. The account ID is configured in the workflow. The listed-security universe is the version of the IRBANK master committed in the repository; refresh that snapshot before running when new listings or delistings need to be reflected.

The universe job fails if the IRBANK master is missing, malformed, or contains fewer than 1,000 unique issue codes. New issues enter after the committed IRBANK master is refreshed; removed issues are omitted from the latest summary and search catalog. Previously stored per-symbol R2 history is retained. A Yahoo 404 by itself does not remove an issue from the IRBANK universe, so listed issues can appear in the catalog without Yahoo price coverage.

Before starting the data jobs, the workflow checks the Tokyo Stock Exchange session for the current Japan date using `exchange_calendars` (`XTKS`). Weekends and JPX holidays skip collection and summary publication. The scheduled check runs at 03:30 JST on weekdays, and manual starts use the same check. The next trading day's incremental update catches up with the latest available data.

The yfinance `Ticker.calendar` and `Calendars` interfaces describe company events (earnings, dividends, IPOs, splits, and economic events), not Japanese exchange sessions. yfinance's market-status endpoint currently reports U.S. status only, so it cannot reliably identify JPX holidays; the workflow uses the dedicated Tokyo exchange calendar instead.

The collector logs the installed yfinance version, sets the Yahoo locale to Japanese, enables yfinance's own bounded network retry, and uses explicit request timeouts. Transient failures get exponential backoff with jitter; persistent rate limiting stops that shard so it does not keep sending requests. The workflow limits concurrent shards and spaces symbol requests. It refuses to publish the latest summary if any shard is incomplete or contains duplicate symbols. If an API/library error occurs while retrieving data and the installed version is below 0.2, it upgrades yfinance and retries. Other unavailable fields remain warnings and are not fabricated. The scheduled environment starts with the current release allowed by `yfinance>=0.2.66,<2`.

The data source is Yahoo Finance accessed through yfinance, under the access permission the user stated they have. Verify that the permission scope covers full-universe automated retrieval, persistent R2 storage, and any later app use or delivery.
