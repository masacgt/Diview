"""Shared R2 contract for the weekly IRBANK listed-security universe."""

from __future__ import annotations

import hashlib
import re
from typing import Any


IRBANK_UNIVERSE_R2_KEY = "market/yfinance/v2/universe/irbank_source.json"
IRBANK_UNIVERSE_SOURCE = "IRBANK Connect API v1 /securities"
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


def normalize_securities(rows: Any) -> list[dict[str, str]]:
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
    if len(normalized) < MIN_SECURITY_COUNT:
        raise ValueError(f"IRBANK securities list returned only {len(normalized)} valid issues")
    return [
        {"security_code_display": code, "irbank_source_code": normalized[code]}
        for code in sorted(normalized)
    ]


def build_snapshot(securities: list[dict[str, str]], fetched_at: str) -> dict[str, Any]:
    codes = [row["security_code_display"] for row in securities]
    return {
        "schema_version": 1,
        "source": IRBANK_UNIVERSE_SOURCE,
        "complete": True,
        "fetched_at": fetched_at,
        "security_count": len(codes),
        "security_code_sha256": code_sha256(codes),
        "securities": securities,
    }


def validate_snapshot(snapshot: Any, *, minimum_security_count: int = MIN_SECURITY_COUNT) -> list[dict[str, str]]:
    if not isinstance(snapshot, dict):
        raise ValueError("IRBANK universe snapshot is missing or is not an object")
    if snapshot.get("schema_version") != 1 or snapshot.get("source") != IRBANK_UNIVERSE_SOURCE:
        raise ValueError("IRBANK universe snapshot has an unsupported schema or source")
    if snapshot.get("complete") is not True:
        raise ValueError("IRBANK universe snapshot is incomplete")
    securities = snapshot.get("securities")
    if not isinstance(securities, list):
        raise ValueError("IRBANK universe snapshot does not contain securities[]")
    codes: list[str] = []
    normalized_rows: list[dict[str, str]] = []
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
        normalized_rows.append({"security_code_display": code, "irbank_source_code": source_code})
    if len(codes) < minimum_security_count or len(codes) != len(set(codes)):
        raise ValueError("IRBANK universe snapshot is too small or contains duplicate codes")
    if codes != sorted(codes):
        raise ValueError("IRBANK universe snapshot codes are not sorted")
    if snapshot.get("security_count") != len(codes):
        raise ValueError("IRBANK universe snapshot count does not match securities[]")
    if snapshot.get("security_code_sha256") != code_sha256(codes):
        raise ValueError("IRBANK universe snapshot code hash does not match securities[]")
    fetched_at = str(snapshot.get("fetched_at") or "").strip()
    if not fetched_at:
        raise ValueError("IRBANK universe snapshot has no fetched_at timestamp")
    return normalized_rows
