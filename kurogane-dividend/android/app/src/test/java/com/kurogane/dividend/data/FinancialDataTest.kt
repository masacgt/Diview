package com.kurogane.dividend.data

import com.kurogane.dividend.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinancialDataTest {
    private fun document() = JSONObject("""{
      "security":{"code":"7203"},
      "financials":{"annual":[{"fiscal_year":"2025/03","sales":1000,"operating_profit":100,"operating_cf":200,"investing_cf":-50,"equity_ratio_computed":40}]},
      "dividends":{"records":[{"fiscal_year":2023,"annual_dps_adjusted":10},{"fiscal_year":2024,"annual_dps_adjusted":20},{"fiscal_year":2025,"annual_dps_adjusted":30,"payout_ratio":35,"doe":3,"consecutive_dividend_increase_years":2}]},
      "valuation":{"history":[{"fiscal_year":"2025/03","eps_adjusted":50,"bps_adjusted":200,"dividend_yield_adjusted":3}]}
    }""")
    @Test fun calculatesMarginsAndUsesAdjustedPerShareData() {
        val data = FinancialData.parse(document(), "7203")
        assertEquals("2025/03", data.period)
        assertEquals(10.0, data.profile.operatingMargin, 0.0)
        assertEquals(15.0, data.profile.fcfMargin!!, 0.0)
        assertEquals(35.0, data.profile.payoutRatio, 0.0)
        assertEquals(2, data.profile.noCutYears)
        assertEquals("10.0倍", data.profile.getPer(500.0))
        assertEquals(30.0, data.dividends.last().dps, 0.0)
    }
    @Test fun missingDataDoesNotProduceInventedMetricsOrScores() {
        val data = FinancialData.parse(JSONObject("""{"security":{"code":"7203"}}"""), "7203")
        assertTrue(data.profile.payoutRatio.isNaN())
        assertEquals(-1, data.profile.healthScore)
        assertEquals("未取得", data.profile.getPer(1000.0))
        assertEquals("未取得", data.profile.payoutRatio.financialText())
        assertTrue(data.dividends.isEmpty())
    }
    @Test fun rejectsOtherSecurityAndDoesNotUseUnadjustedHistory() {
        assertThrows(IllegalArgumentException::class.java) { FinancialData.parse(document(), "2914") }
        val root = document()
        root.getJSONObject("dividends").getJSONArray("records").getJSONObject(1).remove("annual_dps_adjusted")
        val data = FinancialData.parse(root, "7203")
        assertEquals(2, data.dividends.size)
        assertTrue(data.profile.growthRate.isNaN())
        assertEquals(0, data.profile.noCutYears)
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
        assertNull(incomplete.amount)
        assertEquals(1, incomplete.covered)
    }
}
