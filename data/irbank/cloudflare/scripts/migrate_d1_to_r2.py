#!/usr/bin/env python3
"""Resumable, read-only D1 export to reviewed R2 namespaces.

The default mode is a dry run. This script never executes D1 writes or deletes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import sys
import tomllib
import unicodedata
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
WRANGLER = ROOT / "wrangler.toml"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts import d1_daily_usage


DEFAULT_READ_RESERVE_ROWS = 100_000
WRITE_SET_DIGEST_ALGORITHM = "rolling-sha256-v1"
ALLOWED_PHASES = {
    "market-audit",
    "market-source-audit",
    "d1-schema-audit",
    "d1-operational-audit",
    "analysis-current",
    "analysis-history",
    "scheduled-runs",
    "definitions",
    "definition-audit",
    "analysis-source",
    "legacy-market-archive",
}
ARCHIVE_TABLES = {
    "securities",
    "financial_periods",
    "dividend_events",
    "issuer_dividend_periods",
    "market_price_refresh_runs",
    "market_price_refresh_batches",
}
MARKET_SOURCE_AUDIT_TABLES = {
    "financial_periods",
    "dividend_events",
    "issuer_dividend_periods",
}
OPERATIONAL_AUDIT_TABLES = {
    "backend_state",
    "scheduled_run_log",
    "market_price_refresh_runs",
    "market_price_refresh_batches",
}
TABLE_SELECTION_PHASES = {
    "market-source-audit": MARKET_SOURCE_AUDIT_TABLES,
    "legacy-market-archive": ARCHIVE_TABLES,
    "d1-operational-audit": OPERATIONAL_AUDIT_TABLES,
}
DEFINITION_FILES = {
    "backend-config:v1": "backend-config",
    "diagnosis-definitions:v1": "diagnosis-definitions",
    "valuation-scenarios:v1": "valuation-scenarios",
    "stock-analysis-definitions:v1": "stock-analysis-definitions",
    "watchlist-analysis-definitions:v1": "watchlist-analysis-definitions",
    "portfolio-analysis-definitions:v1": "portfolio-analysis-definitions",
}
DEFINITION_TABLES = {
    "diagnosis-definitions:v1": "SELECT min_score, max_score, grade, title, comment FROM diagnosis_bands ORDER BY min_score DESC",
    "valuation-scenarios:v1": "SELECT id, per_max, pbr_max, title, badge, comment FROM valuation_scenarios ORDER BY id",
}


FINANCIAL_AUDIT_VERSION = 3
IRBANK_D1_FINANCIAL_FIELD_UNITS = {
    "sales": "million_jpy",
    "operating_profit": "million_jpy",
    "ordinary_income": "million_jpy",
    "net_income": "million_jpy",
    "eps": "jpy_per_share",
    "operating_cf": "million_jpy",
    "investing_cf": "million_jpy",
    "extraordinary_income": "million_jpy",
    "extraordinary_loss": "million_jpy",
    "total_assets": "million_jpy",
    "net_assets": "million_jpy",
    "total_liabilities": "million_jpy",
    "equity_ratio_computed": "percent",
}
JPY_UNITS_TO_JPY = {
    "jpy": 1.0,
    "thousand_jpy": 1_000.0,
    "million_jpy": 1_000_000.0,
    "hundred_million_jpy": 100_000_000.0,
}


class MigrationError(RuntimeError):
    pass


def canonical_json(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def roll_object_digest(previous: str, key: str, body: bytes) -> str:
    fingerprint = sha256(key.encode("utf-8") + b"\0" + body)
    return sha256((previous + fingerprint).encode("ascii"))


def parse_json_value(value: object, context: str) -> object:
    try:
        if isinstance(value, (dict, list)):
            return value
        return json.loads(str(value))
    except (TypeError, json.JSONDecodeError) as error:
        raise MigrationError(f"Invalid JSON in {context}") from error


def values_equal(left: object, right: object) -> bool:
    if isinstance(left, bool) or isinstance(right, bool):
        return type(left) is type(right) and left == right
    if isinstance(left, (int, float)) and isinstance(right, (int, float)):
        return math.isclose(float(left), float(right), rel_tol=1e-10, abs_tol=1e-9)
    if isinstance(left, dict) and isinstance(right, dict):
        return left.keys() == right.keys() and all(values_equal(left[key], right[key]) for key in left)
    if isinstance(left, list) and isinstance(right, list):
        return len(left) == len(right) and all(values_equal(a, b) for a, b in zip(left, right))
    return left == right


def audit_financial_value_differences(source: dict, target: dict) -> tuple[list[str], list[str], int, list[str], dict[str, int]]:
    """Compare legacy D1 financial values in their documented source units."""
    ignored = {"period_end", "fiscal_period", "period", "fiscal_year", "field_units"}
    source_fields = {key: value for key, value in source.items() if key not in ignored}
    target_fields = {key: value for key, value in target.items() if key not in ignored}
    common = [key for key in source_fields if key in target_fields]
    missing = sorted(key for key in source_fields if key not in target_fields)
    source_units = dict(IRBANK_D1_FINANCIAL_FIELD_UNITS)
    explicit_source_units = source.get("field_units")
    if isinstance(explicit_source_units, dict):
        for key, unit in explicit_source_units.items():
            if isinstance(unit, str) and unit.strip():
                source_units[key] = unit.strip()
    target_units = target.get("field_units") if isinstance(target.get("field_units"), dict) else {}
    different: list[str] = []
    unit_incomplete: list[str] = []
    conversions: dict[str, int] = {}

    for key in common:
        left, right = source_fields[key], target_fields[key]
        if left is None or right is None:
            if not values_equal(left, right):
                different.append(key)
            continue
        numeric = (
            isinstance(left, (int, float)) and not isinstance(left, bool)
            and isinstance(right, (int, float)) and not isinstance(right, bool)
        )
        if not numeric:
            if not values_equal(left, right):
                different.append(key)
            continue

        source_unit = source_units.get(key)
        target_unit = target_units.get(key)
        source_unit = str(source_unit).strip() if source_unit else None
        target_unit = str(target_unit).strip() if target_unit else None
        if not source_unit and not target_unit:
            if not values_equal(left, right):
                different.append(key)
            continue
        if not source_unit or not target_unit:
            unit_incomplete.append(key)
            continue
        if source_unit == target_unit:
            equal = values_equal(left, right)
        elif source_unit in JPY_UNITS_TO_JPY and target_unit in JPY_UNITS_TO_JPY:
            factor = JPY_UNITS_TO_JPY[source_unit] / JPY_UNITS_TO_JPY[target_unit]
            tolerance = 0.5 * factor
            equal = math.isclose(float(left) * factor, float(right), rel_tol=1e-10, abs_tol=tolerance)
            pair = f"{source_unit}->{target_unit}"
            conversions[pair] = conversions.get(pair, 0) + 1
        else:
            unit_incomplete.append(key)
            continue
        if not equal:
            different.append(key)

    return missing, sorted(different), len(common), sorted(unit_incomplete), conversions


def normalize_period(value: object) -> str:
    raw = str(value or "").strip().replace("/", "-")
    return raw[:10] if re.fullmatch(r"\d{4}-\d{2}-\d{2}.*", raw) else raw


def period_matches(left: object, right: object) -> tuple[bool, bool]:
    a, b = normalize_period(left), normalize_period(right)
    if not a or not b:
        return False, False
    if a == b:
        return True, False
    if len(a) == 7 and b.startswith(a + "-") or len(b) == 7 and a.startswith(b + "-"):
        return True, True
    return False, False


def normalize_boolean(value: object) -> bool | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)) and value in {0, 1}:
        return bool(value)
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes"}:
            return True
        if normalized in {"0", "false", "no"}:
            return False
    raise MigrationError("Unsupported boolean value in market source audit")


class D1Reader:
    def __init__(self, account_id: str, database_id: str, token: str):
        self.endpoint = f"https://api.cloudflare.com/client/v4/accounts/{account_id}/d1/database/{database_id}/query"
        self.token = token

    def query(self, sql: str, params: list[object] | None = None) -> tuple[list[dict], dict]:
        if not re.match(r"^\s*SELECT\b", sql, re.IGNORECASE) or ";" in sql:
            raise MigrationError("D1 migration queries must be a single SELECT statement")
        request = Request(
            self.endpoint,
            data=json.dumps({"sql": sql, "params": params or []}).encode("utf-8"),
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urlopen(request, timeout=60) as response:
                payload = json.load(response)
        except HTTPError as error:
            # D1 queries use POST. A failed response does not prove the query
            # was not executed, so never retry and silently consume more budget.
            raise MigrationError(f"D1 read failed with HTTP {error.code}; automatic retry was not attempted") from error
        except (OSError, json.JSONDecodeError) as error:
            raise MigrationError("D1 read outcome is uncertain; automatic retry was not attempted") from error
        if not payload.get("success"):
            raise MigrationError("D1 read returned an unsuccessful response")
        results = payload.get("result") or []
        first = results[0] if results else {}
        return first.get("results") or [], first.get("meta") or {}


class R2Store:
    def __init__(self, account_id: str, access_key: str, secret_key: str, bucket: str):
        try:
            import boto3
        except ImportError as error:
            raise MigrationError("boto3 is required for R2 access") from error
        self.client = boto3.client(
            "s3",
            endpoint_url=f"https://{account_id}.r2.cloudflarestorage.com",
            region_name="auto",
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
        )
        event_system = self.client.meta.events

        def process_custom_arguments(params: dict, context: dict, **_kwargs) -> None:
            custom_headers = params.pop("custom_headers", None)
            if custom_headers:
                context["custom_headers"] = custom_headers

        def add_custom_headers(params: dict, context: dict, **_kwargs) -> None:
            custom_headers = context.get("custom_headers")
            if custom_headers:
                params["headers"].update(custom_headers)

        # Botocore rejects If-Match / If-None-Match as put_object arguments.
        # Cloudflare documents these as custom HTTP headers for boto3.
        event_system.register("before-parameter-build.s3.PutObject", process_custom_arguments)
        event_system.register("before-call.s3.PutObject", add_custom_headers)
        self.bucket = bucket

    def get_with_etag(self, key: str) -> tuple[bytes, str] | None:
        from botocore.exceptions import ClientError

        try:
            response = self.client.get_object(Bucket=self.bucket, Key=key)
            body = response["Body"].read()
            response["Body"].close()
            etag = str(response.get("ETag") or "").strip('"')
            if not etag:
                raise MigrationError("R2 object has no ETag required for a safe update")
            return body, etag
        except ClientError as error:
            status = error.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
            if status == 404 or error.response.get("Error", {}).get("Code") in {"404", "NoSuchKey"}:
                return None
            raise MigrationError("R2 read failed") from error

    def get(self, key: str) -> bytes | None:
        result = self.get_with_etag(key)
        return result[0] if result is not None else None

    def _put_conditionally(self, key: str, body: bytes, condition: dict[str, str]) -> bool:
        from botocore.exceptions import ClientError

        try:
            self.client.put_object(
                Bucket=self.bucket,
                Key=key,
                Body=body,
                ContentType="application/json; charset=utf-8",
                custom_headers=condition,
            )
            return True
        except ClientError as error:
            status = error.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
            code = error.response.get("Error", {}).get("Code")
            if status == 412 or code in {"PreconditionFailed", "ConditionalRequestConflict"}:
                return False
            raise MigrationError("R2 write failed") from error
        except Exception as error:
            raise MigrationError("R2 write failed") from error

    def put_if_absent(self, key: str, body: bytes) -> bool:
        return self._put_conditionally(key, body, {"If-None-Match": "*"})

    def put_if_unchanged(self, key: str, body: bytes, etag: str) -> bool:
        return self._put_conditionally(key, body, {"If-Match": f'"{etag}"'})

    def list_keys(self, prefix: str) -> list[str]:
        try:
            pages = self.client.get_paginator("list_objects_v2").paginate(Bucket=self.bucket, Prefix=prefix)
            return [item["Key"] for page in pages for item in page.get("Contents", [])]
        except Exception as error:
            raise MigrationError("R2 list failed") from error


def load_config() -> dict:
    with WRANGLER.open("rb") as file:
        return tomllib.load(file)


def rate_rows(meta: dict) -> int:
    value = meta.get("rows_read")
    try:
        count = int(value)
    except (TypeError, ValueError) as error:
        raise MigrationError("D1 response omitted rows_read; cannot enforce the read budget") from error
    if count < 0:
        raise MigrationError("D1 returned an invalid rows_read count")
    return count


def definition_object_keys(key: str, value: object) -> tuple[str, str, bytes]:
    version = str(value.get("version", 1)) if isinstance(value, dict) else "1"
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", version):
        raise MigrationError("Unsupported definition version")
    encoded = quote(key, safe="")
    root = f"definitions/v1/{encoded}/"
    return f"{root}{quote(version, safe='')}.json", f"{root}current.json", canonical_json(value)


def analysis_history_key(row: dict) -> str:
    code = str(row.get("security_code") or "").upper()
    observed = str(row.get("observed_at") or "")
    if not re.fullmatch(r"[A-Z0-9]{4,5}", code):
        raise MigrationError("Invalid security code in analysis history")
    try:
        date = datetime.fromisoformat(observed.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError as error:
        raise MigrationError("Invalid observed_at in analysis history") from error
    return f"analysis/v1/history/{code}/{date:%Y}/{date:%m}/{date:%Y%m%d}-{int(row['id'])}.json"


class Migrator:
    def __init__(self, args: argparse.Namespace, reader: D1Reader, r2: R2Store, namespace_id: str):
        self.args = args
        self.reader = reader
        self.r2 = r2
        self.namespace_id = namespace_id
        self.market_source_cache: dict[str, tuple[dict | None, bytes | None]] = {}
        self.analysis_history_keys: set[str] | None = None
        self.report = {
            "phase": args.phase,
            "table": args.table if args.phase in {"legacy-market-archive", "market-source-audit", "d1-operational-audit"} else None,
            "dry_run": args.dry_run,
            "checkpoint_restarted_for_mode_change": False,
            "checkpoint_restarted_for_cursor_change": False,
            "checkpoint_restarted_for_financial_audit_version_change": False,
            "read_budget_rows": args.max_rows,
            "account_daily_usage_preflight": getattr(args, "account_daily_usage_preflight", None),
            "rows_read": 0,
            "d1_queries_attempted": 0,
            "d1_read_count_unknown": False,
            "d1_rows_returned_without_meta": 0,
            "budget_exceeded": False,
            "source_rows": 0,
            "source_rows_returned": 0,
            "objects_written": 0,
            "objects_planned": 0,
            "objects_unchanged": 0,
            "objects_skipped_newer": 0,
            "objects_superseded_newer": 0,
            "objects_verified": 0,
            "verification_failures": 0,
            "conflicts": 0,
            "complete": False,
            "started_at": datetime.now(timezone.utc).isoformat(),
        }
        self.checkpoint = self.load_checkpoint()
        self.digest = str(self.checkpoint.get("rolling_hash") or "0" * 64)
        self.verified_digest = str(self.checkpoint.get("r2_readback_sha256") or "0" * 64)
        self.write_set_digest = str(self.checkpoint.get("write_set_sha256") or "0" * 64)
        self.write_set_hash_prefix_complete = bool(self.checkpoint.get("write_set_hash_prefix_complete", True))
        self.source_time_ranges = dict(self.checkpoint.get("source_time_ranges") or {})
        self.r2_time_ranges = dict(self.checkpoint.get("r2_time_ranges") or {})
        saved_totals = self.checkpoint.get("totals") or {}
        self.base_totals = {
            key: max(0, int(saved_totals.get(key, 0)))
            for key in (
                "rows_read", "source_rows", "objects_written", "objects_planned", "objects_unchanged",
                "objects_skipped_newer", "objects_superseded_newer", "objects_verified", "verification_failures",
            )
        }

    def load_checkpoint(self) -> dict:
        if not self.args.checkpoint_in or not Path(self.args.checkpoint_in).exists():
            return {
                "phase": self.args.phase,
                "table": self.args.table,
                "dry_run": self.args.dry_run,
                "cursor": None,
                "rolling_hash": "0" * 64,
                "write_set_sha256": "0" * 64,
                "write_set_digest_algorithm": WRITE_SET_DIGEST_ALGORITHM,
                "write_set_hash_prefix_complete": True,
            }
        value = json.loads(Path(self.args.checkpoint_in).read_text(encoding="utf-8"))
        if value.get("phase") != self.args.phase or value.get("table") != self.args.table:
            raise MigrationError("Checkpoint phase/table does not match this dispatch")
        if value.get("dry_run") is not self.args.dry_run:
            self.report["checkpoint_restarted_for_mode_change"] = True
            return {
                "phase": self.args.phase,
                "table": self.args.table,
                "dry_run": self.args.dry_run,
                "cursor": None,
                "rolling_hash": "0" * 64,
                "write_set_sha256": "0" * 64,
                "write_set_digest_algorithm": WRITE_SET_DIGEST_ALGORITHM,
                "write_set_hash_prefix_complete": True,
            }
        if (
            self.args.phase == "market-source-audit"
            and self.args.table == "financial_periods"
            and value.get("market_source_audit_version") != FINANCIAL_AUDIT_VERSION
        ):
            self.report["checkpoint_restarted_for_financial_audit_version_change"] = True
            return {
                "phase": self.args.phase,
                "table": self.args.table,
                "dry_run": self.args.dry_run,
                "cursor": None,
                "rolling_hash": "0" * 64,
                "write_set_sha256": "0" * 64,
                "write_set_digest_algorithm": WRITE_SET_DIGEST_ALGORITHM,
                "write_set_hash_prefix_complete": True,
            }
        if not re.fullmatch(r"[a-fA-F0-9]{64}", str(value.get("rolling_hash") or "")):
            raise MigrationError("Checkpoint has an invalid rolling hash")
        if not re.fullmatch(r"[a-fA-F0-9]{64}", str(value.get("r2_readback_sha256") or "0" * 64)):
            raise MigrationError("Checkpoint has an invalid R2 readback hash")
        write_set_algorithm = value.get("write_set_digest_algorithm")
        write_set_hash = value.get("write_set_sha256")
        write_set_prefix_complete = value.get("write_set_hash_prefix_complete")
        if write_set_algorithm is None and write_set_hash is None and write_set_prefix_complete is None:
            totals = value.get("totals") or {}
            try:
                prior_output_count = int(totals.get("objects_planned", 0)) + int(totals.get("objects_written", 0))
            except (AttributeError, TypeError, ValueError) as error:
                raise MigrationError("Checkpoint has invalid output totals") from error
            value["write_set_sha256"] = "0" * 64
            value["write_set_digest_algorithm"] = WRITE_SET_DIGEST_ALGORITHM
            value["write_set_hash_prefix_complete"] = prior_output_count == 0
        else:
            if write_set_algorithm != WRITE_SET_DIGEST_ALGORITHM:
                raise MigrationError("Checkpoint has an unsupported write-set digest algorithm")
            if not re.fullmatch(r"[a-fA-F0-9]{64}", str(write_set_hash or "")):
                raise MigrationError("Checkpoint has an invalid write-set digest")
            if not isinstance(write_set_prefix_complete, bool):
                raise MigrationError("Checkpoint has an invalid write-set digest completeness flag")
        if self.args.phase == "analysis-current":
            if value.get("cursor_kind") != "rowid-v1":
                # Earlier checkpoints stored a security code as the cursor.
                # Restart idempotently instead of coercing it to a rowid and
                # accidentally skipping source rows.
                self.report["checkpoint_restarted_for_cursor_change"] = True
                return {
                    "phase": self.args.phase,
                    "table": self.args.table,
                    "dry_run": self.args.dry_run,
                    "cursor": 0,
                    "cursor_kind": "rowid-v1",
                    "rolling_hash": "0" * 64,
                    "r2_readback_sha256": "0" * 64,
                }
            cursor = value.get("cursor")
            if cursor is not None and (isinstance(cursor, bool) or not isinstance(cursor, int) or cursor < 0):
                raise MigrationError("Checkpoint has an invalid analysis-current rowid cursor")
        elif self.args.phase == "definitions":
            # definition_index is the resumable cursor; older checkpoints also
            # copied the logical KV key into `cursor` unnecessarily.
            value.pop("cursor", None)
        # Sanitize legacy identifiers before writing any resumed checkpoint.
        for field in ("d1_security_missing_sample", "d1_security_invalid_sample"):
            value.pop(field, None)
        field_audit = value.get("market_audit_fields")
        if isinstance(field_audit, dict):
            for field in (
                "name_mismatch_sample", "edinet_mismatch_sample", "issuer_index_missing_sample",
                "metrics_nonempty_sample", "metrics_invalid_sample",
            ):
                field_audit.pop(field, None)
        source_audit = value.get("market_source_audit")
        if isinstance(source_audit, dict):
            source_audit.pop("sample", None)
        source_hashes = value.get("market_source_object_hashes")
        if isinstance(source_hashes, dict):
            sanitized_hashes = {}
            for object_key, object_hash in source_hashes.items():
                key_text = str(object_key)
                if re.fullmatch(r"[a-fA-F0-9]{64}", key_text):
                    key_fingerprint = key_text
                else:
                    key_fingerprint = sha256(key_text.encode("utf-8"))
                sanitized_hashes[key_fingerprint] = object_hash
            value["market_source_object_hashes"] = sanitized_hashes
        latest_run = value.get("latest_scheduled_run")
        if isinstance(latest_run, dict):
            latest_run.pop("run_key", None)
        return value

    def save_outputs(self) -> None:
        self.report["source_sha256"] = self.digest
        cumulative = {key: self.base_totals[key] + int(self.report.get(key, 0)) for key in self.base_totals}
        self.report["cumulative"] = cumulative
        self.report["source_time_ranges"] = self.source_time_ranges
        self.report["r2_time_ranges"] = self.r2_time_ranges
        self.report["r2_readback_sha256"] = self.verified_digest
        self.report["write_set_sha256"] = self.write_set_digest
        self.report["write_set_digest_algorithm"] = WRITE_SET_DIGEST_ALGORITHM
        self.report["write_set_hash_complete"] = (
            self.write_set_hash_prefix_complete
            and bool(self.report.get("complete"))
            and self.report.get("status") != "failed"
        )
        self.report["finished_at"] = datetime.now(timezone.utc).isoformat()
        report_body = canonical_json(self.report)
        self.report["report_sha256"] = sha256(report_body)
        Path(self.args.report).write_bytes(canonical_json(self.report) + b"\n")
        self.checkpoint["rolling_hash"] = self.digest
        self.checkpoint["dry_run"] = self.args.dry_run
        if self.args.phase == "market-source-audit" and self.args.table == "financial_periods":
            self.checkpoint["market_source_audit_version"] = FINANCIAL_AUDIT_VERSION
        self.checkpoint["r2_readback_sha256"] = self.verified_digest
        self.checkpoint["write_set_sha256"] = self.write_set_digest
        self.checkpoint["write_set_digest_algorithm"] = WRITE_SET_DIGEST_ALGORITHM
        self.checkpoint["write_set_hash_prefix_complete"] = self.write_set_hash_prefix_complete
        self.checkpoint["source_time_ranges"] = self.source_time_ranges
        self.checkpoint["r2_time_ranges"] = self.r2_time_ranges
        self.checkpoint["totals"] = cumulative
        self.checkpoint["complete"] = bool(self.report.get("complete"))
        self.checkpoint["updated_at"] = datetime.now(timezone.utc).isoformat()
        Path(self.args.checkpoint_out).write_bytes(canonical_json(self.checkpoint) + b"\n")

    def read_page(self, sql: str, params: list[object]) -> list[dict]:
        if not re.match(r"^\s*SELECT\b", sql, re.IGNORECASE) or ";" in sql:
            raise MigrationError("D1 migration queries must be a single SELECT statement")
        if self.report["rows_read"] >= self.args.max_rows:
            raise MigrationError("D1 read budget is exhausted; no further D1 query was issued")
        self.report["d1_queries_attempted"] += 1
        try:
            rows, meta = self.reader.query(sql, params)
        except Exception:
            self.report["d1_read_count_unknown"] = True
            raise
        try:
            count = max(rate_rows(meta), len(rows))
        except MigrationError:
            self.report["d1_read_count_unknown"] = True
            self.report["d1_rows_returned_without_meta"] += len(rows)
            self.report["source_rows_returned"] += len(rows)
            raise
        total = self.report["rows_read"] + count
        self.report["rows_read"] = total
        self.report["source_rows_returned"] += len(rows)
        if total > self.args.max_rows:
            self.report["budget_exceeded"] = True
            raise MigrationError("D1 read budget exceeded; rerun with a smaller batch or a larger approved budget")
        self.report["source_rows"] += len(rows)
        return rows

    @staticmethod
    def normalize_time(value: object) -> str | None:
        if not isinstance(value, str) or not value.strip():
            return None
        raw = value.strip()
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", raw):
            try:
                return date.fromisoformat(raw).isoformat()
            except ValueError:
                return None
        try:
            parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
        except ValueError:
            try:
                return date.fromisoformat(raw).isoformat()
            except ValueError:
                return None

    @classmethod
    def collect_time_ranges(cls, value: object, ranges: dict[str, dict[str, str]]) -> None:
        if isinstance(value, dict):
            for key, item in value.items():
                if isinstance(key, str) and (key.endswith("_at") or key.endswith("_date") or key in {"date", "period_end"}):
                    normalized = cls.normalize_time(item)
                    if normalized is not None:
                        current = ranges.setdefault(key, {"min": normalized, "max": normalized})
                        current["min"] = min(current["min"], normalized)
                        current["max"] = max(current["max"], normalized)
                cls.collect_time_ranges(item, ranges)
        elif isinstance(value, list):
            for item in value:
                cls.collect_time_ranges(item, ranges)

    def record_source_row(self, row: object) -> None:
        self.digest = sha256((self.digest + sha256(canonical_json(row))).encode("ascii"))
        self.collect_time_ranges(row, self.source_time_ranges)

    def record_r2_readback(self, key: str, body: bytes) -> None:
        try:
            decoded = json.loads(body)
        except json.JSONDecodeError as error:
            self.report["verification_failures"] += 1
            raise MigrationError("R2 readback is not valid JSON") from error
        self.report["objects_verified"] += 1
        self.verified_digest = sha256((self.verified_digest + sha256(key.encode("utf-8") + b"\0" + body)).encode("ascii"))
        self.collect_time_ranges(decoded, self.r2_time_ranges)

    def record_write_set_object(self, key: str, body: bytes) -> None:
        self.write_set_digest = roll_object_digest(self.write_set_digest, key, body)

    def verify_written_object(self, key: str, expected: bytes) -> bool:
        actual = self.r2.get(key)
        if actual == expected:
            self.record_r2_readback(key, actual)
            return True
        self.report["verification_failures"] += 1
        return False

    def put_immutable(self, key: str, body: bytes, known_keys: set[str] | None = None) -> None:
        # A complete R2 listing avoids one GET per absent history object. The
        # conditional PUT below still protects against objects created after
        # the listing was captured.
        existing = self.r2.get(key) if known_keys is None or key in known_keys else None
        if existing is not None:
            if sha256(existing) == sha256(body):
                self.report["objects_unchanged"] += 1
                self.record_r2_readback(key, existing)
                return
            self.report["conflicts"] += 1
            raise MigrationError("R2 key already contains different data; review required")
        if self.args.dry_run:
            self.report["objects_planned"] += 1
            self.record_write_set_object(key, body)
            return
        if self.r2.put_if_absent(key, body):
            if known_keys is not None:
                known_keys.add(key)
            self.report["objects_written"] += 1
            self.record_write_set_object(key, body)
            if not self.verify_written_object(key, body):
                raise MigrationError("R2 write did not pass readback verification")
            return
        raced = self.r2.get(key)
        if raced is not None and sha256(raced) == sha256(body):
            if known_keys is not None:
                known_keys.add(key)
            self.report["objects_unchanged"] += 1
            self.record_r2_readback(key, raced)
            return
        self.report["conflicts"] += 1
        raise MigrationError("R2 immutable key changed during migration; review required")

    def put_analysis_current(self, key: str, body: bytes, incoming: dict) -> None:
        incoming_at = str(incoming.get("observed_at") or "")
        for _ in range(3):
            current = self.r2.get_with_etag(key)
            if current is None:
                if self.args.dry_run:
                    self.report["objects_planned"] += 1
                    self.record_write_set_object(key, body)
                    return
                if self.r2.put_if_absent(key, body):
                    self.report["objects_written"] += 1
                    self.record_write_set_object(key, body)
                    if not self.verify_written_current(key, body, incoming_at):
                        raise MigrationError("R2 current analysis did not pass readback verification")
                    return
                continue
            existing_body, etag = current
            try:
                existing = json.loads(existing_body)
            except json.JSONDecodeError as error:
                self.report["conflicts"] += 1
                raise MigrationError("Existing R2 current analysis is invalid JSON") from error
            existing_at = str(existing.get("observed_at") or "")
            if existing_at > incoming_at:
                self.report["objects_skipped_newer"] += 1
                return
            if existing_at == incoming_at:
                if sha256(existing_body) == sha256(body):
                    self.report["objects_unchanged"] += 1
                    self.record_r2_readback(key, existing_body)
                    return
                self.report["conflicts"] += 1
                raise MigrationError("Current analysis has different data at the same observed_at")
            if self.args.dry_run:
                self.report["objects_planned"] += 1
                self.record_write_set_object(key, body)
                return
            if self.r2.put_if_unchanged(key, body, etag):
                self.report["objects_written"] += 1
                self.record_write_set_object(key, body)
                if not self.verify_written_current(key, body, incoming_at):
                    raise MigrationError("R2 current analysis did not pass readback verification")
                return
        self.report["conflicts"] += 1
        raise MigrationError("Current analysis changed concurrently; rerun after reviewing")

    def verify_written_current(
        self, key: str, expected: bytes, incoming_at: str, timestamp_field: str = "observed_at"
    ) -> bool:
        actual = self.r2.get(key)
        if actual == expected:
            self.record_r2_readback(key, actual)
            return True
        if actual is not None:
            try:
                current = json.loads(actual)
            except json.JSONDecodeError:
                current = {}
            current_at = str(current.get(timestamp_field) or "")
            if current_at > incoming_at:
                self.report["objects_superseded_newer"] += 1
                return True
        self.report["verification_failures"] += 1
        return False

    @classmethod
    def scheduled_run_order(cls, pointer: dict) -> tuple[float, int]:
        started_at = cls.normalize_time(pointer.get("started_at"))
        run_id = str(pointer.get("run_id") or "")
        if started_at is None or not run_id.isdigit():
            raise MigrationError("Scheduled-run pointer has an invalid timestamp or run id")
        parsed = datetime.fromisoformat(started_at.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc).timestamp(), int(run_id)

    def put_latest_scheduled_run_pointer(self, pointer: dict) -> None:
        key = "operations/v1/runs/legacy-d1/current.json"
        incoming_at = self.normalize_time(pointer.get("started_at"))
        if incoming_at is None:
            raise MigrationError("Latest completed D1 scheduled run has an invalid started_at")
        run_id = str(pointer.get("run_id") or "")
        if not run_id.isdigit():
            raise MigrationError("Latest completed D1 scheduled run has an invalid run id")
        run_type = str(pointer.get("run_type") or "unknown")
        run_key = f"operations/v1/runs/{quote(run_type, safe='')}/legacy-d1-{run_id}/summary.json"
        published_pointer = {
            "run_key": run_key,
            "run_id": run_id,
            "run_type": run_type,
            "started_at": incoming_at,
        }
        body = canonical_json(published_pointer)
        incoming_order = self.scheduled_run_order(published_pointer)

        for _ in range(3):
            current = self.r2.get_with_etag(key)
            if current is None:
                if self.args.dry_run:
                    self.report["objects_planned"] += 1
                    self.record_write_set_object(key, body)
                    return
                if self.r2.put_if_absent(key, body):
                    self.report["objects_written"] += 1
                    self.record_write_set_object(key, body)
                    if not self.verify_written_current(key, body, incoming_at, "started_at"):
                        raise MigrationError("Latest scheduled-run pointer did not pass readback verification")
                    return
                continue

            current_body, etag = current
            try:
                existing = json.loads(current_body)
            except json.JSONDecodeError as error:
                self.report["conflicts"] += 1
                raise MigrationError("Existing latest scheduled-run pointer is invalid JSON") from error
            existing_at = self.normalize_time(existing.get("started_at"))
            if existing_at is None:
                self.report["conflicts"] += 1
                raise MigrationError("Existing latest scheduled-run pointer has an invalid started_at")

            existing_order = self.scheduled_run_order({**existing, "started_at": existing_at})
            if existing_order > incoming_order:
                self.report["objects_skipped_newer"] += 1
                self.record_r2_readback(key, current_body)
                return
            if existing_order == incoming_order:
                if json.loads(body) == {**existing, "started_at": existing_at}:
                    self.report["objects_unchanged"] += 1
                    self.record_r2_readback(key, current_body)
                    return
                self.report["conflicts"] += 1
                raise MigrationError("Latest scheduled-run pointer differs at the same run position")
            if self.args.dry_run:
                self.report["objects_planned"] += 1
                self.record_write_set_object(key, body)
                return
            if self.r2.put_if_unchanged(key, body, etag):
                self.report["objects_written"] += 1
                self.record_write_set_object(key, body)
                if not self.verify_written_current(key, body, incoming_at, "started_at"):
                    raise MigrationError("Latest scheduled-run pointer did not pass readback verification")
                return

        self.report["conflicts"] += 1
        raise MigrationError("Latest scheduled-run pointer changed concurrently; rerun after reviewing")

    def page_loop(
        self,
        query: str,
        cursor_param: object,
        cursor_field: str,
        map_row,
        *,
        max_rows_per_result: int = 1,
    ) -> None:
        if max_rows_per_result < 1:
            raise MigrationError("max_rows_per_result must be at least 1")
        while self.report["rows_read"] < self.args.max_rows:
            remaining = self.args.max_rows - self.report["rows_read"]
            # Reserve a conservative amount for joined lookup rows before
            # querying. read_page still accounts Cloudflare's actual rows_read.
            limit = min(self.args.batch_size, remaining // max_rows_per_result)
            if limit < 1:
                return
            rows = self.read_page(query, [cursor_param, limit])
            if not rows:
                self.report["complete"] = True
                return
            for row in rows:
                self.record_source_row(row)
                map_row(row)
                cursor_param = row[cursor_field]
                self.checkpoint["cursor"] = cursor_param
            self.save_outputs()
            if len(rows) < limit:
                self.report["complete"] = True
                return

    def read_market_source(self, key: str) -> tuple[dict | None, bytes | None]:
        if key not in self.market_source_cache:
            body = self.r2.get(key)
            source_hash = sha256(body) if body is not None else "missing"
            pinned_hashes = self.checkpoint.setdefault("market_source_object_hashes", {})
            key_fingerprint = sha256(key.encode("utf-8"))
            if key_fingerprint in pinned_hashes and pinned_hashes[key_fingerprint] != source_hash:
                raise MigrationError("R2 source object changed during audit; restart from a new checkpoint")
            pinned_hashes[key_fingerprint] = source_hash
            summary = self.checkpoint.get("market_source_audit")
            if isinstance(summary, dict):
                prior = str(summary.get("r2_source_sha256") or "0" * 64)
                fingerprint = sha256(key.encode("utf-8") + b"\0" + (body if body is not None else b"<missing>"))
                summary["r2_source_sha256"] = sha256((prior + fingerprint).encode("ascii"))
                summary["r2_source_lookups"] = int(summary.get("r2_source_lookups", 0)) + 1
            if body is None:
                self.market_source_cache[key] = (None, None)
            else:
                try:
                    document = json.loads(body)
                except json.JSONDecodeError as error:
                    raise MigrationError("R2 source document is invalid JSON") from error
                if not isinstance(document, dict):
                    raise MigrationError("R2 source document is not an object")
                self.market_source_cache[key] = (document, body)
        return self.market_source_cache[key]

    @staticmethod
    def audit_source_records(table: str, document: dict, period_type: str | None = None) -> tuple[str, list[dict]]:
        if table == "financial_periods":
            source_key = "overview.json"
            records = document.get("financials", {}).get(period_type or "", [])
            return source_key, [row for row in records if isinstance(row, dict)] if isinstance(records, list) else []
        dividend_data = document.get("dividends")
        if isinstance(dividend_data, dict) and isinstance(dividend_data.get("records"), list):
            return "overview.json", [row for row in dividend_data["records"] if isinstance(row, dict)]
        records = document.get("records")
        return "dividends.json", [row for row in records if isinstance(row, dict)] if isinstance(records, list) else []

    @staticmethod
    def audit_value_differences(source: dict, target: dict) -> tuple[list[str], list[str], int]:
        ignored = {"period_end", "fiscal_period", "period", "fiscal_year", "field_units"}
        source_fields = {key: value for key, value in source.items() if key not in ignored}
        common = [key for key in source_fields if key in target]
        missing = sorted(key for key in source_fields if key not in target)
        different = sorted(key for key in common if not values_equal(source_fields[key], target[key]))
        return missing, different, len(common)

    def run_market_source_audit(self) -> None:
        table = self.args.table
        if table not in MARKET_SOURCE_AUDIT_TABLES:
            raise MigrationError("market-source-audit requires financial_periods, dividend_events, or issuer_dividend_periods")

        summary = self.checkpoint.setdefault("market_source_audit", {
            "comparison_version": FINANCIAL_AUDIT_VERSION if table == "financial_periods" else 1,
            "rows_checked": 0,
            "status_counts": {},
            "missing_field_counts": {},
            "different_field_counts": {},
            "source_category_mismatch_counts": {},
            "unit_metadata_incomplete_counts": {},
            "unit_conversion_counts": {},
            "period_range": {},
            "r2_source_lookups": 0,
            "r2_source_sha256": "0" * 64,
        })
        if table == "financial_periods":
            summary["comparison_version"] = FINANCIAL_AUDIT_VERSION
        counts = summary.setdefault("status_counts", {})
        missing_field_counts = summary.setdefault("missing_field_counts", {})
        different_field_counts = summary.setdefault("different_field_counts", {})
        source_category_mismatch_counts = summary.setdefault("source_category_mismatch_counts", {})
        unit_metadata_incomplete_counts = summary.setdefault("unit_metadata_incomplete_counts", {})
        unit_conversion_counts = summary.setdefault("unit_conversion_counts", {})

        if table == "financial_periods":
            query = (
                "SELECT fp.rowid AS migration_rowid, fp.security_code AS source_security_code, "
                "s.security_code_display, CASE WHEN s.security_code IS NULL THEN 1 ELSE 0 END AS security_reference_missing, "
                "fp.period_type, fp.fiscal_year, "
                "fp.accounting_standard, fp.is_consolidated, fp.data_json "
                "FROM financial_periods fp LEFT JOIN securities s ON s.security_code = fp.security_code "
                "WHERE fp.rowid > ? ORDER BY fp.rowid LIMIT ?"
            )
        elif table == "dividend_events":
            query = (
                "SELECT d.rowid AS migration_rowid, d.security_code AS source_security_code, "
                "s.security_code_display, CASE WHEN s.security_code IS NULL THEN 1 ELSE 0 END AS security_reference_missing, "
                "d.fiscal_year, d.event_type, "
                "d.annual_dps, d.annual_dps_adjusted, d.record_date, d.final_rights_date, d.date_status, d.data_json "
                "FROM dividend_events d LEFT JOIN securities s ON s.security_code = d.security_code "
                "WHERE d.rowid > ? ORDER BY d.rowid LIMIT ?"
            )
        else:
            query = (
                "SELECT i.rowid AS migration_rowid, i.security_code AS source_security_code, "
                "s.security_code_display, CASE WHEN s.security_code IS NULL THEN 1 ELSE 0 END AS security_reference_missing, "
                "i.fiscal_period, i.event_type, i.data_json "
                "FROM issuer_dividend_periods i LEFT JOIN securities s ON s.security_code = i.security_code "
                "WHERE i.rowid > ? ORDER BY i.rowid LIMIT ?"
            )

        def audit(row: dict) -> None:
            unit_metadata_incomplete_fields: list[str] = []
            row_unit_conversion_counts: dict[str, int] = {}
            code = str(row.get("security_code_display") or "").strip().upper()
            source_code = str(row.get("source_security_code") or "").strip().upper()
            if bool(row.get("security_reference_missing")):
                status, source_key, missing_fields, different_fields = "security_reference_missing", None, [], []
            elif not re.fullmatch(r"[A-Z0-9]{4}", code):
                status, source_key, missing_fields, different_fields = "unsupported_security_code", None, [], []
            else:
                source_key = (
                    f"market/irbank/v1/securities/{code}/overview.json"
                    if table == "financial_periods"
                    else f"market/irbank/v1/securities/{code}/dividends.json"
                )
                document, body = self.read_market_source(source_key)
                if document is None and table != "financial_periods":
                    source_key = f"market/irbank/v1/securities/{code}/overview.json"
                    document, body = self.read_market_source(source_key)
                if document is None:
                    status, missing_fields, different_fields = "r2_source_missing", [], []
                else:
                    _, records = self.audit_source_records(table, document, str(row.get("period_type") or ""))
                    if table == "financial_periods":
                        category = str(row.get("period_type") or "")
                        period = normalize_period(row.get("fiscal_year"))
                        if category not in {"annual", "quarterly", "semiannual"}:
                            status, missing_fields, different_fields = "unsupported_period_type", [], []
                            same_period = []
                            exact = []
                        else:
                            same_period = [item for item in records if normalize_period(item.get("fiscal_year")) == period]
                            consolidated = normalize_boolean(row.get("is_consolidated"))
                            exact = [item for item in same_period
                                     if str(item.get("accounting_standard") or "") == str(row.get("accounting_standard") or "")
                                     and normalize_boolean(item.get("is_consolidated")) == consolidated]
                        if category in {"annual", "quarterly", "semiannual"} and not exact:
                            other_full_year_categories = []
                            if not same_period and category == "annual":
                                financials = document.get("financials") or {}
                                if not isinstance(financials, dict):
                                    financials = {}
                                for other_category in ("quarterly", "semiannual"):
                                    other_records = financials.get(other_category, [])
                                    if not isinstance(other_records, list):
                                        continue
                                    has_full_year = any(
                                        isinstance(item, dict)
                                        and normalize_period(item.get("fiscal_year")) == period
                                        and str(item.get("accounting_standard") or "")
                                            == str(row.get("accounting_standard") or "")
                                        and normalize_boolean(item.get("is_consolidated")) == consolidated
                                        and str(item.get("quarter") or "").strip().lower().replace(" ", "_")
                                            in {"full_year", "fy"}
                                        for item in other_records
                                    )
                                    if has_full_year:
                                        other_full_year_categories.append(other_category)
                            if same_period:
                                status = "source_identity_mismatch"
                            elif other_full_year_categories:
                                status = "source_category_mismatch"
                                for other_category in other_full_year_categories:
                                    key = f"{category}->{other_category}"
                                    source_category_mismatch_counts[key] = (
                                        int(source_category_mismatch_counts.get(key, 0)) + 1
                                    )
                            else:
                                status = "source_period_missing"
                            missing_fields, different_fields = [], []
                        elif exact:
                            source_payload = parse_json_value(row.get("data_json"), table)
                            if not isinstance(source_payload, dict):
                                raise MigrationError(f"D1 source row is not a JSON object in {table}")
                            source_payload = dict(source_payload)
                            identity_mismatches = []
                            if "fiscal_year" in source_payload and normalize_period(source_payload["fiscal_year"]) != period:
                                identity_mismatches.append("fiscal_year")
                            if "accounting_standard" in source_payload and str(source_payload["accounting_standard"] or "") != str(row.get("accounting_standard") or ""):
                                identity_mismatches.append("accounting_standard")
                            if "is_consolidated" in source_payload and normalize_boolean(source_payload["is_consolidated"]) != consolidated:
                                identity_mismatches.append("is_consolidated")
                            source_payload["fiscal_year"] = row.get("fiscal_year")
                            source_payload["accounting_standard"] = row.get("accounting_standard")
                            source_payload["is_consolidated"] = consolidated
                            target = exact[0]
                            (
                                missing_fields,
                                different_fields,
                                common,
                                unit_metadata_incomplete_fields,
                                row_unit_conversion_counts,
                            ) = audit_financial_value_differences(source_payload, target)
                            different_fields = sorted(set(different_fields) | set(identity_mismatches))
                            if common and not missing_fields and not different_fields:
                                status = "unit_metadata_incomplete" if unit_metadata_incomplete_fields else "source_values_match"
                            elif common or missing_fields or different_fields:
                                status = "source_values_differ"
                            else:
                                status = "schema_not_comparable"
                    else:
                        source_payload = parse_json_value(row.get("data_json"), table)
                        if not isinstance(source_payload, dict):
                            raise MigrationError(f"D1 source row is not a JSON object in {table}")
                        source_payload = dict(source_payload)
                        period = row.get("fiscal_period") or row.get("fiscal_year") or source_payload.get("fiscal_year")
                        source_payload.setdefault("fiscal_year", row.get("fiscal_year") or str(period)[:4])
                        if row.get("event_type") is not None:
                            source_payload.setdefault("event_type", row.get("event_type"))
                        for field in ("annual_dps", "annual_dps_adjusted", "record_date", "final_rights_date", "date_status"):
                            if row.get(field) is not None:
                                source_payload.setdefault(field, row[field])
                        year = normalize_period(period)[:4]
                        same_year = [item for item in records if normalize_period(item.get("fiscal_year"))[:4] == year]
                        if table == "issuer_dividend_periods":
                            exact = []
                            granularity_match = False
                            for item in same_year:
                                target_period = item.get("period_end") or item.get("fiscal_period") or item.get("period") or item.get("fiscal_year")
                                matched, granularity = period_matches(period, target_period)
                                if matched:
                                    exact.append(item)
                                    granularity_match = granularity_match or granularity
                            if not exact and same_year:
                                status = "period_granularity_gap"
                                target = same_year[0]
                                granularity_match = True
                            elif not exact:
                                status, target = "source_period_missing", None
                            else:
                                target = exact[0]
                        else:
                            target = same_year[0] if same_year else None
                            granularity_match = False
                            status = "source_period_missing" if target is None else ""
                        if target is not None:
                            missing_fields, different_fields, common = self.audit_value_differences(source_payload, target)
                            if missing_fields or different_fields:
                                status = "source_values_differ"
                            elif not common:
                                status = "schema_not_comparable"
                            elif table == "issuer_dividend_periods" and granularity_match:
                                status = "period_granularity_gap"
                            else:
                                status = "source_values_match"

            summary["rows_checked"] = int(summary.get("rows_checked", 0)) + 1
            normalized_period = normalize_period(row.get("fiscal_period") or row.get("fiscal_year"))
            if normalized_period:
                period_range = summary.setdefault("period_range", {})
                period_range["min"] = min(str(period_range.get("min") or normalized_period), normalized_period)
                period_range["max"] = max(str(period_range.get("max") or normalized_period), normalized_period)
            counts[status] = int(counts.get(status, 0)) + 1
            for field in set(missing_fields):
                missing_field_counts[field] = int(missing_field_counts.get(field, 0)) + 1
            for field in set(different_fields):
                different_field_counts[field] = int(different_field_counts.get(field, 0)) + 1
            for field in set(unit_metadata_incomplete_fields):
                unit_metadata_incomplete_counts[field] = int(unit_metadata_incomplete_counts.get(field, 0)) + 1
            for pair, count in row_unit_conversion_counts.items():
                unit_conversion_counts[pair] = int(unit_conversion_counts.get(pair, 0)) + count
            self.report["market_source_audit"] = summary

        self.page_loop(
            query,
            self.checkpoint.get("cursor") or 0,
            "migration_rowid",
            audit,
            max_rows_per_result=2,
        )
        self.report["market_source_audit"] = summary

    def run_market_audit(self) -> None:
        catalog_key = "market/yfinance/v2/catalog/current.json"
        object_body = self.r2.get(catalog_key)
        if object_body is None:
            raise MigrationError("R2 v2 catalog/current.json is missing")
        catalog = json.loads(object_body)
        catalog_hash = sha256(object_body)
        if self.checkpoint.get("r2_catalog_generation") not in {None, catalog.get("generation")}:
            raise MigrationError("R2 catalog generation changed during audit; restart with a new checkpoint")
        if self.checkpoint.get("r2_catalog_sha256") not in {None, catalog_hash}:
            raise MigrationError("R2 catalog contents changed during audit; restart with a new checkpoint")
        self.checkpoint["r2_catalog_generation"] = catalog.get("generation")
        self.checkpoint["r2_catalog_sha256"] = catalog_hash
        catalog_items = catalog.get("items")
        if not isinstance(catalog_items, list):
            raise MigrationError("R2 v2 catalog items are missing or invalid")
        catalog_by_code = {
            str(row.get("code", "")).strip().upper(): row
            for row in catalog_items
            if isinstance(row, dict) and str(row.get("code", "")).strip()
        }

        issuer_key = "market/yfinance/v2/issuer_dividends/current.json"
        issuer_body = self.r2.get(issuer_key)
        issuer_items = {}
        issuer_hash = None
        issuer_index_status = "missing"
        if issuer_body is not None:
            issuer_hash = sha256(issuer_body)
            if self.checkpoint.get("r2_issuer_index_sha256") not in {None, issuer_hash}:
                raise MigrationError("R2 issuer-dividend index changed during audit; restart with a new checkpoint")
            issuer_index = json.loads(issuer_body)
            issuer_items_value = issuer_index.get("items") if isinstance(issuer_index, dict) else None
            if isinstance(issuer_items_value, dict):
                issuer_items = {str(code).strip().upper(): item for code, item in issuer_items_value.items()}
                issuer_index_status = "available"
            else:
                issuer_index_status = "invalid"
        if issuer_hash:
            self.checkpoint["r2_issuer_index_sha256"] = issuer_hash

        irbank_manifest_key = "market/irbank/v1/manifest.json"
        irbank_manifest_body = self.r2.get(irbank_manifest_key)
        irbank_manifest_hash = sha256(irbank_manifest_body) if irbank_manifest_body is not None else None
        if self.checkpoint.get("r2_irbank_manifest_sha256") not in {None, irbank_manifest_hash}:
            raise MigrationError("R2 IRBANK manifest changed during audit; restart with a new checkpoint")
        overview_hashes: dict[str, str] = {}
        irbank_manifest_status = "missing"
        if irbank_manifest_body is not None:
            self.checkpoint["r2_irbank_manifest_sha256"] = irbank_manifest_hash
            try:
                irbank_manifest = json.loads(irbank_manifest_body)
            except json.JSONDecodeError:
                irbank_manifest_status = "invalid"
            else:
                objects = irbank_manifest.get("objects") if isinstance(irbank_manifest, dict) else None
                if isinstance(objects, list):
                    irbank_manifest_status = "available"
                    for item in objects:
                        if not isinstance(item, dict):
                            continue
                        key = item.get("key")
                        object_hash = str(item.get("sha256") or "").lower()
                        if (
                            isinstance(key, str)
                            and re.fullmatch(r"market/irbank/v1/securities/[A-Z0-9]{4,5}/overview\.json", key)
                            and re.fullmatch(r"[a-f0-9]{64}", object_hash)
                        ):
                            overview_hashes[key] = object_hash
                else:
                    irbank_manifest_status = "invalid"

        field_audit = self.checkpoint.setdefault("market_audit_fields", {
            "name_mismatch_count": 0,
            "edinet_mismatch_count": 0,
            "issuer_index_missing_count": 0,
            "metrics_nonempty_count": 0,
            "metrics_invalid_count": 0,
            "metrics_compared_count": 0,
            "metrics_mismatch_count": 0,
            "metrics_r2_profile_missing_count": 0,
            "metrics_r2_invalid_count": 0,
        })
        for field in (
            "name_mismatch_count", "edinet_mismatch_count", "issuer_index_missing_count",
            "metrics_nonempty_count", "metrics_invalid_count", "metrics_compared_count",
            "metrics_mismatch_count", "metrics_r2_profile_missing_count", "metrics_r2_invalid_count",
        ):
            field_audit.setdefault(field, 0)
        missing_count = int(self.checkpoint.get("d1_security_missing_count") or 0)
        invalid_count = int(self.checkpoint.get("d1_security_invalid_count") or 0)
        cursor = self.checkpoint.get("cursor") or 0

        def check(row: dict) -> None:
            nonlocal missing_count, invalid_count
            raw_display_code = row.get("security_code_display")
            display_code = "" if raw_display_code is None else str(raw_display_code)
            code = display_code.strip().upper()

            raw_metrics = row.get("metrics_json")
            metrics = None
            metrics_valid = True
            try:
                metrics = json.loads(raw_metrics) if isinstance(raw_metrics, str) else raw_metrics
            except (TypeError, json.JSONDecodeError):
                metrics_valid = False
                field_audit["metrics_invalid_count"] = int(field_audit.get("metrics_invalid_count", 0)) + 1
            metrics_nonempty = metrics_valid and metrics is not None and metrics != "" and metrics != [] and metrics != {}
            if metrics_nonempty:
                field_audit["metrics_nonempty_count"] = int(field_audit.get("metrics_nonempty_count", 0)) + 1

            if not re.fullmatch(r"[A-Z0-9]{4,5}", code):
                invalid_count += 1
                self.checkpoint["d1_security_invalid_count"] = invalid_count
                self.report["d1_security_invalid_count"] = invalid_count
                return

            if metrics_nonempty:
                overview_key = f"market/irbank/v1/securities/{code}/overview.json"
                expected_overview_hash = overview_hashes.get(overview_key)
                overview_body = self.r2.get(overview_key) if expected_overview_hash else None
                if overview_body is None:
                    field_audit["metrics_r2_profile_missing_count"] = int(
                        field_audit.get("metrics_r2_profile_missing_count", 0)
                    ) + 1
                elif sha256(overview_body) != expected_overview_hash:
                    raise MigrationError("R2 IRBANK overview changed since manifest; restart the audit")
                else:
                    try:
                        overview = json.loads(overview_body)
                    except json.JSONDecodeError:
                        overview = None
                    security = overview.get("security") if isinstance(overview, dict) else None
                    r2_metrics = security.get("metrics") if isinstance(security, dict) else None
                    if not isinstance(r2_metrics, list):
                        field_audit["metrics_r2_invalid_count"] = int(
                            field_audit.get("metrics_r2_invalid_count", 0)
                        ) + 1
                    else:
                        field_audit["metrics_compared_count"] = int(
                            field_audit.get("metrics_compared_count", 0)
                        ) + 1
                        if not values_equal(metrics, r2_metrics):
                            field_audit["metrics_mismatch_count"] = int(
                                field_audit.get("metrics_mismatch_count", 0)
                            ) + 1

            catalog_item = catalog_by_code.get(code)
            if catalog_item is None:
                missing_count += 1
                self.checkpoint["d1_security_missing_count"] = missing_count
                self.report["d1_security_missing_count"] = missing_count
                return

            d1_name = unicodedata.normalize("NFKC", str(row.get("name") or "")).strip()
            r2_name = unicodedata.normalize("NFKC", str(catalog_item.get("name") or "")).strip()
            if d1_name and r2_name and d1_name != r2_name:
                field_audit["name_mismatch_count"] = int(field_audit.get("name_mismatch_count", 0)) + 1

            if issuer_index_status == "available":
                issuer_item = issuer_items.get(code)
                if not isinstance(issuer_item, dict):
                    field_audit["issuer_index_missing_count"] = int(field_audit.get("issuer_index_missing_count", 0)) + 1
                else:
                    d1_edinet = str(row.get("edinet_code") or "").strip().upper()
                    r2_edinet = str(issuer_item.get("edinet_code") or "").strip().upper()
                    if d1_edinet != r2_edinet:
                        field_audit["edinet_mismatch_count"] = int(field_audit.get("edinet_mismatch_count", 0)) + 1

        self.page_loop(
            "SELECT rowid AS migration_rowid, security_code, security_code_display, name, edinet_code, metrics_json FROM securities WHERE rowid > ? ORDER BY rowid LIMIT ?",
            cursor,
            "migration_rowid",
            check,
        )
        self.report["r2_catalog_generation"] = catalog.get("generation")
        self.report["r2_catalog_count"] = len(catalog_by_code)
        self.report["r2_catalog_sha256"] = catalog_hash
        self.report["r2_issuer_index_status"] = issuer_index_status
        self.report["r2_issuer_index_sha256"] = issuer_hash
        self.report["r2_irbank_manifest_status"] = irbank_manifest_status
        self.report["r2_irbank_manifest_sha256"] = irbank_manifest_hash
        self.report["d1_security_missing_count"] = missing_count
        self.report["d1_security_invalid_count"] = invalid_count
        self.report["market_audit_fields"] = field_audit

    def run_analysis_current(self) -> None:
        cursor = int(self.checkpoint.get("cursor") or 0)
        self.checkpoint["cursor_kind"] = "rowid-v1"

        def migrate(row: dict) -> None:
            code = str(row.get("security_code") or "").upper()
            snapshot = parse_json_value(row.get("analysis_json"), "stock_analysis_state")
            if not isinstance(snapshot, dict) or not snapshot.get("observed_at"):
                raise MigrationError("Invalid analysis snapshot")
            if snapshot.get("canonical_code") and snapshot["canonical_code"] != code:
                raise MigrationError("Analysis security-code mismatch")
            self.put_analysis_current(f"analysis/v1/current/{code}.json", canonical_json(snapshot), snapshot)

        self.page_loop(
            "SELECT rowid AS migration_rowid, security_code, analysis_json, last_observed_at FROM stock_analysis_state WHERE rowid > ? ORDER BY rowid LIMIT ?",
            cursor,
            "migration_rowid",
            migrate,
        )

    def run_analysis_history(self) -> None:
        cursor = int(self.checkpoint.get("cursor") or 0)
        if self.analysis_history_keys is None:
            self.analysis_history_keys = set(self.r2.list_keys("analysis/v1/history/"))

        def migrate(row: dict) -> None:
            payload = {
                "observed_at": row.get("observed_at"),
                "market_date": row.get("market_date"),
                "definition_version": row.get("definition_version"),
                "overall_score": row.get("overall_score"),
                "overall_grade": row.get("overall_grade"),
                "buy_zone_status": row.get("buy_zone_status"),
                "buy_zone_ratio": row.get("buy_zone_ratio"),
                "anomaly_flags_json": row.get("anomaly_flags_json") or "[]",
                "metrics_json": row.get("metrics_json") or "{}",
                "analysis_json": row.get("analysis_json") or "{}",
            }
            parse_json_value(payload["analysis_json"], "stock_analysis_history")
            self.put_immutable(
                analysis_history_key(row), canonical_json(payload), self.analysis_history_keys
            )

        self.page_loop(
            "SELECT id, security_code, observed_at, market_date, definition_version, overall_score, overall_grade, buy_zone_status, buy_zone_ratio, anomaly_flags_json, metrics_json, analysis_json FROM stock_analysis_history WHERE id > ? ORDER BY id LIMIT ?",
            cursor,
            "id",
            migrate,
        )

    def run_scheduled_runs(self) -> None:
        cursor = int(self.checkpoint.get("cursor") or 0)

        def migrate(row: dict) -> None:
            started_at = self.normalize_time(row.get("started_at"))
            if started_at is None:
                raise MigrationError("Invalid started_at in scheduled_run_log")
            summary = {
                "run_id": str(row["id"]),
                "run_type": row.get("run_type"),
                "started_at": row.get("started_at"),
                "finished_at": row.get("finished_at"),
                "status": row.get("status"),
                "processed": row.get("processed"),
                "failed": row.get("failed"),
                "metadata": parse_json_value(row.get("metadata_json") or "{}", "scheduled_run_log"),
            }
            kind = quote(str(row.get("run_type") or "unknown"), safe="")
            run_key = f"operations/v1/runs/{kind}/legacy-d1-{row['id']}/summary.json"
            self.put_immutable(run_key, canonical_json(summary))
            candidate = {
                "run_id": str(row["id"]),
                "run_type": row.get("run_type"),
                "started_at": started_at,
            }
            latest = self.checkpoint.get("latest_scheduled_run")
            if latest is None or self.scheduled_run_order(candidate) > self.scheduled_run_order(latest):
                self.checkpoint["latest_scheduled_run"] = candidate
            elif self.scheduled_run_order(candidate) == self.scheduled_run_order(latest) and candidate != latest:
                raise MigrationError("Scheduled-run source changed at the same run position")

        self.page_loop(
            "SELECT id, run_type, started_at, finished_at, status, processed, failed, metadata_json FROM scheduled_run_log WHERE id > ? AND finished_at IS NOT NULL AND started_at >= datetime('now','-365 days') ORDER BY id LIMIT ?",
            cursor,
            "id",
            migrate,
        )
        latest = self.checkpoint.get("latest_scheduled_run")
        if self.report["complete"] and isinstance(latest, dict):
            self.put_latest_scheduled_run_pointer(latest)

    def run_definition_source(self) -> None:
        definitions = list(DEFINITION_FILES.items())
        start_index = int(self.checkpoint.get("definition_index") or 0)
        if not 0 <= start_index <= len(definitions):
            raise MigrationError("Checkpoint has an invalid definition index")
        for index in range(start_index, len(definitions)):
            logical_key, file_stem = definitions[index]
            if self.report["rows_read"] >= self.args.max_rows:
                self.report["complete"] = False
                return
            kv_value = self.read_kv(logical_key)
            if kv_value is not None:
                value = kv_value
            elif logical_key in DEFINITION_TABLES:
                limit = min(self.args.batch_size, self.args.max_rows - self.report["rows_read"])
                if limit <= 0:
                    self.report["complete"] = False
                    return
                rows = self.read_page(f"{DEFINITION_TABLES[logical_key]} LIMIT ?", [limit])
                if len(rows) == limit:
                    raise MigrationError("Definition table page reached its limit; increase the budget to read it whole")
                value = rows
                self.record_source_row(rows)
            else:
                rows = self.read_page(
                    "SELECT value_json FROM app_definition_documents WHERE definition_key = ?",
                    [logical_key],
                )
                if rows:
                    value = parse_json_value(rows[0]["value_json"], "app_definition_documents")
                    self.record_source_row(rows[0])
                else:
                    seed_path = ROOT / "kv" / f"{file_stem}.json"
                    value = json.loads(seed_path.read_text(encoding="utf-8"))
            version_key, pointer_key, body = definition_object_keys(logical_key, value)
            self.put_immutable(version_key, body)
            pointer = canonical_json({"version": value.get("version", 1) if isinstance(value, dict) else 1})
            existing_pointer = self.r2.get(pointer_key)
            if existing_pointer is not None and json.loads(existing_pointer) != json.loads(pointer):
                self.report["conflicts"] += 1
                self.report["complete"] = False
                self.save_outputs()
                return
            self.put_immutable(pointer_key, pointer)
            self.checkpoint["definition_index"] = index + 1
            self.report["complete"] = index + 1 == len(definitions)
            self.save_outputs()
        self.report["complete"] = start_index >= len(definitions) or self.report["conflicts"] == 0

    def read_kv(self, key: str) -> object | None:
        url = (
            f"https://api.cloudflare.com/client/v4/accounts/{os.environ['CLOUDFLARE_ACCOUNT_ID']}"
            f"/storage/kv/namespaces/{self.namespace_id}/values/{quote(key, safe='')}"
        )
        request = Request(url, headers={"Authorization": f"Bearer {self.reader.token}"})
        try:
            with urlopen(request, timeout=30) as response:
                return json.loads(response.read())
        except HTTPError as error:
            if error.code == 404:
                return None
            raise MigrationError(f"KV definition read failed with HTTP {error.code}") from error
        except (URLError, TimeoutError, json.JSONDecodeError) as error:
            raise MigrationError("KV definition read failed") from error

    def run_definition_audit(self) -> None:
        cursor = int(self.checkpoint.get("cursor") or 0)

        def migrate(row: dict) -> None:
            key = str(row.get("definition_key") or "")
            version = int(row.get("version") or 1)
            content_hash = str(row.get("content_hash") or "")
            if not key or not re.fullmatch(r"[a-fA-F0-9]{64}", content_hash):
                raise MigrationError("Invalid D1 definition audit row")
            audit = {
                "definition_key": key,
                "version": version,
                "content_hash": content_hash,
                "metadata": parse_json_value(row.get("metadata_json") or "{}", "app_definition_audit"),
                "updated_at": row.get("updated_at"),
            }
            encoded = quote(key, safe="")
            self.put_immutable(
                f"audit/v1/definitions/{encoded}/{version}/{content_hash}.json",
                canonical_json(audit),
            )

        self.page_loop(
            "SELECT rowid AS migration_rowid, definition_key, version, content_hash, updated_at, metadata_json FROM app_definition_audit WHERE rowid > ? ORDER BY rowid LIMIT ?",
            cursor,
            "migration_rowid",
            migrate,
        )

    def run_analysis_source(self) -> None:
        rows = self.read_page(
            "SELECT state_key, value_json, updated_at FROM backend_state WHERE state_key = ?",
            ["analysis_source"],
        )
        if not rows:
            self.report["complete"] = True
            return
        row = rows[0]
        self.record_source_row(row)
        key = "analysis/v1/state/source.json"
        incoming = {"source_tag": row["value_json"], "updated_at": row.get("updated_at")}
        incoming_at = str(incoming.get("updated_at") or "")
        body = canonical_json(incoming)
        for _ in range(3):
            current = self.r2.get_with_etag(key)
            if current is None:
                if self.args.dry_run:
                    self.report["objects_planned"] += 1
                    self.record_write_set_object(key, body)
                    break
                if self.r2.put_if_absent(key, body):
                    self.report["objects_written"] += 1
                    self.record_write_set_object(key, body)
                    if not self.verify_written_current(key, body, incoming_at, "updated_at"):
                        raise MigrationError("R2 analysis source did not pass readback verification")
                    break
                continue
            current_body, etag = current
            try:
                existing = json.loads(current_body)
            except json.JSONDecodeError as error:
                self.report["conflicts"] += 1
                raise MigrationError("Existing R2 analysis source state is invalid JSON") from error
            existing_at = str(existing.get("updated_at") or "")
            if existing == incoming:
                self.report["objects_unchanged"] += 1
                self.record_r2_readback(key, current_body)
                break
            if existing_at > incoming_at:
                self.report["objects_skipped_newer"] += 1
                break
            if existing_at == incoming_at:
                self.report["conflicts"] += 1
                raise MigrationError("Analysis source state differs at the same updated_at")
            if self.args.dry_run:
                self.report["objects_planned"] += 1
                self.record_write_set_object(key, body)
                break
            if self.r2.put_if_unchanged(key, body, etag):
                self.report["objects_written"] += 1
                self.record_write_set_object(key, body)
                if not self.verify_written_current(key, body, incoming_at, "updated_at"):
                    raise MigrationError("R2 analysis source did not pass readback verification")
                break
        else:
            self.report["conflicts"] += 1
            raise MigrationError("Analysis source state changed concurrently; rerun after reviewing")
        self.report["complete"] = True

    @staticmethod
    def inventory_token(value: object, *, limit: int = 128) -> str:
        text = str(value or "")
        if len(text) <= limit and re.fullmatch(r"[A-Za-z][A-Za-z0-9._-]*", text):
            return text
        return f"sha256:{sha256(text.encode('utf-8'))}"

    def run_d1_schema_audit(self) -> None:
        rows = self.read_page(
            "SELECT type, name, tbl_name FROM sqlite_schema "
            "WHERE type IN ('table', 'trigger') ORDER BY type, name",
            [],
        )
        for row in rows:
            self.record_source_row(row)
        self.report["schema_objects"] = [
            {
                "type": self.inventory_token(row.get("type"), limit=16),
                "name": self.inventory_token(row.get("name")),
                "table": self.inventory_token(row.get("tbl_name")),
            }
            for row in rows
        ]
        self.report["complete"] = True

    def run_d1_operational_audit(self) -> None:
        table = self.args.table
        if table not in OPERATIONAL_AUDIT_TABLES:
            raise MigrationError("Unsupported d1-operational-audit table")

        cursor_value = self.checkpoint.get("cursor")
        if cursor_value is None:
            cursor = 0
        elif isinstance(cursor_value, int) and not isinstance(cursor_value, bool) and cursor_value >= 0:
            cursor = cursor_value
        else:
            raise MigrationError("Operational audit checkpoint has an invalid rowid cursor")

        inventory = self.checkpoint.get("operational_inventory")
        if not isinstance(inventory, dict) or inventory.get("table") != table:
            if cursor > 0:
                raise MigrationError("Operational audit checkpoint is missing its cumulative summary")
            inventory = {
                "table": table,
                "row_count": 0,
                "status_counts": {},
                "date_ranges": {},
                "state_keys": [],
                "numeric_totals": {},
                "rows_without_completion_time": 0,
            }
            if table == "scheduled_run_log":
                inventory["run_type_counts"] = {}
        if table == "scheduled_run_log" and "run_type_counts" not in inventory:
            if cursor > 0:
                raise MigrationError("Operational audit checkpoint is missing run type counts")
            inventory["run_type_counts"] = {}
        if (
            not isinstance(inventory.get("row_count"), int)
            or isinstance(inventory.get("row_count"), bool)
            or inventory["row_count"] < 0
            or not isinstance(inventory.get("status_counts"), dict)
            or (table == "scheduled_run_log" and not isinstance(inventory.get("run_type_counts"), dict))
            or not isinstance(inventory.get("date_ranges"), dict)
            or not isinstance(inventory.get("state_keys"), list)
            or not isinstance(inventory.get("numeric_totals"), dict)
            or not isinstance(inventory.get("rows_without_completion_time"), int)
            or isinstance(inventory.get("rows_without_completion_time"), bool)
            or inventory["rows_without_completion_time"] < 0
        ):
            raise MigrationError("Operational audit checkpoint has an invalid cumulative summary")
        self.report["operational_inventory"] = inventory
        self.checkpoint["operational_inventory"] = inventory

        if table == "backend_state":
            sql = (
                "SELECT rowid AS migration_rowid, state_key, updated_at "
                "FROM backend_state WHERE rowid > ? ORDER BY rowid LIMIT ?"
            )
        elif table == "market_price_refresh_runs":
            sql = (
                "SELECT rowid AS migration_rowid, status, run_date, started_at, last_batch_at, "
                "completed_at, updated_at, total_batches, enqueued_batches, completed_batches, "
                "failed_batches, processed_symbols FROM market_price_refresh_runs "
                "WHERE rowid > ? ORDER BY rowid LIMIT ?"
            )
        elif table == "market_price_refresh_batches":
            sql = (
                "SELECT rowid AS migration_rowid, status, market_data_date, completed_at, "
                "processed_symbols FROM market_price_refresh_batches "
                "WHERE rowid > ? ORDER BY rowid LIMIT ?"
            )
        else:
            sql = (
                "SELECT rowid AS migration_rowid, status, run_type, started_at, finished_at, "
                "processed, failed FROM scheduled_run_log "
                "WHERE rowid > ? ORDER BY rowid LIMIT ?"
            )

        def summarize(row: dict) -> None:
            rowid = int(row["migration_rowid"])
            self.record_source_row(row)
            inventory["row_count"] = int(inventory.get("row_count", 0)) + 1

            if table != "backend_state":
                status = self.inventory_token(row.get("status") or "unknown", limit=64)
                counts = inventory.setdefault("status_counts", {})
                counts[status] = int(counts.get(status, 0)) + 1
            if table == "scheduled_run_log":
                run_type = self.inventory_token(row.get("run_type") or "unknown", limit=64)
                run_types = inventory.setdefault("run_type_counts", {})
                run_types[run_type] = int(run_types.get(run_type, 0)) + 1

            date_fields = {
                "backend_state": ("updated_at",),
                "scheduled_run_log": ("started_at", "finished_at"),
                "market_price_refresh_runs": ("run_date", "started_at", "last_batch_at", "completed_at", "updated_at"),
                "market_price_refresh_batches": ("market_data_date", "completed_at"),
            }[table]
            ranges = inventory.setdefault("date_ranges", {})
            for field in date_fields:
                normalized = self.normalize_time(row.get(field))
                if normalized is None:
                    continue
                current = ranges.setdefault(field, {"min": normalized, "max": normalized})
                current["min"] = min(current["min"], normalized)
                current["max"] = max(current["max"], normalized)

            if table == "backend_state":
                key = self.inventory_token(row.get("state_key"))
                keys = inventory.setdefault("state_keys", [])
                if key not in keys:
                    keys.append(key)
                    keys.sort()
            else:
                completion_field = "finished_at" if table == "scheduled_run_log" else "completed_at"
                if not row.get(completion_field):
                    inventory["rows_without_completion_time"] = int(
                        inventory.get("rows_without_completion_time", 0)
                    ) + 1
                numeric_fields = (
                    ("total_batches", "enqueued_batches", "completed_batches", "failed_batches", "processed_symbols")
                    if table == "market_price_refresh_runs"
                    else ("processed", "failed")
                    if table == "scheduled_run_log"
                    else ("processed_symbols",)
                )
                totals = inventory.setdefault("numeric_totals", {})
                for field in numeric_fields:
                    try:
                        amount = max(0, int(row.get(field) or 0))
                    except (TypeError, ValueError):
                        inventory["invalid_numeric_fields"] = int(inventory.get("invalid_numeric_fields", 0)) + 1
                        continue
                    totals[field] = int(totals.get(field, 0)) + amount

            self.checkpoint["operational_inventory"] = inventory
            self.checkpoint["cursor"] = rowid
            self.report["operational_inventory"] = inventory

        self.page_loop(sql, cursor, "migration_rowid", summarize)

    def run_legacy_market_archive(self) -> None:
        table = self.args.table
        if table not in ARCHIVE_TABLES:
            raise MigrationError("Unsupported legacy-market-archive table")
        cursor = int(self.checkpoint.get("cursor") or 0)
        archive_prefix = f"migration/v1/d1/archive/{table}/"
        known_archive_keys = set(self.r2.list_keys(archive_prefix))

        def migrate(row: dict) -> None:
            source_row = dict(row)
            rowid = int(source_row.pop("migration_rowid"))
            body = canonical_json({"source_table": table, "source_rowid": rowid, "row": source_row})
            record_hash = sha256(body)
            key = f"{archive_prefix}{rowid}-{record_hash}.json"
            self.put_immutable(key, body, known_archive_keys)

        self.page_loop(
            f"SELECT rowid AS migration_rowid, * FROM {table} WHERE rowid > ? ORDER BY rowid LIMIT ?",
            cursor,
            "migration_rowid",
            migrate,
        )

    def run(self) -> dict:
        if self.args.phase == "d1-schema-audit":
            self.run_d1_schema_audit()
        elif self.args.phase == "d1-operational-audit":
            self.run_d1_operational_audit()
        elif self.args.phase == "market-audit":
            self.run_market_audit()
        elif self.args.phase == "analysis-current":
            self.run_analysis_current()
        elif self.args.phase == "analysis-history":
            self.run_analysis_history()
        elif self.args.phase == "scheduled-runs":
            self.run_scheduled_runs()
        elif self.args.phase == "definitions":
            self.run_definition_source()
        elif self.args.phase == "definition-audit":
            self.run_definition_audit()
        elif self.args.phase == "analysis-source":
            self.run_analysis_source()
        elif self.args.phase == "market-source-audit":
            self.run_market_source_audit()
        elif self.args.phase == "legacy-market-archive":
            self.run_legacy_market_archive()
        self.report["status"] = "complete" if self.report.get("complete") else "incomplete"
        self.save_outputs()
        return self.report


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", required=True, choices=sorted(ALLOWED_PHASES))
    parser.add_argument(
        "--table",
        default="securities",
        choices=sorted(ARCHIVE_TABLES | MARKET_SOURCE_AUDIT_TABLES | OPERATIONAL_AUDIT_TABLES),
    )
    parser.add_argument("--max-rows", type=int, default=500)
    parser.add_argument(
        "--read-reserve-rows",
        type=int,
        default=DEFAULT_READ_RESERVE_ROWS,
        help="minimum account-wide D1 rows-read allowance to leave unused after this phase",
    )
    parser.add_argument("--batch-size", type=int, default=100)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", dest="dry_run")
    mode.add_argument("--write-r2", action="store_true")
    parser.set_defaults(dry_run=True)
    parser.add_argument("--confirm-write", default="")
    parser.add_argument("--checkpoint-in", default="migration-checkpoint.json")
    parser.add_argument("--checkpoint-out", default="migration-checkpoint.json")
    parser.add_argument("--report", default="migration-report.json")
    args = parser.parse_args()
    if args.write_r2 and args.confirm_write != "WRITE_R2":
        parser.error("R2 writes require --confirm-write WRITE_R2")
    if args.write_r2:
        args.dry_run = False
    if args.phase in {"market-audit", "market-source-audit", "d1-schema-audit", "d1-operational-audit"} and args.write_r2:
        parser.error(f"{args.phase} is read-only; omit --write-r2")
    allowed_tables = TABLE_SELECTION_PHASES.get(args.phase)
    if allowed_tables is None and args.table != "securities":
        parser.error(f"{args.phase} does not select a table; keep the default --table securities")
    if allowed_tables is not None and args.table not in allowed_tables:
        parser.error(f"{args.phase} does not support table {args.table}")
    if not (1 <= args.max_rows <= 100_000 and 1 <= args.batch_size <= 1_000):
        parser.error("max-rows must be 1..100000 and batch-size must be 1..1000")
    if args.read_reserve_rows < 0:
        parser.error("read-reserve-rows must be non-negative")
    if args.max_rows + args.read_reserve_rows > d1_daily_usage.DEFAULT_FREE_DAILY_LIMIT:
        parser.error("max-rows plus read-reserve-rows cannot exceed the 5,000,000-row Free allowance")
    return args


def account_daily_usage_preflight(account_id: str, args: argparse.Namespace) -> dict:
    required_rows = args.max_rows + args.read_reserve_rows
    token = os.environ.get("CLOUDFLARE_API_TOKEN", "")
    utc_date = datetime.now(timezone.utc).date().isoformat()
    try:
        usage = d1_daily_usage.fetch_daily_usage(account_id, token, utc_date)
        sufficient = d1_daily_usage.has_sufficient_remaining_budget(usage, required_rows)
    except d1_daily_usage.UsageQueryError as error:
        raise MigrationError(f"Account-wide D1 daily usage preflight failed: {error}") from error
    if not sufficient:
        raise MigrationError(
            "Account-wide D1 daily usage preflight found insufficient remaining allowance "
            f"(required_rows={required_rows}, rows_remaining={usage.get('rows_remaining')})"
        )
    return {
        "utc_date": usage["utc_date"],
        "queried_at_utc": usage["queried_at_utc"],
        "rows_read": usage["rows_read"],
        "rows_remaining": usage["rows_remaining"],
        "daily_limit_rows": usage["daily_limit_rows"],
        "required_rows_remaining": required_rows,
    }


def main() -> int:
    args = arguments()
    migrator = None
    try:
        config = load_config()
        account_id = os.environ["CLOUDFLARE_ACCOUNT_ID"]
        args.account_daily_usage_preflight = account_daily_usage_preflight(account_id, args)
        database_id = os.environ.get("CLOUDFLARE_D1_DATABASE_ID") or config["d1_databases"][0]["database_id"]
        reader = D1Reader(account_id, database_id, os.environ["CLOUDFLARE_MIGRATION_READ_TOKEN"])
        d1_only_phase = args.phase in {"d1-schema-audit", "d1-operational-audit"}
        if d1_only_phase:
            r2 = None
            namespace_id = "d1-only"
        else:
            namespace_id = os.environ.get("APP_DEFINITIONS_KV_NAMESPACE_ID") or config["kv_namespaces"][0]["id"]
            r2 = R2Store(
                account_id,
                os.environ["R2_ACCESS_KEY_ID"],
                os.environ["R2_SECRET_ACCESS_KEY"],
                os.environ.get("R2_BUCKET", "diview-market-data"),
            )
        migrator = Migrator(args, reader, r2, namespace_id)
        report = migrator.run()
        print(json.dumps(report, ensure_ascii=False, sort_keys=True))
        return 0
    except (KeyError, OSError, tomllib.TOMLDecodeError, MigrationError) as error:
        if migrator is not None:
            migrator.report["status"] = "failed"
            migrator.report["failure"] = {
                "type": type(error).__name__,
                "message": str(error),
            }
            try:
                migrator.save_outputs()
            except Exception as report_error:
                print(f"migration failure report could not be saved: {report_error}", file=sys.stderr)
        print(f"migration failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
