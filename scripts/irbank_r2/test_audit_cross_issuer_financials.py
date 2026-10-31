from unittest import TestCase
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from audit_cross_issuer_financials import find_candidates, find_known_partial_contamination
from prepare_dataset import financial_periods, validate_annual_overrides


class CrossIssuerAuditTest(TestCase):
    def test_known_2708_partial_contamination_does_not_suppress_corrected_data(self):
        wrong = {"edinet_code": "E02938", "periods": [{"fiscal_year": "2014/03", "sales": 62268,
                 "total_assets": 27566, "operating_cf": 5179}]}
        self.assertEqual(find_known_partial_contamination([wrong]), ["E02938/2014/03"])
        corrected = {"edinet_code": "E02938", "periods": [{"fiscal_year": "2014/03", "sales": 62268445000,
                     "total_assets": 19002585000, "operating_cf": -1082741000}]}
        self.assertEqual(find_known_partial_contamination([corrected]), [])
    def test_distinct_issuers_repeated_values_and_shifted_period(self):
        rows = [{"fiscal_year": str(y), "sales": y * 10, "operating_profit": y,
                 "total_assets": y * 9, "net_assets": y * 2} for y in (2023, 2024, 2025)]
        other = [dict(row) for row in rows]
        other[0]["fiscal_year"] = "2017"
        result = find_candidates([{"edinet_code": "E1", "periods": rows},
                                  {"edinet_code": "E2", "periods": other}])
        self.assertEqual(result[0]["edinet_codes"], ["E1", "E2"])
        self.assertEqual(result[0]["matching_count"], 3)
        self.assertIn(["2023", "2017"], result[0]["matching_periods"])

    def test_missing_fields_and_duplicate_single_issuer_do_not_match(self):
        row = {"fiscal_year": "2025", "sales": 10, "total_assets": 20}
        self.assertEqual(find_candidates([{"edinet_code": "E1", "periods": [row, row]},
                                          {"edinet_code": "E2", "periods": [row]}], minimum=1), [])

    def test_preparation_rejects_repeated_cross_issuer_records_before_writing(self):
        rows = [{"fiscal_year": str(y), "sales": y * 10, "operating_profit": y,
                 "total_assets": y * 9, "net_assets": y * 2} for y in (2023, 2024, 2025)]
        with TemporaryDirectory() as tmp:
            source = Path(tmp)
            annual = source / "financials" / "annual"
            annual.mkdir(parents=True)
            (annual / "batch_0000.json").write_text(json.dumps({"results": [
                {"edinet_code": "E1", "periods": rows}, {"edinet_code": "E2", "periods": rows}
            ]}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "E1/E2"):
                financial_periods(source)

    def test_supplemental_override_is_checked_against_other_issuers(self):
        rows = [{"fiscal_year": f"{year}/03", "sales": year * 10, "operating_profit": year,
                 "total_assets": year * 9, "net_assets": year * 2} for year in (2023, 2024, 2025)]
        financials = {"E00001": {"annual": rows}}
        master = [{"security_code": "11110", "edinet_code": "E00001"},
                  {"security_code": "22220", "edinet_code": "E00002"}]
        with self.assertRaisesRegex(ValueError, "E00001/E00002"):
            validate_annual_overrides(financials, {"2222": {"financials_annual": rows}}, master)
        validate_annual_overrides(financials, {"1111": {"financials_annual": rows}}, master)
