#!/usr/bin/env python3
"""Refresh only app-used Yahoo dividend and current valuation fields."""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
from datetime import datetime, timezone
from typing import Any

import yfinance as yf

import fetch_and_upload as collector


UTC = timezone.utc
PREFIX = os.environ.get("R2_PREFIX", "market/yfinance").strip("/")
CURRENT_FIELDS = ("trailingPE", "priceToBook", "dividendRate", "dividendYield")


def now_utc() -> str:
    return datetime.now(UTC).isoformat()


def safe_symbol(symbol: str) -> str:
    return symbol.replace("^", "_").replace("=", "_").replace("/", "_")


def numeric(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    result = float(value)
    return result if math.isfinite(result) else None


def recent_actions(rows: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    dividends: list[dict[str, Any]] = []
    splits: list[dict[str, Any]] = []
    for row in rows:
        date = str(row.get("date") or "")[:10]
        amount = numeric(row.get("dividend_per_share"))
        ratio = numeric(row.get("stock_split"))
        if len(date) == 10 and amount is not None and amount > 0:
            dividends.append({"date": date, "amount": amount})
        if len(date) == 10 and ratio is not None and ratio > 0:
            splits.append({"date": date, "ratio": ratio})
    return dividends, splits


def publish_compact_dividends(client: Any, bucket: str, symbol: str, document: dict[str, Any]) -> None:
    key = f"{PREFIX}/dividends/{safe_symbol(symbol)}.json"
    original = collector.read_object(client, bucket, key)
    previous = json.loads(original) if original else {}
    records = document.get("actions", {}).get("dividends", [])
    updated_at = previous.get("updated_at") if previous.get("records", []) == records else now_utc()
    value = {
        "schema_version": 1,
        "source": "Yahoo Finance via yfinance",
        "symbol": symbol,
        "updated_at": updated_at or document.get("updated_at") or now_utc(),
        "records": records,
    }
    collector.put_json_if_changed(client, bucket, key, value, original)


def fetch_symbol(client: Any, bucket: str, symbol: str) -> dict[str, Any]:
    document, _ = collector.load_existing(client, bucket, symbol, with_raw=True)
    before = dict(document)
    before.pop("updated_at", None)

    warnings: list[str] = []
    upgrade_state = [False]
    ticker = yf.Ticker(symbol)
    info = collector.call_yfinance(
        "current_dividend_and_valuation",
        ticker.get_info,
        warnings,
        upgrade_state,
    )
    if not isinstance(info, dict):
        info = {}
    if not info:
        warnings.append("Yahoo returned no current company statistics; retaining cached values")

    # Five recent sessions capture new dividend/split actions without re-fetching
    # the full price history already maintained by the Cloudflare price Worker.
    rows = collector.call_yfinance(
        "recent_dividend_and_split_actions",
        lambda: collector.history_rows(ticker, "1d", "5d"),
        warnings,
        upgrade_state,
    )
    if rows is None:
        warnings.append("Yahoo returned no recent action history; retaining cached events")
        rows = []

    stats = dict(document.get("statistics_current") or {})
    profile = dict(document.get("profile") or {})
    fetched_fields: dict[str, Any] = {}
    for field in CURRENT_FIELDS:
        if field not in info or info[field] is None:
            continue
        value = collector.json_safe(info[field])
        if value is None:
            continue
        stats[field] = value
        profile[field] = value
        fetched_fields[field] = value
    document["statistics_current"] = stats
    document["profile"] = profile

    dividends, splits = recent_actions(rows)
    actions = dict(document.get("actions") or {})
    actions["dividends"] = collector.merge_dated(actions.get("dividends", []), dividends, "date")
    actions["stock_splits"] = collector.merge_dated(actions.get("stock_splits", []), splits, "date")
    document["actions"] = actions
    document.setdefault("symbol", symbol)
    document.setdefault("schema_version", 2)

    after = dict(document)
    after.pop("updated_at", None)
    changed = collector.json_payload(before) != collector.json_payload(after)
    if changed:
        document["source"] = "Yahoo Finance via yfinance"
        document["updated_at"] = now_utc()
        key = f"{PREFIX}/securities/{safe_symbol(symbol)}.json"
        original = collector.read_object(client, bucket, key)
        collector.put_json_if_changed(client, bucket, key, document, original)
    else:
        document["updated_at"] = document.get("updated_at") or now_utc()

    publish_compact_dividends(client, bucket, symbol, document)
    if warnings:
        print(f"WARNING {symbol}: {'; '.join(warnings)}", file=sys.stderr)
    latest_dividend = actions["dividends"][-1] if actions["dividends"] else None
    return {
        "symbol": symbol,
        "statistics": fetched_fields,
        "latest_dividend": latest_dividend,
        "warning_count": len(warnings),
    }


def run_shard(client: Any, bucket: str, run_id: str, shard_index: int, shard_count: int) -> int:
    symbols = collector.configured_symbols()
    if shard_count < 1 or not 0 <= shard_index < shard_count:
        raise RuntimeError("YFINANCE_SHARD_INDEX must be between 0 and YFINANCE_SHARD_COUNT - 1")
    selected = symbols[shard_index::shard_count]
    updates: list[dict[str, Any]] = []
    failed: list[str] = []

    for position, symbol in enumerate(selected, start=1):
        try:
            updates.append(fetch_symbol(client, bucket, symbol))
        except Exception as exc:
            failed.append(f"{symbol}: {type(exc).__name__}: {exc}")
            print(f"ERROR {failed[-1]}", file=sys.stderr)
            if isinstance(exc, collector.RateLimitAbort):
                break
        if position % 25 == 0:
            print(f"shard {shard_index + 1}/{shard_count}: {position}/{len(selected)} processed", flush=True)
        time.sleep(max(0.0, float(os.environ.get("YFINANCE_REQUEST_PAUSE_SECONDS", "2"))))

    key = f"{PREFIX}/latest/weekly-minimal/runs/{run_id}/shards/{shard_index:02d}.json"
    collector.put_json(client, bucket, key, {
        "schema_version": 1,
        "run_id": run_id,
        "shard": shard_index,
        "symbols": updates,
        "failed": failed,
        "failed_count": len(failed),
    })
    print(f"Shard {shard_index + 1}/{shard_count}: {len(updates)}/{len(selected)} updated; failures={len(failed)}")
    return 1 if failed or len(updates) != len(selected) else 0


def publish_minimal_snapshot(client: Any, bucket: str, run_id: str, shard_count: int) -> None:
    expected = set(collector.configured_symbols())
    updates: dict[str, dict[str, Any]] = {}
    for shard in range(shard_count):
        key = f"{PREFIX}/latest/weekly-minimal/runs/{run_id}/shards/{shard:02d}.json"
        raw = collector.read_object(client, bucket, key)
        if raw is None:
            raise RuntimeError(f"Required weekly shard is missing: {key}")
        payload = json.loads(raw)
        if payload.get("run_id") != run_id or int(payload.get("failed_count", 0)):
            raise RuntimeError(f"Weekly shard {shard} is incomplete or has failures")
        for item in payload.get("symbols", []):
            symbol = str(item.get("symbol") or "").upper()
            if not symbol or symbol in updates:
                raise RuntimeError(f"Invalid or duplicate symbol in weekly shard: {symbol}")
            updates[symbol] = item
    if set(updates) != expected:
        raise RuntimeError(
            f"Weekly shards differ from the current universe: missing={len(expected - set(updates))}, "
            f"extra={len(set(updates) - expected)}"
        )

    now = now_utc()
    snapshot_key = f"{PREFIX}/latest_market_data.json"
    snapshot_raw = collector.read_object(client, bucket, snapshot_key)
    if snapshot_raw is None:
        raise RuntimeError("latest_market_data.json is missing; run the full update once to create the baseline")
    previous_snapshot = json.loads(snapshot_raw)
    previous_rows = previous_snapshot.get("securities", [])
    if not isinstance(previous_rows, list) or any(not isinstance(item, dict) for item in previous_rows):
        raise RuntimeError("latest_market_data.json has an invalid securities array")
    catalog_key = f"{PREFIX}/search_catalog.json"
    catalog_raw = collector.read_object(client, bucket, catalog_key)
    if catalog_raw is None:
        raise RuntimeError("search_catalog.json is missing; run the full update once to create the baseline")
    catalog = json.loads(catalog_raw)
    catalog_items = catalog.get("items")
    if not isinstance(catalog_items, list):
        raise RuntimeError("search_catalog.json has no items array")

    previous_by_symbol = {
        str(item.get("symbol") or "").upper(): item
        for item in previous_rows if item.get("symbol")
    }
    merged_rows: list[dict[str, Any]] = []
    for symbol in sorted(expected):
        row = dict(previous_by_symbol.get(symbol) or {"symbol": symbol})
        update = updates.get(symbol)
        if update:
            for field, value in update.get("statistics", {}).items():
                row[field] = value
            if update.get("latest_dividend") is not None:
                row["latest_dividend"] = update["latest_dividend"]
        merged_rows.append(row)
    previous_sorted = sorted(previous_rows, key=lambda item: str(item.get("symbol", "")).upper())
    snapshot_changed = merged_rows != previous_sorted or previous_snapshot.get("security_count") != len(merged_rows)
    snapshot = dict(previous_snapshot)
    snapshot.update({
        "schema_version": previous_snapshot.get("schema_version", 1),
        "source": previous_snapshot.get("source", "Yahoo Finance via yfinance"),
        "generated_at": now if snapshot_changed else previous_snapshot.get("generated_at", now),
        "security_count": len(merged_rows),
        "securities": merged_rows,
    })
    collector.put_json_if_changed(client, bucket, snapshot_key, snapshot, snapshot_raw)

    items_by_code = {str(item.get("code") or "").upper(): item for item in catalog_items if isinstance(item, dict)}
    catalog_changed = False
    for symbol, update in updates.items():
        code = symbol[:-2] if symbol.endswith(".T") else symbol
        item = items_by_code.get(code)
        if item is None:
            continue
        statistics = update.get("statistics")
        if not isinstance(statistics, dict):
            continue
        dividend = numeric(statistics.get("dividendRate"))
        reported_yield = numeric(statistics.get("dividendYield"))
        no_dividend = (
            (dividend == 0.0) or (dividend is None and reported_yield == 0.0)
        ) and not (reported_yield is not None and reported_yield > 0.0)
        if dividend is None and not no_dividend:
            continue
        if no_dividend:
            dividend = 0.0
        if dividend is not None and dividend < 0:
            dividend = None
        if item.get("annual_dividend_per_share") != dividend:
            item["annual_dividend_per_share"] = dividend
            catalog_changed = True
        price = numeric(item.get("price"))
        yield_rate = round(dividend / price * 100, 2) if dividend is not None and price is not None and price > 0 else None
        if item.get("yield_rate") != yield_rate:
            item["yield_rate"] = yield_rate
            catalog_changed = True
        dividend_status = "no_dividend" if no_dividend else "has_dividend" if dividend is not None and dividend > 0 else "unknown"
        if item.get("dividend_status") != dividend_status:
            item["dividend_status"] = dividend_status
            catalog_changed = True
    if catalog_changed:
        catalog["dividend_data_updated_at"] = now
    collector.put_json_if_changed(client, bucket, catalog_key, catalog, catalog_raw)
    print(
        f"Published weekly app fields for {len(updates)} securities; "
        f"warnings={sum(int(item.get('warning_count', 0)) for item in updates.values())}, "
        f"market snapshot changed={snapshot_changed}, catalog changed={catalog_changed}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aggregate", action="store_true", help="Publish the completed weekly shards")
    args = parser.parse_args()
    collector.configure_yfinance()
    client = collector.get_client()
    bucket = collector.required_env("R2_BUCKET")
    run_id = os.environ.get("YFINANCE_RUN_ID", datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ"))
    shard_count = int(os.environ.get("YFINANCE_SHARD_COUNT", "8"))

    if args.aggregate:
        publish_minimal_snapshot(client, bucket, run_id, shard_count)
        return 0
    shard_index = int(os.environ.get("YFINANCE_SHARD_INDEX", "0"))
    return run_shard(client, bucket, run_id, shard_index, shard_count)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
