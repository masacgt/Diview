"""Copy legacy IRBANK fiscal-year dividend records into the yfinance v2 namespace."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import json
import math
import os
import re
import threading
from typing import Any

import boto3
from botocore.config import Config


V2_ROOT = "market/yfinance/v2"
LEGACY_IRBANK_ROOT = "market/irbank/v1/securities"
LEGACY_BUNDLE_ROOT = "market/securities/v1"
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


def read_json(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    try:
        raw = client.get_object(Bucket=bucket, Key=key)["Body"].read()
    except Exception as exc:
        error_code = str(getattr(exc, "response", {}).get("Error", {}).get("Code", ""))
        if error_code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise
    parsed = json.loads(raw)
    if not isinstance(parsed, dict):
        raise ValueError(f"Expected a JSON object at {key}")
    return parsed


def json_safe(value: Any) -> Any:
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if isinstance(value, list):
        return [json_safe(item) for item in value]
    if isinstance(value, dict):
        return {str(key): json_safe(item) for key, item in value.items()}
    return value


def json_bytes(value: dict[str, Any]) -> bytes:
    return json.dumps(json_safe(value), ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")


def security_code(value: Any) -> str | None:
    code = str(value or "").strip().upper()
    if re.fullmatch(r"[A-Z0-9]{4}", code):
        return code
    if re.fullmatch(r"[A-Z0-9]{4}0", code):
        return code[:-1]
    return None


def fiscal_year(value: Any) -> int | None:
    match = re.fullmatch(r"(\d{4})(?:/\d{2})?", str(value or "").strip())
    year = int(match.group(1)) if match else None
    return year if year is not None and 1900 <= year <= 2100 else None


def legacy_dividends(client: Any, bucket: str, code: str) -> tuple[list[dict[str, Any]], str, Any]:
    source_key = f"{LEGACY_IRBANK_ROOT}/{code}/dividends.json"
    source = read_json(client, bucket, source_key)
    if source is not None:
        records = source.get("records")
        valid_records = [row for row in records if isinstance(row, dict)] if isinstance(records, list) else []
        if valid_records:
            return valid_records, source_key, source.get("attribution")

    # Older combined snapshots kept the same issuer rows under a source-specific field.
    bundle_key = f"{LEGACY_BUNDLE_ROOT}/{code}.json"
    bundle = read_json(client, bucket, bundle_key)
    records = ((bundle or {}).get("dividend_history_by_source") or {}).get("irbank")
    attribution = (((bundle or {}).get("sources") or {}).get("irbank") or {}).get("attribution")
    return ([row for row in records if isinstance(row, dict)] if isinstance(records, list) else [],
            bundle_key, attribution)


def records_by_year(records: list[dict[str, Any]]) -> dict[int, dict[str, Any]]:
    selected: dict[int, dict[str, Any]] = {}
    for row in records:
        year = fiscal_year(row.get("fiscal_year"))
        if year is not None:
            selected[year] = json_safe(row)
    return selected


def migrate_one(client: Any, bucket: str, security: dict[str, Any], *, dry_run: bool) -> dict[str, Any]:
    code = security_code(security.get("code"))
    symbol = str(security.get("symbol") or "").strip().upper()
    if code is None or not re.fullmatch(r"[A-Z0-9]{4,5}\.T", symbol):
        return {"code": str(security.get("code") or ""), "status": "unsupported_code"}

    source_rows, source_key, attribution = legacy_dividends(client, bucket, code)
    rows = records_by_year(source_rows)
    if not rows:
        return {"code": code, "symbol": symbol, "status": "no_legacy_records", "source_key": source_key}

    inserted = 0
    unchanged = 0
    for year, source_row in sorted(rows.items()):
        key = f"{V2_ROOT}/securities/{symbol}/issuer_dividends/{year}.json"
        existing = read_json(client, bucket, key) or {}
        existing_rows = records_by_year(
            [row for row in existing.get("records", []) if isinstance(row, dict)]
            if isinstance(existing.get("records"), list) else []
        )
        if year in existing_rows:
            unchanged += 1
            continue

        updated = {
            **existing,
            "schema_version": 1,
            "symbol": symbol,
            "fiscal_year": year,
            "history_basis": "issuer_reported_fiscal_year",
            "amount_basis": "irbank_adjusted_fiscal_dps",
            "migration_status": "legacy_migrated",
            "records": [*existing_rows.values(), source_row],
            "source": "IRBANK Connect",
            "source_key": source_key,
            "attribution": attribution,
            "updated_at": iso_now(),
        }
        if not dry_run:
            client.put_object(
                Bucket=bucket,
                Key=key,
                Body=json_bytes(updated),
                ContentType="application/json; charset=utf-8",
                CacheControl="public, max-age=300",
            )
            saved = read_json(client, bucket, key)
            saved_rows = records_by_year(
                [row for row in saved.get("records", []) if isinstance(row, dict)]
                if saved and isinstance(saved.get("records"), list) else []
            )
            if saved is None or saved_rows.get(year) != source_row:
                raise RuntimeError(f"v2 issuer-dividend read-back mismatch: {key}")
        inserted += 1

    return {
        "code": code,
        "symbol": symbol,
        "status": "dry_run" if dry_run else "migrated" if inserted else "already_present",
        "source_key": source_key,
        "record_count": len(rows),
        "inserted_count": inserted,
        "existing_count": unchanged,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--limit", type=int, default=0, help="Process only the first N active securities")
    parser.add_argument("--dry-run", action="store_true", help="Report changes without writing R2")
    parser.add_argument("--report", default="legacy-dividend-migration-report.json")
    args = parser.parse_args()
    if not 1 <= args.workers <= 32:
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

    print(f"IRBANK dividend seed: securities={len(securities)}, workers={args.workers}, dry_run={args.dry_run}", flush=True)
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

    counts: dict[str, int] = {}
    for result in results:
        status = str(result.get("status") or "unknown")
        counts[status] = counts.get(status, 0) + 1
    report = {
        "schema_version": 1,
        "started_at": started_at,
        "completed_at": iso_now(),
        "source_prefixes": [LEGACY_IRBANK_ROOT, LEGACY_BUNDLE_ROOT],
        "target_prefix": f"{V2_ROOT}/securities/{{symbol}}/issuer_dividends/",
        "status": "failed" if counts.get("failed") else "dry_run" if args.dry_run else "complete",
        "security_count": len(securities),
        "counts_by_status": counts,
        "record_count": sum(int(result.get("record_count") or 0) for result in results),
        "inserted_count": sum(int(result.get("inserted_count") or 0) for result in results),
        "results": sorted(results, key=lambda row: str(row.get("code") or "")),
    }
    with open(args.report, "w", encoding="utf-8") as report_file:
        json.dump(report, report_file, ensure_ascii=False, indent=2, allow_nan=False)
        report_file.write("\n")
    print(json.dumps({key: value for key, value in report.items() if key != "results"}, ensure_ascii=False), flush=True)
    return 1 if counts.get("failed") else 0


if __name__ == "__main__":
    raise SystemExit(main())
