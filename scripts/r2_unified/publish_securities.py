#!/usr/bin/env python3
"""Publish compact, source-attributed dividend bundles per listed security."""

from __future__ import annotations

import json
import os
import re
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Any

import boto3


BUCKET = os.environ.get("R2_BUCKET", "diview-market-data")
IRBANK_ROOT = "market/irbank/v1/"
IRBANK_DIVIDENDS = IRBANK_ROOT + "securities/"
YFINANCE_DIVIDENDS = "market/yfinance/dividends/"
OUTPUT_PREFIX = "market/securities/v1/"
WORKERS = max(1, min(32, int(os.environ.get("R2_PUBLISH_WORKERS", "16"))))


def client_from_env() -> Any:
    account = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "8d8f4f1f3c53232aaf7c7d6dd7b33944")
    access = os.environ.get("R2_ACCESS_KEY_ID", "").strip()
    secret = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip()
    if not access or not secret:
        raise RuntimeError("R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY are required")
    return boto3.client(
        "s3", endpoint_url=f"https://{account}.r2.cloudflarestorage.com",
        aws_access_key_id=access, aws_secret_access_key=secret, region_name="auto",
    )


def list_keys(client: Any, prefix: str) -> list[str]:
    paginator = client.get_paginator("list_objects_v2")
    return [item["Key"] for page in paginator.paginate(Bucket=BUCKET, Prefix=prefix)
            for item in page.get("Contents", [])]


def read_json(client: Any, key: str) -> Any:
    response = client.get_object(Bucket=BUCKET, Key=key)
    return json.loads(response["Body"].read())


def read_optional_json(client: Any, key: str) -> Any | None:
    try:
        return read_json(client, key)
    except Exception as exc:
        code = str(getattr(exc, "response", {}).get("Error", {}).get("Code", ""))
        if code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise


def code_from_yahoo_key(key: str) -> str | None:
    name = key[len(YFINANCE_DIVIDENDS):]
    if not name.endswith(".json"):
        return None
    symbol = name[:-5]
    if not symbol.endswith(".T"):
        return None
    code = symbol[:-2].upper()
    if len(code) == 5 and code.endswith("0"):
        code = code[:-1]
    return code if re.fullmatch(r"[A-Z0-9]{4,5}", code) else None


def publish(client: Any) -> tuple[int, int, int, int, int, int, int, int]:
    catalog = read_json(client, IRBANK_ROOT + "catalog.json")
    securities = catalog.get("securities", [])
    irbank: dict[str, dict[str, Any]] = {}
    for row in securities:
        code = str(row.get("code") or "").upper()
        if re.fullmatch(r"[A-Z0-9]{4}", code):
            irbank[code] = {
                "r2_key": f"{IRBANK_DIVIDENDS}{code}/overview.json",
                "attribution": "IRBANK Connect",
            }
    compact_keys = set(list_keys(client, IRBANK_DIVIDENDS))

    def load_irbank_dividends(item: tuple[str, dict[str, Any]]) -> tuple[str, dict[str, Any]]:
        code, source = item
        key = f"{IRBANK_DIVIDENDS}{code}/dividends.json"
        if key in compact_keys:
            compact = read_json(client, key)
        else:
            # One-time migration from the existing overview bundle; later runs read only compact dividend objects.
            overview = read_json(client, source["r2_key"])
            compact = {
                "schema_version": 1,
                "source": "IRBANK Connect",
                "security_code": code,
                "records": (overview.get("dividends") or {}).get("records", []),
                "attribution": (overview.get("sources") or {}).get("dividends"),
            }
            client.put_object(
                Bucket=BUCKET, Key=key,
                Body=json.dumps(compact, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
                ContentType="application/json; charset=utf-8", CacheControl="public, max-age=300",
            )
        source.update({"dividend_r2_key": key, "updated_at": compact.get("updated_at"),
                       "attribution": compact.get("attribution") or compact.get("source") or "IRBANK Connect",
                       "records": compact.get("records", [])})
        return code, source

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        irbank = dict(pool.map(load_irbank_dividends, irbank.items()))

    yahoo: dict[str, dict[str, Any]] = {}
    def load_yahoo_compact(key: str) -> tuple[str, dict[str, Any]] | None:
        code = code_from_yahoo_key(key)
        if not code:
            return None
        if code in irbank and irbank[code].get("records"):
            return None
        compact = read_json(client, key)
        return code, {
            "r2_key": key,
            "updated_at": compact.get("updated_at"),
            "attribution": compact.get("source", "Yahoo Finance via yfinance"),
            "records": compact.get("records", []),
        }

    yahoo_keys = list_keys(client, YFINANCE_DIVIDENDS)
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        yahoo = dict(pair for pair in pool.map(load_yahoo_compact, yahoo_keys) if pair is not None)

    # Reuse already stored Yahoo history for IRBANK gaps; only download the larger object for missing dividend histories.
    legacy_keys = set(list_keys(client, "market/yfinance/securities/"))
    gap_codes = [code for code, source in irbank.items() if not source.get("records") and code not in yahoo]

    def migrate_yahoo_history(code: str) -> tuple[str, dict[str, Any]] | None:
        symbol = f"{code}.T"
        source_key = f"market/yfinance/securities/{symbol}.json"
        if source_key not in legacy_keys:
            return None
        previous = read_json(client, source_key)
        if not previous:
            return None
        records = (previous.get("actions") or {}).get("dividends", [])
        compact_key = f"{YFINANCE_DIVIDENDS}{symbol}.json"
        compact = {
            "schema_version": 1,
            "source": previous.get("source", "Yahoo Finance via yfinance"),
            "symbol": symbol,
            "updated_at": previous.get("updated_at"),
            "records": records,
        }
        client.put_object(
            Bucket=BUCKET, Key=compact_key,
            Body=json.dumps(compact, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
            ContentType="application/json; charset=utf-8", CacheControl="public, max-age=300",
        )
        return code, {
            "r2_key": compact_key,
            "updated_at": compact.get("updated_at"),
            "attribution": compact["source"],
            "records": records,
        }

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        yahoo.update(pair for pair in pool.map(migrate_yahoo_history, gap_codes) if pair is not None)

    timestamp = datetime.now(timezone.utc).isoformat()
    codes = sorted(irbank.keys() | yahoo.keys())
    existing_output_keys = set(list_keys(client, OUTPUT_PREFIX))

    def publish_one(code: str) -> bool:
        sources: dict[str, Any] = {}
        dividend_history: dict[str, Any] = {}
        if code in irbank:
            source = irbank[code]
            sources["irbank"] = {key: source[key] for key in ("r2_key", "dividend_r2_key", "updated_at", "attribution") if key in source}
            dividend_history["irbank"] = source.get("records", [])
        if code in yahoo:
            source = yahoo[code]
            sources["yfinance"] = {key: source[key] for key in ("r2_key", "updated_at", "attribution") if key in source}
            dividend_history["yfinance"] = source["records"]
        output = {
            "schema_version": 1,
            "security": {"code": code},
            "updated_at": timestamp,
            "sources": sources,
            "dividend_history_by_source": dividend_history,
        }
        key = f"{OUTPUT_PREFIX}{code}.json"
        if key in existing_output_keys:
            previous = read_json(client, key)
            if {k: v for k, v in previous.items() if k != "updated_at"} == {
                k: v for k, v in output.items() if k != "updated_at"
            }:
                return False
            output["updated_at"] = timestamp
        payload = json.dumps(output, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        client.put_object(
            Bucket=BUCKET, Key=key, Body=payload,
            ContentType="application/json; charset=utf-8", CacheControl="public, max-age=300",
        )
        return True

    written = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for index, changed in enumerate(pool.map(publish_one, codes), start=1):
            written += int(changed)
            if index % 200 == 0 or index == len(codes):
                print(f"Published {index}/{len(codes)} security bundles", flush=True)
    irbank_records = sum(len(source.get("records", [])) for source in irbank.values())
    yahoo_records = sum(len(source.get("records", [])) for source in yahoo.values())
    irbank_with_records = sum(bool(source.get("records")) for source in irbank.values())
    yahoo_with_records = sum(bool(source.get("records")) for source in yahoo.values())
    uncovered = sum(not irbank.get(code, {}).get("records") and not yahoo.get(code, {}).get("records") for code in codes)
    return (len(codes), written, len(irbank.keys() & yahoo.keys()), irbank_with_records,
            yahoo_with_records, uncovered, irbank_records, yahoo_records)


def main() -> int:
    count, written, overlap, irbank_count, yahoo_count, uncovered, irbank_events, yahoo_events = publish(client_from_env())
    print(json.dumps({"bucket": BUCKET, "prefix": OUTPUT_PREFIX, "security_count": count,
                      "written": written, "irbank_yfinance_overlap": overlap,
                      "irbank_securities_with_dividends": irbank_count,
                      "irbank_dividend_records": irbank_events,
                      "yfinance_securities_with_dividends": yahoo_count,
                      "yfinance_dividend_records": yahoo_events,
                      "securities_without_dividend_records": uncovered}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
