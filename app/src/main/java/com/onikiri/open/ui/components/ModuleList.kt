package com.onikiri.open.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.installStateOf
import com.onikiri.open.viewmodel.DetailsState

@Composable
fun ModuleList(
    modules: List<RootModule>,
    installed: Map<String, InstalledModule>,
    expanded: Set<String>,
    details: Map<String, DetailsState>,
    onToggle: (RootModule) -> Unit,
    onInstall: (RootModule) -> Unit,
    onRetryDetails: (RootModule) -> Unit,
    onOpenSource: (RootModule) -> Unit,
    modifier: Modifier = Modifier,
    banner: String? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = NavCapsuleClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (banner != null) {
            item(key = "banner") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        items(modules, key = { it.id }) { module ->
            val installedModule = installed[module.id]
            val loaded = (details[module.id] as? DetailsState.Loaded)?.details
            ModuleCard(
                module = module,
                installState = installStateOf(installedModule, loaded),
                installed = installedModule,
                expanded = module.id in expanded,
                details = details[module.id],
                onToggle = { onToggle(module) },
                onInstall = { onInstall(module) },
                onRetryDetails = { onRetryDetails(module) },
                onOpenSource = { onOpenSource(module) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MorphLoading(modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(
                visible = actionLabel != null && onAction != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Button(onClick = { onAction?.invoke() }) { Text(actionLabel ?: "") }
            }
        }
    }
}
