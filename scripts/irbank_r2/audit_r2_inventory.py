#!/usr/bin/env python3
"""Read-only inventory of every object in one Cloudflare R2 bucket."""

from __future__ import annotations

import argparse
import csv
import getpass
import json
import os
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_ACCOUNT_ID = "8d8f4f1f3c53232aaf7c7d6dd7b33944"
DEFAULT_BUCKET = "diview-market-data"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--account-id", default=os.environ.get("CLOUDFLARE_ACCOUNT_ID", DEFAULT_ACCOUNT_ID))
    parser.add_argument("--bucket", default=DEFAULT_BUCKET)
    parser.add_argument("--output-dir", type=Path, default=Path(os.environ.get("TEMP", ".")) / "diview-r2-inventory")
    args = parser.parse_args()

    import boto3

    access_key = os.environ.get("R2_ACCESS_KEY_ID", "").strip() or getpass.getpass("R2_ACCESS_KEY_ID (入力は非表示): ").strip()
    secret_key = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip() or getpass.getpass("R2_SECRET_ACCESS_KEY (入力は非表示): ").strip()
    if not access_key or not secret_key:
        parser.error("both R2 credentials are required")

    client = boto3.client(
        "s3",
        endpoint_url=f"https://{args.account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name="auto",
    )

    objects: list[dict[str, Any]] = []
    paginator = client.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=args.bucket):
        for item in page.get("Contents", []):
            objects.append({
                "key": item["Key"],
                "size_bytes": int(item.get("Size", 0)),
                "last_modified": item.get("LastModified").astimezone(timezone.utc).isoformat()
                if item.get("LastModified") else "",
                "storage_class": item.get("StorageClass", "STANDARD"),
            })

    args.output_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    csv_path = args.output_dir / f"r2_inventory_{stamp}.csv"
    summary_path = args.output_dir / f"r2_inventory_{stamp}_summary.json"

    objects.sort(key=lambda row: row["key"])
    with csv_path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["key", "size_bytes", "last_modified", "storage_class"])
        writer.writeheader()
        writer.writerows(objects)

    prefixes: dict[str, dict[str, int]] = defaultdict(lambda: {"objects": 0, "bytes": 0})
    for row in objects:
        components = row["key"].split("/")
        prefix = "/".join(components[:2]) + "/" if len(components) > 1 else "(bucket root)"
        prefixes[prefix]["objects"] += 1
        prefixes[prefix]["bytes"] += row["size_bytes"]

    summary = {
        "bucket": args.bucket,
        "account_id": args.account_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "object_count": len(objects),
        "total_bytes": sum(row["size_bytes"] for row in objects),
        "prefixes": dict(sorted(prefixes.items())),
        "inventory_csv": str(csv_path),
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
