# Cross-component review fixes (2026-10-20)

## Scope and baseline

Reviewed and changed the working tree on `codex/yfinance-financial-coverage-20261018`, based on `51c2fa6d11627c0a11c1a2c4771d009d1d770a2e`. The 15 pre-existing modified files and `.codex-remote-attachments/` were preserved. No commit, push, Worker deployment, R2 publication, or production D1 migration/repair was performed.

## Changes

| Review issue | Before | After |
| --- | --- | --- |
| Normal financial persistence | Daily valuation dict replaced the annual valuation list, causing `str.get` failure | Separate variables; persistence regression exercises `collect_symbol` |
| D1 financial periods | Unquoted `YYYY/MM` was evaluated as division, causing key collisions | Per-table SQL types; annual periods quoted, dividend years numeric; streamed CSV input |
| yfinance statement contract | Compact API keys were omitted | Compact and legacy formatted aliases accepted |
| Historical valuation contract | API did not provide the requested split-adjusted EPS/BPS/yield but could report success | Invalid extraction removed; saved history retained as stale, missing fields explicitly unsupported |
| DOE partial retry | BS and CF could be fetched separately and never produce a new DOE | Retry either dependency together, using same-period fresh inputs |
| Letter security codes | Targeted/all-financial refresh excluded `130A`-style codes | Same code validation as normal sync; malformed active universe entries fail instead of disappearing |
| Android cancellation/cache | Cancelled request left a daily attempt marker; force still returned same-day cache | Per-code ownership, cancellation cleanup, force bypasses cache |
| D1 analysis freshness | Change detection watched the retired market snapshot | V2 catalog and financial revision ETags drive persistence; failed persistence retains the pending source tag |
| Publication identity | Same-day catalog generations were overwritten | UUID-suffixed immutable generations; API retains legacy date-ID compatibility |
| D1 reconstruction | Operational schema was absent | Additive schema migration and insert-only definition defaults; local initializer applies migrations |
| Period identity | Latest financial/valuation matching used only `YYYY/MM` | Full `period_end` ordering and exact match; unambiguous legacy fallback |

Historical EPS/BPS/yield are not reconstructed from current quotes or unadjusted annual EPS: those would change the meaning of the stored fields. A source providing historical split-adjusted, fiscal-period values is still required for fresh values. This limitation is explicitly represented in status/reason metadata.

`refresh_financial_v2.py` publishes `market/yfinance/v2/financial_revision.json` in `finally` so a handled partial-write/read-back failure also invalidates analysis. It cannot guarantee this write after a hard process kill or an unavailable R2 service.

Legacy financial migration also updates the revision marker after its processing phase, including caught partial failures; `--dry-run` never writes it. Mixed-unit guards remain intact if migration is replayed. For sales/profit/CF, seed-only flat amounts are withheld when yfinance amounts are present in the same group. Raw seed evidence and metadata are preserved; the Worker does not reintroduce those amounts while merging provenance.

Generation IDs are opaque `YYYY-MM-DD-<32 lowercase hex>` strings. `market_data_date` and schedule `active_session_date` remain dates. The immutable generation object is written before `catalog/current.json`; the catalog, income-advice catalog/rows, publication record, and manifest reference the same generation. This fixes pagination identity, not atomicity across every per-security R2 object.

## Validation

- Offline Python collector/refresh/calendar/universe suite: 88 passed; separate existing R2 bundle suite: 3 passed.
- Worker/D1 suite: 46 passed, including compilation of 61 actual Worker SQL literals against SQLite, migration replay/preservation, period collision regression, generation pinning, mixed-unit guards, and analysis persistence retry.
- D1 input streaming: generated approximately 49 MB fixture completes with a 32 MB Node heap in the regression test. This is a fixture result, not a production memory or latency measurement.
- JDK 17 Android final validation: all 153 JVM tests passed (zero failures/errors/skips), and `:app:assembleDebug` succeeded with the waiting-caller correction. APK: `kurogane-dividend/android/app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check` passed. The index remains empty; pre-existing edits are retained.
- Device install and runtime/visual verification: not performed.

## Production rollout and repair boundary

1. Deploy the backward-compatible Worker generation consumer before publishing new UUID generation catalogs.
2. Back up and inspect the actual D1 schema before applying the additive migrations. `CREATE TABLE IF NOT EXISTS` does not repair incompatible existing tables.
3. Compare D1 annual `fiscal_year` with `json_extract(data_json, '$.fiscal_year')`, validate `YYYY/MM` including month range, and inventory complete keys. Division results can be stored as TEXT, so checking only SQL type is insufficient.
4. Compare affected securities with trusted original CSVs. Rows overwritten by `INSERT OR REPLACE` cannot be recovered from D1 alone.
5. Generate corrected imports into a fresh directory. Validate a narrowly scoped repair in an offline copy: recover complete periods, compare payloads, then remove only identified corrupt rows. Do not blanket-rebuild production tables or reuse filename-only completed-import state as proof that corrected files were applied.
6. After an explicitly authorized deployment/refresh, verify 4502 and letter-code samples via Worker and on device. Local tests do not prove that production objects or installed APKs have changed.

## Local verification and optional device install

Run from PowerShell. The Python test environment is local and ignored by Git; its dependencies match the collector test requirements. All remote services in these tests are replaced by fixtures.

```powershell
Set-Location C:\Codex\Diview
$env:TEMP = 'C:\Codex\Diview\.build'
$env:TMP = $env:TEMP
.\.build\review-fixes-venv\Scripts\python.exe -B -m unittest discover -s scripts/yfinance_r2 -p 'test_*.py'
node --test data/irbank/cloudflare/test/*.test.mjs data/irbank/cloudflare/test/d1.test.cjs

$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME = 'C:\Codex\Diview\.gradle-home'
Set-Location C:\Codex\Diview\kurogane-dividend\android
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug

# Optional installs: these steps were NOT executed during the source fix.
adb devices -l
adb -s emulator-5554 install -r 'C:\Codex\Diview\kurogane-dividend\android\app\build\outputs\apk\debug\app-debug.apk'
adb -s RFCNC0E4H8J install -r 'C:\Codex\Diview\kurogane-dividend\android\app\build\outputs\apk\debug\app-debug.apk'
```

Commit/push are not executed. Publication must selectively stage only this task's changes: `sync_v2.py`, `test_sync_v2.py`, and project notes also contain earlier work, so do not stage whole dirty files indiscriminately or use `git add .` / `git commit -a`.

If publication is subsequently requested, inspect the branch/remote and start with a clean index. Use `git add -p -- <reviewed tracked paths>` to select only the intended hunks and `git add -- <reviewed new paths>` for the new migration/test/report files. Review `git diff --cached` before running `git commit -m "Fix financial pipeline contracts and retry consistency"` and `git push -u origin HEAD`. The placeholders deliberately require selecting the reviewed paths; the existing unrelated changes are not publication targets.

## Requested rollout attempt (2026-10-20)

- Installed the JDK 17 debug APK on SCG01 (RFCNC0E4H8J) and emulator-5554. Both app processes remained alive after launch; the emulator reported MainActivity as top-resumed. No visual or feature-by-feature device acceptance was performed.
- npx wrangler whoami reported that the saved Cloudflare token is expired; no CLOUDFLARE_API_TOKEN or R2 credentials are configured in the environment. Therefore no production D1 backup, data repair, migration, R2 refresh, Worker deployment, or production API read-back was performed. No production data was changed.