# yfinance v2 market data

The `yfinance-r2.yml` workflow is configured as the scheduled collector. It starts at 21:17 Japan time on weekdays, checks the XTKS exchange calendar, obtains the current listed-security universe from J-Quants v2, and writes app-required yfinance data to `market/yfinance/v2/` in the private `diview-market-data` R2 bucket. It uses the standard GitHub-hosted Ubuntu runner; standard runners are free for public repositories. The first v2 R2 publication and production Worker deployment are still pending; until then, production API responses may continue to use the previous data path.

The J-Quants master includes equities, ETFs, and REITs. Its product category is retained in the v2 universe, while the app continues to resolve industry names through its bundled industry master. Each J-Quants code is converted to a Yahoo Finance `.T` symbol. A security enters the search catalog only after its yfinance profile and completed daily close are available.

The collector pins `yfinance==1.7.0`, `scipy==1.18.1`, and `scikit-learn==1.9.1` so scheduled runs do not silently change price-repair or dividend-event behavior when a library release appears. yfinance's `repair=True` path uses SciPy for random unit-mixup detection and scikit-learn's DBSCAN when reconstructing damaged price intervals; without scikit-learn, some history requests fail before actions can be stored. The collector configures yfinance to raise request and repair failures into the per-security retry state machine. Upgrade these dependencies deliberately and validate representative split, reverse-split, and ordinary dividend histories before publishing a new collector version.

## Published data

- `manifest.json`, `universe/current.json`, `catalog/current.json`, generation snapshots, and `publications/current.json` describe the active universe and publication state.
- `securities/{symbol}/profile.json` contains the Japanese-locale yfinance company profile and display-name fallback fields.
- `securities/{symbol}/current.json` contains the latest completed close and date, trailing 12-month dividends, Yahoo's `dividendRate` estimate for the next 12 months, current-fiscal-year dividend events observed through the collection date, yield, PER/PBR, and per-field retrieval state. `dividendRate` is not an issuer-confirmed next-fiscal-year DPS.
- `securities/{symbol}/prices/monthly/{year}.json` contains the latest 13 months of split-adjusted `Close` values. Older points remain under `archive/prices/monthly/{symbol}/{year}.json` for up to ten years.
- `securities/{symbol}/dividends/{year}.json` and `splits/{year}.json` contain the recent ten years of events. Older events move to `archive/events/{symbol}/{dividends|splits}/{year}.json` and are retained indefinitely. Actions come from yfinance daily history with `period="max"`, `actions=True`, and `repair=True`; a successful response replaces the stored events inside its reported date coverage so repaired amounts and removed duplicates replace older values. Event amounts are stored as returned after yfinance repair, with no second split-ratio adjustment in this pipeline. A failed fetch keeps the previous event history and records the failure separately.
- The financial-data history chart sums yfinance-repaired distribution events by calendar year. It does not present IRBANK's adjusted fiscal-year DPS as an observed yfinance distribution or company-reported calendar-year total. Calendar-year sums can differ from issuer fiscal-year DPS, and the event share basis is not independently normalized. `current.json` records the action-history coverage, yfinance version, amount basis, and repair mode.
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

The secrets must be added in the repository's Actions secrets settings before the scheduled collector can publish. Their values are never stored in the repository. Scheduled runs can be delayed during GitHub Actions load; the schedule is set away from the start of the hour to reduce this risk. GitHub automatically disables scheduled workflows in public repositories with no repository activity for 60 days, so periodically confirm the workflow remains active.

The R2 credentials should be scoped to read and write the `diview-market-data` bucket. `CLOUDFLARE_ACCOUNT_ID` and the bucket name are configured in the workflow. A manual workflow dispatch runs the same calendar-gated v2 sync as the schedule.

The first v2 run also migrates required values from existing `market/yfinance/securities/{symbol}.json` objects. It verifies the v2 profile, current data, event history, monthly prices, financial records, and legacy-field archive before deleting each migrated legacy object. The existing IRBANK namespace is not bulk archived or pruned.

## Retired collection paths

The prior `full`, `weekly-minimal`, and `publish-dividends` jobs have been removed from the production workflow so they cannot continue publishing into the legacy yfinance schema. Their scripts remain in the repository for historical reference and must not be used for scheduled production collection. The monthly rights-date workflow remains active and reads/writes only v2 objects.
