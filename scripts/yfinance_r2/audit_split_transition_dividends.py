"""Audit split-adjusted yfinance dividend events against issuer DPS history.

This is a read-only R2 study. It does not modify the published market data.
The comparison groups dated yfinance events into the actual fiscal periods
whose end dates are present in the IRBANK/EDINET/TDnet snapshot.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from calendar import monthrange
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError


ROOT = "market/yfinance/v2"
SPLIT_EVENTS_PATH = Path(__file__).parents[2] / "data/irbank/catalog/share_split_events.csv"
ISSUER_DATA_ROOT = Path(__file__).parents[2] / "data/irbank/organized/by_security"
OUTPUT_ROOT = Path("split-transition-audit")
FISCAL_PERIOD_RE = re.compile(r"^(\d{4})/(\d{2})$")


def parse_period_end(value: Any) -> date | None:
    match = FISCAL_PERIOD_RE.fullmatch(str(value or ""))
    if not match:
        return None
    year, month = int(match.group(1)), int(match.group(2))
    if month < 1 or month > 12:
        return None
    return date(year, month, monthrange(year, month)[1])


def fiscal_year_for_event(event_date: date, period_ends: list[date]) -> int | None:
    """Return the first reported fiscal period end on or after an event date."""
    following = [period_end for period_end in period_ends if period_end >= event_date]
    return min(following).year if following else None


def compare_fiscal_dividends(
    issuer_rows: list[dict[str, Any]],
    yfinance_events: list[dict[str, Any]],
    period_ends: list[date],
    *,
    first_year: int,
    last_year: int,
    tolerance: float = 0.05,
) -> list[dict[str, Any]]:
    """Compare actual reported DPS with yfinance events attributed by fiscal dates."""
    yfinance_by_fiscal_year: dict[int, float] = {}
    event_keys: set[tuple[str, float]] = set()
    for event in yfinance_events:
        try:
            event_date = date.fromisoformat(str(event.get("date") or "")[:10])
            amount = float(event.get("amount"))
        except (TypeError, ValueError):
            continue
        if amount <= 0:
            continue
        key = (event_date.isoformat(), amount)
        if key in event_keys:
            continue
        event_keys.add(key)
        fiscal_year = fiscal_year_for_event(event_date, period_ends)
        if fiscal_year is not None:
            yfinance_by_fiscal_year[fiscal_year] = yfinance_by_fiscal_year.get(fiscal_year, 0.0) + amount

    comparisons = []
    seen_years: set[int] = set()
    for row in issuer_rows:
        if str(row.get("event_type") or "") != "annual_reported":
            continue
        try:
            fiscal_year = int(row.get("fiscal_year"))
            issuer_amount = float(row.get("annual_dps_adjusted"))
        except (TypeError, ValueError):
            continue
        if fiscal_year < first_year or fiscal_year > last_year or fiscal_year in seen_years:
            continue
        seen_years.add(fiscal_year)
        yfinance_amount = yfinance_by_fiscal_year.get(fiscal_year, 0.0)
        difference = round(yfinance_amount - issuer_amount, 8)
        comparisons.append({
            "fiscal_year": fiscal_year,
            "issuer_adjusted_dps": issuer_amount,
            "yfinance_event_total": round(yfinance_amount, 8),
            "difference": difference,
            "within_tolerance": abs(difference) <= tolerance,
        })
    return sorted(comparisons, key=lambda row: row["fiscal_year"])


def read_optional_json(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    try:
        response = client.get_object(Bucket=bucket, Key=key)
    except ClientError as exc:
        error_code = str(exc.response.get("Error", {}).get("Code", ""))
        if error_code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise
    value = json.loads(response["Body"].read())
    return value if isinstance(value, dict) else None


def recent_current_split_codes(events_path: Path, active_codes: set[str], as_of: date) -> dict[str, list[dict[str, Any]]]:
    cutoff = date(as_of.year - 8, as_of.month, min(as_of.day, monthrange(as_of.year - 8, as_of.month)[1]))
    result: dict[str, list[dict[str, Any]]] = {}
    with events_path.open(encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            code = str(row.get("code") or "").strip()
            event_type = str(row.get("event_type") or "").strip().lower()
            try:
                effective_date = date.fromisoformat(str(row.get("effective_date") or ""))
                factor = float(row.get("factor") or "")
            except (TypeError, ValueError):
                continue
            if (
                code not in active_codes
                or event_type not in {"split", "reverse_split"}
                or not cutoff <= effective_date <= as_of
                or factor <= 0
                or abs(factor - 1) < 1e-9
            ):
                continue
            result.setdefault(code, []).append({
                "date": effective_date.isoformat(),
                "ratio": factor,
                "event_type": event_type,
            })
    for rows in result.values():
        rows.sort(key=lambda row: (row["date"], row["ratio"]))
    return result


def load_issuer_snapshot(code: str) -> dict[str, Any] | None:
    path = ISSUER_DATA_ROOT / f"{code}0.json"
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def audit_security(
    client: Any,
    bucket: str,
    code: str,
    split_events: list[dict[str, Any]],
    issuer_document: dict[str, Any] | None,
    *,
    as_of: date,
    first_year: int,
    last_year: int,
    tolerance: float,
) -> dict[str, Any]:
    symbol = f"{code}.T"
    current = read_optional_json(client, bucket, f"{ROOT}/securities/{symbol}/current.json") or {}
    history_status = str((current.get("field_status") or {}).get("dividend_history") or "not_fetched")
    events: list[dict[str, Any]] = []
    first_event_year = first_year - 1
    for year in range(first_event_year, as_of.year + 1):
        key = f"{ROOT}/securities/{symbol}/dividends/{year}.json"
        document = read_optional_json(client, bucket, key)
        if document and isinstance(document.get("records"), list):
            events.extend(row for row in document["records"] if isinstance(row, dict))

    issuer_rows = (issuer_document or {}).get("dividends") or []
    periods = (issuer_document or {}).get("financials") or {}
    period_ends = sorted({
        end_date
        for row in periods.get("annual", [])
        if isinstance(row, dict)
        for end_date in [parse_period_end(row.get("fiscal_year"))]
        if end_date is not None
    })
    comparisons = compare_fiscal_dividends(
        issuer_rows,
        events,
        period_ends,
        first_year=first_year,
        last_year=last_year,
        tolerance=tolerance,
    )
    matched = [row for row in comparisons if row["within_tolerance"]]
    mismatched = [row for row in comparisons if not row["within_tolerance"]]
    issuer_year_count = len(comparisons)
    status = "compared" if issuer_year_count and period_ends else (
        "issuer_dps_unavailable" if not issuer_year_count else "fiscal_period_ends_unavailable"
    )
    if history_status in {"failed", "not_fetched"}:
        status = f"yfinance_{history_status}"
    return {
        "code": code,
        "symbol": symbol,
        "name": str((issuer_document or {}).get("name") or code),
        "as_of": as_of.isoformat(),
        "yfinance_history_status": history_status,
        "yfinance_event_count": len(events),
        "split_events": split_events,
        "issuer_dps_years_compared": issuer_year_count,
        "fiscal_years_matched": len(matched),
        "fiscal_years_mismatched": len(mismatched),
        "status": status,
        "comparisons": comparisons,
    }


def run(*, workers: int, as_of: date, first_year: int, last_year: int, tolerance: float) -> int:
    account_id = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "").strip()
    access_key = os.environ.get("R2_ACCESS_KEY_ID", "").strip()
    secret_key = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip()
    bucket = os.environ.get("R2_BUCKET", "").strip()
    if not all((account_id, access_key, secret_key, bucket)):
        raise RuntimeError("CLOUDFLARE_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, and R2_BUCKET are required")

    client = boto3.client(
        "s3",
        endpoint_url=f"https://{account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name="auto",
        config=Config(
            signature_version="s3v4",
            retries={"max_attempts": 4, "mode": "standard"},
            max_pool_connections=workers,
        ),
    )
    universe = read_optional_json(client, bucket, f"{ROOT}/universe/current.json") or {}
    active_codes = {
        str(row.get("code") or "").strip()
        for row in universe.get("securities", [])
        if isinstance(row, dict) and row.get("status") == "active" and re.fullmatch(r"[A-Z0-9]{4}", str(row.get("code") or ""))
    }
    split_codes = recent_current_split_codes(SPLIT_EVENTS_PATH, active_codes, as_of)
    print(json.dumps({
        "phase": "selected",
        "active_universe_codes": len(active_codes),
        "recent_split_security_codes": len(split_codes),
        "split_event_records": sum(len(rows) for rows in split_codes.values()),
        "as_of": as_of.isoformat(),
        "fiscal_year_range": [first_year, last_year],
        "source": "read-only R2 yfinance events; IRBANK issuer DPS snapshot",
    }, ensure_ascii=False, sort_keys=True), flush=True)

    issuer_documents = {code: load_issuer_snapshot(code) for code in split_codes}
    results: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(
                audit_security, client, bucket, code, split_events, issuer_documents[code],
                as_of=as_of, first_year=first_year, last_year=last_year, tolerance=tolerance,
            ): code
            for code, split_events in split_codes.items()
        }
        for future in as_completed(futures):
            code = futures[future]
            try:
                results.append(future.result())
            except Exception as exc:
                failures.append({"code": code, "error": f"{type(exc).__name__}: {exc}"[:400]})

    results.sort(key=lambda row: row["code"])
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    comparison_rows = [
        {"code": result["code"], "name": result["name"], "status": result["status"], **comparison}
        for result in results
        for comparison in result["comparisons"]
    ]
    security_fields = [
        "code", "symbol", "name", "as_of", "yfinance_history_status", "yfinance_event_count",
        "split_events", "issuer_dps_years_compared", "fiscal_years_matched", "fiscal_years_mismatched", "status",
    ]
    with (OUTPUT_ROOT / "by-security.csv").open("w", encoding="utf-8-sig", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=security_fields)
        writer.writeheader()
        for result in results:
            writer.writerow({**result, "split_events": json.dumps(result["split_events"], ensure_ascii=False)})
    comparison_fields = [
        "code", "name", "status", "fiscal_year", "issuer_adjusted_dps", "yfinance_event_total", "difference", "within_tolerance",
    ]
    with (OUTPUT_ROOT / "by-fiscal-year.csv").open("w", encoding="utf-8-sig", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=comparison_fields)
        writer.writeheader()
        writer.writerows(comparison_rows)

    status_counts: dict[str, int] = {}
    for result in results:
        status_counts[result["status"]] = status_counts.get(result["status"], 0) + 1
    summary = {
        "as_of": as_of.isoformat(),
        "active_universe_codes": len(active_codes),
        "recent_split_security_codes": len(split_codes),
        "split_event_records": sum(len(rows) for rows in split_codes.values()),
        "audited_security_codes": len(results),
        "failed_security_codes": len(failures),
        "issuer_fiscal_year_comparisons": len(comparison_rows),
        "matched_fiscal_years": sum(row["within_tolerance"] for row in comparison_rows),
        "mismatched_fiscal_years": sum(not row["within_tolerance"] for row in comparison_rows),
        "status_counts": dict(sorted(status_counts.items())),
        "failures": failures[:50],
        "tolerance_per_share": tolerance,
        "fiscal_year_range": [first_year, last_year],
        "method": "Deduplicate dated yfinance events, assign each event to the first reported fiscal period end on/after its ex-date, then compare the sum with IRBANK annual_dps_adjusted. No R2 objects are written.",
        "limitations": [
            "IRBANK is a processed issuer-disclosure snapshot; a mismatch is a review candidate, not proof that yfinance is wrong.",
            "Periods without reported issuer DPS or without fiscal period-end dates are not numerically compared.",
            "This audit does not change the app's yfinance-only market-data source or stored values.",
        ],
        "attribution": "Issuer DPS snapshot: IRBANK Connect, processed from EDINET/TDnet; source refs are retained in the repository snapshot.",
    }
    (OUTPUT_ROOT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True), flush=True)
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=24)
    parser.add_argument("--as-of", type=date.fromisoformat, default=datetime.now(ZoneInfo("Asia/Tokyo")).date())
    parser.add_argument("--first-year", type=int, default=2018)
    parser.add_argument("--last-year", type=int, default=2026)
    parser.add_argument("--tolerance", type=float, default=0.05)
    args = parser.parse_args()
    workers = max(1, min(48, args.workers))
    try:
        return run(
            workers=workers,
            as_of=args.as_of,
            first_year=args.first_year,
            last_year=args.last_year,
            tolerance=max(0.0, args.tolerance),
        )
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
