package com.kurogane.dividend.data

import com.kurogane.dividend.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinancialDataTest {
    private fun parseFixture(root: JSONObject, expectedCode: String) = FinancialData.parse(
        root.apply {
            fun JSONObject.tagUnits(definitions: Map<String, String>) {
                val units = optJSONObject("field_units") ?: JSONObject()
                definitions.forEach { (field, unit) ->
                    if (has(field) && !isNull(field) && (!units.has(field) || units.isNull(field))) {
                        units.put(field, unit)
                    }
                }
                put("field_units", units)
            }
            val financials = optJSONObject("financials")?.optJSONArray("annual")
            for (index in 0 until (financials?.length() ?: 0)) {
                financials?.optJSONObject(index)?.tagUnits(mapOf(
                    "sales" to "jpy", "operating_profit" to "jpy", "operating_cf" to "jpy", "investing_cf" to "jpy",
                    "operating_margin" to "percent", "fcf_margin" to "percent", "equity_ratio_computed" to "percent",
                    "calculated_doe" to "percent", "net_de_ratio" to "times",
                ))
            }
            val valuation = optJSONObject("valuation")?.optJSONArray("history")
            for (index in 0 until (valuation?.length() ?: 0)) {
                valuation?.optJSONObject(index)?.tagUnits(mapOf(
                    "eps_adjusted" to "jpy_per_share", "bps_adjusted" to "jpy_per_share",
                    "dividend_yield_adjusted" to "percent",
                ))
            }
            val dividends = optJSONObject("dividends")
            dividends?.tagUnits(mapOf("payout_ratio" to "percent"))
            val records = dividends?.optJSONArray("records")
            for (index in 0 until (records?.length() ?: 0)) {
                records?.optJSONObject(index)?.tagUnits(mapOf(
                    "calendar_year_distribution_per_share" to "jpy_per_share",
                    "annual_dps" to "jpy_per_share", "annual_dps_adjusted" to "jpy_per_share",
                    "payout_ratio" to "percent", "doe" to "percent",
                ))
            }
            val issuerDividends = optJSONObject("issuer_dividends")
            val issuerRecords = issuerDividends?.optJSONArray("records")
            for (index in 0 until (issuerRecords?.length() ?: 0)) {
                issuerRecords?.optJSONObject(index)?.tagUnits(mapOf(
                    "annual_dps" to "jpy_per_share", "annual_dps_adjusted" to "jpy_per_share",
                    "payout_ratio" to "percent",
                ))
            }
            issuerDividends?.optJSONObject("forecast")?.tagUnits(mapOf(
                "annual_dps_current_share_basis" to "jpy_per_share",
            ))
            optJSONObject("market")?.tagUnits(mapOf("forwardEps" to "jpy_per_share"))
        },
        expectedCode,
    )

    @Test fun issuerDividendPeriodsInTheSameYearAreNotCollapsed() {
        val data = parseFixture(JSONObject("""{
            "security":{"code":"4502"},
            "issuer_dividends":{"records":[
                {"fiscal_year":2026,"period":"2026/03","annual_dps":10},
                {"fiscal_year":2026,"period":"2026/12","annual_dps":20}
            ]}
        }"""), "4502")
        assertEquals(listOf("2026-03", "2026-12"), data.issuerDividends.map { it.fiscalPeriod })
        assertEquals(listOf(10.0, 20.0), data.issuerDividends.map { it.displayDps })
    }
    private fun periods(annual: String, valuation: String) = parseFixture(
        JSONObject("""{"security":{"code":"7203"},"financials":{"annual":$annual},"valuation":{"history":$valuation}}"""),
        "7203"
    )

    @Test fun latestFullDateWinsAndValuationMatchesDayRegardlessOfArrayOrder() {
        for (reverse in listOf(false, true)) {
            val rows = listOf(
                """{"period_end":"2025-03-20","fiscal_year":"2025/03","net_de_ratio":1}""",
                """{"period_end":"2025-03-31","fiscal_year":"2025/03","net_de_ratio":2}"""
            ).let { if (reverse) it.reversed() else it }
            val data = periods(rows.joinToString(",", "[", "]"), """[
                {"period_end":"2025-03-20","fiscal_year":"2025/03","eps_adjusted":10},
                {"period_end":"2025-03-31","fiscal_year":"2025/03","eps_adjusted":20}
            ]""")
            assertEquals("2025/03", data.period)
            assertEquals("2.00倍", data.profile.netDeRatio)
            assertEquals(20.0, data.profile.eps, 0.0)
        }
    }

    @Test fun dateIsAuthoritativeEvenWithoutOrWithConflictingFiscalLabel() {
        val data = periods("""[
            {"period_end":"2025-03-31","fiscal_year":"2099/12","net_de_ratio":1},
            {"period_end":"2025-04-01","net_de_ratio":2}
        ]""", """[{"period_end":"2025-04-01","eps_adjusted":30}]""")
        assertEquals("2025/04", data.period)
        assertEquals("2.00倍", data.profile.netDeRatio)
        assertEquals(30.0, data.profile.eps, 0.0)
    }

    @Test fun differentDayCannotMatchByMonth() {
        val data = periods("""[{"period_end":"2025-03-31","fiscal_year":"2025/03"}]""",
            """[{"period_end":"2025-03-20","fiscal_year":"2025/03","eps_adjusted":10}]""")
        assertTrue(data.profile.eps.isNaN())
    }

    @Test fun legacyFallbackRequiresOneUnambiguousPeriodAndOneCandidate() {
        val dated = """[{"period_end":"2025-03-31","fiscal_year":"2025/03"}]"""
        val legacy = """[{"fiscal_year":"2025/03","eps_adjusted":10}]"""
        assertEquals(10.0, periods(dated, legacy).profile.eps, 0.0)
        assertEquals(10.0, periods("""[{"fiscal_year":"2025/03"}]""",
            """[{"period_end":"2025-03-31","eps_adjusted":10}]""").profile.eps, 0.0)
        assertTrue(periods("""[
            {"period_end":"2025-03-20"},{"period_end":"2025-03-31"}
        ]""", legacy).profile.eps.isNaN())
        assertTrue(periods(dated, """[
            {"fiscal_year":"2025/03","eps_adjusted":10},
            {"fiscal_year":"2025/03","eps_adjusted":20}
        ]""").profile.eps.isNaN())
    }

    @Test fun invalidExplicitDateDoesNotBecomeLegacyAndLegacyMonthCanStillBeLatest() {
        val data = periods("""[
            {"period_end":"2026-02-30","fiscal_year":"2026/02","net_de_ratio":9},
            {"period_end":"2025-03-31","net_de_ratio":1},
            {"fiscal_year":"2025/04","net_de_ratio":2}
        ]""", """[{"period_end":"invalid","fiscal_year":"2025/04","eps_adjusted":10}]""")
        assertEquals("2025/04", data.period)
        assertEquals("2.00倍", data.profile.netDeRatio)
        assertTrue(data.profile.eps.isNaN())
    }

    private fun document() = JSONObject("""{
      "security":{"code":"7203"},
      "financials":{"annual":[{"fiscal_year":"2025/03","sales":1000,"operating_profit":100,"operating_cf":200,"investing_cf":-50,"equity_ratio_computed":40}]},
      "dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2023,"calendar_year_distribution_per_share":10},{"calendar_year":2024,"calendar_year_distribution_per_share":20},{"calendar_year":2025,"calendar_year_distribution_per_share":30,"payout_ratio":35,"doe":3,"consecutive_dividend_increase_years":2}]},
      "valuation":{"history":[{"fiscal_year":"2025/03","eps_adjusted":50,"bps_adjusted":200,"dividend_yield_adjusted":3}]}
    }""")
    @Test fun calculatesMarginsAndUsesAdjustedPerShareData() {
        val data = parseFixture(document(), "7203")
        assertEquals("2025/03", data.period)
        assertEquals(10.0, data.profile.operatingMargin, 0.0)
        assertEquals(15.0, data.profile.fcfMargin!!, 0.0)
        assertEquals(35.0, data.profile.payoutRatio, 0.0)
        assertEquals(-1, data.profile.noCutYears)
        assertEquals("10.0倍", data.profile.getPer(500.0))
        assertEquals(30.0, data.dividends.last().dps, 0.0)
    }
    @Test fun usesVerifiedPercentagesAndRejectsYahooNativeMoneyAsYen() {
        val root = JSONObject("""{
          "security":{"code":"7203"},
          "financials":{"annual":[{
            "fiscal_year":"2025/03","sales":1000,"operating_profit":100,
            "operating_cf":200,"investing_cf":-50,"operating_margin":10,
            "fcf_margin":15,"equity_ratio_computed":40,
            "field_units":{
              "sales":"yfinance_native","operating_profit":"yfinance_native",
              "operating_cf":"yfinance_native","investing_cf":"yfinance_native",
              "operating_margin":"percent","fcf_margin":"percent","equity_ratio_computed":"percent"
            }
          }]}
        }""")

        val data = parseFixture(root, "7203")

        assertEquals(10.0, data.profile.operatingMargin, 0.0)
        assertEquals(15.0, data.profile.fcfMargin!!, 0.0)
        assertEquals("フリーキャッシュフロー黒字", data.profile.fcfStatus)
        assertEquals(40.0, data.profile.equityRatio, 0.0)
        assertNull(data.values["sales"])
    }
    @Test fun missingDataDoesNotProduceInventedMetricsOrScores() {
        val data = parseFixture(JSONObject("""{"security":{"code":"7203"}}"""), "7203")
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

        val data = parseFixture(root, "7203")

        assertEquals("3.25%", data.profile.calculatedDoe)
        assertEquals("2025/03 計算値", data.profile.calculatedDoeSub)
        assertEquals("-0.22倍", data.profile.netDeRatio)
        assertEquals(-0.22, data.values["net_de_ratio"]!!, 0.0)
        assertEquals("2025/03", annual.getString("fiscal_year"))
    }
    @Test fun readsCanonicalPayoutRatioAsPercentagePoints() {
        val root = JSONObject("""{"security":{"code":"7203"},"dividends":{"payout_ratio":25}}""")
        val data = parseFixture(root, "7203")
        assertEquals(25.0, data.profile.payoutRatio, 0.0)
    }
    @Test fun payoutRatioUsesDeclaredUnitForAnnualAndTopLevelValues() {
        val annualPercent = JSONObject("""{
          "security":{"code":"7203"},
          "financials":{"annual":[{"period_end":"2025-03-31"}]},
          "dividends":{"records":[{"calendar_year":2025,"payout_ratio":25,
            "field_units":{"payout_ratio":"percent"}}]}
        }""")
        val topLevelFraction = JSONObject("""{
          "security":{"code":"7203"},
          "dividends":{"payout_ratio":0.25,"field_units":{"payout_ratio":"ratio"}}
        }""")
        val unknownUnit = JSONObject("""{
          "security":{"code":"7203"},
          "dividends":{"payout_ratio":25,"field_units":{"payout_ratio":"unknown"}}
        }""")

        assertEquals(25.0, parseFixture(annualPercent, "7203").profile.payoutRatio, 0.0)
        assertEquals(25.0, parseFixture(topLevelFraction, "7203").profile.payoutRatio, 0.0)
        assertTrue(parseFixture(unknownUnit, "7203").profile.payoutRatio.isNaN())
    }
    @Test fun identifiesObservedCalendarYearDistributionsAndCalculatesTheirCagr() {
        val root = JSONObject("""{"security":{"code":"1306"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2024,"calendar_year_distribution_per_share":100},{"calendar_year":2025,"calendar_year_distribution_per_share":110}]}}""")
        val data = parseFixture(root, "1306")
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

        val data = parseFixture(root, "8303")

        assertEquals("unverified_incomplete_corporate_action_history", data.dividendShareBasisStatus)
        assertEquals("unverified_incomplete_corporate_action_history", data.issuerDividendShareBasisStatus)
        assertEquals(40000000.0, data.issuerDividends.single().displayDps!!, 0.0)
    }
    @Test fun calendarYearCagrUsesOnlyTheLatestFiveConsecutiveYears() {
        val root = JSONObject("""{"security":{"code":"1306"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2020,"calendar_year_distribution_per_share":1},{"calendar_year":2021,"calendar_year_distribution_per_share":10},{"calendar_year":2022,"calendar_year_distribution_per_share":20},{"calendar_year":2023,"calendar_year_distribution_per_share":40},{"calendar_year":2024,"calendar_year_distribution_per_share":80},{"calendar_year":2025,"calendar_year_distribution_per_share":160}]}}""")
        assertEquals(100.0, parseFixture(root, "1306").profile.growthRate, 0.000001)
    }
    @Test fun calendarYearCagrRemainsUnknownWhenItsStartingDividendIsZero() {
        val root = JSONObject("""{"security":{"code":"1306"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2024,"calendar_year_distribution_per_share":0},{"calendar_year":2025,"calendar_year_distribution_per_share":10}]}}""")
        assertTrue(parseFixture(root, "1306").profile.growthRate.isNaN())
    }
    @Test fun duplicateFiscalYearUsesLastPublishedRecordOnlyOnce() {
        val root = JSONObject("""{"security":{"code":"7203"},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2024,"calendar_year_distribution_per_share":10},{"calendar_year":2024,"calendar_year_distribution_per_share":12},{"calendar_year":2025,"calendar_year_distribution_per_share":15}]}}""")
        val data = parseFixture(root, "7203")
        assertEquals(listOf(2024, 2025), data.dividends.map { it.year })
        assertEquals(12.0, data.dividends.first().dps, 0.0)
    }
    @Test fun rejectsOtherSecurityAndIgnoresRowsWithoutDistributionAmounts() {
        assertThrows(IllegalArgumentException::class.java) { parseFixture(document(), "2914") }
        val root = document()
        root.getJSONObject("dividends").getJSONArray("records").getJSONObject(1).remove("calendar_year_distribution_per_share")
        val data = parseFixture(root, "7203")
        assertEquals(2, data.dividends.size)
        assertTrue(data.profile.growthRate.isNaN())
        assertEquals(-1, data.profile.noCutYears)
    }
    @Test fun zeroSalesAndNegativeCashFlowAreHandled() {
        val root = document()
        root.getJSONObject("financials").getJSONArray("annual").getJSONObject(0).put("sales", 0).put("investing_cf", -300)
        val data = parseFixture(root, "7203")
        assertTrue(data.profile.operatingMargin.isNaN())
        assertNull(data.profile.fcfMargin)
        assertEquals("フリーキャッシュフロー赤字", data.profile.fcfStatus)
    }
    @Test fun portfolioHistoryHasMissingGapsAndUsesCurrentAccountAllocation() {
        val data = parseFixture(document(), "7203")
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
        val zeroData = parseFixture(zeroDocument, "7203")
        val h = Holding("id", "7203", "トヨタ", "輸送用機器", "東証", Category.JAPAN, 1000.0, shares = 100, acqPrice = 1000.0, dps = 30.0, yieldRate = 3.0, yoc = 3.0, nextPayoutTiming = "", nextPayoutAmount = "", dividendMonths = emptyList(), dividendType = "", accountType = "特定", dailyChange = "", isPositive = true)
        val zeroYear = DividendHistory.calculate(listOf(h, h.copy(code = "2914")), mapOf("7203" to zeroData), 2022..2022, "gross").single()
        assertEquals(0.0, zeroYear.amount!!, 0.0)
        assertEquals(1, zeroYear.covered)
        assertEquals(2, zeroYear.expected)
    }
}
