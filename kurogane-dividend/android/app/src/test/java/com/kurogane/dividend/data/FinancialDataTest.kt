package com.kurogane.dividend.data

import com.kurogane.dividend.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinancialDataTest {
    private fun document() = JSONObject("""{
      "security":{"code":"7203"},
      "financials":{"annual":[{"fiscal_year":"2025/03","sales":1000,"operating_profit":100,"operating_cf":200,"investing_cf":-50,"equity_ratio_computed":40}]},
      "dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2023,"calendar_year_distribution_per_share":10},{"calendar_year":2024,"calendar_year_distribution_per_share":20},{"calendar_year":2025,"calendar_year_distribution_per_share":30,"payout_ratio":35,"doe":3,"consecutive_dividend_increase_years":2}]},
      "valuation":{"history":[{"fiscal_year":"2025/03","eps_adjusted":50,"bps_adjusted":200,"dividend_yield_adjusted":3}]}
    }""")
    @Test fun calculatesMarginsAndUsesAdjustedPerShareData() {
        val data = FinancialData.parse(document(), "7203")
        assertEquals("2025/03", data.period)
        assertEquals(10.0, data.profile.operatingMargin, 0.0)
        assertEquals(15.0, data.profile.fcfMargin!!, 0.0)
        assertEquals(35.0, data.profile.payoutRatio, 0.0)
        assertEquals(-1, data.profile.noCutYears)
        assertEquals("10.0倍", data.profile.getPer(500.0))
        assertEquals(30.0, data.dividends.last().dps, 0.0)
    }
    @Test fun missingDataDoesNotProduceInventedMetricsOrScores() {
        val data = FinancialData.parse(JSONObject("""{"security":{"code":"7203"}}"""), "7203")
        assertTrue(data.profile.payoutRatio.isNaN())
        assertEquals(-1, data.profile.healthScore)
        assertEquals("未取得", data.profile.getPer(1000.0))
        assertEquals("未取得", data.profile.netDeRatio)
        assertEquals("未取得", data.profile.payoutRatio.financialText())
        assertTrue(data.dividends.isEmpty())
    }
    @Test fun readsCalculatedDoeAndNetDeFromTheLatestAnnualFinancialRecord() {
        val root = document()
        val annual = root.getJSONObject("financials").getJSONArray("annual").getJSONObject(0)
            .put("calculated_doe", 3.25)
            .put("net_de_ratio", -0.22)
            .put("field_status", JSONObject().put("calculated_doe", "available").put("net_de_ratio", "available"))

        val data = FinancialData.parse(root, "7203")

        assertEquals("3.25%", data.profile.calculatedDoe)
        assertEquals("2025/03 計算値", data.profile.calculatedDoeSub)
        assertEquals("-0.22倍", data.profile.netDeRatio)
        assertEquals(-0.22, data.values["net_de_ratio"]!!, 0.0)
        assertEquals("2025/03", annual.getString("fiscal_year"))
    }
    @Test fun convertsCurrentYahooPayoutRatioFractionToPercentagePoints() {
        val root = JSONObject("""{"security":{"code":"7203"},"dividends":{"payout_ratio":0.25}}""")
        val data = FinancialData.parse(root, "7203")
        assertEquals(25.0, data.profile.payoutRatio, 0.0)
    }
    @Test fun identifiesObservedCalendarYearDistributionsAndCalculatesTheirCagr() {
        val root = JSONObject("""{"security":{"code":"1306"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2024,"calendar_year_distribution_per_share":100},{"calendar_year":2025,"calendar_year_distribution_per_share":110}]}}""")
        val data = FinancialData.parse(root, "1306")
        assertEquals("calendar_year_observed_distributions", data.dividendHistoryBasis)
        assertEquals(110.0, data.dividends.last().dps, 0.0)
        assertEquals(-1, data.profile.noCutYears)
        assertEquals(10.0, data.profile.growthRate, 0.000001)
    }
    @Test fun preservesUnverifiedDividendShareBasisForBothHistorySources() {
        val root = JSONObject("""{
          "security":{"code":"8303"},
          "dividends":{"history_basis":"calendar_year_observed_distributions","share_basis_status":"unverified_incomplete_corporate_action_history"},
          "issuer_dividends":{"status":"available","share_basis_status":"unverified_incomplete_corporate_action_history","records":[{"fiscal_year":2025,"annual_dps":40000000,"annual_dps_adjusted":40000000}]}
        }""")

        val data = FinancialData.parse(root, "8303")

        assertEquals("unverified_incomplete_corporate_action_history", data.dividendShareBasisStatus)
        assertEquals("unverified_incomplete_corporate_action_history", data.issuerDividendShareBasisStatus)
        assertEquals(40000000.0, data.issuerDividends.single().displayDps!!, 0.0)
    }
    @Test fun calendarYearCagrUsesOnlyTheLatestFiveConsecutiveYears() {
        val root = JSONObject("""{"security":{"code":"1306"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2020,"calendar_year_distribution_per_share":1},{"calendar_year":2021,"calendar_year_distribution_per_share":10},{"calendar_year":2022,"calendar_year_distribution_per_share":20},{"calendar_year":2023,"calendar_year_distribution_per_share":40},{"calendar_year":2024,"calendar_year_distribution_per_share":80},{"calendar_year":2025,"calendar_year_distribution_per_share":160}]}}""")
        assertEquals(100.0, FinancialData.parse(root, "1306").profile.growthRate, 0.000001)
    }
    @Test fun calendarYearCagrRemainsUnknownWhenItsStartingDividendIsZero() {
        val root = JSONObject("""{"security":{"code":"1306"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2024,"calendar_year_distribution_per_share":0},{"calendar_year":2025,"calendar_year_distribution_per_share":10}]}}""")
        assertTrue(FinancialData.parse(root, "1306").profile.growthRate.isNaN())
    }
    @Test fun duplicateFiscalYearUsesLastPublishedRecordOnlyOnce() {
        val root = JSONObject("""{"security":{"code":"7203"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2024,"calendar_year_distribution_per_share":10},{"calendar_year":2024,"calendar_year_distribution_per_share":12},{"calendar_year":2025,"calendar_year_distribution_per_share":15}]}}""")
        val data = FinancialData.parse(root, "7203")
        assertEquals(listOf(2024, 2025), data.dividends.map { it.year })
        assertEquals(12.0, data.dividends.first().dps, 0.0)
    }
    @Test fun rejectsOtherSecurityAndIgnoresRowsWithoutDistributionAmounts() {
        assertThrows(IllegalArgumentException::class.java) { FinancialData.parse(document(), "2914") }
        val root = document()
        root.getJSONObject("dividends").getJSONArray("records").getJSONObject(1).remove("calendar_year_distribution_per_share")
        val data = FinancialData.parse(root, "7203")
        assertEquals(2, data.dividends.size)
        assertTrue(data.profile.growthRate.isNaN())
        assertEquals(-1, data.profile.noCutYears)
    }
    @Test fun zeroSalesAndNegativeCashFlowAreHandled() {
        val root = document()
        root.getJSONObject("financials").getJSONArray("annual").getJSONObject(0).put("sales", 0).put("investing_cf", -300)
        val data = FinancialData.parse(root, "7203")
        assertTrue(data.profile.operatingMargin.isNaN())
        assertNull(data.profile.fcfMargin)
        assertEquals("フリーCF赤字", data.profile.fcfStatus)
    }
    @Test fun portfolioHistoryHasMissingGapsAndUsesCurrentAccountAllocation() {
        val data = FinancialData.parse(document(), "7203")
        val h = Holding("id", "7203", "トヨタ", "輸送用機器", "東証", Category.JAPAN, 1000.0, shares = 100, acqPrice = 1000.0, dps = 30.0, yieldRate = 3.0, yoc = 3.0, nextPayoutTiming = "", nextPayoutAmount = "", dividendMonths = emptyList(), dividendType = "", accountType = "特定", dailyChange = "", isPositive = true, taxFreeShares = 50)
        val years = DividendHistory.calculate(listOf(h), mapOf("7203" to data), 2022..2025, "net")
        assertNull(years.first().amount)
        assertEquals(2695.275, years.last().amount!!, .0001)
        val incomplete = DividendHistory.calculate(listOf(h, h.copy(code = "2914")), mapOf("7203" to data), 2025..2025, "gross").single()
        assertEquals(3000.0, incomplete.amount!!, 0.0)
        assertEquals(1, incomplete.covered)
        assertEquals(2, incomplete.expected)
    }
    @Test fun zeroDividendRecordCountsAsCoveredAndMissingHoldingsAreExcluded() {
        val zeroDocument = document()
        zeroDocument.getJSONObject("dividends").getJSONArray("records").put(
            JSONObject().put("calendar_year", 2022).put("calendar_year_distribution_per_share", 0)
        )
        val zeroData = FinancialData.parse(zeroDocument, "7203")
        val h = Holding("id", "7203", "トヨタ", "輸送用機器", "東証", Category.JAPAN, 1000.0, shares = 100, acqPrice = 1000.0, dps = 30.0, yieldRate = 3.0, yoc = 3.0, nextPayoutTiming = "", nextPayoutAmount = "", dividendMonths = emptyList(), dividendType = "", accountType = "特定", dailyChange = "", isPositive = true)
        val zeroYear = DividendHistory.calculate(listOf(h, h.copy(code = "2914")), mapOf("7203" to zeroData), 2022..2022, "gross").single()
        assertEquals(0.0, zeroYear.amount!!, 0.0)
        assertEquals(1, zeroYear.covered)
        assertEquals(2, zeroYear.expected)
    }
}
