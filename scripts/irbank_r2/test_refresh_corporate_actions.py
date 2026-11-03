import unittest
from datetime import date

from refresh_corporate_actions import extract_section_actions, normalize_raw_dividends


class CorporateActionExtractionTests(unittest.TestCase):
    def test_extracts_effective_dates_and_ignores_board_dates(self):
        sections = [{
            "section_id": None,
            "section_title_raw": "記載事項",
            "html_or_text": (
                "2023年9月1日開催の臨時株主総会決議により、同年10月2日付で普通株式 "
                "20,000,000株につき1株の割合で株式併合を行いました。"
                "2024年2月22日開催の取締役会決議により、同年3月15日付で普通株式 "
                "1株につき6株の割合で株式分割を行いました。"
                "2025年7月11日開催の取締役会決議により、同年7月27日付で普通株式 "
                "1株につき14,000,000株の割合で株式分割を行いました。"
            ),
        }]
        events = extract_section_actions(sections, {
            "doc_id": "S1234567", "source": "edinet", "filing_date": "2026-06-19",
        })
        self.assertEqual(
            [(row["effective_date"], row["ratio"], row["action_type"]) for row in events],
            [
                ("2023-10-02", 5e-8, "reverse_split"),
                ("2024-03-15", 6.0, "split"),
                ("2025-07-27", 14_000_000.0, "split"),
            ],
        )
        self.assertEqual(events[0]["sources"][0]["doc_id"], "S1234567")

    def test_ratio_direction_classifies_consolidation(self):
        events = extract_section_actions([{
            "html_or_text": "2017年10月1日付で普通株式10株につき1株の割合で株式併合を実施しました。",
        }], {})
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]["ratio"], 0.1)
        self.assertEqual(events[0]["action_type"], "reverse_split")

    def test_normalizes_raw_dividends_once_to_latest_share_basis(self):
        actions = [
            {"effective_date": "2023-10-02", "ratio": 5e-8},
            {"effective_date": "2024-03-15", "ratio": 6},
            {"effective_date": "2025-07-27", "ratio": 14_000_000},
        ]
        rows = normalize_raw_dividends([
            {"date": "2022-03-30", "amount": 12},
            {"date": "2026-03-30", "amount": 42},
        ], actions, date(2026, 11, 3), date(2017, 1, 1))
        self.assertAlmostEqual(rows[0]["amount"], 12 / 4.2, places=7)
        self.assertEqual(rows[0]["share_adjustment_factor"], 4.2)
        self.assertEqual(rows[1]["amount"], 42)
        self.assertEqual(rows[1]["share_adjustment_factor"], 1)

    def test_conflicting_values_on_one_ex_date_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "Conflicting raw dividends"):
            normalize_raw_dividends([
                {"date": "2026-03-30", "amount": 42},
                {"date": "2026-03-30", "amount": 4_200_000_000_000},
            ], [], date(2026, 11, 3), date(2017, 1, 1))


if __name__ == "__main__":
    unittest.main()
