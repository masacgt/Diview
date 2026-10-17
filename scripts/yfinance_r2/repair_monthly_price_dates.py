"""Normalize stored December close dates to the final XTKS session without Yahoo calls."""

from __future__ import annotations

import json
import os
import re
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from typing import Any

import boto3
from botocore.config import Config

from monthly_price_calendar import normalize_december_price_point


MONTHLY_KEY = re.compile(
    r"^market/yfinance/v2/(?:securities/[^/]+/prices/monthly/|archive/prices/monthly/[^/]+/)\d{4}\.json$"
)
ROOT = "market/yfinance/v2"


def normalize_points(document: dict[str, Any]) -> tuple[dict[str, Any], int]:
    """Return a copy with each December point labeled by its final XTKS session."""
    points = document.get("points")
    if not isinstance(points, list):
        raise ValueError("monthly price document must contain a points array")

    normalized = dict(document)
    normalized_points: list[Any] = []
    changed = 0
    for point in points:
        if not isinstance(point, dict):
            normalized_points.append(point)
            continue
        month = str(point.get("month") or "")
        if not re.fullmatch(r"\d{4}-12", month):
            normalized_points.append(point)
            continue
        normalized_point, point_changed = normalize_december_price_point(point)
        normalized_points.append(normalized_point)
        changed += int(point_changed)

    normalized["points"] = normalized_points
    return normalized, changed


def repair_object(client: Any, bucket: str, key: str, updated_at: str) -> tuple[int, int]:
    response = client.get_object(Bucket=bucket, Key=key)
    document = json.loads(response["Body"].read())
    if not isinstance(document, dict):
        raise ValueError(f"monthly price object is not a JSON object: {key}")
    repaired, changed_points = normalize_points(document)
    if changed_points == 0:
        return 0, 0

    repaired["updated_at"] = updated_at
    payload = json.dumps(repaired, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    client.put_object(Bucket=bucket, Key=key, Body=payload, ContentType="application/json")

    saved_response = client.get_object(Bucket=bucket, Key=key)
    saved = json.loads(saved_response["Body"].read())
    if saved.get("points") != repaired["points"]:
        raise RuntimeError(f"monthly price date read-back failed: {key}")
    return 1, changed_points


def run(max_workers: int = 16) -> int:
    account_id = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "").strip()
    access_key = os.environ.get("R2_ACCESS_KEY_ID", "").strip()
    secret_key = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip()
    bucket = os.environ.get("R2_BUCKET", "").strip()
    if not all((account_id, access_key, secret_key, bucket)):
        raise RuntimeError("CLOUDFLARE_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, and R2_BUCKET are required")
    client = boto3.client(
        "s3",
        endpoint_url=f"https://{account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name="auto",
        config=Config(
            signature_version="s3v4",
            retries={"max_attempts": 4, "mode": "standard"},
            max_pool_connections=max_workers,
        ),
    )
    keys = sorted(
        str(item["Key"])
        for page in client.get_paginator("list_objects_v2").paginate(
            Bucket=bucket, Prefix=f"{ROOT}/",
        )
        for item in page.get("Contents", [])
        if item.get("Key") and MONTHLY_KEY.fullmatch(str(item["Key"]))
    )
    updated_at = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    print(f"Found {len(keys)} monthly-price year partitions; no yfinance requests will be made.", flush=True)

    changed_objects = 0
    changed_points = 0
    failures: list[tuple[str, str]] = []
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {pool.submit(repair_object, client, bucket, key, updated_at): key for key in keys}
        for future in as_completed(futures):
            key = futures[future]
            try:
                object_count, point_count = future.result()
                changed_objects += object_count
                changed_points += point_count
            except Exception as exc:
                failures.append((key, f"{type(exc).__name__}: {exc}"[:500]))

    print(json.dumps({
        "partitions_scanned": len(keys),
        "partitions_updated": changed_objects,
        "points_normalized": changed_points,
        "failures": len(failures),
        "failure_examples": failures[:20],
        "yfinance_requests": 0,
    }, ensure_ascii=False, sort_keys=True), flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        workers = max(1, min(64, int(sys.argv[1]) if len(sys.argv) > 1 else 16))
        raise SystemExit(run(workers))
    except Exception as exc:
        print(f"FATAL: {type(exc).__name__}: {exc}", file=sys.stderr)
        raise SystemExit(1)
