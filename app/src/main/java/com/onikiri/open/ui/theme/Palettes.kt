package com.onikiri.open.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.onikiri.open.R
import kotlin.math.abs

@Immutable
data class AccentPreset(
    val id: String,
    @param:StringRes val nameRes: Int,
    val seed: Color
)

const val DEFAULT_PRESET_ID = "onikiri"

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset(DEFAULT_PRESET_ID, R.string.preset_onikiri, Color(0xFFE4C36C)),
    AccentPreset("ocean", R.string.preset_ocean, Color(0xFF2F7DD1)),
    AccentPreset("forest", R.string.preset_forest, Color(0xFF3E8E52)),
    AccentPreset("rose", R.string.preset_rose, Color(0xFFD6477B)),
    AccentPreset("violet", R.string.preset_violet, Color(0xFF7E57C2)),
    AccentPreset("crimson", R.string.preset_crimson, Color(0xFFC62828)),
    AccentPreset("mint", R.string.preset_mint, Color(0xFF1FB5A0)),
    AccentPreset("sunset", R.string.preset_sunset, Color(0xFFEF7C2B))
)

fun presetColorScheme(presetId: String, dark: Boolean): ColorScheme {
    val preset = AccentPresets.firstOrNull { it.id == presetId } ?: AccentPresets.first()
    if (preset.id == DEFAULT_PRESET_ID) return if (dark) onikiriDarkScheme else onikiriLightScheme
    return schemeFromSeed(preset.seed, dark)
}

/** Pure black surfaces for OLED panels; only meaningful for dark schemes. */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1C1C1C)
)

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f)
    )
}

private fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return ((h * 60f) + 360f) % 360f
}

private fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val h = hueOf(seed)
    val t = h + 60f
    return if (dark) {
        darkColorScheme(
            primary = hsl(h, 0.70f, 0.78f),
            onPrimary = hsl(h, 0.80f, 0.18f),
            primaryContainer = hsl(h, 0.55f, 0.28f),
            onPrimaryContainer = hsl(h, 0.75f, 0.90f),
            secondary = hsl(h, 0.35f, 0.76f),
            onSecondary = hsl(h, 0.35f, 0.18f),
            secondaryContainer = hsl(h, 0.25f, 0.28f),
            onSecondaryContainer = hsl(h, 0.35f, 0.89f),
            tertiary = hsl(t, 0.45f, 0.76f),
            onTertiary = hsl(t, 0.45f, 0.18f),
            tertiaryContainer = hsl(t, 0.32f, 0.28f),
            onTertiaryContainer = hsl(t, 0.45f, 0.89f),
            error = errorDark,
            onError = onErrorDark,
            errorContainer = errorContainerDark,
            onErrorContainer = onErrorContainerDark,
            background = hsl(h, 0.14f, 0.08f),
            onBackground = hsl(h, 0.12f, 0.90f),
            surface = hsl(h, 0.14f, 0.08f),
            onSurface = hsl(h, 0.12f, 0.90f),
            surfaceVariant = hsl(h, 0.10f, 0.26f),
            onSurfaceVariant = hsl(h, 0.12f, 0.80f),
            outline = hsl(h, 0.08f, 0.58f),
            outlineVariant = hsl(h, 0.10f, 0.28f),
            scrim = Color.Black,
            inverseSurface = hsl(h, 0.12f, 0.90f),
            inverseOnSurface = hsl(h, 0.12f, 0.20f),
            inversePrimary = hsl(h, 0.60f, 0.38f),
            surfaceDim = hsl(h, 0.14f, 0.08f),
            surfaceBright = hsl(h, 0.12f, 0.24f),
            surfaceContainerLowest = hsl(h, 0.14f, 0.05f),
            surfaceContainerLow = hsl(h, 0.14f, 0.10f),
            surfaceContainer = hsl(h, 0.13f, 0.12f),
            surfaceContainerHigh = hsl(h, 0.12f, 0.16f),
            surfaceContainerHighest = hsl(h, 0.12f, 0.20f)
        )
    } else {
        lightColorScheme(
            primary = hsl(h, 0.60f, 0.38f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, 0.75f, 0.90f),
            onPrimaryContainer = hsl(h, 0.70f, 0.15f),
            secondary = hsl(h, 0.28f, 0.38f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h, 0.35f, 0.89f),
            onSecondaryContainer = hsl(h, 0.35f, 0.14f),
            tertiary = hsl(t, 0.40f, 0.38f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(t, 0.45f, 0.88f),
            onTertiaryContainer = hsl(t, 0.45f, 0.14f),
            error = errorLight,
            onError = onErrorLight,
            errorContainer = errorContainerLight,
            onErrorContainer = onErrorContainerLight,
            background = hsl(h, 0.25f, 0.97f),
            onBackground = hsl(h, 0.15f, 0.10f),
            surface = hsl(h, 0.25f, 0.97f),
            onSurface = hsl(h, 0.15f, 0.10f),
            surfaceVariant = hsl(h, 0.20f, 0.90f),
            onSurfaceVariant = hsl(h, 0.12f, 0.30f),
            outline = hsl(h, 0.08f, 0.46f),
            outlineVariant = hsl(h, 0.14f, 0.80f),
            scrim = Color.Black,
            inverseSurface = hsl(h, 0.10f, 0.20f),
            inverseOnSurface = hsl(h, 0.15f, 0.95f),
            inversePrimary = hsl(h, 0.65f, 0.75f),
            surfaceDim = hsl(h, 0.18f, 0.86f),
            surfaceBright = hsl(h, 0.25f, 0.97f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.25f, 0.95f),
            surfaceContainer = hsl(h, 0.22f, 0.93f),
            surfaceContainerHigh = hsl(h, 0.20f, 0.91f),
            surfaceContainerHighest = hsl(h, 0.20f, 0.89f)
        )
    }
}
