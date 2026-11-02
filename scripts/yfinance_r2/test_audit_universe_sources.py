import json
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase

from audit_universe_sources import build_report


class UniverseSourceAuditTests(TestCase):
    def test_reports_normalized_differences_categories_and_duplicates(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            irbank = root / "irbank.json"
            jquants = root / "jquants.json"
            irbank.write_text(json.dumps({"fetched_at": "2026-11-03", "securities": [
                {"code": "13010"}, {"code": "130A0"}, {"code": "13010"}, {"code": "92025"},
                {"code": "15530", "industry": "ETF"},
            ]}), encoding="utf-8")
            jquants.write_text(json.dumps({"response_dates": ["2026-11-03"], "data": [
                {"Code": "13010", "ProdCat": "011"},
                {"Code": "130A0", "ProdCat": "014"},
                {"Code": "99990", "ProdCat": "013"},
            ]}), encoding="utf-8")

            report = build_report(irbank, jquants)

        self.assertEqual(report["counts"]["irbank_duplicates"], 1)
        self.assertEqual(report["counts"]["intersection"], 2)
        self.assertEqual(report["intersection_codes"], ["1301", "130A"])
        self.assertEqual(report["normalized_code_union_count"], 5)
        self.assertEqual(len(report["normalized_code_sha256"]["irbank"]), 64)
        self.assertEqual(len(report["normalized_code_sha256"]["jquants"]), 64)
        self.assertEqual([row["code"] for row in report["irbank_only"]], ["1553", "92025"])
        self.assertEqual([row["code"] for row in report["jquants_only"]], ["9999"])
        self.assertEqual(report["jquants_only"][0]["likely_product_scope"], "reit")
        self.assertIn({"source": "IRBANK", "raw": "130A0", "display": "130A"}, report["normalization_examples"])
        self.assertEqual(report["irbank_only"][0]["likely_product_scope"], "etf")
        self.assertEqual(report["irbank_only"][0]["product_scope_source"], "irbank_industry")
        self.assertEqual(report["irbank_only"][1]["reason"], "unclassified_membership_difference")

    def test_reads_categories_and_raw_codes_from_enriched_api_snapshot(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            irbank = root / "irbank.json"
            jquants = root / "jquants.json"
            irbank.write_text(json.dumps({
                "fetched_at": "2026-11-03T00:05:00Z",
                "securities": [{"code": "13010"}, {"code": "650A0"}, {"code": "99990"}],
            }), encoding="utf-8")
            jquants.write_text(json.dumps({
                "response_dates": ["2026-08-10"],
                "retrieved_at": "2026-11-03T00:06:00Z",
                "security_codes": ["1301", "650A", "2643"],
                "data": [
                    {"Code": "13010", "ProdCat": "011"},
                    {"Code": "650A0", "ProdCat": "014"},
                    {"Code": "26430", "ProdCat": "014"},
                ],
            }), encoding="utf-8")

            report = build_report(irbank, jquants)

        self.assertTrue(report["retrieved_same_day"])
        self.assertEqual(report["jquants_product_categories"], {"011": 1, "014": 2})
        self.assertEqual(report["jquants_only"][0]["likely_product_scope"], "etf")
        self.assertIn({"source": "J-Quants", "raw": "13010", "display": "1301"}, report["normalization_examples"])
        self.assertEqual(report["shared_etf_reit_codes"], [{"code": "650A", "prodcat": "014", "type": "etf"}])
