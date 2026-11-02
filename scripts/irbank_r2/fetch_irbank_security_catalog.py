"""Fetch and checkpoint the complete IRBANK securities list using cursor pagination."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from typing import Any
from urllib.parse import urlencode

import refresh_v2_dividends as api


DEFAULT_OUTPUT = Path.home() / ".cache" / "irbank-security-catalog.json"
SECURITY_CODE_RE = re.compile(r"^[A-Z0-9]{4,5}$")
PAGE_SIZE = 500


def save_checkpoint(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(state, ensure_ascii=False, separators=(",", ":")) + "\n"
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent,
                                     prefix=path.name + ".", suffix=".tmp", delete=False) as handle:
        handle.write(payload)
        temp_path = Path(handle.name)
    os.replace(temp_path, path)


def load_checkpoint(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {
            "schema_version": 1,
            "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "page_size": PAGE_SIZE,
            "pages": 0,
            "securities": [],
            "next_cursor": None,
            "complete": False,
        }
    state = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(state, dict) or not isinstance(state.get("securities"), list):
        raise ValueError(f"Invalid IRBANK security catalog checkpoint: {path}")
    state.setdefault("schema_version", 1)
    state.setdefault("page_size", PAGE_SIZE)
    state.setdefault("pages", 0)
    state["complete"] = not bool(state.get("next_cursor"))
    return state


def empty_checkpoint() -> dict[str, Any]:
    return {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "page_size": PAGE_SIZE,
        "pages": 0,
        "securities": [],
        "next_cursor": None,
        "complete": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--max-api-requests", type=int, default=200)
    parser.add_argument("--restart", action="store_true", help="Discard the local cursor and restart from page one")
    args = parser.parse_args()
    if not 1 <= args.max_api_requests <= 200:
        raise ValueError("--max-api-requests must be between 1 and 200")

    state = load_checkpoint(args.output)
    if args.restart:
        state = empty_checkpoint()
    if state.get("complete"):
        codes = sorted(str(row.get("code") or "").upper() for row in state["securities"]
                       if isinstance(row, dict))
        print(json.dumps({"output": str(args.output), "count": len(codes), "complete": True,
                          "requests_used": 0,
                          "code_sha256": hashlib.sha256("\n".join(codes).encode()).hexdigest()},
                         ensure_ascii=False))
        return 0

    token = api.required_env("IRBANK_API_KEY")
    usage = api.request_json(f"{api.API_BASE}/usage", token)
    try:
        quota_remaining = max(0, int(usage.get("remaining")))
    except (TypeError, ValueError):
        raise ValueError("IRBANK usage response has no valid remaining request count")
    budget = api.RequestBudget(min(args.max_api_requests, quota_remaining))
    securities = {
        str(row["code"]).upper(): row
        for row in state["securities"]
        if isinstance(row, dict) and SECURITY_CODE_RE.fullmatch(str(row.get("code") or "").upper())
    }
    cursor = str(state.get("next_cursor") or "") or None
    seen_cursors: set[str] = set()
    if cursor:
        seen_cursors.add(cursor)
    while budget.remaining > 0:
        params = {"limit": PAGE_SIZE}
        if cursor:
            params["cursor"] = cursor
        page = api.request_json(f"{api.API_BASE}/securities?{urlencode(params)}", token, budget=budget)
        rows = page.get("securities")
        if not isinstance(rows, list):
            raise ValueError("IRBANK securities response does not contain securities[]")
        for row in rows:
            if not isinstance(row, dict):
                continue
            code = str(row.get("code") or "").upper()
            if SECURITY_CODE_RE.fullmatch(code):
                securities[code] = row

        next_cursor = str(page.get("next_cursor") or "") or None
        state.update({
            "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "page_size": PAGE_SIZE,
            "pages": int(state.get("pages") or 0) + 1,
            "securities": [securities[code] for code in sorted(securities)],
            "next_cursor": next_cursor,
            "complete": next_cursor is None,
        })
        save_checkpoint(args.output, state)
        print(json.dumps({"page": state["pages"], "rows_in_page": len(rows),
                          "catalog_count": len(securities), "has_next_cursor": bool(next_cursor),
                          "requests_used": budget.used}, ensure_ascii=False))
        if not next_cursor:
            break
        if next_cursor in seen_cursors:
            raise RuntimeError("IRBANK securities API repeated a cursor; checkpoint retained")
        seen_cursors.add(next_cursor)
        cursor = next_cursor

    codes = sorted(securities)
    print(json.dumps({"output": str(args.output), "count": len(codes), "complete": bool(state.get("complete")),
                      "requests_used": budget.used, "quota_remaining_at_start": quota_remaining,
                      "has_next_cursor": bool(state.get("next_cursor")),
                      "market_counts": {
                          str(market or "null"): sum(1 for row in securities.values() if row.get("market") == market)
                          for market in sorted({row.get("market") for row in securities.values()}, key=lambda value: str(value))
                      },
                      "code_sha256": hashlib.sha256("\n".join(codes).encode()).hexdigest()}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
