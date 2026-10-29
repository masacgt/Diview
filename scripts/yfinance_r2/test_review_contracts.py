from contextlib import redirect_stdout
from copy import deepcopy
from datetime import date
import io
import json
import sys
import tempfile
import threading
from pathlib import Path
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

    def test_issuer_migration_bumps_revision_after_write_even_if_readback_fails(self):
        client = Mock()
        code = "7203"
        universe_key = f"{dividends.V2_ROOT}/universe/current.json"
        legacy_key = f"{dividends.LEGACY_IRBANK_ROOT}/{code}/dividends.json"
        target_key = f"{dividends.V2_ROOT}/securities/{code}.T/issuer_dividends/2026.json"
        docs = {universe_key: {"securities": [{"code": code, "symbol": f"{code}.T", "status": "active"}]},
                legacy_key: {"records": [{"fiscal_year": 2026, "annual_dps": 100}]}}
        reads = []
        def read(_client, _bucket, key):
            if key == target_key:
                reads.append(key)
                return None  # Initial read is empty; verification also fails after the successful PUT.
            return docs.get(key)
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(sys, "argv", ["migration", "--workers", "1", "--report", str(Path(directory) / "report.json")]), \
             patch.object(dividends, "required_env", return_value="bucket"), \
             patch.object(dividends, "make_client", return_value=client), \
             patch.object(dividends, "read_json", side_effect=read), \
             patch.object(dividends, "bump_financial_revision") as bump, \
             redirect_stdout(io.StringIO()):
            self.assertEqual(dividends.main(), 1)
            report = json.loads((Path(directory) / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(len(reads), 2)
        self.assertEqual(report["counts_by_status"], {"failed": 1})
        client.put_object.assert_called_once()
        bump.assert_called_once_with(client, "bucket")

    def test_issuer_migration_dry_run_does_not_record_a_write(self):
        client = Mock()
        event = threading.Event()
        with patch.object(dividends, "legacy_dividends", return_value=(
                [{"fiscal_year": 2026, "annual_dps": 100}], "test", None)), \
             patch.object(dividends, "read_json", return_value=None):
            result = dividends.migrate_one(client, "bucket", {"code": "7203", "symbol": "7203.T"},
                                           dry_run=True, write_event=event)
        self.assertEqual(result["status"], "dry_run")
        self.assertFalse(event.is_set())
        client.put_object.assert_not_called()

    def test_issuer_migration_records_put_attempt_even_if_response_is_lost(self):
        client = Mock()
        client.put_object.side_effect = TimeoutError("PUT response lost")
        event = threading.Event()
        with patch.object(dividends, "legacy_dividends", return_value=(
                [{"fiscal_year": 2026, "annual_dps": 100}], "test", None)), \
             patch.object(dividends, "read_json", return_value=None):
            with self.assertRaisesRegex(TimeoutError, "response lost"):
                dividends.migrate_one(client, "bucket", {"code": "7203", "symbol": "7203.T"},
                                      dry_run=False, write_event=event)
        self.assertTrue(event.is_set())
        client.put_object.assert_called_once()

    def test_issuer_migration_bumps_once_for_mixed_success_and_failure(self):
        client = Mock()
        codes = ("7203", "8316")
        universe = {"securities": [{"code": code, "symbol": f"{code}.T", "status": "active"} for code in codes]}
        docs = {f"{dividends.V2_ROOT}/universe/current.json": universe}
        for code in codes:
            docs[f"{dividends.LEGACY_IRBANK_ROOT}/{code}/dividends.json"] = {
                "records": [{"fiscal_year": 2026, "annual_dps": 100}]}
        def put(*, Bucket, Key, Body, **_kwargs):
            docs[Key] = json.loads(Body)
            if "/8316.T/" in Key:
                raise TimeoutError("PUT response lost")
        client.put_object.side_effect = put
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(sys, "argv", ["migration", "--workers", "2", "--report", str(Path(directory) / "report.json")]), \
             patch.object(dividends, "required_env", return_value="bucket"), \
             patch.object(dividends, "make_client", return_value=client), \
             patch.object(dividends, "read_json", side_effect=lambda _c, _b, key: deepcopy(docs.get(key))), \
             patch.object(dividends, "bump_financial_revision") as bump, \
             redirect_stdout(io.StringIO()):
            self.assertEqual(dividends.main(), 1)
            report = json.loads((Path(directory) / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(report["counts_by_status"], {"migrated": 1, "failed": 1})
        self.assertEqual(client.put_object.call_count, 2)
        bump.assert_called_once_with(client, "bucket")
