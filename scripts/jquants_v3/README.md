# J-Quants v3 local database

`jquants_v3.sqlite` is a local J-Quants-only snapshot for app-market data. It combines the project-local J-Quants daily quote snapshot with J-Quants API files acquired under the currently configured API key. It does not import yfinance or IRBANK rows, and it is not deployed to Cloudflare.

## Current snapshot

The expanded database is stored under the Git-ignored `local_artifacts/jquants-v3-acquisition-20261025/` directory. Its SHA-256 and exact row counts are recorded in `jquants_v3_report.json` beside the database.

| Dataset | Current contents |
| --- | --- |
| Daily quotes | 2,186,594 rows, 4,746 securities, 2024-07-22 through 2026-07-31. The project-local J-Quants snapshot runs through 2026-07-17; API data fills the next nine trading sessions. |
| Monthly prices | 105,784 rows derived from each security's final observed adjusted close per month. |
| Securities master | 4,444 rows for 2026-07-31. |
| Valuation indicators | 4,444 rows for 2026-07-31. Historical valuation dates are not yet included. |
| Financial summary | 328 disclosures dated 2026-07-31. This is one disclosure date, not the full historical summary archive. |
| Trading calendar | 731 dates, 2024-08-02 through 2026-08-02. |

`dataset_coverage` records the available periods and gaps inside SQLite. `jquants_api_records` retains the full J-Quants API columns as JSON. `api_source_files` records the downloaded file hashes and endpoint names. The J-Quants API key is not stored.

## Access limits

The current key returned the accessible daily/master/valuation window as 2024-08-02 through 2026-08-02 (12-week delay). The official [plan data-period table](https://jpx-jquants.com/en/spec/data-spec) says Free-plan CSV bulk downloads are unavailable except for the trading calendar, so the 2-year historical API datasets require date-by-date retrieval and are rate-limited. The current key returned HTTP 403 for Premium-only cash-dividend and financial-statement-details endpoints.

As a result, this snapshot does not yet contain full historical valuation or financial-summary coverage, cash-dividend event dates, or detailed BS/PL/CF rows. J-Quants personal API does not provide a standalone exact split-event history; daily adjustment factors remain available in `daily_quotes`. Do not treat these gaps as empty/zero app data. The local v3 database must remain local unless the applicable J-Quants usage terms authorize external distribution.

## Build from the project-local backup

The base database builder verifies the original J-Quants manifest hashes, adjusted-row correspondence, unique keys, record counts, and SQLite integrity:

```powershell
python scripts/jquants_v3/build_database.py `
  --daily-quotes local_artifacts/cloudflare-backups/<snapshot>/r2/debug/jquants/v1/daily_quotes.csv `
  --adjusted-daily-quotes local_artifacts/cloudflare-backups/<snapshot>/r2/debug/jquants/v1/adjusted_daily_quotes.csv `
  --manifest local_artifacts/cloudflare-backups/<snapshot>/r2/debug/jquants/v1/manifest.json `
  --output local_artifacts/<new-snapshot>/v3/jquants_v3.sqlite
```

## Extend with acquired API files

With `JQUANTS_API_KEY` set in the environment, the J-Quants CLI can save API responses to CSV. The current snapshot's API files are under `local_artifacts/jquants-v3-acquisition-20261025/`. After adding date-named `daily_quotes_YYYYMMDD.csv` files, extend a new base database with:

```powershell
python scripts/jquants_v3/extend_database.py `
  --base-database local_artifacts/cloudflare-backups/20261025T0106Z/v3/jquants_v3.sqlite `
  --data-dir local_artifacts/jquants-v3-acquisition-20261025 `
  --output local_artifacts/jquants-v3-acquisition-20261025/jquants_v3.sqlite
```

The extension refuses to overwrite an existing output, checks API CSV fields and duplicate quote consistency, derives monthly prices, and runs SQLite `integrity_check` before publishing the local output. Acquisition artifacts, database files, and reports belong under `local_artifacts/`, which is excluded from Git.
