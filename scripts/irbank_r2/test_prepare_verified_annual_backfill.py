import unittest
from copy import deepcopy
from prepare_verified_annual_backfill import merge_annual, periods, seed_values


class AnnualBackfillTest(unittest.TestCase):
    def setUp(self):
        self.row={'fiscal_year':'2014/03','is_consolidated':True,'sales':62268445000,
                  'operating_profit':41253000,'operating_cf':-1082741000,'investing_cf':-374066000,
                  'total_assets':19002585000,'owners_equity':4792197000,'scope_exceptions':[]}
        self.response={'edinet_code':'E02938','attribution':{'source_label':'EDINET / TDnet'},
            'fields':[{'key':k,'unit':'jpy'} for k in ('sales','operating_profit','operating_cf','investing_cf','total_assets','owners_equity')],
            'periods':[{**self.row,'fiscal_year':f'{y}/03'} for y in range(2008,2025)]}

    def test_correct_issuer_units_and_all_periods_required(self):
        rows,_=periods('2708',self.response); self.assertEqual(len(rows),17)
        for change in ({'edinet_code':'E03375'},{'periods':self.response['periods'][:-1]}):
            with self.assertRaises(ValueError): periods('2708',{**self.response,**change})
        mixed=deepcopy(self.response); mixed['periods'][0]['scope_exceptions']=['total_assets']
        with self.assertRaisesRegex(ValueError,'scope'): periods('2708',mixed)

    def test_ratio_uses_owners_equity_and_yahoo_values_are_preserved(self):
        document={'symbol':'2708.T','annual':{'fiscal_year':'2014/03','period_end':'2014-03-31',
            'source_values':{'seed':{'values':{}},'yfinance':{'values':{'sales':62268000000}}},
            'sales':62268000000},'valuation':{'eps_adjusted':25.85}}
        result=merge_annual(document,'2708',self.row,self.response['attribution'],'2026-10-31')
        self.assertEqual(result['annual']['sales'],62268000000)
        self.assertEqual(result['annual']['source_values']['seed']['values']['sales'],62268445000)
        self.assertAlmostEqual(result['annual']['equity_ratio_computed'],4792197000/19002585000*100)
        self.assertEqual(result['valuation'],document['valuation'])
        self.assertEqual(document['annual']['source_values']['seed']['values'],{})

    def test_existing_seed_and_mismatched_period_are_rejected(self):
        doc={'symbol':'2708.T','annual':{'fiscal_year':'2014/03','period_end':'2014-03-31',
            'source_values':{'seed':{'values':{'sales':5}}}}}
        with self.assertRaisesRegex(ValueError,'existing seed'): merge_annual(doc,'2708',self.row,{},'2026-10-31')
        doc['annual']['period_end']='2014-10-31'
        with self.assertRaisesRegex(ValueError,'different annual'): merge_annual(doc,'2708',self.row,{},'2026-10-31')


if __name__=='__main__': unittest.main()
