package com.onikiri.open.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onikiri.open.R
import com.onikiri.open.data.ModuleDetails
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.RootModule
import com.onikiri.open.viewmodel.DetailsState

/**
 * Module card. Tapping the card toggles the expanded view (full description, author, version,
 * module id, update date and README); the Install button handles its own click.
 */
@Composable
fun ModuleCard(
    module: RootModule,
    expanded: Boolean,
    details: DetailsState?,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    onRetryDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevron"
    )
    ElevatedCard(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = module.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusLabel(module.status)
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onInstall,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = if (module.status == ModuleStatus.HAZARD) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.module_install))
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevron)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(260)),
                exit = fadeOut(tween(120))
            ) {
                ExpandedDetails(module = module, details = details, onRetry = onRetryDetails)
            }
        }
    }
}

@Composable
private fun StatusLabel(status: ModuleStatus) {
    val (text, color) = when (status) {
        ModuleStatus.VERIFIED -> stringResource(R.string.status_verifed) to MaterialTheme.colorScheme.primary
        ModuleStatus.REVIEW -> stringResource(R.string.status_review) to MaterialTheme.colorScheme.tertiary
        ModuleStatus.HAZARD -> stringResource(R.string.status_hazard) to MaterialTheme.colorScheme.error
        ModuleStatus.UNKNOWN -> "" to MaterialTheme.colorScheme.outline
    }
    if (text.isNotEmpty()) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ExpandedDetails(module: RootModule, details: DetailsState?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider()
        val loaded: ModuleDetails? = (details as? DetailsState.Loaded)?.details

        InfoRow(stringResource(R.string.author), loaded?.author ?: module.owner)
        InfoRow(
            stringResource(R.string.module_version),
            when {
                loaded == null -> "…"
                loaded.version != null -> loaded.version
                else -> stringResource(R.string.module_no_release)
            }
        )
        InfoRow(stringResource(R.string.module_id), module.id)
        InfoRow(
            stringResource(R.string.module_updated),
            loaded?.publishedAt?.let { formatIsoDate(it) } ?: "—"
        )
        InfoRow(stringResource(R.string.module_source), "github.com/${module.githubRepo}")

        when (details) {
            null, DetailsState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.module_details_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is DetailsState.Failed -> Column {
                Text(
                    text = stringResource(R.string.module_details_failed, details.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.catalog_retry)) }
            }
            is DetailsState.Loaded -> {
                val readme = details.details.readme
                if (!readme.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.module_readme),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MarkdownText(markdown = readme, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
