"""Copy app-used legacy financial values into yfinance v2 R2 partitions once."""

from __future__ import annotations

import argparse
import calendar
import json
import math
import os
import re
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from typing import Any

import boto3
from botocore.config import Config


V2_ROOT = "market/yfinance/v2"
LEGACY_ROOT = "market/irbank/v1/securities"
ANNUAL_FIELDS = (
    "sales",
    "operating_profit",
    "operating_cf",
    "investing_cf",
    "total_assets",
    "equity_ratio_computed",
)
VALUATION_FIELDS = ("eps_adjusted", "bps_adjusted", "dividend_yield_adjusted")
_PROGRESS_LOCK = threading.Lock()


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


def make_client(workers: int):
    account_id = required_env("CLOUDFLARE_ACCOUNT_ID")
    return boto3.client(
        "s3",
        endpoint_url=os.environ.get("R2_ENDPOINT_URL", f"https://{account_id}.r2.cloudflarestorage.com"),
        aws_access_key_id=required_env("R2_ACCESS_KEY_ID"),
        aws_secret_access_key=required_env("R2_SECRET_ACCESS_KEY"),
        region_name="auto",
        config=Config(max_pool_connections=max(10, workers * 2), retries={"max_attempts": 4, "mode": "standard"}),
    )


def read_object(client: Any, bucket: str, key: str) -> bytes | None:
    try:
        return client.get_object(Bucket=bucket, Key=key)["Body"].read()
    except Exception as exc:
        error_code = str(getattr(exc, "response", {}).get("Error", {}).get("Code", ""))
        if error_code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise


def read_json(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    raw = read_object(client, bucket, key)
    if raw is None:
        return None
    parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError(f"Expected a JSON object at {key}")
    return parsed


def safe_symbol(value: str) -> str:
    return value.replace("^", "_").replace("=", "_").replace("/", "_")


def legacy_code(value: Any) -> str | None:
    code = str(value or "").strip().upper()
    if re.fullmatch(r"[A-Z0-9]{4}", code):
        return code
    if re.fullmatch(r"[A-Z0-9]{4}0", code):
        return code[:-1]
    return None


def period_fields(row: Any) -> tuple[str, str] | None:
    if not isinstance(row, dict):
        return None
    fiscal_year = str(row.get("fiscal_year") or "")
    match = re.fullmatch(r"(\d{4})/(\d{2})", fiscal_year)
    if not match:
        return None
    year, month = int(match.group(1)), int(match.group(2))
    if year < 1900 or month < 1 or month > 12:
        return None
    day = calendar.monthrange(year, month)[1]
    return fiscal_year, f"{year:04d}-{month:02d}-{day:02d}"


def finite_value(value: Any) -> Any:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value if math.isfinite(float(value)) else None
    if isinstance(value, str):
        return value
    return None


def selected_rows(rows: Any, fields: tuple[str, ...]) -> list[tuple[str, str, dict[str, Any]]]:
    if not isinstance(rows, list):
        return []
    result: dict[str, tuple[str, str, dict[str, Any]]] = {}
    for row in rows:
        period = period_fields(row)
        if period is None:
            continue
        fiscal_year, period_end = period
        selected = {field: value for field in fields
                    if (value := finite_value(row.get(field))) is not None}
        if selected:
            result[fiscal_year] = (fiscal_year, period_end, selected)
    return [result[key] for key in sorted(result)[-10:]]


def merge_row(
    current: Any,
    fiscal_year: str,
    period_end: str,
    values: dict[str, Any],
    *,
    source_key: str,
) -> tuple[dict[str, Any], int]:
    row = dict(current) if isinstance(current, dict) else {}
    current_period = str(row.get("fiscal_year") or "")
    if current_period and current_period != fiscal_year:
        raise ValueError(f"v2 period conflict: expected {fiscal_year}, found {current_period}")
    row["fiscal_year"] = fiscal_year
    row["period_end"] = str(row.get("period_end") or period_end)
    statuses = dict(row.get("field_status") or {})
    source_values = dict(row.get("source_values") or {})
    seed_record = dict(source_values.get("seed") or {})
    seed_values = dict(seed_record.get("values") or {})
    added = 0
    for field, value in values.items():
        if field not in seed_values:
            seed_values[field] = value
            added += 1
        blocked_reason = (row.get("field_status_reason") or {}).get(field, [])
        if row.get(field) is None and "mixed_source_units_not_comparable" not in blocked_reason:
            row[field] = value
        statuses.setdefault(field, "legacy_migrated")
    if seed_values:
        seed_record.update({"source": "IRBANK", "source_key": source_key, "values": seed_values})
        source_values["seed"] = seed_record
        row["source_values"] = source_values
    row["field_status"] = statuses
    if added and not row.get("migration_source"):
        row["migration_source"] = source_key
    return row, added


def json_bytes(value: dict[str, Any]) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")


def list_financial_keys(client: Any, bucket: str, symbol: str) -> dict[str, str]:
    prefix = f"{V2_ROOT}/securities/{safe_symbol(symbol)}/financials/"
    paginator = client.get_paginator("list_objects_v2")
    keys: dict[str, str] = {}
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        for item in page.get("Contents", []):
            key = str(item.get("Key") or "")
            name = key.removeprefix(prefix)
            if not re.fullmatch(r"\d{4}(?:-\d{2}-\d{2})?\.json", name):
                continue
            document = read_json(client, bucket, key)
            if not document:
                continue
            annual = document.get("annual")
            valuation = document.get("valuation")
            annual_period = str(annual.get("period_end") or "")[:10] if isinstance(annual, dict) else ""
            valuation_period = str(valuation.get("period_end") or "")[:10] if isinstance(valuation, dict) else ""
            for period_end in (annual_period, valuation_period or annual_period):
                if re.fullmatch(r"\d{4}-\d{2}-\d{2}", period_end):
                    keys.setdefault(period_end, key)
    return keys


def migrate_one(client: Any, bucket: str, security: dict[str, Any], *, dry_run: bool) -> dict[str, Any]:
    code = str(security.get("code") or "").strip().upper()
    symbol = str(security.get("symbol") or "").strip().upper()
    legacy = legacy_code(code)
    if not re.fullmatch(r"[A-Z0-9]{4,5}\.T", symbol) or not legacy:
        return {"code": code, "status": "unsupported_code"}

    source_key = f"{LEGACY_ROOT}/{legacy}/overview.json"
    source = read_json(client, bucket, source_key)
    if source is None:
        return {"code": code, "symbol": symbol, "status": "legacy_missing"}

    annual_rows = selected_rows((source.get("financials") or {}).get("annual"), ANNUAL_FIELDS)
    valuation_rows = selected_rows((source.get("valuation") or {}).get("history"), VALUATION_FIELDS)
    annual_by_period = {fiscal_year: (period_end, values) for fiscal_year, period_end, values in annual_rows}
    valuation_by_period = {fiscal_year: (period_end, values) for fiscal_year, period_end, values in valuation_rows}
    period_years = sorted(set(annual_by_period) | set(valuation_by_period))
    if not period_years:
        return {"code": code, "symbol": symbol, "status": "no_app_fields"}

    keys = list_financial_keys(client, bucket, symbol)
    changed_documents = 0
    migrated_fields = 0
    for fiscal_year in period_years:
        period_end = (annual_by_period.get(fiscal_year) or valuation_by_period[fiscal_year])[0]
        year = period_end[:4]
        key = keys.get(period_end) or f"{V2_ROOT}/securities/{safe_symbol(symbol)}/financials/{period_end}.json"
        document = read_json(client, bucket, key) or {
            "schema_version": 1,
            "symbol": symbol,
            "year": int(year),
            "source": "legacy IRBANK values migrated; yfinance refresh pending",
        }
        updated = dict(document)
        added = 0
        if fiscal_year in annual_by_period:
            annual_end, annual_values = annual_by_period[fiscal_year]
            updated["annual"], count = merge_row(
                updated.get("annual"), fiscal_year, annual_end, annual_values, source_key=source_key,
            )
            added += count
        if fiscal_year in valuation_by_period:
            valuation_end, valuation_values = valuation_by_period[fiscal_year]
            updated["valuation"], count = merge_row(
                updated.get("valuation"), fiscal_year, valuation_end, valuation_values, source_key=source_key,
            )
            added += count
        if added == 0:
            continue
        retrieval_status = dict(updated.get("retrieval_status") or {})
        retrieval_status["legacy_migration"] = "seeded"
        updated["retrieval_status"] = retrieval_status
        updated["updated_at"] = iso_now()
        migrated_fields += added
        if dry_run:
            continue

        client.put_object(
            Bucket=bucket,
            Key=key,
            Body=json_bytes(updated),
            ContentType="application/json; charset=utf-8",
        )
        saved = read_json(client, bucket, key)
        if saved is None:
            raise RuntimeError(f"v2 read-back missing: {key}")
        for section in ("annual", "valuation"):
            expected_row = updated.get(section)
            if not isinstance(expected_row, dict):
                continue
            actual_row = saved.get(section)
            if not isinstance(actual_row, dict):
                raise RuntimeError(f"v2 read-back missing {section}: {key}")
            for field, value in expected_row.items():
                if field in (*ANNUAL_FIELDS, *VALUATION_FIELDS) and actual_row.get(field) != value:
                    raise RuntimeError(f"v2 read-back mismatch for {section}.{field}: {key}")
            expected_seed = ((updated.get(section) or {}).get("source_values") or {}).get("seed", {}).get("values", {})
            actual_seed = ((actual_row.get("source_values") or {}).get("seed") or {}).get("values", {})
            for field, value in expected_seed.items():
                if actual_seed.get(field) != value:
                    raise RuntimeError(f"v2 read-back seed mismatch for {section}.{field}: {key}")
        changed_documents += 1

    return {
        "code": code,
        "symbol": symbol,
        "status": "dry_run" if dry_run else "migrated" if changed_documents else "already_seeded",
        "document_count": changed_documents,
        "field_count": migrated_fields,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--limit", type=int, default=0, help="Process only the first N active securities")
    parser.add_argument("--dry-run", action="store_true", help="Report changes without writing R2")
    parser.add_argument("--report", default="legacy-financial-migration-report.json")
    args = parser.parse_args()
    if args.workers < 1 or args.workers > 32:
        parser.error("--workers must be between 1 and 32")

    started_at = iso_now()
    bucket = required_env("R2_BUCKET")
    client = make_client(args.workers)
    universe = read_json(client, bucket, f"{V2_ROOT}/universe/current.json")
    securities = [row for row in (universe or {}).get("securities", [])
                  if isinstance(row, dict) and row.get("status") == "active"]
    if not securities:
        raise RuntimeError("No active securities found in the published v2 universe")
    if args.limit > 0:
        securities = securities[:args.limit]

    print(f"Financial seed: securities={len(securities)}, workers={args.workers}, dry_run={args.dry_run}", flush=True)
    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(migrate_one, client, bucket, row, dry_run=args.dry_run): row for row in securities}
        for completed, future in enumerate(as_completed(futures), start=1):
            security = futures[future]
            try:
                results.append(future.result())
            except Exception as exc:
                results.append({
                    "code": str(security.get("code") or ""),
                    "symbol": str(security.get("symbol") or ""),
                    "status": "failed",
                    "error": f"{type(exc).__name__}: {exc}"[:500],
                })
            if completed % 100 == 0 or completed == len(securities):
                with _PROGRESS_LOCK:
                    print(f"Processed {completed}/{len(securities)} securities", flush=True)

    if not args.dry_run:
        # Successful and partially failed migrations may both change annual
        # documents without publishing a new market catalog.
        client.put_object(
            Bucket=bucket, Key=f"{V2_ROOT}/financial_revision.json",
            Body=json_bytes({
                "schema_version": 1, "revision": uuid.uuid4().hex, "updated_at": iso_now(),
            }),
            ContentType="application/json",
        )

    counts: dict[str, int] = {}
    for result in results:
        state = str(result.get("status") or "unknown")
        counts[state] = counts.get(state, 0) + 1
    report = {
        "schema_version": 1,
        "started_at": started_at,
        "completed_at": iso_now(),
        "source_prefix": LEGACY_ROOT,
        "target_prefix": f"{V2_ROOT}/securities/{{symbol}}/financials/",
        "status": "failed" if counts.get("failed") else "dry_run" if args.dry_run else "complete",
        "security_count": len(securities),
        "counts": counts,
        "migrated_field_count": sum(int(row.get("field_count", 0)) for row in results),
        "securities": sorted(results, key=lambda row: str(row.get("code") or "")),
    }
    with open(args.report, "w", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, indent=2, allow_nan=False)
        output.write("\n")
    print(json.dumps({key: value for key, value in report.items() if key != "securities"}, ensure_ascii=False), flush=True)
    return 1 if counts.get("failed") else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
