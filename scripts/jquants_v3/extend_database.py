#!/usr/bin/env python3
"""Extend a local J-Quants v3 SQLite snapshot with locally acquired API files."""

from __future__ import annotations

import argparse
import csv
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import sys
from typing import Any


API_DATASETS = {
    "securities_master": ("master_20260731.csv", "/equities/master", "Date", "Code", "Code"),
    "valuation_indicators": ("valuation_20260731.csv", "/equities/valuation", "Date", "Code", "Code"),
    "financial_summary": ("financial_summary_20260731.csv", "/fins/summary", "DiscDate", "Code", "DiscNo"),
    "trading_calendar": (
        "trading_calendar_20240802_20260802.csv", "/markets/calendar", "Date", "", "Date"
    ),
}

DAILY_FIELDS = (
    "Date", "Code", "O", "H", "L", "C", "UL", "LL", "Vo", "Va", "AdjFactor",
    "AdjO", "AdjH", "AdjL", "AdjC", "AdjVo", "MktCap", "ExRT",
)
DAILY_NUMERIC_FIELDS = {
    "O", "H", "L", "C", "UL", "LL", "Vo", "Va", "AdjFactor", "AdjO", "AdjH",
    "AdjL", "AdjC", "AdjVo", "MktCap",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def number_or_none(value: str | None, *, field: str, row_number: int) -> int | float | None:
    raw = (value or "").strip()
    if not raw:
        return None
    try:
        number = Decimal(raw)
    except InvalidOperation as exc:
        raise ValueError(f"Invalid {field} at row {row_number}: {raw!r}") from exc
    if not number.is_finite():
        raise ValueError(f"Non-finite {field} at row {row_number}")
    if number == number.to_integral_value():
        return int(number)
    return float(number)


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames:
            raise ValueError(f"CSV has no header: {path.name}")
        return reader.fieldnames, list(reader)


def insert_daily_quotes(connection: sqlite3.Connection, paths: list[Path]) -> dict[str, Any]:
    inserted = 0
    duplicates = 0
    dates: set[str] = set()
    for path in paths:
        fields, rows = read_csv(path)
        if not set(DAILY_FIELDS).issubset(fields):
            raise ValueError(f"Daily quote fields are incomplete in {path.name}")
        expected_date = re.search(r"(\d{8})\.csv$", path.name)
        if not expected_date:
            raise ValueError(f"Unexpected daily file name: {path.name}")
        filename_date = datetime.strptime(expected_date.group(1), "%Y%m%d").date().isoformat()
        if not rows:
            raise ValueError(f"Daily quote file is empty: {path.name}")
        for row_number, row in enumerate(rows, start=2):
            quote_date = row.get("Date", "").strip()
            if date.fromisoformat(quote_date).isoformat() != quote_date or quote_date != filename_date:
                raise ValueError(f"Date mismatch in {path.name} row {row_number}")
            code = row.get("Code", "").strip().upper()
            if not re.fullmatch(r"[0-9A-Z]{5}", code):
                raise ValueError(f"Invalid J-Quants code in {path.name} row {row_number}: {code!r}")
            values: list[Any] = [quote_date, code]
            values.extend(
                number_or_none(row.get(field), field=field, row_number=row_number)
                if field in DAILY_NUMERIC_FIELDS else (row.get(field) or None)
                for field in DAILY_FIELDS[2:]
            )
            cursor = connection.execute(
                """INSERT OR IGNORE INTO daily_quotes (
                    quote_date, code, o, h, l, c, upper_limit, lower_limit, volume, turnover_value,
                    adjustment_factor, adjusted_open, adjusted_high, adjusted_low, adjusted_close,
                    adjusted_volume, market_cap, ex_rt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                values,
            )
            if cursor.rowcount:
                inserted += 1
            else:
                existing = connection.execute(
                    "SELECT o,h,l,c,upper_limit,lower_limit,volume,turnover_value,adjustment_factor,"
                    "adjusted_open,adjusted_high,adjusted_low,adjusted_close,adjusted_volume,market_cap,ex_rt "
                    "FROM daily_quotes WHERE quote_date=? AND code=?",
                    (quote_date, code),
                ).fetchone()
                if existing != tuple(values[2:]):
                    raise ValueError(f"Conflicting duplicate quote for {quote_date} {code}")
                duplicates += 1
            dates.add(quote_date)
        connection.execute(
            "INSERT INTO api_source_files VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                path.name, "J-Quants API", "/equities/bars/daily", sha256_file(path),
                path.stat().st_size, len(rows), filename_date, filename_date,
                datetime.now(timezone.utc).isoformat(), "CSV",
            ),
        )
    return {
        "files": len(paths), "rows_inserted": inserted, "identical_duplicate_rows": duplicates,
        "date_from": min(dates), "date_to": max(dates),
    }


def insert_api_records(connection: sqlite3.Connection, data_dir: Path) -> list[dict[str, Any]]:
    reports: list[dict[str, Any]] = []
    for dataset, (filename, endpoint, date_field, code_field, key_field) in API_DATASETS.items():
        path = data_dir / filename
        fields, rows = read_csv(path)
        if date_field not in fields or (code_field and code_field not in fields) or key_field not in fields:
            raise ValueError(f"Expected key fields are missing in {path.name}")
        if not rows:
            raise ValueError(f"API dataset file is empty: {path.name}")
        dates: set[str] = set()
        for row in rows:
            record_date = (row.get(date_field) or "").strip()
            if record_date:
                date.fromisoformat(record_date)
                dates.add(record_date)
            code = (row.get(code_field) or "").strip().upper() if code_field else ""
            record_key = (row.get(key_field) or "").strip()
            if not record_key:
                raise ValueError(f"Blank key in {path.name}")
            payload = json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
            connection.execute(
                "INSERT INTO jquants_api_records VALUES (?, ?, ?, ?, ?)",
                (dataset, record_date, code, record_key, payload),
            )
        connection.execute(
            "INSERT INTO api_source_files VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                path.name, "J-Quants API", endpoint, sha256_file(path), path.stat().st_size,
                len(rows), min(dates) if dates else None, max(dates) if dates else None,
                datetime.now(timezone.utc).isoformat(), "CSV",
            ),
        )
        reports.append({
            "dataset": dataset, "file": path.name, "records": len(rows),
            "date_from": min(dates) if dates else None, "date_to": max(dates) if dates else None,
        })
    return reports


def build_extended_database(*, base_path: Path, data_dir: Path, output_path: Path, report_path: Path) -> dict[str, Any]:
    base_path = base_path.resolve()
    data_dir = data_dir.resolve()
    output_path = output_path.resolve()
    report_path = report_path.resolve()
    building_path = output_path.with_name(output_path.name + ".building")
    report_temp = report_path.with_name(report_path.name + ".tmp")
    if not base_path.is_file():
        raise FileNotFoundError(base_path)
    if output_path.exists() or report_path.exists() or building_path.exists() or report_temp.exists():
        raise FileExistsError("Refusing to overwrite an existing output database or report")

    daily_paths = sorted(data_dir.glob("daily_quotes_????????.csv"))
    if not daily_paths:
        raise ValueError(f"No daily_quotes_YYYYMMDD.csv files found in {data_dir}")
    required_paths = [data_dir / spec[0] for spec in API_DATASETS.values()]
    for path in required_paths:
        if not path.is_file():
            raise FileNotFoundError(path)

    report_path.parent.mkdir(parents=True, exist_ok=True)
    source = sqlite3.connect(f"file:{base_path.as_posix()}?mode=ro", uri=True)
    target: sqlite3.Connection | None = None
    try:
        check = source.execute("PRAGMA integrity_check").fetchone()[0]
        if check != "ok":
            raise RuntimeError(f"Base database integrity check failed: {check}")
        provider = source.execute(
            "SELECT value FROM database_metadata WHERE key='provider'"
        ).fetchone()
        if not provider or provider[0] != "J-Quants API":
            raise ValueError("Base database does not identify J-Quants API as its provider")
        with building_path.open("xb"):
            pass
        target = sqlite3.connect(building_path)
        source.backup(target)
        target.executescript(
            """
            CREATE TABLE api_source_files (
              file_name TEXT PRIMARY KEY,
              provider TEXT NOT NULL,
              endpoint TEXT NOT NULL,
              sha256 TEXT NOT NULL,
              byte_size INTEGER NOT NULL,
              record_count INTEGER NOT NULL,
              date_from TEXT,
              date_to TEXT,
              imported_at TEXT NOT NULL,
              format TEXT NOT NULL
            );
            CREATE TABLE jquants_api_records (
              dataset TEXT NOT NULL,
              record_date TEXT NOT NULL,
              code TEXT NOT NULL,
              record_key TEXT NOT NULL,
              record_json TEXT NOT NULL,
              PRIMARY KEY (dataset, record_key)
            );
            CREATE INDEX idx_jquants_api_records_dataset_date
              ON jquants_api_records(dataset, record_date);
            CREATE INDEX idx_jquants_api_records_dataset_code_date
              ON jquants_api_records(dataset, code, record_date);
            CREATE TABLE dataset_coverage (
              dataset TEXT PRIMARY KEY,
              provider TEXT NOT NULL,
              status TEXT NOT NULL,
              date_from TEXT,
              date_to TEXT,
              record_count INTEGER,
              notes TEXT NOT NULL
            );
            CREATE TABLE monthly_price_history (
              month TEXT NOT NULL,
              code TEXT NOT NULL,
              last_quote_date TEXT NOT NULL,
              adjusted_close REAL,
              PRIMARY KEY (month, code)
            );
            """
        )

        daily_report = insert_daily_quotes(target, daily_paths)
        api_reports = insert_api_records(target, data_dir)
        target.execute(
            """INSERT INTO monthly_price_history(month, code, last_quote_date, adjusted_close)
               SELECT substr(q.quote_date, 1, 7), q.code, q.quote_date, q.adjusted_close
               FROM daily_quotes AS q
               JOIN (
                 SELECT code, substr(quote_date, 1, 7) AS month, MAX(quote_date) AS last_date
                 FROM daily_quotes GROUP BY code, substr(quote_date, 1, 7)
               ) AS last
               ON last.code=q.code AND last.month=substr(q.quote_date,1,7)
                  AND last.last_date=q.quote_date
               WHERE q.adjusted_close IS NOT NULL"""
        )
        coverage_rows = [
            (
                "daily_quotes", "J-Quants API", "available_window_complete",
                target.execute("SELECT MIN(quote_date) FROM daily_quotes").fetchone()[0],
                target.execute("SELECT MAX(quote_date) FROM daily_quotes").fetchone()[0],
                target.execute("SELECT COUNT(*) FROM daily_quotes").fetchone()[0],
                "Project-local J-Quants R2 snapshot plus API backfill through the final session in the available window.",
            ),
            (
                "securities_master", "J-Quants API", "snapshot",
                "2026-07-31", "2026-07-31",
                target.execute("SELECT COUNT(*) FROM jquants_api_records WHERE dataset='securities_master'").fetchone()[0],
                "All securities returned for the latest accessible master date; no historical master versions were fetched.",
            ),
            (
                "valuation_indicators", "J-Quants API", "partial_snapshot",
                "2026-07-31", "2026-07-31",
                target.execute("SELECT COUNT(*) FROM jquants_api_records WHERE dataset='valuation_indicators'").fetchone()[0],
                "One all-securities daily snapshot; historical valuation dates were not downloaded.",
            ),
            (
                "financial_summary", "J-Quants API", "partial_snapshot",
                "2026-07-31", "2026-07-31",
                target.execute("SELECT COUNT(*) FROM jquants_api_records WHERE dataset='financial_summary'").fetchone()[0],
                "Disclosures published on 2026-07-31 only; not the full historical disclosure archive.",
            ),
            (
                "trading_calendar", "J-Quants API", "available_window_complete",
                "2024-08-02", "2026-08-02",
                target.execute("SELECT COUNT(*) FROM jquants_api_records WHERE dataset='trading_calendar'").fetchone()[0],
                "All calendar dates in the API-accessible period.",
            ),
            (
                "monthly_price_history", "J-Quants API", "derived",
                target.execute("SELECT MIN(month) FROM monthly_price_history").fetchone()[0],
                target.execute("SELECT MAX(month) FROM monthly_price_history").fetchone()[0],
                target.execute("SELECT COUNT(*) FROM monthly_price_history").fetchone()[0],
                "Last observed daily adjusted close per security and calendar month; derived from daily_quotes.",
            ),
            (
                "cash_dividend_events", "J-Quants API", "unavailable",
                None, None, 0,
                "The current API key receives HTTP 403 for the Premium-only dividend endpoint.",
            ),
            (
                "financial_statement_details", "J-Quants API", "unavailable",
                None, None, 0,
                "The current API key receives HTTP 403 for the Premium-only BS/PL/CF details endpoint.",
            ),
            (
                "split_events", "J-Quants API", "not_provided_as_event_history",
                None, None, 0,
                "Daily adjustment factors are preserved in daily_quotes; the personal API does not supply an exact standalone split-event history.",
            ),
        ]
        target.executemany("INSERT INTO dataset_coverage VALUES (?, ?, ?, ?, ?, ?, ?)", coverage_rows)
        metadata = {
            "snapshot_version": "3.1",
            "database_scope": "J-Quants-only app market data; local snapshot, not deployed",
            "last_accessible_market_date": "2026-07-31",
            "api_subscription_window": "2024-08-02 through 2026-08-02 (inclusive; 12-week delay observed)",
            "api_key_value_saved": "false",
        }
        target.executemany(
            "INSERT OR REPLACE INTO database_metadata(key, value) VALUES (?, ?)", metadata.items()
        )
        target.execute(
            "UPDATE database_metadata SET value=(SELECT MAX(quote_date) FROM daily_quotes) WHERE key='date_to'"
        )
        target.execute(
            "UPDATE database_metadata SET value=CAST((SELECT COUNT(DISTINCT code) FROM daily_quotes) AS TEXT) "
            "WHERE key='source_security_count'"
        )
        target.commit()

        integrity = target.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"Extended database integrity check failed: {integrity}")
        daily_count, security_count, date_from, date_to = target.execute(
            "SELECT COUNT(*), COUNT(DISTINCT code), MIN(quote_date), MAX(quote_date) FROM daily_quotes"
        ).fetchone()
        monthly_count = target.execute("SELECT COUNT(*) FROM monthly_price_history").fetchone()[0]
        target.close()
        target = None
        os.replace(building_path, output_path)

        report = {
            "schema_version": "3.1",
            "provider": "J-Quants API only",
            "database": str(output_path),
            "database_bytes": output_path.stat().st_size,
            "database_sha256": sha256_file(output_path),
            "integrity_check": integrity,
            "daily_quotes": {
                **daily_report, "total_records": int(daily_count), "securities": int(security_count),
                "date_from": date_from, "date_to": date_to,
            },
            "api_datasets": api_reports,
            "monthly_price_records": int(monthly_count),
            "coverage_gaps": [row[0] for row in coverage_rows if row[2] in ("partial_snapshot", "unavailable", "not_provided_as_event_history")],
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        with report_temp.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        os.replace(report_temp, report_path)
        return report
    except Exception:
        if target is not None:
            target.close()
        raise
    finally:
        source.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-database", type=Path, required=True)
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report_path = args.report or args.output.with_name(args.output.stem + "_report.json")
    report = build_extended_database(
        base_path=args.base_database, data_dir=args.data_dir,
        output_path=args.output, report_path=report_path,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"v3 database extension failed: {exc}", file=sys.stderr)
        raise
