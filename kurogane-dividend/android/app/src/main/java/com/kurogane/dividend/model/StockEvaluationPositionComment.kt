package com.kurogane.dividend.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Numeric band evidence only. Open-ended bands have no invented terminal boundary. */
object StockEvaluationPositionComment {
    fun range(value: Double, lower: Double?, upper: Double?, unit: String,
              lowerInclusive: Boolean = false, upperInclusive: Boolean = true): String {
        require(value.isFinite() && (lower == null || lower.isFinite()) && (upper == null || upper.isFinite()))
        require(lower == null || upper == null || lower <= upper)
        fun number(n: Double): String = when {
            unit == "年" -> String.format(Locale.JAPAN, "%.0f", n)
            abs(n) >= 1e12 -> String.format(Locale.JAPAN, "%.2e", n)
            else -> String.format(Locale.JAPAN, "%.2f", if (n == 0.0) 0.0 else n)
        }
        fun bound(n: Double) = number(n) + unit
        fun difference(n: Double) = number(n) + if (unit == "%") "ポイント" else unit
        if (lower != null && lower == upper) {
            return "判定基準は${bound(lower)}との一致です。基準上にあり、境界との差は${difference(0.0)}です。"
        }
        val lowerWord = if (lowerInclusive) "以上" else "超"
        val upperWord = if (upperInclusive) "以下" else "未満"
        val band = when {
            lower == null -> "${bound(requireNotNull(upper))}$upperWord"
            upper == null -> "${bound(lower)}$lowerWord"
            else -> "${bound(lower)}$lowerWord〜${bound(upper)}$upperWord"
        }
        val position = when {
            lower == null -> "下限のない帯で、上側境界${bound(requireNotNull(upper))}を${difference(abs(upper - value))}下回ります"
            upper == null -> "上限のない帯で、下側境界${bound(lower)}を${difference(abs(value - lower))}上回ります"
            else -> {
                val fraction = ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0)
                val side = if (value - lower <= upper - value) "下側" else "上側"
                val boundary = if (side == "下側") lower else upper
                "帯の下端から約${(fraction * 100).roundToInt()}%の位置で、${side}境界${bound(boundary)}との差は${difference(abs(value - boundary))}です"
            }
        }
        // Equality belongs to the configured inclusive side, with zero distance rather than a crossing.
        val exact = listOfNotNull(lower, upper).firstOrNull { value == it }
        return "判定範囲は${band}です。" + if (exact != null) {
            "${if (exact == lower) "下側" else "上側"}境界上にあり、境界との差は${difference(0.0)}です。"
        } else "$position。"
    }

    fun threshold(value: Double, cutoffs: List<Double>, unit: String, minimum: Double? = null): String {
        val index = cutoffs.indexOfFirst { value <= it }.let { if (it < 0) cutoffs.size else it }
        return range(value, if (index == 0) minimum else cutoffs[index - 1], cutoffs.getOrNull(index), unit,
            lowerInclusive = index == 0 && minimum != null)
    }

    fun yield(value: Double, minimum: Double, average: Double, maximum: Double): String {
        if (!listOf(minimum, average, maximum).all { it.isFinite() } || minimum > average || average > maximum)
            return "過去実績の比較基準が確定していないため、判定帯・境界・帯内位置・境界との差は算出できません。"
        return when {
            value < minimum -> range(value, null, minimum, "%", upperInclusive = false)
            value > maximum -> range(value, maximum, null, "%")
            value < average -> range(value, minimum, average, "%", lowerInclusive = true, upperInclusive = false)
            else -> range(value, average, maximum, "%", lowerInclusive = true)
        }
    }

    fun cagr(value: Double): String = when {
        value > 0 -> range(value, 0.0, null, "%")
        value < 0 -> range(value, null, 0.0, "%", upperInclusive = false)
        else -> range(value, 0.0, 0.0, "%", lowerInclusive = true)
    }
}
