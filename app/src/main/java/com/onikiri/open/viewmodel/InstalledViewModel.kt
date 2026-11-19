package com.onikiri.open.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.root.RootShell
import com.onikiri.open.root.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InstalledState {
    data object Loading : InstalledState
    data object NoRoot : InstalledState

    @Immutable
    data class Ready(val modules: List<InstalledModule>) : InstalledState
}

/** Modules found in /data/adb/modules, including ones that are in no repository. */
class InstalledViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<InstalledState>(InstalledState.Loading)
    val state: StateFlow<InstalledState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Installed modules (module.prop + data.onikiri); empty without root. */
    val modules: StateFlow<List<InstalledModule>> = _state
        .map { s -> (s as? InstalledState.Ready)?.modules.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var started = false

    init {
        // Reads /data/adb/modules as soon as setup is done (root is not requested before that).
        viewModelScope.launch {
            SettingsStore.get(app.applicationContext).settings.first { it.setupDone }
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (!started) refresh()
    }

    fun refresh() {
        started = true
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val modules = RootShell.listInstalledModules()
                _state.value = if (modules == null) InstalledState.NoRoot else InstalledState.Ready(modules)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = InstalledState.NoRoot
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setEnabled(dir: String, enabled: Boolean) {
        viewModelScope.launch {
            if (RootShell.setModuleDisabled(dir, disabled = !enabled)) {
                patch(dir) { it.copy(disabled = !enabled) }
                SessionState.markRebootRequired()
            }
        }
    }

    /** Marks the module for removal at the next boot, or cancels a pending removal. */
    fun setRemoval(dir: String, remove: Boolean) {
        viewModelScope.launch {
            if (RootShell.setModuleRemoved(dir, removed = remove)) {
                patch(dir) { it.copy(removing = remove) }
                SessionState.markRebootRequired()
            }
        }
    }

    private fun patch(dir: String, change: (InstalledModule) -> InstalledModule) {
        _state.update { current ->
            if (current is InstalledState.Ready) {
                current.copy(modules = current.modules.map { if (it.dir == dir) change(it) else it })
            } else {
                current
            }
        }
    }
}
