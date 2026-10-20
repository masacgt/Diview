"""Build the first V2 income-advice catalog from already stored R2 records."""

from __future__ import annotations

import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date
from typing import Any

import fetch_and_upload as collector
import sync_v2 as sync


def latest_annual_statement(client: Any, bucket: str, symbol: str) -> dict[str, Any] | None:
    prefix = sync.object_key("securities", sync.safe_symbol(symbol), "financials") + "/"
    paginator = client.get_paginator("list_objects_v2")
    keys = [
        str(item["Key"])
        for page in paginator.paginate(Bucket=bucket, Prefix=prefix)
        for item in page.get("Contents", [])
        if str(item.get("Key", "")).endswith(".json")
    ]
    dated = sorted(
        (key for key in keys if key.rsplit("/", 1)[-1][:10].count("-") == 2),
        reverse=True,
    )
    year_only = sorted((key for key in keys if key not in dated), reverse=True)
    for key in (*dated, *year_only):
        document = sync.read_json(client, bucket, key)
        annual = document.get("annual") if isinstance(document, dict) else None
        if isinstance(annual, dict) and annual.get("period_end"):
            return annual
    return None


def build_candidate(client: Any, bucket: str, row: dict[str, Any], years: range,
                    as_of: date) -> tuple[dict[str, Any] | None, str | None]:
    code = str(row.get("code") or "").strip()
    symbol = sync.market_symbol(code)
    safe = sync.safe_symbol(symbol)
    current = sync.read_json(client, bucket, sync.object_key("securities", safe, "current.json"))
    if not current:
        return None, "current_record_missing"

    statements = latest_annual_statement(client, bucket, symbol)
    metrics = sync.income_advice_metrics(current, [statements] if statements else [], None)
    current["income_advice_metrics"] = metrics
    dividends: list[dict[str, Any]] = []
    for year in years:
        document = sync.read_json(
            client, bucket,
            sync.object_key("securities", safe, "dividends", f"{year}.json"),
        )
        if isinstance(document, dict) and isinstance(document.get("records"), list):
            dividends.extend(item for item in document["records"] if isinstance(item, dict))
    exclusions: dict[str, int] = {}
    candidate = sync.income_advice_candidate(
        code, str(row.get("name") or code), current, dividends, as_of, exclusions
    )
    reason = next(iter(exclusions), None)
    return candidate, reason


def build_catalog(client: Any, bucket: str, catalog: dict[str, Any],
                  workers: int = 8) -> tuple[dict[str, Any], dict[str, Any]]:
    generation = str(catalog.get("generation") or "")
    try:
        as_of = date.fromisoformat(str(catalog.get("market_data_date") or generation[:10]))
    except ValueError as exc:
        raise RuntimeError(f"Invalid V2 catalog generation: {generation!r}") from exc

    years = range(as_of.year - 3, as_of.year)
    items: list[dict[str, Any]] = []
    counts = {
        "catalog_items": 0,
        "eligible_currency": 0,
        "candidate_items": 0,
        "read_failures": 0,
        "excluded": {},
    }
    eligible_rows: list[dict[str, Any]] = []
    for row in catalog.get("items", []):
        if not isinstance(row, dict):
            continue
        counts["catalog_items"] += 1
        if row.get("category") != "jp" or row.get("currency") != "JPY":
            continue
        counts["eligible_currency"] += 1
        eligible_rows.append(row)

    print(json.dumps({
        "phase": "candidate_reads_started",
        "eligible_currency": len(eligible_rows),
        "workers": max(1, workers),
        "generation": generation,
    }, ensure_ascii=False, sort_keys=True), flush=True)
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        futures = {
            pool.submit(build_candidate, client, bucket, row, years, as_of): str(row.get("code") or "")
            for row in eligible_rows
        }
        for completed, future in enumerate(as_completed(futures), start=1):
            code = futures[future]
            try:
                candidate, reason = future.result()
                if candidate is not None:
                    items.append({**candidate, "source_catalog_generation": generation})
                elif reason:
                    counts["excluded"][reason] = counts["excluded"].get(reason, 0) + 1
            except Exception as exc:
                counts["read_failures"] += 1
                print(f"Unable to build candidate {code}: {type(exc).__name__}: {exc}", file=sys.stderr)
            if completed % 100 == 0 or completed == len(futures):
                print(json.dumps({
                    "phase": "candidate_reads_progress",
                    "completed": completed,
                    "total": len(futures),
                    "candidates": len(items),
                    "read_failures": counts["read_failures"],
                }, ensure_ascii=False, sort_keys=True), flush=True)

    items.sort(key=lambda item: item["code"])
    counts["candidate_items"] = len(items)
    result = {
        "schema_version": 1,
        "generation": generation,
        "generated_at": sync.iso_now(),
        "source": "Yahoo Finance via yfinance V2 stored in R2",
        "amount_basis": "per_share_annual_amount_and_recurring_event_months",
        "items": items,
    }
    return result, counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publish", action="store_true", help="Write and read back the derived catalog in R2")
    parser.add_argument("--workers", type=int, default=8, help="Maximum concurrent R2 candidate reads (default: 8)")
    args = parser.parse_args()
    if not args.publish:
        parser.error("--publish is required because this workflow is intended to publish the initial R2 catalog")

    bucket = collector.required_env("R2_BUCKET")
    client = collector.get_client()
    catalog_key = sync.object_key("catalog", "current.json")
    catalog = sync.read_json(client, bucket, catalog_key)
    if not catalog or not isinstance(catalog.get("items"), list):
        raise RuntimeError("The published V2 catalog is missing or invalid")

    result, counts = build_catalog(client, bucket, catalog, workers=args.workers)
    print(json.dumps({**counts, "generation": result["generation"]}, ensure_ascii=False, sort_keys=True), flush=True)
    if counts["read_failures"]:
        raise RuntimeError(f"Aborting without publishing because {counts['read_failures']} candidate records failed to read")
    key = sync.object_key("derived", "income_advice", "current.json")
    sync.put_json_if_changed(client, bucket, key, result)
    saved = sync.read_json(client, bucket, key)
    if saved != result:
        raise RuntimeError("R2 read-back verification failed for the income-advice catalog")
    print(json.dumps({**counts, "generation": result["generation"], "r2_key": key}, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
