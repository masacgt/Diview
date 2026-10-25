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

    @Test
    fun detailedDividendContinuityCommentsStayWithinTheRequestedLength() {
        val violations = mutableListOf<String>()
        ranges.forEach { (key, metricRanges) ->
            metricRanges.forEachIndexed { bandIndex, (low, high) ->
                val values = baseline.toMutableMap().apply { this[key] = (low + high) / 2.0 }
                val text = StockAnalysisNarrativeGenerator.generateDetailed(values)
                    .findings.single { it.key == key }.text
                val allowed = if (key == "per" || key == "pbr") 130..170 else 100..140
                if (text.length !in allowed) violations += "$key band $bandIndex: ${text.length} characters"
                if (text.count { it == '。' } != 2) violations += "$key band $bandIndex: expected about two sentences"
            }
        }
        listOf("per" to -1.0, "pbr" to -1.0, "payout_ratio" to -10.0).forEach { (key, value) ->
            val values = baseline.toMutableMap().apply { this[key] = value }
            val text = StockAnalysisNarrativeGenerator.generateDetailed(values)
                .findings.single { it.key == key }.text
            val allowed = if (key == "per" || key == "pbr") 130..170 else 100..140
            if (text.length !in allowed) violations += "$key non-positive: ${text.length} characters"
            if (text.count { it == '。' } != 2) violations += "$key non-positive: expected about two sentences"
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun evaluationPositionCommentsStayWithinTwoHundredToTwoHundredFiftyCharacters() {
        val violations = mutableListOf<String>()
        ranges.forEach { (key, metricRanges) ->
            metricRanges.forEachIndexed { bandIndex, (low, high) ->
                (0 until 9).forEach { positionIndex ->
                    val fraction = (positionIndex + 0.5) / 9.0
                    val values = baseline.toMutableMap().apply { this[key] = low + (high - low) * fraction }
                    val text = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
                        .findings.single { it.key == key }.text
                    if (text.length !in 200..250) {
                        violations += "$key band $bandIndex position $positionIndex: ${text.length} characters"
                    }
                    if (text.count { it == '。' } !in 4..6) {
                        violations += "$key band $bandIndex position $positionIndex: expected four to six sentences"
                    }
                }
            }
        }
        listOf("per" to -1.0, "pbr" to -1.0, "payout_ratio" to -1.0).forEach { (key, value) ->
            val values = baseline.toMutableMap().apply { this[key] = value }
            val text = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
                .findings.single { it.key == key }.text
            if (text.length !in 200..250) violations += "$key non-positive: ${text.length} characters"
            if (text.count { it == '。' } !in 4..6) violations += "$key non-positive: expected four to six sentences"
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun semanticPositionStatesKeepLongCommentsAndEvaluationCombination() {
        data class StateSample(val key: String, val value: Double, val expectedPhrase: String)

        val samples = listOf(
            StateSample("operating_margin", -0.01, "営業利益はマイナス"),
            StateSample("operating_margin", -0.005, "営業利益はマイナス"),
            StateSample("operating_margin", -0.0049, "営業利益はゼロ付近"),
            StateSample("operating_margin", 0.0, "営業利益はゼロ付近"),
            StateSample("operating_margin", 0.0049, "営業利益はゼロ付近"),
            StateSample("operating_margin", 0.005, "営業利益はプラス"),
            StateSample("operating_margin", 0.01, "営業利益はプラス"),
            StateSample("fcf_margin", -0.01, "FCFはマイナス"),
            StateSample("fcf_margin", -0.005, "FCFはマイナス"),
            StateSample("fcf_margin", -0.0049, "FCFはゼロ付近"),
            StateSample("fcf_margin", 0.0, "FCFはゼロ付近"),
            StateSample("fcf_margin", 0.0049, "FCFはゼロ付近"),
            StateSample("fcf_margin", 0.005, "FCFはプラス"),
            StateSample("fcf_margin", 0.01, "FCFはプラス"),
            StateSample("per", -1.0, "通常の倍率比較が難しい"),
            StateSample("per", 0.0, "通常の倍率比較が難しい"),
            StateSample("per", 0.1, "「割安」帯の下端寄り"),
            StateSample("pbr", -1.0, "負の純資産などでは通常の比較が難しい"),
            StateSample("pbr", 0.0, "負の純資産などでは通常の比較が難しい"),
            StateSample("pbr", 0.1, "「資産面で割安」帯の下端寄り"),
            StateSample("payout_ratio", -1.0, "赤字や利益の符号"),
            StateSample("payout_ratio", 0.0, "「余力大」帯の下端寄り")
        )

        val violations = mutableListOf<String>()
        samples.forEach { sample ->
            val values = baseline.toMutableMap().apply { this[sample.key] = sample.value }
            val positionResult = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
            val text = positionResult.findings.single { it.key == sample.key }.text
            if (text.length !in 200..250) {
                violations += "${sample.key}=${sample.value}: ${text.length} characters"
            }
            if (text.count { it == '。' } !in 4..6) {
                violations += "${sample.key}=${sample.value}: expected four to six sentences"
            }
            if (!text.contains(sample.expectedPhrase)) {
                violations += "${sample.key}=${sample.value}: missing ${sample.expectedPhrase}"
            }
            if (StockAnalysisNarrativeGenerator.generate(values).combinationIndex != positionResult.combinationIndex) {
                violations += "${sample.key}=${sample.value}: evaluation combination changed by text style"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun customAnalysisCommentsStayWithinTwoHundredToTwoHundredEightyCharacters() {
        val comments = buildList {
            addAll(
                listOf(1.5, 2.65, 3.3, 4.2).map {
                    StockDetailCommentGenerator.dividendYield(it, 2.02, 2.99, 3.81)
                }
            )
            addAll(listOf(0, 2, 4, 8, 12).map(StockDetailCommentGenerator::dividendIncreaseYears))
            addAll(listOf(0, 2, 4, 8, 12).map(StockDetailCommentGenerator::nonCutYears))
            addAll(
                listOf(-5.0, 0.0, 5.0).flatMap { rate ->
                    listOf(false, true).map { isCalendarYear ->
                        StockDetailCommentGenerator.annualDividendCagr(rate, isCalendarYear)
                    }
                }
            )
            addAll(
                listOf(
                    Triple(5.0, "適正範囲", "中央"),
                    Triple(13.3, "やや高め", "下限寄り"),
                    Triple(30.0, "高リスク・要分散", "上限寄り")
                ).map { (value, band, position) ->
                    StockDetailCommentGenerator.dividendDependency(value, band, position)
                }
            )
            addAll(
                listOf(
                    Triple(2.5, "依存度10%以内の半分相当", "中央"),
                    Triple(8.0, "依存度10%超25%以内の半分相当", "中央"),
                    Triple(15.0, "依存度25%超の半分相当", "基準超過")
                ).map { (value, band, position) ->
                    StockDetailCommentGenerator.halfDividendImpact(value, band, position)
                }
            )
            addAll(
                listOf(
                    Triple(10.0, "適正分散", "中央"),
                    Triple(25.0, "やや集中", "中央"),
                    Triple(40.0, "高集中", "基準超過")
                ).map { (value, band, position) ->
                    StockDetailCommentGenerator.sectorConcentration("卸売業", value, band, position)
                }
            )
        }

        val violations = comments.mapIndexedNotNull { index, text ->
            if (text.length !in 200..250 || text.count { it == '。' } !in 3..5) {
                "comment $index: ${text.length} characters, ${text.count { it == '。' }} sentences"
            } else null
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }
}
