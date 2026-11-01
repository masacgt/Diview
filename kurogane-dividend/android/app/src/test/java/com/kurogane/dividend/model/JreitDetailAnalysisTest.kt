package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test

class JreitDetailAnalysisTest {
    @Test fun productCategoryDistinguishesReitFromEtfAndUnknown() {
        assertTrue(isJreitProduct(Category.JAPAN, "013", "reit"))
        assertTrue(isJreitProduct(Category.JAPAN, null, "reit"))
        assertFalse(isJreitProduct(Category.JAPAN, "013", "infrastructure"))
        assertFalse(isJreitProduct(Category.JAPAN, "013", null))
        assertFalse(isJreitProduct(Category.JAPAN, "014", "reit"))
        assertFalse(isJreitProduct(Category.US, "013", "reit"))
    }

    @Test fun highPayoutIsReferenceRatherThanAStockPayoutGrade() {
        val payout = JreitDetailAnalysis.explanations(mapOf("payout_ratio" to 100.0))
            .single { it.key == "payout_ratio" }
        assertEquals("100.00%", payout.value)
        assertTrue(payout.text.contains("100%前後"))
        assertFalse(payout.text.contains("高負担"))
        assertFalse(payout.text.contains("要注意"))
    }

    @Test fun missingAndNegativeValuesAreNotShownAsZero() {
        val missing = JreitDetailAnalysis.explanations(mapOf("payout_ratio" to Double.NaN, "dividend_growth_years" to -1.0))
        assertEquals("未取得", missing.single { it.key == "payout_ratio" }.value)
        assertEquals("未取得", missing.single { it.key == "dividend_growth_years" }.value)
        val negative = JreitDetailAnalysis.explanations(mapOf("payout_ratio" to -30.0)).single { it.key == "payout_ratio" }
        assertEquals("-30.00%", negative.value)
        assertTrue(negative.text.contains("負の値"))
    }
}
