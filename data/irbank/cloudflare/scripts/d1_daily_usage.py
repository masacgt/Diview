#!/usr/bin/env python3
"""Read account-wide D1 daily usage from Cloudflare GraphQL Analytics (no D1 SQL)."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import date, datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


GRAPHQL_URL = "https://api.cloudflare.com/client/v4/graphql"
MAX_GROUPS = 10_000
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
) -> dict:
    account_id = validate_account_id(account_id)
    utc_date = validate_utc_date(utc_date)
    if not token or not token.strip():
        raise UsageQueryError("CLOUDFLARE_API_TOKEN is required")
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
        with open_request(request, timeout=30) as response:
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
    remaining = report.get("rows_remaining")
    exceeded = report.get("limit_exceeded")
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
        report = fetch_daily_usage(args.account_id, token or "", args.utc_date, args.daily_limit)
        report["minimum_rows_remaining"] = args.minimum_rows_remaining
        report["budget_sufficient"] = has_sufficient_remaining_budget(report, args.minimum_rows_remaining)
    except UsageQueryError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2))
    if not report["budget_sufficient"]:
        print("error: remaining account-wide D1 daily allowance is below the required reserve", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
