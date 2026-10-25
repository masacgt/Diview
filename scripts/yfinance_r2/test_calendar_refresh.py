import unittest
from datetime import date, timedelta

import sync_v2 as sync


class DividendCalendarRefreshPolicyTests(unittest.TestCase):
    def setUp(self):
        self.as_of = date(2026, 10, 26)

    def should_refresh(self, **overrides):
        options = {
            "as_of": self.as_of,
            "fetched_on": self.as_of - timedelta(days=29),
            "ex_dividend_date": None,
            "last_attempt_session_date": self.as_of - timedelta(days=29),
            "last_attempt_status": "empty",
            "status": "empty",
        }
        options.update(overrides)
        return sync.should_refresh_dividend_calendar(**options)

    def test_successful_empty_calendar_uses_30_day_interval(self):
        self.assertFalse(self.should_refresh())
        self.assertTrue(self.should_refresh(fetched_on=self.as_of - timedelta(days=30)))

    def test_upcoming_ex_date_uses_seven_day_interval_within_45_days(self):
        ex_date = self.as_of + timedelta(days=20)
        self.assertFalse(self.should_refresh(
            fetched_on=self.as_of - timedelta(days=6), ex_dividend_date=ex_date,
        ))
        self.assertTrue(self.should_refresh(
            fetched_on=self.as_of - timedelta(days=7), ex_dividend_date=ex_date,
        ))

    def test_ex_date_beyond_45_days_keeps_30_day_interval(self):
        ex_date = self.as_of + timedelta(days=46)
        self.assertFalse(self.should_refresh(
            fetched_on=self.as_of - timedelta(days=29), ex_dividend_date=ex_date,
        ))
        self.assertTrue(self.should_refresh(
            fetched_on=self.as_of - timedelta(days=30), ex_dividend_date=ex_date,
        ))

    def test_failed_calendar_is_not_retried_again_in_same_session(self):
        self.assertFalse(self.should_refresh(
            fetched_on=self.as_of - timedelta(days=60),
            last_attempt_session_date=self.as_of,
            last_attempt_status="failed",
            status="stale",
        ))

    def test_failed_calendar_retries_on_next_session(self):
        previous_session = self.as_of - timedelta(days=1)
        self.assertTrue(self.should_refresh(
            fetched_on=self.as_of - timedelta(days=2),
            last_attempt_session_date=previous_session,
            last_attempt_status="failed",
            status="stale",
        ))


if __name__ == "__main__":
    unittest.main()
