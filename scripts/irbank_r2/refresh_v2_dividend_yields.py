"""Refresh the IRBANK screening dividend-yield values in the v2 issuer index."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import tempfile
from typing import Any
from urllib.parse import urlencode

from refresh_v2_dividends import (
    API_BASE,
    SECURITY_CODE_RE,
    iso_now,
    json_bytes,
    make_r2_client,
    read_r2_document,
    request_json,
    required_env,
)


DEFAULT_R2_KEY = "market/yfinance/v2/issuer_dividends/current.json"
DEFAULT_OUTPUT = Path(tempfile.gettempdir()) / "diview-irbank-v2-dividend-yields-current.json"
YIELD_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def fetch_screening_yields(token: str, limit: int) -> tuple[dict[str, dict[str, Any]], dict[str, Any]]:
    usage = request_json(f"{API_BASE}/usage", token)
    remaining_before = int(usage.get("remaining") or 0)
    params = {
        "filters": "dividendYield:gte:0",
        "sort_by": "dividendYield",
        "sort_order": "desc",
        "limit": limit,
    }
    first_page = request_json(f"{API_BASE}/screening?{urlencode(params)}", token)
    total_count = first_page.get("total_count")
    if not isinstance(total_count, int) or total_count < 0:
        raise ValueError("IRBANK screening response has an invalid total_count")
    expected_pages = max(1, math.ceil(total_count / limit))
    if remaining_before < expected_pages + 1:
        raise RuntimeError(
            f"IRBANK quota preflight: need about {expected_pages + 1} requests, {remaining_before} remain"
        )

    yields: dict[str, dict[str, Any]] = {}
    identities: dict[str, tuple[Any, ...]] = {}
    returned_rows = 0
    duplicate_rows = 0
    attribution: dict[str, Any] | None = None
    cursors: set[str] = set()
    page = first_page
    page_count = 0
    while True:
        page_count += 1
        if page.get("total_count") != total_count:
            raise ValueError("IRBANK screening total_count changed during pagination")
        current_attribution = page.get("attribution")
        if not isinstance(current_attribution, dict):
            raise ValueError("IRBANK screening response does not contain attribution")
        if attribution is None:
            attribution = current_attribution
        elif current_attribution != attribution:
            raise ValueError("IRBANK screening attribution changed during pagination")

        securities = page.get("securities")
        if not isinstance(securities, list):
            raise ValueError("IRBANK screening response does not contain securities[]")
        for security in securities:
            returned_rows += 1
            if not isinstance(security, dict):
                raise ValueError("IRBANK screening contains a malformed security")
            code = str(security.get("security_code") or "").strip().upper()
            if not SECURITY_CODE_RE.fullmatch(code):
                raise ValueError("IRBANK screening contains an invalid four-character security_code")
            metrics = security.get("metrics")
            if not isinstance(metrics, list):
                raise ValueError(f"IRBANK screening has no metrics[] for {code}")
            metric = next((row for row in metrics if isinstance(row, dict)
                           and row.get("field") == "dividendYield"), None)
            if not metric or isinstance(metric.get("value"), bool):
                raise ValueError(f"IRBANK screening has no dividendYield for {code}")
            value = metric.get("value")
            if not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
                raise ValueError(f"IRBANK screening has an invalid dividendYield for {code}")
            as_of = str(metric.get("as_of") or "")
            if not YIELD_DATE_RE.fullmatch(as_of):
                raise ValueError(f"IRBANK screening has an invalid dividendYield as_of for {code}")
            identity = (str(security.get("edinet_code") or ""), str(security.get("name") or ""), value, as_of)
            if code in yields:
                if identities[code] != identity:
                    raise ValueError(f"IRBANK screening returned conflicting dividend yields for {code}")
                duplicate_rows += 1
                continue
            identities[code] = identity
            yields[code] = {
                "rate": value,
                "as_of": as_of,
                "source": "IRBANK screening dividendYield",
            }

        print(f"IRBANK screening rows {returned_rows}/{total_count}; unique={len(yields)}", flush=True)
        cursor = page.get("next_cursor")
        if not cursor:
            break
        cursor = str(cursor)
        if cursor in cursors:
            raise ValueError("IRBANK screening repeated a pagination cursor")
        cursors.add(cursor)
        page_params = {**params, "cursor": cursor}
        page = request_json(f"{API_BASE}/screening?{urlencode(page_params)}", token)

    if returned_rows != total_count:
        raise ValueError(f"IRBANK screening returned {returned_rows} rows but total_count is {total_count}")
    if attribution is None:
        raise ValueError("IRBANK screening did not provide attribution")
    return yields, {
        "total_count": total_count,
        "unique_count": len(yields),
        "duplicate_rows": duplicate_rows,
        "page_count": page_count,
        "api_remaining_before_fetch": remaining_before,
        "attribution": attribution,
    }


def merge_yields(document: dict[str, Any], yields: dict[str, dict[str, Any]],
                 screening_meta: dict[str, Any]) -> dict[str, Any]:
    items = document.get("items")
    if not isinstance(items, dict):
        raise ValueError("Issuer dividend index does not contain items{}")
    merged = {str(code).upper(): dict(item) for code, item in items.items() if isinstance(item, dict)}
    for item in merged.values():
        item.pop("yield", None)
        item["yield_status"] = "empty"

    for code, record in yields.items():
        item = merged.get(code)
        if item is None:
            item = {
                "code": code,
                "name": code,
                "actual_status": "not_fetched",
                "actual": None,
                "forecast_status": "not_requested",
                "forecast": None,
            }
            merged[code] = item
        item["yield"] = {**record, "attribution": screening_meta["attribution"]}
        item["yield_status"] = "available"

    result = dict(document)
    result["items"] = {code: merged[code] for code in sorted(merged)}
    result["count"] = len(merged)
    result["yield_generated_at"] = iso_now()
    result["yield_source"] = "IRBANK Connect REST API /screening"
    result["yield_metric"] = "dividendYield"
    result["yield_unit"] = "percent"
    result["yield_attribution"] = screening_meta["attribution"]
    result["yield_coverage"] = {
        "screened_securities": screening_meta["total_count"],
        "available_securities": len(yields),
        "as_of_min": min(row["as_of"] for row in yields.values()) if yields else None,
        "as_of_max": max(row["as_of"] for row in yields.values()) if yields else None,
    }
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Local issuer-dividend index to update in dry-run mode")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--bucket", default="diview-market-data")
    parser.add_argument("--key", default=DEFAULT_R2_KEY)
    parser.add_argument("--limit", type=int, default=100)
    args = parser.parse_args()
    if not 1 <= args.limit <= 100:
        raise ValueError("--limit must be between 1 and 100")
    if not args.publish and not args.input:
        parser.error("--input is required unless --publish is set")

    token = required_env("IRBANK_API_KEY")
    client = make_r2_client(1) if args.publish else None
    if client:
        source_document = read_r2_document(client, args.bucket, args.key)
        if source_document is None:
            raise RuntimeError(f"Issuer dividend index not found in R2: {args.key}")
    else:
        source_document = json.loads(args.input.read_text(encoding="utf-8"))
    if not isinstance(source_document, dict):
        raise ValueError("Issuer dividend index is not a JSON object")

    yields, screening_meta = fetch_screening_yields(token, args.limit)
    updated = merge_yields(source_document, yields, screening_meta)
    payload = json_bytes(updated)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest()
    print(json.dumps({
        "output": str(args.output),
        "index_count": updated["count"],
        "yield_count": len(yields),
        "duplicate_screening_rows": screening_meta["duplicate_rows"],
        "page_count": screening_meta["page_count"],
        "api_remaining_before_fetch": screening_meta["api_remaining_before_fetch"],
        "sha256": digest,
        "bytes": len(payload),
        "yield_generated_at": updated["yield_generated_at"],
    }, ensure_ascii=False))

    if client:
        response = client.put_object(
            Bucket=args.bucket,
            Key=args.key,
            Body=payload,
            ContentType="application/json; charset=utf-8",
            CacheControl="public, max-age=300",
        )
        saved = client.get_object(Bucket=args.bucket, Key=args.key)["Body"].read()
        if hashlib.sha256(saved).hexdigest() != digest:
            raise RuntimeError(f"IRBANK v2 dividend-yield read-back mismatch: {args.key}")
        print(json.dumps({
            "published_key": args.key,
            "etag": str(response.get("ETag") or "").strip('"'),
            "read_back_sha256": digest,
        }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
