# Follow-up consistency fixes

Baseline: `f9aadfc9b50f51cb5bf00b8c2a233212f8343767` on `codex/yfinance-financial-coverage-20261018`.
The 15 pre-existing modified files and untracked attachment directory are retained.

## Changes

- Worker preserves finalized DOE/net D/E/equity calculations over retained seed values.
- Analysis converts fractional payout ratio to percentage points and uses the quote date/status for freshness.
- Analysis source identity includes contract version 2 to refresh stored diagnostics after rollout.
- D1 imports require a newly generated version-2 manifest and verified SQL hashes. Resume state is content-based.
- A guarded repair planner compares the D1 export with the original CSV, restores missing periods before removing each matching corrupt row, and verifies offline replay. It does not overwrite valid rows.
- Android preserves per-security DPS over lagging catalog values, revalidates historical prices daily, and expires/forces analysis retrieval correctly.
- Financial cache contract version 3 refreshes old payloads. Missing DOE/net D/E can be explicitly retried.
- Advice generation selects latest financial periods from object contents across both key formats. Financial refresh updates stored advice metrics and rebuilds the published candidate catalog; sharded refresh has one final publication job.
- Issuer dividends preserve fiscal period precision through organization, migration, Worker and Android. Additive D1 table `issuer_dividend_periods` preserves full periods; `dividend_events` remains a legacy yearly projection. The yearly Android chart withholds ambiguous same-year values and explains that limitation instead of choosing one or summing them.

## Local validation

- Python collector/refresh suite: 91 passed in the original working tree, 90 in its published subset, and 95 after integration with latest main.
- Worker/D1 suite: 50 passed initially; 53 passed after retaining main's class-share and share-basis protections.
- Guarded repair planner regression: 1 passed.
- JDK 17 Android: 154 JVM tests passed initially; integrated production source passed 155 JVM tests and a fresh debug APK build.
- Actual production export plus additive migrations compiled 59 Worker SQL statements offline, preserving existing operational row counts.
- Actual backup contains 17,522 corrupt annual rows. All surviving payloads matched the source CSV. Guarded offline repair yields 49,592 annual rows, zero period mismatches, and passes replay verification. This is the verified affected-group subset, not an unconditional reload of all 61,204 source CSV rows.

## Rollout status

Production backup and the post-repair export are stored only under ignored `.build/`.

- Applied additive migrations 0003–0005 to production. Existing canonical issuer-dividend table contains zero records because the legacy D1 dividend table was empty; the current issuer history is served from R2.
- Applied all 36 guarded repair chunks. The live export exactly matches the offline expected `financial_periods` table: 49,592 annual rows and zero JSON/key period mismatches. Valid rows were not overwritten. The other 11,612 source CSV rows were outside the identified corrupt groups and were not blindly imported.
- The first repair attempt stopped before writes on a checksum mismatch caused by Windows newline translation. SQL now writes explicit UTF-8 bytes and the regression checks the actual file hashes.
- Integrated onto `origin/main` in an isolated worktree, preserving newer class-share, 8303 share-basis, typography, and collection changes. Resolved the annual valuation list/dictionary integration boundary; all final suites passed.
- Production Worker version `e95ca56b-88d1-4b98-ab4d-daa74120fc8a` serves 100% traffic, deployed from `a021f937`. Existing triggers were retained.
- Production 4502 API returns 2026-03-31 DOE 4.1981758789120205, net D/E 0.5769995346890836, and equity ratio 47.89632289733827; both requested metrics are available. Analysis returns payout ratio 280.18 percentage points and quote-date freshness. 8303 retains the unverified share-basis status; 94345 exact-code financial API returns HTTP 200.
- Installed the integrated debug APK on SCG01 `RFCNC0E4H8J` and `emulator-5554`. Both retained a running process and resumed MainActivity after launch; no new matching crash-buffer entries were found. This is startup validation, not visual acceptance of every cache or chart scenario.
- R2 targeted run #187 verified all 10 financial periods for 4502. Full derived-catalog regeneration is pending final read-back. Since that regeneration scans all 4,356 eligible securities, its workflow budget is now 90 minutes with 32 R2 readers; Yahoo request limits remain unchanged.
