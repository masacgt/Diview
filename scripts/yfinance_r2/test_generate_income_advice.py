import unittest
from unittest.mock import patch

import generate_income_advice


class GenerateIncomeAdviceTests(unittest.TestCase):
    def test_jp_jpy_candidates_keep_existing_eligibility(self):
        catalog = {
            "generation": "2026-11-04-test",
            "market_data_date": "2026-11-04",
            "items": [
                {
                    "code": "1343",
                    "name": "ETF",
                    "category": "jp",
                    "currency": "JPY",
                    "security_type": "ETF",
                },
                {
                    "code": "7203",
                    "name": "Equity",
                    "category": "jp",
                    "currency": "JPY",
                    "security_type": "equity",
                },
                {
                    "code": "162A",
                    "name": "Foreign ETF",
                    "category": "jp",
                    "currency": "JPY",
                    "security_type": "listed_security",
                    "jquants_product_category": "023",
                },
            ],
        }
        def build_candidate(_client, _bucket, row, _years, _as_of):
            return ({"code": row["code"], "name": row["name"]}, None)

        with patch.object(
            generate_income_advice,
            "build_candidate",
            side_effect=build_candidate,
        ) as build_candidate:
            result, counts = generate_income_advice.build_catalog(
                client=object(), bucket="unused", catalog=catalog, workers=1
            )

        self.assertEqual(build_candidate.call_count, 3)
        self.assertEqual(
            {call.args[2]["code"] for call in build_candidate.call_args_list},
            {"1343", "7203", "162A"},
        )
        self.assertEqual(counts["catalog_items"], 3)
        self.assertEqual(counts["eligible_currency"], 3)
        self.assertEqual(counts["candidate_items"], 3)
        self.assertEqual({item["code"] for item in result["items"]}, {"1343", "7203", "162A"})
        self.assertTrue(all(item["source_catalog_generation"] == "2026-11-04-test" for item in result["items"]))


if __name__ == "__main__":
    unittest.main()
