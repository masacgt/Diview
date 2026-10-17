"""Recompute calendar-year dividend streaks from already published V2 records."""

from __future__ import annotations

import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import fetch_and_upload as collector
from sync_v2 import calendar_year_dividend_metrics, market_symbol, object_key, read_json, safe_symbol


JST = ZoneInfo("Asia/Tokyo")


def reaggregate_security(client, bucket: str, code: str, as_of) -> dict:
    symbol = market_symbol(code)
    key = object_key("securities", safe_symbol(symbol), "current.json")
    current = read_json(client, bucket, key)
    if not current:
        return {"code": code, "status": "missing_current"}

    field_status = current.get("field_status") if isinstance(current.get("field_status"), dict) else {}
    history_status = field_status.get("dividend_history")
    if history_status not in {"available", "empty"}:
        return {"code": code, "status": "skipped_history_" + str(history_status or "unknown")}

    dividend = current.get("dividend") if isinstance(current.get("dividend"), dict) else {}
    previous_metrics = dividend.get("calendar_year_metrics")
    if not isinstance(previous_metrics, dict) or not isinstance(previous_metrics.get("annual_records"), list):
        return {"code": code, "status": "missing_annual_records"}

    metrics = calendar_year_dividend_metrics([], previous_metrics, as_of)
    same_metrics = (
        {key: value for key, value in metrics.items() if key != "updated_at"}
        == {key: value for key, value in previous_metrics.items() if key != "updated_at"}
    )
    if same_metrics:
        return {"code": code, "status": "unchanged", "record_count": len(metrics["annual_records"])}

    updated = dict(current)
    updated_dividend = dict(dividend)
    updated_dividend["calendar_year_metrics"] = metrics
    updated["dividend"] = updated_dividend
    collector.put_json(client, bucket, key, updated)
    readback = read_json(client, bucket, key)
    if readback != updated:
        raise RuntimeError(f"R2 read-back mismatch for {symbol}")
    return {"code": code, "status": "updated", "record_count": len(metrics["annual_records"])}


def run(*, workers: int, report_path: str | None = None) -> int:
    collector.required_env("CLOUDFLARE_ACCOUNT_ID")
    bucket = collector.required_env("R2_BUCKET")
    client = collector.get_client()
    universe = read_json(client, bucket, object_key("universe", "current.json"))
    if not universe or not isinstance(universe.get("securities"), list):
        raise RuntimeError("Published yfinance V2 universe is unavailable")
    codes = sorted({
        str(row.get("code", "")).strip()
        for row in universe["securities"]
        if isinstance(row, dict) and row.get("status") == "active" and row.get("code")
    })
    as_of = datetime.now(JST).date()
    results = []
    errors = []
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        futures = {pool.submit(reaggregate_security, client, bucket, code, as_of): code for code in codes}
        for future in as_completed(futures):
            code = futures[future]
            try:
                result = future.result()
                results.append(result)
            except Exception as exc:
                errors.append({"code": code, "error": f"{type(exc).__name__}: {exc}"})

    summary = {
        "generated_at": datetime.now(JST).isoformat(),
        "as_of_date": as_of.isoformat(),
        "bucket": bucket,
        "universe_count": len(codes),
        "updated": sum(row["status"] == "updated" for row in results),
        "unchanged": sum(row["status"] == "unchanged" for row in results),
        "skipped": sum(row["status"] not in {"updated", "unchanged"} for row in results),
        "errors": errors,
        "results": sorted(results, key=lambda row: row["code"]),
    }
    payload = json.dumps(summary, ensure_ascii=False, indent=2)
    print(payload)
    if report_path:
        Path(report_path).write_text(payload + "\n", encoding="utf-8")
    return 1 if errors else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publish", action="store_true", help="Write recalculated metrics to R2 and verify each write")
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--report", help="Optional JSON report path")
    args = parser.parse_args()
    if not args.publish:
        parser.error("--publish is required to update R2")
    return run(workers=args.workers, report_path=args.report)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
