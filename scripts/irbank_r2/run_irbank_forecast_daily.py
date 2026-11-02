"""Process the saved IRBANK universe snapshot in up to twenty forecast batches."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Any

import refresh_v2_dividends as api


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_UNIVERSE = Path.home() / ".cache" / "irbank-security-catalog-20261102.json"


def run(command: list[str], *, label: str, env: dict[str, str]) -> str:
    print(f"Starting: {label}", flush=True)
    result = subprocess.run(command, cwd=SCRIPT_DIR.parents[1], env=env,
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.stdout:
        print(result.stdout.rstrip(), flush=True)
    if result.stderr:
        print(result.stderr.rstrip(), file=sys.stderr, flush=True)
    if result.returncode != 0:
        raise RuntimeError(f"{label} exited with status {result.returncode}")
    return result.stdout


def json_objects(output: str) -> list[dict[str, Any]]:
    rows = []
    for line in output.splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            rows.append(value)
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--universe-json", type=Path, default=DEFAULT_UNIVERSE)
    parser.add_argument("--max-batches", type=int, default=20)
    parser.add_argument("--requests-per-batch", type=int, default=200)
    parser.add_argument("--refresh-days", type=int, default=60)
    parser.add_argument("--target-days", type=int, default=60)
    parser.add_argument("--bucket", default="diview-market-data")
    parser.add_argument("--key", default="market/yfinance/v2/issuer_dividends/current.json")
    args = parser.parse_args()
    if not 1 <= args.max_batches <= 20:
        raise ValueError("--max-batches must be between 1 and 20")
    if not 1 <= args.requests_per_batch <= 200:
        raise ValueError("--requests-per-batch must be between 1 and 200")

    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    env["PYTHONIOENCODING"] = "utf-8"
    token = api.required_env("IRBANK_API_KEY")
    usage = api.request_json(f"{api.API_BASE}/usage", token)
    quota_remaining = max(0, int(usage.get("remaining") or 0))
    if quota_remaining == 0:
        print(json.dumps({
            "stopped": "daily_quota_exhausted",
            "quota_remaining": 0,
            "resets_at": usage.get("resets_at"),
            "r2_published": False,
        }, ensure_ascii=False), flush=True)
        return 0

    universe = json.loads(args.universe_json.read_text(encoding="utf-8"))
    if not isinstance(universe, dict) or not isinstance(universe.get("securities"), list):
        raise ValueError("Saved universe snapshot does not contain securities[]")
    universe_complete = bool(universe.get("complete")) and not bool(universe.get("next_cursor"))
    print(json.dumps({
        "universe_count": len(universe["securities"]),
        "universe_complete": universe_complete,
        "next_cursor_present": bool(universe.get("next_cursor")),
        "scope": "complete" if universe_complete else "partial_snapshot_only",
    }, ensure_ascii=False), flush=True)

    total_requests_including_usage = 1
    quota_consuming_calls = 0
    total_codes_touched = 0
    total_forecast_updates = 0
    total_forecast_cache_reuses = 0
    total_failures = 0
    batches_run = 0
    for batch_number in range(1, args.max_batches + 1):
        output = run([
            sys.executable, str(SCRIPT_DIR / "refresh_v2_dividend_batch.py"),
            "--publish",
            "--universe-json", str(args.universe_json),
            "--allow-partial-universe",
            "--forecasts-only",
            "--max-api-requests", str(args.requests_per_batch),
            "--refresh-days", str(args.refresh_days),
            "--target-days", str(args.target_days),
            "--bucket", args.bucket,
            "--key", args.key,
        ], label=f"forecast batch {batch_number}/{args.max_batches}", env=env)
        reports = json_objects(output)
        report = next((row for row in reports if "api_requests_this_run_including_usage" in row), None)
        if report is None:
            raise RuntimeError(f"Forecast batch {batch_number} emitted no batch report")
        batches_run += 1
        used = int(report.get("api_requests_this_run_including_usage") or 0)
        total_requests_including_usage += used
        quota_consuming_calls += max(0, used - 1)  # /v1/usage is free under IRBANK's published limit rules.
        total_codes_touched += int(report.get("codes_touched") or 0)
        total_forecast_updates += int(report.get("forecast_updates") or 0)
        total_forecast_cache_reuses += int(report.get("forecast_cache_reuses") or 0)
        total_failures += int(report.get("failures") or 0)
        if used <= 1 or int(report.get("codes_touched") or 0) == 0:
            break

    audit_output = run([
        sys.executable, str(SCRIPT_DIR / "audit_irbank_forecast_coverage.py"),
        "--universe-json", str(args.universe_json),
        "--bucket", args.bucket,
        "--key", args.key,
    ], label="audit R2 forecast coverage", env=env)
    audit_reports = json_objects(audit_output)
    audit_report = audit_reports[-1] if audit_reports else {}
    print(json.dumps({
        "batches_run": batches_run,
        "catalog_api_calls": 0,
        "runner_usage_checks": 1,
        "quota_remaining_at_start": quota_remaining,
        "universe_complete": universe_complete,
        "total_api_calls_including_free_usage_checks": total_requests_including_usage,
        "quota_consuming_api_calls": quota_consuming_calls,
        "codes_touched": total_codes_touched,
        "forecast_updates": total_forecast_updates,
        "forecast_cache_reuses": total_forecast_cache_reuses,
        "forecast_detail_calls_saved": total_forecast_cache_reuses,
        "failures": total_failures,
        "coverage_audit": audit_report,
    }, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"fatal: {error}", file=sys.stderr, flush=True)
        raise SystemExit(1)
