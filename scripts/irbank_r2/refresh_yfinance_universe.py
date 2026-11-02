"""Refresh the weekly IRBANK source list used by the yfinance v2 daily sync."""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import sys
from zoneinfo import ZoneInfo
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parents[2]
YFINANCE_DIR = ROOT / "scripts" / "yfinance_r2"
sys.path.insert(0, str(YFINANCE_DIR))

import refresh_v2_dividends as irbank_api  # noqa: E402
from irbank_universe import (  # noqa: E402
    IRBANK_UNIVERSE_R2_KEY,
    JPX_ETF_SOURCE,
    JPX_ETF_SOURCE_URL,
    build_snapshot,
    merge_jpx_etfs,
    merge_jpx_reits,
    normalize_securities,
    validate_snapshot,
)
from jpx_etf_universe import fetch_jpx_etfs  # noqa: E402
from jpx_reit_universe import JPX_REIT_SOURCE, JPX_REIT_SOURCE_URL, fetch_jpx_reits  # noqa: E402


API_BASE = "https://api.irbank.net/v1"
DEFAULT_BUCKET = "diview-market-data"
PAGE_SIZE = 500
DEFAULT_MAX_API_REQUESTS = 200


def parse_timestamp(value: object) -> datetime | None:
    raw = str(value or "").strip()
    if not raw:
        return None
    try:
        parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed.astimezone(timezone.utc) if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


def has_current_sources(snapshot: object) -> bool:
    if not isinstance(snapshot, dict) or snapshot.get("schema_version") != 2:
        return False
    supplemental = snapshot.get("supplemental_sources")
    if not isinstance(supplemental, list):
        return False
    required_sources = (
        (JPX_ETF_SOURCE, JPX_ETF_SOURCE_URL),
        (JPX_REIT_SOURCE, JPX_REIT_SOURCE_URL),
    )
    return all(
        any(
            isinstance(source, dict)
            and source.get("source") == expected
            and source.get("url") == url
            for source in supplemental
        )
        for expected, url in required_sources
    )


def write_json(path: Path, document: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def write_github_outputs(requests_used: int, status: str) -> None:
    output_path = os.environ.get("GITHUB_OUTPUT", "").strip()
    if not output_path:
        return
    with Path(output_path).open("a", encoding="utf-8", newline="") as handle:
        handle.write(f"requests_used={requests_used}\nstatus={status}\n")


def fetch_complete_universe(token: str, max_api_requests: int) -> tuple[list[dict[str, str]] | None, irbank_api.RequestBudget, str]:
    budget = irbank_api.RequestBudget(max_api_requests)
    try:
        usage = irbank_api.request_json(f"{API_BASE}/usage", token, budget=budget)
        try:
            quota_remaining = max(0, int(usage.get("remaining")))
        except (TypeError, ValueError) as error:
            raise ValueError("IRBANK usage response has no valid remaining request count") from error
        # Count the usage request against our workflow cap and conservatively
        # bound the complete refresh by the provider's remaining quota.
        budget.limit = min(max_api_requests, max(1, quota_remaining))
        if budget.remaining == 0:
            return None, budget, "deferred_quota"

        rows: list[dict[str, object]] = []
        cursor: str | None = None
        seen_cursors: set[str] = set()
        while True:
            if budget.remaining == 0:
                return None, budget, "deferred_budget"
            params: dict[str, str | int] = {"limit": PAGE_SIZE}
            if cursor:
                params["cursor"] = cursor
            page = irbank_api.request_json(
                f"{API_BASE}/securities?{urlencode(params)}", token, budget=budget,
            )
            securities = page.get("securities")
            if not isinstance(securities, list):
                raise ValueError("IRBANK securities response does not contain securities[]")
            rows.extend(row for row in securities if isinstance(row, dict))
            next_cursor = str(page.get("next_cursor") or "") or None
            if not next_cursor:
                return normalize_securities(rows), budget, "updated"
            if next_cursor in seen_cursors:
                raise RuntimeError("IRBANK securities API repeated a cursor; previous R2 snapshot retained")
            seen_cursors.add(next_cursor)
            cursor = next_cursor
    except Exception as error:
        setattr(error, "irbank_requests_used", budget.used)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bucket", default=os.environ.get("R2_BUCKET", DEFAULT_BUCKET))
    parser.add_argument("--max-api-requests", type=int, default=DEFAULT_MAX_API_REQUESTS)
    parser.add_argument("--refresh-days", type=int, default=7)
    parser.add_argument("--output", type=Path, help="Write the active snapshot for the Actions artifact")
    args = parser.parse_args()
    if not 1 <= args.max_api_requests <= DEFAULT_MAX_API_REQUESTS:
        raise ValueError(f"--max-api-requests must be between 1 and {DEFAULT_MAX_API_REQUESTS}")
    if args.refresh_days < 1:
        raise ValueError("--refresh-days must be at least 1")

    requests_used = 0
    status = "failed"
    try:
        client = irbank_api.make_r2_client(1)
        existing = irbank_api.read_r2_document(client, args.bucket, IRBANK_UNIVERSE_R2_KEY)
        if existing is not None:
            try:
                validate_snapshot(existing, allow_legacy=True)
            except ValueError as error:
                print(f"Existing IRBANK universe snapshot is invalid and will be replaced only after a complete refresh: {error}", flush=True)
                existing = None
            else:
                fetched_at = parse_timestamp(existing.get("fetched_at"))
                now = datetime.now(timezone.utc)
                if has_current_sources(existing) and fetched_at is not None and timedelta(0) <= now - fetched_at < timedelta(days=args.refresh_days):
                    if args.output:
                        write_json(args.output, existing)
                    print(
                        f"IRBANK yfinance universe is fresh: count={existing['security_count']}, "
                        f"fetched_at={existing['fetched_at']}, api_requests=0",
                        flush=True,
                    )
                    status = "fresh"
                    return 0

        token = irbank_api.required_env("IRBANK_API_KEY")
        try:
            snapshot_securities, budget, fetch_status = fetch_complete_universe(token, args.max_api_requests)
        except Exception as error:
            requests_used = max(requests_used, int(getattr(error, "irbank_requests_used", 0)))
            raise
        requests_used = budget.used
        if fetch_status.startswith("deferred") or snapshot_securities is None:
            print(
                f"IRBANK yfinance universe refresh deferred ({fetch_status}); "
                f"previous R2 snapshot preserved; api_requests={requests_used}",
                flush=True,
            )
            if existing is not None and args.output:
                write_json(args.output, existing)
            status = fetch_status
            return 0

        fetched_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        as_of = datetime.now(ZoneInfo("Asia/Tokyo")).date()
        jpx_etfs = fetch_jpx_etfs(as_of=as_of)
        jpx_reits = fetch_jpx_reits(as_of=as_of)
        combined_securities = merge_jpx_reits(merge_jpx_etfs(snapshot_securities, jpx_etfs), jpx_reits)
        snapshot = build_snapshot(
            combined_securities, fetched_at, jpx_etf_count=len(jpx_etfs), jpx_reit_count=len(jpx_reits)
        )
        validate_snapshot(snapshot)
        previous_codes = {
            str(row["security_code_display"])
            for row in (existing or {}).get("securities", [])
            if isinstance(row, dict) and row.get("security_code_display")
        }
        current_codes = {row["security_code_display"] for row in combined_securities}
        body = irbank_api.json_bytes(snapshot)
        client.put_object(
            Bucket=args.bucket,
            Key=IRBANK_UNIVERSE_R2_KEY,
            Body=body,
            ContentType="application/json; charset=utf-8",
            CacheControl="no-store",
        )
        response = client.get_object(Bucket=args.bucket, Key=IRBANK_UNIVERSE_R2_KEY)
        read_back = response["Body"].read()
        if read_back != body:
            raise RuntimeError("IRBANK yfinance universe R2 read-back does not match the uploaded snapshot")
        if args.output:
            write_json(args.output, snapshot)
        print(json.dumps({
            "status": "updated",
            "source": snapshot["source"],
            "fetched_at": snapshot["fetched_at"],
            "security_count": snapshot["security_count"],
            "irbank_security_count": len(snapshot_securities),
            "jpx_listed_etf_count": len(jpx_etfs),
            "jpx_listed_reit_count": len(jpx_reits),
            "jpx_etfs_added_to_irbank": len(set(row["security_code_display"] for row in jpx_etfs) - set(row["security_code_display"] for row in snapshot_securities)),
            "jpx_reits_added_to_irbank": len(set(row["security_code_display"] for row in jpx_reits) - set(row["security_code_display"] for row in snapshot_securities)),
            "security_code_sha256": snapshot["security_code_sha256"],
            "added_codes": len(current_codes - previous_codes),
            "removed_codes": len(previous_codes - current_codes),
            "api_requests": requests_used,
            "r2_key": IRBANK_UNIVERSE_R2_KEY,
            "read_back_sha256": hashlib.sha256(read_back).hexdigest(),
        }, ensure_ascii=False), flush=True)
        status = "updated"
        return 0
    finally:
        write_github_outputs(requests_used, status)


if __name__ == "__main__":
    raise SystemExit(main())
