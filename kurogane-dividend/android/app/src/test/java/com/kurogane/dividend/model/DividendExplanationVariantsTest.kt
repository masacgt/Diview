package com.kurogane.dividend.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DividendExplanationVariantsTest {
    private fun history(key: String, vararg values: Double, basis: String = MetricHistoryPoint.ANNUAL_ACTUAL) =
        StockAnalysisContext(mapOf(key to values.mapIndexed { i, value ->
            MetricHistoryPoint(LocalDate.of(2021 + i, 3, 31), value, "test", basis)
        }))

    private fun text(key: String, value: Double, context: StockAnalysisContext = StockAnalysisContext(),
                     others: Map<String, Double?> = emptyMap()) = StockAnalysisNarrativeGenerator.generateDetailed(
        others + (key to value), context).findings.single { it.key == key }.text

    @Test fun extremeLevelsHaveDistinctExplanationsWithoutChangingScores() {
        val cases = mapOf(
            "payout_ratio" to listOf(95.0, 110.0, 150.0, 250.0),
            "operating_margin" to listOf(-0.5, -5.0, -15.0, -25.0, 40.0, 60.0),
            "fcf_margin" to listOf(-1.0, -8.0, -20.0, -40.0, 40.0, 60.0),
            "per" to listOf(-1.0, 0.0, 2.0, 4.0, 80.0, 150.0, 250.0),
            "pbr" to listOf(-1.0, 0.0, 0.1, 0.2, 8.0, 12.0, 20.0),
            "dividend_growth_years" to listOf(0.0, 1.0, 2.0, 12.0, 22.0, 32.0)
        )
        cases.forEach { (key, values) ->
            assertEquals(key, values.size, values.map { text(key, it) }.toSet().size)
            values.forEach { value ->
                val detailed = StockAnalysisNarrativeGenerator.generateDetailed(mapOf(key to value))
                val numeric = StockAnalysisNarrativeGenerator.generatePositionDetailed(mapOf(key to value))
                assertEquals(detailed.score, numeric.score)
                assertEquals(detailed.combinationIndex, numeric.combinationIndex)
                assertTrue(text(key, value), text(key, value).endsWith("。"))
            }
        }
        assertTrue(text("operating_margin", -0.5).contains("小幅"))
        assertTrue(text("operating_margin", -15.0).contains("深い"))
        assertTrue(text("fcf_margin", 40.0).contains("大幅"))
    }

    @Test fun alignedCapitalAndCashDisagreementIsDatedAndDoesNotImplySafety() {
        val context = history("equity_ratio", 60.0).let { capital ->
            capital.copy(history = capital.history + history("fcf_margin", -3.0).history)
        }
        val result = text("equity_ratio", 60.0, context)
        assertTrue(result, result.contains("2021/03"))
        assertTrue(result, result.contains("FCFは赤字"))
        val mismatched = context.copy(history = context.history.mapValues { (key, points) ->
            if (key == "fcf_margin") points.map { it.copy(periodEnd = it.periodEnd.minusYears(1)) } else points
        })
        assertFalse(text("equity_ratio", 60.0, mismatched).contains("FCFは赤字"))
        val yearOnly = context.copy(history = context.history.mapValues { (_, points) ->
            points.map { it.copy(basis = MetricHistoryPoint.ISSUER_YEAR, periodPrecision = "year") }
        })
        assertFalse(text("equity_ratio", 60.0, yearOnly).contains("FCFは赤字"))
    }

    @Test fun currentSnapshotDisagreementsExplainTheirDifferentMeaningsAndTiming() {
        val per = text("per", 4.0, others = mapOf("operating_margin" to -1.0))
        assertTrue(per, per.contains("営業赤字"))
        assertTrue(per, per.contains("時点"))
        val growth = text("dividend_growth_years", 20.0, others = mapOf("payout_ratio" to 150.0))
        assertTrue(growth, growth.contains("配当負担") || growth.contains("配当性向"))
        assertTrue(growth, growth.contains("時点"))
        assertFalse(text("per", 4.0, others = mapOf("operating_margin" to Double.NaN)).contains("営業赤字"))
        assertFalse(text("dividend_growth_years", 20.0).contains("高い配当負担"))
    }

    @Test fun boundarySupplementUsesNinePositionsButNotArtificialScaleEnds() {
        assertTrue(text("per", 19.99), text("per", 19.99).contains("境界付近"))
        assertTrue(text("per", 20.01), text("per", 20.01).contains("境界付近"))
        assertFalse(text("per", 17.5).contains("境界付近"))
        assertFalse(text("per", 500.0).contains("境界付近"))
        assertFalse(text("equity_ratio", -50.0).contains("境界付近"))
    }

    @Test fun persistentDeficitsImproveWorsenOrStayUnrecovered() {
        val improving = text("fcf_margin", -1.0, history("fcf_margin", -20.0, -8.0, -1.0))
        val worsening = text("fcf_margin", -20.0, history("fcf_margin", -1.0, -8.0, -20.0))
        val flat = text("fcf_margin", -3.0, history("fcf_margin", -3.0, -3.0, -3.0))
        assertTrue(improving, improving.contains("縮小"))
        assertTrue(worsening, worsening.contains("深ま"))
        assertTrue(flat, flat.contains("回復はまだ"))
        val unstable = text("operating_margin", 8.0, history("operating_margin", 1.0, 15.0, 3.0, 8.0))
        assertTrue(unstable, unstable.contains("上下の変動"))
        assertFalse(unstable, unstable.contains("単年の改善だけ"))
    }

    @Test fun dividendHistorySeparatesPausesCutsRecoveryAndRestartWithoutInventingEvents() {
        val cases = listOf(
            listOf(10.0, 12.0, 12.0) to "据え置",
            listOf(10.0, 10.0, 10.0) to "据え置",
            listOf(10.0, 8.0, 9.0) to "減配後",
            listOf(10.0, 12.0, 9.0) to "減ら",
            listOf(0.0, 0.0, 5.0) to "再開"
        )
        cases.forEach { (values, word) ->
            val context = history("issuer_dps", *values.toDoubleArray(), basis = MetricHistoryPoint.ISSUER_YEAR)
            val result = text("dividend_growth_years", 1.0, context)
            assertTrue(result, result.contains(word))
            assertTrue(result, result.contains("年度実績"))
        }
        val calendar = text("dividend_growth_years", 1.0,
            history("dividend_dps", 10.0, 8.0, 9.0, basis = MetricHistoryPoint.CALENDAR_ACTUAL))
        assertTrue(calendar, calendar.contains("暦年実績"))
        val gap = history("issuer_dps", 10.0, 8.0, 9.0).let { original ->
            original.copy(history = original.history.mapValues { (_, points) -> listOf(points.first(), points.last()) })
        }
        assertFalse(text("dividend_growth_years", 1.0, gap).contains("減配後に再び"))
    }

    @Test fun expandedCopyFitsTheFormerInputBudgetWithoutCuttingSentences() {
        val core = "還元と事業投資を両立しやすい配当負担の範囲です。"
        val fullLimit = "利益に対する配当負担だけでは、手元資金や投資・借入返済後の支払余力は分かりません。特別損益を除く利益の安定性、営業キャッシュフロー、FCFと配当方針も確認します。"
        val previous = core + fullLimit
        val actual = text("payout_ratio", 50.0)
        assertTrue("${actual.length} > ${previous.length}: $actual", actual.length <= previous.length)
        assertTrue(actual, actual.length >= previous.length * 0.8)
        assertTrue(actual, actual.contains("境界付近"))
        val rich = history("payout_ratio", 40.0, 80.0, 150.0)
        for (budget in listOf(110, 160, 230)) {
            val output = DividendExplanationVariants.compose("payout_ratio", 150.0, core, rich,
                mapOf("dividend_growth_years" to 20.0, "payout_ratio" to 150.0), true, null, null,
                "現金と一時損益も見ます。" to fullLimit, budget)
            assertTrue(output, output.length <= budget)
            assertTrue(output, output.length >= budget * 0.75)
            assertTrue(output.endsWith("。"))
            assertTrue(output, output.contains("現金") || output.contains("手元資金"))
        }
    }

    @Test fun missingHistoryKeepsCurrentFactsWithoutMissingDataMessagesOrChangingNumericPanels() {
        val values = mapOf("per" to 4.0, "operating_margin" to -0.5, "dividend_growth_years" to 20.0, "payout_ratio" to 150.0)
        val result = StockAnalysisNarrativeGenerator.generateDetailed(values)
        result.findings.forEach { finding ->
            assertFalse(finding.text, finding.text.contains("複数年分のデータがない"))
            assertFalse(finding.text, finding.text.contains(finding.formattedValue))
        }
        val context = history("operating_margin", -10.0, -5.0, -0.5)
        assertEquals(StockAnalysisNarrativeGenerator.generatePositionDetailed(values),
            StockAnalysisNarrativeGenerator.generatePositionDetailed(values, context))
    }
}
