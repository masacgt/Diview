from __future__ import annotations

from io import BytesIO
import json
import unittest

from botocore.exceptions import ClientError

from audit_legacy_dividend_events import inspect_security


class FakePaginator:
    def __init__(self, client):
        self.client = client

    def paginate(self, *, Bucket, Prefix):
        yield {
            "Contents": [
                {"Key": key}
                for key in sorted(self.client.objects)
                if key.startswith(Prefix)
            ]
        }


class FakeR2:
    def __init__(self, objects):
        self.objects = objects

    def get_object(self, *, Bucket, Key):
        if Key not in self.objects:
            raise ClientError({"Error": {"Code": "NoSuchKey"}}, "GetObject")
        return {"Body": BytesIO(json.dumps(self.objects[Key]).encode("utf-8"))}

    def get_paginator(self, operation):
        self.assert_operation = operation
        return FakePaginator(self)


class LegacyDividendAuditTests(unittest.TestCase):
    def test_missing_legacy_object_still_reads_current_and_archived_v2(self):
        client = FakeR2({
            "market/yfinance/v2/securities/7203.T/dividends/2024.json": {
                "records": [{"date": "2024-05-01", "amount": 100}],
            },
            "market/yfinance/v2/archive/events/7203.T/dividends/2023-archive.json": {
                "records": [{"date": "2023-05-01", "amount": 90}],
            },
        })

        result = inspect_security(client, "bucket", "7203")

        self.assertEqual(result["status"], "legacy_missing_v2_present")
        self.assertFalse(result["legacy_baseline_available"])
        self.assertIsNone(result["legacy_event_count"])
        self.assertEqual(result["v2_current_event_count"], 1)
        self.assertEqual(result["v2_archive_event_count"], 1)
        self.assertEqual(result["v2_date_range"], ["2023-05-01", "2024-05-01"])

    def test_missing_legacy_object_and_empty_v2_is_reported(self):
        result = inspect_security(FakeR2({}), "bucket", "7203")

        self.assertEqual(result["status"], "legacy_missing_v2_empty")
        self.assertEqual(result["v2_current_event_count"], 0)
        self.assertEqual(result["v2_archive_event_count"], 0)

    def test_legacy_date_without_current_or_archive_record_is_reported(self):
        result = inspect_security(FakeR2({
            "market/yfinance/securities/7203.T.json": {
                "actions": {"dividends": [{"date": "2020-06-01", "amount": 50}]},
            },
        }), "bucket", "7203")

        self.assertEqual(result["status"], "legacy_events_missing_from_v2")
        self.assertEqual(result["missing_date_count"], 1)
        self.assertEqual(result["missing_legacy_events"], [{
            "date": "2020-06-01",
            "events": [{"date": "2020-06-01", "amount": 50}],
        }])

    def test_legacy_dates_match_current_and_archive_records(self):
        result = inspect_security(FakeR2({
            "market/yfinance/securities/7203.T.json": {
                "actions": {"dividends": [
                    {"date": "2023-05-01", "amount": 90},
                    {"date": "2024-05-01", "amount": 100},
                ]},
            },
            "market/yfinance/v2/securities/7203.T/dividends/2024.json": {
                "records": [{"date": "2024-05-01", "amount": 100}],
            },
            "market/yfinance/v2/archive/events/7203.T/dividends/2023-archive.json": {
                "records": [{"date": "2023-05-01", "amount": 90}],
            },
        }), "bucket", "7203")

        self.assertEqual(result["status"], "complete")
        self.assertEqual(result["matched_date_count"], 2)
        self.assertEqual(result["missing_date_count"], 0)


if __name__ == "__main__":
    unittest.main()
