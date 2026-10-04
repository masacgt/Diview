#!/usr/bin/env python3
"""Refresh the bundled TSE 33-industry code master from the official JPX workbook."""

from __future__ import annotations

import argparse
import io
import re
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


SOURCE_URL = "https://www.jpx.co.jp/markets/statistics-equities/misc/tvdivq0000001vg2-att/data_j.xlsx"
OUTPUT = Path(__file__).resolve().parents[1] / "android/app/src/main/assets/tse33_industry_master.tsv"
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def cell_value(cell: ET.Element, shared: list[str]) -> str:
    value = cell.find("m:v", NS)
    raw = value.text if value is not None and value.text else ""
    if cell.get("t") == "s" and raw:
        return shared[int(raw)]
    if cell.get("t") == "inlineStr":
        return "".join(part.text or "" for part in cell.findall(".//m:t", NS))
    return raw


def parse_workbook(payload: bytes) -> list[tuple[str, str, str, str]]:
    with zipfile.ZipFile(io.BytesIO(payload)) as workbook:
        shared_root = ET.fromstring(workbook.read("xl/sharedStrings.xml"))
        shared = [
            "".join(part.text or "" for part in item.findall(".//m:t", NS))
            for item in shared_root.findall("m:si", NS)
        ]
        sheet = ET.fromstring(workbook.read("xl/worksheets/sheet1.xml"))

    records: list[tuple[str, str, str, str]] = []
    seen: set[str] = set()
    for row in sheet.findall(".//m:sheetData/m:row", NS)[1:]:
        cells = {
            re.match(r"[A-Z]+", cell.get("r", "")).group(0): cell_value(cell, shared)
            for cell in row.findall("m:c", NS)
        }
        code = cells.get("B", "").strip()
        industry_code = cells.get("E", "").strip()
        industry_name = cells.get("F", "").strip()
        name = cells.get("C", "").strip()
        if not re.fullmatch(r"[A-Z0-9]{4}", code.upper()) or not industry_code.isdigit() or industry_name in ("", "-"):
            continue
        code = code.upper()
        if code in seen:
            raise ValueError(f"Duplicate TSE security code: {code}")
        seen.add(code)
        records.append((code, name, industry_code.zfill(4), industry_name))

    industry_names = {record[3] for record in records}
    if len(industry_names) != 33:
        raise ValueError(f"Expected all 33 TSE industries, found {len(industry_names)}")
    return sorted(records)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-url", default=SOURCE_URL)
    args = parser.parse_args()
    request = urllib.request.Request(args.source_url, headers={"User-Agent": "Diview TSE33 master updater"})
    with urllib.request.urlopen(request, timeout=60) as response:
        records = parse_workbook(response.read())

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    lines = ["code\tname\tindustry_code\tindustry_name"]
    lines.extend("\t".join(record) for record in records)
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} securities across 33 industries to {OUTPUT}")


if __name__ == "__main__":
    main()
