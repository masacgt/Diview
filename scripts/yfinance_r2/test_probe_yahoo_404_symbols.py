from datetime import datetime
import json
import tempfile
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

import pandas as pd

import probe_yahoo_404_symbols as probe


class Yahoo404ProbeTests(TestCase):
    def test_probe_records_most_recent_close_and_market_session_status(self):
        history = pd.DataFrame(
            {"Close": [12.5]}, index=[pd.Timestamp("2026-11-02", tz="Asia/Tokyo")],
        )
        ticker = type("Ticker", (), {"history": lambda self, **kwargs: history})()
        with patch.object(probe.yf, "Ticker", return_value=ticker):
            result = probe.probe_symbol("7203", "2026-11-02")
        self.assertEqual(result["status"], "session_close")
        self.assertEqual(result["symbol"], "7203.T")
        self.assertEqual(result["last_close_date"], "2026-11-02")
        self.assertEqual(result["last_close"], 12.5)

    def test_probe_preserves_http_404_reason(self):
        def fail(*args, **kwargs):
            raise RuntimeError("HTTP Error 404: Not Found")

        ticker = type("Ticker", (), {"history": fail})()
        with patch.object(probe.yf, "Ticker", return_value=ticker):
            result = probe.probe_symbol("6565", "2026-11-02")
        self.assertEqual(result["status"], "http_404")
        self.assertIn("404", result["error"])

    def test_load_codes_deduplicates_and_keeps_audit_market_date(self):
        payload = {
            "official_listing_status_check": {"sync_market_date": "2026-11-02"},
            "top_level_failures": [{"code": "7203"}, {"code": "7203"}, {"code": "8058"}],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "audit.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            codes, session = probe.load_codes(path)
        self.assertEqual(codes, ["7203", "8058"])
        self.assertEqual(session, "2026-11-02")
