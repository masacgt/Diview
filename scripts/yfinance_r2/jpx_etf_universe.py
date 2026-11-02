"""Fetch and validate currently listed ETFs from JPX's public issue list."""

from __future__ import annotations

from datetime import date, datetime
from html.parser import HTMLParser
import re
from urllib.request import Request, urlopen

from irbank_universe import JPX_ETF_SOURCE_URL, display_code


MIN_JPX_ETF_COUNT = 350
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
    raise ValueError(f"JPX ETF list contains an unrecognized listing date: {raw!r}")


def parse_jpx_etf_html(
    html: str,
    *,
    as_of: date,
    minimum_count: int = MIN_JPX_ETF_COUNT,
) -> list[dict[str, str]]:
    parser = _IssueTableParser()
    parser.feed(html)
    target_rows: list[list[str]] | None = None
    date_index = code_index = -1
    for table in parser.tables:
        if not table:
            continue
        headers = [cell.strip().casefold() for cell in table[0]]
        if "listing date" in headers and "code" in headers:
            target_rows = table[1:]
            date_index = headers.index("listing date")
            code_index = headers.index("code")
            break
    if target_rows is None:
        raise ValueError("Could not find the JPX ETF issue table with Listing Date and Code columns")

    found: dict[str, dict[str, str]] = {}
    for row in target_rows:
        if max(date_index, code_index) >= len(row):
            raise ValueError("JPX ETF issue table contains a truncated row")
        raw_code = row[code_index].strip().upper()
        code = display_code(raw_code)
        if code is None or code != raw_code:
            raise ValueError(f"JPX ETF issue table contains an invalid code: {raw_code!r}")
        listing_date = _parse_listing_date(row[date_index])
        if code in found:
            raise ValueError(f"JPX ETF issue table contains a duplicate code: {code}")
        if listing_date <= as_of:
            found[code] = {"security_code_display": code, "listing_date": listing_date.isoformat()}

    if len(found) < minimum_count:
        raise ValueError(f"JPX ETF issue table returned only {len(found)} currently listed issues")
    return [found[code] for code in sorted(found)]


def fetch_jpx_etfs(*, as_of: date, timeout_seconds: int = 30) -> list[dict[str, str]]:
    request = Request(JPX_ETF_SOURCE_URL, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=timeout_seconds) as response:
        if response.status != 200:
            raise RuntimeError(f"JPX ETF list returned HTTP {response.status}")
        html = response.read().decode("utf-8", "replace")
    return parse_jpx_etf_html(html, as_of=as_of)
