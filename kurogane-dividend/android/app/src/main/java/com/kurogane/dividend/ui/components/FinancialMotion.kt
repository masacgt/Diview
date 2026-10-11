package com.kurogane.dividend.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import java.text.NumberFormat
import java.util.Locale

/** Rolls only changed digits; separators and the currency prefix remain still. */
@Composable
fun RollingMoney(amount: String, style: TextStyle, modifier: Modifier = Modifier) {
    val mergedStyle = LocalTextStyle.current.merge(style)
    Row(modifier = modifier) {
        amount.forEachIndexed { index, char ->
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(tween(420)) { height -> if (targetState > initialState) height else -height } + fadeIn(tween(240))) togetherWith
                            (slideOutVertically(tween(420)) { height -> if (targetState > initialState) -height else height } + fadeOut(tween(240)))
                    },
                    label = "Digit $index"
                ) { digit -> Text(digit.toString(), style = mergedStyle, maxLines = 1) }
            } else {
                Text(char.toString(), style = mergedStyle, maxLines = 1)
            }
        }
    }
}

@Composable
fun SmoothMoney(value: Double, suffix: String = "", style: TextStyle, modifier: Modifier = Modifier, inTenThousands: Boolean = false) {
    val mergedStyle = LocalTextStyle.current.merge(style)
    val doubleConverter = androidx.compose.runtime.remember {
        TwoWayConverter<Double, AnimationVector1D>(
            convertToVector = { AnimationVector1D(it.toFloat()) },
            convertFromVector = { it.value.toDouble() }
        )
    }
    val animated by animateValueAsState(value, doubleConverter, tween(400), label = "Simulation amount")
    val formatted = if (inTenThousands) "¥${kotlin.math.round(animated / 10000).toLong()}万"
        else "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(kotlin.math.round(animated).toLong())}"
    Text(formatted + suffix, modifier = modifier, style = mergedStyle, maxLines = 1)
}
