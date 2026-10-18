"""Build the first V2 income-advice catalog from already stored R2 records."""

from __future__ import annotations

import argparse
import json
import os
import sys
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


def build_catalog(client: Any, bucket: str, catalog: dict[str, Any]) -> tuple[dict[str, Any], dict[str, int]]:
    generation = str(catalog.get("generation") or "")
    try:
        as_of = date.fromisoformat(generation)
    except ValueError as exc:
        raise RuntimeError(f"Invalid V2 catalog generation: {generation!r}") from exc

    years = range(as_of.year - 3, as_of.year)
    items: list[dict[str, Any]] = []
    counts = {"catalog_items": 0, "eligible_currency": 0, "candidate_items": 0, "read_failures": 0}
    for row in catalog.get("items", []):
        if not isinstance(row, dict):
            continue
        counts["catalog_items"] += 1
        code = str(row.get("code") or "").strip()
        if row.get("category") != "jp" or row.get("currency") != "JPY":
            continue
        counts["eligible_currency"] += 1
        try:
            symbol = sync.market_symbol(code)
            safe = sync.safe_symbol(symbol)
            current = sync.read_json(client, bucket, sync.object_key("securities", safe, "current.json"))
            if not current:
                continue
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
            candidate = sync.income_advice_candidate(code, str(row.get("name") or code), current, dividends, as_of)
            if candidate is not None:
                items.append(candidate)
        except Exception as exc:
            counts["read_failures"] += 1
            print(f"Unable to build candidate {code}: {type(exc).__name__}: {exc}", file=sys.stderr)

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
    args = parser.parse_args()
    if not args.publish:
        parser.error("--publish is required because this workflow is intended to publish the initial R2 catalog")

    bucket = collector.required_env("R2_BUCKET")
    client = collector.get_client()
    catalog_key = sync.object_key("catalog", "current.json")
    catalog = sync.read_json(client, bucket, catalog_key)
    if not catalog or not isinstance(catalog.get("items"), list):
        raise RuntimeError("The published V2 catalog is missing or invalid")

    result, counts = build_catalog(client, bucket, catalog)
    print(json.dumps({**counts, "generation": result["generation"]}, ensure_ascii=False, sort_keys=True), flush=True)
    if counts["read_failures"]:
        raise RuntimeError(f"Aborting without publishing because {counts['read_failures']} candidate records failed to read")
    if not result["items"]:
        raise RuntimeError("Aborting without publishing an empty candidate catalog")

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
