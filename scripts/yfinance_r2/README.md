# yfinance v2 market data

The scheduled `yfinance-r2.yml` workflow is the production collector. It runs at 21:00 Japan time on weekdays, checks the XTKS exchange calendar, obtains the current listed-security universe from J-Quants v2, and writes app-required yfinance data to `market/yfinance/v2/` in the private `diview-market-data` R2 bucket.

The J-Quants master includes equities, ETFs, and REITs. Its product category is retained in the v2 universe, while the app continues to resolve industry names through its bundled industry master. Each J-Quants code is converted to a Yahoo Finance `.T` symbol. A security enters the search catalog only after its yfinance profile and completed daily close are available.

## Published data

- `manifest.json`, `universe/current.json`, `catalog/current.json`, generation snapshots, and `publications/current.json` describe the active universe and publication state.
- `securities/{symbol}/profile.json` contains the Japanese-locale yfinance company profile and display-name fallback fields.
- `securities/{symbol}/current.json` contains the latest completed close, its date, trailing 12-month dividends, expected and current fiscal-year dividend inputs, yield, PER/PBR, and per-field retrieval state.
- `securities/{symbol}/prices/monthly/{year}.json` contains the latest 13 months of split-adjusted `Close` values. Older points remain under `archive/prices/monthly/{symbol}/{year}.json` for up to ten years.
- `securities/{symbol}/dividends/{year}.json` and `splits/{year}.json` contain the recent ten years of events. Older events move to `archive/events/{symbol}/{dividends|splits}/{year}.json` and are retained indefinitely.
- App-required annual financial inputs are stored in `securities/{symbol}/financials/{fiscal-year}.json`; `sync.json` records attempts, last success, retry count, per-field state, and failure reason.
- Calculated XTKS rights-date data is published at `derived/rights_dates/current.json`. The calculation consumes only v2 event and current-data objects.
- Unused fields from a legacy yfinance security object are compressed under `archive/legacy/securities/{symbol}/unused.json.gz` and retained for ten years. The source object is removed only after the v2 data and archive have been read back and checked.

## Collection and publication behavior

The market calendar skips weekends and Japanese exchange holidays. Per security, the collector allows one initial attempt and up to three retries per session, using 15-minute, one-hour, and four-hour backoffs. The count resets on the next market session. A failed fetch preserves prior successful values and records the failure. A successful response with no dividend or split history records an empty state and is checked again on the next market session.

The catalog includes newly successful values and the last available data for securities whose refresh failed. Securities that have never had a successful profile and close remain in the universe status list but stay out of search. Securities missing from the latest J-Quants list are marked inactive and are not fetched or newly searchable; their stored history remains in R2.

The Worker exposes v2 data through the app API; R2 public URLs remain disabled. Android stores its last successful catalog fetch time on each device and observes both the Japan-local daily limit and the 12-hour minimum interval. Search and registration use the published R2 catalog and do not trigger an on-demand yfinance request.

## GitHub configuration

The repository requires these Actions secrets:

- `JQUANTS_API_KEY`
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`

The R2 credentials should be scoped to read and write the `diview-market-data` bucket. `CLOUDFLARE_ACCOUNT_ID` and the bucket name are configured in the workflow. A manual workflow dispatch runs the same calendar-gated v2 sync as the schedule.

The first v2 run also migrates required values from existing `market/yfinance/securities/{symbol}.json` objects. It verifies the v2 profile, current data, event history, monthly prices, financial records, and legacy-field archive before deleting each migrated legacy object. The existing IRBANK namespace is not bulk archived or pruned.

## Retired collection paths

The prior `full`, `weekly-minimal`, and `publish-dividends` jobs have been removed from the production workflow so they cannot continue publishing into the legacy yfinance schema. Their scripts remain in the repository for historical reference and must not be used for scheduled production collection. The monthly rights-date workflow remains active and reads/writes only v2 objects.
