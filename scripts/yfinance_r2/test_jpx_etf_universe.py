from datetime import date
import unittest
from unittest.mock import Mock, patch

from irbank_universe import (
    IRBANK_UNIVERSE_SOURCE,
    JPX_ETF_SOURCE,
    JPX_ETF_SOURCE_URL,
    build_snapshot,
    merge_jpx_etfs,
    normalize_securities,
    validate_snapshot,
)
from jpx_etf_universe import MIN_JPX_ETF_COUNT, parse_jpx_etf_html
import sync_v2 as sync


def issue_row(listing_date: str, code: str) -> str:
    return f"<tr><td>{listing_date}</td><td>Index</td><td>{code}</td></tr>"


def etf_page(rows: str) -> str:
    return (
        "<html><table><tr><th>Listing Date</th><th>Index</th><th>Code</th></tr>"
        f"{rows}</table></html>"
    )


class JpxEtfUniverseTests(unittest.TestCase):
    def test_parser_includes_listed_and_excludes_future_issues(self):
        rows = issue_row("Oct. 15, 2026", "650A") + issue_row("Nov. 30, 2026", "671A")
        parsed = parse_jpx_etf_html(etf_page(rows), as_of=date(2026, 11, 3), minimum_count=1)
        self.assertEqual(parsed, [{"security_code_display": "650A", "listing_date": "2026-10-15"}])

    def test_parser_rejects_missing_table_malformed_date_and_duplicate_codes(self):
        with self.assertRaisesRegex(ValueError, "Could not find"):
            parse_jpx_etf_html("<table><tr><th>Symbol</th></tr></table>", as_of=date(2026, 11, 3))
        with self.assertRaisesRegex(ValueError, "unrecognized listing date"):
            parse_jpx_etf_html(etf_page(issue_row("soon", "650A")), as_of=date(2026, 11, 3))
        duplicated = issue_row("Oct. 15, 2026", "650A") * 2
        with self.assertRaisesRegex(ValueError, "duplicate code"):
            parse_jpx_etf_html(etf_page(duplicated), as_of=date(2026, 11, 3))

    def test_parser_fails_closed_on_suspiciously_small_universe(self):
        rows = "".join(issue_row("Jan. 01, 2020", f"{1000 + i}") for i in range(MIN_JPX_ETF_COUNT - 1))
        with self.assertRaisesRegex(ValueError, "only 349 currently listed"):
            parse_jpx_etf_html(etf_page(rows), as_of=date(2026, 11, 3))

    def test_merge_adds_missing_etfs_and_tags_existing_irbank_etfs(self):
        irbank = normalize_securities([{"code": "650A0"}, {"code": "13010"}], minimum_security_count=1)
        merged = merge_jpx_etfs(irbank, [
            {"security_code_display": "650A", "listing_date": "2026-10-15"},
            {"security_code_display": "651A", "listing_date": "2026-10-22"},
        ])
        by_code = {row["security_code_display"]: row for row in merged}
        self.assertEqual(by_code["650A"]["irbank_source_code"], "650A0")
        self.assertEqual(by_code["650A"]["jquants_product_category"], "014")
        self.assertIn(IRBANK_UNIVERSE_SOURCE, by_code["650A"]["sources"])
        self.assertIn(JPX_ETF_SOURCE, by_code["650A"]["sources"])
        self.assertNotIn("irbank_source_code", by_code["651A"])
        self.assertNotIn("security_type", by_code["1301"])

    def test_snapshot_validates_source_category_listing_date_and_union_hash(self):
        rows = merge_jpx_etfs(
            normalize_securities([{"code": "13010"}], minimum_security_count=1),
            [{"security_code_display": f"{4000 + i}", "listing_date": "2026-01-05"}
             for i in range(MIN_JPX_ETF_COUNT)],
        )
        snapshot = build_snapshot(rows, "2026-11-03T00:00:00Z", jpx_etf_count=MIN_JPX_ETF_COUNT)
        validated = validate_snapshot(snapshot, minimum_security_count=1)
        self.assertEqual(len(validated), MIN_JPX_ETF_COUNT + 1)
        self.assertEqual(validated[1]["jquants_product_category"], "014")
        self.assertEqual(validated[1]["jpx_source_url"], JPX_ETF_SOURCE_URL)
        self.assertEqual(snapshot["supplemental_sources"][0]["source"], JPX_ETF_SOURCE)
        invalid = dict(snapshot, security_code_sha256="invalid")
        with self.assertRaisesRegex(ValueError, "code hash"):
            validate_snapshot(invalid, minimum_security_count=1)

    def test_legacy_snapshot_is_allowed_only_for_refresh_migration(self):
        rows = normalize_securities([{"code": f"{1000 + i}"} for i in range(3500)])
        legacy = build_snapshot(rows, "2026-11-01T00:00:00Z")
        legacy["schema_version"] = 1
        legacy.pop("supplemental_sources")
        with self.assertRaisesRegex(ValueError, "unsupported schema"):
            validate_snapshot(legacy)
        self.assertEqual(len(validate_snapshot(legacy, allow_legacy=True)), 3500)

    def test_daily_universe_persists_jpx_source_and_product_metadata(self):
        irbank = normalize_securities([{"code": "650A0"}], minimum_security_count=1)
        rows = merge_jpx_etfs(irbank, [{"security_code_display": "650A", "listing_date": "2026-10-15"}])
        with patch.object(sync, "read_json", return_value={"securities": []}):
            daily = sync.active_universe(Mock(), "bucket", rows, date(2026, 11, 3))
        by_code = {row["code"]: row for row in daily}
        self.assertEqual(by_code["650A"]["security_type"], "etf")
        self.assertEqual(by_code["650A"]["jquants_product_category"], "014")
        self.assertIn(JPX_ETF_SOURCE, by_code["650A"]["sources"])
        self.assertEqual(by_code["650A"]["jpx_listing_date"], "2026-10-15")
        self.assertEqual(by_code["650A"]["jpx_source_url"], JPX_ETF_SOURCE_URL)

    def test_daily_universe_preserves_existing_etf_and_reit_product_categories(self):
        previous = {"securities": [
            {"code": "1343", "security_type": "etf", "jquants_product_category": "014"},
            {"code": "8951", "security_type": "reit", "jquants_product_category": "013"},
        ]}
        irbank = normalize_securities(
            [{"code": "13430"}, {"code": "89510"}], minimum_security_count=1
        )
        with patch.object(sync, "read_json", return_value=previous):
            daily = sync.active_universe(Mock(), "bucket", irbank, date(2026, 11, 3))

        by_code = {row["code"]: row for row in daily}
        self.assertEqual(by_code["1343"]["security_type"], "etf")
        self.assertEqual(by_code["1343"]["jquants_product_category"], "014")
        self.assertEqual(by_code["8951"]["security_type"], "reit")
        self.assertEqual(by_code["8951"]["jquants_product_category"], "013")


if __name__ == "__main__":
    unittest.main()
