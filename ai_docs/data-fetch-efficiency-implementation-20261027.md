# Display-preserving fetch efficiency, 2026-10-27

## Baseline and scope

Source: `origin/main` at `22ee36786052a3cc7cd242d6ed8f67c4a6a4af23`, isolated branch `codex/fetch-efficiency-20261027`. The original investigation thread is `01a17d16-4e88-7a53-94b7-baace24b9d0f`. The shared checkout, its dirty files, account CSVs, and other worktrees were preserved.

The user's additional acceptance condition is unchanged display content. Therefore this change reduces duplicate work while retaining the current price/DPS/yield source priorities, forecast resolution, rounding, notification thresholds, successful daily/12-hour limits, 30-second retry handling, and existing unknown/no-dividend semantics. It does not merge differently defined financial or dividend series.

## Changes and checks by stage

1. Worker reads share promises within one HTTP request or one scheduled-security analysis. JSON documents and list results are cloned for each consumer, so in-place sorting/enrichment cannot affect another consumer. Missing-object lookups are shared only in that context; failed reads are evicted and remain retryable. A later HTTP request always gets a new context.
2. Android's default Repository instances share overlapping HTTP reads using the complete URL and HTTP-cache-bypass mode. Each caller parses its own JSONObject from the shared immutable body. Completed requests and failures are removed immediately: morning/evening refreshes and explicit retries are not suppressed by another new TTL. Injected transports remain isolated.
3. An explicit full-catalog fetch also populates the existing five-minute search/detail fallback cache. The two existing parsing projections remain distinct, including their current TTM/unknown filtering. Explicit full-catalog refreshes still perform HTTP. Empty responses clear the old fallback snapshot.
4. `securities/{code}?include_history_bounds=false` omits only the unrendered stored-dividend-history bounds scan. Default requests retain their old response. Company-information metadata remains available, so the existing cache/migration gate is preserved. Current ViewModel security requests opt out; dividend events, amounts, prices, dates, forecasts, and all other metadata are unchanged. Older Workers may ignore the parameter safely.
5. Monthly-price batches exclude months that no participating code needs to refresh. Existing cached months are still returned, and correction/split revalidation keeps its existing daily policy. Success/failure markers are updated only for due code/month pairs.

The latest main had already removed speculative startup catalog/analysis/year-end-price/split prefetch. That work was retained. No screen/resource/layout or notification policy file was changed.

## Evidence

- Worker baseline: 58/58 tests; final: 66/66 tests. Added checks cover concurrent reads, mutable-object isolation, context boundaries, missing/error retry, 4/5-character codes, live updates between requests, and history-scan opt-out.
- Differential mock execution: 108 baseline-main/candidate responses matched, excluding only `observed_at` and continuously changing `market_age_days`. The same 108 comparisons matched separately against the actual deployed version 67 and its narrowly patched candidate. Status codes and headers were compared too. Main-fixture analysis R2 GETs fell 11 to 7; deployed-version-fixture analysis GETs fell 10 to 7. These are fixture counts, not measured production savings.
- History opt-out regression: every security field matches except `stored_history_status/start/end`; current/archive LISTs fall 2 to 0. These hidden fields become `not_requested/null/null` only when explicitly opted out.
- JDK 17 (`C:\Users\htdn\.jdks\ms-17.0.20.1`): baseline Android unit tests 216/216; final 220/220; `assembleDebug` and `assembleDebugAndroidTest` succeeded.
- Physical SCG01 `RFCNC0E4H8J` and `emulator-5560`: each passed 24 instrumentation tests covering isolated portfolio acquisition, daily limits, retry, empty success, forecast/no-dividend display, monthly batching, notification suppression/cancellation/reset, and visual capture.
- Each device's 33 screenshots (nine screens in light/dark/OLED plus large-font samples) are pixel-identical to its own baseline: 66/66 images. These use synthetic, isolated data, not the user's portfolio.
- Final APK and both installed `base.apk` files share SHA-256 `7ea7530f7fd4a56c80f1b0a5ee6c37476aa0c9c97d9e6bf58cbb90173d4296e6`. Both launch successfully.
- Local logs, PNGs, APKs, mock comparison JSON, and production read-backs are under ignored `local_artifacts/fetch-efficiency/` in this worktree; none belongs in Git.

## Production publication boundary

Production baseline: version 67, `4d0c0767-e004-4b90-831a-f9236b1afa37`, message `Financial statement unit contract v6`. Current main contains unrelated Worker changes, including the unpublished category endpoint; deploying main wholesale would exceed this change's display-preservation scope.

The existing production module was downloaded read-only. A guarded local transformation applies only request-local R2 sharing and history-bounds opt-out, using the same `market-read-cache.mjs` as source. The old module remains available for rollback. All 108 mock comparisons and Wrangler version-upload dry-run passed. All 27 baseline production API reads (9 securities x security/financial/analysis) returned HTTP 200.

Publication completed after the user explicitly approved both upload and traffic switch. The initial automatic-review rejection was resolved by that approval. The deployed version was rechecked before upload and immediately before switching; it was still the expected version 67. Uploaded version 68's bindings and compatibility date matched version 67; its preview security/financial/analysis responses matched the production baseline in three comparisons.

- Serving version: **68**, `1a0c7851-3301-4b10-ab98-4d6933489b38`, 100% traffic.
- Deployment: `5b8a7e1d-b967-4dcf-98e4-a71143454aa1`, 2026-10-27 21:23:19 JST. Deployment-list read-back confirms the version and percentage.
- All 27 pre/post-production responses (7203, 8316, 8984, 1326, 6721, 25935, 285A, 8058, 8953; security, financial, analysis) returned HTTP 200 and matched, excluding only `observed_at` and continuously changing `market_age_days`.
- Three further production comparisons (7203, 8984, 25935) confirm the history opt-out returns `not_requested/null/null` for the three hidden bounds fields, with all other response content identical to the full endpoint.
- Prepared production entry module SHA-256: `84905413ecb46ec920f813d7143a09ccf696e4779e7309bdf6aa03eaa1f2d9bf`. The original entry module is retained locally with SHA-256 `d17167253c576c4be0db870ebcf70b9d0b2269e7f289c746549059b1800fa2b3`.
- Rollback target remains version 67; no database/R2 data migration, trigger change, or unrelated main Worker feature was published. Publication logs and exact comparison results are in `worker-upload.log`, `worker-deploy.log`, `worker-deployments-after-publish.json`, `publication-preview-comparison.json`, and `publication-after-comparison.json` under the ignored evidence directory.

The code commit is `e70874e5` on `codex/fetch-efficiency-20261027`. GitHub push/main integration was not included in the user's specific Worker-publication approval and has not been performed.

## Remaining architectural opportunities

The original audit's differing price sources, notification yield definition, persistent cross-process caching, combined financial/analysis API, historical-partition revision protocol, and fallback-route consolidation are not claimed as resolved. They require separate source/freshness/compatibility decisions; changing those contracts in this performance patch could change displayed values or notification behavior. This patch retains their current behavior and proves the bounded optimizations above. Device PNG equality does not establish unchanged live financial values for every security or a measured end-to-end latency improvement.

## Reproduction

```powershell
Set-Location C:\Codex\Diview\.task-worktrees\fetch-efficiency
node --test data/irbank/cloudflare/test/*.test.mjs
$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:ANDROID_HOME = 'C:\Android\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;C:\Android\sdk\platform-tools;$env:PATH"
Set-Location kurogane-dividend/android
git status --short --branch
git rev-parse HEAD
.\gradlew.bat --no-daemon --console plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
$apk = (Resolve-Path app/build/outputs/apk/debug/app-debug.apk).Path
adb -s RFCNC0E4H8J install -r $apk
adb -s emulator-5560 install -r $apk
```
