from datetime import date
from unittest import TestCase
from unittest.mock import patch

import pandas as pd

import sync_v2 as sync


class SyncPriceTests(TestCase):
    def test_today_close_requires_exact_session_date(self):
        rows = [
            {"date": "2026-10-14", "close": 100.0},
            {"date": "2026-10-15", "close": 101.5},
        ]
        selected = sync.completed_close_for_session(rows, date(2026, 10, 15))
        self.assertEqual(selected["close"], 101.5)

    def test_stale_close_is_not_published_as_today(self):
        with self.assertRaisesRegex(RuntimeError, "no closing price for Japan session"):
            sync.completed_close_for_session(
                [{"date": "2026-10-14", "close": 100.0}], date(2026, 10, 15)
            )

    def test_new_split_requests_full_history_refresh_once(self):
        self.assertTrue(sync.has_new_split(
            [{"date": "2025-04-01", "ratio": 2.0}], []
        ))
        self.assertFalse(sync.has_new_split(
            [{"date": "2026-10-14", "ratio": 2.0}],
            [{"date": "2026-10-14", "ratio": 2.0}],
        ))
        self.assertTrue(sync.has_new_split(
            [{"date": "2026-10-14", "ratio": 3.0}],
            [{"date": "2026-10-14", "ratio": 2.0}],
        ))

    def test_new_split_forces_eight_year_price_refresh(self):
        self.assertTrue(sync.year_end_price_refresh_required(
            previous_monthly=True,
            refresh_monthly=False,
            initialize_monthly=False,
            retry_monthly=False,
            new_split_detected=True,
        ))

    def test_initial_prices_keep_only_december_closes_for_eight_years(self):
        index = pd.to_datetime([
            f"{year}-{month:02d}-01" for year in range(2019, 2027) for month in (6, 12)
        ]).tz_localize("Asia/Tokyo")
        frame = pd.DataFrame({"Close": [float(value) for value in range(1, len(index) + 1)]}, index=index)

        class Ticker:
            def history(self, **kwargs):
                self.kwargs = kwargs
                return frame

        ticker = Ticker()
        with patch.object(sync, "year_end_price_years", return_value=(2019, 2026)), \
             patch.object(sync, "last_xtks_session_in_december", side_effect=lambda year: date(year, 12, 30)), \
             patch.object(sync, "count_yfinance_call"):
            result = sync.monthly_history_rows(ticker, initial=True, as_of=date(2026, 10, 15))

        self.assertEqual(ticker.kwargs["interval"], "1mo")
        self.assertEqual(ticker.kwargs["auto_adjust"], False)
        self.assertEqual(len(result), 8)
        self.assertTrue(all(item["month"].endswith("-12") for item in result))
        self.assertEqual([item["month"] for item in result], [f"{year}-12" for year in range(2019, 2027)])


if __name__ == "__main__":
    import unittest

    unittest.main()
