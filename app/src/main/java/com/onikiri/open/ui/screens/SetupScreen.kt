package com.onikiri.open.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onikiri.open.R
import com.onikiri.open.data.ManagerChoice
import com.onikiri.open.root.DetectedManager
import com.onikiri.open.root.toManager
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.Confetti3D
import com.onikiri.open.viewmodel.RootInfo
import com.onikiri.open.viewmodel.RootViewModel
import com.onikiri.open.viewmodel.SettingsViewModel

@Composable
fun SetupScreen(settingsVm: SettingsViewModel, rootVm: RootViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var confettiKey by rememberSaveable { mutableIntStateOf(0) }
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val info by rootVm.info.collectAsStateWithLifecycle()

    // Checks root when the welcome step is shown and again every time the user comes back to the app
    // (for example after granting root in KernelSU / APatch), so no manual restart is needed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (step == 0) rootVm.refresh()
    }
    BackHandler(enabled = step > 0) { step-- }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(step) {
                    detectTapGestures { if (step == 2) confettiKey++ }
                }
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(380)) { it * direction / 3 } + fadeIn(tween(380))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it * direction / 3 } + fadeOut(tween(220)))
                },
                label = "setupStep"
            ) { current ->
                when (current) {
                    0 -> WelcomeStep(info = info, onRetry = { rootVm.refresh(userInitiated = true) }, onNext = { step = 1 })
                    1 -> ManagerStep(
                        info = info,
                        selected = settings.managerChoice,
                        onSelect = settingsVm::setManagerChoice,
                        onNext = {
                            step = 2
                            confettiKey++
                        }
                    )
                    else -> SetupDoneScreen(
                        info = info,
                        choice = settings.managerChoice,
                        onFinish = settingsVm::completeSetup
                    )
                }
            }
            if (step == 2) {
                Confetti3D(playKey = confettiKey, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StepLayout(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            content()
        }
        Spacer(Modifier.height(12.dp))
        actions()
    }
}

@Composable
private fun HeroIcon(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun WelcomeStep(info: RootInfo, onRetry: () -> Unit, onNext: () -> Unit) {
    StepLayout(
        icon = {
            HeroIcon {
                Icon(
                    AppIcons.systemRoot(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = stringResource(R.string.setup_welcome_title),
        body = stringResource(R.string.setup_welcome_body),
        content = {
            AnimatedContent(
                targetState = when {
                    info.checking || !info.loaded -> 0
                    info.status.granted -> 1
                    else -> 2
                },
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "rootStatus"
            ) { state ->
                when (state) {
                    0 -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.checking_root))
                    }
                    1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                AppIcons.checkCircle(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.setup_root_ok),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (info.status.managers.isEmpty()) {
                                stringResource(R.string.setup_no_manager_detected)
                            } else {
                                stringResource(R.string.setup_detected, detectedNames(info.status.managers))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                AppIcons.warning(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                stringResource(R.string.root_not_available),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.give_root),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.catalog_retry)) }
                    }
                }
            }
        },
        actions = {
            Button(
                onClick = onNext,
                enabled = !info.checking,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.setup_continue)) }
        }
    )
}

@Composable
private fun ManagerStep(
    info: RootInfo,
    selected: ManagerChoice,
    onSelect: (ManagerChoice) -> Unit,
    onNext: () -> Unit
) {
    val managers = info.status.managers
    StepLayout(
        icon = {
            HeroIcon {
                Icon(
                    AppIcons.checkCircle(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = stringResource(R.string.setup_manager_title),
        body = stringResource(R.string.setup_manager_body),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ManagerOption(
                    title = stringResource(R.string.manager_auto),
                    subtitle = if (managers.isEmpty()) {
                        stringResource(R.string.setup_auto_none)
                    } else {
                        stringResource(R.string.setup_auto_will_use, stringResource(managers.first().manager.labelRes))
                    },
                    selected = selected == ManagerChoice.AUTO,
                    onClick = { onSelect(ManagerChoice.AUTO) }
                )
                for (choice in listOf(ManagerChoice.MAGISK, ManagerChoice.KERNELSU, ManagerChoice.APATCH)) {
                    val manager = choice.toManager() ?: continue
                    val detected = managers.firstOrNull { it.manager == manager }
                    ManagerOption(
                        title = stringResource(manager.labelRes),
                        subtitle = if (detected != null) {
                            stringResource(R.string.setup_found, detected.version ?: "")
                        } else {
                            stringResource(R.string.setup_not_found)
                        },
                        selected = selected == choice,
                        onClick = { onSelect(choice) }
                    )
                }
            }
        },
        actions = {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_finish))
            }
        }
    )
}

@Composable
private fun ManagerOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "optionContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "optionBorder"
    )
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(2.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetupDoneScreen(info: RootInfo, choice: ManagerChoice, onFinish: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "doneScale"
    )
    val effective: DetectedManager? = info.status.resolve(choice)
    val summary = when {
        choice != ManagerChoice.AUTO -> stringResource(choice.toManager()!!.labelRes)
        effective != null -> stringResource(R.string.setup_auto_will_use, stringResource(effective.manager.labelRes))
        else -> stringResource(R.string.manager_auto)
    }
    StepLayout(
        icon = {
            Box(Modifier.scale(scale)) {
                HeroIcon {
                    Icon(
                        AppIcons.checkCircle(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        title = stringResource(R.string.setup_done_title),
        body = stringResource(R.string.setup_done_body),
        content = {
            Text(
                text = stringResource(R.string.setup_summary, summary),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        },
        actions = {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_open))
            }
        }
    )
}

@Composable
private fun detectedNames(managers: List<DetectedManager>): String {
    val names = managers.map { stringResource(it.manager.labelRes) }
    return names.joinToString(", ")
}
