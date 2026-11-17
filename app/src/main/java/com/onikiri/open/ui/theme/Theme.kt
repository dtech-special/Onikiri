package com.onikiri.open.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.onikiri.open.data.ThemeMode

internal val onikiriLightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

internal val onikiriDarkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnikiriTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    presetId: String,
    amoled: Boolean,
    fontId: String,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val base = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        presetColorScheme(presetId, dark)
    }
    val target = if (dark && amoled) base.toAmoled() else base
    val scheme = target.animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(target.background.toArgb()))
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val typography = remember(fontId) { appTypography(fontFamilyFor(fontId)) }

    // Expressive theme: spring-based motion scheme used by MaterialTheme.motionScheme everywhere.
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun Color.anim(): Color =
    animateColorAsState(targetValue = this, animationSpec = tween(450), label = "themeColor").value

/** Cross-fades every role of the scheme so theme, accent and AMOLED switches are smooth. */
@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = primary.anim(),
    onPrimary = onPrimary.anim(),
    primaryContainer = primaryContainer.anim(),
    onPrimaryContainer = onPrimaryContainer.anim(),
    inversePrimary = inversePrimary.anim(),
    secondary = secondary.anim(),
    onSecondary = onSecondary.anim(),
    secondaryContainer = secondaryContainer.anim(),
    onSecondaryContainer = onSecondaryContainer.anim(),
    tertiary = tertiary.anim(),
    onTertiary = onTertiary.anim(),
    tertiaryContainer = tertiaryContainer.anim(),
    onTertiaryContainer = onTertiaryContainer.anim(),
    background = background.anim(),
    onBackground = onBackground.anim(),
    surface = surface.anim(),
    onSurface = onSurface.anim(),
    surfaceVariant = surfaceVariant.anim(),
    onSurfaceVariant = onSurfaceVariant.anim(),
    surfaceTint = surfaceTint.anim(),
    inverseSurface = inverseSurface.anim(),
    inverseOnSurface = inverseOnSurface.anim(),
    error = error.anim(),
    onError = onError.anim(),
    errorContainer = errorContainer.anim(),
    onErrorContainer = onErrorContainer.anim(),
    outline = outline.anim(),
    outlineVariant = outlineVariant.anim(),
    scrim = scrim.anim(),
    surfaceBright = surfaceBright.anim(),
    surfaceDim = surfaceDim.anim(),
    surfaceContainer = surfaceContainer.anim(),
    surfaceContainerHigh = surfaceContainerHigh.anim(),
    surfaceContainerHighest = surfaceContainerHighest.anim(),
    surfaceContainerLow = surfaceContainerLow.anim(),
    surfaceContainerLowest = surfaceContainerLowest.anim()
)
