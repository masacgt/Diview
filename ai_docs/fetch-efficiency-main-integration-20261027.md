# Fetch-efficiency main integration, 2026-10-27

## Integration and branch audit

Baseline main: `22ee36786052a3cc7cd242d6ed8f67c4a6a4af23`. Source branch: `codex/fetch-efficiency-20261027`, ending at `c65468da603a820f4f3a24fe994a9b824994772d`. The three commits (`e70874e5`, `6f928962`, `c65468da`) fast-forwarded without conflicts in isolated worktree `C:\Codex\Diview\.task-worktrees\fetch-efficiency-main`, branch `codex/integrate-fetch-efficiency-20261027`. Publication target is `origin/main`.

The audit inspected 105 linked worktrees (79 dirty), 99 unmerged local/remote references and 89 distinct unmerged heads. Of those heads, 67 contain incoming `sampleCsv` history and were protected; 12 have patch-equivalent changes already in main; ten needed context review, including this integration head. Historical/dirty branches and all user changes were preserved; no branches were deleted and no CSV/APK/configuration/build artifacts were imported.

| Unmerged remote branch at audit time | Decision |
| --- | --- |
| `codex/fetch-efficiency-20261027` | Integrate the three verified new commits. |
| `codex/financial-revision-concurrent-writes-20261026` | `git cherry` reports the revision fix already represented in main. |
| `codex/irbank-rollout-evidence-20261025` | Patch-equivalent documentation already represented in main. |
| `codex/6721-production-evidence-20261025` | Patch-equivalent changes already represented in main. |
| `codex/integrate-selected-branches-20261025` | Remaining apparent additions are older rollout/6721 documentation; corresponding current-main records are `eb7ba4b4` and `e6cc8462`. Retain the historical branch. |
| `codex/yfinance-financial-coverage-20261018` | Dirty shared checkout with older overlapping feature history. Current main contains replacement metric, comment, forecast and yfinance changes; do not restore its older tree. |

Other old local candidates were also retained. Matching current-main subjects include `49cb40fb` (daily change), `a934a4a4` (health explanations), `b5349286` (event preload), `7c339180` (categories), and `ec1285ea` (metric fallbacks). The three J-Quants tool files at `001de56a` match current main exactly; current main has the newer dividend-audit and financial-unit contracts. Old commit IDs alone are not a reason to reapply superseded implementations or dirty files.

Raw branch/worktree audit details and the old-patch review are ignored local evidence under `C:\Codex\Diview\local_artifacts\branch-integration-20261027/`; sensitive sample paths and dirty status dumps are not committed.

## Validation of the integrated checkout

- Explicit JDK 17, serialized Gradle, correct `kurogane-dividend/android/` root: all 220 unit tests and app/test APK assemblies succeeded. Worker tests: 67/67.
- Installed only the fresh APK built in this integration worktree on SCG01 `RFCNC0E4H8J` and `emulator-5560`. Both installed `base.apk` hashes match the integration APK: `e346179408aa8e8828f49edcc62e58fb0678d0840be366b1c8493380e76cb847`. Both normal launches succeeded.
- All 591 uncompressed APK entries match the previous verified APK by SHA-256; package/container hashes differ, not payload content. No app source or resource changed during integration beyond the already validated branch diff.
- Emulator: all 24 acquisition/notification/visual regressions passed. Physical SCG01: the 23 acquisition/notification cases passed initially; visual capture first failed to find Compose, then passed after explicit wake/start; the live case passed after the production publication reconciliation below. All 25 cases are accounted for without weakening assertions.
- All 33 images per device are pixel-identical to the original baselines: 66/66.

## Concurrent production publication reconciliation

During integration verification another publication replaced version 69 with version **70**, `6865bfe1-86c2-46d0-8426-65b3a80425be` (2026-10-27 22:04 JST). Its security response ignored `include_history_bounds=false`, so the live check correctly failed (`empty` instead of `not_requested`). This was an external deployment change, not an Android merge regression.

Version 70 also contained updated financial-period handling and the security-category endpoint. Its current source and bindings were read back. Only this task's request-local small-document sharing and optional history-bounds omission were added to that newer source; no version-70 fixes were rolled back.

- Candidate comparisons: 108 mock responses match version 70; three preview security/financial/analysis responses match the new production baseline. The existing category endpoint matches in full (4,452 entries) and a three-code batch.
- Serving version: **71**, `234d8184-ecb2-4720-a08b-0284de248270`, 100% traffic; version-70 bindings were preserved. This is the continuation of the user's earlier explicit Worker-publication approval.
- Post-publication: all 27 production API responses match the immediate version-70 baseline, excluding observation time/elapsed age; three history-opt-out checks preserve every other field. Physical `MarketFetchEfficiencyLiveTest` passes unchanged against the restored endpoint.
- Sustained CPU-limit behavior remains the existing monitoring follow-up. The fix does not introduce a new freshness TTL or change price/DPS/yield/notification selection rules.

Fresh build, test, APK-content/hash, PNG-comparison, version and production-response evidence is ignored under this worktree's `local_artifacts/fetch-efficiency-main/`. This Git integration does not publish unrelated main Worker features: production was updated by the bounded delta on version 70.
