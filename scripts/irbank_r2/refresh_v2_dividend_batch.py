"""Refresh IRBANK issuer dividends under a resumable 200-request daily cap."""

from __future__ import annotations

import argparse
from datetime import date, datetime, timedelta, timezone
import hashlib
import json
import math
from pathlib import Path
import sys
from typing import Any

import refresh_v2_dividends as refresh


DEFAULT_R2_KEY = "market/yfinance/v2/issuer_dividends/current.json"
DEFAULT_OUTPUT = Path.home() / ".cache" / "diview-irbank-v2-dividend-batch.json"
MAX_DAILY_REQUESTS = 200
MAX_FORECAST_REQUESTS = 9  # one disclosure list and up to eight candidate documents


def parse_time(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)
    except ValueError:
        return None


def complete_actual(item: dict[str, Any]) -> bool:
    return item.get("actual_status") == "empty" or (
        item.get("actual_status") == "available" and isinstance(item.get("actual"), dict)
    )


def complete_forecast(item: dict[str, Any]) -> bool:
    return item.get("forecast_status") == "empty" or (
        item.get("forecast_status") == "available" and isinstance(item.get("forecast"), dict)
    )


def initialize_checked_at(item: dict[str, Any], kind: str, generated_at: Any,
                          now: datetime) -> datetime | None:
    key = f"{kind}_checked_at"
    checked = parse_time(item.get(key))
    if checked is not None:
        return checked

    complete = complete_actual(item) if kind == "actual" else complete_forecast(item)
    if not complete:
        return None

    # Older indexes did not record a per-security fetch time. Use the existing
    # index generation time once, then keep that timestamp on the item.
    reference = parse_time(generated_at)
    if kind == "forecast" and isinstance(item.get("forecast"), dict):
        disclosed_at = parse_time(item["forecast"].get("disclosed_at"))
        if disclosed_at is not None:
            reference = disclosed_at
    reference = reference or now
    item[key] = reference.isoformat().replace("+00:00", "Z")
    return reference


def is_due(item: dict[str, Any], kind: str, generated_at: Any, now: datetime,
           refresh_days: int) -> bool:
    checked = initialize_checked_at(item, kind, generated_at, now)
    if checked is None:
        return True
    if kind == "forecast" and isinstance(item.get("forecast"), dict):
        first_period_end = item["forecast"].get("first_period_end")
        if first_period_end:
            try:
                if date.fromisoformat(str(first_period_end)) <= now.astimezone(timezone(timedelta(hours=9))).date():
                    return True
            except ValueError:
                return True
    if kind == "forecast" and str(item.get("edinet_code") or "").startswith("G") and item.get("forecast_status") == "empty":
        return now - checked >= timedelta(days=min(refresh_days, 7))
    return now - checked >= timedelta(days=refresh_days)


def code_order(codes: list[str], next_code: Any) -> list[str]:
    if not codes:
        return []
    try:
        start = codes.index(str(next_code).upper())
    except ValueError:
        start = 0
    return codes[start:] + codes[:start]


def update_actual(item: dict[str, Any], fetched: dict[str, Any], checked_at: str) -> None:
    for key in ("code", "name", "edinet_code", "actual_status", "actual"):
        if key in fetched:
            item[key] = fetched[key]
    if fetched.get("attribution"):
        item["attribution"] = fetched["attribution"]
    item["actual_checked_at"] = checked_at
    item.pop("actual_last_error", None)
    item.pop("actual_last_attempt_at", None)


def update_forecast(item: dict[str, Any], forecast: dict[str, Any] | None,
                    attribution: dict[str, Any] | None, checked_at: str) -> None:
    if forecast is None and isinstance(item.get("forecast"), dict):
        # Keep the previous observation for audit, but do not expose it as a
        # current forecast when the latest disclosure cannot be normalized.
        item["previous_forecast"] = item["forecast"]
    item["forecast"] = forecast
    item["forecast_status"] = "available" if forecast else "empty"
    item["forecast_checked_at"] = checked_at
    if attribution:
        item["forecast_attribution"] = attribution
    item.pop("forecast_last_error", None)
    item.pop("forecast_last_attempt_at", None)


def record_failure(item: dict[str, Any], kind: str, error: Exception, attempted_at: str) -> None:
    item[f"{kind}_last_attempt_at"] = attempted_at
    item[f"{kind}_last_error"] = f"http_{error.status}" if isinstance(error, refresh.ApiFailure) else type(error).__name__


def count_pending(items: dict[str, dict[str, Any]], codes: list[str], generated_at: Any,
                  now: datetime, refresh_days: int) -> tuple[int, int]:
    pending_actual = 0
    pending_forecast = 0
    for code in codes:
        item = items[code]
        pending_actual += int(is_due(item, "actual", generated_at, now, refresh_days))
        pending_forecast += int(is_due(item, "forecast", generated_at, now, refresh_days))
    return pending_actual, pending_forecast


def publish_index(client: Any, bucket: str, key: str, document: dict[str, Any]) -> tuple[str, str]:
    payload = refresh.json_bytes(document)
    digest = hashlib.sha256(payload).hexdigest()
    wrote_index = False
    try:
        shard_manifest = (
            refresh.prepare_dividend_index_shards(client, bucket, document)
            if key == DEFAULT_R2_KEY else None
        )
        response = client.put_object(
            Bucket=bucket,
            Key=key,
            Body=payload,
            ContentType="application/json; charset=utf-8",
            CacheControl="public, max-age=300",
        )
        saved = client.get_object(Bucket=bucket, Key=key)["Body"].read()
        if hashlib.sha256(saved).hexdigest() != digest:
            raise RuntimeError(f"IRBANK v2 dividend read-back mismatch: {key}")
        if shard_manifest is not None:
            refresh.activate_dividend_index_shards(client, bucket, shard_manifest)
        wrote_index = True
        return digest, str(response.get("ETag") or "").strip('"')
    finally:
        if wrote_index and key == DEFAULT_R2_KEY:
            refresh.bump_financial_revision(client, bucket)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-url", default=refresh.DEFAULT_CATALOG_URL)
    parser.add_argument("--from-fiscal-year", type=int, default=2024)
    parser.add_argument("--max-api-requests", type=int, default=MAX_DAILY_REQUESTS)
    parser.add_argument("--refresh-days", type=int, default=60)
    parser.add_argument("--target-days", type=int, default=60)
    parser.add_argument("--forecast-codes", default="", help="Refresh only these issuer forecasts without moving the batch cursor")
    parser.add_argument("--input", type=Path, help="Local dividend index for a non-publishing run")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--bucket", default="diview-market-data")
    parser.add_argument("--key", default=DEFAULT_R2_KEY)
    args = parser.parse_args()
    if not 1 <= args.max_api_requests <= MAX_DAILY_REQUESTS:
        raise ValueError(f"--max-api-requests must be between 1 and {MAX_DAILY_REQUESTS}")
    if args.refresh_days < 1:
        raise ValueError("--refresh-days must be at least 1")
    if args.target_days < 1:
        raise ValueError("--target-days must be at least 1")
    if not args.publish and not args.input:
        parser.error("--input is required unless --publish is set")

    token = refresh.required_env("IRBANK_API_KEY")
    catalog = refresh.load_catalog(args.catalog_url)
    securities = sorted(catalog, key=lambda row: str(row["code"]).upper())
    codes = [str(row["code"]).upper() for row in securities]
    if not securities:
        raise ValueError("IRBANK catalog is empty; refusing to publish")

    client = refresh.make_r2_client(1) if args.publish else None
    if client:
        existing = refresh.read_r2_document(client, args.bucket, args.key)
        if existing is None:
            raise RuntimeError(f"Refusing to create a partial issuer dividend index: {args.key} is missing")
    else:
        existing = json.loads(args.input.read_text(encoding="utf-8"))
    if not isinstance(existing, dict) or not isinstance(existing.get("items"), dict):
        raise ValueError("Issuer dividend index does not contain items{}")

    # The usage lookup is counted as one of the daily requests to keep the cap
    # safe even if IRBANK includes this endpoint in its quota.
    usage = refresh.request_json(f"{refresh.API_BASE}/usage", token)
    try:
        quota_remaining = max(0, int(usage.get("remaining")))
    except (TypeError, ValueError):
        raise ValueError("IRBANK usage response has no valid remaining request count")
    # Reserve one slot for the usage lookup itself. This is conservative if
    # IRBANK excludes that endpoint from its daily quota.
    budget_limit = min(args.max_api_requests, quota_remaining)
    budget = refresh.RequestBudget(budget_limit, used=1)

    items = {
        str(code).upper(): dict(item)
        for code, item in existing["items"].items()
        if isinstance(item, dict)
    }
    generated_at = existing.get("generated_at")
    for security in securities:
        code = str(security["code"]).upper()
        item = items.setdefault(code, {})
        item.setdefault("code", code)
        item.setdefault("name", str(security.get("name") or code))
        item.setdefault("edinet_code", security.get("edinet_code"))
        if "actual_status" not in item:
            item["actual_status"] = "available" if isinstance(item.get("actual"), dict) else "not_fetched"
        item.setdefault("actual", None)
        if "forecast_status" not in item:
            item["forecast_status"] = "available" if isinstance(item.get("forecast"), dict) else "not_requested"
        item.setdefault("forecast", None)

    previous_sync = existing.get("batch_sync")
    sync = dict(previous_sync) if isinstance(previous_sync, dict) else {}
    rotating = code_order(codes, sync.get("next_code"))
    if not rotating:
        raise ValueError("IRBANK catalog contains no valid security codes")
    today_utc = datetime.now(timezone.utc).date()
    retry_codes = [
        code for code in rotating
        if any(
            items[code].get(f"{kind}_last_error")
            and (last_attempt := parse_time(items[code].get(f"{kind}_last_attempt_at"))) is not None
            and last_attempt.date() < today_utc
            for kind in ("actual", "forecast")
        )
    ]
    retry_set = set(retry_codes)
    ordered = retry_codes + [code for code in rotating if code not in retry_set]
    target_codes = list(dict.fromkeys(code.strip().upper() for code in args.forecast_codes.split(",") if code.strip()))
    if target_codes:
        unknown = set(target_codes) - set(codes)
        if unknown:
            raise ValueError(f"Forecast codes missing from IRBANK catalog: {', '.join(sorted(unknown))}")
        ordered = target_codes

    now = datetime.now(timezone.utc)
    now_iso = now.replace(microsecond=0).isoformat().replace("+00:00", "Z")
    attempted_at = now_iso
    failures = 0
    actual_updates = 0
    forecast_updates = 0
    codes_touched = 0
    next_code = ordered[0]
    stopped_for_budget = False
    security_by_code = {str(row["code"]).upper(): row for row in securities}

    for index, code in enumerate(ordered):
        security = security_by_code[code]
        item = items[code]
        actual_due = not target_codes and is_due(item, "actual", generated_at, now, args.refresh_days)
        forecast_due = bool(target_codes) or is_due(item, "forecast", generated_at, now, args.refresh_days)
        after_code = ordered[(index + 1) % len(ordered)]
        if not actual_due and not forecast_due:
            next_code = after_code
            continue

        if actual_due and budget.remaining < 1:
            next_code = code
            stopped_for_budget = True
            break
        if not actual_due and forecast_due and budget.remaining < MAX_FORECAST_REQUESTS:
            next_code = code
            stopped_for_budget = True
            break
        codes_touched += 1

        if actual_due:
            try:
                _, fetched = refresh.fetch_dividend_record(
                    security, token, args.from_fiscal_year, budget=budget
                )
                update_actual(item, fetched, attempted_at)
                actual_updates += 1
            except refresh.RequestBudgetExceeded:
                next_code = code
                stopped_for_budget = True
                break
            except refresh.ApiFailure as error:
                if error.status == 404 and not isinstance(item.get("actual"), dict):
                    update_actual(item, {
                        "code": code,
                        "name": str(security.get("name") or code),
                        "edinet_code": security.get("edinet_code"),
                        "actual_status": "empty",
                        "actual": None,
                    }, attempted_at)
                    actual_updates += 1
                else:
                    failures += 1
                    record_failure(item, "actual", error, attempted_at)
            except Exception as error:
                failures += 1
                record_failure(item, "actual", error, attempted_at)

        # Forecast extraction may use a disclosure-list call plus as many as
        # eight document calls. Start it only when the whole current policy's
        # worst-case allowance remains; request retries are still hard-capped.
        forecast_due = bool(target_codes) or is_due(item, "forecast", generated_at, now, args.refresh_days)
        if forecast_due:
            if budget.remaining < MAX_FORECAST_REQUESTS:
                next_code = code
                stopped_for_budget = True
                break
            try:
                forecast, attribution = refresh.fetch_latest_forecast(
                    code, item.get("actual"), token, budget=budget
                )
                update_forecast(item, forecast, attribution, attempted_at)
                forecast_updates += 1
            except refresh.RequestBudgetExceeded:
                next_code = code
                stopped_for_budget = True
                break
            except refresh.ApiFailure as error:
                # A missing disclosure endpoint is not a successful empty forecast.
                # Preserve any previously fetched forecast for review and retry.
                failures += 1
                record_failure(item, "forecast", error, attempted_at)
            except Exception as error:
                failures += 1
                record_failure(item, "forecast", error, attempted_at)

        next_code = after_code
        if budget.remaining == 0:
            stopped_for_budget = True
            break

    if not stopped_for_budget:
        next_code = ordered[0]
    if target_codes:
        next_code = rotating[0]

    # Pending counts are a lower bound: a forecast can require multiple
    # disclosure-document calls, while every pending item needs at least one.
    pending_actual, pending_forecast = count_pending(items, codes, generated_at, now, args.refresh_days)
    completed_actual = sum(complete_actual(items.get(code, {})) for code in codes)
    completed_forecast = sum(complete_forecast(items.get(code, {})) for code in codes)
    minimum_requests_remaining = pending_actual + pending_forecast
    usable_per_day = max(0, args.max_api_requests - 1)
    minimum_days_remaining = (
        math.ceil(minimum_requests_remaining / usable_per_day)
        if usable_per_day > 0 else (0 if minimum_requests_remaining == 0 else None)
    )
    backfill_started = parse_time(sync.get("backfill_started_at")) or now
    elapsed_days = max(0, (now.date() - backfill_started.date()).days)
    days_remaining_in_target = max(0, args.target_days - elapsed_days)
    target_date = (backfill_started + timedelta(days=args.target_days)).date().isoformat()
    coverage_complete = pending_actual == 0 and pending_forecast == 0
    initial_completion_at = parse_time(sync.get("initial_backfill_complete_at"))
    if coverage_complete and initial_completion_at is None:
        initial_completion_at = now
    initial_completion_within_target = (
        initial_completion_at.date().isoformat() <= target_date if initial_completion_at else None
    )
    minimum_estimate_within_target = (
        initial_completion_within_target
        if initial_completion_at is not None
        else minimum_days_remaining is not None and minimum_days_remaining <= days_remaining_in_target
    )

    old_cycle = int(sync.get("cycle") or 0)
    if not stopped_for_budget and not target_codes:
        old_cycle += 1
    sync.update({
        "policy": "round_robin_irbank_dividend_and_forecast",
        "daily_request_limit": args.max_api_requests,
        "refresh_interval_days": args.refresh_days,
        "target_days": args.target_days,
        "backfill_started_at": backfill_started.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "target_date": target_date,
        "elapsed_days": elapsed_days,
        "days_remaining_in_target": days_remaining_in_target,
        "initial_backfill_complete_at": (
            initial_completion_at.replace(microsecond=0).isoformat().replace("+00:00", "Z")
            if initial_completion_at else None
        ),
        "initial_backfill_completed_within_target": initial_completion_within_target,
        "catalog_count": len(codes),
        "next_code": next_code,
        "cycle": old_cycle,
        "last_run_at": attempted_at,
        "last_run_request_count_including_usage": budget.used,
        "last_run_actual_updates": actual_updates,
        "last_run_forecast_updates": forecast_updates,
        "last_run_codes_touched": codes_touched,
        "last_run_failures": failures,
        "quota_remaining_at_start": quota_remaining,
        "pending_actual_count": pending_actual,
        "pending_forecast_count": pending_forecast,
        "minimum_requests_remaining": minimum_requests_remaining,
        "minimum_days_remaining_at_200_per_day": minimum_days_remaining,
        "minimum_estimate_within_target": minimum_estimate_within_target,
        "coverage_complete": coverage_complete,
        "last_run_stopped_for_budget": stopped_for_budget,
    })

    updated = dict(existing)
    updated.update({
        "schema_version": int(existing.get("schema_version") or 1),
        "generated_at": attempted_at,
        "source": existing.get("source") or "IRBANK Connect REST API",
        "count": len(items),
        "items": {code: items[code] for code in sorted(items)},
        "batch_sync": sync,
    })
    args.output.parent.mkdir(parents=True, exist_ok=True)
    payload = refresh.json_bytes(updated)
    args.output.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest()

    report = {
        "catalog_count": len(codes),
        "actual_complete": completed_actual,
        "forecast_complete": completed_forecast,
        "actual_pending": pending_actual,
        "forecast_pending": pending_forecast,
        "minimum_requests_remaining": minimum_requests_remaining,
        "minimum_days_remaining_at_200_per_day": minimum_days_remaining,
        "target_date": target_date,
        "minimum_estimate_within_target": minimum_estimate_within_target,
        "api_requests_this_run_including_usage": budget.used,
        "quota_remaining_at_start": quota_remaining,
        "actual_updates": actual_updates,
        "forecast_updates": forecast_updates,
        "codes_touched": codes_touched,
        "failures": failures,
        "next_code": next_code,
        "coverage_complete": sync["coverage_complete"],
        "sha256": digest,
    }
    print(json.dumps(report, ensure_ascii=False))

    if client:
        saved_digest, etag = publish_index(client, args.bucket, args.key, updated)
        print(json.dumps({"published_key": args.key, "etag": etag, "read_back_sha256": saved_digest}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"fatal: {error}", file=sys.stderr)
        raise
