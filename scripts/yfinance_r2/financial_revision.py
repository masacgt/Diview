"""Publish the shared R2 revision token used to invalidate financial API caches."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4


REVISION_KEY = "market/yfinance/v2/financial_revision.json"


def bump_financial_revision(client: Any, bucket: str) -> str:
    revision = uuid4().hex
    document = {
        "schema_version": 1,
        "revision": revision,
        "updated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    }
    client.put_object(
        Bucket=bucket,
        Key=REVISION_KEY,
        Body=json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8"),
        ContentType="application/json; charset=utf-8",
        CacheControl="no-store",
    )
    saved = client.get_object(Bucket=bucket, Key=REVISION_KEY)["Body"].read()
    if json.loads(saved).get("revision") != revision:
        raise RuntimeError("Financial revision read-back mismatch")
    return revision
