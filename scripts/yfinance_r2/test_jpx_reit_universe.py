from datetime import date
import unittest

from irbank_universe import (
    IRBANK_UNIVERSE_SOURCE,
    JPX_REIT_SOURCE,
    JPX_REIT_SOURCE_URL,
    build_snapshot,
    merge_jpx_reits,
    normalize_securities,
    validate_snapshot,
)
from jpx_reit_universe import MIN_JPX_REIT_COUNT, parse_jpx_reit_html
from unittest.mock import Mock, patch
import sync_v2 as sync


def reit_row(listing_date: str, code: str) -> str:
    return f"<tr><td>{listing_date}</td><td>Example REIT</td><td><a>{code}(JP0000000000)</a></td></tr>"


def reit_page(rows: str) -> str:
    return (
        "<html><table><tr><th>Listing Date</th><th>Issue Name</th><th>Code<br>(ISIN Code)</th></tr>"
        f"{rows}</table></html>"
    )


class JpxReitUniverseTests(unittest.TestCase):
    def test_parser_excludes_future_listing_and_reads_alphanumeric_codes(self):
        html = reit_page(reit_row("Aug. 13, 2025", "401A") + reit_row("Nov. 25, 2026", "666A"))
        self.assertEqual(
            parse_jpx_reit_html(html, as_of=date(2026, 11, 3), minimum_count=1),
            [{"security_code_display": "401A", "listing_date": "2025-08-13"}],
        )

    def test_parser_rejects_bad_table_date_duplicate_code_and_small_list(self):
        with self.assertRaisesRegex(ValueError, "Could not find"):
            parse_jpx_reit_html("<table><tr><th>Symbol</th></tr></table>", as_of=date(2026, 11, 3))
        with self.assertRaisesRegex(ValueError, "unrecognized listing date"):
            parse_jpx_reit_html(reit_page(reit_row("soon", "401A")), as_of=date(2026, 11, 3))
        with self.assertRaisesRegex(ValueError, "duplicate code"):
            parse_jpx_reit_html(reit_page(reit_row("Aug. 13, 2025", "401A") * 2), as_of=date(2026, 11, 3), minimum_count=1)
        rows = "".join(reit_row("Jan. 01, 2020", f"{1000 + i}") for i in range(MIN_JPX_REIT_COUNT - 1))
        with self.assertRaisesRegex(ValueError, "only 49 currently listed"):
            parse_jpx_reit_html(reit_page(rows), as_of=date(2026, 11, 3))

    def test_merge_tags_existing_and_adds_new_reit_with_official_provenance(self):
        irbank = normalize_securities([{"code": "401A0"}, {"code": "13010"}], minimum_security_count=1)
        reits = [
            {"security_code_display": f"{1000 + i}", "listing_date": "2020-01-02"}
            for i in range(MIN_JPX_REIT_COUNT)
        ]
        reits[0] = {"security_code_display": "401A", "listing_date": "2025-08-13"}
        merged = merge_jpx_reits(irbank, reits)
        by_code = {row["security_code_display"]: row for row in merged}
        self.assertEqual(by_code["401A"]["security_type"], "reit")
        self.assertEqual(by_code["401A"]["jquants_product_category"], "013")
        self.assertEqual(by_code["401A"]["irbank_source_code"], "401A0")
        self.assertIn(IRBANK_UNIVERSE_SOURCE, by_code["401A"]["sources"])
        self.assertIn(JPX_REIT_SOURCE, by_code["401A"]["sources"])
        added = next(code for code in by_code if code != "401A" and code != "1301")
        self.assertEqual(by_code[added]["security_type"], "reit")
        self.assertEqual(by_code[added]["jpx_reit_source_url"], JPX_REIT_SOURCE_URL)

    def test_snapshot_validates_official_reit_count_and_row_metadata(self):
        reits = [
            {"security_code_display": f"{1000 + i}", "listing_date": "2020-01-02"}
            for i in range(MIN_JPX_REIT_COUNT)
        ]
        rows = merge_jpx_reits(normalize_securities([{"code": "13010"}], minimum_security_count=1), reits)
        snapshot = build_snapshot(rows, "2026-11-03T00:00:00Z", jpx_etf_count=0, jpx_reit_count=len(reits))
        validated = validate_snapshot(snapshot, minimum_security_count=1, minimum_jpx_etf_count=0)
        self.assertEqual(len(validated), MIN_JPX_REIT_COUNT + 1)
        self.assertEqual(validated[0]["jquants_product_category"], "013")
        self.assertEqual(snapshot["supplemental_sources"][1]["source"], JPX_REIT_SOURCE)
        invalid = dict(snapshot, supplemental_sources=snapshot["supplemental_sources"][:-1])
        self.assertEqual(len(validate_snapshot(invalid, minimum_security_count=1, minimum_jpx_etf_count=0)), len(validated))
        malformed = {**snapshot, "securities": [dict(row) for row in snapshot["securities"]]}
        reit_row = next(row for row in malformed["securities"] if JPX_REIT_SOURCE in row.get("sources", []))
        reit_row["jpx_reit_source_url"] = "https://invalid.example/"
        with self.assertRaisesRegex(ValueError, "unknown JPX REIT source URL"):
            validate_snapshot(malformed, minimum_security_count=1, minimum_jpx_etf_count=0)

    def test_daily_universe_persists_reit_category_and_source_metadata(self):
        reits = [
            {"security_code_display": f"{1000 + i}", "listing_date": "2020-01-02"}
            for i in range(MIN_JPX_REIT_COUNT)
        ]
        reits[0] = {"security_code_display": "401A", "listing_date": "2025-08-13"}
        merged = merge_jpx_reits(normalize_securities([{"code": "401A0"}], minimum_security_count=1), reits)
        with patch.object(sync, "read_json", return_value={"securities": []}):
            daily = sync.active_universe(Mock(), "bucket", merged, date(2026, 11, 3))
        row = next(item for item in daily if item["code"] == "401A")
        self.assertEqual(row["security_type"], "reit")
        self.assertEqual(row["jquants_product_category"], "013")
        self.assertEqual(row["jpx_reit_listing_date"], "2025-08-13")
        self.assertEqual(row["jpx_reit_source_url"], JPX_REIT_SOURCE_URL)


if __name__ == "__main__":
    unittest.main()
