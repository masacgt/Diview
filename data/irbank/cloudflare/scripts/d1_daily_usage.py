#!/usr/bin/env python3
"""Read account-wide D1 usage from Cloudflare GraphQL Analytics (no D1 SQL)."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
from datetime import date, datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


GRAPHQL_URL = "https://api.cloudflare.com/client/v4/graphql"
MAX_GROUPS = 10_000
MAX_MINUTE_BUCKETS = 24 * 60
DEFAULT_FREE_DAILY_LIMIT = 5_000_000
QUERY = """
query D1DailyUsage($accountTag: string!, $start: Date!, $end: Date!) {
  viewer {
    accounts(filter: { accountTag: $accountTag }) {
      d1AnalyticsAdaptiveGroups(
        limit: 10000
        filter: { date_geq: $start, date_leq: $end }
        orderBy: [date_DESC]
      ) {
        sum { rowsRead rowsWritten }
        dimensions { date databaseId }
      }
    }
  }
}
"""
MINUTE_QUERY = """
query D1MinuteUsage($accountTag: string!, $start: Time!, $end: Time!) {
  viewer {
    accounts(filter: { accountTag: $accountTag }) {
      d1AnalyticsAdaptiveGroups(
        limit: 10000
        filter: { datetimeMinute_geq: $start, datetimeMinute_leq: $end }
        orderBy: [datetimeMinute_ASC]
      ) {
        sum { readQueries rowsRead rowsWritten }
        confidence(level: 0.95) {
          sum {
            readQueries { estimate lower upper sampleSize isValid }
            rowsRead { estimate lower upper sampleSize isValid }
          }
        }
        dimensions { datetimeMinute databaseId }
      }
    }
  }
}
"""


class UsageQueryError(RuntimeError):
    """A sanitized analytics API or response error."""


def validate_account_id(value: str) -> str:
    account_id = value.strip()
    if not re.fullmatch(r"[a-fA-F0-9]{32}", account_id):
        raise UsageQueryError("account ID must contain exactly 32 hexadecimal characters")
    return account_id


def validate_utc_date(value: str) -> str:
    try:
        parsed = date.fromisoformat(value)
    except ValueError as error:
        raise UsageQueryError("UTC date must use YYYY-MM-DD format") from error
    if parsed.isoformat() != value:
        raise UsageQueryError("UTC date must use YYYY-MM-DD format")
    return value


def build_usage_report(
    account_id: str,
    utc_date: str,
    groups: object,
    daily_limit_rows: int = DEFAULT_FREE_DAILY_LIMIT,
) -> dict:
    validate_account_id(account_id)
    validate_utc_date(utc_date)
    if isinstance(daily_limit_rows, bool) or not isinstance(daily_limit_rows, int) or daily_limit_rows < 0:
        raise UsageQueryError("daily limit must be a non-negative integer")
    if not isinstance(groups, list):
        raise UsageQueryError("GraphQL response did not contain D1 analytics groups")
    if len(groups) >= MAX_GROUPS:
        raise UsageQueryError("analytics group limit reached; refusing to report a possibly truncated total")

    per_database: dict[str, dict[str, int]] = {}
    for group in groups:
        if not isinstance(group, dict):
            raise UsageQueryError("GraphQL response contains an invalid analytics group")
        dimensions = group.get("dimensions")
        totals = group.get("sum")
        if not isinstance(dimensions, dict) or not isinstance(totals, dict):
            raise UsageQueryError("GraphQL response is missing D1 analytics dimensions or totals")
        if dimensions.get("date") != utc_date:
            raise UsageQueryError("GraphQL returned a group outside the requested UTC date")
        database_id = dimensions.get("databaseId")
        if not isinstance(database_id, str) or not re.fullmatch(r"[a-fA-F0-9-]{36}", database_id):
            raise UsageQueryError("GraphQL returned an invalid database ID")

        bucket = per_database.setdefault(database_id, {"rows_read": 0, "rows_written": 0})
        for source_field, report_field in (("rowsRead", "rows_read"), ("rowsWritten", "rows_written")):
            amount = totals.get(source_field)
            if isinstance(amount, bool) or not isinstance(amount, int) or amount < 0:
                raise UsageQueryError(f"GraphQL returned an invalid {source_field} total")
            bucket[report_field] += amount

    rows_read = sum(item["rows_read"] for item in per_database.values())
    return {
        "account_id": account_id,
        "utc_date": utc_date,
        "queried_at_utc": datetime.now(timezone.utc).isoformat(),
        "analytics_groups": len(groups),
        "databases_with_usage": len(per_database),
        "rows_read": rows_read,
        "rows_written": sum(item["rows_written"] for item in per_database.values()),
        "daily_limit_rows": daily_limit_rows,
        "rows_remaining": max(0, daily_limit_rows - rows_read),
        "limit_exceeded": rows_read > daily_limit_rows,
        "per_database": [
            {"database_id": database_id, **per_database[database_id]}
            for database_id in sorted(per_database)
        ],
    }


def fetch_daily_usage(
    account_id: str,
    token: str,
    utc_date: str,
    daily_limit_rows: int = DEFAULT_FREE_DAILY_LIMIT,
    opener=None,
    timeout_seconds: float = 30.0,
) -> dict:
    account_id = validate_account_id(account_id)
    utc_date = validate_utc_date(utc_date)
    if not token or not token.strip():
        raise UsageQueryError("CLOUDFLARE_API_TOKEN is required")
    if isinstance(timeout_seconds, bool) or not isinstance(timeout_seconds, (int, float)):
        raise UsageQueryError("Analytics request timeout must be a finite positive number")
    try:
        timeout = float(timeout_seconds)
    except (OverflowError, ValueError) as error:
        raise UsageQueryError("Analytics request timeout must be a finite positive number") from error
    if not math.isfinite(timeout) or timeout <= 0:
        raise UsageQueryError("Analytics request timeout must be a finite positive number")
    open_request = opener or urlopen
    payload = json.dumps({
        "query": QUERY,
        "variables": {"accountTag": account_id, "start": utc_date, "end": utc_date},
    }).encode("utf-8")
    request = Request(
        GRAPHQL_URL,
        data=payload,
        headers={
            "Authorization": f"Bearer {token.strip()}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with open_request(request, timeout=timeout) as response:
            raw = response.read(5_000_001)
    except HTTPError as error:
        raise UsageQueryError(f"Cloudflare GraphQL request failed with HTTP {error.code}") from error
    except URLError as error:
        raise UsageQueryError("Cloudflare GraphQL request failed before receiving a response") from error
    except TimeoutError as error:
        raise UsageQueryError("Cloudflare GraphQL request timed out") from error
    if len(raw) > 5_000_000:
        raise UsageQueryError("Cloudflare GraphQL response exceeded the 5 MB safety limit")
    try:
        body = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise UsageQueryError("Cloudflare GraphQL returned an invalid JSON response") from error
    if not isinstance(body, dict):
        raise UsageQueryError("Cloudflare GraphQL returned an invalid response")
    if body.get("errors"):
        raise UsageQueryError("Cloudflare GraphQL reported an analytics query error")
    try:
        accounts = body["data"]["viewer"]["accounts"]
        if not isinstance(accounts, list) or len(accounts) != 1:
            raise UsageQueryError("Cloudflare GraphQL did not return exactly one account")
        groups = accounts[0]["d1AnalyticsAdaptiveGroups"]
    except (KeyError, TypeError) as error:
        raise UsageQueryError("Cloudflare GraphQL response is missing D1 analytics data") from error
    return build_usage_report(account_id, utc_date, groups, daily_limit_rows)


def parse_utc_minute(value: object) -> datetime:
    if not isinstance(value, str):
        raise UsageQueryError("UTC minute must be an ISO-8601 timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise UsageQueryError("UTC minute must be an ISO-8601 timestamp") from error
    if parsed.tzinfo is None or parsed.utcoffset() != timedelta(0):
        raise UsageQueryError("UTC minute must include a UTC timezone")
    return parsed.astimezone(timezone.utc).replace(second=0, microsecond=0)


def format_utc_minute(value: datetime) -> str:
    return value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:00Z")


def build_minute_usage_report(
    account_id: str,
    start_utc: str,
    end_utc: str,
    groups: object,
    daily_limit_rows: int = DEFAULT_FREE_DAILY_LIMIT,
) -> dict:
    validate_account_id(account_id)
    start = parse_utc_minute(start_utc)
    end = parse_utc_minute(end_utc)
    if end < start:
        raise UsageQueryError("minute usage end must not precede its start")
    if start.date() != end.date():
        raise UsageQueryError("minute usage window must stay within one UTC date")
    bucket_count = int((end - start).total_seconds() // 60) + 1
    if bucket_count > MAX_MINUTE_BUCKETS:
        raise UsageQueryError("minute usage window exceeds the one-day safety limit")
    if isinstance(daily_limit_rows, bool) or not isinstance(daily_limit_rows, int) or daily_limit_rows < 0:
        raise UsageQueryError("daily limit must be a non-negative integer")
    if not isinstance(groups, list):
        raise UsageQueryError("GraphQL response did not contain D1 minute analytics groups")
    if len(groups) >= MAX_GROUPS:
        raise UsageQueryError("analytics group limit reached; refusing to report a possibly truncated total")

    per_database: dict[str, dict[str, int]] = {}
    per_minute = []
    seen_groups: set[tuple[str, str]] = set()
    for group in groups:
        if not isinstance(group, dict):
            raise UsageQueryError("GraphQL response contains an invalid analytics group")
        dimensions = group.get("dimensions")
        totals = group.get("sum")
        if not isinstance(dimensions, dict) or not isinstance(totals, dict):
            raise UsageQueryError("GraphQL response is missing D1 minute dimensions or totals")
        confidence = group.get("confidence")
        confidence_sum = confidence.get("sum") if isinstance(confidence, dict) else None
        if not isinstance(confidence_sum, dict):
            raise UsageQueryError("GraphQL response is missing D1 minute confidence bounds")
        minute = parse_utc_minute(dimensions.get("datetimeMinute"))
        if minute < start or minute > end:
            raise UsageQueryError("GraphQL returned a group outside the requested minute window")
        minute_text = format_utc_minute(minute)
        database_id = dimensions.get("databaseId")
        if not isinstance(database_id, str) or not re.fullmatch(r"[a-fA-F0-9-]{36}", database_id):
            raise UsageQueryError("GraphQL returned an invalid database ID")
        group_key = (minute_text, database_id)
        if group_key in seen_groups:
            raise UsageQueryError("GraphQL returned a duplicate D1 minute analytics group")
        seen_groups.add(group_key)

        values = {}
        confidence_values = {}
        for source_field, report_field in (
            ("readQueries", "read_queries"),
            ("rowsRead", "rows_read"),
            ("rowsWritten", "rows_written"),
        ):
            amount = totals.get(source_field)
            if isinstance(amount, bool) or not isinstance(amount, int) or amount < 0:
                raise UsageQueryError(f"GraphQL returned an invalid {source_field} total")
            values[report_field] = amount
            if source_field in {"readQueries", "rowsRead"}:
                metric_confidence = confidence_sum.get(source_field)
                if not isinstance(metric_confidence, dict):
                    raise UsageQueryError(f"GraphQL response is missing {source_field} confidence bounds")
                bounds = {}
                for key in ("estimate", "lower", "upper"):
                    bound = metric_confidence.get(key)
                    if (
                        isinstance(bound, bool)
                        or not isinstance(bound, (int, float))
                        or not math.isfinite(float(bound))
                    ):
                        raise UsageQueryError(f"GraphQL returned an invalid {source_field} confidence bound")
                    bounds[key] = float(bound)
                sample_size = metric_confidence.get("sampleSize")
                is_valid = metric_confidence.get("isValid")
                if (
                    isinstance(sample_size, bool)
                    or not isinstance(sample_size, int)
                    or sample_size < 0
                    or not isinstance(is_valid, bool)
                ):
                    raise UsageQueryError(f"GraphQL returned invalid {source_field} confidence metadata")
                if not (
                    bounds["estimate"] == bounds["lower"] == bounds["upper"] == amount
                ):
                    raise UsageQueryError(f"GraphQL returned uncertain {source_field} minute analytics")
                confidence_values[report_field] = {
                    **bounds,
                    "sample_size": sample_size,
                    "is_valid": is_valid,
                }

        bucket = per_database.setdefault(database_id, {"read_queries": 0, "rows_read": 0, "rows_written": 0})
        for field, amount in values.items():
            bucket[field] += amount
        per_minute.append({
            "datetime_minute": minute_text,
            "database_id": database_id,
            "confidence": confidence_values,
            **values,
        })

    rows_read = sum(item["rows_read"] for item in per_database.values())
    read_queries = sum(item["read_queries"] for item in per_database.values())
    rows_written = sum(item["rows_written"] for item in per_database.values())
    return {
        "account_id": account_id,
        "start_utc": format_utc_minute(start),
        "end_utc": format_utc_minute(end),
        "queried_at_utc": datetime.now(timezone.utc).isoformat(),
        "analytics_groups": len(groups),
        "confidence_bounds_verified": bool(groups),
        "rows_read": rows_read,
        "read_queries": read_queries,
        "rows_written": rows_written,
        "daily_limit_rows": daily_limit_rows,
        "rows_remaining": max(0, daily_limit_rows - rows_read),
        "limit_exceeded": rows_read > daily_limit_rows,
        "per_database": [
            {"database_id": database_id, **per_database[database_id]}
            for database_id in sorted(per_database)
        ],
        "per_minute": sorted(per_minute, key=lambda item: (item["datetime_minute"], item["database_id"])),
    }


def fetch_minute_usage(
    account_id: str,
    token: str,
    start_utc: str,
    end_utc: str,
    daily_limit_rows: int = DEFAULT_FREE_DAILY_LIMIT,
    opener=None,
    timeout_seconds: float = 30.0,
) -> dict:
    account_id = validate_account_id(account_id)
    start = parse_utc_minute(start_utc)
    end = parse_utc_minute(end_utc)
    if end < start:
        raise UsageQueryError("minute usage end must not precede its start")
    if start.date() != end.date():
        raise UsageQueryError("minute usage window must stay within one UTC date")
    if int((end - start).total_seconds() // 60) + 1 > MAX_MINUTE_BUCKETS:
        raise UsageQueryError("minute usage window exceeds the one-day safety limit")
    if not token or not token.strip():
        raise UsageQueryError("CLOUDFLARE_API_TOKEN is required")
    if isinstance(timeout_seconds, bool) or not isinstance(timeout_seconds, (int, float)):
        raise UsageQueryError("Analytics request timeout must be a finite positive number")
    try:
        timeout = float(timeout_seconds)
    except (OverflowError, ValueError) as error:
        raise UsageQueryError("Analytics request timeout must be a finite positive number") from error
    if not math.isfinite(timeout) or timeout <= 0:
        raise UsageQueryError("Analytics request timeout must be a finite positive number")

    open_request = opener or urlopen
    payload = json.dumps({
        "query": MINUTE_QUERY,
        "variables": {
            "accountTag": account_id,
            "start": format_utc_minute(start),
            "end": format_utc_minute(end),
        },
    }).encode("utf-8")
    request = Request(
        GRAPHQL_URL,
        data=payload,
        headers={
            "Authorization": f"Bearer {token.strip()}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with open_request(request, timeout=timeout) as response:
            raw = response.read(5_000_001)
    except HTTPError as error:
        raise UsageQueryError(f"Cloudflare GraphQL request failed with HTTP {error.code}") from error
    except URLError as error:
        raise UsageQueryError("Cloudflare GraphQL request failed before receiving a response") from error
    except TimeoutError as error:
        raise UsageQueryError("Cloudflare GraphQL request timed out") from error
    if len(raw) > 5_000_000:
        raise UsageQueryError("Cloudflare GraphQL response exceeded the 5 MB safety limit")
    try:
        body = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise UsageQueryError("Cloudflare GraphQL returned an invalid JSON response") from error
    if not isinstance(body, dict):
        raise UsageQueryError("Cloudflare GraphQL returned an invalid response")
    if body.get("errors"):
        raise UsageQueryError("Cloudflare GraphQL reported an analytics query error")
    try:
        accounts = body["data"]["viewer"]["accounts"]
        if not isinstance(accounts, list) or len(accounts) != 1:
            raise UsageQueryError("Cloudflare GraphQL did not return exactly one account")
        groups = accounts[0]["d1AnalyticsAdaptiveGroups"]
    except (KeyError, TypeError) as error:
        raise UsageQueryError("Cloudflare GraphQL response is missing D1 minute analytics data") from error
    return build_minute_usage_report(
        account_id,
        format_utc_minute(start),
        format_utc_minute(end),
        groups,
        daily_limit_rows,
    )


def build_budget_usage_report(daily_usage: dict, minute_usage: dict | None = None) -> dict:
    """Keep the daily snapshot for reconciliation and add a conservative budget total."""
    if not isinstance(daily_usage, dict):
        raise UsageQueryError("daily analytics report is invalid")
    daily_rows_read = daily_usage.get("rows_read")
    daily_limit_rows = daily_usage.get("daily_limit_rows")
    daily_per_database = daily_usage.get("per_database")
    if (
        isinstance(daily_rows_read, bool)
        or not isinstance(daily_rows_read, int)
        or daily_rows_read < 0
        or isinstance(daily_limit_rows, bool)
        or not isinstance(daily_limit_rows, int)
        or daily_limit_rows < 0
        or not isinstance(daily_per_database, list)
    ):
        raise UsageQueryError("daily analytics report does not contain valid usage counts")

    minute_rows_by_database: dict[str, int] = {}
    minute_rows_read = None
    minute_queried_at = None
    minute_confidence_verified = None
    if minute_usage is not None:
        minute_rows_read = minute_usage.get("rows_read")
        minute_per_database = minute_usage.get("per_database")
        minute_confidence_verified = minute_usage.get("confidence_bounds_verified")
        minute_queried_at = minute_usage.get("queried_at_utc")
        minute_analytics_groups = minute_usage.get("analytics_groups")
        if (
            isinstance(minute_rows_read, bool)
            or not isinstance(minute_rows_read, int)
            or minute_rows_read < 0
            or not isinstance(minute_per_database, list)
            or not isinstance(minute_confidence_verified, bool)
            or isinstance(minute_analytics_groups, bool)
            or not isinstance(minute_analytics_groups, int)
            or minute_analytics_groups < 0
        ):
            raise UsageQueryError("minute analytics report does not contain valid usage counts")
        if minute_analytics_groups > 0 and not minute_confidence_verified:
            raise UsageQueryError("minute analytics report did not verify confidence bounds")
        if daily_rows_read > 0 and not minute_confidence_verified:
            raise UsageQueryError("minute analytics omitted rows shown in the daily account usage")
        if minute_rows_read > 0 and not minute_confidence_verified:
            raise UsageQueryError("minute analytics reported rows without verified confidence bounds")
        for item in minute_per_database:
            if not isinstance(item, dict):
                raise UsageQueryError("minute analytics report contains an invalid database total")
            database_id = item.get("database_id")
            rows_read = item.get("rows_read")
            if (
                not isinstance(database_id, str)
                or isinstance(rows_read, bool)
                or not isinstance(rows_read, int)
                or rows_read < 0
            ):
                raise UsageQueryError("minute analytics report contains an invalid database total")
            if database_id in minute_rows_by_database:
                raise UsageQueryError("minute analytics report contains a duplicate database total")
            minute_rows_by_database[database_id] = rows_read
        if sum(minute_rows_by_database.values()) != minute_rows_read:
            raise UsageQueryError("minute analytics account total does not match its database totals")

    daily_rows_by_database: dict[str, int] = {}
    for item in daily_per_database:
        if not isinstance(item, dict):
            raise UsageQueryError("daily analytics report contains an invalid database total")
        database_id = item.get("database_id")
        rows_read = item.get("rows_read")
        if (
            not isinstance(database_id, str)
            or isinstance(rows_read, bool)
            or not isinstance(rows_read, int)
            or rows_read < 0
        ):
            raise UsageQueryError("daily analytics report contains an invalid database total")
        if database_id in daily_rows_by_database:
            raise UsageQueryError("daily analytics report contains a duplicate database total")
        daily_rows_by_database[database_id] = rows_read
    if sum(daily_rows_by_database.values()) != daily_rows_read:
        raise UsageQueryError("daily analytics account total does not match its database totals")

    effective_per_database = []
    for database_id in sorted(daily_rows_by_database.keys() | minute_rows_by_database.keys()):
        effective_per_database.append({
            "database_id": database_id,
            "rows_read": max(
                daily_rows_by_database.get(database_id, 0),
                minute_rows_by_database.get(database_id, 0),
            ),
        })
    effective_rows_read = sum(item["rows_read"] for item in effective_per_database)
    return {
        **daily_usage,
        "daily_analytics_rows_read": daily_rows_read,
        "daily_analytics_per_database": daily_per_database,
        "minute_day_to_date_rows_read": minute_rows_read,
        "minute_day_to_date_per_database": [
            {"database_id": database_id, "rows_read": minute_rows_by_database[database_id]}
            for database_id in sorted(minute_rows_by_database)
        ],
        "minute_day_to_date_queried_at_utc": minute_queried_at,
        "minute_day_to_date_confidence_bounds_verified": minute_confidence_verified,
        "effective_rows_read": effective_rows_read if minute_usage is not None else daily_rows_read,
        "effective_rows_remaining": max(
            0,
            daily_limit_rows - (effective_rows_read if minute_usage is not None else daily_rows_read),
        ),
        "effective_limit_exceeded": (
            (effective_rows_read if minute_usage is not None else daily_rows_read) > daily_limit_rows
        ),
        "effective_per_database": effective_per_database if minute_usage is not None else daily_per_database,
        "budget_usage_source": (
            "daily_and_minute_day_to_date" if minute_usage is not None else "daily_analytics_only"
        ),
        "budget_usage_queried_at_utc": minute_queried_at or daily_usage.get("queried_at_utc"),
    }


def fetch_budget_usage(
    account_id: str,
    token: str,
    utc_date: str,
    daily_limit_rows: int = DEFAULT_FREE_DAILY_LIMIT,
    opener=None,
    timeout_seconds: float = 30.0,
) -> dict:
    """Fetch daily usage and, for today, cross-check it against exact minute totals."""
    utc_date = validate_utc_date(utc_date)
    started_at = datetime.now(timezone.utc)
    request_options = {"timeout_seconds": timeout_seconds}
    if opener is not None:
        request_options["opener"] = opener
    daily_usage = fetch_daily_usage(
        account_id,
        token,
        utc_date,
        daily_limit_rows,
        **request_options,
    )
    if utc_date != started_at.date().isoformat():
        return build_budget_usage_report(daily_usage)

    minute_end_at = datetime.now(timezone.utc)
    if minute_end_at.date().isoformat() != utc_date:
        raise UsageQueryError("UTC date changed during the usage check; rerun the preflight")
    minute_usage = fetch_minute_usage(
        account_id,
        token,
        f"{utc_date}T00:00:00Z",
        format_utc_minute(minute_end_at),
        daily_limit_rows,
        **request_options,
    )
    if datetime.now(timezone.utc).date().isoformat() != utc_date:
        raise UsageQueryError("UTC date changed during the usage check; rerun the preflight")
    return build_budget_usage_report(daily_usage, minute_usage)


def non_negative_int(value: str) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("must be an integer") from error
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be non-negative")
    return parsed


def has_sufficient_remaining_budget(report: dict, minimum_rows_remaining: int) -> bool:
    if (
        isinstance(minimum_rows_remaining, bool)
        or not isinstance(minimum_rows_remaining, int)
        or minimum_rows_remaining < 0
    ):
        raise UsageQueryError("minimum remaining rows must be a non-negative integer")
    remaining = report.get("effective_rows_remaining", report.get("rows_remaining"))
    exceeded = report.get("effective_limit_exceeded", report.get("limit_exceeded"))
    if isinstance(remaining, bool) or not isinstance(remaining, int) or remaining < 0 or not isinstance(exceeded, bool):
        raise UsageQueryError("usage report does not contain a valid remaining-row budget")
    return not exceeded and remaining >= minimum_rows_remaining


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--account-id", default=os.environ.get("CLOUDFLARE_ACCOUNT_ID"))
    parser.add_argument(
        "--utc-date",
        default=datetime.now(timezone.utc).date().isoformat(),
        help="UTC calendar date to sum (default: today in UTC)",
    )
    parser.add_argument("--daily-limit", type=non_negative_int, default=DEFAULT_FREE_DAILY_LIMIT)
    parser.add_argument(
        "--minimum-rows-remaining",
        type=non_negative_int,
        default=0,
        help="fail after reporting if the account has less than this many rows remaining today",
    )
    args = parser.parse_args(argv)
    token = os.environ.get("CLOUDFLARE_API_TOKEN")
    if not args.account_id:
        parser.error("--account-id or CLOUDFLARE_ACCOUNT_ID is required")
    try:
        report = fetch_budget_usage(args.account_id, token or "", args.utc_date, args.daily_limit)
        report["minimum_rows_remaining"] = args.minimum_rows_remaining
        report["budget_sufficient"] = has_sufficient_remaining_budget(report, args.minimum_rows_remaining)
    except UsageQueryError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2))
    if not report["budget_sufficient"]:
        print("error: effective account-wide D1 daily allowance is below the required reserve", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
