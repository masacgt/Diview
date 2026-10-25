package com.kurogane.dividend.model

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AcquisitionPriceInputTest {
    @Test fun pricesRoundTripWithoutGroupingOrRoundingAcrossLocales() {
        val previous = Locale.getDefault()
        try {
            listOf(Locale.JAPAN, Locale.US, Locale.GERMANY).forEach { locale ->
                Locale.setDefault(locale)
                listOf(0.0, 999.9, 2015.0, 1234567.89, 0.00001, 1234.56789).forEach { price ->
                    val input = formatAcquisitionPriceInput(price)
                    assertFalse(input.contains(','))
                    assertFalse(input.contains('E'))
                    assertEquals(price, input.toDouble(), 0.0)
                }
            }
            assertEquals("2015", formatAcquisitionPriceInput(2015.0))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun pastedGroupingIsRemovedAndInvalidPricesStayInvalid() {
        assertEquals("2015", normalizeAcquisitionPriceInput("2,015"))
        assertEquals("1234567.89", normalizeAcquisitionPriceInput("1,234,567.89"))
        assertEquals("2015", normalizeAcquisitionPriceInput("2，015"))
        assertEquals("", normalizeAcquisitionPriceInput(""))
        assertNull(normalizeAcquisitionPriceInput("1.2.3").toDoubleOrNull())
        assertEquals("", formatAcquisitionPriceInput(Double.NaN))
        assertEquals("", formatAcquisitionPriceInput(Double.POSITIVE_INFINITY))
    }
}
