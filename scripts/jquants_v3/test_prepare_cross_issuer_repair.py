import unittest
from prepare_cross_issuer_repair import modify


class CrossIssuerRepairTest(unittest.TestCase):
    def test_october_annual_removed_without_discarding_march_valuation(self):
        doc={'symbol':'2708.T','annual':{'period_end':'2017-10-31','sales':122766,
             'source_values':{'seed':{'source_key':'market/irbank/v1/securities/2708/overview.json',
                 'values':{'sales':122766,'total_assets':52745}}}},
             'valuation':{'period_end':'2017-03-31','eps_adjusted':42}}
        result=modify('market/yfinance/v2/securities/2708.T/financials/2017.json',doc,{})
        self.assertNotIn('annual',result)
        self.assertEqual(result['valuation'],doc['valuation'])
        self.assertIn('annual',doc)

    def test_wrong_fingerprint_fails_closed(self):
        doc={'symbol':'2708.T','annual':{'period_end':'2017-10-31',
             'source_values':{'seed':{'source_key':'market/irbank/v1/securities/2708/overview.json',
                 'values':{'sales':1,'total_assets':52745}}}}}
        with self.assertRaisesRegex(ValueError,'fingerprint'):
            modify('market/yfinance/v2/securities/2708.T/financials/2017.json',doc,{})

    def test_march_repair_cannot_replace_nonempty_seed(self):
        candidate={'code':'2708','period_end':'2025-03-31','source':'J-Quants API /fins/summary',
                   'document_type':'FYFinancialStatements_Consolidated_JP','values':{'sales':'1'},'field_units':{'sales':'jpy'}}
        doc={'symbol':'2708.T','annual':{'period_end':'2025-03-31','source_values':{'seed':{'values':{'sales':2}}}}}
        with self.assertRaisesRegex(ValueError,'not empty'):
            modify('market/yfinance/v2/securities/2708.T/financials/2025-03-31.json',doc,{('2708','2025-03-31'):candidate})

    def test_independent_yahoo_annual_remains(self):
        doc={'symbol':'1301.T','annual':{'period_end':'2023-03-31','sales':1,'equity_ratio_computed':35.15,
             'source_values':{'seed':{'source_key':'market/irbank/v1/securities/1301/overview.json',
                 'values':{'sales':7070,'total_assets':6073,'equity_ratio_computed':35.15}},
                 'yfinance':{'values':{'sales':222}}}}}
        result=modify('market/yfinance/v2/securities/1301.T/financials/2023.json',doc,{})
        self.assertEqual(result['annual']['source_values']['yfinance']['values']['sales'],222)
        self.assertNotIn('equity_ratio_computed',result['annual'])
        self.assertEqual(result['annual']['field_status']['equity_ratio_computed'],'unsupported')


if __name__=='__main__': unittest.main()
