package com.kurogane.dividend.model

/** Expected withholding through a Japanese broker; excludes filing, credits and special distributions. */
object DividendTax {
    const val JAPAN_RATE = 0.20315
    const val US_RATE = 0.10

    fun net(gross: Double, freeRatio: Double, usWithholding: Boolean): Double {
        if (!gross.isFinite() || gross <= 0.0) return 0.0
        val ratio = freeRatio.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        val afterForeign = gross * if (usWithholding) 1.0 - US_RATE else 1.0
        return afterForeign * (ratio + (1.0 - ratio) * (1.0 - JAPAN_RATE))
    }
}
