package com.kurogane.dividend.data

import com.kurogane.dividend.model.MetricHistoryPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialHistoryParserTest {
    private val asOf = LocalDate.of(2026, 10, 26)
    private fun annual(year: Int, margin: Double = year - 2020.0) = JSONObject()
        .put("period_end", "$year-03-31").put("operating_margin", margin)
        .put("source", "yfinance").put("field_units", JSONObject().put("operating_margin", "percent"))
    private fun document(vararg rows: JSONObject) = JSONObject()
        .put("security", JSONObject().put("code", "8058"))
        .put("financials", JSONObject().put("annual", JSONArray(rows.toList())))

    @Test fun quarantinedCrossIssuerRatioIsExcludedFromHistory() {
        val invalid = JSONObject().put("period_end", "2025-10-31")
            .put("equity_ratio_computed", 39.95)
            .put("field_status", JSONObject().put("equity_ratio_computed", "unsupported"))
            .put("field_units", JSONObject().put("equity_ratio_computed", "percent"))
        val valid = JSONObject().put("period_end", "2026-03-31")
            .put("equity_ratio_computed", 39.08)
            .put("field_status", JSONObject().put("equity_ratio_computed", "available"))
            .put("field_units", JSONObject().put("equity_ratio_computed", "percent"))
        val history = FinancialHistoryParser.parse(document(invalid, valid), asOf).series("equity_ratio")
        assertEquals(listOf(2026), history.map { it.periodEnd.year })
        assertEquals(39.08, history.single().value, 0.0)
    }

    @Test fun currentMetricsAndFiveCompletedPeriodsComeFromTheSameDocument() {
        val root = document(*(2018..2026).reversed().map { annual(it) }.toTypedArray())
        root.getJSONObject("financials").getJSONArray("annual").put(annual(2027))
        val parsed = FinancialHistoryParser.parse(root, asOf)
        assertEquals((2022..2026).toList(), parsed.history.getValue("operating_margin").map { it.periodEnd.year })
        val withNoFuture = document(*(2020..2026).map { annual(it) }.toTypedArray())
        val financial = FinancialData.parse(withNoFuture, "8058")
        assertEquals(6.0, financial.profile.operatingMargin, 0.0)
        assertEquals(listOf(2.0, 3.0, 4.0, 5.0, 6.0), financial.analysisContext.series("operating_margin").map { it.value })
    }

    @Test fun malformedDatesConflictingPeriodsAndFailedFieldsDoNotBecomeObservations() {
        val root = document(
            annual(2020).put("period_end", "2020-02-30"),
            annual(2021).put("field_status", JSONObject().put("operating_margin", "failed")),
            annual(2022).put("operating_margin", JSONObject.NULL),
            annual(2023, 1.0), annual(2023, 2.0), annual(2024, 4.0), annual(2025, 5.0)
        )
        val context = FinancialHistoryParser.parse(root, asOf)
        assertEquals(listOf(2024, 2025), context.series("operating_margin").map { it.periodEnd.year })
        assertEquals(2, context.history.getValue("operating_margin").size)
    }

    @Test fun percentagesAreNormalizedAndForecastOrCurrentStatisticsAreNotBackfilled() {
        val root = document(annual(2024).put("payout_ratio", 0.4).apply {
            getJSONObject("field_units").put("payout_ratio", "ratio")
        }, annual(2025).put("payout_ratio", 45).apply {
            getJSONObject("field_units").put("payout_ratio", "percent")
        })
            .put("market", JSONObject().put("summary", JSONObject().put("payoutRatio", 2.8).put("trailingPE", 99)))
            .put("issuer_dividends", JSONObject().put("forecast", JSONObject().put("payout_ratio", 10)))
        val parsed = FinancialHistoryParser.parse(root, asOf)
        assertEquals(listOf(40.0, 45.0), parsed.series("payout_ratio").map { it.value })
        assertFalse(parsed.history.containsKey("per"))
        assertFalse(parsed.history.containsKey("pbr"))
    }

    @Test fun monetaryRatiosRequireMatchingUnitsAndSources() {
        fun raw(year: Int) = JSONObject().put("period_end", "$year-03-31")
            .put("sales", 100).put("operating_profit", 10)
            .put("source", "yfinance")
            .put("field_units", JSONObject().put("sales", "yfinance_native").put("operating_profit", "yfinance_native"))
        val good = raw(2023)
        val badUnits = raw(2024).apply { getJSONObject("field_units").put("sales", "million_jpy") }
        val mixed = raw(2025).put("source_values", JSONObject()
            .put("seed", JSONObject().put("source", "IRBANK").put("values", JSONObject().put("sales", 100)))
            .put("yfinance", JSONObject().put("values", JSONObject().put("operating_profit", 10))))
        val parsed = FinancialHistoryParser.parse(document(good, badUnits, mixed), asOf)
        assertEquals(listOf(10.0), parsed.history.getValue("operating_margin").map { it.value })
    }

    @Test fun aProviderChangeOrAFiscalYearGapStopsTheComparison() {
        val sourceChange = FinancialHistoryParser.parse(document(
            annual(2023), annual(2024), annual(2025).put("source", "IRBANK")
        ), asOf)
        assertEquals(1, sourceChange.series("operating_margin").size)
        val gap = FinancialHistoryParser.parse(document(annual(2022), annual(2024), annual(2025)), asOf)
        assertEquals(listOf(2024, 2025), gap.series("operating_margin").map { it.periodEnd.year })
    }

    @Test fun issuerYearAloneCanDescribeAnnualChangesButCannotMatchAnArbitraryFiscalPeriod() {
        val root = document(annual(2025)).put("issuer_dividends", JSONObject().put("records", JSONArray()
            .put(JSONObject().put("fiscal_year", 2025).put("payout_ratio", 40)
                .put("field_units", JSONObject().put("payout_ratio", "percent")))))
        val yearOnly = FinancialHistoryParser.parse(root, asOf)
        assertEquals(MetricHistoryPoint.ISSUER_YEAR, yearOnly.series("payout_ratio").single().basis)
        assertEquals("year", yearOnly.series("payout_ratio").single().periodPrecision)
        assertNull(yearOnly.alignedLatest("payout_ratio", "operating_margin"))
        val row = root.getJSONObject("issuer_dividends").getJSONArray("records").getJSONObject(0)
        row.put("period_end", "2025-03-31")
        assertEquals(40.0, FinancialHistoryParser.parse(root, asOf).series("payout_ratio").single().value, 0.0)
    }

    @Test fun monthPrecisionJoinsOnlyOneUnambiguousKnownFinancialPeriod() {
        val root = document(annual(2025)).put("issuer_dividends", JSONObject().put("records", JSONArray()
            .put(JSONObject().put("fiscal_year", 2025).put("fiscal_period", "2025-03").put("payout_ratio", 40)
                .put("field_units", JSONObject().put("payout_ratio", "percent")))))
        val clear = FinancialHistoryParser.parse(root, asOf)
        assertNotNull(clear.alignedLatest("payout_ratio", "operating_margin"))
        root.getJSONObject("financials").getJSONArray("annual").put(annual(2025).put("period_end", "2025-03-20"))
        assertFalse(FinancialHistoryParser.parse(root, asOf).history.containsKey("payout_ratio"))
    }

    @Test fun dividendHistoryPreservesObservedZeroButDoesNotFillGapsOrIncludeCurrentYear() {
        val payload = JSONObject().put("history_basis", "calendar_year_observed_distributions")
            .put("records", JSONArray().apply {
                listOf(2021 to 10, 2022 to 0, 2024 to 12, 2025 to 13, 2026 to 50).forEach { (year, dps) ->
                    put(JSONObject().put("calendar_year", year).put("calendar_year_distribution_per_share", dps)
                        .put("field_units", JSONObject().put("calendar_year_distribution_per_share", "jpy_per_share")))
                }
            })
        val root = document().put("dividends", payload)
        val parsed = FinancialHistoryParser.parse(root, asOf)
        assertEquals(listOf(2021, 2022, 2024, 2025), parsed.history.getValue("dividend_dps").map { it.periodEnd.year })
        assertEquals(0.0, parsed.history.getValue("dividend_dps")[1].value, 0.0)
        assertEquals(listOf(2024, 2025), parsed.series("dividend_dps").map { it.periodEnd.year })
        assertEquals(MetricHistoryPoint.CALENDAR_ACTUAL, parsed.series("dividend_dps").last().basis)
        payload.put("share_basis_status", "unverified_incomplete_corporate_action_history")
        assertFalse(FinancialHistoryParser.parse(root, asOf).history.containsKey("dividend_dps"))
    }
}
