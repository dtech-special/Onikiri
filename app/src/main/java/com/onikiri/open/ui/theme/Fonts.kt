package com.onikiri.open.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.onikiri.open.R

@Immutable
data class FontOption(val id: String, @param:StringRes val nameRes: Int)

const val SYSTEM_FONT_ID = "system"

val FontOptions: List<FontOption> = listOf(
    FontOption(SYSTEM_FONT_ID, R.string.font_system),
    FontOption("geometric", R.string.font_geometric),
    FontOption("inter", R.string.font_inter),
    FontOption("jetbrains_mono", R.string.font_jetbrains_mono)
)

/** All bundled fonts are licensed under the SIL Open Font License 1.1 (see About > Licenses). */
fun fontFamilyFor(id: String): FontFamily? = when (id) {
    "geometric" -> FontFamily(
        Font(R.font.outfit_regular, FontWeight.Normal),
        Font(R.font.outfit_bold, FontWeight.Bold)
    )
    "inter" -> FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    "jetbrains_mono" -> FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
    )
    else -> null
}
