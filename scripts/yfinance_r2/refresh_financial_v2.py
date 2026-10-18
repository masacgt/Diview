"""Refresh annual yfinance financial records for one security in R2 v2."""

from __future__ import annotations

import argparse
import json
import re
import sys
from typing import Any

import yfinance as yf

import fetch_and_upload as collector
import sync_v2 as sync


def refresh(code: str) -> int:
    code = code.strip().upper()
    if not re.fullmatch(r"\d{4,5}[A-Z]?", code):
        raise ValueError("Security code must be a 4-5 digit code, optionally followed by a letter")

    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    symbol = sync.market_symbol(code)
    as_of = sync.latest_completed_xtks_session()
    bucket = collector.required_env("R2_BUCKET")
    client = collector.get_client()
    safe = sync.safe_symbol(symbol)
    ticker = yf.Ticker(symbol)

    sync_key = sync.object_key("securities", safe, "sync.json")
    current_key = sync.object_key("securities", safe, "current.json")
    prior_sync = sync.read_json(client, bucket, sync_key) or {}
    current = sync.read_json(client, bucket, current_key) or {}
    prior_field_status = current.get("field_status", {})
    if not isinstance(prior_field_status, dict):
        prior_field_status = {}
    old = sync.legacy_document(client, bucket, symbol)

    info: dict[str, Any] = {}
    try:
        sync.count_yfinance_call("ticker_get_info")
        info = ticker.get_info() or {}
    except Exception as exc:
        print(f"Profile markers unavailable for {symbol}: {type(exc).__name__}: {exc}", file=sys.stderr)

    statements, valuation, financial_status, document_keys = sync.collect_financials(
        ticker,
        old,
        client,
        bucket,
        symbol,
        fetch_groups=set(sync.FINANCIAL_GROUPS),
        previous_status=prior_field_status,
    )
    wrote = 0
    for row in statements:
        period_end = str(row.get("period_end") or "")[:10]
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", period_end):
            continue
        key = document_keys.get(period_end) or sync.object_key(
            "securities", safe, "financials", f"{period_end}.json",
        )
        sync.put_json_if_changed(client, bucket, key, {
            "schema_version": 1,
            "symbol": symbol,
            "year": period_end[:4],
            "annual": row,
            "valuation": next((item for item in valuation if item.get("period_end") == period_end), None),
            "retrieval_status": financial_status,
            "updated_at": sync.iso_now(),
            "source": "Yahoo Finance via yfinance",
        })
        wrote += 1

    expected = {str(row.get("period_end")): row for row in statements if row.get("period_end")}
    verified = 0
    for period_end, row in expected.items():
        key = document_keys.get(period_end) or sync.object_key(
            "securities", safe, "financials", f"{period_end}.json",
        )
        saved = sync.read_json(client, bucket, key) or {}
        annual = saved.get("annual") if isinstance(saved, dict) else None
        if not isinstance(annual, dict):
            raise RuntimeError(f"Financial record read-back failed: {symbol} {period_end}")
        for field in (
            "doe_dividend_total", "stockholders_equity", "calculated_doe",
            "net_debt", "net_debt_source_field", "net_de_equity_source_field",
            "net_de_ratio", "net_de_formula",
        ):
            if annual.get(field) != row.get(field):
                raise RuntimeError(f"Financial field read-back mismatch: {symbol} {period_end} {field}")
        if (annual.get("field_status", {}).get("net_de_ratio")
                != row.get("field_status", {}).get("net_de_ratio")):
            raise RuntimeError(f"Financial field status read-back mismatch: {symbol} {period_end} net_de_ratio")
        verified += 1

    marker = sync.financial_period_marker(info)
    failed_groups = [group for group in sync.FINANCIAL_GROUPS if financial_status.get(group) == "failed"]
    updated_sync = {
        **prior_sync,
        "schema_version": 1,
        "code": code,
        "symbol": symbol,
        "financials_initialized": bool(prior_sync.get("financials_initialized")) or not failed_groups,
        "financial_refresh_policy_version": sync.FINANCIAL_REFRESH_POLICY_VERSION,
        "financial_period_marker": marker or prior_sync.get("financial_period_marker"),
        "financials_last_checked_session_date": as_of.isoformat(),
        "financial_retry_pending": bool(failed_groups),
        "financial_targeted_refresh_at": sync.iso_now(),
        "financial_targeted_refresh_status": "partial" if failed_groups else "success",
        "financial_targeted_refresh_fields": financial_status,
    }
    sync.put_json_if_changed(client, bucket, sync_key, updated_sync)

    if current:
        current_fields = dict(prior_field_status)
        current_fields["financial_fields"] = financial_status.get("fields", {})
        for group in sync.FINANCIAL_GROUPS:
            current_fields[f"financial_{group}"] = financial_status.get(group, "empty")
        sync.put_json_if_changed(client, bucket, current_key, {
            **current,
            "field_status": current_fields,
            "updated_at": sync.iso_now(),
        })

    doe_rows = [row for row in statements if row.get("calculated_doe") is not None]
    net_de_rows = [row for row in statements if row.get("net_de_ratio") is not None]
    print(json.dumps({
        "code": code,
        "symbol": symbol,
        "session_date": as_of.isoformat(),
        "financial_rows_written": wrote,
        "financial_rows_verified": verified,
        "doe_rows": [{
            "period_end": row.get("period_end"),
            "doe_dividend_total": row.get("doe_dividend_total"),
            "doe_dividend_source_field": row.get("doe_dividend_source_field"),
            "stockholders_equity": row.get("stockholders_equity"),
            "calculated_doe": row.get("calculated_doe"),
            "field_status": row.get("field_status", {}).get("calculated_doe"),
        } for row in doe_rows],
        "net_de_rows": [{
            "period_end": row.get("period_end"),
            "net_debt": row.get("net_debt"),
            "net_debt_source_field": row.get("net_debt_source_field"),
            "net_de_equity_source_field": row.get("net_de_equity_source_field"),
            "net_de_ratio": row.get("net_de_ratio"),
            "field_status": row.get("field_status", {}).get("net_de_ratio"),
        } for row in net_de_rows],
        "financial_status": financial_status,
    }, ensure_ascii=False, sort_keys=True))
    return 1 if failed_groups else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--code", required=True, help="One security code to refresh")
    args = parser.parse_args()
    return refresh(args.code)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
