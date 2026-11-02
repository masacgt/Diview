"""Read-only comparison of IRBANK and J-Quants universe snapshots.

The report intentionally leaves membership differences unclassified unless the
input snapshots contain evidence that supports a specific explanation.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any


def display_code(value: Any) -> str:
    code = str(value or "").strip().upper()
    if not re.fullmatch(r"[0-9A-Z]{4,5}", code):
        raise ValueError(f"Invalid security code: {value!r}")
    return code[:4] if len(code) == 5 and code.endswith("0") else code


def load_codes(path: Path, source: str) -> tuple[list[str], dict[str, str], dict[str, str], dict[str, dict[str, str]], int]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    if source == "irbank":
        rows = doc.get("securities") if isinstance(doc, dict) else None
        if not isinstance(rows, list):
            raise ValueError("IRBANK input must contain securities[]")
        raw_codes = [str(row.get("code") or row.get("irbank_source_code") or row.get("security_code_display") or "") for row in rows]
        categories: dict[str, str] = {}
        row_details = {display_code(row.get("code") or row.get("security_code_display")): {
            "irbank_market": str(row.get("market") or "unknown"),
            "irbank_industry": str(row.get("industry") or "unknown"),
        } for row in rows}
    else:
        raw_codes = doc.get("security_codes") if isinstance(doc, dict) else None
        rows = (doc.get("data") or doc.get("securities")) if isinstance(doc, dict) else None
        if not isinstance(raw_codes, list):
            if not isinstance(rows, list):
                raise ValueError("J-Quants input must contain security_codes[] or data[]/securities[]")
            raw_codes = [row.get("Code") or row.get("code") or row.get("security_code_display") for row in rows]
        if not isinstance(raw_codes, list):
            raise ValueError("J-Quants input must contain a security code list")
        if not isinstance(rows, list):
            rows = []
        categories = {
            display_code(row.get("Code") or row.get("code") or row.get("security_code_display")): str(row.get("ProdCat") or row.get("jquants_product_category") or "")
            for row in rows if isinstance(row, dict)
            if row.get("Code") or row.get("code") or row.get("security_code_display")
        }
        row_details = {
            display_code(row.get("Code") or row.get("code") or row.get("security_code_display")): {
                "jquants_prodcat": str(row.get("ProdCat") or row.get("jquants_product_category") or "unknown")
            } for row in (doc.get("data") or doc.get("securities") or []) if isinstance(row, dict)
        } if isinstance(doc, dict) else {}
    normalized: list[str] = []
    raw_by_display: dict[str, str] = {}
    duplicates = 0
    for raw in raw_codes:
        code = display_code(raw)
        if code in raw_by_display:
            duplicates += 1
            continue
        normalized.append(code)
        raw_by_display[code] = str(raw).strip().upper()
    if source != "irbank":
        for row in rows:
            if not isinstance(row, dict):
                continue
            raw = row.get("Code") or row.get("code") or row.get("security_code_display")
            if raw:
                raw_by_display[display_code(raw)] = str(raw).strip().upper()
    return normalized, raw_by_display, categories, row_details, duplicates


def build_report(irbank_path: Path, jquants_path: Path) -> dict[str, Any]:
    ir_doc = json.loads(irbank_path.read_text(encoding="utf-8"))
    jq_doc = json.loads(jquants_path.read_text(encoding="utf-8"))
    ir_codes, ir_raw, _, ir_details, ir_duplicates = load_codes(irbank_path, "irbank")
    jq_codes, jq_raw, categories, jq_details, jq_duplicates = load_codes(jquants_path, "jquants")
    ir, jq = set(ir_codes), set(jq_codes)
    category_counts = Counter(categories.values())
    both, ir_only, jq_only = sorted(ir & jq), sorted(ir - jq), sorted(jq - ir)

    def difference(code: str, owner: str, raw: dict[str, str]) -> dict[str, str]:
        prod = categories.get(code, "")
        ir_industry = ir_details.get(code, {}).get("irbank_industry", "")
        if prod in ("013", "014"):
            likely_scope = {"013": "reit", "014": "etf"}[prod]
            scope_source = "jquants_product_category"
        elif ir_industry.strip().casefold() == "etf":
            likely_scope = "etf"
            scope_source = "irbank_industry"
        elif ir_industry.strip().casefold() in ("reit", "j-reit"):
            likely_scope = "reit"
            scope_source = "irbank_industry"
        elif prod in ("011", "012"):
            likely_scope = "equity"
            scope_source = "jquants_product_category"
        else:
            likely_scope = "unknown"
            scope_source = "unavailable"
        return {
            "code": code,
            "present_in": owner,
            "jquants_prodcat": prod or "unknown",
            "likely_product_scope": likely_scope,
            "product_scope_source": scope_source,
            **ir_details.get(code, {}),
            **jq_details.get(code, {}),
            "raw_irbank_code": ir_raw.get(code, ""),
            "raw_jquants_code": jq_raw.get(code, ""),
            "reason": "unclassified_membership_difference",
        }

    ir_date = ir_doc.get("fetched_at") or ir_doc.get("generated_at")
    jq_dates = jq_doc.get("response_dates") or jq_doc.get("generated_at")
    jq_date_set = set(jq_dates) if isinstance(jq_dates, list) else ({str(jq_dates)} if jq_dates else set())
    ir_day = str(ir_date or "")[:10]
    aligned = bool(ir_day and jq_date_set == {ir_day})
    ir_retrieved_at = ir_doc.get("retrieved_at") or ir_doc.get("generated_at") or ir_doc.get("fetched_at")
    jq_retrieved_at = jq_doc.get("retrieved_at") or jq_doc.get("generated_at")
    ir_retrieved_day = str(ir_retrieved_at or "")[:10]
    jq_retrieved_day = str(jq_retrieved_at or "")[:10]
    return {
        "read_only": True,
        "inputs": {"irbank": str(irbank_path), "jquants": str(jquants_path)},
        "snapshot_dates": {"irbank": ir_date, "jquants": jq_dates},
        "retrieval_timestamps": {"irbank": ir_retrieved_at, "jquants": jq_retrieved_at},
        "retrieved_same_day": bool(ir_retrieved_day and jq_retrieved_day and ir_retrieved_day == jq_retrieved_day),
        "date_alignment": "aligned" if aligned else "mismatch_or_unknown",
        "comparison_is_temporally_comparable": aligned,
        "date_alignment_warning": None if aligned else "The source snapshots have different or unverifiable effective dates; membership differences cannot be classified as listing/delisting from this comparison alone.",
        "counts": {
            "irbank_rows": len(ir_codes) + ir_duplicates, "irbank_unique": len(ir), "irbank_duplicates": ir_duplicates,
            "jquants_rows": len(jq_codes) + jq_duplicates, "jquants_unique": len(jq), "jquants_duplicates": jq_duplicates,
            "intersection": len(both), "irbank_only": len(ir_only), "jquants_only": len(jq_only),
        },
        "normalized_code_union_count": len(ir | jq),
        "intersection_codes": both,
        "normalized_code_sha256": {
            "irbank": hashlib.sha256("\n".join(sorted(ir)).encode("utf-8")).hexdigest(),
            "jquants": hashlib.sha256("\n".join(sorted(jq)).encode("utf-8")).hexdigest(),
        },
        "jquants_product_categories": dict(sorted(category_counts.items())),
        "normalization_examples": [
            {"source": source, "raw": raw, "display": code}
            for source, mapping in (("IRBANK", ir_raw), ("J-Quants", jq_raw))
            for code, raw in sorted(mapping.items()) if raw != code
        ],
        "irbank_only": [difference(code, "IRBANK", ir_raw) for code in ir_only],
        "jquants_only": [difference(code, "J-Quants", jq_raw) for code in jq_only],
        "shared_etf_reit_codes": [
            {"code": code, "prodcat": categories[code], "type": {"013": "reit", "014": "etf"}[categories[code]]}
            for code in both if categories.get(code) in ("013", "014")
        ],
        "classification_note": "Membership differences are not inferred to be listings, delistings, or product-scope differences without dated exchange/listing evidence. Supply row-level J-Quants input to verify ETF/REIT categories.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--irbank", type=Path, required=True, help="IRBANK /securities snapshot JSON")
    parser.add_argument("--jquants", type=Path, required=True, help="J-Quants master snapshot JSON")
    parser.add_argument("--output", type=Path, required=True, help="Output JSON report path")
    args = parser.parse_args()
    report = build_report(args.irbank, args.jquants)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["counts"], ensure_ascii=False))
    print(f"Wrote read-only comparison: {args.output}")


if __name__ == "__main__":
    main()
