"""Fetch and validate currently listed J-REITs from JPX's public issue list."""

from __future__ import annotations

from datetime import date, datetime
from html.parser import HTMLParser
import re
from urllib.request import Request, urlopen


JPX_REIT_SOURCE = "JPX Listed Issues - REITs"
JPX_REIT_SOURCE_URL = "https://www.jpx.co.jp/english/equities/products/reits/issues/"
MIN_JPX_REIT_COUNT = 50
USER_AGENT = "Diview-yfinance-universe/1.0 (+https://github.com/masacgt/Diview)"


class _IssueTableParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.tables: list[list[list[str]]] = []
        self._in_table = False
        self._row: list[str] | None = None
        self._cell: list[str] | None = None
        self._table: list[list[str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "table":
            self._in_table = True
            self._table = []
        elif self._in_table and tag == "tr":
            self._row = []
        elif self._in_table and tag in {"td", "th"} and self._row is not None:
            self._cell = []
        elif self._in_table and tag == "br" and self._cell is not None:
            self._cell.append(" ")

    def handle_endtag(self, tag: str) -> None:
        if self._in_table and tag in {"td", "th"} and self._row is not None and self._cell is not None:
            self._row.append(" ".join("".join(self._cell).split()))
            self._cell = None
        elif self._in_table and tag == "tr" and self._row is not None:
            if self._row:
                self._table.append(self._row)
            self._row = None
        elif tag == "table" and self._in_table:
            self.tables.append(self._table)
            self._table = []
            self._in_table = False

    def handle_data(self, data: str) -> None:
        if self._cell is not None:
            self._cell.append(data)


def _parse_listing_date(raw: str) -> date:
    normalized = re.sub(r"\s+", " ", raw.strip())
    for fmt in ("%b. %d, %Y", "%b %d, %Y"):
        try:
            return datetime.strptime(normalized, fmt).date()
        except ValueError:
            pass
    raise ValueError(f"JPX REIT list contains an unrecognized listing date: {raw!r}")


def parse_jpx_reit_html(
    html: str,
    *,
    as_of: date,
    minimum_count: int = MIN_JPX_REIT_COUNT,
) -> list[dict[str, str]]:
    parser = _IssueTableParser()
    parser.feed(html)
    target_rows: list[list[str]] | None = None
    date_index = code_index = -1
    for table in parser.tables:
        if not table:
            continue
        headers = [re.sub(r"\s+", " ", cell.strip().casefold()) for cell in table[0]]
        if "listing date" in headers and any(header.startswith("code") for header in headers):
            target_rows = table[1:]
            date_index = headers.index("listing date")
            code_index = next(index for index, header in enumerate(headers) if header.startswith("code"))
            break
    if target_rows is None:
        raise ValueError("Could not find the JPX REIT issue table with Listing Date and Code columns")

    found: dict[str, dict[str, str]] = {}
    for row in target_rows:
        if max(date_index, code_index) >= len(row):
            # JPX inserts a two-cell management-company/offer-price row after
            # each six-cell issue row. It is supplementary, not another issue.
            if len(row) == 2:
                continue
            raise ValueError("JPX REIT issue table contains a truncated row")
        listing_date = _parse_listing_date(row[date_index])
        match = re.match(r"\s*([A-Z0-9]{4,5})(?:\s*\(|\s*$)", row[code_index].upper())
        if not match:
            raise ValueError(f"JPX REIT issue table contains an unrecognized code: {row[code_index]!r}")
        code = match.group(1)
        if len(code) == 5 and code.endswith("0"):
            code = code[:4]
        if not re.fullmatch(r"[A-Z0-9]{4,5}", code):
            raise ValueError(f"JPX REIT issue table contains an invalid code: {row[code_index]!r}")
        if code in found:
            raise ValueError(f"JPX REIT issue table contains a duplicate code: {code}")
        if listing_date <= as_of:
            found[code] = {"security_code_display": code, "listing_date": listing_date.isoformat()}

    if len(found) < minimum_count:
        raise ValueError(f"JPX REIT list returned only {len(found)} currently listed issues")
    return [found[code] for code in sorted(found)]


def fetch_jpx_reits(*, as_of: date, timeout_seconds: int = 30) -> list[dict[str, str]]:
    request = Request(JPX_REIT_SOURCE_URL, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=timeout_seconds) as response:
        if response.status != 200:
            raise RuntimeError(f"JPX REIT list returned HTTP {response.status}")
        html = response.read().decode("utf-8", "replace")
    return parse_jpx_reit_html(html, as_of=as_of)
