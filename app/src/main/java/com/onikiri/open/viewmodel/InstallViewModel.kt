package com.onikiri.open.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.R
import com.onikiri.open.data.ManagerChoice
import com.onikiri.open.data.ModuleDetailsRepository
import com.onikiri.open.data.ReleaseAsset
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.data.UiException
import com.onikiri.open.data.defaultAsset
import com.onikiri.open.data.selectAssets
import com.onikiri.open.data.userMessage
import com.onikiri.open.install.CacheManager
import com.onikiri.open.install.ModuleDownloader
import com.onikiri.open.install.ModuleValidator
import com.onikiri.open.install.ValidationResult
import com.onikiri.open.root.RootShell
import com.onikiri.open.root.SessionState
import com.onikiri.open.root.toManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

enum class InstallStage { IDLE, PREPARING, DOWNLOADING, VALIDATING, INSTALLING, SUCCESS, FAILED }

enum class LogKind { INFO, STAGE, COMMAND, OUTPUT, SUCCESS, ERROR }

@Immutable
data class LogLine(val id: Long, val text: String, val kind: LogKind, val timeMs: Long)

/** State of the "choose the module archive" dialog. */
@Immutable
data class ArchivePickerState(
    val assets: List<ReleaseAsset>,
    val selectedName: String?,
    /** Archives that were already tried and had no module.prop. */
    val rejected: Set<String>
)

@Immutable
data class InstallUiState(
    val visible: Boolean = false,
    val title: String = "",
    val module: RootModule? = null,
    val stage: InstallStage = InstallStage.IDLE,
    /** -1 = preparing, 0 = download, 1 = validate, 2 = install, 3 = finished. */
    val stepIndex: Int = -1,
    val downloaded: Long = 0L,
    val total: Long = -1L,
    val log: List<LogLine> = emptyList(),
    val error: String? = null,
    val picker: ArchivePickerState? = null
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

    fun logAsText(): String = log.joinToString("\n") { it.text }
}

private class Validated(val file: File, val info: ValidationResult)

class InstallViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val store = SettingsStore.get(ctx)
    private val detailsRepository = ModuleDetailsRepository(ctx)
    private val downloader = ModuleDownloader(ctx)

    private val _state = MutableStateFlow(InstallUiState())
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    private val nextLogId = AtomicLong(0)
    private var job: Job? = null
    private var tempFile: File? = null
    private var pendingPick: CompletableDeferred<ReleaseAsset?>? = null
    private var lastLocalUri: Uri? = null

    /** Install or update a catalog module. */
    fun start(module: RootModule) {
        if (_state.value.busy) return
        lastLocalUri = null
        begin(title = module.title, module = module) { fetchAndValidate(module) }
    }

    /** Install a .zip chosen on the device. */
    fun startLocal(uri: Uri) {
        if (_state.value.busy) return
        lastLocalUri = uri
        begin(title = displayName(uri), module = null) { importLocal(uri) }
    }

    fun retry() {
        val module = _state.value.module
        val uri = lastLocalUri
        when {
            module != null -> start(module)
            uri != null -> startLocal(uri)
        }
    }

    fun cancel() {
        if (_state.value.cancellable) job?.cancel()
    }

    /** Hides the install screen. Ignored while an installation is running. */
    fun dismiss() {
        if (_state.value.busy) return
        _state.update { it.copy(visible = false) }
    }

    fun confirmArchive(asset: ReleaseAsset) {
        pendingPick?.complete(asset)
    }

    fun dismissArchivePicker() {
        pendingPick?.complete(null)
    }

    fun reboot() {
        viewModelScope.launch { RootShell.reboot() }
    }

    fun softReboot() {
        viewModelScope.launch { RootShell.softReboot() }
    }

    private fun begin(title: String, module: RootModule?, source: suspend () -> Validated) {
        _state.value = InstallUiState(
            visible = true,
            title = title,
            module = module,
            stage = InstallStage.PREPARING
        )
        job = viewModelScope.launch { runPipeline(source) }
    }

    private suspend fun runPipeline(source: suspend () -> Validated) {
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

            val validated = source()

            setStage(InstallStage.INSTALLING)
            log(str(R.string.log_installing), LogKind.STAGE)
            val command = RootShell.installCommand(manager, validated.file.absolutePath)
            log("$ $command", LogKind.COMMAND)
            val code = RootShell.runStreaming(command) { line -> log(line, LogKind.OUTPUT) }
            if (code != 0) throw UiException(R.string.log_install_failed, code)

            log(str(R.string.log_install_ok), LogKind.SUCCESS)
            SessionState.markRebootRequired()
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
            // The archive is always removed once flashing is over, whatever the outcome.
            val leftover = tempFile
            tempFile = null
            if (leftover != null) {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (leftover.delete() || !leftover.exists()) log(str(R.string.log_cleanup), LogKind.INFO)
                }
            }
        }
    }

    // ---- sources -------------------------------------------------------------------------

    private suspend fun fetchAndValidate(module: RootModule): Validated {
        log(str(R.string.log_fetching_release), LogKind.STAGE)
        val details = detailsRepository.load(module, force = false)
        val candidates = selectAssets(details.assets, module.assetFilter)
        if (candidates.isEmpty()) throw UiException(R.string.log_no_zip)

        // One archive: take it. Several: let the user choose.
        var chosen: ReleaseAsset = if (candidates.size == 1) {
            candidates[0]
        } else {
            askArchive(candidates, emptySet()) ?: throw CancellationException("archive choice cancelled")
        }
        val rejected = HashSet<String>()

        while (true) {
            setStage(InstallStage.DOWNLOADING)
            _state.update { it.copy(downloaded = 0L, total = -1L) }
            log(str(R.string.log_downloading, chosen.name), LogKind.STAGE)
            val fileKey = module.id + "_" + chosen.name.removeSuffix(".zip")
            val file = downloader.download(chosen.url, fileKey, chosen.size) { done, total ->
                _state.update { it.copy(downloaded = done, total = total) }
            }
            tempFile = file
            log(str(R.string.log_download_done, formatSize(file.length())), LogKind.INFO)

            setStage(InstallStage.VALIDATING)
            log(str(R.string.log_validating), LogKind.STAGE)
            try {
                val info = withContext(Dispatchers.IO) { ModuleValidator.validate(file) }
                logValid(info)
                return Validated(file, info)
            } catch (e: UiException) {
                if (e.resId != R.string.val_no_prop || candidates.size < 2) throw e
                rejected += chosen.name
                withContext(Dispatchers.IO) { file.delete() }
                tempFile = null
                log(str(R.string.log_no_prop_retry, chosen.name), LogKind.ERROR)
                setStage(InstallStage.PREPARING)
                chosen = askArchive(candidates, rejected)
                    ?: throw CancellationException("archive choice cancelled")
            }
        }
    }

    private suspend fun importLocal(uri: Uri): Validated {
        log(str(R.string.log_copying_local), LogKind.STAGE)
        val dir = CacheManager.downloadsDir(ctx)
        val file = withContext(Dispatchers.IO) {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")
            val target = File(dir, "local_${System.currentTimeMillis()}.zip")
            tempFile = target
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            input.use { source ->
                target.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            target
        }
        setStage(InstallStage.VALIDATING)
        log(str(R.string.log_validating), LogKind.STAGE)
        val info = withContext(Dispatchers.IO) { ModuleValidator.validate(file) }
        logValid(info)
        return Validated(file, info)
    }

    private suspend fun askArchive(candidates: List<ReleaseAsset>, rejected: Set<String>): ReleaseAsset? {
        val deferred = CompletableDeferred<ReleaseAsset?>()
        pendingPick = deferred
        _state.update {
            it.copy(
                picker = ArchivePickerState(
                    assets = candidates,
                    selectedName = defaultAsset(candidates, rejected)?.name,
                    rejected = rejected
                )
            )
        }
        try {
            return deferred.await()
        } finally {
            pendingPick = null
            _state.update { it.copy(picker = null) }
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun logValid(info: ValidationResult) {
        log(
            str(R.string.log_valid, info.moduleName ?: info.moduleId, info.version ?: "?", info.entryCount),
            LogKind.INFO
        )
    }

    private fun displayName(uri: Uri): String {
        val name = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "module.zip"
    }

    private fun markCancelled() {
        val message = str(R.string.log_cancelled)
        log(message, LogKind.ERROR)
        _state.update { it.copy(stage = InstallStage.FAILED, error = message, picker = null) }
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
