#!/usr/bin/env python3
"""Calculate Japanese last rights dates from Yahoo ex-dividend events."""

from __future__ import annotations

import argparse
import calendar as calendar_module
import csv
import json
import os
import re
import sys
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from statistics import median
from typing import Any

import exchange_calendars as xcals


UTC = timezone.utc
PREFIX = os.environ.get("R2_PREFIX", "market/yfinance/v2").strip("/")
OUTPUT_KEY = f"{PREFIX}/derived/rights_dates/current.json"
HISTORY_LOOKBACK_YEARS = 10
ESTIMATE_LOOKBACK_YEARS = 5
MIN_RECURRING_YEARS = 2


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


def next_session(calendar: Any, ex_date: date) -> date:
    """The first XTKS session after ex-date is the standard record date."""
    last_session = calendar.last_session.date()
    if ex_date >= last_session:
        raise ValueError(f"XTKS calendar history does not cover the day after {ex_date}")
    sessions = calendar.sessions_in_range(
        (ex_date + timedelta(days=1)).isoformat(),
        min(last_session, ex_date + timedelta(days=14)).isoformat(),
    )
    if not len(sessions):
        raise ValueError(f"no XTKS session after ex-dividend date {ex_date}")
    return sessions[0].date()


def calendar_name_for_symbol(symbol: str) -> str:
    return "XTKS" if symbol.endswith(".T") else "XNYS"


def calendar_for_symbol(symbol: str, calendars: Any) -> Any:
    if isinstance(calendars, dict):
        name = calendar_name_for_symbol(symbol)
        if name not in calendars:
            raise ValueError(f"missing {name} exchange calendar for {symbol}")
        return calendars[name]
    return calendars


def nearest_session(calendar: Any, candidate: date) -> date:
    """Snap a recurring calendar-date estimate to the closest exchange session."""
    first_session = calendar.first_session.date()
    last_session = calendar.last_session.date()
    if candidate < first_session or candidate > last_session:
        raise ValueError(f"exchange calendar does not cover estimated ex-dividend date {candidate}")
    start = max(first_session, candidate - timedelta(days=4))
    end = min(last_session, candidate + timedelta(days=4))
    sessions = calendar.sessions_in_range(start.isoformat(), end.isoformat())
    if not len(sessions):
        raise ValueError(f"no nearby exchange session for estimated ex-dividend date {candidate}")
    return min((session.date() for session in sessions), key=lambda session: (abs((session - candidate).days), session))


def infer_upcoming_events(
    rows: list[dict[str, Any]],
    as_of: date,
    calendars: Any,
) -> list[dict[str, Any]]:
    """Infer one next ex-date per symbol from recurring historical dividend months."""
    events_by_symbol_month: dict[tuple[str, int], list[date]] = defaultdict(list)
    earliest_year = as_of.year - ESTIMATE_LOOKBACK_YEARS
    for row in rows:
        symbol = str(row.get("symbol") or row.get("yfinance_symbol") or "").strip().upper()
        if not symbol or row.get("upcoming") is True or row.get("inferred_from_history") is True:
            continue
        try:
            event_date = date.fromisoformat(str(row.get("date") or row.get("ex_dividend_date") or "")[:10])
            amount = float(row.get("amount", row.get("dividend_per_share")))
        except (TypeError, ValueError):
            continue
        if event_date > as_of or event_date.year < earliest_year or not amount > 0:
            continue
        events_by_symbol_month[(symbol, event_date.month)].append(event_date)

    estimates: list[dict[str, Any]] = []
    for (symbol, month), event_dates in events_by_symbol_month.items():
        years = sorted({event.year for event in event_dates})
        if len(years) < MIN_RECURRING_YEARS:
            continue
        recent_events = [event for event in event_dates if event.year >= earliest_year]
        typical_day = int(median(event.day for event in recent_events))
        candidate_year = as_of.year
        candidate = date(candidate_year, month, min(typical_day, calendar_module.monthrange(candidate_year, month)[1]))
        if candidate <= as_of:
            candidate_year += 1
            candidate = date(candidate_year, month,
                             min(typical_day, calendar_module.monthrange(candidate_year, month)[1]))
        try:
            estimated_ex_date = nearest_session(calendar_for_symbol(symbol, calendars), candidate)
        except ValueError:
            continue
        if estimated_ex_date <= as_of:
            candidate_year += 1
            candidate = date(candidate_year, month,
                             min(typical_day, calendar_module.monthrange(candidate_year, month)[1]))
            try:
                estimated_ex_date = nearest_session(calendar_for_symbol(symbol, calendars), candidate)
            except ValueError:
                continue
        estimates.append({
            "symbol": symbol,
            "date": estimated_ex_date.isoformat(),
            "upcoming": True,
            "inferred_from_history": True,
            "history_year_count": len(years),
        })

    earliest_by_symbol: dict[str, dict[str, Any]] = {}
    for estimate in estimates:
        symbol = estimate["symbol"]
        current = earliest_by_symbol.get(symbol)
        if current is None or estimate["date"] < current["date"]:
            earliest_by_symbol[symbol] = estimate
    return list(earliest_by_symbol.values())


def build_records(
    rows: list[dict[str, Any]],
    calendars: Any,
    as_of: date | None = None,
) -> tuple[list[dict[str, Any]], int]:
    as_of = as_of or datetime.now(UTC).date()
    # Keep historical provenance when a direct upcoming schedule happens to
    # fall on the same date as the recurring-date estimate.
    inference_rows = infer_upcoming_events(rows, as_of, calendars) + rows
    records: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    skipped_events = 0
    for row in inference_rows:
        symbol = str(row.get("symbol") or row.get("yfinance_symbol") or "").strip().upper()
        code = str(row.get("security_code") or symbol.removesuffix(".T")).strip().upper()
        raw_date = str(row.get("date") or row.get("ex_dividend_date") or "")[:10]
        upcoming = row.get("upcoming") is True
        inferred = row.get("inferred_from_history") is True
        amount_value = row.get("amount", row.get("dividend_per_share"))
        try:
            ex_date = date.fromisoformat(raw_date)
        except (TypeError, ValueError):
            continue
        try:
            amount = float(amount_value) if amount_value is not None else None
        except (TypeError, ValueError):
            amount = None
        valid_code = (re.fullmatch(r"[A-Z0-9]{4,5}", code) if symbol.endswith(".T")
                      else re.fullmatch(r"[A-Z0-9.^_=-]{1,32}", code))
        if (not valid_code or not symbol or
                (not upcoming and (amount is None or not amount > 0))):
            continue
        if not upcoming and ex_date < date(as_of.year - 1, 1, 1):
            continue
        key = (symbol, ex_date.isoformat())
        if key in seen:
            continue
        seen.add(key)
        calendar_name = calendar_name_for_symbol(symbol)
        try:
            event_calendar = calendar_for_symbol(symbol, calendars)
            final_rights_date = previous_session(event_calendar, ex_date).isoformat()
            record_date = next_session(event_calendar, ex_date).isoformat()
        except ValueError:
            skipped_events += 1
            continue
        records.append({
            "security_code": code.removesuffix("0") if symbol.endswith(".T") and len(code) == 5 and code.endswith("0") else code,
            "symbol": symbol,
            "ex_dividend_date": ex_date.isoformat(),
            "final_rights_date": final_rights_date,
            "record_date": record_date,
            "dividend_per_share": amount,
            "event_type": "history_estimated" if inferred else "upcoming" if upcoming else "observed",
            "estimated_from_history": inferred,
            "history_year_count": row.get("history_year_count") if inferred else None,
            "calendar": calendar_name,
            "source": "Yahoo Finance historical dividend event dates" if inferred else "Yahoo Finance via yfinance",
            "calculation_method": (
                f"median_recurring_month_day_snapped_to_nearest_{calendar_name}_session"
                if inferred else f"previous_and_next_{calendar_name}_sessions_around_ex_dividend_date"
            ),
        })
    return sorted(records, key=lambda row: (row["security_code"], row["ex_dividend_date"])), skipped_events


def read_csv(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, records: list[dict[str, Any]], generated_at: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    columns = ["security_code", "symbol", "ex_dividend_date", "final_rights_date", "record_date",
               "dividend_per_share", "event_type", "estimated_from_history", "history_year_count",
               "calendar", "source", "calculation_method", "generated_at"]
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
    keys: list[str] = []
    current_year = datetime.now(UTC).year
    recent_years = set(range(current_year - HISTORY_LOOKBACK_YEARS + 1, current_year + 1))
    for page in paginator.paginate(Bucket=bucket, Prefix=f"{PREFIX}/securities/"):
        for item in page.get("Contents", []):
            key = str(item.get("Key") or "")
            match = re.search(r"/dividends/(\d{4})\.json$", key)
            if key.endswith("/current.json") or (match and int(match.group(1)) in recent_years):
                keys.append(key)

    def load(key: str) -> list[dict[str, Any]]:
        response = client.get_object(Bucket=bucket, Key=key)
        document = json.loads(response["Body"].read())
        symbol = str(document.get("symbol") or "").upper()
        if key.endswith("/current.json"):
            calendar_ex_date = str((document.get("calendar") or {}).get("ex_dividend_date") or "")
            if calendar_ex_date and symbol:
                try:
                    parsed_calendar_date = date.fromisoformat(calendar_ex_date[:10])
                    if parsed_calendar_date > datetime.now(UTC).date():
                        return [{"symbol": symbol, "date": parsed_calendar_date.isoformat(), "upcoming": True}]
                except ValueError:
                    pass
            raw_ex_date = (document.get("statistics") or {}).get("exDividendDate")
            if raw_ex_date is None or not symbol:
                return []
            ex_date = datetime.fromtimestamp(float(raw_ex_date), UTC).date()
            if ex_date > datetime.now(UTC).date():
                return [{"symbol": symbol, "date": ex_date.isoformat(), "upcoming": True}]
            return []
        return [{"symbol": symbol, **row} for row in document.get("records", []) if isinstance(row, dict)]

    rows: list[dict[str, Any]] = []
    workers = max(1, min(int(os.environ.get("RIGHTS_DATE_WORKERS", "16")), 32))
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for batch in pool.map(load, keys):
            rows.extend(batch)
    return rows, len({str(row.get("symbol") or "").upper() for row in rows if row.get("symbol")})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-csv", type=Path, help="Local Yahoo dividend event CSV to calculate")
    parser.add_argument("--output-csv", type=Path, help="Write a calculated local CSV")
    parser.add_argument("--output-json", type=Path, help="Write a calculated local JSON bundle")
    parser.add_argument("--upload", action="store_true", help=f"Publish the complete result to R2 key {OUTPUT_KEY}")
    parser.add_argument("--bucket", default=os.environ.get("R2_BUCKET", "diview-market-data"))
    args = parser.parse_args()

    calendars = {"XTKS": xcals.get_calendar("XTKS"), "XNYS": xcals.get_calendar("XNYS")}
    generated_at = datetime.now(UTC).isoformat()
    client = make_client() if args.upload or not args.input_csv else None
    source_symbols = 0
    if args.input_csv:
        input_rows = read_csv(args.input_csv)
        source_symbols = len({str(row.get("security_code", "")).upper() for row in input_rows})
    else:
        input_rows, source_symbols = rows_from_r2(client, args.bucket)
    records, skipped_events = build_records(input_rows, calendars, datetime.now(UTC).date())
    if not records:
        raise RuntimeError("No valid dividend events were available; refusing to publish an empty result")

    document = {
        "schema_version": 1,
        "source": "Yahoo Finance via yfinance",
        "generated_at": generated_at,
        "calendar": "XTKS for .T symbols; XNYS for US symbols",
        "calculation_method": "Recurring ex-dividend month/day is inferred from up to five years of positive historical dividend events when that month appears in at least two distinct years. The nearest session on the applicable exchange calendar is used. The preceding session is the final rights date and the following session is the estimated record date.",
        "settlement_rule_note": "Historical event dates are used as ex-dividend dates, not payment dates. Future dates are estimates, not issuer-announced schedules. Rights dates are calculated from the applicable exchange calendar and are not issuer-confirmed record dates.",
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
        payload = json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        client.put_object(Bucket=args.bucket, Key=OUTPUT_KEY,
                          Body=payload,
                          ContentType="application/json; charset=utf-8",
                          CacheControl="private, max-age=300")
        uploaded = client.head_object(Bucket=args.bucket, Key=OUTPUT_KEY)
        if uploaded.get("ContentLength") != len(payload):
            raise IOError("R2 object size verification failed")
        saved = client.get_object(Bucket=args.bucket, Key=OUTPUT_KEY)["Body"].read()
        if saved != payload:
            raise IOError("R2 object read-back verification failed")
    print(json.dumps({"source": "local_csv" if args.input_csv else "r2_yfinance_v2",
                      "source_symbols": source_symbols, "input_events": len(input_rows),
                      "calculated_events": len(records), "skipped_events": skipped_events,
                      "history_estimated_events": sum(row.get("estimated_from_history") is True for row in records),
                      "output_key": OUTPUT_KEY if args.upload else None,
                      "generated_at": generated_at}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"rights-date calculation failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise
