"""Collect only the Yahoo Finance fields consumed by the Diview app."""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import yfinance as yf

import fetch_and_upload as collector


UTC = timezone.utc
PREFIX = os.environ.get("R2_PREFIX", "market/yfinance").strip("/")
UNIVERSE = Path(os.environ.get("MARKET_UNIVERSE_CSV", "data/irbank/organized/d1/securities.csv"))

APP_FIELDS = (
    "currency", "exchange", "market", "quoteType",
    "regularMarketChange", "regularMarketChangePercent", "trailingPE", "priceToBook",
    "dividendRate", "dividendYield",
)


def collect_symbol(client: Any, bucket: str, symbol: str) -> tuple[dict[str, Any], dict[str, Any], list[str]]:
    document = collector.load_existing(client, bucket, symbol)
    warnings: list[str] = []
    upgraded = [False]

    old_daily = document["price_history"].get("1d", [])
    period = "5d" if old_daily else os.environ.get("YFINANCE_INITIAL_PERIOD", "max")
    fresh_daily = collector.call_yfinance(
        "daily_prices",
        lambda: collector.history_rows(yf.Ticker(symbol), "1d", period),
        warnings,
        upgraded,
    )
    if fresh_daily:
        daily = collector.merge_dated(old_daily, fresh_daily, "date")
        document["price_history"]["1d"] = daily
    else:
        daily = old_daily
        if not daily:
            warnings.append("no daily price history returned")

    dividends = [
        {"date": row["date"], "amount": row["dividend_per_share"]}
        for row in daily
        if row.get("dividend_per_share") not in (None, 0, 0.0)
    ]
    splits = [
        {"date": row["date"], "ratio": row["stock_split"]}
        for row in daily
        if row.get("stock_split") not in (None, 0, 0.0)
    ]
    document["actions"]["dividends"] = collector.merge_dated(
        document["actions"].get("dividends", []), dividends, "date"
    )
    document["actions"]["stock_splits"] = collector.merge_dated(
        document["actions"].get("stock_splits", []), splits, "date"
    )

    info = collector.call_yfinance(
        "current_quote_and_valuation",
        lambda: yf.Ticker(symbol).get_info(),
        warnings,
        upgraded,
    )
    stats = dict(document.get("statistics_current", {}))
    stats.pop("sector", None)
    stats.pop("industry", None)
    if isinstance(info, dict):
        stats.update({key: collector.json_safe(info[key]) for key in APP_FIELDS if key in info and info[key] is not None})
        document["profile"] = {key: stats[key] for key in APP_FIELDS if key in stats}
        document["statistics_current"] = stats
    stats.pop("shortName", None)
    stats.pop("longName", None)
    profile = dict(document.get("profile", {}))
    profile.pop("shortName", None)
    profile.pop("longName", None)
    document["profile"] = profile
    profile = dict(document.get("profile", {}))
    profile.pop("sector", None)
    profile.pop("industry", None)
    document["profile"] = profile

    now = datetime.now(UTC).isoformat()
    document["source"] = "Yahoo Finance via yfinance"
    document["updated_at"] = now
    recent_values = [
        float(row["close"]) * float(row["volume"])
        for row in daily[-20:]
        if isinstance(row.get("close"), (int, float))
        and isinstance(row.get("volume"), (int, float))
        and row["close"] > 0
        and row["volume"] > 0
    ]
    summary = {
        "symbol": symbol,
        "updated_at": now,
        **stats,
        "latest_daily_price": daily[-1] if daily else None,
        "estimated_average_trading_value_20d": round(sum(recent_values) / len(recent_values)) if recent_values else None,
        "latest_dividend": document["actions"].get("dividends", [])[-1]
        if document["actions"].get("dividends") else None,
        "attribution": "Yahoo Finance",
    }
    return document, summary, warnings


def publish_search_catalog(client: Any, bucket: str, summaries: list[dict[str, Any]]) -> int:
    summary_by_symbol = {item["symbol"].upper(): item for item in summaries}
    old_names: dict[str, str] = {}
    with Path("data/irbank/organized/d1/securities.csv").open(encoding="utf-8-sig", newline="") as source:
        old_names = {
            row["security_code_display"].strip().upper(): row["name"].strip()
            for row in csv.DictReader(source)
        }
    with Path("kurogane-dividend/android/app/src/main/assets/jpx_security_name_master.tsv").open(encoding="utf-8", newline="") as source:
        old_names.update({row["code"].strip().upper(): row["name"].strip() for row in csv.DictReader(source, delimiter="\t")})

    items = []
    with UNIVERSE.open(encoding="utf-8-sig", newline="") as source:
        for security in csv.DictReader(source):
            code = security["security_code_display"].strip().upper()
            market = summary_by_symbol.get(f"{code}.T", {})
            quote = market.get("latest_daily_price") or {}
            price = quote.get("close")
            dividend = market.get("dividendRate")
            yield_rate = (
                round(dividend / price * 100, 2)
                if isinstance(dividend, (int, float)) and dividend >= 0
                and isinstance(price, (int, float)) and price > 0
                else None
            )
            items.append({
                "code": code,
                "name": old_names.get(code) or code,
                "category": "jp",
                "sector": security.get("sector_name") or None,
                "estimated_average_trading_value_20d": market.get("estimated_average_trading_value_20d"),
                "price": price if isinstance(price, (int, float)) and price > 0 else None,
                "yield_rate": yield_rate,
            })

    if len(items) != len({item["code"] for item in items}):
        raise RuntimeError("Search catalog contains duplicate security codes")
    collector.put_json(client, bucket, f"{PREFIX}/search_catalog.json", {
        "schema_version": 1,
        "generated_at": datetime.now(UTC).isoformat(),
        "ranking": "estimated_average_trading_value_20d",
        "items": items,
    })
    return len(items)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aggregate", action="store_true")
    args = parser.parse_args()
    collector.configure_yfinance()
    bucket = collector.required_env("R2_BUCKET")
    client = collector.get_client()
    shard_count = int(os.environ.get("YFINANCE_SHARD_COUNT", "1"))

    if args.aggregate:
        summaries: list[dict[str, Any]] = []
        for shard in range(shard_count):
            key = f"{PREFIX}/latest/shards/{shard:02d}.json"
            raw = collector.read_object(client, bucket, key)
            if raw is None:
                raise RuntimeError(f"Required snapshot shard is missing: {key}")
            snapshot = json.loads(raw)
            if int(snapshot.get("failed_count", 0)):
                raise RuntimeError(f"Shard {shard} has failed symbols; refusing to publish partial data")
            summaries.extend(snapshot.get("securities", []))

        symbols = [item.get("symbol") for item in summaries]
        if len(symbols) != len(set(symbols)):
            raise RuntimeError("Shard snapshots contain duplicate symbols")
        expected = set(collector.configured_symbols())
        if set(symbols) != expected:
            raise RuntimeError(
                f"Shard snapshots differ from the universe: missing={len(expected - set(symbols))}, "
                f"extra={len(set(symbols) - expected)}"
            )
        summaries.sort(key=lambda item: item["symbol"])
        collector.put_json(client, bucket, f"{PREFIX}/latest_market_data.json", {
            "schema_version": 1,
            "source": "Yahoo Finance via yfinance",
            "generated_at": datetime.now(UTC).isoformat(),
            "security_count": len(summaries),
            "securities": summaries,
        })
        catalog_count = publish_search_catalog(client, bucket, summaries)
        print(f"Published latest_market_data.json for {len(summaries)} securities.")
        print(f"Published search_catalog.json for {catalog_count} securities.")
        return 0

    symbols = collector.configured_symbols()
    shard_index = int(os.environ.get("YFINANCE_SHARD_INDEX", "0"))
    if shard_count < 1 or not 0 <= shard_index < shard_count:
        raise RuntimeError("YFINANCE_SHARD_INDEX must be between 0 and YFINANCE_SHARD_COUNT - 1")
    selected = symbols[shard_index::shard_count]
    summaries: list[dict[str, Any]] = []
    failed: list[str] = []
    for position, symbol in enumerate(selected, start=1):
        try:
            document, summary, warnings = collect_symbol(client, bucket, symbol)
            safe = symbol.replace("^", "_").replace("=", "_").replace("/", "_")
            collector.put_json(client, bucket, f"{PREFIX}/securities/{safe}.json", document)
            summaries.append(summary)
            if warnings:
                print(f"WARNING {symbol}: {'; '.join(warnings)}", file=sys.stderr)
        except Exception as exc:
            failed.append(f"{symbol}: {type(exc).__name__}: {exc}")
            print(f"ERROR {failed[-1]}", file=sys.stderr)
            if isinstance(exc, collector.RateLimitAbort):
                break
        if position % 10 == 0:
            print(f"shard {shard_index + 1}/{shard_count}: {position}/{len(selected)} processed")
        time.sleep(float(os.environ.get("YFINANCE_REQUEST_PAUSE_SECONDS", "2")))

    collector.put_json(client, bucket, f"{PREFIX}/latest/shards/{shard_index:02d}.json", {
        "schema_version": 1,
        "shard": shard_index,
        "generated_at": datetime.now(UTC).isoformat(),
        "securities": summaries,
        "failed_count": len(failed),
    })
    print(f"Shard {shard_index + 1}/{shard_count}: {len(summaries)}/{len(selected)} symbols, errors={len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
