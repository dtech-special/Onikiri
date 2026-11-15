package com.onikiri.open.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

private val TOKEN = Regex("\\s+|\\S+")

/**
 * Text whose words fade in one after another. Spacing, indentation and wrapping are preserved
 * because it is a single Text with per-word alpha, not a row of separate words.
 */
@Composable
fun WordRevealText(
    text: String,
    animate: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tokens = remember(text) { TOKEN.findAll(text).map { it.value }.toList() }
    val wordCount = remember(tokens) { tokens.count { !it[0].isWhitespace() } }
    val progress = remember(text) {
        Animatable(if (animate && wordCount > 0) 0f else wordCount.toFloat())
    }

    LaunchedEffect(text, animate) {
        if (animate && wordCount > 0) {
            progress.animateTo(
                targetValue = wordCount.toFloat(),
                animationSpec = tween(
                    durationMillis = (wordCount * 45).coerceIn(160, 900),
                    easing = LinearEasing
                )
            )
        } else {
            progress.snapTo(wordCount.toFloat())
        }
    }

    val current = progress.value
    val annotated = buildAnnotatedString {
        var wordIndex = 0
        for (token in tokens) {
            if (token[0].isWhitespace()) {
                append(token)
            } else {
                val alpha = (current - wordIndex).coerceIn(0f, 1f)
                withStyle(SpanStyle(color = color.copy(alpha = color.alpha * alpha))) { append(token) }
                wordIndex++
            }
        }
    }
    Text(text = annotated, style = style, modifier = modifier)
}
