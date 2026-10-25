# Cross-stack review fixes (2026-10-25)

## Changes

- `scripts/yfinance_r2/sync_v2.py`: same-session successful skips and exhausted partial retries rebuild their catalog row and income-advice candidate from saved profile/current/dividend objects. A missing publishable record becomes an explicit failure instead of silently reporting old catalog data as a successful refresh.
- `data/irbank/cloudflare/src/index.js`: issuer-dividend corrections replace an exact or unambiguous fiscal-period match; a year-only correction cannot discard multiple periods. Month-only source periods remain supported.
- Android `MarketDataContract.kt`, `CloudflareMarketRepository.kt`, `DividendViewModel.kt`, and `StockDetailResolver.kt`: successful responses withdrawing annual DPS remove the old cache amount and block fallback to persisted holdings, observed historical dividends, and an older catalog. Confirmed no-dividend remains separate from unavailable DPS. Existing finite numeric calculations use zero as the unavailable contribution, without setting the confirmed-no-dividend flag. Failed requests retain existing cached values.
- Android Japanese financial, monthly-price, split-history and analysis requests accept distinct five-character class-share codes; ordinary padded codes ending in zero normalize to four characters.
- `build-d1-import.cjs` rejects missing financial composite-key components. Migration `0007_financial_period_key_guard.sql` rejects new/updated nullable accounting-standard/consolidation keys without deleting historical rows. Migration 0006 belongs to separate feedback work and is preserved.

## Validation

- Worker/routes, scheduled analysis, feedback, and D1 tests: 57 passed, including period corrections, ambiguous-year retention, nullable-key rejection, migration replay, and preserving pre-existing rows.
- yfinance offline suite: 106 passed in a task-local Python virtual environment outside the repository. The final sync test fixture also passed all 75 sync tests, including repeated publication from completed per-security writes with an older catalog and zero Yahoo calls.
- Android JDK 17 `:app:testDebugUnitTest :app:assembleDebug`: passed; debug APK generated. Regression coverage includes withdrawn-DPS cache/holding resolution and five-character financial/price/split requests. The first test run exposed a missing `code` in the new split-response fixture; the fixture was corrected and the full suite rerun successfully.
- Production Worker deployment, D1 migration, R2 writes, device installation and interaction acceptance have not been performed for this change.
- Concurrent feedback/email, asset-screen, and documentation edits are outside this fix. No staging, commits, pushes, or resets were performed by this task.

## Publication and device steps (not executed)

Review and select only this fix's hunks. The guard stops if another task already staged changes; do not clear that task's index. The APK includes the current checkout, including other Android edits, so review that state before installation.

```powershell
Set-Location C:\Codex\Diview
git status --short
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw 'Other changes are already staged. Finish their review separately before publishing these fixes.' }
git add -p -- scripts/yfinance_r2/sync_v2.py scripts/yfinance_r2/test_sync_v2.py data/irbank/cloudflare/src/index.js data/irbank/cloudflare/test/routes.test.mjs data/irbank/cloudflare/test/d1.test.cjs data/irbank/scripts/build-d1-import.cjs kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/CloudflareMarketRepository.kt kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/model/StockDetailResolver.kt kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/CloudflareMarketRepositoryTest.kt kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/model/StockDetailResolverTest.kt
git add -- data/irbank/cloudflare/migrations/0007_financial_period_key_guard.sql kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/data/MarketDataContract.kt kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/data/MarketDataContractTest.kt ai_docs/review-fixes-20261025.md
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Resolve staged diff errors before committing.' }
git diff --cached
# Continue only after checking that the staged diff contains these fixes alone.
git commit -m 'Fix market publication recovery and financial data contracts'
if ($LASTEXITCODE -ne 0) { throw 'Commit failed; do not push.' }
git push origin HEAD
if ($LASTEXITCODE -ne 0) { throw 'Push failed.' }

$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location C:\Codex\Diview\kurogane-dividend\android
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed; do not install an older APK.' }
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$apkPath = 'C:\Codex\Diview\kurogane-dividend\android\app\build\outputs\apk\debug\app-debug.apk'
& $adbPath devices -l
& $adbPath -s emulator-5554 install -r $apkPath
if ($LASTEXITCODE -ne 0) { throw 'Emulator installation failed.' }
& $adbPath -s RFCNC0E4H8J install -r $apkPath
if ($LASTEXITCODE -ne 0) { throw 'Physical-device installation failed.' }
```

Worker/D1 fixes require a separately authorized production release. Existing NULL-key financial rows, if any, must be investigated before any cleanup; this migration deliberately does not remove them.
