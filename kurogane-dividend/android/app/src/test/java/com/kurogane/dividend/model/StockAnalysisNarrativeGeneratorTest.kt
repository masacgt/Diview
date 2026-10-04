package com.kurogane.dividend.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
