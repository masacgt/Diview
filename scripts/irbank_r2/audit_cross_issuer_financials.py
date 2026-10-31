#!/usr/bin/env python3
"""Read-only candidate audit; identical figures require independent verification."""
from __future__ import annotations

import argparse
from collections import defaultdict
from itertools import combinations
import json
import math
from pathlib import Path
from typing import Any

FIELDS = ("sales", "operating_profit", "total_assets", "net_assets")
PROFILES = {
    'core': FIELDS,
    'cash': ('operating_cf', 'investing_cf', 'total_assets', 'net_assets'),
    'income_cash': ('sales', 'operating_profit', 'operating_cf', 'investing_cf'),
    'earnings': ('ordinary_income', 'net_income', 'total_assets', 'net_assets'),
}


def find_known_partial_contamination(results: list[dict[str, Any]]) -> list[str]:
    """Identify the independently verified mixed-scope 2708 FY2014 seed."""
    matches = []
    for result in results:
        if str(result.get('edinet_code') or '') != 'E02938':
            continue
        for row in result.get('periods', []):
            if not isinstance(row, dict) or row.get('fiscal_year') != '2014/03':
                continue
            if (row.get('sales'), row.get('total_assets'), row.get('operating_cf')) in {
                (62268, 27566, 5179), (62268000000, 27566000000, 5179000000),
            }:
                matches.append('E02938/2014/03')
    return matches


def find_candidates(results: list[dict[str, Any]], minimum: int = 3, *, fields: tuple[str, ...] = FIELDS) -> list[dict[str, Any]]:
    if minimum < 1:
        raise ValueError("minimum must be positive")
    fingerprints: dict[tuple[Any, ...], set[tuple[str, str]]] = defaultdict(set)
    for result in results:
        code = str(result.get("edinet_code") or "").strip()
        if not code:
            continue
        for row in result.get("periods", []):
            if not isinstance(row, dict):
                continue
            period = str(row.get("fiscal_year") or "").strip()
            values = tuple(row.get(field) for field in fields)
            if not period or any(not isinstance(v, (int, float)) or isinstance(v, bool)
                                 or not math.isfinite(v) for v in values):
                continue
            fingerprints[values].add((code, period))
    pairs: dict[tuple[str, str], set[tuple[str, str]]] = defaultdict(set)
    for entries in fingerprints.values():
        for left, right in combinations(sorted(entries), 2):
            if left[0] == right[0]:
                continue
            pairs[(left[0], right[0])].add((left[1], right[1]))
    return [
        {"edinet_codes": list(pair), "matching_periods": [list(period) for period in sorted(periods)],
         "matching_count": len(periods)}
        for pair, periods in sorted(pairs.items()) if len(periods) >= minimum
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="Directory of saved annual batch_*.json responses")
    parser.add_argument("--minimum", type=int, default=3)
    parser.add_argument('--profile', choices=PROFILES, default='core')
    args = parser.parse_args()
    paths = sorted(args.directory.glob("batch_*.json"))
    if not paths:
        parser.error("no annual batch files found")
    results = []
    for path in paths:
        with path.open(encoding="utf-8-sig") as handle:
            document = json.load(handle)
        if not isinstance(document, dict) or not isinstance(document.get("results"), list):
            raise ValueError(f"invalid batch: {path.name}")
        results.extend(document["results"])
    print(json.dumps({"files": len(paths), "results": len(results), "profile": args.profile,
                      "known_partial_contamination": sorted(set(find_known_partial_contamination(results))),
                      "candidates": find_candidates(results, args.minimum, fields=PROFILES[args.profile])}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
