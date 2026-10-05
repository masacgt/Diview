#!/usr/bin/env python3
"""Export IRBANK Connect's saved listed-security master for Yahoo collection."""

from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = ROOT / "data/irbank/organized/d1/securities.csv"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    rows: dict[str, str] = {}
    with args.source.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or "security_code_display" not in reader.fieldnames:
            raise RuntimeError("IRBANK security master lacks security_code_display")
        for row in reader:
            code = (row.get("security_code_display") or "").strip().upper()
            if not re.fullmatch(r"[A-Z0-9]{4,5}", code):
                continue
            if code in rows:
                raise RuntimeError(f"Duplicate security code in IRBANK master: {code}")
            rows[code] = ""
    if len(rows) < 1000:
        raise RuntimeError(f"IRBANK master returned only {len(rows)} issues; refusing partial universe")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(args.output.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["security_code_display", "sector_name"])
        writer.writerows((code, rows[code]) for code in sorted(rows))
    temporary.replace(args.output)
    print(f"Saved {len(rows)} IRBANK listed securities to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
