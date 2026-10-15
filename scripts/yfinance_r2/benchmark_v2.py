"""Measure a deterministic J-Quants sample without writing to R2."""

from __future__ import annotations

import json
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
    report = {
        "universe_count": len(universe),
        "sample_count": len(sample),
        "sample_types": dict(sorted(Counter(row.get("security_type", "listed_security") for row in sample).items())),
        "session_date": session_date.isoformat(),
        "elapsed_seconds": elapsed,
        "yfinance_call_count": sum(calls.values()),
        "yfinance_calls": calls,
        "failed_requests": dict(sorted(failures.items())),
        "r2_accessed": False,
        "r2_written": False,
        "note": "Measures daily profile/close/actions plus rotating eight-year December prices and split-triggered refreshes; excludes hidden HTTP retries and R2 publication work.",
    }
    print("BENCHMARK_RESULT=" + json.dumps(report, ensure_ascii=False, sort_keys=True), flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
