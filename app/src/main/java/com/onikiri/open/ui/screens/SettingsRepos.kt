package com.onikiri.open.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.repoNameFromUrl
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.ConnectedAction
import com.onikiri.open.ui.components.ConnectedActions
import com.onikiri.open.ui.components.SettingsSection
import com.onikiri.open.ui.components.SubScreen
import com.onikiri.open.ui.components.formatBytes
import com.onikiri.open.ui.theme.emphasized
import com.onikiri.open.viewmodel.AddRepoResult
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun ReposCacheScreen(
    settingsVm: SettingsViewModel,
    catalogVm: CatalogViewModel,
    installBusy: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val cacheSize by settingsVm.cacheSize.collectAsStateWithLifecycle()
    val names by catalogVm.repoNames.collectAsStateWithLifecycle()
    val refreshing by catalogVm.refreshing.collectAsStateWithLifecycle()

    var repoInput by rememberSaveable { mutableStateOf("") }
    var repoError by remember { mutableStateOf<Int?>(null) }

    val clearedMessage = stringResource(R.string.cache_cleared)
    val exportedMessage = stringResource(R.string.repos_exported)
    val nothingToExport = stringResource(R.string.repos_nothing_to_export)
    val clipboardEmpty = stringResource(R.string.repos_clipboard_empty)
    val exportLabel = stringResource(R.string.repos_export)
    val importLabel = stringResource(R.string.repos_import)
    val exportIcon = AppIcons.exportFile()
    val importIcon = AppIcons.importFile()

    LaunchedEffect(installBusy) { settingsVm.refreshCacheSize() }

    SubScreen(title = stringResource(R.string.cat_repos), onBack = onBack) {
        item(key = "sources") {
            SettingsSection(title = stringResource(R.string.settings_repos)) {
                val builtIn = RepoUrl.BUILT_IN
                RepoRow(
                    name = names[builtIn] ?: repoNameFromUrl(builtIn),
                    url = builtIn,
                    enabled = builtIn !in settings.disabledRepos,
                    onEnabledChange = { settingsVm.setRepoEnabled(builtIn, it) },
                    onRemove = null
                )
                for (url in settings.customRepos) {
                    RepoRow(
                        name = names[url] ?: repoNameFromUrl(url),
                        url = url,
                        enabled = url !in settings.disabledRepos,
                        onEnabledChange = { settingsVm.setRepoEnabled(url, it) },
                        onRemove = { settingsVm.removeRepo(url) }
                    )
                }
                val currentError = repoError
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = {
                        repoInput = it
                        repoError = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    isError = currentError != null,
                    placeholder = { Text(stringResource(R.string.repo_add_hint)) },
                    supportingText = if (currentError != null) ({ Text(stringResource(currentError)) }) else null,
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
                            Icon(AppIcons.add(), contentDescription = stringResource(R.string.repo_add))
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.repo_any_server_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                ConnectedActions(
                    actions = listOf(
                        ConnectedAction(
                            label = exportLabel,
                            icon = exportIcon,
                            onClick = {
                                val text = settingsVm.exportRepos()
                                if (text.isBlank()) {
                                    scope.launch { snackbar.showSnackbar(nothingToExport) }
                                } else {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Onikiri repositories", text))
                                    scope.launch { snackbar.showSnackbar(exportedMessage) }
                                }
                            }
                        ),
                        ConnectedAction(
                            label = importLabel,
                            icon = importIcon,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (text.isBlank()) {
                                    scope.launch { snackbar.showSnackbar(clipboardEmpty) }
                                } else {
                                    val result = settingsVm.importRepos(text)
                                    val message = context.getString(R.string.repos_imported, result.added, result.skipped)
                                    scope.launch { snackbar.showSnackbar(message) }
                                }
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Button(
                    onClick = { catalogVm.refresh(force = true) },
                    enabled = !refreshing,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)
                ) {
                    Icon(AppIcons.refresh(), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.repo_refresh),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        item(key = "cache") {
            SettingsSection(title = stringResource(R.string.settings_storage)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.storage_downloads)) },
                    supportingContent = { Text(formatBytes(cacheSize)) },
                    leadingContent = { Icon(AppIcons.storage(), contentDescription = null) },
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
                            Icon(AppIcons.deleteSweep(), contentDescription = null, modifier = Modifier.size(18.dp))
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
    }
}

/** Readable name as a large title, the full URL small underneath, a switch and (for custom ones) delete. */
@Composable
private fun RepoRow(
    name: String,
    url: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: (() -> Unit)?
) {
    ListItem(
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.emphasized(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(AppIcons.delete(), contentDescription = stringResource(R.string.repo_remove))
                    }
                } else {
                    Icon(
                        AppIcons.lock(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
