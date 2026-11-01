#!/usr/bin/env python3
"""Refresh REIT and infrastructure market membership from JPX issue lists."""
from __future__ import annotations

from html.parser import HTMLParser
from pathlib import Path
import json
import re
import urllib.request

SOURCES = {
    "reit": "https://www.jpx.co.jp/equities/products/reits/issues/",
    "infrastructure": "https://www.jpx.co.jp/equities/products/infrastructure/issues/index.html",
}
OUTPUT = Path(__file__).resolve().parents[1] / "android/app/src/main/assets/jpx_fund_market_master.tsv"
WORKER_OUTPUT = Path(__file__).resolve().parents[2] / "data/irbank/cloudflare/src/jpx_reit_market_master.json"


class Rows(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.row: list[str] | None = None
        self.rows: list[list[str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "tr":
            self.row = []

    def handle_data(self, data: str) -> None:
        if self.row is not None and data.strip():
            self.row.append(data.strip())

    def handle_endtag(self, tag: str) -> None:
        if tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None


def parse(payload: str) -> list[tuple[str, str]]:
    parser = Rows()
    parser.feed(payload)
    result = []
    for row in parser.rows:
        if len(row) >= 4 and re.fullmatch(r"\d{4}/\d{2}/\d{2}", row[0]) and \
                re.fullmatch(r"[0-9A-Z]{4}", row[2]) and re.fullmatch(r"[（(]JP[A-Z0-9]{10}[）)]", row[3]):
            result.append((row[2], row[0]))
    return result


def main() -> None:
    records = []
    for market, url in SOURCES.items():
        request = urllib.request.Request(url, headers={"User-Agent": "Diview JPX market master updater"})
        with urllib.request.urlopen(request, timeout=60) as response:
            rows = parse(response.read().decode("utf-8"))
        if len(rows) < (30 if market == "reit" else 1):
            raise ValueError(f"Incomplete JPX {market} list: {len(rows)} rows")
        records.extend((code, market, date) for code, date in rows)
    if len({row[0] for row in records}) != len(records):
        raise ValueError("Duplicate code across JPX fund markets")
    OUTPUT.write_text("code\tmarket\tlisting_date\n" + "".join("\t".join(row) + "\n" for row in sorted(records)), encoding="utf-8")
    reits = {code: date.replace("/", "-") for code, market, date in sorted(records) if market == "reit"}
    WORKER_OUTPUT.write_text(json.dumps(reits, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(records)} JPX REIT and infrastructure listings to {OUTPUT}")


if __name__ == "__main__":
    main()
