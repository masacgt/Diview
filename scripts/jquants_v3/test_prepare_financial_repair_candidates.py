import unittest

from prepare_financial_repair_candidates import extract_candidates


class FinancialRepairCandidatesTest(unittest.TestCase):
    def test_only_matching_full_year_consolidated_values_and_units_are_selected(self):
        base = {"Code": "13010", "CurPerType": "FY", "DocType": "FYFinancialStatements_Consolidated_JP",
                "CurPerEn": "2026-03-31", "CurFYEn": "2026-03-31", "Sales": "2000000", "EqAR": "0.361"}
        rows = [base, {**base, "Code": "13800"}, {**base, "CurPerType": "3Q"},
                {**base, "DocType": "EarnForecastRevision"}, {**base, "CurFYEn": "2027-03-31"}]
        result = extract_candidates("1301", rows)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["values"], {"sales": "2000000", "equity_ratio_computed": "36.100"})
        self.assertEqual(result[0]["field_units"]["sales"], "jpy")
        self.assertEqual(result[0]["field_units"]["equity_ratio_computed"], "percent")

    def test_nonfinite_and_wrong_code_are_rejected(self):
        row = {"Code": "13010", "CurPerType": "FY", "DocType": "FYFinancialStatements_Consolidated_JP",
               "CurPerEn": "2026-03-31", "CurFYEn": "2026-03-31", "Sales": "NaN"}
        with self.assertRaises(ValueError):
            extract_candidates("1301", [row])
        with self.assertRaises(ValueError):
            extract_candidates("1301.T", [row])
