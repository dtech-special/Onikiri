package com.onikiri.open.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikiri.open.R
import com.onikiri.open.ui.components.AppIcons
import com.onikiri.open.ui.components.IndicatorState
import com.onikiri.open.ui.components.StatusIndicator
import com.onikiri.open.ui.components.WordRevealText
import com.onikiri.open.ui.components.formatBytes
import com.onikiri.open.data.ReleaseAsset
import com.onikiri.open.ui.theme.emphasized
import com.onikiri.open.viewmodel.InstallStage
import com.onikiri.open.viewmodel.InstallUiState
import com.onikiri.open.viewmodel.LogKind
import com.onikiri.open.viewmodel.LogLine

private enum class ActionKind { BUSY, SUCCESS, FAILED }

private val TerminalBackground = Color(0xFF0D1117)
private val TerminalText = Color(0xFFE6EDF3)
private val TerminalStage = Color(0xFF79C0FF)
private val TerminalCommand = Color(0xFFD2A8FF)
private val TerminalSuccess = Color(0xFF56D364)
private val TerminalError = Color(0xFFFF7B72)

@Composable
fun InstallScreen(
    state: InstallUiState,
    confirmSoftReboot: Boolean,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReboot: () -> Unit,
    onSoftReboot: () -> Unit,
    onPickArchive: (ReleaseAsset) -> Unit,
    onDismissArchivePicker: () -> Unit
) {
    var confirmSoft by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val logCopied = stringResource(R.string.log_copied)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.install_title, state.title),
                        style = MaterialTheme.typography.headlineSmall.emphasized(),
                        maxLines = 2
                    )
                    AnimatedContent(
                        targetState = state.stage,
                        transitionSpec = {
                            (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 }) togetherWith fadeOut(tween(150))
                        },
                        label = "stageLabel"
                    ) { stage ->
                        Text(
                            text = stringResource(stageLabel(stage)),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (stage == InstallStage.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
                StatusIndicator(
                    state = when (state.stage) {
                        InstallStage.SUCCESS -> IndicatorState.SUCCESS
                        InstallStage.FAILED -> IndicatorState.FAILED
                        else -> IndicatorState.WORKING
                    },
                    size = 64.dp
                )
            }
            StepRow(state)
            ProgressBlock(state)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LogPanel(lines = state.log, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Onikiri install log", state.logAsText()))
                        Toast.makeText(context, logCopied, Toast.LENGTH_SHORT).show()
                    },
                    enabled = state.log.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = AppIcons.copy(),
                        contentDescription = stringResource(R.string.copy_log),
                        tint = TerminalText
                    )
                }
            }

            val kind = when (state.stage) {
                InstallStage.SUCCESS -> ActionKind.SUCCESS
                InstallStage.FAILED -> ActionKind.FAILED
                else -> ActionKind.BUSY
            }
            AnimatedContent(
                targetState = kind,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }) togetherWith fadeOut(tween(140))
                },
                label = "installActions"
            ) { current ->
                when (current) {
                    ActionKind.BUSY -> OutlinedButton(
                        onClick = onCancel,
                        enabled = state.cancellable,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (state.cancellable) R.string.install_cancel else R.string.module_installing
                            )
                        )
                    }
                    ActionKind.SUCCESS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.reboot_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onReboot, modifier = Modifier.weight(1f)) {
                                Icon(AppIcons.power(), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = stringResource(R.string.reboot),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            FilledTonalButton(
                                onClick = { if (confirmSoftReboot) confirmSoft = true else onSoftReboot() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(AppIcons.restart(), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = stringResource(R.string.soft_reboot),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.install_close))
                        }
                    }
                    ActionKind.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.install_close))
                        }
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.install_retry))
                        }
                    }
                }
            }
        }
    }

    state.picker?.let { picker ->
        ArchivePickerDialog(picker = picker, onConfirm = onPickArchive, onDismiss = onDismissArchivePicker)
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

private fun stageLabel(stage: InstallStage): Int = when (stage) {
    InstallStage.IDLE, InstallStage.PREPARING -> R.string.stage_prepare
    InstallStage.DOWNLOADING -> R.string.module_downloading
    InstallStage.VALIDATING -> R.string.module_checking
    InstallStage.INSTALLING -> R.string.module_installing
    InstallStage.SUCCESS -> R.string.module_installed
    InstallStage.FAILED -> R.string.fail
}

@Composable
private fun StepRow(state: InstallUiState) {
    val labels = listOf(R.string.step_download, R.string.step_validate, R.string.step_install)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val done = state.stepIndex > index
            val failed = state.stage == InstallStage.FAILED && state.stepIndex == index
            val active = state.busy && state.stepIndex == index
            StepChip(label = stringResource(label), number = index + 1, done = done, active = active, failed = failed)
        }
    }
}

@Composable
private fun StepChip(label: String, number: Int, done: Boolean, active: Boolean, failed: Boolean) {
    val container by animateColorAsState(
        targetValue = when {
            failed -> MaterialTheme.colorScheme.error
            done -> MaterialTheme.colorScheme.primary
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "stepContainer"
    )
    val content by animateColorAsState(
        targetValue = when {
            failed -> MaterialTheme.colorScheme.onError
            done -> MaterialTheme.colorScheme.onPrimary
            active -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "stepContent"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(28.dp).background(container, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = when {
                    failed -> 3
                    done -> 2
                    active -> 1
                    else -> 0
                },
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "stepIcon"
            ) { mode ->
                when (mode) {
                    3 -> Icon(AppIcons.close(), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                    2 -> Icon(AppIcons.check(), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                    1 -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = content
                    )
                    else -> Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = content)
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ProgressBlock(state: InstallUiState) {
    val target = state.progress
    val animated by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = tween(250, easing = LinearEasing),
        label = "downloadProgress"
    )
    val failed = state.stage == InstallStage.FAILED
    val barColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            state.stage == InstallStage.SUCCESS -> LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxWidth(),
                color = barColor
            )
            state.stage == InstallStage.DOWNLOADING && target != null || failed -> LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth(),
                color = barColor
            )
            state.busy -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = barColor)
            else -> LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
        }
        if (state.stage == InstallStage.DOWNLOADING) {
            val text = if (state.total > 0) {
                "${formatBytes(state.downloaded)} / ${formatBytes(state.total)}  ·  ${(animated * 100).toInt()}%"
            } else {
                formatBytes(state.downloaded)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogPanel(lines: List<LogLine>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = TerminalBackground
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines, key = { it.id }) { line -> LogLineRow(line) }
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine) {
    // Only lines that were just printed fade in; lines scrolled back into view show instantly.
    val animate = remember(line.id) { System.currentTimeMillis() - line.timeMs < 1200L }
    val color = when (line.kind) {
        LogKind.STAGE -> TerminalStage
        LogKind.COMMAND -> TerminalCommand
        LogKind.SUCCESS -> TerminalSuccess
        LogKind.ERROR -> TerminalError
        LogKind.INFO, LogKind.OUTPUT -> TerminalText
    }
    WordRevealText(
        text = line.text.ifEmpty { " " },
        animate = animate,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        ),
        color = color,
        modifier = Modifier.fillMaxWidth()
    )
}
