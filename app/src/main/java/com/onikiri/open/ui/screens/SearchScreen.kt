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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.filterModules
import com.onikiri.open.ui.components.CenteredLoading
import com.onikiri.open.ui.components.CenteredMessage
import com.onikiri.open.ui.components.ModuleList
import com.onikiri.open.ui.components.OnikiriSearchField
import com.onikiri.open.viewmodel.CatalogState
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.DetailsState

@Composable
fun SearchScreen(
    catalogVm: CatalogViewModel,
    onInstall: (RootModule) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val state by catalogVm.state.collectAsStateWithLifecycle()
    val expanded by catalogVm.expanded.collectAsStateWithLifecycle()
    val details by catalogVm.details.collectAsStateWithLifecycle()

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
                // Filtering runs synchronously on every keystroke, so the list updates instantly.
                val results = remember(current.modules, query, details) {
                    filterModules(current.modules, query) { module ->
                        (details[module.id] as? DetailsState.Loaded)?.details?.author
                    }
                }
                Text(
                    text = stringResource(R.string.search_count, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                )
                AnimatedContent(
                    targetState = results.isEmpty(),
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "searchResults"
                ) { empty ->
                    if (empty) {
                        CenteredMessage(text = stringResource(R.string.search_no_results, query.trim()))
                    } else {
                        ModuleList(
                            modules = results,
                            expanded = expanded,
                            details = details,
                            onToggle = catalogVm::toggle,
                            onInstall = onInstall,
                            onRetryDetails = { catalogVm.loadDetails(it, force = true) }
                        )
                    }
                }
            }
        }
    }
}
