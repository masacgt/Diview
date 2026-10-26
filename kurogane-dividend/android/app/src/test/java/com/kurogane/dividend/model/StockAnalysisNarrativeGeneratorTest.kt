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
    fun evaluationPositionCommentsStayWithinOneHundredToOneHundredFortyCharacters() {
        val violations = mutableListOf<String>()
        ranges.forEach { (key, metricRanges) ->
            metricRanges.forEachIndexed { bandIndex, (low, high) ->
                (0 until 9).forEach { positionIndex ->
                    val fraction = (positionIndex + 0.5) / 9.0
                    val values = baseline.toMutableMap().apply { this[key] = low + (high - low) * fraction }
                    val text = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
                        .findings.single { it.key == key }.text
                    if (text.length !in 100..140) {
                        violations += "$key band $bandIndex position $positionIndex: ${text.length} characters"
                    }
                    if (text.count { it == '。' } !in 2..4) {
                        violations += "$key band $bandIndex position $positionIndex: expected two to four sentences"
                    }
                }
            }
        }
        listOf("per" to -1.0, "pbr" to -1.0, "payout_ratio" to -1.0).forEach { (key, value) ->
            val values = baseline.toMutableMap().apply { this[key] = value }
            val text = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
                .findings.single { it.key == key }.text
            if (text.length !in 100..140) violations += "$key non-positive: ${text.length} characters"
            if (text.count { it == '。' } !in 2..4) violations += "$key non-positive: expected two to four sentences"
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun everyMetricUsesFortyFiveDistinctShortCommentPatterns() {
        val violations = mutableListOf<String>()
        ranges.forEach { (key, metricRanges) ->
            val patterns = metricRanges.flatMap { (low, high) ->
                (0 until 9).map { positionIndex ->
                    val fraction = (positionIndex + 0.5) / 9.0
                    val values = baseline.toMutableMap().apply { this[key] = low + (high - low) * fraction }
                    val finding = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
                        .findings.single { it.key == key }
                    finding.text.replace(finding.formattedValue, "<value>")
                }
            }.toSet()
            if (patterns.size != 45) violations += "$key: ${patterns.size} distinct patterns"
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun semanticPositionStatesKeepShortCommentsAndEvaluationCombination() {
        data class StateSample(val key: String, val value: Double, val expectedPhrase: String)

        val samples = listOf(
            StateSample("operating_margin", -0.01, "本業赤字"),
            StateSample("operating_margin", -0.005, "本業赤字"),
            StateSample("operating_margin", -0.0049, "営業利益はゼロ付近"),
            StateSample("operating_margin", 0.0, "営業利益はゼロ付近"),
            StateSample("operating_margin", 0.0049, "営業利益はゼロ付近"),
            StateSample("operating_margin", 0.005, "営業利益はプラス"),
            StateSample("operating_margin", 0.01, "営業利益はプラス"),
            StateSample("fcf_margin", -0.01, "投資後の不足分"),
            StateSample("fcf_margin", -0.005, "投資後の不足分"),
            StateSample("fcf_margin", -0.0049, "FCFはゼロ付近"),
            StateSample("fcf_margin", 0.0, "FCFはゼロ付近"),
            StateSample("fcf_margin", 0.0049, "FCFはゼロ付近"),
            StateSample("fcf_margin", 0.005, "FCFはプラス"),
            StateSample("fcf_margin", 0.01, "FCFはプラス"),
            StateSample("per", -1.0, "通常の評価帯比較には注意"),
            StateSample("per", 0.0, "通常の評価帯比較には注意"),
            StateSample("per", 0.1, "「割安」帯の下端に近い位置"),
            StateSample("pbr", -1.0, "負の純資産などで通常の比較が難しく"),
            StateSample("pbr", 0.0, "負の純資産などで通常の比較が難しく"),
            StateSample("pbr", 0.1, "「資産面で割安」帯"),
            StateSample("payout_ratio", -1.0, "赤字時は比率の符号に注意が必要"),
            StateSample("payout_ratio", 0.0, "「余力大」帯の下端に近い位置")
        )

        val violations = mutableListOf<String>()
        samples.forEach { sample ->
            val values = baseline.toMutableMap().apply { this[sample.key] = sample.value }
            val positionResult = StockAnalysisNarrativeGenerator.generatePositionDetailed(values)
            val text = positionResult.findings.single { it.key == sample.key }.text
            if (text.length !in 100..140) {
                violations += "${sample.key}=${sample.value}: ${text.length} characters"
            }
            if (text.count { it == '。' } !in 2..4) {
                violations += "${sample.key}=${sample.value}: expected two to four sentences"
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
    fun customAnalysisCommentsStayWithinOneHundredToOneHundredFortyCharacters() {
        val comments = buildList {
            addAll(
                listOf(1.5, 2.65, 3.3, 4.2).map {
                    StockDetailCommentGenerator.dividendYield(it, 2.02, 2.99, 3.81)
                }
            )
            val yearsBands = listOf("1年以内", "1年超3年以内", "3年超5年以内", "5年超10年以内", "10年超")
            val yearsPositions = listOf("下端に近い位置", "中央付近", "中央付近", "中央付近", "下端寄り")
            val years = listOf(0, 2, 4, 8, 12)
            addAll(years.mapIndexed { index, value ->
                StockDetailCommentGenerator.dividendIncreaseYears(value, yearsBands[index], yearsPositions[index])
            })
            addAll(years.mapIndexed { index, value ->
                StockDetailCommentGenerator.nonCutYears(value, yearsBands[index], yearsPositions[index])
            })
            addAll(
                listOf(-5.0, 0.0, 5.0).flatMap { rate ->
                    listOf(false, true).map { isCalendarYear ->
                        val band = when {
                            rate < 0.0 -> "0%未満・減少傾向"
                            rate > 0.0 -> "0%超・増加傾向"
                            else -> "0%・横ばい"
                        }
                        val position = when {
                            rate < 0.0 -> "基準を下回る"
                            rate > 0.0 -> "基準を上回る"
                            else -> "基準値"
                        }
                        StockDetailCommentGenerator.annualDividendCagr(rate, isCalendarYear, band, position)
                    }
                }
            )
            addAll(
                listOf(
                    Triple(5.0, "適正範囲", "中央付近"),
                    Triple(13.3, "やや高め", "下端寄り"),
                    Triple(30.0, "高リスク・要分散", "基準超過")
                ).map { (value, band, position) ->
                    StockDetailCommentGenerator.dividendDependency(value, band, position)
                }
            )
            addAll(
                listOf(
                    Triple(2.5, "依存度10%以内の半分相当", "中央付近"),
                    Triple(8.0, "依存度10%超25%以内の半分相当", "中央より下"),
                    Triple(15.0, "依存度25%超の半分相当", "基準超過")
                ).map { (value, band, position) ->
                    StockDetailCommentGenerator.halfDividendImpact(value, band, position)
                }
            )
            addAll(
                listOf(
                    Triple(10.0, "適正分散", "中央付近"),
                    Triple(25.0, "やや集中", "中央付近"),
                    Triple(40.0, "高集中", "基準超過")
                ).map { (value, band, position) ->
                    StockDetailCommentGenerator.sectorConcentration("卸売業", value, band, position)
                }
            )
        }

        val violations = comments.mapIndexedNotNull { index, text ->
            if (text.length !in 100..140 || text.count { it == '。' } !in 2..4) {
                "comment $index: ${text.length} characters, ${text.count { it == '。' }} sentences"
            } else null
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun customAnalysisCommentsUseMorePositionSpecificPatterns() {
        val positions = listOf(
            "下端に近い位置", "下端寄り", "下側寄り", "中央より下", "中央付近",
            "中央より上", "上側寄り", "上端寄り", "上端に近い位置"
        )
        val yearsBands = listOf("1年以内", "1年超3年以内", "3年超5年以内", "5年超10年以内", "10年超")
        val years = listOf(0, 2, 4, 8, 12)

        val increasePatterns = years.indices.flatMap { bandIndex ->
            positions.map { position ->
                StockDetailCommentGenerator.dividendIncreaseYears(
                    years[bandIndex], yearsBands[bandIndex], position
                ).replace(Regex("\\d+"), "<n>")
            }
        }.toSet()
        val nonCutPatterns = years.indices.flatMap { bandIndex ->
            positions.map { position ->
                StockDetailCommentGenerator.nonCutYears(
                    years[bandIndex], yearsBands[bandIndex], position
                ).replace("${years[bandIndex]}年", "<year>年")
            }
        }.toSet()

        val riskSamples = listOf(
            Triple(StockDetailCommentGenerator::dividendDependency, listOf(5.0, 15.0, 30.0), listOf("適正範囲", "やや高め", "高リスク・要分散")),
            Triple(StockDetailCommentGenerator::halfDividendImpact, listOf(2.5, 8.0, 15.0), listOf("低依存", "中依存", "高依存"))
        )
        val riskPatterns = riskSamples.flatMap { (generator, values, bands) ->
            values.indices.flatMap { bandIndex ->
                positions.map { position ->
                    generator(values[bandIndex], bands[bandIndex], position)
                        .replace(Regex("\\d+(?:\\.\\d+)?%"), "<percent>")
                }
            }
        }.toSet()

        val yieldPatterns = buildSet {
            listOf(2.02 to 2.99, 2.99 to 3.81).forEach { (lower, upper) ->
                positions.indices.forEach { index ->
                    val fraction = (index + 0.5) / positions.size
                    val value = lower + (upper - lower) * fraction
                    add(
                        StockDetailCommentGenerator.dividendYield(value, 2.02, 2.99, 3.81)
                            .replace(Regex("\\d+(?:\\.\\d+)?%"), "<percent>")
                    )
                }
            }
        }

        assertEquals(45, increasePatterns.size)
        assertEquals(45, nonCutPatterns.size)
        assertEquals(54, riskPatterns.size)
        assertEquals(18, yieldPatterns.size)
    }
}
