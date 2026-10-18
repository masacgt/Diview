package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareIncomeAdviceCandidate

data class RankedIncomeAdviceCandidate(
    val candidate: CloudflareIncomeAdviceCandidate,
    val coveredMonths: List<Int>,
    val estimatedAmountPerCoveredMonth: Double
)

fun rankIncomeAdviceCandidates(
    candidates: List<CloudflareIncomeAdviceCandidate>,
    emptyMonths: List<Int>,
    watchlistCodes: Set<String>
): List<RankedIncomeAdviceCandidate> {
    val gaps = emptyMonths.filter { it in 1..12 }.toSet()
    if (gaps.isEmpty()) return emptyList()
    val remaining = candidates.asSequence()
        .filterNot { it.code in watchlistCodes }
        .mapNotNull { candidate ->
            val coveredMonths = candidate.observedMonths.filter { it in gaps }.distinct().sorted()
            if (coveredMonths.isEmpty()) null
            else RankedIncomeAdviceCandidate(
                candidate = candidate,
                coveredMonths = coveredMonths,
                estimatedAmountPerCoveredMonth = candidate.monthlyEstimatePer100Shares
            )
        }
        .toMutableList()
    val uncoveredMonths = gaps.toMutableSet()
    val selected = mutableListOf<RankedIncomeAdviceCandidate>()
    while (selected.size < 3 && remaining.isNotEmpty()) {
        val next = remaining.minWithOrNull(
            compareByDescending<RankedIncomeAdviceCandidate> { candidate ->
                candidate.coveredMonths.count { it in uncoveredMonths }
            }
                .thenByDescending { it.candidate.score }
                .thenByDescending { it.coveredMonths.size }
                .thenByDescending { it.candidate.annualDividendPerShare }
                .thenBy { it.candidate.code }
        ) ?: break
        selected += next
        remaining.remove(next)
        uncoveredMonths.removeAll(next.coveredMonths.toSet())
    }
    return selected
}
