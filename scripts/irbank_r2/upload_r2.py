#!/usr/bin/env python3
"""Upload a prepared IRBANK dataset to the private Cloudflare R2 bucket."""

from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT = Path.home() / "AppData" / "Local" / "Temp" / "diview-irbank-r2-ready"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_OUTPUT, help="Prepared output folder")
    parser.add_argument("--bucket", default="diview-market-data")
    parser.add_argument("--account-id", default=os.environ.get("CLOUDFLARE_ACCOUNT_ID", "8d8f4f1f3c53232aaf7c7d6dd7b33944"))
    parser.add_argument("--prefix", default="", help="Optional additional key prefix")
    parser.add_argument("--upload", action="store_true", help="Perform uploads; without this flag, only show the planned upload")
    parser.add_argument("--allow-jpx-data", action="store_true", help="Confirm authorization for JPX-derived daily prices and margin data")
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()

    source = args.source.resolve()
    manifest_path = source / "market/irbank/v1/manifest.json"
    if not manifest_path.is_file():
        parser.error(f"prepared manifest not found: {manifest_path}")
    manifest: dict[str, Any] = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("jpx_data_included") and not args.allow_jpx_data:
        parser.error("dataset contains JPX-derived data; pass --allow-jpx-data only after confirming app storage/use is authorized")

    base_keys = [str(row["key"]) for row in manifest.get("objects", [])]
    keys = base_keys + ["market/irbank/v1/manifest.json"]
    keys = list(dict.fromkeys(keys))
    prefix = args.prefix.strip("/")
    if prefix:
        keys = [f"{prefix}/{key}" for key in keys]
    uploads: list[tuple[str, Path]] = []
    for key in keys:
        relative_key = key[len(prefix) + 1:] if prefix else key
        local_path = source / Path(*relative_key.split("/"))
        if not local_path.is_file():
            raise FileNotFoundError(f"prepared object listed in manifest is missing: {local_path}")
        uploads.append((key, local_path))

    total_bytes = sum(path.stat().st_size for _, path in uploads)
    print(json.dumps({
        "bucket": args.bucket,
        "objects": len(uploads),
        "bytes": total_bytes,
        "jpx_data_included": bool(manifest.get("jpx_data_included")),
        "mode": "upload" if args.upload else "dry_run",
        "prefix": prefix,
    }, ensure_ascii=False, indent=2))
    if not args.upload:
        print("Dry run only. Add --upload to write these objects to R2.")
        return 0

    try:
        import boto3
    except ImportError as exc:
        raise RuntimeError("boto3 is required; install scripts/irbank_r2/requirements.txt") from exc

    access_key_id = os.environ.get("R2_ACCESS_KEY_ID", "").strip() or getpass.getpass("R2_ACCESS_KEY_ID (入力は非表示): ").strip()
    secret_access_key = os.environ.get("R2_SECRET_ACCESS_KEY", "").strip() or getpass.getpass("R2_SECRET_ACCESS_KEY (入力は非表示): ").strip()
    if not access_key_id or not secret_access_key:
        raise RuntimeError("both R2 credentials are required")

    client = boto3.client(
        "s3",
        endpoint_url=f"https://{args.account_id}.r2.cloudflarestorage.com",
        aws_access_key_id=access_key_id,
        aws_secret_access_key=secret_access_key,
        region_name="auto",
    )

    # Publish data objects first, then the discoverability files, and manifest last.
    def order(item: tuple[str, Path]) -> tuple[int, str]:
        key = item[0]
        if key.endswith("/manifest.json"):
            return (2, key)
        if key.endswith("/catalog.json") or key.endswith("/index.json"):
            return (1, key)
        return (0, key)

    ordered = sorted(uploads, key=order)

    def put(item: tuple[str, Path]) -> tuple[str, int]:
        key, path = item
        with path.open("rb") as body:
            client.put_object(
                Bucket=args.bucket,
                Key=key,
                Body=body,
                ContentLength=path.stat().st_size,
                ContentType="application/json; charset=utf-8",
                CacheControl="public, max-age=300",
            )
        head = client.head_object(Bucket=args.bucket, Key=key)
        size = path.stat().st_size
        if head.get("ContentLength") != size:
            raise IOError(f"R2 size verification failed for {key}")
        return key, size

    completed = 0
    worker_count = max(1, min(args.workers, 16))
    for group_priority in (0, 1, 2):
        group = [item for item in ordered if order(item)[0] == group_priority]
        with ThreadPoolExecutor(max_workers=worker_count) as pool:
            for future in as_completed([pool.submit(put, item) for item in group]):
                key, size = future.result()
                completed += 1
                if completed % 100 == 0 or completed == len(ordered):
                    print(f"uploaded {completed}/{len(ordered)}; last={key}; bytes={size}", flush=True)
    print(f"Upload complete: {completed} objects to {args.bucket}.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"upload failed: {exc}", file=sys.stderr)
        raise
