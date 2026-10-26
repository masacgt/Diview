package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test

class StockEvaluationPositionCommentTest {
    private val cutoffs = mapOf(
        "per" to listOf(10.0, 15.0, 20.0, 30.0), "pbr" to listOf(0.8, 1.2, 2.0, 3.5),
        "payout_ratio" to listOf(30.0, 50.0, 70.0, 90.0), "equity_ratio" to listOf(15.0, 25.0, 40.0, 60.0),
        "operating_margin" to listOf(2.0, 5.0, 10.0, 15.0), "fcf_margin" to listOf(-5.0, 0.0, 5.0, 10.0),
        "dividend_growth_years" to listOf(1.0, 3.0, 5.0, 10.0), "non_cut_years" to listOf(1.0, 3.0, 5.0, 10.0),
        "dividend_dependency" to listOf(10.0, 25.0), "half_dividend_impact" to listOf(5.0, 12.5),
        "sector_concentration" to listOf(20.0, 30.0))
    private fun unit(key: String) = when (key) { "per", "pbr" -> "倍"; "dividend_growth_years", "non_cut_years" -> "年"; else -> "%" }
    private fun text(key: String, value: Double) = StockEvaluationPositionComment.threshold(value, cutoffs.getValue(key), unit(key),
        if (key in setOf("non_cut_years", "dividend_dependency", "half_dividend_impact", "sector_concentration")) 0.0 else null, key)
    private fun valid(text: String) {
        assertTrue("length=${text.length}: $text", text.length in 80..160)
        assertFalse(text, text.contains("判定範囲"))
        assertFalse(text, text.contains("Infinity") || text.contains("NaN"))
        assertTrue(text.endsWith("。"))
    }
    @Test fun allIndicatorsAndPositionsFitTheLengthWithoutRepeatingRanges() {
        cutoffs.forEach { (key, edges) ->
            val intervals = (listOf(0.0) + edges + (edges.last() + 30.0)).zipWithNext()
            val values = intervals.flatMap { (lo, hi) -> (0 until 9).map { lo + (hi - lo) * (it + 0.5) / 9 } } +
                edges.flatMap { listOf(it - 0.001, it, it + 0.001) } + listOf(0.0, 1000.0)
            values.filter { unit(key) != "年" || it >= 0 }.forEach { valid(text(key, it)) }
        }
        for (key in listOf("per", "pbr", "equity_ratio", "operating_margin", "fcf_margin"))
            for (value in listOf(-Double.MAX_VALUE, -1000.0, Double.MAX_VALUE)) valid(text(key, value))
        for (value in listOf(1.0, 2.0, 2.65, 3.0, 3.5, 4.0, 5.0)) valid(StockEvaluationPositionComment.yield(value, 2.0, 3.0, 4.0))
        for (value in listOf(-2.4, 0.0, 2.4)) valid(StockEvaluationPositionComment.cagr(value))
        valid(StockEvaluationPositionComment.yield(3.0, 3.0, 3.0, 3.0))
        valid(StockEvaluationPositionComment.yield(2.0, Double.NaN, 3.0, 4.0))
        valid(StockEvaluationPositionComment.unavailable("比較期間が不足しているため、位置は算出できません。"))
    }
    @Test fun scoresBandsAndCombinationCodesRemainUnchanged() {
        cutoffs.filterKeys { it !in setOf("non_cut_years", "dividend_dependency", "half_dividend_impact", "sector_concentration") }
            .forEach { (key, edges) -> edges.flatMap { listOf(it - 0.01, it, it + 0.01) }.forEach { value ->
                val numeric = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf(key to value)).findings.single()
                val detailed = StockAnalysisNarrativeGenerator.generateDetailed(mapOf(key to value)).findings.single()
                assertEquals(detailed.bandLabel, numeric.bandLabel); assertEquals(detailed.score, numeric.score)
                assertEquals(detailed.combinationDigit, numeric.combinationDigit); valid(numeric.text)
            } }
    }
    @Test fun eachIndicatorExplainsItsOwnMeaningAndCorrectDistanceUnits() {
        val payout = text("payout_ratio", 45.93)
        assertTrue(payout, payout.contains("配当負担") && payout.contains("上端寄り") && payout.contains("4.07ポイント"))
        assertFalse(payout, payout.contains("45.93") || payout.contains("30.00%") || payout.contains("50.00%"))
        assertTrue(text("per", 19.67).contains("株価評価") && text("per", 19.67).contains("0.33倍"))
        assertTrue(text("equity_ratio", 35.0).contains("自己資本の厚み"))
        assertTrue(text("operating_margin", -0.5).contains("採算は赤字"))
        assertTrue(text("fcf_margin", 6.0).contains("現金収支"))
        assertTrue(text("dividend_growth_years", 9.0).contains("増配を続けた実績"))
        assertTrue(text("dividend_dependency", 13.3).contains("一社への配当収入"))
        assertTrue(text("half_dividend_impact", 6.65).contains("半減"))
        assertTrue(text("sector_concentration", 28.0).contains("業種への資産"))
    }
    @Test fun ninePositionsCentralPointAndEqualityHaveDistinctReadings() {
        val phrases = listOf("下端に非常に近く", "下端寄り", "帯の下側", "中央よりやや下側", "中央", "中央よりやや上側", "帯の上側", "上端寄り", "上端に非常に近く")
        (0 until 9).forEach { i ->
            val result = StockEvaluationPositionComment.range(10.0 + i + 0.5, 10.0, 19.0, "%", key = "payout_ratio")
            assertTrue(result, result.contains(phrases[i]))
        }
        assertTrue(StockEvaluationPositionComment.range(40.0, 30.0, 50.0, "%").contains("それぞれ10.00ポイント"))
        val exact = text("payout_ratio", 50.0)
        assertTrue(exact, exact.contains("上側境界と一致") && exact.contains("0.00ポイント"))
        assertFalse(exact.contains("上回"))
    }
    @Test fun openEndedBandsShowCrossingsAndExtremeDistancesWithoutInventedEnds() {
        assertTrue(text("payout_ratio", 90.01).contains("越えた直後"))
        val far = text("payout_ratio", 280.18)
        assertTrue(far, far.contains("上限のない帯") && far.contains("190.18ポイント上回") && far.contains("大きく離れ"))
        assertFalse(far, far.contains("150") || far.contains("上端") || far.contains("上限寄り"))
        assertTrue(text("fcf_margin", -35.0).contains("30.00ポイント下回"))
        assertTrue(text("payout_ratio", 50.00001).contains("0.01ポイント未満"))
        assertFalse(text("payout_ratio", 50.00001).contains("0.00ポイント"))
        assertTrue(text("per", -1.0).contains("通常の正の倍率"))
    }
    @Test fun yieldAndCagrKeepTheirActualComparisonBasis() {
        assertTrue(StockEvaluationPositionComment.yield(1.5, 2.0, 3.0, 4.0).contains("過去最低との差は0.50ポイント"))
        assertTrue(StockEvaluationPositionComment.yield(2.65, 2.0, 3.0, 4.0).contains("平均との差は0.35ポイント"))
        assertTrue(StockEvaluationPositionComment.yield(3.0, 2.0, 3.0, 4.0).contains("基準上"))
        assertTrue(StockEvaluationPositionComment.yield(5.0, 2.0, 3.0, 4.0).contains("過去最高との差は1.00ポイント"))
        assertTrue(StockEvaluationPositionComment.cagr(2.4).contains("2.40ポイント上回"))
        assertTrue(StockEvaluationPositionComment.cagr(-2.4).contains("2.40ポイント下回"))
        assertTrue(StockEvaluationPositionComment.cagr(0.0).contains("基準上"))
        assertTrue(StockEvaluationPositionComment.cagr(2.4).contains("毎年同じ割合"))
    }
    @Test fun historyDoesNotAlterPositionsAndDividendExplanationStaysIndependent() {
        val points = listOf(20.0, 30.0, 40.0).mapIndexed { i, value -> MetricHistoryPoint(java.time.LocalDate.of(2021 + i, 3, 31), value, "test") }
        val values = mapOf("payout_ratio" to 45.93)
        val context = StockAnalysisContext(mapOf("payout_ratio" to points))
        assertEquals(StockAnalysisNarrativeGenerator.generatePositionDetailed(values), StockAnalysisNarrativeGenerator.generatePositionDetailed(values, context))
        val detailed = StockAnalysisNarrativeGenerator.generateDetailed(values, context).findings.single().text
        assertTrue(detailed, detailed.contains("手元資金")); assertFalse(detailed.contains("帯の上端寄り"))
    }
    @Test fun negativePayoutAndNonPositiveMultiplesDoNotImplyFavorableValuation() {
        val negative = text("payout_ratio", -20.0)
        valid(negative)
        assertTrue(negative, negative.contains("負の比率"))
        assertFalse(negative, negative.contains("小さい側"))
        for (key in listOf("per", "pbr")) for (value in listOf(-1.0, 0.0)) {
            val result = text(key, value)
            valid(result)
            assertTrue(result, result.contains("通常の正の倍率"))
        }
        valid(StockEvaluationPositionComment.cagr(Double.MAX_VALUE))
        valid(StockEvaluationPositionComment.yield(Double.MAX_VALUE, 2.0, 3.0, 4.0))
    }
    @Test fun zeroDomainMinimumDoesNotInventAnotherBandBelowZero() {
        for (key in listOf("non_cut_years", "dividend_dependency", "half_dividend_impact", "sector_concentration")) {
            for (value in listOf(0.0, 0.01)) {
                val result = text(key, value)
                valid(result)
                assertFalse(result, result.contains("隣") || result.contains("切り替") || result.contains("切替"))
            }
        }
    }
}
