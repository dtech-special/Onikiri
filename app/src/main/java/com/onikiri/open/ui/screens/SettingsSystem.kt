package com.onikiri.open.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.ManagerChoice
import com.onikiri.open.root.RootManager
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.CheckIcon
import com.onikiri.open.ui.components.ConnectedAction
import com.onikiri.open.ui.components.ConnectedActions
import com.onikiri.open.ui.components.InfoLine
import com.onikiri.open.ui.components.SettingsSection
import com.onikiri.open.ui.components.SubScreen
import com.onikiri.open.ui.components.SwitchRow
import com.onikiri.open.viewmodel.RootViewModel
import com.onikiri.open.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemRootScreen(
    settingsVm: SettingsViewModel,
    rootVm: RootViewModel,
    onPickLocal: () -> Unit,
    onReboot: () -> Unit,
    onSoftReboot: () -> Unit,
    onBack: () -> Unit
) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val rootInfo by rootVm.info.collectAsStateWithLifecycle()
    var confirmSoft by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { rootVm.ensureLoaded() }

    val localLabel = stringResource(R.string.system_install_local)
    val rebootLabel = stringResource(R.string.reboot)
    val softLabel = stringResource(R.string.soft_reboot)
    val addIcon = AppIcons.add()
    val powerIcon = AppIcons.power()
    val restartIcon = AppIcons.restart()

    SubScreen(title = stringResource(R.string.cat_system), onBack = onBack) {
        item(key = "root") {
            SettingsSection(
                title = stringResource(R.string.settings_system),
                trailing = {
                    IconButton(onClick = { rootVm.refresh(userInitiated = true) }, enabled = !rootInfo.checking) {
                        if (rootInfo.checking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(AppIcons.refresh(), contentDescription = stringResource(R.string.sys_recheck))
                        }
                    }
                }
            ) {
                InfoLine(
                    label = stringResource(R.string.sys_root),
                    value = stringResource(
                        when {
                            rootInfo.checking || !rootInfo.loaded -> R.string.sys_root_checking
                            rootInfo.status.granted -> R.string.sys_root_granted
                            else -> R.string.sys_root_denied
                        }
                    )
                )
                val managers = rootInfo.status.managers
                InfoLine(
                    label = stringResource(R.string.sys_manager),
                    value = if (managers.isEmpty()) {
                        stringResource(R.string.sys_none)
                    } else {
                        managers.joinToString("\n") { detected ->
                            val name = when (detected.manager) {
                                RootManager.MAGISK -> "Magisk"
                                RootManager.KERNELSU -> "KernelSU"
                                RootManager.APATCH -> "APatch"
                            }
                            if (detected.version != null) "$name — ${detected.version}" else name
                        }
                    }
                )
                InfoLine(stringResource(R.string.sys_kernel), settingsVm.system.kernel)
                InfoLine(stringResource(R.string.sys_arch), settingsVm.system.architecture)
                InfoLine(stringResource(R.string.sys_android), settingsVm.system.android)
                InfoLine(stringResource(R.string.sys_device), settingsVm.system.device)
            }
        }

        item(key = "manager") {
            SettingsSection(title = stringResource(R.string.settings_install)) {
                Text(
                    text = stringResource(R.string.settings_manager),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val choices = listOf(
                        ManagerChoice.AUTO to stringResource(R.string.manager_auto),
                        ManagerChoice.MAGISK to stringResource(R.string.magisk),
                        ManagerChoice.KERNELSU to stringResource(R.string.ksu),
                        ManagerChoice.APATCH to stringResource(R.string.apatch)
                    )
                    for ((choice, label) in choices) {
                        val selected = settings.managerChoice == choice
                        FilterChip(
                            selected = selected,
                            onClick = { settingsVm.setManagerChoice(choice) },
                            label = { Text(label) },
                            leadingIcon = if (selected) ({ CheckIcon(Modifier.size(18.dp)) }) else null
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_manager_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                SwitchRow(
                    title = stringResource(R.string.settings_auto_clean),
                    subtitle = stringResource(R.string.settings_auto_clean_sub),
                    checked = settings.autoCleanTemp,
                    onCheckedChange = settingsVm::setAutoClean
                )
                SwitchRow(
                    title = stringResource(R.string.settings_confirm_soft),
                    subtitle = stringResource(R.string.settings_confirm_soft_sub),
                    checked = settings.confirmSoftReboot,
                    onCheckedChange = settingsVm::setConfirmSoftReboot
                )
            }
        }

        item(key = "actions") {
            SettingsSection(title = stringResource(R.string.system_actions)) {
                ConnectedActions(
                    actions = listOf(ConnectedAction(label = localLabel, icon = addIcon, onClick = onPickLocal, primary = true)),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
                ConnectedActions(
                    actions = listOf(
                        ConnectedAction(label = rebootLabel, icon = powerIcon, onClick = onReboot),
                        ConnectedAction(
                            label = softLabel,
                            icon = restartIcon,
                            onClick = { if (settings.confirmSoftReboot) confirmSoft = true else onSoftReboot() }
                        )
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }

    if (confirmSoft) {
        AlertDialog(
            onDismissRequest = { confirmSoft = false },
            title = { Text(stringResource(R.string.soft_reboot_confirm_title)) },
            text = { Text(stringResource(R.string.soft_reboot_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSoft = false
                    onSoftReboot()
                }) { Text(stringResource(R.string.confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSoft = false }) { Text(stringResource(R.string.cancel_action)) }
            }
        )
    }
}
