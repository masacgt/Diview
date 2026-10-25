"""Create the active sharded view of the current IRBANK dividend index."""

from __future__ import annotations

import argparse
import json

import refresh_v2_dividends as refresh


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bucket", default="diview-market-data")
    args = parser.parse_args()

    client = refresh.make_r2_client(1)
    document = refresh.read_r2_document(client, args.bucket, refresh.DEFAULT_R2_KEY)
    if not isinstance(document, dict) or not isinstance(document.get("items"), dict):
        raise RuntimeError(f"Issuer dividend index is missing or invalid: {refresh.DEFAULT_R2_KEY}")

    manifest = refresh.prepare_dividend_index_shards(client, args.bucket, document)
    manifest_digest = refresh.activate_dividend_index_shards(client, args.bucket, manifest)
    print(json.dumps({
        "source_key": refresh.DEFAULT_R2_KEY,
        "slot": manifest["slot"],
        "generation": manifest.get("generated_at"),
        "item_count": manifest["item_count"],
        "prefix_count": manifest["shard_count"],
        "shards_sha256": manifest["shards_sha256"],
        "manifest_sha256": manifest_digest,
        "financial_revision_changed": False,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
