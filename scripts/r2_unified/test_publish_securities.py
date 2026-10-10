import importlib.util
import io
import json
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("publish_securities.py")
SPEC = importlib.util.spec_from_file_location("publish_securities", MODULE_PATH)
publisher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(publisher)


class FakeClient:
    def __init__(self, objects):
        self.objects = {key: json.dumps(value).encode() for key, value in objects.items()}

    def get_paginator(self, _operation):
        client = self

        class Paginator:
            def paginate(self, Bucket, Prefix):
                keys = sorted(key for key in client.objects if key.startswith(Prefix))
                return [{"Contents": [{"Key": key} for key in keys]}]

        return Paginator()

    def get_object(self, Bucket, Key):
        return {"Body": io.BytesIO(self.objects[Key])}

    def put_object(self, Bucket, Key, Body, **_kwargs):
        self.objects[Key] = Body

    def delete_object(self, Bucket, Key):
        del self.objects[Key]


class CleanYahooEventsTest(unittest.TestCase):
    def test_removes_invalid_and_exact_duplicate_events_and_sorts(self):
        rows = [
            {"date": "2024-09-01", "amount": 2},
            {"date": "2024-03-01", "amount": 1},
            {"date": "2024-03-01", "amount": 1},
            {"date": "2024-02-30", "amount": 100},
            {"date": "2024-04-01", "amount": 0},
            {"date": "2024-05-01", "amount": float("nan")},
            None,
        ]
        self.assertEqual(
            publisher.clean_yahoo_events(rows),
            [{"date": "2024-03-01", "amount": 1.0}, {"date": "2024-09-01", "amount": 2.0}],
        )

    def test_non_array_is_empty(self):
        self.assertEqual(publisher.clean_yahoo_events(None), [])

    def test_publisher_keeps_both_sources_and_prunes_stale_managed_bundle(self):
        client = FakeClient({
            "market/irbank/v1/catalog.json": {"securities": [{"code": "7203"}]},
            "market/irbank/v1/securities/7203/dividends.json": {
                "source": "IRBANK Connect", "records": [{"fiscal_year": 2024, "annual_dps_adjusted": 100}]
            },
            "market/yfinance/dividends/7203.T.json": {
                "source": "Yahoo Finance via yfinance",
                "records": [{"date": "2024-03-01", "amount": 50}, {"date": "2024-03-01", "amount": 50}],
            },
            "market/securities/v1/9999.json": {"security": {"code": "9999"}},
        })
        stats = publisher.publish(client)
        result = json.loads(client.objects["market/securities/v1/7203.json"])
        self.assertIn("irbank", result["dividend_history_by_source"])
        self.assertEqual(result["dividend_history_by_source"]["yfinance"], [
            {"date": "2024-03-01", "amount": 50.0}
        ])
        self.assertNotIn("market/securities/v1/9999.json", client.objects)
        self.assertEqual(stats[2], 1)


if __name__ == "__main__":
    unittest.main()
