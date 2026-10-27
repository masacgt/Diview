# Physical-device fetch verification, 2026-10-27

Device: SCG01 `RFCNC0E4H8J`. Branch: `codex/fetch-efficiency-20261027`, initially at pushed HEAD `6f928962`. The app source is unchanged from `e70874e5`; this follow-up adds an opt-in live instrumentation test and narrows Worker read sharing to avoid copying large, single-use documents.

## Installation and device checks

- Explicit JDK 17 (`C:\Users\htdn\.jdks\ms-17.0.20.1`), serialized Gradle build from this worktree's `kurogane-dividend/android/`: `testDebugUnitTest`/`assembleDebug` succeeded; all 220 unit results remain successful. Test APK assembly also succeeded.
- Installed the APK produced in this worktree with `adb -s RFCNC0E4H8J install -r`; user app data was retained. Pulled installed `base.apk` and matched SHA-256 `7ea7530f7fd4a56c80f1b0a5ee6c37476aa0c9c97d9e6bf58cbb90173d4296e6`.
- Re-ran all 24 `ReleaseVisualRegressionTest`, `AssetDataRefreshTest`, and `NotificationDeliveryTest` cases on SCG01: success. This covers acquisition/update throttling, empty success, failure retry, monthly batching, forecast/no-dividend display, and notification/reset/cancellation behavior using isolated test storage.
- All 33 newly captured synthetic-screen PNGs are pixel-identical to the original device baseline (nine screens, light/dark/OLED, large fonts).
- Normal app launch succeeded. Opened existing 2914 from Asset Management, verified its quote/dividend/analysis sections render, and returned to Asset Management. No holding or setting was edited. Crash-buffer read-back was empty.

## Live production verification and CPU follow-up

`MarketFetchEfficiencyLiveTest` requires explicit `allowLiveMarketReads=true` and the production base URL. It uses public 285A data directly through default repositories, without accessing portfolio storage or posting notifications. The initial test signature omitted an explicit Unit return and was corrected before live checks.

The first live check on version 68 exposed an HTTP 503 on the fresh financial request after the shared batch. Worker tail recorded `outcome=exceededCpu` / `Worker exceeded CPU time limit`. The batch itself had used one successful HTTP request for four default Repository instances.

Inspection identified avoidable work: the generic request cache cloned even large issuer indexes and whole history arrays that were read only once. The fix shares/clones only the repeatedly read small security `profile.json` and `current.json`; other JSON and document lists use the existing independent load path. A 4,451-item-index regression prevents copying/retaining that full index. This removes unnecessary CPU work; one transient failure alone does not establish the entire CPU-limit root cause or a universal latency improvement.

- Worker tests: **67/67**. Main/source and deployed-base mock comparisons: **108/108** responses identical in each comparison, excluding observation time/elapsed age. The profile/current R2 read reduction remains intact.
- Version 69 preview: financial and analysis 285A calls each succeeded 4/4 with unchanged response content. Version 68 preview also succeeded 8/8, so its earlier CPU failure was intermittent.
- Published version **69**, `d9ab22b6-6165-4a7a-ba4a-7b69c9895cd9`, after binding and current-version checks. Deployment `aa1d6b7e-760d-4660-b00c-fa31d85ba3a1`, 2026-10-27 21:44:47 JST. Read-back confirms 100% traffic. This reused the previously approved Worker-publication scope and preserved other production behavior/settings.
- Re-ran the live device test against version 69: **1/1 passed without internal retries**, making **25 successful device checks** in this follow-up. Four financial results were equal and independently mutable; the later financial call remained a new request. Four analysis results contained the same seven values. The security response's stored-history status was `not_requested`.
- Production tail for that successful device test confirms exactly **5 HTTP 200 / outcome=ok** events: financial-data/285A **2** (one shared four-caller batch plus a fresh request), search-catalog **1**, securities/285A?include_history_bounds=false **1**, analysis/285A **1** (four callers). Thus the explicit catalog fetch also satisfied detail fallback without downloading the catalog again.
- Repeated the production comparisons after the fix: **27/27** security/financial/analysis responses and **3/3** history-opt-out comparisons succeeded, with content unchanged apart from the previously documented observation-time and hidden-bounds fields.

All raw logs, UI dumps, images, APKs, sanitized HTTP-count proof, and API response comparisons are in ignored `local_artifacts/fetch-efficiency/`. Relevant files: `device-recheck-apk-proof.json`, `device-recheck-regression.log`, `device-recheck-visual-comparison.json`, `device-live-api-fixed.log`, `device-live-request-proof.json`, `worker-cpu-fix-tests.log`, `preview-cpu-probe.json`, `publication-after-comparison.json`, and `worker-cpu-fix-after-deploy.json`. Raw tail request headers are local evidence only and must not be committed.

## Re-run live acceptance

```powershell
$env:JAVA_HOME = 'C:\Users\htdn\.jdks\ms-17.0.20.1'
$env:PATH = "$env:JAVA_HOME\bin;C:\Android\sdk\platform-tools;$env:PATH"
adb -s RFCNC0E4H8J shell am instrument -w -r -e class com.kurogane.dividend.MarketFetchEfficiencyLiveTest -e allowLiveMarketReads true com.kurogane.dividend.test/com.kurogane.dividend.ReleaseFixtureInstrumentation
```

Sustained-load CPU behavior remains a monitoring follow-up; these sampled successes do not prove that no other production request can hit Cloudflare's CPU limit.
