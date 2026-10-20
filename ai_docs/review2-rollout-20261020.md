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

- Python collector/refresh suite: 91 passed.
- Worker/D1 suite: 50 passed.
- Guarded repair planner regression: 1 passed.
- JDK 17 Android: 154 JVM tests passed; debug APK build succeeded.
- Actual production export plus additive migrations compiled 59 Worker SQL statements offline, preserving existing operational row counts.
- Actual backup contains 17,522 corrupt annual rows. All surviving payloads matched the source CSV. Guarded offline repair yields 49,592 annual rows, zero period mismatches, and passes replay verification. This is the verified affected-group subset, not an unconditional reload of all 61,204 source CSV rows.

## Rollout status

Production backup is stored only under ignored `.build/`. Production deployment, repair execution, and API read-back are pending in this snapshot of the note.
