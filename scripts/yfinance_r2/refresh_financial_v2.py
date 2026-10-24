"""Refresh annual yfinance financial records for one security in R2 v2."""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import re
import sys
import uuid
from pathlib import Path
from typing import Any

import yfinance as yf

import fetch_and_upload as collector
import sync_v2 as sync


def normalize_security_code(code: str) -> str:
    """Use the same display-code contract as the normal universe sync."""
    return sync.market_symbol(code).removesuffix(".T")


def refresh(code: str) -> dict[str, Any]:
    code = normalize_security_code(code)
    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    bucket = collector.required_env("R2_BUCKET")
    client = collector.get_client()
    # Publish a new change token even if a later write/read-back fails: some
    # annual rows may already have changed and must invalidate D1 analysis.
    try:
        return refresh_with_client(code, client, bucket)
    finally:
        sync.put_json_if_changed(client, bucket, sync.object_key("financial_revision.json"), {
            "schema_version": 1,
            "revision": uuid.uuid4().hex,
            "updated_at": sync.iso_now(),
        })


def refresh_with_client(code: str, client: Any, bucket: str) -> dict[str, Any]:
    symbol = sync.market_symbol(code)
    as_of = sync.latest_completed_xtks_session()
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
            "net_de_ratio", "net_de_formula", "field_status_reason",
        ):
            if annual.get(field) != row.get(field):
                raise RuntimeError(f"Financial field read-back mismatch: {symbol} {period_end} {field}")
        for metric in ("calculated_doe", "net_de_ratio"):
            if annual.get("field_status", {}).get(metric) != row.get("field_status", {}).get(metric):
                raise RuntimeError(f"Financial field status read-back mismatch: {symbol} {period_end} {metric}")
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
            "income_advice_metrics": sync.income_advice_metrics(
                current, statements, current.get("income_advice_metrics"),
            ),
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
        "missing_reasons": {
            row.get("period_end"): row.get("field_status_reason", {})
            for row in statements
            if row.get("field_status_reason")
        },
    }, ensure_ascii=False, sort_keys=True))
    return {"exit_code": 1 if failed_groups else 0, "code": code, "financial_rows_processed": wrote}


def partition_securities(securities: list[str], shard_index: int, shard_count: int) -> list[str]:
    if shard_count < 1:
        raise ValueError("Shard count must be at least 1")
    if shard_index < 0 or shard_index >= shard_count:
        raise ValueError("Shard index must be between 0 and shard_count - 1")
    return [code for index, code in enumerate(securities) if index % shard_count == shard_index]


def refresh_all(report_path: str, *, shard_index: int = 0, shard_count: int = 1) -> int:
    collector.configure_yfinance()
    client = collector.get_client()
    bucket = collector.required_env("R2_BUCKET")
    universe = sync.read_json(client, bucket, sync.object_key("universe", "current.json")) or {}
    securities = [
        str(row.get("code") or row.get("security_code_display") or "").strip()
        for row in universe.get("securities", [])
        if isinstance(row, dict) and row.get("status", "active") == "active"
    ]
    # Reject malformed active rows instead of silently excluding them from a
    # run reported as a successful full-universe refresh.
    securities = sorted({normalize_security_code(code) for code in securities})
    if not securities:
        raise RuntimeError("No active securities found in the published V2 universe")
    universe_security_count = len(securities)
    securities = partition_securities(securities, shard_index, shard_count)
    if not securities:
        raise RuntimeError(f"No active securities assigned to shard {shard_index}/{shard_count}")

    results: list[dict[str, Any]] = []
    failed = 0
    for index, code in enumerate(securities, start=1):
        output = io.StringIO()
        errors = io.StringIO()
        try:
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                result = refresh(code)
            payload = next(
                (json.loads(line) for line in reversed(output.getvalue().splitlines())
                 if line.lstrip().startswith("{")),
                {},
            )
            record = {
                "code": code,
                "status": "partial" if result["exit_code"] else "success",
                "financial_rows_processed": result["financial_rows_processed"],
                "doe_rows_available": len(payload.get("doe_rows", [])),
                "net_de_rows_available": len(payload.get("net_de_rows", [])),
                "missing_reasons": payload.get("missing_reasons", {}),
                "financial_status": payload.get("financial_status", {}),
            }
            failed += int(result["exit_code"] != 0)
        except Exception as exc:
            record = {
                "code": code,
                "status": "failed",
                "error": f"{type(exc).__name__}: {exc}"[:500],
                "diagnostics": errors.getvalue()[-2000:],
            }
            failed += 1
        results.append(record)
        if index % 25 == 0 or index == len(securities):
            print(
                f"Progress shard={shard_index}/{shard_count} "
                f"{index}/{len(securities)} (universe={universe_security_count}): "
                f"failed_or_partial={failed}",
                flush=True,
            )

    report = {
        "security_count": len(securities),
        "universe_security_count": universe_security_count,
        "shard_index": shard_index,
        "shard_count": shard_count,
        "success_count": sum(row["status"] == "success" for row in results),
        "partial_count": sum(row["status"] == "partial" for row in results),
        "failure_count": sum(row["status"] == "failed" for row in results),
        "results": results,
    }
    Path(report_path).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "results"}, ensure_ascii=False))
    return 1 if failed else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--code", help="One security code to refresh")
    target.add_argument("--all", action="store_true", help="Refresh every active security in the R2 V2 universe")
    parser.add_argument("--report", default="financial-refresh-report.json", help="JSON report output path for --all")
    parser.add_argument("--shard-index", type=int, default=0, help="Zero-based shard index for --all")
    parser.add_argument("--shard-count", type=int, default=1, help="Number of disjoint shards for --all")
    parser.add_argument("--defer-advice", action="store_true",
                        help="A coordinating job must rebuild income advice after all shards finish")
    parser.add_argument("--advice-workers", type=int, default=8,
                        help="Parallel R2 readers for derived catalog regeneration (not Yahoo requests)")
    args = parser.parse_args()
    if not args.all and (args.shard_index != 0 or args.shard_count != 1):
        parser.error("--shard-index and --shard-count can only be used with --all")
    if args.shard_count > 1 and not args.defer_advice:
        parser.error("Sharded refresh requires --defer-advice and one final income-advice publication")
    try:
        if args.all:
            return refresh_all(args.report, shard_index=args.shard_index, shard_count=args.shard_count)
        return refresh(args.code)["exit_code"]
    finally:
        if not args.defer_advice:
            # Also repair derived data after a handled partial financial write.
            import generate_income_advice
            generate_income_advice.publish(collector.get_client(), collector.required_env("R2_BUCKET"),
                                           workers=max(1, args.advice_workers))


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
