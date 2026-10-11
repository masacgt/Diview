package com.kurogane.dividend.ui.components

import java.text.Normalizer
import java.util.Locale

internal fun normalizeStockSearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
