package com.onikiri.open.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.ThemeMode
import com.onikiri.open.root.LauncherIcon
import com.onikiri.open.ui.components.CheckIcon
import com.onikiri.open.ui.components.SettingsSection
import com.onikiri.open.ui.components.SubScreen
import com.onikiri.open.ui.components.SwitchRow
import com.onikiri.open.ui.theme.AccentPreset
import com.onikiri.open.ui.theme.AccentPresets
import com.onikiri.open.ui.theme.FontOptions
import com.onikiri.open.ui.theme.fontFamilyFor
import com.onikiri.open.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomizationScreen(
    settingsVm: SettingsViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit
) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val launcherIcon by settingsVm.launcherIcon.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val iconChanged = stringResource(R.string.icon_changed)

    SubScreen(title = stringResource(R.string.cat_custom), onBack = onBack) {
        item(key = "preview") {
            // Everything here uses the live theme, so changes are visible immediately.
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.preview_text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {}) { Text(stringResource(R.string.module_install)) }
                        FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.filter_all)) })
                    }
                }
            }
        }

        item(key = "theme") {
            SettingsSection(title = stringResource(R.string.settings_theme)) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { settingsVm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                        ) { Text(stringResource(label)) }
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_amoled),
                    subtitle = stringResource(R.string.settings_amoled_sub),
                    checked = settings.amoled,
                    onCheckedChange = settingsVm::setAmoled
                )
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic),
                    subtitle = stringResource(
                        if (dynamicSupported) R.string.settings_dynamic_sub else R.string.settings_dynamic_unavailable
                    ),
                    checked = settings.dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onCheckedChange = settingsVm::setDynamicColor
                )
            }
        }

        item(key = "accent") {
            AnimatedVisibility(
                visible = !(settings.dynamicColor && dynamicSupported),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                SettingsSection(title = stringResource(R.string.settings_accent)) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (preset in AccentPresets) {
                            PresetSwatch(
                                preset = preset,
                                selected = settings.presetId == preset.id,
                                onClick = { settingsVm.setPreset(preset.id) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "font") {
            SettingsSection(title = stringResource(R.string.settings_font)) {
                for (option in FontOptions) {
                    val family = fontFamilyFor(option.id) ?: FontFamily.Default
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(option.nameRes), fontFamily = family)
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.font_sample), fontFamily = family)
                        },
                        leadingContent = { RadioButton(selected = settings.fontId == option.id, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { settingsVm.setFont(option.id) }
                    )
                }
            }
        }

        item(key = "icon") {
            SettingsSection(title = stringResource(R.string.settings_launcher_icon)) {
                Text(
                    text = stringResource(R.string.settings_launcher_icon_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (icon in LauncherIcon.entries) {
                        IconSwatch(
                            icon = icon,
                            selected = launcherIcon == icon,
                            onClick = {
                                if (launcherIcon != icon) {
                                    settingsVm.setLauncherIcon(icon)
                                    scope.launch { snackbar.showSnackbar(iconChanged) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "swatchRing"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchScale"
    )
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(BorderStroke(3.dp, ring), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(preset.seed),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                CheckIcon(tint = Color.White)
            }
        }
        Text(
            text = stringResource(preset.nameRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconSwatch(icon: LauncherIcon, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "iconRing"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale"
    )
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(BorderStroke(if (selected) 3.dp else 1.dp, ring), CircleShape)
                .clip(CircleShape)
                .background(icon.previewColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(56.dp)
            )
        }
        Text(
            text = stringResource(icon.nameRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
