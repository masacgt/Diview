# Architecture

## Runtime and storage

- The current app source is `kurogane-dividend/`; Android builds come from `kurogane-dividend/android/`.
- The Cloudflare Worker API is in `data/irbank/cloudflare/src/index.js` and uses D1 for operational records and R2 bucket `diview-market-data` for market snapshots.
- The Worker binds R2 as `MARKET_DATA`. R2 public URLs remain disabled; selected objects are served by explicit Worker routes.

## Japanese market data

- The target source for listed-security data is yfinance. J-Quants v2 supplies the eligible security-code universe; XTKS calendar sessions gate the scheduled run at 21:00 JST.
- New objects use `market/yfinance/v2/`: current profile and quote, year-partitioned dividends and splits, 13 months of monthly closes, 10 years of monthly-price archive, and app-required annual financial inputs. Unused legacy yfinance fields are gzip archived for 10 years after v2 read-back.
- The workflow in `.github/workflows/yfinance-r2.yml` runs the v2 job on weekdays and gates it with the XTKS calendar. It records per-security failures and makes at most four attempts per session, with 15-minute, 1-hour, and 4-hour backoffs.
- Worker search and registration require the published v2 catalog; security, dividend, monthly-price, financial-data, and analysis routes prefer v2 R2 objects. `/api/v1/live-quotes` serves only a completed static catalog page; the hourly Worker trigger no longer enqueues Yahoo live-price collection, and any messages left in the retired quote queue are acknowledged without fetching prices.
- Android stores the last successful catalog fetch time per device. It refreshes no more than once per JST date and no sooner than 12 hours after the previous success; catalog failure leaves local prices intact.
- The J-Quants adjusted-daily-quotes snapshot below is a separate historical debug endpoint. The Android market-data flow does not use it as a quote source.

## J-Quants adjusted daily quotes

- The original snapshot is stored at `debug/jquants/v1/daily_quotes.csv`.
- The public subset is stored at `debug/jquants/v1/adjusted_daily_quotes.csv` with columns `Date,Code,AdjFactor,AdjO,AdjH,AdjL,AdjC,AdjVo`.
- `GET` and `HEAD /api/v1/jquants/daily-quotes` stream the subset from R2; single byte-range requests are supported and limited to five requests per IP per minute.
- This is a static snapshot for 2024-07-22 through 2026-07-17. No refresh job is configured.
