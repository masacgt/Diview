from contextlib import redirect_stdout
from copy import deepcopy
from datetime import date
import io
from unittest import TestCase
from unittest.mock import Mock, patch

import generate_income_advice as advice
import migrate_legacy_dividends as dividends
import refresh_financial_v2 as refresh


class ReviewContractTests(TestCase):
    def test_latest_statement_compares_contents_across_both_key_formats(self):
        base = "market/yfinance/v2/securities/4502.T/financials/"
        docs = {
            base + "2026.json": {"annual": {"period_end": "2026-03-31"}},
            base + "2025-03-31.json": {"annual": {"period_end": "2025-03-31"}},
        }
        client = Mock()
        client.get_paginator.return_value.paginate.return_value = [
            {"Contents": [{"Key": key} for key in docs]}
        ]
        with patch.object(advice.sync, "read_json", side_effect=lambda c, b, k: docs[k]):
            self.assertEqual(advice.latest_annual_statement(client, "test", "4502.T")["period_end"], "2026-03-31")

    def test_targeted_refresh_recomputes_cached_advice_financials(self):
        base = "market/yfinance/v2/securities/4502.T/"
        docs = {base + "current.json": {
            "statistics": {}, "field_status": {},
            "income_advice_metrics": {"equity_ratio": 24, "financial_period_end": "2025-03-31"},
        }}
        annual = {"period_end": "2026-03-31", "equity_ratio_computed": 60,
                  "field_status": {"equity_ratio_computed": "available"}}
        ticker = Mock()
        ticker.get_info.return_value = {}
        status = {group: "available" for group in refresh.sync.FINANCIAL_GROUPS}
        def put(c, b, key, value):
            docs[key] = deepcopy(value)
        with patch.object(refresh.yf, "Ticker", return_value=ticker), \
                patch.object(refresh.sync, "latest_completed_xtks_session", return_value=date(2026, 10, 19)), \
                patch.object(refresh.sync, "read_json", side_effect=lambda c, b, k: deepcopy(docs.get(k))), \
                patch.object(refresh.sync, "put_json_if_changed", side_effect=put), \
                patch.object(refresh.sync, "legacy_document", return_value={}), \
                patch.object(refresh.sync, "collect_financials", return_value=([annual], [], status, {})), \
                redirect_stdout(io.StringIO()):
            self.assertEqual(refresh.refresh_with_client("4502", object(), "test")["exit_code"], 0)
        cached = docs[base + "current.json"]["income_advice_metrics"]
        self.assertEqual(cached["equity_ratio"], 60)
        self.assertEqual(cached["financial_period_end"], "2026-03-31")

    def test_issuer_migration_preserves_both_periods_and_accepts_corrections(self):
        rows = [{"fiscal_year": 2026, "period": "2026/03", "annual_dps": 10},
                {"fiscal_year": 2026, "period": "2026/12", "annual_dps": 20}]
        saved = dividends.records_by_period(rows)
        self.assertEqual(set(saved), {"2026-03", "2026-12"})
        self.assertEqual(saved["2026-03"]["annual_dps"], 10)
