"""Write the current J-Quants v2 listed issue codes for one workflow run."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ENDPOINT = "https://api.jquants.com/v2/equities/master"
SECURITY_TYPES = {
    "011": "equity",
    "012": "equity",
    "013": "reit",
    "014": "etf",
}


def display_code(value: object) -> str:
    code = str(value).strip().upper()
    if not re.fullmatch(r"[0-9A-Z]{5}", code):
        raise ValueError(f"Unexpected J-Quants issue code: {value!r}")
    # J-Quants uses a fifth, zero suffix for ordinary four-character TSE codes.
    return code[:4] if code.endswith("0") else code


def fetch_securities(api_key: str, *, snapshot_path: Path | None = None) -> list[dict[str, str]]:
    securities: dict[str, tuple[str, str]] = {}
    response_dates: set[str] = set()
    cursor: str | None = None
    seen_cursors: set[str] = set()
    while True:
        url = ENDPOINT + ("?" + urlencode({"pagination_key": cursor}) if cursor else "")
        for attempt in range(4):
            try:
                request = Request(url, headers={"x-api-key": api_key, "Accept": "application/json"})
                with urlopen(request, timeout=30) as response:
                    payload = json.load(response)
                break
            except HTTPError as exc:
                if exc.code not in (429, 500, 502, 503, 504) or attempt == 3:
                    raise RuntimeError(f"J-Quants master request failed: HTTP {exc.code}") from exc
            except (URLError, TimeoutError) as exc:
                if attempt == 3:
                    raise RuntimeError("J-Quants master request failed after retries") from exc
            time.sleep(2 ** attempt)
        if not isinstance(payload, dict) or not isinstance(payload.get("data"), list):
            raise RuntimeError("J-Quants master response has no data array")
        for row in payload["data"]:
            if not isinstance(row, dict) or "Code" not in row:
                raise RuntimeError("J-Quants master response contains an invalid issue row")
            response_date = str(row.get("Date") or "").strip()
            if response_date:
                if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", response_date):
                    raise RuntimeError(f"Unexpected J-Quants master response date: {response_date!r}")
                response_dates.add(response_date)
            code = display_code(row["Code"])
            if code in securities:
                raise RuntimeError(f"Duplicate issue code in J-Quants master: {code}")
            industry = str(row.get("S33Nm") or "").strip()
            product_category = str(row.get("ProdCat") or "").strip()
            securities[code] = (industry, product_category)
        cursor = payload.get("pagination_key")
        if not cursor:
            break
        if not isinstance(cursor, str) or cursor in seen_cursors:
            raise RuntimeError("Invalid or repeated J-Quants pagination key")
        seen_cursors.add(cursor)
    if len(securities) < 1000:
        raise RuntimeError(f"J-Quants master returned only {len(securities)} issues; refusing partial universe")
    rows = [{
        "security_code_display": code,
        "sector_name": securities[code][0],
        "jquants_product_category": securities[code][1],
        "security_type": SECURITY_TYPES.get(securities[code][1], "listed_security"),
    } for code in sorted(securities)]
    if snapshot_path is not None:
        codes = [row["security_code_display"] for row in rows]
        report = {
            "schema_version": 1,
            "source": "J-Quants API v2 /equities/master",
            "response_dates": sorted(response_dates),
            "security_count": len(codes),
            "security_code_sha256": hashlib.sha256("\n".join(codes).encode("utf-8")).hexdigest(),
            "security_codes": codes,
        }
        snapshot_path.parent.mkdir(parents=True, exist_ok=True)
        temporary = snapshot_path.with_suffix(snapshot_path.suffix + ".tmp")
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        temporary.replace(snapshot_path)
    return rows


def fetch_codes(api_key: str) -> list[str]:
    """Compatibility helper for callers that only need the listed issue codes."""
    return [row["security_code_display"] for row in fetch_securities(api_key)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    api_key = os.environ.get("JQUANTS_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("JQUANTS_API_KEY is required")
    securities = fetch_securities(api_key)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(args.output.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["security_code_display", "sector_name", "jquants_product_category", "security_type"])
        writer.writerows((row["security_code_display"], row["sector_name"],
                          row["jquants_product_category"], row["security_type"]) for row in securities)
    temporary.replace(args.output)
    print(f"Saved {len(securities)} J-Quants listed issues to {args.output}")


if __name__ == "__main__":
    main()
