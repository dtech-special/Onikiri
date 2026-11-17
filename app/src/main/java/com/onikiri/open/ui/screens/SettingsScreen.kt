package com.onikiri.open.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onikiri.open.BuildConfig
import com.onikiri.open.R
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.NavCapsuleClearance
import com.onikiri.open.ui.theme.emphasized
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.RootViewModel
import com.onikiri.open.viewmodel.SettingsViewModel

internal enum class SettingsRoute(val depth: Int) {
    HUB(0), CUSTOMIZATION(1), REPOS(1), SYSTEM(1), ABOUT(1), LICENSES(2)
}

/** Settings as a menu of categories, each opening its own sub-screen (like system settings). */
@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel,
    rootVm: RootViewModel,
    catalogVm: CatalogViewModel,
    installBusy: Boolean,
    snackbar: SnackbarHostState,
    onPickLocal: () -> Unit,
    onReboot: () -> Unit,
    onSoftReboot: () -> Unit
) {
    var routeName by rememberSaveable { mutableStateOf(SettingsRoute.HUB.name) }
    val route = SettingsRoute.valueOf(routeName)
    val goHub = { routeName = SettingsRoute.HUB.name }

    BackHandler(enabled = route != SettingsRoute.HUB) {
        routeName = if (route == SettingsRoute.LICENSES) SettingsRoute.ABOUT.name else SettingsRoute.HUB.name
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(320)) { it * direction / 4 } + fadeIn(tween(320))) togetherWith
                (slideOutHorizontally(tween(260)) { -it * direction / 4 } + fadeOut(tween(200)))
        },
        label = "settingsRoute"
    ) { current ->
        when (current) {
            SettingsRoute.HUB -> SettingsHub(onOpen = { routeName = it.name })
            SettingsRoute.CUSTOMIZATION -> CustomizationScreen(settingsVm, snackbar, onBack = goHub)
            SettingsRoute.REPOS -> ReposCacheScreen(settingsVm, catalogVm, installBusy, snackbar, onBack = goHub)
            SettingsRoute.SYSTEM -> SystemRootScreen(
                settingsVm = settingsVm,
                rootVm = rootVm,
                onPickLocal = onPickLocal,
                onReboot = onReboot,
                onSoftReboot = onSoftReboot,
                onBack = goHub
            )
            SettingsRoute.ABOUT -> AboutScreen(
                onOpenLicenses = { routeName = SettingsRoute.LICENSES.name },
                onBack = goHub
            )
            SettingsRoute.LICENSES -> LicensesScreen(onBack = { routeName = SettingsRoute.ABOUT.name })
        }
    }
}

@Composable
private fun SettingsHub(onOpen: (SettingsRoute) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = NavCapsuleClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium.emphasized(),
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        item {
            CategoryCard(AppIcons.customization(), R.string.cat_custom, R.string.cat_custom_sub) {
                onOpen(SettingsRoute.CUSTOMIZATION)
            }
        }
        item {
            CategoryCard(AppIcons.repositories(), R.string.cat_repos, R.string.cat_repos_sub) {
                onOpen(SettingsRoute.REPOS)
            }
        }
        item {
            CategoryCard(AppIcons.systemRoot(), R.string.cat_system, R.string.cat_system_sub) {
                onOpen(SettingsRoute.SYSTEM)
            }
        }
        item {
            CategoryCard(AppIcons.about(), R.string.cat_about, R.string.cat_about_sub) {
                onOpen(SettingsRoute.ABOUT)
            }
        }
        item {
            Text(
                text = versionLine(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }
}

/** `Onikiri v4.0-0 "Powder"` with the codename baked in at build time. */
@Composable
internal fun versionLine(): String =
    stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODENAME)

@Composable
private fun CategoryCard(icon: Painter, titleRes: Int, subtitleRes: Int, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium.emphasized())
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = AppIcons.chevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
