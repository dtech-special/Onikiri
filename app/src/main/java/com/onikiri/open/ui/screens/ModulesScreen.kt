package com.onikiri.open.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.repoNameFromUrl
import com.onikiri.open.ui.components.CenteredLoading
import com.onikiri.open.ui.components.CenteredMessage
import com.onikiri.open.ui.components.FilterChipsRow
import com.onikiri.open.ui.components.FilterOption
import com.onikiri.open.ui.components.ModuleList
import com.onikiri.open.viewmodel.CatalogState
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.InstalledViewModel

private const val FILTER_ALL = "all"
private const val FILTER_INSTALLED = "installed"
private const val FILTER_REPO_PREFIX = "repo:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    catalogVm: CatalogViewModel,
    installedVm: InstalledViewModel,
    onInstall: (RootModule) -> Unit
) {
    val state by catalogVm.state.collectAsStateWithLifecycle()
    val refreshing by catalogVm.refreshing.collectAsStateWithLifecycle()
    val expanded by catalogVm.expanded.collectAsStateWithLifecycle()
    val details by catalogVm.details.collectAsStateWithLifecycle()
    val repoNames by catalogVm.repoNames.collectAsStateWithLifecycle()
    val installed by installedVm.byId.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var filterKey by rememberSaveable { mutableStateOf(FILTER_ALL) }

    // Release info of installed modules is needed to tell "Installed" from "Update".
    LaunchedEffect(state, installed.keys) {
        if (state is CatalogState.Ready && installed.isNotEmpty()) catalogVm.preloadDetails(installed.keys)
    }

    val ready = state as? CatalogState.Ready
    val allLabel = stringResource(R.string.filter_all)
    val installedLabel = stringResource(R.string.filter_installed)
    val options = remember(ready?.modules, repoNames, allLabel, installedLabel) {
        buildList {
            add(FilterOption(FILTER_ALL, allLabel))
            add(FilterOption(FILTER_INSTALLED, installedLabel))
            ready?.modules?.map { it.repoUrl }?.distinct()?.filter { it.isNotEmpty() }?.forEach { url ->
                add(FilterOption(FILTER_REPO_PREFIX + url, repoNames[url] ?: repoNameFromUrl(url)))
            }
        }
    }
    val activeKey = if (options.any { it.key == filterKey }) filterKey else FILTER_ALL

    Column(modifier = Modifier.fillMaxSize()) {
        if (ready != null) {
            FilterChipsRow(options = options, selectedKey = activeKey, onSelect = { filterKey = it })
        }
        PullToRefreshBox(
            isRefreshing = refreshing && state is CatalogState.Ready,
            onRefresh = { catalogVm.refresh(force = true) },
            modifier = Modifier.weight(1f)
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "catalogState"
            ) { current ->
                when (current) {
                    CatalogState.Loading -> CenteredLoading()
                    is CatalogState.Error -> CenteredMessage(
                        text = stringResource(R.string.catalog_error, current.message),
                        actionLabel = stringResource(R.string.catalog_retry),
                        onAction = { catalogVm.refresh(force = true) }
                    )
                    is CatalogState.Ready -> {
                        val visible = when {
                            activeKey == FILTER_ALL -> current.modules
                            activeKey == FILTER_INSTALLED -> current.modules.filter { it.id in installed }
                            else -> current.modules.filter { it.repoUrl == activeKey.removePrefix(FILTER_REPO_PREFIX) }
                        }
                        if (visible.isEmpty()) {
                            CenteredMessage(
                                text = stringResource(
                                    if (current.modules.isEmpty()) R.string.catalog_empty else R.string.filter_empty
                                )
                            )
                        } else {
                            val banner = when {
                                current.offline -> stringResource(R.string.catalog_offline)
                                current.failedRepos > 0 -> stringResource(R.string.catalog_partial, current.failedRepos)
                                else -> null
                            }
                            ModuleList(
                                modules = visible,
                                installed = installed,
                                expanded = expanded,
                                details = details,
                                onToggle = catalogVm::toggle,
                                onInstall = onInstall,
                                onRetryDetails = { catalogVm.loadDetails(it, force = true) },
                                onOpenSource = { uriHandler.openUri("https://github.com/${it.githubRepo}") },
                                banner = banner
                            )
                        }
                    }
                }
            }
        }
    }
}
