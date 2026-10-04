# IRBANK data for Cloudflare R2

## Prepare

From the repository root, build one compact overview per security. Overview JSON groups organization details, annual/quarterly/semiannual financial periods, dividends, valuation history, segments, shareholders, and source attribution. Full daily price and weekly margin histories are separate objects so the app can load them only when needed.

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
- `market/irbank/v1/securities/{code}/daily_prices.json`: optional full daily history.
- `market/irbank/v1/securities/{code}/weekly_margin.json`: optional weekly margin history.

The Worker exposes `/api/v1/irbank/catalog`, `/api/v1/irbank/manifest`, `/api/v1/irbank/securities/{code}`, and the optional `/prices` and `/weekly-margin` families. Four-character numeric and alphabetic codes are accepted; five-character codes with a trailing `0` are normalized.
