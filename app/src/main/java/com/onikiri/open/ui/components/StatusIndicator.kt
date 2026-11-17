package com.onikiri.open.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IndicatorState { WORKING, SUCCESS, FAILED }

/** The shapes the Material 3 Expressive loading indicator morphs through while work is running. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val LoadingPolygons = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Flower
)

/**
 * Morphing loading indicator in the accent colour that springs into a check mark on success or an
 * error badge on failure.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusIndicator(
    state: IndicatorState,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier.size(size),
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.55f, stiffness = 380f), initialScale = 0.4f) +
                fadeIn(tween(150))) togetherWith
                (scaleOut(tween(150), targetScale = 0.6f) + fadeOut(tween(120)))
        },
        label = "statusIndicator"
    ) { current ->
        when (current) {
            IndicatorState.WORKING -> ContainedLoadingIndicator(
                modifier = Modifier.size(size),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
                polygons = LoadingPolygons
            )
            IndicatorState.SUCCESS -> ResultBadge(
                shape = MaterialShapes.Cookie9Sided.toShape(),
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                icon = AppIcons.check(),
                size = size
            )
            IndicatorState.FAILED -> ResultBadge(
                shape = MaterialShapes.Sunny.toShape(),
                container = MaterialTheme.colorScheme.error,
                content = MaterialTheme.colorScheme.onError,
                icon = AppIcons.close(),
                size = size
            )
        }
    }
}

@Composable
private fun ResultBadge(shape: Shape, container: Color, content: Color, icon: Painter, size: Dp) {
    Box(
        modifier = Modifier.size(size).background(container, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** Plain morphing indicator for list loading and refreshing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphLoading(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier, polygons = LoadingPolygons)
}
