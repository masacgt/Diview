# IRBANK data for Cloudflare R2

## Prepare

From the repository root, build one compact overview per security. Overview JSON groups organization details, annual/quarterly/semiannual financial periods, dividends, valuation history, segments, shareholders, and source attribution. `prepare_dataset.py` preserves IRBANK `fields[].unit` metadata as per-record `field_units`; values remain in their source units. For example, financial and segment monetary rows can be `million_jpy`, valuation market capitalization can be `hundred_million_jpy`, and per-share values use `jpy_per_share`. Full daily price and weekly margin histories are separate objects so the app can load them only when needed.

```powershell
python scripts/irbank_r2/prepare_dataset.py
```

The default output is `%LOCALAPPDATA%\Temp\diview-irbank-r2-ready`. It contains a 4-digit/alphanumeric security catalog, a code-to-object index, and a manifest with object sizes and SHA-256 hashes.

JPX-derived prices and margin data are excluded by default. Include them only after confirming the terms permit persistent R2 storage and app use:

```powershell
python scripts/irbank_r2/prepare_dataset.py --include-jpx
```

## Upload

Install the uploader dependency and inspect its dry-run plan first:

```powershell
python -m pip install -r scripts/irbank_r2/requirements.txt
python scripts/irbank_r2/upload_r2.py
```

For an upload, create an S3-compatible R2 key scoped to the `diview-market-data` bucket with Object Read & Write. Upload independently of Wrangler authentication:

```powershell
& "C:\Codex\Diview\scripts\irbank_r2\upload-and-deploy.ps1" -UploadOnly
```

The uploader prompts locally for keys; neither input is echoed. Objects are size-checked after upload. Data files are uploaded before the catalog/index and the manifest is published last. Existing objects are not deleted.

After the R2 upload succeeds, authenticate Wrangler if needed and deploy the Worker:

```powershell
Set-Location "C:\Codex\Diview\data\irbank\cloudflare"
npx wrangler login
Set-Location "C:\Codex\Diview"
& "C:\Codex\Diview\scripts\irbank_r2\upload-and-deploy.ps1" -DeployOnly
```

## R2 layout and Worker API

- `market/irbank/v1/catalog.json`: app search catalog.
- `market/irbank/v1/index.json`: code-to-object index.
- `market/irbank/v1/manifest.json`: dataset version, counts, included families, byte sizes, and hashes.
- `market/irbank/v1/securities/{code}/overview.json`: one-request company/financial/dividend/valuation/segment/shareholder bundle.
- `market/irbank/v1/securities/{code}/dividends.json`: compact dividend-only records for source aggregation.
- `market/irbank/v1/securities/{code}/daily_prices.json`: optional full daily history.
- `market/irbank/v1/securities/{code}/weekly_margin.json`: optional weekly margin history.
- `market/irbank/v1/securities/{code}/jsf_margin.json`: optional JSF daily lending and reverse-fee history (available when included in the prepared snapshot).

## Unified per-security view

IRBANK and Yahoo source snapshots remain available at their existing keys. To also publish a combined, source-attributed object per Japanese security, run this after uploading either source dataset:

```powershell
python scripts/r2_unified/publish_securities.py
```

The output is `market/securities/v1/{code}.json`. It records each provider's source key and update/attribution metadata, and exposes dividend rows grouped under `dividend_history_by_source`; both sources are retained when available, without merging fiscal-year issuer DPS and dated observed distributions. Invalid Yahoo events and exact duplicate date/amount pairs are filtered. After all current bundles publish successfully, stale four-character objects under this managed output prefix are removed; an empty security universe aborts before publishing or pruning. For IRBANK securities without dividend rows, the publisher uses a Yahoo compact dividend object or migrates events from the existing yfinance security history. This script is a legacy compatibility publisher, not part of the scheduled yfinance v2 collection. The current scheduled job uses the J-Quants universe and writes `market/yfinance/v2/`; it does not refresh this combined prefix. The Worker may retain item-level compatibility reads while financial fields migrate, but v2 search, quote, dividend-event, and monthly-price routes use the v2 namespace.

The Worker exposes `/api/v1/irbank/catalog`, `/api/v1/irbank/manifest`, `/api/v1/irbank/securities/{code}`, and the optional `/prices` and `/weekly-margin` families. Four-character numeric and alphabetic codes are accepted; five-character codes with a trailing `0` are normalized.

## v2 current dividend index

`refresh_v2_dividends.py` fetches the latest annual actual DPS from `GET /v1/dividends` and can parse current-year `DividendPerShare` forecast facts from IRBANK disclosure documents. The output is `market/yfinance/v2/issuer_dividends/current.json`; the Worker uses this index for current annual DPS and yield. A forecast that cannot be normalized to a single current-share basis stays unavailable. The script checks `/v1/usage` before calling the API, writes to a temporary local JSON file by default, and verifies a read-back when `--publish` is used. A partial publication merges into an existing index and is rejected if no full index exists yet.

Per-security API routes use `market/yfinance/v2/issuer_dividends/shards/current.json` and read only the active shard for the security's first two code characters. Publishers write and verify the inactive A/B shard slot, publish the full index, then switch the manifest. Run `python scripts/irbank_r2/sync_v2_dividend_index_shards.py` once to initialize the shard set from the existing R2 index before deploying a Worker version that reads shards; the manual `sync-irbank-v2-dividend-shards.yml` workflow runs this migration without using IRBANK API quota. The daily batch, full dividend refresh, and yield refresh keep both representations current.

`refresh_v2_dividend_yields.py` pages through `GET /v1/screening?filters=dividendYield:gte:0`, validates the complete result and its attribution, and merges each IRBANK percentage and `as_of` date into the same index without replacing DPS or forecast records. A full refresh is about 38 requests at the current listing size. Dry runs require `--input <local-index.json>`; `--publish` reads the existing R2 object and verifies the uploaded object by SHA-256.

For the complete IRBANK listed-security universe (including market and industry), use the authenticated `/v1/securities` cursor API rather than the older Worker overview catalog. The checkpoint script resumes from the last saved cursor and caps each invocation at 200 requests:

```powershell
python scripts/irbank_r2/fetch_irbank_security_catalog.py --output "$env:USERPROFILE\.cache\irbank-security-catalog.json" --max-api-requests 200
```

The current task intentionally uses the saved 4,000-code snapshot without fetching the remaining cursor pages. Process only those saved codes for forecasts while preserving existing actual DPS and yield data:

```powershell
python scripts/irbank_r2/run_irbank_forecast_daily.py --universe-json "$env:USERPROFILE\.cache\irbank-security-catalog-20261102.json" --max-batches 20 --requests-per-batch 200
python scripts/irbank_r2/audit_irbank_forecast_coverage.py --universe-json "$env:USERPROFILE\.cache\irbank-security-catalog-20261102.json"
```

`run_irbank_forecast_daily.py` consumes only the saved snapshot, does not fetch additional securities pages, and stops before any R2 write when the daily quota is exhausted. Each run allows at most twenty batches of 200 requests. Partial-snapshot metadata stays attached to `batch_sync`; `coverage_complete` remains false because the snapshot has a next cursor. The auditor labels its counts `partial_snapshot_only`. The batch processor preserves existing actual DPS, yield, and unrelated index items. Forecast extraction follows `next_cursor` for up to ten disclosure-list pages and inspects up to eight candidate XBRL documents (18-request worst-case reservation). When a cached forecast's source document is still in the ordered disclosure list, newer candidates are inspected first; only then may the cached value be reused, and only for a future period without split-basis normalization. The batch report counts cache reuses and detail calls avoided. It records a separate `forecast_resolution` for an explicit value/zero, no title-matched disclosure, missing XBRL, missing dividend/distribution element, securities unsupported by the disclosure endpoint, or a case requiring review; the legacy `forecast_status` remains compatible with the Worker. Records without the new audit version are reprocessed so older `empty` results can be classified. Each published batch verifies its SHA-256 read-back. The workflow requires the `IRBANK_API_KEY`, `R2_ACCESS_KEY_ID`, and `R2_SECRET_ACCESS_KEY` credentials.

```powershell
$env:IRBANK_API_KEY = '<IRBANK Connect API key>'
$env:CLOUDFLARE_ACCOUNT_ID = '<Cloudflare account ID>'
$env:R2_ACCESS_KEY_ID = '<R2 access key>'
$env:R2_SECRET_ACCESS_KEY = '<R2 secret key>'
python scripts/irbank_r2/refresh_v2_dividends.py --forecast-codes 8766 --publish
python scripts/irbank_r2/refresh_v2_dividend_yields.py --publish
python scripts/irbank_r2/refresh_v2_dividend_batch.py --publish --max-api-requests 200 --refresh-days 60
```

Omit `--forecast-codes` to refresh actuals only. Use `--codes 8766 --forecast-codes 8766` for a targeted update; it preserves other securities and existing forecasts in the R2 index. The default full-catalog refresh consumes about one IRBANK request per security, plus selected disclosure-list/document requests, and stops before exceeding the remaining daily quota. Requests are paced below IRBANK's current 60-per-minute limit, so a full-catalog refresh takes about 70 minutes.
