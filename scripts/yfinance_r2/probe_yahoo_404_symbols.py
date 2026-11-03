#!/usr/bin/env python3
"""Read-only, rate-limited Yahoo history probe for previously failed symbols."""

from __future__ import annotations

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import yfinance as yf


def probe_symbol(code: str, session_date: str) -> dict[str, Any]:
    symbol = f"{code}.T"
    try:
        history = yf.Ticker(symbol).history(
            period="1mo", interval="1d", auto_adjust=False, actions=False,
            raise_errors=True,
        )
        if history is None or history.empty or "Close" not in history:
            return {
                "code": code, "symbol": symbol, "status": "empty_history",
                "session_date": session_date, "observed_rows": 0,
                "last_close_date": None, "last_close": None,
            }
        closes = history["Close"].dropna()
        if closes.empty:
            return {
                "code": code, "symbol": symbol, "status": "no_close_values",
                "session_date": session_date, "observed_rows": int(len(history)),
                "last_close_date": None, "last_close": None,
            }
        last_date = closes.index[-1].date().isoformat()
        close = float(closes.iloc[-1])
        return {
            "code": code, "symbol": symbol,
            "status": "session_close" if last_date >= session_date else "stale_close",
            "session_date": session_date, "observed_rows": int(len(history)),
            "last_close_date": last_date, "last_close": close,
        }
    except Exception as exc:  # retain the provider's classification for diagnosis
        text = f"{type(exc).__name__}: {exc}"[:500]
        if "429" in text:
            status = "http_429"
        elif type(exc).__name__ == "YFPricesMissingError":
            status = "provider_no_data"
        elif "HTTP Error 404" in text or "Quote not found" in text:
            status = "http_404"
        else:
            status = "error"
        return {
            "code": code, "symbol": symbol,
            "status": status, "error_class": type(exc).__name__,
            "session_date": session_date, "observed_rows": 0,
            "last_close_date": None, "last_close": None,
            "error": text,
        }


def load_codes(path: str | Path) -> tuple[list[str], str]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    session_date = str(payload["official_listing_status_check"]["sync_market_date"])
    codes = sorted({str(row["code"]) for row in payload["top_level_failures"]})
    return codes, session_date


def write_report(path: str | Path, report: dict[str, Any]) -> None:
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    temporary.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    temporary.replace(output)


def run(audit_path: str | Path, output_path: str | Path, interval_seconds: float = 0.5) -> dict[str, Any]:
    codes, session_date = load_codes(audit_path)
    results = []
    for index, code in enumerate(codes):
        started = time.monotonic()
        results.append(probe_symbol(code, session_date))
        if results[-1]["status"] == "http_429":
            break
        if index + 1 < len(codes):
            time.sleep(max(0.0, interval_seconds - (time.monotonic() - started)))
    counts: dict[str, int] = {}
    for result in results:
        key = str(result["status"])
        counts[key] = counts.get(key, 0) + 1
    report = {
        "schema_version": 1,
        "source_audit": str(audit_path),
        "source_audit_market_date": session_date,
        "probe_completed_at": datetime.now(timezone.utc).isoformat(),
        "yfinance_version": yf.__version__,
        "interval_seconds": interval_seconds,
        "requested_count": len(codes),
        "probed_count": len(results),
        "status_counts": dict(sorted(counts.items())),
        "interpretation": (
            "This probe records current Yahoo history responses and cannot establish listing status on the historical sync date. "
            "YFPricesMissingError is provider no-data, not proof of delisting."
        ),
        "results": results,
    }
    write_report(output_path, report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", default="ai_docs/audits/yfinance-run314-failure-classification-20261102.json")
    parser.add_argument("--output", default="ai_docs/audits/yfinance-run314-yahoo-reprobe-20261103.json")
    parser.add_argument("--interval-seconds", type=float, default=0.5)
    args = parser.parse_args()
    report = run(args.audit, args.output, args.interval_seconds)
    print(json.dumps({key: report[key] for key in ("source_audit_market_date", "requested_count", "probed_count", "status_counts")}, ensure_ascii=False))
    return 1 if report["status_counts"].get("http_429") else 0


if __name__ == "__main__":
    raise SystemExit(main())
