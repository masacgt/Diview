from datetime import date, datetime
import json
import tempfile
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

import pandas as pd

import benchmark_v2 as benchmark
import sync_v2 as sync
import repair_monthly_price_dates as monthly_date_repair
import monthly_price_calendar as monthly_calendar
import audit_split_transition_dividends as split_audit


class SplitTransitionAuditTests(TestCase):
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


class SyncPriceTests(TestCase):
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
            "financial_refresh_policy_version": 1,
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
        self.assertEqual(requested, {"cash_flow"})

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


if __name__ == "__main__":
    import unittest

    unittest.main()
