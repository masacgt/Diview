import unittest

from prepare_v2_financial_repair import prepare


class RepairTest(unittest.TestCase):
    def setUp(self):
        self.document = {'symbol': '1301.T', 'annual': {
            'period_end': '2026-03-31', 'sales': 334612000000, 'old_only': 7,
            'field_status': {'sales': 'available', 'old_only': 'available'},
            'source_values': {'seed': {'source_key': 'market/irbank/v1/securities/1301/overview.json',
                'values': {'sales': 8283, 'total_assets': 6973, 'old_only': 7}},
                'yfinance': {'values': {'sales': 334612000000}},
                'calculated': {'values': {'bad_ratio': {'input_sources': {'assets': 'seed'}}}}}},
            'valuation': {'eps': 575.97}}
        self.candidate = {'code': '1301', 'period_end': '2026-03-31',
            'source': 'J-Quants API /fins/summary', 'document_type': 'FYFinancialStatements_Consolidated_JP',
            'values': {'sales': '334612000000', 'total_assets': '214128000000', 'equity_ratio_computed': '36.100'},
            'field_units': {'sales': 'jpy', 'total_assets': 'jpy', 'equity_ratio_computed': 'percent'}}

    def test_preserves_independent_values_and_quarantines_old_fields(self):
        result = prepare(self.document, self.candidate, (8283, 6973))
        annual = result['annual']
        self.assertEqual(annual['sales'], 334612000000)
        self.assertEqual(annual['total_assets'], 214128000000)
        self.assertEqual(annual['equity_ratio_computed'], 36.1)
        self.assertNotIn('old_only', annual)
        self.assertEqual(annual['field_status']['old_only'], 'unsupported')
        self.assertEqual(annual['source_values']['calculated']['values'], {})
        self.assertEqual(result['valuation'], self.document['valuation'])
        self.assertEqual(self.document['annual']['old_only'], 7)

    def test_rejects_wrong_period_or_fingerprint(self):
        with self.assertRaises(ValueError):
            prepare(self.document, {**self.candidate, 'period_end': '2025-03-31'}, (8283, 6973))
        with self.assertRaises(ValueError):
            prepare(self.document, self.candidate, (8284, 6973))


if __name__ == '__main__':
    unittest.main()
