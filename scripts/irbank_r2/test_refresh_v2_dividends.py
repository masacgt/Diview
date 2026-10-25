import hashlib
import json
import unittest
from datetime import date
from io import BytesIO
from unittest.mock import patch

from refresh_v2_dividends import (
    DEFAULT_R2_KEY,
    DIVIDEND_SHARD_MANIFEST_KEY,
    DIVIDEND_SHARD_ROOT,
    activate_dividend_index_shards,
    build_dividend_index_shards,
    fetch_latest_forecast,
    json_bytes,
    parse_forecast,
    prepare_dividend_index_shards,
)
from refresh_v2_dividend_batch import publish_index


class MissingR2Key(Exception):
    response = {"Error": {"Code": "NoSuchKey"}}


class FakeR2Client:
    def __init__(self, objects=None):
        self.objects = dict(objects or {})

    def put_object(self, *, Bucket, Key, Body, **_kwargs):
        self.objects[Key] = bytes(Body)
        return {"ETag": "test-etag"}

    def get_object(self, *, Bucket, Key):
        if Key not in self.objects:
            raise MissingR2Key(Key)
        value = self.objects[Key]
        return {"Body": BytesIO(value if isinstance(value, bytes) else json_bytes(value))}


class IrbankForecastParsingTests(unittest.TestCase):
    def test_reads_disclosures_from_irbank_security_disclosure_response(self):
        listing = {
            "disclosures": [{
                "doc_id": "20260825525826",
                "title": "株式分割と配当予想の修正",
                "disclosed_at": "2026-08-25T06:30:00Z",
            }],
            "attribution": {"source_label": "金融庁 EDINET / TDnet"},
        }
        document = {
            "meta": {"doc_id": "20260825525826", "source": "tdnet", "title": "株式分割と配当予想の修正"},
            "attribution": {"source_label": "金融庁 EDINET / TDnet"},
            "xbrl_facts": [
                {"element_name": "FiscalYearEnd", "value": "2027-03-31"},
                {"element_name": "ReasonForDividendForecastCorrection", "value": "株式分割に伴う修正"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_SecondQuarterMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 122.5, "unit": "JPYPerShares"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_YearEndMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 8.17, "unit": "JPYPerShares"},
            ],
        }
        actual = {"annual_dps": 218, "annual_dps_adjusted": 14.533333333333333}

        with patch("refresh_v2_dividends.request_json", side_effect=[listing, document]) as request:
            forecast, attribution = fetch_latest_forecast("8766", actual, "test-token", as_of=date(2026, 10, 24))

        self.assertEqual(request.call_count, 2)
        self.assertEqual(forecast["annual_dps_current_share_basis"], 16.34)
        self.assertEqual(attribution, listing["attribution"])

    def test_normalizes_tokio_marine_forecast_across_the_15_for_1_split(self):
        document = {
            "meta": {
                "doc_id": "20260825525826",
                "source": "tdnet",
                "title": "株式分割、配当予想の修正に関するお知らせ",
                "disclosed_at": "2026-08-25T06:30:00Z",
            },
            "xbrl_facts": [
                {"element_name": "FiscalYearEnd", "value": "2027-03-31"},
                {"element_name": "ReasonForDividendForecastCorrection", "value": "株式分割に伴う修正"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_SecondQuarterMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 122.5, "unit": "JPYPerShares"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_YearEndMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 8.17, "unit": "JPYPerShares"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_AnnualMember_NonConsolidatedMember_PreviousMember_ForecastMember", "value": 245, "unit": "JPYPerShares"},
            ],
        }
        actual = {"annual_dps": 218, "annual_dps_adjusted": 14.533333333333333}

        result = parse_forecast(document, actual)

        self.assertIsNotNone(result)
        self.assertEqual(result["annual_dps_current_share_basis"], 16.34)
        self.assertEqual(result["fiscal_year"], 2027)
        self.assertEqual(result["normalization"]["factor"], 15)
        self.assertEqual(result["source_refs"], [{"doc_id": "20260825525826", "source": "tdnet"}])

    def test_uses_current_annual_forecast_without_summing_components(self):
        document = {
            "meta": {"doc_id": "20261001000001", "source": "tdnet", "title": "決算短信"},
            "xbrl_facts": [
                {"element_name": "FiscalYearEnd", "value": "2027-03-31"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_AnnualMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 50, "unit": "JPYPerShares"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_SecondQuarterMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 20, "unit": "JPYPerShares"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_YearEndMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 30, "unit": "JPYPerShares"},
            ],
        }

        result = parse_forecast(document, None)

        self.assertEqual(result["annual_dps_current_share_basis"], 50)
        self.assertEqual(result["normalization"]["method"], "reported_current_forecast")

    def test_does_not_guess_when_split_adjustment_cannot_be_confirmed(self):
        document = {
            "meta": {"doc_id": "20260825525826", "source": "tdnet", "title": "株式分割と配当予想の修正"},
            "xbrl_facts": [
                {"element_name": "FiscalYearEnd", "value": "2027-03-31"},
                {"element_name": "ReasonForDividendForecastCorrection", "value": "株式分割に伴う修正"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_SecondQuarterMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 100, "unit": "JPYPerShares"},
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_YearEndMember_NonConsolidatedMember_CurrentMember_ForecastMember", "value": 4, "unit": "JPYPerShares"},
            ],
        }
        actual = {"annual_dps": 120, "annual_dps_adjusted": 10}

        self.assertIsNone(parse_forecast(document, actual))

    def test_ignores_previous_forecast_without_current_period_facts(self):
        document = {
            "meta": {"doc_id": "20260825525826", "source": "tdnet", "title": "配当予想"},
            "xbrl_facts": [
                {"element_name": "DividendPerShare", "context": "CurrentYearDuration_AnnualMember_NonConsolidatedMember_PreviousMember_ForecastMember", "value": 245, "unit": "JPYPerShares"},
            ],
        }

        self.assertIsNone(parse_forecast(document, None))


class IrbankDividendIndexShardTests(unittest.TestCase):
    def test_builds_balanced_prefix_shards_with_only_index_items(self):
        document = {
            "generated_at": "2026-10-26T00:00:00Z",
            "source": "IRBANK Connect",
            "attribution": {"source_label": "IRBANK"},
            "yield_coverage": {"large_metadata": True},
            "items": {
                "7203": {"actual_status": "available", "actual": {"annual_dps": 100}},
                "7211": {"actual_status": "empty", "actual": None},
                "130A": {"actual_status": "not_fetched", "actual": None},
            },
        }

        shards, manifest = build_dividend_index_shards(document, "b")

        self.assertEqual(set(shards), {
            f"{DIVIDEND_SHARD_ROOT}/b/13.json",
            f"{DIVIDEND_SHARD_ROOT}/b/72.json",
        })
        self.assertEqual(manifest["slot"], "b")
        self.assertEqual(manifest["prefixes"], ["13", "72"])
        self.assertEqual(manifest["item_count"], 3)
        self.assertNotIn(b"yield_coverage", shards[f"{DIVIDEND_SHARD_ROOT}/b/13.json"])

    def test_prepares_inactive_slot_then_activates_only_after_verified_writes(self):
        client = FakeR2Client({DIVIDEND_SHARD_MANIFEST_KEY: {
            "schema_version": 1, "slot": "a", "prefixes": ["72"],
        }})
        document = {
            "generated_at": "2026-10-26T00:00:00Z",
            "source": "IRBANK Connect",
            "items": {"7203": {"actual_status": "available", "actual": {"annual_dps": 100}}},
        }

        manifest = prepare_dividend_index_shards(client, "bucket", document)

        self.assertEqual(manifest["slot"], "b")
        self.assertEqual(manifest["shard_count"], 1)
        current = json_bytes(client.objects[DIVIDEND_SHARD_MANIFEST_KEY])
        self.assertIn(b'"slot":"a"', current)
        self.assertEqual(activate_dividend_index_shards(client, "bucket", manifest),
                         hashlib.sha256(json_bytes(manifest)).hexdigest())
        active = client.objects[DIVIDEND_SHARD_MANIFEST_KEY]
        self.assertIn(b'"slot":"b"', active)
        self.assertEqual(client.get_object(Bucket="bucket", Key=f"{DIVIDEND_SHARD_ROOT}/b/72.json")["Body"].read(),
                         json_bytes({"schema_version": 1, "prefix": "72", "items": document["items"]}))

    def test_batch_publisher_activates_shards_before_advancing_financial_revision(self):
        client = FakeR2Client()
        document = {
            "generated_at": "2026-10-26T00:00:00Z",
            "source": "IRBANK Connect",
            "items": {"7203": {"actual_status": "available", "actual": {"annual_dps": 100}}},
        }

        with patch("refresh_v2_dividend_batch.refresh.bump_financial_revision") as bump_revision:
            digest, etag = publish_index(client, "bucket", DEFAULT_R2_KEY, document)

        self.assertEqual(digest, hashlib.sha256(json_bytes(document)).hexdigest())
        self.assertEqual(etag, "test-etag")
        self.assertEqual(json.loads(client.objects[DIVIDEND_SHARD_MANIFEST_KEY])["slot"], "a")
        self.assertEqual(json.loads(client.objects[f"{DIVIDEND_SHARD_ROOT}/a/72.json"])["items"], document["items"])
        bump_revision.assert_called_once_with(client, "bucket")


if __name__ == "__main__":
    unittest.main()
