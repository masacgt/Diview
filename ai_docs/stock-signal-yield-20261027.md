# Stock Detail buy-signal dividend yield correction

The top card resolved annual forecasts from `AnnualDividendResolver`, while the buy-signal summary and detail still used current/trailing security DPS and its no-dividend flag. A positive forecast could therefore coexist with `0.00%（無配）` in the same screen.

`StockDetailScreen.kt` now shares the top-card yield, forecast DPS, availability and no-dividend status with the buy-signal summary, detailed yield range, comparison progress and explanatory rows. Existing forecast source priority, explicit zero handling and screen design are preserved. Missing historical range or EPS still leaves the corresponding comparison/valuation unavailable.

Regression coverage includes the reported 8953 values (6018 / 106900 * 100 = 5.63%), existing explicit-zero/missing-forecast cases, and a Compose assertion that a zero security quote with an 80-yen forecast displays `配当利回り 2.67%` in the buy-signal card.

## Validation

- JDK 17 `:app:testDebugUnitTest :app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded. All 216 unit tests passed.
- Emulator `AssetDataRefreshTest#forecastReplacesNoDividendQuoteAndSurvivesCachedRefreshForEveryHeldJapaneseCode` passed (1 test, 9.771 seconds), including the buy-signal `2.67%` assertion under a zero/no-dividend quote.
- Installed and launched on emulator-5560 and physical RFCNC0E4H8J (SCG01). Both installed base.apk hashes match the built APK: `45700f86b3e0d8b291dee3b08472ab8f9bd110801c06101e6c84deca3cf391eb`.
- Physical UI and live 8953 production values were not visually verified; 8953 numeric expectations are covered by the unit regression. Build warnings were existing Compose deprecations, SDK metadata version and unstripped native library notices.
- Publication authorized on 2026-10-27. Correction commit `c90b58f2` is based on refreshed main `594dd116`, which already includes acquisition-yield caption commit `dcb2e98b`. Rebase skipped our duplicate acquisition-caption cherry-pick; `git diff 6e85b5a5 HEAD -- kurogane-dividend/android` confirms identical validated Android sources.
- Final integrated JDK 17 unit/debug/test-APK build passed (216 unit tests). The isolated buy-signal Compose regression passed on emulator-5560 (8.305 seconds) and physical RFCNC0E4H8J (4.685 seconds). Both devices installed/launched the exact same-worktree APK, and both base.apk hashes match `6c165cdef2535ab547189e6f2d26cab3325ff4e2689a8563c284a0ab3a6056c1`. These are synthetic regression values; live 8953 production UI remains unverified.

## Branch integration review

- Reviewed 99 non-ancestor local/remote refs and all 103 registered worktrees; 13 refs were patch-equivalent at audit time. Preserve dirty worktrees, attachments and CSV samples, including historical temporary worktrees with deleted-file states.
- Other unmatched historical patches are already represented or superseded on main (stock comments/finite metric fallback, category labels, financial revision, yfinance contracts/tests, J-Quants tools and expanded legacy audit). Existing review records in `branch-integration-20261027.md`, `asset-acquisition-yield-main-integration-20261027.md` and `todo.md` remain applicable. Current code confirms hidden schedule method labels and removed company-info status. J-Quants v3 source matches the historical tool patch; current legacy audit is expanded.
- Exclude the `67a63751` lineage (14 CSV paths). No historical branches or worktrees are deleted or blindly merged. Final main diff contains only this buy-signal correction, its regressions and project notes; the asset-caption change is already in main.
- Publish the correction branch and fast-forward main without force after a final fetch and ancestry/diff checks.

Worktree: `C:/Codex/Diview/.task-worktrees/stock-signal-yield`, base main `dd20fb86`.

## Reproduce build/install and publication

```powershell
$taskRoot = 'C:\Codex\Diview\.task-worktrees\stock-signal-yield'
$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:ANDROID_HOME = 'C:\Android\sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "$taskRoot\kurogane-dividend\android"
git status --short --branch
git rev-parse HEAD
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$taskApk = "$taskRoot\kurogane-dividend\android\app\build\outputs\apk\debug\app-debug.apk"
foreach ($targetSerial in @('emulator-5560', 'RFCNC0E4H8J')) {
    & "$env:ANDROID_HOME\platform-tools\adb.exe" -s $targetSerial install -r $taskApk
    if ($LASTEXITCODE -ne 0) { throw "Install failed: $targetSerial" }
    & "$env:ANDROID_HOME\platform-tools\adb.exe" -s $targetSerial shell am start -n com.kurogane.dividend/.MainActivity
}
# Publication commands used after committing only this correction:
Set-Location $taskRoot
git add -- ai_docs/stock-signal-yield-20261027.md ai_docs/todo.md kurogane-dividend/android/app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt kurogane-dividend/android/app/src/test/java/com/kurogane/dividend/model/AnnualDividendResolverTest.kt kurogane-dividend/android/app/src/androidTest/java/com/kurogane/dividend/AssetDataRefreshTest.kt
git diff --cached --check
git commit -m "fix: share forecast dividend yield with stock buy signal"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }
git push -u origin HEAD:refs/heads/codex/fix-stock-signal-yield-20261027
```
