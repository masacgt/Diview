import unittest
from datetime import date
from unittest.mock import patch

from refresh_v2_dividends import fetch_latest_forecast, parse_forecast


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


if __name__ == "__main__":
    unittest.main()
