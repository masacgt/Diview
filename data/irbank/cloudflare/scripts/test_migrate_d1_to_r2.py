import importlib.util
import json
import re
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


class FakeReader:
    def __init__(self, tables=None, reported_rows=None):
        self.tables = tables or {}
        self.reported_rows = reported_rows
        self.token = "test-token"
        self.queries = []

    def query(self, sql, params=None):
        self.queries.append((sql, params or []))
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
            checkpoint_in=str(root / "checkpoint.json"),
            checkpoint_out=str(root / "checkpoint.json"),
            report=str(root / "report.json"),
        )

    def test_only_single_select_is_accepted(self):
        reader = migration.D1Reader("account", "database", "token")
        for sql in ("DELETE FROM securities", "SELECT 1; DELETE FROM securities", "WITH x AS (SELECT 1) SELECT * FROM x"):
            with self.subTest(sql=sql), self.assertRaises(migration.MigrationError):
                reader.query(sql)

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
                "CLOUDFLARE_API_TOKEN": "analytics-read-token",
                "CLOUDFLARE_MIGRATION_READ_TOKEN": "read-token",
                "R2_ACCESS_KEY_ID": "access",
                "R2_SECRET_ACCESS_KEY": "secret",
            }
            argv = [
                "migrate_d1_to_r2.py", "--phase", "analysis-current",
                "--checkpoint-in", str(checkpoint_path), "--checkpoint-out", str(checkpoint_path),
                "--report", str(report_path),
            ]
            config = {"d1_databases": [{"database_id": "database"}], "kv_namespaces": [{"id": "namespace"}]}
            usage_report = {
                "utc_date": "2026-11-04", "queried_at_utc": "2026-11-04T09:00:00+00:00",
                "rows_read": 100, "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000, "limit_exceeded": False,
            }
            with (
                patch("sys.argv", argv),
                patch.dict("os.environ", env, clear=True),
                patch.object(migration, "load_config", return_value=config),
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
                patch.object(migration.d1_daily_usage, "fetch_daily_usage", wraps=migration.d1_daily_usage.fetch_daily_usage) as usage_query,
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
                patch.object(migration.d1_daily_usage, "fetch_daily_usage", return_value=usage_report) as usage_query,
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
            self.assertIn("started_at >= datetime('now','-365 days')", reader.queries[0][0])
            self.assertIn("operations/v1/runs/analysis/legacy-d1-2/summary.json", r2.objects)
            self.assertIn("operations/v1/runs/analysis/legacy-d1-4/summary.json", r2.objects)
            self.assertIn("operations/v1/runs/analysis/legacy-d1-5/summary.json", r2.objects)
            self.assertNotIn("operations/v1/runs/analysis/legacy-d1-1/summary.json", r2.objects)
            pointer = json.loads(r2.objects["operations/v1/runs/legacy-d1/current.json"])
            self.assertEqual(pointer["run_key"], "operations/v1/runs/analysis/legacy-d1-5/summary.json")
            self.assertEqual(pointer["run_id"], "5")
            checkpoint_text = Path(migrator.args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn('"run_key"', checkpoint_text)
            self.assertNotIn("operations/v1/runs/analysis/legacy-d1-5/summary.json", checkpoint_text)

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
            pointer = json.loads(r2.objects["operations/v1/runs/legacy-d1/current.json"])
            self.assertEqual(pointer["run_key"], old_key)
            checkpoint_text = Path(args.checkpoint_out).read_text(encoding="utf-8")
            report_text = Path(args.report).read_text(encoding="utf-8")
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
            artifacts = {
                "checkpoint": without_hash_values(json.loads(
                    Path(args.checkpoint_out).read_text(encoding="utf-8")
                )),
                "report": without_hash_values(json.loads(
                    Path(args.report).read_text(encoding="utf-8")
                )),
            }
            serialized_artifacts = json.dumps(artifacts, sort_keys=True)
            self.assertNotIn("DO_NOT_LOG", serialized_artifacts)
            self.assertNotIn("7203", serialized_artifacts)
            self.assertNotIn("8306", serialized_artifacts)
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
            self.assertNotIn("7203", checkpoint_text + report_text)
            self.assertNotIn("8306", checkpoint_text + report_text)
            self.assertNotIn("PRIVATE_COMPANY_NAME", checkpoint_text + report_text)
            self.assertNotIn("DO_NOT_LOG", checkpoint_text + report_text)

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
            self.assertNotIn("7203", checkpoint_text + report_text)
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

    def test_d1_schema_audit_lists_table_and_trigger_names_without_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            reader = FakeReader({"sqlite_schema": [
                {"type": "table", "name": "sqlite_sequence", "tbl_name": "sqlite_sequence"},
                {"type": "table", "name": "support_feedback", "tbl_name": "support_feedback"},
                {"type": "trigger", "name": "support_feedback_email_insert", "tbl_name": "support_feedback"},
            ]})
            r2 = FakeR2()
            report = migration.Migrator(
                self.make_args(temp, phase="d1-schema-audit"), reader, r2, "namespace"
            ).run()

            self.assertTrue(report["complete"])
            self.assertEqual(report["source_rows"], 3)
            self.assertEqual(
                report["schema_objects"],
                [
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
                "rows_read": 100, "rows_remaining": 4_999_900,
                "daily_limit_rows": 5_000_000, "limit_exceeded": False,
            }

            def fetch_usage(account_id, token, utc_date):
                events.append(("analytics", account_id, token))
                return {**usage_report, "utc_date": utc_date}

            def create_reader(account_id, database_id, token):
                events.append(("d1", account_id, token))
                return FakeReader({"sqlite_schema": []})

            with (
                patch("sys.argv", args),
                patch.dict("os.environ", env, clear=True),
                patch.object(migration, "load_config", return_value=config),
                patch.object(migration.d1_daily_usage, "fetch_daily_usage", side_effect=fetch_usage),
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
                ["migrate_d1_to_r2.py", "--phase", phase, *table_args, "--write-r2", "--confirm-write", "WRITE_R2"],
            ), self.assertRaises(SystemExit):
                migration.arguments()

    def test_market_audit_reports_missing_rows_without_security_code_samples(self):
        with tempfile.TemporaryDirectory() as temp:
            catalog = migration.canonical_json({"generation": "g1", "items": [{"code": "7203"}]})
            rows = [
                {"migration_rowid": rowid, "security_code": code, "security_code_display": code}
                for rowid, code in enumerate(("7203", "8306", "9432", "9501"), start=1)
            ]
            migrator = migration.Migrator(
                self.make_args(temp, phase="market-audit"),
                FakeReader({"securities": rows}),
                FakeR2({"market/yfinance/v2/catalog/current.json": catalog}),
                "namespace",
            )
            report = migrator.run()
            self.assertEqual(report["d1_security_missing_count"], 3)
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
            artifacts = Path(args.report).read_text(encoding="utf-8") + Path(args.checkpoint_out).read_text(encoding="utf-8")
            self.assertNotIn("9999", artifacts)
            self.assertIn("sales", artifacts)

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
            serialized = json.dumps(report)
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
                self.assertNotIn("7203", artifact)
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
                                "--table", "financial_periods", "--write-r2", "--confirm-write", "WRITE_R2"]):
            with self.assertRaises(SystemExit):
                migration.arguments()

    def test_definition_phase_resumes_at_the_next_logical_key(self):
        definitions = {key: {"version": 1, "key": key} for key in migration.DEFINITION_FILES}
        first_key = next(iter(migration.DEFINITION_FILES))
        with tempfile.TemporaryDirectory() as temp:
            args = self.make_args(temp, phase="definitions", max_rows=1)
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
        with patch("sys.argv", ["migrate_d1_to_r2.py", "--phase", "analysis-current", "--dry-run", "--write-r2", "--confirm-write", "WRITE_R2"]):
            with self.assertRaises(SystemExit):
                migration.arguments()

    def test_cli_rejects_table_choices_for_phases_that_do_not_select_a_table(self):
        for phase in ("analysis-history", "scheduled-runs", "definitions", "market-audit"):
            with self.subTest(phase=phase), patch(
                "sys.argv", ["migrate_d1_to_r2.py", "--phase", phase, "--table", "backend_state"]
            ), self.assertRaises(SystemExit):
                migration.arguments()

    def test_cli_accepts_table_selection_only_for_supported_table_phases(self):
        for phase, table in (
            ("d1-operational-audit", "scheduled_run_log"),
            ("market-source-audit", "financial_periods"),
            ("legacy-market-archive", "securities"),
        ):
            with self.subTest(phase=phase), patch(
                "sys.argv", ["migrate_d1_to_r2.py", "--phase", phase, "--table", table]
            ):
                args = migration.arguments()
                self.assertEqual(args.table, table)

    def test_cli_rejects_read_budget_and_reserve_above_free_daily_limit(self):
        with patch(
            "sys.argv",
            [
                "migrate_d1_to_r2.py", "--phase", "analysis-current",
                "--max-rows", "100000", "--read-reserve-rows", "4900001",
            ],
        ), self.assertRaises(SystemExit):
            migration.arguments()


if __name__ == "__main__":
    unittest.main()
