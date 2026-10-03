# Yahoo Finance data collection to Cloudflare R2

This scheduled GitHub Actions workflow reads the project's existing 3,960-row security master (`data/irbank/organized/d1/securities.csv`), converts each 4- or 5-character code to a Yahoo `.T` symbol, and stores the returned data per symbol in the private `diview-market-data` R2 bucket.

## Collected data

- Daily, weekly, and monthly OHLCV price history for the maximum period Yahoo makes available (`period="max"` on the first run). Adjusted close and Yahoo's dividend, split, and capital-gain actions are retained when returned.
- Dividend and split events derived from that full daily history.
- Current company profile and quote statistics, including current/trailing/forward PER, PBR, EPS, market capitalization, dividend rate/yield, and payout ratio when Yahoo supplies them.
- Monthly, quarterly, and yearly historical valuation measures from yfinance's `get_valuation_measures(periods=None)` when available. These rows are the source for historical PER/PBR; the workflow does not estimate old PER from current values.
- Available annual, quarterly, and trailing income statements, balance sheets, and cash-flow statements; available earnings history; and historical share-count observations.

Yahoo does not provide unlimited history for every data family. Price/actions can go back to the earliest date returned for each symbol; valuation measures and financial statements only cover the periods Yahoo exposes, and profile/quote statistics are current snapshots. Missing fields or unavailable symbol histories remain absent/null rather than being inferred. The `YFINANCE_INITIAL_PERIOD=max` setting controls first-time price history; existing R2 histories are incrementally refreshed and merged.

Each symbol is stored at `market/yfinance/securities/{symbol}.json`. The summary at `market/yfinance/latest_market_data.json` contains current statistics and latest price/dividend per symbol. The bucket is private; this workflow does not publish a public URL.

## GitHub configuration

Repository secrets required: `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`. They must be S3-compatible credentials with Object Read & Write access scoped to the `diview-market-data` bucket. The account ID is configured in the workflow. The workflow runs on weekdays at 03:30 JST and supports manual start from GitHub Actions.

The collector logs the installed yfinance version. If an API/library error occurs while retrieving price history and the installed version is below `YFINANCE_MIN_VERSION`, it upgrades yfinance and retries that history request once. Other errors are recorded as warnings and do not cause fabricated data to be written. The scheduled environment starts with the current release allowed by `yfinance>=0.2.66,<2`.

The data source is Yahoo Finance accessed through yfinance, under the access permission the user stated they have. Verify that the permission scope covers full-universe automated retrieval, persistent R2 storage, and any later app use or delivery.
