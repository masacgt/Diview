"""Fetch configured Yahoo Finance price history and append it to Cloudflare R2."""

from __future__ import annotations

import json
import os
import sys
import csv
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any

import boto3
import yfinance as yf


UTC = timezone.utc
DATA_DIR = Path(os.environ.get("PRICE_DATA_DIR", "data/market/yfinance"))


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


def symbols_from_environment() -> list[str]:
    symbols = [s.strip().upper() for s in os.environ.get("YFINANCE_SYMBOLS", "").split(",") if s.strip()]
    if not symbols:
        raise RuntimeError("Set YFINANCE_SYMBOLS to a comma-separated list, e.g. 7203.T,6758.T")
    if len(set(symbols)) != len(symbols):
        raise RuntimeError("YFINANCE_SYMBOLS contains duplicate symbols")
    return symbols


def configured_symbols() -> list[str]:
    """Use an explicit list, or derive Japan-listed Yahoo symbols from the provided master CSV."""
    raw = os.environ.get("YFINANCE_SYMBOLS", "").strip()
    if raw:
        return symbols_from_environment()

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
            # Yahoo Finance's Japanese listing symbols are exchange codes suffixed with .T.
            if code and len(code) in (4, 5) and code.isalnum():
                symbols.add(f"{code}.T")
    result = sorted(symbols)
    if not result:
        raise RuntimeError(f"No Yahoo-compatible Japanese listing codes found in {universe_csv}")
    return result


def json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(k): json_safe(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_safe(v) for v in value]
    if hasattr(value, "item"):
        return json_safe(value.item())
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, float) and (value != value or value in (float("inf"), float("-inf"))):
        return None
    return value


def read_existing(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"schema_version": 1, "source": "Yahoo Finance via yfinance", "prices": []}
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict) or not isinstance(data.get("prices"), list):
        raise RuntimeError(f"Existing history has an unexpected format: {path}")
    return data


def main() -> int:
    symbols = configured_symbols()
    bucket = required_env("R2_BUCKET")
    account_id = required_env("CLOUDFLARE_ACCOUNT_ID")
    access_key = required_env("R2_ACCESS_KEY_ID")
    secret_key = required_env("R2_SECRET_ACCESS_KEY")
    endpoint = os.environ.get("R2_ENDPOINT_URL", f"https://{account_id}.r2.cloudflarestorage.com")
    prefix = os.environ.get("R2_PREFIX", "market/yfinance").strip("/")

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    client = boto3.client(
        "s3",
        endpoint_url=endpoint,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name="auto",
    )
    latest: dict[str, Any] = {
        "schema_version": 1,
        "source": "Yahoo Finance via yfinance",
        "generated_at": datetime.now(UTC).isoformat(),
        "prices": [],
    }
    changed: list[tuple[Path, str, dict[str, Any]]] = []
    errors: list[str] = []

    for symbol in symbols:
        try:
            file_path = DATA_DIR / f"{symbol.replace('^', '_').replace('=', '_')}.json"
            key = f"{prefix}/history/{file_path.name}" if prefix else f"history/{file_path.name}"
            if not file_path.exists():
                try:
                    response = client.get_object(Bucket=bucket, Key=key)
                    file_path.write_bytes(response["Body"].read())
                except Exception as exc:
                    # A missing key may be reported as a generic S3 404 by some clients.
                    code = str(getattr(exc, "response", {}).get("Error", {}).get("Code", ""))
                    if code not in ("NoSuchKey", "404", "NotFound"):
                        raise
            existing = read_existing(file_path)

            initial_period = os.environ.get("YFINANCE_INITIAL_PERIOD", "1y")
            fetch_period = "5d" if existing["prices"] else initial_period
            history = yf.Ticker(symbol).history(period=fetch_period, interval="1d", auto_adjust=False, actions=False)
            if history.empty:
                raise RuntimeError("Yahoo Finance returned no daily prices")

            rows: list[dict[str, Any]] = []
            for timestamp, row in history.iterrows():
                day = timestamp.date().isoformat()
                entry = {"date": day}
                for source, target in (("Open", "open"), ("High", "high"), ("Low", "low"), ("Close", "close"), ("Adj Close", "adjusted_close"), ("Volume", "volume")):
                    if source in row.index:
                        entry[target] = json_safe(row[source])
                rows.append(entry)

            by_day = {str(item["date"]): item for item in existing["prices"] if isinstance(item, dict) and item.get("date")}
            for entry in rows:
                by_day[entry["date"]] = entry
            document = {
                "schema_version": 1,
                "symbol": symbol,
                "source": "Yahoo Finance via yfinance",
                "updated_at": datetime.now(UTC).isoformat(),
                "prices": [by_day[k] for k in sorted(by_day)],
            }
            latest["prices"].append({"symbol": symbol, **rows[-1]})
            changed.append((file_path, key, document))
        except Exception as exc:  # report symbol failures, but upload successful symbols
            errors.append(f"{symbol}: {type(exc).__name__}: {exc}")

    if not changed:
        raise RuntimeError("No symbols were fetched successfully. " + "; ".join(errors))

    for file_path, key, document in changed:
        payload = json.dumps(document, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
        client.put_object(Bucket=bucket, Key=key, Body=payload, ContentType="application/json; charset=utf-8")
        file_path.write_bytes(payload)

    latest_key = f"{prefix}/latest_prices.json" if prefix else "latest_prices.json"
    latest_payload = json.dumps(latest, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
    client.put_object(Bucket=bucket, Key=latest_key, Body=latest_payload, ContentType="application/json; charset=utf-8")
    (DATA_DIR / "latest_prices.json").write_bytes(latest_payload)

    print(f"Uploaded price history for {len(changed)} of {len(symbols)} universe symbols to R2 bucket {bucket!r}.")
    print(f"Latest snapshot key: {latest_key}")
    for error in errors:
        print(f"WARNING: {error}", file=sys.stderr)
    if errors:
        return 1
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
