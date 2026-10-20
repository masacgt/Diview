import importlib.util
from pathlib import Path
import csv
import sqlite3
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("repair", Path(__file__).with_name("plan-financial-repair.py"))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


class FinancialRepairTest(unittest.TestCase):
    def test_collision_recovery_preserves_valid_rows_and_replays(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            snapshot = root / "backup.sql"
            snapshot.write_text("""
CREATE TABLE financial_periods (security_code TEXT,period_type TEXT,fiscal_year TEXT,accounting_standard TEXT,is_consolidated INTEGER,data_json TEXT,
PRIMARY KEY(security_code,period_type,fiscal_year,accounting_standard,is_consolidated));
INSERT INTO financial_periods VALUES ('45020','annual',2026/03,'IFRS',1,'{"fiscal_year":"2026/03","sales":10}');
INSERT INTO financial_periods VALUES ('45020','annual','2025/03','IFRS',1,'{"fiscal_year":"2025/03","sales":99}');
""")
            source = root / "source.csv"
            with source.open("w", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(repair.COLS)
                for year in (2025, 2026):
                    writer.writerow(["45020", "annual", f"{year}/03", "IFRS", "true",
                                     f'{{"fiscal_year":"{year}/03","sales":10}}'])
            groups, report = repair.plan(snapshot, source)
            self.assertEqual(report["corrupt_rows"], 1)
            self.assertEqual(report["annual_rows_after"], 2)
            db = sqlite3.connect(":memory:")
            db.executescript(snapshot.read_text())
            db.executescript("\n".join(groups))
            self.assertEqual(db.execute("SELECT json_extract(data_json,'$.sales') FROM financial_periods WHERE fiscal_year='2025/03'").fetchone()[0], 99)
            # A changed source survivor must stop planning, not be guessed.
            snapshot.write_text(snapshot.read_text().replace('"sales":10', '"sales":11'))
            with self.assertRaisesRegex(ValueError, "disagree"):
                repair.plan(snapshot, source)


if __name__ == "__main__":
    unittest.main()
