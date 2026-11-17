package com.onikiri.open.root

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide state that only lives for the current app session. */
object SessionState {
    private val _rebootRequired = MutableStateFlow(false)

    /** True once a module was installed, updated, switched or removed in this session. */
    val rebootRequired: StateFlow<Boolean> = _rebootRequired.asStateFlow()

    fun markRebootRequired() {
        _rebootRequired.value = true
    }

    fun clearRebootRequired() {
        _rebootRequired.value = false
    }
}
