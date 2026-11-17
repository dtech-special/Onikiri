package com.onikiri.open.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onikiri.open.R
import com.onikiri.open.data.InstallState
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.RootModule
import com.onikiri.open.ui.theme.emphasized
import com.onikiri.open.viewmodel.DetailsState

/**
 * Module card. Tapping the card (or "Details") toggles the expanded view with the full description,
 * author, version, module id, update date, a clickable source link and the README. The primary
 * action reflects the installed state: Install, Installed (tap to reinstall) or Update.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleCard(
    module: RootModule,
    installState: InstallState,
    installed: InstalledModule?,
    expanded: Boolean,
    details: DetailsState?,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    onRetryDetails: () -> Unit,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron"
    )
    val hazard = module.status == ModuleStatus.HAZARD
    val primaryLabel = stringResource(
        when (installState) {
            InstallState.NOT_INSTALLED -> R.string.module_install
            InstallState.INSTALLED -> R.string.module_installed_label
            InstallState.UPDATE -> R.string.module_update
        }
    )
    val primaryIcon = when (installState) {
        InstallState.NOT_INSTALLED -> AppIcons.download()
        InstallState.INSTALLED -> AppIcons.check()
        InstallState.UPDATE -> AppIcons.update()
    }
    val detailsLabel = stringResource(if (expanded) R.string.module_hide else R.string.module_details)
    val detailsIcon = AppIcons.expandMore()
    val actions = remember(installState, primaryLabel, detailsLabel, hazard, onInstall, onToggle) {
        listOf(
            ConnectedAction(
                label = primaryLabel,
                onClick = onInstall,
                icon = primaryIcon,
                primary = installState != InstallState.INSTALLED && !hazard,
                destructive = hazard && installState == InstallState.NOT_INSTALLED
            ),
            ConnectedAction(label = detailsLabel, onClick = onToggle, icon = detailsIcon)
        )
    }

    ElevatedCard(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = module.title, style = MaterialTheme.typography.titleMedium.emphasized())
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusLabel(module.status, installed)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = AppIcons.expandMore(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevron)
                )
            }
            ConnectedActions(actions)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                ExpandedDetails(
                    module = module,
                    installed = installed,
                    details = details,
                    onRetry = onRetryDetails,
                    onOpenSource = onOpenSource
                )
            }
        }
    }
}

@Composable
private fun StatusLabel(status: ModuleStatus, installed: InstalledModule?) {
    val (text, color) = when (status) {
        ModuleStatus.VERIFIED -> stringResource(R.string.status_verified) to MaterialTheme.colorScheme.primary
        ModuleStatus.REVIEW -> stringResource(R.string.status_review) to MaterialTheme.colorScheme.tertiary
        ModuleStatus.HAZARD -> stringResource(R.string.status_hazard) to MaterialTheme.colorScheme.error
        ModuleStatus.UNKNOWN -> "" to MaterialTheme.colorScheme.outline
    }
    val installedText = installed?.let {
        stringResource(R.string.module_installed_version, it.version.ifBlank { "?" })
    }
    val combined = listOfNotNull(text.ifEmpty { null }, installedText).joinToString("  ·  ")
    if (combined.isNotEmpty()) {
        Text(text = combined, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ExpandedDetails(
    module: RootModule,
    installed: InstalledModule?,
    details: DetailsState?,
    onRetry: () -> Unit,
    onOpenSource: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        val loaded = (details as? DetailsState.Loaded)?.details

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
        if (installed != null) {
            InfoRow(
                stringResource(R.string.module_installed_label),
                installed.version.ifBlank { "?" } + if (installed.versionCode > 0) " (${installed.versionCode})" else ""
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSource),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.module_source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp)
            )
            Text(
                text = "github.com/${module.githubRepo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = AppIcons.openInNew(),
                contentDescription = stringResource(R.string.open_in_browser),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        when (details) {
            null, DetailsState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MorphLoading(modifier = Modifier.size(28.dp))
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
                        style = MaterialTheme.typography.titleSmall.emphasized(),
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
                Spacer(Modifier.height(2.dp))
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
