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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RootModule
import com.onikiri.open.ui.components.CenteredLoading
import com.onikiri.open.ui.components.CenteredMessage
import com.onikiri.open.ui.components.FilterChipsRow
import com.onikiri.open.ui.components.FilterOption
import com.onikiri.open.ui.components.ModuleList
import com.onikiri.open.viewmodel.CatalogFilter
import com.onikiri.open.viewmodel.CatalogState
import com.onikiri.open.viewmodel.CatalogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    catalogVm: CatalogViewModel,
    onInstall: (RootModule) -> Unit
) {
    val state by catalogVm.state.collectAsStateWithLifecycle()
    val refreshing by catalogVm.refreshing.collectAsStateWithLifecycle()
    val filter by catalogVm.filter.collectAsStateWithLifecycle()
    val visible by catalogVm.visibleItems.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val allLabel = stringResource(R.string.filter_all)
    val installedLabel = stringResource(R.string.filter_installed)
    val verifiedLabel = stringResource(R.string.filter_verified)
    val reviewLabel = stringResource(R.string.filter_review)
    val hazardLabel = stringResource(R.string.filter_hazard)
    // Fixed set of filters; nothing is generated from repository file names any more.
    val options = remember(allLabel, installedLabel, verifiedLabel, reviewLabel, hazardLabel) {
        listOf(
            FilterOption(CatalogFilter.ALL.name, allLabel),
            FilterOption(CatalogFilter.INSTALLED.name, installedLabel),
            FilterOption(CatalogFilter.VERIFIED.name, verifiedLabel),
            FilterOption(CatalogFilter.REVIEW.name, reviewLabel),
            FilterOption(CatalogFilter.HAZARD.name, hazardLabel)
        )
    }
    val onSelectFilter = remember(catalogVm) { { key: String -> catalogVm.setFilter(CatalogFilter.valueOf(key)) } }
    val onToggle = remember(catalogVm) { { id: String -> catalogVm.toggle(id) } }
    val onRetry = remember(catalogVm) { { id: String -> catalogVm.retryDetails(id) } }
    val onInstallId = remember(catalogVm, onInstall) {
        { id: String -> catalogVm.moduleById(id)?.let(onInstall); Unit }
    }
    val onOpenSource = remember(uriHandler) { { repo: String -> uriHandler.openUri("https://github.com/$repo") } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state is CatalogState.Ready) {
            FilterChipsRow(options = options, selectedKey = filter.name, onSelect = onSelectFilter)
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
                        val items = visible
                        when {
                            items == null -> CenteredLoading()
                            items.isEmpty() -> CenteredMessage(
                                text = stringResource(
                                    if (current.modules.isEmpty()) R.string.catalog_empty else R.string.filter_empty
                                )
                            )
                            else -> {
                                val banner = when {
                                    current.offline -> stringResource(R.string.catalog_offline)
                                    current.failedRepos > 0 -> stringResource(R.string.catalog_partial, current.failedRepos)
                                    else -> null
                                }
                                ModuleList(
                                    items = items,
                                    onToggle = onToggle,
                                    onInstall = onInstallId,
                                    onRetryDetails = onRetry,
                                    onOpenSource = onOpenSource,
                                    banner = banner
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
