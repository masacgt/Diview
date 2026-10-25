package com.kurogane.dividend.model

import java.math.BigDecimal

/** Editable prices must round-trip without grouping, locale separators, or display rounding. */
fun formatAcquisitionPriceInput(value: Double): String =
    if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else ""

/** Remove grouping separators from typed or pasted acquisition prices. */
fun normalizeAcquisitionPriceInput(value: String): String =
    value.replace(",", "").replace("，", "")
