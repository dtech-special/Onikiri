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

/** Shared by the setup flow and the settings screens: asks for root and keeps the result. */
class RootViewModel(app: Application) : AndroidViewModel(app) {

    private val _info = MutableStateFlow(RootInfo())
    val info: StateFlow<RootInfo> = _info.asStateFlow()

    /**
     * @param userInitiated true for the Retry button: the cached libsu shell is always closed so the
     * root manager is asked again. Automatic re-checks (returning to the app) only do that while
     * root has not been granted yet, so a working root shell is never closed behind the user's back.
     */
    fun refresh(userInitiated: Boolean = false) {
        if (_info.value.checking) return
        val current = _info.value
        val fresh = userInitiated || (current.loaded && !current.status.granted)
        _info.update { it.copy(checking = true) }
        viewModelScope.launch {
            val status = try {
                RootShell.detect(fresh = fresh)
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
