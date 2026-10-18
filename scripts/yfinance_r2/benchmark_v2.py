"""Measure a deterministic J-Quants sample without writing to R2."""

from __future__ import annotations

import json
import hashlib
import os
import sys
import threading
import time
import csv
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, timedelta
from pathlib import Path

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


def recent_action_codes(events_path: str | Path, session_date: date) -> dict[str, set[str]]:
    """Return recent split/reverse-split security codes used only to stratify the sample."""
    cutoff = session_date - timedelta(days=365)
    by_code: dict[str, set[str]] = {}
    with open(events_path, encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            try:
                event_date = date.fromisoformat(str(row.get("effective_date") or ""))
            except ValueError:
                continue
            if not cutoff <= event_date <= session_date:
                continue
            code = str(row.get("code") or "").strip()
            if not code:
                continue
            event_type = str(row.get("event_type") or "").strip().lower()
            try:
                factor = float(row.get("factor") or "")
            except (TypeError, ValueError):
                factor = None
            normalized = "reverse_split" if event_type == "reverse_split" or (factor is not None and factor < 1) else "split"
            by_code.setdefault(code, set()).add(normalized)
    return by_code


def action_stratified_sample(
    universe: list[dict[str, str]], base_sample: list[dict[str, str]], limit: int,
    session_date: date, events_path: str | Path | None = None,
) -> tuple[list[dict[str, str]], dict[str, object]]:
    """Prioritize recent split cases while retaining a stratified non-event sample."""
    source_path = events_path or Path(__file__).parents[2] / "data/irbank/catalog/share_split_events.csv"
    event_codes = recent_action_codes(source_path, session_date)
    universe_by_code = {row["security_code_display"]: row for row in universe}
    active_events = {code: types for code, types in event_codes.items() if code in universe_by_code}
    reverse_codes = sorted(code for code, types in active_events.items() if "reverse_split" in types)
    split_codes = sorted(code for code, types in active_events.items() if "split" in types and code not in reverse_codes)
    # Small smoke samples still cover both event directions. At 500, include every known
    # recent event security that is present in the J-Quants universe.
    event_limit = min(limit, len(active_events))
    if limit <= 20:
        event_limit = min(event_limit, max(0, limit // 3))
        if reverse_codes and event_limit == 0:
            event_limit = 1
    prioritized_codes = (reverse_codes + split_codes)[:event_limit]
    selected_codes = list(prioritized_codes)
    selected_set = set(selected_codes)
    for row in base_sample:
        code = row["security_code_display"]
        if code not in selected_set and code not in active_events and len(selected_codes) < limit:
            selected_codes.append(code)
            selected_set.add(code)
    if len(selected_codes) < limit:
        for row in universe:
            code = row["security_code_display"]
            if code not in selected_set and code not in active_events:
                selected_codes.append(code)
                selected_set.add(code)
                if len(selected_codes) == limit:
                    break
    selected = [universe_by_code[code] for code in selected_codes[:limit] if code in universe_by_code]
    sampled_event_types = Counter(
        event_type for code in selected_codes if code in active_events
        for event_type in active_events[code]
    )
    selection = {
        "event_window_start": (session_date - timedelta(days=365)).isoformat(),
        "event_window_end": session_date.isoformat(),
        "event_source_for_sampling_only": str(source_path),
        "known_recent_event_codes_in_jquants_universe": len(active_events),
        "known_recent_split_codes_in_jquants_universe": sum("split" in types for types in active_events.values()),
        "known_recent_reverse_split_codes_in_jquants_universe": sum("reverse_split" in types for types in active_events.values()),
        "sampled_recent_event_code_count": sum(code in active_events for code in selected_codes),
        "sampled_recent_event_types": dict(sorted(sampled_event_types.items())),
        "sampled_security_types": dict(sorted(Counter(row.get("security_type", "listed_security") for row in selected).items())),
        "sampled_event_codes": [code for code in selected_codes if code in active_events],
    }
    return selected, selection


def attempt(name: str, call, failures: Counter[str]) -> bool:
    try:
        call()
        return True
    except Exception as exc:
        failures[type(exc).__name__] += 1
        print(f"{name} failed: {type(exc).__name__}: {str(exc)[:240]}", file=sys.stderr, flush=True)
        return False


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

    def fetch_info():
        info = ticker.get_info()
        if not isinstance(info, dict) or not info:
            raise RuntimeError("yfinance returned no company profile")

    info_succeeded = attempt("ticker_get_info", fetch_info, failures)

    quote_succeeded = False

    def daily_quote_and_candidate():
        nonlocal quote_succeeded
        period = "5d" if is_long_history else "1y"
        daily, frame = sync.fetch_daily_history_with_actions(ticker, session_date, period=period)
        sync.completed_close_for_session(daily, session_date)
        quote_succeeded = True
        if not is_long_history:
            sync.action_candidate_from_history(frame, session_date)

    history_succeeded = attempt("daily_quote_and_action_history_1y", daily_quote_and_candidate, failures)

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
    return {
        "code": code,
        "symbol": symbol,
        "profile_succeeded": info_succeeded,
        "quote_succeeded": quote_succeeded,
        "core_succeeded": info_succeeded and history_succeeded,
        "failures": dict(failures),
    }


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

    def fetch_info():
        info = ticker.get_info()
        if not isinstance(info, dict) or not info:
            raise RuntimeError("yfinance returned no company profile")

    info_succeeded = attempt("ticker_get_info", fetch_info, failures)

    quote_succeeded = False

    def daily_quote_and_candidate():
        nonlocal quote_succeeded
        period = "5d" if is_long_history else "1y"
        daily, frame = sync.fetch_daily_history_with_actions(ticker, session_date, period=period)
        sync.completed_close_for_session(daily, session_date)
        quote_succeeded = True
        if not is_long_history:
            sync.action_candidate_from_history(frame, session_date)

    history_succeeded = attempt("daily_quote_and_action_history_1y", daily_quote_and_candidate, failures)

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
    return {
        "code": code,
        "symbol": symbol,
        "profile_succeeded": info_succeeded,
        "quote_succeeded": quote_succeeded,
        "core_succeeded": info_succeeded and history_succeeded,
        "failures": dict(failures),
    }


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
                repair=False, keepna=True,
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
            "dividend_events": [
                {key: row[key] for key in ("date", "amount") if key in row} for row in dividends
            ],
            "split_events": [
                {key: row[key] for key in ("date", "ratio") if key in row} for row in splits
            ],
            "event_fingerprint": hashlib.sha256(json.dumps({
                "dividends": [{key: row[key] for key in ("date", "amount") if key in row} for row in dividends],
                "splits": [{key: row[key] for key in ("date", "ratio") if key in row} for row in splits],
            }, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest(),
        }
    finally:
        session.get = original_get


def compare_action_methods(sample: list[dict[str, str]], session_date) -> tuple[dict[str, object], list[dict[str, object]]]:
    results = []
    for entry in sample:
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
    by_symbol: dict[str, dict[str, dict[str, object]]] = {}
    for row in results:
        by_symbol.setdefault(str(row["symbol"]), {})[str(row["method"])] = row
    comparable = [
        methods for methods in by_symbol.values()
        if all(method in methods and "error" not in methods[method]
               for method in ("repaired_history", "daily_chart_candidate"))
    ]
    equal_symbols = [
        symbol for symbol, methods in by_symbol.items()
        if all(method in methods and "error" not in methods[method]
               for method in ("repaired_history", "daily_chart_candidate"))
        and methods["repaired_history"].get("event_fingerprint") == methods["daily_chart_candidate"].get("event_fingerprint")
    ]
    differing_symbols = [
        symbol for symbol, methods in by_symbol.items()
        if all(method in methods and "error" not in methods[method]
               for method in ("repaired_history", "daily_chart_candidate"))
        and methods["repaired_history"].get("event_fingerprint") != methods["daily_chart_candidate"].get("event_fingerprint")
    ]
    summary = {
        "sample_count": len(sample),
        "comparison_count": len(by_symbol),
        "both_methods_succeeded": len(comparable),
        "equal_event_fingerprints": len(equal_symbols),
        "different_event_fingerprints": len(differing_symbols),
        "comparison_errors": len(by_symbol) - len(comparable),
        "different_symbols": differing_symbols,
        "note": "response_body_bytes counts decoded HTTP response bodies, not TLS/compression wire bytes; comparison is diagnostic, and repaired history remains authoritative whenever an event change is detected.",
    }
    return summary, results


def benchmark_session_date(now=None, latest_yahoo_date: date | None = None) -> date:
    """Bound the benchmark date by both the latest XTKS session and Yahoo's available close."""
    latest_session = sync.latest_completed_xtks_session(now)
    return min(latest_session, latest_yahoo_date) if latest_yahoo_date else latest_session


def benchmark_session_date(now=None, latest_yahoo_date: date | None = None) -> date:
    """Bound the benchmark date by both the latest XTKS session and Yahoo's available close."""
    latest_session = sync.latest_completed_xtks_session(now)
    return min(latest_session, latest_yahoo_date) if latest_yahoo_date else latest_session


def main() -> int:
    benchmark_started = time.perf_counter()
    api_key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("JQUANTS_API_KEY is required")
    limit = min(500, max(1, int(os.environ.get("YFINANCE_BENCHMARK_SAMPLE_SIZE", "40"))))
    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    universe = fetch_securities(api_key)
    expected_session_date = sync.latest_completed_xtks_session()
    worker_count = max(1, int(os.environ.get("YFINANCE_BENCHMARK_WORKERS", "16")))
    max_requests_per_second = float(os.environ.get("YFINANCE_HTTP_MAX_REQUESTS_PER_SECOND", "8"))
    http_limit = HttpRateLimit(max_requests_per_second)
    http_limit.install()
    started = time.perf_counter()
    results = []
    action_comparison = None
    sample_http_request_count = 0
    sample_http_429_count = 0
    session_probe_http_request_count = 0
    session_probe_429_count = 0
    session_probe_yahoo_date = None
    session_probe_elapsed = 0.0
    session_probe_calls: dict[str, int] = {}
    with sync._YF_CALL_LOCK:
        sample_calls: dict[str, int] = {}
    try:
        probe_started = time.perf_counter()
        probe = yf.Ticker("7203.T")
        probe_daily, _ = sync.fetch_daily_history_with_actions(
            probe, expected_session_date, period="5d",
        )
        if not probe_daily:
            raise RuntimeError("Yahoo session probe returned no completed daily close")
        session_probe_yahoo_date = max(date.fromisoformat(str(row["date"])) for row in probe_daily)
        session_date = benchmark_session_date(latest_yahoo_date=session_probe_yahoo_date)
        sample, action_sample = action_stratified_sample(
            universe,
            ensure_workload_coverage(universe, sample_universe(universe, limit)),
            limit,
            session_date,
        )
        session_probe_elapsed = round(time.perf_counter() - probe_started, 3)
        session_probe_http_request_count = http_limit.request_count
        session_probe_429_count = http_limit.http_429_count
        with sync._YF_CALL_LOCK:
            session_probe_calls = dict(sorted(sync._YF_CALLS.items()))

        sample_started = time.perf_counter()
        with ThreadPoolExecutor(max_workers=worker_count) as pool:
            futures = [pool.submit(measure_security, index, len(sample), entry, session_date)
                       for index, entry in enumerate(sample, start=1)]
            for future in as_completed(futures):
                results.append(future.result())
        elapsed = round(time.perf_counter() - sample_started, 3)
        sample_http_request_count = http_limit.request_count - session_probe_http_request_count
        sample_http_429_count = http_limit.http_429_count - session_probe_429_count
        with sync._YF_CALL_LOCK:
            all_calls = dict(sorted(sync._YF_CALLS.items()))
            sample_calls = {
                name: count - session_probe_calls.get(name, 0)
                for name, count in all_calls.items()
                if count - session_probe_calls.get(name, 0) > 0
            }
        action_comparison, action_details = compare_action_methods(sample, session_date)
        action_report_path = Path(os.environ.get(
            "YFINANCE_BENCHMARK_ACTION_REPORT_PATH", "benchmark-action-comparison.json",
        ))
        action_report_path.parent.mkdir(parents=True, exist_ok=True)
        action_report_path.write_text(json.dumps({
            "session_date": session_date.isoformat(),
            "sampling": action_sample,
            "comparison": action_comparison,
            "results": action_details,
        }, ensure_ascii=False, indent=2), encoding="utf-8")
        action_comparison["details_report_path"] = str(action_report_path)
    finally:
        http_limit.restore()

    failures: Counter[str] = Counter()
    for result in results:
        failures.update(result["failures"])
    profile_success_count = sum(1 for result in results if result["profile_succeeded"])
    quote_success_count = sum(1 for result in results if result["quote_succeeded"])
    profile_failure_count = len(results) - profile_success_count
    quote_failure_count = len(results) - quote_success_count
    core_failure_count = sum(1 for result in results if not result["core_succeeded"])
    core_success_count = len(results) - core_failure_count
    core_success_rate_pct = round(core_success_count / len(results) * 100, 2) if results else 0.0
    profile_success_rate_pct = round(profile_success_count / len(results) * 100, 2) if results else 0.0
    quote_success_rate_pct = round(quote_success_count / len(results) * 100, 2) if results else 0.0
    comparison_http_request_count = http_limit.request_count - sample_http_request_count - session_probe_http_request_count
    comparison_http_429_count = http_limit.http_429_count - sample_http_429_count - session_probe_429_count
    benchmark_elapsed = round(time.perf_counter() - benchmark_started, 3)
    report = {
        "universe_count": len(universe),
        "sample_count": len(sample),
        "sample_types": dict(sorted(Counter(row.get("security_type", "listed_security") for row in sample).items())),
        "action_sample": action_sample,
        "workers": worker_count,
        "http_max_requests_per_second": max_requests_per_second,
        "http_request_count": sample_http_request_count,
        "action_comparison_http_request_count": comparison_http_request_count,
        "http_429_count": http_limit.http_429_count,
        "action_comparison_http_429_count": comparison_http_429_count,
        "http_rate_limit_circuit_tripped": http_limit.tripped,
        "expected_session_date": expected_session_date.isoformat(),
        "session_date": session_date.isoformat(),
        "session_fallback_used": session_date < expected_session_date,
        "session_probe_yahoo_date": session_probe_yahoo_date.isoformat(),
        "session_probe_elapsed_seconds": session_probe_elapsed,
        "session_probe_http_request_count": session_probe_http_request_count,
        "session_probe_yfinance_calls": session_probe_calls,
        "elapsed_seconds": elapsed,
        "benchmark_elapsed_seconds": benchmark_elapsed,
        "core_success_count": core_success_count,
        "core_failure_count": core_failure_count,
        "core_success_rate_pct": core_success_rate_pct,
        "profile_success_count": profile_success_count,
        "profile_failure_count": profile_failure_count,
        "profile_success_rate_pct": profile_success_rate_pct,
        "quote_close_success_count": quote_success_count,
        "quote_close_failure_count": quote_failure_count,
        "quote_close_success_rate_pct": quote_success_rate_pct,
        "core_failed_securities": [
            {"code": result["code"], "symbol": result["symbol"],
             "profile_succeeded": result["profile_succeeded"],
             "quote_succeeded": result["quote_succeeded"], "failures": result["failures"]}
            for result in results if not result["core_succeeded"]
        ],
        "securities_with_any_failed_request": sum(1 for result in results if result["failures"]),
        "yfinance_call_count": sum(sample_calls.values()),
        "yfinance_calls": sample_calls,
        "failed_requests": dict(sorted(failures.items())),
        "action_method_comparison": action_comparison,
        "r2_accessed": False,
        "r2_written": False,
        "note": "Measures a production-shaped sample with at least one full-history rotation and one annual-financial refresh. The action-method comparison covers every sampled security, prioritizing all recent split/reverse-split event codes available in J-Quants at samples of 500. The benchmark session is the earlier of the latest completed XTKS session and the latest close returned by a 7203.T probe; production sync continues to require the exact latest completed XTKS close. elapsed_seconds covers sample acquisition; benchmark_elapsed_seconds also includes J-Quants retrieval, the session probe, and serial action-method comparison. Both exclude R2 publication, dependency installation, hidden HTTP retry, and runner setup.",
    }
    print("BENCHMARK_RESULT=" + json.dumps(report, ensure_ascii=False, sort_keys=True), flush=True)
    max_profile_failure_rate = min(1.0, max(0.0, float(os.environ.get("YFINANCE_BENCHMARK_MAX_PROFILE_FAILURE_RATE", "0.10"))))
    min_quote_success_rate = min(1.0, max(0.0, float(os.environ.get("YFINANCE_BENCHMARK_MIN_QUOTE_SUCCESS_RATE", "0.50"))))
    return 1 if (
        http_limit.http_429_count
        or profile_failure_count / max(1, len(results)) > max_profile_failure_rate
        or quote_success_count / max(1, len(results)) < min_quote_success_rate
    ) else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
