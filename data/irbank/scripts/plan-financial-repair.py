"""Plan a guarded repair from a D1 SQL export and the original financial CSV.

No remote calls. The original snapshot must agree with the CSV before a plan
is emitted. Apply generated chunks only after retaining the original export.
Each group inserts missing periods first and deletes its corrupt row only when
all replacement keys exist. Reruns and interruptions do not erase source rows.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import sqlite3


COLS = ("security_code", "period_type", "fiscal_year", "accounting_standard", "is_consolidated", "data_json")
PERIOD = re.compile(r"^\d{4}/(0[1-9]|1[0-2])$")


def quote(value):
    return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def predicate(row, payload=False):
    return " AND ".join(f"{col} IS {quote(row[col])}" for col in COLS[:6 if payload else 5])


def plan(snapshot, source):
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript(Path(snapshot).read_text(encoding="utf-8-sig"))
    originals = [dict(row) for row in db.execute("SELECT * FROM financial_periods WHERE period_type='annual'")]
    corrupt = [row for row in originals if row["fiscal_year"] != json.loads(row["data_json"]).get("fiscal_year")]
    corrupt_keys = {tuple(row[c] for c in COLS) for row in corrupt}
    buckets = {}
    with Path(source).open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            if row["period_type"] != "annual":
                continue
            period = row["fiscal_year"]
            if not PERIOD.fullmatch(period):
                raise ValueError("Invalid source period")
            row["is_consolidated"] = {"true": 1, "false": 0, "1": 1, "0": 0}[row["is_consolidated"]]
            row["accounting_standard"] = row["accounting_standard"] or None
            key = (row["security_code"], row["accounting_standard"], row["is_consolidated"],
                   str(int(period[:4]) // int(period[5:])))
            buckets.setdefault(key, []).append(row)
    groups = []
    for old in corrupt:
        key = (old["security_code"], old["accounting_standard"], old["is_consolidated"], str(old["fiscal_year"]))
        rows = buckets.get(key, [])
        survivor = json.loads(old["data_json"])
        if not any(json.loads(row["data_json"]) == survivor for row in rows):
            raise ValueError(f"Snapshot and source disagree; stop repair: {old['security_code']} {old['fiscal_year']}")
        if len({tuple(row[c] for c in COLS[:5]) for row in rows}) != len(rows):
            raise ValueError("Duplicate source keys; stop repair")
        exists_old = f"EXISTS(SELECT 1 FROM financial_periods WHERE {predicate(old, True)})"
        statements = []
        for row in rows:
            statements.append(f"INSERT INTO financial_periods ({','.join(COLS)}) SELECT "
                              + ",".join(quote(row[c]) for c in COLS)
                              + f" WHERE {exists_old} ON CONFLICT DO NOTHING;")
        complete = " AND ".join(f"EXISTS(SELECT 1 FROM financial_periods WHERE {predicate(row)})" for row in rows)
        statements.append(f"DELETE FROM financial_periods WHERE {predicate(old, True)} AND {complete};")
        groups.append("\n".join(statements))
    # Execute and replay the entire plan offline against the actual backup.
    sql = "\n".join(groups)
    db.executescript(sql)
    first = list(db.execute("SELECT * FROM financial_periods ORDER BY security_code,period_type,fiscal_year,accounting_standard,is_consolidated"))
    db.executescript(sql)
    assert first == list(db.execute("SELECT * FROM financial_periods ORDER BY security_code,period_type,fiscal_year,accounting_standard,is_consolidated"))
    remaining = db.execute("SELECT COUNT(*) FROM financial_periods WHERE period_type='annual' AND fiscal_year != json_extract(data_json,'$.fiscal_year')").fetchone()[0]
    if remaining:
        raise ValueError(f"Repair leaves {remaining} mismatches")
    # Every initially valid record, including more recent source corrections, survives.
    for row in originals:
        if tuple(row[c] for c in COLS) not in corrupt_keys:
            assert db.execute(f"SELECT COUNT(*) FROM financial_periods WHERE {predicate(row, True)}").fetchone()[0] == 1
    return groups, {"corrupt_rows": len(corrupt), "annual_rows_before": len(originals),
                    "annual_rows_after": db.execute("SELECT COUNT(*) FROM financial_periods WHERE period_type='annual'").fetchone()[0],
                    "offline_replay_verified": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    groups, report = plan(args.snapshot, args.source)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    files = []
    for index in range(0, len(groups), 500):
        name = f"repair_{index // 500:05d}.sql"
        body = "\n".join(groups[index:index + 500]) + "\n"
        encoded = body.encode("utf-8")
        (output / name).write_bytes(encoded)
        files.append({"name": name, "sha256": hashlib.sha256(encoded).hexdigest()})
    report.update({"snapshot_sha256": hashlib.sha256(Path(args.snapshot).read_bytes()).hexdigest(),
                   "source_sha256": hashlib.sha256(Path(args.source).read_bytes()).hexdigest(), "files": files})
    (output / "manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "files"}))


if __name__ == "__main__":
    main()
