package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareIncomeAdviceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeAdviceRankingTest {
    @Test fun returnsNoRecommendationsWhenThereAreNoValidEmptyMonths() {
        val result = rankIncomeAdviceCandidates(
            candidates = listOf(candidate("7203", listOf(1, 2), score = 80)),
            emptyMonths = listOf(0, 13, -1),
            watchlistCodes = emptySet()
        )

        assertTrue(result.isEmpty())
    }

    @Test fun excludesWatchlistAndKeepsDistinctValidMonthsThatCoverTheCurrentGaps() {
        val result = rankIncomeAdviceCandidates(
            candidates = listOf(
                candidate("7203", listOf(2, 4), score = 100),
                candidate("8306", listOf(4, 2, 2, 13, 0), score = 80, monthlyEstimate = 1200.0)
            ),
            emptyMonths = listOf(4, 2, 4, 13, 0),
            watchlistCodes = setOf("7203")
        )

        assertEquals(listOf("8306"), result.map { it.candidate.code })
        assertEquals(listOf(2, 4), result.single().coveredMonths)
        assertEquals(1200.0, result.single().estimatedAmountPerCoveredMonth, 0.0)
    }

    @Test fun greedilyCoversTheMostUncoveredMonthsThenUsesScoreAndCapsAtThree() {
        val result = rankIncomeAdviceCandidates(
            candidates = listOf(
                candidate("1001", listOf(1, 2, 3), score = 80),
                candidate("1002", listOf(4), score = 90),
                candidate("1003", listOf(1, 4), score = 95),
                candidate("1004", listOf(4), score = 92),
                candidate("1005", listOf(3), score = 99)
            ),
            emptyMonths = listOf(1, 2, 3, 4),
            watchlistCodes = emptySet()
        )

        assertEquals(3, result.size)
        assertEquals(listOf("1001", "1003", "1005"), result.map { it.candidate.code })
        assertEquals(listOf(1, 2, 3), result[0].coveredMonths)
        assertEquals(listOf(1, 4), result[1].coveredMonths)
    }

    private fun candidate(
        code: String,
        observedMonths: List<Int>,
        score: Int,
        monthlyEstimate: Double = 1000.0
    ) = CloudflareIncomeAdviceCandidate(
        code = code,
        name = "Stock $code",
        currency = "JPY",
        annualDividendPerShare = 100.0,
        annualDividendBasis = "reference",
        observedMonths = observedMonths,
        monthlyEstimatePer100Shares = monthlyEstimate,
        score = score
    )
}
