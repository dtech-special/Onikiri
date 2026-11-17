package com.onikiri.open.root

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.onikiri.open.R

/**
 * Launcher icon colours. Every entry is an <activity-alias> in AndroidManifest.xml carrying its own
 * LAUNCHER intent filter; exactly one alias is enabled at a time.
 */
enum class LauncherIcon(
    val id: String,
    val alias: String,
    @param:StringRes val nameRes: Int,
    val previewColor: Color
) {
    VIOLET("violet", "IconViolet", R.string.icon_violet, Color(0xFFE8DDFF)),
    EMERALD("emerald", "IconEmerald", R.string.icon_emerald, Color(0xFFCFF5E0)),
    CRIMSON("crimson", "IconCrimson", R.string.icon_crimson, Color(0xFFFFD9D6)),
    CYBER("cyber", "IconCyber", R.string.icon_cyber, Color(0xFFCDE6FF)),
    MONO("mono", "IconMono", R.string.icon_mono, Color(0xFFE6E6E6));

    companion object {
        fun fromId(id: String): LauncherIcon = entries.firstOrNull { it.id == id } ?: VIOLET

        private fun component(context: Context, icon: LauncherIcon) =
            ComponentName(context.packageName, "${context.packageName}.${icon.alias}")

        /** The icon whose alias is currently enabled (VIOLET is enabled in the manifest by default). */
        fun current(context: Context): LauncherIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                when (pm.getComponentEnabledSetting(component(context, icon))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == VIOLET
                    else -> false
                }
            } ?: VIOLET
        }

        /**
         * Enables the chosen alias first and only then disables the others, so there is never a
         * moment without a launcher entry. DONT_KILL_APP keeps the app running.
         */
        fun apply(context: Context, icon: LauncherIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                component(context, icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            for (other in entries) {
                if (other == icon) continue
                pm.setComponentEnabledSetting(
                    component(context, other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
