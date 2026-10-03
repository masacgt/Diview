# yfinance → Cloudflare R2

This job stores daily Yahoo Finance OHLCV prices for Japanese listed share symbols (Yahoo `.T` tickers) in Cloudflare R2. Its universe is the project's existing 3,960-row security master at `data/irbank/organized/d1/securities.csv`.

## GitHub configuration

Add these repository secrets:

- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`

The workflow is configured for account `8d8f4f1f3c53232aaf7c7d6dd7b33944` and the private bucket `diview-market-data`. Its R2 token should have Object Read & Write permission scoped only to that bucket. A general Cloudflare API token is not used as an R2 S3 secret.

The workflow reads the `security_code_display` column from the project master and turns each 4- or 5-character alphanumeric code into a Yahoo `.T` symbol. Update the project master when the listed universe changes. The master can include non-company listed instruments as well as operating companies.

The workflow runs weekdays at 03:30 JST and can also be started manually. It fetches an initial one-year history for symbols without an R2 history object, then refreshes the most recent five days on later runs. It merges and retains the history object per symbol under `market/yfinance/history/` and replaces `market/yfinance/latest_prices.json`. Workflow runners do not commit fetched prices into the repository.

To test locally, install `requirements.txt`, set `MARKET_UNIVERSE_CSV`, `YFINANCE_INITIAL_PERIOD`, `CLOUDFLARE_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, and `R2_SECRET_ACCESS_KEY`, then run `python scripts/yfinance_r2/fetch_and_upload.py`.

This uses the Yahoo Finance access permission you stated you have. Confirm its scope covers automated full-universe daily collection, persistent R2 storage, and any later app delivery. This workflow does not publish a public R2 URL or configure a public bucket. The retrieved fields are daily open, high, low, close, adjusted close, and volume; it does not retrieve every kind of company information.
