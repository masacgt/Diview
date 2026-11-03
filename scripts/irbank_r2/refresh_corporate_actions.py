#!/usr/bin/env python3
"""Extract issuer-confirmed share actions and normalize raw Yahoo dividends."""

from __future__ import annotations

import argparse
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation, localcontext
from html import unescape
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import unicodedata
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts" / "yfinance_r2"))
sys.path.insert(0, str(ROOT / "scripts" / "irbank_r2"))

from financial_revision import bump_financial_revision  # noqa: E402
from refresh_v2_dividends import (  # noqa: E402
    API_BASE,
    RequestBudget,
    make_r2_client,
    read_r2_document,
    request_json,
)

API_REQUEST_LIMIT = 200
DEFAULT_FROM = "2017-01-01"
ACTION_OBJECT = "market/irbank/v1/events/share-splits/{code}.json"
DIVIDEND_BASIS_OBJECT = "market/yfinance/v2/securities/{symbol}/dividend_basis/current.json"
DISCLOSURE_TYPE = "有価証券報告書"
TAG_RE = re.compile(r"<[^>]*>")
DATE_RE = re.compile(
    r"(?P<year>20\d{2}|同)年\s*(?P<month>\d{1,2})月\s*(?P<day>\d{1,2})日"
    r"(?P<effective_suffix>\s*(?:付|付け)(?:\s*で)?)?"
)
EXPLICIT_YEAR_RE = re.compile(r"(?P<year>20\d{2})年")
ACTION_RE = re.compile(r"株式\s*(?P<kind>分割|併合)")
RATIO_ACTION_RE = re.compile(
    r"(?P<before>[\d,]+)\s*株\s*につき\s*(?P<after>[\d,]+)\s*株"
    r"(?:\s*の割合で)?"
)


def _plain_text(value: str) -> str:
    return " ".join(unescape(TAG_RE.sub(" ", value or "")).split())


def _positive_decimal(value: str) -> Decimal | None:
    try:
        parsed = Decimal(value.replace(",", ""))
    except (InvalidOperation, AttributeError):
        return None
    return parsed if parsed.is_finite() and parsed > 0 else None


def extract_section_actions(
    sections: list[dict[str, Any]], meta: dict[str, Any],
) -> list[dict[str, Any]]:
    """Extract dated split/consolidation ratios from disclosure section text."""
    events: dict[tuple[str, str], dict[str, Any]] = {}
    for section in sections:
        raw = unicodedata.normalize("NFKC", _plain_text(str(section.get("html_or_text") or "")))
        if not raw:
            continue
        for action_match in ACTION_RE.finditer(raw):
            candidates = [
                match for match in DATE_RE.finditer(raw, max(0, action_match.start() - 280), action_match.start())
                if match.end() < action_match.start() and match.group("effective_suffix")
            ]
            extracted = None
            for date_match in reversed(candidates):
                ratio_match = RATIO_ACTION_RE.search(raw, date_match.end(), action_match.start())
                if ratio_match:
                    extracted = (date_match, ratio_match)
                    break
            if extracted is None:
                continue
            date_match, match = extracted
            year_text = date_match.group("year")
            if year_text == "同":
                preceding_years = list(EXPLICIT_YEAR_RE.finditer(raw[max(0, date_match.start() - 300):date_match.start()]))
                if not preceding_years:
                    continue
                year_text = preceding_years[-1].group("year")
            date_text = date(
                int(year_text), int(date_match.group("month")), int(date_match.group("day")),
            ).isoformat()
            before = _positive_decimal(match.group("before"))
            after = _positive_decimal(match.group("after"))
            if before is None or after is None:
                continue
            ratio = after / before
            if ratio == 1:
                continue
            action = "split" if ratio > 1 else "reverse_split"
            key = (date_text, format(ratio.normalize(), "f"))
            event = events.setdefault(key, {
                "effective_date": date_text,
                "shares_before": int(before),
                "shares_after": int(after),
                "ratio": float(ratio),
                "action_type": action,
                "sources": [],
            })
            source_ref = {
                "doc_id": meta.get("doc_id"),
                "source": meta.get("source"),
                "filing_date": meta.get("filing_date"),
                "source_url": meta.get("source_url"),
                "section_id": section.get("section_id"),
                "section_title": section.get("section_title_raw"),
            }
            if source_ref not in event["sources"]:
                event["sources"].append(source_ref)
    return sorted(events.values(), key=lambda row: (row["effective_date"], row["ratio"]))


def _disclosure_pages(code: str, start: str, end: str, token: str,
                      budget: RequestBudget) -> list[dict[str, Any]]:
    import urllib.parse

    params = {
        "from": start, "to": end, "document_type": DISCLOSURE_TYPE,
        "limit": "100",
    }
    url = f"{API_BASE}/securities/{urllib.parse.quote(code)}/disclosures?{urllib.parse.urlencode(params)}"
    documents: list[dict[str, Any]] = []
    while url:
        page = request_json(url, token, budget=budget)
        documents.extend(page.get("disclosures") or [])
        cursor = page.get("next_cursor")
        if not cursor:
            break
        params["cursor"] = str(cursor)
        url = f"{API_BASE}/securities/{urllib.parse.quote(code)}/disclosures?{urllib.parse.urlencode(params)}"
    return documents


def fetch_issuer_actions(code: str, start: str, end: str, token: str,
                         budget: RequestBudget) -> tuple[list[dict[str, Any]], list[dict[str, str]]]:
    documents = _disclosure_pages(code, start, end, token, budget)
    events: dict[tuple[str, float], dict[str, Any]] = {}
    errors: list[dict[str, str]] = []
    for listing in documents:
        doc_id = str(listing.get("doc_id") or "")
        if not doc_id:
            continue
        url = f"{API_BASE}/disclosures/{doc_id}?include=meta,sections"
        try:
            document = request_json(url, token, budget=budget)
            meta = document.get("meta") or listing
            extracted = extract_section_actions(document.get("sections") or [], meta)
            for event in extracted:
                key = (event["effective_date"], event["ratio"])
                merged = events.setdefault(key, event)
                for source in event["sources"]:
                    if source not in merged["sources"]:
                        merged["sources"].append(source)
        except Exception as exc:  # retain partial retrieval state, never claim complete coverage
            errors.append({"doc_id": doc_id, "error": f"{type(exc).__name__}: {str(exc)[:240]}"})
    return sorted(events.values(), key=lambda row: row["effective_date"]), errors


def normalize_raw_dividends(raw_events: list[dict[str, Any]], actions: list[dict[str, Any]],
                            as_of: date, coverage_start: date) -> list[dict[str, Any]]:
    """Adjust repair=False Yahoo amounts into the current share basis."""
    normalized: dict[str, dict[str, Any]] = {}
    valid_actions = [
        row for row in actions
        if row.get("effective_date") and date.fromisoformat(row["effective_date"]) <= as_of
        and Decimal(str(row.get("ratio", 0))) > 0
    ]
    for event in raw_events:
        event_date = str(event.get("date") or "")[:10]
        amount = _positive_decimal(str(event.get("amount", "")))
        if (not re.fullmatch(r"\d{4}-\d{2}-\d{2}", event_date) or amount is None
                or event_date < coverage_start.isoformat()):
            continue
        factor = Decimal(1)
        applied: list[dict[str, Any]] = []
        for action in valid_actions:
            if event_date < action["effective_date"]:
                ratio = Decimal(str(action["ratio"]))
                factor *= ratio
                applied.append({"effective_date": action["effective_date"], "ratio": float(ratio)})
        if factor <= 0:
            continue
        with localcontext() as context:
            context.prec = max(60, len(amount.as_tuple().digits) + len(factor.as_tuple().digits) + 24)
            adjusted = amount / factor
            rounded = adjusted.quantize(Decimal("0.00000001"))
        record = {
            "date": event_date,
            "amount": float(rounded),
            "raw_amount": float(amount),
            "share_adjustment_factor": float(factor),
            "applied_actions": applied,
        }
        # Yahoo should have one cash distribution per ex-date. Conflicting values
        # remain a data-quality failure instead of being summed or guessed.
        previous = normalized.get(event_date)
        if previous is None:
            normalized[event_date] = record
        elif previous["amount"] != record["amount"]:
            raise ValueError(f"Conflicting raw dividends on {event_date}")
    return [normalized[key] for key in sorted(normalized)]


def fetch_raw_yahoo_dividends(symbol: str, as_of: date) -> tuple[list[dict[str, Any]], str | None]:
    import yfinance as yf

    ticker = yf.Ticker(symbol)
    frame = ticker.history(
        period="max", interval="1d", actions=True, auto_adjust=False,
        repair=False, keepna=True, raise_errors=True,
    )
    if frame is None or frame.empty or "Dividends" not in frame.columns:
        raise RuntimeError("Yahoo raw action history unavailable")
    raw_events = []
    for timestamp, row in frame.iterrows():
        event_date = timestamp.date().isoformat()
        amount = _positive_decimal(str(row.get("Dividends", 0)))
        if event_date <= as_of.isoformat() and amount is not None:
            raw_events.append({"date": event_date, "amount": float(amount)})
    return raw_events, frame.index.min().date().isoformat()


def build_documents(code: str, actions: list[dict[str, Any]], action_errors: list[dict[str, str]],
                    dividends: list[dict[str, Any]], start: str, end: str,
                    dividend_coverage_start: str | None) -> tuple[dict[str, Any], dict[str, Any]]:
    dates = [row["effective_date"] for row in actions]
    action_dates: dict[str, set[float]] = {}
    for row in actions:
        action_dates.setdefault(row["effective_date"], set()).add(float(row["ratio"]))
    conflicts = sorted(day for day, ratios in action_dates.items() if len(ratios) > 1)
    action_document = {
        "schema_version": 1,
        "security_code": code,
        "status": "review_required" if conflicts else "available" if actions else "empty",
        "coverage": {"disclosures_from": start, "disclosures_to": end,
                     "documents_with_errors": len(action_errors), "conflicting_dates": conflicts},
        "events": actions,
        "errors": action_errors,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": "IRBANK Connect securities disclosures and disclosure sections",
        "attribution": {"source_label": "金融庁 EDINET / JPX TDnet", "processed_by": "IRBANK"},
    }
    dividend_document = {
        "schema_version": 1,
        "symbol": f"{code}.T",
        "status": "review_required" if conflicts else "available" if dividends else "empty",
        "coverage": {"normalized_events_from": start, "raw_yahoo_from": dividend_coverage_start,
                     "actions_disclosures_from": start,
                     "actions_disclosures_to": end, "documents_with_errors": len(action_errors)},
        "amount_basis": "current_share_basis_from_raw_yahoo_and_issuer_actions",
        "records": dividends,
        "source": "Yahoo Finance raw dividend events normalized with IRBANK Connect issuer-filed share actions",
        "generated_at": datetime.now(timezone.utc).isoformat(),
    }
    return action_document, dividend_document


def sha256_payload(document: dict[str, Any]) -> tuple[bytes, str]:
    payload = json.dumps(document, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
    return payload, hashlib.sha256(payload).hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codes", required=True, help="Comma-separated 4-character security codes")
    parser.add_argument("--from", dest="date_from", default=DEFAULT_FROM, help="Disclosure filing date lower bound")
    parser.add_argument("--to", dest="date_to", default=date.today().isoformat(), help="Disclosure filing date upper bound")
    parser.add_argument("--max-api-requests", type=int, default=API_REQUEST_LIMIT)
    parser.add_argument("--publish", action="store_true", help="Write verified derived JSON objects to R2")
    parser.add_argument("--output-dir", type=Path, help="Optional local output directory for dry-run artifacts")
    args = parser.parse_args(argv)
    codes = sorted({value.strip().upper() for value in args.codes.split(",") if value.strip()})
    if not codes or any(not re.fullmatch(r"[A-Z0-9]{4}", code) for code in codes):
        parser.error("--codes must contain only four-character security codes")
    if date.fromisoformat(args.date_from) > date.fromisoformat(args.date_to):
        parser.error("--from must be on or before --to")
    token = os.environ.get("IRBANK_API_KEY", "").strip()
    if not token:
        parser.error("IRBANK_API_KEY is required")
    if not 1 <= args.max_api_requests <= API_REQUEST_LIMIT:
        parser.error(f"--max-api-requests must be 1..{API_REQUEST_LIMIT}")
    budget = RequestBudget(args.max_api_requests)
    as_of = date.fromisoformat(args.date_to)
    r2 = make_r2_client(4) if args.publish else None
    bucket = os.environ.get("R2_BUCKET", "diview-market-data")
    summary = []
    for code in codes:
        actions, errors = fetch_issuer_actions(code, args.date_from, args.date_to, token, budget)
        if errors or not actions:
            raise RuntimeError(f"{code}: issuer action retrieval incomplete ({len(errors)} errors, {len(actions)} events)")
        raw_dividends, history_start = fetch_raw_yahoo_dividends(f"{code}.T", as_of)
        dividends = normalize_raw_dividends(raw_dividends, actions, as_of, date.fromisoformat(args.date_from))
        action_doc, dividend_doc = build_documents(code, actions, errors, dividends, args.date_from,
                                                   args.date_to, history_start)
        if action_doc["status"] != "available" or dividend_doc["status"] != "available":
            raise RuntimeError(f"{code}: conflicting action dates require review")
        objects = {
            ACTION_OBJECT.format(code=code): action_doc,
            DIVIDEND_BASIS_OBJECT.format(symbol=f"{code}.T"): dividend_doc,
        }
        for key, document in objects.items():
            payload, digest = sha256_payload(document)
            if args.output_dir:
                args.output_dir.mkdir(parents=True, exist_ok=True)
                target = args.output_dir / f"{code}-{Path(key).name}"
                target.write_bytes(payload)
            if args.publish:
                r2.put_object(Bucket=bucket, Key=key, Body=payload,
                              ContentType="application/json; charset=utf-8", CacheControl="public, max-age=300")
                saved = read_r2_document(r2, bucket, key)
                saved_payload = json.dumps(saved, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8") if saved else b""
                if hashlib.sha256(saved_payload).hexdigest() != digest:
                    raise RuntimeError(f"R2 read-back SHA-256 mismatch: {key}")
            summary.append({"code": code, "key": key, "sha256": digest,
                            "events": len(document.get("events", document.get("records", [])))})
    revision = bump_financial_revision(r2, bucket) if args.publish else None
    print(json.dumps({"codes": codes, "api_requests": budget.used, "published": bool(args.publish),
                      "financial_revision": revision,
                      "objects": summary}, ensure_ascii=False, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
