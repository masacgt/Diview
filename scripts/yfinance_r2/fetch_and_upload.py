"""Collect Yahoo Finance history and fundamentals for one shard of the security master."""

from __future__ import annotations

import argparse
import csv
import importlib.metadata
import importlib
import json
import math
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable

import boto3
import yfinance as yf


UTC = timezone.utc
DATA_DIR = Path(os.environ.get("PRICE_DATA_DIR", "data/market/yfinance"))
PREFIX = os.environ.get("R2_PREFIX", "market/yfinance").strip("/")


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


def configured_symbols() -> list[str]:
    raw = os.environ.get("YFINANCE_SYMBOLS", "").strip()
    if raw:
        symbols = []
        for item in raw.split(","):
            symbol = item.strip().upper()
            if not symbol:
                continue
            if symbol.isalnum() and len(symbol) in (4, 5):
                symbol = f"{symbol}.T"
            symbols.append(symbol)
        if len(set(symbols)) != len(symbols):
            raise RuntimeError("YFINANCE_SYMBOLS contains duplicate symbols")
        return symbols

    universe_csv = os.environ.get("MARKET_UNIVERSE_CSV", "").strip()
    if not universe_csv:
        raise RuntimeError("Set YFINANCE_SYMBOLS or MARKET_UNIVERSE_CSV")
    symbols: set[str] = set()
    with open(universe_csv, "r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames or "security_code_display" not in reader.fieldnames:
            raise RuntimeError("Universe CSV must contain a security_code_display column")
        for row in reader:
            code = (row.get("security_code_display") or "").strip().upper()
            if code and len(code) in (4, 5) and code.isalnum():
                symbols.add(f"{code}.T")
    result = sorted(symbols)
    if not result:
        raise RuntimeError(f"No Yahoo-compatible symbols found in {universe_csv}")
    return result


def json_safe(value: Any) -> Any:
    if value is None or isinstance(value, (str, int, bool)):
        return value
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, dict):
        return {str(k): json_safe(v) for k, v in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [json_safe(v) for v in value]
    if hasattr(value, "item"):
        try:
            return json_safe(value.item())
        except (ValueError, TypeError):
            pass
    if hasattr(value, "isoformat"):
        try:
            return value.isoformat()
        except (ValueError, TypeError):
            pass
    return str(value)


def frame_periods(frame: Any) -> list[dict[str, Any]]:
    """Serialize yfinance DataFrames as dated records with named financial metrics."""
    if frame is None or getattr(frame, "empty", True):
        return []
    periods = []
    for period in frame.columns:
        values = {str(metric): json_safe(frame.loc[metric, period]) for metric in frame.index}
        periods.append({"period_end": json_safe(period), "values": values})
    return periods


def frame_records(frame: Any, date_key: str = "date") -> list[dict[str, Any]]:
    """Serialize a row-oriented DataFrame such as earnings history."""
    if frame is None or getattr(frame, "empty", True):
        return []
    rows = []
    for index, row in frame.iterrows():
        record = {date_key: json_safe(index)}
        record.update({str(column): json_safe(value) for column, value in row.items()})
        rows.append(record)
    return rows


def merge_dated(old: list[Any], new: list[Any], key: str) -> list[Any]:
    records = {str(item[key]): item for item in old if isinstance(item, dict) and item.get(key) is not None}
    for item in new:
        if isinstance(item, dict) and item.get(key) is not None:
            records[str(item[key])] = item
    return [records[k] for k in sorted(records)]


def is_transient_yfinance_error(exc: Exception) -> bool:
    name = type(exc).__name__.lower()
    message = str(exc).lower()
    return any(token in name or token in message for token in (
        "ratelimit", "connection", "timeout", "jsondecode", "temporarily", "service unavailable",
        "too many requests", "429", "502", "503", "504", "remote end closed", "network is unreachable",
    ))


def call_yfinance(
    label: str,
    fn: Callable[[], Any],
    warnings: list[str],
    upgrade_state: list[bool],
) -> Any:
    """Retry transient Yahoo/network errors and refresh an outdated yfinance once per symbol."""
    retries = max(0, int(os.environ.get("YFINANCE_RETRIES", "3")))
    for attempt in range(retries + 1):
        try:
            return fn()
        except Exception as exc:
            version_text = yfinance_version()
            print(f"yfinance {version_text}; {label} failed ({type(exc).__name__}): {exc}", file=sys.stderr)
            if not upgrade_state[0] and is_yfinance_outdated():
                try:
                    refresh_yfinance_after_error(exc)
                    upgrade_state[0] = True
                except Exception as update_exc:
                    warnings.append(f"yfinance upgrade failed: {type(update_exc).__name__}: {update_exc}")
                    break
                continue
            if attempt < retries and is_transient_yfinance_error(exc):
                delay = min(60, float(os.environ.get("YFINANCE_RETRY_BASE_SECONDS", "5")) * (2 ** attempt))
                print(f"Retrying {label} in {delay:g}s ({attempt + 1}/{retries})", file=sys.stderr)
                time.sleep(delay)
                continue
            warnings.append(f"{label}: {type(exc).__name__}: {exc}")
            return None
    return None


def yfinance_version() -> str:
    try:
        return importlib.metadata.version("yfinance")
    except importlib.metadata.PackageNotFoundError:
        return str(getattr(yf, "__version__", "unknown"))


def is_yfinance_outdated() -> bool:
    try:
        parts = tuple(int(part) for part in yfinance_version().split(".")[:2])
    except ValueError:
        return True
    return parts < (0, 2)


def refresh_yfinance_after_error(exc: Exception) -> bool:
    """Upgrade yfinance once after a provider/library failure, then let caller retry."""
    if os.environ.get("YFINANCE_AUTO_UPGRADE", "1").lower() not in {"1", "true", "yes"}:
        return False
    minimum = tuple(int(part) for part in os.environ.get("YFINANCE_MIN_VERSION", "0.2").split(".")[:2])
    installed_text = yfinance_version()
    try:
        installed = tuple(int(part) for part in installed_text.split(".")[:2])
    except ValueError:
        installed = (0,)
    print(f"yfinance error: {type(exc).__name__}: {exc}; installed version={installed_text}", file=sys.stderr)
    if installed >= minimum:
        return False
    print(f"Upgrading yfinance {installed_text} to >= {'.'.join(map(str, minimum))},<2 and retrying once", file=sys.stderr)
    subprocess.run([sys.executable, "-m", "pip", "install", "--upgrade", "yfinance"], check=True)
    # Replace the module in this process so the retry uses the upgraded package.
    importlib.invalidate_caches()
    importlib.reload(yf)
    return True


def get_client():
    account_id = required_env("CLOUDFLARE_ACCOUNT_ID")
    return boto3.client(
        "s3",
        endpoint_url=os.environ.get("R2_ENDPOINT_URL", f"https://{account_id}.r2.cloudflarestorage.com"),
        aws_access_key_id=required_env("R2_ACCESS_KEY_ID"),
        aws_secret_access_key=required_env("R2_SECRET_ACCESS_KEY"),
        region_name="auto",
    )


def read_object(client: Any, bucket: str, key: str) -> bytes | None:
    try:
        return client.get_object(Bucket=bucket, Key=key)["Body"].read()
    except Exception as exc:
        code = str(getattr(exc, "response", {}).get("Error", {}).get("Code", ""))
        if code in ("NoSuchKey", "404", "NotFound"):
            return None
        raise


def put_json(client: Any, bucket: str, key: str, value: Any) -> None:
    payload = json.dumps(json_safe(value), ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
    client.put_object(Bucket=bucket, Key=key, Body=payload, ContentType="application/json; charset=utf-8")


def load_existing(client: Any, bucket: str, symbol: str) -> dict[str, Any]:
    safe = symbol.replace("^", "_").replace("=", "_").replace("/", "_")
    raw = read_object(client, bucket, f"{PREFIX}/securities/{safe}.json")
    if raw is None:
        return {"schema_version": 2, "symbol": symbol, "price_history": {}, "actions": {}, "financials": {}, "valuation_history": {}}
    data = json.loads(raw)
    if "price_history" not in data:
        # Migrate the v1 daily-close-only object written by the initial updater.
        old_prices = data.get("prices", [])
        data["price_history"] = {"1d": old_prices}
    data.setdefault("schema_version", 2)
    data.setdefault("price_history", {})
    data.setdefault("actions", {})
    data.setdefault("financials", {})
    data.setdefault("valuation_history", {})
    return data


def history_rows(ticker: Any, interval: str, period: str) -> list[dict[str, Any]]:
    frame = ticker.history(period=period, interval=interval, auto_adjust=False, actions=True, repair=False, raise_errors=True)
    if frame is None or frame.empty:
        return []
    rows: list[dict[str, Any]] = []
    for timestamp, row in frame.iterrows():
        item: dict[str, Any] = {"date": timestamp.date().isoformat()}
        for source, target in (
            ("Open", "open"), ("High", "high"), ("Low", "low"), ("Close", "close"),
            ("Adj Close", "adjusted_close"), ("Volume", "volume"),
            ("Dividends", "dividend_per_share"), ("Stock Splits", "stock_split"),
            ("Capital Gains", "capital_gain"),
        ):
            if source in row.index:
                item[target] = json_safe(row[source])
        rows.append(item)
    return rows


def collect_symbol(client: Any, bucket: str, symbol: str) -> tuple[dict[str, Any], dict[str, Any], list[str]]:
    warnings: list[str] = []
    document = load_existing(client, bucket, symbol)
    now = datetime.now(UTC).isoformat()
    initial_period = os.environ.get("YFINANCE_INITIAL_PERIOD", "max")

    upgrade_state = [False]
    for interval, incremental_period in (("1d", "5d"), ("1wk", "1mo"), ("1mo", "3mo")):
        old_rows = document["price_history"].get(interval, [])
        period = incremental_period if old_rows else initial_period
        rows = call_yfinance(
            f"prices[{interval}]",
            lambda i=interval, p=period: history_rows(yf.Ticker(symbol), i, p),
            warnings,
            upgrade_state,
        )
        if rows == []:
            warnings.append(f"prices[{interval}]: Yahoo returned no rows for {symbol}; verify symbol/listing/history availability")
        if rows:
            document["price_history"][interval] = merge_dated(old_rows, rows, "date")

    daily = document["price_history"].get("1d", [])
    new_dividends = [{"date": row["date"], "amount": row["dividend_per_share"]} for row in daily if row.get("dividend_per_share") not in (None, 0, 0.0)]
    new_splits = [{"date": row["date"], "ratio": row["stock_split"]} for row in daily if row.get("stock_split") not in (None, 0, 0.0)]
    document["actions"]["dividends"] = merge_dated(document["actions"].get("dividends", []), new_dividends, "date")
    document["actions"]["stock_splits"] = merge_dated(document["actions"].get("stock_splits", []), new_splits, "date")

    info = call_yfinance("profile_and_current_statistics", lambda: yf.Ticker(symbol).get_info(), warnings, upgrade_state)
    if isinstance(info, dict) and info:
        document["profile"] = json_safe(info)
        current_fields = (
            "currency", "exchange", "market", "quoteType", "shortName", "longName", "sector", "industry",
            "marketCap", "regularMarketPrice", "currentPrice", "regularMarketChange", "regularMarketChangePercent",
            "regularMarketTime", "fiftyTwoWeekHigh", "fiftyTwoWeekLow", "fiftyDayAverage", "twoHundredDayAverage",
            "trailingPE", "forwardPE", "priceToBook", "enterpriseValue", "trailingEps", "forwardEps", "bookValue",
            "dividendRate", "dividendYield", "payoutRatio", "fiveYearAvgDividendYield", "lastDividendValue",
            "lastDividendDate", "exDividendDate", "sharesOutstanding", "floatShares", "beta", "lastFiscalYearEnd",
            "mostRecentQuarter", "targetMeanPrice", "recommendationKey",
        )
        document["statistics_current"] = {k: json_safe(info[k]) for k in current_fields if k in info}
    elif info == {}:
        warnings.append(f"profile: Yahoo returned no company information for {symbol}")

    for frequency in ("monthly", "quarterly", "yearly"):
        periods = call_yfinance(f"valuation[{frequency}]", lambda f=frequency: yf.Ticker(symbol).get_valuation_measures(freq=f, periods=None), warnings, upgrade_state)
        if periods is not None:
            fresh = frame_periods(periods)
            old = document["valuation_history"].get(frequency, [])
            # The valuation table includes a Current column in addition to dated observations.
            current = None
            if "Current" in getattr(periods, "columns", []):
                current = {str(metric): json_safe(periods.loc[metric, "Current"]) for metric in periods.index}
            document["valuation_history"][frequency] = merge_dated(old, fresh, "period_end")
            if current:
                document.setdefault("valuation_current", {})[frequency] = current
            if not fresh and not current:
                warnings.append(f"valuation[{frequency}]: Yahoo returned no valuation history for {symbol}")

    statement_methods = ("income_statement", "balance_sheet", "cash_flow")
    for statement in statement_methods:
        for frequency in ("yearly", "quarterly", "trailing"):
            frame = call_yfinance(
                f"financials.{statement}.{frequency}",
                lambda name=statement, f=frequency: getattr(yf.Ticker(symbol), {
                    "income_statement": "get_income_stmt", "balance_sheet": "get_balance_sheet", "cash_flow": "get_cash_flow"
                }[name])(freq=f),
                warnings,
                upgrade_state,
            )
            if frame is not None:
                fresh = frame_periods(frame)
                old = document["financials"].setdefault(statement, {}).get(frequency, [])
                document["financials"][statement][frequency] = merge_dated(old, fresh, "period_end")
                if not fresh:
                    warnings.append(f"financials.{statement}.{frequency}: Yahoo returned no rows for {symbol}")

    earnings = call_yfinance("earnings_history", lambda: yf.Ticker(symbol).get_earnings_history(), warnings, upgrade_state)
    if earnings is not None:
        document["earnings_history"] = merge_dated(
            document.get("earnings_history", []), frame_records(earnings), "date"
        )
    shares = call_yfinance("share_count_history", lambda: yf.Ticker(symbol).get_shares_full(), warnings, upgrade_state)
    if shares is not None:
        # shares_full returns a Series indexed by date.
        try:
            share_rows = [{"date": json_safe(idx), "shares": json_safe(val)} for idx, val in shares.items()]
            document["share_count_history"] = merge_dated(document.get("share_count_history", []), share_rows, "date")
        except Exception as exc:
            warnings.append(f"share_count_history serialization: {type(exc).__name__}: {exc}")

    document["source"] = "Yahoo Finance via yfinance"
    document["updated_at"] = now
    summary = {
        "symbol": symbol,
        "updated_at": now,
        **document.get("statistics_current", {}),
        "latest_daily_price": daily[-1] if daily else None,
        "latest_dividend": document["actions"].get("dividends", [])[-1] if document["actions"].get("dividends") else None,
        "attribution": "Yahoo Finance",
    }
    return document, summary, warnings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aggregate", action="store_true", help="Merge shard snapshots into latest_market_data.json")
    args = parser.parse_args()

    bucket = required_env("R2_BUCKET")
    client = get_client()
    shard_count = int(os.environ.get("YFINANCE_SHARD_COUNT", "1"))
    if args.aggregate:
        summaries = []
        for shard in range(shard_count):
            key = f"{PREFIX}/latest/shards/{shard:02d}.json"
            raw = read_object(client, bucket, key)
            if raw is None:
                raise RuntimeError(f"Required snapshot shard is missing: {key}")
            summaries.extend(json.loads(raw).get("securities", []))
        put_json(client, bucket, f"{PREFIX}/latest_market_data.json", {
            "schema_version": 1,
            "source": "Yahoo Finance via yfinance",
            "generated_at": datetime.now(UTC).isoformat(),
            "security_count": len(summaries),
            "securities": sorted(summaries, key=lambda item: item["symbol"]),
        })
        print(f"Published latest_market_data.json with {len(summaries)} securities.")
        return 0

    symbols = configured_symbols()
    shard_index = int(os.environ.get("YFINANCE_SHARD_INDEX", "0"))
    if shard_count < 1 or not 0 <= shard_index < shard_count:
        raise RuntimeError("YFINANCE_SHARD_INDEX must be between 0 and YFINANCE_SHARD_COUNT - 1")
    selected = symbols[shard_index::shard_count]
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    summaries: list[dict[str, Any]] = []
    failed: list[str] = []
    warning_count = 0

    for position, symbol in enumerate(selected, start=1):
        try:
            document, summary, warnings = collect_symbol(client, bucket, symbol)
            safe = symbol.replace("^", "_").replace("=", "_").replace("/", "_")
            put_json(client, bucket, f"{PREFIX}/securities/{safe}.json", document)
            summaries.append(summary)
            if warnings:
                warning_count += len(warnings)
                print(f"WARNING {symbol}: {len(warnings)} optional data groups unavailable", file=sys.stderr)
        except Exception as exc:
            failed.append(f"{symbol}: {type(exc).__name__}: {exc}")
            print(f"ERROR {failed[-1]}", file=sys.stderr)
        if position % 10 == 0:
            print(f"shard {shard_index + 1}/{shard_count}: {position}/{len(selected)} processed")
        time.sleep(float(os.environ.get("YFINANCE_REQUEST_PAUSE_SECONDS", "1")))

    put_json(client, bucket, f"{PREFIX}/latest/shards/{shard_index:02d}.json", {
        "schema_version": 1,
        "shard": shard_index,
        "generated_at": datetime.now(UTC).isoformat(),
        "securities": summaries,
        "failed_count": len(failed),
    })
    print(f"Shard {shard_index + 1}/{shard_count} saved: {len(summaries)}/{len(selected)} securities; optional-data warnings={warning_count}; errors={len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
