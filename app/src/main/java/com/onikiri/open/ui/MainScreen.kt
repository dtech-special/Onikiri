package com.onikiri.open.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikiri.open.R
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.RootModule
import com.onikiri.open.root.SessionState
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.FloatingNavCapsule
import com.onikiri.open.ui.components.NavCapsuleClearance
import com.onikiri.open.ui.components.NavDestination
import com.onikiri.open.ui.components.RebootBanner
import com.onikiri.open.ui.screens.InstallScreen
import com.onikiri.open.ui.screens.InstalledScreen
import com.onikiri.open.ui.screens.ModulesScreen
import com.onikiri.open.ui.screens.SearchScreen
import com.onikiri.open.ui.screens.SecretPhysicsScreen
import com.onikiri.open.ui.screens.SettingsScreen
import com.onikiri.open.ui.screens.SetupScreen
import com.onikiri.open.viewmodel.CatalogViewModel
import com.onikiri.open.viewmodel.InstallStage
import com.onikiri.open.viewmodel.InstallViewModel
import com.onikiri.open.viewmodel.InstalledViewModel
import com.onikiri.open.viewmodel.RootViewModel
import com.onikiri.open.viewmodel.SettingsViewModel

private const val PAGE_MODULES = 0
private const val PAGE_INSTALLED = 1
private const val PAGE_SEARCH = 2
private const val PAGE_SETTINGS = 3

private val Destinations = listOf(
    NavDestination(R.string.nav_modules) { AppIcons.modules(it) },
    NavDestination(R.string.nav_installed) { AppIcons.installed(it) },
    NavDestination(R.string.nav_search) { AppIcons.search(it) },
    NavDestination(R.string.nav_settings) { AppIcons.settings(it) }
)

/** One tab. Hidden tabs stay in the tree but are invisible, below the active one and ignore input. */
@Composable
private fun TabLayer(active: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer { alpha = if (active) 1f else 0f }
            .then(if (active) Modifier else Modifier.blockInput())
    ) {
        content()
    }
}

private fun Modifier.blockInput(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }
    .clearAndSetSemantics { }

/** Entry point of the UI: first-run setup, then the main scaffold. */
@Composable
fun OnikiriRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val rootVm: RootViewModel = viewModel()
    val catalogVm: CatalogViewModel = viewModel()
    val installedVm: InstalledViewModel = viewModel()
    val installVm: InstallViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    // Catalog cards (Installed / Update) are mapped from the installed list inside the ViewModel.
    LaunchedEffect(catalogVm, installedVm) { catalogVm.bindInstalled(installedVm.modules) }

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
                installedVm = installedVm,
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
    installedVm: InstalledViewModel,
    installVm: InstallViewModel
) {
    var selected by rememberSaveable { mutableIntStateOf(PAGE_MODULES) }
    val snackbar = remember { SnackbarHostState() }
    var visitedMask by rememberSaveable { mutableIntStateOf(1 shl PAGE_MODULES) }
    val shown = visitedMask or (1 shl selected)
    var secretOpen by remember { mutableStateOf(false) }
    val install by installVm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val rebootRequired by SessionState.rebootRequired.collectAsStateWithLifecycle()
    var pendingHazard by remember { mutableStateOf<RootModule?>(null) }
    var confirmBannerSoft by remember { mutableStateOf(false) }

    val requestInstall: (RootModule) -> Unit = { module ->
        if (module.status == ModuleStatus.HAZARD) pendingHazard = module else installVm.start(module)
    }
    val softReboot = {
        installVm.softReboot()
        SessionState.clearRebootRequired()
    }
    val localZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installVm.startLocal(uri)
    }
    val pickLocalZip = {
        localZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    // Reads /data/adb/modules (module.prop + data.onikiri) so the catalog can show Installed / Update right away.
    LaunchedEffect(Unit) { installedVm.ensureLoaded() }
    LaunchedEffect(selected) { visitedMask = visitedMask or (1 shl selected) }
    // A finished installation changes the installed list.
    LaunchedEffect(install.stage) {
        if (install.stage == InstallStage.SUCCESS) installedVm.refresh()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = NavCapsuleClearance - 24.dp)) }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                RebootBanner(
                    visible = rebootRequired && !install.visible,
                    onSoftReboot = { if (settings.confirmSoftReboot) confirmBannerSoft = true else softReboot() },
                    onDismiss = SessionState::clearRebootRequired
                )
                // Visited tabs stay composed (scroll position, cards and input survive); only the
                // visibility changes, so switching tabs costs no recomposition.
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    for (page in PAGE_MODULES..PAGE_SETTINGS) {
                        if (shown and (1 shl page) == 0) continue
                        key(page) {
                            TabLayer(active = page == selected) {
                                when (page) {
                                    PAGE_MODULES -> ModulesScreen(catalogVm, requestInstall)
                                    PAGE_INSTALLED -> InstalledScreen(
                                        installedVm = installedVm,
                                        catalogVm = catalogVm,
                                        onUpdate = requestInstall,
                                        onPickLocal = pickLocalZip
                                    )
                                    PAGE_SEARCH -> SearchScreen(catalogVm, requestInstall)
                                    else -> SettingsScreen(
                                        settingsVm = settingsVm,
                                        rootVm = rootVm,
                                        catalogVm = catalogVm,
                                        installBusy = install.busy,
                                        snackbar = snackbar,
                                        active = selected == PAGE_SETTINGS,
                                        onPickLocal = pickLocalZip,
                                        onReboot = installVm::reboot,
                                        onSoftReboot = softReboot,
                                        onSecret = { secretOpen = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingNavCapsule(
            destinations = Destinations,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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
                onSoftReboot = softReboot,
                onPickArchive = installVm::confirmArchive,
                onDismissArchivePicker = installVm::dismissArchivePicker
            )
        }

        if (secretOpen) {
            SecretPhysicsScreen(onClose = { secretOpen = false })
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

    if (confirmBannerSoft) {
        AlertDialog(
            onDismissRequest = { confirmBannerSoft = false },
            title = { Text(stringResource(R.string.soft_reboot_confirm_title)) },
            text = { Text(stringResource(R.string.soft_reboot_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBannerSoft = false
                    softReboot()
                }) { Text(stringResource(R.string.confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBannerSoft = false }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }
}
