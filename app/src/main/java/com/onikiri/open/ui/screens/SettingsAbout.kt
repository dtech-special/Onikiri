package com.onikiri.open.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikiri.open.R
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.SettingsSection
import com.onikiri.open.ui.components.SubScreen
import com.onikiri.open.ui.theme.emphasized

private const val GITHUB_URL = "https://github.com/dtech-special/Onikiri"

@Composable
fun AboutScreen(onOpenLicenses: () -> Unit, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SubScreen(title = stringResource(R.string.cat_about), onBack = onBack) {
        item(key = "about") {
            SettingsSection(title = stringResource(R.string.app_name)) {
                ListItem(
                    headlineContent = { Text(versionLine(), style = MaterialTheme.typography.titleMedium.emphasized()) },
                    supportingContent = { Text(stringResource(R.string.about_license_line)) },
                    leadingContent = { Icon(AppIcons.about(), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_github)) },
                    supportingContent = { Text(GITHUB_URL.removePrefix("https://")) },
                    leadingContent = { Icon(AppIcons.code(), contentDescription = null) },
                    trailingContent = { Icon(AppIcons.openInNew(), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { uriHandler.openUri(GITHUB_URL) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_licenses)) },
                    supportingContent = { Text(stringResource(R.string.about_licenses_sub)) },
                    leadingContent = { Icon(AppIcons.licenses(), contentDescription = null) },
                    trailingContent = { Icon(AppIcons.chevronRight(), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenLicenses)
                )
            }
        }
    }
}

private class LicenseEntry(
    val name: String,
    val license: String,
    val copyright: String,
    val url: String,
    /** File in assets/licenses with the full license text, when the license requires shipping it. */
    val textAsset: String? = null
)

private val LicenseEntries = listOf(
    LicenseEntry("Onikiri", "GNU General Public License v3.0", "© Onikiri contributors", "https://www.gnu.org/licenses/gpl-3.0.html"),
    LicenseEntry("Material Icons / Symbols", "Apache License 2.0", "© Google LLC", "https://www.apache.org/licenses/LICENSE-2.0"),
    LicenseEntry("Jetpack Compose & AndroidX (Material 3, DataStore, Lifecycle, Graphics Shapes)", "Apache License 2.0", "© The Android Open Source Project", "https://developer.android.com/jetpack/androidx"),
    LicenseEntry("Kotlin & kotlinx.coroutines", "Apache License 2.0", "© JetBrains s.r.o. and Kotlin contributors", "https://kotlinlang.org/"),
    LicenseEntry("libsu", "Apache License 2.0", "© John Wu (topjohnwu)", "https://github.com/topjohnwu/libsu"),
    LicenseEntry("Inter (font)", "SIL Open Font License 1.1", "© The Inter Project Authors", "https://rsms.me/inter/", "Inter-OFL.txt"),
    LicenseEntry("JetBrains Mono (font)", "SIL Open Font License 1.1", "© The JetBrains Mono Project Authors", "https://www.jetbrains.com/lp/mono/", "JetBrainsMono-OFL.txt"),
    LicenseEntry("Outfit (font)", "SIL Open Font License 1.1", "© The Outfit Project Authors", "https://github.com/Outfitio/Outfit-Fonts", "Outfit-OFL.txt")
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    SubScreen(title = stringResource(R.string.about_licenses), onBack = onBack) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        items(LicenseEntries.size, key = { LicenseEntries[it].name }) { index ->
            LicenseCard(LicenseEntries[index])
        }
    }
}

@Composable
private fun LicenseCard(entry: LicenseEntry) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var showText by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = entry.name, style = MaterialTheme.typography.titleMedium.emphasized())
            Text(
                text = entry.license,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = entry.copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { uriHandler.openUri(entry.url) }
            )
            if (entry.textAsset != null) {
                Text(
                    text = stringResource(if (showText) R.string.licenses_hide_text else R.string.licenses_show_text),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { showText = !showText }
                )
                if (showText) {
                    val text = remember(entry.textAsset) {
                        try {
                            context.assets.open("licenses/${entry.textAsset}").bufferedReader().use { it.readText() }
                        } catch (_: Exception) {
                            entry.license
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
