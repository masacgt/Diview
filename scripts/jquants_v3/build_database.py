#!/usr/bin/env python3
"""Build a local J-Quants-only v3 SQLite snapshot from backed-up R2 CSVs."""

from __future__ import annotations

import argparse
import csv
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sqlite3
import sys
from typing import Any, TextIO


RAW_FIELDS = [
    "Date", "Code", "O", "H", "L", "C", "UL", "LL", "Vo", "Va",
    "AdjFactor", "AdjO", "AdjH", "AdjL", "AdjC", "AdjVo", "MktCap", "ExRT",
]
ADJUSTED_FIELDS = ["Date", "Code", "AdjFactor", "AdjO", "AdjH", "AdjL", "AdjC", "AdjVo"]
NUMERIC_FIELDS = {
    "O", "H", "L", "C", "UL", "LL", "Vo", "Va", "AdjFactor",
    "AdjO", "AdjH", "AdjL", "AdjC", "AdjVo", "MktCap",
}
ADJUSTED_NUMERIC_FIELDS = ["AdjFactor", "AdjO", "AdjH", "AdjL", "AdjC", "AdjVo"]
INSERT_SQL = """
INSERT INTO daily_quotes (
  quote_date, code, o, h, l, c, upper_limit, lower_limit, volume, turnover_value,
  adjustment_factor, adjusted_open, adjusted_high, adjusted_low, adjusted_close,
  adjusted_volume, market_cap, ex_rt
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
"""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def number_or_none(raw: str | None, *, field: str, row_number: int) -> int | float | None:
    value = (raw or "").strip()
    if not value:
        return None
    try:
        number = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError(f"Invalid number in {field} at CSV row {row_number}: {value!r}") from exc
    if not number.is_finite():
        raise ValueError(f"Non-finite number in {field} at CSV row {row_number}")
    if number == number.to_integral_value():
        return int(number)
    result = float(number)
    if not math.isfinite(result):
        raise ValueError(f"Number out of SQLite range in {field} at CSV row {row_number}")
    return result


def decimal_or_none(raw: str | None, *, field: str, row_number: int) -> Decimal | None:
    value = (raw or "").strip()
    if not value:
        return None
    try:
        result = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError(f"Invalid number in {field} at CSV row {row_number}: {value!r}") from exc
    if not result.is_finite():
        raise ValueError(f"Non-finite number in {field} at CSV row {row_number}")
    return result


def validate_manifest(manifest_path: Path, daily_path: Path, adjusted_path: Path) -> dict[str, Any]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    if manifest.get("provider") != "J-Quants API" or manifest.get("dataset") != "daily_quotes":
        raise ValueError("The manifest is not for the J-Quants daily_quotes dataset")
    if manifest.get("proposed_r2_bucket") != "diview-market-data":
        raise ValueError("Unexpected R2 bucket in the J-Quants manifest")

    expected_files = [
        (daily_path, manifest.get("file", {}), manifest.get("fields")),
        (adjusted_path, manifest.get("adjusted_api_file", {}), manifest.get("adjusted_api_file", {}).get("fields")),
    ]
    for path, record, manifest_fields in expected_files:
        if not path.is_file():
            raise FileNotFoundError(path)
        if path.stat().st_size != int(record.get("bytes", -1)):
            raise ValueError(f"Byte size does not match the manifest: {path.name}")
        if sha256_file(path) != str(record.get("sha256", "")).lower():
            raise ValueError(f"SHA-256 does not match the manifest: {path.name}")
        expected_fields = RAW_FIELDS if path == daily_path else ADJUSTED_FIELDS
        if manifest_fields != expected_fields:
            raise ValueError(f"Unexpected manifest columns for {path.name}")

    if int(manifest.get("records", -1)) != int(manifest["adjusted_api_file"].get("records", -2)):
        raise ValueError("Raw and adjusted record counts differ in the manifest")
    return manifest


def read_header(path: Path, expected: list[str]) -> tuple[TextIO, csv.DictReader]:
    stream = path.open("r", encoding="utf-8-sig", newline="")
    reader = csv.DictReader(stream)
    if reader.fieldnames != expected:
        stream.close()
        raise ValueError(f"Unexpected CSV header in {path.name}: {reader.fieldnames!r}")
    return stream, reader


def compare_adjusted_row(raw: dict[str, str], adjusted: dict[str, str], row_number: int) -> None:
    for field in ("Date", "Code"):
        if raw[field].strip() != adjusted[field].strip():
            raise ValueError(f"Adjusted CSV {field} mismatch at data row {row_number}")
    for field in ADJUSTED_NUMERIC_FIELDS:
        if decimal_or_none(raw.get(field), field=field, row_number=row_number) != decimal_or_none(
            adjusted.get(field), field=field, row_number=row_number
        ):
            raise ValueError(f"Adjusted CSV {field} mismatch at data row {row_number}")


def build_database(
    *, daily_path: Path, adjusted_path: Path, manifest_path: Path, output_path: Path, report_path: Path
) -> dict[str, Any]:
    daily_path = daily_path.resolve()
    adjusted_path = adjusted_path.resolve()
    manifest_path = manifest_path.resolve()
    output_path = output_path.resolve()
    report_path = report_path.resolve()

    temporary_path = output_path.with_name(output_path.name + ".building")
    report_temporary = report_path.with_name(report_path.name + ".tmp")
    if output_path.exists() or report_path.exists():
        raise FileExistsError("Refusing to overwrite an existing database or report")
    if temporary_path.exists() or report_temporary.exists():
        raise FileExistsError("Refusing to overwrite an existing temporary database or report")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = validate_manifest(manifest_path, daily_path, adjusted_path)
    raw_stream, raw_reader = read_header(daily_path, RAW_FIELDS)
    try:
        adjusted_stream, adjusted_reader = read_header(adjusted_path, ADJUSTED_FIELDS)
    except Exception:
        raw_stream.close()
        raise

    connection: sqlite3.Connection | None = None
    try:
        with temporary_path.open("xb"):
            pass
        connection = sqlite3.connect(temporary_path)
        connection.execute("PRAGMA journal_mode = DELETE")
        connection.execute("PRAGMA synchronous = FULL")
        connection.execute("PRAGMA user_version = 3")
        connection.execute("PRAGMA application_id = 1146509107")  # ASCII "DVW3"
        connection.executescript(
            """
            CREATE TABLE database_metadata (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );
            CREATE TABLE source_files (
              file_name TEXT PRIMARY KEY,
              provider TEXT NOT NULL,
              r2_bucket TEXT NOT NULL,
              r2_key TEXT NOT NULL,
              sha256 TEXT NOT NULL,
              byte_size INTEGER NOT NULL,
              record_count INTEGER,
              date_from TEXT,
              date_to TEXT
            );
            CREATE TABLE daily_quotes (
              quote_date TEXT NOT NULL,
              code TEXT NOT NULL,
              o REAL,
              h REAL,
              l REAL,
              c REAL,
              upper_limit REAL,
              lower_limit REAL,
              volume REAL,
              turnover_value REAL,
              adjustment_factor REAL,
              adjusted_open REAL,
              adjusted_high REAL,
              adjusted_low REAL,
              adjusted_close REAL,
              adjusted_volume REAL,
              market_cap REAL,
              ex_rt TEXT,
              PRIMARY KEY (quote_date, code)
            );
            CREATE VIEW adjusted_daily_quotes AS
              SELECT quote_date AS Date,
                     code AS Code,
                     adjustment_factor AS AdjFactor,
                     adjusted_open AS AdjO,
                     adjusted_high AS AdjH,
                     adjusted_low AS AdjL,
                     adjusted_close AS AdjC,
                     adjusted_volume AS AdjVo
              FROM daily_quotes;
            """
        )

        provider = str(manifest["provider"])
        bucket = str(manifest["proposed_r2_bucket"])
        date_from = str(manifest["date_from"])
        date_to = str(manifest["date_to"])
        source_rows = [
            (
                daily_path.name, provider, bucket, str(manifest["proposed_r2_key"]),
                str(manifest["file"]["sha256"]).lower(), int(manifest["file"]["bytes"]),
                int(manifest["records"]), date_from, date_to,
            ),
            (
                adjusted_path.name, provider, bucket, str(manifest["adjusted_api_file"]["r2_key"]),
                str(manifest["adjusted_api_file"]["sha256"]).lower(),
                int(manifest["adjusted_api_file"]["bytes"]),
                int(manifest["adjusted_api_file"]["records"]), date_from, date_to,
            ),
            (
                manifest_path.name, provider, bucket, str(manifest["manifest_key"]),
                sha256_file(manifest_path), manifest_path.stat().st_size, None, date_from, date_to,
            ),
        ]
        connection.executemany(
            "INSERT INTO source_files VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", source_rows
        )
        metadata = {
            "schema_version": "3",
            "provider": provider,
            "dataset": "daily_quotes",
            "source_fetched_at": str(manifest["fetched_at"]),
            "date_from": date_from,
            "date_to": date_to,
            "source_security_count": str(manifest["securities"]),
            "source_manifest_sha256": sha256_file(manifest_path),
        }
        connection.executemany("INSERT INTO database_metadata VALUES (?, ?)", metadata.items())

        expected_records = int(manifest["records"])
        actual_records = 0
        batch: list[tuple[Any, ...]] = []
        for row_number, raw in enumerate(raw_reader, start=1):
            if None in raw or any(raw.get(field) is None for field in RAW_FIELDS):
                raise ValueError(f"Malformed daily_quotes.csv row {row_number}")
            adjusted = next(adjusted_reader, None)
            if adjusted is None:
                raise ValueError("Adjusted CSV ended before the raw CSV")
            compare_adjusted_row(raw, adjusted, row_number)

            quote_date = raw["Date"].strip()
            if date.fromisoformat(quote_date).isoformat() != quote_date:
                raise ValueError(f"Invalid quote date at CSV row {row_number}")
            code = raw["Code"].strip().upper()
            if not re.fullmatch(r"[0-9A-Z]{5}", code):
                raise ValueError(f"Invalid J-Quants code at CSV row {row_number}: {code!r}")

            values: list[Any] = [quote_date, code]
            values.extend(
                number_or_none(raw.get(field), field=field, row_number=row_number)
                if field in NUMERIC_FIELDS else (raw.get(field) or None)
                for field in RAW_FIELDS[2:]
            )
            batch.append(tuple(values))
            actual_records += 1
            if len(batch) >= 10_000:
                connection.executemany(INSERT_SQL, batch)
                connection.commit()
                batch.clear()
            if actual_records % 500_000 == 0:
                print(f"Imported {actual_records:,}/{expected_records:,} J-Quants rows", flush=True)

        if batch:
            connection.executemany(INSERT_SQL, batch)
            connection.commit()
        if actual_records != expected_records:
            raise ValueError(f"Expected {expected_records:,} daily rows; imported {actual_records:,}")
        if next(adjusted_reader, None) is not None:
            raise ValueError("Adjusted CSV contains more rows than the raw CSV")

        connection.execute("CREATE INDEX idx_daily_quotes_code_date ON daily_quotes(code, quote_date DESC)")
        connection.execute("CREATE INDEX idx_daily_quotes_date ON daily_quotes(quote_date)")
        connection.commit()

        integrity = str(connection.execute("PRAGMA integrity_check").fetchone()[0])
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity_check failed: {integrity}")
        row_count, security_count, min_date, max_date = connection.execute(
            "SELECT COUNT(*), COUNT(DISTINCT code), MIN(quote_date), MAX(quote_date) FROM daily_quotes"
        ).fetchone()
        if int(row_count) != expected_records or int(security_count) != int(manifest["securities"]):
            raise ValueError("Imported row/security counts do not match the J-Quants manifest")
        if min_date != date_from or max_date != date_to:
            raise ValueError("Imported date range does not match the J-Quants manifest")
        connection.close()
        connection = None

        if output_path.exists():
            raise FileExistsError(f"Refusing to overwrite an existing database: {output_path}")
        os.replace(temporary_path, output_path)

        report = {
            "schema_version": "3",
            "provider": provider,
            "dataset": "daily_quotes",
            "database": str(output_path),
            "database_bytes": output_path.stat().st_size,
            "database_sha256": sha256_file(output_path),
            "integrity_check": integrity,
            "records": int(row_count),
            "securities": int(security_count),
            "date_from": min_date,
            "date_to": max_date,
            "source_fetched_at": str(manifest["fetched_at"]),
            "source_objects": [item[3] for item in source_rows],
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        if report_temporary.exists():
            raise FileExistsError(f"Refusing to overwrite an existing report build file: {report_temporary}")
        with report_temporary.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        if report_path.exists():
            raise FileExistsError(f"Refusing to overwrite an existing report: {report_path}")
        os.replace(report_temporary, report_path)
        return report
    except Exception:
        if connection is not None:
            connection.close()
        if temporary_path.exists():
            temporary_path.unlink()
        journal_path = Path(str(temporary_path) + "-journal")
        if journal_path.exists():
            journal_path.unlink()
        if report_temporary.exists():
            report_temporary.unlink()
        raise
    finally:
        raw_stream.close()
        adjusted_stream.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--daily-quotes", type=Path, required=True, help="Backed-up J-Quants daily_quotes.csv")
    parser.add_argument("--adjusted-daily-quotes", type=Path, required=True, help="Backed-up adjusted subset CSV")
    parser.add_argument("--manifest", type=Path, required=True, help="Backed-up J-Quants manifest.json")
    parser.add_argument("--output", type=Path, required=True, help="New SQLite database path; existing files are preserved")
    parser.add_argument("--report", type=Path, help="Build report path (defaults beside the SQLite file)")
    args = parser.parse_args()
    report_path = args.report or args.output.with_name(args.output.stem + "_report.json")
    report = build_database(
        daily_path=args.daily_quotes,
        adjusted_path=args.adjusted_daily_quotes,
        manifest_path=args.manifest,
        output_path=args.output,
        report_path=report_path,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"v3 database build failed: {exc}", file=sys.stderr)
        raise
