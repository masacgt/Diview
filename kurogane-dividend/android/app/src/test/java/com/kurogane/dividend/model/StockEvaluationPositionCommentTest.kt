package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test

class StockEvaluationPositionCommentTest {
    private val cutoffs = mapOf(
        "per" to listOf(10.0, 15.0, 20.0, 30.0),
        "pbr" to listOf(0.8, 1.2, 2.0, 3.5),
        "payout_ratio" to listOf(30.0, 50.0, 70.0, 90.0),
        "equity_ratio" to listOf(15.0, 25.0, 40.0, 60.0),
        "operating_margin" to listOf(2.0, 5.0, 10.0, 15.0),
        "fcf_margin" to listOf(-5.0, 0.0, 5.0, 10.0),
        "dividend_growth_years" to listOf(1.0, 3.0, 5.0, 10.0)
    )

    @Test fun actualThresholdsKeepTheirOriginalBandsScoresAndCodes() {
        cutoffs.forEach { (key, edges) ->
            edges.flatMap { listOf(it - 0.01, it, it + 0.01) }.filter { key != "dividend_growth_years" || it >= 0 }
                .forEach { value ->
                    val values = mapOf(key to value)
                    val numeric = StockAnalysisNarrativeGenerator.generatePositionDetailed(values).findings.single()
                    val continuity = StockAnalysisNarrativeGenerator.generateDetailed(values).findings.single()
                    assertEquals(continuity.bandLabel, numeric.bandLabel)
                    assertEquals(continuity.score, numeric.score)
                    assertEquals(continuity.combinationDigit, numeric.combinationDigit)
                    assertTrue(numeric.text, numeric.text.startsWith("判定範囲は"))
                    assertFalse(numeric.text, numeric.text.contains("FCFも"))
                }
        }
    }

    @Test fun interiorBandShowsBoundariesFractionAndCorrectDifferenceUnit() {
        val text = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf("payout_ratio" to 45.93)).findings.single().text
        assertTrue(text, text.contains("30.00%超〜50.00%以下"))
        assertTrue(text, text.contains("約80%"))
        assertTrue(text, text.contains("上側境界50.00%との差は4.07ポイント"))
        assertFalse(text, text.contains("45.93")) // Already in the unchanged numeric header.
        assertTrue(StockEvaluationPositionComment.range(19.67, 15.0, 20.0, "倍").contains("0.33倍"))
        assertTrue(StockEvaluationPositionComment.range(9.0, 5.0, 10.0, "年").contains("1年"))
    }

    @Test fun openEndedExtremeBandsDoNotInventA150PercentCeiling() {
        val text = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf("payout_ratio" to 280.18)).findings.single().text
        assertTrue(text, text.contains("90.00%超"))
        assertTrue(text, text.contains("上限のない帯"))
        assertTrue(text, text.contains("190.18ポイント上回"))
        assertFalse(text, text.contains("150"))
        assertEquals("下側境界超", StockAnalysisNarrativeGenerator.generatePositionDetailed(
            mapOf("payout_ratio" to 280.18)).findings.single().evaluationPositionLabel)
        assertNull(StockAnalysisNarrativeGenerator.generateDetailed(
            mapOf("payout_ratio" to 280.18)).findings.single().evaluationPositionLabel)
        val negative = StockEvaluationPositionComment.threshold(-35.0, cutoffs.getValue("fcf_margin"), "%")
        assertTrue(negative, negative.contains("下限のない帯"))
        assertTrue(negative, negative.contains("30.00ポイント下回"))
    }

    @Test fun equalityUsesInclusiveBoundaryAndNeverClaimsItWasCrossed() {
        val text = StockEvaluationPositionComment.threshold(50.0, cutoffs.getValue("payout_ratio"), "%")
        assertTrue(text, text.contains("50.00%以下"))
        assertTrue(text, text.contains("上側境界上"))
        assertTrue(text, text.contains("0.00ポイント"))
        assertFalse(text, text.contains("上回"))
    }

    @Test fun yieldUsesObservedMinAverageMaxIncludingCollapsedRanges() {
        val below = StockEvaluationPositionComment.yield(1.5, 2.0, 3.0, 4.0)
        assertTrue(below, below.contains("2.00%未満"))
        assertTrue(below, below.contains("0.50ポイント"))
        assertTrue(StockEvaluationPositionComment.yield(2.65, 2.0, 3.0, 4.0).contains("約65%"))
        assertTrue(StockEvaluationPositionComment.yield(3.0, 2.0, 3.0, 4.0).contains("下側境界上"))
        assertTrue(StockEvaluationPositionComment.yield(4.0, 2.0, 3.0, 4.0).contains("上側境界上"))
        assertTrue(StockEvaluationPositionComment.yield(5.0, 2.0, 3.0, 4.0).contains("1.00ポイント上回"))
        assertTrue(StockEvaluationPositionComment.yield(3.0, 3.0, 3.0, 3.0).contains("基準上"))
        assertTrue(StockEvaluationPositionComment.yield(2.0, Double.NaN, 3.0, 4.0).contains("算出できません"))
    }

    @Test fun growthAndRiskUseTheirExistingThresholds() {
        assertTrue(StockEvaluationPositionComment.cagr(2.4).contains("2.40ポイント上回"))
        assertTrue(StockEvaluationPositionComment.cagr(-2.4).contains("2.40ポイント下回"))
        assertTrue(StockEvaluationPositionComment.cagr(0.0).contains("基準上"))
        listOf(Triple(13.3, listOf(10.0, 25.0), "3.30ポイント"),
            Triple(6.65, listOf(5.0, 12.5), "1.65ポイント"),
            Triple(28.0, listOf(20.0, 30.0), "2.00ポイント")).forEach { (value, edges, gap) ->
            assertTrue(StockEvaluationPositionComment.threshold(value, edges, "%", 0.0).contains(gap))
        }
    }

    @Test fun numericCommentsDoNotConsumeHistoryOrChangeContinuityExplanations() {
        val points = listOf(20.0, 30.0, 40.0).mapIndexed { i, v ->
            MetricHistoryPoint(java.time.LocalDate.of(2021 + i, 3, 31), v, "yfinance")
        }
        val values = mapOf("payout_ratio" to 45.93)
        val context = StockAnalysisContext(mapOf("payout_ratio" to points))
        val numeric = StockAnalysisNarrativeGenerator.generatePositionDetailed(values, context).findings.single().text
        assertEquals(StockAnalysisNarrativeGenerator.generatePositionDetailed(values).findings.single().text, numeric)
        val detailed = StockAnalysisNarrativeGenerator.generateDetailed(values, context).findings.single().text
        assertTrue(detailed, detailed.contains("手元資金"))
        assertTrue(detailed, detailed.contains("実績"))
        assertFalse(detailed, detailed.contains("判定範囲"))
    }
}
