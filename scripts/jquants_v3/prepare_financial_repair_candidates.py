#!/usr/bin/env python3
"""Make a private, review-only J-Quants annual repair candidate file.

This tool never updates R2, the Worker, or app data. Its output contains
licensed data and must remain in an authorized private location.
"""
from __future__ import annotations

import argparse
from datetime import date
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import re
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ENDPOINT = "https://api.jquants.com/v2/fins/summary"
FIELDS = {"Sales": "sales", "OP": "operating_profit", "TA": "total_assets",
          "CFO": "operating_cf", "CFI": "investing_cf", "EqAR": "equity_ratio_computed"}


def extract_candidates(code: str, rows: list[dict]) -> list[dict]:
    if not re.fullmatch(r"[A-Z0-9]{4}", code):
        raise ValueError("expected a four-character security code")
    out = []
    for row in rows:
        if row.get("Code") != code + "0" or row.get("CurPerType") != "FY":
            continue
        if row.get("DocType") != "FYFinancialStatements_Consolidated_JP":
            continue
        end = row.get("CurPerEn")
        if not isinstance(end, str) or end != row.get("CurFYEn"):
            continue
        try:
            if date.fromisoformat(end).isoformat() != end:
                continue
        except ValueError:
            continue
        values = {}
        for source, target in FIELDS.items():
            raw = row.get(source)
            if raw in (None, ""):
                continue
            try:
                value = Decimal(str(raw))
            except InvalidOperation as exc:
                raise ValueError(f"invalid {source} for {code}") from exc
            if not value.is_finite():
                raise ValueError(f"non-finite {source} for {code}")
            if source == "EqAR":
                value *= 100
            values[target] = str(value)
        out.append({"code": code, "period_end": end, "disclosed_date": row.get("DiscDate"),
                    "disclosure_number": row.get("DiscNo"), "document_type": row.get("DocType"),
                    "source": "J-Quants API /fins/summary", "values": values,
                    "field_units": {k: "percent" if k == "equity_ratio_computed" else "jpy" for k in values}})
    return sorted(out, key=lambda item: (item["period_end"], item["disclosed_date"] or "", item["disclosure_number"] or ""))


def fetch(code: str, key: str) -> list[dict]:
    rows = []
    token = None
    seen = set()
    while True:
        params = {"code": code}
        if token:
            params["pagination_key"] = token
        req = Request(f"{ENDPOINT}?{urlencode(params)}", headers={"x-api-key": key})
        with urlopen(req, timeout=30) as response:
            payload = json.load(response)
        if not isinstance(payload.get("data"), list):
            raise ValueError("missing J-Quants data array")
        rows.extend(payload["data"])
        token = payload.get("pagination_key")
        if not token:
            return rows
        if token in seen:
            raise ValueError("repeated J-Quants pagination key")
        seen.add(token)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codes", nargs="+", required=True)
    parser.add_argument("--output", type=Path, required=True, help="New private JSON file; existing files are not replaced")
    args = parser.parse_args()
    key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not key:
        parser.error("JQUANTS_API_KEY is required")
    candidates = []
    for code in args.codes:
        candidates.extend(extract_candidates(code, fetch(code, key)))
    if not candidates:
        raise ValueError("no eligible consolidated annual disclosures")
    with args.output.open("x", encoding="utf-8", newline="\n") as output:
        json.dump({"schema_version": 1, "review_only": True, "candidates": candidates}, output,
                  ensure_ascii=False, indent=2)
        output.write("\n")
    print(f"Prepared {len(candidates)} private review candidates; no production changes.")


if __name__ == "__main__":
    main()
