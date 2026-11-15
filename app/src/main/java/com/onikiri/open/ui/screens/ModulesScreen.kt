package com.onikiri.open.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RootModule
import com.onikiri.open.ui.components.CenteredLoading
import com.onikiri.open.ui.components.CenteredMessage
import com.onikiri.open.ui.components.ModuleList
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
    val expanded by catalogVm.expanded.collectAsStateWithLifecycle()
    val details by catalogVm.details.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = refreshing && state is CatalogState.Ready,
        onRefresh = { catalogVm.refresh(force = true) },
        modifier = Modifier.fillMaxSize()
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
                is CatalogState.Ready -> if (current.modules.isEmpty()) {
                    CenteredMessage(text = stringResource(R.string.catalog_empty))
                } else {
                    val banner = when {
                        current.offline -> stringResource(R.string.catalog_offline)
                        current.failedRepos > 0 -> stringResource(R.string.catalog_partial, current.failedRepos)
                        else -> null
                    }
                    ModuleList(
                        modules = current.modules,
                        expanded = expanded,
                        details = details,
                        onToggle = catalogVm::toggle,
                        onInstall = onInstall,
                        onRetryDetails = { catalogVm.loadDetails(it, force = true) },
                        banner = banner
                    )
                }
            }
        }
    }
}
