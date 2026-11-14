from __future__ import annotations

import io
import json
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

from scripts import d1_daily_usage as usage


ACCOUNT_ID = "8d8f4f1f3c53232aaf7c7d6dd7b33944"
DB_A = "11111111-1111-4111-8111-111111111111"
DB_B = "22222222-2222-4222-8222-222222222222"
UTC_DATE = "2026-11-04"


def group(database_id: str, rows_read: int, rows_written: int = 0) -> dict:
    return {
        "dimensions": {"date": UTC_DATE, "databaseId": database_id},
        "sum": {"rowsRead": rows_read, "rowsWritten": rows_written},
    }


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()


class DailyUsageTests(unittest.TestCase):
    def test_report_sums_every_database_and_reports_remaining_daily_allowance(self):
        report = usage.build_usage_report(
            ACCOUNT_ID,
            UTC_DATE,
            [group(DB_A, 2_100_000, 7), group(DB_B, 3_000_001, 9)],
        )
        self.assertEqual(report["rows_read"], 5_100_001)
        self.assertEqual(report["rows_written"], 16)
        self.assertEqual(report["rows_remaining"], 0)
        self.assertTrue(report["limit_exceeded"])
        self.assertEqual(report["databases_with_usage"], 2)
        self.assertEqual([row["database_id"] for row in report["per_database"]], [DB_A, DB_B])

    def test_report_does_not_mark_exact_limit_as_exceeded(self):
        report = usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [group(DB_A, 5_000_000)])
        self.assertEqual(report["rows_remaining"], 0)
        self.assertFalse(report["limit_exceeded"])

    def test_empty_groups_report_zero_usage(self):
        report = usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [])
        self.assertEqual(report["rows_read"], 0)
        self.assertEqual(report["databases_with_usage"], 0)

    def test_rejects_invalid_or_out_of_day_groups(self):
        with self.assertRaises(usage.UsageQueryError):
            usage.build_usage_report("bad", UTC_DATE, [])
        with self.assertRaises(usage.UsageQueryError):
            usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [group(DB_A, -1)])
        wrong_day = group(DB_A, 1)
        wrong_day["dimensions"]["date"] = "2026-11-03"
        with self.assertRaises(usage.UsageQueryError):
            usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [wrong_day])

    def test_refuses_potentially_truncated_group_result(self):
        with self.assertRaises(usage.UsageQueryError):
            usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [{}] * usage.MAX_GROUPS)

    def test_query_uses_account_scoped_analytics_and_never_returns_token(self):
        token = "secret-test-token"
        response_body = {
            "data": {
                "viewer": {
                    "accounts": [{"d1AnalyticsAdaptiveGroups": [group(DB_A, 123)]}],
                },
            },
        }
        captured = {}

        def fake_open(request, timeout):
            captured["request"] = request
            captured["timeout"] = timeout
            return FakeResponse(json.dumps(response_body).encode("utf-8"))

        report = usage.fetch_daily_usage(ACCOUNT_ID, token, UTC_DATE, opener=fake_open)
        request = captured["request"]
        payload = json.loads(request.data)
        self.assertEqual(request.full_url, usage.GRAPHQL_URL)
        self.assertEqual(request.get_header("Authorization"), f"Bearer {token}")
        self.assertEqual(captured["timeout"], 30)
        self.assertEqual(payload["variables"], {"accountTag": ACCOUNT_ID, "start": UTC_DATE, "end": UTC_DATE})
        self.assertIn("d1AnalyticsAdaptiveGroups", payload["query"])
        self.assertNotIn("SELECT", payload["query"].upper())
        self.assertNotIn(token, json.dumps(report))
        self.assertEqual(report["rows_read"], 123)

    def test_graphql_errors_are_sanitized(self):
        def fake_open(request, timeout):
            return FakeResponse(json.dumps({"errors": [{"message": "private detail"}]}).encode("utf-8"))

        with self.assertRaisesRegex(usage.UsageQueryError, "analytics query error"):
            usage.fetch_daily_usage(ACCOUNT_ID, "secret", UTC_DATE, opener=fake_open)

    def test_missing_token_fails_before_network(self):
        called = False

        def fake_open(request, timeout):
            nonlocal called
            called = True
            raise AssertionError("network must not be called")

        with self.assertRaisesRegex(usage.UsageQueryError, "CLOUDFLARE_API_TOKEN"):
            usage.fetch_daily_usage(ACCOUNT_ID, "", UTC_DATE, opener=fake_open)
        self.assertFalse(called)

    def test_budget_gate_requires_reserve_and_rejects_exceeded_usage(self):
        self.assertTrue(usage.has_sufficient_remaining_budget({"rows_remaining": 1000, "limit_exceeded": False}, 1000))
        self.assertFalse(usage.has_sufficient_remaining_budget({"rows_remaining": 999, "limit_exceeded": False}, 1000))
        self.assertFalse(usage.has_sufficient_remaining_budget({"rows_remaining": 0, "limit_exceeded": True}, 0))
        with self.assertRaises(usage.UsageQueryError):
            usage.has_sufficient_remaining_budget({"rows_remaining": 100, "limit_exceeded": False}, True)

    def test_cli_exits_nonzero_when_remaining_budget_is_below_reserve(self):
        report = usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [group(DB_A, 4_999_500)])
        stdout = io.StringIO()
        stderr = io.StringIO()
        with patch.dict(
            "os.environ",
            {"CLOUDFLARE_ACCOUNT_ID": ACCOUNT_ID, "CLOUDFLARE_API_TOKEN": "test-token"},
        ), patch.object(
            usage, "fetch_daily_usage", return_value=report
        ), redirect_stdout(stdout), redirect_stderr(stderr):
            exit_code = usage.main(["--minimum-rows-remaining", "1000"])
        self.assertEqual(exit_code, 2)
        self.assertIn('"budget_sufficient": false', stdout.getvalue())
        self.assertIn("below the required reserve", stderr.getvalue())
        self.assertNotIn("test-token", stdout.getvalue() + stderr.getvalue())

    def test_cli_succeeds_when_remaining_budget_meets_reserve(self):
        report = usage.build_usage_report(ACCOUNT_ID, UTC_DATE, [group(DB_A, 4_999_000)])
        stdout = io.StringIO()
        with patch.dict(
            "os.environ",
            {"CLOUDFLARE_ACCOUNT_ID": ACCOUNT_ID, "CLOUDFLARE_API_TOKEN": "test-token"},
        ), patch.object(
            usage, "fetch_daily_usage", return_value=report
        ), redirect_stdout(stdout), redirect_stderr(io.StringIO()):
            exit_code = usage.main(["--minimum-rows-remaining", "1000"])
        self.assertEqual(exit_code, 0)
        self.assertIn('"budget_sufficient": true', stdout.getvalue())

    def test_migration_workflow_checks_budget_before_any_d1_or_r2_migration_phase(self):
        repository_root = Path(__file__).resolve().parents[4]
        workflow = (repository_root / ".github" / "workflows" / "d1-r2-migration.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("permissions:\n  contents: read\n  actions: read", workflow)
        self.assertIn('main_branch = get("branches/main")', workflow)
        self.assertIn('main_branch.get("protected") is not True', workflow)
        gate = workflow.index("Check account-wide UTC D1 allowance before any D1 query")
        d1_phase = workflow.index("Run D1-only inventory audit")
        r2_phase = workflow.index("Run R2-backed migration or audit")

        self.assertLess(gate, d1_phase)
        self.assertLess(gate, r2_phase)
        self.assertIn("D1_R2_MIGRATION_ANALYTICS_READ_TOKEN", workflow)
        self.assertIn("read_reserve_rows", workflow)
        self.assertIn("--minimum-rows-remaining", workflow)
        self.assertEqual(workflow.count("CLOUDFLARE_API_TOKEN: ${{ secrets.D1_R2_MIGRATION_ANALYTICS_READ_TOKEN }}"), 3)
        self.assertEqual(workflow.count('--read-reserve-rows "$READ_RESERVE_ROWS"'), 2)


if __name__ == "__main__":
    unittest.main()
