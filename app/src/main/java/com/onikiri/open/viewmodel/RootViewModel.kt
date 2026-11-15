package com.onikiri.open.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.root.RootShell
import com.onikiri.open.root.RootStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RootInfo(
    val checking: Boolean = false,
    val loaded: Boolean = false,
    val status: RootStatus = RootStatus(granted = false, managers = emptyList())
)

/** Shared by the setup flow and the settings screen: asks for root once and keeps the result. */
class RootViewModel(app: Application) : AndroidViewModel(app) {

    private val _info = MutableStateFlow(RootInfo())
    val info: StateFlow<RootInfo> = _info.asStateFlow()

    fun refresh() {
        if (_info.value.checking) return
        _info.update { it.copy(checking = true) }
        viewModelScope.launch {
            val status = try {
                RootShell.detect()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                RootStatus(granted = false, managers = emptyList())
            }
            _info.value = RootInfo(checking = false, loaded = true, status = status)
        }
    }

    fun ensureLoaded() {
        val current = _info.value
        if (!current.loaded && !current.checking) refresh()
    }
}
