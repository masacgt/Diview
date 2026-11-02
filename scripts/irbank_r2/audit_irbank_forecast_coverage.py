"""Read-only comparison of a saved IRBANK universe snapshot with the R2 v2 index."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
from typing import Any

import refresh_v2_dividends as refresh


DEFAULT_R2_KEY = "market/yfinance/v2/issuer_dividends/current.json"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--universe-json", type=Path, required=True)
    parser.add_argument("--bucket", default="diview-market-data")
    parser.add_argument("--key", default=DEFAULT_R2_KEY)
    args = parser.parse_args()

    universe = json.loads(args.universe_json.read_text(encoding="utf-8"))
    if not isinstance(universe, dict) or not isinstance(universe.get("securities"), list):
        raise ValueError("Universe JSON does not contain securities[]")
    universe_complete = bool(universe.get("complete")) and not bool(universe.get("next_cursor"))

    securities = {
        str(row["code"]).upper(): row
        for row in universe["securities"]
        if isinstance(row, dict) and row.get("code")
    }
    client = refresh.make_r2_client(1)
    document = refresh.read_r2_document(client, args.bucket, args.key)
    if not isinstance(document, dict) or not isinstance(document.get("items"), dict):
        raise ValueError(f"R2 v2 dividend index is missing or malformed: {args.key}")
    items: dict[str, Any] = {
        str(code).upper(): item for code, item in document["items"].items() if isinstance(item, dict)
    }
    universe_codes, index_codes = set(securities), set(items)
    status_counts: Counter[str] = Counter()
    resolution_counts: Counter[str] = Counter()
    market_status_counts: dict[str, Counter[str]] = defaultdict(Counter)
    audit_version_counts: Counter[str] = Counter()
    explicit_zero = 0
    errors = 0
    for code in universe_codes:
        item = items.get(code, {})
        market = str(securities[code].get("market") or "null")
        status = str(item.get("forecast_status") or "missing")
        resolution = str(item.get("forecast_resolution") or "unclassified")
        status_counts[status] += 1
        resolution_counts[resolution] += 1
        market_status_counts[market][status] += 1
        audit_version_counts[str(item.get("forecast_audit_version") or "none")] += 1
        forecast = item.get("forecast")
        if (isinstance(forecast, dict)
                and forecast.get("annual_dps_current_share_basis") == 0):
            explicit_zero += 1
        if item.get("forecast_last_error"):
            errors += 1

    codes_digest = hashlib.sha256("\n".join(sorted(universe_codes)).encode("utf-8")).hexdigest()
    report = {
        "universe_generated_at": universe.get("generated_at"),
        "universe_complete": universe_complete,
        "universe_next_cursor_present": bool(universe.get("next_cursor")),
        "coverage_scope": "complete_universe" if universe_complete else "partial_snapshot_only",
        "universe_count": len(universe_codes),
        "universe_code_sha256": codes_digest,
        "index_item_count": len(index_codes),
        "matched_codes": len(universe_codes & index_codes),
        "new_codes_missing_from_index": len(universe_codes - index_codes),
        "index_codes_outside_universe_preserved": len(index_codes - universe_codes),
        "forecast_status_counts": dict(status_counts),
        "forecast_resolution_counts": dict(resolution_counts),
        "audit_version_counts": dict(audit_version_counts),
        "explicit_zero_forecasts": explicit_zero,
        "forecast_errors": errors,
        "market_status_counts": {key: dict(value) for key, value in sorted(market_status_counts.items())},
        "batch_sync": document.get("batch_sync"),
    }
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
