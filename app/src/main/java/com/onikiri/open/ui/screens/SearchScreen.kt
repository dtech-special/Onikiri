package com.onikiri.open.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RootModule
import com.onikiri.open.ui.components.CenteredLoading
import com.onikiri.open.ui.components.CenteredMessage
import com.onikiri.open.ui.components.ModuleList
import com.onikiri.open.ui.components.OnikiriSearchField
import com.onikiri.open.viewmodel.CatalogState
import com.onikiri.open.viewmodel.CatalogViewModel

@Composable
fun SearchScreen(
    catalogVm: CatalogViewModel,
    onInstall: (RootModule) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val state by catalogVm.state.collectAsStateWithLifecycle()
    val results by catalogVm.searchResults.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    // Filtering runs in the ViewModel on Dispatchers.Default; the screen only forwards the query.
    LaunchedEffect(query) { catalogVm.setQuery(query) }

    val isEmpty by remember { derivedStateOf { results.isNullOrEmpty() } }
    val count by remember { derivedStateOf { results?.size ?: 0 } }

    val onToggle = remember(catalogVm) { { id: String -> catalogVm.toggle(id) } }
    val onRetry = remember(catalogVm) { { id: String -> catalogVm.retryDetails(id) } }
    val onInstallId = remember(catalogVm, onInstall) {
        { id: String -> catalogVm.moduleById(id)?.let(onInstall); Unit }
    }
    val onOpenSource = remember(uriHandler) { { repo: String -> uriHandler.openUri("https://github.com/$repo") } }

    Column(modifier = Modifier.fillMaxSize()) {
        OnikiriSearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        when (val current = state) {
            CatalogState.Loading -> CenteredLoading()
            is CatalogState.Error -> CenteredMessage(
                text = stringResource(R.string.catalog_error, current.message),
                actionLabel = stringResource(R.string.catalog_retry),
                onAction = { catalogVm.refresh(force = true) }
            )
            is CatalogState.Ready -> {
                val items = results
                if (items == null) {
                    CenteredLoading()
                } else {
                    Text(
                        text = stringResource(R.string.search_count, count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                    )
                    AnimatedContent(
                        targetState = isEmpty,
                        modifier = Modifier.weight(1f),
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "searchResults"
                    ) { empty ->
                        if (empty) {
                            CenteredMessage(text = stringResource(R.string.search_no_results, query.trim()))
                        } else {
                            ModuleList(
                                items = items,
                                onToggle = onToggle,
                                onInstall = onInstallId,
                                onRetryDetails = onRetry,
                                onOpenSource = onOpenSource
                            )
                        }
                    }
                }
            }
        }
    }
}
