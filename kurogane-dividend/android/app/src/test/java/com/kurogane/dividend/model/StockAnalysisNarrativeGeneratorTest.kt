package com.kurogane.dividend.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StockAnalysisNarrativeGeneratorTest {
    private val ranges = linkedMapOf(
        "per" to listOf(0.0 to 10.0, 10.0 to 15.0, 15.0 to 20.0, 20.0 to 30.0, 30.0 to 60.0),
        "pbr" to listOf(0.0 to 0.8, 0.8 to 1.2, 1.2 to 2.0, 2.0 to 3.5, 3.5 to 7.0),
        "payout_ratio" to listOf(0.0 to 30.0, 30.0 to 50.0, 50.0 to 70.0, 70.0 to 90.0, 90.0 to 150.0),
        "equity_ratio" to listOf(-20.0 to 15.0, 15.0 to 25.0, 25.0 to 40.0, 40.0 to 60.0, 60.0 to 100.0),
        "operating_margin" to listOf(-20.0 to 2.0, 2.0 to 5.0, 5.0 to 10.0, 10.0 to 15.0, 15.0 to 30.0),
        "fcf_margin" to listOf(-30.0 to -5.0, -5.0 to 0.0, 0.0 to 5.0, 5.0 to 10.0, 10.0 to 30.0),
        "dividend_growth_years" to listOf(0.0 to 1.0, 1.0 to 3.0, 3.0 to 5.0, 5.0 to 10.0, 10.0 to 25.0)
    )

    private val baseline = mapOf(
        "per" to 17.0,
        "pbr" to 1.6,
        "payout_ratio" to 40.0,
        "equity_ratio" to 35.0,
        "operating_margin" to 7.0,
        "fcf_margin" to 3.0,
        "dividend_growth_years" to 4.0
    )

    @Test
    fun sevenMetricsHaveTheExpectedNumberOfCompletePatterns() {
        val result = StockAnalysisNarrativeGenerator.generate(baseline)

        assertEquals(7, result.findings.size)
        assertEquals(StockAnalysisNarrative.TOTAL_COMBINATIONS, result.possibleCombinations)
        assertEquals(45L * 45L * 45L * 45L * 45L * 45L * 45L, result.possibleCombinations)
        assertTrue(result.complete)
        assertNotNull(result.combinationIndex)
    }

    @Test
    fun eachMetricHasFortyFiveDistinctBandAndFinePositionCodes() {
        ranges.forEach { (key, metricRanges) ->
            val codes = metricRanges.flatMap { (low, high) ->
                (0 until 9).map { positionIndex ->
                    val fraction = (positionIndex + 0.5) / 9.0
                    val values = baseline.toMutableMap()
                    values[key] = low + (high - low) * fraction
                    StockAnalysisNarrativeGenerator.generate(values).combinationIndex
                }
            }
            assertEquals("$key must have 5 x 9 distinct selections", 45, codes.toSet().size)
        }
    }

    @Test
    fun missingMetricsAreNotInventedAndReduceActivePatternCount() {
        val values = baseline.toMutableMap().apply { remove("fcf_margin") }
        val result = StockAnalysisNarrativeGenerator.generate(values)

        assertEquals(6, result.findings.size)
        assertEquals(listOf("FCF余力"), result.missingLabels)
        assertEquals(45L * 45L * 45L * 45L * 45L * 45L, result.possibleCombinations)
        assertNull(result.combinationIndex)
    }

    private fun context(key: String, values: List<Double>, source: String = "yfinance") = StockAnalysisContext(
        mapOf(key to values.mapIndexed { index, value ->
            MetricHistoryPoint(java.time.LocalDate.of(2021 + index, 3, 31), value, source)
        })
    )

    @Test fun payoutExtremeExplainsMeaningWithoutEchoingItsLabel() {
        val result = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf("payout_ratio" to 280.18))
        val text = result.findings.single().text
        assertTrue(text.contains("配当額が利益を大きく超え"))
        for (repetition in listOf("280.18", "要警戒", "上限寄り", "上端", "帯の", "「")) {
            assertFalse(text, text.contains(repetition))
        }
        assertTrue(text.contains("現金"))
    }

    @Test fun approvedExtremeBoundariesHaveDifferentEconomicMeaning() {
        val pairs = listOf(
            Triple("per", 5.0, 4.99), Triple("per", 60.0, 60.01), Triple("per", 100.0, 100.01),
            Triple("pbr", 0.3, 0.29), Triple("pbr", 7.0, 7.01), Triple("pbr", 10.0, 10.01),
            Triple("payout_ratio", 100.0, 100.01), Triple("payout_ratio", 120.0, 120.01),
            Triple("payout_ratio", 200.0, 200.01), Triple("equity_ratio", 10.0, 9.99),
            Triple("equity_ratio", 100.0, 100.01), Triple("operating_margin", -20.0, -20.01),
            Triple("operating_margin", 30.0, 30.01), Triple("fcf_margin", -30.0, -30.01),
            Triple("fcf_margin", 30.0, 30.01), Triple("dividend_growth_years", 9.0, 10.0),
            Triple("dividend_growth_years", 19.0, 20.0)
        )
        pairs.forEach { (key, boundary, outside) ->
            val a = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf(key to boundary)).findings.single()
            val b = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf(key to outside)).findings.single()
            assertNotEquals("$key at $boundary / $outside", a.text, b.text)
            assertEquals(StockAnalysisNarrativeGenerator.generate(mapOf(key to outside)).score, b.score)
        }
        assertNotEquals(StockMetricCommentary.extreme("per", -1.0), StockMetricCommentary.extreme("per", 0.0))
        assertNotEquals(StockMetricCommentary.extreme("equity_ratio", -1.0), StockMetricCommentary.extreme("equity_ratio", 0.0))
        assertNotNull(StockMetricCommentary.extreme("operating_margin", -0.0049))
        assertNull(StockMetricCommentary.extreme("operating_margin", 0.005))
    }

    @Test fun annualChangeUsesActualHistoryWithoutAppendingTodaysQuote() {
        val historical = context("per", listOf(10.0, 12.0)).copy(history = context("per", listOf(10.0, 12.0)).history.mapValues { (_, points) ->
            points.map { it.copy(basis = MetricHistoryPoint.YEAR_END) }
        })
        val result = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf("per" to 19.67), historical)
        val text = result.findings.single().text
        assertTrue(text, text.contains("2.00倍"))
        assertTrue(text, text.contains("期末実績"))
        assertFalse(text, text.contains("19.67"))
        assertFalse(text, text.contains("9.67"))
    }

    @Test fun recoversToPositiveAndDetectsAReversalRatherThanOnlyNetChange() {
        val recovery = StockAnalysisNarrativeGenerator.generatePositionDetailed(
            mapOf("operating_margin" to 2.0), context("operating_margin", listOf(-5.0, -2.0, 2.0))
        ).findings.single().text
        assertTrue(recovery, recovery.contains("プラスに転じ"))
        val volatile = StockAnalysisNarrativeGenerator.generateDetailed(
            mapOf("operating_margin" to 2.0), context("operating_margin", listOf(2.0, 8.0, 2.0))
        ).findings.single().text
        assertTrue(volatile, volatile.contains("上下の変動"))
    }

    @Test fun missingHistoryAndSourceOrPeriodChangesUseAvailableFacts() {
        val values = mapOf("payout_ratio" to 45.93)
        val simple = StockAnalysisNarrativeGenerator.generatePositionDetailed(values).findings.single().text
        val points = context("payout_ratio", listOf(20.0, 30.0, 40.0)).history.getValue("payout_ratio")
        val switched = StockAnalysisContext(mapOf("payout_ratio" to points.mapIndexed { index, point ->
            if (index == 2) point.copy(source = "IRBANK") else point
        }))
        assertEquals(simple, StockAnalysisNarrativeGenerator.generatePositionDetailed(values, switched).findings.single().text)
        val gap = StockAnalysisContext(mapOf("payout_ratio" to listOf(points.first(), points.last())))
        assertEquals(simple, StockAnalysisNarrativeGenerator.generatePositionDetailed(values, gap).findings.single().text)
        for (text in listOf(simple, StockAnalysisNarrativeGenerator.generateDetailed(values).findings.single().text)) {
            for (phrase in listOf("複数年分", "履歴がない", "履歴不足", "データがない", "比較できません")) assertFalse(text, text.contains(phrase))
        }
    }

    @Test fun relatedIndicatorsRequireTheLatestMatchingActualPeriod() {
        val payout = context("payout_ratio", listOf(40.0, 45.0)).history.getValue("payout_ratio")
        val fcf = context("fcf_margin", listOf(-1.0, -2.0)).history.getValue("fcf_margin")
        fun text(points: List<MetricHistoryPoint>) = StockAnalysisNarrativeGenerator.generateDetailed(
            mapOf("payout_ratio" to 45.0), StockAnalysisContext(mapOf("payout_ratio" to payout, "fcf_margin" to points))
        ).findings.single().text
        assertTrue(text(fcf), text(fcf).contains("投資後の資金不足"))
        assertTrue(text(fcf), text(fcf).contains("2022/03"))
        assertFalse(text(fcf.dropLast(1)).contains("投資後の資金不足"))
        assertFalse(text(fcf.map { it.copy(basis = MetricHistoryPoint.CALENDAR_ACTUAL) }).contains("投資後の資金不足"))
        assertFalse(text(fcf.map { it.copy(periodPrecision = "month") }).contains("投資後の資金不足"))
    }

    @Test fun dividendRestartIsInferredFromObservedCompletedYearsOnly() {
        val history = context("dividend_dps", listOf(20.0, 10.0, 15.0)).history.mapValues { (_, points) ->
            points.map { it.copy(basis = MetricHistoryPoint.CALENDAR_ACTUAL) }
        }
        val text = StockDetailCommentGenerator.dividendIncreaseYears(1, "短期実績", "下限寄り", StockAnalysisContext(history))
        assertTrue(text, text.contains("減配後に再び"))
        assertFalse(text.contains("1年"))
        assertFalse(text.contains("短期実績"))
        assertFalse(text.contains("下限寄り"))
    }

    @Test fun shortCommentsStayWithinBudgetEvenWhenAllEvidenceIsPresent() {
        val history = baseline.keys.associateWith { key ->
            context(key, when (key) {
                "payout_ratio" -> listOf(60.0, 110.0, 150.0)
                "fcf_margin" -> listOf(2.0, -1.0, -5.0)
                else -> listOf(1.0, 2.0, 3.0)
            }).history.getValue(key)
        }
        val rich = StockAnalysisContext(history)
        val extremes = mapOf("per" to 105.0, "pbr" to 11.0, "payout_ratio" to 280.18,
            "equity_ratio" to -5.0, "operating_margin" to -25.0, "fcf_margin" to -35.0, "dividend_growth_years" to 25.0)
        for (values in listOf(baseline, extremes)) {
            val score = StockAnalysisNarrativeGenerator.generate(values)
            val short = StockAnalysisNarrativeGenerator.generatePositionDetailed(values, rich)
            assertEquals(score.score, short.score)
            assertEquals(score.combinationIndex, short.combinationIndex)
            short.findings.forEach { finding ->
                assertTrue("${finding.key}: ${finding.text.length} ${finding.text}", finding.text.length <= 140)
                assertFalse(finding.text.contains(finding.formattedValue))
                assertTrue(finding.text.endsWith("。"))
            }
        }
        val additional = listOf(
            StockDetailCommentGenerator.dividendYield(2.65, 2.0, 3.0, 4.0, rich),
            StockDetailCommentGenerator.nonCutYears(15, "10年超", "中央", rich),
            StockDetailCommentGenerator.annualDividendCagr(2.4, true, "増加", "基準超過", rich),
            StockDetailCommentGenerator.dividendDependency(30.0, "高リスク", "基準超過", rich),
            StockDetailCommentGenerator.halfDividendImpact(15.0, "高依存", "基準超過", rich),
            StockDetailCommentGenerator.sectorConcentration("卸売業", 40.0, "高集中", "基準超過", rich)
        )
        additional.forEach { assertTrue("${it.length}: $it", it.length <= 140) }
    }

    @Test fun extremeFiniteInputsDoNotLeakArithmeticOverflowIntoComments() {
        val overflow = context("equity_ratio", listOf(-Double.MAX_VALUE, Double.MAX_VALUE))
        val normal = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf("equity_ratio" to 45.0))
        val result = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf("equity_ratio" to 45.0), overflow)
        assertEquals(normal.findings.single().text, result.findings.single().text)
        val huge = StockAnalysisNarrativeGenerator.generateDetailed(
            mapOf("equity_ratio" to 45.0), context("equity_ratio", listOf(1.0, 1e100))
        ).findings.single().text
        assertFalse(huge.contains("Infinity"))
        assertFalse(huge.contains("NaN"))
        assertTrue(huge, huge.contains("大幅に上昇"))
        assertTrue(huge.length < 350)
    }
}
