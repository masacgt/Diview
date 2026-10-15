"""Synchronize the app's Japanese listed-security universe to the R2 v2 schema."""

from __future__ import annotations

import argparse
import gzip
import json
import math
import os
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime, timedelta, timezone
from typing import Any
from zoneinfo import ZoneInfo

import yfinance as yf

# Surface yfinance request/repair exceptions to the per-security retry state machine.
yf.config.debug.hide_exceptions = False

import fetch_and_upload as collector
from fetch_jquants_universe import fetch_securities


UTC = timezone.utc
JST = ZoneInfo("Asia/Tokyo")
ROOT = "market/yfinance/v2"
OLD_ROOT = "market/yfinance"
RETRY_DELAYS = (15 * 60, 60 * 60, 4 * 60 * 60)
PROFILE_FIELDS = (
    "quoteType", "exchange", "fullExchangeName", "market", "currency", "country",
    "website", "longBusinessSummary", "marketCap", "sharesOutstanding",
)
CURRENT_FIELDS = (
    "regularMarketChange", "regularMarketChangePercent", "trailingPE", "forwardPE",
    "priceToBook", "enterpriseValue", "trailingEps", "forwardEps", "bookValue",
    "dividendRate", "dividendYield", "payoutRatio", "fiveYearAvgDividendYield", "lastDividendValue",
    "lastDividendDate", "exDividendDate", "beta", "lastFiscalYearEnd", "mostRecentQuarter",
)
INCOME_FIELDS = {
    "Total Revenue": "sales",
    "Operating Revenue": "sales",
    "Operating Income": "operating_profit",
    "Net Income": "net_income",
    "Basic EPS": "eps",
    "Diluted EPS": "eps_diluted",
}
BALANCE_FIELDS = {
    "Stockholders Equity": "stockholders_equity",
    "Total Equity Gross Minority Interest": "stockholders_equity",
    "Total Assets": "total_assets",
    "Ordinary Shares Number": "shares_outstanding",
}
CASH_FLOW_FIELDS = {
    "Operating Cash Flow": "operating_cf",
    "Investing Cash Flow": "investing_cf",
    "Capital Expenditure": "capital_expenditure",
}


def now_utc() -> datetime:
    return datetime.now(UTC)


def iso_now() -> str:
    return now_utc().isoformat()


def safe_symbol(symbol: str) -> str:
    return symbol.replace("^", "_").replace("=", "_").replace("/", "_")


def market_symbol(code: str) -> str:
    normalized = str(code).strip().upper()
    if not re.fullmatch(r"[A-Z0-9]{4,5}", normalized):
        raise ValueError(f"Invalid J-Quants security code: {code!r}")
    return f"{normalized}.T"


def object_key(*parts: str) -> str:
    return "/".join((ROOT, *parts))


def read_json(client: Any, bucket: str, key: str) -> dict[str, Any] | None:
    raw = collector.read_object(client, bucket, key)
    if raw is None:
        return None
    value = json.loads(raw)
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object at {key}")
    return value


def put_json_if_changed(client: Any, bucket: str, key: str, value: dict[str, Any]) -> bool:
    previous = collector.read_object(client, bucket, key)
    return collector.put_json_if_changed(client, bucket, key, value, previous)


def finite_number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    result = float(value)
    return result if math.isfinite(result) else None


def date_value(value: Any) -> date | None:
    if value is None:
        return None
    try:
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(float(value), UTC).date()
        if hasattr(value, "date"):
            return value.date()
        return date.fromisoformat(str(value)[:10])
    except (ValueError, TypeError, OSError, OverflowError):
        return None


def years_before(day: date, years: int) -> date:
    try:
        return day.replace(year=day.year - years)
    except ValueError:
        return day.replace(year=day.year - years, day=28)


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
        for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
            for item in page.get("Contents", []):
                document = read_json(client, bucket, item["Key"])
                if document:
                    records.extend(row for row in document.get("records", []) if isinstance(row, dict))
    return records


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


def fetch_repaired_action_history(ticker: Any, as_of: date) -> tuple[
    list[dict[str, Any]], list[dict[str, Any]], date, date
]:
    """Fetch Yahoo's complete daily action history with yfinance's repair pass enabled."""
    frame = ticker.history(
        period="max", interval="1d", auto_adjust=False, actions=True,
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


def replace_dated_history(
    previous: list[dict[str, Any]], fresh: list[dict[str, Any]], key: str,
    coverage_start: date, coverage_end: date,
) -> list[dict[str, Any]]:
    """Replace all stored actions in a successfully fetched range, including removed/corrected events."""
    start_text = coverage_start.isoformat()
    end_text = coverage_end.isoformat()
    retained = [
        row for row in previous
        if str(row.get(key) or "")[:10] < start_text or str(row.get(key) or "")[:10] > end_text
    ]
    return collector.merge_dated(retained, fresh, key)


def monthly_history_rows(ticker: Any, *, initial: bool) -> list[dict[str, Any]]:
    frame = ticker.history(
        period="10y" if initial else "3mo", interval="1d", auto_adjust=False,
        actions=False, repair=True,
        timeout=float(os.environ.get("YFINANCE_HTTP_TIMEOUT_SECONDS", "30")),
    )
    latest_by_month: dict[str, dict[str, Any]] = {}
    if frame is None or frame.empty:
        return []
    for timestamp, row in frame.iterrows():
        point_date = date_value(timestamp)
        close = finite_number(row.get("Close"))
        if point_date and close and close > 0:
            month = point_date.strftime("%Y-%m")
            point = {"month": month, "date": point_date.isoformat(), "close": close}
            if month not in latest_by_month or point["date"] > latest_by_month[month]["date"]:
                latest_by_month[month] = point
    return [latest_by_month[month] for month in sorted(latest_by_month)]


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
    cutoff_archive = years_before(as_of, 10)
    # Hot storage includes the current month and the previous twelve months.
    for year in range(as_of.year - 10, as_of.year + 1):
        points = read_monthly_points(client, bucket, symbol, year)
        if year == as_of.year:
            points.extend(point for point in fresh if point["month"].startswith(f"{year}-"))
        if not points:
            continue
        unique = {str(item.get("month")): item for item in points if item.get("month")}
        merged = sorted(unique.values(), key=lambda item: item["month"])
        hot: list[dict[str, Any]] = []
        archived: list[dict[str, Any]] = []
        for point in merged:
            month = str(point["month"])
            month_date = date.fromisoformat(f"{month}-01")
            point_date = date.fromisoformat(str(point.get("date") or f"{month}-01")[:10])
            if point_date < cutoff_archive:
                continue
            if month_date < cutoff_hot:
                archived.append(point)
            else:
                hot.append(point)
        hot_key = object_key("securities", safe, "prices", "monthly", f"{year}.json")
        archive_key = object_key("archive", "prices", "monthly", safe, f"{year}.json")
        if hot:
            put_json_if_changed(client, bucket, hot_key, {
                "schema_version": 1, "symbol": symbol, "year": year, "points": hot,
                "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
            })
        if archived:
            archive_document = {
                "schema_version": 1, "symbol": symbol, "year": year, "points": archived,
                "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
            }
            put_json_if_changed(client, bucket, archive_key, archive_document)
            saved = read_json(client, bucket, archive_key)
            if not saved or {item.get("month") for item in saved.get("points", [])} != {item.get("month") for item in archived}:
                raise RuntimeError(f"Monthly price archive read-back failed: {archive_key}")
        if not hot:
            client.delete_object(Bucket=bucket, Key=hot_key)
        if not archived:
            client.delete_object(Bucket=bucket, Key=archive_key)

    # Remove year partitions that have aged completely beyond the ten-year window.
    expired_year = as_of.year - 10
    for prefix in (
        object_key("archive", "prices", "monthly", safe) + "/",
        object_key("securities", safe, "prices", "monthly") + "/",
    ):
        for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=prefix):
            for item in page.get("Contents", []):
                match = re.search(r"/(\d{4})\.json$", str(item.get("Key", "")))
                if match and int(match.group(1)) < expired_year:
                    client.delete_object(Bucket=bucket, Key=item["Key"])


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


def prior_financial_maps(client: Any, bucket: str, symbol: str) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]]]:
    safe = safe_symbol(symbol)
    prefix = object_key("securities", safe, "financials") + "/"
    statements: dict[str, dict[str, Any]] = {}
    valuations: dict[str, dict[str, Any]] = {}
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=prefix):
        for item in page.get("Contents", []):
            document = read_json(client, bucket, item["Key"])
            annual = document.get("annual") if document else None
            if not isinstance(annual, dict):
                continue
            period_end = str(annual.get("period_end") or "")[:10]
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", period_end):
                continue
            statements[period_end] = {key: value for key, value in annual.items()
                                      if key not in {"fiscal_year", "period_end", "field_status"}}
            valuation = document.get("valuation")
            if isinstance(valuation, dict):
                valuations[period_end] = {key: value for key, value in valuation.items()
                                          if key not in {"fiscal_year", "period_end", "field_status"}}
    return statements, valuations


def collect_financials(ticker: Any, old: dict[str, Any], client: Any, bucket: str, symbol: str
                       ) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    retrieval_status: dict[str, Any] = {}

    def fetch_metrics(name: str, request: Any, fields: dict[str, str]) -> dict[str, dict[str, Any]]:
        try:
            result = frame_metric_map(request(), fields)
            retrieval_status[name] = "available" if result else "empty"
            return result
        except Exception as exc:
            retrieval_status[name] = "failed"
            print(f"Financial field {name} unavailable: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
            return {}

    fresh_income = fetch_metrics("income_statement", lambda: ticker.get_income_stmt(freq="yearly"), INCOME_FIELDS)
    fresh_balance = fetch_metrics("balance_sheet", lambda: ticker.get_balance_sheet(freq="yearly"), BALANCE_FIELDS)
    fresh_cash = fetch_metrics("cash_flow", lambda: ticker.get_cash_flow(freq="yearly"), CASH_FLOW_FIELDS)
    legacy_income = legacy_frame_map(old, "income_statement", INCOME_FIELDS)
    legacy_balance = legacy_frame_map(old, "balance_sheet", BALANCE_FIELDS)
    legacy_cash = legacy_frame_map(old, "cash_flow", CASH_FLOW_FIELDS)
    prior_v2, prior_v2_valuation = prior_financial_maps(client, bucket, symbol)
    try:
        valuation_frame = ticker.get_valuation_measures(freq="yearly", periods=10)
        retrieval_status["valuation"] = "available" if valuation_frame is not None and not getattr(valuation_frame, "empty", True) else "empty"
    except Exception as exc:
        valuation_frame = None
        retrieval_status["valuation"] = "failed"
        print(f"Financial field valuation unavailable: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
    valuation: dict[str, dict[str, Any]] = {}
    if valuation_frame is not None and not getattr(valuation_frame, "empty", True):
        for period in valuation_frame.columns:
            period_date = date_value(period)
            if not period_date:
                continue
            values: dict[str, Any] = {}
            for source, target in (
                ("EPS", "eps_adjusted"), ("Book Value", "bps_adjusted"),
                ("Dividend Yield", "dividend_yield_adjusted"),
            ):
                if source in valuation_frame.index:
                    value = finite_number(valuation_frame.loc[source, period])
                    if value is not None:
                        values[target] = value
            valuation[period_date.isoformat()] = values

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

    keys = sorted(set(fresh_income) | set(fresh_balance) | set(fresh_cash) | set(valuation)
                  | set(legacy_income) | set(legacy_balance) | set(legacy_cash) | set(legacy_valuation)
                  | set(prior_v2) | set(prior_v2_valuation))[-10:]
    statements: list[dict[str, Any]] = []
    valuation_rows: list[dict[str, Any]] = []
    for period_end in keys:
        old_values = {**legacy_income.get(period_end, {}), **legacy_balance.get(period_end, {}),
                      **legacy_cash.get(period_end, {}), **prior_v2.get(period_end, {})}
        fresh_values = {**fresh_income.get(period_end, {}), **fresh_balance.get(period_end, {}), **fresh_cash.get(period_end, {})}
        values = {**old_values, **fresh_values}
        equity = finite_number(values.get("stockholders_equity"))
        assets = finite_number(values.get("total_assets"))
        if equity is not None and assets and assets > 0:
            values["equity_ratio_computed"] = equity / assets * 100
        statements.append({
            "fiscal_year": f"{period_end[:4]}/{period_end[5:7]}",
            "period_end": period_end,
            "field_status": {
                field: "available" if field in fresh_values else "stale" if field in old_values else field_retrieval_status.get(field, "empty")
                for source, fields in (("income_statement", INCOME_FIELDS), ("balance_sheet", BALANCE_FIELDS), ("cash_flow", CASH_FLOW_FIELDS))
                for field in fields.values()
            },
            **values,
        })
        fresh_valuation = valuation.get(period_end, {})
        valuation_values = {**legacy_valuation.get(period_end, {}), **prior_v2_valuation.get(period_end, {}), **fresh_valuation}
        if valuation_values:
            valuation_rows.append({
                "fiscal_year": f"{period_end[:4]}/{period_end[5:7]}",
                "field_status": {field: "available" if field in fresh_valuation else "stale" for field in valuation_values},
                **valuation_values,
            })
    retrieval_status["fields"] = field_retrieval_status
    retrieval_status["legacy_source"] = "migrated" if any((legacy_income, legacy_balance, legacy_cash, legacy_valuation)) else "not_found"
    return statements, valuation_rows, retrieval_status


def legacy_document(client: Any, bucket: str, symbol: str) -> dict[str, Any]:
    safe = safe_symbol(symbol)
    raw = collector.read_object(client, bucket, f"{OLD_ROOT}/securities/{safe}.json")
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
        existing_raw = collector.read_object(client, bucket, archive_key)
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
        saved_raw = collector.read_object(client, bucket, archive_key)
        if not saved_raw:
            raise RuntimeError(f"Legacy field archive read-back failed: {archive_key}")
        saved = json.loads(gzip.decompress(saved_raw))
        if saved.get("source_key") != legacy_key or saved.get("fields") != archive_document["fields"]:
            raise RuntimeError(f"Legacy field archive verification failed: {archive_key}")
    client.delete_object(Bucket=bucket, Key=legacy_key)


def verify_v2_transfer(client: Any, bucket: str, symbol: str, old: dict[str, Any],
                       statements: list[dict[str, Any]], monthly: list[dict[str, Any]]) -> None:
    safe = safe_symbol(symbol)
    if not read_json(client, bucket, object_key("securities", safe, "profile.json")):
        raise RuntimeError(f"v2 profile missing before legacy cleanup: {symbol}")
    if not read_json(client, bucket, object_key("securities", safe, "current.json")):
        raise RuntimeError(f"v2 current data missing before legacy cleanup: {symbol}")
    for family, legacy_key in (("dividends", "dividends"), ("splits", "stock_splits")):
        events = (old.get("actions") or {}).get(legacy_key, [])
        if not events:
            continue
        required_dates = {str(row.get("date"))[:10] for row in events if row.get("date")}
        stored_dates = {str(row.get("date"))[:10] for row in load_all_year_records(client, bucket, symbol, family)}
        archive_prefix = object_key("archive", "events", safe, family) + "/"
        for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=archive_prefix):
            for item in page.get("Contents", []):
                document = read_json(client, bucket, item["Key"])
                stored_dates.update(str(row.get("date"))[:10] for row in document.get("records", []) if row.get("date"))
        missing = required_dates - stored_dates
        if missing:
            raise RuntimeError(f"v2 {family} events missing before legacy cleanup for {symbol}: {len(missing)}")
    for point in monthly:
        point_date = date_value(point.get("date"))
        if point_date is None or point_date < years_before(datetime.now(JST).date(), 10):
            continue
        year = int(str(point.get("month", "0000"))[:4])
        stored_months = {str(row.get("month")) for row in read_monthly_points(client, bucket, symbol, year)}
        if point.get("month") not in stored_months:
            raise RuntimeError(f"v2 monthly price missing before legacy cleanup: {symbol} {point.get('month')}")
    for row in statements:
        period_end = str(row.get("period_end") or "")
        year = period_end[:4]
        document = read_json(client, bucket, object_key("securities", safe, "financials", f"{year}.json"))
        if not document or str(document.get("annual", {}).get("period_end") or "") != period_end:
            raise RuntimeError(f"v2 financial record missing before legacy cleanup: {symbol} {period_end}")


def collect_symbol(client: Any, bucket: str, code: str, symbol: str, as_of: date,
                   *, attempt_count: int = 1) -> dict[str, Any]:
    safe = safe_symbol(symbol)
    prior_sync = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
    prior_profile = read_json(client, bucket, object_key("securities", safe, "profile.json")) or {}
    prior_current = read_json(client, bucket, object_key("securities", safe, "current.json")) or {}
    old = legacy_document(client, bucket, symbol)
    ticker = yf.Ticker(symbol)
    info = ticker.get_info()
    if not isinstance(info, dict) or not info:
        raise RuntimeError("yfinance returned no company profile")
    daily = collector.history_rows(ticker, "1d", "5d")
    daily = [row for row in daily if finite_number(row.get("close")) and float(row["close"]) > 0]
    if not daily:
        raise RuntimeError("yfinance returned no completed daily close")
    latest = max(daily, key=lambda row: str(row.get("date") or ""))
    price_date = date.fromisoformat(str(latest["date"])[:10])
    if price_date > as_of:
        raise RuntimeError("yfinance daily close is newer than the Japan session date")

    old_actions = old.get("actions") if isinstance(old.get("actions"), dict) else {}
    prior_dividends = load_all_year_records(client, bucket, symbol, "dividends", include_archive=True)
    prior_splits = load_all_year_records(client, bucket, symbol, "splits", include_archive=True)
    prior_dividends.extend(row for row in old_actions.get("dividends", []) if isinstance(row, dict))
    prior_splits.extend(row for row in old_actions.get("stock_splits", []) if isinstance(row, dict))
    action_status: dict[str, str] = {}
    action_retrieval: dict[str, Any] = {
        "source": "yfinance_history",
        "yfinance_version": yf.__version__,
        "period": "max",
        "interval": "1d",
        "repair": True,
        "dividend_amount_basis": "yfinance_split_adjusted_assumption",
        "split_ratio_reapplied_to_dividends": False,
    }
    try:
        fresh_dividends, fresh_splits, action_coverage_start, action_coverage_end = fetch_repaired_action_history(ticker, as_of)
        dividends = replace_dated_history(
            prior_dividends, fresh_dividends, "date", action_coverage_start, action_coverage_end,
        )
        splits = replace_dated_history(
            prior_splits, fresh_splits, "date", action_coverage_start, action_coverage_end,
        )
        action_status["dividends"] = "available" if fresh_dividends else "empty"
        action_status["splits"] = "available" if fresh_splits else "empty"
        action_retrieval.update({
            "status": "success",
            "coverage_start": action_coverage_start.isoformat(),
            "coverage_end": action_coverage_end.isoformat(),
            "dividend_event_count": len(fresh_dividends),
            "split_event_count": len(fresh_splits),
        })
    except Exception as exc:
        dividends = prior_dividends
        splits = prior_splits
        action_status["dividends"] = "failed"
        action_status["splits"] = "failed"
        action_retrieval.update({"status": "failed", "failure_reason": f"{type(exc).__name__}: {exc}"[:500]})
        print(f"Dividend history unavailable for {symbol}: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
    action_years: dict[str, set[str]] = {
        "dividends": {str(row.get("date") or "")[:4] for row in prior_dividends + dividends if row.get("date")},
        "splits": {str(row.get("date") or "")[:4] for row in prior_splits + splits if row.get("date")},
    }

    previous_monthly = prior_sync.get("monthly_history_initialized") is True
    try:
        monthly = monthly_history_rows(ticker, initial=not previous_monthly)
        monthly_status = "available" if monthly else "empty"
    except Exception as exc:
        monthly = []
        monthly_status = "failed"
        print(f"Monthly history unavailable for {symbol}: {type(exc).__name__}: {exc}"[:500], file=sys.stderr)
    old_prices = old.get("price_history") or {}
    migrated_months = [
        {"month": str(row.get("date", ""))[:7], "date": row.get("date"), "close": row.get("close")}
        for row in old_prices.get("1mo", []) if row.get("date") and row.get("close")
    ]
    merged_months = {str(point["month"]): point for point in migrated_months}
    merged_months.update({str(point["month"]): point for point in monthly})
    monthly = [merged_months[month] for month in sorted(merged_months)]
    for point in monthly:
        # Yahoo's Close is already split-adjusted; Adj Close would also adjust for dividends.
        point["close"] = float(point["close"])
        point["currency"] = str(info.get("currency") or "JPY")

    statements, valuation, financial_status = collect_financials(ticker, old, client, bucket, symbol)
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
    profile["field_status"] = {
        field: ("stale" if field in stale_profile_fields else "available" if profile.get(field) is not None else "empty")
        for field in (*PROFILE_FIELDS, "short_name", "long_name", "display_name", "english_name")
    }

    close = finite_number(latest.get("close"))
    trailing_start = years_before(as_of, 1).isoformat()
    trailing_dividends = round(sum(
        finite_number(row.get("amount")) or 0.0 for row in dividends
        if trailing_start < str(row.get("date") or "")[:10] <= as_of.isoformat()
    ), 8)
    forecast_dps = finite_number(info.get("dividendRate"))
    has_forecast_dps = forecast_dps is not None and forecast_dps > 0
    current_fy_dps = fiscal_year_events(dividends, info, as_of)
    reported_yield = finite_number(info.get("dividendYield"))
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
    dividend_yield = round(yield_dps / close * 100, 4) if yield_dps is not None and close and close > 0 else None
    dividend_history_failed = action_status["dividends"] == "failed"
    dividend_status = "no_dividend" if no_dividend else "has_dividend" if yield_dps is not None and yield_dps > 0 else "unknown"
    current_fiscal_status = (
        "failed" if dividend_history_failed and current_fy_dps is None
        else "stale" if dividend_history_failed
        else "available" if current_fy_dps is not None else "empty"
    )
    dividend_yield_status = (
        "failed" if dividend_history_failed and yield_basis is None
        else "stale" if dividend_history_failed and yield_basis == "current_fiscal_year"
        else "empty" if dividend_yield is None else "available"
    )
    dividend_status_state = (
        "failed" if dividend_history_failed and dividend_status == "unknown"
        else "stale" if dividend_history_failed and yield_basis == "current_fiscal_year"
        else "empty" if dividend_status == "unknown" else "available"
    )
    current = {
        "schema_version": 1,
        "code": code,
        "symbol": symbol,
        "price": {"close": close, "date": price_date.isoformat(), "currency": str(info.get("currency") or "JPY")},
        "dividend": {
            "trailing_12_month_per_share": trailing_dividends,
            "trailing_12_month_period": {
                "start_exclusive": trailing_start,
                "end_inclusive": as_of.isoformat(),
            },
            "next_fiscal_year_forecast_per_share": forecast_dps,
            "current_fiscal_year_per_share": current_fy_dps,
            "yield_rate": dividend_yield,
            "yield_basis": yield_basis,
            "event_amount_basis": "yfinance_split_adjusted_assumption",
            "split_adjustment_policy": "assume_yfinance_returned_amounts_are_split_adjusted",
            "dividend_status": dividend_status,
        },
        "statistics": {key: collector.json_safe(info[key]) for key in CURRENT_FIELDS if info.get(key) is not None},
        "field_status": {
            "dividend_history": action_status["dividends"],
            "split_history": action_status["splits"],
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
        f"statistics_{field}": "available" if current["statistics"].get(field) is not None else "empty"
        for field in CURRENT_FIELDS
    })
    previous_statistics = prior_current.get("statistics", {})
    for field, value in (previous_statistics.items() if isinstance(previous_statistics, dict) else []):
        if current["statistics"].get(field) is None and value is not None:
            current["statistics"][field] = value
            current["field_status"][f"statistics_{field}"] = "stale"

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
        years = action_years[family] | set(by_year) | set(archive_by_year)
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
                client.delete_object(Bucket=bucket, Key=archive_key)
            if year in by_year:
                put_json_if_changed(client, bucket, hot_key, {
                    "schema_version": 1, "symbol": symbol, "year": int(year),
                    "records": by_year[year], "action_history": action_retrieval,
                    "updated_at": iso_now(), "source": "Yahoo Finance via yfinance",
                })
            else:
                client.delete_object(Bucket=bucket, Key=hot_key)

    save_monthly_prices(client, bucket, symbol, monthly, as_of=as_of)
    for row in statements:
        year = str(row.get("period_end", ""))[:4]
        if year:
            key = object_key("securities", safe, "financials", f"{year}.json")
            put_json_if_changed(client, bucket, key, {
                "schema_version": 1, "symbol": symbol, "year": year,
                "annual": row,
                "valuation": next((item for item in valuation if item.get("fiscal_year") == row.get("fiscal_year")), None),
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
        "monthly_history_initialized": previous_monthly or monthly_status != "failed",
        "failure_reason": f"Field fetch failed: {', '.join(field_failures)}"[:500] if field_failures else None,
        "attempt_count_today": attempt_count,
        "retry_count_today": max(0, attempt_count - 1),
        "retry_session_date": as_of.isoformat(),
        "field_status": current["field_status"],
        "source": "Yahoo Finance via yfinance",
    }
    put_json_if_changed(client, bucket, object_key("securities", safe, "sync.json"), sync)
    if old:
        verify_v2_transfer(client, bucket, symbol, old, statements, monthly)
        archived_legacy_document(client, bucket, symbol, old)
    return {"code": code, "symbol": symbol, "name": profile["display_name"], "price": close,
            "quote_date": price_date.isoformat(), "annual_dividend_per_share": trailing_dividends,
            "yield_rate": current["dividend"]["yield_rate"], "currency": current["price"]["currency"],
            "field_status": current["field_status"], "field_failures": field_failures,
            "status": "partial" if field_failures else "ok"}


def save_failure(client: Any, bucket: str, code: str, symbol: str, session_date: date, error: Exception,
                 *, attempt_count: int) -> None:
    safe = safe_symbol(symbol)
    prior = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
    prior.update({
        "schema_version": 1, "code": code, "symbol": symbol,
        "status": "failed", "last_attempt_at": iso_now(),
        "last_attempt_session_date": session_date.isoformat(),
        "failure_reason": f"{type(error).__name__}: {error}"[:500],
        "attempt_count_today": attempt_count,
        "retry_count_today": max(0, attempt_count - 1),
        "retry_session_date": session_date.isoformat(),
    })
    put_json_if_changed(client, bucket, object_key("securities", safe, "sync.json"), prior)


def record_attempt_start(client: Any, bucket: str, code: str, symbol: str,
                         session_date: date, attempt_count: int) -> None:
    safe = safe_symbol(symbol)
    prior = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
    prior.update({
        "schema_version": 1, "code": code, "symbol": symbol,
        "status": "running", "last_attempt_at": iso_now(),
        "last_attempt_session_date": session_date.isoformat(),
        "attempt_count_today": attempt_count,
        "retry_count_today": max(0, attempt_count - 1),
        "retry_session_date": session_date.isoformat(),
    })
    put_json_if_changed(client, bucket, object_key("securities", safe, "sync.json"), prior)


def publish_catalog(client: Any, bucket: str, universe: list[dict[str, str]], session_date: date,
                    previous_catalog: dict[str, Any] | None) -> dict[str, Any]:
    old_items = {str(row.get("code")): row for row in (previous_catalog or {}).get("items", []) if isinstance(row, dict)}
    items: list[dict[str, Any]] = []
    for security in universe:
        code = security["security_code_display"]
        symbol = market_symbol(code)
        safe = safe_symbol(symbol)
        profile = read_json(client, bucket, object_key("securities", safe, "profile.json"))
        current = read_json(client, bucket, object_key("securities", safe, "current.json"))
        if not profile or not current or not current.get("price", {}).get("close"):
            continue
        sync = read_json(client, bucket, object_key("securities", safe, "sync.json")) or {}
        dividend = current.get("dividend") or {}
        items.append({
            "code": code,
            "name": profile.get("display_name") or old_items.get(code, {}).get("name") or code,
            "category": "jp",
            "security_type": security.get("security_type", "listed_security"),
            "jquants_product_category": security.get("jquants_product_category") or None,
            "price": current["price"].get("close"),
            "quote_date": current["price"].get("date"),
            "currency": current["price"].get("currency"),
            "annual_dividend_per_share": dividend.get("trailing_12_month_per_share"),
            "trailing_12_month_distribution_per_share": dividend.get("trailing_12_month_per_share"),
            "dividend_period": "trailing_12_months",
            "dividend_amount_basis": dividend.get("event_amount_basis"),
            "yield_rate": dividend.get("yield_rate"),
            "yield_basis": dividend.get("yield_basis"),
            "dividend_status": dividend.get("dividend_status", "unknown"),
            "status": sync.get("status") if sync.get("status") in {"ok", "partial", "failed"} else "ok",
            "failure_reason": sync.get("failure_reason"),
        })
    items.sort(key=lambda item: item["code"])
    if len(items) != len({item["code"] for item in items}):
        raise RuntimeError("v2 search catalog contains duplicate security codes")
    return {
        "schema_version": 1,
        "generation": session_date.isoformat(),
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


def run(*, publish: bool, max_workers: int, dry_run: bool) -> int:
    if not publish and not dry_run:
        raise RuntimeError("Specify --publish or --dry-run")
    configure = getattr(collector, "configure_yfinance", None)
    if configure:
        configure()
    api_key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("JQUANTS_API_KEY is required")
    fetched = fetch_securities(api_key)
    session_date = datetime.now(JST).date()
    client = collector.get_client() if publish else None
    bucket = collector.required_env("R2_BUCKET") if publish else ""
    if dry_run:
        print(f"J-Quants universe: {len(fetched)} listed issues; session date={session_date}")
        print("Dry-run validates the universe only; no yfinance calls or R2 writes were made.")
        return 0

    old_universe = read_json(client, bucket, object_key("universe", "current.json")) or {}
    old_status = {str(row.get("code")): row for row in old_universe.get("securities", []) if isinstance(row, dict)}
    previous_catalog = read_json(client, bucket, object_key("catalog", "current.json"))
    securities = [row["security_code_display"] for row in fetched]
    results: dict[str, dict[str, Any]] = {}
    failures: dict[str, str] = {}

    def process(code: str) -> dict[str, Any]:
        symbol = market_symbol(code)
        error: Exception | None = None
        previous_sync = read_json(client, bucket, object_key("securities", safe_symbol(symbol), "sync.json")) or {}
        attempts_used = 0
        if previous_sync.get("retry_session_date") == session_date.isoformat():
            try:
                attempts_used = max(0, int(previous_sync.get("attempt_count_today", 0)))
            except (TypeError, ValueError):
                attempts_used = 0
        if (previous_sync.get("status") == "ok"
                and previous_sync.get("last_success_session_date") == session_date.isoformat()):
            return {"code": code, "symbol": symbol, "status": "ok", "field_failures": []}
        if attempts_used >= len(RETRY_DELAYS) + 1:
            if previous_sync.get("status") == "partial":
                reason = str(previous_sync.get("failure_reason") or "")
                failures_today = reason.removeprefix("Field fetch failed: ").split(", ") if reason else []
                return {"code": code, "symbol": symbol, "status": "partial", "field_failures": failures_today}
            error = RuntimeError("Daily per-security attempt limit reached")
            save_failure(client, bucket, code, symbol, session_date, error,
                         attempt_count=len(RETRY_DELAYS) + 1)
            raise error
        if attempts_used:
            delay = RETRY_DELAYS[attempts_used - 1]
            print(f"{symbol}: resuming today's retries after {delay}s", file=sys.stderr, flush=True)
            time.sleep(delay)
        attempt_count = attempts_used
        for retry_index in range(attempts_used, len(RETRY_DELAYS) + 1):
            attempt_count = retry_index + 1
            record_attempt_start(client, bucket, code, symbol, session_date, attempt_count)
            try:
                result = collect_symbol(client, bucket, code, symbol, session_date, attempt_count=attempt_count)
                if result.get("field_failures") and retry_index < len(RETRY_DELAYS):
                    delay = RETRY_DELAYS[retry_index]
                    print(f"{symbol}: field fetch failed; retrying in {delay}s", file=sys.stderr, flush=True)
                    time.sleep(delay)
                    continue
                return result
            except Exception as exc:
                error = exc
                if retry_index < len(RETRY_DELAYS):
                    delay = RETRY_DELAYS[retry_index]
                    print(f"{symbol}: attempt {retry_index + 1} failed; retrying in {delay}s", file=sys.stderr, flush=True)
                    time.sleep(delay)
        assert error is not None
        save_failure(client, bucket, code, symbol, session_date, error, attempt_count=attempt_count)
        raise error

    with ThreadPoolExecutor(max_workers=max(1, max_workers)) as pool:
        futures = {pool.submit(process, code): code for code in securities}
        for future in as_completed(futures):
            code = futures[future]
            try:
                results[code] = future.result()
            except Exception as exc:
                failures[code] = f"{type(exc).__name__}: {exc}"
                print(f"FAILED {code}: {failures[code]}", file=sys.stderr, flush=True)

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
    catalog = publish_catalog(client, bucket, fetched, session_date, previous_catalog)
    put_json_if_changed(client, bucket, object_key("universe", "current.json"), {
        "schema_version": 1, "updated_at": iso_now(), "source": "J-Quants API v2",
        "session_date": session_date.isoformat(), "security_count": len(fetched), "securities": universe_rows,
    })
    put_json_if_changed(client, bucket, object_key("catalog", "generations", f"{session_date.isoformat()}.json"), catalog)
    put_json_if_changed(client, bucket, object_key("catalog", "current.json"), catalog)
    publication = {
        "schema_version": 1,
        "yfinance_version": yf.__version__,
        "generation": session_date.isoformat(),
        "market_data_date": session_date.isoformat(),
        "last_completed_at": iso_now(),
        "security_count": len(fetched),
        "success_count": len(results),
        "partial_count": sum(1 for row in results.values() if row.get("status") == "partial"),
        "failure_count": len(failures),
        "catalog_count": len(catalog["items"]),
    }
    put_json_if_changed(client, bucket, object_key("publications", "current.json"), publication)
    put_json_if_changed(client, bucket, object_key("manifest.json"), {
        "schema_version": 1, "active_generation": session_date.isoformat(),
        "generated_at": iso_now(), "source": "Yahoo Finance via yfinance",
        "yfinance_version": yf.__version__,
        "universe_source": "J-Quants API v2", "security_count": len(fetched),
        "catalog_count": len(catalog["items"]),
    })
    print(f"Published v2 generation {session_date}: success={len(results)}, failed={len(failures)}, catalog={len(catalog['items'])}")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publish", action="store_true", help="Write the v2 generation to R2")
    parser.add_argument("--dry-run", action="store_true", help="Fetch the J-Quants universe only; do not access R2 or yfinance")
    parser.add_argument("--workers", type=int, default=int(os.environ.get("YFINANCE_V2_WORKERS", "8")))
    args = parser.parse_args()
    return run(publish=args.publish, max_workers=args.workers, dry_run=args.dry_run)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
