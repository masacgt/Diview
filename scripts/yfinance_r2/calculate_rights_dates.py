#!/usr/bin/env python3
"""Calculate Japanese last rights dates from Yahoo ex-dividend events."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import exchange_calendars as xcals


UTC = timezone.utc
PREFIX = os.environ.get("R2_PREFIX", "market/yfinance").strip("/")
OUTPUT_KEY = f"{PREFIX}/rights_dates.json"


def previous_session(calendar: Any, ex_date: date) -> date:
    """The session immediately before ex-date is the last cum-rights session."""
    first_session = calendar.first_session.date()
    if ex_date <= first_session:
        raise ValueError(f"XTKS calendar history does not cover {ex_date}")
    sessions = calendar.sessions_in_range(
        max(first_session, ex_date - timedelta(days=14)).isoformat(),
        (ex_date - timedelta(days=1)).isoformat(),
    )
    if not len(sessions):
        raise ValueError(f"no XTKS session before ex-dividend date {ex_date}")
    return sessions[-1].date()


def build_records(rows: list[dict[str, Any]], calendar: Any) -> tuple[list[dict[str, Any]], int]:
    records: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    skipped_events = 0
    for row in rows:
        symbol = str(row.get("symbol") or row.get("yfinance_symbol") or "").strip().upper()
        code = str(row.get("security_code") or symbol.removesuffix(".T")).strip().upper()
        raw_date = str(row.get("date") or row.get("ex_dividend_date") or "")[:10]
        amount_value = row.get("amount", row.get("dividend_per_share"))
        try:
            ex_date = date.fromisoformat(raw_date)
            amount = float(amount_value)
        except (TypeError, ValueError):
            continue
        if not re.fullmatch(r"[A-Z0-9]{4,5}", code) or not symbol or not amount > 0:
            continue
        key = (symbol, ex_date.isoformat())
        if key in seen:
            continue
        seen.add(key)
        try:
            final_rights_date = previous_session(calendar, ex_date).isoformat()
        except ValueError:
            skipped_events += 1
            continue
        records.append({
            "security_code": code.removesuffix("0") if len(code) == 5 and code.endswith("0") else code,
            "symbol": symbol,
            "ex_dividend_date": ex_date.isoformat(),
            "final_rights_date": final_rights_date,
            "dividend_per_share": amount,
            "source": "Yahoo Finance via yfinance",
            "calculation_method": "previous_XTKS_session_before_ex_dividend_date",
        })
    return sorted(records, key=lambda row: (row["security_code"], row["ex_dividend_date"])), skipped_events


def read_csv(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, records: list[dict[str, Any]], generated_at: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    columns = ["security_code", "symbol", "ex_dividend_date", "final_rights_date",
               "dividend_per_share", "source", "calculation_method", "generated_at"]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for record in records:
            writer.writerow({**record, "generated_at": generated_at})


def make_client() -> Any:
    import boto3

    account = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "8d8f4f1f3c53232aaf7c7d6dd7b33944")
    access_key = os.environ.get("R2_ACCESS_KEY_ID", "").strip()
    secret_key = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip()
    if not access_key or not secret_key:
        raise RuntimeError("R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY are required for R2 mode")
    return boto3.client("s3", endpoint_url=f"https://{account}.r2.cloudflarestorage.com",
                        aws_access_key_id=access_key, aws_secret_access_key=secret_key,
                        region_name="auto")


def rows_from_r2(client: Any, bucket: str) -> tuple[list[dict[str, Any]], int]:
    paginator = client.get_paginator("list_objects_v2")
    keys = [item["Key"] for page in paginator.paginate(Bucket=bucket, Prefix=f"{PREFIX}/dividends/")
            for item in page.get("Contents", []) if item["Key"].endswith(".json")]

    def load(key: str) -> list[dict[str, Any]]:
        response = client.get_object(Bucket=bucket, Key=key)
        document = json.loads(response["Body"].read())
        symbol = str(document.get("symbol") or Path(key).stem).upper()
        return [{"symbol": symbol, **row} for row in document.get("records", []) if isinstance(row, dict)]

    rows: list[dict[str, Any]] = []
    workers = max(1, min(int(os.environ.get("RIGHTS_DATE_WORKERS", "16")), 32))
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for batch in pool.map(load, keys):
            rows.extend(batch)
    return rows, len(keys)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-csv", type=Path, help="Local Yahoo dividend event CSV to calculate")
    parser.add_argument("--output-csv", type=Path, help="Write a calculated local CSV")
    parser.add_argument("--output-json", type=Path, help="Write a calculated local JSON bundle")
    parser.add_argument("--upload", action="store_true", help=f"Publish the complete result to R2 key {OUTPUT_KEY}")
    parser.add_argument("--bucket", default=os.environ.get("R2_BUCKET", "diview-market-data"))
    args = parser.parse_args()

    calendar = xcals.get_calendar("XTKS")
    generated_at = datetime.now(UTC).isoformat()
    client = make_client() if args.upload or not args.input_csv else None
    source_symbols = 0
    if args.input_csv:
        input_rows = read_csv(args.input_csv)
        source_symbols = len({str(row.get("security_code", "")).upper() for row in input_rows})
    else:
        input_rows, source_symbols = rows_from_r2(client, args.bucket)
    records, skipped_events = build_records(input_rows, calendar)
    if not records:
        raise RuntimeError("No valid dividend events were available; refusing to publish an empty result")

    document = {
        "schema_version": 1,
        "source": "Yahoo Finance via yfinance",
        "generated_at": generated_at,
        "calendar": "XTKS",
        "calculation_method": "last XTKS session strictly before Yahoo ex-dividend date",
        "settlement_rule_note": "Derived from the observed ex-dividend date; this is a calculated historical date, not an issuer-announced future schedule.",
        "source_symbols": source_symbols,
        "event_count": len(records),
        "skipped_events": skipped_events,
        "records": records,
    }
    if args.output_csv:
        write_csv(args.output_csv, records, generated_at)
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.upload:
        client.put_object(Bucket=args.bucket, Key=OUTPUT_KEY,
                          Body=json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
                          ContentType="application/json; charset=utf-8",
                          CacheControl="public, max-age=300")
        uploaded = client.head_object(Bucket=args.bucket, Key=OUTPUT_KEY)
        expected_bytes = len(json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        if uploaded.get("ContentLength") != expected_bytes:
            raise IOError("R2 object size verification failed")
    print(json.dumps({"source": "local_csv" if args.input_csv else "r2_dividends_prefix",
                      "source_symbols": source_symbols, "input_events": len(input_rows),
                      "calculated_events": len(records), "skipped_events": skipped_events,
                      "output_key": OUTPUT_KEY if args.upload else None,
                      "generated_at": generated_at}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"rights-date calculation failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise
