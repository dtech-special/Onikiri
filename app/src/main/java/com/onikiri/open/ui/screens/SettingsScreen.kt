package com.onikiri.open.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.ManagerChoice
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.ThemeMode
import com.onikiri.open.root.RootManager
import com.onikiri.open.ui.components.CheckIcon
import com.onikiri.open.ui.components.InfoLine
import com.onikiri.open.ui.components.SettingsSection
import com.onikiri.open.ui.components.SwitchRow
import com.onikiri.open.ui.components.formatBytes
import com.onikiri.open.ui.theme.AccentPreset
import com.onikiri.open.ui.theme.AccentPresets
import com.onikiri.open.viewmodel.AddRepoResult
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.RootViewModel
import com.onikiri.open.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel,
    rootVm: RootViewModel,
    catalogVm: CatalogViewModel,
    installBusy: Boolean,
    snackbar: SnackbarHostState
) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val cacheSize by settingsVm.cacheSize.collectAsStateWithLifecycle()
    val rootInfo by rootVm.info.collectAsStateWithLifecycle()
    val refreshing by catalogVm.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var repoInput by rememberSaveable { mutableStateOf("") }
    var repoError by remember { mutableStateOf<Int?>(null) }
    val clearedMessage = stringResource(R.string.cache_cleared)
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(Unit) { rootVm.ensureLoaded() }
    LaunchedEffect(installBusy) { settingsVm.refreshCacheSize() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Appearance -------------------------------------------------------------------
        item(key = "appearance") {
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
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
                AnimatedVisibility(
                    visible = !(settings.dynamicColor && dynamicSupported),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_accent),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
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
        }

        // ---- Repositories -----------------------------------------------------------------
        item(key = "repos") {
            SettingsSection(title = stringResource(R.string.settings_repos)) {
                RepoRow(
                    url = RepoUrl.BUILT_IN,
                    label = stringResource(R.string.repo_builtin),
                    onRemove = null
                )
                for (url in settings.customRepos) {
                    RepoRow(url = url, label = null, onRemove = { settingsVm.removeRepo(url) })
                }
                val currentRepoError = repoError
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = {
                        repoInput = it
                        repoError = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    isError = currentRepoError != null,
                    placeholder = { Text(stringResource(R.string.repo_add_hint)) },
                    supportingText = if (currentRepoError != null) ({ Text(stringResource(currentRepoError)) }) else null,
                    trailingIcon = {
                        IconButton(onClick = {
                            when (settingsVm.addRepo(repoInput)) {
                                AddRepoResult.ADDED -> {
                                    repoInput = ""
                                    repoError = null
                                }
                                AddRepoResult.INVALID -> repoError = R.string.repo_invalid
                                AddRepoResult.DUPLICATE -> repoError = R.string.repo_duplicate
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.repo_add))
                        }
                    }
                )
                Text(
                    text = "${stringResource(R.string.hint)} ${stringResource(R.string.raw_url_hint)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(
                    onClick = { catalogVm.refresh(force = true) },
                    enabled = !refreshing,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = stringResource(R.string.repo_refresh),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // ---- Install behaviour ------------------------------------------------------------
        item(key = "install") {
            SettingsSection(title = stringResource(R.string.settings_install)) {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_clean),
                    subtitle = stringResource(R.string.settings_auto_clean_sub),
                    checked = settings.autoCleanTemp,
                    onCheckedChange = settingsVm::setAutoClean
                )
                SwitchRow(
                    title = stringResource(R.string.settings_confirm_soft),
                    subtitle = stringResource(R.string.settings_confirm_soft_sub),
                    checked = settings.confirmSoftReboot,
                    onCheckedChange = settingsVm::setConfirmSoftReboot
                )
                Text(
                    text = stringResource(R.string.settings_manager),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val choices = listOf(
                        ManagerChoice.AUTO to stringResource(R.string.manager_auto),
                        ManagerChoice.MAGISK to stringResource(R.string.magisk),
                        ManagerChoice.KERNELSU to stringResource(R.string.ksu),
                        ManagerChoice.APATCH to stringResource(R.string.apatch)
                    )
                    for ((choice, label) in choices) {
                        val selected = settings.managerChoice == choice
                        FilterChip(
                            selected = selected,
                            onClick = { settingsVm.setManagerChoice(choice) },
                            label = { Text(label) },
                            leadingIcon = if (selected) ({ CheckIcon(Modifier.size(18.dp)) }) else null
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_manager_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // ---- Storage ----------------------------------------------------------------------
        item(key = "storage") {
            SettingsSection(title = stringResource(R.string.settings_storage)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.storage_downloads)) },
                    supportingContent = { Text(formatBytes(cacheSize)) },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                    trailingContent = {
                        OutlinedButton(
                            enabled = !installBusy && cacheSize > 0,
                            onClick = {
                                scope.launch {
                                    settingsVm.clearCache()
                                    snackbar.showSnackbar(clearedMessage)
                                }
                            }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.storage_clear),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        // ---- System summary ---------------------------------------------------------------
        item(key = "system") {
            SettingsSection(
                title = stringResource(R.string.settings_system),
                trailing = {
                    IconButton(onClick = { rootVm.refresh() }, enabled = !rootInfo.checking) {
                        if (rootInfo.checking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sys_recheck))
                        }
                    }
                }
            ) {
                InfoLine(
                    label = stringResource(R.string.sys_root),
                    value = stringResource(
                        when {
                            rootInfo.checking || !rootInfo.loaded -> R.string.sys_root_checking
                            rootInfo.status.granted -> R.string.sys_root_granted
                            else -> R.string.sys_root_denied
                        }
                    )
                )
                val managers = rootInfo.status.managers
                InfoLine(
                    label = stringResource(R.string.sys_manager),
                    value = if (managers.isEmpty()) {
                        stringResource(R.string.sys_none)
                    } else {
                        managers.joinToString("\n") { detected ->
                            val name = when (detected.manager) {
                                RootManager.MAGISK -> "Magisk"
                                RootManager.KERNELSU -> "KernelSU"
                                RootManager.APATCH -> "APatch"
                            }
                            if (detected.version != null) "$name — ${detected.version}" else name
                        }
                    }
                )
                InfoLine(stringResource(R.string.sys_kernel), settingsVm.system.kernel)
                InfoLine(stringResource(R.string.sys_arch), settingsVm.system.architecture)
                InfoLine(stringResource(R.string.sys_android), settingsVm.system.android)
                InfoLine(stringResource(R.string.sys_device), settingsVm.system.device)
                InfoLine(stringResource(R.string.sys_app), settingsVm.appVersion)
            }
        }
    }
}

@Composable
private fun RepoRow(url: String, label: String?, onRemove: (() -> Unit)?) {
    ListItem(
        headlineContent = {
            Text(
                text = label ?: url.removePrefix("https://"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (label != null) ({
            Text(text = url.removePrefix("https://"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }) else null,
        leadingContent = {
            Icon(
                imageVector = if (onRemove == null) Icons.Default.Lock else Icons.Default.Security,
                contentDescription = null
            )
        },
        trailingContent = if (onRemove != null) ({
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.repo_remove))
            }
        }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PresetSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "swatchRing"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
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
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
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
