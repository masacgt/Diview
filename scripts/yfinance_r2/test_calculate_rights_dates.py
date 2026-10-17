from datetime import date
from unittest import TestCase

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


if __name__ == "__main__":
    import unittest

    unittest.main()
