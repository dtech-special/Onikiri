package com.onikiri.open.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikiri.open.R
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.RootModule
import com.onikiri.open.ui.components.AnimatedNavigationBar
import com.onikiri.open.ui.components.NavDestination
import com.onikiri.open.ui.screens.InstallScreen
import com.onikiri.open.ui.screens.ModulesScreen
import com.onikiri.open.ui.screens.SearchScreen
import com.onikiri.open.ui.screens.SettingsScreen
import com.onikiri.open.ui.screens.SetupScreen
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.InstallViewModel
import com.onikiri.open.viewmodel.RootViewModel
import com.onikiri.open.viewmodel.SettingsViewModel

private val Destinations = listOf(
    NavDestination(R.string.nav_modules, Icons.Filled.Folder, Icons.Outlined.Folder),
    NavDestination(R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    NavDestination(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

/** Entry point of the UI: first-run setup, then the main scaffold. */
@Composable
fun OnikiriRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val rootVm: RootViewModel = viewModel()
    val catalogVm: CatalogViewModel = viewModel()
    val installVm: InstallViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = settings.setupDone,
        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(350)) },
        label = "setupGate"
    ) { done ->
        if (done) {
            MainScaffold(
                settingsVm = settingsVm,
                rootVm = rootVm,
                catalogVm = catalogVm,
                installVm = installVm
            )
        } else {
            SetupScreen(settingsVm = settingsVm, rootVm = rootVm)
        }
    }
}

@Composable
private fun MainScaffold(
    settingsVm: SettingsViewModel,
    rootVm: RootViewModel,
    catalogVm: CatalogViewModel,
    installVm: InstallViewModel
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()
    val install by installVm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var pendingHazard by remember { mutableStateOf<RootModule?>(null) }

    val requestInstall: (RootModule) -> Unit = { module ->
        if (module.status == ModuleStatus.HAZARD) pendingHazard = module else installVm.start(module)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedNavigationBar(
                    destinations = Destinations,
                    selectedIndex = selected,
                    onSelect = { selected = it }
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = selected,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it * direction / 4 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it * direction / 4 } + fadeOut(tween(200)))
                },
                label = "tabs"
            ) { page ->
                // Keeps scroll position and input of every tab while it is off screen.
                stateHolder.SaveableStateProvider(page) {
                    when (page) {
                        0 -> ModulesScreen(catalogVm = catalogVm, onInstall = requestInstall)
                        1 -> SearchScreen(catalogVm = catalogVm, onInstall = requestInstall)
                        else -> SettingsScreen(
                            settingsVm = settingsVm,
                            rootVm = rootVm,
                            catalogVm = catalogVm,
                            installBusy = install.busy,
                            snackbar = snackbar
                        )
                    }
                }
            }
        }

        BackHandler(enabled = install.visible) {
            if (!install.busy) installVm.dismiss()
        }
        AnimatedVisibility(
            visible = install.visible,
            enter = slideInVertically(tween(350)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200))
        ) {
            InstallScreen(
                state = install,
                confirmSoftReboot = settings.confirmSoftReboot,
                onCancel = installVm::cancel,
                onClose = installVm::dismiss,
                onRetry = installVm::retry,
                onReboot = installVm::reboot,
                onSoftReboot = installVm::softReboot
            )
        }
    }

    val hazard = pendingHazard
    if (hazard != null) {
        AlertDialog(
            onDismissRequest = { pendingHazard = null },
            title = { Text(stringResource(R.string.hazard_title)) },
            text = { Text(stringResource(R.string.hazard_message, hazard.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingHazard = null
                    installVm.start(hazard)
                }) { Text(stringResource(R.string.install_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHazard = null }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }
}
