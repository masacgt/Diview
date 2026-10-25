#!/usr/bin/env python3
"""Prepare IRBANK Connect snapshots as app-oriented, per-security R2 objects."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = ROOT / "irbankMCP" / "irbank"
DEFAULT_OUTPUT = Path.home() / "AppData" / "Local" / "Temp" / "diview-irbank-r2-ready"
FAMILIES = ("organization", "dividends", "valuation", "shareholders", "segments", "disclosures")
FINANCIAL_PERIODS = ("annual", "quarterly", "semiannual")


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> tuple[int, str]:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    path.write_bytes(payload)
    return len(payload), hashlib.sha256(payload).hexdigest()


def attribution(document: Any) -> Any:
    return document.get("attribution") if isinstance(document, dict) else None


def unit_metadata(document: Any, *, prefix: str = "") -> dict[str, str]:
    if not isinstance(document, dict) or not isinstance(document.get("fields"), list):
        return {}
    units: dict[str, str] = {}
    for field in document["fields"]:
        if not isinstance(field, dict):
            continue
        key = str(field.get("key") or field.get("field") or "").strip()
        unit = str(field.get("unit") or "").strip()
        if prefix:
            if not key.startswith(prefix):
                continue
            key = key[len(prefix):]
        if key and "." not in key and "[]" not in key and unit:
            units[key] = unit
    return units


def attach_field_units(rows: Any, document: Any, *, prefix: str = "") -> Any:
    units = unit_metadata(document, prefix=prefix)
    if not units or not isinstance(rows, list):
        return rows
    result = []
    for row in rows:
        if not isinstance(row, dict):
            result.append(row)
            continue
        existing = row.get("field_units") if isinstance(row.get("field_units"), dict) else {}
        result.append({**row, "field_units": {**units, **existing}})
    return result


def attach_object_field_units(value: Any, document: Any, *, prefix: str) -> Any:
    units = unit_metadata(document, prefix=prefix)
    if not units or not isinstance(value, dict):
        return value
    existing = value.get("field_units") if isinstance(value.get("field_units"), dict) else {}
    return {**value, "field_units": {**units, **existing}}


def attach_nested_field_units(rows: Any, document: Any, *, prefix: str, child_key: str) -> Any:
    units = unit_metadata(document, prefix=prefix)
    if not units or not isinstance(rows, list):
        return rows
    result = []
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get(child_key), dict):
            result.append(row)
            continue
        nested = row[child_key]
        existing = nested.get("field_units") if isinstance(nested.get("field_units"), dict) else {}
        result.append({**row, child_key: {**nested, "field_units": {**units, **existing}}})
    return result


def security_display_code(row: dict[str, Any]) -> str:
    display = str(row.get("security_code_display") or "").strip()
    canonical = str(row.get("security_code") or "").strip()
    code = display or canonical
    if len(code) == 5 and code[-1] == "0" and re.fullmatch(r"[A-Za-z0-9]{4}", code[:4]):
        code = code[:-1]
    code = code.upper()
    if not re.fullmatch(r"[A-Z0-9]{4}", code):
        raise ValueError(f"invalid security code in master: {row!r}")
    return code


def load_by_edinet(folder: Path) -> dict[str, Any]:
    result: dict[str, Any] = {}
    if not folder.exists():
        return result
    for path in folder.glob("*.json"):
        if path.name == "acquisition_status.json":
            continue
        doc = read_json(path)
        edinet = str(doc.get("edinet_code") or path.stem)
        result[edinet] = doc
    return result


def financial_periods(source: Path) -> tuple[dict[str, dict[str, Any]], dict[str, Any]]:
    by_edinet: dict[str, dict[str, Any]] = defaultdict(dict)
    source_attribution: dict[str, Any] = {}
    for period_type in FINANCIAL_PERIODS:
        folder = source / "financials" / period_type
        for path in folder.glob("batch_*.json"):
            doc = read_json(path)
            if attribution(doc):
                source_attribution[period_type] = attribution(doc)
            for result in doc.get("results", []):
                edinet = str(result.get("edinet_code") or "")
                if edinet:
                    by_edinet[edinet][period_type] = attach_field_units(result.get("periods", []), doc)
    return by_edinet, source_attribution


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--include-jpx",
        action="store_true",
        help="Include JPX-derived daily prices and weekly margin history; use only when app storage/use is authorized.",
    )
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    if not (source / "securities.json").is_file():
        parser.error(f"IRBANK snapshot not found: {source}")
    if output == source or source in output.parents:
        parser.error("output must be outside the source snapshot")
    output.mkdir(parents=True, exist_ok=True)

    master = read_json(source / "securities.json")
    if not isinstance(master, list):
        raise ValueError("securities.json must contain a JSON array")
    by_edinet = {str(row.get("edinet_code")): row for row in master if row.get("edinet_code")}
    category_docs = {name: load_by_edinet(source / name) for name in FAMILIES}
    financial_data, financial_attributions = financial_periods(source)
    margin_docs = load_by_edinet(source / "weekly_margin") if args.include_jpx else {}
    jsf_margin_docs = load_by_edinet(source / "jsf_margin")
    price_docs = load_by_edinet(source / "prices") if args.include_jpx else {}
    app_required_docs: dict[str, Any] = {}
    app_snapshot = read_json(source / "app_required_7203_20261008.json") if (source / "app_required_7203_20261008.json").is_file() else {}
    for path in source.glob("app_required_*.json"):
        document = read_json(path)
        code = str(document.get("security_code") or "")
        if code:
            app_required_docs.setdefault(code, {})
            for family in ("weekly_margin", "jsf_margin", "disclosures"):
                if family in document:
                    app_required_docs[code][family] = document[family]
        canonical_default_code = next((str(row.get("security_code")) for row in master if row.get("edinet_code") == "E02144"), "")
        security_code = str(document.get("security_code") or canonical_default_code)
        if security_code:
            target = app_required_docs.setdefault(security_code, {})
            for family in ("organization", "dividends", "valuation", "shareholders", "segments"):
                if family in document:
                    target[family] = document[family]
            if isinstance(document.get("basic"), dict) and "edinet_code" in document["basic"]:
                target["organization"] = document
            if "annual_dps" in {row.get("key") for row in document.get("fields", [])} and "records" in document:
                target["dividends"] = document
            if "history" in document:
                target["valuation"] = document
            if "shareholders" in document:
                target["shareholders"] = document
            if "segments" in document:
                target["segments"] = document
        if document.get("results"):
            for result in document["results"]:
                if result.get("periods") is not None and result.get("edinet_code"):
                    target = app_required_docs.setdefault(str(result["edinet_code"]), {})
                    period_type = path.stem.split("_")[-1]
                    if period_type in FINANCIAL_PERIODS:
                        target[f"financials_{period_type}"] = attach_field_units(result["periods"], document)
        if isinstance(document.get("fields"), list):
            edinet = str(document.get("edinet_code") or "")
            if edinet:
                master_row = next((row for row in master if row.get("edinet_code") == edinet), None)
                if master_row:
                    code = str(master_row.get("security_code") or "")
                    target = app_required_docs.setdefault(code, {})
                    if isinstance(document.get("history"), list):
                        target["valuation"] = document
                    if isinstance(document.get("records"), list) and "annual_dps" in {row.get("key") for row in document["fields"]}:
                        target["dividends"] = document
                    if isinstance(document.get("shareholders"), list):
                        target["shareholders"] = document
                    if isinstance(document.get("segments"), list):
                        target["segments"] = document
        financial_bundle = document.get("financials")
        if isinstance(financial_bundle, dict):
            for period_type in FINANCIAL_PERIODS:
                response = financial_bundle.get(period_type)
                result = next((row for row in response.get("results", []) if row.get("edinet_code")), None) if isinstance(response, dict) else None
                if result:
                    app_required_docs.setdefault(str(result["edinet_code"]), {})[f"financials_{period_type}"] = attach_field_units(
                        result.get("periods", []), response,
                    )
        if isinstance(document.get("results"), list):
            result = next((row for row in document["results"] if row.get("edinet_code")), None)
            if result:
                app_required_docs.setdefault(str(result["edinet_code"]), {})[f"financials_{path.stem.rsplit('_', 1)[-1]}"] = attach_field_units(
                    result.get("periods", []), document,
                )

    catalog: list[dict[str, Any]] = []
    index: list[dict[str, Any]] = []
    object_meta: list[dict[str, Any]] = []
    missing: dict[str, int] = defaultdict(int)
    for edinet, master_row in sorted(by_edinet.items(), key=lambda item: security_display_code(item[1])):
        code = security_display_code(master_row)
        company: dict[str, Any] = {
            "schema_version": 1,
            "security": {
                "code": code,
                "canonical_code": str(master_row.get("security_code") or f"{code}0"),
                "edinet_code": edinet,
                "name": master_row.get("name"),
                "metrics": master_row.get("metrics", []),
            },
            "organization": None,
            "financials": {period: [] for period in FINANCIAL_PERIODS},
            "dividends": None,
            "valuation": None,
            "segments": None,
            "shareholders": None,
            "disclosures": None,
            "sources": {},
        }
        dividend_bundle: dict[str, Any] | None = None
        for family, docs in category_docs.items():
            document = docs.get(edinet)
            if not document:
                missing[family] += 1
                continue
            if family == "organization":
                company[family] = {
                    "basic": document.get("basic", {}),
                    "financials": attach_object_field_units(document.get("financials", {}), document, prefix="financials."),
                    "securities": attach_nested_field_units(document.get("securities", []), document,
                                                             prefix="securities[].valuation.", child_key="valuation"),
                    "disclosures": document.get("disclosures", []),
                    "officers": attach_field_units(document.get("officers", []), document, prefix="officers[]."),
                }
            elif family == "dividends":
                records = attach_field_units(document.get("records", []), document)
                company[family] = {"records": records}
                dividend_bundle = {
                    "schema_version": 1,
                    "source": "IRBANK Connect",
                    "security_code": code,
                    "records": records,
                    "attribution": attribution(document),
                }
            elif family == "valuation":
                company[family] = {
                    "history": attach_field_units(document.get("history", []), document),
                    "per_5y_avg": document.get("per_5y_avg"),
                }
            elif family == "disclosures":
                company[family] = {"records": document.get("records", [])}
            else:
                collection = "segments" if family == "segments" else "shareholders"
                company[family] = {"records": attach_field_units(document.get(collection, []), document)}
            if attribution(document):
                company["sources"][family] = attribution(document)

        if financial_data.get(edinet):
            company["financials"] = financial_data[edinet]
        else:
            missing["financials"] += 1
        for period_type, source_info in financial_attributions.items():
            company["sources"].setdefault("financials", {})[period_type] = source_info

        key_dir = Path("market") / "irbank" / "v1" / "securities" / code
        app_required = app_required_docs.get(str(master_row.get("security_code") or "")) or app_required_docs.get(code) or (app_required_docs.get("E00012") if code == "7203" else None)
        if app_required:
            for family, key in (("weekly_margin", "weekly_margin"), ("jsf_margin", "jsf_margin"), ("disclosures", "disclosures")):
                document = app_required.get(family)
                if not document:
                    continue
                filename = {"weekly_margin": "weekly_margin.json", "jsf_margin": "jsf_margin.json", "disclosures": "disclosures.json"}[family]
                size, digest = write_json(output / key_dir / filename, document)
                object_meta.append({"key": (key_dir / filename).as_posix(), "bytes": size, "sha256": digest, "family": family})
                if family == "disclosures":
                    company["disclosures"] = {"records": document.get("records", [])}
                    company["sources"][family] = attribution(document)
            for family in ("organization", "dividends", "valuation", "shareholders", "segments"):
                document = app_required.get(family)
                if not document:
                    continue
                if family == "organization":
                    company[family] = {
                        "basic": document.get("basic", {}),
                        "financials": attach_object_field_units(document.get("financials", {}), document, prefix="financials."),
                        "securities": attach_nested_field_units(document.get("securities", []), document,
                                                                 prefix="securities[].valuation.", child_key="valuation"),
                        "disclosures": document.get("disclosures", []),
                        "officers": attach_field_units(document.get("officers", []), document, prefix="officers[]."),
                    }
                elif family == "dividends":
                    company[family] = {"records": attach_field_units(document.get("records", []), document)}
                elif family == "valuation":
                    company[family] = {"history": attach_field_units(document.get("history", []), document), "per_5y_avg": document.get("per_5y_avg")}
                else:
                    collection = "segments" if family == "segments" else "shareholders"
                    company[family] = {"records": attach_field_units(document.get(collection, []), document)}
                if attribution(document):
                    company["sources"][family] = attribution(document)
            financial_override = {period: app_required.get(f"financials_{period}") for period in FINANCIAL_PERIODS}
            if any(financial_override.values()):
                company["financials"] = {period: financial_override[period] or company["financials"].get(period, []) for period in FINANCIAL_PERIODS}
            if app_required.get("disclosures"):
                company["disclosures"] = {"records": app_required["disclosures"].get("records", [])}
                company["sources"]["disclosures"] = attribution(app_required["disclosures"])
            for family in ("organization", "dividends", "valuation", "shareholders", "segments"):
                document = app_required.get(family)
                if not document:
                    continue
                if family == "organization":
                    company[family] = {
                        "basic": document.get("basic", {}),
                        "financials": attach_object_field_units(document.get("financials", {}), document, prefix="financials."),
                        "securities": attach_nested_field_units(document.get("securities", []), document,
                                                                 prefix="securities[].valuation.", child_key="valuation"),
                        "disclosures": document.get("disclosures", []),
                        "officers": attach_field_units(document.get("officers", []), document, prefix="officers[]."),
                    }
                elif family == "dividends":
                    company[family] = {"records": attach_field_units(document.get("records", []), document)}
                elif family == "valuation":
                    company[family] = {"history": attach_field_units(document.get("history", []), document), "per_5y_avg": document.get("per_5y_avg")}
                else:
                    collection = "segments" if family == "segments" else "shareholders"
                    company[family] = {"records": attach_field_units(document.get(collection, []), document)}
                if attribution(document):
                    company["sources"][family] = attribution(document)
            financial_override = {period: app_required.get(f"financials_{period}") for period in FINANCIAL_PERIODS}
            if any(financial_override.values()):
                company["financials"] = {period: financial_override[period] or company["financials"].get(period, []) for period in FINANCIAL_PERIODS}
        overview_size, overview_hash = write_json(output / key_dir / "overview.json", company)
        object_meta.append({
            "key": (key_dir / "overview.json").as_posix(),
            "bytes": overview_size,
            "sha256": overview_hash,
            "family": "overview",
        })
        if dividend_bundle is not None:
            dividend_size, dividend_hash = write_json(output / key_dir / "dividends.json", dividend_bundle)
            object_meta.append({
                "key": (key_dir / "dividends.json").as_posix(),
                "bytes": dividend_size,
                "sha256": dividend_hash,
                "family": "dividends",
            })
        index.append({"code": code, "edinet_code": edinet, "key": (key_dir / "overview.json").as_posix()})
        catalog.append({
            "code": code,
            "name": master_row.get("name"),
            "edinet_code": edinet,
            "has_overview": True,
        })

        for family, docs in (("prices", price_docs), ("weekly_margin", margin_docs), ("jsf_margin", jsf_margin_docs)):
            document = docs.get(edinet)
            if not document:
                if args.include_jpx:
                    missing[family] += 1
                continue
            filename = {
                "prices": "daily_prices.json",
                "weekly_margin": "weekly_margin.json",
                "jsf_margin": "jsf_margin.json",
            }[family]
            size, digest = write_json(output / key_dir / filename, document)
            object_meta.append({
                "key": (key_dir / filename).as_posix(),
                "bytes": size,
                "sha256": digest,
                "family": family,
            })

    catalog_size, catalog_hash = write_json(output / "market/irbank/v1/catalog.json", {
        "schema_version": 1,
        "source": "IRBANK Connect securities master",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "count": len(catalog),
        "securities": catalog,
    })
    index_size, index_hash = write_json(output / "market/irbank/v1/index.json", {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "count": len(index),
        "securities": index,
    })
    object_meta.extend([
        {"key": "market/irbank/v1/catalog.json", "family": "catalog", "bytes": catalog_size, "sha256": catalog_hash},
        {"key": "market/irbank/v1/index.json", "family": "index", "bytes": index_size, "sha256": index_hash},
    ])
    summary = {
        "schema_version": 1,
        "source": "IRBANK Connect",
        "source_snapshot": "irbankMCP/irbank",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "security_count": len(catalog),
        "overview_count": len(index),
        "jpx_data_included": bool(args.include_jpx),
        "jsf_margin_included": bool(jsf_margin_docs),
        "layout": {
            "catalog": "market/irbank/v1/catalog.json",
            "index": "market/irbank/v1/index.json",
            "overview": "market/irbank/v1/securities/{4_digit_code}/overview.json",
            "dividends": "market/irbank/v1/securities/{4_digit_code}/dividends.json",
            "daily_prices": "market/irbank/v1/securities/{4_digit_code}/daily_prices.json",
            "weekly_margin": "market/irbank/v1/securities/{4_digit_code}/weekly_margin.json",
            "jsf_margin": "market/irbank/v1/securities/{4_digit_code}/jsf_margin.json",
        },
        "missing_families": dict(sorted(missing.items())),
        "objects": sorted(object_meta, key=lambda row: row["key"]),
    }
    write_json(output / "market/irbank/v1/manifest.json", summary)
    total_bytes = sum(row.get("bytes", 0) for row in object_meta)
    print(json.dumps({
        "output": str(output),
        "security_count": len(catalog),
        "objects": len(object_meta) + 1,
        "bytes": total_bytes,
        "jpx_data_included": bool(args.include_jpx),
        "missing_families": dict(sorted(missing.items())),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"prepare failed: {exc}", file=sys.stderr)
        raise
