import unittest
import io
import json

from migrate_legacy_financials import migrate_one, repair_mixed_source_equity_ratio, select_shard


class FakePaginator:
    def __init__(self, client):
        self.client = client

    def paginate(self, *, Bucket, Prefix):
        keys = [key for key in self.client.objects if key.startswith(Prefix)]
        return [{"Contents": [{"Key": key} for key in keys]}]


class FakeS3Client:
    def __init__(self, objects, *, ignore_put=False):
        self.objects = {key: json.dumps(value).encode("utf-8") for key, value in objects.items()}
        self.ignore_put = ignore_put

    def get_object(self, *, Bucket, Key):
        return {"Body": io.BytesIO(self.objects[Key])}

    def get_paginator(self, operation):
        self.assert_operation = operation
        return FakePaginator(self)

    def put_object(self, *, Bucket, Key, Body, ContentType):
        if not self.ignore_put:
            self.objects[Key] = Body


class MigrationShardTests(unittest.TestCase):
    def test_shards_are_disjoint_balanced_and_cover_sorted_active_codes(self):
        securities = [{"code": code, "status": "active"} for code in ("9434", "7203", "1301", "8303", "4502", "1605", "7011", "2914", "6501")]

        shards = [
            select_shard(securities, shard_index=index, shard_count=4)
            for index in range(4)
        ]
        shard_codes = [[row["code"] for row in shard] for shard in shards]
        flattened = [code for shard in shard_codes for code in shard]
        expected_sorted = sorted(row["code"] for row in securities)

        self.assertCountEqual(flattened, expected_sorted)
        self.assertEqual(shard_codes, [expected_sorted[index::4] for index in range(4)])
        self.assertEqual(len(flattened), len(set(flattened)))
        self.assertEqual(sorted(map(len, shards)), [2, 2, 2, 3])

    def test_limit_is_applied_before_partitioning(self):
        securities = [{"code": f"{code:04d}"} for code in range(10, 0, -1)]

        shard = select_shard(securities, limit=5, shard_index=1, shard_count=2)

        self.assertEqual([row["code"] for row in shard], ["0002", "0004"])

    def test_invalid_shard_index_is_rejected(self):
        with self.assertRaises(ValueError):
            select_shard([{"code": "7203"}], shard_index=2, shard_count=2)

    def test_empty_shard_count_is_rejected(self):
        with self.assertRaises(ValueError):
            select_shard([{"code": "7203"}], shard_index=0, shard_count=0)

    def test_mixed_source_calculation_is_removed_and_seed_ratio_is_restored(self):
        annual = {
            "equity_ratio_computed": 37975096.6,
            "field_status": {"equity_ratio_computed": "stale"},
            "source_values": {
                "seed": {"values": {"equity_ratio_computed": 37.975}},
                "calculated": {"values": {
                    "equity_ratio_computed": {
                        "value": 37975096.6,
                        "input_sources": {"equity": "yfinance", "total_assets": "seed"},
                    },
                }},
            },
        }

        self.assertTrue(repair_mixed_source_equity_ratio(annual))

        self.assertEqual(annual["equity_ratio_computed"], 37.975)
        self.assertEqual(annual["field_status"]["equity_ratio_computed"], "legacy_migrated")
        self.assertNotIn("equity_ratio_computed", annual["source_values"]["calculated"]["values"])

    def test_mixed_source_calculation_without_seed_ratio_becomes_empty(self):
        annual = {
            "equity_ratio_computed": 48139282.0,
            "field_status": {"equity_ratio_computed": "available"},
            "source_values": {
                "seed": {"values": {"total_assets": 15108792}},
                "calculated": {"values": {
                    "equity_ratio_computed": {
                        "value": 48139282.0,
                        "input_sources": {"equity": "yfinance", "total_assets": "seed"},
                    },
                }},
            },
        }

        self.assertTrue(repair_mixed_source_equity_ratio(annual))

        self.assertNotIn("equity_ratio_computed", annual)
        self.assertEqual(annual["field_status"]["equity_ratio_computed"], "empty")
        self.assertEqual(
            annual["field_status_reason"]["equity_ratio_computed"],
            ["mixed_source_inputs_not_comparable"],
        )

    def test_same_source_equity_ratio_calculation_is_preserved(self):
        annual = {
            "equity_ratio_computed": 37.5,
            "field_status": {"equity_ratio_computed": "available"},
            "source_values": {"calculated": {"values": {
                "equity_ratio_computed": {
                    "value": 37.5,
                    "input_sources": {"equity": "yfinance", "total_assets": "yfinance"},
                },
            }}},
        }

        self.assertFalse(repair_mixed_source_equity_ratio(annual))
        self.assertEqual(annual["equity_ratio_computed"], 37.5)

    def test_same_new_source_equity_ratio_calculation_is_preserved(self):
        annual = {
            "equity_ratio_computed": 38.0,
            "source_values": {"calculated": {"values": {
                "equity_ratio_computed": {
                    "value": 38.0,
                    "input_sources": {"equity": "jquants", "total_assets": "jquants"},
                },
            }}},
        }

        self.assertTrue(repair_mixed_source_equity_ratio(annual))
        self.assertEqual(annual["equity_ratio_computed"], 38.0)
        self.assertEqual(annual["field_status"]["equity_ratio_computed"], "available")

    def test_unknown_source_equity_ratio_calculation_is_removed(self):
        annual = {
            "equity_ratio_computed": 999.0,
            "source_values": {"calculated": {"values": {
                "equity_ratio_computed": {
                    "value": 999.0,
                    "input_sources": {"equity": "unknown", "total_assets": "unknown"},
                },
            }}},
        }

        self.assertTrue(repair_mixed_source_equity_ratio(annual))
        self.assertNotIn("equity_ratio_computed", annual)
        self.assertEqual(annual["field_status"]["equity_ratio_computed"], "empty")

    def test_migrate_one_repairs_existing_record_even_when_no_seed_field_is_added(self):
        source_key = "market/irbank/v1/securities/7203/overview.json"
        target_key = "market/yfinance/v2/securities/7203.T/financials/2025-03-31.json"
        source = {"financials": {"annual": [{"fiscal_year": "2025/03", "total_assets": 105522331}]}}
        existing = {
            "schema_version": 1,
            "symbol": "7203.T",
            "annual": {
                "fiscal_year": "2025/03",
                "period_end": "2025-03-31",
                "total_assets": 105522331,
                "equity_ratio_computed": 37829768.9,
                "field_status": {"equity_ratio_computed": "empty"},
                "field_status_reason": {"equity_ratio_computed": ["mixed_source_inputs_not_comparable"]},
                "source_values": {
                    "seed": {"values": {"total_assets": 105522331, "equity_ratio_computed": 37.8}},
                    "calculated": {"values": {
                        "equity_ratio_computed": {
                            "value": 37829768.9,
                            "input_sources": {"equity": "yfinance", "total_assets": "seed"},
                        },
                    }},
                },
            },
        }
        client = FakeS3Client({source_key: source, target_key: existing})

        result = migrate_one(
            client,
            "test-bucket",
            {"code": "7203", "symbol": "7203.T"},
            dry_run=False,
        )

        saved = json.loads(client.objects[target_key])
        self.assertEqual(result["status"], "migrated")
        self.assertEqual(result["field_count"], 0)
        self.assertEqual(result["repaired_derived_field_count"], 1)
        self.assertEqual(saved["annual"]["equity_ratio_computed"], 37.8)
        self.assertEqual(saved["annual"]["field_status"]["equity_ratio_computed"], "legacy_migrated")
        self.assertNotIn("equity_ratio_computed", saved["annual"]["source_values"]["calculated"]["values"])

    def test_migrate_one_fails_when_r2_readback_keeps_old_derived_ratio(self):
        source_key = "market/irbank/v1/securities/7203/overview.json"
        target_key = "market/yfinance/v2/securities/7203.T/financials/2025-03-31.json"
        source = {"financials": {"annual": [{"fiscal_year": "2025/03", "total_assets": 105522331}]}}
        existing = {
            "schema_version": 1,
            "symbol": "7203.T",
            "annual": {
                "fiscal_year": "2025/03",
                "period_end": "2025-03-31",
                "total_assets": 105522331,
                "equity_ratio_computed": 37829768.9,
                "field_status": {"total_assets": "legacy_migrated", "equity_ratio_computed": "empty"},
                "field_status_reason": {"equity_ratio_computed": ["mixed_source_inputs_not_comparable"]},
                "source_values": {
                    "seed": {"values": {"total_assets": 105522331}},
                    "calculated": {"values": {
                        "equity_ratio_computed": {
                            "value": 37829768.9,
                            "input_sources": {"equity": "yfinance", "total_assets": "seed"},
                        },
                    }},
                },
            },
        }
        client = FakeS3Client({source_key: source, target_key: existing}, ignore_put=True)

        with self.assertRaisesRegex(RuntimeError, "source_values.calculated"):
            migrate_one(
                client,
                "test-bucket",
                {"code": "7203", "symbol": "7203.T"},
                dry_run=False,
            )


if __name__ == "__main__":
    unittest.main()
