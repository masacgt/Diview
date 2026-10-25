"""Read-only comparison of legacy dividend actions with their V2 R2 records."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
from typing import Any

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError


AUDIT_CODES = (
    "1319", "1382", "1484", "1498", "1596", "1615", "1618", "1621",
    "2084", "2501", "2568", "2641", "4840", "4848", "6727", "6835",
)
V2_ROOT = "market/yfinance/v2"
LEGACY_ROOT = "market/yfinance/securities"
MISSING_CODES = {"NoSuchKey", "404", "NotFound"}


def read_json(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    try:
        response = client.get_object(Bucket=bucket, Key=key)
    except ClientError as exc:
        code = str(exc.response.get("Error", {}).get("Code", ""))
        if code in MISSING_CODES:
            return None
        raise
    body = response["Body"]
    try:
        value = json.loads(body.read())
    finally:
        body.close()
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object at {key}")
    return value


def event_date(row: dict[str, Any]) -> str:
    return str(row.get("date") or "")[:10]


def json_keys(client: Any, bucket: str, prefix: str) -> list[str]:
    keys: list[str] = []
    paginator = client.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        keys.extend(
            str(item["Key"])
            for item in page.get("Contents", [])
            if str(item.get("Key", "")).endswith(".json")
        )
    return sorted(set(keys))


def records_from_keys(client: Any, bucket: str, keys: list[str]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for key in keys:
        document = read_json(client, bucket, key)
        if document and isinstance(document.get("records"), list):
            records.extend(row for row in document["records"] if isinstance(row, dict))
    return records


def inspect_security(client: Any, bucket: str, code: str) -> dict[str, Any]:
    symbol = f"{code}.T"
    safe_symbol = re.sub(r"[^A-Za-z0-9.^_=-]", "_", symbol)
    legacy_key = f"{LEGACY_ROOT}/{safe_symbol}.json"
    legacy = read_json(client, bucket, legacy_key)
    legacy_events = []
    if legacy is not None:
        actions = legacy.get("actions") if isinstance(legacy.get("actions"), dict) else {}
        legacy_events = [row for row in actions.get("dividends", []) if isinstance(row, dict)]
    required_dates = {event_date(row) for row in legacy_events if row.get("date")}

    current_prefix = f"{V2_ROOT}/securities/{safe_symbol}/dividends/"
    current_keys = json_keys(client, bucket, current_prefix)
    current_records = records_from_keys(client, bucket, current_keys)

    archive_prefix = f"{V2_ROOT}/archive/events/{safe_symbol}/dividends/"
    archive_keys = json_keys(client, bucket, archive_prefix)
    archived_records = records_from_keys(client, bucket, archive_keys)

    stored_records = current_records + archived_records
    stored_dates = {event_date(row) for row in stored_records if row.get("date")}
    missing_dates = sorted(required_dates - stored_dates)
    legacy_by_date: dict[str, list[dict[str, Any]]] = {}
    for row in legacy_events:
        date_value = event_date(row)
        if date_value in missing_dates:
            legacy_by_date.setdefault(date_value, []).append(row)

    if legacy is None:
        status = "legacy_missing_v2_present" if stored_records else "legacy_missing_v2_empty"
    else:
        status = "legacy_events_missing_from_v2" if missing_dates else "complete"
    return {
        "code": code,
        "symbol": symbol,
        "status": status,
        "legacy_baseline_available": legacy is not None,
        "legacy_key": legacy_key,
        "legacy_event_count": len(legacy_events) if legacy is not None else None,
        "legacy_unique_date_count": len(required_dates) if legacy is not None else None,
        "v2_current_object_count": len(current_keys),
        "v2_current_event_count": len(current_records),
        "v2_archive_object_count": len(archive_keys),
        "v2_archive_event_count": len(archived_records),
        "v2_unique_date_count": len(stored_dates),
        "v2_date_range": [min(stored_dates), max(stored_dates)] if stored_dates else None,
        "matched_date_count": len(required_dates & stored_dates) if legacy is not None else None,
        "missing_date_count": len(missing_dates) if legacy is not None else None,
        "missing_legacy_events": [
            {"date": date_value, "events": legacy_by_date.get(date_value, [])}
            for date_value in missing_dates
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    account_id = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "").strip()
    access_key = os.environ.get("R2_ACCESS_KEY_ID", "").strip()
    secret_key = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip()
    bucket = os.environ.get("R2_BUCKET", "").strip()
    if not all((account_id, access_key, secret_key, bucket)):
        raise RuntimeError("Cloudflare R2 audit credentials and R2_BUCKET are required")

    client = boto3.client(
        "s3",
        endpoint_url=os.environ.get("R2_ENDPOINT_URL", f"https://{account_id}.r2.cloudflarestorage.com"),
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name="auto",
        config=Config(max_pool_connections=32, retries={"max_attempts": 4, "mode": "standard"}),
    )

    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = {executor.submit(inspect_security, client, bucket, code): code for code in AUDIT_CODES}
        for future in as_completed(futures):
            code = futures[future]
            try:
                results.append(future.result())
            except Exception as exc:
                results.append({"code": code, "symbol": f"{code}.T", "status": "read_failed", "error": str(exc)})

    results.sort(key=lambda row: row["code"])
    missing_date_count = sum(int(row.get("missing_date_count") or 0) for row in results)
    report = {
        "schema_version": 2,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "read_only": True,
        "code_count": len(AUDIT_CODES),
        "missing_legacy_date_count": missing_date_count,
        "status_counts": {
            status: sum(row.get("status") == status for row in results)
            for status in sorted({str(row.get("status")) for row in results})
        },
        "securities": results,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        key: report[key]
        for key in ("generated_at", "code_count", "missing_legacy_date_count", "status_counts")
    }, ensure_ascii=False))
    blocking_statuses = {"read_failed", "legacy_events_missing_from_v2", "legacy_missing_v2_empty"}
    return 1 if any(row.get("status") in blocking_statuses for row in results) else 0


if __name__ == "__main__":
    raise SystemExit(main())
