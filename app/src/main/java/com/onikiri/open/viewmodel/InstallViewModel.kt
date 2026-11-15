package com.onikiri.open.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.R
import com.onikiri.open.data.ManagerChoice
import com.onikiri.open.data.ModuleDetailsRepository
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.data.UiException
import com.onikiri.open.data.userMessage
import com.onikiri.open.install.ModuleDownloader
import com.onikiri.open.install.ModuleValidator
import com.onikiri.open.root.RootShell
import com.onikiri.open.root.toManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class InstallStage { IDLE, PREPARING, DOWNLOADING, VALIDATING, INSTALLING, SUCCESS, FAILED }

enum class LogKind { INFO, STAGE, COMMAND, OUTPUT, SUCCESS, ERROR }

@Immutable
data class LogLine(val id: Long, val text: String, val kind: LogKind, val timeMs: Long)

@Immutable
data class InstallUiState(
    val visible: Boolean = false,
    val module: RootModule? = null,
    val stage: InstallStage = InstallStage.IDLE,
    /** -1 = preparing, 0 = download, 1 = validate, 2 = install, 3 = finished. */
    val stepIndex: Int = -1,
    val downloaded: Long = 0L,
    val total: Long = -1L,
    val log: List<LogLine> = emptyList(),
    val error: String? = null
) {
    val busy: Boolean
        get() = stage == InstallStage.PREPARING || stage == InstallStage.DOWNLOADING ||
            stage == InstallStage.VALIDATING || stage == InstallStage.INSTALLING

    val cancellable: Boolean
        get() = stage == InstallStage.PREPARING || stage == InstallStage.DOWNLOADING ||
            stage == InstallStage.VALIDATING

    val progress: Float?
        get() = if (stage == InstallStage.DOWNLOADING && total > 0) {
            (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
}

class InstallViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val store = SettingsStore.get(ctx)
    private val detailsRepository = ModuleDetailsRepository(ctx)
    private val downloader = ModuleDownloader(ctx)

    private val _state = MutableStateFlow(InstallUiState())
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    private val nextLogId = AtomicLong(0)
    private var job: Job? = null

    fun start(module: RootModule) {
        if (_state.value.busy) return
        _state.value = InstallUiState(
            visible = true,
            module = module,
            stage = InstallStage.PREPARING
        )
        job = viewModelScope.launch { runInstall(module) }
    }

    fun retry() {
        val module = _state.value.module ?: return
        start(module)
    }

    fun cancel() {
        if (_state.value.cancellable) job?.cancel()
    }

    /** Hides the install screen. Ignored while an installation is running. */
    fun dismiss() {
        if (_state.value.busy) return
        _state.update { it.copy(visible = false) }
    }

    fun reboot() {
        viewModelScope.launch { RootShell.reboot() }
    }

    fun softReboot() {
        viewModelScope.launch { RootShell.softReboot() }
    }

    private suspend fun runInstall(module: RootModule) {
        var zip: File? = null
        try {
            log(str(R.string.log_root_check), LogKind.STAGE)
            val status = RootShell.detect()
            if (!status.granted) throw UiException(R.string.log_root_denied)

            val choice = store.settings.value.managerChoice
            val manager = status.resolve(choice) ?: throw if (choice == ManagerChoice.AUTO) {
                UiException(R.string.log_manager_not_found)
            } else {
                UiException(R.string.log_manager_missing_chosen, str(choice.toManager()!!.labelRes))
            }
            val managerLabel = str(manager.manager.labelRes)
            log(
                str(R.string.log_manager_using, managerLabel + (manager.version?.let { " ($it)" } ?: "")),
                LogKind.INFO
            )

            log(str(R.string.log_fetching_release), LogKind.STAGE)
            val details = detailsRepository.load(module, force = false)
            val url = details.downloadUrl ?: throw UiException(R.string.log_no_zip)

            setStage(InstallStage.DOWNLOADING)
            log(str(R.string.log_downloading, details.downloadName ?: "${module.id}.zip"), LogKind.STAGE)
            val file = downloader.download(url, module.id, details.downloadSize) { done, total ->
                _state.update { it.copy(downloaded = done, total = total) }
            }
            zip = file
            log(str(R.string.log_download_done, formatSize(file.length())), LogKind.INFO)

            setStage(InstallStage.VALIDATING)
            log(str(R.string.log_validating), LogKind.STAGE)
            val info = withContext(Dispatchers.IO) { ModuleValidator.validate(file) }
            log(
                str(
                    R.string.log_valid,
                    info.moduleName ?: info.moduleId,
                    info.version ?: "?",
                    info.entryCount
                ),
                LogKind.INFO
            )

            setStage(InstallStage.INSTALLING)
            log(str(R.string.log_installing), LogKind.STAGE)
            val command = RootShell.installCommand(manager, file.absolutePath)
            log("$ $command", LogKind.COMMAND)
            val code = RootShell.runStreaming(command) { line -> log(line, LogKind.OUTPUT) }
            if (code != 0) throw UiException(R.string.log_install_failed, code)

            log(str(R.string.log_install_ok), LogKind.SUCCESS)
            setStage(InstallStage.SUCCESS)
        } catch (e: CancellationException) {
            markCancelled()
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                markCancelled()
            } else {
                val message = e.userMessage(ctx)
                log(str(R.string.log_error, message), LogKind.ERROR)
                _state.update { it.copy(stage = InstallStage.FAILED, error = message) }
            }
        } finally {
            val leftover = zip
            if (leftover != null) {
                // The archive is always removed once flashing is over, whatever the outcome.
                withContext(NonCancellable + Dispatchers.IO) {
                    if (leftover.delete() || !leftover.exists()) log(str(R.string.log_cleanup), LogKind.INFO)
                }
            }
        }
    }

    private fun markCancelled() {
        val message = str(R.string.log_cancelled)
        log(message, LogKind.ERROR)
        _state.update { it.copy(stage = InstallStage.FAILED, error = message) }
    }

    private fun setStage(stage: InstallStage) {
        val step = when (stage) {
            InstallStage.DOWNLOADING -> 0
            InstallStage.VALIDATING -> 1
            InstallStage.INSTALLING -> 2
            InstallStage.SUCCESS -> 3
            else -> _state.value.stepIndex
        }
        _state.update { it.copy(stage = stage, stepIndex = step) }
    }

    private fun log(text: String, kind: LogKind) {
        val line = LogLine(nextLogId.getAndIncrement(), text, kind, System.currentTimeMillis())
        _state.update { current ->
            val merged = current.log + line
            current.copy(log = if (merged.size > MAX_LOG_LINES) merged.takeLast(MAX_LOG_LINES) else merged)
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any?): String = ctx.getString(id, *args)

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private companion object {
        const val MAX_LOG_LINES = 1500
    }
}
