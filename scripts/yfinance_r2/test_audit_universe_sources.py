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
            ]}), encoding="utf-8")
            jquants.write_text(json.dumps({"response_dates": ["2026-11-03"], "data": [
                {"Code": "13010", "ProdCat": "011"},
                {"Code": "130A0", "ProdCat": "014"},
                {"Code": "99990", "ProdCat": "013"},
            ]}), encoding="utf-8")

            report = build_report(irbank, jquants)

        self.assertEqual(report["counts"]["irbank_duplicates"], 1)
        self.assertEqual(report["counts"]["intersection"], 2)
        self.assertEqual([row["code"] for row in report["irbank_only"]], ["92025"])
        self.assertEqual([row["code"] for row in report["jquants_only"]], ["9999"])
        self.assertEqual(report["jquants_only"][0]["likely_product_scope"], "reit")
        self.assertIn({"source": "IRBANK", "raw": "130A0", "display": "130A"}, report["normalization_examples"])
        self.assertEqual(report["irbank_only"][0]["reason"], "unclassified_membership_difference")
