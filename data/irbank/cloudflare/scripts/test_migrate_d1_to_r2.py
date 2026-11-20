import importlib.util
import io
import json
import re
import sqlite3
import sys
import tempfile
import types
import unittest
from argparse import Namespace
from datetime import datetime, timedelta, timezone
from hashlib import sha256 as digest
from pathlib import Path
from urllib.error import HTTPError, URLError
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("migrate_d1_to_r2.py")
SPEC = importlib.util.spec_from_file_location("migrate_d1_to_r2", SCRIPT)
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)

CLI_FILE_ARGS = [
    "--checkpoint-in", "unused-checkpoint-input.json",
    "--checkpoint-out", "unused-checkpoint-output.json",
    "--report", "unused-report.json",
]


def without_hash_values(value):
    if isinstance(value, dict):
        return {
            key: without_hash_values(item)
            for key, item in value.items()
            if "hash" not in key.lower() and "digest" not in key.lower()
        }
    if isinstance(value, list):
        return [without_hash_values(item) for item in value]
    return value


def valid_irbank_source_snapshot(codes):
    codes = sorted(codes)
    return migration.canonical_json({
        "schema_version": 2,
        "source": "IRBANK Connect API v1 /securities",
        "complete": True,
        "security_count": len(codes),
        "security_code_sha256": digest("\n".join(codes).encode("utf-8")).hexdigest(),
        "securities": [{"security_code_display": code} for code in codes],
    })


def valid_irbank_catalog(codes):
    return migration.canonical_json({
        "schema_version": 1,
        "source": "IRBANK Connect securities master",
        "count": len(codes),
        "securities": [{"code": code} for code in codes],
    })


def budget_usage_report(daily_report, effective_rows_read=None):
    daily_rows_read = daily_report["rows_read"]
    effective_rows_read = daily_rows_read if effective_rows_read is None else effective_rows_read
    daily_limit_rows = daily_report.get("daily_limit_rows", migration.d1_daily_usage.DEFAULT_FREE_DAILY_LIMIT)
    return {
        **daily_report,
        "daily_analytics_rows_read": daily_rows_read,
        "daily_analytics_per_database": daily_report.get("per_database", []),
        "minute_day_to_date_rows_read": effective_rows_read,
        "minute_day_to_date_per_database": daily_report.get("per_database", []),
        "minute_day_to_date_queried_at_utc": daily_report.get("queried_at_utc"),
        "minute_day_to_date_confidence_bounds_verified": True,
        "effective_rows_read": effective_rows_read,
        "effective_rows_remaining": max(0, daily_limit_rows - effective_rows_read),
        "effective_limit_exceeded": effective_rows_read > daily_limit_rows,
        "effective_per_database": daily_report.get("per_database", []),
        "budget_usage_source": "daily_and_minute_day_to_date",
        "budget_usage_queried_at_utc": daily_report.get("queried_at_utc"),
    }


class FakeReader:
    def __init__(self, tables=None, reported_rows=None, query_plan_detail=None):
        self.tables = tables or {}
        self.reported_rows = reported_rows
        self.query_plan_detail = query_plan_detail or (
            "SEARCH scheduled_run_log USING INDEX idx_scheduled_run_log_completed_started_id"
        )
        self.token = "test-token"
        self.queries = []

    def query(self, sql, params=None):
        self.queries.append((sql, params or []))
        if sql.lstrip().upper().startswith("EXPLAIN QUERY PLAN"):
            return [{"detail": self.query_plan_detail}], {"rows_read": 0}
        table_match = re.search(r"\bFROM\s+([a-z_]+)", sql, re.IGNORECASE)
        table = table_match.group(1) if table_match else ""
        rows = list(self.tables.get(table, []))
        if re.search(r"\bWHERE\s+security_code\s*>", sql, re.IGNORECASE):
            cursor, limit = params
            rows = [row for row in rows if str(row.get("security_code", "")) > str(cursor)]
            rows = rows[: int(limit)]
        elif re.search(r"\bWHERE\s+security_code_display\s*>", sql, re.IGNORECASE):
            cursor, limit = params
            rows = [row for row in rows if str(row.get("security_code_display", "")) > str(cursor)]
            rows = rows[: int(limit)]
        elif table == "scheduled_run_log" and "finished_at IS NOT NULL" in sql:
            cutoff, cursor_started_at, cursor_id, limit = params
            rows = [
                row for row in rows
                if row.get("finished_at") is not None
                and str(row.get("status") or "").strip().lower()
                in migration.SCHEDULED_RUN_TERMINAL_STATUSES
                and migration.scheduled_runs_key_time(row.get("started_at")) is not None
                and migration.scheduled_runs_key_time(row["started_at"]) >= cutoff
                and (migration.scheduled_runs_key_time(row["started_at"]), int(row["id"]))
                > (str(cursor_started_at), int(cursor_id))
            ]
            rows.sort(key=lambda row: (migration.scheduled_runs_key_time(row["started_at"]), int(row["id"])))
            rows = rows[: int(limit)]
        elif re.search(r"\bWHERE\s+id\s*>", sql, re.IGNORECASE):
            cursor, limit = params
            rows = [row for row in rows if int(row.get("id", 0)) > int(cursor)]
            if "finished_at IS NOT NULL" in sql:
                rows = [row for row in rows if row.get("finished_at") is not None]
            if "started_at >= datetime('now','-365 days')" in sql:
                cutoff = datetime.now(timezone.utc) - timedelta(days=365)
                rows = [row for row in rows if row.get("started_at") and
                        datetime.fromisoformat(row["started_at"].replace("Z", "+00:00")) >= cutoff]
            rows = rows[: int(limit)]
        elif re.search(r"\bWHERE\s+(?:[a-z_]+\.)?rowid\s*>", sql, re.IGNORECASE):
            cursor, limit = params
            rows = [row for row in rows if int(row.get("migration_rowid", 0)) > int(cursor)]
            rows = rows[: int(limit)]
        return rows, {"rows_read": len(rows) if self.reported_rows is None else self.reported_rows}


class FakeR2:
    def __init__(self, objects=None):
        self.objects = dict(objects or {})
        self.get_calls = []
        self.list_calls = []

    def get(self, key):
        self.get_calls.append(key)
        return self.objects.get(key)

    def list_keys(self, prefix):
        self.list_calls.append(prefix)
        return [key for key in self.objects if key.startswith(prefix)]

    def get_with_etag(self, key):
        body = self.get(key)
        return (body, digest(body).hexdigest()) if body is not None else None

    def put_if_absent(self, key, body):
        if key in self.objects:
            return False
        self.objects[key] = body
        return True

    def put_if_unchanged(self, key, body, etag):
        current = self.get(key)
        if current is None or digest(current).hexdigest() != etag:
            return False
        self.objects[key] = body
        return True


class R2ConditionalPutTest(unittest.TestCase):
    def make_store(self):
        class FakeEvents:
            def __init__(self):
                self.handlers = {}

            def register(self, event_name, handler):
                self.handlers[event_name] = handler

        class ClientError(Exception):
            def __init__(self, response, operation_name):
                super().__init__(operation_name)
                self.response = response

        class FakeClient:
            def __init__(self):
                self.meta = types.SimpleNamespace(events=FakeEvents())
                self.calls = []
                self.next_error = None

            def put_object(self, **params):
                context = {}
                self.meta.events.handlers["before-parameter-build.s3.PutObject"](params, context)
                params.setdefault("headers", {})
                self.meta.events.handlers["before-call.s3.PutObject"](params, context)
                self.calls.append(params)
                if self.next_error is not None:
                    error, self.next_error = self.next_error, None
                    raise error

        client = FakeClient()
        boto3 = types.ModuleType("boto3")
        boto3.client = lambda *_args, **_kwargs: client
        botocore = types.ModuleType("botocore")
        botocore.__path__ = []
        exceptions = types.ModuleType("botocore.exceptions")
        exceptions.ClientError = ClientError
        modules = {"boto3": boto3, "botocore": botocore, "botocore.exceptions": exceptions}
        return modules, client, ClientError

    def test_conditional_headers_are_sent_without_unknown_put_object_parameters(self):
        modules, client, _ = self.make_store()
        with patch.dict(sys.modules, modules):
            store = migration.R2Store("account", "access", "secret", "bucket")
            self.assertTrue(store.put_if_absent("new.json", b"new"))
            self.assertTrue(store.put_if_unchanged("current.json", b"next", "etag-value"))

        self.assertEqual(client.calls[0]["headers"]["If-None-Match"], "*")
        self.assertEqual(client.calls[1]["headers"]["If-Match"], '"etag-value"')
        self.assertNotIn("custom_headers", client.calls[0])
        self.assertNotIn("IfNoneMatch", client.calls[0])
        self.assertNotIn("IfMatch", client.calls[1])

    def test_precondition_failure_is_reported_as_a_conflict(self):
        modules, client, client_error = self.make_store()
        with patch.dict(sys.modules, modules):
            store = migration.R2Store("account", "access", "secret", "bucket")
            client.next_error = client_error(
                {"ResponseMetadata": {"HTTPStatusCode": 412}, "Error": {"Code": "PreconditionFailed"}},
                "PutObject",
            )
            self.assertFalse(store.put_if_absent("existing.json", b"new"))


@unittest.skipUnless(importlib.util.find_spec("boto3"), "boto3 is required for the Botocore integration check")
class RealBotocoreConditionalPutTest(unittest.TestCase):
    def test_condition_headers_reach_the_http_request_without_network(self):
        from botocore.awsrequest import AWSResponse

        store = migration.R2Store("example-account", "access", "secret", "bucket")
        sent_headers = []

        class EmptyRawResponse:
            def stream(self, *args, **kwargs):
                return iter(())

            def read(self, *args, **kwargs):
                return b""

        def intercept_before_send(request, **_kwargs):
            sent_headers.append({
                str(name).lower(): value.decode() if isinstance(value, bytes) else str(value)
                for name, value in request.headers.items()
            })
            return AWSResponse(request.url, 200, {"ETag": '"new-etag"'}, EmptyRawResponse())

        def reject_network_transport(_request):
            raise AssertionError("The Botocore integration test must not access the network")

        store.client.meta.events.register(
            "before-send.s3.PutObject",
            intercept_before_send,
            unique_id="migration-test-no-network-interceptor",
        )
        with patch.object(store.client._endpoint.http_session, "send", reject_network_transport):
            self.assertTrue(store.put_if_absent("new.json", b"new"))
            self.assertTrue(store.put_if_unchanged("current.json", b"next", "etag-value"))

        self.assertEqual(sent_headers[0].get("if-none-match"), "*")
        self.assertEqual(sent_headers[1].get("if-match"), '"etag-value"')
        self.assertEqual(len(sent_headers), 2)


class MigrationScriptTest(unittest.TestCase):
    def make_args(self, temp, phase="analysis-current", *, table="securities", dry_run=True, max_rows=20, batch_size=5):
        root = Path(temp)
        return Namespace(
            phase=phase,
            table=table,
            dry_run=dry_run,
            max_rows=max_rows,
            batch_size=batch_size,
            allow_d1_definition_fallback=False,
            checkpoint_in=str(root / "checkpoint.json"),
            checkpoint_out=str(root / "checkpoint.json"),
            report=str(root / "report.json"),
        )

    def test_only_single_select_or_select_query_plan_is_accepted(self):
        reader = migration.D1Reader("account", "database", "token")
        for sql in (
            "DELETE FROM securities",
            "EXPLAIN QUERY PLAN DELETE FROM securities",
            "EXPLAIN SELECT 1",
            "SELECT 1; DELETE FROM securities",
            "EXPLAIN QUERY PLAN SELECT 1; DELETE FROM securities",
            "WITH x AS (SELECT 1) SELECT * FROM x",
        ):
            with self.subTest(sql=sql), self.assertRaises(migration.MigrationError):
                reader.query(sql)

    def test_d1_reader_allows_only_read_only_explain_query_plan_select(self):
        reader = migration.D1Reader("account", "database", "token")
        sql = "EXPLAIN QUERY PLAN SELECT security_code FROM securities WHERE security_code = ?"
        payload = {
            "success": True,
            "result": [{
                "results": [{"detail": "SEARCH securities USING PRIMARY KEY (security_code=?)"}],
                "meta": {"rows_read": 0},
            }],
        }
        with patch.object(
            migration, "urlopen", return_value=io.BytesIO(json.dumps(payload).encode("utf-8"))
        ) as request_call:
            rows, meta = reader.query(sql, ["7203"])

        self.assertEqual(rows, payload["result"][0]["results"])
        self.assertEqual(meta, {"rows_read": 0})
        request = request_call.call_args.args[0]
        self.assertEqual(json.loads(request.data.decode("utf-8")), {"sql": sql, "params": ["7203"]})

    def test_read_budget_requires_cloudflare_rows_read_metadata(self):
        with self.assertRaises(migration.MigrationError):
            migration.rate_rows({})
        with self.assertRaises(migration.MigrationError):
            migration.rate_rows({"rows_read": -1})

    def test_failed_d1_post_is_not_retried_when_execution_is_ambiguous(self):
        reader = migration.D1Reader("account", "database", "token")
        with patch.object(migration, "urlopen", side_effect=HTTPError("url", 500, "server error", {}, None)) as call:
            with self.assertRaisesRegex(migration.MigrationError, "automatic retry was not attempted"):
                reader.query("SELECT security_code FROM stock_analysis_state")
        self.assertEqual(call.call_count, 1)

    def test_kv_network_error_is_reported_without_masking_name_error(self):
        with tempfile.TemporaryDirectory() as temp:
            migrator = migration.Migrator(self.make_args(temp), FakeReader(), FakeR2(), "namespace")
            with patch.dict("os.environ", {"CLOUDFLARE_ACCOUNT_ID": "account"}), patch.object(
                migration, "urlopen", side_effect=URLError("network unavailable")
            ):
                with self.assertRaisesRegex(migration.MigrationError, "KV definition read failed"):
                    migrator.read_kv("backend-config:v1")

    def test_page_read_stops_when_cloudflare_read_count_exceeds_budget(self):
        with tempfile.TemporaryDirectory() as temp:
            migrator = migration.Migrator(
                self.make_args(temp, max_rows=1),
                FakeReader({"stock_analysis_state": [{"security_code": "7203"}]}, reported_rows=2),
                FakeR2(),
                "namespace",
            )
            with self.assertRaises(migration.MigrationError):
                migrator.read_page("SELECT security_code FROM stock_analysis_state", [])
            self.assertEqual(migrator.report["rows_read"], 2)
            self.assertEqual(migrator.report["source_rows_returned"], 1)
            self.assertTrue(migrator.report["budget_exceeded"])
            self.assertEqual(migrator.report["d1_queries_attempted"], 1)

    def test_report_hash_is_recomputed_after_repeated_checkpoint_saves(self):
        with tempfile.TemporaryDirectory() as temp:
            migrator = migration.Migrator(self.make_args(temp), FakeReader(), FakeR2(), "namespace")
            migrator.report["status"] = "incomplete"
            migrator.save_outputs()
            first = json.loads(Path(migrator.args.report).read_text(encoding="utf-8"))
            first_hash = first.pop("report_sha256")
            self.assertEqual(digest(migration.canonical_json(first)).hexdigest(), first_hash)

            migrator.report["rows_read"] = 5
            migrator.report["complete"] = True
            migrator.report["status"] = "complete"
            migrator.save_outputs()
            final = json.loads(Path(migrator.args.report).read_text(encoding="utf-8"))
            final_hash = final.pop("report_sha256")
            self.assertEqual(digest(migration.canonical_json(final)).hexdigest(), final_hash)
            self.assertNotEqual(first_hash, final_hash)

    def test_missing_read_metadata_reports_returned_rows_as_uncertain_lower_bound(self):
        with tempfile.TemporaryDirectory() as temp:
            migrator = migration.Migrator(
                self.make_args(temp),
                FakeReader({"stock_analysis_state": [{"security_code": "7203"}]}, reported_rows="missing"),
                FakeR2(),
                "namespace",
            )
            with self.assertRaises(migration.MigrationError):
                migrator.read_page("SELECT security_code FROM stock_analysis_state", [])
            self.assertTrue(migrator.report["d1_read_count_unknown"])
            self.assertEqual(migrator.report["d1_rows_returned_without_meta"], 1)
            self.assertEqual(migrator.report["rows_read"], 0)

    def test_join_audit_reserves_two_d1_rows_for_each_result(self):
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader()
            migrator = migration.Migrator(self.make_args(temp, max_rows=5, batch_size=5), reader, FakeR2(), "namespace")
            migrator.page_loop(
                "SELECT fp.rowid AS migration_rowid FROM financial_periods fp LEFT JOIN securities s ON s.security_code = fp.security_code WHERE fp.rowid > ? ORDER BY fp.rowid LIMIT ?",
                0,
                "migration_rowid",
                lambda row: None,
                max_rows_per_result=2,
            )
            self.assertEqual(reader.queries[0][1], [0, 2])

    def test_join_audit_does_not_query_when_remaining_budget_cannot_cover_a_row(self):
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader()
            migrator = migration.Migrator(self.make_args(temp, max_rows=5), reader, FakeR2(), "namespace")
            migrator.report["rows_read"] = 4
            migrator.page_loop(
                "SELECT fp.rowid AS migration_rowid FROM financial_periods fp LEFT JOIN securities s ON s.security_code = fp.security_code WHERE fp.rowid > ? ORDER BY fp.rowid LIMIT ?",
                0,
                "migration_rowid",
                lambda row: None,
                max_rows_per_result=2,
            )
            self.assertEqual(reader.queries, [])
            self.assertFalse(migrator.report["complete"])

    def test_main_saves_failure_report_after_uncertain_d1_read(self):
        class FailingReader:
            def query(self, sql, params=None):
                raise migration.MigrationError("D1 read outcome is uncertain")

        with tempfile.TemporaryDirectory() as temp:
            report_path = Path(temp) / "report.json"
            checkpoint_path = Path(temp) / "checkpoint.json"
            env = {
                "CLOUDFLARE_ACCOUNT_ID": "account",
                "CLOUDFLARE_API_TOKEN": "ANALYTICS_TOKEN_SENTINEL",
                "CLOUDFLARE_MIGRATION_READ_TOKEN": "D1_READ_TOKEN_SENTINEL",
                "R2_ACCESS_KEY_ID": "R2_ACCESS_TOKEN_SENTINEL",
                "R2_SECRET_ACCESS_KEY": "R2_SECRET_TOKEN_SENTINEL",
            }
            argv = [
                "migrate_d1_to_r2.py", "--phase", "analysis-current",
                "--checkpoint-in", str(checkpoint_path), "--checkpoint-out", str(checkpoint_path),
                "--report", str(report_path),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": [{"id": "namespace"}]}
            usage_report = {
                "utc_date": "2026-11-04", "queried_at_utc": "2026-11-04T09:00:00+00:00",
                "rows_read": 100, "rows_written": 0, "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000, "limit_exceeded": False,
            }
            with (
                patch("sys.argv", argv),
                patch.dict("os.environ", env, clear=True),
                patch.object(migration, "load_config", return_value=config),
                patch.object(
                    migration.d1_daily_usage,
                    "fetch_budget_usage",
                    return_value=budget_usage_report(usage_report),
                ),
                patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=usage_report),
                patch.object(migration, "D1Reader", return_value=FailingReader()),
                patch.object(migration, "R2Store", return_value=FakeR2()),
            ):
                self.assertEqual(migration.main(), 1)
            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "failed")
            self.assertEqual(report["d1_queries_attempted"], 1)
            self.assertTrue(report["d1_read_count_unknown"])
            self.assertEqual(report["failure"]["message"], "D1 read outcome is uncertain")
            artifacts = report_path.read_text(encoding="utf-8") + checkpoint_path.read_text(encoding="utf-8")
            for token in (
                "ANALYTICS_TOKEN_SENTINEL",
                "D1_READ_TOKEN_SENTINEL",
                "R2_ACCESS_TOKEN_SENTINEL",
                "R2_SECRET_TOKEN_SENTINEL",
            ):
                with self.subTest(token=token):
                    self.assertNotIn(token, artifacts)
            self.assertEqual(report["account_daily_usage_postflight"]["status"], "d1_read_count_unknown")

    def test_account_usage_postflight_reconciles_target_and_reports_other_database_usage(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {
            "utc_date": utc_date,
            "rows_read": 100,
            "effective_rows_read": 120,
            "rows_written": 10,
            "per_database": [
                {"database_id": "db-a", "rows_read": 70, "rows_written": 4},
                {"database_id": "db-b", "rows_read": 30, "rows_written": 6},
            ],
        }
        postflight = {
            "utc_date": utc_date,
            "queried_at_utc": "2026-11-14T08:00:00+00:00",
            "rows_read": 140,
            "rows_written": 15,
            "rows_remaining": 4_999_860,
            "daily_limit_rows": 5_000_000,
            "per_database": [
                {"database_id": "db-a", "rows_read": 85, "rows_written": 5},
                {"database_id": "db-b", "rows_read": 55, "rows_written": 10},
            ],
        }
        migration_report = {
            "rows_read": 15,
            "d1_read_count_unknown": False,
            "d1_database_id": "db-a",
        }

        with patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=postflight):
            report = migration.account_daily_usage_postflight("account", preflight, migration_report)

        self.assertEqual(report["status"], "reconciled")
        self.assertEqual(report["account_rows_read_delta"], 40)
        self.assertEqual(report["migration_meta_rows_read"], 15)
        self.assertEqual(report["account_vs_migration_meta_rows_read_delta"], 25)
        self.assertEqual(report["target_database_rows_read_delta"], 15)
        self.assertEqual(report["other_databases_rows_read_delta"], 25)
        self.assertEqual(report["target_database_vs_migration_meta_rows_read_delta"], 0)
        self.assertTrue(report["per_database_delta_matches_account_delta"])
        self.assertEqual(report["effective_account_rows_read"], 160)
        self.assertEqual(report["effective_rows_remaining"], 4_999_840)
        self.assertEqual(report["per_database_delta"], [
            {"database_id": "db-a", "rows_read_delta": 15, "rows_written_delta": 1},
            {"database_id": "db-b", "rows_read_delta": 25, "rows_written_delta": 4},
        ])

    def test_account_usage_preflight_gates_on_effective_day_to_date_total(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        daily = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T12:00:00+00:00",
            "rows_read": 4_700_000,
            "rows_written": 0,
            "rows_remaining": 300_000,
            "daily_limit_rows": 5_000_000,
            "limit_exceeded": False,
            "per_database": [{"database_id": "db-a", "rows_read": 4_700_000, "rows_written": 0}],
        }
        effective = budget_usage_report(daily, 4_950_000)
        args = Namespace(max_rows=100_000, read_reserve_rows=100_000)

        with (
            patch.dict("os.environ", {"CLOUDFLARE_API_TOKEN": "analytics-token"}),
            patch.object(migration.d1_daily_usage, "fetch_budget_usage", return_value=effective),
        ):
            with self.assertRaisesRegex(migration.MigrationError, "insufficient remaining allowance"):
                migration.account_daily_usage_preflight("account", args)

    def test_account_usage_preflight_preserves_daily_delta_baseline_and_effective_budget(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        daily = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T12:00:00+00:00",
            "rows_read": 4_700_000,
            "rows_written": 5,
            "rows_remaining": 300_000,
            "daily_limit_rows": 5_000_000,
            "limit_exceeded": False,
            "per_database": [{"database_id": "db-a", "rows_read": 4_700_000, "rows_written": 5}],
        }
        effective = budget_usage_report(daily, 4_800_000)
        effective["budget_usage_queried_at_utc"] = f"{utc_date}T12:00:10+00:00"
        args = Namespace(max_rows=100_000, read_reserve_rows=100_000)

        with (
            patch.dict("os.environ", {"CLOUDFLARE_API_TOKEN": "analytics-token"}),
            patch.object(migration.d1_daily_usage, "fetch_budget_usage", return_value=effective),
        ):
            report = migration.account_daily_usage_preflight("account", args)

        self.assertEqual(report["rows_read"], 4_700_000)
        self.assertEqual(report["effective_rows_read"], 4_800_000)
        self.assertEqual(report["rows_remaining"], 200_000)
        self.assertEqual(report["queried_at_utc"], f"{utc_date}T12:00:10+00:00")
        self.assertEqual(report["per_database"][0]["rows_read"], 4_700_000)

    def test_account_usage_postflight_rejects_target_database_residual(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {
            "utc_date": utc_date,
            "rows_read": 100,
            "rows_written": 10,
            "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 10}],
        }
        postflight = {
            "utc_date": utc_date,
            "queried_at_utc": "2026-11-14T08:00:00+00:00",
            "rows_read": 125,
            "rows_written": 10,
            "rows_remaining": 4_999_875,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": "db-a", "rows_read": 125, "rows_written": 10}],
        }

        with patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=postflight):
            report = migration.account_daily_usage_postflight(
                "account", preflight, {"rows_read": 20, "d1_read_count_unknown": False, "d1_database_id": "db-a"}
            )

        self.assertEqual(report["status"], "unreconciled")
        self.assertEqual(report["target_database_vs_migration_meta_rows_read_delta"], 5)

    def test_account_usage_postflight_marks_matching_counts_reconciled(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {
            "utc_date": utc_date,
            "rows_read": 100,
            "rows_written": 8,
            "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 8}],
        }
        postflight = {
            "utc_date": utc_date,
            "queried_at_utc": "2026-11-14T08:00:00+00:00",
            "rows_read": 120,
            "rows_written": 8,
            "rows_remaining": 4_999_880,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": "db-a", "rows_read": 120, "rows_written": 8}],
        }

        with patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=postflight):
            report = migration.account_daily_usage_postflight(
                "account", preflight, {"rows_read": 20, "d1_read_count_unknown": False}
            )

        self.assertEqual(report["status"], "reconciled")
        self.assertEqual(report["account_vs_migration_meta_rows_read_delta"], 0)

    def test_account_usage_postflight_uses_exact_minute_groups_when_daily_analytics_lags(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        target_database_id = "11111111-1111-4111-8111-111111111111"
        other_database_id = "22222222-2222-4222-8222-222222222222"
        preflight = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T20:10:30+00:00",
            "rows_read": 100,
            "effective_rows_read": 140,
            "rows_written": 0,
            "rows_remaining": 4_999_900,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": target_database_id, "rows_read": 100, "rows_written": 0}],
        }
        stale_daily = {
            **preflight,
            "queried_at_utc": f"{utc_date}T20:23:00+00:00",
        }
        migration_report = {
            "rows_read": 480,
            "d1_queries_attempted": 5,
            "d1_read_count_unknown": False,
            "d1_database_id": target_database_id,
            "started_at": f"{utc_date}T20:18:04+00:00",
            "finished_at": f"{utc_date}T20:22:33+00:00",
            "read_reserve_rows": 100_000,
        }
        minute_usage = {
            "start_utc": f"{utc_date}T20:10:00Z",
            "end_utc": f"{utc_date}T20:23:00Z",
            "rows_read": 495,
            "read_queries": 7,
            "analytics_groups": 3,
            "confidence_bounds_verified": True,
            "per_database": [
                {"database_id": target_database_id, "rows_read": 480, "read_queries": 5},
                {"database_id": other_database_id, "rows_read": 15, "read_queries": 2},
            ],
            "per_minute": [
                {
                    "datetime_minute": f"{utc_date}T20:11:00Z",
                    "database_id": other_database_id,
                    "rows_read": 10,
                    "read_queries": 1,
                    "rows_written": 0,
                },
                {
                    "datetime_minute": f"{utc_date}T20:19:00Z",
                    "database_id": target_database_id,
                    "rows_read": 480,
                    "read_queries": 5,
                    "rows_written": 0,
                },
                {
                    "datetime_minute": f"{utc_date}T20:20:00Z",
                    "database_id": other_database_id,
                    "rows_read": 5,
                    "read_queries": 1,
                    "rows_written": 0,
                },
            ],
        }

        with (
            patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=stale_daily),
            patch.object(migration.d1_daily_usage, "fetch_minute_usage", return_value=minute_usage) as fetch_minute,
        ):
            report = migration.account_daily_usage_postflight("account", preflight, migration_report)

        self.assertEqual(report["status"], "reconciled")
        self.assertEqual(report["usage_source"], "daily_and_minute_window_observed")
        self.assertEqual(report["target_database_rows_read_delta"], 0)
        self.assertEqual(report["effective_account_rows_read"], 635)
        self.assertEqual(report["rows_remaining"], 4_999_365)
        self.assertEqual(report["minute_fallback"]["status"], "reconciled")
        self.assertEqual(report["minute_fallback"]["target_database_rows_read"], 480)
        self.assertEqual(report["minute_fallback"]["target_database_read_queries"], 5)
        self.assertTrue(report["minute_fallback"]["confidence_bounds_verified"])
        self.assertEqual(report["minute_fallback"]["other_databases_rows_read"], 5)
        fetch_minute.assert_called_once()
        call_args, call_kwargs = fetch_minute.call_args
        self.assertEqual(call_args[0], "account")
        self.assertEqual(call_args[2:], (preflight["queried_at_utc"], stale_daily["queried_at_utc"]))
        self.assertEqual(call_kwargs, {"timeout_seconds": migration.POSTFLIGHT_ANALYTICS_TIMEOUT_SECONDS})

    def test_minute_fallback_fails_closed_on_extra_target_database_activity(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        target_database_id = "11111111-1111-4111-8111-111111111111"
        preflight = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T20:10:30+00:00",
            "rows_read": 100,
            "rows_written": 0,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": target_database_id, "rows_read": 100, "rows_written": 0}],
        }
        stale_daily = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T20:23:00+00:00",
            "rows_read": 100,
            "rows_written": 0,
            "rows_remaining": 4_999_900,
            "daily_limit_rows": 5_000_000,
            "limit_exceeded": False,
            "per_database": [{"database_id": target_database_id, "rows_read": 100, "rows_written": 0}],
        }
        migration_report = {
            "rows_read": 480,
            "d1_queries_attempted": 5,
            "d1_read_count_unknown": False,
            "d1_database_id": target_database_id,
            "started_at": f"{utc_date}T20:18:04+00:00",
            "finished_at": f"{utc_date}T20:22:33+00:00",
            "read_reserve_rows": 100_000,
        }
        minute_usage = {
            "start_utc": f"{utc_date}T20:10:00Z",
            "end_utc": f"{utc_date}T20:23:00Z",
            "rows_read": 481,
            "read_queries": 6,
            "analytics_groups": 1,
            "confidence_bounds_verified": True,
            "per_database": [{"database_id": target_database_id, "rows_read": 481, "read_queries": 6}],
            "per_minute": [{
                "datetime_minute": f"{utc_date}T20:19:00Z",
                "database_id": target_database_id,
                "rows_read": 481,
                "read_queries": 6,
                "rows_written": 0,
            }],
        }

        with (
            patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=stale_daily),
            patch.object(migration.d1_daily_usage, "fetch_minute_usage", return_value=minute_usage),
        ):
            report = migration.account_daily_usage_postflight("account", preflight, migration_report)

        self.assertEqual(report["status"], "unreconciled")
        self.assertEqual(report["minute_fallback"]["status"], "target_activity_mismatch")

    def test_postflight_retries_until_minute_analytics_contains_the_migration(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        target_database_id = "11111111-1111-4111-8111-111111111111"
        preflight = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T20:10:30+00:00",
            "rows_read": 100,
            "rows_written": 0,
            "per_database": [{"database_id": target_database_id, "rows_read": 100, "rows_written": 0}],
        }
        stale_daily_reports = [
            {
                "utc_date": utc_date,
                "queried_at_utc": f"{utc_date}T20:23:0{second}+00:00",
                "rows_read": 100,
                "rows_written": 0,
                "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000,
                "limit_exceeded": False,
                "per_database": [{"database_id": target_database_id, "rows_read": 100, "rows_written": 0}],
            }
            for second in (0, 1)
        ]
        migration_report = {
            "rows_read": 5,
            "d1_queries_attempted": 1,
            "d1_database_id": target_database_id,
            "d1_read_count_unknown": False,
            "started_at": f"{utc_date}T20:18:04+00:00",
            "finished_at": f"{utc_date}T20:22:33+00:00",
            "read_reserve_rows": 100_000,
        }
        minute_reports = [
            {
                "start_utc": f"{utc_date}T20:10:00Z",
                "end_utc": f"{utc_date}T20:23:00Z",
                "rows_read": 0,
                "read_queries": 0,
                "analytics_groups": 0,
                "confidence_bounds_verified": False,
                "per_database": [],
                "per_minute": [],
            },
            {
                "start_utc": f"{utc_date}T20:10:00Z",
                "end_utc": f"{utc_date}T20:23:00Z",
                "rows_read": 5,
                "read_queries": 1,
                "analytics_groups": 1,
                "confidence_bounds_verified": True,
                "per_database": [{"database_id": target_database_id, "rows_read": 5, "read_queries": 1}],
                "per_minute": [{
                    "datetime_minute": f"{utc_date}T20:19:00Z",
                    "database_id": target_database_id,
                    "rows_read": 5,
                    "read_queries": 1,
                    "rows_written": 0,
                }],
            },
        ]
        migrator = types.SimpleNamespace(
            report={"account_daily_usage_preflight": preflight, **migration_report},
            save_outputs=lambda: None,
        )

        with (
            patch.object(
                migration.d1_daily_usage,
                "fetch_daily_usage",
                side_effect=stale_daily_reports,
            ) as fetch_daily,
            patch.object(
                migration.d1_daily_usage,
                "fetch_minute_usage",
                side_effect=minute_reports,
            ) as fetch_minute,
            patch.object(migration.time, "sleep") as sleep,
        ):
            result = migration.record_account_daily_usage_postflight(migrator, "account")

        self.assertEqual(result["status"], "reconciled")
        self.assertEqual(result["attempt"], 2)
        self.assertEqual(result["minute_fallback"]["target_database_rows_read"], 5)
        self.assertEqual(fetch_daily.call_count, 2)
        self.assertEqual(fetch_minute.call_count, 2)
        sleep.assert_called_once_with(migration.POSTFLIGHT_RETRY_INTERVAL_SECONDS)

    def test_minute_fallback_includes_other_databases_in_reserve_check(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        target_database_id = "11111111-1111-4111-8111-111111111111"
        other_database_id = "22222222-2222-4222-8222-222222222222"
        preflight = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T20:10:30+00:00",
            "rows_read": 4_899_990,
            "rows_written": 0,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": target_database_id, "rows_read": 4_899_990, "rows_written": 0}],
        }
        stale_daily = {
            "utc_date": utc_date,
            "queried_at_utc": f"{utc_date}T20:23:00+00:00",
            "rows_read": 4_899_990,
            "rows_written": 0,
            "rows_remaining": 100_010,
            "daily_limit_rows": 5_000_000,
            "limit_exceeded": False,
            "per_database": [{"database_id": target_database_id, "rows_read": 4_899_990, "rows_written": 0}],
        }
        migration_report = {
            "rows_read": 5,
            "d1_queries_attempted": 1,
            "d1_read_count_unknown": False,
            "d1_database_id": target_database_id,
            "started_at": f"{utc_date}T20:18:04+00:00",
            "finished_at": f"{utc_date}T20:22:33+00:00",
            "read_reserve_rows": 100_000,
        }
        minute_usage = {
            "start_utc": f"{utc_date}T20:10:00Z",
            "end_utc": f"{utc_date}T20:23:00Z",
            "rows_read": 20,
            "read_queries": 2,
            "analytics_groups": 2,
            "confidence_bounds_verified": True,
            "per_database": [
                {"database_id": target_database_id, "rows_read": 5, "read_queries": 1},
                {"database_id": other_database_id, "rows_read": 15, "read_queries": 1},
            ],
            "per_minute": [
                {
                    "datetime_minute": f"{utc_date}T20:19:00Z",
                    "database_id": target_database_id,
                    "rows_read": 5,
                    "read_queries": 1,
                    "rows_written": 0,
                },
                {
                    "datetime_minute": f"{utc_date}T20:20:00Z",
                    "database_id": other_database_id,
                    "rows_read": 15,
                    "read_queries": 1,
                    "rows_written": 0,
                },
            ],
        }

        with (
            patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=stale_daily),
            patch.object(migration.d1_daily_usage, "fetch_minute_usage", return_value=minute_usage),
        ):
            report = migration.account_daily_usage_postflight("account", preflight, migration_report)

        self.assertEqual(report["status"], "reserve_breached")
        self.assertFalse(report["reserve_satisfied"])
        self.assertEqual(report["effective_account_rows_read"], 4_900_010)
        self.assertEqual(report["effective_rows_remaining"], 99_990)

    def test_account_usage_postflight_does_not_compare_across_utc_date_boundary(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {"utc_date": "2026-11-13", "rows_read": 100, "rows_written": 8, "per_database": []}
        postflight = {
            "utc_date": utc_date,
            "queried_at_utc": "2026-11-14T08:00:00+00:00",
            "rows_read": 5,
            "rows_written": 1,
            "rows_remaining": 4_999_995,
            "daily_limit_rows": 5_000_000,
            "per_database": [],
        }

        with patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=postflight):
            report = migration.account_daily_usage_postflight(
                "account", preflight, {"rows_read": 20, "d1_read_count_unknown": False}
            )

        self.assertEqual(report["status"], "utc_date_changed")
        self.assertNotIn("account_rows_read_delta", report)

    def test_postflight_waits_for_analytics_lag_then_reconciles_without_rerunning_d1(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {
            "utc_date": utc_date,
            "rows_read": 100,
            "rows_written": 10,
            "rows_remaining": 4_999_900,
            "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 10}],
        }
        delayed_reports = [
            {
                "utc_date": utc_date,
                "queried_at_utc": "2026-11-14T08:00:00+00:00",
                "rows_read": 100,
                "rows_written": 10,
                "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000,
                "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 10}],
            },
            {
                "utc_date": utc_date,
                "queried_at_utc": "2026-11-14T08:00:15+00:00",
                "rows_read": 105,
                "rows_written": 10,
                "rows_remaining": 4_999_895,
                "daily_limit_rows": 5_000_000,
                "per_database": [{"database_id": "db-a", "rows_read": 105, "rows_written": 10}],
            },
        ]
        saved = []
        migrator = types.SimpleNamespace(
            report={
                "account_daily_usage_preflight": preflight,
                "rows_read": 5,
                "d1_database_id": "db-a",
                "d1_read_count_unknown": False,
                "read_reserve_rows": 100_000,
            },
            save_outputs=lambda: saved.append(True),
        )

        with (
            patch.object(migration.d1_daily_usage, "fetch_daily_usage", side_effect=delayed_reports) as fetch,
            patch.object(migration.time, "sleep") as sleep,
        ):
            result = migration.record_account_daily_usage_postflight(migrator, "account")

        self.assertEqual(result["status"], "reconciled")
        self.assertEqual(result["attempt"], 2)
        self.assertEqual(fetch.call_count, 2)
        sleep.assert_called_once_with(migration.POSTFLIGHT_RETRY_INTERVAL_SECONDS)
        self.assertEqual(len(saved), 2)
        self.assertEqual(
            [item["target_database_rows_read_delta"] for item in migrator.report["account_daily_usage_postflight_attempts"]],
            [0, 5],
        )

    def test_postflight_deadline_stops_retries_and_records_unreconciled_state(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {
            "utc_date": utc_date,
            "rows_read": 100,
            "rows_written": 10,
            "rows_remaining": 4_999_900,
            "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 10}],
        }
        undercounted = {
            "utc_date": utc_date,
            "queried_at_utc": "2026-11-14T08:00:00+00:00",
            "rows_read": 100,
            "rows_written": 10,
            "rows_remaining": 4_999_900,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 10}],
        }
        saved = []
        migrator = types.SimpleNamespace(
            report={
                "account_daily_usage_preflight": preflight,
                "rows_read": 5,
                "d1_database_id": "db-a",
                "d1_read_count_unknown": False,
                "read_reserve_rows": 100_000,
            },
            save_outputs=lambda: saved.append(True),
        )

        with (
            patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=undercounted) as fetch,
            patch.object(migration.time, "monotonic", side_effect=[0.0, 0.0, 290.0, 300.0]),
            patch.object(migration.time, "sleep") as sleep,
        ):
            result = migration.record_account_daily_usage_postflight(migrator, "account")

        self.assertEqual(result["status"], "unreconciled")
        self.assertEqual(result["attempt"], 1)
        self.assertTrue(result["postflight_deadline_exceeded"])
        self.assertTrue(migrator.report["account_daily_usage_postflight_deadline_exceeded"])
        fetch.assert_called_once()
        self.assertEqual(fetch.call_args.kwargs["timeout_seconds"], migration.POSTFLIGHT_ANALYTICS_TIMEOUT_SECONDS)
        sleep.assert_called_once_with(10.0)
        self.assertEqual(len(saved), 2)

    def test_postflight_does_not_retry_when_analytics_rows_exceed_query_metadata(self):
        utc_date = datetime.now(timezone.utc).date().isoformat()
        preflight = {
            "utc_date": utc_date,
            "rows_read": 100,
            "rows_written": 10,
            "rows_remaining": 4_999_900,
            "per_database": [{"database_id": "db-a", "rows_read": 100, "rows_written": 10}],
        }
        postflight = {
            "utc_date": utc_date,
            "queried_at_utc": "2026-11-14T08:00:00+00:00",
            "rows_read": 106,
            "rows_written": 10,
            "rows_remaining": 4_999_894,
            "daily_limit_rows": 5_000_000,
            "per_database": [{"database_id": "db-a", "rows_read": 106, "rows_written": 10}],
        }
        migrator = types.SimpleNamespace(
            report={
                "account_daily_usage_preflight": preflight,
                "rows_read": 5,
                "d1_database_id": "db-a",
                "d1_read_count_unknown": False,
                "read_reserve_rows": 100_000,
            },
            save_outputs=lambda: None,
        )

        with (
            patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=postflight) as fetch,
            patch.object(migration.time, "sleep") as sleep,
        ):
            result = migration.record_account_daily_usage_postflight(migrator, "account")

        self.assertEqual(result["status"], "unreconciled")
        self.assertEqual(result["target_database_vs_migration_meta_rows_read_delta"], 1)
        fetch.assert_called_once()
        sleep.assert_not_called()

    def test_main_returns_nonzero_when_account_usage_postflight_is_unreconciled(self):
        with tempfile.TemporaryDirectory() as temp:
            report_path = Path(temp) / "report.json"
            checkpoint_path = Path(temp) / "checkpoint.json"
            argv = [
                "migrate_d1_to_r2.py", "--phase", "d1-schema-audit", "--dry-run",
                "--checkpoint-in", str(Path(temp) / "missing.json"),
                "--checkpoint-out", str(checkpoint_path), "--report", str(report_path),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": []}
            usage_report = {
                "utc_date": datetime.now(timezone.utc).date().isoformat(),
                "queried_at_utc": "2026-11-14T08:00:00+00:00",
                "rows_read": 100,
                "rows_written": 10,
                "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000,
                "limit_exceeded": False,
                "per_database": [],
            }
            postflight_report = {**usage_report, "rows_read": 101}
            with (
                patch("sys.argv", argv),
                patch.dict("os.environ", {
                    "CLOUDFLARE_ACCOUNT_ID": "account",
                    "CLOUDFLARE_API_TOKEN": "analytics-token",
                    "CLOUDFLARE_MIGRATION_READ_TOKEN": "d1-read-token",
                }, clear=True),
                patch.object(migration, "load_config", return_value=config),
                patch.object(
                    migration.d1_daily_usage,
                    "fetch_budget_usage",
                    return_value=budget_usage_report(usage_report),
                ),
                patch.object(
                    migration.d1_daily_usage,
                    "fetch_daily_usage",
                    return_value=postflight_report,
                ),
                patch.object(migration, "D1Reader", return_value=FakeReader({"sqlite_schema": []})),
                patch.object(migration, "R2Store", side_effect=AssertionError("R2 must not be constructed")),
            ):
                self.assertEqual(migration.main(), 3)

            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertTrue(report["complete"])
            self.assertEqual(report["status"], "complete")
            self.assertEqual(report["account_daily_usage_postflight"]["status"], "per_database_delta_mismatch")
            report_hash = report.pop("report_sha256")
            self.assertEqual(digest(migration.canonical_json(report)).hexdigest(), report_hash)

    def test_main_fails_closed_when_analytics_token_is_missing_before_d1_client(self):
        with tempfile.TemporaryDirectory() as temp:
            argv = [
                "migrate_d1_to_r2.py", "--phase", "d1-schema-audit",
                "--checkpoint-in", str(Path(temp) / "missing.json"),
                "--checkpoint-out", str(Path(temp) / "checkpoint.json"),
                "--report", str(Path(temp) / "report.json"),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": []}
            env = {
                "CLOUDFLARE_ACCOUNT_ID": "8d8f4f1f3c53232aaf7c7d6dd7b33944",
                "CLOUDFLARE_MIGRATION_READ_TOKEN": "d1-read-only-token",
            }
            with (
                patch("sys.argv", argv),
                patch.dict("os.environ", env, clear=True),
                patch.object(migration, "load_config", return_value=config),
                patch.object(migration, "D1Reader") as reader_factory,
                patch.object(migration.d1_daily_usage, "fetch_budget_usage", wraps=migration.d1_daily_usage.fetch_budget_usage) as usage_query,
            ):
                self.assertEqual(migration.main(), 1)
            usage_query.assert_called_once()
            self.assertEqual(usage_query.call_args.args[1], "")
            reader_factory.assert_not_called()

    def test_main_fails_closed_when_account_usage_leaves_insufficient_budget(self):
        with tempfile.TemporaryDirectory() as temp:
            fixed_now = datetime(2026, 11, 4, 9, 0, tzinfo=timezone.utc)

            class FixedDateTime(datetime):
                @classmethod
                def now(cls, tz=None):
                    return fixed_now if tz is None else fixed_now.astimezone(tz)

            argv = [
                "migrate_d1_to_r2.py", "--phase", "d1-schema-audit",
                "--checkpoint-in", str(Path(temp) / "missing.json"),
                "--checkpoint-out", str(Path(temp) / "checkpoint.json"),
                "--report", str(Path(temp) / "report.json"),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": []}
            env = {
                "CLOUDFLARE_ACCOUNT_ID": "8d8f4f1f3c53232aaf7c7d6dd7b33944",
                "CLOUDFLARE_API_TOKEN": "analytics-read-only-token",
                "CLOUDFLARE_MIGRATION_READ_TOKEN": "d1-read-only-token",
            }
            usage_report = {
                "utc_date": fixed_now.date().isoformat(), "queried_at_utc": fixed_now.isoformat(),
                "rows_read": 4_999_900, "rows_remaining": 100,
                "daily_limit_rows": 5_000_000, "limit_exceeded": False,
            }
            with (
                patch("sys.argv", argv),
                patch.dict("os.environ", env, clear=True),
                patch.object(migration, "datetime", FixedDateTime),
                patch.object(migration, "load_config", return_value=config),
                patch.object(
                    migration.d1_daily_usage,
                    "fetch_budget_usage",
                    return_value=budget_usage_report(usage_report),
                ) as usage_query,
                patch.object(migration, "D1Reader") as reader_factory,
            ):
                self.assertEqual(migration.main(), 1)
            usage_query.assert_called_once_with(
                env["CLOUDFLARE_ACCOUNT_ID"], env["CLOUDFLARE_API_TOKEN"], fixed_now.date().isoformat()
            )
            reader_factory.assert_not_called()

    def test_exhausted_budget_does_not_issue_another_d1_query(self):
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader()
            migrator = migration.Migrator(self.make_args(temp, max_rows=1), reader, FakeR2(), "namespace")
            migrator.report["rows_read"] = 1
            with self.assertRaises(migration.MigrationError):
                migrator.read_page("SELECT security_code FROM stock_analysis_state", [])
            self.assertEqual(reader.queries, [])

    def test_immutable_objects_are_idempotent_and_conflicts_are_reported(self):
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2()
            migrator = migration.Migrator(self.make_args(temp), FakeReader(), r2, "namespace")
            migrator.put_immutable("immutable.json", b'{"a":1}')
            self.assertEqual(migrator.report["objects_planned"], 1)
            self.assertEqual(r2.objects, {})

            migrator.args.dry_run = False
            migrator.put_immutable("immutable.json", b'{"a":1}')
            self.assertEqual(migrator.report["objects_written"], 1)
            migrator.put_immutable("immutable.json", b'{"a":1}')
            self.assertEqual(migrator.report["objects_unchanged"], 1)
            with self.assertRaises(migration.MigrationError):
                migrator.put_immutable("immutable.json", b'{"a":2}')
            self.assertEqual(migrator.report["conflicts"], 1)

    def test_complete_definition_plan_digest_matches_empty_store_write_and_readback(self):
        definitions = {
            key: {"version": 1, "private_payload": f"definition-{index}"}
            for index, key in enumerate(migration.DEFINITION_FILES)
        }
        with tempfile.TemporaryDirectory() as temp:
            dry_args = self.make_args(temp, phase="definitions", max_rows=20)
            dry_args.checkpoint_in = str(Path(temp) / "dry-checkpoint.json")
            dry_args.checkpoint_out = dry_args.checkpoint_in
            dry_args.report = str(Path(temp) / "dry-report.json")
            dry_run = migration.Migrator(dry_args, FakeReader(), FakeR2(), "namespace")
            with patch.object(dry_run, "read_kv", side_effect=lambda key: definitions[key]):
                planned = dry_run.run()

            write_args = self.make_args(temp, phase="definitions", dry_run=False, max_rows=20)
            write_args.checkpoint_in = str(Path(temp) / "write-checkpoint.json")
            write_args.checkpoint_out = write_args.checkpoint_in
            write_args.report = str(Path(temp) / "write-report.json")
            r2 = FakeR2()
            write_run = migration.Migrator(write_args, FakeReader(), r2, "namespace")
            with patch.object(write_run, "read_kv", side_effect=lambda key: definitions[key]):
                written = write_run.run()

            self.assertTrue(planned["complete"])
            self.assertTrue(planned["write_set_hash_complete"])
            self.assertEqual(planned["objects_planned"], 12)
            self.assertEqual(written["objects_written"], 12)
            self.assertEqual(planned["write_set_sha256"], written["write_set_sha256"])
            self.assertEqual(written["write_set_sha256"], written["r2_readback_sha256"])

            artifacts = Path(dry_args.report).read_text(encoding="utf-8")
            artifacts += Path(dry_args.checkpoint_out).read_text(encoding="utf-8")
            artifacts += Path(write_args.report).read_text(encoding="utf-8")
            artifacts += Path(write_args.checkpoint_out).read_text(encoding="utf-8")
            for key, value in definitions.items():
                version_key, pointer_key, body = migration.definition_object_keys(key, value)
                self.assertIn(version_key, r2.objects)
                self.assertIn(pointer_key, r2.objects)
                self.assertNotIn(version_key, artifacts)
                self.assertNotIn(pointer_key, artifacts)
                self.assertNotIn(body.decode("utf-8"), artifacts)

    def test_legacy_checkpoint_with_prior_outputs_marks_write_set_hash_incomplete(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="definitions")
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": "definitions",
                "table": "securities",
                "dry_run": True,
                "cursor": None,
                "rolling_hash": "a" * 64,
                "r2_readback_sha256": "0" * 64,
                "totals": {"objects_planned": 2, "objects_written": 0},
            }), encoding="utf-8")

            migrator = migration.Migrator(args, FakeReader(), FakeR2(), "namespace")
            self.assertFalse(migrator.write_set_hash_prefix_complete)
            migrator.report["complete"] = True
            migrator.report["status"] = "complete"
            migrator.save_outputs()

            report = json.loads(Path(args.report).read_text(encoding="utf-8"))
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertFalse(report["write_set_hash_complete"])
            self.assertFalse(checkpoint["write_set_hash_prefix_complete"])

    def test_analysis_history_dry_run_lists_once_and_skips_get_for_absent_keys(self):
        row = {
            "id": 1,
            "security_code": "7203",
            "observed_at": "2026-11-03T12:00:00Z",
            "market_date": "2026-11-03",
            "definition_version": 1,
            "overall_score": 70,
            "overall_grade": "B",
            "buy_zone_status": "watch",
            "buy_zone_ratio": 0.9,
            "anomaly_flags_json": "[]",
            "metrics_json": "{}",
            "analysis_json": "{}",
        }
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2()
            report = migration.Migrator(
                self.make_args(temp, phase="analysis-history", max_rows=2, batch_size=5),
                FakeReader({"stock_analysis_history": [row]}),
                r2,
                "namespace",
            ).run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["objects_planned"], 1)
            self.assertEqual(r2.list_calls, ["analysis/v1/history/"])
            self.assertEqual(r2.get_calls, [])

    def test_analysis_history_existing_objects_are_read_and_checked(self):
        row = {
            "id": 1,
            "security_code": "7203",
            "observed_at": "2026-11-03T12:00:00Z",
            "market_date": "2026-11-03",
            "definition_version": 1,
            "overall_score": 70,
            "overall_grade": "B",
            "buy_zone_status": "watch",
            "buy_zone_ratio": 0.9,
            "anomaly_flags_json": "[]",
            "metrics_json": "{}",
            "analysis_json": "{}",
        }
        body = migration.canonical_json({
            "observed_at": row["observed_at"],
            "market_date": row["market_date"],
            "definition_version": row["definition_version"],
            "overall_score": row["overall_score"],
            "overall_grade": row["overall_grade"],
            "buy_zone_status": row["buy_zone_status"],
            "buy_zone_ratio": row["buy_zone_ratio"],
            "anomaly_flags_json": row["anomaly_flags_json"],
            "metrics_json": row["metrics_json"],
            "analysis_json": row["analysis_json"],
        })
        key = migration.analysis_history_key(row)
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2({key: body})
            report = migration.Migrator(
                self.make_args(temp, phase="analysis-history", max_rows=2, batch_size=5),
                FakeReader({"stock_analysis_history": [row]}),
                r2,
                "namespace",
            ).run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["objects_unchanged"], 1)
            self.assertEqual(r2.list_calls, ["analysis/v1/history/"])
            self.assertEqual(r2.get_calls, [key])

    def test_analysis_history_write_keeps_conditional_create_and_readback(self):
        row = {
            "id": 1,
            "security_code": "7203",
            "observed_at": "2026-11-03T12:00:00Z",
            "market_date": "2026-11-03",
            "definition_version": 1,
            "overall_score": 70,
            "overall_grade": "B",
            "buy_zone_status": "watch",
            "buy_zone_ratio": 0.9,
            "anomaly_flags_json": "[]",
            "metrics_json": "{}",
            "analysis_json": "{}",
        }
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2()
            report = migration.Migrator(
                self.make_args(temp, phase="analysis-history", dry_run=False, max_rows=2, batch_size=5),
                FakeReader({"stock_analysis_history": [row]}),
                r2,
                "namespace",
            ).run()

            key = migration.analysis_history_key(row)
            self.assertTrue(report["complete"])
            self.assertEqual(report["objects_written"], 1)
            self.assertEqual(report["objects_verified"], 1)
            self.assertEqual(r2.list_calls, ["analysis/v1/history/"])
            self.assertEqual(r2.get_calls, [key])
            self.assertIn(key, r2.objects)

    def test_current_snapshot_keeps_newer_r2_and_rejects_same_time_conflicts(self):
        with tempfile.TemporaryDirectory() as temp:
            current_key = "analysis/v1/current/7203.json"
            newer = migration.canonical_json({"observed_at": "2026-11-02T00:00:00Z", "score": 90})
            r2 = FakeR2({current_key: newer})
            migrator = migration.Migrator(self.make_args(temp), FakeReader(), r2, "namespace")
            incoming = {"observed_at": "2026-11-01T00:00:00Z", "score": 80}
            migrator.put_analysis_current(current_key, migration.canonical_json(incoming), incoming)
            self.assertEqual(migrator.report["objects_skipped_newer"], 1)
            self.assertEqual(r2.get(current_key), newer)

            conflict = {"observed_at": "2026-11-02T00:00:00Z", "score": 81}
            with self.assertRaises(migration.MigrationError):
                migrator.put_analysis_current(current_key, migration.canonical_json(conflict), conflict)

    def test_current_snapshot_write_uses_etag_condition(self):
        with tempfile.TemporaryDirectory() as temp:
            current_key = "analysis/v1/current/7203.json"
            old = migration.canonical_json({"observed_at": "2026-11-01T00:00:00Z", "score": 80})
            newer = {"observed_at": "2026-11-02T00:00:00Z", "score": 90}
            r2 = FakeR2({current_key: old})
            migrator = migration.Migrator(self.make_args(temp, dry_run=False), FakeReader(), r2, "namespace")
            migrator.put_analysis_current(current_key, migration.canonical_json(newer), newer)
            self.assertEqual(json.loads(r2.get(current_key)), newer)
            self.assertEqual(migrator.report["objects_verified"], 1)

    def test_scheduled_run_migration_preserves_the_365_day_retention_window(self):
        rows = [
            {"id": 1, "run_type": "analysis", "started_at": "2025-01-01T00:00:00Z",
             "finished_at": "2025-01-01T00:01:00Z", "status": "success", "processed": 1,
             "failed": 0, "metadata_json": "{}"},
            {"id": 2, "run_type": "analysis", "started_at": "2026-11-01T00:00:00Z",
             "finished_at": "2026-11-01T00:01:00Z", "status": "success", "processed": 1,
             "failed": 0, "metadata_json": "{}"},
            {"id": 3, "run_type": "analysis", "started_at": "2026-11-02T00:00:00Z",
             "finished_at": None, "status": "running", "processed": 0,
             "failed": 0, "metadata_json": "{}"},
            {"id": 4, "run_type": "analysis", "started_at": "2026-10-31T00:00:00Z",
             "finished_at": "2026-10-31T00:01:00Z", "status": "success", "processed": 1,
             "failed": 0, "metadata_json": "{}"},
            {"id": 5, "run_type": "analysis", "started_at": "2026-11-01T00:00:00Z",
             "finished_at": "2026-11-01T00:01:00Z", "status": "success", "processed": 1,
             "failed": 0, "metadata_json": "{}"},
            {"id": 6, "run_type": "analysis", "started_at": "2026-11-03T00:00:00Z",
             "finished_at": "2026-11-03T00:01:00Z", "status": "running", "processed": 0,
             "failed": 0, "metadata_json": "{}"},
        ]
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader({"scheduled_run_log": rows})
            r2 = FakeR2()
            migrator = migration.Migrator(
                self.make_args(temp, phase="scheduled-runs", dry_run=False), reader, r2, "namespace"
            )

            report = migrator.run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["source_rows_returned"], 3)
            self.assertEqual(report["objects_written"], 4)
            query = next(sql for sql, _ in reader.queries if sql == migration.SCHEDULED_RUNS_QUERY)
            self.assertIn("datetime(started_at) >= ?", query)
            self.assertIn("status IN ('success', 'failed', 'partial', 'superseded')", query)
            self.assertIn("(datetime(started_at), id) > (?, ?)", query)
            self.assertIn("ORDER BY datetime(started_at), id LIMIT ?", query)
            self.assertTrue(report["scheduled_runs_index_plan_verified"])
            self.assertIn("operations/v1/runs/analysis/legacy-d1-2/summary.json", r2.objects)
            self.assertIn("operations/v1/runs/analysis/legacy-d1-4/summary.json", r2.objects)
            self.assertIn("operations/v1/runs/analysis/legacy-d1-5/summary.json", r2.objects)
            self.assertNotIn("operations/v1/runs/analysis/legacy-d1-1/summary.json", r2.objects)
            self.assertNotIn("operations/v1/runs/analysis/legacy-d1-6/summary.json", r2.objects)
            pointer = json.loads(r2.objects["operations/v1/runs/legacy-d1/current.json"])
            self.assertEqual(pointer["run_key"], "operations/v1/runs/analysis/legacy-d1-5/summary.json")
            self.assertEqual(pointer["run_id"], "5")
            checkpoint_text = Path(migrator.args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn('"run_key"', checkpoint_text)
            self.assertNotIn("operations/v1/runs/analysis/legacy-d1-5/summary.json", checkpoint_text)

    def test_scheduled_runs_query_uses_partial_expression_index_and_normalizes_cutoff_day(self):
        database = sqlite3.connect(":memory:")
        try:
            database.execute(
                "CREATE TABLE scheduled_run_log ("
                "id INTEGER PRIMARY KEY AUTOINCREMENT, run_type TEXT NOT NULL, started_at TEXT NOT NULL, "
                "finished_at TEXT, status TEXT NOT NULL, processed INTEGER NOT NULL DEFAULT 0, "
                "failed INTEGER NOT NULL DEFAULT 0, metadata_json TEXT NOT NULL DEFAULT '{}')"
            )
            migration_sql = (
                SCRIPT.parents[1] / "migrations" / "0009_scheduled_run_log_completed_started_index.sql"
            )
            database.executescript(migration_sql.read_text(encoding="utf-8"))
            database.executemany(
                "INSERT INTO scheduled_run_log(run_type, started_at, finished_at, status) VALUES (?, ?, ?, ?)",
                [("analysis", "2026-11-01T13:00:00Z", None, "running") for _ in range(783)]
                + [
                    ("analysis", "2026-11-01T11:59:59Z", "2026-11-01T12:00:00Z", "success"),
                    ("analysis", "2026-11-01 12:00:00", "2026-11-01 12:01:00", "success"),
                    ("analysis", "2026-11-01T12:30:00Z", "2026-11-01T12:31:00Z", "success"),
                    ("analysis", "2026-11-01T13:00:00+01:00", "2026-11-01T13:01:00+01:00", "success"),
                    ("analysis", "2026-11-01T12:45:00Z", "2026-11-01T12:46:00Z", "running"),
                ]
            )
            params = ("2026-11-01 12:00:00", "", 0, 10)
            plan = database.execute(
                f"EXPLAIN QUERY PLAN {migration.SCHEDULED_RUNS_QUERY}", params
            ).fetchall()
            self.assertTrue(any("idx_scheduled_run_log_completed_started_id" in row[-1] for row in plan))
            rows = database.execute(migration.SCHEDULED_RUNS_QUERY, params).fetchall()
            self.assertEqual([row[0] for row in rows], [785, 787, 786])
        finally:
            database.close()

    def test_scheduled_run_migration_accepts_fractional_timestamp_at_cutoff_second(self):
        row = {
            "id": 1, "run_type": "analysis", "started_at": "2025-11-15T12:00:00.500Z",
            "finished_at": "2025-11-15T12:01:00.000Z", "status": "success",
            "processed": 1, "failed": 0, "metadata_json": "{}",
        }
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2()
            migrator = migration.Migrator(
                self.make_args(temp, phase="scheduled-runs", dry_run=False),
                FakeReader({"scheduled_run_log": [row]}), r2, "namespace"
            )
            with patch.object(migration, "scheduled_runs_cutoff_utc", return_value="2025-11-15 12:00:00"):
                report = migrator.run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["source_rows_returned"], 1)
            self.assertIn("operations/v1/runs/analysis/legacy-d1-1/summary.json", r2.objects)

    def test_scheduled_runs_fails_closed_when_query_plan_does_not_use_partial_index(self):
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader(query_plan_detail="SCAN scheduled_run_log")
            migrator = migration.Migrator(
                self.make_args(temp, phase="scheduled-runs"), reader, FakeR2(), "namespace"
            )

            with self.assertRaisesRegex(
                migration.MigrationError, "does not use idx_scheduled_run_log_completed_started_id"
            ):
                migrator.run()

            self.assertEqual(len(reader.queries), 1)
            self.assertTrue(reader.queries[0][0].startswith("EXPLAIN QUERY PLAN"))

    def test_scheduled_run_pointer_is_not_published_until_the_phase_is_complete(self):
        rows = [
            {"id": 1, "run_type": "analysis", "started_at": "2026-11-01T00:00:00Z",
             "finished_at": "2026-11-01T00:01:00Z", "status": "success", "processed": 1,
             "failed": 0, "metadata_json": "{}"},
            {"id": 2, "run_type": "analysis", "started_at": "2026-11-02T00:00:00Z",
             "finished_at": "2026-11-02T00:01:00Z", "status": "success", "processed": 1,
             "failed": 0, "metadata_json": "{}"},
        ]
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2()
            report = migration.Migrator(
                self.make_args(temp, phase="scheduled-runs", dry_run=False, max_rows=1, batch_size=1),
                FakeReader({"scheduled_run_log": rows}), r2, "namespace"
            ).run()

            self.assertFalse(report["complete"])
            self.assertIn("operations/v1/runs/analysis/legacy-d1-1/summary.json", r2.objects)
            self.assertNotIn("operations/v1/runs/legacy-d1/current.json", r2.objects)
            checkpoint_text = Path(self.make_args(temp, phase="scheduled-runs", dry_run=False,
                                                  max_rows=1, batch_size=1).checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn('"run_key"', checkpoint_text)

    def test_scheduled_run_pointer_never_moves_back_from_a_newer_r2_run(self):
        with tempfile.TemporaryDirectory() as temp:
            key = "operations/v1/runs/legacy-d1/current.json"
            newer = {
                "run_key": "operations/v1/runs/analysis/legacy-d1-90/summary.json",
                "run_id": "90",
                "run_type": "analysis",
                "started_at": "2026-12-01T00:00:00Z",
            }
            r2 = FakeR2({key: migration.canonical_json(newer)})
            migrator = migration.Migrator(
                self.make_args(temp, phase="scheduled-runs", dry_run=False), FakeReader(), r2, "namespace"
            )

            migrator.put_latest_scheduled_run_pointer({
                "run_id": "2",
                "run_type": "analysis",
                "started_at": "2026-11-01T00:00:00Z",
            })

            self.assertEqual(json.loads(r2.objects[key]), newer)
            self.assertEqual(migrator.report["objects_skipped_newer"], 1)

    def test_resuming_legacy_scheduled_run_checkpoint_removes_raw_r2_key(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="scheduled-runs", dry_run=False)
            old_key = "operations/v1/runs/analysis/legacy-d1-2/summary.json"
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": "scheduled-runs", "table": "securities", "dry_run": False,
                "cursor": 2, "rolling_hash": "a" * 64, "r2_readback_sha256": "b" * 64,
                "latest_scheduled_run": {
                    "run_key": old_key, "run_id": "2", "run_type": "analysis",
                    "started_at": "2026-11-02T00:00:00Z",
                },
            }), encoding="utf-8")
            r2 = FakeR2()
            report = migration.Migrator(args, FakeReader(), r2, "namespace").run()

            self.assertTrue(report["complete"])
            self.assertTrue(report["checkpoint_restarted_for_scheduled_runs_cursor_change"])
            self.assertNotIn("operations/v1/runs/legacy-d1/current.json", r2.objects)
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            report_text = Path(args.report).read_text(encoding="utf-8")
            self.assertIn('"scheduled_runs_cursor_version":2', checkpoint_text)
            self.assertNotIn('"run_key"', checkpoint_text)
            self.assertNotIn(old_key, checkpoint_text + report_text)

    def test_analysis_source_initial_write_uses_timestamp_and_is_read_back(self):
        with tempfile.TemporaryDirectory() as temp:
            source_row = {
                "state_key": "analysis_source",
                "value_json": "catalog=2026-11-03;financials=2026-11-02",
                "updated_at": "2026-11-03T01:02:03Z",
            }
            args = self.make_args(temp, phase="analysis-source", dry_run=False)
            r2 = FakeR2()
            migrator = migration.Migrator(
                args,
                FakeReader({"backend_state": [source_row]}),
                r2,
                "namespace",
            )

            report = migrator.run()

            key = "analysis/v1/state/source.json"
            self.assertEqual(json.loads(r2.get(key)), {
                "source_tag": source_row["value_json"],
                "updated_at": source_row["updated_at"],
            })
            self.assertEqual(report["objects_written"], 1)
            self.assertEqual(report["objects_verified"], 1)

    def test_r2_readback_hash_and_period_ranges_are_recorded(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, dry_run=False)
            r2 = FakeR2()
            migrator = migration.Migrator(args, FakeReader(), r2, "namespace")
            source = {"observed_at": "2026-11-02T12:00:00Z", "market_date": "2026-11-02"}
            body = migration.canonical_json(source)
            migrator.record_source_row(source)
            migrator.put_immutable("history.json", body)
            report = migrator.run()
            self.assertEqual(report["objects_verified"], 1)
            self.assertEqual(report["source_time_ranges"]["market_date"], {"min": "2026-11-02", "max": "2026-11-02"})
            self.assertEqual(report["r2_time_ranges"]["observed_at"], {
                "min": "2026-11-02T12:00:00Z", "max": "2026-11-02T12:00:00Z"
            })
            self.assertNotEqual(report["r2_readback_sha256"], "0" * 64)

    def test_checkpoint_resumes_and_reports_cumulative_counts_without_row_bodies(self):
        rows = [
            {"migration_rowid": 1, "security_code": "7203", "last_observed_at": "2026-11-01T00:00:00Z",
             "analysis_json": '{"observed_at":"2026-11-01T00:00:00Z","secret_row_body":"DO_NOT_LOG"}'},
            {"migration_rowid": 2, "security_code": "8306", "last_observed_at": "2026-11-01T00:00:00Z",
             "analysis_json": '{"observed_at":"2026-11-01T00:00:00Z","secret_row_body":"DO_NOT_LOG"}'},
        ]
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, max_rows=1, batch_size=1)
            r2 = FakeR2()
            first = migration.Migrator(args, FakeReader({"stock_analysis_state": rows}), r2, "namespace").run()
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertEqual(checkpoint["cursor"], 1)
            self.assertEqual(checkpoint["cursor_kind"], "rowid-v1")
            self.assertNotIn("7203", json.dumps(checkpoint))
            self.assertFalse(first["complete"])

            second = migration.Migrator(args, FakeReader({"stock_analysis_state": rows}), r2, "namespace").run()
            self.assertEqual(second["cumulative"]["source_rows"], 2)
            self.assertEqual(second["cumulative"]["rows_read"], 2)
            artifacts = Path(args.checkpoint_out).read_text(encoding="utf-8") + Path(args.report).read_text(encoding="utf-8")
            self.assertNotIn("DO_NOT_LOG", artifacts)
            self.assertNotIn("7203", artifacts)
            self.assertNotIn("8306", artifacts)
            self.assertIn("source_sha256", second)

    def test_legacy_market_archive_dry_run_preserves_cursor_and_sanitizes_artifacts(self):
        rows = [
            {"migration_rowid": 1, "security_code": "7203", "name": "PRIVATE_COMPANY_NAME",
             "data_json": '{"secret_row_body":"DO_NOT_LOG"}'},
            {"migration_rowid": 2, "security_code": "8306", "name": "PRIVATE_COMPANY_NAME",
             "data_json": '{"secret_row_body":"DO_NOT_LOG"}'},
        ]
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(
                temp, phase="legacy-market-archive", table="securities", max_rows=10, batch_size=1
            )
            reader = FakeReader({"securities": rows})
            r2 = FakeR2()
            report = migration.Migrator(args, reader, r2, "namespace").run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["rows_read"], 2)
            self.assertEqual(report["source_rows"], 2)
            self.assertEqual(report["objects_planned"], 2)
            self.assertEqual(report["objects_written"], 0)
            self.assertEqual(r2.objects, {})
            self.assertEqual(r2.list_calls, ["migration/v1/d1/archive/securities/"])
            self.assertEqual(r2.get_calls, [])
            self.assertEqual(len(reader.queries), 3)
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            report_text = Path(args.report).read_text(encoding="utf-8")
            self.assertEqual(json.loads(checkpoint_text)["cursor"], 2)
            public_text = json.dumps({
                "checkpoint": without_hash_values(json.loads(checkpoint_text)),
                "report": without_hash_values(json.loads(report_text)),
            }, sort_keys=True)
            self.assertNotIn("7203", public_text)
            self.assertNotIn("8306", public_text)
            self.assertNotIn("PRIVATE_COMPANY_NAME", public_text)
            self.assertNotIn("DO_NOT_LOG", public_text)

    def test_legacy_analysis_current_code_cursor_restarts_from_numeric_rowid(self):
        rows = [
            {"migration_rowid": 1, "security_code": "7203", "last_observed_at": "2026-11-01T00:00:00Z",
             "analysis_json": '{"observed_at":"2026-11-01T00:00:00Z"}'},
            {"migration_rowid": 2, "security_code": "8306", "last_observed_at": "2026-11-01T00:00:00Z",
             "analysis_json": '{"observed_at":"2026-11-01T00:00:00Z"}'},
        ]
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, max_rows=1, batch_size=1)
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": "analysis-current", "table": "securities", "dry_run": True,
                "cursor": "7203", "rolling_hash": "a" * 64, "r2_readback_sha256": "b" * 64,
                "totals": {"source_rows": 1, "rows_read": 1},
            }), encoding="utf-8")
            reader = FakeReader({"stock_analysis_state": rows})
            r2 = FakeR2()
            report = migration.Migrator(args, reader, r2, "namespace").run()

            self.assertTrue(report["checkpoint_restarted_for_cursor_change"])
            self.assertEqual(reader.queries[0][1], [0, 1])
            self.assertEqual(report["source_rows"], 1)
            self.assertIn("WHERE rowid > ? ORDER BY rowid", reader.queries[0][0])
            resumed = migration.Migrator(args, FakeReader({"stock_analysis_state": rows}), r2, "namespace").run()
            self.assertEqual(resumed["cumulative"]["source_rows"], 2)
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            report_text = Path(args.report).read_text(encoding="utf-8")
            public_text = json.dumps({
                "checkpoint": without_hash_values(json.loads(checkpoint_text)),
                "report": without_hash_values(json.loads(report_text)),
            }, sort_keys=True)
            self.assertNotIn("7203", public_text)
            self.assertNotIn('"run_key"', checkpoint_text)

    def test_write_mode_restarts_a_completed_dry_run_checkpoint(self):
        rows = [
            {"migration_rowid": 1, "security_code": "72030", "last_observed_at": "2026-11-01T00:00:00Z",
             "analysis_json": '{"observed_at":"2026-11-01T00:00:00Z","canonical_code":"72030"}'},
            {"migration_rowid": 2, "security_code": "83060", "last_observed_at": "2026-11-01T00:00:00Z",
             "analysis_json": '{"observed_at":"2026-11-01T00:00:00Z","canonical_code":"83060"}'},
        ]
        with tempfile.TemporaryDirectory() as temp:
            r2 = FakeR2()
            dry_args = self.make_args(temp, max_rows=3, batch_size=1, dry_run=True)
            dry_report = migration.Migrator(
                dry_args, FakeReader({"stock_analysis_state": rows}), r2, "namespace"
            ).run()
            self.assertTrue(dry_report["complete"])
            self.assertEqual(dry_report["objects_planned"], 2)
            self.assertEqual(r2.objects, {})

            write_args = self.make_args(temp, max_rows=3, batch_size=1, dry_run=False)
            write_report = migration.Migrator(
                write_args, FakeReader({"stock_analysis_state": rows}), r2, "namespace"
            ).run()

            self.assertTrue(write_report["checkpoint_restarted_for_mode_change"])
            self.assertTrue(write_report["complete"])
            self.assertEqual(write_report["source_rows"], 2)
            self.assertEqual(write_report["objects_written"], 2)
            self.assertIn("analysis/v1/current/72030.json", r2.objects)
            self.assertIn("analysis/v1/current/83060.json", r2.objects)

    def test_d1_schema_audit_lists_table_index_and_trigger_names_without_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader({"sqlite_schema": [
                {"type": "index", "name": "idx_support_feedback_created_at", "tbl_name": "support_feedback"},
                {"type": "table", "name": "_cf_KV", "tbl_name": "_cf_KV"},
                {"type": "table", "name": "sqlite_sequence", "tbl_name": "sqlite_sequence"},
                {"type": "table", "name": "support_feedback", "tbl_name": "support_feedback"},
                {"type": "trigger", "name": "support_feedback_email_insert", "tbl_name": "support_feedback"},
            ]})
            r2 = FakeR2()
            report = migration.Migrator(
                self.make_args(temp, phase="d1-schema-audit"), reader, r2, "namespace"
            ).run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["source_rows"], 5)
            self.assertEqual(
                report["schema_objects"],
                [
                    {"type": "index", "name": "idx_support_feedback_created_at", "table": "support_feedback"},
                    {
                        "type": "table",
                        "name": "sha256:2601183a39b0e8003b6a908905e3765953734fa7c7bfca750938b70e95e11638",
                        "table": "sha256:2601183a39b0e8003b6a908905e3765953734fa7c7bfca750938b70e95e11638",
                        "classification": "cloudflare_reserved",
                    },
                    {"type": "table", "name": "sqlite_sequence", "table": "sqlite_sequence"},
                    {"type": "table", "name": "support_feedback", "table": "support_feedback"},
                    {"type": "trigger", "name": "support_feedback_email_insert", "table": "support_feedback"},
                ],
            )
            self.assertEqual(r2.objects, {})

    def test_d1_only_inventory_main_does_not_require_or_construct_r2_client(self):
        with tempfile.TemporaryDirectory() as temp:
            args = [
                "migrate_d1_to_r2.py", "--phase", "d1-schema-audit", "--dry-run",
                "--max-rows", "10", "--checkpoint-in", str(Path(temp) / "missing.json"),
                "--checkpoint-out", str(Path(temp) / "checkpoint.json"),
                "--report", str(Path(temp) / "report.json"),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": []}
            env = {
                "CLOUDFLARE_ACCOUNT_ID": "account",
                "CLOUDFLARE_API_TOKEN": "analytics-read-only-token",
                "CLOUDFLARE_MIGRATION_READ_TOKEN": "d1-read-only-token",
            }
            events = []
            usage_report = {
                "utc_date": "2026-11-04", "queried_at_utc": "2026-11-04T09:00:00+00:00",
                "rows_read": 100, "rows_written": 0, "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000, "limit_exceeded": False,
            }

            def fetch_usage(account_id, token, utc_date):
                events.append(("analytics", account_id, token))
                return budget_usage_report({**usage_report, "utc_date": utc_date})

            def create_reader(account_id, database_id, token):
                events.append(("d1", account_id, token))
                return FakeReader({"sqlite_schema": []})

            with (
                patch("sys.argv", args),
                patch.dict("os.environ", env, clear=True),
                patch.object(migration, "load_config", return_value=config),
                patch.object(migration.d1_daily_usage, "fetch_budget_usage", side_effect=fetch_usage),
                patch.object(
                    migration.d1_daily_usage,
                    "fetch_daily_usage",
                    return_value={**usage_report, "utc_date": datetime.now(timezone.utc).date().isoformat()},
                ),
                patch.object(migration, "D1Reader", side_effect=create_reader),
                patch.object(migration, "R2Store", side_effect=AssertionError("R2 client must not be constructed")),
            ):
                self.assertEqual(migration.main(), 0)

            report = json.loads((Path(temp) / "report.json").read_text(encoding="utf-8"))
            self.assertTrue(report["complete"])
            self.assertEqual(report["objects_written"], 0)
            self.assertEqual(events[0], ("analytics", "account", "analytics-read-only-token"))
            self.assertEqual(events[1], ("d1", "account", "d1-read-only-token"))
            self.assertEqual(report["account_daily_usage_preflight"]["required_rows_remaining"], 100_010)
            self.assertNotIn("analytics-read-only-token", json.dumps(report))
            self.assertNotIn("d1-read-only-token", json.dumps(report))
            report_hash = report.pop("report_sha256")
            self.assertEqual(digest(migration.canonical_json(report)).hexdigest(), report_hash)

    def test_main_returns_nonzero_when_a_phase_is_incomplete(self):
        with tempfile.TemporaryDirectory() as temp:
            argv = [
                "migrate_d1_to_r2.py", "--phase", "d1-schema-audit",
                "--checkpoint-in", str(Path(temp) / "missing.json"),
                "--checkpoint-out", str(Path(temp) / "checkpoint.json"),
                "--report", str(Path(temp) / "report.json"),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": []}
            with (
                patch("sys.argv", argv),
                patch.dict("os.environ", {
                    "CLOUDFLARE_ACCOUNT_ID": "account",
                    "CLOUDFLARE_MIGRATION_READ_TOKEN": "read-only-token",
                }, clear=True),
                patch.object(migration, "load_config", return_value=config),
                patch.object(migration, "account_daily_usage_preflight", return_value={}),
                patch.object(migration, "D1Reader", return_value=FakeReader()),
                patch.object(
                    migration.Migrator,
                    "run",
                    return_value={"status": "incomplete", "complete": False},
                ),
            ):
                self.assertEqual(migration.main(), 2)

    def test_d1_operational_audit_sanitizes_keys_and_resumes_from_rowid(self):
        rows = [
            {"migration_rowid": 1, "state_key": "analysis_source", "updated_at": "2026-11-03T01:00:00Z", "value_json": "SENSITIVE_VALUE"},
            {"migration_rowid": 2, "state_key": "user@example.invalid", "updated_at": "2026-11-04T01:00:00Z", "value_json": "OTHER_SENSITIVE_VALUE"},
        ]
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="d1-operational-audit", table="backend_state", max_rows=1, batch_size=1)
            first = migration.Migrator(args, FakeReader({"backend_state": rows}), FakeR2(), "namespace").run()
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertFalse(first["complete"])
            self.assertEqual(checkpoint["cursor"], 1)

            resume_args = self.make_args(temp, phase="d1-operational-audit", table="backend_state", max_rows=5, batch_size=2)
            resumed = migration.Migrator(
                resume_args, FakeReader({"backend_state": rows}), FakeR2(), "namespace"
            ).run()

            self.assertTrue(resumed["complete"])
            self.assertEqual(resumed["cumulative"]["source_rows"], 2)
            inventory = resumed["operational_inventory"]
            self.assertEqual(inventory["row_count"], 2)
            self.assertIn("analysis_source", inventory["state_keys"])
            self.assertTrue(any(key.startswith("sha256:") for key in inventory["state_keys"]))
            self.assertNotIn("user@example.invalid", json.dumps(resumed))
            self.assertNotIn("SENSITIVE_VALUE", json.dumps(resumed))
            self.assertNotIn("OTHER_SENSITIVE_VALUE", Path(args.checkpoint_out).read_text(encoding="utf-8"))

    def test_checkpoint_artifact_drops_unrecognized_top_level_and_nested_payloads(self):
        private_value = "DO_NOT_UPLOAD_user@example.invalid"
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="d1-operational-audit", table="backend_state")
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": args.phase,
                "table": args.table,
                "dry_run": True,
                "cursor": 1,
                "rolling_hash": "0" * 64,
                "private_payload": private_value,
                "market_source_audit": {
                    "rows_checked": 0,
                    "sample": [{"security_code": private_value, "data_json": private_value}],
                    "unrecognized_payload": private_value,
                },
                "market_audit_fields": {
                    "name_mismatch_count": 0,
                    "private_sample": private_value,
                },
                "operational_inventory": {
                    "table": "backend_state",
                    "row_count": 1,
                    "status_counts": {"running": 1, "PRIVATE_CODE": 2},
                    "date_ranges": {
                        "updated_at": {
                            "min": "2026-11-03T01:00:00Z",
                            "max": "2026-11-03T01:00:00Z",
                        },
                        "private_date": {"min": private_value, "max": private_value},
                    },
                    "state_keys": ["analysis_source", "9432", private_value],
                    "numeric_totals": {},
                    "rows_without_completion_time": 0,
                    "unrecognized_payload": private_value,
                },
            }), encoding="utf-8")

            report = migration.Migrator(
                args, FakeReader({"backend_state": []}), FakeR2(), "namespace"
            ).run()
            report_text = Path(args.report).read_text(encoding="utf-8")
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            self.assertTrue(report["complete"])
            self.assertNotIn(private_value, report_text + checkpoint_text)
            self.assertNotIn("private_payload", checkpoint_text)
            self.assertNotIn("unrecognized_payload", checkpoint_text)
            self.assertNotIn("private_sample", checkpoint_text)
            self.assertNotIn('"PRIVATE_CODE"', checkpoint_text)

    def test_d1_operational_audit_summarizes_market_refresh_runs_without_run_ids(self):
        row = {
            "migration_rowid": 1,
            "run_id": "PRIVATE_RUN_ID",
            "status": "queued",
            "run_date": "2026-11-02",
            "started_at": "2026-11-02T01:00:00Z",
            "last_batch_at": None,
            "completed_at": None,
            "updated_at": "2026-11-02T01:00:00Z",
            "total_batches": 4,
            "enqueued_batches": 2,
            "completed_batches": 1,
            "failed_batches": 0,
            "processed_symbols": 25,
        }
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="d1-operational-audit", table="market_price_refresh_runs")
            report = migration.Migrator(
                args, FakeReader({"market_price_refresh_runs": [row]}), FakeR2(), "namespace"
            ).run()

            self.assertTrue(report["complete"])
            inventory = report["operational_inventory"]
            self.assertEqual(inventory["status_counts"], {"queued": 1})
            self.assertEqual(inventory["numeric_totals"]["processed_symbols"], 25)
            self.assertEqual(inventory["rows_without_completion_time"], 1)
            self.assertNotIn("PRIVATE_RUN_ID", json.dumps(report))

    def test_d1_operational_audit_summarizes_market_refresh_batches(self):
        row = {
            "migration_rowid": 1,
            "run_id": "PRIVATE_RUN_ID",
            "batch_index": 3,
            "status": "complete",
            "processed_symbols": 20,
            "market_data_date": "2026-11-02",
            "completed_at": "2026-11-02T03:00:00Z",
        }
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="d1-operational-audit", table="market_price_refresh_batches")
            report = migration.Migrator(
                args, FakeReader({"market_price_refresh_batches": [row]}), FakeR2(), "namespace"
            ).run()

            self.assertTrue(report["complete"])
            inventory = report["operational_inventory"]
            self.assertEqual(inventory["status_counts"], {"complete": 1})
            self.assertEqual(inventory["numeric_totals"]["processed_symbols"], 20)
            self.assertEqual(inventory["date_ranges"]["market_data_date"]["max"], "2026-11-02")
            self.assertNotIn("PRIVATE_RUN_ID", json.dumps(report))

    def test_d1_operational_audit_counts_incomplete_scheduled_runs_without_run_ids_or_metadata(self):
        rows = [
            {
                "migration_rowid": 41,
                "run_id": "PRIVATE_RUNNING_RUN_ID",
                "status": "running",
                "run_type": "analysis",
                "started_at": "2026-11-03T01:00:00Z",
                "finished_at": None,
                "processed": 12,
                "failed": 1,
                "metadata_json": "PRIVATE_METADATA",
            },
            {
                "migration_rowid": 42,
                "run_id": "PRIVATE_FINISHED_RUN_ID",
                "status": "complete",
                "run_type": "analysis",
                "started_at": "2026-11-04T01:00:00Z",
                "finished_at": "2026-11-04T01:02:00Z",
                "processed": 25,
                "failed": 0,
                "metadata_json": "OTHER_PRIVATE_METADATA",
            },
        ]
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="d1-operational-audit", table="scheduled_run_log", max_rows=1, batch_size=1)
            first = migration.Migrator(
                args, FakeReader({"scheduled_run_log": rows}), FakeR2(), "namespace"
            ).run()
            self.assertFalse(first["complete"])
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertEqual(checkpoint["cursor"], 41)

            resume_args = self.make_args(temp, phase="d1-operational-audit", table="scheduled_run_log", max_rows=10, batch_size=2)
            report = migration.Migrator(
                resume_args, FakeReader({"scheduled_run_log": rows}), FakeR2(), "namespace"
            ).run()
            self.assertTrue(report["complete"])
            inventory = report["operational_inventory"]
            self.assertEqual(inventory["row_count"], 2)
            self.assertEqual(inventory["status_counts"], {"running": 1, "complete": 1})
            self.assertEqual(inventory["rows_without_completion_time"], 1)
            self.assertEqual(inventory["numeric_totals"], {"processed": 37, "failed": 1})
            self.assertEqual(inventory["date_ranges"]["started_at"]["min"], "2026-11-03T01:00:00Z")
            self.assertEqual(inventory["date_ranges"]["finished_at"]["max"], "2026-11-04T01:02:00Z")
            self.assertEqual(inventory["run_type_counts"], {"analysis": 2})
            serialized = json.dumps(report)
            self.assertNotIn("PRIVATE_RUNNING_RUN_ID", serialized)
            self.assertNotIn("PRIVATE_FINISHED_RUN_ID", serialized)
            self.assertNotIn("PRIVATE_METADATA", serialized)
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn("PRIVATE_RUNNING_RUN_ID", checkpoint_text)
            self.assertNotIn("PRIVATE_FINISHED_RUN_ID", checkpoint_text)
            self.assertNotIn("PRIVATE_METADATA", checkpoint_text)

    def test_scheduled_run_audit_rejects_old_checkpoint_without_run_type_counts(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="d1-operational-audit", table="scheduled_run_log")
            checkpoint = {
                "phase": args.phase,
                "table": args.table,
                "dry_run": True,
                "cursor": 41,
                "rolling_hash": "0" * 64,
                "operational_inventory": {
                    "table": "scheduled_run_log",
                    "row_count": 1,
                    "status_counts": {"running": 1},
                    "date_ranges": {},
                    "state_keys": [],
                    "numeric_totals": {"processed": 0, "failed": 0},
                    "rows_without_completion_time": 1,
                },
            }
            Path(args.checkpoint_in).write_text(json.dumps(checkpoint), encoding="utf-8")

            migrator = migration.Migrator(args, FakeReader(), FakeR2(), "namespace")
            with self.assertRaisesRegex(migration.MigrationError, "run type counts"):
                migrator.run()

    def test_d1_operational_audit_rejects_invalid_or_incomplete_checkpoints(self):
        for cursor, summary in (("1", None), (1, None)):
            with self.subTest(cursor=cursor), tempfile.TemporaryDirectory() as temp:
                args = self.make_args(temp, phase="d1-operational-audit", table="backend_state")
                checkpoint = {
                    "phase": args.phase,
                    "table": args.table,
                    "dry_run": True,
                    "cursor": cursor,
                    "rolling_hash": "0" * 64,
                }
                if summary is not None:
                    checkpoint["operational_inventory"] = summary
                Path(args.checkpoint_in).write_text(json.dumps(checkpoint), encoding="utf-8")
                migrator = migration.Migrator(args, FakeReader(), FakeR2(), "namespace")
                with self.assertRaisesRegex(migration.MigrationError, "checkpoint"):
                    migrator.run()

    def test_d1_inventory_phases_reject_r2_write_mode(self):
        for phase, table_args in (
            ("d1-schema-audit", []),
            ("d1-operational-audit", ["--table", "backend_state"]),
        ):
            with self.subTest(phase=phase), patch(
                "sys.argv",
                ["migrate_d1_to_r2.py", "--phase", phase, *table_args, *CLI_FILE_ARGS,
                 "--write-r2", "--confirm-write", "WRITE_R2"],
            ), self.assertRaises(SystemExit):
                migration.arguments()

    def test_market_audit_reports_missing_rows_without_security_code_samples(self):
        with tempfile.TemporaryDirectory() as temp:
            catalog = migration.canonical_json({"generation": "g1", "items": [{"code": "7203"}]})
            universe = migration.canonical_json({"securities": [
                {
                    "code": "8306", "status": "active", "security_type": "listed_security",
                    "jquants_product_category": "011", "sources": ["IRBANK Connect API v1 /securities"],
                },
                {
                    "code": "9600", "status": "active", "security_type": "etf",
                    "jquants_product_category": "014", "sources": ["JPX Listed Issues - ETFs"],
                },
                {"code": "9432", "status": "inactive"},
                {"code": "9501", "status": "unknown"},
            ]})
            rows = [
                {"migration_rowid": rowid, "security_code": code, "security_code_display": code}
                for rowid, code in enumerate(("7203", "8306", "9432", "9501"), start=1)
            ]
            source_codes = [f"{code:04d}" for code in range(1000, 4497)] + ["8306", "9501", "9600"]
            source = valid_irbank_source_snapshot(source_codes)
            irbank_catalog = valid_irbank_catalog(["83060", "95010"])
            migrator = migration.Migrator(
                self.make_args(temp, phase="market-audit"),
                FakeReader({"securities": rows}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/yfinance/v2/universe/current.json": universe,
                    "market/yfinance/v2/universe/irbank_source.json": source,
                    "market/irbank/v1/catalog.json": irbank_catalog,
                }),
                "namespace",
            )
            report = migrator.run()
            self.assertEqual(report["d1_security_missing_count"], 3)
            self.assertEqual(report["r2_universe_status"], "available")
            self.assertEqual(report["r2_universe_count"], 4)
            self.assertEqual(report["r2_universe_active_count"], 2)
            self.assertEqual(report["r2_universe_inactive_count"], 1)
            self.assertEqual(report["r2_active_universe_missing_catalog_count"], 2)
            self.assertEqual(report["r2_catalog_outside_active_universe_count"], 1)
            self.assertEqual(report["r2_irbank_source_universe_status"], "available")
            self.assertEqual(report["r2_irbank_source_universe_count"], 3500)
            self.assertEqual(report["r2_active_catalog_missing_in_irbank_source_count"], 2)
            self.assertEqual(report["r2_active_universe_in_irbank_source_count"], 2)
            self.assertEqual(report["r2_active_universe_not_in_irbank_source_count"], 0)
            self.assertEqual(report["r2_irbank_source_not_active_universe_count"], 3498)
            self.assertEqual(report["r2_irbank_catalog_status"], "available")
            self.assertEqual(report["r2_irbank_catalog_count"], 2)
            self.assertEqual(report["r2_active_universe_in_irbank_catalog_count"], 1)
            self.assertEqual(report["r2_active_universe_supported_by_catalogs_count"], 1)
            self.assertEqual(report["r2_active_universe_missing_both_catalogs_count"], 1)
            self.assertEqual(report["r2_active_universe_by_security_type"], {"etf": 1, "listed_security": 1})
            self.assertEqual(report["r2_active_universe_missing_both_by_security_type"], {"etf": 1})
            self.assertEqual(report["r2_active_universe_missing_both_by_product_category"], {"014": 1})
            self.assertEqual(report["r2_active_universe_missing_both_by_source"], {"JPX Listed Issues - ETFs": 1})
            self.assertEqual(report["market_audit_fields"]["d1_security_missing_active_count"], 1)
            self.assertEqual(
                report["market_audit_fields"]["d1_security_missing_active_in_irbank_source_count"],
                1,
            )
            self.assertEqual(
                report["market_audit_fields"]["d1_security_missing_active_in_irbank_catalog_count"],
                1,
            )
            self.assertEqual(report["market_audit_fields"]["d1_security_missing_active_uncovered_count"], 0)
            self.assertEqual(report["market_audit_fields"]["d1_security_missing_inactive_count"], 1)
            self.assertEqual(report["market_audit_fields"]["d1_security_missing_unclassified_count"], 1)
            self.assertNotIn("d1_security_missing_sample", report)
            self.assertTrue(report["complete"])
            self.assertEqual(report["r2_catalog_generation"], "g1")
            self.assertEqual(report["r2_catalog_sha256"], digest(catalog).hexdigest())
            artifact = json.loads(Path(migrator.args.report).read_text(encoding="utf-8"))
            checkpoint = json.loads(Path(migrator.args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertNotIn("d1_security_missing_sample", artifact)
            self.assertNotIn("d1_security_missing_sample", checkpoint)
            self.assertNotIn('"8306"', json.dumps(artifact))
            self.assertNotIn('"9432"', json.dumps(checkpoint))

    def test_market_audit_rejects_changed_irbank_source_universe_on_resume(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-audit")
            catalog = migration.canonical_json({"generation": "g1", "items": []})
            initial_codes = [f"{code:04d}" for code in range(1000, 4498)] + ["8306", "9501"]
            first = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/yfinance/v2/universe/irbank_source.json": valid_irbank_source_snapshot(initial_codes),
                }),
                "namespace",
            )
            self.assertTrue(first.run()["complete"])

            changed_codes = [code for code in initial_codes if code != "9501"] + ["9998"]
            resumed = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/yfinance/v2/universe/irbank_source.json": valid_irbank_source_snapshot(changed_codes),
                }),
                "namespace",
            )
            with self.assertRaisesRegex(migration.MigrationError, "source universe changed"):
                resumed.run()

    def test_market_audit_rejects_changed_irbank_catalog_on_resume(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-audit")
            catalog = migration.canonical_json({"generation": "g1", "items": []})
            first = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/irbank/v1/catalog.json": valid_irbank_catalog(["83060"]),
                }),
                "namespace",
            )
            self.assertTrue(first.run()["complete"])

            resumed = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/irbank/v1/catalog.json": valid_irbank_catalog(["95010"]),
                }),
                "namespace",
            )
            with self.assertRaisesRegex(migration.MigrationError, "IRBANK catalog changed"):
                resumed.run()

    def test_market_audit_rejects_changed_active_universe_on_resume(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-audit")
            catalog = migration.canonical_json({"generation": "g1", "items": []})
            first_universe = migration.canonical_json({"securities": [
                {"code": "7203", "status": "active"},
            ]})
            first = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/yfinance/v2/universe/current.json": first_universe,
                }),
                "namespace",
            )
            self.assertTrue(first.run()["complete"])

            changed_universe = migration.canonical_json({"securities": [
                {"code": "7203", "status": "inactive"},
            ]})
            resumed = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({
                    "market/yfinance/v2/catalog/current.json": catalog,
                    "market/yfinance/v2/universe/current.json": changed_universe,
                }),
                "namespace",
            )
            with self.assertRaisesRegex(migration.MigrationError, "active-universe snapshot changed"):
                resumed.run()

    def test_market_audit_restarts_precoverage_checkpoint_before_resuming(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-audit")
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": args.phase,
                "table": args.table,
                "dry_run": True,
                "cursor": None,
                "rolling_hash": "0" * 64,
                "r2_readback_sha256": "0" * 64,
                "write_set_sha256": "0" * 64,
                "write_set_digest_algorithm": migration.WRITE_SET_DIGEST_ALGORITHM,
                "market_audit_fields": {"d1_security_missing_active_count": 99},
            }), encoding="utf-8")
            migrator = migration.Migrator(
                args,
                FakeReader({"securities": [{
                    "migration_rowid": 1,
                    "security_code": "83060",
                    "security_code_display": "8306",
                }]}),
                FakeR2({"market/yfinance/v2/catalog/current.json": migration.canonical_json({"generation": "g1", "items": []})}),
                "namespace",
            )
            report = migrator.run()
            self.assertTrue(report["checkpoint_restarted_for_market_audit_version_change"])
            self.assertTrue(report["complete"])
            self.assertEqual(report["source_rows"], 1)
            self.assertEqual(report["d1_security_missing_count"], 1)
            self.assertEqual(report["market_audit_fields"]["d1_security_missing_active_count"], 0)
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertEqual(checkpoint["market_audit_version"], migration.MARKET_AUDIT_VERSION)

    def test_market_audit_rowid_paging_counts_duplicate_and_invalid_display_codes(self):
        with tempfile.TemporaryDirectory() as temp:
            catalog = migration.canonical_json({"generation": "g1", "items": [{"code": "7203"}]})
            rows = [
                {"migration_rowid": 1, "security_code": "7203", "security_code_display": "7203"},
                {"migration_rowid": 2, "security_code": "8306A", "security_code_display": "8306"},
                {"migration_rowid": 3, "security_code": "8306B", "security_code_display": "8306"},
                {"migration_rowid": 4, "security_code": "", "security_code_display": ""},
                {"migration_rowid": 5, "security_code": "XYZ", "security_code_display": " XYZ "},
            ]
            reader = FakeReader({"securities": rows})
            migrator = migration.Migrator(
                self.make_args(temp, phase="market-audit", batch_size=1),
                reader,
                FakeR2({"market/yfinance/v2/catalog/current.json": catalog}),
                "namespace",
            )

            report = migrator.run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["d1_security_missing_count"], 2)
            self.assertEqual(report["d1_security_invalid_count"], 2)
            self.assertNotIn("d1_security_missing_sample", report)
            self.assertNotIn("d1_security_invalid_sample", report)
            self.assertFalse(any(key.endswith("_sample") for key in report["market_audit_fields"]))
            self.assertEqual(len(reader.queries), 6)
            self.assertTrue(all("ORDER BY rowid" in query for query, _ in reader.queries))

    def test_resuming_legacy_audit_checkpoint_strips_security_samples(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-audit")
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": "market-audit",
                "table": "securities",
                "dry_run": True,
                "cursor": 1,
                "rolling_hash": "0" * 64,
                "r2_readback_sha256": "0" * 64,
                "d1_security_missing_count": 1,
                "d1_security_missing_sample": ["PRIVATE_CODE"],
                "market_audit_fields": {
                    "name_mismatch_count": 1,
                    "name_mismatch_sample": [{"security_code": "PRIVATE_CODE"}],
                },
            }), encoding="utf-8")
            catalog = migration.canonical_json({"generation": "g1", "items": []})
            migrator = migration.Migrator(
                args,
                FakeReader({"securities": []}),
                FakeR2({"market/yfinance/v2/catalog/current.json": catalog}),
                "namespace",
            )

            migrator.run()

            report = Path(args.report).read_text(encoding="utf-8")
            checkpoint = Path(args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn("PRIVATE_CODE", report)
            self.assertNotIn("PRIVATE_CODE", checkpoint)
            self.assertNotIn("_sample", report)
            self.assertNotIn("_sample", checkpoint)

    def test_market_audit_compares_catalog_name_and_edinet_and_counts_legacy_metrics(self):
        with tempfile.TemporaryDirectory() as temp:
            catalog = migration.canonical_json({"generation": "g1", "items": [
                {"code": "7203", "name": "トヨタ"},
            ]})
            issuer_index = migration.canonical_json({"items": {
                "7203": {"edinet_code": "E00002"},
            }})
            overview_key = "market/irbank/v1/securities/8306/overview.json"
            metrics = [{"private_metric": "DO_NOT_LOG"}]
            overview = migration.canonical_json({"security": {"metrics": metrics}})
            irbank_manifest = migration.canonical_json({"objects": [
                {"key": overview_key, "sha256": migration.sha256(overview)},
            ]})
            rows = [
                {"migration_rowid": 1, "security_code": "72030", "security_code_display": "7203",
                 "name": "トヨタ自動車", "edinet_code": "E00001", "metrics_json": "[]"},
                {"migration_rowid": 2, "security_code": "83060", "security_code_display": "8306",
                 "name": "三菱ＵＦＪ", "edinet_code": "E00003", "metrics_json": migration.canonical_json(metrics).decode("utf-8")},
            ]
            reader = FakeReader({"securities": rows})
            r2 = FakeR2({
                "market/yfinance/v2/catalog/current.json": catalog,
                "market/yfinance/v2/issuer_dividends/current.json": issuer_index,
                "market/irbank/v1/manifest.json": irbank_manifest,
                overview_key: overview,
            })
            args = self.make_args(temp, phase="market-audit")
            report = migration.Migrator(args, reader, r2, "namespace").run()

            self.assertEqual(report["market_audit_fields"]["name_mismatch_count"], 1)
            self.assertEqual(report["market_audit_fields"]["edinet_mismatch_count"], 1)
            self.assertEqual(report["market_audit_fields"]["metrics_nonempty_count"], 1)
            self.assertEqual(report["market_audit_fields"]["metrics_compared_count"], 1)
            self.assertEqual(report["market_audit_fields"]["metrics_mismatch_count"], 0)
            self.assertEqual(report["r2_issuer_index_status"], "available")
            self.assertEqual(report["r2_irbank_manifest_status"], "available")
            report_text = Path(args.report).read_text(encoding="utf-8")
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn("DO_NOT_LOG", report_text + checkpoint_text)
            self.assertNotIn("8306", report_text + checkpoint_text)
            self.assertNotIn(overview_key, report_text + checkpoint_text)

    def test_market_audit_counts_metrics_differences_without_emitting_values(self):
        with tempfile.TemporaryDirectory() as temp:
            overview_key = "market/irbank/v1/securities/8306/overview.json"
            overview = migration.canonical_json({"security": {"metrics": [{"field": "dividend", "value": 2}]}})
            irbank_manifest = migration.canonical_json({"objects": [
                {"key": overview_key, "sha256": migration.sha256(overview)},
            ]})
            reader = FakeReader({"securities": [{
                "migration_rowid": 1, "security_code": "83060", "security_code_display": "8306",
                "name": "銘柄", "edinet_code": "E00001",
                "metrics_json": '[{"field":"dividend","value":999}]',
            }]})
            r2 = FakeR2({
                "market/yfinance/v2/catalog/current.json": migration.canonical_json({
                    "generation": "g1", "items": [{"code": "8306", "name": "銘柄"}],
                }),
                "market/yfinance/v2/issuer_dividends/current.json": migration.canonical_json({"items": {
                    "8306": {"edinet_code": "E00001"},
                }}),
                "market/irbank/v1/manifest.json": irbank_manifest,
                overview_key: overview,
            })
            args = self.make_args(temp, phase="market-audit")
            report = migration.Migrator(args, reader, r2, "namespace").run()

            self.assertEqual(report["market_audit_fields"]["metrics_compared_count"], 1)
            self.assertEqual(report["market_audit_fields"]["metrics_mismatch_count"], 1)
            report_text = Path(args.report).read_text(encoding="utf-8")
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            report_artifact = json.loads(report_text)
            checkpoint_artifact = json.loads(checkpoint_text)

            def values(value):
                if isinstance(value, dict):
                    for nested in value.values():
                        yield from values(nested)
                elif isinstance(value, list):
                    for nested in value:
                        yield from values(nested)
                else:
                    yield value

            artifact_values = list(values(report_artifact)) + list(values(checkpoint_artifact))
            self.assertNotIn(999, artifact_values)
            self.assertNotIn("999", artifact_values)
            self.assertNotIn("8306", artifact_values)
            self.assertNotIn(overview_key, report_text + checkpoint_text)

    def test_market_audit_stops_if_irbank_overview_differs_from_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            overview_key = "market/irbank/v1/securities/8306/overview.json"
            expected_overview = migration.canonical_json({"security": {"metrics": [{"value": 2}]}})
            changed_overview = migration.canonical_json({"security": {"metrics": [{"value": 3}]}})
            irbank_manifest = migration.canonical_json({"objects": [
                {"key": overview_key, "sha256": migration.sha256(expected_overview)},
            ]})
            reader = FakeReader({"securities": [{
                "migration_rowid": 1, "security_code": "83060", "security_code_display": "8306",
                "name": "銘柄", "edinet_code": "E00001", "metrics_json": '[{"value":2}]',
            }]})
            r2 = FakeR2({
                "market/yfinance/v2/catalog/current.json": migration.canonical_json({
                    "generation": "g1", "items": [{"code": "8306", "name": "銘柄"}],
                }),
                "market/yfinance/v2/issuer_dividends/current.json": migration.canonical_json({"items": {
                    "8306": {"edinet_code": "E00001"},
                }}),
                "market/irbank/v1/manifest.json": irbank_manifest,
                overview_key: changed_overview,
            })

            with self.assertRaisesRegex(migration.MigrationError, "changed since manifest"):
                migration.Migrator(self.make_args(temp, phase="market-audit"), reader, r2, "namespace").run()

    def test_financial_source_audit_normalizes_million_yen_to_yen_and_allows_source_rounding(self):
        missing, different, common, incomplete, conversions = migration.audit_financial_value_differences(
            {"sales": 62268},
            {"sales": 62268445000, "field_units": {"sales": "jpy"}},
        )
        self.assertEqual((missing, different, common, incomplete), ([], [], 1, []))
        self.assertEqual(conversions, {"million_jpy->jpy": 1})

    def test_financial_source_audit_preserves_material_value_mismatch_after_unit_conversion(self):
        missing, different, common, incomplete, _ = migration.audit_financial_value_differences(
            {"sales": 6639},
            {"sales": 236561000000, "field_units": {"sales": "jpy"}},
        )
        self.assertEqual((missing, different, common, incomplete), ([], ["sales"], 1, []))

    def test_financial_source_audit_does_not_claim_match_when_target_units_are_missing(self):
        missing, different, common, incomplete, _ = migration.audit_financial_value_differences(
            {"sales": 1234},
            {"sales": 1234},
        )
        self.assertEqual((missing, different, common, incomplete), ([], [], 1, ["sales"]))

    def test_financial_source_audit_restarts_checkpoint_after_comparison_version_change(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-source-audit", table="financial_periods")
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": "market-source-audit",
                "table": "financial_periods",
                "dry_run": True,
                "cursor": 1200,
                "rolling_hash": "a" * 64,
                "market_source_audit_version": migration.FINANCIAL_AUDIT_VERSION - 1,
                "market_source_audit": {"rows_checked": 500},
            }), encoding="utf-8")
            migrator = migration.Migrator(args, FakeReader({"financial_periods": []}), FakeR2(), "namespace")
            self.assertTrue(migrator.report["checkpoint_restarted_for_financial_audit_version_change"])
            self.assertIsNone(migrator.checkpoint.get("cursor"))
            self.assertNotIn("market_source_audit", migrator.checkpoint)
            report = migrator.run()
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertTrue(report["complete"])
            self.assertEqual(checkpoint["market_source_audit_version"], migration.FINANCIAL_AUDIT_VERSION)

    def test_market_source_audit_compares_financial_period_values(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-source-audit", table="financial_periods")
            source_key = "market/irbank/v1/securities/7203/overview.json"
            source = {"financials": {"annual": [{
                "fiscal_year": "2025/03",
                "accounting_standard": "J-GAAP",
                "is_consolidated": True,
                "sales": 1234,
                "field_units": {"sales": "million_jpy"},
            }]}}
            row = {
                "migration_rowid": 1,
                "security_code_display": "7203",
                "period_type": "annual",
                "fiscal_year": "2025/03",
                "accounting_standard": "J-GAAP",
                "is_consolidated": 1,
                "data_json": json.dumps({"fiscal_year": "2025/03", "sales": 1234,
                                         "field_units": {"sales": "million_jpy"}}),
            }
            r2 = FakeR2({source_key: migration.canonical_json(source)})
            report = migration.Migrator(args, FakeReader({"financial_periods": [row]}), r2, "namespace").run()
            self.assertTrue(report["complete"])
            self.assertEqual(report["market_source_audit"]["status_counts"], {"source_values_match": 1})
            self.assertEqual(report["market_source_audit"]["period_range"], {"min": "2025-03", "max": "2025-03"})
            self.assertNotEqual(report["market_source_audit"]["r2_source_sha256"], "0" * 64)

            changed = dict(row, data_json=json.dumps({"fiscal_year": "2025/03", "sales": 9999,
                                                        "field_units": {"sales": "million_jpy"}}))
            changed_temp = Path(temp) / "changed"
            changed_temp.mkdir()
            args = self.make_args(changed_temp, phase="market-source-audit", table="financial_periods")
            report = migration.Migrator(args, FakeReader({"financial_periods": [changed]}), r2, "namespace").run()
            self.assertEqual(report["market_source_audit"]["status_counts"], {"source_values_differ": 1})
            self.assertEqual(report["market_source_audit"]["anomaly_counts_by_period_type_and_year"], {
                "source_values_differ|annual|2025": 1,
            })
            artifacts = Path(args.report).read_text(encoding="utf-8") + Path(args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn("9999", artifacts)
            self.assertNotIn("7203", artifacts)
            self.assertIn("sales", artifacts)

    def test_market_source_audit_keeps_aggregates_across_checkpoint_pages(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(
                temp,
                phase="market-source-audit",
                table="financial_periods",
                max_rows=20,
                batch_size=1,
            )
            rows = []
            objects = {}
            for index in range(4):
                code = str(7203 + index)
                source_key = f"market/irbank/v1/securities/{code}/overview.json"
                year = f"202{index}/03"
                payload = {
                    "fiscal_year": year,
                    "sales": 1234 + index,
                    "field_units": {"sales": "million_jpy"},
                }
                objects[source_key] = migration.canonical_json({
                    "financials": {
                        "annual": [{
                            **payload,
                            "accounting_standard": "J-GAAP",
                            "is_consolidated": True,
                        }],
                    },
                })
                rows.append({
                    "migration_rowid": index + 1,
                    "source_security_code": code,
                    "security_code_display": code,
                    "period_type": "annual",
                    "fiscal_year": year,
                    "accounting_standard": "J-GAAP",
                    "is_consolidated": 1,
                    "data_json": json.dumps(payload),
                })

            r2 = FakeR2(objects)
            report = migration.Migrator(
                args,
                FakeReader({"financial_periods": rows}),
                r2,
                "namespace",
            ).run()

            audit = report["market_source_audit"]
            self.assertTrue(report["complete"])
            self.assertEqual(audit["rows_checked"], 4)
            self.assertEqual(audit["status_counts"], {"source_values_match": 4})
            self.assertEqual(audit["r2_source_lookups"], 4)
            self.assertEqual(len(r2.get_calls), 4)
            self.assertNotEqual(audit["r2_source_sha256"], "0" * 64)

            saved_report = json.loads(Path(args.report).read_text(encoding="utf-8"))
            saved_checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertEqual(saved_report["market_source_audit"]["rows_checked"], 4)
            self.assertEqual(saved_checkpoint["market_source_audit"]["status_counts"], {
                "source_values_match": 4,
            })

    def test_market_source_audit_distinguishes_full_year_rows_in_other_categories(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-source-audit", table="financial_periods")
            source_key = "market/irbank/v1/securities/7203/overview.json"
            source = {"financials": {
                "annual": [],
                "quarterly": [{
                    "fiscal_year": "2025/03",
                    "accounting_standard": "J-GAAP",
                    "is_consolidated": True,
                    "quarter": "full_year",
                    "sales": 92384711,
                }],
                "semiannual": [{
                    "fiscal_year": "2025/03",
                    "accounting_standard": "J-GAAP",
                    "is_consolidated": True,
                    "quarter": "full_year",
                    "sales": 92384711,
                }],
            }}
            rows = [
                {
                    "migration_rowid": 1,
                    "security_code_display": "7203",
                    "period_type": "annual",
                    "fiscal_year": "2025/03",
                    "accounting_standard": "J-GAAP",
                    "is_consolidated": 1,
                    "data_json": json.dumps({"fiscal_year": "2025/03", "sales": 92384711}),
                },
                {
                    "migration_rowid": 2,
                    "security_code_display": "7203",
                    "period_type": "annual",
                    "fiscal_year": "2024/03",
                    "accounting_standard": "J-GAAP",
                    "is_consolidated": 1,
                    "data_json": json.dumps({"fiscal_year": "2024/03", "sales": 92384712}),
                },
            ]
            r2 = FakeR2({source_key: migration.canonical_json(source)})
            report = migration.Migrator(
                args, FakeReader({"financial_periods": rows}), r2, "namespace"
            ).run()
            audit = report["market_source_audit"]
            self.assertEqual(audit["status_counts"], {
                "source_category_mismatch": 1,
                "source_period_missing": 1,
            })
            self.assertEqual(audit["source_category_mismatch_counts"], {
                "annual->quarterly": 1,
                "annual->semiannual": 1,
            })
            self.assertEqual(audit["anomaly_counts_by_period_type_and_year"], {
                "source_category_mismatch|annual|2025": 1,
                "source_period_missing|annual|2024": 1,
            })
            artifacts = Path(args.report).read_text(encoding="utf-8")
            self.assertNotIn("92384711", artifacts)
            self.assertNotIn("92384712", artifacts)

    def test_market_source_audit_rejects_changed_r2_object_after_resume(self):
        with tempfile.TemporaryDirectory() as temp:
            source_key = "market/irbank/v1/securities/7203/overview.json"
            source = {"financials": {"annual": [
                {"fiscal_year": "2024/03", "accounting_standard": "J-GAAP",
                 "is_consolidated": True, "sales": 100},
                {"fiscal_year": "2025/03", "accounting_standard": "J-GAAP",
                 "is_consolidated": True, "sales": 200},
            ]}}
            rows = [
                {"migration_rowid": 1, "security_code_display": "7203", "period_type": "annual",
                 "fiscal_year": "2024/03", "accounting_standard": "J-GAAP", "is_consolidated": 1,
                 "data_json": json.dumps({"fiscal_year": "2024/03", "sales": 100})},
                {"migration_rowid": 2, "security_code_display": "7203", "period_type": "annual",
                 "fiscal_year": "2025/03", "accounting_standard": "J-GAAP", "is_consolidated": 1,
                 "data_json": json.dumps({"fiscal_year": "2025/03", "sales": 200})},
            ]
            r2 = FakeR2({source_key: migration.canonical_json(source)})
            first_args = self.make_args(
                temp, phase="market-source-audit", table="financial_periods", max_rows=2, batch_size=1
            )
            first = migration.Migrator(first_args, FakeReader({"financial_periods": rows}), r2, "namespace").run()
            self.assertFalse(first["complete"])
            checkpoint = json.loads(Path(first_args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertEqual(
                checkpoint["market_source_object_hashes"][migration.sha256(source_key.encode("utf-8"))],
                digest(migration.canonical_json(source)).hexdigest(),
            )
            self.assertNotIn(source_key, Path(first_args.checkpoint_out).read_text(encoding="utf-8"))
            checkpoint["market_source_object_hashes"] = {
                source_key: digest(migration.canonical_json(source)).hexdigest(),
            }
            Path(first_args.checkpoint_out).write_text(json.dumps(checkpoint), encoding="utf-8")

            changed_source = json.loads(migration.canonical_json(source))
            changed_source["financials"]["annual"][1]["sales"] = 201
            r2.objects[source_key] = migration.canonical_json(changed_source)
            resumed_args = self.make_args(
                temp, phase="market-source-audit", table="financial_periods", max_rows=2, batch_size=1
            )
            resumed = migration.Migrator(
                resumed_args, FakeReader({"financial_periods": rows}), r2, "namespace"
            )
            with self.assertRaisesRegex(migration.MigrationError, "R2 source object changed during audit") as error:
                resumed.run()
            self.assertNotIn(source_key, str(error.exception))

    def test_market_source_audit_errors_do_not_include_security_codes_or_object_keys(self):
        key = "market/irbank/v1/securities/7203/overview.json"
        row = {
            "migration_rowid": 1,
            "security_code_display": "7203",
            "period_type": "annual",
            "fiscal_year": "2025/03",
            "accounting_standard": "J-GAAP",
            "is_consolidated": 1,
            "data_json": json.dumps({"fiscal_year": "2025/03", "sales": 100}),
        }
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-source-audit", table="financial_periods")
            migrator = migration.Migrator(
                args,
                FakeReader({"financial_periods": [row]}),
                FakeR2({key: b"{"}),
                "namespace",
            )
            with self.assertRaisesRegex(migration.MigrationError, "R2 source document is invalid JSON") as error:
                migrator.run()
            self.assertNotIn("7203", str(error.exception))
            self.assertNotIn(key, str(error.exception))

        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="market-source-audit", table="financial_periods")
            row["data_json"] = "not-json"
            source = {"financials": {"annual": [{
                "fiscal_year": "2025/03",
                "accounting_standard": "J-GAAP",
                "is_consolidated": True,
                "sales": 100,
            }]}}
            migrator = migration.Migrator(
                args,
                FakeReader({"financial_periods": [row]}),
                FakeR2({key: migration.canonical_json(source)}),
                "namespace",
            )
            with self.assertRaisesRegex(migration.MigrationError, "Invalid JSON in financial_periods") as error:
                migrator.run()
            self.assertNotIn("7203", str(error.exception))
            self.assertNotIn(key, str(error.exception))

    def test_market_source_audit_flags_issuer_period_granularity(self):
        with tempfile.TemporaryDirectory() as temp:
            key = "market/irbank/v1/securities/7203/dividends.json"
            source = {"records": [{"fiscal_year": 2025, "event_type": "actual", "annual_dps": 90}]}
            row = {
                "migration_rowid": 1,
                "security_code_display": "7203",
                "fiscal_period": "2025-03",
                "event_type": "actual",
                "data_json": json.dumps({"fiscal_year": 2025, "period_end": "2025-03-31",
                                         "event_type": "actual", "annual_dps": 90}),
            }
            args = self.make_args(temp, phase="market-source-audit", table="issuer_dividend_periods")
            report = migration.Migrator(
                args,
                FakeReader({"issuer_dividend_periods": [row]}),
                FakeR2({key: migration.canonical_json(source)}),
                "namespace",
            ).run()
            audit = report["market_source_audit"]
            self.assertEqual(audit["status_counts"], {"period_granularity_gap": 1})
            self.assertNotIn("sample", audit)
            serialized = json.dumps(without_hash_values(report))
            self.assertNotIn("7203", serialized)
            self.assertNotIn(key, serialized)

    def test_market_source_audit_reports_dividend_fields_missing_from_r2(self):
        with tempfile.TemporaryDirectory() as temp:
            key = "market/irbank/v1/securities/7203/dividends.json"
            source = {"records": [{"fiscal_year": 2025, "annual_dps": 90}]}
            row = {
                "migration_rowid": 1,
                "security_code_display": "7203",
                "fiscal_year": 2025,
                "event_type": "actual",
                "annual_dps": 90,
                "date_status": "confirmed",
                "data_json": json.dumps({"fiscal_year": 2025, "annual_dps": 90, "date_status": "confirmed"}),
            }
            args = self.make_args(temp, phase="market-source-audit", table="dividend_events")
            report = migration.Migrator(
                args,
                FakeReader({"dividend_events": [row]}),
                FakeR2({key: migration.canonical_json(source)}),
                "namespace",
            ).run()
            audit = report["market_source_audit"]
            self.assertEqual(audit["status_counts"], {"source_values_differ": 1})
            self.assertEqual(audit["missing_field_counts"]["event_type"], 1)
            self.assertEqual(audit["missing_field_counts"]["date_status"], 1)
            self.assertNotIn("sample", audit)
            for artifact_path in (args.report, args.checkpoint_out):
                artifact = Path(artifact_path).read_text(encoding="utf-8")
                parsed_artifact = json.loads(artifact)
                sanitized_artifact = json.dumps(without_hash_values(parsed_artifact))
                self.assertNotIn("7203", sanitized_artifact)
                self.assertNotIn(key, artifact)
                self.assertNotIn('"sample"', artifact)

    def test_market_source_audit_counts_rows_with_missing_security_references(self):
        for table in ("financial_periods", "dividend_events", "issuer_dividend_periods"):
            with self.subTest(table=table), tempfile.TemporaryDirectory() as temp:
                row = {
                    "migration_rowid": 1,
                    "source_security_code": "77770",
                    "security_code_display": None,
                    "security_reference_missing": 1,
                    "data_json": "{}",
                }
                reader = FakeReader({table: [row]})
                args = self.make_args(temp, phase="market-source-audit", table=table)
                report = migration.Migrator(args, reader, FakeR2(), "namespace").run()
                audit = report["market_source_audit"]

                self.assertEqual(audit["rows_checked"], 1)
                self.assertEqual(audit["status_counts"], {"security_reference_missing": 1})
                self.assertNotIn("sample", audit)
                self.assertNotIn("77770", json.dumps(report))
                self.assertIn("LEFT JOIN securities", reader.queries[0][0])

    def test_resuming_legacy_market_source_audit_checkpoint_strips_row_samples(self):
        with tempfile.TemporaryDirectory() as temp:
            key = "market/private/object.json"
            args = self.make_args(temp, phase="market-source-audit", table="financial_periods")
            Path(args.checkpoint_in).write_text(json.dumps({
                "phase": "market-source-audit",
                "table": "financial_periods",
                "dry_run": True,
                "market_source_audit_version": migration.FINANCIAL_AUDIT_VERSION,
                "cursor": 10,
                "rolling_hash": "0" * 64,
                "r2_readback_sha256": "0" * 64,
                "market_source_audit": {
                    "rows_checked": 1,
                    "status_counts": {"source_values_differ": 1},
                    "sample": [{
                        "security_code": "PRIVATE_CODE",
                        "r2_source_key": key,
                        "period": "2025-03",
                    }],
                },
                "market_source_object_hashes": {key: "a" * 64},
            }), encoding="utf-8")
            migrator = migration.Migrator(
                args,
                FakeReader({"financial_periods": []}),
                FakeR2(),
                "namespace",
            )

            migrator.run()

            report = Path(args.report).read_text(encoding="utf-8")
            checkpoint = Path(args.checkpoint_out).read_text(encoding="utf-8")
            checkpoint_data = json.loads(checkpoint)
            self.assertNotIn("PRIVATE_CODE", report)
            self.assertNotIn(key, report)
            self.assertNotIn("PRIVATE_CODE", checkpoint)
            self.assertNotIn(key, checkpoint)
            self.assertNotIn('"sample"', report)
            self.assertNotIn('"sample"', checkpoint)
            self.assertIn(migration.sha256(key.encode("utf-8")), checkpoint_data["market_source_object_hashes"])

    def test_market_source_audit_is_read_only(self):
        with patch("sys.argv", ["migrate_d1_to_r2.py", "--phase", "market-source-audit",
                                "--table", "financial_periods", *CLI_FILE_ARGS,
                                "--write-r2", "--confirm-write", "WRITE_R2"]):
            with self.assertRaises(SystemExit):
                migration.arguments()

    def test_definition_phase_resumes_at_the_next_logical_key(self):
        definitions = {key: {"version": 1, "key": key} for key in migration.DEFINITION_FILES}
        first_key = next(iter(migration.DEFINITION_FILES))
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="definitions", max_rows=1)
            args.allow_d1_definition_fallback = True
            reader = FakeReader({"app_definition_documents": [{"value_json": json.dumps(definitions[first_key])}]})
            r2 = FakeR2()
            with patch.object(migration.Migrator, "read_kv", side_effect=lambda key: None if key == first_key else definitions[key]):
                first = migration.Migrator(args, reader, r2, "namespace").run()
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertFalse(first["complete"])
            self.assertEqual(checkpoint["definition_index"], 1)
            checkpoint["cursor"] = first_key
            Path(args.checkpoint_out).write_text(json.dumps(checkpoint), encoding="utf-8")

            with patch.object(migration.Migrator, "read_kv", side_effect=lambda key: definitions[key]):
                resumed = migration.Migrator(args, FakeReader(), r2, "namespace").run()
            self.assertTrue(resumed["complete"])
            self.assertEqual(resumed["cumulative"]["rows_read"], 1)
            checkpoint = json.loads(Path(args.checkpoint_out).read_text(encoding="utf-8"))
            self.assertNotIn("cursor", checkpoint)

    def test_definition_phase_fails_closed_without_kv_by_default(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="definitions")
            reader = FakeReader({"app_definition_documents": [{"value_json": json.dumps({"version": 1})}]})
            r2 = FakeR2()
            with patch.object(migration.Migrator, "read_kv", return_value=None):
                with self.assertRaisesRegex(migration.MigrationError, "KV definition missing"):
                    migration.Migrator(args, reader, r2, "namespace").run()

            self.assertEqual(reader.queries, [])
            self.assertEqual(r2.objects, {})

    def test_explicit_definition_fallback_reads_legacy_table(self):
        target_key = "diagnosis-definitions:v1"
        fallback_rows = [{"min_score": 90, "max_score": 100, "grade": "A"}]
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="definitions", max_rows=20)
            args.allow_d1_definition_fallback = True
            reader = FakeReader({"diagnosis_bands": fallback_rows})
            with patch.object(
                migration.Migrator,
                "read_kv",
                side_effect=lambda key: None if key == target_key else {"version": 1, "key": key},
            ):
                report = migration.Migrator(args, reader, FakeR2(), "namespace").run()

            self.assertEqual(report["rows_read"], 1)
            self.assertEqual(len(reader.queries), 1)
            self.assertIn("FROM diagnosis_bands", reader.queries[0][0])

    def test_definition_phase_prefers_live_kv_over_stale_d1_document(self):
        target_key = "stock-analysis-definitions:v1"
        live_value = {"version": 2, "source": "live-kv"}
        stale_value = {"version": 1, "source": "stale-d1"}
        live_definitions = {
            key: live_value if key == target_key else {"version": 1, "source": "live-kv", "key": key}
            for key in migration.DEFINITION_FILES
        }
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="definitions", dry_run=False, max_rows=20)
            reader = FakeReader({
                "app_definition_documents": [{
                    "definition_key": target_key,
                    "value_json": json.dumps(stale_value),
                }]
            })
            r2 = FakeR2()
            with patch.object(
                migration.Migrator,
                "read_kv",
                side_effect=lambda key: live_definitions[key],
            ):
                report = migration.Migrator(args, reader, r2, "namespace").run()

            version_key, pointer_key, expected_body = migration.definition_object_keys(target_key, live_value)
            self.assertTrue(report["complete"])
            self.assertEqual(report["rows_read"], 0)
            self.assertEqual(reader.queries, [])
            self.assertEqual(r2.get(version_key), expected_body)
            self.assertEqual(json.loads(r2.get(pointer_key)), {"version": 2})

    def test_cli_rejects_conflicting_dry_run_and_write_flags(self):
        with patch("sys.argv", ["migrate_d1_to_r2.py", "--phase", "analysis-current", *CLI_FILE_ARGS,
                                "--dry-run", "--write-r2", "--confirm-write", "WRITE_R2"]):
            with self.assertRaises(SystemExit):
                migration.arguments()

    def test_cli_limits_d1_definition_fallback_to_definitions_phase(self):
        with patch("sys.argv", ["migrate_d1_to_r2.py", "--phase", "definitions",
                                *CLI_FILE_ARGS, "--allow-d1-definition-fallback"]):
            args = migration.arguments()
            self.assertTrue(args.allow_d1_definition_fallback)
        with patch("sys.argv", ["migrate_d1_to_r2.py", "--phase", "analysis-current",
                                *CLI_FILE_ARGS, "--allow-d1-definition-fallback"]):
            with self.assertRaises(SystemExit):
                migration.arguments()

    def test_cli_requires_isolated_checkpoint_and_report_paths(self):
        with patch("sys.argv", ["migrate_d1_to_r2.py", "--phase", "analysis-current"]):
            with self.assertRaises(SystemExit):
                migration.arguments()

    def test_cli_rejects_table_choices_for_phases_that_do_not_select_a_table(self):
        for phase in ("analysis-history", "scheduled-runs", "definitions", "market-audit"):
            with self.subTest(phase=phase), patch(
                "sys.argv", ["migrate_d1_to_r2.py", "--phase", phase, "--table", "backend_state", *CLI_FILE_ARGS]
            ), self.assertRaises(SystemExit):
                migration.arguments()

    def test_cli_accepts_table_selection_only_for_supported_table_phases(self):
        for phase, table in (
            ("d1-operational-audit", "scheduled_run_log"),
            ("market-source-audit", "financial_periods"),
            ("legacy-market-archive", "securities"),
        ):
            with self.subTest(phase=phase), patch(
                "sys.argv", ["migrate_d1_to_r2.py", "--phase", phase, "--table", table, *CLI_FILE_ARGS]
            ):
                args = migration.arguments()
                self.assertEqual(args.table, table)

    def test_cli_rejects_read_budget_and_reserve_above_free_daily_limit(self):
        with patch(
            "sys.argv",
            [
                "migrate_d1_to_r2.py", "--phase", "analysis-current",
                *CLI_FILE_ARGS,
                "--max-rows", "100000", "--read-reserve-rows", "4900001",
            ],
        ), self.assertRaises(SystemExit):
            migration.arguments()


if __name__ == "__main__":
    unittest.main()
