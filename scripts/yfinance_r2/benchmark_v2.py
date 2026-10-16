"""Measure a deterministic J-Quants sample without writing to R2."""

from __future__ import annotations

import json
import hashlib
import os
import sys
import threading
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

import yfinance as yf

import sync_v2 as sync
import fetch_and_upload as collector
from fetch_jquants_universe import fetch_securities


class HttpRateLimit:
    """Apply one process-wide request-start limit and stop after the first HTTP 429."""

    def __init__(self, requests_per_second: float):
        if requests_per_second <= 0:
            raise ValueError("YFINANCE_HTTP_MAX_REQUESTS_PER_SECOND must be positive")
        self.interval = 1.0 / requests_per_second
        self.lock = threading.Lock()
        self.next_start = 0.0
        self.request_count = 0
        self.http_429_count = 0
        self.tripped = False
        self.original_request = None

    def install(self) -> None:
        from curl_cffi.requests import Session

        self.original_request = Session.request

        def limited_request(session, *args, **kwargs):
            with self.lock:
                if self.tripped:
                    raise RuntimeError("HTTP rate-limit circuit is open after a Yahoo 429")
                now = time.monotonic()
                if now < self.next_start:
                    time.sleep(self.next_start - now)
                self.next_start = time.monotonic() + self.interval
                self.request_count += 1
            response = self.original_request(session, *args, **kwargs)
            if getattr(response, "status_code", None) == 429:
                with self.lock:
                    self.http_429_count += 1
                    self.tripped = True
            return response

        Session.request = limited_request

    def restore(self) -> None:
        if self.original_request is not None:
            from curl_cffi.requests import Session
            Session.request = self.original_request


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


def ensure_workload_coverage(universe: list[dict[str, str]], sample: list[dict[str, str]]) -> list[dict[str, str]]:
    """Include one full-history rotation and one financial refresh in the sample."""
    selected = list(sample)
    for bucket_count in (sync.FULL_HISTORY_BUCKETS, sync.FINANCIAL_BUCKETS):
        if any(sync.bucket_for(row["security_code_display"], bucket_count) == 0 for row in selected):
            continue
        selected_codes = {row["security_code_display"] for row in selected}
        candidate = next((row for row in universe
                          if sync.bucket_for(row["security_code_display"], bucket_count) == 0
                          and row["security_code_display"] not in selected_codes), None)
        if candidate is None:
            continue
        replace_index = next((index for index, row in enumerate(selected)
                              if row.get("security_type") == candidate.get("security_type")
                              and sync.bucket_for(row["security_code_display"], sync.FULL_HISTORY_BUCKETS) != 0
                              and sync.bucket_for(row["security_code_display"], sync.FINANCIAL_BUCKETS) != 0), None)
        selected[replace_index if replace_index is not None else len(selected) - 1] = candidate
    return selected


def measure_security(index: int, total: int, entry: dict[str, str], session_date) -> dict[str, object]:
    """Measure one security using the production collector's per-ticker workload."""
    code = entry["security_code_display"]
    symbol = sync.market_symbol(code)
    ticker = yf.Ticker(symbol)
    is_long_history = sync.bucket_for(code, sync.FULL_HISTORY_BUCKETS) == 0
    is_financial = sync.bucket_for(code, sync.FINANCIAL_BUCKETS) == 0
    failures: Counter[str] = Counter()
    print(
        f"[{index}/{total}] {code} ({entry.get('security_type', 'listed_security')}): "
        f"long_history={is_long_history}, financials={is_financial}",
        flush=True,
    )
    sync.count_yfinance_call("ticker_get_info")
    attempt("ticker_get_info", ticker.get_info, failures)
    def daily_quote_and_candidate():
        period = "5d" if is_long_history else "1y"
        daily, frame = sync.fetch_daily_history_with_actions(ticker, session_date, period=period)
        sync.completed_close_for_session(daily, session_date)
        if not is_long_history:
            sync.action_candidate_from_history(frame, session_date)

    attempt("daily_quote_and_action_history_1y", daily_quote_and_candidate, failures)

    if is_long_history:
        attempt("history_actions_max", lambda: sync.fetch_repaired_action_history(ticker, session_date, period="max"), failures)
        attempt("history_year_end_prices_initial_8y", lambda: sync.monthly_history_rows(ticker, initial=True, as_of=session_date), failures)

    if is_financial:
        for name, request in (
            ("income_statement_yearly", lambda: ticker.get_income_stmt(freq="yearly")),
            ("balance_sheet_yearly", lambda: ticker.get_balance_sheet(freq="yearly")),
            ("cash_flow_yearly", lambda: ticker.get_cash_flow(freq="yearly")),
            ("valuation_measures_yearly", lambda: ticker.get_valuation_measures(freq="yearly", periods=10)),
        ):
            sync.count_yfinance_call(name)
            attempt(name, request, failures)
    return {"code": code, "symbol": symbol, "failures": dict(failures)}


def measure_action_method(symbol: str, method: str, session_date) -> dict[str, object]:
    """Compare unrepaired daily-chart action candidates with repaired history."""
    ticker = yf.Ticker(symbol)
    session = ticker._data._session
    original_get = session.get
    response_bytes = 0
    response_count = 0

    def counted_get(*args, **kwargs):
        nonlocal response_bytes, response_count
        response = original_get(*args, **kwargs)
        response_count += 1
        response_bytes += len(getattr(response, "content", b"") or b"")
        return response

    started = time.perf_counter()
    session.get = counted_get
    try:
        if method == "repaired_history":
            dividends, splits, _, _ = sync.fetch_repaired_action_history(ticker, session_date, period="1y")
        else:
            frame = ticker.history(
                period="1y", interval="1d", auto_adjust=False, actions=True,
                repair=False, keepna=True, raise_errors=True,
                timeout=float(os.environ.get("YFINANCE_HTTP_TIMEOUT_SECONDS", "30")),
            )
            dividends, splits, _, _ = sync.action_candidate_from_history(frame, session_date)
        return {
            "method": method,
            "elapsed_seconds": round(time.perf_counter() - started, 3),
            "http_get_response_count": response_count,
            "response_body_bytes": response_bytes,
            "dividend_event_count": len(dividends),
            "split_event_count": len(splits),
            "event_fingerprint": hashlib.sha256(json.dumps({
                "dividends": [{key: row[key] for key in ("date", "amount") if key in row} for row in dividends],
                "splits": [{key: row[key] for key in ("date", "ratio") if key in row} for row in splits],
            }, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest(),
        }
    finally:
        session.get = original_get


def compare_action_methods(sample: list[dict[str, str]], session_date) -> dict[str, object]:
    results = []
    for entry in sample[: min(6, len(sample))]:
        symbol = sync.market_symbol(entry["security_code_display"])
        for method in ("repaired_history", "daily_chart_candidate"):
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
        "note": "response_body_bytes counts decoded HTTP response bodies, not TLS/compression wire bytes; comparison is diagnostic, and repaired history remains authoritative whenever an event change is detected.",
    }


def main() -> int:
    api_key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("JQUANTS_API_KEY is required")
    limit = min(500, max(1, int(os.environ.get("YFINANCE_BENCHMARK_SAMPLE_SIZE", "40"))))
    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    universe = fetch_securities(api_key)
    sample = ensure_workload_coverage(universe, sample_universe(universe, limit))
    session_date = datetime.now(sync.JST).date()
    worker_count = max(1, int(os.environ.get("YFINANCE_BENCHMARK_WORKERS", "16")))
    max_requests_per_second = float(os.environ.get("YFINANCE_HTTP_MAX_REQUESTS_PER_SECOND", "8"))
    http_limit = HttpRateLimit(max_requests_per_second)
    http_limit.install()
    started = time.perf_counter()
    results = []
    action_comparison = None
    sample_http_request_count = 0
    sample_http_429_count = 0
    with sync._YF_CALL_LOCK:
        sample_calls: dict[str, int] = {}
    try:
        with ThreadPoolExecutor(max_workers=worker_count) as pool:
            futures = [pool.submit(measure_security, index, len(sample), entry, session_date)
                       for index, entry in enumerate(sample, start=1)]
            for future in as_completed(futures):
                results.append(future.result())
        elapsed = round(time.perf_counter() - started, 3)
        sample_http_request_count = http_limit.request_count
        sample_http_429_count = http_limit.http_429_count
        with sync._YF_CALL_LOCK:
            sample_calls = dict(sorted(sync._YF_CALLS.items()))
        action_comparison = compare_action_methods(sample, session_date)
    finally:
        http_limit.restore()

    failures: Counter[str] = Counter()
    for result in results:
        failures.update(result["failures"])
    comparison_http_request_count = http_limit.request_count - sample_http_request_count
    comparison_http_429_count = http_limit.http_429_count - sample_http_429_count
    report = {
        "universe_count": len(universe),
        "sample_count": len(sample),
        "sample_types": dict(sorted(Counter(row.get("security_type", "listed_security") for row in sample).items())),
        "workers": worker_count,
        "http_max_requests_per_second": max_requests_per_second,
        "http_request_count": sample_http_request_count,
        "action_comparison_http_request_count": comparison_http_request_count,
        "http_429_count": http_limit.http_429_count,
        "action_comparison_http_429_count": comparison_http_429_count,
        "http_rate_limit_circuit_tripped": http_limit.tripped,
        "session_date": session_date.isoformat(),
        "elapsed_seconds": elapsed,
        "yfinance_call_count": sum(sample_calls.values()),
        "yfinance_calls": sample_calls,
        "failed_requests": dict(sorted(failures.items())),
        "action_method_comparison": action_comparison,
        "r2_accessed": False,
        "r2_written": False,
        "note": "Measures a production-shaped sample with at least one full-history rotation and one annual-financial refresh, using the configured ticker worker count and a process-wide HTTP request-start limiter. It excludes R2 publication, installation, calendar, hidden HTTP retry, and repository setup time. Action-method comparison is serial and excluded from elapsed_seconds but remains rate-limited.",
    }
    print("BENCHMARK_RESULT=" + json.dumps(report, ensure_ascii=False, sort_keys=True), flush=True)
    return 1 if http_limit.http_429_count else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
