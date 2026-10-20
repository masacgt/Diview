import json
import sys
from datetime import date
from io import BytesIO
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import Mock, patch

import exchange_calendars as xcals

import calculate_rights_dates as rights_dates


class RightsDateCalculationTests(TestCase):
    @classmethod
    def setUpClass(cls):
        cls.calendars = {
            "XTKS": xcals.get_calendar("XTKS"),
            "XNYS": xcals.get_calendar("XNYS"),
        }

    def test_japanese_next_ex_date_uses_recurring_historical_month(self):
        rows = [
            {"symbol": "7203.T", "date": "2023-03-29", "amount": 35},
            {"symbol": "7203.T", "date": "2024-03-28", "amount": 40},
            {"symbol": "7203.T", "date": "2025-03-28", "amount": 45},
        ]

        estimates = rights_dates.infer_upcoming_events(rows, date(2026, 10, 17), self.calendars)

        self.assertEqual(len(estimates), 1)
        self.assertEqual(estimates[0]["symbol"], "7203.T")
        self.assertEqual(estimates[0]["date"], "2027-03-29")
        self.assertTrue(estimates[0]["inferred_from_history"])

    def test_single_year_events_do_not_create_a_recurring_estimate(self):
        rows = [
            {"symbol": "7203.T", "date": "2025-03-28", "amount": 45},
            {"symbol": "7203.T", "date": "2025-03-31", "amount": 45},
        ]

        self.assertEqual(
            rights_dates.infer_upcoming_events(rows, date(2026, 1, 1), self.calendars),
            [],
        )

    def test_us_last_rights_and_record_dates_skip_thanksgiving_closure(self):
        rows = [
            {"symbol": "A", "date": "2024-11-29", "amount": 0.25},
            {"symbol": "A", "date": "2025-11-28", "amount": 0.25},
        ]

        records, skipped = rights_dates.build_records(rows, self.calendars, date(2026, 1, 1))
        estimate = next(row for row in records if row["estimated_from_history"])

        self.assertEqual(skipped, 0)
        self.assertEqual(estimate["security_code"], "A")
        self.assertEqual(estimate["calendar"], "XNYS")
        self.assertEqual(estimate["ex_dividend_date"], "2026-11-27")
        self.assertEqual(estimate["final_rights_date"], "2026-11-25")
        self.assertEqual(estimate["record_date"], "2026-11-30")
        self.assertEqual(estimate["event_type"], "history_estimated")

    def test_history_estimate_is_distinguished_from_an_observed_event(self):
        rows = [
            {"symbol": "7203.T", "date": "2024-03-28", "amount": 40},
            {"symbol": "7203.T", "date": "2025-03-28", "amount": 45},
        ]

        records, _ = rights_dates.build_records(rows, self.calendars, date(2026, 1, 1))

        estimate = next(row for row in records if row["estimated_from_history"])
        self.assertEqual(estimate["event_type"], "history_estimated")
        self.assertEqual(estimate["source"], "Yahoo Finance historical dividend event dates")
        self.assertEqual(estimate["calendar"], "XTKS")

    def test_history_estimate_keeps_its_provenance_when_calendar_date_matches(self):
        history = [
            {"symbol": "7203.T", "date": "2024-03-28", "amount": 40},
            {"symbol": "7203.T", "date": "2025-03-28", "amount": 45},
        ]
        estimated_date = rights_dates.infer_upcoming_events(
            history, date(2026, 1, 1), self.calendars
        )[0]["date"]
        rows = [{"symbol": "7203.T", "date": estimated_date, "upcoming": True}, *history]

        records, skipped = rights_dates.build_records(rows, self.calendars, date(2026, 1, 1))
        upcoming = [row for row in records if row["ex_dividend_date"] == estimated_date]

        self.assertEqual(skipped, 0)
        self.assertEqual(len(upcoming), 1)
        self.assertTrue(upcoming[0]["estimated_from_history"])
        self.assertEqual(upcoming[0]["event_type"], "history_estimated")
        self.assertEqual(upcoming[0]["source"], "Yahoo Finance historical dividend event dates")

    def test_coverage_audit_classifies_each_active_security_without_an_estimate(self):
        active = [
            {"code": "7203", "symbol": "7203.T"},
            {"code": "1111", "symbol": "1111.T"},
            {"code": "2222", "symbol": "2222.T"},
            {"code": "3333", "symbol": "3333.T"},
            {"code": "4444", "symbol": "4444.T"},
            {"code": "5555", "symbol": "5555.T"},
        ]
        statuses = {
            "7203.T": "available",
            "2222.T": "empty",
            "3333.T": "available",
            "4444.T": "available",
            "5555.T": "failed",
        }
        rows = [
            {"symbol": "7203.T", "date": "2024-03-28", "amount": 40},
            {"symbol": "7203.T", "date": "2025-03-28", "amount": 45},
            {"symbol": "3333.T", "date": "2025-03-10", "amount": 20},
            {"symbol": "3333.T", "date": "2025-03-30", "amount": 20},
            {"symbol": "4444.T", "date": "2024-03-28", "amount": 20},
            {"symbol": "4444.T", "date": "2025-03-28", "amount": 20},
            {"symbol": "5555.T", "date": "2025-03-28", "amount": 20},
        ]
        report = rights_dates.build_coverage_report(
            active,
            statuses,
            rows,
            [{"symbol": "7203.T", "estimated_from_history": True}],
            date(2026, 10, 20),
        )
        by_code = {row["code"]: row["classification"] for row in report["symbols"]}

        self.assertEqual(report["active_universe_count"], 6)
        self.assertEqual(by_code["7203"], "history_estimated")
        self.assertEqual(by_code["1111"], "missing_current_document")
        self.assertEqual(by_code["2222"], "empty_history")
        self.assertEqual(by_code["3333"], "no_month_repeated_in_two_distinct_years")
        self.assertEqual(by_code["4444"], "recurring_history_without_estimate")
        self.assertEqual(by_code["5555"], "history_status_failed")
        self.assertEqual(sum(report["classification_counts"].values()), 6)

    def test_coverage_only_reads_r2_and_never_publishes(self):
        prefix = rights_dates.PREFIX
        objects = {
            f"{prefix}/universe/current.json": {
                "securities": [{"code": "7203", "symbol": "7203.T", "status": "active"}],
            },
            f"{prefix}/securities/7203.T/current.json": {
                "symbol": "7203.T",
                "field_status": {"dividend_history": "available"},
                "calendar": {},
                "statistics": {},
            },
            f"{prefix}/securities/7203.T/dividends/2024.json": {
                "records": [{"date": "2024-03-28", "amount": 40}],
            },
            f"{prefix}/securities/7203.T/dividends/2025.json": {
                "records": [{"date": "2025-03-28", "amount": 45}],
            },
        }
        client = Mock()
        client.get_object.side_effect = lambda *, Bucket, Key: {
            "Body": BytesIO(json.dumps(objects[Key]).encode("utf-8")),
        }
        client.get_paginator.return_value.paginate.return_value = [{
            "Contents": [{"Key": key} for key in objects if "/securities/" in key],
        }]
        input_rows, source_count, coverage_inputs = rights_dates.rows_from_r2(
            client, "diview-market-data", include_coverage=True
        )
        self.assertEqual(source_count, 1)
        self.assertEqual(len(input_rows), 2)
        self.assertEqual(coverage_inputs["history_status_by_symbol"], {"7203.T": "available"})

        with TemporaryDirectory() as temp_dir:
            report_path = f"{temp_dir}/coverage.json"
            with patch.object(rights_dates, "make_client", return_value=client), patch.object(
                sys,
                "argv",
                ["calculate_rights_dates.py", "--coverage-only", "--coverage-report", report_path],
            ):
                self.assertEqual(rights_dates.main(), 0)

            with open(report_path, encoding="utf-8") as report_file:
                report = json.load(report_file)

        self.assertEqual(report["active_universe_count"], 1)
        self.assertEqual(report["classification_counts"], {"history_estimated": 1})
        self.assertEqual(client.put_object.call_count, 0)


if __name__ == "__main__":
    import unittest

    unittest.main()
