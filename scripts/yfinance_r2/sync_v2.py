"""Synchronize the app's Japanese listed-security universe to the R2 v2 schema."""

from __future__ import annotations

import argparse
import hashlib
import gzip
import json
import math
import os
import re
import sys
import threading
import time
import traceback
from collections import Counter
from contextlib import contextmanager
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo
from uuid import uuid4

import yfinance as yf

# Surface yfinance request/repair exceptions to the per-security retry state machine.
yf.config.debug.hide_exceptions = False

import fetch_and_upload as collector
from fetch_jquants_universe import fetch_securities
from monthly_price_calendar import last_xtks_session_in_december, normalize_december_price_point as normalize_monthly_price_point


UTC = timezone.utc
JST = ZoneInfo("Asia/Tokyo")
ROOT = "market/yfinance/v2"
OLD_ROOT = "market/yfinance"
MAX_DAILY_ATTEMPTS = 4
FULL_HISTORY_BUCKETS = 20
FINANCIAL_BUCKETS = 5
COMPANY_INFO_BUCKETS = 20
COMPANY_INFO_REFRESH_POLICY_VERSION = 2
FINANCIAL_REFRESH_MAX_AGE_DAYS = 45
FINANCIAL_REFRESH_POLICY_VERSION = 2
CALENDAR_REFRESH_MAX_AGE_DAYS = 30
CALENDAR_NEAR_EVENT_WINDOW_DAYS = 45
CALENDAR_NEAR_EVENT_REFRESH_DAYS = 7
FINANCIAL_GROUPS = ("income_statement", "balance_sheet", "cash_flow", "valuation")
# Latest company guidance replaces Yahoo's stale dividendRate while it still
# reports the audited old amount. AOKI: https://ir.aoki-hd.co.jp/ja/news/news/auto_20260508520915/pdfFile.pdf
# Mitsubishi: https://www.mitsubishicorp.com/jp/ja/ir/shareholder/allot/
OFFICIAL_NEXT_FY_DPS = {"8214": (120.0, 90.0), "8058": (126.0, 125.0)}
_YF_CALL_LOCK = threading.Lock()
_YF_CALLS: dict[str, int] = {}
_ACTION_HISTORY_REPAIR_TRIGGERS: Counter[str] = Counter()
_R2_DOCUMENT_CACHE = threading.local()
_ATTRIBUTE_ERROR_TRACEBACK_LOCK = threading.Lock()
_ATTRIBUTE_ERROR_TRACEBACK_KEYS: set[tuple[str, str, str, int, str]] = set()
_ATTRIBUTE_ERROR_TRACEBACK_LIMIT = 8


def log_attribute_error_traceback(exc: Exception) -> None:
    """Log a bounded traceback for the repeated string.get() failure cohort."""
    if not isinstance(exc, AttributeError) or "'str' object has no attribute 'get'" not in str(exc):
        return
    frames = traceback.extract_tb(exc.__traceback__) if exc.__traceback__ else []
    origin = frames[-1] if frames else None
    key = (
        type(exc).__name__,
        str(exc),
        origin.filename if origin else "",
        origin.lineno if origin else 0,
        origin.name if origin else "",
    )
    with _ATTRIBUTE_ERROR_TRACEBACK_LOCK:
        if key in _ATTRIBUTE_ERROR_TRACEBACK_KEYS or len(_ATTRIBUTE_ERROR_TRACEBACK_KEYS) >= _ATTRIBUTE_ERROR_TRACEBACK_LIMIT:
            return
        _ATTRIBUTE_ERROR_TRACEBACK_KEYS.add(key)
        detail = "".join(traceback.format_exception(type(exc), exc, exc.__traceback__, limit=12))
        print(
            f"DIAGNOSTIC AttributeError traceback origin={key[2]}:{key[3]}:{key[4]}\n{detail}",
            file=sys.stderr,
            flush=True,
        )


class ActivityTimeline:
    """Measure wall-clock intervals with Yahoo and R2 requests active, including overlap."""

    def __init__(self):
        self.lock = threading.Lock()
        self.intervals: list[tuple[str, float, float]] = []

    def record(self, category: str, started: float, ended: float) -> None:
        if ended <= started:
            return
        with self.lock:
            self.intervals.append((category, started, ended))

    def snapshot(self, window_start: float, window_end: float) -> dict[str, float]:
        if window_end < window_start:
            raise ValueError("activity window end precedes start")
        with self.lock:
            intervals = tuple(self.intervals)
        boundaries: dict[float, dict[str, int]] = {}
        for category, started, ended in intervals:
            left = max(window_start, started)
            right = min(window_end, ended)
            if right <= left:
                continue
            boundaries.setdefault(left, {}).setdefault(category, 0)
            boundaries[left][category] += 1
            boundaries.setdefault(right, {}).setdefault(category, 0)
            boundaries[right][category] -= 1

        active: Counter[str] = Counter()
        totals = Counter()
        points = sorted(set(boundaries) | {window_start, window_end})
        for index, point in enumerate(points[:-1]):
            for category, delta in boundaries.get(point, {}).items():
                active[category] += delta
                if active[category] <= 0:
                    active.pop(category, None)
            duration = points[index + 1] - point
            yahoo_active = active.get("yfinance", 0) > 0
            r2_active = active.get("r2", 0) > 0
            if yahoo_active:
                totals["yfinance_active_wall_seconds"] += duration
            if r2_active:
                totals["r2_active_wall_seconds"] += duration
            if yahoo_active and r2_active:
                totals["overlap_wall_seconds"] += duration
            elif yahoo_active:
                totals["yfinance_only_wall_seconds"] += duration
            elif r2_active:
                totals["r2_only_wall_seconds"] += duration
            else:
                totals["other_wall_seconds"] += duration

        return {
            "window_wall_seconds": round(window_end - window_start, 3),
            **{key: round(totals[key], 3) for key in (
                "yfinance_active_wall_seconds", "r2_active_wall_seconds",
                "overlap_wall_seconds", "yfinance_only_wall_seconds",
                "r2_only_wall_seconds", "other_wall_seconds",
            )},
        }


class R2Metrics:
    """Thread-safe timings for R2 operations performed by the sync run."""

    def __init__(self):
        self.lock = threading.Lock()
        self.calls: dict[str, int] = {}
        self.seconds: dict[str, float] = {}
        self.activity = ActivityTimeline()

    def record(self, operation: str, elapsed: float, *, started: float | None = None,
               ended: float | None = None) -> None:
        with self.lock:
            self.calls[operation] = self.calls.get(operation, 0) + 1
            self.seconds[operation] = self.seconds.get(operation, 0.0) + elapsed
        if started is not None and ended is not None:
            self.activity.record("r2", started, ended)

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {
                "calls": dict(sorted(self.calls.items())),
                "seconds": {key: round(value, 3) for key, value in sorted(self.seconds.items())},
            }


class _MeasuredBody:
    def __init__(self, body: Any, metrics: R2Metrics):
        self.body = body
        self.metrics = metrics

    def read(self, *args: Any, **kwargs: Any) -> Any:
        started = time.perf_counter()
        try:
            return self.body.read(*args, **kwargs)
        finally:
            ended = time.perf_counter()
            self.metrics.record("get_body", ended - started, started=started, ended=ended)

    def __getattr__(self, name: str) -> Any:
        return getattr(self.body, name)


class _MeasuredPaginator:
    def __init__(self, paginator: Any, metrics: R2Metrics):
        self.paginator = paginator
        self.metrics = metrics

    def paginate(self, *args: Any, **kwargs: Any):
        pages = iter(self.paginator.paginate(*args, **kwargs))
        while True:
            started = time.perf_counter()
            try:
                page = next(pages)
            except StopIteration:
                return
            except Exception:
                ended = time.perf_counter()
                self.metrics.record("list_objects_v2", ended - started, started=started, ended=ended)
                raise
            ended = time.perf_counter()
            self.metrics.record("list_objects_v2", ended - started, started=started, ended=ended)
            yield page

    def __getattr__(self, name: str) -> Any:
        return getattr(self.paginator, name)


class MeasuredR2Client:
    """Transparent boto client proxy that records request and body-read time."""

    _MEASURED_METHODS = {"get_object", "put_object", "delete_object", "head_object"}

    def __init__(self, client: Any, metrics: R2Metrics):
        self.client = client
        self.metrics = metrics

    def __getattr__(self, name: str) -> Any:
        if name == "get_paginator":
            return lambda operation: _MeasuredPaginator(self.client.get_paginator(operation), self.metrics)
        if name not in self._MEASURED_METHODS:
            return getattr(self.client, name)

        method = getattr(self.client, name)

        def measured(*args: Any, **kwargs: Any) -> Any:
            started = time.perf_counter()
            try:
                response = method(*args, **kwargs)
            finally:
                ended = time.perf_counter()
                self.metrics.record(name, ended - started, started=started, ended=ended)
            if name == "get_object" and isinstance(response, dict) and response.get("Body") is not None:
                response = dict(response)
                response["Body"] = _MeasuredBody(response["Body"], self.metrics)
            return response

        return measured


class GlobalHttpRateLimiter:
    """Limit request starts across all yfinance worker threads in this process."""

    def __init__(self, requests_per_second: float):
        if requests_per_second <= 0:
            raise ValueError("YFINANCE_HTTP_MAX_REQUESTS_PER_SECOND must be positive")
        self.requests_per_second = requests_per_second
        self.interval = 1.0 / requests_per_second
        self.lock = threading.Lock()
        self.next_start = 0.0
        self.request_count = 0
        self.http_429_count = 0
        self.original_request = None
        self.activity: ActivityTimeline | None = None

    def __enter__(self):
        from curl_cffi.requests import Session

        self.session_class = Session
        self.original_request = Session.request

        def limited_request(session, *args, **kwargs):
            with self.lock:
                now = time.monotonic()
                if now < self.next_start:
                    time.sleep(self.next_start - now)
                self.next_start = time.monotonic() + self.interval
                self.request_count += 1
            request_started = time.perf_counter()
            try:
                response = self.original_request(session, *args, **kwargs)
            finally:
                if self.activity is not None:
                    self.activity.record("yfinance", request_started, time.perf_counter())
            if getattr(response, "status_code", None) == 429:
                with self.lock:
                    self.http_429_count += 1
            return response

        Session.request = limited_request
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        if self.original_request is not None:
            self.session_class.request = self.original_request
# J-Quants is the source for the supported Japanese-market universe and product
# category. Keep only names/IDs in profile.json; price currency remains on the
# current quote, where app calculations and display consume it.
PROFILE_FIELDS: tuple[str, ...] = ()
CURRENT_FIELDS = (
    "trailingEps", "forwardEps", "bookValue", "dividendRate", "dividendYield",
    "trailingPE", "priceToBook", "operatingMargins",
    "payoutRatio", "exDividendDate", "lastFiscalYearEnd", "mostRecentQuarter",
)
DERIVED_STATISTIC_FIELDS = ("trailingPE", "priceToBook")
INCOME_FIELDS = {
    "TotalRevenue": "sales",
    "Total Revenue": "sales",
    "OperatingRevenue": "sales",
    "Operating Revenue": "sales",
    "OperatingIncome": "operating_profit",
    "Operating Income": "operating_profit",
}
BALANCE_FIELDS = {
    "StockholdersEquity": "stockholders_equity",
    "Stockholders Equity": "stockholders_equity",
    "Total Equity Gross Minority Interest": "total_equity_gross_minority_interest",
    "TotalEquityGrossMinorityInterest": "total_equity_gross_minority_interest",
    "NetDebt": "net_debt",
    "Net Debt": "net_debt",
    "TotalDebt": "total_debt",
    "Total Debt": "total_debt",
    "CashCashEquivalentsAndShortTermInvestments": "cash_and_short_term_investments",
    "Cash Cash Equivalents And Short Term Investments": "cash_and_short_term_investments",
    "CashCashEquivalentsAndFederalFundsSold": "cash_and_equivalents",
    "Cash And Cash Equivalents": "cash_and_equivalents",
    "CashAndCashEquivalents": "cash_and_equivalents",
    "CashEquivalents": "cash_equivalents",
    "Cash Equivalents": "cash_equivalents",
    "CashFinancial": "cash_equivalents",
    "Cash Financial": "cash_equivalents",
    "TotalAssets": "total_assets",
    "Total Assets": "total_assets",
}
CASH_FLOW_FIELDS = {
    "OperatingCashFlow": "operating_cf",
    "Operating Cash Flow": "operating_cf",
    "InvestingCashFlow": "investing_cf",
    "Investing Cash Flow": "investing_cf",
    "CashDividendsPaid": "cash_dividends_paid",
    "Cash Dividends Paid": "cash_dividends_paid",
    "CommonStockDividendPaid": "common_stock_dividend_paid",
    "Common Stock Dividend Paid": "common_stock_dividend_paid",
}


def now_utc() -> datetime:
    return datetime.now(UTC)


def exception_http_status(error: Exception) -> int | None:
    candidates = [getattr(error, "status_code", None), getattr(error, "code", None)]
    response = getattr(error, "response", None)
    if isinstance(response, dict):
        candidates.extend((response.get("status_code"), response.get("status")))
        response_error = response.get("Error")
        if isinstance(response_error, dict):
            candidates.append(response_error.get("HTTPStatusCode"))
    elif response is not None:
        candidates.extend((getattr(response, "status_code", None), getattr(response, "status", None)))
    for candidate in candidates:
        try:
            status = int(candidate)
        except (TypeError, ValueError):
            continue
        if 100 <= status <= 599:
            return status
    message = str(error)
    match = re.search(
        r"\b(?:HTTP(?:\s+Error)?\s*[:=]?\s*|HTTP/\d(?:\.\d)?\s+)(\d{3})\b"
        r"|\b([45]\d\d)\s+(?:Client|Server) Error\b",
        message,
        re.IGNORECASE,
    )
    if match:
        return int(match.group(1) or match.group(2))
    return None


def classify_yfinance_error(error: Exception) -> str:
    """Separate absent Yahoo data from retryable service/network errors."""
    error_name = type(error).__name__
    status = exception_http_status(error)
    if error_name == "YFPricesMissingError" or status == 404:
        return "no_data"
    if error_name == "YFRateLimitError" or status in {408, 425, 429} or (status is not None and 500 <= status <= 599):
        return "transient"
    lowered_name = error_name.lower()
    if any(token in lowered_name for token in ("timeout", "connectionerror", "networkerror")):
        return "transient"
    return "unknown"


def iso_now() -> str:
    return now_utc().isoformat()


def count_yfinance_call(name: str) -> None:
    with _YF_CALL_LOCK:
        _YF_CALLS[name] = _YF_CALLS.get(name, 0) + 1


def count_action_history_repair_trigger(name: str) -> None:
    """Track overlapping reasons that caused a repaired action-history fetch."""
    with _YF_CALL_LOCK:
        _ACTION_HISTORY_REPAIR_TRIGGERS[name] += 1


def bucket_for(code: str, count: int) -> int:
    digest = hashlib.sha256(code.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") % count


def should_refresh_company_info(code: str, active_bucket: int, has_previous_success: bool, retry_pending: bool) -> bool:
    return (
        retry_pending
        or not has_previous_success
        or bucket_for(code, COMPANY_INFO_BUCKETS) == active_bucket
    )


def is_last_market_session(day: date) -> bool:
    import exchange_calendars as xcals
    import pandas as pd

    calendar = xcals.get_calendar("XTKS")
    session = pd.Timestamp(day)
    if not calendar.is_session(session):
        return False
    next_session = calendar.next_session(session)
    return next_session.month != session.month


def latest_completed_xtks_session(now: datetime | None = None) -> date:
    """Return today's XTKS session only after its close; otherwise use the last closed session."""
    import exchange_calendars as xcals
    import pandas as pd

    current = now or datetime.now(JST)
    current = current.replace(tzinfo=JST) if current.tzinfo is None else current.astimezone(JST)
    calendar = xcals.get_calendar("XTKS")
    today = pd.Timestamp(current.date())
    session = calendar.date_to_session(today, direction="previous")
    if session.date() == current.date():
        market_close = calendar.session_close(session).to_pydatetime().astimezone(JST)
        if current < market_close:
            session = calendar.previous_session(session)
    return session.date()


def year_end_price_years(as_of: date) -> tuple[int, int]:
    latest_year = as_of.year if as_of.month == 12 and is_last_market_session(as_of) else as_of.year - 1
    return latest_year - 7, latest_year


def safe_symbol(symbol: str) -> str:
    return symbol.replace("^", "_").replace("=", "_").replace("/", "_")


def market_symbol(code: str) -> str:
    normalized = str(code).strip().upper()
    if not re.fullmatch(r"[A-Z0-9]{4,5}", normalized):
        raise ValueError(f"Invalid J-Quants security code: {code!r}")
    return f"{normalized}.T"


def object_key(*parts: str) -> str:
    return "/".join((ROOT, *parts))


@contextmanager
def r2_document_cache_scope():
    """Reuse a security's R2 documents during one worker task, then release them."""
    previous = getattr(_R2_DOCUMENT_CACHE, "documents", None)
    previous_listed_prefixes = getattr(_R2_DOCUMENT_CACHE, "listed_prefixes", None)
    _R2_DOCUMENT_CACHE.documents = {}
    _R2_DOCUMENT_CACHE.listed_prefixes = set()
    try:
        yield
    finally:
        _R2_DOCUMENT_CACHE.documents = previous
        _R2_DOCUMENT_CACHE.listed_prefixes = previous_listed_prefixes


def read_object(client: Any, bucket: str, key: str) -> bytes | None:
    cache = getattr(_R2_DOCUMENT_CACHE, "documents", None)
    cache_key = (bucket, key)
    if cache is not None and cache_key in cache:
        return cache[cache_key]
    raw = collector.read_object(client, bucket, key)
    if cache is not None:
        cache[cache_key] = raw
    return raw


def cache_object(client: Any, bucket: str, key: str, raw: bytes | None) -> None:
    cache = getattr(_R2_DOCUMENT_CACHE, "documents", None)
    if cache is not None:
        cache[(bucket, key)] = raw


def delete_object_if_present(client: Any, bucket: str, key: str) -> bool:
    cache = getattr(_R2_DOCUMENT_CACHE, "documents", None)
    cache_key = (bucket, key)
    if cache is not None and cache_key not in cache:
        if any(
            listed_bucket == bucket and key.startswith(prefix)
            for listed_bucket, prefix in getattr(_R2_DOCUMENT_CACHE, "listed_prefixes", set())
        ):
            return False
    raw = read_object(client, bucket, key)
    if raw is None:
        return False
    client.delete_object(Bucket=bucket, Key=key)
    if cache is not None:
        cache[cache_key] = None
    return True


def read_json(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    raw = read_object(client, bucket, key)
    if raw is None:
        return None
    value = json.loads(raw)
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object at {key}")
    return value


def put_json_if_changed(client: Any, bucket: str, key: str, value: dict[str, Any]) -> bool:
    previous = read_object(client, bucket, key)
    changed = collector.put_json_if_changed(client, bucket, key, value, previous)
    if changed:
        cache_object(client, bucket, key, collector.json_payload(value))
    return changed


def finite_number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    result = float(value)
    return result if math.isfinite(result) else None


def calculate_daily_valuation(
    close: Any, trailing_eps: Any, book_value: Any,
    reported_per: Any = None, reported_pbr: Any = None,
) -> dict[str, float | None]:
    """Prefer reported yfinance valuation multiples and calculate only missing values."""
    price = finite_number(close)
    eps = finite_number(trailing_eps)
    bps = finite_number(book_value)
    per = finite_number(reported_per)
    pbr = finite_number(reported_pbr)
    return {
        "trailingPE": per if per is not None else round(price / eps, 6) if price is not None and price > 0 and eps is not None and eps > 0 else None,
        "priceToBook": pbr if pbr is not None else round(price / bps, 6) if price is not None and price > 0 and bps is not None and bps > 0 else None,
    }


def daily_price_change(frame: Any, price_date: date | None, close: Any) -> dict[str, float | None]:
    """Calculate the close-to-close change from the same daily chart used for the quote."""
    price = finite_number(close)
    if frame is None or getattr(frame, "empty", True) or "Close" not in getattr(frame, "columns", ()):
        return {"change": None, "change_percent": None, "previous_close": None}
    rows: list[tuple[date, float]] = []
    for index, raw_close in frame["Close"].items():
        row_date = date_value(index)
        row_close = finite_number(raw_close)
        if row_date is not None and row_close is not None:
            rows.append((row_date, row_close))
    rows.sort(key=lambda item: item[0])
    target = next((position for position, item in enumerate(rows) if item[0] == price_date), None)
    if target is None or target == 0 or price is None or price <= 0:
        return {"change": None, "change_percent": None, "previous_close": None}
    previous_close = rows[target - 1][1]
    if previous_close <= 0:
        return {"change": None, "change_percent": None, "previous_close": previous_close}
    change = price - previous_close
    return {
        "change": round(change, 8),
        "change_percent": round(change / previous_close * 100, 8),
        "previous_close": previous_close,
    }


def company_info_failure_metadata(error: Exception) -> dict[str, str]:
    return {
        "failure_class": classify_yfinance_error(error),
        "exception_type": type(error).__name__,
        "reason": str(error)[:300],
    }


def archive_unused_statistics(client: Any, bucket: str, symbol: str, previous_statistics: Any) -> None:
    if not isinstance(previous_statistics, dict):
        return
    unused = {key: value for key, value in previous_statistics.items() if key not in CURRENT_FIELDS}
    if not unused:
        return
    key = object_key("archive", "unused_statistics", safe_symbol(symbol), "statistics.json")
    previous = read_json(client, bucket, key) or {}
    archived_values = previous.get("statistics") if isinstance(previous.get("statistics"), dict) else {}
    merged = dict(archived_values)
    for field, value in unused.items():
        merged.setdefault(field, value)
    if merged == archived_values:
        return
    put_json_if_changed(client, bucket, key, {
        "schema_version": 1,
        "symbol": symbol,
        "statistics": merged,
        "reason": "fields no longer consumed by the application or daily calculations",
        "updated_at": iso_now(),
        "source": "Yahoo Finance via yfinance; prior V2 current snapshot",
    })


def next_fiscal_year_forecast_dps(code: str, reported_value: Any, as_of: date) -> float | None:
    reported = finite_number(reported_value)
    stale_value, official_value = OFFICIAL_NEXT_FY_DPS.get(code, (None, None))
    if (as_of <= date(2027, 3, 31) and official_value is not None
            and (reported is None or math.isclose(reported, stale_value, abs_tol=0.001))):
        return official_value
    return reported


def date_value(value: Any) -> date | None:
    if value is None:
        return None
    try:
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(float(value), UTC).date()
        if hasattr(value, "date"):
            return value.date()
        text = str(value).strip()
        for pattern in ("%Y/%m/%d", "%m/%d/%Y"):
            try:
                return datetime.strptime(text, pattern).date()
            except ValueError:
                pass
        return date.fromisoformat(text[:10])
    except (ValueError, TypeError, OSError, OverflowError):
        return None


def canonical_event_date(value: Any) -> str | None:
    parsed = date_value(value)
    return parsed.isoformat() if parsed is not None else None


def normalize_event_rows(rows: list[dict[str, Any]], key: str) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        copied = dict(row)
        event_date = canonical_event_date(copied.get(key))
        if event_date is not None:
            copied[key] = event_date
        normalized.append(copied)
    return normalized


def preserve_legacy_event_gaps(
    rows: list[dict[str, Any]], legacy_rows: list[dict[str, Any]], key: str,
) -> list[dict[str, Any]]:
    """Keep legacy events that Yahoo omitted while replacing a refreshed history range."""
    result = normalize_event_rows(rows, key)
    seen = {canonical_event_date(row.get(key)) for row in result}
    for legacy_row in normalize_event_rows(legacy_rows, key):
        event_date = canonical_event_date(legacy_row.get(key))
        if event_date is not None and event_date not in seen:
            result.append(legacy_row)
            seen.add(event_date)
    return result


def calendar_field_date(calendar: Any, keys: tuple[str, ...]) -> date | None:
    """Read a named date from dictionary or DataFrame yfinance calendar payloads."""
    value: Any = None
    if isinstance(calendar, dict):
        for key in keys:
            if key in calendar:
                value = calendar[key]
                break
    elif hasattr(calendar, "index") and hasattr(calendar, "columns"):
        # Older yfinance releases returned a one-column DataFrame with event
        # names on the index; newer releases return a dictionary.
        for key in keys:
            try:
                if key in calendar.index:
                    value = calendar.loc[key]
                    if hasattr(value, "iloc"):
                        value = value.iloc[0]
                    break
                if key in calendar.columns:
                    value = calendar[key]
                    if hasattr(value, "iloc"):
                        value = value.iloc[0]
                    break
            except (KeyError, IndexError, TypeError):
                continue
    return date_value(value)


def calendar_ex_dividend_date(calendar: Any) -> date | None:
    """Read Yahoo's next Ex-Dividend Date from yfinance's calendar payload."""
    return calendar_field_date(calendar, ("Ex-Dividend Date", "exDividendDate"))


def calendar_payment_start_date(calendar: Any) -> date | None:
    """Read Yahoo's announced next dividend payment date when provided."""
    return calendar_field_date(calendar, ("Dividend Date", "dividendDate"))


def years_before(day: date, years: int) -> date:
    try:
        return day.replace(year=day.year - years)
    except ValueError:
        return day.replace(year=day.year - years, day=28)


def calendar_year_dividend_metrics(
    events: list[dict[str, Any]],
    previous: dict[str, Any] | None,
    as_of: date,
) -> dict[str, Any]:
    """Build consecutive dividend metrics from complete, observed calendar years."""
    previous = previous if isinstance(previous, dict) else {}
    old_records = previous.get("annual_records")
    annual: dict[int, float] = {}
    if isinstance(old_records, list):
        for row in old_records:
            year = row.get("calendar_year") if isinstance(row, dict) else None
            amount = finite_number(row.get("calendar_year_distribution_per_share")) if isinstance(row, dict) else None
            if isinstance(year, int) and 1900 <= year < as_of.year and amount is not None and amount >= 0:
                annual[year] = amount

    event_totals: dict[int, float] = {}
    seen: set[tuple[str, float]] = set()
    for event in events:
        event_date = date_value(event.get("date")) if isinstance(event, dict) else None
        amount = finite_number(event.get("amount")) if isinstance(event, dict) else None
        if event_date is None or event_date.year >= as_of.year or amount is None or amount <= 0:
            continue
        key = (event_date.isoformat(), amount)
        if key in seen:
            continue
        seen.add(key)
        event_totals[event_date.year] = event_totals.get(event_date.year, 0.0) + amount
    annual.update(event_totals)

    latest_year = as_of.year - 1
    if annual:
        # A successful event-history fetch covers the interval from the first
        # observed dividend through the latest completed calendar year. Any
        # year without an event in that interval is treated as a confirmed 0
        # for calendar-year comparisons. Failed fetches bypass this function.
        for year in range(min(annual), latest_year + 1):
            annual.setdefault(year, 0.0)
    records = [
        {
            "calendar_year": year,
            "calendar_year_distribution_per_share": round(amount, 8),
            "amount_basis": "yfinance_split_adjusted_assumption",
            "record_basis": "no_observed_distribution_assumed_zero" if amount == 0 else "observed_distribution_total",
            "source": "Yahoo Finance via yfinance",
        }
        for year, amount in sorted(annual.items())
    ]
    latest = annual.get(latest_year)
    streak_increase = 0
    streak_no_cut = 0
    status = "insufficient_history"
    if latest is not None:
        years = sorted(annual)
        latest_index = years.index(latest_year)
        if latest_index > 0 and years[latest_index - 1] == latest_year - 1:
            status = "available"
            for index in range(latest_index, 0, -1):
                year = years[index]
                previous_year = years[index - 1]
                if year != previous_year + 1:
                    break
                current_amount = annual[year]
                previous_amount = annual[previous_year]
                if current_amount > previous_amount:
                    streak_increase += 1
                else:
                    break
            for index in range(latest_index, 0, -1):
                year = years[index]
                previous_year = years[index - 1]
                if year != previous_year + 1 or annual[year] < annual[previous_year]:
                    break
                streak_no_cut += 1

    return {
        "history_basis": "calendar_year_observed_distributions",
        "annual_records": records,
        "latest_completed_year": latest_year if latest is not None else None,
        "consecutive_increase_years": streak_increase if status == "available" else None,
        "no_cut_years": streak_no_cut if status == "available" else None,
        "status": status,
        "updated_at": iso_now(),
        "source": "Yahoo Finance via yfinance",
    }


def contains_japanese(value: str) -> bool:
    return bool(re.search(r"[\u3040-\u30ff\u3400-\u9fff]", value))


def japanese_names(info: dict[str, Any]) -> dict[str, str | None]:
    short_name = str(info.get("shortName") or "").strip()
    long_name = str(info.get("longName") or "").strip()
    localized_short = short_name if contains_japanese(short_name) else ""
    localized_long = long_name if contains_japanese(long_name) else ""
    english_name = next((str(info.get(key) or "").strip()
                         for key in ("shortName", "longName", "displayName", "name")
                         if str(info.get(key) or "").strip() and not contains_japanese(str(info.get(key)).strip())), "")
    return {
        "short_name": localized_short or None,
        "long_name": localized_long or None,
        "english_name": english_name or None,
        "display_name": localized_short or localized_long or english_name or None,
    }


def frame_metric_map(frame: Any, allowed: dict[str, str]) -> dict[str, dict[str, Any]]:
    records: dict[str, dict[str, Any]] = {}
    if frame is None or getattr(frame, "empty", True):
        return records
    for period in frame.columns:
        period_date = date_value(period)
        if not period_date:
            continue
        fiscal_key = period_date.isoformat()
        values: dict[str, Any] = {}
        for source, target in allowed.items():
            if source not in frame.index:
                continue
            value = collector.json_safe(frame.loc[source, period])
            if value is not None:
                values[target] = value
        if values:
            records[fiscal_key] = values
    return records


def calculate_net_de_metrics(values: dict[str, Any]) -> dict[str, Any]:
    """Calculate net debt/equity only from complete inputs for one fiscal period."""
    equity_field = next((field for field in (
        "stockholders_equity", "total_equity_gross_minority_interest",
    ) if finite_number(values.get(field)) is not None
        and finite_number(values.get(field)) > 0), None)
    if equity_field is None:
        return {}

    net_debt = finite_number(values.get("net_debt"))
    debt_source = "net_debt"
    if net_debt is None:
        total_debt = finite_number(values.get("total_debt"))
        cash_field = next((field for field in (
            "cash_and_short_term_investments", "cash_and_equivalents",
            "cash_equivalents",
        ) if finite_number(values.get(field)) is not None), None)
        if total_debt is None or cash_field is None:
            return {}
        net_debt = total_debt - finite_number(values.get(cash_field))
        debt_source = f"total_debt_minus_{cash_field}"

    equity = finite_number(values.get(equity_field))
    return {
        "net_debt": net_debt,
        "net_debt_source_field": debt_source,
        "net_de_equity_source_field": equity_field,
        "net_de_ratio": net_debt / equity,
        "net_de_formula": f"{debt_source} / {equity_field}",
    }


def financial_metric_missing_reasons(
    *, fresh_balance: dict[str, Any], fresh_cash: dict[str, Any],
    retrieval_status: dict[str, Any], calculated_doe: float | None,
    net_de_metrics: dict[str, Any],
) -> dict[str, list[str]]:
    """Explain which fresh same-period source inputs prevent each metric."""
    reasons: dict[str, list[str]] = {"calculated_doe": [], "net_de_ratio": []}
    if calculated_doe is None:
        doe_reasons = reasons["calculated_doe"]
        if retrieval_status.get("cash_flow") == "failed":
            doe_reasons.append("cash_flow_fetch_failed")
        if retrieval_status.get("balance_sheet") == "failed":
            doe_reasons.append("balance_sheet_fetch_failed")
        if not any(finite_number(fresh_cash.get(field)) is not None for field in (
            "common_stock_dividend_paid", "cash_dividends_paid",
        )):
            doe_reasons.append("dividend_paid_missing")
        equity = finite_number(fresh_balance.get("stockholders_equity"))
        if equity is None:
            doe_reasons.append("stockholders_equity_missing")
        elif equity <= 0:
            doe_reasons.append("stockholders_equity_non_positive")

    if not net_de_metrics:
        net_reasons = reasons["net_de_ratio"]
        if retrieval_status.get("balance_sheet") == "failed":
            net_reasons.append("balance_sheet_fetch_failed")
        equity = next((finite_number(fresh_balance.get(field)) for field in (
            "stockholders_equity", "total_equity_gross_minority_interest",
        ) if finite_number(fresh_balance.get(field)) is not None
            and finite_number(fresh_balance.get(field)) > 0), None)
        if equity is None:
            if any(finite_number(fresh_balance.get(field)) is not None for field in (
                "stockholders_equity", "total_equity_gross_minority_interest",
            )):
                net_reasons.append("equity_non_positive")
            else:
                net_reasons.append("equity_missing")
        if finite_number(fresh_balance.get("net_debt")) is None:
            if finite_number(fresh_balance.get("total_debt")) is None:
                net_reasons.append("net_debt_and_total_debt_missing")
            elif not any(finite_number(fresh_balance.get(field)) is not None for field in (
                "cash_and_short_term_investments", "cash_and_equivalents", "cash_equivalents",
            )):
                net_reasons.append("cash_input_missing")
    return reasons


def merge_year_documents(
    client: Any,
    bucket: str,
    family: str,
    symbol: str,
    rows: list[dict[str, Any]],
    event_key: str,
) -> dict[str, list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
        event_date = str(row.get(event_key) or "")[:10]
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", event_date):
            continue
        grouped.setdefault(event_date[:4], []).append(row)

    output: dict[str, list[dict[str, Any]]] = {}
    safe = safe_symbol(symbol)
    for year, fresh in grouped.items():
        key = object_key("securities", safe, family, f"{year}.json")
        old = read_json(client, bucket, key) or {}
        records = collector.merge_dated(old.get("records", []), fresh, event_key)
        output[year] = records
        put_json_if_changed(client, bucket, key, {
            "schema_version": 1,
            "symbol": symbol,
            "year": int(year),
            "records": records,
            "updated_at": iso_now(),
            "source": "Yahoo Finance via yfinance",
        })
    return output


def load_all_year_records(client: Any, bucket: Any, symbol: str, family: str,
                          *, include_archive: bool = False) -> list[dict[str, Any]]:
    safe = safe_symbol(symbol)
    paginator = client.get_paginator("list_objects_v2")
    records: list[dict[str, Any]] = []
    prefixes = [object_key("securities", safe, family) + "/"]
    if include_archive:
        prefixes.append(object_key("archive", "events", safe, family) + "/")
    for prefix in prefixes:
        listed_prefixes = getattr(_R2_DOCUMENT_CACHE, "listed_prefixes", None)
        if listed_prefixes is not None:
            listed_prefixes.add((bucket, prefix))
        for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
            for item in page.get("Contents", []):
                document = read_json(client, bucket, item["Key"])
                if document:
                    records.extend(row for row in document.get("records", []) if isinstance(row, dict))
    return records


def load_year_records(client: Any, bucket: str, symbol: str, family: str,
                      years: set[int], *, include_archive: bool = True) -> list[dict[str, Any]]:
    safe = safe_symbol(symbol)
    records: list[dict[str, Any]] = []
    for year in sorted(years):
        keys = [object_key("securities", safe, family, f"{year}.json")]
        if include_archive:
            keys.append(object_key("archive", "events", safe, family, f"{year}.json"))
        for key in keys:
            document = read_json(client, bucket, key)
            if document:
                records.extend(row for row in document.get("records", []) if isinstance(row, dict))
    return records


def load_recent_year_records(client: Any, bucket: str, symbol: str, family: str, as_of: date) -> list[dict[str, Any]]:
    # Three calendar years cover a trailing 12-month period and the current
    # fiscal-year event window, including fiscal years that straddle January.
    # These years are newer than the 10-year archive cutoff, so archive keys
    # cannot contain valid rows and should not be requested on every daily run.
    years = set(range(as_of.year - 2, as_of.year + 1))
    return load_year_records(client, bucket, symbol, family, years, include_archive=False)


def preserve_profile_timestamp_if_unchanged(
    profile: dict[str, Any], previous: dict[str, Any],
) -> dict[str, Any]:
    """Avoid rewriting a stable profile just because its collection time changed."""
    previous_updated_at = previous.get("updated_at")
    if not previous_updated_at:
        return profile
    comparable_profile = {key: value for key, value in profile.items() if key != "updated_at"}
    comparable_previous = {key: value for key, value in previous.items() if key != "updated_at"}
    if comparable_profile == comparable_previous:
        profile["updated_at"] = previous_updated_at
    return profile


def action_partitions_need_persist(
    fresh_dividends: list[dict[str, Any]] | None,
    fresh_splits: list[dict[str, Any]] | None,
    legacy_dividends: list[dict[str, Any]],
    legacy_splits: list[dict[str, Any]],
) -> bool:
    # A no-change daily chart is only a verification signal. Keep existing
    # historical partitions intact instead of rewriting every year each day.
    return (
        fresh_dividends is not None
        or fresh_splits is not None
        or bool(legacy_dividends or legacy_splits)
    )


def series_records(
    series: Any,
    *,
    key: str,
    value_field: str = "amount",
    repaired_rows: Any = None,
) -> list[dict[str, Any]]:
    if series is None or not hasattr(series, "items"):
        return []
    rows = []
    for timestamp, value in series.items():
        event_date = date_value(timestamp)
        amount = finite_number(value)
        if event_date is None or amount is None or amount <= 0:
            continue
        record = {key: event_date.isoformat(), value_field: amount}
        if repaired_rows is not None:
            try:
                repaired = repaired_rows.get(timestamp, False)
            except (AttributeError, TypeError):
                repaired = False
            if repaired is True or str(repaired).strip().lower() == "true":
                record["row_repaired_by_yfinance"] = True
        rows.append(record)
    return rows


def fetch_repaired_action_history(ticker: Any, as_of: date, *, period: str = "max") -> tuple[
    list[dict[str, Any]], list[dict[str, Any]], date, date
]:
    """Fetch Yahoo's complete daily action history with yfinance's repair pass enabled."""
    count_yfinance_call(f"history_actions_{period}")
    frame = ticker.history(
        period=period, interval="1d", auto_adjust=False, actions=True,
        repair=True, keepna=True,
        timeout=float(os.environ.get("YFINANCE_HTTP_TIMEOUT_SECONDS", "30")),
    )
    if frame is None or frame.empty:
        raise RuntimeError("yfinance returned no daily action history")
    if "Dividends" not in frame.columns or "Stock Splits" not in frame.columns:
        raise RuntimeError("yfinance daily history omitted dividend or split action columns")
    dates = [date_value(value) for value in frame.index]
    dates = [value for value in dates if value is not None and value <= as_of]
    if not dates:
        raise RuntimeError("yfinance daily action history has no dates in the completed market period")
    coverage_start = min(dates)
    coverage_end = max(dates)
    repaired_rows = frame["Repaired?"] if "Repaired?" in frame.columns else None
    fresh_dividends = series_records(frame["Dividends"], key="date", repaired_rows=repaired_rows)
    fresh_splits = series_records(frame["Stock Splits"], key="date", value_field="ratio", repaired_rows=repaired_rows)
    fresh_dividends = [row for row in fresh_dividends if str(row["date"]) <= coverage_end.isoformat()]
    fresh_splits = [row for row in fresh_splits if str(row["date"]) <= coverage_end.isoformat()]
    return fresh_dividends, fresh_splits, coverage_start, coverage_end


def fetch_daily_history_with_actions(ticker: Any, as_of: date, *, period: str = "1y") -> tuple[
    list[dict[str, Any]], Any
]:
    """Fetch one daily chart containing both the current close and action columns."""
    count_yfinance_call(f"history_daily_{period}_with_actions")
    frame = ticker.history(
        period=period, interval="1d", auto_adjust=False, actions=True,
        repair=False, keepna=True, raise_errors=True,
        timeout=float(os.environ.get("YFINANCE_HTTP_TIMEOUT_SECONDS", "30")),
    )
    if frame is None or getattr(frame, "empty", True):
        raise RuntimeError("yfinance returned no daily price and action history")
    daily_rows = []
    for timestamp, row in frame.iterrows():
        row_date = date_value(timestamp)
        close = finite_number(row.get("Close"))
        if row_date is not None and row_date <= as_of and close is not None:
            daily_rows.append({"date": row_date.isoformat(), "close": close})
    return daily_rows, frame


def action_candidate_from_history(frame: Any, as_of: date) -> tuple[
    list[dict[str, Any]], list[dict[str, Any]], date, date
]:
    """Extract one-year action candidates from the already fetched daily chart."""
    if frame is None or getattr(frame, "empty", True):
        raise RuntimeError("yfinance returned no daily action history")
    if not hasattr(frame, "iterrows") or "Dividends" not in frame.columns or "Stock Splits" not in frame.columns:
        raise RuntimeError("yfinance daily history omitted dividend or split action columns")
    dates = [date_value(value) for value in frame.index]
    dates = [value for value in dates if value is not None and value <= as_of]
    if not dates:
        raise RuntimeError("yfinance daily history has no dates in the completed market period")
    coverage_start = min(dates)
    coverage_end = max(dates)
    dividends: list[dict[str, Any]] = []
    splits: list[dict[str, Any]] = []
    for timestamp, row in frame.iterrows():
        event_date = date_value(timestamp)
        if event_date is None or event_date < coverage_start or event_date > coverage_end:
            continue
        amount = finite_number(row.get("Dividends"))
        ratio = finite_number(row.get("Stock Splits"))
        if amount is not None and amount > 0:
            dividends.append({"date": event_date.isoformat(), "amount": amount})
        if ratio is not None and ratio > 0:
            splits.append({"date": event_date.isoformat(), "ratio": ratio})
    return dividends, splits, coverage_start, coverage_end


def fetch_action_event_candidate(ticker: Any, as_of: date, *, period: str = "1y") -> tuple[
    list[dict[str, Any]], list[dict[str, Any]], date, date
]:
    """Fetch chart action columns for change detection; repaired history remains authoritative."""
    _, frame = fetch_daily_history_with_actions(ticker, as_of, period=period)
    return action_candidate_from_history(frame, as_of)


def action_events_changed(
    candidate: list[dict[str, Any]], previous: list[dict[str, Any]], *,
    value_field: str, coverage_start: date, coverage_end: date,
) -> bool:
    def signature(rows: list[dict[str, Any]]) -> set[tuple[str, float]]:
        result = set()
        for row in rows:
            event_date = str(row.get("date") or "")[:10]
            value = finite_number(row.get(value_field))
            if coverage_start.isoformat() <= event_date <= coverage_end.isoformat() and value is not None and value > 0:
                result.add((event_date, round(value, 12)))
        return result

    return signature(candidate) != signature(previous)


def action_event_fingerprint(dividends: list[dict[str, Any]], splits: list[dict[str, Any]]) -> str:
    canonical = {
        "dividends": sorted(
            (str(row.get("date") or "")[:10], round(finite_number(row.get("amount")) or 0.0, 12))
            for row in dividends
        ),
        "splits": sorted(
            (str(row.get("date") or "")[:10], round(finite_number(row.get("ratio")) or 0.0, 12))
            for row in splits
        ),
    }
    return hashlib.sha256(json.dumps(canonical, separators=(",", ":")).encode("utf-8")).hexdigest()


def pending_action_repair_period(field_status: dict[str, Any], action_history: dict[str, Any]) -> str | None:
    if field_status.get("dividend_history") != "failed" and field_status.get("split_history") != "failed":
        return None
    period = action_history.get("repair_attempt_period")
    return period if period in {"1y", "max"} else None


def replace_dated_history(
    previous: list[dict[str, Any]], fresh: list[dict[str, Any]], key: str,
    coverage_start: date, coverage_end: date,
) -> list[dict[str, Any]]:
    """Replace all stored actions in a successfully fetched range, including removed/corrected events."""
    start_text = coverage_start.isoformat()
    end_text = coverage_end.isoformat()
    normalized_previous = normalize_event_rows(previous, key)
    normalized_fresh = normalize_event_rows(fresh, key)
    retained = [
        row for row in normalized_previous
        if str(row.get(key) or "")[:10] < start_text or str(row.get(key) or "")[:10] > end_text
    ]
    return collector.merge_dated(retained, normalized_fresh, key)


def has_duplicate_event_dates(rows: list[dict[str, Any]], key: str = "date") -> bool:
    """Detect repeated event dates that require rebuilding the partition from Yahoo."""
    seen: set[str] = set()
    for row in rows:
        event_date = str(row.get(key) or "")[:10]
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", event_date):
            continue
        if event_date in seen:
            return True
        seen.add(event_date)
    return False


def monthly_history_rows(ticker: Any, *, initial: bool, as_of: date) -> list[dict[str, Any]]:
    first_year, latest_year = year_end_price_years(as_of)
    if initial:
        count_yfinance_call("history_year_end_prices_initial_8y")
        frame = ticker.history(
            start=date(first_year, 12, 1), end=date(latest_year + 1, 1, 1),
            # Yahoo's monthly Close is already split-adjusted. yfinance's
            # monthly repair resampler requires a Stock Splits column even
            # when actions=False, and can raise KeyError for valid symbols.
            interval="1mo", auto_adjust=False, actions=False, repair=False,
            timeout=float(os.environ.get("YFINANCE_HTTP_TIMEOUT_SECONDS", "30")),
        )
    else:
        count_yfinance_call("history_year_end_price_incremental_5d")
        frame = ticker.history(
            period="5d", interval="1d", auto_adjust=False, actions=False, repair=True,
            timeout=float(os.environ.get("YFINANCE_HTTP_TIMEOUT_SECONDS", "30")),
        )
    latest_by_month: dict[str, dict[str, Any]] = {}
    if frame is None or frame.empty:
        return []
    for timestamp, row in frame.iterrows():
        point_date = date_value(timestamp)
        close = finite_number(row.get("Close"))
        if point_date and close and close > 0 and point_date.month == 12 and first_year <= point_date.year <= latest_year:
            month = f"{point_date.year}-12"
            point = {"month": month, "date": last_xtks_session_in_december(point_date.year).isoformat(), "close": close}
            if month not in latest_by_month or point["date"] > latest_by_month[month]["date"]:
                latest_by_month[month] = point
    return [latest_by_month[month] for month in sorted(latest_by_month)]


def completed_close_for_session(daily: list[dict[str, Any]], session_date: date) -> dict[str, Any]:
    """Require Yahoo's daily Close for this XTKS session; never publish a stale quote as today's."""
    matches = [
        row for row in daily
        if str(row.get("date") or "")[:10] == session_date.isoformat()
        and (finite_number(row.get("close")) or 0) > 0
    ]
    if not matches:
        raise RuntimeError(f"yfinance returned no closing price for Japan session {session_date.isoformat()}")
    return matches[-1]


def current_close_with_fallback(fetch_daily: Any, previous_price: dict[str, Any], session_date: date
                                ) -> tuple[dict[str, Any], str, str | None]:
    try:
        return completed_close_for_session(fetch_daily(), session_date), "available", None
    except Exception as exc:
        return {
            "date": previous_price.get("date"),
            "close": previous_price.get("close"),
        }, "failed", f"{type(exc).__name__}: {exc}"


def has_new_split(
    fresh_splits: list[dict[str, Any]], prior_splits: list[dict[str, Any]],
) -> bool:
    """Detect a newly reported event or changed ratio, independent of prior fetch dates."""
    previous = {
        str(row.get("date") or "")[:10]: finite_number(row.get("ratio"))
        for row in prior_splits if row.get("date")
    }
    return any(
        (event_date := str(row.get("date") or "")[:10])
        and (event_date not in previous or finite_number(row.get("ratio")) != previous[event_date])
        for row in fresh_splits
    )


def year_end_price_refresh_required(
    *, previous_monthly: bool, refresh_monthly: bool, initialize_monthly: bool,
    retry_monthly: bool, new_split_detected: bool,
) -> bool:
    return (
        (previous_monthly and refresh_monthly)
        or (not previous_monthly and initialize_monthly)
        or retry_monthly
        or new_split_detected
    )


def financial_period_marker(info: dict[str, Any]) -> str | None:
    markers = {
        key: marker.isoformat() if (marker := date_value(info.get(key))) else None
        for key in ("lastFiscalYearEnd", "mostRecentQuarter")
    }
    if not any(markers.values()):
        return None
    return json.dumps(markers, sort_keys=True, separators=(",", ":"))


def financial_failed_groups(field_status: dict[str, Any]) -> set[str]:
    return {
        group for group in FINANCIAL_GROUPS
        if field_status.get(f"financial_{group}") == "failed"
    }


def same_session_partial_retry(prior_sync: dict[str, Any], as_of: date) -> bool:
    return (
        prior_sync.get("status") in {"partial", "running"}
        and prior_sync.get("retry_session_date") == as_of.isoformat()
        and prior_sync.get("last_success_session_date") == as_of.isoformat()
    )


def skip_same_session_no_data_retry(prior_sync: dict[str, Any], as_of: date) -> bool:
    return (
        prior_sync.get("failure_class") == "no_data"
        and prior_sync.get("retry_session_date") == as_of.isoformat()
        and prior_sync.get("status") in {"failed", "running"}
    )


def financial_groups_to_fetch(
    *, same_session_retry: bool, retry_pending: bool, due: bool, failed_groups: set[str],
) -> set[str]:
    if same_session_retry:
        groups = set(failed_groups)
    elif retry_pending:
        groups = set(failed_groups) or set(FINANCIAL_GROUPS)
    else:
        groups = set(FINANCIAL_GROUPS) if due else set()
    # DOE must use balance and dividends from the same fresh collection attempt.
    if groups & {"balance_sheet", "cash_flow"}:
        groups.update({"balance_sheet", "cash_flow"})
    return groups


def financial_refresh_due(
    *, info: dict[str, Any], prior_sync: dict[str, Any], as_of: date,
    initialize_due: bool, retry_pending: bool,
) -> tuple[bool, str | None, bool]:
    marker = financial_period_marker(info)
    previous_marker = prior_sync.get("financial_period_marker")
    initialized = prior_sync.get("financials_initialized") is True
    marker_changed = bool(initialized and marker and previous_marker and marker != previous_marker)
    last_checked = date_value(prior_sync.get("financials_last_checked_session_date"))
    stale = bool(last_checked and as_of >= last_checked + timedelta(days=FINANCIAL_REFRESH_MAX_AGE_DAYS))
    return initialize_due or retry_pending or marker_changed or stale, marker, marker_changed


def should_refresh_dividend_calendar(
    *, as_of: date, fetched_on: date | None, ex_dividend_date: date | None,
    last_attempt_session_date: date | None, last_attempt_status: str, status: str,
) -> bool:
    days_until_ex_date = (ex_dividend_date - as_of).days if ex_dividend_date is not None else None
    refresh_interval_days = (
        CALENDAR_NEAR_EVENT_REFRESH_DAYS
        if days_until_ex_date is not None
        and 0 <= days_until_ex_date <= CALENDAR_NEAR_EVENT_WINDOW_DAYS
        else CALENDAR_REFRESH_MAX_AGE_DAYS
    )
    failed = last_attempt_status == "failed" or status == "failed"
    failed_this_session = (
        failed
        and last_attempt_session_date is not None
        and last_attempt_session_date >= as_of
    )
    if failed_this_session:
        return False
    return (
        fetched_on is None
        or failed
        or (as_of - fetched_on).days >= refresh_interval_days
    )


def read_monthly_points(client: Any, bucket: str, symbol: str, year: int) -> list[dict[str, Any]]:
    safe = safe_symbol(symbol)
    points: list[dict[str, Any]] = []
    for key in (
        object_key("archive", "prices", "monthly", safe, f"{year}.json"),
        object_key("securities", safe, "prices", "monthly", f"{year}.json"),
    ):
        document = read_json(client, bucket, key)
        if document:
            points.extend(row for row in document.get("points", []) if isinstance(row, dict))
    return points


def list_object_keys(client: Any, bucket: str, prefixes: tuple[str, ...]) -> set[str]:
    keys: set[str] = set()
    paginator = client.get_paginator("list_objects_v2")
    for prefix in prefixes:
        listed_prefixes = getattr(_R2_DOCUMENT_CACHE, "listed_prefixes", None)
        if listed_prefixes is not None:
            listed_prefixes.add((bucket, prefix))
        for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
            keys.update(
                str(item["Key"])
                for item in page.get("Contents", [])
                if isinstance(item, dict) and item.get("Key")
            )
    return keys


def normalize_december_price_point(point: dict[str, Any]) -> tuple[dict[str, Any], bool]:
    return normalize_monthly_price_point(point)


def save_monthly_prices(
    client: Any,
    bucket: str,
    symbol: str,
    fresh: list[dict[str, Any]],
    *,
    as_of: date,
) -> None:
    safe = safe_symbol(symbol)
    month_index = as_of.year * 12 + as_of.month - 1 - 12
    cutoff_hot = date(month_index // 12, month_index % 12 + 1, 1)
    first_year, latest_year = year_end_price_years(as_of)
    monthly_prefixes = (
        object_key("archive", "prices", "monthly", safe) + "/",
        object_key("securities", safe, "prices", "monthly") + "/",
    )
    existing_keys = list_object_keys(client, bucket, monthly_prefixes)
    # Keep only the eight most recent completed December closes. A close from
    # the latest 13 months stays hot; older retained year-end prices are archived.
    for year in range(as_of.year - 10, as_of.year + 1):
        hot_key = object_key("securities", safe, "prices", "monthly", f"{year}.json")
        archive_key = object_key("archive", "prices", "monthly", safe, f"{year}.json")
        for key in (hot_key, archive_key):
            if key not in existing_keys:
                cache_object(client, bucket, key, None)
        points: list[dict[str, Any]] = []
        for key in (archive_key, hot_key):
            if key not in existing_keys:
                continue
            document = read_json(client, bucket, key)
            if document:
                points.extend(row for row in document.get("points", []) if isinstance(row, dict))
        points.extend(point for point in fresh if point["month"].startswith(f"{year}-"))
        if not points:
            continue
        unique = {str(item.get("month")): item for item in points if item.get("month")}
        merged = sorted(unique.values(), key=lambda item: item["month"])
        hot: list[dict[str, Any]] = []
        archived: list[dict[str, Any]] = []
        for point in merged:
            month = str(point["month"])
            if not re.fullmatch(r"\d{4}-12", month):
                continue
            point_year = int(month[:4])
            if point_year < first_year or point_year > latest_year:
                continue
            point, _ = normalize_december_price_point(point)
            month_date = date.fromisoformat(f"{month}-01")
            if month_date < cutoff_hot:
                archived.append(point)
            else:
                hot.append(point)
        if hot:
            put_json_if_changed(client, bucket, hot_key, {
                "schema_version": 1, "symbol": symbol, "year": year, "points": hot,
                "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
            })
            existing_keys.add(hot_key)
        if archived:
            archive_document = {
                "schema_version": 1, "symbol": symbol, "year": year, "points": archived,
                "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
            }
            put_json_if_changed(client, bucket, archive_key, archive_document)
            saved = read_json(client, bucket, archive_key)
            if not saved or {item.get("month") for item in saved.get("points", [])} != {item.get("month") for item in archived}:
                raise RuntimeError(f"Monthly price archive read-back failed: {archive_key}")
            existing_keys.add(archive_key)
        if not hot and hot_key in existing_keys:
            client.delete_object(Bucket=bucket, Key=hot_key)
            cache_object(client, bucket, hot_key, None)
            existing_keys.discard(hot_key)
        if not archived and archive_key in existing_keys:
            client.delete_object(Bucket=bucket, Key=archive_key)
            cache_object(client, bucket, archive_key, None)
            existing_keys.discard(archive_key)

    # Remove year partitions that are no longer part of the rolling eight-year window.
    expired_year = as_of.year - 10
    for key in existing_keys:
        match = re.search(r"/(\d{4})\.json$", key)
        if match and int(match.group(1)) < expired_year:
            client.delete_object(Bucket=bucket, Key=key)
            cache_object(client, bucket, key, None)


def fiscal_year_events(events: list[dict[str, Any]], info: dict[str, Any], as_of: date) -> float | None:
    last_end = date_value(info.get("lastFiscalYearEnd"))
    next_end = date_value(info.get("nextFiscalYearEnd"))
    if not last_end or not next_end or next_end <= last_end:
        start = date(as_of.year, 1, 1)
        end = date(as_of.year, 12, 31)
    else:
        start = last_end + timedelta(days=1)
        end = next_end
    total = sum(
        finite_number(row.get("amount")) or 0.0
        for row in events
        if start.isoformat() <= str(row.get("date") or "")[:10] <= min(end, as_of).isoformat()
    )
    return round(total, 8) if total > 0 else None


def legacy_frame_map(old: dict[str, Any], section: str, allowed: dict[str, str]) -> dict[str, dict[str, Any]]:
    output: dict[str, dict[str, Any]] = {}
    records = ((old.get("financials") or {}).get(section) or {}).get("yearly", [])
    for record in records if isinstance(records, list) else []:
        period_end = date_value(record.get("period_end")) if isinstance(record, dict) else None
        values = record.get("values", {}) if isinstance(record, dict) else {}
        if not period_end or not isinstance(values, dict):
            continue
        mapped = {}
        for source, target in allowed.items():
            value = finite_number(values.get(source))
            if value is not None:
                mapped[target] = value
        if mapped:
            output[period_end.isoformat()] = mapped
    return output


def legacy_valuation_map(old: dict[str, Any]) -> dict[str, dict[str, Any]]:
    output: dict[str, dict[str, Any]] = {}
    records = (old.get("valuation_history") or {}).get("yearly", [])
    sources = {"EPS": "eps_adjusted", "Book Value": "bps_adjusted", "Dividend Yield": "dividend_yield_adjusted"}
    for record in records if isinstance(records, list) else []:
        period_end = date_value(record.get("period_end")) if isinstance(record, dict) else None
        values = record.get("values", {}) if isinstance(record, dict) else {}
        if not period_end or not isinstance(values, dict):
            continue
        mapped = {target: value for source, target in sources.items()
                  if (value := finite_number(values.get(source))) is not None}
        if mapped:
            output[period_end.isoformat()] = mapped
    return output


def prior_financial_maps(
    client: Any, bucket: str, symbol: str,
) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]], dict[str, str], dict[str, dict[str, Any]]]:
    safe = safe_symbol(symbol)
    prefix = object_key("securities", safe, "financials") + "/"
    statements: dict[str, dict[str, Any]] = {}
    valuations: dict[str, dict[str, Any]] = {}
    document_keys: dict[str, str] = {}
    records: dict[str, dict[str, Any]] = {}
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=prefix):
        for item in page.get("Contents", []):
            document_key = str(item.get("Key") or "")
            document = read_json(client, bucket, document_key)
            annual = document.get("annual") if document else None
            annual_period_end = str(annual.get("period_end") or "")[:10] if isinstance(annual, dict) else ""
            if re.fullmatch(r"\d{4}-\d{2}-\d{2}", annual_period_end):
                records.setdefault(annual_period_end, {})["annual"] = annual
                statements[annual_period_end] = {
                    key: value for key, value in annual.items()
                    if key not in {"fiscal_year", "period_end", "field_status", "field_status_reason", "migration_source", "source_values"}
                }
                document_keys.setdefault(annual_period_end, document_key)
            valuation = document.get("valuation")
            if isinstance(valuation, dict):
                valuation_period_end = str(valuation.get("period_end") or annual_period_end)[:10]
                if re.fullmatch(r"\d{4}-\d{2}-\d{2}", valuation_period_end):
                    records.setdefault(valuation_period_end, {})["valuation"] = valuation
                    valuations[valuation_period_end] = {
                        key: value for key, value in valuation.items()
                        if key not in {"fiscal_year", "period_end", "field_status", "field_status_reason", "migration_source", "source_values"}
                    }
                    document_keys.setdefault(valuation_period_end, document_key)
    return statements, valuations, document_keys, records


FINANCIAL_RECORD_METADATA = {"fiscal_year", "period_end", "field_status", "field_status_reason", "migration_source", "source_values"}
FINANCIAL_DERIVED_FIELDS = {"equity_ratio_computed"}
IRBANK_ANNUAL_FIELD_UNITS = {
    "sales": "million_jpy",
    "operating_profit": "million_jpy",
    "operating_cf": "million_jpy",
    "investing_cf": "million_jpy",
    "total_assets": "million_jpy",
    "equity_ratio_computed": "percent",
}
IRBANK_VALUATION_FIELD_UNITS = {
    "eps_adjusted": "jpy_per_share",
    "bps_adjusted": "jpy_per_share",
    "dividend_yield_adjusted": "percent",
}
YFINANCE_FINANCIAL_MONEY_FIELDS = (
    set(INCOME_FIELDS.values()) | set(BALANCE_FIELDS.values()) | set(CASH_FLOW_FIELDS.values())
    | {"doe_dividend_total"}
)


def source_field_units(values: dict[str, Any], source: Any, existing: Any,
                       known_units: dict[str, str]) -> dict[str, str]:
    units = dict(existing) if isinstance(existing, dict) else {}
    if "irbank" in str(source or "").lower():
        for field, unit in known_units.items():
            if field in values:
                units[field] = unit
    return units


def equity_ratio_result(seed_values: dict[str, Any], yfinance_values: dict[str, Any]) -> dict[str, Any]:
    """Calculate equity ratio only from inputs with a shared source and unit basis."""
    equity_field = next((
        field for field in ("stockholders_equity", "total_equity_gross_minority_interest")
        if finite_number(yfinance_values.get(field)) is not None
    ), None)
    equity_source = "yfinance" if equity_field else None
    if equity_field is None:
        equity_field = next((
            field for field in ("stockholders_equity", "total_equity_gross_minority_interest")
            if finite_number(seed_values.get(field)) is not None
        ), None)
        equity_source = "seed" if equity_field else None

    if finite_number(yfinance_values.get("total_assets")) is not None:
        assets_source = "yfinance"
        assets = finite_number(yfinance_values.get("total_assets"))
    else:
        assets_source = "seed" if finite_number(seed_values.get("total_assets")) is not None else None
        assets = finite_number(seed_values.get("total_assets"))

    equity_values = yfinance_values if equity_source == "yfinance" else seed_values
    equity = finite_number(equity_values.get(equity_field)) if equity_field else None
    ratio_out_of_range = False
    if equity is not None and assets is not None and assets > 0 and equity_source == assets_source:
        calculated_ratio = equity / assets * 100
        if math.isfinite(calculated_ratio) and abs(calculated_ratio) <= 1000:
            return {
                "value": calculated_ratio,
                "source": "calculated",
                "input_sources": {"equity": equity_source, "total_assets": assets_source},
                "reason": None,
            }
        ratio_out_of_range = True

    # IRBANK seed balance-sheet amounts are stored in million JPY, while
    # yfinance balance-sheet amounts are JPY. Keep the seed's same-source
    # ratio when one input has been refreshed from a different provider.
    seed_ratio = finite_number(seed_values.get("equity_ratio_computed"))
    if seed_ratio is not None and abs(seed_ratio) <= 1000:
        return {
            "value": seed_ratio,
            "source": "seed",
            "input_sources": {"equity": "seed", "total_assets": "seed"},
            "reason": "mixed_source_inputs_not_recalculated",
        }
    return {
        "value": None,
        "source": None,
        "input_sources": {"equity": equity_source, "total_assets": assets_source},
        "reason": "equity_ratio_out_of_range" if ratio_out_of_range
        else "mixed_source_inputs_not_comparable" if equity_source and assets_source else "required_input_missing",
    }


def financial_source_values(record: Any) -> tuple[dict[str, Any], dict[str, Any]]:
    """Read retained seed values and yfinance values without collapsing provenance."""
    if not isinstance(record, dict):
        return {}, {}
    explicit = record.get("source_values")
    seed_record = explicit.get("seed") if isinstance(explicit, dict) else None
    yfinance_record = explicit.get("yfinance") if isinstance(explicit, dict) else None
    seed = dict(seed_record.get("values") or {}) if isinstance(seed_record, dict) else {}
    yfinance = dict(yfinance_record.get("values") or {}) if isinstance(yfinance_record, dict) else {}
    statuses = record.get("field_status") if isinstance(record.get("field_status"), dict) else {}
    fields = {key: value for key, value in record.items() if key not in FINANCIAL_RECORD_METADATA}
    for field, value in fields.items():
        if field in seed or field in yfinance:
            continue
        if field in FINANCIAL_DERIVED_FIELDS:
            continue
        if statuses.get(field) == "available" and not record.get("migration_source"):
            yfinance.setdefault(field, value)
        else:
            seed.setdefault(field, value)
    return seed, yfinance


def collect_financials(ticker: Any, old: dict[str, Any], client: Any, bucket: str, symbol: str,
                       *, fetch_groups: set[str] | None = None,
                       previous_status: dict[str, Any] | None = None
                       ) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any], dict[str, str]]:
    retrieval_status: dict[str, Any] = {}
    requested_groups = set(FINANCIAL_GROUPS) if fetch_groups is None else set(fetch_groups)
    previous_status = previous_status or {}

    def fetch_metrics(name: str, request: Any, fields: dict[str, str]) -> dict[str, dict[str, Any]]:
        try:
            result = frame_metric_map(request(), fields)
            retrieval_status[name] = "available" if result else "empty"
            return result
        except Exception as exc:
            retrieval_status[name] = "failed"
            print(f"Financial field {name} unavailable: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
            return {}

    fresh_income = fetch_metrics("income_statement", lambda: (count_yfinance_call("income_statement_yearly"), ticker.get_income_stmt(freq="yearly"))[1], INCOME_FIELDS) if "income_statement" in requested_groups else {}
    fresh_balance = fetch_metrics("balance_sheet", lambda: (count_yfinance_call("balance_sheet_yearly"), ticker.get_balance_sheet(freq="yearly"))[1], BALANCE_FIELDS) if "balance_sheet" in requested_groups else {}
    fresh_cash = fetch_metrics("cash_flow", lambda: (count_yfinance_call("cash_flow_yearly"), ticker.get_cash_flow(freq="yearly"))[1], CASH_FLOW_FIELDS) if "cash_flow" in requested_groups else {}
    for group in FINANCIAL_GROUPS:
        if group not in requested_groups:
            retrieval_status[group] = previous_status.get(f"financial_{group}", "not_scheduled")
    legacy_income = legacy_frame_map(old, "income_statement", INCOME_FIELDS)
    legacy_balance = legacy_frame_map(old, "balance_sheet", BALANCE_FIELDS)
    legacy_cash = legacy_frame_map(old, "cash_flow", CASH_FLOW_FIELDS)
    prior_v2, prior_v2_valuation, financial_document_keys, prior_financial_records = prior_financial_maps(client, bucket, symbol)
    # valuation_measures contains valuation multiples, not split-adjusted fiscal
    # EPS/BPS or dividend yield. Do not infer these from unrelated TTM values or
    # unadjusted statement EPS. Retain historical values with explicit stale status.
    unsupported_valuation_fields = (
        "eps_adjusted", "bps_adjusted", "dividend_yield_adjusted",
    )
    retrieval_status["valuation"] = "unsupported"
    valuation: dict[str, dict[str, Any]] = {}

    legacy_valuation = legacy_valuation_map(old)
    fresh_groups = {
        "income_statement": (fresh_income, INCOME_FIELDS),
        "balance_sheet": (fresh_balance, BALANCE_FIELDS),
        "cash_flow": (fresh_cash, CASH_FLOW_FIELDS),
    }
    field_retrieval_status: dict[str, str] = {}
    for source, (records, allowed) in fresh_groups.items():
        for field in set(allowed.values()):
            if retrieval_status[source] == "failed":
                field_retrieval_status[field] = "failed"
            elif any(field in values for values in records.values()):
                field_retrieval_status[field] = "available"
            else:
                field_retrieval_status[field] = "empty"

    field_retrieval_status.update({field: "unsupported" for field in unsupported_valuation_fields})

    keys = sorted(set(fresh_income) | set(fresh_balance) | set(fresh_cash) | set(valuation)
                  | set(legacy_income) | set(legacy_balance) | set(legacy_cash) | set(legacy_valuation)
                  | set(prior_v2) | set(prior_v2_valuation))[-10:]
    statements: list[dict[str, Any]] = []
    valuation_rows: list[dict[str, Any]] = []
    for period_end in keys:
        prior_record = prior_financial_records.get(period_end, {})
        seed_values, prior_yfinance_values = financial_source_values(prior_record.get("annual"))
        legacy_yfinance_values = {**legacy_income.get(period_end, {}), **legacy_balance.get(period_end, {}),
                                  **legacy_cash.get(period_end, {})}
        seed_values = {**seed_values}
        yfinance_values = {**prior_yfinance_values, **legacy_yfinance_values}
        fresh_values = {**fresh_income.get(period_end, {}), **fresh_balance.get(period_end, {}), **fresh_cash.get(period_end, {})}
        yfinance_values.update(fresh_values)
        values = {**seed_values, **yfinance_values}
        net_de_metrics = calculate_net_de_metrics(fresh_balance.get(period_end, {}))
        if net_de_metrics:
            yfinance_values.update(net_de_metrics)
            values.update(net_de_metrics)
        equity_ratio = equity_ratio_result(seed_values, yfinance_values)
        if equity_ratio["value"] is not None:
            values["equity_ratio_computed"] = equity_ratio["value"]
        else:
            values.pop("equity_ratio_computed", None)
        fresh_doe_dividend_values = fresh_cash.get(period_end, {})
        doe_dividend_field = (
            "common_stock_dividend_paid"
            if finite_number(fresh_doe_dividend_values.get("common_stock_dividend_paid")) is not None
            else "cash_dividends_paid"
        )
        doe_dividend_total = finite_number(fresh_doe_dividend_values.get(doe_dividend_field))
        fresh_doe_dividend = doe_dividend_total is not None
        fresh_stockholders_equity = finite_number(
            fresh_balance.get(period_end, {}).get("stockholders_equity")
        )
        calculated_doe = None
        if (fresh_doe_dividend and fresh_stockholders_equity is not None
                and fresh_stockholders_equity > 0 and doe_dividend_total is not None):
            calculated_doe = abs(doe_dividend_total) / fresh_stockholders_equity * 100
            values["calculated_doe"] = calculated_doe
            values["doe_dividend_total"] = abs(doe_dividend_total)
            values["doe_dividend_source_field"] = doe_dividend_field
            values["doe_formula"] = "dividend_total_paid / period_end_stockholders_equity * 100"
            yfinance_values.update({
                "calculated_doe": calculated_doe,
                "doe_dividend_total": abs(doe_dividend_total),
                "doe_dividend_source_field": doe_dividend_field,
                "doe_formula": "dividend_total_paid / period_end_stockholders_equity * 100",
            })
        annual_field_status = {
            field: "available" if field in fresh_values or field in net_de_metrics
            else "stale" if field in yfinance_values
            else "legacy_migrated" if field in seed_values
            else field_retrieval_status.get(field, "empty")
            for source, fields in (("income_statement", INCOME_FIELDS), ("balance_sheet", BALANCE_FIELDS), ("cash_flow", CASH_FLOW_FIELDS))
            for field in fields.values()
        }
        if calculated_doe is not None:
            annual_field_status["calculated_doe"] = "available"
        elif finite_number(yfinance_values.get("calculated_doe", seed_values.get("calculated_doe"))) is not None:
            annual_field_status["calculated_doe"] = "stale"
        elif any(retrieval_status.get(group) == "failed" for group in ("balance_sheet", "cash_flow")):
            annual_field_status["calculated_doe"] = "failed"
        else:
            annual_field_status["calculated_doe"] = "empty"
        if net_de_metrics:
            annual_field_status["net_debt"] = "available"
            annual_field_status["net_de_ratio"] = "available"
        elif finite_number(yfinance_values.get("net_de_ratio", seed_values.get("net_de_ratio"))) is not None:
            annual_field_status["net_de_ratio"] = "stale"
        elif retrieval_status.get("balance_sheet") == "failed":
            annual_field_status["net_de_ratio"] = "failed"
        else:
            annual_field_status["net_de_ratio"] = "empty"
        if "equity_ratio_computed" in values:
            annual_field_status["equity_ratio_computed"] = (
                "legacy_migrated" if equity_ratio["input_sources"] == {"equity": "seed", "total_assets": "seed"}
                else "available" if ("stockholders_equity" in fresh_values or "total_equity_gross_minority_interest" in fresh_values)
                and "total_assets" in fresh_values
                else "stale"
            )
        annual_field_status_reason = financial_metric_missing_reasons(
            fresh_balance=fresh_balance.get(period_end, {}),
            fresh_cash=fresh_cash.get(period_end, {}),
            retrieval_status=retrieval_status,
            calculated_doe=calculated_doe,
            net_de_metrics=net_de_metrics,
        )
        if equity_ratio["reason"]:
            annual_field_status_reason["equity_ratio_computed"] = [equity_ratio["reason"]]
        # Flat-row consumers calculate margins without consulting provenance.
        # Preserve raw seed amounts, but never mix their units with Yahoo amounts.
        margin_fields = ("sales", "operating_profit", "operating_cf", "investing_cf")
        if any(finite_number(yfinance_values.get(field)) is not None for field in margin_fields):
            for field in margin_fields:
                if field in seed_values and finite_number(yfinance_values.get(field)) is None:
                    values[field] = None
                    annual_field_status[field] = "unsupported"
                    annual_field_status_reason[field] = ["mixed_source_units_not_comparable"]
        prior_annual = prior_record.get("annual") if isinstance(prior_record.get("annual"), dict) else {}
        prior_sources = prior_annual.get("source_values")
        prior_seed = prior_sources.get("seed") if isinstance(prior_sources, dict) else None
        prior_seed = prior_seed if isinstance(prior_seed, dict) else {}
        seed_source = (
            (prior_annual.get("source_values") or {}).get("seed", {}).get("source")
            if isinstance(prior_annual.get("source_values"), dict) else None
        ) or ("IRBANK migration seed" if prior_annual.get("migration_source") else "existing v2 snapshot")
        prior_seed_units = prior_seed.get("field_units")
        seed_field_units = source_field_units(
            seed_values, seed_source, prior_seed_units, IRBANK_ANNUAL_FIELD_UNITS,
        )
        yfinance_field_units = {
            field: "jpy" for field in yfinance_values if field in YFINANCE_FINANCIAL_MONEY_FIELDS
        }
        annual_sources = {
            "seed": {
                **prior_seed,
                "source": seed_source,
                "values": seed_values,
                "field_units": seed_field_units,
            },
            "yfinance": {
                "source": "Yahoo Finance via yfinance",
                "values": yfinance_values,
                "field_units": yfinance_field_units,
                "field_status": {field: "available" if field in fresh_values or field in net_de_metrics else "stale" for field in yfinance_values},
            },
            "calculated": {
                "values": {
                    "equity_ratio_computed": {
                        "value": values["equity_ratio_computed"],
                        "formula": "equity / total_assets * 100",
                        "input_sources": equity_ratio["input_sources"],
                    }
                } if "equity_ratio_computed" in values and equity_ratio["source"] == "calculated" else {},
            },
        }
        statements.append({
            "fiscal_year": f"{period_end[:4]}/{period_end[5:7]}",
            "period_end": period_end,
            "field_status": annual_field_status,
            "field_status_reason": annual_field_status_reason,
            "source_values": annual_sources,
            **values,
        })
        fresh_valuation = valuation.get(period_end, {})
        prior_valuation = prior_record.get("valuation")
        seed_valuation, prior_yfinance_valuation = financial_source_values(prior_valuation)
        yfinance_valuation_values = {**prior_yfinance_valuation, **legacy_valuation.get(period_end, {}), **fresh_valuation}
        valuation_values = {**seed_valuation, **yfinance_valuation_values}
        if valuation_values or "valuation" in requested_groups:
            prior_valuation_sources = prior_valuation.get("source_values") if isinstance(prior_valuation, dict) else None
            prior_valuation_seed = prior_valuation_sources.get("seed") if isinstance(prior_valuation_sources, dict) else None
            prior_valuation_seed = prior_valuation_seed if isinstance(prior_valuation_seed, dict) else {}
            valuation_source = (
                prior_valuation_seed.get("source")
            ) or ("IRBANK migration seed" if isinstance(prior_valuation, dict) and prior_valuation.get("migration_source") else "existing v2 snapshot")
            valuation_seed_units = source_field_units(
                seed_valuation, valuation_source, prior_valuation_seed.get("field_units"),
                IRBANK_VALUATION_FIELD_UNITS,
            )
            valuation_yfinance_units = {
                field: "jpy_per_share" for field in yfinance_valuation_values
                if field in {"eps_adjusted", "bps_adjusted"}
            }
            if "dividend_yield_adjusted" in yfinance_valuation_values:
                valuation_yfinance_units["dividend_yield_adjusted"] = "percent"
            valuation_seed_record = {
                **prior_valuation_seed,
                "source": valuation_source,
                "values": seed_valuation,
                "field_units": valuation_seed_units,
            }
            valuation_rows.append({
                "fiscal_year": f"{period_end[:4]}/{period_end[5:7]}",
                "period_end": period_end,
                "field_status": {
                    field: "stale" if field in valuation_values else "unsupported"
                    for field in set(valuation_values) | set(unsupported_valuation_fields)
                },
                "field_status_reason": {
                    field: ["split_adjusted_fiscal_source_unavailable"]
                    for field in unsupported_valuation_fields
                },
                "source_values": {
                    "seed": valuation_seed_record,
                    "yfinance": {
                        "source": "Yahoo Finance via yfinance",
                        "values": yfinance_valuation_values,
                        "field_units": valuation_yfinance_units,
                        "field_status": {field: "available" if field in fresh_valuation else "stale" for field in yfinance_valuation_values},
                    },
                },
                **valuation_values,
            })
    retrieval_status["fields"] = field_retrieval_status
    retrieval_status["requested_groups"] = sorted(requested_groups)
    retrieval_status["legacy_source"] = "migrated" if any((legacy_income, legacy_balance, legacy_cash, legacy_valuation)) else "not_found"
    return statements, valuation_rows, retrieval_status, financial_document_keys


def legacy_document(client: Any, bucket: str, symbol: str) -> dict[str, Any]:
    safe = safe_symbol(symbol)
    raw = read_object(client, bucket, f"{OLD_ROOT}/securities/{safe}.json")
    if not raw:
        return {}
    value = json.loads(raw)
    return value if isinstance(value, dict) else {}


def legacy_unused_fields(old: dict[str, Any]) -> dict[str, Any]:
    fields: dict[str, Any] = {}
    profile = old.get("profile") if isinstance(old.get("profile"), dict) else {}
    profile_extra = {key: value for key, value in profile.items() if key not in PROFILE_FIELDS and key not in {"shortName", "longName"}}
    if profile_extra:
        fields["profile"] = profile_extra
    statistics = old.get("statistics_current") if isinstance(old.get("statistics_current"), dict) else {}
    statistics_extra = {key: value for key, value in statistics.items() if key not in CURRENT_FIELDS}
    if statistics_extra:
        fields["statistics_current"] = statistics_extra

    prices = old.get("price_history") if isinstance(old.get("price_history"), dict) else {}
    unused_prices = {interval: rows for interval, rows in prices.items() if interval != "1mo" and isinstance(rows, list) and rows}
    if unused_prices:
        fields["price_history"] = unused_prices

    unused_financials: dict[str, Any] = {}
    for section, allowed in (("income_statement", INCOME_FIELDS), ("balance_sheet", BALANCE_FIELDS), ("cash_flow", CASH_FLOW_FIELDS)):
        frequencies = ((old.get("financials") or {}).get(section) or {})
        for frequency, records in frequencies.items():
            if not isinstance(records, list):
                continue
            if frequency != "yearly":
                if records:
                    unused_financials.setdefault(section, {})[frequency] = records
                continue
            extras = []
            for record in records:
                values = record.get("values", {}) if isinstance(record, dict) else {}
                extra_values = {key: value for key, value in values.items() if key not in allowed}
                if extra_values:
                    extras.append({**record, "values": extra_values})
            if extras:
                unused_financials.setdefault(section, {})[frequency] = extras
    if unused_financials:
        fields["financials"] = unused_financials

    unused_valuation: dict[str, Any] = {}
    for frequency, records in (old.get("valuation_history") or {}).items():
        if not isinstance(records, list):
            continue
        if frequency != "yearly":
            if records:
                unused_valuation[frequency] = records
            continue
        extras = []
        for record in records:
            values = record.get("values", {}) if isinstance(record, dict) else {}
            extra_values = {key: value for key, value in values.items()
                            if key not in {"EPS", "Book Value", "Dividend Yield"}}
            if extra_values:
                extras.append({**record, "values": extra_values})
        if extras:
            unused_valuation[frequency] = extras
    if unused_valuation:
        fields["valuation_history"] = unused_valuation
    for key in ("valuation_current", "earnings_history", "share_count_history"):
        value = old.get(key)
        if value:
            fields[key] = value
    known = {
        "schema_version", "symbol", "updated_at", "fundamentals_checked_at", "price_history", "actions",
        "profile", "statistics_current", "financials", "valuation_history", "valuation_current",
        "earnings_history", "share_count_history",
    }
    for key, value in old.items():
        if key not in known and value is not None:
            fields[key] = value
    return fields


def archived_legacy_document(client: Any, bucket: str, symbol: str, old: dict[str, Any]) -> None:
    if not old:
        return
    safe = safe_symbol(symbol)
    legacy_key = f"{OLD_ROOT}/securities/{safe}.json"
    archive_key = object_key("archive", "legacy", "securities", safe, "unused.json.gz")
    fields = legacy_unused_fields(old)
    if fields:
        existing_raw = read_object(client, bucket, archive_key)
        existing = json.loads(gzip.decompress(existing_raw)) if existing_raw else {}
        archive_document = {
            "schema_version": 1,
            "source_key": legacy_key,
            "source_schema_version": old.get("schema_version"),
            "source_updated_at": old.get("updated_at"),
            "archived_at": iso_now(),
            "retention_until": years_before(datetime.now(JST).date(), -10).isoformat(),
            "fields": {**(existing.get("fields", {}) if isinstance(existing, dict) else {}), **fields},
        }
        payload = gzip.compress(json.dumps(archive_document, ensure_ascii=False, separators=(",", ":")).encode("utf-8"), compresslevel=6)
        client.put_object(Bucket=bucket, Key=archive_key, Body=payload,
                          ContentType="application/gzip", CacheControl="private, max-age=31536000")
        cache_object(client, bucket, archive_key, payload)
        # Keep this one remote read-back: the source legacy object is deleted
        # only after the compressed archive has been verified in R2.
        saved_raw = collector.read_object(client, bucket, archive_key)
        if not saved_raw:
            raise RuntimeError(f"Legacy field archive read-back failed: {archive_key}")
        cache_object(client, bucket, archive_key, saved_raw)
        saved = json.loads(gzip.decompress(saved_raw))
        if saved.get("source_key") != legacy_key or saved.get("fields") != archive_document["fields"]:
            raise RuntimeError(f"Legacy field archive verification failed: {archive_key}")
    client.delete_object(Bucket=bucket, Key=legacy_key)
    cache_object(client, bucket, legacy_key, None)


def verify_v2_transfer(client: Any, bucket: str, symbol: str, old: dict[str, Any],
                       statements: list[dict[str, Any]], monthly: list[dict[str, Any]],
                       as_of: date) -> None:
    safe = safe_symbol(symbol)
    if not read_json(client, bucket, object_key("securities", safe, "profile.json")):
        raise RuntimeError(f"v2 profile missing before legacy cleanup: {symbol}")
    if not read_json(client, bucket, object_key("securities", safe, "current.json")):
        raise RuntimeError(f"v2 current data missing before legacy cleanup: {symbol}")
    for family, legacy_key in (("dividends", "dividends"), ("splits", "stock_splits")):
        events = (old.get("actions") or {}).get(legacy_key, [])
        if not events:
            continue
        required_dates: set[str] = set()
        invalid_dates = 0
        for row in events:
            event_date = canonical_event_date(row.get("date")) if isinstance(row, dict) else None
            if event_date is None:
                invalid_dates += 1
            else:
                required_dates.add(event_date)
        if invalid_dates:
            raise RuntimeError(
                f"legacy {family} events have missing or invalid dates before cleanup for {symbol}: {invalid_dates}"
            )
        stored_dates = {
            event_date
            for row in load_all_year_records(client, bucket, symbol, family)
            if (event_date := canonical_event_date(row.get("date"))) is not None
        }
        archive_prefix = object_key("archive", "events", safe, family) + "/"
        for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=archive_prefix):
            for item in page.get("Contents", []):
                document = read_json(client, bucket, item["Key"])
                if not isinstance(document, dict):
                    continue
                records = document.get("records", [])
                if not isinstance(records, list):
                    continue
                stored_dates.update(
                    event_date
                    for row in records if isinstance(row, dict)
                    if (event_date := canonical_event_date(row.get("date"))) is not None
                )
        missing = required_dates - stored_dates
        if missing:
            raise RuntimeError(f"v2 {family} events missing before legacy cleanup for {symbol}: {len(missing)}")
    first_year, latest_year = year_end_price_years(as_of)
    for point in monthly:
        point_date = date_value(point.get("date"))
        month = str(point.get("month") or "")
        point_year = int(month[:4]) if re.fullmatch(r"\d{4}-12", month) else None
        if point_date is None or point_year is None or not first_year <= point_year <= latest_year:
            continue
        stored_months = {str(row.get("month")) for row in read_monthly_points(client, bucket, symbol, point_year)}
        if point.get("month") not in stored_months:
            raise RuntimeError(f"v2 monthly price missing before legacy cleanup: {symbol} {point.get('month')}")
    stored_statements, _, _, _ = prior_financial_maps(client, bucket, symbol)
    for row in statements:
        period_end = str(row.get("period_end") or "")[:10]
        if period_end not in stored_statements:
            raise RuntimeError(f"v2 financial record missing before legacy cleanup: {symbol} {period_end}")


def collect_symbol(client: Any, bucket: str, code: str, symbol: str, as_of: date,
                   *, attempt_count: int = 1, refresh_full_history: bool = False,
                   refresh_financials: bool = False, refresh_monthly: bool = False,
                   refresh_info: bool = False,
                   force_refresh_info: bool = False,
                   initialize_monthly: bool = False, retry_monthly: bool = False,
                   universe_entry: dict[str, Any] | None = None,
                   prior_sync_document: dict[str, Any] | None = None) -> dict[str, Any]:
    safe = safe_symbol(symbol)
    prior_sync = prior_sync_document or read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
    prior_profile = read_json(client, bucket, object_key("securities", safe, "profile.json")) or {}
    prior_current = read_json(client, bucket, object_key("securities", safe, "current.json")) or {}
    old = legacy_document(client, bucket, symbol)
    ticker = yf.Ticker(symbol)
    prior_field_status = prior_current.get("field_status", {})
    if not isinstance(prior_field_status, dict):
        prior_field_status = {}
    same_session_retry = same_session_partial_retry(prior_sync, as_of)
    prior_action_history = prior_current.get("action_history", {})
    if not isinstance(prior_action_history, dict):
        prior_action_history = {}
    prior_actions_succeeded = (
        prior_field_status.get("dividend_history") != "failed"
        and prior_field_status.get("split_history") != "failed"
    )
    prior_repair_period = pending_action_repair_period(prior_field_status, prior_action_history)
    skip_action_retry = same_session_retry and prior_actions_succeeded
    forced_repair_expected = (
        refresh_full_history
        or prior_sync.get("long_history_retry_pending") is True
        or bool(prior_repair_period)
    )
    prior_statistics = prior_current.get("statistics")
    if not isinstance(prior_statistics, dict):
        prior_statistics = {}
    cached_info_available = bool(
        prior_profile.get("display_name")
        or prior_profile.get("short_name")
        or prior_profile.get("long_name")
        or prior_statistics
    )

    def cached_company_info() -> dict[str, Any]:
        cached = {**prior_profile, **prior_statistics}
        cached["shortName"] = prior_profile.get("short_name") or prior_profile.get("display_name")
        cached["longName"] = prior_profile.get("long_name")
        cached["currency"] = (prior_current.get("price") or {}).get("currency") or "JPY"
        return cached

    info_refresh_pending_before_attempt = prior_sync.get("info_refresh_pending") is True
    info_last_fetched_session_date = (
        prior_sync.get("info_last_fetched_session_date")
        or (prior_sync.get("last_success_session_date") if cached_info_available else None)
    )
    info_last_fetched_at = (
        prior_sync.get("info_last_fetched_at")
        or (prior_sync.get("last_success_at") if cached_info_available else None)
    )
    refresh_info_now = refresh_info or force_refresh_info or info_refresh_pending_before_attempt or not cached_info_available
    already_fetched_this_session = (
        same_session_retry
        and info_last_fetched_session_date == as_of.isoformat()
        and not info_refresh_pending_before_attempt
        and not force_refresh_info
    )
    info_attempted = False
    info_fetched = False
    info_refresh_failed = False
    info_last_attempt_at = prior_sync.get("info_last_attempt_at")
    info_last_attempt_status = prior_sync.get("info_last_attempt_status")
    info_refresh_failure = prior_sync.get("info_refresh_failure")
    if already_fetched_this_session:
        info = cached_company_info()
    elif refresh_info_now:
        info_attempted = True
        info_last_attempt_at = iso_now()
        info_last_attempt_status = "attempting"
        count_yfinance_call("ticker_get_info")
        try:
            info = ticker.get_info()
            if not isinstance(info, dict) or not info:
                raise RuntimeError("yfinance returned no company profile")
            info_fetched = True
            info_last_attempt_status = "success"
            info_refresh_failure = None
            info_last_fetched_session_date = as_of.isoformat()
            info_last_fetched_at = iso_now()
        except Exception as exc:
            if not cached_info_available:
                raise
            info = cached_company_info()
            info_refresh_failed = True
            info_last_attempt_status = "failed"
            info_refresh_failure = company_info_failure_metadata(exc)
            print(
                f"Company information refresh failed for {symbol}; retaining cached values: "
                f"{type(exc).__name__}: {exc}"[:500],
                file=sys.stderr,
            )
    else:
        info = cached_company_info()
    previous_calendar = prior_current.get("calendar", {})
    if not isinstance(previous_calendar, dict):
        previous_calendar = {}
    previous_calendar_attempt_session = date_value(
        previous_calendar.get("last_attempt_session_date")
        or previous_calendar.get("last_attempt_at")
        or previous_calendar.get("fetched_at")
    )
    calendar_ex_date = date_value(previous_calendar.get("ex_dividend_date"))
    calendar_payment_date = date_value(previous_calendar.get("payment_start_date"))
    calendar_status = str(previous_calendar.get("status") or "not_fetched")
    calendar_payment_status = str(
        previous_calendar.get("payment_start_date_status")
        or ("available" if calendar_payment_date else "not_fetched")
    )
    calendar_last_attempt_status = str(previous_calendar.get("last_attempt_status") or calendar_status)
    calendar_fetched_at = previous_calendar.get("fetched_at")
    calendar_last_attempt_at = previous_calendar.get("last_attempt_at") or calendar_fetched_at
    calendar_last_attempt_session_date = (
        previous_calendar.get("last_attempt_session_date")
        or (previous_calendar_attempt_session.isoformat() if previous_calendar_attempt_session else None)
    )
    calendar_refresh_due = should_refresh_dividend_calendar(
        as_of=as_of,
        fetched_on=date_value(calendar_fetched_at),
        ex_dividend_date=calendar_ex_date,
        last_attempt_session_date=previous_calendar_attempt_session,
        last_attempt_status=calendar_last_attempt_status,
        status=calendar_status,
    )
    if calendar_refresh_due:
        calendar_last_attempt_at = iso_now()
        calendar_last_attempt_session_date = as_of.isoformat()
        try:
            count_yfinance_call("ticker_calendar")
            calendar_payload = ticker.calendar
            calendar_ex_date = calendar_ex_dividend_date(calendar_payload)
            calendar_payment_date = calendar_payment_start_date(calendar_payload)
            calendar_status = "available" if calendar_ex_date else "empty"
            calendar_payment_status = "available" if calendar_payment_date else "empty"
            calendar_last_attempt_status = calendar_status
            calendar_fetched_at = calendar_last_attempt_at
        except Exception as exc:
            calendar_last_attempt_status = "failed"
            calendar_status = "stale" if calendar_ex_date else "failed"
            calendar_payment_status = "stale" if calendar_payment_date else "failed"
            print(f"Dividend calendar unavailable for {symbol}: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
    calendar = {
        "ex_dividend_date": calendar_ex_date.isoformat() if calendar_ex_date else None,
        "payment_start_date": calendar_payment_date.isoformat() if calendar_payment_date else None,
        "payment_start_date_status": calendar_payment_status,
        "status": calendar_status,
        "last_attempt_status": calendar_last_attempt_status,
        "last_attempt_at": calendar_last_attempt_at,
        "last_attempt_session_date": calendar_last_attempt_session_date,
        "fetched_at": calendar_fetched_at,
        "source": "Yahoo Finance via yfinance Ticker.calendar",
    }
    prior_price = prior_current.get("price") if isinstance(prior_current.get("price"), dict) else {}
    market_history_frame = None
    market_history_error: Exception | None = None
    if same_session_retry and str(prior_price.get("date") or "")[:10] == as_of.isoformat():
        latest = {"date": prior_price["date"], "close": prior_price.get("close")}
        price_status = str(prior_field_status.get("price") or "available")
    else:
        def fetch_daily_close():
            nonlocal market_history_frame, market_history_error
            try:
                quote_period = "5d" if forced_repair_expected or skip_action_retry else "1y"
                daily_rows, market_history_frame = fetch_daily_history_with_actions(
                    ticker, as_of, period=quote_period,
                )
                return daily_rows
            except Exception as exc:
                market_history_error = exc
                raise

        latest, price_status, price_failure = current_close_with_fallback(fetch_daily_close, prior_price, as_of)
        if price_failure:
            print(f"Daily close unavailable for {symbol}: {price_failure}"[:500], file=sys.stderr)
    price_date = date_value(latest.get("date"))
    if price_date and price_date > as_of:
        latest = {"date": prior_price.get("date"), "close": prior_price.get("close")}
        price_date = date_value(latest.get("date"))
        price_status = "failed"

    old_actions = old.get("actions") if isinstance(old.get("actions"), dict) else {}
    legacy_dividends = normalize_event_rows(
        [row for row in old_actions.get("dividends", []) if isinstance(row, dict)], "date",
    )
    legacy_splits = normalize_event_rows(
        [row for row in old_actions.get("stock_splits", []) if isinstance(row, dict)], "date",
    )
    full_action_history_required = (
        forced_repair_expected
        or bool(legacy_dividends or legacy_splits)
        or not prior_action_history.get("candidate_fingerprint")
        or not isinstance(
            (prior_current.get("dividend") or {}).get("calendar_year_metrics"), dict,
        )
    )
    if full_action_history_required:
        prior_dividends = load_all_year_records(client, bucket, symbol, "dividends", include_archive=True)
        prior_splits = load_all_year_records(client, bucket, symbol, "splits", include_archive=True)
    else:
        prior_dividends = load_recent_year_records(client, bucket, symbol, "dividends", as_of)
        prior_splits = load_recent_year_records(client, bucket, symbol, "splits", as_of)
    prior_dividends.extend(legacy_dividends)
    prior_splits.extend(legacy_splits)
    stored_duplicate_events = (
        has_duplicate_event_dates(prior_dividends)
        or has_duplicate_event_dates(prior_splits)
    )
    if stored_duplicate_events and not full_action_history_required:
        # Load every hot/archive partition before a full Yahoo rebuild so rows
        # outside the recent three-year window are retained or repaired too.
        full_action_history_required = True
        prior_dividends = load_all_year_records(client, bucket, symbol, "dividends", include_archive=True)
        prior_splits = load_all_year_records(client, bucket, symbol, "splits", include_archive=True)
        prior_dividends.extend(legacy_dividends)
        prior_splits.extend(legacy_splits)
        stored_duplicate_events = (
            has_duplicate_event_dates(prior_dividends)
            or has_duplicate_event_dates(prior_splits)
        )
    if stored_duplicate_events:
        # Corrupt stored partitions must bypass same-session reuse and refresh
        # the complete Yahoo history before their derived totals are published.
        skip_action_retry = False
        forced_repair_expected = True
    action_status: dict[str, str] = {}
    action_retrieval: dict[str, Any] = {
        "source": "yfinance_history_chart",
        "yfinance_version": yf.__version__,
        "period": "max" if refresh_full_history else "1y",
        "repair": False,
        "repair_on_change": True,
        "full_history_refreshed": False,
        "full_history_coverage_end": prior_action_history.get("full_history_coverage_end"),
        "candidate_fingerprint": prior_action_history.get("candidate_fingerprint"),
        "dividend_amount_basis": "yfinance_split_adjusted_assumption",
        "split_ratio_reapplied_to_dividends": False,
    }
    full_history_attempted = (
        refresh_full_history
        or prior_sync.get("long_history_retry_pending") is True
        or prior_repair_period == "max"
        or stored_duplicate_events
    )
    full_history_refreshed = False
    new_split_detected = False
    forced_repair = False
    repair_period = "1y"
    actions_changed = False
    fresh_dividends: list[dict[str, Any]] | None = None
    fresh_splits: list[dict[str, Any]] | None = None
    if stored_duplicate_events:
        action_retrieval["repair_reason"] = "duplicate_stored_event_dates"
    if skip_action_retry:
        dividends = prior_dividends
        splits = prior_splits
        action_status["dividends"] = str(prior_field_status.get("dividend_history") or "empty")
        action_status["splits"] = str(prior_field_status.get("split_history") or "empty")
        action_retrieval = dict(prior_action_history)
        action_retrieval["retry_status"] = "reused_successful_same_session"
    else:
        try:
            forced_repair = (
                refresh_full_history
                or prior_sync.get("long_history_retry_pending") is True
                or bool(prior_repair_period)
                or stored_duplicate_events
            )
            repair_period = "max" if (
                refresh_full_history
                or prior_sync.get("long_history_retry_pending") is True
                or prior_repair_period == "max"
                or stored_duplicate_events
            ) else "1y"
            prior_full_coverage_end = str(prior_action_history.get("full_history_coverage_end") or "")
            if forced_repair:
                repair_triggers = []
                if refresh_full_history:
                    repair_triggers.append("scheduled_full_history_bucket")
                if prior_sync.get("long_history_retry_pending") is True:
                    repair_triggers.append("pending_full_history_retry")
                if prior_repair_period:
                    repair_triggers.append(f"pending_action_repair_{prior_repair_period}")
                if stored_duplicate_events:
                    repair_triggers.append("duplicate_stored_events")
                if not repair_triggers:
                    repair_triggers.append("other_forced_repair")
                for trigger in set(repair_triggers):
                    count_action_history_repair_trigger(trigger)
                fresh_dividends, fresh_splits, action_coverage_start, action_coverage_end = fetch_repaired_action_history(
                    ticker, as_of, period=repair_period,
                )
                new_split_detected = has_new_split(fresh_splits, prior_splits)
                full_history_refreshed = repair_period == "max"
            else:
                if market_history_frame is None:
                    if market_history_error is not None:
                        raise market_history_error
                    _, market_history_frame = fetch_daily_history_with_actions(
                        ticker, as_of, period="1y",
                    )
                candidate_dividends, candidate_splits, candidate_start, candidate_end = action_candidate_from_history(
                    market_history_frame, as_of,
                )
                candidate_fingerprint = action_event_fingerprint(candidate_dividends, candidate_splits)
                new_split_detected = has_new_split(candidate_splits, prior_splits)
                previous_candidate_fingerprint = prior_action_history.get("candidate_fingerprint")
                actions_changed = (
                    candidate_fingerprint != previous_candidate_fingerprint
                    if previous_candidate_fingerprint
                    else (
                        action_events_changed(candidate_dividends, prior_dividends, value_field="amount",
                                              coverage_start=candidate_start, coverage_end=candidate_end)
                        or action_events_changed(candidate_splits, prior_splits, value_field="ratio",
                                                 coverage_start=candidate_start, coverage_end=candidate_end)
                    )
                )
                if new_split_detected:
                    # A newly observed split can change the interpretation of older actions and prices.
                    if not full_action_history_required:
                        prior_dividends = load_all_year_records(
                            client, bucket, symbol, "dividends", include_archive=True,
                        )
                        prior_splits = load_all_year_records(
                            client, bucket, symbol, "splits", include_archive=True,
                        )
                        prior_dividends.extend(legacy_dividends)
                        prior_splits.extend(legacy_splits)
                        full_action_history_required = True
                    full_history_attempted = True
                    count_action_history_repair_trigger("new_split_detected")
                    fresh_dividends, fresh_splits, action_coverage_start, action_coverage_end = fetch_repaired_action_history(
                        ticker, as_of, period="max",
                    )
                    full_history_refreshed = True
                elif actions_changed:
                    count_action_history_repair_trigger("recent_action_candidate_changed")
                    fresh_dividends, fresh_splits, action_coverage_start, action_coverage_end = fetch_repaired_action_history(
                        ticker, as_of, period="1y",
                    )
                else:
                    fresh_dividends = fresh_splits = None
                    action_coverage_start, action_coverage_end = candidate_start, candidate_end
                    dividends, splits = prior_dividends, prior_splits
                    action_status["dividends"] = "available" if candidate_dividends else "empty"
                    action_status["splits"] = "available" if candidate_splits else "empty"
                    action_retrieval.update({
                        "status": "success",
                        "period": "1y",
                        "repair": False,
                        "change_detected": False,
                        "candidate_fingerprint": candidate_fingerprint,
                        "coverage_start": candidate_start.isoformat(),
                        "coverage_end": candidate_end.isoformat(),
                        "dividend_event_count": len(candidate_dividends),
                        "split_event_count": len(candidate_splits),
                        "stored_repaired_events_reused": True,
                    })
            if fresh_dividends is not None and fresh_splits is not None:
                action_retrieval["repair_attempt_period"] = "max" if full_history_refreshed else "1y"
                if not forced_repair:
                    action_retrieval["candidate_fingerprint"] = candidate_fingerprint
                action_retrieval["source"] = "yfinance_history"
                action_retrieval["repair"] = True
                action_retrieval["change_detected"] = not forced_repair
                dividends = preserve_legacy_event_gaps(
                    replace_dated_history(
                        prior_dividends, fresh_dividends, "date", action_coverage_start, action_coverage_end,
                    ),
                    legacy_dividends, "date",
                )
                splits = preserve_legacy_event_gaps(
                    replace_dated_history(
                        prior_splits, fresh_splits, "date", action_coverage_start, action_coverage_end,
                    ),
                    legacy_splits, "date",
                )
                action_status["dividends"] = "available" if fresh_dividends else "empty"
                action_status["splits"] = "available" if fresh_splits else "empty"
                action_retrieval.update({
                    "status": "success",
                    "period": "max" if full_history_refreshed else "1y",
                    "full_history_refreshed": full_history_refreshed,
                    "full_history_attempted": full_history_attempted,
                    "full_history_coverage_end": (
                        action_coverage_end.isoformat() if full_history_refreshed
                        else prior_full_coverage_end or None
                    ),
                    "coverage_start": action_coverage_start.isoformat(),
                    "coverage_end": action_coverage_end.isoformat(),
                    "dividend_event_count": len(fresh_dividends),
                    "split_event_count": len(fresh_splits),
                    "stored_repaired_events_reused": False,
                })
        except Exception as exc:
            dividends = prior_dividends
            splits = prior_splits
            action_status["dividends"] = "failed"
            action_status["splits"] = "failed"
            action_retrieval.update({
                "status": "failed",
                "repair_attempt_period": repair_period if forced_repair or actions_changed or new_split_detected else None,
                "failure_reason": f"{type(exc).__name__}: {exc}"[:500],
            })
            print(f"Dividend history unavailable for {symbol}: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
    action_years: dict[str, set[str]] = {
        "dividends": {str(row.get("date") or "")[:4] for row in prior_dividends + dividends if row.get("date")},
        "splits": {str(row.get("date") or "")[:4] for row in prior_splits + splits if row.get("date")},
    }

    previous_monthly = prior_sync.get("monthly_history_schema_version") == 2
    update_monthly_now = year_end_price_refresh_required(
        previous_monthly=previous_monthly,
        refresh_monthly=refresh_monthly,
        initialize_monthly=initialize_monthly,
        retry_monthly=retry_monthly,
        new_split_detected=new_split_detected,
    )
    if update_monthly_now:
        try:
            monthly = monthly_history_rows(
                ticker,
                initial=(
                    not previous_monthly
                    or prior_sync.get("monthly_retry_pending") is True
                    or new_split_detected
                ),
                as_of=as_of,
            )
            monthly_status = "available" if monthly else "empty"
        except Exception as exc:
            monthly = []
            monthly_status = "failed"
            print(f"Monthly history unavailable for {symbol}: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
    else:
        monthly = []
        monthly_status = prior_current.get("field_status", {}).get("monthly_prices", "not_scheduled")
    old_prices = old.get("price_history") or {}
    migrated_months = [
        {"month": str(row.get("date", ""))[:7], "date": row.get("date"), "close": row.get("close")}
        for row in old_prices.get("1mo", []) if row.get("date") and row.get("close")
    ]
    legacy_monthly_migration_pending = bool(migrated_months) and not bool(
        prior_sync.get("legacy_monthly_migrated")
    )
    merged_months = {str(point["month"]): point for point in migrated_months}
    merged_months.update({str(point["month"]): point for point in monthly})
    monthly = [merged_months[month] for month in sorted(merged_months)]
    for point in monthly:
        # Yahoo's Close is already split-adjusted; Adj Close would also adjust for dividends.
        point["close"] = float(point["close"])
        point["currency"] = str(info.get("currency") or "JPY")

    retry_financials = prior_sync.get("financial_retry_pending") is True
    failed_financial_groups = financial_failed_groups(prior_field_status)
    financial_due, current_financial_marker, financial_marker_changed = financial_refresh_due(
        info=info, prior_sync=prior_sync, as_of=as_of,
        initialize_due=refresh_financials, retry_pending=retry_financials,
    )
    financial_groups = financial_groups_to_fetch(
        same_session_retry=same_session_retry,
        retry_pending=retry_financials,
        due=financial_due,
        failed_groups=failed_financial_groups,
    )
    financial_attempted = bool(financial_groups)
    financial_document_keys: dict[str, str] = {}
    if financial_attempted:
        statements, valuation, financial_status, financial_document_keys = collect_financials(
            ticker, old, client, bucket, symbol,
            fetch_groups=financial_groups, previous_status=prior_field_status,
        )
        financial_retry_pending = any(value == "failed" for value in financial_status.values() if isinstance(value, str))
        if isinstance(financial_status.get("fields"), dict):
            financial_retry_pending = financial_retry_pending or any(
                value == "failed" for value in financial_status["fields"].values()
            )
    else:
        statements, valuation = [], []
        financial_status = prior_field_status.get("financial_fields", {})
        if not isinstance(financial_status, dict):
            financial_status = {}
        financial_status = {**financial_status, "refresh_status": "not_scheduled"}
        financial_retry_pending = retry_financials
    names = japanese_names(info)
    profile = {
        "schema_version": 1,
        "code": code,
        "symbol": symbol,
        **names,
        **{key: collector.json_safe(info[key]) for key in PROFILE_FIELDS if info.get(key) is not None},
        "updated_at": iso_now(),
        "source": "Yahoo Finance via yfinance",
    }
    previous_profile = prior_profile
    stale_profile_fields: set[str] = set()
    for field, value in (previous_profile.items() if previous_profile else []):
        if profile.get(field) is None and value is not None:
            profile[field] = value
            stale_profile_fields.add(field)
    previous_profile_status = previous_profile.get("field_status")
    if not info_fetched and isinstance(previous_profile_status, dict):
        profile["field_status"] = dict(previous_profile_status)
    else:
        profile["field_status"] = {
            field: ("stale" if field in stale_profile_fields else "available" if profile.get(field) is not None else "empty")
            for field in (*PROFILE_FIELDS, "short_name", "long_name", "display_name", "english_name")
        }
    profile = preserve_profile_timestamp_if_unchanged(profile, previous_profile)

    close = finite_number(latest.get("close"))
    price_change = daily_price_change(market_history_frame, price_date, close)
    if price_change["change"] is None and str(prior_price.get("date") or "")[:10] == (price_date.isoformat() if price_date else ""):
        price_change = {
            "change": finite_number(prior_price.get("change")),
            "change_percent": finite_number(prior_price.get("change_percent")),
            "previous_close": finite_number(prior_price.get("previous_close")),
        }
    trailing_start = years_before(as_of, 1).isoformat()
    trailing_dividends = round(sum(
        finite_number(row.get("amount")) or 0.0 for row in dividends
        if trailing_start < str(row.get("date") or "")[:10] <= as_of.isoformat()
    ), 8)
    forecast_dps = next_fiscal_year_forecast_dps(code, info.get("dividendRate"), as_of)
    has_forecast_dps = forecast_dps is not None and forecast_dps > 0
    current_fy_dps = fiscal_year_events(dividends, info, as_of)
    reported_yield = finite_number(info.get("dividendYield"))
    reported_yield_percent = (
        round(reported_yield * 100, 4)
        if reported_yield is not None and 0 <= reported_yield <= 1 else None
    )
    no_dividend = (
        not has_forecast_dps
        and current_fy_dps is None
        and (forecast_dps == 0.0 or reported_yield == 0.0)
    )
    yield_dps = forecast_dps if has_forecast_dps else (
        current_fy_dps if current_fy_dps is not None else 0.0 if no_dividend else None
    )
    yield_basis = "next_fiscal_year_forecast" if has_forecast_dps else (
        "current_fiscal_year" if current_fy_dps is not None else "yfinance_reported_no_dividend" if no_dividend else None
    )
    calculated_yield = round(yield_dps / close * 100, 4) if yield_dps is not None and close and close > 0 else None
    dividend_yield = reported_yield_percent if reported_yield_percent is not None else calculated_yield
    dividend_yield_source = (
        "yfinance_reported_dividendYield" if reported_yield_percent is not None
        else "calculated_from_yfinance_dividend_and_quote" if calculated_yield is not None
        else None
    )
    dividend_history_failed = action_status["dividends"] == "failed"
    dividend_status = "no_dividend" if no_dividend else "has_dividend" if yield_dps is not None and yield_dps > 0 else "unknown"
    current_fiscal_status = (
        "failed" if dividend_history_failed and current_fy_dps is None
        else "stale" if dividend_history_failed
        else "available" if current_fy_dps is not None else "empty"
    )
    dividend_yield_status = (
        "available" if reported_yield_percent is not None
        else "failed" if dividend_history_failed and yield_basis is None
        else "stale" if dividend_history_failed and yield_basis == "current_fiscal_year"
        else "empty" if dividend_yield is None else "available"
    )
    dividend_status_state = (
        "failed" if dividend_history_failed and dividend_status == "unknown"
        else "stale" if dividend_history_failed and yield_basis == "current_fiscal_year"
        else "empty" if dividend_status == "unknown" else "available"
    )
    prior_dividend_payload = prior_current.get("dividend")
    previous_calendar_year_metrics = (prior_dividend_payload.get("calendar_year_metrics")
                                      if isinstance(prior_dividend_payload, dict) else None)
    if action_status["dividends"] == "failed":
        calendar_year_metrics = dict(previous_calendar_year_metrics) if isinstance(previous_calendar_year_metrics, dict) else {
            "history_basis": "calendar_year_observed_distributions",
            "annual_records": [],
            "latest_completed_year": None,
            "consecutive_increase_years": None,
            "no_cut_years": None,
            "status": "failed",
            "source": "Yahoo Finance via yfinance",
        }
        if calendar_year_metrics.get("status") == "available":
            calendar_year_metrics["status"] = "stale"
    else:
        calendar_year_metrics = calendar_year_dividend_metrics(
            prior_dividends + dividends,
            previous_calendar_year_metrics,
            as_of,
        )
    current = {
        "schema_version": 1,
        "code": code,
        "symbol": symbol,
        "price": {
            "close": close,
            "date": price_date.isoformat() if price_date else None,
            "currency": str(info.get("currency") or "JPY"),
            **price_change,
        },
        "dividend": {
            "trailing_12_month_per_share": trailing_dividends,
            "trailing_12_month_period": {
                "start_exclusive": trailing_start,
                "end_inclusive": as_of.isoformat(),
            },
            "next_fiscal_year_forecast_per_share": forecast_dps,
            "current_fiscal_year_per_share": current_fy_dps,
            "yield_rate": dividend_yield,
            "yield_source": dividend_yield_source,
            "yield_basis": yield_basis,
            "event_amount_basis": "yfinance_split_adjusted_assumption",
            "split_adjustment_policy": "assume_yfinance_returned_amounts_are_split_adjusted",
            "dividend_status": dividend_status,
            "calendar_year_metrics": calendar_year_metrics,
        },
        "statistics": {key: collector.json_safe(info[key]) for key in CURRENT_FIELDS if info.get(key) is not None},
        "statistics_last_fetched_session_date": info_last_fetched_session_date,
        "statistics_last_fetched_at": info_last_fetched_at,
        "calendar": calendar,
        "field_status": {
            "price": price_status,
            "dividend_history": action_status["dividends"],
            "calendar_year_dividend_metrics": calendar_year_metrics["status"],
            "split_history": action_status["splits"],
            "upcoming_ex_dividend_date": calendar["status"],
            "upcoming_dividend_payment_start_date": calendar_payment_status,
            "monthly_prices": monthly_status,
            "forecast_dividend": "available" if has_forecast_dps else "empty",
            "trailing_12_month_per_share": "stale" if dividend_history_failed else "available" if trailing_dividends > 0 else "empty",
            "current_fiscal_dividend": current_fiscal_status,
            "dividend_yield": dividend_yield_status,
            "dividend_status": dividend_status_state,
            "financial_fields": financial_status.get("fields", {}),
            **{f"financial_{key}": value for key, value in financial_status.items()},
        },
        "action_history": action_retrieval,
        "yfinance_version": yf.__version__,
        "updated_at": iso_now(),
        "source": "Yahoo Finance via yfinance",
    }
    current["field_status"].update({
        f"statistics_{field}": (
            "stale" if current["statistics"].get(field) is not None and not info_fetched
            else "available" if current["statistics"].get(field) is not None else "empty"
        )
        for field in CURRENT_FIELDS
    })
    previous_statistics = prior_current.get("statistics", {})
    prior_valuation = prior_current.get("valuation_calculation")
    prior_valuation_source = prior_valuation.get("source") if isinstance(prior_valuation, dict) else None
    prior_valuation_sources = prior_valuation.get("sources") if isinstance(prior_valuation, dict) else None
    if not isinstance(prior_valuation_sources, dict):
        prior_valuation_sources = {}
    prior_valuation_was_derived = prior_valuation_source == "yfinance_v2_daily_calculation"
    archive_unused_statistics(client, bucket, symbol, previous_statistics)
    if not info_fetched:
        for field in DERIVED_STATISTIC_FIELDS:
            if prior_valuation_was_derived or prior_valuation_sources.get(field) == "calculated_fallback":
                current["statistics"].pop(field, None)
                current["field_status"][f"statistics_{field}"] = "empty"
    for field, value in (previous_statistics.items() if isinstance(previous_statistics, dict) else []):
        if field in CURRENT_FIELDS and current["statistics"].get(field) is None and value is not None:
            if field in DERIVED_STATISTIC_FIELDS and (
                info_fetched
                or prior_valuation_was_derived
                or prior_valuation_sources.get(field) == "calculated_fallback"
            ):
                continue
            current["statistics"][field] = value
            current["field_status"][f"statistics_{field}"] = "stale"
    daily_valuation = calculate_daily_valuation(
        close,
        current["statistics"].get("trailingEps"),
        current["statistics"].get("bookValue"),
        current["statistics"].get("trailingPE"),
        current["statistics"].get("priceToBook"),
    )
    valuation_status = (
        "available" if price_status == "available" else "stale"
    )
    valuation_sources: dict[str, str] = {}
    for field, value in daily_valuation.items():
        reported_value = finite_number(current["statistics"].get(field))
        if value is not None:
            current["statistics"][field] = value
        valuation_sources[field] = (
            "yfinance_reported" if reported_value is not None
            else "calculated_fallback" if value is not None
            else "unavailable"
        )
        if reported_value is None:
            current["field_status"][f"statistics_{field}"] = valuation_status if value is not None else "empty"
    current["valuation_calculation"] = {
        "source": "yfinance_reported_with_daily_calculation_fallback",
        "price_date": price_date.isoformat() if price_date else None,
        "statistics_last_fetched_session_date": info_last_fetched_session_date,
        "formulas": {"trailingPE": "price.close / statistics.trailingEps", "priceToBook": "price.close / statistics.bookValue"},
        "sources": {
            "trailingPE": valuation_sources["trailingPE"],
            "priceToBook": valuation_sources["priceToBook"],
        },
    }
    current["field_status"]["statistics_refresh"] = (
        "failed" if info_refresh_failed else "available" if info_fetched else "stale"
    )

    advice_statements = statements
    if not advice_statements and not isinstance(prior_current.get("income_advice_metrics"), dict):
        period = date_value(info.get("lastFiscalYearEnd"))
        if period is not None:
            safe_period = safe_symbol(symbol)
            for key in (
                object_key("securities", safe_period, "financials", f"{period.isoformat()}.json"),
                object_key("securities", safe_period, "financials", f"{period.year}.json"),
            ):
                document = read_json(client, bucket, key)
                annual = document.get("annual") if isinstance(document, dict) else None
                if isinstance(annual, dict):
                    advice_statements = [annual]
                    break
    current["income_advice_metrics"] = income_advice_metrics(
        current, advice_statements, prior_current.get("income_advice_metrics"),
    )

    now = now_utc()
    cutoff10 = years_before(as_of, 10).isoformat()
    for family, rows, event_key in (("dividends", dividends, "date"), ("splits", splits, "date")):
        by_year: dict[str, list[dict[str, Any]]] = {}
        archive_by_year: dict[str, list[dict[str, Any]]] = {}
        for row in rows:
            event_date = str(row.get(event_key) or "")[:10]
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", event_date):
                continue
            target = by_year if event_date >= cutoff10 else archive_by_year
            target.setdefault(event_date[:4], []).append(row)
        persist_action_partitions = action_partitions_need_persist(
            fresh_dividends, fresh_splits, legacy_dividends, legacy_splits,
        )
        years = (action_years[family] | set(by_year) | set(archive_by_year)) if persist_action_partitions else set()
        for year in sorted(years):
            hot_key = object_key("securities", safe, family, f"{year}.json")
            archive_key = object_key("archive", "events", safe, family, f"{year}.json")
            if archive_by_year.get(year):
                put_json_if_changed(client, bucket, archive_key, {
                    "schema_version": 1, "symbol": symbol, "year": int(year),
                    "records": archive_by_year[year], "action_history": action_retrieval,
                    "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
                })
                saved = read_json(client, bucket, archive_key)
                if not saved or saved.get("records") != archive_by_year[year]:
                    raise RuntimeError(f"Event archive read-back failed: {archive_key}")
            else:
                delete_object_if_present(client, bucket, archive_key)
            if year in by_year:
                put_json_if_changed(client, bucket, hot_key, {
                    "schema_version": 1, "symbol": symbol, "year": int(year),
                    "records": by_year[year], "action_history": action_retrieval,
                    "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
                })
            else:
                delete_object_if_present(client, bucket, hot_key)

    if update_monthly_now or legacy_monthly_migration_pending:
        save_monthly_prices(client, bucket, symbol, monthly, as_of=as_of)
    valuation_by_period = {str(row.get("period_end", ""))[:10]: row for row in valuation}
    for row in statements:
        period_end = str(row.get("period_end", ""))[:10]
        year = period_end[:4]
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", period_end):
            key = financial_document_keys.get(period_end) or object_key(
                "securities", safe, "financials", f"{period_end}.json",
            )
            put_json_if_changed(client, bucket, key, {
                "schema_version": 1, "symbol": symbol, "year": year,
                "annual": row,
                "valuation": valuation_by_period.get(period_end),
                "retrieval_status": financial_status,
                "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
            })

    # Keep prior field values when Yahoo omits a field; expose their source status for the app.
    previous_dividend = prior_current.get("dividend", {})
    for field in ("trailing_12_month_per_share", "next_fiscal_year_forecast_per_share", "current_fiscal_year_per_share", "yield_rate", "yield_basis"):
        if current["dividend"].get(field) is None and previous_dividend.get(field) is not None:
            current["dividend"][field] = previous_dividend[field]
            current["field_status"][field] = "stale"
    put_json_if_changed(client, bucket, object_key("securities", safe, "profile.json"), profile)
    put_json_if_changed(client, bucket, object_key("securities", safe, "current.json"), current)
    def failed_fields(value: Any, path: str = "") -> list[str]:
        if isinstance(value, dict):
            return [field for key, nested in value.items() for field in failed_fields(nested, f"{path}.{key}" if path else str(key))]
        return [path] if value == "failed" else []

    field_failures = failed_fields(current["field_status"])
    sync = {
        "schema_version": 1,
        "code": code,
        "symbol": symbol,
        "status": "partial" if field_failures else "ok",
        "last_attempt_at": iso_now(),
        "last_success_at": iso_now(),
        "last_success_session_date": as_of.isoformat(),
        "monthly_history_initialized": bool(prior_sync.get("monthly_history_initialized")) or (
            update_monthly_now and monthly_status != "failed"
        ),
        "monthly_history_schema_version": (
            2 if previous_monthly or (update_monthly_now and monthly_status != "failed")
            else prior_sync.get("monthly_history_schema_version")
        ),
        "legacy_monthly_migrated": bool(prior_sync.get("legacy_monthly_migrated")) or legacy_monthly_migration_pending,
        "full_history_initialized": bool(prior_sync.get("full_history_initialized")) or full_history_refreshed,
        "financials_initialized": bool(prior_sync.get("financials_initialized")) or (
            financial_attempted and not financial_retry_pending
        ),
        "info_refresh_policy_version": COMPANY_INFO_REFRESH_POLICY_VERSION,
        "info_last_fetched_session_date": info_last_fetched_session_date,
        "info_last_fetched_at": info_last_fetched_at,
        "info_last_attempt_session_date": as_of.isoformat() if info_attempted else prior_sync.get("info_last_attempt_session_date"),
        "info_last_attempt_at": info_last_attempt_at,
        "info_last_attempt_status": info_last_attempt_status,
        "info_refresh_failure": info_refresh_failure,
        "info_refresh_status": "failed_using_cache" if info_refresh_failed else "updated" if info_fetched else "cached",
        "info_refresh_pending": info_refresh_failed or (
            info_refresh_pending_before_attempt and not info_fetched
        ),
        "financial_refresh_policy_version": (
            FINANCIAL_REFRESH_POLICY_VERSION
            if financial_attempted or prior_sync.get("financial_refresh_policy_version") == FINANCIAL_REFRESH_POLICY_VERSION
            else None
        ),
        "financial_period_marker": (
            current_financial_marker if financial_attempted and current_financial_marker
            else prior_sync.get("financial_period_marker")
        ),
        "financials_last_checked_session_date": (
            as_of.isoformat() if financial_attempted
            else prior_sync.get("financials_last_checked_session_date")
        ),
        "monthly_retry_pending": monthly_status == "failed" or (
            not update_monthly_now and bool(prior_sync.get("monthly_retry_pending"))
        ),
        "long_history_retry_pending": (
            action_retrieval.get("status") == "failed" and full_history_attempted
        ) or (not full_history_attempted and bool(prior_sync.get("long_history_retry_pending"))),
        "financial_retry_pending": financial_retry_pending,
        "failure_reason": f"Field fetch failed: {', '.join(field_failures)}"[:500] if field_failures else None,
        "failure_class": None,
        "retry_policy": None,
        "attempt_count_today": attempt_count,
        "retry_count_today": max(0, attempt_count - 1),
        "retry_session_date": as_of.isoformat(),
        "field_status": current["field_status"],
        "source": "Yahoo Finance via yfinance",
    }
    put_json_if_changed(client, bucket, object_key("securities", safe, "sync.json"), sync)
    if (old and sync["full_history_initialized"] and sync["financials_initialized"]
            and sync["monthly_history_schema_version"] == 2):
        stored_statements, _, _, _ = prior_financial_maps(client, bucket, symbol)
        verification_statements = [
            {"period_end": period_end, **values}
            for period_end, values in sorted(stored_statements.items())
        ]
        verify_v2_transfer(client, bucket, symbol, old, verification_statements, monthly, as_of)
        archived_legacy_document(client, bucket, symbol, old)
    catalog_item = catalog_item_from_documents(
        code, universe_entry or {"security_code_display": code}, profile, current, sync,
    )
    if catalog_item is not None:
        catalog_item["income_advice"] = income_advice_candidate(
            code, catalog_item["name"], current, dividends, as_of,
        )
    return {"code": code, "symbol": symbol, "name": profile["display_name"], "price": close,
            "quote_date": price_date.isoformat() if price_date else None,
            "annual_dividend_per_share": trailing_dividends,
            "yield_rate": current["dividend"]["yield_rate"], "currency": current["price"]["currency"],
            "info_refreshed": info_fetched, "info_refresh_failed": info_refresh_failed,
            "field_status": current["field_status"], "field_failures": field_failures,
            "status": "partial" if field_failures else "ok", "catalog_item": catalog_item}


def save_failure(client: Any, bucket: str, code: str, symbol: str, session_date: date, error: Exception,
                 *, attempt_count: int, retry_full_history: bool = False,
                 retry_financials: bool = False, retry_monthly: bool = False,
                 retry_info: bool = False) -> None:
    safe = safe_symbol(symbol)
    prior = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
    failure_class = classify_yfinance_error(error)
    first_company_info_attempt_failed = retry_info and not prior.get("last_success_session_date")
    prior.update({
        "schema_version": 1, "code": code, "symbol": symbol,
        "status": "failed", "last_attempt_at": iso_now(),
        "last_attempt_session_date": session_date.isoformat(),
        "failure_reason": f"{type(error).__name__}: {error}"[:500],
        "failure_class": failure_class,
        "retry_policy": "next_market_session" if failure_class == "no_data" else "same_session_retryable_limited",
        "attempt_count_today": attempt_count,
        "retry_count_today": max(0, attempt_count - 1),
        "retry_session_date": session_date.isoformat(),
        "long_history_retry_pending": bool(prior.get("long_history_retry_pending")) or retry_full_history,
        "financial_retry_pending": bool(prior.get("financial_retry_pending")) or retry_financials,
        "monthly_retry_pending": bool(prior.get("monthly_retry_pending")) or retry_monthly,
        "info_refresh_pending": bool(prior.get("info_refresh_pending")) or retry_info,
        "info_last_attempt_session_date": (
            session_date.isoformat() if first_company_info_attempt_failed
            else prior.get("info_last_attempt_session_date")
        ),
        "info_last_attempt_at": iso_now() if first_company_info_attempt_failed else prior.get("info_last_attempt_at"),
        "info_last_attempt_status": (
            "failed" if first_company_info_attempt_failed else prior.get("info_last_attempt_status")
        ),
        "info_refresh_failure": (
            company_info_failure_metadata(error) if first_company_info_attempt_failed
            else prior.get("info_refresh_failure")
        ),
    })
    put_json_if_changed(client, bucket, object_key("securities", safe, "sync.json"), prior)


def record_attempt_start(client: Any, bucket: str, code: str, symbol: str,
                         session_date: date, attempt_count: int,
                         previous_sync: dict[str, Any] | None = None) -> dict[str, Any]:
    safe = safe_symbol(symbol)
    prior = dict(previous_sync or read_json(client, bucket, object_key("securities", safe, "sync.json")) or {})
    prior.update({
        "schema_version": 1, "code": code, "symbol": symbol,
        "status": "running", "last_attempt_at": iso_now(),
        "last_attempt_session_date": session_date.isoformat(),
        "attempt_count_today": attempt_count,
        "retry_count_today": max(0, attempt_count - 1),
        "retry_session_date": session_date.isoformat(),
    })
    # last_attempt_at always changes, so a read-before-write check only adds an R2 request.
    collector.put_json(client, bucket, object_key("securities", safe, "sync.json"), prior)
    return prior


def income_advice_metrics(current: dict[str, Any], statements: list[dict[str, Any]],
                          previous: dict[str, Any] | None) -> dict[str, Any]:
    """Keep the latest V2 source values and statuses needed by the advice ranking."""
    prior = previous if isinstance(previous, dict) else {}
    statistics = current.get("statistics") if isinstance(current.get("statistics"), dict) else {}
    field_status = current.get("field_status") if isinstance(current.get("field_status"), dict) else {}
    prior_fields = prior.get("fields") if isinstance(prior.get("fields"), dict) else {}
    values: dict[str, Any] = {}
    statuses: dict[str, str] = {}

    payout_ratio = finite_number(statistics.get("payoutRatio"))
    values["payout_ratio"] = payout_ratio * 100 if payout_ratio is not None else prior.get("payout_ratio")
    statuses["payout_ratio"] = str(field_status.get("statistics_payoutRatio", "empty"))
    if statuses["payout_ratio"] in {"empty", "not_scheduled"} and prior.get("payout_ratio") is not None:
        values["payout_ratio"] = prior.get("payout_ratio")
        statuses["payout_ratio"] = str(prior_fields.get("payout_ratio", "stale"))

    latest_statement = max(
        (row for row in statements if isinstance(row, dict) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(row.get("period_end") or ""))),
        key=lambda row: str(row["period_end"]), default=None,
    )
    if latest_statement is not None:
        annual_status = latest_statement.get("field_status") if isinstance(latest_statement.get("field_status"), dict) else {}
        equity_ratio = finite_number(latest_statement.get("equity_ratio_computed"))
        if equity_ratio is not None and abs(equity_ratio) > 1000:
            equity_ratio = None
        sales = finite_number(latest_statement.get("sales"))
        operating_profit = finite_number(latest_statement.get("operating_profit"))
        operating_margin = operating_profit / sales * 100 if sales is not None and sales > 0 and operating_profit is not None else None
        values.update({"equity_ratio": equity_ratio, "operating_margin": operating_margin})
        statuses.update({
            "equity_ratio": str(annual_status.get("equity_ratio_computed", "empty")),
            "operating_margin": "available" if operating_margin is not None
            and annual_status.get("sales") == "available"
            and annual_status.get("operating_profit") == "available" else "empty",
        })
        period_end = latest_statement.get("period_end")
    else:
        values.update({"equity_ratio": prior.get("equity_ratio"), "operating_margin": prior.get("operating_margin")})
        statuses.update({
            "equity_ratio": str(prior_fields.get("equity_ratio", "not_fetched")),
            "operating_margin": str(prior_fields.get("operating_margin", "not_fetched")),
        })
        period_end = prior.get("financial_period_end")

    for metric, statistic in (("per", "trailingPE"), ("pbr", "priceToBook")):
        values[metric] = finite_number(statistics.get(statistic))
        statuses[metric] = str(field_status.get(f"statistics_{statistic}", "empty"))

    reported_operating_margin = finite_number(statistics.get("operatingMargins"))
    if reported_operating_margin is not None:
        values["operating_margin"] = reported_operating_margin * 100
        statuses["operating_margin"] = str(field_status.get("statistics_operatingMargins", "available"))

    dividend = current.get("dividend") if isinstance(current.get("dividend"), dict) else {}
    streak = dividend.get("calendar_year_metrics") if isinstance(dividend.get("calendar_year_metrics"), dict) else {}
    streak_status = str(streak.get("status") or field_status.get("calendar_year_dividend_metrics") or "not_fetched")
    for metric, source in (("consecutive_increase_years", "consecutive_increase_years"), ("no_cut_years", "no_cut_years")):
        value = finite_number(streak.get(source))
        values[metric] = value
        statuses[metric] = streak_status if value is not None else "empty" if streak_status == "available" else streak_status

    return {
        "fields": statuses,
        **values,
        "financial_period_end": period_end,
        "source": "Yahoo Finance via yfinance V2",
    }


def income_advice_score_breakdown(metrics: dict[str, Any]) -> tuple[int, dict[str, float]]:
    fields = metrics.get("fields") if isinstance(metrics.get("fields"), dict) else {}
    weights = {
        "payout_ratio": 20,
        "equity_ratio": 20,
        "operating_margin": 15,
        "per": 10,
        "pbr": 10,
        "consecutive_increase_years": 15,
        "no_cut_years": 10,
    }
    score_functions = {
        "payout_ratio": lambda value: 20 if 0 <= value <= 30 else 16 if 0 <= value <= 50 else 12 if 0 <= value <= 70 else 6 if 0 <= value <= 90 else 3 if 0 <= value <= 100 else 0,
        "equity_ratio": lambda value: 20 if value >= 60 else 16 if value >= 40 else 12 if value >= 30 else 8 if value >= 20 else 4 if value >= 10 else 0,
        "operating_margin": lambda value: 15 if value >= 15 else 12 if value >= 10 else 9 if value >= 5 else 5 if value >= 0 else 0,
        "per": lambda value: 10 if 0 < value <= 10 else 8 if 0 < value <= 15 else 6 if 0 < value <= 20 else 3 if 0 < value <= 30 else 0,
        "pbr": lambda value: 10 if 0 < value <= 1 else 8 if 0 < value <= 1.5 else 6 if 0 < value <= 2 else 3 if 0 < value <= 3 else 0,
        "consecutive_increase_years": lambda value: 15 if value >= 5 else 12 if value >= 4 else 9 if value >= 3 else 6 if value >= 2 else 3 if value >= 1 else 0,
        "no_cut_years": lambda value: 10 if value >= 10 else 8 if value >= 7 else 6 if value >= 4 else 3 if value >= 2 else 1 if value >= 1 else 0,
    }

    available_points: dict[str, float] = {}
    for name, score_function in score_functions.items():
        value = finite_number(metrics.get(name))
        if fields.get(name) == "available" and value is not None:
            available_points[name] = float(score_function(value))

    # Average normalized attainment so metrics with different point maxima remain comparable.
    # With no available metrics, use the neutral midpoint (50% of each metric's weight).
    average_attainment = (
        sum(available_points[name] / weights[name] for name in available_points) / len(available_points)
        if available_points else 0.5
    )
    points = {
        name: available_points.get(name, average_attainment * weight)
        for name, weight in weights.items()
    }
    total = sum(points.values())
    return int(total + 0.5), {
        name: round(average_attainment * weights[name], 2)
        for name in weights if name not in available_points
    }


def income_advice_score(metrics: dict[str, Any]) -> int:
    return income_advice_score_breakdown(metrics)[0]


def income_advice_candidate(code: str, name: str, current: dict[str, Any],
                            dividends: list[dict[str, Any]], as_of: date,
                            exclusion_counts: dict[str, int] | None = None) -> dict[str, Any] | None:
    def exclude(reason: str) -> None:
        if exclusion_counts is not None:
            exclusion_counts[reason] = exclusion_counts.get(reason, 0) + 1

    if str((current.get("price") or {}).get("currency") or "") != "JPY":
        exclude("currency_not_jpy")
        return None
    if current.get("field_status", {}).get("dividend_history") != "available":
        exclude("dividend_history_unavailable")
        return None
    latest_completed_year = as_of.year - 1
    years = list(range(latest_completed_year - 2, latest_completed_year + 1))
    event_month_years: dict[int, set[int]] = {}
    annual_totals = {year: 0.0 for year in years}
    for event in dividends:
        event_date = date_value(event.get("date"))
        amount = finite_number(event.get("amount"))
        if event_date is None or event_date.year not in annual_totals or amount is None or amount <= 0:
            continue
        event_month_years.setdefault(event_date.month, set()).add(event_date.year)
        annual_totals[event_date.year] += amount
    recurring_months = sorted(month for month, observed_years in event_month_years.items()
                              if len(observed_years.intersection(years)) >= 2)
    if not recurring_months:
        exclude("no_recurring_dividend_months")
        return None

    metrics = current.get("income_advice_metrics") if isinstance(current.get("income_advice_metrics"), dict) else {}
    score, imputed_score_points = income_advice_score_breakdown(metrics)
    dividend = current.get("dividend") if isinstance(current.get("dividend"), dict) else {}
    forecast = finite_number(dividend.get("next_fiscal_year_forecast_per_share"))
    forecast_status = current.get("field_status", {}).get("forecast_dividend")
    if forecast_status == "available" and forecast is not None and forecast > 0:
        annual_dps = forecast
        annual_basis = "next_fiscal_year_forecast"
    else:
        action_history = current.get("action_history") if isinstance(current.get("action_history"), dict) else {}
        coverage_end = date_value(action_history.get("full_history_coverage_end"))
        if coverage_end is None or coverage_end < date(latest_completed_year, 12, 31):
            exclude("annual_history_incomplete")
            return None
        annual_dps = annual_totals.get(latest_completed_year, 0.0)
        annual_basis = "previous_calendar_year_observed_distributions"
    if annual_dps <= 0:
        exclude("annual_dividend_amount_unavailable")
        return None
    return {
        "code": code,
        "name": name,
        "currency": "JPY",
        "annual_dividend_per_share": annual_dps,
        "annual_dividend_basis": annual_basis,
        "observed_months": recurring_months,
        "monthly_estimate_per_100_shares": round(annual_dps * 100 / len(recurring_months), 2),
        "score": score,
        "score_metric_imputed_points": imputed_score_points,
        "score_metrics": {key: metrics.get(key) for key in (
            "payout_ratio", "equity_ratio", "operating_margin", "per", "pbr",
            "consecutive_increase_years", "no_cut_years",
        )},
        "score_metric_status": dict(metrics.get("fields", {})),
        "score_metric_source": {
            "payout_ratio": "securities/{symbol}/current.json:statistics.payoutRatio",
            "equity_ratio": "latest annual financial row:equity_ratio_computed",
            "operating_margin": "latest annual financial row:operating_profit/sales",
            "per": "securities/{symbol}/current.json:statistics.trailingPE",
            "pbr": "securities/{symbol}/current.json:statistics.priceToBook",
            "consecutive_increase_years": "securities/{symbol}/current.json:dividend.calendar_year_metrics",
            "no_cut_years": "securities/{symbol}/current.json:dividend.calendar_year_metrics",
        },
        "financial_period_end": metrics.get("financial_period_end"),
        "dividend_history_years": years,
        "source_catalog_generation": None,  # Bound when included in a catalog generation.
        "computed_at": iso_now(),
        "source": "Yahoo Finance via yfinance V2",
    }


def catalog_item_from_documents(code: str, security: dict[str, Any], profile: dict[str, Any],
                                current: dict[str, Any], sync: dict[str, Any],
                                previous_name: str | None = None) -> dict[str, Any] | None:
    price = current.get("price") if isinstance(current.get("price"), dict) else {}
    close = finite_number(price.get("close"))
    if not profile or not current or close is None or close <= 0:
        return None
    dividend = current.get("dividend") if isinstance(current.get("dividend"), dict) else {}
    yield_basis = dividend.get("yield_basis")
    annual_dps = (
        finite_number(dividend.get("next_fiscal_year_forecast_per_share"))
        if yield_basis == "next_fiscal_year_forecast" else
        finite_number(dividend.get("current_fiscal_year_per_share"))
        if yield_basis == "current_fiscal_year" else
        0.0 if yield_basis == "yfinance_reported_no_dividend" or dividend.get("dividend_status") == "no_dividend" else
        None
    )
    if yield_basis == "next_fiscal_year_forecast" and annual_dps is not None:
        annual_dps = next_fiscal_year_forecast_dps(
            code, annual_dps, date_value(current.get("updated_at")) or date.today()
        )
    current_statistics = current.get("statistics") if isinstance(current.get("statistics"), dict) else {}
    reported_yield = finite_number(current_statistics.get("dividendYield"))
    reported_yield_percent = (
        round(reported_yield * 100, 4)
        if reported_yield is not None and 0 <= reported_yield <= 1 else None
    )
    annual_yield = reported_yield_percent if reported_yield_percent is not None else (
        finite_number(dividend.get("yield_rate"))
        if dividend.get("yield_rate") is not None else
        round(annual_dps / close * 100, 4)
        if annual_dps is not None and close is not None and close > 0 else
        None
    )
    return {
        "code": code,
        "name": profile.get("display_name") or previous_name or code,
        "category": "jp",
        "security_type": security.get("security_type", "listed_security"),
        "jquants_product_category": security.get("jquants_product_category") or None,
        "price": price.get("close"),
        "quote_date": price.get("date"),
        "currency": price.get("currency"),
        "annual_dividend_per_share": annual_dps,
        "trailing_12_month_distribution_per_share": dividend.get("trailing_12_month_per_share"),
        "dividend_period": yield_basis or "unknown",
        "dividend_amount_basis": dividend.get("event_amount_basis"),
        "yield_rate": annual_yield,
        "yield_source": "yfinance_reported_dividendYield" if reported_yield_percent is not None
        else dividend.get("yield_source") or ("calculated_from_yfinance_dividend_and_quote" if annual_yield is not None else None),
        "yield_basis": dividend.get("yield_basis"),
        "dividend_status": dividend.get("dividend_status", "unknown"),
        "status": sync.get("status") if sync.get("status") in {"ok", "partial", "failed"} else "ok",
        "failure_reason": sync.get("failure_reason"),
    }


def restore_catalog_item(client: Any, bucket: str, code: str, security: dict[str, Any],
                         state: dict[str, Any], session_date: date) -> dict[str, Any]:
    """Recover a completed per-security write even if catalog publication was interrupted."""
    symbol = market_symbol(code)
    safe = safe_symbol(symbol)
    profile = read_json(client, bucket, object_key("securities", safe, "profile.json")) or {}
    current = read_json(client, bucket, object_key("securities", safe, "current.json")) or {}
    item = catalog_item_from_documents(code, security, profile, current, state)
    if item is None:
        raise RuntimeError(f"Completed security has no publishable current record: {code}")
    dividends = load_year_records(client, bucket, symbol, "dividends",
                                  set(range(session_date.year - 3, session_date.year)), include_archive=False)
    item["income_advice"] = income_advice_candidate(code, item["name"], current, dividends, session_date)
    return item


def reusable_same_session_catalog_item(
    previous_items: dict[str, Any], catalog_session_date: Any, code: str, session_date: date,
) -> dict[str, Any] | None:
    """Reuse an already published successful item for duplicate runs of one session."""
    if catalog_session_date != session_date.isoformat():
        return None
    item = previous_items.get(code)
    if not isinstance(item, dict) or item.get("status") != "ok" or item.get("failure_reason"):
        return None
    return dict(item)


def publish_catalog(client: Any, bucket: str, universe: list[dict[str, str]], session_date: date,
                    previous_catalog: dict[str, Any] | None,
                    processed_items: dict[str, dict[str, Any]] | None = None,
                    failures: dict[str, str] | None = None) -> dict[str, Any]:
    generation = f"{session_date.isoformat()}-{uuid4().hex}"
    old_items = {str(row.get("code")): row for row in (previous_catalog or {}).get("items", []) if isinstance(row, dict)}
    processed_items = processed_items or {}
    failures = failures or {}
    items: list[dict[str, Any]] = []
    for security in universe:
        code = security["security_code_display"]
        old_item = old_items.get(code)
        item = processed_items.get(code)
        if item is None and old_item and old_item.get("dividend_period") == "trailing_12_months":
            symbol = market_symbol(code)
            safe = safe_symbol(symbol)
            profile = read_json(client, bucket, object_key("securities", safe, "profile.json"))
            current = read_json(client, bucket, object_key("securities", safe, "current.json"))
            sync = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
            item = catalog_item_from_documents(
                code, security, profile or {}, current or {}, sync,
                previous_name=old_item.get("name"),
            )
            if item is None:
                # Preserve quote metadata while making the unknown DPS basis explicit.
                item = {
                    **old_item,
                    "annual_dividend_per_share": None,
                    "dividend_period": "unknown",
                    "yield_rate": None,
                    "yield_basis": None,
                    "dividend_status": "unknown",
                }
        if item is None:
            item = old_item
        if item is None:
            symbol = market_symbol(code)
            safe = safe_symbol(symbol)
            profile = read_json(client, bucket, object_key("securities", safe, "profile.json"))
            current = read_json(client, bucket, object_key("securities", safe, "current.json"))
            sync = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
            item = catalog_item_from_documents(
                code, security, profile or {}, current or {}, sync,
                previous_name=old_items.get(code, {}).get("name"),
            )
        if item is None:
            continue
        item = {**item}
        if isinstance(item.get("income_advice"), dict):
            item["income_advice"] = {
                **item["income_advice"], "source_catalog_generation": generation,
            }
        if code in failures:
            item["status"] = "failed"
            item["failure_reason"] = failures[code][:500]
        items.append(item)
    items.sort(key=lambda item: item["code"])
    if len(items) != len({item["code"] for item in items}):
        raise RuntimeError("v2 search catalog contains duplicate security codes")
    return {
        "schema_version": 1,
        "generation": generation,
        "generated_at": iso_now(),
        "market_data_date": session_date.isoformat(),
        "ranking": "security_code",
        "items": items,
    }


def active_universe(client: Any, bucket: str, fetched: list[dict[str, str]], session_date: date) -> list[dict[str, Any]]:
    old = read_json(client, bucket, object_key("universe", "current.json")) or {}
    old_by_code = {str(row.get("code")): row for row in old.get("securities", []) if isinstance(row, dict)}
    rows = []
    for entry in fetched:
        code = entry["security_code_display"]
        previous = old_by_code.get(code, {})
        rows.append({
            "code": code,
            "symbol": market_symbol(code),
            "security_type": entry.get("security_type", "listed_security"),
            "jquants_product_category": entry.get("jquants_product_category") or None,
            "status": "active",
            "first_seen": previous.get("first_seen") or session_date.isoformat(),
            "last_seen": session_date.isoformat(),
            "industry_source": "android_asset_master",
        })
    current_codes = {row["code"] for row in rows}
    for code, previous in old_by_code.items():
        if code not in current_codes:
            rows.append({**previous, "status": "inactive", "inactive_since": previous.get("inactive_since") or session_date.isoformat()})
    rows.sort(key=lambda row: row["code"])
    return rows


def run(
    *, publish: bool,
    max_workers: int,
    dry_run: bool,
    reprocess_successful: bool = False,
    allow_one_extra_attempt: bool = False,
    refresh_company_info: bool = False,
    target_session: date | None = None,
) -> int:
    started_at = time.perf_counter()
    with _YF_CALL_LOCK:
        _YF_CALLS.clear()
        _ACTION_HISTORY_REPAIR_TRIGGERS.clear()
    if not publish and not dry_run:
        raise RuntimeError("Specify --publish or --dry-run")
    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    api_key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("JQUANTS_API_KEY is required")
    phase_seconds: dict[str, float] = {}
    phase_started = time.perf_counter()
    universe_snapshot_path = os.environ.get("YFINANCE_UNIVERSE_SNAPSHOT_PATH", "").strip()
    fetched = fetch_securities(
        api_key,
        snapshot_path=Path(universe_snapshot_path) if universe_snapshot_path else None,
    )
    phase_seconds["jquants_universe_fetch"] = round(time.perf_counter() - phase_started, 3)
    latest_session = latest_completed_xtks_session()
    session_date = target_session or latest_session
    if target_session is not None:
        import exchange_calendars as xcals
        import pandas as pd

        if not xcals.get_calendar("XTKS").is_session(pd.Timestamp(target_session)):
            raise RuntimeError(f"Requested sync date is not an XTKS session: {target_session}")
        if target_session > latest_session:
            raise RuntimeError(
                f"Requested sync date {target_session} is later than the latest completed XTKS session {latest_session}"
            )
    r2_metrics = R2Metrics()
    client = MeasuredR2Client(collector.get_client(), r2_metrics) if publish else None
    bucket = collector.required_env("R2_BUCKET") if publish else ""
    if dry_run:
        print(f"J-Quants universe: {len(fetched)} listed issues; session date={session_date}")
        print("Dry-run validates the universe only; no yfinance calls or R2 writes were made.")
        return 0

    phase_started = time.perf_counter()
    old_universe = read_json(client, bucket, object_key("universe", "current.json")) or {}
    old_status = {str(row.get("code")): row for row in old_universe.get("securities", []) if isinstance(row, dict)}
    previous_catalog = read_json(client, bucket, object_key("catalog", "current.json"))
    previous_catalog_items = {
        str(row.get("code")): row
        for row in (previous_catalog or {}).get("items", [])
        if isinstance(row, dict) and row.get("code") is not None
    }
    previous_catalog_session_date = (previous_catalog or {}).get("market_data_date")
    schedule_key = object_key("schedules", "current.json")
    schedule_state = read_json(client, bucket, schedule_key) or {}
    phase_seconds["initial_r2_state"] = round(time.perf_counter() - phase_started, 3)
    active_session = date_value(schedule_state.get("active_session_date"))
    if active_session is not None and active_session > session_date:
        print(
            f"Skipping stale scheduled session {session_date}; R2 already publishes newer session {active_session}.",
            flush=True,
        )
        return 0
    if schedule_state.get("active_session_date") == session_date.isoformat():
        full_history_bucket = int(schedule_state.get("active_full_history_bucket", 0)) % FULL_HISTORY_BUCKETS
        financial_bucket = int(schedule_state.get("active_financial_bucket", 0)) % FINANCIAL_BUCKETS
    else:
        full_history_bucket = int(schedule_state.get("next_full_history_bucket", 0)) % FULL_HISTORY_BUCKETS
        financial_bucket = int(schedule_state.get("next_financial_bucket", 0)) % FINANCIAL_BUCKETS
    month_end_session = is_last_market_session(session_date)
    december_end_session = month_end_session and session_date.month == 12
    if refresh_company_info:
        print("Manual full-universe mode: forcing yfinance Ticker.get_info() for every active security.", flush=True)
    print(
        f"Rotation: long_history={full_history_bucket + 1}/{FULL_HISTORY_BUCKETS}, "
        f"financials={financial_bucket + 1}/{FINANCIAL_BUCKETS}, "
        f"company_info={full_history_bucket + 1}/{COMPANY_INFO_BUCKETS}, "
        f"december_price_refresh={december_end_session}",
        flush=True,
    )
    securities = [row["security_code_display"] for row in fetched]
    security_by_code = {row["security_code_display"]: row for row in fetched}
    results: dict[str, dict[str, Any]] = {}
    failures: dict[str, str] = {}

    def process(code: str) -> dict[str, Any]:
        with r2_document_cache_scope():
            return process_with_cache(code)

    def process_with_cache(code: str) -> dict[str, Any]:
        symbol = market_symbol(code)
        previous_sync = read_json(client, bucket, object_key("securities", safe_symbol(symbol), "sync.json")) or {}
        attempts_used = 0
        if previous_sync.get("retry_session_date") == session_date.isoformat():
            try:
                attempts_used = max(0, int(previous_sync.get("attempt_count_today", 0)))
            except (TypeError, ValueError):
                attempts_used = 0
        if skip_same_session_no_data_retry(previous_sync, session_date):
            return {
                "code": code,
                "symbol": symbol,
                "status": "failed",
                "skip_no_data_retry": True,
                "failure_reason": previous_sync.get("failure_reason") or "Yahoo returned no data",
            }
        if (previous_sync.get("status") == "ok"
                and previous_sync.get("last_success_session_date") == session_date.isoformat()
                and not reprocess_successful):
            catalog_item = reusable_same_session_catalog_item(
                previous_catalog_items, previous_catalog_session_date, code, session_date,
            )
            if catalog_item is None:
                catalog_item = restore_catalog_item(
                    client, bucket, code, security_by_code[code], previous_sync, session_date,
                )
            return {"code": code, "symbol": symbol, "status": "ok", "field_failures": [],
                    "catalog_item": catalog_item}
        attempt_limit = MAX_DAILY_ATTEMPTS + (1 if allow_one_extra_attempt else 0)
        if attempts_used >= attempt_limit:
            reason = str(previous_sync.get("failure_reason") or "")
            fields = reason.removeprefix("Field fetch failed: ").split(", ") if reason else []
            if previous_sync.get("status") == "partial":
                return {"code": code, "symbol": symbol, "status": "partial", "field_failures": fields,
                        "catalog_item": restore_catalog_item(client, bucket, code, security_by_code[code],
                                                             previous_sync, session_date)}
            raise RuntimeError("Daily per-security attempt limit reached")
        attempt_count = attempts_used + 1
        retry_full_history = (bucket_for(code, FULL_HISTORY_BUCKETS) == full_history_bucket
                              or previous_sync.get("long_history_retry_pending") is True)
        retry_financials = previous_sync.get("financial_retry_pending") is True
        refresh_info = should_refresh_company_info(
            code,
            full_history_bucket,
            bool(previous_sync.get("last_success_session_date")),
            previous_sync.get("info_refresh_pending") is True,
        ) or refresh_company_info
        initialize_financials = (
            not previous_sync
            or (bucket_for(code, FINANCIAL_BUCKETS) == financial_bucket
                and previous_sync.get("financial_refresh_policy_version") != FINANCIAL_REFRESH_POLICY_VERSION)
        )
        previous_monthly = previous_sync.get("monthly_history_schema_version") == 2
        initialize_monthly = bucket_for(code, FULL_HISTORY_BUCKETS) == full_history_bucket
        retry_monthly = (
            previous_sync.get("monthly_retry_pending") is True
            or (december_end_session and previous_monthly)
            or (initialize_monthly and not previous_monthly)
        )
        running_sync = record_attempt_start(
            client, bucket, code, symbol, session_date, attempt_count,
            previous_sync=previous_sync,
        )
        try:
            return collect_symbol(
                client, bucket, code, symbol, session_date,
                attempt_count=attempt_count,
                refresh_full_history=retry_full_history,
                refresh_financials=initialize_financials,
                refresh_info=refresh_info,
                force_refresh_info=refresh_company_info,
                refresh_monthly=december_end_session,
                initialize_monthly=initialize_monthly,
                retry_monthly=retry_monthly,
                universe_entry=security_by_code.get(code),
                prior_sync_document=running_sync,
            )
        except Exception as exc:
            save_failure(
                client, bucket, code, symbol, session_date, exc,
                attempt_count=attempt_count, retry_full_history=retry_full_history,
                retry_financials=retry_financials, retry_monthly=retry_monthly,
                retry_info=refresh_info,
            )
            raise

    completed_count = 0
    rate_limiter = GlobalHttpRateLimiter(
        float(os.environ.get("YFINANCE_HTTP_MAX_REQUESTS_PER_SECOND", "8"))
    )
    rate_limiter.activity = r2_metrics.activity
    phase_started = time.perf_counter()
    with rate_limiter:
        with ThreadPoolExecutor(max_workers=max(1, max_workers)) as pool:
            futures = {pool.submit(process, code): code for code in securities}
            for future in as_completed(futures):
                completed_count += 1
                code = futures[future]
                try:
                    result = future.result()
                    if result.get("skip_no_data_retry"):
                        failures[code] = str(result.get("failure_reason") or "Yahoo returned no data")
                        print(f"SKIP same-session no-data retry {code}: {failures[code]}", flush=True)
                    else:
                        results[code] = result
                except Exception as exc:
                    log_attribute_error_traceback(exc)
                    failures[code] = f"{type(exc).__name__}: {exc}"
                    print(f"FAILED {code}: {failures[code]}", file=sys.stderr, flush=True)
                if completed_count % 100 == 0 or completed_count == len(futures):
                    with _YF_CALL_LOCK:
                        partial_call_count = sum(_YF_CALLS.values())
                    print(
                        f"Progress {completed_count}/{len(futures)}: success={len(results)}, "
                        f"failed={len(failures)}, elapsed={time.perf_counter() - started_at:.1f}s, "
                        f"yfinance_calls={partial_call_count}",
                        flush=True,
                    )

    collection_ended = time.perf_counter()
    phase_seconds["security_collection"] = round(collection_ended - phase_started, 3)
    collection_activity = r2_metrics.activity.snapshot(phase_started, collection_ended)
    phase_started = time.perf_counter()
    universe_rows = active_universe(client, bucket, fetched, session_date)
    for row in universe_rows:
        code = row["code"]
        if row["status"] != "active":
            continue
        if code in failures:
            row["sync_status"] = "failed"
            row["failure_reason"] = failures[code][:500]
        elif code in results:
            row["sync_status"] = results[code].get("status", "ok")
            if results[code].get("status") == "partial":
                row["failure_reason"] = f"Field fetch failed: {', '.join(results[code].get('field_failures', []))}"[:500]
        else:
            row["sync_status"] = old_status.get(code, {}).get("sync_status", "pending")
    phase_seconds["active_universe"] = round(time.perf_counter() - phase_started, 3)
    phase_started = time.perf_counter()
    processed_items = {
        code: result["catalog_item"]
        for code, result in results.items()
        if isinstance(result.get("catalog_item"), dict)
    }
    catalog = publish_catalog(
        client, bucket, fetched, session_date, previous_catalog,
        processed_items=processed_items, failures=failures,
    )
    generation = catalog["generation"]
    income_advice_catalog = {
        "schema_version": 1,
        "generation": generation,
        "generated_at": iso_now(),
        "source": "Yahoo Finance via yfinance V2",
        "amount_basis": "per_share_annual_amount_and_recurring_event_months",
        "items": [
            row["income_advice"]
            for row in catalog["items"]
            if row.get("code") not in failures
            and row.get("status") != "failed"
            and isinstance(row.get("income_advice"), dict)
        ],
    }
    phase_seconds["catalog_build"] = round(time.perf_counter() - phase_started, 3)
    phase_started = time.perf_counter()
    put_json_if_changed(client, bucket, object_key("universe", "current.json"), {
        "schema_version": 1, "updated_at": iso_now(), "source": "J-Quants API v2",
        "session_date": session_date.isoformat(), "security_count": len(fetched), "securities": universe_rows,
    })
    put_json_if_changed(client, bucket, object_key("catalog", "generations", f"{generation}.json"), catalog)
    put_json_if_changed(client, bucket, object_key("catalog", "current.json"), catalog)
    put_json_if_changed(client, bucket, object_key("derived", "income_advice", "current.json"), income_advice_catalog)
    phase_seconds["catalog_publication"] = round(time.perf_counter() - phase_started, 3)
    elapsed_seconds = round(time.perf_counter() - started_at, 3)
    with _YF_CALL_LOCK:
        yfinance_calls = dict(sorted(_YF_CALLS.items()))
        action_history_repair_triggers = dict(sorted(_ACTION_HISTORY_REPAIR_TRIGGERS.items()))
    company_info_refresh_attempt_count = yfinance_calls.get("ticker_get_info", 0)
    company_info_refreshed_count = sum(1 for row in results.values() if row.get("info_refreshed"))
    publication = {
        "schema_version": 1,
        "yfinance_version": yf.__version__,
        "generation": generation,
        "market_data_date": session_date.isoformat(),
        "last_completed_at": iso_now(),
        "security_count": len(fetched),
        "success_count": len(results),
        "partial_count": sum(1 for row in results.values() if row.get("status") == "partial"),
        "failure_count": len(failures),
        "company_info_refresh_attempt_count": company_info_refresh_attempt_count,
        "company_info_refreshed_count": company_info_refreshed_count,
        "company_info_refresh_failed_count": max(
            0, company_info_refresh_attempt_count - company_info_refreshed_count,
        ),
        "catalog_count": len(catalog["items"]),
        "elapsed_seconds": elapsed_seconds,
        "yfinance_call_count": sum(yfinance_calls.values()),
        "yfinance_calls": yfinance_calls,
        "action_history_repair_triggers": action_history_repair_triggers,
        "http_max_requests_per_second": rate_limiter.requests_per_second,
        "http_request_count": rate_limiter.request_count,
        "http_429_count": rate_limiter.http_429_count,
        "phase_seconds": phase_seconds,
        "security_collection_activity": collection_activity,
        "r2_metrics_before_publication_record": r2_metrics.snapshot(),
        "rotation": {
            "full_history_bucket": full_history_bucket,
            "full_history_bucket_count": FULL_HISTORY_BUCKETS,
            "financial_bucket": financial_bucket,
            "financial_bucket_count": FINANCIAL_BUCKETS,
            "company_info_bucket": full_history_bucket,
            "company_info_bucket_count": COMPANY_INFO_BUCKETS,
            "company_info_refresh_policy_version": COMPANY_INFO_REFRESH_POLICY_VERSION,
            "december_end_session": december_end_session,
        },
    }
    put_json_if_changed(client, bucket, object_key("publications", "current.json"), publication)
    put_json_if_changed(client, bucket, object_key("manifest.json"), {
        "schema_version": 1, "active_generation": generation,
        "generated_at": iso_now(), "source": "Yahoo Finance via yfinance",
        "yfinance_version": yf.__version__,
        "universe_source": "J-Quants API v2", "security_count": len(fetched),
        "catalog_count": len(catalog["items"]),
    })
    put_json_if_changed(client, bucket, schedule_key, {
        "schema_version": 1,
        "active_session_date": session_date.isoformat(),
        "active_full_history_bucket": full_history_bucket,
        "active_financial_bucket": financial_bucket,
        "next_full_history_bucket": (full_history_bucket + 1) % FULL_HISTORY_BUCKETS,
        "next_financial_bucket": (financial_bucket + 1) % FINANCIAL_BUCKETS,
        "updated_at": iso_now(),
    })
    print(
        f"Published v2 generation {generation}: success={len(results)}, failed={len(failures)}, "
        f"catalog={len(catalog['items'])}, elapsed={elapsed_seconds}s, "
        f"yfinance_calls={sum(yfinance_calls.values())} {json.dumps(yfinance_calls, ensure_ascii=False, sort_keys=True)}"
    )
    print(
        "Action-history repair triggers (overlapping): "
        f"{json.dumps(action_history_repair_triggers, ensure_ascii=False, sort_keys=True)}",
        flush=True,
    )
    print(
        "Phase timings: " + json.dumps(phase_seconds, ensure_ascii=False, sort_keys=True)
        + "; security-collection activity: " + json.dumps(collection_activity, ensure_ascii=False, sort_keys=True)
        + "; R2 operations: " + json.dumps(r2_metrics.snapshot(), ensure_ascii=False, sort_keys=True),
        flush=True,
    )
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publish", action="store_true", help="Write the v2 generation to R2")
    parser.add_argument("--dry-run", action="store_true", help="Fetch the J-Quants universe only; do not access R2 or yfinance")
    parser.add_argument("--workers", type=int, default=int(os.environ.get("YFINANCE_V2_WORKERS", "8")))
    parser.add_argument(
        "--session-date", type=date.fromisoformat,
        help="XTKS session selected by the workflow gate; defaults to the latest completed session",
    )
    parser.add_argument(
        "--reprocess-successful", action="store_true",
        help="Re-fetch securities already successful this session; intended for controlled full-universe timing studies",
    )
    parser.add_argument(
        "--allow-one-extra-attempt", action="store_true",
        help="Allow one additional per-security attempt beyond the daily cap for a controlled manual backfill",
    )
    parser.add_argument(
        "--refresh-company-info", action="store_true",
        help="Refresh yfinance company profile and current statistics for every active security",
    )
    args = parser.parse_args()
    return run(
        publish=args.publish,
        max_workers=args.workers,
        dry_run=args.dry_run,
        reprocess_successful=args.reprocess_successful,
        allow_one_extra_attempt=args.allow_one_extra_attempt,
        refresh_company_info=args.refresh_company_info,
        target_session=args.session_date,
    )


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
