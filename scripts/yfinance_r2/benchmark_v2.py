"""Measure a deterministic J-Quants sample without writing to R2."""

from __future__ import annotations

import json
import hashlib
import os
import sys
import time
from collections import Counter
from datetime import datetime

import yfinance as yf

import sync_v2 as sync
import fetch_and_upload as collector
from fetch_jquants_universe import fetch_securities


def sample_universe(rows: list[dict[str, str]], limit: int) -> list[dict[str, str]]:
    groups: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        groups.setdefault(row.get("security_type", "listed_security"), []).append(row)
    for values in groups.values():
        values.sort(key=lambda item: item["security_code_display"])
    selected: list[dict[str, str]] = []
    while len(selected) < limit and any(groups.values()):
        for kind in sorted(groups):
            if groups[kind] and len(selected) < limit:
                values = groups[kind]
                index = min(len(values) - 1, (len(selected) * len(values)) // max(1, limit))
                selected.append(values.pop(index))
    return selected


def attempt(name: str, call, failures: Counter[str]) -> None:
    try:
        call()
    except Exception as exc:
        failures[type(exc).__name__] += 1
        print(f"{name} failed: {type(exc).__name__}: {str(exc)[:240]}", file=sys.stderr, flush=True)


def measure_action_method(symbol: str, method: str, session_date) -> dict[str, object]:
    """Compare repaired price-history actions with yfinance's actions-only result accessor."""
    ticker = yf.Ticker(symbol)
    session = ticker._data._session
    original_get = session.get
    response_bytes = 0

    def counted_get(*args, **kwargs):
        nonlocal response_bytes
        response = original_get(*args, **kwargs)
        response_bytes += len(getattr(response, "content", b"") or b"")
        return response

    started = time.perf_counter()
    session.get = counted_get
    try:
        if method == "repaired_history":
            dividends, splits, _, _ = sync.fetch_repaired_action_history(ticker, session_date, period="1y")
        else:
            actions = ticker.get_actions(period="1y")
            dividends, splits = [], []
            if actions is not None and not getattr(actions, "empty", True):
                if hasattr(actions, "iterrows"):
                    for timestamp, row in actions.iterrows():
                        event_date = sync.date_value(timestamp)
                        if event_date is None or event_date > session_date:
                            continue
                        amount = sync.finite_number(row.get("Dividends"))
                        ratio = sync.finite_number(row.get("Stock Splits"))
                        if amount and amount > 0:
                            dividends.append({"date": event_date.isoformat(), "amount": amount})
                        if ratio and ratio > 0:
                            splits.append({"date": event_date.isoformat(), "ratio": ratio})
        return {
            "method": method,
            "elapsed_seconds": round(time.perf_counter() - started, 3),
            "response_body_bytes": response_bytes,
            "dividend_event_count": len(dividends),
            "split_event_count": len(splits),
            "event_fingerprint": hashlib.sha256(json.dumps(
                {"dividends": dividends, "splits": splits}, sort_keys=True, separators=(",", ":")
            ).encode("utf-8")).hexdigest(),
        }
    finally:
        session.get = original_get


def compare_action_methods(sample: list[dict[str, str]], session_date) -> dict[str, object]:
    results = []
    for entry in sample[: min(6, len(sample))]:
        symbol = sync.market_symbol(entry["security_code_display"])
        for method in ("repaired_history", "actions_accessor"):
            try:
                result = measure_action_method(symbol, method, session_date)
                result["symbol"] = symbol
                results.append(result)
            except Exception as exc:
                results.append({
                    "symbol": symbol,
                    "method": method,
                    "error": f"{type(exc).__name__}: {str(exc)[:240]}",
                })
    return {
        "sample_count": min(6, len(sample)),
        "results": results,
        "note": "response_body_bytes counts decoded HTTP response bodies, not TLS/compression wire bytes; comparison is non-publishing and does not replace repair=True collection.",
    }


def main() -> int:
    api_key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("JQUANTS_API_KEY is required")
    limit = min(200, max(1, int(os.environ.get("YFINANCE_BENCHMARK_SAMPLE_SIZE", "40"))))
    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    universe = fetch_securities(api_key)
    sample = sample_universe(universe, limit)
    session_date = datetime.now(sync.JST).date()
    failures: Counter[str] = Counter()
    started = time.perf_counter()

    for index, entry in enumerate(sample, start=1):
        code = entry["security_code_display"]
        symbol = sync.market_symbol(code)
        ticker = yf.Ticker(symbol)
        is_long_history = sync.bucket_for(code, sync.FULL_HISTORY_BUCKETS) == 0
        is_financial = sync.bucket_for(code, sync.FINANCIAL_BUCKETS) == 0
        print(
            f"[{index}/{len(sample)}] {code} ({entry.get('security_type', 'listed_security')}): "
            f"long_history={is_long_history}, financials={is_financial}",
            flush=True,
        )
        sync.count_yfinance_call("ticker_get_info")
        attempt("ticker_get_info", ticker.get_info, failures)
        sync.count_yfinance_call("history_latest_daily_5d")
        attempt("history_latest_daily_5d", lambda: collector.history_rows(ticker, "1d", "5d"), failures)

        def fetch_recent_or_full() -> bool:
            if is_long_history:
                sync.fetch_repaired_action_history(ticker, session_date, period="max")
                return False
            _, splits, _, _ = sync.fetch_repaired_action_history(ticker, session_date, period="1y")
            if splits:
                sync.fetch_repaired_action_history(ticker, session_date, period="max")
                return True
            return False

        split_refresh = False
        try:
            split_refresh = fetch_recent_or_full()
        except Exception as exc:
            failures[type(exc).__name__] += 1
            print(f"history_actions failed: {type(exc).__name__}: {str(exc)[:240]}", file=sys.stderr, flush=True)
        if is_long_history or split_refresh:
            label = "history_year_end_prices_split_refresh_8y" if split_refresh else "history_year_end_prices_initial_8y"
            attempt(label, lambda: sync.monthly_history_rows(ticker, initial=True, as_of=session_date), failures)
        if is_financial:
            for name, request in (
                ("income_statement_yearly", lambda: ticker.get_income_stmt(freq="yearly")),
                ("balance_sheet_yearly", lambda: ticker.get_balance_sheet(freq="yearly")),
                ("cash_flow_yearly", lambda: ticker.get_cash_flow(freq="yearly")),
                ("valuation_measures_yearly", lambda: ticker.get_valuation_measures(freq="yearly", periods=10)),
            ):
                sync.count_yfinance_call(name)
                attempt(name, request, failures)

    elapsed = round(time.perf_counter() - started, 3)
    calls = dict(sorted(sync._YF_CALLS.items()))
    action_comparison = compare_action_methods(sample, session_date)
    report = {
        "universe_count": len(universe),
        "sample_count": len(sample),
        "sample_types": dict(sorted(Counter(row.get("security_type", "listed_security") for row in sample).items())),
        "session_date": session_date.isoformat(),
        "elapsed_seconds": elapsed,
        "yfinance_call_count": sum(calls.values()),
        "yfinance_calls": calls,
        "failed_requests": dict(sorted(failures.items())),
        "action_method_comparison": action_comparison,
        "r2_accessed": False,
        "r2_written": False,
        "note": "Measures daily profile/close/actions plus rotating eight-year December prices and split-triggered refreshes. Action-method comparison separately measures elapsed time, decoded response bytes, and event counts; excludes hidden HTTP retries and R2 publication work.",
    }
    print("BENCHMARK_RESULT=" + json.dumps(report, ensure_ascii=False, sort_keys=True), flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
