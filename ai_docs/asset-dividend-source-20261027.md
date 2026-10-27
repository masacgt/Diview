# Asset Management dividend sources (2026-10-27)

## Change

Asset holdings previously preferred cached current/trailing DPS and could stay at confirmed zero while Stock Detail displayed a positive forecast. `AnnualDividendResolver` now supplies both Stock Detail's top card and portfolio runtime values with the same priority: current/future fiscal-year issuer forecast, current JST year event estimate, then unit-validated Yahoo forward annual DPS. Finite nonnegative values are accepted; an explicit forecast zero remains authoritative. Positive forecasts replace stale no-dividend status, and forecast-based yields replace trailing provider yields. Fund distributions keep their existing price basis.

Asset refresh now loads financial data and current-year dividend events for supported non-fund holdings using the existing endpoint/cache implementations, even when historical rows or dividend months already exist. Quote/event refreshes reapply forecasts, preventing a later market snapshot from restoring zero. The old portfolio-only historical DPS fallback is removed when current security/observed-distribution data is available.

## Validation

- JDK: `C:/Users/htdn/.jdks/ms-17.0.20.1`; Android SDK: `C:/Android/sdk`.
- JDK 17 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` succeeds; all 213 unit tests pass.
- App APK SHA-256: `bafbf4043f8fd8667c9a46044cbca7d6a068cf72c84e6d36f135992fd5085442`.
- Final app installed and launched on physical `RFCNC0E4H8J` (SCG01) and `emulator-5560`; both installed `base.apk` hashes match the build SHA-256 above. No emulator crash-buffer entries were present at read-back.
- Emulator isolated-preference tests prove positive issuer forecasts replace no-dividend market quotes for 7203 and 2914, 100 shares show ¥8,000/year while Stock Detail shows ¥80, and current-year event estimates refresh despite saved dividend months. The four existing `AssetTabNavigationTest` cases also pass on the final app. The final eight-case acquisition rerun passes 8/8 after correcting only its request-count fixture (`instrumentation-final.txt`, 52.931 seconds).
- Live existing emulator portfolio: 8984 displays 5.93% and ¥20,944/year for four shares with Japanese withholding enabled, consistent with ¥6,571 gross DPS (6,571 × 4 × 0.79685). The final app screenshot `final-asset-8984.png` confirms these values. Live 8984 detail-screen navigation was not confirmed in the manual screenshot session; cross-screen equality is covered by the isolated UI test.
- Physical UI acceptance remains unverified because the device is locked. No portfolio fixtures were written to production preferences; instrumentation uses isolated `AuditApplication` stores.

## Test fixture correction

The financial request-count assertion formerly counted `/api/v1/financial-data/revision` as a security request. It now asserts exactly one request for each expected security path and no additional requests on revisiting successful empty financial data. Revision polling remains enabled.

## Publication

Worktree: `C:/Codex/Diview/.task-worktrees/asset-dividend-source`; branch: `codex/asset-dividend-source-20261027`, based on main `8887c9a4`. Shared dirty source, strings, project notes, AGENTS.md and account CSV exports are preserved. Remote publication has not been requested.


## Rebuild, install, and optional publication

```powershell
$taskRoot = 'C:\Codex\Diview\.task-worktrees\asset-dividend-source'
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
# The intended files are locally committed. To publish this task branch, explicitly run:
# git -C $taskRoot push origin HEAD:refs/heads/codex/asset-dividend-source-20261027
```
