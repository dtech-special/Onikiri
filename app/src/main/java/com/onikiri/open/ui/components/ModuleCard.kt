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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onikiri.open.R
import com.onikiri.open.data.InstallState
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.ui.theme.emphasized
import com.onikiri.open.viewmodel.DetailsState
import com.onikiri.open.viewmodel.ModuleUiItem

/**
 * Module card. Tapping the card (or "Details") toggles the expanded view with the full description,
 * author, version, module id, update date, a clickable source link and the README. The primary
 * action reflects the installed state: Install, Installed (tap to reinstall) or Update.
 * It only receives an immutable UI item and callbacks that take ids, so it recomposes only when
 * its own item changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleCard(
    item: ModuleUiItem,
    onToggle: (String) -> Unit,
    onInstall: (String) -> Unit,
    onRetryDetails: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = item.expanded
    val installState = item.installState
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron"
    )
    val hazard = item.status == ModuleStatus.HAZARD
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
    val id = item.id
    val toggle = remember(id, onToggle) { { onToggle(id) } }
    val install = remember(id, onInstall) { { onInstall(id) } }
    val retry = remember(id, onRetryDetails) { { onRetryDetails(id) } }
    val openSource = remember(item.githubRepo, onOpenSource) { { onOpenSource(item.githubRepo) } }
    val actions = remember(installState, primaryLabel, detailsLabel, hazard, install, toggle) {
        listOf(
            ConnectedAction(
                label = primaryLabel,
                onClick = install,
                icon = primaryIcon,
                primary = installState != InstallState.INSTALLED && !hazard,
                destructive = hazard && installState == InstallState.NOT_INSTALLED
            ),
            ConnectedAction(label = detailsLabel, onClick = toggle, icon = detailsIcon)
        )
    }

    ElevatedCard(
        onClick = toggle,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = item.title, style = MaterialTheme.typography.titleMedium.emphasized())
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusRow(item.status, item.installedVersion)
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
                    item = item,
                    onRetry = retry,
                    onOpenSource = openSource
                )
            }
        }
    }
}

/** Verification badge (Material 3 style: rounded tinted surface with icon and text) and the installed version. */
@Composable
private fun StatusRow(status: ModuleStatus, installedVersion: String?) {
    val installedText = installedVersion?.let {
        stringResource(R.string.module_installed_version, it.ifBlank { "?" })
    }
    if (status == ModuleStatus.UNKNOWN && installedText == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        StatusBadge(status)
        if (installedText != null) {
            Text(
                text = installedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
fun StatusBadge(status: ModuleStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val color: Color
    val label: String
    val icon: Painter
    when (status) {
        ModuleStatus.VERIFIED -> {
            color = scheme.primary
            label = stringResource(R.string.status_verified)
            icon = AppIcons.checkCircle()
        }
        ModuleStatus.REVIEW -> {
            color = scheme.tertiary
            label = stringResource(R.string.status_review)
            icon = AppIcons.schedule()
        }
        ModuleStatus.HAZARD -> {
            color = scheme.error
            label = stringResource(R.string.status_hazard)
            icon = AppIcons.warning()
        }
        ModuleStatus.UNKNOWN -> return
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ExpandedDetails(
    item: ModuleUiItem,
    onRetry: () -> Unit,
    onOpenSource: () -> Unit
) {
    val details = item.details
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        val loaded = (details as? DetailsState.Loaded)?.details

        InfoRow(stringResource(R.string.author), loaded?.author ?: item.owner)
        InfoRow(
            stringResource(R.string.module_version),
            when {
                loaded == null -> "…"
                loaded.version != null -> loaded.version
                else -> stringResource(R.string.module_no_release)
            }
        )
        InfoRow(stringResource(R.string.module_id), item.id)
        InfoRow(
            stringResource(R.string.module_updated),
            loaded?.publishedAt?.let { formatIsoDate(it) } ?: "—"
        )
        if (item.installedVersion != null) {
            InfoRow(
                stringResource(R.string.module_installed_label),
                item.installedVersion.ifBlank { "?" } +
                    if (item.installedVersionCode > 0) " (${item.installedVersionCode})" else ""
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
                text = "github.com/${item.githubRepo}",
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
