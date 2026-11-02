"""Shared R2 contract for the weekly IRBANK + JPX ETF universe."""

from __future__ import annotations

import hashlib
import re
from typing import Any


IRBANK_UNIVERSE_R2_KEY = "market/yfinance/v2/universe/irbank_source.json"
IRBANK_UNIVERSE_SOURCE = "IRBANK Connect API v1 /securities"
JPX_ETF_SOURCE = "JPX Listed Issues - ETFs"
JPX_ETF_SOURCE_URL = "https://www.jpx.co.jp/english/equities/products/etfs/issues/01.html"
SECURITY_CODE_RE = re.compile(r"^[A-Z0-9]{4,5}$")
MIN_SECURITY_COUNT = 3500


def display_code(raw_code: Any) -> str | None:
    code = str(raw_code or "").strip().upper()
    if not SECURITY_CODE_RE.fullmatch(code):
        return None
    # IRBANK exposes ordinary TSE codes as five characters with a trailing
    # zero, including newer alphanumeric codes such as 130A0. Keep class-share
    # codes such as 92025 intact.
    if len(code) == 5 and code.endswith("0"):
        return code[:4]
    return code


def code_sha256(codes: list[str]) -> str:
    return hashlib.sha256("\n".join(codes).encode("utf-8")).hexdigest()


def normalize_securities(rows: Any, *, minimum_security_count: int = MIN_SECURITY_COUNT) -> list[dict[str, Any]]:
    if not isinstance(rows, list):
        raise ValueError("IRBANK securities response does not contain securities[]")
    normalized: dict[str, str] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("IRBANK securities response contains a non-object security row")
        raw_code = str(row.get("code") or "").strip().upper()
        if not raw_code:
            raise ValueError("IRBANK securities response contains a row without code")
        code = display_code(raw_code)
        if code is None:
            raise ValueError(f"IRBANK securities response contains an invalid code: {raw_code}")
        if code in normalized:
            raise ValueError(f"Duplicate IRBANK code after display normalization: {code}")
        normalized[code] = raw_code
    if len(normalized) < minimum_security_count:
        raise ValueError(f"IRBANK securities list returned only {len(normalized)} valid issues")
    return [
        {
            "security_code_display": code,
            "irbank_source_code": normalized[code],
            "sources": [IRBANK_UNIVERSE_SOURCE],
        }
        for code in sorted(normalized)
    ]


def merge_jpx_etfs(
    irbank_securities: list[dict[str, Any]],
    jpx_etfs: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Union IRBANK listings with JPX ETFs and attach official product metadata."""
    merged = {row["security_code_display"]: dict(row) for row in irbank_securities}
    for etf in jpx_etfs:
        code = display_code(etf.get("security_code_display"))
        if code is None or code != etf.get("security_code_display"):
            raise ValueError(f"JPX ETF list contains an invalid display code: {etf.get('security_code_display')!r}")
        row = merged.setdefault(code, {"security_code_display": code})
        row["security_type"] = "etf"
        row["jquants_product_category"] = "014"
        row["sources"] = sorted(set(row.get("sources", [])) | {JPX_ETF_SOURCE})
        row["jpx_listing_date"] = str(etf.get("listing_date") or "")
        row["jpx_source_url"] = JPX_ETF_SOURCE_URL
    return [merged[code] for code in sorted(merged)]


def build_snapshot(
    securities: list[dict[str, Any]],
    fetched_at: str,
    *,
    jpx_etf_count: int = 0,
) -> dict[str, Any]:
    codes = [row["security_code_display"] for row in securities]
    return {
        "schema_version": 2,
        "source": IRBANK_UNIVERSE_SOURCE,
        "complete": True,
        "fetched_at": fetched_at,
        "supplemental_sources": [{
            "source": JPX_ETF_SOURCE,
            "url": JPX_ETF_SOURCE_URL,
            "fetched_at": fetched_at,
            "listed_etf_count": jpx_etf_count,
        }],
        "security_count": len(codes),
        "security_code_sha256": code_sha256(codes),
        "securities": securities,
    }


def validate_snapshot(
    snapshot: Any,
    *,
    minimum_security_count: int = MIN_SECURITY_COUNT,
    minimum_jpx_etf_count: int = 350,
    allow_legacy: bool = False,
) -> list[dict[str, Any]]:
    if not isinstance(snapshot, dict):
        raise ValueError("IRBANK universe snapshot is missing or is not an object")
    schema_version = snapshot.get("schema_version")
    if schema_version not in ({1, 2} if allow_legacy else {2}) or snapshot.get("source") != IRBANK_UNIVERSE_SOURCE:
        raise ValueError("IRBANK universe snapshot has an unsupported schema or source")
    if schema_version == 2:
        supplemental = snapshot.get("supplemental_sources")
        if not isinstance(supplemental, list) or not any(
            isinstance(source, dict)
            and source.get("source") == JPX_ETF_SOURCE
            and source.get("url") == JPX_ETF_SOURCE_URL
            and isinstance(source.get("listed_etf_count"), int)
            and source["listed_etf_count"] >= minimum_jpx_etf_count
            for source in supplemental
        ):
            raise ValueError("Universe snapshot is missing a complete JPX ETF source record")
    if snapshot.get("complete") is not True:
        raise ValueError("IRBANK universe snapshot is incomplete")
    securities = snapshot.get("securities")
    if not isinstance(securities, list):
        raise ValueError("IRBANK universe snapshot does not contain securities[]")
    codes: list[str] = []
    normalized_rows: list[dict[str, Any]] = []
    for row in securities:
        if not isinstance(row, dict):
            raise ValueError("IRBANK universe snapshot contains an invalid security row")
        code = display_code(row.get("security_code_display"))
        if code is None or code != row.get("security_code_display"):
            raise ValueError("IRBANK universe snapshot contains an invalid display code")
        codes.append(code)
        source_code = str(row.get("irbank_source_code") or code).strip().upper()
        if not SECURITY_CODE_RE.fullmatch(source_code):
            raise ValueError(f"IRBANK universe snapshot contains an invalid source code: {code}")
        if display_code(source_code) != code:
            raise ValueError(f"IRBANK source code does not match its display code: {code}")
        normalized = {"security_code_display": code, "irbank_source_code": source_code}
        security_type = row.get("security_type")
        product_category = row.get("jquants_product_category")
        if security_type is not None:
            if security_type not in {"listed_security", "etf", "reit"}:
                raise ValueError(f"Universe snapshot contains an unsupported security type: {code}")
            normalized["security_type"] = security_type
        if product_category is not None:
            if product_category not in {"011", "012", "013", "014", "021", "023"}:
                raise ValueError(f"Universe snapshot contains an unsupported product category: {code}")
            normalized["jquants_product_category"] = product_category
        sources = row.get("sources")
        if sources is not None:
            if not isinstance(sources, list) or not all(isinstance(source, str) and source for source in sources):
                raise ValueError(f"Universe snapshot contains invalid source metadata: {code}")
            normalized["sources"] = sorted(set(sources))
        listing_date = row.get("jpx_listing_date")
        if listing_date is not None:
            if not isinstance(listing_date, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", listing_date):
                raise ValueError(f"Universe snapshot contains invalid JPX listing date: {code}")
            normalized["jpx_listing_date"] = listing_date
        source_url = row.get("jpx_source_url")
        if source_url is not None:
            if source_url != JPX_ETF_SOURCE_URL:
                raise ValueError(f"Universe snapshot contains an unknown JPX source URL: {code}")
            normalized["jpx_source_url"] = source_url
        if product_category == "014" and security_type != "etf":
            raise ValueError(f"ETF category is missing ETF security type: {code}")
        normalized_rows.append(normalized)
    if len(codes) < minimum_security_count or len(codes) != len(set(codes)):
        raise ValueError("IRBANK universe snapshot is too small or contains duplicate codes")
    if codes != sorted(codes):
        raise ValueError("IRBANK universe snapshot codes are not sorted")
    if snapshot.get("security_count") != len(codes):
        raise ValueError("IRBANK universe snapshot count does not match securities[]")
    if snapshot.get("security_code_sha256") != code_sha256(codes):
        raise ValueError("IRBANK universe snapshot code hash does not match securities[]")
    if schema_version == 2:
        jpx_source = next(source for source in snapshot["supplemental_sources"] if source.get("source") == JPX_ETF_SOURCE)
        tagged_etfs = [row for row in normalized_rows if JPX_ETF_SOURCE in row.get("sources", [])]
        if len(tagged_etfs) != jpx_source["listed_etf_count"]:
            raise ValueError("JPX ETF count does not match the source-tagged securities")
        if any(
            row.get("security_type") != "etf"
            or row.get("jquants_product_category") != "014"
            or not row.get("jpx_listing_date")
            or row.get("jpx_source_url") != JPX_ETF_SOURCE_URL
            for row in tagged_etfs
        ):
            raise ValueError("JPX ETF source metadata is incomplete")
    fetched_at = str(snapshot.get("fetched_at") or "").strip()
    if not fetched_at:
        raise ValueError("IRBANK universe snapshot has no fetched_at timestamp")
    return normalized_rows
