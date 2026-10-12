#!/usr/bin/env python3
"""Fetch IRBANK dividend-page stock split and reverse-split notes for the R2 catalog."""

from __future__ import annotations

import csv
import json
import re
import sys
import time
from datetime import datetime, timezone
from html import unescape
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
CATALOG_URL = "https://diview-irbank.omasa.workers.dev/api/v1/irbank/catalog"
OUTPUT_CSV = ROOT / "data/irbank/catalog/share_split_events.csv"
OUTPUT_MANIFEST = ROOT / "data/irbank/catalog/share_split_events_manifest.json"
OUTPUT_ERRORS = ROOT / "data/irbank/catalog/share_split_events_errors.json"
USER_AGENT = "Diview-IRBANK-event-research/1.0 (public page, paced requests)"
PACE_SECONDS = 0.5
MAX_ATTEMPTS = 4

NOTE_RE = re.compile(
    r"<dt[^>]*>\s*<sup>\s*#\d+\s*</sup>\s*株式分割・併合等\s*</dt>\s*<dd>(.*?)</dd>",
    re.S,
)
TAG_RE = re.compile(r"<[^>]+>")
EVENT_RE = re.compile(
    r"(?P<before>\d+(?:\.\d+)?)\s*[:：]\s*(?P<after>\d+(?:\.\d+)?)\s*"
    r"(?P<year>\d{4})年\s*(?P<month>\d{1,2})月\s*(?P<day>\d{1,2})日"
)


def get_json(url: str) -> dict:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def get_html(url: str) -> str:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def extract_note(html: str) -> list[tuple[str, str]]:
    events: list[tuple[str, str]] = []
    for match in NOTE_RE.finditer(html):
        text = " ".join(unescape(TAG_RE.sub(" ", match.group(1))).split())
        found = list(EVENT_RE.finditer(text))
        if not found and text:
            events.append((text, ""))
            continue
        for event in found:
            before = float(event.group("before"))
            after = float(event.group("after"))
            kind = "split" if after > before else "reverse_split" if after < before else "share_change"
            date = f"{int(event.group('year')):04d}-{int(event.group('month')):02d}-{int(event.group('day')):02d}"
            events.append((event.group(0), date, before, after, after / before, kind))
    return events


def save(rows: list[dict], errors: list[dict], catalog_count: int, attempted: int) -> None:
    fields = ["code", "name", "edinet_code", "ratio_text", "shares_before", "shares_after", "factor", "effective_date", "event_type", "source_url"]
    OUTPUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_CSV.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(sorted(rows, key=lambda row: (row["effective_date"] or "", row["code"])))
    OUTPUT_ERRORS.write_text(json.dumps(errors, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    manifest = {
        "source": "IRBANK Connect R2 catalog and IRBANK dividend history note: 株式分割・併合等",
        "catalog_url": CATALOG_URL,
        "retrieved_at": datetime.now(timezone.utc).isoformat(),
        "catalog_total": catalog_count,
        "companies_attempted": attempted,
        "event_rows": len(rows),
        "unique_event_securities": len({row["code"] for row in rows}),
        "request_errors": len(errors),
        "request_pacing_seconds": PACE_SECONDS,
        "errors_file": OUTPUT_ERRORS.name,
    }
    OUTPUT_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    catalog = get_json(CATALOG_URL)
    securities = [item for item in catalog["securities"] if str(item.get("edinet_code", "")).startswith("E")]
    rows: list[dict] = []
    errors: list[dict] = []
    attempted = 0
    for security in securities:
        code = str(security["code"])
        edinet_code = str(security["edinet_code"])
        url = f"https://irbank.net/{edinet_code}/dividend"
        attempted += 1
        try:
            html = get_html(url)
            for event in extract_note(html):
                if len(event) == 2:
                    ratio_text, _ = event
                    rows.append({"code": code, "name": security.get("name", ""), "edinet_code": edinet_code,
                                 "ratio_text": ratio_text, "shares_before": "", "shares_after": "", "factor": "",
                                 "effective_date": "", "event_type": "unparsed", "source_url": url})
                else:
                    ratio_text, date, before, after, factor, kind = event
                    rows.append({"code": code, "name": security.get("name", ""), "edinet_code": edinet_code,
                                 "ratio_text": ratio_text, "shares_before": before, "shares_after": after,
                                 "factor": factor, "effective_date": date, "event_type": kind, "source_url": url})
        except (HTTPError, URLError, TimeoutError, OSError, ValueError) as error:
            errors.append({"code": code, "name": security.get("name", ""), "edinet_code": edinet_code,
                           "url": url, "error": str(error)})
            for attempt in range(1, MAX_ATTEMPTS):
                time.sleep(attempt * 3)
                try:
                    html = get_html(url)
                    for event in extract_note(html):
                        if len(event) == 2:
                            ratio_text, _ = event
                            rows.append({"code": code, "name": security.get("name", ""), "edinet_code": edinet_code,
                                         "ratio_text": ratio_text, "shares_before": "", "shares_after": "", "factor": "",
                                         "effective_date": "", "event_type": "unparsed", "source_url": url})
                        else:
                            ratio_text, date, before, after, factor, kind = event
                            rows.append({"code": code, "name": security.get("name", ""), "edinet_code": edinet_code,
                                         "ratio_text": ratio_text, "shares_before": before, "shares_after": after,
                                         "factor": factor, "effective_date": date, "event_type": kind, "source_url": url})
                    errors.pop()
                    break
                except (HTTPError, URLError, TimeoutError, OSError, ValueError) as retry_error:
                    errors[-1]["error"] = str(retry_error)
        if attempted % 100 == 0:
            save(rows, errors, len(catalog["securities"]), attempted)
            print(f"progress {attempted}/{len(securities)} events={len(rows)} errors={len(errors)}", flush=True)
        time.sleep(PACE_SECONDS)
    save(rows, errors, len(catalog["securities"]), attempted)
    print(json.dumps({"catalog_total": len(catalog["securities"]), "companies_attempted": attempted,
                      "event_rows": len(rows), "unique_event_securities": len({row['code'] for row in rows}),
                      "request_errors": len(errors), "unparsed_rows": sum(row['event_type'] == 'unparsed' for row in rows)},
                     ensure_ascii=False))
    return 0 if not errors else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"fatal: {exc}", file=sys.stderr)
        raise
