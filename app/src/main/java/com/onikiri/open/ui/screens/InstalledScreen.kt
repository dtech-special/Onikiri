package com.onikiri.open.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RootModule
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.CenteredLoading
import com.onikiri.open.ui.components.CenteredMessage
import com.onikiri.open.ui.components.ConnectedAction
import com.onikiri.open.ui.components.ConnectedActions
import com.onikiri.open.ui.components.NavCapsuleClearance
import com.onikiri.open.ui.theme.emphasized
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.InstalledModuleUiItem
import com.onikiri.open.viewmodel.InstalledState
import com.onikiri.open.viewmodel.InstalledViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledScreen(
    installedVm: InstalledViewModel,
    catalogVm: CatalogViewModel,
    onUpdate: (RootModule) -> Unit,
    onPickLocal: () -> Unit
) {
    val state by installedVm.state.collectAsStateWithLifecycle()
    val refreshing by installedVm.refreshing.collectAsStateWithLifecycle()
    val items by catalogVm.installedItems.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { installedVm.ensureLoaded() }

    val onEnabledChange = remember(installedVm) { { dir: String, on: Boolean -> installedVm.setEnabled(dir, on) } }
    val onRemove = remember(installedVm) { { dir: String -> installedVm.setRemoval(dir, true) } }
    val onUndoRemove = remember(installedVm) { { dir: String -> installedVm.setRemoval(dir, false) } }
    val onUpdateCatalog = remember(catalogVm, onUpdate) {
        { catalogId: String -> catalogVm.moduleById(catalogId)?.let(onUpdate); Unit }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing && state is InstalledState.Ready,
            onRefresh = installedVm::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "installedState"
            ) { current ->
                when (current) {
                    InstalledState.Loading -> CenteredLoading()
                    InstalledState.NoRoot -> CenteredMessage(
                        text = stringResource(R.string.installed_no_root),
                        actionLabel = stringResource(R.string.catalog_retry),
                        onAction = installedVm::refresh
                    )
                    is InstalledState.Ready -> if (current.modules.isEmpty()) {
                        CenteredMessage(text = stringResource(R.string.installed_empty))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = NavCapsuleClearance + 72.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items, key = { it.dir }, contentType = { "installed_card" }) { module ->
                                InstalledCard(
                                    module = module,
                                    onEnabledChange = onEnabledChange,
                                    onRemove = onRemove,
                                    onUndoRemove = onUndoRemove,
                                    onUpdate = onUpdateCatalog,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onPickLocal,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = NavCapsuleClearance)
        ) {
            Icon(AppIcons.add(), contentDescription = stringResource(R.string.installed_add_zip))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstalledCard(
    module: InstalledModuleUiItem,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onUndoRemove: (String) -> Unit,
    onUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val canUpdate = module.canUpdate
    val updateLabel = stringResource(R.string.module_update)
    val removeLabel = stringResource(R.string.installed_remove)
    val undoLabel = stringResource(R.string.installed_undo)
    val updateIcon = AppIcons.update()
    val deleteIcon = AppIcons.delete()
    val restartIcon = AppIcons.restart()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = module.name, style = MaterialTheme.typography.titleMedium.emphasized())
                    val versionLine = buildString {
                        append(module.version.ifBlank { "?" })
                        if (module.versionCode > 0) append(" (").append(module.versionCode).append(")")
                        if (module.author.isNotBlank()) append("  ·  ").append(module.author)
                    }
                    Text(
                        text = versionLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = module.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = !module.disabled,
                    onCheckedChange = { onEnabledChange(module.dir, it) },
                    enabled = !module.removing
                )
            }
            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                module.removing -> Text(
                    text = stringResource(R.string.installed_will_remove),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                module.disabled -> Text(
                    text = stringResource(R.string.installed_disabled_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            val actions = buildList {
                if (canUpdate && !module.removing) {
                    add(ConnectedAction(updateLabel, { module.catalogId?.let(onUpdate) }, icon = updateIcon, primary = true))
                }
                if (module.removing) {
                    add(ConnectedAction(undoLabel, { onUndoRemove(module.dir) }, icon = restartIcon))
                } else {
                    add(ConnectedAction(removeLabel, { onRemove(module.dir) }, icon = deleteIcon, destructive = true))
                }
            }
            ConnectedActions(actions)
        }
    }
}
