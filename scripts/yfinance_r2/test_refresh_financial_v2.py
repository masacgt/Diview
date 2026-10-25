from contextlib import redirect_stdout
from datetime import date
import io
import json
from pathlib import Path
import tempfile
from unittest import TestCase
from unittest.mock import Mock, patch

import generate_income_advice as advice
import migrate_legacy_financials as migration
import refresh_financial_v2 as refresh


class FinancialRefreshContractTests(TestCase):
    def test_letter_codes_use_normal_sync_contract(self):
        self.assertEqual(refresh.normalize_security_code(" 130a "), "130A")
        self.assertEqual(refresh.normalize_security_code("4502"), "4502")
        with self.assertRaises(ValueError):
            refresh.normalize_security_code("../4502")

    def test_all_active_letter_codes_are_included(self):
        universe = {"securities": [
            {"code": "130A"}, {"code": "4502"},
            {"code": "285A", "status": "inactive"},
        ]}
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(refresh.collector, "configure_yfinance"), \
                    patch.object(refresh.collector, "get_client", return_value=object()), \
                    patch.object(refresh.collector, "required_env", return_value="test"), \
                    patch.object(refresh.sync, "read_json", return_value=universe), \
                    patch.object(refresh, "refresh", return_value={
                        "exit_code": 0, "financial_rows_processed": 1
                    }) as fetch, redirect_stdout(io.StringIO()):
                path = Path(directory) / "report.json"
                self.assertEqual(refresh.refresh_all(str(path)), 0)
                self.assertEqual([call.args[0] for call in fetch.call_args_list], ["130A", "4502"])
                self.assertEqual(json.loads(path.read_text())["universe_security_count"], 2)

    def test_financial_revision_changes_even_after_partial_write_failure(self):
        client = object()
        published_revisions = []

        def publish_revision(*_args):
            revision = f"revision-{len(published_revisions) + 1}"
            published_revisions.append(revision)
            return revision

        with patch.object(refresh.collector, "configure_yfinance"), \
                patch.object(refresh.collector, "required_env", return_value="test"), \
                patch.object(refresh.collector, "get_client", return_value=client), \
                patch.object(refresh, "refresh_with_client", side_effect=RuntimeError("read-back failed")), \
                patch.object(refresh, "bump_financial_revision", side_effect=publish_revision) as bump_revision:
            for _ in range(2):
                with self.assertRaisesRegex(RuntimeError, "read-back failed"):
                    refresh.refresh("4502")
            self.assertEqual(bump_revision.call_count, 2)
            self.assertEqual(bump_revision.call_args_list[0].args, (client, "test"))
            self.assertEqual(bump_revision.call_args_list[1].args, (client, "test"))
            self.assertNotEqual(*published_revisions)

    def test_income_advice_uses_market_date_with_opaque_generation(self):
        generation = "2026-10-19-" + "a" * 32
        catalog = {"generation": generation, "market_data_date": "2026-10-19", "items": [
            {"code": "4502", "category": "jp", "currency": "JPY"}
        ]}
        with patch.object(advice, "build_candidate", return_value=({"code": "4502"}, None)) as build, \
                redirect_stdout(io.StringIO()):
            result, _ = advice.build_catalog(None, "test", catalog, workers=1)
        self.assertEqual(result["generation"], generation)
        self.assertEqual(result["items"][0]["source_catalog_generation"], generation)
        self.assertEqual(build.call_args.args[-1], date(2026, 10, 19))

    def test_migration_preserves_explicit_mixed_unit_guard_and_raw_seed(self):
        row, _ = migration.merge_row(
            {
                "fiscal_year": "2026/03", "period_end": "2026-03-31",
                "operating_cf": None,
                "field_status": {"operating_cf": "unsupported"},
                "field_status_reason": {"operating_cf": ["mixed_source_units_not_comparable"]},
            },
            "2026/03", "2026-03-31", {"operating_cf": 50},
            source_key="market/irbank/v1/securities/4502/overview.json",
        )
        self.assertIsNone(row["operating_cf"])
        self.assertEqual(row["source_values"]["seed"]["values"]["operating_cf"], 50)

    def test_migration_invalidates_financials_on_failure_but_not_dry_run(self):
        for dry_run in (False, True):
            with self.subTest(dry_run=dry_run), tempfile.TemporaryDirectory() as directory:
                client = Mock()
                argv = ["migrate", "--report", str(Path(directory) / "report.json")]
                if dry_run:
                    argv += ["--dry-run"]
                with patch.object(migration.sys, "argv", argv), \
                        patch.object(migration, "required_env", return_value="test"), \
                        patch.object(migration, "make_client", return_value=client), \
                        patch.object(migration, "bump_financial_revision") as bump_revision, \
                        patch.object(migration, "read_json", return_value={
                            "securities": [{"code": "4502", "status": "active"}]
                        }), \
                        patch.object(migration, "migrate_one", side_effect=RuntimeError("partial write")), \
                        redirect_stdout(io.StringIO()):
                    self.assertEqual(migration.main(), 1)
                if dry_run:
                    bump_revision.assert_not_called()
                else:
                    bump_revision.assert_called_once_with(client, "test")
