# Branch integration — 2026-10-26

## Included

- Remote main at review: `bc4fd313d748a34967fd8e4bece5a53b2d064793`.
- Local main already contains `7c339180`, the REIT/ETF display supplement and per-code category cache. `git range-diff fc5b0e45^..fc5b0e45 7c339180^..7c339180` confirms the original topic patch is equivalent. Use local main as the integration base instead of importing the topic's older parent history.
- Merge `codex/stock-analysis-history-20261026` (`ca5068e7`) into that base. This adds same-response annual history and contextual comments without repeating row labels. Preserve remote main's title `配当指標の解説` and subtitle `7指標で見る配当継続の強み・弱み`.
- Resolve the sole conflict in `ai_docs/todo.md` by retaining both feature records and the pending production-category API publication item.

## Reviewed and retained

- No open pull requests were returned by the GitHub connector at review.
- `origin/codex/financial-revision-concurrent-writes-20261026`: `git cherry` marks its revision-write patch equivalent to current main; no duplicate merge.
- `origin/codex/6721-production-evidence-20261025`, `origin/codex/irbank-rollout-evidence-20261025`, and `origin/codex/integrate-selected-branches-20261025`: production-unit and 6721 evidence is already recorded on main. Their older ancestors include the excluded personal/account CSV history, so do not merge that history or restore old todo states.
- `origin/codex/yfinance-financial-coverage-20261018` and older local integration/analysis branches: main already contains newer financial unit guards, forecast/current-value fallbacks, event preloading, legacy-audit tooling, and current comment generation. Their older implementations would remove newer safeguards or reinstate superseded comments. Keep those branches and dirty worktrees intact.
- The primary checkout and other dirty worktrees, including staged CSV/deletion states in old temporary worktrees, are excluded from this integration. No existing branch or worktree is deleted.

## Verification and publication

- Worker suite: 70/70 tests passed, including security categories and existing routes, SQL, feedback and scheduled analysis.
- JDK 17 Android build, debug APK and instrumentation APK assembly succeeded; all 191 unit tests and five isolated category/list UI tests passed.
- Installed the integration APK on physical device RFCNC0E4H8J and emulator-5560. Both installed base.apk hashes match the built APK SHA-256 `171b58f1e8a7985d790e8131ad8065a3a90fe9bb36f9d03847bf844057dee599`. Verified the payout narrative omits repeated numeric/rating labels and includes annual trend context; no fresh AndroidRuntime fatal entries.
- Re-fetch and require current remote-main ancestry before publishing without force. Main publication is recorded by the final Git state, not implied by the build.
- This is a code integration. The new production `/api/v1/security-categories` endpoint still requires Worker publication; production API-to-Android category communication remains a tracked follow-up.
