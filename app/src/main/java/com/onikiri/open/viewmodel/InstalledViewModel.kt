package com.onikiri.open.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.root.RootShell
import com.onikiri.open.root.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Installed modules keyed by module id, used to match them with the catalog. */
    val byId: StateFlow<Map<String, InstalledModule>> = _state
        .map { s -> (s as? InstalledState.Ready)?.modules?.associateBy { it.id }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var started = false

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

    fun setEnabled(module: InstalledModule, enabled: Boolean) {
        viewModelScope.launch {
            if (RootShell.setModuleDisabled(module.dir, disabled = !enabled)) {
                patch(module.dir) { it.copy(disabled = !enabled) }
                SessionState.markRebootRequired()
            }
        }
    }

    /** Marks the module for removal at the next boot, or cancels a pending removal. */
    fun setRemoval(module: InstalledModule, remove: Boolean) {
        viewModelScope.launch {
            if (RootShell.setModuleRemoved(module.dir, removed = remove)) {
                patch(module.dir) { it.copy(removing = remove) }
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
