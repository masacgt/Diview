from contextlib import ExitStack
from copy import deepcopy
from datetime import date, datetime
import io
import json
import tempfile
from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock, patch

import pandas as pd

import benchmark_v2 as benchmark
import refresh_financial_v2 as financial_refresh
import sync_v2 as sync
import migrate_legacy_financials as financial_migration
import repair_monthly_price_dates as monthly_date_repair
import monthly_price_calendar as monthly_calendar
import audit_split_transition_dividends as split_audit


class SplitTransitionAuditTests(TestCase):
    def test_security_csv_row_excludes_internal_comparisons(self):
        row = split_audit.security_csv_row({
            "code": "7203",
            "split_events": [{"date": "2021-09-29", "ratio": 5.0}],
            "comparisons": [{"fiscal_year": 2022, "within_tolerance": True}],
        })

        self.assertEqual(set(row), set(split_audit.SECURITY_CSV_FIELDS))
        self.assertEqual(json.loads(row["split_events"]), [{"date": "2021-09-29", "ratio": 5.0}])
        self.assertNotIn("comparisons", row)

    def test_event_dates_are_grouped_by_reported_fiscal_period_ends(self):
        period_ends = [date(2020, 3, 31), date(2021, 3, 31), date(2022, 3, 31)]
        self.assertEqual(split_audit.fiscal_year_for_event(date(2020, 9, 29), period_ends), 2021)
        self.assertEqual(split_audit.fiscal_year_for_event(date(2021, 3, 30), period_ends), 2021)

    def test_split_adjusted_yfinance_events_match_adjusted_issuer_fiscal_dps(self):
        comparisons = split_audit.compare_fiscal_dividends(
            [{"event_type": "annual_reported", "fiscal_year": 2021, "annual_dps_adjusted": 48}],
            [
                {"date": "2020-09-29", "amount": 21},
                {"date": "2021-03-30", "amount": 27},
                {"date": "2021-03-30", "amount": 27},
            ],
            [date(2020, 3, 31), date(2021, 3, 31)],
            first_year=2021,
            last_year=2021,
        )
        self.assertEqual(comparisons, [{
            "fiscal_year": 2021,
            "issuer_adjusted_dps": 48.0,
            "yfinance_event_total": 48.0,
            "difference": 0.0,
            "within_tolerance": True,
        }])

    def test_positive_issuer_dps_without_events_is_reported_as_mismatch(self):
        comparisons = split_audit.compare_fiscal_dividends(
            [{"event_type": "annual_reported", "fiscal_year": 2022, "annual_dps_adjusted": 52.25}],
            [],
            [date(2021, 3, 31), date(2022, 3, 31)],
            first_year=2022,
            last_year=2022,
        )
        self.assertFalse(comparisons[0]["within_tolerance"])

    def test_recent_split_selection_excludes_non_split_share_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            events_path = Path(directory) / "events.csv"
            events_path.write_text(
                "code,effective_date,factor,event_type\n"
                "0001,2026-01-01,2,split\n"
                "0002,2026-01-01,0.1,reverse_split\n"
                "0003,2026-01-01,2,share_change\n",
                encoding="utf-8",
            )
            selected = split_audit.recent_current_split_codes(
                events_path, {"0001", "0002", "0003"}, date(2026, 10, 17),
            )

        self.assertEqual(set(selected), {"0001", "0002"})


class FinancialRefreshPartitionTests(TestCase):
    def test_shards_are_disjoint_and_cover_all_securities(self):
        securities = ["1301", "1605", "2349", "2914", "7203", "9984"]
        shards = [financial_refresh.partition_securities(securities, index, 3) for index in range(3)]

        self.assertEqual(len(set().union(*map(set, shards))), len(securities))
        self.assertEqual(sum(map(len, shards)), len(securities))
        self.assertEqual([shard for shard in shards], [["1301", "2914"], ["1605", "7203"], ["2349", "9984"]])

    def test_shard_index_must_be_in_range(self):
        with self.assertRaises(ValueError):
            financial_refresh.partition_securities(["1301"], 2, 2)


class NetDeRatioTests(TestCase):
    def test_equity_ratio_keeps_seed_ratio_when_refreshed_yfinance_input_has_a_different_unit_basis(self):
        result = sync.equity_ratio_result(
            {
                "total_assets": 105_522_331,
                "equity_ratio_computed": 37.829768942,
            },
            {"stockholders_equity": 39_918_854_000_000},
            {"total_assets": "million_jpy", "equity_ratio_computed": "percent"},
            {"stockholders_equity": "jpy"},
        )

        self.assertEqual(result["value"], 37.829768942)
        self.assertEqual(result["source"], "seed")
        self.assertEqual(result["reason"], "mixed_source_inputs_not_recalculated")

    def test_equity_ratio_is_calculated_when_yfinance_inputs_share_native_unit(self):
        result = sync.equity_ratio_result(
            {"total_assets": 100},
            {"stockholders_equity": 40, "total_assets": 200},
            {},
            {"stockholders_equity": "yfinance_native", "total_assets": "yfinance_native"},
        )

        self.assertEqual(result["value"], 20)
        self.assertEqual(result["source"], "calculated")
        self.assertEqual(result["input_sources"], {"equity": "yfinance", "total_assets": "yfinance"})

    def test_yfinance_native_ratio_replaces_stale_seed_ratio_without_cross_source_amount_comparison(self):
        result = sync.equity_ratio_result(
            {"total_assets": 6073, "equity_ratio_computed": 35.15439778},
            {"stockholders_equity": 47_541_000_000, "total_assets": 146_294_000_000},
            {"total_assets": "million_jpy", "equity_ratio_computed": "percent"},
            {"stockholders_equity": "yfinance_native", "total_assets": "yfinance_native"},
        )

        self.assertAlmostEqual(result["value"], 32.495, places=2)
        self.assertEqual(result["source"], "calculated")
        self.assertEqual(result["input_units"], {
            "equity": "yfinance_native", "total_assets": "yfinance_native",
        })

    def test_seed_ratio_is_preserved_when_yfinance_native_amount_is_not_comparable(self):
        result = sync.equity_ratio_result(
            {"total_assets": 416206, "equity_ratio_computed": 25.8795},
            {"stockholders_equity": 811640000},
            {"total_assets": "million_jpy", "equity_ratio_computed": "percent"},
            {"stockholders_equity": "yfinance_native"},
        )

        self.assertEqual(result["value"], 25.8795)
        self.assertEqual(result["source"], "seed")

    def test_equity_ratio_stays_unavailable_for_mixed_sources_without_same_source_seed_ratio(self):
        result = sync.equity_ratio_result(
            {"total_assets": 100},
            {"stockholders_equity": 40},
            {"total_assets": "million_jpy"},
            {"stockholders_equity": "yfinance_native"},
        )

        self.assertIsNone(result["value"])
        self.assertEqual(result["reason"], "mixed_source_inputs_not_comparable")


    def test_financial_source_values_keep_seed_and_yfinance_rows_separate(self):
        seed, yfinance = sync.financial_source_values({
            "period_end": "2025-03-31",
            "field_status": {"sales": "legacy_migrated", "operating_income": "available"},
            "sales": 100.0,
            "operating_income": 12.0,
        })

        self.assertEqual(seed, {"sales": 100.0})
        self.assertEqual(yfinance, {"operating_income": 12.0})

    def test_financial_source_values_preserve_explicit_seed_when_yfinance_changes_value(self):
        seed, yfinance = sync.financial_source_values({
            "sales": 120.0,
            "source_values": {
                "seed": {"source": "IRBANK", "values": {"sales": 100.0}},
                "yfinance": {"source": "Yahoo Finance via yfinance", "values": {"sales": 120.0}},
            },
        })

        self.assertEqual(seed, {"sales": 100.0})
        self.assertEqual(yfinance, {"sales": 120.0})

    def test_financial_source_values_do_not_mislabel_computed_equity_ratio(self):
        seed, yfinance = sync.financial_source_values({
            "equity_ratio_computed": 24.0,
            "field_status": {"equity_ratio_computed": "available"},
            "source_values": {
                "calculated": {
                    "values": {
                        "equity_ratio_computed": {
                            "value": 24.0,
                            "formula": "equity / total_assets * 100",
                            "input_sources": {"equity": "seed", "total_assets": "yfinance"},
                        },
                    },
                },
            },
        })

        self.assertEqual(seed, {})
        self.assertEqual(yfinance, {})

    def test_financial_migration_retains_seed_when_flat_yfinance_value_exists(self):
        row, added = financial_migration.merge_row(
            {
                "fiscal_year": "2025/03",
                "period_end": "2025-03-31",
                "sales": 120.0,
                "field_status": {"sales": "available"},
                "source_values": {
                    "yfinance": {"source": "Yahoo Finance via yfinance", "values": {"sales": 120.0}},
                },
            },
            "2025/03",
            "2025-03-31",
            {"sales": 100.0},
            source_key="market/irbank/v1/securities/72030/overview.json",
        )

        self.assertEqual(added, 1)
        self.assertEqual(row["sales"], 120.0)
        self.assertEqual(row["source_values"]["seed"]["values"]["sales"], 100.0)
        self.assertEqual(row["source_values"]["yfinance"]["values"]["sales"], 120.0)

    def test_calculates_net_de_ratio_from_total_debt_and_cash_for_one_period(self):
        result = sync.calculate_net_de_metrics({
            "stockholders_equity": 100.0,
            "total_debt": 40.0,
            "cash_and_short_term_investments": 62.0,
        })

        self.assertEqual(result["net_debt"], -22.0)
        self.assertEqual(result["net_de_ratio"], -0.22)
        self.assertEqual(result["net_debt_source_field"], "total_debt_minus_cash_and_short_term_investments")
        self.assertEqual(result["net_de_equity_source_field"], "stockholders_equity")

    def test_prefers_yahoo_net_debt_and_accepts_gross_equity_fallback(self):
        result = sync.calculate_net_de_metrics({
            "net_debt": 30.0,
            "total_debt": 100.0,
            "cash_and_equivalents": 90.0,
            "total_equity_gross_minority_interest": 150.0,
        })

        self.assertEqual(result["net_debt"], 30.0)
        self.assertEqual(result["net_debt_source_field"], "net_debt")
        self.assertEqual(result["net_de_ratio"], 0.2)
        self.assertEqual(result["net_de_equity_source_field"], "total_equity_gross_minority_interest")

    def test_does_not_assume_missing_cash_is_zero_or_use_nonpositive_equity(self):
        self.assertEqual(sync.calculate_net_de_metrics({
            "stockholders_equity": 100.0,
            "total_debt": 20.0,
        }), {})
        self.assertEqual(sync.calculate_net_de_metrics({
            "stockholders_equity": 0.0,
            "net_debt": 20.0,
        }), {})

    def test_financial_metric_missing_reasons_name_required_inputs(self):
        reasons = sync.financial_metric_missing_reasons(
            fresh_balance={"stockholders_equity": 100.0, "total_debt": 20.0},
            fresh_cash={},
            retrieval_status={"balance_sheet": "available", "cash_flow": "available"},
            calculated_doe=None,
            net_de_metrics={},
        )

        self.assertEqual(reasons["calculated_doe"], ["dividend_paid_missing"])
        self.assertEqual(reasons["net_de_ratio"], ["cash_input_missing"])


class IncomeAdviceFinancialUnitTests(TestCase):
    def test_payout_ratio_uses_declared_ratio_or_percent_unit(self):
        ratio_value = sync.income_advice_metrics({
            "statistics": {"payoutRatio": 0.25},
            "statistics_field_units": {"payoutRatio": "ratio"},
            "field_status": {"statistics_payoutRatio": "available"},
        }, [], None)
        percent_value = sync.income_advice_metrics({
            "statistics": {"payoutRatio": 25, "operatingMargins": 10},
            "statistics_field_units": {"payoutRatio": "percent", "operatingMargins": "percent"},
            "field_status": {"statistics_payoutRatio": "available"},
        }, [], None)
        ratio_operating_margin = sync.income_advice_metrics({
            "statistics": {"operatingMargins": 0.1},
            "statistics_field_units": {"operatingMargins": "ratio"},
            "field_status": {"statistics_operatingMargins": "available"},
        }, [], None)
        unknown_unit = sync.income_advice_metrics({
            "statistics": {"payoutRatio": 25},
            "statistics_field_units": {"payoutRatio": "unknown"},
            "field_status": {"statistics_payoutRatio": "available"},
        }, [], None)

        self.assertEqual(ratio_value["payout_ratio"], 25)
        self.assertEqual(percent_value["payout_ratio"], 25)
        self.assertEqual(percent_value["operating_margin"], 10)
        self.assertEqual(ratio_operating_margin["operating_margin"], 10)
        self.assertIsNone(unknown_unit["payout_ratio"])
        self.assertEqual(unknown_unit["fields"]["payout_ratio"], "unsupported")


class SyncPriceTests(TestCase):
    def test_daily_valuation_uses_close_and_latest_eps_or_book_value(self):
        self.assertEqual(
            sync.calculate_daily_valuation(3000, 200, 1000),
            {"trailingPE": 15.0, "priceToBook": 3.0},
        )
        self.assertEqual(
            sync.calculate_daily_valuation(3000, -10, 0),
            {"trailingPE": None, "priceToBook": None},
        )

    def test_daily_price_change_uses_previous_close_from_same_chart(self):
        index = pd.DatetimeIndex(["2026-10-14", "2026-10-15"], tz="Asia/Tokyo")
        frame = pd.DataFrame({"Close": [99.0, 100.0]}, index=index)

        self.assertEqual(
            sync.daily_price_change(frame, date(2026, 10, 15), 100),
            {"change": 1.0, "change_percent": 1.01010101, "previous_close": 99.0},
        )

    def test_v2_current_fields_keep_only_used_inputs_and_update_markers(self):
        self.assertEqual(set(sync.CURRENT_FIELDS), {
            "trailingEps", "forwardEps", "bookValue", "dividendRate", "dividendYield",
            "trailingPE", "priceToBook", "operatingMargins", "payoutRatio",
            "exDividendDate", "lastFiscalYearEnd", "mostRecentQuarter",
        })
        self.assertTrue({"trailingPE", "priceToBook"}.issubset(sync.DERIVED_STATISTIC_FIELDS))
        self.assertTrue(set(sync.DERIVED_STATISTIC_FIELDS).issubset(sync.CURRENT_FIELDS))

    def test_company_info_failure_is_stored_with_class_and_reason(self):
        error = TimeoutError("request timed out")
        self.assertEqual(sync.company_info_failure_metadata(error), {
            "failure_class": "transient",
            "exception_type": "TimeoutError",
            "reason": "request timed out",
        })

    def test_unused_previous_statistics_are_archived_once(self):
        previous = {
            "trailingPE": 12.5, "priceToBook": 2.3, "operatingMargins": 0.1,
            "enterpriseValue": 1000, "forwardEps": 42.0,
        }
        with patch.object(sync, "read_json", return_value=None), \
             patch.object(sync, "put_json_if_changed") as write:
            sync.archive_unused_statistics(object(), "bucket", "7203.T", previous)

        self.assertEqual(write.call_count, 1)
        archived = write.call_args.args[3]
        self.assertEqual(archived["statistics"], {"enterpriseValue": 1000})

    def test_monthly_price_point_date_is_normalized_without_changing_close(self):
        point = {"month": "2024-12", "date": "2024-12-01", "close": 3146.0}
        with patch.object(monthly_calendar, "last_xtks_session_in_december", return_value=date(2024, 12, 30)):
            normalized, changed = sync.normalize_december_price_point(point)

        self.assertTrue(changed)
        self.assertEqual(normalized, {"month": "2024-12", "date": "2024-12-30", "close": 3146.0})
        self.assertEqual(point["date"], "2024-12-01")

    def test_monthly_price_repair_normalizes_all_december_points(self):
        document = {
            "symbol": "7203.T",
            "points": [
                {"month": "2024-12", "date": "2024-12-01", "close": 3146.0},
                {"month": "2025-12", "date": "2025-12-30", "close": 3356.0},
                {"month": "2025-11", "date": "2025-11-28", "close": 3200.0},
            ],
        }
        with patch.object(monthly_calendar, "last_xtks_session_in_december", side_effect=lambda year: {
            2024: date(2024, 12, 30), 2025: date(2025, 12, 30),
        }[year]):
            repaired, changed = monthly_date_repair.normalize_points(document)

        self.assertEqual(changed, 1)
        self.assertEqual(repaired["points"][0], {"month": "2024-12", "date": "2024-12-30", "close": 3146.0})
        self.assertEqual(repaired["points"][1], document["points"][1])
        self.assertEqual(repaired["points"][2], document["points"][2])
        self.assertEqual(document["points"][0]["date"], "2024-12-01")

    def test_no_action_rows_are_recorded_as_empty_history_when_chart_has_coverage(self):
        class Ticker:
            def history(self, **kwargs):
                index = pd.DatetimeIndex(["2026-10-15"], tz="Asia/Tokyo")
                return pd.DataFrame({
                    "Close": [100.0], "Dividends": [0.0], "Stock Splits": [0.0],
                }, index=index)

        with patch.object(sync, "count_yfinance_call"):
            dividends, splits, start, end = sync.fetch_action_event_candidate(
                Ticker(), date(2026, 10, 15), period="1y"
            )

        self.assertEqual(dividends, [])
        self.assertEqual(splits, [])
        self.assertEqual(start, date(2026, 10, 15))
        self.assertEqual(end, date(2026, 10, 15))

    def test_one_daily_chart_provides_close_and_action_candidates(self):
        index = pd.DatetimeIndex(["2026-10-14", "2026-10-15"], tz="Asia/Tokyo")

        class Ticker:
            calls = 0

            def history(self, **kwargs):
                self.calls += 1
                self.kwargs = kwargs
                return pd.DataFrame({
                    "Close": [99.0, 100.0],
                    "Dividends": [0.0, 2.5],
                    "Stock Splits": [0.0, 2.0],
                }, index=index)

        ticker = Ticker()
        with patch.object(sync, "count_yfinance_call"):
            daily, frame = sync.fetch_daily_history_with_actions(
                ticker, date(2026, 10, 15), period="1y",
            )
            dividends, splits, start, end = sync.action_candidate_from_history(
                frame, date(2026, 10, 15),
            )

        self.assertEqual(ticker.calls, 1)
        self.assertEqual(ticker.kwargs["actions"], True)
        self.assertEqual(ticker.kwargs["auto_adjust"], False)
        self.assertEqual(daily[-1], {"date": "2026-10-15", "close": 100.0})
        self.assertEqual(dividends, [{"date": "2026-10-15", "amount": 2.5}])
        self.assertEqual(splits, [{"date": "2026-10-15", "ratio": 2.0}])
        self.assertEqual((start, end), (date(2026, 10, 14), date(2026, 10, 15)))

    def test_missing_action_columns_are_not_treated_as_no_events(self):
        index = pd.DatetimeIndex(["2026-10-15"], tz="Asia/Tokyo")
        frame = pd.DataFrame({"Close": [100.0]}, index=index)

        with self.assertRaisesRegex(RuntimeError, "omitted dividend or split"):
            sync.action_candidate_from_history(frame, date(2026, 10, 15))


class CatalogTests(TestCase):
    def test_catalog_keeps_annual_forecast_separate_from_trailing_distributions(self):
        item = sync.catalog_item_from_documents(
            "7203",
            {"security_type": "listed_security"},
            {"display_name": "トヨタ"},
            {"price": {"close": 3000, "date": "2026-10-16"}, "updated_at": "2026-10-16T00:00:00Z",
             "dividend": {
                 "yield_basis": "next_fiscal_year_forecast",
                 "next_fiscal_year_forecast_per_share": 100,
                 "current_fiscal_year_per_share": 95,
                 "trailing_12_month_per_share": 630,
                 "dividend_status": "has_dividend",
                 "yield_rate": 3.33,
             }},
            {"status": "ok"},
        )

        self.assertEqual(item["annual_dividend_per_share"], 100)
        self.assertEqual(item["trailing_12_month_distribution_per_share"], 630)
        self.assertEqual(item["dividend_period"], "next_fiscal_year_forecast")

    def test_catalog_does_not_reuse_yield_when_current_annual_dps_is_unknown(self):
        item = sync.catalog_item_from_documents(
            "7203",
            {"security_type": "listed_security"},
            {"display_name": "トヨタ"},
            {"price": {"close": 3000}, "updated_at": "2026-10-16T00:00:00Z",
             "dividend": {
                 "yield_basis": None,
                 "next_fiscal_year_forecast_per_share": None,
                 "current_fiscal_year_per_share": None,
                 "trailing_12_month_per_share": 630,
                 "yield_rate": 21,
                 "yield_source": "stale_yield",
                 "dividend_status": "unknown",
             }},
            {"status": "ok"},
        )

        self.assertIsNone(item["annual_dividend_per_share"])
        self.assertIsNone(item["yield_rate"])
        self.assertIsNone(item["yield_source"])

    def test_official_forecast_replaces_stale_yahoo_value_and_accepts_an_updated_value(self):
        self.assertEqual(sync.next_fiscal_year_forecast_dps("8214", 120, date(2026, 10, 17)), 90)
        self.assertEqual(sync.next_fiscal_year_forecast_dps("8058", 126, date(2026, 10, 17)), 125)
        self.assertEqual(sync.next_fiscal_year_forecast_dps("7203", 100, date(2026, 10, 17)), 100)
        self.assertEqual(sync.next_fiscal_year_forecast_dps("8214", 90, date(2026, 10, 17)), 90)
        self.assertEqual(sync.next_fiscal_year_forecast_dps("8214", 100, date(2026, 10, 17)), 100)
        self.assertEqual(sync.next_fiscal_year_forecast_dps("8214", 120, date(2027, 4, 1)), 120)

    def test_catalog_rebuilds_legacy_trailing_total_rows_from_current_documents(self):
        current = {
            "updated_at": "2026-10-16T00:00:00Z",
            "price": {"close": 1800, "date": "2026-10-16"},
            "dividend": {
                "dividend_status": "has_dividend",
                "yield_basis": "next_fiscal_year_forecast",
                "next_fiscal_year_forecast_per_share": 120,
                "trailing_12_month_per_share": 630,
            },
        }
        documents = {
            sync.object_key("securities", "8214.T", "profile.json"): {"display_name": "AOKI"},
            sync.object_key("securities", "8214.T", "current.json"): current,
            sync.object_key("securities", "8214.T", "sync.json"): {"status": "ok"},
        }
        previous = {"items": [{
            "code": "8214", "name": "AOKI", "price": 1800,
            "annual_dividend_per_share": 630,
            "trailing_12_month_distribution_per_share": 630,
            "dividend_period": "trailing_12_months",
        }]}

        with patch.object(sync, "read_json", side_effect=lambda _client, _bucket, key: documents.get(key)):
            catalog = sync.publish_catalog(
                object(), "bucket", [{"security_code_display": "8214", "security_type": "listed_security"}],
                date(2026, 10, 17), previous,
            )

        item = catalog["items"][0]
        self.assertEqual(item["annual_dividend_per_share"], 90)
        self.assertEqual(item["trailing_12_month_distribution_per_share"], 630)
        self.assertEqual(item["dividend_period"], "next_fiscal_year_forecast")
        self.assertEqual(item["yield_rate"], 5.0)

    def test_catalog_reuses_previous_rows_without_r2_reads(self):
        previous = {"items": [
            {"code": "1111", "name": "Old Name", "price": 10.0, "status": "ok"},
            {"code": "2222", "name": "Failed Name", "price": 20.0, "status": "ok"},
        ]}
        universe = [
            {"security_code_display": "1111", "security_type": "listed_security"},
            {"security_code_display": "2222", "security_type": "listed_security"},
        ]
        updated = {
            "1111": {
                "code": "1111", "name": "Updated Name", "price": 11.0,
                "status": "ok", "security_type": "listed_security",
            },
        }
        with patch.object(sync, "read_json", side_effect=AssertionError("unexpected R2 catalog read")):
            catalog = sync.publish_catalog(
                object(), "bucket", universe, date(2026, 10, 15), previous,
                processed_items=updated, failures={"2222": "Yahoo timeout"},
            )

        self.assertEqual(catalog["items"][0]["name"], "Updated Name")
        self.assertEqual(catalog["items"][0]["price"], 11.0)
        self.assertEqual(catalog["items"][1]["price"], 20.0)
        self.assertEqual(catalog["items"][1]["status"], "failed")
        self.assertEqual(catalog["items"][1]["failure_reason"], "Yahoo timeout")


class CalendarYearDividendMetricsTests(TestCase):
    def test_missing_completed_years_are_assumed_zero_within_successful_history(self):
        metrics = sync.calendar_year_dividend_metrics(
            [
                {"date": "2023-06-30", "amount": 5.0},
                {"date": "2025-06-30", "amount": 10.0},
            ],
            None,
            date(2026, 7, 1),
        )

        records = {row["calendar_year"]: row for row in metrics["annual_records"]}
        self.assertEqual(set(records), {2023, 2024, 2025})
        self.assertEqual(records[2024]["calendar_year_distribution_per_share"], 0.0)
        self.assertEqual(records[2024]["record_basis"], "no_observed_distribution_assumed_zero")
        self.assertEqual(metrics["consecutive_increase_years"], 1)
        self.assertEqual(metrics["no_cut_years"], 1)


class R2MetricsTests(TestCase):
    def test_activity_timeline_separates_and_counts_overlapping_work(self):
        timeline = sync.ActivityTimeline()
        timeline.record("yfinance", 1.0, 4.0)
        timeline.record("r2", 3.0, 5.0)

        self.assertEqual(timeline.snapshot(0.0, 6.0), {
            "window_wall_seconds": 6.0,
            "yfinance_active_wall_seconds": 3.0,
            "r2_active_wall_seconds": 2.0,
            "overlap_wall_seconds": 1.0,
            "yfinance_only_wall_seconds": 2.0,
            "r2_only_wall_seconds": 1.0,
            "other_wall_seconds": 2.0,
        })

    def test_measured_client_counts_requests_and_body_reads(self):
        class Body:
            def read(self):
                return b"{}"

        class Paginator:
            def paginate(self, **kwargs):
                yield {"Contents": []}

        class Client:
            def get_object(self, **kwargs):
                return {"Body": Body()}

            def put_object(self, **kwargs):
                return {"ETag": "test"}

            def get_paginator(self, operation):
                return Paginator()

        metrics = sync.R2Metrics()
        client = sync.MeasuredR2Client(Client(), metrics)
        self.assertEqual(client.get_object(Bucket="test", Key="test")["Body"].read(), b"{}")
        client.put_object(Bucket="test", Key="test", Body=b"{}")
        list(client.get_paginator("list_objects_v2").paginate(Bucket="test"))
        snapshot = metrics.snapshot()

        self.assertEqual(snapshot["calls"]["get_object"], 1)
        self.assertEqual(snapshot["calls"]["get_body"], 1)
        self.assertEqual(snapshot["calls"]["put_object"], 1)
        self.assertEqual(snapshot["calls"]["list_objects_v2"], 1)
        self.assertGreaterEqual(snapshot["seconds"]["list_objects_v2"], 0.0)

    def test_security_cache_reuses_reads_and_tracks_successful_writes(self):
        class MissingObject(Exception):
            response = {"Error": {"Code": "NoSuchKey"}}

        class Body:
            def __init__(self, value):
                self.value = value

            def read(self):
                return self.value

        class Client:
            def __init__(self):
                self.get_count = 0
                self.put_count = 0
                self.objects = {}

            def get_object(self, **kwargs):
                self.get_count += 1
                key = kwargs["Key"]
                if key not in self.objects:
                    raise MissingObject(key)
                return {"Body": Body(self.objects[key])}

            def put_object(self, **kwargs):
                self.put_count += 1
                self.objects[kwargs["Key"]] = kwargs["Body"]

        client = Client()
        with sync.r2_document_cache_scope():
            self.assertIsNone(sync.read_object(client, "bucket", "security/current.json"))
            self.assertIsNone(sync.read_object(client, "bucket", "security/current.json"))
            sync.put_json_if_changed(client, "bucket", "security/current.json", {"price": 10})
            self.assertEqual(
                sync.read_json(client, "bucket", "security/current.json"),
                {"price": 10},
            )

        self.assertEqual(client.get_count, 1)
        self.assertEqual(client.put_count, 1)

    def test_monthly_price_migration_reads_only_listed_objects_and_skips_absent_deletes(self):
        class Body:
            def __init__(self, value):
                self.value = value

            def read(self):
                return self.value

        class Paginator:
            def __init__(self, client):
                self.client = client

            def paginate(self, **kwargs):
                prefix = kwargs["Prefix"]
                matches = [
                    {"Key": key} for key in self.client.objects
                    if key.startswith(prefix)
                ]
                self.client.list_count += 1
                yield {"Contents": matches}

        class Client:
            def __init__(self, objects):
                self.objects = objects
                self.get_count = 0
                self.put_count = 0
                self.delete_count = 0
                self.list_count = 0

            def get_paginator(self, operation):
                self.asserted_operation = operation
                return Paginator(self)

            def get_object(self, **kwargs):
                self.get_count += 1
                key = kwargs["Key"]
                if key not in self.objects:
                    raise KeyError(key)
                return {"Body": Body(self.objects[key])}

            def put_object(self, **kwargs):
                self.put_count += 1
                self.objects[kwargs["Key"]] = kwargs["Body"]

            def delete_object(self, **kwargs):
                self.delete_count += 1
                self.objects.pop(kwargs["Key"], None)

        safe = "7203.T"
        hot_key = sync.object_key("securities", safe, "prices", "monthly", "2025.json")
        existing = {
            "schema_version": 1,
            "symbol": "7203.T",
            "year": 2025,
            "points": [{"month": "2025-12", "date": "2025-12-30", "close": 100.0}],
            "updated_at": "old",
            "source": "Yahoo Finance via yfinance",
        }
        client = Client({
            hot_key: json.dumps(existing, separators=(",", ":")).encode("utf-8"),
        })

        with sync.r2_document_cache_scope():
            sync.save_monthly_prices(client, "bucket", "7203.T", [], as_of=date(2026, 10, 16))

        self.assertEqual(client.list_count, 2)
        self.assertEqual(client.get_count, 1)
        self.assertEqual(client.delete_count, 0)
        self.assertEqual(client.put_count, 1)


class EventPartitionPersistenceTests(TestCase):
    def test_duplicate_event_dates_are_detected_for_yfinance_rebuild(self):
        self.assertTrue(sync.has_duplicate_event_dates([
            {"date": "2026-03-30", "amount": 24.5},
            {"date": "2026-03-30", "amount": 24.5},
        ]))
        self.assertFalse(sync.has_duplicate_event_dates([
            {"date": "2026-03-30", "amount": 24.5},
            {"date": "2026-09-29", "amount": 26.0},
        ]))

    def test_unchanged_daily_chart_does_not_rewrite_event_partitions(self):
        self.assertFalse(sync.action_partitions_need_persist(None, None, [], []))

    def test_repaired_or_migrated_event_history_is_written(self):
        self.assertTrue(sync.action_partitions_need_persist([], [], [], []))
        self.assertTrue(sync.action_partitions_need_persist(
            None, None, [{"date": "2020-01-01", "amount": 1.0}], [],
        ))

    def test_normal_event_refresh_reads_only_recent_year_partitions(self):
        class MissingObject(Exception):
            response = {"Error": {"Code": "NoSuchKey"}}

        class Body:
            def read(self):
                return json.dumps({"records": [{"date": "2025-03-01", "amount": 1.0}]}).encode()

        class Client:
            def __init__(self):
                self.keys = []
                self.list_count = 0

            def get_object(self, **kwargs):
                self.keys.append(kwargs["Key"])
                if kwargs["Key"] == sync.object_key("securities", "7203.T", "dividends", "2025.json"):
                    return {"Body": Body()}
                raise MissingObject(kwargs["Key"])

            def get_paginator(self, operation):
                self.list_count += 1
                raise AssertionError("normal event refresh must not list archive partitions")

        client = Client()
        with sync.r2_document_cache_scope():
            records = sync.load_recent_year_records(
                client, "bucket", "7203.T", "dividends", date(2026, 10, 16),
            )

        self.assertEqual(records, [{"date": "2025-03-01", "amount": 1.0}])
        self.assertEqual(len(client.keys), 3)
        self.assertTrue(all("/archive/" not in key for key in client.keys))
        self.assertEqual(client.list_count, 0)

    def test_unchanged_profile_keeps_timestamp_to_avoid_rewriting_r2(self):
        previous = {"display_name": "会社", "field_status": {"display_name": "available"}, "updated_at": "old"}
        profile = {"display_name": "会社", "field_status": {"display_name": "available"}, "updated_at": "new"}

        result = sync.preserve_profile_timestamp_if_unchanged(profile, previous)

        self.assertEqual(result["updated_at"], "old")

    def test_changed_profile_gets_new_timestamp(self):
        previous = {"display_name": "旧社名", "updated_at": "old"}
        profile = {"display_name": "新社名", "updated_at": "new"}

        result = sync.preserve_profile_timestamp_if_unchanged(profile, previous)

        self.assertEqual(result["updated_at"], "new")


class SyncPolicyTests(TestCase):
    def test_company_info_rotation_selects_each_security_once_per_twenty_sessions(self):
        for number in range(1000, 1020):
            code = str(number)
            selected = [
                bucket for bucket in range(sync.COMPANY_INFO_BUCKETS)
                if sync.should_refresh_company_info(code, bucket, True, False)
            ]
            self.assertEqual(selected, [sync.bucket_for(code, sync.COMPANY_INFO_BUCKETS)])
            self.assertTrue(all(
                sync.should_refresh_company_info(code, bucket, False, False)
                for bucket in range(sync.COMPANY_INFO_BUCKETS)
            ))
            self.assertTrue(all(
                sync.should_refresh_company_info(code, bucket, True, True)
                for bucket in range(sync.COMPANY_INFO_BUCKETS)
            ))

    def test_same_session_catalog_item_is_reused_only_when_successful(self):
        session_date = date(2026, 10, 23)
        item = {"code": "7203", "status": "ok", "name": "Toyota"}
        previous_items = {"7203": item}

        reused = sync.reusable_same_session_catalog_item(
            previous_items, session_date.isoformat(), "7203", session_date,
        )

        self.assertEqual(reused, item)
        self.assertIsNot(reused, item)
        self.assertIsNone(sync.reusable_same_session_catalog_item(
            previous_items, "2026-10-22", "7203", session_date,
        ))
        self.assertIsNone(sync.reusable_same_session_catalog_item(
            {"7203": {"code": "7203", "status": "partial"}},
            session_date.isoformat(), "7203", session_date,
        ))

    def test_yahoo_missing_data_and_transient_errors_are_classified(self):
        class YFPricesMissingError(Exception):
            pass

        class HttpError(Exception):
            response = type("Response", (), {"status_code": 404})()

        class RateLimited(Exception):
            response = type("Response", (), {"status_code": 429})()

        class ServerFailure(Exception):
            response = type("Response", (), {"status_code": 503})()

        self.assertEqual(sync.classify_yfinance_error(YFPricesMissingError()), "no_data")
        self.assertEqual(sync.classify_yfinance_error(HttpError("missing")), "no_data")
        self.assertEqual(sync.classify_yfinance_error(RateLimited("rate limited")), "transient")
        self.assertEqual(sync.classify_yfinance_error(ServerFailure("unavailable")), "transient")
        self.assertEqual(sync.classify_yfinance_error(TimeoutError("timed out")), "transient")
        self.assertEqual(sync.classify_yfinance_error(ValueError("bad response")), "unknown")
        self.assertEqual(sync.classify_yfinance_error(RuntimeError("HTTP 404 Not Found")), "no_data")

    def test_no_data_is_skipped_only_after_it_failed_in_the_same_session(self):
        prior = {
            "failure_class": "no_data",
            "retry_policy": "next_market_session",
            "retry_session_date": "2026-10-16",
            "status": "failed",
        }
        self.assertTrue(sync.skip_same_session_no_data_retry(prior, date(2026, 10, 16)))
        self.assertFalse(sync.skip_same_session_no_data_retry(prior, date(2026, 10, 19)))
        self.assertFalse(sync.skip_same_session_no_data_retry({**prior, "failure_class": "transient"}, date(2026, 10, 16)))

    def test_no_data_failure_state_preserves_the_last_success_and_defers_retry(self):
        previous = {
            "status": "ok",
            "last_success_session_date": "2026-10-15",
            "last_success_at": "2026-10-15T12:00:00+00:00",
        }
        with patch.object(sync, "read_json", return_value=previous), \
             patch.object(sync, "put_json_if_changed") as write:
            sync.save_failure(
                object(), "bucket", "7203", "7203.T", date(2026, 10, 16),
                RuntimeError("HTTP Error 404: Not Found"), attempt_count=1,
            )

        saved = write.call_args.args[3]
        self.assertEqual(saved["status"], "failed")
        self.assertEqual(saved["failure_class"], "no_data")
        self.assertEqual(saved["retry_policy"], "next_market_session")
        self.assertEqual(saved["last_success_session_date"], "2026-10-15")
        self.assertEqual(saved["attempt_count_today"], 1)

    def test_first_company_info_failure_is_recorded_with_structured_reason(self):
        with patch.object(sync, "read_json", return_value={}), \
             patch.object(sync, "put_json_if_changed") as write:
            sync.save_failure(
                object(), "bucket", "7203", "7203.T", date(2026, 10, 19),
                TimeoutError("Yahoo request timed out"), attempt_count=1, retry_info=True,
            )

        saved = write.call_args.args[3]
        self.assertEqual(saved["info_last_attempt_session_date"], "2026-10-19")
        self.assertEqual(saved["info_last_attempt_status"], "failed")
        self.assertTrue(saved["info_refresh_pending"])
        self.assertEqual(saved["info_refresh_failure"], {
            "failure_class": "transient",
            "exception_type": "TimeoutError",
            "reason": "Yahoo request timed out",
        })

    def test_action_sample_includes_recent_split_and_reverse_split_codes(self):
        universe = [
            {"security_code_display": f"{number:04d}", "security_type": "listed_security"}
            for number in range(1, 9)
        ]
        with tempfile.TemporaryDirectory() as temp_dir:
            events_path = Path(temp_dir) / "events.csv"
            events_path.write_text(
                "code,effective_date,factor,event_type\n"
                "0001,2026-06-01,2,split\n"
                "0002,2026-07-01,0.1,reverse_split\n"
                "0003,2024-01-01,3,split\n",
                encoding="utf-8",
            )
            sample, summary = benchmark.action_stratified_sample(
                universe, universe[:6], 6, date(2026, 10, 16), events_path,
            )

        codes = {row["security_code_display"] for row in sample}
        self.assertEqual(len(sample), 6)
        self.assertTrue({"0001", "0002"}.issubset(codes))
        self.assertEqual(summary["known_recent_event_codes_in_jquants_universe"], 2)
        self.assertEqual(summary["sampled_recent_event_types"], {"reverse_split": 1, "split": 1})

    def test_action_method_comparison_covers_the_entire_sample(self):
        sample = [{"security_code_display": f"{number:04d}"} for number in range(12)]

        def measured(symbol, method, session_date):
            return {"symbol": symbol, "method": method, "event_fingerprint": "same"}

        with patch.object(benchmark, "measure_action_method", side_effect=measured) as mock_measure:
            summary, details = benchmark.compare_action_methods(sample, date(2026, 10, 16))

        self.assertEqual(mock_measure.call_count, 24)
        self.assertEqual(summary["comparison_count"], 12)
        self.assertEqual(summary["equal_event_fingerprints"], 12)
        self.assertEqual(len(details), 24)

    def test_pre_market_sync_uses_latest_completed_xtks_session(self):
        before_open = datetime(2026, 10, 16, 6, 0, tzinfo=sync.JST)
        after_close = datetime(2026, 10, 16, 21, 30, tzinfo=sync.JST)

        self.assertEqual(sync.latest_completed_xtks_session(before_open), date(2026, 10, 15))
        self.assertEqual(sync.latest_completed_xtks_session(after_close), date(2026, 10, 16))

    def test_weekend_benchmark_uses_latest_completed_xtks_session(self):
        saturday = datetime(2026, 10, 17, 12, 0, tzinfo=sync.JST)

        self.assertEqual(benchmark.benchmark_session_date(saturday), date(2026, 10, 16))
        self.assertEqual(
            benchmark.benchmark_session_date(saturday, date(2026, 10, 15)),
            date(2026, 10, 15),
        )

    def test_today_close_requires_exact_session_date(self):
        rows = [
            {"date": "2026-10-14", "close": 100.0},
            {"date": "2026-10-15", "close": 101.5},
        ]
        selected = sync.completed_close_for_session(rows, date(2026, 10, 15))
        self.assertEqual(selected["close"], 101.5)

    def test_stale_close_is_not_published_as_today(self):
        with self.assertRaisesRegex(RuntimeError, "no closing price for Japan session"):
            sync.completed_close_for_session(
                [{"date": "2026-10-14", "close": 100.0}], date(2026, 10, 15)
            )

    def test_new_split_requests_full_history_refresh_once(self):
        self.assertTrue(sync.has_new_split(
            [{"date": "2025-04-01", "ratio": 2.0}], []
        ))
        self.assertFalse(sync.has_new_split(
            [{"date": "2026-10-14", "ratio": 2.0}],
            [{"date": "2026-10-14", "ratio": 2.0}],
        ))
        self.assertTrue(sync.has_new_split(
            [{"date": "2026-10-14", "ratio": 3.0}],
            [{"date": "2026-10-14", "ratio": 2.0}],
        ))

    def test_new_split_forces_eight_year_price_refresh(self):
        self.assertTrue(sync.year_end_price_refresh_required(
            previous_monthly=True,
            refresh_monthly=False,
            initialize_monthly=False,
            retry_monthly=False,
            new_split_detected=True,
        ))

    def test_initial_prices_keep_only_december_closes_for_eight_years(self):
        index = pd.to_datetime([
            f"{year}-{month:02d}-01" for year in range(2019, 2027) for month in (6, 12)
        ]).tz_localize("Asia/Tokyo")
        frame = pd.DataFrame({"Close": [float(value) for value in range(1, len(index) + 1)]}, index=index)

        class Ticker:
            def history(self, **kwargs):
                self.kwargs = kwargs
                return frame

        ticker = Ticker()
        with patch.object(sync, "year_end_price_years", return_value=(2019, 2026)), \
             patch.object(sync, "last_xtks_session_in_december", side_effect=lambda year: date(year, 12, 30)), \
             patch.object(sync, "count_yfinance_call"):
            result = sync.monthly_history_rows(ticker, initial=True, as_of=date(2026, 10, 15))

        self.assertEqual(ticker.kwargs["interval"], "1mo")
        self.assertEqual(ticker.kwargs["auto_adjust"], False)
        self.assertEqual(len(result), 8)
        self.assertTrue(all(item["month"].endswith("-12") for item in result))
        self.assertEqual([item["month"] for item in result], [f"{year}-12" for year in range(2019, 2027)])

    def test_financial_refresh_on_new_reporting_period_marker(self):
        info = {"lastFiscalYearEnd": "2025-03-31", "mostRecentQuarter": "2025-12-31"}
        prior = {
            "financials_initialized": True,
            "financial_refresh_policy_version": sync.FINANCIAL_REFRESH_POLICY_VERSION,
            "financial_period_marker": sync.financial_period_marker(info),
            "financials_last_checked_session_date": "2026-01-01",
        }
        changed = {**info, "mostRecentQuarter": "2026-03-31"}
        due, marker, marker_changed = sync.financial_refresh_due(
            info=changed, prior_sync=prior, as_of=date(2026, 4, 2),
            initialize_due=False, retry_pending=False,
        )
        self.assertTrue(due)
        self.assertTrue(marker_changed)
        self.assertNotEqual(marker, prior["financial_period_marker"])

    def test_financial_refresh_has_max_age_fallback(self):
        due, _, marker_changed = sync.financial_refresh_due(
            info={},
            prior_sync={
                "financials_initialized": True,
                "financials_last_checked_session_date": "2026-01-01",
            },
            as_of=date(2026, 2, 16), initialize_due=False, retry_pending=False,
        )
        self.assertTrue(due)
        self.assertFalse(marker_changed)

    def test_same_session_retry_reuses_successful_financial_groups(self):
        requested = sync.financial_groups_to_fetch(
            same_session_retry=True,
            retry_pending=True,
            due=True,
            failed_groups={"cash_flow"},
        )
        self.assertEqual(requested, {"balance_sheet", "cash_flow"})

    def test_retry_of_failed_financial_groups_does_not_repeat_successful_groups(self):
        self.assertEqual(
            sync.financial_failed_groups({
                "financial_income_statement": "available",
                "financial_balance_sheet": "failed",
                "financial_cash_flow": "empty",
                "financial_valuation": "available",
            }),
            {"balance_sheet"},
        )

    def test_same_session_partial_retry_is_identified_after_attempt_start(self):
        self.assertTrue(sync.same_session_partial_retry({
            "status": "running",
            "retry_session_date": "2026-10-15",
            "last_success_session_date": "2026-10-15",
        }, date(2026, 10, 15)))

    def test_financial_period_marker_changes_only_when_reporting_dates_change(self):
        first = sync.financial_period_marker({
            "lastFiscalYearEnd": "2025-03-31", "mostRecentQuarter": "2025-12-31",
        })
        same = sync.financial_period_marker({
            "lastFiscalYearEnd": "2025-03-31", "mostRecentQuarter": "2025-12-31",
        })
        changed = sync.financial_period_marker({
            "lastFiscalYearEnd": "2025-03-31", "mostRecentQuarter": "2026-03-31",
        })
        self.assertEqual(first, same)
        self.assertNotEqual(first, changed)

    def test_failed_financial_retry_falls_back_to_all_groups_without_field_status(self):
        self.assertEqual(
            sync.financial_groups_to_fetch(
                same_session_retry=False,
                retry_pending=True,
                due=False,
                failed_groups=set(),
            ),
            set(sync.FINANCIAL_GROUPS),
        )


class AttributeErrorTracebackTests(TestCase):
    def setUp(self):
        with sync._ATTRIBUTE_ERROR_TRACEBACK_LOCK:
            self.previous_keys = set(sync._ATTRIBUTE_ERROR_TRACEBACK_KEYS)
            sync._ATTRIBUTE_ERROR_TRACEBACK_KEYS.clear()

    def tearDown(self):
        with sync._ATTRIBUTE_ERROR_TRACEBACK_LOCK:
            sync._ATTRIBUTE_ERROR_TRACEBACK_KEYS.clear()
            sync._ATTRIBUTE_ERROR_TRACEBACK_KEYS.update(self.previous_keys)

    def test_same_origin_string_get_error_is_logged_only_once(self):
        output = io.StringIO()
        with patch("sys.stderr", output):
            try:
                "text".get("key")
            except AttributeError as exc:
                sync.log_attribute_error_traceback(exc)
                sync.log_attribute_error_traceback(exc)

        self.assertEqual(output.getvalue().count("DIAGNOSTIC AttributeError traceback"), 1)
        self.assertIn("Traceback (most recent call last)", output.getvalue())


if __name__ == "__main__":
    import unittest

    unittest.main()

class OfflineFinancialSyncTests(TestCase):
    class Store:
        def __init__(self, documents=None):
            self.documents = deepcopy(documents or {})
            self.writes = []

        def read(self, client, bucket, key):
            return deepcopy(self.documents.get(key))

        def write(self, client, bucket, key, value):
            self.documents[key] = deepcopy(value)
            self.writes.append(key)
            return True

        def put_object(self, *, Bucket, Key, Body, **kwargs):
            self.documents[Key] = json.loads(Body.decode("utf-8"))

        def get_object(self, *, Bucket, Key):
            body = json.dumps(self.documents[Key], sort_keys=True).encode("utf-8")
            return {"Body": io.BytesIO(body)}

        def get_paginator(self, name):
            return self

        def paginate(self, *, Bucket, Prefix):
            return [{"Contents": [{"Key": key} for key in self.documents if key.startswith(Prefix)]}]

    @staticmethod
    def ticker():
        ticker = Mock()
        ticker.get_income_stmt.return_value = pd.DataFrame({"2026-03-31": {
            "TotalRevenue": 1000, "OperatingIncome": 100, "BasicEPS": 99,
        }})
        ticker.get_balance_sheet.return_value = pd.DataFrame({"2026-03-31": {
            "TotalAssets": 800, "StockholdersEquity": 400,
        }})
        ticker.get_cash_flow.return_value = pd.DataFrame({"2026-03-31": {
            "OperatingCashFlow": 120, "InvestingCashFlow": -30, "CashDividendsPaid": -20,
        }})
        ticker.get_valuation_measures.side_effect = AssertionError("unsupported valuation route")
        return ticker

    def test_compact_statement_keys_and_unsupported_valuation(self):
        ticker = self.ticker()
        with patch.object(sync, "read_json", return_value=None):
            annual, valuations, status, _ = sync.collect_financials(
                ticker, {}, self.Store(), "b", "7203.T", financial_currency="JPY",
            )
        row = annual[0]
        for field, value in {"sales": 1000, "operating_profit": 100, "total_assets": 800,
                             "operating_cf": 120, "investing_cf": -30, "calculated_doe": 5}.items():
            self.assertEqual(row[field], value)
            self.assertEqual(row["field_status"][field], "available")
        self.assertEqual(status["valuation"], "unsupported")
        self.assertEqual(valuations[0]["field_status"]["eps_adjusted"], "unsupported")
        self.assertNotIn("eps_adjusted", valuations[0])
        self.assertEqual(annual[0]["source_values"]["yfinance"]["field_units"]["total_assets"], "yfinance_native")
        self.assertEqual(annual[0]["source_values"]["yfinance"]["field_reported_currencies"]["total_assets"], "JPY")
        self.assertEqual(annual[0]["source_values"]["yfinance"]["reported_financial_currency_status"], "reported_unverified")
        self.assertEqual(row["operating_margin"], 10)
        self.assertEqual(row["fcf_margin"], 9)
        self.assertEqual(row["source_values"]["calculated"]["values"]["operating_margin"]["input_units"], {
            "operating_profit": "yfinance_native", "sales": "yfinance_native",
        })
        ticker.get_valuation_measures.assert_not_called()
        for mapping in (sync.INCOME_FIELDS, sync.BALANCE_FIELDS, sync.CASH_FLOW_FIELDS):
            for key, value in mapping.items():
                self.assertEqual(mapping[key.replace(" ", "")], value)

    def test_statement_money_uses_native_unit_even_when_reported_currency_is_missing(self):
        ticker = self.ticker()
        with patch.object(sync, "read_json", return_value=None):
            annual, _, _, _ = sync.collect_financials(
                ticker, {}, self.Store(), "b", "6269.T", financial_currency="USD",
            )
        row = annual[0]
        self.assertEqual(row["source_values"]["yfinance"]["field_units"]["total_assets"], "yfinance_native")
        self.assertEqual(row["source_values"]["yfinance"]["field_reported_currencies"]["total_assets"], "USD")
        self.assertEqual(row["source_values"]["yfinance"]["reported_financial_currency_status"], "reported_unverified")

        ticker = self.ticker()
        with patch.object(sync, "read_json", return_value=None):
            annual, _, _, _ = sync.collect_financials(ticker, {}, self.Store(), "b", "6269.T")
        row = annual[0]
        self.assertEqual(row["total_assets"], 800)
        self.assertEqual(row["source_values"]["yfinance"]["field_units"]["total_assets"], "yfinance_native")
        self.assertIsNone(row["source_values"]["yfinance"]["reported_financial_currency"])
        self.assertEqual(row["source_values"]["yfinance"]["reported_financial_currency_status"], "not_reported")

    def test_same_period_yfinance_ratio_replaces_stale_seed_ratio(self):
        ticker = self.ticker()
        ticker.get_income_stmt.return_value = pd.DataFrame({"2023-03-31": {"TotalRevenue": 272_167_000_000}})
        ticker.get_balance_sheet.return_value = pd.DataFrame({"2023-03-31": {
            "StockholdersEquity": 47_541_000_000, "TotalAssets": 146_294_000_000,
        }})
        ticker.get_cash_flow.return_value = pd.DataFrame({"2023-03-31": {"CashDividendsPaid": -972_000_000}})
        key = sync.object_key("securities", "1301.T", "financials", "2023-03-31.json")
        seed = {
            "source": "IRBANK",
            "values": {"total_assets": 6073, "equity_ratio_computed": 35.15439778},
            "field_units": {"total_assets": "million_jpy", "equity_ratio_computed": "percent"},
        }
        store = self.Store({key: {"annual": {
            "fiscal_year": "2023/03", "period_end": "2023-03-31",
            "total_assets": 6073, "equity_ratio_computed": 35.15439778,
            "field_status": {"equity_ratio_computed": "legacy_migrated"},
            "source_values": {"seed": seed},
        }}})
        with patch.object(sync, "read_json", side_effect=store.read):
            rows, _, _, _ = sync.collect_financials(
                ticker, {}, store, "b", "1301.T", financial_currency="JPY",
            )

        row = rows[0]
        self.assertAlmostEqual(row["equity_ratio_computed"], 32.495, places=2)
        self.assertEqual(row["source_values"]["calculated"]["values"]["equity_ratio_computed"]["input_units"], {
            "equity": "yfinance_native", "total_assets": "yfinance_native",
        })
        self.assertEqual(row["source_values"]["seed"]["values"]["equity_ratio_computed"], 35.15439778)

    def test_slash_dates_and_legacy_valuation_are_retained_stale(self):
        old = {"valuation_history": {"yearly": [{"period_end": "03/31/2026", "values": {
            "EPS": 12, "Book Value": 200, "Dividend Yield": 2.5,
        }}]}}
        self.assertEqual(sync.date_value("2026/03/31"), date(2026, 3, 31))
        self.assertIsNone(sync.date_value("02/30/2026"))
        annual, valuations, status, _ = sync.collect_financials(self.ticker(), old, self.Store(), "b", "7203.T")
        self.assertEqual(len(annual), 1)
        for field, value in {"eps_adjusted": 12, "bps_adjusted": 200, "dividend_yield_adjusted": 2.5}.items():
            self.assertEqual(valuations[0][field], value)
            self.assertEqual(valuations[0]["field_status"][field], "stale")
        self.assertEqual(status["valuation"], "unsupported")

    def test_doe_retry_fetches_both_fresh_inputs_and_recovers(self):
        for failed in ("balance_sheet", "cash_flow"):
            for same_session in (False, True):
                with self.subTest(failed=failed, same_session=same_session):
                    ticker = self.ticker()
                    groups = sync.financial_groups_to_fetch(
                        same_session_retry=same_session, retry_pending=True,
                        due=False, failed_groups={failed},
                    )
                    self.assertEqual(groups, {"balance_sheet", "cash_flow"})
                    rows, _, _, _ = sync.collect_financials(
                        ticker, {}, self.Store(), "b", "7203.T", fetch_groups=groups,
                    )
                    self.assertEqual(rows[0]["calculated_doe"], 5)
                    self.assertEqual(rows[0]["field_status"]["calculated_doe"], "available")
                    ticker.get_balance_sheet.assert_called_once()
                    ticker.get_cash_flow.assert_called_once()
                    ticker.get_income_stmt.assert_not_called()
        self.assertEqual(sync.financial_groups_to_fetch(
            same_session_retry=True, retry_pending=True, due=True,
            failed_groups={"income_statement"},
        ), {"income_statement"})

    def test_doe_does_not_combine_different_periods_or_failed_fresh_inputs(self):
        ticker = self.ticker()
        ticker.get_cash_flow.return_value.columns = ["2025-03-31"]
        rows, _, _, _ = sync.collect_financials(ticker, {}, self.Store(), "b", "7203.T")
        self.assertTrue(all("calculated_doe" not in row for row in rows))
        prefix = sync.object_key("securities", "7203.T", "financials", "2026-03-31.json")
        store = self.Store({prefix: {"annual": {
            "period_end": "2026-03-31", "calculated_doe": 4,
            "stockholders_equity": 500, "cash_dividends_paid": -20,
        }}})
        ticker = self.ticker()
        ticker.get_balance_sheet.side_effect = RuntimeError("offline simulated failure")
        with patch.object(sync, "read_json", side_effect=store.read):
            rows, _, status, _ = sync.collect_financials(
                ticker, {}, store, "b", "7203.T", financial_currency="JPY",
            )
        self.assertEqual(rows[0]["calculated_doe"], 4)
        self.assertEqual(rows[0]["field_status"]["calculated_doe"], "stale")
        self.assertEqual(status["balance_sheet"], "failed")

    def test_margin_guard_preserves_all_seed_amounts(self):
        seed = {"source": "IRBANK", "source_key": "legacy/7203.json", "unit": "million_JPY",
                "values": {"sales": 1000, "operating_profit": 100,
                           "operating_cf": 120, "investing_cf": -30}}
        key = sync.object_key("securities", "7203.T", "financials", "2026-03-31.json")
        store = self.Store({key: {"annual": {
            "period_end": "2026-03-31", "source_values": {"seed": seed}, **seed["values"],
        }}})
        with patch.object(sync, "read_json", side_effect=store.read):
            rows, _, _, _ = sync.collect_financials(
                self.ticker(), {}, store, "b", "7203.T", fetch_groups=set(),
            )
        for field, value in seed["values"].items():
            self.assertEqual(rows[0][field], value)
            self.assertEqual(rows[0]["field_status"][field], "legacy_migrated")
        actual_seed = rows[0]["source_values"]["seed"]
        self.assertEqual({key: value for key, value in actual_seed.items() if key != "field_units"}, seed)
        self.assertEqual(actual_seed["field_units"], {
            "sales": "million_jpy", "operating_profit": "million_jpy",
            "operating_cf": "million_jpy", "investing_cf": "million_jpy",
        })

    def test_repaired_seeds_keep_values_and_provenance_during_refresh_and_failure(self):
        for source, source_key in (("J-Quants API /fins/summary", "jquants:/v2/fins/summary"),
                                   ("IRBANK Connect", "market/irbank/v1/securities/7203/overview.json")):
            for failed in (False, True):
                with self.subTest(source=source, failed=failed):
                    seed = {"source": source, "source_key": source_key,
                            "values": {"sales": 2000, "total_assets": 1600, "equity_ratio_computed": 40},
                            "field_units": {"sales": "jpy", "total_assets": "jpy", "equity_ratio_computed": "percent"},
                            "attribution": {"source_label": "issuer disclosures"}}
                    key = sync.object_key("securities", "7203.T", "financials", "2026-03-31.json")
                    store = self.Store({key: {"annual": {"period_end": "2026-03-31", "source_values": {"seed": seed}}}})
                    ticker = self.ticker()
                    if failed:
                        ticker.get_income_stmt.side_effect = RuntimeError("offline")
                    with patch.object(sync, "read_json", side_effect=store.read):
                        rows, _, status, _ = sync.collect_financials(ticker, {}, store, "b", "7203.T", financial_currency="JPY")
                    self.assertEqual(rows[0]["source_values"]["seed"], seed)
                    self.assertEqual(rows[0]["source_values"]["yfinance"]["values"]["total_assets"], 800)
                    if failed:
                        self.assertEqual(status["income_statement"], "failed")

    def test_margin_guard_nulls_seed_in_both_failure_directions_and_survives_reread(self):
        for failed, guarded in (
            ("cash_flow", ("operating_cf", "investing_cf")),
            ("income_statement", ("sales", "operating_profit")),
        ):
            with self.subTest(failed=failed):
                seed = {"source": "IRBANK", "source_key": "legacy/7203.json",
                        "unit": "million_JPY",
                        "values": {"sales": 1000, "operating_profit": 100,
                                   "operating_cf": 120, "investing_cf": -30}}
                expected_seed = {**seed, "field_units": {
                    "sales": "million_jpy", "operating_profit": "million_jpy",
                    "operating_cf": "million_jpy", "investing_cf": "million_jpy",
                }}
                key = sync.object_key("securities", "7203.T", "financials", "2026-03-31.json")
                store = self.Store({key: {"annual": {
                    "period_end": "2026-03-31", "source_values": {"seed": seed},
                    **seed["values"],
                }}})
                ticker = self.ticker()
                method = ticker.get_cash_flow if failed == "cash_flow" else ticker.get_income_stmt
                method.side_effect = RuntimeError("offline simulated failure")
                with patch.object(sync, "read_json", side_effect=store.read):
                    for attempt in range(2):
                        rows, _, status, _ = sync.collect_financials(
                            ticker, {}, store, "b", "7203.T", financial_currency="JPY",
                        )
                        row = rows[0]
                        self.assertEqual(status[failed], "failed")
                        self.assertEqual(row["source_values"]["seed"], expected_seed)
                        for field in guarded:
                            self.assertIn(field, row)
                            self.assertIsNone(row[field])
                            self.assertEqual(row["field_status"][field], "unsupported")
                            self.assertEqual(row["field_status_reason"][field],
                                             ["mixed_source_units_not_comparable"])
                        for field in set(seed["values"]) - set(guarded):
                            self.assertIsNotNone(row[field])
                            self.assertEqual(row["field_status"][field], "available")
                        store.documents[key] = {"annual": deepcopy(row)}
                    # A later unscheduled run must not resurrect the flat seed amounts.
                    rows, _, _, _ = sync.collect_financials(
                        ticker, {}, store, "b", "7203.T", fetch_groups=set(), financial_currency="JPY",
                    )
                    for field in guarded:
                        self.assertIsNone(rows[0][field])
                    self.assertEqual(rows[0]["source_values"]["seed"], expected_seed)

    def test_margin_guard_handles_partial_statement_and_zero_yahoo_amount(self):
        seed = {"source": "IRBANK", "source_key": "legacy/7203.json",
                "values": {"sales": 1000, "operating_profit": 100}}
        key = sync.object_key("securities", "7203.T", "financials", "2026-03-31.json")
        store = self.Store({key: {"annual": {
            "period_end": "2026-03-31", "source_values": {"seed": seed}, **seed["values"],
        }}})
        ticker = self.ticker()
        ticker.get_income_stmt.return_value = pd.DataFrame({"2026-03-31": {"OperatingIncome": 0}})
        with patch.object(sync, "read_json", side_effect=store.read):
            rows, _, _, _ = sync.collect_financials(
                ticker, {}, store, "b", "7203.T", fetch_groups={"income_statement"}, financial_currency="JPY",
            )
        self.assertIsNone(rows[0]["sales"])
        self.assertEqual(rows[0]["operating_profit"], 0)
        expected_seed = {**seed, "field_units": {
            "sales": "million_jpy", "operating_profit": "million_jpy",
        }}
        self.assertEqual(rows[0]["source_values"]["seed"], expected_seed)

    def test_collect_symbol_persists_annual_valuation_and_daily_multiples(self):
        prefix = sync.object_key("securities", "7203.T")
        store = self.Store({
            prefix + "/profile.json": {"display_name": "Toyota"},
            prefix + "/current.json": {
                "price": {"date": "2026-10-19", "close": 2000, "currency": "JPY"},
                "statistics": {"trailingEps": 100, "bookValue": 1000},
                "field_status": {"price": "available", "financial_cash_flow": "failed"},
            },
            prefix + "/financials/2026-03-31.json": {
                "annual": {"period_end": "2026-03-31", "sales": 900},
                "valuation": {"period_end": "2026-03-31", "eps_adjusted": 12},
            },
        })
        ticker = self.ticker()
        ticker.calendar = {}
        prior_sync = {
            "status": "partial", "retry_session_date": "2026-10-19",
            "last_success_session_date": "2026-10-19", "financial_retry_pending": True,
            "monthly_history_schema_version": 2,
        }
        with patch.object(sync.yf, "Ticker", return_value=ticker), \
             patch.object(sync, "read_json", side_effect=store.read), \
             patch.object(sync, "put_json_if_changed", side_effect=store.write), \
             patch.object(sync, "legacy_document", return_value={}):
            result = sync.collect_symbol(store, "b", "7203", "7203.T", date(2026, 10, 19),
                                         prior_sync_document=prior_sync)
        saved = store.documents[prefix + "/financials/2026-03-31.json"]
        self.assertEqual(saved["valuation"]["eps_adjusted"], 12)
        self.assertEqual(saved["valuation"]["field_status"]["eps_adjusted"], "stale")
        self.assertEqual(saved["annual"]["calculated_doe"], 5)
        current = store.documents[prefix + "/current.json"]
        self.assertEqual(current["statistics"]["trailingPE"], 20)
        self.assertEqual(current["statistics"]["priceToBook"], 2)
        self.assertFalse(store.documents[prefix + "/sync.json"]["financial_retry_pending"])
        self.assertEqual(result["status"], "ok")

    def test_repeated_publication_keeps_immutable_generations_and_consistent_references(self):
        legacy_key = sync.object_key("catalog", "generations", "2026-10-19.json")
        old_item = {"code": "7203", "name": "Toyota", "status": "ok", "price": 100, "quote_date": "2026-10-18",
                    "income_advice": {"code": "7203", "source_catalog_generation": "2026-10-19"}}
        legacy = {
            "generation": "2026-10-19", "market_data_date": "2026-10-19",
            "items": [old_item],
        }
        store = self.Store({
            legacy_key: legacy,
            sync.object_key("catalog", "current.json"): legacy,
            sync.object_key("securities", "7203.T", "profile.json"): {"display_name": "Toyota"},
            sync.object_key("securities", "7203.T", "current.json"): {
                "price": {"close": 200, "date": "2026-10-19", "currency": "JPY"},
                "dividend": {"current_fiscal_year_per_share": 10, "yield_basis": "current_fiscal_year"},
                "field_status": {"dividend_history": "available"},
                "action_history": {"full_history_coverage_end": "2025-12-31"},
            },
            sync.object_key("securities", "7203.T", "dividends", "2024.json"): {
                "records": [{"date": "2024-03-29", "amount": 10}]},
            sync.object_key("securities", "7203.T", "dividends", "2025.json"): {
                "records": [{"date": "2025-03-28", "amount": 10}]},
            sync.object_key("securities", "7203.T", "sync.json"): {
                "status": "ok", "last_success_session_date": "2026-10-19",
            },
        })
        limiter = Mock(requests_per_second=8, request_count=0, http_429_count=0)
        limiter.__enter__ = Mock(return_value=limiter)
        limiter.__exit__ = Mock(return_value=False)
        with ExitStack() as stack:
            stack.enter_context(patch.dict(sync.os.environ, {"JQUANTS_API_KEY": "offline"}))
            stack.enter_context(patch.object(sync.collector, "configure_yfinance", create=True))
            stack.enter_context(patch.object(sync.collector, "get_client", return_value=store))
            stack.enter_context(patch.object(sync.collector, "required_env", return_value="b"))
            stack.enter_context(patch.object(sync, "fetch_securities", return_value=[{"security_code_display": "7203"}]))
            stack.enter_context(patch.object(sync, "latest_completed_xtks_session", return_value=date(2026, 10, 19)))
            stack.enter_context(patch.object(sync, "is_last_market_session", return_value=False))
            stack.enter_context(patch.object(sync, "GlobalHttpRateLimiter", return_value=limiter))
            stack.enter_context(patch.object(sync, "read_json", side_effect=store.read))
            stack.enter_context(patch.object(sync, "put_json_if_changed", side_effect=store.write))
            generations = []
            for _ in range(2):
                self.assertEqual(sync.run(publish=True, max_workers=1, dry_run=False), 0)
                catalog = store.documents[sync.object_key("catalog", "current.json")]
                self.assertEqual(catalog["items"][0]["price"], 200)
                self.assertEqual(catalog["items"][0]["quote_date"], "2026-10-19")
                generation = catalog["generation"]
                generations.append(generation)
                self.assertRegex(generation, r"^2026-10-19-[0-9a-f]{32}$")
                self.assertEqual(store.documents[sync.object_key("catalog", "generations", generation + ".json")], catalog)
                self.assertEqual(store.documents[sync.object_key("publications", "current.json")]["generation"], generation)
                self.assertEqual(store.documents[sync.object_key("manifest.json")]["active_generation"], generation)
                advice = store.documents[sync.object_key("derived", "income_advice", "current.json")]
                self.assertEqual(advice["generation"], generation)
                self.assertEqual(advice["items"][0]["source_catalog_generation"], generation)
                self.assertEqual(catalog["items"][0]["income_advice"]["source_catalog_generation"], generation)
            self.assertNotEqual(*generations)
        self.assertEqual(store.documents[legacy_key], legacy)
        self.assertNotIn(legacy_key, store.writes)
        for generation in generations:
            key = sync.object_key("catalog", "generations", generation + ".json")
            self.assertEqual(store.writes.count(key), 1)
            self.assertEqual(store.documents[key]["generation"], generation)
